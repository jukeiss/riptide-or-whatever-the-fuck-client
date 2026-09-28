package riptide.api.macro;

import net.minecraft.client.Minecraft;
import riptide.util.macro.MacroAction;

public interface ContextMacroAction extends MacroAction {
   void run(MacroExecutionContext var1) throws InterruptedException;

   @Override
   default void execute(Minecraft mc) {
   }
}
