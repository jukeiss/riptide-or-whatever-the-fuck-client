package riptide.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientboundRotateHeadPacket.class})
public interface RiptideRotateHeadPacketAccessor {
   @Accessor("entityId")
   int riptide$getEntityId();
}
