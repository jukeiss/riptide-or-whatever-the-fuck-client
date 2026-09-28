package riptide.gui.vanillaui.components;

import java.util.List;
import java.util.function.Consumer;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiComponent;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiInputResult;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideUiScale;

public final class Dropdown implements UiComponent {
   private static final int SCREEN_MARGIN = 4;
   private UiBounds bounds;
   private UiBounds menuBounds = UiBounds.of(0, 0, 0, 0);
   private List<String> options;
   private String selected;
   private final Consumer<String> onSelect;
   private int scroll;
   private int visibleRows;
   private boolean open;
   private long openedAtNanos;
   private boolean draggingScrollbar;
   private int scrollbarGrabOffset;
   private Scrollbar.Metrics scrollbar;
   private boolean menuWidthDirty = true;
   private int cachedMenuWidth = 1;
   private int cachedMenuScreenWidth = -1;
   private int lastScreenWidth = -1;
   private int lastScreenHeight = -1;
   private int lastRowHeight = -1;

   private static int lerpColor(int from, int to, float t) {
      int ar = from >> 16 & 0xFF;
      int ag = from >> 8 & 0xFF;
      int ab = from & 0xFF;
      int aa = from >>> 24 & 0xFF;
      int br = to >> 16 & 0xFF;
      int bg = to >> 8 & 0xFF;
      int bb = to & 0xFF;
      int ba = to >>> 24 & 0xFF;
      int r = Math.round(ar + (br - ar) * t);
      int g = Math.round(ag + (bg - ag) * t);
      int b = Math.round(ab + (bb - ab) * t);
      int a = Math.round(aa + (ba - aa) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public Dropdown(UiBounds bounds, List<String> options, String selected, Consumer<String> onSelect) {
      this.bounds = bounds == null ? UiBounds.of(0, 0, 0, 0) : bounds;
      this.options = options == null ? List.of() : List.copyOf(options);
      this.selected = selected == null ? "" : selected;
      this.onSelect = onSelect == null ? ignored -> {} : onSelect;
   }

   @Override
   public UiBounds bounds() {
      return this.bounds;
   }

   @Override
   public UiBounds hitBounds() {
      return this.open ? this.bounds.union(this.menuBounds) : this.bounds;
   }

   @Override
   public void setBounds(UiBounds bounds) {
      if (bounds != null) {
         if (this.bounds.width() != bounds.width()) {
            this.menuWidthDirty = true;
         }

         this.bounds = bounds;
      }
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOptions(List<String> options) {
      List<String> next = options == null ? List.of() : List.copyOf(options);
      if (!this.options.equals(next)) {
         this.options = next;
         this.menuWidthDirty = true;
         if (!this.options.contains(this.selected)) {
            this.selected = this.options.isEmpty() ? "" : this.options.get(0);
         }

         this.scroll = clamp(this.scroll, 0, this.maxScroll());
         this.ensureSelectedVisible();
      }
   }

   public void setSelected(String selected) {
      this.selected = selected == null ? "" : selected;
      this.ensureSelectedVisible();
   }

   public boolean containsMenu(int mouseX, int mouseY) {
      this.prepareInputLayout();
      return this.open && this.menuBounds.contains(mouseX, mouseY);
   }

   public void open() {
      this.open = true;
      this.openedAtNanos = System.nanoTime();
      this.ensureSelectedVisible();
      this.prepareInputLayout();
   }

   public void close() {
      this.open = false;
      this.openedAtNanos = 0L;
      this.draggingScrollbar = false;
   }

   public static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
      renderControl(context, bounds, label, hovered, open, false, true);
   }

   public static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open, boolean enabled) {
      renderControl(context, bounds, label, hovered, open, false, enabled);
   }

   public static void renderControlCentered(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
      renderControl(context, bounds, label, hovered, open, true, true);
   }

   private static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open, boolean centered, boolean enabled) {
      UiColors colors = context.theme().colors();
      int fill = !enabled ? colors.field : (open ? colors.fieldFocused : (hovered ? colors.rowHover : colors.field));
      UiRenderer.frame(context.graphics(), bounds, fill, open && enabled ? colors.accent : colors.borderSoft);
      int textColor = enabled ? colors.text : colors.disabled;
      if (centered) {
         context.text().drawCentered(context.graphics(), label, bounds, textColor);
      } else {
         context.text().drawFitted(context.graphics(), label, bounds.x() + 5, context.text().centeredY(bounds), Math.max(1, bounds.width() - 18), textColor);
      }

      UiRenderer.chevron(context.graphics(), UiBounds.of(bounds.right() - 10, bounds.y() + Math.max(0, (bounds.height() - 8) / 2), 8, 8), open, textColor);
   }

