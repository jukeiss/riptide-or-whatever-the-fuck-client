package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;

public final class ChamsModule extends Module {
   public ChamsModule() {
      super("chams", "Chams", ModuleCategory.RENDER, "Entities through walls.");
      this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Entities to render.").group("General").build());
      this.add(new ChoiceSetting("style", "Style", "Colored", "Colored", "Texture").description("Colour or texture.").group("General").build());
      this.add(new DoubleSetting("max-distance", "Max Distance", 128.0, 0.0, 512.0, 4.0).description("Range, 0 unlimited.").group("General").build());
      this.add(new BoolSetting("draw-armor", "Draw Armor", true).description("Real armor on top.").group("General").build());
      this.add(
         new ColorSetting("visible-color", "Visible", -13238437)
            .description("Colour when visible.")
            .group("Colors")
            .visibleWhen(() -> "Colored".equals(this.choice("style")))
            .build()
      );
      this.add(
         new ColorSetting("occluded-color", "Behind Walls", -5230337)
            .description("Colour behind walls.")
            .group("Colors")
            .visibleWhen(() -> "Colored".equals(this.choice("style")))
            .build()
      );
      this.add(
         new IntSetting("opacity", "Opacity", 100, 0, 100, 1)
            .description("Solid colour opacity.")
            .group("Colors")
            .visibleWhen(() -> "Colored".equals(this.choice("style")))
            .build()
      );
      this.add(new BoolSetting("hit-color", "Hit Color", true).description("Flash on hit.").group("Hit Color").build());
      this.add(
         new ColorSetting("hit-color-value", "Color", -50373).description("Flash colour.").group("Hit Color").visibleWhen(() -> this.bool("hit-color")).build()
      );
   }

   @Override
   public void onEnable() {
      ModuleRenderUtil.refreshFastFlags();
   }

   @Override
   public void onDisable() {
      ModuleRenderUtil.refreshFastFlags();
   }

   @Override
   public void onOptionValueChanged(String settingId) {
      ModuleRenderUtil.refreshFastFlags();
   }
}
