package riptide.modules;

import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideSpotifyBar;

public final class SpotifyControlsModule extends Module {
   public SpotifyControlsModule() {
      super("spotify-controls", "Spotify Controls", ModuleCategory.MISC, "Music controls inside your inventory.");
      this.add(
         new ChoiceSetting("where", "Show In", "Inventory", "Inventory", "All Screens").description("Inventory and chests only, or every screen.").build()
      );
      this.add(new ChoiceSetting("anchor", "Position", "Bottom", "Bottom", "Top").description("Where the bar sits on screen.").build());
      this.add(new BoolSetting("always-show", "Always Show", false).description("Keep the bar visible even when nothing is playing.").build());
      this.add(
         new ActionSetting("reset-position", "Position", RiptideSpotifyBar::resetPosition)
            .buttonLabel("Reset")
            .description("Drag the bar anywhere; this puts it back.")
            .build()
      );
      this.add(new IntSetting("pos-x", "X", -1, -1, 10000, 1).visibleWhen(() -> false).build());
      this.add(new IntSetting("pos-y", "Y", -1, -1, 10000, 1).visibleWhen(() -> false).build());
   }

   @Override
   public String info() {
      return this.choice("where");
   }
}
