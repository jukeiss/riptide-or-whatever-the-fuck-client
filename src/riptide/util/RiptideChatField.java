package riptide.util;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.commands.RiptideCommands;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.modules.PackHideState;

public class RiptideChatField {
   private static final CompactTheme THEME = new CompactTheme();
   private final Minecraft mc;
   private final Font textRenderer;
   private final CompactTextInput field = new CompactTextInput().setHorizontalPadding(3).setTextYOffset(1);
   private int x;
   private int y;
   private int width;
   private int height;
   private boolean submitOnEnter;
   private boolean multiline;
   private boolean enterInsertsNewline;
   private boolean spaceKeyInsertsSpace;
   private boolean spaceInsertedFromKey;
   private Predicate<String> submitHandler;
   private String placeholderText = "";

   public RiptideChatField(Minecraft mc, Font textRenderer, int x, int y, int width, int height, boolean submitOnEnter) {
      this.mc = mc;
      this.textRenderer = textRenderer;
      this.x = x;
      this.y = y;
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.submitOnEnter = submitOnEnter;
      this.field.setHistoryProvider(() -> (Collection<String>)(this.mc.gui != null ? this.mc.gui.hud.getChat().getRecentChat() : List.of()));
      this.syncBounds();
   }

   public void setSubmitOnEnter(boolean submitOnEnter) {
      this.submitOnEnter = submitOnEnter;
   }

   public void setSubmitHandler(Predicate<String> submitHandler) {
      this.submitHandler = submitHandler;
   }

   public void setChangedListener(Consumer<String> changedListener) {
      this.field.setOnChange(changedListener);
   }

   public void setDisplayTextProvider(Function<String, Component> displayTextProvider) {
      this.field.setDisplayTextProvider(displayTextProvider);
   }

   public void setDisplayTextRevisionProvider(LongSupplier revisionProvider) {
      this.field.setDisplayTextRevisionProvider(revisionProvider);
   }

   public void setMultiline(boolean multiline) {
      this.multiline = multiline;
      this.field.setMultiline(multiline);
   }

   public void setEnterInsertsNewline(boolean enterInsertsNewline) {
      this.enterInsertsNewline = enterInsertsNewline;
   }

   public void setHoverEffectsEnabled(boolean hoverEffectsEnabled) {
      this.field.setHoverEffectsEnabled(hoverEffectsEnabled);
   }

   public void setFocusEffectsEnabled(boolean focusEffectsEnabled) {
      this.field.setFocusEffectsEnabled(focusEffectsEnabled);
   }

   public void setBackgroundColorOverride(int backgroundColorOverride) {
      this.field.setBackgroundColorOverride(backgroundColorOverride);
   }

   public void setSpaceKeyInsertsSpace(boolean spaceKeyInsertsSpace) {
      this.spaceKeyInsertsSpace = spaceKeyInsertsSpace;
   }

   public void setHistoryNavigationEnabled(boolean historyNavigationEnabled) {
      this.field.setHistoryNavigationEnabled(historyNavigationEnabled);
   }

   public void setPlaceholder(Component placeholder) {
      this.placeholderText = placeholder == null ? "" : RiptideText.sanitizeUiLabel(placeholder.getString());
      this.field.setPlaceholder(this.placeholderText);
   }

   public String getPlaceholderText() {
      return this.placeholderText;
   }

   public void setDrawsBackground(boolean drawsBackground) {
      this.field.setDrawBackground(drawsBackground);
   }

   public void setEditable(boolean editable) {
      this.field.setEditable(editable);
   }

   public void setFilter(Predicate<String> filter) {
      this.field.setFilter(filter);
   }

   public void setNumericOnly(boolean allowNegative) {
      this.field.setFilter(next -> {
         if (next != null && !next.isEmpty()) {
            for (int i = 0; i < next.length(); i++) {
               char chr = next.charAt(i);
               if ((!allowNegative || chr != '-' || i != 0) && !Character.isDigit(chr)) {
                  return false;
               }
            }

            return true;
         } else {
            return true;
         }
      });
   }

   public boolean isFocused() {
      return this.field.isFocused();
   }

   public void setFocused(boolean focused) {
      this.field.setFocused(focused);
   }

   public void setMaxLength(int maxLength) {
      this.field.setMaxLength(maxLength);
   }

   public void setText(String text) {
      this.field.setText(text);
   }

   public String getText() {
      return this.field.text();
   }

