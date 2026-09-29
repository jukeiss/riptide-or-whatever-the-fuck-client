package riptide.modules;

import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class ArrayListHudModule extends Module {
   private static ArrayListHudModule cached;

   public ArrayListHudModule() {
      super("arraylist", "Active Modules", ModuleCategory.RENDER, "Lists your enabled modules in a corner, longest first.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the list grows from.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 2, 0, 200, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("hide-dup", "Hide When Duplicated", true).description("Stay hidden while the draggable Active Modules HUD element is already showing this.").build());
      this.add(new BoolSetting("rainbow", "Rainbow Tabs", true).description("Fade the side tab through a rainbow down the list.").build());
      this.add(new BoolSetting("hide-self", "Hide This", true).description("Don't list the Active Modules module itself.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-tab", "Tab", -11890433).group("Colors").description("Side tab color when Rainbow is off.").build());
      this.add(new ColorSetting("c-bg", "Background", Integer.MIN_VALUE).group("Colors").description("Per-line backing. 0 alpha removes it.").build());
   }

   private static ArrayListHudModule instance() {
      ArrayListHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("arraylist") instanceof ArrayListHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      ArrayListHudModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive() && !HudDuplicate.suppresses(var1, "active_modules")) {
         if (MC != null && MC.player != null && !MC.gui.hud.isHidden()) {
            try {
               var1.draw(var0);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor var1) {
      Font var2 = MC.font;
      ArrayList<String> var3 = new ArrayList<>();
      boolean var4 = this.bool("hide-self");

      for (Module var6 : ModuleRegistry.activeModules()) {
         if (!var4 || var6 != this) {
            var3.add(var6.name());
         }
      }

      if (!var3.isEmpty()) {
         var3.sort((var1x, var2x) -> var2.width(var2x) - var2.width(var1x));
         boolean var26 = this.choice("corner").contains("Right");
         int var7 = this.integer("margin");
         int var8 = var1.guiWidth();
         int var9 = var1.guiHeight();
         byte var10 = 11;
         int var11 = var3.size() * var10;
         int var12 = HudStack.y(this.choice("corner"), var7, var11, var9);
         boolean var13 = this.bool("rainbow");
         int var14 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         int var15 = ModuleRenderUtil.color(this, "c-tab", -11890433) | 0xFF000000;
         int var16 = ModuleRenderUtil.color(this, "c-bg", Integer.MIN_VALUE);

         for (int var17 = 0; var17 < var3.size(); var17++) {
            String var18 = (String)var3.get(var17);
            int var19 = var2.width(var18);
            int var20 = var12 + var17 * var10;
            int var21 = var26 ? var8 - var7 - var19 : var7 + 2;
            int var22 = var26 ? var8 - var7 - var19 - 3 : var7;
            int var23 = var26 ? var8 - var7 + 1 : var7 + var19 + 4;
            if (var16 >>> 24 != 0) {
               var1.fill(var22, var20 - 1, var23, var20 + var10 - 2, var16);
            }

            var1.text(var2, Component.literal(var18), var21, var20, var14);
            int var24 = var13 ? hsv(var17 * 18) : var15;
            int var25 = var26 ? var8 - var7 + 1 : var7 - 1;
            var1.fill(var25, var20 - 1, var25 + 1, var20 + var10 - 2, var24);
         }
      }
   }

   private static int hsv(float var0) {
      float var1 = (var0 % 360.0F + 360.0F) % 360.0F / 60.0F;
      float var2 = 1.0F - Math.abs(var1 % 2.0F - 1.0F);
      float var3 = 0.0F;
      float var4 = 0.0F;
      float var5 = 0.0F;
      if (var1 < 1.0F) {
         var3 = 1.0F;
         var4 = var2;
      } else if (var1 < 2.0F) {
         var3 = var2;
         var4 = 1.0F;
      } else if (var1 < 3.0F) {
         var4 = 1.0F;
         var5 = var2;
      } else if (var1 < 4.0F) {
         var4 = var2;
         var5 = 1.0F;
      } else if (var1 < 5.0F) {
         var3 = var2;
         var5 = 1.0F;
      } else {
         var3 = 1.0F;
         var5 = var2;
      }

      return 0xFF000000 | (int)(var3 * 255.0F) << 16 | (int)(var4 * 255.0F) << 8 | (int)(var5 * 255.0F);
   }
}
