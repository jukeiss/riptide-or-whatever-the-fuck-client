package riptide.modules;

import riptide.api.module.BoolSetting;

public final class HudCleanerModule extends Module {
   private static volatile boolean hideScoreboard;
   private static volatile boolean hideBossBars;
   private static volatile boolean hideTitles;
   private static volatile boolean hideActionBar;
   private static volatile boolean hideVignette;

   public HudCleanerModule() {
      super("hud-cleaner", "HudCleaner", ModuleCategory.RENDER, "Hides the scoreboard, boss bars and titles the server puts on your screen.");
      this.add(
         new BoolSetting("scoreboard", "Hide Scoreboard", false)
            .description("The sidebar on the right. Off by default — on many servers it is where your balance and stats are.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("boss-bars", "Hide Boss Bars", true)
            .description("The bars across the top, which servers use for announcements.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("titles", "Hide Titles", false).description("Large text in the middle of the screen.").group("General").build());
      this.add(new BoolSetting("action-bar", "Hide Action Bar", false).description("The line just above the hotbar.").group("General").build());
      this.add(new BoolSetting("vignette", "Hide Vignette", false).description("The darkening around the edges of the screen.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      hideScoreboard = false;
      hideBossBars = false;
      hideTitles = false;
      hideActionBar = false;
      hideVignette = false;
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      this.refresh();
   }

   @Override
   protected void onSettingsReset() {
      this.refresh();
   }

   private void refresh() {
      boolean var1 = this.isEnabled();
      hideScoreboard = var1 && this.bool("scoreboard");
      hideBossBars = var1 && this.bool("boss-bars");
      hideTitles = var1 && this.bool("titles");
      hideActionBar = var1 && this.bool("action-bar");
      hideVignette = var1 && this.bool("vignette");
   }

   public static boolean suppressScoreboard() {
      return hideScoreboard;
   }

   public static boolean suppressBossBars() {
      return hideBossBars;
   }

   public static boolean suppressTitles() {
      return hideTitles;
   }

   public static boolean suppressActionBar() {
      return hideActionBar;
   }

   public static boolean suppressVignette() {
      return hideVignette;
   }

   static int hiddenCount(boolean var0, boolean var1, boolean var2, boolean var3, boolean var4) {
      int var5 = 0;
      if (var0) {
         var5++;
      }

      if (var1) {
         var5++;
      }

      if (var2) {
         var5++;
      }

      if (var3) {
         var5++;
      }

      if (var4) {
         var5++;
      }

      return var5;
   }

   @Override
   public String info() {
      int var1 = hiddenCount(hideScoreboard, hideBossBars, hideTitles, hideActionBar, hideVignette);
      return var1 == 0 ? "" : var1 + " hidden";
   }
}
