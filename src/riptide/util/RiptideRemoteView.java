package riptide.util;

import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import riptide.commands.RiptideCommands;
import riptide.modules.PackFreecamState;
import riptide.util.multi.MultiTakeoverState;

public final class RiptideRemoteView {
   private static volatile Player target;
   private static volatile UUID targetUuid;
   private static volatile String targetName = "";
   private static volatile ClientLevel targetLevel;
   private static CameraType previousCameraType = CameraType.FIRST_PERSON;
   private static boolean forcedFirstPerson;

   private RiptideRemoteView() {
   }

   public static boolean isActive() {
      return targetUuid != null;
   }

   public static String targetName() {
      return targetName;
   }

   public static RiptideRemoteView.Result start(Player player) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null && mc.level != null && mc.getConnection() != null) {
         if (player == null || player.isRemoved() || player.level() != mc.level) {
            return new RiptideRemoteView.Result(false, "That player is not loaded.");
         } else if (player == mc.player) {
            return new RiptideRemoteView.Result(false, "You cannot remote-view yourself.");
         } else if (MultiTakeoverState.isActive()) {
            return new RiptideRemoteView.Result(false, "Leave Multi POV first.");
         } else if (PackFreecamState.isActive()) {
            return new RiptideRemoteView.Result(false, "Disable Freecam first.");
         } else if (isActive() && player.getUUID().equals(targetUuid)) {
            stop(false);
            return new RiptideRemoteView.Result(true, "Remote view stopped.");
         } else {
            if (isActive()) {
               stop(false);
            }

            previousCameraType = mc.options.getCameraType();
            forcedFirstPerson = !previousCameraType.isFirstPerson();
            target = player;
            targetUuid = player.getUUID();
            targetName = profileName(player);
            targetLevel = mc.level;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(player);
            return new RiptideRemoteView.Result(true, "Viewing " + targetName + ". Use " + RiptideCommands.effectivePrefix() + "rv stop to leave.");
         }
      } else {
         return new RiptideRemoteView.Result(false, "Join a world first.");
      }
   }

   public static void stop(boolean notify) {
      Minecraft mc = Minecraft.getInstance();
      Player oldTarget = target;
      String oldName = targetName;
      boolean restorePerspective = forcedFirstPerson;
      CameraType restoreType = previousCameraType;
      clear();
      if (mc != null) {
         Entity camera = mc.getCameraEntity();
         if (mc.player != null && (camera == oldTarget || camera == null || camera.isRemoved())) {
            mc.setCameraEntity(mc.player);
         }

         if (restorePerspective && mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(restoreType);
         }
      }

      if (notify && oldTarget != null) {
         RiptideNotifications.show("Remote view ended" + (oldName.isBlank() ? "" : ": " + oldName), -14249);
      }
   }

   public static void tick() {
      if (isActive()) {
         Minecraft mc = Minecraft.getInstance();
         Player current = target;
         if (mc == null
            || mc.player == null
            || mc.level == null
            || mc.getConnection() == null
            || mc.level != targetLevel
            || current == null
            || current.isRemoved()
            || !current.getUUID().equals(targetUuid)
            || mc.getCameraEntity() != current) {
            stop(true);
         }
      }
   }

   public static Player viewedPlayer() {
      Minecraft mc = Minecraft.getInstance();
      Player current = target;
      return isActive() && current != null && !current.isRemoved() && mc != null && mc.level == targetLevel && mc.getCameraEntity() == current ? current : null;
   }

   public static Player firstPersonPlayer(Player multiPilot) {
      return multiPilot != null ? multiPilot : viewedPlayer();
   }

   public static Entity beginMainPlayerPick(Minecraft mc) {
      Player current = viewedPlayer();
      if (current != null && mc != null && mc.player != null && mc.getCameraEntity() == current) {
         mc.setCameraEntity(mc.player);
         return current;
      } else {
         return null;
      }
   }

   public static void endMainPlayerPick(Minecraft mc, Entity restore) {
      if (mc != null && restore != null && isActive() && restore == target && !restore.isRemoved()) {
         if (mc.getCameraEntity() == mc.player) {
            mc.setCameraEntity(restore);
         }
      }
   }

   private static String profileName(Player player) {
      String name = player.getGameProfile() == null ? null : player.getGameProfile().name();
      if (name == null || name.isBlank()) {
         name = player.getName().getString();
      }

      return name != null && !name.isBlank() ? name : "player";
   }

   private static void clear() {
      target = null;
      targetUuid = null;
      targetName = "";
      targetLevel = null;
      previousCameraType = CameraType.FIRST_PERSON;
      forcedFirstPerson = false;
   }

   public record Result(boolean ok, String message) {
   }
}
