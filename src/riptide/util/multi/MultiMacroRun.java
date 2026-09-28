package riptide.util.multi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.Direction;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.util.RiptideBookPayloadBuilder;
import riptide.util.RiptideMacro;
import riptide.util.RiptidePacketClick;
import riptide.util.macro.AssertAction;
import riptide.util.macro.BranchAction;
import riptide.util.macro.BreakAction;
import riptide.util.macro.CaptureListSelector;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.ClickAction;
import riptide.util.macro.ContainerClickSequenceAction;
import riptide.util.macro.CustomMenuAction;
import riptide.util.macro.CustomMenuActionSupport;
import riptide.util.macro.DelayAction;
import riptide.util.macro.DropAction;
import riptide.util.macro.FinallyAction;
import riptide.util.macro.FlowAction;
import riptide.util.macro.HClipAction;
import riptide.util.macro.IfAction;
import riptide.util.macro.InstaBreakAction;
import riptide.util.macro.InteractEntityAction;
import riptide.util.macro.InventoryAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.JumpAction;
import riptide.util.macro.LabelAction;
import riptide.util.macro.LookAtBlockAction;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;
import riptide.util.macro.MacroCapturePattern;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.MacroGuiMatcher;
import riptide.util.macro.MacroTemplate;
import riptide.util.macro.MacroValue;
import riptide.util.macro.MacroVariableContext;
import riptide.util.macro.MacroVariablesAction;
import riptide.util.macro.MoveAction;
import riptide.util.macro.NbtBookAction;
import riptide.util.macro.OpenContainerAction;
import riptide.util.macro.PacedTpAction;
import riptide.util.macro.PacketBurstAction;
import riptide.util.macro.PacketClickAction;
import riptide.util.macro.PayAction;
import riptide.util.macro.PayloadAction;
import riptide.util.macro.PickUpAllAction;
import riptide.util.macro.PlaceAction;
import riptide.util.macro.RaceAction;
import riptide.util.macro.RepeatAction;
import riptide.util.macro.RevisionSyncAction;
import riptide.util.macro.RotateAction;
import riptide.util.macro.SaveGuiAction;
import riptide.util.macro.SelectSlotAction;
import riptide.util.macro.SendChatAction;
import riptide.util.macro.SendCommandPacketAction;
import riptide.util.macro.SignEditAction;
import riptide.util.macro.SneakAction;
import riptide.util.macro.SprintAction;
import riptide.util.macro.StartMacroAction;
import riptide.util.macro.StoreItemAction;
import riptide.util.macro.SwapSlotsAction;
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
import riptide.util.macro.WaitsForGui;
import riptide.util.macro.XCarryAction;

final class MultiMacroRun {
   private static final int STEP_BUDGET = 128;
   private static final int EMIT_BUDGET = 8;
   private static final int MAX_REPEAT_EMITS = 64;
   private static final int LEAF_BURST_CAP = 1000;
   private static final long GUI_WAIT_TIMEOUT_MS = 3000L;
   private static final long WAIT_TIMEOUT_MS = 10000L;
   private static final long WAIT_EVENT_TIMEOUT_MS = 600000L;
   private static final long ITEM_RESOLVE_GRACE_MS = 2500L;
   private static final long CONTAINER_GRACE_MS = 1500L;
   private static final long ITEM_RESOLVE_POLL_MS = 50L;
   private final RiptideMacro macro;
   private final List<MacroAction> actions;
   private final Map<String, Integer> labelIndex = new HashMap<>();
   private final Map<String, String> vars = new HashMap<>();
   private final Map<String, MacroValue> structuredVars = new HashMap<>();
   private final Map<CaptureValueAction, CaptureListSelector.State> captureSelections = new IdentityHashMap<>();
   private final Map<String, List<String>> suggestionCache = new HashMap<>();
   private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
   private static final int MIN_SWEEP_CAP = 8;
   private static final int MAX_SWEEP_NAMES = 10000;
   private final Map<String, MultiMacroRun.AutofillUnion> autofillUnions = new HashMap<>();
   private MultiMacroRun.AutofillUnion pendingAutofillUnion;
   private final Deque<int[]> repeatStack = new ArrayDeque<>();
   private final int maxLoops;
   private int ip;
   private int loopIndex;
   private int branchElseStart = -1;
   private int branchElseCount;
   private long delayUntil;
   private boolean waitingForGui;
   private String guiWaitName = "";
   private long guiWaitBaselineSeq;
   private long guiWaitDeadline;
   private UseItemPhaseAction.Phase pendingUsePhase;
   private boolean pendingUseOffhand;
   private boolean pendingUseRelease;
   private int pendingUseRemaining;
   private long pendingUseHoldMs;
   private CaptureValueAction pendingCapture;
   private int pendingSuggestionId = -1;
   private String pendingSuggestionQuery = "";
   private long pendingCaptureDeadline;
   private boolean pendingScoreboardBaselineReady;
   private String pendingScoreboardKey = "";
   private String pendingScoreboardText = "";
   private PacketBurstAction pendingBurst;
   private int pendingBurstRemaining;
   private long pendingBurstDelayMs;
   private NbtBookAction pendingBook;
   private int pendingBookIndex;
   private int pendingBookTotal;
   private int pendingBookHotbarMask;
   private long pendingBookDelayMs;
   private int pendingBookSwapAttempts;
   private XCarryAction pendingXCarry;
   private PacedTpAction pendingTp;
   private double pendingTpX;
   private double pendingTpY;
   private double pendingTpZ;
   private double pendingTpStep;
   private double pendingTpProgressDistance;
   private int pendingTpWindowPackets;
   private long pendingTpResumeAt;
   private long pendingTpTeleportSeq;
   private long pendingTpArrivalAt;
   private long pendingTpRecoveryNextAt;
   private long pendingTpRecoveryStableAt;
   private double pendingTpRecoveryAnchor;
   private boolean pendingTpRecovery;
   private String pendingTpDimension = "";
   private CustomMenuAction pendingCustomMenu;
   private long pendingCustomMenuDeadline;
   private MacroAction waitAction;
   private long waitDeadline;
   private int waitBaseCount;
   private boolean waitBaseSlotFilled;
   private double waitBaseX;
   private double waitBaseY;
   private double waitBaseZ;
   private long waitBaseTeleportSeq;
   private String waitBaseDimension = "";
   private String[] waitSlotBaseline;
   private int waitBaseGameMode;
   private long waitBaseChatSeq;
   private long waitBasePacketSeq;
   private boolean packetWaitArmed;
   private long waitBaseSoundSeq;
   private boolean soundWaitArmed;
   private long waitBaseRevision;
   private List<String> cmdQueue;
   private int cmdQueueIdx;
   private long cmdQueueDelayMs;
   private int leafBurstRemaining;
   private MultiMacroHost activeHost;
   private List<int[]> clickPlan;
   private int clickPlanIdx;
   private long clickResolveDeadline;
   private long clickPlanDelayMs;
   private boolean clickPlanCloseAfter;
   private boolean clickPlanCloseSilent;
   private boolean pendingClickCloseAfter;
   private boolean pendingClickCloseSilent;
   private int finallyStart = -1;
   private int finallyEnd;
   private boolean finallyRegistered;
   private boolean finallyRan;
   private boolean runningFinally;
   private String pendingFinishReason;
   private List<MacroAction> raceConditions;
   private Set<Integer> raceSkipIndices;
   private long raceDeadline;
   private int raceBlockEnd = -1;
   private boolean raceWaiting;
   private boolean done;
   private volatile String status = "idle";
   private final boolean hasCustomMenuAction;
   private boolean terminateRequested;
   private String terminateReason = "done";
   private final Set<MacroActionType> notedSkips = EnumSet.noneOf(MacroActionType.class);

   MultiMacroRun(RiptideMacro macro) {
      this.macro = macro;
      this.actions = macro.actions;
      this.maxLoops = macro.loop ? (macro.loopCount < 0 ? Integer.MAX_VALUE : Math.max(1, macro.loopCount)) : 1;
      boolean anyCustomMenu = false;

      for (int i = 0; i < this.actions.size(); i++) {
         MacroAction a = this.actions.get(i);
         if (a instanceof LabelAction label) {
            this.labelIndex.putIfAbsent(label.normalizedName(), i);
         }

         if (a != null && a.getType() == MacroActionType.CUSTOM_MENU) {
            anyCustomMenu = true;
         }
      }

      this.hasCustomMenuAction = anyCustomMenu;
      this.status = "Running " + macro.name;
   }

   boolean done() {
      return this.done;
   }

   String status() {
      return this.status;
   }

   String macroName() {
      return this.macro.name;
   }

   int stepIndex() {
      return Math.max(0, Math.min(this.actions.size(), this.ip));
   }

   int totalSteps() {
      return this.actions.size();
   }

   int loopNumber() {
      return Math.max(1, this.loopIndex + 1);
   }

   MultiSession.MacroProgress progress() {
      return new MultiSession.MacroProgress(
         this.macro.name, !this.done, this.stepIndex(), this.totalSteps(), this.loopNumber(), MultiManager.singleLine(this.status, 64)
      );
   }

