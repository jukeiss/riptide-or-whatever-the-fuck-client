package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.util.RiptideBindUtil;

public final class CompactKeybindButton {
   public static final int WIDTH = 34;
   public static final int HEIGHT = 11;

   private CompactKeybindButton() {
   }

   public static UiBounds atRowEnd(UiBounds row, int rightInset, int verticalInset) {
      int height = Math.max(1, row.height() - verticalInset * 2);
      return UiBounds.of(row.right() - 34 - rightInset, row.y() + verticalInset, 34, height);
   }

   public static String label(int bindCode, boolean capturing) {
      if (capturing) {
         return "Key";
      } else {
         return bindCode == -1 ? "Bind" : RiptideBindUtil.getBindName(bindCode);
      }
   }

   public static int keyOrClear(int keyCode) {
      return keyCode != 256 && keyCode != 259 && keyCode != 261 ? keyCode : -1;
   }

   public static void render(UiContext context, UiBounds bounds, int bindCode, boolean capturing, boolean hovered) {
      Button.render(context, bounds, label(bindCode, capturing), Button.Tone.NORMAL, hovered, capturing);
   }
}
