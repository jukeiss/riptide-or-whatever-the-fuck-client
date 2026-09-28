package riptide.modules;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class SlowPickaxeModule extends Module {
   private static SlowPickaxeModule cached;

   public SlowPickaxeModule() {
      super("slow-pickaxe", "Slow Pickaxe", ModuleCategory.RENDER, "Plays your swing in slow motion. Blocks still break at normal speed.");
      this.add(
         new IntSetting("speed", "Slowdown", 300, 110, 1200, 10)
            .description("How much longer the swing takes, as a percent. 300 means three times slower.")
            .build()
      );
      this.add(new BoolSetting("pickaxe-only", "Pickaxe Only", true).description("Only slow the swing while you're holding a pickaxe.").build());
      this.add(
         new BoolSetting("mining-only", "While Mining Only", false).description("Only slow it while you're actually holding the attack key down.").build()
      );
   }

   @Override
   public String info() {
      return String.format("%.1fx", this.integer("speed") / 100.0);
   }

   public static float multiplier() {
      SlowPickaxeModule var0 = instance();
      if (var0 != null && var0.isEnabled() && MC.player != null && !PackHideState.isActive()) {
         if (var0.bool("pickaxe-only")) {
            ItemStack var1 = MC.player.getMainHandItem();
            if (var1.isEmpty() || !var1.is(ItemTags.PICKAXES)) {
               return 1.0F;
            }
         }

         return !var0.bool("mining-only") || MC.options != null && MC.options.keyAttack.isDown() ? Math.max(1.0F, var0.integer("speed") / 100.0F) : 1.0F;
      } else {
         return 1.0F;
      }
   }

   private static SlowPickaxeModule instance() {
      SlowPickaxeModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("slow-pickaxe") instanceof SlowPickaxeModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }
}
