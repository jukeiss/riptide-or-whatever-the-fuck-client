package riptide.util.multi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import riptide.modules.TpClickModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptidePathWalker;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.PacedTpAction;
import riptide.util.macro.PacketClipSafety;
import riptide.util.macro.PacketRoutePlanner;

public final class PacketTeleportController {
   public static final int DEFAULT_MAX_PACKETS = 20;
   public static final int DEFAULT_PAUSE_MS = 500;
   public static final double DEFAULT_STEP = 10.0;
   private static final double VEHICLE_STEP = 4.0;
   private static final double MIN_STEP = 0.0625;
   private static final double CORRECTION_MATCH_EPSILON = 0.05;
   private static final long RECOVERY_PROBE_DELAY_MS = 100L;
   private static final long RECOVERY_STABLE_MS = 600L;
   private static final long ARRIVAL_SETTLE_MS = 250L;
   private static final double ARRIVAL_EPSILON = 0.5;
   private static final long JOB_STALL_TIMEOUT_MS = 3500L;
   private static final long RESCUE_EXHAUSTED_MS = 12000L;
   private static final int MAX_RESCUE_LEVEL = 3;
   private static final long JOB_TOTAL_TIMEOUT_MS = 300000L;
   private static final int HYBRID_FRONTIER_BLOCKS = 80;
   private static final int BLIND_POV_FRONTIER_BLOCKS = 32;
   private static final ThreadLocal<Boolean> OWNED_SEND = ThreadLocal.withInitial(() -> false);
   private static final AtomicInteger OWNED_SEND_SCOPES = new AtomicInteger();
   private static final Object LOCK = new Object();
   private static final AtomicLong MACRO_IDS = new AtomicLong();
   private static volatile PacketTeleportController.Job active;
   private static boolean inputWasBlocked;

   private PacketTeleportController() {
   }

