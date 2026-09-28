package riptide.util;

import net.minecraft.client.player.LocalPlayer;

public final class RiptideKillAuraRotation {
   public static final float TURN_SPEED = 72.0F;
   public static final float WIND_DOWN_MAX_YAW_STEP = 37.0F;
   public static final float WIND_DOWN_MAX_PITCH_STEP = 37.0F;
   public static final float RESET_THRESHOLD = 2.0F;
   public static final int TICKS_UNTIL_RESET = 5;
   public static final int PRIORITY_BED_DEFENDER = 30;
   public static final int PRIORITY_SURROUND = 25;
   public static final int PRIORITY_ANCHOR_AURA = 20;
   public static final int PRIORITY_CRYSTAL_AURA = 18;
   public static final int PRIORITY_AUTO_TRAP = 15;
   public static final int PRIORITY_KILL_AURA = 10;
   public static final int PRIORITY_AUTO_FARM = 5;
   public static final String OWNER_BED_DEFENDER = "bed-defender";
   public static final String OWNER_SURROUND = "surround";
   public static final String OWNER_CRYSTAL_AURA = "crystal-aura";
   public static final String OWNER_ANCHOR_AURA = "anchor-aura";
   public static final String OWNER_AUTO_TRAP = "auto-trap";
   public static final String OWNER_KILL_AURA = "kill-aura";
   public static final String OWNER_AUTO_FARM = "auto-farm";
   private static final RiptideHumanRotation.Stream STREAM = new RiptideHumanRotation.Stream();
   private static RiptideRotationUtil.Rotation currentRotation = null;
   private static RiptideRotationUtil.Rotation targetRotation = null;
   private static String owner = null;
   private static String tickWinner = null;
   private static int tickWinnerPriority = Integer.MIN_VALUE;
   private static int arbitrationTick = Integer.MIN_VALUE;
   private static int streamStepTick = Integer.MIN_VALUE;
   private static final float PIN_HOLD_MAX_DEGREES = 0.05F;
   private static int resetTicks = 0;
   private static final int WIND_DOWN_MAX_TICKS = 27;
   private static int windDownTicks = 0;
   private static boolean windingDown = false;

   private RiptideKillAuraRotation() {
   }

   public static void setTarget(RiptideRotationUtil.Rotation rotation) {
      setTarget("kill-aura", 10, rotation);
   }

   public static void setTarget(String ownerId, int priority, RiptideRotationUtil.Rotation rotation) {
      if (ownerId != null && rotation != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != arbitrationTick) {
            arbitrationTick = tick;
            tickWinner = null;
            tickWinnerPriority = Integer.MIN_VALUE;
         }

         if (tickWinner == null || priority > tickWinnerPriority) {
            tickWinner = ownerId;
            tickWinnerPriority = priority;
            owner = ownerId;
            targetRotation = rotation;
            resetTicks = 5;
            windingDown = false;
         }
      }
   }

   public static String currentOwner() {
      return owner;
   }

   public static void reset() {
      currentRotation = null;
      targetRotation = null;
      owner = null;
      tickWinner = null;
      tickWinnerPriority = Integer.MIN_VALUE;
      arbitrationTick = Integer.MIN_VALUE;
      streamStepTick = Integer.MIN_VALUE;
      resetTicks = 0;
      windDownTicks = 0;
      windingDown = false;
      RiptideHumanRotation.clear(STREAM);
   }

   public static void beginWindDown(String ownerId) {
      if (owner == null || owner.equals(ownerId)) {
         targetRotation = null;
         resetTicks = 0;
         windingDown = currentRotation != null;
      }
   }

   public static boolean isWindingDown() {
      return windingDown && currentRotation != null;
   }

   public static boolean hasCurrentRotation() {
      return currentRotation != null;
   }

   public static RiptideRotationUtil.Rotation getCurrentRotation() {
      return currentRotation;
   }

   public static void update(String ownerId, LocalPlayer player) {
      update(ownerId, player, 72.0F, 72.0F);
   }

   public static void update(String ownerId, LocalPlayer player, boolean pinQuiet) {
      update(ownerId, player, 72.0F, 72.0F, RiptideHumanRotation.MotionProfile.STANDARD, pinQuiet);
   }

   public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep) {
      update(ownerId, player, maxYawStep, maxPitchStep, RiptideHumanRotation.MotionProfile.STANDARD);
   }

   public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep, RiptideHumanRotation.MotionProfile profile) {
      update(ownerId, player, maxYawStep, maxPitchStep, profile, false);
   }

   private static void update(
      String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep, RiptideHumanRotation.MotionProfile profile, boolean pinQuiet
   ) {
      if (player == null) {
         reset();
      } else if (owner == null || owner.equals(ownerId)) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != streamStepTick) {
            streamStepTick = tick;
            RiptideRotationUtil.Rotation playerRotation = RiptideRotationUtil.playerRotation(player);
            if (targetRotation != null && resetTicks > 0) {
               if (!RiptideHumanRotation.isInitialized(STREAM)) {
                  RiptideRotationUtil.Rotation seedFrom = currentRotation;
                  if (seedFrom == null) {
                     RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
                     seedFrom = wire.initialized() ? new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch()) : playerRotation;
                  }

                  RiptideHumanRotation.seed(STREAM, seedFrom);
               }

               if (pinQuiet && currentRotation != null && RiptideRotationUtil.rotationAngleTo(currentRotation, targetRotation) <= 0.05F) {
                  resetTicks--;
               } else {
                  currentRotation = RiptideHumanRotation.step(
                     STREAM, targetRotation, maxYawStep, maxPitchStep, RiptideRotationUtil.sensitivityGcd(), false, profile
                  );
                  resetTicks--;
               }
            } else {
               targetRotation = null;
               if (currentRotation == null) {
                  windDownTicks = 0;
                  windingDown = false;
               } else {
                  if (windDownTicks <= 0) {
                     windDownTicks = 27;
                  }

                  RiptideRotationUtil.Rotation next = RiptideHumanRotation.step(
                     STREAM, playerRotation, 37.0F, 37.0F, RiptideRotationUtil.sensitivityGcd(), false
                  );
                  windDownTicks--;
                  if (!(RiptideRotationUtil.rotationAngleTo(next, playerRotation) <= 2.0F) && windDownTicks > 0) {
                     currentRotation = next;
                  } else {
                     float fixedYaw = currentRotation.yaw() + RiptideRotationUtil.angleDifference(player.getYRot(), currentRotation.yaw());
                     player.setYRot(fixedYaw);
                     player.yBob = fixedYaw;
                     player.yBobO = fixedYaw;
                     currentRotation = null;
                     owner = null;
                     windDownTicks = 0;
                     windingDown = false;
                     RiptideHumanRotation.clear(STREAM);
                  }
               }
            }
         }
      }
   }
}
