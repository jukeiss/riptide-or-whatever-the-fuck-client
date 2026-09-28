package riptide.util;

final class RiptideConfigWriter {
   private static final String CONFIG_KEY = "config:" + RiptideConfig.configFile().getAbsolutePath();

   private RiptideConfigWriter() {
   }

   static void request(RiptideConfig config) {
      SaveCoordinator.requestConfigSave(config);
   }

   static void captureAndEnqueue(RiptideConfig source) {
      RiptideConfig snapshot;
      try {
         snapshot = RiptideConfigSnapshot.copyForPersistence(source);
      } catch (Throwable var3) {
         riptide.RiptideClientAddon.LOG.error("Failed to capture Riptide config", var3);
         return;
      }

      enqueueSnapshot(snapshot);
      RiptideConfig.onPersistenceSnapshot(snapshot);
   }

   static void enqueueSnapshot(RiptideConfig snapshot) {
      SaveCoordinator.enqueueLatest(CONFIG_KEY, () -> {
         try {
            RiptideConfig.writeToDisk(RiptideConfig.toJson(snapshot));
         } catch (Throwable var2) {
            riptide.RiptideClientAddon.LOG.error("Failed to serialize Riptide config", var2);
         }
      });
   }

   static void capturePendingNow() {
      SaveCoordinator.capturePendingConfigNow();
   }

   static void flushBlocking(long timeoutMs) {
      SaveCoordinator.flushBlocking(timeoutMs);
   }
}
