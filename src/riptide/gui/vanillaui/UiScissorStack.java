package riptide.gui.vanillaui;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.util.RiptideUiScale;

public final class UiScissorStack {
   private static final UiScissorStack GLOBAL = new UiScissorStack();
   private final Deque<UiBounds> stack = new ArrayDeque<>();

   public static UiScissorStack global() {
      return GLOBAL;
   }

   public void push(GuiGraphicsExtractor graphics, UiBounds bounds) {
      UiBounds clipped = bounds;
      if (!this.stack.isEmpty()) {
         UiBounds parent = this.stack.peek();
         int x0 = Math.max(parent.x(), bounds.x());
         int y0 = Math.max(parent.y(), bounds.y());
         int x1 = Math.min(parent.right(), bounds.right());
         int y1 = Math.min(parent.bottom(), bounds.bottom());
         clipped = UiBounds.of(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
      }

      this.stack.push(clipped);
      RiptideUiScale.enableOverlayScissor(graphics, clipped.x(), clipped.y(), clipped.right(), clipped.bottom());
   }

   public void pop(GuiGraphicsExtractor graphics) {
      if (!this.stack.isEmpty()) {
         this.stack.pop();
         this.disableScissorSafely(graphics);
      }
   }

   public void clear(GuiGraphicsExtractor graphics) {
      while (!this.stack.isEmpty()) {
         this.stack.pop();
         this.disableScissorSafely(graphics);
      }
   }

   private void disableScissorSafely(GuiGraphicsExtractor graphics) {
      if (graphics != null) {
         try {
            graphics.disableScissor();
         } catch (IllegalStateException var3) {
            this.stack.clear();
         }
      }
   }
}
