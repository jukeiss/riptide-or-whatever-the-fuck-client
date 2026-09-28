package riptide.gui.vanillaui.components;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class ColorPicker {
   private static final int WIDTH = 244;
   private static final int HEIGHT = 188;
   private static final int HEADER_H = 16;
   private static final int SV_W = 102;
   private static final int SV_H = 82;
   private static final int HUE_W = 13;
   private static final int HUE_H = 82;
   private static final int HUE_BUCKETS = 360;
   private static final int BACKDROP = -1728053248;
   private static final Minecraft MC = Minecraft.getInstance();
   private static ColorPicker.GradientTexture hueTexture;
   private static ColorPicker.GradientTexture saturationValueTexture;
   private static int saturationValueHueBucket = Integer.MIN_VALUE;
   private final Consumer<Integer> onSave;
   private final int initialArgb;
   private final int defaultArgb;
   private final int preservedAlpha;
   private UiBounds bounds;
   private boolean open = true;
   private float hue;
   private float saturation;
   private float brightness;
   private boolean hexFocused;
   private String hexText;
   private int hexCursor;
   private int hexSelectionAnchor = -1;
   private int draftRgb;
   private int draftArgb;
   private int draftRed;
   private int draftGreen;
   private int draftBlue;
   private ColorPicker.DragTarget dragTarget = ColorPicker.DragTarget.NONE;

   public ColorPicker(UiBounds anchor, int initialArgb, int defaultArgb, int screenWidth, int screenHeight, Consumer<Integer> onSave) {
      this.onSave = onSave;
      this.initialArgb = initialArgb;
      this.defaultArgb = defaultArgb;
      this.preservedAlpha = initialArgb >>> 24 & 0xFF;
      this.setDraftRgb(initialArgb & 16777215);
      int x = anchor == null ? 8 : anchor.right() - 244;
      int y = anchor == null ? 8 : anchor.bottom() + 4;
      if (anchor != null && y + 188 > screenHeight - 4) {
         y = anchor.y() - 188 - 4;
      }

      this.bounds = UiBounds.of(x, y, 244, 188).clampInside(Math.max(1, screenWidth - 4), Math.max(1, screenHeight - 4));
   }

   public boolean isOpen() {
      return this.open;
   }

   public void closeCancel() {
      this.open = false;
   }

   public boolean contains(int mouseX, int mouseY) {
      return this.open && this.bounds.contains(mouseX, mouseY);
   }

   public void render(UiContext context) {
      if (this.open) {
         UiRenderer.rect(context.graphics(), UiBounds.of(0, 0, context.screenWidth(), context.screenHeight()), -1728053248);
         CompactWindow.renderFrame(
            context, this.bounds, "Color", false, false, true, this.headerBounds().contains(context.mouseX(), context.mouseY()), true, 5, 5, 16
         );
         UiBounds close = TopBar.closeButton(this.headerBounds());
         boolean closeHover = close.contains(context.mouseX(), context.mouseY());
         if (closeHover) {
            UiRenderer.rect(context.graphics(), close.inset(1), 587202559);
         }

         UiBounds sv = this.svBounds();
         this.renderSaturationValue(context, sv);
         this.drawCrosshair(context, sv);
         UiBounds hueStrip = this.hueBounds();
         this.renderHueStrip(context, hueStrip);
         int hueY = hueStrip.y() + Math.round((1.0F - this.hue) * (hueStrip.height() - 1));
         UiRenderer.outline(context.graphics(), UiBounds.of(hueStrip.x() - 1, hueY - 1, hueStrip.width() + 2, 3), context.theme().colors().text);
         UiBounds current = UiBounds.of(this.bounds.x() + 144, this.bounds.y() + 25, 38, 20);
         UiBounds draft = UiBounds.of(this.bounds.x() + 190, this.bounds.y() + 25, 38, 20);
         this.drawSwatch(context, current, this.initialArgb);
         this.drawSwatch(context, draft, this.draftArgb());
         context.text()
            .drawCentered(context.graphics(), "Old", UiBounds.of(current.x(), current.bottom() + 2, current.width(), 10), context.theme().colors().muted);
         context.text().drawCentered(context.graphics(), "New", UiBounds.of(draft.x(), draft.bottom() + 2, draft.width(), 10), context.theme().colors().muted);
         this.renderSlider(context, this.redBounds(), "R", this.red(), -43691);
         this.renderSlider(context, this.greenBounds(), "G", this.green(), -11149961);
         this.renderSlider(context, this.blueBounds(), "B", this.blue(), -10053121);
         UiBounds hex = this.hexBounds();
         TextField.render(context, hex, this.hexText, "RRGGBB", this.hexFocused, this.hexCursor, this.hexSelectionStart(), this.hexSelectionEnd());
         context.text().drawFitted(context.graphics(), "#", hex.x() - 9, context.text().centeredY(hex), 8, context.theme().colors().muted);
         Button.render(context, this.saveBounds(), "Save", Button.Tone.SUCCESS, this.saveBounds().contains(context.mouseX(), context.mouseY()), false);
         Button.render(context, this.resetBounds(), "Reset", Button.Tone.NORMAL, this.resetBounds().contains(context.mouseX(), context.mouseY()), false);
         Button.render(context, this.cancelBounds(), "Cancel", Button.Tone.DANGER, this.cancelBounds().contains(context.mouseX(), context.mouseY()), false);
      }
   }

   public boolean mouseClicked(int mouseX, int mouseY, int button) {
      if (!this.open) {
         return false;
      } else if (button != 0) {
         return true;
      } else if (!this.bounds.contains(mouseX, mouseY)) {
         this.closeCancel();
         return true;
      } else if (TopBar.closeButton(this.headerBounds()).contains(mouseX, mouseY) || this.cancelBounds().contains(mouseX, mouseY)) {
         this.closeCancel();
         return true;
      } else if (this.saveBounds().contains(mouseX, mouseY)) {
         if (this.onSave != null) {
            this.onSave.accept(this.draftArgb());
         }

         this.open = false;
         return true;
      } else if (this.resetBounds().contains(mouseX, mouseY)) {
         this.setDraftRgb(this.defaultArgb & 16777215);
         return true;
      } else {
         UiBounds hex = this.hexBounds();
         this.hexFocused = hex.contains(mouseX, mouseY);
         if (this.hexFocused) {
            this.hexCursor = this.cursorFromHexMouse(mouseX, hex);
            this.hexSelectionAnchor = this.hexCursor;
            this.dragTarget = ColorPicker.DragTarget.HEX;
            return true;
         } else {
            if (this.svBounds().contains(mouseX, mouseY)) {
               this.dragTarget = ColorPicker.DragTarget.SV;
               this.updateSaturationValue(mouseX, mouseY);
            } else if (this.hueBounds().contains(mouseX, mouseY)) {
               this.dragTarget = ColorPicker.DragTarget.HUE;
               this.updateHue(mouseY);
            } else if (this.redBounds().contains(mouseX, mouseY)) {
               this.dragTarget = ColorPicker.DragTarget.RED;
               this.updateChannel(mouseX, this.redBounds(), 0);
            } else if (this.greenBounds().contains(mouseX, mouseY)) {
               this.dragTarget = ColorPicker.DragTarget.GREEN;
               this.updateChannel(mouseX, this.greenBounds(), 1);
            } else if (this.blueBounds().contains(mouseX, mouseY)) {
               this.dragTarget = ColorPicker.DragTarget.BLUE;
               this.updateChannel(mouseX, this.blueBounds(), 2);
            } else {
               this.dragTarget = ColorPicker.DragTarget.NONE;
            }

            return true;
         }
      }
   }

   public boolean mouseReleased(int mouseX, int mouseY, int button) {
      this.dragTarget = ColorPicker.DragTarget.NONE;
      return this.open;
   }

   public boolean mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
      if (this.open && button == 0) {
         switch (this.dragTarget) {
            case SV:
               this.updateSaturationValue(mouseX, mouseY);
               break;
            case HUE:
               this.updateHue(mouseY);
               break;
            case RED:
               this.updateChannel(mouseX, this.redBounds(), 0);
               break;
            case GREEN:
               this.updateChannel(mouseX, this.greenBounds(), 1);
               break;
            case BLUE:
               this.updateChannel(mouseX, this.blueBounds(), 2);
               break;
            case HEX:
               this.hexCursor = this.cursorFromHexMouse(mouseX, this.hexBounds());
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      return this.open;
   }

   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (!this.open) {
         return false;
      } else if (key == 256) {
         this.closeCancel();
         return true;
      } else if (key != 257 && key != 335) {
         if (!this.hexFocused) {
            return true;
         } else {
            boolean ctrl = (modifiers & 2) != 0;
            if (ctrl && key == 67) {
               Minecraft.getInstance().keyboardHandler.setClipboard(this.selectedHexTextOrAll());
               return true;
            } else if (ctrl && key == 86) {
               this.pasteHex(Minecraft.getInstance().keyboardHandler.getClipboard(), this.hasHexSelection());
               return true;
            } else if (ctrl && key == 65) {
               this.selectAllHex();
               return true;
            } else {
               if (key == 259 && !this.hexText.isEmpty()) {
                  if (this.hasHexSelection()) {
                     this.replaceSelectedHex("");
                  } else if (this.hexCursor > 0) {
                     this.hexText = this.hexText.substring(0, this.hexCursor - 1) + this.hexText.substring(this.hexCursor);
                     this.hexCursor--;
                     this.applyHexText();
                  }
               } else if (key == 261) {
                  if (this.hasHexSelection()) {
                     this.replaceSelectedHex("");
                  } else if (this.hexCursor < this.hexText.length()) {
                     this.hexText = this.hexText.substring(0, this.hexCursor) + this.hexText.substring(this.hexCursor + 1);
                     this.applyHexText();
                  }
               } else if (key == 268 || key == 269 || key == 263 || key == 262) {
                  boolean shift = (modifiers & 1) != 0;
                  this.moveHexCursor(key, shift);
                  return true;
               }

               return true;
            }
         }
      } else {
         if (this.hexFocused) {
            this.applyHexText();
         }

         if (this.onSave != null) {
            this.onSave.accept(this.draftArgb());
         }

         this.open = false;
         return true;
      }
   }

   public boolean charTyped(char chr) {
      if (this.open && this.hexFocused) {
         if (isHex(chr)) {
            if (this.hasHexSelection()) {
               this.replaceSelectedHex(Character.toString(Character.toUpperCase(chr)));
            } else if (this.hexText.length() < 6) {
               this.hexText = (this.hexText.substring(0, this.hexCursor) + Character.toUpperCase(chr) + this.hexText.substring(this.hexCursor))
                  .toUpperCase(Locale.ROOT);
               this.hexCursor++;
               this.clearHexSelection();
               this.applyHexText();
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private int cursorFromHexMouse(int mouseX, UiBounds hex) {
      int local = Math.max(0, mouseX - hex.x() - 4);
      int cursor = this.hexText.length();

      for (int i = 0; i <= this.hexText.length(); i++) {
         if (MC.font.width(this.hexText.substring(0, i)) >= local) {
            cursor = i;
            break;
         }
      }

      return cursor;
   }

   private void moveHexCursor(int key, boolean shift) {
      if (shift && this.hexSelectionAnchor < 0) {
         this.hexSelectionAnchor = this.hexCursor;
      }

      if (!shift) {
         this.clearHexSelection();
      }

      switch (key) {
         case 262:
            this.hexCursor = Math.min(this.hexText.length(), this.hexCursor + 1);
            break;
         case 263:
            this.hexCursor = Math.max(0, this.hexCursor - 1);
         case 264:
         case 265:
         case 266:
         case 267:
         default:
            break;
         case 268:
            this.hexCursor = 0;
            break;
         case 269:
            this.hexCursor = this.hexText.length();
      }

      if (shift && this.hexSelectionAnchor == this.hexCursor) {
         this.clearHexSelection();
      }
   }

   private void selectAllHex() {
      this.hexSelectionAnchor = 0;
      this.hexCursor = this.hexText.length();
   }

   private void clearHexSelection() {
      this.hexSelectionAnchor = -1;
   }

   private boolean hasHexSelection() {
      return this.hexSelectionAnchor >= 0 && this.hexSelectionAnchor != this.hexCursor;
   }

   private int hexSelectionStart() {
      return this.hasHexSelection() ? Math.min(this.hexSelectionAnchor, this.hexCursor) : -1;
   }

   private int hexSelectionEnd() {
      return this.hasHexSelection() ? Math.max(this.hexSelectionAnchor, this.hexCursor) : -1;
   }

   private String selectedHexTextOrAll() {
      return !this.hasHexSelection() ? this.hexText : this.hexText.substring(this.hexSelectionStart(), this.hexSelectionEnd());
   }

   private void replaceSelectedHex(String value) {
      int start = this.hasHexSelection() ? this.hexSelectionStart() : this.hexCursor;
      int end = this.hasHexSelection() ? this.hexSelectionEnd() : this.hexCursor;
      String cleaned = cleanHex(value, 6 - (this.hexText.length() - (end - start)));
      this.hexText = (this.hexText.substring(0, start) + cleaned + this.hexText.substring(end)).toUpperCase(Locale.ROOT);
      this.hexCursor = start + cleaned.length();
      this.clearHexSelection();
      this.applyHexText();
   }

   private static String cleanHex(String value, int maxLength) {
      if (value != null && maxLength > 0) {
         String text = value.trim();
         if (text.startsWith("#")) {
            text = text.substring(1);
         }

         if (text.length() >= 8) {
            text = text.substring(text.length() - 6);
         }

         StringBuilder cleaned = new StringBuilder(Math.min(6, maxLength));

         for (int i = 0; i < text.length() && cleaned.length() < maxLength; i++) {
            char chr = text.charAt(i);
            if (isHex(chr)) {
               cleaned.append(Character.toUpperCase(chr));
            }
         }

         return cleaned.toString();
      } else {
         return "";
      }
   }

   private void normalizeHexCursor() {
      this.hexCursor = Math.max(0, Math.min(this.hexCursor, this.hexText == null ? 0 : this.hexText.length()));
      if (this.hexSelectionAnchor > (this.hexText == null ? 0 : this.hexText.length())) {
         this.hexSelectionAnchor = this.hexText.length();
      }
   }

   private void applyHexText() {
      this.normalizeHexCursor();
      if (this.hexText.length() == 6) {
         try {
            this.setDraftRgb(Integer.parseInt(this.hexText, 16) & 16777215);
            this.hexCursor = this.hexText.length();
            this.clearHexSelection();
         } catch (Exception var2) {
         }
      }
   }

   private void pasteHex(String clipboard, boolean replaceSelectionOnly) {
      String cleaned = cleanHex(clipboard, 6);
      if (cleaned.length() == 6) {
         if (replaceSelectionOnly && this.hasHexSelection()) {
            this.replaceSelectedHex(cleaned);
         } else {
            this.hexText = cleaned.toUpperCase(Locale.ROOT);
            this.hexCursor = this.hexText.length();
            this.clearHexSelection();
            this.applyHexText();
         }
      }
   }

   private void renderSaturationValue(UiContext context, UiBounds area) {
      ColorPicker.GradientTexture texture = saturationValueTexture(this.hue);
      texture.blit(context, area);
      UiRenderer.outline(context.graphics(), area, context.theme().colors().borderSoft);
   }

   private void renderHueStrip(UiContext context, UiBounds area) {
      hueTexture().blit(context, area);
      UiRenderer.outline(context.graphics(), area, context.theme().colors().borderSoft);
   }

   private void renderSlider(UiContext context, UiBounds bounds, String label, int value, int accent) {
      context.text().drawCentered(context.graphics(), label, UiBounds.of(bounds.x() - 15, bounds.y(), 12, bounds.height()), context.theme().colors().muted);
      UiRenderer.frame(context.graphics(), bounds, context.theme().colors().field, context.theme().colors().borderSoft);
      int fillW = Math.max(0, Math.round((bounds.width() - 2) * (value / 255.0F)));
      if (fillW > 0) {
         UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x() + 1, bounds.y() + 1, fillW, bounds.height() - 2), accent);
      }

      int knobX = bounds.x() + 1 + Math.round((bounds.width() - 3) * (value / 255.0F));
      UiRenderer.rect(context.graphics(), UiBounds.of(knobX, bounds.y() + 1, 2, bounds.height() - 2), -1);
      context.text()
         .drawFitted(context.graphics(), Integer.toString(value), bounds.right() + 5, context.text().centeredY(bounds), 24, context.theme().colors().text);
   }

   private void drawSwatch(UiContext context, UiBounds bounds, int color) {
      UiRenderer.frame(context.graphics(), bounds, 0xFF000000 | color & 16777215, context.theme().colors().borderSoft);
   }

   private void drawCrosshair(UiContext context, UiBounds area) {
      int x = area.x() + Math.round(this.saturation * (area.width() - 1));
      int y = area.y() + Math.round((1.0F - this.brightness) * (area.height() - 1));
      UiRenderer.rect(context.graphics(), UiBounds.of(x - 4, y, 9, 1), -1);
      UiRenderer.rect(context.graphics(), UiBounds.of(x, y - 4, 1, 9), -1);
      UiRenderer.rect(context.graphics(), UiBounds.of(x - 3, y, 7, 1), -16777216);
      UiRenderer.rect(context.graphics(), UiBounds.of(x, y - 3, 1, 7), -16777216);
   }

   private void updateSaturationValue(int mouseX, int mouseY) {
      UiBounds area = this.svBounds();
      this.saturation = clamp01((float)(mouseX - area.x()) / Math.max(1, area.width() - 1));
      this.brightness = clamp01(1.0F - (float)(mouseY - area.y()) / Math.max(1, area.height() - 1));
      this.updateDraftFromHsb();
   }

   private void updateHue(int mouseY) {
      UiBounds area = this.hueBounds();
      this.hue = clamp01(1.0F - (float)(mouseY - area.y()) / Math.max(1, area.height() - 1));
      this.updateDraftFromHsb();
   }

   private void updateChannel(int mouseX, UiBounds bounds, int channel) {
      int value = Math.round(clamp01((float)(mouseX - bounds.x()) / Math.max(1, bounds.width())) * 255.0F);
      int r = this.draftRed;
      int g = this.draftGreen;
      int b = this.draftBlue;
      if (channel == 0) {
         r = value;
      } else if (channel == 1) {
         g = value;
      } else {
         b = value;
      }

      this.setDraftRgb(r << 16 | g << 8 | b);
   }

   private void setDraftRgb(int rgb) {
      float[] hsb = Color.RGBtoHSB(rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF, null);
      this.hue = hsb[0];
      this.saturation = hsb[1];
      this.brightness = hsb[2];
      this.setDraftRgbFields(rgb);
   }

   private void updateDraftFromHsb() {
      this.setDraftRgbFields(Color.HSBtoRGB(this.hue, this.saturation, this.brightness) & 16777215);
   }

   private void setDraftRgbFields(int rgb) {
      this.draftRgb = rgb & 16777215;
      this.draftRed = this.draftRgb >>> 16 & 0xFF;
      this.draftGreen = this.draftRgb >>> 8 & 0xFF;
      this.draftBlue = this.draftRgb & 0xFF;
      this.draftArgb = (this.preservedAlpha & 0xFF) << 24 | this.draftRgb;
      this.syncHex();
   }

   private void syncHex() {
      this.hexText = String.format(Locale.ROOT, "%06X", this.draftRgb);
      this.hexCursor = this.hexText.length();
      this.clearHexSelection();
   }

   private int draftArgb() {
      return this.draftArgb;
   }

   private int red() {
      return this.draftRed;
   }

   private int green() {
      return this.draftGreen;
   }

   private int blue() {
      return this.draftBlue;
   }

   private UiBounds headerBounds() {
      return UiBounds.of(this.bounds.x(), this.bounds.y(), this.bounds.width(), 16);
   }

   private UiBounds svBounds() {
      return UiBounds.of(this.bounds.x() + 8, this.bounds.y() + 24, 102, 82);
   }

   private UiBounds hueBounds() {
      return UiBounds.of(this.bounds.x() + 116, this.bounds.y() + 24, 13, 82);
   }

   private UiBounds redBounds() {
      return UiBounds.of(this.bounds.x() + 24, this.bounds.y() + 114, 156, 13);
   }

   private UiBounds greenBounds() {
      return UiBounds.of(this.bounds.x() + 24, this.bounds.y() + 132, 156, 13);
   }

   private UiBounds blueBounds() {
      return UiBounds.of(this.bounds.x() + 24, this.bounds.y() + 150, 156, 13);
   }

   private UiBounds hexBounds() {
      return UiBounds.of(this.bounds.x() + 158, this.bounds.y() + 61, 70, 17);
   }

   private UiBounds saveBounds() {
      return UiBounds.of(this.bounds.x() + 8, this.bounds.bottom() - 24, 68, 17);
   }

   private UiBounds resetBounds() {
      return UiBounds.of(this.bounds.x() + 82, this.bounds.bottom() - 24, 68, 17);
   }

   private UiBounds cancelBounds() {
      return UiBounds.of(this.bounds.x() + 156, this.bounds.bottom() - 24, 80, 17);
   }

   private static boolean isHex(char chr) {
      return chr >= '0' && chr <= '9' || chr >= 'a' && chr <= 'f' || chr >= 'A' && chr <= 'F';
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static ColorPicker.GradientTexture hueTexture() {
      if (hueTexture == null) {
         hueTexture = ColorPicker.GradientTexture.create("color_picker_hue", 13, 82);
         hueTexture.writeHue();
      }

      return hueTexture;
   }

   private static ColorPicker.GradientTexture saturationValueTexture(float hue) {
      int bucket = Math.round(clamp01(hue) * 360.0F);
      if (saturationValueTexture == null) {
         saturationValueTexture = ColorPicker.GradientTexture.create("color_picker_sv", 102, 82);
         saturationValueHueBucket = Integer.MIN_VALUE;
      }

      if (saturationValueHueBucket != bucket) {
         saturationValueTexture.writeSaturationValue(bucket / 360.0F);
         saturationValueHueBucket = bucket;
      }

      return saturationValueTexture;
   }

   private static enum DragTarget {
      NONE,
      SV,
      HUE,
      RED,
      GREEN,
      BLUE,
      HEX;
   }

   private static final class GradientTexture extends AbstractTexture {
      private final Identifier id;
      private final NativeImage pixels;
      private final int width;
      private final int height;

      private static ColorPicker.GradientTexture create(String name, int width, int height) {
         Identifier id = Identifier.fromNamespaceAndPath("riptide", "dynamic/ui/" + name);
         ColorPicker.GradientTexture texture = new ColorPicker.GradientTexture(id, name, width, height);
         ColorPicker.MC.getTextureManager().register(id, texture);
         return texture;
      }

      private GradientTexture(Identifier id, String name, int width, int height) {
         this.id = id;
         this.width = width;
         this.height = height;
         this.pixels = new NativeImage(width, height, true);
         this.texture = RenderSystem.getDevice().createTexture("RIPTIDE " + name, 5, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
         this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
      }

      private void writeHue() {
         for (int y = 0; y < this.height; y++) {
            float h = this.height <= 1 ? 0.0F : 1.0F - (float)y / (this.height - 1);
            int color = 0xFF000000 | Color.HSBtoRGB(h, 1.0F, 1.0F) & 16777215;

            for (int x = 0; x < this.width; x++) {
               this.pixels.setPixel(x, y, color);
            }
         }

         this.upload();
      }

      private void writeSaturationValue(float hue) {
         for (int x = 0; x < this.width; x++) {
            float s = this.width <= 1 ? 0.0F : (float)x / (this.width - 1);

            for (int y = 0; y < this.height; y++) {
               float v = this.height <= 1 ? 0.0F : 1.0F - (float)y / (this.height - 1);
               this.pixels.setPixel(x, y, 0xFF000000 | Color.HSBtoRGB(hue, s, v) & 16777215);
            }
         }

         this.upload();
      }

      private void upload() {
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
      }

      private void blit(UiContext context, UiBounds area) {
         context.graphics()
            .blit(
               RenderPipelines.GUI_TEXTURED,
               this.id,
               area.x(),
               area.y(),
               0.0F,
               0.0F,
               area.width(),
               area.height(),
               this.width,
               this.height,
               this.width,
               this.height,
               -1
            );
      }

      public void close() {
         this.pixels.close();
         super.close();
      }
   }
}
