package riptide.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class RiptideYarnMappings {
   private static final String RESOURCE_PATH = "/riptide-inspector-mappings.tsv";
   private static final Map<String, String> CLASS_ALIASES = new HashMap<>();
   private static final Map<String, String> SIMPLE_CLASS_ALIASES = new HashMap<>();
   private static final Map<String, String> METHOD_ALIASES = new HashMap<>();
   private static final Map<String, String> FIELD_ALIASES = new HashMap<>();
   private static volatile boolean loaded;

   private RiptideYarnMappings() {
   }

   public static String lookupClassAlias(Class<?> type) {
      return type == null ? null : lookupClassAlias(type.getName());
   }

   public static String lookupClassAlias(String className) {
      if (className != null && !className.isBlank()) {
         ensureLoaded();
         return CLASS_ALIASES.get(className.replace('/', '.'));
      } else {
         return null;
      }
   }

   public static String lookupSimpleClassAlias(String simpleName) {
      if (simpleName != null && !simpleName.isBlank()) {
         ensureLoaded();
         String alias = SIMPLE_CLASS_ALIASES.get(simpleName);
         return alias != null && !alias.isBlank() ? alias : null;
      } else {
         return null;
      }
   }

   public static String lookupMethodLabel(Method method) {
      if (method == null) {
         return null;
      } else {
         ensureLoaded();
         return METHOD_ALIASES.get(memberKey(method.getDeclaringClass().getName(), method.getName(), descriptor(method)));
      }
   }

   public static String lookupFieldLabel(Field field) {
      if (field == null) {
         return null;
      } else {
         ensureLoaded();
         return FIELD_ALIASES.get(memberKey(field.getDeclaringClass().getName(), field.getName(), descriptor(field.getType())));
      }
   }

   private static void ensureLoaded() {
      if (!loaded) {
         synchronized (RiptideYarnMappings.class) {
            if (!loaded) {
               loadMappings();
               loaded = true;
            }
         }
      }
   }

   private static void loadMappings() {
      try {
         label73: {
            try (InputStream stream = RiptideYarnMappings.class.getResourceAsStream("/riptide-inspector-mappings.tsv")) {
               if (stream != null) {
                  String line;
                  try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                     while ((line = reader.readLine()) != null) {
                        parseLine(line);
                     }
                     break label73;
                  }
               }
            }

            return;
         }
      } catch (IOException var8) {
      }
   }

   private static void parseLine(String line) {
      if (line != null) {
         String trimmed = line.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            String[] parts = trimmed.split("\t");
            if (parts.length >= 3) {
               String var3 = parts[0];
               switch (var3) {
                  case "C":
                     String owner = parts[1];
                     String alias = parts[2];
                     if (owner.isBlank() || alias.isBlank()) {
                        return;
                     }

                     CLASS_ALIASES.put(owner, alias);
                     String simpleOwner = owner.substring(owner.lastIndexOf(46) + 1);
                     registerSimpleAlias(simpleOwner, alias);
                     break;
                  case "M":
                     if (parts.length < 5) {
                        return;
                     }

                     METHOD_ALIASES.put(memberKey(parts[1], parts[2], parts[3]), parts[4]);
                     break;
                  case "F":
                     if (parts.length < 5) {
                        return;
                     }

                     FIELD_ALIASES.put(memberKey(parts[1], parts[2], parts[3]), parts[4]);
               }
            }
         }
      }
   }

   private static void registerSimpleAlias(String simpleOwner, String alias) {
      if (simpleOwner != null && !simpleOwner.isBlank() && alias != null && !alias.isBlank()) {
         String existing = SIMPLE_CLASS_ALIASES.get(simpleOwner);
         if (existing == null) {
            SIMPLE_CLASS_ALIASES.put(simpleOwner, alias);
         } else {
            if (!existing.equals(alias)) {
               SIMPLE_CLASS_ALIASES.put(simpleOwner, "");
            }
         }
      }
   }

   private static String memberKey(String owner, String name, String descriptor) {
      return owner + "#" + name + "#" + descriptor;
   }

   private static String descriptor(Method method) {
      StringBuilder builder = new StringBuilder();
      builder.append('(');

      for (Class<?> parameterType : method.getParameterTypes()) {
         builder.append(descriptor(parameterType));
      }

      builder.append(')').append(descriptor(method.getReturnType()));
      return builder.toString();
   }

   private static String descriptor(Class<?> type) {
      if (type.isPrimitive()) {
         if (type == void.class) {
            return "V";
         }

         if (type == boolean.class) {
            return "Z";
         }

         if (type == byte.class) {
            return "B";
         }

         if (type == char.class) {
            return "C";
         }

         if (type == short.class) {
            return "S";
         }

         if (type == int.class) {
            return "I";
         }

         if (type == long.class) {
            return "J";
         }

         if (type == float.class) {
            return "F";
         }

         if (type == double.class) {
            return "D";
         }
      }

      return type.isArray() ? type.getName().replace('.', '/') : "L" + type.getName().replace('.', '/') + ";";
   }
}
