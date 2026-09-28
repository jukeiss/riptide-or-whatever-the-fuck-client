package riptide.modules;

public final class ViewmodelState {
   private static volatile boolean on;
   static volatile boolean mainHandOn;
   static volatile float mainHandScale;
   static volatile float mainHandX;
   static volatile float mainHandY;
   static volatile float mainHandRotX;
   static volatile float mainHandRotY;
   static volatile float mainHandRotZ;
   static volatile boolean offHandOn;
   static volatile float offHandScale;
   static volatile float offHandX;
   static volatile float offHandY;
   static volatile float offHandRotX;
   static volatile float offHandRotY;
   static volatile float offHandRotZ;
   static volatile int swingDuration = 6;
   static volatile int blockAnim;
   static volatile float oneSevenY = 0.1F;
   static volatile float oneSevenSwingScale = 0.9F;
   static volatile boolean equipOffsetOn = true;
   static volatile boolean ignoreBlocking = true;
   static volatile boolean ignorePlace = true;
   static volatile boolean ignoreAmount;
   static volatile boolean airWalker;

   private ViewmodelState() {
   }

   static void enable() {
      on = true;
   }

   static void disable() {
      on = false;
   }

   public static boolean active() {
      return on;
   }

   public static boolean mainHandOn() {
      return on && mainHandOn;
   }

   public static float mainHandScale() {
      return mainHandScale;
   }

   public static float mainHandX() {
      return mainHandX;
   }

   public static float mainHandY() {
      return mainHandY;
   }

   public static float mainHandRotX() {
      return mainHandRotX;
   }

   public static float mainHandRotY() {
      return mainHandRotY;
   }

   public static float mainHandRotZ() {
      return mainHandRotZ;
   }

   public static boolean offHandOn() {
      return on && offHandOn;
   }

   public static float offHandScale() {
      return offHandScale;
   }

   public static float offHandX() {
      return offHandX;
   }

   public static float offHandY() {
      return offHandY;
   }

   public static float offHandRotX() {
      return offHandRotX;
   }

   public static float offHandRotY() {
      return offHandRotY;
   }

   public static float offHandRotZ() {
      return offHandRotZ;
   }

   public static int swingDuration() {
      return swingDuration;
   }

   public static int blockAnim() {
      return blockAnim;
   }

   public static float oneSevenY() {
      return oneSevenY;
   }

   public static float oneSevenSwingScale() {
      return oneSevenSwingScale;
   }

   public static boolean equipOffsetOn() {
      return on && equipOffsetOn;
   }

   public static boolean ignoreBlocking() {
      return ignoreBlocking;
   }

   public static boolean ignorePlace() {
      return ignorePlace;
   }

   public static boolean ignoreAmount() {
      return ignoreAmount;
   }

   public static boolean airWalker() {
      return on && airWalker;
   }
}
