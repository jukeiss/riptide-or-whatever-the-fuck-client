package riptide.util.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

final class MultiEntityTracker {
   private static final int CAP = 512;
   private static final double RELATIVE_SCALE = 2.4414062E-4F;
   private final Map<Integer, MultiEntityTracker.State> byId = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> byUuid = new ConcurrentHashMap<>();

   void put(int id, String type, double x, double y, double z) {
      this.put(id, null, type, x, y, z, Vec3.ZERO, 0.0F, 0.0F, 0.0F, false, -1);
   }

   void put(int id, UUID uuid, String type, double x, double y, double z, Vec3 movement, float yRot, float xRot, float headYRot, boolean onGround, int ownerId) {
      this.put(id, uuid, type, x, y, z, movement, yRot, xRot, headYRot, onGround, ownerId, null);
   }

   void put(
      int id,
      UUID uuid,
      String type,
      double x,
      double y,
      double z,
      Vec3 movement,
      float yRot,
      float xRot,
      float headYRot,
      boolean onGround,
      int ownerId,
      Vec3 focus
   ) {
      String safeType = type == null ? "" : type;
      Vec3 newPosition = new Vec3(x, y, z);
      if (this.byId.size() < 512 || this.byId.containsKey(id) || this.evictFor(safeType, newPosition, focus)) {
         MultiEntityTracker.State state = new MultiEntityTracker.State(
            id, uuid, safeType, newPosition, movement == null ? Vec3.ZERO : movement, yRot, xRot, headYRot, onGround, ownerId, -1
         );
         MultiEntityTracker.State replaced = this.byId.put(id, state);
         if (replaced != null && replaced.uuid() != null && !replaced.uuid().equals(uuid)) {
            this.byUuid.remove(replaced.uuid(), id);
         }

         if (uuid != null) {
            Integer oldId = this.byUuid.put(uuid, id);
            if (oldId != null && oldId != id) {
               MultiEntityTracker.State stale = this.byId.get(oldId);
               if (stale != null && uuid.equals(stale.uuid())) {
                  this.byId.remove(oldId, stale);
               }
            }
         }
      }
   }

   private boolean evictFor(String newType, Vec3 newPosition, Vec3 focus) {
      int newRank = priorityRank(newType);
      MultiEntityTracker.State victim = null;
      int victimRank = -1;
      double victimDistance = -1.0;
      double newDistance = focus == null ? Double.POSITIVE_INFINITY : newPosition.distanceToSqr(focus);

      for (MultiEntityTracker.State value : this.byId.values()) {
         int rank = priorityRank(value.type());
         double distance = focus == null ? 0.0 : value.position().distanceToSqr(focus);
         boolean lowerPriority = rank > newRank;
         boolean sameButFarther = rank == newRank && focus != null && distance > newDistance;
         if ((lowerPriority || sameButFarther) && (victim == null || rank > victimRank || rank == victimRank && distance > victimDistance)) {
            victim = value;
            victimRank = rank;
            victimDistance = distance;
         }
      }

      if (victim == null) {
         return false;
      } else {
         MultiEntityTracker.State removed = this.byId.remove(victim.id());
         if (removed == null) {
            return false;
         } else {
            if (removed.uuid() != null) {
               this.byUuid.remove(removed.uuid(), removed.id());
            }

            return true;
         }
      }
   }

   private static int priorityRank(String type) {
      String normalized = normalize(type);
      if (normalized.equals("player") || normalized.equals("fishing bobber")) {
         return 0;
      } else {
         return !normalized.equals("item")
               && !normalized.equals("experience orb")
               && !normalized.contains("arrow")
               && !normalized.contains("projectile")
               && !normalized.contains("display")
               && !normalized.equals("falling block")
               && !normalized.equals("area effect cloud")
               && !normalized.equals("firework rocket")
            ? 1
            : 2;
      }
   }

   void move(int id, double x, double y, double z) {
      this.byId
         .computeIfPresent(
            id, (ignored, old) -> copy(old, new Vec3(x, y, z), old.movement(), old.yRot(), old.xRot(), old.headYRot(), old.onGround(), old.hookedId())
         );
   }

   void moveRelative(int id, short xa, short ya, short za, boolean hasPosition, float yRot, float xRot, boolean hasRotation, boolean onGround) {
      this.byId.computeIfPresent(id, (ignored, old) -> {
         Vec3 pos = old.position();
         if (hasPosition) {
            pos = pos.add(xa * 2.4414062E-4F, ya * 2.4414062E-4F, za * 2.4414062E-4F);
         }

         return copy(old, pos, old.movement(), hasRotation ? yRot : old.yRot(), hasRotation ? xRot : old.xRot(), old.headYRot(), onGround, old.hookedId());
      });
   }

   void sync(int id, PositionMoveRotation values, boolean onGround) {
      if (values != null) {
         this.byId
            .computeIfPresent(
               id,
               (ignored, old) -> copy(old, values.position(), values.deltaMovement(), values.yRot(), values.xRot(), old.headYRot(), onGround, old.hookedId())
            );
      }
   }

