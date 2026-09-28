package riptide.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class CombatLogModule extends Module {
   private static final int MAX_TRACKED = 64;
   private static final double EDGE_MARGIN = 24.0;
   private static final double MOVING_SPEED = 0.08;
   private final Map<String, CombatLogModule.Seen> tracked = new HashMap<>();

   public CombatLogModule() {
      super("combat-log", "CombatLog", ModuleCategory.COMBAT, "Marks where a player disappeared mid-fight.");
      this.add(
         new IntSetting("range", "Range", 48, 8, 128, 8)
            .unit("blocks")
            .description("Only watch players who were at least this close.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("chat", "Announce", true).description("Say who vanished and where.").group("General").build());
      this.add(new BoolSetting("waypoint", "Save Waypoint", true).description("Drop a waypoint where they were standing.").group("General").build());
      this.add(
         new ColorSetting("color", "Waypoint Color", -24005)
            .description("Colour for combat log waypoints.")
            .visibleWhen(() -> this.bool("waypoint"))
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("ignore-friends", "Ignore Friends", true)
            .description("Say nothing when someone on your friends list logs out.")
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
   public void onGameLeft() {
      this.tracked.clear();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.level != null && var1.player != null) {
         int var2 = this.integer("range");
         double var3 = var1.options.getEffectiveRenderDistance() * 16.0;
         HashMap var5 = new HashMap();

         for (Player var7 : var1.level.players()) {
            if (var7 != var1.player) {
               String var8 = var7.getGameProfile().name();
               if (var8 != null
                  && !var8.isEmpty()
                  && !(var7.distanceTo(var1.player) > var2)
                  && (!this.bool("ignore-friends") || !isFriend(var7))
                  && (this.tracked.containsKey(var8) || var5.size() < 64)) {
                  double var9 = Math.sqrt(var7.getDeltaMovement().x * var7.getDeltaMovement().x + var7.getDeltaMovement().z * var7.getDeltaMovement().z);
                  var5.put(var8, new CombatLogModule.Seen(var7.getX(), var7.getY(), var7.getZ(), var7.distanceTo(var1.player), var9, var7.getHealth()));
               }
            }
         }

         for (Entry var12 : this.tracked.entrySet()) {
            if (!var5.containsKey(var12.getKey())) {
               CombatLogModule.Seen var13 = (CombatLogModule.Seen)var12.getValue();
               if (vanished(var13, var3)) {
                  this.report(var1, (String)var12.getKey(), var13);
               }
            }
         }

         this.tracked.clear();
         this.tracked.putAll(var5);
      } else {
         this.tracked.clear();
      }
   }

   static boolean vanished(CombatLogModule.Seen var0, double var1) {
      return var0.distance() > var1 - edgeMargin(var1) ? false : var0.speed() <= 0.08;
   }

   static double edgeMargin(double var0) {
      return Math.min(24.0, Math.max(0.0, var0) * 0.25);
   }

   private void report(Minecraft var1, String var2, CombatLogModule.Seen var3) {
      int var4 = (int)Math.floor(var3.x());
      int var5 = (int)Math.floor(var3.y());
      int var6 = (int)Math.floor(var3.z());
      if (this.bool("waypoint")) {
         RiptideWaypoints.get()
            .add(
               RiptideWaypoints.scopeKey(var1),
               new RiptideWaypoints.Waypoint(
                  "Logout " + var2, var4, var5, var6, ModuleRenderUtil.color(this, "color", -24005), System.currentTimeMillis(), false
               )
            );
      }

      if (this.bool("chat")) {
         RiptideClientMessaging.sendPrefixed("§6" + var2 + " §7vanished at §f" + var4 + " " + var5 + " " + var6 + " §8(" + Math.round(var3.health()) + "hp)");
      }
   }

   private static boolean isFriend(Player var0) {
      try {
         return TeamsModule.isFriendOrTeam(var0);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   @Override
   public String info() {
      return this.tracked.isEmpty() ? "" : this.tracked.size() + " watched";
   }

   record Seen(double x, double y, double z, double distance, double speed, float health) {
   }
}
