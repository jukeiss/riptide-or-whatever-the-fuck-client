package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class DeathLogModule extends Module {
   private static final int MAX_ENTRIES = 50;
   private final List<String> entries = new ArrayList<>();
   private boolean wasAlive = true;

   public DeathLogModule() {
      super("death-log", "DeathLog", ModuleCategory.MISC, "Records where you died and who was near enough to have done it.");
      this.add(new BoolSetting("waypoint", "Save Waypoint", true).description("Drop a waypoint where you died.").group("General").build());
      this.add(
         new ColorSetting("color", "Waypoint Color", -50373)
            .description("Colour for death waypoints.")
            .visibleWhen(() -> this.bool("waypoint"))
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("suspects", "List Who Was Near", true)
            .description("Name the players in range when you died, with their health.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("range", "Suspect Range", 32, 4, 128, 4)
            .unit("blocks")
            .description("How close someone has to be to be worth naming.")
            .visibleWhen(() -> this.bool("suspects"))
            .group("General")
            .build()
      );
      this.add(
         new ActionSetting("report", "Session Deaths", this::report)
            .buttonLabel("Show")
            .description("Print every death recorded since the game started.")
            .group("General")
            .build()
      );
   }

   private void report() {
      List var1 = this.entries();
      if (var1.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§7No deaths recorded this session.");
      } else {
         RiptideClientMessaging.sendPrefixed("§f" + var1.size() + " §7death" + (var1.size() == 1 ? "" : "s") + " this session:");

         for (String var3 : var1) {
            RiptideClientMessaging.sendPrefixed("§8- §7" + var3);
         }
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public void onGameJoin() {
      this.wasAlive = true;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.level != null) {
         boolean var2 = var1.player.getHealth() > 0.0F;
         if (this.wasAlive && !var2 && this.isEnabled()) {
            this.record(var1);
         }

         this.wasAlive = var2;
      } else {
         this.wasAlive = true;
      }
   }

   private void record(Minecraft var1) {
      int var2 = var1.player.getBlockX();
      int var3 = var1.player.getBlockY();
      int var4 = var1.player.getBlockZ();
      StringBuilder var5 = new StringBuilder();
      var5.append(var2).append(' ').append(var3).append(' ').append(var4);
      if (this.bool("waypoint")) {
         RiptideWaypoints.get()
            .add(
               RiptideWaypoints.scopeKey(var1),
               new RiptideWaypoints.Waypoint(
                  "Death " + var2 + " " + var4, var2, var3, var4, ModuleRenderUtil.color(this, "color", -50373), System.currentTimeMillis(), true
               )
            );
      }

      RiptideClientMessaging.sendPrefixed("§cDied at §f" + var2 + " " + var3 + " " + var4);
      if (this.bool("suspects")) {
         List var6 = this.suspects(var1);
         if (var6.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§7Nobody was in range.");
         } else {
            RiptideClientMessaging.sendPrefixed("§7In range: §f" + String.join("§7, §f", var6));
            var5.append(" - ").append(String.join(", ", var6));
         }
      }

      this.entries.add(var5.toString());

      while (this.entries.size() > 50) {
         this.entries.remove(0);
      }
   }

   private List<String> suspects(Minecraft var1) {
      int var2 = this.integer("range");
      ArrayList var3 = new ArrayList();

      for (Player var5 : var1.level.players()) {
         if (var5 != var1.player) {
            float var6 = var5.distanceTo(var1.player);
            if (!(var6 > var2)) {
               String var7 = var5.getGameProfile().name();
               if (var7 != null && !var7.isEmpty()) {
                  String var8 = isFriend(var5) ? ", friend" : "";
                  var3.add(var7 + " (" + Math.round(var5.getHealth()) + "hp, " + Math.round(var6) + "m" + var8 + ")");
               }
            }
         }
      }

      return var3;
   }

   private static boolean isFriend(Player var0) {
      try {
         return TeamsModule.isFriendOrTeam(var0);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   public List<String> entries() {
      return List.copyOf(this.entries);
   }
}
