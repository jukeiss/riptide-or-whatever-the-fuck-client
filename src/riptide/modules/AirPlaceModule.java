package riptide.modules;

import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Fail;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptidePlacementTick;
import riptide.util.multi.MultiPilot;

public final class AirPlaceModule extends Module {
   static final String MODULE_DESCRIPTION = "Places blocks in air.";
   static final String RANGE_TIP = "Set placement reach";
   static final String GUIDE_TIP = "Show placement guide";
   static final String FILL_TIP = "Fill placement guide";
   static final String COLOR_TIP = "Set guide color";
   private volatile BlockPos renderTarget;

   AirPlaceModule() {
      super("air-place", "AirPlace", ModuleCategory.PLAYER, "Places blocks in air.");
      this.add(new DoubleSetting("range", "Range", 5.0, 1.0, 6.0, 0.05).description("Set placement reach").build());
      this.add(new BoolSetting("guide", "Guide", true).description("Show placement guide").build());
      this.add(new BoolSetting("fill", "Fill", true).description("Fill placement guide").build());
      this.add(new ColorSetting("guide-color", "Guide Color", -50373).description("Set guide color").build());
   }

   @Override
   public void onDisable() {
      this.renderTarget = null;
   }

   @Override
   public void tick() {
      this.renderTarget = null;
      if (MC != null && MC.level != null) {
         Player player = MultiPilot.commandPlayer();
         AirPlaceModule.Placement placement = placement(player, MC.hitResult, this.decimal("range"));
         if (placement != null && !ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), this.id())) {
            if (this.bool("guide")) {
               this.renderTarget = placement.hit().getBlockPos().immutable();
            }

            this.executePlacement(placement);
         }
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand ignoredHand) {
      if (MC != null && MC.level != null && MC.player != null && MC.gameMode != null && !MultiPilot.isActive()) {
         AirPlaceModule.Placement placement = placement(MC.player, hitResult, this.decimal("range"));
         if (placement == null) {
            return false;
         } else if (MC.player.isHandsBusy()) {
            return true;
         } else {
            return ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), this.id()) ? true : true;
         }
      } else {
         return false;
      }
   }

   private void executePlacement(AirPlaceModule.Placement placement) {
      if (MC.player != null && MC.gameMode != null && !MultiPilot.isActive()) {
         if (MC.options.keyUse.isDown()) {
            if (((RiptideMinecraftAccessor)MC).riptide$getRightClickDelay() <= 0) {
               if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
                  if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     try {
                        if (RiptidePlacementTick.claim(this.id())) {
                           this.simulateVanillaUse(placement.hit());
                           ((RiptideMinecraftAccessor)MC).riptide$setRightClickDelay(4);
                           return;
                        }
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }
                  }
               }
            }
         }
      }
   }

   private void simulateVanillaUse(BlockHitResult hit) {
      for (InteractionHand hand : InteractionHand.values()) {
         if (hand != InteractionHand.OFF_HAND || !RiptideHandArbiter.offhandClaimedByOther(this.id())) {
            ItemStack stack = MC.player.getItemInHand(hand);
            if (!stack.isItemEnabled(MC.level.enabledFeatures())) {
               return;
            }

            int oldCount = stack.getCount();
            InteractionResult blockResult = MC.gameMode.useItemOn(MC.player, hand, hit);
            if (blockResult instanceof Success success) {
               if (success.swingSource() == SwingSource.CLIENT) {
                  MC.player.swing(hand);
               }

               if (!stack.isEmpty() && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials())) {
                  MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
               }

               return;
            }

            if (blockResult instanceof Fail) {
               return;
            }

            if (!stack.isEmpty() && MC.gameMode.useItem(MC.player, hand) instanceof Success success) {
               if (success.swingSource() == SwingSource.CLIENT) {
                  MC.player.swing(hand);
               }

               MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
               return;
            }
         }
      }
   }

   public BlockPos renderTarget() {
      return this.renderTarget;
   }

   public boolean renderFill() {
      return this.bool("fill");
   }

   public int guideColor() {
      try {
         String value = this.value("guide-color").replace("#", "");
         if (value.length() == 6) {
            value = "FF" + value;
         }

         return (int)Long.parseLong(value, 16);
      } catch (RuntimeException var2) {
         return -50373;
      }
   }

   public static AirPlaceModule.Placement activePlacement(Player player, HitResult currentHit) {
      return ModuleRegistry.get("air-place") instanceof AirPlaceModule airPlace && airPlace.isEnabled()
         ? placement(player, currentHit, airPlace.decimal("range"))
         : null;
   }

   static AirPlaceModule.Placement placement(Player player, HitResult currentHit, double range) {
      if (player != null && player.level() != null && currentHit != null && currentHit.getType() == Type.MISS && !handsBusy(player)) {
         InteractionHand hand = placementHand(player);
         if (hand == null) {
            return null;
         } else {
            HitResult picked = player.pick(Math.max(1.0, Math.min(6.0, range)), 0.0F, false);
            if (usesAirMiss(currentHit, picked) && picked instanceof BlockHitResult blockHit) {
               BlockPos pos = blockHit.getBlockPos();
               return !player.level().isOutsideBuildHeight(pos) && player.level().getBlockState(pos).canBeReplaced()
                  ? new AirPlaceModule.Placement(blockHit, hand)
                  : null;
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   static InteractionHand placementHand(Player player) {
      if (player == null) {
         return null;
      } else {
         ItemStack main = player.getMainHandItem();
         if (isPlaceable(main, player)) {
            return InteractionHand.MAIN_HAND;
         } else if (!main.isEmpty()) {
            return null;
         } else {
            return isPlaceable(player.getOffhandItem(), player) ? InteractionHand.OFF_HAND : null;
         }
      }
   }

   static boolean usesAirMiss(HitResult currentHit, HitResult pickedHit) {
      return currentHit != null && pickedHit instanceof BlockHitResult && currentHit.getType() == Type.MISS && pickedHit.getType() == Type.MISS;
   }

   private static boolean isPlaceable(ItemStack stack, Player player) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.isItemEnabled(player.level().enabledFeatures());
   }

   private static boolean handsBusy(Player player) {
      return player instanceof LocalPlayer local ? local.isHandsBusy() : player.isUsingItem();
   }

   static List<String> settingTips() {
      return List.of("Set placement reach", "Show placement guide", "Fill placement guide", "Set guide color");
   }

   public record Placement(BlockHitResult hit, InteractionHand hand) {
   }
}
