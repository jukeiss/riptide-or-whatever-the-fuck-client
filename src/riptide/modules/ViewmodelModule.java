package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;

public final class ViewmodelModule extends Module {
   public ViewmodelModule() {
      super("viewmodel", "Viewmodel", ModuleCategory.RENDER, "Customize first-person hand position and animations.");
      this.add(new BoolSetting("main-hand", "Main Hand", false).description("Move the main hand item.").group("Main Hand").build());
      this.add(new DoubleSetting("main-hand-scale", "Item Scale", 0.0, -5.0, 5.0, 0.05).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new DoubleSetting("main-hand-x", "X", 0.0, -5.0, 5.0, 0.05).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new DoubleSetting("main-hand-y", "Y", 0.0, -5.0, 5.0, 0.05).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new DoubleSetting("main-hand-rot-x", "Rotation X", 0.0, -50.0, 50.0, 1.0).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new DoubleSetting("main-hand-rot-y", "Rotation Y", 0.0, -50.0, 50.0, 1.0).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new DoubleSetting("main-hand-rot-z", "Rotation Z", 0.0, -50.0, 50.0, 1.0).group("Main Hand").visibleWhen(() -> this.bool("main-hand")).build());
      this.add(new BoolSetting("off-hand", "Off Hand", false).description("Move the off hand item.").group("Off Hand").build());
      this.add(new DoubleSetting("off-hand-scale", "Item Scale", 0.0, -5.0, 5.0, 0.05).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new DoubleSetting("off-hand-x", "X", 0.0, -1.0, 1.0, 0.02).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new DoubleSetting("off-hand-y", "Y", 0.0, -1.0, 1.0, 0.02).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new DoubleSetting("off-hand-rot-x", "Rotation X", 0.0, -50.0, 50.0, 1.0).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new DoubleSetting("off-hand-rot-y", "Rotation Y", 0.0, -50.0, 50.0, 1.0).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new DoubleSetting("off-hand-rot-z", "Rotation Z", 0.0, -50.0, 50.0, 1.0).group("Off Hand").visibleWhen(() -> this.bool("off-hand")).build());
      this.add(new IntSetting("swing-duration", "Swing Duration", 6, 1, 20, 1).description("Attack animation length (ticks)."));
      this.add(
         new ChoiceSetting("blocking-animation", "Blocking Animation", "1.7", "1.7", "Pushdown")
            .description("Sword swing animation.")
            .group("Blocking Animation")
            .build()
      );
      this.add(
         new DoubleSetting("one-seven-y", "1.7 Y", 0.1, 0.05, 0.3, 0.01)
            .group("Blocking Animation")
            .visibleWhen(() -> "1.7".equals(this.choice("blocking-animation")))
            .build()
      );
      this.add(
         new DoubleSetting("one-seven-swing-scale", "1.7 Swing Scale", 0.9, 0.1, 1.0, 0.05)
            .group("Blocking Animation")
            .visibleWhen(() -> "1.7".equals(this.choice("blocking-animation")))
            .build()
      );
      this.add(new BoolSetting("equip-offset", "Equip Offset", true).description("Item lower/raise animation.").group("Equip Offset").build());
      this.add(
         new BoolSetting("ignore-blocking", "Ignore Blocking", true)
            .description("Skip the blocking offset.")
            .group("Equip Offset")
            .visibleWhen(() -> this.bool("equip-offset"))
            .build()
      );
      this.add(
         new BoolSetting("ignore-place", "Ignore Place", true)
            .description("Skip the place bump.")
            .group("Equip Offset")
            .visibleWhen(() -> this.bool("equip-offset"))
            .build()
      );
      this.add(
         new BoolSetting("ignore-amount", "Ignore Amount", false)
            .description("Skip on count change.")
            .group("Equip Offset")
            .visibleWhen(() -> this.bool("equip-offset"))
            .build()
      );
      this.add(new BoolSetting("air-walker", "Air Walker", false).description("Keep the walk bob in the air."));
   }

   @Override
   public void onEnable() {
      this.push();
   }

   @Override
   public void onDisable() {
      ViewmodelState.disable();
   }

   @Override
   public void tick() {
      this.push();
   }

   @Override
   public boolean ticksWhenDisabled() {
      return false;
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if (this.isEnabled()) {
         this.push();
      }
   }

   private void push() {
      ViewmodelState.mainHandOn = this.bool("main-hand");
      ViewmodelState.mainHandScale = (float)this.decimal("main-hand-scale");
      ViewmodelState.mainHandX = (float)this.decimal("main-hand-x");
      ViewmodelState.mainHandY = (float)this.decimal("main-hand-y");
      ViewmodelState.mainHandRotX = (float)this.decimal("main-hand-rot-x");
      ViewmodelState.mainHandRotY = (float)this.decimal("main-hand-rot-y");
      ViewmodelState.mainHandRotZ = (float)this.decimal("main-hand-rot-z");
      ViewmodelState.offHandOn = this.bool("off-hand");
      ViewmodelState.offHandScale = (float)this.decimal("off-hand-scale");
      ViewmodelState.offHandX = (float)this.decimal("off-hand-x");
      ViewmodelState.offHandY = (float)this.decimal("off-hand-y");
      ViewmodelState.offHandRotX = (float)this.decimal("off-hand-rot-x");
      ViewmodelState.offHandRotY = (float)this.decimal("off-hand-rot-y");
      ViewmodelState.offHandRotZ = (float)this.decimal("off-hand-rot-z");
      ViewmodelState.swingDuration = this.integer("swing-duration");
      ViewmodelState.blockAnim = "Pushdown".equals(this.choice("blocking-animation")) ? 1 : 0;
      ViewmodelState.oneSevenY = (float)this.decimal("one-seven-y");
      ViewmodelState.oneSevenSwingScale = (float)this.decimal("one-seven-swing-scale");
      ViewmodelState.equipOffsetOn = this.bool("equip-offset");
      ViewmodelState.ignoreBlocking = this.bool("ignore-blocking");
      ViewmodelState.ignorePlace = this.bool("ignore-place");
      ViewmodelState.ignoreAmount = this.bool("ignore-amount");
      ViewmodelState.airWalker = this.bool("air-walker");
      ViewmodelState.enable();
   }
}
