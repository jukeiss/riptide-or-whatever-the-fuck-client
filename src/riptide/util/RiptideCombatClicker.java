package riptide.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import riptide.modules.PackHideState;

public final class RiptideCombatClicker {
   private static final Minecraft MC = Minecraft.getInstance();
   private static BlockHitResult pendingUseHit;
   private static InteractionHand pendingUseHand;
   private static EntityHitResult pendingAttackHit;
   private static BlockHitResult pendingMissHit;
   private static BlockHitResult pendingHoldUseHit;
   private static InteractionHand pendingHoldUseHand;
   private static BlockHitResult pendingHoldAttackHit;
   private static boolean heldUse;
   private static boolean heldAttack;
   private static boolean useInFlight;
   private static boolean attackInFlight;
   private static boolean pressedUse;
   private static boolean pressedAttack;
   private static boolean suppressedUse;
   private static boolean suppressedAttack;
   private static Float savedYaw;
   private static float savedPitch;

   private RiptideCombatClicker() {
   }

   public static boolean queueUse(BlockHitResult hit, InteractionHand hand) {
      if (hit != null
         && hand != null
         && !PackHideState.isHardLocked()
         && pendingUseHit == null
         && pendingAttackHit == null
         && pendingMissHit == null
         && !holding()
         && !holdRequested()) {
         pendingUseHit = hit;
         pendingUseHand = hand;
         return true;
      } else {
         return false;
      }
   }

   public static boolean queueAttack(EntityHitResult hit) {
      if (hit != null
         && !PackHideState.isHardLocked()
         && pendingUseHit == null
         && pendingAttackHit == null
         && pendingMissHit == null
         && !holding()
         && !holdRequested()) {
         pendingAttackHit = hit;
         return true;
      } else {
         return false;
      }
   }

   public static boolean queueAttackMiss(BlockHitResult miss) {
      if (miss != null
         && !PackHideState.isHardLocked()
         && pendingUseHit == null
         && pendingAttackHit == null
         && pendingMissHit == null
         && !holding()
         && !holdRequested()) {
         pendingMissHit = miss;
         return true;
      } else {
         return false;
      }
   }

   public static boolean holdAttack(BlockHitResult ray) {
      if (ray == null
         || MC == null
         || MC.player == null
         || MC.options == null
         || PackHideState.isHardLocked()
         || pendingUseHit != null
         || pendingAttackHit != null
         || pendingMissHit != null
         || MC.gui.screen() != null
         || MC.gui.overlay() != null) {
         return false;
      } else if (!heldUse && pendingHoldUseHit == null) {
         pendingHoldAttackHit = ray;
         return true;
      } else {
         return false;
      }
   }

   public static boolean holdUse(BlockHitResult ray, InteractionHand hand) {
      if (ray == null
         || hand == null
         || MC == null
         || MC.player == null
         || MC.options == null
         || PackHideState.isHardLocked()
         || pendingUseHit != null
         || pendingAttackHit != null
         || pendingMissHit != null
         || MC.gui.screen() != null
         || MC.gui.overlay() != null) {
         return false;
      } else if (!heldAttack && pendingHoldAttackHit == null) {
         pendingHoldUseHit = ray;
         pendingHoldUseHand = hand;
         return true;
      } else {
         return false;
      }
   }

   public static void releaseHold() {
      pendingHoldUseHit = null;
      pendingHoldUseHand = null;
      pendingHoldAttackHit = null;
      if (MC != null && MC.options != null) {
         if (heldAttack) {
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$resetPressedState();
            heldAttack = false;
         }

         if (heldUse) {
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$resetPressedState();
            heldUse = false;
         }

         if (savedYaw != null && MC.player != null) {
            MC.player.setYRot(savedYaw);
            MC.player.setXRot(savedPitch);
            savedYaw = null;
         }
      } else {
         heldUse = false;
         heldAttack = false;
      }
   }

   public static boolean holding() {
      return heldUse || heldAttack;
   }

   private static boolean holdRequested() {
      return pendingHoldUseHit != null || pendingHoldAttackHit != null;
   }

   public static boolean mainHandWouldPreempt() {
      ItemStack main = MC != null && MC.player != null ? MC.player.getMainHandItem() : ItemStack.EMPTY;
      if (main.isEmpty()) {
         return false;
      } else if (main.getItem() instanceof BlockItem) {
         return true;
      } else if (main.getUseAnimation() != ItemUseAnimation.NONE) {
         return true;
      } else if (main.has(DataComponents.EQUIPPABLE)) {
         return true;
      } else {
         return !main.is(Items.EXPERIENCE_BOTTLE)
               && !main.is(Items.ENDER_PEARL)
               && !main.is(Items.SNOWBALL)
               && !main.is(Items.EGG)
               && !main.is(Items.FIREWORK_ROCKET)
               && !main.is(Items.FISHING_ROD)
               && !(main.getItem() instanceof BucketItem)
               && !main.is(Items.WIND_CHARGE)
               && !main.is(Items.FIRE_CHARGE)
               && !main.is(Items.ENDER_EYE)
               && !main.is(Items.SPLASH_POTION)
               && !main.is(Items.LINGERING_POTION)
               && !main.is(Items.FLINT_AND_STEEL)
               && !main.is(Items.BONE_MEAL)
               && !main.is(Items.GLASS_BOTTLE)
               && !main.is(Items.CARROT_ON_A_STICK)
            ? main.getItem() instanceof SpawnEggItem
               || main.getItem() instanceof BoatItem
               || main.getItem() instanceof MinecartItem
               || main.getItem() instanceof ItemFrameItem
               || main.getItem() instanceof ShovelItem
               || main.getItem() instanceof AxeItem
               || main.getItem() instanceof HoeItem
               || main.getItem() instanceof ShearsItem
            : true;
      }
   }

