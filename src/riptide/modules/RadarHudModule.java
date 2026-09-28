package riptide.modules;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class RadarHudModule extends Module {
   private static RadarHudModule cached;

   public RadarHudModule() {
      super("radar-hud", "Radar", ModuleCategory.RENDER, "A north-up radar of nearby players, mobs and items.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the radar sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 6, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new IntSetting("size", "Size", 96, 48, 200, 4).description("Radar width/height, in pixels.").build());
      this.add(new IntSetting("range", "Range", 64, 16, 256, 4).description("How far the edge of the radar reaches, in blocks.").build());
      this.add(new BoolSetting("players", "Players", true).description("Plot players.").build());
      this.add(new BoolSetting("mobs", "Mobs", true).description("Plot mobs.").build());
      this.add(new BoolSetting("items", "Items", false).description("Plot dropped items.").build());
      this.add(new ColorSetting("c-bg", "Background", -1609559016).group("Colors").build());
      this.add(new ColorSetting("c-grid", "Grid", 1090519039).group("Colors").build());
      this.add(new ColorSetting("c-you", "You", -1).group("Colors").build());
      this.add(new ColorSetting("c-player", "Players", -11890433).group("Colors").build());
      this.add(new ColorSetting("c-hostile", "Hostile", -2080722).group("Colors").build());
      this.add(new ColorSetting("c-passive", "Passive", -11024307).group("Colors").build());
      this.add(new ColorSetting("c-item", "Items", -678365).group("Colors").build());
   }

   private static RadarHudModule instance() {
      RadarHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("radar-hud") instanceof RadarHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      RadarHudModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive()) {
         if (MC != null && MC.player != null && MC.level != null && !MC.gui.hud.isHidden()) {
            try {
               var1.draw(var0);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor var1) {
      int var2 = this.integer("size");
      boolean var3 = this.choice("corner").contains("Right");
      boolean var4 = this.choice("corner").contains("Bottom");
      int var5 = this.integer("margin");
      int var6 = var3 ? var1.guiWidth() - var5 - var2 : var5;
      int var7 = var4 ? var1.guiHeight() - var5 - var2 : var5;
      int var8 = var6 + var2 / 2;
      int var9 = var7 + var2 / 2;
      int var10 = ModuleRenderUtil.color(this, "c-bg", -1609559016);
      int var11 = ModuleRenderUtil.color(this, "c-grid", 1090519039);
      var1.fill(var6, var7, var6 + var2, var7 + var2, var10);
      var1.fill(var8, var7, var8 + 1, var7 + var2, var11);
      var1.fill(var6, var9, var6 + var2, var9 + 1, var11);
      double var12 = this.integer("range");
      double var14 = var2 / 2.0 / var12;
      boolean var16 = this.bool("players");
      boolean var17 = this.bool("mobs");
      boolean var18 = this.bool("items");
      int var19 = ModuleRenderUtil.color(this, "c-player", -11890433) | 0xFF000000;
      int var20 = ModuleRenderUtil.color(this, "c-hostile", -2080722) | 0xFF000000;
      int var21 = ModuleRenderUtil.color(this, "c-passive", -11024307) | 0xFF000000;
      int var22 = ModuleRenderUtil.color(this, "c-item", -678365) | 0xFF000000;
      double var23 = MC.player.getX();
      double var25 = MC.player.getZ();
      int var27 = var2 / 2 - 1;

      for (Entity var29 : MC.level.entitiesForRendering()) {
         if (var29 != MC.player) {
            int var30;
            if (var29 instanceof Player) {
               if (!var16) {
                  continue;
               }

               var30 = var19;
            } else if (var29 instanceof LivingEntity var31) {
               if (!var17) {
                  continue;
               }

               var30 = var31 instanceof Enemy ? var20 : var21;
            } else {
               if (!var18 || !"ItemEntity".equals(var29.getClass().getSimpleName())) {
                  continue;
               }

               var30 = var22;
            }

            double var41 = (var29.getX() - var23) * var14;
            double var33 = (var29.getZ() - var25) * var14;
            if (!(Math.abs(var41) > var27) && !(Math.abs(var33) > var27)) {
               int var35 = var8 + (int)Math.round(var41);
               int var36 = var9 + (int)Math.round(var33);
               var1.fill(var35 - 1, var36 - 1, var35 + 1, var36 + 1, var30);
            }
         }
      }

      int var39 = ModuleRenderUtil.color(this, "c-you", -1) | 0xFF000000;
      double var40 = Math.toRadians(MC.player.getYRot());
      double var42 = -Math.sin(var40);
      double var43 = Math.cos(var40);
      int var44 = var2 / 2 - 2;

      for (int var45 = 0; var45 <= var44; var45++) {
         int var37 = var8 + (int)(var42 * var45);
         int var38 = var9 + (int)(var43 * var45);
         var1.fill(var37, var38, var37 + 1, var38 + 1, var39);
      }

      var1.fill(var8 - 1, var9 - 1, var8 + 2, var9 + 2, var39);
   }
}
