package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideHandArbiter;

public final class AutoShieldModule extends Module {
   private static final double PROJECTILE_RANGE = 24.0;
   private static final double AIM_THRESHOLD = 0.96;
   private static final double MIN_PROJECTILE_SPEED = 0.35;
   private boolean holding;

   public AutoShieldModule() {
      super("auto-shield", "AutoShield", ModuleCategory.COMBAT, "Raises your shield when something is about to hit you.");
      this.add(
         new BoolSetting("projectiles", "Against Projectiles", true)
            .description("Block arrows and tridents that are actually aimed at you.")
            .group("When")
            .build()
      );
      this.add(
         new BoolSetting("melee", "Against Players", false)
            .description("Also block when a player is within reach. Off by default: blocking halves your speed and stops you attacking.")
            .group("When")
            .build()
      );
      this.add(
         new IntSetting("melee-range", "Player Range", 4, 2, 8, 1)
            .unit("blocks")
            .description("How close a player has to be to count.")
            .visibleWhen(() -> this.bool("melee"))
            .group("When")
            .build()
      );
      this.add(
         new BoolSetting("ignore-friends", "Ignore Friends", true)
            .description("Do not treat friends as a reason to block.")
            .visibleWhen(() -> this.bool("melee"))
            .group("When")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.holding = false;
   }

   @Override
   public void onDisable() {
      this.release();
   }

   @Override
   public void onGameLeft() {
      this.release();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.level == null || var1.options == null) {
         this.release();
      } else if (var1.gui.screen() != null || !hasShield(var1)) {
         this.release();
      } else if (!this.threatened(var1)) {
         this.release();
      } else if (!RiptideHandArbiter.holdHand(this.id())) {
         this.release();
      } else {
         var1.options.keyUse.setDown(true);
         this.holding = true;
      }
   }

   private boolean threatened(Minecraft var1) {
      return this.bool("projectiles") && this.incomingProjectile(var1) ? true : this.bool("melee") && this.playerInReach(var1);
   }

   private boolean incomingProjectile(Minecraft var1) {
      Vec3 var2 = var1.player.getEyePosition();

      for (Entity var4 : var1.level.entitiesForRendering()) {
         if (var4 instanceof Projectile var5 && var5.getOwner() != var1.player) {
            Vec3 var6 = var2.subtract(var5.position());
            double var7 = var6.length();
            if (!(var7 > 24.0) && !(var7 < 0.1)) {
               Vec3 var9 = var5.getDeltaMovement();
               double var10 = var9.length();
               if (!(var10 < 0.35) && !(var9.scale(1.0 / var10).dot(var6.scale(1.0 / var7)) < 0.96)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean playerInReach(Minecraft var1) {
      int var2 = this.integer("melee-range");

      for (Player var4 : var1.level.players()) {
         if (var4 != var1.player && !(var4.distanceTo(var1.player) > var2) && (!this.bool("ignore-friends") || !isFriend(var4))) {
            return true;
         }
      }

      return false;
   }

   private static boolean isFriend(Player var0) {
      try {
         return TeamsModule.isFriendOrTeam(var0);
      } catch (RuntimeException var2) {
         return true;
      }
   }

   private static boolean hasShield(Minecraft var0) {
      return isShield(var0.player.getOffhandItem()) || isShield(var0.player.getMainHandItem());
   }

   private static boolean isShield(ItemStack var0) {
      return var0 != null && !var0.isEmpty() && var0.is(Items.SHIELD);
   }

   private void release() {
      if (this.holding) {
         this.holding = false;
         Minecraft var1 = Minecraft.getInstance();
         if (var1 != null && var1.options != null) {
            var1.options.keyUse.setDown(false);
         }

         RiptideHandArbiter.releaseAll(this.id());
      }
   }

   @Override
   public String info() {
      return this.holding ? "blocking" : "";
   }
}
