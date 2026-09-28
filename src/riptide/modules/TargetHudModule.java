package riptide.modules;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class TargetHudModule extends Module {
   private static TargetHudModule cached;
   private LivingEntity lastTarget;
   private long lastSeen;

   public TargetHudModule() {
      super("target-hud", "Target HUD", ModuleCategory.RENDER, "Shows the name and health of the entity under your crosshair.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Top", "Top", "Bottom", "Top Left", "Top Right")
            .description("Where the panel sits. Top/Bottom center it horizontally.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 24, 0, 400, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new IntSetting("width", "Width", 130, 80, 260, 2).description("Panel width, in pixels.").build());
      this.add(new IntSetting("linger", "Linger", 1500, 0, 8000, 100).description("Keep showing the last target for this many ms after you look away.").build());
      this.add(new ColorSetting("c-bg", "Background", -1072688104).group("Colors").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
      this.add(new ColorSetting("c-bar", "Health Bar", -12933547).group("Colors").build());
      this.add(new ColorSetting("c-bar-bg", "Bar Track", -14012618).group("Colors").build());
   }

   private static TargetHudModule instance() {
      TargetHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("target-hud") instanceof TargetHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      TargetHudModule var1 = instance();
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
      Entity var2 = MC.crosshairPickEntity;
      long var3 = System.currentTimeMillis();
      if (var2 instanceof LivingEntity var5 && var5.isAlive() && var5 != MC.player) {
         this.lastTarget = var5;
         this.lastSeen = var3;
      }

      LivingEntity var28 = this.lastTarget;
      if (var28 != null && var28.isAlive() && var3 - this.lastSeen <= this.integer("linger")) {
         Font var6 = MC.font;
         int var7 = this.integer("width");
         byte var8 = 4;
         byte var9 = 6;
         int var10 = var8 * 3 + 9 + var9;
         String var11 = this.choice("corner");
         int var12 = this.integer("margin");
         int var13 = var1.guiWidth();
         int var14 = var1.guiHeight();
         int var15;
         int var16;
         if (var11.equals("Top")) {
            var15 = (var13 - var7) / 2;
            var16 = var12;
         } else if (var11.equals("Bottom")) {
            var15 = (var13 - var7) / 2;
            var16 = var14 - var12 - var10;
         } else if (var11.equals("Top Right")) {
            var15 = var13 - var12 - var7;
            var16 = var12;
         } else {
            var15 = var12;
            var16 = var12;
         }

         int var17 = ModuleRenderUtil.color(this, "c-bg", -1072688104);
         int var18 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         int var19 = ModuleRenderUtil.color(this, "c-bar", -12933547) | 0xFF000000;
         int var20 = ModuleRenderUtil.color(this, "c-bar-bg", -14012618) | 0xFF000000;
         var1.fill(var15, var16, var15 + var7, var16 + var10, var17);
         String var21 = var28.getName().getString();
         float var22 = var28.getHealth();
         float var23 = Math.max(1.0F, var28.getMaxHealth());
         float var24 = Math.max(0.0F, Math.min(1.0F, var22 / var23));
         var1.text(var6, Component.literal(var21), var15 + var8, var16 + var8, var18);
         String var25 = (int)Math.ceil(var22) + "/" + (int)Math.ceil(var23);
         var1.text(var6, Component.literal(var25), var15 + var7 - var8 - var6.width(var25), var16 + var8, var18);
         int var26 = var16 + var8 + 9 + var8;
         int var27 = var7 - var8 * 2;
         var1.fill(var15 + var8, var26, var15 + var8 + var27, var26 + var9, var20);
         var1.fill(var15 + var8, var26, var15 + var8 + (int)(var27 * var24), var26 + var9, var19);
      }
   }
}
