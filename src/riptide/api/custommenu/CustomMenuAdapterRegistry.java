package riptide.api.custommenu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.protocol.Packet;
import riptide.addons.AddonManager;
import riptide.util.custommenu.VanillaDialogAdapter;

public final class CustomMenuAdapterRegistry {
   private static final Map<String, CustomMenuAdapterRegistry.Registered> ADAPTERS = new LinkedHashMap<>();
   private static volatile CustomMenuAdapterRegistry.RegistrySnapshot SNAPSHOT;

   private CustomMenuAdapterRegistry() {
   }

   public static synchronized boolean register(CustomMenuAdapter adapter) {
      if (adapter != null && adapter.id() != null && !adapter.id().isBlank()) {
         String id = adapter.id().trim().toLowerCase(Locale.ROOT);
         if (ADAPTERS.containsKey(id)) {
            return false;
         } else {
            ADAPTERS.put(id, new CustomMenuAdapterRegistry.Registered(adapter, AddonManager.currentAddonId()));
            publishSnapshot();
            return true;
         }
      } else {
         return false;
      }
   }

   public static synchronized void unregisterAddon(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         if (ADAPTERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue().owner()))) {
            publishSnapshot();
         }
      }
   }

   public static boolean acceptsInbound(Packet<?> packet) {
      if (packet == null) {
         return false;
      } else {
         for (CustomMenuAdapterRegistry.Registered registered : SNAPSHOT.ordered()) {
            try {
               if (registered.adapter().acceptsInbound(packet)) {
                  return true;
               }
            } catch (Throwable var6) {
               return true;
            }
         }

         return false;
      }
   }

   public static CustomMenuEvent inspect(Packet<?> packet, String phase) {
      if (packet == null) {
         return CustomMenuEvent.NONE;
      } else {
         for (CustomMenuAdapterRegistry.Registered registered : SNAPSHOT.ordered()) {
            try {
               if (registered.adapter().acceptsInbound(packet)) {
                  CustomMenuEvent event = registered.adapter().inspectInbound(packet, phase);
                  if (event != null && event.type() != CustomMenuEvent.Type.NONE) {
                     return event;
                  }
               }
            } catch (Throwable var7) {
               riptide.RiptideClientAddon.LOG.warn("[CustomMenus] Adapter '{}' failed while reading a packet", registered.adapter().id(), var7);
            }
         }

         return CustomMenuEvent.NONE;
      }
   }

   public static CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
      if (snapshot == null) {
         return CustomMenuSubmitResult.failure("No custom menu is open");
      } else {
         CustomMenuAdapterRegistry.Registered registered = SNAPSHOT.byId().get(snapshot.adapterId().toLowerCase(Locale.ROOT));
         if (registered == null) {
            return CustomMenuSubmitResult.failure("Custom menu adapter is unavailable");
         } else {
            try {
               CustomMenuSubmitResult result = registered.adapter().submit(snapshot, submission);
               return result == null ? CustomMenuSubmitResult.failure("Custom menu adapter returned no result") : result;
            } catch (Throwable var4) {
               riptide.RiptideClientAddon.LOG
                  .warn("[CustomMenus] Adapter '{}' failed while submitting a form ({})", registered.adapter().id(), var4.getClass().getSimpleName());
               return CustomMenuSubmitResult.failure("Custom menu adapter failed");
            }
         }
      }
   }

   public static List<String> ids() {
      return SNAPSHOT.ids();
   }

   private static void publishSnapshot() {
      CustomMenuAdapterRegistry.Registered[] ordered = ADAPTERS.values().toArray(CustomMenuAdapterRegistry.Registered[]::new);
      SNAPSHOT = new CustomMenuAdapterRegistry.RegistrySnapshot(ordered, Map.copyOf(ADAPTERS), List.copyOf(ADAPTERS.keySet()));
   }

   static {
      ADAPTERS.put("minecraft:dialog", new CustomMenuAdapterRegistry.Registered(new VanillaDialogAdapter(), "riptide"));
      publishSnapshot();
   }

   private record Registered(CustomMenuAdapter adapter, String owner) {
   }

   private record RegistrySnapshot(CustomMenuAdapterRegistry.Registered[] ordered, Map<String, CustomMenuAdapterRegistry.Registered> byId, List<String> ids) {
   }
}
