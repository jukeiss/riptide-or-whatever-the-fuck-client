package riptide.util.macro;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideInventoryHelper;

public class DisconnectAction implements MacroAction {
   private boolean enabled = true;
   public int delayMs = 0;
   public DisconnectAction.DisconnectMode mode = DisconnectAction.DisconnectMode.DISCONNECT;
   public DisconnectAction.LagMethod lagMethod = DisconnectAction.LagMethod.CLICK_SLOT;
   public DisconnectAction.KickMethod kickMethod = DisconnectAction.KickMethod.HURT;
   public int packetCount = 200;
   public boolean useNextAction = false;
   private transient List<MacroAction> nextActions = null;
   public DisconnectAction.AutoTrigger trigger = DisconnectAction.AutoTrigger.TELEPORT;
   public double targetX = 0.0;
   public double targetY = 0.0;
   public double targetZ = 0.0;
   public double tolerance = 10.0;
   public int bufferMs = 50;
   public int timeoutSec = 60;
   public boolean useExactPosition = false;

   @Override
   public void execute(Minecraft mc) {
      switch (this.mode) {
         case DISCONNECT:
            this.executeDisconnect(mc);
            break;
         case KICK:
            this.executeKick(mc);
            break;
         case KICK_DUPE:
            this.executeKickDupe(mc);
            break;
         case AUTO_DISCONNECT:
            this.executeAutoDisconnect(mc);
      }
   }

   private void executeDisconnect(Minecraft mc) {
      if (mc.level != null && mc.getConnection() != null) {
         mc.getConnection().getConnection().disconnect(Component.literal("Disconnected by Macro"));
      }
   }

