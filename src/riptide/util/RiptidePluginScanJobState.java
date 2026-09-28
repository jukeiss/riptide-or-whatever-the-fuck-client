package riptide.util;

final class RiptidePluginScanJobState {
   private static final int MAX_SEND_ATTEMPTS = 4;
   private final long generation;
   private final String serverAddress;
   private boolean finalized;

   RiptidePluginScanJobState(long generation, String serverAddress) {
      this.generation = generation;
      this.serverAddress = serverAddress == null ? "" : serverAddress;
   }

   boolean matches(long expectedGeneration, String currentAddress) {
      return !this.finalized && this.generation == expectedGeneration && this.serverAddress.equals(currentAddress == null ? "" : currentAddress);
   }

   boolean canRetry(int completedAttempts) {
      return !this.finalized && completedAttempts + 1 < 4;
   }

   boolean beginFinalize() {
      if (this.finalized) {
         return false;
      } else {
         this.finalized = true;
         return true;
      }
   }

   boolean requiresPartialResult(int runtimeErrors, int failedProbes, int pendingProbes) {
      return runtimeErrors > 0 || failedProbes > 0 || pendingProbes > 0;
   }

   boolean isFinalized() {
      return this.finalized;
   }
}
