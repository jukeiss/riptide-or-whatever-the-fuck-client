package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.util.RiptideWorldHighlightRenderer;

public final class WorldModule extends Module {
   private boolean lastDarken;
   private String lastMode = "";
   private int lastDarkness = Integer.MIN_VALUE;
   private int lastTint = Integer.MIN_VALUE;
   private String lastBlocks = "";

   public WorldModule() {
      super("world", "World", ModuleCategory.RENDER, "World render tweaks.");
      this.add(new BoolSetting("darken-blocks", "Tint Blocks", true).description("Tint block textures.").group("Block Tint").build());
      this.add(
         new ColorSetting("tint-color", "Color", -16777216)
            .description("Tint color.")
            .group("Block Tint")
            .visibleWhen(() -> this.bool("darken-blocks"))
            .build()
      );
      this.add(
         new IntSetting("darkness", "Strength", 50, 0, 100, 1)
            .description("Tint strength, percent.")
            .group("Block Tint")
            .visibleWhen(() -> this.bool("darken-blocks"))
            .build()
      );
      this.add(
         new ChoiceSetting("mode", "Mode", "Blacklist", "Blacklist", "Whitelist")
            .description("List spares or targets.")
            .group("Block Tint")
            .visibleWhen(() -> this.bool("darken-blocks"))
            .build()
      );
      this.add(
         RegistryListSetting.blocks("blocks", "Blocks")
            .description("Blacklist spares, whitelist targets.")
            .group("Block Tint")
            .visibleWhen(() -> this.bool("darken-blocks"))
            .build()
      );
      this.add(new BoolSetting("block-highlight", "Block Highlight", false).description("Custom block outline.").group("Block Highlight").build());
      this.add(
         new ColorSetting("highlight-color", "Color", -1)
            .description("Outline color.")
            .group("Block Highlight")
            .visibleWhen(() -> this.bool("block-highlight"))
            .build()
      );
      this.add(
         new DoubleSetting("highlight-width", "Line Width", 2.0, 0.5, 6.0, 0.1)
            .description("Outline thickness.")
            .group("Block Highlight")
            .visibleWhen(() -> this.bool("block-highlight"))
            .build()
      );
      this.add(
         new ChoiceSetting("highlight-style", "Style", "Full", "Full", "Corners")
            .description("Full cube or corners.")
            .group("Block Highlight")
            .visibleWhen(() -> this.bool("block-highlight"))
            .build()
      );
      this.add(
         new BoolSetting("highlight-fill", "Fill", false)
            .description("Fill the box faintly.")
            .group("Block Highlight")
            .visibleWhen(() -> this.bool("block-highlight"))
            .build()
      );
      this.add(
         new BoolSetting("highlight-progress", "Show Progress", true)
            .description("Damage color shift.")
            .group("Block Highlight")
            .visibleWhen(() -> this.bool("block-highlight"))
            .build()
      );
      this.add(new BoolSetting("skybox", "Skybox", false).description("Replace the sky with the Riptide panorama.").group("Skybox").build());
      this.add(
         new BoolSetting("skybox-spin", "Spin", true)
            .description("Slowly spin the skybox panorama.")
            .group("Skybox")
            .visibleWhen(() -> this.bool("skybox"))
            .build()
      );
      this.add(
         new DoubleSetting("skybox-speed", "Spin Speed", 1.0, 0.5, 3.0, 0.1)
            .description("Rotation speed.")
            .group("Skybox")
            .visibleWhen(() -> this.bool("skybox") && this.bool("skybox-spin"))
            .build()
      );
      this.add(
         new BoolSetting("skybox-recolor", "Theme Recolor", false)
            .description("Theme-recolor the skybox panorama.")
            .group("Skybox")
            .visibleWhen(() -> this.bool("skybox"))
            .build()
      );
      this.add(
         new ColorSetting("skybox-color", "Color", -50373)
            .description("Custom panorama color.")
            .group("Skybox")
            .visibleWhen(() -> this.bool("skybox") && !this.bool("skybox-recolor"))
            .build()
      );
   }

   @Override
   public void onEnable() {
      ModuleRenderUtil.refreshWorldRenderer();
      this.pushHighlight();
   }

   @Override
   public void onDisable() {
      ModuleRenderUtil.refreshWorldRenderer();
      RiptideWorldHighlightRenderer.disable();
   }

   @Override
   public void tick() {
      boolean darken = this.bool("darken-blocks");
      String mode = this.choice("mode");
      int darkness = this.integer("darkness");
      int tint = ModuleRenderUtil.color(this, "tint-color", -16777216);
      String blocks = this.value("blocks");
      if (darken != this.lastDarken || !mode.equals(this.lastMode) || darkness != this.lastDarkness || tint != this.lastTint || !blocks.equals(this.lastBlocks)
         )
       {
         this.lastDarken = darken;
         this.lastMode = mode;
         this.lastDarkness = darkness;
         this.lastTint = tint;
         this.lastBlocks = blocks;
         ModuleRenderUtil.refreshWorldRenderer();
      }

      this.pushHighlight();
   }

   private void pushHighlight() {
      boolean active = this.isEnabled() && this.bool("block-highlight");
      int color = ModuleRenderUtil.color(this, "highlight-color", -1);
      float width = (float)this.decimal("highlight-width");
      boolean corners = "Corners".equals(this.choice("highlight-style"));
      boolean fill = this.bool("highlight-fill");
      int fillColor = color & 16777215 | 1073741824;
      RiptideWorldHighlightRenderer.push(active, color, width, corners, fill, fillColor);
      RiptideWorldHighlightRenderer.pushProgress(active && this.bool("highlight-progress"));
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if (this.isEnabled()) {
         this.pushHighlight();
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return false;
   }
}
