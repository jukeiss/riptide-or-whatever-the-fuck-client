package riptide.util;

import java.util.concurrent.atomic.AtomicLong;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;

public final class RiptideRuntimeActivity {
   public static final long MODULE_TICK = 1L;
   public static final long LEVEL_RENDER = 2L;
   public static final long PRE_MOVEMENT = 4L;
   public static final long MOVEMENT = 8L;
   public static final long MOUSE_ROTATION = 16L;
   public static final long SOUND = 32L;
   public static final long HUD_MODULE = 64L;
   public static final long NAMETAGS = 128L;
   public static final long OVERLAY = 256L;
   public static final long MULTI = 512L;
   public static final long PACKET_CAPTURE = 1024L;
   public static final long HUD_AUX = 2048L;
   private static final AtomicLong EXTERNAL_BITS = new AtomicLong();
   private static volatile RiptideRuntimeActivity.Snapshot published = new RiptideRuntimeActivity.Snapshot(Integer.MIN_VALUE, 0L);

   private RiptideRuntimeActivity() {
   }

   public static RiptideRuntimeActivity.Snapshot current() {
      return published;
   }

   public static boolean has(long mask) {
      return (published.bits & mask) != 0L;
   }

   public static void publish(long bit, boolean active) {
      if ((bit & externalMask()) == 0L) {
         throw new IllegalArgumentException("Not an external activity bit: " + bit);
      } else {
         long current;
         long next;
         do {
            current = EXTERNAL_BITS.get();
            next = active ? current | bit : current & ~bit;
            if (next == current) {
               return;
            }
         } while (!EXTERNAL_BITS.compareAndSet(current, next));

         refresh(ModuleRegistry.revision(), next);
      }
   }

   public static void publishModuleRevision(int revision) {
      refresh(revision, EXTERNAL_BITS.get());
   }

   private static synchronized RiptideRuntimeActivity.Snapshot refresh(int revision, long external) {
      RiptideRuntimeActivity.Snapshot current = published;
      if (current.moduleRevision == revision && (current.bits & externalMask()) == external) {
         return current;
      } else {
         long bits = external;
         if (ModuleRegistry.hasTickWork()) {
            bits = external | 1L;
         }

         if (ModuleRegistry.hasRenderLevelHooks()) {
            bits |= 2L;
         }

         if (ModuleRegistry.hasPreMovementHooks()) {
            bits |= 4L;
         }

         if (ModuleRegistry.hasMovementHooks()) {
            bits |= 8L;
         }

         if (ModuleRegistry.hasMouseRotationHooks()) {
            bits |= 16L;
         }

         if (ModuleRegistry.hasSoundHooks()) {
            bits |= 32L;
         }

         Module hud = ModuleRegistry.get("hud");
         if (hud != null && hud.isEnabled()) {
            bits |= 64L;
         }

         Module nametags = ModuleRegistry.get("nametags");
         if (nametags != null && nametags.isEnabled()) {
            bits |= 128L;
         }

         RiptideRuntimeActivity.Snapshot next = new RiptideRuntimeActivity.Snapshot(revision, bits);
         published = next;
         return next;
      }
   }

   private static long externalMask() {
      return 3840L;
   }

   public record Snapshot(int moduleRevision, long bits) {
      public boolean has(long mask) {
         return (this.bits & mask) != 0L;
      }
   }
}
