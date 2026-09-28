package riptide.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class RiptideEssentialMenu {
   private static final String GUI_UTIL = "gg.essential.util.GuiUtil";
   private static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.Screen";

   private RiptideEssentialMenu() {
   }

   public static boolean isAvailable() {
      return FabricLoader.getInstance().isModLoaded("essential");
   }

   public static void openSocial() {
      openScreen(constructDefaulted("gg.essential.gui.friends.SocialMenu"));
   }

   public static void openWardrobe() {
      openScreen(constructDefaulted("gg.essential.gui.wardrobe.Wardrobe"));
   }

   public static void openPictures() {
      openScreen(constructDefaulted("gg.essential.gui.screenshot.components.ScreenshotBrowser"));
   }

   public static void openHost() {
      if (!pushModal(modalManager -> constructWithArgs("gg.essential.gui.sps.WorldSelectionModal", modalManager))) {
         openQuickAccess();
      }
   }

   public static void openSettings() {
      try {
         Class<?> cfg = Class.forName("gg.essential.config.McEssentialConfig");
         Object instance = cfg.getField("INSTANCE").get(null);
         Method gui = cfg.getMethod("gui", String.class);
         gui.setAccessible(true);
         Object screen = gui.invoke(instance, null);
         openScreen(screen);
      } catch (Throwable var4) {
      }
   }

   public static void openAccount() {
      if (!pushModal(modalManager -> {
         Object accountManager = constructNoArg("gg.essential.gui.menu.AccountManager");
         if (accountManager == null) {
            return null;
         } else {
            Object accounts = invokeNoArg(accountManager, "getAllAccounts");
            return accounts == null ? null : constructWithArgs("gg.essential.gui.menu.AccountManagerModal", modalManager, accountManager, accounts);
         }
      })) {
         openQuickAccess();
      }
   }

   public static void openQuickAccess() {
      try {
         Class<?> clazz = Class.forName("gg.essential.gui.modals.QuickAccessModal");
         Object companion = clazz.getDeclaredField("Companion").get(null);
         Method open = companion.getClass().getDeclaredMethod("open");
         open.setAccessible(true);
         open.invoke(companion);
      } catch (Throwable var3) {
      }
   }

   private static void openScreen(Object screen) {
      if (screen != null) {
         try {
            Class<?> guiUtil = Class.forName("gg.essential.util.GuiUtil");
            Object instance = guiUtil.getField("INSTANCE").get(null);
            Class<?> screenType = Class.forName("net.minecraft.client.gui.screens.Screen");
            Method open = guiUtil.getMethod("openScreen", screenType);
            open.setAccessible(true);
            open.invoke(instance, screen);
         } catch (Throwable var5) {
            if (screen instanceof Screen s) {
               Minecraft mc = Minecraft.getInstance();
               if (mc != null) {
                  mc.execute(() -> mc.gui.setScreen(s));
               }
            }
         }
      }
   }

   private static boolean pushModal(RiptideEssentialMenu.ModalFactory factory) {
      if (factory == null) {
         return false;
      } else {
         try {
            Class<?> guiUtil = Class.forName("gg.essential.util.GuiUtil");
            Object instance = guiUtil.getField("INSTANCE").get(null);
            Class<?> functionType = Class.forName("kotlin.jvm.functions.Function1");
            Object function = Proxy.newProxyInstance(functionType.getClassLoader(), new Class[]{functionType}, (proxy, method, args) -> {
               String name = method.getName();
               if ("toString".equals(name)) {
                  return "RiptideEssentialModalFactory";
               } else if ("hashCode".equals(name)) {
                  return System.identityHashCode(proxy);
               } else if ("equals".equals(name)) {
                  return proxy == (args == null ? null : args[0]);
               } else {
                  return "invoke".equals(name) && args != null && args.length == 1 ? factory.create(args[0]) : null;
               }
            });
            Method push = guiUtil.getMethod("pushModal", functionType);
            push.setAccessible(true);
            push.invoke(instance, function);
            return true;
         } catch (Throwable var6) {
            return false;
         }
      }
   }

   private static Object constructNoArg(String className) {
      try {
         Class<?> clazz = Class.forName(className);
         Constructor<?> ctor = clazz.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      } catch (Throwable var3) {
         return null;
      }
   }

   private static Object constructWithArgs(String className, Object... args) {
      try {
         Class<?> clazz = Class.forName(className);

         for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == (args == null ? 0 : args.length)) {
               boolean match = true;

               for (int i = 0; i < params.length; i++) {
                  Object arg = args[i];
                  if (arg == null) {
                     if (params[i].isPrimitive()) {
                        match = false;
                        break;
                     }
                  } else if (!wrap(params[i]).isInstance(arg)) {
                     match = false;
                     break;
                  }
               }

               if (match) {
                  ctor.setAccessible(true);
                  return ctor.newInstance(args);
               }
            }
         }
      } catch (Throwable var11) {
      }

      return null;
   }

   private static Object constructDefaulted(String className) {
      try {
         Class<?> clazz = Class.forName(className);

         try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
         } catch (NoSuchMethodException var9) {
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
               Class<?>[] params = ctor.getParameterTypes();
               if (params.length >= 2
                  && params[params.length - 1].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")
                  && params[params.length - 2] == int.class) {
                  Object[] args = new Object[params.length];

                  for (int i = 0; i < params.length - 2; i++) {
                     args[i] = defaultValue(params[i]);
                  }

                  int realParams = params.length - 2;
                  args[params.length - 2] = realParams >= 32 ? -1 : (1 << realParams) - 1;
                  args[params.length - 1] = null;
                  ctor.setAccessible(true);
                  return ctor.newInstance(args);
               }
            }
         }
      } catch (Throwable var10) {
      }

      return null;
   }

   private static Object invokeNoArg(Object instance, String methodName) {
      if (instance != null && methodName != null) {
         try {
            Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
         } catch (Throwable var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Class<?> wrap(Class<?> type) {
      if (!type.isPrimitive()) {
         return type;
      } else if (type == boolean.class) {
         return Boolean.class;
      } else if (type == char.class) {
         return Character.class;
      } else if (type == byte.class) {
         return Byte.class;
      } else if (type == short.class) {
         return Short.class;
      } else if (type == int.class) {
         return Integer.class;
      } else if (type == long.class) {
         return Long.class;
      } else if (type == float.class) {
         return Float.class;
      } else {
         return type == double.class ? Double.class : Void.class;
      }
   }

   private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) {
         return null;
      } else if (type == boolean.class) {
         return false;
      } else if (type == char.class) {
         return '\u0000';
      } else if (type == double.class) {
         return 0.0;
      } else if (type == float.class) {
         return 0.0F;
      } else {
         return type == long.class ? 0L : 0;
      }
   }

   @FunctionalInterface
   private interface ModalFactory {
      Object create(Object var1);
   }
}
