package riptide.util.login;

import java.util.LinkedHashMap;
import java.util.Map;
import riptide.api.custommenu.CustomMenuButton;
import riptide.api.custommenu.CustomMenuInput;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;
import riptide.util.macro.CustomMenuActionSupport;

public final class AutoLoginEngine {
   private final AutoLoginHost host;
   private volatile AuthMeChatLogin.Detection pendingChat = AuthMeChatLogin.Detection.NONE;
   private volatile long resetRequestedAt;
   private volatile AutoLoginConfig config = AutoLoginConfig.playerDefaults();
   private volatile AutoLoginEngine.Phase phase = AutoLoginEngine.Phase.IDLE;
   private long startedAt;
   private long spawnedAt;
   private long nextChatSendAt;
   private long screenRetryAt;
   private long settleAt;
   private long owedLoginAt;
   private boolean owedLogin;
   private int chatAttempts;
   private boolean gaveUpNoted;
   private boolean passwordAsked;
   private long answeredGen = Long.MIN_VALUE;
   private String registerToken = "";
   private String loginToken = "";

   public AutoLoginEngine(AutoLoginHost host) {
      this.host = host;
   }

   public void configure(AutoLoginConfig newConfig) {
      if (newConfig != null) {
         this.config = newConfig;
      }
   }

   public void reset(long now) {
      this.pendingChat = AuthMeChatLogin.Detection.NONE;
      this.resetRequestedAt = now == 0L ? 1L : now;
   }

   public void onChatLine(String line) {
      AuthMeChatLogin.Detection detection = AuthMeChatLogin.detect(line);
      if (detection.kind() != AuthMeChatLogin.Kind.NONE) {
         this.pendingChat = detection;
      }
   }

   public AutoLoginEngine.Phase phase() {
      return this.phase;
   }

   public boolean finished() {
      AutoLoginEngine.Phase current = this.phase;
      return current == AutoLoginEngine.Phase.DONE || current == AutoLoginEngine.Phase.GAVE_UP;
   }

   public int chatAttempts() {
      return this.chatAttempts;
   }

   public void tick(long now) {
      this.applyPendingReset();
      if (this.phase != AutoLoginEngine.Phase.IDLE && !this.finished()) {
         if (now - this.startedAt >= this.config.windowMs()) {
            this.phase = AutoLoginEngine.Phase.GAVE_UP;
         } else if (!this.tickScreen(now)) {
            this.tickChat(now);
         }
      }
   }

   private void applyPendingReset() {
      long requested = this.resetRequestedAt;
      if (requested != 0L) {
         this.resetRequestedAt = 0L;
         this.startedAt = requested;
         this.spawnedAt = 0L;
         this.nextChatSendAt = 0L;
         this.screenRetryAt = 0L;
         this.settleAt = 0L;
         this.owedLoginAt = 0L;
         this.owedLogin = false;
         this.chatAttempts = 0;
         this.gaveUpNoted = false;
         this.passwordAsked = false;
         this.answeredGen = Long.MIN_VALUE;
         this.registerToken = "";
         this.loginToken = "";
         this.phase = AutoLoginEngine.Phase.WATCHING;
      }
   }

