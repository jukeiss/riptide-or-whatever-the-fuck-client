package riptide.mixin.accessor;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({KeyboardHandler.class})
public interface RiptideKeyboardHandlerAccessor {
   @Invoker("keyPress")
   void riptide$invokeKeyPress(long var1, int var3, KeyEvent var4);
}