   void step(long now, MultiMacroHost host) {
      this.activeHost = host;
      if (!this.done && !this.actions.isEmpty()) {
         boolean ready = host.macroReady();
         if (ready || host.customMenuPhaseActive()) {
            if (this.pendingCustomMenu != null) {
               if (!this.pollCustomMenu(host, now)) {
                  return;
               }

               this.pendingCustomMenu = null;
               this.pendingCustomMenuDeadline = 0L;
               if (this.consumeTerminate()) {
                  return;
               }
            }

            ready = host.macroReady();
            if (ready) {
               MultiMacroHost.InventorySyncState inventorySync = host.inventorySyncState();
               if (inventorySync != MultiMacroHost.InventorySyncState.READY) {
                  this.status = inventorySync == MultiMacroHost.InventorySyncState.BLOCKED
                     ? "Waiting for authoritative inventory update"
                     : "Waiting for inventory transaction";
                  return;
               }
            }

            if (this.pendingClickCloseAfter || this.pendingClickCloseSilent) {
               boolean sendClose = this.pendingClickCloseAfter;
               this.pendingClickCloseAfter = false;
               this.pendingClickCloseSilent = false;
               host.runClient(sendClose ? "close" : "close-silent", "");
            }

            if (this.delayUntil > 0L) {
               if (now < this.delayUntil) {
                  return;
               }

               this.delayUntil = 0L;
            }

            if (!host.clipBusy()) {
               if (this.pendingTp != null) {
                  if (!this.tickPendingTp(host, now)) {
                     return;
                  }

                  this.clearPendingTp();
               }

               if (this.pendingUsePhase != null) {
                  if (this.pendingUseRelease && this.pendingUsePhase != UseItemPhaseAction.Phase.RELEASE_USE) {
                     host.useItemPhase(UseItemPhaseAction.Phase.RELEASE_USE, this.pendingUseOffhand);
                  }

                  if (this.pendingUseRemaining > 0) {
                     host.useItemPhase(this.pendingUsePhase, this.pendingUseOffhand);
                     this.pendingUseRemaining--;
                     this.delayUntil = now + this.pendingUseHoldMs;
                     return;
                  }

                  this.pendingUsePhase = null;
                  this.pendingUseRelease = false;
                  this.pendingUseHoldMs = 0L;
               }

               if (this.pendingCapture != null) {
                  if (!this.pollPendingCapture(host, now)) {
                     return;
                  }

                  this.pendingCapture = null;
                  this.pendingSuggestionId = -1;
                  this.pendingSuggestionQuery = "";
                  this.pendingAutofillUnion = null;
                  this.pendingCaptureDeadline = 0L;
                  this.pendingScoreboardBaselineReady = false;
                  this.pendingScoreboardKey = "";
                  this.pendingScoreboardText = "";
               }

               if (this.pendingBurst != null) {
                  boolean sent = host.sendPacketBurst(this.pendingBurst);
                  if (!sent) {
                     this.skip(MacroActionType.PACKET_BURST);
                     this.pendingBurst = null;
                     this.pendingBurstRemaining = 0;
                     this.pendingBurstDelayMs = 0L;
                  } else {
                     if (--this.pendingBurstRemaining > 0) {
                        this.delayUntil = now + this.pendingBurstDelayMs;
                        return;
                     }

                     this.pendingBurst = null;
                     this.pendingBurstDelayMs = 0L;
                  }
               }

               if (this.pendingBook != null) {
                  int bookResult = this.sendBook(this.pendingBook, this.pendingBookIndex, this.pendingBookTotal, host);
                  if (bookResult == -2) {
                     if (++this.pendingBookSwapAttempts <= 2) {
                        return;
                     }

                     this.skip(MacroActionType.NBT_BOOK);
                     this.finishPendingBook(host, false);
                     return;
                  }

                  if (bookResult < 0) {
                     this.skip(MacroActionType.NBT_BOOK);
                     this.finishPendingBook(host, false);
                  } else {
                     if (++this.pendingBookIndex < this.pendingBookTotal) {
                        this.pendingBookSwapAttempts = 0;
                        this.delayUntil = now + this.pendingBookDelayMs;
                        return;
                     }

                     this.finishPendingBook(host, true);
                  }
               }

               if (this.pendingXCarry != null) {
                  int result = host.runXCarry(this.pendingXCarry, now);
                  if (result == 0) {
                     return;
                  }

                  if (result < 0) {
                     this.skip(MacroActionType.XCARRY);
                  }

                  this.pendingXCarry = null;
               }

               if (this.waitingForGui) {
                  boolean opened = host.guiOpenSeq() != this.guiWaitBaselineSeq
                     && (this.guiWaitName.isEmpty() || safe(host.openScreenTitle()).toLowerCase(Locale.ROOT).contains(this.guiWaitName));
                  if (!opened && now < this.guiWaitDeadline) {
                     return;
                  }

                  this.waitingForGui = false;
               }

               if (this.waitAction != null) {
                  if (now < this.waitDeadline && !this.waitConditionMet(this.waitAction, host)) {
                     return;
                  }

                  this.waitAction = null;
                  if (this.packetWaitArmed) {
                     host.setPacketCapture(false);
                     this.packetWaitArmed = false;
                  }

                  if (this.soundWaitArmed) {
                     host.setSoundCapture(false);
                     this.soundWaitArmed = false;
                  }
               }

               int budget = 128;
               int emits = 0;

               while (budget-- > 0) {
                  if (this.done) {
                     return;
                  }

                  MultiMacroHost.InventorySyncState inventorySync = host.inventorySyncState();
                  if (inventorySync != MultiMacroHost.InventorySyncState.READY) {
                     this.status = inventorySync == MultiMacroHost.InventorySyncState.BLOCKED
                        ? "Waiting for authoritative inventory update"
                        : "Waiting for inventory transaction";
                     return;
                  }

                  boolean looped = false;

                  while (!this.repeatStack.isEmpty() && this.ip == ((int[])this.repeatStack.peek())[1]) {
                     int[] top = this.repeatStack.peek();
                     if (top[2] > 0) {
                        top[2]--;
                        this.ip = top[0];
                        looped = true;
                        break;
                     }

                     this.repeatStack.pop();
                  }

                  if (!looped) {
                     if (this.runningFinally && this.ip >= this.finallyEnd) {
                        this.finish(this.pendingFinishReason);
                        return;
                     }

                     if (this.ip < this.actions.size()) {
                        if (this.branchElseStart == this.ip && this.branchElseCount > 0) {
                           this.ip = this.ip + this.branchElseCount;
                           this.branchElseStart = -1;
                           this.branchElseCount = 0;
                        } else if (this.raceSkipIndices == null || !this.raceSkipIndices.contains(this.ip)) {
                           if (this.raceBlockEnd >= 0 && !this.raceWaiting && this.ip >= this.raceBlockEnd) {
                              this.raceBlockEnd = -1;
                              this.raceSkipIndices = null;
                              this.raceConditions = null;
                           }

                           MacroAction action = this.actions.get(this.ip);
                           if (action != null && action.isEnabled()) {
                              MacroActionType type = action.getType();
                              if (type != null) {
                                 if (!ready && !isLoginPrefixType(type)) {
                                    return;
                                 }

                                 switch (type) {
                                    case IF:
                                       IfAction ifA = (IfAction)action;
                                       if (MultiMacroConditions.evaluate(ifA.condition, host, this.vars)) {
                                          this.branchElseStart = this.ip + 1 + Math.max(0, ifA.thenSteps);
                                          this.branchElseCount = Math.max(0, ifA.elseSteps);
                                       } else {
                                          this.ip = this.ip + Math.max(0, ifA.thenSteps);
                                       }

                                       this.ip++;
                                       break;
                                    case BRANCH:
                                       BranchAction b = (BranchAction)action;
                                       if (this.branchMatches(b, host)) {
                                          this.branchElseStart = this.ip + 1 + Math.max(0, b.thenSteps);
                                          this.branchElseCount = Math.max(0, b.elseSteps);
                                       } else {
                                          this.ip = this.ip + Math.max(0, b.thenSteps);
                                       }

                                       this.ip++;
                                       break;
                                    case WAIT_GUI:
                                    case WAIT_GUI_TYPE:
                                    case WAIT_HEALTH:
                                    case WAIT_ITEM:
                                    case WAIT_SLOT_CHANGE:
                                    case WAIT_INVENTORY_PREDICATE:
                                    case WAIT_FREE_SLOTS:
                                    case WAIT_POS:
                                    case WAIT_DURABILITY:
                                    case WAIT_PACKET:
                                    case WAIT_CHAT:
                                    case WAIT_BLOCK:
                                    case WAIT_ENTITY:
                                    case WAIT_ENTITY_TARGET:
                                    case WAIT_COOLDOWN:
                                    case WAIT_SOUND:
                                    case WAIT_WORLD_CHANGE:
                                    case WAIT_POSITION_DELTA:
                                    case WAIT_TELEPORT:
                                    case WAIT_GAMEMODE_CHANGE:
                                    case WAIT_MOVEMENT:
                                    case WAIT_PACKET_MATCH:
                                    case WAIT_MACRO_STEP:
                                       if (waitEvaluable(effectiveWait(action), host)) {
                                          this.captureWaitBaseline(action, host);
                                          this.waitAction = action;
                                          this.waitDeadline = now + waitTimeout(action);
                                          this.ip++;
                                          return;
                                       }

                                       this.skip(type);
                                       this.ip++;
                                       break;
                                    case FLOW:
                                       FlowAction flow = (FlowAction)action;
                                       boolean take = !flow.conditional || MultiMacroConditions.evaluate(flow.condition, host, this.vars);
                                       if (take) {
                                          Integer target = this.flowTarget(flow);
                                          if (target == null) {
                                             if (this.finishOrRunFinally("stopped")) {
                                                return;
                                             }
                                          } else {
                                             this.ip = target;
                                             this.clearBookkeepingOutside(target);
                                          }
                                       } else {
                                          this.ip++;
                                       }
                                       break;
                                    case LABEL:
                                       this.ip++;
                                       break;
                                    case FINALLY:
                                       int count = Math.max(0, ((FinallyAction)action).bodyCount);
                                       this.finallyStart = Math.min(this.actions.size(), this.ip + 1);
                                       this.finallyEnd = Math.min(this.actions.size(), this.ip + 1 + count);
                                       this.finallyRegistered = this.finallyEnd > this.finallyStart;
                                       this.ip = this.finallyEnd;
                                       break;
                                    case RACE:
                                       RaceAction race = (RaceAction)action;
                                       if (!this.raceWaiting) {
                                          int bodyCount = race.normalizedBodyCount(this.actions, this.ip);
                                          this.raceBlockEnd = Math.min(this.actions.size(), this.ip + 1 + bodyCount);
                                          this.raceConditions = new ArrayList<>();
                                          this.raceSkipIndices = new HashSet<>();

                                          for (int idx = this.ip + 1; idx < this.raceBlockEnd; idx++) {
                                             MacroAction child = this.actions.get(idx);
                                             if (child != null && child.isEnabled() && RaceAction.isConditionAction(child)) {
                                                this.raceConditions.add(child);
                                                this.raceSkipIndices.add(idx);
                                                this.captureWaitBaseline(child, host);
                                             }
                                          }

                                          this.raceDeadline = now + Math.max(0, race.timeoutMs);
                                          this.raceWaiting = true;
                                       }

                                       boolean fired = this.raceConditions.isEmpty();

                                       for (int i = 0; !fired && i < this.raceConditions.size(); i++) {
                                          if (this.waitConditionMet(this.raceConditions.get(i), host)) {
                                             fired = true;
                                          }
                                       }

                                       boolean timedOut = now >= this.raceDeadline;
                                       if (!fired && !timedOut) {
                                          this.delayUntil = now + 50L;
                                          return;
                                       }

                                       if (this.packetWaitArmed) {
                                          host.setPacketCapture(false);
                                          this.packetWaitArmed = false;
                                       }

                                       if (this.soundWaitArmed) {
                                          host.setSoundCapture(false);
                                          this.soundWaitArmed = false;
                                       }

                                       this.raceWaiting = false;
                                       if (fired) {
                                          this.ip++;
                                       } else {
                                          this.skip(type);
                                          this.ip = this.raceBlockEnd;
                                          this.raceBlockEnd = -1;
                                          this.raceSkipIndices = null;
                                          this.raceConditions = null;
                                       }
                                       break;
                                    case REPEAT:
                                       RepeatAction rep = (RepeatAction)action;
                                       int bodyStart = this.ip + 1;
                                       int bodyEnd = Math.min(this.actions.size(), bodyStart + Math.max(0, rep.stepCount));
                                       int times = Math.max(0, rep.repeatCount);
                                       if (times <= 0) {
                                          this.ip = bodyEnd;
                                       } else {
                                          if (times > 1 && bodyEnd > bodyStart) {
                                             this.repeatStack.push(new int[]{bodyStart, bodyEnd, times - 1});
                                          }

                                          this.ip++;
                                       }
                                       break;
                                    case DELAY:
                                       DelayAction d = (DelayAction)action;
                                       long ms = d.useTicks ? d.delayTicks * 50L : d.delayMs;
                                       this.delayUntil = now + Math.max(0L, ms);
                                       this.ip++;
                                       return;
                                    case TICK_SYNC:
                                    case SERVER_TICK_SYNC:
                                       this.delayUntil = now + 50L;
                                       this.ip++;
                                       return;
                                    case REVISION_SYNC:
                                       this.waitBaseRevision = host.containerRevision();
                                       this.waitAction = action;
                                       this.waitDeadline = now + 600000L;
                                       this.ip++;
                                       return;
                                    case ITEM:
                                    case STORE_ITEM:
                                    case SWAP_SLOTS:
                                    case PICK_UP_ALL:
                                    case CONTAINER_CLICK_SEQUENCE:
                                       if (this.clickPlan == null) {
                                          MultiMacroRun.ContainerPlan cp = this.resolveContainerPlan(action, type, host);
                                          if (cp.clicks.isEmpty()) {
                                             if (cp.graceMs > 0L && host.containerOpen()) {
                                                if (this.clickResolveDeadline == 0L) {
                                                   this.clickResolveDeadline = now + cp.graceMs;
                                                }

                                                if (now < this.clickResolveDeadline) {
                                                   this.delayUntil = now + 50L;
                                                   return;
                                                }
                                             }

                                             this.clickResolveDeadline = 0L;
                                             this.skip(type);
                                             this.ip++;
                                             break;
                                          }

                                          this.clickResolveDeadline = 0L;
                                          this.clickPlan = cp.clicks;
                                          this.clickPlanIdx = 0;
                                          this.clickPlanDelayMs = cp.perClickDelayMs;
                                          this.clickPlanCloseAfter = cp.closeAfter;
                                          this.clickPlanCloseSilent = cp.closeSilent;
                                       }

                                       int[] click = this.clickPlan.get(this.clickPlanIdx++);
                                       host.clickResolved(click[0], click[1], click[2]);
                                       if (this.clickPlanIdx < this.clickPlan.size()) {
                                          if (this.clickPlanDelayMs > 0L) {
                                             this.delayUntil = now + Math.min(5000L, this.clickPlanDelayMs);
                                             return;
                                          }

                                          return;
                                       }

                                       boolean closeAfter = this.clickPlanCloseAfter;
                                       boolean closeSilent = this.clickPlanCloseSilent;
                                       this.clickPlan = null;
                                       this.clickPlanIdx = 0;
                                       this.clickPlanDelayMs = 0L;
                                       this.clickPlanCloseAfter = false;
                                       this.clickPlanCloseSilent = false;
                                       this.pendingClickCloseAfter = closeAfter;
                                       this.pendingClickCloseSilent = !closeAfter && closeSilent;
                                       if (action instanceof WaitsForGui w && w.isWaitForGuiAfter()) {
                                          this.armGuiWaitAfter(w, host, now);
                                          this.ip++;
                                          return;
                                       }

                                       this.ip++;
                                       return;
                                    case PAY:
                                       if (this.cmdQueue == null) {
                                          PayAction p = (PayAction)action;
                                          this.cmdQueue = this.buildPayCommands(p);
                                          this.cmdQueueIdx = 0;
                                          this.cmdQueueDelayMs = p.delayEnabled ? Math.max(0, p.delayMs) : 0L;
                                          if (this.cmdQueue.isEmpty()) {
                                             this.cmdQueue = null;
                                             this.ip++;
                                             break;
                                          }
                                       }

                                       host.chat(this.cmdQueue.get(this.cmdQueueIdx++));
                                       if (this.cmdQueueIdx >= this.cmdQueue.size()) {
                                          this.cmdQueue = null;
                                          this.cmdQueueIdx = 0;
                                          this.cmdQueueDelayMs = 0L;
                                          this.ip++;
                                          if (++emits >= 8) {
                                             return;
                                          }
                                       } else {
                                          if (this.cmdQueueDelayMs > 0L) {
                                             this.delayUntil = now + Math.min(60000L, this.cmdQueueDelayMs);
                                             return;
                                          }

                                          if (++emits >= 8) {
                                             return;
                                          }
                                       }
                                       break;
                                    default:
                                       boolean yield;
                                       try {
                                          yield = this.executeLeaf(action, type, host, now);
                                       } catch (RuntimeException var15) {
                                          this.skip(type);
                                          yield = false;
                                          this.leafBurstRemaining = 0;
                                       }

                                       if (this.terminateRequested) {
                                          this.terminateRequested = false;
                                          if (this.finishOrRunFinally(this.terminateReason)) {
                                             return;
                                          }
                                       } else {
                                          if (yield) {
                                             this.ip++;
                                             return;
                                          }

                                          if (this.leafBurstRemaining > 0) {
                                             if (++emits >= 8) {
                                                return;
                                             }
                                          } else {
                                             this.ip++;
                                             if (++emits >= 8) {
                                                return;
                                             }
                                          }
                                       }
                                 }
                              } else {
                                 this.ip++;
                              }
                           } else {
                              if (action instanceof RaceAction disabledRace) {
                                 this.ip = this.ip + disabledRace.normalizedBodyCount(this.actions, this.ip);
                              }

                              this.ip++;
                           }
                        } else {
                           this.ip++;
                        }
                     } else if (++this.loopIndex >= this.maxLoops) {
                        if (this.finishOrRunFinally("done")) {
                           return;
                        }
                     } else {
                        this.resetForLoop();
                     }
                  }
               }
            }
         }
      } else {
         this.done = true;
      }
   }

