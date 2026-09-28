package riptide.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ItemStackRenderState.class})
public interface RiptideItemStackRenderStateAccessor {
   @Accessor("activeLayerCount")
   int riptide$getActiveLayerCount();

   @Accessor("layers")
   LayerRenderState[] riptide$getLayers();
}
