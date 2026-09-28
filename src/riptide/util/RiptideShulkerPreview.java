package riptide.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.modules.InventoryTweaksModule;

public final class RiptideShulkerPreview {
   private static final int COLUMNS = 9;
   private static final int MAX_ROWS = 3;
   private static final int SLOT = 18;
   private static final int CAPACITY = 27;
   private static final int CURSOR_GAP = 12;
   private static final int GAP = 4;
   private static final int SCREEN_MARGIN = 2;
   private static final int TOOLTIP_INSET = 6;
   private static final int TOOLTIP_LINE_HEIGHT = 10;
   private static final int TOOLTIP_MOUSE_DX = 12;
   private static final int TOOLTIP_MOUSE_DY = 12;
   private static final int SLOT_BG = -15068651;
   private static final int SLOT_BORDER = -7262667;
   private static final float ANIM_SECONDS = 0.12F;
   private static final long ANIM_RESET_GAP_MS = 120L;
   private static final float SLIDE_PIXELS = 5.0F;
   private static final ItemStack[] BUFFER = new ItemStack[27];
   private static int animKey = Integer.MIN_VALUE;
   private static float animProgress;
   private static long animLastMs;

   private RiptideShulkerPreview() {
   }

   public static boolean shouldPreview(ItemStack stack) {
      return InventoryTweaksModule.shulkerPreviewEnabled() && hasPreviewContents(stack);
   }

   private static boolean hasPreviewContents(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         return contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent();
      } else {
         return false;
      }
   }

   public static void render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int targetKey, int mouseX, int mouseY, int screenW, int screenH) {
      try {
         int count = fillItems(stack);
         if (count <= 0) {
            return;
         }

         int rows = Math.min(3, Math.max(1, (count + 9 - 1) / 9));
         int columns = Math.min(9, count);
         int width = columns * 18;
         int height = rows * 18;
         int[] placement = place(font, stack, mouseX, mouseY, screenW, screenH, width, height);
         if (placement == null) {
            return;
         }

         int x = placement[0];
         int y = placement[1];
         int awayX = placement[2];
         int awayY = placement[3];
         float eased = advanceAnimation(targetKey);
         float slide = (1.0F - eased) * 5.0F;
         graphics.pose().pushMatrix();
         graphics.pose().translate(awayX * slide, awayY * slide);

         try {
            drawGrid(graphics, font, x, y, count);
         } finally {
            graphics.pose().popMatrix();
         }
      } catch (Throwable var24) {
      }
   }

   private static int[] place(Font font, ItemStack stack, int mouseX, int mouseY, int screenW, int screenH, int pw, int ph) {
      int[] tip = tooltipRect(font, stack, mouseX, mouseY, screenW, screenH);
      if (tip == null) {
         int x = mouseX + 12;
         if (x + pw > screenW - 2) {
            x = mouseX - pw - 12;
         }

         x = clamp(x, 2, screenW - 2 - pw);
         int y = mouseY - ph - 12;
         if (y < 2) {
            y = mouseY + 18;
         }

         y = clamp(y, 2, screenH - 2 - ph);
         return new int[]{x, y, 0, -1};
      } else {
         int ax = tip[0];
         int ay = tip[1];
         int aw = tip[2];
         int ah = tip[3];
         int alignedX = clamp(ax, 2, screenW - 2 - pw);
         int alignedY = clamp(ay, 2, screenH - 2 - ph);
         int aboveY = ay - 4 - ph;
         if (aboveY >= 2) {
            return new int[]{alignedX, aboveY, 0, -1};
         } else {
            int belowY = ay + ah + 4;
            if (belowY + ph <= screenH - 2) {
               return new int[]{alignedX, belowY, 0, 1};
            } else {
               int rightX = ax + aw + 4;
               if (rightX + pw <= screenW - 2) {
                  return new int[]{rightX, alignedY, 1, 0};
               } else {
                  int leftX = ax - 4 - pw;
                  return leftX >= 2 ? new int[]{leftX, alignedY, -1, 0} : null;
               }
            }
         }
      }
   }

   private static int[] tooltipRect(Font font, ItemStack stack, int mouseX, int mouseY, int screenW, int screenH) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) {
         return null;
      } else {
         List<Component> lines = Screen.getTooltipFromItem(mc, stack);
         if (lines != null && !lines.isEmpty()) {
            int textWidth = 0;

            for (Component line : lines) {
               textWidth = Math.max(textWidth, font.width(line));
            }

            int textHeight = lines.size() == 1 ? 8 : lines.size() * 10;
            int posX = mouseX + 12;
            int posY = mouseY - 12;
            if (posX + textWidth > screenW) {
               posX = Math.max(posX - 24 - textWidth, 4);
            }

            int paddedHeight = textHeight + 3;
            if (posY + paddedHeight > screenH) {
               posY = screenH - paddedHeight;
            }

            return new int[]{posX - 6, posY - 6, textWidth + 12, textHeight + 12};
         } else {
            return null;
         }
      }
   }

   private static void drawGrid(GuiGraphicsExtractor graphics, Font font, int x, int y, int count) {
      for (int index = 0; index < count; index++) {
         int slotX = x + index % 9 * 18;
         int slotY = y + index / 9 * 18;
         UiBounds cell = UiBounds.of(slotX, slotY, 18, 18);
         UiRenderer.rect(graphics, cell, -15068651);
         UiRenderer.outline(graphics, cell, -7262667);
         ItemStack item = BUFFER[index];
         if (item != null && !item.isEmpty()) {
            graphics.item(item, slotX + 1, slotY + 1);
            graphics.itemDecorations(font, item, slotX + 1, slotY + 1);
         }
      }
   }

   private static float advanceAnimation(int targetKey) {
      long now = System.currentTimeMillis();
      if (targetKey != animKey || now - animLastMs > 120L) {
         animKey = targetKey;
         animProgress = 0.0F;
      }

      float dt = (float)Math.min(64L, Math.max(0L, now - animLastMs)) / 1000.0F;
      animLastMs = now;
      animProgress = Math.min(1.0F, animProgress + dt / 0.12F);
      float inverse = 1.0F - animProgress;
      return 1.0F - inverse * inverse * inverse;
   }

   private static int fillItems(ItemStack stack) {
      Arrays.fill(BUFFER, ItemStack.EMPTY);
      ItemContainerContents contents = stack == null ? null : (ItemContainerContents)stack.get(DataComponents.CONTAINER);
      if (contents == null) {
         return 0;
      } else {
         int filled = 0;
         Iterator<ItemStack> iterator = contents.nonEmptyItemCopyStream().iterator();

         while (iterator.hasNext() && filled < 27) {
            ItemStack item = iterator.next();
            if (item != null && !item.isEmpty()) {
               BUFFER[filled++] = item;
            }
         }

         return filled;
      }
   }

   private static int clamp(int value, int min, int max) {
      return max < min ? min : Math.max(min, Math.min(max, value));
   }
}
