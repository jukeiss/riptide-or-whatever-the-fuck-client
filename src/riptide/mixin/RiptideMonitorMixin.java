package riptide.mixin;

import com.mojang.blaze3d.platform.Monitor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Monitor.class})
public class RiptideMonitorMixin {
   @Redirect(
      method = {"queryMonitorName"},
      at = @At(
         value = "INVOKE",
         target = "Lorg/lwjgl/glfw/GLFW;glfwGetMonitorName(J)Ljava/lang/String;"
      ),
      require = 0
   )
   private static String riptide$safeMonitorName(long monitor) {
      try {
         if (GLFW.class.getMethod("glfwGetMonitorName", long.class).invoke(null, monitor) instanceof String text && !text.isEmpty()) {
            return text;
         }
      } catch (Throwable var4) {
      }

      return "unknown";
   }
}
