package riptide.modules;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideMaceAssist;

public final class MaceSlamModule extends Module {
   private MaceSlamModule.Phase phase = MaceSlamModule.Phase.IDLE;
   private int wait;
   private int returnSlot = -1;
   private LivingEntity comboTarget;
   private long lastHitMs;
   private int stall;

   public MaceSlamModule() {
      super("mace-slam", "Stun Slam", ModuleCategory.COMBAT, "Breaks a shield with an axe, then slams with the mace.");
      this.add(
         new ChoiceSetting("mode", "Mode", "Auto", "Auto", "Hotkey")
            .description("Auto hits whenever a target is in range; Hotkey only while the key is held.")
            .build()
      );
      this.add(
         new KeybindSetting("key", "Slam Key", -1).description("Held key for Hotkey mode.").visibleWhen(() -> "Hotkey".equals(this.choice("mode"))).build()
      );
      this.add(new IntSetting("range", "Range", 3, 1, 3, 1).description("How close a target must be, in blocks.").build());
      this.add(
         new BoolSetting("break-shield", "Break Shield", true)
            .description("If the target is blocking, hit them with an axe first to disable the shield.")
            .build()
      );
      this.add(
         new BoolSetting("shield-only", "Shielding Only", false)
            .description("Only run the combo on targets who are blocking. Off also mace-hits unshielded targets.")
            .build()
      );
      this.add(new BoolSetting("players-only", "Players Only", true).group("Targeting").description("Only target players, never mobs.").build());
      this.add(new BoolSetting("face", "Face Target", false).group("Targeting").description("Turn to look at the target before each hit so it lands.").build());
      this.add(
         new BoolSetting("require-fall", "Slam On Fall Only", false)
            .group("Mace")
            .description("Hold the mace hit until you're falling, so the slam lands its bonus fall damage.")
            .build()
      );
      this.add(
         new IntSetting("min-fall", "Min Fall Distance", 3, 0, 20, 1)
            .group("Mace")
            .description("How far you must have fallen before the slam fires, in blocks.")
            .visibleWhen(() -> this.bool("require-fall"))
            .build()
      );
      this.add(
         new IntSetting("swap-delay", "Swap Delay", 3, 1, 20, 1)
            .group("Timing")
            .description("Ticks to wait after switching to the axe or mace before swinging (20 ticks = 1 second).")
            .build()
      );
      this.add(
         new IntSetting("break-delay", "Slam Delay", 4, 0, 40, 1)
            .group("Timing")
            .description("Ticks to wait after the shield break, before switching to the mace.")
            .build()
      );
      this.add(new IntSetting("cooldown", "Cooldown", 600, 0, 5000, 50).group("Timing").description("Milliseconds between combos.").build());
      this.add(new BoolSetting("return-mace", "Return To Slot", true).description("Switch back to the slot you were holding once the combo finishes.").build());
      this.add(new BoolSetting("require-mace", "Require Mace", true).description("Only act when you have a mace in your hotbar.").build());
      this.add(new BoolSetting("swing", "Swing Hand", true).description("Play the swing animation on each hit.").build());
      this.add(new BoolSetting("keep-mace", "Keep Mace In KillAura", true).description("Stop KillAura swapping your mace out for a sword.").build());
   }

   @Override
   public String info() {
      return this.phase == MaceSlamModule.Phase.IDLE ? this.choice("mode") : "combo";
   }

