package riptide.mixin.accessor;

import java.util.List;
import net.minecraft.client.CommandHistory;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ChatComponent.class})
public interface RiptideChatComponentAccessor {
   @Accessor("allMessages")
   List<GuiMessage> riptide$getAllMessages();

   @Accessor("commandHistory")
   CommandHistory riptide$getCommandHistory();

   @Invoker("refreshTrimmedMessages")
   void riptide$refreshTrimmedMessages();
}
