package riptide.modules;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class SpearSwapModule extends Module {
   private SpearSwapModule.Phase phase;
   private int previousSlot = -1;
   private int spearSlot = -1;
   private int timer;

   public SpearSwapModule() {
      super("spear-swap", "Spear Swap", ModuleCategory.COMBAT, "Swap to spear, lunge, swap back.");
      this.add(
         new ChoiceSetting("return-to", "Return To", "Previous", "Previous", "Sword", "Axe", "Mace", "Stay")
            .description("Slot selected after the lunge.")
            .build()
      );
      this.add(new BoolSetting("prefer-lunge", "Prefer Lunge", true).description("Pick the spear with the highest Lunge level.").build());
      this.add(new IntSetting("return-delay", "Return Delay", 2, 0, 20, 1).description("Ticks after the jab before swapping back.").build());
      this.add(new BoolSetting("auto-hit", "Auto Hit", true).description("Attack the entity you're aiming at after swapping back.").build());
      this.add(new IntSetting("hit-charge", "Hit Charge %", 90, 10, 100, 5).description("Minimum attack charge before the auto hit.").build());
      this.add(new IntSetting("hit-window", "Hit Window", 20, 1, 60, 1).description("Ticks to wait for a target before giving up.").build());
   }

   @Override
   public boolean emitsToggleMessage() {
      return false;
   }

   @Override
   public void onEnable() {
      this.phase = null;
      if (MC.player != null && MC.gameMode != null) {
         int var1 = this.findSpearSlot();
         if (var1 < 0) {
            this.finish("§cNo spear in your hotbar.");
         } else {
            String var2 = this.lungeBlockedReason();
            if (var2 != null && this.lungeLevel(MC.player.getInventory().getItem(var1)) > 0) {
               RiptideClientMessaging.sendPrefixed("§eLunge won't trigger: " + var2);
            }

            this.previousSlot = MC.player.getInventory().getSelectedSlot();
            this.spearSlot = var1;
            MC.player.getInventory().setSelectedSlot(var1);
            this.phase = SpearSwapModule.Phase.CHARGING;
            this.timer = 60;
         }
      } else {
         this.finish(null);
      }
   }

   @Override
   public void onDisable() {
      if (this.phase == SpearSwapModule.Phase.CHARGING && MC.player != null && this.previousSlot >= 0) {
         MC.player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.phase = null;
      this.previousSlot = -1;
      this.spearSlot = -1;
   }

   @Override
   public void onGameLeft() {
      this.phase = null;
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   @Override
   public void tick() {
      if (this.phase == null) {
         this.finish(null);
      } else if (MC.player != null && MC.gameMode != null && MC.gui.screen() == null && MC.player.isAlive()) {
         switch (this.phase) {
            case CHARGING:
               this.tickCharging();
               break;
            case RETURNING:
               this.tickReturning();
               break;
            case HITTING:
               this.tickHitting();
         }
      } else {
         this.finish(null);
      }
   }

   private void tickCharging() {
      ItemStack var1 = MC.player.getMainHandItem();
      PiercingWeapon var2 = (PiercingWeapon)var1.get(DataComponents.PIERCING_WEAPON);
      if (MC.player.getInventory().getSelectedSlot() != this.spearSlot || var2 == null) {
         this.finish(null);
      } else if (!MC.player.cannotAttackWithItem(var1, 0) && !MC.player.isHandsBusy()) {
         MC.gameMode.piercingAttack(var2);
         MC.player.swing(InteractionHand.MAIN_HAND);
         this.phase = SpearSwapModule.Phase.RETURNING;
         this.timer = this.integer("return-delay");
         if (this.timer == 0) {
            this.tickReturning();
         }
      } else {
         if (--this.timer <= 0) {
            this.finish("§cSpear never charged, cancelled.");
         }
      }
   }

   private void tickReturning() {
      if (--this.timer <= 0) {
         int var1 = this.returnSlot();
         if (var1 >= 0) {
            MC.player.getInventory().setSelectedSlot(var1);
         }

         if (this.bool("auto-hit")) {
            this.phase = SpearSwapModule.Phase.HITTING;
            this.timer = this.integer("hit-window");
         } else {
            this.finish(null);
         }
      }
   }

   private void tickHitting() {
      if (MC.hitResult instanceof EntityHitResult var1
         && MC.player.getAttackStrengthScale(0.5F) * 100.0F >= this.integer("hit-charge")
         && !MC.player.cannotAttackWithItem(MC.player.getMainHandItem(), 0)) {
         MC.gameMode.attack(MC.player, var1.getEntity());
         MC.player.swing(InteractionHand.MAIN_HAND);
         this.finish(null);
      } else {
         if (--this.timer <= 0) {
            this.finish(null);
         }
      }
   }

   private void finish(String var1) {
      if (var1 != null) {
         RiptideClientMessaging.sendPrefixed(var1);
      }

      this.phase = null;
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private int returnSlot() {
      String var1 = this.choice("return-to");

      return switch (var1) {
         case "Sword" -> this.bestHotbar(var0 -> var0.is(ItemTags.SWORDS));
         case "Axe" -> this.bestHotbar(var0 -> var0.is(ItemTags.AXES));
         case "Mace" -> this.bestHotbar(var0 -> var0.is(Items.MACE));
         case "Stay" -> -1;
         default -> this.previousSlot;
      };
   }

   private int findSpearSlot() {
      int var1 = -1;
      double var2 = -1.0;
      boolean var4 = this.bool("prefer-lunge");

      for (int var5 = 0; var5 < 9; var5++) {
         ItemStack var6 = MC.player.getInventory().getItem(var5);
         if (!var6.isEmpty() && var6.has(DataComponents.PIERCING_WEAPON)) {
            double var7 = attackDamage(var6) + (var4 ? this.lungeLevel(var6) * 100.0 : 0.0);
            if (var7 > var2) {
               var2 = var7;
               var1 = var5;
            }
         }
      }

      return var1;
   }

   private int bestHotbar(Predicate<ItemStack> var1) {
      int var2 = -1;
      double var3 = -1.0;

      for (int var5 = 0; var5 < 9; var5++) {
         ItemStack var6 = MC.player.getInventory().getItem(var5);
         if (!var6.isEmpty() && var1.test(var6)) {
            double var7 = attackDamage(var6);
            if (var7 > var3) {
               var3 = var7;
               var2 = var5;
            }
         }
      }

      return var2 >= 0 ? var2 : this.previousSlot;
   }

   private int lungeLevel(ItemStack var1) {
      for (Entry var3 : var1.getEnchantments().entrySet()) {
         if (((Holder)var3.getKey()).is(Enchantments.LUNGE)) {
            return var3.getIntValue();
         }
      }

      return 0;
   }

   private String lungeBlockedReason() {
      if (MC.player.isPassenger()) {
         return "you're riding something.";
      } else if (MC.player.isFallFlying()) {
         return "you're gliding.";
      } else if (MC.player.isInWater()) {
         return "you're in water.";
      } else {
         return !MC.player.hasInfiniteMaterials() && MC.player.getFoodData().getFoodLevel() < 7 ? "hunger is below 7." : null;
      }
   }

   private static double attackDamage(ItemStack var0) {
      return ((ItemAttributeModifiers)var0.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY))
         .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
   }

   private static enum Phase {
      CHARGING,
      RETURNING,
      HITTING;
   }
}
