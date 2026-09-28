package riptide.util.multi;

import java.lang.reflect.Method;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;

final class MultiViaCompat {
   private MultiViaCompat() {
   }

   static MultiViaCompat.Target captureSelectedTarget() {
      if (!FabricLoader.getInstance().isModLoaded("viafabricplus")) {
         return new MultiViaCompat.Target(false, null, "Native");
      } else {
         try {
            Class<?> apiClass = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object api = apiClass.getMethod("getImpl").invoke(null);
            Method targetMethod = api.getClass().getMethod("getTargetVersion");
            Object target = targetMethod.invoke(api);
            return new MultiViaCompat.Target(true, target, String.valueOf(target));
         } catch (ReflectiveOperationException var4) {
            return new MultiViaCompat.Target(true, null, "ViaFabricPlus");
         }
      }
   }

   static MultiViaCompat.Target captureServerTarget(ServerData serverData) {
      if (serverData != null && FabricLoader.getInstance().isModLoaded("viafabricplus")) {
         try {
            Class<?> apiClass = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object api = apiClass.getMethod("getImpl").invoke(null);
            Object target = api.getClass().getMethod("getServerVersion", ServerData.class).invoke(api, serverData);
            return new MultiViaCompat.Target(true, target, String.valueOf(target));
         } catch (ReflectiveOperationException var4) {
            return new MultiViaCompat.Target(true, null, "ViaFabricPlus Auto Detect");
         }
      } else {
         return new MultiViaCompat.Target(false, null, "Native");
      }
   }

   static boolean isAutoDetect(MultiViaCompat.Target target) {
      if (target == null) {
         return false;
      } else {
         String label = target.label().toLowerCase(Locale.ROOT);
         return label.contains("auto") && label.contains("detect");
      }
   }

   static String validateSelectedTarget(MultiViaCompat.Target target) {
      if (target != null && target.present()) {
         String label = target.label().toLowerCase(Locale.ROOT);
         if (label.contains("bedrock")) {
            return "Multi does not support ViaFabricPlus Bedrock targets";
         } else {
            return label.contains("classicube") ? "Multi supports offline Classic, not authenticated ClassiCube" : "";
         }
      } else {
         return "";
      }
   }

   static void applyTarget(Connection connection, MultiViaCompat.Target target) {
      if (connection != null && target != null && target.present() && target.version() != null) {
         if (!isAutoDetect(target)) {
            try {
               Class<?> access = Class.forName("com.viaversion.viafabricplus.injection.access.core.IConnection");
               Class<?> protocol = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
               access.getMethod("viaFabricPlus$setTargetVersion", protocol).invoke(connection, target.version());
            } catch (ReflectiveOperationException var4) {
            }
         }
      }
   }

   record Target(boolean present, Object version, String label) {
   }
}