   private boolean executeLeaf(MacroAction action, MacroActionType type, MultiMacroHost host, long now) {
      label343:
      switch (type) {
         case SEND_CHAT: {
            MacroTemplate.Resolution r = this.resolveTemplate(((SendChatAction)action).message);
            if (r.success()) {
               host.chat(r.value());
            }
            break;
         }
         case CUSTOM_MENU:
            String matcherError = CustomMenuActionSupport.titleMatcherError((CustomMenuAction)action);
            if (!matcherError.isEmpty()) {
               this.requestTerminate(matcherError);
            } else {
               this.pendingCustomMenu = (CustomMenuAction)action;
               this.pendingCustomMenuDeadline = now + this.pendingCustomMenu.boundedTimeout();
               if (!this.pollCustomMenu(host, now)) {
                  return true;
               }

               this.pendingCustomMenu = null;
               this.pendingCustomMenuDeadline = 0L;
            }
            break;
         case SEND_COMMAND_PACKET: {
            SendCommandPacketAction c = (SendCommandPacketAction)action;
            MacroTemplate.Resolution r = this.resolveTemplate(c.command);
            if (r.success()) {
               String cmdx = r.value().trim();
               if (c.stripLeadingSlash && cmdx.startsWith("/")) {
                  cmdx = cmdx.substring(1);
               }

               if (!cmdx.isBlank()) {
                  host.chat("/" + cmdx);
               }
            }
            break;
         }
         case SELECT_SLOT:
            host.runClient("change-slot", String.valueOf(Math.max(0, Math.min(8, ((SelectSlotAction)action).slot)) + 1));
            break;
         case USE_ITEM:
            host.runClient("use", "");
            break;
         case CLOSE_GUI:
            host.runClient("close", "");
            break;
         case INVENTORY:
            if (((InventoryAction)action).mode == InventoryAction.InvMode.CLOSE) {
               host.runClient("close", "");
            }
            break;
         case DROP:
            DropAction d = (DropAction)action;
            if (!d.hasSpecificTargets()) {
               host.runClient("drop", d.mode == DropAction.DropMode.ALL ? "fullinventory" : Integer.toString(Math.max(1, d.dropCount)));
            } else {
               boolean any = false;
               int dropped = 0;
               List<ItemTarget> targets = d.resolvedTargets();

               for (int i = 0; i < targets.size() && dropped < 64; i++) {
                  ItemTarget t = targets.get(i);
                  if (t != null && (t.hasIdentity() || t.hasSlot())) {
                     int count = d.getItemCount(i);
                     if (t.hasIdentity()) {
                        String name = t.hasRegistryId() ? t.registryId : t.display;
                        if (name == null || name.isBlank()) {
                           continue;
                        }

                        host.runClient("drop", count == 0 ? name : count + " " + name);
                     } else {
                        host.runClient("click-slot", t.slot + (count == 0 ? " drop-stack" : " drop-item " + count));
                     }

                     any = true;
                     dropped++;
                  }
               }

               if (!any) {
                  this.skip(type);
               }
            }
            break;
         case PACKET_CLICK:
            PacketClickAction pc = (PacketClickAction)action;
            if (pc.target == null) {
               this.skip(type);
            } else {
               if (this.leafBurstRemaining <= 0) {
                  this.leafBurstRemaining = Math.max(1, Math.min(64, pc.times));
               }

               host.runClient("click-slot", pc.target.visibleSlot() + " " + modeWord(pc.target.mode()));
               this.leafBurstRemaining--;
            }
            break;
         case ASSERT:
            AssertAction a = (AssertAction)action;
            if (!this.assertPasses(a, host) && a.failureBehavior == AssertAction.FailureBehavior.STOP_MACRO) {
               this.requestTerminate("assert failed: " + a.check);
            }
            break;
         case SIGN_EDIT:
            SignEditAction s = (SignEditAction)action;
            host.editSign(s, this.resolve(s.line1), this.resolve(s.line2), this.resolve(s.line3), this.resolve(s.line4));
            if (s.sendCommandAfter && s.commandAfter != null && !s.commandAfter.isBlank()) {
               String cmd = this.resolve(s.commandAfter).trim();
               if (cmd.startsWith("/")) {
                  cmd = cmd.substring(1);
               }

               if (!cmd.isBlank()) {
                  host.chat("/" + cmd);
               }
            }
            break;
         case CAPTURE_VALUE:
            if (this.beginCapture((CaptureValueAction)action, host, now)) {
               return true;
            }
            break;
         case PAYLOAD: {
            PayloadAction p = (PayloadAction)action;
            String data = p.payloadData == null ? "" : p.payloadData;
            if (data.isBlank()) {
               this.skip(type);
            } else {
               MacroTemplate.Resolution ch = this.resolveTemplate(p.channel);
               MacroTemplate.Resolution dr = this.resolveTemplate(data);
               if (ch.success() && !ch.value().isBlank() && dr.success()) {
                  host.sendRawPayload(ch.value().trim(), dr.value());
               } else {
                  this.skip(type);
               }
            }
            break;
         }
         case MACRO_VARIABLES:
            MacroVariablesAction mv = (MacroVariablesAction)action;
            int count = Math.min(mv.names.size(), mv.values.size());
            Map<String, String> resolvedx = new LinkedHashMap<>();
            boolean ok = true;

            for (int ix = 0; ix < count; ix++) {
               MacroTemplate.Resolution rx = this.resolveTemplate(mv.values.get(ix));
               if (!rx.success()) {
                  ok = false;
                  break;
               }

               String name = MacroVariableContext.cleanRootName(mv.names.get(ix));
               if (!name.isEmpty()) {
                  resolvedx.put(name, rx.value());
               }
            }

            if (ok) {
               this.putPlainVars(resolvedx);
            }
            break;
         case PACKET_BURST:
            PacketBurstAction burst = (PacketBurstAction)action;
            int count = Math.max(1, Math.min(64, burst.count));
            long delay = Math.max(0L, Math.min(60000L, burst.delayTicks * 50L));
            if (!host.sendPacketBurst(burst)) {
               this.skip(type);
            } else {
               if (count > 1 && delay > 0L) {
                  this.pendingBurst = burst;
                  this.pendingBurstRemaining = count - 1;
                  this.pendingBurstDelayMs = delay;
                  this.delayUntil = now + delay;
                  return true;
               }

               for (int ix = 1; ix < count; ix++) {
                  if (!host.sendPacketBurst(burst)) {
                     this.skip(type);
                     break label343;
                  }
               }
            }
            break;
         case NBT_BOOK:
            NbtBookAction book = (NbtBookAction)action;
            int total = Math.max(1, Math.min(9, book.bookCount));
            this.pendingBookHotbarMask = 0;
            int bookResult = this.sendBook(book, 0, total, host);
            if (bookResult < -1) {
               this.pendingBook = book;
               this.pendingBookIndex = 0;
               this.pendingBookTotal = total;
               this.pendingBookDelayMs = Math.max(50L, Math.min(60000L, book.delayTicks * 50L));
               this.pendingBookSwapAttempts = 1;
               return true;
            }

            if (bookResult < 0) {
               this.skip(type);
            } else {
               if (total > 1) {
                  this.pendingBook = book;
                  this.pendingBookIndex = 1;
                  this.pendingBookTotal = total;
                  this.pendingBookDelayMs = Math.max(50L, Math.min(60000L, book.delayTicks * 50L));
                  this.delayUntil = now + this.pendingBookDelayMs;
                  return true;
               }

               if (book.disconnectAfter) {
                  host.disconnectBot("NBT book macro");
               }
            }
            break;
         case SAVE_GUI:
            SaveGuiAction save = (SaveGuiAction)action;
            if (!host.saveGui(save.closeAfter, save.sendPacket)) {
               this.skip(type);
            }
            break;
         case DESYNC:
            if (!host.desyncGui()) {
               this.skip(type);
            }
            break;
         case RESTORE_GUI:
            if (!host.restoreGui()) {
               this.skip(type);
            }

            return false;
         case XCARRY:
            XCarryAction resolved = this.resolveXCarry((XCarryAction)action);
            int result = host.runXCarry(resolved, now);
            if (result == 0) {
               this.pendingXCarry = resolved;
               return true;
            }

            if (result < 0) {
               this.skip(type);
            }
            break;
         case DISCONNECT:
            host.disconnectBot("Macro");
            break;
         case STOP_MACRO:
            host.stopSelfMacro();
            this.requestTerminate("stopped");
            break;
         case START_MACRO:
            String next = ((StartMacroAction)action).macroName;
            if (host.startSelfMacro(next)) {
               this.requestTerminate("chained");
            } else {
               host.macroNote("Macro chain failed: macro not found: " + safe(next));
               this.requestTerminate("error");
            }
            break;
         case USE_ITEM_PHASE:
            UseItemPhaseAction phase = (UseItemPhaseAction)action;
            int repeats = Math.max(1, Math.min(1000, phase.repeat));
            boolean offhand = "OFF_HAND".equalsIgnoreCase(phase.hand);
            if (phase.holdTicks > 0) {
               host.useItemPhase(phase.phase, offhand);
               this.pendingUsePhase = phase.phase;
               this.pendingUseOffhand = offhand;
               this.pendingUseRelease = phase.releaseAfterHold;
               this.pendingUseRemaining = repeats - 1;
               this.pendingUseHoldMs = Math.min(60000L, phase.holdTicks * 50L);
               this.delayUntil = now + this.pendingUseHoldMs;
               return true;
            }

            if (this.leafBurstRemaining <= 0) {
               this.leafBurstRemaining = repeats;
            }

            host.useItemPhase(phase.phase, offhand);
            if (phase.releaseAfterHold && phase.phase != UseItemPhaseAction.Phase.RELEASE_USE) {
               host.useItemPhase(UseItemPhaseAction.Phase.RELEASE_USE, offhand);
            }

            this.leafBurstRemaining--;
            break;
         case CLICK:
            ClickAction ca = (ClickAction)action;
            if (this.leafBurstRemaining <= 0) {
               this.leafBurstRemaining = Math.max(1, Math.min(1000, ca.clickCount));
            }

            String verb = ca.type == ClickAction.ContainerInput.LEFT ? "swing" : "use";
            host.runClient(verb, "");
            this.leafBurstRemaining--;
            break;
         case INSTA_BREAK:
            InstaBreakAction ib = (InstaBreakAction)action;
            int n = Math.max(1, Math.min(64, ib.times));
            int x = ib.blockPos.getX();
            int y = ib.blockPos.getY();
            int z = ib.blockPos.getZ();
            String face = ib.direction == null ? "UP" : ib.direction.name();

            for (int ix = 0; ix < n; ix++) {
               host.breakBlock(x, y, z, face);
            }

            if (ib.interact) {
               host.useOnBlock(x, y, z, face);
            }
            break;
         case ROTATE: {
            RotateAction r = (RotateAction)action;
            host.look(r.yaw, r.pitch);
            break;
         }
         case LOOK_AT_BLOCK:
            this.lookAtBlock((LookAtBlockAction)action, host);
            break;
         case MOVE:
            return this.doMove((MoveAction)action, host, now);
         case JUMP:
            host.jump();
            this.delayUntil = now + Math.max(50L, ((JumpAction)action).durationTicks * 50L);
            return true;
         case SNEAK:
            host.setSneak(((SneakAction)action).sneak);
            break;
         case SPRINT:
            host.setSprint(((SprintAction)action).sprint);
            break;
         case INTERACT_ENTITY:
            if (!host.fullMode()) {
               this.skipFullOnly(type, host);
            } else {
               this.interactNearestEntity((InteractEntityAction)action, host);
            }
            break;
         case BREAK:
            BreakAction b = (BreakAction)action;
            int n = Math.max(1, Math.min(64, b.times));
            int x = b.blockPos.getX();
            int y = b.blockPos.getY();
            int z = b.blockPos.getZ();
            String face = b.direction == null ? "UP" : b.direction.name();

            for (int ix = 0; ix < n; ix++) {
               host.breakBlock(x, y, z, face);
            }

            if (b.interact) {
               host.useOnBlock(x, y, z, face);
            }
            break;
         case PLACE: {
            PlaceAction p = (PlaceAction)action;
            host.useOnBlock(p.blockPos.getX(), p.blockPos.getY(), p.blockPos.getZ(), p.direction.name());
            break;
         }
         case OPEN_CONTAINER:
            OpenContainerAction oc = (OpenContainerAction)action;
            if (oc.targetMode == OpenContainerAction.TargetMode.BLOCK) {
               host.useOnBlock(oc.blockPos.getX(), oc.blockPos.getY(), oc.blockPos.getZ(), "UP");
            } else if (oc.targetMode == OpenContainerAction.TargetMode.ENTITY) {
               String t = oc.entityTargets.isEmpty() ? typeOf(oc.entityTarget) : typeOf(oc.entityTargets.get(0));
               int id = host.nearestEntity(t);
               if (id < 0) {
                  this.skipUnsupported(type, host, "no tracked entity of that type to open");
               } else {
                  host.interactEntity(id, false);
               }
            } else {
               this.skipUnsupported(type, host, "Last-target container needs the client's crosshair; use Block coordinates");
            }
            break;
         case VCLIP:
            VClipAction clip = (VClipAction)action;
            if (clip.mode == VClipAction.Mode.MANUAL) {
               int segments = clipSegments(clip.deltaY, clip.useSegmented, clip.segmentBlocks, clip.maxPackets);
               host.clip(0.0, clip.deltaY, 0.0, segments, clip.forceGrounded);
               this.delayUntil = now + host.clipDrainMillis();
               return true;
            }

            this.skipUnsupported(type, host, "only Manual clip mode works on bots (Top/Bottom scan the client world)");
            break;
         case HCLIP:
            HClipAction clip = (HClipAction)action;
            if (clip.mode == HClipAction.Mode.MANUAL) {
               double yaw = Math.toRadians(host.currentYaw());
               double dx = -Math.sin(yaw) * clip.blocks;
               double dz = Math.cos(yaw) * clip.blocks;
               int segments = clipSegments(clip.blocks, clip.useSegmented, clip.segmentBlocks, clip.maxPackets);
               host.clip(dx, 0.0, dz, segments, clip.forceGrounded);
               this.delayUntil = now + host.clipDrainMillis();
               return true;
            }

            this.skipUnsupported(type, host, "only Manual clip mode works on bots (Forward/Back scan the client world)");
            break;
         case TP:
            this.startPendingTp((PacedTpAction)action, host);
            return true;
         case MULTI:
            this.skipUnsupported(type, host, "orchestrates other bots from the main client");
            break;
         default:
            this.skipUnsupported(type, host, "not supported on Multi bots (needs client world/state)");
      }

      if (action instanceof WaitsForGui w && w.isWaitForGuiAfter()) {
         this.armGuiWaitAfter(w, host, now);
         return true;
      } else {
         return false;
      }
   }

