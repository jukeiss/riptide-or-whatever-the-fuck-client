package riptide.modules;

import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.gui.screen.RiptideWaypointsScreen;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class WaypointsModule extends Module {
   private final BoolSetting onDeath;
   private final BoolSetting trackDeaths;
   private final IntSetting deathMemory;
   private final ColorSetting deathColor;
   private final ColorSetting defaultColor;
   private final IntSetting dotSize;
   private boolean wasAlive = true;

   public WaypointsModule() {
      super("waypoints", "Waypoints", ModuleCategory.RENDER, "Save and render named destinations.");
      this.onDeath = this.add(new BoolSetting("on-death", "Waypoint On Death", true).description("Save a waypoint where you die.").group("Waypoints").build());
      this.trackDeaths = this.add(new BoolSetting("track-deaths", "Track Deaths", true).description("Save deaths even when off.").group("Waypoints").build());
      this.deathMemory = this.add(new IntSetting("death-memory", "Death Memory", 10, 1, 50, 1).description("Max deaths to keep.").group("Waypoints").build());
      this.deathColor = this.add(new ColorSetting("death-color", "Death Color", -50373).description("Color for death waypoints.").group("Waypoints").build());
      this.defaultColor = this.add(
         new ColorSetting("default-color", "Default Color", -12855297).description("Color for new waypoints.").group("Waypoints").build()
      );
      this.dotSize = this.add(new IntSetting("dot-size", "Dot Size", 3, 1, 10, 1).description("Waypoint dot size.").group("Waypoints").build());
      this.add(new BoolSetting("show-distance", "Show Distance", true).description("Show your distance below waypoints.").group("Waypoints").build());
      this.add(
         new ActionSetting("manage-waypoints", "Manage Waypoints", this::openManager)
            .availableOffline()
            .buttonLabel("Open")
            .description("Open the waypoint manager.")
            .group("Waypoints")
            .build()
      );
   }

   public int defaultColor() {
      return this.defaultColor.get();
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public void onGameJoin() {
      if (MC != null) {
         RiptideWaypoints.get().pruneDeaths(RiptideWaypoints.scopeKey(MC), this.deathMemory.get());
      }
   }

   @Override
   public void tick() {
      if (MC != null && MC.player != null) {
         boolean alive = MC.player.getHealth() > 0.0F;
         if (this.wasAlive && !alive) {
            this.onDeath();
         }

         this.wasAlive = alive;
      } else {
         this.wasAlive = true;
      }
   }

   private void onDeath() {
      boolean enabled = this.isEnabled();
      if (enabled ? this.onDeath.get() : this.trackDeaths.get()) {
         int x = (int)Math.floor(MC.player.getX());
         int y = (int)Math.floor(MC.player.getY());
         int z = (int)Math.floor(MC.player.getZ());
         RiptideWaypoints.Waypoint added = RiptideWaypoints.get()
            .addDeath(RiptideWaypoints.scopeKey(MC), x, y, z, this.deathColor.get(), System.currentTimeMillis(), this.deathMemory.get());
         if (added != null && enabled) {
            RiptideClientMessaging.sendPrefixed("§aDeath waypoint saved: §f" + added.name() + " §7(" + x + " " + y + " " + z + ")");
         }
      }
   }

   private void openManager() {
      if (MC != null && MC.gui != null) {
         MC.gui.setScreen(new RiptideWaypointsScreen(MC.gui.screen()));
      }
   }
}
