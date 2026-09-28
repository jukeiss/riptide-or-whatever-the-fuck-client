package riptide.util;

import net.minecraft.client.KeyMapping;

public interface RiptideKeyMappingBridge {
   static RiptideKeyMappingBridge of(KeyMapping mapping) {
      return (RiptideKeyMappingBridge)mapping;
   }

   boolean riptide$isActuallyDown();

   void riptide$resetPressedState();

   void riptide$simulatePress(boolean var1);
}