   private static int clipSegments(double distance, boolean segmented, int segmentBlocks, int maxPackets) {
      if (!segmented) {
         return 1;
      } else {
         int packets = Math.max(1, (int)Math.ceil(Math.abs(distance) / Math.max(1, segmentBlocks)));
         return packets > Math.max(1, maxPackets) ? 1 : Math.min(64, packets);
      }
   }

   private void startPendingTp(PacedTpAction action, MultiMacroHost host) {
      this.pendingTp = action;
      this.pendingTpX = action.relativeX ? host.posX() + action.x : action.x;
      this.pendingTpY = action.relativeY ? host.posY() + action.y : action.y;
      this.pendingTpZ = action.relativeZ ? host.posZ() + action.z : action.z;
      this.pendingTpStep = 10.0;
      this.pendingTpProgressDistance = this.tpDistance(host);
      this.pendingTpWindowPackets = 0;
      this.pendingTpResumeAt = 0L;
      this.pendingTpTeleportSeq = host.teleportSeq();
      this.pendingTpArrivalAt = 0L;
      this.pendingTpRecoveryNextAt = 0L;
      this.pendingTpRecoveryStableAt = 0L;
      this.pendingTpRecoveryAnchor = this.pendingTpProgressDistance;
      this.pendingTpRecovery = false;
      this.pendingTpDimension = safe(host.dimension());
      this.status = String.format(Locale.ROOT, "TP %.1f %.1f %.1f", this.pendingTpX, this.pendingTpY, this.pendingTpZ);
   }

   private boolean tickPendingTp(MultiMacroHost host, long now) {
      if (!safe(host.dimension()).equals(this.pendingTpDimension)) {
         host.macroNote("TP stopped: dimension changed");
         return true;
      } else {
         long teleportSeq = host.teleportSeq();
         if (teleportSeq != this.pendingTpTeleportSeq) {
            this.pendingTpTeleportSeq = teleportSeq;
            double distance = this.tpDistance(host);
            if (this.pendingTpProgressDistance - distance >= 0.25) {
               this.pendingTpProgressDistance = distance;
               this.pendingTpStep = Math.min(10.0, this.pendingTpStep * 1.25);
               if (this.pendingTpRecovery) {
                  this.pendingTpRecoveryAnchor = distance;
                  this.pendingTpRecoveryNextAt = now + 200L;
                  this.pendingTpRecoveryStableAt = now + 1200L;
               }
            } else {
               this.pendingTpStep = Math.max(0.0625, this.pendingTpStep * 0.5);
               this.pendingTpRecovery = true;
               this.pendingTpRecoveryAnchor = distance;
               this.pendingTpRecoveryNextAt = now + 200L;
               this.pendingTpRecoveryStableAt = now + 1200L;
            }

            this.pendingTpWindowPackets = 0;
            this.pendingTpResumeAt = this.pendingTpRecovery ? now + 200L : now;
            this.pendingTpArrivalAt = 0L;
         }

         double dx = this.pendingTpX - host.posX();
         double dy = this.pendingTpY - host.posY();
         double dz = this.pendingTpZ - host.posZ();
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (distance <= 0.05) {
            if (this.pendingTpArrivalAt == 0L) {
               this.pendingTpArrivalAt = now;
            }

            long settle = 500L;
            return now - this.pendingTpArrivalAt >= settle;
         } else {
            this.pendingTpArrivalAt = 0L;
            if (this.pendingTpRecovery) {
               if (now >= this.pendingTpRecoveryStableAt && this.pendingTpRecoveryAnchor - distance >= 0.5) {
                  this.pendingTpRecovery = false;
                  this.pendingTpStep = Math.min(10.0, Math.max(1.0, this.pendingTpStep * 2.0));
                  this.pendingTpWindowPackets = 0;
                  this.pendingTpResumeAt = now;
               } else if (now < this.pendingTpRecoveryNextAt) {
                  return false;
               }
            }

            if (now < this.pendingTpResumeAt) {
               return false;
            } else {
               int window = Math.max(1, Math.min(100, this.pendingTp.maxPackets));
               int remaining = this.pendingTpRecovery ? 1 : Math.max(1, window - this.pendingTpWindowPackets);
               double amount = this.pendingTpRecovery ? Math.min(this.pendingTpStep, distance) : distance;
               int packets = Math.max(1, Math.min(remaining, (int)Math.ceil(amount / this.pendingTpStep)));
               host.clip(dx / distance * amount, dy / distance * amount, dz / distance * amount, packets, true);
               if (this.pendingTpRecovery) {
                  this.pendingTpRecoveryNextAt = now + 200L;
               }

               this.pendingTpWindowPackets += packets;
               if (this.pendingTpWindowPackets >= window) {
                  this.pendingTpWindowPackets = 0;
                  this.pendingTpResumeAt = now + Math.max(50, Math.min(10000, this.pendingTp.pauseMs));
               }

               return false;
            }
         }
      }
   }

