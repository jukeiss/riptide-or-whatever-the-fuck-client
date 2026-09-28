package riptide.gui.vanillaui.components;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class ToastStack {
   private static final long DEFAULT_LIFETIME_MS = 1800L;
   private static final float DEFAULT_ENTER_MS = 140.0F;
   private static final float DEFAULT_EXIT_MS = 220.0F;
   private static final int DEFAULT_MAX_VISIBLE = 4;
   private static final int DEFAULT_GAP = 4;
   private static final int DEFAULT_HEIGHT = 18;
   private static final int LINE_HEIGHT = 10;
   private final List<ToastStack.ToastEntry> toasts = new CopyOnWriteArrayList<>();
   private volatile boolean maybeVisible;
   private final long lifetimeMs;
   private final float enterMs;
   private final float exitMs;
   private final int maxVisible;
   private final int gap;
   private final int height;

   public ToastStack() {
      this(1800L, 140.0F, 220.0F, 4, 4, 18);
   }

   public ToastStack(long lifetimeMs, float enterMs, float exitMs, int maxVisible, int gap, int height) {
      this.lifetimeMs = Math.max(1L, lifetimeMs);
      this.enterMs = Math.max(1.0F, enterMs);
      this.exitMs = Math.max(1.0F, exitMs);
      this.maxVisible = Math.max(1, maxVisible);
      this.gap = Math.max(0, gap);
      this.height = Math.max(1, height);
   }

   public void show(String message, int accentColor) {
      if (message != null && !message.isBlank()) {
         long nowNanos = System.nanoTime();
         this.prune(nowNanos);
         if (this.toasts.size() >= this.maxVisible) {
            this.toasts.remove(0);
         }

         this.toasts.add(new ToastStack.ToastEntry(message, nowNanos, accentColor));
         this.maybeVisible = true;
      }
   }

   public boolean hasVisibleToasts() {
      if (!this.maybeVisible) {
         return false;
      } else {
         this.prune(System.nanoTime());
         if (this.toasts.isEmpty()) {
            this.maybeVisible = false;
            return false;
         } else {
            return true;
         }
      }
   }

   public int size() {
      if (!this.maybeVisible) {
         return 0;
      } else {
         this.prune(System.nanoTime());
         if (this.toasts.isEmpty()) {
            this.maybeVisible = false;
         }

         return this.toasts.size();
      }
   }

   public void clear() {
      this.toasts.clear();
      this.maybeVisible = false;
   }

   public void render(UiContext context, int anchorX, int anchorY, int anchorWidth) {
      if (context != null && anchorWidth > 0) {
         long nowNanos = System.nanoTime();
         this.prune(nowNanos);
         if (this.toasts.isEmpty()) {
            this.maybeVisible = false;
         } else {
            int maxToastWidth = Math.max(1, Math.min(anchorWidth, 260));
            int padX = 9;
            int lineHeight = 10;
            int verticalPad = Math.max(4, this.height - lineHeight);
            int maxTextWidth = Math.max(1, maxToastWidth - padX * 2);
            int y = anchorY;

            for (int i = this.toasts.size() - 1; i >= 0; i--) {
               ToastStack.ToastEntry toast = this.toasts.get(i);
               float ageMs = Math.max(0.0F, (float)(nowNanos - toast.shownAtNanos()) / 1000000.0F);
               float alpha = Math.min(clamp01(ageMs / this.enterMs), clamp01(((float)this.lifetimeMs - ageMs) / this.exitMs));
               if (!(alpha <= 0.001F)) {
                  List<String> lines = context.text().wrapFully(toast.message(), maxTextWidth);
                  int widest = 0;

                  for (String line : lines) {
                     widest = Math.max(widest, context.text().width(line));
                  }

                  int toastWidth = Math.min(maxToastWidth, Math.max(Math.min(48, maxToastWidth), widest + padX * 2));
                  int textBlockHeight = lines.size() * lineHeight;
                  int toastHeight = Math.max(this.height, textBlockHeight + verticalPad);
                  int drawX = anchorX + Math.max(0, (anchorWidth - toastWidth) / 2);
                  float slide = clamp01(ageMs / this.enterMs);
                  float eased = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
                  int drawY = y - Math.round((1.0F - eased) * (toastHeight + this.gap + 2));
                  UiBounds bounds = UiBounds.of(drawX, drawY, toastWidth, toastHeight);
                  int fill = tintToward(1183764, toast.accentColor(), 0.22F);
                  UiRenderer.roundFrame(
                     context.graphics(), bounds, 5, UiRenderer.applyAlpha(-704643072 | fill, alpha), UiRenderer.applyAlpha(toast.accentColor(), alpha)
                  );
                  int textColor = UiRenderer.applyAlpha(-723724, alpha);
                  int textTop = drawY + Math.max(2, (toastHeight - textBlockHeight + 1) / 2 + 1);

                  for (int li = 0; li < lines.size(); li++) {
                     String line = lines.get(li);
                     int lineX = drawX + Math.max(padX, (toastWidth - context.text().width(line)) / 2);
                     context.text().draw(context.graphics(), line, lineX, textTop + li * lineHeight, textColor);
                  }

                  y += toastHeight + this.gap;
               }
            }
         }
      }
   }

   private void prune(long nowNanos) {
      this.toasts.removeIf(toast -> (nowNanos - toast.shownAtNanos()) / 1000000L >= this.lifetimeMs);
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static int tintToward(int baseRgb, int accentColor, float t) {
      int r = baseRgb >> 16 & 0xFF;
      int g = baseRgb >> 8 & 0xFF;
      int b = baseRgb & 0xFF;
      int ar = accentColor >> 16 & 0xFF;
      int ag = accentColor >> 8 & 0xFF;
      int ab = accentColor & 0xFF;
      r += Math.round((ar - r) * t);
      g += Math.round((ag - g) * t);
      b += Math.round((ab - b) * t);
      return r << 16 | g << 8 | b;
   }

   private record ToastEntry(String message, long shownAtNanos, int accentColor) {
   }
}
