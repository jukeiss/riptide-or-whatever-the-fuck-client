package riptide.api;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import riptide.addons.AddonManager;
import riptide.api.custommenu.CustomMenuAdapter;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.event.AddonEvents;
import riptide.api.hud.HudElementProvider;
import riptide.api.hud.HudElements;
import riptide.api.macro.MacroActionEntry;
import riptide.api.macro.MacroActionRegistry;
import riptide.api.macro.MacroPresetRegistry;
import riptide.commands.Command;
import riptide.commands.RiptideCommands;
import riptide.modules.Module;
import riptide.modules.ModuleCategory;
import riptide.modules.ModuleRegistry;
import riptide.util.macro.MacroAction;

public final class RiptideAddons {
   private RiptideAddons() {
   }

   public static RiptideAddons.Modules modules() {
      return RiptideAddons.Modules.INSTANCE;
   }

   public static RiptideAddons.Commands commands() {
      return RiptideAddons.Commands.INSTANCE;
   }

   public static RiptideAddons.MacroActions macroActions() {
      return RiptideAddons.MacroActions.INSTANCE;
   }

   public static RiptideAddons.Presets presets() {
      return RiptideAddons.Presets.INSTANCE;
   }

   public static RiptideAddons.Hud hud() {
      return RiptideAddons.Hud.INSTANCE;
   }

   public static RiptideAddons.Events events() {
      return RiptideAddons.Events.INSTANCE;
   }

   public static RiptideAddons.CustomMenus customMenus() {
      return RiptideAddons.CustomMenus.INSTANCE;
   }

   public static List<RiptideAddon> list() {
      return AddonManager.loaded();
   }

   public static String id(String localId) {
      return AddonManager.scopedId(localId);
   }

   public static int apiVersion() {
      return 3;
   }

   public static final class Commands {
      static final RiptideAddons.Commands INSTANCE = new RiptideAddons.Commands();

      private Commands() {
      }

      public void register(Command command) {
         RiptideCommands.registerAddonCommand(command, AddonManager.currentAddonId());
      }

      public AddonRegistrationResult registerDetailed(Command command) {
         return RiptideCommands.registerAddonCommandDetailed(command, AddonManager.currentAddonId());
      }
   }

   public static final class CustomMenus {
      static final RiptideAddons.CustomMenus INSTANCE = new RiptideAddons.CustomMenus();

      private CustomMenus() {
      }

      public boolean register(CustomMenuAdapter adapter) {
         return CustomMenuAdapterRegistry.register(adapter);
      }
   }

   public static final class Events {
      static final RiptideAddons.Events INSTANCE = new RiptideAddons.Events();

      private Events() {
      }

      public void onTick(Consumer<Minecraft> listener) {
         AddonEvents.onTick(listener);
      }

      public void onPacketSend(Predicate<Packet<?>> listener) {
         AddonEvents.onPacketSend(listener);
      }

      public void onPacketReceive(Consumer<Packet<?>> listener) {
         AddonEvents.onPacketReceive(listener);
      }

      public void onGameJoin(Runnable listener) {
         AddonEvents.onGameJoin(listener);
      }

      public void onGameLeft(Runnable listener) {
         AddonEvents.onGameLeft(listener);
      }
   }

   public static final class Hud {
      static final RiptideAddons.Hud INSTANCE = new RiptideAddons.Hud();

      private Hud() {
      }

      public boolean register(HudElementProvider provider) {
         return HudElements.register(provider);
      }

      public AddonRegistrationResult registerDetailed(HudElementProvider provider) {
         return HudElements.registerDetailed(provider);
      }
   }

   public static final class MacroActions {
      static final RiptideAddons.MacroActions INSTANCE = new RiptideAddons.MacroActions();

      private MacroActions() {
      }

      public boolean register(MacroActionEntry entry) {
         return MacroActionRegistry.register(entry);
      }

      public AddonRegistrationResult registerDetailed(MacroActionEntry entry) {
         return MacroActionRegistry.registerDetailed(entry);
      }

      public void registerCategory(String id, String label, int color) {
         MacroActionRegistry.registerScopedCategory(id, label, color);
      }
   }

   public static final class Modules {
      static final RiptideAddons.Modules INSTANCE = new RiptideAddons.Modules();

      private Modules() {
      }

      public boolean register(Module module) {
         return ModuleRegistry.registerAddonModule(module, AddonManager.currentAddonId());
      }

      public AddonRegistrationResult registerDetailed(Module module) {
         return ModuleRegistry.registerAddonModuleDetailed(module, AddonManager.currentAddonId());
      }

      public ModuleCategory autoCategory() {
         return ModuleCategory.registerAddon(AddonManager.currentAddonId(), AddonManager.scopedCategoryLabel(null));
      }

      public ModuleCategory registerCategory(String label) {
         return ModuleCategory.registerAddon(AddonManager.currentAddonId(), AddonManager.scopedCategoryLabel(label));
      }
   }

   public static final class Presets {
      static final RiptideAddons.Presets INSTANCE = new RiptideAddons.Presets();

      private Presets() {
      }

      public void registerCategory(String id, String label) {
         MacroPresetRegistry.registerScopedCategory(id, label);
      }

      public void register(String label, String tip, Supplier<List<MacroAction>> builder) {
         MacroPresetRegistry.register(MacroPresetRegistry.ensureScopedCategory(null), label, tip, builder);
      }

      public AddonRegistrationResult registerDetailed(String label, String tip, Supplier<List<MacroAction>> builder) {
         return MacroPresetRegistry.registerDetailed(MacroPresetRegistry.ensureScopedCategory(null), label, tip, builder);
      }

      public void register(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
         MacroPresetRegistry.register(MacroPresetRegistry.ensureScopedCategory(categoryId), label, tip, builder);
      }

      public AddonRegistrationResult registerDetailed(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
         return MacroPresetRegistry.registerDetailed(MacroPresetRegistry.ensureScopedCategory(categoryId), label, tip, builder);
      }
   }
}
