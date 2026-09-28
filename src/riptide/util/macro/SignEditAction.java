package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import riptide.modules.PackHideState;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSignEditAccess;

public class SignEditAction implements MacroAction, WaitsForGui {
   public SignEditAction.TargetMode targetMode = SignEditAction.TargetMode.CURRENT_SIGN_GUI;
   public String line1 = "";
   public String line2 = "";
   public String line3 = "";
   public String line4 = "";
   public boolean frontText = true;
   public int x = 0;
   public int y = 0;
   public int z = 0;
   public boolean waitForGuiBefore = true;
   public boolean waitForGuiAfter = false;
   public String guiName = "SIGN";
   public SignEditAction.CloseMode closeMode = SignEditAction.CloseMode.STAY_OPEN;
   public boolean closeAfterEdit = false;
   public boolean closeWithPacket = false;
   public boolean sendCommandAfter = false;
   public String commandAfter = "";
   public boolean sendClosePacketAfter = false;
   public int closePacketContainerId = 0;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc != null && mc.getConnection() != null) {
            BlockPos pos = this.resolvePos(mc);
            if (pos != null) {
               MacroTemplate.Resolution resolvedLine1 = MacroVariables.resolve(this.line1, mc);
               MacroTemplate.Resolution resolvedLine2 = MacroVariables.resolve(this.line2, mc);
               MacroTemplate.Resolution resolvedLine3 = MacroVariables.resolve(this.line3, mc);
               MacroTemplate.Resolution resolvedLine4 = MacroVariables.resolve(this.line4, mc);
               if (MacroVariables.resolved("Edit sign", resolvedLine1, resolvedLine2, resolvedLine3, resolvedLine4)) {
                  MacroTemplate.Resolution resolvedCommand = MacroTemplate.Resolution.ok("");
                  if (this.sendCommandAfter && this.commandAfter != null && !this.commandAfter.isBlank()) {
                     resolvedCommand = MacroVariables.resolve(this.commandAfter, mc);
                     if (!MacroVariables.resolved("Edit sign command", resolvedCommand)) {
                        return;
                     }
                  }

                  RiptideSharedState.get().setForceNextSignUpdatePacket(true);
                  mc.getConnection()
                     .send(
                        new ServerboundSignUpdatePacket(
                           pos, this.resolveFront(mc), resolvedLine1.value(), resolvedLine2.value(), resolvedLine3.value(), resolvedLine4.value()
                        )
                     );
                  if (this.sendCommandAfter && this.commandAfter != null && !this.commandAfter.isBlank()) {
                     String command = resolvedCommand.value().trim();
                     if (command.startsWith("/")) {
                        command = command.substring(1);
                     }

                     if (!command.isBlank()) {
                        mc.getConnection().sendCommand(command);
                     }
                  }

                  SignEditAction.CloseMode mode = this.closeMode == null ? SignEditAction.CloseMode.STAY_OPEN : this.closeMode;
                  if (mode == SignEditAction.CloseMode.SEND_CLOSE_PACKET_ONLY) {
                     mc.getConnection().send(new ServerboundContainerClosePacket(this.closePacketContainerId));
                  }

                  if (mode == SignEditAction.CloseMode.CLOSE_LOCAL || mode == SignEditAction.CloseMode.CLOSE_WITH_PACKET) {
                     if (mode == SignEditAction.CloseMode.CLOSE_WITH_PACKET && mc.player != null && mc.player.containerMenu != null) {
                        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                     }

                     mc.gui.setScreen(null);
                  }
               }
            }
         }
      }
   }

   private BlockPos resolvePos(Minecraft mc) {
      if (this.targetMode == SignEditAction.TargetMode.MANUAL_POS) {
         return new BlockPos(this.x, this.y, this.z);
      } else if (this.targetMode == SignEditAction.TargetMode.LAST_INTERACTED_BLOCK) {
         return RiptideSharedState.get().getLastInteractedBlockPos();
      } else {
         return mc.gui.screen() instanceof RiptideSignEditAccess access ? access.riptide$getSignPos() : null;
      }
   }

   private boolean resolveFront(Minecraft mc) {
      return this.targetMode == SignEditAction.TargetMode.CURRENT_SIGN_GUI && mc.gui.screen() instanceof RiptideSignEditAccess access
         ? access.riptide$isFrontText()
         : this.frontText;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SIGN_EDIT;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SIGN_EDIT");
      tag.putString("targetMode", this.targetMode.name());
      tag.putString("line1", this.line1);
      tag.putString("line2", this.line2);
      tag.putString("line3", this.line3);
      tag.putString("line4", this.line4);
      tag.putBoolean("frontText", this.frontText);
      tag.putInt("x", this.x);
      tag.putInt("y", this.y);
      tag.putInt("z", this.z);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putString("closeMode", this.closeMode == null ? SignEditAction.CloseMode.STAY_OPEN.name() : this.closeMode.name());
      tag.putBoolean("sendCommandAfter", this.sendCommandAfter);
      tag.putString("commandAfter", this.commandAfter);
      tag.putInt("closePacketContainerId", this.closePacketContainerId);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.targetMode = MacroStringList.enumValue(
         SignEditAction.TargetMode.class, tag.getStringOr("targetMode", "CURRENT_SIGN_GUI"), SignEditAction.TargetMode.CURRENT_SIGN_GUI
      );
      this.line1 = tag.getStringOr("line1", "");
      this.line2 = tag.getStringOr("line2", "");
      this.line3 = tag.getStringOr("line3", "");
      this.line4 = tag.getStringOr("line4", "");
      this.frontText = tag.getBooleanOr("frontText", true);
      this.x = tag.getIntOr("x", 0);
      this.y = tag.getIntOr("y", 0);
      this.z = tag.getIntOr("z", 0);
      this.waitForGuiBefore = tag.getBooleanOr("waitForGuiBefore", true);
      this.waitForGuiAfter = tag.getBooleanOr("waitForGuiAfter", false);
      this.guiName = tag.getStringOr("guiName", "SIGN");
      if (tag.contains("closeMode")) {
         this.closeMode = MacroStringList.enumValue(
            SignEditAction.CloseMode.class, tag.getStringOr("closeMode", "STAY_OPEN"), SignEditAction.CloseMode.STAY_OPEN
         );
      } else if (tag.getBooleanOr("sendClosePacketAfter", false)) {
         this.closeMode = SignEditAction.CloseMode.SEND_CLOSE_PACKET_ONLY;
      } else if (tag.getBooleanOr("closeAfterEdit", false)) {
         this.closeMode = tag.getBooleanOr("closeWithPacket", false) ? SignEditAction.CloseMode.CLOSE_WITH_PACKET : SignEditAction.CloseMode.CLOSE_LOCAL;
      } else {
         this.closeMode = SignEditAction.CloseMode.STAY_OPEN;
      }

      this.closeAfterEdit = this.closeMode == SignEditAction.CloseMode.CLOSE_LOCAL || this.closeMode == SignEditAction.CloseMode.CLOSE_WITH_PACKET;
      this.closeWithPacket = this.closeMode == SignEditAction.CloseMode.CLOSE_WITH_PACKET;
      this.sendCommandAfter = tag.getBooleanOr("sendCommandAfter", false);
      this.commandAfter = tag.getStringOr("commandAfter", "");
      this.sendClosePacketAfter = this.closeMode == SignEditAction.CloseMode.SEND_CLOSE_PACKET_ONLY;
      this.closePacketContainerId = tag.getIntOr("closePacketContainerId", 0);
   }

   @Override
   public String getDisplayName() {
      String suffix = this.closeMode != SignEditAction.CloseMode.STAY_OPEN
         ? " + " + this.closeMode.name().toLowerCase(Locale.ROOT)
         : (this.sendCommandAfter ? " + command" : "");
      return "Edit sign" + suffix;
   }

   @Override
   public String getIcon() {
      return "S";
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitForGuiBefore(boolean value) {
      this.waitForGuiBefore = value;
   }

   @Override
   public void setWaitForGuiAfter(boolean value) {
      this.waitForGuiAfter = value;
   }

   @Override
   public void setWaitGuiName(String value) {
      this.guiName = value;
   }

   public static enum CloseMode {
      STAY_OPEN,
      CLOSE_LOCAL,
      CLOSE_WITH_PACKET,
      SEND_CLOSE_PACKET_ONLY;
   }

   public static enum TargetMode {
      CURRENT_SIGN_GUI,
      LAST_INTERACTED_BLOCK,
      MANUAL_POS;
   }
}
