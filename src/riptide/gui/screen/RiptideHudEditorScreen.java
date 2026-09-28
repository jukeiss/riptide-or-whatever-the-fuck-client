package riptide.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.CompactWindow;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public class RiptideHudEditorScreen extends Screen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int GREEN = -10682470;
   private static final int RED = -50373;
   private static final String[] CAT_TITLES = new String[]{"World", "Player", "Client"};
   private static final String[][] CAT_IDS = new String[][]{
      {"coordinates", "nether_coords", "compass", "looking_at", "biome", "weather", "world_time", "real_time", "server", "server_ip", "server_brand"},
      {"speed", "rotation", "game_mode", "armor", "inventory", "durability", "item_counter", "potion_timers", "breaking_progress"},
      {"active_modules", "watermark", "fps", "ping", "tps", "cps", "keystrokes", "anti_vanish", "memory", "fps_graph", "spotify"}
   };
   private static final int VISUAL_GRID_SIZE = 10;
   private static final int SNAP_THRESHOLD = 6;
   private static final int NEIGHBOR_GAP = 0;
   private final Screen parent;
   private String selectedId;
   private String draggingId;
   private int dragOffsetX;
   private int dragOffsetY;
   private boolean moved;
   private boolean addPickerOpen;
   private int pickerX;
   private int pickerY;
   private Integer guideX;
   private Integer guideY;
   private int[] cachedToolbarPos;
   private boolean toolbarPosCached;
   private int toolbarPosSw;
   private int toolbarPosSh;
   private int toolbarPosW;
   private int toolbarPosH;
   private int toolbarPosRev;

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   private static int accent() {
      return RiptideTheme.recolor(-50373, RiptideTheme.Channel.ACCENT);
   }

   public RiptideHudEditorScreen(Screen parent) {
      super(Component.literal("Riptide HUD Editor"));
      this.parent = parent;
      RiptideHudManager.ensureDefaults();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 1426063360);
         if (this.editorGrid()) {
            this.renderGrid(graphics, sw, sh);
         }

         this.renderGuides(graphics, sw, sh);
         RiptideHudManager.render(graphics, this.font, true, this.selectedId, mx, my);
         this.renderToolbar(graphics, sw, sh, mx, my);
         if (this.addPickerOpen) {
            this.renderAddPicker(graphics, mx, my);
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderToolbar(GuiGraphicsExtractor graphics, int sw, int sh, int mx, int my) {
      String help = "Drag elements, right click edits/adds, Delete disables, arrows nudge.";
      int titleW = UiText.width(this.font, "Riptide HUD Editor", THEME.fontFor(UiTone.BODY), -791321) + 30;
      int helpW = UiText.width(this.font, help, THEME.fontFor(UiTone.BODY), muted()) + 16;
      int w = Math.min(sw - 16, Math.max(172, Math.min(430, Math.max(titleW, helpW))));
      int h = 34;
      int[] pos = this.toolbarPosition(sw, sh, w, h);
      if (pos != null) {
         int x = pos[0];
         int y = pos[1];
         CompactWindow.renderFrame(
            UiContexts.overlay(graphics, this.font, mx, my),
            UiBounds.of(x, y, w, h),
            "Riptide HUD Editor",
            false,
            false,
            false,
            this.hover(mx, my, x, y, w, 16),
            true,
            7,
            7,
            16
         );
         this.draw(graphics, help, x + 8, y + 19, muted(), w - 16);
      }
   }

   private int[] toolbarPosition(int sw, int sh, int w, int h) {
      int rev = RiptideHudManager.settingsRevision();
      if (this.toolbarPosCached
         && sw == this.toolbarPosSw
         && sh == this.toolbarPosSh
         && w == this.toolbarPosW
         && h == this.toolbarPosH
         && rev == this.toolbarPosRev) {
         return this.cachedToolbarPos;
      } else {
         int pad = 8;
         int[][] candidates = new int[][]{
            {pad, pad}, {Math.max(pad, sw - w - pad), pad}, {pad, Math.max(pad, sh - h - pad)}, {Math.max(pad, sw - w - pad), Math.max(pad, sh - h - pad)}
         };
         int[] pos = null;

         for (int[] candidate : candidates) {
            if (!this.intersectsHudElement(candidate[0], candidate[1], w, h)) {
               pos = candidate;
               break;
            }
         }

         this.cachedToolbarPos = pos;
         this.toolbarPosCached = true;
         this.toolbarPosSw = sw;
         this.toolbarPosSh = sh;
         this.toolbarPosW = w;
         this.toolbarPosH = h;
         this.toolbarPosRev = rev;
         return pos;
      }
   }

   private boolean intersectsHudElement(int x, int y, int w, int h) {
      for (String id : RiptideHudManager.elementIds()) {
         if (RiptideHudManager.state(id).enabled) {
            RiptideHudManager.ElementBounds bounds = RiptideHudManager.bounds(id, this.font);
            if (this.rectsIntersect(x, y, w, h, bounds.x(), bounds.y(), bounds.width(), bounds.height())) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean rectsIntersect(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
      return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
   }

   private void renderGrid(GuiGraphicsExtractor graphics, int sw, int sh) {
      int step = this.gridSize();

      for (int x = 0; x < sw; x += step) {
         boolean major = x / step % 5 == 0;
         UiRenderer.rect(graphics, UiBounds.of(x, 0, 1, sh), major ? 605098260 : 336662804);
      }

      for (int y = 0; y < sh; y += step) {
         boolean major = y / step % 5 == 0;
         UiRenderer.rect(graphics, UiBounds.of(0, y, sw, 1), major ? 605098260 : 336662804);
      }

      UiRenderer.rect(graphics, UiBounds.of(sw / 2, 0, 1, sh), RiptideTheme.recolor(1157577531, RiptideTheme.Channel.ACCENT));
      UiRenderer.rect(graphics, UiBounds.of(0, sh / 2, sw, 1), RiptideTheme.recolor(1157577531, RiptideTheme.Channel.ACCENT));
   }

   private RiptideHudEditorScreen.PickerLayout pickerLayout() {
      int pad = 8;
      int gap = 8;
      int titleH = 22;
      int catH = 15;
      int rowH = 16;
      int longest = 0;

      for (String[] cat : CAT_IDS) {
         for (String id : cat) {
            longest = Math.max(longest, UiText.width(this.font, RiptideHudManager.label(id), THEME.fontFor(UiTone.BODY), -791321));
         }
      }

      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      int cols = CAT_IDS.length;
      int maxCol = (sw - 8 - pad * 2 - (cols - 1) * gap) / cols;
      int colW = Math.max(60, Math.min(longest + 16, Math.max(60, maxCol)));
      int w = pad * 2 + cols * colW + (cols - 1) * gap;
      int maxRows = 0;

      for (String[] cat : CAT_IDS) {
         maxRows = Math.max(maxRows, cat.length);
      }

      int h = titleH + catH + maxRows * rowH + pad;
      int x = this.clamp(this.pickerX, 4, Math.max(4, sw - w - 4));
      int y = this.clamp(this.pickerY, 4, Math.max(4, sh - h - 4));
      return new RiptideHudEditorScreen.PickerLayout(x, y, w, h, colW, titleH, catH, rowH, gap, pad);
   }

   private void renderAddPicker(GuiGraphicsExtractor graphics, int mx, int my) {
      RiptideHudEditorScreen.PickerLayout p = this.pickerLayout();
      CompactWindow.renderFrame(
         UiContexts.overlay(graphics, this.font, mx, my),
         UiBounds.of(p.x, p.y, p.w, p.h),
         "Add Element",
         false,
         false,
         false,
         this.hover(mx, my, p.x, p.y, p.w, p.titleH),
         true,
         7,
         7,
         p.titleH
      );
      int underline = RiptideTheme.recolor(1728002875, RiptideTheme.Channel.ACCENT);

      for (int col = 0; col < CAT_IDS.length; col++) {
         int colX = p.x + p.pad + col * (p.colW + p.gap);
         int headerY = p.y + p.titleH;
         this.draw(graphics, CAT_TITLES[col], colX + 2, headerY + 3, accent(), p.colW - 4);
         UiRenderer.rect(graphics, UiBounds.of(colX, headerY + p.catH - 2, p.colW, 1), underline);
         int cy = headerY + p.catH;

         for (String id : CAT_IDS[col]) {
            boolean over = this.hover(mx, my, colX, cy, p.colW, p.rowH - 2);
            boolean enabled = RiptideHudManager.state(id).enabled;
            CompactSurfaces.tintedRow(graphics, colX, cy, p.colW, p.rowH - 2, over ? 1714756383 : 856888344);
            if (enabled) {
               CompactSurfaces.indicator(graphics, colX + 1, cy + 1, 3, p.rowH - 4, -10682470);
            }

            this.draw(graphics, RiptideHudManager.label(id), colX + 8, cy + 3, enabled ? -791321 : muted(), p.colW - 12);
            cy += p.rowH;
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (this.addPickerOpen) {
         String picked = this.pickerHit(mx, my);
         if (picked != null) {
            RiptideHudManager.setEnabled(picked, true);
            int[] snapped = this.snappedPosition(picked, mx, my);
            RiptideHudManager.move(picked, snapped[0], snapped[1], RiptideUiScale.getVirtualScreenWidth(), RiptideUiScale.getVirtualScreenHeight());
            this.selectedId = picked;
            this.addPickerOpen = false;
            return true;
         } else {
            this.addPickerOpen = false;
            return true;
         }
      } else {
         String hit = RiptideHudManager.hit(this.font, mx, my);
         if (event.button() == 1) {
            if (hit != null) {
               this.minecraft.gui.setScreen(new RiptideHudElementSettingsScreen(this, hit));
            } else {
               this.pickerX = mx;
               this.pickerY = my;
               this.addPickerOpen = true;
            }

            return true;
         } else if (event.button() == 0 && hit != null) {
            this.selectedId = hit;
            this.draggingId = hit;
            RiptideHudManager.ElementBounds bounds = RiptideHudManager.bounds(hit, this.font);
            this.dragOffsetX = mx - bounds.x();
            this.dragOffsetY = my - bounds.y();
            this.moved = false;
            return true;
         } else {
            this.selectedId = null;
            return true;
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.draggingId = null;
      this.guideX = null;
      this.guideY = null;
      return true;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.draggingId == null) {
         return true;
      } else {
         int mx = RiptideUiScale.toVirtualInt(event.x());
         int my = RiptideUiScale.toVirtualInt(event.y());
         int[] snapped = this.snappedPosition(this.draggingId, mx - this.dragOffsetX, my - this.dragOffsetY);
         RiptideHudManager.move(this.draggingId, snapped[0], snapped[1], RiptideUiScale.getVirtualScreenWidth(), RiptideUiScale.getVirtualScreenHeight());
         this.moved = true;
         return true;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.minecraft.gui.setScreen(this.parent);
         return true;
      } else if (this.selectedId != null && input.key() == 261) {
         RiptideHudManager.setEnabled(this.selectedId, false);
         return true;
      } else {
         if (this.selectedId != null) {
            int step = (input.modifiers() & 2) != 0 ? 10 : 1;
            RiptideHudManager.ElementBounds bounds = RiptideHudManager.bounds(this.selectedId, this.font);
            int x = bounds.x();
            int y = bounds.y();
            boolean moved = true;
            switch (input.key()) {
               case 262:
                  x += step;
                  break;
               case 263:
                  x -= step;
                  break;
               case 264:
                  y += step;
                  break;
               case 265:
                  y -= step;
                  break;
               default:
                  moved = false;
            }

            if (moved) {
               int[] snapped = this.snappedPosition(this.selectedId, x, y);
               RiptideHudManager.move(this.selectedId, snapped[0], snapped[1], RiptideUiScale.getVirtualScreenWidth(), RiptideUiScale.getVirtualScreenHeight());
               return true;
            }
         }

         return true;
      }
   }

   private int[] snappedPosition(String id, int x, int y) {
      RiptideHudManager.ElementBounds bounds = RiptideHudManager.bounds(id, this.font);
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      int outX = x;
      int outY = y;
      int maxX = Math.max(0, sw - bounds.width());
      int maxY = Math.max(0, sh - bounds.height());
      this.guideX = null;
      this.guideY = null;
      RiptideHudEditorScreen.SnapResult snappedX = this.snapAxis(id, x, bounds.width(), sw, true);
      RiptideHudEditorScreen.SnapResult snappedY = this.snapAxis(id, y, bounds.height(), sh, false);
      if (snappedX.snapped()) {
         outX = snappedX.position();
         this.guideX = snappedX.guide();
      }

      if (snappedY.snapped()) {
         outY = snappedY.position();
         this.guideY = snappedY.guide();
      }

      outX = this.clamp(outX, 0, maxX);
      outY = this.clamp(outY, 0, maxY);
      return new int[]{outX, outY};
   }

   private RiptideHudEditorScreen.SnapResult snapAxis(String id, int value, int size, int screenSize, boolean horizontal) {
      int max = Math.max(0, screenSize - size);
      RiptideHudEditorScreen.SnapResult best = RiptideHudEditorScreen.SnapResult.none();
      best = this.bestCloser(best, value, 0, 0);
      best = this.bestCloser(best, value, max, screenSize);
      best = this.bestCloser(best, value, (screenSize - size) / 2, screenSize / 2);

      for (String other : RiptideHudManager.elementIds()) {
         if (!other.equals(id) && RiptideHudManager.state(other).enabled) {
            RiptideHudManager.ElementBounds ob = RiptideHudManager.bounds(other, this.font);
            int otherStart = horizontal ? ob.x() : ob.y();
            int otherSize = horizontal ? ob.width() : ob.height();
            int otherEnd = otherStart + otherSize;
            int otherCenter = otherStart + otherSize / 2;
            best = this.bestCloser(best, value, otherStart, otherStart);
            best = this.bestCloser(best, value, otherEnd - size, otherEnd);
            best = this.bestCloser(best, value, otherCenter - size / 2, otherCenter);
            best = this.bestCloser(best, value, otherStart - size - 0, otherStart);
            best = this.bestCloser(best, value, otherEnd + 0, otherEnd);
         }
      }

      return best;
   }

   private RiptideHudEditorScreen.SnapResult bestCloser(RiptideHudEditorScreen.SnapResult current, int value, int candidatePosition, int guide) {
      int distance = Math.abs(value - candidatePosition);
      if (distance > 6) {
         return current;
      } else {
         return current.snapped() && distance >= current.distance() ? current : new RiptideHudEditorScreen.SnapResult(candidatePosition, guide, distance, true);
      }
   }

   private int gridSize() {
      return 10;
   }

   private String pickerHit(int mx, int my) {
      RiptideHudEditorScreen.PickerLayout p = this.pickerLayout();

      for (int col = 0; col < CAT_IDS.length; col++) {
         int colX = p.x + p.pad + col * (p.colW + p.gap);
         int cy = p.y + p.titleH + p.catH;

         for (String id : CAT_IDS[col]) {
            if (this.hover(mx, my, colX, cy, p.colW, p.rowH - 2)) {
               return id;
            }

            cy += p.rowH;
         }
      }

      return null;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return true;
   }

   private boolean editorGrid() {
      Module hud = ModuleRegistry.get("hud");
      return hud == null || Boolean.parseBoolean(hud.value("editor-grid"));
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private void renderGuides(GuiGraphicsExtractor graphics, int sw, int sh) {
      if (this.guideX != null) {
         UiRenderer.rect(graphics, UiBounds.of(this.guideX, 0, 1, sh), RiptideTheme.recolor(-1426113733, RiptideTheme.Channel.ACCENT));
      }

      if (this.guideY != null) {
         UiRenderer.rect(graphics, UiBounds.of(0, this.guideY, sw, 1), RiptideTheme.recolor(-1426113733, RiptideTheme.Channel.ACCENT));
      }
   }

   private boolean hover(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxW) {
      String trimmed = UiText.trimToWidth(this.font, text, maxW, THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, trimmed, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private record PickerLayout(int x, int y, int w, int h, int colW, int titleH, int catH, int rowH, int gap, int pad) {
   }

   private record SnapResult(int position, int guide, int distance, boolean snapped) {
      static RiptideHudEditorScreen.SnapResult none() {
         return new RiptideHudEditorScreen.SnapResult(0, 0, Integer.MAX_VALUE, false);
      }
   }
}
