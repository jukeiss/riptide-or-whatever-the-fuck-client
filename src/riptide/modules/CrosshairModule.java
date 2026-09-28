package riptide.modules;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideUiScale;

public final class CrosshairModule extends Module {
   private static final String CROSS = "Cross";
   private static final String DOT = "Dot";
   private static final String CROSS_DOT = "Cross And Dot";
   private static final String NONE = "Nothing";
   private static volatile CrosshairModule instance;

   public CrosshairModule() {
      super("crosshair", "Crosshair", ModuleCategory.RENDER, "Draws a crosshair that does not vanish against grey.");
      instance = this;
      this.add(
         new ChoiceSetting("style", "Style", "Cross", "Cross", "Dot", "Cross And Dot", "Nothing")
            .description("What to draw in the middle of the screen.")
            .group("Shape")
            .build()
      );
      this.add(
         new IntSetting("length", "Arm Length", 4, 1, 12, 1)
            .unit("px")
            .description("How long each arm of the cross is.")
            .visibleWhen(() -> !"Dot".equals(this.choice("style")) && !"Nothing".equals(this.choice("style")))
            .group("Shape")
            .build()
      );
      this.add(
         new IntSetting("gap", "Centre Gap", 2, 0, 10, 1)
            .unit("px")
            .description("Space left clear in the middle, so the arms do not cover your target.")
            .visibleWhen(() -> !"Dot".equals(this.choice("style")) && !"Nothing".equals(this.choice("style")))
            .group("Shape")
            .build()
      );
      this.add(
         new IntSetting("thickness", "Thickness", 1, 1, 4, 1)
            .unit("px")
            .description("How thick the lines are.")
            .visibleWhen(() -> !"Nothing".equals(this.choice("style")))
            .group("Shape")
            .build()
      );
      this.add(
         new ColorSetting("color", "Colour", -1)
            .description("Colour of the crosshair.")
            .visibleWhen(() -> !"Nothing".equals(this.choice("style")))
            .group("Appearance")
            .build()
      );
      this.add(
         new BoolSetting("outline", "Outline", true)
            .description("Draw a dark edge around it, so it stays visible on a light background.")
            .visibleWhen(() -> !"Nothing".equals(this.choice("style")))
            .group("Appearance")
            .build()
      );
   }

   public static boolean replacesVanilla() {
      CrosshairModule var0 = instance;
      return var0 != null && var0.isEnabled();
   }

   public static void draw(GuiGraphicsExtractor var0) {
      CrosshairModule var1 = instance;
      if (var1 != null && var1.isEnabled() && var0 != null) {
         String var2 = var1.choice("style");
         if (!"Nothing".equals(var2)) {
            int var3 = RiptideUiScale.getVirtualScreenWidth() / 2;
            int var4 = RiptideUiScale.getVirtualScreenHeight() / 2;
            int var5 = ModuleRenderUtil.color(var1, "color", -1);
            int var6 = Math.max(1, var1.integer("thickness"));
            if (var1.bool("outline")) {
               drawShape(var0, var2, var3, var4, var1, var6 + 2, -1073741824, true);
            }

            drawShape(var0, var2, var3, var4, var1, var6, var5, false);
         }
      }
   }

   private static void drawShape(GuiGraphicsExtractor var0, String var1, int var2, int var3, CrosshairModule var4, int var5, int var6, boolean var7) {
      int var8 = var5 / 2;
      int var9 = var7 ? 1 : 0;
      if (!"Dot".equals(var1)) {
         int var10 = Math.max(1, var4.integer("length"));
         int var11 = Math.max(0, var4.integer("gap"));
         UiRenderer.rect(var0, UiBounds.of(var2 - var11 - var10 - var9, var3 - var8, var10 + var9, var5), var6);
         UiRenderer.rect(var0, UiBounds.of(var2 + var11 + 1, var3 - var8, var10 + var9, var5), var6);
         UiRenderer.rect(var0, UiBounds.of(var2 - var8, var3 - var11 - var10 - var9, var5, var10 + var9), var6);
         UiRenderer.rect(var0, UiBounds.of(var2 - var8, var3 + var11 + 1, var5, var10 + var9), var6);
      }

      if ("Dot".equals(var1) || "Cross And Dot".equals(var1)) {
         int var12 = Math.max(1, var5);
         UiRenderer.rect(var0, UiBounds.of(var2 - var12 / 2, var3 - var12 / 2, var12, var12), var6);
      }
   }

   static int widthOf(String var0, int var1, int var2, int var3) {
      if ("Nothing".equals(var0)) {
         return 0;
      } else {
         return "Dot".equals(var0) ? Math.max(1, var3) : 2 * (Math.max(1, var1) + Math.max(0, var2)) + 1;
      }
   }

   @Override
   public String info() {
      return "Nothing".equals(this.choice("style")) ? "hidden" : this.choice("style").toLowerCase();
   }
}
