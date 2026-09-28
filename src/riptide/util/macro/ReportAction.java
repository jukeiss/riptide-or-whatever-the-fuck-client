package riptide.util.macro;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import riptide.modules.PackHideState;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSharedState;

public class ReportAction implements MacroAction {
   public ReportAction.StartEvent startEvent = ReportAction.StartEvent.ACTION_START;
   public ReportAction.EndEvent endEvent = ReportAction.EndEvent.GUI_OPEN;
   public ReportAction.EndCondition endCondition = ReportAction.EndCondition.GUI_OPEN;
   public String reportLabel = "Report";
   public String startConditionType = RaceAction.TriggerType.DELAY.name();
   public String startActionType = MacroActionType.ITEM.name();
   public String endConditionType = RaceAction.TriggerType.WAIT_GUI.name();
   public int conditionCount = 0;
   public String commandText = "";
   public String endGuiTitle = "";
   public String endPacketName = "";
   public String endTargetDimension = "";
   public double endDistance = 5.0;
   public boolean endHorizontalOnly = false;
   public int timeoutMs = 10000;
   public boolean stashToSharedState = true;
   public String guiTitleSubstr = "";
   public double positionTolerance = 5.0;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("§cReport: no network connection");
         } else {
            long executeStartNanos = System.nanoTime();
            long startNanos = this.resolveStartAnchor(mc);
            if (startNanos > 0L) {
               long spentMs = (System.nanoTime() - executeStartNanos) / 1000000L;
               int remainingTimeout = (int)Math.max(1L, Math.max(100, this.timeoutMs) - spentMs);
               if (remainingTimeout <= 0) {
                  RiptideClientMessaging.sendPrefixed("§eReport \"" + this.safeLabel() + "\": no time left after start event");
               } else {
                  MacroAction condition = this.buildEndConditionAction();

                  boolean fired;
                  try {
                     fired = MacroExecutor.waitForMacroCondition(mc, condition, remainingTimeout);
                  } catch (InterruptedException var13) {
                     Thread.currentThread().interrupt();
                     return;
                  }

                  if (!fired) {
                     RiptideClientMessaging.sendPrefixed("§eReport \"" + this.safeLabel() + "\": timeout waiting for " + this.describeEnd());
                  } else {
                     long deltaMs = (System.nanoTime() - startNanos) / 1000000L;
                     RiptideClientMessaging.sendPrefixed(
                        "§b" + this.safeLabel() + ": " + deltaMs + " ms (" + this.describeStart() + " -> " + this.describeEnd() + ")"
                     );
                     if (this.stashToSharedState) {
                        RiptideSharedState.get()
                           .pushReport(
                              new RiptideSharedState.ReportSample(
                                 this.safeLabel(), deltaMs, this.describeStart(), this.describeEnd(), System.currentTimeMillis()
                              )
                           );
                     }
                  }
               }
            }
         }
      }
   }

   private long resolveStartAnchor(Minecraft mc) {
      if (this.startEvent != ReportAction.StartEvent.ACTION_START && this.startEvent != ReportAction.StartEvent.MANUAL) {
         Predicate<Packet<?>> startMatch = this.startEvent == ReportAction.StartEvent.COMMAND_SENT
            ? p -> p instanceof ServerboundChatCommandPacket
               || p instanceof ServerboundChatCommandSignedPacket
               || p instanceof ServerboundChatPacket spc && spc.message() != null && spc.message().startsWith("/")
            : p -> p instanceof ServerboundContainerClickPacket;
         MacroExecutor.OneShotPacketListener sendListener = MacroExecutor.awaitSend(startMatch);

         try {
            if (this.startEvent == ReportAction.StartEvent.COMMAND_SENT) {
               String command = normalizeCommand(this.commandText);
               if (command.isBlank()) {
                  sendListener.cancel();
                  RiptideClientMessaging.sendPrefixed("Report \"" + this.safeLabel() + "\": set a command for COMMAND_SENT.");
                  return 0L;
               }

               mc.execute(() -> {
                  if (!PackHideState.isHardLocked() && mc.getConnection() != null) {
                     mc.getConnection().sendCommand(command);
                  }
               });
            }

            sendListener.future.get(Math.max(100, this.timeoutMs), TimeUnit.MILLISECONDS);
            return System.nanoTime();
         } catch (Exception var5) {
            sendListener.cancel();
            RiptideClientMessaging.sendPrefixed("§eReport \"" + this.safeLabel() + "\": timeout waiting for start event " + this.startEvent.name());
            return 0L;
         }
      } else {
         return System.nanoTime();
      }
   }

   private MacroAction buildEndConditionAction() {
      return (MacroAction)(switch (this.endCondition) {
         case GUI_OPEN -> {
            WaitForGuiAction action = new WaitForGuiAction();
            action.waitMode = WaitForGuiAction.WaitMode.OPEN;
            action.guiTitle = this.endGuiTitle == null ? "" : this.endGuiTitle;
            yield action;
         }
         case GUI_CLOSE -> {
            WaitForGuiAction action = new WaitForGuiAction();
            action.waitMode = WaitForGuiAction.WaitMode.CLOSE;
            action.guiTitle = this.endGuiTitle == null ? "" : this.endGuiTitle;
            yield action;
         }
         case TELEPORT_PACKET -> {
            WaitForPacketAction action = new WaitForPacketAction("S2C:" + ClientboundPlayerPositionPacket.class.getSimpleName());
            yield action;
         }
         case WORLD_CHANGE -> {
            WaitForWorldChangeAction action = new WaitForWorldChangeAction();
            action.targetDimension = this.endTargetDimension == null ? "" : this.endTargetDimension;
            yield action;
         }
         case POSITION_DELTA -> {
            WaitForPositionDeltaAction action = new WaitForPositionDeltaAction();
            action.distance = this.endDistance;
            action.horizontalOnly = this.endHorizontalOnly;
            yield action;
         }
         case PACKET -> new WaitForPacketAction(this.endPacketName == null ? "" : this.endPacketName);
      });
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("startEvent", this.startEvent.name());
      tag.putString("endEvent", this.legacyEndEvent().name());
      tag.putString("endCondition", this.endCondition.name());
      tag.putString("startConditionType", this.startConditionType == null ? RaceAction.TriggerType.DELAY.name() : this.startConditionType);
      tag.putString("startActionType", this.startActionType == null ? MacroActionType.ITEM.name() : this.startActionType);
      tag.putString("endConditionType", this.endConditionType == null ? RaceAction.TriggerType.WAIT_GUI.name() : this.endConditionType);
      tag.putInt("conditionCount", Math.max(0, this.conditionCount));
      tag.putString("reportLabel", this.reportLabel == null ? "" : this.reportLabel);
      tag.putString("commandText", this.commandText == null ? "" : this.commandText);
      tag.putString("endGuiTitle", this.endGuiTitle == null ? "" : this.endGuiTitle);
      tag.putString("endPacketName", this.endPacketName == null ? "" : this.endPacketName);
      tag.putString("endTargetDimension", this.endTargetDimension == null ? "" : this.endTargetDimension);
      tag.putDouble("endDistance", this.endDistance);
      tag.putBoolean("endHorizontalOnly", this.endHorizontalOnly);
      tag.putInt("timeoutMs", Math.max(100, this.timeoutMs));
      tag.putBoolean("stashToSharedState", this.stashToSharedState);
      tag.putString("guiTitleSubstr", this.endGuiTitle == null ? "" : this.endGuiTitle);
      tag.putDouble("positionTolerance", this.endDistance);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      try {
         this.startEvent = ReportAction.StartEvent.valueOf(tag.getStringOr("startEvent", ReportAction.StartEvent.ACTION_START.name()));
      } catch (IllegalArgumentException var5) {
         this.startEvent = ReportAction.StartEvent.ACTION_START;
      }

      this.reportLabel = tag.getStringOr("reportLabel", "Report");
      this.startConditionType = tag.getStringOr("startConditionType", RaceAction.TriggerType.DELAY.name());
      this.startActionType = tag.getStringOr("startActionType", MacroActionType.ITEM.name());
      this.endConditionType = tag.getStringOr("endConditionType", "");
      this.conditionCount = Math.max(0, tag.getIntOr("conditionCount", 0));
      this.commandText = tag.getStringOr("commandText", "");
      this.timeoutMs = Math.max(100, tag.getIntOr("timeoutMs", 10000));
      this.stashToSharedState = tag.getBooleanOr("stashToSharedState", true);
      this.guiTitleSubstr = tag.getStringOr("guiTitleSubstr", "");
      this.positionTolerance = tag.getDoubleOr("positionTolerance", 5.0);
      if (tag.contains("endCondition")) {
         try {
            this.endCondition = ReportAction.EndCondition.valueOf(tag.getStringOr("endCondition", ReportAction.EndCondition.GUI_OPEN.name()));
         } catch (IllegalArgumentException var4) {
            this.endCondition = ReportAction.EndCondition.GUI_OPEN;
         }

         this.endGuiTitle = tag.getStringOr("endGuiTitle", this.guiTitleSubstr);
         this.endPacketName = tag.getStringOr("endPacketName", "");
         this.endTargetDimension = tag.getStringOr("endTargetDimension", "");
         this.endDistance = tag.getDoubleOr("endDistance", this.positionTolerance);
         this.endHorizontalOnly = tag.getBooleanOr("endHorizontalOnly", false);
         if (this.endConditionType == null || this.endConditionType.isBlank()) {
            this.endConditionType = this.conditionTypeForLegacyEnd();
         }
      } else {
         try {
            this.endEvent = ReportAction.EndEvent.valueOf(tag.getStringOr("endEvent", ReportAction.EndEvent.GUI_OPEN.name()));
         } catch (IllegalArgumentException var3) {
            this.endEvent = ReportAction.EndEvent.GUI_OPEN;
         }

         this.migrateLegacyEndEvent();
      }
   }

   private void migrateLegacyEndEvent() {
      switch (this.endEvent) {
         case GUI_OPEN:
            this.endCondition = ReportAction.EndCondition.GUI_OPEN;
            this.endGuiTitle = this.guiTitleSubstr;
            break;
         case GUI_CLOSE:
            this.endCondition = ReportAction.EndCondition.GUI_CLOSE;
            this.endGuiTitle = this.guiTitleSubstr;
            break;
         case WORLD_CHANGE:
            this.endCondition = ReportAction.EndCondition.WORLD_CHANGE;
            this.endTargetDimension = "";
            break;
         case TELEPORT_PACKET:
            this.endCondition = ReportAction.EndCondition.TELEPORT_PACKET;
            break;
         case POSITION_JUMP:
            this.endCondition = ReportAction.EndCondition.POSITION_DELTA;
            this.endDistance = this.positionTolerance;
            this.endHorizontalOnly = false;
      }

      this.endConditionType = this.conditionTypeForLegacyEnd();
   }

   private ReportAction.EndEvent legacyEndEvent() {
      return switch (this.endCondition) {
         case GUI_OPEN -> ReportAction.EndEvent.GUI_OPEN;
         case GUI_CLOSE -> ReportAction.EndEvent.GUI_CLOSE;
         case TELEPORT_PACKET, PACKET -> ReportAction.EndEvent.TELEPORT_PACKET;
         case WORLD_CHANGE -> ReportAction.EndEvent.WORLD_CHANGE;
         case POSITION_DELTA -> ReportAction.EndEvent.POSITION_JUMP;
      };
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.REPORT;
   }

   @Override
   public String getDisplayName() {
      return "Report \"" + this.safeLabel() + "\" [" + this.describeStart() + " -> " + this.describeEnd() + "]";
   }

   @Override
   public String getIcon() {
      return "Rep";
   }

   private String safeLabel() {
      return this.reportLabel != null && !this.reportLabel.isBlank() ? this.reportLabel.trim() : "Report";
   }

   private String describeStart() {
      return this.startActionType != null && !this.startActionType.isBlank()
         ? this.startActionType
         : (
            this.startConditionType != null && !this.startConditionType.isBlank()
               ? this.startConditionType
               : (this.startEvent == null ? ReportAction.StartEvent.ACTION_START.name() : this.startEvent.name())
         );
   }

   private String describeEnd() {
      return this.endConditionType != null && !this.endConditionType.isBlank()
         ? this.endConditionType
         : (this.endCondition == null ? ReportAction.EndCondition.GUI_OPEN.name() : this.endCondition.name());
   }

   public MacroAction createSelectedStartConditionAction() {
      return RaceAction.createConditionAction(this.startConditionType);
   }

   public MacroAction createSelectedStartAction() {
      return RaceAction.createBodyAction(this.startActionType);
   }

   public MacroAction createSelectedEndConditionAction() {
      return RaceAction.createConditionAction(this.endConditionType);
   }

   public int normalizedConditionCount(List<MacroAction> actions, int headerIndex) {
      if (actions != null && headerIndex >= 0 && headerIndex < actions.size()) {
         int max = Math.max(0, Math.min(this.conditionCount, actions.size() - headerIndex - 1));
         int count = 0;

         for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction action = actions.get(i);
            if (action instanceof RaceAction || action instanceof ReportAction) {
               break;
            }

            count++;
         }

         return count;
      } else {
         return 0;
      }
   }

   private String conditionTypeForLegacyEnd() {
      return switch (this.endCondition) {
         case GUI_OPEN, GUI_CLOSE -> RaceAction.TriggerType.WAIT_GUI.name();
         case TELEPORT_PACKET -> RaceAction.TriggerType.WAIT_TELEPORT.name();
         case WORLD_CHANGE -> RaceAction.TriggerType.WAIT_WORLD_CHANGE.name();
         case POSITION_DELTA -> RaceAction.TriggerType.WAIT_POSITION_DELTA.name();
         case PACKET -> RaceAction.TriggerType.WAIT_PACKET.name();
      };
   }

   private static String normalizeCommand(String command) {
      if (command == null) {
         return "";
      } else {
         String trimmed = command.trim();
         return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
      }
   }

   public static enum EndCondition {
      GUI_OPEN,
      GUI_CLOSE,
      TELEPORT_PACKET,
      WORLD_CHANGE,
      POSITION_DELTA,
      PACKET;
   }

   public static enum EndEvent {
      GUI_OPEN,
      GUI_CLOSE,
      WORLD_CHANGE,
      TELEPORT_PACKET,
      POSITION_JUMP;
   }

   public static enum StartEvent {
      ACTION_START,
      COMMAND_SENT,
      GUI_CLICK_SENT,
      MANUAL;
   }
}
