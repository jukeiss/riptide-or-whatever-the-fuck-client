package riptide.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LocalPlayer.class})
public interface RiptideLocalPlayerAccessor {
   @Accessor("positionReminder")
   void riptide$setPositionReminder(int var1);

   @Invoker("isSlowDueToUsingItem")
   boolean riptide$isSlowDueToUsingItem();
}