   private void executeAutoDisconnect(Minecraft mc) {
      if (mc.player != null && mc.getConnection() != null) {
         RiptideClientMessaging.sendPrefixed("§eAuto Disconnect: Monitoring for " + this.trigger.name() + "...");
         String initialWorld = mc.level != null ? mc.level.dimension().identifier().toString() : "";
         double initialX = mc.player.getX();
         double initialY = mc.player.getY();
         double initialZ = mc.player.getZ();
         Screen initialScreen = mc.gui.screen();
         boolean initialScreenOpen = mc.gui.screen() != null;
         boolean initialInventoryEmpty = this.isMainInventoryEmpty(mc);
         long startTime = System.currentTimeMillis();
         boolean triggered = false;
         long lastPositionCheck = startTime;
         Screen prevScreen = initialScreen;

         while (!triggered && MacroExecutor.isCurrentActionRunActive() && System.currentTimeMillis() - startTime < this.timeoutSec * 1000L) {
            if (mc.player == null || mc.getConnection() == null) {
               RiptideClientMessaging.sendPrefixed("§cConnection lost during wait!");
               return;
            }

            switch (this.trigger) {
               case TELEPORT:
                  long now = System.currentTimeMillis();
                  if (now - lastPositionCheck >= 50L) {
                     double distx = Math.sqrt(
                        Math.pow(mc.player.getX() - initialX, 2.0) + Math.pow(mc.player.getY() - initialY, 2.0) + Math.pow(mc.player.getZ() - initialZ, 2.0)
                     );
                     if (distx > 5.0) {
                        RiptideClientMessaging.sendPrefixed(String.format("§aTeleport detected! Jumped %.1f blocks.", distx));
                        triggered = true;
                     }

                     if (!triggered && mc.level != null) {
                        String cw = mc.level.dimension().identifier().toString();
                        if (!cw.isEmpty() && !cw.equals(initialWorld)) {
                           RiptideClientMessaging.sendPrefixed("§aWorld change detected during teleport!");
                           triggered = true;
                        }
                     }

                     initialX = mc.player.getX();
                     initialY = mc.player.getY();
                     initialZ = mc.player.getZ();
                     lastPositionCheck = now;
                  }
                  break;
               case POSITION:
                  double dx = mc.player.getX() - initialX;
                  double dy = mc.player.getY() - initialY;
                  double dz = mc.player.getZ() - initialZ;
                  double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  if (dist > this.tolerance) {
                     RiptideClientMessaging.sendPrefixed(String.format("§aPosition jump detected! Moved %.1f blocks (tolerance: %.0f)", dist, this.tolerance));
                     triggered = true;
                  }

                  long now = System.currentTimeMillis();
                  if (now - lastPositionCheck >= 500L) {
                     initialX = mc.player.getX();
                     initialY = mc.player.getY();
                     initialZ = mc.player.getZ();
                     lastPositionCheck = now;
                  }
                  break;
               case WORLD_CHANGE:
                  String currentWorld = mc.level != null ? mc.level.dimension().identifier().toString() : "";
                  if (!currentWorld.equals(initialWorld) && !currentWorld.isEmpty()) {
                     RiptideClientMessaging.sendPrefixed("§aWorld change detected!");
                     triggered = true;
                  }
                  break;
               case GUI_CLOSE:
                  Screen curScreen = mc.gui.screen();
                  if (prevScreen != null && curScreen == null) {
                     RiptideClientMessaging.sendPrefixed("§aGUI close detected!");
                     triggered = true;
                  }

                  prevScreen = curScreen;
                  break;
               case INVENTORY_CLEAR:
                  if (!initialInventoryEmpty && this.isMainInventoryEmpty(mc)) {
                     RiptideClientMessaging.sendPrefixed("§aInventory clear detected!");
                     triggered = true;
                  }
            }

            if (!triggered) {
               try {
                  Thread.sleep(5L);
               } catch (InterruptedException var29) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }
         }

         if (!triggered) {
            RiptideClientMessaging.sendPrefixed("§cAuto Disconnect: Timeout after " + this.timeoutSec + "s");
            return;
         } else {
            if (this.bufferMs > 0) {
               RiptideClientMessaging.sendPrefixed("§eBuffer delay: " + this.bufferMs + "ms");

               try {
                  Thread.sleep(this.bufferMs);
               } catch (InterruptedException var28) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }

            RiptideClientMessaging.sendPrefixed("§cExecuting disconnect!");
            this.executeDisconnect(mc);
            return;
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cNo player or network connection!");
      }
   }

   private boolean isMainInventoryEmpty(Minecraft mc) {
      if (mc.player == null) {
         return true;
      } else {
         for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) {
               return false;
            }
         }

         return true;
      }
   }

   private void executeKick(Minecraft mc) {
      if (mc.player == null || mc.getConnection() == null) {
         RiptideClientMessaging.sendPrefixed("§cNo player or network connection!");
      } else if (this.validateLagMethod(mc)) {
         this.sendLagPackets(mc);
         this.sendKickPacket(mc);
         this.sendLagPackets(mc);
         RiptideClientMessaging.sendPrefixed("§aKick executed!");
      }
   }

   public void setNextActions(List<MacroAction> actions) {
      this.nextActions = actions;
   }

   private void executeKickDupe(Minecraft mc) {
      if (mc.player == null || mc.getConnection() == null) {
         RiptideClientMessaging.sendPrefixed("§cNo player or network connection!");
      } else if (this.validateLagMethod(mc)) {
         if (this.useNextAction) {
            List<MacroAction> actions = this.nextActions;
            this.nextActions = null;
            if (actions == null || actions.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cKick Dupe: no next action in macro! Add action(s) after this one.");
               return;
            }

            this.sendLagPackets(mc);

            for (MacroAction act : actions) {
               try {
                  act.execute(mc);
               } catch (Exception var6) {
                  RiptideClientMessaging.sendPrefixed("§cError in dupe action '" + act.getDisplayName() + "': " + var6.getMessage());
               }
            }

            this.sendKickPacket(mc);
            this.sendLagPackets(mc);
            String actNames = actions.size() == 1 ? actions.get(0).getDisplayName() : actions.size() + " actions";
            RiptideClientMessaging.sendPrefixed("§aKick Dupe executed! (" + actNames + ")");
         } else {
            this.sendLagPackets(mc);
            if (!this.findAndUseBundle(mc)) {
               return;
            }

            this.sendKickPacket(mc);
            this.sendLagPackets(mc);
            RiptideClientMessaging.sendPrefixed("§aKick Dupe executed! (bundle)");
         }
      }
   }

   private boolean findAndUseBundle(Minecraft mc) {
      InteractionHand activeInteractionHand = null;
      if (!mc.player.getMainHandItem().isEmpty() && BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().endsWith("bundle")) {
         activeInteractionHand = InteractionHand.MAIN_HAND;
      } else if (!mc.player.getOffhandItem().isEmpty() && BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).getPath().endsWith("bundle")) {
         activeInteractionHand = InteractionHand.OFF_HAND;
      }

      if (activeInteractionHand != null) {
         mc.getConnection().send(new ServerboundUseItemPacket(activeInteractionHand, 0, mc.player.getYRot(), mc.player.getXRot()));
         return true;
      } else {
         int bundleSlot = -1;

         for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("bundle")) {
               bundleSlot = i;
               break;
            }
         }

         if (bundleSlot < 0) {
            RiptideClientMessaging.sendPrefixed("§cNo bundle found in inventory or hands!");
            return false;
         } else {
            int hotbarSlot = bundleSlot;
            if (bundleSlot > 8) {
               int target = 0;

               for (int ix = 0; ix <= 8; ix++) {
                  if (mc.player.getInventory().getItem(ix).isEmpty()) {
                     target = ix;
                     break;
                  }
               }

               RiptideInventoryHelper.swapInventorySlots(mc, bundleSlot, target);
               hotbarSlot = target;
            }

            RiptideInventoryHelper.selectHotbarSlot(mc, hotbarSlot);
            mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
            return true;
         }
      }
   }

   private boolean validateLagMethod(Minecraft mc) {
      switch (this.lagMethod) {
         case BOAT_NBT:
            Entity vehicle = mc.player.getVehicle();
            if (!(vehicle instanceof ChestBoat) && !(vehicle instanceof AbstractMinecartContainer)) {
               RiptideClientMessaging.sendPrefixed("§cYou must be in a Chest Boat or Minecart with Chest for BoatNBT!");
               return false;
            }
            break;
         case ENTITY_NBT:
            if (mc.hitResult == null || mc.hitResult.getType() != Type.ENTITY) {
               RiptideClientMessaging.sendPrefixed("§cYou must be looking at an entity for EntityNBT!");
               return false;
            }

            Entity target = ((EntityHitResult)mc.hitResult).getEntity();
            if (!(target instanceof ChestBoat) && !(target instanceof AbstractMinecartContainer)) {
               RiptideClientMessaging.sendPrefixed("§cTarget must be a Chest Boat or Minecart with Chest!");
               return false;
            }
      }

      return true;
   }

   private void sendLagPackets(Minecraft mc) {
      switch (this.lagMethod) {
         case CLICK_SLOT:
            for (int i = 0; i < this.packetCount; i++) {
               mc.getConnection()
                  .send(new ServerboundContainerClickPacket(0, 0, (short)0, (byte)0, ContainerInput.PICKUP, new Int2ObjectArrayMap(), HashedStack.EMPTY));
            }
            break;
         case BOAT_NBT:
            for (int i = 0; i < this.packetCount; i++) {
               mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.OPEN_INVENTORY));
            }
            break;
         case ENTITY_NBT:
            if (mc.hitResult instanceof EntityHitResult ehr) {
               Entity target = ehr.getEntity();

               for (int i = 0; i < this.packetCount; i++) {
                  mc.getConnection()
                     .send(
                        new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, new Vec3(target.getX(), target.getY(), target.getZ()), true)
                     );
               }
            }
      }
   }

   private void sendKickPacket(Minecraft mc) {
      switch (this.kickMethod) {
         case HURT:
            mc.getConnection().send(new ServerboundInteractPacket(mc.player.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
            break;
         case CLIENT_SETTINGS:
            ClientInformation opts = new ClientInformation(
               mc.options.languageCode,
               -2,
               ChatVisiblity.FULL,
               (Boolean)mc.options.chatColors().get(),
               127,
               (HumanoidArm)mc.options.mainHand().get(),
               false,
               false,
               (ParticleStatus)mc.options.particles().get()
            );
            mc.getConnection().send(new ServerboundClientInformationPacket(opts));
            break;
         case INVALID_SLOT:
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(-1));
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("delayMs", this.delayMs);
      tag.putString("mode", this.mode.name());
      tag.putString("lagMethod", this.lagMethod.name());
      tag.putString("kickMethod", this.kickMethod.name());
      tag.putInt("packetCount", this.packetCount);
      tag.putBoolean("useNextAction", this.useNextAction);
      tag.putBoolean("enabled", this.enabled);
      tag.putString("trigger", this.trigger.name());
      tag.putDouble("targetX", this.targetX);
      tag.putDouble("targetY", this.targetY);
      tag.putDouble("targetZ", this.targetZ);
      tag.putDouble("tolerance", this.tolerance);
      tag.putInt("bufferMs", this.bufferMs);
      tag.putInt("timeoutSec", this.timeoutSec);
      tag.putBoolean("useExactPosition", this.useExactPosition);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("delayMs")) {
         this.delayMs = tag.getIntOr("delayMs", 0);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      if (tag.contains("mode")) {
         try {
            this.mode = DisconnectAction.DisconnectMode.valueOf(tag.getStringOr("mode", "DISCONNECT"));
         } catch (IllegalArgumentException var6) {
            this.mode = DisconnectAction.DisconnectMode.DISCONNECT;
         }
      }

      if (tag.contains("lagMethod")) {
         try {
            this.lagMethod = DisconnectAction.LagMethod.valueOf(tag.getStringOr("lagMethod", "CLICK_SLOT"));
         } catch (IllegalArgumentException var5) {
            this.lagMethod = DisconnectAction.LagMethod.CLICK_SLOT;
         }
      }

      if (tag.contains("kickMethod")) {
         try {
            this.kickMethod = DisconnectAction.KickMethod.valueOf(tag.getStringOr("kickMethod", "HURT"));
         } catch (IllegalArgumentException var4) {
            this.kickMethod = DisconnectAction.KickMethod.HURT;
         }
      }

      if (tag.contains("packetCount")) {
         this.packetCount = tag.getIntOr("packetCount", 200);
      }

      if (tag.contains("useNextAction")) {
         this.useNextAction = tag.getBooleanOr("useNextAction", false);
      }

      if (tag.contains("trigger")) {
         try {
            this.trigger = DisconnectAction.AutoTrigger.valueOf(tag.getStringOr("trigger", "TELEPORT"));
         } catch (IllegalArgumentException var3) {
            this.trigger = DisconnectAction.AutoTrigger.TELEPORT;
         }
      }

      if (tag.contains("targetX")) {
         this.targetX = tag.getDoubleOr("targetX", 0.0);
      }

      if (tag.contains("targetY")) {
         this.targetY = tag.getDoubleOr("targetY", 0.0);
      }

      if (tag.contains("targetZ")) {
         this.targetZ = tag.getDoubleOr("targetZ", 0.0);
      }

      if (tag.contains("tolerance")) {
         this.tolerance = tag.getDoubleOr("tolerance", 2.0);
      }

      if (tag.contains("bufferMs")) {
         this.bufferMs = tag.getIntOr("bufferMs", 50);
      }

      if (tag.contains("timeoutSec")) {
         this.timeoutSec = tag.getIntOr("timeoutSec", 60);
      }

      if (tag.contains("useExactPosition")) {
         this.useExactPosition = tag.getBooleanOr("useExactPosition", false);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.DISCONNECT;
   }

   @Override
   public String getDisplayName() {
      return switch (this.mode) {
         case DISCONNECT -> this.delayMs > 0 ? "Disconnect (" + this.delayMs + "ms)" : "Disconnect";
         case KICK -> "Kick (" + this.lagMethod.name() + ", " + this.kickMethod.name() + ", " + this.packetCount + ")";
         case KICK_DUPE -> {
            String action = this.useNextAction ? "Next Actions" : "Bundle";
            yield "Kick Dupe (" + this.lagMethod.name() + ", " + this.kickMethod.name() + ", " + this.packetCount + ", " + action + ")";
         }
         case AUTO_DISCONNECT -> {
            String triggerInfo = switch (this.trigger) {
               case TELEPORT -> "Loading Screen";
               case POSITION -> String.format("Pos Jump > %.0f blocks", this.tolerance);
               case WORLD_CHANGE -> "World Change";
               case GUI_CLOSE -> "GUI Close";
               case INVENTORY_CLEAR -> "Inv Clear";
            };
            yield "Auto DC (" + triggerInfo + ", +" + this.bufferMs + "ms)";
         }
      };
   }

   @Override
   public String getIcon() {
      return this.mode == DisconnectAction.DisconnectMode.AUTO_DISCONNECT ? "ADC" : "DC";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum AutoTrigger {
      TELEPORT,
      POSITION,
      WORLD_CHANGE,
      GUI_CLOSE,
      INVENTORY_CLEAR;
   }

   public static enum DisconnectMode {
      DISCONNECT,
      KICK,
      KICK_DUPE,
      AUTO_DISCONNECT;
   }

   public static enum KickMethod {
      HURT,
      CLIENT_SETTINGS,
      INVALID_SLOT;
   }

   public static enum LagMethod {
      CLICK_SLOT,
      BOAT_NBT,
      ENTITY_NBT;
   }
}
