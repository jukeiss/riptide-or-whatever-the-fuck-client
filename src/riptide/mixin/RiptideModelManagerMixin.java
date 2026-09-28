package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ModelManager.class})
public class RiptideModelManagerMixin {
   @Unique
   private static long riptide$lastBrokenItemModelWarningMs;

   @WrapOperation(
      method = {"getItemModel"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Map;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
      )}
   )
   private Object riptide$guardBrokenDynamicItemModel(Map<Identifier, ItemModel> models, Object id, Object fallback, Operation<Object> original) {
      try {
         Object model = original.call(new Object[]{models, id, fallback});
         return model != null ? model : fallback;
      } catch (RuntimeException var8) {
         if (!riptide$isNullDynamicModelLoad(var8)) {
            throw var8;
         } else {
            long now = System.currentTimeMillis();
            if (now - riptide$lastBrokenItemModelWarningMs > 10000L) {
               riptide$lastBrokenItemModelWarningMs = now;
               riptide.RiptideClientAddon.LOG
                  .warn("[RIPTIDE] Resource/model pack returned a null item model for {}. Falling back to Minecraft's missing item model.", id);
            }

            return fallback;
         }
      }
   }

   @Unique
   private static boolean riptide$isNullDynamicModelLoad(Throwable error) {
      String className = error.getClass().getName();
      if (className.contains("CacheLoader$InvalidCacheLoadException")) {
         return true;
      } else {
         String message = error.getMessage();
         return message != null && message.contains("CacheLoader returned null");
      }
   }
}
