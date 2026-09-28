package riptide.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class RiptideWelcomeGate {
   private RiptideWelcomeGate() {
   }

   public static boolean shouldShow(RiptideConfig config) {
      if (config == null) {
         return false;
      } else {
         String current = currentInstallIdentity();
         String shown = config.welcomeInstallIdentity == null ? "" : config.welcomeInstallIdentity;
         return !current.equals(shown);
      }
   }

   public static void markShown(RiptideConfig config) {
      if (config != null) {
         config.welcomeShown = true;
         config.welcomeInstallIdentity = currentInstallIdentity();
         config.save();
      }
   }

   private static String currentInstallIdentity() {
      try {
         Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer("riptide");
         if (optional.isEmpty()) {
            return "riptide:unknown";
         } else {
            ModContainer container = optional.get();
            StringBuilder id = new StringBuilder();
            id.append(container.getMetadata().getId()).append(':').append(container.getMetadata().getVersion().getFriendlyString());

            for (Path path : container.getOrigin().getPaths()) {
               appendPathIdentity(id, path);
            }

            return id.toString();
         }
      } catch (Throwable var5) {
         return "riptide:unknown";
      }
   }

   private static void appendPathIdentity(StringBuilder id, Path path) {
      if (path != null) {
         try {
            Path normalized = path.toAbsolutePath().normalize();
            id.append('|').append(normalized.getFileName());
            if (Files.isRegularFile(normalized)) {
               id.append(':').append(Files.size(normalized)).append(':').append(Files.getLastModifiedTime(normalized).toMillis());
            } else if (Files.isDirectory(normalized)) {
               id.append(":dir:").append(Files.getLastModifiedTime(normalized).toMillis());
            }
         } catch (Throwable var3) {
            id.append('|').append(path);
         }
      }
   }
}
