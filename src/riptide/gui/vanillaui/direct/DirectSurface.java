package riptide.gui.vanillaui.direct;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.FocusableTextInput;

public final class DirectSurface {
   private final CompactTheme theme;
   private final DirectUiNode root;
   private final float density;
   private long lastLayoutRevision = Long.MIN_VALUE;
   private float lastLayoutViewportWidth = Float.NaN;
   private float lastLayoutViewportHeight = Float.NaN;
   private float lastRootX = Float.NaN;
   private float lastRootY = Float.NaN;
   private float lastRootWidth = Float.NaN;
   private float lastRootHeight = Float.NaN;

   public DirectSurface(CompactTheme theme, DirectUiNode root) {
      this(theme, root, 2.0F);
   }

   public DirectSurface(CompactTheme theme, DirectUiNode root, float density) {
      this.theme = theme == null ? new CompactTheme() : theme;
      this.root = root;
      this.density = density <= 0.0F ? 2.0F : density;
   }

   public DirectViewport viewport() {
      return DirectViewport.current(this.density);
   }

   public void render(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float delta) {
      if (this.root != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            viewport.push(drawContext);

            try {
               DirectRenderContext context = new DirectRenderContext(
                  drawContext, mc.font, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta
               );
               this.layoutIfNeeded(context, viewport);
               this.root.render(context);
            } finally {
               viewport.pop(drawContext);
            }
         }
      }
   }

   public void invalidateLayout() {
      this.lastLayoutRevision = Long.MIN_VALUE;
   }

   private void layoutIfNeeded(DirectRenderContext context, DirectViewport viewport) {
      long revision = DirectUiNode.globalLayoutRevision();
      boolean viewportChanged = Float.compare(this.lastLayoutViewportWidth, viewport.uiWidth()) != 0
         || Float.compare(this.lastLayoutViewportHeight, viewport.uiHeight()) != 0;
      boolean rootBoundsChanged = Float.compare(this.lastRootX, this.root.x()) != 0
         || Float.compare(this.lastRootY, this.root.y()) != 0
         || Float.compare(this.lastRootWidth, this.root.width()) != 0
         || Float.compare(this.lastRootHeight, this.root.height()) != 0;
      if (viewportChanged || rootBoundsChanged || revision != this.lastLayoutRevision) {
         this.root.layout(context);
         this.lastLayoutRevision = DirectUiNode.globalLayoutRevision();
         this.lastLayoutViewportWidth = viewport.uiWidth();
         this.lastLayoutViewportHeight = viewport.uiHeight();
         this.lastRootX = this.root.x();
         this.lastRootY = this.root.y();
         this.lastRootWidth = this.root.width();
         this.lastRootHeight = this.root.height();
      }
   }

   public DirectRenderContext measurementContext() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.font != null) {
         DirectViewport viewport = this.viewport();
         return new DirectRenderContext(null, mc.font, viewport, this.theme, 0.0F, 0.0F, 0.0F);
      } else {
         return null;
      }
   }

   public float measurePreferredHeight(float availableWidth) {
      if (this.root == null) {
         return 0.0F;
      } else {
         DirectRenderContext context = this.measurementContext();
         return context == null ? 0.0F : this.root.preferredHeight(context, availableWidth);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
            return this.root.mouseClicked(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), button);
         } else {
            return false;
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
            return this.root.mouseReleased(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), button);
         } else {
            return false;
         }
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
            return this.root
               .mouseDragged(
                  context,
                  viewport.toUiX(mouseX),
                  viewport.toUiY(mouseY),
                  button,
                  (float)(deltaX / viewport.drawScaleX()),
                  (float)(deltaY / viewport.drawScaleY())
               );
         } else {
            return false;
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
            return this.root.mouseScrolled(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), (float)amount);
         } else {
            return false;
         }
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, 0.0F, 0.0F, 0.0F);
            return this.root.keyPressed(context, keyCode, scanCode, modifiers);
         } else {
            return false;
         }
      }
   }

   public boolean charTyped(char chr, int modifiers) {
      if (this.root == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            DirectViewport viewport = this.viewport();
            DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, this.theme, 0.0F, 0.0F, 0.0F);
            return this.root.charTyped(context, chr, modifiers);
         } else {
            return false;
         }
      }
   }

   public boolean hasFocusedTextInput() {
      return this.hasFocusedTextInput(this.root);
   }

   public void clearFocusedTextInputs() {
      this.clearFocusedTextInputs(this.root);
   }

   private boolean hasFocusedTextInput(DirectUiNode node) {
      if (node != null && node.isVisible()) {
         if (node instanceof FocusableTextInput input && input.isFocused()) {
            return true;
         } else {
            if (node instanceof DirectUiContainer container) {
               for (DirectUiNode child : container.children()) {
                  if (this.hasFocusedTextInput(child)) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private void clearFocusedTextInputs(DirectUiNode node) {
      if (node != null && node.isVisible()) {
         if (node instanceof FocusableTextInput input) {
            input.setFocused(false);
         }

         if (node instanceof DirectUiContainer container) {
            for (DirectUiNode child : container.children()) {
               this.clearFocusedTextInputs(child);
            }
         }
      }
   }
}
