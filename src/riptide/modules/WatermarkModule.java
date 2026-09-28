package riptide.modules;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.StringSetting;

public final class WatermarkModule extends Module {
   private static WatermarkModule cached;

   public WatermarkModule() {
      super("watermark", "Watermark", ModuleCategory.RENDER, "Shows the Riptide logo tag in a corner of your screen.");
      this.add(new StringSetting("text", "Text", "RIPTIDE").description("What the watermark says.").build());
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner it sits in.")
            .build()
      );
      this.add(new BoolSetting("fps", "Show FPS", true).description("Add an FPS line under the logo.").build());
      this.add(new ColorSetting("color", "Color", -11890433).group("Colors").description("Logo text color.").build());
   }

   @Override
   public String info() {
      return this.text("text");
   }

   public static void renderWatermark(GuiGraphicsExtractor var0) {
      WatermarkModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive()) {
         if (MC != null && MC.player != null && !MC.gui.hud.isHidden()) {
            try {
               Font var2 = MC.font;
               String var3 = var1.text("text");
               if (var3 == null || var3.isBlank()) {
                  var3 = "RIPTIDE";
               }

               boolean var4 = var1.bool("fps");
               String var5 = var4 ? MC.getFps() + " FPS" : null;
               int var6 = ModuleRenderUtil.color(var1, "color", -11890433) | 0xFF000000;
               int var7 = -4602154;
               byte var8 = 5;
               byte var9 = 11;
               int var10 = var2.width(var3);
               int var11 = var5 == null ? 0 : var2.width(var5);
               int var12 = Math.max(var10, var11) + var8 * 2;
               int var13 = var8 * 2 + var9 + (var5 == null ? 0 : var9);
               int var14 = var0.guiWidth();
               int var15 = var0.guiHeight();
               byte var16 = 6;
               String var17 = var1.choice("corner");
               boolean var18 = var17.contains("Right");
               boolean var19 = var17.contains("Bottom");
               int var20 = var18 ? var14 - var16 - var12 : var16;
               int var21 = var19 ? var15 - var16 - var13 : var16;
               var0.fill(var20, var21, var20 + var12, var21 + var13, 1711276032);
               var0.fill(var20, var21, var20 + var12, var21 + 1, var6);
               var0.text(var2, var3, var20 + var8, var21 + var8, var6, true);
               if (var5 != null) {
                  var0.text(var2, var5, var20 + var8, var21 + var8 + var9, var7, true);
               }
            } catch (Throwable var22) {
            }
         }
      }
   }

   private static WatermarkModule instance() {
      WatermarkModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("watermark") instanceof WatermarkModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }
}
