package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.KeyMapping.Category;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtectorTracker;

@Mixin({Options.class})
public class RiptideProtectorOptionsMixin {
   @WrapOperation(
      method = {"<init>"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"
      )}
   )
   private KeyMapping riptide$keyMapping(String name, int key, Category category, Operation<KeyMapping> original) {
      RiptideProtectorTracker.addVanillaKeybind(name);
      return (KeyMapping)original.call(new Object[]{name, key, category});
   }

   @WrapOperation(
      method = {"<init>"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"
      )}
   )
   private KeyMapping riptide$keyMapping(String name, Type type, int key, Category category, Operation<KeyMapping> original) {
      RiptideProtectorTracker.addVanillaKeybind(name);
      return (KeyMapping)original.call(new Object[]{name, type, key, category});
   }

   @WrapOperation(
      method = {"<init>"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)Lnet/minecraft/client/KeyMapping;"
      )}
   )
   private KeyMapping riptide$keyMapping(String name, Type type, int key, Category category, int order, Operation<KeyMapping> original) {
      RiptideProtectorTracker.addVanillaKeybind(name);
      return (KeyMapping)original.call(new Object[]{name, type, key, category, order});
   }

   @WrapOperation(
      method = {"<init>"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"
      )}
   )
   private ToggleKeyMapping riptide$toggleKeyMapping(
      String name, int key, Category category, BooleanSupplier needsToggle, boolean shouldRestore, Operation<ToggleKeyMapping> original
   ) {
      RiptideProtectorTracker.addVanillaKeybind(name);
      return (ToggleKeyMapping)original.call(new Object[]{name, key, category, needsToggle, shouldRestore});
   }

   @WrapOperation(
      method = {"<init>"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"
      )}
   )
   private ToggleKeyMapping riptide$toggleKeyMapping(
      String name, Type type, int key, Category category, BooleanSupplier needsToggle, boolean shouldRestore, Operation<ToggleKeyMapping> original
   ) {
      RiptideProtectorTracker.addVanillaKeybind(name);
      return (ToggleKeyMapping)original.call(new Object[]{name, type, key, category, needsToggle, shouldRestore});
   }
}
