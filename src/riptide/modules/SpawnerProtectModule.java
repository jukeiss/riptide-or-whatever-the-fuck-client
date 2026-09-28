package riptide.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;

/**
 * Watches the spawners around you and warns when someone you have not listed as
 * trusted gets close to one. Detection and alerting only; it never acts for you.
 */
public final class SpawnerProtectModule extends Module {
   private final Set<BlockPos> spawners = new HashSet<>();
   private final Map<String, Long> warned = new HashMap<>();
   private int timer;

   public SpawnerProtectModule() {
      super("spawner-protect", "Spawner Protect", ModuleCategory.MISC, "Warns you when an untrusted player comes near your spawners.");
      this.add(new IntSetting("scan-radius", "Scan Radius", 6, 1, 16, 1).unit("chunks").description("How many loaded chunks around you to watch for spawners.").group("Detection").build());
      this.add(new IntSetting("trigger", "Trigger Range", 24, 4, 128, 4).unit("blocks").description("How close a player has to get to a spawner to set the warning off.").group("Detection").build());
      this.add(new IntSetting("scan-delay", "Scan Delay", 40, 10, 200, 10).description("Ticks between spawner scans (20 ticks = 1 second).").group("Detection").build());
      this.add(
         new BoolSetting("keep", "Remember Spawners", true)
            .description("Keep spawners you have already seen, so a warning still fires if the server stops sending them.")
            .group("Detection")
            .build()
      );
      this.add(new StringListSetting("trusted", "Trusted", "").description("Players who never set the warning off.").playerNameList().group("Detection").build());
      this.add(new BoolSetting("trust-friends", "Trust Friends", true).description("Treat your friends and team as trusted too.").group("Detection").build());
      this.add(new BoolSetting("chat", "Chat Warning", true).description("Print a warning naming the player and the spawner.").group("Alerts").build());
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping when the warning fires.").group("Alerts").build());
      this.add(new IntSetting("cooldown", "Repeat Delay", 15, 1, 300, 1).unit("seconds").description("How long before the same player can set the warning off again.").group("Alerts").build());
   }

   @Override
   public void onEnable() {
      this.forget();
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   @Override
   public void onGameJoin() {
      this.forget();
   }

   @Override
   public void onGameLeft() {
      this.forget();
   }

   private void forget() {
      this.spawners.clear();
      this.warned.clear();
      this.timer = 0;
   }

   @Override
   public String info() {
      return this.spawners.isEmpty() ? "" : String.valueOf(this.spawners.size());
   }

   @Override
   public void tick() {
      if (MC.level == null || MC.player == null) {
         this.forget();
      } else {
         if (++this.timer >= this.integer("scan-delay")) {
            this.timer = 0;
            this.rescan();
         }

         if (!this.spawners.isEmpty()) {
            this.checkPlayers();
         }
      }
   }

   private void rescan() {
      if (!this.bool("keep")) {
         this.spawners.clear();
      }

      int radius = this.integer("scan-radius");
      int centerX = MC.player.blockPosition().getX() >> 4;
      int centerZ = MC.player.blockPosition().getZ() >> 4;

      for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
         for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
            LevelChunk chunk = MC.level.getChunkSource().getChunk(cx, cz, false);
            if (chunk != null) {
               for (BlockEntity entity : chunk.getBlockEntities().values()) {
                  if (entity instanceof SpawnerBlockEntity) {
                     this.spawners.add(entity.getBlockPos().immutable());
                  }
               }
            }
         }
      }

      // Drop spawners well outside the scan area so the set doesn't grow forever.
      int keepRadius = radius + 6;
      this.spawners.removeIf(pos -> Math.abs((pos.getX() >> 4) - centerX) > keepRadius || Math.abs((pos.getZ() >> 4) - centerZ) > keepRadius);
   }

   private void checkPlayers() {
      Set<String> trusted = new HashSet<>();

      for (String name : this.list("trusted")) {
         if (name != null && !name.isBlank()) {
            trusted.add(name.trim().toLowerCase(Locale.ROOT));
         }
      }

      boolean trustFriends = this.bool("trust-friends");
      double trigger = this.integer("trigger");
      double triggerSq = trigger * trigger;
      long now = System.currentTimeMillis();
      long cooldown = this.integer("cooldown") * 1000L;
      List<String> names = new ArrayList<>();

      for (Player player : MC.level.players()) {
         if (player != null && player != MC.player && player.isAlive()) {
            String name = player.getGameProfile().name();
            if (name != null && !name.isBlank() && !trusted.contains(name.toLowerCase(Locale.ROOT))) {
               if (!trustFriends || !isFriend(player)) {
                  BlockPos nearest = this.nearestSpawner(player, triggerSq);
                  if (nearest != null) {
                     Long last = this.warned.get(name);
                     if (last == null || now - last >= cooldown) {
                        this.warned.put(name, now);
                        names.add(name);
                        if (this.bool("chat")) {
                           int distance = (int)Math.sqrt(player.distanceToSqr(nearest.getX() + 0.5, nearest.getY() + 0.5, nearest.getZ() + 0.5));
                           RiptideClientMessaging.sendPrefixed(
                              "§c" + name + " §7is §c" + distance + "m §7from a spawner at §f" + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
                           );
                        }
                     }
                  }
               }
            }
         }
      }

      if (!names.isEmpty() && this.bool("sound")) {
         ping();
      }

      // Forget people who left, so they warn again next time they turn up.
      this.warned.entrySet().removeIf(entry -> now - entry.getValue() > Math.max(cooldown, 60000L) * 2L);
   }

   private BlockPos nearestSpawner(Player player, double triggerSq) {
      BlockPos best = null;
      double bestDistance = Double.MAX_VALUE;

      for (BlockPos pos : this.spawners) {
         double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
         if (distance <= triggerSq && distance < bestDistance) {
            bestDistance = distance;
            best = pos;
         }
      }

      return best;
   }

   private static boolean isFriend(Player player) {
      try {
         return TeamsModule.isFriendOrTeam(player);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   private static void ping() {
      try {
         MC.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.5F);
      } catch (Throwable var1) {
      }
   }
}
