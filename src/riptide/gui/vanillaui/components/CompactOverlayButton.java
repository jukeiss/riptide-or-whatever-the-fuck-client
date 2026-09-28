package riptide.gui.vanillaui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideText;
import riptide.util.RiptideTheme;

public final class CompactOverlayButton {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int HORIZONTAL_PADDING = 4;
   public boolean active = true;
   public boolean visible = true;
   private int x;
   private int y;
   private int width;
   private int height;
   private Component message;
   private CompactOverlayButton.Variant variant = CompactOverlayButton.Variant.SECONDARY;
   private UiTone tone = UiTone.BODY;
   private Boolean toggleState;
   private String animationKey;
   private Identifier icon;
   private Boolean selected;
   private final CompactOverlayButton.PressAction primaryAction;
   private final CompactOverlayButton.PressAction secondaryAction;

   private static Identifier resolvedFont(int height) {
      return THEME.fontFor(UiTone.BODY);
   }

   private static int fittedWidth(int requestedWidth, int height, Component message) {
      if (requestedWidth > 0) {
         return requestedWidth;
      } else {
         Minecraft client = Minecraft.getInstance();
         Font renderer = client == null ? null : client.font;
         if (renderer == null) {
            return requestedWidth;
         } else {
            String label = RiptideText.sanitizeUiLabel(message == null ? "" : message.getString());
            Identifier font = resolvedFont(height);
            int measured = UiSizing.fitTextWidthInt(renderer, label, font, THEME.color(UiTone.BODY), 4, requestedWidth, Integer.MAX_VALUE);
            return Math.max(requestedWidth, measured);
         }
      }
   }