   public void write(String content) {
      if (content != null && !content.isEmpty() && this.field.isFocused() && this.field.isEditable()) {
         this.field.insertText(content);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      } else {
         this.syncBounds();
         if (!this.contains(mouseX, mouseY)) {
            this.field.setFocused(false);
            return false;
         } else {
            return this.field.mouseClicked(this.inputContext(), (float)mouseX, (float)mouseY, button);
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean ignored) {
      return this.mouseClicked(click.x(), click.y(), 0);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.syncBounds();
      return this.field.mouseReleased(this.inputContext(), (float)mouseX, (float)mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      this.syncBounds();
      return this.field.mouseDragged(this.inputContext(), (float)mouseX, (float)mouseY, button, (float)deltaX, (float)deltaY);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      this.syncBounds();
      return !this.contains(mouseX, mouseY) ? false : this.field.mouseScrolled(this.inputContext(), (float)mouseX, (float)mouseY, (float)amount);
   }

   public boolean keyPressed(KeyEvent keyInput) {
      if (!this.field.isFocused()) {
         return false;
      } else {
         int key = keyInput.key();
         if (key != 257 && key != 335) {
            if (this.spaceKeyInsertsSpace && key == 32) {
               int mods = keyInput.modifiers();
               if ((mods & 14) == 0) {
                  this.spaceInsertedFromKey = true;
                  return this.field.charTyped(this.inputContext(), ' ', mods);
               }
            }

            return this.field.keyPressed(this.inputContext(), key, keyInput.scancode(), keyInput.modifiers());
         } else if (this.submitOnEnter) {
            return this.handleSubmit();
         } else if (this.multiline && this.enterInsertsNewline) {
            return this.field.keyPressed(this.inputContext(), key, keyInput.scancode(), keyInput.modifiers());
         } else {
            this.field.setFocused(false);
            return true;
         }
      }
   }

   public boolean charTyped(CharacterEvent charInput) {
      if (charInput == null) {
         return false;
      } else {
         if (charInput.codepoint() == 32) {
            if (this.spaceInsertedFromKey) {
               this.spaceInsertedFromKey = false;
               return true;
            }
         } else {
            this.spaceInsertedFromKey = false;
         }

         return this.field.isFocused() && this.field.charTyped(this.inputContext(), (char)charInput.codepoint(), 0);
      }
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      this.syncBounds();
      this.field.render(this.renderContext(context, mouseX, mouseY, delta));
   }

   public int getX() {
      return this.x;
   }

   public void setX(int x) {
      this.x = x;
      this.syncBounds();
   }

   public int getY() {
      return this.y;
   }

   public void setY(int y) {
      this.y = y;
      this.syncBounds();
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int width) {
      this.width = Math.max(1, width);
      this.syncBounds();
   }

   public int getHeight() {
      return this.height;
   }

   public void setHeight(int height) {
      this.height = Math.max(1, height);
      this.syncBounds();
   }

   public void setSelectionEnd(int selectionEnd) {
      this.field.setSelectionEnd(selectionEnd);
   }

   public String getSelectedText() {
      return this.field.selectedText();
   }

   public void insertText(String text) {
      this.field.insertText(text);
   }

   private void syncBounds() {
      this.field.setBounds(this.x, this.y, this.width, this.height);
   }

   private boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }

   private boolean handleSubmit() {
      String value = this.field.text().trim();
      if (value.isEmpty()) {
         return true;
      } else if (PackHideState.isHardLocked()) {
         this.field.setText("");
         this.field.setFocused(false);
         return true;
      } else {
         boolean submitted = this.submitHandler != null ? this.submitHandler.test(value) : this.sendChat(value);
         if (!submitted) {
            return true;
         } else if (RiptideCommands.isBlockedPanicCommandMessage(value)) {
            this.field.setText("");
            this.field.setFocused(false);
            return true;
         } else {
            RiptideClientMessaging.rememberRecentChat(value);
            this.field.addHistoryEntry(value);
            this.field.setText("");
            this.field.setFocused(false);
            return true;
         }
      }
   }

   private boolean sendChat(String message) {
      if (PackHideState.isHardLocked()) {
         return true;
      } else if (RiptideCommands.isRiptideCommandMessage(message)) {
         if (RiptideCommands.isBlockedPanicCommandMessage(message)) {
            return true;
         } else {
            String body = RiptideCommands.commandBody(message);
            if (!body.isBlank()) {
               RiptideCommands.dispatch(body);
            }

            return true;
         }
      } else if (this.mc.getConnection() == null) {
         riptide.RiptideClientAddon.LOG.warn("Failed to send Riptide chat message because network handler was null.");
         return false;
      } else {
         if (message.startsWith("/") && message.length() > 1) {
            this.mc.getConnection().sendCommand(message.substring(1));
         } else {
            this.mc.getConnection().sendChat(message);
         }

         return true;
      }
   }

   private DirectRenderContext inputContext() {
      return new DirectRenderContext(null, this.textRenderer, DirectViewport.current(2.0F), THEME, 0.0F, 0.0F, 0.0F);
   }

   private DirectRenderContext renderContext(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float delta) {
      return new DirectRenderContext(drawContext, this.textRenderer, DirectViewport.current(2.0F), THEME, mouseX, mouseY, delta);
   }
}
