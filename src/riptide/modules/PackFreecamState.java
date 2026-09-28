package riptide.modules;

import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideColors;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptidePathWalker;
import riptide.util.RiptideRemoteView;

public final class PackFreecamState {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final double SCROLL_SPEED_STEP = 0.5;
   private static final double MIN_SPEED = 0.05;
   private static final double MAX_SPEED = 30.0;
   private static boolean active;
   private static Vec3 pos = Vec3.ZERO;
   private static Vec3 prevPos = Vec3.ZERO;
   private static float yaw;
   private static float pitch;
   private static float lastYaw;
   private static float lastPitch;
   private static CameraType previousCameraType = CameraType.FIRST_PERSON;
   private static boolean reloadChunks;
   private static boolean interactEnabled = true;

   private PackFreecamState() {
   }

   public static boolean isActive() {
      return active;
   }

   public static boolean interactEnabled() {
      return interactEnabled;
   }

   public static void setInteractEnabled(boolean enabled) {
      interactEnabled = enabled;
   }

   public static void enable(boolean shouldReloadChunks) {
      if (MC != null && MC.player != null && MC.level != null) {
         Vec3 startingPos = MC.gameRenderer.mainCamera().position();
         Entity startingView = (Entity)(MC.getCameraEntity() == null ? MC.player : MC.getCameraEntity());
         float startingYaw = startingView.getYRot();
         float startingPitch = startingView.getXRot();
         if (RiptideRemoteView.isActive()) {
            RiptideRemoteView.stop(false);
         }

         reloadChunks = shouldReloadChunks;
         previousCameraType = MC.options.getCameraType();
         pos = startingPos;
         prevPos = pos;
         yaw = startingYaw;
         pitch = startingPitch;
         lastYaw = yaw;
         lastPitch = pitch;
         active = true;
         clearMovementKeys();
         MC.options.setCameraType(CameraType.FIRST_PERSON);
         if (reloadChunks) {
            ModuleRenderUtil.refreshWorldRenderer();
         }
      }
   }

   public static void disable() {
      if (active) {
         active = false;
         if (MC != null) {
            MC.options.setCameraType(previousCameraType);
            if (reloadChunks) {
               ModuleRenderUtil.refreshWorldRenderer();
            }
         }

         reloadChunks = false;
      }
   }

   public static void tickMovement(double speed, double verticalSpeed) {
      if (active) {
         if (MC != null && MC.player != null && MC.level != null) {
            lastYaw = yaw;
            lastPitch = pitch;
            prevPos = pos;
            boolean forward = actuallyDown(MC.options.keyUp);
            boolean backward = actuallyDown(MC.options.keyDown);
            boolean right = actuallyDown(MC.options.keyRight);
            boolean left = actuallyDown(MC.options.keyLeft);
            boolean up = actuallyDown(MC.options.keyJump);
            boolean down = actuallyDown(MC.options.keyShift);
            boolean sprint = actuallyDown(MC.options.keySprint);
            clearMovementKeys();
            if (MC.gui.screen() == null) {
               double horizontal = Math.max(0.0, speed) * (sprint ? 1.0 : 0.5);
               double vertical = Math.max(0.0, verticalSpeed) * (sprint ? 1.0 : 0.5);
               Vec3 forwardVec = Vec3.directionFromRotation(0.0F, yaw);
               Vec3 rightVec = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
               double dx = 0.0;
               double dy = 0.0;
               double dz = 0.0;
               boolean movedForward = false;
               boolean movedSideways = false;
               if (forward) {
                  dx += forwardVec.x * horizontal;
                  dz += forwardVec.z * horizontal;
                  movedForward = true;
               }

               if (backward) {
                  dx -= forwardVec.x * horizontal;
                  dz -= forwardVec.z * horizontal;
                  movedForward = true;
               }

               if (right) {
                  dx += rightVec.x * horizontal;
                  dz += rightVec.z * horizontal;
                  movedSideways = true;
               }

               if (left) {
                  dx -= rightVec.x * horizontal;
                  dz -= rightVec.z * horizontal;
                  movedSideways = true;
               }

               if (movedForward && movedSideways) {
                  double diagonal = 1.0 / Math.sqrt(2.0);
                  dx *= diagonal;
                  dz *= diagonal;
               }

               if (up) {
                  dy += vertical;
               }

               if (down) {
                  dy -= vertical;
               }

               pos = pos.add(dx, dy, dz);
            }
         } else {
            active = false;
         }
      }
   }

