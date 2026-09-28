package riptide.util.multi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import riptide.util.RiptideMacro;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.FlowAction;
import riptide.util.macro.HClipAction;
import riptide.util.macro.IfAction;
import riptide.util.macro.InteractEntityAction;
import riptide.util.macro.LookAtBlockAction;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;
import riptide.util.macro.MacroCondition;
import riptide.util.macro.NbtBookAction;
import riptide.util.macro.OpenContainerAction;
import riptide.util.macro.PacketBurstAction;
import riptide.util.macro.VClipAction;
import riptide.util.macro.WaitEntityTargetAction;
import riptide.util.macro.WaitForBlockAction;
import riptide.util.macro.WaitForEntityAction;

final class MultiMacroSupport {
   private MultiMacroSupport() {
   }

   static MultiMacroSupport.Note actionNote(MacroActionType t) {
      return switch (t) {
         case DELAY, SEND_CHAT, SEND_COMMAND_PACKET, SELECT_SLOT, USE_ITEM, CLOSE_GUI, INVENTORY, DROP, PACKET_CLICK, ITEM, DISCONNECT, START_MACRO, STOP_MACRO, IF, FLOW, LABEL, BRANCH, REPEAT, PAY, ASSERT, SIGN_EDIT, FINALLY, RACE, MACRO_VARIABLES, XCARRY, SAVE_GUI, DESYNC, RESTORE_GUI, CUSTOM_MENU -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.OK, ""
         );
         case CLICK -> new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "uses the no-crosshair behavior: left swings and right uses the held item");
         case USE_ITEM_PHASE -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED,
            "supports use/start/release/swing, repeat and hold timing; item auto-select, packet gates and slot choreography are not reproduced"
         );
         case TICK_SYNC, SERVER_TICK_SYNC, REVISION_SYNC -> new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
         case STORE_ITEM, SWAP_SLOTS, PICK_UP_ALL, CONTAINER_CLICK_SEQUENCE -> new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
         case WAIT_GUI, WAIT_GUI_TYPE, WAIT_HEALTH, WAIT_FREE_SLOTS, WAIT_INVENTORY_PREDICATE, WAIT_DURABILITY, WAIT_POS, WAIT_ITEM, WAIT_SLOT_CHANGE, WAIT_POSITION_DELTA, WAIT_TELEPORT, WAIT_WORLD_CHANGE, WAIT_MOVEMENT, WAIT_CHAT, WAIT_GAMEMODE_CHANGE, WAIT_PACKET, WAIT_SOUND, WAIT_PACKET_MATCH, WAIT_COOLDOWN, WAIT_MACRO_STEP -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.OK, ""
         );
         case CAPTURE_VALUE, PACKET_BURST, NBT_BOOK -> new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
         case ROTATE, SNEAK, SPRINT -> new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
         case MOVE -> new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "walks in a straight line via position packets (no collision or pathfinding)");
         case JUMP -> new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "jumps via a streamed position arc");
         case VCLIP -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "manual relative vertical packet clips are supported; Top/Bottom world-scan modes are not"
         );
         case HCLIP -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "manual relative horizontal packet clips are supported; Forward/Back route-scan modes are not"
         );
         case TP -> new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "ground-first HClip teleport is supported with correction rebasing");
         case BREAK -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "digs a block by coordinates (instant/creative only - no survival dig timing)"
         );
         case PLACE -> new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "places on a block by coordinates (uses the held item; doesn't auto-select)");
         case INSTA_BREAK -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "breaks the block at its configured coordinates (instant); no block-hardness timing"
         );
         case OPEN_CONTAINER -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "opens a block container by coordinates (Last-target mode needs a crosshair no bot has)"
         );
         case LOOK_AT_BLOCK -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "aims at specific coordinates ('find nearest block of type' needs world data no bot has)"
         );
         case INTERACT_ENTITY -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.FULL_ONLY, "interacts with the nearest tracked entity of the given type (targets by type, not crosshair)"
         );
         case WAIT_ENTITY, WAIT_ENTITY_TARGET -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.FULL_ONLY,
            "waits for a tracked entity of the given type within range (radius/reach modes; crosshair/mount modes can't be checked headlessly)"
         );
         case WAIT_LAN_STEP -> new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "LAN step coordination is not available per bot");
         case WAIT_BLOCK -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED,
            "AT_POSITION works via tracked block-change packets (a bot loads no chunks, so it only sees blocks it was told changed); IN_REACH / LOOKING_AT need world/crosshair"
         );
         case MINE -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "scans the world for blocks of a type to break -no chunk data headless (use INSTA_BREAK/BREAK by coordinates)"
         );
         case GO_TO -> new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "Baritone pathfinding -requires a real world/pathfinder");
         case CRAFT -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "needs the client recipe book to resolve + auto-fill recipes (no recipe manager on a headless bot)"
         );
         case INVENTORY_AUDIT -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "container dupe: needs client GUI-timing / world-raycast reopen a headless bot can't reproduce"
         );
         case PAYLOAD -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.LIMITED, "sends raw-byte custom payloads; the structured / JSON / Java-script payload builders aren't run headlessly"
         );
         case PACKET, SEND_PACKET, DELAY_PACKETS, PACKET_GATE, END_PACKET_GATE, SEND_TOGGLE, ROLLBACK -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "raw/queued packet tooling -not wired into the bot executor"
         );
         case TOGGLE_MODULE, FAKE_GAMEMODE, FPS, SPING, BUNDLE_DUPE_V2, REPORT -> new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "client-only / advanced feature not available to a headless bot"
         );
         case MULTI -> new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "orchestrates other bots from the main client");
      };
   }

   private static MultiMacroSupport.Note refine(MacroAction action, MultiMacroSupport.Note base) {
      if (action instanceof WaitForEntityAction w
         && (w.checkMode == WaitForEntityAction.CheckMode.LOOKING_AT || w.checkMode == WaitForEntityAction.CheckMode.MOUNTED_IN)) {
         return new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "waits on the crosshair/mount state, which no bot can observe");
      } else if (action instanceof WaitEntityTargetAction w
         && (w.condition == WaitEntityTargetAction.EntityCondition.LOOKING_AT || w.condition == WaitEntityTargetAction.EntityCondition.MOUNTED_IN)) {
         return new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "waits on the crosshair/mount state, which no bot can observe");
      } else if (action instanceof WaitForBlockAction w && w.checkMode != WaitForBlockAction.CheckMode.AT_POSITION) {
         return new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "IN_REACH / LOOKING_AT block waits need a world/crosshair no bot has; only AT_POSITION works"
         );
      } else if (action instanceof VClipAction v && v.mode != VClipAction.Mode.MANUAL) {
         return new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "Top/Bottom clip modes scan client world collision; only Manual works on Multi bots"
         );
      } else if (action instanceof HClipAction h && h.mode != HClipAction.Mode.MANUAL) {
         return new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "Forward/Back clip modes scan and route through client world collision; only Manual works on Multi bots"
         );
      } else if (action instanceof LookAtBlockAction look) {
         if (look.targetMode == LookAtBlockAction.TargetMode.BLOCK) {
            return new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "nearest-block lookup needs chunk data; use Specific coordinates");
         } else {
            return look.targetMode == LookAtBlockAction.TargetMode.ENTITY
               ? new MultiMacroSupport.Note(
                  MultiMacroSupport.Level.FULL_ONLY, "aims at the nearest tracked entity of the given type; Specific coordinates also work"
               )
               : new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
         }
      } else if (action instanceof InteractEntityAction interact && interact.targetMode == InteractEntityAction.TargetMode.LAST_TARGET) {
         return new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "the rendered client's last crosshair target is not shared with Multi bots");
      } else if (!(action instanceof OpenContainerAction open)) {
         if (action instanceof PacketBurstAction burst) {
            if (burst.mode == PacketBurstAction.BurstMode.CLIENT_COMMAND) {
               return new MultiMacroSupport.Note(
                  MultiMacroSupport.Level.UNSUPPORTED, "CLIENT_COMMAND needs a real client-player entity; the other burst modes work per bot"
               );
            }

            if (burst.mode == PacketBurstAction.BurstMode.ENTITY_INTERACT && burst.entityId < 0) {
               return new MultiMacroSupport.Note(
                  MultiMacroSupport.Level.UNSUPPORTED, "ENTITY_INTERACT needs an explicit entity id because a bot has no crosshair target"
               );
            }

            if (burst.flushBefore || burst.count > 64) {
               return new MultiMacroSupport.Note(
                  MultiMacroSupport.Level.LIMITED, "runs per bot with a 64-packet safety cap; flush-before has no per-bot delayed queue to flush"
               );
            }
         }

         if (action instanceof NbtBookAction book) {
            if ("File".equalsIgnoreCase(book.dataSource)) {
               return new MultiMacroSupport.Note(MultiMacroSupport.Level.UNSUPPORTED, "File-source books remain client-only; Random and Pasted sources work");
            }

            if (book.dropInventoryBefore || book.bookCount > 9 || "Pasted".equalsIgnoreCase(book.dataSource) && book.wordWrap) {
               return new MultiMacroSupport.Note(
                  MultiMacroSupport.Level.LIMITED,
                  "supports Random/Pasted books (up to 9 per action); pixel-font wrapping and drop-inventory-before aren't reproduced"
               );
            }
         }

         if (action instanceof CaptureValueAction capture) {
            if (capture.source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
               return capture.autofillCacheList
                  ? new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "")
                  : new MultiMacroSupport.Note(
                     MultiMacroSupport.Level.LIMITED,
                     "uses the bot's live server suggestion reply; enable Cache List to sweep past the server's suggestion cap"
                  );
            }

            if (capture.source == CaptureValueAction.Source.TABLIST) {
               return new MultiMacroSupport.Note(MultiMacroSupport.Level.LIMITED, "captures live player names; team-prefix metadata isn't tracked per bot");
            }
         }

         return base;
      } else if (open.targetMode == OpenContainerAction.TargetMode.LAST_TARGET) {
         return new MultiMacroSupport.Note(
            MultiMacroSupport.Level.UNSUPPORTED, "the rendered client's last container target is not shared with Multi bots; use Block coordinates"
         );
      } else {
         return open.targetMode == OpenContainerAction.TargetMode.ENTITY
            ? new MultiMacroSupport.Note(
               MultiMacroSupport.Level.FULL_ONLY, "opens the nearest tracked entity's container of the given type; Block coordinates also work"
            )
            : new MultiMacroSupport.Note(MultiMacroSupport.Level.OK, "");
      }
   }

   private static String conditionReason(MacroCondition.Kind kind) {
      return switch (kind) {
         case LOOKING_AT_BLOCK -> "no crosshair/world to raycast a block";
         case MOUNTED_ENTITY -> "mount state isn't tracked headlessly";
         case BUNDLE_V2_READY -> "bundle-v2 readiness needs the client inventory GUI";
         default -> null;
      };
   }

   static List<String> analyze(RiptideMacro macro) {
      Map<String, String> warnings = new LinkedHashMap<>();

      for (MacroAction action : macro.actions) {
         if (action != null && action.isEnabled()) {
            MacroActionType type = action.getType();
            if (type != null) {
               MultiMacroSupport.Note note = refine(action, actionNote(type));
               String label = type.name();
               switch (note.level()) {
                  case OK:
                  case FULL_ONLY:
                  default:
                     break;
                  case LIMITED:
                     warnings.put("A:" + label, "- " + label + ": " + note.reason() + ".");
                     break;
                  case UNSUPPORTED:
                     warnings.put("A:" + label, "- " + label + ": " + note.reason() + "; SKIPPED.");
               }
            }

            MacroCondition condition = conditionOf(action);
            if (condition != null) {
               collectConditionWarnings(condition, warnings);
            }
         }
      }

      return new ArrayList<>(warnings.values());
   }

   private static MacroCondition conditionOf(MacroAction action) {
      if (action instanceof IfAction a) {
         return a.condition;
      } else if (action instanceof FlowAction a) {
         return a.conditional ? a.condition : null;
      } else {
         return null;
      }
   }

   private static void collectConditionWarnings(MacroCondition node, Map<String, String> out) {
      if (node != null) {
         if (node.nodeType == MacroCondition.NodeType.LEAF) {
            String reason = conditionReason(node.kind);
            if (reason != null) {
               out.put("C:" + node.kind.name(), "- condition " + node.kind.name() + ": " + reason + "; treated as false.");
            }
         } else {
            for (MacroCondition child : node.children) {
               collectConditionWarnings(child, out);
            }
         }
      }
   }

   static enum Level {
      OK,
      FULL_ONLY,
      LIMITED,
      UNSUPPORTED;
   }

   record Note(MultiMacroSupport.Level level, String reason) {
   }
}
