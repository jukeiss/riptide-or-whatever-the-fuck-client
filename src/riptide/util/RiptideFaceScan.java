package riptide.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RiptideFaceScan {
   public static final Direction[] FACE_ORDER_UP_FIRST = new Direction[]{
      Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
   };
   public static final double FACE_EPSILON = 1.0E-4;
   public static final double AIM_CENTRE_WINDOW = 0.2;
   public static final int MAX_LADDER_RAYS = 4;
   public static final int DEFAULT_RECTS_PER_FACE = 3;
   public static final int DEFAULT_TICK_RAY_BUDGET = 48;
   public static final float MAX_PLACEMENT_PITCH = 89.5F;
   private static final double FACE_INSET = 0.12;
   private static final double PITCH_EMISSION_SLACK = 0.01;
   private static final int MAX_SHAPE_BOXES = 16;
   private static final double CROSSING_SPAN_MARGIN = 0.06;
   private static final AABB[] NO_RECTS = new AABB[0];
   private static final int SCAN_SLOTS = 48;
   private static final RiptideFaceScan.ScanSlot[] scanSlots = new RiptideFaceScan.ScanSlot[48];
   private static int scanSlotCursor;
   private static final int SHAPE_SLOTS = 8;
   private static final BlockPos[] shapePos = new BlockPos[8];
   private static final BlockState[] shapeState = new BlockState[8];
   private static final AABB[][] shapeBoxes = new AABB[8][];
   private static int shapeCursor;
   private static int leashTick = Integer.MIN_VALUE;
   private static boolean leashLeading;

   private RiptideFaceScan() {
   }

   public static RiptideFaceScan.Placement blockItem(ItemStack stack, Player player, InteractionHand hand) {
      return (hit, cell) -> {
         if (hit == null || cell == null || player == null) {
            return false;
         } else if (stack != null && stack.getItem() instanceof BlockItem) {
            BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
            return context.canPlace() && context.getClickedPos().equals(cell);
         } else {
            return false;
         }
      };
   }

   public static RiptideFaceScan.Placement onSupport(BlockPos support, EnumSet<Direction> allowedFaces) {
      BlockPos anchor = support == null ? null : support.immutable();
      return (hit, cell) -> {
         if (hit != null && anchor != null && cell != null) {
            if (!hit.getBlockPos().equals(anchor)) {
               return false;
            } else {
               return allowedFaces != null && !allowedFaces.contains(hit.getDirection()) ? false : cell.equals(anchor) || cell.equals(anchor.above());
            }
         } else {
            return false;
         }
      };
   }

   public static double goalPitchLimit() {
      return 89.5 - RiptideHumanRotation.settleBandDegrees(RiptideRotationUtil.sensitivityGcd()) - 0.01;
   }

   public static void options(RiptideFaceScan.Request request, List<RiptideFaceScan.Option> out) {
      if (request != null && out != null && request.cell() != null) {
         Level level = level();
         if (level != null) {
            BlockPos cell = request.cell();
            int start = out.size();
            boolean inPlace = request.allowInPlace() && replaceableInPlace(cell, level);
            MutableBlockPos scan = new MutableBlockPos();

            for (Direction face : request.faceOrder()) {
               if (request.allowedFaces() == null || request.allowedFaces().contains(face)) {
                  for (int form = 0; form < 2; form++) {
                     boolean own = form == 0;
                     if (!own || inPlace) {
                        BlockPos support = own ? cell : scan.setWithOffset(cell, face.getOpposite()).immutable();
                        BlockState state = level.getBlockState(support);
                        if (!state.isAir()) {
                           boolean requiresSneak = sneakUnlocks(state, support);
                           boolean sneaking = request.sneaking() || requiresSneak && request.sneakAllowed();
                           if ((own || isPlaceableSupport(state, support, sneaking)) && (!own || !useActionEatsClick(state, support, sneaking))) {
                              RiptideFaceScan.Intent intent = new RiptideFaceScan.Intent(support, face);
                              if (request.banned() == null || !request.banned().test(intent)) {
                                 for (AABB rect : faceRects(state, support, face, request.maxRectsPerFace())) {
                                    out.add(
                                       new RiptideFaceScan.Option(
                                          cell,
                                          support,
                                          face,
                                          form,
                                          state,
                                          rect,
                                          faceArea(rect, face),
                                          edgeConfidence(rect, face, request.eye()),
                                          requiresSneak,
                                          intent
                                       )
                                    );
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            sortSegment(out, start, request);
         }
      }
   }

   public static void options(List<BlockPos> cells, RiptideFaceScan.Request template, List<RiptideFaceScan.Option> out) {
      if (cells != null && template != null && out != null) {
         BlockPos held = template.cell();

         for (BlockPos cell : cells) {
            options(template.cell(cell), out);
         }

         template.cell(held);
      }
   }

   public static long shellKey(List<BlockPos> cells, Level level) {
      if (cells != null && level != null) {
         long key = 0L;
         MutableBlockPos scan = new MutableBlockPos();

         for (BlockPos cell : cells) {
            key += cellKey(cell, level, scan);
         }

         return key;
      } else {
         return 0L;
      }
   }

   private static long cellKey(BlockPos cell, Level level, MutableBlockPos scan) {
      long key = cell.asLong() * 31L + System.identityHashCode(level.getBlockState(cell));

      for (Direction direction : Direction.values()) {
         key = key * 31L + System.identityHashCode(level.getBlockState(scan.setWithOffset(cell, direction)));
      }

      key ^= key >>> 33;
      key *= -49064778989728563L;
      return key ^ key >>> 33;
   }

   private static void sortSegment(List<RiptideFaceScan.Option> out, int start, RiptideFaceScan.Request request) {
      int size = out.size();
      if (size - start >= 2) {
         List<RiptideFaceScan.Option> segment = new ArrayList<>(out.subList(start, size));
         Vec3 eye = request.eye();
         RiptideRotationUtil.Rotation from = request.from();
         Direction[] order = request.faceOrder();
         segment.sort((left, right) -> {
            boolean leftUp = left.face() == Direction.UP;
            boolean rightUp = right.face() == Direction.UP;
            if (leftUp != rightUp) {
               return leftUp ? -1 : 1;
            } else {
               int area = Double.compare(right.faceArea(), left.faceArea());
               if (area != 0) {
                  return area;
               } else {
                  int edge = Double.compare(right.edgeConfidence(), left.edgeConfidence());
                  if (edge != 0) {
                     return edge;
                  } else {
                     if (from != null) {
                        int turn = Float.compare(turnTo(left, eye, from), turnTo(right, eye, from));
                        if (turn != 0) {
                           return turn;
                        }
                     }

                     return Integer.compare(faceIndex(order, left.face()), faceIndex(order, right.face()));
                  }
               }
            }
         });

         for (int i = 0; i < segment.size(); i++) {
            out.set(start + i, segment.get(i));
         }
      }
   }

   private static float turnTo(RiptideFaceScan.Option option, Vec3 eye, RiptideRotationUtil.Rotation from) {
      Vec3 centre = faceCentre(option.rect(), option.face());
      return RiptideRotationUtil.angleTo(from, RiptideRotationUtil.lookingAt(centre, eye));
   }

   private static int faceIndex(Direction[] order, Direction face) {
      for (int i = 0; i < order.length; i++) {
         if (order[i] == face) {
            return i;
         }
      }

      return order.length;
   }

   public static RiptideFaceScan.Aim solve(RiptideFaceScan.Option option, RiptideFaceScan.Request request, RiptideFaceScan.Refusal[] refusal) {
      set(refusal, RiptideFaceScan.Refusal.NONE);
      if (option != null && request != null) {
         Level level = level();
         Direction face = option.face();
         if (level == null) {
            return refuse(refusal, RiptideFaceScan.Refusal.NOT_A_SUPPORT, request, face);
         } else {
            BlockPos support = option.support();
            BlockState state = level.getBlockState(support);
            if (state != option.state()) {
               return refuse(refusal, RiptideFaceScan.Refusal.NOT_A_SUPPORT, request, face);
            } else if (request.banned() != null && request.banned().test(option.intent())) {
               return refuse(refusal, RiptideFaceScan.Refusal.BANNED, request, face);
            } else {
               boolean sneaking = request.sneaking() || option.requiresSneak() && request.sneakAllowed();
               if (request.placement() != null && request.placement().clickable(state, support, sneaking)) {
                  Vec3 eye = request.eye();
                  if (selfOccluded(eye, support, face, level)) {
                     return refuse(refusal, RiptideFaceScan.Refusal.SELF_OCCLUDED, request, face);
                  } else {
                     AABB rect = option.rect();
                     Vec3 point = aimPoint(rect, face, eye, aimFrom(request));
                     if (point == null) {
                        return refuse(refusal, RiptideFaceScan.Refusal.BEHIND_PLANE, request, face);
                     } else {
                        RiptideFaceScan.Tier tier = RiptideFaceScan.Tier.CENTRE;
                        double reachSq = request.reach() * request.reach();
                        if (eye.distanceToSqr(point) > reachSq) {
                           point = windowPoint(rect, face, eye, RiptideFaceScan.Tier.NEAREST);
                           if (point == null || eye.distanceToSqr(point) > reachSq) {
                              return refuse(refusal, RiptideFaceScan.Refusal.OUT_OF_REACH, request, face);
                           }

                           tier = RiptideFaceScan.Tier.NEAREST;
                        }

                        RiptideRotationUtil.Rotation goal = RiptideRotationUtil.lookingAt(point, eye);
                        if (Math.abs(goal.pitch()) > request.pitchLimit()) {
                           Vec3 flat = windowPoint(rect, face, eye, RiptideFaceScan.Tier.FLATTEST);
                           if (flat == null || eye.distanceToSqr(flat) > reachSq) {
                              return refuse(refusal, RiptideFaceScan.Refusal.PAST_PITCH_CAP, request, face);
                           }

                           goal = RiptideRotationUtil.lookingAt(flat, eye);
                           if (Math.abs(goal.pitch()) > request.pitchLimit()) {
                              return refuse(refusal, RiptideFaceScan.Refusal.PAST_PITCH_CAP, request, face);
                           }

                           point = flat;
                           tier = RiptideFaceScan.Tier.FLATTEST;
                        }

                        if (face.getAxis().isHorizontal() && request.leadEye() != null) {
                           double[] window = crossingWindow(eye, request.leadEye(), rect, face, goal.yaw());
                           if (window != null) {
                              float pitch = (float)Mth.clamp(goal.pitch(), window[0], window[1]);
                              if (Math.abs(pitch) > request.pitchLimit()) {
                                 return refuse(refusal, RiptideFaceScan.Refusal.PAST_PITCH_CAP, request, face);
                              }

                              goal = new RiptideRotationUtil.Rotation(goal.yaw(), pitch);
                           }
                        }

                        if (!request.placement().lands(new BlockHitResult(point, face, support, false), request.cell())) {
                           return refuse(refusal, RiptideFaceScan.Refusal.NOT_A_SUPPORT, request, face);
                        } else {
                           RiptideRotationUtil.Rotation from = aimFrom(request);
                           RiptideRotationUtil.Rotation emitted = request.quantize()
                              ? RiptideRotationUtil.normalizeToSensitivity(goal, from == null ? goal : from)
                              : goal;
                           float turn = from == null ? 0.0F : RiptideRotationUtil.angleTo(from, goal);
                           return new RiptideFaceScan.Aim(point, goal, emitted, eye.distanceTo(point), turn, tier);
                        }
                     }
                  }
               } else {
                  return refuse(refusal, RiptideFaceScan.Refusal.NOT_A_SUPPORT, request, face);
               }
            }
         }
      } else {
         return refuse(refusal, RiptideFaceScan.Refusal.NOT_A_SUPPORT, request, null);
      }
   }

   public static Vec3 aimPoint(AABB rect, Direction face, Vec3 eye, RiptideRotationUtil.Rotation from) {
      if (rect != null && face != null && eye != null) {
         Axis normal = face.getAxis();
         double plane = planeOf(rect, face);
         if (eyePastPlane(eye, rect, face) <= 1.0E-4) {
            return null;
         } else {
            Axis first = inPlaneAxis(normal, true);
            Axis second = inPlaneAxis(normal, false);
            double margin = faceMargin(eye.distanceTo(faceCentre(rect, face)));
            double[] windowFirst = aimWindow(rect, first, margin, true);
            double[] windowSecond = aimWindow(rect, second, margin, true);
            double travel = -1.0;
            Vec3 look = from == null ? null : lookVector(from);
            if (look != null) {
               double along = coordinate(look, normal);
               travel = Math.abs(along) > 1.0E-4 ? (plane - coordinate(eye, normal)) / along : -1.0;
            }

            double a;
            double b;
            if (travel > 0.0) {
               Vec3 crossing = eye.add(look.scale(travel));
               a = coordinate(crossing, first);
               b = coordinate(crossing, second);
            } else {
               a = coordinate(eye, first);
               b = coordinate(eye, second);
            }

            return onPlane(normal, plane, Mth.clamp(a, windowFirst[0], windowFirst[1]), Mth.clamp(b, windowSecond[0], windowSecond[1]));
         }
      } else {
         return null;
      }
   }

   public static Vec3 windowPoint(AABB rect, Direction face, Vec3 eye, RiptideFaceScan.Tier tier) {
      if (rect != null && face != null && eye != null) {
         Axis normal = face.getAxis();
         double plane = planeOf(rect, face);
         if (eyePastPlane(eye, rect, face) <= 1.0E-4) {
            return null;
         } else {
            Axis first = inPlaneAxis(normal, true);
            Axis second = inPlaneAxis(normal, false);
            double margin = faceMargin(eye.distanceTo(faceCentre(rect, face)));
            boolean centred = tier == RiptideFaceScan.Tier.CENTRE;
            boolean away = tier == RiptideFaceScan.Tier.FLATTEST || tier == RiptideFaceScan.Tier.ESCAPE;
            return onPlane(
               normal,
               plane,
               windowEnd(aimWindow(rect, first, margin, centred), coordinate(eye, first), first, away),
               windowEnd(aimWindow(rect, second, margin, centred), coordinate(eye, second), second, away)
            );
         }
      } else {
         return null;
      }
   }

   private static double windowEnd(double[] window, double at, Axis axis, boolean away) {
      if (away && axis != Axis.Y) {
         return Math.abs(window[0] - at) >= Math.abs(window[1] - at) ? window[0] : window[1];
      } else {
         return Mth.clamp(at, window[0], window[1]);
      }
   }

   public static double axisInset(AABB rect, Axis axis, double margin) {
      double extent = rect.max(axis) - rect.min(axis);
      return Math.min(Math.max(0.0, margin), Math.max(0.0, extent * 0.5 - 1.0E-4));
   }

   public static double[] aimWindow(AABB rect, Axis axis, double margin, boolean centred) {
      double inset = axisInset(rect, axis, margin);
      double low = rect.min(axis) + inset;
      double high = rect.max(axis) - inset;
      if (high < low) {
         double mid = (low + high) * 0.5;
         low = mid;
         high = mid;
      }

      if (!centred) {
         return new double[]{low, high};
      } else {
         double centre = (rect.min(axis) + rect.max(axis)) * 0.5;
         double half = Math.min(0.2, (high - low) * 0.5);
         return new double[]{centre - half, centre + half};
      }
   }

   public static double faceMargin(double distance) {
      double band = RiptideHumanRotation.settleBandDegrees(RiptideRotationUtil.sensitivityGcd());
      return Math.max(0.12, Math.abs(distance) * Math.tan(Math.toRadians(band)));
   }

   public static double edgeConfidence(AABB rect, Direction face, Vec3 eye) {
      if (rect != null && face != null && eye != null) {
         double margin = faceMargin(eye.distanceTo(faceCentre(rect, face))) * 2.0;
         if (margin <= 0.0) {
            return 1.0;
         } else {
            Axis normal = face.getAxis();
            double first = rect.max(inPlaneAxis(normal, true)) - rect.min(inPlaneAxis(normal, true));
            double second = rect.max(inPlaneAxis(normal, false)) - rect.min(inPlaneAxis(normal, false));
            return Mth.clamp(Math.min(first, second) / margin, 0.0, 1.0);
         }
      } else {
         return 0.0;
      }
   }

   public static double[] crossingWindow(Vec3 eye, AABB rect, Direction face, float yaw) {
      if (eye != null && rect != null && face != null) {
         double past = eyePastPlane(eye, rect, face);
         if (past <= 0.0) {
            return null;
         } else {
            double yawRad = Math.toRadians(yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
            if (toward <= 0.1) {
               return null;
            } else {
               double run = past / toward;
               double margin = 0.06 * rect.getYsize();
               double shallowest = Math.toDegrees(Math.atan2(eye.y - (rect.maxY - margin), run));
               double steepest = Math.toDegrees(Math.atan2(eye.y - (rect.minY + margin), run));
               return new double[]{shallowest, steepest, run};
            }
         }
      } else {
         return null;
      }
   }

   public static double[] crossingWindow(Vec3 eye, Vec3 leadEye, AABB rect, Direction face, float yaw) {
      double[] primary = crossingWindow(eye, rect, face, yaw);
      if (primary == null) {
         return null;
      } else {
         double low = primary[0];
         double high = primary[1];
         double[] secondary = crossingWindow(leadEye, rect, face, yaw);
         if (secondary != null) {
            double bothLow = Math.max(low, secondary[0]);
            double bothHigh = Math.min(high, secondary[1]);
            if (bothLow <= bothHigh) {
               low = bothLow;
               high = bothHigh;
            }
         }

         return new double[]{low, high, primary[2]};
      }
   }

   public static RiptideFaceScan.Candidate probe(
      RiptideFaceScan.Option option, RiptideFaceScan.Aim aim, RiptideFaceScan.Request request, RiptideFaceScan.Refusal[] refusal
   ) {
      set(refusal, RiptideFaceScan.Refusal.NONE);
      if (option != null && aim != null && request != null) {
         Level level = level();
         Player player = player();
         Direction face = option.face();
         if (level != null && player != null) {
            Vec3 eye = request.eye();
            double reach = request.reach();
            double reachSq = reach * reach;
            boolean sneaking = request.sneaking() || option.requiresSneak() && request.sneakAllowed();
            RiptideRotationUtil.Rotation cast = request.quantize() ? aim.emitted() : aim.goal();
            boolean escaped = false;

            for (int rung = 0; rung < 4; rung++) {
               if (request.budget() != null && !request.budget().spend()) {
                  return refuseCandidate(refusal, RiptideFaceScan.Refusal.NO_BUDGET, request, face);
               }

               BlockHitResult hit = ray(eye, cast, reach, level, player);
               BlockPos hitPos = hit == null ? null : hit.getBlockPos();
               BlockState hitState = hit == null ? null : level.getBlockState(hitPos);
               boolean builds = hit != null && request.placement().clickable(hitState, hitPos, sneaking) && request.placement().lands(hit, request.cell());
               if (!builds) {
                  if (escaped) {
                     return refuseCandidate(refusal, hit == null ? RiptideFaceScan.Refusal.RAY_MISSED : RiptideFaceScan.Refusal.RAY_WRONG_CELL, request, face);
                  }

                  escaped = true;
                  cast = retarget(windowPoint(option.rect(), face, eye, RiptideFaceScan.Tier.ESCAPE), cast, eye, reachSq, request);
                  if (cast == null) {
                     return refuseCandidate(refusal, hit == null ? RiptideFaceScan.Refusal.RAY_MISSED : RiptideFaceScan.Refusal.RAY_WRONG_CELL, request, face);
                  }
               } else {
                  RiptideFaceScan.Landing landing = landingUnderHit(hitState, hitPos, hit);
                  if (landing == null) {
                     return refuseCandidate(refusal, RiptideFaceScan.Refusal.RAY_MISSED, request, face);
                  }

                  AABB hitRect = landing.rect();
                  Direction hitFace = landing.face();
                  if (onFaceRect(hitRect, hitFace, hit.getLocation(), faceMargin(eye.distanceTo(hit.getLocation())))) {
                     boolean substituted = !hitPos.equals(option.support()) || hitFace != face;
                     RiptideFaceScan.Option found = substituted ? substitute(option, hitPos, hitFace, hitState, hitRect, eye, request) : option;
                     RiptideFaceScan.Aim landed = new RiptideFaceScan.Aim(
                        hit.getLocation(),
                        cast,
                        cast,
                        eye.distanceTo(hit.getLocation()),
                        aimFrom(request) == null ? 0.0F : RiptideRotationUtil.angleTo(aimFrom(request), cast),
                        aim.tier()
                     );
                     return new RiptideFaceScan.Candidate(found, landed, hit, substituted, score(found, landed));
                  }

                  cast = retarget(aimPoint(hitRect, hitFace, eye, cast), cast, eye, reachSq, request);
                  if (cast == null) {
                     return refuseCandidate(refusal, RiptideFaceScan.Refusal.RAY_MISSED, request, face);
                  }
               }
            }

            return refuseCandidate(refusal, RiptideFaceScan.Refusal.RAY_MISSED, request, face);
         } else {
            return refuseCandidate(refusal, RiptideFaceScan.Refusal.RAY_MISSED, request, face);
         }
      } else {
         return refuseCandidate(refusal, RiptideFaceScan.Refusal.RAY_MISSED, request, null);
      }
   }

   private static RiptideRotationUtil.Rotation retarget(
      Vec3 point, RiptideRotationUtil.Rotation cast, Vec3 eye, double reachSq, RiptideFaceScan.Request request
   ) {
      if (point != null && !(eye.distanceToSqr(point) > reachSq)) {
         RiptideRotationUtil.Rotation next = RiptideRotationUtil.lookingAt(point, eye);
         if (Math.abs(next.pitch()) > request.pitchLimit()) {
            return null;
         } else {
            if (request.quantize()) {
               RiptideRotationUtil.Rotation from = aimFrom(request);
               next = RiptideRotationUtil.normalizeToSensitivity(next, from == null ? next : from);
               if (Math.abs(next.pitch()) > request.pitchLimit()) {
                  return null;
               }
            }

            return Float.compare(next.yaw(), cast.yaw()) == 0 && Float.compare(next.pitch(), cast.pitch()) == 0 ? null : next;
         }
      } else {
         return null;
      }
   }

   private static RiptideFaceScan.Option substitute(
      RiptideFaceScan.Option option, BlockPos support, Direction face, BlockState state, AABB rect, Vec3 eye, RiptideFaceScan.Request request
   ) {
      AABB flat = flatten(rect, face);
      return new RiptideFaceScan.Option(
         option.cell(),
         support.immutable(),
         face,
         support.equals(option.cell()) ? 0 : 1,
         state,
         flat,
         faceArea(flat, face),
         edgeConfidence(flat, face, eye),
         sneakUnlocks(state, support),
         new RiptideFaceScan.Intent(support, face)
      );
   }

   private static double score(RiptideFaceScan.Option option, RiptideFaceScan.Aim aim) {
      return (option.face() == Direction.UP ? 4.0 : 0.0) + option.faceArea() + option.edgeConfidence() - aim.turn() / 180.0;
   }

   public static RiptideFaceScan.Candidate best(RiptideFaceScan.Request request) {
      return best(request, null);
   }

   public static RiptideFaceScan.Candidate best(RiptideFaceScan.Request request, RiptideFaceScan.Refusal[] outcome) {
      set(outcome, RiptideFaceScan.Refusal.NONE);
      if (request != null && request.cell() != null) {
         Level level = level();
         if (level == null) {
            return null;
         } else {
            RiptideFaceScan.ScanSlot slot = scanSlot(request, level);
            List<RiptideFaceScan.Option> found = slot.options;
            int size = found.size();
            if (size == 0) {
               set(outcome, RiptideFaceScan.Refusal.NOT_A_SUPPORT);
               return null;
            } else {
               if (slot.cursor >= size) {
                  slot.cursor = 0;
               }

               RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];
               RiptideFaceScan.Refusal last = RiptideFaceScan.Refusal.NOT_A_SUPPORT;
               int start = slot.cursor;

               for (int step = 0; step < size; step++) {
                  int index = start + step;
                  if (index >= size) {
                     index -= size;
                  }

                  RiptideFaceScan.Option option = found.get(index);
                  RiptideFaceScan.Aim aim = solve(option, request, refusal);
                  if (aim == null) {
                     last = refusal[0];
                     slot.cursor = index + 1 == size ? 0 : index + 1;
                  } else {
                     RiptideFaceScan.Candidate candidate = probe(option, aim, request, refusal);
                     if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                        slot.cursor = index;
                        set(outcome, RiptideFaceScan.Refusal.NO_BUDGET);
                        return null;
                     }

                     if (candidate != null) {
                        slot.cursor = index;
                        return candidate;
                     }

                     slot.cursor = index + 1 == size ? 0 : index + 1;
                     last = refusal[0];
                  }
               }

               set(outcome, last);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static BlockHitResult confirm(
      RiptideFaceScan.Candidate candidate, RiptideRotationUtil.Rotation wire, Vec3 eye, double reach, RiptideFaceScan.Request request
   ) {
      if (candidate != null && wire != null && eye != null && request != null) {
         if (Math.abs(wire.pitch()) > 89.5F) {
            return null;
         } else {
            Level level = level();
            Player player = player();
            if (level != null && player != null) {
               BlockHitResult hit = ray(eye, wire, reach, level, player);
               if (hit == null) {
                  return null;
               } else {
                  BlockPos hitPos = hit.getBlockPos();
                  BlockState hitState = level.getBlockState(hitPos);
                  if (!request.placement().clickable(hitState, hitPos, request.sneaking())) {
                     return null;
                  } else if (!request.placement().lands(hit, candidate.option().cell())) {
                     return null;
                  } else {
                     RiptideFaceScan.Option plan = candidate.option();
                     if (hitPos.equals(plan.support()) && hit.getDirection() == plan.face() && onFaceRect(plan.rect(), plan.face(), hit.getLocation(), 0.0)) {
                        return hit;
                     } else {
                        return rectUnderHit(hitState, hitPos, hit) != null ? hit : null;
                     }
                  }
               }
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static AABB[] faceRects(BlockState state, BlockPos support, Direction face, int max) {
      AABB[] boxes = shapeBoxes(state, support);
      if (boxes.length == 0) {
         return NO_RECTS;
      } else {
         int count = Math.min(boxes.length, 16);
         AABB[] rects = new AABB[count];

         for (int i = 0; i < count; i++) {
            rects[i] = flatten(boxes[i], face);
         }

         count = mergeCoplanar(rects, count, face);

         for (int i = 1; i < count; i++) {
            AABB key = rects[i];
            double area = faceArea(key, face);

            int j;
            for (j = i - 1; j >= 0 && faceArea(rects[j], face) < area; j--) {
               rects[j + 1] = rects[j];
            }

            rects[j + 1] = key;
         }

         int kept = Math.min(count, Math.max(1, max));
         if (kept == rects.length) {
            return rects;
         } else {
            AABB[] out = new AABB[kept];
            System.arraycopy(rects, 0, out, 0, kept);
            return out;
         }
      }
   }

   public static AABB rectUnderHit(BlockState state, BlockPos pos, BlockHitResult hit) {
      RiptideFaceScan.Landing landing = landingUnderHit(state, pos, hit);
      return landing == null ? null : landing.rect();
   }

   public static RiptideFaceScan.Landing landingUnderHit(BlockState state, BlockPos pos, BlockHitResult hit) {
      if (state != null && pos != null && hit != null) {
         Vec3 point = hit.getLocation();
         Direction reported = hit.getDirection();
         AABB[] boxes = shapeBoxes(state, pos);

         for (AABB box : boxes) {
            if (onFaceRect(box, reported, point, 0.0)) {
               return new RiptideFaceScan.Landing(box, reported);
            }
         }

         for (AABB boxx : boxes) {
            for (Direction face : FACE_ORDER_UP_FIRST) {
               if (face != reported && onFaceRect(boxx, face, point, 0.0)) {
                  return new RiptideFaceScan.Landing(boxx, face);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static boolean onFaceRect(AABB rect, Direction face, Vec3 point, double margin) {
      if (rect != null && face != null && point != null) {
         Axis normal = face.getAxis();
         return Math.abs(coordinate(point, normal) - planeOf(rect, face)) > 1.0E-4
            ? false
            : within(rect, inPlaneAxis(normal, true), point, margin) && within(rect, inPlaneAxis(normal, false), point, margin);
      } else {
         return false;
      }
   }

   private static boolean within(AABB rect, Axis axis, Vec3 point, double margin) {
      double inset = acceptInset(rect, axis, margin);
      double at = coordinate(point, axis);
      return at >= rect.min(axis) + inset - 1.0E-4 && at <= rect.max(axis) - inset + 1.0E-4;
   }

   private static double acceptInset(AABB rect, Axis axis, double margin) {
      double extent = rect.max(axis) - rect.min(axis);
      return Math.max(0.0, Math.min(margin, extent * 0.5 - margin));
   }

   public static double eyePastPlane(Vec3 eye, AABB rect, Direction face) {
      if (eye != null && rect != null && face != null) {
         double plane = planeOf(rect, face);
         double along = coordinate(eye, face.getAxis());
         return (along - plane) * (face.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : -1.0);
      } else {
         return 0.0;
      }
   }

   public static Vec3 faceCentre(AABB rect, Direction face) {
      Axis normal = face.getAxis();
      Axis first = inPlaneAxis(normal, true);
      Axis second = inPlaneAxis(normal, false);
      return onPlane(normal, planeOf(rect, face), (rect.min(first) + rect.max(first)) * 0.5, (rect.min(second) + rect.max(second)) * 0.5);
   }

   public static boolean selfOccluded(Vec3 eye, BlockPos support, Direction face, Level level) {
      if (eye == null || support == null || face == null || level == null) {
         return false;
      } else if (!face.getAxis().isHorizontal()) {
         return false;
      } else if (Mth.floor(eye.x) == support.getX() && Mth.floor(eye.z) == support.getZ()) {
         BlockPos lid = support.above();
         return eye.y >= lid.getY() + 1.0 && level.getBlockState(lid).isCollisionShapeFullBlock(level, lid);
      } else {
         return false;
      }
   }

   public static boolean useActionEatsClick(BlockState state, BlockPos pos, boolean sneaking) {
      return state != null && pos != null && hasUseAction(state, pos) ? !sneaking || !(state.getBlock() instanceof BedBlock) : false;
   }

   public static boolean sneakUnlocks(BlockState state, BlockPos pos) {
      return useActionEatsClick(state, pos, false) && !useActionEatsClick(state, pos, true);
   }

   public static boolean isPlaceableSupport(BlockState state, BlockPos support, boolean sneaking) {
      if (state == null || support == null || state.isAir()) {
         return false;
      } else {
         return shapeBoxes(state, support).length == 0 ? false : !useActionEatsClick(state, support, sneaking);
      }
   }

   public static boolean replaceableInPlace(BlockPos cell, Level level) {
      if (cell != null && level != null) {
         BlockState state = level.getBlockState(cell);
         if (state.isAir() || !state.canBeReplaced()) {
            return false;
         } else {
            return hasUseAction(state, cell) ? false : shapeBoxes(state, cell).length > 0;
         }
      } else {
         return false;
      }
   }

   public static BlockHitResult ray(Vec3 eye, RiptideRotationUtil.Rotation rotation, double reach, Level level, Entity entity) {
      if (eye != null && rotation != null && level != null) {
         Vec3 end = eye.add(lookVector(rotation).scale(reach));
         HitResult result = level.clip(
            new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, entity == null ? CollisionContext.empty() : CollisionContext.of(entity))
         );
         return result instanceof BlockHitResult blockHit && result.getType() == Type.BLOCK ? blockHit : null;
      } else {
         return null;
      }
   }

   public static Axis inPlaneAxis(Axis normal, boolean first) {
      return switch (normal) {
         case X -> first ? Axis.Y : Axis.Z;
         case Y -> first ? Axis.X : Axis.Z;
         case Z -> first ? Axis.X : Axis.Y;
         default -> throw new MatchException(null, null);
      };
   }

   public static Vec3 onPlane(Axis normal, double plane, double a, double b) {
      return switch (normal) {
         case X -> new Vec3(plane, a, b);
         case Y -> new Vec3(a, plane, b);
         case Z -> new Vec3(a, b, plane);
         default -> throw new MatchException(null, null);
      };
   }

   public static double coordinate(Vec3 point, Axis axis) {
      return switch (axis) {
         case X -> point.x;
         case Y -> point.y;
         case Z -> point.z;
         default -> throw new MatchException(null, null);
      };
   }

   public static void invalidateShapeCache() {
      for (int i = 0; i < 8; i++) {
         shapePos[i] = null;
         shapeState[i] = null;
         shapeBoxes[i] = null;
      }

      for (int i = 0; i < 48; i++) {
         if (scanSlots[i] != null) {
            scanSlots[i].keyKnown = false;
         }
      }
   }

   private static RiptideFaceScan.ScanSlot scanSlot(RiptideFaceScan.Request request, Level level) {
      long key = optionsKey(request, level);

      for (int i = 0; i < 48; i++) {
         RiptideFaceScan.ScanSlot slot = scanSlots[i];
         if (slot != null && slot.keyKnown && slot.key == key) {
            return slot;
         }
      }

      RiptideFaceScan.ScanSlot slot = scanSlots[scanSlotCursor];
      if (slot == null) {
         slot = scanSlots[scanSlotCursor] = new RiptideFaceScan.ScanSlot();
      }

      scanSlotCursor = (scanSlotCursor + 1) % 48;
      slot.options.clear();
      slot.cursor = 0;
      Predicate<RiptideFaceScan.Intent> held = request.banned();
      request.banned(null);
      options(request, slot.options);
      request.banned(held);
      slot.key = key;
      slot.keyKnown = true;
      return slot;
   }

   private static long optionsKey(RiptideFaceScan.Request request, Level level) {
      long key = cellKey(request.cell(), level, new MutableBlockPos());
      key = key * 31L + System.identityHashCode(level);

      for (Direction face : request.faceOrder()) {
         key = key * 31L + face.ordinal();
      }

      EnumSet<Direction> allowed = request.allowedFaces();
      key = key * 31L + (allowed == null ? -1L : allowed.hashCode());
      key = key * 31L + (request.allowInPlace() ? 1L : 0L);
      key = key * 31L + request.maxRectsPerFace();
      key = key * 31L + (request.sneaking() ? 2L : 0L);
      return key * 31L + (request.sneakAllowed() ? 4L : 0L);
   }

   private static AABB[] shapeBoxes(BlockState state, BlockPos pos) {
      if (state != null && pos != null) {
         for (int i = 0; i < 8; i++) {
            if (shapeState[i] == state && pos.equals(shapePos[i])) {
               return shapeBoxes[i];
            }
         }

         Level level = level();
         if (level == null) {
            return NO_RECTS;
         } else {
            Player player = player();
            VoxelShape shape = state.getShape(level, pos, player == null ? CollisionContext.empty() : CollisionContext.of(player));
            List<AABB> local = shape.isEmpty() ? List.of() : shape.toAabbs();
            AABB[] boxes = local.isEmpty() ? NO_RECTS : new AABB[local.size()];

            for (int ix = 0; ix < local.size(); ix++) {
               boxes[ix] = local.get(ix).move(pos);
            }

            shapePos[shapeCursor] = pos.immutable();
            shapeState[shapeCursor] = state;
            shapeBoxes[shapeCursor] = boxes;
            shapeCursor = (shapeCursor + 1) % 8;
            return boxes;
         }
      } else {
         return NO_RECTS;
      }
   }

   private static int mergeCoplanar(AABB[] rects, int count, Direction face) {
      Axis normal = face.getAxis();
      Axis first = inPlaneAxis(normal, true);
      Axis second = inPlaneAxis(normal, false);
      boolean merged = true;

      while (merged && count > 1) {
         merged = false;

         for (int i = 0; i < count && !merged; i++) {
            for (int j = i + 1; j < count; j++) {
               if (!(Math.abs(planeOf(rects[i], face) - planeOf(rects[j], face)) > 1.0E-4)) {
                  AABB union = exactUnion(rects[i], rects[j], first, second);
                  if (union != null) {
                     rects[i] = union;
                     rects[j] = rects[count - 1];
                     count--;
                     merged = true;
                     break;
                  }
               }
            }
         }
      }

      return count;
   }

   private static AABB exactUnion(AABB left, AABB right, Axis first, Axis second) {
      if (contains(left, right, first, second)) {
         return left;
      } else if (contains(right, left, first, second)) {
         return right;
      } else if (sameExtent(left, right, first) && touches(left, right, second)) {
         return left.minmax(right);
      } else {
         return sameExtent(left, right, second) && touches(left, right, first) ? left.minmax(right) : null;
      }
   }

   private static boolean contains(AABB outer, AABB inner, Axis first, Axis second) {
      return outer.min(first) - 1.0E-4 <= inner.min(first)
         && outer.max(first) + 1.0E-4 >= inner.max(first)
         && outer.min(second) - 1.0E-4 <= inner.min(second)
         && outer.max(second) + 1.0E-4 >= inner.max(second);
   }

   private static boolean sameExtent(AABB left, AABB right, Axis axis) {
      return Math.abs(left.min(axis) - right.min(axis)) <= 1.0E-4 && Math.abs(left.max(axis) - right.max(axis)) <= 1.0E-4;
   }

   private static boolean touches(AABB left, AABB right, Axis axis) {
      return left.min(axis) <= right.max(axis) + 1.0E-4 && right.min(axis) <= left.max(axis) + 1.0E-4;
   }

   private static AABB flatten(AABB box, Direction face) {
      double plane = planeOf(box, face);

      return switch (face.getAxis()) {
         case X -> new AABB(plane, box.minY, box.minZ, plane, box.maxY, box.maxZ);
         case Y -> new AABB(box.minX, plane, box.minZ, box.maxX, plane, box.maxZ);
         case Z -> new AABB(box.minX, box.minY, plane, box.maxX, box.maxY, plane);
         default -> throw new MatchException(null, null);
      };
   }

   private static double planeOf(AABB rect, Direction face) {
      Axis normal = face.getAxis();
      return face.getAxisDirection() == AxisDirection.POSITIVE ? rect.max(normal) : rect.min(normal);
   }

   private static double faceArea(AABB rect, Direction face) {
      Axis normal = face.getAxis();
      Axis first = inPlaneAxis(normal, true);
      Axis second = inPlaneAxis(normal, false);
      return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
   }

   private static boolean hasUseAction(BlockState state, BlockPos pos) {
      Level level = level();
      if (level != null && state.getMenuProvider(level, pos) != null) {
         return true;
      } else {
         net.minecraft.world.level.block.Block block = state.getBlock();
         if (block instanceof FenceBlock) {
            return leadingLeashedMob();
         } else {
            boolean safeBlockEntity = block instanceof AbstractSkullBlock
               || block instanceof ConduitBlock
               || block instanceof SculkSensorBlock
               || block instanceof SculkShriekerBlock
               || block instanceof SculkCatalystBlock
               || block instanceof SpawnerBlock
               || block instanceof BrushableBlock;
            return !safeBlockEntity && block instanceof BaseEntityBlock
               ? true
               : block instanceof BedBlock
                  || block instanceof DoorBlock
                  || block instanceof TrapDoorBlock
                  || block instanceof FenceGateBlock
                  || block instanceof ButtonBlock
                  || block instanceof LeverBlock
                  || block instanceof NoteBlock
                  || block instanceof CakeBlock
                  || block instanceof CandleCakeBlock
                  || block instanceof RespawnAnchorBlock
                  || block instanceof DiodeBlock
                  || block instanceof DragonEggBlock
                  || block instanceof RedStoneWireBlock
                  || block instanceof RedStoneOreBlock
                  || block instanceof FlowerPotBlock
                  || block instanceof AbstractCauldronBlock
                  || block instanceof ComposterBlock
                  || block instanceof CaveVines
                  || block instanceof SweetBerryBushBlock;
         }
      }
   }

   private static boolean leadingLeashedMob() {
      Player player = player();
      if (player == null) {
         return false;
      } else {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != leashTick) {
            leashTick = tick;
            leashLeading = !Leashable.leashableInArea(player, led -> led.getLeashHolder() == player).isEmpty();
         }

         return leashLeading;
      }
   }

   private static Vec3 lookVector(RiptideRotationUtil.Rotation rotation) {
      float yaw = -rotation.yaw() * (float) (Math.PI / 180.0);
      float pitch = -rotation.pitch() * (float) (Math.PI / 180.0);
      float cosPitch = Mth.cos(pitch);
      return new Vec3(Mth.sin(yaw) * cosPitch, Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
   }

   private static RiptideRotationUtil.Rotation aimFrom(RiptideFaceScan.Request request) {
      if (request.from() != null) {
         return request.from();
      } else {
         Player player = player();
         return player == null ? null : new RiptideRotationUtil.Rotation(player.getYRot(), player.getXRot());
      }
   }

   private static Level level() {
      Minecraft mc = Minecraft.getInstance();
      return mc == null ? null : mc.level;
   }

   private static Player player() {
      Minecraft mc = Minecraft.getInstance();
      return mc == null ? null : mc.player;
   }

   private static void set(RiptideFaceScan.Refusal[] refusal, RiptideFaceScan.Refusal value) {
      if (refusal != null && refusal.length > 0) {
         refusal[0] = value;
      }
   }

   private static RiptideFaceScan.Aim refuse(RiptideFaceScan.Refusal[] refusal, RiptideFaceScan.Refusal reason, RiptideFaceScan.Request request, Direction face) {
      set(refusal, reason);
      note(request, face, reason);
      return null;
   }

   private static RiptideFaceScan.Candidate refuseCandidate(
      RiptideFaceScan.Refusal[] refusal, RiptideFaceScan.Refusal reason, RiptideFaceScan.Request request, Direction face
   ) {
      set(refusal, reason);
      note(request, face, reason);
      return null;
   }

   private static void note(RiptideFaceScan.Request request, Direction face, RiptideFaceScan.Refusal reason) {
      if (request != null && face != null) {
         StringBuilder trace = request.trace();
         if (trace != null && trace.length() < 96) {
            if (trace.length() > 0) {
               trace.append(' ');
            }

            trace.append(face.getSerializedName().charAt(0)).append(':').append(reason.code());
         }
      }
   }

   public record Aim(
      Vec3 point, RiptideRotationUtil.Rotation goal, RiptideRotationUtil.Rotation emitted, double distance, float turn, RiptideFaceScan.Tier tier
   ) {
   }

   public static final class Budget {
      private int rays;

      public Budget(int rays) {
         this.rays = Math.max(0, rays);
      }

      public int remaining() {
         return this.rays;
      }

      public boolean spend() {
         if (this.rays <= 0) {
            return false;
         } else {
            this.rays--;
            return true;
         }
      }

      public void reset(int rays) {
         this.rays = Math.max(0, rays);
      }
   }

   public record Candidate(RiptideFaceScan.Option option, RiptideFaceScan.Aim aim, BlockHitResult hit, boolean substituted, double score) {
   }

   public record Intent(BlockPos support, Direction face) {
      public Intent(BlockPos support, Direction face) {
         support = support == null ? null : support.immutable();
         this.support = support;
         this.face = face;
      }
   }

   public record Landing(AABB rect, Direction face) {
   }

   public record Option(
      BlockPos cell,
      BlockPos support,
      Direction face,
      int form,
      BlockState state,
      AABB rect,
      double faceArea,
      double edgeConfidence,
      boolean requiresSneak,
      RiptideFaceScan.Intent intent
   ) {
   }

   public interface Placement {
      boolean lands(BlockHitResult var1, BlockPos var2);

      default boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
         return !RiptideFaceScan.useActionEatsClick(state, pos, sneaking);
      }
   }

   public static enum Refusal {
      NONE("ok"),
      NOT_A_SUPPORT("ns"),
      SELF_OCCLUDED("so"),
      BEHIND_PLANE("sd"),
      OUT_OF_REACH("rc"),
      PAST_PITCH_CAP("pc"),
      BANNED("st"),
      OUTRANKED("pr"),
      RAY_MISSED("rm"),
      RAY_WRONG_CELL("rw"),
      NO_BUDGET("bg");

      private final String code;

      private Refusal(String code) {
         this.code = code;
      }

      public String code() {
         return this.code;
      }
   }

   public static final class Request {
      private BlockPos cell;
      private final Vec3 eye;
      private final double reach;
      private RiptideFaceScan.Placement placement;
      private RiptideRotationUtil.Rotation from;
      private double pitchLimit = RiptideFaceScan.goalPitchLimit();
      private boolean sneaking;
      private boolean sneakAllowed;
      private Direction[] faceOrder = RiptideFaceScan.FACE_ORDER_UP_FIRST;
      private EnumSet<Direction> allowedFaces;
      private boolean allowInPlace = true;
      private Vec3 leadEye;
      private Predicate<RiptideFaceScan.Intent> banned;
      private RiptideFaceScan.Budget budget;
      private int maxRectsPerFace = 3;
      private boolean quantize = true;
      private StringBuilder trace;

      public Request(BlockPos cell, Vec3 eye, double reach, RiptideFaceScan.Placement placement) {
         this.cell = cell == null ? null : cell.immutable();
         this.eye = eye;
         this.reach = reach;
         this.placement = placement;
      }

      public RiptideFaceScan.Request cell(BlockPos cell) {
         this.cell = cell == null ? null : cell.immutable();
         return this;
      }

      public RiptideFaceScan.Request placement(RiptideFaceScan.Placement placement) {
         this.placement = placement;
         return this;
      }

      public RiptideFaceScan.Request from(RiptideRotationUtil.Rotation from) {
         this.from = from;
         return this;
      }

      public RiptideFaceScan.Request pitchLimit(double degrees) {
         this.pitchLimit = degrees;
         return this;
      }

      public RiptideFaceScan.Request sneaking(boolean sneaking) {
         this.sneaking = sneaking;
         return this;
      }

      public RiptideFaceScan.Request sneakAllowed(boolean allowed) {
         this.sneakAllowed = allowed;
         return this;
      }

      public RiptideFaceScan.Request faceOrder(Direction[] order) {
         this.faceOrder = order != null && order.length != 0 ? order : RiptideFaceScan.FACE_ORDER_UP_FIRST;
         return this;
      }

      public RiptideFaceScan.Request allowedFaces(EnumSet<Direction> faces) {
         this.allowedFaces = faces;
         return this;
      }

      public RiptideFaceScan.Request allowInPlace(boolean allow) {
         this.allowInPlace = allow;
         return this;
      }

      public RiptideFaceScan.Request leadEye(Vec3 leadEye) {
         this.leadEye = leadEye;
         return this;
      }

      public RiptideFaceScan.Request banned(Predicate<RiptideFaceScan.Intent> banned) {
         this.banned = banned;
         return this;
      }

      public RiptideFaceScan.Request budget(RiptideFaceScan.Budget budget) {
         this.budget = budget;
         return this;
      }

      public RiptideFaceScan.Request maxRectsPerFace(int max) {
         this.maxRectsPerFace = Math.max(1, max);
         return this;
      }

      public RiptideFaceScan.Request quantize(boolean quantize) {
         this.quantize = quantize;
         return this;
      }

      public RiptideFaceScan.Request trace(StringBuilder trace) {
         this.trace = trace;
         return this;
      }

      public BlockPos cell() {
         return this.cell;
      }

      public Vec3 eye() {
         return this.eye;
      }

      public double reach() {
         return this.reach;
      }

      public RiptideFaceScan.Placement placement() {
         return this.placement;
      }

      public RiptideRotationUtil.Rotation from() {
         return this.from;
      }

      public double pitchLimit() {
         return this.pitchLimit;
      }

      public boolean sneaking() {
         return this.sneaking;
      }

      public boolean sneakAllowed() {
         return this.sneakAllowed;
      }

      public Direction[] faceOrder() {
         return this.faceOrder;
      }

      public EnumSet<Direction> allowedFaces() {
         return this.allowedFaces;
      }

      public boolean allowInPlace() {
         return this.allowInPlace;
      }

      public Vec3 leadEye() {
         return this.leadEye;
      }

      public Predicate<RiptideFaceScan.Intent> banned() {
         return this.banned;
      }

      public RiptideFaceScan.Budget budget() {
         return this.budget;
      }

      public int maxRectsPerFace() {
         return this.maxRectsPerFace;
      }

      public boolean quantize() {
         return this.quantize;
      }

      public StringBuilder trace() {
         return this.trace;
      }
   }

   private static final class ScanSlot {
      private final List<RiptideFaceScan.Option> options = new ArrayList<>();
      private long key;
      private boolean keyKnown;
      private int cursor;
   }

   public static enum Tier {
      CENTRE,
      NEAREST,
      FLATTEST,
      ESCAPE;
   }
}
