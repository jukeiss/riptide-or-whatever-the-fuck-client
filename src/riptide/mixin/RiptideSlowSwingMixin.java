package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.SlowPickaxeModule;

@Mixin({LivingEntity.class})
public abstract class RiptideSlowSwingMixin {
   @ModifyReturnValue(
      method = {"getCurrentSwingDuration"},
      at = {@At("RETURN")}
   )
   private int riptide$slowSwing(int var1) {
      try {
         if (this != Minecraft.getInstance().player) {
            return var1;
         } else {
            float var2 = SlowPickaxeModule.multiplier();
            return var2 <= 1.0F ? var1 : Math.max(var1, Math.min(200, Math.round(var1 * var2)));
         }
      } catch (Throwable var3) {
         return var1;
      }
   }
}
