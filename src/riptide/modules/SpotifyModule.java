package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideSpotify;

public final class SpotifyModule extends Module {
   private static final String ELEMENT = "spotify";
   private static final String[][] SYNC = new String[][]{
      {"source", "spotify-source"},
      {"width", "spotify-width"},
      {"scroll-speed", "spotify-scroll-speed"},
      {"color-mode", "spotify-color-mode"},
      {"album-art", "spotify-part-art"},
      {"artist", "spotify-part-artist"},
      {"elapsed", "spotify-part-time"},
      {"progress", "spotify-part-progress"},
      {"controls", "spotify-menu-strip"}
   };
   private final String[] lastPushed = new String[SYNC.length];

   public SpotifyModule() {
      super("spotify", "Spotify", ModuleCategory.RENDER, "A now-playing card for Spotify or any media player.");
      this.add(new ChoiceSetting("source", "Source", "Spotify", "Spotify", "Any Media").description("Follow Spotify only, or whatever is playing.").build());
      this.add(
         new ChoiceSetting("color-mode", "Color Mode", "Theme", "Theme", "Custom", "Rainbow")
            .description("Theme colors, your custom colors, or an animated rainbow.")
            .build()
      );
      this.add(new IntSetting("width", "Width", 175, 140, 260, 1).description("Card width in pixels.").build());
      this.add(new IntSetting("scroll-speed", "Scroll Speed", 25, 10, 60, 1).description("How fast long titles scroll.").build());
      this.add(new BoolSetting("album-art", "Album Art", true).group("Parts").description("Show the album cover.").build());
      this.add(new BoolSetting("artist", "Artist", true).group("Parts").description("Show the artist line.").build());
      this.add(new BoolSetting("elapsed", "Time", true).group("Parts").description("Show elapsed / total time.").build());
      this.add(new BoolSetting("progress", "Progress Bar", true).group("Parts").description("Show the progress bar.").build());
      this.add(new BoolSetting("controls", "Controls Strip", true).group("Parts").description("Show play/skip controls on hover.").build());
   }

   @Override
   public void onEnable() {
      try {
         RiptideHudManager.setEnabled("spotify", true);
         this.pushAll();
         RiptideSpotify.setWanted();
      } catch (Throwable var2) {
      }
   }

   @Override
   public void onDisable() {
      try {
         RiptideHudManager.setEnabled("spotify", false);
      } catch (Throwable var2) {
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return false;
   }

   @Override
   public void tick() {
      try {
         if (!RiptideHudManager.state("spotify").enabled) {
            RiptideHudManager.setEnabled("spotify", true);
         }

         this.syncChanged();
         RiptideSpotify.setWanted();
      } catch (Throwable var2) {
      }
   }

   @Override
   public String info() {
      try {
         RiptideSpotify.Snapshot var1 = RiptideSpotify.snapshot();
         if (var1 != null && var1.status() != RiptideSpotify.Status.UNAVAILABLE) {
            String var2 = var1.title();
            if (var2 != null && !var2.isBlank()) {
               return var1.status() == RiptideSpotify.Status.PAUSED ? var2 + " (paused)" : var2;
            }
         }
      } catch (Throwable var3) {
      }

      try {
         // An empty card usually means macOS refused the Apple event, so say so
         // here rather than leaving the user staring at nothing.
         String var4 = RiptideSpotify.lastError();
         if (var4 != null && !var4.isBlank()) {
            return var4;
         }
      } catch (Throwable var5) {
      }

      return null;
   }

   private void pushAll() {
      for (int var1 = 0; var1 < SYNC.length; var1++) {
         String var2 = this.value(SYNC[var1][0]);
         RiptideHudManager.setSetting("spotify", SYNC[var1][1], var2);
         this.lastPushed[var1] = var2;
      }

      this.applySource();
   }

   private void syncChanged() {
      boolean var1 = false;

      for (int var2 = 0; var2 < SYNC.length; var2++) {
         String var3 = this.value(SYNC[var2][0]);
         if (!var3.equals(this.lastPushed[var2])) {
            RiptideHudManager.setSetting("spotify", SYNC[var2][1], var3);
            this.lastPushed[var2] = var3;
            if ("source".equals(SYNC[var2][0])) {
               var1 = true;
            }
         }
      }

      if (var1) {
         this.applySource();
      }
   }

   private void applySource() {
      RiptideSpotify.setSourceAnywhere("Any Media".equals(this.value("source")));
   }
}
