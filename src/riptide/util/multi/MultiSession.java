package riptide.util.multi;

import com.google.common.hash.HashCode;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.timeout.ReadTimeoutException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkBatchSizeCalculator;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.HashedStack;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.HashedPatchMap.HashGenerator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.LastSeenMessagesTracker.Update;
import net.minecraft.network.chat.RemoteChatSession.Data;
import net.minecraft.network.chat.SignedMessageChain.Encoder;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket.NodeBuilder;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot;
import net.minecraft.network.protocol.game.GameProtocols.Context;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Crypt;
import net.minecraft.util.HashOps;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Crypt.SaltSupplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey.ValidationException;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior.MinecartStep;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.custommenu.CustomMenuInput;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.gui.multi.MultiMenuGeometry;
import riptide.mixin.accessor.RiptideClientConnectionAccessor;
import riptide.mixin.accessor.RiptideFishingHookAccessor;
import riptide.mixin.accessor.RiptideMoveEntityPacketAccessor;
import riptide.mixin.accessor.RiptidePlayerCommandPacketAccessor;
import riptide.mixin.accessor.RiptideRotateHeadPacketAccessor;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountType;
import riptide.util.RiptideConfig;
import riptide.util.RiptideDropAction;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptidePacketArgumentBuilder;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePayloadSupport;
import riptide.util.RiptideProxy;
import riptide.util.custommenu.CustomMenuSession;
import riptide.util.login.AutoLoginConfig;
import riptide.util.login.AutoLoginEngine;
import riptide.util.login.AutoLoginHost;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.ContainerClickSequenceAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.PacketBurstAction;
import riptide.util.macro.PacketClipSafety;
import riptide.util.macro.PickUpAllAction;
import riptide.util.macro.SignEditAction;
import riptide.util.macro.StoreItemAction;
import riptide.util.macro.SwapSlotsAction;
import riptide.util.macro.UseItemPhaseAction;
import riptide.util.macro.WaitForMacroStepAction;
import riptide.util.macro.WaitForPacketAction;
import riptide.util.macro.WaitForSlotChangeAction;
import riptide.util.macro.WaitPacketMatchAction;
import riptide.util.macro.XCarryAction;
import riptide.util.multi.captcha.CaptchaSolver;

