package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;

public final class RiptideFakeScoreboard {
   private static final int TITLE_COLOR = -10934;
   private static final int TEXT_COLOR = -1;
   private static final int BACKGROUND = -1879048192;
   private static final int TITLE_BACKGROUND = -1342177280;
   private static final int LINE_HEIGHT = 10;
   private static final int PAD = 3;

   private RiptideFakeScoreboard() {
   }

   private static Module module() {
      return ModuleRegistry.get("fake-scoreboard");
   }

   public static boolean active() {
      Module var0 = module();
      return var0 != null
         && var0.isEnabled()
         && !PackHideState.isActive()
         && Minecraft.getInstance().player != null
         && !Minecraft.getInstance().gui.hud.isHidden();
   }

   private static List<String> lines(Module var0) {
      ArrayList var1 = new ArrayList(6);

      for (int var2 = 1; var2 <= 6; var2++) {
         String var3 = var0.value("line" + var2);
         if (var3 != null && !var3.isBlank()) {
            var1.add(var3);
         }
      }

      return var1;
   }

   public static boolean render(GuiGraphicsExtractor var0) {
      if (!active()) {
         return false;
      } else {
         Module var1 = module();
         Minecraft var2 = Minecraft.getInstance();
         UiTextRenderer var3 = UiContexts.textRenderer(var2.font);
         String var4 = var1.value("title");
         if (var4 == null) {
            var4 = "";
         }

         List var5 = lines(var1);
         if (var4.isBlank() && var5.isEmpty()) {
            return false;
         } else {
            int var6 = var3.width(var4);

            for (String var8 : var5) {
               var6 = Math.max(var6, var3.width(var8));
            }

            int var19 = var6 + 6 + 4;
            int var20 = var5.size() * 10 + 6;
            byte var9 = 16;
            // Vanilla HUD space, not RiptideUiScale's fixed-scale space: those only match at GUI scale 2.
            int var10 = var0.guiWidth();
            int var11 = var0.guiHeight();
            boolean var12 = "Left".equals(var1.value("side"));
            int var13 = var12 ? 3 : var10 - var19 - 3;
            int var14 = Math.max(0, var11 / 2 - (var20 + var9) / 2 + parseOffset(var1));
            UiRenderer.rect(var0, UiBounds.of(var13, var14, var19, var9), -1342177280);
            var3.drawCentered(var0, var4, UiBounds.of(var13, var14, var19, var9), -10934);
            int var15 = var14 + var9;
            UiRenderer.rect(var0, UiBounds.of(var13, var15, var19, var20), -1879048192);
            int var16 = var15 + 3;

            for (String var18 : var5) {
               var3.draw(var0, var18, var13 + 3, var16, -1);
               var16 += 10;
            }

            return true;
         }
      }
   }

   private static int parseOffset(Module var0) {
      try {
         return Integer.parseInt(var0.value("offset"));
      } catch (NumberFormatException var2) {
         return 0;
      }
   }
}
