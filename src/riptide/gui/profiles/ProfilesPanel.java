package riptide.gui.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideProfile;
import riptide.util.RiptideProfileManager;
import riptide.util.RiptideTheme;

public final class ProfilesPanel {
   private static final int CARD_BG = 705827348;
   private static final int DISABLED_FILL = 403771667;
   private static final int DISABLED_BORDER = -14013906;
   private static final int WARN = -14249;
   private static final int BORDER_STOCK = -13425624;
   private static final int BORDER_BRIGHT_STOCK = -8759968;
   private static final int ACCENT_STOCK = -13248397;
   private static final int TEXT_STOCK = -855310;
   private static final int MUTED_STOCK = -6645094;
   private static final int ERROR_STOCK = -42149;
   private int BORDER = -13425624;
   private int BORDER_BRIGHT = -8759968;
   private int ACCENT = -13248397;
   private int TEXT = -855310;
   private int MUTED = -6645094;
   private int ERROR = -42149;
   private final ProfilesPanel.Host host;
   private final Font font;
   private final CompactTheme theme = new CompactTheme();
   private final RiptideProfileManager mgr = RiptideProfileManager.get();
   private final List<ProfilesPanel.Hotspot> hotspots = new ArrayList<>();
   private final List<CompactTextInput> activeInputs = new ArrayList<>();
   private final CompactTextInput nameField = this.field("Profile name", 48);
   private final CompactTextInput ruleField = this.field("e.g. play.example.com or *.example.com", 80);
   private String selectedId = "";
   private int listScroll;
   private CompactScrollbar.Metrics listScrollbar;
   private boolean listDragging;
   private int listGrab;
   private int ruleScroll;
   private ProfilesPanel.NameMode nameMode = ProfilesPanel.NameMode.NONE;
   private boolean showPrompt;
   private RiptideProfile pendingLoad;
   private boolean createAutoSave = true;
   private boolean createOwnMacros = true;
   private boolean createOwnTheme = true;
   private boolean suppressInput;
   private float delta;
   private int lastMx;
   private int lastMy;
   private int bx;
   private int by;
   private int bw;
   private int bh;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   private int ruleTop;
   private int ruleBottom;
   private int ruleX;
   private int ruleW;
   private ProfilesPanel.RiptideThemeStateRef cachedTheme = new ProfilesPanel.RiptideThemeStateRef();

   public ProfilesPanel(ProfilesPanel.Host host, Font font) {
      this.host = host;
      this.font = font;
      this.ruleField.setOnSubmit(v -> this.addCurrentRule());
      this.nameField.setOnSubmit(v -> this.confirmName());
   }

