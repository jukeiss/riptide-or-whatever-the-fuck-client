package riptide.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.macro.DelayAction;
import riptide.util.macro.SendChatAction;
import riptide.util.macro.ToggleModuleAction;

public final class RiptideCompatManager {
   public static final List<String> COMMAND_PREFIX_CHOICES = List.of(".", "%", "-", "_", "*", "#", "@", "&", "=");
   private static final String METEOR_MACROS_CLASS = "meteordevelopment.meteorclient.systems.macros.Macros";
   private static final String METEOR_MACRO_CLASS = "meteordevelopment.meteorclient.systems.macros.Macro";
   private static final String METEOR_MODULES_CLASS = "meteordevelopment.meteorclient.systems.modules.Modules";
   private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
   private static final String BARITONE_GOAL_CLASS = "baritone.api.pathing.goals.Goal";
   private static final String BARITONE_GOAL_BLOCK_CLASS = "baritone.api.pathing.goals.GoalBlock";
   private static final boolean METEOR_AVAILABLE = FabricLoader.getInstance().isModLoaded("meteor-client")
      && classExists("meteordevelopment.meteorclient.systems.macros.Macros");
   private static final boolean BARITONE_AVAILABLE = FabricLoader.getInstance().isModLoaded("baritone") || classExists("baritone.api.BaritoneAPI");
   private static volatile String cachedEffectiveStored;
   private static volatile String cachedEffectivePrefix;
   private static volatile String cachedMeteorCommandPrefix = ".";
   private static volatile long meteorPrefixRefreshAtNanos;
   private static volatile int cachedRiptideModuleRevision = Integer.MIN_VALUE;
   private static volatile List<String> cachedRiptideModuleNames = List.of();
   private static volatile List<String> cachedMeteorOnlyModuleNames;

   private RiptideCompatManager() {
   }

   public static boolean isMeteorAvailable() {
      return METEOR_AVAILABLE;
   }

   public static String effectiveCommandPrefix() {
      RiptideModule module = RiptideModule.get();
      String stored = module == null ? RiptideConfig.getGlobal().commandPrefix : module.getCommandPrefix();
      String safeStored = stored == null ? "" : stored;
      String cachedStored = cachedEffectiveStored;
      String cachedPrefix = cachedEffectivePrefix;
      if (safeStored.equals(cachedStored) && cachedPrefix != null) {
         return cachedPrefix;
      } else {
         String normalized = normalizeStoredCommandPrefix(safeStored);
         cachedEffectiveStored = safeStored;
         cachedEffectivePrefix = normalized;
         return normalized;
      }
   }

   public static String environmentDefaultCommandPrefix() {
      return isMeteorAvailable() ? "%" : ".";
   }

   public static String normalizeStoredCommandPrefix(String prefix) {
      String selected = prefix == null ? "" : prefix.trim();
      if (!COMMAND_PREFIX_CHOICES.contains(selected)) {
         return environmentDefaultCommandPrefix();
      } else {
         return ".".equals(selected) && isMeteorAvailable() ? "%" : selected;
      }
   }

   public static boolean isSelectableCommandPrefix(String prefix) {
      String selected = prefix == null ? "" : prefix.trim();
      return COMMAND_PREFIX_CHOICES.contains(selected) && (!".".equals(selected) || !isMeteorAvailable());
   }

   public static boolean isBaritoneAvailable() {
      return BARITONE_AVAILABLE;
   }

   public static String meteorCommandPrefix() {
      if (!METEOR_AVAILABLE) {
         return ".";
      } else {
         long now = System.nanoTime();
         if (now < meteorPrefixRefreshAtNanos) {
            return cachedMeteorCommandPrefix;
         } else {
            synchronized (RiptideCompatManager.class) {
               now = System.nanoTime();
               if (now < meteorPrefixRefreshAtNanos) {
                  return cachedMeteorCommandPrefix;
               } else {
                  cachedMeteorCommandPrefix = readMeteorCommandPrefix();
                  meteorPrefixRefreshAtNanos = now + 1000000000L;
                  return cachedMeteorCommandPrefix;
               }
            }
         }
      }
   }