   void moveAbsolute(int id, Vec3 position, Vec3 movement, float yRot, float xRot) {
      if (position != null) {
         this.byId
            .computeIfPresent(
               id,
               (ignored, old) -> copy(old, position, movement == null ? old.movement() : movement, yRot, xRot, old.headYRot(), old.onGround(), old.hookedId())
            );
      }
   }

   void teleport(int id, PositionMoveRotation change, Set<Relative> relatives, boolean onGround) {
      if (change != null) {
         Set<Relative> relativeSet = relatives == null ? Set.of() : relatives;
         this.byId.computeIfPresent(id, (ignored, old) -> {
            PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(old.positionMoveRotation(), change, relativeSet);
            return copy(old, absolute.position(), absolute.deltaMovement(), absolute.yRot(), absolute.xRot(), old.headYRot(), onGround, old.hookedId());
         });
      }
   }

   void motion(int id, Vec3 movement) {
      if (movement != null) {
         this.byId
            .computeIfPresent(id, (ignored, old) -> copy(old, old.position(), movement, old.yRot(), old.xRot(), old.headYRot(), old.onGround(), old.hookedId()));
      }
   }

   void headRotation(int id, float headYRot) {
      this.byId
         .computeIfPresent(id, (ignored, old) -> copy(old, old.position(), old.movement(), old.yRot(), old.xRot(), headYRot, old.onGround(), old.hookedId()));
   }

   void fishingHookTarget(int hookId, int encodedTargetId) {
      int targetId = encodedTargetId > 0 ? encodedTargetId - 1 : -1;
      this.byId
         .computeIfPresent(
            hookId, (ignored, old) -> copy(old, old.position(), old.movement(), old.yRot(), old.xRot(), old.headYRot(), old.onGround(), targetId)
         );
   }

   MultiEntityTracker.State state(int id) {
      return this.byId.get(id);
   }

   MultiEntityTracker.State state(UUID uuid) {
      if (uuid == null) {
         return null;
      } else {
         Integer id = this.byUuid.get(uuid);
         if (id == null) {
            return null;
         } else {
            MultiEntityTracker.State state = this.byId.get(id);
            if (state != null && uuid.equals(state.uuid())) {
               return state;
            } else {
               this.byUuid.remove(uuid, id);
               return null;
            }
         }
      }
   }

   Iterable<MultiEntityTracker.State> states() {
      return this.byId.values();
   }

   int size() {
      return this.byId.size();
   }

   private static MultiEntityTracker.State copy(
      MultiEntityTracker.State old, Vec3 position, Vec3 movement, float yRot, float xRot, float headYRot, boolean onGround, int hookedId
   ) {
      return new MultiEntityTracker.State(old.id(), old.uuid(), old.type(), position, movement, yRot, xRot, headYRot, onGround, old.ownerId(), hookedId);
   }

   void remove(int id) {
      MultiEntityTracker.State removed = this.byId.remove(id);
      if (removed != null && removed.uuid() != null) {
         this.byUuid.remove(removed.uuid(), id);
      }
   }

   void clear() {
      this.byId.clear();
      this.byUuid.clear();
   }

   int nearest(String typeQuery, Vec3 from) {
      String q = normalize(typeQuery);
      int best = -1;
      double bestDist = Double.MAX_VALUE;

      for (MultiEntityTracker.State value : this.byId.values()) {
         if (q.isEmpty() || normalize(value.type()).contains(q)) {
            double distance = value.position().distanceToSqr(from);
            if (distance < bestDist) {
               bestDist = distance;
               best = value.id();
            }
         }
      }

      return best;
   }

   double[] pos(int id) {
      MultiEntityTracker.State value = this.byId.get(id);
      return value == null ? null : new double[]{value.position().x, value.position().y, value.position().z};
   }

   String typeOf(int id) {
      MultiEntityTracker.State value = this.byId.get(id);
      return value == null ? null : value.type();
   }

   boolean present(List<String> typeQueries, boolean containerOnly, double cx, double cy, double cz, double radius) {
      double radiusSq = radius * radius;
      ArrayList<String> queries = new ArrayList<>();
      if (typeQueries != null) {
         for (String query : typeQueries) {
            if (query != null && !query.isBlank() && !query.startsWith("~")) {
               queries.add(normalize(query));
            }
         }
      }

      for (MultiEntityTracker.State value : this.byId.values()) {
         Vec3 pos = value.position();
         double dx = pos.x - cx;
         double dy = pos.y - cy;
         double dz = pos.z - cz;
         if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
            String type = normalize(value.type());
            if (!containerOnly || type.contains("boat") || type.contains("minecart") || type.contains("llama") || type.contains("chest")) {
               if (queries.isEmpty()) {
                  return true;
               }

               for (String queryx : queries) {
                  if (type.contains(queryx)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static String normalize(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      int colon = normalized.indexOf(58);
      if (colon >= 0) {
         normalized = normalized.substring(colon + 1);
      }

      return normalized.replace('_', ' ');
   }

   record State(
      int id, UUID uuid, String type, Vec3 position, Vec3 movement, float yRot, float xRot, float headYRot, boolean onGround, int ownerId, int hookedId
   ) {
      PositionMoveRotation positionMoveRotation() {
         return new PositionMoveRotation(this.position, this.movement, this.yRot, this.xRot);
      }
   }
}