   private CompactTextInput field(String placeholder, int maxLen) {
      CompactTextInput f = new CompactTextInput();
      f.setPlaceholder(placeholder).setMaxLength(maxLen).setFieldHeight(16);
      return f;
   }

   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float partial) {
      this.refreshTheme();
      this.hotspots.clear();
      this.activeInputs.clear();
      this.delta = partial;
      this.lastMx = mx;
      this.lastMy = my;
      this.bx = x;
      this.by = y;
      this.bw = w;
      this.bh = h;
      boolean modal = this.nameMode != ProfilesPanel.NameMode.NONE || this.showPrompt;
      this.suppressInput = modal;
      RiptideProfile selected = this.resolveSelected();
      RiptideProfile active = this.mgr.active();
      String activeLabel = "Active: " + (active == null ? "—" : active.displayName);
      this.drawText(g, activeLabel, x + 6, y + 6, this.TEXT, false, w - 140);
      boolean dirty = active != null && !active.autoSave && this.mgr.isDirty();
      if (dirty) {
         this.drawText(g, "● unsaved", x + 6, y + 16, -14249, false, w - 140);
      }

      this.button(
         g, x + w - 60, y + 4, 54, 16, "Save", dirty ? this.ACCENT : this.BORDER, this.TEXT, mx, my, dirty ? () -> this.mgr.save(this.mgr.active()) : null
      );
      int contentTop = y + 28;
      int pad = 6;
      int leftW = Math.max(150, Math.min(230, (int)(w * 0.42F)));
      int leftX = x + pad;
      int rightX = x + leftW + 10;
      int rightW = x + w - pad - rightX;
      this.button(g, leftX, contentTop, leftW, 16, "+ New profile", this.ACCENT, this.TEXT, mx, my, () -> this.openName(ProfilesPanel.NameMode.CREATE));
      this.listX = leftX;
      this.listY = contentTop + 20;
      this.listW = leftW;
      int listBottom = y + h - pad;
      this.listH = Math.max(20, listBottom - this.listY);
      this.renderList(g, selected, mx, my);
      this.renderDetail(g, selected, rightX, contentTop, Math.max(120, rightW), y + h - pad - contentTop, mx, my);
      if (modal) {
         this.suppressInput = false;
         UiRenderer.rect(g, UiBounds.of(x, y, w, h), -1342177280);
         if (this.showPrompt) {
            this.renderPrompt(g, x, y, w, h, mx, my);
         } else {
            this.renderNameModal(g, x, y, w, h, mx, my);
         }
      }
   }

   private RiptideProfile resolveSelected() {
      RiptideProfile sel = this.mgr.byId(this.selectedId);
      if (sel == null) {
         this.selectedId = this.mgr.activeId();
         sel = this.mgr.byId(this.selectedId);
         if (sel == null) {
            List<RiptideProfile> all = this.mgr.list();
            if (!all.isEmpty()) {
               sel = all.get(0);
               this.selectedId = sel.id;
            }
         }
      }

      return sel;
   }

   private void renderList(GuiGraphicsExtractor g, RiptideProfile selected, int mx, int my) {
      List<RiptideProfile> profiles = this.mgr.list();
      int rowH = 24;
      int contentH = profiles.size() * rowH;
      int maxScroll = Math.max(0, contentH - this.listH);
      this.listScroll = Math.max(0, Math.min(this.listScroll, maxScroll));
      UiScissorStack.global().push(g, UiBounds.of(this.listX, this.listY, this.listW, this.listH));

      try {
         int rowW = this.listW - (maxScroll > 0 ? 8 : 2);
         int ry = this.listY - this.listScroll;
         String activeId = this.mgr.activeId();

         for (RiptideProfile p : profiles) {
            if (ry + rowH > this.listY && ry < this.listY + this.listH) {
               boolean isActive = p.id.equals(activeId);
               boolean isSel = selected != null && p.id.equals(selected.id);
               boolean hover = !this.suppressInput
                  && mx >= this.listX
                  && mx < this.listX + rowW
                  && my >= ry
                  && my < ry + rowH - 2
                  && my >= this.listY
                  && my < this.listY + this.listH;
               int fill = isSel ? tint(this.ACCENT, 51) : (hover ? tint(this.BORDER_BRIGHT, 34) : 705827348);
               int border = isSel ? this.ACCENT : this.BORDER;
               UiRenderer.frame(g, UiBounds.of(this.listX, ry, rowW, rowH - 2), fill, border);
               this.drawText(g, p.displayName, this.listX + 6, ry + 3, this.TEXT, false, rowW - 16);
               this.drawText(g, this.subLabel(p), this.listX + 6, ry + 13, this.MUTED, false, rowW - 16);
               if (isActive) {
                  this.drawText(g, "●", this.listX + rowW - 10, ry + 7, this.ACCENT, false);
               }

               if (!this.suppressInput) {
                  this.hotspots.add(new ProfilesPanel.Hotspot(this.listX, ry, rowW, rowH - 2, () -> this.selectedId = p.id));
               }
            }

            ry += rowH;
         }
      } finally {
         UiScissorStack.global().pop(g);
      }

      if (maxScroll > 0) {
         this.listScrollbar = CompactScrollbar.compute(contentH, this.listH, this.listX + this.listW - 4, this.listY, 3, this.listH, this.listScroll);
         CompactScrollbar.draw(g, this.listScrollbar, this.listScrollbar.contains(mx, my), this.listDragging);
      } else {
         this.listScrollbar = null;
      }
   }

   private String subLabel(RiptideProfile p) {
      StringBuilder sb = new StringBuilder(p.autoSave ? "auto-save" : "manual");
      if (p.ownMacroLibrary) {
         sb.append(" • own macros");
      }

      int r = p.serverPatterns == null ? 0 : p.serverPatterns.size();
      if (r > 0) {
         sb.append(" • ").append(r).append(r == 1 ? " server" : " servers");
      }

      return sb.toString();
   }

   private void renderDetail(GuiGraphicsExtractor g, RiptideProfile p, int dx, int dy, int dw, int dh, int mx, int my) {
      if (p == null) {
         this.drawText(g, "Select a profile.", dx, dy + 4, this.MUTED, false, dw);
      } else {
         boolean isActive = p.id.equals(this.mgr.activeId());
         this.drawText(g, p.displayName + (isActive ? "  (active)" : ""), dx, dy, this.TEXT, false, dw);
         int cy = dy + 14;
         int half = (dw - 4) / 2;
         this.button(g, dx, cy, half, 16, "Load", isActive ? this.BORDER : this.ACCENT, this.TEXT, mx, my, isActive ? null : () -> this.doLoad(p));
         this.button(g, dx + half + 4, cy, dw - half - 4, 16, "Rename", this.BORDER, this.TEXT, mx, my, () -> this.openName(ProfilesPanel.NameMode.RENAME));
         cy += 19;
         boolean canDelete = !RiptideProfileManager.isDefault(p) && this.mgr.count() > 1;
         this.button(g, dx, cy, half, 16, "Duplicate", this.BORDER, this.TEXT, mx, my, () -> this.openName(ProfilesPanel.NameMode.DUPLICATE));
         this.button(g, dx + half + 4, cy, dw - half - 4, 16, "Delete", this.ERROR, this.TEXT, mx, my, canDelete ? () -> this.doDelete(p) : null);
         cy += 22;
         cy = this.toggle(g, dx, cy, dw, "Auto-save changes", p.autoSave, mx, my, () -> this.mgr.setAutoSave(p, !p.autoSave));
         cy = this.toggle(g, dx, cy, dw, "Own macro library", p.ownMacroLibrary, mx, my, () -> this.mgr.setOwnMacroLibrary(p, !p.ownMacroLibrary));
         cy = this.toggle(g, dx, cy, dw, "Own theme color", p.ownThemeColor, mx, my, () -> this.mgr.setOwnThemeColor(p, !p.ownThemeColor));
         cy += 4;
         this.drawText(g, "Auto-apply on servers:", dx, cy, this.MUTED, false, dw);
         cy += 11;
         this.ruleX = dx;
         this.ruleW = dw;
         this.ruleTop = cy;
         this.ruleBottom = dy + dh - 22;
         int ruleViewH = Math.max(14, this.ruleBottom - this.ruleTop);
         List<String> patterns = p.serverPatterns == null ? List.of() : p.serverPatterns;
         int ruleContentH = patterns.size() * 14;
         int ruleMax = Math.max(0, ruleContentH - ruleViewH);
         this.ruleScroll = Math.max(0, Math.min(this.ruleScroll, ruleMax));
         UiScissorStack.global().push(g, UiBounds.of(dx, this.ruleTop, dw, ruleViewH));

         try {
            int ry = this.ruleTop - this.ruleScroll;

            for (String pat : patterns) {
               if (ry + 14 > this.ruleTop && ry < this.ruleTop + ruleViewH) {
                  UiRenderer.frame(g, UiBounds.of(dx, ry, dw, 13), 705827348, this.BORDER);
                  this.drawText(g, pat, dx + 5, ry + 3, this.TEXT, false, dw - 26);
                  this.button(g, dx + dw - 16, ry, 14, 13, "×", this.BORDER, this.ERROR, mx, my, () -> this.removeRule(p, pat));
               }

               ry += 14;
            }

            if (patterns.isEmpty()) {
               this.drawText(g, "(none — this profile is never auto-applied)", dx + 2, this.ruleTop + 2, this.MUTED, false, dw - 4);
            }
         } finally {
            UiScissorStack.global().pop(g);
         }

         int var31 = dy + dh - 18;
         byte var32 = 78;
         byte var33 = 40;
         this.placeField(g, this.ruleField, dx, var31, dw - var32 - var33 - 8);
         this.button(g, dx + dw - var32 - var33 - 4, var31, var33, 16, "Add", this.ACCENT, this.TEXT, mx, my, this::addCurrentRule);
         this.button(g, dx + dw - var32, var31, var32, 16, "Use current", this.BORDER, this.TEXT, mx, my, this::fillCurrentServer);
      }
   }

   private void renderNameModal(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
      boolean create = this.nameMode == ProfilesPanel.NameMode.CREATE;
      int boxW = Math.min(w - 30, 280);
      int boxH = create ? 140 : 78;
      int boxX = x + (w - boxW) / 2;
      int boxY = y + (h - boxH) / 2;
      UiRenderer.frame(g, UiBounds.of(boxX, boxY, boxW, boxH), -267251180, this.ACCENT);

      String title = switch (this.nameMode) {
         case CREATE -> "New profile";
         case RENAME -> "Rename profile";
         case DUPLICATE -> "Duplicate profile";
         default -> "";
      };
      this.drawText(g, title, boxX + boxW / 2, boxY + 8, this.TEXT, true);
      this.placeField(g, this.nameField, boxX + 10, boxY + 24, boxW - 20);
      int half = (boxW - 24) / 2;
      int btnY = boxY + 50;
      if (create) {
         int tw = boxW - 20;
         int ty = boxY + 44;
         ty = this.toggle(g, boxX + 10, ty, tw, "Auto-save changes", this.createAutoSave, mx, my, () -> this.createAutoSave = !this.createAutoSave);
         ty = this.toggle(g, boxX + 10, ty, tw, "Own macro library", this.createOwnMacros, mx, my, () -> this.createOwnMacros = !this.createOwnMacros);
         ty = this.toggle(g, boxX + 10, ty, tw, "Own theme color", this.createOwnTheme, mx, my, () -> this.createOwnTheme = !this.createOwnTheme);
         btnY = ty + 3;
      }

      this.button(g, boxX + 10, btnY, half, 18, "OK", this.ACCENT, this.TEXT, mx, my, this::confirmName);
      this.button(g, boxX + 14 + half, btnY, half, 18, "Cancel", this.BORDER, this.TEXT, mx, my, this::closeName);
   }

   private void renderPrompt(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
      int boxW = Math.min(w - 30, 320);
      int boxH = 80;
      int boxX = x + (w - boxW) / 2;
      int boxY = y + (h - boxH) / 2;
      UiRenderer.frame(g, UiBounds.of(boxX, boxY, boxW, boxH), -267251180, -14249);
      RiptideProfile active = this.mgr.active();
      this.drawText(g, "Unsaved changes", boxX + boxW / 2, boxY + 8, -14249, true);
      this.drawText(g, "Save \"" + (active == null ? "" : active.displayName) + "\" before switching?", boxX + boxW / 2, boxY + 22, this.MUTED, true, boxW - 12);
      int third = (boxW - 28) / 3;
      this.button(g, boxX + 10, boxY + 50, third, 18, "Save", this.ACCENT, this.TEXT, mx, my, () -> this.resolvePrompt(true));
      this.button(g, boxX + 14 + third, boxY + 50, third, 18, "Discard", this.ERROR, this.TEXT, mx, my, () -> this.resolvePrompt(false));
      this.button(g, boxX + 18 + third * 2, boxY + 50, third, 18, "Cancel", this.BORDER, this.TEXT, mx, my, () -> {
         this.showPrompt = false;
         this.pendingLoad = null;
      });
   }

   private void doLoad(RiptideProfile target) {
      if (target != null) {
         RiptideProfile cur = this.mgr.active();
         if (cur == null || !target.id.equals(cur.id)) {
            if (cur != null && !cur.autoSave && this.mgr.isDirty()) {
               this.pendingLoad = target;
               this.showPrompt = true;
            } else {
               this.mgr.beginLoad(target);
               this.selectedId = target.id;
            }
         }
      }
   }

   private void resolvePrompt(boolean save) {
      RiptideProfile target = this.pendingLoad;
      this.showPrompt = false;
      this.pendingLoad = null;
      if (target != null) {
         if (save) {
            this.mgr.save(this.mgr.active());
         }

         this.mgr.beginLoad(target);
         this.selectedId = target.id;
      }
   }

   private void doDelete(RiptideProfile p) {
      if (p != null) {
         if (this.mgr.delete(p)) {
            this.selectedId = this.mgr.activeId();
            RiptideNotifications.show("Deleted profile: " + p.displayName, this.ERROR);
         }
      }
   }

   private void openName(ProfilesPanel.NameMode mode) {
      this.nameMode = mode;
      if (mode == ProfilesPanel.NameMode.CREATE) {
         this.createAutoSave = true;
         this.createOwnMacros = true;
         this.createOwnTheme = true;
      }

      RiptideProfile sel = this.mgr.byId(this.selectedId);
      this.nameField
         .setText(
            mode == ProfilesPanel.NameMode.RENAME && sel != null
               ? sel.displayName
               : (mode == ProfilesPanel.NameMode.DUPLICATE && sel != null ? sel.displayName + " copy" : "")
         );
      this.nameField.setFocused(true);
   }

   private void closeName() {
      this.nameMode = ProfilesPanel.NameMode.NONE;
      this.nameField.setFocused(false);
      this.nameField.setText("");
   }

   private void confirmName() {
      String name = this.nameField.text() == null ? "" : this.nameField.text().strip();
      if (name.isEmpty()) {
         RiptideNotifications.show("Enter a name.", -14249);
      } else {
         String exclude = this.nameMode == ProfilesPanel.NameMode.RENAME ? this.selectedId : null;
         if (this.mgr.nameExists(name, exclude)) {
            RiptideNotifications.show("Name already in use.", -14249);
         } else {
            switch (this.nameMode) {
               case CREATE:
                  RiptideProfile p = this.mgr.create(name, this.createAutoSave, this.createOwnMacros, this.createOwnTheme);
                  if (p != null) {
                     this.selectedId = p.id;
                     this.mgr.beginLoad(p);
                  }
                  break;
               case RENAME:
                  RiptideProfile selx = this.mgr.byId(this.selectedId);
                  if (selx != null) {
                     this.mgr.rename(selx, name);
                  }
                  break;
               case DUPLICATE:
                  RiptideProfile sel = this.mgr.byId(this.selectedId);
                  RiptideProfile d = this.mgr.duplicate(sel, name);
                  if (d != null) {
                     this.selectedId = d.id;
                  }
            }

            this.closeName();
         }
      }
   }

   private void addCurrentRule() {
      RiptideProfile sel = this.mgr.byId(this.selectedId);
      if (sel != null) {
         String pat = this.ruleField.text() == null ? "" : this.ruleField.text().strip().toLowerCase(Locale.ROOT);
         if (!pat.isEmpty()) {
            List<String> next = new ArrayList<>(sel.serverPatterns);
            if (!next.contains(pat)) {
               next.add(pat);
            }

            this.mgr.setServerPatterns(sel, next);
            this.ruleField.setText("");
         }
      }
   }

   private void removeRule(RiptideProfile p, String pattern) {
      if (p != null) {
         List<String> next = new ArrayList<>(p.serverPatterns);
         next.remove(pattern);
         this.mgr.setServerPatterns(p, next);
      }
   }

   private void fillCurrentServer() {
      Minecraft mc = Minecraft.getInstance();
      ServerData sd = mc == null ? null : mc.getCurrentServer();
      if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
         this.ruleField.setText(sd.ip.strip().toLowerCase(Locale.ROOT));
         this.ruleField.setFocused(true);
      } else {
         RiptideNotifications.show("Not connected to a server.", -14249);
      }
   }

   public boolean mouseClicked(int mx, int my, int button) {
      this.lastMx = mx;
      this.lastMy = my;
      if (button == 0 && !this.suppressInput && this.listScrollbar != null && this.listScrollbar.contains(mx, my)) {
         this.listDragging = true;
         this.listGrab = my - this.listScrollbar.thumbY();
         this.clearFocus();
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);

         for (CompactTextInput in : this.activeInputs) {
            if (in.mouseClicked(ctx, mx, my, button)) {
               for (CompactTextInput other : this.activeInputs) {
                  if (other != in) {
                     other.setFocused(false);
                  }
               }

               return true;
            }
         }

         this.clearFocus();
         if (button == 0) {
            for (ProfilesPanel.Hotspot hsp : this.hotspots) {
               if (hsp.hit(mx, my)) {
                  try {
                     hsp.action.run();
                  } catch (Throwable var9) {
                     RiptideNotifications.show("Action failed: " + var9.getClass().getSimpleName(), this.ERROR);
                  }

                  return true;
               }
            }
         }

         return mx >= this.bx && mx < this.bx + this.bw && my >= this.by && my < this.by + this.bh;
      }
   }

   public boolean mouseReleased(int mx, int my, int button) {
      if (this.listDragging) {
         this.listDragging = false;
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);
         boolean any = false;

         for (CompactTextInput in : this.activeInputs) {
            any |= in.mouseReleased(ctx, mx, my, button);
         }

         return any;
      }
   }

   public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
      if (this.listDragging && this.listScrollbar != null) {
         this.listScroll = CompactScrollbar.scrollFromThumb(this.listScrollbar, my, this.listGrab);
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);
         boolean any = false;

         for (CompactTextInput in : this.activeInputs) {
            any |= in.mouseDragged(ctx, mx, my, button, (float)dx, (float)dy);
         }

         return any;
      }
   }

   public boolean mouseScrolled(int mx, int my, double amount) {
      if (this.suppressInput) {
         return false;
      } else {
         int step = (int)Math.signum(amount) * 16;
         if (mx >= this.ruleX && mx < this.ruleX + this.ruleW && my >= this.ruleTop && my < this.ruleBottom) {
            this.ruleScroll = Math.max(0, this.ruleScroll - step);
            return true;
         } else if (mx >= this.listX && mx < this.listX + this.listW && my >= this.listY && my < this.listY + this.listH) {
            this.listScroll = Math.max(0, this.listScroll - step);
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         if (this.showPrompt) {
            this.showPrompt = false;
            this.pendingLoad = null;
            return true;
         }

         if (this.nameMode != ProfilesPanel.NameMode.NONE) {
            this.closeName();
            return true;
         }
      }

      DirectRenderContext ctx = this.renderCtx(null, this.lastMx, this.lastMy);

      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused() && in.keyPressed(ctx, keyCode, scanCode, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(char chr, int modifiers) {
      DirectRenderContext ctx = this.renderCtx(null, this.lastMx, this.lastMy);

      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused() && in.charTyped(ctx, chr, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasFocusedTextInput() {
      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused()) {
            return true;
         }
      }

      return false;
   }

   public void clearFocus() {
      this.nameField.setFocused(false);
      this.ruleField.setFocused(false);
   }

   public int desiredHeight() {
      return 260;
   }

   private DirectRenderContext renderCtx(GuiGraphicsExtractor g, int mx, int my) {
      return new DirectRenderContext(g, this.font, DirectViewport.current(1.0F), this.theme, mx, my, this.delta);
   }

   private void placeField(GuiGraphicsExtractor g, CompactTextInput f, int x, int y, int w) {
      f.setBounds(x, y, Math.max(10, w), 16.0F);
      f.render(this.renderCtx(g, this.lastMx, this.lastMy));
      if (!this.suppressInput) {
         this.activeInputs.add(f);
      }
   }

   private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int border, int textColor, int mx, int my, Runnable action) {
      boolean enabled = action != null;
      boolean hover = enabled && !this.suppressInput && mx >= x && mx < x + w && my >= y && my < y + h;
      Button.Tone tone = border != this.ACCENT && textColor != this.ACCENT ? Button.Tone.NORMAL : Button.Tone.PRIMARY;
      Button.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, tone, hover, false);
      if (!enabled) {
         UiRenderer.rect(g, UiBounds.of(x, y, w, h), 1711276032);
      }

      if (enabled && !this.suppressInput) {
         this.hotspots.add(new ProfilesPanel.Hotspot(x, y, w, h, action));
      }
   }

   private int toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean value, int mx, int my, Runnable onToggle) {
      int h = 18;
      boolean hover = !this.suppressInput && mx >= x && mx < x + w && my >= y && my < y + h;
      UiRenderer.frame(g, UiBounds.of(x, y, w, h), hover ? tint(this.BORDER, 51) : 705827348, this.BORDER);
      Toggle.renderLabeled(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, value, hover, "profiles:" + label);
      if (!this.suppressInput) {
         this.hotspots.add(new ProfilesPanel.Hotspot(x, y, w, h, onToggle));
      }

      return y + h + 3;
   }

   private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center) {
      this.drawText(g, text, x, y, color, center, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center, int maxWidth) {
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      String value = text == null ? "" : text;
      if (maxWidth != Integer.MAX_VALUE && !center) {
         UiText.drawEllipsized(g, this.font, value, fontId, color, x, y, Math.max(1, maxWidth), false);
      } else {
         if (maxWidth != Integer.MAX_VALUE) {
            value = UiText.trimToWidthEllipsis(this.font, value, maxWidth, fontId, color);
         }

         int wpx = UiText.width(this.font, value, fontId, color);
         UiText.draw(g, this.font, value, fontId, color, center ? x - wpx / 2 : x, y, false);
      }
   }

   private void refreshTheme() {
      RiptideTheme.State st = RiptideTheme.active();
      if (st != this.cachedTheme.state) {
         this.cachedTheme.state = st;
         this.BORDER = RiptideTheme.recolor(-13425624, RiptideTheme.Channel.OUTLINE);
         this.BORDER_BRIGHT = RiptideTheme.recolor(-8759968, RiptideTheme.Channel.OUTLINE);
         this.ACCENT = RiptideTheme.recolor(-13248397, RiptideTheme.Channel.ACCENT);
         this.TEXT = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         this.MUTED = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         this.ERROR = RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
      }
   }

   private static int tint(int color, int alpha) {
      return alpha << 24 | color & 16777215;
   }

   public interface Host {
      Screen returnScreen();
   }

   private static final class Hotspot {
      final int x;
      final int y;
      final int w;
      final int h;
      final Runnable action;

      Hotspot(int x, int y, int w, int h, Runnable action) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.action = action;
      }

      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum NameMode {
      NONE,
      CREATE,
      RENAME,
      DUPLICATE;
   }

   private static final class RiptideThemeStateRef {
      RiptideTheme.State state;
   }
}
