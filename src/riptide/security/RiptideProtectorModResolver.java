package riptide.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;

public final class RiptideProtectorModResolver {
   private RiptideProtectorModResolver() {
   }

   public static LinkedHashSet<String> modsFromStacktrace() {
      LinkedHashSet<String> mods = new LinkedHashSet<>();
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();

      for (int i = 3; i < stack.length; i++) {
         String className = stack[i].getClassName();
         if ("net.minecraft.client.main.Main".equals(className)) {
            break;
         }

         try {
            String mod = modFromClass(Class.forName(className, false, RiptideProtectorModResolver.class.getClassLoader()));
            if ("fabricloader".equals(mod)) {
               break;
            }

            if (mod != null) {
               mods.add(mod);
            }
         } catch (Throwable var5) {
         }
      }

      return mods;
   }

   public static Set<String> dependenciesFor(String modId) {
      Set<String> dependencies = new HashSet<>();
      Set<String> queued = new HashSet<>();
      ArrayDeque<String> stack = new ArrayDeque<>();
      if (modId != null && !modId.isBlank()) {
         stack.push(modId);
         queued.add(modId);

         while (!stack.isEmpty()) {
            Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer(stack.pop());
            if (!optional.isEmpty()) {
               ModContainer container = optional.get();
               String id = container.getMetadata().getId();
               if (!isCore(id)) {
                  if (!id.equals(modId)) {
                     dependencies.add(id);
                  }

                  for (ModContainer contained : container.getContainedMods()) {
                     String containedId = contained.getMetadata().getId();
                     if (queued.add(containedId)) {
                        stack.push(containedId);
                     }
                  }

                  for (ModDependency dependency : container.getMetadata().getDependencies()) {
                     if (dependency.getKind() != Kind.BREAKS) {
                        String dependencyId = dependency.getModId();
                        if (queued.add(dependencyId)) {
                           stack.push(dependencyId);
                        }
                     }
                  }
               }
            }
         }

         dependencies.remove(modId);
         return dependencies;
      } else {
         return dependencies;
      }
   }

   public static String modFromClass(Class<?> type) {
      if (type != null && type.getProtectionDomain() != null && type.getProtectionDomain().getCodeSource() != null) {
         try {
            URI uri = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            String modId = modFromPath(path);
            return rootModId(modId);
         } catch (Throwable var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String modFromPath(Path path) {
      if (path == null) {
         return null;
      } else {
         try {
            if (Files.isDirectory(path)) {
               for (Path candidate : new Path[]{
                  path.resolve("fabric.mod.json"),
                  path.resolve("../resources/main/fabric.mod.json").normalize(),
                  path.resolve("../../resources/main/fabric.mod.json").normalize(),
                  path.resolve("../../../resources/main/fabric.mod.json").normalize()
               }) {
                  String modId = modIdFromJson(candidate);
                  if (modId != null) {
                     return modId;
                  }
               }

               return null;
            } else {
               String var6;
               try (JarFile jar = new JarFile(path.toFile())) {
                  JarEntry entry = jar.getJarEntry("fabric.mod.json");
                  if (entry == null) {
                     return null;
                  }

                  try (
                     InputStream in = jar.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                  ) {
                     JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                     var6 = root.has("id") ? root.get("id").getAsString() : null;
                  }
               }

               return var6;
            }
         } catch (Throwable var13) {
            return null;
         }
      }
   }

   private static String modIdFromJson(Path path) {
      if (path != null && Files.isRegularFile(path)) {
         try {
            String var3;
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               var3 = root.has("id") ? root.get("id").getAsString() : null;
            }

            return var3;
         } catch (Throwable var6) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String rootModId(String modId) {
      if (modId != null && !modId.isBlank()) {
         Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer(modId);
         if (optional.isEmpty()) {
            return modId;
         } else {
            ModContainer current = optional.get();

            for (Optional<ModContainer> parent = current.getContainingMod(); parent.isPresent(); parent = current.getContainingMod()) {
               current = parent.get();
            }

            return current.getMetadata().getId();
         }
      } else {
         return null;
      }
   }

   private static boolean isCore(String modId) {
      return "minecraft".equals(modId) || "java".equals(modId) || "fabricloader".equals(modId) || "fabric-loader".equals(modId);
   }
}
