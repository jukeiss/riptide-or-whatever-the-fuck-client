package riptide.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import riptide.gui.vanillaui.TextWrapLayout;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.InspectorLayout;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.gui.vanillaui.direct.DirectScrollViewport;
import riptide.modules.RiptideAdminToolsBridge;

public class RiptideItemNbtInspectOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int SCROLLBAR_WIDTH = 4;
   private static RiptideItemNbtInspectOverlay sharedOverlay;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private RiptideItemNbtInspector.Inspection inspection;
   private DirectScrollViewport viewport;
   private List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> wrappedLines = Collections.emptyList();
   private boolean wrapDirty = true;
   private boolean wrappedRawView;
   private int wrappedWidth;
   private boolean rawView;
   private boolean dragging;
   private double dragOffsetX;
   private double dragOffsetY;

   public RiptideItemNbtInspectOverlay(Font textRenderer) {
      super("item-nbt-viewer", 280, 230);
      this.textRenderer = textRenderer;
      this.panelX = 110;
      this.panelY = 42;
   }

   public static RiptideItemNbtInspectOverlay getSharedOverlay(Font textRenderer) {
      if (textRenderer == null) {
         return null;
      } else {
         if (sharedOverlay == null) {
            sharedOverlay = new RiptideItemNbtInspectOverlay(textRenderer);
         }

         return sharedOverlay;
      }
   }

   public static void dismissShared() {
      if (sharedOverlay != null) {
         sharedOverlay.setVisible(false);
         RiptideOverlayManager.get().unregister(sharedOverlay);
      }
   }

   public static boolean openGlobal(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && MC.font != null) {
         RiptideItemNbtInspectOverlay overlay = getSharedOverlay(MC.font);
         if (overlay == null) {
            return false;
         } else {
            int screenW = MC.getWindow() == null ? 640 : RiptideUiScale.getVirtualScreenWidth();
            int x = Math.max(8, (screenW - 320) / 2);
            overlay.open(stack, x, 42);
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean openGlobal(RiptideItemNbtInspector.Inspection inspection) {
      if (inspection != null && MC.font != null) {
         RiptideItemNbtInspectOverlay overlay = getSharedOverlay(MC.font);
         if (overlay == null) {
            return false;
         } else {
            int screenW = MC.getWindow() == null ? 640 : RiptideUiScale.getVirtualScreenWidth();
            overlay.open(inspection, Math.max(8, (screenW - 320) / 2), 42);
            return true;
         }
      } else {
         return false;
      }
   }

   public void open(ItemStack stack, int anchorX, int anchorY) {
      if (stack != null && !stack.isEmpty()) {
         this.open(RiptideItemNbtInspector.inspect(stack), anchorX, anchorY);
      }
   }

   public void open(RiptideItemNbtInspector.Inspection inspection, int anchorX, int anchorY) {
      if (inspection != null) {
         RiptideOverlayManager manager = RiptideOverlayManager.get();
         manager.register(this);
         this.inspection = inspection;
         this.rawView = false;
         this.visible = true;
         this.collapsed = false;
         this.viewport = null;
         this.wrapDirty = true;
         int screenW = MC.getWindow() == null ? 640 : RiptideUiScale.getVirtualScreenWidth();
         int screenH = MC.getWindow() == null ? 360 : RiptideUiScale.getVirtualScreenHeight();
         int availableW = Math.max(1, screenW - 18);
         int availableH = Math.max(16, screenH - 18);
         int width = Math.min(320, Math.max(Math.min(260, availableW), Math.min(screenW / 3, availableW)));
         int height = Math.max(Math.min(230, availableH), Math.min(screenH / 2, availableH));
         this.setBounds(
            new RiptideWindowLayout(
               Math.max(8, Math.min(anchorX, screenW - width - 8)), Math.max(8, Math.min(anchorY, screenH - height - 8)), width, height, true, false
            )
         );
         manager.bringToFront(this);
      }
   }

   public void close() {
      this.visible = false;
      this.dragging = false;
      this.viewport = null;
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         RiptideWindowLayout bounds = this.getBounds();
         String title = this.inspection == null ? "NBT Inspector" : this.inspection.windowTitle();
         this.renderWindowFrame(context, mouseX, mouseY, bounds, title, this.collapsed, this.dragging);
         boolean clipped = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipped) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
         } else {
            try {
               if (this.inspection == null) {
                  CompactListRenderer.drawEmptyState(
                     context, this.textRenderer, "No item selected.", this.panelX + 8, this.panelY + 16 + 8, this.panelWidth - 16
                  );
                  return;
               }

               int tabY = this.panelY + 16 + 6;

               for (RiptideItemNbtInspectOverlay.FooterButton tab : this.tabButtons(tabY)) {
                  CompactOverlayControls.tab(
                     context,
                     this.textRenderer,
                     tab.x(),
                     tab.y(),
                     tab.width(),
                     this.buttonHeight(),
                     tab.label(),
                     "Raw".equals(tab.label()) == this.rawView,
                     mouseX,
                     mouseY
                  );
               }

               int listX = this.panelX + this.outerPad();
               int listY = tabY + this.buttonHeight() + 6;
               int listWidth = this.panelWidth - this.outerPad() * 2;
               int footerY = this.panelY + this.panelHeight - this.footerHeight() + this.footerTopInset();
               int listHeight = Math.max(1, footerY - listY - 5);
               int lineH = this.lineHeight();
               int textX = listX + this.innerPad();
               int maxTextWidth = InspectorLayout.contentWidth(listWidth, this.innerPad());
               this.ensureWrappedLines(maxTextWidth);
               if (this.viewport == null
                  || this.viewport.getX() != listX
                  || this.viewport.getY() != listY
                  || this.viewport.getWidth() != listWidth
                  || this.viewport.getHeight() != listHeight) {
                  int oldScroll = this.viewport == null ? 0 : this.viewport.getScrollOffset();
                  this.viewport = new DirectScrollViewport(listX, listY, listWidth, listHeight, lineH, 4);
                  this.viewport.jumpTo(oldScroll);
               }

               this.viewport.setContentHeight(this.wrappedLines.size() * lineH);
               this.viewport.beginRender(context, this.theme.borderSoft(), this.theme.listFill());

               try {
                  this.viewport.renderSimple(context, this.wrappedLines.size(), (idx, bnd) -> {
                     RiptideItemNbtInspectOverlay.WrappedInspectionLine line = this.wrappedLines.get(idx);
                     int y = UiSizing.alignTextY(bnd.y, lineH, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
                     this.drawInspectionLine(context, line, textX, y);
                  });
               } finally {
                  this.viewport.endRender(context);
               }

               this.viewport.renderScrollbar(context, mouseX, mouseY);
               UiText.draw(
                  context,
                  this.textRenderer,
                  this.wrappedLines.size() + " lines",
                  this.theme.fontFor(UiTone.MUTED),
                  this.theme.color(UiTone.MUTED),
                  this.panelX + this.outerPad() + 5,
                  footerY + this.footerLabelInset(),
                  false
               );

               for (RiptideItemNbtInspectOverlay.FooterButton button : this.footerButtons(footerY)) {
                  CompactOverlayControls.action(
                     context,
                     this.textRenderer,
                     button.x(),
                     button.y(),
                     button.width(),
                     this.buttonHeight(),
                     button.label(),
                     button.primary() ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST,
                     true,
                     mouseX,
                     mouseY
                  );
               }
            } finally {
               this.endWindowBodyClip(context, clipped);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (button == 0 && mouseY >= this.panelY && mouseY <= this.panelY + 16 && mouseX >= this.panelX && mouseX <= this.panelX + this.panelWidth) {
            if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
               this.close();
               return true;
            } else {
               this.dragging = true;
               this.dragOffsetX = mouseX - this.panelX;
               this.dragOffsetY = mouseY - this.panelY;
               return true;
            }
         } else if (this.collapsed) {
            return true;
         } else {
            if (button == 0) {
               int tabY = this.panelY + 16 + 6;

               for (RiptideItemNbtInspectOverlay.FooterButton tab : this.tabButtons(tabY)) {
                  if (tab.contains(mouseX, mouseY, this.buttonHeight())) {
                     this.rawView = "Raw".equals(tab.label());
                     this.wrapDirty = true;
                     if (this.viewport != null) {
                        this.viewport.jumpTo(0);
                     }

                     return true;
                  }
               }

               if (this.viewport != null && this.viewport.contains(mouseX, mouseY) && this.viewport.mouseClicked(mouseX, mouseY, button)) {
                  return true;
               }

               int footerY = this.panelY + this.panelHeight - this.footerHeight() + this.footerTopInset();

               for (RiptideItemNbtInspectOverlay.FooterButton footer : this.footerButtons(footerY)) {
                  if (footer.contains(mouseX, mouseY, this.buttonHeight())) {
                     this.handleFooter(footer.label());
                     return true;
                  }
               }
            }

            return this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0 && this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else if (button == 0 && this.viewport != null) {
         this.viewport.mouseReleased();
         return true;
      } else {
         this.dragging = false;
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.viewport != null) {
         this.viewport.mouseDragged(mouseX, mouseY);
      }

      if (this.visible && button == 0 && this.dragging) {
         this.setBounds(
            new RiptideWindowLayout(
               (int)Math.round(mouseX - this.dragOffsetX),
               (int)Math.round(mouseY - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.collapsed
            )
         );
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      return this.visible && !this.collapsed && this.viewport != null && this.viewport.contains(mouseX, mouseY)
         ? this.viewport.mouseScrolled(mouseX, mouseY, amount)
         : false;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return false;
   }

   @Override
   public void setVisible(boolean visible) {
      if (!visible) {
         this.close();
      } else {
         this.visible = true;
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         int height = this.collapsed ? 16 : this.panelHeight;
         return mouseX >= this.panelX && mouseX <= this.panelX + this.panelWidth && mouseY >= this.panelY && mouseY <= this.panelY + height;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      return !this.visible
         ? false
         : mouseX >= this.panelX
            && mouseX <= this.panelX + this.panelWidth
            && mouseY >= this.panelY
            && mouseY <= this.panelY + 16
            && !this.isOverWindowControl(mouseX, mouseY, this.getBounds());
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean collapsed) {
      if (this.collapsed != collapsed) {
         this.collapsed = collapsed;
         this.dragging = false;
         if (collapsed) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public int getZLevel() {
      return 13;
   }

   @Override
   public int getMinWidth() {
      return 240;
   }

   @Override
   public int getMinHeight() {
      return 190;
   }

   private List<RiptideItemNbtInspector.InspectionLine> currentLines() {
      if (this.inspection == null) {
         return List.of();
      } else {
         return this.rawView ? this.inspection.rawLines() : this.inspection.niceLines();
      }
   }

   private void ensureWrappedLines(int maxWidth) {
      if (this.wrapDirty || this.wrappedRawView != this.rawView || this.wrappedWidth != maxWidth) {
         if (this.inspection == null) {
            this.wrappedLines = Collections.emptyList();
            this.wrapDirty = false;
            this.wrappedRawView = this.rawView;
            this.wrappedWidth = maxWidth;
         } else {
            List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> wrapped = new ArrayList<>();

            for (RiptideItemNbtInspector.InspectionLine line : this.currentLines()) {
               this.wrapLine(line, maxWidth, wrapped);
            }

            this.wrappedLines = wrapped;
            this.wrapDirty = false;
            this.wrappedRawView = this.rawView;
            this.wrappedWidth = maxWidth;
         }
      }
   }

   private void wrapLine(RiptideItemNbtInspector.InspectionLine line, int maxWidth, List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> target) {
      if (line.tokens() != null && !line.tokens().isEmpty()) {
         this.wrapTokenLine(line.tokens(), maxWidth, target);
      } else {
         String text = line.text();
         if (text == null || text.isEmpty()) {
            target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.plain("", 0, line.color()));
         } else if (this.isSectionLine(text)) {
            this.wrapPlainText(text, 0, line.color(), maxWidth, target);
         } else {
            int leadingSpaces = this.countLeadingSpaces(text);
            String indentComponent = " ".repeat(leadingSpaces);
            int indentWidth = this.styledWidth(indentComponent);
            int colonIndex = text.indexOf(58, leadingSpaces);
            if (colonIndex > leadingSpaces && colonIndex < text.length() - 1) {
               String label = text.substring(leadingSpaces, colonIndex + 1).trim();
               String value = text.substring(colonIndex + 1).stripLeading();
               String prefix = indentComponent + label + " ";
               int prefixWidth = this.styledWidth(prefix);
               int continuationOffset = Math.min(prefixWidth, indentWidth + this.continuationIndent());
               this.wrapKeyValue(prefix, value, line.color(), prefixWidth, continuationOffset, maxWidth, target);
            } else {
               this.wrapPlainText(text.substring(leadingSpaces), indentWidth, line.color(), maxWidth, target);
            }
         }
      }
   }

   private void wrapKeyValue(
      String prefix,
      String value,
      int valueColor,
      int prefixWidth,
      int continuationOffset,
      int maxWidth,
      List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> target
   ) {
      int safeContinuationOffset = InspectorLayout.clampTextOffset(maxWidth, continuationOffset);
      if (prefixWidth >= maxWidth) {
         this.wrapPlainText(prefix.stripTrailing(), 0, RiptideColors.packetGray(), maxWidth, target);
         this.wrapPlainText(value, safeContinuationOffset, valueColor, maxWidth, target);
      } else if (value != null && !value.isEmpty()) {
         String remaining = value;

         for (boolean firstLine = true; !remaining.isEmpty(); firstLine = false) {
            int offset = firstLine ? prefixWidth : safeContinuationOffset;
            int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, offset);
            String current = remaining;
            int split = TextWrapLayout.nextLineEnd(
               current, 0, current.length(), availableWidth, (start, end) -> this.styledWidth(current.substring(start, end))
            );
            String part = remaining.substring(0, split).stripTrailing();
            target.add(this.keyValueWrappedLine(firstLine ? prefix : null, part, valueColor, offset));
            remaining = remaining.substring(split).stripLeading();
         }
      } else {
         target.add(
            RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(
               List.of(new RiptideItemNbtInspector.TextToken(prefix, RiptideColors.packetGray()), new RiptideItemNbtInspector.TextToken("", valueColor)), 0
            )
         );
      }
   }

   private void wrapPlainText(String text, int offset, int color, int maxWidth, List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> target) {
      if (text != null) {
         if (text.isEmpty()) {
            target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.plain("", offset, color));
         } else {
            int safeOffset = InspectorLayout.clampTextOffset(maxWidth, offset);
            String remaining = text;

            while (!remaining.isEmpty()) {
               int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, safeOffset);
               String current = remaining;
               int split = TextWrapLayout.nextLineEnd(
                  current, 0, current.length(), availableWidth, (start, end) -> this.styledWidth(current.substring(start, end))
               );
               String part = remaining.substring(0, split).stripTrailing();
               target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.plain(part, safeOffset, color));
               remaining = remaining.substring(split).stripLeading();
            }
         }
      }
   }

   private void drawInspectionLine(GuiGraphicsExtractor context, RiptideItemNbtInspectOverlay.WrappedInspectionLine line, int x, int y) {
      if (line != null && line.tokens() != null) {
         int cursor = x + line.offset();

         for (RiptideItemNbtInspector.TextToken token : line.tokens()) {
            if (token != null && token.text() != null && !token.text().isEmpty()) {
               UiText.draw(context, this.textRenderer, token.text(), this.theme.fontFor(UiTone.BODY), token.color(), cursor, y, false);
               cursor += this.styledWidth(token.text());
            }
         }
      }
   }

   private void wrapTokenLine(List<RiptideItemNbtInspector.TextToken> source, int maxWidth, List<RiptideItemNbtInspectOverlay.WrappedInspectionLine> target) {
      if (source != null && !source.isEmpty()) {
         int indent = this.leadingWhitespaceWidth(source);
         int continuationOffset = InspectorLayout.clampTextOffset(maxWidth, indent + this.continuationIndent());
         List<RiptideItemNbtInspector.TextToken> current = new ArrayList<>();
         int currentWidth = 0;
         boolean continuation = false;

         for (RiptideItemNbtInspector.TextToken token : source) {
            if (token != null && token.text() != null && !token.text().isEmpty()) {
               String remaining = token.text();

               while (!remaining.isEmpty()) {
                  int limit = maxWidth - (continuation ? continuationOffset : 0);
                  int room = Math.max(1, limit - currentWidth);
                  String piece = this.trimTokenToWidth(remaining, room);
                  if (piece.isEmpty()) {
                     if (!current.isEmpty()) {
                        target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                        current = new ArrayList<>();
                        currentWidth = 0;
                        continuation = true;
                        continue;
                     }

                     piece = remaining.substring(0, 1);
                  }

                  current.add(new RiptideItemNbtInspector.TextToken(piece, token.color()));
                  currentWidth += this.styledWidth(piece);
                  remaining = remaining.substring(piece.length());
                  if (!remaining.isEmpty()) {
                     target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                     current = new ArrayList<>();
                     currentWidth = 0;
                     continuation = true;
                     remaining = remaining.stripLeading();
                  } else if (currentWidth >= Math.max(22, limit - 4)) {
                     target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                     current = new ArrayList<>();
                     currentWidth = 0;
                     continuation = true;
                  }
               }
            }
         }

         if (!current.isEmpty()) {
            target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
         }
      } else {
         target.add(RiptideItemNbtInspectOverlay.WrappedInspectionLine.plain("", 0, RiptideColors.textMuted()));
      }
   }

   private RiptideItemNbtInspectOverlay.WrappedInspectionLine keyValueWrappedLine(String prefix, String value, int valueColor, int offset) {
      List<RiptideItemNbtInspector.TextToken> tokens = new ArrayList<>();
      if (prefix != null && !prefix.isEmpty()) {
         tokens.add(new RiptideItemNbtInspector.TextToken(prefix, RiptideColors.packetGray()));
      }

      tokens.add(new RiptideItemNbtInspector.TextToken(value == null ? "" : value, valueColor));
      return RiptideItemNbtInspectOverlay.WrappedInspectionLine.of(tokens, prefix != null && !prefix.isEmpty() ? 0 : offset);
   }

   private int leadingWhitespaceWidth(List<RiptideItemNbtInspector.TextToken> tokens) {
      int width = 0;

      for (RiptideItemNbtInspector.TextToken token : tokens) {
         if (token != null && token.text() != null && !token.text().isEmpty()) {
            String text = token.text();
            int spaces = 0;

            while (spaces < text.length() && Character.isWhitespace(text.charAt(spaces))) {
               spaces++;
            }

            if (spaces <= 0) {
               return width;
            }

            width += this.styledWidth(text.substring(0, spaces));
            if (spaces < text.length()) {
               return width;
            }
         }
      }

      return width;
   }

   private String trimTokenToWidth(String value, int maxWidth) {
      if (value != null && !value.isEmpty()) {
         if (this.styledWidth(value) <= maxWidth) {
            return value;
         } else {
            int low = 0;
            int high = value.length();

            while (low < high) {
               int mid = low + high + 1 >>> 1;
               String candidate = value.substring(0, mid);
               if (this.styledWidth(candidate) <= maxWidth) {
                  low = mid;
               } else {
                  high = mid - 1;
               }
            }

            return low <= 0 ? "" : value.substring(0, low);
         }
      } else {
         return "";
      }
   }

   private boolean isSectionLine(String text) {
      String trimmed = text == null ? "" : text.trim();
      return trimmed.startsWith("[") && trimmed.endsWith("]");
   }

   private int countLeadingSpaces(String text) {
      int count = 0;

      while (count < text.length() && text.charAt(count) == ' ') {
         count++;
      }

      return count;
   }

   private int styledWidth(String text) {
      return text != null && !text.isEmpty() ? UiText.width(this.textRenderer, text, this.theme.fontFor(UiTone.BODY), RiptideColors.packetWhite()) : 0;
   }

   private void handleFooter(String label) {
      if (this.inspection != null && MC.keyboardHandler != null) {
         switch (label) {
            case "Copy Raw":
               MC.keyboardHandler.setClipboard(this.inspection.rawCopyText());
               RiptideNotifications.copied("Copied raw " + this.inspection.subject() + " NBT.");
               break;
            case "Copy Nice":
               MC.keyboardHandler.setClipboard(this.inspection.prettyCopyText());
               RiptideNotifications.copied("Copied " + this.inspection.subject() + " NBT summary.");
               break;
            case "Fill Editor":
               if (!RiptideLiteVariant.enabled()
                  && this.inspection.stack() != null
                  && !this.inspection.stack().isEmpty()
                  && RiptideAdminToolsBridge.openFilledAdminEditor(this.inspection.stack())) {
                  RiptideNotifications.show("Filled Admin Tools with complete item NBT.", -13248397);
               } else {
                  RiptideClientMessaging.sendPrefixed("Admin Tools: could not fill NBT editor.");
               }
         }
      }
   }

   private List<RiptideItemNbtInspectOverlay.FooterButton> tabButtons(int y) {
      int x = this.panelX + this.outerPad();
      return List.of(
         new RiptideItemNbtInspectOverlay.FooterButton("Nice", x, y, 54, false), new RiptideItemNbtInspectOverlay.FooterButton("Raw", x + 60, y, 54, false)
      );
   }

   private List<RiptideItemNbtInspectOverlay.FooterButton> footerButtons(int y) {
      ArrayList<RiptideItemNbtInspectOverlay.FooterButton> buttons = new ArrayList<>();
      boolean editor = !RiptideLiteVariant.enabled() && this.inspection != null && this.inspection.stack() != null && !this.inspection.stack().isEmpty();
      if (this.panelWidth < 306) {
         int firstCursor = this.panelX + this.panelWidth - this.outerPad();
         if (editor) {
            this.addFooter(buttons, firstCursor, y + 14, "Fill Editor", true);
         }

         int secondCursor = this.panelX + this.panelWidth - this.outerPad();
         secondCursor = this.addFooter(buttons, secondCursor, y + 30, "Copy Raw", false);
         this.addFooter(buttons, secondCursor, y + 30, "Copy Nice", false);
         return buttons;
      } else {
         int cursor = this.panelX + this.panelWidth - this.outerPad();
         if (editor) {
            cursor = this.addFooter(buttons, cursor, y, "Fill Editor", true);
         }

         cursor = this.addFooter(buttons, cursor, y, "Copy Raw", false);
         this.addFooter(buttons, cursor, y, "Copy Nice", false);
         return buttons;
      }
   }

   private int addFooter(List<RiptideItemNbtInspectOverlay.FooterButton> buttons, int cursorX, int y, String label, boolean primary) {
      int width = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, label, 5, 42, 78);
      int x = cursorX - width;
      buttons.add(new RiptideItemNbtInspectOverlay.FooterButton(label, x, y, width, primary));
      return x - this.buttonGap();
   }

   private int outerPad() {
      return 7;
   }

   private int innerPad() {
      return 5;
   }

   private int footerHeight() {
      return this.panelWidth < 306 ? 50 : 24;
   }

   private int footerTopInset() {
      return 4;
   }

   private int footerLabelInset() {
      return 5;
   }

   private int buttonHeight() {
      return 14;
   }

   private int buttonGap() {
      return 4;
   }

   private int lineHeight() {
      return 12;
   }

   private int continuationIndent() {
      return 12;
   }

   private record FooterButton(String label, int x, int y, int width, boolean primary) {
      boolean contains(double mouseX, double mouseY, int height) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + height;
      }
   }

   private record WrappedInspectionLine(List<RiptideItemNbtInspector.TextToken> tokens, int offset) {
      static RiptideItemNbtInspectOverlay.WrappedInspectionLine plain(String text, int offset, int color) {
         return new RiptideItemNbtInspectOverlay.WrappedInspectionLine(List.of(new RiptideItemNbtInspector.TextToken(text, color)), offset);
      }

      static RiptideItemNbtInspectOverlay.WrappedInspectionLine of(List<RiptideItemNbtInspector.TextToken> tokens, int offset) {
         return new RiptideItemNbtInspectOverlay.WrappedInspectionLine(List.copyOf(tokens), offset);
      }
   }
}