   private boolean tickScreen(long now) {
      CustomMenuSnapshot snapshot = this.host.customMenu();
      if (snapshot == null) {
         this.answeredGen = Long.MIN_VALUE;
         return false;
      } else if (this.host.screenOwnedElsewhere()) {
         return true;
      } else if (snapshot.generation() == this.answeredGen) {
         return true;
      } else if (now < this.screenRetryAt) {
         return true;
      } else {
         boolean hasText = false;

         for (CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == CustomMenuInput.Kind.TEXT) {
               hasText = true;
               break;
            }
         }

         String password = this.host.password();
         if (hasText && password.isEmpty()) {
            this.askForPassword(snapshot.title());
            return true;
         } else {
            Map<String, String> values;
            CustomMenuButton button;
            try {
               CustomMenuButton accept = hasText ? null : CustomMenuActionSupport.acceptButton(snapshot.buttons());
               button = accept != null ? accept : CustomMenuActionSupport.loginButton(snapshot.buttons());
               if (button == null) {
                  this.screenRetryAt = now + this.config.screenRetryMs();
                  return true;
               }

               values = new LinkedHashMap<>();

               for (CustomMenuInput inputx : snapshot.inputs()) {
                  values.put(inputx.key(), inputx.kind() == CustomMenuInput.Kind.TEXT ? password : inputx.initialValue());
               }
            } catch (RuntimeException var11) {
               this.screenRetryAt = now + this.config.screenRetryMs();
               return true;
            }

            if (!this.host.submitCustomMenu(snapshot, new CustomMenuSubmission(values, button))) {
               this.screenRetryAt = now + this.config.screenRetryMs();
               return true;
            } else {
               this.answeredGen = snapshot.generation();
               if (hasText) {
                  this.phase = AutoLoginEngine.Phase.LOGGING_IN;
                  this.settleAt = now + this.config.chatResendMs();
               }

               return true;
            }
         }
      }
   }

   private void tickChat(long now) {
      if (!this.host.spawnedInWorld()) {
         this.spawnedAt = 0L;
      } else {
         if (this.spawnedAt == 0L) {
            this.spawnedAt = now;
         }

         if (now - this.spawnedAt >= this.config.chatDelayMs()) {
            AuthMeChatLogin.Detection detection = this.pendingChat;
            if (detection.kind() != AuthMeChatLogin.Kind.NONE) {
               if (now >= this.nextChatSendAt) {
                  if (this.chatAttempts >= this.config.maxChatAttempts()) {
                     this.giveUp();
                  } else {
                     String password = this.host.password();
                     if (password.isEmpty()) {
                        this.askForPassword("AuthMe login");
                     } else if (this.host.canSendChat()) {
                        this.pendingChat = AuthMeChatLogin.Detection.NONE;
                        if (!this.host.sendCommandLine(detection.commandLine(password))) {
                           this.pendingChat = detection;
                           this.nextChatSendAt = now + 250L;
                        } else {
                           this.chatAttempts++;
                           this.nextChatSendAt = now + this.config.chatResendMs();
                           if (detection.kind() == AuthMeChatLogin.Kind.REGISTER) {
                              this.registerToken = detection.command();
                              this.phase = AutoLoginEngine.Phase.REGISTERING;
                              this.owedLogin = true;
                              this.owedLoginAt = now + this.config.registerFollowUpMs();
                           } else {
                              this.loginToken = detection.command();
                              this.phase = AutoLoginEngine.Phase.LOGGING_IN;
                              this.owedLogin = false;
                              this.settleAt = now + this.config.chatResendMs();
                           }
                        }
                     }
                  }
               }
            } else {
               if (this.owedLogin && now >= this.owedLoginAt) {
                  this.sendSpeculativeLogin(now);
               } else if (this.phase == AutoLoginEngine.Phase.LOGGING_IN && this.settleAt != 0L && now >= this.settleAt) {
                  this.phase = AutoLoginEngine.Phase.DONE;
               }
            }
         }
      }
   }

   private void sendSpeculativeLogin(long now) {
      this.owedLogin = false;
      if (this.chatAttempts >= this.config.maxChatAttempts()) {
         this.giveUp();
      } else {
         String password = this.host.password();
         if (!password.isEmpty() && this.host.canSendChat()) {
            String token = AuthMeChatLogin.loginCommandFor(this.registerToken, this.loginToken);
            if (!this.host.sendCommandLine(token + " " + password)) {
               this.owedLogin = true;
               this.owedLoginAt = now + 250L;
            } else {
               this.chatAttempts++;
               this.nextChatSendAt = now + this.config.chatResendMs();
               this.phase = AutoLoginEngine.Phase.LOGGING_IN;
               this.settleAt = now + this.config.chatResendMs();
            }
         }
      }
   }

   private void giveUp() {
      if (!this.gaveUpNoted) {
         this.gaveUpNoted = true;
         this.host.note("Auto login failed after " + this.config.maxChatAttempts() + " attempts - check the password.");
      }

      this.phase = AutoLoginEngine.Phase.GAVE_UP;
   }

   private void askForPassword(String context) {
      if (!this.passwordAsked) {
         this.passwordAsked = true;
         this.host.needsPassword(context == null ? "" : context);
      }
   }

   public static enum Phase {
      IDLE,
      WATCHING,
      REGISTERING,
      LOGGING_IN,
      DONE,
      GAVE_UP;
   }
}
