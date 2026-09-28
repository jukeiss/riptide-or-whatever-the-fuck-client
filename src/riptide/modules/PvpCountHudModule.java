package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class PvpCountHudModule extends Module {
   private static PvpCountHudModule cached;

   public PvpCountHudModule() {
      super("pvp-count-hud", "PvP Counts", ModuleCategory.RENDER, "Counts your totems, gapples and pearls on the HUD.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Bottom Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the counts sit in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 4, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("totems", "Totems", true).description("Count Totems of Undying.").build());
      this.add(new BoolSetting("gapples", "Gapples", true).description("Count Enchanted Golden Apples.").build());
      this.add(new BoolSetting("pearls", "Pearls", true).description("Count Ender Pearls.").build());
      this.add(new BoolSetting("crystals", "Crystals", false).description("Count End Crystals.").build());
      this.add(new BoolSetting("hide-zero", "Hide Zero", false).description("Don't show a line when the count is 0.").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-low", "Low", -2080722).group("Colors").description("Text color when a tracked count is 0.").build());
   }

   private static PvpCountHudModule instance() {
      PvpCountHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("pvp-count-hud") instanceof PvpCountHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      PvpCountHudModule var1 = instance();
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
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;

      for (int var6 = 0; var6 < MC.player.getInventory().getContainerSize(); var6++) {
         ItemStack var7 = MC.player.getInventory().getItem(var6);
         if (!var7.isEmpty()) {
            if (var7.is(Items.TOTEM_OF_UNDYING)) {
               var2 += var7.getCount();
            } else if (var7.is(Items.ENCHANTED_GOLDEN_APPLE)) {
               var3 += var7.getCount();
            } else if (var7.is(Items.ENDER_PEARL)) {
               var4 += var7.getCount();
            } else if (var7.is(Items.END_CRYSTAL)) {
               var5 += var7.getCount();
            }
         }
      }

      ItemStack var26 = MC.player.getOffhandItem();
      if (var26.is(Items.TOTEM_OF_UNDYING)) {
      }

      boolean var27 = this.bool("hide-zero");
      ArrayList var8 = new ArrayList();
      ArrayList var9 = new ArrayList();
      addLine(var8, var9, this.bool("totems"), var27, "Totems", var2);
      addLine(var8, var9, this.bool("gapples"), var27, "Gapples", var3);
      addLine(var8, var9, this.bool("pearls"), var27, "Pearls", var4);
      addLine(var8, var9, this.bool("crystals"), var27, "Crystals", var5);
      if (!var8.isEmpty()) {
         Font var10 = MC.font;
         byte var11 = 3;
         byte var12 = 10;
         int var13 = 0;

         for (String var15 : var8) {
            var13 = Math.max(var13, var10.width(var15));
         }

         int var28 = var13 + var11 * 2;
         int var29 = var8.size() * var12 + var11 * 2 - 1;
         boolean var16 = this.choice("corner").contains("Right");
         int var18 = this.integer("margin");
         int var19 = var16 ? var1.guiWidth() - var18 - var28 : var18;
         int var20 = HudStack.y(this.choice("corner"), var18, var29, var1.guiHeight());
         int var21 = ModuleRenderUtil.color(this, "c-bg", -1879048192);
         int var22 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         int var23 = ModuleRenderUtil.color(this, "c-low", -2080722) | 0xFF000000;
         if (var21 >>> 24 != 0) {
            var1.fill(var19, var20, var19 + var28, var20 + var29, var21);
         }

         int var24 = var20 + var11;

         for (int var25 = 0; var25 < var8.size(); var25++) {
            var1.text(var10, Component.literal((String)var8.get(var25)), var19 + var11, var24, var9.get(var25) ? var23 : var22);
            var24 += var12;
         }
      }
   }

   private static void addLine(List<String> var0, List<Boolean> var1, boolean var2, boolean var3, String var4, int var5) {
      if (var2 && (!var3 || var5 != 0)) {
         var0.add(var4 + ": " + var5);
         var1.add(var5 == 0);
      }
   }
}
