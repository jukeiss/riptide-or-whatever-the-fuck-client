package riptide.mixin.accessor;

import net.minecraft.client.CommandHistory;
import net.minecraft.util.ArrayListDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({CommandHistory.class})
public interface RiptideCommandHistoryAccessor {
   @Accessor("lastCommands")
   ArrayListDeque<String> riptide$getLastCommands();

   @Invoker("save")
   void riptide$save();
}
