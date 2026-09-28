package riptide.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientboundMoveEntityPacket.class})
public interface RiptideMoveEntityPacketAccessor {
   @Accessor("entityId")
   int riptide$getEntityId();
}
