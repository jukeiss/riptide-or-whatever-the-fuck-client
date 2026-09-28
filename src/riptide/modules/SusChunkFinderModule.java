package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWorldGeometry;

public final class SusChunkFinderModule extends Module {
   private static final int ACTIVITY = 8;
   private static final int STORAGE = 16;
   private static final int CUSTOM = 32;
   private static final int DEEP = 64;
   private static final int SIGNAL = 128;
   private static final String REDSTONE_IDS = "minecraft:dispenser|minecraft:dropper|minecraft:hopper|minecraft:piston|minecraft:sticky_piston|minecraft:observer|minecraft:redstone_block|minecraft:repeater|minecraft:comparator|minecraft:note_block|minecraft:crafter";
   private static final String NATURAL_IDS = "minecraft:spawner|minecraft:trial_spawner|minecraft:vault|minecraft:sculk_shrieker|minecraft:sculk_catalyst";
   private static final String DEFAULT_STORAGE = "minecraft:chest|minecraft:trapped_chest|minecraft:barrel|minecraft:hopper|minecraft:shulker_box|minecraft:white_shulker_box|minecraft:orange_shulker_box|minecraft:magenta_shulker_box|minecraft:light_blue_shulker_box|minecraft:yellow_shulker_box|minecraft:lime_shulker_box|minecraft:pink_shulker_box|minecraft:gray_shulker_box|minecraft:light_gray_shulker_box|minecraft:cyan_shulker_box|minecraft:purple_shulker_box|minecraft:blue_shulker_box|minecraft:brown_shulker_box|minecraft:green_shulker_box|minecraft:red_shulker_box|minecraft:black_shulker_box";
   private static final String DEFAULT_CUSTOM = "minecraft:respawn_anchor|minecraft:beacon";
   private static final long RESCAN_NANOS = 30000000000L;
   private static volatile boolean renderHookInstalled;
   private static SusChunkFinderModule cachedInstance;
   private final Map<Long, SusChunkFinderModule.Found> found = new ConcurrentHashMap<>();
   private final Map<Long, Long> scannedAt = new ConcurrentHashMap<>();
   private int radius = 12;
   private String storageRaw;
   private Set<Block> storageBlocks = Set.of();
   private String customRaw;
   private Set<Block> customBlocks = Set.of();
   private Set<Block> strongStorage = Set.of();
   private Set<Block> redstoneBlocks = null;
   private Set<Block> deepBlocks = Set.of();
   private Set<Block> naturalBlocks = null;
   private static final int SIG_ROTATED = 0;
   private static final int SIG_STONE = 1;
   private static final int SIG_AMETHYST = 2;
   private static final int SIG_BUDDING = 3;
   private static final int SIG_KELP = 4;
   private static final int SIG_CAVE_VINES = 5;
   private static final int SIG_VINES = 6;
   private static final int SIG_BAMBOO = 7;
   private static final int SIG_COCOA = 8;
   private static final int SIG_CANE = 9;
   private static final int SIG_BEE = 10;
   private static final int SIG_CRAFTED = 11;
   private static final int SIG_COUNT = 12;

