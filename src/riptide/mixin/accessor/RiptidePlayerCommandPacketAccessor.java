package riptide.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerboundPlayerCommandPacket.class})
public interface RiptidePlayerCommandPacketAccessor {
   @Mutable
   @Accessor("id")
   void riptide$setEntityId(int var1);
}
