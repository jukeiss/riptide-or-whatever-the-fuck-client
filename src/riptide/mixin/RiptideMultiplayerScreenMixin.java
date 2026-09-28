package riptide.mixin;

import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.ducks.RiptideExternalButtonScreen;
import riptide.gui.screen.RiptideAccountsScreen;
import riptide.gui.screen.RiptideJoinMacroScreen;
import riptide.gui.screen.RiptideMultiConsoleScreen;
import riptide.gui.screen.RiptideMultiDisclaimerScreen;
import riptide.gui.screen.RiptideMultiScreen;
import riptide.gui.screen.RiptidePluginLibraryScreen;
import riptide.gui.screen.RiptideProxiesScreen;
import riptide.gui.screen.RiptideVoiceChatPromptScreen;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.RiptideConfig;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.multi.MultiManager;

@Mixin(
   value = {JoinMultiplayerScreen.class},
   priority = 2000
)
public abstract class RiptideMultiplayerScreenMixin extends Screen implements RiptideExternalButtonScreen {
   @Unique
   private static final int BUTTON_HEIGHT = 20;
   @Unique
   private static final int BUTTON_WIDTH = 60;
   @Unique
   private static final int MACRO_BUTTON_WIDTH = 50;
   @Unique
   private static final int MULTI_BUTTON_WIDTH = 50;
   @Unique
   private static final int STACK_WIDTH = 104;
   @Unique
   private static final int MARGIN = 4;
   @Unique
   private static final int GAP = 3;
   @Unique
   private static final int EXTERNAL_NONE = 0;
   @Unique
   private static final int EXTERNAL_VIA_FABRIC_PLUS = 1;
   @Unique
   private static final int EXTERNAL_REPLAY_RECORD = 2;
   @Unique
   private static final int EXTERNAL_OPSEC = 3;
   @Unique
   private Button riptide$accountsButton;
   @Unique
   private Button riptide$joinMacroButton;
   @Unique
   private Button riptide$multiButton;
   @Unique
   private Button riptide$proxiesButton;
   @Unique
   private Button riptide$spoofButton;
   @Unique
   private Button riptide$packsButton;
   @Unique
   private Button riptide$libraryButton;
   @Unique
   private Button riptide$recordButton;
   @Unique
   private boolean riptide$voicePromptChecked;
   @Unique
   private boolean riptide$meteorUiConfigSuppressed;
   @Unique
   private int riptide$topButtonsLeft = 4;
   @Shadow
   protected ServerSelectionList serverSelectionList;

