package riptide.modules;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.BoolSetting;

public final class KeystrokesHudModule extends Module {
   private static KeystrokesHudModule cached;

   public KeystrokesHudModule() {
      super("keystrokes", "Keystrokes", ModuleCategory.RENDER, "Shows your WASD, mouse buttons and jump as they're pressed.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the block sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 6, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("hide-dup", "Hide When Duplicated", true).description("Stay hidden while the draggable Keystrokes HUD element is already showing this.").build());
      this.add(new IntSetting("size", "Key Size", 20, 12, 40, 1).description("Size of one key box, in pixels.").build());
      this.add(new ColorSetting("c-idle", "Idle", -1877994472).group("Colors").description("Box color when a key is up.").build());
      this.add(new ColorSetting("c-press", "Pressed", -263548673).group("Colors").description("Box color when a key is held.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-text-press", "Text Pressed", -15723496).group("Colors").build());
   }

   private static KeystrokesHudModule instance() {
      KeystrokesHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("keystrokes") instanceof KeystrokesHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      KeystrokesHudModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive() && !HudDuplicate.suppresses(var1, "keystrokes")) {
         if (MC != null && MC.player != null && MC.options != null && !MC.gui.hud.isHidden()) {
            try {
               var1.draw(var0);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor var1) {
      int var2 = this.integer("size");
      byte var3 = 2;
      int var4 = var2 * 3 + var3 * 2;
      int var5 = var2 * 3 + var3 * 2 + var2 / 2 + var3;
      boolean var6 = this.choice("corner").contains("Right");
      int var8 = this.integer("margin");
      int var9 = var1.guiWidth();
      int var10 = var1.guiHeight();
      int var11 = var6 ? var9 - var8 - var4 : var8;
      int var12 = HudStack.y(this.choice("corner"), var8, var5, var10);
      this.key(var1, var11 + var2 + var3, var12, var2, var2, "W", MC.options.keyUp);
      this.key(var1, var11, var12 + var2 + var3, var2, var2, "A", MC.options.keyLeft);
      this.key(var1, var11 + var2 + var3, var12 + var2 + var3, var2, var2, "S", MC.options.keyDown);
      this.key(var1, var11 + (var2 + var3) * 2, var12 + var2 + var3, var2, var2, "D", MC.options.keyRight);
      int var13 = var12 + (var2 + var3) * 2;
      int var14 = (var4 - var3) / 2;
      this.key(var1, var11, var13, var14, var2, "LMB", MC.options.keyAttack);
      this.key(var1, var11 + var14 + var3, var13, var4 - var14 - var3, var2, "RMB", MC.options.keyUse);
      this.key(var1, var11, var13 + var2 + var3, var4, var2 / 2, "___", MC.options.keyJump);
   }

   private void key(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, String var6, KeyMapping var7) {
      boolean var8 = var7.isDown();
      int var9 = var8 ? ModuleRenderUtil.color(this, "c-press", -263548673) : ModuleRenderUtil.color(this, "c-idle", -1877994472);
      int var10 = (var8 ? ModuleRenderUtil.color(this, "c-text-press", -15723496) : ModuleRenderUtil.color(this, "c-text", -1)) | 0xFF000000;
      var1.fill(var2, var3, var2 + var4, var3 + var5, var9);
      Font var11 = MC.font;
      if (var5 >= 8) {
         var1.text(var11, Component.literal(var6), var2 + (var4 - var11.width(var6)) / 2, var3 + (var5 - 8) / 2, var10);
      }
   }
}