public final class MultiSession implements MultiMacroHost {
   private static final long STALL_AFTER_MS = 20000L;
   private static final double GRAVITY = 0.08;
   private static final double DRAG = 0.98;
   private static final double TERMINAL_VELOCITY = -3.92;
   private static final long JOIN_MOVE_ACTIVE_MS = 5000L;
   private static final long AUTO_AUX_INTERVAL_MS = 1000L;
   private static final long IDLE_HEARTBEAT_MS = 1000L;
   private static final long RESPAWN_DELAY_MS = 1000L;
   private static final long CONNECT_PHASE_TIMEOUT_MS = 60000L;
   private static final long PLAYER_LOAD_CLOSE_DELAY_MS = 500L;
   private static final long PLAYER_LOAD_HEADLESS_FALLBACK_MS = 2000L;
   private static final long PLAYER_LOAD_TIMEOUT_MS = 30000L;
   private static final AtomicLong MENU_EPOCHS = new AtomicLong();
   private static final NodeBuilder<Object> COMMAND_NODE_BUILDER = new NodeBuilder<Object>() {
      public ArgumentBuilder<Object, ?> createLiteral(String id) {
         return LiteralArgumentBuilder.literal(id);
      }

      public ArgumentBuilder<Object, ?> createArgument(String id, ArgumentType<?> argumentType, @Nullable Identifier suggestionId) {
         return RequiredArgumentBuilder.argument(id, argumentType);
      }

      public ArgumentBuilder<Object, ?> configure(ArgumentBuilder<Object, ?> builder, boolean executable, boolean restricted) {
         if (executable) {
            builder.executes(context -> 0);
         }

         return builder;
      }
   };
   private final long generation;
   private final MultiProfile.SessionSpec spec;
   private final RiptideProxy proxy;
   private final String proxyName;
   private volatile MultiPacketPolicy policy;
   private final MultiSession.Sink sink;
   private final Executor worker;
   private final CaptchaSolver captcha;
   private final MultiViaCompat.Target viaTarget;
   private final AtomicReference<MultiSession.Status> status = new AtomicReference<>(MultiSession.Status.QUEUED);
   private final Map<Identifier, byte[]> cookies = new LinkedHashMap<>();
   private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
   private final Set<UUID> listedPlayers = ConcurrentHashMap.newKeySet();
   private static final int LISTED_PLAYERS_CAP = 8192;
   private final Object scoreboardLock = new Object();
   private final Map<String, MultiSession.ScoreObjective> scoreObjectives = new LinkedHashMap<>();
   private final Map<String, Map<String, MultiSession.TrackedScore>> scoresByObjective = new LinkedHashMap<>();
   private final Map<DisplaySlot, String> displayedObjectives = new EnumMap<>(DisplaySlot.class);
   private final Map<String, MultiSession.ScoreTeam> scoreTeams = new LinkedHashMap<>();
   private final Map<String, String> scoreOwnerTeams = new HashMap<>();
   private volatile RegistryDataCollector registryData = new RegistryDataCollector();
   private volatile LastSeenMessagesTracker lastSeenMessages = new LastSeenMessagesTracker(20);
   private volatile MessageSignatureCache signatureCache = MessageSignatureCache.createDefault();
   private final Object commandSource = new Object();
   private final Object chatStateLock = new Object();
   private volatile String detail = "Queued";
   private volatile long statusSince = System.currentTimeMillis();
   private volatile int ping = -1;
   private volatile long lastInboundAt;
   private volatile Connection connection;
   private volatile int connectEpoch;
   private volatile boolean nextConnectTransferring;
   private volatile boolean suppressChatKey;
   private volatile UUID serverAssignedUuid;
   private volatile GameProfile serverGameProfile;
   private volatile String lastConnectHost = "";
   private volatile int lastConnectPort;
   private volatile MultiIdentityResolver.Identity identity;
   private volatile boolean sessionRefreshAttempted;
   private volatile FeatureFlagSet enabledFeatures = FeatureFlags.DEFAULT_FLAGS;
   private volatile Frozen registries;
   private volatile PositionMoveRotation position = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F);
   private final Object positionLock = new Object();
   private volatile boolean hasPosition;
   private volatile boolean readyOnce;
   private final ChunkBatchSizeCalculator chunkBatchSizeCalculator = new ChunkBatchSizeCalculator();
   private final AtomicBoolean playerLoadedSent = new AtomicBoolean();
   private volatile boolean levelChunksLoadStarted;
   private volatile boolean playerLoadChunkBatchFinished;
   private volatile boolean playerLoadPositionAccepted;
   private volatile long playerLoadReadyAt = Long.MAX_VALUE;
   private volatile long playerLoadHeadlessFallbackAt = Long.MAX_VALUE;
   private volatile long playerLoadDeadlineAt = Long.MAX_VALUE;
   private volatile double motionY;
   private volatile boolean grounded = true;
   private volatile boolean primeFallTick;
   private volatile boolean inVehicle;
   private volatile int vehicleId = -1;
   private volatile double vehicleX;
   private volatile double vehicleY;
   private volatile double vehicleZ;
   private volatile double vehicleFallMotion;
   private volatile boolean postVehicleFall;
   private volatile long postVehicleFallUntil;
   private volatile float vehicleYaw;
   private static final double VEHICLE_GRAVITY = 0.04F;
   private volatile long moveActiveUntil;
   private volatile boolean gravitySettleRequest;
   private volatile double walkX;
   private volatile double walkZ;
   private volatile long walkUntil;
   private volatile long macroMotorUntil;
   private volatile boolean walkGrounded;
   private volatile long walkProbeAt;
   private volatile long lastWalkCorrectionAt;
   private volatile int rapidWalkCorrections;
   private long lastWalkBlockedNoteAt;
   private static final long WALK_BLOCKED_NOTE_COOLDOWN_MS = 30000L;
   private static final long GROUND_PROBE_MS = 500L;
   private volatile boolean inputShift;
   private volatile boolean inputSprint = true;
   private volatile boolean sprintAnnounced;
   private volatile long lastLookAt;
   private volatile long lastSwingAt;
   private final AtomicBoolean closed = new AtomicBoolean();
   private volatile boolean serverEnforcesSecureChat;
   private volatile long lastMovementAt;
   private volatile int nextChatIndex;
   private volatile CommandDispatcher<Object> commands = new CommandDispatcher();
   private volatile boolean hasCommandTree;
   private long lastCommandsBuildAt;
   private static final long COMMANDS_REBUILD_MIN_MS = 750L;
   private final AtomicInteger suggestReqId = new AtomicInteger();
   private final Object suggestionLock = new Object();
   private final Map<Integer, MultiSession.Suggest> suggestionReplies = new LinkedHashMap<>();
   private volatile ClientInformation clientInformation = ClientInformation.createDefault();
   private volatile int openContainerId = -1;
   private volatile int containerStateId;
   private volatile int inventoryStateId;
   private volatile String openScreenTitle = "";
   private volatile String openMenuTypeId = "";
   private volatile Component openTitle = Component.empty();
   private volatile long openScreenSeq;
   private volatile long menuEpoch;
   private volatile MultiSession.MenuPhase menuPhase = MultiSession.MenuPhase.SYNCING;
   private volatile boolean inventorySynchronized;
   private volatile boolean menuInteractive;
   private volatile long teleportSeq;
   private volatile int gameModeId = -1;
   private final Object chatLogLock = new Object();
   private final ArrayDeque<String> recentChat = new ArrayDeque<>();
   private volatile long chatSeqCounter;
   private static final int CHAT_LOG_CAP = 64;
   private volatile boolean packetCaptureArmed;
   private volatile long packetSeqCounter;
   private final Object packetLogLock = new Object();
   private final ArrayDeque<MultiSession.PktRec> packetLog = new ArrayDeque<>();
   private static final int PACKET_LOG_CAP = 128;
   private volatile BlockPos signEditorPos;
   private volatile boolean signEditorOpen;
   private volatile boolean signEditorFront = true;
   private volatile BlockPos lastInteractBlock;
   private volatile int lastInteractEntityId = -1;
   private volatile boolean soundCaptureArmed;
   private volatile long soundSeqCounter;
   private final Object soundLogLock = new Object();
   private final ArrayDeque<MultiSession.SndRec> soundLog = new ArrayDeque<>();
   private static final int SOUND_LOG_CAP = 64;
   private final Map<String, Long> cooldownExpiry = new ConcurrentHashMap<>();
   private static final int COOLDOWN_CAP = 256;
   private final Map<Long, BlockState> blockUpdates = new ConcurrentHashMap<>();
   private static final int BLOCK_UPDATE_CAP = 512;
   private volatile boolean captureWorld;
   private final MultiWorldCapture worldCapture = new MultiWorldCapture();
   private volatile int selectedHotbar;
   private volatile int lastWireHotbar = -1;
   private volatile int useSeq;
   private volatile boolean containerDismissed;
   private final Object menuLock = new Object();
   private final List<ItemStack> menuSlots = new ArrayList<>();
   private final List<ItemStack> playerInv = new ArrayList<>(Collections.nCopies(46, ItemStack.EMPTY));
   private volatile ItemStack carried = ItemStack.EMPTY;
   private final int[] menuData = new int[16];
   private int menuDataLen;
   private MerchantOffers merchantOffers;
   private int villagerLevel;
   private int villagerXp;
   private boolean villagerShowProgress;
   private static final int CLICK_QUEUE_CAP = 128;
   private static final int COMMAND_CLICK_REPEAT_CAP = 100000;
   private static final long CLICK_QUIET_MS = 50L;
   private final ArrayDeque<MultiSession.QueuedClick> clickQueue = new ArrayDeque<>();
   private MultiSession.QueuedClick clickInFlight;
   private long clickSeq;
   private long clickSentAt;
   private long clickLastUpdateAt;
   private boolean clickSawUpdate;
   private boolean clickSyncBlocked;
   private MultiSession.DeferredMenuClose closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
   private volatile MultiSession.SavedMenu savedMenu;
   private volatile MultiSession.XCarryJob xCarryJob;
   private volatile boolean xCarryForced;
   private volatile boolean xCarryActive;
   private volatile HashGenerator itemHasher;
   private volatile Frozen itemHasherFor;
   private volatile String heldItemName = "";
   private volatile String dimension = "";
   private volatile String serverAddress = "";
   private volatile int playerEntityId = -1;
   private volatile float health = 20.0F;
   private volatile float maxHealth = 20.0F;
   private volatile double blockInteractionRange = 4.5;
   private volatile double entityInteractionRange = 3.0;
   private volatile int food = 20;
   private volatile long respawnAt;
   private volatile long menuRevision = MENU_EPOCHS.incrementAndGet() << 32;
   private volatile Encoder signedEncoder = Encoder.UNSIGNED;
   private volatile LocalChatSession chatSession;
   private long userSendWindowAt;
   private int userSendsInWindow;
   private final MultiProfile.LoginMode loginMode;
   private static final long LOGIN_WINDOW_MS = 40000L;
   private volatile boolean loginMacroRun;
   private long loginMacroDeadline;
   private final String profileName;
   private volatile Map<String, String> formValues;
   private final CustomMenuSession customMenus = new CustomMenuSession();
   private volatile long connectStartedAt;
   private volatile boolean missingPasswordAlerted;
   private static final long LOGIN_SCREEN_ALERT_WINDOW_MS = 10000L;
   private final AutoLoginEngine autoLogin = new AutoLoginEngine(new MultiSession.MultiAutoLoginHost());
   private volatile MultiMacroRun macroRun;
   private volatile RiptideMacro macroStartRequest;
   private volatile boolean macroStopRequest;
   private volatile String macroStatus = "";
   private volatile MultiSession.MacroProgress macroProgress = MultiSession.MacroProgress.idle();
   private volatile MultiSession.MacroQueue macroQueue = MultiSession.MacroQueue.NONE;
   private final MultiPilotTruth outboundTruth = new MultiPilotTruth();
   private final AtomicReference<String> macroFinishNote = new AtomicReference<>();
   private final MultiEntityTracker entities = new MultiEntityTracker();
   private volatile MultiAutoAccept autoAccept = new MultiAutoAccept();
   private volatile String autoAcceptArmedKind = "";
   private volatile long autoAcceptArmedUntil;
   private volatile long autoFireAt;
   private volatile String autoFireCommand = "";
   private volatile String autoFireMacro = "";
   private final Map<String, Long> responderCooldown = new ConcurrentHashMap<>();
   private volatile MultiSession.Snapshot publishedSnapshot;
   private static final int MENU_SLOT_CAP = 2048;
   private volatile long captchaAnnouncedUntil;
   private static final long CAPTCHA_ANNOUNCE_MS = 30000L;
   private static final Pattern CAPTCHA_ANNOUNCE = Pattern.compile("captcha|verif|anti-?bot|bot ?check|\\bhuman\\b|prove you", 2);
   private volatile int verificationRetries = 0;
   private static final int MAX_VERIFICATION_RETRIES = 5;
   private long transferBurstStart;
   private int transferBurst;
   private static final long TRANSFER_WINDOW_MS = 10000L;
   private static final int TRANSFER_BURST_MAX = 12;
   private static final int ITEM_PLAN_CAP = 256;
   private volatile boolean piloted;
   private volatile Vec3 pilotObservedPosition = Vec3.ZERO;
   private volatile long pilotObservedAt;
   private final Object movePairLock = new Object();
   private final Object pilotProtocolLock = new Object();
   private final MultiTimerBudget timerBudget = new MultiTimerBudget();
   private final ArrayDeque<PacketClipSafety.Step> clipQueue = new ArrayDeque<>();
   private final Object clipLock = new Object();
   private volatile long pilotSignSeq;
   private volatile long pilotBookSeq;
   private volatile InteractionHand pilotBookHand = InteractionHand.MAIN_HAND;
   private volatile int digHasteAmp = -1;
   private volatile long digHasteUntil;
   private volatile int digConduitAmp = -1;
   private volatile long digConduitUntil;
   private volatile int digFatigueAmp = -1;
   private volatile long digFatigueUntil;
   private final AtomicReference<Vec3> pilotImpulse = new AtomicReference<>();
   private volatile double kbX;
   private volatile double kbZ;
   private volatile int kbTicks;
   private static final long FALL_MODE_CAP_MS = 15000L;
   private volatile boolean fallMode;
   private volatile long fallModeUntil;

   MultiSession(
      long generation,
      MultiProfile.SessionSpec spec,
      RiptideProxy proxy,
      String proxyName,
      MultiPacketPolicy policy,
      MultiProfile.LoginMode loginMode,
      String profileName,
      Map<String, String> formValues,
      final MultiSession.Sink sink,
      Executor worker,
      MultiViaCompat.Target viaTarget
   ) {
      this.generation = generation;
      this.spec = spec;
      this.proxy = proxy;
      this.proxyName = proxyName != null && !proxyName.isBlank() ? proxyName : "Proxy Off";
      this.policy = new MultiPacketPolicy(policy);
      this.loginMode = loginMode == null ? MultiProfile.LoginMode.Auto : loginMode;
      this.profileName = profileName == null ? "" : profileName;
      this.formValues = formValues == null ? Map.of() : Map.copyOf(formValues);
      this.sink = sink;
      this.worker = worker;
      this.viaTarget = viaTarget;
      this.registries = ClientRegistryLayer.createRegistryAccess().compositeAccess();
      this.captcha = new CaptchaSolver(new CaptchaSolver.Host() {
         {
            Objects.requireNonNull(MultiSession.this);
         }

         @Override
         public void sendCaptchaChat(String message) {
            MultiSession.this.sendChat(message);
         }

         @Override
         public void sendCaptchaCommand(String command) {
            MultiSession.this.sendCommand(command);
         }

         @Override
         public void captchaNote(String note) {
            sink.note(MultiSession.this, note);
         }
      }, worker);
   }

   long generation() {
      return this.generation;
   }

   String accountId() {
      return this.spec.accountId();
   }

   boolean connected() {
      Connection current = this.connection;
      return current != null && current.isConnected() && !this.closed.get();
   }

   boolean ready() {
      return this.status.get() == MultiSession.Status.READY && this.connected() && this.hasPosition;
   }

   MultiSession.Status statusValue() {
      return this.status.get();
   }

   String detailText() {
      return this.detail;
   }

   public MultiSession.Snapshot snapshot() {
      MultiIdentityResolver.Identity resolved = this.identity;
      String name = resolved == null ? this.spec.accountId() : resolved.user().getName();
      MultiSession.Status current = this.status.get();
      PositionMoveRotation p = this.position;
      Vec3 pos = this.piloted && System.currentTimeMillis() - this.pilotObservedAt < 1000L ? this.pilotObservedPosition : p.position();
      boolean isConnected = this.connected();
      String publishedScreen = this.menuPhase == MultiSession.MenuPhase.CONTAINER && !this.containerDismissed ? this.openScreenTitle : "";
      String publishedProtocol = this.viaTarget != null && this.viaTarget.present() ? this.viaTarget.label() : "Native 26.2";
      boolean publishedReady = current == MultiSession.Status.READY && isConnected && this.hasPosition;
      boolean customMenuOpen = this.customMenus.current() != null;
      int publishedHotbar = this.selectedHotbar + 1;
      MultiSession.MacroQueue queue = this.macroQueue;
      MultiSession.Snapshot cached = this.publishedSnapshot;
      if (cached != null
         && Objects.equals(cached.accountId(), this.spec.accountId())
         && Objects.equals(cached.accountName(), name)
         && Objects.equals(cached.proxyName(), this.proxyName)
         && Objects.equals(cached.protocol(), publishedProtocol)
         && cached.status() == current
         && Objects.equals(cached.detail(), this.detail)
         && cached.ping() == this.ping
         && cached.connected() == isConnected
         && cached.ready() == publishedReady
         && cached.lastInboundAt() == this.lastInboundAt
         && Objects.equals(cached.openScreen(), publishedScreen)
         && cached.customMenuOpen() == customMenuOpen
         && Objects.equals(cached.heldItem(), this.heldItemName)
         && cached.hotbarSlot() == publishedHotbar
         && Objects.equals(cached.dimension(), this.dimension)
         && cached.hasPosition() == this.hasPosition
         && Double.doubleToLongBits(cached.x()) == Double.doubleToLongBits(pos.x)
         && Double.doubleToLongBits(cached.y()) == Double.doubleToLongBits(pos.y)
         && Double.doubleToLongBits(cached.z()) == Double.doubleToLongBits(pos.z)
         && Float.floatToIntBits(cached.health()) == Float.floatToIntBits(this.health)
         && Float.floatToIntBits(cached.maxHealth()) == Float.floatToIntBits(this.maxHealth)
         && cached.food() == this.food
         && cached.menuRevision() == this.menuRevision
         && Objects.equals(cached.macroStatus(), this.macroStatus)
         && Objects.equals(cached.macroProgress(), this.macroProgress)
         && Objects.equals(cached.macroQueue(), queue)) {
         return cached;
      } else {
         MultiSession.Snapshot next = new MultiSession.Snapshot(
            this.spec.accountId(),
            name,
            this.proxyName,
            publishedProtocol,
            current,
            this.detail,
            this.ping,
            isConnected,
            publishedReady,
            this.lastInboundAt,
            publishedScreen,
            customMenuOpen,
            this.heldItemName,
            publishedHotbar,
            this.dimension,
            this.hasPosition,
            pos.x,
            pos.y,
            pos.z,
            this.health,
            this.maxHealth,
            this.food,
            this.menuRevision,
            this.macroStatus,
            this.macroProgress,
            queue
         );
         this.publishedSnapshot = next;
         return next;
      }
   }

   void start(InetSocketAddress address, String handshakeHost, int handshakePort) {
      if (!this.closed.get()) {
         this.setStatus(MultiSession.Status.AUTHENTICATING, "Authenticating");
         CompletableFuture.<MultiIdentityResolver.Identity>supplyAsync(() -> MultiIdentityResolver.resolve(this.spec.accountId()), this.worker)
            .whenComplete((resolved, error) -> {
               if (!this.closed.get()) {
                  if (error != null) {
                     this.fail(shortError(error));
                  } else {
                     this.identity = resolved;
                     String identityError = this.sink.identityRejection(this, resolved.user().getProfileId());
                     if (identityError != null && !identityError.isBlank()) {
                        this.fail(identityError);
                     } else {
                        this.connect(address, handshakeHost, handshakePort);
                     }
                  }
               }
            });
      }
   }

   private void connect(InetSocketAddress address, String handshakeHost, int handshakePort) {
      if (!this.closed.get()) {
         this.connectEpoch++;
         this.serverGameProfile = null;
         this.lastWireHotbar = -1;
         this.lastConnectHost = handshakeHost == null ? "" : handshakeHost;
         this.lastConnectPort = handshakePort;
         this.resetChatState();
         this.registryData = new RegistryDataCollector();
         this.serverAddress = handshakeHost != null && !handshakeHost.isBlank()
            ? (handshakePort == 25565 ? handshakeHost : handshakeHost + ":" + handshakePort)
            : "";
         boolean transferIntent = this.nextConnectTransferring;
         this.nextConnectTransferring = false;
         this.setStatus(MultiSession.Status.CONNECTING, transferIntent ? "Transferring" : "Connecting");
         this.connectStartedAt = System.currentTimeMillis();
         this.autoLogin.configure(AutoLoginConfig.multiDefaults());
         this.autoLogin.reset(this.connectStartedAt);
         Connection created = new Connection(PacketFlow.CLIENTBOUND);
         this.connection = created;
         MultiConnectionContext.register(created, this.proxy);
         MultiViaCompat.applyTarget(created, this.viaTarget);
         MultiConnectionContext.ProxySpec connecting = MultiConnectionContext.beginConnect(MultiConnectionContext.ProxySpec.copyOf(this.proxy));

         ChannelFuture future;
         try {
            future = Connection.connect(address, EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()), created);
         } catch (RuntimeException var9) {
            MultiConnectionContext.endConnect(connecting);
            this.fail(shortError(var9));
            return;
         }

         MultiConnectionContext.endConnect(connecting);
         future.addListener(
            done -> {
               if (this.closed.get()) {
                  created.disconnect(Component.literal("Cancelled"));
                  created.handleDisconnection();
                  MultiConnectionContext.remove(created);
               } else if (!done.isSuccess()) {
                  this.fail(shortError(done.cause()));
               } else {
                  this.setStatus(MultiSession.Status.LOGIN, "Logging in");
                  created.initiateServerboundPlayConnection(
                     handshakeHost,
                     handshakePort,
                     LoginProtocols.SERVERBOUND,
                     LoginProtocols.CLIENTBOUND,
                     this.listener(MultiSession.Phase.LOGIN, ClientLoginPacketListener.class),
                     transferIntent
                  );
                  created.send(new ServerboundHelloPacket(this.identity.user().getName(), this.identity.user().getProfileId()));
               }
            }
         );
      }
   }

   private <T> T listener(MultiSession.Phase phase, Class<T> listenerType) {
      int epoch = this.connectEpoch;
      InvocationHandler handler = (proxyObject, method, args) -> this.invokeListener(phase, epoch, proxyObject, method, args);
      return (T)Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, handler);
   }

   private Object invokeListener(MultiSession.Phase phase, int epoch, Object proxyObject, Method method, Object[] args) {
      String var6 = method.getName();

      return switch (var6) {
         case "protocol" -> phase.protocol;
         case "flow" -> PacketFlow.CLIENTBOUND;
         case "isAcceptingMessages", "shouldHandleMessage" -> epoch == this.connectEpoch
            && !this.closed.get()
            && this.connection != null
            && this.connection.isConnected();
         case "onDisconnect" -> {
            DisconnectionDetails details = args != null && args.length > 0 && args[0] instanceof DisconnectionDetails value ? value : null;
            this.handleDisconnected(details, epoch);
            yield null;
         }
         case "createDisconnectionInfo" -> {
            Component reason = (Component)(args != null && args.length > 0 && args[0] instanceof Component value
               ? value
               : Component.literal("Connection error"));
            yield new DisconnectionDetails(reason);
         }
         case "onPacketError" -> {
            if (epoch == this.connectEpoch) {
               Throwable error = args != null && args.length > 1 && args[1] instanceof Throwable value ? value : null;
               this.fail("Packet error: " + shortError(error));
            }

            yield null;
         }
         case "toString" -> "Multi" + phase + "Listener[" + this.spec.accountId() + "]";
         case "hashCode" -> System.identityHashCode(proxyObject);
         case "equals" -> args != null && args.length == 1 && proxyObject == args[0];
         default -> {
            if (epoch == this.connectEpoch && args != null && args.length > 0 && args[0] instanceof Packet<?> packet) {
               this.handlePacket(phase, packet);
            }

            yield defaultValue(method.getReturnType());
         }
      };
   }

   private void handlePacket(MultiSession.Phase phase, Packet<?> packet) {
      this.handlePacket(phase, packet, 0);
   }

   private void handlePacket(MultiSession.Phase phase, Packet<?> packet, int bundleDepth) {
      if (!this.closed.get() && packet != null) {
         if (phase == MultiSession.Phase.PLAY && packet instanceof ClientboundBundlePacket bundle) {
            if (bundleDepth >= 8) {
               this.fail("Protocol error: nested packet bundle limit exceeded");
            } else {
               int count = 0;

               for (Packet<? super ClientGamePacketListener> subPacket : bundle.subPackets()) {
                  if (++count > 4096) {
                     this.fail("Protocol error: packet bundle size limit exceeded");
                     return;
                  }

                  this.handlePacket(phase, subPacket, bundleDepth + 1);
                  if (this.closed.get()) {
                     return;
                  }
               }
            }
         } else {
            if (phase == MultiSession.Phase.PLAY) {
               this.lastInboundAt = System.currentTimeMillis();
            }

            CustomMenuSnapshot beforeMenu = this.customMenus.current();
            boolean customMenuPacket = this.customMenus.accept(packet, phase.name());
            if (customMenuPacket) {
               this.updateCustomMenuTitle(beforeMenu, this.customMenus.current());
               this.maybeAlertMissingPassword(this.customMenus.current());
            }

            boolean critical = customMenuPacket || isCriticalInbound(packet);
            boolean allowed = this.policy.allows(MultiPacketPolicy.Direction.S2C, packet.getClass().getName(), critical, false);
            if (allowed || packet instanceof ClientboundPlayerChatPacket) {
               try {
                  this.handleCommon(packet);
                  switch (phase) {
                     case LOGIN:
                        this.handleLogin(packet);
                        break;
                     case CONFIGURATION:
                        this.handleConfiguration(packet);
                        break;
                     case PLAY:
                        this.handlePlay(packet, allowed);
                  }
               } catch (Throwable var9) {
                  this.fail("Protocol error: " + shortError(var9));
               }
            }
         }
      }
   }

   private void handleLogin(Packet<?> packet) throws Exception {
      if (packet instanceof ClientboundHelloPacket hello) {
         this.authorize(hello);
      } else if (packet instanceof ClientboundLoginFinishedPacket finished) {
         this.sessionRefreshAttempted = false;
         this.setStatus(MultiSession.Status.CONFIGURING, "Configuring");
         this.connection
            .setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, this.listener(MultiSession.Phase.CONFIGURATION, ClientConfigurationPacketListener.class));
         this.send(ServerboundLoginAcknowledgedPacket.INSTANCE, true, false);
         this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
         this.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")), true, false);
         this.clientInformation = Minecraft.getInstance().options.buildPlayerInformation();
         this.send(new ServerboundClientInformationPacket(this.clientInformation), true, false);
         this.playerNames.put(finished.gameProfile().id(), finished.gameProfile().name());
         this.serverGameProfile = finished.gameProfile();
         this.serverAssignedUuid = finished.gameProfile().id();
      } else if (packet instanceof ClientboundLoginDisconnectPacket disconnected) {
         this.connection.disconnect(disconnected.reason());
      } else if (packet instanceof ClientboundLoginCompressionPacket compression) {
         if (!this.connection.isMemoryConnection()) {
            this.connection.setupCompression(compression.getCompressionThreshold(), false);
         }
      } else if (packet instanceof ClientboundCustomQueryPacket query) {
         this.send(new ServerboundCustomQueryAnswerPacket(query.transactionId(), null), true, false);
      }
   }

   private void authorize(ClientboundHelloPacket hello) throws Exception {
      SecretKey secretKey = Crypt.generateSecretKey();
      PublicKey publicKey = hello.getPublicKey();
      String digest = new BigInteger(Crypt.digestData(hello.getServerId(), publicKey, secretKey)).toString(16);
      Cipher decryptCipher = Crypt.getCipher(2, secretKey);
      Cipher encryptCipher = Crypt.getCipher(1, secretKey);
      ServerboundKeyPacket keyPacket = new ServerboundKeyPacket(secretKey, publicKey, hello.getChallenge());
      Runnable enableEncryption = () -> this.connection
         .send(keyPacket, PacketSendListener.thenRun(() -> this.connection.setEncryptionKey(decryptCipher, encryptCipher)));
      if (!hello.shouldAuthenticate()) {
         enableEncryption.run();
      } else {
         AtomicBoolean joinCompleted = new AtomicBoolean();
         CompletableFuture.runAsync(() -> {
            try {
               this.identity.sessionService().joinServer(this.identity.user().getProfileId(), this.identity.user().getAccessToken(), digest);
               joinCompleted.set(true);
               if (!this.closed.get()) {
                  enableEncryption.run();
               }
            } catch (AuthenticationException var5x) {
               joinCompleted.set(true);
               if (!this.closed.get()) {
                  this.onSessionAuthFailed(var5x);
               }
            } catch (Throwable var6x) {
               joinCompleted.set(true);
               if (!this.closed.get()) {
                  this.fail("Session join failed: " + shortError(var6x));
               }
            }
         }, this.worker).orTimeout(15L, TimeUnit.SECONDS).whenComplete((ignored, timeout) -> {
            if (timeout != null && !joinCompleted.get() && !this.closed.get()) {
               this.fail("Session join timed out (Mojang session servers unreachable or rate-limiting)");
            }
         });
      }
   }

   private void onSessionAuthFailed(AuthenticationException error) {
      boolean refreshable = !this.sessionRefreshAttempted
         && !this.closed.get()
         && !this.lastConnectHost.isBlank()
         && this.identity != null
         && this.identity.type() == RiptideAccountType.Microsoft;
      if (!refreshable) {
         this.fail("Session authentication failed: " + shortError(error));
      } else {
         this.sessionRefreshAttempted = true;
         RiptideAccountManager.get().invalidateSessionToken(this.spec.accountId());
         this.beginTransfer(this.lastConnectHost, this.lastConnectPort, false, 1000L, true);
      }
   }

   private void handleConfiguration(Packet<?> packet) {
      if (packet instanceof ClientboundRegistryDataPacket registry) {
         this.registryData.appendContents(registry.registry(), registry.entries());
      } else if (packet instanceof ClientboundUpdateTagsPacket tags) {
         this.registryData.appendTags(tags.getTags());
      } else if (packet instanceof ClientboundUpdateEnabledFeaturesPacket features) {
         this.enabledFeatures = FeatureFlags.REGISTRY.fromNames(features.features());
      } else if (packet instanceof ClientboundSelectKnownPacks) {
         this.send(new ServerboundSelectKnownPacks(List.of()), true, false);
      } else if (packet instanceof ClientboundResetChatPacket) {
         this.resetChatState();
      } else if (packet instanceof ClientboundCodeOfConductPacket) {
         this.send(ServerboundAcceptCodeOfConductPacket.INSTANCE, true, false);
      } else if (packet instanceof ClientboundFinishConfigurationPacket) {
         this.finishConfiguration();
      }
   }

   private void finishConfiguration() {
      CompletableFuture.runAsync(
         () -> {
            try {
               if (this.closed.get()) {
                  return;
               }

               Frozen base = ClientRegistryLayer.createRegistryAccess().compositeAccess();
               Frozen collected = this.registryData.collectGameRegistries(ResourceProvider.EMPTY, base, false);
               if (this.closed.get()) {
                  return;
               }

               this.registries = collected;
               this.connection
                  .setupInboundProtocol(
                     GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(collected)),
                     this.listener(MultiSession.Phase.PLAY, ClientGamePacketListener.class)
                  );
               this.send(ServerboundFinishConfigurationPacket.INSTANCE, true, false);
               this.connection
                  .setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(collected), (Context)() -> true));
               this.setStatus(MultiSession.Status.JOINED, "Joining");
            } catch (Throwable var3) {
               this.fail("Registry setup failed: " + shortError(var3));
            }
         },
         this.worker
      );
   }

   private void handlePlay(Packet<?> packet, boolean allowedForDisplay) {
      if (this.packetCaptureArmed) {
         this.recordPacket(false, packet);
      }

      if (this.captureWorld) {
         this.worldCapture.capture(packet);
      }

      if (packet instanceof ClientboundLoginPacket login) {
         this.serverEnforcesSecureChat = login.enforcesSecureChat();
         this.playerEntityId = login.playerId();
         this.lastWireHotbar = -1;
         this.dimension = dimensionOf(login.commonPlayerSpawnInfo());
         this.gameModeId = gameTypeIdOf(login.commonPlayerSpawnInfo());
         this.entities.clear();
         this.invalidateMenu(true, true);
         long nowJoin = System.currentTimeMillis();
         this.resetPlayerLoadHandshake(nowJoin);
         this.captcha.reset(nowJoin);
         this.captchaAnnouncedUntil = 0L;
         this.grounded = true;
         this.primeFallTick = false;
         this.motionY = 0.0;
         this.inVehicle = false;
         this.vehicleId = -1;
         this.postVehicleFall = false;
         this.blockUpdates.clear();
         this.outboundTruth.reset(this.position.position(), nowJoin);
         if (this.serverEnforcesSecureChat) {
            this.prepareChatSession();
         }
      } else if (packet instanceof ClientboundGameEventPacket gameEvent) {
         if (gameEvent.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            this.gameModeId = (int)gameEvent.getParam();
         } else if (gameEvent.getEvent() == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START) {
            this.levelChunksLoadStarted = true;
            this.updatePlayerLoadReadiness(System.currentTimeMillis());
         }
      } else if (packet instanceof ClientboundChunkBatchStartPacket) {
         this.chunkBatchSizeCalculator.onBatchStart();
      } else if (packet instanceof ClientboundChunkBatchFinishedPacket batchFinished) {
         this.chunkBatchSizeCalculator.onBatchFinished(batchFinished.batchSize());
         this.send(new ServerboundChunkBatchReceivedPacket(this.chunkBatchSizeCalculator.getDesiredChunksPerTick()), true, false);
         this.playerLoadChunkBatchFinished = true;
         this.updatePlayerLoadReadiness(System.currentTimeMillis());
      } else if (packet instanceof ClientboundOpenSignEditorPacket sign) {
         this.signEditorPos = sign.getPos();
         this.signEditorFront = sign.isFrontText();
         this.signEditorOpen = true;
         if (this.piloted) {
            this.pilotSignSeq++;
         } else {
            this.openScreenSeq++;
         }
      } else if (packet instanceof ClientboundOpenBookPacket book) {
         if (this.piloted) {
            this.pilotBookHand = book.getHand();
            this.pilotBookSeq++;
         }
      } else if (packet instanceof ClientboundSoundPacket sound) {
         if (this.soundCaptureArmed) {
            try {
               this.recordSound(((SoundEvent)sound.getSound().value()).location().toString(), sound.getX(), sound.getY(), sound.getZ());
            } catch (RuntimeException var78) {
            }
         }
      } else if (packet instanceof ClientboundCooldownPacket cd) {
         String group = cd.cooldownGroup().toString();
         if (cd.duration() > 0) {
            if (this.cooldownExpiry.size() < 256 || this.cooldownExpiry.containsKey(group)) {
               this.cooldownExpiry.put(group, System.currentTimeMillis() + cd.duration() * 50L);
            }
         } else {
            this.cooldownExpiry.remove(group);
         }
      } else if (packet instanceof ClientboundBlockUpdatePacket bu) {
         this.trackBlock(bu.getPos(), bu.getBlockState());
      } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sbu) {
         sbu.runUpdates(this::trackBlock);
      } else if (packet instanceof ClientboundRespawnPacket respawn) {
         this.resetPlayerLoadHandshake(System.currentTimeMillis());
         this.dimension = dimensionOf(respawn.commonPlayerSpawnInfo());
         this.gameModeId = gameTypeIdOf(respawn.commonPlayerSpawnInfo());
         this.hasPosition = false;
         this.invalidateMenu(true, true);
         this.entities.clear();
         this.blockUpdates.clear();
         this.sprintAnnounced = false;
         this.signEditorOpen = false;
         this.digHasteUntil = 0L;
         this.digConduitUntil = 0L;
         this.digFatigueUntil = 0L;
         synchronized (this.positionLock) {
            this.fallMode = false;
            this.kbTicks = 0;
            this.motionY = 0.0;
            this.grounded = true;
            this.primeFallTick = false;
            this.walkUntil = 0L;
         }

         this.savedMenu = null;
         this.xCarryJob = null;
         this.xCarryForced = false;
         this.xCarryActive = false;
      } else if (packet instanceof ClientboundSetHealthPacket healthPacket) {
         this.health = healthPacket.getHealth();
         this.food = healthPacket.getFood();
      } else if (packet instanceof ClientboundUpdateMobEffectPacket effectUp) {
         if (effectUp.getEntityId() == this.playerEntityId) {
            long until = effectUp.getEffectDurationTicks() < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + effectUp.getEffectDurationTicks() * 50L;
            MobEffect effect = (MobEffect)effectUp.getEffect().value();
            if (effect == MobEffects.HASTE.value()) {
               this.digHasteAmp = effectUp.getEffectAmplifier();
               this.digHasteUntil = until;
            } else if (effect == MobEffects.CONDUIT_POWER.value()) {
               this.digConduitAmp = effectUp.getEffectAmplifier();
               this.digConduitUntil = until;
            } else if (effect == MobEffects.MINING_FATIGUE.value()) {
               this.digFatigueAmp = effectUp.getEffectAmplifier();
               this.digFatigueUntil = until;
            }
         }
      } else if (packet instanceof ClientboundRemoveMobEffectPacket effectRm) {
         if (effectRm.entityId() == this.playerEntityId) {
            MobEffect effect = (MobEffect)effectRm.effect().value();
            if (effect == MobEffects.HASTE.value()) {
               this.digHasteUntil = 0L;
            } else if (effect == MobEffects.CONDUIT_POWER.value()) {
               this.digConduitUntil = 0L;
            } else if (effect == MobEffects.MINING_FATIGUE.value()) {
               this.digFatigueUntil = 0L;
            }
         }
      } else if (packet instanceof ClientboundUpdateAttributesPacket attributes) {
         this.applyAttributes(attributes);
      } else if (packet instanceof ClientboundPlayerPositionPacket move) {
         long correctionAt = System.currentTimeMillis();
         Vec3 beforeCorrection = this.position.position();
         boolean landed;
         boolean walking;
         synchronized (this.positionLock) {
            walking = correctionAt < this.walkUntil;
            boolean wasFalling = this.fallMode;
            this.position = PositionMoveRotation.calculateAbsolute(this.position, move.change(), move.relatives());
            this.motionY = 0.0;
            landed = wasFalling && this.position.position().distanceToSqr(beforeCorrection) < 2.25;
            if (walking) {
               this.grounded = true;
               this.primeFallTick = false;
               this.fallMode = false;
            } else if (landed) {
               this.grounded = true;
               this.primeFallTick = false;
               this.fallMode = false;
            } else if (this.readyOnce && !this.policy.gravity()) {
               this.grounded = true;
               this.primeFallTick = false;
               this.fallMode = false;
            } else {
               this.grounded = false;
               this.primeFallTick = true;
               this.fallModeUntil = correctionAt + 15000L;
               this.fallMode = true;
            }
         }

         this.hasPosition = true;
         this.cancelClip();
         this.outboundTruth.reset(this.position.position(), correctionAt);
         PacketTeleportController.onPovCorrection(this, this.position.position());
         this.teleportSeq++;
         if (walking) {
            this.walkGrounded = true;
            this.walkProbeAt = correctionAt + 500L;
            this.trackWalkCorrection(correctionAt);
         } else if (!landed && (!this.readyOnce || this.policy.gravity())) {
            this.moveActiveUntil = Math.max(this.moveActiveUntil, correctionAt + 5000L);
         }

         this.send(new ServerboundAcceptTeleportationPacket(move.id()), true, false);
         this.sendPosition(true);
         this.playerLoadPositionAccepted = true;
         if (!this.playerLoadedSent.get() && this.playerLoadHeadlessFallbackAt == Long.MAX_VALUE) {
            this.playerLoadHeadlessFallbackAt = correctionAt + 2000L;
         }

         this.updatePlayerLoadReadiness(correctionAt);
         this.setStatus(MultiSession.Status.READY, "Ready");
         this.verificationRetries = 0;
         if (!this.readyOnce) {
            this.readyOnce = true;
            this.moveActiveUntil = System.currentTimeMillis() + 5000L;
            this.timerBudget.reset(System.nanoTime());
         }
      } else if (packet instanceof ClientboundPlayerRotationPacket rotate) {
         float pitch;
         float yaw;
         synchronized (this.positionLock) {
            yaw = rotate.relativeY() ? this.position.yRot() + rotate.yRot() : rotate.yRot();
            pitch = rotate.relativeX() ? this.position.xRot() + rotate.xRot() : rotate.xRot();
            this.position = this.position.withRotation(yaw, pitch);
         }

         this.send(new Rot(yaw, pitch, false, false), true, true);
      } else if (packet instanceof ClientboundSystemChatPacket system) {
         this.captureChat(system.content());
         this.noteLoginPrompt(system.content());
         long now = System.currentTimeMillis();
         this.noteCaptchaAnnounce(system.content(), now);
         if (this.captchaWindowOpen(now)) {
            this.captcha.onChat(system.content(), now);
         }

         if (allowedForDisplay) {
            this.sink.chat(this, system.content());
         }
      } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
         this.captureChat(disguised.message());
         this.noteLoginPrompt(disguised.message());
         long nowx = System.currentTimeMillis();
         this.noteCaptchaAnnounce(disguised.message(), nowx);
         if (this.captchaWindowOpen(nowx)) {
            this.captcha.onChat(disguised.message(), nowx);
         }

         if (allowedForDisplay) {
            this.sink.chat(this, disguised.message());
         }
      } else if (packet instanceof ClientboundMapItemDataPacket mapData) {
         long nowxx = System.currentTimeMillis();
         if (this.captchaWindowOpen(nowxx)) {
            mapData.colorPatch().ifPresent(patch -> {
               if (patch.width() == 128 && patch.height() == 128 && patch.startX() == 0 && patch.startY() == 0) {
                  this.captcha.onMapData(patch.mapColors(), now);
               }
            });
         }
      } else if (packet instanceof ClientboundSetTitleTextPacket title) {
         long nowxx = System.currentTimeMillis();
         this.noteCaptchaAnnounce(title.text(), nowxx);
         if (this.captchaWindowOpen(nowxx)) {
            this.captcha.onTitle(title.text(), nowxx);
         }
      } else if (packet instanceof ClientboundSetSubtitleTextPacket subtitle) {
         long nowxx = System.currentTimeMillis();
         this.noteCaptchaAnnounce(subtitle.text(), nowxx);
         if (this.captchaWindowOpen(nowxx)) {
            this.captcha.onTitle(subtitle.text(), nowxx);
         }
      } else if (packet instanceof ClientboundPlayerChatPacket playerChat) {
         this.handlePlayerChat(playerChat, allowedForDisplay);
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket playerInfo) {
         boolean addsPlayers = playerInfo.actions().contains(Action.ADD_PLAYER);
         boolean updatesListed = playerInfo.actions().contains(Action.UPDATE_LISTED);

         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : playerInfo.entries()) {
            if (entry.profile() != null) {
               this.playerNames.put(entry.profileId(), entry.profile().name());
            }

            if (entry.profile() != null
               && this.identity != null
               && (
                  entry.profileId().equals(this.serverAssignedUuid)
                     || entry.profileId().equals(this.identity.user().getProfileId())
                     || entry.profile().name().equalsIgnoreCase(this.identity.user().getName())
               )) {
               this.serverGameProfile = entry.profile();
            }

            if (updatesListed) {
               if (entry.listed()) {
                  this.addListed(entry.profileId());
               } else {
                  this.listedPlayers.remove(entry.profileId());
               }
            } else if (addsPlayers) {
               this.addListed(entry.profileId());
            }

            if (this.identity != null && entry.profileId().equals(this.identity.user().getProfileId())) {
               int updatedPing = Math.max(0, entry.latency());
               if (this.ping != updatedPing) {
                  this.ping = updatedPing;
               }
            } else if (addsPlayers
               && this.identity != null
               && entry.profile() != null
               && entry.profile().name().equalsIgnoreCase(this.identity.user().getName())
               && !entry.profileId().equals(this.serverAssignedUuid)) {
               this.serverAssignedUuid = entry.profileId();
               this.rekeyChatEncoder();
            }
         }

         while (this.playerNames.size() > 2048) {
            UUID oldest = this.playerNames.keySet().iterator().next();
            this.playerNames.remove(oldest);
            this.listedPlayers.remove(oldest);
         }
      } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
         for (UUID id : remove.profileIds()) {
            this.playerNames.remove(id);
            this.listedPlayers.remove(id);
         }
      } else if (packet instanceof ClientboundSetPlayerTeamPacket team) {
         this.trackTeam(team);
      } else if (packet instanceof ClientboundSetObjectivePacket objective) {
         this.trackObjective(objective);
      } else if (packet instanceof ClientboundSetDisplayObjectivePacket displayObjective) {
         this.trackDisplayObjective(displayObjective);
      } else if (packet instanceof ClientboundSetScorePacket score) {
         this.trackScore(score);
      } else if (packet instanceof ClientboundResetScorePacket resetScore) {
         this.resetScore(resetScore);
      } else if (packet instanceof ClientboundCommandsPacket commandPacket) {
         long nowMs = System.currentTimeMillis();
         if (!this.hasCommandTree || nowMs - this.lastCommandsBuildAt >= 750L) {
            this.lastCommandsBuildAt = nowMs;
            this.commands = new CommandDispatcher(
               commandPacket.getRoot(CommandBuildContext.simple(this.registries, this.enabledFeatures), COMMAND_NODE_BUILDER)
            );
            this.hasCommandTree = true;
         }
      } else if (packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
         MultiSession.Suggest reply = new MultiSession.Suggest(
            suggestions.id(),
            suggestions.start(),
            suggestions.length(),
            suggestions.suggestions().stream().<String>map(net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry::text).toList()
         );
         synchronized (this.suggestionLock) {
            this.suggestionReplies.put(reply.id(), reply);

            while (this.suggestionReplies.size() > 64) {
               this.suggestionReplies.remove(this.suggestionReplies.keySet().iterator().next());
            }
         }
      } else if (packet instanceof ClientboundOpenScreenPacket open) {
         this.beginServerMenu(open);
      } else if (packet instanceof ClientboundMountScreenOpenPacket mount) {
         this.beginMountMenu(mount);
      } else if (packet instanceof ClientboundContainerSetContentPacket content) {
         this.updateSavedMenuContent(content.containerId(), content.stateId(), content.items(), content.carriedItem());
         if (content.containerId() == 0) {
            this.inventoryStateId = content.stateId();
         }

         if (content.containerId() == this.currentContainerId()) {
            this.containerStateId = content.stateId();
            synchronized (this.menuLock) {
               this.menuSlots.clear();

               for (ItemStack stack : content.items()) {
                  this.menuSlots.add(nonNull(stack).copy());
               }

               this.carried = nonNull(content.carriedItem()).copy();
               if (content.containerId() == 0) {
                  this.inventorySynchronized = true;
                  this.menuPhase = MultiSession.MenuPhase.INVENTORY;
               } else {
                  this.menuPhase = MultiSession.MenuPhase.CONTAINER;
               }

               this.menuInteractive = true;
               this.menuRevision++;
            }

            this.syncPlayerInvFromMenu();
            this.recomputeHeldItem();
            if (content.containerId() == 0) {
               this.refreshXCarryActive();
            }

            this.noteAuthoritativeMenuUpdate(content.containerId());
         }
      } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
         this.updateSavedMenuCursor(this.currentContainerId(), cursor.contents());
         synchronized (this.menuLock) {
            this.carried = nonNull(cursor.contents()).copy();
            this.menuRevision++;
         }

         this.refreshXCarryActive();
         this.noteAuthoritativeMenuUpdate(this.currentContainerId());
      } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
         this.updateSavedMenuSlot(slot.getContainerId(), slot.getStateId(), slot.getSlot(), slot.getItem());
         if (slot.getContainerId() == 0) {
            this.inventoryStateId = slot.getStateId();
         }

         if (slot.getContainerId() == this.currentContainerId()) {
            this.containerStateId = slot.getStateId();
            if (this.openContainerId < 0) {
               int h = slot.getSlot();
               synchronized (this.menuLock) {
                  if (h >= 0 && h < this.playerInv.size()) {
                     ItemStack authoritative = nonNull(slot.getItem()).copy();
                     this.playerInv.set(h, authoritative);

                     while (this.menuSlots.size() < this.playerInv.size()) {
                        this.menuSlots.add(ItemStack.EMPTY);
                     }

                     this.menuSlots.set(h, authoritative.copy());
                  }

                  this.menuRevision++;
               }
            } else {
               this.setMenuSlot(slot.getSlot(), slot.getItem());
               this.syncPlayerInvFromMenu();
            }

            this.recomputeHeldItem();
            if (slot.getContainerId() == 0) {
               this.refreshXCarryActive();
            }

            this.noteAuthoritativeMenuUpdate(slot.getContainerId());
         }
      } else if (packet instanceof ClientboundContainerSetDataPacket data) {
         if (data.getContainerId() == this.currentContainerId()) {
            int id = data.getId();
            synchronized (this.menuLock) {
               if (id >= 0 && id < this.menuData.length) {
                  this.menuData[id] = data.getValue();
                  this.menuDataLen = Math.max(this.menuDataLen, id + 1);
               }

               this.menuRevision++;
            }
         }
      } else if (packet instanceof ClientboundMerchantOffersPacket offers) {
         if (offers.getContainerId() == this.currentContainerId()) {
            synchronized (this.menuLock) {
               this.merchantOffers = offers.getOffers();
               this.villagerLevel = offers.getVillagerLevel();
               this.villagerXp = offers.getVillagerXp();
               this.villagerShowProgress = offers.showProgress();
               this.menuRevision++;
            }
         }
      } else if (packet instanceof ClientboundContainerClosePacket close) {
         if (close.getContainerId() == this.openContainerId) {
            this.syncPlayerInvFromMenu();
            this.invalidateMenu(false, true);
         }
      } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
         int slot = held.slot();
         if (slot >= 0 && slot <= 8) {
            this.selectedHotbar = slot;
            this.recomputeHeldItem();
            if (this.send(new ServerboundSetCarriedItemPacket(slot), false, false)) {
               this.lastWireHotbar = slot;
            }
         }
      } else if (packet instanceof ClientboundAnimatePacket animate) {
         if (animate.getId() == this.playerEntityId && animate.getAction() == 0) {
            this.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
         }
      } else if (packet instanceof ClientboundSetEntityMotionPacket motion) {
         this.entities.motion(motion.id(), motion.movement());
         if (motion.id() == this.playerEntityId) {
            if (this.piloted && !this.macroOwnsPilot()) {
               this.pilotImpulse.set(motion.movement());
            } else if (!this.inVehicle && !this.postVehicleFall) {
               long nowKb = System.currentTimeMillis();
               this.kbX = motion.movement().x;
               this.kbZ = motion.movement().z;
               this.kbTicks = 8;
               synchronized (this.positionLock) {
                  this.motionY = motion.movement().y;
               }

               this.fallModeUntil = nowKb + 15000L;
               if (nowKb < this.walkUntil) {
                  this.walkGrounded = false;
                  this.fallMode = true;
               } else {
                  this.fallMode = true;
                  this.grounded = false;
                  this.primeFallTick = false;
               }

               this.moveActiveUntil = Math.max(this.moveActiveUntil, nowKb + 800L);
            }
         }
      } else if (packet instanceof ClientboundSetPassengersPacket passengers) {
         this.handleSetPassengers(passengers);
      } else if (packet instanceof ClientboundSetPlayerInventoryPacket inv) {
         int handler = inventoryIndexToHandler(inv.slot());
         if (handler >= 0) {
            synchronized (this.menuLock) {
               if (handler < this.playerInv.size()) {
                  ItemStack authoritative = inv.contents() == null ? ItemStack.EMPTY : inv.contents().copy();
                  this.playerInv.set(handler, authoritative);
                  if (this.openContainerId >= 0) {
                     int base = this.menuSlots.size() - 36;
                     int live = handler >= 9 && handler <= 35 ? base + (handler - 9) : (handler >= 36 && handler <= 44 ? base + 27 + (handler - 36) : -1);
                     if (live >= 0 && live < this.menuSlots.size()) {
                        this.menuSlots.set(live, authoritative.copy());
                     }
                  }
               }

               this.menuRevision++;
            }

            if (this.openContainerId < 0) {
               this.setMenuSlot(handler, inv.contents());
            }
         }

         this.recomputeHeldItem();
         this.noteAuthoritativeMenuUpdate(this.currentContainerId());
      } else if (packet instanceof ClientboundAddEntityPacket add) {
         String type = entityTypeKey(add.getType());
         int ownerId = type.contains("fishing_bobber") ? add.getData() : -1;
         this.entities
            .put(
               add.getId(),
               add.getUUID(),
               type,
               add.getX(),
               add.getY(),
               add.getZ(),
               add.getMovement(),
               add.getYRot(),
               add.getXRot(),
               add.getYHeadRot(),
               false,
               ownerId,
               this.position.position()
            );
      } else if (packet instanceof ClientboundMoveEntityPacket move) {
         int id = ((RiptideMoveEntityPacketAccessor)move).riptide$getEntityId();
         this.entities
            .moveRelative(
               id, move.getXa(), move.getYa(), move.getZa(), move.hasPosition(), move.getYRot(), move.getXRot(), move.hasRotation(), move.isOnGround()
            );
      } else if (packet instanceof ClientboundMoveMinecartPacket minecart && !minecart.lerpSteps().isEmpty()) {
         MinecartStep step = (MinecartStep)minecart.lerpSteps().getLast();
         this.entities.moveAbsolute(minecart.entityId(), step.position(), step.movement(), step.yRot(), step.xRot());
      } else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
         this.entities.sync(sync.id(), sync.values(), sync.onGround());
      } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
         this.entities.teleport(teleport.id(), teleport.change(), teleport.relatives(), teleport.onGround());
      } else if (packet instanceof ClientboundRotateHeadPacket head) {
         int id = ((RiptideRotateHeadPacketAccessor)head).riptide$getEntityId();
         this.entities.headRotation(id, head.getYHeadRot());
      } else if (packet instanceof ClientboundSetEntityDataPacket datax && isFishingBobber(this.entities.typeOf(datax.id()))) {
         int hookedDataId = RiptideFishingHookAccessor.riptide$getHookedEntityData().id();

         for (DataValue<?> value : datax.packedItems()) {
            if (value.id() == hookedDataId && value.value() instanceof Integer encodedTarget) {
               this.entities.fishingHookTarget(datax.id(), encodedTarget);
               break;
            }
         }
      } else if (packet instanceof ClientboundRemoveEntitiesPacket rem) {
         for (int i = 0; i < rem.getEntityIds().size(); i++) {
            int id = rem.getEntityIds().getInt(i);
            this.entities.remove(id);
            if (this.inVehicle && id == this.vehicleId) {
               this.dismountVehicle();
            }
         }
      } else if (packet instanceof ClientboundStartConfigurationPacket) {
         this.beginReconfiguration();
      }
   }

   private static boolean isFishingBobber(String type) {
      return type != null && (type.equals("fishing_bobber") || type.endsWith(":fishing_bobber"));
   }

   private void resetPlayerLoadHandshake(long now) {
      this.playerLoadedSent.set(false);
      this.levelChunksLoadStarted = false;
      this.playerLoadChunkBatchFinished = false;
      this.playerLoadPositionAccepted = false;
      this.playerLoadReadyAt = Long.MAX_VALUE;
      this.playerLoadHeadlessFallbackAt = Long.MAX_VALUE;
      this.playerLoadDeadlineAt = now + 30000L;
   }

   private void updatePlayerLoadReadiness(long now) {
      if (!this.playerLoadedSent.get()) {
         if (this.levelChunksLoadStarted && this.playerLoadChunkBatchFinished && this.playerLoadPositionAccepted && this.playerLoadReadyAt == Long.MAX_VALUE) {
            this.playerLoadReadyAt = now + 500L;
         }
      }
   }

   private void maybeSendPlayerLoaded(long now) {
      if (!this.playerLoadedSent.get()) {
         boolean normalReady = now >= this.playerLoadReadyAt;
         boolean headlessReady = this.playerLoadPositionAccepted && now >= this.playerLoadHeadlessFallbackAt;
         boolean timedOut = now >= this.playerLoadDeadlineAt;
         if (normalReady || headlessReady || timedOut) {
            if (this.playerLoadedSent.compareAndSet(false, true)) {
               if (!this.send(new ServerboundPlayerLoadedPacket(), true, false)) {
                  this.playerLoadedSent.set(false);
               }
            }
         }
      }
   }

   private int currentContainerId() {
      return this.openContainerId >= 0 ? this.openContainerId : 0;
   }

   private void resetMenuExtrasLocked() {
      Arrays.fill(this.menuData, 0);
      this.menuDataLen = 0;
      this.merchantOffers = null;
      this.villagerLevel = 0;
      this.villagerXp = 0;
      this.villagerShowProgress = false;
   }

   private void invalidateMenu(boolean clearInventory, boolean notifyViewer) {
      synchronized (this.menuLock) {
         this.menuEpoch++;
         this.openContainerId = -1;
         this.containerStateId = this.inventoryStateId;
         this.openScreenTitle = "";
         this.openMenuTypeId = "";
         this.openTitle = Component.empty();
         this.containerDismissed = false;
         this.carried = ItemStack.EMPTY;
         this.menuSlots.clear();
         this.resetMenuExtrasLocked();
         this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.CANCELLED);
         this.clickSawUpdate = false;
         this.clickSyncBlocked = false;
         this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
         if (clearInventory) {
            Collections.fill(this.playerInv, ItemStack.EMPTY);
            this.inventorySynchronized = false;
         }

         if (this.inventorySynchronized) {
            this.menuSlots.addAll(copyStacks(this.playerInv));
         }

         this.menuPhase = this.inventorySynchronized ? MultiSession.MenuPhase.INVENTORY : MultiSession.MenuPhase.SYNCING;
         this.menuInteractive = this.inventorySynchronized;
         this.menuRevision++;
      }

      if (clearInventory) {
         this.heldItemName = "";
      } else {
         this.recomputeHeldItem();
      }

      if (notifyViewer) {
         this.sink.menuClosed(this);
      }
   }

   private void beginServerMenu(ClientboundOpenScreenPacket open) {
      synchronized (this.menuLock) {
         this.menuEpoch++;
         this.openContainerId = open.getContainerId();
         Identifier menuType = BuiltInRegistries.MENU.getKey(open.getType());
         this.openMenuTypeId = menuType == null ? "" : menuType.toString();
         this.openTitle = (Component)(open.getTitle() == null ? Component.empty() : open.getTitle());
         this.openScreenTitle = this.openTitle.getString();
         this.containerDismissed = false;
         this.carried = ItemStack.EMPTY;
         this.menuSlots.clear();
         this.resetMenuExtrasLocked();
         this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.CANCELLED);
         this.clickSawUpdate = false;
         this.clickSyncBlocked = false;
         this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
         this.menuPhase = MultiSession.MenuPhase.SYNCING;
         this.menuInteractive = false;
         this.menuRevision++;
      }

      this.openScreenSeq++;
   }

   private void beginMountMenu(ClientboundMountScreenOpenPacket mount) {
      synchronized (this.menuLock) {
         this.menuEpoch++;
         this.openContainerId = mount.getContainerId();
         this.openMenuTypeId = "riptide:mount";
         this.openTitle = Component.literal("Mount");
         this.openScreenTitle = "Mount";
         this.containerDismissed = false;
         this.carried = ItemStack.EMPTY;
         this.menuSlots.clear();
         this.resetMenuExtrasLocked();
         this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.CANCELLED);
         this.clickSawUpdate = false;
         this.clickSyncBlocked = false;
         this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
         this.menuPhase = MultiSession.MenuPhase.SYNCING;
         this.menuInteractive = false;
         this.menuRevision++;
      }

      this.openScreenSeq++;
   }

   private void noteAuthoritativeMenuUpdate(int containerId) {
      long now = System.currentTimeMillis();
      synchronized (this.menuLock) {
         if (containerId == this.currentContainerId()) {
            this.clickLastUpdateAt = now;
            if (this.clickInFlight != null && this.clickInFlight.epoch() == this.menuEpoch && this.clickInFlight.containerId() == containerId) {
               this.clickSawUpdate = true;
               this.clickInFlight.ticket().completion = MultiSession.ClickCompletion.SETTLING;
            }

            if (this.clickSyncBlocked) {
               this.clickSyncBlocked = false;
               this.menuInteractive = this.menuPhase == MultiSession.MenuPhase.CONTAINER || this.menuPhase == MultiSession.MenuPhase.INVENTORY;
               this.menuRevision++;
            }
         }
      }
   }

   private void cancelClickTransactionsLocked(MultiSession.ClickCompletion inFlightReason) {
      if (this.clickInFlight != null) {
         this.clickInFlight.ticket().completion = inFlightReason;
      }

      this.clickInFlight = null;

      for (MultiSession.QueuedClick queued : this.clickQueue) {
         queued.ticket().completion = MultiSession.ClickCompletion.CANCELLED;
      }

      this.clickQueue.clear();
   }

   private void setMenuSlot(int index, ItemStack stack) {
      if (index >= 0 && index < 2048) {
         synchronized (this.menuLock) {
            while (this.menuSlots.size() <= index) {
               this.menuSlots.add(ItemStack.EMPTY);
            }

            this.menuSlots.set(index, stack == null ? ItemStack.EMPTY : stack.copy());
            this.menuRevision++;
         }
      }
   }

   private void addListed(UUID id) {
      if (id != null) {
         if (this.listedPlayers.size() < 8192 || this.listedPlayers.contains(id)) {
            this.listedPlayers.add(id);
         }
      }
   }

   private void updateSavedMenuContent(int containerId, int stateId, List<ItemStack> items, ItemStack cursor) {
      MultiSession.SavedMenu saved = this.savedMenu;
      if (saved != null && saved.containerId() == containerId) {
         this.savedMenu = new MultiSession.SavedMenu(containerId, stateId, saved.title(), saved.titleText(), copyStacks(items), nonNull(cursor).copy());
      }
   }

   private void updateSavedMenuSlot(int containerId, int stateId, int slot, ItemStack stack) {
      MultiSession.SavedMenu saved = this.savedMenu;
      if (saved != null && saved.containerId() == containerId && slot >= 0 && slot < saved.slots().size()) {
         List<ItemStack> slots = copyStacks(saved.slots());
         slots.set(slot, nonNull(stack).copy());
         this.savedMenu = new MultiSession.SavedMenu(containerId, stateId, saved.title(), saved.titleText(), slots, saved.carried());
      }
   }

   private void updateSavedMenuCursor(int containerId, ItemStack cursor) {
      MultiSession.SavedMenu saved = this.savedMenu;
      if (saved != null && saved.containerId() == containerId) {
         this.savedMenu = new MultiSession.SavedMenu(
            saved.containerId(), saved.stateId(), saved.title(), saved.titleText(), saved.slots(), nonNull(cursor).copy()
         );
      }
   }

   private static int inventoryIndexToHandler(int playerSlot) {
      if (playerSlot >= 0 && playerSlot <= 8) {
         return 36 + playerSlot;
      } else if (playerSlot >= 9 && playerSlot <= 35) {
         return playerSlot;
      } else if (playerSlot >= 36 && playerSlot <= 39) {
         return 44 - playerSlot;
      } else {
         return playerSlot == 40 ? 45 : -1;
      }
   }

   private static String dimensionOf(CommonPlayerSpawnInfo info) {
      try {
         return info == null ? "" : info.dimension().identifier().toString();
      } catch (RuntimeException var2) {
         return "";
      }
   }

   private void applyAttributes(ClientboundUpdateAttributesPacket packet) {
      if (packet.getEntityId() == this.playerEntityId) {
         try {
            for (AttributeSnapshot snapshot : packet.getValues()) {
               double effective = effectiveAttributeValue(snapshot);
               if (snapshot.attribute().value() == Attributes.MAX_HEALTH.value()) {
                  this.maxHealth = (float)Math.max(1.0, effective);
               } else if (snapshot.attribute().value() == Attributes.BLOCK_INTERACTION_RANGE.value()) {
                  this.blockInteractionRange = Math.max(0.0, effective);
               } else if (snapshot.attribute().value() == Attributes.ENTITY_INTERACTION_RANGE.value()) {
                  this.entityInteractionRange = Math.max(0.0, effective);
               }
            }
         } catch (RuntimeException var6) {
         }
      }
   }

   private static double effectiveAttributeValue(AttributeSnapshot snapshot) {
      double base = snapshot.base();
      double multipliedBase = 0.0;
      double multipliedTotal = 1.0;

      for (AttributeModifier modifier : snapshot.modifiers()) {
         switch (modifier.operation()) {
            case ADD_VALUE:
               base += modifier.amount();
               break;
            case ADD_MULTIPLIED_BASE:
               multipliedBase += modifier.amount();
               break;
            case ADD_MULTIPLIED_TOTAL:
               multipliedTotal *= 1.0 + modifier.amount();
         }
      }

      return base * (1.0 + multipliedBase) * multipliedTotal;
   }

   private String displayName() {
      MultiIdentityResolver.Identity resolved = this.identity;
      return resolved == null ? this.spec.accountId() : resolved.user().getName();
   }

   private int heldHandlerSlot() {
      return this.openContainerId < 0 ? 36 + this.selectedHotbar : this.menuSlots.size() - 9 + this.selectedHotbar;
   }

   private void recomputeHeldItem() {
      String name = "";
      synchronized (this.menuLock) {
         if (this.openContainerId < 0) {
            int idx = 36 + Math.max(0, Math.min(8, this.selectedHotbar));
            if (idx < this.playerInv.size()) {
               ItemStack stack = this.playerInv.get(idx);
               if (stack != null && !stack.isEmpty()) {
                  name = stack.getHoverName().getString();
               }
            }
         } else {
            int idx = this.heldHandlerSlot();
            if (idx >= 0 && idx < this.menuSlots.size()) {
               ItemStack stack = this.menuSlots.get(idx);
               if (stack != null && !stack.isEmpty()) {
                  name = stack.getHoverName().getString();
               }
            }
         }
      }

      this.heldItemName = name;
   }

   public int hotbarIndexOfHandler(int handler) {
      synchronized (this.menuLock) {
         if (this.openContainerId < 0) {
            return handler >= 36 && handler <= 44 ? handler - 36 : -1;
         } else {
            int start = this.menuSlots.size() - 9;
            return handler >= start && handler < this.menuSlots.size() ? handler - start : -1;
         }
      }
   }

   public int selectedHotbarHandler() {
      int idx = Math.max(0, Math.min(8, this.selectedHotbar));
      synchronized (this.menuLock) {
         return this.openContainerId >= 0 && this.menuPhase != MultiSession.MenuPhase.SYNTHETIC ? this.menuSlots.size() - 9 + idx : 36 + idx;
      }
   }

   public int clickHandlerLimit() {
      synchronized (this.menuLock) {
         return this.openContainerId < 0 ? 46 : this.menuSlots.size();
      }
   }

   public String menuTypeId() {
      return this.openContainerId < 0 ? "" : this.openMenuTypeId;
   }

   private MultiSession.MenuExtras buildMenuExtras() {
      int[] data = Arrays.copyOf(this.menuData, Math.max(0, Math.min(this.menuDataLen, this.menuData.length)));
      List<MultiSession.TradeView> trades = List.of();
      MerchantOffers offers = this.merchantOffers;
      if (offers != null && !offers.isEmpty()) {
         List<MultiSession.TradeView> list = new ArrayList<>(offers.size());

         for (MerchantOffer o : offers) {
            if (o != null) {
               list.add(
                  new MultiSession.TradeView(
                     nonNull(o.getCostA()).copy(),
                     nonNull(o.getCostB()).copy(),
                     nonNull(o.getResult()).copy(),
                     o.isOutOfStock(),
                     o.getUses(),
                     o.getMaxUses(),
                     o.getXp(),
                     o.getSpecialPriceDiff()
                  )
               );
            }
         }

         trades = list;
      }

      return new MultiSession.MenuExtras(this.openMenuTypeId, data, trades, this.villagerLevel, this.villagerXp, this.villagerShowProgress);
   }

   public MultiSession.MenuContext menuContext() {
      synchronized (this.menuLock) {
         return new MultiSession.MenuContext(
            this.menuPhase,
            this.menuEpoch,
            this.currentContainerId(),
            this.openMenuTypeId,
            this.openContainerId < 0 ? this.inventoryStateId : this.containerStateId,
            this.openTitle == null ? Component.empty() : this.openTitle.copy(),
            copyStacks(this.menuSlots),
            nonNull(this.carried).copy(),
            this.inventorySynchronized,
            this.menuInteractive && !this.clickSyncBlocked,
            this.containerDismissed,
            this.pendingClickCountLocked(),
            this.clickSyncBlocked
         );
      }
   }

   public MultiSession.MenuView menuView() {
      synchronized (this.menuLock) {
         if (this.menuPhase == MultiSession.MenuPhase.SYNCING) {
            return new MultiSession.MenuView(
               Component.literal("Inventory synchronizing..."),
               List.of(),
               ItemStack.EMPTY,
               Math.max(0, this.openContainerId),
               this.openContainerId < 0 ? this.inventoryStateId : this.containerStateId,
               MultiSession.MenuPhase.SYNCING,
               false,
               this.menuEpoch,
               this.pendingClickCount(),
               this.clickSyncBlocked,
               MultiSession.MenuExtras.NONE
            );
         } else if (this.menuPhase == MultiSession.MenuPhase.SYNTHETIC) {
            return this.inventoryView();
         } else if (this.openContainerId >= 0 && !this.containerDismissed) {
            List<MultiSession.ViewSlot> out = new ArrayList<>();
            int size = this.menuSlots.size();
            int containerCount = Math.max(0, size - 36);
            int auxCount = this.merchantOffers != null ? this.merchantOffers.size() : 0;
            MultiMenuGeometry.Layout layout = MultiMenuGeometry.layout(this.openMenuTypeId, containerCount, auxCount);

            for (int i = 0; i < containerCount; i++) {
               this.laidSlot(out, i, layout.x()[i], layout.y()[i]);
            }

            int invTop = layout.invTop();
            int base = containerCount;

            for (int i = 0; i < 27 && base + i < size; i++) {
               this.laidSlot(out, base + i, i % 9 * 18, invTop + i / 9 * 18);
            }

            for (int i = 0; i < 9 && base + 27 + i < size; i++) {
               this.laidSlot(out, base + 27 + i, i * 18, invTop + 54 + 4);
            }

            Component title = (Component)(this.openScreenTitle.isBlank() ? Component.literal("Container") : this.openTitle);
            return new MultiSession.MenuView(
               title,
               out,
               this.carried,
               this.openContainerId,
               this.containerStateId,
               this.menuPhase,
               this.menuInteractive && this.menuPhase == MultiSession.MenuPhase.CONTAINER,
               this.menuEpoch,
               this.pendingClickCount(),
               this.clickSyncBlocked,
               this.buildMenuExtras()
            );
         } else {
            return this.inventoryView();
         }
      }
   }

   public MultiSession.MenuView inventoryView() {
      synchronized (this.menuLock) {
         List<MultiSession.ViewSlot> out = new ArrayList<>();
         boolean synthetic = this.menuPhase == MultiSession.MenuPhase.SYNTHETIC;
         int base = this.openContainerId >= 0 && !synthetic ? this.menuSlots.size() - 36 : -1;
         this.invSlot(out, 0, this.invClickHandler(0, base), 146, 20);

         for (int i = 1; i <= 4; i++) {
            this.invSlot(out, i, this.invClickHandler(i, base), 90 + (i - 1) % 2 * 18, 10 + (i - 1) / 2 * 18);
         }

         for (int i = 5; i <= 8; i++) {
            this.invSlot(out, i, this.invClickHandler(i, base), 0, (i - 5) * 18);
         }

         for (int i = 9; i <= 35; i++) {
            this.invSlot(out, i, this.invClickHandler(i, base), (i - 9) % 9 * 18, 76 + (i - 9) / 9 * 18);
         }

         for (int i = 36; i <= 44; i++) {
            this.invSlot(out, i, this.invClickHandler(i, base), (i - 36) * 18, 134);
         }

         this.invSlot(out, 45, this.invClickHandler(45, base), 69, 54);
         boolean interactive = this.menuInteractive
            && !this.clickSyncBlocked
            && (this.menuPhase == MultiSession.MenuPhase.INVENTORY || this.menuPhase == MultiSession.MenuPhase.CONTAINER && this.containerDismissed);
         Component title = this.menuPhase == MultiSession.MenuPhase.SYNTHETIC
            ? Component.literal(this.displayName() + " Inventory (saved GUI isolated)")
            : Component.literal(this.displayName() + " Inventory");
         ItemStack cursor = this.menuPhase == MultiSession.MenuPhase.SYNTHETIC ? ItemStack.EMPTY : this.carried;
         return new MultiSession.MenuView(
            title,
            out,
            cursor,
            synthetic ? 0 : Math.max(0, this.openContainerId),
            !synthetic && this.openContainerId >= 0 ? this.containerStateId : this.inventoryStateId,
            this.menuPhase,
            interactive,
            this.menuEpoch,
            this.pendingClickCount(),
            this.clickSyncBlocked,
            MultiSession.MenuExtras.NONE
         );
      }
   }

   private int pendingClickCount() {
      synchronized (this.menuLock) {
         return this.pendingClickCountLocked();
      }
   }

   private int pendingClickCountLocked() {
      long pending = this.clickInFlight == null ? 0L : this.clickInFlight.repetitionsRemaining();

      for (MultiSession.QueuedClick queued : this.clickQueue) {
         pending += queued.repetitionsRemaining();
      }

      return (int)Math.min(2147483647L, pending);
   }

   private int invClickHandler(int h, int base) {
      if (base < 0) {
         return h;
      } else if (h >= 9 && h <= 35) {
         return base + (h - 9);
      } else {
         return h >= 36 && h <= 44 ? base + 27 + (h - 36) : -1;
      }
   }

   private void invSlot(List<MultiSession.ViewSlot> out, int h, int clickHandler, int x, int y) {
      ItemStack stack = h >= 0 && h < this.playerInv.size() && this.playerInv.get(h) != null ? this.playerInv.get(h) : ItemStack.EMPTY;
      out.add(new MultiSession.ViewSlot(x, y, clickHandler, stack));
   }

   private void laidSlot(List<MultiSession.ViewSlot> out, int handler, int x, int y) {
      ItemStack stack = handler >= 0 && handler < this.menuSlots.size() && this.menuSlots.get(handler) != null ? this.menuSlots.get(handler) : ItemStack.EMPTY;
      out.add(new MultiSession.ViewSlot(x, y, handler, stack));
   }

   private void syncPlayerInvFromMenu() {
      synchronized (this.menuLock) {
         int size = this.menuSlots.size();
         if (this.openContainerId < 0) {
            for (int h = 0; h < 46; h++) {
               this.playerInv.set(h, h < size ? nonNull(this.menuSlots.get(h)).copy() : ItemStack.EMPTY);
            }
         } else {
            int base = size - 36;
            if (base < 0) {
               return;
            }

            for (int i = 0; i < 27; i++) {
               this.playerInv.set(9 + i, nonNull(this.menuSlots.get(base + i)).copy());
            }

            for (int i = 0; i < 9; i++) {
               this.playerInv.set(36 + i, nonNull(this.menuSlots.get(base + 27 + i)).copy());
            }
         }
      }
   }

   private static ItemStack nonNull(ItemStack stack) {
      return stack == null ? ItemStack.EMPTY : stack;
   }

   long menuRevision() {
      return this.menuRevision;
   }

   public String closeSilent() {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else {
         synchronized (this.menuLock) {
            if (this.clickInFlight != null || !this.clickQueue.isEmpty()) {
               if (this.closeAfterClicks == MultiSession.DeferredMenuClose.NONE) {
                  this.closeAfterClicks = MultiSession.DeferredMenuClose.SILENT;
               }

               return "Sent";
            }
         }

         this.dismissContainerLocally();
         return "Sent";
      }
   }

   private void dismissContainerLocally() {
      synchronized (this.menuLock) {
         this.containerDismissed = true;
         this.openScreenTitle = "";
         this.openTitle = Component.empty();
         this.menuRevision++;
      }
   }

   @Override
   public boolean saveGui(boolean closeAfter, boolean sendClosePacket) {
      if (this.status.get() != MultiSession.Status.READY) {
         return false;
      } else {
         synchronized (this.menuLock) {
            List<ItemStack> source = this.menuSlots.isEmpty() ? this.playerInv : this.menuSlots;
            this.savedMenu = new MultiSession.SavedMenu(
               this.currentContainerId(),
               this.containerStateId,
               this.openTitle == null ? Component.empty() : this.openTitle.copy(),
               this.openScreenTitle,
               copyStacks(source),
               nonNull(this.carried).copy()
            );
         }

         if (!closeAfter) {
            return true;
         } else {
            return sendClosePacket ? "Sent".equals(this.closeContainer()) : "Sent".equals(this.closeSilent());
         }
      }
   }

   @Override
   public boolean desyncGui() {
      if (this.status.get() == MultiSession.Status.READY && this.openContainerId >= 0) {
         synchronized (this.menuLock) {
            if (this.clickInFlight != null || !this.clickQueue.isEmpty()) {
               this.closeAfterClicks = MultiSession.DeferredMenuClose.DESYNC;
               return true;
            }
         }

         boolean sent = this.send(new ServerboundContainerClosePacket(this.openContainerId), false, false);
         if (sent) {
            this.markSyntheticMenu();
         }

         return sent;
      } else {
         return false;
      }
   }

   private void markSyntheticMenu() {
      synchronized (this.menuLock) {
         if (!this.openMenuTypeId.startsWith("synthetic:")) {
            this.openMenuTypeId = "synthetic:" + this.openMenuTypeId;
         }

         this.menuPhase = MultiSession.MenuPhase.SYNTHETIC;
         this.menuInteractive = false;
         this.containerDismissed = false;
         this.menuRevision++;
      }
   }

   @Override
   public boolean restoreGui() {
      MultiSession.SavedMenu saved = this.savedMenu;
      if (this.status.get() == MultiSession.Status.READY && saved != null) {
         synchronized (this.menuLock) {
            this.menuEpoch++;
            this.openContainerId = saved.containerId() == 0 ? -1 : saved.containerId();
            this.containerStateId = saved.stateId();
            this.openTitle = saved.title() == null ? Component.empty() : saved.title().copy();
            this.openScreenTitle = saved.titleText() == null ? "" : saved.titleText();
            this.openMenuTypeId = "synthetic:saved";
            this.containerDismissed = false;
            this.carried = nonNull(saved.carried()).copy();
            this.menuSlots.clear();
            this.menuSlots.addAll(copyStacks(saved.slots()));
            this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.CANCELLED);
            this.clickSawUpdate = false;
            this.clickSyncBlocked = false;
            this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
            this.menuPhase = MultiSession.MenuPhase.SYNTHETIC;
            this.menuInteractive = false;
            this.menuRevision++;
         }

         this.recomputeHeldItem();
         return true;
      } else {
         return false;
      }
   }

   private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
      List<ItemStack> copy = new ArrayList<>(stacks == null ? 0 : stacks.size());
      if (stacks != null) {
         for (ItemStack stack : stacks) {
            copy.add(nonNull(stack).copy());
         }
      }

      return copy;
   }

   @Override
   public int runXCarry(XCarryAction action, long now) {
      if (this.status.get() == MultiSession.Status.READY && action != null) {
         if (this.hasPendingClicks()) {
            return 0;
         } else {
            MultiSession.XCarryJob job = this.xCarryJob;
            if (job == null || job.action != action) {
               boolean hadContainer = this.openContainerId >= 0;
               if (hadContainer && this.lastInteractBlock == null && this.lastInteractEntityId < 0) {
                  return -1;
               }

               job = new MultiSession.XCarryJob(action, hadContainer, this.lastInteractBlock, this.lastInteractEntityId);
               if (action.mode == XCarryAction.Mode.PUT_IN) {
                  this.xCarryForced = true;
                  this.xCarryActive = true;
               }

               if (hadContainer && action.mode == XCarryAction.Mode.PUT_IN) {
                  synchronized (this.menuLock) {
                     int ownSlots = Math.max(0, this.menuSlots.size() - 36);
                     job.collectSlots.addAll(MultiXCarryPlanner.collectContainerSlots(action, this.menuSlots, ownSlots));
                  }
               }

               this.xCarryJob = job;
            }

            if (now < job.nextAt) {
               return 0;
            } else {
               int budget = 8;

               while (budget-- > 0) {
                  switch (job.phase) {
                     case COLLECT:
                        Integer slot = job.collectSlots.pollFirst();
                        if (slot != null) {
                           if (!this.enqueueAutomatedClick(slot, 0, ContainerInput.QUICK_MOVE)) {
                              return this.failXCarry();
                           }

                           job.nextAt = now + 50L;
                           return 0;
                        }

                        job.phase = MultiSession.XCarryPhase.CLOSE_CONTAINER;
                        break;
                     case CLOSE_CONTAINER:
                        int closingId = this.openContainerId;
                        if (closingId >= 0 && !this.send(new ServerboundContainerClosePacket(closingId), false, false)) {
                           return this.failXCarry();
                        }

                        this.activateInventoryMirror();
                        job.phase = MultiSession.XCarryPhase.WAIT_INVENTORY;
                        job.nextAt = now + 50L;
                        return 0;
                     case WAIT_INVENTORY:
                        this.activateInventoryMirror();
                        synchronized (this.menuLock) {
                           job.clicks.addAll(MultiXCarryPlanner.plan(action, this.playerInv, this.carried));
                        }

                        job.phase = MultiSession.XCarryPhase.EXECUTE;
                        break;
                     case EXECUTE:
                        MultiXCarryPlanner.Click click = job.clicks.pollFirst();
                        if (click != null) {
                           if (!this.enqueueAutomatedClick(click.slot(), click.button(), click.input())) {
                              return this.failXCarry();
                           }

                           long delay = Math.max(0L, Math.min(500L, click.delayAfterMs()));
                           job.nextAt = now + delay;
                           return 0;
                        }

                        this.xCarryForced = true;
                        this.refreshXCarryActive();
                        job.phase = job.hadContainer ? MultiSession.XCarryPhase.REOPEN : MultiSession.XCarryPhase.DONE;
                        break;
                     case REOPEN:
                        boolean sent;
                        if (job.reopenBlock != null) {
                           BlockPos pos = job.reopenBlock;
                           sent = "Sent".equals(this.useOnBlock(pos.getX(), pos.getY(), pos.getZ(), "UP"));
                        } else {
                           sent = "Sent".equals(this.interactEntity(job.reopenEntity, false));
                        }

                        if (!sent) {
                           return this.failXCarry();
                        }

                        job.phase = MultiSession.XCarryPhase.DONE;
                        job.nextAt = now + 50L;
                        return 0;
                     case DONE:
                        this.xCarryJob = null;
                        this.refreshXCarryActive();
                        return 1;
                  }
               }

               return 0;
            }
         }
      } else {
         return -1;
      }
   }

   private int failXCarry() {
      this.xCarryJob = null;
      this.xCarryForced = true;
      this.refreshXCarryActive();
      return -1;
   }

   @Override
   public void cancelXCarry() {
      this.xCarryJob = null;
      this.refreshXCarryActive();
   }

   private void activateInventoryMirror() {
      synchronized (this.menuLock) {
         this.menuEpoch++;
         this.openContainerId = -1;
         this.containerStateId = this.inventoryStateId;
         this.openScreenTitle = "";
         this.openMenuTypeId = "";
         this.openTitle = Component.empty();
         this.containerDismissed = false;
         this.menuSlots.clear();
         this.menuSlots.addAll(copyStacks(this.playerInv));
         this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.CANCELLED);
         this.clickSawUpdate = false;
         this.clickSyncBlocked = false;
         this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
         this.menuPhase = MultiSession.MenuPhase.INVENTORY;
         this.menuInteractive = this.inventorySynchronized;
         this.menuRevision++;
      }

      this.recomputeHeldItem();
   }

   private void refreshXCarryActive() {
      synchronized (this.menuLock) {
         if (!this.xCarryForced) {
            this.xCarryActive = false;
         } else {
            boolean stored = !nonNull(this.carried).isEmpty();

            for (int slot = 0; !stored && slot <= 8 && slot < this.playerInv.size(); slot++) {
               stored = !nonNull(this.playerInv.get(slot)).isEmpty();
            }

            if (!stored && this.playerInv.size() > 45) {
               stored = !nonNull(this.playerInv.get(45)).isEmpty();
            }

            if (!stored) {
               this.xCarryForced = false;
            }

            this.xCarryActive = stored;
         }
      }
   }

   private void beginReconfiguration() {
      synchronized (this.pilotProtocolLock) {
         this.setStatus(MultiSession.Status.CONFIGURING, "Reconfiguring");
         this.sendChatAcknowledgement();
      }

      this.resetChatState();
      this.registryData = new RegistryDataCollector();
      this.clearScoreboardState();
      this.savedMenu = null;
      this.xCarryJob = null;
      synchronized (this.suggestionLock) {
         this.suggestionReplies.clear();
      }

      this.hasCommandTree = false;
      this.hasPosition = false;
      this.readyOnce = false;
      this.containerStateId = 0;
      this.inventoryStateId = 0;
      this.invalidateMenu(true, true);
      this.xCarryForced = false;
      this.xCarryActive = false;
      this.heldItemName = "";
      this.dimension = "";
      this.health = 20.0F;
      this.maxHealth = 20.0F;
      this.food = 20;
      this.respawnAt = 0L;
      this.entities.clear();
      Connection transitioning = this.connection;
      int transitionEpoch = this.connectEpoch;
      Runnable applyProtocolTransition = () -> {
         if (!this.closed.get()
            && transitioning != null
            && transitioning == this.connection
            && transitionEpoch == this.connectEpoch
            && transitioning.isConnected()) {
            transitioning.setupInboundProtocol(
               ConfigurationProtocols.CLIENTBOUND, this.listener(MultiSession.Phase.CONFIGURATION, ClientConfigurationPacketListener.class)
            );
            this.send(ServerboundConfigurationAcknowledgedPacket.INSTANCE, true, false);
            transitioning.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
         }
      };

      try {
         Channel channel = ((RiptideClientConnectionAccessor)transitioning).getChannel();
         if (channel != null) {
            channel.eventLoop().execute(applyProtocolTransition);
         } else {
            applyProtocolTransition.run();
         }
      } catch (Throwable var5) {
         this.fail("Reconfiguration failed: " + shortError(var5));
      }
   }

   private void handlePlayerChat(ClientboundPlayerChatPacket packet, boolean show) {
      Optional<SignedMessageBody> unpacked;
      boolean acknowledge;
      synchronized (this.chatStateLock) {
         int expected = this.nextChatIndex++;
         if (packet.globalIndex() != expected) {
            this.fail("Bad chat index: expected " + expected + ", got " + packet.globalIndex());
            return;
         }

         unpacked = packet.body().unpack(this.signatureCache);
         if (unpacked.isEmpty()) {
            this.fail("Unrecognized chat signature");
            return;
         }

         this.signatureCache.push(unpacked.get(), packet.signature());
         acknowledge = packet.signature() != null && this.lastSeenMessages.addPending(packet.signature(), show) && this.lastSeenMessages.offset() > 64;
      }

      if (acknowledge) {
         this.sendChatAcknowledgement();
      }

      this.captureChatText(unpacked.get().content());
      if (show) {
         String sender = this.playerNames.getOrDefault(packet.sender(), packet.sender().toString().substring(0, 8));
         Component content = (Component)(packet.unsignedContent() != null ? packet.unsignedContent() : Component.literal(unpacked.get().content()));
         this.sink.chat(this, Component.literal("<" + sender + "> ").append(content));
      }
   }

   private void noteCaptchaAnnounce(Component component, long now) {
      if (component != null && RiptideConfig.getGlobal().multiAutoSolveCaptcha) {
         try {
            String s = component.getString();
            if (s != null && CAPTCHA_ANNOUNCE.matcher(s).find()) {
               this.captchaAnnouncedUntil = now + 30000L;
            }
         } catch (Throwable var5) {
         }
      }
   }

   private boolean captchaWindowOpen(long now) {
      if (this.piloted) {
         return false;
      } else if (!RiptideConfig.getGlobal().multiAutoSolveCaptcha) {
         return false;
      } else if (this.captcha.isActivelySolving(now)) {
         return true;
      } else {
         return now < this.captchaAnnouncedUntil ? true : this.status.get() != MultiSession.Status.READY;
      }
   }

   private void handleCommon(Packet<?> packet) {
      if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
         this.send(new ServerboundKeepAlivePacket(keepAlive.getId()), true, false);
      } else if (packet instanceof ClientboundPingPacket pingPacket) {
         this.send(new ServerboundPongPacket(pingPacket.getId()), true, false);
      } else if (packet instanceof ClientboundResourcePackPushPacket pack) {
         this.send(
            new ServerboundResourcePackPacket(pack.id(), net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.ACCEPTED), true, false
         );
         this.send(
            new ServerboundResourcePackPacket(pack.id(), net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.DOWNLOADED), true, false
         );
         this.send(
            new ServerboundResourcePackPacket(pack.id(), net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED),
            true,
            false
         );
      } else if (packet instanceof ClientboundStoreCookiePacket cookie) {
         if (!this.cookies.containsKey(cookie.key()) && this.cookies.size() >= 64) {
            this.cookies.remove(this.cookies.keySet().iterator().next());
         }

         this.cookies.put(cookie.key(), (byte[])cookie.payload().clone());
      } else if (packet instanceof ClientboundCookieRequestPacket request) {
         this.send(new ServerboundCookieResponsePacket(request.key(), this.cookies.get(request.key())), true, false);
      } else if (packet instanceof ClientboundDisconnectPacket disconnected) {
         if (this.maybeRejoinKeyless(disconnected.reason())) {
            return;
         }

         if (this.maybeReconnectAfterVerification(disconnected.reason())) {
            return;
         }

         if (this.maybeRetryVerification(disconnected.reason())) {
            return;
         }

         this.connection.disconnect(disconnected.reason());
      } else if (packet instanceof ClientboundTransferPacket transfer && this.allowTransfer()) {
         this.suppressChatKey = false;
         this.serverAssignedUuid = null;
         this.beginTransfer(transfer.host(), transfer.port());
      }
   }

   private boolean maybeRejoinKeyless(Component reason) {
      if (!this.suppressChatKey && !this.closed.get() && !this.lastConnectHost.isBlank()) {
         String text = reason == null ? "" : reason.getString().toLowerCase(Locale.ROOT);
         if (!text.contains("signature")) {
            return false;
         } else {
            this.suppressChatKey = true;
            this.appendLocal("Server rejected signed chat; rejoining without a key in 5s (chat may be limited here).");
            this.beginTransfer(this.lastConnectHost, this.lastConnectPort, false, 5000L);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean maybeReconnectAfterVerification(Component reason) {
      if (!this.closed.get() && !this.lastConnectHost.isBlank()) {
         String text = reason == null ? "" : reason.getString().toLowerCase(Locale.ROOT);
         boolean verifiedOk = text.contains("successfully passed")
            || text.contains("passed the verification")
            || text.contains("success") && text.contains("verif")
            || text.contains("able to play")
            || text.contains("when you reconnect");
         if (!verifiedOk) {
            return false;
         } else {
            long delay = 5000L + ThreadLocalRandom.current().nextLong(2000L);
            this.beginTransfer(this.lastConnectHost, this.lastConnectPort, false, delay);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean allowTransfer() {
      long nowMs = System.currentTimeMillis();
      if (nowMs - this.transferBurstStart > 10000L) {
         this.transferBurstStart = nowMs;
         this.transferBurst = 0;
      }

      if (++this.transferBurst > 12) {
         this.fail("Too many server transfers; stopping to avoid a redirect loop.");
         return false;
      } else {
         return true;
      }
   }

   private boolean maybeRetryVerification(Component reason) {
      if (!this.closed.get() && !this.lastConnectHost.isBlank()) {
         if (this.status.get() == MultiSession.Status.READY) {
            return false;
         } else {
            String text = reason == null ? "" : reason.getString().toLowerCase(Locale.ROOT);
            boolean failure = text.contains("captcha")
               || text.contains("verif")
               || text.contains("incorrect")
               || text.contains("wrong answer")
               || text.contains("try again")
               || text.contains("too many")
               || text.contains("didn't solve")
               || text.contains("did not solve")
               || text.contains("failed the");
            if (!failure) {
               return false;
            } else if (this.verificationRetries >= 5) {
               this.appendLocal("Verification failed " + this.verificationRetries + "x; giving up on " + this.lastConnectHost + ".");
               return false;
            } else {
               this.verificationRetries++;
               long delay = 4000L + ThreadLocalRandom.current().nextLong(2000L);
               this.appendLocal("Verification failed; retrying (" + this.verificationRetries + "/5) in ~4s for a fresh captcha.");
               this.beginTransfer(this.lastConnectHost, this.lastConnectPort, false, delay);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void beginTransfer(String host, int port) {
      this.beginTransfer(host, port, true, 0L);
   }

   private void beginTransfer(String host, int port, boolean transferIntent, long delayMs) {
      this.beginTransfer(host, port, transferIntent, delayMs, false);
   }

   private void beginTransfer(String host, int port, boolean transferIntent, long delayMs, boolean reresolve) {
      if (!this.closed.get() && host != null && !host.isBlank()) {
         String transferDetail = delayMs > 0L ? "Reconnecting in " + delayMs / 1000L + "s" : "Reconnecting to " + host;
         synchronized (this.pilotProtocolLock) {
            this.setStatus(MultiSession.Status.CONNECTING, transferDetail);
         }

         this.invalidateMenu(true, true);
         Connection old = this.connection;
         this.connectEpoch++;
         if (old != null) {
            try {
               old.disconnect(Component.literal("Reconnecting to " + host + ":" + port));
            } catch (RuntimeException var12) {
            }
         }

         Executor exec = delayMs > 0L ? CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, this.worker) : this.worker;
         CompletableFuture.<InetSocketAddress>supplyAsync(() -> resolveTransferTarget(host, port), exec).whenComplete((address, error) -> {
            if (!this.closed.get()) {
               if (error != null || address == null) {
                  this.fail("Reconnect failed: " + host + ":" + port);
               } else {
                  if (reresolve) {
                     try {
                        this.identity = MultiIdentityResolver.resolve(this.spec.accountId());
                     } catch (Exception var8) {
                        this.fail("Re-authentication failed: " + shortError(var8));
                        return;
                     }
                  }

                  this.nextConnectTransferring = transferIntent;
                  this.connect(address, host, port);
               }
            }
         });
      }
   }

   private static InetSocketAddress resolveTransferTarget(String host, int port) {
      ServerAddress server = new ServerAddress(host, port);
      return ServerNameResolver.DEFAULT.resolveAddress(server).<InetSocketAddress>map(ResolvedServerAddress::asInetSocketAddress).orElse(null);
   }

   private void updateCustomMenuTitle(CustomMenuSnapshot before, CustomMenuSnapshot after) {
      if (after != null) {
         this.openScreenTitle = after.title();
         this.openScreenSeq++;
      } else {
         if (before != null) {
            this.openScreenTitle = this.openContainerId >= 0 ? this.openTitle.getString() : "";
         }
      }
   }

   void updateFormValues(Map<String, String> values) {
      this.formValues = values == null ? Map.of() : Map.copyOf(values);
      if (values != null && values.containsKey("password")) {
         this.missingPasswordAlerted = true;
      }
   }

   private void noteLoginPrompt(Component content) {
      if (content != null && this.loginMode == MultiProfile.LoginMode.Auto) {
         this.autoLogin.onChatLine(content.getString());
      }
   }

   private boolean canAnswerLoginScreen() {
      if (this.customMenus.current() == null) {
         return false;
      } else {
         MultiMacroRun run = this.macroRun;
         return run != null && run.isHandlingCustomMenu() ? true : !this.loginPassword().isEmpty();
      }
   }

   private String loginPassword() {
      String p = this.formValues.get("password");
      if (p != null && !p.isBlank()) {
         return p;
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(this.spec.accountId());
         if (account != null && account.password != null && !account.password.isBlank()) {
            return account.password;
         } else {
            try {
               String global = RiptideJoinMacroController.openFormValues().get("password");
               if (global != null && !global.isBlank()) {
                  return global;
               }
            } catch (RuntimeException var4) {
            }

            return "";
         }
      }
   }

   private void maybeAlertMissingPassword(CustomMenuSnapshot snapshot) {
      if (snapshot != null && !this.missingPasswordAlerted) {
         long started = this.connectStartedAt;
         if (started != 0L && System.currentTimeMillis() - started <= 10000L) {
            boolean hasTextInput = false;

            for (CustomMenuInput input : snapshot.inputs()) {
               if (input.kind() == CustomMenuInput.Kind.TEXT) {
                  hasTextInput = true;
                  break;
               }
            }

            if (hasTextInput && !this.formValues.containsKey("password")) {
               this.missingPasswordAlerted = true;
               this.sink.customMenuNeedsPassword(this, snapshot.title());
            }
         }
      }
   }

   private void prepareChatSession() {
      MultiIdentityResolver.Identity id = this.identity;
      if (id != null && !this.suppressChatKey) {
         SignatureValidator validator = id.profileKeyValidator();
         if (validator != null) {
            id.keyPairManager().prepareKeyPair().whenComplete((pair, error) -> {
               if (!this.closed.get() && !this.suppressChatKey && error == null && pair != null && !pair.isEmpty()) {
                  LocalChatSession created = LocalChatSession.create((ProfileKeyPair)pair.get());
                  Data data = created.asRemote().asData();
                  UUID signAs = this.serverAssignedUuid != null ? this.serverAssignedUuid : id.user().getProfileId();

                  try {
                     data.validate(new GameProfile(signAs, id.user().getName()), validator);
                  } catch (ValidationException var11) {
                     return;
                  }

                  if (!this.closed.get() && this.status.get() == MultiSession.Status.READY) {
                     synchronized (this.chatStateLock) {
                        this.chatSession = created;
                        this.signedEncoder = created.createMessageEncoder(signAs);
                     }

                     this.send(new ServerboundChatSessionUpdatePacket(data), true, false);
                  }
               }
            });
         }
      }
   }

   private void rekeyChatEncoder() {
      synchronized (this.chatStateLock) {
         if (this.chatSession != null && this.serverAssignedUuid != null) {
            this.signedEncoder = this.chatSession.createMessageEncoder(this.serverAssignedUuid);
         }
      }
   }

   int requestSuggestions(String command) {
      if (this.status.get() == MultiSession.Status.READY && command != null) {
         int id = this.suggestReqId.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
         return this.send(new ServerboundCommandSuggestionPacket(id, command), true, false) ? id : -1;
      } else {
         return -1;
      }
   }

   MultiSession.Suggest suggestion(int requestId) {
      synchronized (this.suggestionLock) {
         return this.suggestionReplies.get(requestId);
      }
   }

   private void trackObjective(ClientboundSetObjectivePacket packet) {
      String name = packet.getObjectiveName();
      if (name != null && !name.isBlank()) {
         synchronized (this.scoreboardLock) {
            if (packet.getMethod() == 1) {
               this.scoreObjectives.remove(name);
               this.scoresByObjective.remove(name);
               this.displayedObjectives.values().removeIf(name::equals);
            } else {
               String title = packet.getDisplayName() == null ? name : packet.getDisplayName().getString();
               if (this.scoreObjectives.size() >= 64 && !this.scoreObjectives.containsKey(name)) {
                  String oldest = this.scoreObjectives.keySet().iterator().next();
                  this.scoreObjectives.remove(oldest);
                  this.scoresByObjective.remove(oldest);
                  this.displayedObjectives.values().removeIf(oldest::equals);
               }

               this.scoreObjectives.put(name, new MultiSession.ScoreObjective(title, packet.getNumberFormat()));
            }
         }
      }
   }

   private void trackDisplayObjective(ClientboundSetDisplayObjectivePacket packet) {
      DisplaySlot slot = packet.getSlot();
      if (slot != null) {
         String objective = packet.getObjectiveName();
         synchronized (this.scoreboardLock) {
            if (objective != null && !objective.isBlank()) {
               this.displayedObjectives.put(slot, objective);
            } else {
               this.displayedObjectives.remove(slot);
            }
         }
      }
   }

   private void trackScore(ClientboundSetScorePacket packet) {
      String objective = packet.objectiveName();
      String owner = packet.owner();
      if (objective != null && !objective.isBlank() && owner != null && !owner.isBlank()) {
         String display = packet.display().<String>map(Component::getString).orElse(owner);
         synchronized (this.scoreboardLock) {
            if (this.scoresByObjective.size() >= 64 && !this.scoresByObjective.containsKey(objective)) {
               String oldest = this.scoresByObjective.keySet().iterator().next();
               this.scoresByObjective.remove(oldest);
               this.scoreObjectives.remove(oldest);
               this.displayedObjectives.values().removeIf(oldest::equals);
            }

            Map<String, MultiSession.TrackedScore> scores = this.scoresByObjective.computeIfAbsent(objective, ignored -> new LinkedHashMap<>());
            if (scores.size() >= 2048 && !scores.containsKey(owner)) {
               scores.remove(scores.keySet().iterator().next());
            }

            scores.put(owner, new MultiSession.TrackedScore(packet.score(), display, packet.numberFormat()));
         }
      }
   }

   private void trackTeam(ClientboundSetPlayerTeamPacket packet) {
      String teamName = packet.getName();
      if (teamName != null && !teamName.isBlank()) {
         synchronized (this.scoreboardLock) {
            if (packet.getTeamAction() == net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.REMOVE) {
               this.scoreTeams.remove(teamName);
               this.scoreOwnerTeams.entrySet().removeIf(entry -> teamName.equals(entry.getValue()));
            } else {
               packet.getParameters()
                  .ifPresent(
                     parameters -> {
                        if (this.scoreTeams.size() >= 256 && !this.scoreTeams.containsKey(teamName)) {
                           String oldest = this.scoreTeams.keySet().iterator().next();
                           this.scoreTeams.remove(oldest);
                           this.scoreOwnerTeams.entrySet().removeIf(entry -> oldest.equals(entry.getValue()));
                        }

                        this.scoreTeams
                           .put(
                              teamName,
                              new MultiSession.ScoreTeam(componentText(parameters.playerPrefix()), componentText(parameters.playerSuffix()), parameters.color())
                           );
                     }
                  );
               net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();
               if (playerAction == net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.ADD) {
                  for (String owner : packet.getPlayers()) {
                     if (owner != null && !owner.isBlank() && (this.scoreOwnerTeams.size() < 4096 || this.scoreOwnerTeams.containsKey(owner))) {
                        this.scoreOwnerTeams.put(owner, teamName);
                     }
                  }
               } else if (playerAction == net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                  for (String ownerx : packet.getPlayers()) {
                     this.scoreOwnerTeams.remove(ownerx, teamName);
                  }
               }
            }
         }
      }
   }

   private static String componentText(Component component) {
      return component == null ? "" : component.getString();
   }

   private void resetScore(ClientboundResetScorePacket packet) {
      String owner = packet.owner();
      String objective = packet.objectiveName();
      synchronized (this.scoreboardLock) {
         if (objective != null && !objective.isBlank()) {
            Map<String, MultiSession.TrackedScore> scores = this.scoresByObjective.get(objective);
            if (scores != null) {
               scores.remove(owner);
            }
         } else {
            for (Map<String, MultiSession.TrackedScore> scores : this.scoresByObjective.values()) {
               scores.remove(owner);
            }
         }
      }
   }

   private void clearScoreboardState() {
      synchronized (this.scoreboardLock) {
         this.scoreObjectives.clear();
         this.scoresByObjective.clear();
         this.displayedObjectives.clear();
         this.scoreTeams.clear();
         this.scoreOwnerTeams.clear();
      }
   }

   public String sendConsoleLine(String line) {
      String value = line == null ? "" : line.trim();
      if (value.isEmpty()) {
         return "Empty input";
      } else if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (!this.reserveUserSend()) {
         return "Rate limited";
      } else {
         return value.startsWith("/") ? this.sendCommand(value.substring(1)) : this.sendChat(value);
      }
   }

   private String sendChat(String content) {
      if (content.length() > 256) {
         return "Chat exceeds 256 characters";
      } else {
         ServerboundChatPacket packet;
         synchronized (this.chatStateLock) {
            Instant now = Instant.now();
            long salt = SaltSupplier.getLong();
            Update update = this.lastSeenMessages.generateAndApplyUpdate();
            MessageSignature signature = this.signedEncoder.pack(new SignedMessageBody(content, now, salt, update.lastSeen()));
            packet = new ServerboundChatPacket(content, now, salt, signature, update.update());
         }

         return this.send(packet, false, false) ? "Sent" : "Blocked by packet policy";
      }
   }

   private String sendCommand(String command) {
      if (command.isBlank()) {
         return "Empty command";
      } else if (!this.hasCommandTree) {
         return this.send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
      } else {
         SignableCommand<Object> signable = SignableCommand.of(this.commands.parse(command, this.commandSource));
         if (signable.arguments().isEmpty()) {
            return this.send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
         } else {
            ServerboundChatCommandSignedPacket packet;
            synchronized (this.chatStateLock) {
               if (this.chatSession == null) {
                  return this.send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
               }

               Instant now = Instant.now();
               long salt = SaltSupplier.getLong();
               Update update = this.lastSeenMessages.generateAndApplyUpdate();
               ArgumentSignatures signatures = ArgumentSignatures.signCommand(
                  signable, argument -> this.signedEncoder.pack(new SignedMessageBody(argument, now, salt, update.lastSeen()))
               );
               packet = new ServerboundChatCommandSignedPacket(command, now, salt, signatures, update.update());
            }

            return this.send(packet, false, false) ? "Sent" : "Blocked by packet policy";
         }
      }
   }

   public String sendManual(Class<? extends Packet<?>> packetClass, String arguments) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (!MultiManualPackets.isSafe(packetClass)) {
         return "Packet is not headless-safe";
      } else if (!this.reserveUserSend()) {
         return "Rate limited";
      } else {
         RiptidePacketArgumentBuilder.Result result = RiptidePacketArgumentBuilder.build(packetClass, arguments);
         if (result.ok() && result.packet() != null) {
            return this.send(result.packet(), false, result.packet() instanceof ServerboundMovePlayerPacket) ? "Sent" : "Blocked by packet policy";
         } else {
            return result.message();
         }
      }
   }

   private String containerPrecheck() {
      return this.status.get() != MultiSession.Status.READY ? "Session is not ready" : "";
   }

   public String clickSlot(int handlerSlot, int button, ContainerInput input) {
      return this.enqueueClick(handlerSlot, button, input, false, 1);
   }

   private String enqueueClick(int handlerSlot, int button, ContainerInput input, boolean allowSynthetic) {
      return this.enqueueClick(handlerSlot, button, input, allowSynthetic, 1);
   }

   private String enqueueClick(int handlerSlot, int button, ContainerInput input, boolean allowSynthetic, int repetitions) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (input == null) {
         return "Invalid click";
      } else if (repetitions >= 1 && repetitions <= 100000) {
         synchronized (this.menuLock) {
            label110: {
               if (this.menuInteractive
                     && !this.clickSyncBlocked
                     && this.menuPhase != MultiSession.MenuPhase.SYNCING
                     && (this.menuPhase != MultiSession.MenuPhase.SYNTHETIC || allowSynthetic)
                  || allowSynthetic && this.menuPhase == MultiSession.MenuPhase.SYNTHETIC && !this.clickSyncBlocked) {
                  int limit = this.openContainerId < 0 ? 46 : this.menuSlots.size();
                  if (handlerSlot == -999 || handlerSlot >= 0 && handlerSlot < limit) {
                     if (this.clickQueue.size() + (this.clickInFlight == null ? 0 : 1) >= 128) {
                        return "Inventory click queue is full";
                     }

                     MultiSession.ClickTicket ticket = new MultiSession.ClickTicket(++this.clickSeq, this.menuEpoch);
                     this.clickQueue
                        .addLast(
                           new MultiSession.QueuedClick(
                              ticket, this.menuEpoch, this.currentContainerId(), handlerSlot, button, input, allowSynthetic, null, repetitions
                           )
                        );
                     this.menuRevision++;
                     break label110;
                  }

                  return "Invalid slot";
               }

               return "Inventory is synchronizing";
            }
         }

         this.pumpClickTransactions(System.currentTimeMillis());
         return repetitions == 1 ? "Sent" : "Queued " + repetitions + " clicks";
      } else {
         return "Click count must be 1-100000";
      }
   }

   private String enqueuePacket(Packet<?> packet) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (packet == null) {
         return "Invalid packet";
      } else {
         synchronized (this.menuLock) {
            if (!this.menuInteractive
               || this.clickSyncBlocked
               || this.menuPhase == MultiSession.MenuPhase.SYNCING
               || this.menuPhase == MultiSession.MenuPhase.SYNTHETIC) {
               return "Inventory is synchronizing";
            }

            if (this.clickQueue.size() + (this.clickInFlight == null ? 0 : 1) >= 128) {
               return "Inventory click queue is full";
            }

            MultiSession.ClickTicket ticket = new MultiSession.ClickTicket(++this.clickSeq, this.menuEpoch);
            this.clickQueue
               .addLast(new MultiSession.QueuedClick(ticket, this.menuEpoch, this.currentContainerId(), -1, 0, ContainerInput.PICKUP, false, packet, 1));
            this.menuRevision++;
         }

         this.pumpClickTransactions(System.currentTimeMillis());
         return "Sent";
      }
   }

   public String buttonClick(int buttonId) {
      return this.enqueuePacket(new ServerboundContainerButtonClickPacket(this.currentContainerId(), buttonId));
   }

   public String selectTrade(int index) {
      return this.enqueuePacket(new ServerboundSelectTradePacket(index));
   }

   public String setBeacon(int primaryId, int secondaryId) {
      return this.enqueuePacket(new ServerboundSetBeaconPacket(beaconEffect(primaryId), beaconEffect(secondaryId)));
   }

   private static Optional<Holder<MobEffect>> beaconEffect(int id) {
      return id < 0 ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.get(id).map(h -> (Holder<MobEffect>)h);
   }

   public String placeRecipe(RecipeDisplayId id, boolean placeAll) {
      return this.enqueuePacket(new ServerboundPlaceRecipePacket(this.currentContainerId(), id, placeAll));
   }

   public String renameItem(String name) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else {
         String clamped = name == null ? "" : (name.length() > 50 ? name.substring(0, 50) : name);
         return this.send(new ServerboundRenameItemPacket(clamped), false, false) ? "Sent" : "Blocked by packet policy";
      }
   }

   private boolean enqueueAutomatedClick(int handlerSlot, int button, ContainerInput input) {
      return "Sent".equals(this.enqueueClick(handlerSlot, button, input, false));
   }

   private boolean clickSlotRaw(int handlerSlot, int button, ContainerInput input) {
      return "Sent".equals(this.enqueueClick(handlerSlot, button, input, true));
   }

   private boolean hasPendingClicks() {
      synchronized (this.menuLock) {
         return this.clickInFlight != null || !this.clickQueue.isEmpty();
      }
   }

   @Override
   public MultiMacroHost.InventorySyncState inventorySyncState() {
      synchronized (this.menuLock) {
         if (this.clickSyncBlocked) {
            return MultiMacroHost.InventorySyncState.BLOCKED;
         } else {
            return this.menuPhase != MultiSession.MenuPhase.SYNCING && this.clickInFlight == null && this.clickQueue.isEmpty()
               ? MultiMacroHost.InventorySyncState.READY
               : MultiMacroHost.InventorySyncState.WAITING;
         }
      }
   }

   private void pumpClickTransactions(long now) {
      while (true) {
         Packet<?> packet;
         synchronized (this.menuLock) {
            if (this.clickInFlight != null) {
               boolean settled = this.clickSawUpdate && now - this.clickLastUpdateAt >= 50L;
               boolean noOp = !this.clickSawUpdate && now - this.clickSentAt >= noOpGraceMillis(this.ping);
               boolean overdue = now - this.clickSentAt >= clickTimeoutMillis(this.ping);
               if (!settled && !noOp && !overdue) {
                  return;
               }

               MultiSession.QueuedClick completed = this.clickInFlight;
               if (completed.repetitionsRemaining() > 1) {
                  completed.ticket().completion = MultiSession.ClickCompletion.QUEUED;
                  this.clickQueue
                     .addFirst(
                        new MultiSession.QueuedClick(
                           completed.ticket(),
                           completed.epoch(),
                           completed.containerId(),
                           completed.slot(),
                           completed.button(),
                           completed.input(),
                           completed.allowSynthetic(),
                           completed.rawPacket(),
                           completed.repetitionsRemaining() - 1
                        )
                     );
               } else {
                  completed.ticket().completion = MultiSession.ClickCompletion.COMPLETE;
               }

               this.clickInFlight = null;
               this.clickSawUpdate = false;
               this.menuRevision++;
            }

            MultiSession.QueuedClick sending = this.clickQueue.pollFirst();
            if (sending == null) {
               return;
            }

            if (sending.epoch() != this.menuEpoch
               || sending.containerId() != this.currentContainerId()
               || this.menuPhase == MultiSession.MenuPhase.SYNCING
               || !this.menuInteractive && (!sending.allowSynthetic() || this.menuPhase != MultiSession.MenuPhase.SYNTHETIC)
               || this.menuPhase == MultiSession.MenuPhase.SYNTHETIC && !sending.allowSynthetic()) {
               sending.ticket().completion = MultiSession.ClickCompletion.CANCELLED;
               continue;
            }

            if (sending.rawPacket() == null && this.isObviousNoOpLocked(sending)) {
               sending.ticket().completion = MultiSession.ClickCompletion.COMPLETE;
               this.menuRevision++;
               continue;
            }

            if (sending.rawPacket() != null) {
               packet = sending.rawPacket();
            } else {
               int stateId = this.openContainerId < 0 ? this.inventoryStateId : this.containerStateId;
               packet = new ServerboundContainerClickPacket(
                  sending.containerId(),
                  stateId,
                  (short)sending.slot(),
                  (byte)sending.button(),
                  sending.input(),
                  new Int2ObjectOpenHashMap(),
                  hashOf(this.carried, this.itemHasher())
               );
            }

            this.clickInFlight = sending;
            sending.ticket().completion = MultiSession.ClickCompletion.SENT;
            this.clickSentAt = now;
            this.clickLastUpdateAt = now;
            this.clickSawUpdate = sending.input() == ContainerInput.QUICK_CRAFT;
            if (this.clickSawUpdate) {
               sending.ticket().completion = MultiSession.ClickCompletion.SETTLING;
            }
         }

         if (this.send(packet, false, false)) {
            return;
         }

         synchronized (this.menuLock) {
            this.cancelClickTransactionsLocked(MultiSession.ClickCompletion.FAILED);
            this.menuRevision++;
            return;
         }
      }
   }

   static long clickTimeoutMillis(int pingMs) {
      return Math.max(1000L, Math.min(5000L, Math.max(0, pingMs) * 4L + 500L));
   }

   static long noOpGraceMillis(int pingMs) {
      return Math.max(200L, Math.min(clickTimeoutMillis(pingMs), Math.max(0, pingMs) * 3L + 200L));
   }

   private void sendDeferredCloseIfReady() {
      MultiSession.DeferredMenuClose close;
      int containerId;
      synchronized (this.menuLock) {
         if (this.closeAfterClicks == MultiSession.DeferredMenuClose.NONE || this.clickInFlight != null || !this.clickQueue.isEmpty()) {
            return;
         }

         close = this.closeAfterClicks;
         this.closeAfterClicks = MultiSession.DeferredMenuClose.NONE;
         containerId = this.currentContainerId();
      }

      if (close == MultiSession.DeferredMenuClose.SILENT) {
         this.dismissContainerLocally();
      } else {
         if (this.send(new ServerboundContainerClosePacket(containerId), false, false)) {
            if (close == MultiSession.DeferredMenuClose.DESYNC) {
               this.markSyntheticMenu();
            } else {
               if (this.openContainerId >= 0) {
                  this.syncPlayerInvFromMenu();
               }

               this.invalidateMenu(false, true);
            }
         }
      }
   }

   private boolean isObviousNoOpLocked(MultiSession.QueuedClick click) {
      List<ItemStack> authoritativeSlots = this.openContainerId < 0 ? this.playerInv : this.menuSlots;
      ItemStack slot = click.slot() >= 0 && click.slot() < authoritativeSlots.size() ? nonNull(authoritativeSlots.get(click.slot())) : ItemStack.EMPTY;

      return switch (click.input()) {
         case PICKUP -> slot.isEmpty() && nonNull(this.carried).isEmpty();
         case QUICK_MOVE, THROW -> slot.isEmpty();
         case CLONE -> slot.isEmpty() || this.gameModeId != 1;
         case PICKUP_ALL -> this.pickupAllHasNoTargetLocked(authoritativeSlots);
         case SWAP -> this.swapHasNoEffectLocked(click, slot);
         default -> false;
      };
   }

   private boolean pickupAllHasNoTargetLocked(List<ItemStack> slots) {
      ItemStack cursor = nonNull(this.carried);
      if (!cursor.isEmpty() && cursor.getCount() < cursor.getMaxStackSize()) {
         for (ItemStack candidate : slots) {
            if (!nonNull(candidate).isEmpty() && ItemStack.isSameItemSameComponents(cursor, candidate)) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   private boolean swapHasNoEffectLocked(MultiSession.QueuedClick click, ItemStack slot) {
      int inventoryHandler = click.button() >= 0 && click.button() <= 8 ? 36 + click.button() : (click.button() == 40 ? 45 : -1);
      if (inventoryHandler >= 0 && inventoryHandler < this.playerInv.size()) {
         return this.openContainerId < 0 && click.slot() == inventoryHandler ? true : slot.isEmpty() && nonNull(this.playerInv.get(inventoryHandler)).isEmpty();
      } else {
         return false;
      }
   }

   private static HashedStack hashOf(ItemStack stack, HashGenerator hasher) {
      if (stack == null || stack.isEmpty()) {
         return HashedStack.EMPTY;
      } else if (hasher == null) {
         return HashedStack.EMPTY;
      } else {
         try {
            return HashedStack.create(stack, hasher);
         } catch (RuntimeException var3) {
            return HashedStack.EMPTY;
         }
      }
   }

   private HashGenerator itemHasher() {
      Frozen reg = this.registries;
      if (reg == null) {
         return null;
      } else {
         HashGenerator cached = this.itemHasher;
         if (cached != null && this.itemHasherFor == reg) {
            return cached;
         } else {
            RegistryOps<HashCode> ops = reg.createSerializationContext(HashOps.CRC32C_INSTANCE);
            HashGenerator built = component -> ((HashCode)component.encodeValue(ops)
                  .getOrThrow(msg -> new IllegalArgumentException("Failed to hash " + component + ": " + msg)))
               .asInt();
            this.itemHasher = built;
            this.itemHasherFor = reg;
            return built;
         }
      }
   }

   public int visibleToHandler(int visible) {
      if (this.openContainerId < 0) {
         if (visible >= 100 && visible <= 104) {
            return visible - 100;
         } else if (visible >= 0 && visible <= 8) {
            return 36 + visible;
         } else if (visible >= 9 && visible <= 35) {
            return visible;
         } else if (visible >= 36 && visible <= 39) {
            return 44 - visible;
         } else {
            return visible == 40 ? 45 : -1;
         }
      } else {
         int size;
         synchronized (this.menuLock) {
            size = this.menuSlots.size();
         }

         int base = size - 36;
         if (visible >= 100) {
            int gui = visible - 100;
            return gui < base ? gui : -1;
         } else if (visible >= 0 && visible <= 8) {
            return base + 27 + visible;
         } else {
            return visible >= 9 && visible <= 35 ? base + (visible - 9) : -1;
         }
      }
   }

   public String dropFullInventory() {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (!this.reserveUserSend()) {
         return "Rate limited";
      } else {
         int dropped = 0;
         synchronized (this.menuLock) {
            int size = this.menuSlots.size();
            int from = this.openContainerId < 0 ? 9 : Math.max(0, size - 36);
            int to = this.openContainerId < 0 ? Math.min(45, size) : size;

            for (int handler = from; handler < to; handler++) {
               ItemStack stack = this.menuSlots.get(handler);
               if (stack != null && !stack.isEmpty() && this.clickSlotRaw(handler, 1, ContainerInput.THROW)) {
                  dropped++;
               }
            }
         }

         return dropped > 0 ? "Sent" : "Nothing to drop";
      }
   }

   public String selectHotbar(int slot0to8) {
      String pre = this.containerPrecheck();
      if (!pre.isBlank()) {
         return pre;
      } else {
         int slot = Math.max(0, Math.min(8, slot0to8));
         boolean ok = this.send(new ServerboundSetCarriedItemPacket(slot), false, false);
         if (ok) {
            this.selectedHotbar = slot;
            this.lastWireHotbar = slot;
            this.recomputeHeldItem();
         }

         return ok ? "Sent" : "Blocked by packet policy";
      }
   }

   public String giveCreative(ItemStack stack) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (this.gameModeId != 1) {
         return "Creative mode required";
      } else if (stack != null && !stack.isEmpty()) {
         int handler = 36 + Math.max(0, Math.min(8, this.selectedHotbar));
         return this.send(new ServerboundSetCreativeModeSlotPacket((short)handler, stack.copy()), false, false) ? "Sent" : "Blocked by packet policy";
      } else {
         return "Invalid item";
      }
   }

   public String stashSelectedXCarry(int targetHandlerSlot) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else if (this.openContainerId >= 0) {
         return "Close the bot container first";
      } else {
         int source = 36 + Math.max(0, Math.min(8, this.selectedHotbar));
         synchronized (this.menuLock) {
            if (!this.inventorySynchronized || !this.menuInteractive || this.clickSyncBlocked) {
               return "Inventory is synchronizing";
            }

            if (source >= this.playerInv.size() || nonNull(this.playerInv.get(source)).isEmpty()) {
               return "Hold an item first";
            }

            if (!nonNull(this.carried).isEmpty()) {
               return "Bot cursor is not empty";
            }

            if (targetHandlerSlot != Integer.MIN_VALUE) {
               if (targetHandlerSlot < 0 || targetHandlerSlot >= this.playerInv.size()) {
                  return "Invalid XCarry slot";
               }

               if (!nonNull(this.playerInv.get(targetHandlerSlot)).isEmpty()) {
                  return "XCarry slot is occupied";
               }
            }

            this.xCarryForced = true;
            this.xCarryActive = true;
         }

         String pickup = this.enqueueClick(source, 0, ContainerInput.PICKUP, false);
         if ("Sent".equals(pickup) && targetHandlerSlot != Integer.MIN_VALUE) {
            String place = this.enqueueClick(targetHandlerSlot, 0, ContainerInput.PICKUP, false);
            return "Sent".equals(place) ? "Sent" : place;
         } else {
            return pickup;
         }
      }
   }

   public String dropSelected(boolean all) {
      String pre = this.containerPrecheck();
      if (!pre.isBlank()) {
         return pre;
      } else {
         net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action = all
            ? net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
            : net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ITEM;
         boolean ok = this.send(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN), false, false);
         return ok ? "Sent" : "Blocked by packet policy";
      }
   }

   public String closeContainer() {
      String pre = this.containerPrecheck();
      if (!pre.isBlank()) {
         return pre;
      } else {
         synchronized (this.menuLock) {
            if (this.clickInFlight != null || !this.clickQueue.isEmpty()) {
               this.closeAfterClicks = MultiSession.DeferredMenuClose.PACKET;
               return "Sent";
            }
         }

         boolean ok = this.send(new ServerboundContainerClosePacket(this.currentContainerId()), false, false);
         if (ok) {
            if (this.openContainerId >= 0) {
               this.syncPlayerInvFromMenu();
            }

            this.invalidateMenu(false, true);
         }

         return ok ? "Sent" : "Blocked by packet policy";
      }
   }

   public String useItem() {
      String pre = this.containerPrecheck();
      if (!pre.isBlank()) {
         return pre;
      } else {
         PositionMoveRotation current = this.position;
         return this.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, ++this.useSeq, current.yRot(), current.xRot()), false, false)
            ? "Sent"
            : "Blocked by packet policy";
      }
   }

   public String swingArm() {
      String pre = this.containerPrecheck();
      if (!pre.isBlank()) {
         return pre;
      } else {
         return this.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false) ? "Sent" : "Blocked by packet policy";
      }
   }

   public int findSlotByItem(String query) {
      if (query != null && !query.isBlank()) {
         String needle = query.trim().toLowerCase(Locale.ROOT);
         synchronized (this.menuLock) {
            for (int i = 0; i < this.menuSlots.size(); i++) {
               ItemStack stack = this.menuSlots.get(i);
               if (stack != null && !stack.isEmpty()) {
                  String hover = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                  Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                  if (hover.contains(needle)) {
                     return i;
                  }

                  if (id != null && (id.getPath().equals(needle) || id.toString().equals(needle))) {
                     return i;
                  }
               }
            }

            return -1;
         }
      } else {
         return -1;
      }
   }

   @Override
   public List<int[]> resolveItemClicks(ItemAction a) {
      List<int[]> plan = new ArrayList<>();
      if (a == null) {
         return plan;
      } else {
         synchronized (this.menuLock) {
            int size = this.menuSlots.size();
            if (size == 0) {
               return plan;
            } else {
               List<ItemTarget> targets = !a.itemTargets.isEmpty() ? a.itemTargets : legacyItemTargets(a.itemNames);
               if (!targets.isEmpty()) {
                  for (int ei = 0; ei < targets.size() && plan.size() < 256; ei++) {
                     ItemTarget target = targets.get(ei);
                     if (target != null && (target.hasIdentity() || target.hasSlot())) {
                        RiptideDropAction action = a.getItemAction(ei);
                        int button = a.getItemButton(ei);
                        if (action != RiptideDropAction.QUICK_MOVE && action.usesFixedButton()) {
                           button = action.getButton();
                        }

                        int slot;
                        if (action == RiptideDropAction.PICKUP_ALL && a.useCursorItemForPickupAll) {
                           slot = this.cursorGatherSlot(size);
                        } else {
                           slot = this.resolveItemEntrySlot(a, target, ei, size);
                        }

                        if (slot >= 0) {
                           addItemClicks(plan, slot, button, action, clampItemCount(a.getItemTime(ei), action));
                        }
                     }
                  }
               } else if (a.useSlot && a.targetSlot >= 0) {
                  RiptideDropAction actionx = a.getAction();
                  int buttonx = a.getButton();
                  if (actionx != RiptideDropAction.QUICK_MOVE && actionx.usesFixedButton()) {
                     buttonx = actionx.getButton();
                  }

                  int slotx = actionx == RiptideDropAction.PICKUP_ALL && a.useCursorItemForPickupAll
                     ? this.cursorGatherSlot(size)
                     : this.visibleToHandler(a.targetSlot);
                  if (slotx >= 0 && slotx < size) {
                     addItemClicks(plan, slotx, buttonx, actionx, clampItemCount(a.times, actionx));
                  }
               }

               return plan;
            }
         }
      }
   }

   @Override
   public void clickResolved(int handlerSlot, int button, int containerInputOrdinal) {
      ContainerInput[] all = ContainerInput.values();
      if (containerInputOrdinal >= 0 && containerInputOrdinal < all.length) {
         this.clickSlot(handlerSlot, button, all[containerInputOrdinal]);
      }
   }

   @Override
   public boolean sendPacketBurst(PacketBurstAction action) {
      if (action != null && action.mode != null) {
         return switch (action.mode) {
            case CONTAINER_CLICK -> this.clickSlotRaw(action.slot, action.button, containerInput(action.containerInput));
            case ENTITY_INTERACT -> {
               if (action.entityId < 0) {
                  yield false;
               } else {
                  this.lastInteractEntityId = action.entityId;
                  this.lastInteractBlock = null;
                  yield this.send(
                     new ServerboundInteractPacket(
                        action.entityId, "OFF_HAND".equalsIgnoreCase(action.hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, Vec3.ZERO, false
                     ),
                     false,
                     false
                  );
               }
            }
            case BUNDLE_SELECT -> this.send(
               new ServerboundSelectBundleItemPacket(action.slot >= 0 ? action.slot : 36 + this.selectedHotbar, action.bundleIndex), false, false
            );
            case USE_ITEM -> {
               InteractionHand hand = "OFF_HAND".equalsIgnoreCase(action.hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
               PositionMoveRotation current = this.position;
               yield this.send(new ServerboundUseItemPacket(hand, ++this.useSeq, current.yRot(), current.xRot()), false, false);
            }
            case RELEASE_ITEM -> this.send(
               new ServerboundPlayerActionPacket(
                  net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
               ),
               false,
               false
            );
            case SET_CARRIED_ITEM -> "Sent".equals(this.selectHotbar(Math.max(0, Math.min(8, action.carriedSlot))));
            case CLIENT_INFORMATION -> this.send(new ServerboundClientInformationPacket(this.clientInformation), false, false);
            case CLOSE_CONTAINER -> this.closeContainerBurst(action.containerId);
            case CLIENT_COMMAND -> false;
         };
      } else {
         return false;
      }
   }

   private boolean closeContainerBurst(int containerId) {
      boolean closesTrackedContainer = containerId == this.currentContainerId();
      if (closesTrackedContainer) {
         synchronized (this.menuLock) {
            if (this.clickInFlight != null || !this.clickQueue.isEmpty()) {
               this.closeAfterClicks = MultiSession.DeferredMenuClose.PACKET;
               return true;
            }
         }
      }

      boolean sent = this.send(new ServerboundContainerClosePacket(containerId), false, false);
      if (sent && closesTrackedContainer) {
         if (this.openContainerId >= 0 && this.menuPhase != MultiSession.MenuPhase.SYNTHETIC) {
            this.syncPlayerInvFromMenu();
         }

         this.invalidateMenu(false, true);
      }

      return sent;
   }

   private static ContainerInput containerInput(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return ContainerInput.valueOf(value.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return ContainerInput.PICKUP;
         }
      } else {
         return ContainerInput.PICKUP;
      }
   }

   @Override
   public int writeBook(List<String> pages, String title, boolean sign, boolean requireHeld, int excludedHotbarMask) {
      if (pages != null && !pages.isEmpty()) {
         int hotbar = -1;
         synchronized (this.menuLock) {
            int selected = Math.max(0, Math.min(8, this.selectedHotbar));
            if (requireHeld) {
               int handler = 36 + selected;
               if ((excludedHotbarMask & 1 << selected) != 0 || handler >= this.playerInv.size() || !isWritableBook(this.playerInv.get(handler))) {
                  return -1;
               }

               hotbar = selected;
            } else {
               int bookHandler = -1;

               for (int h = 36; h <= 44 && h < this.playerInv.size(); h++) {
                  int slot = h - 36;
                  if ((excludedHotbarMask & 1 << slot) == 0 && isWritableBook(this.playerInv.get(h))) {
                     bookHandler = h;
                     break;
                  }
               }

               if (bookHandler >= 0) {
                  hotbar = bookHandler - 36;
               } else {
                  for (int hx = 9; hx <= 35 && hx < this.playerInv.size(); hx++) {
                     if (isWritableBook(this.playerInv.get(hx))) {
                        hotbar = this.availableBookHotbar(excludedHotbarMask);
                        if (hotbar < 0) {
                           return -1;
                        }

                        int liveHandler = this.visibleToHandler(hx);
                        if (liveHandler >= 0 && this.clickSlotRaw(liveHandler, hotbar, ContainerInput.SWAP)) {
                           return -2;
                        }

                        return -1;
                     }
                  }

                  if (bookHandler < 0) {
                     return -1;
                  }
               }
            }
         }

         if (hotbar < 0) {
            return -1;
         } else {
            List<String> bounded = pages.stream().limit(100L).map(page -> page == null ? "" : page).toList();
            Optional<String> signedTitle = sign ? Optional.of(MultiManager.singleLine(title, 32)) : Optional.empty();
            return this.send(new ServerboundEditBookPacket(hotbar, bounded, signedTitle), false, false) ? hotbar : -1;
         }
      } else {
         return -1;
      }
   }

   private int availableBookHotbar(int excludedMask) {
      for (int slot = 0; slot < 9; slot++) {
         if ((excludedMask & 1 << slot) == 0 && this.playerInv.get(36 + slot).isEmpty()) {
            return slot;
         }
      }

      for (int slotx = 0; slotx < 9; slotx++) {
         if ((excludedMask & 1 << slotx) == 0) {
            return slotx;
         }
      }

      return -1;
   }

   private static boolean isWritableBook(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         return id != null && "minecraft:writable_book".equals(id.toString());
      } else {
         return false;
      }
   }

   private int cursorGatherSlot(int size) {
      if (this.carried != null && !this.carried.isEmpty()) {
         for (int h = 0; h < size; h++) {
            ItemStack stack = this.menuSlots.get(h);
            if (stack == null || stack.isEmpty()) {
               return h;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static int clampItemCount(int raw, RiptideDropAction action) {
      return action == RiptideDropAction.DROP_STACK ? 1 : Math.max(1, Math.min(64, raw));
   }

   private static void addItemClicks(List<int[]> plan, int slot, int button, RiptideDropAction action, int count) {
      ContainerInput input;
      try {
         input = action.toContainerInput();
      } catch (RuntimeException var8) {
         return;
      }

      int ord = input.ordinal();

      for (int i = 0; i < count && plan.size() < 256; i++) {
         plan.add(new int[]{slot, button, ord});
      }
   }

   private static List<ItemTarget> legacyItemTargets(List<String> names) {
      List<ItemTarget> out = new ArrayList<>();
      if (names == null) {
         return out;
      } else {
         for (String n : names) {
            ItemTarget t = ItemTarget.fromLegacyEntry(n);
            if (t.hasSlot() || t.hasIdentity()) {
               out.add(t);
            }
         }

         return out;
      }
   }

   private int resolveItemEntrySlot(ItemAction a, ItemTarget target, int ei, int size) {
      int configuredSlot = target.hasSlot() ? target.slot : -1;
      if (configuredSlot >= 0 && target.hasIdentity()) {
         return this.findExactItemSlot(target, this.visibleToHandler(configuredSlot), size);
      } else if (configuredSlot < 0) {
         return a.useSlot && a.targetSlot >= 0
            ? this.findExactItemSlot(target, this.visibleToHandler(a.targetSlot), size)
            : this.findMatchingItemSlot(target, a.getItemSearchScope(ei), a.getStackAmountMode(ei), size);
      } else {
         int h = this.visibleToHandler(configuredSlot);
         return h >= 0 && h < size ? h : -1;
      }
   }

   private int findExactItemSlot(ItemTarget target, int handler, int size) {
      if (handler >= 0 && handler < size) {
         ItemStack stack = this.menuSlots.get(handler);
         if (stack == null || stack.isEmpty()) {
            return -1;
         } else if (!target.hasIdentity()) {
            return handler;
         } else {
            return target.matches(stack, this.handlerToVisible(handler, size)) ? handler : -1;
         }
      } else {
         return -1;
      }
   }

   private int findMatchingItemSlot(ItemTarget target, ItemAction.ItemSearchScope scope, ItemAction.StackAmountMode mode, int size) {
      if (!target.hasIdentity() && !target.hasSlot()) {
         return -1;
      } else {
         ItemAction.StackAmountMode m = mode == null ? ItemAction.StackAmountMode.DEFAULT : mode;
         ItemAction.ItemSearchScope sc = scope == null ? ItemAction.ItemSearchScope.BOTH : scope;
         int base = this.openContainerId >= 0 ? size - 36 : -1;
         int bestSlot = -1;
         int bestScore = -1;
         int bestCount = m == ItemAction.StackAmountMode.LEAST ? Integer.MAX_VALUE : -1;

         for (int h = 0; h < size; h++) {
            ItemStack stack = this.menuSlots.get(h);
            if (stack != null && !stack.isEmpty()) {
               boolean playerInv = base < 0 || h >= base;
               if ((sc != ItemAction.ItemSearchScope.GUI || base >= 0 && !playerInv) && (sc != ItemAction.ItemSearchScope.PLAYER_INVENTORY || playerInv)) {
                  int score = target.score(stack, this.handlerToVisible(h, size));
                  if (score >= 0) {
                     int count = stack.getCount();
                     if (isBetterItemCandidate(m, score, count, bestScore, bestCount)) {
                        bestScore = score;
                        bestCount = count;
                        bestSlot = h;
                     }
                  }
               }
            }
         }

         return bestScore >= 0 ? bestSlot : -1;
      }
   }

   private static boolean isBetterItemCandidate(ItemAction.StackAmountMode mode, int score, int count, int bestScore, int bestCount) {
      if (bestScore < 0) {
         return true;
      } else if (mode == ItemAction.StackAmountMode.LEAST) {
         return count != bestCount ? count < bestCount : score > bestScore;
      } else if (mode == ItemAction.StackAmountMode.MOST) {
         return count != bestCount ? count > bestCount : score > bestScore;
      } else {
         return score > bestScore;
      }
   }

   public int handlerToVisibleSlot(int handler) {
      if (handler < 0) {
         return -1;
      } else {
         int size;
         synchronized (this.menuLock) {
            size = this.menuSlots.size();
         }

         return this.handlerToVisible(handler, size);
      }
   }

   private int handlerToVisible(int handler, int size) {
      if (this.openContainerId < 0) {
         if (handler >= 0 && handler <= 4) {
            return 100 + handler;
         } else if (handler >= 36 && handler <= 44) {
            return handler - 36;
         } else if (handler >= 9 && handler <= 35) {
            return handler;
         } else if (handler >= 5 && handler <= 8) {
            return 44 - handler;
         } else {
            return handler == 45 ? 40 : handler;
         }
      } else {
         int base = size - 36;
         if (handler < base) {
            return 100 + handler;
         } else {
            return handler < base + 27 ? 9 + (handler - base) : handler - (base + 27);
         }
      }
   }

   @Override
   public List<int[]> resolveStoreClicks(StoreItemAction a) {
      List<int[]> plan = new ArrayList<>();
      if (a == null) {
         return plan;
      } else {
         synchronized (this.menuLock) {
            if (this.openContainerId < 0) {
               return plan;
            } else {
               int size = this.menuSlots.size();
               int base = size - 36;
               if (base < 0) {
                  return plan;
               } else {
                  int quickMove = ContainerInput.QUICK_MOVE.ordinal();
                  boolean loot = a.mode == StoreItemAction.Mode.LOOT;

                  for (int h = 0; h < size && plan.size() < 256; h++) {
                     ItemStack stack = this.menuSlots.get(h);
                     if (stack != null && !stack.isEmpty()) {
                        boolean playerInv = h >= base;
                        if ((!loot || !playerInv) && (loot || playerInv) && storeMatches(a, stack, this.handlerToVisible(h, size))) {
                           plan.add(new int[]{h, 0, quickMove});
                        }
                     }
                  }

                  return plan;
               }
            }
         }
      }
   }

   private static boolean storeMatches(StoreItemAction a, ItemStack stack, int visibleSlot) {
      if (a.allItems) {
         return true;
      } else {
         List<ItemTarget> targets = !a.itemTargets.isEmpty() ? a.itemTargets : legacyItemTargets(a.targetItems);
         if (targets.isEmpty()) {
            return false;
         } else {
            for (ItemTarget t : targets) {
               if (t != null && (t.hasSlot() || t.hasIdentity()) && t.matches(stack, visibleSlot)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public List<int[]> resolveSwapClicks(SwapSlotsAction a) {
      List<int[]> plan = new ArrayList<>();
      if (a == null) {
         return plan;
      } else {
         synchronized (this.menuLock) {
            int size = this.menuSlots.size();
            if (size == 0) {
               return plan;
            } else if (this.carried != null && !this.carried.isEmpty()) {
               return plan;
            } else {
               int from = this.resolveSwapEndpoint(a, true, size);
               int to = this.resolveSwapEndpoint(a, false, size);
               if (from >= 0 && to >= 0 && from != to) {
                  int pickup = ContainerInput.PICKUP.ordinal();
                  plan.add(new int[]{from, 0, pickup});
                  plan.add(new int[]{to, 0, pickup});
                  plan.add(new int[]{from, 0, pickup});
                  return plan;
               } else {
                  return plan;
               }
            }
         }
      }
   }

   private int resolveSwapEndpoint(SwapSlotsAction a, boolean isFrom, int size) {
      boolean useItem = isFrom ? a.fromUseItemName : a.toUseItemName;
      if (useItem) {
         ItemTarget t = isFrom ? a.fromItemTarget : a.toItemTarget;
         if (t != null && (t.hasIdentity() || t.hasSlot())) {
            return t.hasSlot() && !t.hasIdentity()
               ? validHandler(this.visibleToHandler(t.slot), size)
               : this.findMatchingItemSlot(t, ItemAction.ItemSearchScope.BOTH, ItemAction.StackAmountMode.DEFAULT, size);
         } else {
            return -1;
         }
      } else {
         int slot = isFrom ? a.fromSlot : a.toSlot;
         return slot >= 0 ? validHandler(this.visibleToHandler(slot), size) : -1;
      }
   }

   private static int validHandler(int handler, int size) {
      return handler >= 0 && handler < size ? handler : -1;
   }

   @Override
   public List<int[]> resolvePickupAllClicks(PickUpAllAction a) {
      List<int[]> plan = new ArrayList<>();
      if (a == null) {
         return plan;
      } else {
         synchronized (this.menuLock) {
            ItemStack cur = this.carried;
            if (cur != null && !cur.isEmpty()) {
               int size = this.menuSlots.size();
               int trigger = -1;
               boolean hasMatch = false;

               for (int h = 0; h < size; h++) {
                  ItemStack stack = this.menuSlots.get(h);
                  if (stack != null && !stack.isEmpty()) {
                     if (ItemStack.isSameItemSameComponents(stack, cur)) {
                        hasMatch = true;
                     }
                  } else if (trigger < 0) {
                     trigger = h;
                  }
               }

               if (trigger >= 0 && hasMatch) {
                  int pickupAll = ContainerInput.PICKUP_ALL.ordinal();
                  int times = Math.max(1, Math.min(64, a.times));

                  for (int i = 0; i < times; i++) {
                     plan.add(new int[]{trigger, 0, pickupAll});
                  }

                  return plan;
               } else {
                  return plan;
               }
            } else {
               return plan;
            }
         }
      }
   }

   @Override
   public List<int[]> resolveSequenceClicks(ContainerClickSequenceAction a) {
      List<int[]> plan = new ArrayList<>();
      if (a == null) {
         return plan;
      } else if (a.containerSource != ContainerClickSequenceAction.ContainerSource.CURRENT
         && a.containerSource != ContainerClickSequenceAction.ContainerSource.PLAYER_INVENTORY) {
         return plan;
      } else {
         int button = 0;

         try {
            button = Integer.parseInt(a.button.split(" ")[0]);
         } catch (RuntimeException var16) {
         }

         ContainerInput input;
         try {
            input = ContainerInput.valueOf(a.containerInput);
         } catch (RuntimeException var15) {
            input = ContainerInput.PICKUP;
         }

         int inputOrd = input.ordinal();
         List<Integer> visibleSlots = a.resolvedSlots();
         int repeat = Math.max(1, a.repeatCount);
         synchronized (this.menuLock) {
            int size = this.menuSlots.size();

            for (int r = 0; r < repeat && plan.size() < 256; r++) {
               for (int visible : visibleSlots) {
                  if (plan.size() < 256) {
                     int handler = this.visibleToHandler(visible);
                     if (handler >= 0 && handler < size) {
                        plan.add(new int[]{handler, button, inputOrd});
                     }
                     continue;
                  }
               }
            }

            return plan;
         }
      }
   }

   public String runClientAction(String name, String args) {
      if (this.status.get() != MultiSession.Status.READY) {
         return "Session is not ready";
      } else {
         String rest = args == null ? "" : args.trim();
         String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+");
         switch (name) {
            case "click-slot":
               if (parts.length < 1) {
                  return "Usage: click-slot <slot> [mode] [count]";
               } else {
                  int visible = parseVisibleSlot(parts[0]);
                  if (visible < 0) {
                     return "Bad slot: " + parts[0];
                  } else {
                     int handler = this.visibleToHandler(visible);
                     if (handler < 0) {
                        return "Slot not available in this menu";
                     } else {
                        MultiClientCommands.ClickSpec specx = MultiClientCommands.parseClick(parts.length > 1 ? parts[1] : "left");
                        if (specx == null) {
                           return "Bad click mode";
                        }

                        return this.repeatClick(handler, specx, parts.length > 2 ? parts[2] : null);
                     }
                  }
               }
            case "click-item":
               if (rest.isEmpty()) {
                  return "Usage: click-item <name> [mode] [count]";
               } else {
                  String[] split = splitQuotedFirst(rest);
                  String[] tail = split[1].isEmpty() ? new String[0] : split[1].split("\\s+");
                  MultiClientCommands.ClickSpec spec = MultiClientCommands.parseClick(tail.length > 0 ? tail[0] : "left");
                  if (spec == null) {
                     return "Bad click mode";
                  } else {
                     int slot = this.findSlotByItem(split[0]);
                     if (slot < 0) {
                        return "Item not found in the open menu";
                     }

                     return this.repeatClick(slot, spec, tail.length > 1 ? tail[1] : null);
                  }
               }
            case "change-slot":
               Integer n = parts.length > 0 ? parseIntOrNull(parts[0]) : null;
               if (n != null && n >= 1 && n <= 9) {
                  return this.selectHotbar(n - 1);
               }

               return "Usage: change-slot <1-9>";
            case "drop":
               if (parts.length == 0) {
                  return "Usage: drop <hand|fullinventory|<amount> [item]|<item>>";
               } else {
                  String first = parts[0].toLowerCase(Locale.ROOT);
                  if (first.equals("hand")) {
                     return this.dropSelected(true);
                  } else if (isFullInventoryDropRequest(first, parts.length > 1 ? parts[1] : "")) {
                     return this.dropFullInventory();
                  } else {
                     Integer amount = parseIntOrNull(parts[0]);
                     if (amount != null) {
                        if (parts.length >= 2) {
                           String query = splitQuotedFirst(rest.substring(parts[0].length()).trim())[0];
                           return this.throwFromPlayerInventory(query, amount, false);
                        }

                        return this.throwFromHandler(this.heldHandlerSlot(), Math.min(amount, this.handlerStackCount(this.heldHandlerSlot())));
                     }

                     return this.throwFromPlayerInventory(rest, Integer.MAX_VALUE, true);
                  }
               }
            case "close":
               return this.closeContainer();
            case "close-silent":
               return this.closeSilent();
            case "use":
               return this.useItem();
            case "swing":
               return this.swingArm();
            case "say":
               return rest.isEmpty() ? "Usage: say <message>" : this.sendConsoleLine(rest);
            case "damage":
               Double n = parts.length > 0 ? parseDoubleOrNull(parts[0]) : null;
               return n != null && !(n < 0.1) && !(n > 1024.0) ? this.sendConsoleLine("/damage @s " + n) : "Usage: damage <amount>";
            case "vclip":
            case "hclip":
               String precheck = movementPrecheck(this.status.get(), this.hasPosition);
               if (!precheck.isBlank()) {
                  return precheck;
               } else {
                  MultiClientCommands.ClipRequest request = MultiClientCommands.parseClip(name, rest);
                  if (request.spec() == null) {
                     return request.error();
                  }

                  MultiClientCommands.ClipSpec spec = request.spec();
                  double dx = 0.0;
                  double dz = 0.0;
                  if (name.equals("hclip")) {
                     double yaw = Math.toRadians(this.position.yRot());
                     dx = -Math.sin(yaw) * spec.blocks();
                     dz = Math.cos(yaw) * spec.blocks();
                  }

                  double dy = name.equals("vclip") ? spec.blocks() : 0.0;
                  return this.clip(dx, dy, dz, spec.segments(), spec.forceGround()) > 0 ? "Sent" : "Nothing to clip";
               }
            case "send":
               if (parts.length < 1) {
                  return "Usage: send <PacketClass> [args]";
               } else {
                  Class<? extends Packet<?>> cls = RiptidePacketRegistry.getPacket(parts[0]);
                  if (cls == null) {
                     return "Unknown packet: " + parts[0];
                  }

                  return this.sendManual(cls, rest.substring(parts[0].length()).trim());
               }
            default:
               return "unknown or unsupported client command";
         }
      }
   }

   private String repeatClick(int handlerSlot, MultiClientCommands.ClickSpec spec, String countArg) {
      int count = 1;
      if (countArg != null) {
         Integer parsed = parseIntOrNull(countArg);
         if (parsed == null || parsed < 1 || parsed > 100000) {
            return "Click count must be 1-100000";
         }

         count = parsed;
      }

      return this.enqueueClick(handlerSlot, spec.button(), spec.input(), false, count);
   }

   private static Integer parseIntOrNull(String value) {
      try {
         return Integer.parseInt(value.trim());
      } catch (RuntimeException var2) {
         return null;
      }
   }

   private static Double parseDoubleOrNull(String value) {
      try {
         double parsed = Double.parseDouble(value.trim());
         return Double.isFinite(parsed) ? parsed : null;
      } catch (RuntimeException var3) {
         return null;
      }
   }

   static boolean isFullInventoryDropRequest(String first, String second) {
      if (first == null) {
         return false;
      } else {
         String normalized = first.toLowerCase(Locale.ROOT);
         return normalized.equals("fullinventory")
            || normalized.equals("all")
            || normalized.equals("inv")
            || normalized.equals("inventory")
            || normalized.equals("full") && "inventory".equalsIgnoreCase(second);
      }
   }

   private static int parseVisibleSlot(String token) {
      if (token == null) {
         return -1;
      } else {
         String t = token.trim().toLowerCase(Locale.ROOT);
         switch (t) {
            case "boots":
               return 36;
            case "leggings":
               return 37;
            case "chestplate":
               return 38;
            case "helmet":
               return 39;
            case "offhand":
               return 40;
            default:
               int indexed = parseIndexed(t, "hotbar", 1, 9, 0);
               if (indexed >= 0) {
                  return indexed;
               } else {
                  indexed = parseIndexed(t, "inventory", 1, 27, 9);
                  if (indexed >= 0) {
                     return indexed;
                  } else {
                     indexed = parseIndexed(t, "gui", 1, 999, 100);
                     if (indexed >= 0) {
                        return indexed;
                     } else {
                        Integer numeric = parseIntOrNull(t);
                        return numeric != null && numeric >= 0 ? numeric : -1;
                     }
                  }
               }
         }
      }
   }

   private static int parseIndexed(String token, String prefix, int min, int max, int base) {
      if (!token.startsWith(prefix)) {
         return -1;
      } else {
         Integer n = parseIntOrNull(token.substring(prefix.length()));
         return n != null && n >= min && n <= max ? base + (n - min) : -1;
      }
   }

   private static String[] splitQuotedFirst(String input) {
      String s = input == null ? "" : input.trim();
      if (s.startsWith("\"")) {
         int end = s.indexOf(34, 1);
         return end > 0 ? new String[]{s.substring(1, end), s.substring(end + 1).trim()} : new String[]{s.substring(1), ""};
      } else {
         int space = s.indexOf(32);
         return space < 0 ? new String[]{s, ""} : new String[]{s.substring(0, space), s.substring(space + 1).trim()};
      }
   }

   private String throwFromHandler(int handler, int count) {
      if (handler >= 0 && count > 0) {
         if (!this.reserveUserSend()) {
            return "Rate limited";
         } else {
            int n = Math.max(1, Math.min(128, count));

            for (int i = 0; i < n; i++) {
               if (!this.clickSlotRaw(handler, 0, ContainerInput.THROW)) {
                  return "Blocked by packet policy";
               }
            }

            return "Sent";
         }
      } else {
         return "Nothing to drop";
      }
   }

   private int handlerStackCount(int handler) {
      synchronized (this.menuLock) {
         List<ItemStack> source = this.openContainerId < 0 ? this.playerInv : this.menuSlots;
         if (handler >= 0 && handler < source.size()) {
            ItemStack stack = nonNull(source.get(handler));
            return stack.isEmpty() ? 0 : stack.getCount();
         } else {
            return 0;
         }
      }
   }

   private String throwFromPlayerInventory(String query, int requested, boolean wholeFirstStack) {
      String normalized = normalizeItemQuery(query);
      if (normalized.isEmpty()) {
         return "Item not found in the bot inventory";
      } else {
         record Candidate(int liveHandler, int count) {
         }

         List<Candidate> candidates = new ArrayList<>();
         synchronized (this.menuLock) {
            int selected = Math.max(0, Math.min(8, this.selectedHotbar));
            int[] order = new int[36];
            int cursor = 0;
            order[cursor++] = 36 + selected;

            for (int h = 36; h <= 44; h++) {
               if (h != 36 + selected) {
                  order[cursor++] = h;
               }
            }

            int hx = 9;

            while (hx <= 35) {
               order[cursor++] = hx++;
            }

            for (int hxx : order) {
               if (hxx >= 0 && hxx < this.playerInv.size()) {
                  ItemStack stack = nonNull(this.playerInv.get(hxx));
                  if (!stack.isEmpty() && itemMatches(stack, normalized)) {
                     int visible = hxx >= 36 ? hxx - 36 : hxx;
                     int live = this.visibleToHandler(visible);
                     if (live >= 0) {
                        candidates.add(new Candidate(live, stack.getCount()));
                     }
                  }
               }
            }
         }

         if (candidates.isEmpty()) {
            return "Item not found in the bot inventory";
         } else if (!this.reserveUserSend()) {
            return "Rate limited";
         } else if (wholeFirstStack) {
            return this.clickSlotRaw(candidates.getFirst().liveHandler(), 1, ContainerInput.THROW) ? "Sent" : "Blocked by packet policy";
         } else {
            int remaining = Math.max(1, requested);

            for (Candidate candidate : candidates) {
               int count = Math.min(remaining, candidate.count());

               for (int i = 0; i < count; i++) {
                  if (!this.clickSlotRaw(candidate.liveHandler(), 0, ContainerInput.THROW)) {
                     return remaining < requested ? "Sent" : "Blocked by packet policy";
                  }
               }

               remaining -= count;
               if (remaining <= 0) {
                  break;
               }
            }

            return remaining < requested ? "Sent" : "Nothing to drop";
         }
      }
   }

   public String sendImmediateMovement() {
      String precheck = movementPrecheck(this.status.get(), this.hasPosition);
      if (!precheck.isBlank()) {
         return precheck;
      } else if (!this.reserveUserSend()) {
         return "Rate limited";
      } else {
         return this.sendPosition(false) ? "Sent" : "Blocked by packet policy";
      }
   }

   public String sendImmediateMoveLook() {
      return this.sendImmediateMovement();
   }

   static String movementPrecheck(MultiSession.Status status, boolean hasPosition) {
      if (status != MultiSession.Status.READY) {
         return "Session is not ready";
      } else {
         return !hasPosition ? "Position is not ready" : "";
      }
   }

   private synchronized boolean reserveUserSend() {
      long now = System.currentTimeMillis();
      if (now - this.userSendWindowAt >= 1000L) {
         this.userSendWindowAt = now;
         this.userSendsInWindow = 0;
      }

      if (this.userSendsInWindow >= 20) {
         return false;
      } else {
         this.userSendsInWindow++;
         return true;
      }
   }

   void applyPolicy(MultiPacketPolicy updated) {
      if (updated != null) {
         boolean wasGravity = this.policy != null && this.policy.gravity();
         this.policy = new MultiPacketPolicy(updated);
         if (this.policy.gravity() && !wasGravity) {
            this.gravitySettleRequest = true;
         }
      }
   }

   void startAssignedMacro(RiptideMacro macro) {
      this.startAssignedMacro(macro, 0L);
   }

   void startAssignedMacro(RiptideMacro macro, long startAtMs) {
      if (this.piloted) {
         PacketTeleportController.cancelPov(this, "macro took POV ownership");
      }

      long now = System.currentTimeMillis();
      this.macroQueue = startAtMs > now ? new MultiSession.MacroQueue(now, startAtMs) : MultiSession.MacroQueue.NONE;
      this.macroStartRequest = macro;
      this.macroStopRequest = false;
      this.loginMacroRun = false;
   }

   void startLoginMacro(RiptideMacro macro) {
      if (this.piloted) {
         PacketTeleportController.cancelPov(this, "macro took POV ownership");
      }

      this.macroQueue = MultiSession.MacroQueue.NONE;
      this.macroStartRequest = macro;
      this.macroStopRequest = false;
      this.loginMacroRun = true;
      this.loginMacroDeadline = System.currentTimeMillis() + 40000L;
   }

   void stopMacro() {
      this.macroStopRequest = true;
      this.macroStartRequest = null;
      this.macroQueue = MultiSession.MacroQueue.NONE;
      this.loginMacroRun = false;
   }

   boolean isMacroRunning() {
      return this.macroRun != null || this.macroStartRequest != null;
   }

   boolean macroOwnsPilot() {
      long now = System.currentTimeMillis();
      return macroOwnsPilot(this.macroStartRequest != null, this.macroRun != null, this.macroMotorActive(now));
   }

   static boolean macroOwnsPilot(boolean startRequested, boolean interpreterRunning, boolean motorActive) {
      return startRequested || interpreterRunning || motorActive;
   }

   private boolean macroMotorActive(long now) {
      return now < this.walkUntil || this.fallMode && now < this.macroMotorUntil;
   }

   public String currentMacroName() {
      MultiMacroRun run = this.macroRun;
      return run == null ? "" : run.macroName();
   }

   private void driveMacro(long now) {
      if (this.macroStopRequest) {
         this.macroStopRequest = false;
         this.macroRun = null;
         this.macroStatus = "";
         this.macroProgress = MultiSession.MacroProgress.idle();
         this.macroQueue = MultiSession.MacroQueue.NONE;
         this.cancelMacroMotor();
         this.resetMacroInputs();
      }

      RiptideMacro start = this.macroStartRequest;
      if (start != null && this.macroQueue.pending(now)) {
         start = null;
      }

      if (start != null) {
         this.macroStartRequest = null;
         this.macroQueue = MultiSession.MacroQueue.NONE;
         if (this.macroRun != null) {
            this.cancelMacroMotor();
            this.resetMacroInputs();
         }

         this.primeMacroInputs();
         this.macroRun = start.actions.isEmpty() ? null : new MultiMacroRun(start);
         this.macroStatus = this.macroRun == null ? "" : this.macroRun.status();
         this.macroProgress = this.macroRun == null ? MultiSession.MacroProgress.idle() : this.macroRun.progress();
      }

      MultiMacroRun run = this.macroRun;
      if (run != null) {
         try {
            run.step(now, this);
         } catch (RuntimeException var6) {
            this.macroRun = null;
            this.macroStatus = "error";
            this.macroProgress = new MultiSession.MacroProgress(
               run.macroName(), false, run.stepIndex(), run.totalSteps(), run.loopNumber(), MultiManager.singleLine("Error: " + shortError(var6), 64)
            );
            this.macroFinishNote.set(run.macroName() + Character.toString(0) + "error");
            this.loginMacroRun = false;
            this.cancelMacroMotor();
            this.resetMacroInputs();
            return;
         }

         if (run.done()) {
            if ("done".equals(run.status()) && this.macroMotorActive(now)) {
               this.macroStatus = "finishing movement";
               this.macroProgress = new MultiSession.MacroProgress(
                  run.macroName(), true, run.totalSteps(), run.totalSteps(), run.loopNumber(), "Finishing movement"
               );
               return;
            }

            this.macroFinishNote.set(run.macroName() + "\u0000" + run.status());
            this.macroRun = null;
            this.loginMacroRun = false;
            this.macroStatus = "";
            this.macroProgress = new MultiSession.MacroProgress(
               run.macroName(), false, run.totalSteps(), run.totalSteps(), run.loopNumber(), MultiManager.singleLine(run.status(), 64)
            );
            this.resetMacroInputs();
         } else {
            this.macroStatus = run.status();
            this.macroProgress = run.progress();
         }
      }
   }

   String pollMacroFinish() {
      return this.macroFinishNote.getAndSet(null);
   }

   private void resetMacroInputs() {
      this.disarmPacketCapture();
      this.cancelXCarry();
      if (this.inputShift || !this.inputSprint) {
         this.inputShift = false;
         this.inputSprint = true;
         this.sendInput();
      }

      this.send(
         new ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
         ),
         false,
         false
      );
   }

   private void primeMacroInputs() {
      this.inputShift = false;
      this.inputSprint = true;
      this.sendInput();
      this.sprintAnnounced = false;
      this.announceSprint(true);
      this.send(
         new ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
         ),
         false,
         false
      );
   }

   private void cancelMacroMotor() {
      this.cancelClip();
      this.macroMotorUntil = 0L;
      this.walkUntil = 0L;
      this.moveActiveUntil = 0L;
      this.walkX = 0.0;
      this.walkZ = 0.0;
      this.motionY = 0.0;
      this.fallMode = false;
      this.primeFallTick = false;
   }

   @Override
   public boolean macroReady() {
      return this.status.get() == MultiSession.Status.READY && !this.closed.get() && this.health > 0.0F && this.hasPosition;
   }

   @Override
   public void macroNote(String note) {
      if (note != null && !note.isBlank()) {
         this.appendLocal(note);
      }
   }

   @Override
   public boolean customMenuPhaseActive() {
      MultiSession.Status current = this.status.get();
      if (this.closed.get()) {
         return false;
      } else {
         return current == MultiSession.Status.READY
            ? this.health > 0.0F && this.hasPosition
            : current == MultiSession.Status.CONFIGURING || current == MultiSession.Status.JOINED;
      }
   }

   @Override
   public boolean fullMode() {
      return true;
   }

   @Override
   public CustomMenuSnapshot customMenu() {
      return this.customMenus.current();
   }

   @Override
   public CustomMenuSubmitResult submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
      CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, submission);
      if (!result.success()) {
         return result;
      } else {
         for (Packet<?> packet : result.packets()) {
            if (!this.send(packet, true, false)) {
               return CustomMenuSubmitResult.failure("Connection closed during submission");
            }
         }

         this.customMenus.consume(snapshot, result.replacement());
         this.updateCustomMenuTitle(snapshot, this.customMenus.current());
         return result;
      }
   }

   @Override
   public String resolveCustomMenuValue(String template, Map<String, String> macroVariables) {
      String out = template == null ? "" : template;

      for (Entry<String, String> entry : this.formValues.entrySet()) {
         out = out.replace("{secret." + entry.getKey() + "}", entry.getValue());
      }

      if (out.matches("(?s).*\\{secret\\.[^}]+}.*")) {
         return null;
      } else {
         String username = this.identity != null && this.identity.user() != null ? this.identity.user().getName() : "";
         out = out.replace("{username}", username).replace("{account_id}", this.spec.accountId()).replace("{profile_name}", this.profileName);
         if (macroVariables != null) {
            for (Entry<String, String> entry : macroVariables.entrySet()) {
               out = out.replace("{" + entry.getKey() + "}", entry.getValue());
            }
         }

         return out;
      }
   }

   @Override
   public String botUsername() {
      return this.identity != null && this.identity.user() != null ? this.identity.user().getName() : "";
   }

   @Override
   public String botUuid() {
      return this.identity != null && this.identity.user() != null ? this.identity.user().getProfileId().toString() : "";
   }

   @Override
   public String serverAddress() {
      return this.serverAddress;
   }

   void setCaptureWorld(boolean value) {
      this.captureWorld = value;
      if (!value) {
         this.worldCapture.clear();
      }
   }

   boolean capturingWorld() {
      return this.captureWorld;
   }

   boolean hasCapturedWorld() {
      return this.captureWorld && this.worldCapture.hasWorld();
   }

   MultiWorldCapture.Snapshot captureSnapshot() {
      return this.worldCapture.snapshot();
   }

   Connection liveConnection() {
      return this.connection;
   }

   UUID serverUuid() {
      return this.serverAssignedUuid != null
         ? this.serverAssignedUuid
         : (this.identity != null && this.identity.user() != null ? this.identity.user().getProfileId() : null);
   }

   PositionMoveRotation takeoverPosition() {
      return this.position;
   }

   int takeoverPositionEntityId() {
      return this.playerEntityId;
   }

   MultiEntityTracker.State entityTruth(int entityId) {
      return this.entities.state(entityId);
   }

   MultiEntityTracker.State entityTruth(UUID entityUuid) {
      return this.entities.state(entityUuid);
   }

   Iterable<MultiEntityTracker.State> entityTruthStates() {
      return this.entities.states();
   }

   double takeoverBlockInteractionRange() {
      return this.blockInteractionRange;
   }

   double takeoverEntityInteractionRange() {
      return this.entityInteractionRange;
   }

   GameProfile takeoverProfile() {
      UUID id = this.serverAssignedUuid != null
         ? this.serverAssignedUuid
         : (this.identity != null && this.identity.user() != null ? this.identity.user().getProfileId() : null);
      String name = this.identity != null && this.identity.user() != null ? this.identity.user().getName() : this.spec.accountId();
      return skinAwareProfile(this.serverGameProfile, id, name);
   }

   static GameProfile skinAwareProfile(GameProfile known, UUID id, String name) {
      if (known != null && known.properties() != null && !known.properties().isEmpty()) {
         return Objects.equals(known.id(), id) && Objects.equals(known.name(), name) ? known : new GameProfile(id, name, known.properties());
      } else {
         return new GameProfile(id, name);
      }
   }

   List<ItemStack> takeoverInventory() {
      List<ItemStack> copy = new ArrayList<>(this.playerInv.size());
      synchronized (this.menuLock) {
         for (ItemStack stack : this.playerInv) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
         }

         return copy;
      }
   }

   ItemStack takeoverCarried() {
      synchronized (this.menuLock) {
         return this.carried == null ? ItemStack.EMPTY : this.carried.copy();
      }
   }

   int takeoverInventoryStateId() {
      return this.inventoryStateId;
   }

   int takeoverSelectedHotbar() {
      return Math.max(0, Math.min(8, this.selectedHotbar));
   }

   float takeoverHealth() {
      return this.health;
   }

   int takeoverFood() {
      return this.food;
   }

   int takeoverGameModeId() {
      return this.gameModeId;
   }

   String takeoverDimension() {
      return this.dimension;
   }

   void setPiloted(boolean value) {
      if (!value) {
         PacketTeleportController.cancelPov(this, "POV ownership changed");
      }

      this.piloted = value;
      if (value && !this.macroOwnsPilot()) {
         this.walkUntil = 0L;
         this.moveActiveUntil = 0L;
         this.motionY = 0.0;
         this.primeFallTick = false;
         this.kbTicks = 0;
         this.fallMode = false;
         this.captcha.reset(System.currentTimeMillis());
      }
   }

   boolean isPiloted() {
      return this.piloted;
   }

   boolean pilotPacketsReady() {
      return this.status.get() == MultiSession.Status.READY && this.hasPosition && this.connected();
   }

   void pilotObserveServerTruth(Vec3 pos) {
      if (pos != null) {
         long now = System.currentTimeMillis();
         this.pilotObservedPosition = pos;
         this.pilotObservedAt = now;
         if (this.piloted && this.macroOwnsPilot() && this.outboundTruth.observe(pos, now) == MultiPilotTruth.Result.REBASE) {
            synchronized (this.positionLock) {
               this.position = new PositionMoveRotation(pos, this.position.deltaMovement(), this.position.yRot(), this.position.xRot());
            }
         }
      }
   }

   void pilotMove(Vec3 pos, Vec3 deltaMovement, float yRot, float xRot, boolean onGround, boolean horizontalCollision) {
      synchronized (this.pilotProtocolLock) {
         if (this.pilotPacketsReady()) {
            if (!this.clipBusy()) {
               synchronized (this.positionLock) {
                  this.position = new PositionMoveRotation(pos, deltaMovement == null ? Vec3.ZERO : deltaMovement, yRot, xRot);
               }

               this.grounded = onGround;
               MultiPovModuleController.WireMove wire = MultiPovModuleController.transformWireMove(pos, onGround, deltaMovement);
               if (this.sendTickPair(new PosRot(wire.position(), yRot, xRot, wire.onGround(), horizontalCollision), false, false)
                  == MultiSession.PairResult.SENT) {
                  this.lastMovementAt = System.currentTimeMillis();
                  this.outboundTruth.recordSent(pos, this.lastMovementAt);
               }
            }
         }
      }
   }

   boolean pilotTeleportMove(Vec3 pos, boolean onGround) {
      return pos == null ? false : this.pilotTeleportSequence(List.of(new PacketClipSafety.Step(pos, onGround)));
   }

   boolean pilotTeleportSequence(List<PacketClipSafety.Step> steps) {
      if (steps != null && !steps.isEmpty()) {
         synchronized (this.pilotProtocolLock) {
            if (this.pilotPacketsReady() && this.piloted && !this.macroOwnsPilot()) {
               PacketClipSafety.Step accepted = null;
               synchronized (this.movePairLock) {
                  for (PacketClipSafety.Step step : steps) {
                     if (step == null
                        || step.position() == null
                        || this.sendTickPair(new Pos(step.position(), step.onGround(), false), false, false) != MultiSession.PairResult.SENT) {
                        break;
                     }

                     accepted = step;
                  }
               }

               if (accepted == null) {
                  return false;
               } else {
                  synchronized (this.positionLock) {
                     this.position = new PositionMoveRotation(accepted.position(), Vec3.ZERO, this.position.yRot(), this.position.xRot());
                  }

                  this.grounded = accepted.onGround();
                  this.lastMovementAt = System.currentTimeMillis();
                  this.outboundTruth.recordSent(accepted.position(), this.lastMovementAt);
                  this.motionY = 0.0;
                  this.walkUntil = 0L;
                  this.fallMode = false;
                  this.primeFallTick = false;
                  return accepted == steps.getLast();
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   boolean pilotTeleportStatus(boolean onGround) {
      synchronized (this.pilotProtocolLock) {
         if (this.pilotPacketsReady() && this.piloted && !this.macroOwnsPilot()) {
            if (this.sendTickPair(new StatusOnly(onGround, false), false, false) != MultiSession.PairResult.SENT) {
               return false;
            } else {
               this.grounded = onGround;
               this.lastMovementAt = System.currentTimeMillis();
               return true;
            }
         } else {
            return false;
         }
      }
   }

   boolean pilotSend(Packet<?> packet) {
      synchronized (this.pilotProtocolLock) {
         return this.pilotPacketsReady() && this.send(packet, false, false);
      }
   }

   boolean pilotPlayerCommand(Entity renderedBot, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action) {
      synchronized (this.pilotProtocolLock) {
         if (this.pilotPacketsReady() && renderedBot != null && action != null && this.playerEntityId >= 0) {
            ServerboundPlayerCommandPacket packet = new ServerboundPlayerCommandPacket(renderedBot, action);
            ((RiptidePlayerCommandPacketAccessor)packet).riptide$setEntityId(this.playerEntityId);
            return this.send(packet, false, false);
         } else {
            return false;
         }
      }
   }

   boolean pilotAbilities(Abilities abilities) {
      return abilities != null && this.pilotSend(new ServerboundPlayerAbilitiesPacket(abilities));
   }

   boolean pilotSelectHotbar(int slot) {
      return "Sent".equals(this.selectHotbar(Math.max(0, Math.min(8, slot))));
   }

   boolean pilotSwapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
      if (this.openContainerId < 0 && inventorySlot >= 0 && inventorySlot <= 35) {
         int handler = inventorySlot <= 8 ? 36 + inventorySlot : inventorySlot;
         return "Sent".equals(this.enqueueClick(handler, Math.max(0, Math.min(8, hotbarSlot)), ContainerInput.SWAP, false));
      } else {
         return false;
      }
   }

   boolean pilotEquipChestFromInventory(int inventorySlot) {
      if (this.openContainerId < 0 && inventorySlot >= 0 && inventorySlot <= 35) {
         int hotbar = Math.max(0, Math.min(8, this.selectedHotbar));
         return inventorySlot <= 8
            ? "Sent".equals(this.enqueueClick(6, inventorySlot, ContainerInput.SWAP, false))
            : "Sent".equals(this.enqueueClick(inventorySlot, hotbar, ContainerInput.SWAP, false))
               && "Sent".equals(this.enqueueClick(6, hotbar, ContainerInput.SWAP, false))
               && "Sent".equals(this.enqueueClick(inventorySlot, hotbar, ContainerInput.SWAP, false));
      } else {
         return false;
      }
   }

   boolean pilotAttackEntity(int entityId) {
      synchronized (this.pilotProtocolLock) {
         if (!this.pilotPacketsReady() || entityId < 0) {
            return false;
         } else if (!this.ensurePilotCarriedItem()) {
            return false;
         } else if (!this.send(new ServerboundAttackPacket(entityId), false, false)) {
            return false;
         } else {
            this.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
            return true;
         }
      }
   }

   boolean pilotInteractEntity(int entityId, InteractionHand hand, Vec3 location, boolean secondary) {
      synchronized (this.pilotProtocolLock) {
         if (entityId < 0 || hand == null || location == null) {
            return false;
         } else if (!this.pilotPacketsReady()) {
            return false;
         } else {
            boolean slotReady = this.ensurePilotCarriedItem();
            return slotReady && this.send(new ServerboundInteractPacket(entityId, hand, location, secondary), false, false);
         }
      }
   }

   private boolean ensurePilotCarriedItem() {
      int slot = Math.max(0, Math.min(8, this.selectedHotbar));
      if (this.lastWireHotbar != slot) {
         if (!this.send(new ServerboundSetCarriedItemPacket(slot), false, false)) {
            return false;
         }

         this.lastWireHotbar = slot;
      }

      return true;
   }

   int nextUseSeq() {
      return ++this.useSeq;
   }

   void pilotCloseContainer() {
      synchronized (this.pilotProtocolLock) {
         if (this.pilotPacketsReady() && this.openContainerId >= 0) {
            this.closeContainer();
         }
      }
   }

   long pilotSignSeq() {
      return this.pilotSignSeq;
   }

   long pilotBookSeq() {
      return this.pilotBookSeq;
   }

   BlockPos pilotSignPos() {
      return this.signEditorPos;
   }

   boolean pilotSignFront() {
      return this.signEditorFront;
   }

   InteractionHand pilotBookHand() {
      return this.pilotBookHand;
   }

   boolean signEditorOpen() {
      return this.signEditorOpen;
   }

   void clearSignEditor() {
      this.signEditorOpen = false;
   }

   float digSpeedMultiplier() {
      long now = System.currentTimeMillis();
      float mult = 1.0F;
      int haste = -1;
      if (this.digHasteUntil > now && this.digHasteAmp >= 0) {
         haste = this.digHasteAmp;
      }

      if (this.digConduitUntil > now && this.digConduitAmp > haste) {
         haste = this.digConduitAmp;
      }

      if (haste >= 0) {
         mult *= 1.0F + (haste + 1) * 0.2F;
      }

      if (this.digFatigueUntil > now && this.digFatigueAmp >= 0) {
         mult *= switch (Math.min(3, this.digFatigueAmp)) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      return mult;
   }

   Vec3 consumePilotImpulse() {
      return this.pilotImpulse.getAndSet(null);
   }

   void resumeAfterPilot() {
      long now = System.currentTimeMillis();
      Vec3 delta = this.position.deltaMovement() == null ? Vec3.ZERO : this.position.deltaMovement();
      this.primeFallTick = false;
      if (this.grounded) {
         this.motionY = 0.0;
         this.fallMode = false;
      } else {
         this.motionY = Math.max(-3.92, Math.min(1.0, delta.y));
         this.fallModeUntil = now + 15000L;
         this.fallMode = true;
         this.moveActiveUntil = Math.max(this.moveActiveUntil, now + 5000L);
      }

      this.inVehicle = false;
      this.vehicleId = -1;
      this.postVehicleFall = false;
      this.blockUpdates.clear();
   }

   @Override
   public String macroPassword() {
      return this.loginMacroRun ? this.loginPassword() : "";
   }

   @Override
   public float health() {
      return this.health;
   }

   @Override
   public float maxHealth() {
      return this.maxHealth;
   }

   @Override
   public int food() {
      return this.food;
   }

   @Override
   public boolean hasPosition() {
      return this.hasPosition;
   }

   @Override
   public double posX() {
      return this.position.position().x;
   }

   @Override
   public double posY() {
      return this.position.position().y;
   }

   @Override
   public double posZ() {
      return this.position.position().z;
   }

   @Override
   public String dimension() {
      return this.dimension;
   }

   @Override
   public String heldItemName() {
      return this.heldItemName;
   }

   @Override
   public int selectedHotbar() {
      return this.selectedHotbar;
   }

   @Override
   public String openScreenTitle() {
      return this.openScreenTitle;
   }

   @Override
   public boolean containerOpen() {
      return this.openContainerId >= 0 && !this.containerDismissed;
   }

   @Override
   public long guiOpenSeq() {
      return this.openScreenSeq;
   }

   @Override
   public int countItem(String query) {
      String q = normalizeItemQuery(query);
      int total = 0;
      synchronized (this.menuLock) {
         for (ItemStack stack : this.playerInv) {
            if (stack != null && !stack.isEmpty() && (q.isEmpty() || itemMatches(stack, q))) {
               total += stack.getCount();
            }
         }

         return total;
      }
   }

   @Override
   public int freeSlots() {
      int free = 0;
      synchronized (this.menuLock) {
         for (int h = 9; h <= 44; h++) {
            ItemStack stack = h < this.playerInv.size() ? this.playerInv.get(h) : ItemStack.EMPTY;
            if (stack == null || stack.isEmpty()) {
               free++;
            }
         }

         return free;
      }
   }

   @Override
   public boolean slotFilled(int visibleSlot) {
      int handler = this.visibleToHandler(visibleSlot);
      if (handler < 0) {
         return false;
      } else {
         synchronized (this.menuLock) {
            ItemStack stack = handler < this.menuSlots.size() ? this.menuSlots.get(handler) : ItemStack.EMPTY;
            return stack != null && !stack.isEmpty();
         }
      }
   }

   @Override
   public boolean cursorEmpty() {
      ItemStack c = this.carried;
      return c == null || c.isEmpty();
   }

   @Override
   public String cursorName() {
      ItemStack c = this.carried;
      return c != null && !c.isEmpty() ? c.getHoverName().getString() : "";
   }

   @Override
   public int countItemTarget(ItemTarget target) {
      if (target != null && (target.hasIdentity() || target.hasSlot())) {
         int total = 0;
         synchronized (this.menuLock) {
            for (int h = 9; h <= 44 && h < this.playerInv.size(); h++) {
               ItemStack stack = this.playerInv.get(h);
               if (stack != null && !stack.isEmpty()) {
                  int invIndex = h >= 36 ? h - 36 : h;
                  if (target.score(stack, invIndex) >= 0) {
                     total += stack.getCount();
                  }
               }
            }

            return total;
         }
      } else {
         return 0;
      }
   }

   @Override
   public boolean cursorMatches(ItemTarget target) {
      if (target == null) {
         return false;
      } else {
         ItemStack c = this.carried;
         return c != null && !c.isEmpty() && target.score(c, -1) >= 0;
      }
   }

   @Override
   public float currentPitch() {
      PositionMoveRotation p = this.position;
      return p == null ? 0.0F : p.xRot();
   }

   @Override
   public int[] heldDurability() {
      return this.durabilityAtHandler(36 + Math.max(0, Math.min(8, this.selectedHotbar)));
   }

   @Override
   public int[] durabilityAtInv(int inventoryIndex) {
      return this.durabilityAtHandler(invToHandler(inventoryIndex));
   }

   @Override
   public int[] itemDurability(ItemTarget target) {
      if (target == null) {
         return null;
      } else {
         ItemTarget itemOnly = target.copy();
         if (itemOnly.hasIdentity()) {
            itemOnly.slot = -1;
         }

         synchronized (this.menuLock) {
            for (int h = 9; h <= 45 && h < this.playerInv.size(); h++) {
               ItemStack stack = this.playerInv.get(h);
               if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
                  int invIndex = h == 45 ? 40 : (h >= 36 ? h - 36 : h);
                  if (itemOnly.score(stack, invIndex) >= 0) {
                     return new int[]{stack.getDamageValue(), stack.getMaxDamage()};
                  }
               }
            }

            return null;
         }
      }
   }

   private int[] durabilityAtHandler(int handler) {
      synchronized (this.menuLock) {
         if (handler >= 0 && handler < this.playerInv.size()) {
            ItemStack s = this.playerInv.get(handler);
            return s != null && !s.isEmpty() && s.isDamageableItem() ? new int[]{s.getDamageValue(), s.getMaxDamage()} : null;
         } else {
            return null;
         }
      }
   }

   private static int invToHandler(int inv) {
      if (inv >= 0 && inv <= 8) {
         return 36 + inv;
      } else if (inv >= 9 && inv <= 35) {
         return inv;
      } else if (inv >= 36 && inv <= 39) {
         return 44 - inv;
      } else {
         return inv == 40 ? 45 : -1;
      }
   }

   @Override
   public long teleportSeq() {
      return this.teleportSeq;
   }

   @Override
   public long containerRevision() {
      return this.openContainerId < 0 ? this.inventoryStateId : this.containerStateId;
   }

   private void trackBlock(BlockPos pos, BlockState state) {
      if (pos != null && state != null) {
         long key = pos.asLong();
         if (this.blockUpdates.size() >= 512 && !this.blockUpdates.containsKey(key)) {
            this.blockUpdates.clear();
         }

         this.blockUpdates.put(key, state);
      }
   }

   @Override
   public boolean blockAt(int x, int y, int z, List<String> ids, boolean anyBlock, boolean wantDestroyed) {
      BlockState st = this.blockUpdates.get(BlockPos.asLong(x, y, z));
      if (st == null) {
         return false;
      } else if (wantDestroyed) {
         return st.isAir();
      } else if (st.isAir()) {
         return false;
      } else if (!anyBlock && ids != null && !ids.isEmpty()) {
         Identifier id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
         String cur = id == null ? "" : id.toString();

         for (String want : ids) {
            if (want != null && !want.isBlank() && soundIdMatches(want, cur)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public void sendRawPayload(String channel, String rawData) {
      if (channel != null && !channel.isBlank()) {
         try {
            byte[] bytes = RiptidePayloadSupport.parsePayloadBytes(rawData);
            this.send(RiptidePayloadSupport.createC2SPacket(channel.trim(), bytes), false, false);
         } catch (RuntimeException var4) {
         }
      }
   }

   @Override
   public String[] slotChangeBaseline(WaitForSlotChangeAction action) {
      String[] base = new String[action.entries.size()];
      synchronized (this.menuLock) {
         int size = this.menuSlots.size();

         for (int i = 0; i < action.entries.size(); i++) {
            WaitForSlotChangeAction.WaitEntry e = action.entries.get(i);
            ItemTarget t = e.resolvedTarget();
            if (e.waitMode == WaitForSlotChangeAction.WaitMode.ANY_CHANGE && t != null && t.hasSlot()) {
               int handlerSlot = this.visibleToHandler(t.slot);
               ItemStack s = handlerSlot >= 0 && handlerSlot < size ? this.menuSlots.get(handlerSlot) : ItemStack.EMPTY;
               base[i] = snapshotStackIdentity(s);
            } else {
               base[i] = "";
            }
         }

         return base;
      }
   }

   @Override
   public boolean slotChangeMet(WaitForSlotChangeAction action, String[] baseline) {
      if (action.entries.isEmpty()) {
         return true;
      } else {
         synchronized (this.menuLock) {
            int size = this.menuSlots.size();

            for (int i = 0; i < action.entries.size(); i++) {
               if (!this.slotChangeEntryMet(action.entries.get(i), i, baseline, size)) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private boolean slotChangeEntryMet(WaitForSlotChangeAction.WaitEntry e, int idx, String[] baseline, int size) {
      ItemTarget target = e.resolvedTarget();
      if (target == null) {
         target = new ItemTarget();
      }

      if (target.hasSlot()) {
         int handlerSlot = this.visibleToHandler(target.slot);
         return handlerSlot >= 0 && handlerSlot < size
            ? this.checkSlotStack(this.menuSlots.get(handlerSlot), target, target.slot, e, idx, baseline)
            : e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
      } else if (e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY) {
         for (int h = 0; h < size; h++) {
            ItemStack s = this.menuSlots.get(h);
            if (s != null && !s.isEmpty()) {
               int vis = this.handlerToVisible(h, size);
               if (!target.hasIdentity() || target.matches(s, vis)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         for (int hx = 0; hx < size; hx++) {
            ItemStack s = this.menuSlots.get(hx);
            if (s != null && !s.isEmpty()) {
               int vis = this.handlerToVisible(hx, size);
               if ((!target.hasIdentity() || target.matches(s, vis)) && this.checkSlotStack(s, target, vis, e, idx, baseline)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean checkSlotStack(ItemStack stack, ItemTarget target, int visibleSlot, WaitForSlotChangeAction.WaitEntry e, int idx, String[] baseline) {
      boolean empty = stack == null || stack.isEmpty();
      boolean nameMatches = target == null || !target.hasIdentity() || !empty && target.matches(stack, visibleSlot);

      return switch (e.waitMode) {
         case NOT_EMPTY -> !empty && nameMatches;
         case IS_EMPTY -> empty;
         case COUNT_AT_LEAST -> !empty && nameMatches && stack.getCount() >= e.targetCount;
         case COUNT_BELOW -> empty || nameMatches && stack.getCount() < e.targetCount;
         case ANY_CHANGE -> {
            String init = baseline != null && idx < baseline.length && baseline[idx] != null ? baseline[idx] : "";
            yield !snapshotStackIdentity(stack).equals(init);
         }
      };
   }

   private static String snapshotStackIdentity(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         String rich = MacroExecutor.serializeTextComponent(stack.getHoverName());
         String components = String.valueOf(stack.getComponents());
         return (id == null ? "" : id.toString()) + "|" + stack.getCount() + "|" + (rich == null ? "" : rich) + "|" + components.hashCode();
      } else {
         return "";
      }
   }

   private static int gameTypeIdOf(CommonPlayerSpawnInfo info) {
      try {
         return info != null && info.gameType() != null ? info.gameType().getId() : -1;
      } catch (Throwable var2) {
         return -1;
      }
   }

   private void captureChat(Component c) {
      if (c != null) {
         this.captureChatText(c.getString());
      }
   }

   private void captureChatText(String text) {
      if (text != null && !text.isEmpty()) {
         synchronized (this.chatLogLock) {
            this.recentChat.addLast(text);

            while (this.recentChat.size() > 64) {
               this.recentChat.removeFirst();
            }

            this.chatSeqCounter++;
         }

         this.checkAutoAccept(text);
      }
   }

   void setAutoAccept(MultiAutoAccept config) {
      this.autoAccept = config == null ? new MultiAutoAccept() : new MultiAutoAccept(config);
   }

   void armAutoAccept(String kind) {
      MultiAutoAccept config = this.autoAccept;
      if (config != null && kind != null) {
         boolean trade = "trade".equals(kind);
         boolean enabled = trade ? config.tradeEnabled : config.tpaEnabled;
         if (enabled) {
            this.autoAcceptArmedKind = kind;
            this.autoAcceptArmedUntil = System.currentTimeMillis() + (trade ? config.tradeArmWindowMs : config.tpaArmWindowMs);
         }
      }
   }

   private String botName() {
      MultiIdentityResolver.Identity resolved = this.identity;
      return resolved == null ? this.spec.accountId() : resolved.user().getName();
   }

   public String username() {
      return this.botName();
   }

   private static boolean containsCi(String haystack, String needle) {
      return needle != null && !needle.isEmpty() && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
   }

   private void checkAutoAccept(String text) {
      if (text != null && !text.isEmpty() && this.autoFireAt == 0L) {
         MultiAutoAccept config = this.autoAccept;
         if (config != null) {
            long now = System.currentTimeMillis();
            String me = MultiManager.renderedServerName();
            String self = this.botName();
            String kind = this.autoAcceptArmedKind;
            if (!kind.isEmpty() && now < this.autoAcceptArmedUntil && !me.isBlank() && containsCi(text, me)) {
               boolean trade = "trade".equals(kind);
               String cmd = MultiAutoAccept.expand(trade ? config.tradeAcceptCommand : config.tpaAcceptCommand, self, me);
               this.scheduleFire(
                  trade ? config.tradeUseMacro : config.tpaUseMacro,
                  trade ? config.tradeMacroName : config.tpaMacroName,
                  cmd,
                  trade ? config.tradeAcceptDelayMs : config.tpaAcceptDelayMs,
                  now
               );
               this.autoAcceptArmedKind = "";
               this.autoAcceptArmedUntil = 0L;
            } else {
               for (MultiAutoAccept.Responder responder : config.responders) {
                  if (responder.valid()) {
                     String trigger = MultiAutoAccept.expand(responder.trigger(), self, me);
                     if (!trigger.isBlank() && containsCi(text, trigger)) {
                        Long until = this.responderCooldown.get(responder.trigger());
                        if (until == null || now >= until) {
                           this.responderCooldown.put(responder.trigger(), now + responder.delayMs() + 3000L);
                           this.scheduleFire(
                              responder.useMacro(), responder.macroName(), MultiAutoAccept.expand(responder.response(), self, me), responder.delayMs(), now
                           );
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void scheduleFire(boolean useMacro, String macro, String command, int delayMs, long now) {
      this.autoFireMacro = useMacro ? (macro == null ? "" : macro) : "";
      this.autoFireCommand = useMacro ? "" : (command == null ? "" : command);
      this.autoFireAt = now + Math.max(0, delayMs);
   }

   private void maybeAutoAccept(long now) {
      if (this.autoFireAt != 0L && now >= this.autoFireAt) {
         this.autoFireAt = 0L;
         String macro = this.autoFireMacro;
         String command = this.autoFireCommand;
         this.autoFireMacro = "";
         this.autoFireCommand = "";
         if (this.status.get() == MultiSession.Status.READY) {
            if (!macro.isBlank()) {
               RiptideMacro loaded = RiptideMacroManager.get().get(macro);
               if (loaded != null && !loaded.actions.isEmpty()) {
                  this.startAssignedMacro(loaded);
               }
            } else if (!command.isBlank()) {
               this.sendConsoleLine(command);
            }
         }
      }
   }

   @Override
   public int gameMode() {
      return this.gameModeId;
   }

   @Override
   public long chatSeq() {
      return this.chatSeqCounter;
   }

   @Override
   public List<String> chatSince(long baselineSeq) {
      synchronized (this.chatLogLock) {
         long missed = this.chatSeqCounter - baselineSeq;
         int take = (int)Math.min(Math.max(0L, missed), (long)this.recentChat.size());
         if (take <= 0) {
            return List.of();
         } else {
            List<String> out = new ArrayList<>(take);
            int skip = this.recentChat.size() - take;
            int i = 0;

            for (String line : this.recentChat) {
               if (i++ >= skip) {
                  out.add(line);
               }
            }

            return out;
         }
      }
   }

   @Override
   public boolean entityWithin(List<String> typeRefs, boolean containerOnly, boolean centerOnPlayer, double cx, double cy, double cz, double radius) {
      Vec3 base = this.position.position();
      double x = centerOnPlayer ? base.x : cx;
      double y = centerOnPlayer ? base.y : cy;
      double z = centerOnPlayer ? base.z : cz;
      return this.entities.present(typeRefs, containerOnly, x, y, z, radius);
   }

   private void recordPacket(boolean c2s, Packet<?> packet) {
      synchronized (this.packetLogLock) {
         this.packetLog.addLast(new MultiSession.PktRec(++this.packetSeqCounter, c2s, packet));

         while (this.packetLog.size() > 128) {
            this.packetLog.removeFirst();
         }
      }
   }

   @Override
   public void setPacketCapture(boolean on) {
      this.packetCaptureArmed = on;
      if (!on) {
         synchronized (this.packetLogLock) {
            this.packetLog.clear();
         }
      }
   }

   @Override
   public long packetSeq() {
      return this.packetSeqCounter;
   }

   @Override
   public boolean packetSeen(long baselineSeq, List<String> targets) {
      synchronized (this.packetLogLock) {
         for (MultiSession.PktRec r : this.packetLog) {
            if (r.seq() > baselineSeq) {
               if (targets == null || targets.isEmpty()) {
                  return true;
               }

               String simple = r.packet().getClass().getSimpleName();

               for (String target : targets) {
                  String dir = WaitForPacketAction.getDirection(target);
                  if ((dir.isEmpty() || dir.equalsIgnoreCase("C2S") == r.c2s()) && WaitForPacketAction.getPacketName(target).equalsIgnoreCase(simple)) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   @Override
   public boolean packetMatched(long baselineSeq, WaitPacketMatchAction action) {
      synchronized (this.packetLogLock) {
         for (MultiSession.PktRec r : this.packetLog) {
            if (r.seq() > baselineSeq) {
               try {
                  if (action.matches(r.packet(), r.c2s() ? "C2S" : "S2C")) {
                     return true;
                  }
               } catch (RuntimeException var9) {
               }
            }
         }

         return false;
      }
   }

   @Override
   public boolean itemOnCooldown(ItemTarget target, boolean mainHand) {
      if (target != null && target.hasIdentity()) {
         ItemStack stack = this.firstMatchingStack(target);
         return stack != null && !stack.isEmpty() ? this.onCooldown(stack) : true;
      } else {
         ItemStack held = this.heldStack(mainHand);
         return held != null && !held.isEmpty() ? this.onCooldown(held) : false;
      }
   }

   private boolean onCooldown(ItemStack stack) {
      Long expiry = this.cooldownExpiry.get(cooldownGroupOf(stack));
      return expiry != null && expiry > System.currentTimeMillis();
   }

   private ItemStack heldStack(boolean mainHand) {
      synchronized (this.menuLock) {
         int h = mainHand ? 36 + Math.max(0, Math.min(8, this.selectedHotbar)) : 45;
         return h >= 0 && h < this.playerInv.size() ? this.playerInv.get(h) : ItemStack.EMPTY;
      }
   }

   private ItemStack firstMatchingStack(ItemTarget target) {
      ItemTarget itemOnly = target.copy();
      if (itemOnly.hasIdentity()) {
         itemOnly.slot = -1;
      }

      synchronized (this.menuLock) {
         for (int h = 9; h <= 45 && h < this.playerInv.size(); h++) {
            ItemStack s = this.playerInv.get(h);
            if (s != null && !s.isEmpty()) {
               int invIndex = h == 45 ? 40 : (h >= 36 ? h - 36 : h);
               if (itemOnly.score(s, invIndex) >= 0) {
                  return s;
               }
            }
         }
      }

      return ItemStack.EMPTY;
   }

   private static String cooldownGroupOf(ItemStack stack) {
      UseCooldown uc = (UseCooldown)stack.get(DataComponents.USE_COOLDOWN);
      if (uc != null && uc.cooldownGroup().isPresent()) {
         return ((Identifier)uc.cooldownGroup().get()).toString();
      } else {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         return id == null ? "" : id.toString();
      }
   }

   @Override
   public String captureItemText(CaptureValueAction a, ItemTarget filter) {
      ItemStack stack = this.resolveCaptureStack(a, filter);
      if (stack != null && !stack.isEmpty()) {
         return switch (a.itemText == null ? CaptureValueAction.ItemText.NAME : a.itemText) {
            case ID -> {
               Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
               yield id == null ? "" : id.toString();
            }
            case LORE -> loreText(stack);
            default -> stack.getHoverName().getString();
         };
      } else {
         return null;
      }
   }

   private ItemStack resolveCaptureStack(CaptureValueAction a, ItemTarget filter) {
      synchronized (this.menuLock) {
         return switch (a.source) {
            case CURSOR_ITEM -> this.carried;
            case HELD_ITEM -> {
               int h = 36 + Math.max(0, Math.min(8, this.selectedHotbar));
               yield h < this.playerInv.size() ? this.playerInv.get(h) : ItemStack.EMPTY;
            }
            case GUI_ITEM -> this.findCaptureStack(filter, a.slot, true);
            case PLAYER_ITEM -> this.findCaptureStack(filter, a.slot, false);
            default -> null;
         };
      }
   }

   private ItemStack findCaptureStack(ItemTarget filter, int slot, boolean guiRegion) {
      int size = this.menuSlots.size();
      int base = this.openContainerId >= 0 ? size - 36 : 0;

      for (int h = 0; h < size; h++) {
         ItemStack s = this.menuSlots.get(h);
         if (s != null && !s.isEmpty()) {
            boolean playerSlot = this.openContainerId < 0 || h >= base;
            if (guiRegion != playerSlot) {
               int visible = this.handlerToVisible(h, size);
               if ((slot < 0 || slot == visible || slot == h) && (filter == null || !filter.hasIdentity() || filter.score(s, visible) >= 0)) {
                  return s;
               }
            }
         }
      }

      return ItemStack.EMPTY;
   }

   private static String loreText(ItemStack stack) {
      ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
      if (lore == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();

         for (Component line : lore.lines()) {
            if (sb.length() > 0) {
               sb.append('\n');
            }

            sb.append(line.getString());
         }

         return sb.toString();
      }
   }

   @Override
   public List<String> tablistNames(boolean excludeSelf) {
      UUID self = this.identity != null && this.identity.user() != null ? this.identity.user().getProfileId() : null;
      List<String> names = new ArrayList<>(this.listedPlayers.size());

      for (UUID profileId : this.listedPlayers) {
         if (!excludeSelf || self == null || !self.equals(profileId)) {
            String name = this.playerNames.get(profileId);
            if (name != null && !name.isBlank() && !names.contains(name)) {
               names.add(name);
            }
         }
      }

      names.sort(String.CASE_INSENSITIVE_ORDER);
      return List.copyOf(names);
   }

   @Override
   public int requestCommandSuggestions(String command) {
      String query = command == null ? "" : command;
      return this.requestSuggestions(query);
   }

   @Override
   public List<String> commandSuggestions(int requestId) {
      MultiSession.Suggest suggest;
      synchronized (this.suggestionLock) {
         suggest = this.suggestionReplies.remove(requestId);
      }

      return suggest == null ? null : List.copyOf(suggest.entries());
   }

   @Override
   public List<CaptureValueAction.ScoreboardLine> scoreboardLines() {
      synchronized (this.scoreboardLock) {
         String objective = this.displayedSidebarObjective();
         if (objective != null && !objective.isBlank()) {
            Map<String, MultiSession.TrackedScore> tracked = this.scoresByObjective.get(objective);
            if (tracked != null && !tracked.isEmpty()) {
               MultiSession.ScoreObjective objectiveData = this.scoreObjectives
                  .getOrDefault(objective, new MultiSession.ScoreObjective(objective, Optional.empty()));
               List<Entry<String, MultiSession.TrackedScore>> entries = new ArrayList<>(tracked.entrySet());
               entries.removeIf(entryx -> ((String)entryx.getKey()).startsWith("#"));
               entries.sort((a, b) -> {
                  int scorex = Integer.compare(b.getValue().score(), a.getValue().score());
                  return scorex != 0 ? scorex : a.getKey().compareToIgnoreCase(b.getKey());
               });
               List<CaptureValueAction.ScoreboardLine> lines = new ArrayList<>(Math.min(15, entries.size()));

               for (int i = 0; i < entries.size() && i < 15; i++) {
                  Entry<String, MultiSession.TrackedScore> entry = entries.get(i);
                  String owner = entry.getKey();
                  MultiSession.TrackedScore score = entry.getValue();
                  String baseName = score.display() != null && !score.display().isBlank() ? score.display() : owner;
                  MultiSession.ScoreTeam team = this.scoreTeams.get(this.scoreOwnerTeams.get(owner));
                  String name = team == null ? baseName : team.prefix() + baseName + team.suffix();
                  NumberFormat format = score.numberFormat().orElseGet(() -> objectiveData.numberFormat().orElse(StyledFormat.SIDEBAR_DEFAULT));
                  String scoreText = componentText(format.format(score.score()));
                  lines.add(
                     new CaptureValueAction.ScoreboardLine(
                        objective + "\u001f" + owner,
                        i,
                        objective,
                        objectiveData.title(),
                        owner,
                        name,
                        scoreText,
                        scoreText.isEmpty() ? name : name + ": " + scoreText
                     )
                  );
               }

               return List.copyOf(lines);
            } else {
               return List.of();
            }
         } else {
            return List.of();
         }
      }
   }

   private String displayedSidebarObjective() {
      String selfName = this.identity != null && this.identity.user() != null ? this.identity.user().getName() : "";
      MultiSession.ScoreTeam team = this.scoreTeams.get(this.scoreOwnerTeams.get(selfName));
      if (team != null && team.color().isPresent()) {
         String teamObjective = this.displayedObjectives.get(team.color().get().displaySlot());
         if (teamObjective != null && !teamObjective.isBlank()) {
            return teamObjective;
         }
      }

      return this.displayedObjectives.getOrDefault(DisplaySlot.SIDEBAR, "");
   }

   @Override
   public boolean macroStepMet(WaitForMacroStepAction action) {
      return this.sink.macroStepMet(this, action);
   }

   private void disarmPacketCapture() {
      if (this.packetCaptureArmed) {
         this.setPacketCapture(false);
      }

      if (this.soundCaptureArmed) {
         this.setSoundCapture(false);
      }
   }

   @Override
   public boolean editSign(SignEditAction a, String l1, String l2, String l3, String l4) {
      if (a == null) {
         return false;
      } else {
         BlockPos pos;
         boolean front;
         switch (a.targetMode) {
            case MANUAL_POS:
               pos = new BlockPos(a.x, a.y, a.z);
               front = a.frontText;
               break;
            case LAST_INTERACTED_BLOCK:
               pos = this.lastInteractBlock;
               front = a.frontText;
               break;
            default:
               pos = this.signEditorPos;
               front = this.signEditorFront;
         }

         if (pos == null) {
            return false;
         } else {
            boolean sent = this.send(new ServerboundSignUpdatePacket(pos, front, l1, l2, l3, l4), false, false);
            if (sent) {
               this.signEditorOpen = false;
            }

            SignEditAction.CloseMode mode = a.closeMode == null ? SignEditAction.CloseMode.STAY_OPEN : a.closeMode;
            if (mode == SignEditAction.CloseMode.SEND_CLOSE_PACKET_ONLY) {
               this.closeContainerBurst(a.closePacketContainerId);
            } else if (mode == SignEditAction.CloseMode.CLOSE_WITH_PACKET) {
               this.closeContainerBurst(this.openContainerId >= 0 ? this.openContainerId : 0);
            }

            return sent;
         }
      }
   }

   private void recordSound(String id, double x, double y, double z) {
      synchronized (this.soundLogLock) {
         this.soundLog.addLast(new MultiSession.SndRec(++this.soundSeqCounter, id == null ? "" : id, x, y, z));

         while (this.soundLog.size() > 64) {
            this.soundLog.removeFirst();
         }
      }
   }

   @Override
   public void setSoundCapture(boolean on) {
      this.soundCaptureArmed = on;
      if (!on) {
         synchronized (this.soundLogLock) {
            this.soundLog.clear();
         }
      }
   }

   @Override
   public long soundSeq() {
      return this.soundSeqCounter;
   }

   @Override
   public boolean soundMatched(long baselineSeq, List<String> ids, boolean checkDistance, double maxDistance) {
      Vec3 self = this.position.position();
      double maxSq = maxDistance * maxDistance;
      synchronized (this.soundLogLock) {
         Iterator var11 = this.soundLog.iterator();

         while (true) {
            MultiSession.SndRec r;
            while (true) {
               if (!var11.hasNext()) {
                  return false;
               }

               r = (MultiSession.SndRec)var11.next();
               if (r.seq() > baselineSeq) {
                  if (!checkDistance) {
                     break;
                  }

                  double dx = r.x() - self.x;
                  double dy = r.y() - self.y;
                  double dz = r.z() - self.z;
                  if (!(dx * dx + dy * dy + dz * dz > maxSq)) {
                     break;
                  }
               }
            }

            if (ids == null || ids.isEmpty()) {
               return true;
            }

            for (String want : ids) {
               if (want != null && !want.isBlank() && soundIdMatches(want, r.id())) {
                  return true;
               }
            }
         }
      }
   }

   private static boolean soundIdMatches(String want, String actual) {
      String w = want.trim().toLowerCase(Locale.ROOT);
      String a = actual == null ? "" : actual.trim().toLowerCase(Locale.ROOT);
      if (w.equals(a)) {
         return true;
      } else {
         String wp = w.contains(":") ? w.substring(w.indexOf(58) + 1) : w;
         String ap = a.contains(":") ? a.substring(a.indexOf(58) + 1) : a;
         return wp.equals(ap);
      }
   }

   @Override
   public int nearestEntity(String type) {
      return this.entities.nearest(type, this.position.position());
   }

   @Override
   public double[] entityPos(int entityId) {
      return this.entities.pos(entityId);
   }

   @Override
   public String runClient(String name, String args) {
      return this.runClientAction(name, args);
   }

   @Override
   public void useItemPhase(UseItemPhaseAction.Phase phase, boolean offhand) {
      InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      PositionMoveRotation current = this.position;
      switch (phase == null ? UseItemPhaseAction.Phase.USE_ONCE : phase) {
         case USE_ONCE:
         case START_USE:
         case USE_BLOCK:
            this.send(new ServerboundUseItemPacket(hand, ++this.useSeq, current.yRot(), current.xRot()), false, false);
            break;
         case RELEASE_USE:
            this.send(
               new ServerboundPlayerActionPacket(
                  net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
               ),
               false,
               false
            );
            break;
         case SWING:
            this.send(new ServerboundSwingPacket(hand), false, false);
      }
   }

   @Override
   public String chat(String message) {
      return this.sendConsoleLine(message);
   }

   @Override
   public boolean startSelfMacro(String macroName) {
      RiptideMacro found = RiptideMacroManager.get().get(macroName);
      if (found != null && found.actions != null && !found.actions.isEmpty()) {
         RiptideMacro copy = found.deepCopy();
         this.sink.macroChained(this, copy);
         this.startAssignedMacro(copy);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void stopSelfMacro() {
      this.stopMacro();
   }

   @Override
   public void disconnectBot(String reason) {
      this.sink.macroDisconnected(this);
      this.stopMacro();
      this.disconnect(reason == null ? "Macro" : reason);
   }

   @Override
   public float currentYaw() {
      return this.position.yRot();
   }

   @Override
   public void look(float yaw, float pitch) {
      synchronized (this.positionLock) {
         this.position = this.position.withRotation(yaw, pitch);
      }

      this.send(new Rot(yaw, pitch, true, false), false, true);
   }

   @Override
   public void move(double worldDx, double worldDz, long durationMs) {
      this.walkX = worldDx;
      this.walkZ = worldDz;
      this.announceSprint(this.inputSprint);
      long now = System.currentTimeMillis();
      long until = now + Math.max(0L, durationMs);
      this.walkUntil = until;
      this.macroMotorUntil = Math.max(this.macroMotorUntil, until);
      this.walkGrounded = true;
      this.walkProbeAt = now + 500L;
      this.rapidWalkCorrections = 0;
      this.lastWalkCorrectionAt = 0L;
      this.primeFallTick = false;
      this.moveActiveUntil = Math.max(this.moveActiveUntil, until);
   }

   private void trackWalkCorrection(long now) {
      this.rapidWalkCorrections = now - this.lastWalkCorrectionAt <= 150L ? this.rapidWalkCorrections + 1 : 1;
      this.lastWalkCorrectionAt = now;
      if (this.rapidWalkCorrections >= 8) {
         this.walkUntil = 0L;
         this.rapidWalkCorrections = 0;
         if (now - this.lastWalkBlockedNoteAt > 30000L) {
            this.lastWalkBlockedNoteAt = now;
            this.appendLocal("Walk blocked by terrain; move cancelled early. Clear the path or use a clip action.");
         }
      }
   }

   @Override
   public int clip(double dx, double dy, double dz, int segments, boolean onGround) {
      int count = Math.max(1, Math.min(64, segments));
      PositionMoveRotation start;
      synchronized (this.positionLock) {
         start = this.position;
      }

      Vec3 origin = start.position();
      Vec3 from = origin;
      List<PacketClipSafety.Step> steps = new ArrayList<>();

      for (int i = 1; i <= count; i++) {
         double progress = (double)i / count;
         Vec3 next = new Vec3(origin.x + dx * progress, origin.y + dy * progress, origin.z + dz * progress);

         for (PacketClipSafety.Step step : PacketClipSafety.positionSteps(from, next, onGround)) {
            steps.add(step);
            from = step.position();
         }
      }

      if (steps.isEmpty()) {
         return 0;
      } else {
         synchronized (this.clipLock) {
            this.clipQueue.clear();
            this.clipQueue.addAll(steps);
         }

         synchronized (this.positionLock) {
            this.position = new PositionMoveRotation(from, Vec3.ZERO, start.yRot(), start.xRot());
         }

         this.motionY = 0.0;
         this.walkX = 0.0;
         this.walkZ = 0.0;
         this.walkUntil = 0L;
         this.walkGrounded = false;
         this.grounded = onGround;
         this.primeFallTick = false;
         this.drainClip(System.currentTimeMillis());
         return steps.size();
      }
   }

   @Override
   public boolean clipBusy() {
      synchronized (this.clipLock) {
         return !this.clipQueue.isEmpty();
      }
   }

   @Override
   public long clipDrainMillis() {
      int remaining;
      synchronized (this.clipLock) {
         remaining = this.clipQueue.size();
      }

      return this.timerBudget.drainMillis(System.nanoTime(), remaining);
   }

   private void cancelClip() {
      synchronized (this.clipLock) {
         this.clipQueue.clear();
      }
   }

   private boolean drainClip(long now) {
      while (true) {
         PacketClipSafety.Step step;
         synchronized (this.clipLock) {
            step = this.clipQueue.peek();
         }

         if (step == null) {
            return false;
         }

         MultiSession.PairResult result = this.sendTickPair(new Pos(step.position(), step.onGround(), false), false, false);
         if (result == MultiSession.PairResult.NO_BUDGET) {
            return true;
         }

         synchronized (this.clipLock) {
            this.clipQueue.poll();
         }

         if (result == MultiSession.PairResult.REFUSED) {
            this.cancelClip();
            return false;
         }

         this.lastMovementAt = now;
         this.outboundTruth.recordSent(step.position(), now);
      }
   }

   @Override
   public void setSneak(boolean on) {
      this.inputShift = on;
      this.sendInput();
   }

   @Override
   public void setSprint(boolean on) {
      this.inputSprint = on;
      this.sendInput();
      this.announceSprint(on);
      synchronized (this.positionLock) {
         if (System.currentTimeMillis() < this.walkUntil) {
            double mag = Math.sqrt(this.walkX * this.walkX + this.walkZ * this.walkZ);
            if (mag > 1.0E-6) {
               double target = on ? 0.26 : 0.2;
               this.walkX = this.walkX / mag * target;
               this.walkZ = this.walkZ / mag * target;
            }
         }
      }
   }

   @Override
   public boolean sprinting() {
      return this.inputSprint;
   }

   private void sendInput() {
      this.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, this.inputShift, this.inputSprint)), false, false);
   }

   private void announceSprint(boolean on) {
      if (this.playerEntityId >= 0) {
         if (this.sprintAnnounced != on) {
            this.sprintAnnounced = on;
            ByteBuf raw = Unpooled.buffer();

            try {
               FriendlyByteBuf buf = new FriendlyByteBuf(raw);
               buf.writeVarInt(this.playerEntityId);
               buf.writeEnum(
                  on
                     ? net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_SPRINTING
                     : net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
               );
               buf.writeVarInt(0);
               ServerboundPlayerCommandPacket packet = (ServerboundPlayerCommandPacket)ServerboundPlayerCommandPacket.STREAM_CODEC.decode(buf);
               this.send(packet, false, false);
            } catch (Throwable var8) {
               riptide.RiptideClientAddon.LOG.warn("[Multi] {} sprint announce failed", this.spec.accountId(), var8);
            } finally {
               raw.release();
            }
         }
      }
   }

   @Override
   public void jump() {
      long now = System.currentTimeMillis();
      this.motionY = 0.42;
      this.walkGrounded = false;
      this.grounded = false;
      this.primeFallTick = false;
      this.fallModeUntil = now + 15000L;
      this.fallMode = true;
      this.moveActiveUntil = Math.max(this.moveActiveUntil, now + 1200L);
      this.macroMotorUntil = Math.max(this.macroMotorUntil, now + 15000L);
   }

   @Override
   public String interactEntity(int entityId, boolean attack) {
      if (entityId < 0) {
         return "No entity";
      } else {
         if (!attack) {
            this.lastInteractEntityId = entityId;
            this.lastInteractBlock = null;
         }

         if (attack) {
            boolean sent = this.pilotAttackEntity(entityId);
            return sent ? "Sent" : "attack packet blocked or disconnected";
         } else {
            boolean sent = this.send(new ServerboundInteractPacket(entityId, InteractionHand.MAIN_HAND, Vec3.ZERO, false), false, false);
            return sent ? "Sent" : "interact packet blocked or disconnected";
         }
      }
   }

   @Override
   public String useOnBlock(int x, int y, int z, String face) {
      Direction dir = directionByName(face);
      BlockPos pos = new BlockPos(x, y, z);
      this.lastInteractBlock = pos;
      this.lastInteractEntityId = -1;
      Vec3 hitVec = new Vec3(x + 0.5 + dir.getStepX() * 0.5, y + 0.5 + dir.getStepY() * 0.5, z + 0.5 + dir.getStepZ() * 0.5);
      this.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, dir, pos, false), ++this.useSeq), false, false);
      return "Sent";
   }

   @Override
   public String breakBlock(int x, int y, int z, String face) {
      Direction dir = directionByName(face);
      BlockPos pos = new BlockPos(x, y, z);
      this.send(
         new ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, dir, ++this.useSeq
         ),
         false,
         false
      );
      this.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
      this.send(
         new ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, ++this.useSeq),
         false,
         false
      );
      return "Sent";
   }

   private static Direction directionByName(String name) {
      if (name != null) {
         try {
            return Direction.valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
         }
      }

      return Direction.UP;
   }

   private static String entityTypeKey(EntityType<?> type) {
      try {
         return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
      } catch (RuntimeException var2) {
         return "";
      }
   }

   private static String normalizeItemQuery(String query) {
      if (query == null) {
         return "";
      } else {
         String q = query.trim().toLowerCase(Locale.ROOT);
         int colon = q.indexOf(58);
         if (colon >= 0) {
            q = q.substring(colon + 1);
         }

         return q.replace('_', ' ').trim();
      }
   }

   private static boolean itemMatches(ItemStack stack, String normalizedQuery) {
      if (normalizedQuery.isEmpty()) {
         return true;
      } else {
         String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT).replace('_', ' ');
         if (name.contains(normalizedQuery)) {
            return true;
         } else {
            try {
               String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT).replace('_', ' ');
               return id.contains(normalizedQuery);
            } catch (RuntimeException var4) {
               return false;
            }
         }
      }
   }

   void tick(long now) {
      MultiSession.Status currentStatus = this.status.get();
      if (currentStatus != MultiSession.Status.DISCONNECTED && currentStatus != MultiSession.Status.FAILED) {
         Connection current = this.connection;
         if (current != null) {
            current.tick();
         }

         this.maybeSendPlayerLoaded(now);
         this.driveMacro(now);
         if (!this.piloted) {
            this.captcha.tick(now);
            if (this.loginMode == MultiProfile.LoginMode.Auto) {
               this.autoLogin.tick(now);
            }

            this.maybeAutoAccept(now);
         }

         if (this.loginMacroRun && this.macroRun != null && now >= this.loginMacroDeadline) {
            this.stopMacro();
         }

         MultiSession.Status afterNetworkTick = this.status.get();
         if (afterNetworkTick != MultiSession.Status.QUEUED
            && afterNetworkTick != MultiSession.Status.READY
            && afterNetworkTick != MultiSession.Status.DISCONNECTED
            && afterNetworkTick != MultiSession.Status.FAILED
            && now - this.statusSince >= 60000L
            && (this.macroRun == null || !this.macroRun.hasActiveCustomMenuDeadline(now))
            && !this.canAnswerLoginScreen()) {
            this.fail("Timed out during " + this.detail);
         } else if (!this.closed.get() && this.status.get() == MultiSession.Status.READY && this.hasPosition) {
            this.pumpClickTransactions(now);
            this.sendDeferredCloseIfReady();
            if (this.health <= 0.0F) {
               this.cancelClip();
               if (this.respawnAt == 0L) {
                  this.respawnAt = now + 1000L;
               } else if (now >= this.respawnAt) {
                  this.send(
                     new ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN),
                     false,
                     false
                  );
                  this.respawnAt = now + 1000L;
               }
            } else {
               this.respawnAt = 0L;
               if (!this.drainClip(now)) {
                  if (!this.piloted || this.macroOwnsPilot()) {
                     MultiPacketPolicy activePolicy = this.policy;
                     if (this.gravitySettleRequest) {
                        this.gravitySettleRequest = false;
                        if (!this.inVehicle && !this.postVehicleFall && now >= this.walkUntil) {
                           synchronized (this.positionLock) {
                              if (this.motionY > 0.0) {
                                 this.motionY = 0.0;
                              }
                           }

                           this.grounded = false;
                           this.primeFallTick = true;
                           this.fallModeUntil = now + 15000L;
                           this.fallMode = true;
                           this.moveActiveUntil = Math.max(this.moveActiveUntil, now + 5000L);
                        }
                     }

                     boolean walking = now < this.walkUntil;
                     if (this.fallMode) {
                        if (now >= this.fallModeUntil) {
                           this.fallMode = false;
                           this.grounded = true;
                           synchronized (this.positionLock) {
                              this.motionY = 0.0;
                           }
                        } else {
                           this.moveActiveUntil = Math.max(this.moveActiveUntil, now + 800L);
                        }
                     }

                     boolean moving = now < this.moveActiveUntil || walking;
                     if (this.inVehicle) {
                        this.streamVehicle();
                     } else if (this.postVehicleFall) {
                        if (now >= this.postVehicleFallUntil) {
                           this.postVehicleFall = false;
                           this.grounded = true;
                        } else {
                           this.grounded = false;
                           this.sendPosition(false);
                        }
                     } else if (moving) {
                        synchronized (this.positionLock) {
                           if (walking) {
                              Vec3 wp = this.position.position();
                              this.position = new PositionMoveRotation(
                                 new Vec3(wp.x + this.walkX, wp.y, wp.z + this.walkZ),
                                 this.position.deltaMovement(),
                                 this.position.yRot(),
                                 this.position.xRot()
                              );
                              if (this.walkGrounded && now >= this.walkProbeAt) {
                                 this.walkGrounded = false;
                              }
                           }

                           if (this.kbTicks > 0) {
                              this.kbTicks--;
                              Vec3 kp = this.position.position();
                              this.position = new PositionMoveRotation(
                                 new Vec3(kp.x + this.kbX, kp.y, kp.z + this.kbZ), this.position.deltaMovement(), this.position.yRot(), this.position.xRot()
                              );
                              this.kbX *= 0.6;
                              this.kbZ *= 0.6;
                           }

                           if (walking && this.walkGrounded && this.motionY <= 0.0) {
                              this.motionY = 0.0;
                              this.grounded = true;
                           } else if (this.primeFallTick) {
                              this.primeFallTick = false;
                              this.grounded = false;
                           } else if (walking) {
                              this.applyWalkGravity();
                           } else if (this.fallMode) {
                              this.applyFallGravity();
                           } else {
                              this.applyGravity();
                           }
                        }

                        this.sendPosition(false);
                     } else if (shouldSendIdleHeartbeat(activePolicy.autoPosition(), this.lastMovementAt, now)) {
                        this.sendPosition(false);
                     }

                     if (activePolicy.autoLook() && !activePolicy.autoPosition() && !moving && now - this.lastLookAt >= 1000L) {
                        this.lastLookAt = now;
                        PositionMoveRotation p = this.position;
                        this.send(new Rot(p.yRot(), p.xRot(), true, false), false, true);
                     }

                     if (activePolicy.autoSwing() && now - this.lastSwingAt >= 1000L) {
                        this.lastSwingAt = now;
                        this.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
                     }
                  }
               }
            }
         }
      }
   }

   private void applyWalkGravity() {
      double next = (this.motionY - 0.08) * 0.98;
      this.motionY = Math.max(-3.92, next);
      Vec3 p = this.position.position();
      this.position = new PositionMoveRotation(
         new Vec3(p.x, p.y + this.motionY, p.z), this.position.deltaMovement(), this.position.yRot(), this.position.xRot()
      );
      this.grounded = true;
   }

   private void applyGravity() {
      Vec3 p = this.position.position();
      double top = this.columnGroundTop(p.x, p.y, p.z);
      if (Double.isNaN(top)) {
         this.grounded = true;
         this.motionY = 0.0;
      } else {
         double next = (this.motionY - 0.08) * 0.98;
         this.motionY = Math.max(-3.92, next);
         double newY = p.y + this.motionY;
         if (newY <= top) {
            newY = top;
            this.motionY = 0.0;
            this.grounded = true;
         } else {
            this.grounded = false;
         }

         this.position = new PositionMoveRotation(new Vec3(p.x, newY, p.z), this.position.deltaMovement(), this.position.yRot(), this.position.xRot());
      }
   }

   private void applyFallGravity() {
      double next = (this.motionY - 0.08) * 0.98;
      this.motionY = Math.max(-3.92, next);
      Vec3 p = this.position.position();
      double newY = p.y + this.motionY;
      double top = this.columnGroundTop(p.x, p.y, p.z);
      if (!Double.isNaN(top) && newY <= top) {
         newY = top;
         this.motionY = 0.0;
         this.grounded = true;
         this.fallMode = false;
      } else {
         this.grounded = false;
      }

      this.position = new PositionMoveRotation(new Vec3(p.x, newY, p.z), this.position.deltaMovement(), this.position.yRot(), this.position.xRot());
   }

   private double columnGroundTop(double x, double y, double z) {
      int bx = (int)Math.floor(x);
      int bz = (int)Math.floor(z);
      double best = Double.NaN;

      for (Entry<Long, BlockState> e : this.blockUpdates.entrySet()) {
         long key = e.getKey();
         if (BlockPos.getX(key) == bx && BlockPos.getZ(key) == bz) {
            BlockState st = e.getValue();
            if (st != null && !st.isAir()) {
               double h = collisionTopHeight(st);
               if (!(h <= 0.0)) {
                  double topY = BlockPos.getY(key) + h;
                  if (topY <= y + 0.5 && (Double.isNaN(best) || topY > best)) {
                     best = topY;
                  }
               }
            }
         }
      }

      return best;
   }

   private static double collisionTopHeight(BlockState st) {
      try {
         VoxelShape shape = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
         return shape.isEmpty() ? 0.0 : shape.max(Axis.Y);
      } catch (Throwable var2) {
         return 1.0;
      }
   }

   private void handleSetPassengers(ClientboundSetPassengersPacket passengers) {
      int veh = passengers.getVehicle();
      boolean mountsUs = false;

      for (int p : passengers.getPassengers()) {
         if (p == this.playerEntityId) {
            mountsUs = true;
            break;
         }
      }

      if (mountsUs) {
         double[] vp = this.entities.pos(veh);
         synchronized (this.positionLock) {
            this.vehicleId = veh;
            this.inVehicle = true;
            if (vp != null) {
               this.vehicleX = vp[0];
               this.vehicleY = vp[1];
               this.vehicleZ = vp[2];
            } else {
               Vec3 pp = this.position.position();
               this.vehicleX = pp.x;
               this.vehicleY = pp.y;
               this.vehicleZ = pp.z;
            }

            this.vehicleFallMotion = 0.0;
            this.vehicleYaw = this.position.yRot();
            this.grounded = false;
            this.primeFallTick = false;
            this.postVehicleFall = false;
            this.fallMode = false;
            this.kbTicks = 0;
         }

         this.moveActiveUntil = Math.max(this.moveActiveUntil, System.currentTimeMillis() + 5000L);
      } else if (this.inVehicle && veh == this.vehicleId) {
         this.dismountVehicle();
      }
   }

   private void dismountVehicle() {
      if (this.inVehicle) {
         this.inVehicle = false;
         long now = System.currentTimeMillis();
         synchronized (this.positionLock) {
            this.grounded = false;
            this.motionY = 0.0;
            this.primeFallTick = false;
            this.postVehicleFall = true;
            this.fallMode = false;
            this.kbTicks = 0;
         }

         this.postVehicleFallUntil = now + 1000L;
         this.moveActiveUntil = Math.max(this.moveActiveUntil, now + 2000L);
      }
   }

   private void streamVehicle() {
      if (this.timerBudget.reserve(System.nanoTime())) {
         float yaw;
         float pitch;
         double vx;
         double vy;
         double vz;
         synchronized (this.positionLock) {
            this.vehicleFallMotion -= 0.04F;
            this.vehicleY = this.vehicleY + this.vehicleFallMotion;
            this.vehicleYaw += 3.0F;
            yaw = this.vehicleYaw;
            pitch = this.position.xRot();
            vx = this.vehicleX;
            vy = this.vehicleY;
            vz = this.vehicleZ;
         }

         this.send(new ServerboundPaddleBoatPacket(false, false), false, false);
         this.send(new ServerboundMoveVehiclePacket(new Vec3(vx, vy, vz), yaw, pitch, false), false, true);
         this.send(new Rot(yaw, pitch, false, false), false, true);
         this.send(ServerboundClientTickEndPacket.INSTANCE, true, false);
      }
   }

   static boolean shouldSendIdleHeartbeat(boolean enabled, long lastMovementAt, long now) {
      return enabled && now - lastMovementAt >= 1000L;
   }

   private boolean sendPosition(boolean critical) {
      PositionMoveRotation current = this.position;
      if (this.sendTickPair(new PosRot(current.position(), current.yRot(), current.xRot(), this.grounded, false), critical, critical)
         != MultiSession.PairResult.SENT) {
         return false;
      } else {
         this.lastMovementAt = System.currentTimeMillis();
         this.outboundTruth.recordSent(current.position(), this.lastMovementAt);
         return true;
      }
   }

   private MultiSession.PairResult sendTickPair(Packet<?> movement, boolean critical, boolean ungated) {
      synchronized (this.movePairLock) {
         if (!ungated && !this.timerBudget.reserve(System.nanoTime())) {
            return MultiSession.PairResult.NO_BUDGET;
         } else if (!this.send(movement, critical, true)) {
            return MultiSession.PairResult.REFUSED;
         } else {
            this.send(ServerboundClientTickEndPacket.INSTANCE, true, false);
            return MultiSession.PairResult.SENT;
         }
      }
   }

   private void sendChatAcknowledgement() {
      int offset;
      synchronized (this.chatStateLock) {
         offset = this.lastSeenMessages.getAndClearOffset();
      }

      if (offset > 0) {
         this.send(new ServerboundChatAckPacket(offset), true, false);
      }
   }

   private boolean send(Packet<?> packet, boolean critical, boolean movementPacket) {
      Connection current = this.connection;
      if (packet != null && current != null && !this.closed.get()) {
         if (packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == 0 && this.xCarryForced) {
            this.refreshXCarryActive();
            if (this.xCarryActive) {
               return true;
            }
         }

         if (!this.policy.allows(MultiPacketPolicy.Direction.C2S, packet.getClass().getName(), critical, movementPacket)) {
            return false;
         } else {
            if (this.packetCaptureArmed) {
               this.recordPacket(true, packet);
            }

            current.send(packet);
            return true;
         }
      } else {
         return false;
      }
   }

   public void disconnect(String reason) {
      String message = reason == null ? "Disconnected" : reason;
      if (!this.closed.compareAndSet(false, true)) {
         this.setStatus(MultiSession.Status.DISCONNECTED, message);
      } else {
         this.invalidateMenu(true, true);
         Connection current = this.connection;
         if (current != null) {
            current.disconnect(Component.literal(message));
            current.handleDisconnection();
            MultiConnectionContext.remove(current);
         }

         this.setStatus(MultiSession.Status.DISCONNECTED, message);
      }
   }

   void failExternal(String reason) {
      this.fail(reason == null ? "Connection failed" : reason);
   }

   private void handleDisconnected(DisconnectionDetails details, int epoch) {
      if (epoch == this.connectEpoch) {
         MultiConnectionContext.remove(this.connection);
         if (this.closed.compareAndSet(false, true)) {
            this.invalidateMenu(true, true);
            String reason = details == null ? "Disconnected" : details.reason().getString();
            this.setStatus(MultiSession.Status.DISCONNECTED, reason);
         }
      }
   }

   private void fail(String message) {
      if (this.closed.compareAndSet(false, true)) {
         this.invalidateMenu(true, true);
         Connection current = this.connection;
         if (current != null) {
            current.disconnect(Component.literal(message));
            MultiConnectionContext.remove(current);
         }

         this.setStatus(MultiSession.Status.FAILED, message);
         if (current != null) {
            current.handleDisconnection();
         }
      }
   }

   private void setStatus(MultiSession.Status value, String text) {
      if (!this.closed.get() || value == MultiSession.Status.DISCONNECTED || value == MultiSession.Status.FAILED) {
         String updatedDetail = text == null ? value.name() : text;
         MultiSession.Status previous = this.status.get();
         String previousDetail = this.detail;
         this.detail = updatedDetail;
         if (previous != value) {
            this.statusSince = System.currentTimeMillis();
         }

         if (value == MultiSession.Status.DISCONNECTED || value == MultiSession.Status.FAILED) {
            this.ping = -1;
            this.macroMotorUntil = 0L;
            MultiSession.MacroProgress progress = this.macroProgress;
            if (progress != null && progress.running()) {
               this.macroProgress = new MultiSession.MacroProgress(
                  progress.macroName(), false, progress.step(), progress.totalSteps(), progress.loop(), "Disconnected; waiting for retry"
               );
               this.macroStatus = "";
            }
         }

         this.status.set(value);
         if (previous != value || !Objects.equals(previousDetail, updatedDetail)) {
            this.sink.stateChanged(this);
         }
      }
   }

   private void appendLocal(String line) {
      this.sink.chat(this, Component.literal(line));
   }

   private void resetChatState() {
      synchronized (this.chatStateLock) {
         this.nextChatIndex = 0;
         this.lastSeenMessages = new LastSeenMessagesTracker(20);
         this.signatureCache = MessageSignatureCache.createDefault();
         this.signedEncoder = Encoder.UNSIGNED;
         this.chatSession = null;
         this.serverAssignedUuid = null;
      }
   }

   private static boolean isCriticalInbound(Packet<?> packet) {
      return packet instanceof ClientboundKeepAlivePacket
         || packet instanceof ClientboundPingPacket
         || packet instanceof ClientboundDisconnectPacket
         || packet instanceof ClientboundLoginDisconnectPacket
         || packet instanceof ClientboundHelloPacket
         || packet instanceof ClientboundLoginFinishedPacket
         || packet instanceof ClientboundLoginCompressionPacket
         || packet instanceof ClientboundCustomQueryPacket
         || packet instanceof ClientboundFinishConfigurationPacket
         || packet instanceof ClientboundRegistryDataPacket
         || packet instanceof ClientboundUpdateEnabledFeaturesPacket
         || packet instanceof ClientboundSelectKnownPacks
         || packet instanceof ClientboundResetChatPacket
         || packet instanceof ClientboundCodeOfConductPacket
         || packet instanceof ClientboundUpdateTagsPacket
         || packet instanceof ClientboundCookieRequestPacket
         || packet instanceof ClientboundStoreCookiePacket
         || packet instanceof ClientboundResourcePackPushPacket
         || packet instanceof ClientboundTransferPacket
         || packet instanceof ClientboundLoginPacket
         || packet instanceof ClientboundStartConfigurationPacket
         || packet instanceof ClientboundPlayerPositionPacket
         || packet instanceof ClientboundPlayerRotationPacket
         || packet instanceof ClientboundGameEventPacket
         || packet instanceof ClientboundChunkBatchStartPacket
         || packet instanceof ClientboundChunkBatchFinishedPacket
         || packet instanceof ClientboundAddEntityPacket
         || packet instanceof ClientboundMoveEntityPacket
         || packet instanceof ClientboundEntityPositionSyncPacket
         || packet instanceof ClientboundTeleportEntityPacket
         || packet instanceof ClientboundRotateHeadPacket
         || packet instanceof ClientboundSetEntityDataPacket
         || packet instanceof ClientboundSetEntityMotionPacket
         || packet instanceof ClientboundRemoveEntitiesPacket
         || packet instanceof ClientboundSetPassengersPacket
         || packet instanceof ClientboundMoveMinecartPacket
         || packet instanceof ClientboundShowDialogPacket
         || packet instanceof ClientboundClearDialogPacket;
   }

   private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) {
         return null;
      } else if (type == boolean.class) {
         return false;
      } else if (type == char.class) {
         return '\u0000';
      } else if (type == byte.class) {
         return (byte)0;
      } else if (type == short.class) {
         return (short)0;
      } else if (type == int.class) {
         return 0;
      } else if (type == long.class) {
         return 0L;
      } else if (type == float.class) {
         return 0.0F;
      } else {
         return type == double.class ? 0.0 : null;
      }
   }

   private static String shortError(Throwable error) {
      if (error == null) {
         return "Unknown error";
      } else {
         Throwable cause = error;

         while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
         }

         if (cause instanceof ReadTimeoutException) {
            return "Read timed out";
         } else if (cause instanceof SocketTimeoutException) {
            return "Timed out";
         } else {
            String message = cause.getMessage();
            if (message == null || message.isBlank()) {
               message = cause.getClass().getSimpleName();
            }

            message = message.replace('\n', ' ').replace('\r', ' ').trim();
            return message.length() > 180 ? message.substring(0, 177) + "..." : message;
         }
      }
   }

   public static enum ClickCompletion {
      QUEUED,
      SENT,
      SETTLING,
      COMPLETE,
      CANCELLED,
      TIMED_OUT,
      FAILED;
   }

   public static final class ClickTicket {
      private final long id;
      private final long generation;
      private volatile MultiSession.ClickCompletion completion = MultiSession.ClickCompletion.QUEUED;

      private ClickTicket(long id, long generation) {
         this.id = id;
         this.generation = generation;
      }

      public long id() {
         return this.id;
      }

      public long generation() {
         return this.generation;
      }

      public MultiSession.ClickCompletion completion() {
         return this.completion;
      }

      public boolean terminal() {
         return this.completion == MultiSession.ClickCompletion.COMPLETE
            || this.completion == MultiSession.ClickCompletion.CANCELLED
            || this.completion == MultiSession.ClickCompletion.TIMED_OUT
            || this.completion == MultiSession.ClickCompletion.FAILED;
      }
   }

   private static enum DeferredMenuClose {
      NONE,
      SILENT,
      PACKET,
      DESYNC;
   }

   public static enum DisplayState {
      GREEN,
      YELLOW,
      RED;
   }

   public record MacroProgress(String macroName, boolean running, int step, int totalSteps, int loop, String detail) {
      static MultiSession.MacroProgress idle() {
         return new MultiSession.MacroProgress("", false, 0, 0, 0, "");
      }
   }

   public record MacroQueue(long queuedAt, long startAt) {
      public static final MultiSession.MacroQueue NONE = new MultiSession.MacroQueue(0L, 0L);

      public boolean pending(long now) {
         return this.startAt > now;
      }

      public long remainingMs(long now) {
         return Math.max(0L, this.startAt - now);
      }

      public double elapsedRatio(long now) {
         long span = this.startAt - this.queuedAt;
         return span <= 0L ? 1.0 : Math.max(0.0, Math.min(1.0, (double)(now - this.queuedAt) / span));
      }
   }

   public record MenuContext(
      MultiSession.MenuPhase phase,
      long generation,
      int containerId,
      String typeId,
      int revision,
      Component title,
      List<ItemStack> slots,
      ItemStack cursor,
      boolean inventorySynchronized,
      boolean interactive,
      boolean dismissed,
      int pendingClicks,
      boolean synchronizationBlocked
   ) {
   }

   public record MenuExtras(String typeId, int[] data, List<MultiSession.TradeView> trades, int villagerLevel, int villagerXp, boolean showProgress) {
      public static final MultiSession.MenuExtras NONE = new MultiSession.MenuExtras("", new int[0], List.of(), 0, 0, false);
   }

   public static enum MenuPhase {
      SYNCING,
      INVENTORY,
      CONTAINER,
      SYNTHETIC;
   }

   public record MenuView(
      Component title,
      List<MultiSession.ViewSlot> slots,
      ItemStack carried,
      int syncId,
      int stateId,
      MultiSession.MenuPhase phase,
      boolean interactive,
      long generation,
      int pendingClicks,
      boolean synchronizationBlocked,
      MultiSession.MenuExtras extras
   ) {
   }

   private final class MultiAutoLoginHost implements AutoLoginHost {
      private MultiAutoLoginHost() {
         Objects.requireNonNull(MultiSession.this);
         super();
      }

      @Override
      public String password() {
         return MultiSession.this.loginPassword();
      }

      @Override
      public boolean spawnedInWorld() {
         return MultiSession.this.status.get() == MultiSession.Status.READY;
      }

      @Override
      public boolean canSendChat() {
         return MultiSession.this.status.get() == MultiSession.Status.READY;
      }

      @Override
      public CustomMenuSnapshot customMenu() {
         return MultiSession.this.customMenus.current();
      }

      @Override
      public boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
         CustomMenuSubmitResult result = MultiSession.this.submitCustomMenu(snapshot, submission);
         return result != null && result.success();
      }

      @Override
      public boolean sendCommandLine(String line) {
         return "Sent".equals(MultiSession.this.sendConsoleLine(line));
      }

      @Override
      public boolean screenOwnedElsewhere() {
         MultiMacroRun run = MultiSession.this.macroRun;
         return run != null && run.handlesCustomMenu();
      }

      @Override
      public void note(String message) {
         MultiSession.this.appendLocal(message);
      }

      @Override
      public void needsPassword(String context) {
         if (!MultiSession.this.missingPasswordAlerted) {
            MultiSession.this.missingPasswordAlerted = true;
            MultiSession.this.sink.customMenuNeedsPassword(MultiSession.this, context);
         }
      }
   }

   private static enum PairResult {
      SENT,
      NO_BUDGET,
      REFUSED;
   }

   private static enum Phase {
      LOGIN(ConnectionProtocol.LOGIN),
      CONFIGURATION(ConnectionProtocol.CONFIGURATION),
      PLAY(ConnectionProtocol.PLAY);

      final ConnectionProtocol protocol;

      private Phase(ConnectionProtocol protocol) {
         this.protocol = protocol;
      }
   }

   private record PktRec(long seq, boolean c2s, Packet<?> packet) {
   }

   private record QueuedClick(
      MultiSession.ClickTicket ticket,
      long epoch,
      int containerId,
      int slot,
      int button,
      ContainerInput input,
      boolean allowSynthetic,
      Packet<?> rawPacket,
      int repetitionsRemaining
   ) {
   }

   private record SavedMenu(int containerId, int stateId, Component title, String titleText, List<ItemStack> slots, ItemStack carried) {
   }

   private record ScoreObjective(String title, Optional<NumberFormat> numberFormat) {
   }

   private record ScoreTeam(String prefix, String suffix, Optional<TeamColor> color) {
   }

   interface Sink {
      String identityRejection(MultiSession var1, UUID var2);

      void stateChanged(MultiSession var1);

      void chat(MultiSession var1, Component var2);

      default void menuClosed(MultiSession session) {
      }

      default void note(MultiSession session, String text) {
      }

      boolean macroStepMet(MultiSession var1, WaitForMacroStepAction var2);

      default void macroChained(MultiSession session, RiptideMacro macro) {
      }

      default void macroDisconnected(MultiSession session) {
      }

      default void customMenuNeedsPassword(MultiSession session, String title) {
      }
   }

   public record Snapshot(
      String accountId,
      String accountName,
      String proxyName,
      String protocol,
      MultiSession.Status status,
      String detail,
      int ping,
      boolean connected,
      boolean ready,
      long lastInboundAt,
      String openScreen,
      boolean customMenuOpen,
      String heldItem,
      int hotbarSlot,
      String dimension,
      boolean hasPosition,
      double x,
      double y,
      double z,
      float health,
      float maxHealth,
      int food,
      long menuRevision,
      String macroStatus,
      MultiSession.MacroProgress macroProgress,
      MultiSession.MacroQueue macroQueue
   ) {
      public MultiSession.DisplayState displayState(long now) {
         if (this.status != MultiSession.Status.FAILED && this.status != MultiSession.Status.DISCONNECTED) {
            boolean needsChannel = this.status == MultiSession.Status.LOGIN
               || this.status == MultiSession.Status.CONFIGURING
               || this.status == MultiSession.Status.JOINED
               || this.status == MultiSession.Status.READY;
            if (needsChannel && !this.connected) {
               return MultiSession.DisplayState.RED;
            } else {
               return this.status == MultiSession.Status.READY && this.ready && !this.stalled(now)
                  ? MultiSession.DisplayState.GREEN
                  : MultiSession.DisplayState.YELLOW;
            }
         } else {
            return MultiSession.DisplayState.RED;
         }
      }

      public boolean stalled(long now) {
         return this.lastInboundAt > 0L && now - this.lastInboundAt >= 20000L;
      }

      public String displayWord(long now) {
         return switch (this.displayState(now)) {
            case GREEN -> "Ready";
            case YELLOW -> this.status == MultiSession.Status.READY ? (this.ready ? "Stalled" : "Syncing") : "Connecting";
            case RED -> "Down";
         };
      }
   }

   private record SndRec(long seq, String id, double x, double y, double z) {
   }

   public static enum Status {
      QUEUED,
      AUTHENTICATING,
      CONNECTING,
      LOGIN,
      CONFIGURING,
      JOINED,
      READY,
      DISCONNECTED,
      FAILED;
   }

   record Suggest(int id, int start, int length, List<String> entries) {
   }

   private record TrackedScore(int score, String display, Optional<NumberFormat> numberFormat) {
   }

   public record TradeView(ItemStack costA, ItemStack costB, ItemStack result, boolean outOfStock, int uses, int maxUses, int xp, int specialPriceDiff) {
   }

   public record ViewSlot(int x, int y, int handler, ItemStack item) {
   }

   private static final class XCarryJob {
      final XCarryAction action;
      final ArrayDeque<Integer> collectSlots = new ArrayDeque<>();
      final ArrayDeque<MultiXCarryPlanner.Click> clicks = new ArrayDeque<>();
      final boolean hadContainer;
      final BlockPos reopenBlock;
      final int reopenEntity;
      MultiSession.XCarryPhase phase;
      long nextAt;

      XCarryJob(XCarryAction action, boolean hadContainer, BlockPos reopenBlock, int reopenEntity) {
         this.action = action;
         this.hadContainer = hadContainer;
         this.reopenBlock = reopenBlock == null ? null : reopenBlock.immutable();
         this.reopenEntity = reopenEntity;
         this.phase = hadContainer ? MultiSession.XCarryPhase.COLLECT : MultiSession.XCarryPhase.WAIT_INVENTORY;
      }
   }

   private static enum XCarryPhase {
      COLLECT,
      CLOSE_CONTAINER,
      WAIT_INVENTORY,
      EXECUTE,
      REOPEN,
      DONE;
   }
}
