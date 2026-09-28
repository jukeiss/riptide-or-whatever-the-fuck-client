package riptide.modules;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;

public final class KillSayModule extends Module {
   private static final long STALE_AFTER_MS = 4000L;
   private static final String DEFAULT_LINES = String.join("|", "gg", "good fight", "gg {player}");
   private long lastAnnouncedMs;

   public KillSayModule() {
      super("kill-say", "KillSay", ModuleCategory.COMBAT, "Sends a message after you kill someone.");
      this.add(
         new StringListSetting("lines", "Lines", DEFAULT_LINES)
            .description("One message per entry, picked at random. {player} becomes their name.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("delay", "Delay", 900, 200, 5000, 100)
            .unit("ms")
            .description("Wait before sending, so it does not arrive before they have died.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("cooldown", "Cooldown", 8, 1, 120, 1)
            .unit("s")
            .description("Do not send again within this, however many people you kill.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("ignore-friends", "Never About Friends", true)
            .description("Say nothing when the person you killed is on your friends list.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.lastAnnouncedMs = System.currentTimeMillis();
   }

   @Override
   public void onGameJoin() {
      this.onEnable();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.getConnection() != null) {
         StatTrackerModule.Kill var2 = StatTrackerModule.lastKill();
         if (var2 != null) {
            long var3 = System.currentTimeMillis();
            if (var2.atMs() > this.lastAnnouncedMs) {
               if (var3 - var2.atMs() >= this.integer("delay")) {
                  if (var3 - var2.atMs() <= 4000L) {
                     if (var3 - this.lastAnnouncedMs >= this.integer("cooldown") * 1000L) {
                        if (this.bool("ignore-friends") && isFriend(var1, var2.name())) {
                           this.lastAnnouncedMs = var2.atMs();
                        } else {
                           String var5 = pick(this.list("lines"), var2.name());
                           if (var5 != null) {
                              this.lastAnnouncedMs = var2.atMs();
                              this.sendChat(var5);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static String pick(List<String> var0, String var1) {
      List var2 = var0.stream().filter(var0x -> var0x != null && !var0x.isBlank()).map(String::trim).toList();
      if (var2.isEmpty()) {
         return null;
      } else {
         String var3 = var2.size() == 1 ? (String)var2.get(0) : (String)var2.get(ThreadLocalRandom.current().nextInt(var2.size()));
         return fill(var3, var1);
      }
   }

   static String fill(String var0, String var1) {
      return var0 == null ? null : var0.replace("{player}", var1 == null ? "" : var1).trim();
   }

   private static boolean isFriend(Minecraft var0, String var1) {
      if (var1 != null && var0.level != null) {
         try {
            for (Player var3 : var0.level.players()) {
               if (var1.equalsIgnoreCase(var3.getGameProfile().name())) {
                  return TeamsModule.isFriendOrTeam(var3);
               }
            }

            for (String var6 : TeamsModule.storedFriendNames()) {
               if (var6 != null && var6.trim().equalsIgnoreCase(var1)) {
                  return true;
               }
            }

            return false;
         } catch (RuntimeException var4) {
            return true;
         }
      } else {
         return false;
      }
   }

   private void sendChat(String var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2 != null && var2.getConnection() != null) {
         if (!PackHideState.isHardLocked()) {
            try {
               if (var1.startsWith("/")) {
                  var2.getConnection().sendCommand(var1.substring(1));
               } else {
                  var2.getConnection().sendChat(var1);
               }
            } catch (Throwable var4) {
            }
         }
      }
   }
}
