package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.ArmorTrimHiderModule;

// Worn-armour trims are looked up with stack.get(DataComponents.TRIM) inside EquipmentLayerRenderer;
// answering "no trim" there skips the trim layer without touching the item. require = 0 on both so a
// renamed call site just disables the feature instead of failing the mixin.
@Mixin({EquipmentLayerRenderer.class})
public abstract class RiptideArmorTrimHiderMixin {
   @WrapOperation(
      method = {"*"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
      )},
      require = 0
   )
   private Object riptide$hideTrim(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
      return type == DataComponents.TRIM && ArmorTrimHiderModule.hiding() ? null : original.call(stack, type);
   }

   @WrapOperation(
      method = {"*"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/component/DataComponentHolder;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
      )},
      require = 0
   )
   private Object riptide$hideTrimHolder(DataComponentHolder holder, DataComponentType<?> type, Operation<Object> original) {
      return type == DataComponents.TRIM && ArmorTrimHiderModule.hiding() ? null : original.call(holder, type);
   }
}