   @Override
   public void onEnable() {
      RiptideMaceAssist.keepMace = this.bool("keep-mace");
      this.reset();
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if ("keep-mace".equals(var1)) {
         RiptideMaceAssist.keepMace = this.bool("keep-mace");
      }
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.level != null && MC.gameMode != null && MC.gui.screen() == null) {
         int var1 = this.findSlot(true);
         if (this.bool("require-mace") && var1 < 0) {
            this.reset();
         } else {
            if ("Hotkey".equals(this.choice("mode"))) {
               int var2 = this.integer("key");
               if (var2 == -1 || !RiptideBindUtil.isBindPressed(MC, var2)) {
                  this.reset();
                  return;
               }
            }

            if (this.phase == MaceSlamModule.Phase.IDLE) {
               long var7 = System.currentTimeMillis();
               if (var7 - this.lastHitMs >= this.integer("cooldown")) {
                  LivingEntity var4 = this.nearestTarget(this.integer("range"));
                  if (var4 != null) {
                     boolean var5 = var4.isBlocking();
                     if (!this.bool("shield-only") || var5) {
                        if (this.bool("break-shield") && var5) {
                           int var6 = this.findSlot(false);
                           if (var6 >= 0) {
                              this.returnSlot = MC.player.getInventory().getSelectedSlot();
                              this.comboTarget = var4;
                              this.selectSlot(var6);
                              this.phase = MaceSlamModule.Phase.AXE_SWUNG;
                              this.wait = this.integer("swap-delay");
                              return;
                           }
                        }

                        if (this.fallReady()) {
                           if (var1 >= 0) {
                              this.selectSlot(var1);
                           }

                           this.hit(var4);
                        }
                     }
                  }
               }
            } else if (this.wait > 0) {
               this.wait--;
            } else {
               LivingEntity var8 = this.comboTarget != null && this.comboTarget.isAlive() ? this.comboTarget : this.nearestTarget(this.integer("range"));
               switch (this.phase) {
                  case AXE_SWUNG:
                     if (var8 != null) {
                        this.hit(var8);
                     }

                     this.phase = MaceSlamModule.Phase.BROKEN;
                     this.wait = this.integer("break-delay");
                     break;
                  case BROKEN:
                     if (var1 >= 0) {
                        this.selectSlot(var1);
                     }

                     this.phase = MaceSlamModule.Phase.MACE_SWUNG;
                     this.wait = this.integer("swap-delay");
                     break;
                  case MACE_SWUNG:
                     if (!this.fallReady() && this.stall < 60) {
                        this.stall++;
                        this.wait = 1;
                        return;
                     }

                     if (var8 != null) {
                        this.hit(var8);
                     }

                     if (this.bool("return-mace") && this.returnSlot >= 0) {
                        this.selectSlot(this.returnSlot);
                     }

                     this.endCombo();
                     break;
                  default:
                     this.endCombo();
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private boolean fallReady() {
      return !this.bool("require-fall") ? true : !MC.player.onGround() && MC.player.fallDistance >= this.integer("min-fall");
   }

   private void endCombo() {
      this.phase = MaceSlamModule.Phase.IDLE;
      this.wait = 0;
      this.stall = 0;
      this.returnSlot = -1;
      this.comboTarget = null;
   }

   private void hit(LivingEntity var1) {
      if (this.bool("face")) {
         this.faceTarget(var1);
      }

      MC.gameMode.attack(MC.player, var1);
      if (this.bool("swing")) {
         MC.player.swing(InteractionHand.MAIN_HAND);
      }

      this.lastHitMs = System.currentTimeMillis();
   }

   private void faceTarget(LivingEntity var1) {
      Vec3 var2 = MC.player.getEyePosition();
      Vec3 var3 = var1.getBoundingBox().getCenter();
      double var4 = var3.x - var2.x;
      double var6 = var3.y - var2.y;
      double var8 = var3.z - var2.z;
      double var10 = Math.sqrt(var4 * var4 + var8 * var8);
      float var12 = (float)(Math.toDegrees(Math.atan2(var8, var4)) - 90.0);
      float var13 = (float)(-Math.toDegrees(Math.atan2(var6, var10)));
      MC.player.setYRot(var12);
      MC.player.setXRot(Math.max(-90.0F, Math.min(90.0F, var13)));
   }

   private int findSlot(boolean var1) {
      int var2 = MC.player.getInventory().getSelectedSlot();
      if (matches(MC.player.getInventory().getItem(var2), var1)) {
         return var2;
      } else {
         for (int var3 = 0; var3 < 9; var3++) {
            if (matches(MC.player.getInventory().getItem(var3), var1)) {
               return var3;
            }
         }

         return -1;
      }
   }

   private static boolean matches(ItemStack var0, boolean var1) {
      if (var0.isEmpty()) {
         return false;
      } else {
         return var1 ? var0.is(Items.MACE) : var0.is(ItemTags.AXES);
      }
   }

   private void selectSlot(int var1) {
      if (var1 >= 0 && var1 != MC.player.getInventory().getSelectedSlot()) {
         MC.player.getInventory().setSelectedSlot(var1);
      }
   }

   private LivingEntity nearestTarget(double var1) {
      double var3 = var1 * var1;
      LivingEntity var5 = null;
      double var6 = Double.MAX_VALUE;
      Vec3 var8 = MC.player.getEyePosition();

      for (Entity var10 : MC.level.entitiesForRendering()) {
         if (var10 instanceof LivingEntity var11
            && var10 != MC.player
            && var11.isAlive()
            && !(var11 instanceof Player var12 && var12.isSpectator())
            && (!this.bool("players-only") || var11 instanceof Player)) {
            double var14 = var11.getBoundingBox().getCenter().distanceToSqr(var8);
            if (!(var14 > var3) && !(var14 >= var6) && MC.player.hasLineOfSight(var11)) {
               var5 = var11;
               var6 = var14;
            }
         }
      }

      return var5;
   }

   private void reset() {
      if (this.phase != MaceSlamModule.Phase.IDLE && this.bool("return-mace") && this.returnSlot >= 0 && MC.player != null) {
         this.selectSlot(this.returnSlot);
      }

      this.endCombo();
   }

   private static enum Phase {
      IDLE,
      AXE_SWUNG,
      BROKEN,
      MACE_SWUNG;
   }
}
