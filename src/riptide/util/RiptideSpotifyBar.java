package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;

public final class RiptideSpotifyBar {
   private static final int BAR_HEIGHT = 22;
   private static final int BUTTON_SIZE = 16;
   private static final int PAD = 4;
   private static int barX;
   private static int barY;
   private static int barWidth;
   private static boolean visibleLastFrame;
   private static boolean dragging;
   private static int dragGrabX;
   private static int dragGrabY;

   private RiptideSpotifyBar() {
   }

   private static Module module() {
      return ModuleRegistry.get("spotify-controls");
   }

   private static boolean appliesTo(Screen var0) {
      Module var1 = module();
      if (var0 != null && var1 != null && var1.isEnabled() && !PackHideState.isActive()) {
         Minecraft var2 = Minecraft.getInstance();
         return var2.player == null ? false : "All Screens".equals(var1.value("where")) || var0 instanceof AbstractContainerScreen;
      } else {
         return false;
      }
   }

   private static boolean hasTrack(RiptideSpotify.Snapshot var0) {
      return var0 != null
         && var0.status() != RiptideSpotify.Status.UNAVAILABLE
         && var0.status() != RiptideSpotify.Status.STOPPED
         && var0.title() != null
         && !var0.title().isBlank();
   }

   public static void render(GuiGraphicsExtractor var0, Screen var1) {
      visibleLastFrame = false;
      if (appliesTo(var1)) {
         RiptideSpotify.Snapshot var2 = RiptideSpotify.snapshot();
         Module var3 = module();
         if (hasTrack(var2) || Boolean.parseBoolean(var3.value("always-show"))) {
            Minecraft var4 = Minecraft.getInstance();
            UiTextRenderer var5 = UiContexts.textRenderer(var4.font);
            int var6 = RiptideUiScale.getVirtualScreenWidth();
            int var7 = RiptideUiScale.getVirtualScreenHeight();
            barWidth = Math.min(var6 - 8, 190);
            int var8 = (var6 - barWidth) / 2;
            int var9 = "Top".equals(var3.value("anchor")) ? 4 : var7 - 22 - 4;
            barX = clamp(setting(var3, "pos-x", var8), 0, Math.max(0, var6 - barWidth));
            barY = clamp(setting(var3, "pos-y", var9), 0, Math.max(0, var7 - 22));
            visibleLastFrame = true;
            UiBounds var10 = UiBounds.of(barX, barY, barWidth, 22);
            UiRenderer.roundRect(var0, var10, 4, -1072689132);
            UiRenderer.roundFrame(var0, var10, 4, 1, 1090519039);
            int var11 = barY + 3;
            drawPrevious(var0, UiBounds.of(barX + 4, var11, 16, 16));
            drawPlayPause(var0, UiBounds.of(barX + 4 + 16, var11, 16, 16), var2 != null && var2.status() == RiptideSpotify.Status.PLAYING);
            drawNext(var0, UiBounds.of(barX + 4 + 32, var11, 16, 16));
            int var12 = barX + 4 + 48 + 4;
            int var13 = barX + barWidth - 4 - var12;
            if (var13 > 10) {
               String var14 = !hasTrack(var2)
                  ? "Nothing playing"
                  : var2.title() + (var2.artist() != null && !var2.artist().isBlank() ? " — " + var2.artist() : "");
               var5.drawEllipsized(var0, var14, var12, var5.centeredY(var10), var13, -1513236);
            }
         }
      }
   }

   private static void drawPrevious(GuiGraphicsExtractor var0, UiBounds var1) {
      int var2 = var1.x() + var1.width() / 2;
      int var3 = var1.y() + var1.height() / 2;
      UiRenderer.rect(var0, UiBounds.of(var2 - 5, var3 - 4, 1, 8), -1513236);

      for (int var4 = 0; var4 < 5; var4++) {
         UiRenderer.rect(var0, UiBounds.of(var2 - 3 + var4, var3 - (var4 + 1), 1, (var4 + 1) * 2), -1513236);
      }
   }

   private static void drawNext(GuiGraphicsExtractor var0, UiBounds var1) {
      int var2 = var1.x() + var1.width() / 2;
      int var3 = var1.y() + var1.height() / 2;

      for (int var4 = 0; var4 < 5; var4++) {
         UiRenderer.rect(var0, UiBounds.of(var2 - 3 + var4, var3 - (5 - var4), 1, (5 - var4) * 2), -1513236);
      }

      UiRenderer.rect(var0, UiBounds.of(var2 + 4, var3 - 4, 1, 8), -1513236);
   }

   private static void drawPlayPause(GuiGraphicsExtractor var0, UiBounds var1, boolean var2) {
      int var3 = var1.x() + var1.width() / 2;
      int var4 = var1.y() + var1.height() / 2;
      if (var2) {
         UiRenderer.rect(var0, UiBounds.of(var3 - 3, var4 - 4, 2, 8), -1513236);
         UiRenderer.rect(var0, UiBounds.of(var3 + 1, var4 - 4, 2, 8), -1513236);
      } else {
         UiRenderer.play(var0, var3 - 3, var4 - 4, 8, -1513236);
      }
   }

   public static boolean mouseClicked(int var0, int var1, int var2, Screen var3) {
      if (var2 != 0 || !visibleLastFrame || !appliesTo(var3)) {
         return false;
      } else if (var0 >= barX && var0 < barX + barWidth && var1 >= barY && var1 < barY + 22) {
         int var4 = barY + 3;
         if (var1 >= var4 && var1 < var4 + 16) {
            int var5 = barX + 4;
            if (var0 >= var5 && var0 < var5 + 16) {
               RiptideSpotify.previous();
               return true;
            }

            if (var0 >= var5 + 16 && var0 < var5 + 32) {
               RiptideSpotify.togglePlayPause();
               return true;
            }

            if (var0 >= var5 + 32 && var0 < var5 + 48) {
               RiptideSpotify.next();
               return true;
            }
         }

         dragging = true;
         dragGrabX = var0 - barX;
         dragGrabY = var1 - barY;
         return true;
      } else {
         return false;
      }
   }

   public static boolean mouseDragged(int var0, int var1, Screen var2) {
      if (dragging && appliesTo(var2)) {
         Module var3 = module();
         int var4 = RiptideUiScale.getVirtualScreenWidth();
         int var5 = RiptideUiScale.getVirtualScreenHeight();
         barX = clamp(var0 - dragGrabX, 0, Math.max(0, var4 - barWidth));
         barY = clamp(var1 - dragGrabY, 0, Math.max(0, var5 - 22));
         var3.setValue("pos-x", Integer.toString(barX));
         var3.setValue("pos-y", Integer.toString(barY));
         return true;
      } else {
         return false;
      }
   }

   public static boolean mouseReleased(Screen var0) {
      if (!dragging) {
         return false;
      } else {
         dragging = false;
         return true;
      }
   }

   public static void resetPosition() {
      Module var0 = module();
      if (var0 != null) {
         var0.setValue("pos-x", "-1");
         var0.setValue("pos-y", "-1");
      }
   }

   private static int setting(Module var0, String var1, int var2) {
      try {
         int var3 = Integer.parseInt(var0.value(var1));
         return var3 < 0 ? var2 : var3;
      } catch (NumberFormatException var4) {
         return var2;
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }
}
