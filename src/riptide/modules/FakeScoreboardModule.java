package riptide.modules;

import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;

public final class FakeScoreboardModule extends Module {
   public FakeScoreboardModule() {
      super("fake-scoreboard", "Fake Scoreboard", ModuleCategory.MISC, "Replaces the sidebar with your own text.");
      this.add(new StringSetting("title", "Title", "DONUTSMP").description("Heading shown at the top of the sidebar.").build());
      this.add(new StringSetting("line1", "Line 1", "Balance: $1,000,000").group("Lines").build());
      this.add(new StringSetting("line2", "Line 2", "Shards: 2,500").group("Lines").build());
      this.add(new StringSetting("line3", "Line 3", "Kills: 1,337").group("Lines").build());
      this.add(new StringSetting("line4", "Line 4", "Deaths: 0").group("Lines").build());
      this.add(new StringSetting("line5", "Line 5", "Playtime: 420h").group("Lines").build());
      this.add(new StringSetting("line6", "Line 6", "play.donutsmp.net").group("Lines").build());
      this.add(new ChoiceSetting("side", "Side", "Right", "Right", "Left").description("Which edge the sidebar sits on.").build());
      this.add(new IntSetting("offset", "Y Offset", 0, -200, 200, 5).description("Nudge the sidebar up or down.").build());
   }

   @Override
   public String info() {
      return this.text("title");
   }
}
