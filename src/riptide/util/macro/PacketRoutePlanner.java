package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PacketRoutePlanner {
   private static final double PATH_SAMPLE = 0.25;
   private static final double ADVANCE_SAMPLE = 0.5;
   private static final double HYBRID_PATH_SAMPLE = 0.5;
   private static final double HYBRID_ANCHOR_SAMPLE = 0.25;
   private static final double HYBRID_MIN_PROGRESS = 0.5;
   private static final int HYBRID_MAX_LEGS = 3;
   private static final int HYBRID_PROBE_BUDGET = 1800;
   private static final int HYBRID_TOP_PROBE_BUDGET = 1500;
   private static final int HYBRID_MAX_FRONTIER = 128;
   private static final double HYBRID_MAX_ANCHOR_DOWN = 128.0;
   private static final double HYBRID_MAX_ANCHOR_UP = 64.0;
   private static final double LAVA_HARD_HORIZONTAL = 1.5;
   private static final double LAVA_HARD_VERTICAL = 1.0;
   private static final double LAVA_SOFT_SCAN = 6.0;

   private PacketRoutePlanner() {
   }

   public static PacketRoutePlanner.CollisionView forEntity(final Entity entity) {
      if (entity != null && entity.level() != null) {
         final Vec3 origin = entity.position();
         final AABB bounds = entity.getBoundingBox();
         final double height = bounds.getYsize();
         return new PacketRoutePlanner.CollisionView() {
            private final Map<Long, Boolean> lavaBlocks = new HashMap<>();

            @Override
            public Entity entity() {
               return entity;
            }

            private boolean lava(BlockPos position) {
               long key = position.asLong();
               Boolean cached = this.lavaBlocks.get(key);
               if (cached != null) {
                  return cached;
               } else {
                  boolean lava = entity.level().getFluidState(position).is(FluidTags.LAVA);
                  this.lavaBlocks.put(key, lava);
                  return lava;
               }
            }

            @Override
            public boolean loaded(Vec3 position) {
               return position != null && entity.level().isLoaded(BlockPos.containing(position));
            }

            @Override
            public boolean clear(Vec3 position) {
               if (position != null && !(position.y < this.minY()) && !(position.y > this.maxFeetY())) {
                  AABB moved = bounds.move(position.subtract(origin));
                  return entity.level().noCollision(entity, moved);
               } else {
                  return false;
               }
            }

            @Override
            public boolean supported(Vec3 position) {
               if (!this.clear(position)) {
                  return false;
               } else {
                  AABB moved = bounds.move(position.subtract(origin));
                  return !entity.level().noCollision(entity, moved.move(0.0, -0.0625, 0.0));
               }
            }

            @Override
            public boolean breathableWater(Vec3 position) {
               if (!this.clear(position)) {
                  return false;
               } else {
                  double feetY = position.y;
                  double eyeY = feetY + entity.getEyeHeight();
                  double surface = PacketRoutePlanner.waterSurface(entity, position.x, position.z, feetY, eyeY);
                  if (!Double.isFinite(surface)) {
                     return false;
                  } else {
                     double depth = surface - feetY;
                     boolean eyeSubmerged = PacketRoutePlanner.pointInWater(entity, position.x, eyeY - 0.05, position.z);
                     return PacketRoutePlanner.breathableWaterDepth(bounds.getYsize(), entity.getEyeHeight(), depth, eyeSubmerged)
                        && PacketRoutePlanner.pointInWater(entity, position.x, feetY + 0.1, position.z)
                        && PacketRoutePlanner.pointInWater(entity, position.x, feetY + Math.min(0.8, bounds.getYsize() * 0.48), position.z);
                  }
               }
            }

            @Override
            public boolean lavaSafe(Vec3 position) {
               return position != null && PacketRoutePlanner.lavaSafeAt(entity, bounds.move(position.subtract(origin)), this::lava);
            }

            @Override
            public double lavaClearance(Vec3 position) {
               return position == null ? 0.0 : PacketRoutePlanner.lavaClearanceAt(entity, bounds.move(position.subtract(origin)), 6.0, this::lava);
            }

            @Override
            public double minY() {
               return entity.level().getMinY();
            }

            @Override
            public double maxFeetY() {
               return entity.level().getMaxY() + 1.0 - height;
            }
         };
      } else {
         return null;
      }
   }

   static boolean breathableWaterDepth(double entityHeight, double eyeHeight, double waterDepth, boolean eyeSubmerged) {
      if (!eyeSubmerged && Double.isFinite(waterDepth)) {
         double minDepth = Math.min(0.85, Math.max(0.45, entityHeight * 0.45));
         double maxDepth = Math.max(minDepth + 0.1, Math.min(Math.max(0.0, eyeHeight - 0.3), entityHeight * 0.68));
         return waterDepth >= minDepth && waterDepth <= maxDepth;
      } else {
         return false;
      }
   }

   private static double waterSurface(Entity entity, double x, double z, double feetY, double eyeY) {
      if (entity != null && entity.level() != null) {
         double highest = Double.NaN;
         int min = (int)Math.floor(feetY);
         int max = (int)Math.floor(eyeY);

         for (int y = min; y <= max; y++) {
            BlockPos block = BlockPos.containing(x, y, z);
            FluidState fluid = entity.level().getFluidState(block);
            if (fluid.is(FluidTags.WATER)) {
               double surface = block.getY() + fluid.getHeight(entity.level(), block);
               if (!Double.isFinite(highest) || surface > highest) {
                  highest = surface;
               }
            }
         }

         return highest;
      } else {
         return Double.NaN;
      }
   }

   private static boolean pointInWater(Entity entity, double x, double y, double z) {
      if (entity != null && entity.level() != null) {
         BlockPos block = BlockPos.containing(x, y, z);
         FluidState fluid = entity.level().getFluidState(block);
         return fluid.is(FluidTags.WATER) && y < block.getY() + fluid.getHeight(entity.level(), block) - 1.0E-4;
      } else {
         return false;
      }
   }

   private static boolean lavaSafeAt(Entity entity, AABB body, Predicate<BlockPos> lavaAt) {
      if (entity != null && entity.level() != null && body != null) {
         AABB shell = new AABB(body.minX - 1.5, body.minY - 1.0, body.minZ - 1.5, body.maxX + 1.5, body.maxY + 1.0, body.maxZ + 1.5);
         MutableBlockPos cursor = new MutableBlockPos();
         int minY = Math.max(floor(shell.minY), entity.level().getMinY());
         int maxY = Math.min(ceil(shell.maxY), entity.level().getMaxY() + 1);

         for (int x = floor(shell.minX); x < ceil(shell.maxX); x++) {
            for (int z = floor(shell.minZ); z < ceil(shell.maxZ); z++) {
               for (int y = minY; y < maxY; y++) {
                  cursor.set(x, y, z);
                  if (!entity.level().isLoaded(cursor)) {
                     return false;
                  }

                  if (lavaAt.test(cursor)) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static double lavaClearanceAt(Entity entity, AABB body, double scanRadius, Predicate<BlockPos> lavaAt) {
      if (entity != null && entity.level() != null && body != null) {
         double radius = Math.max(0.0, scanRadius);
         AABB scan = new AABB(body.minX - radius, body.minY - radius, body.minZ - radius, body.maxX + radius, body.maxY + radius, body.maxZ + radius);
         double bestSqr = Double.POSITIVE_INFINITY;
         MutableBlockPos cursor = new MutableBlockPos();
         int minY = Math.max(floor(scan.minY), entity.level().getMinY());
         int maxY = Math.min(ceil(scan.maxY), entity.level().getMaxY() + 1);

         for (int x = floor(scan.minX); x < ceil(scan.maxX); x++) {
            for (int z = floor(scan.minZ); z < ceil(scan.maxZ); z++) {
               for (int y = minY; y < maxY; y++) {
                  cursor.set(x, y, z);
                  if (!entity.level().isLoaded(cursor)) {
                     return 0.0;
                  }

                  if (lavaAt.test(cursor)) {
                     double dx = axisGap(body.minX, body.maxX, x, x + 1.0);
                     double dy = axisGap(body.minY, body.maxY, y, y + 1.0);
                     double dz = axisGap(body.minZ, body.maxZ, z, z + 1.0);
                     bestSqr = Math.min(bestSqr, dx * dx + dy * dy + dz * dz);
                  }
               }
            }
         }

         return Double.isFinite(bestSqr) ? Math.sqrt(bestSqr) : Double.POSITIVE_INFINITY;
      } else {
         return 0.0;
      }
   }

   private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
      if (firstMax < secondMin) {
         return secondMin - firstMax;
      } else {
         return secondMax < firstMin ? firstMin - secondMax : 0.0;
      }
   }

   private static int floor(double value) {
      return (int)Math.floor(value);
   }

   private static int ceil(double value) {
      return (int)Math.ceil(value);
   }

   public static PacketRoutePlanner.Route planHorizontal(
      PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int searchRadius, int verticalRange, int maxWaypoints, boolean allowPartial
   ) {
      if (view != null && start != null && target != null) {
         int radius = Math.max(1, Math.min(128, searchRadius));
         int escapeRange = Math.max(0, Math.min(128, verticalRange));
         int routeCap = Math.max(1, Math.min(512, maxWaypoints));
         Vec3 horizontalTarget = new Vec3(target.x, start.y, target.z);
         if (safeClear(view, horizontalTarget) && clearHorizontalPath(view, start, horizontalTarget)) {
            return complete(clean(start, List.of(horizontalTarget)), "direct");
         } else {
            PacketRoutePlanner.Route layered = layered(view, start, horizontalTarget, escapeRange, routeCap);
            if (layered.complete()) {
               return layered;
            } else {
               List<Vec3> route = new ArrayList<>();
               Vec3 current = start;
               boolean unloadedFrontier = false;
               int guard = 0;

               label104:
               while (horizontalDistance(current, horizontalTarget) > 0.5 && guard++ < routeCap) {
                  Vec3 advance = maxClearAdvance(view, current, horizontalTarget, radius);
                  if (horizontalDistance(advance, current) > 0.5) {
                     route.add(advance);
                     current = advance;
                  } else {
                     Vec3 next = toward(current, horizontalTarget, 0.5);
                     if (!view.loaded(next)) {
                        unloadedFrontier = true;
                        break;
                     }

                     boolean escaped = false;
                     Iterator var19 = verticalOffsets(escapeRange).iterator();

                     while (true) {
                        if (var19.hasNext()) {
                           int dy = (Integer)var19.next();
                           if (dy == 0) {
                              continue;
                           }

                           Vec3 layer = new Vec3(current.x, clampY(view, current.y + dy), current.z);
                           if (!safeClear(view, layer) || !safeLavaPath(view, current, layer)) {
                              continue;
                           }

                           Vec3 layerAdvance = maxClearAdvance(view, layer, horizontalTarget, radius);
                           if (horizontalDistance(layerAdvance, layer) <= 1.0) {
                              continue;
                           }

                           route.add(layer);
                           route.add(layerAdvance);
                           current = layerAdvance;
                           escaped = true;
                        }

                        if (!escaped) {
                           break label104;
                        }
                        break;
                     }
                  }
               }

               boolean reached = horizontalDistance(current, horizontalTarget) <= 0.5;
               if (reached) {
                  Vec3 landing = nearestClearAtColumn(view, current.x, current.z, start.y, 128.0);
                  if (landing != null && !same(landing, current) && safeLavaPath(view, current, landing)) {
                     route.add(landing);
                  }
               }

               List<Vec3> cleaned = clean(start, route);
               if (reached && !cleaned.isEmpty()) {
                  return complete(cleaned, "segmented");
               } else if (allowPartial && !cleaned.isEmpty()) {
                  return new PacketRoutePlanner.Route(
                     PacketRoutePlanner.State.PARTIAL, cleaned, unloadedFrontier ? "loaded frontier" : "route stopped before target"
                  );
               } else {
                  return unloadedFrontier
                     ? blocked("loaded frontier unavailable")
                     : blocked(cleaned.isEmpty() ? "no clear route" : "route stopped before target");
               }
            }
         }
      } else {
         return blocked("no collision view");
      }
   }

   public static PacketRoutePlanner.Route planToward(
      PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int frontierRadius, int verticalRange, int maxWaypoints
   ) {
      if (view != null && start != null && target != null) {
         if (start.distanceTo(target) <= 0.05) {
            return new PacketRoutePlanner.Route(PacketRoutePlanner.State.COMPLETE, List.of(), "arrived");
         } else if (!(Math.hypot(target.x - start.x, target.z - start.z) <= 0.05)) {
            PacketRoutePlanner.Route horizontal = planHorizontal(
               view, start, new Vec3(target.x, start.y, target.z), frontierRadius, verticalRange, maxWaypoints, true
            );
            if (horizontal.state() == PacketRoutePlanner.State.BLOCKED) {
               return horizontal;
            } else {
               List<Vec3> route = new ArrayList<>(horizontal.waypoints());
               Vec3 end = route.isEmpty() ? start : route.getLast();
               boolean atTargetColumn = Math.hypot(end.x - target.x, end.z - target.z) <= 0.5;
               if (!atTargetColumn || !safeClear(view, target)) {
                  return new PacketRoutePlanner.Route(PacketRoutePlanner.State.PARTIAL, clean(start, route), horizontal.detail());
               } else if (!safeLavaPath(view, end, target)) {
                  return new PacketRoutePlanner.Route(PacketRoutePlanner.State.PARTIAL, clean(start, route), "lava-safe target approach unavailable");
               } else {
                  if (!same(end, target)) {
                     route.add(target);
                  }

                  return complete(clean(start, route), "target column");
               }
            }
         } else {
            return !safeClear(view, target) ? blocked("target unsafe or unavailable") : complete(List.of(target), "vertical");
         }
      } else {
         return blocked("no collision view");
      }
   }

   public static PacketRoutePlanner.Route planGroundToward(PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int maxWaypoints) {
      return planHybridToward(view, start, target, 128, 128, Math.min(3, Math.max(1, maxWaypoints)));
   }

   public static PacketRoutePlanner.Route planGroundFirst(
      PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int frontierRadius, int verticalRange, int maxWaypoints
   ) {
      return planHybridToward(view, start, target, frontierRadius, verticalRange, maxWaypoints);
   }

   public static PacketRoutePlanner.Route planHybridToward(
      PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int frontierRadius, int verticalRange, int maxWaypoints
   ) {
      if (view != null && start != null && target != null) {
         if (start.distanceTo(target) <= 0.05) {
            return new PacketRoutePlanner.Route(PacketRoutePlanner.State.COMPLETE, List.of(), "arrived");
         } else {
            PacketRoutePlanner.HybridProbe probe = new PacketRoutePlanner.HybridProbe(view, 1800);
            if (!(horizontalDistance(start, target) <= 0.05)) {
               double radius = Math.max(1.0, (double)Math.min(128, frontierRadius));
               Vec3 frontier = farthestLoadedFrontier(probe, start, target, Math.min(horizontalDistance(start, target), radius));
               if (frontier != null && !(horizontalDistance(start, frontier) < 0.5)) {
                  boolean targetColumn = horizontalDistance(frontier, target) <= 0.05;
                  double preferredY = targetColumn ? target.y : start.y;
                  double range = Math.max(0.0, Math.min(128.0, (double)verticalRange));
                  Vec3 anchor = findHybridAnchor(probe, frontier.x, frontier.z, preferredY, Math.min(128.0, range), Math.min(64.0, range));
                  if (anchor != null) {
                     List<Vec3> anchored = routeToStableAnchor(probe, start, anchor, target.y, range, false);
                     PacketRoutePlanner.Route route = finishHybridRoute(probe, start, target, anchored, targetColumn, "hybrid stable anchor");
                     if (route.madeProgress()) {
                        return route;
                     }
                  }

                  PacketRoutePlanner.HybridProbe topProbe = new PacketRoutePlanner.HybridProbe(view, 1500);
                  Vec3 topExit = findTopLanding(topProbe, start, range);
                  List<Vec3> topAdvance = topAdvance(topProbe, start, frontier, topExit);
                  if (!topAdvance.isEmpty()) {
                     List<Vec3> topLanding = new ArrayList<>(topAdvance);
                     if (anchor != null && topLanding.size() < 3 && !same(topLanding.getLast(), anchor)) {
                        topLanding.add(anchor);
                     }

                     PacketRoutePlanner.Route topRoute = finishHybridRoute(topProbe, start, target, topLanding, targetColumn, "VClip Top escape");
                     if (topRoute.madeProgress()) {
                        return topRoute;
                     }
                  }

                  if (topExit != null && safeLavaPath(topProbe, start, topExit)) {
                     return new PacketRoutePlanner.Route(PacketRoutePlanner.State.ESCAPE, clean(start, List.of(topExit)), "VClip Top preparatory escape");
                  } else {
                     if (anchor != null) {
                        List<Vec3> layeredAnchor = routeToStableAnchor(probe, start, anchor, target.y, range, true);
                        PacketRoutePlanner.Route layeredRoute = finishHybridRoute(probe, start, target, layeredAnchor, targetColumn, "extended V/H/V anchor");
                        if (layeredRoute.madeProgress()) {
                           return layeredRoute;
                        }
                     }

                     List<Vec3> airborne = routeToAirFrontier(probe, start, frontier, target, targetColumn, range);
                     PacketRoutePlanner.Route airRoute = finishHybridRoute(probe, start, target, airborne, targetColumn, "air fallback");
                     if (airRoute.madeProgress()) {
                        return airRoute;
                     } else {
                        Vec3 advance = farthestClearAdvance(probe, start, frontier);
                        if (advance != null && horizontalDistance(start, advance) >= 0.5) {
                           Vec3 localAnchor = findHybridAnchor(probe, advance.x, advance.z, start.y, Math.min(16.0, range), Math.min(8.0, range));
                           if (localAnchor != null) {
                              List<Vec3> localRoute = routeToStableAnchor(probe, start, localAnchor, target.y, Math.min(16.0, range));
                              if (!localRoute.isEmpty()) {
                                 return new PacketRoutePlanner.Route(PacketRoutePlanner.State.PARTIAL, localRoute, "stable obstacle frontier");
                              }
                           }

                           return new PacketRoutePlanner.Route(PacketRoutePlanner.State.PARTIAL, clean(start, List.of(advance)), "air obstacle frontier");
                        } else {
                           return unrestrictedClipRoute(
                              view, start, target, radius, probe.exhausted() ? "probe-budget unrestricted fallback" : "unrestricted clip fallback"
                           );
                        }
                     }
                  }
               } else {
                  return unrestrictedClipRoute(view, start, target, radius, "unloaded safety wait");
               }
            } else {
               return !probe.safeClear(target) ? blocked("lava-safe vertical target unavailable") : complete(List.of(target), "VClip target");
            }
         }
      } else {
         return blocked("no collision view");
      }
   }

   public static Vec3 nearestClear(PacketRoutePlanner.CollisionView view, Vec3 target, double radius) {
      if (view != null && target != null && view.loaded(target)) {
         if (safeClear(view, target)) {
            return target;
         } else {
            double limit = Math.max(0.0, radius);
            Vec3 best = null;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (double dy = -limit; dy <= limit + 1.0E-9; dy += 0.5) {
               for (double dx = -limit; dx <= limit + 1.0E-9; dx += 0.5) {
                  for (double dz = -limit; dz <= limit + 1.0E-9; dz += 0.5) {
                     double distance = dx * dx + dy * dy + dz * dz;
                     if (!(distance > limit * limit + 1.0E-9) && !(distance >= bestDistance)) {
                        Vec3 candidate = new Vec3(target.x + dx, clampY(view, target.y + dy), target.z + dz);
                        if (safeClear(view, candidate)) {
                           best = candidate;
                           bestDistance = distance;
                        }
                     }
                  }
               }
            }

            return best;
         }
      } else {
         return null;
      }
   }

   public static Vec3 nearestGrounded(PacketRoutePlanner.CollisionView view, Vec3 target, double radius) {
      if (view != null && target != null && view.loaded(target)) {
         if (safeClear(view, target) && view.traversable(target)) {
            return target;
         } else {
            double limit = Math.max(0.0, radius);
            Vec3 best = null;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (double dy = -limit; dy <= limit + 1.0E-9; dy += 0.0625) {
               for (double dx = -limit; dx <= limit + 1.0E-9; dx += 0.5) {
                  for (double dz = -limit; dz <= limit + 1.0E-9; dz += 0.5) {
                     double distance = dx * dx + dy * dy + dz * dz;
                     if (!(distance > limit * limit + 1.0E-9) && !(distance >= bestDistance)) {
                        Vec3 candidate = new Vec3(target.x + dx, clampY(view, target.y + dy), target.z + dz);
                        if (safeClear(view, candidate) && view.traversable(candidate)) {
                           best = candidate;
                           bestDistance = distance;
                        }
                     }
                  }
               }
            }

            return best;
         }
      } else {
         return null;
      }
   }

   public static Vec3 safestLavaLanding(PacketRoutePlanner.CollisionView view, Vec3 target, double radius) {
      if (view == null || target == null) {
         return null;
      } else if (safeClear(view, target) && view.traversable(target)) {
         return target;
      } else {
         int maxRing = Math.max(0, Math.min(64, (int)Math.ceil(radius)));
         double[] yOffsets = new double[]{0.0, 0.5, -0.5, 1.0, -1.0, 2.0, -2.0, 3.0, -3.0, 4.0, -4.0};
         Vec3 best = null;
         double bestClearance = Double.NEGATIVE_INFINITY;
         double bestDistance = Double.POSITIVE_INFINITY;
         double bestVertical = Double.POSITIVE_INFINITY;
         int firstSafeRing = -1;

         for (int ring = 0; ring <= maxRing && (firstSafeRing < 0 || ring <= Math.min(maxRing, firstSafeRing + 6)); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
               for (int dz = -ring; dz <= ring; dz++) {
                  if (ring <= 0 || Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                     for (double dy : yOffsets) {
                        Vec3 candidate = new Vec3(target.x + dx, clampY(view, target.y + dy), target.z + dz);
                        if (safeClear(view, candidate) && view.traversable(candidate)) {
                           if (firstSafeRing < 0) {
                              firstSafeRing = ring;
                           }

                           double clearance = view.lavaClearance(candidate);
                           if (Double.isNaN(clearance)) {
                              clearance = 0.0;
                           }

                           if (clearance >= 6.0) {
                              return candidate;
                           }

                           double distance = candidate.distanceToSqr(target);
                           double vertical = Math.abs(candidate.y - target.y);
                           if (betterLavaLanding(candidate, clearance, distance, vertical, best, bestClearance, bestDistance, bestVertical)) {
                              best = candidate;
                              bestClearance = clearance;
                              bestDistance = distance;
                              bestVertical = vertical;
                           }
                        }
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private static boolean betterLavaLanding(
      Vec3 candidate, double clearance, double distance, double vertical, Vec3 best, double bestClearance, double bestDistance, double bestVertical
   ) {
      if (best == null) {
         return true;
      } else {
         int clearanceOrder = Double.compare(clearance, bestClearance);
         if (clearanceOrder != 0) {
            return clearanceOrder > 0;
         } else {
            int distanceOrder = Double.compare(distance, bestDistance);
            if (distanceOrder != 0) {
               return distanceOrder < 0;
            } else {
               int verticalOrder = Double.compare(vertical, bestVertical);
               if (verticalOrder != 0) {
                  return verticalOrder < 0;
               } else {
                  int xOrder = Double.compare(candidate.x, best.x);
                  if (xOrder != 0) {
                     return xOrder < 0;
                  } else {
                     int yOrder = Double.compare(candidate.y, best.y);
                     return yOrder != 0 ? yOrder < 0 : candidate.z < best.z;
                  }
               }
            }
         }
      }
   }

   public static Vec3 nearestGroundedAtColumn(PacketRoutePlanner.CollisionView view, Vec3 target, double verticalRange) {
      if (view != null && target != null && view.loaded(target)) {
         double range = Math.max(0.0, Math.min(192.0, verticalRange));
         return findHybridAnchor(new PacketRoutePlanner.HybridProbe(view, 1800), target.x, target.z, target.y, range, range);
      } else {
         return null;
      }
   }

   public static Vec3 findTopLanding(PacketRoutePlanner.CollisionView view, Vec3 start, double verticalRange) {
      return view != null && start != null
         ? findTopLanding(new PacketRoutePlanner.HybridProbe(view, 1800), start, Math.max(0.0, Math.min(128.0, verticalRange)))
         : null;
   }

   private static Vec3 findTopLanding(PacketRoutePlanner.HybridProbe probe, Vec3 start, double verticalRange) {
      if (probe != null && start != null) {
         boolean seenBlocked = false;
         double range = Math.max(0.0, Math.min(128.0, verticalRange));

         for (double offset = 0.5; offset <= range + 1.0E-9 && !probe.exhausted(); offset += 0.5) {
            Vec3 candidate = new Vec3(start.x, clampY(probe.view, start.y + offset), start.z);
            if (sameY(candidate.y, start.y)) {
               break;
            }

            if (probe.loaded(candidate)) {
               if (!probe.clear(candidate)) {
                  seenBlocked = true;
               } else if (seenBlocked && probe.lavaSafe(candidate) && probe.supported(candidate)) {
                  return candidate;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static List<Vec3> topAdvance(PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 frontier, Vec3 topExit) {
      if (topExit != null && frontier != null) {
         Vec3 end = new Vec3(frontier.x, topExit.y, frontier.z);
         if (!safeLavaPath(probe, start, topExit)) {
            return List.of();
         } else if (!probe.safeClear(end)) {
            return List.of();
         } else {
            return !clearHybridHorizontalPath(probe, topExit, end) ? List.of() : clean(start, List.of(topExit, end));
         }
      } else {
         return List.of();
      }
   }

   private static PacketRoutePlanner.Route unrestrictedClipRoute(
      PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, double frontierRadius, String detail
   ) {
      List<Vec3> route = safeFallbackWaypoints(view, start, target, frontierRadius);
      return route.isEmpty()
         ? blocked("lava safety wait: " + detail)
         : new PacketRoutePlanner.Route(same(route.getLast(), target) ? PacketRoutePlanner.State.COMPLETE : PacketRoutePlanner.State.PARTIAL, route, detail);
   }

   public static List<Vec3> safeFallbackWaypoints(PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, double frontierRadius) {
      if (view != null && start != null && target != null) {
         double horizontal = horizontalDistance(start, target);
         if (!(horizontal <= 0.05)) {
            double amount = Math.min(horizontal, Math.max(1.0, Math.min(128.0, frontierRadius)));
            Vec3 horizontalEnd = toward(start, target, amount);
            boolean targetColumn = amount + 0.05 >= horizontal;
            List<Vec3> direct = finishSafeFallback(view, start, horizontalEnd, target, targetColumn, List.of());
            if (!direct.isEmpty()) {
               return direct;
            } else {
               double[] offsets = new double[]{
                  3.0, -3.0, 4.0, -4.0, 8.0, -8.0, 16.0, -16.0, 32.0, -32.0, 48.0, -48.0, 64.0, -64.0, 80.0, -80.0, 96.0, -96.0, 112.0, -112.0, 128.0, -128.0
               };

               for (double offset : offsets) {
                  double layerY = clampY(view, start.y + offset);
                  if (!sameY(layerY, start.y)) {
                     Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
                     Vec3 escapeEnd = new Vec3(horizontalEnd.x, layerY, horizontalEnd.z);
                     if (safeLavaPath(view, start, escapeStart)) {
                        List<Vec3> layered = finishSafeFallback(view, escapeStart, escapeEnd, target, targetColumn, List.of(escapeStart));
                        if (!layered.isEmpty()) {
                           return clean(start, layered);
                        }
                     }
                  }
               }

               return List.of();
            }
         } else {
            return safeLavaPath(view, start, target) && knownLavaSafe(view, target) ? clean(start, List.of(target)) : List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<Vec3> finishSafeFallback(
      PacketRoutePlanner.CollisionView view, Vec3 segmentStart, Vec3 horizontalEnd, Vec3 target, boolean targetColumn, List<Vec3> prefix
   ) {
      if (!safeLavaPath(view, segmentStart, horizontalEnd)) {
         return List.of();
      } else {
         List<Vec3> route = new ArrayList<>(prefix);
         route.add(horizontalEnd);
         if (targetColumn && !same(horizontalEnd, target)) {
            if (!safeLavaPath(view, horizontalEnd, target) || !knownLavaSafe(view, target)) {
               return List.of();
            }

            route.add(target);
         }

         return List.copyOf(route);
      }
   }

   private static boolean safeLavaPath(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
      if (view != null && from != null && to != null) {
         double distance = from.distanceTo(to);
         int samples = Math.max(1, (int)Math.ceil(distance / 0.5));

         for (int i = 1; i <= samples; i++) {
            Vec3 sample = from.lerp(to, (double)i / samples);
            if (!view.loaded(sample) || !view.lavaSafe(sample)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean safeLavaPath(PacketRoutePlanner.HybridProbe probe, Vec3 from, Vec3 to) {
      if (probe != null && from != null && to != null) {
         double distance = from.distanceTo(to);
         int samples = Math.max(1, (int)Math.ceil(distance / 0.5));

         for (int i = 1; i <= samples && !probe.exhausted(); i++) {
            Vec3 sample = from.lerp(to, (double)i / samples);
            if (!probe.loaded(sample) || !probe.lavaSafe(sample)) {
               return false;
            }
         }

         return !probe.exhausted();
      } else {
         return false;
      }
   }

   private static PacketRoutePlanner.Route finishHybridRoute(
      PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 target, List<Vec3> route, boolean targetColumn, String detail
   ) {
      List<Vec3> cleaned = clean(start, route);
      if (cleaned.size() > 3) {
         cleaned = List.copyOf(cleaned.subList(0, 3));
      }

      if (cleaned.isEmpty()) {
         return blocked(probe.exhausted() ? "collision probe budget exhausted" : detail);
      } else {
         Vec3 end = cleaned.getLast();
         if (same(end, target)) {
            return complete(cleaned, detail);
         } else if (targetColumn
            && horizontalDistance(end, target) <= 0.05
            && cleaned.size() < 3
            && probe.safeClear(target)
            && safeLavaPath(probe, end, target)) {
            List<Vec3> completed = new ArrayList<>(cleaned);
            completed.add(target);
            return complete(clean(start, completed), detail + " then VClip target");
         } else {
            return new PacketRoutePlanner.Route(PacketRoutePlanner.State.PARTIAL, cleaned, detail);
         }
      }
   }

   private static List<Vec3> routeToStableAnchor(PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 anchor, double targetY, double verticalRange) {
      return routeToStableAnchor(probe, start, anchor, targetY, verticalRange, true);
   }

   private static List<Vec3> routeToStableAnchor(
      PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 anchor, double targetY, double verticalRange, boolean includeLayerScan
   ) {
      if (horizontalDistance(start, anchor) <= 0.05) {
         return clean(start, List.of(anchor));
      } else {
         Vec3 startAtAnchorY = new Vec3(start.x, anchor.y, start.z);
         if (probe.safeClear(startAtAnchorY)
            && probe.safeClear(anchor)
            && safeLavaPath(probe, start, startAtAnchorY)
            && clearHybridHorizontalPath(probe, startAtAnchorY, anchor)) {
            return clean(start, List.of(startAtAnchorY, anchor));
         } else {
            Vec3 endAtStartY = new Vec3(anchor.x, start.y, anchor.z);
            if (probe.safeClear(endAtStartY) && clearHybridHorizontalPath(probe, start, endAtStartY) && safeLavaPath(probe, endAtStartY, anchor)) {
               return clean(start, List.of(endAtStartY, anchor));
            } else if (!includeLayerScan) {
               return List.of();
            } else {
               for (double layerY : hybridLayerHeights(probe, start.y, anchor.y, targetY, verticalRange)) {
                  if (!sameY(layerY, start.y) && !sameY(layerY, anchor.y)) {
                     Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
                     Vec3 escapeEnd = new Vec3(anchor.x, layerY, anchor.z);
                     if (probe.safeClear(escapeStart)
                        && probe.safeClear(escapeEnd)
                        && safeLavaPath(probe, start, escapeStart)
                        && clearHybridHorizontalPath(probe, escapeStart, escapeEnd)
                        && safeLavaPath(probe, escapeEnd, anchor)) {
                        return clean(start, List.of(escapeStart, escapeEnd, anchor));
                     }
                  }
               }

               return List.of();
            }
         }
      }
   }

   private static List<Vec3> routeToAirFrontier(
      PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 frontier, Vec3 target, boolean targetColumn, double verticalRange
   ) {
      Vec3 endAtStartY = new Vec3(frontier.x, start.y, frontier.z);
      if (probe.safeClear(endAtStartY) && clearHybridHorizontalPath(probe, start, endAtStartY)) {
         List<Vec3> route = new ArrayList<>(2);
         route.add(endAtStartY);
         if (targetColumn && !same(endAtStartY, target) && probe.safeClear(target) && safeLavaPath(probe, endAtStartY, target)) {
            route.add(target);
         }

         return clean(start, route);
      } else {
         for (double layerY : hybridLayerHeights(probe, start.y, target.y, target.y, verticalRange)) {
            if (!sameY(layerY, start.y)) {
               Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
               Vec3 escapeEnd = new Vec3(frontier.x, layerY, frontier.z);
               if (probe.safeClear(escapeStart)
                  && probe.safeClear(escapeEnd)
                  && safeLavaPath(probe, start, escapeStart)
                  && clearHybridHorizontalPath(probe, escapeStart, escapeEnd)) {
                  List<Vec3> route = new ArrayList<>(3);
                  route.add(escapeStart);
                  route.add(escapeEnd);
                  if (targetColumn && !same(escapeEnd, target) && probe.safeClear(target) && safeLavaPath(probe, escapeEnd, target)) {
                     route.add(target);
                  }

                  return clean(start, route);
               }
            }
         }

         return List.of();
      }
   }

   private static Vec3 farthestLoadedFrontier(PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 target, double maxDistance) {
      for (double distance = Math.max(0.0, maxDistance); distance >= 0.499999999 && !probe.exhausted(); distance -= 2.0) {
         Vec3 candidate = toward(start, target, distance);
         if (probe.loaded(candidate)) {
            return candidate;
         }
      }

      return null;
   }

   private static Vec3 farthestClearAdvance(PacketRoutePlanner.HybridProbe probe, Vec3 start, Vec3 target) {
      double distance = horizontalDistance(start, target);
      if (distance < 0.5) {
         return null;
      } else {
         Vec3 best = null;

         for (double step = 0.5; step <= distance + 1.0E-9 && !probe.exhausted(); step += 0.5) {
            Vec3 candidate = toward(start, target, Math.min(step, distance));
            if (!probe.safeClear(candidate)) {
               break;
            }

            best = candidate;
         }

         return best;
      }
   }

   private static boolean clearHybridHorizontalPath(PacketRoutePlanner.HybridProbe probe, Vec3 from, Vec3 to) {
      if (!sameY(from.y, to.y)) {
         return false;
      } else {
         double distance = horizontalDistance(from, to);
         int samples = Math.max(1, (int)Math.ceil(distance / 0.5));

         for (int i = 1; i <= samples && !probe.exhausted(); i++) {
            Vec3 candidate = from.lerp(to, (double)i / samples);
            if (!probe.safeClear(candidate)) {
               return false;
            }
         }

         if (probe.exhausted()) {
            return false;
         } else {
            Entity entity = probe.view.entity();
            if (entity != null) {
               int segments = Math.max(1, (int)Math.ceil(distance / 8.0));
               Vec3 prev = from;

               for (int ix = 1; ix <= segments; ix++) {
                  Vec3 next = from.lerp(to, (double)ix / segments);
                  if (!PacketClipSafety.sweptClear(entity, prev, next)) {
                     return false;
                  }

                  prev = next;
               }
            }

            return true;
         }
      }
   }

   private static Vec3 findHybridAnchor(PacketRoutePlanner.HybridProbe probe, double x, double z, double preferredY, double downRange, double upRange) {
      double preferred = clampY(probe.view, preferredY);
      Vec3 exact = stableCandidate(probe, x, z, preferred);
      if (exact != null) {
         return exact;
      } else {
         Vec3 down = scanHybridAnchor(probe, x, z, preferred, -1.0, Math.max(0.0, downRange));
         return down != null ? down : scanHybridAnchor(probe, x, z, preferred, 1.0, Math.max(0.0, upRange));
      }
   }

   private static Vec3 scanHybridAnchor(PacketRoutePlanner.HybridProbe probe, double x, double z, double preferredY, double direction, double range) {
      double previousY = preferredY;
      Vec3 previous = new Vec3(x, preferredY, z);
      boolean previousClear = probe.loaded(previous) && probe.clear(previous);

      for (double offset = 0.25; offset <= range + 1.0E-9 && !probe.exhausted(); offset += 0.25) {
         double y = clampY(probe.view, preferredY + direction * offset);
         if (sameY(y, previousY)) {
            break;
         }

         Vec3 candidate = new Vec3(x, y, z);
         PacketRoutePlanner.AnchorCheck check = probe.anchor(candidate);
         if (check.stable()) {
            return candidate;
         }

         boolean clear = check.clear();
         if (clear != previousClear) {
            Vec3 boundary = refineStableBoundary(probe, x, z, previousY, previousClear, y, clear);
            if (boundary != null) {
               return boundary;
            }
         }

         previousY = y;
         previousClear = clear;
      }

      return null;
   }

   private static Vec3 refineStableBoundary(
      PacketRoutePlanner.HybridProbe probe, double x, double z, double firstY, boolean firstClear, double secondY, boolean secondClear
   ) {
      if (firstClear == secondClear) {
         return null;
      } else {
         double clearY = firstClear ? firstY : secondY;
         double blockedY = firstClear ? secondY : firstY;

         for (int i = 0; i < 7 && !probe.exhausted(); i++) {
            double middleY = (clearY + blockedY) * 0.5;
            Vec3 middle = new Vec3(x, middleY, z);
            if (probe.loaded(middle) && probe.clear(middle)) {
               clearY = middleY;
            } else {
               blockedY = middleY;
            }
         }

         return stableCandidate(probe, x, z, clearY);
      }
   }

   private static Vec3 stableCandidate(PacketRoutePlanner.HybridProbe probe, double x, double z, double y) {
      double clamped = clampY(probe.view, y);
      Vec3 candidate = new Vec3(x, clamped, z);
      if (probe.anchor(candidate).stable()) {
         return candidate;
      } else {
         double snapped = clampY(probe.view, Math.rint(clamped * 16.0) / 16.0);
         if (!sameY(snapped, clamped)) {
            Vec3 snappedCandidate = new Vec3(x, snapped, z);
            if (probe.anchor(snappedCandidate).stable()) {
               return snappedCandidate;
            }
         }

         return null;
      }
   }

   private static List<Double> hybridLayerHeights(PacketRoutePlanner.HybridProbe probe, double startY, double anchorY, double targetY, double verticalRange) {
      List<Double> heights = new ArrayList<>(32);
      addHybridLayer(heights, clampY(probe.view, startY));
      addHybridLayer(heights, clampY(probe.view, anchorY));
      addHybridLayer(heights, clampY(probe.view, targetY));
      int[] offsets = new int[]{
         1, -1, 2, -2, 3, 4, -3, -4, 6, -6, 8, -8, 12, -12, 16, -16, 24, -24, 32, -32, 48, -48, 64, -64, 80, -80, 96, -96, 112, -112, 128, -128
      };
      double range = Math.max(0.0, Math.min(128.0, verticalRange));

      for (int offset : offsets) {
         if (heights.size() >= 32) {
            break;
         }

         if (!(Math.abs(offset) > range + 1.0E-9)) {
            addHybridLayer(heights, clampY(probe.view, startY + offset));
         }
      }

      return List.copyOf(heights);
   }

   private static void addHybridLayer(List<Double> heights, double y) {
      for (double existing : heights) {
         if (sameY(existing, y)) {
            return;
         }
      }

      heights.add(y);
   }

   private static boolean sameY(double a, double b) {
      return Math.abs(a - b) <= 1.0E-5;
   }

   static boolean reached(Vec3 actual, Vec3 target, double tolerance) {
      return actual != null && target != null && actual.distanceTo(target) <= Math.max(0.0, tolerance);
   }

   private static PacketRoutePlanner.Route layered(PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 target, int escapeRange, int routeCap) {
      if (!view.loaded(target)) {
         return blocked("target chunk unavailable");
      } else {
         for (int dy : verticalOffsets(escapeRange)) {
            if (dy != 0) {
               double y = clampY(view, start.y + dy);
               Vec3 escapeStart = new Vec3(start.x, y, start.z);
               Vec3 escapeEnd = new Vec3(target.x, y, target.z);
               if (safeClear(view, escapeStart)
                  && safeClear(view, escapeEnd)
                  && safeLavaPath(view, start, escapeStart)
                  && clearHorizontalPath(view, escapeStart, escapeEnd)) {
                  List<Vec3> route = new ArrayList<>(3);
                  route.add(escapeStart);
                  route.add(escapeEnd);
                  Vec3 landing = nearestClearAtColumn(view, target.x, target.z, start.y, 128.0);
                  if (landing != null && !same(landing, escapeEnd) && safeLavaPath(view, escapeEnd, landing)) {
                     route.add(landing);
                  }

                  List<Vec3> cleaned = clean(start, route);
                  if (!cleaned.isEmpty() && cleaned.size() <= routeCap) {
                     return complete(cleaned, "layered dy=" + dy);
                  }
               }
            }
         }

         return blocked("no clear layer");
      }
   }

   private static Vec3 nearestClearAtColumn(PacketRoutePlanner.CollisionView view, double x, double z, double preferredY, double range) {
      for (double offset = 0.0; offset <= range; offset += 0.5) {
         int[] signs = offset == 0.0 ? new int[]{1} : new int[]{1, -1};

         for (int sign : signs) {
            Vec3 candidate = new Vec3(x, clampY(view, preferredY + sign * offset), z);
            if (safeClear(view, candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   private static Vec3 maxClearAdvance(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 target, int searchRadius) {
      double distance = horizontalDistance(from, target);
      if (distance < 1.0E-5) {
         return from;
      } else {
         double maxDistance = Math.min(distance, (double)Math.max(1, searchRadius));
         Entity entity = view.entity();
         Vec3 best = from;

         for (double step = 0.5; step <= maxDistance + 1.0E-9; step += 0.5) {
            Vec3 candidate = new Vec3(from.x + (target.x - from.x) / distance * step, from.y, from.z + (target.z - from.z) / distance * step);
            if (entity != null ? !PacketClipSafety.sweptClear(entity, best, candidate) : !safeClear(view, candidate)) {
               break;
            }

            best = candidate;
         }

         return best;
      }
   }

   private static boolean clearHorizontalPath(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
      if (Math.abs(from.y - to.y) > 1.0E-5) {
         return false;
      } else {
         Entity entity = view.entity();
         if (entity != null) {
            double distance = from.distanceTo(to);
            int segments = Math.max(1, (int)Math.ceil(distance / 8.0));
            Vec3 prev = from;

            for (int i = 1; i <= segments; i++) {
               Vec3 next = from.lerp(to, (double)i / segments);
               if (!PacketClipSafety.sweptClear(entity, prev, next)) {
                  return false;
               }

               prev = next;
            }

            return true;
         } else {
            double distance = from.distanceTo(to);
            int samples = Math.max(1, (int)Math.ceil(distance / 0.25));

            for (int i = 1; i <= samples; i++) {
               Vec3 candidate = from.lerp(to, (double)i / samples);
               if (!safeClear(view, candidate)) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private static List<Integer> verticalOffsets(int range) {
      List<Integer> offsets = new ArrayList<>(range * 2 + 1);
      offsets.add(0);

      for (int i = 1; i <= range; i++) {
         offsets.add(i);
         offsets.add(-i);
      }

      return offsets;
   }

   private static List<Vec3> clean(Vec3 start, List<Vec3> route) {
      List<Vec3> cleaned = new ArrayList<>();
      Vec3 previous = start;

      for (Vec3 waypoint : route) {
         if (waypoint != null && !same(previous, waypoint)) {
            cleaned.add(waypoint);
            previous = waypoint;
         }
      }

      return List.copyOf(cleaned);
   }

   private static boolean safeClear(PacketRoutePlanner.CollisionView view, Vec3 position) {
      return knownLavaSafe(view, position) && view.clear(position);
   }

   private static boolean knownLavaSafe(PacketRoutePlanner.CollisionView view, Vec3 position) {
      return view != null && position != null && view.loaded(position) && view.lavaSafe(position);
   }

   private static Vec3 toward(Vec3 from, Vec3 target, double distance) {
      double horizontal = horizontalDistance(from, target);
      return horizontal <= distance
         ? new Vec3(target.x, from.y, target.z)
         : new Vec3(from.x + (target.x - from.x) / horizontal * distance, from.y, from.z + (target.z - from.z) / horizontal * distance);
   }

   private static double clampY(PacketRoutePlanner.CollisionView view, double y) {
      return Math.max(view.minY(), Math.min(view.maxFeetY(), y));
   }

   private static double horizontalDistance(Vec3 a, Vec3 b) {
      return Math.hypot(a.x - b.x, a.z - b.z);
   }

   private static boolean same(Vec3 a, Vec3 b) {
      return a.distanceToSqr(b) < 1.0E-10;
   }

   private static PacketRoutePlanner.Route complete(List<Vec3> waypoints, String detail) {
      return new PacketRoutePlanner.Route(PacketRoutePlanner.State.COMPLETE, waypoints, detail);
   }

   private static PacketRoutePlanner.Route blocked(String detail) {
      return new PacketRoutePlanner.Route(PacketRoutePlanner.State.BLOCKED, List.of(), detail);
   }

   private record AnchorCheck(boolean clear, boolean stable) {
   }

   public interface CollisionView {
      boolean loaded(Vec3 var1);

      boolean clear(Vec3 var1);

      default Entity entity() {
         return null;
      }

      default boolean supported(Vec3 position) {
         return this.clear(position);
      }

      default boolean breathableWater(Vec3 position) {
         return false;
      }

      default boolean traversable(Vec3 position) {
         return this.supported(position) || this.breathableWater(position);
      }

      default boolean lavaSafe(Vec3 position) {
         return true;
      }

      default double lavaClearance(Vec3 position) {
         return this.lavaSafe(position) ? Double.POSITIVE_INFINITY : 0.0;
      }

      double minY();

      double maxFeetY();
   }

   private static final class HybridProbe {
      final PacketRoutePlanner.CollisionView view;
      private int remaining;

      HybridProbe(PacketRoutePlanner.CollisionView view, int budget) {
         this.view = view;
         this.remaining = Math.max(1, budget);
      }

      boolean loaded(Vec3 position) {
         return this.claim() && this.view.loaded(position);
      }

      boolean clear(Vec3 position) {
         return this.claim() && this.view.clear(position);
      }

      boolean lavaSafe(Vec3 position) {
         return this.claim() && this.view.lavaSafe(position);
      }

      boolean safeClear(Vec3 position) {
         return this.loaded(position) && this.lavaSafe(position) && this.clear(position);
      }

      boolean supported(Vec3 position) {
         return this.claim() && this.view.supported(position);
      }

      PacketRoutePlanner.AnchorCheck anchor(Vec3 position) {
         if (!this.loaded(position)) {
            return new PacketRoutePlanner.AnchorCheck(false, false);
         } else if (!this.lavaSafe(position)) {
            return new PacketRoutePlanner.AnchorCheck(false, false);
         } else {
            boolean clear = this.clear(position);
            return clear && this.claim()
               ? new PacketRoutePlanner.AnchorCheck(true, this.view.traversable(position))
               : new PacketRoutePlanner.AnchorCheck(clear, false);
         }
      }

      boolean exhausted() {
         return this.remaining <= 0;
      }

      private boolean claim() {
         if (this.remaining <= 0) {
            return false;
         } else {
            this.remaining--;
            return true;
         }
      }
   }

   public record Route(PacketRoutePlanner.State state, List<Vec3> waypoints, String detail) {
      public Route(PacketRoutePlanner.State state, List<Vec3> waypoints, String detail) {
         state = state == null ? PacketRoutePlanner.State.BLOCKED : state;
         waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
         detail = detail == null ? "" : detail;
         this.state = state;
         this.waypoints = waypoints;
         this.detail = detail;
      }

      public boolean complete() {
         return this.state == PacketRoutePlanner.State.COMPLETE;
      }

      public boolean madeProgress() {
         return !this.waypoints.isEmpty();
      }
   }

   public static enum State {
      COMPLETE,
      PARTIAL,
      ESCAPE,
      BLOCKED;
   }
}
