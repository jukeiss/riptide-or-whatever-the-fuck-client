package riptide.util;

import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;

public final class RiptideNetworkCaptureState {
   public static final int PLAINTEXT = 1;
   public static final int PAYLOAD = 2;
   public static final byte[] EMPTY_BYTES = new byte[0];
   private static final int MODE_MASK = 3;
   private static volatile long state;
   private static volatile long expirationDeadlineMs;
   private static final ThreadLocal<int[]> CODEC_SUPPRESSION_DEPTH = new ThreadLocal<>();

   private RiptideNetworkCaptureState() {
   }

   public static long state() {
      return state;
   }

   public static long codecState() {
      long current = state;
      if (!capturesPayloads(current)) {
         return current;
      } else {
         int[] depth = CODEC_SUPPRESSION_DEPTH.get();
         return depth != null && depth[0] > 0 ? current & -3L : current;
      }
   }

   public static void beginMultiCodecSuppression() {
      int[] depth = CODEC_SUPPRESSION_DEPTH.get();
      if (depth == null) {
         depth = new int[1];
         CODEC_SUPPRESSION_DEPTH.set(depth);
      }

      depth[0]++;
   }

   public static void endMultiCodecSuppression() {
      int[] depth = CODEC_SUPPRESSION_DEPTH.get();
      if (depth != null) {
         if (--depth[0] <= 0) {
            CODEC_SUPPRESSION_DEPTH.remove();
         }
      }
   }

   public static void clearCodecSuppression() {
      CODEC_SUPPRESSION_DEPTH.remove();
   }

   public static int mode(long capturedState) {
      return (int)capturedState & 3;
   }

   public static boolean capturesPlaintext(long capturedState) {
      return (mode(capturedState) & 1) != 0;
   }

   public static boolean capturesPayloads(long capturedState) {
      return (mode(capturedState) & 2) != 0;
   }

   public static boolean capturesPlaintext() {
      return capturesPlaintext(state);
   }

   public static boolean capturesPayloads() {
      return capturesPayloads(state);
   }

   public static void refreshCurrent() {
      refresh(RiptideModule.get());
   }

   public static void refreshIfDue(RiptideModule module) {
      long deadline = expirationDeadlineMs;
      if (deadline > 0L && System.currentTimeMillis() > deadline) {
         expirationDeadlineMs = 0L;
         refresh(module);
      }
   }

   public static void refresh(RiptideModule module) {
      int next = 0;
      if (module != null && !PackHideState.isHardLocked()) {
         if (module.shouldCapturePacketPlaintext()) {
            next |= 1;
         }

         if (module.shouldCapturePayloadBytes()) {
            next |= 2;
         }

         long deadline = module.passivePayloadCaptureDeadlineMs();
         expirationDeadlineMs = deadline > System.currentTimeMillis() ? deadline : 0L;
      } else {
         expirationDeadlineMs = 0L;
      }

      publish(next);
   }

   public static void disable() {
      expirationDeadlineMs = 0L;
      publish(0);
   }

   private static void publish(int nextMode) {
      long previous = state;
      if (mode(previous) != nextMode) {
         synchronized (RiptideNetworkCaptureState.class) {
            previous = state;
            if (mode(previous) == nextMode) {
               return;
            }

            long nextEpoch = (previous >>> 2) + 1L;
            state = nextEpoch << 2 | nextMode & 3;
         }

         RiptideRuntimeActivity.publish(1024L, nextMode != 0);
      }
   }
}
