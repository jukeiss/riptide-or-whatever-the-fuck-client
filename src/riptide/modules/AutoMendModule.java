package riptide.modules;

import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class AutoMendModule extends Module {
   private static final int HOTBAR_SLOTS = 9;
   private static final long MIN_GAP_MS = 250L;
   private long lastThrowMs;
   private int previousSlot = -1;
   private boolean warnedEmpty;

   public AutoMendModule() {
      super("auto-mend", "AutoMend", ModuleCategory.PLAYER, "Throws experience bottles to keep mending gear repaired.");
      this.add(
         new IntSetting("threshold", "Repair Below", 60, 5, 99, 5)
            .unit("%")
            .description("Start throwing once a piece drops below this much durability.")
            .group("General")
            .build()
      );
      this.add(new IntSetting("delay", "Delay", 400, 250, 3000, 50).unit("ms").description("Gap between throws.").group("General").build());
      this.add(
         new BoolSetting("include-held", "Include Held Item", true)
            .description("Also count whatever is in your hand, not just armour.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("pause-near-players", "Pause Near Players", true)
            .description("Stop while another player is close. Bottles in your face lose fights.")
            .group("Safety")
            .build()
      );
      this.add(
         new IntSetting("player-range", "Player Range", 16, 4, 64, 4)
            .unit("blocks")
            .description("How close is too close.")
            .visibleWhen(() -> this.bool("pause-near-players"))
            .group("Safety")
            .build()
      );
      this.add(new BoolSetting("only-on-ground", "Only On Ground", true).description("Do not throw while falling or flying.").group("Safety").build());
   }

   @Override
   public void onDisable() {
      this.restore();
      this.warnedEmpty = false;
   }

   @Override
   public void onGameLeft() {
      this.previousSlot = -1;
      this.warnedEmpty = false;
      RiptideHandArbiter.releaseAll(this.id());
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.gameMode == null || var1.level == null) {
         this.previousSlot = -1;
         RiptideHandArbiter.releaseAll(this.id());
      } else if (this.previousSlot >= 0) {
         this.restore();
      } else if (!this.bool("only-on-ground") || var1.player.onGround()) {
         if (!this.bool("pause-near-players") || !this.playerNearby(var1)) {
            if (System.currentTimeMillis() - this.lastThrowMs >= Math.max(250L, (long)this.integer("delay"))) {
               if (!this.needsRepair(var1)) {
                  this.warnedEmpty = false;
               } else {
                  int var2 = this.bottleSlot(var1);
                  if (var2 < 0) {
                     if (!this.warnedEmpty) {
                        this.warnedEmpty = true;
                        RiptideClientMessaging.sendPrefixed("§cAutoMend: no experience bottles in your hotbar.");
                     }
                  } else {
                     this.warnedEmpty = false;
                     if (this.takeHand(var1, var2)) {
                        var1.gameMode.useItem(var1.player, InteractionHand.MAIN_HAND);
                        this.lastThrowMs = System.currentTimeMillis();
                     }
                  }
               }
            }
         }
      }
   }

   private boolean needsRepair(Minecraft var1) {
      int var2 = this.integer("threshold");

      for (EquipmentSlot var6 : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         if (isWorn(var1.player.getItemBySlot(var6), var2)) {
            return true;
         }
      }

      return !this.bool("include-held") ? false : isWorn(var1.player.getMainHandItem(), var2) || isWorn(var1.player.getOffhandItem(), var2);
   }

   private static boolean isWorn(ItemStack var0, int var1) {
      return var0 != null && !var0.isEmpty() && var0.isDamageableItem() ? ItemSaverModule.percentLeft(var0) < var1 : false;
   }

   private boolean playerNearby(Minecraft var1) {
      int var2 = this.integer("player-range");
      Iterator var3 = var1.level.players().iterator();

      while (true) {
         if (!var3.hasNext()) {
            return false;
         }

         Player var4 = (Player)var3.next();
         if (var4 != var1.player && !(var4.distanceTo(var1.player) > var2)) {
            try {
               if (TeamsModule.isFriendOrTeam(var4)) {
                  continue;
               }
            } catch (RuntimeException var6) {
            }
            break;
         }
      }

      return true;
   }

   private int bottleSlot(Minecraft var1) {
      for (int var2 = 0; var2 < 9; var2++) {
         if (!RiptideHandArbiter.slotReserved(var2, this.id())) {
            ItemStack var3 = var1.player.getInventory().getItem(var2);
            if (var3 != null && var3.is(Items.EXPERIENCE_BOTTLE)) {
               return var2;
            }
         }
      }

      return -1;
   }

   private void restore() {
      int var1 = this.previousSlot;
      this.previousSlot = -1;

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

   private boolean takeHand(Minecraft var1, int var2) {
      if (!RiptideHandArbiter.holdHand(this.id())) {
         return false;
      } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         RiptideHandArbiter.releaseHand(this.id());
         return false;
      } else {
         try {
            this.previousSlot = var1.player.getInventory().getSelectedSlot();
            RiptideHandArbiter.reserveSlot(this.id(), var2);
            RiptideInventoryHelper.selectHotbarSlot(var1, var2);
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }

         return true;
      }
   }
}
