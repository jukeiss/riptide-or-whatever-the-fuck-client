package riptide.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.RegionMapModule;

@Mixin({Hud.class})
public abstract class RiptideRegionMapMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$regionMap(GuiGraphicsExtractor var1, DeltaTracker var2, CallbackInfo var3) {
      RegionMapModule.render(var1);
   }
}
