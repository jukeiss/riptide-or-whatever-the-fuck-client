package riptide.api.macro;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import riptide.util.macro.MacroTemplate;
import riptide.util.macro.MacroValue;

public interface MacroExecutionContext {
   Minecraft mc();

   void runOnClientThread(Runnable var1);

   <T> T callOnClientThread(Supplier<T> var1);

   void waitTicks(int var1);

   void awaitCondition(CompletableFuture<Void> var1);

   void awaitCondition(AddonCondition var1);

   boolean isActive();

   void setStatus(String var1);

   void sendPacket(Packet<?> var1);

   Optional<MacroValue> variable(String var1);

   void setVariable(String var1, MacroValue var2);

   default void setVariable(String name, Object value) {
      this.setVariable(name, MacroValue.text(value));
   }

   void removeVariable(String var1);

   Map<String, MacroValue> variables();

   MacroTemplate.Resolution resolveTemplate(String var1);

   default void captureResult(String name, MacroValue value) {
      this.setVariable(name, value);
   }

   default void captureResults(Map<String, MacroValue> values) {
      if (values != null) {
         values.forEach(this::setVariable);
      }
   }

   default Optional<Integer> resolveInt(String template) {
      MacroTemplate.Resolution resolved = this.resolveTemplate(template);
      if (!resolved.success()) {
         return Optional.empty();
      } else {
         try {
            return Optional.of(Integer.parseInt(resolved.value().trim()));
         } catch (NumberFormatException var4) {
            return Optional.empty();
         }
      }
   }

   default Optional<Double> resolveDouble(String template) {
      MacroTemplate.Resolution resolved = this.resolveTemplate(template);
      if (!resolved.success()) {
         return Optional.empty();
      } else {
         try {
            return Optional.of(Double.parseDouble(resolved.value().trim()));
         } catch (NumberFormatException var4) {
            return Optional.empty();
         }
      }
   }

   default <E extends Enum<E>> Optional<E> resolveEnum(String template, Class<E> type) {
      if (type == null) {
         return Optional.empty();
      } else {
         MacroTemplate.Resolution resolved = this.resolveTemplate(template);
         if (!resolved.success()) {
            return Optional.empty();
         } else {
            for (E value : (Enum[])type.getEnumConstants()) {
               if (value.name().equalsIgnoreCase(resolved.value().trim())) {
                  return Optional.of(value);
               }
            }

            return Optional.empty();
         }
      }
   }
}
