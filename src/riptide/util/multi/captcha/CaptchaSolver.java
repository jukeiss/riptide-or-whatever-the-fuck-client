package riptide.util.multi.captcha;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.minecraft.network.chat.Component;

public final class CaptchaSolver {
   private static final long WINDOW_MS = 120000L;
   private static final long MIN_DELAY_MS = 600L;
   private static final long MAX_DELAY_MS = 1900L;
   private static final long RESEND_GUARD_MS = 1500L;
   private static final long ADVANCE_TIMEOUT_MS = 3500L;
   private static final double MAP_CONFIDENCE = 0.001;
   private static final int MAX_CANDIDATES = 3;
   private static final int CACHE_MAX = 8192;
   private static final Map<Long, String> IMAGE_CACHE = new ConcurrentHashMap<>();
   private final CaptchaSolver.Host host;
   private final Executor worker;
   private final CaptchaChatSolver chatSolver = new CaptchaChatSolver();
   private final Random random = new Random();
   private volatile long windowUntil;
   private volatile long lastAnswerAt;
   private volatile String lastAnswer = "";
   private volatile CaptchaChatSolver.Answer pending;
   private volatile long sendAt;
   private volatile long solvingHash = Long.MIN_VALUE;
   private volatile byte[] pendingColors;
   private volatile long pendingHash;
   private volatile String pendingCachedFirst;
   private volatile long challengeHash;
   private volatile String[] candidates;
   private volatile int candidateIndex;
   private volatile long advanceDeadline;
   private volatile boolean submittedWasNudged;
   private volatile boolean timeoutAdvanced;
   private volatile String submittedText;

   public CaptchaSolver(CaptchaSolver.Host host, Executor worker) {
      this.host = host;
      this.worker = worker;
   }

   public void reset(long now) {
      if (this.challengeHash != 0L && this.submittedText != null && !this.submittedWasNudged && !this.timeoutAdvanced) {
         if (IMAGE_CACHE.size() < 8192) {
            IMAGE_CACHE.putIfAbsent(this.challengeHash, this.submittedText);
         }

         this.host.captchaNote("Solved captcha");
      }

      this.windowUntil = now + 120000L;
      this.pending = null;
      this.sendAt = 0L;
      this.solvingHash = Long.MIN_VALUE;
      this.pendingColors = null;
      this.pendingHash = 0L;
      this.pendingCachedFirst = null;
      this.challengeHash = 0L;
      this.candidates = null;
      this.candidateIndex = 0;
      this.advanceDeadline = 0L;
      this.submittedWasNudged = false;
      this.timeoutAdvanced = false;
      this.submittedText = null;
   }

   private boolean armed(long now) {
      return now < this.windowUntil;
   }

   public boolean hasActiveChallenge() {
      return this.challengeHash != 0L || this.pending != null || this.candidates != null;
   }

   public boolean isActivelySolving(long now) {
      return this.armed(now) && this.hasActiveChallenge();
   }

   private void extend(long now) {
      this.windowUntil = now + 120000L;
   }

   public void onChat(Component component, long now) {
      if (component != null) {
         if (this.challengeHash != 0L && isWrongNudge(component)) {
            this.submittedWasNudged = true;
            IMAGE_CACHE.remove(this.challengeHash);
            this.advanceCandidate(now);
         } else if (this.armed(now)) {
            CaptchaChatSolver.Answer answer = this.chatSolver.onChat(component);
            if (answer != null) {
               this.extend(now);
               this.queue(answer, now);
            }
         }
      }
   }

   private static boolean isWrongNudge(Component c) {
      String s = c.getString().toLowerCase(Locale.ROOT);
      return s.contains("incorrect") || s.contains("wrong") || s.contains("try again") && !s.contains("➤");
   }