   public static void cancel() {
      pendingUseHit = null;
      pendingUseHand = null;
      pendingAttackHit = null;
      pendingMissHit = null;
      useInFlight = false;
      attackInFlight = false;
      releaseHold();
      if (MC != null && MC.options != null) {
         if (pressedUse) {
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$resetPressedState();
            pressedUse = false;
         }

         if (pressedAttack) {
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$resetPressedState();
            pressedAttack = false;
         }
      }

      if (savedYaw != null && MC != null && MC.player != null) {
         MC.player.setYRot(savedYaw);
         MC.player.setXRot(savedPitch);
         savedYaw = null;
      }
   }

   public static boolean useInFlight() {
      return useInFlight;
   }

   public static boolean beginUse() {
      if (!useInFlight) {
         return false;
      } else {
         useInFlight = false;
         return true;
      }
   }

   public static boolean attackInFlight() {
      return attackInFlight;
   }

   public static boolean beginAttack() {
      if (!attackInFlight) {
         return false;
      } else {
         attackInFlight = false;
         return true;
      }
   }

   public static boolean ownsKeyUseThisTick() {
      return pendingUseHit != null || useInFlight || pressedUse || heldUse || pendingHoldUseHit != null;
   }

   public static boolean ownsKeyAttackThisTick() {
      return pendingAttackHit != null || pendingMissHit != null || attackInFlight || pressedAttack || heldAttack || pendingHoldAttackHit != null;
   }

   public static void beforeHandleKeybinds() {
      if (MC != null && MC.player != null && MC.options != null && !PackHideState.isHardLocked()) {
         BlockHitResult useHit = pendingUseHit;
         EntityHitResult attackHit = pendingAttackHit;
         BlockHitResult missHit = pendingMissHit;
         BlockHitResult holdAttackRay = pendingHoldAttackHit;
         BlockHitResult holdUseRay = pendingHoldUseHit;
         if (useHit == null && attackHit == null && missHit == null && holdAttackRay == null && holdUseRay == null) {
            if (heldAttack || heldUse) {
               releaseHold();
            }
         } else if (MC.gui.screen() == null && MC.gui.overlay() == null) {
            pendingUseHit = null;
            pendingUseHand = null;
            pendingAttackHit = null;
            pendingMissHit = null;
            pendingHoldAttackHit = null;
            pendingHoldUseHit = null;
            pendingHoldUseHand = null;
            savedYaw = MC.player.getYRot();
            savedPitch = MC.player.getXRot();
            RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
            if (wire.initialized()) {
               MC.player.setYRot(wire.currentYaw());
               MC.player.setXRot(wire.currentPitch());
            }

            if (attackHit != null) {
               MC.hitResult = attackHit;
               drainAllKeys();
               suppressOppositeKey(MC.options.keyUse, true);
               RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(true);
               pressedAttack = true;
               attackInFlight = true;
            } else if (missHit != null) {
               MC.hitResult = missHit;
               drainAllKeys();
               suppressOppositeKey(MC.options.keyUse, true);
               RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(true);
               pressedAttack = true;
               attackInFlight = true;
            } else if (useHit != null) {
               MC.hitResult = useHit;
               drainAllKeys();
               suppressOppositeKey(MC.options.keyAttack, false);
               RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(true);
               pressedUse = true;
               useInFlight = true;
            } else if (holdAttackRay != null) {
               MC.hitResult = holdAttackRay;
               drainAllKeys();
               suppressOppositeKey(MC.options.keyUse, true);
               if (!heldAttack) {
                  RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(false);
                  RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(true);
                  heldAttack = true;
                  attackInFlight = true;
               }
            } else {
               MC.hitResult = holdUseRay;
               drainAllKeys();
               suppressOppositeKey(MC.options.keyAttack, false);
               if (!heldUse) {
                  RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(false);
                  RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(true);
                  heldUse = true;
               }

               useInFlight = true;
            }
         } else {
            cancel();
         }
      } else {
         cancel();
      }
   }

   private static void suppressOppositeKey(KeyMapping key, boolean use) {
      RiptideKeyMappingBridge.of(key).riptide$simulatePress(false);
      if (use) {
         suppressedUse = true;
      } else {
         suppressedAttack = true;
      }
   }

   private static void drainAllKeys() {
      while (MC.options.keyUse.consumeClick()) {
      }

      while (MC.options.keyAttack.consumeClick()) {
      }

      while (MC.options.keyPickItem.consumeClick()) {
      }
   }

   public static void afterHandleKeybinds() {
      if (MC != null && MC.options != null) {
         if (pressedUse) {
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$resetPressedState();
            pressedUse = false;
         }

         if (pressedAttack) {
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$simulatePress(false);
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$resetPressedState();
            pressedAttack = false;
         }

         if (suppressedUse) {
            RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$resetPressedState();
            suppressedUse = false;
         }

         if (suppressedAttack) {
            RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$resetPressedState();
            suppressedAttack = false;
         }

         useInFlight = false;
         attackInFlight = false;
         if (savedYaw != null && MC.player != null) {
            MC.player.setYRot(savedYaw);
            MC.player.setXRot(savedPitch);
            savedYaw = null;
         }
      } else {
         pressedUse = false;
         pressedAttack = false;
         suppressedUse = false;
         suppressedAttack = false;
         heldUse = false;
         heldAttack = false;
         useInFlight = false;
         attackInFlight = false;
         savedYaw = null;
      }
   }
}
