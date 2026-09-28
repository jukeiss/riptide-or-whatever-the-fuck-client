package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class AutoWeaponModule extends Module {
   private static final String BEST = "Best Damage";
   private static final String SWORD = "Prefer Sword";
   private static final String AXE = "Prefer Axe";
   private static final int HOTBAR_SLOTS = 9;
   private static final int RETURN_DELAY_TICKS = 3;
   private int previousSlot = -1;
   private int returnIn;

   public AutoWeaponModule() {
      super("auto-weapon", "AutoWeapon", ModuleCategory.COMBAT, "Switches to your best weapon when you attack a player, then switches back.");
      this.add(
         new ChoiceSetting("prefer", "Choose", "Best Damage", "Best Damage", "Prefer Sword", "Prefer Axe")
            .description("Which weapon to pick. Axes hit harder and slower; swords are steadier.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("players-only", "Players Only", true).description("Only switch when the thing you hit is a player.").group("General").build());
      this.add(
         new BoolSetting("switch-back", "Switch Back", true)
            .description("Return to the slot you were holding once the hit has gone out.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.previousSlot = -1;
      this.returnIn = 0;
   }

   @Override
   public void onDisable() {
      this.restore();
   }

   @Override
   public void onGameLeft() {
      this.previousSlot = -1;
      this.returnIn = 0;
      RiptideHandArbiter.releaseAll(this.id());
   }

   @Override
   public boolean shouldCancelAttack(HitResult var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2 == null || var2.player == null || var2.level == null) {
         return false;
      } else if (this.previousSlot >= 0) {
         return false;
      } else if (!this.bool("players-only") || var1 instanceof EntityHitResult var3 && var3.getEntity() instanceof Player) {
         int var7 = this.bestWeaponSlot(var2);
         if (var7 < 0 || var7 == var2.player.getInventory().getSelectedSlot()) {
            return false;
         } else if (!RiptideHandArbiter.holdHand(this.id())) {
            return false;
         } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            RiptideHandArbiter.releaseHand(this.id());
            return false;
         } else {
            try {
               this.previousSlot = var2.player.getInventory().getSelectedSlot();
               RiptideHandArbiter.reserveSlot(this.id(), var7);
               RiptideInventoryHelper.selectHotbarSlot(var2, var7);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }

            this.returnIn = 3;
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.level == null) {
         this.previousSlot = -1;
         this.returnIn = 0;
         RiptideHandArbiter.releaseAll(this.id());
      } else if (this.previousSlot >= 0) {
         if (!this.bool("switch-back")) {
            this.previousSlot = -1;
            this.returnIn = 0;
            RiptideHandArbiter.releaseAll(this.id());
         } else if (--this.returnIn <= 0) {
            this.restore();
         }
      }
   }

   private int bestWeaponSlot(Minecraft var1) {
      String var2 = this.choice("prefer");
      int var3 = -1;
      double var4 = 0.0;

      for (int var6 = 0; var6 < 9; var6++) {
         if (!RiptideHandArbiter.slotReserved(var6, this.id())) {
            ItemStack var7 = var1.player.getInventory().getItem(var6);
            if (var7 != null && !var7.isEmpty()) {
               double var8 = score(var7, var2);
               if (!(var8 <= var4)) {
                  var4 = var8;
                  var3 = var6;
               }
            }
         }
      }

      return var3;
   }

   static double score(ItemStack var0, String var1) {
      if (var0 != null && !var0.isEmpty()) {
         boolean var2 = var0.is(ItemTags.SWORDS);
         boolean var3 = var0.is(ItemTags.AXES);
         if (!var2 && !var3) {
            return 0.0;
         } else {
            double var4 = 1.0 + attackDamage(var0);
            if (var2 && "Prefer Sword".equals(var1)) {
               var4 += 100.0;
            }

            if (var3 && "Prefer Axe".equals(var1)) {
               var4 += 100.0;
            }

            return var4;
         }
      } else {
         return 0.0;
      }
   }

   private static double attackDamage(ItemStack var0) {
      double var1 = 0.0;

      try {
         ItemAttributeModifiers var3 = (ItemAttributeModifiers)var0.get(DataComponents.ATTRIBUTE_MODIFIERS);
         if (var3 == null) {
            return 0.0;
         } else {
            for (Entry var5 : var3.modifiers()) {
               if (var5.attribute().is(Attributes.ATTACK_DAMAGE)) {
                  var1 += var5.modifier().amount();
               }
            }

            return var1;
         }
      } catch (Throwable var6) {
         return 0.0;
      }
   }

   private void restore() {
      int var1 = this.previousSlot;
      this.previousSlot = -1;
      this.returnIn = 0;

      try {
         if (var1 < 0) {
            return;
         }

         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null) {
            RiptideInventoryHelper.restoreHotbarSlot(var2, var1);
            return;
         }
      } finally {
         RiptideHandArbiter.releaseAll(this.id());
      }
   }
}