   private CompactOverlayButton(
      int x, int y, int width, int height, Component message, CompactOverlayButton.PressAction primaryAction, CompactOverlayButton.PressAction secondaryAction
   ) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.message = (Component)(message == null ? Component.empty() : message);
      this.primaryAction = primaryAction;
      this.secondaryAction = secondaryAction;
   }

   public static CompactOverlayButton create(int x, int y, int width, int height, Component message, CompactOverlayButton.PressAction pressAction) {
      return new CompactOverlayButton(x, y, fittedWidth(width, height, message), height, message, pressAction, null);
   }

   public static CompactOverlayButton create(
      int x,
      int y,
      int width,
      int height,
      Component message,
      CompactOverlayButton.PressAction pressAction,
      CompactOverlayButton.PressAction secondaryPressAction
   ) {
      return new CompactOverlayButton(x, y, fittedWidth(width, height, message), height, message, pressAction, secondaryPressAction);
   }

   public CompactOverlayButton setTone(UiTone tone) {
      this.tone = tone == null ? UiTone.BODY : tone;
      return this;
   }

   public CompactOverlayButton setToggleState(boolean toggleState) {
      this.toggleState = toggleState;
      return this;
   }

   public CompactOverlayButton setAnimationKey(String animationKey) {
      this.animationKey = animationKey;
      return this;
   }

   public CompactOverlayButton setIcon(Identifier icon) {
      this.icon = icon;
      return this;
   }

   public CompactOverlayButton setSelected(boolean selected) {
      this.selected = selected;
      return this;
   }

   public static boolean fireIfHit(CompactOverlayButton button, double mouseX, double mouseY, int mouseButton) {
      if (button != null && button.visible && button.active) {
         if ((mouseButton == 0 || mouseButton == 1) && button.contains(mouseX, mouseY)) {
            CompactOverlayButton.PressAction action = mouseButton == 1 ? button.secondaryAction : button.primaryAction;
            if (action == null) {
               return false;
            } else {
               action.onPress(button);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void renderStyled(GuiGraphicsExtractor context, Font textRenderer, CompactOverlayButton button, int mouseX, int mouseY) {
      if (button != null && button.visible) {
         if (button.icon != null) {
            boolean iconHovered = button.contains(mouseX, mouseY);
            CompactSymbolButton.renderIcon(
               context,
               textRenderer,
               UiBounds.of(button.x, button.y, button.width, button.height),
               button.icon,
               iconHovered,
               button.active,
               button.variant == CompactOverlayButton.Variant.DANGER
            );
         } else {
            String label = RiptideText.sanitizeUiLabel(button.message.getString());
            String upperLabel = label.toUpperCase();
            boolean inferredDanger = "x".equalsIgnoreCase(label) || label.toLowerCase().contains("delete") || label.toLowerCase().contains("clear");
            boolean toggleOn = label.startsWith("[x]")
               || label.startsWith("[X]")
               || label.startsWith("[✓]")
               || upperLabel.equals("ON")
               || upperLabel.endsWith(": ON")
               || upperLabel.equals("MODE: ENABLE")
               || upperLabel.equals("ENABLE")
               || upperLabel.equals("ENABLED")
               || upperLabel.endsWith(": ENABLED");
            boolean toggleOff = label.startsWith("[ ]")
               || upperLabel.equals("OFF")
               || upperLabel.endsWith(": OFF")
               || upperLabel.equals("MODE: DISABLE")
               || upperLabel.equals("DISABLE")
               || upperLabel.equals("DISABLED")
               || upperLabel.endsWith(": DISABLED");
            CompactOverlayButton.Variant variant = button.variant;
            if (variant == CompactOverlayButton.Variant.SECONDARY && inferredDanger) {
               variant = CompactOverlayButton.Variant.DANGER;
            }

            if (variant == CompactOverlayButton.Variant.SECONDARY && toggleOn) {
               variant = CompactOverlayButton.Variant.SUCCESS;
            }

            if (variant == CompactOverlayButton.Variant.SECONDARY && toggleOff) {
               variant = CompactOverlayButton.Variant.DANGER;
            }

            boolean hovered = button.contains(mouseX, mouseY);
            UiContext ui = UiContexts.overlay(context, textRenderer, mouseX, mouseY);
            UiBounds bounds = UiBounds.of(button.x, button.y, button.width, button.height);
            boolean allowInferredToggle = button.variant != CompactOverlayButton.Variant.FILTER_ON && button.variant != CompactOverlayButton.Variant.FILTER_OFF;
            Boolean resolvedToggleState = button.toggleState != null
               ? button.toggleState
               : (allowInferredToggle ? (toggleOn ? Boolean.TRUE : (toggleOff ? Boolean.FALSE : null)) : null);
            if (resolvedToggleState != null) {
               AnimatedToggleButton.render(
                  ui, bounds, label, resolvedToggleState, hovered && button.active, button.animationKey == null ? stableVisualKey(button) : button.animationKey
               );
            } else {
               boolean var10000;
               if (button.selected != null) {
                  var10000 = button.selected;
               } else {
                  switch (variant) {
                     case DANGER:
                     case SUCCESS:
                     case PRIMARY:
                     case FILTER_ON:
                        var10000 = true;
                        break;
                     case GHOST:
                     default:
                        var10000 = false;
                  }
               }

               boolean on = var10000;
               Button.render(ui, bounds, label, buttonTone(variant), hovered && button.active, on);
            }

            if (!button.active && !Boolean.TRUE.equals(button.selected)) {
               UiRenderer.rect(context, bounds.inset(1), 1711276032);
               UiRenderer.outline(context, bounds, RiptideTheme.recolor(-2009060816, RiptideTheme.Channel.OUTLINE));
            }
         }
      }
   }

   public int getX() {
      return this.x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return this.y;
   }

   public void setY(int y) {
      this.y = y;
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public int getHeight() {
      return this.height;
   }

   public Component getMessage() {
      return this.message;
   }

   public void setMessage(Component message) {
      this.message = (Component)(message == null ? Component.empty() : message);
   }

   public CompactOverlayButton.Variant getVariant() {
      return this.variant;
   }

   public CompactOverlayButton setVariant(CompactOverlayButton.Variant variant) {
      this.variant = variant == null ? CompactOverlayButton.Variant.SECONDARY : variant;
      return this;
   }

   private boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }

   private static Button.Tone buttonTone(CompactOverlayButton.Variant variant) {
      return switch (variant) {
         case SECONDARY, GHOST, FILTER_OFF -> Button.Tone.NORMAL;
         case DANGER -> Button.Tone.DANGER;
         case SUCCESS, FILTER_ON -> Button.Tone.SUCCESS;
         case PRIMARY -> Button.Tone.PRIMARY;
      };
   }

   private static String stableVisualKey(CompactOverlayButton button) {
      return "overlay-toggle:" + button.x + ":" + button.y + ":" + button.width + ":" + button.height + ":" + button.tone;
   }

   @FunctionalInterface
   public interface PressAction {
      void onPress(CompactOverlayButton var1);
   }

   public static enum Variant {
      SECONDARY,
      DANGER,
      SUCCESS,
      PRIMARY,
      GHOST,
      FILTER_ON,
      FILTER_OFF;
   }
}
