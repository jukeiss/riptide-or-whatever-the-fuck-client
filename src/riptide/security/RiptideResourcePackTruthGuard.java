package riptide.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;

public final class RiptideResourcePackTruthGuard {
   private static final ConcurrentMap<UUID, RiptideResourcePackTruthGuard.GuardedPack> GUARDED_PACKS = new ConcurrentHashMap<>();

   private RiptideResourcePackTruthGuard() {
   }

   public static RiptideResourcePackTruthGuard.Verdict classify(ClientboundResourcePackPushPacket packet, boolean forceDeny, boolean bypass) {
      if (packet != null && packet.id() != null) {
         RiptideResourcePackTruthGuard.UrlVerdict url = inspectUrl(packet.url());
         RiptideResourcePackTruthGuard.Verdict verdict;
         if (forceDeny) {
            verdict = RiptideResourcePackTruthGuard.Verdict.of(RiptideResourcePackTruthGuard.ResponseKind.DECLINE, "resource pack auto-deny");
         } else if (bypass) {
            if (url.invalid) {
               verdict = RiptideResourcePackTruthGuard.Verdict.of(RiptideResourcePackTruthGuard.ResponseKind.INVALID_URL, url.reason);
            } else if (!url.impossible && !url.blockedLocal) {
               verdict = RiptideResourcePackTruthGuard.Verdict.of(
                  RiptideResourcePackTruthGuard.ResponseKind.BYPASS_SUCCESS, "resource pack bypass compatibility response"
               );
            } else {
               verdict = RiptideResourcePackTruthGuard.Verdict.of(RiptideResourcePackTruthGuard.ResponseKind.FAILED_DOWNLOAD, url.reason);
            }
         } else if (url.invalid) {
            verdict = RiptideResourcePackTruthGuard.Verdict.of(RiptideResourcePackTruthGuard.ResponseKind.INVALID_URL, url.reason);
         } else {
            if (!url.impossible && !url.blockedLocal) {
               GUARDED_PACKS.remove(packet.id());
               return RiptideResourcePackTruthGuard.Verdict.pass();
            }

            verdict = RiptideResourcePackTruthGuard.Verdict.of(RiptideResourcePackTruthGuard.ResponseKind.FAILED_DOWNLOAD, url.reason);
         }

         if (verdict.kind() == RiptideResourcePackTruthGuard.ResponseKind.PASS) {
            GUARDED_PACKS.remove(packet.id());
         } else {
            GUARDED_PACKS.put(packet.id(), new RiptideResourcePackTruthGuard.GuardedPack(verdict.kind(), verdict.reason()));
         }

         return verdict;
      } else {
         return RiptideResourcePackTruthGuard.Verdict.pass();
      }
   }

   public static boolean shouldCancelOutboundStatus(ServerboundResourcePackPacket packet) {
      if (packet != null && packet.id() != null && packet.action() != null) {
         RiptideResourcePackTruthGuard.GuardedPack guarded = GUARDED_PACKS.get(packet.id());
         if (guarded == null) {
            return false;
         } else if (guarded.kind() == RiptideResourcePackTruthGuard.ResponseKind.BYPASS_SUCCESS) {
            if (packet.action() == Action.SUCCESSFULLY_LOADED) {
               GUARDED_PACKS.remove(packet.id(), guarded);
            }

            return false;
         } else {
            return isImpossibleSuccess(packet.action());
         }
      } else {
         return false;
      }
   }

   public static void onPop(UUID id) {
      if (id == null) {
         GUARDED_PACKS.clear();
      } else {
         GUARDED_PACKS.remove(id);
      }
   }

   public static void clearAll() {
      GUARDED_PACKS.clear();
   }

   private static boolean isImpossibleSuccess(Action action) {
      return action == Action.DOWNLOADED || action == Action.SUCCESSFULLY_LOADED;
   }