   @Override
   public void render(UiContext context) {
      if (this.open && !this.options.isEmpty()) {
         int rowHeight = 15;
         this.lastScreenWidth = context.screenWidth();
         this.lastScreenHeight = context.screenHeight();
         this.lastRowHeight = rowHeight;
         this.visibleRows = this.visibleRows(context.screenHeight(), rowHeight);
         int width = this.menuWidth(context);
         this.layoutMenu(context.screenWidth(), context.screenHeight(), rowHeight, width);
         UiColors colors = context.theme().colors();
         float fade = this.openedAtNanos == 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, (float)(System.nanoTime() - this.openedAtNanos) / 7.0E7F));
         UiRenderer.frame(context.graphics(), this.menuBounds, UiRenderer.applyAlpha(colors.windowStrong, fade), UiRenderer.applyAlpha(colors.borderSoft, fade));
         int contentWidth = this.options.size() > this.visibleRows ? this.menuBounds.width() - 6 - 3 : this.menuBounds.width() - 2;

         for (int rowIndex = 0; rowIndex < this.visibleRows; rowIndex++) {
            int optionIndex = this.scroll + rowIndex;
            if (optionIndex >= this.options.size()) {
               break;
            }

            String option = this.options.get(optionIndex);
            UiBounds row = UiBounds.of(this.menuBounds.x() + 1, this.menuBounds.y() + 1 + rowIndex * rowHeight, Math.max(1, contentWidth), rowHeight);
            boolean hovered = row.contains(context.mouseX(), context.mouseY());
            boolean active = option.equals(this.selected);
            float hoverT = HoverFades.get("dropdown:" + option, hovered);
            int rowFill = active ? colors.accentSoft : lerpColor(colors.row, colors.rowHover, hoverT);
            UiRenderer.rect(context.graphics(), row, UiRenderer.applyAlpha(rowFill, fade));
            context.text()
               .drawFitted(
                  context.graphics(),
                  option,
                  row.x() + 4,
                  context.text().centeredY(row),
                  Math.max(1, row.width() - 18),
                  UiRenderer.applyAlpha(colors.text, fade)
               );
            if (active) {
               UiRenderer.check(
                  context.graphics(),
                  UiBounds.of(row.right() - 11, row.y() + Math.max(0, (row.height() - 8) / 2), 8, 8),
                  UiRenderer.applyAlpha(colors.accent, fade)
               );
            }
         }

