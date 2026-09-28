package riptide.gui.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactListViewport;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.modules.ModuleRegistry;
import riptide.modules.WaypointsModule;
import riptide.util.RiptideChatField;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.RiptideWaypoints;

public class RiptideWaypointsScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 460;
   private static final int PANEL_H = 330;
   private static final int HEADER_H = 24;
   private static final int FIELD_H = 20;
   private static final int ROW_H = 22;
   private static final int SWATCH_W = 22;
   private static final int SWATCH_H = 14;
   private static final int NAME_FIELD_W = 140;
   private static final int PICKER_H = 48;
   private static final int BG = -872086265;
   private static final int ROW = -1441722092;
   private static final int ROW_HOVER = -1441000940;
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int RED = -50373;
   private static final int FALLBACK_DEFAULT_COLOR = -12855297;
   private static final int[] PRESETS = new int[]{-50373, -24806, -5829, -13248397, -12855297, -11895553, -4957185, -37177, -1, -7566196};
   private final Screen parent;
   private final String scope;
   private final List<RiptideWaypointsScreen.Hit> hits = new ArrayList<>();
   private final Map<String, RiptideChatField> rowFields = new LinkedHashMap<>();
   private final List<String> visibleRowNames = new ArrayList<>();
   private RiptideChatField entryField;
   private RiptideChatField hexField;
   private int scroll;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private String pickerTarget;
   private String focusedName;
   private boolean syncingHex;
   private UiBounds pickerBounds;
   private long cachedRevision = Long.MIN_VALUE;
   private List<RiptideWaypoints.Waypoint> cachedRows = List.of();

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   public RiptideWaypointsScreen(Screen parent) {
      super(Component.literal("Waypoints"));
      this.parent = parent;
      this.scope = RiptideWaypoints.scopeKey(Minecraft.getInstance());
   }

   protected void init() {
      this.ensureFields();
      this.syncFieldBounds();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         this.hits.clear();
         this.pickerBounds = null;
         this.visibleRowNames.clear();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -872086265);
         int x = this.panelX();
         int y = this.panelY();
         int panelW = this.panelW();
         int panelH = this.panelH();
         this.ensureFields();
         this.syncFieldBounds();
         this.syncRowFields();
         this.drawTopBar(graphics, x, y, panelW, panelH, mx, my);
         if (panelW < 180 || panelH < 160) {
            this.drawText(graphics, "Window too small.", x + 8, y + 24 + 10, muted(), Math.max(0, panelW - 16));
            return;
         }

         int fieldX = x + 10;
         int fieldY = y + 24 + 10;
         this.entryField.render(graphics, mx, my, delta);
         this.button(graphics, "Add", x + panelW - 70, fieldY, 60, 20, CompactOverlayButton.Variant.SUCCESS, RiptideWaypointsScreen.HitType.ADD, "", mx, my);
         int scopeY = fieldY + 20 + 8;
         String scopeLabel = "unknown".equals(this.scope) ? "Scope: unknown (join a server or world)" : "Scope: " + this.scope;
         this.drawText(graphics, scopeLabel, fieldX, scopeY + 2, muted(), panelW - 20);
         int listY = scopeY + 16;
         if (this.pickerTarget != null) {
            this.drawPicker(graphics, fieldX, listY, panelW - 20, delta, mx, my);
            listY += 54;
         }

         int listH = y + panelH - listY - 10;
         CompactListViewport.Layout listLayout = this.listLayout(listY, listH);
         this.frame(graphics, fieldX, listY, panelW - 20, listH, -1442248437, THEME.borderSoft());
         listLayout.beginRows(graphics);

         try {
            this.drawRows(graphics, listLayout, delta, mx, my);
         } finally {
            listLayout.endRows(graphics);
         }

         listLayout.drawScrollbar(graphics, mx, my, this.scrollbarDragging);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawRows(GuiGraphicsExtractor graphics, CompactListViewport.Layout layout, float delta, int mx, int my) {
      List<RiptideWaypoints.Waypoint> rows = this.rows();
      this.scroll = this.clamp(this.scroll, 0, layout.maxScroll());
      int x = layout.x() + layout.contentInset();
      int w = layout.contentWidth();
      if (rows.isEmpty()) {
         this.drawText(graphics, "No waypoints for this scope.", x + 4, layout.y() + layout.contentInset() + 6, muted(), w - 8);
      } else {
         layout.forEachVisibleRow(
            rows.size(),
            (i, rowY) -> {
               RiptideWaypoints.Waypoint waypoint = rows.get(i);
               this.visibleRowNames.add(waypoint.name());
               boolean over = mx >= x && mx < x + w && my >= rowY && my < rowY + 22;
               CompactSurfaces.tintedRow(graphics, x, rowY, w, 21, over ? -1441000940 : -1441722092);
               int swatchY = rowY + 4;
               boolean swatchOver = mx >= x + 4 && mx < x + 4 + 22 && my >= swatchY && my < swatchY + 14;
               UiRenderer.rect(graphics, UiBounds.of(x + 4, swatchY, 22, 14), waypoint.color());
               UiRenderer.outline(
                  graphics,
                  UiBounds.of(x + 4, swatchY, 22, 14),
                  !swatchOver && !waypoint.name().equals(this.pickerTarget) ? THEME.borderSoft() : RiptideTheme.recolor(-50373, RiptideTheme.Channel.OUTLINE)
               );
               int nameX = x + 4 + 22 + 7;
               RiptideChatField nameField = this.rowFields.get(waypoint.name());
               if (nameField != null) {
                  nameField.setX(nameX);
                  nameField.setY(rowY + 3);
                  nameField.setWidth(140);
                  nameField.setHeight(14);
                  nameField.render(graphics, mx, my, delta);
               }

               this.drawText(graphics, waypoint.x() + " " + waypoint.y() + " " + waypoint.z(), nameX + 140 + 8, rowY + 7, muted(), w - (nameX - x) - 140 - 40);
               CompactListRenderer.drawDeleteButton(graphics, x + w - 22, rowY + 4, 18, 14, over);
               this.hits.add(new RiptideWaypointsScreen.Hit(RiptideWaypointsScreen.HitType.ROW, x, rowY, w, 21, waypoint.name()));
               this.hits.add(new RiptideWaypointsScreen.Hit(RiptideWaypointsScreen.HitType.SWATCH, x + 4, swatchY, 22, 14, waypoint.name()));
               this.hits.add(new RiptideWaypointsScreen.Hit(RiptideWaypointsScreen.HitType.REMOVE, x + w - 22, rowY + 4, 18, 14, waypoint.name()));
            }
         );
      }
   }

   private void drawPicker(GuiGraphicsExtractor graphics, int x, int y, int w, float delta, int mx, int my) {
      this.pickerBounds = UiBounds.of(x, y, w, 48);
      this.frame(graphics, x, y, w, 48, -301266162, THEME.borderSoft());
      int current = RiptideWaypoints.get().colorOf(this.scope, this.pickerTarget, defaultColor());
      int swatchY = y + 6;
      int swatchX = x + 8;

      for (int i = 0; i < PRESETS.length; i++) {
         int px = swatchX + i * 26;
         if (px + 22 > x + w - 8) {
            break;
         }

         UiRenderer.rect(graphics, UiBounds.of(px, swatchY, 22, 14), PRESETS[i]);
         boolean over = mx >= px && mx < px + 22 && my >= swatchY && my < swatchY + 14;
         UiRenderer.outline(graphics, UiBounds.of(px, swatchY, 22, 14), !over && PRESETS[i] != current ? THEME.borderSoft() : -1);
         this.hits.add(new RiptideWaypointsScreen.Hit(RiptideWaypointsScreen.HitType.PRESET, px, swatchY, 22, 14, Integer.toHexString(i)));
      }

      int hexY = swatchY + 14 + 6;
      this.hexField.setX(x + 8);
      this.hexField.setY(hexY);
      this.hexField.setWidth(96);
      this.hexField.setHeight(18);
      this.hexField.render(graphics, mx, my, delta);
      UiRenderer.rect(graphics, UiBounds.of(x + 112, hexY, 22, 18), current);
      UiRenderer.outline(graphics, UiBounds.of(x + 112, hexY, 22, 18), THEME.borderSoft());
      this.drawText(graphics, "# hex applies live", x + 142, hexY + 5, muted(), x + w - 142 - 8);
   }

   private void button(
      GuiGraphicsExtractor graphics,
      String label,
      int x,
      int y,
      int w,
      int h,
      CompactOverlayButton.Variant variant,
      RiptideWaypointsScreen.HitType type,
      String value,
      int mx,
      int my
   ) {
      CompactOverlayControls.action(graphics, this.font, x, y, w, h, label, variant, true, mx, my);
      this.hits.add(new RiptideWaypointsScreen.Hit(type, x, y, w, h, value));
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(UiContexts.overlay(graphics, this.font, mx, my), bounds, 24, "Waypoints", mx >= x && mx < x + width && my >= y && my < y + 24);
      UiBounds close = CompactScreenPanel.closeButton(bounds, 24);
      this.hits.add(new RiptideWaypointsScreen.Hit(RiptideWaypointsScreen.HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), ""));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() != 0) {
         return true;
      } else {
         RiptideChatField focused = this.focusedName == null ? null : this.rowFields.get(this.focusedName);
         if (focused != null && !contains(focused, mx, my)) {
            this.commitRename();
         }

         CompactScrollbar.Metrics metrics = this.listScrollbarMetrics();
         if (metrics.hasScroll() && metrics.contains(mx, my)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            this.scroll = this.snapScrollToRows(CompactScrollbar.scrollFromThumb(metrics, my, this.scrollbarGrabOffset));
            return true;
         } else {
            this.ensureFields();
            if (this.pickerTarget != null && this.hexField.mouseClicked(mx, my, event.button())) {
               this.entryField.setFocused(false);
               return true;
            } else if (this.entryField.mouseClicked(mx, my, event.button())) {
               this.hexField.setFocused(false);
               return true;
            } else {
               for (String name : this.visibleRowNames) {
                  RiptideChatField rowField = this.rowFields.get(name);
                  if (rowField != null && rowField.mouseClicked(mx, my, event.button())) {
                     this.focusRename(name);
                     return true;
                  }
               }

               for (int i = this.hits.size() - 1; i >= 0; i--) {
                  RiptideWaypointsScreen.Hit hit = this.hits.get(i);
                  if (hit.contains(mx, my)
                     && (
                        hit.type != RiptideWaypointsScreen.HitType.REMOVE
                              && hit.type != RiptideWaypointsScreen.HitType.SWATCH
                              && hit.type != RiptideWaypointsScreen.HitType.ROW
                           || this.insideListBody(mx, my)
                     )) {
                     switch (hit.type) {
                        case ADD:
                           this.addEntry();
                           break;
                        case SWATCH:
                           this.openPicker(hit.value);
                           break;
                        case PRESET:
                           this.applyPreset(hit.value);
                           break;
                        case REMOVE:
                           this.removeEntry(hit.value);
                           break;
                        case ROW:
                           this.copyCoords(hit.value);
                           break;
                        case CLOSE:
                           this.onClose();
                     }

                     return true;
                  }
               }

               if (this.pickerTarget != null && (this.pickerBounds == null || !this.pickerBounds.contains(mx, my))) {
                  this.closePicker();
               }

               this.entryField.setFocused(false);
               this.hexField.setFocused(false);
               return true;
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      if (!this.insideListBody(mx, my)) {
         return true;
      } else {
         this.scroll += scrollY < 0.0 ? 22 : -22;
         this.scroll = this.clamp(this.scroll, 0, this.listScrollbarMetrics().maxScroll());
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      this.scrollbarDragging = false;
      RiptideChatField focused = this.focusedName == null ? null : this.rowFields.get(this.focusedName);
      if (focused != null && focused.mouseReleased(mx, my, event.button())) {
         return true;
      } else if (this.entryField != null && this.entryField.mouseReleased(mx, my, event.button())) {
         return true;
      } else {
         return this.hexField != null && this.hexField.mouseReleased(mx, my, event.button()) ? true : true;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      RiptideChatField focused = this.focusedName == null ? null : this.rowFields.get(this.focusedName);
      if (focused != null && focused.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (this.entryField != null && this.entryField.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (this.hexField != null && this.hexField.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (!this.scrollbarDragging) {
         return true;
      } else {
         this.scroll = this.snapScrollToRows(CompactScrollbar.scrollFromThumb(this.listScrollbarMetrics(), my, this.scrollbarGrabOffset));
         return true;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      this.ensureFields();
      if (input.key() == 256) {
         if (this.focusedName != null) {
            this.cancelRename();
         } else if (this.pickerTarget != null) {
            this.closePicker();
         } else {
            this.onClose();
         }

         return true;
      } else if (input.key() != 257 && input.key() != 335) {
         RiptideChatField focused = this.focusedName == null ? null : this.rowFields.get(this.focusedName);
         if (focused != null && focused.keyPressed(input)) {
            return true;
         } else if (this.entryField.keyPressed(input)) {
            return true;
         } else {
            return this.pickerTarget != null && this.hexField.keyPressed(input) ? true : true;
         }
      } else {
         if (this.focusedName != null) {
            this.commitRename();
         } else if (this.entryField.isFocused()) {
            this.addEntry();
         }

         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      this.ensureFields();
      RiptideChatField focused = this.focusedName == null ? null : this.rowFields.get(this.focusedName);
      if (focused != null && focused.charTyped(input)) {
         return true;
      } else if (this.entryField.charTyped(input)) {
         return true;
      } else {
         return this.pickerTarget != null && this.hexField.charTyped(input) ? true : true;
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void addEntry() {
      if (this.minecraft != null && this.minecraft.player != null) {
         RiptideWaypoints store = RiptideWaypoints.get();
         String name = this.entryText().trim();
         if (name.isEmpty()) {
            name = store.nextName(this.scope, "Waypoint");
         }

         int x = (int)Math.floor(this.minecraft.player.getX());
         int y = (int)Math.floor(this.minecraft.player.getY());
         int z = (int)Math.floor(this.minecraft.player.getZ());
         store.add(this.scope, new RiptideWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
         this.setEntryText("");
         this.clampScrollToList();
      } else {
         RiptideNotifications.warning("Join a world to add a waypoint here.");
      }
   }

   private void removeEntry(String name) {
      RiptideWaypoints.get().remove(this.scope, name);
      if (name != null && name.equals(this.pickerTarget)) {
         this.closePicker();
      }

      if (name != null && name.equals(this.focusedName)) {
         this.focusedName = null;
      }

      this.clampScrollToList();
   }

   private void copyCoords(String name) {
      for (RiptideWaypoints.Waypoint waypoint : this.rows()) {
         if (waypoint.name().equals(name)) {
            String coords = waypoint.x() + " " + waypoint.y() + " " + waypoint.z();
            Minecraft.getInstance().keyboardHandler.setClipboard(coords);
            RiptideNotifications.copied("Copied coordinates: " + coords + ".");
            return;
         }
      }
   }

   private void syncRowFields() {
      List<RiptideWaypoints.Waypoint> rows = this.rows();
      Map<String, RiptideChatField> alive = new LinkedHashMap<>();

      for (RiptideWaypoints.Waypoint waypoint : rows) {
         RiptideChatField field = this.rowFields.get(waypoint.name());
         if (field == null) {
            field = new RiptideChatField(this.minecraft, this.font, 0, 0, 1, 14, false);
            field.setMaxLength(64);
            field.setText(waypoint.name());
         } else if (!waypoint.name().equals(this.focusedName) && !waypoint.name().equals(field.getText())) {
            field.setText(waypoint.name());
         }

         alive.put(waypoint.name(), field);
      }

      if (this.focusedName != null && !alive.containsKey(this.focusedName)) {
         this.focusedName = null;
      }

      this.rowFields.clear();
      this.rowFields.putAll(alive);
   }

   private void focusRename(String name) {
      if (this.focusedName != null && !this.focusedName.equals(name)) {
         this.commitRename();
      }

      this.focusedName = name;
      RiptideChatField field = this.rowFields.get(name);
      if (field != null) {
         field.setFocused(true);
         field.setSelectionEnd(field.getText().length());
      }

      if (this.entryField != null) {
         this.entryField.setFocused(false);
      }

      if (this.hexField != null) {
         this.hexField.setFocused(false);
      }
   }

   private void commitRename() {
      if (this.focusedName != null) {
         String original = this.focusedName;
         RiptideChatField field = this.rowFields.get(original);
         this.focusedName = null;
         if (field != null) {
            field.setFocused(false);
            String renamed = field.getText().trim();
            if (renamed.equals(original)) {
               field.setText(original);
            } else {
               if (RiptideWaypoints.get().rename(this.scope, original, renamed)) {
                  this.rowFields.remove(original);
                  this.rowFields.put(renamed, field);
                  field.setText(renamed);
                  if (original.equals(this.pickerTarget)) {
                     this.pickerTarget = renamed;
                  }
               } else {
                  field.setText(original);
               }
            }
         }
      }
   }

   private void cancelRename() {
      if (this.focusedName != null) {
         RiptideChatField field = this.rowFields.get(this.focusedName);
         if (field != null) {
            field.setText(this.focusedName);
            field.setFocused(false);
         }

         this.focusedName = null;
      }
   }

   private static boolean contains(RiptideChatField field, int mx, int my) {
      return mx >= field.getX() && mx < field.getX() + field.getWidth() && my >= field.getY() && my < field.getY() + field.getHeight();
   }

   private void openPicker(String name) {
      this.pickerTarget = name;
      this.syncingHex = true;

      try {
         this.hexField.setText(String.format(Locale.ROOT, "%06X", RiptideWaypoints.get().colorOf(this.scope, name, defaultColor()) & 16777215));
      } finally {
         this.syncingHex = false;
      }

      this.hexField.setFocused(true);
      this.entryField.setFocused(false);
   }

   private void closePicker() {
      this.pickerTarget = null;
      if (this.hexField != null) {
         this.hexField.setFocused(false);
      }
   }

   private void applyPreset(String hexIndex) {
      if (this.pickerTarget != null) {
         int index;
         try {
            index = Integer.parseInt(hexIndex, 16);
         } catch (NumberFormatException var8) {
            return;
         }

         if (index >= 0 && index < PRESETS.length) {
            RiptideWaypoints.get().setColor(this.scope, this.pickerTarget, PRESETS[index]);
            this.syncingHex = true;

            try {
               this.hexField.setText(String.format(Locale.ROOT, "%06X", PRESETS[index] & 16777215));
            } finally {
               this.syncingHex = false;
            }
         }
      }
   }

   private void applyHex(String raw) {
      if (!this.syncingHex && this.pickerTarget != null) {
         Integer color = parseHex(raw);
         if (color != null) {
            RiptideWaypoints.get().setColor(this.scope, this.pickerTarget, color);
         }
      }
   }

   private static Integer parseHex(String raw) {
      if (raw == null) {
         return null;
      } else {
         String text = raw.trim();
         if (text.startsWith("#")) {
            text = text.substring(1);
         }

         if (text.length() != 6) {
            return null;
         } else {
            try {
               return 0xFF000000 | Integer.parseInt(text, 16);
            } catch (NumberFormatException var3) {
               return null;
            }
         }
      }
   }

   private static int defaultColor() {
      return ModuleRegistry.get("waypoints") instanceof WaypointsModule waypoints ? waypoints.defaultColor() : -12855297;
   }

   private List<RiptideWaypoints.Waypoint> rows() {
      RiptideWaypoints store = RiptideWaypoints.get();
      long revision = store.revision();
      if (revision != this.cachedRevision) {
         this.cachedRevision = revision;
         this.cachedRows = store.list(this.scope);
      }

      return this.cachedRows;
   }

   private void ensureFields() {
      if (this.entryField == null) {
         this.entryField = new RiptideChatField(this.minecraft, this.font, 0, 0, 1, 20, false);
         this.entryField.setMaxLength(64);
         this.entryField.setPlaceholder(Component.literal("Waypoint name"));
      }

      if (this.hexField == null) {
         this.hexField = new RiptideChatField(this.minecraft, this.font, 0, 0, 1, 18, false);
         this.hexField.setMaxLength(7);
         this.hexField.setPlaceholder(Component.literal("RRGGBB"));
         this.hexField.setFilter(text -> {
            if (text != null && !text.isEmpty()) {
               int start = text.startsWith("#") ? 1 : 0;
               if (text.length() - start > 6) {
                  return false;
               } else {
                  for (int i = start; i < text.length(); i++) {
                     char chr = text.charAt(i);
                     boolean hex = chr >= '0' && chr <= '9' || chr >= 'a' && chr <= 'f' || chr >= 'A' && chr <= 'F';
                     if (!hex) {
                        return false;
                     }
                  }

                  return true;
               }
            } else {
               return true;
            }
         });
         this.hexField.setChangedListener(this::applyHex);
      }
   }

   private void syncFieldBounds() {
      if (this.entryField != null) {
         int x = this.panelX();
         int y = this.panelY();
         this.entryField.setX(x + 10);
         this.entryField.setY(y + 24 + 10);
         this.entryField.setWidth(this.panelW() - 88);
         this.entryField.setHeight(20);
      }
   }

   private String entryText() {
      return this.entryField == null ? "" : this.entryField.getText();
   }

   private void setEntryText(String value) {
      this.ensureFields();
      this.entryField.setText(value == null ? "" : value);
      this.entryField.setSelectionEnd(this.entryField.getText().length());
   }

   private int listY() {
      int y = this.panelY() + 24 + 10 + 20 + 8 + 16;
      return this.pickerTarget != null ? y + 48 + 6 : y;
   }

   private CompactListViewport.Layout listLayout(int listY, int listH) {
      return CompactListViewport.layout(this.panelX() + 10, listY, this.panelW() - 20, listH, this.rows().size(), 22, 22, this.scroll, 4, 0, 3, 6, false);
   }

   private CompactScrollbar.Metrics listScrollbarMetrics() {
      int listY = this.listY();
      int listH = this.panelY() + this.panelH() - listY - 10;
      return this.listLayout(listY, listH).scrollbar();
   }

   private boolean insideListBody(int mx, int my) {
      int listY = this.listY();
      int listH = this.panelY() + this.panelH() - listY - 10;
      CompactListViewport.Layout listLayout = this.listLayout(listY, listH);
      return mx >= listLayout.x() + listLayout.contentInset()
         && mx < listLayout.x() + listLayout.contentInset() + listLayout.contentWidth()
         && my >= listLayout.y() + listLayout.contentInset()
         && my < listLayout.y() + listLayout.contentInset() + listLayout.viewHeight();
   }

   private void clampScrollToList() {
      this.scroll = this.clamp(this.scroll, 0, this.listScrollbarMetrics().maxScroll());
   }

   private int snapScrollToRows(int offset) {
      return Math.max(0, offset / 22) * 22;
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 4);
   }

   private int panelY() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 4);
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 4, 460);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 4, 330);
   }

   private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE));
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
      String display = UiText.trimToWidth(this.font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private record Hit(RiptideWaypointsScreen.HitType type, int x, int y, int w, int h, String value) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum HitType {
      ADD,
      SWATCH,
      PRESET,
      REMOVE,
      ROW,
      CLOSE;
   }
}
