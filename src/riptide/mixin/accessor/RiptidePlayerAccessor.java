package riptide.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Player.class})
public interface RiptidePlayerAccessor {
   @Invoker("getEnchantedDamage")
   float riptide$getEnchantedDamage(Entity var1, float var2, DamageSource var3);

   @Invoker("getBlockSpeedFactor")
   float riptide$getBlockSpeedFactor();
}
