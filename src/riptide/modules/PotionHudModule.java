package riptide.modules;

import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class PotionHudModule extends Module {
   private static PotionHudModule cached;

   public PotionHudModule() {
      super("potion-hud", "Potion HUD", ModuleCategory.RENDER, "Lists your active potion effects with level and time left.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Bottom Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the list sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 4, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("infinite", "Show Infinite", true).description("Show effects that never expire (beacon, etc.).").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-time", "Time", -4669236).group("Colors").build());
   }

   private static PotionHudModule instance() {
      PotionHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("potion-hud") instanceof PotionHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      PotionHudModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive()) {
         if (MC != null && MC.player != null && !MC.gui.hud.isHidden()) {
            try {
               var1.draw(var0);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor var1) {
      boolean var2 = this.bool("infinite");
      ArrayList var3 = new ArrayList();

      for (MobEffectInstance var5 : MC.player.getActiveEffects()) {
         if (var2 || !var5.isInfiniteDuration()) {
            String var6 = ((MobEffect)var5.getEffect().value()).getDisplayName().getString();
            int var7 = var5.getAmplifier();
            if (var7 > 0) {
               var6 = var6 + " " + toRoman(var7 + 1);
            }

            String var8 = var5.isInfiniteDuration() ? "**:**" : formatTicks(var5.getDuration());
            var3.add(var6 + "  " + var8);
         }
      }

      if (!var3.isEmpty()) {
         Font var20 = MC.font;
         byte var21 = 3;
         byte var22 = 10;
         int var23 = 0;

         for (String var9 : var3) {
            var23 = Math.max(var23, var20.width(var9));
         }

         int var25 = var23 + var21 * 2;
         int var26 = var3.size() * var22 + var21 * 2 - 1;
         boolean var10 = this.choice("corner").contains("Right");
         int var12 = this.integer("margin");
         int var13 = var10 ? var1.guiWidth() - var12 - var25 : var12;
         int var14 = HudStack.y(this.choice("corner"), var12, var26, var1.guiHeight());
         int var15 = ModuleRenderUtil.color(this, "c-bg", -1879048192);
         int var16 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         if (var15 >>> 24 != 0) {
            var1.fill(var13, var14, var13 + var25, var14 + var26, var15);
         }

         int var17 = var14 + var21;

         for (String var19 : var3) {
            var1.text(var20, Component.literal(var19), var13 + var21, var17, var16);
            var17 += var22;
         }
      }
   }

   private static String formatTicks(int var0) {
      int var1 = var0 / 20;
      return String.format("%d:%02d", var1 / 60, var1 % 60);
   }

   private static String toRoman(int var0) {
      return switch (var0) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> Integer.toString(var0);
      };
   }
}
