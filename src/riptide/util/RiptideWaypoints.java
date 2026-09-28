package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

public final class RiptideWaypoints {
   public static final long DEATH_DEDUPE_MS = 30000L;
   private static final File FILE = new File(riptide.RiptideClientAddon.FOLDER, "waypoints.json");
   private static final File TMP = new File(riptide.RiptideClientAddon.FOLDER, "waypoints.json.tmp");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type STORE_TYPE = (new TypeToken<Map<String, List<RiptideWaypoints.Waypoint>>>() {}).getType();
   private static final int MAX_PER_SCOPE = 512;
   private static final long SAVE_DEBOUNCE_MS = 400L;
   private static volatile RiptideWaypoints instance;
   private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "waypoints-writer");
      t.setDaemon(true);
      return t;
   });
   private final AtomicBoolean dirty = new AtomicBoolean(false);
   private final AtomicBoolean scheduled = new AtomicBoolean(false);
   private final AtomicLong revision = new AtomicLong();
   private final Map<String, List<RiptideWaypoints.Waypoint>> byScope = new LinkedHashMap<>();

   private RiptideWaypoints() {
   }

   public static RiptideWaypoints get() {
      RiptideWaypoints local = instance;
      if (local == null) {
         synchronized (RiptideWaypoints.class) {
            if (instance == null) {
               instance = load();
            }

            local = instance;
         }
      }

      return local;
   }

   public long revision() {
      return this.revision.get();
   }

   public static String scopeKey(Minecraft mc) {
      if (mc == null) {
         return "unknown";
      } else {
         ServerData server = mc.getCurrentServer();
         if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
         } else {
            if (mc.hasSingleplayerServer()) {
               IntegratedServer integrated = mc.getSingleplayerServer();
               if (integrated != null && integrated.getWorldData() != null) {
                  String levelName = integrated.getWorldData().getLevelName();
                  if (levelName != null && !levelName.isBlank()) {
                     return levelName.trim();
                  }
               }
            }

            return "unknown";
         }
      }
   }

   public List<RiptideWaypoints.Waypoint> list(String scope) {
      synchronized (this.byScope) {
         List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
         return list == null ? List.of() : List.copyOf(list);
      }
   }

   public void add(String scope, RiptideWaypoints.Waypoint waypoint) {
      if (waypoint != null && waypoint.name() != null && !waypoint.name().isBlank()) {
         synchronized (this.byScope) {
            List<RiptideWaypoints.Waypoint> list = this.byScope.computeIfAbsent(normalize(scope), key -> new ArrayList<>());

            for (int i = 0; i < list.size(); i++) {
               if (list.get(i).name().equalsIgnoreCase(waypoint.name())) {
                  list.set(i, waypoint);
                  this.touch();
                  return;
               }
            }

            if (list.size() >= 512) {
               return;
            }

            list.add(waypoint);
         }

         this.touch();
      }
   }

   public boolean remove(String scope, String name) {
      if (name != null && !name.isBlank()) {
         boolean removed = false;
         synchronized (this.byScope) {
            List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
            if (list != null) {
               removed = list.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name.trim()));
               if (list.isEmpty()) {
                  this.byScope.remove(normalize(scope));
               }
            }
         }

         if (removed) {
            this.touch();
         }

         return removed;
      } else {
         return false;
      }
   }

   public void clear(String scope) {
      boolean removed;
      synchronized (this.byScope) {
         removed = this.byScope.remove(normalize(scope)) != null;
      }

      if (removed) {
         this.touch();
      }
   }

   public RiptideWaypoints.Waypoint find(String scope, String name) {
      if (name == null) {
         return null;
      } else {
         synchronized (this.byScope) {
            List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
            if (list == null) {
               return null;
            } else {
               for (RiptideWaypoints.Waypoint waypoint : list) {
                  if (waypoint.name().equalsIgnoreCase(name.trim())) {
                     return waypoint;
                  }
               }

               return null;
            }
         }
      }
   }

   public int colorOf(String scope, String name, int fallback) {
      RiptideWaypoints.Waypoint waypoint = this.find(scope, name);
      return waypoint == null ? fallback : waypoint.color();
   }

   public boolean setColor(String scope, String name, int color) {
      RiptideWaypoints.Waypoint updated = null;
      synchronized (this.byScope) {
         List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
         if (list != null) {
            for (int i = 0; i < list.size(); i++) {
               RiptideWaypoints.Waypoint waypoint = list.get(i);
               if (waypoint.name().equalsIgnoreCase(name == null ? "" : name.trim())) {
                  updated = new RiptideWaypoints.Waypoint(
                     waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), color, waypoint.createdMs(), waypoint.death()
                  );
                  list.set(i, updated);
                  break;
               }
            }
         }
      }

      if (updated == null) {
         return false;
      } else {
         this.touch();
         return true;
      }
   }

   public boolean rename(String scope, String oldName, String newName) {
      if (oldName != null && newName != null) {
         String trimmed = newName.trim();
         if (trimmed.isEmpty()) {
            return false;
         } else {
            String old = oldName.trim();
            synchronized (this.byScope) {
               List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
               if (list == null) {
                  return false;
               }

               int index = -1;

               for (int i = 0; i < list.size(); i++) {
                  String name = list.get(i).name();
                  if (name.equalsIgnoreCase(trimmed) && !name.equalsIgnoreCase(old)) {
                     return false;
                  }

                  if (name.equalsIgnoreCase(old)) {
                     index = i;
                  }
               }

               if (index < 0) {
                  return false;
               }

               RiptideWaypoints.Waypoint current = list.get(index);
               if (current.name().equals(trimmed)) {
                  return true;
               }

               list.set(
                  index, new RiptideWaypoints.Waypoint(trimmed, current.x(), current.y(), current.z(), current.color(), current.createdMs(), current.death())
               );
            }

            this.touch();
            return true;
         }
      } else {
         return false;
      }
   }

   public String nextName(String scope, String base) {
      String stem = (base != null && !base.isBlank() ? base.trim() : "Waypoint") + " #";
      int max = 0;
      synchronized (this.byScope) {
         List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
         if (list != null) {
            for (RiptideWaypoints.Waypoint waypoint : list) {
               String name = waypoint.name();
               if (name != null && name.startsWith(stem)) {
                  try {
                     max = Math.max(max, Integer.parseInt(name.substring(stem.length()).trim()));
                  } catch (NumberFormatException var12) {
                  }
               }
            }
         }
      }

      return stem + (max + 1);
   }

   public RiptideWaypoints.Waypoint addDeath(String scope, int x, int y, int z, int color, long nowMs, int maxDeaths) {
      synchronized (this.byScope) {
         List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
         if (list != null) {
            for (RiptideWaypoints.Waypoint waypoint : list) {
               if (waypoint.x() == x && waypoint.y() == y && waypoint.z() == z && nowMs >= waypoint.createdMs() && nowMs - waypoint.createdMs() < 30000L) {
                  return null;
               }
            }
         }
      }

      RiptideWaypoints.Waypoint waypointx = new RiptideWaypoints.Waypoint(this.nextName(scope, "Death"), x, y, z, color, nowMs, true);
      this.add(scope, waypointx);
      this.pruneDeaths(scope, maxDeaths);
      return waypointx;
   }

   public void pruneDeaths(String scope, int maxDeaths) {
      if (maxDeaths >= 1) {
         boolean changed = false;
         synchronized (this.byScope) {
            List<RiptideWaypoints.Waypoint> list = this.byScope.get(normalize(scope));
            if (list == null) {
               return;
            }

            List<RiptideWaypoints.Waypoint> deaths = new ArrayList<>();

            for (RiptideWaypoints.Waypoint waypoint : list) {
               if (waypoint.death()) {
                  deaths.add(waypoint);
               }
            }

            if (deaths.size() <= maxDeaths) {
               return;
            }

            deaths.sort((a, b) -> Long.compare(b.createdMs(), a.createdMs()));

            for (int i = maxDeaths; i < deaths.size(); i++) {
               changed |= list.remove(deaths.get(i));
            }
         }

         if (changed) {
            this.touch();
         }
      }
   }

   private static String normalize(String scope) {
      return scope != null && !scope.isBlank() ? scope.trim() : "unknown";
   }

   private void touch() {
      this.revision.incrementAndGet();
      this.save();
   }

   private static RiptideWaypoints load() {
      RiptideWaypoints waypoints = new RiptideWaypoints();
      if (FILE.exists()) {
         try (FileReader reader = new FileReader(FILE)) {
            Map<String, List<RiptideWaypoints.Waypoint>> parsed = (Map<String, List<RiptideWaypoints.Waypoint>>)GSON.fromJson(reader, STORE_TYPE);
            if (parsed != null) {
               for (Entry<String, List<RiptideWaypoints.Waypoint>> entry : parsed.entrySet()) {
                  if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                     List<RiptideWaypoints.Waypoint> list = new ArrayList<>();

                     for (RiptideWaypoints.Waypoint waypoint : entry.getValue()) {
                        if (waypoint != null && waypoint.name() != null && !waypoint.name().isBlank()) {
                           if (list.size() >= 512) {
                              break;
                           }

                           list.add(waypoint);
                        }
                     }

                     if (!list.isEmpty()) {
                        waypoints.byScope.put(entry.getKey().trim(), list);
                     }
                  }
               }
            }
         } catch (Throwable var10) {
            riptide.RiptideClientAddon.LOG.warn("Failed to read waypoints", var10);
         }
      }

      Runtime.getRuntime().addShutdownHook(new Thread(waypoints::flush, "waypoints-flush"));
      return waypoints;
   }

   private void save() {
      this.dirty.set(true);
      if (this.scheduled.compareAndSet(false, true)) {
         try {
            WRITER.schedule(() -> {
               this.scheduled.set(false);
               this.flush();
            }, 400L, TimeUnit.MILLISECONDS);
         } catch (Throwable var2) {
            this.scheduled.set(false);
            this.flush();
         }
      }
   }

   private void flush() {
      if (this.dirty.compareAndSet(true, false)) {
         this.writeNow();
      }
   }

   private synchronized void writeNow() {
      try {
         File dir = FILE.getParentFile();
         if (dir != null) {
            dir.mkdirs();
         }

         Map<String, List<RiptideWaypoints.Waypoint>> snapshot = new LinkedHashMap<>();
         synchronized (this.byScope) {
            for (Entry<String, List<RiptideWaypoints.Waypoint>> entry : this.byScope.entrySet()) {
               snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
         }

         try (FileWriter out = new FileWriter(TMP)) {
            GSON.toJson(snapshot, STORE_TYPE, out);
         }

         try {
            Files.move(TMP.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var8) {
            Files.move(TMP.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable var11) {
         riptide.RiptideClientAddon.LOG.warn("Failed to save waypoints", var11);
      }
   }

   public record Waypoint(String name, int x, int y, int z, int color, long createdMs, boolean death) {
      public Waypoint(String name, int x, int y, int z, int color, long createdMs) {
         this(name, x, y, z, color, createdMs, false);
      }
   }
}
