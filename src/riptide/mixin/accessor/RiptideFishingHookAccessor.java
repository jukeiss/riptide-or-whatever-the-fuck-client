package riptide.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({FishingHook.class})
public interface RiptideFishingHookAccessor {
   @Accessor("biting")
   boolean riptide$isBiting();

   @Accessor("DATA_HOOKED_ENTITY")
   static EntityDataAccessor<Integer> riptide$getHookedEntityData() {
      throw new AssertionError();
   }
}
