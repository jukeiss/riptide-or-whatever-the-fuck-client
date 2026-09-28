package riptide.modules;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.api.module.StringSetting;
import riptide.util.AntiVanishHeuristics;
import riptide.util.AntiVanishText;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePlayerScanner;

public final class AntiVanishModule extends Module {
   private static final long SIGNAL_WINDOW_MS = 15000L;
   private static final long DETECTION_TTL_MS = 20000L;
   private static final long SELF_BREAK_TTL_MS = 6000L;
   private static final long CRITICAL_COOLDOWN_MS = 12000L;
   private static final long ANNOUNCE_COOLDOWN_MS = 1500L;
   private static final long ANNOUNCE_WINDOW_MS = 4000L;
   private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
   private static final int MAX_OBSERVATIONS_PER_TICK = 512;
   private static final int CRITICAL_SCORE = 35;
   private static final long PLACE_SOUND_MATCH_MS = 700L;
   private static final long BREAK_MATCH_MS = 900L;
   private static final long SELF_PLACE_TTL_MS = 6000L;
   private static final long AMBIGUOUS_DEPARTURE_TTL_MS = 30000L;
   private static final long REMOTE_DIG_TTL_MS = 3000L;
   private static final long CONTAINER_SELF_GRACE_MS = 2500L;
   private static final int[][] BLOCK_AXES = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
   private final ConcurrentLinkedQueue<AntiVanishModule.Observation> observations = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<AntiVanishModule.BlockTransition> blockTransitions = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<AntiVanishModule.RawPlaceSound> rawPlaceSounds = new ConcurrentLinkedQueue<>();
   private final AtomicInteger blockTransitionCount = new AtomicInteger();
   private final AtomicInteger rawPlaceSoundCount = new AtomicInteger();
   private final Map<UUID, AntiVanishModule.KnownPlayer> knownPlayers = new HashMap<>();
   private final Map<String, AntiVanishModule.Detection> detections = new LinkedHashMap<>();
   private final Map<String, Long> signalCooldowns = new HashMap<>();
   private final Map<String, Long> announceCooldowns = new HashMap<>();
   private final Deque<Long> announceTimes = new ArrayDeque<>();
   private final Map<Long, Long> selfBrokenBlocks = new HashMap<>();
   private final Map<Long, Long> selfPlacedBlocks = new HashMap<>();
   private final Map<UUID, Long> confirmedDepartures = new HashMap<>();
   private final Map<UUID, AntiVanishModule.AmbiguousDeparture> ambiguousDepartures = new HashMap<>();
   private final Map<Long, Long> automatedMechanisms = new HashMap<>();
   private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<>();
   private final Map<Long, Deque<Long>> chunkResends = new HashMap<>();
   private final Map<Long, Long> blockChunkQuietUntil = new HashMap<>();
   private final Deque<AntiVanishModule.Signal> signals = new ArrayDeque<>();
   private final Deque<Long> cameraCorrections = new ArrayDeque<>();
   private final Deque<AntiVanishModule.ExplosionEvent> recentExplosions = new ArrayDeque<>();
   private final Deque<AntiVanishModule.PendingPlace> pendingPlaces = new ArrayDeque<>();
   private final Deque<AntiVanishModule.PlaceSound> recentPlaceSounds = new ArrayDeque<>();
   private final Map<Long, AntiVanishModule.RemoteDig> recentRemoteDigs = new HashMap<>();
   private final Map<Long, AntiVanishModule.Removal> recentAirUpdates = new HashMap<>();
   private final Map<Long, AntiVanishModule.BreakEffect> recentBreakEffects = new HashMap<>();
   private final Deque<AntiVanishModule.PendingBreak> pendingBreaks = new ArrayDeque<>();
   private final Deque<AntiVanishModule.PendingAnonymousBreak> pendingAnonymousBreaks = new ArrayDeque<>();
   private final Map<Integer, AntiVanishModule.HiddenSwing> recentHiddenSwings = new HashMap<>();
   private final Deque<AntiVanishModule.PendingVanish> pendingVanishes = new ArrayDeque<>();
   private final Set<Long> seenChunks = new HashSet<>();
   private final Deque<AntiVanishModule.RecentMessage> recentMessages = new ArrayDeque<>();
   private boolean serverSendsLeaveMessages;
   private final Set<Integer> completionRequestIds = new HashSet<>();
   private volatile List<String> pendingCompletionNames;
   private int nextCompletionId = 30000;
   private static final long RECENT_MESSAGE_TTL_MS = 8000L;
   private static final long TRUSTED_LISTED_MS = 1500L;
   private final Map<UUID, Long> listedSinceMs = new HashMap<>();
   private Object lastLevel;
   private volatile Vec3 lastPosition;
   private volatile float lastYaw;
   private volatile float lastPitch;
   private int stationaryTicks;
   private int tickCounter;
   private volatile int localPlayerId = Integer.MIN_VALUE;
   private volatile long lastLocalActionMs;
   private long lastContainerActivityMs;
   private long lastServerCorrectionMs;
   private long lastCriticalMs;
   private long criticalUntilMs;
   private int currentScore;
   private String criticalSummary = "";
   private String lastTrigger = "";
   private final Map<String, String> tagByName = new HashMap<>();
   private long lastTagScanMs = 0L;

   public AntiVanishModule() {
      super("anti-vanish", "AntiVanish", ModuleCategory.MISC, "Detects vanished players.");
      this.add(new BoolSetting("vanish-tracker", "Vanish Tracker", true).group("Detection").description("Detect TAB disappearances.").build());
      this.add(new BoolSetting("completion-probe", "Tab Probe", true).group("Detection").description("Active tab probe.").build());
      this.add(
         new StringSetting("probe-command", "Probe Command", "minecraft:msg")
            .group("Detection")
            .description("Command for probe.")
            .visibleWhen(() -> false)
            .build()
      );
      this.add(new BoolSetting("gamemode-alerts", "Gamemode Alerts", false).group("Detection").description("Notify gamemode switches.").build());
      this.add(new BoolSetting("player-filter", "Player Filter", false).group("Detection").description("Scan listed names only.").build());
      this.add(new StringListSetting("players", "Players", "").group("Detection").playerNameList().visibleWhen(() -> this.bool("player-filter")).build());
      this.add(new BoolSetting("environmental", "Environmental", true).group("Sensors").description("Sounds, blocks, particles.").build());
      this.add(
         new BoolSetting("sound-sensor", "Suspicious Sounds", true).group("Sensors").description("Detect unexplained sounds.").visibleWhen(() -> false).build()
      );
      this.add(
         new BoolSetting("particle-sensor", "Ghost Particles", true).group("Sensors").description("Detect ghost particles.").visibleWhen(() -> false).build()
      );
      this.add(
         new BoolSetting("block-sensor", "Block Updates", true).group("Sensors").description("Detect unseen interactions.").visibleWhen(() -> false).build()
      );
      this.add(
         new BoolSetting("invisible-sensor", "Invisible Entities", true)
            .group("Sensors")
            .description("Detect invisible players.")
            .visibleWhen(() -> false)
            .build()
      );
      this.add(
         new BoolSetting("camera-sensor", "Camera Aberrations", true)
            .group("Sensors")
            .description("Detect forced camera resets.")
            .visibleWhen(() -> false)
            .build()
      );
      this.add(
         new BoolSetting("chunk-sensor", "Chunk Re-sends", true).group("Sensors").description("Detect nearby chunk resends.").visibleWhen(() -> false).build()
      );
      this.add(
         new IntSetting("range", "Detection Range", 64, 8, 160, 8).group("Sensors").description("Sensor watch distance.").visibleWhen(() -> false).build()
      );
      this.add(
         new BoolSetting("critical-alert", "Critical Alert", true).group("Alerts").description("Combine recent signals.").visibleWhen(() -> false).build()
      );
      this.add(new BoolSetting("alert-sound", "Warning Sound", true).group("Alerts").description("Play critical warning.").visibleWhen(() -> false).build());
      this.add(new BoolSetting("hud-list", "Vanish HUD", true).group("Alerts").description("Show detections.").build());
      this.add(new BoolSetting("chat-alerts", "Chat Alerts", true).group("Alerts").description("Log detections to chat.").build());
   }

   @Override
   public String info() {
      return this.currentScore > 0 ? Integer.toString(this.currentScore) : "";
   }

   @Override
   public void onEnable() {
      this.resetRuntime();
      if (MC.player != null && MC.level != null) {
         this.lastLevel = MC.level;
         this.lastPosition = MC.player.position();
         this.lastYaw = MC.player.getYRot();
         this.lastPitch = MC.player.getXRot();
         this.localPlayerId = MC.player.getId();
         this.trackListedPlayers();
      }
   }

   @Override
   public void onDisable() {
      this.resetRuntime();
   }

