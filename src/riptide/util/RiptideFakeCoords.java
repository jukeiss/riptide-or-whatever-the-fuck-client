package riptide.util;

import java.security.SecureRandom;
import riptide.modules.PackHideState;

public final class RiptideFakeCoords {
   private static final SecureRandom RNG = new SecureRandom();
   private static final long REGION = 1024L;
   private static final long SPAN = 20000000L;
   private static volatile boolean enabled;
   private static volatile RiptideFakeCoords.Mode mode = RiptideFakeCoords.Mode.OFFSET;
   private static volatile boolean fakeY = false;
   private static double offX;
   private static double offY;
   private static double offZ;
   private static double sclX;
   private static double sclZ;
   private static double pivX;
   private static double pivZ;
   private static double rotAngle;
   private static double rotPivX;
   private static double rotPivZ;
   private static long scrambleKey;
   private static double frozenX;
   private static double frozenY;
   private static double frozenZ;
   private static volatile double customX;
   private static volatile double customY;
   private static volatile double customZ;
   private static double anchorX;
   private static double anchorY;
   private static double anchorZ;
   private static volatile double custOffX;
   private static volatile double custOffY;
   private static volatile double custOffZ;

   private RiptideFakeCoords() {
   }

   public static boolean active() {
      return enabled && !PackHideState.isActive();
   }

   public static void enable(RiptideFakeCoords.Mode m, boolean spoofY) {
      mode = m == null ? RiptideFakeCoords.Mode.OFFSET : m;
      fakeY = spoofY;
      regenerate();
      enabled = true;
   }

   public static void disable() {
      enabled = false;
   }

   public static void setMode(RiptideFakeCoords.Mode m) {
      if (m != null) {
         mode = m;
      }
   }

   public static void setFakeY(boolean spoofY) {
      fakeY = spoofY;
   }

   public static void regenerate() {
      offX = randSpan();
      offY = randRange(-120, 200);
      offZ = randSpan();
      sclX = randScale();
      sclZ = randScale();
      pivX = randSpan();
      pivZ = randSpan();
      rotAngle = RNG.nextDouble() * Math.PI * 2.0;
      rotPivX = randSpan();
      rotPivZ = randSpan();
      scrambleKey = RNG.nextLong();
      frozenX = randSpan();
      frozenY = randRange(-50, 320);
      frozenZ = randSpan();
   }

   public static void setCustom(double cx, double cy, double cz) {
      customX = cx;
      customY = cy;
      customZ = cz;
      custOffX = customX - anchorX;
      custOffY = customY - anchorY;
      custOffZ = customZ - anchorZ;
   }

   public static void anchorTo(double realX, double realY, double realZ) {
      anchorX = realX;
      anchorY = realY;
      anchorZ = realZ;
      custOffX = customX - anchorX;
      custOffY = customY - anchorY;
      custOffZ = customZ - anchorZ;
   }

   public static double[] apply(double x, double y, double z) {
      if (!enabled) {
         return new double[]{x, y, z};
      } else {
         double fx;
         double fy;
         double fz;
         switch (mode) {
            case SCALED:
               fx = (x - pivX) * sclX + offX;
               fy = y + offY;
               fz = (z - pivZ) * sclZ + offZ;
               break;
            case ROTATED:
               double dx = x - rotPivX;
               double dz = z - rotPivZ;
               double c = Math.cos(rotAngle);
               double s = Math.sin(rotAngle);
               fx = dx * c - dz * s + offX;
               fz = dx * s + dz * c + offZ;
               fy = y + offY;
               break;
            case SCRAMBLED:
               long rx = Math.floorDiv((long)Math.floor(x), 1024L);
               long ry = Math.floorDiv((long)Math.floor(y), 1024L);
               long rz = Math.floorDiv((long)Math.floor(z), 1024L);
               fx = x + regionOffset(scrambleKey, rx, rz, 0);
               fz = z + regionOffset(scrambleKey, rx, rz, 1);
               fy = y + regionOffset(scrambleKey ^ -7046029254386353131L, ry, 0L, 2);
               break;
            case FROZEN:
               fx = frozenX;
               fy = frozenY;
               fz = frozenZ;
               break;
            case CUSTOM:
               fx = x + custOffX;
               fy = y + custOffY;
               fz = z + custOffZ;
               break;
            default:
               fx = x + offX;
               fy = y + offY;
               fz = z + offZ;
         }

         if (!fakeY && mode != RiptideFakeCoords.Mode.FROZEN) {
            fy = y;
         }

         return new double[]{fx, fy, fz};
      }
   }

   private static double regionOffset(long key, long a, long b, int axis) {
      long h = key + -7046029254386353131L * (a + 1L);
      h ^= h >>> 29;
      h = h * -4658895280553007687L + b + 1L;
      h ^= h >>> 27;
      h = h * -7723592293110705685L + (axis + 1);
      h ^= h >>> 31;
      return Math.floorMod(h, 20000000L) - 1.0E7;
   }

   private static double randSpan() {
      return RNG.nextDouble() * 2.0E7 - 1.0E7;
   }

   private static double randRange(int lo, int hi) {
      return lo + RNG.nextInt(hi - lo);
   }

   private static double randScale() {
      double s = 0.5 + RNG.nextDouble() * 1.5;
      return RNG.nextBoolean() ? s : -s;
   }

   public static enum Mode {
      OFFSET,
      SCALED,
      ROTATED,
      SCRAMBLED,
      FROZEN,
      CUSTOM;
   }
}