   private static RiptideResourcePackTruthGuard.UrlVerdict inspectUrl(String rawUrl) {
      if (rawUrl != null && !rawUrl.isBlank()) {
         URI uri;
         try {
            uri = new URI(rawUrl.trim());
         } catch (URISyntaxException var6) {
            return RiptideResourcePackTruthGuard.UrlVerdict.invalid("malformed resource pack URL");
         }

         String scheme = uri.getScheme();
         if (scheme == null) {
            return RiptideResourcePackTruthGuard.UrlVerdict.invalid("resource pack URL has no scheme");
         } else {
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
               return RiptideResourcePackTruthGuard.UrlVerdict.invalid("unsupported resource pack URL scheme");
            } else {
               String host = uri.getHost();
               if (host != null && !host.isBlank()) {
                  int port = uri.getPort();
                  if (port == 0) {
                     return RiptideResourcePackTruthGuard.UrlVerdict.impossible("resource pack URL uses impossible port 0");
                  } else {
                     return RiptideProtector.shouldBlockLocalUrls() && RiptideProtectorLocalAddressUtil.shouldBlock(host)
                        ? RiptideResourcePackTruthGuard.UrlVerdict.blockedLocal("resource pack URL points to a protected local/private address")
                        : RiptideResourcePackTruthGuard.UrlVerdict.ok();
                  }
               } else {
                  return RiptideResourcePackTruthGuard.UrlVerdict.invalid("resource pack URL has no host");
               }
            }
         }
      } else {
         return RiptideResourcePackTruthGuard.UrlVerdict.invalid("empty resource pack URL");
      }
   }

   private record GuardedPack(RiptideResourcePackTruthGuard.ResponseKind kind, String reason) {
   }

   public static enum ResponseKind {
      PASS,
      BYPASS_SUCCESS,
      DECLINE,
      FAILED_DOWNLOAD,
      INVALID_URL;
   }

   private static final class UrlVerdict {
      private static final RiptideResourcePackTruthGuard.UrlVerdict OK = new RiptideResourcePackTruthGuard.UrlVerdict(false, false, false, "");
      private final boolean invalid;
      private final boolean impossible;
      private final boolean blockedLocal;
      private final String reason;

      private UrlVerdict(boolean invalid, boolean impossible, boolean blockedLocal, String reason) {
         this.invalid = invalid;
         this.impossible = impossible;
         this.blockedLocal = blockedLocal;
         this.reason = reason == null ? "" : reason;
      }

      private static RiptideResourcePackTruthGuard.UrlVerdict ok() {
         return OK;
      }

      private static RiptideResourcePackTruthGuard.UrlVerdict invalid(String reason) {
         return new RiptideResourcePackTruthGuard.UrlVerdict(true, false, false, reason);
      }

      private static RiptideResourcePackTruthGuard.UrlVerdict impossible(String reason) {
         return new RiptideResourcePackTruthGuard.UrlVerdict(false, true, false, reason);
      }

      private static RiptideResourcePackTruthGuard.UrlVerdict blockedLocal(String reason) {
         return new RiptideResourcePackTruthGuard.UrlVerdict(false, false, true, reason);
      }
   }

   public static final class Verdict {
      private static final RiptideResourcePackTruthGuard.Verdict PASS = new RiptideResourcePackTruthGuard.Verdict(
         RiptideResourcePackTruthGuard.ResponseKind.PASS, ""
      );
      private final RiptideResourcePackTruthGuard.ResponseKind kind;
      private final String reason;

      private Verdict(RiptideResourcePackTruthGuard.ResponseKind kind, String reason) {
         this.kind = kind;
         this.reason = reason == null ? "" : reason;
      }

      public static RiptideResourcePackTruthGuard.Verdict pass() {
         return PASS;
      }

      public static RiptideResourcePackTruthGuard.Verdict of(RiptideResourcePackTruthGuard.ResponseKind kind, String reason) {
         return kind == RiptideResourcePackTruthGuard.ResponseKind.PASS ? PASS : new RiptideResourcePackTruthGuard.Verdict(kind, reason);
      }

      public RiptideResourcePackTruthGuard.ResponseKind kind() {
         return this.kind;
      }

      public String reason() {
         return this.reason;
      }

      public boolean shouldCancelVanilla() {
         return this.kind != RiptideResourcePackTruthGuard.ResponseKind.PASS;
      }
   }
}