   @Override
   public void onGameJoin() {
      this.resetRuntime();
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.level != null && MC.getConnection() != null) {
         if (this.lastLevel != MC.level) {
            this.resetRuntime();
            this.lastLevel = MC.level;
         }

         this.tickCounter++;
         this.localPlayerId = MC.player.getId();
         if (MC.player.containerMenu != MC.player.inventoryMenu) {
            this.lastContainerActivityMs = System.currentTimeMillis();
         }

         this.updateStationaryState();
         this.drainObservations();
         this.drainBlockEvidence();
         this.processPendingPlaces();
         this.processPendingBreaks();
         this.processPendingAnonymousBreaks();
         this.processPendingVanishes();
         this.processCompletionProbe();
         if (this.bool("completion-probe") && this.tickCounter % 100 == 0) {
            this.sendCompletionProbe();
         }

         if (this.tickCounter % 10 == 0) {
            this.trackListedPlayers();
         }

         if (this.tickCounter % 5 == 0 && this.sensorOn("invisible-sensor")) {
            this.scanInvisiblePlayers();
         }

         this.pruneState();
      } else {
         this.resetRuntime();
      }
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      if (packet == null) {
         return false;
      } else if (packet instanceof ServerboundPlayerActionPacket action) {
         this.lastLocalActionMs = System.currentTimeMillis();
         Action a = action.getAction();
         if (action.getPos() != null) {
            if (a == Action.START_DESTROY_BLOCK) {
               this.markSelfBreakFootprint(action.getPos(), System.currentTimeMillis() + 6000L, false);
            } else if (a == Action.STOP_DESTROY_BLOCK) {
               this.markSelfBreakFootprint(action.getPos(), System.currentTimeMillis() + 2000L, false);
            } else if (a == Action.ABORT_DESTROY_BLOCK) {
               this.markSelfBreakFootprint(action.getPos(), 0L, true);
            }
         }

         return false;
      } else if (packet instanceof ServerboundUseItemOnPacket useOn) {
         this.lastLocalActionMs = System.currentTimeMillis();
         if (useOn.getHitResult() != null && useOn.getHitResult().getBlockPos() != null) {
            long until = System.currentTimeMillis() + 6000L;
            BlockPos hit = useOn.getHitResult().getBlockPos();
            BlockPos adjacent = hit.relative(useOn.getHitResult().getDirection());
            this.selfPlacedBlocks.put(hit.asLong(), until);
            this.selfPlacedBlocks.put(adjacent.asLong(), until);
            String itemPath = "";
            if (MC.player != null && useOn.getHand() != null) {
               Identifier itemId = BuiltInRegistries.ITEM.getKey(MC.player.getItemInHand(useOn.getHand()).getItem());
               itemPath = AntiVanishHeuristics.path(itemId == null ? "" : itemId.toString());
            }

            markSelfMultiBlockFootprint(hit, itemPath, this.selfPlacedBlocks, until, false);
            markSelfMultiBlockFootprint(adjacent, itemPath, this.selfPlacedBlocks, until, false);
         }

         return false;
      } else {
         String name = packet.getClass().getSimpleName();
         if (name.equals("ServerboundUseItemPacket") || name.equals("ServerboundInteractPacket") || name.equals("ServerboundSwingPacket")) {
            this.lastLocalActionMs = System.currentTimeMillis();
         }

         return false;
      }
   }

   private void markSelfBreakFootprint(BlockPos pos, long until, boolean remove) {
      if (pos != null) {
         String blockPath = "";
         if (MC.level != null) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
            blockPath = AntiVanishHeuristics.path(id == null ? "" : id.toString());
         }

         markSelfMultiBlockFootprint(pos, blockPath, this.selfBrokenBlocks, until, remove);
      }
   }

   static void markSelfMultiBlockFootprint(BlockPos pos, String path, Map<Long, Long> targets, long until, boolean remove) {
      if (pos != null && targets != null) {
         markSelfTarget(targets, pos, until, remove);
         String id = path == null ? "" : path;
         boolean vertical = id.endsWith("_door") && !id.endsWith("trapdoor")
            || id.contains("sunflower")
            || id.contains("lilac")
            || id.contains("rose_bush")
            || id.contains("peony")
            || id.contains("tall_grass")
            || id.contains("large_fern")
            || id.contains("pitcher_plant");
         if (vertical) {
            markSelfTarget(targets, pos.above(), until, remove);
            markSelfTarget(targets, pos.below(), until, remove);
         }

         if (id.endsWith("_bed")) {
            markSelfTarget(targets, pos.offset(1, 0, 0), until, remove);
            markSelfTarget(targets, pos.offset(-1, 0, 0), until, remove);
            markSelfTarget(targets, pos.offset(0, 0, 1), until, remove);
            markSelfTarget(targets, pos.offset(0, 0, -1), until, remove);
         }
      }
   }

   private static void markSelfTarget(Map<Long, Long> targets, BlockPos pos, long until, boolean remove) {
      if (remove) {
         targets.remove(pos.asLong());
      } else {
         targets.put(pos.asLong(), until);
      }
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
         if (remove.profileIds().size() < 4) {
            for (UUID id : remove.profileIds()) {
               this.observations.offer(AntiVanishModule.Observation.tabRemove(id));
            }
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info
         && info.actions().contains(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)) {
         int unlisted = 0;

         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
            if (entry != null && !entry.listed()) {
               unlisted++;
            }
         }

         if (unlisted > 0 && unlisted < 4) {
            for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entryx : info.entries()) {
               if (entryx != null && !entryx.listed()) {
                  this.observations.offer(AntiVanishModule.Observation.tabHide(entryx.profileId()));
               }
            }
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info
         && info.actions().contains(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
         this.handleGamemodeUpdates(info);
      } else if (packet instanceof ClientboundSystemChatPacket chat) {
         String text = chat.content() == null ? "" : chat.content().getString();
         if (!text.isBlank()) {
            this.observations.offer(AntiVanishModule.Observation.systemChat(text));
         }

         String departedName = departedPlayerName(chat.content());
         if (!departedName.isBlank()) {
            this.observations.offer(AntiVanishModule.Observation.playerLeft(departedName));
         }
      } else {
         if (packet instanceof ClientboundCommandSuggestionsPacket suggestions && this.completionRequestIds.remove(suggestions.id())) {
            List<String> names = new ArrayList<>();

            for (Suggestion suggestion : suggestions.toSuggestions().getList()) {
               String textx = suggestion.getText();
               if (textx != null && !textx.isBlank()) {
                  names.add(textx.trim());
               }
            }

            this.pendingCompletionNames = names;
            return true;
         }

         if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            this.observations.offer(AntiVanishModule.Observation.entityMetadata(metadata.id()));
         } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            this.observations.offer(this.cameraObservation(position));
         } else if (packet instanceof ClientboundExplodePacket explosion) {
            this.observations.offer(AntiVanishModule.Observation.explosion(explosion.center(), explosion.radius()));
         } else if (packet instanceof ClientboundLevelEventPacket levelEvent && levelEvent.getType() == 2001) {
            BlockState broken = Block.stateById(levelEvent.getData());
            Identifier id = broken == null ? null : BuiltInRegistries.BLOCK.getKey(broken.getBlock());
            this.observations
               .offer(AntiVanishModule.Observation.block(AntiVanishModule.ObservationType.BLOCK_BREAK, levelEvent.getPos(), id == null ? "" : id.toString()));
         } else if (packet instanceof ClientboundBlockDestructionPacket destruction) {
            this.observations
               .offer(
                  AntiVanishModule.Observation.blockActor(
                     AntiVanishModule.ObservationType.BLOCK_DIG, destruction.getPos(), destruction.getId(), destruction.getProgress()
                  )
               );
         } else if (packet instanceof ClientboundAnimatePacket animation && (animation.getAction() == 0 || animation.getAction() == 3)) {
            this.observations
               .offer(AntiVanishModule.Observation.entityAction(AntiVanishModule.ObservationType.ENTITY_SWING, animation.getId(), animation.getAction()));
         } else if (packet instanceof ClientboundSoundEntityPacket sound) {
            this.observations.offer(AntiVanishModule.Observation.entitySound(sound.getId(), soundId(((SoundEvent)sound.getSound().value()).location())));
         } else if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particles.getParticle().getType());
            this.observations
               .offer(
                  AntiVanishModule.Observation.position(
                     AntiVanishModule.ObservationType.PARTICLE, particles.getX(), particles.getY(), particles.getZ(), id == null ? "" : id.toString()
                  )
               );
         } else if (packet instanceof ClientboundBlockEventPacket blockEvent) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(blockEvent.getBlock());
            this.observations
               .offer(AntiVanishModule.Observation.block(AntiVanishModule.ObservationType.BLOCK_EVENT, blockEvent.getPos(), id == null ? "" : id.toString()));
         } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            this.observations.offer(AntiVanishModule.Observation.chunk(chunk.getX(), chunk.getZ()));
         }
      }

      return false;
   }

   @Override
   public void onSoundPacket(ClientboundSoundPacket packet) {
      if (packet != null && packet.getSound() != null && packet.getSound().value() != null) {
         String id = soundId(((SoundEvent)packet.getSound().value()).location());
         this.observations
            .offer(AntiVanishModule.Observation.position(AntiVanishModule.ObservationType.POSITIONAL_SOUND, packet.getX(), packet.getY(), packet.getZ(), id));
         if (this.sensorOn("block-sensor") && packet.getSource() == SoundSource.BLOCKS && id.endsWith(".place")) {
            this.offerRawPlaceSound(
               new AntiVanishModule.RawPlaceSound(
                  new Vec3(packet.getX(), packet.getY(), packet.getZ()), id, packet.getVolume(), packet.getPitch(), System.currentTimeMillis()
               )
            );
         }
      }
   }

   public static void observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet) {
      AntiVanishModule module = instance();
      if (module != null && module.isEnabled() && module.sensorOn("block-sensor") && packet != null && MC.level != null) {
         module.observeBlockTransition(packet.getPos(), packet.getBlockState());
      }
   }

   private void observeBlockTransition(BlockPos pos, BlockState after) {
      if (pos != null && after != null && MC.level != null) {
         BlockState before = MC.level.getBlockState(pos);
         if (before != null && after != null && !before.equals(after)) {
            Identifier beforeKey = BuiltInRegistries.BLOCK.getKey(before.getBlock());
            Identifier afterKey = BuiltInRegistries.BLOCK.getKey(after.getBlock());
            String beforeId = beforeKey == null ? "" : beforeKey.toString();
            String afterId = afterKey == null ? "" : afterKey.toString();
            if (after.isAir()) {
               if (!before.isAir()) {
                  this.offerBlockTransition(
                     new AntiVanishModule.BlockTransition(pos.immutable(), afterId, beforeId, "", 0.0F, 0.0F, true, System.currentTimeMillis())
                  );
               }
            } else if (before.getBlock() != after.getBlock()) {
               boolean soundCandidate = AntiVanishHeuristics.crediblePlacementTransition(beforeId, afterId, before.canBeReplaced());
               boolean anonymousCandidate = AntiVanishHeuristics.credibleAnonymousPlacementTransition(beforeId, afterId);
               if (soundCandidate || anonymousCandidate) {
                  String expectedSound = after.getSoundType().getPlaceSound().location().toString();
                  float expectedVolume = (after.getSoundType().getVolume() + 1.0F) / 2.0F;
                  float expectedPitch = after.getSoundType().getPitch() * 0.8F;
                  this.offerBlockTransition(
                     new AntiVanishModule.BlockTransition(
                        pos.immutable(), afterId, beforeId, expectedSound, expectedVolume, expectedPitch, false, System.currentTimeMillis()
                     )
                  );
               }
            }
         }
      }
   }

   private void offerBlockTransition(AntiVanishModule.BlockTransition transition) {
      if (transition != null) {
         if (this.blockTransitionCount.incrementAndGet() > 1024) {
            this.blockTransitionCount.updateAndGet(value -> Math.max(0, value - 1));
         } else {
            this.blockTransitions.offer(transition);
         }
      }
   }

   private void offerRawPlaceSound(AntiVanishModule.RawPlaceSound sound) {
      if (sound != null) {
         if (this.rawPlaceSoundCount.incrementAndGet() > 1024) {
            this.rawPlaceSoundCount.updateAndGet(value -> Math.max(0, value - 1));
         } else {
            this.rawPlaceSounds.offer(sound);
         }
      }
   }

   public static boolean shouldShowHud() {
      AntiVanishModule module = instance();
      return module != null && module.isEnabled() && module.bool("hud-list") && module.hasHudContent();
   }

   private boolean hasHudContent() {
      long now = System.currentTimeMillis();
      if (now < this.criticalUntilMs) {
         return true;
      } else {
         for (AntiVanishModule.Detection detection : this.detections.values()) {
            if (detection.expiresAt > now && detectionWorthShowing(detection)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean detectionWorthShowing(AntiVanishModule.Detection detection) {
      if (detection == null) {
         return false;
      } else {
         String name = detection.name == null ? "" : detection.name.trim();
         if (!name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name) && !"CRITICAL".equalsIgnoreCase(name)) {
            return true;
         } else {
            String reason = detection.reason == null ? "" : detection.reason.toLowerCase(Locale.ROOT);
            return reason.contains("rank detection:");
         }
      }
   }

   private void refreshTags() {
      long now = System.currentTimeMillis();
      if (now - this.lastTagScanMs >= 750L) {
         this.lastTagScanMs = now;

         try {
            Map<String, String> next = new HashMap<>();

            for (RiptidePlayerScanner.ScannedPlayer p : RiptidePlayerScanner.scan(MC)) {
               if (p.hasPrefix()) {
                  next.put(p.name().toLowerCase(Locale.ROOT), p.prefix());
               }
            }

            this.tagByName.clear();
            this.tagByName.putAll(next);
         } catch (Throwable var6) {
         }
      }
   }

   public static String hudTag(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "WATCH";
      } else if ("CRITICAL".equalsIgnoreCase(entry.name())) {
         return "ALERT";
      } else {
         String reason = entry.reason() == null ? "" : entry.reason();
         String lower = reason.toLowerCase(Locale.ROOT);
         int idx = lower.indexOf("rank detection:");
         if (idx >= 0) {
            AntiVanishModule module = instance();
            String name = entry.name() == null ? "" : entry.name().trim();
            if (module != null && !name.isBlank()) {
               String glyph = module.tagByName.get(name.toLowerCase(Locale.ROOT));
               if (glyph != null && !glyph.isBlank()) {
                  return glyph.length() <= 12 ? glyph : glyph.substring(0, 12);
               }
            }

            String word = reason.substring(idx + "rank detection:".length()).trim();
            if (!word.isBlank()) {
               String up = word.toUpperCase(Locale.ROOT);
               return up.length() <= 12 ? up : up.substring(0, 12);
            } else {
               return "RANK";
            }
         } else if (lower.startsWith("vanish event")) {
            return "VANISH";
         } else if (lower.startsWith("invisible entity")) {
            return "INVIS";
         } else if (lower.startsWith("suspicious sound")) {
            return "SOUND";
         } else if (lower.startsWith("camera aberration")) {
            return "CAMERA";
         } else if (lower.startsWith("ghost particle")) {
            return "PARTICLE";
         } else if (lower.startsWith("block")) {
            return "BLOCK";
         } else {
            return lower.startsWith("chunk") ? "CHUNK" : "WATCH";
         }
      }
   }

   public static String hudValue(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "Staff";
      } else if ("CRITICAL".equalsIgnoreCase(entry.name())) {
         AntiVanishModule module = instance();
         String summary = module == null ? "" : module.criticalSummary;
         return summary != null && !summary.isBlank() ? summary : "watching";
      } else {
         return compactHudName(entry);
      }
   }

   private static String shortSignal(AntiVanishModule.SignalType type) {
      return switch (type) {
         case VANISH -> "Vanish";
         case CAMERA -> "Camera";
         case INVISIBLE -> "Invisible";
         case PARTICLE -> "Particles";
         case SOUND -> "Sounds";
         case BLOCK -> "Blocks";
         case CHUNK -> "Chunks";
      };
   }

   public static boolean criticalActive() {
      AntiVanishModule module = instance();
      return module != null && module.isEnabled() && System.currentTimeMillis() < module.criticalUntilMs;
   }

   public static String criticalSummary() {
      AntiVanishModule module = instance();
      return module == null ? "" : module.criticalSummary;
   }

   public static List<AntiVanishModule.HudEntry> hudEntries() {
      AntiVanishModule module = instance();
      return module == null ? List.of() : module.hudSnapshot();
   }

   public static String compactHudName(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "Staff";
      } else {
         String name = entry.name() == null ? "" : entry.name().trim();
         if (!name.isBlank() && !"CRITICAL".equalsIgnoreCase(name) && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name)) {
            return name.length() <= 16 ? name : name.substring(0, 16);
         } else {
            return "Staff";
         }
      }
   }

   public static String compactHudReason(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "";
      } else {
         String reason = entry.reason() == null ? "" : entry.reason().trim();
         String lower = reason.toLowerCase(Locale.ROOT);
         if ("CRITICAL".equalsIgnoreCase(entry.name())) {
            return "WATCH";
         } else if (lower.startsWith("vanish event")) {
            return "Vanish";
         } else if (lower.startsWith("rank detection")) {
            return "Rank";
         } else if (lower.startsWith("invisible entity")) {
            return "Invis";
         } else if (lower.startsWith("entity packet spike")) {
            return "Packets";
         } else if (lower.startsWith("camera aberration")) {
            return "Camera";
         } else if (lower.startsWith("ghost particle")) {
            return "Particle";
         } else if (lower.startsWith("suspicious sound")) {
            return "Sound";
         } else if (lower.startsWith("block")) {
            return "Block";
         } else if (lower.startsWith("chunk")) {
            return "Chunk";
         } else {
            return reason.length() <= 10 ? reason : reason.substring(0, 10);
         }
      }
   }

   private static AntiVanishModule instance() {
      return ModuleRegistry.get("anti-vanish") instanceof AntiVanishModule antiVanish ? antiVanish : null;
   }

   public static String censusSummary() {
      AntiVanishModule module = instance();
      return module != null && module.isEnabled()
         ? "obs="
            + module.observations.size()
            + " known="
            + module.knownPlayers.size()
            + " det="
            + module.detections.size()
            + " chunks="
            + module.seenChunks.size()
            + " pendingVanish="
            + module.pendingVanishes.size()
            + " msgs="
            + module.recentMessages.size()
         : "off";
   }

   private void updateStationaryState() {
      Vec3 position = MC.player.position();
      if (this.lastPosition != null) {
         double dx = position.x - this.lastPosition.x;
         double dz = position.z - this.lastPosition.z;
         if (dx * dx + dz * dz < 4.0E-4) {
            this.stationaryTicks++;
         } else {
            this.stationaryTicks = 0;
         }
      }

      this.lastPosition = position;
      this.lastYaw = MC.player.getYRot();
      this.lastPitch = MC.player.getXRot();
   }

   private AntiVanishModule.Observation cameraObservation(ClientboundPlayerPositionPacket packet) {
      Vec3 base = this.lastPosition;
      Vec3 target = packet.change().position();
      Set<Relative> relatives = packet.relatives();
      double displacement = Double.POSITIVE_INFINITY;
      if (base != null && target != null) {
         double x = relatives.contains(Relative.X) ? base.x + target.x : target.x;
         double y = relatives.contains(Relative.Y) ? base.y + target.y : target.y;
         double z = relatives.contains(Relative.Z) ? base.z + target.z : target.z;
         displacement = base.distanceTo(new Vec3(x, y, z));
      }

      float targetYaw = relatives.contains(Relative.Y_ROT) ? this.lastYaw + packet.change().yRot() : packet.change().yRot();
      float targetPitch = relatives.contains(Relative.X_ROT) ? this.lastPitch + packet.change().xRot() : packet.change().xRot();
      double rotation = Math.hypot(wrapDegrees(targetYaw - this.lastYaw), targetPitch - this.lastPitch);
      return AntiVanishModule.Observation.cameraCorrection(displacement, rotation);
   }

   private void drainObservations() {
      for (int i = 0; i < 512; i++) {
         AntiVanishModule.Observation observation = this.observations.poll();
         if (observation == null) {
            break;
         }

         this.processObservation(observation);
      }

      while (this.observations.size() > 4096) {
         this.observations.poll();
      }
   }

   private void processObservation(AntiVanishModule.Observation observation) {
      switch (observation.type) {
         case TAB_REMOVE:
            this.handleTabRemoval(observation.profileId);
            break;
         case TAB_HIDE:
            this.handleTabHidden(observation.profileId);
            break;
         case PLAYER_LEFT:
            this.serverSendsLeaveMessages = true;
            this.confirmDeparture(observation.detail);
            break;
         case SYSTEM_CHAT:
            this.cacheRecentMessage(observation.detail);
            break;
         case ENTITY_METADATA:
            this.inspectInvisibleEntity(observation.entityId);
            break;
         case ENTITY_SWING:
            this.rememberHiddenSwing(observation);
            break;
         case POSITIONAL_SOUND:
            this.inspectPositionalSound(observation);
            break;
         case ENTITY_SOUND:
            this.inspectEntitySound(observation);
            break;
         case PARTICLE:
            this.inspectParticle(observation);
            break;
         case BLOCK_EVENT:
         case BLOCK_BREAK:
            this.inspectBlockUpdate(observation);
         case BLOCK_UPDATE:
         default:
            break;
         case BLOCK_DIG:
            this.rememberRemoteDig(observation);
            break;
         case CHUNK_DATA:
            this.inspectChunk(observation.chunkX, observation.chunkZ);
            break;
         case EXPLOSION:
            this.rememberExplosion(observation);
            break;
         case CAMERA_CORRECTION:
            this.inspectCameraCorrection(observation);
      }
   }

   private void trackListedPlayers() {
      if (MC.getConnection() != null) {
         long now = System.currentTimeMillis();

         for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().id() != null) {
               UUID uuid = info.getProfile().id();
               if (MC.player == null || !MC.player.getUUID().equals(uuid)) {
                  this.ambiguousDepartures.remove(uuid);
                  this.listedSinceMs.putIfAbsent(uuid, now);
                  String name = info.getProfile().name();
                  if (name != null && !this.knownPlayers.containsKey(uuid)) {
                     this.knownPlayers.put(uuid, new AntiVanishModule.KnownPlayer(uuid, name, "", "", false));
                  }
               }
            }
         }

         if (this.listedSinceMs.size() > 1024 || this.knownPlayers.size() > 1024) {
            Set<UUID> connected = new HashSet<>();

            for (PlayerInfo infox : MC.getConnection().getOnlinePlayers()) {
               if (infox != null && infox.getProfile() != null) {
                  connected.add(infox.getProfile().id());
               }
            }

            this.listedSinceMs.keySet().retainAll(connected);
            this.knownPlayers.entrySet().removeIf(entry -> !connected.contains(entry.getKey()) && !entry.getValue().staff);
         }
      }
   }

   private boolean trustedListed(UUID uuid) {
      Long since = uuid == null ? null : this.listedSinceMs.get(uuid);
      return since != null && System.currentTimeMillis() - since >= 1500L;
   }

   private boolean credibleSubject(UUID uuid, String name) {
      return uuid != null
         && RiptidePlayerScanner.isUsername(name)
         && this.trustedListed(uuid)
         && !RiptideAntiBot.isConfirmedBot(uuid)
         && this.passesPlayerFilter(name);
   }

   private boolean sensorOn(String id) {
      return this.bool("environmental") && this.bool(id);
   }

   private boolean passesPlayerFilter(String name) {
      if (this.bool("player-filter") && name != null) {
         for (String watched : this.list("players")) {
            if (watched.equalsIgnoreCase(name)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private void handleTabRemoval(UUID uuid) {
      if (this.bool("vanish-tracker") && uuid != null && !MC.player.getUUID().equals(uuid)) {
         String name = this.knownName(uuid);
         if (this.credibleSubject(uuid, name) && AntiVanishText.isPlausiblePlayerName(name)) {
            this.pendingVanishes.removeIf(pending -> pending.uuid.equals(uuid));
            this.pendingVanishes.addLast(new AntiVanishModule.PendingVanish(uuid, name, this.tickCounter + 20));
         }
      }
   }

   private void handleTabHidden(UUID uuid) {
      if (this.bool("vanish-tracker") && uuid != null && !MC.player.getUUID().equals(uuid)) {
         AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
         String name = known != null && known.name != null ? known.name : this.knownName(uuid);
         if (this.credibleSubject(uuid, name) && AntiVanishText.isPlausiblePlayerName(name)) {
            if (this.recentMessageNames(name)) {
               this.rememberAmbiguousDeparture(uuid, name, known != null && known.staff);
            } else {
               long now = System.currentTimeMillis();
               boolean staff = known != null && known.staff;
               String reason = staff ? "Vanish Event: staff hidden from TAB" : "Vanish Event: hidden from TAB";
               this.upsertDetection(uuid.toString(), name, reason, 100, now + 20000L);
               this.addSignal(AntiVanishModule.SignalType.VANISH, name, reason, 100, 15000L, true);
            }
         }
      }
   }

   private String knownName(UUID uuid) {
      AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
      if (known != null && known.name != null && !known.name.isBlank()) {
         return known.name;
      } else {
         if (MC.getConnection() != null) {
            PlayerInfo info = MC.getConnection().getPlayerInfo(uuid);
            if (info != null && info.getProfile() != null) {
               return info.getProfile().name();
            }
         }

         return null;
      }
   }

   private void confirmDeparture(String displayedName) {
      long now = System.currentTimeMillis();

      for (AntiVanishModule.KnownPlayer known : this.knownPlayers.values()) {
         if (AntiVanishText.containsPlayerName(displayedName, known.name)) {
            boolean hadPending = this.pendingVanishes.stream().anyMatch(candidate -> candidate.uuid.equals(known.uuid));
            if (hadPending) {
               this.rememberAmbiguousDeparture(known.uuid, known.name, known.staff);
            }

            this.confirmedDepartures.put(known.uuid, now + 5000L);
            this.pendingVanishes.removeIf(pending -> pending.uuid.equals(known.uuid));
            AntiVanishModule.Detection detection = this.detections.get(known.uuid.toString());
            if (detection != null && tabDepartureReason(detection.reason)) {
               this.detections.remove(known.uuid.toString());
            }

            this.signals
               .removeIf(
                  signal -> signal.type == AntiVanishModule.SignalType.VANISH
                     && signal.subject.equalsIgnoreCase(known.name)
                     && tabDepartureReason(signal.reason)
               );
         }
      }
   }

   private void processPendingVanishes() {
      while (!this.pendingVanishes.isEmpty() && this.pendingVanishes.peekFirst().dueTick <= this.tickCounter) {
         AntiVanishModule.PendingVanish pending = this.pendingVanishes.removeFirst();
         if (MC.getConnection().getPlayerInfo(pending.uuid) == null) {
            long now = System.currentTimeMillis();
            if (this.confirmedDepartures.getOrDefault(pending.uuid, 0L) <= now) {
               if (this.recentMessageNames(pending.name)) {
                  this.serverSendsLeaveMessages = true;
                  AntiVanishModule.KnownPlayer known = this.knownPlayers.get(pending.uuid);
                  this.rememberAmbiguousDeparture(pending.uuid, pending.name, known != null && known.staff);
               } else if (!RiptideAntiBot.isConfirmedBot(pending.uuid)) {
                  Player remaining = MC.level.getPlayerByUUID(pending.uuid);
                  AntiVanishModule.KnownPlayer known = this.knownPlayers.get(pending.uuid);
                  boolean staff = known != null && known.staff;
                  if (remaining != null && !remaining.isRemoved()) {
                     String reason = "Vanish Event: entity remained";
                     this.upsertDetection(pending.uuid.toString(), pending.name, reason, 100, now + 20000L);
                     this.addSignal(AntiVanishModule.SignalType.VANISH, pending.name, reason, 100, 10000L, true);
                  } else {
                     int score = silentTabRemovalScore(staff, this.serverSendsLeaveMessages);
                     String reason = staff ? "Vanish Event: staff left TAB silently" : "Vanish Event: silent TAB disappearance";
                     this.upsertDetection(pending.uuid.toString(), pending.name, reason, score, now + 20000L);
                     this.addSignal(AntiVanishModule.SignalType.VANISH, pending.name, reason, score, 10000L, true);
                  }
               }
            }
         }
      }
   }

   static int silentTabRemovalScore(boolean staff, boolean serverSendsLeaveMessages) {
      return !staff && !serverSendsLeaveMessages ? 70 : 100;
   }

   static boolean tabDepartureReason(String reason) {
      if (reason == null) {
         return false;
      } else {
         String lower = reason.toLowerCase(Locale.ROOT);
         return lower.startsWith("vanish event:") && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
      }
   }

   private void rememberAmbiguousDeparture(UUID uuid, String name, boolean staff) {
      if (uuid != null && name != null && !name.isBlank()) {
         this.ambiguousDepartures.put(uuid, new AntiVanishModule.AmbiguousDeparture(name, staff, System.currentTimeMillis() + 30000L));
      }
   }

   private void cacheRecentMessage(String text) {
      if (text != null && !text.isBlank()) {
         long now = System.currentTimeMillis();
         this.recentMessages.addLast(new AntiVanishModule.RecentMessage(text, now));

         while (!this.recentMessages.isEmpty() && (now - this.recentMessages.peekFirst().atMs > 8000L || this.recentMessages.size() > 64)) {
            this.recentMessages.removeFirst();
         }
      }
   }

   private boolean recentMessageNames(String name) {
      if (name != null && !name.isBlank()) {
         long now = System.currentTimeMillis();

         for (AntiVanishModule.RecentMessage message : this.recentMessages) {
            if (now - message.atMs <= 8000L && AntiVanishText.looksLikeLeaveMessage(message.text, name)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void sendCompletionProbe() {
      if (MC.getConnection() != null) {
         String command = this.text("probe-command");
         if (command == null || command.isBlank()) {
            command = "minecraft:msg";
         }

         int id = this.nextCompletionId++;
         if (this.nextCompletionId > 40000) {
            this.nextCompletionId = 30000;
         }

         this.completionRequestIds.add(id);

         while (this.completionRequestIds.size() > 8) {
            this.completionRequestIds.remove(this.completionRequestIds.iterator().next());
         }

         try {
            MC.getConnection().send(new ServerboundCommandSuggestionPacket(id, command.trim() + " "));
         } catch (Throwable var4) {
         }
      }
   }

   private void processCompletionProbe() {
      List<String> current = this.pendingCompletionNames;
      if (current != null) {
         this.pendingCompletionNames = null;
         if (this.bool("completion-probe") && MC.getConnection() != null && MC.player != null) {
            Set<String> tabNames = new HashSet<>();

            for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
               if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                  tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
               }
            }

            String self = MC.player.getName().getString();
            List<String> hidden = new ArrayList<>();

            for (String name : current) {
               if (AntiVanishText.isPlausiblePlayerName(name)
                  && !name.equalsIgnoreCase(self)
                  && this.passesPlayerFilter(name)
                  && !tabNames.contains(name.toLowerCase(Locale.ROOT))
                  && !this.recentMessageNames(name)) {
                  hidden.add(name);
               }
            }

            if (!hidden.isEmpty() && hidden.size() <= 3) {
               long now = System.currentTimeMillis();

               for (String namex : hidden) {
                  String reason = "Vanish Event: hidden but targetable";
                  this.upsertDetection(namex, namex, reason, 90, now + 20000L);
                  this.addSignal(AntiVanishModule.SignalType.VANISH, namex, reason, 90, 10000L, true);
               }
            }
         }
      }
   }

   private static String departedPlayerName(Component component) {
      if (component != null && component.getContents() instanceof TranslatableContents translated && "multiplayer.player.left".equals(translated.getKey())) {
         Object[] args = translated.getArgs();
         if (args.length != 0 && args[0] != null) {
            return args[0] instanceof Component name ? name.getString() : String.valueOf(args[0]);
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   private void scanInvisiblePlayers() {
      double rangeSq = this.sensorRangeSq();

      for (Player player : MC.level.players()) {
         if (player != null && player != MC.player && player.isInvisible() && !(player.distanceToSqr(MC.player) > rangeSq) && this.realPlayer(player)) {
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            this.triggerSensor(AntiVanishModule.SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5000L);
         }
      }
   }

   private void inspectInvisibleEntity(int entityId) {
      if (this.sensorOn("invisible-sensor")) {
         if (MC.level.getEntity(entityId) instanceof Player player && player != MC.player && player.isInvisible()) {
            if (!(player.distanceToSqr(MC.player) > this.sensorRangeSq())) {
               if (this.realPlayer(player)) {
                  String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
                  this.triggerSensor(AntiVanishModule.SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5000L);
               }
            }
         }
      }
   }

   private boolean realPlayer(Player player) {
      String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
      return this.credibleSubject(player.getUUID(), name);
   }

   private void rememberHiddenSwing(AntiVanishModule.Observation observation) {
      if ((MC.level == null ? null : MC.level.getEntity(observation.entityId)) instanceof Player player) {
         String subject = this.credibleHiddenSubject(player);
         if (!subject.isBlank()) {
            this.recentHiddenSwings
               .put(
                  observation.entityId,
                  new AntiVanishModule.HiddenSwing(
                     observation.entityId, subject, player.getEyePosition(), player.getLookAngle().normalize(), System.currentTimeMillis() + 750L
                  )
               );
         }
      }
   }

   private void rememberRemoteDig(AntiVanishModule.Observation observation) {
      int progress;
      try {
         progress = Integer.parseInt(observation.detail);
      } catch (NumberFormatException var7) {
         return;
      }

      if (progress >= 0 && observation.entityId != this.localPlayerId && MC.level != null) {
         if (MC.level.getEntity(observation.entityId) instanceof Player player) {
            String subject = this.credibleHiddenSubject(player);
            Vec3 source = observation.position();
            if (!subject.isBlank() && aimedAt(player.getEyePosition(), player.getLookAngle(), source)) {
               this.recentRemoteDigs
                  .put(BlockPos.containing(source).asLong(), new AntiVanishModule.RemoteDig(observation.entityId, subject, System.currentTimeMillis() + 3000L));
            }
         }
      }
   }

   private String credibleHiddenSubject(Player player) {
      if (player != null && player != MC.player && this.realPlayer(player)) {
         UUID uuid = player.getUUID();
         AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
         boolean staff = known != null && known.staff;
         boolean listed = false;
         if (MC.getConnection() != null) {
            for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
               if (info != null && info.getProfile() != null && uuid.equals(info.getProfile().id())) {
                  listed = true;
                  break;
               }
            }
         }

         if (!listed || staff && player.isInvisible()) {
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            return name == null ? "" : name;
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   private AntiVanishModule.HiddenSwing hiddenSwingNear(Vec3 source, long now) {
      AntiVanishModule.HiddenSwing best = null;
      double bestDistance = Double.POSITIVE_INFINITY;
      Iterator<Entry<Integer, AntiVanishModule.HiddenSwing>> iterator = this.recentHiddenSwings.entrySet().iterator();

      while (iterator.hasNext()) {
         AntiVanishModule.HiddenSwing swing = iterator.next().getValue();
         if (swing.expiresAt < now) {
            iterator.remove();
         } else {
            double distance = swing.eye.distanceToSqr(source);
            if (distance < bestDistance && aimedAt(swing.eye, swing.look, source)) {
               best = swing;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private static boolean aimedAt(Vec3 eye, Vec3 look, Vec3 source) {
      if (eye != null && look != null && source != null) {
         Vec3 delta = source.subtract(eye);
         double distanceSq = delta.lengthSqr();
         return !(distanceSq < 0.01) && !(distanceSq > 42.25) ? look.normalize().dot(delta.normalize()) >= 0.8 : false;
      } else {
         return false;
      }
   }

   private void inspectPositionalSound(AntiVanishModule.Observation observation) {
      if (this.sensorOn("sound-sensor") && AntiVanishHeuristics.suspiciousSound(observation.detail)) {
         Vec3 source = observation.position();
         long now = System.currentTimeMillis();
         if (this.nearPlayer(source)
            && !this.hasVisibleCause(source)
            && !this.isExplosionRelated(source)
            && !this.isPoweredMechanism(source, observation.detail)
            && now - this.lastLocalActionMs >= 1000L
            && (!this.selfContainerActive() || !isContainerSignal(observation.detail))) {
            this.triggerSensor(AntiVanishModule.SignalType.SOUND, this.locatedSubject(source), "Suspicious Sound: " + shortId(observation.detail), 14, 3000L);
         }
      }
   }

   private void inspectEntitySound(AntiVanishModule.Observation observation) {
      if (this.sensorOn("sound-sensor") && AntiVanishHeuristics.suspiciousSound(observation.detail)) {
         if (MC.level.getEntity(observation.entityId) instanceof Player player
            && player != MC.player
            && player.isInvisible()
            && player.distanceToSqr(MC.player) <= this.sensorRangeSq()
            && this.realPlayer(player)) {
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            this.triggerSensor(AntiVanishModule.SignalType.SOUND, name, "Suspicious Sound: invisible source", 16, 3000L);
         }
      }
   }

   private void inspectParticle(AntiVanishModule.Observation observation) {
      if (this.sensorOn("particle-sensor") && AntiVanishHeuristics.suspiciousParticle(observation.detail)) {
         Vec3 source = observation.position();
         long now = System.currentTimeMillis();
         if (this.nearPlayer(source)
            && !this.hasVisibleCause(source)
            && !this.isExplosionRelated(source)
            && now - this.lastLocalActionMs >= 900L
            && !this.hasAmbientParticleSource(source, observation.detail)) {
            String particle = shortId(observation.detail);
            if (!particle.equals("block") && !particle.contains("smoke") || !this.nearSelf(source, 6.25)) {
               if (!particle.equals("block") && !particle.contains("smoke") || this.particleBurstReady(particle, now)) {
                  this.triggerSensor(
                     AntiVanishModule.SignalType.PARTICLE, this.locatedSubject(source), "Ghost Particle: " + shortId(observation.detail), 16, 3000L
                  );
               }
            }
         }
      }
   }

   private void inspectBlockUpdate(AntiVanishModule.Observation observation) {
      if (this.sensorOn("block-sensor")) {
         Vec3 source = observation.position();
         long now = System.currentTimeMillis();
         if (observation.type == AntiVanishModule.ObservationType.BLOCK_BREAK) {
            this.recentBreakEffects.put(BlockPos.containing(source).asLong(), new AntiVanishModule.BreakEffect(observation.detail, now));
         }

         if (this.blockWorldStable(source, now)
            && this.nearPlayer(source)
            && !this.hasVisibleBlockCause(source)
            && !this.isExplosionRelated(source)
            && !this.isPoweredMechanism(source, observation.detail)
            && !this.recentlySelfBroke(source)) {
            String label = classifyBlockChange(observation.type, observation.detail);
            if (label != null) {
               if (!label.equals("Block Interaction") || !this.villagerToggledDoor(observation.detail, source)) {
                  if (!label.equals("Block Interaction") || !this.selfContainerActive() || !isContainerSignal(observation.detail)) {
                     AntiVanishModule.HiddenSwing swing = this.hiddenSwingNear(source, now);
                     if (label.equals("Block Break")) {
                        long key = BlockPos.containing(source).asLong();
                        AntiVanishModule.RemoteDig dig = this.recentRemoteDigs.get(key);
                        String actorSubject = dig != null && dig.expiresAt >= now ? dig.subject : (swing == null ? "" : swing.subject);
                        if (!actorSubject.isBlank()) {
                           String subject = blockEvidenceSubject(actorSubject, this.locatedSubject(source));
                           int actorId = dig != null && dig.expiresAt >= now ? dig.entityId : (swing == null ? -1 : swing.entityId);
                           this.pendingBreaks.addLast(new AntiVanishModule.PendingBreak(source, observation.detail, subject, actorId, now));

                           while (this.pendingBreaks.size() > 32) {
                              this.pendingBreaks.removeFirst();
                           }
                        }
                     } else if (swing != null) {
                        String dedupKey = label + "@" + BlockPos.containing(source).asLong();
                        this.triggerSensor(AntiVanishModule.SignalType.BLOCK, swing.subject, dedupKey, label + ": " + shortId(observation.detail), 13, 800L);
                     }
                  }
               }
            }
         }
      }
   }

   private static String classifyBlockChange(AntiVanishModule.ObservationType type, String id) {
      String path = AntiVanishHeuristics.path(id);
      if (type == AntiVanishModule.ObservationType.BLOCK_BREAK) {
         return AntiVanishHeuristics.credibleBreakBlock(id) ? "Block Break" : null;
      } else if (type == AntiVanishModule.ObservationType.BLOCK_EVENT) {
         return AntiVanishHeuristics.blockEventInteraction(path) ? "Block Interaction" : null;
      } else {
         return null;
      }
   }

   private boolean villagerToggledDoor(String blockId, Vec3 source) {
      String path = AntiVanishHeuristics.path(blockId);
      boolean doorLike = path.contains("door") && !path.contains("trapdoor") || path.contains("fence_gate");
      if (doorLike && source != null && MC.level != null) {
         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof Villager && entity.position().distanceToSqr(source) <= 9.0) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void drainBlockEvidence() {
      long now = System.currentTimeMillis();

      for (int i = 0; i < 128; i++) {
         AntiVanishModule.RawPlaceSound sound = this.rawPlaceSounds.poll();
         if (sound == null) {
            break;
         }

         this.rawPlaceSoundCount.updateAndGet(value -> Math.max(0, value - 1));
         this.recentPlaceSounds.addLast(new AntiVanishModule.PlaceSound(sound.pos, sound.soundId, sound.volume, sound.pitch, sound.timeMs));
      }

      for (int i = 0; i < 128; i++) {
         AntiVanishModule.BlockTransition transition = this.blockTransitions.poll();
         if (transition == null) {
            break;
         }

         this.blockTransitionCount.updateAndGet(value -> Math.max(0, value - 1));
         Vec3 source = Vec3.atCenterOf(transition.pos);
         long key = transition.pos.asLong();
         if (transition.removal) {
            this.recentAirUpdates.put(key, new AntiVanishModule.Removal(transition.previousId, transition.timeMs));
            if (AntiVanishHeuristics.credibleAnonymousBreakTransition(transition.previousId, transition.nextId)
               && this.blockWorldStable(source, now)
               && this.nearPlayer(source)
               && !this.recentlySelfBroke(source)
               && !this.hasVisibleBlockCause(source)
               && !this.hasAutomatedBlockSource(source)
               && !this.isExplosionRelated(source)
               && !this.hasNearbyFireOrLava(source)) {
               this.pendingAnonymousBreaks
                  .addLast(new AntiVanishModule.PendingAnonymousBreak(source, transition.previousId, transition.nextId, transition.timeMs));

               while (this.pendingAnonymousBreaks.size() > 64) {
                  this.pendingAnonymousBreaks.removeFirst();
               }
            }
         } else if (this.blockWorldStable(source, now)
            && this.nearPlayer(source)
            && !this.hasVisibleBlockCause(source)
            && !this.hasAutomatedBlockSource(source)
            && !this.isExplosionRelated(source)
            && !this.hasNearbyFluidOrFire(source)
            && !this.recentlySelfPlaced(source)) {
            this.pendingPlaces
               .addLast(
                  new AntiVanishModule.PendingPlace(
                     source,
                     transition.previousId,
                     transition.nextId,
                     transition.expectedSound,
                     transition.expectedVolume,
                     transition.expectedPitch,
                     transition.timeMs
                  )
               );

            while (this.pendingPlaces.size() > 64) {
               this.pendingPlaces.removeFirst();
            }
         }
      }

      this.trimPlaceSounds(now);
   }

   private void trimPlaceSounds(long now) {
      while (!this.recentPlaceSounds.isEmpty() && now - this.recentPlaceSounds.peekFirst().timeMs > 1000L) {
         this.recentPlaceSounds.removeFirst();
      }

      while (this.recentPlaceSounds.size() > 64) {
         this.recentPlaceSounds.removeFirst();
      }
   }

   private void processPendingPlaces() {
      if (!this.pendingPlaces.isEmpty()) {
         long now = System.currentTimeMillis();
         this.trimPlaceSounds(now);
         int pending = this.pendingPlaces.size();

         for (int i = 0; i < pending; i++) {
            AntiVanishModule.PendingPlace place = this.pendingPlaces.pollFirst();
            if (place == null) {
               break;
            }

            AntiVanishModule.HiddenSwing swing = this.hiddenSwingNear(place.pos, now);
            boolean heard = this.hasMatchingPlaceSound(place);
            if (heard) {
               if (swing != null && this.validAnonymousBlockContext(place.pos, false) && this.blockStillMatches(place.pos, place.detail)) {
                  this.recentHiddenSwings.remove(swing.entityId, swing);
                  String dedupKey = "Block Place@" + BlockPos.containing(place.pos).asLong();
                  this.triggerSensor(AntiVanishModule.SignalType.BLOCK, swing.subject, dedupKey, "Block Place: " + shortId(place.detail), 13, 800L);
               }
            } else if (now - place.timeMs < 700L) {
               this.pendingPlaces.addLast(place);
            } else if (anonymousPlacementReady(place.previousId, place.detail, false, this.blockStillMatches(place.pos, place.detail))
               && this.validAnonymousBlockContext(place.pos, false)) {
               String subject = this.anonymousBlockSubject(place.pos);
               String dedupKey = "Block Place@" + BlockPos.containing(place.pos).asLong();
               this.triggerSensor(AntiVanishModule.SignalType.BLOCK, subject, dedupKey, "Block Place: " + shortId(place.detail), 13, 800L);
            }
         }
      }
   }

   private boolean hasMatchingPlaceSound(AntiVanishModule.PendingPlace place) {
      BlockPos expectedPos = BlockPos.containing(place.pos);

      for (AntiVanishModule.PlaceSound sound : this.recentPlaceSounds) {
         BlockPos soundPos = BlockPos.containing(sound.pos);
         int manhattan = Math.abs(soundPos.getX() - expectedPos.getX())
            + Math.abs(soundPos.getY() - expectedPos.getY())
            + Math.abs(soundPos.getZ() - expectedPos.getZ());
         if (manhattan <= 1
            && AntiVanishHeuristics.sameEvidenceWindow(sound.timeMs, place.timeMs, 700L)
            && AntiVanishHeuristics.matchingPlaceSound(place.expectedSound, sound.soundId)
            && !(Math.abs(place.expectedVolume - sound.volume) > 1.0E-4F)
            && !(Math.abs(place.expectedPitch - sound.pitch) > 1.0E-4F)) {
            return true;
         }
      }

      return false;
   }

   private void processPendingBreaks() {
      if (!this.pendingBreaks.isEmpty()) {
         long now = System.currentTimeMillis();
         int pending = this.pendingBreaks.size();

         for (int i = 0; i < pending; i++) {
            AntiVanishModule.PendingBreak broken = this.pendingBreaks.pollFirst();
            if (broken == null) {
               break;
            }

            if (now - broken.timeMs <= 900L) {
               long key = BlockPos.containing(broken.pos).asLong();
               AntiVanishModule.Removal removal = this.recentAirUpdates.get(key);
               if (removal == null || !AntiVanishHeuristics.matchingBreakEvidence(broken.detail, broken.timeMs, removal.previousId, removal.timeMs, 900L)) {
                  this.pendingBreaks.addLast(broken);
               } else if (!this.hasVisibleBlockCause(broken.pos) && !this.isExplosionRelated(broken.pos)) {
                  this.recentAirUpdates.remove(key);
                  this.recentRemoteDigs.remove(key);
                  if (broken.actorId >= 0) {
                     this.recentHiddenSwings.remove(broken.actorId);
                  }

                  String dedupKey = "Block Break@" + key;
                  this.triggerSensor(AntiVanishModule.SignalType.BLOCK, broken.subject, dedupKey, "Block Break: " + shortId(broken.detail), 13, 800L);
               }
            }
         }
      }
   }

   private void processPendingAnonymousBreaks() {
      if (!this.pendingAnonymousBreaks.isEmpty()) {
         long now = System.currentTimeMillis();
         int pending = this.pendingAnonymousBreaks.size();

         for (int i = 0; i < pending; i++) {
            AntiVanishModule.PendingAnonymousBreak broken = this.pendingAnonymousBreaks.pollFirst();
            if (broken == null) {
               break;
            }

            if (now - broken.timeMs < 900L) {
               this.pendingAnonymousBreaks.addLast(broken);
            } else {
               long key = BlockPos.containing(broken.pos).asLong();
               AntiVanishModule.BreakEffect effect = this.recentBreakEffects.get(key);
               boolean matchingEffect = effect != null
                  && AntiVanishHeuristics.matchingBreakEffect(effect.blockId, effect.timeMs, broken.previousId, broken.timeMs, 900L);
               if (anonymousBreakReady(broken.previousId, broken.nextId, matchingEffect, this.blockStillMatches(broken.pos, broken.nextId))
                  && this.validAnonymousBlockContext(broken.pos, true)) {
                  this.recentAirUpdates.remove(key);
                  String subject = this.anonymousBlockSubject(broken.pos);
                  String dedupKey = "Block Break@" + key;
                  this.triggerSensor(AntiVanishModule.SignalType.BLOCK, subject, dedupKey, "Block Break: " + shortId(broken.previousId), 13, 800L);
               }
            }
         }
      }
   }

   private void inspectChunk(int chunkX, int chunkZ) {
      long key = chunkKey(chunkX, chunkZ);
      long now = System.currentTimeMillis();
      this.blockChunkQuietUntil.put(key, now + 750L);
      boolean resend = !this.seenChunks.add(key);
      if (this.sensorOn("chunk-sensor")
         && resend
         && this.tickCounter >= 100
         && this.stationaryTicks >= 40
         && System.currentTimeMillis() - this.lastServerCorrectionMs >= 5000L) {
         int playerChunkX = (int)Math.floor(MC.player.getX()) >> 4;
         int playerChunkZ = (int)Math.floor(MC.player.getZ()) >> 4;
         if (Math.abs(chunkX - playerChunkX) <= 2 && Math.abs(chunkZ - playerChunkZ) <= 2) {
            Deque<Long> repeats = this.chunkResends.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            repeats.addLast(now);

            while (!repeats.isEmpty() && now - repeats.peekFirst() > 10000L) {
               repeats.removeFirst();
            }

            if (repeats.size() >= 2) {
               repeats.clear();
               this.triggerSensor(AntiVanishModule.SignalType.CHUNK, "near you", "Chunk Re-send: " + chunkX + ", " + chunkZ, 15, 15000L);
            }
         }
      }
   }

   private boolean recentlySelfBroke(Vec3 source) {
      if (source == null) {
         return false;
      } else {
         Long until = this.selfBrokenBlocks.get(BlockPos.containing(source).asLong());
         return until != null && until > System.currentTimeMillis();
      }
   }

   private boolean recentlySelfPlaced(Vec3 source) {
      if (source == null) {
         return false;
      } else {
         Long until = this.selfPlacedBlocks.get(BlockPos.containing(source).asLong());
         return until != null && until > System.currentTimeMillis();
      }
   }

   private boolean validAnonymousBlockContext(Vec3 source, boolean removal) {
      long now = System.currentTimeMillis();
      return this.blockWorldStable(source, now)
         && this.nearPlayer(source)
         && !this.hasVisibleBlockCause(source)
         && !this.hasAutomatedBlockSource(source)
         && !this.isExplosionRelated(source)
         && (removal ? !this.hasNearbyFireOrLava(source) : !this.hasNearbyFluidOrFire(source))
         && (removal ? !this.recentlySelfBroke(source) : !this.recentlySelfPlaced(source));
   }

   private boolean blockStillMatches(Vec3 source, String expectedId) {
      if (source != null && expectedId != null && MC.level != null) {
         BlockState current = MC.level.getBlockState(BlockPos.containing(source));
         if (current == null) {
            return false;
         } else {
            Identifier id = BuiltInRegistries.BLOCK.getKey(current.getBlock());
            return AntiVanishHeuristics.path(id == null ? "" : id.toString()).equals(AntiVanishHeuristics.path(expectedId));
         }
      } else {
         return false;
      }
   }

   private String anonymousBlockSubject(Vec3 source) {
      long now = System.currentTimeMillis();
      AntiVanishModule.AmbiguousDeparture onlyStaff = null;
      int staffCount = 0;
      Iterator<Entry<UUID, AntiVanishModule.AmbiguousDeparture>> iterator = this.ambiguousDepartures.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, AntiVanishModule.AmbiguousDeparture> entry = iterator.next();
         AntiVanishModule.AmbiguousDeparture candidate = entry.getValue();
         if (candidate.expiresAt > now && (MC.getConnection() == null || MC.getConnection().getPlayerInfo(entry.getKey()) == null)) {
            if (candidate.staff) {
               staffCount++;
               onlyStaff = candidate;
            }
         } else {
            iterator.remove();
         }
      }

      return staffCount == 1 ? onlyStaff.name : this.locatedSubject(source);
   }

   private void inspectCameraCorrection(AntiVanishModule.Observation observation) {
      long now = System.currentTimeMillis();
      this.lastServerCorrectionMs = now;
      if (this.sensorOn("camera-sensor") && this.stationaryTicks >= 15 && this.tickCounter > 60) {
         double displacement = observation.x;
         double rotation = observation.y;
         boolean smallPositionReset = displacement >= 0.02 && displacement <= 1.5;
         boolean cameraJerk = rotation >= 2.0 && rotation <= 45.0;
         if (smallPositionReset || cameraJerk) {
            this.cameraCorrections.addLast(now);

            while (!this.cameraCorrections.isEmpty() && now - this.cameraCorrections.peekFirst() > 4000L) {
               this.cameraCorrections.removeFirst();
            }

            if (this.cameraCorrections.size() >= 2) {
               this.cameraCorrections.clear();
               String detail = cameraJerk
                  ? String.format(Locale.ROOT, "Camera Aberration: %.1f° reset", rotation)
                  : String.format(Locale.ROOT, "Camera Aberration: %.2fm reset", displacement);
               this.triggerSensor(AntiVanishModule.SignalType.CAMERA, "on you", detail, 20, 8000L);
            }
         }
      }
   }

   private void rememberExplosion(AntiVanishModule.Observation observation) {
      long now = System.currentTimeMillis();
      this.recentExplosions
         .addLast(new AntiVanishModule.ExplosionEvent(observation.position(), Math.max(2.0, parseDouble(observation.detail, 4.0) + 4.0), now));

      while (this.recentExplosions.size() > 8) {
         this.recentExplosions.removeFirst();
      }
   }

   private boolean particleBurstReady(String particle, long now) {
      Deque<Long> burst = this.weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque<>());
      burst.addLast(now);

      while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
         burst.removeFirst();
      }

      if (burst.size() < 2) {
         return false;
      } else {
         burst.clear();
         return true;
      }
   }

   private void triggerSensor(AntiVanishModule.SignalType type, String subject, String reason, int weight, long cooldownMs) {
      this.addSignal(type, subject, subject, reason, weight, cooldownMs, false);
   }

   private void triggerSensor(AntiVanishModule.SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs) {
      this.addSignal(type, subject, dedupKey, reason, weight, cooldownMs, false);
   }

   private void addSignal(AntiVanishModule.SignalType type, String subject, String reason, int weight, long cooldownMs, boolean instant) {
      this.addSignal(type, subject, subject, reason, weight, cooldownMs, instant);
   }

   private void addSignal(AntiVanishModule.SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs, boolean instant) {
      long now = System.currentTimeMillis();
      String cooldownKey = type.name() + "|" + dedupKey.toLowerCase(Locale.ROOT);
      long last = this.signalCooldowns.getOrDefault(cooldownKey, 0L);
      if (now - last >= cooldownMs) {
         this.signalCooldowns.put(cooldownKey, now);
         this.signals.addLast(new AntiVanishModule.Signal(type, subject, reason, weight, now));
         this.upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + 20000L);
         this.lastTrigger = reason;
         this.announceTrigger(cooldownKey, subject, reason);
         this.evaluateCritical(instant);
      }
   }

   private void evaluateCritical(boolean instant) {
      long now = System.currentTimeMillis();
      this.pruneSignals(now);
      EnumMap<AntiVanishModule.SignalType, Integer> strongest = new EnumMap<>(AntiVanishModule.SignalType.class);

      for (AntiVanishModule.Signal signal : this.signals) {
         strongest.merge(signal.type, signal.weight, Math::max);
      }

      this.currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
      if (this.bool("critical-alert")) {
         if (instant || strongest.size() >= 2 && this.currentScore >= 35) {
            if (now - this.lastCriticalMs >= 12000L) {
               this.lastCriticalMs = now;
               this.criticalUntilMs = now + 7000L;
               this.criticalSummary = strongest.keySet()
                  .stream()
                  .map(AntiVanishModule::shortSignal)
                  .sorted()
                  .reduce((a, b) -> a + " + " + b)
                  .orElse(this.lastTrigger);
               AntiVanishModule.SignalType top = strongest.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(null);
               RiptideNotifications.error(top == null ? "Staff may be watching you" : "Staff watching you: " + shortSignal(top));
               if (this.bool("alert-sound") && MC.getSoundManager() != null) {
                  MC.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
               }
            }
         }
      }
   }

   private void handleGamemodeUpdates(ClientboundPlayerInfoUpdatePacket info) {
      if (this.bool("gamemode-alerts")) {
         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
            if (entry != null && entry.gameMode() != null) {
               UUID uuid = entry.profileId();
               if (uuid != null && (MC.player == null || !MC.player.getUUID().equals(uuid))) {
                  AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
                  if (known != null && known.name != null && !known.name.isBlank() && this.passesPlayerFilter(known.name)) {
                     RiptideClientMessaging.sendPrefixed("§bGamemode: §f" + known.name + " §7-> " + entry.gameMode().getName());
                  }
               }
            }
         }
      }
   }

   private void announceTrigger(String eventKey, String subject, String reason) {
      if (this.bool("chat-alerts")) {
         long now = System.currentTimeMillis();
         Long last = this.announceCooldowns.get(eventKey);
         if (last == null || now - last >= 1500L) {
            while (!this.announceTimes.isEmpty() && now - this.announceTimes.peekFirst() > 4000L) {
               this.announceTimes.removeFirst();
            }

            if (this.announceTimes.size() < 6) {
               this.announceCooldowns.put(eventKey, now);
               this.announceTimes.addLast(now);
               boolean located = subject != null && !subject.isBlank() && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
               String where = located ? " §7(" + subject + ")" : "";
               RiptideClientMessaging.sendPrefixed("§b" + reason + where);
            }
         }
      }
   }

   private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
      AntiVanishModule.Detection existing = this.detections.get(key);
      if (existing == null) {
         this.detections.put(key, new AntiVanishModule.Detection(name, reason, score, expiresAt));
      } else {
         existing.name = name;
         existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
         if (score >= existing.score) {
            existing.reason = reason;
            existing.score = score;
         }
      }
   }

   private List<AntiVanishModule.HudEntry> hudSnapshot() {
      this.refreshTags();
      long now = System.currentTimeMillis();
      List<AntiVanishModule.HudEntry> out = new ArrayList<>();
      if (now < this.criticalUntilMs) {
         out.add(new AntiVanishModule.HudEntry("CRITICAL", this.criticalSummary, 100));
      }

      Set<String> seenRows = new HashSet<>();
      this.detections
         .values()
         .stream()
         .filter(detection -> detection.expiresAt > now)
         .filter(AntiVanishModule::detectionWorthShowing)
         .sorted(
            Comparator.<AntiVanishModule.Detection>comparingInt(detection -> detection.score)
               .reversed()
               .thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER)
         )
         .map(detection -> new AntiVanishModule.HudEntry(detection.name, detection.reason, detection.score))
         .filter(entry -> seenRows.add(hudTag(entry) + "\u0000" + hudValue(entry)))
         .limit(4L)
         .forEach(out::add);
      return List.copyOf(out);
   }

   private void pruneState() {
      long now = System.currentTimeMillis();
      this.detections.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
      this.signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
      this.announceCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
      this.selfBrokenBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.selfPlacedBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.confirmedDepartures.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.ambiguousDepartures.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
      this.automatedMechanisms.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.blockChunkQuietUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.recentRemoteDigs.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
      this.recentAirUpdates.entrySet().removeIf(entry -> now - entry.getValue().timeMs > 1400L);
      this.recentBreakEffects.entrySet().removeIf(entry -> now - entry.getValue().timeMs > 1400L);
      this.recentHiddenSwings.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
      this.weakParticleBursts.values().removeIf(burst -> {
         while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
            burst.removeFirst();
         }

         return burst.isEmpty();
      });
      this.chunkResends.values().removeIf(repeats -> {
         while (!repeats.isEmpty() && now - repeats.peekFirst() > 10000L) {
            repeats.removeFirst();
         }

         return repeats.isEmpty();
      });

      while (!this.cameraCorrections.isEmpty() && now - this.cameraCorrections.peekFirst() > 4000L) {
         this.cameraCorrections.removeFirst();
      }

      while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
         this.recentExplosions.removeFirst();
      }

      this.pruneSignals(now);
      EnumMap<AntiVanishModule.SignalType, Integer> strongest = new EnumMap<>(AntiVanishModule.SignalType.class);

      for (AntiVanishModule.Signal signal : this.signals) {
         strongest.merge(signal.type, signal.weight, Math::max);
      }

      this.currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
   }

   private void pruneSignals(long now) {
      while (!this.signals.isEmpty() && now - this.signals.peekFirst().timeMs > 15000L) {
         this.signals.removeFirst();
      }
   }

   private boolean nearPlayer(Vec3 source) {
      return source != null && source.distanceToSqr(MC.player.position()) <= this.sensorRangeSq();
   }

   private boolean blockWorldStable(Vec3 source, long now) {
      if (source != null && this.tickCounter >= 10) {
         int chunkX = (int)Math.floor(source.x) >> 4;
         int chunkZ = (int)Math.floor(source.z) >> 4;
         return this.blockChunkQuietUntil.getOrDefault(chunkKey(chunkX, chunkZ), 0L) <= now;
      } else {
         return false;
      }
   }

   private boolean nearSelf(Vec3 source, double maxDistSq) {
      return source != null && MC.player != null && source.distanceToSqr(MC.player.position()) < maxDistSq;
   }

   private boolean selfContainerActive() {
      return System.currentTimeMillis() - this.lastContainerActivityMs < 2500L;
   }

   private static boolean isContainerSignal(String id) {
      String path = AntiVanishHeuristics.path(id);
      return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
   }

   private String locatedSubject(Vec3 source) {
      if (source != null && MC.player != null) {
         Vec3 me = MC.player.position();
         double dx = source.x - me.x;
         double dy = source.y - me.y;
         double dz = source.z - me.z;
         long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
         String dir = compass(dx, dz);
         return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
      } else {
         return "nearby";
      }
   }

   static String blockEvidenceSubject(String actorSubject, String locatedSubject) {
      String actor = actorSubject == null ? "" : actorSubject.trim();
      if (!actor.isEmpty()) {
         return actor;
      } else {
         String located = locatedSubject == null ? "" : locatedSubject.trim();
         return located.isEmpty() ? "nearby" : located;
      }
   }

   static boolean anonymousPlacementReady(String previousId, String nextId, boolean matchingSound, boolean finalStateMatches) {
      return !matchingSound && finalStateMatches && AntiVanishHeuristics.credibleAnonymousPlacementTransition(previousId, nextId);
   }

   static boolean anonymousBreakReady(String previousId, String nextId, boolean matchingBreakEffect, boolean finalStateMatches) {
      return !matchingBreakEffect && finalStateMatches && AntiVanishHeuristics.credibleAnonymousBreakTransition(previousId, nextId);
   }

   private static String compass(double dx, double dz) {
      String ns = dz < -1.0 ? "N" : (dz > 1.0 ? "S" : "");
      String ew = dx > 1.0 ? "E" : (dx < -1.0 ? "W" : "");
      return ns + ew;
   }

   private boolean hasVisibleCause(Vec3 source) {
      if (source == null) {
         return true;
      } else {
         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity != MC.player) {
               if (entity instanceof Player player) {
                  if (!player.isInvisible() && entity.position().distanceToSqr(source) <= 16.0) {
                     return true;
                  }
               } else if (entity instanceof Projectile && entity.position().distanceToSqr(source) <= 16.0) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean hasVisibleBlockCause(Vec3 source) {
      if (source != null && MC.level != null) {
         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity != null && entity != MC.player) {
               if (entity instanceof Player player) {
                  if (this.credibleHiddenSubject(player).isBlank()) {
                     double distanceSq = entity.position().distanceToSqr(source);
                     if (distanceSq <= 9.0 || aimedAt(player.getEyePosition(), player.getLookAngle(), source)) {
                        return true;
                     }
                  }
               } else if (entity instanceof LivingEntity || entity instanceof Projectile || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt
                  )
                {
                  double distanceSq = entity.position().distanceToSqr(source);
                  if ((entity instanceof Projectile || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) && distanceSq <= 25.0) {
                     return true;
                  }

                  if (entity instanceof LivingEntity && distanceSq <= 36.0 && entityCanModifyBlocks(entity)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static boolean entityCanModifyBlocks(Entity entity) {
      Identifier id = entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
      return path.equals("enderman")
         || path.equals("ravager")
         || path.equals("wither")
         || path.equals("ender_dragon")
         || path.equals("zombie")
         || path.equals("husk")
         || path.equals("drowned")
         || path.equals("silverfish")
         || path.equals("sheep")
         || path.equals("rabbit")
         || path.equals("fox")
         || path.equals("turtle")
         || path.equals("snow_golem")
         || path.equals("villager");
   }

   private boolean hasAutomatedBlockSource(Vec3 source) {
      if (source != null && MC.level != null) {
         BlockPos center = BlockPos.containing(source);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  BlockPos pos = center.offset(dx, dy, dz);
                  BlockState state = MC.level.getBlockState(pos);
                  Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                  String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
                  if (path.contains("moving_piston") || path.contains("piston_head")) {
                     return true;
                  }

                  if (path.contains("dispenser") || path.contains("dropper") || path.contains("piston")) {
                     boolean active = MC.level.hasNeighborSignal(pos);
                     if (state.hasProperty(BlockStateProperties.TRIGGERED)) {
                        active |= state.getValue(BlockStateProperties.TRIGGERED);
                     }

                     if (state.hasProperty(BlockStateProperties.EXTENDED)) {
                        active |= state.getValue(BlockStateProperties.EXTENDED);
                     }

                     if (active) {
                        return true;
                     }
                  }
               }
            }
         }

         for (int[] axis : BLOCK_AXES) {
            for (int distance = 2; distance <= 12; distance++) {
               BlockPos posx = center.offset(axis[0] * distance, axis[1] * distance, axis[2] * distance);
               BlockState statex = MC.level.getBlockState(posx);
               Identifier idx = BuiltInRegistries.BLOCK.getKey(statex.getBlock());
               String pathx = AntiVanishHeuristics.path(idx == null ? "" : idx.toString());
               if (pathx.contains("piston")
                  && (
                     pathx.contains("moving_piston")
                        || pathx.contains("piston_head")
                        || MC.level.hasNeighborSignal(posx)
                        || statex.hasProperty(BlockStateProperties.EXTENDED) && (Boolean)statex.getValue(BlockStateProperties.EXTENDED)
                  )) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private boolean isExplosionRelated(Vec3 source) {
      if (source == null) {
         return false;
      } else {
         long now = System.currentTimeMillis();

         while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
            this.recentExplosions.removeFirst();
         }

         for (AntiVanishModule.ExplosionEvent explosion : this.recentExplosions) {
            if (explosion.center.distanceToSqr(source) <= explosion.radius * explosion.radius) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasNearbyFluidOrFire(Vec3 source) {
      return this.hasNearbyEnvironment(source, true);
   }

   private boolean hasNearbyFireOrLava(Vec3 source) {
      return this.hasNearbyEnvironment(source, false);
   }

   private boolean hasNearbyEnvironment(Vec3 source, boolean includeWater) {
      if (source != null && MC.level != null) {
         BlockPos center = BlockPos.containing(source);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(center.offset(dx, dy, dz)).getBlock());
                  String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
                  if (path.contains("lava") || path.contains("fire") || includeWater && path.contains("water")) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private boolean hasAmbientParticleSource(Vec3 source, String particleId) {
      if (source != null && shortId(particleId).contains("smoke")) {
         BlockPos center = BlockPos.containing(source);

         for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
               for (int dz = -2; dz <= 2; dz++) {
                  BlockPos pos = center.offset(dx, dy, dz);
                  Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
                  String path = shortId(id == null ? "" : id.toString());
                  if (path.contains("campfire")
                     || path.contains("furnace")
                     || path.contains("smoker")
                     || path.contains("torch")
                     || path.contains("fire")
                     || path.contains("candle")
                     || path.contains("respawn_anchor")) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isPoweredMechanism(Vec3 source, String blockOrSoundId) {
      String path = shortId(blockOrSoundId);
      if (!path.contains("door") && !path.contains("trapdoor")) {
         return false;
      } else {
         BlockPos center = BlockPos.containing(source);
         long now = System.currentTimeMillis();

         for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = center.offset(0, dy, 0);
            BlockState state = MC.level.getBlockState(pos);
            long key = pos.asLong();
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && (Boolean)state.getValue(BlockStateProperties.POWERED);
            if (powered || MC.level.hasNeighborSignal(pos)) {
               this.automatedMechanisms.put(key, now + 5000L);
               return true;
            }

            if (this.automatedMechanisms.getOrDefault(key, 0L) > now) {
               return true;
            }
         }

         return false;
      }
   }

   private double sensorRangeSq() {
      double r = Math.max(8, this.integer("range"));
      return r * r;
   }

   private static double parseDouble(String value, double fallback) {
      try {
         return Double.parseDouble(value);
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static double wrapDegrees(double degrees) {
      double wrapped = degrees % 360.0;
      if (wrapped >= 180.0) {
         wrapped -= 360.0;
      }

      if (wrapped < -180.0) {
         wrapped += 360.0;
      }

      return wrapped;
   }

   private static String soundId(Identifier id) {
      return id == null ? "" : id.toString();
   }

   private static String shortId(String id) {
      return AntiVanishHeuristics.path(id);
   }

   private static long chunkKey(int x, int z) {
      return (long)x << 32 ^ z & 4294967295L;
   }

   private void resetRuntime() {
      this.observations.clear();
      this.blockTransitions.clear();
      this.rawPlaceSounds.clear();
      this.blockTransitionCount.set(0);
      this.rawPlaceSoundCount.set(0);
      this.knownPlayers.clear();
      this.detections.clear();
      this.signalCooldowns.clear();
      this.announceCooldowns.clear();
      this.announceTimes.clear();
      this.selfBrokenBlocks.clear();
      this.selfPlacedBlocks.clear();
      this.confirmedDepartures.clear();
      this.ambiguousDepartures.clear();
      this.automatedMechanisms.clear();
      this.weakParticleBursts.clear();
      this.chunkResends.clear();
      this.blockChunkQuietUntil.clear();
      this.signals.clear();
      this.cameraCorrections.clear();
      this.recentExplosions.clear();
      this.pendingPlaces.clear();
      this.recentPlaceSounds.clear();
      this.recentRemoteDigs.clear();
      this.recentAirUpdates.clear();
      this.recentBreakEffects.clear();
      this.pendingBreaks.clear();
      this.pendingAnonymousBreaks.clear();
      this.recentHiddenSwings.clear();
      this.pendingVanishes.clear();
      this.seenChunks.clear();
      this.lastLevel = null;
      this.lastPosition = null;
      this.stationaryTicks = 0;
      this.tickCounter = 0;
      this.localPlayerId = Integer.MIN_VALUE;
      this.lastLocalActionMs = 0L;
      this.lastContainerActivityMs = 0L;
      this.lastServerCorrectionMs = 0L;
      this.lastYaw = 0.0F;
      this.lastPitch = 0.0F;
      this.lastCriticalMs = 0L;
      this.criticalUntilMs = 0L;
      this.currentScore = 0;
      this.criticalSummary = "";
      this.lastTrigger = "";
      this.recentMessages.clear();
      this.listedSinceMs.clear();
      this.serverSendsLeaveMessages = false;
      this.completionRequestIds.clear();
      this.pendingCompletionNames = null;
      this.nextCompletionId = 30000;
   }

   private record AmbiguousDeparture(String name, boolean staff, long expiresAt) {
   }

   private record BlockTransition(
      BlockPos pos, String nextId, String previousId, String expectedSound, float expectedVolume, float expectedPitch, boolean removal, long timeMs
   ) {
   }

   private record BreakEffect(String blockId, long timeMs) {
   }

   private static final class Detection {
      String name;
      String reason;
      int score;
      long expiresAt;

      Detection(String name, String reason, int score, long expiresAt) {
         this.name = name;
         this.reason = reason;
         this.score = score;
         this.expiresAt = expiresAt;
      }
   }

   private record ExplosionEvent(Vec3 center, double radius, long timeMs) {
   }

   private record HiddenSwing(int entityId, String subject, Vec3 eye, Vec3 look, long expiresAt) {
   }

   public record HudEntry(String name, String reason, int score) {
   }

   private record KnownPlayer(UUID uuid, String name, String rank, String rankSource, boolean staff) {
   }

   private record Observation(
      AntiVanishModule.ObservationType type, UUID profileId, int entityId, double x, double y, double z, String detail, int chunkX, int chunkZ
   ) {
      static AntiVanishModule.Observation tabRemove(UUID id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.TAB_REMOVE, id, -1, 0.0, 0.0, 0.0, "", 0, 0);
      }

      static AntiVanishModule.Observation tabHide(UUID id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.TAB_HIDE, id, -1, 0.0, 0.0, 0.0, "", 0, 0);
      }

      static AntiVanishModule.Observation playerLeft(String name) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.PLAYER_LEFT, null, -1, 0.0, 0.0, 0.0, name, 0, 0);
      }

      static AntiVanishModule.Observation systemChat(String text) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.SYSTEM_CHAT, null, -1, 0.0, 0.0, 0.0, text, 0, 0);
      }

      static AntiVanishModule.Observation entityMetadata(int id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.ENTITY_METADATA, null, id, 0.0, 0.0, 0.0, "", 0, 0);
      }

      static AntiVanishModule.Observation cameraCorrection(double displacement, double rotation) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.CAMERA_CORRECTION, null, -1, displacement, rotation, 0.0, "", 0, 0);
      }

      static AntiVanishModule.Observation explosion(Vec3 center, float radius) {
         return new AntiVanishModule.Observation(
            AntiVanishModule.ObservationType.EXPLOSION, null, -1, center.x, center.y, center.z, Float.toString(radius), 0, 0
         );
      }

      static AntiVanishModule.Observation entitySound(int id, String sound) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.ENTITY_SOUND, null, id, 0.0, 0.0, 0.0, sound, 0, 0);
      }

      static AntiVanishModule.Observation position(AntiVanishModule.ObservationType type, double x, double y, double z, String detail) {
         return new AntiVanishModule.Observation(type, null, -1, x, y, z, detail, 0, 0);
      }

      static AntiVanishModule.Observation block(AntiVanishModule.ObservationType type, BlockPos pos, String detail) {
         return position(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, detail);
      }

      static AntiVanishModule.Observation blockActor(AntiVanishModule.ObservationType type, BlockPos pos, int entityId, int action) {
         return new AntiVanishModule.Observation(type, null, entityId, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Integer.toString(action), 0, 0);
      }

      static AntiVanishModule.Observation entityAction(AntiVanishModule.ObservationType type, int entityId, int action) {
         return new AntiVanishModule.Observation(type, null, entityId, 0.0, 0.0, 0.0, Integer.toString(action), 0, 0);
      }

      static AntiVanishModule.Observation chunk(int x, int z) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.CHUNK_DATA, null, -1, 0.0, 0.0, 0.0, "", x, z);
      }

      Vec3 position() {
         return new Vec3(this.x, this.y, this.z);
      }
   }

   private static enum ObservationType {
      TAB_REMOVE,
      TAB_HIDE,
      PLAYER_LEFT,
      SYSTEM_CHAT,
      ENTITY_METADATA,
      ENTITY_SWING,
      POSITIONAL_SOUND,
      ENTITY_SOUND,
      PARTICLE,
      BLOCK_EVENT,
      BLOCK_UPDATE,
      BLOCK_BREAK,
      BLOCK_DIG,
      CHUNK_DATA,
      EXPLOSION,
      CAMERA_CORRECTION;
   }

   private record PendingAnonymousBreak(Vec3 pos, String previousId, String nextId, long timeMs) {
   }

   private record PendingBreak(Vec3 pos, String detail, String subject, int actorId, long timeMs) {
   }

   private record PendingPlace(Vec3 pos, String previousId, String detail, String expectedSound, float expectedVolume, float expectedPitch, long timeMs) {
   }

   private record PendingVanish(UUID uuid, String name, int dueTick) {
   }

   private record PlaceSound(Vec3 pos, String soundId, float volume, float pitch, long timeMs) {
   }

   private record RawPlaceSound(Vec3 pos, String soundId, float volume, float pitch, long timeMs) {
   }

   private record RecentMessage(String text, long atMs) {
   }

   private record RemoteDig(int entityId, String subject, long expiresAt) {
   }

   private record Removal(String previousId, long timeMs) {
   }

   private record Signal(AntiVanishModule.SignalType type, String subject, String reason, int weight, long timeMs) {
   }

   private static enum SignalType {
      VANISH("Vanish Event"),
      CAMERA("Camera Aberration"),
      INVISIBLE("Invisible Entity"),
      PARTICLE("Ghost Particles"),
      SOUND("Suspicious Sounds"),
      BLOCK("Block Updates"),
      CHUNK("Chunk Re-sends");

      final String label;

      private SignalType(String label) {
         this.label = label;
      }
   }
}
