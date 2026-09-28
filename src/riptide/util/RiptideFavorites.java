package riptide.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.module.VanillaModuleMenuController;
import riptide.modules.Module;
import riptide.modules.ModuleCategory;

public final class RiptideFavorites {
   private static final int STAR_COLOR = -11745;
   private static final String ORIG = "$riptideOrig";
   public static volatile ModuleCategory FAVORITES;
   private static final Set<String> ids = ConcurrentHashMap.newKeySet();
   private static Method origOrdered;
   private static Method origClick;
   private static Method origRender;
   private static Field hitsField;
   private static Field hitType;
   private static Field hitBounds;
   private static Field hitModule;

   private RiptideFavorites() {
   }

   public static void init() {
      try {
         FAVORITES = ModuleCategory.register("Favorites");
         Field var0 = ModuleCategory.class.getDeclaredField("CATEGORIES");
         var0.setAccessible(true);
         List var1 = (List)var0.get(null);
         synchronized (ModuleCategory.class) {
            var1.remove(FAVORITES);
            var1.add(0, FAVORITES);
         }
      } catch (Throwable var5) {
         System.out.println("[Riptide] favorites tab setup failed: " + var5);
      }

      load();
   }

   public static boolean isFavorite(Module var0) {
      return var0 != null && ids.contains(var0.id());
   }

   public static void toggle(Module var0) {
      if (ids.remove(var0.id())) {
         RiptideClientMessaging.sendPrefixed("§7Removed §f" + var0.name() + " §7from Favorites");
      } else {
         ids.add(var0.id());
         RiptideClientMessaging.sendPrefixed("§e★ §fAdded " + var0.name() + " §fto Favorites");
      }

      save();
   }

   public static List<Module> orderedModules(VanillaModuleMenuController var0, ModuleCategory var1) {
      if (var1 != null && var1 == FAVORITES) {
         ArrayList var2 = new ArrayList();

         for (ModuleCategory var4 : ModuleCategory.values()) {
            if (var4 != FAVORITES) {
               for (Module var6 : (List)call(original(var0, "ordered"), var0, var4)) {
                  if (ids.contains(var6.id())) {
                     var2.add(var6);
                  }
               }
            }
         }

         var2.sort(Comparator.comparing(var0x -> var0x.name().toLowerCase()));
         return var2;
      } else {
         return (List<Module>)call(original(var0, "ordered"), var0, var1);
      }
   }

   public static boolean mouseClicked(VanillaModuleMenuController var0, int var1, int var2, int var3) {
      if (var3 == 1 && Minecraft.getInstance().hasShiftDown() && !var0.hasTopLayer()) {
         Module var4 = moduleRowAt(var0, var1, var2);
         if (var4 != null) {
            toggle(var4);
            return true;
         }
      }

      return (Boolean)call(original(var0, "click"), var0, var1, var2, var3);
   }

   public static void renderModuleRow(VanillaModuleMenuController var0, UiContext var1, Module var2, UiBounds var3, ModuleCategory var4, int var5) {
      call(original(var0, "render"), var0, var1, var2, var3, var4, var5);
      if (isFavorite(var2)) {
         UiRenderer.rect(var1.graphics(), UiBounds.of(var3.x(), var3.y(), 2, var3.height()), -11745);
      }
   }

   private static Module moduleRowAt(VanillaModuleMenuController var0, int var1, int var2) {
      try {
         if (hitsField == null) {
            hitsField = VanillaModuleMenuController.class.getDeclaredField("hits");
            hitsField.setAccessible(true);
            Class var3 = Class.forName("riptide.gui.vanillaui.module.VanillaModuleMenuController$Hit");
            hitType = var3.getDeclaredField("type");
            hitBounds = var3.getDeclaredField("bounds");
            hitModule = var3.getDeclaredField("module");
            hitType.setAccessible(true);
            hitBounds.setAccessible(true);
            hitModule.setAccessible(true);
         }

         List var9 = (List)hitsField.get(var0);

         for (int var4 = var9.size() - 1; var4 >= 0; var4--) {
            Object var5 = var9.get(var4);
            UiBounds var6 = (UiBounds)hitBounds.get(var5);
            if (var6 != null && var6.contains(var1, var2)) {
               String var7 = ((Enum)hitType.get(var5)).name();
               if ("MODULE".equals(var7)) {
                  return (Module)hitModule.get(var5);
               }

               if (!"SCROLL_AREA".equals(var7) && !"WINDOW_HEADER".equals(var7)) {
                  return null;
               }
            }
         }
      } catch (Throwable var8) {
         System.out.println("[Riptide] favorites click lookup failed: " + var8);
      }

      return null;
   }

   private static synchronized Method original(VanillaModuleMenuController var0, String var1) {
      try {
         Class<VanillaModuleMenuController> var2 = VanillaModuleMenuController.class;
         switch (var1) {
            case "ordered":
               if (origOrdered == null) {
                  origOrdered = var2.getDeclaredMethod("orderedModules$riptideOrig", ModuleCategory.class);
                  origOrdered.setAccessible(true);
               }

               return origOrdered;
            case "click":
               if (origClick == null) {
                  origClick = var2.getDeclaredMethod("mouseClicked$riptideOrig", int.class, int.class, int.class);
                  origClick.setAccessible(true);
               }

               return origClick;
            default:
               if (origRender == null) {
                  origRender = var2.getDeclaredMethod(
                     "renderModuleRow$riptideOrig", UiContext.class, Module.class, UiBounds.class, ModuleCategory.class, int.class
                  );
                  origRender.setAccessible(true);
               }

               return origRender;
         }
      } catch (NoSuchMethodException var5) {
         throw new IllegalStateException(var5);
      }
   }

   private static Object call(Method var0, Object var1, Object... var2) {
      try {
         return var0.invoke(var1, var2);
      } catch (InvocationTargetException var4) {
         throw sneaky(var4.getCause());
      } catch (IllegalAccessException var5) {
         throw new IllegalStateException(var5);
      }
   }

   private static <T extends Throwable> RuntimeException sneaky(Throwable var0) throws T {
      throw var0;
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("riptide").resolve("favorites.txt");
   }

   private static void load() {
      try {
         Path var0 = file();
         if (Files.exists(var0)) {
            for (String var2 : Files.readAllLines(var0, StandardCharsets.UTF_8)) {
               if (!var2.isBlank()) {
                  ids.add(var2.trim());
               }
            }
         }
      } catch (Throwable var3) {
         System.out.println("[Riptide] couldn't load favorites: " + var3);
      }
   }

   private static void save() {
      try {
         Path var0 = file();
         Files.createDirectories(var0.getParent());
         Files.write(var0, new TreeSet<>(ids), StandardCharsets.UTF_8);
      } catch (Throwable var1) {
         System.out.println("[Riptide] couldn't save favorites: " + var1);
      }
   }
}
