package riptide.mixin;

import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.render.RiptideFemaleBodyRenderer;

@Mixin({HumanoidArmorLayer.class})
public class RiptideHumanoidArmorLayerFemaleBodyMixin {
   @Inject(
      method = {"<init>(Lnet/minecraft/client/renderer/entity/RenderLayerParent;Lnet/minecraft/client/renderer/entity/ArmorModelSet;Lnet/minecraft/client/renderer/entity/ArmorModelSet;Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;)V"},
      at = {@At("TAIL")}
   )
   private void riptide$rememberPlayerArmorModels(
      RenderLayerParent<?, ?> renderer, ArmorModelSet<?> modelSet, ArmorModelSet<?> babyModelSet, EquipmentLayerRenderer equipmentRenderer, CallbackInfo ci
   ) {
      if (renderer instanceof AvatarRenderer) {
         RiptideFemaleBodyRenderer.markArmorModels(modelSet);
         if (babyModelSet != modelSet) {
            RiptideFemaleBodyRenderer.markArmorModels(babyModelSet);
         }
      }
   }
}
