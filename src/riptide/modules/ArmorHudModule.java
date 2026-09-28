package riptide.modules;

import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class ArmorHudModule extends Module {
   private static ArmorHudModule cached;
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
   };

   public ArmorHudModule() {
      super("armor-hud", "Armor HUD", ModuleCategory.RENDER, "Lists your armour and held items with durability.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Bottom Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the list sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 4, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("hands", "Include Hands", true).description("Also list your main-hand and off-hand items.").build());
      this.add(new BoolSetting("percent", "As Percent", true).description("Durability as a percentage instead of raw remaining.").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-low", "Low Durability", -2080722).group("Colors").description("Text color under 20% durability.").build());
   }

   private static ArmorHudModule instance() {
      ArmorHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("armor-hud") instanceof ArmorHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      ArmorHudModule var1 = instance();
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
      boolean var2 = this.bool("hands");
      boolean var3 = this.bool("percent");
      ArrayList var4 = new ArrayList();
      ArrayList var5 = new ArrayList();

      for (int var6 = 0; var6 < SLOTS.length && (var6 < 4 || var2); var6++) {
         ItemStack var7 = MC.player.getItemBySlot(SLOTS[var6]);
         if (!var7.isEmpty()) {
            String var8 = var7.getHoverName().getString();
            boolean var9 = false;
            if (var7.isDamageableItem() && var7.getMaxDamage() > 0) {
               int var10 = var7.getMaxDamage() - var7.getDamageValue();
               double var11 = (double)var10 / var7.getMaxDamage();
               var9 = var11 < 0.2;
               var8 = var8 + "  " + (var3 ? (int)Math.round(var11 * 100.0) + "%" : var10 + "/" + var7.getMaxDamage());
            }

            if (var7.getCount() > 1) {
               var8 = var8 + " x" + var7.getCount();
            }

            var4.add(var8);
            var5.add(var9);
         }
      }

      if (!var4.isEmpty()) {
         Font var22 = MC.font;
         byte var23 = 3;
         byte var24 = 10;
         int var25 = 0;

         for (String var28 : var4) {
            var25 = Math.max(var25, var22.width(var28));
         }

         int var27 = var25 + var23 * 2;
         int var29 = var4.size() * var24 + var23 * 2 - 1;
         boolean var12 = this.choice("corner").contains("Right");
         int var14 = this.integer("margin");
         int var15 = var12 ? var1.guiWidth() - var14 - var27 : var14;
         int var16 = HudStack.y(this.choice("corner"), var14, var29, var1.guiHeight());
         int var17 = ModuleRenderUtil.color(this, "c-bg", -1879048192);
         int var18 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         int var19 = ModuleRenderUtil.color(this, "c-low", -2080722) | 0xFF000000;
         if (var17 >>> 24 != 0) {
            var1.fill(var15, var16, var15 + var27, var16 + var29, var17);
         }

         int var20 = var16 + var23;

         for (int var21 = 0; var21 < var4.size(); var21++) {
            var1.text(var22, Component.literal((String)var4.get(var21)), var15 + var23, var20, var5.get(var21) ? var19 : var18);
            var20 += var24;
         }
      }
   }
}
