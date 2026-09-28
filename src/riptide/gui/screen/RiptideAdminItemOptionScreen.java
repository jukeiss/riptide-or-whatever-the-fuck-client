package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
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
import riptide.util.RiptideItemCommandSerializer;
import riptide.util.RiptideItemNbtInspector;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideUiScale;
import riptide.util.SnbtRepair;

public final class RiptideAdminItemOptionScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 430;
   private static final int PANEL_H = 286;
   private static final int HEADER_H = 18;
   private static final int FOOTER_H = 24;
   private static final int FULL_NBT_FOOTER_H = 44;
   private static final int TOOLBAR_GAP = 4;
   private final Screen parent;
   private final Module module;
   private final Setting<?, ?> option;
   private final RiptideAdminItemOptionScreen.Mode mode;
   private final CompactTextInput editor = new CompactTextInput()
      .setMultiline(true)
      .setMaxLength(1048576)
      .setHorizontalPadding(5)
      .setHoverEffectsEnabled(false)
      .setFocusEffectsEnabled(true);
   private DirectRenderContext lastDirectContext;
   private UiBounds closeBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds saveBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds cancelBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds formatBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds validateBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds copyBounds = UiBounds.of(0, 0, 0, 0);
   private UiBounds pasteBounds = UiBounds.of(0, 0, 0, 0);
   private String validationStatus = "";
   private boolean validationOk;

   public RiptideAdminItemOptionScreen(Screen parent, Module module, Setting<?, ?> option, RiptideAdminItemOptionScreen.Mode mode) {
      super(Component.literal("Edit " + (mode == null ? "Value" : mode.title)));
      this.parent = parent;
      this.module = module;
      this.option = option;
      this.mode = mode == null ? RiptideAdminItemOptionScreen.Mode.RAW : mode;
      if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT) {
         this.editor.setDisplayTextProvider(this::snbtSyntax);
         this.editor.setBackgroundColorOverride(1376389388);
         this.editor.setHoverEffectsEnabled(false);
         this.editor.setFocusEffectsEnabled(false);
      }

      this.editor.setText(this.decode(module != null && option != null ? module.value(option.id()) : ""));
      this.editor.setOnChange(ignored -> this.validationStatus = "");
      this.editor.setFocused(true);
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
         int w = Math.min(430, Math.max(180, sw - 8));
         int h = Math.min(286, Math.max(130, sh - 8));
         int x = Math.max(4, (sw - w) / 2);
         int y = Math.max(4, (sh - h) / 2);
         UiContext ui = UiContexts.overlay(graphics, this.font, mx, my);
         UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), -1728053248);
         UiBounds panel = UiBounds.of(x, y, w, h);
         CompactScreenPanel.render(ui, panel, 18, "Edit " + this.mode.title, mx >= x && mx < x + w && my >= y && my < y + 18);
         this.closeBounds = CompactScreenPanel.closeButton(panel, 18);
         int footerHeight = this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT ? 44 : 24;
         int footerTop = y + h - footerHeight;
         int tipY = y + 18 + 5;
         List<String> tipLines = ui.text().wrapFully(this.mode.tip, Math.max(1, w - 14));
         int maxTipLines = Math.min(2, tipLines.size());
         int lineH = 9;

         for (int i = 0; i < maxTipLines; i++) {
            ui.text().draw(graphics, tipLines.get(i), x + 7, tipY + i * lineH, ui.theme().colors().muted);
         }

         int editorY = tipY + Math.max(1, maxTipLines) * lineH + 4;
         int editorH = Math.max(42, footerTop - editorY - 5);
         this.editor.setBounds(x + 6, editorY, w - 12, editorH);
         DirectViewport viewport = DirectViewport.current(1.0F);
         this.lastDirectContext = new DirectRenderContext(graphics, this.font, viewport, THEME, mx, my, delta);
         this.editor.render(this.lastDirectContext);
         int buttonY = footerTop + 4;
         if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT) {
            int toolbarX = x + 6;
            int toolbarW = Math.max(1, w - 12);
            int cellW = Math.max(1, (toolbarW - 12) / 4);
            this.formatBounds = UiBounds.of(toolbarX, buttonY, cellW, 16);
            this.validateBounds = UiBounds.of(this.formatBounds.right() + 4, buttonY, cellW, 16);
            this.copyBounds = UiBounds.of(this.validateBounds.right() + 4, buttonY, cellW, 16);
            this.pasteBounds = UiBounds.of(this.copyBounds.right() + 4, buttonY, Math.max(1, x + w - 6 - this.copyBounds.right() - 4), 16);
            Button.render(ui, this.formatBounds, "Format", Button.Tone.NORMAL, this.formatBounds.contains(mx, my), false);
            Button.render(ui, this.validateBounds, "Validate", Button.Tone.NORMAL, this.validateBounds.contains(mx, my), false);
            Button.render(ui, this.copyBounds, "Copy", Button.Tone.NORMAL, this.copyBounds.contains(mx, my), false);
            Button.render(ui, this.pasteBounds, "Paste", Button.Tone.NORMAL, this.pasteBounds.contains(mx, my), false);
            buttonY += 20;
            String status = this.validationStatus.isBlank()
               ? this.editor.text().length() + " / 1,048,576 chars  •  Ctrl+S save  •  Ctrl+Shift+F format"
               : this.validationStatus;
            ui.text()
               .drawFitted(
                  graphics,
                  status,
                  x + 7,
                  buttonY + 5,
                  Math.max(1, w - 124),
                  this.validationStatus.isBlank() ? ui.theme().colors().muted : (this.validationOk ? ui.theme().colors().success : ui.theme().colors().bad)
               );
         } else {
            this.formatBounds = UiBounds.of(0, 0, 0, 0);
            this.validateBounds = UiBounds.of(0, 0, 0, 0);
            this.copyBounds = UiBounds.of(0, 0, 0, 0);
            this.pasteBounds = UiBounds.of(0, 0, 0, 0);
         }

         this.saveBounds = UiBounds.of(x + w - 110, buttonY, 50, 16);
         this.cancelBounds = UiBounds.of(x + w - 56, buttonY, 50, 16);
         Button.render(ui, this.saveBounds, "Save", Button.Tone.SUCCESS, this.saveBounds.contains(mx, my), false);
         Button.render(ui, this.cancelBounds, "Cancel", Button.Tone.NORMAL, this.cancelBounds.contains(mx, my), false);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
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
      } else if (event.button() == 0 && this.formatBounds.contains(mx, my)) {
         this.formatFullNbt();
         return true;
      } else if (event.button() == 0 && this.validateBounds.contains(mx, my)) {
         this.validateFullNbt();
         return true;
      } else if (event.button() == 0 && this.copyBounds.contains(mx, my)) {
         this.copyRaw();
         return true;
      } else if (event.button() == 0 && this.pasteBounds.contains(mx, my)) {
         this.pasteRaw();
         return true;
      } else {
         return this.lastDirectContext == null || this.editor.mouseClicked(this.lastDirectContext, mx, my, event.button());
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.lastDirectContext == null
         || this.editor.mouseReleased(this.lastDirectContext, RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button());
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.lastDirectContext == null
         || this.editor
            .mouseDragged(
               this.lastDirectContext, RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button(), (float)dx, (float)dy
            );
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return this.lastDirectContext == null
         || this.editor.mouseScrolled(this.lastDirectContext, RiptideUiScale.toVirtualInt(mouseX), RiptideUiScale.toVirtualInt(mouseY), (float)scrollY);
   }

   public boolean keyPressed(KeyEvent input) {
      boolean ctrl = (input.modifiers() & 10) != 0;
      boolean shift = (input.modifiers() & 1) != 0;
      if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT && ctrl && input.key() == 83) {
         this.save();
         return true;
      } else if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT && ctrl && shift && input.key() == 70) {
         this.formatFullNbt();
         return true;
      } else if (this.mode != RiptideAdminItemOptionScreen.Mode.FULL_NBT || !ctrl || input.key() != 257 && input.key() != 335) {
         if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT && input.key() == 258 && this.editor.isFocused()) {
            this.editor.insertText("  ");
            return true;
         } else if (input.key() == 256) {
            this.onClose();
            return true;
         } else {
            return this.lastDirectContext == null || this.editor.keyPressed(this.lastDirectContext, input.key(), input.scancode(), input.modifiers());
         }
      } else {
         this.validateFullNbt();
         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      return this.lastDirectContext == null || this.editor.charTyped(this.lastDirectContext, (char)input.codepoint(), 0);
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void save() {
      if (this.module != null && this.option != null) {
         String value = this.encode(this.editor.text());
         if ("nbt-item-components".equals(this.option.id()) && this.module instanceof BuiltinModules.AdminToolsModule adminTools) {
            adminTools.setRawItemComponents(value);
         } else if ("nbt-item-stack".equals(this.option.id()) && this.module instanceof BuiltinModules.AdminToolsModule adminTools) {
            if (!adminTools.setRawItemStackSnbt(value)) {
               this.validationOk = false;
               this.validationStatus = "Invalid full ItemStack SNBT";
               RiptideNotifications.error("Invalid full ItemStack SNBT.");
               return;
            }
         } else {
            this.module.setValue(this.option.id(), value);
         }

         if (!"nbt-item-stack".equals(this.option.id()) && this.module instanceof BuiltinModules.AdminToolsModule adminToolsx) {
            adminToolsx.prepareRawItemStackEditor();
         }
      }

      RiptideNotifications.show(this.mode.title + " updated.", -13248397);
      this.onClose();
   }

   private boolean validateFullNbt() {
      if (this.mode != RiptideAdminItemOptionScreen.Mode.FULL_NBT) {
         return true;
      } else {
         ItemStack stack = RiptideItemCommandSerializer.itemStackFromSnbt(this.editor.text());
         if (stack.isEmpty()) {
            this.validationOk = false;
            this.validationStatus = "Invalid ItemStack SNBT";
            RiptideNotifications.error(this.validationStatus);
            return false;
         } else {
            String error = RiptideItemCommandSerializer.validationError(stack);
            this.validationOk = error.isBlank();
            this.validationStatus = this.validationOk ? "Valid complete ItemStack" : error;
            if (this.validationOk) {
               RiptideNotifications.show("Full ItemStack NBT is valid.", -13248397);
            } else {
               RiptideNotifications.error(error);
            }

            return this.validationOk;
         }
      }
   }

   private void formatFullNbt() {
      if (this.mode == RiptideAdminItemOptionScreen.Mode.FULL_NBT) {
         String repaired = SnbtRepair.repair(this.editor.text());
         ItemStack stack = RiptideItemCommandSerializer.itemStackFromSnbt(repaired);
         if (stack.isEmpty()) {
            this.editor.setText(RiptideItemNbtInspector.prettySnbt(repaired));
            this.validationOk = false;
            this.validationStatus = "Formatted (structure repaired — still not a valid ItemStack; check ids/values)";
         } else {
            String error = RiptideItemCommandSerializer.validationError(stack);
            this.editor.setText(RiptideItemNbtInspector.prettySnbt(RiptideItemCommandSerializer.itemStackSnbt(stack)));
            this.validationOk = error.isBlank();
            this.validationStatus = this.validationOk ? "Formatted and valid" : "Formatted — " + error;
         }
      }
   }

   private void copyRaw() {
      if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
         String selected = this.editor.selectedText();
         this.minecraft.keyboardHandler.setClipboard(selected.isEmpty() ? this.editor.text() : selected);
         RiptideNotifications.copied(selected.isEmpty() ? "Copied full ItemStack NBT." : "Copied selection.");
      }
   }

   private void pasteRaw() {
      if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
         this.editor.insertText(this.minecraft.keyboardHandler.getClipboard());
      }
   }

   private Component snbtSyntax(String source) {
      String safe = source == null ? "" : source;
      if (safe.length() > 131072) {
         return Component.literal(safe);
      } else {
         MutableComponent out = Component.empty();
         char quote = 0;
         boolean escaped = false;
         int runStart = 0;
         int runColor = this.snbtColor(safe, 0, quote);

         for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            int color;
            if (quote != 0) {
               color = 15129460;
               if (escaped) {
                  escaped = false;
               } else if (ch == '\\') {
                  escaped = true;
               } else if (ch == quote) {
                  quote = 0;
               }
            } else if (ch != '"' && ch != '\'') {
               color = this.snbtColor(safe, i, quote);
            } else {
               quote = ch;
               color = 15129460;
            }

            if (i == 0) {
               runColor = color;
            } else if (color != runColor) {
               this.appendStyled(out, safe.substring(runStart, i), runColor);
               runStart = i;
               runColor = color;
            }
         }

         if (runStart < safe.length()) {
            this.appendStyled(out, safe.substring(runStart), runColor);
         }

         return out;
      }
   }

   private int snbtColor(String source, int index, char quote) {
      if (source != null && index >= 0 && index < source.length()) {
         char ch = source.charAt(index);
         if (Character.isWhitespace(ch)) {
            return 7829367;
         } else if ("{}[],:;".indexOf(ch) >= 0) {
            return 6740463;
         } else if (Character.isDigit(ch) || ch == '-' || ch == '+') {
            return 11436543;
         } else {
            return !Character.isLetter(ch) && ch != 95 && ch != 46 && ch != 33 ? 16316658 : 10936878;
         }
      } else {
         return 16316658;
      }
   }

   private void appendStyled(MutableComponent out, String text, int color) {
      if (text != null && !text.isEmpty()) {
         out.append(Component.literal(text).withStyle(style -> style.withColor(color)));
      }
   }

   private String decode(String raw) {
      String value = raw == null ? "" : raw;

      return switch (this.mode) {
         case LORE -> value.replace("|", "\n");
         case ENCHANTMENTS -> String.join("\n", splitTopLevel(value));
         default -> value;
         case FULL_NBT -> RiptideItemNbtInspector.prettySnbt(value);
      };
   }

   private String encode(String displayed) {
      String value = displayed == null ? "" : displayed;

      return switch (this.mode) {
         case LORE -> String.join("|", nonEmptyLines(value));
         case ENCHANTMENTS -> String.join(",", nonEmptyLines(value));
         default -> value;
      };
   }

   private static List<String> nonEmptyLines(String value) {
      List<String> out = new ArrayList<>();

      for (String line : value.split("\\R", -1)) {
         String trimmed = line.trim();
         if (!trimmed.isEmpty()) {
            out.add(trimmed);
         }
      }

      return out;
   }

   private static List<String> splitTopLevel(String raw) {
      List<String> out = new ArrayList<>();
      if (raw != null && !raw.isBlank()) {
         StringBuilder current = new StringBuilder();
         int depth = 0;
         boolean quoted = false;
         char quote = 0;
         boolean escaped = false;

         for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
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
                  String entry = current.toString().trim();
                  if (!entry.isEmpty()) {
                     out.add(entry);
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

         String entry = current.toString().trim();
         if (!entry.isEmpty()) {
            out.add(entry);
         }

         return out;
      } else {
         return out;
      }
   }

   public static enum Mode {
      LORE("Lore", "One lore line per row. Order is preserved."),
      ENCHANTMENTS("Enchantments", "One enchantment per row: minecraft:id:level"),
      ATTRIBUTES("Attributes", "Raw attribute component rows. Advanced values stay exact."),
      FULL_NBT("Full ItemStack NBT", "Complete ItemStack SNBT. Saving validates every field before it can be applied."),
      RAW("Raw Data", "Exact raw value. Multiline editing does not discard data.");

      private final String title;
      private final String tip;

      private Mode(String title, String tip) {
         this.title = title;
         this.tip = tip;
      }
   }
}
