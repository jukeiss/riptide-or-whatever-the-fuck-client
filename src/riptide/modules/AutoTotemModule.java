package riptide.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RangeSetting;
import riptide.api.module.ValueRange;
import riptide.gui.RiptideGhostInventoryScreen;
import riptide.mixin.accessor.RiptideEntityAccessor;
import riptide.mixin.accessor.RiptideLivingEntityAccessor;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.mixin.accessor.RiptidePlayerAccessor;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryClickHelper;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideSharedState;

public final class AutoTotemModule extends Module {
   private static final int OFFHAND_INV = 40;
   private static final int MODE_NONE = 0;
   private static final int MODE_TOTEM = 1;
   private static final int MODE_BACK = 2;
   private static final Direction[] HOLE_DIRECTIONS = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final float BASE_HORIZONTAL_AIR_DRAG = 0.91F;
   private static final float BASE_VERTICAL_AIR_DRAG = 0.98F;
   private static final float ELYTRA_HORIZONTAL_AIR_DRAG = 0.99F;
   private static final float ELYTRA_VERTICAL_AIR_DRAG = 0.98F;
   private static final List<BlockPos> SPHERE_OFFSETS = buildSphere(10.0F);
   private static final long DEAD_ROUND_TRIP_MS = 2000L;
   private static final long SMART_SWAP_SYNC_MS = 500L;
   private long lastSwitchAt;
   private long switchBackSince = -1L;
   private int lastMode = 0;
   private ItemStack displacedItem = ItemStack.EMPTY;
   private int displacedSlot = -1;
   private long smartSwapSentAt = -1L;
   private Item smartSwapOffhandItem = Items.AIR;
   private static final int SEQ_NONE = 0;
   private static final int SEQ_PICKUP = 1;
   private static final int SEQ_SMART = 2;
   private static final int SEQ_CLOSE = 3;
   private int seqKind = 0;
   private int seqFromSlot = -1;
   private int seqSelectedSlot = -1;
   private int seqStep;
   private int seqStepCount;
   private int seqNextTick;
   private int nextOperationTick;
   private int reactionHoldUntilTick = -1;
   private RiptideGhostInventoryScreen ghost;
   private boolean ghostSession;
   private int ghostSettleUntilTick = -1;
   private static volatile int operationActiveUntilTick = Integer.MIN_VALUE;
   private final Map<String, String> bandRaws = new HashMap<>();
   private final Map<String, ValueRange> bandParsed = new HashMap<>();
   private static volatile int pauseMovementUntilTick = Integer.MIN_VALUE;
   private final Map<Long, Float> entityDamageMemo = new HashMap<>();
   private int entityDamageMemoTick = -2;