         if (this.options.size() > this.visibleRows) {
            UiBounds track = UiBounds.of(this.menuBounds.right() - 6 - 1, this.menuBounds.y() + 1, 6, Math.max(1, this.menuBounds.height() - 2));
            this.scrollbar = Scrollbar.metrics(track, this.options.size() * rowHeight, this.visibleRows * rowHeight, this.scroll * rowHeight);
            Scrollbar.render(context, this.scrollbar, this.scrollbar.track().contains(context.mouseX(), context.mouseY()), this.draggingScrollbar);
         } else {
            this.scrollbar = null;
         }
      }
   }

   @Override
   public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
      this.prepareInputLayout();
      if (button != 0) {
         if (!this.open) {
            return UiInputResult.IGNORED;
         } else {
            if (!this.bounds.contains(mouseX, mouseY) && !this.menuBounds.contains(mouseX, mouseY)) {
               this.close();
            }

            return UiInputResult.HANDLED;
         }
      } else if (this.open) {
         if (this.menuBounds.contains(mouseX, mouseY)) {
            if (this.scrollbar != null && this.scrollbar.track().contains(mouseX, mouseY)) {
               this.scrollbarGrabOffset = this.scrollbar.thumb().contains(mouseX, mouseY)
                  ? mouseY - this.scrollbar.thumb().y()
                  : this.scrollbar.thumb().height() / 2;
               this.draggingScrollbar = true;
               this.updateScrollbar(mouseY);
               return UiInputResult.HANDLED;
            } else {
               int index = this.scroll + Math.max(0, (mouseY - this.menuBounds.y() - 1) / Math.max(1, this.rowHeight()));
               if (index >= 0 && index < this.options.size()) {
                  this.selected = this.options.get(index);
                  this.onSelect.accept(this.selected);
               }

               this.close();
               return UiInputResult.HANDLED;
            }
         } else if (this.bounds.contains(mouseX, mouseY)) {
            this.close();
            return UiInputResult.HANDLED;
         } else {
            this.close();
            return UiInputResult.HANDLED;
         }
      } else if (this.bounds.contains(mouseX, mouseY)) {
         this.open();
         return UiInputResult.HANDLED;
      } else {
         return UiInputResult.IGNORED;
      }
   }

   @Override
   public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
      this.prepareInputLayout();
      if (!this.draggingScrollbar) {
         return this.open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
      } else {
         this.draggingScrollbar = false;
         return UiInputResult.HANDLED;
      }
   }

   @Override
   public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
      this.prepareInputLayout();
      if (this.draggingScrollbar && button == 0) {
         this.updateScrollbar(mouseY);
         return UiInputResult.HANDLED;
      } else {
         return this.open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
      }
   }

   @Override
   public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
      this.prepareInputLayout();
      if (!this.open) {
         return UiInputResult.IGNORED;
      } else {
         if (this.menuBounds.contains(mouseX, mouseY)) {
            this.scroll = clamp(this.scroll + (amount < 0.0 ? 1 : -1), 0, this.maxScroll());
         }

         return UiInputResult.HANDLED;
      }
   }

   private int menuWidth(UiContext context) {
      if (!this.menuWidthDirty && this.cachedMenuScreenWidth == context.screenWidth()) {
         return this.cachedMenuWidth;
      } else {
         int width = Math.max(1, this.bounds.width());

         for (String option : this.options) {
            width = Math.max(width, context.text().width(option) + 12);
         }

         this.cachedMenuWidth = Math.min(width, Math.max(1, context.screenWidth() - 8));
         this.cachedMenuScreenWidth = context.screenWidth();
         this.menuWidthDirty = false;
         return this.cachedMenuWidth;
      }
   }

   private void prepareInputLayout() {
      if (this.open) {
         int screenWidth = this.lastScreenWidth > 0 ? this.lastScreenWidth : Math.max(1, RiptideUiScale.getVirtualScreenWidth());
         int screenHeight = this.lastScreenHeight > 0 ? this.lastScreenHeight : Math.max(1, RiptideUiScale.getVirtualScreenHeight());
         int rowHeight = this.lastRowHeight > 0 ? this.lastRowHeight : 16;
         this.visibleRows = this.visibleRows(screenHeight, rowHeight);
         int width = Math.max(1, Math.min(this.cachedMenuWidth > 1 ? this.cachedMenuWidth : this.bounds.width(), screenWidth - 8));
         this.layoutMenu(screenWidth, screenHeight, rowHeight, width);
      }
   }

   private void layoutMenu(int screenWidth, int screenHeight, int rowHeight, int width) {
      this.visibleRows = Math.max(1, Math.min(this.options.size(), this.visibleRows <= 0 ? this.visibleRows(screenHeight, rowHeight) : this.visibleRows));
      int height = Math.max(1, this.visibleRows * rowHeight + 2);
      int x = this.bounds.x();
      int below = this.bounds.bottom() + 1;
      int above = this.bounds.y() - height - 1;
      int availableBelow = screenHeight - 4 - below;
      int availableAbove = this.bounds.y() - 4;
      int y = availableBelow < height && availableBelow < availableAbove ? above : below;
      this.menuBounds = UiBounds.of(clamp(x, 4, Math.max(4, screenWidth - 4 - width)), clamp(y, 4, Math.max(4, screenHeight - 4 - height)), width, height);
   }

   private int visibleRows(int screenHeight, int rowHeight) {
      int below = Math.max(0, (screenHeight - 4 - this.bounds.bottom() - 1) / Math.max(1, rowHeight));
      int above = Math.max(0, (this.bounds.y() - 4 - 1) / Math.max(1, rowHeight));
      return Math.max(1, Math.min(this.options.size(), Math.max(below, above)));
   }

   private int rowHeight() {
      return this.visibleRows <= 0 ? 1 : Math.max(1, (this.menuBounds.height() - 2) / this.visibleRows);
   }

   private int maxScroll() {
      return Math.max(0, this.options.size() - Math.max(1, this.visibleRows));
   }

   private void ensureSelectedVisible() {
      int index = this.options.indexOf(this.selected);
      if (index >= 0) {
         if (index < this.scroll) {
            this.scroll = index;
         }

         if (this.visibleRows > 0 && index >= this.scroll + this.visibleRows) {
            this.scroll = index - this.visibleRows + 1;
         }

         this.scroll = clamp(this.scroll, 0, this.maxScroll());
      }
   }

   private void updateScrollbar(int mouseY) {
      if (this.scrollbar != null && this.scrollbar.maxScroll() > 0) {
         int pixelScroll = Scrollbar.scrollFromMouse(this.scrollbar, mouseY, this.scrollbarGrabOffset);
         this.scroll = clamp(Math.round((float)pixelScroll / Math.max(1, this.rowHeight())), 0, this.maxScroll());
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }
}
