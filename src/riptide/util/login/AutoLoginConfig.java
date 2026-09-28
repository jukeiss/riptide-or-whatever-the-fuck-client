package riptide.util.login;

public record AutoLoginConfig(long windowMs, long chatDelayMs, long chatResendMs, int maxChatAttempts, long screenRetryMs, long registerFollowUpMs) {
   public AutoLoginConfig(long windowMs, long chatDelayMs, long chatResendMs, int maxChatAttempts, long screenRetryMs, long registerFollowUpMs) {
      windowMs = clamp(windowMs, 1000L, 300000L);
      chatDelayMs = clamp(chatDelayMs, 0L, 60000L);
      chatResendMs = clamp(chatResendMs, 250L, 30000L);
      maxChatAttempts = (int)clamp(maxChatAttempts, 1L, 20L);
      screenRetryMs = clamp(screenRetryMs, 100L, 10000L);
      registerFollowUpMs = clamp(registerFollowUpMs, 250L, 30000L);
      this.windowMs = windowMs;
      this.chatDelayMs = chatDelayMs;
      this.chatResendMs = chatResendMs;
      this.maxChatAttempts = maxChatAttempts;
      this.screenRetryMs = screenRetryMs;
      this.registerFollowUpMs = registerFollowUpMs;
   }

   public static AutoLoginConfig multiDefaults() {
      return new AutoLoginConfig(40000L, 2000L, 2500L, 4, 1000L, 3000L);
   }

   public static AutoLoginConfig playerDefaults() {
      return new AutoLoginConfig(10000L, 2000L, 2500L, 4, 1000L, 3000L);
   }

   private static long clamp(long value, long low, long high) {
      return Math.max(low, Math.min(high, value));
   }
}
