package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import riptide.api.module.Setting;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.modules.BuiltinModules;
import riptide.modules.Module;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideUiScale;

public final class RiptideAdminItemStructuredScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 540;
   private static final int PANEL_H = 318;
   private static final int HEADER_H = 18;
   private static final int ROW_H = 18;
   private static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
   private static final List<String> SLOTS = List.of("any", "mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "body", "saddle");
   private final Screen parent;
   private final Module module;
   private final Setting<?, ?> option;
   private final RiptideAdminItemStructuredScreen.Mode mode;
   private final CompactTextInput search = input("Search registry");
   private final CompactTextInput level = input("1").setText("1");
   private final CompactTextInput amount = input("1").setText("1");
   private final CompactTextInput modifier = input("riptide:modifier").setText("riptide:modifier");
   private final List<String> entries = new ArrayList<>();
   private DirectRenderContext direct;
   private List<RiptideAdminItemStructuredScreen.RegistryRow> filteredRows = List.of();
   private String cachedSearch = null;
   private int resultScroll;
   private int entryScroll;
   private int selectedIndex = -1;
   private String operation = OPERATIONS.get(0);
   private String slot = "mainhand";
   private UiBounds closeBounds = empty();
   private UiBounds saveBounds = empty();
   private UiBounds cancelBounds = empty();
   private UiBounds rawBounds = empty();
   private UiBounds applyBounds = empty();
   private UiBounds operationBounds = empty();
   private UiBounds slotBounds = empty();
   private UiBounds resultBounds = empty();
   private UiBounds entryBounds = empty();
   private final List<RiptideAdminItemStructuredScreen.RowHit> resultHits = new ArrayList<>();
   private final List<RiptideAdminItemStructuredScreen.RowHit> entryHits = new ArrayList<>();

   public RiptideAdminItemStructuredScreen(Screen parent, Module module, Setting<?, ?> option, RiptideAdminItemStructuredScreen.Mode mode) {
      super(Component.literal("Edit " + mode.title));
      this.parent = parent;
      this.module = module;
      this.option = option;
      this.mode = mode;
      this.entries.addAll(splitTopLevel(module != null && option != null ? module.value(option.id()) : ""));
   }

   private static CompactTextInput input(String placeholder) {
      return new CompactTextInput().setPlaceholder(placeholder).setMaxLength(4096).setHorizontalPadding(4).setFieldHeight(16);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         int sw = this.screenWidth();
         int sh = this.screenHeight();
         int w = Math.min(540, Math.max(300, sw - 8));
         int h = Math.min(318, Math.max(188, sh - 8));
         int x = Math.max(4, (sw - w) / 2);
         int y = Math.max(4, (sh - h) / 2);
         UiContext ui = UiContexts.overlay(graphics, this.font, mx, my);
         this.direct = new DirectRenderContext(graphics, this.font, DirectViewport.current(1.0F), THEME, mx, my, delta);
         UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), -1728053248);
         UiBounds panel = UiBounds.of(x, y, w, h);
         CompactScreenPanel.render(ui, panel, 18, "Edit " + this.mode.title, mx >= x && mx < x + w && my >= y && my < y + 18);
         this.closeBounds = CompactScreenPanel.closeButton(panel, 18);
         int tipY = y + 18 + 5;
         List<String> tipLines = ui.text().wrapFully(this.mode.tip, Math.max(1, w - 14));
         int tipLineCount = Math.min(2, tipLines.size());
         int lineH = 9;

         for (int i = 0; i < tipLineCount; i++) {
            ui.text().draw(graphics, tipLines.get(i), x + 7, tipY + i * lineH, ui.theme().colors().muted);
         }

         int searchY = tipY + Math.max(1, tipLineCount) * lineH + 4;
         this.search.setBounds(x + 6, searchY, w - 12, 16.0F);
         this.search.render(this.direct);
         this.refreshRows();
         int contentY = searchY + 20;
         int footerY = y + h - 20;
         int contentH = Math.max(50, footerY - contentY - 5);
         boolean stacked = w < 430 || contentH < 132;
         if (stacked) {
            int available = Math.max(96, contentH - 6);
            int resultH = Math.max(42, Math.min(available / 2, contentH - 48));
            this.resultBounds = UiBounds.of(x + 6, contentY, w - 12, resultH);
            this.entryBounds = UiBounds.of(x + 6, this.resultBounds.bottom() + 6, w - 12, Math.max(42, footerY - this.resultBounds.bottom() - 11));
         } else {
            int leftW = Math.max(128, (w - 18) / 2);
            this.resultBounds = UiBounds.of(x + 6, contentY, leftW, contentH);
            this.entryBounds = UiBounds.of(this.resultBounds.right() + 6, contentY, Math.max(120, w - leftW - 18), contentH);
         }

         this.renderResults(ui, mx, my);
         this.renderEntries(ui, mx, my);
         this.rawBounds = UiBounds.of(x + 6, footerY, 48, 16);
         this.saveBounds = UiBounds.of(x + w - 110, footerY, 50, 16);
         this.cancelBounds = UiBounds.of(x + w - 56, footerY, 50, 16);
         Button.render(ui, this.rawBounds, "Raw", Button.Tone.NORMAL, this.rawBounds.contains(mx, my), false);
         Button.render(ui, this.saveBounds, "Save", Button.Tone.SUCCESS, this.saveBounds.contains(mx, my), false);
         Button.render(ui, this.cancelBounds, "Cancel", Button.Tone.NORMAL, this.cancelBounds.contains(mx, my), false);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderResults(UiContext ui, int mx, int my) {
      UiRenderer.frame(ui.graphics(), this.resultBounds, ui.theme().colors().windowStrong, ui.theme().colors().borderSoft);
      int headerH = 15;
      ui.text()
         .drawFitted(
            ui.graphics(), "Available", this.resultBounds.x() + 5, this.resultBounds.y() + 4, this.resultBounds.width() - 10, ui.theme().colors().muted
         );
      this.resultHits.clear();
      int visible = Math.max(1, (this.resultBounds.height() - headerH - 2) / 18);
      this.resultScroll = clamp(this.resultScroll, 0, Math.max(0, this.filteredRows.size() - visible));

      for (int row = 0; row < visible; row++) {
         int index = this.resultScroll + row;
         if (index >= this.filteredRows.size()) {
            break;
         }

         RiptideAdminItemStructuredScreen.RegistryRow entry = this.filteredRows.get(index);
         UiBounds bounds = UiBounds.of(this.resultBounds.x() + 1, this.resultBounds.y() + headerH + row * 18, this.resultBounds.width() - 2, 18);
         UiRenderer.rect(ui.graphics(), bounds, bounds.contains(mx, my) ? ui.theme().colors().rowHover : ui.theme().colors().row);
         ui.text().drawFitted(ui.graphics(), entry.label, bounds.x() + 4, bounds.y() + 5, bounds.width() - 8, ui.theme().colors().text);
         this.resultHits.add(new RiptideAdminItemStructuredScreen.RowHit(bounds, index));
      }
   }

   private void renderEntries(UiContext ui, int mx, int my) {
      UiRenderer.frame(ui.graphics(), this.entryBounds, ui.theme().colors().windowStrong, ui.theme().colors().borderSoft);
      int x = this.entryBounds.x() + 5;
      int w = this.entryBounds.width() - 10;
      int controlsH;
      if (this.mode == RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS) {
         ui.text().drawFitted(ui.graphics(), "Level", x, this.entryBounds.y() + 4, 36, ui.theme().colors().muted);
         this.level.setBounds(x + 38, this.entryBounds.y() + 2, Math.max(42, w - 38), 16.0F);
         this.level.render(this.direct);
         this.operationBounds = empty();
         this.slotBounds = empty();
         controlsH = 23;
      } else {
         ui.text().drawFitted(ui.graphics(), "Amount", x, this.entryBounds.y() + 4, 42, ui.theme().colors().muted);
         this.amount.setBounds(x + 44, this.entryBounds.y() + 2, Math.max(38, w - 44), 16.0F);
         this.amount.render(this.direct);
         this.modifier.setBounds(x, this.entryBounds.y() + 21, w, 16.0F);
         this.modifier.render(this.direct);
         this.operationBounds = UiBounds.of(x, this.entryBounds.y() + 40, Math.max(42, (w - 4) / 2), 16);
         this.slotBounds = UiBounds.of(this.operationBounds.right() + 4, this.entryBounds.y() + 40, Math.max(38, w - this.operationBounds.width() - 4), 16);
         Button.render(ui, this.operationBounds, this.shortOperation(), Button.Tone.NORMAL, this.operationBounds.contains(mx, my), false);
         Button.render(ui, this.slotBounds, this.slot, Button.Tone.NORMAL, this.slotBounds.contains(mx, my), false);
         controlsH = 62;
      }

      this.applyBounds = UiBounds.of(x, this.entryBounds.y() + controlsH, w, 16);
      Button.render(
         ui,
         this.applyBounds,
         this.selectedIndex >= 0 ? "Update Selected" : "Select an available entry",
         Button.Tone.PRIMARY,
         this.applyBounds.contains(mx, my),
         false
      );
      int listY = this.applyBounds.bottom() + 5;
      ui.text().drawFitted(ui.graphics(), "Current entries", x, listY, w, ui.theme().colors().muted);
      listY += 13;
      int listH = Math.max(16, this.entryBounds.bottom() - listY - 2);
      this.entryHits.clear();
      int visible = Math.max(1, listH / 18);
      this.entryScroll = clamp(this.entryScroll, 0, Math.max(0, this.entries.size() - visible));

      for (int row = 0; row < visible; row++) {
         int index = this.entryScroll + row;
         if (index >= this.entries.size()) {
            break;
         }

         UiBounds bounds = UiBounds.of(this.entryBounds.x() + 1, listY + row * 18, this.entryBounds.width() - 2, 18);
         boolean selected = this.selectedIndex == index;
         UiRenderer.rect(
            ui.graphics(),
            bounds,
            selected ? ui.theme().colors().accentSoft : (bounds.contains(mx, my) ? ui.theme().colors().rowHover : ui.theme().colors().row)
         );
         ui.text()
            .drawFitted(ui.graphics(), this.entries.get(index), bounds.x() + 4, bounds.y() + 5, Math.max(1, bounds.width() - 24), ui.theme().colors().text);
         UiBounds remove = UiBounds.of(bounds.right() - 17, bounds.y() + 2, 14, 14);
         Button.render(ui, remove, "X", Button.Tone.DANGER, remove.contains(mx, my), false);
         this.entryHits.add(new RiptideAdminItemStructuredScreen.RowHit(bounds, index, remove));
      }
   }

   private void refreshRows() {
      String filter = this.search.text().trim().toLowerCase(Locale.ROOT);
      if (!filter.equals(this.cachedSearch)) {
         this.cachedSearch = filter;
         List<RiptideAdminItemStructuredScreen.RegistryRow> rows = new ArrayList<>();
         if (this.mode == RiptideAdminItemStructuredScreen.Mode.ATTRIBUTES) {
            for (Entry<ResourceKey<Attribute>, Attribute> entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
               Identifier id = entry.getKey().identifier();
               Attribute attribute = entry.getValue();
               String label = Component.translatable(attribute.getDescriptionId()).getString();
               this.addRow(rows, filter, id.toString(), label);
            }
         } else {
            Registry<Enchantment> registry = this.enchantmentRegistry();
            if (registry != null) {
               for (Entry<ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
                  Identifier id = entry.getKey().identifier();
                  Enchantment enchantment = entry.getValue();
                  String label = enchantment.description() == null ? "" : enchantment.description().getString();
                  this.addRow(rows, filter, id.toString(), label);
               }
            }
         }

         rows.sort(
            Comparator.comparing(RiptideAdminItemStructuredScreen.RegistryRow::label, String.CASE_INSENSITIVE_ORDER)
               .thenComparing(RiptideAdminItemStructuredScreen.RegistryRow::id)
         );
         this.filteredRows = List.copyOf(rows);
         this.resultScroll = 0;
      }
   }

   private void addRow(List<RiptideAdminItemStructuredScreen.RegistryRow> rows, String filter, String id, String label) {
      String resolved = label != null && !label.isBlank() ? label : RiptideRegistryLabels.identifier(id);
      String searchable = (id + " " + resolved).toLowerCase(Locale.ROOT);
      if (filter.isEmpty() || searchable.contains(filter)) {
         rows.add(new RiptideAdminItemStructuredScreen.RegistryRow(id, resolved));
      }
   }

   private Registry<Enchantment> enchantmentRegistry() {
      try {
         RegistryAccess access;
         if (this.minecraft != null && this.minecraft.level != null) {
            access = this.minecraft.level.registryAccess();
         } else if (this.minecraft != null && this.minecraft.getConnection() != null) {
            access = this.minecraft.getConnection().registryAccess();
         } else {
            access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
         }

         Optional<Registry<Enchantment>> registry = access.lookup(Registries.ENCHANTMENT);
         return registry.orElse(null);
      } catch (RuntimeException var3) {
         return null;
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() == 0 && this.closeBounds.contains(mx, my)) {
         this.onClose();
         return true;
      } else if (event.button() == 0 && this.saveBounds.contains(mx, my)) {
         this.save();
         return true;
      } else if (event.button() == 0 && this.cancelBounds.contains(mx, my)) {
         this.onClose();
         return true;
      } else if (event.button() == 0 && this.rawBounds.contains(mx, my)) {
         this.saveDraft();
         if (this.minecraft != null) {
            this.minecraft
               .gui
               .setScreen(
                  new RiptideAdminItemOptionScreen(
                     this.parent,
                     this.module,
                     this.option,
                     this.mode == RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS
                        ? RiptideAdminItemOptionScreen.Mode.ENCHANTMENTS
                        : RiptideAdminItemOptionScreen.Mode.ATTRIBUTES
                  )
               );
         }

         return true;
      } else if (event.button() == 0 && this.operationBounds.contains(mx, my)) {
         this.operation = cycle(OPERATIONS, this.operation);
         return true;
      } else if (event.button() == 0 && this.slotBounds.contains(mx, my)) {
         this.slot = cycle(SLOTS, this.slot);
         return true;
      } else if (event.button() == 0 && this.applyBounds.contains(mx, my) && this.selectedIndex >= 0 && this.selectedIndex < this.entries.size()) {
         this.entries.set(this.selectedIndex, this.updatedEntry(this.entries.get(this.selectedIndex)));
         return true;
      } else {
         for (RiptideAdminItemStructuredScreen.RowHit hit : this.entryHits) {
            if (event.button() == 0 && hit.remove.contains(mx, my)) {
               this.entries.remove(hit.index);
               if (this.selectedIndex == hit.index) {
                  this.selectedIndex = -1;
               } else if (this.selectedIndex > hit.index) {
                  this.selectedIndex--;
               }

               return true;
            }

            if (event.button() == 0 && hit.bounds.contains(mx, my)) {
               this.selectEntry(hit.index);
               return true;
            }
         }

         for (RiptideAdminItemStructuredScreen.RowHit hit : this.resultHits) {
            if (event.button() == 0 && hit.bounds.contains(mx, my)) {
               this.entries.add(this.newEntry(this.filteredRows.get(hit.index).id));
               this.selectedIndex = this.entries.size() - 1;
               this.selectEntry(this.selectedIndex);
               return true;
            }
         }

         if (this.direct == null) {
            return false;
         } else {
            this.unfocusInputs();
            return this.search.mouseClicked(this.direct, mx, my, event.button())
               || this.level.mouseClicked(this.direct, mx, my, event.button())
               || this.amount.mouseClicked(this.direct, mx, my, event.button())
               || this.modifier.mouseClicked(this.direct, mx, my, event.button());
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      int direction = scrollY < 0.0 ? 1 : -1;
      if (this.resultBounds.contains(mx, my)) {
         this.resultScroll = Math.max(0, this.resultScroll + direction);
         return true;
      } else if (this.entryBounds.contains(mx, my)) {
         this.entryScroll = Math.max(0, this.entryScroll + direction);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.direct == null) {
         return false;
      } else {
         int mx = RiptideUiScale.toVirtualInt(event.x());
         int my = RiptideUiScale.toVirtualInt(event.y());
         return this.search.mouseReleased(this.direct, mx, my, event.button())
            || this.level.mouseReleased(this.direct, mx, my, event.button())
            || this.amount.mouseReleased(this.direct, mx, my, event.button())
            || this.modifier.mouseReleased(this.direct, mx, my, event.button());
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.direct == null) {
         return false;
      } else {
         int mx = RiptideUiScale.toVirtualInt(event.x());
         int my = RiptideUiScale.toVirtualInt(event.y());
         return this.search.mouseDragged(this.direct, mx, my, event.button(), (float)dx, (float)dy)
            || this.level.mouseDragged(this.direct, mx, my, event.button(), (float)dx, (float)dy)
            || this.amount.mouseDragged(this.direct, mx, my, event.button(), (float)dx, (float)dy)
            || this.modifier.mouseDragged(this.direct, mx, my, event.button(), (float)dx, (float)dy);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.onClose();
         return true;
      } else {
         return this.direct == null
            ? false
            : this.search.keyPressed(this.direct, input.key(), input.scancode(), input.modifiers())
               || this.level.keyPressed(this.direct, input.key(), input.scancode(), input.modifiers())
               || this.amount.keyPressed(this.direct, input.key(), input.scancode(), input.modifiers())
               || this.modifier.keyPressed(this.direct, input.key(), input.scancode(), input.modifiers());
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.direct == null) {
         return false;
      } else {
         char chr = (char)input.codepoint();
         return this.search.charTyped(this.direct, chr, 0)
            || this.level.charTyped(this.direct, chr, 0)
            || this.amount.charTyped(this.direct, chr, 0)
            || this.modifier.charTyped(this.direct, chr, 0);
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void save() {
      this.saveDraft();
      RiptideNotifications.show(this.mode.title + " updated.", -13248397);
      this.onClose();
   }

   private void saveDraft() {
      if (this.module != null && this.option != null) {
         this.module.setValue(this.option.id(), String.join(",", this.entries));
         if (this.module instanceof BuiltinModules.AdminToolsModule adminTools) {
            adminTools.prepareRawItemStackEditor();
         }
      }
   }

   private void selectEntry(int index) {
      if (index >= 0 && index < this.entries.size()) {
         this.selectedIndex = index;
         String entry = this.entries.get(index);
         if (this.mode == RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS) {
            int split = entry.lastIndexOf(58);
            this.level.setText(split > 0 && split < entry.length() - 1 ? entry.substring(split + 1) : "1");
         } else {
            this.amount.setText(extract(entry, "amount", "1"));
            this.operation = sanitizeChoice(OPERATIONS, extract(entry, "operation", OPERATIONS.get(0)));
            this.slot = sanitizeChoice(SLOTS, extract(entry, "slot", "mainhand"));
            this.modifier.setText(extract(entry, "id", "riptide:modifier"));
         }
      }
   }

   private String newEntry(String id) {
      return this.mode == RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS
         ? stripMinecraft(id) + ":" + clamp(parseInt(this.level.text(), 1), 1, 255)
         : this.attributeEntry(id, null);
   }

   private String updatedEntry(String existing) {
      if (this.mode == RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS) {
         int split = existing.lastIndexOf(58);
         String id = split > 0 ? existing.substring(0, split) : existing;
         return stripMinecraft(id) + ":" + clamp(parseInt(this.level.text(), 1), 1, 255);
      } else {
         return this.attributeEntry(extract(existing, "type", "minecraft:generic.attack_damage"), existing);
      }
   }

   private String attributeEntry(String type, String existing) {
      String value = sanitizeNumber(this.amount.text(), "1");
      String modifierId = sanitizeIdentifier(this.modifier.text(), "riptide:modifier");
      String display = extractTopLevelValue(existing, "display");
      return "{type:\""
         + escape(type)
         + "\",amount:"
         + value
         + ",operation:\""
         + escape(this.operation)
         + "\",slot:\""
         + escape(this.slot)
         + "\",id:\""
         + escape(modifierId)
         + "\""
         + (display.isBlank() ? "" : ",display:" + display)
         + "}";
   }

   private static String extractTopLevelValue(String entry, String key) {
      if (entry != null && key != null && !key.isBlank()) {
         String text = entry.trim();
         if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1);
         }

         for (String part : splitTopLevel(text)) {
            int separator = topLevelSeparator(part);
            if (separator > 0) {
               String candidate = part.substring(0, separator).trim();
               if (candidate.startsWith("\"") && candidate.endsWith("\"") || candidate.startsWith("'") && candidate.endsWith("'")) {
                  candidate = candidate.substring(1, candidate.length() - 1);
               }

               if (key.equals(candidate)) {
                  return part.substring(separator + 1).trim();
               }
            }
         }

         return "";
      } else {
         return "";
      }
   }

   private static int topLevelSeparator(String text) {
      int depth = 0;
      char quote = 0;
      boolean escaped = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (quote != 0) {
            if (escaped) {
               escaped = false;
            } else if (c == '\\') {
               escaped = true;
            } else if (c == quote) {
               quote = 0;
            }
         } else if (c == '"' || c == '\'') {
            quote = c;
         } else if (c == '{' || c == '[' || c == '(') {
            depth++;
         } else if (c != '}' && c != ']' && c != ')') {
            if (c == ':' && depth == 0) {
               return i;
            }
         } else {
            depth = Math.max(0, depth - 1);
         }
      }

      return -1;
   }

   private String shortOperation() {
      String var1 = this.operation;

      return switch (var1) {
         case "add_multiplied_base" -> "Multiply Base";
         case "add_multiplied_total" -> "Multiply Total";
         default -> "Add Value";
      };
   }

   private void unfocusInputs() {
      this.search.setFocused(false);
      this.level.setFocused(false);
      this.amount.setFocused(false);
      this.modifier.setFocused(false);
   }

   private static String extract(String entry, String key, String fallback) {
      if (entry == null) {
         return fallback;
      } else {
         String needle = key + ":";
         int start = entry.indexOf(needle);
         if (start < 0) {
            return fallback;
         } else {
            start += needle.length();

            while (start < entry.length() && Character.isWhitespace(entry.charAt(start))) {
               start++;
            }

            if (start >= entry.length()) {
               return fallback;
            } else {
               char quote = entry.charAt(start);
               if (quote != '"' && quote != '\'') {
                  int end = start;

                  while (end < entry.length() && entry.charAt(end) != ',' && entry.charAt(end) != '}') {
                     end++;
                  }

                  String value = entry.substring(start, end).trim();
                  return value.isEmpty() ? fallback : value;
               } else {
                  int end = entry.indexOf(quote, start + 1);
                  return end > start ? entry.substring(start + 1, end) : fallback;
               }
            }
         }
      }
   }

   private static List<String> splitTopLevel(String raw) {
      List<String> out = new ArrayList<>();
      if (raw != null && !raw.isBlank()) {
         String text = raw.trim();
         if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
         }

         StringBuilder current = new StringBuilder();
         int depth = 0;
         boolean quoted = false;
         char quote = 0;
         boolean escaped = false;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
               current.append(c);
               escaped = false;
            } else if (c == '\\') {
               current.append(c);
               escaped = true;
            } else if (quoted) {
               current.append(c);
               if (c == quote) {
                  quoted = false;
               }
            } else if (c != '\'' && c != '"') {
               if (c == '{' || c == '[' || c == '(') {
                  depth++;
               } else if (c == '}' || c == ']' || c == ')') {
                  depth = Math.max(0, depth - 1);
               }

               if (c == ',' && depth == 0) {
                  String value = current.toString().trim();
                  if (!value.isEmpty()) {
                     out.add(value);
                  }

                  current.setLength(0);
               } else {
                  current.append(c);
               }
            } else {
               current.append(c);
               quoted = true;
               quote = c;
            }
         }

         String value = current.toString().trim();
         if (!value.isEmpty()) {
            out.add(value);
         }

         return out;
      } else {
         return out;
      }
   }

   private static String cycle(List<String> choices, String current) {
      int index = choices.indexOf(current);
      return choices.get((index + 1 + choices.size()) % choices.size());
   }

   private static String sanitizeChoice(List<String> choices, String value) {
      return choices.contains(value) ? value : choices.get(0);
   }

   private static String sanitizeNumber(String value, String fallback) {
      try {
         double number = Double.parseDouble(value);
         return Double.isFinite(number) ? value : fallback;
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static String sanitizeIdentifier(String value, String fallback) {
      return Identifier.tryParse(value) == null ? fallback : value;
   }

   private static String stripMinecraft(String id) {
      return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
   }

   private static String escape(String value) {
      return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   private static UiBounds empty() {
      return UiBounds.of(0, 0, 0, 0);
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (Exception var3) {
         return fallback;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }

   public static enum Mode {
      ENCHANTMENTS("Enchantments", "Pick an enchantment, set its level, then add it."),
      ATTRIBUTES("Attributes", "Pick an attribute and keep the exact modifier controls editable.");

      private final String title;
      private final String tip;

      private Mode(String title, String tip) {
         this.title = title;
         this.tip = tip;
      }
   }

   private record RegistryRow(String id, String label) {
   }

   private record RowHit(UiBounds bounds, int index, UiBounds remove) {
      private RowHit(UiBounds bounds, int index) {
         this(bounds, index, RiptideAdminItemStructuredScreen.empty());
      }
   }
}
