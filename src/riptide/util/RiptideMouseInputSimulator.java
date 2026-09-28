package riptide.util;

import java.util.EnumMap;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import riptide.modules.PackHideState;

public final class RiptideMouseInputSimulator {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final double AIM_DISTRIBUTION_SECONDS = 0.05;
   private static final EnumMap<RiptideMouseInputSimulator.Source, RawMouseAccumulator> ACCUMULATORS = new EnumMap<>(RiptideMouseInputSimulator.Source.class);
   private static final SmoothedMouseAccumulator AIM_ACCUMULATOR = new SmoothedMouseAccumulator();
   private static volatile RiptideMouseInputSimulator.Source exclusiveSource;
   private static long lastConsumeNanos;

   private RiptideMouseInputSimulator() {
   }

   public static void queueRotation(RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation target) {
      queueRotation(RiptideMouseInputSimulator.Source.GENERIC, current, target);
   }

   public static void queueRotation(RiptideMouseInputSimulator.Source source, RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation target) {
      if (current != null && target != null) {
         float yawDelta = RiptideRotationUtil.angleDifference(target.yaw(), current.yaw());
         float pitchDelta = Mth.clamp(target.pitch(), -90.0F, 90.0F) - Mth.clamp(current.pitch(), -90.0F, 90.0F);
         queueRotationDelta(source, yawDelta, pitchDelta);
      }
   }

   public static void queueRotationDelta(float yawDelta, float pitchDelta) {
      queueRotationDelta(RiptideMouseInputSimulator.Source.GENERIC, yawDelta, pitchDelta);
   }

   public static void queueRotationDelta(RiptideMouseInputSimulator.Source source, float yawDelta, float pitchDelta) {
      if (!canUseMouseLook()) {
         clear(source);
      } else {
         double degreesPerRawInput = RiptideRotationUtil.mouseDegreesPerRawInput();
         if (!(degreesPerRawInput <= 1.0E-8) && Double.isFinite(degreesPerRawInput)) {
            double rawX = yawDelta / degreesPerRawInput;
            double rawY = pitchDelta / degreesPerRawInput;
            if ((Boolean)MC.options.invertMouseX().get()) {
               rawX = -rawX;
            }

            if ((Boolean)MC.options.invertMouseY().get()) {
               rawY = -rawY;
            }

            queueRawDelta(source, rawX, rawY);
         }
      }
   }

   public static void queueRotationCounts(RiptideMouseInputSimulator.Source source, long yawCounts, long pitchCounts) {
      if (!canUseMouseLook()) {
         clear(source);
      } else {
         double rawX = yawCounts;
         double rawY = pitchCounts;
         if ((Boolean)MC.options.invertMouseX().get()) {
            rawX = -rawX;
         }

         if ((Boolean)MC.options.invertMouseY().get()) {
            rawY = -rawY;
         }

         queueRawDelta(source, rawX, rawY);
      }
   }

   public static void queueRawDelta(double deltaX, double deltaY) {
      queueRawDelta(RiptideMouseInputSimulator.Source.GENERIC, deltaX, deltaY);
   }

   public static void queueRawDelta(RiptideMouseInputSimulator.Source source, double deltaX, double deltaY) {
      if (!canUseMouseLook()) {
         clear(source);
      } else if (Double.isFinite(deltaX) && Double.isFinite(deltaY)) {
         if (source == RiptideMouseInputSimulator.Source.AIM_ASSIST) {
            AIM_ACCUMULATOR.replace(deltaX, deltaY, 0.05);
         } else if (source == RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY) {
            accumulator(source).replaceQueued(deltaX, deltaY);
         } else if (!(Math.abs(deltaX) < 1.0E-7) || !(Math.abs(deltaY) < 1.0E-7)) {
            accumulator(source).queue(deltaX, deltaY);
         }
      }
   }