   protected RiptideMultiplayerScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"repositionElements"},
      at = {@At("TAIL")}
   )
   private void riptide$repositionElements(CallbackInfo ci) {
      this.riptide$layoutButtons();
      this.riptide$maybeShowVoiceChatPrompt();
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void riptide$refreshMultiButton(CallbackInfo ci) {
      if (this.riptide$multiButton != null) {
         String label = riptide$multiLabel();
         if (!label.equals(this.riptide$multiButton.getMessage().getString())) {
            this.riptide$multiButton.setMessage(Component.literal(label));
         }
      }
   }

   @Unique
   private static String riptide$multiLabel() {
      MultiManager manager = MultiManager.get();
      return manager.isActive() ? "Multi " + manager.readyFraction() : "Multi";
   }

   @Unique
   private void riptide$maybeShowVoiceChatPrompt() {
      if (!this.riptide$voicePromptChecked) {
         this.riptide$voicePromptChecked = true;
         if (!PackHideState.isActive()) {
            if (FabricLoader.getInstance().isModLoaded("voicechat")) {
               RiptideModule module = RiptideModule.get();
               if (module != null && module.isSpoofClientVanilla()) {
                  RiptideConfig config = RiptideConfig.getGlobal();
                  if (config != null && !config.voiceChatModdedPromptShown) {
                     config.voiceChatModdedPromptShown = true;
                     config.save();
                     this.minecraft.execute(() -> {
                        if (this.minecraft.gui.screen() == this) {
                           this.minecraft.gui.setScreen(new RiptideVoiceChatPromptScreen(this));
                        }
                     });
                  }
               }
            }
         }
      }
   }

   @Unique
   private void riptide$layoutButtons() {
      this.riptide$suppressMeteorWidgets();
      AbstractWidget via = null;
      AbstractWidget opsec = null;

      for (GuiEventListener child : this.children()) {
         if (child instanceof AbstractWidget widget && !this.riptide$isOwned(widget)) {
            int kind = this.riptide$externalKind(widget);
            if (kind == 2) {
               riptide$setVisible(widget, false);
            } else if (kind == 1) {
               via = widget;
            } else if (kind == 3) {
               opsec = widget;
            }
         }
      }

      boolean hidden = PackHideState.isActive();
      if (hidden) {
         riptide$setVisible(this.riptide$accountsButton, false);
         riptide$setVisible(this.riptide$joinMacroButton, false);
         riptide$setVisible(this.riptide$multiButton, false);
         riptide$setVisible(this.riptide$proxiesButton, false);
         riptide$setVisible(this.riptide$spoofButton, false);
         riptide$setVisible(this.riptide$packsButton, false);
         riptide$setVisible(this.riptide$libraryButton, false);
         riptide$setVisible(this.riptide$recordButton, false);
         riptide$setVisible(via, false);
         riptide$setVisible(opsec, false);
      } else {
         this.riptide$ensureOwnedButtons();
         int topRight = this.width - 4;
         int topAvailable = Math.max(4, this.width - 8);
         boolean lite = RiptideLiteVariant.enabled();
         int topCell = lite ? Math.max(1, (topAvailable - 9) / 4) : Math.max(1, (topAvailable - 12) / 5);
         int accountsW = Math.min(60, topCell);
         int proxiesW = Math.min(60, topCell);
         int libraryW = Math.min(60, topCell);
         int macroW = Math.min(50, topCell);
         int multiW = Math.min(50, topCell);
         int cursor = topRight - accountsW;
         riptide$place(this.riptide$accountsButton, cursor, 4, accountsW, 20);
         cursor -= 3 + proxiesW;
         riptide$place(this.riptide$proxiesButton, cursor, 4, proxiesW, 20);
         cursor -= 3 + libraryW;
         this.riptide$libraryButton.setMessage(this.riptide$fitLabel(libraryW, "Library"));
         riptide$place(this.riptide$libraryButton, cursor, 4, libraryW, 20);
         cursor -= 3 + macroW;
         riptide$place(this.riptide$joinMacroButton, cursor, 4, macroW, 20);
         if (!lite) {
            cursor -= 3 + multiW;
            riptide$place(this.riptide$multiButton, cursor, 4, multiW, 20);
            String multiLabel = riptide$multiLabel();
            if (!multiLabel.equals(this.riptide$multiButton.getMessage().getString())) {
               this.riptide$multiButton.setMessage(Component.literal(multiLabel));
            }
         }

         this.riptide$topButtonsLeft = Math.max(4, cursor);
         int footerRight = this.width / 2 - 154 - 3;
         int footerWidth = Math.max(60, Math.min(104, footerRight - 4));
         int footerX = Math.max(4, footerRight - footerWidth);
         int footerY = Math.max(4, this.height - 51);
         this.riptide$spoofButton.setMessage(this.riptide$spoofClientLabel(footerWidth));
         this.riptide$packsButton.setMessage(this.riptide$bypassPacksLabel(footerWidth));
         riptide$place(this.riptide$spoofButton, footerX, footerY, footerWidth, 20);
         riptide$place(this.riptide$packsButton, footerX, footerY + 20 + 3, footerWidth, 20);
         int rightX = Math.min(this.width - 4 - 104, this.width / 2 + 154 + 3);
         int rightWidth = Math.max(60, Math.min(104, this.width - 4 - rightX));
         int count = (FabricLoader.getInstance().isModLoaded("replaymod") ? 1 : 0) + (via != null ? 1 : 0) + (opsec != null ? 1 : 0);
         int rightY = Math.max(4, this.height - 8 - Math.max(0, count * 20 + Math.max(0, count - 1) * 3));
         int slot = 0;
         if (FabricLoader.getInstance().isModLoaded("replaymod")) {
            this.riptide$recordButton.setMessage(this.riptide$replayServerLabel(rightWidth));
            riptide$place(this.riptide$recordButton, rightX, rightY + slot++ * 23, rightWidth, 20);
         } else {
            riptide$setVisible(this.riptide$recordButton, false);
         }

         if (via != null) {
            riptide$place(via, rightX, rightY + slot++ * 23, rightWidth, 20);
         }

         if (opsec != null) {
            riptide$place(opsec, rightX, rightY + slot * 23, rightWidth, 20);
         }
      }
   }

   @Unique
   private void riptide$ensureOwnedButtons() {
      if (this.riptide$accountsButton == null) {
         this.riptide$accountsButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Accounts"), ignored -> this.minecraft.gui.setScreen(new RiptideAccountsScreen(this)))
               .bounds(0, 0, 60, 20)
               .build()
         );
      }

      if (this.riptide$joinMacroButton == null) {
         this.riptide$joinMacroButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Macro"), ignored -> this.minecraft.gui.setScreen(new RiptideJoinMacroScreen(this))).bounds(0, 0, 50, 20).build()
         );
      }

      if (this.riptide$multiButton == null && !RiptideLiteVariant.enabled()) {
         this.riptide$multiButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Multi"), ignored -> this.riptide$openMulti()).bounds(0, 0, 50, 20).build()
         );
      }

      if (this.riptide$proxiesButton == null) {
         this.riptide$proxiesButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Proxies"), ignored -> this.minecraft.gui.setScreen(new RiptideProxiesScreen(this))).bounds(0, 0, 60, 20).build()
         );
      }

      if (this.riptide$spoofButton == null) {
         this.riptide$spoofButton = (Button)this.addRenderableWidget(Button.builder(Component.literal("Client"), ignored -> {
            RiptideModule module = RiptideModule.get();
            if (module != null) {
               module.setSpoofClientVanilla(!module.isSpoofClientVanilla());
            }

            this.riptide$layoutButtons();
         }).bounds(0, 0, 104, 20).build());
      }

      if (this.riptide$packsButton == null) {
         this.riptide$packsButton = (Button)this.addRenderableWidget(Button.builder(Component.literal("Packs"), ignored -> {
            RiptideModule module = RiptideModule.get();
            if (module != null) {
               module.setBypassResourcePack(!module.isBypassResourcePack());
            }

            this.riptide$layoutButtons();
         }).bounds(0, 0, 104, 20).build());
      }

      if (this.riptide$libraryButton == null) {
         this.riptide$libraryButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Library"), ignored -> this.riptide$openPluginLibrary()).bounds(0, 0, 60, 20).build()
         );
      }

      if (this.riptide$recordButton == null) {
         this.riptide$recordButton = (Button)this.addRenderableWidget(Button.builder(Component.literal("Replay"), ignored -> {
            this.riptide$toggleReplayServerRecording();
            this.riptide$layoutButtons();
         }).bounds(0, 0, 104, 20).build());
      }
   }

   @Unique
   private void riptide$openPluginLibrary() {
      this.minecraft.gui.setScreen(new RiptidePluginLibraryScreen(this));
   }

   @Unique
   private void riptide$openMulti() {
      if (!RiptideLiteVariant.enabled()) {
         ServerData selected = this.riptide$selectedServerData();
         String address = selected == null ? "" : selected.ip;
         Runnable proceed = () -> {
            if (MultiManager.get().isActive()) {
               this.minecraft.gui.setScreen(new RiptideMultiConsoleScreen(this));
            } else {
               MultiManager.get().rememberSelectedServer(selected);
               this.minecraft.gui.setScreen(new RiptideMultiScreen(this, address));
            }
         };
         RiptideMultiDisclaimerScreen.open(this.minecraft, this, proceed);
      }
   }

   @Unique
   private boolean riptide$isOwned(AbstractWidget widget) {
      return widget == this.riptide$accountsButton
         || widget == this.riptide$joinMacroButton
         || widget == this.riptide$multiButton
         || widget == this.riptide$proxiesButton
         || widget == this.riptide$spoofButton
         || widget == this.riptide$packsButton
         || widget == this.riptide$libraryButton
         || widget == this.riptide$recordButton;
   }

   @Unique
   private ServerData riptide$selectedServerData() {
      if (this.serverSelectionList == null) {
         return null;
      } else {
         Entry selected = (Entry)this.serverSelectionList.getSelected();
         return selected instanceof OnlineServerEntry online ? online.getServerData() : null;
      }
   }

   @Unique
   private static void riptide$place(AbstractWidget widget, int x, int y, int width, int height) {
      if (widget != null) {
         widget.setX(Math.max(4, x));
         widget.setY(Math.max(4, y));
         widget.setSize(Math.max(1, width), Math.max(1, height));
         riptide$setVisible(widget, true);
      }
   }

   @Unique
   private static void riptide$setVisible(AbstractWidget widget, boolean visible) {
      if (widget != null) {
         widget.visible = visible;
         widget.active = visible;
      }
   }

   @Unique
   private Component riptide$spoofClientLabel(int width) {
      RiptideModule module = RiptideModule.get();
      boolean enabled = module != null && module.isSpoofClientVanilla();
      return this.riptide$fitLabel(width, enabled ? "Client: Vanilla" : "Client: Modded", enabled ? "Vanilla" : "Modded", "Client");
   }

   @Unique
   private Component riptide$bypassPacksLabel(int width) {
      RiptideModule module = RiptideModule.get();
      boolean enabled = module != null && module.isBypassResourcePack();
      return this.riptide$fitLabel(width, enabled ? "Packs: Bypass" : "Packs: Normal", enabled ? "Bypass" : "Normal", "Packs");
   }

   @Unique
   private Component riptide$replayServerLabel(int width) {
      boolean enabled = riptide$getReplayBoolean("RECORD_SERVER", true);
      return this.riptide$fitLabel(width, enabled ? "Replay: On" : "Replay: Off", enabled ? "Rec: On" : "Rec: Off", "Replay");
   }

   @Unique
   private Component riptide$fitLabel(int width, String... candidates) {
      int available = Math.max(1, width - 8);

      for (String candidate : candidates) {
         if (this.font.width(candidate) <= available) {
            return Component.literal(candidate);
         }
      }

      return Component.literal(candidates[candidates.length - 1]);
   }

   @Unique
   private int riptide$externalKind(AbstractWidget widget) {
      String label = widget.getMessage().getString();
      String normalized = label.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
      String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
      if (FabricLoader.getInstance().isModLoaded("viafabricplus") && "ViaFabricPlus".equals(label)) {
         return 1;
      } else if (!FabricLoader.getInstance().isModLoaded("replaymod")
         || !className.contains("replaymod") && !normalized.contains("recordserver") && !normalized.contains("replaymodguisettingsrecordserver")) {
         return !className.contains("opsec") && !normalized.contains("opsec") ? 0 : 3;
      } else {
         return 2;
      }
   }

   @Unique
   private void riptide$suppressMeteorWidgets() {
      if (FabricLoader.getInstance().isModLoaded("meteor-client")) {
         if (!this.riptide$meteorUiConfigSuppressed) {
            this.riptide$meteorUiConfigSuppressed = true;
            riptide$disableMeteorMultiplayerUiConfig();
         }

         for (GuiEventListener child : this.children()) {
            if (child instanceof Button button && !this.riptide$isOwned(button)) {
               String label = button.getMessage().getString();
               if ("Accounts".equals(label) || "Proxies".equals(label)) {
                  riptide$setVisible(button, false);
               }
            }
         }
      }
   }

   @Unique
   private void riptide$toggleReplayServerRecording() {
      boolean enabled = riptide$getReplayBoolean("RECORD_SERVER", true);
      riptide$setReplayBoolean("RECORD_SERVER", !enabled);
   }

   @Unique
   private static boolean riptide$getReplayBoolean(String settingField, boolean fallback) {
      try {
         Object settings = riptide$replaySettingsRegistry();
         Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
         return settings.getClass().getMethod("get", Class.forName("com.replaymod.core.SettingsRegistry$SettingKey")).invoke(settings, key) instanceof Boolean bool
            ? bool
            : fallback;
      } catch (LinkageError | ReflectiveOperationException var6) {
         return fallback;
      }
   }

   @Unique
   private static void riptide$setReplayBoolean(String settingField, boolean value) {
      try {
         Object settings = riptide$replaySettingsRegistry();
         Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
         Class<?> settingKeyClass = Class.forName("com.replaymod.core.SettingsRegistry$SettingKey");
         settings.getClass().getMethod("set", settingKeyClass, Object.class).invoke(settings, key, value);
         settings.getClass().getMethod("save").invoke(settings);
      } catch (LinkageError | ReflectiveOperationException var5) {
      }
   }

   @Unique
   private static Object riptide$replaySettingsRegistry() throws ReflectiveOperationException {
      Object replayMod = Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);
      return replayMod.getClass().getMethod("getSettingsRegistry").invoke(replayMod);
   }

   @Unique
   private static void riptide$disableMeteorMultiplayerUiConfig() {
      try {
         Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
         Object config = configClass.getMethod("get").invoke(null);
         Class<?> buttonPositionClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config$ButtonPosition");
         Object hidden = Enum.valueOf(buttonPositionClass.asSubclass(Enum.class), "Hidden");
         riptide$setMeteorSetting(configClass.getField("accountButtonAnchor").get(config), hidden);
         riptide$setMeteorSetting(configClass.getField("proxiesButtonAnchor").get(config), hidden);
         riptide$setMeteorSetting(configClass.getField("showAccountStatus").get(config), false);
         riptide$setMeteorSetting(configClass.getField("showProxiesStatus").get(config), false);
      } catch (ReflectiveOperationException var4) {
      }
   }

   @Unique
   private static void riptide$setMeteorSetting(Object setting, Object value) throws ReflectiveOperationException {
      setting.getClass().getSuperclass().getMethod("set", Object.class).invoke(setting, value);
   }

   @Override
   public void riptide$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
      this.riptide$layoutButtons();
      if (!PackHideState.isActive()) {
         int leftTextWidth = Math.max(0, this.riptide$topButtonsLeft - 4 - 3);
         String username = this.minecraft.getUser().getName();
         if (leftTextWidth > 8) {
            graphics.text(this.font, this.riptide$fitPlain("Logged in as " + username, leftTextWidth), 4, 4, -1, false);
         }

         RiptideProxy proxy = RiptideProxyManager.get().getEnabled();
         int statusY = 16;
         if (proxy != null) {
            String proxyLabel = "Using proxy " + proxy.address + ":" + proxy.port;
            if (leftTextWidth > 8) {
               graphics.text(this.font, this.riptide$fitPlain(proxyLabel, leftTextWidth), 4, statusY, -5263441, false);
            }

            statusY += 12;
         }

         String macroName = RiptideJoinMacroController.selectedMacroName();
         String macroLabel;
         int macroColor;
         if (macroName.isBlank()) {
            macroLabel = "Join Macro: none";
            macroColor = -7370102;
         } else if (RiptideMacroManager.get().get(macroName) == null) {
            macroLabel = "Join Macro missing: " + macroName;
            macroColor = -38037;
         } else {
            macroLabel = "Join Macro: " + macroName + " - " + RiptideJoinMacroController.modeSummary();
            macroColor = -10035062;
         }

         if (leftTextWidth > 8) {
            graphics.text(this.font, this.riptide$fitPlain(macroLabel, leftTextWidth), 4, statusY, macroColor, false);
         }
      }
   }

   @Unique
   private String riptide$fitPlain(String label, int maxWidth) {
      if (label == null) {
         return "";
      } else {
         return this.font.width(label) <= maxWidth ? label : this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
      }
   }
}
