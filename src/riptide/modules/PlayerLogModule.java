package riptide.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class PlayerLogModule extends Module {
   private static final int MAX_PLAYERS = 500;
   private final Map<String, PlayerLogModule.Sighting> sightings = new LinkedHashMap<>();

   public PlayerLogModule() {
      super("player-log", "PlayerLog", ModuleCategory.MISC, "Remembers every player you have seen this session and where.");
      this.add(
         new IntSetting("range", "Range", 128, 16, 512, 16).unit("blocks").description("How close someone has to be to be logged.").group("General").build()
      );
      this.add(
         new BoolSetting("repeat-alert", "Tell Me On Repeats", true)
            .description("Say something when you meet a name you have already logged.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("repeat-gap", "Repeat Gap", 300, 30, 3600, 30)
            .unit("s")
            .description("How long before seeing them again counts as a new sighting.")
            .visibleWhen(() -> this.bool("repeat-alert"))
            .group("General")
            .build()
      );
      this.add(
         new ActionSetting("report", "Show Log", this::report).buttonLabel("Show").description("Print the log to chat, most seen first.").group("Log").build()
      );
      this.add(new ActionSetting("clear", "Clear Log", this::clear).buttonLabel("Clear").description("Forget everyone.").group("Log").build());
   }

   @Override
   public void onGameJoin() {
      this.sightings.clear();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.level != null && var1.player != null) {
         int var2 = this.integer("range");
         long var3 = System.currentTimeMillis();
         long var5 = this.integer("repeat-gap") * 1000L;

         for (Player var8 : var1.level.players()) {
            if (var8 != var1.player && !(var8.distanceTo(var1.player) > var2)) {
               String var9 = var8.getGameProfile().name();
               if (var9 != null && !var9.isEmpty()) {
                  PlayerLogModule.Sighting var10 = this.sightings.get(var9);
                  boolean var11 = var10 == null || var3 - var10.whenMs() > var5;
                  if (var11) {
                     int var12 = var10 == null ? 1 : var10.times() + 1;
                     this.sightings.put(var9, new PlayerLogModule.Sighting(var8.getBlockX(), var8.getBlockY(), var8.getBlockZ(), var3, var12));
                     this.trim();
                     if (var10 != null && this.bool("repeat-alert")) {
                        RiptideClientMessaging.sendPrefixed(
                           "§e"
                              + var9
                              + " §7again §8("
                              + var12
                              + " times, last seen "
                              + ago(var3 - var10.whenMs())
                              + " ago at "
                              + var10.x()
                              + " "
                              + var10.z()
                              + "§8)"
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void trim() {
      while (this.sightings.size() > 500) {
         String var1 = null;
         long var2 = Long.MAX_VALUE;

         for (Entry var5 : this.sightings.entrySet()) {
            if (((PlayerLogModule.Sighting)var5.getValue()).whenMs() < var2) {
               var2 = ((PlayerLogModule.Sighting)var5.getValue()).whenMs();
               var1 = (String)var5.getKey();
            }
         }

         if (var1 == null) {
            return;
         }

         this.sightings.remove(var1);
      }
   }

   private void report() {
      if (this.sightings.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§7Nobody logged yet.");
      } else {
         RiptideClientMessaging.sendPrefixed("§f" + this.sightings.size() + " §7players seen:");
         this.sightings
            .entrySet()
            .stream()
            .sorted((var0, var1) -> Integer.compare(var1.getValue().times(), var0.getValue().times()))
            .limit(15L)
            .forEach(var0 -> {
               PlayerLogModule.Sighting var1 = var0.getValue();
               RiptideClientMessaging.sendPrefixed("  §f" + var0.getKey() + " §8x" + var1.times() + " §7at §f" + var1.x() + " " + var1.y() + " " + var1.z());
            });
      }
   }

   private void clear() {
      int var1 = this.sightings.size();
      this.sightings.clear();
      RiptideClientMessaging.sendPrefixed("§7Forgot §f" + var1 + " §7players.");
   }

   static String ago(long var0) {
      long var2 = Math.max(0L, var0 / 1000L);
      if (var2 < 60L) {
         return var2 + "s";
      } else {
         long var4 = var2 / 60L;
         return var4 < 60L ? var4 + "m" : var4 / 60L + "h" + var4 % 60L + "m";
      }
   }

   private record Sighting(int x, int y, int z, long whenMs, int times) {
   }
}
