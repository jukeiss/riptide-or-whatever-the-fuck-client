package riptide.modules;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import riptide.util.RiptidePlayerScanner;

public final class RiptideAntiBot {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int GROUND_VL = 10;
   private static final int PROFILE_GRACE = 40;
   private static final Map<Integer, Integer> groundViolations = new HashMap<>();
   private static final Set<UUID> confirmedBots = new HashSet<>();
   private static final Set<UUID> loggedBots = new HashSet<>();
   private static final Map<Long, double[]> botSpots = new HashMap<>();
   private static final int CACHE_MAX = 4096;
   private static final int BOT_SPOTS_MAX = 512;
   private static final double SPOT_EPS = 0.15;
   private static final double STILL_EPS = 0.02;
   private static final Set<Integer> duplicateNameIds = new HashSet<>();
   private static Object dupLevel;
   private static int dupSize = -1;
   private static Module cachedModule;
   private static int cachedModuleRev = -1;

   private RiptideAntiBot() {
   }

   private static Module module() {
      int rev = ModuleRegistry.revision();
      if (rev != cachedModuleRev) {
         Module m = ModuleRegistry.get("antibot");
         cachedModule = m != null && m.isEnabled() ? m : null;
         cachedModuleRev = rev;
      }

      return cachedModule;
   }

   public static boolean isBot(Entity entity) {
      Module m = module();
      if (m == null) {
         return false;
      } else if (entity instanceof Player player && MC.player != null && player != MC.player) {
         UUID uuid = player.getUUID();
         if (confirmedBots.contains(uuid)) {
            return true;
         } else {
            String reason = stickyReason(player, m);
            if (reason != null) {
               confirm(uuid);
               rememberSpot(player);
               logHidden(player, reason);
               return true;
            } else if (missingProfile(player)) {
               rememberSpot(player);
               logHidden(player, "no player-info entry");
               return true;
            } else if (atKnownBotSpot(player)) {
               logHidden(player, "known bot location");
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static void confirm(UUID uuid) {
      if (confirmedBots.size() >= 4096) {
         confirmedBots.clear();
      }

      confirmedBots.add(uuid);
   }

   private static void rememberSpot(Player player) {
      if (botSpots.size() >= 512) {
         botSpots.clear();
      }

      botSpots.put(player.blockPosition().asLong(), new double[]{player.getX(), player.getY(), player.getZ()});
   }

   private static boolean atKnownBotSpot(Player player) {
      if (botSpots.isEmpty()) {
         return false;
      } else {
         double moved = Math.abs(player.getX() - player.xOld) + Math.abs(player.getZ() - player.zOld);
         if (moved > 0.02) {
            return false;
         } else {
            double[] spot = botSpots.get(player.blockPosition().asLong());
            if (spot == null) {
               return false;
            } else {
               double dx = player.getX() - spot[0];
               double dy = player.getY() - spot[1];
               double dz = player.getZ() - spot[2];
               return dx * dx + dy * dy + dz * dz <= 0.0225;
            }
         }
      }
   }

   private static String stickyReason(Player player, Module m) {
      if (duplicates().contains(player.getId())) {
         return "duplicate name";
      } else {
         float pitch = player.getXRot();
         if (Math.abs(pitch) > 90.0F) {
            return "impossible pitch " + pitch;
         } else {
            GameProfile profile = player.getGameProfile();
            if (profile != null && RiptidePlayerScanner.isUsername(profile.name())) {
               return "Aggressive".equals(m.value("mode")) && groundViolations.getOrDefault(player.getId(), 0) >= 10 ? "flying-on-ground (aggressive)" : null;
            } else {
               return "invalid profile name '" + (profile == null ? "<null>" : profile.name()) + "'";
            }
         }
      }
   }

   private static void logHidden(Player player, String reason) {
      if (loggedBots.size() >= 4096) {
         loggedBots.clear();
      }

      if (loggedBots.add(player.getUUID())) {
         GameProfile p = player.getGameProfile();
         riptide.RiptideClientAddon.LOG.debug("[antibot] hiding player id={} name='{}' — {}", new Object[]{player.getId(), p == null ? "?" : p.name(), reason});
      }
   }

   public static boolean suppress(Entity entity) {
      return isBot(entity);
   }

   public static boolean isConfirmedBot(UUID uuid) {
      return uuid != null && confirmedBots.contains(uuid);
   }

   public static void tick() {
      Module m = module();
      if (m != null && MC.level != null && MC.player != null) {
         rebuildDuplicates();
         if ("Aggressive".equals(m.value("mode"))) {
            updateGround();
         } else if (!groundViolations.isEmpty()) {
            groundViolations.clear();
         }
      } else {
         reset();
      }
   }

   public static void reset() {
      groundViolations.clear();
      confirmedBots.clear();
      loggedBots.clear();
      botSpots.clear();
      duplicateNameIds.clear();
      dupLevel = null;
      dupSize = -1;
   }

   private static boolean missingProfile(Player player) {
      ClientPacketListener conn = MC.getConnection();
      return conn == null ? false : player.tickCount >= 40 && conn.getPlayerInfo(player.getUUID()) == null;
   }

   private static Set<Integer> duplicates() {
      ClientLevel level = MC.level;
      if (level == null) {
         return duplicateNameIds;
      } else {
         if (level != dupLevel || level.players().size() != dupSize) {
            rebuildDuplicates();
         }

         return duplicateNameIds;
      }
   }

   private static void rebuildDuplicates() {
      ClientLevel level = MC.level;
      duplicateNameIds.clear();
      if (level == null) {
         dupLevel = null;
         dupSize = -1;
      } else {
         List<? extends Player> players = level.players();
         int n = players.size();

         for (int i = 0; i < n; i++) {
            Player a = players.get(i);
            GameProfile pa = a.getGameProfile();
            if (pa != null && pa.name() != null) {
               for (int j = i + 1; j < n; j++) {
                  Player b = players.get(j);
                  GameProfile pb = b.getGameProfile();
                  if (pb != null && pa.name().equals(pb.name()) && !pa.id().equals(pb.id())) {
                     duplicateNameIds.add(a.getId());
                     duplicateNameIds.add(b.getId());
                  }
               }
            }
         }

         dupLevel = level;
         dupSize = n;
      }
   }

   private static void updateGround() {
      Set<Integer> present = new HashSet<>();

      for (Player p : MC.level.players()) {
         if (p != MC.player) {
            int id = p.getId();
            present.add(id);
            int vl = groundViolations.getOrDefault(id, 0);
            if (p.onGround() && p.yOld != p.getY()) {
               groundViolations.put(id, vl + 1);
            } else if (!p.onGround() && vl > 0) {
               int next = vl / 2;
               if (next <= 0) {
                  groundViolations.remove(id);
               } else {
                  groundViolations.put(id, next);
               }
            }
         }
      }

      groundViolations.keySet().retainAll(present);
   }
}