   public static Vec3 onPlayerMove(MoverType type, Vec3 movement) {
      return movement;
   }

   public static boolean onMouseScroll(double scrollY) {
      if (!active || scrollY == 0.0) {
         return false;
      } else if (MC != null && MC.player != null && MC.gui.screen() == null && MC.gui.overlay() == null) {
         Module freecam = ModuleRegistry.get("freecam");
         if (freecam == null) {
            return false;
         } else {
            double step = scrollY > 0.0 ? 0.5 : -0.5;
            double speed = snapSpeed(parseSpeed(freecam.value("speed")) + step);
            double vertical = snapSpeed(parseSpeed(freecam.value("vertical-speed")) + step);
            freecam.setValueTransient("speed", formatSpeed(speed));
            freecam.setValueTransient("vertical-speed", formatSpeed(vertical));
            MC.gui.hud.setOverlayMessage(speedMessage(speed), false);
            return true;
         }
      } else {
         return false;
      }
   }

   private static double snapSpeed(double value) {
      return clampSpeed(Math.ceil(value * 2.0) / 2.0);
   }

   private static Component speedMessage(double speed) {
      return Component.empty()
         .append(styled("[", RiptideColors.textMuted(), false))
         .append(styled("RIPTIDE", RiptideColors.accent(), true))
         .append(styled("] ", RiptideColors.textMuted(), false))
         .append(styled("Freecam Speed", RiptideColors.packetGray(), true))
         .append(styled(": ", RiptideColors.textMuted(), false))
         .append(styled(formatSpeed(speed), RiptideColors.packetLightYellow(), true));
   }

   private static MutableComponent styled(String value, int color, boolean bold) {
      Style style = Style.EMPTY.withColor(color);
      if (bold) {
         style = style.withBold(true);
      }

      return Component.literal(value).setStyle(style);
   }

   private static double parseSpeed(String value) {
      try {
         return Double.parseDouble(value);
      } catch (NullPointerException | NumberFormatException var2) {
         return 1.0;
      }
   }

   private static double clampSpeed(double value) {
      return Math.max(0.05, Math.min(30.0, value));
   }

   private static String formatSpeed(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   public static void turn(double deltaYaw, double deltaPitch) {
      if (active) {
         yaw += (float)(deltaYaw * 0.15);
         pitch += (float)(deltaPitch * 0.15);
         pitch = Mth.clamp(pitch, -90.0F, 90.0F);
      }
   }

   public static double getX(float tickDelta) {
      return Mth.lerp(tickDelta, prevPos.x, pos.x);
   }

   public static double getY(float tickDelta) {
      return Mth.lerp(tickDelta, prevPos.y, pos.y);
   }

   public static double getZ(float tickDelta) {
      return Mth.lerp(tickDelta, prevPos.z, pos.z);
   }

   public static float getYaw(float tickDelta) {
      return Mth.lerp(tickDelta, lastYaw, yaw);
   }

   public static float getPitch(float tickDelta) {
      return Mth.lerp(tickDelta, lastPitch, pitch);
   }

   public static Vec3 eyePosition(float tickDelta) {
      return new Vec3(getX(tickDelta), getY(tickDelta), getZ(tickDelta));
   }

   public static Vec3 footPosition(float tickDelta) {
      return eyePosition(tickDelta).subtract(0.0, eyeOffset(), 0.0);
   }

   private static double eyeOffset() {
      Entity view = MC == null ? null : MC.getCameraEntity();
      return view == null ? 0.0 : view.getEyeY() - view.getY();
   }

   private static boolean actuallyDown(KeyMapping mapping) {
      return mapping != null && RiptideKeyMappingBridge.of(mapping).riptide$isActuallyDown();
   }

   private static void clearMovementKeys() {
      if (MC != null && MC.options != null) {
         MC.options.keyUp.setDown(false);
         MC.options.keyDown.setDown(false);
         MC.options.keyRight.setDown(false);
         MC.options.keyLeft.setDown(false);
         MC.options.keyJump.setDown(false);
         if (!ModuleRegistry.sneakHoldsShift()) {
            MC.options.keyShift.setDown(false);
         }

         MC.options.keySprint.setDown(false);
         RiptidePathWalker.onExternalKeyRelease();
      }
   }
}
