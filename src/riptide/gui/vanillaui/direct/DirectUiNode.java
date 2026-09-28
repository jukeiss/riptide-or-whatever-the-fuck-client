package riptide.gui.vanillaui.direct;

public abstract class DirectUiNode {
   private static volatile long globalLayoutRevision = 1L;
   protected float x;
   protected float y;
   protected float width;
   protected float height;
   protected boolean visible = true;
   protected boolean enabled = true;
   protected boolean growX = false;
   protected float hoverProgress = 0.0F;

   public static long globalLayoutRevision() {
      return globalLayoutRevision;
   }

   protected void markLayoutDirty() {
      globalLayoutRevision++;
   }

   public float x() {
      return this.x;
   }

   public float y() {
      return this.y;
   }

   public float width() {
      return this.width;
   }

   public float height() {
      return this.height;
   }

   public DirectUiNode setBounds(float x, float y, float width, float height) {
      float nextWidth = Math.max(0.0F, width);
      float nextHeight = Math.max(0.0F, height);
      if (Float.compare(this.x, x) != 0
         || Float.compare(this.y, y) != 0
         || Float.compare(this.width, nextWidth) != 0
         || Float.compare(this.height, nextHeight) != 0) {
         this.x = x;
         this.y = y;
         this.width = nextWidth;
         this.height = nextHeight;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiNode setVisible(boolean visible) {
      if (this.visible != visible) {
         this.visible = visible;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiNode setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiNode setGrowX(boolean growX) {
      if (this.growX != growX) {
         this.growX = growX;
         this.markLayoutDirty();
      }

      return this;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean growX() {
      return this.growX;
   }

   public boolean contains(float px, float py) {
      return this.visible && px >= this.x && px <= this.x + this.width && py >= this.y && py <= this.y + this.height;
   }

   protected float animate(float current, float target, float speed, float delta) {
      float amount = Math.max(0.04F, delta * speed);
      if (current < target) {
         return Math.min(target, current + amount);
      } else {
         return current > target ? Math.max(target, current - amount) : current;
      }
   }

   protected float updateHover(boolean hovered, float delta) {
      this.hoverProgress = this.animate(this.hoverProgress, hovered ? 1.0F : 0.0F, 7.5F, delta);
      return this.hoverProgress;
   }

   public float preferredWidth(DirectRenderContext context) {
      return this.width;
   }

   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return this.height;
   }

   public void layout(DirectRenderContext context) {
   }

   public void render(DirectRenderContext context) {
   }

   public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
      return false;
   }

   public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
      return false;
   }

   public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
      return false;
   }

   public boolean mouseScrolled(DirectRenderContext context, float mouseX, float mouseY, float amount) {
      return false;
   }

   public boolean keyPressed(DirectRenderContext context, int keyCode, int scanCode, int modifiers) {
      return false;
   }

   public boolean charTyped(DirectRenderContext context, char chr, int modifiers) {
      return false;
   }
}