   private void advanceCandidate(long now) {
      String[] cands = this.candidates;
      int idx = this.candidateIndex + 1;
      this.candidateIndex = idx;
      this.advanceDeadline = 0L;
      if (cands != null && idx < cands.length) {
         this.extend(now);
         this.queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, cands[idx]), now);
      }
   }

   public void onMapData(byte[] colors, long now) {
      if (this.armed(now) && colors != null && colors.length == 16384) {
         long hash = fnv1a(colors);
         if (this.solvingHash != hash && hash != this.challengeHash) {
            this.challengeHash = hash;
            this.candidateIndex = 0;
            this.candidates = null;
            this.submittedWasNudged = false;
            this.timeoutAdvanced = false;
            this.submittedText = null;
            this.extend(now);
            String cached = IMAGE_CACHE.get(hash);
            if (cached != null) {
               this.candidates = new String[]{cached};
               this.queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, cached), now);
            }

            if (this.solvingHash != Long.MIN_VALUE) {
               this.pendingColors = colors;
               this.pendingHash = hash;
               this.pendingCachedFirst = cached;
            } else {
               this.solvingHash = hash;
               this.submitOcr(colors, hash, cached, now);
            }
         }
      }
   }

   private void submitOcr(byte[] colors, long hash, String cachedFirst, long now) {
      this.worker.execute(() -> {
         try {
            CaptchaMapImage img = CaptchaMapImage.fromMapColors(colors);
            List<CaptchaMapOcr.OcrResult> ranked = CaptchaMapOcr.detectAndSolveRanked(img, 3);
            List<String> list = new ArrayList<>();
            if (cachedFirst != null) {
               list.add(cachedFirst);
            }

            for (int i = 0; i < ranked.size(); i++) {
               CaptchaMapOcr.OcrResult r = ranked.get(i);
               if (r.ok()) {
                  boolean isBest = list.isEmpty();
                  if ((isBest || !(r.confidence() < 0.001)) && !list.contains(r.text())) {
                     list.add(r.text());
                  }
               }
            }

            if (!list.isEmpty() && hash == this.challengeHash) {
               this.candidates = list.toArray(new String[0]);
               this.extend(now);
               if (cachedFirst == null) {
                  int idx = Math.min(this.candidateIndex, this.candidates.length - 1);
                  this.queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, this.candidates[idx]), now);
               }
            }
         } catch (Throwable var16) {
         } finally {
            this.drainNextOcr(hash);
         }
      });
   }

   private void drainNextOcr(long finishedHash) {
      byte[] next = this.pendingColors;
      long nextHash = this.pendingHash;
      String nextCached = this.pendingCachedFirst;
      this.pendingColors = null;
      this.pendingHash = 0L;
      this.pendingCachedFirst = null;
      if (next != null && nextHash == this.challengeHash && this.armed(System.currentTimeMillis())) {
         this.solvingHash = nextHash;
         this.submitOcr(next, nextHash, nextCached, System.currentTimeMillis());
      } else if (this.solvingHash == finishedHash) {
         this.solvingHash = Long.MIN_VALUE;
      }
   }

   public void onTitle(Component component, long now) {
      if (component != null) {
         String s = component.getString().toLowerCase(Locale.ROOT);
         if (s.contains("captcha") || s.contains("code") || s.contains("verif")) {
            this.extend(now);
         }
      }
   }

   private void queue(CaptchaChatSolver.Answer answer, long now) {
      if (!answer.text().equals(this.lastAnswer) || now - this.lastAnswerAt >= 1500L) {
         this.pending = answer;
         this.sendAt = now + 600L + (long)(this.random.nextDouble() * 1300.0);
      }
   }

   public void tick(long now) {
      String[] cands = this.candidates;
      if (this.advanceDeadline != 0L && now >= this.advanceDeadline && this.armed(now) && cands != null && this.candidateIndex + 1 < cands.length) {
         this.timeoutAdvanced = true;
         this.advanceCandidate(now);
      }

      CaptchaChatSolver.Answer answer = this.pending;
      if (answer != null && now >= this.sendAt) {
         this.pending = null;
         this.lastAnswer = answer.text();
         this.lastAnswerAt = now;

         try {
            if (answer.kind() == CaptchaChatSolver.Kind.COMMAND) {
               this.host.sendCaptchaCommand(answer.text());
            } else {
               this.host.sendCaptchaChat(answer.text());
            }

            if (this.challengeHash != 0L) {
               this.submittedText = answer.text();
               this.submittedWasNudged = false;
               this.advanceDeadline = now + 3500L;
            }
         } catch (Throwable var6) {
         }
      }
   }

   private static long fnv1a(byte[] data) {
      long h = -3750763034362895579L;

      for (byte b : data) {
         h ^= b & 255;
         h *= 1099511628211L;
      }

      return h;
   }

   public interface Host {
      void sendCaptchaChat(String var1);

      void sendCaptchaCommand(String var1);

      default void captchaNote(String note) {
      }
   }
}