   public static PacketTeleportController.CommandRequest parse(String arguments, Vec3 origin, int configuredPackets, int configuredPause) {
      String trimmed = arguments == null ? "" : arguments.trim();
      if (trimmed.isEmpty()) {
         return new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.HELP, null, 0, 0, "");
      } else {
         String[] parts = trimmed.split("\\s+");
         String first = parts[0].toLowerCase(Locale.ROOT);
         if ("stop".equals(first)) {
            return parts.length == 1
               ? new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.STOP, null, 0, 0, "")
               : PacketTeleportController.CommandRequest.error("Usage: tp stop");
         } else if ("status".equals(first)) {
            return parts.length == 1
               ? new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.STATUS, null, 0, 0, "")
               : PacketTeleportController.CommandRequest.error("Usage: tp status");
         } else if ("reset".equals(first)) {
            return parts.length == 1
               ? new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.RESET, null, 20, 500, "")
               : PacketTeleportController.CommandRequest.error("Usage: tp reset");
         } else if ("config".equals(first)) {
            if (parts.length != 3) {
               return PacketTeleportController.CommandRequest.error("Usage: tp config <maxPackets> <pauseMs>");
            } else {
               Integer packets = boundedInteger(parts[1], 1, 100);
               Integer pause = boundedInteger(parts[2], 50, 10000);
               if (packets == null) {
                  return PacketTeleportController.CommandRequest.error("maxPackets must be 1-100");
               } else {
                  return pause == null
                     ? PacketTeleportController.CommandRequest.error("pauseMs must be 50-10000")
                     : new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.CONFIG, null, packets, pause, "");
               }
            }
         } else {
            int offset = 0;
            if ("fast".equals(first)) {
               offset = 1;
            }

            if (parts.length - offset >= 3 && parts.length - offset <= 6 && origin != null) {
               Double x = coordinate(parts[offset], origin.x);
               Double y = coordinate(parts[offset + 1], origin.y);
               Double z = coordinate(parts[offset + 2], origin.z);
               if (x != null && y != null && z != null) {
                  int packets = clamp(configuredPackets, 1, 100);
                  int pause = clamp(configuredPause, 50, 10000);
                  int option = offset + 3;
                  if (option < parts.length && "fast".equalsIgnoreCase(parts[option])) {
                     option++;
                  }

                  if (option < parts.length) {
                     Integer parsed = boundedInteger(parts[option], 1, 100);
                     if (parsed == null) {
                        return PacketTeleportController.CommandRequest.error("maxPackets must be 1-100");
                     }

                     packets = parsed;
                     option++;
                  }

                  if (option < parts.length) {
                     Integer parsed = boundedInteger(parts[option], 50, 10000);
                     if (parsed == null) {
                        return PacketTeleportController.CommandRequest.error("pauseMs must be 50-10000");
                     }

                     pause = parsed;
                     option++;
                  }

                  return option != parts.length
                     ? PacketTeleportController.CommandRequest.error("Usage: tp <x> <y> <z> [maxPackets] [pauseMs]")
                     : new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.START, new Vec3(x, y, z), packets, pause, "");
               } else {
                  return PacketTeleportController.CommandRequest.error("Coordinates must be numbers or ~ offsets");
               }
            } else {
               return PacketTeleportController.CommandRequest.error("Usage: tp <x> <y> <z> [maxPackets] [pauseMs]");
            }
         }
      }
   }

   public static String executeMain(String arguments) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc == null ? null : mc.player;
      RiptideConfig config = RiptideConfig.getGlobal();
      PacketTeleportController.CommandRequest request = parse(
         arguments, player == null ? null : controlledMainPosition(player), config.tpMaxPackets, config.tpPauseMs
      );
      String common = applyCommon(request, null);
      if (common != null) {
         return common;
      } else if (player == null || mc.getConnection() == null || player.level() == null) {
         return "Join a world first";
      } else if (MultiPilot.isActive()) {
         return "POV owns player commands";
      } else if (MacroExecutor.isRunning()) {
         return "Stop the running macro first";
      } else {
         Entity vehicle = player.getVehicle();
         synchronized (LOCK) {
            active = PacketTeleportController.Job.main(player, mc.getConnection(), player.level(), vehicle, request);
         }

         neutralizeMain(player);
         return startedMessage(request.destination(), request.maxPackets(), request.pauseMs(), vehicle != null);
      }
   }

   public static PacketTeleportController.MacroHandle startMacro(PacedTpAction action) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc == null ? null : mc.player;
      if (action == null || player == null || mc.getConnection() == null || player.level() == null) {
         return completedMacro(false, "No world or connection");
      } else if (MultiPilot.isActive()) {
         return completedMacro(false, "POV owns player movement");
      } else {
         Vec3 origin = controlledMainPosition(player);
         PacketTeleportController.CommandRequest request = parse(action.commandArguments(), origin, action.maxPackets, action.pauseMs);
         if (request.kind() != PacketTeleportController.CommandKind.START) {
            return completedMacro(false, request.error().isBlank() ? "Invalid TP action" : request.error());
         } else {
            cancelAll("replaced by macro TP");
            long id = MACRO_IDS.incrementAndGet();
            CompletableFuture<PacketTeleportController.MacroResult> completion = new CompletableFuture<>();
            Entity vehicle = player.getVehicle();
            PacketTeleportController.Job job = PacketTeleportController.Job.mainMacro(
               player, mc.getConnection(), player.level(), vehicle, request, id, completion
            );
            synchronized (LOCK) {
               active = job;
            }

            neutralizeMain(player);
            return new PacketTeleportController.MacroHandle(id, completion);
         }
      }
   }

   public static void cancelMacro(PacketTeleportController.MacroHandle handle, String reason) {
      if (handle != null) {
         PacketTeleportController.Job job;
         synchronized (LOCK) {
            job = active != null && active.macroId == handle.id() ? active : null;
         }

         if (job != null) {
            finish(job, false, reason == null ? "macro stopped" : reason);
         }
      }
   }

   private static PacketTeleportController.MacroHandle completedMacro(boolean success, String detail) {
      CompletableFuture<PacketTeleportController.MacroResult> future = CompletableFuture.completedFuture(
         new PacketTeleportController.MacroResult(success, detail)
      );
      return new PacketTeleportController.MacroHandle(0L, future);
   }

   static String executePov(String arguments) {
      MultiSession session = MultiPilot.activeCommandSession();
      RemotePlayer bot = MultiPilot.activeBotEntity();
      RiptideConfig config = RiptideConfig.getGlobal();
      Vec3 origin = session == null ? null : session.takeoverPosition().position();
      PacketTeleportController.CommandRequest request = parse(arguments, origin, config.tpMaxPackets, config.tpPauseMs);
      String common = applyCommon(request, session);
      if (common != null) {
         return common;
      } else if (session == null || bot == null || !session.pilotPacketsReady()) {
         return "POV session is not ready";
      } else if (session.macroOwnsPilot()) {
         return "Macro owns the POV bot";
      } else if (bot.isPassenger()) {
         return "Vehicle POV is unsupported";
      } else {
         synchronized (LOCK) {
            active = PacketTeleportController.Job.pov(session, bot, request);
         }

         MultiPilot.preparePacedTeleport(session, bot);
         return startedMessage(request.destination(), request.maxPackets(), request.pauseMs(), false);
      }
   }

   private static String applyCommon(PacketTeleportController.CommandRequest request, MultiSession owner) {
      if (request.kind() == PacketTeleportController.CommandKind.ERROR) {
         return request.error();
      } else if (request.kind() == PacketTeleportController.CommandKind.HELP) {
         return "Usage: tp <x> <y> <z> [maxPackets] [pauseMs]";
      } else if (request.kind() == PacketTeleportController.CommandKind.CONFIG || request.kind() == PacketTeleportController.CommandKind.RESET) {
         RiptideConfig config = RiptideConfig.getGlobal();
         config.tpMaxPackets = request.maxPackets();
         config.tpPauseMs = request.pauseMs();
         config.save();
         return "TP defaults: " + request.maxPackets() + " packets, " + request.pauseMs() + " ms";
      } else if (request.kind() == PacketTeleportController.CommandKind.STATUS) {
         return status(owner);
      } else {
         return request.kind() == PacketTeleportController.CommandKind.STOP ? stop(owner, "stopped by command") : null;
      }
   }

   public static void tick(Minecraft mc) {
      PacketTeleportController.Job job = active;
      boolean settling = job != null && job.arrivalCandidateAt != 0L;
      boolean blockInput = job != null && job.owner == PacketTeleportController.Owner.MAIN && mc != null && !settling && TpClickModule.blocksMovement();
      if (blockInput) {
         releaseMovementInput(mc);
         inputWasBlocked = true;
      } else if (inputWasBlocked) {
         inputWasBlocked = false;
         restoreMovementInput(mc);
      }

      if (job != null && mc != null) {
         job.tickCounter++;
         long now = System.currentTimeMillis();
         String invalid = validateOwner(job, mc);
         if (invalid != null) {
            finish(job, false, invalid);
         } else if (now - job.startedAt > 300000L) {
            finish(job, false, "timeout");
         } else {
            if (now - job.lastActivityAt > 3500L) {
               if (job.rescueLevel < 3) {
                  job.rescueLevel++;
                  job.lastActivityAt = now;
                  job.frames.clear();
                  job.waypoints.clear();
                  job.activeWaypoint = null;
                  job.lastPlanTick = Long.MIN_VALUE;
               } else if (now - job.lastActivityAt > 15500L) {
                  finish(job, false, "timeout");
                  return;
               }
            }

            if (!settling && physicalMovementDown(mc)) {
               applyRendered(job, job.current, protocolGroundAt(job, job.current));
               finish(job, false, "cancelled by movement input");
            } else {
               PacketTeleportController.Correction correction;
               synchronized (LOCK) {
                  correction = job.pendingCorrection;
                  job.pendingCorrection = null;
               }

               if (correction != null) {
                  double predictedDistance = job.current.distanceTo(job.requestedTarget);
                  job.current = correction.position();
                  if (job.current.distanceTo(job.requestedTarget) <= 2.5) {
                     job.frames.clear();
                     job.waypoints.clear();
                     job.activeWaypoint = null;
                     job.arrivalCandidateAt = now;
                     job.lastProgressAt = now;
                     applyRendered(job, job.current, protocolGroundAt(job, job.current));
                     return;
                  }

                  job.frames.clear();
                  job.waypoints.clear();
                  job.activeWaypoint = null;
                  job.arrivalCandidateAt = 0L;
                  double distance = job.current.distanceTo(job.requestedTarget);
                  double predictionError = correction.position().distanceTo(job.lastSentPosition);
                  if (correctionAccepted(job.bestDistance, predictedDistance, distance, predictionError)) {
                     job.bestDistance = Math.min(job.bestDistance, distance);
                     job.rejectionCount = 0;
                     job.lastProgressAt = now;
                     job.lastFailedTarget = null;
                     job.sameTargetFailures = 0;
                     job.roofY = Double.NaN;
                     job.effectiveStep = Math.min(10.0, job.effectiveStep * 1.25);
                     job.pacer.rebase(now, job.incrementalRecovery ? 100L : 0L);
                  } else {
                     job.rejectionCount++;
                     if (job.lastPlannedTarget != null) {
                        if (job.lastFailedTarget != null && job.lastFailedTarget.distanceTo(job.lastPlannedTarget) < 1.5) {
                           job.sameTargetFailures++;
                        } else {
                           job.sameTargetFailures = 1;
                        }

                        job.lastFailedTarget = job.lastPlannedTarget;
                     }

                     double reduced = job.rejectionCount >= 3
                        ? job.effectiveStep * 0.5
                        : (job.rejectionCount == 2 ? Math.max(0.5, job.effectiveStep * 0.25) : Math.max(2.0, job.effectiveStep * 0.5));
                     job.effectiveStep = Math.max(0.0625, reduced);
                     job.incrementalRecovery = true;
                     job.recoveryAnchorDistance = distance;
                     job.recoveryNextSendAt = now + 100L;
                     job.recoveryStableAt = now + 600L;
                     job.pacer.rebase(now, 100L);
                  }

                  if (stableAnchorAt(job, job.current)) {
                     job.spatialLegsSinceStable = 0;
                  }

                  applyRendered(job, job.current, protocolGroundAt(job, job.current));
               }

               if (armLavaEscape(job, now)) {
                  Vec3 destination = job.lavaEmergency ? job.requestedTarget : resolveDestination(job);
                  if (destination != null) {
                     if (job.current.distanceTo(destination) <= 0.5) {
                        PacketRoutePlanner.CollisionView view = collisionView(job);
                        if (view != null && view.loaded(job.current) && !view.clear(job.current)) {
                           Vec3 clearSpot = PacketRoutePlanner.nearestClear(view, job.current, 3.0);
                           if (clearSpot != null && view.lavaSafe(clearSpot)) {
                              job.activeWaypoint = clearSpot;
                              job.arrivalCandidateAt = 0L;
                              return;
                           }
                        }

                        if (job.arrivalCandidateAt == 0L) {
                           job.arrivalCandidateAt = now;
                        }

                        if (now - job.arrivalCandidateAt >= 250L) {
                           finish(job, true, landingMessage(job));
                        }
                     } else {
                        job.arrivalCandidateAt = 0L;
                        if (job.incrementalRecovery) {
                           double recoveryDistance = job.current.distanceTo(destination);
                           if (now >= job.recoveryStableAt && job.recoveryAnchorDistance - recoveryDistance >= 0.5) {
                              job.incrementalRecovery = false;
                              job.recoveryNextSendAt = 0L;
                              job.effectiveStep = Math.min(10.0, Math.max(1.0, job.effectiveStep * 2.0));
                              job.pacer.rebase(now, 0L);
                           } else if (now < job.recoveryNextSendAt) {
                              return;
                           }
                        }

                        if (!job.pacer.waiting(now)) {
                           int budget = job.incrementalRecovery ? 1 : job.pacer.remainingInWindow();

                           while (budget-- > 0) {
                              if (job.frames.isEmpty() && !prepareNextFrames(job, destination, now, budget + 1)) {
                                 return;
                              }

                              PacketTeleportController.MoveFrame frame = job.frames.pollFirst();
                              if (frame == null) {
                                 return;
                              }

                              if (!frameLavaSafe(job, frame)) {
                                 job.frames.clear();
                                 job.waypoints.clear();
                                 job.activeWaypoint = null;
                                 job.resolvedTarget = null;
                                 job.lavaEmergency = false;
                                 job.pacer.rebase(now, 100L);
                                 return;
                              }

                              boolean emergencyFrame = job.lavaEmergency && frame.moves();
                              if (!send(job, frame)) {
                                 finish(job, false, "movement connection became unavailable");
                                 return;
                              }

                              int sentPackets = packetCount(job, frame);
                              job.totalPackets += sentPackets;

                              for (int i = 0; i < sentPackets; i++) {
                                 job.pacer.markSent(now);
                              }

                              budget -= sentPackets - 1;
                              if (frame.moves()) {
                                 job.current = frame.position();
                                 job.lastSentPosition = job.current;
                                 job.lastActivityAt = now;
                                 if (job.current.distanceTo(destination) < job.bestDistance - 0.01) {
                                    job.lastProgressAt = now;
                                 }

                                 job.spatialLegsSinceStable = nextGroundingCounter(job.spatialLegsSinceStable, stableAnchorAt(job, job.current));
                                 applyRendered(job, job.current, frame.onGround());
                                 if (job.incrementalRecovery) {
                                    job.recoveryNextSendAt = now + 100L;
                                 }
                              }

                              if (emergencyFrame) {
                                 job.lavaEmergency = false;
                                 job.incrementalRecovery = false;
                                 job.pacer.rebase(now, 0L);
                                 return;
                              }

                              if (job.current.distanceTo(destination) <= 0.5 && job.frames.isEmpty() && job.waypoints.isEmpty() && job.activeWaypoint == null) {
                                 job.arrivalCandidateAt = now;
                                 return;
                              }

                              if (job.pacer.waiting(now)) {
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Vec3 resolveDestination(PacketTeleportController.Job job) {
      PacketRoutePlanner.CollisionView view = collisionView(job);
      if (job.resolvedTarget != null) {
         if (view == null || !view.loaded(job.resolvedTarget)) {
            return null;
         }

         if (view.clear(job.resolvedTarget) && view.lavaSafe(job.resolvedTarget) && (!job.adjustedLanding || view.traversable(job.resolvedTarget))) {
            return job.resolvedTarget;
         }

         job.resolvedTarget = null;
         job.adjustedLanding = false;
      }

      if (view != null && view.loaded(job.requestedTarget)) {
         boolean targetLavaSafe = view.lavaSafe(job.requestedTarget);
         if (!targetLavaSafe && System.currentTimeMillis() < job.nextDestinationSearchAt) {
            return null;
         } else {
            Vec3 clear = resolveLoadedDestination(view, job.requestedTarget, job.lavaDestinationSearchRadius);
            if (clear != null) {
               job.resolvedTarget = clear;
               job.adjustedLanding = clear.distanceTo(job.requestedTarget) > 0.5;
               return clear;
            } else if (targetLavaSafe) {
               job.resolvedTarget = job.requestedTarget;
               return job.resolvedTarget;
            } else {
               job.lavaDestinationSearchRadius = Math.min(32, job.lavaDestinationSearchRadius + 8);
               job.nextDestinationSearchAt = System.currentTimeMillis() + 1000L;
               return null;
            }
         }
      } else {
         return job.owner == PacketTeleportController.Owner.POV ? job.requestedTarget : job.requestedTarget;
      }
   }

   static Vec3 resolveLoadedDestination(PacketRoutePlanner.CollisionView view, Vec3 requested, double lavaSearchRadius) {
      if (view == null || requested == null || !view.loaded(requested)) {
         return null;
      } else if (!view.lavaSafe(requested)) {
         return PacketRoutePlanner.safestLavaLanding(view, requested, lavaSearchRadius);
      } else {
         Vec3 clear = PacketRoutePlanner.nearestClear(view, requested, 3.0);
         return clear == null ? requested : clear;
      }
   }

   private static boolean prepareNextFrames(PacketTeleportController.Job job, Vec3 destination, long now, int packetBudget) {
      if (job.activeWaypoint == null && !job.waypoints.isEmpty()) {
         job.activeWaypoint = job.waypoints.pollFirst();
      }

      if (job.activeWaypoint == null) {
         if (job.lastPlanTick == job.tickCounter) {
            return false;
         }

         job.lastPlanTick = job.tickCounter;
         PacketRoutePlanner.CollisionView view = collisionView(job);
         if (job.arrivalCandidateAt == 0L && groundingDue(job.spatialLegsSinceStable) && view != null && view.loaded(job.current)) {
            Vec3 localAnchor = PacketRoutePlanner.nearestGroundedAtColumn(view, job.current, 32.0);
            if (localAnchor != null && localAnchor.distanceTo(job.current) > 0.5) {
               job.activeWaypoint = localAnchor;
            } else if (localAnchor != null) {
               job.spatialLegsSinceStable = 0;
            }
         }

         PacketRoutePlanner.Route route = null;
         if (job.activeWaypoint == null && view != null) {
            Vec3 direct = roofStep(view, job, destination);
            if (direct == null) {
               direct = directFastLane(view, job.current, destination);
            }

            if (failedTarget(job, direct)) {
               direct = null;
            }

            if (direct == null) {
               direct = verticalFirstCandidate(view, job.current, destination);
               if (failedTarget(job, direct)) {
                  direct = null;
               }
            }

            if (direct == null) {
               direct = raisedArcCandidate(view, job.current, destination);
               if (failedTarget(job, direct)) {
                  direct = null;
               }
            }

            if (direct == null) {
               direct = straightLegCandidate(view, job.current, destination);
               if (failedTarget(job, direct)) {
                  direct = null;
               }
            }

            if (direct == null && job.sameTargetFailures >= 2) {
               direct = hopRouteCandidate(view, job, destination);
            }

            if (direct != null) {
               job.activeWaypoint = direct;
               job.lastPlannedTarget = direct;
            } else {
               route = PacketRoutePlanner.planHybridToward(view, job.current, destination, 80 + job.rescueLevel * 24, 128, 3);
            }
         }

         if (job.activeWaypoint == null) {
            if (acceptsPlannedRoute(route, job.current, destination) && routeLavaSafe(view, job.current, route.waypoints())) {
               job.waypoints.addAll(route.waypoints());
               job.activeWaypoint = job.waypoints.pollFirst();
               job.lastPlannedTarget = job.activeWaypoint;
            } else {
               job.waypoints.addAll(unrestrictedFallbackWaypoints(view, job.current, destination, job.rescueLevel));
               job.activeWaypoint = job.waypoints.pollFirst();
            }
         }

         if (job.activeWaypoint == null) {
            return false;
         }
      }

      Vec3 from = job.current;
      Vec3 to = job.activeWaypoint;
      double distance = from.distanceTo(to);
      if (distance <= 0.5) {
         job.activeWaypoint = null;
         return prepareNextFrames(job, destination, now, packetBudget);
      } else if (job.lavaEmergency) {
         job.activeWaypoint = null;
         job.frames.addLast(PacketTeleportController.MoveFrame.position(from, to, protocolGroundAt(job, to)));
         return true;
      } else {
         int available = Math.max(1, packetBudget);
         double stepLimit = job.vehicle != null ? Math.min(4.0, job.effectiveStep) : job.effectiveStep;
         PacketTeleportController.FastClipPlan clip = planFastClip(distance, stepLimit, available, job.incrementalRecovery);
         Vec3 next = from.add(to.subtract(from).scale(clip.amount() / distance));
         boolean deepDescent = next.y - from.y < -3.0;
         if (deepDescent && !job.incrementalRecovery) {
            clip = planFastClip(distance, job.effectiveStep, Math.max(1, available - 2), false);
            next = from.add(to.subtract(from).scale(clip.amount() / distance));
         }

         boolean rerouting = false;
         PacketRoutePlanner.CollisionView sweepView = collisionView(job);
         Entity sweepEntity = sweepView == null ? null : sweepView.entity();
         if (sweepEntity != null && Math.hypot(next.x - from.x, next.z - from.z) > 0.5) {
            Vec3 requested = next.subtract(from);
            Vec3 allowed = PacketClipSafety.sweptCollide(sweepEntity, from, next);
            if (!allowed.equals(requested)) {
               double wanted = Math.hypot(requested.x, requested.z);
               double contact = Math.hypot(allowed.x, allowed.z);
               if (contact >= wanted - 1.0E-6) {
                  next = from.add(allowed);
               } else {
                  job.lastPlannedTarget = to;
                  job.lastFailedTarget = to;
                  job.sameTargetFailures += contact >= 0.5 ? 1 : 2;
                  job.waypoints.clear();
                  job.activeWaypoint = null;
                  if (contact < 0.5) {
                     Vec3 over = riseOverCandidate(sweepView, job, destination);
                     if (over != null) {
                        job.activeWaypoint = over;
                        job.lastPlannedTarget = over;
                     } else {
                        Vec3 pocket = escapePocketCandidate(sweepView, job, destination);
                        if (pocket != null && !failedTarget(job, pocket)) {
                           job.activeWaypoint = pocket;
                           job.lastPlannedTarget = pocket;
                        }
                     }

                     return false;
                  }

                  next = from.add(allowed);
                  rerouting = true;
               }
            }
         }

         if (!rerouting && next.distanceTo(to) <= 0.5) {
            next = to;
            job.activeWaypoint = null;
         }

         boolean fromGrounded = protocolGroundAt(job, from);
         boolean nextGrounded = protocolGroundAt(job, next);

         for (int i = 1; i < clip.packets(); i++) {
            job.frames.addLast(PacketTeleportController.MoveFrame.status(from, fromGrounded));
         }

         job.frames.addLast(PacketTeleportController.MoveFrame.position(from, next, nextGrounded));
         return true;
      }
   }

   private static boolean routeAdvances(Vec3 from, Vec3 end, Vec3 destination) {
      if (from != null && end != null && destination != null) {
         double fromHorizontal = Math.hypot(destination.x - from.x, destination.z - from.z);
         double endHorizontal = Math.hypot(destination.x - end.x, destination.z - end.z);
         return endHorizontal + 0.5 < fromHorizontal ? true : fromHorizontal <= 0.5 && Math.abs(destination.y - end.y) + 0.5 < Math.abs(destination.y - from.y);
      } else {
         return false;
      }
   }

   static boolean acceptsPlannedRoute(PacketRoutePlanner.Route route, Vec3 from, Vec3 destination) {
      return route != null
         && route.madeProgress()
         && (route.state() == PacketRoutePlanner.State.ESCAPE || routeAdvances(from, route.waypoints().getLast(), destination));
   }

   private static boolean armLavaEscape(PacketTeleportController.Job job, long now) {
      PacketRoutePlanner.CollisionView view = collisionView(job);
      if (view == null || !view.loaded(job.current)) {
         return false;
      } else if (view.lavaSafe(job.current)) {
         return true;
      } else {
         Vec3 escape = PacketRoutePlanner.safestLavaLanding(view, job.current, 32.0);
         if (escape == null) {
            return false;
         } else {
            job.frames.clear();
            job.waypoints.clear();
            job.activeWaypoint = escape;
            job.lavaEmergency = true;
            job.incrementalRecovery = false;
            job.pacer.rebase(now, 0L);
            return true;
         }
      }
   }

   private static boolean frameLavaSafe(PacketTeleportController.Job job, PacketTeleportController.MoveFrame frame) {
      PacketRoutePlanner.CollisionView view = collisionView(job);
      if (view == null || frame == null) {
         return false;
      } else {
         return frame.moves() ? packetStepsLavaSafe(view, frame.steps()) : view.loaded(job.current) && view.lavaSafe(job.current);
      }
   }

   static boolean packetStepsLavaSafe(PacketRoutePlanner.CollisionView view, List<PacketClipSafety.Step> steps) {
      if (view != null && steps != null && !steps.isEmpty()) {
         for (PacketClipSafety.Step step : steps) {
            if (step == null || step.position() == null || !view.loaded(step.position()) || !view.lavaSafe(step.position())) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean routeLavaSafe(PacketRoutePlanner.CollisionView view, Vec3 start, List<Vec3> route) {
      if (view != null && start != null && route != null && !route.isEmpty()) {
         for (Vec3 waypoint : route) {
            if (waypoint == null || !view.loaded(waypoint) || !view.lavaSafe(waypoint)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean lavaSegmentSafe(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
      if (view != null && from != null && to != null) {
         int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 0.3));

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

   private static Vec3 directFastLane(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
      if (view != null && from != null && destination != null) {
         if (from.distanceTo(destination) <= 0.5) {
            return null;
         } else if (view.loaded(destination) && view.clear(destination) && view.lavaSafe(destination) && view.traversable(destination)) {
            return clearSegment(view, from, destination) && lavaSegmentSafe(view, from, destination) ? destination : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Vec3 verticalFirstCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
      if (view != null && from != null && destination != null) {
         if (Math.abs(destination.y - from.y) < 4.0) {
            return null;
         } else {
            Vec3 vertical = new Vec3(from.x, destination.y, from.z);
            if (!cleanStraight(view, from, vertical)) {
               return null;
            } else {
               return Math.hypot(destination.x - vertical.x, destination.z - vertical.z) > 0.5 && !cleanStraight(view, vertical, destination) ? null : vertical;
            }
         }
      } else {
         return null;
      }
   }

   private static Vec3 raisedArcCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
      if (view != null && from != null && destination != null) {
         if (from.distanceTo(destination) < 6.0) {
            return null;
         } else if (view.loaded(destination) && view.clear(destination) && view.lavaSafe(destination) && view.traversable(destination)) {
            for (int lift = 1; lift <= 4; lift++) {
               Vec3 mid = from.add(destination).scale(0.5).add(0.0, lift, 0.0);
               if (view.loaded(mid)
                  && view.clear(mid)
                  && view.lavaSafe(mid)
                  && !submergedEye(view, mid)
                  && clearSegment(view, from, mid)
                  && lavaSegmentSafe(view, from, mid)
                  && clearSegment(view, mid, destination)
                  && lavaSegmentSafe(view, mid, destination)) {
                  return mid;
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Vec3 straightLegCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
      if (view != null && from != null && destination != null) {
         double dx = destination.x - from.x;
         double dz = destination.z - from.z;
         if (Math.abs(dx) <= 0.5 && Math.abs(dz) <= 0.5) {
            return null;
         } else {
            Vec3[] baseCorners = new Vec3[]{
               new Vec3(destination.x, from.y, from.z),
               new Vec3(from.x, from.y, destination.z),
               new Vec3(destination.x, destination.y, from.z),
               new Vec3(from.x, destination.y, destination.z)
            };
            Vec3 best = null;
            double bestLength = 0.0;

            for (Vec3 base : baseCorners) {
               for (int lift = 0; lift <= 4; lift++) {
                  Vec3 corner = lift == 0 ? base : base.add(0.0, lift, 0.0);
                  double length = from.distanceTo(corner);
                  if (length < 4.0) {
                     break;
                  }

                  if (cleanStraight(view, from, corner) && !submergedEye(view, corner)) {
                     if (length > bestLength) {
                        bestLength = length;
                        best = corner;
                     }
                     break;
                  }
               }
            }

            double horizontal = Math.hypot(dx, dz);
            return best != null && bestLength >= Math.max(4.0, horizontal * 0.34) ? best : null;
         }
      } else {
         return null;
      }
   }

   private static boolean cleanStraight(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
      if (view.loaded(to) && view.clear(to) && view.lavaSafe(to)) {
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

            return lavaSegmentSafe(view, from, to);
         } else {
            return clearSegment(view, from, to) && lavaSegmentSafe(view, from, to);
         }
      } else {
         return false;
      }
   }

   private static boolean submergedEye(PacketRoutePlanner.CollisionView view, Vec3 pos) {
      Entity entity = view == null ? null : view.entity();
      if (entity != null && entity.level() != null && pos != null) {
         double eyeY = pos.y + entity.getEyeHeight();
         BlockPos eyeBlock = BlockPos.containing(pos.x, eyeY, pos.z);
         FluidState fluid = entity.level().getFluidState(eyeBlock);
         return !fluid.isEmpty() && eyeY < eyeBlock.getY() + fluid.getHeight(entity.level(), eyeBlock);
      } else {
         return false;
      }
   }

   private static Vec3 escapePocketCandidate(PacketRoutePlanner.CollisionView view, PacketTeleportController.Job job, Vec3 destination) {
      if (view != null && job != null && destination != null) {
         Vec3 current = job.current;
         Vec3 best = null;
         double bestScore = Double.NEGATIVE_INFINITY;

         for (int dir = 0; dir < 8; dir++) {
            double angle = dir * (Math.PI / 4);
            double dx = -Math.sin(angle);
            double dz = Math.cos(angle);
            double reach = 0.0;
            double maxReach = 4.0 + job.rescueLevel * 4.0;

            for (int step = 1; step <= maxReach; step++) {
               Vec3 candidate = new Vec3(current.x + dx * step, current.y, current.z + dz * step);
               if (!cleanStraight(view, current, candidate)) {
                  break;
               }

               reach = step;
            }

            if (!(reach <= 0.0)) {
               Vec3 end = new Vec3(current.x + dx * reach, current.y, current.z + dz * reach);
               double regression = end.distanceTo(destination) - current.distanceTo(destination);
               double score = reach - regression * 0.25;
               if (score > bestScore) {
                  bestScore = score;
                  best = end;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static boolean failedTarget(PacketTeleportController.Job job, Vec3 candidate) {
      return candidate != null && job.lastFailedTarget != null && candidate.distanceTo(job.lastFailedTarget) < 1.5;
   }

   private static Vec3 hopRouteCandidate(PacketRoutePlanner.CollisionView view, PacketTeleportController.Job job, Vec3 destination) {
      if (view != null && job != null) {
         Vec3 flatAtFeet = new Vec3(destination.x, job.current.y, destination.z);
         return cleanStraight(view, job.current, flatAtFeet) ? null : riseOverCandidate(view, job, destination);
      } else {
         return null;
      }
   }

   private static Vec3 riseOverCandidate(PacketRoutePlanner.CollisionView view, PacketTeleportController.Job job, Vec3 destination) {
      if (view != null && job != null && destination != null) {
         Vec3 current = job.current;
         double horizontal = Math.hypot(destination.x - current.x, destination.z - current.z);
         if (horizontal <= 0.5) {
            return null;
         } else {
            double reach = Math.min(8.0, horizontal);
            double dirX = (destination.x - current.x) / horizontal;
            double dirZ = (destination.z - current.z) / horizontal;
            int maxLift = 32 + job.rescueLevel * 8;

            for (int lift = 1; lift <= maxLift; lift++) {
               Vec3 lifted = new Vec3(current.x, current.y + lift, current.z);
               if (!cleanStraight(view, current, lifted)) {
                  return null;
               }

               Vec3 ahead = new Vec3(lifted.x + dirX * reach, lifted.y, lifted.z + dirZ * reach);
               if (cleanStraight(view, lifted, ahead)) {
                  job.roofY = lifted.y;
                  return lifted;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static Vec3 roofStep(PacketRoutePlanner.CollisionView view, PacketTeleportController.Job job, Vec3 destination) {
      if (view != null && job != null && !Double.isNaN(job.roofY)) {
         Vec3 current = job.current;
         Vec3 drop = new Vec3(current.x, destination.y, current.z);
         if (cleanStraight(view, current, drop) && cleanStraight(view, drop, destination)) {
            job.roofY = Double.NaN;
            return drop;
         } else {
            double dx = destination.x - current.x;
            double dz = destination.z - current.z;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal > 0.5) {
               double step = Math.min(4.0, horizontal);
               Vec3 flat = new Vec3(current.x + dx / horizontal * step, job.roofY, current.z + dz / horizontal * step);
               if (cleanStraight(view, current, flat)) {
                  return flat;
               }
            }

            job.roofY = Double.NaN;
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean clearSegment(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
      int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 0.3));

      for (int i = 1; i <= samples; i++) {
         Vec3 sample = from.lerp(to, (double)i / samples);
         if (!view.loaded(sample) || !view.clear(sample)) {
            return false;
         }
      }

      return true;
   }

   static List<Vec3> unrestrictedFallbackWaypoints(PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 destination) {
      return unrestrictedFallbackWaypoints(view, start, destination, 0);
   }

   static List<Vec3> unrestrictedFallbackWaypoints(PacketRoutePlanner.CollisionView view, Vec3 start, Vec3 destination, int rescueLevel) {
      return PacketRoutePlanner.safeFallbackWaypoints(view, start, destination, 32 + Math.max(0, rescueLevel) * 16);
   }

   static List<Vec3> unrestrictedFallbackWaypoints(Vec3 start, Vec3 destination) {
      return List.of();
   }

   static int nextGroundingCounter(int current, boolean stable) {
      return stable ? 0 : Math.min(5, Math.max(0, current) + 1);
   }

   static boolean groundingDue(int spatialLegsSinceStable) {
      return spatialLegsSinceStable >= 4;
   }

   static PacketTeleportController.FastClipPlan planFastClip(double distance, double step, int packetBudget, boolean recovery) {
      double safeDistance = Math.max(0.0, distance);
      double safeStep = Math.max(0.0625, Math.min(10.0, step));
      int available = Math.max(1, packetBudget);
      if (recovery) {
         return new PacketTeleportController.FastClipPlan(Math.min(safeDistance, safeStep), 1, false);
      } else {
         int packets = Math.max(1, Math.min(available, (int)Math.ceil(safeDistance / safeStep)));
         return new PacketTeleportController.FastClipPlan(safeDistance, packets, true);
      }
   }

   static boolean correctionAccepted(double bestDistance, double predictedDistance, double correctedDistance, double predictionError) {
      return bestDistance - correctedDistance >= 0.25 || predictionError <= 0.05 || correctedDistance + 0.05 < predictedDistance;
   }

   private static boolean send(PacketTeleportController.Job job, PacketTeleportController.MoveFrame frame) {
      if (job.owner == PacketTeleportController.Owner.POV) {
         return !frame.moves() ? job.session.pilotTeleportStatus(frame.onGround()) : job.session.pilotTeleportSequence(frame.steps());
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null && mc.player != null) {
            boolean noFall = job.owner == PacketTeleportController.Owner.MAIN && TpClickModule.noFallActive();
            boolean previousOwnedSend = enterOwnedSendScope();

            boolean var11;
            try {
               if (job.vehicle != null) {
                  if (!frame.moves()) {
                     mc.getConnection().send(ServerboundMoveVehiclePacket.fromEntity(job.vehicle));
                  } else {
                     for (PacketClipSafety.Step step : frame.steps()) {
                        job.vehicle.setPos(step.position());
                        mc.getConnection().send(ServerboundMoveVehiclePacket.fromEntity(job.vehicle));
                     }

                     job.vehicle.positionRider(mc.player);
                  }
               } else if (frame.moves()) {
                  for (PacketClipSafety.Step step : frame.steps()) {
                     mc.getConnection().send(new Pos(antiKickPosition(job, step.position()), step.onGround(), false));
                  }
               } else {
                  mc.getConnection().send(new StatusOnly(noFall || frame.onGround(), false));
               }

               var11 = true;
            } finally {
               exitOwnedSendScope(previousOwnedSend);
            }

            return var11;
         } else {
            return false;
         }
      }
   }

   private static Vec3 antiKickPosition(PacketTeleportController.Job job, Vec3 position) {
      if (job.owner == PacketTeleportController.Owner.MAIN && TpClickModule.antiKickActive()) {
         long now = System.currentTimeMillis();
         if (now - job.lastAntiKickNudgeAt < 1000L) {
            return position;
         } else {
            double lastY = job.lastSentPosition == null ? position.y : job.lastSentPosition.y;
            if (lastY - position.y >= 0.0313) {
               return position;
            } else {
               PacketRoutePlanner.CollisionView view = collisionView(job);
               if (view != null && view.clear(position) && !view.supported(position)) {
                  job.lastAntiKickNudgeAt = now;
                  return new Vec3(position.x, position.y - 0.0313, position.z);
               } else {
                  return position;
               }
            }
         }
      } else {
         return position;
      }
   }

   private static int packetCount(PacketTeleportController.Job job, PacketTeleportController.MoveFrame frame) {
      return !frame.moves() ? 1 : Math.max(1, frame.steps().size());
   }

   private static void applyRendered(PacketTeleportController.Job job, Vec3 position, boolean onGround) {
      if (job.owner == PacketTeleportController.Owner.POV) {
         MultiPilot.applyPacedTeleportStep(job.session, position, onGround);
      } else {
         LocalPlayer player = job.player;
         if (player != null) {
            if (job.vehicle != null) {
               job.vehicle.setPos(position);
            }

            if (job.vehicle != null) {
               job.vehicle.positionRider(player);
            } else {
               player.setPos(position.x, position.y, position.z);
            }

            player.setOnGround(onGround);
            player.setDeltaMovement(Vec3.ZERO);
            player.xxa = 0.0F;
            player.zza = 0.0F;
            player.setJumping(false);
            player.resetFallDistance();
         }
      }
   }

   private static PacketRoutePlanner.CollisionView collisionView(PacketTeleportController.Job job) {
      Entity entity = (Entity)(job.owner == PacketTeleportController.Owner.POV ? job.bot : (job.vehicle != null ? job.vehicle : job.player));
      return PacketRoutePlanner.forEntity(entity);
   }

   private static boolean protocolGroundAt(PacketTeleportController.Job job, Vec3 position) {
      PacketRoutePlanner.CollisionView view = collisionView(job);
      return protocolGround(view, position);
   }

   private static boolean stableAnchorAt(PacketTeleportController.Job job, Vec3 position) {
      PacketRoutePlanner.CollisionView view = collisionView(job);
      return view != null && position != null && view.loaded(position) && view.clear(position) && view.lavaSafe(position) && view.traversable(position);
   }

   static boolean protocolGround(PacketRoutePlanner.CollisionView view, Vec3 position) {
      return view != null && position != null && view.loaded(position) && view.clear(position) && view.lavaSafe(position) && view.supported(position);
   }

   public static void onMainCorrection(Vec3 corrected) {
      synchronized (LOCK) {
         if (active != null && active.owner == PacketTeleportController.Owner.MAIN && active.vehicle == null && corrected != null) {
            active.pendingCorrection = new PacketTeleportController.Correction(corrected);
         }
      }
   }

   public static void onMainVehicleCorrection(Vec3 corrected) {
      synchronized (LOCK) {
         if (active != null && active.owner == PacketTeleportController.Owner.MAIN && active.vehicle != null && corrected != null) {
            active.pendingCorrection = new PacketTeleportController.Correction(corrected);
         }
      }
   }

   static void onPovCorrection(MultiSession session, Vec3 corrected) {
      synchronized (LOCK) {
         if (active != null && active.owner == PacketTeleportController.Owner.POV && active.session == session && corrected != null) {
            active.pendingCorrection = new PacketTeleportController.Correction(corrected);
         }
      }
   }

   public static boolean ownsMainMovement() {
      PacketTeleportController.Job job = active;
      return job != null && job.owner == PacketTeleportController.Owner.MAIN;
   }

   static boolean ownsPov(MultiSession session) {
      PacketTeleportController.Job job = active;
      return job != null && job.owner == PacketTeleportController.Owner.POV && job.session == session;
   }

   public static boolean isControllerOwnedSend() {
      return OWNED_SEND_SCOPES.get() == 0 ? false : Boolean.TRUE.equals(OWNED_SEND.get());
   }

   public static void runAtomicClipSend(Runnable action) {
      if (action != null) {
         boolean previous = enterOwnedSendScope();

         try {
            action.run();
         } finally {
            exitOwnedSendScope(previous);
         }
      }
   }

   private static boolean enterOwnedSendScope() {
      boolean previous = Boolean.TRUE.equals(OWNED_SEND.get());
      if (!previous) {
         OWNED_SEND.set(true);
         OWNED_SEND_SCOPES.incrementAndGet();
      }

      return previous;
   }

   private static void exitOwnedSendScope(boolean previous) {
      if (!previous) {
         OWNED_SEND.set(false);
         OWNED_SEND_SCOPES.decrementAndGet();
      }
   }

   public static boolean shouldSuppressMainMovement(Packet<?> packet) {
      if (!(packet instanceof ServerboundMovePlayerPacket)
         && !(packet instanceof ServerboundMoveVehiclePacket)
         && !(packet instanceof ServerboundPlayerInputPacket)) {
         return false;
      } else {
         PacketTeleportController.Job job = active;
         return job != null && job.owner == PacketTeleportController.Owner.MAIN && !isControllerOwnedSend() ? job.arrivalCandidateAt == 0L : false;
      }
   }

   public static void cancelAll(String reason) {
      PacketTeleportController.Job job;
      synchronized (LOCK) {
         job = active;
      }

      if (job != null) {
         finish(job, false, reason == null ? "cancelled" : reason);
      }
   }

   static void cancelPov(MultiSession session, String reason) {
      PacketTeleportController.Job job;
      synchronized (LOCK) {
         job = active != null && active.owner == PacketTeleportController.Owner.POV && active.session == session ? active : null;
      }

      if (job != null) {
         finish(job, false, reason == null ? "POV ownership changed" : reason);
      }
   }

   private static String validateOwner(PacketTeleportController.Job job, Minecraft mc) {
      if (job.owner == PacketTeleportController.Owner.POV) {
         if (MultiPilot.activeCommandSession() != job.session || !job.session.isPiloted()) {
            return "POV ownership changed";
         } else if (!job.session.pilotPacketsReady()) {
            return "POV connection changed";
         } else if (job.session.macroOwnsPilot()) {
            return "macro took POV ownership";
         } else if (!job.dimension.equals(job.session.takeoverDimension())) {
            return "POV dimension changed";
         } else {
            return job.bot != null && !job.bot.isDeadOrDying() ? null : "POV bot died";
         }
      } else if (MultiPilot.isActive()) {
         return "POV ownership changed";
      } else if (job.macroOwned ? MacroExecutor.isRunning() : !MacroExecutor.isRunning()) {
         if (mc.player != job.player || mc.getConnection() != job.mainConnection || mc.level != job.level) {
            return "player connection changed";
         } else if (job.player.isDeadOrDying()) {
            return "player died";
         } else {
            return job.vehicle != null && job.player.getVehicle() != job.vehicle ? "vehicle ownership changed" : null;
         }
      } else {
         return job.macroOwned ? "owning macro stopped" : "macro took movement ownership";
      }
   }

   private static boolean physicalMovementDown(Minecraft mc) {
      return mc.options.keyUp.isDown()
         || mc.options.keyDown.isDown()
         || mc.options.keyLeft.isDown()
         || mc.options.keyRight.isDown()
         || mc.options.keyJump.isDown()
         || mc.options.keyShift.isDown();
   }

   private static void releaseMovementInput(Minecraft mc) {
      if (mc.options != null) {
         mc.options.keyUp.setDown(false);
         mc.options.keyDown.setDown(false);
         mc.options.keyLeft.setDown(false);
         mc.options.keyRight.setDown(false);
         mc.options.keyJump.setDown(false);
         mc.options.keyShift.setDown(false);
         RiptidePathWalker.onExternalKeyRelease();
      }
   }

   private static void restoreMovementInput(Minecraft mc) {
      if (mc != null && mc.options != null) {
         RiptideKeyMappingBridge.of(mc.options.keyUp).riptide$resetPressedState();
         RiptideKeyMappingBridge.of(mc.options.keyDown).riptide$resetPressedState();
         RiptideKeyMappingBridge.of(mc.options.keyLeft).riptide$resetPressedState();
         RiptideKeyMappingBridge.of(mc.options.keyRight).riptide$resetPressedState();
         RiptideKeyMappingBridge.of(mc.options.keyJump).riptide$resetPressedState();
         RiptideKeyMappingBridge.of(mc.options.keyShift).riptide$resetPressedState();
      }
   }

   private static String status(MultiSession requestedOwner) {
      synchronized (LOCK) {
         if (active == null) {
            return "No active TP";
         } else {
            return requestedOwner == null || active.owner == PacketTeleportController.Owner.POV && active.session == requestedOwner
               ? String.format(
                  Locale.ROOT,
                  "TP %s%s: %.2f %.2f %.2f -> %.2f %.2f %.2f, %d packets, step %.3f",
                  active.owner == PacketTeleportController.Owner.POV ? active.session.accountId() : "main",
                  active.incrementalRecovery ? " recovery" : "",
                  active.current.x,
                  active.current.y,
                  active.current.z,
                  active.requestedTarget.x,
                  active.requestedTarget.y,
                  active.requestedTarget.z,
                  active.totalPackets,
                  active.effectiveStep
               )
               : "No active TP for this POV";
         }
      }
   }

   private static String stop(MultiSession requestedOwner, String reason) {
      PacketTeleportController.Job job;
      synchronized (LOCK) {
         job = active;
         if (job == null) {
            return "No active TP";
         }

         if (requestedOwner != null && (job.owner != PacketTeleportController.Owner.POV || job.session != requestedOwner)) {
            return "No active TP for this POV";
         }
      }

      synchronized (LOCK) {
         if (active == job) {
            active = null;
         }
      }

      if (job.owner == PacketTeleportController.Owner.MAIN && job.player != null) {
         job.player.setDeltaMovement(Vec3.ZERO);
      }

      if (job.completion != null) {
         job.completion.complete(new PacketTeleportController.MacroResult(false, reason));
      }

      return "TP stopped";
   }

   private static void finish(PacketTeleportController.Job job, boolean success, String reason) {
      synchronized (LOCK) {
         if (active != job) {
            return;
         }

         active = null;
      }

      if (job.owner == PacketTeleportController.Owner.MAIN && inputWasBlocked) {
         inputWasBlocked = false;
         restoreMovementInput(Minecraft.getInstance());
      }

      if (job.owner == PacketTeleportController.Owner.MAIN && job.player != null) {
         job.player.setDeltaMovement(Vec3.ZERO);
      }

      if (job.completion != null) {
         job.completion.complete(new PacketTeleportController.MacroResult(success, reason));
      }

      String color = success ? "§a" : "§e";
      RiptideClientMessaging.sendPrefixed(color + "TP " + (success ? "complete: " : "stopped: ") + "§f" + reason);
   }

   private static Vec3 controlledMainPosition(LocalPlayer player) {
      Entity vehicle = player.getVehicle();
      return vehicle == null ? player.position() : vehicle.position();
   }

   private static void neutralizeMain(LocalPlayer player) {
      if (player != null && Minecraft.getInstance().getConnection() != null) {
         boolean sprinting = player.isSprinting();
         player.xxa = 0.0F;
         player.zza = 0.0F;
         player.setJumping(false);
         player.setShiftKeyDown(false);
         player.setSprinting(false);
         player.stopFallFlying();
         player.getAbilities().flying = false;
         PacketRoutePlanner.CollisionView view = PacketRoutePlanner.forEntity(player);
         player.setOnGround(view != null && view.loaded(player.position()) && view.lavaSafe(player.position()) && view.supported(player.position()));
         player.setDeltaMovement(Vec3.ZERO);
         boolean previousOwnedSend = enterOwnedSendScope();

         try {
            Minecraft.getInstance().getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
            Minecraft.getInstance().getConnection().send(new ServerboundPlayerAbilitiesPacket(player.getAbilities()));
            if (sprinting) {
               Minecraft.getInstance().getConnection().send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
            }
         } finally {
            exitOwnedSendScope(previousOwnedSend);
         }
      }
   }

   private static String landingMessage(PacketTeleportController.Job job) {
      return !job.adjustedLanding
         ? String.format(Locale.ROOT, "arrived at %.2f %.2f %.2f", job.current.x, job.current.y, job.current.z)
         : String.format(Locale.ROOT, "adjusted landing %.2f %.2f %.2f", job.current.x, job.current.y, job.current.z);
   }

   private static String startedMessage(Vec3 destination, int packets, int pause, boolean vehicle) {
      return String.format(
         Locale.ROOT,
         "TP started%s: %.2f %.2f %.2f · %d packets / %d ms",
         vehicle ? " for vehicle" : "",
         destination.x,
         destination.y,
         destination.z,
         packets,
         pause
      );
   }

   private static Double coordinate(String token, double origin) {
      if (token != null && !token.isBlank() && token.charAt(0) != '^') {
         try {
            double value;
            if (token.charAt(0) == '~') {
               value = token.length() == 1 ? origin : origin + Double.parseDouble(token.substring(1));
            } else {
               value = Double.parseDouble(token);
            }

            return Double.isFinite(value) ? value : null;
         } catch (NumberFormatException var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Integer boundedInteger(String value, int min, int max) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed >= min && parsed <= max ? parsed : null;
      } catch (NumberFormatException var4) {
         return null;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   public static enum CommandKind {
      START,
      STOP,
      STATUS,
      CONFIG,
      RESET,
      HELP,
      ERROR;
   }

   public record CommandRequest(PacketTeleportController.CommandKind kind, Vec3 destination, int maxPackets, int pauseMs, String error) {
      static PacketTeleportController.CommandRequest error(String message) {
         return new PacketTeleportController.CommandRequest(PacketTeleportController.CommandKind.ERROR, null, 0, 0, message);
      }
   }

   private record Correction(Vec3 position) {
   }

   record FastClipPlan(double amount, int packets, boolean banked) {
   }

   private static final class Job {
      final PacketTeleportController.Owner owner;
      final LocalPlayer player;
      final Object mainConnection;
      final Object level;
      final Entity vehicle;
      final MultiSession session;
      final RemotePlayer bot;
      final String dimension;
      final Vec3 requestedTarget;
      final PacketTeleportController.Pacer pacer;
      final boolean macroOwned;
      final long macroId;
      final CompletableFuture<PacketTeleportController.MacroResult> completion;
      final Deque<Vec3> waypoints = new ArrayDeque<>();
      final Deque<PacketTeleportController.MoveFrame> frames = new ArrayDeque<>();
      Vec3 current;
      Vec3 lastSentPosition;
      Vec3 resolvedTarget;
      Vec3 activeWaypoint;
      double effectiveStep;
      double bestDistance;
      int totalPackets;
      long tickCounter;
      long lastPlanTick = Long.MIN_VALUE;
      final long startedAt = System.currentTimeMillis();
      long lastProgressAt = this.startedAt;
      long lastActivityAt = this.startedAt;
      int rescueLevel;
      long arrivalCandidateAt;
      long recoveryNextSendAt;
      long recoveryStableAt;
      long nextDestinationSearchAt;
      double recoveryAnchorDistance;
      int spatialLegsSinceStable;
      int rejectionCount;
      Vec3 lastPlannedTarget;
      Vec3 lastFailedTarget;
      int sameTargetFailures;
      double roofY = Double.NaN;
      long lastAntiKickNudgeAt;
      int lavaDestinationSearchRadius = 12;
      boolean adjustedLanding;
      boolean incrementalRecovery;
      boolean lavaEmergency;
      PacketTeleportController.Correction pendingCorrection;

      private Job(
         PacketTeleportController.Owner owner,
         LocalPlayer player,
         Object mainConnection,
         Object level,
         Entity vehicle,
         MultiSession session,
         RemotePlayer bot,
         String dimension,
         Vec3 start,
         PacketTeleportController.CommandRequest request,
         boolean macroOwned,
         long macroId,
         CompletableFuture<PacketTeleportController.MacroResult> completion
      ) {
         this.owner = owner;
         this.player = player;
         this.mainConnection = mainConnection;
         this.level = level;
         this.vehicle = vehicle;
         this.session = session;
         this.bot = bot;
         this.dimension = dimension == null ? "" : dimension;
         this.current = start;
         this.lastSentPosition = start;
         this.requestedTarget = request.destination();
         this.effectiveStep = 10.0;
         this.bestDistance = start.distanceTo(request.destination());
         this.pacer = new PacketTeleportController.Pacer(request.maxPackets(), request.pauseMs());
         this.macroOwned = macroOwned;
         this.macroId = macroId;
         this.completion = completion;
      }

      static PacketTeleportController.Job main(
         LocalPlayer player, Object connection, Object level, Entity vehicle, PacketTeleportController.CommandRequest request
      ) {
         return new PacketTeleportController.Job(
            PacketTeleportController.Owner.MAIN,
            player,
            connection,
            level,
            vehicle,
            null,
            null,
            "",
            vehicle == null ? player.position() : vehicle.position(),
            request,
            false,
            0L,
            null
         );
      }

      static PacketTeleportController.Job mainMacro(
         LocalPlayer player,
         Object connection,
         Object level,
         Entity vehicle,
         PacketTeleportController.CommandRequest request,
         long macroId,
         CompletableFuture<PacketTeleportController.MacroResult> completion
      ) {
         return new PacketTeleportController.Job(
            PacketTeleportController.Owner.MAIN,
            player,
            connection,
            level,
            vehicle,
            null,
            null,
            "",
            vehicle == null ? player.position() : vehicle.position(),
            request,
            true,
            macroId,
            completion
         );
      }

      static PacketTeleportController.Job pov(MultiSession session, RemotePlayer bot, PacketTeleportController.CommandRequest request) {
         Vec3 start = session.takeoverPosition().position();
         return new PacketTeleportController.Job(
            PacketTeleportController.Owner.POV, null, null, null, null, session, bot, session.takeoverDimension(), start, request, false, 0L, null
         );
      }
   }

   public record MacroHandle(long id, CompletableFuture<PacketTeleportController.MacroResult> completion) {
   }

   public record MacroResult(boolean success, String detail) {
   }

   private record MoveFrame(Vec3 position, boolean moves, boolean onGround, List<PacketClipSafety.Step> steps) {
      MoveFrame(Vec3 position, boolean moves, boolean onGround, List<PacketClipSafety.Step> steps) {
         steps = steps == null ? List.of() : List.copyOf(steps);
         this.position = position;
         this.moves = moves;
         this.onGround = onGround;
         this.steps = steps;
      }

      static PacketTeleportController.MoveFrame status(Vec3 position, boolean onGround) {
         return new PacketTeleportController.MoveFrame(position, false, onGround, List.of());
      }

      static PacketTeleportController.MoveFrame position(Vec3 from, Vec3 to, boolean onGround) {
         return new PacketTeleportController.MoveFrame(to, true, onGround, PacketClipSafety.positionSteps(from, to, onGround));
      }
   }

   private static enum Owner {
      MAIN,
      POV;
   }

   public static final class Pacer {
      private final int maxPackets;
      private final int pauseMs;
      private int packetsInWindow;
      private long resumeAt;
      private long lastSendTick = Long.MIN_VALUE;

      public Pacer(int maxPackets, int pauseMs) {
         this.maxPackets = PacketTeleportController.clamp(maxPackets, 1, 100);
         this.pauseMs = PacketTeleportController.clamp(pauseMs, 50, 10000);
      }

      public boolean canSend(long tick, long now) {
         if (tick != this.lastSendTick && now >= this.resumeAt) {
            this.lastSendTick = tick;
            return true;
         } else {
            return false;
         }
      }

      public void markSent(long now) {
         this.packetsInWindow++;
         if (this.packetsInWindow >= this.maxPackets) {
            this.packetsInWindow = 0;
            this.resumeAt = now + this.pauseMs;
         }
      }

      public boolean waiting(long now) {
         return now < this.resumeAt;
      }

      public int remainingInWindow() {
         return Math.max(1, this.maxPackets - this.packetsInWindow);
      }

      public int packetsInWindow() {
         return this.packetsInWindow;
      }

      public long resumeAt() {
         return this.resumeAt;
      }

      public void rebase(long now, long delayMs) {
         this.packetsInWindow = 0;
         this.resumeAt = now + Math.max(0L, delayMs);
         this.lastSendTick = Long.MIN_VALUE;
      }
   }
}
