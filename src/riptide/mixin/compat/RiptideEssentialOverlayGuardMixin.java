package riptide.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({Screen.class})
public abstract class RiptideEssentialOverlayGuardMixin {
   @WrapMethod(
      method = {"extractRenderState"}
   )
   private void riptide$catchEssentialOverlayNpe(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
      try {
         original.call(new Object[]{graphics, mouseX, mouseY, delta});
      } catch (NullPointerException var7) {
         if (!riptide$isEssentialFault(var7)) {
            throw var7;
         }
      }
   }

   @Unique
   private static boolean riptide$isEssentialFault(Throwable t) {
      for (Throwable cur = t; cur != null; cur = cur.getCause()) {
         for (StackTraceElement el : cur.getStackTrace()) {
            if (el.getClassName().startsWith("gg.essential")) {
               return true;
            }
         }
      }

      return false;
   }
}