   public SusChunkFinderModule() {
      super("sus-chunk-finder", "SusChunkFinder", ModuleCategory.RENDER, "Highlights chunks with signs of players nearby.");
      this.add(
         new BoolSetting("storage", "Storage", true)
            .group("Storage")
            .description("Flag chunks with a lot of chests, barrels, shulkers and other storage.")
            .build()
      );
      this.add(
         new IntSetting("storage-count", "Common Storage Needed", 10, 1, 100, 1)
            .group("Storage")
            .description(
               "How many common storage blocks (chests, barrels, hoppers) in one chunk before flagging. Higher = fewer false alarms from villages and shipwrecks."
            )
            .build()
      );
      this.add(
         new IntSetting("strong-count", "Rare Storage Needed", 3, 1, 50, 1)
            .group("Storage")
            .description("Shulker boxes or ender chests needed to flag. These never generate naturally, so even a few means a player.")
            .build()
      );
      this.add(
         RegistryListSetting.blocks(
               "storage-blocks",
               "Storage Blocks",
               "minecraft:chest|minecraft:trapped_chest|minecraft:barrel|minecraft:hopper|minecraft:shulker_box|minecraft:white_shulker_box|minecraft:orange_shulker_box|minecraft:magenta_shulker_box|minecraft:light_blue_shulker_box|minecraft:yellow_shulker_box|minecraft:lime_shulker_box|minecraft:pink_shulker_box|minecraft:gray_shulker_box|minecraft:light_gray_shulker_box|minecraft:cyan_shulker_box|minecraft:purple_shulker_box|minecraft:blue_shulker_box|minecraft:brown_shulker_box|minecraft:green_shulker_box|minecraft:red_shulker_box|minecraft:black_shulker_box"
            )
            .group("Storage")
            .description("Which blocks count as storage. Add or remove any block.")
            .build()
      );
      this.add(new BoolSetting("custom", "Custom Blocks", false).group("Custom Blocks").description("Flag chunks with your own list of blocks.").build());
      this.add(
         new IntSetting("custom-count", "Custom Needed", 2, 1, 200, 1)
            .group("Custom Blocks")
            .description("How many of your blocks in one chunk before it's flagged.")
            .build()
      );
      this.add(
         RegistryListSetting.blocks("custom-blocks", "Blocks", "minecraft:respawn_anchor|minecraft:beacon")
            .group("Custom Blocks")
            .description("Any blocks you want counted, e.g. spawners, beds, crafting tables.")
            .build()
      );
      this.add(
         new BoolSetting("deep-base", "Deep Base", true)
            .group("Deep Base")
            .description("Flag a cluster of storage or redstone (dispensers, pistons, droppers...) found deep underground.")
            .build()
      );
      this.add(
         new IntSetting("deep-y", "Below Y", -1, -64, 64, 1)
            .group("Deep Base")
            .description("Only count storage/redstone at or below this Y. Real underground bases sit under Y-1.")
            .build()
      );
      this.add(
         new IntSetting("deep-count", "Blocks Needed", 10, 1, 200, 1)
            .group("Deep Base")
            .description("How many storage/redstone blocks below the Y before flagging.")
            .build()
      );
      this.add(
         new BoolSetting("skip-natural", "Ignore Structures", true)
            .group("Deep Base")
            .description(
               "Don't flag dungeons, mineshafts, ancient cities and trial chambers (spawners, sculk shriekers, vaults). This kills most false positives."
            )
            .build()
      );
      this.add(
         new BoolSetting("signals", "Signals", true)
            .group("Signals")
            .description("Flag chunks by tell-tale blocks a player leaves behind, not just storage counts.")
            .build()
      );
      this.add(
         new IntSetting("sensitivity", "Sensitivity", 3, 1, 20, 1)
            .group("Signals")
            .description("Higher values flag weaker chunks. At 3 one strong signal is enough; past 5 farms alone will flag.")
            .build()
      );
      this.add(
         new BoolSetting("smart-adjustment", "Smart Adjustment", false)
            .group("Signals")
            .description("Drops the score needed by one, for more aggressive detection.")
            .build()
      );
      this.add(
         new IntSetting("farm-count", "Farm Size", 24, 4, 200, 2)
            .group("Signals")
            .description("How many of one crop (kelp, bamboo, cane...) in a chunk counts as a farm.")
            .build()
      );
      this.add(
         new BoolSetting("sig-rotated", "Rotated Deepslate", true)
            .group("Signals")
            .description("Natural deepslate always lies on the Y axis. Any other rotation was placed by a player.")
            .build()
      );
      this.add(
         new BoolSetting("sig-stone", "Stone Down Deep", true)
            .group("Signals")
            .description("Stone or cobble below Y-8, where only deepslate generates. Strong sign of a built base.")
            .build()
      );
      this.add(
         new BoolSetting("sig-amethyst", "Loose Amethyst", true)
            .group("Signals")
            .description("Amethyst with no budding amethyst nearby. Geodes always keep their buds, so this was carried in.")
            .build()
      );
      this.add(
         new BoolSetting("sig-crafted", "Crafters & Blocks", true)
            .group("Signals")
            .description(
               "Crafters, note blocks, observers and iron/diamond/emerald/netherite/redstone blocks. None of these generate in the world, so someone crafted and placed them."
            )
            .build()
      );
      this.add(new BoolSetting("sig-kelp", "Kelp", true).group("Signals").build());
      this.add(new BoolSetting("sig-cave-vines", "Cave Vines", true).group("Signals").build());
      this.add(new BoolSetting("sig-vines", "Vines", true).group("Signals").build());
      this.add(new BoolSetting("sig-bamboo", "Bamboo", true).group("Signals").build());
      this.add(new BoolSetting("sig-cocoa", "Cocoa", true).group("Signals").build());
      this.add(new BoolSetting("sig-cane", "Sugar Cane", true).group("Signals").build());
      this.add(new BoolSetting("sig-bee", "Bee Nests", true).group("Signals").build());
      this.add(
         new BoolSetting("block-updates", "Block Updates", false)
            .group("Detections")
            .description("Flag chunks where the server sends block changes you didn't make.")
            .build()
      );
      this.add(
         new IntSetting("ignore-radius", "Ignore Radius", 24, 0, 128, 1).group("Detections").description("Ignore block updates this close to you.").build()
      );
      this.add(
         new IntSetting("fade-seconds", "Update Fade", 60, 0, 600, 5).group("Detections").description("Seconds a block-update flag stays. 0 keeps it.").build()
      );
      this.add(
         new ChoiceSetting("style", "Style", "Krypton", "Krypton", "Detailed")
            .description("Krypton: one flat red sheet. Detailed: outlined and colored per detection.")
            .build()
      );
      this.add(new ColorSetting("flat-color", "Flat Color", -1497312).group("Colors").description("The single color used by the flat sheet style.").build());
      this.add(
         new ChoiceSetting("height", "Sheet Height", "Bottom", "Bottom", "Fixed", "Player")
            .description("Bottom draws it on the world floor so it reads from anywhere; Fixed uses Sheet Y; Player follows your feet.")
            .build()
      );
      this.add(new IntSetting("plane-y", "Sheet Y", 63, -64, 320, 1).description("Y level of the square in Fixed mode.").build());
      this.add(new IntSetting("fill-alpha", "Fill Opacity", 150, 0, 255, 5).description("How solid the square's fill is.").build());
      this.add(new IntSetting("radius", "Radius", 12, 2, 32, 1).description("Chunk radius scanned and drawn around you.").build());
      this.add(new BoolSetting("notify", "Notify", true).description("Chat message when a new chunk is flagged.").build());
      this.add(new ColorSetting("color-storage", "Storage", -11745).group("Colors").build());
      this.add(new ColorSetting("color-custom", "Custom Blocks", -14690049).group("Colors").build());
      this.add(new ColorSetting("color-deep", "Deep Base", -38369).group("Colors").build());
      this.add(new ColorSetting("color-signal", "Signals", -1497312).group("Colors").build());
      this.add(new ColorSetting("color-activity", "Block Updates", -4961281).group("Colors").build());
   }

