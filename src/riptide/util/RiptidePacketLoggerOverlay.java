package riptide.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.level.block.state.BlockState;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Chip;
import riptide.gui.vanillaui.components.CompactControlGlyphs;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.macro.PayloadAction;
import riptide.util.mm.MatchmakingManager;
import riptide.util.mm.msg.MmMessages;

public class RiptidePacketLoggerOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
   private static final int HEADER_H = 16;
   private static final int GROUP_THRESHOLD = 10;
   private static final int DEFAULT_PANEL_WIDTH = 360;
   private static final int DEFAULT_PANEL_HEIGHT = 250;
   private static final int CAP_ALL = 800;
   private static final int CAP_INVENTORY = 400;
   private static final int CAP_MOVEMENT = 200;
   private static final int CAP_PAYLOAD = 400;
   private static final String OVERLAY_ID = "RiptidePacketLoggerOverlay";
   private static final long UI_FLUSH_INTERVAL_MS = 500L;
   private static final int BLOCKED_DEFAULTS_VERSION = 2;
   private static final int METADATA_TIME_COLOR = -4809052;
   private static final int METADATA_TICK_COLOR = -6114880;
   private static final Set<String> DEFAULT_BLOCKED_NAMES_V2_ADDITIONS = createIgnoredPacketKeyAdditionsV2();
   private static final Set<String> DEFAULT_BLOCKED_NAMES = createIgnoredPacketKeys();
   private static final Set<String> INVENTORY_NAMES = new HashSet<>(
      Arrays.asList(
         "ClickSlot",
         "CloseAbstractContainerScreen",
         "OpenScreen",
         "AbstractContainerMenu",
         "CreativeInventoryAction",
         "PickFromInventory",
         "PlayerAction",
         "InventoryS2C",
         "AbstractContainerMenuSlotUpdate",
         "AbstractContainerMenuProperty",
         "SetTradeOffers",
         "OpenHorseScreen",
         "CraftRequest",
         "ButtonClick",
         "RecipeBookData",
         "UpdateSelectedSlot",
         "HandSwing",
         "PlayerInteractBlock",
         "PlayerInteractItem",
         "PlayerInteractEntity",
         "ItemPickupAnimation",
         "ContainerClick",
         "ContainerClose",
         "ContainerSetContent",
         "ContainerSetData",
         "ContainerSetSlot",
         "ContainerButtonClick",
         "SetCreativeModeSlot",
         "SetCarriedItem",
         "Swing",
         "UseItem",
         "UseItemOn",
         "Interact",
         "SetCursorItem",
         "SetPlayerInventory",
         "TakeItemEntity",
         "MerchantOffers"
      )
   );
   private static final Set<String> MOVEMENT_NAMES = new HashSet<>(
      Arrays.asList(
         "PlayerMove",
         "PlayerMoveFull",
         "PlayerMovePositionAndOnGround",
         "PlayerMoveLookAndOnGround",
         "PlayerMoveOnGroundOnly",
         "EntityPosition",
         "EntityPositionSync",
         "EntitySetHead",
         "EntityVelocityUpdate",
         "VehicleMove",
         "MoveRelative",
         "PacketMoveRelative",
         "RotateRelative",
         "PacketRotateRelative",
         "EntityPacketRotate",
         "EntityMoveRelative",
         "EntityRotate",
         "TeleportConfirm",
         "ClientTickEnd",
         "ServerboundMovePlayer",
         "ServerboundMoveVehicle",
         "ServerboundPlayerInput",
         "ServerboundAcceptTeleportation",
         "ClientboundMoveEntity",
         "ClientboundMoveVehicle",
         "ClientboundMoveMinecart",
         "ClientboundPlayerPosition",
         "ClientboundPlayerRotation",
         "ClientboundEntityPositionSync",
         "ClientboundSetEntityMotion",
         "ClientboundRotateHead",
         "ClientboundTeleportEntity"
      )
   );
   private static final Set<String> PAYLOAD_NAMES = new HashSet<>(
      Arrays.asList(
         "CustomPacketPayload",
         "CustomPacketPayloadC2S",
         "CustomPacketPayloadS2C",
         "BrandPayload",
         "PluginMessage",
         "CustomPayload",
         "S2CPayload",
         "DiscardedPayload"
      )
   );
   private final Font textRenderer;
   private final RiptidePacketInspectOverlay inspectOverlay;
   private final RiptidePacketLoggerOverlay.BlockedPacketListOverlay blockedListOverlay;
   private final RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay payloadListenerOverlay;
   private final RiptidePayloadChannelListeners payloadListeners;
   private final RiptidePayloadChannelRegistrations payloadRegistrations;
   private final CompactTheme theme = new CompactTheme();
   private final RiptideContextMenu<RiptidePacketLoggerOverlay.LogEntry> ctxMenu;
   private int PANEL_WIDTH = 360;
   private int PANEL_HEIGHT = 250;
   private int currentPanelHeight = 250;
   private volatile boolean paused = true;
   private boolean configurationOnly = false;
   private boolean isDragging;
   private double dragOffX;
   private double dragOffY;
   private boolean headerDragMoved;
   private int scrollOffset;
   private final ScrollState contentScrollState = new ScrollState();
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private String searchFilter = "";
   private final RiptideChatField searchField;
   private RiptidePacketLoggerOverlay.Category activeTab = RiptidePacketLoggerOverlay.Category.ALL;
   private boolean groupingEnabled = true;
   private int dirFilter = 0;
   private boolean payloadFilteredOnly;
   private final Set<String> blockedNames = new LinkedHashSet<>();
   private final Set<String> blockedNormalized = new HashSet<>();
   private final Map<Class<?>, Boolean> blockedClassCache = new HashMap<>();
   private boolean blockedExpanded;
   private final Deque<RiptidePacketLoggerOverlay.LogEntry> bufAll = new ArrayDeque<>();
   private final Deque<RiptidePacketLoggerOverlay.LogEntry> bufInventory = new ArrayDeque<>();
   private final Deque<RiptidePacketLoggerOverlay.LogEntry> bufMovement = new ArrayDeque<>();
   private final Deque<RiptidePacketLoggerOverlay.LogEntry> bufPayload = new ArrayDeque<>();
   private final List<RiptidePacketLoggerOverlay.LogEntry> pendingEntries = new ArrayList<>();
   private final RiptidePacketContextTracker packetContextTracker = new RiptidePacketContextTracker();
   private volatile int gameTick;
   private long lastUiFlushMs;
   private List<RiptidePacketLoggerOverlay.DisplayRow> displayRows = new ArrayList<>();
   private boolean dirty = true;
   private final Set<String> expandedGroups = new HashSet<>();
   private static long idCounter = 0L;

   public RiptidePacketLoggerOverlay(Font textRenderer) {
      super("RiptidePacketLoggerOverlay", 360, 250);
      this.textRenderer = textRenderer;
      this.PANEL_WIDTH = this.defaultPanelWidth();
      this.PANEL_HEIGHT = this.defaultPanelHeight();
      this.currentPanelHeight = this.PANEL_HEIGHT;
      this.panelX = 200;
      this.panelY = 40;
      this.searchField = new RiptideChatField(MC, textRenderer, 0, 0, this.searchFieldWidth(), this.filterRowHeight(), false);
      this.searchField.setPlaceholder(Component.literal("Search..."));
      this.searchField.setMaxLength(160);
      this.searchField.setChangedListener(value -> {
         this.searchFilter = value == null ? "" : value;
         this.dirty = true;
      });
      this.inspectOverlay = new RiptidePacketInspectOverlay(textRenderer);
      this.blockedListOverlay = new RiptidePacketLoggerOverlay.BlockedPacketListOverlay();
      this.payloadListeners = new RiptidePayloadChannelListeners();
      this.payloadRegistrations = new RiptidePayloadChannelRegistrations();
      this.payloadListenerOverlay = new RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay();
      this.ctxMenu = new RiptideContextMenu<>(this.theme, textRenderer, this::getCtxItems, this.lineHeight());
      this.setContextMenu(this.ctxMenu);
      RiptideOverlayManager.get().register(this.inspectOverlay);
      RiptideOverlayManager.get().register(this.blockedListOverlay);
      RiptideOverlayManager.get().register(this.payloadListenerOverlay);
      this.loadBlockedFromConfig();
      this.paused = !RiptideConfig.getGlobal().packetLoggerCapturing;
   }

   public void saveState() {
      this.saveLayout();
   }

   public synchronized String censusSummary() {
      return "capturing="
         + !this.paused
         + " all="
         + this.bufAll.size()
         + " inv="
         + this.bufInventory.size()
         + " move="
         + this.bufMovement.size()
         + " payload="
         + this.bufPayload.size()
         + " pending="
         + this.pendingEntries.size();
   }

   public void restoreState() {
      this.restoreLayout();
      this.payloadListeners.load();
      this.payloadRegistrations.load();
      this.dirty = true;
   }

   public static boolean shouldRestoreSavedVisible() {
      RiptideWindowLayout layout = RiptideSharedState.get().getWindowLayout("RiptidePacketLoggerOverlay");
      return layout != null && layout.visible;
   }

   @Override
   public int getMinWidth() {
      return this.defaultPanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.defaultPanelHeight();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout clamped = this.clampToScreen(this, bounds);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.PANEL_WIDTH = clamped.width;
         this.PANEL_HEIGHT = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
      }
   }

   public void setGameTick(int t) {
      this.gameTick = t;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      boolean leavingOfflineSetup = this.configurationOnly && !configurationOnly;
      this.configurationOnly = configurationOnly;
      if (configurationOnly) {
         this.ctxMenu.close();
      }

      if (leavingOfflineSetup) {
         this.inspectOverlay.close();
         this.blockedListOverlay.setVisible(false);
         this.payloadListenerOverlay.setVisible(false);
      }
   }

   public synchronized void setPaused(boolean paused) {
      if (this.paused != paused) {
         if (paused) {
            this.flushPendingLocked();
         } else {
            this.lastUiFlushMs = System.currentTimeMillis();
         }

         this.paused = paused;
         this.dirty = true;
         RiptideNetworkCaptureState.refreshCurrent();
         RiptideConfig config = RiptideConfig.getGlobal();
         if (config.packetLoggerCapturing != !paused) {
            config.packetLoggerCapturing = !paused;
            config.save();
         }
      }
   }

   public void logPacket(Packet<?> packet, String direction) {
      this.logPacket(packet, direction, false, false);
   }

   public void logPayloadPacketSilently(Packet<?> packet, String direction) {
      this.logPacket(packet, direction, true, true);
   }

   public void logPayloadSnapshotSilently(
      long timestampMs, int tick, String direction, Class<?> packetClass, RiptidePayloadSupport.PayloadSnapshot payloadSnapshot
   ) {
      if (payloadSnapshot != null) {
         synchronized (this) {
            String name = packetClass != null && Packet.class.isAssignableFrom(packetClass) ? friendlyNameForClass(packetClass) : "Custom Payload";
            boolean isInventory = matchesAny(name, INVENTORY_NAMES);
            boolean isMovement = matchesAny(name, MOVEMENT_NAMES);
            RiptidePacketLoggerOverlay.LogEntry e = new RiptidePacketLoggerOverlay.LogEntry(
               timestampMs,
               tick,
               direction,
               name,
               packetClass,
               null,
               isInventory,
               isMovement,
               true,
               null,
               null,
               payloadSnapshot,
               RiptidePacketContextTracker.EMPTY_CAPTURE
            );
            this.pendingEntries.add(e);
            this.maybeFlushPendingLocked(true);
         }
      }
   }

   private static String friendlyNameForClass(Class<?> packetClass) {
      try {
         return RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)packetClass);
      } catch (Throwable var2) {
         return packetClass == null ? "Custom Payload" : packetClass.getSimpleName();
      }
   }

   private void logPacket(Packet<?> packet, String direction, boolean ignorePaused, boolean payloadOnly) {
      if (packet != null) {
         if (ignorePaused || !this.paused) {
            synchronized (this) {
               if (ignorePaused || !this.paused) {
                  Class<?> cls = packet.getClass();
                  String name = RiptidePacketNamer.getFriendlyName(packet, direction);
                  boolean isInventory = matchesAny(name, INVENTORY_NAMES);
                  boolean isMovement = matchesAny(name, MOVEMENT_NAMES);
                  boolean isPayload = isPayloadPacket(cls, name);
                  if (payloadOnly && isPayload || !this.isBlockedName(cls, name)) {
                     if (!payloadOnly || isPayload) {
                        RiptidePacketContextTracker.Capture packetContext = this.packetContextTracker.capture(packet, direction);
                        RiptidePacketLoggerOverlay.LogCaptureContext captureContext = captureLogContext(packet);
                        RiptidePayloadSupport.PayloadSnapshot payloadSnapshot = isPayload ? RiptidePayloadSupport.snapshot(packet, direction) : null;
                        if (payloadSnapshot != null) {
                           RiptidePayloadChannelListeners.Match match = this.payloadListeners.match(payloadSnapshot, direction);
                           if (match != null) {
                              RiptidePayloadFilterNotifier.onMatch(payloadSnapshot.channel(), direction, match);
                           }
                        }

                        RiptidePacketLoggerOverlay.LogEntry e = new RiptidePacketLoggerOverlay.LogEntry(
                           System.currentTimeMillis(),
                           this.gameTick,
                           direction,
                           name,
                           cls,
                           packet,
                           isInventory,
                           isMovement,
                           isPayload,
                           captureContext.blockStateSummary(),
                           captureContext.screenSummary(),
                           payloadSnapshot,
                           packetContext
                        );
                        this.pendingEntries.add(e);
                        this.maybeFlushPendingLocked(false);
                     }
                  }
               }
            }
         }
      }
   }

   private static RiptidePacketLoggerOverlay.LogCaptureContext captureLogContext(Packet<?> packet) {
      if (packet != null && MC != null && MC.isSameThread()) {
         try {
            if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
               return new RiptidePacketLoggerOverlay.LogCaptureContext(snapshotBlockState(actionPacket.getPos()), null);
            }

            if (packet instanceof ServerboundUseItemOnPacket interactBlockPacket) {
               return new RiptidePacketLoggerOverlay.LogCaptureContext(snapshotBlockState(interactBlockPacket.getHitResult().getBlockPos()), null);
            }

            if (packet instanceof ServerboundContainerClosePacket) {
               return new RiptidePacketLoggerOverlay.LogCaptureContext(null, snapshotCurrentScreen());
            }
         } catch (Throwable var2) {
         }

         return RiptidePacketLoggerOverlay.LogCaptureContext.EMPTY;
      } else {
         return RiptidePacketLoggerOverlay.LogCaptureContext.EMPTY;
      }
   }

   private static String snapshotBlockState(BlockPos pos) {
      if (pos != null && MC.level != null) {
         try {
            BlockState state = MC.level.getBlockState(pos);
            return state == null ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state;
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String snapshotCurrentScreen() {
      if (MC.gui.screen() == null) {
         return null;
      } else {
         try {
            String title = MC.gui.screen().getTitle() == null ? "" : MC.gui.screen().getTitle().getString().trim();
            String className = MC.gui.screen().getClass().getSimpleName();
            if (title.isEmpty()) {
               return className;
            } else {
               return className != null && !className.isBlank() ? title + " [" + className + "]" : title;
            }
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   public boolean isPacketBlocked(Class<?> cls) {
      return this.isBlockedClass(cls);
   }

   private static boolean matchesAny(String name, Set<String> set) {
      for (String s : set) {
         if (name.contains(s)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isPayloadPacket(Class<?> cls, String name) {
      if (matchesAny(name, PAYLOAD_NAMES)) {
         return true;
      } else if (cls == null) {
         return false;
      } else {
         String simpleName = cls.getSimpleName();
         if (matchesAny(simpleName, PAYLOAD_NAMES)) {
            return true;
         } else {
            String className = cls.getName();
            return matchesAny(className, PAYLOAD_NAMES);
         }
      }
   }

   private static Set<String> createIgnoredPacketKeys() {
      Set<String> keys = new LinkedHashSet<>();
      registerIgnoredPackets(
         keys,
         "ServerboundMovePlayerPacket.Pos",
         "ServerboundMovePlayerPacket.PosRot",
         "ServerboundMovePlayerPacket.Rot",
         "ServerboundMovePlayerPacket.StatusOnly",
         "ServerboundClientTickEndPacket",
         "ServerboundPlayerInputPacket",
         "ServerboundAcceptTeleportationPacket",
         "ServerboundChunkBatchReceivedPacket",
         "ServerboundPlayerCommandPacket",
         "ServerboundKeepAlivePacket",
         "ServerboundPongPacket",
         "ClientboundBundlePacket",
         "ClientboundBundleDelimiterPacket",
         "ClientboundKeepAlivePacket",
         "ClientboundPingPacket",
         "ClientboundMoveEntityPacket.Pos",
         "ClientboundMoveEntityPacket.PosRot",
         "ClientboundMoveEntityPacket.Rot",
         "ClientboundEntityPositionSyncPacket",
         "ClientboundSetEntityMotionPacket",
         "ClientboundRotateHeadPacket",
         "ClientboundTeleportEntityPacket",
         "ClientboundMoveMinecartPacket",
         "ClientboundSetEntityDataPacket",
         "ClientboundUpdateAttributesPacket",
         "ClientboundEntityEventPacket",
         "ClientboundLightUpdatePacket",
         "ClientboundLevelChunkWithLightPacket",
         "ClientboundForgetLevelChunkPacket",
         "ClientboundChunkBatchStartPacket",
         "ClientboundChunkBatchFinishedPacket",
         "ClientboundSetChunkCacheCenterPacket",
         "ClientboundChunksBiomesPacket",
         "ClientboundSectionBlocksUpdatePacket",
         "ClientboundBlockUpdatePacket",
         "ClientboundBlockEntityDataPacket",
         "ClientboundSetTimePacket",
         "ClientboundLevelParticlesPacket",
         "ClientboundLevelEventPacket",
         "ClientboundSoundPacket",
         "ClientboundSoundEntityPacket",
         "ClientboundPlayerInfoUpdatePacket",
         "ClientboundSetObjectivePacket",
         "ClientboundTickingStatePacket"
      );
      keys.addAll(DEFAULT_BLOCKED_NAMES_V2_ADDITIONS);
      return Collections.unmodifiableSet(keys);
   }

   private static Set<String> createIgnoredPacketKeyAdditionsV2() {
      Set<String> keys = new LinkedHashSet<>();
      registerIgnoredPackets(
         keys,
         "ClientboundSetScorePacket",
         "ClientboundSetEquipmentPacket",
         "ClientboundAnimatePacket",
         "ClientboundTabListPacket",
         "ClientboundBlockDestructionPacket",
         "ClientboundPlayerPositionPacket",
         "ClientboundExplodePacket",
         "ClientboundRemoveEntitiesPacket",
         "ClientboundBlockEventPacket",
         "ClientboundSetPlayerTeamPacket",
         "ClientboundSystemChatPacket"
      );
      return Collections.unmodifiableSet(keys);
   }

   private static void registerIgnoredPackets(Set<String> keys, String... names) {
      for (String name : names) {
         registerIgnoredPacket(keys, name);
      }
   }

   private static void registerIgnoredPacket(Set<String> keys, String name) {
      if (name != null && !name.isBlank()) {
         keys.add(name.trim());
      }
   }

   private static String normalizePacketKey(String value) {
      if (value != null && !value.isEmpty()) {
         String lower = value.toLowerCase(Locale.ROOT);
         StringBuilder normalized = new StringBuilder(lower.length());

         for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
               normalized.append(c);
            }
         }

         return normalized.toString();
      } else {
         return "";
      }
   }

   private void recomputeBlockedNormalized() {
      this.blockedNormalized.clear();
      this.blockedClassCache.clear();

      for (String name : this.blockedNames) {
         if (name != null && !name.isBlank()) {
            this.blockedNormalized.add(normalizePacketKey(name));
            if (name.endsWith("Packet")) {
               this.blockedNormalized.add(normalizePacketKey(name.substring(0, name.length() - 6)));
            }
         }
      }
   }

   private boolean isBlockedName(Class<?> cls, String friendlyName) {
      if (this.blockedNormalized.isEmpty()) {
         return false;
      } else {
         return this.blockedNormalized.contains(normalizePacketKey(friendlyName)) ? true : this.isBlockedClass(cls);
      }
   }

   private boolean isBlockedClass(Class<?> cls) {
      if (cls != null && !this.blockedNormalized.isEmpty()) {
         Boolean cached = this.blockedClassCache.get(cls);
         if (cached != null) {
            return cached;
         } else {
            boolean blocked = this.computeBlockedClass(cls);
            this.blockedClassCache.put(cls, blocked);
            return blocked;
         }
      } else {
         return false;
      }
   }

   private boolean computeBlockedClass(Class<?> cls) {
      if (this.blockedNormalized.contains(normalizePacketKey(cls.getSimpleName()))) {
         return true;
      } else {
         return this.blockedNormalized.contains(normalizePacketKey(cls.getName()))
            ? true
            : Packet.class.isAssignableFrom(cls)
               && this.blockedNormalized.contains(normalizePacketKey(RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)cls)));
      }
   }

   private void loadBlockedFromConfig() {
      RiptideConfig config = RiptideConfig.getGlobal();
      this.blockedNames.clear();
      if (config.packetLoggerBlockedInit) {
         if (config.packetLoggerBlocked != null) {
            this.blockedNames.addAll(config.packetLoggerBlocked);
         }

         if (config.packetLoggerBlockedDefaultsVersion < 2) {
            this.blockedNames.addAll(DEFAULT_BLOCKED_NAMES_V2_ADDITIONS);
            config.packetLoggerBlocked = new ArrayList<>(this.blockedNames);
            config.packetLoggerBlockedDefaultsVersion = 2;
            config.save();
         }
      } else {
         this.blockedNames.addAll(DEFAULT_BLOCKED_NAMES);
         config.packetLoggerBlocked = new ArrayList<>(this.blockedNames);
         config.packetLoggerBlockedInit = true;
         config.packetLoggerBlockedDefaultsVersion = 2;
         config.save();
      }

      this.recomputeBlockedNormalized();
   }

   private void saveBlocked() {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.packetLoggerBlocked = new ArrayList<>(this.blockedNames);
      config.packetLoggerBlockedInit = true;
      config.packetLoggerBlockedDefaultsVersion = 2;
      config.save();
   }

   public synchronized void blockPacketName(String name) {
      if (name != null && !name.isBlank()) {
         if (this.blockedNames.add(name.trim())) {
            this.recomputeBlockedNormalized();
            this.saveBlocked();
         }

         this.purgeBlockedEntries();
      }
   }

   private synchronized void unblockName(String name) {
      if (this.blockedNames.remove(name)) {
         this.recomputeBlockedNormalized();
         this.saveBlocked();
      }
   }

   private synchronized void unblockAllMatching(Class<?> cls, String friendlyName) {
      Set<String> targets = new HashSet<>();
      if (friendlyName != null && !friendlyName.isBlank()) {
         targets.add(normalizePacketKey(friendlyName));
      }

      if (cls != null) {
         targets.add(normalizePacketKey(cls.getSimpleName()));
         targets.add(normalizePacketKey(cls.getName()));
      }

      for (String t : new ArrayList<>(targets)) {
         if (t.endsWith("packet")) {
            targets.add(t.substring(0, t.length() - 6));
         }
      }

      targets.remove("");
      if (!targets.isEmpty()) {
         boolean changed = this.blockedNames
            .removeIf(
               raw -> {
                  if (raw == null) {
                     return false;
                  } else {
                     return targets.contains(normalizePacketKey(raw))
                        ? true
                        : raw.endsWith("Packet") && targets.contains(normalizePacketKey(raw.substring(0, raw.length() - 6)));
                  }
               }
            );
         if (changed) {
            this.recomputeBlockedNormalized();
            this.saveBlocked();
         }
      }
   }

   private synchronized void resetBlockedToDefault() {
      this.blockedNames.clear();
      this.blockedNames.addAll(DEFAULT_BLOCKED_NAMES);
      this.recomputeBlockedNormalized();
      this.saveBlocked();
      this.purgeBlockedEntries();
   }

   private synchronized void clearBlocked() {
      this.blockedNames.clear();
      this.recomputeBlockedNormalized();
      this.saveBlocked();
   }

   private synchronized void purgeBlockedEntries() {
      this.bufAll.removeIf(e -> this.isBlockedName(e.packetClass, e.shortName));
      this.bufInventory.removeIf(e -> this.isBlockedName(e.packetClass, e.shortName));
      this.bufMovement.removeIf(e -> this.isBlockedName(e.packetClass, e.shortName));
      this.bufPayload.removeIf(e -> this.isBlockedName(e.packetClass, e.shortName));
      this.dirty = true;
   }

   public static boolean isPayloadPacket(Packet<?> packet) {
      if (packet == null) {
         return false;
      } else if (RiptidePayloadSupport.extractPayload(packet) != null) {
         return true;
      } else {
         Class<?> cls = packet.getClass();
         return isPayloadPacket(cls, RiptidePacketNamer.getFriendlyName(packet, ""));
      }
   }

   private static void addCapped(Deque<RiptidePacketLoggerOverlay.LogEntry> buf, RiptidePacketLoggerOverlay.LogEntry e, int cap) {
      buf.addLast(e);

      while (buf.size() > cap) {
         buf.removeFirst();
      }
   }

   private void maybeFlushPending() {
      synchronized (this) {
         this.maybeFlushPendingLocked(false);
      }
   }

   private void maybeFlushPendingLocked(boolean force) {
      long now = System.currentTimeMillis();
      if (!force) {
         if (this.pendingEntries.isEmpty()) {
            return;
         }

         if (this.lastUiFlushMs != 0L && now - this.lastUiFlushMs < 500L) {
            return;
         }
      }

      this.flushPendingLocked();
      this.lastUiFlushMs = now;
   }

   private void flushPending() {
      synchronized (this) {
         this.flushPendingLocked();
         this.lastUiFlushMs = System.currentTimeMillis();
      }
   }

   private void flushPendingLocked() {
      if (!this.pendingEntries.isEmpty()) {
         for (RiptidePacketLoggerOverlay.LogEntry e : this.pendingEntries) {
            addCapped(this.bufAll, e, 800);
            if (e.isInventory) {
               addCapped(this.bufInventory, e, 400);
            }

            if (e.isMovement) {
               addCapped(this.bufMovement, e, 200);
            }

            if (e.isPayload) {
               addCapped(this.bufPayload, e, 400);
            }
         }

         this.pendingEntries.clear();
         this.dirty = true;
      }
   }

   @Override
   public void setVisible(boolean v) {
      this.visible = v;
      if (v) {
         this.scrollOffset = 0;
         this.contentScrollState.jumpTo(0, 0);
         this.dirty = true;
         this.ctxMenu.close();
         RiptideOverlayManager.get().bringToFront(this);
      } else {
         this.inspectOverlay.close();
      }

      this.saveLayout();
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean c) {
      if (this.collapsed != c) {
         this.collapsed = c;
         this.isDragging = false;
         this.headerDragMoved = false;
         this.scrollbarDragging = false;
         if (c) {
            this.clearHiddenInteractionState();
            this.ctxMenu.close();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.searchField != null && this.searchField.isFocused();
   }

   @Override
   public void clearTextFieldFocus() {
      if (this.searchField != null) {
         this.searchField.setFocused(false);
      }
   }

   @Override
   public int getZLevel() {
      return 10;
   }

   private void drawUiText(GuiGraphicsExtractor context, String text, UiTone tone, int color, int x, int y) {
      UiText.draw(context, this.textRenderer, text, this.theme.fontFor(tone), color, x, y, false);
   }

   @Override
   public boolean isMouseOver(double mx, double my) {
      if (!this.visible) {
         return false;
      } else {
         int panelHeight = this.collapsed ? 16 : this.currentPanelHeight;
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, panelHeight, this.visible, this.collapsed);
         int h = this.getRenderedFrameHeight(bounds, this.collapsed);
         boolean overPanel = mx >= this.panelX && mx <= this.panelX + this.PANEL_WIDTH && my >= this.panelY && my <= this.panelY + h;
         return overPanel | this.ctxMenu.isMouseOver(mx, my);
      }
   }

   @Override
   public boolean isOverDragBar(double mx, double my) {
      return !this.visible
         ? false
         : mx >= this.panelX
            && mx <= this.panelX + this.PANEL_WIDTH
            && my >= this.panelY
            && my <= this.panelY + 16
            && !this.isOverWindowControl(mx, my, this.getBounds());
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         this.maybeFlushPending();
         if (this.dirty) {
            this.rebuildDisplay();
            this.dirty = false;
         }

         RiptideWindowLayout clamped = this.clampToScreen(
            this, new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.calcPanelH(), this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.PANEL_WIDTH = clamped.width;
         this.currentPanelHeight = clamped.height;
         int ph = this.currentPanelHeight;
         int total = 0;

         for (RiptidePacketLoggerOverlay.DisplayRow r : this.displayRows) {
            total += r.type == RiptidePacketLoggerOverlay.RowType.GROUP ? r.groupCount : 1;
         }

         int bodyMx = mx;
         int bodyMy = my;
         if (this.ctxMenu.isMouseOver(mx, my)) {
            bodyMx = -10000;
            bodyMy = -10000;
         }

         String title = "Packet Logger";
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, ph, this.visible, this.collapsed);
         this.renderWindowFrame(ctx, bodyMx, bodyMy, bounds, title, this.collapsed, this.isDragging);
         boolean clipBody = this.beginWindowBodyClip(ctx, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, this.isDragging);
         } else {
            try {
               int tabY = this.panelY + 16 + 2;
               this.renderTabs(ctx, bodyMx, bodyMy, tabY, total);
               int filterY = tabY + this.tabHeight() + 2;
               this.renderFilterBar(ctx, bodyMx, bodyMy, filterY);
               int contentY = filterY + this.filterHeight() + 2;
               int contentEndY = contentY + this.contentAreaHeight();
               if (this.displayRows.isEmpty()) {
                  this.drawUiText(ctx, "No packets matching filters", UiTone.MUTED, RiptideColors.textDim(), this.panelX + 10, contentY + 6);
               } else {
                  int contentHeight = this.displayRows.size() * this.lineHeight();
                  int viewHeight = this.contentAreaHeight();
                  int maxScroll = Math.max(0, contentHeight - viewHeight);
                  this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset, this.lineHeight(), maxScroll);
                  this.contentScrollState.setTarget(this.scrollOffset, maxScroll);
                  int drawScroll = this.contentScrollState.tick(delta, maxScroll);
                  UiScissorStack.global().push(ctx, UiBounds.of(this.panelX, contentY, this.PANEL_WIDTH, contentEndY - contentY));
                  int drawBase = contentY - drawScroll;

                  for (int i = 0; i < this.displayRows.size(); i++) {
                     int ey = drawBase + i * this.lineHeight();
                     if (ey + this.lineHeight() > contentY && ey < contentEndY) {
                        RiptidePacketLoggerOverlay.DisplayRow row = this.displayRows.get(i);
                        if (row.type == RiptidePacketLoggerOverlay.RowType.GROUP) {
                           this.renderGroup(ctx, row, ey, bodyMx, bodyMy);
                        } else {
                           this.renderEntry(ctx, row.entry, ey, bodyMx, bodyMy);
                        }
                     }
                  }

                  UiScissorStack.global().pop(ctx);
                  CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics();
                  CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(bodyMx, bodyMy), this.scrollbarDragging);
               }
            } finally {
               this.endWindowBodyClip(ctx, clipBody);
               this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, this.isDragging);
            }

            if (this.ctxMenu.isOpen()) {
               ctx.nextStratum();
               this.ctxMenu.render(ctx, mx, my);
            }
         }
      }
   }

   private void renderTabs(GuiGraphicsExtractor ctx, int mx, int my, int y, int total) {
      int x = this.panelX + 4;

      for (RiptidePacketLoggerOverlay.Category cat : RiptidePacketLoggerOverlay.Category.values()) {
         int w = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, cat.label, 5, 32, 64);
         CompactOverlayControls.tab(ctx, this.textRenderer, x, y, w, this.tabHeight(), cat.label, this.activeTab == cat, mx, my);
         x += w + 2;
      }

      String summary = this.paused ? "Paused  " + total : Integer.toString(total);
      if (this.activeTab == RiptidePacketLoggerOverlay.Category.PAYLOAD) {
         RiptidePayloadChannelSubscriptionManager.Status subscriptionStatus = RiptidePayloadChannelSubscriptionManager.status();
         String channelStatus = subscriptionStatus == null ? "" : subscriptionStatus.shortLabel();
         if (!channelStatus.isBlank()) {
            summary = summary + "  " + channelStatus;
         }
      }

      int summaryColor = this.paused ? this.theme.color(UiTone.MUTED) : RiptideColors.textSecondary();
      int summaryWidth = UiText.width(this.textRenderer, summary, this.theme.fontFor(UiTone.MUTED), summaryColor);
      int summaryX = this.panelX + this.PANEL_WIDTH - 6 - summaryWidth;
      int minSummaryX = x + 4;
      if (summaryX >= minSummaryX) {
         int summaryY = UiSizing.alignTextY(y, this.tabHeight(), this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
         this.drawUiText(ctx, summary, UiTone.MUTED, summaryColor, summaryX, summaryY);
      }
   }

   private int contentAreaY() {
      return this.panelY + 16 + 2 + this.tabHeight() + 2 + this.filterHeight() + 2;
   }

   private int contentAreaHeight() {
      int rawHeight = Math.max(0, this.currentPanelHeight - 16 - this.tabHeight() - this.filterHeight() - 8 - this.blockedH());
      return this.alignViewportHeight(rawHeight, this.lineHeight());
   }

   private CompactScrollbar.Metrics getContentScrollbarMetrics() {
      int contentHeight = this.displayRows.size() * this.lineHeight();
      int viewHeight = this.contentAreaHeight();
      int maxScroll = Math.max(0, contentHeight - viewHeight);
      return CompactScrollbar.compute(
         contentHeight, viewHeight, this.panelX + this.PANEL_WIDTH - 5, this.contentAreaY(), 3, viewHeight, this.contentScrollState.tick(0.0F, maxScroll)
      );
   }

   private void renderFilterBar(GuiGraphicsExtractor ctx, int mx, int my, int y) {
      int gap = 2;
      int row2Y = y + this.filterRowHeight() + this.filterRowGap();
      int x = this.panelX + 4;
      String captureLabel = this.configurationOnly ? (this.paused ? "Auto OFF" : "Auto ON") : (this.paused ? "Start" : "Stop");
      int captureW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, captureLabel, 5, 38, this.configurationOnly ? 66 : 54);
      if (this.paused) {
         this.drawOverlayToggleButton(ctx, x, y, captureW, this.filterRowHeight(), captureLabel, false, "packet-logger:capture", mx, my);
      } else {
         this.drawOverlayToggleButton(ctx, x, y, captureW, this.filterRowHeight(), captureLabel, true, "packet-logger:capture", mx, my);
      }

      x += captureW + gap;
      int clearW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Clear", 5, 34, 54);
      this.drawOverlayButton(ctx, x, y, clearW, this.filterRowHeight(), "Clear", CompactOverlayButton.Variant.GHOST, true, mx, my);
      x += clearW + gap;
      int copyW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Copy", 5, 34, 52);
      this.drawOverlayButton(ctx, x, y, copyW, this.filterRowHeight(), "Copy", CompactOverlayButton.Variant.GHOST, true, mx, my);
      x += copyW + gap;
      String grpLabel = this.groupingEnabled ? "Group" : "Ungrp";
      int groupW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, grpLabel, 5, 38, 58);
      this.drawOverlayToggleButton(ctx, x, y, groupW, this.filterRowHeight(), grpLabel, this.groupingEnabled, "packet-logger:grouping", mx, my);
      x += groupW + gap;
      String bt = "Blocked";
      int blockedW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, bt, 5, 54, 72);
      this.drawOverlayButton(ctx, x, y, blockedW, this.filterRowHeight(), bt, CompactOverlayButton.Variant.GHOST, true, mx, my);
      x += blockedW + gap;
      String channelsLabel = "Channels";
      int channelsW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, channelsLabel, 5, 58, 78);
      this.drawOverlayButton(ctx, x, y, channelsW, this.filterRowHeight(), channelsLabel, CompactOverlayButton.Variant.GHOST, true, mx, my);
      x += channelsW + gap;
      int payloadW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Payload", 5, 44, 64);
      this.drawOverlayButton(ctx, x, y, payloadW, this.filterRowHeight(), "Payload", CompactOverlayButton.Variant.GHOST, true, mx, my);
      x = this.panelX + 4;
      int sw = this.filterSearchFieldWidth();
      this.searchField.setX(x);
      this.searchField.setY(row2Y);
      this.searchField.setWidth(sw);
      this.searchField.setHeight(this.filterRowHeight());
      if (!Objects.equals(this.searchField.getText(), this.searchFilter)) {
         this.searchField.setText(this.searchFilter);
      }

      this.searchField.render(ctx, mx, my, 0.0F);
      x += sw + 3;
      String[] dirLabels = new String[]{"Both", "C2S", "S2C"};

      for (int d = 0; d < 3; d++) {
         int bw = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, dirLabels[d], 4, 30, 48);
         CompactOverlayControls.tab(ctx, this.textRenderer, x, row2Y, bw, this.filterRowHeight(), dirLabels[d], this.dirFilter == d, mx, my);
         x += bw + gap;
      }

      if (this.activeTab == RiptidePacketLoggerOverlay.Category.PAYLOAD) {
         String listenLabel = this.payloadFilteredOnly ? "Filtered" : "All";
         int listenW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, listenLabel, 4, 42, 62);
         CompactOverlayControls.tab(ctx, this.textRenderer, x, row2Y, listenW, this.filterRowHeight(), listenLabel, this.payloadFilteredOnly, mx, my);
      }
   }

   private void drawOverlayButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean active, int mx, int my
   ) {
      CompactOverlayControls.action(ctx, this.textRenderer, x, y, w, h, label, variant, active, mx, my);
   }

   private void drawOverlayToggleButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean enabled, String animationKey, int mx, int my
   ) {
      CompactOverlayControls.toggle(ctx, this.textRenderer, x, y, w, h, label, enabled, animationKey, mx, my);
   }

   private void renderGroup(GuiGraphicsExtractor ctx, RiptidePacketLoggerOverlay.DisplayRow row, int y, int mx, int my) {
      int x = this.panelX + 4;
      boolean exp = this.expandedGroups.contains(row.groupKey);
      boolean hov = mx >= this.panelX && mx <= this.panelX + this.PANEL_WIDTH && my >= y && my < y + this.lineHeight();
      RiptidePayloadChannelListeners.Match listenerMatch = row.payloadSnapshot == null ? null : this.payloadListeners.match(row.payloadSnapshot, row.direction);
      if (listenerMatch != null) {
         ctx.fill(this.panelX + 2, y, this.panelX + this.PANEL_WIDTH - 4, y + this.lineHeight(), RiptideColors.packetRowSelectedBg(hov));
         ctx.fill(this.panelX + 2, y, this.panelX + 4, y + this.lineHeight(), RiptideColors.packetRowSelectedAccent());
      } else if (hov) {
         CompactSurfaces.row(ctx, this.panelX + 2, y, this.PANEL_WIDTH - 4, this.lineHeight(), true, false);
      }

      CompactControlGlyphs.drawChevron(
         ctx,
         x,
         y + 2,
         8,
         exp ? CompactControlGlyphs.ChevronDirection.DOWN : CompactControlGlyphs.ChevronDirection.RIGHT,
         hov ? -659730 : -1582374,
         -1204153320,
         1.0F
      );
      x += 10;
      String arrow = row.direction.equals("C2S") ? ">" : "<";
      int color = row.direction.equals("C2S") ? -12268289 : -21948;
      this.drawUiText(ctx, arrow, UiTone.BODY, color, x, y + 1);
      x += 12;
      String displayName = row.groupKey.contains(":") ? row.groupKey.substring(row.groupKey.indexOf(58) + 1) : row.groupKey;
      String summary = row.payloadSnapshot == null ? "" : RiptidePayloadSupport.summarizeForLogger(row.payloadSnapshot, true);
      String listenText = listenerMatch == null ? "" : " [" + listenerMatch.label() + "]";
      String line = displayName + " (x" + row.groupCount + ")" + listenText + (summary.isBlank() ? "" : " " + summary);
      int maxW = this.PANEL_WIDTH - (x - this.panelX) - 32;
      if (UiText.width(this.textRenderer, line, this.theme.fontFor(UiTone.BODY), color) > maxW) {
         line = UiText.trimToWidth(this.textRenderer, line, Math.max(1, maxW), this.theme.fontFor(UiTone.BODY), color);
      }

      this.drawUiText(ctx, line, UiTone.BODY, color, x, y + 1);
      int bx = this.panelX + this.PANEL_WIDTH - 28;
      boolean hb = mx >= bx && mx <= bx + 24 && my >= y && my < y + this.lineHeight();
      this.drawUiText(ctx, "BLK", UiTone.MUTED, hb ? -48060 : RiptideColors.textDim(), bx, y + 1);
   }

   private void renderEntry(GuiGraphicsExtractor ctx, RiptidePacketLoggerOverlay.LogEntry e, int y, int mx, int my) {
      int x = this.panelX + 4;
      boolean hov = mx >= this.panelX && mx <= this.panelX + this.PANEL_WIDTH && my >= y && my < y + this.lineHeight();
      RiptidePayloadChannelListeners.Match listenerMatch = this.payloadListenerMatch(e);
      if (listenerMatch != null) {
         ctx.fill(this.panelX + 2, y, this.panelX + this.PANEL_WIDTH - 4, y + this.lineHeight(), RiptideColors.packetRowSelectedBg(hov));
         ctx.fill(this.panelX + 2, y, this.panelX + 4, y + this.lineHeight(), RiptideColors.packetRowSelectedAccent());
      } else if (hov) {
         CompactSurfaces.row(ctx, this.panelX + 2, y, this.PANEL_WIDTH - 4, this.lineHeight(), true, false);
      }

      int color = e.direction.equals("C2S") ? -12268289 : -21948;
      this.drawUiText(ctx, e.direction.equals("C2S") ? ">" : "<", UiTone.BODY, color, x, y + 1);
      x += 12;
      String time = TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs));
      this.drawUiText(ctx, time, UiTone.MUTED, -4809052, x, y + 1);
      x += UiText.width(this.textRenderer, time, this.theme.fontFor(UiTone.MUTED), -4809052) + 4;
      String tick = "T" + e.gameTick;
      this.drawUiText(ctx, tick, UiTone.MUTED, -6114880, x, y + 1);
      x += UiText.width(this.textRenderer, tick, this.theme.fontFor(UiTone.MUTED), -6114880) + 4;
      int maxW = this.PANEL_WIDTH - (x - this.panelX) - 30;
      String name = e.shortName;
      if (e.payloadSnapshot != null) {
         String summary = RiptidePayloadSupport.summarizeForLogger(e.payloadSnapshot, true);
         if (!summary.isBlank()) {
            name = name + " " + summary;
         }
      }

      if (listenerMatch != null) {
         name = name + " [" + listenerMatch.label() + "]";
      }

      if (UiText.width(this.textRenderer, name, this.theme.fontFor(UiTone.BODY), RiptideColors.textPrimary()) > maxW) {
         name = UiText.trimToWidth(this.textRenderer, name, Math.max(1, maxW), this.theme.fontFor(UiTone.BODY), RiptideColors.textPrimary());
      }

      this.drawUiText(ctx, name, UiTone.BODY, color, x, y + 1);
      int bx = this.panelX + this.PANEL_WIDTH - 16;
      boolean hb = mx >= bx && mx <= bx + 12 && my >= y && my < y + this.lineHeight();
      this.drawUiText(ctx, "=", UiTone.MUTED, hb ? -8875 : RiptideColors.textDim(), bx, y + 1);
   }

   private void renderBlocked(GuiGraphicsExtractor ctx, int mx, int my, int startY, int endY) {
      CompactSurfaces.divider(ctx, this.panelX + 4, startY, this.PANEL_WIDTH - 8);
      int y = startY + 3;
      boolean hh = mx >= this.panelX + 4 && mx <= this.panelX + 150 && my >= y && my < y + this.lineHeight();
      int blockedColor = hh ? RiptideTheme.recolor(-30584, RiptideTheme.Channel.DANGER) : RiptideTheme.recolor(-39322, RiptideTheme.Channel.DANGER);
      UiRenderer.chevron(ctx, UiBounds.of(this.panelX + 6, y + 1, 9, 9), this.blockedExpanded, blockedColor);
      this.drawUiText(ctx, "Blocked (" + this.blockedNames.size() + ")", UiTone.LABEL, blockedColor, this.panelX + 17, y + 1);
      int ubX = this.panelX + this.PANEL_WIDTH - 58;
      boolean hua = mx >= ubX && mx <= ubX + 54 && my >= y && my < y + this.lineHeight();
      this.drawUiText(ctx, "CLR ALL", UiTone.MUTED, hua ? -12255420 : RiptideColors.textSecondary(), ubX, y + 1);
      int rstX = ubX - 44;
      boolean hRst = mx >= rstX && mx <= rstX + 40 && my >= y && my < y + this.lineHeight();
      this.drawUiText(ctx, "RESET", UiTone.MUTED, hRst ? -10923 : RiptideColors.textSecondary(), rstX, y + 1);
      if (this.blockedExpanded) {
         y += this.lineHeight();

         for (String name : this.blockedNames) {
            if (y + this.lineHeight() > endY) {
               break;
            }

            this.drawUiText(
               ctx,
               "  " + UiText.trimToWidth(this.textRenderer, name, Math.max(1, this.PANEL_WIDTH - 50), this.theme.fontFor(UiTone.BODY), -5609882),
               UiTone.BODY,
               -5609882,
               this.panelX + 8,
               y + 1
            );
            int ux = this.panelX + this.PANEL_WIDTH - 28;
            boolean hu = mx >= ux && mx <= ux + 24 && my >= y && my < y + this.lineHeight();
            this.drawUiText(ctx, "UB", UiTone.MUTED, hu ? -12255420 : RiptideColors.textSecondary(), ux, y + 1);
            y += this.lineHeight();
         }
      }
   }

   private String[] getCtxItems(RiptidePacketLoggerOverlay.LogEntry e) {
      boolean isC2S = e != null && "C2S".equalsIgnoreCase(e.direction);
      boolean isPayload = e != null && e.isPayload;
      String[] base;
      if (this.configurationOnly) {
         if (isC2S && isPayload) {
            base = new String[]{"Block", "Queue", "Edit Payload", "+PAYLOAD", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
         } else {
            base = isC2S
               ? new String[]{"Block", "Queue", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"}
               : new String[]{"Block", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
         }
      } else if (isC2S && isPayload) {
         base = new String[]{"Block", "Queue", "Replay", "Send", "Edit Payload", "+PAYLOAD", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
      } else {
         base = isC2S
            ? new String[]{"Block", "Queue", "Replay", "Send", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"}
            : new String[]{"Block", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
      }

      if (e != null && e.packetRef != null && !RiptideLiteVariant.enabled()) {
         String[] withShare = Arrays.copyOf(base, base.length + 1);
         withShare[base.length] = "Share to Lobby";
         return withShare;
      } else {
         return base;
      }
   }

   private void openCtxMenu(RiptidePacketLoggerOverlay.LogEntry entry, int mouseX, int mouseY) {
      this.ctxMenu.open(mouseX, mouseY, entry);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (this.ctxMenu.handleClick(mouseX, mouseY, button, (entry, action, index) -> this.executeCtxAction(action, entry))) {
         return true;
      } else if (button != 0 && button != 1) {
         return false;
      } else if (mouseY >= this.panelY && mouseY <= this.panelY + 16 && mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH) {
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.calcPanelH(), this.visible, this.collapsed);
         if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
            this.setVisible(false);
            return true;
         } else {
            if (button == 0) {
               this.isDragging = true;
               this.headerDragMoved = false;
               this.dragOffX = mouseX - this.panelX;
               this.dragOffY = mouseY - this.panelY;
            }

            return true;
         }
      } else if (this.collapsed) {
         return false;
      } else {
         int tabY = this.panelY + 16 + 2;
         int filterY = tabY + this.tabHeight() + 2;
         int contentY = filterY + this.filterHeight() + 2;
         if (mouseY >= tabY && mouseY < tabY + this.tabHeight() && button == 0) {
            int x = this.panelX + 4;

            for (RiptidePacketLoggerOverlay.Category cat : RiptidePacketLoggerOverlay.Category.values()) {
               int w = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, cat.label, 5, 32, 64);
               if (mouseX >= x && mouseX < x + w) {
                  this.activeTab = cat;
                  this.dirty = true;
                  this.scrollOffset = 0;
                  return true;
               }

               x += w + 2;
            }

            return true;
         } else if (mouseY >= filterY && mouseY < filterY + this.filterHeight() && button == 0) {
            return this.handleFilterClick(mouseX, mouseY, filterY);
         } else {
            if (this.searchField.isFocused()) {
               this.searchField.setFocused(false);
            }

            int contentEndY = contentY + this.contentAreaHeight();
            if (mouseY >= contentY && mouseY < contentEndY) {
               if (button == 0) {
                  CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics();
                  if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains((int)mouseX, (int)mouseY)) {
                     this.scrollbarDragging = true;
                     this.scrollbarGrabOffset = Math.max(0, (int)mouseY - scrollbarMetrics.thumbY());
                     this.scrollOffset = this.quantizeScrollOffset(
                        CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int)mouseY, this.scrollbarGrabOffset),
                        this.lineHeight(),
                        scrollbarMetrics.maxScroll()
                     );
                     this.contentScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
                     return true;
                  }
               }

               return this.handleContentClick(mouseX, mouseY, contentY, button);
            } else {
               return false;
            }
         }
      }
   }

   private boolean handleFilterClick(double mouseX, double mouseY, int y) {
      int gap = 2;
      int row2Y = y + this.filterRowHeight() + this.filterRowGap();
      int x = this.panelX + 4;
      String captureLabel = this.configurationOnly ? (this.paused ? "Auto OFF" : "Auto ON") : (this.paused ? "Start" : "Stop");
      int captureW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, captureLabel, 5, 38, this.configurationOnly ? 66 : 54);
      if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + captureW) {
         this.setPaused(!this.paused);
         return true;
      } else {
         x += captureW + gap;
         int clrW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Clear", 5, 34, 54);
         if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + clrW) {
            this.clearAll();
            return true;
         } else {
            x += clrW + gap;
            int cpW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Copy", 5, 34, 52);
            if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + cpW) {
               this.copyToClipboard();
               return true;
            } else {
               x += cpW + gap;
               int grpW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, this.groupingEnabled ? "Group" : "Ungrp", 5, 38, 58);
               if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + grpW) {
                  this.groupingEnabled = !this.groupingEnabled;
                  this.dirty = true;
                  return true;
               } else {
                  x += grpW + gap;
                  String bt = "Blocked";
                  int bw = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, bt, 5, 54, 72);
                  if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + bw) {
                     this.openBlockedListOverlay();
                     return true;
                  } else {
                     x += bw + gap;
                     String channelsLabel = "Channels";
                     int channelsW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, channelsLabel, 5, 58, 78);
                     if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + channelsW) {
                        this.openPayloadListenerOverlay();
                        return true;
                     } else {
                        x += channelsW + gap;
                        int payloadW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, "Payload", 5, 44, 64);
                        if (mouseY >= y && mouseY < y + this.filterRowHeight() && mouseX >= x && mouseX < x + payloadW) {
                           this.openCleanPayloadEditor();
                           return true;
                        } else {
                           x = this.panelX + 4;
                           int sw = this.filterSearchFieldWidth();
                           if (mouseY >= row2Y && mouseY < row2Y + this.filterRowHeight() && mouseX >= x && mouseX < x + sw) {
                              this.searchField.mouseClicked(mouseX, mouseY, 0);
                              return true;
                           } else {
                              x += sw + 3;
                              String[] dirLabels = new String[]{"Both", "C2S", "S2C"};

                              for (int d = 0; d < 3; d++) {
                                 int dirW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, dirLabels[d], 4, 30, 48);
                                 if (mouseY >= row2Y && mouseY < row2Y + this.filterRowHeight() && mouseX >= x && mouseX < x + dirW) {
                                    this.dirFilter = d;
                                    this.dirty = true;
                                    return true;
                                 }

                                 x += dirW + gap;
                              }

                              if (this.activeTab == RiptidePacketLoggerOverlay.Category.PAYLOAD) {
                                 String listenLabel = this.payloadFilteredOnly ? "Filtered" : "All";
                                 int listenW = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, listenLabel, 4, 42, 62);
                                 if (mouseY >= row2Y && mouseY < row2Y + this.filterRowHeight() && mouseX >= x && mouseX < x + listenW) {
                                    this.payloadFilteredOnly = !this.payloadFilteredOnly;
                                    this.dirty = true;
                                    return true;
                                 }
                              }

                              this.searchField.setFocused(false);
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean handleContentClick(double mouseX, double mouseY, int contentY, int button) {
      int idx = (int)(
         (mouseY - contentY + this.contentScrollState.tick(0.0F, Math.max(0, this.displayRows.size() * this.lineHeight() - this.contentAreaHeight())))
            / this.lineHeight()
      );
      if (idx >= 0 && idx < this.displayRows.size()) {
         RiptidePacketLoggerOverlay.DisplayRow row = this.displayRows.get(idx);
         if (row.type == RiptidePacketLoggerOverlay.RowType.GROUP) {
            int bx = this.panelX + this.PANEL_WIDTH - 28;
            if (button == 0 && mouseX >= bx && mouseX <= bx + 24 && row.packetClass != null) {
               String n = RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)row.packetClass);
               this.blockPacketName(n);
               return true;
            }

            if (button == 0) {
               if (this.expandedGroups.contains(row.groupKey)) {
                  this.expandedGroups.remove(row.groupKey);
               } else {
                  this.expandedGroups.add(row.groupKey);
               }

               this.dirty = true;
               return true;
            }
         }

         if (row.type == RiptidePacketLoggerOverlay.RowType.ENTRY && row.entry != null) {
            int menuIconX = this.panelX + this.PANEL_WIDTH - 16;
            if (button == 1 || button == 0 && mouseX >= menuIconX) {
               this.openCtxMenu(row.entry, (int)mouseX, (int)mouseY);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void executeCtxAction(String action, RiptidePacketLoggerOverlay.LogEntry e) {
      if (!this.configurationOnly || !"Send".equals(action) && !"Replay".equals(action)) {
         switch (action) {
            case "Block":
               this.blockPacketName(e.shortName);
               RiptideClientMessaging.sendPrefixed("Blocked + purged from logger: " + e.shortName);
               break;
            case "Queue":
               RiptidePacketEntryActions.queue(e);
               break;
            case "Send":
               RiptidePacketEntryActions.directSend(e);
               break;
            case "+SEND":
               RiptidePacketEntryActions.addSendActionToVisibleMacro(e);
               break;
            case "+WAIT":
               RiptidePacketEntryActions.addWaitActionToVisibleMacro(e);
               break;
            case "Edit Payload":
               RiptidePacketEntryActions.openPayloadEditor(e);
               break;
            case "+PAYLOAD":
               RiptidePacketEntryActions.addPayloadActionToVisibleMacro(e);
               break;
            case "+ Filter":
               RiptideSharedState shared = RiptideSharedState.get();
               Class<? extends Packet<?>> pktCls = (Class<? extends Packet<?>>)e.packetClass;
               boolean added;
               if (e.direction.equals("C2S")) {
                  added = shared.getC2SPackets().add(pktCls);
               } else {
                  added = shared.getS2CPackets().add(pktCls);
               }

               shared.setUseCustomPackets(true);
               RiptideClientMessaging.sendPrefixed(
                  added ? "Added to custom " + e.direction + " filter: " + e.shortName : "Already present in custom " + e.direction + " filter: " + e.shortName
               );
               break;
            case "- Filter":
               RiptideSharedState shared = RiptideSharedState.get();
               Class<? extends Packet<?>> pktCls = (Class<? extends Packet<?>>)e.packetClass;
               boolean removed;
               if (e.direction.equals("C2S")) {
                  removed = shared.getC2SPackets().remove(pktCls);
               } else {
                  removed = shared.getS2CPackets().remove(pktCls);
               }

               RiptideClientMessaging.sendPrefixed(
                  removed
                     ? "Removed from custom " + e.direction + " filter: " + e.shortName
                     : "Not present in custom " + e.direction + " filter: " + e.shortName
               );
               break;
            case "Replay":
               if (e.packetRef != null && e.direction.equals("C2S")) {
                  RiptidePacketEntryActions.directSend(e);
               }
               break;
            case "Copy":
               String line = e.direction + " " + TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs)) + " T" + e.gameTick + " " + e.shortName;
               MC.keyboardHandler.setClipboard(line);
               RiptideNotifications.copied("Copied packet info.");
               break;
            case "Inspect":
               this.inspectOverlay.open(e, this.panelX + this.PANEL_WIDTH + 10, this.panelY + 8);
               break;
            case "Share to Lobby":
               this.shareEntryToLobby(e);
         }
      }
   }

   private void shareEntryToLobby(RiptidePacketLoggerOverlay.LogEntry e) {
      if (!RiptideLiteVariant.enabled()) {
         MatchmakingManager mm = MatchmakingManager.get();
         if (!mm.inLobby()) {
            RiptideClientMessaging.sendPrefixed("§cNot in a Matchmaking lobby. Open Matchmaking and join one first.");
         } else if (e != null && e.packetRef != null) {
            String data = RiptideClipboardHelper.serializeQueueToBase64(List.of(new RiptideSharedState.QueuedPacket(e.packetRef, 0)));
            if (data == null) {
               RiptideClientMessaging.sendPrefixed("§cFailed to serialize packet for sharing.");
            } else {
               MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
               offer.friendlyName = e.shortName;
               offer.direction = e.direction;
               offer.data = data;
               mm.offerPacket(offer);
               RiptideNotifications.show("Shared packet to lobby: " + e.shortName, -13248397);
            }
         } else {
            RiptideClientMessaging.sendPrefixed("§cThis entry has no replayable packet to share.");
         }
      }
   }

   private boolean handleBlockedClick(double mouseX, double mouseY, int contentEndY) {
      int y = contentEndY + 3;
      if (mouseY >= y && mouseY < y + this.lineHeight()) {
         int ubX = this.panelX + this.PANEL_WIDTH - 58;
         if (mouseX >= ubX && mouseX <= ubX + 54) {
            this.clearBlocked();
            return true;
         } else {
            int rstX = ubX - 44;
            if (mouseX >= rstX && mouseX <= rstX + 40) {
               this.resetBlockedToDefault();
               return true;
            } else {
               this.blockedExpanded = !this.blockedExpanded;
               return true;
            }
         }
      } else {
         if (this.blockedExpanded) {
            int ey = y + this.lineHeight();
            int ux = this.panelX + this.PANEL_WIDTH - 28;

            for (String name : new ArrayList<>(this.blockedNames)) {
               if (mouseX >= ux && mouseX <= ux + 24 && mouseY >= ey && mouseY < ey + this.lineHeight()) {
                  this.unblockName(name);
                  return true;
               }

               ey += this.lineHeight();
            }
         }

         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
      if (this.scrollbarDragging && b == 0) {
         CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics();
         this.scrollOffset = this.quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int)my, this.scrollbarGrabOffset), this.lineHeight(), scrollbarMetrics.maxScroll()
         );
         this.contentScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
         return true;
      } else if (this.isDragging && b == 0) {
         RiptideWindowLayout nextBounds = this.clampToScreen(
            this,
            new RiptideWindowLayout((int)(mx - this.dragOffX), (int)(my - this.dragOffY), this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed)
         );
         if (nextBounds.x != this.panelX || nextBounds.y != this.panelY) {
            this.headerDragMoved = true;
         }

         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         return true;
      } else {
         return this.searchField.mouseDragged(mx, my, b, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int b) {
      if (b == 0 && this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else if (b == 0 && this.isDragging) {
         this.isDragging = false;
         this.headerDragMoved = false;
         this.saveLayout();
         return true;
      } else {
         return this.searchField.mouseReleased(mx, my, b);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amt) {
      if (this.visible && !this.collapsed) {
         int totalH = this.displayRows.size() * this.lineHeight();
         int visH = this.contentAreaHeight();
         int maxScroll = Math.max(0, totalH - visH);
         this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset - (int)Math.round(amt * this.lineHeight() * 3.0), this.lineHeight(), maxScroll);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      return this.visible && !this.collapsed ? this.searchField.isFocused() && this.searchField.keyPressed(new KeyEvent(key, scan, mods)) : false;
   }

   @Override
   public boolean charTyped(char c, int mods) {
      return this.visible && !this.collapsed ? this.searchField.isFocused() && this.searchField.charTyped(new CharacterEvent(c)) : false;
   }

   private synchronized List<RiptidePacketLoggerOverlay.LogEntry> getActiveBuffer() {
      switch (this.activeTab) {
         case ALL:
         default:
            return new ArrayList<>(this.bufAll);
         case INVENTORY:
            return new ArrayList<>(this.bufInventory);
         case MOVEMENT:
            return new ArrayList<>(this.bufMovement);
         case PAYLOAD:
            return new ArrayList<>(this.bufPayload);
      }
   }

   private RiptidePayloadChannelListeners.Match payloadListenerMatch(RiptidePacketLoggerOverlay.LogEntry entry) {
      return entry != null && entry.isPayload && entry.payloadSnapshot != null ? this.payloadListeners.match(entry.payloadSnapshot, entry.direction) : null;
   }

   private String entrySearchKey(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry == null) {
         return "";
      } else {
         RiptidePayloadChannelListeners.Match match = this.payloadListenerMatch(entry);
         return match == null ? entry.searchKey() : entry.searchKey() + " " + match.searchText();
      }
   }

   private void rebuildDisplay() {
      List<RiptidePacketLoggerOverlay.LogEntry> source = this.getActiveBuffer();
      if (source == null) {
         source = new ArrayList<>();
      }

      String ls = this.searchFilter.toLowerCase(Locale.ROOT);
      List<RiptidePacketLoggerOverlay.LogEntry> filtered = new ArrayList<>();

      for (RiptidePacketLoggerOverlay.LogEntry e : source) {
         if (!this.isBlockedClass(e.packetClass)
            && (this.activeTab != RiptidePacketLoggerOverlay.Category.PAYLOAD || !this.payloadFilteredOnly || this.payloadListenerMatch(e) != null)
            && (ls.isEmpty() || this.entrySearchKey(e).contains(ls))
            && (this.dirFilter != 1 || e.direction.equals("C2S"))
            && (this.dirFilter != 2 || e.direction.equals("S2C"))) {
            filtered.add(e);
         }
      }

      Map<String, Integer> counts = new LinkedHashMap<>();
      Map<String, String> dirs = new LinkedHashMap<>();
      Map<String, Class<?>> classes = new LinkedHashMap<>();

      for (RiptidePacketLoggerOverlay.LogEntry ex : filtered) {
         String key = ex.groupKey;
         counts.merge(key, 1, Integer::sum);
         dirs.put(key, ex.direction);
         classes.put(key, ex.packetClass);
      }

      Set<String> groupedKeys = new HashSet<>();
      if (this.groupingEnabled) {
         for (Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 10) {
               groupedKeys.add(entry.getKey());
            }
         }
      }

      List<RiptidePacketLoggerOverlay.LogEntry> reversed = new ArrayList<>(filtered);
      Collections.reverse(reversed);
      List<RiptidePacketLoggerOverlay.DisplayRow> rows = new ArrayList<>();
      Set<String> added = new LinkedHashSet<>();

      for (RiptidePacketLoggerOverlay.LogEntry ex : reversed) {
         String key = ex.groupKey;
         if (groupedKeys.contains(key) && added.add(key)) {
            RiptidePacketLoggerOverlay.DisplayRow h = new RiptidePacketLoggerOverlay.DisplayRow();
            h.type = RiptidePacketLoggerOverlay.RowType.GROUP;
            h.groupKey = key;
            h.groupCount = counts.get(key);
            h.direction = dirs.get(key);
            h.packetClass = classes.get(key);
            h.payloadSnapshot = ex.payloadSnapshot;
            rows.add(h);
            if (this.expandedGroups.contains(key)) {
               for (RiptidePacketLoggerOverlay.LogEntry child : reversed) {
                  if (child.groupKey.equals(key)) {
                     RiptidePacketLoggerOverlay.DisplayRow r = new RiptidePacketLoggerOverlay.DisplayRow();
                     r.type = RiptidePacketLoggerOverlay.RowType.ENTRY;
                     r.entry = child;
                     rows.add(r);
                  }
               }
            }
         }
      }

      for (RiptidePacketLoggerOverlay.LogEntry exx : reversed) {
         if (!groupedKeys.contains(exx.groupKey)) {
            RiptidePacketLoggerOverlay.DisplayRow r = new RiptidePacketLoggerOverlay.DisplayRow();
            r.type = RiptidePacketLoggerOverlay.RowType.ENTRY;
            r.entry = exx;
            rows.add(r);
         }
      }

      this.displayRows = rows;
      this.clampScrollOffset();
   }

   private void clampScrollOffset() {
      int totalH = this.displayRows.size() * this.lineHeight();
      int visH = this.currentPanelHeight - 16 - this.tabHeight() - this.filterHeight() - 8 - this.blockedH();
      int maxScroll = Math.max(0, totalH - visH);
      this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset, this.lineHeight(), maxScroll);
   }

   private int calcPanelH() {
      int rc = Math.min(this.displayRows.size(), this.maxVisibleRows());
      return Math.max(this.PANEL_HEIGHT, 16 + this.tabHeight() + this.filterHeight() + 8 + Math.max(rc * this.lineHeight(), 24) + this.blockedH() + 4);
   }

   private int blockedH() {
      return 0;
   }

   private void openBlockedListOverlay() {
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      this.blockedListOverlay.panelX = Math.max(4, (sw - this.blockedListOverlay.panelWidth) / 2);
      this.blockedListOverlay.panelY = Math.max(4, (sh - this.blockedListOverlay.panelHeight) / 2);
      this.blockedListOverlay.setVisible(true);
      this.blockedListOverlay.rebuildRows();
      RiptideOverlayManager.get().bringToFront(this.blockedListOverlay);
   }

   private void openPayloadListenerOverlay() {
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      this.payloadListeners.load();
      this.payloadRegistrations.load();
      this.payloadListenerOverlay.panelX = Math.max(4, (sw - this.payloadListenerOverlay.panelWidth) / 2);
      this.payloadListenerOverlay.panelY = Math.max(4, (sh - this.payloadListenerOverlay.panelHeight) / 2);
      this.payloadListenerOverlay.setVisible(true);
      this.payloadListenerOverlay.rebuildRows();
      RiptideOverlayManager.get().bringToFront(this.payloadListenerOverlay);
   }

   private void openCleanPayloadEditor() {
      ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlay();
      editor.openStandalonePayloadEditor(new PayloadAction());
      RiptideOverlayManager.get().bringToFront(editor);
   }

   private synchronized void clearAll() {
      this.pendingEntries.clear();
      this.bufAll.clear();
      this.bufInventory.clear();
      this.bufMovement.clear();
      this.bufPayload.clear();
      this.packetContextTracker.reset();
      this.displayRows.clear();
      this.scrollOffset = 0;
      this.contentScrollState.jumpTo(0, 0);
      this.expandedGroups.clear();
      this.dirty = true;
   }

   private void copyToClipboard() {
      this.flushPending();
      StringBuilder sb = new StringBuilder();
      sb.append("=== Packet Logger [").append(this.activeTab.label).append("] ===\n");
      if (!this.searchFilter.isEmpty()) {
         sb.append("Search: \"").append(this.searchFilter).append("\"\n");
      }

      sb.append(String.format("%-5s %-14s %-8s %s%n", "DIR", "TIME", "TICK", "PACKET"));
      sb.append("----------------------------------------------\n");
      List<RiptidePacketLoggerOverlay.LogEntry> source = this.getActiveBuffer();
      if (source == null) {
         source = new ArrayList<>();
      }

      String ls = this.searchFilter.toLowerCase(Locale.ROOT);
      int count = 0;

      for (RiptidePacketLoggerOverlay.LogEntry e : source) {
         if (!this.isBlockedClass(e.packetClass)
            && (this.activeTab != RiptidePacketLoggerOverlay.Category.PAYLOAD || !this.payloadFilteredOnly || this.payloadListenerMatch(e) != null)
            && (ls.isEmpty() || this.entrySearchKey(e).contains(ls))
            && (this.dirFilter != 1 || e.direction.equals("C2S"))
            && (this.dirFilter != 2 || e.direction.equals("S2C"))) {
            String copiedName = e.shortName;
            if (e.payloadSnapshot != null) {
               String summary = RiptidePayloadSupport.summarizeForLogger(e.payloadSnapshot, true);
               if (!summary.isBlank()) {
                  copiedName = copiedName + " " + summary;
               }
            }

            RiptidePayloadChannelListeners.Match match = this.payloadListenerMatch(e);
            if (match != null) {
               copiedName = copiedName + " [" + match.label() + "]";
            }

            sb.append(
               String.format(
                  "%-5s %-14s %-8s %s%n",
                  e.direction.equals("C2S") ? "->" : "<-",
                  TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs)),
                  "T" + e.gameTick,
                  copiedName
               )
            );
            count++;
         }
      }

      sb.append("----------------------------------------------\n");
      sb.append("Total: ").append(count).append(" packets\n");
      MC.keyboardHandler.setClipboard(sb.toString());
      RiptideNotifications.copied("Copied " + count + " packets.");
   }

   private int defaultPanelWidth() {
      return 360;
   }

   private int defaultPanelHeight() {
      return 250;
   }

   private int lineHeight() {
      return 12;
   }

   private int tabHeight() {
      return 16;
   }

   private int filterRowHeight() {
      return 16;
   }

   private int filterRowGap() {
      return 2;
   }

   private int filterHeight() {
      return this.filterRowHeight() * 2 + this.filterRowGap();
   }

   private int searchFieldWidth() {
      return 148;
   }

   private int filterSearchFieldWidth() {
      return this.activeTab == RiptidePacketLoggerOverlay.Category.PAYLOAD ? 108 : this.searchFieldWidth();
   }

   private int maxVisibleRows() {
      return 22;
   }

   private final class BlockedPacketListOverlay extends RiptideOverlayBase {
      private final RiptideChatField search;
      private final ScrollState rowsScroll;
      private final List<Class<? extends Packet<?>>> allPackets;
      private final Set<Class<? extends Packet<?>>> c2sSet;
      private final List<Class<? extends Packet<?>>> rows;
      private int rowScroll;
      private boolean draggingScroll;
      private int scrollGrabOffset;
      private boolean showBlockedOnly;
      private boolean draggingWindow;
      private double dragOffsetX;
      private double dragOffsetY;
      private boolean searchRowsDirty;
      private static final int BLOCKED_W = 54;
      private static final int CLEAR_W = 44;
      private static final int RESET_W = 44;

      BlockedPacketListOverlay() {
         Objects.requireNonNull(RiptidePacketLoggerOverlay.this);
         super("PacketLoggerBlockedList", 250, 248);
         this.search = new RiptideChatField(RiptidePacketLoggerOverlay.MC, RiptidePacketLoggerOverlay.this.textRenderer, 0, 0, 120, 16, false);
         this.rowsScroll = new ScrollState();
         this.allPackets = new ArrayList<>();
         this.c2sSet = new HashSet<>();
         this.rows = new ArrayList<>();
         this.panelX = 260;
         this.panelY = 56;
         this.visible = false;
         this.search.setPlaceholder(Component.literal("Search packets..."));
         this.search.setMaxLength(120);
         this.search.setChangedListener(value -> this.searchRowsDirty = true);
         List<Class<? extends Packet<?>>> c2s = new ArrayList<>(RiptidePacketRegistry.getC2SPackets());
         List<Class<? extends Packet<?>>> s2c = new ArrayList<>(RiptidePacketRegistry.getS2CPackets());
         Comparator<Class<? extends Packet<?>>> byName = Comparator.comparing(RiptidePacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER);
         c2s.sort(byName);
         s2c.sort(byName);
         this.c2sSet.addAll(c2s);
         this.allPackets.addAll(c2s);
         this.allPackets.addAll(s2c);
      }

      private int controlsRowY() {
         return this.panelY + 16 + 5 + 20;
      }

      private int listTopY() {
         return this.controlsRowY() + 21;
      }

      private String blockKey(Class<? extends Packet<?>> cls) {
         String name = RiptidePacketRegistry.getName(cls);
         return name != null ? name : RiptidePacketNamer.getFriendlyName(cls);
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
         if (this.visible) {
            if (this.searchRowsDirty) {
               this.searchRowsDirty = false;
               this.rebuildRows();
            }

            RiptideWindowLayout bounds = this.clampToScreen(
               this, new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
            );
            this.panelX = bounds.x;
            this.panelY = bounds.y;
            this.renderWindowFrame(ctx, mx, my, bounds, "Block List", this.collapsed, false);
            boolean clip = this.beginWindowBodyClip(ctx, bounds, this.collapsed);
            if (!clip) {
               this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, false);
            } else {
               try {
                  int x = this.panelX + 6;
                  int y = this.panelY + 16 + 5;
                  int rowH = 16;
                  this.search.setX(x);
                  this.search.setY(y);
                  this.search.setWidth(Math.max(80, this.panelWidth - 12));
                  this.search.setHeight(rowH);
                  this.search.render(ctx, mx, my, delta);
                  y = this.controlsRowY();
                  CompactOverlayControls.tab(ctx, RiptidePacketLoggerOverlay.this.textRenderer, x, y, 54, rowH, "Blocked", this.showBlockedOnly, mx, my);
                  int clearX = x + 54 + 4;
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, clearX, y, 44, rowH, "Clear", CompactOverlayButton.Variant.DANGER, true, mx, my
                  );
                  int resetX = clearX + 44 + 4;
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, resetX, y, 44, rowH, "Reset", CompactOverlayButton.Variant.GHOST, true, mx, my
                  );
                  int countX = resetX + 44 + 8;
                  RiptidePacketLoggerOverlay.this.drawUiText(
                     ctx, RiptidePacketLoggerOverlay.this.blockedNames.size() + " blocked", UiTone.MUTED, RiptideColors.textSecondary(), countX, y + 4
                  );
                  int listTop = this.listTopY();
                  int listH = Math.max(20, this.panelY + this.panelHeight - listTop - 6);
                  int contentH = this.rows.size() * RiptidePacketLoggerOverlay.this.lineHeight();
                  int maxScroll = Math.max(0, contentH - listH);
                  this.rowScroll = this.quantizeScrollOffset(this.rowScroll, RiptidePacketLoggerOverlay.this.lineHeight(), maxScroll);
                  this.rowsScroll.setTarget(this.rowScroll, maxScroll);
                  int drawScroll = this.rowsScroll.tick(delta, maxScroll);
                  UiScissorStack.global().push(ctx, UiBounds.of(this.panelX + 4, listTop, this.panelWidth - 8, listH));
                  int base = listTop - drawScroll;
                  int rowHeight = RiptidePacketLoggerOverlay.this.lineHeight();
                  int firstVisible = Math.max(0, drawScroll / rowHeight);
                  int lastVisible = Math.min(this.rows.size() - 1, (drawScroll + listH - 1) / rowHeight);

                  for (int i = firstVisible; i <= lastVisible; i++) {
                     int ry = base + i * rowHeight;
                     Class<? extends Packet<?>> cls = this.rows.get(i);
                     boolean c2s = this.c2sSet.contains(cls);
                     boolean blocked = RiptidePacketLoggerOverlay.this.isBlockedName(cls, RiptidePacketNamer.getFriendlyName(cls));
                     boolean hover = mx >= this.panelX + 4
                        && mx < this.panelX + this.panelWidth - 8
                        && my >= ry
                        && my < ry + RiptidePacketLoggerOverlay.this.lineHeight();
                     int fill = blocked ? RiptideColors.packetRowBlockedBg(hover) : RiptideColors.packetRowBg(c2s, i, hover);
                     ctx.fill(this.panelX + 4, ry, this.panelX + this.panelWidth - 8, ry + RiptidePacketLoggerOverlay.this.lineHeight(), fill);
                     if (blocked) {
                        ctx.fill(
                           this.panelX + 4, ry, this.panelX + 6, ry + RiptidePacketLoggerOverlay.this.lineHeight(), RiptideColors.packetRowBlockedAccent()
                        );
                     }

                     int color = RiptideColors.packetRowText(c2s, i);
                     String name = RiptidePacketNamer.getFriendlyName(cls);
                     String label = UiText.trimToWidth(
                        RiptidePacketLoggerOverlay.this.textRenderer,
                        name,
                        Math.max(1, this.panelWidth - 24),
                        RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY),
                        color
                     );
                     RiptidePacketLoggerOverlay.this.drawUiText(ctx, label, UiTone.BODY, color, this.panelX + 9, ry + 1);
                  }

                  UiScissorStack.global().pop(ctx);
                  CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, listH, this.panelX + this.panelWidth - 5, listTop, 3, listH, drawScroll);
                  CompactScrollbar.draw(ctx, metrics, metrics.contains(mx, my), this.draggingScroll);
               } finally {
                  this.endWindowBodyClip(ctx, clip);
                  this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, false);
               }
            }
         }
      }

      private void rebuildRows() {
         String query = this.search.getText() == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
         this.rows.clear();

         for (Class<? extends Packet<?>> cls : this.allPackets) {
            if (!this.showBlockedOnly || RiptidePacketLoggerOverlay.this.isBlockedName(cls, RiptidePacketNamer.getFriendlyName(cls))) {
               if (!query.isEmpty()) {
                  String hay = (RiptidePacketNamer.getFriendlyName(cls) + " " + cls.getSimpleName()).toLowerCase(Locale.ROOT);
                  if (!hay.contains(query)) {
                     continue;
                  }
               }

               this.rows.add(cls);
            }
         }

         this.rowScroll = Math.min(this.rowScroll, Math.max(0, this.rows.size() * RiptidePacketLoggerOverlay.this.lineHeight()));
      }

      @Override
      public boolean usesSharedHeaderClickCollapse() {
         return true;
      }

      @Override
      public boolean mouseClicked(double mx, double my, int button) {
         if (!this.visible) {
            return false;
         } else {
            RiptideWindowLayout bounds = this.getBounds();
            if (button == 0 && this.isOverCloseButton(mx, my, bounds)) {
               this.setVisible(false);
               return true;
            } else if (button == 0 && this.isOverDragBar(mx, my)) {
               this.draggingWindow = true;
               this.dragOffsetX = mx - this.panelX;
               this.dragOffsetY = my - this.panelY;
               return true;
            } else if (this.collapsed) {
               return true;
            } else {
               int x = this.panelX + 6;
               if (this.search.mouseClicked(mx, my, button)) {
                  return true;
               } else {
                  int y = this.controlsRowY();
                  if (button == 0 && my >= y && my < y + 16) {
                     int clearX = x + 54 + 4;
                     int resetX = clearX + 44 + 4;
                     if (mx >= x && mx < x + 54) {
                        this.showBlockedOnly = !this.showBlockedOnly;
                        this.rowScroll = 0;
                        this.rebuildRows();
                        return true;
                     }

                     if (mx >= clearX && mx < clearX + 44) {
                        RiptidePacketLoggerOverlay.this.clearBlocked();
                        this.rebuildRows();
                        return true;
                     }

                     if (mx >= resetX && mx < resetX + 44) {
                        RiptidePacketLoggerOverlay.this.resetBlockedToDefault();
                        this.rebuildRows();
                        return true;
                     }
                  }

                  int listTop = this.listTopY();
                  int listH = Math.max(20, this.panelY + this.panelHeight - listTop - 6);
                  CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
                     this.rows.size() * RiptidePacketLoggerOverlay.this.lineHeight(),
                     listH,
                     this.panelX + this.panelWidth - 5,
                     listTop,
                     3,
                     listH,
                     this.rowScroll
                  );
                  if (button == 0 && metrics.hasScroll() && metrics.contains((int)mx, (int)my)) {
                     this.draggingScroll = true;
                     this.scrollGrabOffset = (int)my - metrics.thumbY();
                     return true;
                  } else {
                     if (button == 0 && my >= listTop) {
                        int index = (int)((my - listTop + this.rowScroll) / RiptidePacketLoggerOverlay.this.lineHeight());
                        if (index >= 0 && index < this.rows.size()) {
                           Class<? extends Packet<?>> cls = this.rows.get(index);
                           String key = this.blockKey(cls);
                           if (RiptidePacketLoggerOverlay.this.isBlockedName(cls, RiptidePacketNamer.getFriendlyName(cls))) {
                              RiptidePacketLoggerOverlay.this.unblockAllMatching(cls, RiptidePacketNamer.getFriendlyName(cls));
                           } else {
                              RiptidePacketLoggerOverlay.this.blockPacketName(key);
                           }

                           if (this.showBlockedOnly) {
                              this.rebuildRows();
                           }

                           return true;
                        }
                     }

                     return this.isMouseOver(mx, my);
                  }
               }
            }
         }
      }

      @Override
      public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
         if (this.draggingWindow && button == 0) {
            this.setBounds(
               new RiptideWindowLayout(
                  (int)Math.round(mx - this.dragOffsetX),
                  (int)Math.round(my - this.dragOffsetY),
                  this.panelWidth,
                  this.panelHeight,
                  this.visible,
                  this.collapsed
               )
            );
            return true;
         } else if (this.draggingScroll && button == 0) {
            int listTop = this.listTopY();
            int listH = Math.max(20, this.panelY + this.panelHeight - listTop - 6);
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
               this.rows.size() * RiptidePacketLoggerOverlay.this.lineHeight(), listH, this.panelX + this.panelWidth - 5, listTop, 3, listH, this.rowScroll
            );
            this.rowScroll = this.quantizeScrollOffset(
               CompactScrollbar.scrollFromThumb(metrics, (int)my, this.scrollGrabOffset), RiptidePacketLoggerOverlay.this.lineHeight(), metrics.maxScroll()
            );
            return true;
         } else {
            return this.search.mouseDragged(mx, my, button, dx, dy);
         }
      }

      @Override
      public boolean mouseReleased(double mx, double my, int button) {
         if (button == 0 && this.draggingWindow) {
            this.draggingWindow = false;
            this.saveLayout();
            return true;
         } else if (button == 0 && this.draggingScroll) {
            this.draggingScroll = false;
            return true;
         } else {
            return this.search.mouseReleased(mx, my, button);
         }
      }

      @Override
      public boolean mouseScrolled(double mx, double my, double amount) {
         if (this.visible && !this.collapsed && this.isMouseOver(mx, my)) {
            int listTop = this.listTopY();
            int listH = Math.max(20, this.panelY + this.panelHeight - listTop - 6);
            int maxScroll = Math.max(0, this.rows.size() * RiptidePacketLoggerOverlay.this.lineHeight() - listH);
            this.rowScroll = this.quantizeScrollOffset(
               this.rowScroll - (int)Math.signum(amount) * RiptidePacketLoggerOverlay.this.lineHeight(),
               RiptidePacketLoggerOverlay.this.lineHeight(),
               maxScroll
            );
            this.rowsScroll.jumpTo(this.rowScroll, maxScroll);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
         return this.visible && this.search.isFocused() && this.search.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
      }

      @Override
      public boolean charTyped(char chr, int modifiers) {
         return this.visible && this.search.isFocused() && this.search.charTyped(new CharacterEvent(chr));
      }

      @Override
      public boolean hasTextFieldFocused() {
         return this.visible && this.search.isFocused();
      }

      @Override
      public void clearTextFieldFocus() {
         this.search.setFocused(false);
      }
   }

   public static enum Category {
      ALL("All"),
      INVENTORY("INV"),
      MOVEMENT("Move"),
      PAYLOAD("Payload");

      public final String label;

      private Category(String l) {
         this.label = l;
      }
   }

   static class DisplayRow {
      RiptidePacketLoggerOverlay.RowType type;
      RiptidePacketLoggerOverlay.LogEntry entry;
      String groupKey;
      int groupCount;
      String direction;
      Class<?> packetClass;
      RiptidePayloadSupport.PayloadSnapshot payloadSnapshot;
   }

   private static final class ListenerRow {
      final RiptidePacketLoggerOverlay.ListenerRowKind kind;
      final String header;
      final int ruleIndex;
      final RiptideConfig.PayloadChannelFilterRule rule;
      final int registrationIndex;
      final RiptideConfig.PayloadChannelRegistrationRule registration;
      final String learnedChannel;
      final RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family;
      final RiptidePayloadChannelListeners.Preset preset;

      private ListenerRow(
         RiptidePacketLoggerOverlay.ListenerRowKind kind,
         String header,
         int ruleIndex,
         RiptideConfig.PayloadChannelFilterRule rule,
         int registrationIndex,
         RiptideConfig.PayloadChannelRegistrationRule registration,
         String learnedChannel,
         RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family,
         RiptidePayloadChannelListeners.Preset preset
      ) {
         this.kind = kind;
         this.header = header;
         this.ruleIndex = ruleIndex;
         this.rule = rule;
         this.registrationIndex = registrationIndex;
         this.registration = registration;
         this.learnedChannel = learnedChannel;
         this.family = family;
         this.preset = preset;
      }

      static RiptidePacketLoggerOverlay.ListenerRow header(String label) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.HEADER, label, -1, null, -1, null, null, null, null);
      }

      static RiptidePacketLoggerOverlay.ListenerRow empty(String label) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.EMPTY, label, -1, null, -1, null, null, null, null);
      }

      static RiptidePacketLoggerOverlay.ListenerRow registration(int index, RiptideConfig.PayloadChannelRegistrationRule rule) {
         return new RiptidePacketLoggerOverlay.ListenerRow(
            RiptidePacketLoggerOverlay.ListenerRowKind.REGISTRATION, null, -1, null, index, rule, null, null, null
         );
      }

      static RiptidePacketLoggerOverlay.ListenerRow learned(String channel) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.LEARNED, null, -1, null, -1, null, channel, null, null);
      }

      static RiptidePacketLoggerOverlay.ListenerRow active(int index, RiptideConfig.PayloadChannelFilterRule rule) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.ACTIVE, null, index, rule, -1, null, null, null, null);
      }

      static RiptidePacketLoggerOverlay.ListenerRow family(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.FAMILY, null, -1, null, -1, null, null, family, null);
      }

      static RiptidePacketLoggerOverlay.ListenerRow preset(RiptidePayloadChannelListeners.Preset preset) {
         return new RiptidePacketLoggerOverlay.ListenerRow(RiptidePacketLoggerOverlay.ListenerRowKind.PRESET, null, -1, null, -1, null, null, null, preset);
      }
   }

   private static enum ListenerRowKind {
      HEADER,
      REGISTRATION,
      LEARNED,
      ACTIVE,
      FAMILY,
      PRESET,
      EMPTY;
   }

   private record LogCaptureContext(String blockStateSummary, String screenSummary) {
      private static final RiptidePacketLoggerOverlay.LogCaptureContext EMPTY = new RiptidePacketLoggerOverlay.LogCaptureContext(null, null);
   }

   public static class LogEntry {
      public final long id = RiptidePacketLoggerOverlay.idCounter++;
      public final long timestampMs;
      public final int gameTick;
      public final String direction;
      public final String shortName;
      private volatile String searchKey;
      public final String groupKey;
      public final Class<?> packetClass;
      public final Packet<?> packetRef;
      public final boolean isInventory;
      public final boolean isMovement;
      public final boolean isPayload;
      public final String capturedBlockState;
      public final String capturedScreen;
      public final RiptidePayloadSupport.PayloadSnapshot payloadSnapshot;
      public final RiptidePacketContextTracker.Capture packetContext;

      public LogEntry(
         long ts,
         int tick,
         String dir,
         String name,
         Class<?> cls,
         Packet<?> ref,
         boolean inv,
         boolean mov,
         boolean payload,
         String capturedBlockState,
         String capturedScreen,
         RiptidePayloadSupport.PayloadSnapshot payloadSnapshot,
         RiptidePacketContextTracker.Capture packetContext
      ) {
         this.timestampMs = ts;
         this.gameTick = tick;
         this.direction = dir;
         this.shortName = name;
         this.payloadSnapshot = payloadSnapshot;
         this.groupKey = dir + ":" + name;
         this.packetClass = cls;
         this.packetRef = ref;
         this.isInventory = inv;
         this.isMovement = mov;
         this.isPayload = payload;
         this.capturedBlockState = capturedBlockState;
         this.capturedScreen = capturedScreen;
         this.packetContext = packetContext == null ? RiptidePacketContextTracker.EMPTY_CAPTURE : packetContext;
      }

      public String searchKey() {
         String key = this.searchKey;
         if (key == null) {
            String payloadSearch = this.payloadSnapshot == null ? "" : " " + this.payloadSnapshot.channel() + " " + this.payloadSnapshot.rawDump();
            key = (this.shortName == null ? "" : this.shortName).concat(payloadSearch).toLowerCase(Locale.ROOT);
            this.searchKey = key;
         }

         return key;
      }
   }

   private static final class PayloadChannelCategoryRow {
      final String group;
      final Map<String, RiptidePacketLoggerOverlay.PayloadChannelFamilyRow> families = new LinkedHashMap<>();
      final List<RiptidePayloadChannelListeners.Preset> standalonePresets = new ArrayList<>();
      List<RiptidePacketLoggerOverlay.PayloadChannelFamilyRow> sortedFamilies = List.of();
      List<RiptidePayloadChannelListeners.Preset> sortedStandalonePresets = List.of();

      PayloadChannelCategoryRow(String group) {
         this.group = group;
      }

      RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family(RiptidePacketLoggerOverlay.PayloadChannelFamilyKey familyKey) {
         return this.families.computeIfAbsent(familyKey.stableKey(), unused -> new RiptidePacketLoggerOverlay.PayloadChannelFamilyRow(familyKey));
      }
   }

   private record PayloadChannelFamilyKey(String group, String key, String label) {
      String stableKey() {
         return this.group + "|" + this.key;
      }
   }

   private static final class PayloadChannelFamilyRow {
      final String group;
      final String key;
      final String label;
      final List<RiptidePayloadChannelListeners.Preset> presets = new ArrayList<>();
      List<RiptidePayloadChannelListeners.Preset> sortedPresets = List.of();
      private Set<String> exactChannelsCache;

      PayloadChannelFamilyRow(RiptidePacketLoggerOverlay.PayloadChannelFamilyKey familyKey) {
         this.group = familyKey.group();
         this.key = familyKey.key();
         this.label = familyKey.label();
      }

      String stableKey() {
         return this.group + "|" + this.key;
      }

      int exactChannelCount() {
         return this.exactChannels().size();
      }

      Set<String> exactChannels() {
         if (this.exactChannelsCache != null) {
            return this.exactChannelsCache;
         } else {
            LinkedHashSet<String> channels = new LinkedHashSet<>();

            for (RiptidePayloadChannelListeners.Preset preset : this.presets) {
               if (preset != null) {
                  String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
                  if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                     channels.add(pattern);
                  }
               }
            }

            this.exactChannelsCache = Collections.unmodifiableSet(channels);
            return this.exactChannelsCache;
         }
      }

      List<RiptidePayloadChannelListeners.Preset> sortedPresets() {
         return this.sortedPresets != null && !this.sortedPresets.isEmpty() ? this.sortedPresets : this.presets;
      }
   }

   private final class PayloadChannelListenerOverlay extends RiptideOverlayBase {
      private final RiptideChatField search;
      private final RiptideChatField customPattern;
      private final ScrollState rowsScroll;
      private final List<RiptidePacketLoggerOverlay.ListenerRow> rows;
      private int rowScroll;
      private boolean draggingScroll;
      private int scrollGrabOffset;
      private boolean draggingWindow;
      private double dragOffsetX;
      private double dragOffsetY;
      private boolean enabledOnly;
      private boolean channelConfigDirty;
      private final Set<String> expandedPayloadFamilies;
      private int editingRuleIndex;
      private int editingRegistrationIndex;
      private Map<String, RiptidePacketLoggerOverlay.PayloadChannelCategoryRow> payloadCategoryCache;
      private List<RiptidePacketLoggerOverlay.PayloadChannelCategoryRow> sortedPayloadCategoryCache;
      private final Set<String> allPresetKeysCache;
      private final Set<String> enabledPresetKeysCache;
      private final Set<String> enabledRegistrationChannelsCache;
      private final Map<String, Integer> groupPresetCountCache;
      private final Map<String, Integer> groupExactCountCache;
      private final Map<String, RiptidePayloadChannelListeners.Preset> presetByExactChannelCache;
      private final Map<RiptidePayloadChannelListeners.Preset, String> presetSearchTextCache;
      private final Map<String, RiptidePayloadChannelSubscriptionManager.RegistrationImpact> impactByPatternCache;
      private int enabledHighlightCountCache;
      private String pendingRegistrationLabelCache;
      private int cachedRegistrationLimit;
      private boolean registrationWarningOpen;
      private long registrationWarningOpenedAtMs;
      private boolean searchRowsDirty;
      private static final int ROW_H = 16;
      private static final int CUSTOM_ADD_W = 52;
      private static final int CUSTOM_CANCEL_W = 52;
      private static final int CUSTOM_GAP = 4;
      private static final int LIST_PAD_X = 6;
      private static final int LIST_FRAME_PAD = 2;
      private static final int SCROLLBAR_GUTTER = 7;
      private static final int CHANNEL_COL_W = 58;
      private static final int STUDY_COL_W = 36;
      private static final int STATUS_COL_W = 30;
      private static final int STATUS_BADGE_H = 12;
      private static final int REMOVE_COL_W = 14;
      private static final int DEFAULTS_W = 88;
      private static final int KNOWN_ON_W = 62;
      private static final int CAPTURE_W = 56;
      private static final int FILTER_W = 62;
      private static final int APPLY_W = 46;
      private static final int REVERT_W = 50;
      private static final int ALL_OFF_W = 50;
      private static final int REGISTRATION_LOCK_W = 122;
      private static final int REGISTRATION_WARNING_DELAY_MS = 5000;
      private static final int REGISTRATION_WARNING_PAD_X = 14;
      private static final int REGISTRATION_WARNING_PAD_Y = 10;
      private static final int CONTROL_GAP = 4;

      PayloadChannelListenerOverlay() {
         Objects.requireNonNull(RiptidePacketLoggerOverlay.this);
         super("PacketLoggerPayloadChannels", 492, 336);
         this.search = new RiptideChatField(RiptidePacketLoggerOverlay.MC, RiptidePacketLoggerOverlay.this.textRenderer, 0, 0, 120, 16, false);
         this.customPattern = new RiptideChatField(RiptidePacketLoggerOverlay.MC, RiptidePacketLoggerOverlay.this.textRenderer, 0, 0, 112, 16, false);
         this.rowsScroll = new ScrollState();
         this.rows = new ArrayList<>();
         this.expandedPayloadFamilies = new HashSet<>();
         this.editingRuleIndex = -1;
         this.editingRegistrationIndex = -1;
         this.allPresetKeysCache = new HashSet<>();
         this.enabledPresetKeysCache = new HashSet<>();
         this.enabledRegistrationChannelsCache = new LinkedHashSet<>();
         this.groupPresetCountCache = new HashMap<>();
         this.groupExactCountCache = new HashMap<>();
         this.presetByExactChannelCache = new HashMap<>();
         this.presetSearchTextCache = new IdentityHashMap<>();
         this.impactByPatternCache = new HashMap<>();
         this.pendingRegistrationLabelCache = "";
         this.cachedRegistrationLimit = 96;
         this.panelX = 270;
         this.panelY = 58;
         this.visible = false;
         this.search.setPlaceholder(Component.literal("Search presets/channels..."));
         this.search.setMaxLength(120);
         this.search.setChangedListener(value -> this.searchRowsDirty = true);
         this.customPattern.setPlaceholder(Component.literal("channel or wildcard: shopgui:main / oraxen:*"));
         this.customPattern.setMaxLength(96);
      }

      private int searchY() {
         return this.panelY + 16 + 5;
      }

      private int customY() {
         return this.searchY() + 18;
      }

      private int controlsY() {
         return this.customY() + 18;
      }

      private int statusY() {
         return this.controlsY() + 18;
      }

      private int registrationButtonY() {
         return this.statusY() - 1;
      }

      private int listTopY() {
         return this.statusY() + 18;
      }

      private int listLeft() {
         return this.panelX + 6;
      }

      private int listRight() {
         return this.panelX + this.panelWidth - 6 - 7;
      }

      private int listWidth() {
         return Math.max(20, this.listRight() - this.listLeft());
      }

      private int listFrameLeft() {
         return this.listLeft() - 2;
      }

      private int listFrameRight() {
         return this.panelX + this.panelWidth - 5;
      }

      private int listenerListHeight() {
         int raw = Math.max(16, this.panelY + this.panelHeight - this.listTopY() - 8);
         return Math.max(16, this.alignViewportHeight(raw, 16));
      }

      private boolean isEditingCustomEntry() {
         return this.editingRuleIndex >= 0 || this.editingRegistrationIndex >= 0;
      }

      private int customPatternWidth() {
         int buttons = 52 + (this.isEditingCustomEntry() ? 56 : 0);
         return Math.max(120, this.panelWidth - 12 - buttons - 4);
      }

      private void clearCustomEditState() {
         this.editingRuleIndex = -1;
         this.editingRegistrationIndex = -1;
         this.customPattern.setText("");
         this.customPattern.setPlaceholder(Component.literal("exact channel or highlight wildcard"));
         this.customPattern.setFocused(false);
      }

      private void commitPayloadChannelConfigInMemory() {
         RiptidePacketLoggerOverlay.this.payloadListeners.commit(false);
         RiptidePacketLoggerOverlay.this.payloadRegistrations.commit(false);
      }

      private void markPayloadChannelConfigDirty() {
         this.channelConfigDirty = true;
         RiptidePacketLoggerOverlay.this.dirty = true;
      }

      private void savePendingPayloadChannelConfig() {
         if (this.channelConfigDirty) {
            this.commitPayloadChannelConfigInMemory();
            RiptideConfig.getGlobal().save();
            this.channelConfigDirty = false;
         }
      }

      @Override
      public void setVisible(boolean v) {
         if (!v) {
            this.savePendingPayloadChannelConfig();
            this.clearCustomEditState();
            this.registrationWarningOpen = false;
         }

         super.setVisible(v);
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
         if (this.visible) {
            if (this.searchRowsDirty) {
               this.searchRowsDirty = false;
               this.rebuildRows();
            }

            RiptideWindowLayout bounds = this.clampToScreen(
               this, new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
            );
            this.panelX = bounds.x;
            this.panelY = bounds.y;
            this.renderWindowFrame(ctx, mx, my, bounds, "Payload Channels", this.collapsed, false);
            boolean clip = this.beginWindowBodyClip(ctx, bounds, this.collapsed);
            if (!clip) {
               this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, false);
            } else {
               try {
                  int x = this.panelX + 6;
                  int y = this.searchY();
                  this.search.setX(x);
                  this.search.setY(y);
                  this.search.setWidth(Math.max(80, this.panelWidth - 12));
                  this.search.setHeight(16);
                  this.search.render(ctx, mx, my, delta);
                  y = this.customY();
                  int customW = this.customPatternWidth();
                  this.customPattern.setX(x);
                  this.customPattern.setY(y);
                  this.customPattern.setWidth(customW);
                  this.customPattern.setHeight(16);
                  this.customPattern.render(ctx, mx, my, delta);
                  int addX = x + customW + 4;
                  CompactOverlayControls.action(
                     ctx,
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     addX,
                     y,
                     52,
                     16,
                     this.isEditingCustomEntry() ? "Save" : "Add",
                     CompactOverlayButton.Variant.SUCCESS,
                     true,
                     mx,
                     my
                  );
                  if (this.isEditingCustomEntry()) {
                     int cancelX = addX + 52 + 4;
                     CompactOverlayControls.action(
                        ctx, RiptidePacketLoggerOverlay.this.textRenderer, cancelX, y, 52, 16, "Cancel", CompactOverlayButton.Variant.GHOST, true, mx, my
                     );
                  }

                  y = this.controlsY();
                  int knownOnX = x + 88 + 4;
                  int captureX = knownOnX + 62 + 4;
                  int filterX = captureX + 56 + 4;
                  int applyX = filterX + 62 + 4;
                  int revertX = applyX + 46 + 4;
                  int allOffX = revertX + 50 + 4;
                  CompactOverlayControls.action(
                     ctx,
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     x,
                     y,
                     88,
                     16,
                     "Recommended",
                     RiptidePacketLoggerOverlay.this.payloadListeners.isDefaultRecommendedProfileActive()
                        ? CompactOverlayButton.Variant.GHOST
                        : CompactOverlayButton.Variant.SUCCESS,
                     true,
                     mx,
                     my
                  );
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, knownOnX, y, 62, 16, "Known On", CompactOverlayButton.Variant.SUCCESS, true, mx, my
                  );
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, captureX, y, 56, 16, "Capture", CompactOverlayButton.Variant.SUCCESS, true, mx, my
                  );
                  CompactOverlayControls.action(
                     ctx,
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     filterX,
                     y,
                     62,
                     16,
                     this.enabledOnly ? "Enabled" : "All",
                     this.enabledOnly ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST,
                     true,
                     mx,
                     my
                  );
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, applyX, y, 46, 16, "Apply", CompactOverlayButton.Variant.SUCCESS, true, mx, my
                  );
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, revertX, y, 50, 16, "Revert", CompactOverlayButton.Variant.GHOST, true, mx, my
                  );
                  CompactOverlayControls.action(
                     ctx, RiptidePacketLoggerOverlay.this.textRenderer, allOffX, y, 50, 16, "All Off", CompactOverlayButton.Variant.GHOST, true, mx, my
                  );
                  RiptidePayloadChannelSubscriptionManager.Status subscriptionStatus = RiptidePayloadChannelSubscriptionManager.status();
                  String sub = subscriptionStatus == null ? "Registered 0/96" : subscriptionStatus.shortLabel();
                  if (subscriptionStatus != null
                     && !subscriptionStatus.locked()
                     && subscriptionStatus.registeredCount() > 0
                     && !subscriptionStatus.lastSendSucceeded()) {
                     sub = sub + " pending";
                  }

                  boolean registrationUnlocked = RiptidePayloadChannelSubscriptionManager.isRegistrationUnlocked();
                  int lockX = this.panelX + this.panelWidth - 8 - 122;
                  CompactOverlayControls.action(
                     ctx,
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     lockX,
                     this.registrationButtonY(),
                     122,
                     16,
                     registrationUnlocked ? "Registration : On" : "Registration : Off",
                     registrationUnlocked ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.GHOST,
                     true,
                     mx,
                     my
                  );
                  String count = this.enabledHighlightCountCache + " highlight  " + sub + this.pendingRegistrationLabelCache;
                  int countMaxW = Math.max(1, lockX - x - 5);
                  RiptidePacketLoggerOverlay.this.drawUiText(
                     ctx,
                     UiText.trimToWidth(
                        RiptidePacketLoggerOverlay.this.textRenderer,
                        count,
                        countMaxW,
                        RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED),
                        RiptideColors.textSecondary()
                     ),
                     UiTone.MUTED,
                     RiptideColors.textSecondary(),
                     x,
                     this.statusY() + 3
                  );
                  int listTop = this.listTopY();
                  int listH = this.listenerListHeight();
                  int contentH = this.rows.size() * 16;
                  int maxScroll = Math.max(0, contentH - listH);
                  this.rowScroll = this.quantizeScrollOffset(this.rowScroll, 16, maxScroll);
                  this.rowsScroll.setTarget(this.rowScroll, maxScroll);
                  int drawScroll = this.rowsScroll.tick(delta, maxScroll);
                  UiRenderer.frame(
                     ctx,
                     UiBounds.of(this.listFrameLeft(), listTop - 2, Math.max(1, this.listFrameRight() - this.listFrameLeft()), listH + 4),
                     RiptideColors.listBg(),
                     RiptideColors.subPanelBorder()
                  );
                  UiScissorStack.global().push(ctx, UiBounds.of(this.listLeft(), listTop, this.listWidth(), listH));
                  int base = listTop - drawScroll;
                  int firstVisible = Math.max(0, drawScroll / 16);
                  int lastVisible = Math.min(this.rows.size() - 1, (drawScroll + listH - 1) / 16);

                  for (int i = firstVisible; i <= lastVisible; i++) {
                     int ry = base + i * 16;
                     this.renderListenerRow(ctx, this.rows.get(i), ry, mx, my, i);
                  }

                  UiScissorStack.global().pop(ctx);
                  CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, listH, this.panelX + this.panelWidth - 7, listTop, 3, listH, drawScroll);
                  CompactScrollbar.draw(ctx, metrics, metrics.contains(mx, my), this.draggingScroll);
                  if (this.registrationWarningOpen) {
                     this.renderRegistrationWarning(ctx, mx, my);
                  }
               } finally {
                  this.endWindowBodyClip(ctx, clip);
                  this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, false);
               }
            }
         }
      }

      private void renderRegistrationWarning(GuiGraphicsExtractor ctx, int mx, int my) {
         int bodyTop = this.panelY + 16;
         ctx.fill(this.panelX + 4, bodyTop + 2, this.panelX + this.panelWidth - 4, this.panelY + this.panelHeight - 5, -872086265);
         RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay.RegistrationWarningBounds bounds = this.registrationWarningBounds();
         int x = bounds.x;
         int y = bounds.y;
         int w = bounds.w;
         int h = bounds.h;
         UiRenderer.popup(
            ctx,
            UiBounds.of(x, y, w, h),
            -15594225,
            RiptideTheme.recolor(-42149, RiptideTheme.Channel.OUTLINE),
            RiptideTheme.recolor(-42149, RiptideTheme.Channel.OUTLINE)
         );
         this.drawCenteredUiText(ctx, "Payload Registration Warning", UiTone.LABEL, RiptideTheme.recolor(-19276, RiptideTheme.Channel.DANGER), x, w, y + 10);
         this.drawCenteredUiText(ctx, "Servers can see every registered channel.", UiTone.BODY, RiptideColors.textPrimary(), x, w, y + 30);
         this.drawCenteredUiText(ctx, "This can identify this client or selected plugins.", UiTone.BODY, RiptideColors.textPrimary(), x, w, y + 43);
         this.drawCenteredUiText(ctx, "Passive logging and decoding still work while locked.", UiTone.MUTED, RiptideColors.textSecondary(), x, w, y + 60);
         int cancelW = 72;
         int acceptW = 92;
         int buttonY = bounds.buttonY;
         int buttonsW = cancelW + 6 + acceptW;
         int cancelX = x + (w - buttonsW) / 2;
         int acceptX = cancelX + cancelW + 6;
         CompactOverlayControls.action(
            ctx, RiptidePacketLoggerOverlay.this.textRenderer, cancelX, buttonY, cancelW, 16, "Cancel", CompactOverlayButton.Variant.GHOST, true, mx, my
         );
         int remaining = this.registrationWarningRemainingSeconds();
         boolean ready = remaining <= 0;
         CompactOverlayControls.action(
            ctx,
            RiptidePacketLoggerOverlay.this.textRenderer,
            acceptX,
            buttonY,
            acceptW,
            16,
            ready ? "Accept" : "Accept " + remaining + "s",
            CompactOverlayButton.Variant.DANGER,
            ready,
            mx,
            my
         );
      }

      private int registrationWarningRemainingSeconds() {
         long elapsed = Math.max(0L, System.currentTimeMillis() - this.registrationWarningOpenedAtMs);
         long remaining = Math.max(0L, 5000L - elapsed);
         return (int)Math.ceil(remaining / 1000.0);
      }

      private boolean handleRegistrationWarningClick(double mx, double my, int button) {
         if (button != 0) {
            return true;
         } else {
            RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay.RegistrationWarningBounds bounds = this.registrationWarningBounds();
            int cancelW = 72;
            int acceptW = 92;
            int buttonsW = cancelW + 6 + acceptW;
            int cancelX = bounds.x + (bounds.w - buttonsW) / 2;
            int acceptX = cancelX + cancelW + 6;
            int buttonY = bounds.buttonY;
            if (my >= buttonY && my < buttonY + 16 && mx >= cancelX && mx < cancelX + cancelW) {
               this.registrationWarningOpen = false;
               return true;
            } else if (!(my >= buttonY) || !(my < buttonY + 16) || !(mx >= acceptX) || !(mx < acceptX + acceptW)) {
               return true;
            } else if (this.registrationWarningRemainingSeconds() > 0) {
               return true;
            } else {
               this.registrationWarningOpen = false;
               RiptidePayloadChannelSubscriptionManager.unlockRegistration();
               this.rebuildRows();
               RiptideNotifications.copied("Payload registration unlocked. Press Apply.");
               return true;
            }
         }
      }

      private RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay.RegistrationWarningBounds registrationWarningBounds() {
         int bodyTop = this.panelY + 16;
         int contentW = 0;
         contentW = Math.max(
            contentW, this.warningTextWidth("Payload Registration Warning", UiTone.LABEL, RiptideTheme.recolor(-19276, RiptideTheme.Channel.DANGER))
         );
         contentW = Math.max(contentW, this.warningTextWidth("Servers can see every registered channel.", UiTone.BODY, RiptideColors.textPrimary()));
         contentW = Math.max(contentW, this.warningTextWidth("This can identify this client or selected plugins.", UiTone.BODY, RiptideColors.textPrimary()));
         contentW = Math.max(
            contentW, this.warningTextWidth("Passive logging and decoding still work while locked.", UiTone.MUTED, RiptideColors.textSecondary())
         );
         int buttonsW = 170;
         int maxW = Math.max(180, this.panelWidth - 24);
         int w = Math.min(maxW, Math.max(buttonsW + 28, contentW + 28));
         int h = 101;
         int x = this.panelX + (this.panelWidth - w) / 2;
         int y = bodyTop + Math.max(8, (this.panelHeight - 16 - h) / 2);
         return new RiptidePacketLoggerOverlay.PayloadChannelListenerOverlay.RegistrationWarningBounds(x, y, w, h, y + h - 10 - 16);
      }

      private int warningTextWidth(String text, UiTone tone, int color) {
         return UiText.width(RiptidePacketLoggerOverlay.this.textRenderer, text, RiptidePacketLoggerOverlay.this.theme.fontFor(tone), color);
      }

      private void drawCenteredUiText(GuiGraphicsExtractor ctx, String text, UiTone tone, int color, int x, int w, int y) {
         String fitted = UiText.trimToWidth(
            RiptidePacketLoggerOverlay.this.textRenderer, text, Math.max(1, w - 28), RiptidePacketLoggerOverlay.this.theme.fontFor(tone), color
         );
         int textW = UiText.width(RiptidePacketLoggerOverlay.this.textRenderer, fitted, RiptidePacketLoggerOverlay.this.theme.fontFor(tone), color);
         RiptidePacketLoggerOverlay.this.drawUiText(ctx, fitted, tone, color, x + Math.max(0, (w - textW) / 2), y);
      }

      private void openRegistrationWarning() {
         this.registrationWarningOpen = true;
         this.registrationWarningOpenedAtMs = System.currentTimeMillis();
      }

      private void disablePayloadRegistration() {
         this.savePendingPayloadChannelConfig();
         RiptidePayloadChannelSubscriptionManager.lockRegistrationAndUnregister(RiptidePacketLoggerOverlay.MC);
         this.rebuildRows();
         RiptideNotifications.warning("Payload registration locked.");
      }

      private boolean ensurePayloadRegistrationUnlockedForActiveUse() {
         if (RiptidePayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
            return true;
         } else {
            this.openRegistrationWarning();
            return false;
         }
      }

      private void renderListenerRow(GuiGraphicsExtractor ctx, RiptidePacketLoggerOverlay.ListenerRow row, int y, int mx, int my, int index) {
         int left = this.listLeft();
         int right = this.listRight();
         int width = Math.max(1, right - left);
         int x = left + 4;
         if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.HEADER) {
            boolean groupHeader = this.groupPresetCountCache.getOrDefault(this.cleanPresetGroup(row.header), 0) > 0;
            boolean groupOn = groupHeader && this.isPresetGroupFullyEnabled(row.header);
            ctx.fill(left, y, right, y + 16, RiptideColors.sectionHeaderBg());
            CompactSurfaces.divider(ctx, left + 2, y + 16 - 1, Math.max(1, width - 4));
            int statusW = groupHeader ? 30 : 0;
            int statusX = right - statusW - 4;
            int channelX = groupHeader ? statusX - 58 - 3 : right;
            int studyX = groupHeader ? channelX - 36 - 3 : right;
            int maxHeaderW = groupHeader ? Math.max(1, studyX - x - 6) : Math.max(1, width - 10);
            String header = UiText.trimToWidth(
               RiptidePacketLoggerOverlay.this.textRenderer,
               row.header,
               maxHeaderW,
               RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED),
               RiptideColors.textSecondary()
            );
            RiptidePacketLoggerOverlay.this.drawUiText(ctx, header, UiTone.MUTED, RiptideColors.textSecondary(), x, y + 3);
            if (groupHeader) {
               this.drawStudyPill(ctx, studyX, y, mx, my, "Study");
               this.drawGroupChannelImpact(ctx, row.header, channelX, y);
               this.drawStatusPill(ctx, statusX, y + 2, statusW, 12, groupOn ? "ON" : "OFF", groupOn);
            }
         } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.EMPTY) {
            ctx.fill(left, y, right, y + 16, RiptideColors.rowNormal());
            RiptidePacketLoggerOverlay.this.drawUiText(
               ctx,
               UiText.trimToWidth(
                  RiptidePacketLoggerOverlay.this.textRenderer,
                  row.header == null ? "" : row.header,
                  Math.max(1, width - 12),
                  RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED),
                  RiptideColors.textDim()
               ),
               UiTone.MUTED,
               RiptideColors.textDim(),
               x + 4,
               this.rowTextY(y, UiTone.MUTED)
            );
         } else {
            boolean hover = mx >= left && mx < right && my >= y && my < y + 16;
            if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.FAMILY) {
               RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family = row.family;
               if (family != null) {
                  int total = family.exactChannelCount();
                  int enabled = this.familyEnabledExactCount(family);
                  boolean allEnabled = total > 0 && enabled == total;
                  int fill = enabled > 0 ? RiptideColors.packetRowSelectedBg(hover) : (hover ? RiptideColors.rowHover() : RiptideColors.rowNormal());
                  ctx.fill(left, y, right, y + 16, fill);
                  if (enabled > 0) {
                     ctx.fill(left, y, left + 2, y + 16, RiptideColors.packetRowSelectedAccent());
                  }

                  int statusX = right - 30 - 4;
                  int channelX = statusX - 58 - 3;
                  int studyX = channelX - 36 - 3;
                  boolean familyExpanded = this.expandedPayloadFamilies.contains(family.stableKey());
                  String text = family.label + "  " + enabled + "/" + total;
                  int textColor = enabled > 0 ? RiptideColors.packetRowSelectedText() : RiptideColors.textPrimary();
                  UiRenderer.chevron(ctx, UiBounds.of(x + 4, y + 3, 9, 9), familyExpanded, textColor);
                  RiptidePacketLoggerOverlay.this.drawUiText(
                     ctx,
                     UiText.trimToWidth(
                        RiptidePacketLoggerOverlay.this.textRenderer,
                        text,
                        Math.max(1, studyX - (x + 15) - 5),
                        RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY),
                        textColor
                     ),
                     UiTone.BODY,
                     textColor,
                     x + 15,
                     this.rowTextY(y, UiTone.BODY)
                  );
                  this.drawStudyPill(ctx, studyX, y, mx, my, "Study");
                  this.drawImpactText(ctx, total + "ch", total > 0 ? -7346000 : RiptideColors.textDim(), channelX, y);
                  this.drawStatusPill(ctx, statusX, y + 2, 30, 12, allEnabled ? "ON" : "OFF", allEnabled);
               }
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.REGISTRATION) {
               RiptideConfig.PayloadChannelRegistrationRule rule = row.registration;
               boolean enabled = rule != null && rule.enabled;
               int fill = enabled ? RiptideColors.packetRowSelectedBg(hover) : (hover ? RiptideColors.rowHover() : RiptideColors.rowNormal());
               ctx.fill(left, y, right, y + 16, fill);
               if (enabled) {
                  ctx.fill(left, y, left + 2, y + 16, RiptideColors.packetRowSelectedAccent());
               }

               String label = rule != null && rule.label != null && !rule.label.isBlank() ? rule.label.trim() : "";
               String channel = rule == null ? "" : RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel);
               String text = !label.isBlank() && !label.equals(channel) ? label + "  " + channel : channel;
               boolean editable = this.isEditableRegistration(rule);
               int removeX = editable ? right - 14 : right;
               int editX = editable ? removeX - 14 : right;
               int statusRight = editable ? editX - 3 : right - 4;
               int statusX = statusRight - 30;
               int sourceW = 52;
               int sourceX = statusX - sourceW - 3;
               int maxTextW = Math.max(1, sourceX - (x + 4) - 5);
               int color = enabled ? RiptideColors.packetRowSelectedText() : RiptideColors.textPrimary();
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx,
                  UiText.trimToWidth(
                     RiptidePacketLoggerOverlay.this.textRenderer, text, maxTextW, RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY), color
                  ),
                  UiTone.BODY,
                  color,
                  x + 4,
                  this.rowTextY(y, UiTone.BODY)
               );
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx,
                  UiText.trimToWidth(
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     RiptidePayloadChannelRegistrations.sourceLabel(rule == null ? "" : rule.source),
                     sourceW,
                     RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED),
                     RiptideColors.textSecondary()
                  ),
                  UiTone.MUTED,
                  RiptideColors.textSecondary(),
                  sourceX,
                  this.rowTextY(y, UiTone.MUTED)
               );
               this.drawStatusPill(ctx, statusX, y + 2, 30, 12, enabled ? "ON" : "OFF", enabled);
               if (editable) {
                  boolean eu = mx >= editX && mx <= editX + 14 && my >= y && my < y + 16;
                  boolean hu = mx >= removeX && mx <= removeX + 14 && my >= y && my < y + 16;
                  RiptidePacketLoggerOverlay.this.drawUiText(
                     ctx, "E", UiTone.MUTED, eu ? -7346000 : RiptideColors.textSecondary(), editX + 3, this.rowTextY(y, UiTone.MUTED)
                  );
                  UiRenderer.cross(
                     ctx, UiBounds.of(removeX + 3, y + 3, 9, 9), hu ? RiptideTheme.recolor(-39322, RiptideTheme.Channel.DANGER) : RiptideColors.textSecondary()
                  );
               }
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.LEARNED) {
               ctx.fill(left, y, right, y + 16, hover ? RiptideColors.rowHover() : RiptideColors.rowNormal());
               int statusX = right - 30 - 4;
               int sourceX = statusX - 56;
               int maxTextW = Math.max(1, sourceX - (x + 4) - 5);
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx,
                  UiText.trimToWidth(
                     RiptidePacketLoggerOverlay.this.textRenderer,
                     row.learnedChannel == null ? "" : row.learnedChannel,
                     maxTextW,
                     RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY),
                     RiptideColors.textPrimary()
                  ),
                  UiTone.BODY,
                  RiptideColors.textPrimary(),
                  x + 4,
                  this.rowTextY(y, UiTone.BODY)
               );
               RiptidePacketLoggerOverlay.this.drawUiText(ctx, "Learned", UiTone.MUTED, RiptideColors.textSecondary(), sourceX, this.rowTextY(y, UiTone.MUTED));
               this.drawStatusPill(ctx, statusX, y + 2, 30, 12, "OFF", false);
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.ACTIVE) {
               RiptideConfig.PayloadChannelFilterRule rulex = row.rule;
               int fillx = rulex.enabled ? RiptideColors.packetRowSelectedBg(hover) : (hover ? RiptideColors.rowHover() : RiptideColors.rowNormal());
               ctx.fill(left, y, right, y + 16, fillx);
               if (rulex.enabled) {
                  ctx.fill(left, y, left + 2, y + 16, RiptideColors.packetRowSelectedAccent());
               }

               String label = rulex.label != null && !rulex.label.isBlank() ? rulex.label : rulex.pattern;
               String text = label + "  " + rulex.pattern;
               int color = rulex.enabled ? RiptideColors.packetRowSelectedText() : RiptideColors.textSecondary();
               int removeX = right - 14;
               int editX = removeX - 14;
               int statusRight = editX - 3;
               int statusW = 30;
               int statusX = statusRight - statusW;
               int channelX = statusX - 58 - 3;
               int studyX = channelX - 36 - 3;
               int maxTextW = Math.max(1, studyX - (x + 4) - 4);
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx,
                  UiText.trimToWidth(
                     RiptidePacketLoggerOverlay.this.textRenderer, text, maxTextW, RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY), color
                  ),
                  UiTone.BODY,
                  color,
                  x + 4,
                  this.rowTextY(y, UiTone.BODY)
               );
               this.drawStudyPill(ctx, studyX, y, mx, my, "Study");
               this.drawChannelImpact(ctx, rulex.pattern, channelX, y);
               this.drawStatusPill(ctx, statusX, y + 2, statusW, 12, rulex.enabled ? "ON" : "OFF", rulex.enabled);
               boolean eu = mx >= editX && mx <= editX + 14 && my >= y && my < y + 16;
               boolean hu = mx >= removeX && mx <= removeX + 14 && my >= y && my < y + 16;
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx, "E", UiTone.MUTED, eu ? -7346000 : RiptideColors.textSecondary(), editX + 3, this.rowTextY(y, UiTone.MUTED)
               );
               UiRenderer.cross(
                  ctx, UiBounds.of(removeX + 3, y + 3, 9, 9), hu ? RiptideTheme.recolor(-39322, RiptideTheme.Channel.DANGER) : RiptideColors.textSecondary()
               );
            } else {
               RiptidePayloadChannelListeners.Preset preset = row.preset;
               boolean enabledx = this.isPresetEnabled(preset);
               int fillx = enabledx ? RiptideColors.packetRowSelectedBg(hover) : (hover ? RiptideColors.rowHover() : RiptideColors.rowNormal());
               ctx.fill(left, y, right, y + 16, fillx);
               if (enabledx) {
                  ctx.fill(left, y, left + 2, y + 16, RiptideColors.packetRowSelectedAccent());
               }

               String status = enabledx ? "ON" : "OFF";
               int statusW = 30;
               int statusX = right - statusW - 4;
               String text = preset.label() + "  " + preset.pattern();
               int textColor = enabledx ? RiptideColors.packetRowSelectedText() : RiptideColors.textPrimary();
               int channelX = statusX - 58 - 3;
               int studyX = channelX - 36 - 3;
               int maxTextW = Math.max(1, studyX - (x + 4) - 5);
               RiptidePacketLoggerOverlay.this.drawUiText(
                  ctx,
                  UiText.trimToWidth(
                     RiptidePacketLoggerOverlay.this.textRenderer, text, maxTextW, RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.BODY), textColor
                  ),
                  UiTone.BODY,
                  textColor,
                  x + 4,
                  this.rowTextY(y, UiTone.BODY)
               );
               this.drawStudyPill(ctx, studyX, y, mx, my, this.presetActionLabel(preset));
               this.drawChannelImpact(ctx, preset, channelX, y);
               this.drawStatusPill(ctx, statusX, y + 2, statusW, 12, status, enabledx);
            }
         }
      }

      private String presetActionLabel(RiptidePayloadChannelListeners.Preset preset) {
         return preset != null && preset.kind().templateLike() ? "Use" : "Study";
      }

      private void drawStudyPill(GuiGraphicsExtractor ctx, int x, int y, int mx, int my, String label) {
         int h = 12;
         int py = y + (16 - h) / 2;
         boolean disabled = RiptidePacketLoggerOverlay.this.configurationOnly && !"Use".equals(label);
         boolean hover = !disabled && mx >= x && mx < x + 36 && my >= py && my < py + h;
         if (disabled) {
            Chip.renderDisabled(UiContexts.overlay(ctx, RiptidePacketLoggerOverlay.this.textRenderer, mx, my), UiBounds.of(x, py, 36, h), label);
         } else {
            Chip.render(UiContexts.overlay(ctx, RiptidePacketLoggerOverlay.this.textRenderer, mx, my), UiBounds.of(x, py, 36, h), label, false, hover);
         }
      }

      private boolean studyHit(double mx, double my, int rowY) {
         int right = this.listRight();
         int statusX = right - 30 - 4;
         int channelX = statusX - 58 - 3;
         int studyX = channelX - 36 - 3;
         int studyY = rowY + 2;
         return mx >= studyX && mx < studyX + 36 && my >= studyY && my < studyY + 12;
      }

      private boolean familyExpandHit(double mx, double my, int rowY) {
         int x = this.listLeft() + 4;
         return mx >= x && mx < x + 16 && my >= rowY && my < rowY + 16;
      }

      private void drawChannelImpact(GuiGraphicsExtractor ctx, String pattern, int x, int y) {
         RiptidePayloadChannelSubscriptionManager.RegistrationImpact impact = this.impactForPatternCached(pattern);
         boolean core = impact.exactChannelCount() <= 0 && this.isCorePayloadPattern(pattern);
         String label = impact.exactChannelCount() > 0 ? impact.compactLabel() : (core ? "Core" : "Highlight");
         int color = impact.exactChannelCount() <= 0 && !core ? RiptideColors.textDim() : -7346000;
         this.drawImpactText(ctx, label, color, x, y);
      }

      private void drawChannelImpact(GuiGraphicsExtractor ctx, RiptidePayloadChannelListeners.Preset preset, int x, int y) {
         if (preset == null) {
            this.drawChannelImpact(ctx, "", x, y);
         } else {
            RiptidePayloadChannelSubscriptionManager.RegistrationImpact impact = this.impactForPatternCached(preset.pattern());
            String label;
            int color;
            if (preset.kind() == RiptidePayloadChannelListeners.PresetKind.EXACT) {
               label = impact.exactChannelCount() > 0 ? impact.compactLabel() : "Exact";
               color = -7346000;
            } else if (preset.kind() == RiptidePayloadChannelListeners.PresetKind.CORE) {
               label = preset.kind().badge();
               color = -7346000;
            } else if (preset.kind() == RiptidePayloadChannelListeners.PresetKind.UNVERIFIED) {
               label = preset.kind().badge();
               color = RiptideColors.packetYellow();
            } else {
               label = preset.kind().badge();
               color = RiptideColors.textDim();
            }

            this.drawImpactText(ctx, label, color, x, y);
         }
      }

      private void drawImpactText(GuiGraphicsExtractor ctx, String label, int color, int x, int y) {
         label = UiText.trimToWidth(
            RiptidePacketLoggerOverlay.this.textRenderer, label, Math.max(1, 56), RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED), color
         );
         RiptidePacketLoggerOverlay.this.drawUiText(
            ctx,
            label,
            UiTone.MUTED,
            color,
            x
               + Math.max(
                     0,
                     58 - UiText.width(RiptidePacketLoggerOverlay.this.textRenderer, label, RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED), color)
                  )
                  / 2,
            this.rowTextY(y, UiTone.MUTED)
         );
      }

      private boolean isCorePayloadPattern(String pattern) {
         String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);

         return switch (normalized) {
            case "minecraft:register", "minecraft:unregister", "register", "unregister", "bungeecord" -> true;
            default -> false;
         };
      }

      private void drawGroupChannelImpact(GuiGraphicsExtractor ctx, String group, int x, int y) {
         int count = this.groupExactChannelCount(group);
         String label = count > 0 ? count + "ch" : "Tpl";
         int color = count > 0 ? -7346000 : RiptideColors.textDim();
         RiptidePacketLoggerOverlay.this.drawUiText(
            ctx,
            label,
            UiTone.MUTED,
            color,
            x
               + Math.max(
                     0,
                     58 - UiText.width(RiptidePacketLoggerOverlay.this.textRenderer, label, RiptidePacketLoggerOverlay.this.theme.fontFor(UiTone.MUTED), color)
                  )
                  / 2,
            this.rowTextY(y, UiTone.MUTED)
         );
      }

      private int groupExactChannelCount(String group) {
         return group == null ? 0 : this.groupExactCountCache.getOrDefault(this.cleanPresetGroup(group), 0);
      }

      private boolean isPresetEnabled(RiptidePayloadChannelListeners.Preset preset) {
         if (preset == null) {
            return false;
         } else {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            return this.enabledPresetKeysCache.contains(preset.key())
               || RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern) && this.enabledRegistrationChannelsCache.contains(pattern);
         }
      }

      private int familyEnabledExactCount(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family) {
         if (family == null) {
            return 0;
         } else {
            int enabled = 0;

            for (String channel : family.exactChannels()) {
               if (this.enabledRegistrationChannelsCache.contains(channel)) {
                  enabled++;
               }
            }

            return enabled;
         }
      }

      private boolean isFamilyFullyEnabled(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family) {
         return family != null && family.exactChannelCount() > 0 && this.familyEnabledExactCount(family) == family.exactChannelCount();
      }

      private boolean isPresetGroupFullyEnabled(String group) {
         if (group != null && !group.isBlank()) {
            String cleanGroup = this.cleanPresetGroup(group);
            int total = this.groupPresetCountCache.getOrDefault(cleanGroup, 0);
            if (total <= 0) {
               return false;
            } else {
               int enabled = 0;
               RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category = this.buildPayloadChannelCategories().get(cleanGroup);
               if (category == null) {
                  return false;
               } else {
                  for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.families.values()) {
                     for (RiptidePayloadChannelListeners.Preset preset : family.presets) {
                        if (this.isPresetEnabled(preset)) {
                           enabled++;
                        }
                     }
                  }

                  for (RiptidePayloadChannelListeners.Preset presetx : category.standalonePresets) {
                     if (this.isPresetEnabled(presetx)) {
                        enabled++;
                     }
                  }

                  return enabled == total;
               }
            }
         } else {
            return false;
         }
      }

      private void drawStatusPill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean enabled) {
         Chip.render(UiContexts.overlay(ctx, RiptidePacketLoggerOverlay.this.textRenderer, -1, -1), UiBounds.of(x, y, w, h), label, enabled, false);
      }

      private int rowTextY(int y, UiTone tone) {
         return UiSizing.alignTextY(y, 16, RiptidePacketLoggerOverlay.this.theme.fontHeight(tone), RiptidePacketLoggerOverlay.this.theme.bodyTextNudge());
      }

      private void refreshPayloadChannelUiCaches() {
         this.enabledRegistrationChannelsCache.clear();

         for (RiptideConfig.PayloadChannelRegistrationRule rule : RiptidePacketLoggerOverlay.this.payloadRegistrations.rules()) {
            if (rule != null && rule.enabled) {
               String channel = RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel);
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(channel)) {
                  this.enabledRegistrationChannelsCache.add(channel);
               }
            }
         }

         if (this.allPresetKeysCache.isEmpty()) {
            for (RiptidePayloadChannelListeners.Preset preset : RiptidePacketLoggerOverlay.this.payloadListeners.presets()) {
               if (preset != null) {
                  this.allPresetKeysCache.add(preset.key());
               }
            }
         }

         this.enabledPresetKeysCache.clear();
         this.enabledHighlightCountCache = 0;

         for (RiptideConfig.PayloadChannelFilterRule rulex : RiptidePacketLoggerOverlay.this.payloadListeners.rules()) {
            if (rulex != null) {
               if (rulex.enabled) {
                  this.enabledHighlightCountCache++;
               }

               if (rulex.enabled) {
                  this.enabledPresetKeysCache.add(RiptidePayloadChannelListeners.normalizePattern(rulex.pattern));
               }
            }
         }

         if (this.groupPresetCountCache.isEmpty() && this.groupExactCountCache.isEmpty()) {
            this.buildPayloadChannelGroupCaches();
         }

         this.cachedRegistrationLimit = this.payloadRegistrationLimitFromStatus();
         this.pendingRegistrationLabelCache = this.computePendingRegistrationLabel();
      }

      private void buildPayloadChannelGroupCaches() {
         for (RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category : this.buildPayloadChannelCategories().values()) {
            int presetCount = category.standalonePresets.size();
            LinkedHashSet<String> exact = new LinkedHashSet<>();

            for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.families.values()) {
               presetCount += family.presets.size();
               exact.addAll(family.exactChannels());
            }

            for (RiptidePayloadChannelListeners.Preset preset : category.standalonePresets) {
               if (preset != null) {
                  String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
                  if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                     exact.add(pattern);
                  }
               }
            }

            this.groupPresetCountCache.put(category.group, presetCount);
            this.groupExactCountCache.put(category.group, exact.size());
         }
      }

      private void rebuildRows() {
         this.refreshPayloadChannelUiCaches();
         String query = this.search.getText() == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
         this.rows.clear();
         boolean registrationUnlocked = RiptidePayloadChannelSubscriptionManager.isRegistrationUnlocked();
         String registeredHeader = registrationUnlocked ? "Registered Channels" : "Saved Channels (Locked)";
         List<RiptideConfig.PayloadChannelRegistrationRule> registrations = RiptidePacketLoggerOverlay.this.payloadRegistrations.rules();
         Map<String, List<RiptidePacketLoggerOverlay.ListenerRow>> registeredGroups = new LinkedHashMap<>();

         for (int i = 0; i < registrations.size(); i++) {
            RiptideConfig.PayloadChannelRegistrationRule rule = registrations.get(i);
            if (rule != null && rule.enabled && this.registrationMatchesSearch(rule, query)) {
               String group = this.registeredGroupTitle(rule);
               registeredGroups.computeIfAbsent(group, unused -> new ArrayList<>()).add(RiptidePacketLoggerOverlay.ListenerRow.registration(i, rule));
            }
         }

         int registeredCount = registeredGroups.values().stream().mapToInt(List::size).sum();
         if (!registeredGroups.isEmpty()) {
            this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header(registeredHeader));

            for (Entry<String, List<RiptidePacketLoggerOverlay.ListenerRow>> group : this.sortedRegisteredGroups(registeredGroups)) {
               this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header(group.getKey()));
               group.getValue().sort(this::compareRegistrationRows);
               this.rows.addAll(group.getValue());
            }
         } else if (query.isEmpty()) {
            this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header(registeredHeader));
            this.rows
               .add(
                  RiptidePacketLoggerOverlay.ListenerRow.empty(
                     registrationUnlocked
                        ? "No channels registered. Enable a preset or add an exact channel."
                        : "No channels saved. Enable a preset or add an exact channel."
                  )
               );
         } else if (registeredCount == 0) {
            this.rows
               .add(RiptidePacketLoggerOverlay.ListenerRow.empty(registrationUnlocked ? "No matching registered channels." : "No matching saved channels."));
         }

         Set<String> knownRegistrationChannels = new LinkedHashSet<>();

         for (RiptideConfig.PayloadChannelRegistrationRule rule : registrations) {
            if (rule != null) {
               knownRegistrationChannels.add(RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel));
            }
         }

         boolean customHeaderAdded = false;
         List<RiptideConfig.PayloadChannelFilterRule> active = RiptidePacketLoggerOverlay.this.payloadListeners.rules();

         for (int ix = 0; ix < active.size(); ix++) {
            RiptideConfig.PayloadChannelFilterRule rulex = active.get(ix);
            if (rulex != null
               && !rulex.preset
               && !this.allPresetKeysCache.contains(RiptidePayloadChannelListeners.normalizePattern(rulex.pattern))
               && (!this.enabledOnly || rulex.enabled)) {
               String normalizedPattern = RiptidePayloadChannelListeners.normalizePattern(rulex.pattern);
               if (!RiptidePayloadChannelRegistrations.isRegisterableChannel(normalizedPattern) || !knownRegistrationChannels.contains(normalizedPattern)) {
                  String hay = ((rulex.label == null ? "" : rulex.label) + " " + rulex.pattern).toLowerCase(Locale.ROOT);
                  if (query.isEmpty() || hay.contains(query)) {
                     if (!customHeaderAdded) {
                        this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header("Custom Highlights"));
                        customHeaderAdded = true;
                     }

                     this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.active(ix, rulex));
                  }
               }
            }
         }

         boolean availableHeader = false;
         if (!this.enabledOnly) {
            for (String learned : RiptidePayloadChannelSubscriptionManager.learnedChannels()) {
               if (learned != null
                  && !learned.isBlank()
                  && !knownRegistrationChannels.contains(learned)
                  && (query.isEmpty() || learned.toLowerCase(Locale.ROOT).contains(query))) {
                  if (!availableHeader) {
                     this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header("Learned Exact Channels"));
                     availableHeader = true;
                  }

                  this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.learned(learned));
               }
            }
         }

         for (int ixx = 0; ixx < registrations.size(); ixx++) {
            RiptideConfig.PayloadChannelRegistrationRule rulex = registrations.get(ixx);
            if (rulex != null && !rulex.enabled && !this.enabledOnly && !"preset".equals(rulex.source) && this.registrationMatchesSearch(rulex, query)) {
               if (!availableHeader) {
                  this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header("Available Exact Channels"));
                  availableHeader = true;
               }

               this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.registration(ixx, rulex));
            }
         }

         for (RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category : this.sortedPayloadCategories()) {
            List<RiptidePacketLoggerOverlay.PayloadChannelFamilyRow> visibleFamilies = new ArrayList<>();

            for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.sortedFamilies) {
               if (this.familyMatchesSearch(family, query) && (!this.enabledOnly || this.familyEnabledExactCount(family) > 0)) {
                  visibleFamilies.add(family);
               }
            }

            List<RiptidePayloadChannelListeners.Preset> standalone = new ArrayList<>();

            for (RiptidePayloadChannelListeners.Preset preset : category.sortedStandalonePresets) {
               if (this.presetMatchesSearch(preset, query) && (!this.enabledOnly || this.isPresetEnabled(preset))) {
                  standalone.add(preset);
               }
            }

            if (!visibleFamilies.isEmpty() || !standalone.isEmpty()) {
               this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.header(category.group));

               for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow familyx : visibleFamilies) {
                  this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.family(familyx));
                  if (this.isFamilyExpandedForRender(familyx, query)) {
                     for (RiptidePayloadChannelListeners.Preset presetx : familyx.sortedPresets()) {
                        if (this.presetVisibleInExpandedFamily(familyx, presetx, query) && (!this.enabledOnly || this.isPresetEnabled(presetx))) {
                           this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.preset(presetx));
                        }
                     }
                  }
               }

               for (RiptidePayloadChannelListeners.Preset presetxx : standalone) {
                  this.rows.add(RiptidePacketLoggerOverlay.ListenerRow.preset(presetxx));
               }
            }
         }

         if (this.rows.isEmpty()) {
            this.rows
               .add(
                  RiptidePacketLoggerOverlay.ListenerRow.empty(this.enabledOnly ? "No enabled payload channels or highlights." : "No payload channels match.")
               );
         }

         this.clampRowScroll();
      }

      private Map<String, RiptidePacketLoggerOverlay.PayloadChannelCategoryRow> buildPayloadChannelCategories() {
         if (this.payloadCategoryCache != null) {
            return this.payloadCategoryCache;
         } else {
            Map<String, RiptidePacketLoggerOverlay.PayloadChannelCategoryRow> categories = new LinkedHashMap<>();
            this.presetByExactChannelCache.clear();
            this.presetSearchTextCache.clear();

            for (RiptidePayloadChannelListeners.Preset preset : RiptidePacketLoggerOverlay.this.payloadListeners.presets()) {
               if (preset != null) {
                  this.presetSearchTextCache.put(preset, (preset.group() + " " + preset.label() + " " + preset.pattern()).toLowerCase(Locale.ROOT));
                  if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
                     String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
                     if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                        RiptidePayloadChannelListeners.Preset existing = this.presetByExactChannelCache.get(pattern);
                        if (existing == null || this.presetKindPriority(preset) < this.presetKindPriority(existing)) {
                           this.presetByExactChannelCache.put(pattern, preset);
                        }
                     }
                  }

                  String group = this.cleanPresetGroup(preset.group());
                  RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category = categories.computeIfAbsent(
                     group, RiptidePacketLoggerOverlay.PayloadChannelCategoryRow::new
                  );
                  RiptidePacketLoggerOverlay.PayloadChannelFamilyKey familyKey = this.familyKeyForPreset(preset);
                  if (familyKey != null) {
                     category.family(familyKey).presets.add(preset);
                  } else {
                     category.standalonePresets.add(preset);
                  }
               }
            }

            this.payloadCategoryCache = categories;
            this.sortedPayloadCategoryCache = new ArrayList<>(categories.values());
            this.sortedPayloadCategoryCache.sort((a, b) -> {
               int priority = Integer.compare(this.groupPriority(a.group), this.groupPriority(b.group));
               return priority != 0 ? priority : String.CASE_INSENSITIVE_ORDER.compare(a.group, b.group);
            });

            for (RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category : this.sortedPayloadCategoryCache) {
               category.sortedFamilies = new ArrayList<>(category.families.values());
               category.sortedFamilies.sort(this::compareFamilies);
               category.sortedStandalonePresets = new ArrayList<>(category.standalonePresets);
               this.sortPresetRows(category.sortedStandalonePresets);

               for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.sortedFamilies) {
                  family.sortedPresets = new ArrayList<>(family.presets);
                  this.sortPresetRows(family.sortedPresets);
               }
            }

            return this.payloadCategoryCache;
         }
      }

      private List<RiptidePacketLoggerOverlay.PayloadChannelCategoryRow> sortedPayloadCategories() {
         this.buildPayloadChannelCategories();
         return this.sortedPayloadCategoryCache == null ? List.of() : this.sortedPayloadCategoryCache;
      }

      private String cleanPresetGroup(String group) {
         return group != null && !group.isBlank() ? group.trim() : "Other";
      }

      private RiptidePacketLoggerOverlay.PayloadChannelFamilyKey familyKeyForPreset(RiptidePayloadChannelListeners.Preset preset) {
         if (preset != null && RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            if (!RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
               return null;
            } else {
               String family = this.derivePayloadFamily(pattern);
               return family.isBlank() ? null : new RiptidePacketLoggerOverlay.PayloadChannelFamilyKey(this.cleanPresetGroup(preset.group()), family, family);
            }
         } else {
            return null;
         }
      }

      private String derivePayloadFamily(String channel) {
         String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
         int colon = normalized.indexOf(58);
         if (colon <= 0) {
            return "";
         } else {
            String namespace = normalized.substring(0, colon);
            String path = normalized.substring(colon + 1);
            if (namespace.equals("plasmo") && path.startsWith("voice/v2/")) {
               return "plasmo:voice/v2/*";
            } else if (namespace.equals("ftbchunks")) {
               return "ftbchunks:*";
            } else {
               return namespace.equals("ftbteams") ? "ftbteams:*" : namespace + ":*";
            }
         }
      }

      private boolean familyMatchesSearch(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family, String query) {
         if (family == null) {
            return false;
         } else if (query != null && !query.isBlank()) {
            String needle = query.toLowerCase(Locale.ROOT);
            if ((family.group + " " + family.label).toLowerCase(Locale.ROOT).contains(needle)) {
               return true;
            } else {
               for (RiptidePayloadChannelListeners.Preset preset : family.presets) {
                  if (this.presetMatchesSearch(preset, needle)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return true;
         }
      }

      private boolean presetVisibleInExpandedFamily(
         RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family, RiptidePayloadChannelListeners.Preset preset, String query
      ) {
         if (query != null && !query.isBlank()) {
            String needle = query.toLowerCase(Locale.ROOT);
            return family != null && (family.group + " " + family.label).toLowerCase(Locale.ROOT).contains(needle)
               ? true
               : this.presetMatchesSearch(preset, needle);
         } else {
            return true;
         }
      }

      private boolean isFamilyExpandedForRender(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family, String query) {
         return family != null && (this.expandedPayloadFamilies.contains(family.stableKey()) || query != null && !query.isBlank());
      }

      private int compareFamilies(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow a, RiptidePacketLoggerOverlay.PayloadChannelFamilyRow b) {
         int exact = Integer.compare(b.exactChannelCount(), a.exactChannelCount());
         return exact != 0 ? exact : String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
      }

      private List<Entry<String, List<RiptidePacketLoggerOverlay.ListenerRow>>> sortedRegisteredGroups(
         Map<String, List<RiptidePacketLoggerOverlay.ListenerRow>> groups
      ) {
         List<Entry<String, List<RiptidePacketLoggerOverlay.ListenerRow>>> out = new ArrayList<>(groups.entrySet());
         out.sort((a, b) -> {
            int priority = Integer.compare(this.registeredGroupPriority(a.getKey()), this.registeredGroupPriority(b.getKey()));
            return priority != 0 ? priority : String.CASE_INSENSITIVE_ORDER.compare(a.getKey(), b.getKey());
         });
         return out;
      }

      private int compareRegistrationRows(RiptidePacketLoggerOverlay.ListenerRow a, RiptidePacketLoggerOverlay.ListenerRow b) {
         String ac = a != null && a.registration != null ? RiptidePayloadChannelRegistrations.normalizeChannel(a.registration.channel) : "";
         String bc = b != null && b.registration != null ? RiptidePayloadChannelRegistrations.normalizeChannel(b.registration.channel) : "";
         return String.CASE_INSENSITIVE_ORDER.compare(ac, bc);
      }

      private String registeredGroupTitle(RiptideConfig.PayloadChannelRegistrationRule rule) {
         if (rule == null) {
            return "Registered: Other";
         } else {
            String source = rule.source == null ? "" : rule.source.trim().toLowerCase(Locale.ROOT);
            if ("custom".equals(source)) {
               return "Registered: Custom";
            } else if ("learned".equals(source)) {
               return "Registered: Learned";
            } else {
               RiptidePayloadChannelListeners.Preset preset = this.presetForRegisteredChannel(rule.channel);
               if (preset != null && preset.group() != null && !preset.group().isBlank()) {
                  RiptidePacketLoggerOverlay.PayloadChannelFamilyKey family = this.familyKeyForPreset(preset);
                  return family != null ? "Registered: " + preset.group().trim() + " - " + family.label() : "Registered: " + preset.group().trim();
               } else {
                  return "Registered: Other";
               }
            }
         }
      }

      private int registeredGroupPriority(String group) {
         String clean = group == null ? "" : group.trim();
         if (clean.startsWith("Registered: ")) {
            clean = clean.substring("Registered: ".length()).trim();
         }

         int familySep = clean.indexOf(" - ");
         if (familySep > 0) {
            clean = clean.substring(0, familySep).trim();
         }

         if ("Custom".equalsIgnoreCase(clean)) {
            return 100;
         } else if ("Learned".equalsIgnoreCase(clean)) {
            return 101;
         } else {
            return "Other".equalsIgnoreCase(clean) ? 102 : this.groupPriority(clean);
         }
      }

      private RiptidePayloadChannelListeners.Preset presetForRegisteredChannel(String channel) {
         String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
         if (normalized.isBlank()) {
            return null;
         } else {
            this.buildPayloadChannelCategories();
            return this.presetByExactChannelCache.get(normalized);
         }
      }

      private boolean presetMatchesSearch(RiptidePayloadChannelListeners.Preset preset, String query) {
         if (preset == null) {
            return false;
         } else if (query != null && !query.isBlank()) {
            String hay = this.presetSearchTextCache.get(preset);
            if (hay == null) {
               hay = (preset.group() + " " + preset.label() + " " + preset.pattern()).toLowerCase(Locale.ROOT);
               this.presetSearchTextCache.put(preset, hay);
            }

            return hay.contains(query);
         } else {
            return true;
         }
      }

      private void sortPresetRows(List<RiptidePayloadChannelListeners.Preset> presets) {
         presets.sort((a, b) -> {
            int kind = Integer.compare(this.presetKindPriority(a), this.presetKindPriority(b));
            if (kind != 0) {
               return kind;
            } else {
               int exact = Integer.compare(this.presetExactCount(b), this.presetExactCount(a));
               if (exact != 0) {
                  return exact;
               } else {
                  boolean aDefault = RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(a);
                  boolean bDefault = RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(b);
                  if (aDefault != bDefault) {
                     return aDefault ? -1 : 1;
                  } else {
                     int labelCompare = String.CASE_INSENSITIVE_ORDER.compare(a.label(), b.label());
                     return labelCompare != 0 ? labelCompare : String.CASE_INSENSITIVE_ORDER.compare(a.pattern(), b.pattern());
                  }
               }
            }
         });
      }

      private int presetExactCount(RiptidePayloadChannelListeners.Preset preset) {
         if (preset == null) {
            return 0;
         } else {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            return RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern) ? 1 : 0;
         }
      }

      private RiptidePayloadChannelSubscriptionManager.RegistrationImpact impactForPatternCached(String pattern) {
         String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);
         return normalized.isBlank()
            ? new RiptidePayloadChannelSubscriptionManager.RegistrationImpact(false, false, 0, List.of())
            : this.impactByPatternCache.computeIfAbsent(normalized, RiptidePayloadChannelSubscriptionManager::impactForPattern);
      }

      private int presetKindPriority(RiptidePayloadChannelListeners.Preset preset) {
         if (preset != null && preset.kind() != null) {
            return switch (preset.kind()) {
               case EXACT -> 0;
               case CORE -> 1;
               case TEMPLATE -> 2;
               case UNVERIFIED -> 3;
            };
         } else {
            return 4;
         }
      }

      private int groupPriority(String group) {
         String var2 = group == null ? "" : group.toLowerCase(Locale.ROOT);

         return switch (var2) {
            case "minecraft" -> 0;
            case "proxy" -> 1;
            case "mod api" -> 2;
            case "voice" -> 3;
            case "protection" -> 4;
            case "shops" -> 5;
            case "auctions" -> 6;
            case "crates" -> 7;
            case "gambling" -> 8;
            case "containers" -> 9;
            case "storage" -> 10;
            case "backpacks" -> 11;
            case "economy" -> 12;
            case "items" -> 13;
            default -> 50;
         };
      }

      private boolean registrationMatchesSearch(RiptideConfig.PayloadChannelRegistrationRule rule, String query) {
         if (query != null && !query.isBlank()) {
            String hay = ((rule.label == null ? "" : rule.label)
                  + " "
                  + (rule.channel == null ? "" : rule.channel)
                  + " "
                  + RiptidePayloadChannelRegistrations.sourceLabel(rule.source))
               .toLowerCase(Locale.ROOT);
            return hay.contains(query);
         } else {
            return true;
         }
      }

      private void mutateRowsKeepingAnchor(int clickedIndex, Runnable mutation) {
         String anchor = this.rowStableKey(clickedIndex);
         mutation.run();
         this.rebuildRows();
         if (anchor != null) {
            int newIndex = this.findRowByStableKey(anchor);
            if (newIndex >= 0) {
               this.rowScroll += (newIndex - clickedIndex) * 16;
            }
         }

         this.clampRowScroll();
         int listH = this.listenerListHeight();
         int maxScroll = Math.max(0, this.rows.size() * 16 - listH);
         this.rowsScroll.jumpTo(this.rowScroll, maxScroll);
      }

      private String rowStableKey(int index) {
         if (index >= 0 && index < this.rows.size()) {
            RiptidePacketLoggerOverlay.ListenerRow row = this.rows.get(index);
            if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.ACTIVE && row.rule != null) {
               return "custom:" + RiptidePayloadChannelListeners.normalizePattern(row.rule.pattern);
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.REGISTRATION && row.registration != null) {
               return "reg:" + RiptidePayloadChannelRegistrations.normalizeChannel(row.registration.channel);
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.LEARNED && row.learnedChannel != null) {
               return "learned:" + RiptidePayloadChannelRegistrations.normalizeChannel(row.learnedChannel);
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.PRESET && row.preset != null) {
               return "preset:" + row.preset.key();
            } else if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.FAMILY && row.family != null) {
               return "family:" + row.family.stableKey();
            } else {
               return row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.HEADER && row.header != null ? "header:" + row.header : null;
            }
         } else {
            return null;
         }
      }

      private int findRowByStableKey(String key) {
         if (key == null) {
            return -1;
         } else {
            for (int i = 0; i < this.rows.size(); i++) {
               if (key.equals(this.rowStableKey(i))) {
                  return i;
               }
            }

            return -1;
         }
      }

      private void clampRowScroll() {
         int listH = this.listenerListHeight();
         int maxScroll = Math.max(0, this.rows.size() * 16 - listH);
         this.rowScroll = this.quantizeScrollOffset(this.rowScroll, 16, maxScroll);
      }

      @Override
      public boolean usesSharedHeaderClickCollapse() {
         return true;
      }

      @Override
      public boolean mouseClicked(double mx, double my, int button) {
         if (!this.visible) {
            return false;
         } else {
            RiptideWindowLayout bounds = this.getBounds();
            if (button == 0 && this.isOverCloseButton(mx, my, bounds)) {
               this.setVisible(false);
               return true;
            } else if (this.registrationWarningOpen) {
               return this.handleRegistrationWarningClick(mx, my, button);
            } else if (button == 0 && this.isOverDragBar(mx, my)) {
               this.draggingWindow = true;
               this.dragOffsetX = mx - this.panelX;
               this.dragOffsetY = my - this.panelY;
               return true;
            } else if (this.collapsed) {
               return true;
            } else if (this.search.mouseClicked(mx, my, button)) {
               return true;
            } else if (this.customPattern.mouseClicked(mx, my, button)) {
               return true;
            } else {
               int y = this.customY();
               int customW = this.customPatternWidth();
               int addX = this.panelX + 6 + customW + 4;
               if (button == 0 && my >= y && my < y + 16) {
                  if (mx >= addX && mx < addX + 52) {
                     this.saveCustomRuleField();
                     return true;
                  }

                  if (this.isEditingCustomEntry()) {
                     int cancelX = addX + 52 + 4;
                     if (mx >= cancelX && mx < cancelX + 52) {
                        this.clearCustomEditState();
                        return true;
                     }
                  }
               }

               y = this.controlsY();
               int defaultsX = this.panelX + 6;
               int knownOnX = defaultsX + 88 + 4;
               int captureX = knownOnX + 62 + 4;
               int filterX = captureX + 56 + 4;
               int applyX = filterX + 62 + 4;
               int revertX = applyX + 46 + 4;
               int allOffX = revertX + 50 + 4;
               if (button == 0 && my >= y && my < y + 16 && mx >= defaultsX && mx < defaultsX + 88) {
                  RiptidePacketLoggerOverlay.this.payloadListeners.applyDefaultRecommendedOnlyInMemory();
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.applyRecommendedOnlyInMemory();
                  this.markPayloadChannelConfigDirty();
                  this.rebuildRows();
                  RiptidePacketLoggerOverlay.this.dirty = true;
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= knownOnX && mx < knownOnX + 62) {
                  this.enableAllKnownExactChannels();
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= captureX && mx < captureX + 56) {
                  this.captureLearnedChannels(false);
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= filterX && mx < filterX + 62) {
                  this.enabledOnly = !this.enabledOnly;
                  this.rebuildRows();
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= applyX && mx < applyX + 46) {
                  this.applyPayloadChannelRegistration();
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= revertX && mx < revertX + 50) {
                  this.revertPayloadChannelRegistration();
                  return true;
               } else if (button == 0 && my >= y && my < y + 16 && mx >= allOffX && mx < allOffX + 50) {
                  RiptidePacketLoggerOverlay.this.payloadListeners.disableAllInMemory();
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.disableAllInMemory();
                  this.markPayloadChannelConfigDirty();
                  this.rebuildRows();
                  RiptidePacketLoggerOverlay.this.dirty = true;
                  return true;
               } else {
                  int lockX = this.panelX + this.panelWidth - 8 - 122;
                  int lockY = this.registrationButtonY();
                  if (button == 0 && my >= lockY && my < lockY + 16 && mx >= lockX && mx < lockX + 122) {
                     if (RiptidePayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
                        this.disablePayloadRegistration();
                     } else {
                        this.openRegistrationWarning();
                     }

                     return true;
                  } else {
                     int listTop = this.listTopY();
                     int listH = this.listenerListHeight();
                     CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
                        this.rows.size() * 16, listH, this.panelX + this.panelWidth - 7, listTop, 3, listH, this.rowScroll
                     );
                     if (button == 0 && metrics.hasScroll() && metrics.contains((int)mx, (int)my)) {
                        this.draggingScroll = true;
                        this.scrollGrabOffset = (int)my - metrics.thumbY();
                        return true;
                     } else {
                        if (button == 0 && my >= listTop && my < listTop + listH && mx >= this.listLeft() && mx < this.listRight()) {
                           int index = (int)((my - listTop + this.rowScroll) / 16.0);
                           if (index >= 0 && index < this.rows.size()) {
                              RiptidePacketLoggerOverlay.ListenerRow row = this.rows.get(index);
                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.REGISTRATION) {
                                 boolean editable = this.isEditableRegistration(row.registration);
                                 int removeX = editable ? this.listRight() - 14 : this.listRight();
                                 int editX = editable ? removeX - 14 : this.listRight();
                                 if (editable && mx >= removeX && mx <= removeX + 14) {
                                    this.mutateRowsKeepingAnchor(index, () -> {
                                       this.removeRegistrationAndLinkedHighlight(row.registrationIndex, row.registration);
                                       this.markPayloadChannelConfigDirty();
                                    });
                                 } else if (editable && mx >= editX && mx <= editX + 14) {
                                    this.beginEditRegistration(row.registrationIndex, row.registration);
                                 } else {
                                    this.mutateRowsKeepingAnchor(index, () -> {
                                       RiptidePacketLoggerOverlay.this.payloadRegistrations.toggleInMemory(row.registrationIndex);
                                       this.markPayloadChannelConfigDirty();
                                    });
                                 }

                                 RiptidePacketLoggerOverlay.this.dirty = true;
                                 return true;
                              }

                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.LEARNED) {
                                 if (!this.canEnablePatterns(List.of(row.learnedChannel))) {
                                    this.showPayloadChannelCapToast();
                                    return true;
                                 }

                                 this.mutateRowsKeepingAnchor(index, () -> {
                                    RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(row.learnedChannel, row.learnedChannel, "learned");
                                    this.markPayloadChannelConfigDirty();
                                 });
                                 RiptidePacketLoggerOverlay.this.dirty = true;
                                 return true;
                              }

                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.ACTIVE) {
                                 if (this.studyHit(mx, my, listTop + index * 16 - this.rowScroll)) {
                                    this.startStudyCustom(row.ruleIndex, row.rule);
                                    return true;
                                 }

                                 int ux = this.listRight() - 14;
                                 int editX = ux - 14;
                                 if (mx >= ux && mx <= ux + 14) {
                                    this.mutateRowsKeepingAnchor(index, () -> this.removeCustomWatch(row.ruleIndex, row.rule));
                                 } else if (mx >= editX && mx <= editX + 14) {
                                    this.beginEditFilter(row.ruleIndex, row.rule);
                                 } else {
                                    this.mutateRowsKeepingAnchor(index, () -> this.toggleCustomWatch(row.ruleIndex, row.rule));
                                 }

                                 RiptidePacketLoggerOverlay.this.dirty = true;
                                 return true;
                              }

                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.FAMILY) {
                                 int rowY = listTop + index * 16 - this.rowScroll;
                                 if (this.studyHit(mx, my, rowY)) {
                                    this.startStudyFamily(row.family);
                                    return true;
                                 }

                                 if (this.familyExpandHit(mx, my, rowY)) {
                                    if (row.family != null) {
                                       String key = row.family.stableKey();
                                       if (!this.expandedPayloadFamilies.remove(key)) {
                                          this.expandedPayloadFamilies.add(key);
                                       }

                                       this.rebuildRows();
                                    }

                                    return true;
                                 }

                                 this.mutateRowsKeepingAnchor(index, () -> this.togglePresetFamily(row.family));
                                 RiptidePacketLoggerOverlay.this.dirty = true;
                                 return true;
                              }

                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.PRESET) {
                                 if (!this.studyHit(mx, my, listTop + index * 16 - this.rowScroll)) {
                                    if (row.preset != null
                                       && row.preset.kind() == RiptidePayloadChannelListeners.PresetKind.EXACT
                                       && !this.isPresetEnabled(row.preset)
                                       && !this.canEnablePatterns(List.of(row.preset.pattern()))) {
                                       this.showPayloadChannelCapToast();
                                       return true;
                                    }

                                    this.mutateRowsKeepingAnchor(index, () -> this.togglePreset(row.preset));
                                    RiptidePacketLoggerOverlay.this.dirty = true;
                                    return true;
                                 }

                                 if (row.preset != null && row.preset.kind().templateLike()) {
                                    this.beginUsePresetTemplate(row.preset);
                                 } else {
                                    this.startStudyPreset(row.preset);
                                 }

                                 return true;
                              }

                              if (row.kind == RiptidePacketLoggerOverlay.ListenerRowKind.HEADER
                                 && this.groupPresetCountCache.getOrDefault(this.cleanPresetGroup(row.header), 0) > 0) {
                                 if (this.studyHit(mx, my, listTop + index * 16 - this.rowScroll)) {
                                    this.startStudyGroup(row.header);
                                    return true;
                                 }

                                 this.mutateRowsKeepingAnchor(index, () -> this.togglePresetGroup(row.header));
                                 RiptidePacketLoggerOverlay.this.dirty = true;
                                 return true;
                              }
                           }
                        }

                        return this.isMouseOver(mx, my);
                     }
                  }
               }
            }
         }
      }

      private boolean isEditableRegistration(RiptideConfig.PayloadChannelRegistrationRule rule) {
         if (rule == null) {
            return false;
         } else {
            String source = rule.source == null ? "" : rule.source.trim().toLowerCase(Locale.ROOT);
            return "custom".equals(source) || "learned".equals(source);
         }
      }

      private void beginEditRegistration(int index, RiptideConfig.PayloadChannelRegistrationRule rule) {
         if (!this.isEditableRegistration(rule)) {
            RiptideNotifications.warning("Presets can be toggled, not edited.");
         } else {
            this.editingRegistrationIndex = index;
            this.editingRuleIndex = -1;
            this.customPattern.setText(RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel));
            this.customPattern.setPlaceholder(Component.literal("edit exact channel"));
            this.customPattern.setFocused(true);
         }
      }

      private void beginEditFilter(int index, RiptideConfig.PayloadChannelFilterRule rule) {
         if (rule != null && !rule.preset) {
            this.editingRuleIndex = index;
            this.editingRegistrationIndex = -1;
            this.customPattern.setText(RiptidePayloadChannelListeners.normalizePattern(rule.pattern));
            this.customPattern.setPlaceholder(Component.literal("edit highlight pattern"));
            this.customPattern.setFocused(true);
         }
      }

      private void saveCustomRuleField() {
         String pattern = this.customPattern.getText() == null ? "" : this.customPattern.getText().trim();
         if (pattern.isBlank()) {
            RiptideNotifications.warning("Enter a payload channel first.");
         } else if (!this.isEditingCustomEntry() && !this.canEnablePatterns(List.of(pattern))) {
            this.showPayloadChannelCapToast();
         } else {
            String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);
            if (this.editingRegistrationIndex >= 0) {
               this.saveEditedRegistration(normalized);
            } else if (this.editingRuleIndex >= 0) {
               this.saveEditedFilter(normalized);
            } else {
               boolean filterChanged = RiptidePacketLoggerOverlay.this.payloadListeners
                  .addCustomInMemory(normalized, normalized, RiptidePayloadChannelListeners.Direction.ANY);
               boolean registrationChanged = false;
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                  registrationChanged = RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(normalized, normalized, "custom");
               }

               if (!filterChanged && !registrationChanged) {
                  RiptideNotifications.warning("Payload channel already exists.");
               } else {
                  this.customPattern.setText("");
                  this.markPayloadChannelConfigDirty();
                  this.rebuildRows();
                  this.rowScroll = 0;
                  this.rowsScroll.jumpTo(0, Math.max(0, this.rows.size() * 16 - this.listenerListHeight()));
                  RiptidePacketLoggerOverlay.this.dirty = true;
                  if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                     RiptideNotifications.copied("Payload channel added. Press Apply to register.");
                  } else {
                     RiptideNotifications.copied("Payload highlight added.");
                  }
               }
            }
         }
      }

      private void saveEditedRegistration(String normalized) {
         if (!RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
            RiptideNotifications.warning("Exact registered channels need namespace:path.");
         } else {
            List<RiptideConfig.PayloadChannelRegistrationRule> regs = RiptidePacketLoggerOverlay.this.payloadRegistrations.mutableRules();
            if (this.editingRegistrationIndex >= 0 && this.editingRegistrationIndex < regs.size()) {
               int existing = RiptidePacketLoggerOverlay.this.payloadRegistrations.indexOf(normalized);
               String oldChannel = "";
               RiptideConfig.PayloadChannelRegistrationRule currentRule = this.editingRegistrationIndex >= 0 && this.editingRegistrationIndex < regs.size()
                  ? regs.get(this.editingRegistrationIndex)
                  : null;
               if (currentRule != null) {
                  oldChannel = RiptidePayloadChannelRegistrations.normalizeChannel(currentRule.channel);
               }

               if (existing >= 0 && existing != this.editingRegistrationIndex) {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.removeInMemory(this.editingRegistrationIndex);
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(normalized, normalized, "custom");
               } else {
                  RiptideConfig.PayloadChannelRegistrationRule rule = regs.get(this.editingRegistrationIndex);
                  if (rule != null && this.isEditableRegistration(rule)) {
                     rule.channel = normalized;
                     rule.label = normalized;
                     rule.source = "custom";
                     rule.enabled = true;
                  }
               }

               if (!oldChannel.isBlank() && !oldChannel.equals(normalized)) {
                  this.removeCustomHighlightByPattern(oldChannel);
               }

               RiptidePacketLoggerOverlay.this.payloadListeners.addCustomInMemory(normalized, normalized, RiptidePayloadChannelListeners.Direction.ANY);
               this.clearCustomEditState();
               this.markPayloadChannelConfigDirty();
               this.rebuildRows();
               RiptideNotifications.copied("Payload channel edited. Press Apply to register.");
            } else {
               this.clearCustomEditState();
               this.rebuildRows();
            }
         }
      }

      private void saveEditedFilter(String normalized) {
         List<RiptideConfig.PayloadChannelFilterRule> filters = RiptidePacketLoggerOverlay.this.payloadListeners.mutableRules();
         if (this.editingRuleIndex >= 0 && this.editingRuleIndex < filters.size()) {
            RiptideConfig.PayloadChannelFilterRule rule = filters.get(this.editingRuleIndex);
            if (rule != null && !rule.preset) {
               String oldPattern = RiptidePayloadChannelListeners.normalizePattern(rule.pattern);
               rule.pattern = normalized;
               rule.label = normalized;
               rule.enabled = true;
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(oldPattern)) {
                  this.removeCustomRegistration(oldPattern);
               }

               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(normalized, normalized, "custom");
               }
            }

            this.clearCustomEditState();
            this.markPayloadChannelConfigDirty();
            this.rebuildRows();
            RiptideNotifications.copied("Payload highlight edited.");
         } else {
            this.clearCustomEditState();
            this.rebuildRows();
         }
      }

      private void togglePreset(RiptidePayloadChannelListeners.Preset preset) {
         if (preset != null) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            boolean enabled = this.isPresetEnabled(preset);
            if (!enabled) {
               RiptidePacketLoggerOverlay.this.payloadListeners.addOrEnablePresetInMemory(preset);
               if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, "preset");
               }

               this.markPayloadChannelConfigDirty();
            } else {
               if (RiptidePacketLoggerOverlay.this.payloadListeners.isRuleEnabledFor(preset)) {
                  RiptidePacketLoggerOverlay.this.payloadListeners.toggleOrAddPresetInMemory(preset);
               }

               if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
                  this.removePresetRegistration(pattern);
               }

               this.markPayloadChannelConfigDirty();
            }
         }
      }

      private void toggleCustomWatch(int ruleIndex, RiptideConfig.PayloadChannelFilterRule rule) {
         if (rule != null) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(rule.pattern);
            boolean enabling = !rule.enabled;
            RiptidePacketLoggerOverlay.this.payloadListeners.toggleInMemory(ruleIndex);
            if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
               if (enabling) {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(rule.label, pattern, "custom");
               } else {
                  this.removeCustomRegistration(pattern);
               }
            }

            this.markPayloadChannelConfigDirty();
         }
      }

      private void removeCustomWatch(int ruleIndex, RiptideConfig.PayloadChannelFilterRule rule) {
         String pattern = rule == null ? "" : RiptidePayloadChannelListeners.normalizePattern(rule.pattern);
         RiptidePacketLoggerOverlay.this.payloadListeners.removeInMemory(ruleIndex);
         if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
            this.removeCustomRegistration(pattern);
         }

         this.markPayloadChannelConfigDirty();
      }

      private void removeRegistrationAndLinkedHighlight(int registrationIndex, RiptideConfig.PayloadChannelRegistrationRule rule) {
         String channel = rule == null ? "" : RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel);
         RiptidePacketLoggerOverlay.this.payloadRegistrations.removeInMemory(registrationIndex);
         this.removeCustomHighlightByPattern(channel);
      }

      private void removeCustomHighlightByPattern(String pattern) {
         String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);
         if (!normalized.isBlank()) {
            List<RiptideConfig.PayloadChannelFilterRule> filters = RiptidePacketLoggerOverlay.this.payloadListeners.mutableRules();

            for (int i = filters.size() - 1; i >= 0; i--) {
               RiptideConfig.PayloadChannelFilterRule rule = filters.get(i);
               if (rule != null && !rule.preset && normalized.equals(RiptidePayloadChannelListeners.normalizePattern(rule.pattern))) {
                  filters.remove(i);
               }
            }
         }
      }

      private void beginUsePresetTemplate(RiptidePayloadChannelListeners.Preset preset) {
         if (preset != null) {
            this.editingRuleIndex = -1;
            this.editingRegistrationIndex = -1;
            String prefix = RiptidePayloadChannelListeners.templatePrefix(preset);
            this.customPattern.setText(prefix);
            this.customPattern.setPlaceholder(Component.literal("finish exact channel from " + preset.label()));
            this.customPattern.setFocused(true);
            RiptideNotifications.copied("Finish the exact payload channel, then Add.");
         }
      }

      private void startStudyPreset(RiptidePayloadChannelListeners.Preset preset) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               if (preset != null) {
                  this.startStudy("Preset: " + preset.label(), List.of(preset), List.of(preset.pattern()));
               }
            }
         }
      }

      private void startStudyGroup(String group) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               if (group != null && !group.isBlank()) {
                  List<RiptidePayloadChannelListeners.Preset> presets = new ArrayList<>();
                  List<String> patterns = new ArrayList<>();
                  RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category = this.buildPayloadChannelCategories().get(this.cleanPresetGroup(group));
                  if (category != null) {
                     for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.sortedFamilies) {
                        for (RiptidePayloadChannelListeners.Preset preset : family.sortedPresets()) {
                           presets.add(preset);
                           patterns.add(preset.pattern());
                        }
                     }

                     for (RiptidePayloadChannelListeners.Preset preset : category.sortedStandalonePresets) {
                        presets.add(preset);
                        patterns.add(preset.pattern());
                     }

                     this.startStudy("Category: " + group, presets, patterns);
                  }
               }
            }
         }
      }

      private void startStudyFamily(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               if (family != null && !family.presets.isEmpty()) {
                  List<String> patterns = new ArrayList<>();

                  for (RiptidePayloadChannelListeners.Preset preset : family.presets) {
                     patterns.add(preset.pattern());
                  }

                  this.startStudy("Family: " + family.label, new ArrayList<>(family.presets), patterns);
               }
            }
         }
      }

      private void startStudyCustom(int ruleIndex, RiptideConfig.PayloadChannelFilterRule rule) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               if (rule != null) {
                  if (!rule.enabled) {
                     RiptidePacketLoggerOverlay.this.payloadListeners.toggleInMemory(ruleIndex);
                     this.markPayloadChannelConfigDirty();
                  }

                  String pattern = RiptidePayloadChannelListeners.normalizePattern(rule.pattern);
                  String label = rule.label != null && !rule.label.isBlank() ? rule.label.trim() : pattern;
                  int skipped = this.enableExactChannelsForPattern(label, pattern, "custom");
                  this.markPayloadChannelConfigDirty();
                  this.rebuildRows();
                  this.beginPayloadStudy(label, List.of(pattern), skipped);
               }
            }
         }
      }

      private void startStudy(String label, List<RiptidePayloadChannelListeners.Preset> presets, List<String> patterns) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               if (presets != null && !presets.isEmpty()) {
                  int skipped = 0;

                  for (RiptidePayloadChannelListeners.Preset preset : presets) {
                     if (preset != null) {
                        RiptidePacketLoggerOverlay.this.payloadListeners.addOrEnablePresetInMemory(preset);
                        skipped += this.enableExactChannelsForPattern(preset.label(), preset.pattern(), "preset");
                     }
                  }

                  this.markPayloadChannelConfigDirty();
                  this.rebuildRows();
                  this.beginPayloadStudy(label, patterns, skipped);
               }
            }
         }
      }

      private int enableExactChannelsForPattern(String label, String pattern, String source) {
         RiptidePayloadChannelSubscriptionManager.RegistrationImpact impact = this.impactForPatternCached(pattern);
         int skipped = 0;

         for (String channel : impact.exactChannels()) {
            String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
            if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
               if (!RiptidePacketLoggerOverlay.this.payloadRegistrations.hasEnabled(normalized) && !this.canEnablePatterns(List.of(normalized))) {
                  skipped++;
               } else {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(label, normalized, source);
               }
            }
         }

         return skipped;
      }

      private void beginPayloadStudy(String label, List<String> patterns, int skipped) {
         if (!RiptidePacketLoggerOverlay.this.configurationOnly) {
            if (this.ensurePayloadRegistrationUnlockedForActiveUse()) {
               this.savePendingPayloadChannelConfig();
               RiptidePayloadChannelSubscriptionManager.requestRefresh();
               RiptidePayloadChannelSubscriptionManager.tick(RiptidePacketLoggerOverlay.MC, false);
               RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.status();
               if (skipped > 0 || status != null && status.skippedCount() > 0) {
                  int count = status == null ? 0 : status.registeredCount();
                  int limit = status == null ? 96 : status.limit();
                  RiptideNotifications.error("Payload channel cap: registered " + count + "/" + limit + ".");
               }

               List<String> applied = status == null ? List.of() : status.channels();
               RiptidePayloadStudySession.start(label, patterns == null ? List.of() : patterns, applied);
               this.setVisible(false);
               if (RiptidePacketLoggerOverlay.MC != null) {
                  RiptidePacketLoggerOverlay.MC.gui.setScreen(null);
               }
            }
         }
      }

      private void togglePresetGroup(String group) {
         if (group != null && !group.isBlank()) {
            boolean enable = !this.isPresetGroupFullyEnabled(group);
            boolean capHit = false;
            RiptidePacketLoggerOverlay.PayloadChannelCategoryRow category = this.buildPayloadChannelCategories().get(this.cleanPresetGroup(group));
            if (category != null) {
               List<RiptidePayloadChannelListeners.Preset> presets = new ArrayList<>();

               for (RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family : category.sortedFamilies) {
                  presets.addAll(family.sortedPresets());
               }

               presets.addAll(category.sortedStandalonePresets);

               for (RiptidePayloadChannelListeners.Preset preset : presets) {
                  String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
                  if (enable) {
                     if (RiptidePayloadChannelListeners.isRegisterablePreset(preset) && !this.canEnablePatterns(List.of(pattern))) {
                        capHit = true;
                     } else {
                        RiptidePacketLoggerOverlay.this.payloadListeners.addOrEnablePresetInMemory(preset);
                        if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
                           RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, "preset");
                        }
                     }
                  } else {
                     if (RiptidePacketLoggerOverlay.this.payloadListeners.isRuleEnabledFor(preset)) {
                        RiptidePacketLoggerOverlay.this.payloadListeners.toggleOrAddPresetInMemory(preset);
                     }

                     if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
                        this.removePresetRegistration(pattern);
                     }
                  }
               }

               if (capHit) {
                  this.showPayloadChannelCapToast();
               }

               this.markPayloadChannelConfigDirty();
            }
         }
      }

      private void togglePresetFamily(RiptidePacketLoggerOverlay.PayloadChannelFamilyRow family) {
         if (family != null && !family.presets.isEmpty()) {
            boolean enable = !this.isFamilyFullyEnabled(family);
            boolean capHit = false;
            List<RiptidePayloadChannelListeners.Preset> presets = new ArrayList<>(family.presets);
            this.sortPresetRows(presets);

            for (RiptidePayloadChannelListeners.Preset preset : presets) {
               String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                  if (enable) {
                     if (!RiptidePacketLoggerOverlay.this.payloadRegistrations.hasEnabled(pattern) && !this.canEnablePatterns(List.of(pattern))) {
                        capHit = true;
                     } else {
                        RiptidePacketLoggerOverlay.this.payloadListeners.addOrEnablePresetInMemory(preset);
                        RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, "preset");
                     }
                  } else {
                     if (RiptidePacketLoggerOverlay.this.payloadListeners.isRuleEnabledFor(preset)) {
                        RiptidePacketLoggerOverlay.this.payloadListeners.toggleOrAddPresetInMemory(preset);
                     }

                     this.removePresetRegistration(pattern);
                  }
               }
            }

            if (capHit) {
               this.showPayloadChannelCapToast();
            }

            this.markPayloadChannelConfigDirty();
         }
      }

      private void removePresetRegistration(String channel) {
         int index = RiptidePacketLoggerOverlay.this.payloadRegistrations.indexOf(channel);
         if (index >= 0) {
            List<RiptideConfig.PayloadChannelRegistrationRule> rules = RiptidePacketLoggerOverlay.this.payloadRegistrations.rules();
            if (index < rules.size()) {
               RiptideConfig.PayloadChannelRegistrationRule rule = rules.get(index);
               if (rule != null && "preset".equals(rule.source)) {
                  RiptidePacketLoggerOverlay.this.payloadRegistrations.removeInMemory(index);
               }
            }
         }
      }

      private void removeCustomRegistration(String channel) {
         int index = RiptidePacketLoggerOverlay.this.payloadRegistrations.indexOf(channel);
         if (index >= 0) {
            List<RiptideConfig.PayloadChannelRegistrationRule> rules = RiptidePacketLoggerOverlay.this.payloadRegistrations.rules();
            if (index < rules.size()) {
               RiptideConfig.PayloadChannelRegistrationRule rule = rules.get(index);
               if (rule != null) {
                  String source = rule.source == null ? "" : rule.source;
                  if ("custom".equals(source) || "learned".equals(source)) {
                     RiptidePacketLoggerOverlay.this.payloadRegistrations.removeInMemory(index);
                  }
               }
            }
         }
      }

      private void captureLearnedChannels(boolean replaceExisting) {
         List<String> learned = RiptidePayloadChannelSubscriptionManager.learnedChannels();
         if (learned.isEmpty()) {
            RiptideNotifications.warning("No captured payload channels yet.");
         } else {
            if (replaceExisting) {
               RiptidePacketLoggerOverlay.this.payloadRegistrations.disableAllInMemory();
            }

            int added = 0;
            int skipped = 0;

            for (String channel : learned) {
               String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                  if (!RiptidePacketLoggerOverlay.this.payloadRegistrations.hasEnabled(normalized) && !this.canEnablePatterns(List.of(normalized))) {
                     skipped++;
                  } else {
                     boolean regChanged = RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(normalized, normalized, "learned");
                     RiptidePacketLoggerOverlay.this.payloadListeners.addCustomInMemory(normalized, normalized, RiptidePayloadChannelListeners.Direction.ANY);
                     if (regChanged) {
                        added++;
                     }
                  }
               }
            }

            this.markPayloadChannelConfigDirty();
            this.rebuildRows();
            RiptidePacketLoggerOverlay.this.dirty = true;
            if (skipped > 0) {
               RiptideNotifications.error("Captured " + added + ", skipped " + skipped + " at channel cap.");
            } else {
               RiptideNotifications.copied((replaceExisting ? "Reset to " : "Captured ") + added + " payload channels.");
            }
         }
      }

      private void enableAllKnownExactChannels() {
         int added = 0;
         int skipped = 0;

         for (String channel : RiptidePayloadChannelSubscriptionManager.learnedChannels()) {
            String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
            if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
               if (!RiptidePacketLoggerOverlay.this.payloadRegistrations.hasEnabled(normalized) && !this.canEnablePatterns(List.of(normalized))) {
                  skipped++;
               } else {
                  boolean regChanged = RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(normalized, normalized, "learned");
                  RiptidePacketLoggerOverlay.this.payloadListeners.addCustomInMemory(normalized, normalized, RiptidePayloadChannelListeners.Direction.ANY);
                  if (regChanged) {
                     added++;
                  }
               }
            }
         }

         this.buildPayloadChannelCategories();

         for (RiptidePayloadChannelListeners.Preset preset : this.presetByExactChannelCache.values()) {
            if (preset.kind() == RiptidePayloadChannelListeners.PresetKind.EXACT) {
               RiptidePacketLoggerOverlay.this.payloadListeners.addOrEnablePresetInMemory(preset);
               String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(preset.pattern());
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                  if (!RiptidePacketLoggerOverlay.this.payloadRegistrations.hasEnabled(normalized) && !this.canEnablePatterns(List.of(normalized))) {
                     skipped++;
                  } else {
                     boolean regChanged = RiptidePacketLoggerOverlay.this.payloadRegistrations.addOrEnableInMemory(preset.label(), normalized, "preset");
                     if (regChanged) {
                        added++;
                     }
                  }
               }
            }
         }

         this.markPayloadChannelConfigDirty();
         this.rebuildRows();
         this.rowScroll = 0;
         this.rowsScroll.jumpTo(0, Math.max(0, this.rows.size() * 16 - this.listenerListHeight()));
         RiptidePacketLoggerOverlay.this.dirty = true;
         if (skipped > 0) {
            RiptideNotifications.error("Known channels enabled with " + skipped + " skipped at cap.");
         } else {
            RiptideNotifications.copied("Known exact channels enabled. Press Apply.");
         }
      }

      private boolean canEnablePatterns(Collection<String> patterns) {
         return this.localProjectedChannels(patterns).size() <= this.cachedRegistrationLimit;
      }

      private LinkedHashSet<String> localProjectedChannels(Collection<String> patterns) {
         LinkedHashSet<String> projected = new LinkedHashSet<>();

         for (RiptideConfig.PayloadChannelRegistrationRule rule : RiptidePacketLoggerOverlay.this.payloadRegistrations.rules()) {
            if (rule != null && rule.enabled) {
               String channel = RiptidePayloadChannelRegistrations.normalizeChannel(rule.channel);
               if (RiptidePayloadChannelRegistrations.isRegisterableChannel(channel)) {
                  projected.add(channel);
               }
            }
         }

         if (patterns != null) {
            for (String pattern : patterns) {
               RiptidePayloadChannelSubscriptionManager.RegistrationImpact impact = this.impactForPatternCached(pattern);

               for (String channel : impact.exactChannels()) {
                  String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
                  if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                     projected.add(normalized);
                  }
               }
            }
         }

         return projected;
      }

      private int payloadRegistrationLimit() {
         return this.cachedRegistrationLimit;
      }

      private int payloadRegistrationLimitFromStatus() {
         RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.status();
         return status != null && status.limit() > 0 ? status.limit() : 96;
      }

      private List<String> groupPatterns(String group) {
         if (group != null && !group.isBlank()) {
            List<String> patterns = new ArrayList<>();

            for (RiptidePayloadChannelListeners.Preset preset : RiptidePacketLoggerOverlay.this.payloadListeners.presets()) {
               if (group.equals(preset.group())) {
                  patterns.add(preset.pattern());
               }
            }

            return patterns;
         } else {
            return List.of();
         }
      }

      private void showPayloadChannelCapToast() {
         int limit = this.payloadRegistrationLimit();
         int count = Math.min(this.enabledRegistrationChannelsCache.size(), limit);
         RiptideNotifications.error("Payload channel cap reached: " + count + "/" + limit);
      }

      private String computePendingRegistrationLabel() {
         RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.status();
         Set<String> applied = new LinkedHashSet<>(status == null ? List.of() : status.channels());
         Set<String> wanted = this.enabledRegistrationChannelsCache;
         int added = 0;

         for (String channel : wanted) {
            if (!applied.contains(channel)) {
               added++;
            }
         }

         int removed = 0;

         for (String channelx : applied) {
            if (!wanted.contains(channelx)) {
               removed++;
            }
         }

         return added == 0 && removed == 0 ? "" : "  Pending +" + added + " / -" + removed;
      }

      private void applyPayloadChannelRegistration() {
         this.savePendingPayloadChannelConfig();
         if (!RiptidePayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
            RiptidePayloadChannelSubscriptionManager.requestRefresh();
            RiptidePayloadChannelSubscriptionManager.tick(RiptidePacketLoggerOverlay.MC, false);
            RiptideNotifications.warning("Registration locked. Saved, nothing sent.");
            this.rebuildRows();
         } else {
            RiptidePayloadChannelSubscriptionManager.requestRefresh();
            RiptidePayloadChannelSubscriptionManager.tick(RiptidePacketLoggerOverlay.MC, false);
            RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.status();
            if (status != null && status.skippedCount() > 0) {
               RiptideNotifications.error("Payload channel cap reached: " + status.registeredCount() + "/" + status.limit());
            } else {
               RiptideNotifications.copied("Payload channels applied.");
            }

            this.rebuildRows();
         }
      }

      private void revertPayloadChannelRegistration() {
         RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.status();
         RiptidePacketLoggerOverlay.this.payloadRegistrations.replaceWithApplied(status == null ? List.of() : status.channels());
         RiptidePacketLoggerOverlay.this.payloadRegistrations.load();
         this.rebuildRows();
         RiptideNotifications.copied("Payload channels reverted.");
      }

      @Override
      public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
         if (this.draggingWindow && button == 0) {
            this.setBounds(
               new RiptideWindowLayout(
                  (int)Math.round(mx - this.dragOffsetX),
                  (int)Math.round(my - this.dragOffsetY),
                  this.panelWidth,
                  this.panelHeight,
                  this.visible,
                  this.collapsed
               )
            );
            return true;
         } else if (this.draggingScroll && button == 0) {
            int listTop = this.listTopY();
            int listH = this.listenerListHeight();
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
               this.rows.size() * 16, listH, this.panelX + this.panelWidth - 7, listTop, 3, listH, this.rowScroll
            );
            this.rowScroll = this.quantizeScrollOffset(CompactScrollbar.scrollFromThumb(metrics, (int)my, this.scrollGrabOffset), 16, metrics.maxScroll());
            return true;
         } else {
            return this.search.mouseDragged(mx, my, button, dx, dy) ? true : this.customPattern.mouseDragged(mx, my, button, dx, dy);
         }
      }

      @Override
      public boolean mouseReleased(double mx, double my, int button) {
         if (button == 0 && this.draggingWindow) {
            this.draggingWindow = false;
            this.saveLayout();
            return true;
         } else if (button == 0 && this.draggingScroll) {
            this.draggingScroll = false;
            return true;
         } else {
            return this.search.mouseReleased(mx, my, button) ? true : this.customPattern.mouseReleased(mx, my, button);
         }
      }

      @Override
      public boolean mouseScrolled(double mx, double my, double amount) {
         if (this.visible && !this.collapsed && this.isMouseOver(mx, my)) {
            int listTop = this.listTopY();
            int listH = this.listenerListHeight();
            int maxScroll = Math.max(0, this.rows.size() * 16 - listH);
            this.rowScroll = this.quantizeScrollOffset(this.rowScroll - (int)Math.signum(amount) * 16, 16, maxScroll);
            this.rowsScroll.jumpTo(this.rowScroll, maxScroll);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
         if (!this.visible) {
            return false;
         } else {
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (this.search.isFocused()) {
               return this.search.keyPressed(event);
            } else {
               return this.customPattern.isFocused() ? this.customPattern.keyPressed(event) : false;
            }
         }
      }

      @Override
      public boolean charTyped(char chr, int modifiers) {
         if (!this.visible) {
            return false;
         } else {
            CharacterEvent event = new CharacterEvent(chr);
            if (this.search.isFocused()) {
               return this.search.charTyped(event);
            } else {
               return this.customPattern.isFocused() ? this.customPattern.charTyped(event) : false;
            }
         }
      }

      @Override
      public boolean hasTextFieldFocused() {
         return this.visible && (this.search.isFocused() || this.customPattern.isFocused());
      }

      @Override
      public void clearTextFieldFocus() {
         this.search.setFocused(false);
         this.customPattern.setFocused(false);
      }

      private record RegistrationWarningBounds(int x, int y, int w, int h, int buttonY) {
      }
   }

   static enum RowType {
      ENTRY,
      GROUP;
   }
}
