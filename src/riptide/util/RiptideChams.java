package riptide.util;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RiptideChamsRenderTypes;
import net.minecraft.resources.Identifier;
import riptide.mixin.RiptideRenderTypeStateAccessor;

public final class RiptideChams {
   public static final int FULLBRIGHT = 15728880;

   private RiptideChams() {
   }

   public static RenderType chamsVisible(RenderType original) {
      Identifier texture = textureOf(original);
      return texture == null ? null : RiptideChamsRenderTypes.visible(texture, RiptideChamsPipelines.visible());
   }

   public static RenderType chamsOccluded(RenderType original) {
      Identifier texture = textureOf(original);
      return texture == null ? null : RiptideChamsRenderTypes.occluded(texture, RiptideChamsPipelines.occluded());
   }

   private static Identifier textureOf(RenderType original) {
      try {
         RenderSetup setup = ((RiptideRenderTypeStateAccessor)original).riptide$getState();
         return setup == null ? null : RiptideChamsRenderTypes.textureOf(setup);
      } catch (Throwable var2) {
         return null;
      }
   }
}
