package riptide.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import riptide.api.macro.MacroActionRegistry;
import riptide.util.macro.AssertAction;
import riptide.util.macro.BranchAction;
import riptide.util.macro.BreakAction;
import riptide.util.macro.BundleDupeV2Action;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.ClickAction;
import riptide.util.macro.CloseGuiAction;
import riptide.util.macro.ContainerClickSequenceAction;
import riptide.util.macro.CraftAction;
import riptide.util.macro.CustomMenuAction;
import riptide.util.macro.DelayAction;
import riptide.util.macro.DelayPacketsAction;
import riptide.util.macro.DesyncAction;
import riptide.util.macro.DisconnectAction;
import riptide.util.macro.DropAction;
import riptide.util.macro.EndPacketGateAction;
import riptide.util.macro.FakeGamemodeAction;
import riptide.util.macro.FinallyAction;
import riptide.util.macro.FlowAction;
import riptide.util.macro.FpsAction;
import riptide.util.macro.GoToAction;
import riptide.util.macro.HClipAction;
import riptide.util.macro.IfAction;
import riptide.util.macro.InstaBreakAction;
import riptide.util.macro.InteractEntityAction;
import riptide.util.macro.InventoryAction;
import riptide.util.macro.InventoryAuditAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.JumpAction;
import riptide.util.macro.LabelAction;
import riptide.util.macro.LookAtBlockAction;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;
import riptide.util.macro.MacroDynamicBindings;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.MacroVariablesAction;
import riptide.util.macro.MineAction;
import riptide.util.macro.MissingAddonAction;
import riptide.util.macro.MoveAction;
import riptide.util.macro.MultiAction;
import riptide.util.macro.NbtBookAction;
import riptide.util.macro.OpenContainerAction;
import riptide.util.macro.PacedTpAction;
import riptide.util.macro.PacketAction;
import riptide.util.macro.PacketBurstAction;
import riptide.util.macro.PacketClickAction;
import riptide.util.macro.PacketGateAction;
import riptide.util.macro.PayAction;
import riptide.util.macro.PayloadAction;
import riptide.util.macro.PickUpAllAction;
import riptide.util.macro.PlaceAction;
import riptide.util.macro.RaceAction;
import riptide.util.macro.RepeatAction;
import riptide.util.macro.ReportAction;
import riptide.util.macro.RestoreGuiAction;
import riptide.util.macro.RevisionSyncAction;
import riptide.util.macro.RollbackAction;
import riptide.util.macro.RotateAction;
import riptide.util.macro.SPingAction;
import riptide.util.macro.SaveGuiAction;
import riptide.util.macro.SelectSlotAction;
import riptide.util.macro.SendChatAction;
import riptide.util.macro.SendCommandPacketAction;
import riptide.util.macro.SendPacketAction;
import riptide.util.macro.SendToggleAction;
import riptide.util.macro.ServerTickSyncAction;
import riptide.util.macro.SignEditAction;
import riptide.util.macro.SneakAction;
import riptide.util.macro.SprintAction;
import riptide.util.macro.StartMacroAction;
import riptide.util.macro.StopMacroAction;
import riptide.util.macro.StoreItemAction;
import riptide.util.macro.SwapSlotsAction;
import riptide.util.macro.TickSyncAction;
import riptide.util.macro.ToggleModuleAction;
import riptide.util.macro.UseItemAction;
import riptide.util.macro.UseItemPhaseAction;
import riptide.util.macro.VClipAction;
import riptide.util.macro.WaitDurabilityAction;
import riptide.util.macro.WaitEntityTargetAction;
import riptide.util.macro.WaitForBlockAction;
import riptide.util.macro.WaitForChatAction;
import riptide.util.macro.WaitForCooldownAction;
import riptide.util.macro.WaitForEntityAction;
import riptide.util.macro.WaitForGuiAction;
import riptide.util.macro.WaitForHealthAction;
import riptide.util.macro.WaitForLanStepAction;
import riptide.util.macro.WaitForMacroStepAction;
import riptide.util.macro.WaitForPacketAction;
import riptide.util.macro.WaitForPositionDeltaAction;
import riptide.util.macro.WaitForSlotChangeAction;
import riptide.util.macro.WaitForSoundAction;
import riptide.util.macro.WaitForTeleportAction;
import riptide.util.macro.WaitForWorldChangeAction;
import riptide.util.macro.WaitFreeSlotsAction;
import riptide.util.macro.WaitGamemodeChangeAction;
import riptide.util.macro.WaitGuiTypeAction;
import riptide.util.macro.WaitInventoryPredicateAction;
import riptide.util.macro.WaitMovementAction;
import riptide.util.macro.WaitPacketMatchAction;
import riptide.util.macro.WaitPosAction;
import riptide.util.macro.XCarryAction;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiTakeoverState;

