package riptide.util.macro;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.PacketTeleportController;

public class HClipAction implements MacroAction {
   private static final int ESCAPE_SCAN_LIMIT = 128;
   private static final double LAND_SCAN_RANGE = 128.0;
   private static final double LAND_SCAN_STEP = 0.5;
   private static final double ADVANCE_STEP = 0.5;
   private static final boolean DEBUG_ROUTES = Boolean.getBoolean("riptide.hclip.debug");
   private static HClipAction.RouteCacheKey lastRouteKey;
   private static HClipAction.HClipPlan lastRoutePlan;
   private static long lastRouteTick = Long.MIN_VALUE;
   public HClipAction.Mode mode = HClipAction.Mode.MANUAL;
   public double blocks = 0.0;
   public boolean useSegmented = true;
   public int segmentBlocks = 10;
   public int maxPackets = 20;
   public boolean updateLocalPosition = true;
   public boolean tryVehicleFirst = true;
   public boolean forceGrounded = false;
   public int searchRadius = 32;
   public int verticalRange = 8;
   public int maxRoutePackets = 80;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      HClipAction.Options options = new HClipAction.Options();
      options.mode = this.mode;
      options.blocks = this.blocks;
      options.useSegmented = this.useSegmented;
      options.segmentBlocks = this.segmentBlocks;
      options.maxPackets = this.maxPackets;
      options.updateLocalPosition = this.updateLocalPosition;
      options.tryVehicleFirst = this.tryVehicleFirst;
      options.forceGrounded = this.forceGrounded;
      options.searchRadius = this.searchRadius;
      options.verticalRange = this.verticalRange;
      options.maxRoutePackets = this.maxRoutePackets;
      perform(mc, options);
   }

   public static HClipAction.Result perform(Minecraft mc, HClipAction.Options options) {
      if (options == null) {
         options = new HClipAction.Options();
      }

      if (mc != null && mc.player != null && mc.getConnection() != null) {
         LocalPlayer player = mc.player;
         double blocks = options.blocks;
         if (options.mode != HClipAction.Mode.MANUAL) {
            HClipAction.AutoHorizontalTarget target = resolveAutoHorizontalTarget(player, options);
            if (!target.success()) {
               if (options.showMessage) {
                  RiptideClientMessaging.sendPrefixed("§cHClip: " + target.message());
               }

               return new HClipAction.Result(false, 0, target.message());
            }

            blocks = target.blocks();
         }

         int segment = Math.max(1, options.segmentBlocks);
         int maxPaddingPackets = Math.max(1, options.maxPackets);
         int paddingPackets = options.useSegmented ? Math.max(0, (int)Math.ceil(Math.abs(blocks) / segment) - 1) : 0;
         if (paddingPackets + 1 > maxPaddingPackets) {
            paddingPackets = 0;
         }

         double yawRad = Math.toRadians(player.getYRot());
         double deltaX = -Math.sin(yawRad) * blocks;
         double deltaZ = Math.cos(yawRad) * blocks;
         HClipAction.HClipPlan plan = planRoute(player, deltaX, deltaZ, blocks, options);
         if (!plan.success()) {
            if (options.showMessage) {
               RiptideClientMessaging.sendPrefixed("§cHClip: " + plan.message());
            }

            return new HClipAction.Result(false, 0, plan.message());
         } else {
            Entity vehicle = options.tryVehicleFirst ? player.getVehicle() : null;
            int packetCount;
            if (vehicle != null) {
               if (plan.requiresVertical(player.getY())) {
                  String message = "planned route requires vertical clipping; vehicle hclip refused";
                  if (options.showMessage) {
                     RiptideClientMessaging.sendPrefixed("§cHClip: " + message);
                  }

                  return new HClipAction.Result(false, plan.waypoints().size(), message);
               }

               try {
                  for (int i = 0; i < paddingPackets; i++) {
                     sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
                  }

                  for (Vec3 waypoint : plan.waypoints()) {
                     vehicle.setPos(waypoint.x, waypoint.y, waypoint.z);
                     sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
                  }
               } catch (Throwable var26) {
                  String message = "Vehicle hclip failed: " + var26.getMessage();
                  if (options.showMessage) {
                     RiptideClientMessaging.sendPrefixed("§c" + message);
                  }

                  return new HClipAction.Result(false, paddingPackets + plan.waypoints().size(), message);
               }

               packetCount = paddingPackets + plan.waypoints().size();
            } else {
               boolean grounded = options.forceGrounded;
               int sent = 0;
               Vec3 current = player.position();
               clearLocalFallState(player);

               for (Vec3 waypoint : plan.waypoints()) {
                  if (options.useSegmented) {
                     double legDist = current.distanceTo(waypoint);
                     int legPadding = Math.max(0, (int)Math.ceil(legDist / segment) - 1);
                     if (legPadding > maxPaddingPackets - 1) {
                        legPadding = Math.max(0, maxPaddingPackets - 1);
                     }

                     for (int i = 0; i < legPadding; i++) {
                        sendPacket(mc, new StatusOnly(grounded, false));
                     }

                     sent += legPadding;
                  }

                  sent += sendWaypointFallSafe(mc, current, waypoint, grounded);
                  current = waypoint;
               }

               Vec3 finalPos = plan.waypoints().getLast();
               if (options.updateLocalPosition) {
                  player.setPos(finalPos.x, finalPos.y, finalPos.z);
                  clearLocalFallState(player);
               }

               packetCount = sent;
            }

            String prefix = options.mode == HClipAction.Mode.MANUAL
               ? "hclip " + blocks
               : "hclip " + options.mode.name().toLowerCase(Locale.ROOT) + " -> " + String.format(Locale.ROOT, "%.2f", blocks);
            String message = prefix + " (" + packetCount + " packet" + (packetCount == 1 ? "" : "s") + ")";
            if (DEBUG_ROUTES && plan.success()) {
               message = message + ", " + plan.message();
            }

            if (options.showMessage) {
               RiptideClientMessaging.sendPrefixed("§a" + message);
            }

            return new HClipAction.Result(true, packetCount, message);
         }
      } else {
         if (options.showMessage) {
            RiptideClientMessaging.sendPrefixed("§cHClip: no world / connection.");
         }

         return new HClipAction.Result(false, 0, "No world / connection");
      }
   }

   private static int sendWaypointFallSafe(Minecraft mc, Vec3 from, Vec3 to, boolean grounded) {
      List<PacketClipSafety.Step> steps = PacketClipSafety.positionSteps(from, to, grounded);

      for (PacketClipSafety.Step step : steps) {
         sendPacket(mc, new Pos(step.position(), step.onGround(), false));
      }

      return steps.size();
   }

   private static void sendPacket(Minecraft mc, Packet<?> packet) {
      PacketTeleportController.runAtomicClipSend(() -> mc.getConnection().send(packet));
   }

   private static void clearLocalFallState(LocalPlayer player) {
      player.resetFallDistance();
      Vec3 velocity = player.getDeltaMovement();
      if (velocity.y < 0.0) {
         player.setDeltaMovement(velocity.x, 0.0, velocity.z);
      }
   }

   private static HClipAction.HClipPlan planRoute(LocalPlayer player, double deltaX, double deltaZ, double blocks, HClipAction.Options options) {
      Vec3 start = player.position();
      Vec3 requestedTarget = start.add(deltaX, 0.0, deltaZ);
      int radius = Math.max(1, options.searchRadius);
      int verticalRange = Math.max(0, options.verticalRange);
      int escapeRange = Math.max(verticalRange, 128);
      int routeCap = Math.max(1, Math.min(200, options.maxRoutePackets));
      HClipAction.RouteCacheKey cacheKey = HClipAction.RouteCacheKey.create(player, start, requestedTarget, radius, verticalRange, routeCap);
      long tick = player.level() == null ? Long.MIN_VALUE : player.level().getGameTime();
      if (lastRouteKey != null && lastRouteKey.equals(cacheKey) && lastRoutePlan != null && tick - lastRouteTick <= 2L) {
         return lastRoutePlan;
      } else {
         PacketRoutePlanner.Route route = PacketRoutePlanner.planHorizontal(
            PacketRoutePlanner.forEntity(player), start, requestedTarget, radius, escapeRange, routeCap, false
         );
         return !route.complete()
            ? HClipAction.HClipPlan.fail(route.detail())
            : rememberRoute(cacheKey, tick, HClipAction.HClipPlan.ok(start.y, route.waypoints(), route.detail()));
      }
   }

   private static HClipAction.HClipPlan rememberRoute(HClipAction.RouteCacheKey key, long tick, HClipAction.HClipPlan plan) {
      if (plan.success()) {
         lastRouteKey = key;
         lastRoutePlan = plan;
         lastRouteTick = tick;
      }

      return plan;
   }

   private static HClipAction.HClipPlan planLayered(LocalPlayer player, Vec3 start, Vec3 target, int escapeRange, int routeCap) {
      for (int dy : verticalOffsets(escapeRange)) {
         if (dy != 0) {
            Vec3 escStart = new Vec3(start.x, start.y + dy, start.z);
            Vec3 escEnd = new Vec3(target.x, start.y + dy, target.z);
            if (isPositionLoaded(player, escStart)
               && isPositionClear(player, escStart)
               && isPositionLoaded(player, escEnd)
               && isPositionClear(player, escEnd)
               && hasClearHorizontalPath(player, escStart, escEnd)) {
               List<Vec3> waypoints = new ArrayList<>(3);
               waypoints.add(escStart);
               waypoints.add(escEnd);
               Vec3 landing = findLanding(player, target.x, target.z, start.y);
               if (landing != null && !samePosition(landing, escEnd)) {
                  waypoints.add(landing);
               }

               List<Vec3> route = cleanupRoute(start, waypoints);
               if (!route.isEmpty() && route.size() <= routeCap) {
                  return HClipAction.HClipPlan.ok(start.y, route, "layered dy=" + dy);
               }
            }
         }
      }

      return HClipAction.HClipPlan.fail("no clear layer");
   }

   private static HClipAction.HClipPlan planSegmented(LocalPlayer player, Vec3 start, Vec3 target, int escapeRange, int searchRadius, int routeCap) {
      List<Vec3> route = new ArrayList<>();
      Vec3 cur = start;
      int guard = 0;

      label62:
      while (horizontalDist(cur, target) > 0.5 && guard++ < routeCap) {
         Vec3 adv = maxClearAdvance(player, cur, target, searchRadius);
         if (horizontalDist(adv, cur) > 0.5) {
            route.add(adv);
            cur = adv;
         } else {
            boolean escaped = false;
            Iterator var11 = verticalOffsets(escapeRange).iterator();

            while (true) {
               if (var11.hasNext()) {
                  int dy = (Integer)var11.next();
                  if (dy == 0) {
                     continue;
                  }

                  Vec3 layer = new Vec3(cur.x, cur.y + dy, cur.z);
                  if (!isPositionLoaded(player, layer) || !isPositionClear(player, layer)) {
                     continue;
                  }

                  Vec3 adv2 = maxClearAdvance(player, layer, target, searchRadius);
                  if (!(horizontalDist(adv2, layer) > 1.0)) {
                     continue;
                  }

                  route.add(layer);
                  route.add(adv2);
                  cur = adv2;
                  escaped = true;
               }

               if (!escaped) {
                  break label62;
               }
               break;
            }
         }
      }

      if (route.isEmpty()) {
         return HClipAction.HClipPlan.fail("segmented made no progress");
      } else {
         Vec3 landing = findLanding(player, cur.x, cur.z, start.y);
         if (landing != null && !samePosition(landing, cur)) {
            route.add(landing);
         }

         List<Vec3> cleaned = cleanupRoute(start, route);
         if (cleaned.isEmpty()) {
            return HClipAction.HClipPlan.fail("segmented empty route");
         } else {
            return cleaned.size() > routeCap
               ? HClipAction.HClipPlan.fail("segmented route needs " + cleaned.size() + " packets, cap is " + routeCap)
               : HClipAction.HClipPlan.ok(start.y, cleaned, "segmented");
         }
      }
   }

   private static Vec3 findLanding(LocalPlayer player, double x, double z, double preferredY) {
      for (double off = 0.0; off <= 128.0; off += 0.5) {
         int[] signs = off == 0.0 ? new int[]{1} : new int[]{1, -1};

         for (int sign : signs) {
            Vec3 candidate = new Vec3(x, preferredY + sign * off, z);
            if (isPositionLoaded(player, candidate) && isPositionClear(player, candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   private static Vec3 maxClearAdvance(LocalPlayer player, Vec3 from, Vec3 target, int searchRadius) {
      double dx = target.x - from.x;
      double dz = target.z - from.z;
      double dist = Math.hypot(dx, dz);
      if (dist < 1.0E-5) {
         return from;
      } else {
         double maxDist = Math.min(dist, (double)searchRadius);
         double ux = dx / dist;
         double uz = dz / dist;
         Vec3 best = from;

         for (double d = 0.5; d <= maxDist + 1.0E-9; d += 0.5) {
            Vec3 candidate = new Vec3(from.x + ux * d, from.y, from.z + uz * d);
            if (!isPositionLoaded(player, candidate) || !isPositionClear(player, candidate)) {
               break;
            }

            best = candidate;
         }

         return best;
      }
   }

   private static HClipAction.AutoHorizontalTarget resolveAutoHorizontalTarget(LocalPlayer player, HClipAction.Options options) {
      int direction = options.mode == HClipAction.Mode.BACK ? -1 : 1;
      int radius = Math.max(1, Math.min(128, options.searchRadius));
      double yawRad = Math.toRadians(player.getYRot());
      double dirX = -Math.sin(yawRad) * direction;
      double dirZ = Math.cos(yawRad) * direction;
      Vec3 start = player.position();
      boolean seenBlocked = false;
      int firstBlocked = 0;
      int lastBlocked = 0;

      for (int distance = 1; distance <= radius; distance++) {
         Vec3 candidate = new Vec3(start.x + dirX * distance, start.y, start.z + dirZ * distance);
         boolean clear = isPositionLoaded(player, candidate) && isPositionClear(player, candidate);
         if (!clear) {
            if (!seenBlocked) {
               firstBlocked = distance;
            }

            lastBlocked = distance;
            seenBlocked = true;
         } else if (seenBlocked) {
            return new HClipAction.AutoHorizontalTarget(true, (double)(direction * distance), "past obstruction");
         }
      }

      if (seenBlocked) {
         int requested = Math.min(radius, Math.max(firstBlocked + 1, lastBlocked + 1));
         return new HClipAction.AutoHorizontalTarget(true, (double)(direction * requested), "route around obstruction");
      } else {
         return new HClipAction.AutoHorizontalTarget(
            false, 0.0, options.mode == HClipAction.Mode.BACK ? "no blocking run behind you" : "no blocking run in front of you"
         );
      }
   }

   private static List<Integer> verticalOffsets(int verticalRange) {
      List<Integer> offsets = new ArrayList<>(verticalRange * 2 + 1);
      offsets.add(0);

      for (int i = 1; i <= verticalRange; i++) {
         offsets.add(i);
         offsets.add(-i);
      }

      return offsets;
   }

   private static List<Vec3> cleanupRoute(Vec3 start, List<Vec3> route) {
      List<Vec3> cleaned = new ArrayList<>(route.size());
      Vec3 previous = start;

      for (Vec3 waypoint : route) {
         if (!samePosition(previous, waypoint)) {
            cleaned.add(waypoint);
            previous = waypoint;
         }
      }

      return cleaned;
   }

   private static boolean samePosition(Vec3 a, Vec3 b) {
      return Math.abs(a.x - b.x) < 1.0E-5 && Math.abs(a.y - b.y) < 1.0E-5 && Math.abs(a.z - b.z) < 1.0E-5;
   }

   private static double horizontalDist(Vec3 a, Vec3 b) {
      return Math.hypot(a.x - b.x, a.z - b.z);
   }

   private static boolean isPositionLoaded(LocalPlayer player, Vec3 pos) {
      return player != null && player.level() != null && player.level().isLoaded(BlockPos.containing(pos));
   }

   private static boolean hasClearHorizontalPath(LocalPlayer player, Vec3 from, Vec3 to) {
      if (Math.abs(from.x - to.x) < 1.0E-5 && Math.abs(from.z - to.z) < 1.0E-5) {
         return true;
      } else if (Math.abs(from.y - to.y) > 1.0E-5) {
         return false;
      } else {
         double distance = from.distanceTo(to);
         int steps = Math.max(1, (int)Math.ceil(distance / 0.25));

         for (int i = 1; i <= steps; i++) {
            double t = (double)i / steps;
            Vec3 pos = from.lerp(to, t);
            if (!isPositionLoaded(player, pos) || !isPositionClear(player, pos)) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean isPositionClear(LocalPlayer player, Vec3 pos) {
      Vec3 delta = pos.subtract(player.position());
      return player.level().noCollision(player, player.getBoundingBox().move(delta));
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("mode", this.mode.name());
      tag.putDouble("blocks", this.blocks);
      tag.putBoolean("useSegmented", this.useSegmented);
      tag.putInt("segmentBlocks", Math.max(1, this.segmentBlocks));
      tag.putInt("maxPackets", Math.max(1, this.maxPackets));
      tag.putBoolean("updateLocalPosition", this.updateLocalPosition);
      tag.putBoolean("tryVehicleFirst", this.tryVehicleFirst);
      tag.putBoolean("forceGrounded", this.forceGrounded);
      tag.putInt("searchRadius", Math.max(1, this.searchRadius));
      tag.putInt("verticalRange", Math.max(0, this.verticalRange));
      tag.putInt("maxRoutePackets", Math.max(1, Math.min(200, this.maxRoutePackets)));
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.mode = parseMode(tag.getStringOr("mode", HClipAction.Mode.MANUAL.name()));
      this.blocks = tag.getDoubleOr("blocks", 0.0);
      this.useSegmented = tag.getBooleanOr("useSegmented", true);
      this.segmentBlocks = Math.max(1, tag.getIntOr("segmentBlocks", 10));
      this.maxPackets = Math.max(1, tag.getIntOr("maxPackets", 20));
      this.updateLocalPosition = tag.getBooleanOr("updateLocalPosition", true);
      this.tryVehicleFirst = tag.getBooleanOr("tryVehicleFirst", true);
      this.forceGrounded = tag.getBooleanOr("forceGrounded", false);
      this.searchRadius = Math.max(1, tag.getIntOr("searchRadius", 32));
      this.verticalRange = Math.max(0, tag.getIntOr("verticalRange", 8));
      this.maxRoutePackets = Math.max(1, Math.min(200, tag.contains("maxRoutePackets") ? tag.getIntOr("maxRoutePackets", 80) : tag.getIntOr("maxPackets", 80)));
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.HCLIP;
   }

   @Override
   public String getDisplayName() {
      if (this.mode == HClipAction.Mode.FORWARD) {
         return "HClip Forward";
      } else {
         return this.mode == HClipAction.Mode.BACK ? "HClip Back" : "HClip " + String.format(Locale.ROOT, "%.2f", this.blocks);
      }
   }

   @Override
   public String getIcon() {
      return "HC";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private static HClipAction.Mode parseMode(String value) {
      try {
         return HClipAction.Mode.valueOf(value == null ? HClipAction.Mode.MANUAL.name() : value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return HClipAction.Mode.MANUAL;
      }
   }

   private record AutoHorizontalTarget(boolean success, double blocks, String message) {
   }

   private record HClipPlan(boolean success, double originY, List<Vec3> waypoints, String message) {
      static HClipAction.HClipPlan ok(double originY, List<Vec3> waypoints) {
         return ok(originY, waypoints, "ok");
      }

      static HClipAction.HClipPlan ok(double originY, List<Vec3> waypoints, String message) {
         return new HClipAction.HClipPlan(true, originY, List.copyOf(waypoints), message);
      }

      static HClipAction.HClipPlan fail(String message) {
         return new HClipAction.HClipPlan(false, 0.0, List.of(), message);
      }

      boolean requiresVertical(double fallbackOriginY) {
         if (this.waypoints.isEmpty()) {
            return false;
         } else {
            double y = this.originY == 0.0 ? fallbackOriginY : this.originY;

            for (Vec3 waypoint : this.waypoints) {
               if (Math.abs(waypoint.y - y) > 1.0E-5) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public static enum Mode {
      MANUAL,
      FORWARD,
      BACK;
   }

   public static final class Options {
      public HClipAction.Mode mode = HClipAction.Mode.MANUAL;
      public double blocks = 0.0;
      public boolean useSegmented = true;
      public int segmentBlocks = 10;
      public int maxPackets = 20;
      public boolean updateLocalPosition = true;
      public boolean tryVehicleFirst = true;
      public boolean forceGrounded = false;
      public int searchRadius = 32;
      public int verticalRange = 8;
      public int maxRoutePackets = 80;
      public boolean showMessage = false;

      public static HClipAction.Options defaults(double blocks) {
         HClipAction.Options options = new HClipAction.Options();
         options.blocks = blocks;
         return options;
      }

      public HClipAction.Options singlePacket() {
         this.useSegmented = false;
         return this;
      }
   }

   public record Result(boolean success, int packetsRequired, String message) {
   }

   private record RouteCacheKey(
      int levelId, long startX, long startY, long startZ, long targetX, long targetY, long targetZ, int radius, int verticalRange, int routeCap
   ) {
      static HClipAction.RouteCacheKey create(LocalPlayer player, Vec3 start, Vec3 target, int radius, int verticalRange, int routeCap) {
         int levelId = player.level() == null ? 0 : System.identityHashCode(player.level());
         return new HClipAction.RouteCacheKey(
            levelId,
            quantize(start.x),
            quantize(start.y),
            quantize(start.z),
            quantize(target.x),
            quantize(target.y),
            quantize(target.z),
            radius,
            verticalRange,
            routeCap
         );
      }

      private static long quantize(double value) {
         return Math.round(value * 64.0);
      }
   }
}
