package riptide.modules;

import net.minecraft.client.Minecraft;

public final class RiptideJoinGrace {
   private static final long HARD_CAP_MS = 3000L;
   private static final int STABLE_GROUND_TICKS = 4;
   private static volatile long graceUntil;
   private static volatile boolean paused;
   private static int groundedTicks;

   private RiptideJoinGrace() {
   }

   public static void onJoin() {
      graceUntil = System.currentTimeMillis() + 3000L;
      groundedTicks = 0;
      paused = true;
   }

   public static void clear() {
      paused = false;
      graceUntil = 0L;
      groundedTicks = 0;
   }

   public static void tick() {
      if (paused) {
         Minecraft mc = Minecraft.getInstance();
         boolean grounded = mc.player != null && mc.player.onGround();
         groundedTicks = grounded ? groundedTicks + 1 : 0;
         if (System.currentTimeMillis() >= graceUntil || groundedTicks >= 4) {
            paused = false;
         }
      }
   }

   public static boolean isMovementPaused() {
      return paused;
   }
}
