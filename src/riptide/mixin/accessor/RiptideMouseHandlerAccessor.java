package riptide.mixin.accessor;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MouseHandler.class})
public interface RiptideMouseHandlerAccessor {
   @Invoker("onButton")
   void riptide$invokeOnButton(long var1, MouseButtonInfo var3, int var4);
}
