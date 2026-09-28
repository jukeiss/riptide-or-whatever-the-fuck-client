package riptide.modules;

import java.time.LocalTime;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class InfoHudModule extends Module {
   private static InfoHudModule cached;
   private static final String[] DIRS = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

   public InfoHudModule() {
      super("info-hud", "Info HUD", ModuleCategory.RENDER, "A corner panel with FPS, coordinates, facing, speed and biome.");
      this.add(new BoolSetting("fps", "FPS", true).description("Show the current framerate.").build());
      this.add(new BoolSetting("coords", "Coordinates", true).description("Show your X Y Z.").build());
      this.add(new BoolSetting("direction", "Direction", true).description("Show the cardinal direction you face.").build());
      this.add(new BoolSetting("speed", "Speed", false).description("Show your horizontal speed in blocks/second.").build());
      this.add(new BoolSetting("biome", "Biome", false).description("Show the biome you're standing in.").build());
      this.add(new BoolSetting("ping", "Ping", false).description("Show your latency to the server, in ms.").build());
      this.add(new BoolSetting("day", "In-game Day", false).description("Show the current world day number.").build());
      this.add(new BoolSetting("clock", "Real Clock", false).description("Show your real-world time.").build());
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the panel sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 4, 0, 200, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("hide-dup", "Hide When Duplicated", true).description("Hide any line the draggable HUD elements already show.").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
   }

   private static InfoHudModule instance() {
      InfoHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("info-hud") instanceof InfoHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      InfoHudModule var1 = instance();
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
      ArrayList var2 = new ArrayList(5);
      if (this.bool("fps") && !this.dup("fps")) {
         var2.add("FPS: " + MC.getFps());
      }

      if (this.bool("coords") && !this.dup("coordinates")) {
         BlockPos var3 = MC.player.blockPosition();
         var2.add("XYZ: " + var3.getX() + " " + var3.getY() + " " + var3.getZ());
      }

      if (this.bool("direction") && !this.dup("rotation")) {
         float var20 = MC.player.getYRot() % 360.0F;
         if (var20 < 0.0F) {
            var20 += 360.0F;
         }

         var2.add("Facing: " + DIRS[Math.round(var20 / 45.0F) & 7]);
      }

      if (this.bool("speed") && !this.dup("speed")) {
         double var21 = MC.player.getX() - MC.player.xOld;
         double var5 = MC.player.getZ() - MC.player.zOld;
         double var7 = Math.sqrt(var21 * var21 + var5 * var5) * 20.0;
         var2.add(String.format("Speed: %.1f b/s", var7));
      }

      if (this.bool("biome") && !this.dup("biome")) {
         String var22 = biomeName();
         if (!var22.isEmpty()) {
            var2.add("Biome: " + var22);
         }
      }

      if (this.bool("ping") && !this.dup("ping")) {
         int var23 = ownPing();
         if (var23 >= 0) {
            var2.add("Ping: " + var23 + "ms");
         }
      }

      if (this.bool("day") && !this.dup("world_time")) {
         var2.add("Day: " + MC.level.getGameTime() / 24000L);
      }

      if (this.bool("clock") && !this.dup("real_time")) {
         var2.add("Time: " + LocalTime.now().withNano(0).toString());
      }

      if (!var2.isEmpty()) {
         Font var24 = MC.font;
         byte var4 = 3;
         byte var25 = 10;
         int var6 = 0;

         for (String var8 : var2) {
            var6 = Math.max(var6, var24.width(var8));
         }

         int var27 = var6 + var4 * 2;
         int var28 = var2.size() * var25 + var4 * 2 - (var25 - 9);
         String var9 = this.choice("corner");
         int var10 = this.integer("margin");
         int var11 = var1.guiWidth();
         int var12 = var1.guiHeight();
         int var13 = var9.contains("Right") ? var11 - var10 - var27 : var10;
         int var14 = HudStack.y(var9, var10, var28, var12);
         int var15 = ModuleRenderUtil.color(this, "c-bg", -1879048192);
         int var16 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         if (var15 >>> 24 != 0) {
            var1.fill(var13, var14, var13 + var27, var14 + var28, var15);
         }

         int var17 = var14 + var4;

         for (String var19 : var2) {
            var1.text(var24, Component.literal(var19), var13 + var4, var17, var16);
            var17 += var25;
         }
      }
   }

   /** True when the draggable HUD element of this id already shows the same line. */
   private boolean dup(String elementId) {
      return this.bool("hide-dup") && HudDuplicate.shows(elementId);
   }

   private static int ownPing() {
      try {
         ClientPacketListener var0 = MC.getConnection();
         if (var0 == null) {
            return -1;
         } else {
            PlayerInfo var1 = var0.getPlayerInfo(MC.player.getUUID());
            return var1 == null ? -1 : var1.getLatency();
         }
      } catch (Throwable var2) {
         return -1;
      }
   }

   private static String biomeName() {
      try {
         return MC.level.getBiome(MC.player.blockPosition()).unwrapKey().map(var0 -> {
            String var1x = var0.identifier().getPath();
            return var1x.substring(0, 1).toUpperCase() + var1x.substring(1).replace('_', ' ');
         }).orElse("");
      } catch (Throwable var1) {
         return "";
      }
   }
}
