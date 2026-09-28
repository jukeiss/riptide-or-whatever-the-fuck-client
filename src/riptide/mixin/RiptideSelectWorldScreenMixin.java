package riptide.mixin;

import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.ducks.RiptideExternalButtonScreen;
import riptide.gui.screen.RiptideJoinMacroScreen;
import riptide.modules.PackHideState;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideMacroManager;

@Mixin(
   value = {SelectWorldScreen.class},
   priority = 2000
)
public abstract class RiptideSelectWorldScreenMixin extends Screen implements RiptideExternalButtonScreen {
   @Unique
   private static final int BUTTON_HEIGHT = 20;
   @Unique
   private static final int BUTTON_WIDTH = 104;
   @Unique
   private static final int MACRO_BUTTON_WIDTH = 50;
   @Unique
   private static final int BUTTON_GAP = 3;
   @Unique
   private Button riptide$macroButton;
   @Unique
   private Button riptide$recordButton;

   protected RiptideSelectWorldScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"repositionElements"},
      at = {@At("TAIL")}
   )
   private void riptide$repositionElements(CallbackInfo ci) {
      this.riptide$layoutReplayButton();
   }

   @Unique
   private void riptide$layoutReplayButton() {
      AbstractWidget back = null;

      for (GuiEventListener child : this.children()) {
         if (child instanceof AbstractWidget widget && widget != this.riptide$recordButton && widget != this.riptide$macroButton) {
            if (this.riptide$isReplayRecordButton(widget)) {
               widget.visible = false;
               widget.active = false;
            } else if (this.riptide$isBackButton(widget)) {
               back = widget;
            }
         }
      }

      if (PackHideState.isActive()) {
         if (this.riptide$macroButton != null) {
            this.riptide$macroButton.visible = false;
            this.riptide$macroButton.active = false;
         }

         if (this.riptide$recordButton != null) {
            this.riptide$recordButton.visible = false;
            this.riptide$recordButton.active = false;
         }
      } else {
         if (this.riptide$macroButton == null) {
            this.riptide$macroButton = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Macro"), ignored -> this.minecraft.gui.setScreen(new RiptideJoinMacroScreen(this)))
                  .bounds(0, 0, 50, 20)
                  .build()
            );
         }

         if (this.riptide$recordButton == null) {
            this.riptide$recordButton = (Button)this.addRenderableWidget(Button.builder(this.riptide$replaySingleplayerLabel(), ignored -> {
               this.riptide$toggleReplaySingleplayerRecording();
               this.riptide$recordButton.setMessage(this.riptide$replaySingleplayerLabel());
            }).bounds(0, 0, 104, 20).build());
         }

         int macroX = back != null ? back.getRight() + 3 : this.width / 2 + 104;
         int y = back != null ? back.getY() : this.height - 28;
         if (macroX + 50 > this.width - 4) {
            macroX = Math.max(4, this.width - 4 - 50);
            y = Math.max(4, y - 20 - 3);
         }

         this.riptide$macroButton.setX(macroX);
         this.riptide$macroButton.setY(y);
         this.riptide$macroButton.setSize(50, 20);
         this.riptide$macroButton.visible = true;
         this.riptide$macroButton.active = true;
         if (!FabricLoader.getInstance().isModLoaded("replaymod")) {
            this.riptide$recordButton.visible = false;
            this.riptide$recordButton.active = false;
         } else {
            int width = Math.min(104, Math.max(60, this.width - 8));
            int recordX = macroX + 50 + 3;
            int recordY = y;
            if (recordX + width > this.width - 4) {
               recordX = Math.max(4, this.width - 4 - width);
               recordY = Math.max(4, y - 20 - 3);
            }

            this.riptide$recordButton.setX(recordX);
            this.riptide$recordButton.setY(recordY);
            this.riptide$recordButton.setSize(width, 20);
            this.riptide$recordButton.setMessage(this.riptide$replaySingleplayerLabel());
            this.riptide$recordButton.visible = true;
            this.riptide$recordButton.active = true;
         }
      }
   }

   @Unique
   private boolean riptide$isBackButton(AbstractWidget widget) {
      String label = widget.getMessage().getString();
      return label.equals(Component.translatable("gui.back").getString()) || "back".equalsIgnoreCase(label);
   }

   @Unique
   private boolean riptide$isReplayRecordButton(AbstractWidget widget) {
      if (!FabricLoader.getInstance().isModLoaded("replaymod")) {
         return false;
      } else {
         String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
         String normalizedLabel = widget.getMessage().getString().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
         return className.contains("replaymod")
            || normalizedLabel.contains("recordsingleplayer")
            || normalizedLabel.contains("replaymodguisettingsrecordsingleplayer");
      }
   }

   @Unique
   private Component riptide$replaySingleplayerLabel() {
      boolean enabled = riptide$getReplayBoolean("RECORD_SINGLEPLAYER", true);
      return Component.literal(enabled ? "Replay: On" : "Replay: Off");
   }

   @Unique
   private void riptide$toggleReplaySingleplayerRecording() {
      boolean enabled = riptide$getReplayBoolean("RECORD_SINGLEPLAYER", true);
      riptide$setReplayBoolean("RECORD_SINGLEPLAYER", !enabled);
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

   @Override
   public void riptide$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
      this.riptide$layoutReplayButton();
      if (!PackHideState.isActive()) {
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

         graphics.text(this.font, this.riptide$fitPlain(macroLabel, 220), 4, 4, macroColor, false);
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
