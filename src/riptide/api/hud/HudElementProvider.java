package riptide.api.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface HudElementProvider {
   String id();

   String label();

   default String description() {
      return "";
   }

   int width();

   int height();

   void render(GuiGraphicsExtractor var1, Font var2, int var3, int var4, float var5);

   default boolean defaultEnabled() {
      return false;
   }

   default String defaultAnchor() {
      return "TOP_LEFT";
   }

   default int defaultX() {
      return 0;
   }

   default int defaultY() {
      return 0;
   }
}