   public static List<RiptideMacro> getMeteorMacros() {
      if (!isMeteorAvailable()) {
         return Collections.emptyList();
      } else {
         List<RiptideMacro> macros = new ArrayList<>();

         try {
            Object meteorMacros = getMeteorMacrosSystem();
            Method getAllMethod = meteorMacros.getClass().getMethod("getAll");
            Object allMacros = getAllMethod.invoke(meteorMacros);
            if (!(allMacros instanceof Iterable)) {
               return macros;
            }

            for (Object meteorMacro : (Iterable)allMacros) {
               RiptideMacro converted = convertMeteorMacro(meteorMacro);
               if (converted != null) {
                  macros.add(converted);
               }
            }
         } catch (Exception var8) {
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to read Meteor macros reflectively.", var8);
         }

         return macros;
      }
   }

   public static List<String> getMeteorMacroNames() {
      List<String> names = new ArrayList<>();

      for (RiptideMacro macro : getMeteorMacros()) {
         if (macro != null && macro.name != null && !macro.name.isBlank()) {
            names.add(macro.name);
         }
      }

      return names;
   }

   public static RiptideMacro getMeteorMacro(String macroName) {
      if (macroName != null && !macroName.isBlank()) {
         for (RiptideMacro macro : getMeteorMacros()) {
            if (macroName.equalsIgnoreCase(macro.name)) {
               return macro;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static boolean importToMeteor(RiptideMacro packUtilMacro) {
      return false;
   }

   public static List<String> getRiptideModuleNames() {
      int revision = ModuleRegistry.revision();
      List<String> names = cachedRiptideModuleNames;
      if (cachedRiptideModuleRevision != revision) {
         List<String> rebuilt = new ArrayList<>(ModuleRegistry.names());
         rebuilt.removeIf(name -> name == null || name.isBlank());
         rebuilt.sort(String::compareToIgnoreCase);
         names = List.copyOf(rebuilt);
         cachedRiptideModuleNames = names;
         cachedRiptideModuleRevision = revision;
      }

      return new ArrayList<>(names);
   }

   public static List<String> getMeteorOnlyModuleNames() {
      if (!isMeteorAvailable()) {
         return Collections.emptyList();
      } else {
         List<String> cached = cachedMeteorOnlyModuleNames;
         if (cached != null) {
            return new ArrayList<>(cached);
         } else {
            List<String> nativeNames = getRiptideModuleNames();
            List<String> names = new ArrayList<>();

            try {
               Object modulesSystem = getMeteorModulesSystem();
               Method getAllMethod = modulesSystem.getClass().getMethod("getAll");
               Object allModules = getAllMethod.invoke(modulesSystem);
               if (allModules instanceof Iterable) {
                  for (Object module : (Iterable)allModules) {
                     if (getFieldValue(module, "name") instanceof String s
                        && !s.isBlank()
                        && !containsIgnoreCase(nativeNames, s)
                        && !containsIgnoreCase(names, s)) {
                        names.add(s);
                     }
                  }
               }

               names.sort(String::compareToIgnoreCase);
               cachedMeteorOnlyModuleNames = List.copyOf(names);
            } catch (Exception var11) {
               riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to read Meteor modules reflectively.", var11);
            }

            return names;
         }
      }
   }

   private static String readMeteorCommandPrefix() {
      try {
         Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
         Object config = configClass.getMethod("get").invoke(null);
         Object prefixSetting = configClass.getField("prefix").get(config);
         if (prefixSetting.getClass().getMethod("get").invoke(prefixSetting) instanceof String stringPrefix && !stringPrefix.isBlank()) {
            return stringPrefix.trim();
         }
      } catch (Throwable var5) {
      }

      return ".";
   }

   public static List<String> getMeteorModuleNames() {
      List<String> names = getRiptideModuleNames();
      names.addAll(getMeteorOnlyModuleNames());
      return names;
   }

   private static boolean containsIgnoreCase(List<String> values, String target) {
      if (values != null && target != null) {
         for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean toggleMeteorModule(String moduleName, ToggleModuleAction.ToggleMode toggleMode) {
      if (PackHideState.isActive() && !PackHideState.isHideModuleName(moduleName)) {
         return false;
      } else if (ModuleRegistry.toggle(moduleName, toggleMode)) {
         return true;
      } else if (isMeteorAvailable() && moduleName != null && !moduleName.isBlank()) {
         try {
            Object modulesSystem = getMeteorModulesSystem();
            Method getAllMethod = modulesSystem.getClass().getMethod("getAll");
            Object allModules = getAllMethod.invoke(modulesSystem);
            Object targetModule = null;
            if (allModules instanceof Iterable) {
               for (Object module : (Iterable)allModules) {
                  if (getFieldValue(module, "name") instanceof String s && moduleName.equalsIgnoreCase(s)) {
                     targetModule = module;
                     break;
                  }
               }
            }

            if (targetModule == null) {
               return false;
            } else {
               Method isActiveMethod = targetModule.getClass().getMethod("isActive");
               Method toggleMethod = targetModule.getClass().getMethod("toggle");
               boolean isActive = (Boolean)isActiveMethod.invoke(targetModule);
               switch (toggleMode) {
                  case ENABLE:
                     if (!isActive) {
                        toggleMethod.invoke(targetModule);
                     }
                     break;
                  case DISABLE:
                     if (isActive) {
                        toggleMethod.invoke(targetModule);
                     }
                     break;
                  default:
                     toggleMethod.invoke(targetModule);
               }

               return true;
            }
         } catch (Exception var11) {
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to toggle Meteor module '{}' reflectively.", moduleName, var11);
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean sendBaritoneCommand(Minecraft mc, String command) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (isBaritoneAvailable() && mc != null && mc.getConnection() != null && command != null && !command.isBlank()) {
         mc.getConnection().sendChat(command);
         return true;
      } else {
         return false;
      }
   }

   public static boolean startBaritoneGoTo(Minecraft mc, int x, int y, int z) {
      if (!isBaritoneAvailable()) {
         return false;
      } else {
         try {
            Object baritone = getPrimaryBaritone();
            Object process = invokeMethod(baritone, "getCustomGoalProcess");
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
            invokeMethod(process, "setGoalAndPath", new Class[]{goalType}, new Object[]{goal});
            return true;
         } catch (Throwable var9) {
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to start Baritone goto reflectively, falling back to chat command.", var9);
            return sendBaritoneCommand(mc, String.format("#goto %d %d %d", x, y, z));
         }
      }
   }

   public static boolean startBaritoneMine(Minecraft mc, List<String> blockNames) {
      if (isBaritoneAvailable() && blockNames != null && !blockNames.isEmpty()) {
         List<String> sanitized = new ArrayList<>();

         for (String blockName : blockNames) {
            if (blockName != null) {
               String trimmed = blockName.trim();
               if (!trimmed.isEmpty()) {
                  sanitized.add(trimmed.startsWith("minecraft:") ? trimmed.substring(10) : trimmed);
               }
            }
         }

         if (sanitized.isEmpty()) {
            return false;
         } else {
            try {
               Object baritone = getPrimaryBaritone();
               Object process = invokeMethod(baritone, "getMineProcess");
               invokeMethod(process, "mineByName", new Class[]{String[].class}, new Object[]{sanitized.toArray(String[]::new)});
               return true;
            } catch (Throwable var6) {
               riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to start Baritone mine reflectively, falling back to chat command.", var6);
               return sendBaritoneCommand(mc, "#mine " + String.join(" ", sanitized));
            }
         }
      } else {
         return false;
      }
   }

   public static boolean isBaritoneGoalActive() {
      return queryBaritoneProcessActive("getCustomGoalProcess");
   }

   public static boolean isBaritoneMineActive() {
      return queryBaritoneProcessActive("getMineProcess");
   }

   public static boolean isBaritonePathing() {
      try {
         Object pathing = invokeMethod(getPrimaryBaritone(), "getPathingBehavior");
         return invokeBoolean(pathing, "isPathing");
      } catch (Throwable var1) {
         return false;
      }
   }

   public static boolean isBaritoneBusy() {
      if (!isBaritoneAvailable()) {
         return false;
      } else {
         try {
            Object baritone = getPrimaryBaritone();
            Object pathing = invokeMethod(baritone, "getPathingBehavior");
            boolean pathingNow = invokeBoolean(pathing, "isPathing");
            boolean hasPath = invokeBoolean(pathing, "hasPath");
            boolean calculating = invokeMethod(pathing, "getInProgress") instanceof Optional<?> optional && optional.isPresent();
            return pathingNow
               || hasPath
               || calculating
               || queryProcessActive(baritone, "getCustomGoalProcess")
               || queryProcessActive(baritone, "getMineProcess");
         } catch (Throwable var7) {
            return false;
         }
      }
   }

   public static void stopBaritone(Minecraft mc) {
      if (isBaritoneAvailable()) {
         boolean stoppedReflectively = false;

         try {
            Object baritone = getPrimaryBaritone();
            Object pathing = invokeMethod(baritone, "getPathingBehavior");
            invokeMethod(pathing, "cancelEverything");
            stoppedReflectively = true;
         } catch (Throwable var4) {
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Failed to stop Baritone reflectively, falling back to chat command.", var4);
         }

         if (!stoppedReflectively) {
            sendBaritoneCommand(mc, "#stop");
         }
      }
   }

   private static RiptideMacro convertMeteorMacro(Object meteorMacro) {
      if (meteorMacro == null) {
         return null;
      } else {
         try {
            RiptideMacro macro = new RiptideMacro();
            String name = null;
            List<String> messages = null;
            Object keybindObj = null;

            try {
               if (getSettingValue(meteorMacro, "name") instanceof String s) {
                  name = s;
               }
            } catch (Exception var22) {
            }

            try {
               Object msgVal = getSettingValue(meteorMacro, "messages");
               if (msgVal instanceof List<?> list) {
                  messages = new ArrayList<>();

                  for (Object o : list) {
                     if (o instanceof String s) {
                        messages.add(s);
                     }
                  }
               } else if (msgVal instanceof Iterable<?> iter) {
                  messages = new ArrayList<>();

                  for (Object ox : iter) {
                     if (ox instanceof String s) {
                        messages.add(s);
                     }
                  }
               }
            } catch (Exception var25) {
            }

            try {
               keybindObj = getSettingValue(meteorMacro, "keybind");
            } catch (Exception var21) {
            }

            if (name == null || messages == null) {
               try {
                  if (getFieldValue(meteorMacro, "settings") instanceof Iterable<?> groups) {
                     for (Object group : groups) {
                        if (group instanceof Iterable) {
                           for (Object setting : (Iterable)group) {
                              try {
                                 String sName = (String)setting.getClass().getField("name").get(setting);
                                 Method getM = setting.getClass().getMethod("get");
                                 Object val = getM.invoke(setting);
                                 if ("name".equals(sName) && name == null && val instanceof String s) {
                                    name = s;
                                 } else if ("messages".equals(sName) && messages == null) {
                                    if (val instanceof List<?> list) {
                                       messages = new ArrayList<>();

                                       for (Object oxx : list) {
                                          if (oxx instanceof String s) {
                                             messages.add(s);
                                          }
                                       }
                                    }
                                 } else if ("keybind".equals(sName) && keybindObj == null) {
                                    keybindObj = val;
                                 }
                              } catch (Exception var23) {
                              }
                           }
                        }
                     }
                  }
               } catch (Exception var24) {
                  riptide.RiptideClientAddon.LOG.warn("[Riptide] Settings iteration fallback failed.", var24);
               }
            }

            if (name != null && !name.isBlank()) {
               macro.name = name;
               if (messages != null) {
                  for (int mi = 0; mi < messages.size(); mi++) {
                     String msg = messages.get(mi);
                     if (msg != null) {
                        String trimmed = msg.trim();
                        if (!trimmed.isEmpty()) {
                           if (trimmed.startsWith(".toggle ")) {
                              String modName = trimmed.substring(".toggle ".length()).trim();
                              if (!modName.isEmpty()) {
                                 macro.actions.add(new ToggleModuleAction(modName));
                              }
                           } else {
                              if (trimmed.startsWith(".") && !trimmed.startsWith("./") && trimmed.length() > 1) {
                                 macro.description = macro.description
                                    + (macro.description.isEmpty() ? "" : "; ")
                                    + "Warning: '"
                                    + trimmed
                                    + "' is a Meteor command, may not work as chat";
                              }

                              macro.actions.add(new SendChatAction(msg));
                           }

                           if (mi < messages.size() - 1) {
                              macro.actions.add(new DelayAction(50));
                           }
                        }
                     }
                  }
               }

               if (keybindObj != null) {
                  try {
                     Method getValue = keybindObj.getClass().getMethod("getValue");
                     if (getValue.invoke(keybindObj) instanceof Integer k && k != -1 && k != 0) {
                        macro.keyCode = k;
                     }
                  } catch (Exception var20) {
                  }
               }

               return macro;
            } else {
               return null;
            }
         } catch (Exception var26) {
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Error converting Meteor macro.", var26);
            return null;
         }
      }
   }

   private static Object getMeteorMacrosSystem() throws Exception {
      Class<?> macrosClass = Class.forName("meteordevelopment.meteorclient.systems.macros.Macros");
      Method getMethod = macrosClass.getMethod("get");
      return getMethod.invoke(null);
   }

   private static Object getMeteorModulesSystem() throws Exception {
      Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
      Method getMethod = modulesClass.getMethod("get");
      return getMethod.invoke(null);
   }

   private static Object getPrimaryBaritone() throws Exception {
      Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
      Object provider = apiClass.getMethod("getProvider").invoke(null);
      return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
   }

   private static Object getSettingValue(Object owner, String fieldName) throws Exception {
      Object setting = getFieldValue(owner, fieldName);
      if (setting == null) {
         return null;
      } else {
         Method getMethod = setting.getClass().getMethod("get");
         return getMethod.invoke(setting);
      }
   }

   private static void setSettingValue(Object owner, String fieldName, Object value) throws Exception {
      Object setting = getFieldValue(owner, fieldName);
      if (setting != null) {
         Method setMethod = setting.getClass().getMethod("set", Object.class);
         setMethod.invoke(setting, value);
      }
   }

   private static Object getFieldValue(Object owner, String fieldName) throws Exception {
      Field field = owner.getClass().getField(fieldName);
      return field.get(owner);
   }

   private static boolean queryBaritoneProcessActive(String getterName) {
      if (!isBaritoneAvailable()) {
         return false;
      } else {
         try {
            return queryProcessActive(getPrimaryBaritone(), getterName);
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   private static boolean queryProcessActive(Object baritone, String getterName) throws Exception {
      Object process = invokeMethod(baritone, getterName);
      return invokeBoolean(process, "isActive");
   }

   private static boolean invokeBoolean(Object target, String methodName) throws Exception {
      return invokeMethod(target, methodName) instanceof Boolean b && b;
   }

   private static Object invokeMethod(Object target, String methodName) throws Exception {
      return target.getClass().getMethod(methodName).invoke(target);
   }

   private static Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
      return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
   }

   private static boolean classExists(String className) {
      try {
         Class.forName(className);
         return true;
      } catch (Throwable var2) {
         return false;
      }
   }
}
