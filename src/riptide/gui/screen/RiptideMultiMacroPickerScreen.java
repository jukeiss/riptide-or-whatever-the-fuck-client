package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfileManager;

public final class RiptideMultiMacroPickerScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MARGIN = 14;
   private static final int ROW_HEIGHT = 24;
   private static final int ROW_FRAME_H = 18;
   private final Screen parent;
   private String currentName;
   private final Consumer<String> onPick;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<RiptideMultiMacroPickerScreen.Row> rows = new ArrayList<>();
   private EditBox searchField;
   private String search = "";
   private String pendingDelete = "";
   private int scrollOffset;
   private boolean scrollbarDragging;
   private int scrollbarGrab;
   private long cachedNamesRevision = Long.MIN_VALUE;
   private String cachedNamesSearch = "";
   private List<String> cachedNames = List.of();
   private boolean searchDirty;

   public RiptideMultiMacroPickerScreen(Screen parent, String currentName, Consumer<String> onPick) {
      super(Component.literal("Choose Macro"));
      this.parent = parent;
      this.currentName = currentName == null ? "" : currentName;
      this.onPick = onPick;
   }

   public void tick() {
      super.tick();
      if (this.searchDirty || RiptideMacroManager.get().getRevision() != this.cachedNamesRevision) {
         this.searchDirty = false;
         this.rebuild();
      }
   }

   protected void init() {
      this.searchField = new EditBox(this.font, 24, 42, this.panelW() - 24, 18, Component.literal("Search macros"));
      this.searchField.setHint(Component.literal("Search macros..."));
      this.searchField.setMaxLength(128);
      this.searchField.setValue(this.search);
      this.searchField.setResponder(value -> {
         this.search = safeTrim(value);
         this.scrollOffset = 0;
         this.searchDirty = true;
      });
      this.addRenderableWidget(this.searchField);
      this.rebuild();
   }

   private void rebuild() {
      this.buttons.clear();
      this.rows.clear();
      this.buttons
         .add(
            CompactOverlayButton.create(this.screenWidth() - 14 - 10 - 60, 22, 60, 18, Component.literal("Back"), b -> this.onClose())
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      this.buttons
         .add(
            CompactOverlayButton.create(this.screenWidth() - 14 - 10 - 60 - 6 - 92, 22, 92, 18, Component.literal("New Macro"), b -> this.openMacroEditor(null))
               .setVariant(CompactOverlayButton.Variant.PRIMARY)
         );
      List<String> names = this.filteredNames();
      int total = 1 + names.size();
      int viewport = this.rowsBottom() - this.rowsTop();
      int visible = Math.max(1, viewport / 24);
      this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, Math.max(0, total - visible)));
      int y = this.rowsTop();

      for (int i = this.scrollOffset; i < total && y + 24 <= this.rowsBottom(); i++) {
         String name = i == 0 ? null : names.get(i - 1);
         CompactOverlayButton edit = null;
         CompactOverlayButton delete = null;
         if (name != null) {
            edit = CompactOverlayButton.create(
                  this.rowRight() - 46, y + 3, 42, 12, Component.literal("Edit"), b -> this.openMacroEditor(RiptideMacroManager.get().get(name))
               )
               .setVariant(CompactOverlayButton.Variant.SECONDARY);
            boolean arming = name.equals(this.pendingDelete);
            delete = CompactOverlayButton.create(
                  this.rowRight() - 46 - 6 - 52, y + 3, 52, 12, Component.literal(arming ? "Sure?" : "Delete"), b -> this.onDelete(name)
               )
               .setVariant(CompactOverlayButton.Variant.DANGER);
         }

         this.rows.add(new RiptideMultiMacroPickerScreen.Row(name, y, delete, edit));
         y += 24;
      }
   }

   private void onDelete(String name) {
      if (name != null && !name.isBlank()) {
         if (name.equals(this.pendingDelete)) {
            RiptideMacro macro = RiptideMacroManager.get().get(name);
            if (macro != null) {
               RiptideMacroManager.get().delete(macro);
            }

            MultiProfileManager.get().replaceMacroReferences(name, "");
            MultiManager.get().replaceMacroReference(name, "");
            if (name.equals(this.currentName)) {
               this.currentName = "";
               if (this.onPick != null) {
                  this.onPick.accept("");
               }
            }

            this.pendingDelete = "";
         } else {
            this.pendingDelete = name;
         }

         this.rebuild();
      }
   }

   private void openMacroEditor(RiptideMacro macro) {
      RiptideMacroEditorOverlay editor = RiptideMacroEditorOverlay.getSharedOverlay();
      if (editor != null) {
         RiptideOverlayManager.get().register(editor, IRiptideOverlay.OverlayScope.HOST_SCREEN);
         boolean inWorld = this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null;
         editor.setConfigurationOnly(!inWorld);
         String oldName = macro != null && macro.name != null ? macro.name : "";
         editor.openForMulti(macro, saved -> {
            if (saved != null && saved.name != null && !oldName.isBlank() && !oldName.equals(saved.name)) {
               MultiProfileManager.get().replaceMacroReferences(oldName, saved.name);
               MultiManager.get().replaceMacroReference(oldName, saved.name);
               if (oldName.equals(this.currentName)) {
                  this.currentName = saved.name;
                  if (this.onPick != null) {
                     this.onPick.accept(saved.name);
                  }
               }
            }
         });
         if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new RiptideOverlayHostScreen(editor, this, true));
         }
      }
   }

   private void pick(String name) {
      if (this.onPick != null) {
         this.onPick.accept(name == null ? "" : name);
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private List<String> filteredNames() {
      String query = this.search.toLowerCase(Locale.ROOT);
      long revision = RiptideMacroManager.get().getRevision();
      if (revision == this.cachedNamesRevision && query.equals(this.cachedNamesSearch)) {
         return this.cachedNames;
      } else {
         List<String> out = new ArrayList<>();

         for (RiptideMacro macro : RiptideMacroManager.get().getAll()) {
            if (macro != null && macro.name != null && (query.isEmpty() || macro.name.toLowerCase(Locale.ROOT).contains(query))) {
               out.add(macro.name);
            }
         }

         this.cachedNamesRevision = revision;
         this.cachedNamesSearch = query;
         this.cachedNames = List.copyOf(out);
         return this.cachedNames;
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0) {
         CompactScrollbar.Metrics bar = this.scrollbarMetrics();
         if (bar.hasScroll() && bar.contains(virtualEvent.x(), virtualEvent.y())) {
            this.scrollbarDragging = true;
            this.scrollbarGrab = bar.overThumb(virtualEvent.x(), virtualEvent.y()) ? (int)Math.round(virtualEvent.y() - bar.thumbY()) : bar.thumbHeight() / 2;
            this.scrollOffset = this.clampScroll(CompactScrollbar.scrollFromThumb(bar, virtualEvent.y(), this.scrollbarGrab) / 24);
            this.rebuild();
            return true;
         }

         for (CompactOverlayButton button : this.buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
               return true;
            }
         }

         for (RiptideMultiMacroPickerScreen.Row row : this.rows) {
            if (row.delete() != null && CompactOverlayButton.fireIfHit(row.delete(), virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
               return true;
            }
         }

         for (RiptideMultiMacroPickerScreen.Row rowx : this.rows) {
            if (rowx.edit() != null && CompactOverlayButton.fireIfHit(rowx.edit(), virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
               return true;
            }
         }

         for (RiptideMultiMacroPickerScreen.Row rowxx : this.rows) {
            if (virtualEvent.x() >= this.rowX() && virtualEvent.x() < this.rowRight() && virtualEvent.y() >= rowxx.y() && virtualEvent.y() < rowxx.y() + 18) {
               this.pendingDelete = "";
               this.pick(rowxx.name() == null ? "" : rowxx.name());
               return true;
            }
         }
      }

      return super.mouseClicked(virtualEvent, doubled);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.scrollbarDragging) {
         CompactScrollbar.Metrics bar = this.scrollbarMetrics();
         this.scrollOffset = this.clampScroll(CompactScrollbar.scrollFromThumb(bar, virtualEvent.y(), this.scrollbarGrab) / 24);
         this.rebuild();
         return true;
      } else {
         return super.mouseDragged(virtualEvent, RiptideUiScale.toVirtual(dragX), RiptideUiScale.toVirtual(dragY));
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      this.scrollOffset = this.clampScroll(this.scrollOffset + (vertical < 0.0 ? 1 : -1));
      this.rebuild();
      return true;
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      int total = 1 + this.filteredNames().size();
      int viewport = this.rowsBottom() - this.rowsTop();
      return CompactScrollbar.compute(total * 24, viewport, 14 + this.panelW() - 8, this.rowsTop(), 4, viewport, this.scrollOffset * 24);
   }

   private int clampScroll(int rowIndex) {
      int total = 1 + this.filteredNames().size();
      int visible = Math.max(1, (this.rowsBottom() - this.rowsTop()) / 24);
      return Math.max(0, Math.min(rowIndex, Math.max(0, total - visible)));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), themeBg());
         UiRenderer.frame(graphics, UiBounds.of(14, 14, this.panelW(), this.screenHeight() - 28), themePanel(), themeBorder());
         this.drawText(graphics, "Choose Macro", 24, 24, themeText());

         for (RiptideMultiMacroPickerScreen.Row row : this.rows) {
            this.renderRow(graphics, row);
         }

         for (CompactOverlayButton button : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
         }

         CompactScrollbar.Metrics bar = this.scrollbarMetrics();
         if (bar.hasScroll()) {
            CompactScrollbar.draw(graphics, bar, bar.contains(virtualMouseX, virtualMouseY), this.scrollbarDragging);
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderRow(GuiGraphicsExtractor graphics, RiptideMultiMacroPickerScreen.Row row) {
      boolean none = row.name() == null;
      boolean selected = none ? this.currentName.isBlank() : this.currentName.equals(row.name());
      int fill = selected ? themeSelected() : RiptideTheme.recolor(-1206643689, RiptideTheme.Channel.BUTTON);
      UiRenderer.rect(graphics, UiBounds.of(this.rowX(), row.y(), this.rowRight() - this.rowX(), 18), fill);
      if (selected) {
         UiRenderer.rect(graphics, UiBounds.of(this.rowX(), row.y(), 2, 18), RiptideTheme.recolor(-12588930, RiptideTheme.Channel.SUCCESS));
      }

      String label = none ? "None (clear macro)" : row.name();
      int textRight = row.delete() != null ? row.delete().getX() - 6 : (row.edit() != null ? row.edit().getX() - 6 : this.rowRight() - 8);
      this.drawFitted(graphics, label, this.rowX() + 8, row.y() + 5, Math.max(20, textRight - this.rowX() - 8), selected ? themeSuccess() : themeText());
      if (row.delete() != null) {
         CompactOverlayButton.renderStyled(graphics, this.font, row.delete(), Integer.MIN_VALUE, Integer.MIN_VALUE);
      }

      if (row.edit() != null) {
         CompactOverlayButton.renderStyled(graphics, this.font, row.edit(), Integer.MIN_VALUE, Integer.MIN_VALUE);
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private int panelW() {
      return this.screenWidth() - 28;
   }

   private int rowX() {
      return 24;
   }

   private int rowRight() {
      return 14 + this.panelW() - 14;
   }

   private int rowsTop() {
      return 66;
   }

   private int rowsBottom() {
      return this.screenHeight() - 18;
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      UiText.draw(graphics, this.font, text, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      String safe = UiText.trimToWidthEllipsis(this.font, text == null ? "" : text, Math.max(1, maxWidth), fontId, color);
      UiText.draw(graphics, this.font, safe, fontId, color, x, y, false);
   }

   private static int themeBg() {
      return RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP);
   }

   private static int themePanel() {
      return RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON);
   }

   private static int themeSelected() {
      return RiptideTheme.recolor(858052714, RiptideTheme.Channel.SUCCESS);
   }

   private static int themeBorder() {
      return RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
   }

   private static int themeText() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private static int themeSuccess() {
      return RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
   }

   private record Row(String name, int y, CompactOverlayButton delete, CompactOverlayButton edit) {
   }
}
