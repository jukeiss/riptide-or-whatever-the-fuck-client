package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import riptide.api.module.BoolSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideSettingCache;

public final class ParticleFilterModule extends Module {
   private static volatile boolean active;
   private static volatile Set<String> blocked = Set.of();
   private final RiptideSettingCache<Set<String>> extra = new RiptideSettingCache<>(ParticleFilterModule::parseIds);

   public ParticleFilterModule() {
      super("particle-filter", "ParticleFilter", ModuleCategory.RENDER, "Hides particles that block your view, such as explosions and totems.");
      this.add(
         new BoolSetting("explosions", "Explosions", true)
            .description("Crystal and TNT blasts, which are the ones that blind you.")
            .group("What To Hide")
            .build()
      );
      this.add(new BoolSetting("totems", "Totems", true).description("The gold burst when somebody pops a totem.").group("What To Hide").build());
      this.add(new BoolSetting("fire", "Fire And Smoke", false).description("Flames, smoke and lava drips.").group("What To Hide").build());
      this.add(new BoolSetting("blocks", "Block Breaking", false).description("The dust from breaking and placing blocks.").group("What To Hide").build());
      this.add(
         new BoolSetting("enchant", "Enchanting Glyphs", false)
            .description("The letters that drift from enchanting tables and bookshelves.")
            .group("What To Hide")
            .build()
      );
      this.add(
         new StringListSetting("extra", "Also Hide", "").description("Particle ids to hide as well, such as minecraft:dust.").group("What To Hide").build()
      );
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      active = false;
      blocked = Set.of();
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      this.refresh();
   }

   @Override
   protected void onSettingsReset() {
      this.refresh();
   }

   private void refresh() {
      if (!this.isEnabled()) {
         this.onDisable();
      } else {
         HashSet var1 = new HashSet();
         if (this.bool("explosions")) {
            var1.add("explosion");
            var1.add("explosion_emitter");
            var1.add("flash");
         }

         if (this.bool("totems")) {
            var1.add("totem_of_undying");
         }

         if (this.bool("fire")) {
            var1.add("flame");
            var1.add("smoke");
            var1.add("large_smoke");
            var1.add("campfire_cosy_smoke");
            var1.add("lava");
            var1.add("dripping_lava");
            var1.add("falling_lava");
         }

         if (this.bool("blocks")) {
            var1.add("block");
            var1.add("block_marker");
            var1.add("falling_dust");
         }

         if (this.bool("enchant")) {
            var1.add("enchant");
            var1.add("enchanted_hit");
         }

         var1.addAll(this.extra.get(this.list("extra")));
         blocked = Set.copyOf(var1);
         active = !var1.isEmpty();
      }
   }

   public static boolean shouldHide(ParticleOptions var0) {
      if (active && var0 != null) {
         try {
            return blocked.contains(BuiltInRegistries.PARTICLE_TYPE.getKey(var0.getType()).getPath());
         } catch (Throwable var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   static Set<String> parseIds(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            String var4 = var3.trim().toLowerCase(Locale.ROOT);
            int var5 = var4.indexOf(58);
            if (var5 >= 0) {
               var4 = var4.substring(var5 + 1);
            }

            if (!var4.isEmpty()) {
               var1.add(var4);
            }
         }
      }

      return Set.copyOf(var1);
   }

   @Override
   public String info() {
      int var1 = blocked.size();
      return var1 == 0 ? "" : var1 + " hidden";
   }
}
