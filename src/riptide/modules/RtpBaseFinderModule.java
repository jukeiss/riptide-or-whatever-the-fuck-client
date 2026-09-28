package riptide.modules;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class RtpBaseFinderModule extends Module {
   private static final Set<Block> BASE_BLOCKS = Set.of(
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.ENDER_CHEST,
      Blocks.BARREL,
      Blocks.SHULKER_BOX,
      Blocks.HOPPER,
      Blocks.CRAFTING_TABLE,
      Blocks.FURNACE,
      Blocks.BLAST_FURNACE,
      Blocks.SMOKER,
      Blocks.ANVIL,
      Blocks.ENCHANTING_TABLE,
      Blocks.BREWING_STAND,
      Blocks.BEACON,
      Blocks.OBSIDIAN,
      Blocks.CRYING_OBSIDIAN,
      Blocks.RESPAWN_ANCHOR,
      Blocks.NETHERITE_BLOCK,
      Blocks.DIAMOND_BLOCK,
      Blocks.EMERALD_BLOCK,
      Blocks.GOLD_BLOCK,
      Blocks.IRON_BLOCK,
      Blocks.GLASS,
      Blocks.SEA_LANTERN,
      Blocks.REDSTONE_BLOCK,
      Blocks.PISTON,
      Blocks.STICKY_PISTON,
      Blocks.OBSERVER,
      Blocks.REPEATER,
      Blocks.COMPARATOR,
      Blocks.TNT,
      Blocks.LODESTONE
   );
   private final Set<Long> scanned = new HashSet<>();
   private final Set<Long> reported = new HashSet<>();
   private int scanCooldown;
   private int rtpCooldown;
   private int hitsThisSpot;
   private int totalHits;

   public RtpBaseFinderModule() {
      super("rtp-base-finder", "RTP Base Finder", ModuleCategory.MISC, "Scans chunks for player-built blocks.");
      this.add(
         new IntSetting("threshold", "Blocks Needed", 12, 1, 200, 1).description("How many player-made blocks in a chunk before it counts as a base.").build()
      );
      this.add(new IntSetting("radius", "Scan Radius", 6, 1, 16, 1).description("Chunk radius scanned around you.").build());
      this.add(new IntSetting("min-y", "Min Y", -60, -64, 320, 4).description("Ignore anything below this height.").group("Range").build());
      this.add(new IntSetting("max-y", "Max Y", 200, -64, 320, 4).description("Ignore anything above this height.").group("Range").build());
      this.add(new BoolSetting("waypoint", "Save Waypoint", true).description("Drop a waypoint on each base found.").build());
      this.add(new BoolSetting("auto-rtp", "Auto RTP", false).description("Send the command below until a base is found.").group("Auto RTP").build());
      this.add(new StringSetting("rtp-command", "RTP Command", "/rtp").description("Your server's random-teleport command.").group("Auto RTP").build());
      this.add(
         new IntSetting("rtp-delay", "RTP Delay", 8, 2, 120, 1)
            .description("Seconds to wait after teleporting before scanning again.")
            .group("Auto RTP")
            .build()
      );
   }

   @Override
   public String info() {
      return this.totalHits + " found";
   }

   @Override
   public void onEnable() {
      this.scanned.clear();
      this.reported.clear();
      this.scanCooldown = 20;
      this.rtpCooldown = 0;
      this.hitsThisSpot = 0;
      this.totalHits = 0;
   }

   @Override
   public void onGameLeft() {
      this.onEnable();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.level != null) {
         if (this.rtpCooldown > 0) {
            this.rtpCooldown--;
         } else if (this.scanCooldown > 0) {
            this.scanCooldown--;
         } else {
            this.scanCooldown = 10;
            this.scanAround();
            if (this.bool("auto-rtp") && this.hitsThisSpot == 0 && this.scannedEverythingNearby()) {
               String var1 = this.text("rtp-command");
               if (!var1.isBlank()) {
                  this.sendCommand(var1);
                  this.scanned.clear();
                  this.rtpCooldown = 20 * this.integer("rtp-delay");
               }
            }
         }
      }
   }

   private boolean scannedEverythingNearby() {
      int var1 = this.integer("radius");
      int var2 = MC.player.blockPosition().getX() >> 4;
      int var3 = MC.player.blockPosition().getZ() >> 4;

      for (int var4 = var2 - var1; var4 <= var2 + var1; var4++) {
         for (int var5 = var3 - var1; var5 <= var3 + var1; var5++) {
            if (MC.level.getChunkSource().getChunk(var4, var5, false) != null && !this.scanned.contains(key(var4, var5))) {
               return false;
            }
         }
      }

      return true;
   }

   private void scanAround() {
      int var1 = this.integer("radius");
      int var2 = MC.player.blockPosition().getX() >> 4;
      int var3 = MC.player.blockPosition().getZ() >> 4;
      int var4 = 4;

      for (int var5 = var2 - var1; var5 <= var2 + var1 && var4 > 0; var5++) {
         for (int var6 = var3 - var1; var6 <= var3 + var1 && var4 > 0; var6++) {
            long var7 = key(var5, var6);
            if (!this.scanned.contains(var7)) {
               LevelChunk var9 = MC.level.getChunkSource().getChunk(var5, var6, false);
               if (var9 != null) {
                  this.scanned.add(var7);
                  var4--;
                  int var10 = this.countChunk(var9);
                  if (var10 >= this.integer("threshold") && this.reported.add(var7)) {
                     this.report(var5, var6, var10);
                  }
               }
            }
         }
      }
   }

   private int countChunk(LevelChunk var1) {
      int var2 = Math.min(this.integer("min-y"), this.integer("max-y"));
      int var3 = Math.max(this.integer("min-y"), this.integer("max-y"));
      int var4 = 0;
      LevelChunkSection[] var5 = var1.getSections();
      int var6 = var1.getMinY();

      for (int var7 = 0; var7 < var5.length; var7++) {
         LevelChunkSection var8 = var5[var7];
         if (var8 != null && !var8.hasOnlyAir()) {
            int var9 = var6 + (var7 << 4);
            if (var9 + 15 >= var2 && var9 <= var3) {
               for (int var10 = 0; var10 < 16; var10++) {
                  int var11 = var9 + var10;
                  if (var11 >= var2 && var11 <= var3) {
                     for (int var12 = 0; var12 < 16; var12++) {
                        for (int var13 = 0; var13 < 16; var13++) {
                           BlockState var14 = var8.getBlockState(var12, var10, var13);
                           if (!var14.isAir() && BASE_BLOCKS.contains(var14.getBlock())) {
                              var4++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return var4;
   }

   private void report(int var1, int var2, int var3) {
      int var4 = (var1 << 4) + 8;
      int var5 = (var2 << 4) + 8;
      int var6 = MC.player.blockPosition().getY();
      this.hitsThisSpot++;
      this.totalHits++;
      RiptideClientMessaging.sendPrefixed("§aBase §7(" + var3 + " blocks) §fat §a" + var4 + "§f, §a" + var5);
      if (this.bool("waypoint")) {
         try {
            RiptideWaypoints.get()
               .add(
                  RiptideWaypoints.scopeKey(MC),
                  new RiptideWaypoints.Waypoint("Base " + var4 + "," + var5, var4, var6, var5, -11682836, System.currentTimeMillis())
               );
         } catch (Throwable var8) {
         }
      }
   }

   private static long key(int var0, int var1) {
      return (long)var0 << 32 ^ var1 & 4294967295L;
   }
}