   public static RiptideMouseInputSimulator.Delta consume() {
      if (!canUseMouseLook()) {
         clear();
         return new RiptideMouseInputSimulator.Delta(0.0, 0.0);
      } else {
         long now = System.nanoTime();
         double elapsedSeconds = lastConsumeNanos == 0L ? 0.016666666666666666 : Math.clamp((now - lastConsumeNanos) / 1.0E9, 0.001, 0.05);
         lastConsumeNanos = now;
         RiptideMouseInputSimulator.Source exclusive = exclusiveSource;
         if (exclusive != null) {
            clearExcept(exclusive);
            RawMouseAccumulator.Counts counts = consumeSource(exclusive, elapsedSeconds);
            return new RiptideMouseInputSimulator.Delta(counts.x(), counts.y());
         } else {
            long x = 0L;
            long y = 0L;

            for (Entry<RiptideMouseInputSimulator.Source, RawMouseAccumulator> entry : ACCUMULATORS.entrySet()) {
               if (entry.getKey() != RiptideMouseInputSimulator.Source.AIM_ASSIST && entry.getKey() != RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY) {
                  RawMouseAccumulator accumulator = entry.getValue();
                  RawMouseAccumulator.Counts counts = accumulator.consume();
                  x += counts.x();
                  y += counts.y();
               }
            }

            RawMouseAccumulator.Counts aim = AIM_ACCUMULATOR.consume(elapsedSeconds);
            x += aim.x();
            y += aim.y();
            RawMouseAccumulator.Counts telly = accumulator(RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY).consume();
            x += telly.x();
            y += telly.y();
            return new RiptideMouseInputSimulator.Delta(x, y);
         }
      }
   }

   public static void clearIfUnavailable() {
      if (!canUseMouseLook()) {
         clear();
      }
   }

   public static void clear() {
      for (RawMouseAccumulator accumulator : ACCUMULATORS.values()) {
         accumulator.clear();
      }

      AIM_ACCUMULATOR.clear();
      exclusiveSource = null;
      lastConsumeNanos = 0L;
   }

   public static void clear(RiptideMouseInputSimulator.Source source) {
      if (source == RiptideMouseInputSimulator.Source.AIM_ASSIST) {
         AIM_ACCUMULATOR.clear();
         accumulator(source).clear();
      } else if (source == RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY) {
         accumulator(source).clear();
      } else {
         accumulator(source).clear();
      }
   }

   public static void setExclusive(RiptideMouseInputSimulator.Source source, boolean exclusive) {
      if (exclusive) {
         if (source != null) {
            exclusiveSource = source;
            clearExcept(source);
         }
      } else {
         if (source == null || exclusiveSource == source) {
            exclusiveSource = null;
            if (source != null) {
               clear(source);
            }
         }
      }
   }

   public static boolean hasExclusiveInput() {
      return exclusiveSource != null;
   }

   public static boolean hasExclusiveInput(RiptideMouseInputSimulator.Source source) {
      return source != null && exclusiveSource == source;
   }

   public static boolean canUseMouseLook() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.options != null
         && MC.getWindow() != null
         && MC.mouseHandler != null
         && MC.mouseHandler.isMouseGrabbed()
         && MC.gui.screen() == null
         && MC.gui.overlay() == null
         && !PackHideState.isActive();
   }

   private static RawMouseAccumulator accumulator(RiptideMouseInputSimulator.Source source) {
      return ACCUMULATORS.get(source == null ? RiptideMouseInputSimulator.Source.GENERIC : source);
   }

   private static RawMouseAccumulator.Counts consumeSource(RiptideMouseInputSimulator.Source source, double elapsedSeconds) {
      if (source == RiptideMouseInputSimulator.Source.AIM_ASSIST) {
         return AIM_ACCUMULATOR.consume(elapsedSeconds);
      } else {
         return source == RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY ? accumulator(source).consume() : accumulator(source).consume();
      }
   }

   private static void clearExcept(RiptideMouseInputSimulator.Source retained) {
      for (Entry<RiptideMouseInputSimulator.Source, RawMouseAccumulator> entry : ACCUMULATORS.entrySet()) {
         if (entry.getKey() != retained) {
            entry.getValue().clear();
         }
      }

      if (retained != RiptideMouseInputSimulator.Source.AIM_ASSIST) {
         AIM_ACCUMULATOR.clear();
      }
   }

   static {
      for (RiptideMouseInputSimulator.Source source : RiptideMouseInputSimulator.Source.values()) {
         ACCUMULATORS.put(source, new RawMouseAccumulator());
      }
   }

   public record Delta(double x, double y) {
      public boolean isZero() {
         return Math.abs(this.x) < 1.0E-7 && Math.abs(this.y) < 1.0E-7;
      }
   }

   public static enum Source {
      GENERIC,
      AIM_ASSIST,
      AUTO_FISH,
      SCAFFOLD_TELLY;
   }
}
