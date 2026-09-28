package riptide.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.mixin.accessor.RiptideKeyboardHandlerAccessor;
import riptide.mixin.accessor.RiptideMouseHandlerAccessor;
import riptide.modules.AutoArmorModule;
import riptide.modules.AutoTotemModule;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptidePathWalker;

@Mixin({KeyMapping.class})
public abstract class RiptideKeyMappingMixin implements RiptideKeyMappingBridge {
   @Shadow
   protected Key key;

   @Shadow
   public abstract void setDown(boolean var1);

   @Inject(
      method = {"releaseAll"},
      at = {@At("TAIL")}
   )
   private static void riptide$reassertModuleHeldKeys(CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.options != null && ModuleRegistry.sneakHoldsShift()) {
         mc.options.keyShift.setDown(true);
      }

      RiptidePathWalker.onExternalKeyRelease();
   }

   @Unique
   @Override
   public boolean riptide$isActuallyDown() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null) {
         Window window = mc.getWindow();
         int code = this.key.getValue();
         return this.key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window.handle(), code) == 1 : InputConstants.isKeyDown(window, code);
      } else {
         return false;
      }
   }

   @Unique
   @Override
   public void riptide$resetPressedState() {
      this.setDown(this.riptide$isActuallyDown());
   }

   @Unique
   @Override
   public void riptide$simulatePress(boolean pressed) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null && mc.keyboardHandler != null && mc.mouseHandler != null) {
         Window window = mc.getWindow();
         int action = pressed ? 1 : 0;
         switch (this.key.getType()) {
            case KEYSYM:
               ((RiptideKeyboardHandlerAccessor)mc.keyboardHandler).riptide$invokeKeyPress(window.handle(), action, new KeyEvent(this.key.getValue(), 0, 0));
               break;
            case SCANCODE:
               ((RiptideKeyboardHandlerAccessor)mc.keyboardHandler).riptide$invokeKeyPress(window.handle(), action, new KeyEvent(-1, this.key.getValue(), 0));
               break;
            case MOUSE:
               ((RiptideMouseHandlerAccessor)mc.mouseHandler).riptide$invokeOnButton(window.handle(), new MouseButtonInfo(this.key.getValue(), 0), action);
               break;
            default:
               this.setDown(pressed);
         }
      }
   }

   @Inject(
      method = {"consumeClick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$consumeHotbarKeysDuringTotemOperation(CallbackInfoReturnable<Boolean> cir) {
      if (AutoTotemModule.operationActive() || AutoArmorModule.operationActive()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.options != null) {
            KeyMapping self = (KeyMapping)this;

            for (KeyMapping hotbar : mc.options.keyHotbarSlots) {
               if (self == hotbar) {
                  cir.setReturnValue(false);
                  return;
               }
            }

            if (self == mc.options.keySwapOffhand || self == mc.options.keyDrop || self == mc.options.keyPickItem) {
               cir.setReturnValue(false);
            }
         }
      }
   }
}
