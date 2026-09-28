package riptide.modules;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class PotionAlertModule extends Module {
   private static final int TICKS_PER_SECOND = 20;
   private final Set<String> warned = new HashSet<>();

   public PotionAlertModule() {
      super("potion-alert", "PotionAlert", ModuleCategory.COMBAT, "Warns you before a potion effect runs out.");
      this.add(
         new IntSetting("warn-at", "Warn At", 15, 3, 120, 1).unit("s").description("Say something once an effect has this long left.").group("General").build()
      );
      this.add(
         new BoolSetting("combat-only", "Only Combat Effects", true)
            .description(
               "Only warn about effects that change a fight: strength, resistance, regeneration, fire resistance, speed, absorption and invisibility."
            )
            .group("General")
            .build()
      );
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping as well as printing the warning.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.warned.clear();
   }

   @Override
   public void onGameJoin() {
      this.warned.clear();
   }

   @Override
   public void onGameLeft() {
      this.warned.clear();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null) {
         int var2 = Math.max(1, this.integer("warn-at")) * 20;
         int var3 = var2 * 2;
         HashSet var4 = new HashSet();

         for (MobEffectInstance var6 : var1.player.getActiveEffects()) {
            if (var6 != null) {
               String var7 = effectName(var6.getEffect());
               if (var7 != null) {
                  var4.add(var7);
                  if (var6.isInfiniteDuration()) {
                     this.warned.remove(var7);
                  } else if (!this.bool("combat-only") || isCombatEffect(var7)) {
                     int var8 = var6.getDuration();
                     if (var8 > var3) {
                        this.warned.remove(var7);
                     } else if (var8 <= var2 && this.warned.add(var7)) {
                        this.warn(var1, var7, var8);
                     }
                  }
               }
            }
         }

         this.warned.retainAll(var4);
      } else {
         this.warned.clear();
      }
   }

   private void warn(Minecraft var1, String var2, int var3) {
      int var4 = Math.max(0, var3 / 20);
      RiptideClientMessaging.sendPrefixed("§d" + pretty(var2) + " §7ends in §f" + var4 + "s");
      if (this.bool("sound")) {
         try {
            var1.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.4F);
         } catch (Throwable var6) {
         }
      }
   }

   private static String effectName(Holder<MobEffect> var0) {
      try {
         return var0 == null ? null : var0.unwrapKey().map(var0x -> var0x.identifier().getPath()).orElse(null);
      } catch (Throwable var2) {
         return null;
      }
   }

   static boolean isCombatEffect(String var0) {
      return switch (var0) {
         case "strength", "resistance", "regeneration", "fire_resistance", "speed", "absorption", "invisibility", "health_boost" -> true;
         default -> false;
      };
   }

   static String pretty(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         String var1 = var0.replace('_', ' ');
         return Character.toUpperCase(var1.charAt(0)) + var1.substring(1).toLowerCase(Locale.ROOT);
      } else {
         return "Effect";
      }
   }
}
