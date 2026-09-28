package riptide.modules;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;

public final class RegionMapModule extends Module {
   private static final int GRID = 9;
   private static final String DEFAULT_LAYOUT = "82,100,101,102,103,104,105,106,91;83,44,75,42,41,40,39,38,92;84,45,14,13,12,11,10,37,93;85,46,74,3,2,1,25,36,94;86,47,72,71,5,4,24,35,95;87,51,17,9,8,7,23,34,96;88,54,18,61,62,21,22,33,97;89,26,27,28,29,30,59,32,98;90,107,108,109,110,111,112,113,99";
   private static final String DEFAULT_ZONES = "144455554;145555554;145555554;141155554;116655554;122223334;122223333;133333333;233333333";
   private static RegionMapModule cached;
   private int[][] layout;
   private int[][] zones;
   private String layoutRaw = "";
   private String zonesRaw = "";

   public RegionMapModule() {
      super("region-map", "Region Map", ModuleCategory.RENDER, "The DonutSMP RTP region grid, with the region you're in marked.");
      this.add(
         new IntSetting("region-size", "Region Size", 50000, 1000, 100000, 1000)
            .description("How many blocks wide one region is. DonutSMP uses 50,000.")
            .build()
      );
      this.add(
         new IntSetting("world-min", "World Min", -225000, -1000000, 0, 5000)
            .description("The X/Z of the map's top-left corner. DonutSMP starts at -225,000.")
            .build()
      );
      this.add(new IntSetting("cell", "Cell Pixels", 22, 12, 64, 1).description("Size of each region cell on screen.").build());
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the map sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 6, 0, 200, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new BoolSetting("numbers", "Show Numbers", true).description("Draw each region's number in its cell.").build());
      this.add(new BoolSetting("heading", "Show Heading", true).description("Draw an arrow for the way you're facing on your region.").build());
      this.add(new BoolSetting("header", "Show Current Region", true).description("Label the region you're in above the map.").build());
      this.add(new BoolSetting("legend", "Show Legend", true).description("List the datacenter colours below the map.").build());
      this.add(
         new StringSetting(
               "layout",
               "Region Numbers",
               "82,100,101,102,103,104,105,106,91;83,44,75,42,41,40,39,38,92;84,45,14,13,12,11,10,37,93;85,46,74,3,2,1,25,36,94;86,47,72,71,5,4,24,35,95;87,51,17,9,8,7,23,34,96;88,54,18,61,62,21,22,33,97;89,26,27,28,29,30,59,32,98;90,107,108,109,110,111,112,113,99"
            )
            .group("Data")
            .description("Nine rows of nine numbers, rows separated by ; and cells by , — edit to fix any cell.")
            .build()
      );
      this.add(
         new StringSetting("zone-map", "Datacenter Map", "144455554;145555554;145555554;141155554;116655554;122223334;122223333;133333333;233333333")
            .group("Data")
            .description("Nine rows of nine digits (1-6) picking each cell's datacenter colour.")
            .build()
      );
      this.add(new ColorSetting("c-1", "EU Central", -678365).group("Datacenters").build());
      this.add(new ColorSetting("c-2", "EU West", -12933547).group("Datacenters").build());
      this.add(new ColorSetting("c-3", "NA East", -7617718).group("Datacenters").build());
      this.add(new ColorSetting("c-4", "NA West", -10773547).group("Datacenters").build());
      this.add(new ColorSetting("c-5", "Asia", -12291388).group("Datacenters").build());
      this.add(new ColorSetting("c-6", "Oceania", -1538514).group("Datacenters").build());
      this.add(new ColorSetting("c-bg", "Background", -1728053248).group("Colors").build());
      this.add(new ColorSetting("c-grid", "Grid Lines", -14670804).group("Colors").build());
      this.add(new ColorSetting("c-me", "You Marker", -53200).group("Colors").build());
      this.add(new ColorSetting("c-num", "Numbers", -1).group("Colors").build());
      this.add(new ColorSetting("c-text", "Header Text", -2235414).group("Colors").build());
   }

   @Override
   public String info() {
      int[] var1 = currentCell();
      if (var1 == null) {
         return "";
      } else {
         int var2 = this.regionAt(var1[0], var1[1]);
         return var2 > 0 ? "R" + var2 : "";
      }
   }

   private static RegionMapModule instance() {
      RegionMapModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("region-map") instanceof RegionMapModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static int[] currentCell() {
      RegionMapModule var0 = instance();
      if (var0 != null && MC.player != null) {
         int var1 = var0.integer("region-size");
         int var2 = var0.integer("world-min");
         int var3 = Math.floorDiv((int)Math.floor(MC.player.getX()) - var2, var1);
         int var4 = Math.floorDiv((int)Math.floor(MC.player.getZ()) - var2, var1);
         return var3 >= 0 && var3 < 9 && var4 >= 0 && var4 < 9 ? new int[]{var3, var4} : null;
      } else {
         return null;
      }
   }

   private int regionAt(int var1, int var2) {
      this.refreshData();
      return this.layout != null && var2 >= 0 && var2 < this.layout.length && var1 >= 0 && var1 < this.layout[var2].length ? this.layout[var2][var1] : -1;
   }

   private void refreshData() {
      String var1 = this.text("layout");
      if (!var1.equals(this.layoutRaw)) {
         this.layoutRaw = var1;
         this.layout = parse(var1);
      }

      String var2 = this.text("zone-map");
      if (!var2.equals(this.zonesRaw)) {
         this.zonesRaw = var2;
         this.zones = parse(var2.replaceAll("(.)", "$1,"));
      }
   }

   private static int[][] parse(String var0) {
      String[] var1 = var0.split(";");
      int[][] var2 = new int[var1.length][];

      for (int var3 = 0; var3 < var1.length; var3++) {
         String[] var4 = var1[var3].trim().split("[,\\s]+");
         int[] var5 = new int[var4.length];

         for (int var6 = 0; var6 < var4.length; var6++) {
            try {
               var5[var6] = var4[var6].isEmpty() ? 0 : Integer.parseInt(var4[var6].trim());
            } catch (NumberFormatException var8) {
               var5[var6] = 0;
            }
         }

         var2[var3] = var5;
      }

      return var2;
   }

   public static void render(GuiGraphicsExtractor var0) {
      RegionMapModule var1 = instance();
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
      this.refreshData();
      if (this.layout != null) {
         Font var2 = MC.font;
         int var3 = this.integer("cell");
         boolean var4 = this.bool("numbers");
         boolean var5 = this.bool("header");
         boolean legend = this.bool("legend");
         String[] dcNames = new String[]{"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};
         String[] dcIds = new String[]{"c-1", "c-2", "c-3", "c-4", "c-5", "c-6"};
         int[] dcDefaults = new int[]{-678365, -12933547, -7617718, -10773547, -12291388, -1538514};
         int legendRow = 10;
         int legendH = legend ? dcNames.length * legendRow + 4 : 0;
         byte var6 = 4;
         int var7 = 9 * var3;
         int var8 = var5 ? 11 : 0;
         int var9 = var7 + var6 * 2;
         int var10 = var7 + var6 * 2 + var8 + legendH;
         int var11 = this.integer("margin");
         String var12 = this.choice("corner");
         int var13 = var1.guiWidth();
         int var14 = var1.guiHeight();
         int var15 = var12.contains("Right") ? var13 - var11 - var9 : var11;
         int var16 = var12.contains("Bottom") ? var14 - var11 - var10 : var11;
         int var17 = this.color("c-bg", -1728053248);
         int var18 = this.color("c-grid", -14670804) | 0xFF000000;
         int var19 = this.color("c-me", -53200) | 0xFF000000;
         int var20 = this.color("c-num", -1) | 0xFF000000;
         int var21 = this.color("c-text", -2235414) | 0xFF000000;
         var1.fill(var15, var16, var15 + var9, var16 + var10, var17);
         int var22 = var15 + var6;
         int var23 = var16 + var6 + var8;
         int[] var24 = currentCell();

         for (int var25 = 0; var25 < 9; var25++) {
            for (int var26 = 0; var26 < 9; var26++) {
               int var27 = var22 + var26 * var3;
               int var28 = var23 + var25 * var3;
               var1.fill(var27 + 1, var28 + 1, var27 + var3, var28 + var3, this.zoneColor(var25, var26));
               if (var4) {
                  int var29 = this.regionAt(var26, var25);
                  if (var29 > 0) {
                     String var30 = Integer.toString(var29);
                     int var31 = var2.width(var30);
                     var1.text(var2, Component.literal(var30), var27 + (var3 - var31) / 2 + 1, var28 + (var3 - 8) / 2 + 1, var20);
                  }
               }
            }
         }

         for (int var32 = 0; var32 <= 9; var32++) {
            int var35 = var22 + var32 * var3;
            int var38 = var23 + var32 * var3;
            var1.fill(var35, var23, var35 + 1, var23 + var7, var18);
            var1.fill(var22, var38, var22 + var7, var38 + 1, var18);
         }

         if (var24 != null) {
            int var33 = var22 + var24[0] * var3;
            int var36 = var23 + var24[1] * var3;
            var1.fill(var33, var36, var33 + var3 + 1, var36 + 2, var19);
            var1.fill(var33, var36 + var3 - 1, var33 + var3 + 1, var36 + var3 + 1, var19);
            var1.fill(var33, var36, var33 + 2, var36 + var3 + 1, var19);
            var1.fill(var33 + var3 - 1, var36, var33 + var3 + 1, var36 + var3 + 1, var19);
            if (this.bool("heading")) {
               this.drawHeading(var1, var33 + var3 / 2, var36 + var3 / 2, var3, var19);
            }
         }

         if (var5) {
            String var34;
            if (var24 != null) {
               int var37 = this.regionAt(var24[0], var24[1]);
               var34 = var37 > 0 ? "Region " + var37 : "Off map";
            } else {
               var34 = "Outside regions";
            }

            var1.text(var2, Component.literal(var34), var22, var16 + var6, var21);
         }

         if (legend) {
            int legendY = var23 + var7 + var6;

            for (int i = 0; i < dcNames.length; i++) {
               int swatch = this.color(dcIds[i], dcDefaults[i]) | 0xFF000000;
               int rowY = legendY + i * legendRow;
               var1.fill(var22, rowY, var22 + 7, rowY + 7, swatch);
               var1.text(var2, Component.literal(dcNames[i]), var22 + 11, rowY, var21);
            }
         }
      }
   }

   private void drawHeading(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5) {
      double var6 = Math.toRadians(MC.player.getYRot());
      double var8 = -Math.sin(var6);
      double var10 = Math.cos(var6);
      int var12 = var4 / 2;

      for (int var13 = 0; var13 <= var12; var13++) {
         int var14 = var2 + (int)(var8 * var13);
         int var15 = var3 + (int)(var10 * var13);
         var1.fill(var14, var15, var14 + 2, var15 + 2, var5);
      }
   }

   private int zoneColor(int var1, int var2) {
      int var3 = 4;
      if (this.zones != null && var1 < this.zones.length && var2 < this.zones[var1].length) {
         int var4 = this.zones[var1][var2];
         if (var4 >= 1 && var4 <= 6) {
            var3 = var4;
         }
      }

      return this.color("c-" + var3, -10773547) | 0xFF000000;
   }

   private int color(String var1, int var2) {
      return ModuleRenderUtil.color(this, var1, var2);
   }
}
