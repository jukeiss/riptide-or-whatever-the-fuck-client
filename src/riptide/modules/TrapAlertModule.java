package riptide.modules;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class TrapAlertModule extends Module {
   private static final long ALERT_INTERVAL_MS = 1500L;
   private final AtomicLong nextAlertMs = new AtomicLong();

   public TrapAlertModule() {
      super("trap-alert", "TrapAlert", ModuleCategory.COMBAT, "Warns you when trap blocks appear next to you.");
      this.add(
         new IntSetting("range", "Range", 4, 1, 12, 1)
            .unit("blocks")
            .description("How close a block has to appear to count as being about you.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("obsidian", "Obsidian", true).description("Obsidian, crying obsidian and bedrock.").group("Blocks").build());
      this.add(new BoolSetting("webs", "Cobwebs", true).description("Cobwebs, which are the usual way of holding somebody still.").group("Blocks").build());
      this.add(
         new BoolSetting("anchors", "Anchors And Beds", true)
            .description("Respawn anchors and beds, which are placed to be detonated.")
            .group("Blocks")
            .build()
      );
      this.add(new BoolSetting("lava", "Lava", true).description("Lava appearing beside you.").group("Blocks").build());
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping as well as printing the warning.").group("Alerts").build());
   }

   @Override
   public void onEnable() {
      this.nextAlertMs.set(0L);
   }

   @Override
   public void onGameJoin() {
      this.nextAlertMs.set(0L);
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundBlockUpdatePacket var2) {
         Minecraft var3 = Minecraft.getInstance();
         if (var3 == null || var3.player == null) {
            return false;
         } else if (!this.isTrapBlock(var2.getBlockState())) {
            return false;
         } else {
            BlockPos var4 = var2.getPos();
            int var5 = this.integer("range");
            double var6 = var4.getX() + 0.5 - var3.player.getX();
            double var8 = var4.getY() + 0.5 - var3.player.getY();
            double var10 = var4.getZ() + 0.5 - var3.player.getZ();
            if (var6 * var6 + var8 * var8 + var10 * var10 > (double)var5 * var5) {
               return false;
            } else {
               long var12 = System.currentTimeMillis();
               long var14 = this.nextAlertMs.get();
               if (var12 >= var14 && this.nextAlertMs.compareAndSet(var14, var12 + 1500L)) {
                  this.alert(var3, var2.getBlockState(), var4);
                  return false;
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   private void alert(Minecraft var1, BlockState var2, BlockPos var3) {
      String var4 = var2.getBlock().getName().getString();
      RiptideClientMessaging.sendPrefixed("§c" + var4 + " §7placed beside you at §f" + var3.getX() + " " + var3.getY() + " " + var3.getZ());
      if (this.bool("sound")) {
         try {
            var1.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.7F);
         } catch (Throwable var6) {
         }
      }
   }

   private boolean isTrapBlock(BlockState var1) {
      if (var1 != null && !var1.isAir()) {
         if (!this.bool("obsidian") || !var1.is(Blocks.OBSIDIAN) && !var1.is(Blocks.CRYING_OBSIDIAN) && !var1.is(Blocks.BEDROCK)) {
            if (this.bool("webs") && var1.is(Blocks.COBWEB)) {
               return true;
            } else {
               return !this.bool("anchors") || !var1.is(Blocks.RESPAWN_ANCHOR) && !(var1.getBlock() instanceof BedBlock)
                  ? this.bool("lava") && var1.is(Blocks.LAVA)
                  : true;
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }
}