public class RiptideMacro {
   public String name = "New Macro";
   public String description = "";
   public boolean loop = false;
   public int loopCount = -1;
   public int keyCode = -1;
   public List<MacroAction> actions = new ArrayList<>();
   private static final Set<String> BUILT_IN_ACTION_TYPES;

   public RiptideMacro() {
   }

   public RiptideMacro(String name) {
      this.name = name;
   }

   public RiptideMacro deepCopy() {
      return new RiptideMacro().fromTag(this.toTag());
   }

   public RiptideMacro deepCopy(String newName) {
      RiptideMacro copy = this.deepCopy();
      if (newName != null && !newName.isBlank()) {
         copy.name = newName;
      }

      return copy;
   }

   public RiptideMacro sanitizeForSharing() {
      if (this.actions != null) {
         for (MacroAction action : this.actions) {
            if (action != null) {
               action.sanitizeForSharing();
            }
         }
      }

      return this;
   }

   public CompoundTag toShareableTag() {
      return this.deepCopy().sanitizeForSharing().toTag();
   }

   public void execute() {
      this.execute(true);
   }

   public void execute(boolean regenerate) {
      this.executeTracked(regenerate);
   }

   public long executeTracked() {
      return this.executeTracked(true);
   }

   public long executeTracked(boolean regenerate) {
      if (this.actions != null && !this.actions.isEmpty()) {
         if (regenerate) {
            this.regenerateAllPackets();
         }

         if (MultiTakeoverState.isActive()) {
            MultiManager multi = MultiManager.getIfInitialized();
            if (multi != null && multi.isActive()) {
               MultiManager.BroadcastResult result = multi.runMacroDirectInteractive(this, Set.of());
               RiptideNotifications.show("Run on POV: " + result.summary() + ".", -11013497);
               return -1L;
            } else {
               RiptideNotifications.error("The POV Multi session is no longer active.");
               return -1L;
            }
         } else {
            return MacroExecutor.executeTracked(this);
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cMacro has no actions!");
         return -1L;
      }
   }

   public void regenerateAllPackets() {
      if (this.actions != null) {
         for (MacroAction action : this.actions) {
            if (action instanceof SendPacketAction) {
               ((SendPacketAction)action).regeneratePackets();
            }
         }
      }
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("name", this.name);
      tag.putString("description", this.description);
      tag.putBoolean("loop", this.loop);
      tag.putInt("loopCount", this.loopCount);
      tag.putInt("keyCode", this.keyCode);
      ListTag actionsList = new ListTag();
      if (this.actions != null) {
         for (MacroAction action : this.actions) {
            if (action != null) {
               actionsList.add(serializeAction(action));
            }
         }
      }

      tag.put("actions", actionsList);
      return tag;
   }

   public RiptideMacro fromTag(CompoundTag tag) {
      if (tag.contains("name")) {
         this.name = tag.getStringOr("name", "");
      }

      if (tag.contains("description")) {
         this.description = tag.getStringOr("description", "");
      }

      if (tag.contains("loop")) {
         this.loop = tag.getBooleanOr("loop", false);
      }

      if (tag.contains("loopCount")) {
         this.loopCount = tag.getIntOr("loopCount", -1);
      }

      if (tag.contains("keyCode")) {
         this.keyCode = tag.getIntOr("keyCode", -1);
      }

      if (tag.get("actions") instanceof ListTag actionsList) {
         List<MacroAction> loadedActions = new ArrayList<>();

         for (Tag element : actionsList) {
            if (element instanceof CompoundTag actionTag) {
               MacroAction action = createActionFromTag(actionTag);
               if (action != null) {
                  loadedActions.add(action);
               }
            }
         }

         this.actions = loadedActions;
         this.migrateLegacyRaceGroups();
      }

      return this;
   }

   public static MacroAction createActionFromTag(CompoundTag actionTag) {
      if (actionTag != null && actionTag.contains("type")) {
         try {
            String typeName = actionTag.getStringOr("type", "");
            MacroAction migratedLegacy = createHardDebloatMigration(typeName, actionTag);
            if (migratedLegacy != null) {
               return migratedLegacy;
            } else {
               MacroActionType type = MacroActionType.valueOf(typeName);
               MacroAction action = createBuiltInAction(type);
               if (type == MacroActionType.WAIT_ITEM) {
                  ((WaitForSlotChangeAction)action).fromTagLegacyItem(actionTag);
               } else {
                  action.fromTag(actionTag);
               }

               MacroDynamicBindings.load(action, actionTag);
               return action;
            }
         } catch (IllegalArgumentException var5) {
            return recoverUnknownAction(actionTag);
         } catch (Throwable var6) {
            String typeNamex = actionTag.getStringOr("type", "");
            riptide.RiptideClientAddon.LOG.warn("[Macros] Failed to deserialize action '{}'; preserving its data", typeNamex, var6);
            return new MissingAddonAction(typeNamex.isBlank() ? "invalid:action" : typeNamex, actionTag.copy());
         }
      } else {
         return null;
      }
   }

   public static CompoundTag serializeAction(MacroAction action) {
      CompoundTag tag = action == null ? new CompoundTag() : action.toTag();
      MacroDynamicBindings.write(action, tag);
      return tag;
   }

   public static boolean isBuiltInActionType(String type) {
      return type != null && BUILT_IN_ACTION_TYPES.contains(type);
   }

   public static int stripToBuiltInActions(CompoundTag macroTag) {
      if (macroTag == null) {
         return 0;
      } else if (!(macroTag.get("actions") instanceof ListTag actionsList)) {
         return 0;
      } else {
         ListTag var7 = new ListTag();
         int dropped = 0;

         for (Tag element : actionsList) {
            if (element instanceof CompoundTag actionTag && isBuiltInActionType(actionTag.getStringOr("type", ""))) {
               var7.add(actionTag);
            } else {
               dropped++;
            }
         }

         if (dropped > 0) {
            macroTag.put("actions", var7);
         }

         return dropped;
      }
   }

   private static MacroAction createBuiltInAction(MacroActionType type) {
      return (MacroAction)(switch (type) {
         case DELAY -> new DelayAction();
         case PACKET -> new PacketAction();
         case PACKET_CLICK -> new PacketClickAction();
         case WAIT_PACKET -> new WaitForPacketAction();
         case WAIT_HEALTH -> new WaitForHealthAction();
         case WAIT_ITEM, WAIT_SLOT_CHANGE -> new WaitForSlotChangeAction();
         case WAIT_BLOCK -> new WaitForBlockAction();
         case WAIT_GUI -> new WaitForGuiAction();
         case CUSTOM_MENU -> new CustomMenuAction();
         case CLICK -> new ClickAction();
         case ROTATE -> new RotateAction();
         case USE_ITEM -> new UseItemAction();
         case INVENTORY -> new InventoryAction();
         case SEND_PACKET -> new SendPacketAction();
         case CRAFT -> new CraftAction();
         case SELECT_SLOT -> new SelectSlotAction();
         case XCARRY -> new XCarryAction();
         case DROP -> new DropAction();
         case ITEM -> new ItemAction();
         case PICK_UP_ALL -> new PickUpAllAction();
         case TICK_SYNC -> new TickSyncAction();
         case REVISION_SYNC -> new RevisionSyncAction();
         case SERVER_TICK_SYNC -> new ServerTickSyncAction();
         case CLOSE_GUI -> new CloseGuiAction();
         case SWAP_SLOTS -> new SwapSlotsAction();
         case WAIT_COOLDOWN -> new WaitForCooldownAction();
         case GO_TO -> new GoToAction();
         case WAIT_POS -> new WaitPosAction();
         case PAYLOAD -> new PayloadAction();
         case DISCONNECT -> new DisconnectAction();
         case TOGGLE_MODULE -> new ToggleModuleAction();
         case START_MACRO -> new StartMacroAction();
         case STOP_MACRO -> new StopMacroAction();
         case SNEAK -> new SneakAction();
         case JUMP -> new JumpAction();
         case SPRINT -> new SprintAction();
         case MOVE -> new MoveAction();
         case LOOK_AT_BLOCK -> new LookAtBlockAction();
         case REPEAT -> new RepeatAction();
         case WAIT_CHAT -> new WaitForChatAction();
         case WAIT_ENTITY -> new WaitForEntityAction();
         case OPEN_CONTAINER -> new OpenContainerAction();
         case INTERACT_ENTITY -> new InteractEntityAction();
         case DESYNC -> new DesyncAction();
         case RESTORE_GUI -> new RestoreGuiAction();
         case SAVE_GUI -> new SaveGuiAction();
         case SEND_TOGGLE -> new SendToggleAction();
         case DELAY_PACKETS -> new DelayPacketsAction();
         case INVENTORY_AUDIT -> new InventoryAuditAction();
         case STORE_ITEM -> new StoreItemAction();
         case WAIT_SOUND -> new WaitForSoundAction();
         case MINE -> new MineAction();
         case INSTA_BREAK -> new InstaBreakAction();
         case BREAK -> new BreakAction();
         case PAY -> new PayAction();
         case NBT_BOOK -> new NbtBookAction();
         case SEND_CHAT -> new SendChatAction();
         case WAIT_LAN_STEP -> new WaitForLanStepAction();
         case WAIT_MACRO_STEP -> new WaitForMacroStepAction();
         case WAIT_WORLD_CHANGE -> new WaitForWorldChangeAction();
         case WAIT_POSITION_DELTA -> new WaitForPositionDeltaAction();
         case WAIT_TELEPORT -> new WaitForTeleportAction();
         case WAIT_GAMEMODE_CHANGE -> new WaitGamemodeChangeAction();
         case WAIT_MOVEMENT -> new WaitMovementAction();
         case RACE -> new RaceAction();
         case REPORT -> new ReportAction();
         case VCLIP -> new VClipAction();
         case HCLIP -> new HClipAction();
         case TP -> new PacedTpAction();
         case PACKET_GATE -> new PacketGateAction();
         case END_PACKET_GATE -> new EndPacketGateAction();
         case PACKET_BURST -> new PacketBurstAction();
         case CONTAINER_CLICK_SEQUENCE -> new ContainerClickSequenceAction();
         case ASSERT -> new AssertAction();
         case USE_ITEM_PHASE -> new UseItemPhaseAction();
         case SEND_COMMAND_PACKET -> new SendCommandPacketAction();
         case WAIT_PACKET_MATCH -> new WaitPacketMatchAction();
         case WAIT_INVENTORY_PREDICATE -> new WaitInventoryPredicateAction();
         case WAIT_DURABILITY -> new WaitDurabilityAction();
         case WAIT_FREE_SLOTS -> new WaitFreeSlotsAction();
         case WAIT_ENTITY_TARGET -> new WaitEntityTargetAction();
         case WAIT_GUI_TYPE -> new WaitGuiTypeAction();
         case BRANCH -> new BranchAction();
         case FINALLY -> new FinallyAction();
         case MACRO_VARIABLES -> new MacroVariablesAction();
         case CAPTURE_VALUE -> new CaptureValueAction();
         case FAKE_GAMEMODE -> new FakeGamemodeAction();
         case BUNDLE_DUPE_V2 -> new BundleDupeV2Action();
         case ROLLBACK -> new RollbackAction();
         case PLACE -> new PlaceAction();
         case SIGN_EDIT -> new SignEditAction();
         case FPS -> new FpsAction();
         case SPING -> new SPingAction();
         case IF -> new IfAction();
         case FLOW -> new FlowAction();
         case LABEL -> new LabelAction();
         case MULTI -> new MultiAction();
      });
   }

   private static MacroAction recoverUnknownAction(CompoundTag actionTag) {
      if (actionTag == null) {
         return null;
      } else {
         String typeName = actionTag.getStringOr("type", "");
         if (typeName.isEmpty()) {
            return null;
         } else {
            Supplier<MacroAction> factory = MacroActionRegistry.factory(typeName);
            if (factory != null) {
               try {
                  MacroAction action = factory.get();
                  if (action != null) {
                     action.fromTag(actionTag);
                     MacroDynamicBindings.load(action, actionTag);
                     return action;
                  }
               } catch (Throwable var4) {
                  riptide.RiptideClientAddon.LOG.warn("[MacroActions] Addon action '{}' failed to deserialize; keeping as placeholder", typeName, var4);
               }
            }

            return new MissingAddonAction(typeName, actionTag.copy());
         }
      }
   }

   private static MacroAction createHardDebloatMigration(String typeName, CompoundTag actionTag) {
      if ("WAIT_ENTITY_TARGET".equals(typeName)) {
         WaitForEntityAction migrated = new WaitForEntityAction();
         migrated.fromLegacyTargetTag(actionTag);
         return migrated;
      } else if ("WAIT_GUI_TYPE".equals(typeName)) {
         WaitForGuiAction migrated = new WaitForGuiAction();
         migrated.fromGuiTypeTag(actionTag);
         return migrated;
      } else {
         return null;
      }
   }

   private void migrateLegacyRaceGroups() {
      if (this.actions != null && !this.actions.isEmpty()) {
         for (int i = 0; i < this.actions.size(); i++) {
            if (this.actions.get(i) instanceof RaceAction race && race.needsLegacyMigration()) {
               int inserted = 0;
               PacketClickAction packetClick = race.consumeLegacyPacketClickAction();
               if (packetClick != null) {
                  this.actions.add(i + 1, packetClick);
                  inserted++;
               }

               if (race.legacyUseNextAction()) {
                  for (int j = i + 1 + inserted; j < this.actions.size(); j++) {
                     MacroAction candidate = this.actions.get(j);
                     if (!RaceAction.isBodyAction(candidate)) {
                        break;
                     }

                     inserted++;
                  }
               }

               race.bodyCount = inserted;
               race.clearLegacyMigration();
               i += inserted;
            }
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         RiptideMacro that = (RiptideMacro)o;
         return MacroNames.equal(this.name, that.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(MacroNames.key(this.name));
   }

   static {
      Set<String> s = new HashSet<>();

      for (MacroActionType t : MacroActionType.values()) {
         s.add(t.name());
      }

      BUILT_IN_ACTION_TYPES = Set.copyOf(s);
   }
}