   public static void initialize() {
      installRenderHook();
   }

   @Override
   public String info() {
      return Integer.toString(this.found.size());
   }

   @Override
   public void onEnable() {
      installRenderHook();
      this.found.clear();
      this.scannedAt.clear();
   }

   @Override
   public void onGameLeft() {
      this.found.clear();
      this.scannedAt.clear();
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if (var1.startsWith("storage")
         || var1.startsWith("custom")
         || var1.startsWith("deep")
         || var1.startsWith("sig-")
         || var1.equals("signals")
         || var1.equals("sensitivity")
         || var1.equals("farm-count")
         || var1.equals("smart-adjustment")
         || var1.equals("skip-natural")
         || var1.equals("strong-count")) {
         this.found.clear();
         this.scannedAt.clear();
      }
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         this.radius = this.integer("radius");
         int var1 = MC.player.blockPosition().getX() >> 4;
         int var2 = MC.player.blockPosition().getZ() >> 4;
         long var3 = System.nanoTime();
         int var5 = 5;

         for (int var6 = 0; var6 <= this.radius && var5 > 0; var6++) {
            for (int var7 = var1 - var6; var7 <= var1 + var6 && var5 > 0; var7++) {
               for (int var8 = var2 - var6; var8 <= var2 + var6 && var5 > 0; var8++) {
                  if (Math.max(Math.abs(var7 - var1), Math.abs(var8 - var2)) == var6) {
                     long var9 = key(var7, var8);
                     Long var11 = this.scannedAt.get(var9);
                     if (var11 == null || var3 - var11 >= 30000000000L) {
                        LevelChunk var12 = MC.level.getChunkSource().getChunk(var7, var8, false);
                        if (var12 != null) {
                           this.scannedAt.put(var9, var3);
                           this.scan(var12, var7, var8);
                           var5--;
                        }
                     }
                  }
               }
            }
         }

         this.prune(var1, var2, var3);
      }
   }

   private void scan(LevelChunk var1, int var2, int var3) {
      boolean var4 = this.bool("custom");
      this.refreshBlockLists();
      Set var5 = this.customBlocks;
      var4 = var4 && !var5.isEmpty();
      boolean var6 = this.bool("deep-base") && !this.deepBlocks.isEmpty();
      int var7 = this.integer("deep-y");
      short var8 = 0;
      int var9 = 0;
      int var10 = 0;
      int var11 = 0;
      int var12 = 0;
      boolean var13 = this.bool("skip-natural");
      boolean var14 = false;

      for (BlockEntity var16 : var1.getBlockEntities().values()) {
         Block var17 = var16.getBlockState().getBlock();
         if (var13 && this.naturalBlocks != null && this.naturalBlocks.contains(var17)) {
            var14 = true;
         }

         if (this.strongStorage.contains(var17)) {
            var11++;
         } else if (this.storageBlocks.contains(var17)) {
            var10++;
         }
      }

      if (this.bool("storage") && !this.storageBlocks.isEmpty() && !var14 && (var11 >= this.integer("strong-count") || var10 >= this.integer("storage-count"))) {
         var8 |= 16;
      }

      Object var35 = null;
      boolean var36 = this.bool("signals") && !var14;
      int[] var37 = new int[12];
      LevelChunkSection[] var18 = var1.getSections();
      int var19 = MC.level.getMinY();

      for (int var20 = 0; var20 < var18.length; var20++) {
         LevelChunkSection var21 = var18[var20];
         if (var21 != null && !var21.hasOnlyAir()) {
            int var22 = var19 + (var20 << 4);
            boolean var23 = var22 <= -8;
            boolean var24 = var22 <= 16;
            boolean var25 = var4 && var21.maybeHas(var1x -> var5.contains(var1x.getBlock()));
            boolean var26 = var6 && var22 <= var7 && var21.maybeHas(var1x -> this.deepBlocks.contains(var1x.getBlock()));
            boolean var27 = var36 && var21.maybeHas(var3x -> this.isSignalBlock(var3x, var24, var23));
            if (var25 || var26 || var27) {
               for (int var28 = 0; var28 < 16; var28++) {
                  int var29 = var22 + var28;

                  for (int var30 = 0; var30 < 16; var30++) {
                     for (int var31 = 0; var31 < 16; var31++) {
                        BlockState var32 = var21.getBlockState(var30, var28, var31);
                        Block var33 = var32.getBlock();
                        if (var25 && var5.contains(var33)) {
                           var9++;
                        }

                        if (var26 && var29 <= var7 && this.deepBlocks.contains(var33)) {
                           var12++;
                        }

                        if (var27) {
                           this.countSignal(var32, var33, var24, var23, var37);
                        }
                     }
                  }
               }
            }
         }
      }

      int var38 = var36 ? this.score(var37) : 0;
      int var39 = Math.max(1, 8 - this.integer("sensitivity") - (this.bool("smart-adjustment") ? 1 : 0));
      if (var36 && var38 >= var39) {
         var8 |= 128;
      }

      if (var6 && !var14 && var12 >= this.integer("deep-count")) {
         var8 |= 64;
      }

      if (var4 && var9 >= this.integer("custom-count")) {
         var8 |= 32;
      }

      long var40 = key(var2, var3);
      SusChunkFinderModule.Found var41 = this.found.get(var40);
      int var42 = var41 == null ? 0 : var41.reasons() & 8;
      int var43 = var8 | var42;
      if (var43 == 0) {
         this.found.remove(var40);
      } else {
         this.found.put(var40, new SusChunkFinderModule.Found(var2, var3, var43, var41 == null ? 0L : var41.activityAt()));
         int var44 = var8 & ~(var41 == null ? 0 : var41.reasons());
         if (var44 != 0 && this.bool("notify")) {
            int var45 = var35 != null ? var35.getX() : (var2 << 4) + 8;
            int var46 = var35 != null ? var35.getZ() : (var3 << 4) + 8;
            String var47 = describe(var44, var10, var11, var9, var12);
            if ((var44 & 128) != 0) {
               String var48 = describeSignals(var37);
               if (!var48.isEmpty()) {
                  var47 = var47.isEmpty() ? var48 : var47 + ", " + var48;
               }
            }

            RiptideClientMessaging.sendPrefixed("§eSus chunk §f" + var45 + ", " + var46 + " §7(" + var47 + ")");
         }
      }
   }

   private static boolean isCrafted(Block var0) {
      return var0 == Blocks.CRAFTER
         || var0 == Blocks.NOTE_BLOCK
         || var0 == Blocks.OBSERVER
         || var0 == Blocks.REDSTONE_BLOCK
         || var0 == Blocks.IRON_BLOCK
         || var0 == Blocks.DIAMOND_BLOCK
         || var0 == Blocks.EMERALD_BLOCK
         || var0 == Blocks.NETHERITE_BLOCK;
   }

   private boolean isSignalBlock(BlockState var1, boolean var2, boolean var3) {
      Block var4 = var1.getBlock();
      if (var2 && var4 == Blocks.DEEPSLATE) {
         return true;
      } else {
         return !var3 || var4 != Blocks.STONE && var4 != Blocks.COBBLESTONE
            ? isCrafted(var4)
               || var4 == Blocks.AMETHYST_BLOCK
               || var4 == Blocks.AMETHYST_CLUSTER
               || var4 == Blocks.BUDDING_AMETHYST
               || var4 == Blocks.KELP
               || var4 == Blocks.KELP_PLANT
               || var4 == Blocks.CAVE_VINES
               || var4 == Blocks.CAVE_VINES_PLANT
               || var4 == Blocks.VINE
               || var4 == Blocks.BAMBOO
               || var4 == Blocks.COCOA
               || var4 == Blocks.SUGAR_CANE
               || var4 == Blocks.BEE_NEST
            : true;
      }
   }

   private void countSignal(BlockState var1, Block var2, boolean var3, boolean var4, int[] var5) {
      if (var3 && var2 == Blocks.DEEPSLATE) {
         try {
            if (var1.getValue(RotatedPillarBlock.AXIS) != Axis.Y) {
               var5[0]++;
            }
         } catch (Throwable var7) {
         }
      } else {
         if (isCrafted(var2)) {
            var5[11]++;
         } else if (!var4 || var2 != Blocks.STONE && var2 != Blocks.COBBLESTONE) {
            if (var2 == Blocks.AMETHYST_BLOCK || var2 == Blocks.AMETHYST_CLUSTER) {
               var5[2]++;
            } else if (var2 == Blocks.BUDDING_AMETHYST) {
               var5[3]++;
            } else if (var2 == Blocks.KELP || var2 == Blocks.KELP_PLANT) {
               var5[4]++;
            } else if (var2 == Blocks.CAVE_VINES || var2 == Blocks.CAVE_VINES_PLANT) {
               var5[5]++;
            } else if (var2 == Blocks.VINE) {
               var5[6]++;
            } else if (var2 == Blocks.BAMBOO) {
               var5[7]++;
            } else if (var2 == Blocks.COCOA) {
               var5[8]++;
            } else if (var2 == Blocks.SUGAR_CANE) {
               var5[9]++;
            } else if (var2 == Blocks.BEE_NEST) {
               var5[10]++;
            }
         } else {
            var5[1]++;
         }
      }
   }

   private int score(int[] var1) {
      int var2 = this.integer("farm-count");
      int var3 = 0;
      if (this.bool("sig-rotated") && var1[0] > 0) {
         var3 += 5;
      }

      if (this.bool("sig-crafted") && var1[11] > 0) {
         var3 += var1[11] >= 2 ? 5 : 3;
      }

      if (this.bool("sig-stone") && var1[1] >= 32) {
         var3 += 3;
      }

      if (this.bool("sig-amethyst") && var1[2] >= 4 && var1[3] == 0) {
         var3 += 3;
      }

      if (this.bool("sig-kelp") && var1[4] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-cave-vines") && var1[5] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-vines") && var1[6] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-bamboo") && var1[7] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-cocoa") && var1[8] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-cane") && var1[9] >= var2) {
         var3 += 2;
      }

      if (this.bool("sig-bee") && var1[10] >= 2) {
         var3++;
      }

      return var3;
   }

   private static String describeSignals(int[] var0) {
      StringBuilder var1 = new StringBuilder();
      appendSignal(var1, "crafted blocks", var0[11]);
      appendSignal(var1, "rotated deepslate", var0[0]);
      appendSignal(var1, "deep stone", var0[1]);
      appendSignal(var1, "amethyst", var0[2]);
      appendSignal(var1, "kelp", var0[4]);
      appendSignal(var1, "cave vines", var0[5]);
      appendSignal(var1, "vines", var0[6]);
      appendSignal(var1, "bamboo", var0[7]);
      appendSignal(var1, "cocoa", var0[8]);
      appendSignal(var1, "cane", var0[9]);
      appendSignal(var1, "bee nests", var0[10]);
      return var1.toString();
   }

   private static void appendSignal(StringBuilder var0, String var1, int var2) {
      if (var2 > 0) {
         if (!var0.isEmpty()) {
            var0.append(", ");
         }

         var0.append(var2).append(' ').append(var1);
      }
   }

   private static String describe(int var0, int var1, int var2, int var3, int var4) {
      StringBuilder var5 = new StringBuilder();
      if ((var0 & 16) != 0) {
         var5.append(var1 + var2).append(" storage");
      }

      if ((var0 & 32) != 0) {
         var5.append(var5.isEmpty() ? "" : ", ").append(var3).append(" custom blocks");
      }

      if ((var0 & 64) != 0) {
         var5.append(var5.isEmpty() ? "" : ", ").append(var4).append(" deep");
      }

      return var5.toString();
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (this.isEnabled() && this.bool("block-updates") && MC.player != null) {
         if (var1 instanceof ClientboundBlockUpdatePacket var2) {
            this.activity(var2.getPos());
         } else if (var1 instanceof ClientboundSectionBlocksUpdatePacket var3) {
            BlockPos[] var4 = new BlockPos[1];
            var3.runUpdates((var1x, var2x) -> {
               if (var4[0] == null) {
                  var4[0] = var1x.immutable();
               }
            });
            if (var4[0] != null) {
               this.activity(var4[0]);
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void activity(BlockPos var1) {
      int var2 = this.integer("ignore-radius");
      if (!(MC.player.blockPosition().distSqr(var1) <= (double)var2 * var2)) {
         int var3 = var1.getX() >> 4;
         int var4 = var1.getZ() >> 4;
         long var5 = key(var3, var4);
         SusChunkFinderModule.Found var7 = this.found.get(var5);
         int var8 = (var7 == null ? 0 : var7.reasons()) | 8;
         this.found.put(var5, new SusChunkFinderModule.Found(var3, var4, var8, System.nanoTime()));
      }
   }

   private void prune(int var1, int var2, long var3) {
      int var5 = this.radius + 4;
      long var6 = this.integer("fade-seconds") * 1000000000L;

      for (Entry var9 : this.found.entrySet()) {
         SusChunkFinderModule.Found var10 = (SusChunkFinderModule.Found)var9.getValue();
         if (Math.abs(var10.x() - var1) > var5 || Math.abs(var10.z() - var2) > var5) {
            this.found.remove(var9.getKey());
            this.scannedAt.remove(var9.getKey());
         } else if (var6 > 0L && (var10.reasons() & 8) != 0 && var3 - var10.activityAt() > var6) {
            int var11 = var10.reasons() & -9;
            if (var11 == 0) {
               this.found.remove(var9.getKey());
            } else {
               this.found.put((Long)var9.getKey(), new SusChunkFinderModule.Found(var10.x(), var10.z(), var11, 0L));
            }
         }
      }

      this.scannedAt.keySet().removeIf(var3x -> Math.abs(keyX(var3x) - var1) > var5 || Math.abs(keyZ(var3x) - var2) > var5);
   }

   private static long key(int var0, int var1) {
      return var0 & 4294967295L | (long)var1 << 32;
   }

   private static int keyX(long var0) {
      return (int)var0;
   }

   private static int keyZ(long var0) {
      return (int)(var0 >>> 32);
   }

   private void refreshBlockLists() {
      String var1 = String.join("|", this.list("storage-blocks"));
      if (!var1.equals(this.storageRaw)) {
         this.storageRaw = var1;
         this.storageBlocks = resolve(this.list("storage-blocks"));
         HashSet var2 = new HashSet();

         for (Block var4 : this.storageBlocks) {
            String var5 = BuiltInRegistries.BLOCK.getKey(var4).getPath();
            if (var5.endsWith("shulker_box") || var5.equals("ender_chest")) {
               var2.add(var4);
            }
         }

         this.strongStorage = Set.copyOf(var2);
         this.rebuildDeep();
      }

      if (this.redstoneBlocks == null) {
         this.redstoneBlocks = resolve(
            List.of(
               "minecraft:dispenser|minecraft:dropper|minecraft:hopper|minecraft:piston|minecraft:sticky_piston|minecraft:observer|minecraft:redstone_block|minecraft:repeater|minecraft:comparator|minecraft:note_block|minecraft:crafter"
                  .split("\\|")
            )
         );
         this.rebuildDeep();
      }

      if (this.naturalBlocks == null) {
         this.naturalBlocks = resolve(
            List.of("minecraft:spawner|minecraft:trial_spawner|minecraft:vault|minecraft:sculk_shrieker|minecraft:sculk_catalyst".split("\\|"))
         );
      }

      String var6 = String.join("|", this.list("custom-blocks"));
      if (!var6.equals(this.customRaw)) {
         this.customRaw = var6;
         this.customBlocks = resolve(this.list("custom-blocks"));
      }
   }

   private void rebuildDeep() {
      if (this.redstoneBlocks != null) {
         HashSet var1 = new HashSet<>(this.storageBlocks);
         var1.addAll(this.redstoneBlocks);
         this.deepBlocks = Set.copyOf(var1);
      }
   }

   private static Set<Block> resolve(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         String var4 = var3.trim();
         if (!var4.isEmpty()) {
            Identifier var5 = Identifier.tryParse(var4.contains(":") ? var4 : "minecraft:" + var4);
            if (var5 != null) {
               BuiltInRegistries.BLOCK.getOptional(var5).filter(var0x -> var0x != Blocks.AIR).ifPresent(var1::add);
            }
         }
      }

      return Set.copyOf(var1);
   }

   private int colorFor(int var1) {
      if ((var1 & 128) != 0) {
         return ModuleRenderUtil.color(this, "color-signal", -1497312);
      } else if ((var1 & 16) != 0) {
         return ModuleRenderUtil.color(this, "color-storage", -11745);
      } else if ((var1 & 64) != 0) {
         return ModuleRenderUtil.color(this, "color-deep", -38369);
      } else {
         return (var1 & 32) != 0 ? ModuleRenderUtil.color(this, "color-custom", -14690049) : ModuleRenderUtil.color(this, "color-activity", -4961281);
      }
   }

   private static SusChunkFinderModule instance() {
      SusChunkFinderModule var0 = cachedInstance;
      if (var0 == null && ModuleRegistry.get("sus-chunk-finder") instanceof SusChunkFinderModule var1) {
         var0 = var1;
         cachedInstance = var1;
      }

      return var0;
   }

   private static synchronized void installRenderHook() {
      if (!renderHookInstalled) {
         renderHookInstalled = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     SusChunkFinderModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.found.isEmpty()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     Vec3 var2 = var0.levelState().cameraRenderState.pos;
                     var0.submitNodeCollector()
                        .submitCustomGeometry(
                           var0.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var2, true)
                        );
                     var0.submitNodeCollector()
                        .submitCustomGeometry(
                           var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var2, false)
                        );
                  } catch (Throwable var3) {
                  }
               }
            );
      }
   }

   private void emit(Pose var1, VertexConsumer var2, Vec3 var3, boolean var4) {
      String var5 = this.choice("height");
      double var6;
      if ("Player".equals(var5)) {
         var6 = Math.floor(MC.player.getY());
      } else if ("Bottom".equals(var5)) {
         var6 = MC.level.getMinY();
      } else {
         var6 = this.integer("plane-y");
      }

      int var8 = this.integer("fill-alpha");
      boolean var9 = "Krypton".equals(this.choice("style"));
      int var10 = ModuleRenderUtil.color(this, "flat-color", -54742) | 0xFF000000;

      for (SusChunkFinderModule.Found var12 : this.found.values()) {
         int var13 = var9 ? var10 : this.colorFor(var12.reasons()) | 0xFF000000;
         double var14 = (var12.x() << 4) - var3.x;
         double var16 = var14 + 16.0;
         double var18 = (var12.z() << 4) - var3.z;
         double var20 = var18 + 16.0;
         double var22 = var6 + 0.02 - var3.y;
         if (var4) {
            if (var8 > 0) {
               int var24 = var8 << 24 | var13 & 16777215;
               quad(var1, var2, var14, var22, var18, var14, var22, var20, var16, var22, var20, var16, var22, var18, var24);
               quad(var1, var2, var14, var22, var18, var16, var22, var18, var16, var22, var20, var14, var22, var20, var24);
            }
         } else if (!var9) {
            square(var1, var2, var14, var16, var18, var20, var22, var13);
         }
      }
   }

   private static void square(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, int var12) {
      RiptideWorldGeometry.line(var0, var1, var2, var10, var6, var4, var10, var6, var12, 2.0F);
      RiptideWorldGeometry.line(var0, var1, var4, var10, var6, var4, var10, var8, var12, 2.0F);
      RiptideWorldGeometry.line(var0, var1, var4, var10, var8, var2, var10, var8, var12, 2.0F);
      RiptideWorldGeometry.line(var0, var1, var2, var10, var8, var2, var10, var6, var12, 2.0F);
   }

   private static void quad(
      Pose var0,
      VertexConsumer var1,
      double var2,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      double var20,
      double var22,
      double var24,
      int var26
   ) {
      var1.addVertex(var0, (float)var2, (float)var4, (float)var6).setColor(var26);
      var1.addVertex(var0, (float)var8, (float)var10, (float)var12).setColor(var26);
      var1.addVertex(var0, (float)var14, (float)var16, (float)var18).setColor(var26);
      var1.addVertex(var0, (float)var20, (float)var22, (float)var24).setColor(var26);
   }

   private record Found(int x, int z, int reasons, long activityAt) {
   }
}
