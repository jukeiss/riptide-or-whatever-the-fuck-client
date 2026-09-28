package riptide.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RiptideMeteorBridge {
   private static final String MODULES_CLASS = "meteordevelopment.meteorclient.systems.modules.Modules";
   private static final String HUD_CLASS = "meteordevelopment.meteorclient.systems.hud.Hud";

   private RiptideMeteorBridge() {
   }

   public static void disableAndSave(RiptideConfig config) {
      if (config != null) {
         try {
            Object modules = modulesInstance();
            if (modules == null) {
               return;
            }

            List<?> active = (List<?>)modules.getClass().getMethod("getActive").invoke(modules);
            List<Object> snapshot = new ArrayList<>((Collection<? extends Object>)active);
            List<String> names = new ArrayList<>();

            for (Object module : snapshot) {
               String name = (String)module.getClass().getField("name").get(module);
               names.add(name);
               module.getClass().getMethod("disable").invoke(module);
            }

            config.hideRestoreMeteorModules = names;
            Object hud = hudInstance();
            if (hud != null) {
               config.hideMeteorHudActive = hud.getClass().getField("active").getBoolean(hud);
               hud.getClass().getField("active").setBoolean(hud, false);
            }
         } catch (Throwable var8) {
         }
      }
   }

   public static void restore(RiptideConfig config) {
      if (config != null) {
         try {
            Object modules = modulesInstance();
            if (modules != null && config.hideRestoreMeteorModules != null) {
               for (String name : config.hideRestoreMeteorModules) {
                  Object module = modules.getClass().getMethod("get", String.class).invoke(modules, name);
                  if (module != null) {
                     boolean activeNow = (Boolean)module.getClass().getMethod("isActive").invoke(module);
                     if (!activeNow) {
                        module.getClass().getMethod("enable").invoke(module);
                     }
                  }
               }
            }

            config.hideRestoreMeteorModules = new ArrayList<>();
            Object hud = hudInstance();
            if (hud != null) {
               hud.getClass().getField("active").setBoolean(hud, config.hideMeteorHudActive);
            }
         } catch (Throwable var6) {
         }
      }
   }

   public static void enforceHidden() {
      try {
         Object modules = modulesInstance();
         if (modules != null) {
            List<?> active = (List<?>)modules.getClass().getMethod("getActive").invoke(modules);

            for (Object module : new ArrayList(active)) {
               module.getClass().getMethod("disable").invoke(module);
            }
         }

         Object hud = hudInstance();
         if (hud != null) {
            hud.getClass().getField("active").setBoolean(hud, false);
         }
      } catch (Throwable var4) {
      }
   }

   private static Object modulesInstance() throws ReflectiveOperationException {
      try {
         return Class.forName("meteordevelopment.meteorclient.systems.modules.Modules").getMethod("get").invoke(null);
      } catch (ClassNotFoundException var1) {
         return null;
      }
   }

   private static Object hudInstance() throws ReflectiveOperationException {
      try {
         return Class.forName("meteordevelopment.meteorclient.systems.hud.Hud").getMethod("get").invoke(null);
      } catch (ClassNotFoundException var1) {
         return null;
      }
   }
}
