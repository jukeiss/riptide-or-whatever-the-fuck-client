package riptide.util.oresim;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RiptideOreSimOre {
   public static final List<String> ORE_SIM_BLOCK_IDS = List.of(
      "minecraft:coal_ore",
      "minecraft:deepslate_coal_ore",
      "minecraft:iron_ore",
      "minecraft:deepslate_iron_ore",
      "minecraft:gold_ore",
      "minecraft:deepslate_gold_ore",
      "minecraft:lapis_ore",
      "minecraft:deepslate_lapis_ore",
      "minecraft:redstone_ore",
      "minecraft:deepslate_redstone_ore",
      "minecraft:diamond_ore",
      "minecraft:deepslate_diamond_ore",
      "minecraft:emerald_ore",
      "minecraft:deepslate_emerald_ore",
      "minecraft:copper_ore",
      "minecraft:deepslate_copper_ore",
      "minecraft:raw_iron_block",
      "minecraft:raw_copper_block",
      "minecraft:nether_gold_ore",
      "minecraft:nether_quartz_ore",
      "minecraft:ancient_debris"
   );
   private static volatile Set<Block> oreSimBlocks;

   private RiptideOreSimOre() {
   }

   public static boolean isOreSimBlock(Block block) {
      if (block == null) {
         return false;
      } else {
         Set<Block> known = oreSimBlocks;
         if (known == null) {
            Set<Block> built = Collections.newSetFromMap(new IdentityHashMap<>());

            for (String id : ORE_SIM_BLOCK_IDS) {
               Identifier parsed = Identifier.tryParse(id);
               if (parsed != null) {
                  BuiltInRegistries.BLOCK.getOptional(parsed).ifPresent(built::add);
               }
            }

            known = built;
            oreSimBlocks = built;
         }

         return known.contains(block);
      }
   }

   public static RiptideOreSimOre.Kind familyOf(String blockId) {
      if (blockId == null) {
         return null;
      } else {
         String path = blockId.strip().toLowerCase(Locale.ROOT);
         int colon = path.indexOf(58);
         if (colon >= 0) {
            path = path.substring(colon + 1);
         }

         for (RiptideOreSimOre.Kind kind : RiptideOreSimOre.Kind.values()) {
            if (path.contains(kind.match)) {
               return kind;
            }
         }

         return null;
      }
   }

   public static enum Kind {
      COAL("coal", "Coal", -11645352, "coal"),
      IRON("iron", "Iron", -2579078, "iron"),
      GOLD("gold", "Gold", -204725, "gold"),
      REDSTONE("redstone", "Redstone", -50373, "redstone"),
      DIAMOND("diamond", "Diamond", -11869223, "diamond"),
      LAPIS("lapis", "Lapis", -12950816, "lapis"),
      COPPER("copper", "Copper", -2065590, "copper"),
      EMERALD("emerald", "Emerald", -13510293, "emerald"),
      QUARTZ("quartz", "Quartz", -1187110, "quartz"),
      DEBRIS("debris", "Ancient Debris", -4689959, "ancient_debris");

      public final String id;
      public final String label;
      public final int defaultColor;
      public final String match;

      private Kind(String id, String label, int defaultColor, String match) {
         this.id = id;
         this.label = label;
         this.defaultColor = defaultColor;
         this.match = match;
      }

      public String colorId() {
         return "ore-color-" + this.id;
      }
   }

   public static final class OreStates {
      private static final Map<BlockState, Integer> IDS = new IdentityHashMap<>();
      private static volatile BlockState[] states = new BlockState[0];
      private static volatile RiptideOreSimOre.Kind[] kinds = new RiptideOreSimOre.Kind[0];

      private OreStates() {
      }

      private static synchronized int intern(BlockState state, RiptideOreSimOre.Kind kind) {
         Integer existing = IDS.get(state);
         if (existing != null) {
            return existing;
         } else {
            int id = states.length;
            BlockState[] nextStates = Arrays.copyOf(states, id + 1);
            RiptideOreSimOre.Kind[] nextKinds = Arrays.copyOf(kinds, id + 1);
            nextStates[id] = state;
            nextKinds[id] = kind;
            IDS.put(state, id);
            kinds = nextKinds;
            states = nextStates;
            return id;
         }
      }

      public static BlockState state(int id) {
         BlockState[] snapshot = states;
         return id >= 0 && id < snapshot.length ? snapshot[id] : null;
      }

      public static RiptideOreSimOre.Kind kind(int id) {
         RiptideOreSimOre.Kind[] snapshot = kinds;
         return id >= 0 && id < snapshot.length ? snapshot[id] : null;
      }

      public static int count() {
         return states.length;
      }

      public static int internGenerated(BlockState state) {
         if (state != null && RiptideOreSimOre.isOreSimBlock(state.getBlock())) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            RiptideOreSimOre.Kind kind = id == null ? null : RiptideOreSimOre.familyOf(id.toString());
            return kind == null ? -1 : intern(state, kind);
         } else {
            return -1;
         }
      }
   }
}
