package riptide.util.macro;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class MacroDynamicBindings {
   public static final String TAG_KEY = "_dynamicBindings";
   private static final Map<MacroAction, Map<String, String>> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

   private MacroDynamicBindings() {
   }

   public static void load(MacroAction action, CompoundTag actionTag) {
      if (action != null) {
         Map<String, String> values = new LinkedHashMap<>();
         if (actionTag != null && actionTag.get("_dynamicBindings") instanceof CompoundTag bindings) {
            for (String key : bindings.keySet()) {
               String value = bindings.getStringOr(key, "");
               if (!value.isBlank()) {
                  values.put(key, value);
               }
            }
         }

         if (values.isEmpty()) {
            BINDINGS.remove(action);
         } else {
            BINDINGS.put(action, values);
         }
      }
   }

   public static void write(MacroAction action, CompoundTag actionTag) {
      if (action != null && actionTag != null) {
         Map<String, String> values = BINDINGS.get(action);
         if (values != null && !values.isEmpty()) {
            CompoundTag bindings = new CompoundTag();
            values.forEach(bindings::putString);
            actionTag.put("_dynamicBindings", bindings);
         } else {
            actionTag.remove("_dynamicBindings");
         }
      }
   }

   public static String get(MacroAction action, String key) {
      Map<String, String> values = BINDINGS.get(action);
      return values == null ? "" : values.getOrDefault(key, "");
   }

   public static void set(MacroAction action, String key, String template) {
      if (action != null && key != null && !key.isBlank()) {
         String value = template == null ? "" : template.trim();
         synchronized (BINDINGS) {
            Map<String, String> values = BINDINGS.computeIfAbsent(action, ignored -> new LinkedHashMap<>());
            if (value.isBlank()) {
               values.remove(key);
            } else {
               values.put(key, value);
            }

            if (values.isEmpty()) {
               BINDINGS.remove(action);
            }
         }
      }
   }

   public static Map<String, String> snapshot(MacroAction action) {
      Map<String, String> values = BINDINGS.get(action);
      return values == null ? Map.of() : Map.copyOf(values);
   }

   public static boolean apply(MacroAction action, Minecraft mc) {
      if (action == null) {
         return true;
      } else {
         for (Entry<String, String> binding : snapshot(action).entrySet()) {
            MacroTemplate.Resolution resolution = MacroVariables.resolve(binding.getValue(), mc);
            if (!resolution.success()) {
               return false;
            }

            try {
               String value = resolution.value().trim();

               Field field;
               try {
                  field = action.getClass().getField(binding.getKey());
               } catch (NoSuchFieldException var13) {
                  if (applyBlockPositionBinding(action, binding.getKey(), value)) {
                     continue;
                  }

                  return false;
               }

               Class<?> type = field.getType();
               if (type == int.class || type == Integer.class) {
                  field.set(action, Integer.parseInt(value));
               } else if (type == long.class || type == Long.class) {
                  field.set(action, Long.parseLong(value));
               } else if (type != double.class && type != Double.class) {
                  if (type != float.class && type != Float.class) {
                     if (type == String.class) {
                        field.set(action, value);
                     } else {
                        if (!type.isEnum()) {
                           return false;
                        }

                        Object selected = null;
                        Object[] var9 = type.getEnumConstants();
                        int var10 = var9.length;
                        int var11 = 0;

                        while (true) {
                           if (var11 < var10) {
                              Object constant = var9[var11];
                              if (!((Enum)constant).name().equalsIgnoreCase(value)) {
                                 var11++;
                                 continue;
                              }

                              selected = constant;
                           }

                           if (selected == null) {
                              return false;
                           }

                           field.set(action, selected);
                           break;
                        }
                     }
                  } else {
                     field.set(action, Float.parseFloat(value));
                  }
               } else {
                  field.set(action, Double.parseDouble(value));
               }
            } catch (NumberFormatException | ReflectiveOperationException var14) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean applyBlockPositionBinding(MacroAction action, String key, String rawValue) {
      String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
      int axis = lower.endsWith("x") ? 0 : (lower.endsWith("y") ? 1 : (lower.endsWith("z") ? 2 : -1));
      if (axis < 0) {
         return false;
      } else {
         int value;
         try {
            value = (int)Math.floor(Double.parseDouble(rawValue));
         } catch (NumberFormatException var14) {
            return false;
         }

         String preferred = lower.startsWith("container") ? "containerPos" : "blockPos";
         Field positionField = null;

         try {
            positionField = action.getClass().getField(preferred);
         } catch (NoSuchFieldException var15) {
            for (Field candidate : action.getClass().getFields()) {
               if (candidate.getType() == BlockPos.class) {
                  positionField = candidate;
                  break;
               }
            }
         }

         if (positionField != null && positionField.getType() == BlockPos.class) {
            try {
               BlockPos current = (BlockPos)positionField.get(action);
               if (current == null) {
                  current = BlockPos.ZERO;
               }
               BlockPos next = switch (axis) {
                  case 0 -> new BlockPos(value, current.getY(), current.getZ());
                  case 1 -> new BlockPos(current.getX(), value, current.getZ());
                  default -> new BlockPos(current.getX(), current.getY(), value);
               };
               positionField.set(action, next);
               return true;
            } catch (ReflectiveOperationException var13) {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static int resolveInt(MacroAction action, String key, int fallback, Minecraft mc) {
      String template = get(action, key);
      if (template.isBlank()) {
         return fallback;
      } else {
         MacroTemplate.Resolution resolution = MacroVariables.resolve(template, mc);
         if (!resolution.success()) {
            throw new MacroDynamicBindings.MissingDynamicValueException();
         } else {
            try {
               return Integer.parseInt(resolution.value().trim());
            } catch (NumberFormatException var9) {
               long compact = PayAction.parseAmount(resolution.value());
               if (compact <= 2147483647L && compact >= -2147483648L) {
                  return (int)compact;
               } else {
                  throw new MacroDynamicBindings.MissingDynamicValueException();
               }
            }
         }
      }
   }

   public static double resolveDouble(MacroAction action, String key, double fallback, Minecraft mc) {
      String template = get(action, key);
      if (template.isBlank()) {
         return fallback;
      } else {
         MacroTemplate.Resolution resolution = MacroVariables.resolve(template, mc);
         if (!resolution.success()) {
            throw new MacroDynamicBindings.MissingDynamicValueException();
         } else {
            try {
               return Double.parseDouble(resolution.value().trim());
            } catch (NumberFormatException var8) {
               throw new MacroDynamicBindings.MissingDynamicValueException();
            }
         }
      }
   }

   public static final class MissingDynamicValueException extends RuntimeException {
      private MissingDynamicValueException() {
         super("Missing or invalid dynamic value", null, false, false);
      }
   }
}
