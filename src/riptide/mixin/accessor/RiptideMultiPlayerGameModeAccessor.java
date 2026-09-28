package riptide.mixin.accessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MultiPlayerGameMode.class})
public interface RiptideMultiPlayerGameModeAccessor {
   @Accessor("destroyProgress")
   float riptide$getDestroyProgress();

   @Accessor("destroyProgress")
   void riptide$setDestroyProgress(float var1);

   @Accessor("destroyDelay")
   void riptide$setDestroyDelay(int var1);

   @Accessor("isDestroying")
   boolean riptide$isDestroying();

   @Accessor("destroyBlockPos")
   BlockPos riptide$getDestroyBlockPos();

   @Invoker("startPrediction")
   void riptide$startPrediction(ClientLevel var1, PredictiveAction var2);

   @Invoker("ensureHasSentCarriedItem")
   void riptide$ensureHasSentCarriedItem();
}
