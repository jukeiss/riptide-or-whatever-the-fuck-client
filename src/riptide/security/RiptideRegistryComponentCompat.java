package riptide.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public final class RiptideRegistryComponentCompat {
   private static final ThreadLocal<Integer> REMOTE_COMPONENT_BAKE_DEPTH = ThreadLocal.withInitial(() -> 0);
   private static final Set<String> REPORTED_MISSING_TRIM_MATERIALS = ConcurrentHashMap.newKeySet();
   private static final Set<String> REPORTED_MISSING_COMPONENTS = ConcurrentHashMap.newKeySet();

   private RiptideRegistryComponentCompat() {
   }

   public static void beginRemoteComponentBake() {
      REMOTE_COMPONENT_BAKE_DEPTH.set(REMOTE_COMPONENT_BAKE_DEPTH.get() + 1);
   }

   public static void endRemoteComponentBake() {
      int depth = REMOTE_COMPONENT_BAKE_DEPTH.get() - 1;
      if (depth <= 0) {
         REMOTE_COMPONENT_BAKE_DEPTH.remove();
      } else {
         REMOTE_COMPONENT_BAKE_DEPTH.set(depth);
      }
   }

   public static boolean shouldSkipMissingDelayedHolder(ResourceKey<?> valueKey, Throwable error) {
      if (valueKey == null || error == null) {
         return false;
      } else if (REMOTE_COMPONENT_BAKE_DEPTH.get() <= 0) {
         return false;
      } else if (!valueKey.isFor(Registries.TRIM_MATERIAL)) {
         return false;
      } else {
         String message = error.getMessage();
         return error instanceof IllegalStateException && message != null && message.startsWith("Missing element ResourceKey[");
      }
   }

   public static void reportSkippedMissingTrimMaterial(ResourceKey<?> valueKey) {
      if (valueKey != null) {
         String id = valueKey.identifier().toString();
         if (REPORTED_MISSING_TRIM_MATERIALS.add(id)) {
            riptide.RiptideClientAddon.LOG
               .warn("[Riptide] Server registry is missing trim material '{}'; omitted that item component so configuration can continue.", id);
         }
      }
   }

   public static boolean shouldSkipMissingComponentData(Throwable error) {
      if (error == null) {
         return false;
      } else if (REMOTE_COMPONENT_BAKE_DEPTH.get() <= 0) {
         return false;
      } else if (!(error instanceof IllegalStateException)) {
         return false;
      } else {
         String message = error.getMessage();
         return message == null ? false : message.startsWith("Missing tag ") || message.startsWith("Missing element ");
      }
   }

   public static void reportSkippedMissingComponent(Throwable error) {
      String message = error != null && error.getMessage() != null ? error.getMessage() : "unknown";
      if (REPORTED_MISSING_COMPONENTS.add(message)) {
         riptide.RiptideClientAddon.LOG
            .warn("[Riptide] Server registry is missing data for a delayed item component ({}); omitted that component so configuration can continue.", message);
      }
   }
}
