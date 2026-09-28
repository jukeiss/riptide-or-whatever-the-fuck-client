package riptide.modules;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class VelocityModule extends Module {
   private final AtomicReference<Vec3> pending = new AtomicReference<>();

   public VelocityModule() {
      super("velocity", "Velocity", ModuleCategory.COMBAT, "Reduces how far hits and explosions push you.");
      this.add(
         new IntSetting("horizontal", "Horizontal", 0, 0, 100, 5)
            .unit("%")
            .description("How much sideways knockback to keep. Zero means none at all.")
            .group("Hits")
            .build()
      );
      this.add(new IntSetting("vertical", "Vertical", 0, 0, 100, 5).unit("%").description("How much upward knockback to keep.").group("Hits").build());
      this.add(new BoolSetting("explosions", "Also Explosions", true).description("Apply the same to crystal and anchor blasts.").group("Explosions").build());
      this.add(
         new IntSetting("explosion-amount", "Explosion Knockback", 0, 0, 100, 5)
            .unit("%")
            .description("How much blast knockback to keep.")
            .visibleWhen(() -> this.bool("explosions"))
            .group("Explosions")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.pending.set(null);
   }

   @Override
   public void onDisable() {
      this.pending.set(null);
   }

   @Override
   public void onGameLeft() {
      this.pending.set(null);
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2 == null || var2.player == null) {
         return false;
      } else if (var1 instanceof ClientboundSetEntityMotionPacket var6) {
         if (var6.id() != var2.player.getId()) {
            return false;
         } else {
            Vec3 var7 = scale(var6.movement(), this.integer("horizontal"), this.integer("vertical"));
            if (var7 == null) {
               return false;
            } else {
               this.pending.set(var7);
               return true;
            }
         }
      } else if (!(var1 instanceof ClientboundExplodePacket var3 && this.bool("explosions"))) {
         return false;
      } else if (var3.playerKnockback().isEmpty()) {
         return false;
      } else {
         int var4 = this.integer("explosion-amount");
         if (var4 >= 100) {
            return false;
         } else {
            Vec3 var5 = scale((Vec3)var3.playerKnockback().get(), var4, var4);
            if (var5 != null) {
               this.pending.set(var5);
            }

            return false;
         }
      }
   }

   static Vec3 scale(Vec3 var0, int var1, int var2) {
      if (var0 == null) {
         return null;
      } else {
         int var3 = clampPercent(var1);
         int var4 = clampPercent(var2);
         return var3 == 100 && var4 == 100 ? null : new Vec3(var0.x * var3 / 100.0, var0.y * var4 / 100.0, var0.z * var3 / 100.0);
      }
   }

   static int clampPercent(int var0) {
      return Math.max(0, Math.min(100, var0));
   }

   @Override
   public void tick() {
      Vec3 var1 = this.pending.getAndSet(null);
      if (var1 != null) {
         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null) {
            var2.player.setDeltaMovement(var1);
         }
      }
   }

   @Override
   public String info() {
      int var1 = clampPercent(this.integer("horizontal"));
      int var2 = clampPercent(this.integer("vertical"));
      return var1 == 0 && var2 == 0 ? "none" : var1 + "/" + var2 + "%";
   }
}
