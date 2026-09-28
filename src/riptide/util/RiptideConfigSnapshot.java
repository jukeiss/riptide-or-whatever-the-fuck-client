package riptide.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

final class RiptideConfigSnapshot {
   private RiptideConfigSnapshot() {
   }

   static RiptideConfig copyOf(RiptideConfig source) {
      return copyOf(source, true);
   }

   static RiptideConfig copyForPersistence(RiptideConfig source) {
      return copyOf(source, false);
   }

   private static RiptideConfig copyOf(RiptideConfig source, boolean applyDefaults) {
      if (source == null) {
         return new RiptideConfig();
      } else {
         try {
            RiptideConfig copy = (RiptideConfig)copyValue(source, new IdentityHashMap<>());
            if (applyDefaults) {
               copy.applyRuntimeDefaults();
            }

            return copy;
         } catch (ReflectiveOperationException var3) {
            throw new IllegalStateException("Could not capture Riptide config", var3);
         }
      }
   }

   private static Object copyValue(Object value, IdentityHashMap<Object, Object> seen) throws ReflectiveOperationException {
      if (value != null && !isImmutable(value.getClass())) {
         Object prior = seen.get(value);
         if (prior != null) {
            return prior;
         } else if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            seen.put(value, copy);

            for (Object element : list) {
               copy.add(copyValue(element, seen));
            }

            return copy;
         } else if (!(value instanceof Map<?, ?> map)) {
            Class<?> type = value.getClass();
            if (type != RiptideConfig.class && type.getEnclosingClass() != RiptideConfig.class) {
               throw new IllegalStateException("Unsupported config value: " + type.getName());
            } else {
               Constructor<?> constructor = type.getDeclaredConstructor();
               constructor.setAccessible(true);
               Object copy = constructor.newInstance();
               seen.put(value, copy);
               copyFields(value, copy, type, seen);
               return copy;
            }
         } else {
            Map<Object, Object> copy = new LinkedHashMap<>(Math.max(4, map.size()));
            seen.put(value, copy);

            for (Entry<?, ?> entry : map.entrySet()) {
               copy.put(copyValue(entry.getKey(), seen), copyValue(entry.getValue(), seen));
            }

            return copy;
         }
      } else {
         return value;
      }
   }

   private static void copyFields(Object source, Object target, Class<?> type, IdentityHashMap<Object, Object> seen) throws ReflectiveOperationException {
      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Field field : current.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
               field.setAccessible(true);
               field.set(target, copyValue(field.get(source), seen));
            }
         }
      }
   }

   private static boolean isImmutable(Class<?> type) {
      return type.isPrimitive()
         || type.isEnum()
         || type == String.class
         || type == Boolean.class
         || type == Byte.class
         || type == Short.class
         || type == Integer.class
         || type == Long.class
         || type == Float.class
         || type == Double.class
         || type == Character.class;
   }
}
