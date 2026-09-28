package riptide.gui.vanillaui.components;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;

public final class AnimatedToggleButton {
   private static final int SLOW_WIDTH = 40;
   private static final int FAST_WIDTH = 96;
   private static final long SLOW_ANIMATION_NANOS = 205000000L;
   private static final long FAST_ANIMATION_NANOS = 128000000L;
   private static final long FADE_NANOS = 200000000L;
   private static final long STALE_NANOS = 30000000000L;
   private static final Map<String, AnimatedToggleButton.State> STATES = new HashMap<>();
   private static long lastPruneNanos;

   private AnimatedToggleButton() {
   }

   public static void render(UiContext context, UiBounds bounds, String label, boolean enabled, boolean hovered, String animationKey) {
      long now = System.nanoTime();
      AnimatedToggleButton.State state = STATES.get(animationKey);
      if (state == null) {
         state = new AnimatedToggleButton.State(enabled ? 1.0F : 0.0F, enabled, now);
         STATES.put(animationKey, state);
      } else if (state.targetEnabled != enabled) {
         state.startProgress = state.progress(now, 200000000L);
         state.targetEnabled = enabled;
         state.changedNanos = now;
      }

      state.lastSeenNanos = now;
      renderStateButton(context, bounds, label, hovered, state.progress(now, 200000000L));
      pruneIfNeeded(now);
   }

   private static void renderStateButton(UiContext context, UiBounds bounds, String label, boolean hovered, float progress) {
      UiColors colors = context.theme().colors();
      float p = clamp01(progress);
      UiRenderer.frame(context.graphics(), bounds, -1071766483, colors.buttonBorder);
      if (p > 0.001F) {
         UiRenderer.rect(context.graphics(), bounds.inset(1), UiRenderer.applyAlpha(RiptideTheme.recolor(-736144845, RiptideTheme.Channel.SUCCESS), p));
         UiRenderer.outline(context.graphics(), bounds, UiRenderer.applyAlpha(colors.success, p));
      }

      float hoverT = HoverFades.get(HoverFades.key(bounds), hovered);
      if (hoverT > 0.001F) {
         UiRenderer.rect(context.graphics(), bounds.inset(1), Math.round(20.0F * hoverT) << 24 | 16777215);
      }

      if (label != null && !label.isEmpty()) {
         context.text().drawCentered(context.graphics(), label, bounds, colors.text);
      }
   }

   public static long durationNanos(int width) {
      float t = clamp01((float)(width - 40) / Math.max(1, 56));
      return Math.round(2.05E8F + -7.7E7F * t);
   }

   private static void pruneIfNeeded(long now) {
      if (now - lastPruneNanos >= 30000000000L) {
         lastPruneNanos = now;
         Iterator<AnimatedToggleButton.State> iterator = STATES.values().iterator();

         while (iterator.hasNext()) {
            if (now - iterator.next().lastSeenNanos > 30000000000L) {
               iterator.remove();
            }
         }
      }
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static final class State {
      private float startProgress;
      private boolean targetEnabled;
      private long changedNanos;
      private long lastSeenNanos;

      private State(float startProgress, boolean targetEnabled, long now) {
         this.startProgress = startProgress;
         this.targetEnabled = targetEnabled;
         this.changedNanos = now;
         this.lastSeenNanos = now;
      }

      private float progress(long now, long durationNanos) {
         float elapsed = Math.min(1.0F, (float)(now - this.changedNanos) / (float)Math.max(1L, durationNanos));
         float eased = elapsed * elapsed * (3.0F - 2.0F * elapsed);
         float target = this.targetEnabled ? 1.0F : 0.0F;
         return this.startProgress + (target - this.startProgress) * eased;
      }
   }
}
