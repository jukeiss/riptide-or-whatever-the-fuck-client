package riptide.modules;

import java.util.List;
import net.minecraft.client.Minecraft;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class WaypointShareModule extends Module {
   private static final String HERE = "Where I Am";
   private static final String NEWEST = "Newest Waypoint";
   private boolean keyWasDown;
   private long nextSendMs;
   private static final long SEND_INTERVAL_MS = 2000L;

   public WaypointShareModule() {
      super("waypoint-share", "WaypointShare", ModuleCategory.MISC, "Sends your position or your newest waypoint to chat on one key.");
      this.add(
         new ChoiceSetting("what", "Share", "Where I Am", "Where I Am", "Newest Waypoint")
            .description("Whether to send where you are standing or your most recent waypoint.")
            .group("General")
            .build()
      );
      this.add(new KeybindSetting("bind", "Share Key", 86).description("Press to send it.").group("General").build());
      this.add(
         new StringSetting("prefix", "Message", "")
            .description("Text to put in front, such as /msg Steve. Leave empty for public chat.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("dimension", "Include Dimension", true)
            .description("Add which world it is in, since the same numbers mean different places.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.keyWasDown = false;
   }

   @Override
   public void onGameLeft() {
      this.keyWasDown = false;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.getConnection() != null) {
         int var2 = this.integer("bind");
         boolean var3 = var2 != -1 && var1.gui.screen() == null && RiptideBindUtil.isBindPressed(var1, var2);
         boolean var4 = var3 && !this.keyWasDown;
         this.keyWasDown = var3;
         if (var4) {
            long var5 = System.currentTimeMillis();
            if (var5 >= this.nextSendMs) {
               String var7 = this.compose(var1);
               if (var7 != null) {
                  this.nextSendMs = var5 + 2000L;
                  this.send(var1, var7);
               }
            }
         }
      } else {
         this.keyWasDown = false;
      }
   }

   private String compose(Minecraft var1) {
      String var2;
      int var3;
      int var4;
      int var5;
      if ("Newest Waypoint".equals(this.choice("what"))) {
         RiptideWaypoints.Waypoint var6 = newestWaypoint(var1);
         if (var6 == null) {
            RiptideClientMessaging.sendPrefixed("§cNo waypoints in this world yet.");
            return null;
         }

         var2 = var6.name();
         var3 = var6.x();
         var4 = var6.y();
         var5 = var6.z();
      } else {
         var2 = "Here";
         var3 = var1.player.getBlockX();
         var4 = var1.player.getBlockY();
         var5 = var1.player.getBlockZ();
      }

      StringBuilder var8 = new StringBuilder();
      String var7 = this.value("prefix");
      if (var7 != null && !var7.isBlank()) {
         var8.append(var7.trim()).append(' ');
      }

      var8.append(var2).append(' ').append(var3).append(' ').append(var4).append(' ').append(var5);
      if (this.bool("dimension")) {
         var8.append(" (").append(dimensionName(var1)).append(')');
      }

      return var8.toString();
   }

   static RiptideWaypoints.Waypoint newest(List<RiptideWaypoints.Waypoint> var0) {
      RiptideWaypoints.Waypoint var1 = null;

      for (RiptideWaypoints.Waypoint var3 : var0) {
         if (var3 != null && (var1 == null || var3.createdMs() > var1.createdMs())) {
            var1 = var3;
         }
      }

      return var1;
   }

   private static RiptideWaypoints.Waypoint newestWaypoint(Minecraft var0) {
      try {
         return newest(RiptideWaypoints.get().list(RiptideWaypoints.scopeKey(var0)));
      } catch (RuntimeException var2) {
         return null;
      }
   }

   static String dimensionName(Minecraft var0) {
      try {
         String var1 = var0.level.dimension().identifier().getPath();

         return switch (var1) {
            case "the_nether" -> "nether";
            case "the_end" -> "end";
            case "overworld" -> "overworld";
            default -> var1;
         };
      } catch (Throwable var4) {
         return "here";
      }
   }

   private void send(Minecraft var1, String var2) {
      if (!PackHideState.isHardLocked()) {
         try {
            if (var2.startsWith("/")) {
               var1.getConnection().sendCommand(var2.substring(1));
            } else {
               var1.getConnection().sendChat(var2);
            }
         } catch (Throwable var4) {
         }
      }
   }
}
