package riptide.gui.macro.editor.components;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.util.RiptideTheme;

public final class MacroTypedListControl {
   private static final int GAP = 2;
   private static final int ADD_WIDTH = 34;
   private static final int SLOT_WIDTH = 44;

   private MacroTypedListControl() {
   }

   public static MacroTypedListControl.ToolbarLayout toolbar(UiBounds bounds, boolean slotField, boolean capture) {
      int captureWidth = capture ? 52 : 0;
      int captureX = bounds.right() - captureWidth;
      int addX = (capture ? captureX : bounds.right()) - 3 - 34;
      int slotX = slotField ? addX - 2 - 44 : 0;
      int textRight = slotField ? slotX : addX;
      return new MacroTypedListControl.ToolbarLayout(
         UiBounds.of(bounds.x(), bounds.y(), Math.max(1, textRight - bounds.x() - 2), 14),
         slotField ? UiBounds.of(slotX, bounds.y(), 44, 14) : null,
         UiBounds.of(addX, bounds.y(), 34, 14),
         capture ? UiBounds.of(captureX, bounds.y(), captureWidth, 14) : null
      );
   }

   public static <T> List<Integer> refilter(List<T> values, List<Integer> target, Predicate<T> predicate) {
      target.clear();
      if (values != null && predicate != null) {
         for (int index = 0; index < values.size(); index++) {
            if (predicate.test(values.get(index))) {
               target.add(index);
            }
         }

         return target;
      } else {
         return target;
      }
   }

   public static <T> List<T> refilterValues(List<T> values, List<T> target, Predicate<T> predicate) {
      target.clear();
      if (values != null && predicate != null) {
         for (T value : values) {
            if (predicate.test(value)) {
               target.add(value);
            }
         }

         return target;
      } else {
         return target;
      }
   }

   public static void renderRow(
      GuiGraphicsExtractor graphics,
      Font font,
      Component label,
      ItemStack icon,
      UiBounds bounds,
      boolean hovered,
      boolean selected,
      CompactListRenderer.RowTone tone,
      boolean minecraftText
   ) {
      int color = switch (tone == null ? CompactListRenderer.RowTone.NORMAL : tone) {
         case READY -> selected ? -1507344 : -6426438;
         case WARNING -> selected ? -3104 : -14726;
         case MISSING -> selected ? -4884 : -20561;
         case DANGER -> selected ? -5398 : -29556;
         case NORMAL -> selected ? RiptideTheme.recolor(-2828, RiptideTheme.Channel.TEXT) : -1;
      };
      CompactListRenderer.drawRowWithColorAndIcon(
         graphics,
         font,
         label,
         icon == null ? ItemStack.EMPTY : icon,
         bounds.x(),
         bounds.y(),
         bounds.width(),
         bounds.height(),
         hovered,
         selected,
         color,
         minecraftText
      );
   }

   public static void renderRow(
      GuiGraphicsExtractor graphics,
      Font font,
      Component label,
      UiBounds bounds,
      boolean hovered,
      boolean selected,
      CompactListRenderer.RowTone tone,
      boolean minecraftText
   ) {
      renderRow(graphics, font, label, ItemStack.EMPTY, bounds, hovered, selected, tone, minecraftText);
   }

   public static void renderRowWithColor(
      GuiGraphicsExtractor graphics, Font font, Component label, UiBounds bounds, boolean hovered, boolean selected, int textColor, boolean minecraftText
   ) {
      CompactListRenderer.drawRowWithColor(
         graphics, font, label, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered, selected, textColor, minecraftText
      );
   }

   public static void renderDelete(GuiGraphicsExtractor graphics, UiBounds bounds, boolean hovered) {
      CompactListRenderer.drawDeleteButton(graphics, bounds.x(), bounds.y() + 1, bounds.width(), Math.max(1, bounds.height() - 2), hovered);
   }

   public static void renderSymbol(GuiGraphicsExtractor graphics, UiBounds bounds, String symbol, boolean hovered, boolean danger) {
      CompactListRenderer.drawStructuralButton(graphics, bounds.x(), bounds.y(), bounds.width(), symbol, hovered, danger);
   }

   public record ToolbarLayout(UiBounds text, UiBounds slot, UiBounds add, UiBounds capture) {
   }
}
