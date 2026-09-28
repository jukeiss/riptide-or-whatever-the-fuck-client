package riptide.modules;

public final class ArmorTrimHiderModule extends Module {
   private static ArmorTrimHiderModule cached;

   public ArmorTrimHiderModule() {
      super("armor-trim-hider", "Armor Trim Hider", ModuleCategory.RENDER, "Draws worn armour without its trim patterns, on everyone. Client-side only.");
   }

   private static ArmorTrimHiderModule instance() {
      ArmorTrimHiderModule module = cached;
      if (module == null && ModuleRegistry.get("armor-trim-hider") instanceof ArmorTrimHiderModule found) {
         module = found;
         cached = found;
      }

      return module;
   }

   // Read from RiptideArmorTrimHiderMixin on the render thread.
   public static boolean hiding() {
      ArmorTrimHiderModule module = instance();
      return module != null && module.isEnabled() && !PackHideState.isActive();
   }
}