   AutoTotemModule() {
      super("auto-totem", "AutoTotem", ModuleCategory.PLAYER, "Keeps a totem in offhand.");
      this.add(new ChoiceSetting("switch-mode", "Switch Mode", "Switch", "Smart", "Switch", "PickUp").description("How the totem is swapped.").build());
      this.add(new IntSetting("switch-delay", "Switch Delay (ms)", 0, 0, 500, 5).description("Delay between offhand swaps.").build());
      this.add(new IntSetting("switch-back-delay", "Switch Back Delay (ms)", 40, 0, 500, 5).description("Delay before switching back.").build());
      this.add(
         new RangeSetting("click-delay", "Click Delay", new ValueRange(1, 2), 0.0, 10.0, 1.0)
            .group("Timing")
            .unit("ticks")
            .description("Ticks between packets")
      );
      this.add(
         new RangeSetting("close-delay", "Close Delay", new ValueRange(1, 2), 0.0, 10.0, 1.0)
            .group("Timing")
            .unit("ticks")
            .description("Ticks before session close")
      );
      this.add(
         new RangeSetting("operation-delay", "Spacing", new ValueRange(0, 2), 0.0, 10.0, 1.0)
            .group("Timing")
            .unit("ticks")
            .description("Ticks between operations")
      );
      this.add(
         new RangeSetting("reaction", "Reaction", new ValueRange(0, 2), 0.0, 10.0, 1.0).group("Timing").unit("ticks").description("Reaction ticks after pop")
      );
      this.add(new RangeSetting("settle", "Settle", new ValueRange(1, 1), 0.0, 5.0, 1.0).group("Timing").unit("ticks").description("Ticks before first packet"));
      this.add(new BoolSetting("send-directly", "Send Directly", false).description("Swap even with screens open.").build());
      this.add(new BoolSetting("health", "Health", true).group("Health").description("Totem only when endangered.").build());
      this.add(
         new IntSetting("health-threshold", "Health Threshold", 14, 0, 20, 1)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Totem below this health.")
            .build()
      );
      this.add(
         new BoolSetting("safety", "Safety", true)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Stricter threshold inside holes.")
            .build()
      );
      this.add(
         new IntSetting("safe-health-threshold", "Safe Health Threshold", 10, 0, 20, 1)
            .group("Health")
            .visibleWhen(() -> this.bool("health") && this.bool("safety"))
            .description("Threshold while in holes.")
            .build()
      );
      this.add(
         new BoolSetting("subtract-calculated-damage", "Subtract Calculated Damage", false)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Subtract predicted damage too.")
            .build()
      );
      this.add(
         new BoolSetting("predict-explosions", "Predict Explosion Damage", true)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Predict entity explosion damage.")
            .build()
      );
      this.add(
         new BoolSetting("predict-block-explosions", "Predict Block Explosions", false)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Predict bed and anchor explosions.")
            .build()
      );
      this.add(
         new BoolSetting("predict-fall-damage", "Predict Fall Damage", true)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Predict incoming fall damage.")
            .build()
      );
      this.add(
         new BoolSetting("ignore-elytra", "Ignore Elytra", false)
            .group("Health")
            .visibleWhen(() -> this.bool("health") && this.bool("predict-fall-damage"))
            .description("Ignore fall damage while gliding.")
            .build()
      );
      this.add(
         new BoolSetting("missing-armor", "Missing Armor", true)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Totem when armor missing.")
            .build()
      );
      this.add(
         new BoolSetting("switch-back", "Switch Back", true)
            .group("Health")
            .visibleWhen(() -> this.bool("health"))
            .description("Restore item after healing.")
            .build()
      );
      this.add(new BoolSetting("pause-movement", "Pause Movement", true));
      this.add(new BoolSetting("no-render", "No Render", true).description("Hide the totem pop animation.").build());
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   private void reset() {
      this.lastMode = 0;
      this.switchBackSince = -1L;
      this.lastSwitchAt = 0L;
      this.displacedItem = ItemStack.EMPTY;
      this.displacedSlot = -1;
      this.smartSwapSentAt = -1L;
      this.smartSwapOffhandItem = Items.AIR;
      if (this.seqKind != 0) {
         this.abortSequence();
      } else {
         this.clearSequence();
      }

      this.closeGhostSession(false);
      this.entityDamageMemo.clear();
      this.entityDamageMemoTick = -2;
      this.nextOperationTick = 0;
      this.reactionHoldUntilTick = -1;
      operationActiveUntilTick = Integer.MIN_VALUE;
      RiptideHandArbiter.releaseAll(this.id());
   }

   @Override
   public void preMovementTick() {
      if (MC != null && MC.player != null && MC.level != null) {
         if (PackHideState.isHardLocked()) {
            boolean ghostHadClicks = this.seqKind != 0;
            this.clearSequence();
            this.closeGhostSession(ghostHadClicks);
         } else {
            this.reconcileClaims();
            if (this.seqKind != 0) {
               this.tickSequence();
            } else {
               int now = RiptideSharedState.get().getClientTickCounter();
               boolean health = this.bool("health");
               boolean wantTotem = health ? !isBlocked() && this.healthBelowThreshold() : true;
               int mode = wantTotem ? 1 : 0;
               if (mode == 0 && health && this.bool("switch-back") && this.lastMode == 1) {
                  mode = 2;
               }

               if (mode == 0) {
                  this.reactionHoldUntilTick = -1;
               }

               if (mode != this.lastMode && this.lastMode == 1) {
                  if (this.switchBackSince < 0L) {
                     this.switchBackSince = System.currentTimeMillis();
                  }

                  if (System.currentTimeMillis() - this.switchBackSince < this.integer("switch-back-delay")) {
                     this.dropSettleGhost();
                     return;
                  }
               }

               this.switchBackSince = -1L;
               boolean timed = this.timed();
               if (!timed || now >= this.nextOperationTick) {
                  boolean handedOver = mode == 1 && RiptideHandArbiter.handHandedOver(this.id());
                  if (handedOver || System.currentTimeMillis() - this.lastSwitchAt >= this.integer("switch-delay")) {
                     int fromSlot;
                     if (mode == 1) {
                        fromSlot = findTotemSlot();
                        if (fromSlot < 0) {
                           this.dropSettleGhost();
                           return;
                        }
                     } else {
                        if (mode != 2) {
                           this.dropSettleGhost();
                           return;
                        }

                        fromSlot = this.findDisplacedSlot();
                        if (fromSlot < 0) {
                           if (this.displacedSlot < 0) {
                              this.lastMode = 0;
                           }

                           this.dropSettleGhost();
                           return;
                        }
                     }

                     int previousMode = this.lastMode;
                     this.lastMode = mode;
                     if (fromSlot == 40) {
                        this.dropSettleGhost();
                     } else {
                        if (timed) {
                           if (this.reactionHoldUntilTick < 0 && this.ghost == null) {
                              this.reactionHoldUntilTick = now + this.drawTicks("reaction", 0, 2);
                           }

                           if (now < this.reactionHoldUntilTick) {
                              return;
                           }

                           this.reactionHoldUntilTick = -1;
                        }

                        if (timed) {
                           if (this.ghostSettleUntilTick >= 0) {
                              if (this.ghost != null && MC.gui.screen() != this.ghost) {
                                 this.ghost = null;
                              }

                              if (this.ghost != null && now < this.ghostSettleUntilTick) {
                                 armOperationWindow(this.windowTail());
                                 return;
                              }

                              this.ghostSettleUntilTick = -1;
                           } else if (this.ghost == null && MC.gui.screen() == null && this.clickPathSwitch(fromSlot)) {
                              this.ghost = new RiptideGhostInventoryScreen(MC.player);
                              this.ghostSession = true;
                              MC.gui.setScreen(this.ghost);
                              MC.mouseHandler.grabMouse();
                              this.ghostSettleUntilTick = now + this.drawTicks("settle", 1, 1);
                              armOperationWindow(this.windowTail());
                              return;
                           }
                        }

                        ItemStack offhandBefore = MC.player.getOffhandItem().copy();
                        boolean switched = timed ? this.startPacedSwitch(fromSlot, mode == 1, offhandBefore) : this.performSwitch(fromSlot, mode == 1);
                        if (!switched) {
                           this.lastMode = previousMode;
                           this.closeGhostSession(false);
                        } else {
                           if (mode == 1 && health && this.bool("switch-back")) {
                              this.displacedItem = offhandBefore;
                              this.displacedSlot = fromSlot;
                           } else {
                              this.displacedItem = ItemStack.EMPTY;
                              this.displacedSlot = -1;
                           }

                           this.lastSwitchAt = System.currentTimeMillis();
                           this.reconcileClaims();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void reconcileClaims() {
      boolean tripLive = this.roundTripLive();
      if (!tripLive && this.displacedSlot >= 0) {
         this.displacedItem = ItemStack.EMPTY;
         this.displacedSlot = -1;
      }

      if (tripLive) {
         RiptideHandArbiter.reserveSlot(this.id(), this.displacedSlot);
      } else if (this.seqKind != 0) {
         RiptideHandArbiter.reserveSlot(this.id(), this.seqFromSlot);
      } else {
         RiptideHandArbiter.releaseSlots(this.id());
      }

      if (!tripLive && this.seqKind == 0 && !this.smartSwapPending() && !isTotem(MC.player.getOffhandItem())) {
         RiptideHandArbiter.releaseOffhand(this.id());
      } else {
         RiptideHandArbiter.claimOffhand(this.id());
      }
   }

   private boolean roundTripLive() {
      if (this.displacedSlot < 0) {
         return false;
      } else if (!this.bool("health") || !this.bool("switch-back")) {
         return false;
      } else if (this.seqKind != 0) {
         return true;
      } else {
         return this.findDisplacedSlot() >= 0 ? true : System.currentTimeMillis() - this.lastSwitchAt < 2000L;
      }
   }

   private boolean smartSwapPending() {
      if (this.smartSwapSentAt < 0L) {
         return false;
      } else if (MC.player.getOffhandItem().getItem() == this.smartSwapOffhandItem && System.currentTimeMillis() - this.smartSwapSentAt < 500L) {
         return true;
      } else {
         this.smartSwapSentAt = -1L;
         return false;
      }
   }

   private static boolean isBlocked() {
      return MC.player.isCreative() || MC.player.isSpectator() || MC.player.isDeadOrDying();
   }

   private static int findTotemSlot() {
      LocalPlayer player = MC.player;
      if (isTotem(player.getOffhandItem())) {
         return 40;
      } else {
         for (int i = 0; i < 9; i++) {
            if (isTotem(player.getInventory().getItem(i))) {
               return i;
            }
         }

         for (int ix = 9; ix < 36; ix++) {
            if (isTotem(player.getInventory().getItem(ix))) {
               return ix;
            }
         }

         return -1;
      }
   }

   private static boolean isTotem(ItemStack stack) {
      return !stack.isEmpty() && stack.has(DataComponents.DEATH_PROTECTION);
   }

   private int findDisplacedSlot() {
      if (this.displacedSlot >= 0 && this.displacedSlot != 40) {
         ItemStack stack = MC.player.getInventory().getItem(this.displacedSlot);
         if (this.displacedItem.isEmpty()) {
            return stack.isEmpty() ? this.displacedSlot : -1;
         } else {
            return !stack.isEmpty() && stack.getItem() == this.displacedItem.getItem() ? this.displacedSlot : -1;
         }
      } else {
         return -1;
      }
   }

   private boolean performSwitch(int fromSlot, boolean isTotem) {
      if (!this.isEnabled()) {
         return false;
      } else if (!MC.player.containerMenu.getCarried().isEmpty()) {
         return false;
      } else {
         boolean foreign = MC.player.containerMenu != MC.player.inventoryMenu;
         if (!foreign || this.bool("send-directly") && isTotem) {
            if (this.smartSwapPending()) {
               return false;
            } else {
               String switchMode = this.choice("switch-mode");
               boolean smart = foreign || "Smart".equals(switchMode) && fromSlot >= 0 && fromSlot < 9;
               if (isTotem && !RiptideHandArbiter.claimOffhand(this.id())) {
                  return false;
               } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                  return false;
               } else {
                  boolean switched;
                  try {
                     if (smart) {
                        switched = this.smartHotbarSwitch(fromSlot);
                     } else {
                        switched = switch (switchMode) {
                           case "Smart", "PickUp" -> pickupSwitch(fromSlot);
                           default -> swapSwitch(fromSlot);
                        };
                     }
                  } finally {
                     RiptideHandArbiter.endHandPacketGroup(this.id());
                  }

                  if (switched && this.bool("pause-movement")) {
                     pauseMovementUntilTick = RiptideSharedState.get().getClientTickCounter() + 1;
                  }

                  return switched;
               }
            }
         } else {
            return false;
         }
      }
   }

   private boolean startPacedSwitch(int fromSlot, boolean isTotem, ItemStack offhandBefore) {
      if (!this.isEnabled()) {
         return false;
      } else if (!MC.player.containerMenu.getCarried().isEmpty()) {
         return false;
      } else {
         boolean foreign = MC.player.containerMenu != MC.player.inventoryMenu;
         if (!foreign || this.bool("send-directly") && isTotem) {
            if (this.smartSwapPending()) {
               return false;
            } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
               return false;
            } else {
               String switchMode = this.choice("switch-mode");
               boolean smart = foreign || "Smart".equals(switchMode) && fromSlot >= 0 && fromSlot < 9;
               boolean pickup = !smart && ("Smart".equals(switchMode) || "PickUp".equals(switchMode));
               if (smart) {
                  return this.startPacedSmartSwitch(fromSlot, isTotem);
               } else if (!pickup) {
                  boolean switched = this.performSwitch(fromSlot, isTotem);
                  if (switched) {
                     int now = RiptideSharedState.get().getClientTickCounter();
                     this.seqKind = 3;
                     this.seqFromSlot = fromSlot;
                     this.seqStep = 0;
                     this.seqStepCount = 1;
                     this.seqNextTick = now + this.drawTicks("close-delay", 1, 2);
                     armOperationWindow(this.windowTail());
                     this.stampPauseMovement();
                  }

                  return switched;
               } else {
                  int from = RiptideInventoryHelper.toHandlerSlot(MC, fromSlot);
                  int offhand = RiptideInventoryHelper.toHandlerSlot(MC, 40);
                  if (from < 0 || offhand < 0) {
                     return false;
                  } else if (isTotem && !RiptideHandArbiter.claimOffhand(this.id())) {
                     return false;
                  } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     return false;
                  } else {
                     boolean clicked;
                     try {
                        clicked = RiptideInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }

                     if (!clicked) {
                        return false;
                     } else {
                        int now = RiptideSharedState.get().getClientTickCounter();
                        this.seqKind = 1;
                        this.seqFromSlot = fromSlot;
                        this.seqStep = 1;
                        this.seqStepCount = (offhandBefore.isEmpty() ? 2 : 3) + 1;
                        this.seqNextTick = now + this.drawTicks("click-delay", 1, 2);
                        armOperationWindow(this.windowTail());
                        this.stampPauseMovement();
                        return true;
                     }
                  }
               }
            }
         } else {
            return false;
         }
      }
   }

   private boolean startPacedSmartSwitch(int fromSlot, boolean isTotem) {
      if (fromSlot >= 0 && fromSlot <= 8) {
         ClientPacketListener connection = MC.getConnection();
         if (connection == null || MC.gameMode == null) {
            return false;
         } else if (isTotem && !RiptideHandArbiter.claimOffhand(this.id())) {
            return false;
         } else {
            ((RiptideMultiPlayerGameModeAccessor)MC.gameMode).riptide$ensureHasSentCarriedItem();
            int now = RiptideSharedState.get().getClientTickCounter();
            int selected = MC.player.getInventory().getSelectedSlot();
            if (selected == fromSlot) {
               if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                  return false;
               } else {
                  try {
                     connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                  } finally {
                     RiptideHandArbiter.endHandPacketGroup(this.id());
                  }

                  this.smartSwapOffhandItem = MC.player.getOffhandItem().getItem();
                  this.smartSwapSentAt = System.currentTimeMillis();
                  armOperationWindow(this.windowTail());
                  this.nextOperationTick = now + this.drawTicks("operation-delay", 0, 2);
                  this.stampPauseMovement();
                  return true;
               }
            } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
               return false;
            } else {
               try {
                  connection.send(new ServerboundSetCarriedItemPacket(fromSlot));
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }

               this.seqKind = 2;
               this.seqFromSlot = fromSlot;
               this.seqSelectedSlot = selected;
               this.seqStep = 1;
               this.seqStepCount = 3;
               this.seqNextTick = now + this.drawTicks("click-delay", 1, 2);
               armOperationWindow(this.windowTail());
               this.stampPauseMovement();
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void tickSequence() {
      int now = RiptideSharedState.get().getClientTickCounter();
      operationActiveUntilTick = now + this.windowTail();
      if (this.ghost != null && MC.gui.screen() != this.ghost) {
         this.ghost = null;
      }

      if (now >= this.seqNextTick) {
         if (!this.isEnabled() || this.sequenceEnvironmentBroken()) {
            this.abortSequence();
         } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            this.seqNextTick = now + 1;
         } else {
            boolean stepped;
            try {
               stepped = switch (this.seqKind) {
                  case 2 -> this.stepSmartSequence();
                  case 3 -> this.stepCloseSequence();
                  default -> this.stepPickupSequence();
               };
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }

            if (!stepped) {
               this.abortSequence();
            } else {
               this.seqStep++;
               this.seqNextTick = now + this.gapBeforeNextStep();
               this.stampPauseMovement();
               if (this.seqStep >= this.seqStepCount) {
                  this.nextOperationTick = now + this.drawTicks("operation-delay", 0, 2);
                  this.clearSequence();
               }
            }
         }
      }
   }

   private boolean sequenceEnvironmentBroken() {
      if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
         return true;
      } else if (this.seqKind == 2) {
         return MC.getConnection() == null || MC.gameMode == null;
      } else if (this.seqKind == 3) {
         return false;
      } else {
         return MC.player.containerMenu != MC.player.inventoryMenu
            ? true
            : this.seqStep < this.seqStepCount - 1 && MC.player.containerMenu.getCarried().isEmpty();
      }
   }

   private boolean stepPickupSequence() {
      if (this.seqStep == this.seqStepCount - 1) {
         this.sendSessionClose();
         return true;
      } else {
         int handlerSlot = RiptideInventoryHelper.toHandlerSlot(MC, this.seqStep == 1 ? 40 : this.seqFromSlot);
         return handlerSlot < 0 ? false : RiptideInventoryClickHelper.click(MC, handlerSlot, 0, ContainerInput.PICKUP);
      }
   }

   private boolean stepCloseSequence() {
      this.sendSessionClose();
      return true;
   }

   private boolean stepSmartSequence() {
      ClientPacketListener connection = MC.getConnection();
      if (connection == null) {
         return false;
      } else {
         if (this.seqStep == 1) {
            connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            this.smartSwapOffhandItem = MC.player.getOffhandItem().getItem();
            this.smartSwapSentAt = System.currentTimeMillis();
         } else {
            connection.send(new ServerboundSetCarriedItemPacket(this.seqSelectedSlot));
         }

         return true;
      }
   }

   private void abortSequence() {
      if (this.seqKind == 2) {
         ClientPacketListener connection = MC.getConnection();
         if (connection != null && this.seqSelectedSlot != this.seqFromSlot && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            try {
               connection.send(new ServerboundSetCarriedItemPacket(this.seqSelectedSlot));
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }
         }
      } else if (MC.player != null && MC.player.containerMenu == MC.player.inventoryMenu && !MC.player.containerMenu.getCarried().isEmpty()) {
         int from = RiptideInventoryHelper.toHandlerSlot(MC, this.seqFromSlot);
         if (from >= 0 && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            try {
               RiptideInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }
         }
      }

      this.nextOperationTick = RiptideSharedState.get().getClientTickCounter() + this.drawTicks("operation-delay", 0, 2);
      this.closeGhostSession(true);
      this.clearSequence();
   }

   private void clearSequence() {
      this.seqKind = 0;
      this.seqFromSlot = -1;
      this.seqSelectedSlot = -1;
      this.seqStep = 0;
      this.seqStepCount = 0;
      this.seqNextTick = 0;
   }

   private boolean clickPathSwitch(int fromSlot) {
      boolean foreign = MC.player.containerMenu != MC.player.inventoryMenu;
      String switchMode = this.choice("switch-mode");
      return !foreign && (!"Smart".equals(switchMode) || fromSlot < 0 || fromSlot >= 9);
   }

   private void dropSettleGhost() {
      this.closeGhostSession(false);
   }

   private void closeGhostSession(boolean humanShape) {
      this.ghostSession = false;
      this.ghostSettleUntilTick = -1;
      RiptideGhostInventoryScreen screen = this.ghost;
      this.ghost = null;
      if (screen != null && MC.gui.screen() == screen) {
         if (humanShape && MC.getConnection() != null && MC.player != null) {
            screen.onClose();
         }

         if (MC.gui.screen() == screen && MC.player != null) {
            MC.gui.setScreen(null);
            MC.mouseHandler.grabMouse();
         }
      }
   }

   private static void armOperationWindow(int ticks) {
      operationActiveUntilTick = RiptideSharedState.get().getClientTickCounter() + ticks;
   }

   private ValueRange band(String settingId, int fallbackMin, int fallbackMax) {
      String raw = this.value(settingId);
      String previous = this.bandRaws.put(settingId, raw);
      if (previous == null || !previous.equals(raw)) {
         this.bandParsed.put(settingId, ValueRange.parse(raw, new ValueRange(fallbackMin, fallbackMax)).clamp(0.0, 10.0));
      }

      return this.bandParsed.get(settingId);
   }

   private int drawTicks(String settingId, int fallbackMin, int fallbackMax) {
      return (int)Math.round(this.band(settingId, fallbackMin, fallbackMax).random(ThreadLocalRandom.current()));
   }

   private int gapBeforeNextStep() {
      boolean nextIsClose = this.seqKind == 3 || this.seqKind == 1 && this.seqStep == this.seqStepCount - 1;
      return nextIsClose ? this.drawTicks("close-delay", 1, 2) : this.drawTicks("click-delay", 1, 2);
   }

   private int windowTail() {
      return Math.max(1, this.drawTicks("click-delay", 1, 2));
   }

   private double bandMax(String settingId, int fallbackMax) {
      return this.band(settingId, 0, fallbackMax).max();
   }

   private boolean timed() {
      return this.bandMax("click-delay", 5) > 0.0
         || this.bandMax("close-delay", 5) > 0.0
         || this.bandMax("operation-delay", 5) > 0.0
         || this.bandMax("reaction", 7) > 0.0;
   }

   private void sendSessionClose() {
      if (this.ghostSession) {
         this.closeGhostSession(true);
      } else if (MC.getConnection() != null && MC.gui.screen() == null) {
         if (MC.player.containerMenu == MC.player.inventoryMenu) {
            MC.getConnection().send(new ServerboundContainerClosePacket(MC.player.containerMenu.containerId));
         }
      }
   }

   public static boolean operationActive() {
      int until = operationActiveUntilTick;
      if (until != Integer.MIN_VALUE && MC != null && MC.player != null) {
         Module module = ModuleRegistry.get("auto-totem");
         return module != null && module.isEnabled() ? RiptideSharedState.get().getClientTickCounter() <= until : false;
      } else {
         return false;
      }
   }

   public static boolean hidesTotemAnimation() {
      return ModuleRegistry.get("auto-totem") instanceof AutoTotemModule totem && totem.isEnabled() && totem.bool("no-render");
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return operationActive();
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return operationActive();
   }

   private void stampPauseMovement() {
      if (this.bool("pause-movement")) {
         pauseMovementUntilTick = RiptideSharedState.get().getClientTickCounter() + 1;
      }
   }

   public static boolean movementInputPaused() {
      if (operationActive()) {
         return true;
      } else {
         int until = pauseMovementUntilTick;
         if (until == Integer.MIN_VALUE) {
            return false;
         } else {
            int age = RiptideSharedState.get().getClientTickCounter() - until;
            return age <= 0 && age > -2;
         }
      }
   }

   public static Input modifyMovementInput(ClientInput source, Input original) {
      if (original == null || MC == null || MC.player == null || MC.player.input != source) {
         return original;
      } else if (operationActive()) {
         return new Input(false, false, false, false, false, false, false);
      } else {
         return RiptideSharedState.get().getClientTickCounter() > pauseMovementUntilTick
            ? original
            : new Input(false, false, false, false, original.jump(), original.shift(), false);
      }
   }

   private static boolean swapSwitch(int fromSlot) {
      int handlerSlot = RiptideInventoryHelper.toHandlerSlot(MC, fromSlot);
      return handlerSlot < 0 ? false : RiptideInventoryClickHelper.click(MC, handlerSlot, 40, ContainerInput.SWAP);
   }

   private static boolean pickupSwitch(int fromSlot) {
      int from = RiptideInventoryHelper.toHandlerSlot(MC, fromSlot);
      int offhand = RiptideInventoryHelper.toHandlerSlot(MC, 40);
      if (from >= 0 && offhand >= 0) {
         boolean offhandHadItem = !MC.player.getOffhandItem().isEmpty();
         boolean ok = RiptideInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
         ok &= RiptideInventoryClickHelper.click(MC, offhand, 0, ContainerInput.PICKUP);
         if (offhandHadItem) {
            ok &= RiptideInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
         }

         return ok;
      } else {
         return false;
      }
   }

   private boolean smartHotbarSwitch(int fromSlot) {
      if (fromSlot >= 0 && fromSlot <= 8) {
         LocalPlayer player = MC.player;
         ClientPacketListener connection = MC.getConnection();
         if (connection != null && MC.gameMode != null) {
            ((RiptideMultiPlayerGameModeAccessor)MC.gameMode).riptide$ensureHasSentCarriedItem();
            int selected = player.getInventory().getSelectedSlot();
            if (selected != fromSlot) {
               connection.send(new ServerboundSetCarriedItemPacket(fromSlot));
            }

            connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            if (selected != fromSlot) {
               connection.send(new ServerboundSetCarriedItemPacket(selected));
            }

            this.smartSwapOffhandItem = player.getOffhandItem().getItem();
            this.smartSwapSentAt = System.currentTimeMillis();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean healthBelowThreshold() {
      if (!this.bool("health")) {
         return true;
      } else {
         if (this.bool("missing-armor")) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
               if (MC.player.getItemBySlot(slot).isEmpty()) {
                  return true;
               }
            }
         }

         float health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
         boolean safetyOperating = this.bool("safety") && (isBurrowed() || isInHole());
         float allowedDamage = health - (safetyOperating ? this.integer("safe-health-threshold") : this.integer("health-threshold"));
         if (allowedDamage <= 0.0F) {
            return true;
         } else {
            if (!this.bool("subtract-calculated-damage")) {
               allowedDamage = health;
            }

            float calculatedDamage = this.getDamageFromEntities(allowedDamage);
            if (calculatedDamage >= allowedDamage) {
               return true;
            } else {
               calculatedDamage = Math.max(calculatedDamage, this.getDamageFromBlocks(allowedDamage));
               if (calculatedDamage >= allowedDamage) {
                  return true;
               } else {
                  calculatedDamage += this.getFallDamage();
                  return calculatedDamage >= allowedDamage;
               }
            }
         }
      }
   }

   private float getDamageFromEntities(float allowedDamage) {
      if (!this.bool("predict-explosions")) {
         return 0.0F;
      } else {
         float maxDamage = 0.0F;

         for (Entity entity : MC.level.entitiesForRendering()) {
            maxDamage = Math.max(maxDamage, this.getExplosionDamageFromEntityMemoized(entity));
            if (maxDamage >= allowedDamage) {
               return maxDamage;
            }
         }

         return maxDamage;
      }
   }

   private float getExplosionDamageFromEntityMemoized(Entity entity) {
      int now = RiptideSharedState.get().getClientTickCounter();
      if (now - this.entityDamageMemoTick >= 2) {
         this.entityDamageMemoTick = now;
         this.entityDamageMemo.clear();
      }

      Vec3 pos = entity.position();
      long key = (long)entity.getId() << 48 | ((long)(pos.x * 4.0) & 65535L) << 32 | ((long)(pos.y * 4.0) & 65535L) << 16 | (long)(pos.z * 4.0) & 65535L;
      return this.entityDamageMemo.computeIfAbsent(key, k -> getExplosionDamageFromEntity(entity));
   }

   private static float getExplosionDamageFromEntity(Entity entity) {
      if (entity instanceof EndCrystal) {
         return getDamageFromExplosion(entity.position(), 6.0F, 12.0F, 144.0F, null, Explosion.getDefaultDamageSource(MC.level, entity));
      } else if (entity instanceof PrimedTnt) {
         return getDamageFromExplosion(entity.position().add(0.0, 0.0625, 0.0), 4.0F, 8.0F, 64.0F, null, Explosion.getDefaultDamageSource(MC.level, entity));
      } else if (entity instanceof MinecartTNT cart) {
         float speed = (float)Math.min(Math.sqrt(cart.getDeltaMovement().horizontalDistanceSqr()), 5.0);
         float power = 4.0F + speed * 1.5F;
         float range = power * 2.0F;
         return getDamageFromExplosion(cart.position(), power, range, range * range, null, Explosion.getDefaultDamageSource(MC.level, entity));
      } else if (entity instanceof Creeper creeper) {
         float power = 3.0F * (creeper.isPowered() ? 2.0F : 1.0F);
         float range = power * 2.0F;
         return getDamageFromExplosion(creeper.position(), power, range, range * range, null, Explosion.getDefaultDamageSource(MC.level, entity));
      } else {
         return 0.0F;
      }
   }

   private float getDamageFromBlocks(float allowedDamage) {
      if (!this.bool("predict-block-explosions")) {
         return 0.0F;
      } else {
         boolean bedsExplode = MC.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.BED_RULE) == BedRule.EXPLODES;
         boolean anchorsExplode = !(Boolean)MC.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS);
         if (!bedsExplode && !anchorsExplode) {
            return 0.0F;
         } else {
            BlockPos center = MC.player.blockPosition();
            float maxDamage = 0.0F;

            for (BlockPos offset : SPHERE_OFFSETS) {
               BlockPos pos = center.offset(offset);
               BlockState state = MC.level.getBlockState(pos);
               Block block = state.getBlock();
               boolean bedExplosion = bedsExplode && block instanceof BedBlock;
               boolean anchorExplosion = anchorsExplode && block instanceof RespawnAnchorBlock && (Integer)state.getValue(RespawnAnchorBlock.CHARGE) > 0;
               if (bedExplosion || anchorExplosion) {
                  List<BlockPos> exclude = bedExplosion
                     ? List.of(pos, pos.relative(((Direction)state.getValue(HorizontalDirectionalBlock.FACING)).getOpposite()))
                     : List.of(pos);
                  Vec3 posCenter = Vec3.atCenterOf(pos);
                  maxDamage = Math.max(
                     maxDamage, getDamageFromExplosion(posCenter, 5.0F, 10.0F, 100.0F, exclude, MC.player.damageSources().badRespawnPointExplosion(posCenter))
                  );
                  if (maxDamage >= allowedDamage) {
                     return maxDamage;
                  }
               }
            }

            return maxDamage;
         }
      }
   }

   private static float getDamageFromExplosion(Vec3 pos, float power, float range, float damageDistance, List<BlockPos> exclude, DamageSource source) {
      LocalPlayer player = MC.player;
      if (player.distanceToSqr(pos) > damageDistance) {
         return 0.0F;
      } else {
         float exposure = exclude == null ? ServerExplosion.getSeenPercent(pos, player) : getExposureToExplosion(player, pos, exclude);
         double distanceDecay = 1.0 - Math.sqrt(player.distanceToSqr(pos)) / range;
         double pre1 = exposure * distanceDecay;
         double preprocessedDamage = (pre1 * pre1 + pre1) / 2.0 * 7.0 * range + 1.0;
         return preprocessedDamage == 0.0 ? 0.0F : getEffectiveDamage(source, (float)preprocessedDamage);
      }
   }

   private static float getExposureToExplosion(LocalPlayer player, Vec3 source, List<BlockPos> exclude) {
      AABB box = player.getBoundingBox();
      double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
      if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
         int hits = 0;
         int totalRays = 0;

         for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
               for (double z = 0.0; z <= 1.0; z += stepZ) {
                  Vec3 sample = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offsetX, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                  BlockHitResult hit = MC.level.clip(new ClipContext(sample, source, net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, player));
                  if (hit.getType() == Type.MISS || exclude.contains(hit.getBlockPos())) {
                     hits++;
                  }

                  totalRays++;
               }
            }
         }

         return (float)hits / totalRays;
      } else {
         return 0.0F;
      }
   }

   private float getFallDamage() {
      LocalPlayer player = MC.player;
      if (this.bool("predict-fall-damage") && !(player.fallDistance <= 3.0)) {
         Module noFall = ModuleRegistry.get("no-fall");
         if (noFall != null && noFall.isEnabled()) {
            return 0.0F;
         } else if (this.bool("ignore-elytra") && player.isFallFlying() && player.hasPose(Pose.FALL_FLYING)) {
            return 0.0F;
         } else {
            BlockPos collision = AutoTotemModule.FallingPlayerSim.findCollision(player, 20);
            float multiplier = fallDamageMultiplier(collision, player);
            if (multiplier <= 0.0F) {
               return 0.0F;
            } else {
               float amount = ((RiptideLivingEntityAccessor)player).riptide$calculateFallDamage(player.fallDistance, multiplier);
               return getEffectiveDamage(player.damageSources().fall(), amount);
            }
         }
      } else {
         return 0.0F;
      }
   }

   private static float fallDamageMultiplier(BlockPos pos, LocalPlayer player) {
      if (pos == null) {
         return 1.0F;
      } else {
         Block block = MC.level.getBlockState(pos).getBlock();
         if (block == Blocks.WATER || block == Blocks.COBWEB || block == Blocks.POWDER_SNOW) {
            return 0.0F;
         } else if (block == Blocks.HAY_BLOCK || block == Blocks.HONEY_BLOCK) {
            return 0.2F;
         } else if (block == Blocks.SLIME_BLOCK) {
            return player.isSuppressingBounce() ? 1.0F : 0.0F;
         } else {
            return block instanceof BedBlock ? 0.5F : 1.0F;
         }
      }
   }

   private static float getEffectiveDamage(DamageSource source, float damage) {
      LocalPlayer player = MC.player;
      if (!((RiptideEntityAccessor)player).riptide$isInvulnerableToBase(source) && !player.isDeadOrDying()) {
         float amount = damage;
         if (player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return 0.0F;
         } else {
            if (source.scalesWithDifficulty()) {
               Difficulty difficulty = MC.level.getDifficulty();
               if (difficulty == Difficulty.PEACEFUL) {
                  amount = 0.0F;
               } else if (difficulty == Difficulty.EASY) {
                  amount = Math.min(damage / 2.0F + 1.0F, damage);
               } else if (difficulty == Difficulty.HARD) {
                  amount = damage * 3.0F / 2.0F;
               }
            }

            if (amount == 0.0F) {
               return 0.0F;
            } else if (source.is(DamageTypeTags.IS_FIRE) && player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
               return 0.0F;
            } else {
               amount -= getBlockedDamage(player, source, amount);
               if (amount == 0.0F) {
                  return 0.0F;
               } else {
                  amount = ((RiptideLivingEntityAccessor)player).riptide$getDamageAfterArmorAbsorb(source, amount);
                  return ((RiptideLivingEntityAccessor)player).riptide$getDamageAfterMagicAbsorb(source, amount);
               }
            }
         }
      } else {
         return 0.0F;
      }
   }

   private static float getBlockedDamage(LocalPlayer player, DamageSource source, float amount) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else {
         ItemStack blocking = player.getItemBlockingWith();
         if (blocking == null) {
            return 0.0F;
         } else {
            BlocksAttacks blocksAttacks = (BlocksAttacks)blocking.get(DataComponents.BLOCKS_ATTACKS);
            if (blocksAttacks == null) {
               return 0.0F;
            } else if (blocksAttacks.bypassedBy().isPresent() && ((HolderSet)blocksAttacks.bypassedBy().get()).contains(source.typeHolder())) {
               return 0.0F;
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
               return 0.0F;
            } else {
               float horizontalAngle = (float) Math.PI;
               Vec3 sourcePosition = source.getSourcePosition();
               if (sourcePosition != null) {
                  Vec3 view = viewVectorAtZeroPitch(player);
                  Vec3 direction = sourcePosition.subtract(player.position());
                  direction = new Vec3(direction.x, 0.0, direction.z).normalize();
                  horizontalAngle = (float)Math.acos(direction.dot(view));
               }

               return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
            }
         }
      }
   }

   private static Vec3 viewVectorAtZeroPitch(LocalPlayer player) {
      float yaw = player.getYHeadRot() * (float) (Math.PI / 180.0);
      return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
   }

   private static boolean isInHole() {
      BlockPos feet = feetBlockPos();

      for (Direction direction : HOLE_DIRECTIONS) {
         if (!isBlastResistant(feet.relative(direction))) {
            return false;
         }
      }

      return true;
   }

   private static boolean isBurrowed() {
      return isBlastResistant(feetBlockPos());
   }

   private static boolean isBlastResistant(BlockPos pos) {
      return MC.level.getBlockState(pos).getBlock().getExplosionResistance() >= 600.0F;
   }

   private static BlockPos feetBlockPos() {
      AABB box = MC.player.getBoundingBox();
      return new BlockPos(Mth.floor(Mth.lerp(0.5, box.minX, box.maxX)), Mth.ceil(box.minY), Mth.floor(Mth.lerp(0.5, box.minZ, box.maxZ)));
   }

   private static List<BlockPos> buildSphere(float radius) {
      List<BlockPos> out = new ArrayList<>();
      int r = Mth.ceil(radius);
      float rSq = radius * radius;

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               if (x * x + y * y + z * z <= rSq) {
                  out.add(new BlockPos(x, y, z));
               }
            }
         }
      }

      out.sort(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ()));
      return out;
   }

   private static final class FallingPlayerSim {
      private final LocalPlayer player;
      private double x;
      private double y;
      private double z;
      private double motionX;
      private double motionY;
      private double motionZ;
      private int simulatedTicks;

      private FallingPlayerSim(LocalPlayer player) {
         this.player = player;
         this.x = player.getX();
         this.y = player.getY();
         this.z = player.getZ();
         Vec3 delta = player.getDeltaMovement();
         this.motionX = delta.x;
         this.motionY = delta.y;
         this.motionZ = delta.z;
      }

      static BlockPos findCollision(LocalPlayer player, int ticks) {
         AutoTotemModule.FallingPlayerSim sim = new AutoTotemModule.FallingPlayerSim(player);
         Vec3 rotationVec = player.getLookAngle();

         for (int i = 0; i < ticks; i++) {
            Vec3 start = new Vec3(sim.x, sim.y, sim.z);
            sim.calculateForTick(rotationVec);
            Vec3 end = new Vec3(sim.x, sim.y, sim.z);
            AABB box = player.getDimensions(player.getPose()).makeBoundingBox(start).expandTowards(end.subtract(start));
            Optional<BlockPos> support = Module.MC.level.findSupportingBlock(player, box);
            if (support.isPresent()) {
               return support.get();
            }
         }

         return null;
      }

      private void calculateForTick(Vec3 rotationVec) {
         if (this.player.isFallFlying()) {
            this.calculateElytraTick(rotationVec);
         } else {
            this.calculateFreeFallTick();
         }

         this.x = this.x + this.motionX;
         this.y = this.y + this.motionY;
         this.z = this.z + this.motionZ;
         this.simulatedTicks++;
      }

      private void calculateFreeFallTick() {
         double gravity = this.player.getGravity();
         if (this.motionY <= 0.0 && this.hasStatusEffect(MobEffects.SLOW_FALLING)) {
            this.motionY = this.motionY - Math.min(gravity, 0.01);
         } else {
            this.motionY -= gravity;
         }

         float speed = this.player.getSpeed() * 0.1F;
         if (speed > 0.0F) {
            Vec3 input = RiptideEntityAccessor.riptide$getInputVector(this.movementInput(), speed, this.player.getYRot());
            this.motionX = this.motionX + input.x;
            this.motionZ = this.motionZ + input.z;
         }

         this.motionX *= 0.91F;
         this.motionY *= 0.98F;
         this.motionZ *= 0.91F;
      }

      private void calculateElytraTick(Vec3 rotationVec) {
         double gravity = 0.08;
         if (this.motionY <= 0.0 && this.hasStatusEffect(MobEffects.SLOW_FALLING)) {
            gravity = 0.01;
         }

         double pitchRad = this.player.getXRot() * (float) (Math.PI / 180.0);
         double k = Math.sqrt(rotationVec.x * rotationVec.x + rotationVec.z * rotationVec.z);
         double l = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
         double m = rotationVec.length();
         double n = Mth.cos((float)pitchRad);
         n = n * n * Math.min(1.0, m / 0.4);
         Vec3 vec = new Vec3(this.motionX, this.motionY + gravity * (-1.0 + n * 0.75), this.motionZ);
         if (vec.y < 0.0 && k > 0.0) {
            double q = vec.y * -0.1 * n;
            vec = vec.add(rotationVec.x * q / k, q, rotationVec.z * q / k);
         }

         if (pitchRad < 0.0 && k > 0.0) {
            double q = l * -Mth.sin((float)pitchRad) * 0.04;
            vec = vec.add(-rotationVec.x * q / k, q * 3.2, -rotationVec.z * q / k);
         }

         if (k > 0.0) {
            vec = vec.add((rotationVec.x / k * l - vec.x) * 0.1, 0.0, (rotationVec.z / k * l - vec.z) * 0.1);
         }

         vec.add(RiptideEntityAccessor.riptide$getInputVector(this.movementInput(), 0.02F, this.player.getYRot()));
         float blockSpeedFactor = ((RiptidePlayerAccessor)this.player).riptide$getBlockSpeedFactor();
         this.motionX = vec.x * 0.99F * blockSpeedFactor;
         this.motionY = vec.y * 0.98F;
         this.motionZ = vec.z * 0.99F * blockSpeedFactor;
      }

      private boolean hasStatusEffect(Holder<MobEffect> effect) {
         MobEffectInstance instance = this.player.getEffect(effect);
         return instance != null && instance.getDuration() >= this.simulatedTicks;
      }

      private Vec3 movementInput() {
         Input keyPresses = this.player.input.keyPresses;
         float forward = (keyPresses.forward() ? 1.0F : 0.0F) - (keyPresses.backward() ? 1.0F : 0.0F);
         float sideways = (keyPresses.left() ? 1.0F : 0.0F) - (keyPresses.right() ? 1.0F : 0.0F);
         return new Vec3(sideways * 0.98, 0.0, forward * 0.98);
      }
   }
}
