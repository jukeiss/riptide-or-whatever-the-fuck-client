package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import riptide.util.RiptideMacro;
import riptide.util.RiptidePacketClick;

public class RaceAction implements MacroAction {
   public String label = "Race";
   public MacroAction triggerAction = new DelayAction(0);
   public int bodyCount = 0;
   public int timeoutMs = 10000;
   public String conditionType = RaceAction.TriggerType.WAIT_PACKET.name();
   public String actionType = MacroActionType.PACKET_CLICK.name();
   public ArrayList<String> raceSteps = new ArrayList<>();
   public ArrayList<String> conditionTypes = new ArrayList<>();
   public ArrayList<String> actionTypes = new ArrayList<>();
   private transient boolean legacyNeedsMigration = false;
   private transient boolean legacyUseNextAction = false;
   private transient RiptidePacketClick.Target legacyTarget = null;
   private transient int legacyTimes = 1;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("label", this.label == null ? "" : this.label);
      tag.putInt("bodyCount", Math.max(0, this.bodyCount));
      tag.putInt("timeoutMs", Math.max(0, this.timeoutMs));
      tag.putString("conditionType", this.conditionType == null ? RaceAction.TriggerType.WAIT_PACKET.name() : this.conditionType);
      tag.putString("actionType", this.actionType == null ? MacroActionType.PACKET_CLICK.name() : this.actionType);
      tag.put("raceSteps", MacroStringList.toTag(this.effectiveRaceSteps()));
      tag.put("conditionTypes", MacroStringList.toTag(this.effectiveConditionTypes()));
      tag.put("actionTypes", MacroStringList.toTag(this.effectiveActionTypes()));
      MacroAction trigger = (MacroAction)(this.triggerAction == null ? new DelayAction(0) : this.triggerAction);
      if (!this.effectiveConditionTypes().isEmpty()) {
         tag.put("triggerAction", trigger.toTag());
         writeFlatTriggerFields(tag, trigger);
      }

      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.label = tag.getStringOr("label", "Race");
      this.bodyCount = Math.max(0, tag.getIntOr("bodyCount", 0));
      this.timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 10000));
      this.conditionType = canonicalTriggerName(tag.getStringOr("conditionType", tag.getStringOr("triggerType", RaceAction.TriggerType.WAIT_PACKET.name())));
      this.actionType = tag.getStringOr("actionType", MacroActionType.PACKET_CLICK.name());
      this.raceSteps = MacroStringList.fromTag(tag.getList("raceSteps").orElse(new ListTag()));
      this.raceSteps.removeIf(s -> s == null || s.isBlank());
      boolean hasConditionTypes = tag.contains("conditionTypes");
      this.conditionTypes = MacroStringList.fromTag(tag.getList("conditionTypes").orElse(new ListTag()));
      this.conditionTypes.removeIf(s -> s == null || s.isBlank());
      if (!hasConditionTypes && this.conditionType != null && !this.conditionType.isBlank()) {
         this.conditionTypes.add(this.conditionType);
      }

      this.actionTypes = MacroStringList.fromTag(tag.getList("actionTypes").orElse(new ListTag()));
      if (this.actionTypes.isEmpty() && this.actionType != null && !this.actionType.isBlank()) {
         this.actionTypes.add(this.actionType);
      }

      this.actionTypes.removeIf(s -> s == null || s.isBlank());
      if (this.actionTypes.isEmpty()) {
         this.actionTypes.add(MacroActionType.PACKET_CLICK.name());
      }

      this.conditionTypes.replaceAll(RaceAction::canonicalTriggerName);
      if (this.raceSteps.isEmpty()) {
         for (String type : this.conditionTypes) {
            this.raceSteps.add("CONDITION:" + canonicalTriggerName(type));
         }

         for (String type : this.actionTypes) {
            this.raceSteps.add("ACTION:" + canonicalActionName(type));
         }
      } else {
         this.raceSteps.replaceAll(RaceAction::canonicalRaceStep);
         this.raceSteps.removeIf(s -> s == null || s.isBlank());
         this.syncLegacyListsFromRaceSteps();
         if (this.actionTypes.isEmpty()) {
            this.actionTypes.add(MacroActionType.PACKET_CLICK.name());
         }
      }

      this.conditionType = this.conditionTypes.isEmpty() ? "" : this.conditionTypes.get(0);
      this.actionType = this.actionTypes.get(0);
      MacroAction nested = tag.getCompound("triggerAction").map(RiptideMacro::createActionFromTag).orElse(null);
      if (isConditionAction(nested)) {
         this.triggerAction = nested;
      }

      if (tag.contains("triggerType")) {
         this.triggerAction = triggerFromFlatFields(tag);
      } else if (tag.contains("trigger")) {
         this.triggerAction = migrateLegacyTrigger(tag);
         this.legacyNeedsMigration = true;
         this.legacyUseNextAction = tag.getBooleanOr("useNextAction", false);
         this.legacyTimes = Math.max(1, tag.getIntOr("times", 1));
         this.legacyTarget = tag.getCompound("target").map(RiptidePacketClick.Target::fromTag).orElse(null);
      }

      if (!isConditionAction(this.triggerAction)) {
         this.triggerAction = new DelayAction(0);
      }
   }

   public boolean needsLegacyMigration() {
      return this.legacyNeedsMigration;
   }

   public boolean legacyUseNextAction() {
      return this.legacyUseNextAction;
   }

   public PacketClickAction consumeLegacyPacketClickAction() {
      if (this.legacyTarget == null) {
         return null;
      } else {
         PacketClickAction action = new PacketClickAction(this.legacyTarget, this.legacyTimes, false);
         this.legacyTarget = null;
         return action;
      }
   }

   public void clearLegacyMigration() {
      this.legacyNeedsMigration = false;
      this.legacyUseNextAction = false;
      this.legacyTarget = null;
   }

   public static boolean isConditionAction(MacroAction action) {
      return action instanceof DelayAction
         || action instanceof WaitForPacketAction
         || action instanceof WaitForHealthAction
         || action instanceof WaitForBlockAction
         || action instanceof WaitForGuiAction
         || action instanceof WaitForCooldownAction
         || action instanceof WaitPosAction
         || action instanceof WaitForChatAction
         || action instanceof WaitForEntityAction
         || action instanceof WaitForSlotChangeAction
         || action instanceof WaitForSoundAction
         || action instanceof WaitPacketMatchAction
         || action instanceof WaitInventoryPredicateAction
         || action instanceof WaitDurabilityAction
         || action instanceof WaitFreeSlotsAction
         || action instanceof WaitMovementAction
         || action instanceof WaitForLanStepAction
         || action instanceof WaitForMacroStepAction
         || action instanceof TickSyncAction
         || action instanceof RevisionSyncAction
         || action instanceof ServerTickSyncAction
         || action instanceof WaitForWorldChangeAction
         || action instanceof WaitForPositionDeltaAction
         || action instanceof WaitForTeleportAction
         || action instanceof WaitGamemodeChangeAction;
   }

   public static boolean isBodyAction(MacroAction action) {
      if (action == null) {
         return false;
      } else {
         return isConditionAction(action)
            ? false
            : !(action instanceof RaceAction)
               && !(action instanceof ReportAction)
               && !(action instanceof MultiAction)
               && !(action instanceof RepeatAction)
               && !(action instanceof DisconnectAction)
               && !(action instanceof GoToAction)
               && !(action instanceof MoveAction);
      }
   }

   public MacroAction createSelectedConditionAction() {
      return defaultTrigger(parseTriggerType(this.conditionType));
   }

   public List<String> effectiveConditionTypes() {
      ArrayList<String> out = new ArrayList<>();
      if (this.conditionTypes != null) {
         for (String type : this.conditionTypes) {
            String canonical = canonicalTriggerName(type);
            if (canonical != null && !canonical.isBlank()) {
               out.add(canonical);
            }
         }
      }

      return out;
   }

   public List<MacroAction> createSelectedConditionActions() {
      ArrayList<MacroAction> out = new ArrayList<>();

      for (String typeName : this.effectiveConditionTypes()) {
         out.add(createConditionAction(typeName));
      }

      return out;
   }

   public MacroAction createSelectedBodyAction() {
      return createBodyAction(this.actionType);
   }

   public List<String> effectiveRaceSteps() {
      ArrayList<String> out = new ArrayList<>();
      if (this.raceSteps != null) {
         for (String step : this.raceSteps) {
            String canonical = canonicalRaceStep(step);
            if (!canonical.isBlank()) {
               out.add(canonical);
            }
         }
      }

      if (out.isEmpty()) {
         for (String type : this.effectiveConditionTypes()) {
            out.add("CONDITION:" + type);
         }

         for (String type : this.effectiveActionTypes()) {
            out.add("ACTION:" + type);
         }
      }

      return out;
   }

   public List<String> effectiveActionTypes() {
      ArrayList<String> out = new ArrayList<>();
      if (this.actionTypes != null) {
         for (String type : this.actionTypes) {
            if (type != null && !type.isBlank()) {
               out.add(type.trim().toUpperCase(Locale.ROOT));
            }
         }
      }

      if (out.isEmpty()) {
         out.add(this.actionType != null && !this.actionType.isBlank() ? this.actionType.trim().toUpperCase(Locale.ROOT) : MacroActionType.PACKET_CLICK.name());
      }

      return out;
   }

   public List<MacroAction> createSelectedBodyActions() {
      ArrayList<MacroAction> out = new ArrayList<>();

      for (String typeName : this.effectiveActionTypes()) {
         out.add(createBodyAction(typeName));
      }

      return out;
   }

   public static boolean isConditionStep(String step) {
      return step != null && step.trim().toUpperCase(Locale.ROOT).startsWith("CONDITION:");
   }

   public static boolean isActionStep(String step) {
      return step != null && step.trim().toUpperCase(Locale.ROOT).startsWith("ACTION:");
   }

   public static String stepTypeName(String step) {
      if (step == null) {
         return "";
      } else {
         int idx = step.indexOf(58);
         return (idx >= 0 ? step.substring(idx + 1) : step).trim().toUpperCase(Locale.ROOT);
      }
   }

   public static MacroAction createStepAction(String step) {
      return isConditionStep(step) ? createConditionAction(stepTypeName(step)) : createBodyAction(stepTypeName(step));
   }

   public static MacroAction createBodyAction(String typeName) {
      MacroActionType type;
      try {
         type = MacroActionType.valueOf(typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var3) {
         type = MacroActionType.PACKET_CLICK;
      }
      MacroAction action = (MacroAction)(switch (type) {
         case PACKET_CLICK -> new PacketClickAction();
         case SEND_CHAT -> new SendChatAction();
         case PAYLOAD -> new PayloadAction();
         case CLOSE_GUI -> new CloseGuiAction();
         case CLICK -> new ClickAction();
         case USE_ITEM -> new UseItemAction();
         case ITEM -> new ItemAction();
         case PICK_UP_ALL -> new PickUpAllAction();
         case XCARRY -> new XCarryAction();
         case DROP -> new DropAction();
         case SELECT_SLOT -> new SelectSlotAction();
         case SWAP_SLOTS -> new SwapSlotsAction();
         case SEND_PACKET -> new SendPacketAction();
         case INVENTORY -> new InventoryAction();
         case RESTORE_GUI -> new RestoreGuiAction();
         case SAVE_GUI -> new SaveGuiAction();
         case DESYNC -> new DesyncAction();
         case NBT_BOOK -> new NbtBookAction();
         case PAY -> new PayAction();
         case INSTA_BREAK -> new InstaBreakAction();
         case BREAK -> new BreakAction();
         case TOGGLE_MODULE -> new ToggleModuleAction();
         case START_MACRO -> new StartMacroAction();
         case STOP_MACRO -> new StopMacroAction();
         case SNEAK -> new SneakAction();
         case JUMP -> new JumpAction();
         case SPRINT -> new SprintAction();
         case PACKET_GATE -> new PacketGateAction();
         case PACKET_BURST -> new PacketBurstAction();
         case CONTAINER_CLICK_SEQUENCE -> new ContainerClickSequenceAction();
         case ASSERT -> new AssertAction();
         case USE_ITEM_PHASE -> new UseItemPhaseAction();
         case SEND_COMMAND_PACKET -> new SendCommandPacketAction();
         case SIGN_EDIT -> new SignEditAction();
         case MACRO_VARIABLES -> new MacroVariablesAction();
         case FAKE_GAMEMODE -> new FakeGamemodeAction();
         case BUNDLE_DUPE_V2 -> new BundleDupeV2Action();
         case VCLIP -> new VClipAction();
         case HCLIP -> new HClipAction();
         case TP -> new PacedTpAction();
         case MINE -> new MineAction();
         default -> new PacketClickAction();
      });
      return (MacroAction)(isBodyAction(action) ? action : new PacketClickAction());
   }

   public static RaceAction.TriggerType triggerTypeFor(MacroAction action) {
      if (action instanceof WaitForGuiAction) {
         return RaceAction.TriggerType.WAIT_GUI;
      } else if (action instanceof WaitForChatAction) {
         return RaceAction.TriggerType.WAIT_CHAT;
      } else if (action instanceof WaitForPacketAction) {
         return RaceAction.TriggerType.WAIT_PACKET;
      } else if (action instanceof WaitForHealthAction) {
         return RaceAction.TriggerType.WAIT_HEALTH;
      } else if (action instanceof WaitForSlotChangeAction) {
         return RaceAction.TriggerType.WAIT_SLOT_CHANGE;
      } else if (action instanceof WaitForBlockAction) {
         return RaceAction.TriggerType.WAIT_BLOCK;
      } else if (action instanceof WaitForEntityAction) {
         return RaceAction.TriggerType.WAIT_ENTITY;
      } else if (action instanceof WaitForCooldownAction) {
         return RaceAction.TriggerType.WAIT_COOLDOWN;
      } else if (action instanceof WaitForSoundAction) {
         return RaceAction.TriggerType.WAIT_SOUND;
      } else if (action instanceof WaitPacketMatchAction) {
         return RaceAction.TriggerType.WAIT_PACKET_MATCH;
      } else if (action instanceof WaitInventoryPredicateAction) {
         return RaceAction.TriggerType.WAIT_INVENTORY_PREDICATE;
      } else if (action instanceof WaitDurabilityAction) {
         return RaceAction.TriggerType.WAIT_DURABILITY;
      } else if (action instanceof WaitFreeSlotsAction) {
         return RaceAction.TriggerType.WAIT_FREE_SLOTS;
      } else if (action instanceof WaitMovementAction) {
         return RaceAction.TriggerType.WAIT_MOVEMENT;
      } else if (action instanceof WaitForLanStepAction) {
         return RaceAction.TriggerType.WAIT_LAN_STEP;
      } else if (action instanceof WaitForMacroStepAction) {
         return RaceAction.TriggerType.WAIT_MACRO_STEP;
      } else if (action instanceof WaitGamemodeChangeAction) {
         return RaceAction.TriggerType.WAIT_GAMEMODE_CHANGE;
      } else if (action instanceof WaitPosAction
         || action instanceof WaitForWorldChangeAction
         || action instanceof WaitForPositionDeltaAction
         || action instanceof WaitForTeleportAction) {
         return RaceAction.TriggerType.WAIT_MOVEMENT;
      } else if (action instanceof TickSyncAction) {
         return RaceAction.TriggerType.TICK_SYNC;
      } else if (action instanceof RevisionSyncAction) {
         return RaceAction.TriggerType.REVISION_SYNC;
      } else {
         return action instanceof ServerTickSyncAction ? RaceAction.TriggerType.SERVER_TICK_SYNC : RaceAction.TriggerType.DELAY;
      }
   }

   public static MacroAction createConditionAction(String typeName) {
      return defaultTrigger(parseTriggerType(typeName));
   }

   private static MacroAction defaultTrigger(RaceAction.TriggerType type) {
      return (MacroAction)(switch (type) {
         case DELAY -> new DelayAction(0);
         case WAIT_GUI -> new WaitForGuiAction();
         case WAIT_CHAT -> new WaitForChatAction();
         case WAIT_PACKET -> new WaitForPacketAction();
         case WAIT_HEALTH -> new WaitForHealthAction();
         case WAIT_SLOT_CHANGE -> new WaitForSlotChangeAction();
         case WAIT_BLOCK -> new WaitForBlockAction();
         case WAIT_ENTITY -> new WaitForEntityAction();
         case WAIT_COOLDOWN -> new WaitForCooldownAction();
         case WAIT_POS -> new WaitPosAction();
         case WAIT_SOUND -> new WaitForSoundAction();
         case WAIT_PACKET_MATCH -> new WaitPacketMatchAction();
         case WAIT_INVENTORY_PREDICATE -> new WaitInventoryPredicateAction();
         case WAIT_DURABILITY -> new WaitDurabilityAction();
         case WAIT_FREE_SLOTS -> new WaitFreeSlotsAction();
         case WAIT_MOVEMENT -> new WaitMovementAction();
         case WAIT_LAN_STEP -> new WaitForLanStepAction();
         case WAIT_MACRO_STEP -> new WaitForMacroStepAction();
         case TICK_SYNC -> new TickSyncAction();
         case REVISION_SYNC -> new RevisionSyncAction();
         case SERVER_TICK_SYNC -> new ServerTickSyncAction();
         case WAIT_WORLD_CHANGE -> new WaitForWorldChangeAction();
         case WAIT_POSITION_DELTA -> new WaitForPositionDeltaAction();
         case WAIT_TELEPORT -> new WaitForTeleportAction();
         case WAIT_GAMEMODE_CHANGE -> new WaitGamemodeChangeAction();
      });
   }

   private static MacroAction triggerFromFlatFields(CompoundTag tag) {
      RaceAction.TriggerType type = parseTriggerType(tag.getStringOr("triggerType", RaceAction.TriggerType.DELAY.name()));
      MacroAction action = defaultTrigger(type);
      CompoundTag actionTag = RiptideMacro.serializeAction(action);
      switch (type) {
         case DELAY:
            actionTag.putBoolean("useTicks", tag.getBooleanOr("useTicks", false));
            actionTag.putInt("delayMs", tag.getIntOr("delayMs", 0));
            actionTag.putInt("delayTicks", tag.getIntOr("delayTicks", 1));
            break;
         case WAIT_GUI:
            actionTag.putString("waitMode", tag.getStringOr("waitMode", "OPEN"));
            actionTag.putString("guiTitle", tag.getStringOr("guiTitle", ""));
            break;
         case WAIT_CHAT:
            actionTag.putString("pattern", tag.getStringOr("pattern", ""));
            actionTag.putBoolean("useRegex", tag.getBooleanOr("useRegex", false));
            actionTag.putInt("fuzzyPercent", tag.getIntOr("fuzzyPercent", 100));
            break;
         case WAIT_PACKET:
            actionTag.putString("packetName", tag.getStringOr("packetName", ""));
            copyStringList(tag, actionTag, "packetNames");
            break;
         case WAIT_HEALTH:
            actionTag.putFloat("healthThreshold", (float)tag.getDoubleOr("healthThreshold", 20.0));
            actionTag.putString("comparison", tag.getStringOr("comparison", "Drops Below"));
         case WAIT_SLOT_CHANGE:
         case WAIT_PACKET_MATCH:
         case WAIT_INVENTORY_PREDICATE:
         case WAIT_DURABILITY:
         case WAIT_FREE_SLOTS:
         case WAIT_MOVEMENT:
         case WAIT_LAN_STEP:
         case WAIT_MACRO_STEP:
         case TICK_SYNC:
         case REVISION_SYNC:
         case SERVER_TICK_SYNC:
         case WAIT_WORLD_CHANGE:
         default:
            break;
         case WAIT_BLOCK:
            actionTag.putString("checkMode", tag.getStringOr("checkMode", "AT_POSITION"));
            actionTag.putString("waitBehavior", tag.getStringOr("waitBehavior", "PRESENT"));
            actionTag.putBoolean("anyBlock", tag.getBooleanOr("anyBlock", false));
            copyStringList(tag, actionTag, "blockIds");
            actionTag.putInt("x", tag.getIntOr("blockX", tag.getIntOr("x", 0)));
            actionTag.putInt("y", tag.getIntOr("blockY", tag.getIntOr("y", 0)));
            actionTag.putInt("z", tag.getIntOr("blockZ", tag.getIntOr("z", 0)));
            actionTag.putDouble("searchRadius", tag.getDoubleOr("searchRadius", 6.0));
            break;
         case WAIT_ENTITY:
            actionTag.putString("checkMode", tag.getStringOr("entityCheckMode", "RADIUS"));
            copyStringList(tag, actionTag, "entityIds");
            actionTag.putBoolean("centerOnPlayer", tag.getBooleanOr("centerOnPlayer", true));
            actionTag.putDouble("radius", tag.getDoubleOr("radius", 8.0));
            actionTag.putDouble("x", tag.getDoubleOr("entityX", 0.0));
            actionTag.putDouble("y", tag.getDoubleOr("entityY", 0.0));
            actionTag.putDouble("z", tag.getDoubleOr("entityZ", 0.0));
            break;
         case WAIT_COOLDOWN:
            actionTag.putString("itemName", tag.getStringOr("itemName", ""));
            actionTag.putBoolean("checkMainHand", tag.getBooleanOr("checkMainHand", true));
            break;
         case WAIT_POS:
            actionTag.putDouble("x", tag.getDoubleOr("x", 0.0));
            actionTag.putDouble("y", tag.getDoubleOr("y", 0.0));
            actionTag.putDouble("z", tag.getDoubleOr("z", 0.0));
            actionTag.putDouble("leeway", tag.getDoubleOr("leeway", 1.0));
            actionTag.putBoolean("checkRotation", tag.getBooleanOr("checkRotation", false));
            actionTag.putFloat("yaw", (float)tag.getDoubleOr("yaw", 0.0));
            actionTag.putFloat("pitch", (float)tag.getDoubleOr("pitch", 0.0));
            actionTag.putFloat("rotLeeway", (float)tag.getDoubleOr("rotLeeway", 5.0));
            break;
         case WAIT_SOUND:
            copyStringList(tag, actionTag, "soundIds");
            actionTag.putBoolean("checkDistance", tag.getBooleanOr("checkDistance", false));
            actionTag.putDouble("maxDistance", tag.getDoubleOr("maxDistance", 16.0));
            break;
         case WAIT_POSITION_DELTA:
            actionTag.putDouble("distance", tag.getDoubleOr("distance", 5.0));
            actionTag.putBoolean("horizontalOnly", tag.getBooleanOr("horizontalOnly", false));
      }

      action.fromTag(actionTag);
      return action;
   }

   private static MacroAction migrateLegacyTrigger(CompoundTag tag) {
      String legacy = tag.getStringOr("trigger", "MANUAL_MS").toUpperCase(Locale.ROOT);
      CompoundTag flat = new CompoundTag();
      switch (legacy) {
         case "POSITION_JUMP":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_POSITION_DELTA.name());
            flat.putDouble("distance", tag.getDoubleOr("positionTolerance", 5.0));
            break;
         case "WORLD_CHANGE":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_WORLD_CHANGE.name());
            break;
         case "GUI_OPEN_BY_TITLE":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_GUI.name());
            flat.putString("waitMode", "OPEN");
            flat.putString("guiTitle", tag.getStringOr("guiTitleSubstr", ""));
            break;
         case "GUI_CLOSE_BY_TITLE":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_GUI.name());
            flat.putString("waitMode", "CLOSE");
            flat.putString("guiTitle", tag.getStringOr("guiTitleSubstr", ""));
            break;
         case "TELEPORT_PACKET":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_PACKET.name());
            flat.putString("packetName", "S2C:ClientboundPlayerPositionPacket");
            break;
         case "CHAT_MATCH":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_CHAT.name());
            flat.putString("pattern", tag.getStringOr("chatPattern", ""));
            flat.putBoolean("useRegex", tag.getBooleanOr("chatUseRegex", false));
            break;
         case "S2C_PACKET_CLASS":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_PACKET.name());
            flat.putString("packetName", "S2C:" + tag.getStringOr("s2cPacketClassName", ""));
            break;
         case "HEALTH_BELOW":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_HEALTH.name());
            flat.putDouble("healthThreshold", tag.getDoubleOr("healthBelowThreshold", 10.0));
            flat.putString("comparison", "Drops Below");
            break;
         case "SLOT_CHANGE":
         case "ITEM_FOUND":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_SLOT_CHANGE.name());
            break;
         case "BLOCK_STATE":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_BLOCK.name());
            ListTag blockIds = new ListTag();
            String blockId = tag.getStringOr("blockId", "");
            if (!blockId.isBlank()) {
               blockIds.add(StringTag.valueOf(blockId));
            }

            flat.put("blockIds", blockIds);
            flat.putInt("blockX", tag.getIntOr("blockX", 0));
            flat.putInt("blockY", tag.getIntOr("blockY", 0));
            flat.putInt("blockZ", tag.getIntOr("blockZ", 0));
            break;
         case "ENTITY_NEAR":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_ENTITY.name());
            copyStringList(tag, flat, "entityNearIds", "entityIds");
            flat.putDouble("radius", tag.getDoubleOr("entityNearRadius", 8.0));
            break;
         case "COOLDOWN_READY":
            flat.putString("triggerType", RaceAction.TriggerType.WAIT_COOLDOWN.name());
            flat.putString("itemName", tag.getStringOr("cooldownItemName", ""));
            break;
         default:
            flat.putString("triggerType", RaceAction.TriggerType.DELAY.name());
            flat.putInt("delayMs", tag.getIntOr("manualDelayMs", 0));
      }

      return triggerFromFlatFields(flat);
   }

   private static void writeFlatTriggerFields(CompoundTag tag, MacroAction action) {
      RaceAction.TriggerType type = triggerTypeFor(action);
      tag.putString("triggerType", type.name());
      CompoundTag src = RiptideMacro.serializeAction(action);

      for (String key : src.keySet()) {
         if (!"type".equals(key) && !tag.contains(key)) {
            tag.put(key, src.get(key));
         }
      }

      if (action instanceof WaitForBlockAction block) {
         tag.putInt("blockX", block.blockPos == null ? 0 : block.blockPos.getX());
         tag.putInt("blockY", block.blockPos == null ? 0 : block.blockPos.getY());
         tag.putInt("blockZ", block.blockPos == null ? 0 : block.blockPos.getZ());
      }

      if (action instanceof WaitForEntityAction entity) {
         tag.putString("entityCheckMode", entity.checkMode == null ? "NEAR_PLAYER" : entity.checkMode.name());
         tag.putDouble("entityX", entity.x);
         tag.putDouble("entityY", entity.y);
         tag.putDouble("entityZ", entity.z);
      }
   }

   private static RaceAction.TriggerType parseTriggerType(String raw) {
      try {
         return RaceAction.TriggerType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return RaceAction.TriggerType.DELAY;
      }
   }

   private static String canonicalTriggerName(String raw) {
      String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);

      return switch (key) {
         case "WAIT_POS", "WAIT_WORLD_CHANGE", "WAIT_POSITION_DELTA", "WAIT_TELEPORT" -> RaceAction.TriggerType.WAIT_MOVEMENT.name();
         default -> key;
      };
   }

   private static String canonicalActionName(String raw) {
      return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
   }

   public static String canonicalRaceStep(String raw) {
      if (raw == null) {
         return "";
      } else {
         String text = raw.trim();
         if (text.isBlank()) {
            return "";
         } else {
            int idx = text.indexOf(58);
            String role = idx >= 0 ? text.substring(0, idx).trim().toUpperCase(Locale.ROOT) : "";
            String type = idx >= 0 ? text.substring(idx + 1).trim() : text;
            if ("CONDITION".equals(role) || "COND".equals(role)) {
               return "CONDITION:" + canonicalTriggerName(type);
            } else if (!"ACTION".equals(role) && !"ACT".equals(role)) {
               String condition = canonicalTriggerName(type);

               try {
                  RaceAction.TriggerType.valueOf(condition);
                  return "CONDITION:" + condition;
               } catch (IllegalArgumentException var7) {
                  return "ACTION:" + canonicalActionName(type);
               }
            } else {
               return "ACTION:" + canonicalActionName(type);
            }
         }
      }
   }

   public void syncLegacyListsFromRaceSteps() {
      this.conditionTypes = new ArrayList<>();
      this.actionTypes = new ArrayList<>();

      for (String step : this.effectiveRaceSteps()) {
         String type = stepTypeName(step);
         if (isConditionStep(step)) {
            this.conditionTypes.add(canonicalTriggerName(type));
         } else if (isActionStep(step)) {
            this.actionTypes.add(canonicalActionName(type));
         }
      }
   }

   private static void copyStringList(CompoundTag from, CompoundTag to, String key) {
      copyStringList(from, to, key, key);
   }

   private static void copyStringList(CompoundTag from, CompoundTag to, String fromKey, String toKey) {
      ListTag out = new ListTag();
      from.getList(fromKey).ifPresent(list -> {
         for (int i = 0; i < list.size(); i++) {
            list.getString(i).ifPresent(s -> {
               if (!s.isBlank()) {
                  out.add(StringTag.valueOf(s));
               }
            });
         }
      });
      to.put(toKey, out);
   }

   public int normalizedBodyCount(List<MacroAction> actions, int headerIndex) {
      if (actions != null && headerIndex >= 0 && headerIndex < actions.size()) {
         int max = Math.max(0, Math.min(this.bodyCount, actions.size() - headerIndex - 1));
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

   public int normalizedConditionCount(List<MacroAction> actions, int headerIndex) {
      int body = this.normalizedBodyCount(actions, headerIndex);
      int count = 0;

      for (int offset = 1; offset <= body && headerIndex + offset < actions.size(); offset++) {
         if (isConditionAction(actions.get(headerIndex + offset))) {
            count++;
         }
      }

      return count;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.RACE;
   }

   @Override
   public String getDisplayName() {
      String name = this.label != null && !this.label.isBlank() ? this.label.trim() : "Race";
      List<String> conditions = this.effectiveConditionTypes();
      String trigger = conditions.isEmpty() ? "instant" : (conditions.size() == 1 ? conditions.get(0) : conditions.size() + " conditions");
      List<String> actions = this.effectiveActionTypes();
      String action = actions.size() == 1 ? actions.get(0) : actions.size() + " actions";
      return name + " [" + trigger + " -> " + action + "]";
   }

   @Override
   public String getIcon() {
      return "Rce";
   }

   public static enum TriggerType {
      DELAY,
      WAIT_GUI,
      WAIT_CHAT,
      WAIT_PACKET,
      WAIT_HEALTH,
      WAIT_SLOT_CHANGE,
      WAIT_BLOCK,
      WAIT_ENTITY,
      WAIT_COOLDOWN,
      WAIT_POS,
      WAIT_SOUND,
      WAIT_PACKET_MATCH,
      WAIT_INVENTORY_PREDICATE,
      WAIT_DURABILITY,
      WAIT_FREE_SLOTS,
      WAIT_MOVEMENT,
      WAIT_LAN_STEP,
      WAIT_MACRO_STEP,
      TICK_SYNC,
      REVISION_SYNC,
      SERVER_TICK_SYNC,
      WAIT_WORLD_CHANGE,
      WAIT_POSITION_DELTA,
      WAIT_TELEPORT,
      WAIT_GAMEMODE_CHANGE;
   }
}
