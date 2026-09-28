package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import riptide.api.module.BoolSetting;

public final class NoFogModule extends Module {
   private static final float FAR = 4096.0F;
   private static volatile boolean active;
   private static volatile boolean keepFluid = true;
   private static volatile boolean keepBlindness = true;

   public NoFogModule() {
      super("no-fog", "NoFog", ModuleCategory.RENDER, "Pushes distance fog out so you can see to the edge of your render distance.");
      this.add(
         new BoolSetting("keep-fluid", "Keep Water And Lava", true)
            .description("Leave the fog you get underwater or in lava. It is the effect itself, not a way of hiding the edge of the world.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("keep-blindness", "Keep Blindness", true)
            .description("Leave the fog from blindness and darkness, which are things done to you.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      active = false;
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
      active = this.isEnabled();
      keepFluid = this.bool("keep-fluid");
      keepBlindness = this.bool("keep-blindness");
   }

   public static float farDistance() {
      return active && !fogIsTheEffect() ? 4096.0F : -1.0F;
   }

   static boolean fogIsTheEffect() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0 != null && var0.player != null) {
         try {
            if (keepFluid && var0.player.isEyeInFluid(FluidTags.WATER)) {
               return true;
            } else if (keepFluid && var0.player.isEyeInFluid(FluidTags.LAVA)) {
               return true;
            } else {
               return !keepBlindness || !var0.player.hasEffect(MobEffects.BLINDNESS) && !var0.player.hasEffect(MobEffects.DARKNESS)
                  ? keepFluid && var0.player.isInPowderSnow
                  : true;
            }
         } catch (Throwable var2) {
            return true;
         }
      } else {
         return true;
      }
   }

   @Override
   public String info() {
      return active ? "clear" : "";
   }
}
