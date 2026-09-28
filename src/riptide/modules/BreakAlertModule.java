package riptide.modules;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class BreakAlertModule extends Module {
   private static final long ALERT_INTERVAL_MS = 4000L;
   private static final long BURST_WINDOW_MS = 10000L;
   private final AtomicLong nextAlertMs = new AtomicLong();
   private final AtomicLong burst = new AtomicLong();
   private volatile long pendingPos = Long.MIN_VALUE;
   private static final double REACH_SQ = 49.0;

   public BreakAlertModule() {
      super("break-alert", "BreakAlert", ModuleCategory.MISC, "Warns you when blocks are broken near you.");
      this.add(new IntSetting("range", "Range", 24, 4, 96, 4).unit("blocks").description("How close a break has to be to count.").group("General").build());
      this.add(
         new IntSetting("burst", "Blocks Before Warning", 4, 1, 20, 1)
            .description("How many breaks within ten seconds count as digging rather than an accident. One block is somebody walking through a field.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("waypoint", "Save Waypoint", false).description("Drop a waypoint where the digging started.").group("General").build());
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping as well as printing the warning.").group("Alerts").build());
   }

   @Override
   public void onEnable() {
      this.nextAlertMs.set(0L);
      this.burst.set(0L);
      this.pendingPos = Long.MIN_VALUE;
   }

   @Override
   public void onGameJoin() {
      this.onEnable();
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundBlockUpdatePacket var2) {
         BlockState var3 = var2.getBlockState();
         if (var3 != null && var3.is(Blocks.AIR)) {
            Minecraft var4 = Minecraft.getInstance();
            if (var4 != null && var4.player != null) {
               BlockPos var5 = var2.getPos();
               int var6 = this.integer("range");
               double var7 = var5.getX() + 0.5 - var4.player.getX();
               double var9 = var5.getY() + 0.5 - var4.player.getY();
               double var11 = var5.getZ() + 0.5 - var4.player.getZ();
               double var13 = var7 * var7 + var9 * var9 + var11 * var11;
               if (var13 > (double)var6 * var6) {
                  return false;
               } else if (var13 <= 49.0) {
                  return false;
               } else {
                  long var15 = System.currentTimeMillis();
                  if (this.countBreak(var15) < this.integer("burst")) {
                     return false;
                  } else {
                     long var17 = this.nextAlertMs.get();
                     if (var15 >= var17 && this.nextAlertMs.compareAndSet(var17, var15 + 4000L)) {
                        this.pendingPos = var5.asLong();
                        this.alert(var4, var5, (int)Math.round(Math.sqrt(var13)));
                        return false;
                     } else {
                        return false;
                     }
                  }
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int countBreak(long var1) {
      long var3;
      int var9;
      long var12;
      do {
         var3 = this.burst.get();
         int var5 = (int)(var3 >>> 48);
         long var6 = var3 & 281474976710655L;
         boolean var8 = var5 == 0 || var1 - var6 > 10000L;
         var9 = var8 ? 1 : Math.min(var5 + 1, 65535);
         long var10 = var8 ? var1 : var6;
         var12 = (long)var9 << 48 | var10 & 281474976710655L;
      } while (!this.burst.compareAndSet(var3, var12));

      return var9;
   }

   private void alert(Minecraft var1, BlockPos var2, int var3) {
      RiptideClientMessaging.sendPrefixed("§eSomebody is digging §f" + var3 + "m §7away at §f" + var2.getX() + " " + var2.getY() + " " + var2.getZ());
      if (this.bool("sound")) {
         try {
            var1.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.9F);
         } catch (Throwable var5) {
         }
      }
   }

   @Override
   public void tick() {
      long var1 = this.pendingPos;
      if (var1 != Long.MIN_VALUE) {
         this.pendingPos = Long.MIN_VALUE;
         if (this.bool("waypoint")) {
            Minecraft var3 = Minecraft.getInstance();
            if (var3 != null && var3.level != null) {
               BlockPos var4 = BlockPos.of(var1);
               RiptideWaypoints.get()
                  .add(
                     RiptideWaypoints.scopeKey(var3),
                     new RiptideWaypoints.Waypoint(
                        "Break " + var4.getX() + " " + var4.getZ(), var4.getX(), var4.getY(), var4.getZ(), -11205, System.currentTimeMillis(), false
                     )
                  );
            }
         }
      }
   }
}
