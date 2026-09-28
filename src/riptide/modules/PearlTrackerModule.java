package riptide.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class PearlTrackerModule extends Module {
   private static final int MAX_TRACKED = 32;
   private final Map<Integer, double[]> tracked = new HashMap<>();

   public PearlTrackerModule() {
      super("pearl-tracker", "PearlTracker", ModuleCategory.RENDER, "Marks where thrown ender pearls come down.");
      this.add(new BoolSetting("own", "Track Mine", true).description("Follow pearls you threw yourself.").group("General").build());
      this.add(new BoolSetting("others", "Track Others", true).description("Follow pearls thrown by anyone else.").group("General").build());
      this.add(new BoolSetting("waypoint", "Save Waypoint", true).description("Drop a waypoint where a pearl lands.").group("General").build());
      this.add(new BoolSetting("chat", "Announce", true).description("Say where it landed.").group("General").build());
      this.add(
         new IntSetting("range", "Range", 128, 16, 512, 16).unit("blocks").description("Ignore pearls thrown further away than this.").group("General").build()
      );
      this.add(
         new ColorSetting("color", "Waypoint Color", -5230337)
            .description("Colour for pearl waypoints.")
            .visibleWhen(() -> this.bool("waypoint"))
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.tracked.clear();
   }

   @Override
   public void onGameJoin() {
      this.tracked.clear();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.level != null && var1.player != null) {
         HashMap var2 = new HashMap();
         int var3 = this.integer("range");

         for (Entity var5 : var1.level.entitiesForRendering()) {
            if (var5 instanceof ThrownEnderpearl var6 && this.wanted(var1, var6)) {
               int var7 = var6.getId();
               if (this.tracked.containsKey(var7) || var2.size() < 32) {
                  var2.put(var7, new double[]{var6.getX(), var6.getY(), var6.getZ()});
               }
            }
         }

         for (Entry var9 : this.tracked.entrySet()) {
            if (!var2.containsKey(var9.getKey()) && this.landedHere(var1, (double[])var9.getValue(), var3)) {
               this.land(var1, (double[])var9.getValue());
            }
         }

         this.tracked.clear();
         this.tracked.putAll(var2);
      } else {
         this.tracked.clear();
      }
   }

   private boolean landedHere(Minecraft var1, double[] var2, int var3) {
      BlockPos var4 = BlockPos.containing(var2[0], var2[1], var2[2]);
      return !var1.level.getChunkSource().hasChunk(var4.getX() >> 4, var4.getZ() >> 4)
         ? false
         : var1.player.distanceToSqr(var2[0], var2[1], var2[2]) <= (double)var3 * var3;
   }

   private boolean wanted(Minecraft var1, ThrownEnderpearl var2) {
      boolean var3 = var2.getOwner() == var1.player;
      return var3 ? this.bool("own") : this.bool("others");
   }

   private void land(Minecraft var1, double[] var2) {
      int var3 = (int)Math.floor(var2[0]);
      int var4 = (int)Math.floor(var2[1]);
      int var5 = (int)Math.floor(var2[2]);
      if (this.bool("waypoint")) {
         RiptideWaypoints.get()
            .add(
               RiptideWaypoints.scopeKey(var1),
               new RiptideWaypoints.Waypoint(
                  "Pearl " + var3 + " " + var5, var3, var4, var5, ModuleRenderUtil.color(this, "color", -5230337), System.currentTimeMillis(), false
               )
            );
      }

      if (this.bool("chat")) {
         RiptideClientMessaging.sendPrefixed("§dPearl landed at §f" + var3 + " " + var4 + " " + var5);
      }
   }
}