   private double tpDistance(MultiMacroHost host) {
      double dx = this.pendingTpX - host.posX();
      double dy = this.pendingTpY - host.posY();
      double dz = this.pendingTpZ - host.posZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private void clearPendingTp() {
      this.pendingTp = null;
      this.pendingTpWindowPackets = 0;
      this.pendingTpResumeAt = 0L;
      this.pendingTpArrivalAt = 0L;
      this.pendingTpRecoveryNextAt = 0L;
      this.pendingTpRecoveryStableAt = 0L;
      this.pendingTpRecoveryAnchor = 0.0;
      this.pendingTpRecovery = false;
      this.pendingTpDimension = "";
   }

   private void armGuiWaitAfter(WaitsForGui w, MultiMacroHost host, long now) {
      this.waitingForGui = true;
      String name = w.getWaitGuiName();
      this.guiWaitName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
      this.guiWaitBaselineSeq = host.guiOpenSeq();
      this.guiWaitDeadline = now + 3000L;
   }

   private MultiMacroRun.ContainerPlan resolveContainerPlan(MacroAction action, MacroActionType type, MultiMacroHost host) {
      switch (type) {
         case ITEM: {
            ItemAction ia = (ItemAction)action;
            long grace = ia.waitForItem ? 10000L : 2500L;
            long delay = ia.useClickDelay && ia.clickDelayMs > 0 ? ia.clickDelayMs : 0L;
            return new MultiMacroRun.ContainerPlan(host.resolveItemClicks(ia), grace, delay, false, false);
         }
         case STORE_ITEM: {
            StoreItemAction sa = (StoreItemAction)action;
            boolean closePacket = sa.closeAfter && !sa.persistent && sa.closeSendPkt;
            boolean closeLocal = sa.closeAfter && !sa.persistent && !sa.closeSendPkt;
            long delay = sa.delayTicks > 0 ? sa.delayTicks * 50L : 0L;
            return new MultiMacroRun.ContainerPlan(host.resolveStoreClicks(sa), 1500L, delay, closePacket, closeLocal);
         }
         case SWAP_SLOTS:
            return new MultiMacroRun.ContainerPlan(host.resolveSwapClicks((SwapSlotsAction)action), 1500L, 0L, false, false);
         case PICK_UP_ALL:
            return new MultiMacroRun.ContainerPlan(host.resolvePickupAllClicks((PickUpAllAction)action), 0L, 0L, false, false);
         case CONTAINER_CLICK_SEQUENCE: {
            ContainerClickSequenceAction ca = (ContainerClickSequenceAction)action;
            long delay = ca.delayTicks > 0 ? ca.delayTicks * 50L : 0L;
            return new MultiMacroRun.ContainerPlan(host.resolveSequenceClicks(ca), 1500L, delay, false, false);
         }
         default:
            return new MultiMacroRun.ContainerPlan(List.of(), 0L, 0L, false, false);
      }
   }

   private Integer flowTarget(FlowAction flow) {
      return switch (flow.target) {
         case FORWARD -> Math.min(this.actions.size(), this.ip + 1 + Math.max(0, flow.amount));
         case BACK -> Math.max(0, this.ip - Math.max(0, flow.amount));
         case STEP -> Math.max(0, Math.min(this.actions.size(), flow.amount));
         case TOP -> 0;
         case END -> this.actions.size();
         case STOP -> null;
         case LABEL -> {
            Integer idx = this.labelIndex.get(LabelAction.normalize(flow.labelName));
            yield idx != null ? idx : (flow.onMissingLabel == FlowAction.MissingPolicy.STOP ? null : this.ip + 1);
         }
      };
   }

   private boolean doMove(MoveAction a, MultiMacroHost host, long now) {
      double yaw = Math.toRadians(host.currentYaw());
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      double dirX;
      double dirZ;
      switch (a.direction) {
         case BACKWARD:
            dirX = -fx;
            dirZ = -fz;
            break;
         case LEFT:
            dirX = fz;
            dirZ = -fx;
            break;
         case RIGHT:
            dirX = -fz;
            dirZ = fx;
            break;
         default:
            dirX = fx;
            dirZ = fz;
      }

      double speed = host.sprinting() ? 0.26 : 0.2;
      long durationMs = Math.max(50L, a.durationTicks * 50L);
      host.move(dirX * speed, dirZ * speed, durationMs);
      if (a.nonBlocking) {
         return false;
      } else {
         this.delayUntil = now + durationMs;
         return true;
      }
   }

   private void lookAtBlock(LookAtBlockAction a, MultiMacroHost host) {
      double tx;
      double ty;
      double tz;
      if (a.targetMode == LookAtBlockAction.TargetMode.ENTITY) {
         int id = host.nearestEntity(typeOf(a.entityIds.isEmpty() ? "" : a.entityIds.get(0)));
         double[] p = host.entityPos(id);
         if (p == null) {
            this.skip(MacroActionType.LOOK_AT_BLOCK);
            return;
         }

         tx = p[0];
         ty = p[1] + 1.0;
         tz = p[2];
      } else {
         if (a.targetMode != LookAtBlockAction.TargetMode.SPECIFIC) {
            this.skip(MacroActionType.LOOK_AT_BLOCK);
            return;
         }

         tx = a.blockX + 0.5;
         ty = a.blockY + 0.5;
         tz = a.blockZ + 0.5;
      }

      float[] angles = anglesTo(host, tx, ty, tz);
      host.look(angles[0], angles[1]);
   }

   private void interactNearestEntity(InteractEntityAction a, MultiMacroHost host) {
      if (a.targetMode != InteractEntityAction.TargetMode.ENTITY) {
         this.skip(MacroActionType.INTERACT_ENTITY);
      } else {
         String type = a.entityTargets.isEmpty() ? "" : typeOf(a.entityTargets.get(0));
         int id = host.nearestEntity(type);
         if (id < 0) {
            this.skip(MacroActionType.INTERACT_ENTITY);
         } else {
            host.interactEntity(id, false);
         }
      }
   }

   private static float[] anglesTo(MultiMacroHost host, double tx, double ty, double tz) {
      double dx = tx - host.posX();
      double dy = ty - (host.posY() + 1.62);
      double dz = tz - host.posZ();
      double horiz = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horiz)));
      return new float[]{yaw, pitch};
   }

   private boolean branchMatches(BranchAction b, MultiMacroHost host) {
      String v = b.value == null ? "" : b.value;

      return switch (b.conditionKind) {
         case ALWAYS -> true;
         case GUI_TYPE -> !v.isBlank() && !v.equalsIgnoreCase("ANY")
            ? host.openScreenTitle().toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT))
            : host.containerOpen();
         case INVENTORY_ITEM -> host.countItem(v) > 0;
         case HELD_ITEM -> host.heldItemName().toLowerCase(Locale.ROOT).replace('_', ' ').contains(typeOf(v).toLowerCase(Locale.ROOT).replace('_', ' '));
         case ENTITY_TARGET -> host.fullMode() && host.nearestEntity(typeOf(v)) >= 0;
      };
   }

   private static boolean guiOpenForWait(String guiType, MultiMacroHost host) {
      boolean menuOpen = host.customMenu() != null;
      if (MacroGuiMatcher.isCustomMenuType(guiType)) {
         return menuOpen;
      } else {
         boolean any = guiType == null || guiType.isBlank() || "ANY".equalsIgnoreCase(guiType);
         return host.containerOpen() || any && menuOpen;
      }
   }

   private boolean guiTitleMatches(String wanted, MultiMacroHost host) {
      String resolved = this.resolve(wanted == null ? "" : wanted);
      return resolved != null && !resolved.isBlank() ? host.openScreenTitle().toLowerCase(Locale.ROOT).contains(resolved.toLowerCase(Locale.ROOT)) : true;
   }

   private boolean waitConditionMet(MacroAction action, MultiMacroHost host) {
      MacroAction eff = effectiveWait(action);
      if (eff instanceof WaitForGuiAction w) {
         boolean open = guiOpenForWait(w.guiType, host);
         return w.waitMode == WaitForGuiAction.WaitMode.CLOSE ? !open : open && this.guiTitleMatches(w.guiTitle, host);
      } else if (eff instanceof WaitGuiTypeAction w) {
         boolean open = guiOpenForWait(w.guiType, host);
         return w.waitMode == WaitGuiTypeAction.WaitMode.CLOSE ? !open : open && this.guiTitleMatches(w.title, host);
      } else if (eff instanceof WaitForHealthAction w) {
         return w.below ? host.health() < w.healthThreshold : host.health() > w.healthThreshold;
      } else if (eff instanceof WaitInventoryPredicateAction w) {
         return this.invPredicateMet(w, host);
      } else if (eff instanceof WaitPosAction w) {
         return this.posReached(w, host);
      } else if (eff instanceof WaitDurabilityAction w) {
         return durabilityMet(w, host);
      } else if (eff instanceof WaitForSlotChangeAction w) {
         return host.slotChangeMet(w, this.waitSlotBaseline);
      } else if (eff instanceof WaitForPositionDeltaAction w) {
         return this.positionDeltaMet(w, host);
      } else if (eff instanceof WaitForTeleportAction w) {
         return this.teleportMet(w, host);
      } else if (eff instanceof WaitForWorldChangeAction w) {
         return this.worldChangeMet(w, host);
      } else if (eff instanceof WaitForChatAction w) {
         return this.chatMet(w, host);
      } else if (eff instanceof WaitGamemodeChangeAction w) {
         return this.gamemodeMet(w, host);
      } else if (eff instanceof WaitForEntityAction w) {
         return this.entityWaitMet(w, host);
      } else if (eff instanceof WaitEntityTargetAction w) {
         return this.entityTargetWaitMet(w, host);
      } else if (eff instanceof WaitForPacketAction w) {
         return host.packetSeen(this.waitBasePacketSeq, w.effectiveList());
      } else if (eff instanceof WaitForSoundAction w) {
         return host.soundMatched(this.waitBaseSoundSeq, w.soundIds, w.checkDistance, w.maxDistance);
      } else if (eff instanceof WaitPacketMatchAction w) {
         return host.packetMatched(this.waitBasePacketSeq, w);
      } else if (eff instanceof WaitForMacroStepAction w) {
         return host.macroStepMet(w);
      } else if (eff instanceof RevisionSyncAction) {
         return host.containerRevision() != this.waitBaseRevision;
      } else if (eff instanceof WaitForBlockAction w) {
         return w.checkMode != WaitForBlockAction.CheckMode.AT_POSITION
            ? false
            : host.blockAt(
               w.blockPos.getX(), w.blockPos.getY(), w.blockPos.getZ(), w.blockIds, w.anyBlock, w.waitBehavior == WaitForBlockAction.WaitBehavior.DESTROYED
            );
      } else if (eff instanceof WaitForCooldownAction w) {
         return !host.itemOnCooldown(w.itemTarget, w.checkMainInteractionHand);
      } else if (eff instanceof WaitFreeSlotsAction w) {
         int free = host.freeSlots();
         int actual = w.countMode == WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? 36 - free : free;
         int target = Math.max(0, Math.min(36, w.slots));

         return switch (w.comparison == null ? WaitFreeSlotsAction.Comparison.AT_MOST : w.comparison) {
            case BELOW -> actual < target;
            case AT_MOST -> actual <= target;
            case EXACT -> actual == target;
            case AT_LEAST -> actual >= target;
            case ABOVE -> actual > target;
         };
      } else {
         return false;
      }
   }

   private static MacroAction effectiveWait(MacroAction action) {
      return action instanceof WaitMovementAction wm ? wm.resolveSubAction() : action;
   }

   private void captureWaitBaseline(MacroAction action, MultiMacroHost host) {
      MacroAction eff = effectiveWait(action);
      if (eff instanceof WaitInventoryPredicateAction w) {
         this.waitBaseCount = host.countItemTarget(ItemTarget.fromLegacyEntry(w.itemName));
         this.waitBaseSlotFilled = host.slotFilled(w.slot);
      } else if (eff instanceof WaitForSlotChangeAction w) {
         this.waitSlotBaseline = host.slotChangeBaseline(w);
      } else if (eff instanceof WaitForPositionDeltaAction) {
         this.waitBaseX = host.posX();
         this.waitBaseY = host.posY();
         this.waitBaseZ = host.posZ();
      } else if (eff instanceof WaitForTeleportAction) {
         this.waitBaseX = host.posX();
         this.waitBaseY = host.posY();
         this.waitBaseZ = host.posZ();
         this.waitBaseTeleportSeq = host.teleportSeq();
      } else if (eff instanceof WaitForWorldChangeAction) {
         this.waitBaseDimension = host.dimension();
      } else if (eff instanceof WaitGamemodeChangeAction) {
         this.waitBaseGameMode = host.gameMode();
      } else if (eff instanceof WaitForChatAction) {
         this.waitBaseChatSeq = host.chatSeq();
      } else if (eff instanceof WaitForPacketAction) {
         host.setPacketCapture(true);
         this.waitBasePacketSeq = host.packetSeq();
         this.packetWaitArmed = true;
      } else if (eff instanceof WaitForSoundAction) {
         host.setSoundCapture(true);
         this.waitBaseSoundSeq = host.soundSeq();
         this.soundWaitArmed = true;
      } else if (eff instanceof WaitPacketMatchAction) {
         host.setPacketCapture(true);
         this.waitBasePacketSeq = host.packetSeq();
         this.packetWaitArmed = true;
      }
   }

   private boolean chatMet(WaitForChatAction w, MultiMacroHost host) {
      for (String line : host.chatSince(this.waitBaseChatSeq)) {
         if (chatMatches(w, line)) {
            return true;
         }
      }

      return false;
   }

   private static boolean chatMatches(WaitForChatAction w, String text) {
      if (text == null) {
         return false;
      } else {
         String pattern = w.patternJson != null && !w.patternJson.isBlank() ? w.pattern : MacroExecutor.normalizeManualText(w.pattern);
         if (pattern != null && !pattern.isBlank()) {
            MacroCapturePattern.Mode mode = w.effectiveMatchMode();
            return mode == MacroCapturePattern.Mode.MATCH
               ? MacroExecutor.fuzzyChatMatch(pattern, text, WaitForChatAction.clampFuzzyPercent(w.fuzzyPercent))
               : MacroCapturePattern.match(mode, pattern, text).isPresent();
         } else {
            return true;
         }
      }
   }

   private boolean gamemodeMet(WaitGamemodeChangeAction w, MultiMacroHost host) {
      int cur = host.gameMode();
      if (cur < 0) {
         return false;
      } else {
         return w.match == WaitGamemodeChangeAction.Match.TO_MODE ? cur == w.targetGameType().getId() : cur != this.waitBaseGameMode;
      }
   }

   private boolean entityWaitMet(WaitForEntityAction w, MultiMacroHost host) {
      if (!host.fullMode()) {
         return false;
      } else if (w.checkMode != WaitForEntityAction.CheckMode.LOOKING_AT && w.checkMode != WaitForEntityAction.CheckMode.MOUNTED_IN) {
         double radius = w.checkMode == WaitForEntityAction.CheckMode.WITHIN_REACH ? 4.5 : w.radius;
         return host.entityWithin(w.entityIds, w.containerEntitiesOnly, w.centerOnPlayer, w.x, w.y, w.z, radius);
      } else {
         return false;
      }
   }

   private boolean entityTargetWaitMet(WaitEntityTargetAction w, MultiMacroHost host) {
      if (!host.fullMode()) {
         return false;
      } else if (w.condition != WaitEntityTargetAction.EntityCondition.LOOKING_AT && w.condition != WaitEntityTargetAction.EntityCondition.MOUNTED_IN) {
         List<String> types = w.entityId != null && !w.entityId.isBlank() ? List.of(w.entityId) : List.of();
         return host.entityWithin(types, w.containerEntitiesOnly, true, 0.0, 0.0, 0.0, w.range);
      } else {
         return false;
      }
   }

   private boolean assertPasses(AssertAction a, MultiMacroHost host) {
      String item = this.resolve(a.itemName);

      return switch (a.check) {
         case CONNECTION -> host.macroReady();
         case GUI_TYPE -> {
            String g = this.resolve(a.guiType);
            yield g != null && !g.isBlank() && !g.equalsIgnoreCase("ANY")
               ? host.openScreenTitle().toLowerCase(Locale.ROOT).contains(g.toLowerCase(Locale.ROOT))
               : host.containerOpen();
         }
         case HELD_ITEM -> host.heldItemName()
            .toLowerCase(Locale.ROOT)
            .replace('_', ' ')
            .contains(stripNamespace(item).toLowerCase(Locale.ROOT).replace('_', ' '));
         case INVENTORY_ITEM -> host.countItemTarget(ItemTarget.fromLegacyEntry(item)) > 0;
         case HAS_BUNDLE -> host.countItem("bundle") > 0;
         case HAS_WRITABLE_BOOK -> host.countItem("writable_book") > 0;
         default -> true;
      };
   }

   private static String stripNamespace(String s) {
      if (s == null) {
         return "";
      } else {
         int colon = s.indexOf(58);
         return colon >= 0 ? s.substring(colon + 1) : s;
      }
   }

   private List<String> buildPayCommands(PayAction p) {
      List<String> out = new ArrayList<>();
      if (p.players != null && !p.players.isEmpty()) {
         List<String> targets = new ArrayList<>();

         for (String player : p.players) {
            if (player != null) {
               String t = this.resolve(player).trim();
               if (!t.isEmpty() && !targets.contains(t)) {
                  targets.add(t);
               }
            }
         }

         if (targets.isEmpty()) {
            return out;
         } else {
            long amount = Math.max(0L, PayAction.parseAmount(this.resolve(p.amountInput)));
            long[] divided = p.divideEnabled ? PayAction.distribute(amount, targets.size()) : null;
            String template = p.commandTemplate != null && !p.commandTemplate.isBlank() ? p.commandTemplate : "/pay <player> <amount>";

            for (int i = 0; i < targets.size(); i++) {
               long per = divided != null ? divided[i] : amount;
               if (per > 0L) {
                  String amountValue = String.valueOf(per);
                  String cmd = template.replace("<player>", targets.get(i))
                     .replace("{player}", targets.get(i))
                     .replace("<amount>", amountValue)
                     .replace("{amount}", amountValue)
                     .trim();
                  cmd = this.resolve(cmd).trim();
                  if (!cmd.isEmpty()) {
                     out.add(cmd);
                  }
               }
            }

            return out;
         }
      } else {
         return out;
      }
   }

   private boolean positionDeltaMet(WaitForPositionDeltaAction w, MultiMacroHost host) {
      if (!host.hasPosition()) {
         return false;
      } else {
         double dx = host.posX() - this.waitBaseX;
         double dy = w.horizontalOnly ? 0.0 : host.posY() - this.waitBaseY;
         double dz = host.posZ() - this.waitBaseZ;
         double d2 = dx * dx + dy * dy + dz * dz;
         double dist = Math.max(0.0, w.distance);
         return dist <= 0.0 ? d2 > 1.0E-4 : d2 >= dist * dist;
      }
   }

   private boolean teleportMet(WaitForTeleportAction w, MultiMacroHost host) {
      if (host.teleportSeq() == this.waitBaseTeleportSeq) {
         return false;
      } else if (w.minDistance <= 0.0) {
         return true;
      } else if (!host.hasPosition()) {
         return false;
      } else {
         double dx = host.posX() - this.waitBaseX;
         double dy = w.horizontalOnly ? 0.0 : host.posY() - this.waitBaseY;
         double dz = host.posZ() - this.waitBaseZ;
         return dx * dx + dy * dy + dz * dz >= w.minDistance * w.minDistance;
      }
   }

   private boolean worldChangeMet(WaitForWorldChangeAction w, MultiMacroHost host) {
      String cur = normalizeDimension(host.dimension());
      if (cur.equals(normalizeDimension(this.waitBaseDimension))) {
         return false;
      } else {
         String target = w.targetDimension;
         return target == null || target.isBlank() || cur.equals(normalizeDimension(target));
      }
   }

   private static String normalizeDimension(String raw) {
      if (raw == null) {
         return "";
      } else {
         String v = raw.trim().toLowerCase(Locale.ROOT);
         if (v.isEmpty()) {
            return "";
         } else if (v.equals("nether") || v.equals("the_nether")) {
            return "minecraft:the_nether";
         } else if (v.equals("overworld") || v.equals("world")) {
            return "minecraft:overworld";
         } else if (!v.equals("end") && !v.equals("the_end")) {
            return v.contains(":") ? v : "minecraft:" + v;
         } else {
            return "minecraft:the_end";
         }
      }
   }

   private boolean invPredicateMet(WaitInventoryPredicateAction w, MultiMacroHost host) {
      ItemTarget t = ItemTarget.fromLegacyEntry(w.itemName);

      return switch (w.condition) {
         case ITEM_EXISTS -> host.countItemTarget(t) > 0;
         case COUNT_AT_LEAST -> host.countItemTarget(t) >= Math.max(1, w.count);
         case COUNT_CHANGED -> host.countItemTarget(t) != this.waitBaseCount;
         case COUNT_INCREASED -> host.countItemTarget(t) > this.waitBaseCount;
         case COUNT_DECREASED -> host.countItemTarget(t) < this.waitBaseCount;
         case SLOT_EMPTY -> !host.slotFilled(w.slot);
         case SLOT_FILLED -> host.slotFilled(w.slot);
         case SLOT_CHANGED -> host.slotFilled(w.slot) != this.waitBaseSlotFilled;
         case INVENTORY_FULL -> host.freeSlots() <= 0;
         case INVENTORY_EMPTY -> host.countItem("") <= 0;
         case CURSOR_MATCHES -> host.cursorMatches(t);
         case CURSOR_EMPTY -> host.cursorEmpty();
         case CURSOR_FILLED -> !host.cursorEmpty();
         case SELECTED_SLOT -> host.selectedHotbar() == Math.max(0, Math.min(8, w.slot));
      };
   }

   private boolean posReached(WaitPosAction w, MultiMacroHost host) {
      if (!host.hasPosition()) {
         return false;
      } else {
         double dx = host.posX() - w.x;
         double dy = host.posY() - w.y;
         double dz = host.posZ() - w.z;
         double lee = Math.max(0.0, w.leeway);
         if (dx * dx + dy * dy + dz * dz > lee * lee) {
            return false;
         } else if (!w.checkRotation) {
            return true;
         } else {
            float dyaw = Math.abs(wrapDegrees(host.currentYaw() - w.yaw));
            float dpitch = Math.abs(host.currentPitch() - w.pitch);
            return dyaw <= w.rotLeeway && dpitch <= w.rotLeeway;
         }
      }
   }

   static boolean durabilityMet(WaitDurabilityAction w, MultiMacroHost host) {
      return switch (w.targetMode) {
         case HELD -> durabilityStackMet(w, host.heldDurability()) || durabilityStackMet(w, host.durabilityAtInv(40));
         case SLOT -> durabilityStackMet(w, host.durabilityAtInv(w.slot));
         case ITEM -> {
            ItemTarget t = ItemTarget.fromLegacyEntry(w.itemName);
            yield !t.hasIdentity() && !t.hasSlot()
               ? durabilityStackMet(w, host.heldDurability()) || durabilityStackMet(w, host.durabilityAtInv(40))
               : durabilityStackMet(w, host.itemDurability(t));
         }
      };
   }

   private static boolean durabilityStackMet(WaitDurabilityAction w, int[] dur) {
      if (dur != null && dur.length >= 2 && dur[1] > 0) {
         int damage = Math.max(0, dur[0]);
         int max = dur[1];
         int remaining = Math.max(0, max - damage);

         int metric = switch (w.measurement) {
            case REMAINING -> remaining;
            case DAMAGE_USED -> damage;
            case PERCENT_REMAINING -> Math.round(remaining * 100.0F / max);
         };
         int cap = w.measurement == WaitDurabilityAction.Measurement.PERCENT_REMAINING ? 100 : max;
         int target = Math.max(0, Math.min(cap, w.value));

         return switch (w.comparison) {
            case BELOW -> metric < target;
            case AT_MOST -> metric <= target;
            case EXACT -> metric == target;
            case AT_LEAST -> metric >= target;
            case ABOVE -> metric > target;
         };
      } else {
         return false;
      }
   }

   private static float wrapDegrees(float deg) {
      float d = deg % 360.0F;
      if (d >= 180.0F) {
         d -= 360.0F;
      }

      if (d < -180.0F) {
         d += 360.0F;
      }

      return d;
   }

   private static boolean waitEvaluable(MacroAction eff, MultiMacroHost host) {
      if (eff instanceof WaitForEntityAction w) {
         return host.fullMode() && w.checkMode != WaitForEntityAction.CheckMode.LOOKING_AT && w.checkMode != WaitForEntityAction.CheckMode.MOUNTED_IN;
      } else if (!(eff instanceof WaitEntityTargetAction w)) {
         return eff instanceof WaitForBlockAction wx ? wx.checkMode == WaitForBlockAction.CheckMode.AT_POSITION : true;
      } else {
         return host.fullMode()
            && w.condition != WaitEntityTargetAction.EntityCondition.LOOKING_AT
            && w.condition != WaitEntityTargetAction.EntityCondition.MOUNTED_IN;
      }
   }

   private static long waitTimeout(MacroAction action) {
      MacroAction eff = effectiveWait(action);
      if (eff instanceof WaitForGuiAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitGuiTypeAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitFreeSlotsAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitPacketMatchAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitInventoryPredicateAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitDurabilityAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitForChatAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitForEntityAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitEntityTargetAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitGamemodeChangeAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else if (eff instanceof WaitForMacroStepAction w && w.timeoutMs > 0) {
         return w.timeoutMs;
      } else {
         return !(eff instanceof WaitForSlotChangeAction)
               && !(eff instanceof WaitForPositionDeltaAction)
               && !(eff instanceof WaitForTeleportAction)
               && !(eff instanceof WaitForWorldChangeAction)
               && !(eff instanceof WaitPosAction)
               && !(eff instanceof WaitForChatAction)
               && !(eff instanceof WaitForEntityAction)
               && !(eff instanceof WaitEntityTargetAction)
               && !(eff instanceof WaitGamemodeChangeAction)
               && !(eff instanceof WaitForPacketAction)
               && !(eff instanceof WaitForSoundAction)
               && !(eff instanceof WaitPacketMatchAction)
               && !(eff instanceof WaitForCooldownAction)
               && !(eff instanceof RevisionSyncAction)
               && !(eff instanceof WaitForBlockAction)
               && !(eff instanceof WaitForMacroStepAction)
            ? 10000L
            : 600000L;
      }
   }

   private static String typeOf(String ref) {
      if (ref != null && !ref.isBlank()) {
         String s = ref.trim();
         if (s.startsWith("~")) {
            String[] parts = s.split("~");
            if (parts.length >= 3) {
               s = parts[2];
            }
         }

         int colon = s.indexOf(58);
         if (colon >= 0) {
            s = s.substring(colon + 1);
         }

         return s;
      } else {
         return "";
      }
   }

   private void clearBookkeepingOutside(int target) {
      this.branchElseStart = -1;
      this.branchElseCount = 0;

      while (!this.repeatStack.isEmpty()) {
         int[] top = this.repeatStack.peek();
         if (target >= top[0] && target < top[1]) {
            break;
         }

         this.repeatStack.pop();
      }

      if (this.raceBlockEnd >= 0 && (target < 0 || target >= this.raceBlockEnd)) {
         this.raceBlockEnd = -1;
         this.raceSkipIndices = null;
         this.raceConditions = null;
         this.raceWaiting = false;
      }
   }

   private void resetForLoop() {
      this.ip = 0;
      this.branchElseStart = -1;
      this.branchElseCount = 0;
      this.repeatStack.clear();
      this.delayUntil = 0L;
      this.waitingForGui = false;
      this.waitAction = null;
      this.pendingUsePhase = null;
      this.pendingUseRemaining = 0;
      this.pendingUseRelease = false;
      this.pendingUseHoldMs = 0L;
      this.pendingCapture = null;
      this.pendingSuggestionId = -1;
      this.pendingSuggestionQuery = "";
      this.pendingCaptureDeadline = 0L;
      this.pendingBurst = null;
      this.pendingBurstRemaining = 0;
      this.pendingBurstDelayMs = 0L;
      this.pendingBook = null;
      this.pendingBookIndex = 0;
      this.pendingBookTotal = 0;
      this.pendingBookHotbarMask = 0;
      this.pendingBookDelayMs = 0L;
      this.pendingBookSwapAttempts = 0;
      this.clearPendingTp();
      this.pendingCustomMenu = null;
      this.pendingCustomMenuDeadline = 0L;
      this.leafBurstRemaining = 0;
      this.clickPlan = null;
      this.clickPlanIdx = 0;
      this.clickResolveDeadline = 0L;
      this.clickPlanDelayMs = 0L;
      this.clickPlanCloseAfter = false;
      this.clickPlanCloseSilent = false;
      this.pendingClickCloseAfter = false;
      this.pendingClickCloseSilent = false;
      this.cmdQueue = null;
      this.cmdQueueIdx = 0;
      this.cmdQueueDelayMs = 0L;
      this.packetWaitArmed = false;
      this.soundWaitArmed = false;
      this.raceWaiting = false;
      this.raceBlockEnd = -1;
      this.raceSkipIndices = null;
      this.raceConditions = null;
   }

   private void requestTerminate(String why) {
      this.terminateRequested = true;
      this.terminateReason = why != null && !why.isBlank() ? why : "done";
   }

   private boolean consumeTerminate() {
      if (!this.terminateRequested) {
         return this.done;
      } else {
         this.terminateRequested = false;
         return this.finishOrRunFinally(this.terminateReason);
      }
   }

   private void finish(String why) {
      this.done = true;
      this.status = why != null && !why.isBlank() ? why : "done";
   }

   private boolean finishOrRunFinally(String why) {
      if (this.finallyRegistered && !this.finallyRan && !this.runningFinally) {
         this.finallyRan = true;
         this.runningFinally = true;
         this.pendingFinishReason = why;
         this.ip = this.finallyStart;
         this.clearBookkeepingOutside(this.finallyStart);
         return false;
      } else {
         this.finish(why);
         return true;
      }
   }

   private void skip(MacroActionType type) {
      this.status = "skipped " + type.name().toLowerCase(Locale.ROOT);
   }

   private void skipFullOnly(MacroActionType type, MultiMacroHost host) {
      this.skipUnsupported(type, host, "entity tracking is unavailable on this bot");
   }

   private void skipUnsupported(MacroActionType type, MultiMacroHost host, String reason) {
      this.skip(type);
      if (this.notedSkips.add(type)) {
         host.macroNote("Skipped " + type.name() + ": " + reason + ".");
      }
   }

   boolean hasActiveCustomMenuDeadline(long now) {
      return this.pendingCustomMenu != null && now < this.pendingCustomMenuDeadline;
   }

   boolean isHandlingCustomMenu() {
      return this.pendingCustomMenu != null;
   }

   boolean handlesCustomMenu() {
      return this.hasCustomMenuAction || this.pendingCustomMenu != null;
   }

   private static boolean isLoginPrefixType(MacroActionType type) {
      return type == MacroActionType.CUSTOM_MENU || type == MacroActionType.WAIT_GUI || type == MacroActionType.WAIT_GUI_TYPE || type == MacroActionType.DELAY;
   }

   private boolean pollCustomMenu(MultiMacroHost host, long now) {
      CustomMenuAction action = this.pendingCustomMenu;
      if (action == null) {
         return true;
      } else {
         CustomMenuSnapshot snapshot = host.customMenu();
         if (snapshot == null) {
            if (now < this.pendingCustomMenuDeadline) {
               return false;
            } else {
               this.requestTerminate("custom screen never appeared (timed out)");
               return true;
            }
         } else {
            CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(
               action, snapshot, value -> host.resolveCustomMenuValue(value, this.vars)
            );
            if (!prepared.success()) {
               if (prepared.error() != null && prepared.error().contains("unavailable")) {
                  this.pendingCustomMenuDeadline = now + action.boundedTimeout();
                  this.status = "waiting for password";
                  return false;
               } else {
                  this.requestTerminate("custom screen failed: " + prepared.error());
                  return true;
               }
            } else {
               CustomMenuSubmitResult result = host.submitCustomMenu(snapshot, prepared.submission());
               if (result != null && result.success()) {
                  this.status = "submitted custom screen";
                  return true;
               } else {
                  String error = result == null ? "no submission result" : result.error();
                  this.requestTerminate("custom screen failed: " + error);
                  return true;
               }
            }
         }
      }
   }

   private boolean beginCapture(CaptureValueAction a, MultiMacroHost host, long now) {
      CaptureValueAction.Source source = a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source;
      if (source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
         String query = this.autofillQuery(a);
         if (query.isBlank()) {
            return false;
         } else if (a.autofillCacheList) {
            return this.beginAutofillSweep(a, query, host, now);
         } else {
            List<String> cached = this.suggestionCache.get(query);
            if (cached != null && this.applyListCapture(a, cached)) {
               return false;
            } else {
               int requestId = host.requestCommandSuggestions(query);
               if (requestId < 0) {
                  return false;
               } else {
                  this.pendingCapture = a;
                  this.pendingSuggestionId = requestId;
                  this.pendingSuggestionQuery = query;
                  this.pendingCaptureDeadline = now + Math.max(100L, Math.min(60000L, (long)a.autofillTimeoutMs));
                  return true;
               }
            }
         }
      } else if (source == CaptureValueAction.Source.SCOREBOARD && a.waitForTrigger) {
         this.pendingCapture = a;
         this.pendingCaptureDeadline = now + 600000L;
         CaptureValueAction.ScoreboardLine line = this.selectScoreboardLine(a, host.scoreboardLines());
         if (line != null) {
            this.pendingScoreboardBaselineReady = true;
            this.pendingScoreboardKey = line.key();
            this.pendingScoreboardText = safe(line.text());
         }

         return true;
      } else if (this.tryCapture(a, host)) {
         return false;
      } else if (!a.waitForTrigger) {
         return false;
      } else {
         this.pendingCapture = a;
         this.pendingCaptureDeadline = now + 600000L;
         return true;
      }
   }

   private boolean pollPendingCapture(MultiMacroHost host, long now) {
      CaptureValueAction.Source source = this.pendingCapture.source == null ? CaptureValueAction.Source.GUI_TITLE : this.pendingCapture.source;
      if (source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
         List<String> suggestions = host.commandSuggestions(this.pendingSuggestionId);
         if (suggestions == null) {
            return now >= this.pendingCaptureDeadline;
         } else if (this.pendingAutofillUnion != null) {
            MultiMacroRun.AutofillUnion union = this.pendingAutofillUnion;
            union.names.addAll(suggestions);
            union.cap = Math.max(union.cap, suggestions.size());
            union.baseFetched = true;
            this.applyListCapture(this.pendingCapture, unionPool(union, this.pendingCapture));
            this.driveAutofillSweep(union, host);
            return true;
         } else {
            this.applyListCapture(this.pendingCapture, suggestions);
            return true;
         }
      } else if (source == CaptureValueAction.Source.SCOREBOARD) {
         CaptureValueAction.ScoreboardLine line = this.selectScoreboardLine(this.pendingCapture, host.scoreboardLines(), this.pendingScoreboardKey);
         if (line == null) {
            return now >= this.pendingCaptureDeadline;
         } else if (!this.pendingScoreboardBaselineReady) {
            this.pendingScoreboardBaselineReady = true;
            this.pendingScoreboardKey = line.key();
            this.pendingScoreboardText = safe(line.text());
            return now >= this.pendingCaptureDeadline;
         } else {
            String currentText = safe(line.text());
            if (!currentText.equals(this.pendingScoreboardText)) {
               this.pendingScoreboardKey = line.key();
               this.pendingScoreboardText = currentText;
               if (this.applyPreview(this.pendingCapture.previewScoreboardLine(line))) {
                  return true;
               }
            }

            return now >= this.pendingCaptureDeadline;
         }
      } else {
         return this.tryCapture(this.pendingCapture, host) ? true : now >= this.pendingCaptureDeadline;
      }
   }

   private boolean tryCapture(CaptureValueAction a, MultiMacroHost host) {
      return switch (a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source) {
         case TABLIST -> this.applyListCapture(a, host.tablistNames(a.excludeSelf));
         case SCOREBOARD -> this.applyScoreboardCapture(a, host.scoreboardLines());
         case COMMAND_AUTOFILL -> false;
         default -> this.captureValue(a, host);
      };
   }

   private boolean captureValue(CaptureValueAction a, MultiMacroHost host) {
      String text = this.rawCaptureText(a, host);
      if (text != null && captureMatches(a, text)) {
         Map<String, String> additions = new LinkedHashMap<>();
         if (isPatternMode(a) && a.pattern != null && !a.pattern.isBlank()) {
            MacroCapturePattern.match(a.matchMode, a.pattern, text).ifPresent(r -> r.values().forEach((k, v) -> additions.put(k, v.value())));
         }

         String saveAs = MacroVariableContext.cleanRootName(a.saveAs);
         if (!saveAs.isEmpty()) {
            additions.putIfAbsent(saveAs, text);
         }

         if (additions.isEmpty()) {
            return false;
         } else {
            CaptureValueAction.NumberMode mode = a.numberMode == null ? CaptureValueAction.NumberMode.OFF : a.numberMode;
            if (mode != CaptureValueAction.NumberMode.OFF) {
               additions.replaceAll((k, v) -> normalizeCapturedNumber(v, k.equals(saveAs), numberStyle(mode)));
            }

            if (!applyNumberModifier(a, additions)) {
               return false;
            } else {
               this.putPlainVars(additions);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private String rawCaptureText(CaptureValueAction a, MultiMacroHost host) {
      return switch (a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source) {
         case GUI_TITLE -> {
            String t = host.openScreenTitle();
            yield t != null && !t.isEmpty() ? t : null;
         }
         case RECENT_CHAT -> {
            List<String> lines = host.chatSince(0L);

            for (int i = lines.size() - 1; i >= 0; i--) {
               if (captureMatches(a, lines.get(i))) {
                  yield lines.get(i);
               }
            }

            yield null;
         }
         case HELD_ITEM, CURSOR_ITEM, GUI_ITEM, PLAYER_ITEM -> {
            ItemTarget filter = a.itemFilter != null && !a.itemFilter.isBlank() ? ItemTarget.fromLegacyEntry(this.resolve(a.itemFilter)) : null;
            yield host.captureItemText(a, filter);
         }
         default -> null;
      };
   }

   private boolean applyListCapture(CaptureValueAction a, List<String> candidates) {
      if (candidates != null && !candidates.isEmpty() && CaptureListSelector.filterError(a.listFilter, a.listFilterText).isBlank()) {
         List<String> pool = CaptureListSelector.filter(candidates, a.listFilter, a.listFilterText);
         pool = CaptureListSelector.exclude(pool, a.listExcludeText);
         CaptureListSelector.State state = this.captureSelections.computeIfAbsent(a, ignored -> new CaptureListSelector.State());
         Optional<CaptureListSelector.Pick> picked = CaptureListSelector.pick(pool, a.listSelection, a.listPickPosition, state);
         if (picked.isEmpty()) {
            return false;
         } else {
            String value = picked.get().value();
            if (a.listStripPrefix) {
               value = CaptureListSelector.stripMatch(value, a.listFilter, a.listFilterText);
            }

            String saveAs = MacroVariableContext.cleanRootName(a.saveAs);
            if (saveAs.isEmpty()) {
               return false;
            } else {
               this.putPlainVars(Map.of(saveAs, value));
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean applyScoreboardCapture(CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines) {
      CaptureValueAction.ScoreboardLine selected = this.selectScoreboardLine(a, lines);
      return selected != null && this.applyPreview(a.previewScoreboardLine(selected));
   }

   private CaptureValueAction.ScoreboardLine selectScoreboardLine(CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines) {
      return this.selectScoreboardLine(a, lines, a.scoreboardRow);
   }

   private CaptureValueAction.ScoreboardLine selectScoreboardLine(CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines, String preferredKeyValue) {
      if (lines != null && !lines.isEmpty()) {
         String preferredKey = preferredKeyValue == null ? "" : preferredKeyValue;

         for (CaptureValueAction.ScoreboardLine line : lines) {
            if (!preferredKey.isBlank() && preferredKey.equals(line.key())) {
               return line;
            }
         }

         CaptureValueAction.ScoreboardLine rowCandidate = null;

         for (CaptureValueAction.ScoreboardLine linex : lines) {
            if (sameScoreboardObjective(a, linex) && linex.row() == a.scoreboardRowIndex) {
               rowCandidate = linex;
               break;
            }
         }

         if (rowCandidate != null && a.previewScoreboardLine(rowCandidate).success()) {
            return rowCandidate;
         } else {
            CaptureValueAction.ScoreboardLine selected = this.bestScoreboardLine(a, lines, true);
            return selected != null ? selected : this.bestScoreboardLine(a, lines, false);
         }
      } else {
         return null;
      }
   }

   private CaptureValueAction.ScoreboardLine bestScoreboardLine(CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines, boolean requireObjective) {
      CaptureValueAction.ScoreboardLine selected = null;
      int bestDistance = Integer.MAX_VALUE;

      for (CaptureValueAction.ScoreboardLine line : lines) {
         if (!requireObjective || sameScoreboardObjective(a, line)) {
            CaptureValueAction.Preview preview = a.previewScoreboardLine(line);
            if (preview.success()) {
               int distance = a.scoreboardRowIndex < 0 ? line.row() : Math.abs(line.row() - a.scoreboardRowIndex);
               if (selected == null || distance < bestDistance) {
                  selected = line;
                  bestDistance = distance;
               }
            }
         }
      }

      return selected;
   }

   private static boolean sameScoreboardObjective(CaptureValueAction action, CaptureValueAction.ScoreboardLine line) {
      return action.scoreboardObjective == null || action.scoreboardObjective.isBlank() || action.scoreboardObjective.equals(line.objective());
   }

   private void putPlainVars(Map<String, String> additions) {
      additions.forEach((name, value) -> {
         this.vars.put(name, value == null ? "" : value);
         this.structuredVars.remove(name);
      });
   }

   private boolean applyPreview(CaptureValueAction.Preview preview) {
      if (preview != null && preview.success() && !preview.values().isEmpty()) {
         preview.values().forEach((name, value) -> {
            this.vars.put(name, value == null ? "" : value.value());
            if (value == null) {
               this.structuredVars.remove(name);
            } else {
               this.structuredVars.put(name, value);
            }
         });
         return true;
      } else {
         return false;
      }
   }

   private boolean beginAutofillSweep(CaptureValueAction a, String query, MultiMacroHost host, long now) {
      MultiMacroRun.AutofillUnion union = this.autofillUnions.computeIfAbsent(query, MultiMacroRun.AutofillUnion::new);
      if (union.baseFetched) {
         this.applyListCapture(a, unionPool(union, a));
         this.driveAutofillSweep(union, host);
         return false;
      } else {
         int id = host.requestCommandSuggestions(query);
         if (id < 0) {
            this.applyListCapture(a, unionPool(union, a));
            return false;
         } else {
            this.pendingCapture = a;
            this.pendingSuggestionId = id;
            this.pendingSuggestionQuery = query;
            this.pendingAutofillUnion = union;
            this.pendingCaptureDeadline = now + Math.max(100L, Math.min(60000L, (long)a.autofillTimeoutMs));
            return true;
         }
      }
   }

   private void driveAutofillSweep(MultiMacroRun.AutofillUnion union, MultiMacroHost host) {
      if (union.inflightId >= 0) {
         List<String> reply = host.commandSuggestions(union.inflightId);
         if (reply == null) {
            return;
         }

         union.names.addAll(reply);
         int size = reply.size();
         boolean truncated = size >= union.cap && union.cap >= 8;
         union.cap = Math.max(union.cap, size);
         if (truncated) {
            union.sweepQueue.add(union.inflightQuery);
         }

         union.inflightId = -1;
         union.inflightQuery = "";
      }

      if (union.names.size() >= 10000) {
         union.sweepQueue.clear();
      } else {
         String target = nextSweepTarget(union);
         if (target != null) {
            int id = host.requestCommandSuggestions(target);
            if (id >= 0) {
               union.inflightId = id;
               union.inflightQuery = target;
            }
         }
      }
   }

   private static String nextSweepTarget(MultiMacroRun.AutofillUnion union) {
      while (!union.sweepQueue.isEmpty()) {
         if (union.alphabetCursor < "abcdefghijklmnopqrstuvwxyz0123456789_".length()) {
            String target = union.sweepQueue.peek() + "abcdefghijklmnopqrstuvwxyz0123456789_".charAt(union.alphabetCursor);
            union.alphabetCursor++;
            return target;
         }

         union.sweepQueue.poll();
         union.alphabetCursor = 0;
      }

      return null;
   }

   private static List<String> unionPool(MultiMacroRun.AutofillUnion union, CaptureValueAction a) {
      List<String> pool = new ArrayList<>(union.names);
      boolean ordered = a.listSelection == CaptureListSelector.Selection.SEQUENTIAL
         || a.listSelection == CaptureListSelector.Selection.FIRST
         || a.listSelection == CaptureListSelector.Selection.LAST
         || a.listSelection == CaptureListSelector.Selection.POSITION;
      if (ordered) {
         pool.sort(String.CASE_INSENSITIVE_ORDER);
      }

      return pool;
   }

   private String autofillQuery(CaptureValueAction a) {
      String query = this.resolve(a.autofillCommand);
      if (query == null) {
         return "";
      } else {
         if (!query.startsWith("/")) {
            query = "/" + query;
         }

         if (!query.contains(" ")) {
            query = query + " ";
         }

         String prefix = a.listFilter == CaptureListSelector.Filter.PREFIX && a.listFilterText != null ? this.resolve(a.listFilterText).trim() : "";
         if (!prefix.isEmpty() && !prefix.contains(" ") && query.endsWith(" ")) {
            int end = query.length();

            while (end > 0 && query.charAt(end - 1) == ' ') {
               end--;
            }

            query = query.substring(0, end) + " " + prefix;
         }

         return query;
      }
   }

   private int sendBook(NbtBookAction book, int index, int total, MultiMacroHost host) {
      List<String> pages = this.bookPages(book);
      if (pages != null && !pages.isEmpty()) {
         String title = book.title == null ? "" : this.resolve(book.title);
         if (book.appendCount && total > 1 && index > 0) {
            title = title + " #" + (index + 1);
         }

         int slot = host.writeBook(pages, title, book.sign, book.requireHeldWritableBook, this.pendingBookHotbarMask);
         if (slot == -2) {
            return -2;
         } else if (slot >= 0 && slot <= 8) {
            this.pendingBookHotbarMask |= 1 << slot;
            return slot;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private List<String> bookPages(NbtBookAction book) {
      int pageCount = Math.max(1, Math.min(100, book.pages));
      int chars = Math.max(1, Math.min(1024, book.characters));
      String source = book.dataSource == null ? "Random" : book.dataSource.trim();
      if ("File".equalsIgnoreCase(source)) {
         return null;
      } else if ("Pasted".equalsIgnoreCase(source) && book.customComponent != null && !book.customComponent.isEmpty()) {
         String text = this.resolve(book.customComponent);
         List<String> pages = new ArrayList<>(pageCount);

         for (int offset = 0; offset < text.length() && pages.size() < pageCount; offset += chars) {
            pages.add(text.substring(offset, Math.min(text.length(), offset + chars)));
         }

         if (pages.isEmpty()) {
            pages.add("");
         }

         return List.copyOf(pages);
      } else {
         return RiptideBookPayloadBuilder.randomPages(pageCount, chars, book.randomType, new Random());
      }
   }

   private void finishPendingBook(MultiMacroHost host, boolean completed) {
      NbtBookAction book = this.pendingBook;
      this.pendingBook = null;
      this.pendingBookIndex = 0;
      this.pendingBookTotal = 0;
      this.pendingBookHotbarMask = 0;
      this.pendingBookDelayMs = 0L;
      this.pendingBookSwapAttempts = 0;
      if (completed && book != null && book.disconnectAfter) {
         host.disconnectBot("NBT book macro");
      }
   }

   private static boolean isPatternMode(CaptureValueAction a) {
      return a.matchMode != null && a.matchMode != MacroCapturePattern.Mode.MATCH;
   }

   private static boolean captureMatches(CaptureValueAction a, String text) {
      if (text == null) {
         return false;
      } else if (a.pattern == null || a.pattern.isBlank()) {
         return true;
      } else {
         return isPatternMode(a)
            ? MacroCapturePattern.match(a.matchMode, a.pattern, text).isPresent()
            : text.toLowerCase(Locale.ROOT).contains(a.pattern.toLowerCase(Locale.ROOT));
      }
   }

   private static MacroTemplate.NumberStyle numberStyle(CaptureValueAction.NumberMode mode) {
      return mode == CaptureValueAction.NumberMode.DROP_CENTS ? MacroTemplate.NumberStyle.WHOLE : MacroTemplate.NumberStyle.AUTO;
   }

   private static String normalizeCapturedNumber(String v, boolean embedded, MacroTemplate.NumberStyle style) {
      BigDecimal n = parseCapturedNumber(v, embedded, style);
      return n == null ? v : formatCapturedNumber(n);
   }

   private static boolean applyNumberModifier(CaptureValueAction a, Map<String, String> values) {
      CaptureValueAction.NumberModifier mod = a.numberModifier == null ? CaptureValueAction.NumberModifier.NONE : a.numberModifier;
      if (mod != CaptureValueAction.NumberModifier.NONE && !values.isEmpty()) {
         if (!Double.isFinite(a.numberModifierAmount)) {
            return false;
         } else if (mod == CaptureValueAction.NumberModifier.DIVIDE && a.numberModifierAmount == 0.0) {
            return false;
         } else {
            BigDecimal operand = BigDecimal.valueOf(a.numberModifierAmount);
            String outputName = MacroVariableContext.cleanRootName(a.saveAs);
            boolean targetTracked = !outputName.isBlank() && values.containsKey(outputName);
            MacroTemplate.NumberStyle style = numberStyle(a.numberMode == null ? CaptureValueAction.NumberMode.OFF : a.numberMode);
            boolean ok = false;

            for (Entry<String, String> e : values.entrySet()) {
               boolean target = targetTracked && e.getKey().equals(outputName);
               BigDecimal n = parseCapturedNumber(e.getValue(), target, style);
               if (n != null) {
                  BigDecimal r = switch (mod) {
                     case PLUS -> n.add(operand);
                     case MINUS -> n.subtract(operand);
                     case MULTIPLY -> n.multiply(operand);
                     case DIVIDE -> divideForDisplay(n, operand);
                     case PLUS_PERCENT -> roundForDisplay(n.multiply(BigDecimal.ONE.add(CaptureValueAction.percentFactor(operand))), n);
                     case MINUS_PERCENT -> roundForDisplay(n.multiply(BigDecimal.ONE.subtract(CaptureValueAction.percentFactor(operand))), n);
                     case NONE -> n;
                  };
                  e.setValue(formatCapturedNumber(r));
                  if (target || !targetTracked) {
                     ok = true;
                  }
               }
            }

            return ok;
         }
      } else {
         return true;
      }
   }

   private static BigDecimal divideForDisplay(BigDecimal number, BigDecimal operand) {
      return roundForDisplay(number.divide(operand, new MathContext(Math.max(16, number.precision() + 8), RoundingMode.HALF_UP)), number);
   }

   private static BigDecimal roundForDisplay(BigDecimal result, BigDecimal source) {
      int scale = Math.max(2, source.scale() + 2);
      int adjustedExponent = result.precision() - result.scale() - 1;
      if (result.signum() != 0 && adjustedExponent < 0) {
         scale = Math.max(scale, 1 - adjustedExponent);
      }

      return result.setScale(scale, RoundingMode.HALF_UP);
   }

   private static BigDecimal parseCapturedNumber(String raw, boolean embedded, MacroTemplate.NumberStyle style) {
      if (raw == null) {
         return null;
      } else {
         try {
            return new BigDecimal(embedded ? MacroTemplate.parseCaptureNumber(raw, style) : MacroTemplate.parseCaptureNumberStrict(raw, style));
         } catch (RuntimeException var4) {
            return null;
         }
      }
   }

   private static String formatCapturedNumber(BigDecimal n) {
      BigDecimal stripped = n.stripTrailingZeros();
      return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
   }

   private XCarryAction resolveXCarry(XCarryAction source) {
      XCarryAction copy = new XCarryAction();
      copy.mode = source.mode;
      copy.transferMode = source.transferMode;
      copy.safeClickDelayTicks = source.safeClickDelayTicks;
      copy.safeClickDelayAfterPickup = source.safeClickDelayAfterPickup;
      copy.safeClickDelayBeforeReturn = source.safeClickDelayBeforeReturn;
      copy.carryCursor = source.carryCursor;
      copy.useCrafting = source.useCrafting;
      copy.useArmor = source.useArmor;
      copy.useOffhand = source.useOffhand;
      List<ItemTarget> configured = source.entryTargets.isEmpty() ? source.entries.stream().map(ItemTarget::fromLegacyEntry).toList() : source.entryTargets;
      int count = Math.min(11, configured.size());

      for (int i = 0; i < count; i++) {
         ItemTarget target = configured.get(i);
         if (target != null) {
            ItemTarget resolved = target.copy();
            String template = target.template != null && !target.template.isBlank() ? target.template : target.editorText();
            if (template != null && template.indexOf(123) >= 0) {
               ItemTarget dynamic = ItemTarget.fromLegacyEntry(this.resolve(template));
               if (target.hasSlot()) {
                  dynamic.slot = target.slot;
               }

               resolved = dynamic;
            }

            if (resolved.hasSlot() || resolved.hasIdentity()) {
               copy.entryTargets.add(resolved);
               copy.entryDestinations.add(source.destinationFor(i));
               copy.entryAmountModes.add(source.amountModeFor(i));
               copy.entryAmounts.add(source.amountFor(i));
            }
         }
      }

      return copy;
   }

   private MacroTemplate.Resolution resolveTemplate(String template) {
      return template != null && !template.isEmpty() && template.indexOf(123) >= 0
         ? MacroTemplate.resolve(template, this.buildContext(), null)
         : MacroTemplate.Resolution.ok(template == null ? "" : template);
   }

   private String resolve(String template) {
      return this.resolveTemplate(template).value();
   }

   private MacroVariableContext buildContext() {
      MacroVariableContext ctx = new MacroVariableContext();
      MultiMacroHost host = this.activeHost;
      if (host != null) {
         seedBuiltIns(ctx, host);
      }

      for (Entry<String, String> e : this.vars.entrySet()) {
         MacroValue structured = this.structuredVars.get(e.getKey());
         ctx.set(e.getKey(), structured != null ? structured : MacroValue.text(e.getValue()));
      }

      return ctx;
   }

   private static void seedBuiltIns(MacroVariableContext ctx, MultiMacroHost host) {
      String username = host.botUsername();
      if (!username.isBlank()) {
         MacroValue u = MacroValue.text(username);
         ctx.set("username", u);
         ctx.set("user", u);
         ctx.set("player", u);
      }

      String uuid = host.botUuid();
      if (!uuid.isBlank()) {
         ctx.set("uuid", MacroValue.text(uuid));
      }

      String server = host.serverAddress();
      if (!server.isBlank()) {
         ctx.set("server", MacroValue.text(server));
      }

      String password = host.macroPassword();
      if (!password.isBlank()) {
         ctx.set("password", MacroValue.text(password));
      }

      String dim = host.dimension();
      if (dim != null && !dim.isBlank()) {
         MacroValue d = MacroValue.text(dim);
         ctx.set("dimension", d);
         ctx.set("dim", d);
      }

      MacroValue slot = MacroValue.slot(Math.max(0, Math.min(8, host.selectedHotbar())));
      ctx.set("selected_slot", slot);
      ctx.set("target_slot", slot);
      if (host.hasPosition()) {
         double x = host.posX();
         double y = host.posY();
         double z = host.posZ();
         ctx.set("x", MacroValue.text(String.format(Locale.ROOT, "%.3f", x)));
         ctx.set("y", MacroValue.text(String.format(Locale.ROOT, "%.3f", y)));
         ctx.set("z", MacroValue.text(String.format(Locale.ROOT, "%.3f", z)));
         int bx = (int)Math.floor(x);
         int by = (int)Math.floor(y);
         int bz = (int)Math.floor(z);
         ctx.set("bx", MacroValue.text(Integer.toString(bx)));
         ctx.set("by", MacroValue.text(Integer.toString(by)));
         ctx.set("bz", MacroValue.text(Integer.toString(bz)));
         ctx.set("pos", MacroValue.text(bx + " " + by + " " + bz));
         float yaw = host.currentYaw();
         float pitch = host.currentPitch();
         ctx.set("yaw", MacroValue.text(String.format(Locale.ROOT, "%.2f", yaw)));
         ctx.set("pitch", MacroValue.text(String.format(Locale.ROOT, "%.2f", pitch)));
         ctx.set("rot", MacroValue.text(String.format(Locale.ROOT, "%.2f %.2f", yaw, pitch)));
         ctx.set("facing", MacroValue.text(Direction.fromYRot(yaw).getName()));
      }
   }

   private static String modeWord(RiptidePacketClick.Mode mode) {
      if (mode == RiptidePacketClick.Mode.RIGHT_CLICK) {
         return "right";
      } else {
         return mode == RiptidePacketClick.Mode.QUICK_MOVE ? "shift" : "left";
      }
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }

   private static final class AutofillUnion {
      final LinkedHashSet<String> names = new LinkedHashSet<>();
      final ArrayDeque<String> sweepQueue = new ArrayDeque<>();
      int alphabetCursor;
      int cap;
      boolean baseFetched;
      int inflightId = -1;
      String inflightQuery = "";

      AutofillUnion(String baseQuery) {
         this.sweepQueue.add(baseQuery);
      }
   }

   private record ContainerPlan(List<int[]> clicks, long graceMs, long perClickDelayMs, boolean closeAfter, boolean closeSilent) {
   }
}
