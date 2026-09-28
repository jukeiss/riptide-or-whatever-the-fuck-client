package riptide.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerboundMovePlayerPacket.class})
public interface RiptideMovePlayerPacketAccessor {
   @Mutable
   @Accessor("y")
   void riptide$setY(double var1);

   @Mutable
   @Accessor("onGround")
   void riptide$setOnGround(boolean var1);

   @Mutable
   @Accessor("yRot")
   void riptide$setYRot(float var1);

   @Mutable
   @Accessor("xRot")
   void riptide$setXRot(float var1);
}
