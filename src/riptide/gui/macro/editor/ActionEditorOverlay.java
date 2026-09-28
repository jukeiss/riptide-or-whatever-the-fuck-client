package riptide.gui.macro.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import riptide.api.custommenu.CustomMenuButton;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.gui.macro.editor.components.MacroCaptureButton;
import riptide.gui.macro.editor.components.MacroCaptureSession;
import riptide.gui.macro.editor.components.MacroTypedListControl;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ToastStack;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectScrollViewport;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideChatField;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptideColors;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideCraftingHelper;
import riptide.util.RiptideDropAction;
import riptide.util.RiptideInstaBreakRenderer;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayBase;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketClick;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePacketSelectorOverlay;
import riptide.util.RiptidePayloadEditorModel;
import riptide.util.RiptidePayloadJsonSupport;
import riptide.util.RiptidePayloadSupport;
import riptide.util.RiptidePayloadTemplate;
import riptide.util.RiptidePlayerProbe;
import riptide.util.RiptidePlayerScanner;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.RiptideWindowLayout;
import riptide.util.custommenu.CustomMenuTracker;
import riptide.util.macro.CaptureListSelector;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.CloseGuiAction;
import riptide.util.macro.CraftAction;
import riptide.util.macro.DelayPacketsAction;
import riptide.util.macro.DropAction;
import riptide.util.macro.InteractEntityAction;
import riptide.util.macro.InventoryAuditAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.LookAtBlockAction;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;
import riptide.util.macro.MacroCaptureOutput;
import riptide.util.macro.MacroCapturePattern;
import riptide.util.macro.MacroCondition;
import riptide.util.macro.MacroDynamicBindings;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.MacroTemplate;
import riptide.util.macro.MacroVariableContext;
import riptide.util.macro.MacroVariablesAction;
import riptide.util.macro.MissingAddonAction;
import riptide.util.macro.OpenContainerAction;
import riptide.util.macro.PacketClickAction;
import riptide.util.macro.PayAction;
import riptide.util.macro.PayloadAction;
import riptide.util.macro.PlaceAction;
import riptide.util.macro.RaceAction;
import riptide.util.macro.RotateAction;
import riptide.util.macro.SelectSlotAction;
import riptide.util.macro.SendPacketAction;
import riptide.util.macro.StoreItemAction;
import riptide.util.macro.SwapSlotsAction;
import riptide.util.macro.ToggleModuleAction;
import riptide.util.macro.UseItemAction;
import riptide.util.macro.WaitForChatAction;
import riptide.util.macro.WaitForCooldownAction;
import riptide.util.macro.WaitForEntityAction;
import riptide.util.macro.WaitForGuiAction;
import riptide.util.macro.WaitForHealthAction;
import riptide.util.macro.WaitForLanStepAction;
import riptide.util.macro.WaitForPacketAction;
import riptide.util.macro.WaitForPositionDeltaAction;
import riptide.util.macro.WaitForSlotChangeAction;
import riptide.util.macro.WaitForSoundAction;
import riptide.util.macro.WaitForTeleportAction;
import riptide.util.macro.WaitForWorldChangeAction;
import riptide.util.macro.WaitGamemodeChangeAction;
import riptide.util.macro.WaitGuiTypeAction;
import riptide.util.macro.WaitPacketMatchAction;
import riptide.util.macro.WaitPosAction;
import riptide.util.macro.WaitsForGui;
import riptide.util.macro.XCarryAction;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiSession;

public class ActionEditorOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int DEFAULT_W = 280;
   private static final int COMPACT_W = 184;
   private static final int MIN_W = 168;
   private static final int MIN_H = 96;
   private static final int COMPACT_MIN_H = 88;
   private static final int PAD = 4;
   private static final int ROW_H = 15;
   private static final int ROW_GAP = 2;
   private static final int FOOTER_H = 32;
   private static final int LABEL_MIN_W = 44;
   private static final int LABEL_MAX_W = 68;
   private static final int FIELD_GAP = 3;
   private static final int CATALOG_LIST_H = 60;
   private static final int CATALOG_ITEM_H = 13;
   private static final int CAPTURE_BTN_W = 52;
   private static final int SEL_LIST_MAX_VIS = 4;
   private static final int SEL_ITEM_H = 15;
   private static final int CONTAINER_LIST_VISIBLE_ROWS = 2;
   private static final int CRAFT_LIST_ROWS = 4;
   private static final int CRAFT_LIST_H = 52;
   private static final int SCROLLBAR_W = 5;
   private static final int WAIT_CHAT_ROW_H = 34;
   private static final int WAIT_CHAT_VISIBLE_ROWS = 4;
   private static final int CAPTURE_CHAT_VISIBLE_ROWS = 3;
   private static final int CAPTURE_CHAT_HISTORY_LIMIT = 6;
   private static final int CAPTURE_SCOREBOARD_VISIBLE_ROWS = 5;
   private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,63}");
   private static final int CAPTURE_SCOREBOARD_ROW_H = 22;
   private static final int CAPTURE_TABLIST_VISIBLE_ROWS = 5;
   private static final int CAPTURE_TABLIST_ROW_H = 14;
   private static final long CAPTURE_TABLIST_SCAN_TTL_MS = 500L;
   private static final int WAIT_CHAT_PATTERN_H = 40;
   private static final double WAIT_ENTITY_NEARBY_LIST_RADIUS = 16.0;
   private static final int EDITOR_HINT_ROW_H = 11;
   private static final int EDITOR_GHOST_MAX_CHARS = 16;
   private static final int PAYLOAD_CONTENT_H = 74;
   private static final int PAYLOAD_JSON_H = 74;
   private static final int PAYLOAD_TEXT_H = 30;
   private static final int PAYLOAD_RAW_H = 42;
   private static final int PAYLOAD_JAVA_H = 48;
   private static final int PAYLOAD_FIELD_ROW_H = 18;
   private static final int PAYLOAD_SCRIPT_H = 126;
   private static final int PAYLOAD_VIEW_H = 138;
   private static final long PAYLOAD_EDIT_DEBOUNCE_NANOS = 125000000L;
   private static final Gson PAYLOAD_TEXT_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final int CAPTURE_TOAST_SUCCESS = -10035062;
   private static final int CAPTURE_TOAST_ERROR = -38037;
   private static final int MACRO_SELECT_VISIBLE_ROWS = 5;
   private static List<String> ALL_BLOCK_IDS;
   private static List<String> ALL_SOUND_IDS;
   private static List<String> ALL_ENTITY_IDS;
   private static List<String> ALL_ITEM_IDS;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final RiptidePacketSelectorOverlay packetSelectorOverlay;
   private final RaceStepSelectorOverlay raceStepSelectorOverlay;
   private int panelX = 320;
   private int panelY = 60;
   private int panelW = 280;
   private int panelH = 96;
   private boolean visible = false;
   private boolean dragging = false;
   private double dragOffX;
   private double dragOffY;
   private boolean restoreVisibleAfterCapture = false;
   private boolean worldCaptureAllowed = true;
   private boolean configurationOnly = false;
   private Screen screenBeforeGBreak;
   private Screen screenBeforeCapture;
   private boolean autoOpenedInventoryForCapture = false;
   private boolean entitySpecificCaptureMode = false;
   private boolean suppressCaptureValueAutoPattern = false;
   private String lastGeneratedCapturePattern = "";
   private String lastCaptureSource = "";
   private boolean captureWaitTouched;
   private MacroAction targetAction;
   private int multiEditCount = 1;
   private CompoundTag workingTag;
   private ActionFieldSchema schema;
   private Runnable onSaveCallback;
   private final Map<String, RiptideChatField> textFields = new LinkedHashMap<>();
   private final Map<String, Boolean> toggleStates = new LinkedHashMap<>();
   private final Map<String, Integer> enumIndices = new LinkedHashMap<>();
   private final Map<String, List<String>> stringLists = new LinkedHashMap<>();
   private final Map<String, ItemTarget> editorItemFields = new HashMap<>();
   private final Map<String, List<ItemTarget>> editorItemLists = new HashMap<>();
   private final Map<String, RiptideChatField> addFields = new LinkedHashMap<>();
   private final Map<String, DirectScrollViewport> catalogScrollViewports = new HashMap<>();
   private final Map<String, Integer> stringListEditIndex = new HashMap<>();
   private final Map<String, String> stringListEditPendingText = new HashMap<>();
   private final Map<String, List<Integer>> stringListFilteredIndices = new HashMap<>();
   private final Map<String, List<String>> catalogFilteredValues = new HashMap<>();
   private final Map<String, DirectScrollViewport> selectedScrollViewports = new HashMap<>();
   private final List<MacroExecutor.RecentChatMessage> waitChatFilteredHistory = new ArrayList<>();
   private final List<String> cachedMacroNames = new ArrayList<>();
   private final List<String> macroNamesWithCurrent = new ArrayList<>();
   private long cachedMacroNamesRevision = -1L;
   private int scrollOffset = 0;
   private ItemAction itemAction;
   private final MacroCaptureSession captureSession = new MacroCaptureSession();
   private String packetClickCapturePendingKey;
   private final List<CompactDropdown> enumDropdowns = new ArrayList<>();
   private final Map<String, CompactDropdown> enumDropdownCache = new HashMap<>();
   private final List<String> variableTokens = new ArrayList<>();
   private RiptideMacro variableMacro;
   private int variableActionIndex = -1;
   private static final List<String> RACE_CONDITION_CHOICES = List.of(
      "Timing / DELAY",
      "GUI / WAIT_GUI",
      "Chat / WAIT_CHAT",
      "Packets / WAIT_PACKET",
      "Packets / WAIT_PACKET_MATCH",
      "Inventory / WAIT_SLOT_CHANGE",
      "Inventory / WAIT_INVENTORY_PREDICATE",
      "Inventory / WAIT_DURABILITY",
      "Inventory / WAIT_FREE_SLOTS",
      "Player / WAIT_HEALTH",
      "Player / WAIT_GAMEMODE_CHANGE",
      "World / WAIT_BLOCK",
      "World / WAIT_ENTITY",
      "World / WAIT_MOVEMENT",
      "World / WAIT_SOUND",
      "State / WAIT_COOLDOWN",
      "Sync / WAIT_LAN_STEP",
      "Sync / WAIT_MACRO_STEP",
      "Sync / TICK_SYNC",
      "Sync / REVISION_SYNC",
      "Sync / SERVER_TICK_SYNC"
   );
   private static final List<String> RACE_ACTION_CHOICES = List.of(
      "Inventory / PACKET_CLICK",
      "Inventory / ITEM",
      "Inventory / CLICK",
      "Inventory / CLOSE_GUI",
      "Inventory / INVENTORY",
      "Inventory / SELECT_SLOT",
      "Inventory / SWAP_SLOTS",
      "Inventory / DROP",
      "Inventory / XCARRY",
      "Inventory / CONTAINER_CLICK_SEQUENCE",
      "Packets / SEND_PACKET",
      "Packets / PACKET_GATE",
      "Packets / PACKET_BURST",
      "Packets / PAYLOAD",
      "Chat / SEND_CHAT",
      "Chat / SEND_COMMAND_PACKET",
      "Items / USE_ITEM",
      "Items / USE_ITEM_PHASE",
      "Items / NBT_BOOK",
      "World / MINE",
      "World / INSTA_BREAK",
      "World / BREAK",
      "Movement / VCLIP",
      "Movement / HCLIP",
      "Movement / TP",
      "Movement / SNEAK",
      "Movement / JUMP",
      "Movement / SPRINT",
      "Macro / MACRO_VARIABLES",
      "Macro / CAPTURE_VALUE",
      "Macro / FAKE_GAMEMODE",
      "Macro / ASSERT",
      "Macro / TOGGLE_MODULE",
      "Macro / START_MACRO",
      "Macro / STOP_MACRO",
      "Misc / PAY",
      "Misc / SIGN_EDIT",
      "Misc / RESTORE_GUI",
      "Misc / SAVE_GUI",
      "Misc / DESYNC"
   );
   private List<IRiptideOverlay> captureHiddenOverlays;
   private final ToastStack captureToasts = new ToastStack(700L, 140.0F, 180.0F, 4, 4, 18);
   private List<CraftAction.CraftEntry> craftEntries;
   private List<RiptideCraftingHelper.CraftableRecipeOption> craftAllRecipes;
   private List<RiptideCraftingHelper.CraftableRecipeOption> craftFilteredRecipes;
   private RiptideCraftingHelper.CraftableRecipeOption craftSelectedRecipe;
   private int craftRecipeScrollOffset;
   private boolean craftUseMax;
   private String craftLastQuery;
   private int[] craftRecipeListBounds;
   private DropAction dropAction;
   private PayloadAction payloadAction;
   private RiptidePayloadEditorModel payloadEditorModel;
   private boolean standalonePayloadEditor = false;
   private ActionEditorOverlay.PayloadContentMode payloadContentMode = ActionEditorOverlay.PayloadContentMode.BINARY_REPLAY;
   private int payloadFieldCount = 0;
   private int payloadTabIndex = 0;
   private boolean payloadContentEdited = false;
   private boolean payloadRawEdited = false;
   private boolean payloadChannelEdited = false;
   private boolean payloadJsonEdited = false;
   private boolean payloadModeManuallyChanged = false;
   private boolean payloadAddTypeManuallyChanged = false;
   private boolean suppressPayloadEditorChange = false;
   private ActionEditorOverlay.PendingPayloadEdit pendingPayloadEdit;
   private long pendingPayloadEditAtNanos;
   private long payloadPreviewSyncedRevision = Long.MIN_VALUE;
   private ActionEditorOverlay.PayloadBuildCache payloadBuildCache;
   private int itemEditIndex = -1;
   private int dropEditIndex = -1;
   private int wscEditIndex = -1;
   private List<WaitForSlotChangeAction.WaitEntry> wscEntries;
   private WaitForSlotChangeAction.WaitMode wscAddMode = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
   private int wscAddCount = 1;
   private boolean suppressItemEntryLiveUpdate = false;
   private boolean suppressDropEntryLiveUpdate = false;
   private boolean suppressWscLiveUpdate = false;
   private boolean suppressDropCountEditorUpdate = false;
   private boolean waitChatFuzzySliderDragging = false;
   private int waitChatFuzzySliderX = -1;
   private int waitChatFuzzySliderY = -1;
   private int waitChatFuzzySliderW = 0;
   private int waitChatFuzzySliderH = 0;
   private boolean rotateSmoothnessSliderDragging = false;
   private int rotateSmoothnessSliderX = -1;
   private int rotateSmoothnessSliderY = -1;
   private int rotateSmoothnessSliderW = 0;
   private int rotateSmoothnessSliderH = 0;
   private boolean xcarrySafeDelaySliderDragging = false;
   private int xcarrySafeDelaySliderX = -1;
   private int xcarrySafeDelaySliderY = -1;
   private int xcarrySafeDelaySliderW = 0;
   private int xcarrySafeDelaySliderH = 0;
   private boolean suppressWaitChatPatternSync = false;
   private static final List<RiptidePayloadTemplate.FieldType> PAYLOAD_FIELD_TYPES = List.of(
      RiptidePayloadTemplate.FieldType.BYTE,
      RiptidePayloadTemplate.FieldType.UNSIGNED_BYTE,
      RiptidePayloadTemplate.FieldType.BOOLEAN,
      RiptidePayloadTemplate.FieldType.SHORT,
      RiptidePayloadTemplate.FieldType.UNSIGNED_SHORT,
      RiptidePayloadTemplate.FieldType.CHAR,
      RiptidePayloadTemplate.FieldType.INT,
      RiptidePayloadTemplate.FieldType.LONG,
      RiptidePayloadTemplate.FieldType.FLOAT,
      RiptidePayloadTemplate.FieldType.DOUBLE,
      RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF,
      RiptidePayloadTemplate.FieldType.RAW_BYTES,
      RiptidePayloadTemplate.FieldType.BYTE_ARRAY,
      RiptidePayloadTemplate.FieldType.STRING_BYTES,
      RiptidePayloadTemplate.FieldType.VAR_INT,
      RiptidePayloadTemplate.FieldType.VAR_LONG,
      RiptidePayloadTemplate.FieldType.MINECRAFT_STRING,
      RiptidePayloadTemplate.FieldType.IDENTIFIER,
      RiptidePayloadTemplate.FieldType.UUID_FIELD,
      RiptidePayloadTemplate.FieldType.BLOCK_POS,
      RiptidePayloadTemplate.FieldType.ENUM_VAR_INT,
      RiptidePayloadTemplate.FieldType.OPTIONAL_VALUE,
      RiptidePayloadTemplate.FieldType.NBT,
      RiptidePayloadTemplate.FieldType.ITEM_STACK,
      RiptidePayloadTemplate.FieldType.TEXT_COMPONENT
   );
   private static final List<String> PAYLOAD_FIELD_TYPE_LABELS = payloadFieldTypeLabels();
   private static final List<String> PAYLOAD_MODE_LABELS = List.of("Raw UTF-8", "Hex", "DataOutput", "ByteBuf", "JSON", "Mixed");
   private static final List<RiptidePayloadTemplate.EncodingMode> PAYLOAD_MODES = List.of(
      RiptidePayloadTemplate.EncodingMode.RAW_UTF8,
      RiptidePayloadTemplate.EncodingMode.MANUAL_HEX,
      RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT,
      RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF,
      RiptidePayloadTemplate.EncodingMode.JSON_TEXT,
      RiptidePayloadTemplate.EncodingMode.ADVANCED_MIXED
   );
   private List<WaitForLanStepAction.LanStepEntry> lanStepEntries;
   private List<WaitPacketMatchAction.Rule> waitPacketMatchRules;
   private int waitPacketMatchEditIndex = -1;
   private final List<String> payScannedPlayers = new ArrayList<>();
   private final Map<String, String> payPlayerPrefixes = new HashMap<>();
   private final Map<String, Component> payPrefixComponents = new HashMap<>();
   private boolean payPlayerScanPerformed = false;
   private final List<String> riptideModuleNames = new ArrayList<>();
   private final List<String> meteorModuleNames = new ArrayList<>();
   private List<ToggleModuleAction.ModuleEntry> toggleModuleEntries;
   private Runnable onPreSave;
   private final List<ActionEditorOverlay.HitRegion> hitRegions = new ArrayList<>();
   private UiBounds bodyInputClip;
   private float frameDelta = 0.0F;
   private final List<ActionEditorOverlay.ScrollDragRegion> scrollDragRegions = new ArrayList<>();
   private IntConsumer activeScrollDragHandler = null;
   private static ActionEditorOverlay sharedInstance;
   private static final List<String> DEFAULT_VARIABLE_TOKENS = List.of(
      "{user}", "{pos}", "{rot}", "{x}", "{y}", "{z}", "{uuid}", "{dimension}", "{facing}", "{server}", "{selected_slot}", "{timestamp}"
   );
   private List<String> captureTablistNamesCache;
   private long captureTablistNamesAtMs;

   private static List<String> payloadFieldTypeLabels() {
      List<String> labels = new ArrayList<>();

      for (RiptidePayloadTemplate.FieldType type : PAYLOAD_FIELD_TYPES) {
         labels.add(type.label());
      }

      return List.copyOf(labels);
   }

   public static ActionEditorOverlay getSharedOverlay() {
      if (sharedInstance == null) {
         sharedInstance = new ActionEditorOverlay(MC.font);
         RiptideOverlayManager.get().register(sharedInstance);
      }

      return sharedInstance;
   }

   private static boolean isCompactEditorType(MacroActionType type) {
      if (type == null) {
         return false;
      } else {
         return switch (type) {
            case INVENTORY, RESTORE_GUI, SAVE_GUI, SEND_TOGGLE, DESYNC -> true;
            default -> false;
         };
      }
   }

   private int preferredPanelWidthFor(MacroAction action) {
      return isCompactEditorType(action == null ? null : action.getType()) ? 184 : 280;
   }

   private int currentMinPanelHeight() {
      return isCompactEditorType(this.targetAction == null ? null : this.targetAction.getType()) ? 88 : 96;
   }

   public static boolean supportsActionEditor(MacroAction action) {
      if (action == null) {
         return false;
      } else if (action instanceof MissingAddonAction) {
         return false;
      } else {
         MacroActionType type = action.getType();
         if (type == null) {
            return !ActionFieldRegistry.get(action).fields().isEmpty();
         } else {
            return switch (type) {
               case CRAFT, DROP, ITEM, PAYLOAD, WAIT_LAN_STEP, WAIT_PACKET, WAIT_PACKET_MATCH, WAIT_SLOT_CHANGE -> true;
               default -> !ActionFieldRegistry.get(type).fields().isEmpty();
            };
         }
      }
   }

   public static ActionEditorOverlay getSharedOverlayIfExists() {
      return sharedInstance;
   }

   public boolean isEditingAction(MacroAction action) {
      return this.visible && !this.standalonePayloadEditor && this.targetAction == action;
   }

   public void setWorldCaptureAllowed(boolean allowed) {
      this.worldCaptureAllowed = allowed;
      if (!allowed) {
         this.cancelCaptureIfActive();
      }
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      this.configurationOnly = configurationOnly;
      if (configurationOnly) {
         this.setWorldCaptureAllowed(false);
      }
   }

   private boolean guardWorldCaptureAction() {
      if (this.worldCaptureUsable()) {
         return true;
      } else {
         RiptideNotifications.warning("Pick/Capture only works in-game.");
         return false;
      }
   }

   private boolean worldCaptureUsable() {
      if (this.worldCaptureAllowed) {
         return true;
      } else {
         boolean inWorld = MC != null && MC.player != null && MC.level != null;
         if (!inWorld) {
            return false;
         } else {
            this.configurationOnly = false;
            this.worldCaptureAllowed = true;
            return true;
         }
      }
   }

   private boolean captureControlAvailable(CaptureMode mode) {
      return mode == CaptureMode.CUSTOM_MENU_BUTTON ? true : mode == CaptureMode.PACKET_NAME || this.worldCaptureUsable();
   }

   public void closeIfEditingAny(List<MacroAction> actions) {
      if (this.visible && !this.standalonePayloadEditor && this.targetAction != null && actions != null) {
         if (!actions.contains(this.targetAction)) {
            this.closeEditor(false);
         }
      }
   }

   public void closeIfEditingAction(MacroAction action) {
      if (this.isEditingAction(action)) {
         this.closeEditor(false);
      }
   }

   public ActionEditorOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.packetSelectorOverlay = new RiptidePacketSelectorOverlay(textRenderer);
      this.raceStepSelectorOverlay = new RaceStepSelectorOverlay(textRenderer);
   }

   public void setMacroContext(RiptideMacro macro, int actionIndex) {
      this.variableMacro = macro;
      this.variableActionIndex = actionIndex;
      this.rebuildVariableTokens();
   }

   public void open(MacroAction action, Runnable onPreSave, Runnable onSave) {
      this.open(action, onPreSave, onSave, 1);
   }

   public void open(MacroAction action, Runnable onPreSave, Runnable onSave, int editCount) {
      this.standalonePayloadEditor = false;
      this.targetAction = action;
      this.multiEditCount = Math.max(1, editCount);
      this.onPreSave = onPreSave;
      this.onSaveCallback = onSave;
      this.workingTag = RiptideMacro.serializeAction(action);
      this.schema = ActionFieldRegistry.get(action);
      this.scrollOffset = 0;
      this.rebuildVariableTokens();
      this.textFields.clear();
      this.toggleStates.clear();
      this.enumIndices.clear();
      this.stringLists.clear();
      this.editorItemFields.clear();
      this.editorItemLists.clear();
      this.addFields.clear();
      this.catalogScrollViewports.clear();
      this.stringListEditIndex.clear();
      this.stringListEditPendingText.clear();
      this.stringListFilteredIndices.clear();
      this.catalogFilteredValues.clear();
      this.selectedScrollViewports.clear();
      this.packetSelectorOverlay.close();
      this.prepareWorkingTagForEditor(action);

      for (FieldDef field : this.schema.fields()) {
         String key = field.key();
         switch (field.type()) {
            case TOGGLE:
               this.toggleStates.put(key, this.workingTag.contains(key) ? this.workingTag.getBooleanOr(key, false) : false);
               break;
            case NUMBER:
               int vx = this.workingTag.contains(key) ? this.workingTag.getIntOr(key, 0) : 0;
               RiptideChatField f = this.makeField(80);
               if (!field.dynamic()) {
                  f.setNumericOnly(true);
               }

               String binding = MacroDynamicBindings.get(action, key);
               f.setText(binding.isBlank() ? String.valueOf(vx) : binding);
               this.textFields.put(key, f);
               break;
            case DECIMAL:
               double vx = this.workingTag.contains(key) ? this.workingTag.getDoubleOr(key, 0.0) : 0.0;
               RiptideChatField f = this.makeField(80);
               if (!field.dynamic()) {
                  f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*\\.?\\d*"));
               }

               String binding = MacroDynamicBindings.get(action, key);
               f.setText(binding.isBlank() ? fmtDouble(vx) : binding);
               this.textFields.put(key, f);
               break;
            case TEXT:
            case MACRO_SELECT:
               String vx = this.workingTag.contains(key) ? this.workingTag.getStringOr(key, "") : "";
               RiptideChatField f = this.makeField(80);
               f.setText(vx);
               if ("saveAs".equals(key) && (vx.isEmpty() || VARIABLE_NAME.matcher(vx).matches())) {
                  f.setFilter(s -> s.isEmpty() || VARIABLE_NAME.matcher(s).matches());
               }

               this.textFields.put(key, f);
               break;
            case ENUM:
               String v = this.workingTag.contains(key) ? this.workingTag.getStringOr(key, "") : "";
               List<String> opts = field.enumOptions();
               int idx = opts.indexOf(v);
               if (idx < 0) {
                  idx = opts.indexOf(v.toUpperCase());
               }

               this.enumIndices.put(key, Math.max(0, idx));
               if (field.dynamic()) {
                  RiptideChatField bindingField = this.makeField(80);
                  bindingField.setText(MacroDynamicBindings.get(action, key));
                  this.textFields.put(key, bindingField);
               }
               break;
            case SLOT:
               int v = this.workingTag.contains(key) ? this.workingTag.getIntOr(key, 0) : 0;
               RiptideChatField f = this.makeField(50);
               if (!field.dynamic()) {
                  f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*"));
               }

               String binding = MacroDynamicBindings.get(action, key);
               f.setText(binding.isBlank() ? String.valueOf(v) : binding);
               this.textFields.put(key, f);
               break;
            case BLOCK_POS:
               String[] xyzKeys = field.xyzKeys();
               boolean dbl = field.xyzDouble();

               for (int i = 0; i < 3; i++) {
                  RiptideChatField f = this.makeField(50);
                  if (dbl) {
                     if (!field.dynamic()) {
                        f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*\\.?\\d*"));
                     }

                     double vx = this.workingTag.contains(xyzKeys[i]) ? this.workingTag.getDoubleOr(xyzKeys[i], 0.0) : 0.0;
                     String binding = MacroDynamicBindings.get(action, xyzKeys[i]);
                     f.setText(binding.isBlank() ? fmtDouble(vx) : binding);
                  } else {
                     if (!field.dynamic()) {
                        f.setNumericOnly(true);
                     }

                     int vx = this.workingTag.contains(xyzKeys[i]) ? this.workingTag.getIntOr(xyzKeys[i], 0) : 0;
                     String binding = MacroDynamicBindings.get(action, xyzKeys[i]);
                     f.setText(binding.isBlank() ? String.valueOf(vx) : binding);
                  }

                  this.textFields.put(key + "_" + i, f);
               }
               break;
            case STRING_LIST:
               List<String> list = new ArrayList<>();
               if (this.workingTag.contains(key)) {
                  for (Tag el : this.workingTag.getList(key).orElse(new ListTag())) {
                     String s = el.asString().orElse("");
                     if (!s.isEmpty()) {
                        list.add(s);
                     }
                  }
               }

               this.stringLists.put(key, list);
               RiptideChatField af = this.makeField(80);
               if (field.captureMode() == CaptureMode.BLOCK_CATALOG) {
                  af.setPlaceholder(Component.literal("Search blocks..."));
               } else {
                  af.setPlaceholder(Component.literal("Search / " + field.addLabel()));
                  af.setSubmitHandler(text -> {
                     if (!text.isBlank()) {
                        List<String> entries = this.stringLists.get(key);
                        if (entries != null && this.addStringListEntry(field, entries, text.strip())) {
                           af.setText("");
                        }
                     }

                     return true;
                  });
               }

               this.addFields.put(key, af);
         }
      }

      if (action instanceof UseItemAction useItemAction) {
         RiptideChatField slotField = this.textFields.get("slot");
         if (slotField != null && useItemAction.slot < 0 && !this.workingTag.contains("slot")) {
            slotField.setText("");
         }
      }

      this.itemAction = null;
      this.captureSession.clearItemSlotCapture();
      this.itemEditIndex = -1;
      this.dropEditIndex = -1;
      if (action instanceof ItemAction ia) {
         this.itemAction = new ItemAction();
         this.itemAction.itemNames = new ArrayList<>(ia.itemNames);
         this.itemAction.itemTargets = this.copyEditorTargets(ia.itemTargets, ia.itemNames);
         this.itemAction.itemTimes = new ArrayList<>(ia.itemTimes);
         this.itemAction.itemActionIdx = new ArrayList<>(ia.itemActionIdx);
         this.itemAction.itemButtons = new ArrayList<>(ia.itemButtons);
         this.itemAction.itemSearchScopes = new ArrayList<>(ia.itemSearchScopes);
         this.itemAction.stackAmountModes = new ArrayList<>(ia.stackAmountModes);

         while (this.itemAction.itemTimes.size() < this.itemAction.itemNames.size()) {
            this.itemAction.itemTimes.add(1);
         }

         while (this.itemAction.itemActionIdx.size() < this.itemAction.itemNames.size()) {
            this.itemAction.itemActionIdx.add(0);
         }

         while (this.itemAction.itemButtons.size() < this.itemAction.itemNames.size()) {
            this.itemAction.itemButtons.add(0);
         }

         while (this.itemAction.itemSearchScopes.size() < this.itemAction.itemNames.size()) {
            int index = this.itemAction.itemSearchScopes.size();
            this.itemAction.itemSearchScopes.add(ia.getItemSearchScope(index).ordinal());
         }

         while (this.itemAction.stackAmountModes.size() < this.itemAction.itemNames.size()) {
            this.itemAction.stackAmountModes.add(0);
         }

         this.itemAction.targetSlot = ia.targetSlot;
         this.itemAction.useSlot = ia.useSlot;
         this.itemAction.actionIndex = ia.actionIndex;
         this.itemAction.button = ia.button;
         this.itemAction.times = ia.times;
         this.itemAction.waitForGuiBefore = ia.waitForGuiBefore;
         this.itemAction.waitForGuiAfter = ia.waitForGuiAfter;
         this.itemAction.guiName = ia.guiName != null ? ia.guiName : "";
         this.itemAction.waitForItem = ia.waitForItem;
         this.itemAction.useCursorItemForPickupAll = ia.useCursorItemForPickupAll;

         for (int i = 0; i < this.itemAction.itemNames.size(); i++) {
            RiptideChatField f = this.makeField(28);
            f.setNumericOnly(true);
            f.setText(String.valueOf(this.itemAction.getItemTime(i)));
            this.textFields.put("item_times_" + i, f);
         }

         RiptideChatField addF = this.makeField(120);
         addF.setPlaceholder(Component.literal("Item name"));
         addF.setChangedListener(text -> this.handleItemEntryEditorChanged());
         this.addFields.put("_item_add", addF);
         RiptideChatField addSlotF = this.makeField(52);
         addSlotF.setNumericOnly(true);
         addSlotF.setPlaceholder(Component.literal("Slot"));
         addSlotF.setChangedListener(text -> this.handleItemEntryEditorChanged());
         this.textFields.put("item_entrySlot", addSlotF);
         this.toggleStates.put("item_waitForGuiBefore", ia.waitForGuiBefore);
         this.toggleStates.put("item_waitForGuiAfter", ia.waitForGuiAfter);
         this.toggleStates.put("item_waitForItem", ia.waitForItem);
         this.toggleStates.put("item_useCursorItemForPickupAll", ia.useCursorItemForPickupAll);
         this.toggleStates.put("item_useClickDelay", ia.useClickDelay);
         RiptideChatField guiF = this.makeField(80);
         guiF.setText(ia.guiName != null ? ia.guiName : "");
         this.textFields.put("item_guiName", guiF);
         RiptideChatField delayF = this.makeField(60);
         delayF.setNumericOnly(true);
         delayF.setText(String.valueOf(Math.max(0, ia.clickDelayMs)));
         this.textFields.put("item_clickDelayMs", delayF);
      }

      this.wscEditIndex = -1;
      this.wscEntries = new ArrayList<>();
      this.wscAddMode = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
      this.wscAddCount = 1;
      if (action instanceof WaitForSlotChangeAction wsc) {
         for (WaitForSlotChangeAction.WaitEntry e : wsc.entries) {
            this.wscEntries.add(e.copy());
         }
      }

      if (action.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
         this.toggleStates.put("listenDuringPreviousAction", action.listensDuringPreviousAction());
         RiptideChatField wscAddF = this.makeField(120);
         wscAddF.setPlaceholder(Component.literal("Item name (optional)"));
         wscAddF.setChangedListener(text -> this.handleWscEntryEditorChanged());
         this.addFields.put("_wsc_add", wscAddF);
         RiptideChatField wscSlotF = this.makeField(52);
         wscSlotF.setNumericOnly(true);
         wscSlotF.setPlaceholder(Component.literal("Slot #"));
         wscSlotF.setChangedListener(text -> this.handleWscEntryEditorChanged());
         this.textFields.put("wsc_slot", wscSlotF);
         RiptideChatField wscCountF = this.makeField(36);
         wscCountF.setNumericOnly(true);
         wscCountF.setPlaceholder(Component.literal("Count"));
         wscCountF.setChangedListener(text -> this.handleWscCountChanged());
         this.textFields.put("wsc_count", wscCountF);
      }

      this.craftEntries = null;
      this.craftAllRecipes = null;
      this.craftFilteredRecipes = null;
      this.craftSelectedRecipe = null;
      this.craftRecipeScrollOffset = 0;
      this.craftUseMax = false;
      this.craftLastQuery = null;
      this.craftRecipeListBounds = null;
      if (action instanceof CraftAction craftAction) {
         this.craftEntries = craftAction.copyEntries();

         for (int i = 0; i < this.craftEntries.size(); i++) {
            CraftAction.CraftEntry entry = this.craftEntries.get(i);
            RiptideChatField f = this.makeField(44);
            f.setNumericOnly(true);
            f.setText(String.valueOf(entry.amount));
            this.textFields.put("craft_amount_" + i, f);
            this.toggleStates.put("craft_useMax_" + i, entry.useMaxAmount);
         }

         RiptideChatField amtF = this.makeField(44);
         amtF.setNumericOnly(true);
         amtF.setText("1");
         this.textFields.put("_craft_amount", amtF);
         RiptideChatField srchF = this.makeField(180);
         srchF.setPlaceholder(Component.literal("Search recipes..."));
         this.addFields.put("_craft_search", srchF);
         this.craftAllRecipes = RiptideCraftingHelper.getCraftableRecipes(MC);
         this.craftFilteredRecipes = RiptideCraftingHelper.filterRecipes(this.craftAllRecipes, "");
      }

      if (action instanceof WaitForPacketAction waitForPacketAction) {
         this.stringLists.put("packetNames", this.sanitizeWaitPacketTargets(waitForPacketAction.effectiveList()));
         this.toggleStates.put("listenDuringPreviousAction", waitForPacketAction.listenDuringPreviousAction);
      }

      this.waitPacketMatchRules = null;
      this.waitPacketMatchEditIndex = -1;
      if (action instanceof WaitPacketMatchAction packetMatchAction) {
         this.waitPacketMatchRules = new ArrayList<>();

         for (WaitPacketMatchAction.Rule rule : packetMatchAction.effectiveRules()) {
            this.waitPacketMatchRules.add(rule.copy());
         }

         if (this.waitPacketMatchRules.isEmpty()) {
            this.waitPacketMatchRules.add(new WaitPacketMatchAction.Rule());
         }

         this.waitPacketMatchEditIndex = 0;
         this.toggleStates.put("listenDuringPreviousAction", packetMatchAction.listenDuringPreviousAction);
         RiptideChatField valueField = this.makeField(120);
         valueField.setPlaceholder(Component.literal("Value"));
         this.textFields.put("_wpm_value", valueField);
         this.syncWaitPacketMatchValueField();
      }

      this.dropAction = null;
      if (action instanceof DropAction da) {
         this.dropAction = new DropAction();
         this.dropAction.mode = da.mode;
         this.dropAction.dropCount = da.dropCount;
         this.dropAction.itemNames = new ArrayList<>(da.itemNames);
         this.dropAction.itemTargets = this.copyEditorTargets(da.itemTargets, da.itemNames);
         this.dropAction.itemCounts = new ArrayList<>(da.itemCounts);
         this.dropAction.waitForGuiBefore = da.waitForGuiBefore;
         this.dropAction.waitForGuiAfter = da.waitForGuiAfter;
         this.dropAction.guiName = da.guiName != null ? da.guiName : "";
         this.dropAction.useHandlerSlots = da.useHandlerSlots;

         while (this.dropAction.itemCounts.size() < this.dropAction.itemNames.size()) {
            this.dropAction.itemCounts.add(this.dropAction.getItemCount(this.dropAction.itemCounts.size()));
         }

         for (int i = 0; i < this.dropAction.itemNames.size(); i++) {
            RiptideChatField f = this.makeField(32);
            f.setNumericOnly(true);
            f.setText(String.valueOf(this.dropAction.itemCounts.get(i)));
            this.textFields.put("drop_count_" + i, f);
         }

         RiptideChatField cntF = this.makeField(60);
         cntF.setNumericOnly(true);
         cntF.setText(String.valueOf(da.dropCount));
         cntF.setChangedListener(text -> this.handleDropCountEditorChanged());
         this.textFields.put("drop_globalCount", cntF);
         RiptideChatField addDropF = this.makeField(120);
         addDropF.setPlaceholder(Component.literal("Item name..."));
         addDropF.setChangedListener(text -> this.handleDropEntryEditorChanged());
         this.addFields.put("_drop_add", addDropF);
         RiptideChatField addDropSlotF = this.makeField(52);
         addDropSlotF.setNumericOnly(true);
         addDropSlotF.setPlaceholder(Component.literal("Slot"));
         addDropSlotF.setChangedListener(text -> this.handleDropEntryEditorChanged());
         this.textFields.put("drop_entrySlot", addDropSlotF);
         RiptideChatField dropGuiF = this.makeField(80);
         dropGuiF.setText(da.guiName != null ? da.guiName : "");
         this.textFields.put("drop_guiName", dropGuiF);
         this.toggleStates.put("drop_waitForGuiBefore", da.waitForGuiBefore);
         this.toggleStates.put("drop_waitForGuiAfter", da.waitForGuiAfter);
         this.toggleStates.put("drop_useHandlerSlots", da.useHandlerSlots);
         DropAction.DropMode[] modes = DropAction.DropMode.values();
         int mi = 0;

         for (int i = 0; i < modes.length; i++) {
            if (modes[i] == da.mode) {
               mi = i;
               break;
            }
         }

         this.enumIndices.put("drop_mode", mi);
         this.syncDropCountEditorField();
      }

      this.payloadAction = null;
      this.payloadEditorModel = null;
      if (action instanceof PayloadAction pa) {
         this.payloadAction = new PayloadAction();
         this.payloadAction.fromTag(pa.toTag());
         this.initializePayloadEditorFields(this.payloadAction);
      }

      this.lanStepEntries = null;
      if (action instanceof WaitForLanStepAction wls) {
         this.lanStepEntries = new ArrayList<>();

         for (WaitForLanStepAction.LanStepEntry e : wls.entries) {
            this.lanStepEntries.add(new WaitForLanStepAction.LanStepEntry(e.username, e.step));
         }

         for (int ix = 0; ix < this.lanStepEntries.size(); ix++) {
            WaitForLanStepAction.LanStepEntry e = this.lanStepEntries.get(ix);
            RiptideChatField uf = this.makeField(80);
            uf.setText(e.username);
            this.textFields.put("lan_user_" + ix, uf);
            RiptideChatField sf = this.makeField(40);
            sf.setNumericOnly(true);
            sf.setText(String.valueOf(e.step));
            this.textFields.put("lan_step_" + ix, sf);
         }

         RiptideChatField newUF = this.makeField(80);
         newUF.setPlaceholder(Component.literal("Peer name..."));
         this.addFields.put("_lan_user_add", newUF);
         RiptideChatField newSF = this.makeField(40);
         newSF.setNumericOnly(true);
         newSF.setText("1");
         this.addFields.put("_lan_step_add", newSF);
         RiptideChatField dsF = this.makeField(60);
         dsF.setNumericOnly(true);
         dsF.setText(String.valueOf(wls.defaultStep));
         this.textFields.put("lan_defaultStep", dsF);
         this.toggleStates.put("lan_filterByUser", wls.filterByUser);
         this.toggleStates.put("listenDuringPreviousAction", wls.listenDuringPreviousAction);
      }

      this.entitySpecificCaptureMode = false;
      if (action instanceof WaitForSoundAction) {
         RiptideChatField search = this.addFields.get("soundIds");
         if (search != null) {
            search.setPlaceholder(Component.literal("Search sound id..."));
         }
      }

      if (action instanceof WaitForEntityAction) {
         RiptideChatField search = this.addFields.get("entityIds");
         if (search != null) {
            search.setPlaceholder(Component.literal("Search entity type..."));
         }
      }

      if (action instanceof LookAtBlockAction) {
         RiptideChatField search = this.addFields.get("entityIds");
         if (search != null) {
            search.setPlaceholder(Component.literal("Search entity type..."));
         }
      }

      if (action instanceof StoreItemAction) {
         RiptideChatField search = this.addFields.get("targetItems");
         if (search != null) {
            search.setPlaceholder(Component.literal("Search item id..."));
         }
      }

      if (action instanceof InventoryAuditAction) {
         RiptideChatField search = this.addFields.get("targetItems");
         if (search != null) {
            search.setPlaceholder(Component.literal("Item name"));
         }
      }

      this.payScannedPlayers.clear();
      this.payPlayerPrefixes.clear();
      this.payPrefixComponents.clear();
      this.payPlayerScanPerformed = false;
      if (action instanceof PayAction) {
         RiptideChatField search = this.addFields.get("players");
         if (search != null) {
            search.setPlaceholder(Component.literal("Search or add player..."));
         }
      }

      this.riptideModuleNames.clear();
      this.meteorModuleNames.clear();
      this.toggleModuleEntries = null;
      if (action instanceof ToggleModuleAction) {
         RiptideChatField search = this.makeField(180);
         search.setPlaceholder(Component.literal("Search module..."));
         this.addFields.put("_toggle_module_search", search);
         this.refreshMeteorModuleNames();
         ToggleModuleAction toggleModuleAction = (ToggleModuleAction)action;
         this.toggleModuleEntries = new ArrayList<>();

         for (ToggleModuleAction.ModuleEntry entry : toggleModuleAction.entries) {
            if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
               this.toggleModuleEntries.add(new ToggleModuleAction.ModuleEntry(entry.moduleName, entry.toggleMode));
            }
         }
      }

      if (action instanceof WaitForChatAction) {
         RiptideChatField search = this.makeField(180);
         search.setPlaceholder(Component.literal("Search recent chat..."));
         this.addFields.put("_wait_chat_search", search);
         RiptideChatField captureName = this.makeField(100);
         captureName.setPlaceholder(Component.literal("Variable name"));
         this.addFields.put("_capture_name", captureName);
         this.waitChatFuzzySliderDragging = false;
         this.clearWaitChatFuzzySliderBounds();
         RiptideChatField patternField = this.textFields.get("pattern");
         if (patternField != null) {
            patternField.setMultiline(true);
            patternField.setHeight(40);
            String existingPatternJson = this.workingTag.getStringOr("patternJson", "");
            Component initialPattern = (Component)(existingPatternJson != null && !existingPatternJson.isBlank()
               ? this.getWaitChatPatternComponent(this.workingTag.getStringOr("pattern", ""))
               : Component.literal(MacroExecutor.normalizeManualText(this.workingTag.getStringOr("pattern", ""))));
            String visiblePattern = initialPattern.getString();
            this.workingTag.putString("pattern", visiblePattern);
            if (existingPatternJson != null && !existingPatternJson.isBlank()) {
               this.workingTag.putString("patternJson", MacroExecutor.serializeTextComponent(initialPattern));
            } else {
               this.workingTag.putString("patternJson", "");
            }

            patternField.setDisplayTextProvider(this::getWaitChatPatternComponent);
            this.suppressWaitChatPatternSync = true;
            patternField.setText(visiblePattern);
            this.suppressWaitChatPatternSync = false;
            patternField.setChangedListener(value -> {
               if (!this.suppressWaitChatPatternSync) {
                  String visibleValue = MacroExecutor.normalizeManualText(value);
                  this.workingTag.putString("pattern", visibleValue);
                  this.workingTag.putString("patternJson", "");
               }
            });
         }
      } else {
         this.suppressWaitChatPatternSync = false;
         this.waitChatFuzzySliderDragging = false;
         this.clearWaitChatFuzzySliderBounds();
      }

      if (action instanceof WaitForGuiAction) {
         RiptideChatField captureName = this.makeField(100);
         captureName.setPlaceholder(Component.literal("Variable name"));
         this.addFields.put("_capture_name", captureName);
      }

      if (action instanceof CaptureValueAction) {
         RiptideChatField token = this.makeField(180);
         token.setEditable(false);
         token.setFocusEffectsEnabled(false);
         token.setPlaceholder(Component.literal("Set a variable name"));
         this.addFields.put("_capture_token", token);
         RiptideChatField preview = this.makeField(180);
         preview.setEditable(false);
         preview.setFocusEffectsEnabled(false);
         preview.setPlaceholder(Component.literal("Nothing available now"));
         this.addFields.put("_capture_preview", preview);
         this.lastGeneratedCapturePattern = CaptureValueAction.buildCapturePattern(
            this.editorFieldText("exampleText", ""), this.editorFieldText("selectedText", ""), this.editorFieldText("saveAs", "value")
         );
         this.lastCaptureSource = "";
         this.captureWaitTouched = false;

         for (String key : List.of("saveAs", "exampleText", "selectedText")) {
            RiptideChatField field = this.textFields.get(key);
            if (field != null) {
               field.setChangedListener(ignored -> this.refreshCaptureValueAutoPattern());
            }
         }
      } else {
         this.suppressCaptureValueAutoPattern = false;
         this.lastGeneratedCapturePattern = "";
      }

      if (action instanceof RotateAction) {
         this.rotateSmoothnessSliderDragging = false;
         this.clearRotateSmoothnessSliderBounds();
         RotateAction rotateAction = (RotateAction)action;
         RiptideChatField smoothnessField = this.textFields.get("smoothness");
         int smoothness = RotateAction.clampSmoothness(rotateAction.smoothness);
         this.workingTag.putInt("smoothness", smoothness);
         if (smoothnessField != null) {
            smoothnessField.setText(String.valueOf(smoothness));
         }
      } else if (action instanceof LookAtBlockAction lookAtAction) {
         this.rotateSmoothnessSliderDragging = false;
         this.clearRotateSmoothnessSliderBounds();
         RiptideChatField smoothnessField = this.textFields.get("smoothness");
         int smoothness = RotateAction.clampSmoothness(lookAtAction.smoothness);
         this.workingTag.putInt("smoothness", smoothness);
         if (smoothnessField != null) {
            smoothnessField.setText(String.valueOf(smoothness));
         }
      } else {
         this.rotateSmoothnessSliderDragging = false;
         this.clearRotateSmoothnessSliderBounds();
      }

      if (action instanceof XCarryAction xCarryAction) {
         this.xcarrySafeDelaySliderDragging = false;
         this.clearXCarrySafeDelaySliderBounds();
         RiptideChatField delayField = this.textFields.get("safeClickDelayTicks");
         int safeDelay = XCarryAction.clampSafeClickDelayTicks(xCarryAction.safeClickDelayTicks);
         this.workingTag.putInt("safeClickDelayTicks", safeDelay);
         this.workingTag.putBoolean("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
         this.workingTag.putBoolean("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
         this.toggleStates.put("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
         this.toggleStates.put("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
         if (delayField != null) {
            delayField.setText(String.valueOf(safeDelay));
         }
      } else {
         this.xcarrySafeDelaySliderDragging = false;
         this.clearXCarrySafeDelaySliderBounds();
      }

      if (action.getType() == MacroActionType.INVENTORY || action.getType() == MacroActionType.CLOSE_GUI || action.getType() == MacroActionType.SAVE_GUI) {
         this.toggleStates.put("sendPacket", !this.workingTag.getBooleanOr("sendPacket", true));
      }

      if (action.getType() == MacroActionType.STORE_ITEM) {
         this.toggleStates.put("closeSendPkt", !this.workingTag.getBooleanOr("closeSendPkt", true));
      }

      this.refreshItemTextDisplayProviders();
      this.applyEditorPlaceholders();
      if (action.getType() == MacroActionType.PLACE) {
         RiptideChatField slotF = this.makeField(52);
         slotF.setNumericOnly(true);
         slotF.setPlaceholder(Component.literal("Slot"));
         slotF.setChangedListener(text -> this.syncPlaceItemEditorFields());
         this.textFields.put("place_itemSlot", slotF);
         if (action instanceof PlaceAction placeAction) {
            this.toggleStates.put("place_waitForItem", placeAction.waitForItem);
            this.toggleStates.put("place_silentSwitch", placeAction.silentSwitch);
         }

         ItemTarget placeTarget = this.editorItemFields.get("itemName");
         if (placeTarget == null && action instanceof PlaceAction placeAction) {
            placeTarget = this.resolveEditorTarget(placeAction.itemTarget, placeAction.itemName);
         }

         if (placeTarget != null) {
            slotF.setText(placeTarget.hasSlot() ? String.valueOf(placeTarget.slot) : "");
         }

         RiptideChatField nameF = this.textFields.get("itemName");
         if (nameF != null) {
            nameF.setChangedListener(text -> this.syncPlaceItemEditorFields());
         }

         if (action instanceof PlaceAction placeAction && placeAction.blockPos != null) {
            RiptideInstaBreakRenderer.setTarget(placeAction.blockPos, -50373);
         }
      }

      this.panelW = this.preferredPanelWidthFor(action);
      int contentH = this.computeContentH();
      int desiredH = 20 + contentH + 32 + 4;
      int minH = this.currentMinPanelHeight();
      int maxH = Math.max(minH, RiptideUiScale.getVirtualScreenHeight() * 4 / 5);
      this.panelH = Math.max(minH, Math.min(maxH, desiredH));
      this.visible = true;
      RiptideOverlayManager.get().bringToFront(this);
   }

   public void openStandalonePayloadEditor(PayloadAction action) {
      this.open(action, null, null);
      this.standalonePayloadEditor = true;
   }

   public boolean updateOpenPayloadChannel(String channel) {
      String target = channel == null ? "" : channel.trim();
      if (!this.visible || target.isBlank()) {
         return false;
      } else if (!this.standalonePayloadEditor && this.payloadAction == null && !(this.targetAction instanceof PayloadAction)) {
         return false;
      } else {
         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField == null) {
            return false;
         } else {
            this.suppressPayloadEditorChange = true;

            try {
               channelField.setText(target);
               if (this.payloadEditorModel != null) {
                  this.payloadEditorModel.channel = target;
                  this.payloadEditorModel.touch();
                  this.payloadBuildCache = null;
               }

               if (this.payloadAction != null) {
                  this.payloadAction.channel = target;
               }

               if (this.targetAction instanceof PayloadAction targetPayload) {
                  targetPayload.channel = target;
               }

               RiptideChatField contentField = this.textFields.get("payload_content");
               if (contentField != null) {
                  contentField.setText(this.replacePayloadScriptChannelLine(contentField.getText(), target));
               }

               this.payloadChannelEdited = true;
            } finally {
               this.suppressPayloadEditorChange = false;
            }

            this.refreshInteractiveLayout();
            RiptideOverlayManager.get().bringToFront(this);
            return true;
         }
      }
   }

   private String replacePayloadScriptChannelLine(String script, String channel) {
      String safeScript = script == null ? "" : script.replace("\r\n", "\n").replace('\r', '\n');
      String safeChannel = channel != null && !channel.isBlank() ? channel.trim() : "minecraft:brand";
      String[] lines = safeScript.split("\n", -1);

      for (int i = 0; i < lines.length; i++) {
         String raw = lines[i] == null ? "" : lines[i];
         String stripped = raw.stripLeading();
         if (!stripped.startsWith("#")) {
            int split = this.firstPayloadDelimiter(raw);
            if (split >= 0) {
               String key = raw.substring(0, split).strip();
               if ("channel".equalsIgnoreCase(key)) {
                  String indent = raw.substring(0, raw.length() - stripped.length());
                  lines[i] = indent + "channel = " + safeChannel;
                  return String.join("\n", lines);
               }
            }
         }
      }

      return safeScript.isBlank() ? "channel = " + safeChannel : "channel = " + safeChannel + "\n" + safeScript;
   }

   @Override
   public String getOverlayId() {
      return "riptide-action-editor";
   }

   @Override
   public int getMinWidth() {
      return 168;
   }

   @Override
   public int getMinHeight() {
      return this.currentMinPanelHeight();
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean v) {
      this.visible = v;
      if (v) {
         this.hitRegions.clear();
         this.scrollDragRegions.clear();
      } else {
         this.flushPendingPayloadEdit(true);
         CompactDropdown.closeOpenMenu(this.enumDropdowns);
         this.packetSelectorOverlay.setVisible(false);
         this.raceStepSelectorOverlay.setVisible(false);
         this.clearTextFieldFocus();
      }
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean value) {
      if (this.collapsed != value) {
         super.setCollapsed(value);
         if (value) {
            CompactDropdown.closeOpenMenu(this.enumDropdowns);
            this.packetSelectorOverlay.setVisible(false);
            this.raceStepSelectorOverlay.setVisible(false);
            this.hitRegions.clear();
            this.scrollDragRegions.clear();
         }
      }
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout b) {
      RiptideWindowLayout c = this.clampToScreen(this, b);
      this.panelX = c.x;
      this.panelY = c.y;
      this.panelW = c.width;
      this.panelH = c.height;
      this.visible = c.visible;
      this.collapsed = c.collapsed;
   }

   @Override
   public boolean isMouseOver(double mx, double my) {
      return this.visible
         && (
            this.packetSelectorOverlay.isVisible()
               || this.raceStepSelectorOverlay.isVisible()
               || CompactDropdown.isMenuOpen(this.enumDropdowns)
               || mx >= this.panelX && mx < this.panelX + this.panelW && my >= this.panelY && my < this.panelY + (this.collapsed ? 16 : this.panelH)
         );
   }

   @Override
   public boolean isOverDragBar(double mx, double my) {
      return this.visible
         && mx >= this.panelX
         && mx < this.panelX + this.panelW
         && my >= this.panelY
         && my < this.panelY + 16
         && !this.isOverWindowControl(mx, my, this.getBounds());
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      if (this.packetSelectorOverlay.hasTextFieldFocused()) {
         return true;
      } else if (this.raceStepSelectorOverlay.hasTextFieldFocused()) {
         return true;
      } else {
         for (RiptideChatField f : this.textFields.values()) {
            if (f.isFocused()) {
               return true;
            }
         }

         for (RiptideChatField fx : this.addFields.values()) {
            if (fx.isFocused()) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public void clearTextFieldFocus() {
      this.textFields.values().forEach(f -> f.setFocused(false));
      this.addFields.values().forEach(f -> f.setFocused(false));
   }

   public boolean wantsItemSlotCapture() {
      return this.captureSession.hasItemSlotCapture();
   }

   public boolean shouldRenderAbstractContainerScreenCaptureBanner() {
      return this.captureSession.hasItemSlotCapture();
   }

   public String getAbstractContainerScreenCaptureTitle() {
      return "Capturing " + this.getAbstractContainerScreenCaptureTargetLabel() + " - " + this.getCaptureActionLabel();
   }

   public String getAbstractContainerScreenCaptureInstruction() {
      String activeCaptureKey = this.captureSession.itemSlotKey();
      if ("_item_entries".equals(activeCaptureKey) || "_drop_entries".equals(activeCaptureKey)) {
         return "Right-click a slot to set the item. Esc = cancel";
      } else if ("_wsc_entries".equals(activeCaptureKey)) {
         return "Right-click slots to add items or exact slots. Esc = done";
      } else if (!this.stringLists.containsKey(activeCaptureKey)) {
         return "Right-click a slot in this screen. Esc = cancel";
      } else if (this.isXCarryListKey(activeCaptureKey)) {
         return "Right-click slots to add items, or empty slots for exact slots. Esc = done";
      } else {
         return this.isStoreItemTargetListKey(activeCaptureKey)
            ? "Right-click items anywhere to add by name. Ctrl+right-click for exact slots. Esc = done"
            : "Right-click slots to add item names. Esc = done";
      }
   }

   public String getAbstractContainerScreenCaptureHoverText(Slot slot, String itemName, String registryId) {
      if (slot == null) {
         return "";
      } else {
         int handlerSlot = RiptideInventoryHelper.toMenuSlotId(MC, slot);
         int visibleSlot = handlerSlot >= 0 ? RiptideInventoryHelper.toUserVisibleSlot(MC, handlerSlot) : -1;
         String slotText = visibleSlot >= 0 ? String.valueOf(visibleSlot) : "Handler " + handlerSlot;
         String slotDetail = "Handler " + handlerSlot;
         String itemText = !registryId.isEmpty() ? registryId : (!itemName.isEmpty() ? itemName : "Empty slot");
         return slotText.equals(slotDetail) ? "Hover: " + slotText + " | " + itemText : "Hover: " + slotText + " | " + slotDetail + " | " + itemText;
      }
   }

   public boolean cancelCaptureIfActive() {
      return this.cancelPacketClickCaptureIfActive() ? true : this.captureSession.stopItemSlotCapture(() -> this.exitCaptureMode(false, false));
   }

   public boolean hasActiveCaptureSession() {
      RiptideSharedState state = RiptideSharedState.get();
      return this.captureSession.hasItemSlotCapture()
         || this.packetClickCapturePendingKey != null
         || this.captureHiddenOverlays != null
         || this.restoreVisibleAfterCapture
         || this.screenBeforeCapture != null
         || this.screenBeforeGBreak != null
         || state.hasCaptureCancelCallback()
         || state.hasBlockCaptureCallback()
         || state.hasEntityCaptureCallback()
         || state.hasAttackCaptureCallback()
         || state.isGBreakCapturing();
   }

   public boolean onInventorySlotCapture(Slot slot, String itemName, String registryId) {
      return this.onInventorySlotCapture(slot, itemName, registryId, false);
   }

   public boolean onInventorySlotCapture(Slot slot, String itemName, String registryId, boolean slotMode) {
      int visibleSlot = slot != null ? RiptideInventoryHelper.toUserVisibleSlot(MC, slot) : -1;
      ItemTarget capturedTarget = this.captureItemTarget(slot, itemName, registryId, visibleSlot);
      if (this.captureSession.hasItemSlotCapture() && (!itemName.isEmpty() || visibleSlot >= 0)) {
         boolean useSlot = slotMode && visibleSlot >= 0;
         if (useSlot) {
            capturedTarget = ItemTarget.slotOnly(Math.max(0, visibleSlot));
         }

         String key = this.captureSession.itemSlotKey();
         if ("_item_entries".equals(key) && this.itemAction != null) {
            this.applyCapturedItemEntry(capturedTarget);
            this.captureSession.clearItemSlotCapture();
            this.exitCaptureMode(false, false);
            return true;
         } else if ("_drop_entries".equals(key) && this.dropAction != null) {
            this.applyCapturedDropEntry(capturedTarget);
            this.captureSession.clearItemSlotCapture();
            this.exitCaptureMode(false, false);
            return true;
         } else if ("_wsc_entries".equals(key)) {
            String rawTarget = capturedTarget.toLegacyEntry();
            if (rawTarget == null) {
               this.showCaptureToast("Nothing to add from that slot", -38037);
               return true;
            } else {
               String norm = StoreItemAction.normalizeTargetEntry(rawTarget);
               if (norm != null && !norm.isBlank()) {
                  String disp = StoreItemAction.formatTargetEntry(norm);
                  if (this.wscEditIndex >= 0 && this.wscEditIndex < this.wscEntries.size()) {
                     if (!this.wscTargetExistsOtherThan(norm, this.wscEditIndex)) {
                        WaitForSlotChangeAction.WaitEntry entry = this.wscEntries.get(this.wscEditIndex);
                        entry.target = norm;
                        entry.itemTarget = capturedTarget.copy();
                        this.syncWscEditorFromEntry(entry);
                        this.showCaptureToast("Updated: " + disp, -10035062);
                     } else {
                        this.showCaptureToast("Already in list: " + disp, -38037);
                     }
                  } else if (this.wscTargetExists(norm)) {
                     this.showCaptureToast("Already added: " + disp, -38037);
                  } else {
                     WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry(norm, this.wscAddMode, this.wscAddCount);
                     entry.itemTarget = capturedTarget.copy();
                     entry.target = norm;
                     this.wscEntries.add(entry);
                     this.showCaptureToast("Added: " + disp, -10035062);
                  }

                  return true;
               } else {
                  this.showCaptureToast("Nothing to add from that slot", -38037);
                  return true;
               }
            }
         } else if ("wait_inventory_slot".equals(key)) {
            this.setWaitInventorySlot(Math.max(0, visibleSlot));
            this.captureSession.clearItemSlotCapture();
            this.exitCaptureMode(false, false);
            this.showCaptureToast("Slot set: " + Math.max(0, visibleSlot), -10035062);
            return true;
         } else if ("itemName".equals(key) && this.targetAction != null && this.targetAction.getType() == MacroActionType.USE_ITEM) {
            RiptideChatField itemField = this.textFields.get("itemName");
            RiptideChatField slotField = this.textFields.get("slot");
            if (capturedTarget.hasIdentity()) {
               ItemTarget nameOnly = stripSlotFromTarget(capturedTarget);
               if (itemField != null) {
                  itemField.setText(nameOnly.editorText());
               }

               this.editorItemFields.put("itemName", nameOnly.copy());
               this.showCaptureToast("Item set: " + nameOnly.displayLabel(), -10035062);
            } else if (capturedTarget.hasSlot()) {
               if (itemField != null) {
                  itemField.setText("");
               }

               this.editorItemFields.remove("itemName");
               if (slotField != null) {
                  slotField.setText(String.valueOf(Math.max(0, capturedTarget.slot)));
               }

               this.showCaptureToast("Slot set: " + Math.max(0, capturedTarget.slot), -10035062);
            } else {
               this.showCaptureToast("Nothing to capture from that slot", -38037);
            }

            this.captureSession.clearItemSlotCapture();
            this.exitCaptureMode(false, false);
            return true;
         } else if (this.stringLists.containsKey(key)) {
            List<String> list = this.stringLists.get(key);
            if (list == null) {
               return false;
            } else {
               ActionEditorOverlay.CaptureListAddResult result = this.isStoreItemTargetListKey(key)
                  ? this.tryAddCapturedStoreItemEntry(slot, itemName, registryId, visibleSlot, useSlot, list)
                  : this.tryAddCapturedStringListEntry(
                     this.findField(key),
                     key,
                     list,
                     useSlot
                        ? capturedTarget.toLegacyEntry()
                        : (
                           this.isXCarryListKey(key)
                              ? capturedTarget.toLegacyEntry()
                              : (
                                 this.isInventoryAuditTargetListKey(key)
                                    ? stripSlotFromTarget(capturedTarget).toLegacyEntry()
                                    : (this.usesStoreTargetFormatting(key) ? capturedTarget.toLegacyEntry() : itemName)
                              )
                        )
                  );
               if (result != null
                  && result.added()
                  && (this.usesStoreTargetFormatting(key) || this.isXCarryListKey(key) || this.isInventoryAuditTargetListKey(key))) {
                  ItemTarget preservedTarget = this.isStoreItemTargetListKey(key)
                     ? storeCaptureTarget(capturedTarget)
                     : (this.isInventoryAuditTargetListKey(key) ? (useSlot ? capturedTarget : stripSlotFromTarget(capturedTarget)) : capturedTarget);
                  this.preserveCapturedListTarget(key, list, preservedTarget);
               }

               if (result != null && result.message() != null && !result.message().isBlank()) {
                  this.showCaptureToast(result.message(), result.accentColor());
               }

               return true;
            }
         } else {
            RiptideChatField tf = this.textFields.get(key);
            if (tf == null) {
               return false;
            } else {
               FieldDef field = this.findField(key);
               if (field != null && field.type() == FieldType.SLOT) {
                  tf.setText(String.valueOf(Math.max(0, visibleSlot)));
               } else if (useSlot) {
                  tf.setText(capturedTarget.toLegacyEntry());
                  if ("itemName".equals(key) || "fromItemName".equals(key) || "toItemName".equals(key) || this.editorItemFields.containsKey(key)) {
                     this.editorItemFields.put(key, capturedTarget.copy());
                  }
               } else {
                  ItemTarget nameOnly = stripSlotFromTarget(capturedTarget);
                  tf.setText(nameOnly.editorText());
                  if ("itemName".equals(key) || "fromItemName".equals(key) || "toItemName".equals(key) || this.editorItemFields.containsKey(key)) {
                     this.editorItemFields.put(key, nameOnly.copy());
                  }
               }

               this.captureSession.clearItemSlotCapture();
               this.exitCaptureMode(false, false);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.flushPendingPayloadEdit(false);
         this.frameDelta = delta;
         this.hitRegions.clear();
         this.scrollDragRegions.clear();
         int rawMouseX = mouseX;
         int rawMouseY = mouseY;
         if (!this.enumDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.enumDropdowns)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
         }

         this.enumDropdowns.clear();
         this.bodyInputClip = null;
         if (!this.collapsed) {
            int neededH = 20 + this.computeContentH() + 32 + 4;
            int minH = this.currentMinPanelHeight();
            int maxH = Math.max(minH, RiptideUiScale.getVirtualScreenHeight() * 4 / 5);
            this.panelH = Math.max(minH, Math.min(maxH, neededH));
         }

         RiptideWindowLayout clamped = this.clampToScreen(
            this, new RiptideWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelW = clamped.width;
         this.panelH = clamped.height;
         String title = this.targetActionTitle();
         RiptideWindowLayout bounds = this.getBounds();
         this.renderWindowFrame(context, mouseX, mouseY, bounds, title, this.collapsed, this.dragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (clipBody) {
            try {
               int frameH = this.getRenderedFrameHeight(bounds, false);
               int bodyTop = this.panelY + 16;
               int bodyBtm = this.panelY + frameH;
               if (bodyBtm > bodyTop + 1) {
                  this.renderBody(context, mouseX, mouseY, delta, bodyTop, bodyBtm);
               }

               if (!this.enumDropdowns.isEmpty()) {
                  CompactDropdown.renderButtons(context, this.textRenderer, this.enumDropdowns, rawMouseX, rawMouseY);
               }
            } finally {
               this.endWindowBodyClip(context, true);
            }
         }

         this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
         if (!this.enumDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.enumDropdowns)) {
            context.nextStratum();
            CompactDropdown.renderOpenMenu(context, this.textRenderer, this.enumDropdowns, rawMouseX, rawMouseY);
         }

         if (this.packetSelectorOverlay.isVisible()) {
            this.packetSelectorOverlay.render(context, mouseX, mouseY, delta);
         }

         if (this.raceStepSelectorOverlay.isVisible()) {
            this.raceStepSelectorOverlay.render(context, mouseX, mouseY, delta);
         }
      }
   }

   private void renderBody(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, int bodyTop, int bodyBtm) {
      if (this.itemAction != null) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.payloadAction != null) {
         int contentBtm = bodyBtm - 32;
         int contentAreaH = contentBtm - bodyTop;
         int totalContentH = this.computeContentH();
         int maxScroll = Math.max(0, totalContentH - contentAreaH);
         this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
         boolean needsScroll = maxScroll > 0;
         int sbReserve = needsScroll ? 6 : 0;
         int x = this.panelX + 4;
         int w = this.panelW - 8 - sbReserve;
         UiScissorStack.global().push(context, UiBounds.of(this.panelX + 1, bodyTop, Math.max(0, this.panelW - 2), Math.max(0, contentBtm - bodyTop)));

         try {
            this.renderPayloadPanel(context, x, bodyTop - this.scrollOffset, w, mouseX, mouseY, delta);
         } finally {
            UiScissorStack.global().pop(context);
         }

         if (needsScroll) {
            int sbX = this.panelX + this.panelW - 5 - 1;
            this.drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, this.scrollOffset, mouseX, mouseY);
            this.scrollDragRegions.add(new ActionEditorOverlay.ScrollDragRegion(sbX, bodyTop, 5, contentAreaH, my -> {
               int rel = Math.max(0, Math.min(contentAreaH, my - bodyTop));
               this.scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, contentAreaH)));
            }));
         }
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SEND_PACKET) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderSendPacketPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitPacketPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitPacketMatchPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SOUND) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitSoundPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_ENTITY) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitEntityPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.USE_ITEM) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderUseItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.LOOK_AT_BLOCK) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderLookAtPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.ROTATE) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderRotatePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.GO_TO) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderGoToPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SWAP_SLOTS) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderSwapSlotsPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CLICK) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderClickPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DISCONNECT) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderDisconnectPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_GUI) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitGuiPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_CHAT) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitChatPanel(context, x, bodyTop, bodyBtm - 32, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SELECT_SLOT) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderSelectSlotPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_COOLDOWN) {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderWaitCooldownPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      } else if (this.targetAction == null
         || this.targetAction.getType() != MacroActionType.OPEN_CONTAINER && this.targetAction.getType() != MacroActionType.INTERACT_ENTITY) {
         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderWaitSlotChangePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_INVENTORY_PREDICATE) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderWaitInventoryPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
            int contentBtm = bodyBtm - 32;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = this.computeContentH();
            int maxScroll = Math.max(0, totalContentH - contentAreaH);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? 6 : 0;
            int x = this.panelX + 4;
            int w = this.panelW - 8 - sbReserve;
            UiBounds clip = UiBounds.of(this.panelX + 1, bodyTop, Math.max(0, this.panelW - 2), contentAreaH);
            this.bodyInputClip = clip;
            int firstBodyHit = this.hitRegions.size();
            UiScissorStack.global().push(context, clip);

            try {
               this.renderCaptureValuePanel(context, x, bodyTop - this.scrollOffset, w, mouseX, mouseY, delta);
            } finally {
               UiScissorStack.global().pop(context);
            }

            this.clipHitRegions(firstBodyHit, clip);
            if (needsScroll) {
               int sbX = this.panelX + this.panelW - 5 - 1;
               this.drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, this.scrollOffset, mouseX, mouseY);
               int capSbH = contentAreaH;
               this.scrollDragRegions.add(new ActionEditorOverlay.ScrollDragRegion(sbX, bodyTop, 5, contentAreaH, my -> {
                  int rel = Math.max(0, Math.min(capSbH, my - bodyTop));
                  this.scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
               }));
            }
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DELAY_PACKETS) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderDelayPacketsPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.MINE) {
            int contentBtm = bodyBtm - 32;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = this.computeContentH();
            int maxScroll = Math.max(0, totalContentH - contentAreaH);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? 6 : 0;
            int x = this.panelX + 4;
            int w = this.panelW - 8 - sbReserve;
            UiBounds clip = UiBounds.of(this.panelX + 1, bodyTop, Math.max(0, this.panelW - 2), contentAreaH);
            this.bodyInputClip = clip;
            int firstBodyHit = this.hitRegions.size();
            UiScissorStack.global().push(context, clip);

            try {
               this.renderMinePanel(context, x, bodyTop - this.scrollOffset, w, mouseX, mouseY, delta);
            } finally {
               UiScissorStack.global().pop(context);
            }

            this.clipHitRegions(firstBodyHit, clip);
            if (needsScroll) {
               int sbX = this.panelX + this.panelW - 5 - 1;
               this.drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, this.scrollOffset, mouseX, mouseY);
               int capSbH = contentAreaH;
               this.scrollDragRegions.add(new ActionEditorOverlay.ScrollDragRegion(sbX, bodyTop, 5, contentAreaH, my -> {
                  int rel = Math.max(0, Math.min(capSbH, my - bodyTop));
                  this.scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
               }));
            }
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PAY) {
            int contentBtm = bodyBtm - 32;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = this.computeContentH();
            int maxScroll = Math.max(0, totalContentH - contentAreaH);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? 6 : 0;
            int x = this.panelX + 4;
            int w = this.panelW - 8 - sbReserve;
            UiBounds clip = UiBounds.of(this.panelX + 1, bodyTop, Math.max(0, this.panelW - 2), contentAreaH);
            this.bodyInputClip = clip;
            int firstBodyHit = this.hitRegions.size();
            UiScissorStack.global().push(context, clip);

            try {
               this.renderPayPanel(context, x, bodyTop - this.scrollOffset, w, mouseX, mouseY, delta);
            } finally {
               UiScissorStack.global().pop(context);
            }

            this.clipHitRegions(firstBodyHit, clip);
            if (needsScroll) {
               int sbX = this.panelX + this.panelW - 5 - 1;
               this.drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, this.scrollOffset, mouseX, mouseY);
               this.scrollDragRegions.add(new ActionEditorOverlay.ScrollDragRegion(sbX, bodyTop, 5, contentAreaH, my -> {
                  int rel = Math.max(0, Math.min(contentAreaH, my - bodyTop));
                  this.scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, contentAreaH)));
               }));
            }
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderInventoryAuditPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.STORE_ITEM) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderStoreItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.TOGGLE_MODULE) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderToggleModulePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.craftEntries != null) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderCraftPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.dropAction != null) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderDropPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.lanStepEntries != null) {
            int x = this.panelX + 4;
            int w = this.panelW - 8;
            this.renderLanStepPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
         } else if (this.schema != null && !this.schema.fields().isEmpty()) {
            int contentBtm = bodyBtm - 32;
            int contentAreaH = contentBtm - bodyTop;
            int totalContentH = this.computeContentH();
            int maxScroll = Math.max(0, totalContentH - contentAreaH);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? 6 : 0;
            int x = this.panelX + 4;
            int w = this.panelW - 8 - sbReserve;
            UiScissorStack.global().push(context, UiBounds.of(this.panelX + 1, bodyTop, Math.max(0, this.panelW - 2), Math.max(0, contentBtm - bodyTop)));

            try {
               int y = bodyTop + 4 - this.scrollOffset;
               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
                  y = this.renderFieldByKey(context, "packetOrder", x, y, w, mouseX, mouseY, delta);
                  BlockPos previewPos = null;

                  try {
                     RiptideChatField fx = this.textFields.get("blockPos_0");
                     RiptideChatField fy = this.textFields.get("blockPos_1");
                     RiptideChatField fz = this.textFields.get("blockPos_2");
                     if (fx != null && fy != null && fz != null) {
                        String sx = fx.getText().strip();
                        String sy = fy.getText().strip();
                        String sz = fz.getText().strip();
                        if (!sx.isEmpty() && !sy.isEmpty() && !sz.isEmpty()) {
                           int xPos = Integer.parseInt(sx);
                           int yPos = Integer.parseInt(sy);
                           int zPos = Integer.parseInt(sz);
                           previewPos = new BlockPos(xPos, yPos, zPos);
                        }
                     }
                  } catch (NumberFormatException var57) {
                     previewPos = null;
                  }

                  if (previewPos != null) {
                     RiptideInstaBreakRenderer.setTarget(previewPos, -50373);
                  }

                  Identifier placeFont = this.theme.fontFor(UiTone.BODY);
                  UiText.draw(context, this.textRenderer, "Item", placeFont, RiptideColors.textSecondary(), x, y + 2, false);
                  y += 13;
                  this.renderPlaceItemRow(context, x, y, w, mouseX, mouseY, delta);
                  y += 20;
                  y = this.renderEditorHint(context, x, y, w, "Name:any  Slot:exact  None:held");
                  y += 2;
                  this.renderInlineToggle(context, placeFont, "place_waitForItem", "Wait for Item", x, y, w, mouseX, mouseY);
                  y += 18;
                  this.renderInlineToggle(context, placeFont, "place_silentSwitch", "Silent Switch", x, y, w, mouseX, mouseY);
                  y += 18;
               }

               for (FieldDef field : this.schema.fields()) {
                  if (field.type() != FieldType.STRING_LIST
                     && !this.isGuiWaitAfterKey(field.key())
                     && this.isFieldVisible(field)
                     && (
                        this.targetAction == null
                           || this.targetAction.getType() != MacroActionType.PLACE
                           || !"itemName".equals(field.key()) && !"packetOrder".equals(field.key())
                     )) {
                     this.renderRow(context, field, x, y, w, mouseX, mouseY, delta);
                     y += this.rowH(field) + 2;
                  }
               }

               for (FieldDef fieldx : this.schema.fields()) {
                  if (fieldx.type() == FieldType.STRING_LIST
                     && !this.isGuiWaitAfterKey(fieldx.key())
                     && this.isFieldVisible(fieldx)
                     && (
                        this.targetAction == null
                           || this.targetAction.getType() != MacroActionType.PLACE
                           || !"itemName".equals(fieldx.key()) && !"packetOrder".equals(fieldx.key())
                     )) {
                     this.renderRow(context, fieldx, x, y, w, mouseX, mouseY, delta);
                     y += this.rowH(fieldx) + 2;
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PACKET) {
                  this.renderPacketActionButtons(context, x, y, w, mouseX, mouseY);
                  y += 52;
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SEND_PACKET) {
                  this.renderSendPacketButtons(context, x, y, w, mouseX, mouseY);
                  y += 52;
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DELAY_PACKETS) {
                  this.renderDelayPacketsPresetButtons(context, x, y, w, mouseX, mouseY);
               }
            } finally {
               UiScissorStack.global().pop(context);
            }

            if (needsScroll) {
               int sbX = this.panelX + this.panelW - 5 - 1;
               this.drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, this.scrollOffset, mouseX, mouseY);
               this.scrollDragRegions.add(new ActionEditorOverlay.ScrollDragRegion(sbX, bodyTop, 5, contentAreaH, my -> {
                  int rel = Math.max(0, Math.min(contentAreaH, my - bodyTop));
                  this.scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, contentAreaH)));
               }));
            }
         }
      } else {
         int x = this.panelX + 4;
         int w = this.panelW - 8;
         this.renderOpenContainerPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
      }

      this.renderFooter(context, bodyBtm - 32, mouseX, mouseY);
   }

   private void renderItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      int headerBtnW = 44;
      UiText.draw(
         ctx, this.textRenderer, "Items / Exact Slots (" + this.itemAction.itemNames.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false
      );
      boolean canClearAll = !this.itemAction.itemNames.isEmpty();
      int clearX = x + w - headerBtnW;
      this.renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClearAll, mx, my, () -> {
         this.itemAction.itemNames.clear();
         this.itemAction.itemTimes.clear();
         this.itemAction.itemActionIdx.clear();
         this.itemAction.itemButtons.clear();
         this.itemAction.itemSearchScopes.clear();
         this.itemAction.stackAmountModes.clear();
         this.clearItemEditSelection();
         this.rebuildItemFields();
      });
      cy += 14;
      int selAreaH = 60;
      int sbX = x + w - 5;
      DirectScrollViewport itemViewport = this.getOrCreateViewport(this.selectedScrollViewports, "_item_entries", x, cy, w, selAreaH, 15, 5);
      itemViewport.setContentHeight(this.itemAction.itemNames.size() * 15);
      itemViewport.renderScrollbar(ctx, mx, my);
      RiptideDropAction[] ACTIONS = RiptideDropAction.values();
      int sbGap = 7;
      int delW = 13;
      int itemW = w - sbGap - delW - 2;
      if (!this.itemAction.itemNames.isEmpty()) {
         itemViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstVis = itemViewport.getFirstVisibleRow();
         int iy0 = cy - itemViewport.getScrollOffset() % 15;

         for (int i = firstVis; i < this.itemAction.itemNames.size() && i <= itemViewport.getLastVisibleRow(); i++) {
            int iy = itemViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               String entry = this.itemAction.itemNames.get(i);
               int eiActIdx = this.itemAction.getItemActionIdx(i);
               RiptideDropAction eiAct = ACTIONS[eiActIdx];
               int eiBtn = this.itemAction.getItemButton(i);
               boolean selected = i == this.itemEditIndex;
               ItemTarget entryTarget = this.targetAt(this.itemAction.itemTargets, this.itemAction.itemNames, i);
               Component displayName = this.formatItemTargetText(entryTarget, entry);
               boolean rowHovered = mx >= x && mx < x + itemW && my >= iy && my < iy + 13;
               String summaryAct = eiAct.shortName;
               String var10000;
               if (eiAct == RiptideDropAction.SWAP) {
                  var10000 = "H" + Math.max(1, Math.min(9, eiBtn + 1));
               } else if (eiAct == RiptideDropAction.PICKUP_ALL) {
                  var10000 = "";
               } else {
                  switch (eiBtn) {
                     case 1:
                        var10000 = "R";
                        break;
                     case 2:
                        var10000 = "M";
                        break;
                     default:
                        var10000 = "L";
                  }
               }

               String summaryBtn = var10000;
               int times = this.itemAction.getItemTime(i);
               String summary = " • " + summaryAct + (summaryBtn.isBlank() ? "" : " " + summaryBtn) + (times != 1 ? " ×" + times : "");
               Component rowLabel = Component.empty().append(displayName).append(Component.literal(summary).withStyle(s -> s.withColor(-7829368)));
               MacroTypedListControl.renderRow(
                  ctx, this.textRenderer, rowLabel, UiBounds.of(x, iy, itemW, 13), rowHovered, selected, CompactListRenderer.RowTone.NORMAL, true
               );
               if (iy + 13 > cy && iy < cy + selAreaH) {
                  int fi = i;
                  this.hitRegions
                     .add(
                        new ActionEditorOverlay.HitRegion(
                           x, Math.max(cy, iy), itemW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> this.toggleItemEditSelection(fi)
                        )
                     );
               }

               if (iy + 13 > cy && iy < cy + selAreaH) {
                  int delX = x + itemW + 2;
                  int fi = i;
                  this.renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                     this.itemAction.itemNames.remove(fi);
                     if (fi < this.itemAction.itemTargets.size()) {
                        this.itemAction.itemTargets.remove(fi);
                     }

                     if (fi < this.itemAction.itemTimes.size()) {
                        this.itemAction.itemTimes.remove(fi);
                     }

                     if (fi < this.itemAction.itemActionIdx.size()) {
                        this.itemAction.itemActionIdx.remove(fi);
                     }

                     if (fi < this.itemAction.itemButtons.size()) {
                        this.itemAction.itemButtons.remove(fi);
                     }

                     if (fi < this.itemAction.itemSearchScopes.size()) {
                        this.itemAction.itemSearchScopes.remove(fi);
                     }

                     if (fi < this.itemAction.stackAmountModes.size()) {
                        this.itemAction.stackAmountModes.remove(fi);
                     }

                     if (this.itemEditIndex == fi) {
                        this.clearItemEditSelection();
                     } else if (this.itemEditIndex > fi) {
                        this.itemEditIndex--;
                     }

                     this.rebuildItemFields();
                     DirectScrollViewport vp = this.selectedScrollViewports.get("_item_entries");
                     if (vp != null) {
                        vp.scrollBy(-1);
                     }
                  });
               }
            }
         }

         itemViewport.endRender(ctx);
      } else {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No rows yet. Add an item or exact slot.", x, cy, w - 5 - 1);
      }

      cy += selAreaH;
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      int addPickW = 32;
      int addBtnW = 34;
      int slotW = 26;
      RiptideChatField addF = this.addFields.get("_item_add");
      RiptideChatField entrySlotF = this.textFields.get("item_entrySlot");
      if (addF != null && entrySlotF != null) {
         int pickX = x + w - addBtnW - 3 - addPickW;
         int plusX = x + w - addBtnW;
         int slotX = pickX - 3 - slotW;
         addF.setX(x);
         addF.setY(cy + 1);
         addF.setWidth(slotX - x - 2);
         addF.render(ctx, mx, my, delta);
         entrySlotF.setX(slotX);
         entrySlotF.setY(cy + 1);
         entrySlotF.setWidth(slotW);
         entrySlotF.render(ctx, mx, my, delta);
         boolean capturing = this.captureSession.isItemSlotCapture("_item_entries");
         this.renderFieldCaptureButton(
            ctx, pickX, cy, addPickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("_item_entries")
         );
         String addLbl = this.itemEditIndex >= 0 ? "New" : "+Add";
         this.renderOverlayButton(ctx, plusX, cy, addBtnW, 14, addLbl, CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
            if (this.itemEditIndex >= 0) {
               this.clearItemEditSelection();
            } else {
               this.applyItemEntryEditor();
            }
         });
      }

      cy += 16;
      cy += 4;
      if (this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
         int eiActIdxx = this.itemAction.getItemActionIdx(this.itemEditIndex);
         RiptideDropAction eiActx = ACTIONS[eiActIdxx];
         int eiBtnx = this.itemAction.getItemButton(this.itemEditIndex);
         boolean btnActive = eiActx == RiptideDropAction.PICKUP || eiActx == RiptideDropAction.QUICK_MOVE || eiActx == RiptideDropAction.SWAP;
         int editIdx = this.itemEditIndex;
         int editActW = 54;
         int editBtnW = 28;
         int editTimesW = 28;
         int editGap = 3;
         List<String> actOptions = new ArrayList<>();

         for (RiptideDropAction a : ItemAction.itemClickActions()) {
            actOptions.add(a.shortName);
         }

         this.renderInlineDropdown(
            "item_action",
            actOptions,
            this.itemAction.getItemClickActionOptionIndex(editIdx),
            x,
            cy,
            editActW,
            14,
            idx -> this.itemAction.setItemActionByOptionIndex(editIdx, idx)
         );
         String var79;
         if (eiActx == RiptideDropAction.SWAP) {
            var79 = "H" + Math.max(1, Math.min(9, eiBtnx + 1));
         } else if (eiActx == RiptideDropAction.PICKUP_ALL) {
            var79 = "All";
         } else {
            switch (eiBtnx) {
               case 1:
                  var79 = "R";
                  break;
               case 2:
                  var79 = "M";
                  break;
               default:
                  var79 = "L";
            }
         }

         String btnLbl = var79;
         if (this.itemAction.itemButtonOptionCount(editIdx) > 2) {
            this.renderInlineDropdown(
               "item_button",
               this.itemAction.itemButtonOptions(editIdx),
               this.itemAction.getItemButton(editIdx),
               x + editActW + editGap,
               cy,
               editBtnW,
               14,
               idx -> this.itemAction.setItemButton(editIdx, idx)
            );
         } else {
            this.renderOverlayButton(
               ctx,
               x + editActW + editGap,
               cy,
               editBtnW,
               14,
               btnLbl,
               eiActx == RiptideDropAction.SWAP ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
               btnActive,
               mx,
               my,
               () -> this.itemAction.cycleItemButton(editIdx),
               () -> this.itemAction.cycleItemButtonBackwards(editIdx)
            );
         }

         RiptideChatField tf = this.textFields.get("item_times_" + this.itemEditIndex);
         if (tf != null) {
            tf.setX(x + editActW + editGap + editBtnW + editGap);
            tf.setY(cy + 1);
            tf.setWidth(editTimesW);
            tf.render(ctx, mx, my, delta);
         }

         cy += 16;
         if (this.isEditingNameOnlyItemTargetingAction()) {
            this.renderItemTargetingControls(ctx, x, cy, w, mx, my);
            cy += 17;
         }
      }

      this.renderGuiWaitRow(ctx, font, "item_waitForGuiBefore", "item_waitForGuiAfter", x, cy, w, mx, my);
      cy += 17;
      if (!this.toggleStates.getOrDefault("item_waitForGuiBefore", false) && !this.toggleStates.getOrDefault("item_waitForGuiAfter", false)) {
         RiptideChatField guiF = this.textFields.get("item_guiName");
         if (guiF != null) {
            guiF.setX(-1000);
         }
      } else {
         RiptideChatField guiF = this.textFields.get("item_guiName");
         if (guiF != null) {
            int lw = this.labelWidth(w, "GUI Name", font);
            this.drawLabel(ctx, "GUI Name", x, cy, lw, font);
            guiF.setX(this.controlX(x, lw));
            guiF.setY(cy + 2);
            guiF.setWidth(this.controlWidth(w, lw));
            guiF.render(ctx, mx, my, delta);
         }

         cy += 17;
      }

      this.renderInlineToggle(ctx, font, "item_waitForItem", "Wait for Item", x, cy, w, mx, my);
      cy += 17;
      this.renderInlineToggle(ctx, font, "item_useClickDelay", "Delay Clicks", x, cy, w, mx, my);
      cy += 17;
      if (this.toggleStates.getOrDefault("item_useClickDelay", false)) {
         RiptideChatField delayF = this.textFields.get("item_clickDelayMs");
         if (delayF != null) {
            int lw = this.labelWidth(w, "Delay (ms)", font);
            this.drawLabel(ctx, "Delay (ms)", x, cy, lw, font);
            delayF.setX(this.controlX(x, lw));
            delayF.setY(cy + 2);
            delayF.setWidth(this.controlWidth(w, lw));
            delayF.render(ctx, mx, my, delta);
         }

         cy += 17;
      } else {
         RiptideChatField delayF = this.textFields.get("item_clickDelayMs");
         if (delayF != null) {
            delayF.setX(-1000);
         }
      }
   }

   private void renderItemTargetingControls(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      if (this.itemAction != null && this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
         int gap = 4;
         int invW = Math.max(68, (w - gap) / 2);
         int stackW = Math.max(72, w - invW - gap);
         int stackX = x + invW + gap;
         ItemAction.ItemSearchScope scope = this.itemAction.getItemSearchScope(this.itemEditIndex);
         ItemAction.ItemSearchScope[] scopes = ItemAction.ItemSearchScope.values();
         List<String> scopeOptions = new ArrayList<>();

         for (ItemAction.ItemSearchScope s : scopes) {
            scopeOptions.add(s.label);
         }

         this.renderInlineDropdown(
            "item_scope",
            scopeOptions,
            scope.ordinal(),
            x,
            y + 2,
            invW,
            14,
            idx -> this.itemAction.setItemSearchScope(this.itemEditIndex, scopes[Math.max(0, Math.min(idx, scopes.length - 1))])
         );
         ItemAction.StackAmountMode mode = this.itemAction.getStackAmountMode(this.itemEditIndex);
         ItemAction.StackAmountMode[] stackModes = ItemAction.StackAmountMode.values();
         List<String> stackOptions = new ArrayList<>();

         for (ItemAction.StackAmountMode m : stackModes) {
            stackOptions.add("Stack: " + m.label);
         }

         this.renderInlineDropdown("item_stack", stackOptions, mode.ordinal(), stackX, y + 2, stackW, 14, idx -> {
            while (this.itemAction.stackAmountModes.size() <= this.itemEditIndex) {
               this.itemAction.stackAmountModes.add(ItemAction.StackAmountMode.DEFAULT.ordinal());
            }

            this.itemAction.stackAmountModes.set(this.itemEditIndex, stackModes[Math.max(0, Math.min(idx, stackModes.length - 1))].ordinal());
         });
      }
   }

   private boolean isEditingNameOnlyItemTargetingAction() {
      if (this.itemAction != null && this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
         ItemTarget target = this.targetAt(this.itemAction.itemTargets, this.itemAction.itemNames, this.itemEditIndex);
         return target.hasIdentity() && !target.hasSlot()
            ? this.itemAction.getItemAction(this.itemEditIndex) != RiptideDropAction.PICKUP_ALL
               || !this.toggleStates.getOrDefault("item_useCursorItemForPickupAll", this.itemAction.useCursorItemForPickupAll)
            : false;
      } else {
         return false;
      }
   }

   private void addItemEntry(ItemTarget target) {
      this.addTargetEntry(this.itemAction.itemTargets, this.itemAction.itemNames, target);

      while (this.itemAction.itemTimes.size() < this.itemAction.itemNames.size()) {
         this.itemAction.itemTimes.add(1);
      }

      while (this.itemAction.itemActionIdx.size() < this.itemAction.itemNames.size()) {
         this.itemAction.itemActionIdx.add(0);
      }

      while (this.itemAction.itemButtons.size() < this.itemAction.itemNames.size()) {
         this.itemAction.itemButtons.add(0);
      }

      while (this.itemAction.itemSearchScopes.size() < this.itemAction.itemNames.size()) {
         this.itemAction.itemSearchScopes.add(0);
      }

      while (this.itemAction.stackAmountModes.size() < this.itemAction.itemNames.size()) {
         this.itemAction.stackAmountModes.add(0);
      }

      int idx = this.itemAction.itemNames.size() - 1;
      RiptideChatField f = this.makeField(28);
      f.setNumericOnly(true);
      f.setText("1");
      this.textFields.put("item_times_" + idx, f);
   }

   private void rebuildItemFields() {
      this.textFields.keySet().removeIf(k -> k.startsWith("item_times_"));
      this.trimTargetEntries(this.itemAction.itemTargets, this.itemAction.itemNames.size());

      while (this.itemAction.itemTimes.size() > this.itemAction.itemNames.size()) {
         this.itemAction.itemTimes.remove(this.itemAction.itemTimes.size() - 1);
      }

      while (this.itemAction.itemActionIdx.size() > this.itemAction.itemNames.size()) {
         this.itemAction.itemActionIdx.remove(this.itemAction.itemActionIdx.size() - 1);
      }

      while (this.itemAction.itemButtons.size() > this.itemAction.itemNames.size()) {
         this.itemAction.itemButtons.remove(this.itemAction.itemButtons.size() - 1);
      }

      while (this.itemAction.itemSearchScopes.size() > this.itemAction.itemNames.size()) {
         this.itemAction.itemSearchScopes.remove(this.itemAction.itemSearchScopes.size() - 1);
      }

      while (this.itemAction.stackAmountModes.size() > this.itemAction.itemNames.size()) {
         this.itemAction.stackAmountModes.remove(this.itemAction.stackAmountModes.size() - 1);
      }

      while (this.itemAction.itemSearchScopes.size() < this.itemAction.itemNames.size()) {
         this.itemAction.itemSearchScopes.add(0);
      }

      while (this.itemAction.stackAmountModes.size() < this.itemAction.itemNames.size()) {
         this.itemAction.stackAmountModes.add(0);
      }

      for (int i = 0; i < this.itemAction.itemNames.size(); i++) {
         RiptideChatField f = this.makeField(28);
         f.setNumericOnly(true);
         f.setText(String.valueOf(this.itemAction.getItemTime(i)));
         this.textFields.put("item_times_" + i, f);
      }
   }

   private void toggleItemEditSelection(int index) {
      if (this.itemEditIndex == index) {
         this.clearItemEditSelection();
      } else {
         this.itemEditIndex = index;
         this.syncItemEntryEditorFromSelection();
      }
   }

   private void clearItemEditSelection() {
      this.itemEditIndex = -1;
      RiptideChatField addF = this.addFields.get("_item_add");
      this.suppressItemEntryLiveUpdate = true;
      if (addF != null) {
         addF.setText("");
      }

      RiptideChatField slotF = this.textFields.get("item_entrySlot");
      if (slotF != null) {
         slotF.setText("");
      }

      this.suppressItemEntryLiveUpdate = false;
   }

   private void syncItemEntryEditorFromSelection() {
      RiptideChatField addF = this.addFields.get("_item_add");
      RiptideChatField slotF = this.textFields.get("item_entrySlot");
      if (addF != null && slotF != null) {
         this.suppressItemEntryLiveUpdate = true;
         if (this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
            ItemTarget target = this.targetAt(this.itemAction.itemTargets, this.itemAction.itemNames, this.itemEditIndex);
            addF.setText(target.editorText());
            slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
            this.suppressItemEntryLiveUpdate = false;
         } else {
            addF.setText("");
            slotF.setText("");
            this.suppressItemEntryLiveUpdate = false;
         }
      }
   }

   private void applyItemEntryEditor() {
      this.applyItemEntryEditor(false);
   }

   private void handleItemEntryEditorChanged() {
      if (!this.suppressItemEntryLiveUpdate && this.itemAction != null) {
         if (this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
            this.applyItemEntryEditor(true);
         }
      }
   }

   private void applyItemEntryEditor(boolean preserveSelection) {
      if (this.itemAction != null) {
         RiptideChatField addF = this.addFields.get("_item_add");
         RiptideChatField slotF = this.textFields.get("item_entrySlot");
         if (addF != null && slotF != null) {
            String nameText = addF.getText().strip();
            String slotText = slotF.getText().strip();
            ItemTarget target = this.buildEntryTargetFromEditor(
               nameText, slotText, this.targetAt(this.itemAction.itemTargets, this.itemAction.itemNames, this.itemEditIndex)
            );
            String entry = target.toLegacyEntry();
            if (entry != null && !entry.isBlank()) {
               if (this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
                  if (!this.containsEntryOtherThan(this.itemAction.itemNames, entry, this.itemEditIndex)) {
                     this.setTargetAt(this.itemAction.itemTargets, this.itemAction.itemNames, this.itemEditIndex, target);
                  }
               } else if (!this.itemAction.itemNames.contains(entry)) {
                  this.addItemEntry(target);
               }

               if (!preserveSelection) {
                  this.clearItemEditSelection();
               }
            }
         }
      }
   }

   private void applyCapturedItemEntry(ItemTarget rawTarget) {
      if (this.itemAction != null) {
         ItemTarget target = storeCaptureTarget(rawTarget);
         String entry = target == null ? "" : target.toLegacyEntry();
         if (!entry.isBlank()) {
            int targetIndex = this.itemEditIndex;
            if (targetIndex < 0 || targetIndex >= this.itemAction.itemNames.size()) {
               targetIndex = this.itemAction.itemNames.indexOf(entry);
               if (targetIndex < 0) {
                  this.addItemEntry(target.copy());
                  targetIndex = this.itemAction.itemNames.size() - 1;
               }
            } else if (!this.containsEntryOtherThan(this.itemAction.itemNames, entry, targetIndex)) {
               this.setTargetAt(this.itemAction.itemTargets, this.itemAction.itemNames, targetIndex, target.copy());
            }

            this.itemEditIndex = targetIndex;
            RiptideChatField addF = this.addFields.get("_item_add");
            if (addF != null) {
               addF.setText(target.editorText());
            }
         }
      }
   }

   private void renderInlineToggle(GuiGraphicsExtractor ctx, Identifier font, String stateKey, String label, int x, int y, int w, int mx, int my) {
      boolean val = this.toggleStates.getOrDefault(stateKey, false);
      int lw = this.labelWidth(w, label, font, 34);
      this.drawLabel(ctx, label, x, y, lw, font);
      int btnW = 34;
      int btnH = 14;
      int btnX = x + w - btnW;
      this.renderOverlayToggleButton(
         ctx,
         btnX,
         y + 2,
         btnW,
         btnH,
         val ? "ON" : "OFF",
         val,
         "macro-inline:" + stateKey,
         mx,
         my,
         () -> this.toggleStates.put(stateKey, !this.toggleStates.getOrDefault(stateKey, false))
      );
   }

   private void renderGuiWaitRow(GuiGraphicsExtractor ctx, Identifier font, String beforeKey, String afterKey, int x, int y, int w, int mx, int my) {
      this.drawLabel(ctx, "GUI Wait", x, y, this.labelWidth(w, "GUI Wait", font, 90), font);
      int btnW = 42;
      int gap = 4;
      int afterX = x + w - btnW;
      int beforeX = afterX - gap - btnW;
      this.renderGuiWaitButton(ctx, beforeKey, "Before", beforeX, y + 2, btnW, mx, my);
      this.renderGuiWaitButton(ctx, afterKey, "After", afterX, y + 2, btnW, mx, my);
   }

   private void renderGuiWaitButton(GuiGraphicsExtractor ctx, String key, String label, int x, int y, int w, int mx, int my) {
      boolean val = this.toggleStates.getOrDefault(key, false);
      this.renderOverlayToggleButton(
         ctx, x, y, w, 14, label, val, "macro-gui-wait:" + key, mx, my, () -> this.toggleStates.put(key, !this.toggleStates.getOrDefault(key, false))
      );
   }

   private void renderCraftPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int removeW = 13;
      int maxTogW = 42;
      int amountW = 44;
      int headerBtnW = 44;
      int cy = bodyTop + 4;
      UiText.draw(ctx, this.textRenderer, "Craft Entries (" + this.craftEntries.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      boolean canClearEntries = !this.craftEntries.isEmpty();
      this.renderOverlayButton(ctx, x + w - headerBtnW, cy, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClearEntries, mx, my, () -> {
         this.craftEntries.clear();
         this.rebuildCraftFields();
         DirectScrollViewport craftVp = this.selectedScrollViewports.get("_craft_entries");
         if (craftVp != null) {
            craftVp.jumpTo(0);
         }
      });
      cy += 14;
      int selAreaH = 60;
      DirectScrollViewport craftViewport = this.getOrCreateViewport(this.selectedScrollViewports, "_craft_entries", x, cy, w, selAreaH, 15, 5);
      craftViewport.setContentHeight(this.craftEntries.size() * 15);
      craftViewport.renderScrollbar(ctx, mx, my);
      int sbGap = 7;
      if (!this.craftEntries.isEmpty()) {
         craftViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstSel = craftViewport.getFirstVisibleRow();

         for (int i = firstSel; i < this.craftEntries.size() && i <= craftViewport.getLastVisibleRow(); i++) {
            int siy = craftViewport.getRowScreenY(i);
            if (siy != Integer.MIN_VALUE) {
               CraftAction.CraftEntry entry = this.craftEntries.get(i);
               boolean useMax = this.toggleStates.getOrDefault("craft_useMax_" + i, false);
               int removeX = x + w - sbGap - removeW;
               int fi = i;
               this.renderIconDeleteButton(ctx, removeX, siy + 1, removeW, mx, my, () -> {
                  this.craftEntries.remove(fi);
                  this.rebuildCraftFields();
                  DirectScrollViewport vp = this.selectedScrollViewports.get("_craft_entries");
                  if (vp != null) {
                     vp.scrollBy(-1);
                  }
               });
               int maxX = removeX - 3 - maxTogW;
               String mLbl = useMax ? "Max: On" : "Max: Off";
               this.renderOverlayToggleButton(
                  ctx,
                  maxX,
                  siy + 1,
                  maxTogW,
                  13,
                  mLbl,
                  useMax,
                  "macro-craft-use-max:" + i,
                  mx,
                  my,
                  () -> this.toggleStates.put("craft_useMax_" + i, !this.toggleStates.getOrDefault("craft_useMax_" + i, false))
               );
               if (!useMax) {
                  int amtX = maxX - 3 - amountW;
                  RiptideChatField af = this.textFields.get("craft_amount_" + i);
                  if (af != null) {
                     af.setX(amtX);
                     af.setY(siy + 1);
                     af.setWidth(amountW);
                     af.render(ctx, mx, my, delta);
                  }
               }

               int nameW = useMax ? maxX - 3 - x : maxX - 3 - amountW - 3 - x;
               boolean rowHovered = mx >= x && mx < x + nameW && my >= siy && my < siy + 13;
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  entry.resultNameComponent(),
                  UiBounds.of(x, siy, Math.max(1, nameW), 13),
                  rowHovered,
                  false,
                  useMax ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                  true
               );
            }
         }

         craftViewport.endRender(ctx);
      } else {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No craft entries yet.", x, cy, w - 5 - 1);
      }

      cy += selAreaH;
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      int refreshW = 44;
      RiptideChatField srchF = this.addFields.get("_craft_search");
      if (srchF != null) {
         srchF.setX(x);
         srchF.setY(cy);
         srchF.setWidth(w - refreshW - 3);
         srchF.render(ctx, mx, my, delta);
      }

      int rfX = x + w - refreshW;
      this.renderOverlayButton(ctx, rfX, cy, refreshW, 14, "Reload", CompactOverlayButton.Variant.SECONDARY, true, mx, my, this::refreshCraftRecipes);
      cy += 16;
      String query = srchF != null ? srchF.getText() : "";
      if (!query.equals(this.craftLastQuery)) {
         this.craftFilteredRecipes = RiptideCraftingHelper.filterRecipes(this.craftAllRecipes != null ? this.craftAllRecipes : List.of(), query);
         this.craftLastQuery = query;
         this.craftRecipeScrollOffset = 0;
         if (this.craftSelectedRecipe != null && (this.craftFilteredRecipes == null || !this.craftFilteredRecipes.contains(this.craftSelectedRecipe))) {
            this.craftSelectedRecipe = null;
         }
      }

      List<RiptideCraftingHelper.CraftableRecipeOption> filtered = this.craftFilteredRecipes != null ? this.craftFilteredRecipes : List.of();
      int recipeListH = 52;
      DirectScrollViewport recipeViewport = this.getOrCreateViewport(this.catalogScrollViewports, "_craft_recipe_browser", x, cy, w, recipeListH, 13, 5);
      recipeViewport.setContentHeight(filtered.size() * 13);
      this.craftRecipeListBounds = new int[]{cy, recipeListH};
      recipeViewport.renderScrollbar(ctx, mx, my);
      int recW = w - 5 - 1;
      if (filtered.isEmpty()) {
         CompactListRenderer.drawEmptyState(
            ctx,
            this.textRenderer,
            this.craftAllRecipes != null && !this.craftAllRecipes.isEmpty() ? "No recipes match the search." : "No crafting recipes loaded. Use Reload.",
            x,
            cy,
            recW
         );
      } else {
         recipeViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstRec = recipeViewport.getFirstVisibleRow();

         for (int ix = firstRec; ix < filtered.size() && ix <= recipeViewport.getLastVisibleRow(); ix++) {
            int ry = recipeViewport.getRowScreenY(ix);
            if (ry != Integer.MIN_VALUE) {
               RiptideCraftingHelper.CraftableRecipeOption opt = filtered.get(ix);
               boolean inList = this.craftEntries
                  .stream()
                  .anyMatch(e -> opt.recipeKey != null && opt.recipeKey.equals(e.recipeKey) || opt.recipeId >= 0 && opt.recipeId == e.recipeId);
               boolean hov = !inList && mx >= x && mx < x + recW && my >= ry && my < ry + 13;
               CompactListRenderer.RowTone tone = inList
                  ? CompactListRenderer.RowTone.READY
                  : (opt.craftableNow ? CompactListRenderer.RowTone.NORMAL : CompactListRenderer.RowTone.MISSING);
               MacroTypedListControl.renderRow(ctx, this.textRenderer, opt.labelComponent, UiBounds.of(x, ry, recW, 13), hov, inList, tone, true);
               if (opt.result != null && opt.result.getCount() > 1) {
                  String cnt = "×" + opt.result.getCount();
                  UiText.draw(ctx, this.textRenderer, cnt, font, RiptideColors.textDim(), x + recW - this.uiWidth(font, cnt) - 2, ry + 2, false);
               }

               this.hitRegions
                  .add(
                     new ActionEditorOverlay.HitRegion(
                        x,
                        ry,
                        recW,
                        13,
                        () -> {
                           if (inList) {
                              this.craftEntries
                                 .removeIf(e -> opt.recipeKey != null && opt.recipeKey.equals(e.recipeKey) || opt.recipeId >= 0 && opt.recipeId == e.recipeId);
                              this.rebuildCraftFields();
                           } else {
                              CraftAction.CraftEntry newEntry = CraftAction.CraftEntry.fromOption(opt, 1, false);
                              this.craftEntries.add(newEntry);
                              int idx = this.craftEntries.size() - 1;
                              RiptideChatField f = this.makeField(44);
                              f.setNumericOnly(true);
                              f.setText("1");
                              this.textFields.put("craft_amount_" + idx, f);
                              this.toggleStates.put("craft_useMax_" + idx, false);
                           }
                        }
                     )
                  );
            }
         }

         recipeViewport.endRender(ctx);
      }
   }

   private void refreshCraftRecipes() {
      this.craftAllRecipes = RiptideCraftingHelper.getCraftableRecipes(MC);
      this.craftLastQuery = null;
      this.craftSelectedRecipe = null;
      this.craftRecipeScrollOffset = 0;
   }

   private void addCraftEntry() {
      if (this.craftSelectedRecipe != null) {
         RiptideChatField amtF = this.textFields.get("_craft_amount");
         int amount = 1;
         if (amtF != null && !this.craftUseMax) {
            try {
               amount = Math.max(1, Integer.parseInt(amtF.getText().strip()));
            } catch (NumberFormatException var7) {
            }
         }

         CraftAction.CraftEntry newEntry = CraftAction.CraftEntry.fromOption(this.craftSelectedRecipe, amount, this.craftUseMax);

         for (int i = 0; i < this.craftEntries.size(); i++) {
            CraftAction.CraftEntry existing = this.craftEntries.get(i);
            if (newEntry.recipeKey != null && newEntry.recipeKey.equals(existing.recipeKey) || newEntry.recipeId >= 0 && newEntry.recipeId == existing.recipeId
               )
             {
               existing.amount = newEntry.amount;
               existing.useMaxAmount = newEntry.useMaxAmount;
               this.toggleStates.put("craft_useMax_" + i, newEntry.useMaxAmount);
               RiptideChatField f = this.textFields.get("craft_amount_" + i);
               if (f != null) {
                  f.setText(String.valueOf(newEntry.amount));
               }

               return;
            }
         }

         this.craftEntries.add(newEntry);
         int idx = this.craftEntries.size() - 1;
         RiptideChatField f = this.makeField(44);
         f.setNumericOnly(true);
         f.setText(String.valueOf(newEntry.amount));
         this.textFields.put("craft_amount_" + idx, f);
         this.toggleStates.put("craft_useMax_" + idx, newEntry.useMaxAmount);
      }
   }

   private void rebuildCraftFields() {
      this.textFields.keySet().removeIf(k -> k.startsWith("craft_amount_"));

      for (int i = 0; i < this.craftEntries.size(); i++) {
         CraftAction.CraftEntry entry = this.craftEntries.get(i);
         RiptideChatField f = this.makeField(44);
         f.setNumericOnly(true);
         f.setText(String.valueOf(entry.amount));
         this.textFields.put("craft_amount_" + i, f);
         if (!this.toggleStates.containsKey("craft_useMax_" + i)) {
            this.toggleStates.put("craft_useMax_" + i, entry.useMaxAmount);
         }
      }

      this.toggleStates.keySet().removeIf(k -> {
         if (!k.startsWith("craft_useMax_")) {
            return false;
         } else {
            try {
               return Integer.parseInt(k.substring("craft_useMax_".length())) >= this.craftEntries.size();
            } catch (NumberFormatException var3x) {
               return true;
            }
         }
      });
   }

   private void renderDropPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      int headerBtnW = 44;
      UiText.draw(ctx, this.textRenderer, "Drop Entries (" + this.dropAction.itemNames.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      boolean canClearAll = !this.dropAction.itemNames.isEmpty();
      int clearX = x + w - headerBtnW;
      this.renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClearAll, mx, my, () -> {
         this.dropAction.itemNames.clear();
         this.dropAction.itemTargets.clear();
         this.dropAction.itemCounts.clear();
         this.clearDropEditSelection();
         this.rebuildDropFields();
      });
      cy += 14;
      int selAreaH = 60;
      DirectScrollViewport dropViewport = this.getOrCreateViewport(this.selectedScrollViewports, "_drop_entries", x, cy, w, selAreaH, 15, 5);
      dropViewport.setContentHeight(this.dropAction.itemNames.size() * 15);
      dropViewport.renderScrollbar(ctx, mx, my);
      int sbGap = 7;
      int delW = 13;
      int dropItemW = w - sbGap - delW - 2;
      if (!this.dropAction.itemNames.isEmpty()) {
         dropViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstVis = dropViewport.getFirstVisibleRow();

         for (int i = firstVis; i < this.dropAction.itemNames.size() && i <= dropViewport.getLastVisibleRow(); i++) {
            int iy = dropViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               String entry = this.dropAction.itemNames.get(i);
               boolean selected = i == this.dropEditIndex;
               ItemTarget entryTarget = this.targetAt(this.dropAction.itemTargets, this.dropAction.itemNames, i);
               Component displayName = this.formatItemTargetText(entryTarget, entry);
               int cnt = i < this.dropAction.itemCounts.size() ? this.dropAction.itemCounts.get(i) : 0;
               String summary = " • " + (cnt == 0 ? "all" : "×" + cnt);
               Component rowLabel = Component.empty().append(displayName).append(Component.literal(summary).withStyle(s -> s.withColor(-7829368)));
               boolean rowHovered = mx >= x && mx < x + dropItemW && my >= iy && my < iy + 13;
               MacroTypedListControl.renderRow(
                  ctx, this.textRenderer, rowLabel, UiBounds.of(x, iy, dropItemW, 13), rowHovered, selected, CompactListRenderer.RowTone.NORMAL, true
               );
               if (iy + 13 > cy && iy < cy + selAreaH) {
                  this.hitRegions
                     .add(
                        new ActionEditorOverlay.HitRegion(
                           x, Math.max(cy, iy), dropItemW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> this.toggleDropEditSelection(i)
                        )
                     );
               }

               if (iy + 13 > cy && iy < cy + selAreaH) {
                  int delX = x + dropItemW + 2;
                  int fi = i;
                  this.renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                     this.dropAction.itemNames.remove(fi);
                     if (fi < this.dropAction.itemTargets.size()) {
                        this.dropAction.itemTargets.remove(fi);
                     }

                     if (fi < this.dropAction.itemCounts.size()) {
                        this.dropAction.itemCounts.remove(fi);
                     }

                     if (this.dropEditIndex == fi) {
                        this.clearDropEditSelection();
                     } else if (this.dropEditIndex > fi) {
                        this.dropEditIndex--;
                     }

                     this.rebuildDropFields();
                     DirectScrollViewport vp = this.selectedScrollViewports.get("_drop_entries");
                     if (vp != null) {
                        vp.scrollBy(-1);
                     }
                  });
               }
            }
         }

         dropViewport.endRender(ctx);
      } else {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "Optional: add items/slots, or use the fallback below.", x, cy, w - 5 - 1);
      }

      cy += selAreaH;
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      int addPickW = 32;
      int addBtnW = 34;
      int slotW = 44;
      RiptideChatField addF = this.addFields.get("_drop_add");
      RiptideChatField entrySlotF = this.textFields.get("drop_entrySlot");
      if (addF != null && entrySlotF != null) {
         int pickX = x + w - addBtnW - 3 - addPickW;
         int plusX = x + w - addBtnW;
         int slotX = pickX - 3 - slotW;
         addF.setX(x);
         addF.setY(cy + 1);
         addF.setWidth(slotX - x - 2);
         addF.render(ctx, mx, my, delta);
         entrySlotF.setX(slotX);
         entrySlotF.setY(cy + 1);
         entrySlotF.setWidth(slotW);
         entrySlotF.render(ctx, mx, my, delta);
         boolean capturing = this.captureSession.isItemSlotCapture("_drop_entries");
         this.renderFieldCaptureButton(
            ctx, pickX, cy, addPickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("_drop_entries")
         );
         String addLbl = this.dropEditIndex >= 0 ? "New" : "+Add";
         this.renderOverlayButton(ctx, plusX, cy, addBtnW, 14, addLbl, CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
            if (this.dropEditIndex >= 0) {
               this.clearDropEditSelection();
            } else {
               this.applyDropEntryEditor();
            }
         });
      }

      cy += 16;
      cy += 4;
      boolean editingSelectedDrop = this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size();
      boolean noDropTargets = this.dropAction.itemNames.isEmpty();
      String dropHint = editingSelectedDrop
         ? "Editing this row. 0 means drop the full stack from that slot or item."
         : (
            noDropTargets
               ? (
                  this.dropAction.mode == DropAction.DropMode.ALL
                     ? "No items selected: Drop All drops the entire main inventory and hotbar."
                     : "No items selected: Times drops the currently held item by the count below."
               )
               : "No row selected. These controls set defaults for newly added rows."
         );
      cy = this.renderEditorHint(ctx, x, cy, w, dropHint);
      boolean dropAllSelected = editingSelectedDrop ? this.getDropEntryCount(this.dropEditIndex) == 0 : this.dropAction.mode == DropAction.DropMode.ALL;
      String modeLabel = editingSelectedDrop ? "Selected Row" : (noDropTargets ? "No Items" : "Default Mode");
      int lw = this.labelWidth(w, modeLabel, font);
      this.drawLabel(ctx, modeLabel, x, cy, lw, font);
      int ctrlX = this.controlX(x, lw);
      int ctrlW = this.controlWidth(w, lw);
      int leftW = (ctrlW - 2) / 2;
      int rightX = ctrlX + leftW + 2;
      int rightW = ctrlW - leftW - 2;
      this.renderOverlayButton(
         ctx,
         ctrlX,
         cy + 2,
         leftW,
         14,
         "Drop All",
         dropAllSelected ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
         true,
         mx,
         my,
         () -> {
            if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
               this.setDropEntryCount(this.dropEditIndex, 0);
            } else {
               this.enumIndices.put("drop_mode", 0);
               this.dropAction.mode = DropAction.DropMode.ALL;
            }

            this.syncDropCountEditorField();
         }
      );
      this.renderOverlayButton(
         ctx,
         rightX,
         cy + 2,
         rightW,
         14,
         "Times",
         !dropAllSelected ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST,
         true,
         mx,
         my,
         () -> {
            if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
               this.setDropEntryCount(this.dropEditIndex, Math.max(1, this.getDropEntryCount(this.dropEditIndex)));
            } else {
               this.enumIndices.put("drop_mode", 1);
               this.dropAction.mode = DropAction.DropMode.TIMES;
            }

            this.syncDropCountEditorField();
         }
      );
      cy += 17;
      RiptideChatField cntF = this.textFields.get("drop_globalCount");
      this.syncDropCountEditorField();
      if (cntF != null) {
         modeLabel = editingSelectedDrop ? "Selected Count" : (noDropTargets ? "Held Count" : "Default Count");
         lw = this.labelWidth(w, modeLabel, font);
         this.drawLabel(ctx, modeLabel, x, cy, lw, font);
         cntF.setX(this.controlX(x, lw));
         cntF.setY(cy + 2);
         cntF.setWidth(this.controlWidth(w, lw));
         cntF.render(ctx, mx, my, delta);
         cy += 17;
      }

      this.renderGuiWaitRow(ctx, font, "drop_waitForGuiBefore", "drop_waitForGuiAfter", x, cy, w, mx, my);
      cy += 17;
      if (!this.toggleStates.getOrDefault("drop_waitForGuiBefore", false) && !this.toggleStates.getOrDefault("drop_waitForGuiAfter", false)) {
         RiptideChatField guiF = this.textFields.get("drop_guiName");
         if (guiF != null) {
            guiF.setX(-1000);
         }
      } else {
         RiptideChatField guiF = this.textFields.get("drop_guiName");
         if (guiF != null) {
            lw = this.labelWidth(w, "GUI Name", font);
            this.drawLabel(ctx, "GUI Name", x, cy, lw, font);
            guiF.setX(this.controlX(x, lw));
            guiF.setY(cy + 2);
            guiF.setWidth(this.controlWidth(w, lw));
            guiF.render(ctx, mx, my, delta);
         }

         cy += 17;
      }
   }

   private void addDropEntry(ItemTarget target, int count) {
      if (target != null) {
         String entry = target.toLegacyEntry();
         if (!entry.isEmpty()) {
            this.addTargetEntry(this.dropAction.itemTargets, this.dropAction.itemNames, target);
            int safeCount = Math.max(0, count);
            this.dropAction.itemCounts.add(safeCount);
            int idx = this.dropAction.itemNames.size() - 1;
            RiptideChatField f = this.makeField(32);
            f.setNumericOnly(true);
            f.setText(String.valueOf(safeCount));
            f.setChangedListener(text -> {
               try {
                  this.setDropEntryCount(idx, Math.max(0, Integer.parseInt(text.strip())));
                  if (idx == this.dropEditIndex) {
                     this.syncDropCountEditorField();
                  }
               } catch (NumberFormatException var4x) {
               }
            });
            this.textFields.put("drop_count_" + idx, f);
         }
      }
   }

   private void rebuildDropFields() {
      this.textFields.keySet().removeIf(k -> k.startsWith("drop_count_"));
      this.trimTargetEntries(this.dropAction.itemTargets, this.dropAction.itemNames.size());

      while (this.dropAction.itemCounts.size() > this.dropAction.itemNames.size()) {
         this.dropAction.itemCounts.remove(this.dropAction.itemCounts.size() - 1);
      }

      for (int i = 0; i < this.dropAction.itemNames.size(); i++) {
         RiptideChatField f = this.makeField(32);
         f.setNumericOnly(true);
         f.setText(String.valueOf(this.dropAction.itemCounts.get(i)));
         int fieldIndex = i;
         f.setChangedListener(text -> {
            try {
               this.setDropEntryCount(fieldIndex, Math.max(0, Integer.parseInt(text.strip())));
               if (fieldIndex == this.dropEditIndex) {
                  this.syncDropCountEditorField();
               }
            } catch (NumberFormatException var4) {
            }
         });
         this.textFields.put("drop_count_" + i, f);
      }
   }

   private void toggleDropEditSelection(int index) {
      if (this.dropEditIndex == index) {
         this.clearDropEditSelection();
      } else {
         this.dropEditIndex = index;
         this.syncDropEntryEditorFromSelection();
         this.syncDropCountEditorField();
      }
   }

   private void clearDropEditSelection() {
      this.dropEditIndex = -1;
      RiptideChatField addF = this.addFields.get("_drop_add");
      this.suppressDropEntryLiveUpdate = true;
      if (addF != null) {
         addF.setText("");
      }

      RiptideChatField slotF = this.textFields.get("drop_entrySlot");
      if (slotF != null) {
         slotF.setText("");
      }

      this.suppressDropEntryLiveUpdate = false;
      this.syncDropCountEditorField();
   }

   private void syncDropEntryEditorFromSelection() {
      RiptideChatField addF = this.addFields.get("_drop_add");
      RiptideChatField slotF = this.textFields.get("drop_entrySlot");
      if (addF != null && slotF != null) {
         this.suppressDropEntryLiveUpdate = true;
         if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
            ItemTarget target = this.targetAt(this.dropAction.itemTargets, this.dropAction.itemNames, this.dropEditIndex);
            addF.setText(target.editorText());
            slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
            this.suppressDropEntryLiveUpdate = false;
            this.syncDropCountEditorField();
         } else {
            addF.setText("");
            slotF.setText("");
            this.suppressDropEntryLiveUpdate = false;
         }
      }
   }

   private void applyDropEntryEditor() {
      this.applyDropEntryEditor(false);
   }

   private void handleDropEntryEditorChanged() {
      if (!this.suppressDropEntryLiveUpdate && this.dropAction != null) {
         if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
            this.applyDropEntryEditor(true);
         }
      }
   }

   private void applyDropEntryEditor(boolean preserveSelection) {
      if (this.dropAction != null) {
         RiptideChatField addF = this.addFields.get("_drop_add");
         RiptideChatField slotF = this.textFields.get("drop_entrySlot");
         if (addF != null && slotF != null) {
            String nameText = addF.getText().strip();
            String slotText = slotF.getText().strip();
            ItemTarget target = this.buildEntryTargetFromEditor(
               nameText, slotText, this.targetAt(this.dropAction.itemTargets, this.dropAction.itemNames, this.dropEditIndex)
            );
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) {
               if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
                  if (!this.containsEntryOtherThan(this.dropAction.itemNames, entry, this.dropEditIndex)) {
                     this.setTargetAt(this.dropAction.itemTargets, this.dropAction.itemNames, this.dropEditIndex, target);
                  }
               } else if (!this.dropAction.itemNames.contains(entry)) {
                  this.addDropEntry(target, this.currentDropEditorCount());
               }

               if (!preserveSelection) {
                  this.clearDropEditSelection();
               }
            }
         }
      }
   }

   private void applyCapturedDropEntry(ItemTarget rawTarget) {
      if (this.dropAction != null) {
         ItemTarget target = storeCaptureTarget(rawTarget);
         String entry = target == null ? "" : target.toLegacyEntry();
         if (!entry.isBlank()) {
            int targetIndex = this.dropEditIndex;
            if (targetIndex < 0 || targetIndex >= this.dropAction.itemNames.size()) {
               targetIndex = this.dropAction.itemNames.indexOf(entry);
               if (targetIndex < 0) {
                  this.addDropEntry(target.copy(), this.currentDropEditorCount());
                  targetIndex = this.dropAction.itemNames.size() - 1;
               }
            } else if (!this.containsEntryOtherThan(this.dropAction.itemNames, entry, targetIndex)) {
               this.setTargetAt(this.dropAction.itemTargets, this.dropAction.itemNames, targetIndex, target.copy());
            }

            this.dropEditIndex = targetIndex;
            this.syncDropEntryEditorFromSelection();
            this.syncDropCountEditorField();
         }
      }
   }

   private int currentDropEditorCount() {
      RiptideChatField globalCountField = this.textFields.get("drop_globalCount");
      if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemCounts.size()) {
         return Math.max(0, this.dropAction.itemCounts.get(this.dropEditIndex));
      } else if (this.enumIndices.getOrDefault("drop_mode", 0) == 0) {
         return 0;
      } else {
         if (globalCountField != null) {
            try {
               return Math.max(0, Integer.parseInt(globalCountField.getText().strip()));
            } catch (NumberFormatException var3) {
            }
         }

         return Math.max(0, this.dropAction.dropCount);
      }
   }

   private int getDropEntryCount(int index) {
      if (this.dropAction != null && index >= 0 && index < this.dropAction.itemNames.size()) {
         while (this.dropAction.itemCounts.size() <= index) {
            this.dropAction.itemCounts.add(1);
         }

         return Math.max(0, this.dropAction.itemCounts.get(index));
      } else {
         return 1;
      }
   }

   private void setDropEntryCount(int index, int count) {
      if (this.dropAction != null && index >= 0 && index < this.dropAction.itemNames.size()) {
         while (this.dropAction.itemCounts.size() <= index) {
            this.dropAction.itemCounts.add(1);
         }

         int safeCount = Math.max(0, count);
         this.dropAction.itemCounts.set(index, safeCount);
         RiptideChatField rowField = this.textFields.get("drop_count_" + index);
         if (rowField != null) {
            rowField.setText(String.valueOf(safeCount));
         }
      }
   }

   private void syncDropCountEditorField() {
      if (this.dropAction != null) {
         RiptideChatField countField = this.textFields.get("drop_globalCount");
         if (countField != null) {
            boolean editingSelectedDrop = this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size();
            boolean dropAll = editingSelectedDrop ? this.getDropEntryCount(this.dropEditIndex) == 0 : this.dropAction.mode == DropAction.DropMode.ALL;
            int displayCount = editingSelectedDrop ? Math.max(1, this.getDropEntryCount(this.dropEditIndex)) : Math.max(1, this.dropAction.dropCount);
            this.suppressDropCountEditorUpdate = true;
            countField.setText(String.valueOf(displayCount));
            countField.setEditable(!dropAll);
            this.suppressDropCountEditorUpdate = false;
         }
      }
   }

   private void handleDropCountEditorChanged() {
      if (!this.suppressDropCountEditorUpdate && this.dropAction != null) {
         RiptideChatField countField = this.textFields.get("drop_globalCount");
         if (countField != null) {
            boolean editingSelectedDrop = this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size();
            boolean dropAll = editingSelectedDrop ? this.getDropEntryCount(this.dropEditIndex) == 0 : this.dropAction.mode == DropAction.DropMode.ALL;
            if (!dropAll) {
               try {
                  int value = Math.max(1, Integer.parseInt(countField.getText().strip()));
                  if (editingSelectedDrop) {
                     this.setDropEntryCount(this.dropEditIndex, value);
                  } else {
                     this.dropAction.dropCount = value;
                  }
               } catch (NumberFormatException var5) {
               }
            }
         }
      }
   }

   private void renderLanStepPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      boolean filterByUser = this.toggleStates.getOrDefault("lan_filterByUser", false);
      this.renderInlineToggle(ctx, font, "listenDuringPreviousAction", "Listen During Previous", x, cy, w, mx, my);
      cy += 17;
      this.renderInlineToggle(ctx, font, "lan_filterByUser", "Specific Peers", x, cy, w, mx, my);
      cy += 17;
      RiptideChatField dsF = this.textFields.get("lan_defaultStep");
      if (dsF != null && (!filterByUser || this.lanStepEntries.isEmpty())) {
         int lw = this.labelWidth(w, filterByUser ? "Fallback Step" : "Any Peer Step", font);
         this.drawLabel(ctx, filterByUser ? "Fallback Step" : "Any Peer Step", x, cy, lw, font);
         dsF.setX(this.controlX(x, lw));
         dsF.setY(cy + 2);
         dsF.setWidth(this.controlWidth(w, lw));
         dsF.render(ctx, mx, my, delta);
         cy += 17;
      }

      String summary = !filterByUser
         ? "Continue when any LAN peer reaches the target step."
         : (
            this.lanStepEntries.isEmpty()
               ? "Add peers below to narrow it down. Until then, it waits for any peer at the fallback step."
               : "Each peer below must reach its step before this continues."
         );
      cy = this.renderEditorHint(ctx, x, cy, w, summary);
      if (filterByUser) {
         if (!this.lanStepEntries.isEmpty()) {
            UiText.draw(ctx, this.textRenderer, "Peers (" + this.lanStepEntries.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
            cy += 13;
         }

         boolean hasEntries = !this.lanStepEntries.isEmpty();
         int visibleRows = hasEntries ? Math.min(3, Math.max(1, this.lanStepEntries.size())) : 1;
         int selAreaH = hasEntries ? visibleRows * 15 : 12;
         DirectScrollViewport lanViewport = null;
         if (hasEntries) {
            lanViewport = this.getOrCreateViewport(this.selectedScrollViewports, "_lan_entries", x, cy, w, selAreaH, 15, 5);
            lanViewport.setContentHeight(this.lanStepEntries.size() * 15);
         }

         if (hasEntries && lanViewport != null) {
            lanViewport.renderScrollbar(ctx, mx, my);
         }

         int sbGap = 7;
         int removeW = 13;
         int stepW = 40;
         int gapPx = 2;
         int removeX = x + w - sbGap - removeW;
         int stepX = removeX - gapPx - stepW;
         int userW = stepX - x - gapPx;
         if (hasEntries && lanViewport != null) {
            lanViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
            int firstVis = lanViewport.getFirstVisibleRow();

            for (int i = firstVis; i < this.lanStepEntries.size() && i <= lanViewport.getLastVisibleRow(); i++) {
               int iy = lanViewport.getRowScreenY(i);
               if (iy != Integer.MIN_VALUE) {
                  CompactSurfaces.valueField(ctx, x, iy, stepX - gapPx - x, 13);
                  RiptideChatField uf = this.textFields.get("lan_user_" + i);
                  if (uf != null) {
                     uf.setX(x);
                     uf.setY(iy + 1);
                     uf.setWidth(userW);
                     uf.render(ctx, mx, my, delta);
                  }

                  RiptideChatField sf = this.textFields.get("lan_step_" + i);
                  if (sf != null) {
                     sf.setX(stepX);
                     sf.setY(iy + 1);
                     sf.setWidth(stepW);
                     sf.render(ctx, mx, my, delta);
                  }

                  int fi = i;
                  this.renderIconDeleteButton(ctx, removeX, iy + 1, removeW, mx, my, () -> {
                     this.lanStepEntries.remove(fi);
                     this.rebuildLanStepFields();
                     DirectScrollViewport vp = this.selectedScrollViewports.get("_lan_entries");
                     if (vp != null) {
                        vp.scrollBy(-1);
                     }
                  });
               }
            }

            lanViewport.endRender(ctx);
         } else {
            CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No peer filters yet.", x, cy, w - 5 - 1);
         }

         cy += selAreaH;
         CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
         cy += 6;
         int addBtnW = 36;
         RiptideChatField newUF = this.addFields.get("_lan_user_add");
         RiptideChatField newSF = this.addFields.get("_lan_step_add");
         if (newUF != null && newSF != null) {
            int plusX = x + w - addBtnW;
            int stepAX = plusX - 3 - stepW;
            int userAW = stepAX - x - 2;
            newUF.setX(x);
            newUF.setY(cy + 1);
            newUF.setWidth(userAW);
            newUF.render(ctx, mx, my, delta);
            newSF.setX(stepAX);
            newSF.setY(cy + 1);
            newSF.setWidth(stepW);
            newSF.render(ctx, mx, my, delta);
            this.renderOverlayButton(ctx, plusX, cy, addBtnW, 14, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
               String uname = newUF.getText().strip();
               int step = 1;

               try {
                  step = Math.max(1, Integer.parseInt(newSF.getText().strip()));
               } catch (NumberFormatException var8x) {
               }

               this.lanStepEntries.add(new WaitForLanStepAction.LanStepEntry(uname, step));
               int idx = this.lanStepEntries.size() - 1;
               RiptideChatField uf2 = this.makeField(80);
               uf2.setText(uname);
               this.textFields.put("lan_user_" + idx, uf2);
               RiptideChatField sf2 = this.makeField(40);
               sf2.setNumericOnly(true);
               sf2.setText(String.valueOf(step));
               this.textFields.put("lan_step_" + idx, sf2);
               newUF.setText("");
               newSF.setText("1");
            });
         }
      }
   }

   private void rebuildLanStepFields() {
      this.textFields.keySet().removeIf(k -> k.startsWith("lan_user_") || k.startsWith("lan_step_"));

      for (int i = 0; i < this.lanStepEntries.size(); i++) {
         WaitForLanStepAction.LanStepEntry e = this.lanStepEntries.get(i);
         RiptideChatField uf = this.makeField(80);
         uf.setText(e.username);
         this.textFields.put("lan_user_" + i, uf);
         RiptideChatField sf = this.makeField(40);
         sf.setNumericOnly(true);
         sf.setText(String.valueOf(e.step));
         this.textFields.put("lan_step_" + i, sf);
      }
   }

   private void renderPacketActionButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int btnH = 14;
      int halfW = (w - 4) / 2;
      int topY = y + 2;
      int bottomY = y + 20;
      String info = this.buildPacketActionInfo();
      UiText.draw(ctx, this.textRenderer, UiText.trimToWidth(this.textRenderer, info, w, font, -1), font, RiptideColors.textDim(), x, y - 10, false);
      this.renderActionButton(ctx, x, topY, halfW, btnH, "Queue First", mx, my, () -> {
         List<RiptideSharedState.QueuedPacket> queue = RiptideSharedState.get().getDelayedPackets();
         if (queue != null && !queue.isEmpty()) {
            this.setRawPacketActionData(queue.get(0));
            RiptideClientMessaging.sendPrefixed("Loaded first queued packet");
         } else {
            RiptideClientMessaging.sendPrefixed("Queue is empty");
         }
      });
      this.renderActionButton(ctx, x + halfW + 4, topY, halfW, btnH, "Paste Base64", mx, my, () -> {
         List<RiptideSharedState.QueuedPacket> pasted = RiptideClipboardHelper.pasteFromClipboard();
         if (pasted != null && !pasted.isEmpty()) {
            this.setRawPacketActionData(pasted.get(0));
            RiptideClientMessaging.sendPrefixed("Loaded first pasted packet");
         } else {
            RiptideClientMessaging.sendPrefixed("Failed to paste packet data");
         }
      });
      this.renderActionButton(ctx, x, bottomY, w, btnH, "Clear Raw Packet", mx, my, () -> {
         this.workingTag.putString("packetData", "");
         RiptideClientMessaging.sendPrefixed("Cleared raw packet data");
      });
   }

   private void renderWaitPacketPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      int halfW = (w - 4) / 2;
      List<String> c2sTargets = this.getWaitPacketTargets("C2S");
      List<String> s2cTargets = this.getWaitPacketTargets("S2C");
      String summary = c2sTargets.isEmpty() && s2cTargets.isEmpty()
         ? "No packets selected. This step will continue on the next packet in either direction."
         : "This step continues as soon as any selected C2S or S2C packet arrives.";
      cy = this.renderEditorHint(ctx, x, cy, w, summary);
      this.renderInlineToggle(ctx, font, "listenDuringPreviousAction", "Listen During Previous", x, cy, w, mx, my);
      cy += 18;
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      this.renderActionButton(ctx, x, cy, halfW, 14, "Add C2S", mx, my, () -> this.openWaitPacketSelector(true));
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Add S2C", mx, my, () -> this.openWaitPacketSelector(false));
      cy += 18;
      this.renderActionButton(ctx, x, cy, halfW, 14, "Load Queue", mx, my, this::loadWaitPacketTargetsFromQueue);
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Clear All", mx, my, () -> {
         this.getOrCreateWaitPacketTargets().clear();
         DirectScrollViewport vpC2s = this.selectedScrollViewports.get("wait_packet_c2s");
         if (vpC2s != null) {
            vpC2s.jumpTo(0);
         }

         DirectScrollViewport vpS2c = this.selectedScrollViewports.get("wait_packet_s2c");
         if (vpS2c != null) {
            vpS2c.jumpTo(0);
         }
      });
      cy += 20;
      cy = this.renderSimpleSelectedList(
         ctx,
         x,
         cy,
         w,
         mx,
         my,
         "wait_packet_c2s",
         "C2S Packets",
         c2sTargets,
         this::removeWaitPacketTarget,
         this::formatWaitPacketTarget,
         ignored -> null,
         () -> this.clearWaitPacketTargets("C2S"),
         "No C2S packets selected"
      );
      cy += 4;
      this.renderSimpleSelectedList(
         ctx,
         x,
         cy,
         w,
         mx,
         my,
         "wait_packet_s2c",
         "S2C Packets",
         s2cTargets,
         this::removeWaitPacketTarget,
         this::formatWaitPacketTarget,
         ignored -> null,
         () -> this.clearWaitPacketTargets("S2C"),
         "No S2C packets selected"
      );
   }

   private void renderWaitPacketMatchPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      if (this.waitPacketMatchRules == null) {
         this.waitPacketMatchRules = new ArrayList<>();
      }

      if (this.waitPacketMatchEditIndex >= this.waitPacketMatchRules.size()) {
         this.waitPacketMatchEditIndex = this.waitPacketMatchRules.isEmpty() ? -1 : 0;
      }

      cy = this.renderEditorHint(ctx, x, cy, w, "Add a packet, then set field + rule.");
      this.renderInlineToggle(ctx, font, "listenDuringPreviousAction", "Listen During Previous", x, cy, w, mx, my);
      cy += 18;
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      int halfW = (w - 4) / 2;
      this.renderActionButton(ctx, x, cy, halfW, 14, "Add C2S", mx, my, () -> this.openWaitPacketMatchSelector(true));
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Add S2C", mx, my, () -> this.openWaitPacketMatchSelector(false));
      cy += 18;
      this.renderWaitPacketMatchRuleList(ctx, x, cy, w, mx, my);
      cy += 13 + Math.max(1, Math.min(4, Math.max(1, this.waitPacketMatchRules.size()))) * 15 + 5;
      if (this.waitPacketMatchRules.isEmpty()) {
         this.renderEditorHint(ctx, x, cy, w, "No packet rules yet. Add C2S or S2C.");
      } else {
         WaitPacketMatchAction.Rule rule = this.waitPacketMatchRules.get(Math.max(0, this.waitPacketMatchEditIndex));
         List<String> dirOpts = new ArrayList<>();

         for (WaitPacketMatchAction.Direction d : WaitPacketMatchAction.Direction.values()) {
            dirOpts.add(d.name());
         }

         cy = this.renderInlineEnumDropdown(
            ctx, font, "Direction", "_wpm_dir", dirOpts, rule.direction.ordinal(), x, cy, w, i -> rule.direction = WaitPacketMatchAction.Direction.values()[i]
         );
         Class<? extends Packet<?>> packetClass = this.resolveWaitPacketMatchClass(rule);
         List<String> fieldNames = new ArrayList<>(WaitPacketMatchAction.packetFieldNames(packetClass));
         List<String> fieldDisplay = new ArrayList<>();
         fieldDisplay.add("Packet Only");
         fieldDisplay.addAll(fieldNames);
         int fieldIdx = rule.fieldName != null && !rule.fieldName.isBlank() ? Math.max(0, fieldNames.indexOf(rule.fieldName) + 1) : 0;
         cy = this.renderInlineEnumDropdown(ctx, font, "Field", "_wpm_field", fieldDisplay, fieldIdx, x, cy, w, i -> {
            rule.fieldName = i <= 0 ? "" : fieldNames.get(i - 1);
            if (rule.fieldName.isBlank()) {
               rule.operator = WaitPacketMatchAction.Operator.EXISTS;
            }

            List<String> vo = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
            if (!vo.isEmpty() && (rule.value == null || rule.value.isBlank() || !vo.contains(rule.value))) {
               rule.value = vo.get(0);
            }

            this.syncWaitPacketMatchValueField();
         });
         if (rule.fieldName != null && !rule.fieldName.isBlank()) {
            List<String> opOpts = new ArrayList<>();

            for (WaitPacketMatchAction.Operator o : WaitPacketMatchAction.Operator.values()) {
               opOpts.add(o.name());
            }

            cy = this.renderInlineEnumDropdown(
               ctx, font, "Operator", "_wpm_op", opOpts, rule.operator.ordinal(), x, cy, w, i -> rule.operator = WaitPacketMatchAction.Operator.values()[i]
            );
            if (rule.operator != WaitPacketMatchAction.Operator.EXISTS) {
               List<String> valueOptions = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
               if (!valueOptions.isEmpty()) {
                  int valIdx = Math.max(0, valueOptions.indexOf(rule.value));
                  cy = this.renderInlineEnumDropdown(ctx, font, "Value", "_wpm_value_dd", valueOptions, valIdx, x, cy, w, i -> {
                     rule.value = valueOptions.get(i);
                     this.syncWaitPacketMatchValueField();
                  });
               } else {
                  RiptideChatField valueField = this.textFields.get("_wpm_value");
                  if (valueField != null) {
                     int lw = this.labelWidth(w, "Value", font, 72);
                     this.drawLabel(ctx, "Value", x, cy, lw, font);
                     valueField.setX(this.controlX(x, lw));
                     valueField.setY(cy + 1);
                     valueField.setWidth(this.controlWidth(w, lw));
                     valueField.render(ctx, mx, my, delta);
                     cy += 17;
                  }
               }
            }
         }
      }
   }

   private int renderInlineEnumDropdown(
      GuiGraphicsExtractor ctx,
      Identifier font,
      String label,
      String cacheKey,
      List<String> options,
      int selectedIdx,
      int x,
      int cy,
      int w,
      IntConsumer onSelect
   ) {
      if (options.isEmpty()) {
         return cy;
      } else {
         int lw = this.labelWidth(w, label, font, 72);
         this.drawLabel(ctx, label, x, cy, lw, font);
         int ctrlX = this.controlX(x, lw);
         int ctrlW = this.controlWidth(w, lw);
         int idx = Math.max(0, Math.min(selectedIdx, options.size() - 1));
         CompactDropdown dd = this.enumDropdownCache.computeIfAbsent(cacheKey, k -> new CompactDropdown(ctrlX, cy + 1, ctrlW, 16, options, idx, onSelect));
         dd.setBounds(ctrlX, cy + 1, ctrlW, 16).setOptions(options).setSelectedIndex(idx).setOnSelect(onSelect);
         this.enumDropdowns.add(dd);
         return cy + 15 + 2;
      }
   }

   private void renderInlineDropdown(String cacheKey, List<String> options, int selectedIdx, int x, int y, int w, int h, IntConsumer onSelect) {
      if (!options.isEmpty()) {
         int idx = Math.max(0, Math.min(selectedIdx, options.size() - 1));
         CompactDropdown dd = this.enumDropdownCache.computeIfAbsent(cacheKey, k -> new CompactDropdown(x, y, w, h, options, idx, onSelect));
         dd.setBounds(x, y, w, h).setOptions(options).setSelectedIndex(idx).setOnSelect(onSelect);
         this.enumDropdowns.add(dd);
      }
   }

   private void renderWaitPacketMatchRuleList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, "Packet Rules (" + this.waitPacketMatchRules.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      this.renderOverlayButton(ctx, x + w - 44, y, 44, 14, "Clear", CompactOverlayButton.Variant.DANGER, !this.waitPacketMatchRules.isEmpty(), mx, my, () -> {
         this.waitPacketMatchRules.clear();
         this.waitPacketMatchEditIndex = -1;
      });
      y += 14;
      int rows = Math.max(1, Math.min(4, Math.max(1, this.waitPacketMatchRules.size())));
      int listH = rows * 15;
      int delW = 13;
      if (this.waitPacketMatchRules.isEmpty()) {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No packet rules selected.", x, y, w - 5 - 1);
      } else {
         DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, "_wpm_rules", x, y, w, listH, 15, 5);
         viewport.setContentHeight(this.waitPacketMatchRules.size() * 15);
         viewport.renderScrollbar(ctx, mx, my);
         int rowW = w - 5 - 1 - delW - 2;
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);

         for (int i = viewport.getFirstVisibleRow(); i < this.waitPacketMatchRules.size() && i <= viewport.getLastVisibleRow(); i++) {
            int iy = viewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               WaitPacketMatchAction.Rule rule = this.waitPacketMatchRules.get(i);
               boolean selected = i == this.waitPacketMatchEditIndex;
               boolean hovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  this.formatWaitPacketMatchRule(rule),
                  UiBounds.of(x, iy, rowW, 13),
                  hovered,
                  selected,
                  CompactListRenderer.RowTone.NORMAL,
                  true
               );
               int fi = i;
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, rowW, 13, () -> {
                  this.syncWaitPacketMatchEditedRule();
                  this.waitPacketMatchEditIndex = fi;
                  this.syncWaitPacketMatchValueField();
               }));
               this.renderIconDeleteButton(ctx, x + rowW + 2, iy, delW, mx, my, () -> {
                  this.waitPacketMatchRules.remove(fi);
                  if (this.waitPacketMatchEditIndex >= this.waitPacketMatchRules.size()) {
                     this.waitPacketMatchEditIndex = this.waitPacketMatchRules.size() - 1;
                  }

                  this.syncWaitPacketMatchValueField();
               });
            }
         }

         viewport.endRender(ctx);
      }
   }

   private void renderSendPacketPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      List<RiptideSharedState.QueuedPacket> actionPackets = this.getWorkingQueuedPackets();
      List<RiptideSharedState.QueuedPacket> queuePackets = RiptideSharedState.get().getDelayedPackets();
      if (queuePackets == null) {
         queuePackets = Collections.emptyList();
      }

      List<RiptideSharedState.QueuedPacket> finalQueuePackets = queuePackets;
      UiText.draw(ctx, this.textRenderer, "Action Packets (" + actionPackets.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      this.renderOverlayButton(
         ctx,
         x + w - 44,
         cy,
         44,
         14,
         "Clear",
         CompactOverlayButton.Variant.DANGER,
         !actionPackets.isEmpty(),
         mx,
         my,
         () -> this.setWorkingQueuedPackets(Collections.emptyList())
      );
      cy += 14;
      cy = this.renderQueuedPacketList(ctx, x, cy, w, mx, my, delta, "_send_packet_action", actionPackets, true, packetIndex -> {
         List<RiptideSharedState.QueuedPacket> updated = this.getWorkingQueuedPackets();
         if (packetIndex >= 0 && packetIndex < updated.size()) {
            updated.remove(packetIndex);
            this.setWorkingQueuedPackets(updated);
         }
      });
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      RiptideChatField nameField = this.textFields.get("customName");
      if (nameField != null) {
         int lw = this.labelWidth(w, "Name", font);
         this.drawLabel(ctx, "Name", x, cy, lw, font);
         nameField.setX(this.controlX(x, lw));
         nameField.setY(cy + 2);
         nameField.setWidth(this.controlWidth(w, lw));
         nameField.render(ctx, mx, my, delta);
         cy += 17;
      }

      this.renderGuiWaitRow(ctx, font, "waitForGuiBefore", "waitForGuiAfter", x, cy, w, mx, my);
      cy += 17;
      if (!this.toggleStates.getOrDefault("waitForGuiBefore", false) && !this.toggleStates.getOrDefault("waitForGuiAfter", false)) {
         RiptideChatField guiField = this.textFields.get("guiName");
         if (guiField != null) {
            guiField.setX(-1000);
         }
      } else {
         RiptideChatField guiField = this.textFields.get("guiName");
         if (guiField != null) {
            int lw = this.labelWidth(w, "GUI Name", font);
            this.drawLabel(ctx, "GUI Name", x, cy, lw, font);
            guiField.setX(this.controlX(x, lw));
            guiField.setY(cy + 2);
            guiField.setWidth(this.controlWidth(w, lw));
            guiField.render(ctx, mx, my, delta);
            cy += 17;
         }
      }

      cy += 2;
      int halfW = (w - 4) / 2;
      this.renderActionButton(ctx, x, cy, halfW, 14, "From Queue", mx, my, () -> {
         this.setWorkingQueuedPackets(finalQueuePackets);
         RiptideClientMessaging.sendPrefixed("Loaded " + finalQueuePackets.size() + " packets from queue");
      });
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Paste Base64", mx, my, () -> {
         List<RiptideSharedState.QueuedPacket> pasted = RiptideClipboardHelper.pasteFromClipboard();
         if (pasted != null && !pasted.isEmpty()) {
            this.setWorkingQueuedPackets(pasted);
            RiptideClientMessaging.sendPrefixed("Pasted " + pasted.size() + " packets");
         } else {
            RiptideNotifications.error("Clipboard does not contain packets.");
         }
      });
      cy += 18;
      this.renderActionButton(ctx, x, cy, halfW, 14, "Clear", mx, my, () -> {
         this.setWorkingQueuedPackets(Collections.emptyList());
         RiptideClientMessaging.sendPrefixed("Cleared packet list");
      });
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "GBreak", mx, my, this::startGBreakCaptureForEditor);
      cy += 20;
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      UiText.draw(ctx, this.textRenderer, "Current Queue (" + finalQueuePackets.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 14;
      this.renderQueuedPacketList(ctx, x, cy, w, mx, my, delta, "_send_packet_queue", finalQueuePackets, false, packetIndex -> {
         if (packetIndex >= 0 && packetIndex < finalQueuePackets.size()) {
            List<RiptideSharedState.QueuedPacket> updated = this.getWorkingQueuedPackets();
            updated.add(finalQueuePackets.get(packetIndex));
            this.setWorkingQueuedPackets(updated);
         }
      });
   }

   private int renderQueuedPacketList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      float delta,
      String listKey,
      List<RiptideSharedState.QueuedPacket> packets,
      boolean removable,
      IntConsumer rowAction
   ) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int listH = 60;
      DirectScrollViewport packetViewport = this.getOrCreateViewport(this.selectedScrollViewports, listKey, x, y, w, listH, 15, 5);
      packetViewport.setContentHeight(packets.size() * 15);
      packetViewport.renderScrollbar(ctx, mx, my);
      int removeW = removable ? 13 : 0;
      int textW = w - 5 - 2 - (removable ? removeW + 2 : 0);
      if (packets.isEmpty()) {
         UiText.draw(
            ctx, this.textRenderer, removable ? "(none - use GBreak, queue, or Paste)" : "(queue empty)", font, RiptideColors.textDim(), x, y + 2, false
         );
         return y + listH;
      } else {
         packetViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstVis = packetViewport.getFirstVisibleRow();

         for (int i = firstVis; i < packets.size() && i <= packetViewport.getLastVisibleRow(); i++) {
            int iy = packetViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               RiptideSharedState.QueuedPacket qp = packets.get(i);
               String pname = qp != null && qp.packet != null ? RiptidePacketNamer.getFriendlyName(qp.packet) : "???";
               String rowText = i + 1 + ". " + pname + " d=" + (qp != null ? qp.getDelay() : 0);
               boolean hovered = mx >= x && mx < x + textW && my >= iy && my < iy + 13;
               MacroTypedListControl.renderRow(
                  ctx, this.textRenderer, Component.literal(rowText), UiBounds.of(x, iy, textW, 13), hovered, false, CompactListRenderer.RowTone.NORMAL, false
               );
               int rowIndex = i;
               if (!removable) {
                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, textW, 13, () -> rowAction.accept(rowIndex)));
               }

               if (removable) {
                  int removeX = x + textW + 2;
                  this.renderIconDeleteButton(ctx, removeX, iy, removeW, mx, my, () -> rowAction.accept(rowIndex));
               }
            }
         }

         packetViewport.endRender(ctx);
         return y + listH;
      }
   }

   private void renderSendPacketButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int btnH = 14;
      int halfW = (w - 4) / 2;
      int topY = y + 2;
      int bottomY = y + 20;
      String info = this.buildSendPacketInfo();
      UiText.draw(ctx, this.textRenderer, UiText.trimToWidth(this.textRenderer, info, w, font, -1), font, RiptideColors.textDim(), x, y - 10, false);
      this.renderActionButton(ctx, x, topY, halfW, btnH, "From Queue", mx, my, () -> {
         List<RiptideSharedState.QueuedPacket> queue = RiptideSharedState.get().getDelayedPackets();
         this.setWorkingQueuedPackets(queue);
         RiptideClientMessaging.sendPrefixed("Loaded " + this.getWorkingQueuedPackets().size() + " packets from queue");
      });
      this.renderActionButton(ctx, x + halfW + 4, topY, halfW, btnH, "Paste Base64", mx, my, () -> {
         List<RiptideSharedState.QueuedPacket> pasted = RiptideClipboardHelper.pasteFromClipboard();
         if (pasted != null && !pasted.isEmpty()) {
            this.setWorkingQueuedPackets(pasted);
            RiptideClientMessaging.sendPrefixed("Pasted " + pasted.size() + " packets");
         } else {
            RiptideNotifications.error("Clipboard does not contain packets.");
         }
      });
      this.renderActionButton(ctx, x, bottomY, halfW, btnH, "Clear", mx, my, () -> {
         this.setWorkingQueuedPackets(Collections.emptyList());
         RiptideClientMessaging.sendPrefixed("Cleared packet list");
      });
      this.renderOverlayButton(
         ctx,
         x + halfW + 4,
         bottomY,
         halfW,
         btnH,
         this.configurationOnly ? "Capture: In Game" : "GBreak",
         CompactOverlayButton.Variant.SECONDARY,
         !this.configurationOnly,
         mx,
         my,
         () -> {
            if (!this.configurationOnly) {
               RiptideSharedState.get().startGBreakCapture(() -> MC.execute(() -> {
                  List<RiptideSharedState.QueuedPacket> captured = RiptideSharedState.get().getGBreakCapturedPackets();
                  this.setWorkingQueuedPackets(captured);
                  RiptideClientMessaging.sendPrefixed(captured.isEmpty() ? "GBreak capture finished with no packet" : "GBreak packet captured");
               }));
               RiptideClientMessaging.sendPrefixed("Break a block now to capture the GBreak packet");
            }
         }
      );
   }

   private void initializePayloadEditorFields(PayloadAction seedAction) {
      this.payloadEditorModel = RiptidePayloadEditorModel.fromAction(seedAction);
      this.pendingPayloadEdit = null;
      this.payloadPreviewSyncedRevision = Long.MIN_VALUE;
      this.payloadBuildCache = null;
      ActionEditorOverlay.PayloadEditorState state = this.resolvePayloadEditorState(seedAction);
      this.payloadContentMode = state.contentMode();
      this.payloadContentEdited = false;
      this.payloadRawEdited = false;
      this.payloadChannelEdited = false;
      this.payloadJsonEdited = false;
      this.payloadModeManuallyChanged = false;
      this.payloadAddTypeManuallyChanged = false;
      this.suppressPayloadEditorChange = false;
      RiptideChatField channelField = this.makeField(120);
      channelField.setText(state.channel());
      channelField.setPlaceholder(Component.literal("namespace:channel"));
      channelField.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange) {
            this.payloadChannelEdited = true;
            if (this.payloadEditorModel != null) {
               this.payloadEditorModel.channel = text != null && !text.isBlank() ? text.strip() : "minecraft:brand";
               this.payloadEditorModel.touch();
               this.queuePayloadEdit(ActionEditorOverlay.PayloadEditKind.PREVIEW_ONLY, "");
            }

            this.autoSelectPayloadControls();
         }
      });
      this.textFields.put("payload_channel", channelField);
      if (seedAction != null) {
         seedAction.payloadPhase = this.payloadEditorModel.phase;
      }

      RiptideChatField contentField = this.makeField(126);
      contentField.setMultiline(true);
      contentField.setEnterInsertsNewline(true);
      contentField.setSpaceKeyInsertsSpace(true);
      contentField.setHoverEffectsEnabled(false);
      contentField.setFocusEffectsEnabled(false);
      contentField.setBackgroundColorOverride(1376389388);
      contentField.setMaxLength(32767);
      contentField.setPlaceholder(Component.literal("direction = C2S\nphase = PLAY\nchannel = minecraft:brand\n\nwriteMcString = vanilla"));
      contentField.setDisplayTextProvider(this::payloadScriptComponent);
      contentField.setDisplayTextRevisionProvider(this::payloadEditorRevision);
      contentField.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange) {
            this.payloadContentEdited = true;
            this.queuePayloadEdit(ActionEditorOverlay.PayloadEditKind.SCRIPT, text);
         }
      });
      this.textFields.put("payload_content", contentField);
      RiptideChatField hexView = this.makeField(138);
      hexView.setMultiline(true);
      hexView.setEnterInsertsNewline(true);
      hexView.setSpaceKeyInsertsSpace(true);
      hexView.setHoverEffectsEnabled(false);
      hexView.setFocusEffectsEnabled(false);
      hexView.setBackgroundColorOverride(1376389388);
      hexView.setEditable(true);
      hexView.setMaxLength(32767);
      hexView.setPlaceholder(Component.literal("00 01 02 FF"));
      hexView.setDisplayTextProvider(this::payloadHexComponent);
      hexView.setDisplayTextRevisionProvider(this::payloadEditorRevision);
      hexView.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange) {
            this.queuePayloadEdit(ActionEditorOverlay.PayloadEditKind.HEX, text);
         }
      });
      this.textFields.put("payload_hex_view", hexView);
      RiptideChatField utf8View = this.makeField(138);
      utf8View.setMultiline(true);
      utf8View.setEnterInsertsNewline(true);
      utf8View.setSpaceKeyInsertsSpace(true);
      utf8View.setHoverEffectsEnabled(false);
      utf8View.setFocusEffectsEnabled(false);
      utf8View.setBackgroundColorOverride(1376389388);
      utf8View.setEditable(true);
      utf8View.setMaxLength(32767);
      utf8View.setPlaceholder(Component.literal("Raw UTF-8 text"));
      utf8View.setDisplayTextProvider(this::payloadUtf8Component);
      utf8View.setDisplayTextRevisionProvider(this::payloadEditorRevision);
      utf8View.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange) {
            this.queuePayloadEdit(ActionEditorOverlay.PayloadEditKind.UTF8, text);
         }
      });
      this.textFields.put("payload_utf8_view", utf8View);
      RiptideChatField logicalView = this.makeField(138);
      logicalView.setMultiline(true);
      logicalView.setEnterInsertsNewline(true);
      logicalView.setSpaceKeyInsertsSpace(true);
      logicalView.setHoverEffectsEnabled(false);
      logicalView.setFocusEffectsEnabled(false);
      logicalView.setBackgroundColorOverride(1376389388);
      logicalView.setEditable(true);
      logicalView.setMaxLength(32767);
      logicalView.setPlaceholder(Component.literal("direction = C2S\nphase = PLAY\nchannel = minecraft:brand\nbodyHex = ..."));
      logicalView.setDisplayTextProvider(this::payloadLogicalComponent);
      logicalView.setDisplayTextRevisionProvider(this::payloadEditorRevision);
      logicalView.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange) {
            this.queuePayloadEdit(ActionEditorOverlay.PayloadEditKind.LOGICAL, text);
         }
      });
      this.textFields.put("payload_logical_view", logicalView);
      RiptideChatField commandField = this.makeField(66);
      commandField.setNumericOnly(true);
      commandField.setText(String.valueOf(this.payloadEditorModel.commandApiValue));
      commandField.setPlaceholder(Component.literal("value"));
      commandField.setChangedListener(text -> {
         if (!this.suppressPayloadEditorChange && this.payloadEditorModel != null) {
            try {
               this.payloadEditorModel.commandApiValue = Integer.parseInt((text == null ? "" : text).strip());
               this.payloadEditorModel.commandApiRecognized = true;
               this.payloadEditorModel.commandApiOverride = true;
               this.payloadEditorModel.touch();
               this.payloadBuildCache = null;
               this.toggleStates.put("payload_command_api", true);
               this.payloadContentEdited = true;
            } catch (NumberFormatException var3x) {
            }
         }
      });
      this.textFields.put("payload_command_value", commandField);
      this.toggleStates.put("payload_command_api", this.payloadEditorModel.commandApiOverride);
      this.syncPayloadAllFieldsFromModel(true);
      RiptidePayloadTemplate.Template template = this.currentPayloadTemplateFromModel();
      RiptidePayloadTemplate.EncodingMode suggestedMode = this.suggestPayloadMode(template.channel(), template.fields(), template.mode());
      this.enumIndices.put("payload_mode", this.payloadModeIndex(suggestedMode));
      this.enumIndices.put("payload_add_type", this.payloadTypeIndex(this.suggestPayloadAddType(template.channel(), suggestedMode, template.fields())));
   }

   private ActionEditorOverlay.PayloadEditorState resolvePayloadEditorState(PayloadAction seedAction) {
      PayloadAction action = new PayloadAction();
      if (seedAction != null) {
         action.fromTag(seedAction.toTag());
      }

      String channel = action.channel == null ? "" : action.channel.strip();
      byte[] bytes = new byte[0];
      boolean haveBytes = false;
      if (action.payloadData != null && !action.payloadData.isBlank()) {
         try {
            bytes = RiptidePayloadSupport.parsePayloadBytes(action.payloadData);
            haveBytes = true;
         } catch (Exception var12) {
            bytes = new byte[0];
         }
      }

      if (action.payloadJson != null && !action.payloadJson.isBlank()) {
         try {
            action.payloadJson = RiptidePayloadJsonSupport.normalizeJson(action.payloadJson);
            RiptidePayloadJsonSupport.EncodedPayload encoded = RiptidePayloadJsonSupport.encodeAction(action);
            if (channel.isBlank() && encoded.channel() != null && !encoded.channel().isBlank()) {
               channel = encoded.channel();
            }

            if (!haveBytes) {
               bytes = encoded.bytes();
               if (bytes.length <= 0 && action.payloadData != null && !action.payloadData.isBlank()) {
                  boolean var17 = false;
               } else {
                  boolean var10000 = true;
               }
            }
         } catch (Exception var13) {
         }
      }

      if (channel.isBlank()) {
         channel = "minecraft:brand";
      }

      if (RiptidePayloadSupport.isBrandChannel(channel)) {
         String brandText = RiptidePayloadSupport.decodeMinecraftStringPayload(bytes);
         if (brandText != null || bytes.length == 0) {
            return new ActionEditorOverlay.PayloadEditorState(
               channel,
               bytes,
               false,
               action.commandApiValue,
               ActionEditorOverlay.PayloadContentMode.BRAND_STRING,
               brandText != null ? brandText : RiptidePayloadSupport.defaultBrandPayloadString()
            );
         }
      }

      Integer commandValue = RiptidePayloadSupport.tryParseCommandApiValue(null, channel, bytes);
      boolean commandRecognized = action.commandApiRecognized || commandValue != null;
      int resolvedCommandValue = commandValue != null ? commandValue : action.commandApiValue;
      ActionEditorOverlay.PayloadContentMode mode = ActionEditorOverlay.PayloadContentMode.BINARY_REPLAY;
      String contentText = "";
      if (commandRecognized) {
         mode = ActionEditorOverlay.PayloadContentMode.COMMAND_INT;
         contentText = String.valueOf(resolvedCommandValue);
      } else {
         String readableText = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
         if (readableText.isBlank() && bytes.length != 0) {
            contentText = "Binary payload (" + bytes.length + " bytes). Safe replay keeps the captured bytes.";
         } else {
            mode = ActionEditorOverlay.PayloadContentMode.UTF8_TEXT;
            contentText = this.prettifyPayloadText(readableText);
         }
      }

      return new ActionEditorOverlay.PayloadEditorState(channel, bytes, commandRecognized, resolvedCommandValue, mode, contentText);
   }

   private String prettifyPayloadText(String text) {
      if (text != null && !text.isBlank()) {
         String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
         String stripped = normalized.strip();
         if (!stripped.startsWith("{") && !stripped.startsWith("[")) {
            return normalized;
         } else {
            try {
               return PAYLOAD_TEXT_GSON.toJson(JsonParser.parseString(stripped));
            } catch (Throwable var5) {
               return normalized;
            }
         }
      } else {
         return "";
      }
   }

   private void formatPayloadJsonField(boolean syncPreview) {
      RiptideChatField jsonField = this.textFields.get("payload_json");
      if (jsonField != null) {
         jsonField.setText(RiptidePayloadJsonSupport.normalizeJson(jsonField.getText()));
         if (syncPreview) {
            this.syncPayloadPreviewFromJson(true);
         }
      }
   }

   private void syncPayloadPreviewFromJson(boolean notifyOnError) {
      RiptideChatField jsonField = this.textFields.get("payload_json");
      if (jsonField != null) {
         PayloadAction preview = new PayloadAction();
         if (this.payloadAction != null) {
            preview.fromTag(this.payloadAction.toTag());
         }

         RiptideChatField channelField = this.textFields.get("payload_channel");
         RiptideChatField rawField = this.textFields.get("payload_data");
         RiptideChatField textField = this.textFields.get("payload_text");
         RiptideChatField commandField = this.textFields.get("payload_command_value");
         preview.payloadJson = jsonField.getText();
         preview.channel = channelField == null ? preview.channel : channelField.getText().strip();
         preview.payloadData = rawField == null ? preview.payloadData : rawField.getText();
         preview.commandApiRecognized = this.toggleStates.getOrDefault("payload_command_api", preview.commandApiRecognized);
         if (commandField != null) {
            try {
               preview.commandApiValue = Integer.parseInt(commandField.getText().strip());
            } catch (NumberFormatException var13) {
            }
         }

         try {
            preview.payloadJson = RiptidePayloadJsonSupport.normalizeJson(preview.payloadJson);
            RiptidePayloadJsonSupport.EncodedPayload encoded = RiptidePayloadJsonSupport.encodeAction(preview);
            this.suppressPayloadEditorChange = true;

            try {
               jsonField.setText(preview.payloadJson);
               if (channelField != null) {
                  channelField.setText(encoded.channel());
               }

               if (rawField != null) {
                  rawField.setText(RiptidePayloadSupport.toHex(encoded.bytes()));
               }

               if (textField != null) {
                  textField.setText(RiptidePayloadSupport.decodeLikelyUtf8Text(encoded.bytes()));
               }

               this.payloadChannelEdited = true;
               this.payloadRawEdited = true;
               this.payloadJsonEdited = false;
            } finally {
               this.suppressPayloadEditorChange = false;
            }

            Integer commandValue = RiptidePayloadSupport.tryParseCommandApiValue(null, encoded.channel(), encoded.bytes());
            if (commandValue != null) {
               this.toggleStates.put("payload_command_api", true);
               if (commandField != null) {
                  commandField.setText(String.valueOf(commandValue));
               }
            }
         } catch (Exception var15) {
            if (notifyOnError) {
               RiptideClientMessaging.sendPrefixed("§cCould not rebuild payload from JSON: " + RiptidePayloadSupport.safeMessage(var15));
            }
         }
      }
   }

   private void setPayloadFieldsInEditor(List<RiptidePayloadTemplate.Field> fields) {
      this.removePayloadRowFields();
      List<RiptidePayloadTemplate.Field> safeFields = fields == null ? List.of() : fields;
      this.payloadFieldCount = safeFields.size();
      if (this.payloadEditorModel != null) {
         this.payloadEditorModel.bodyFields = new ArrayList<>(safeFields);
         this.payloadEditorModel.touch();
         this.payloadEditorModel.applyPacketScript(this.payloadEditorModel.packetScript());
         this.payloadBuildCache = null;
         this.syncPayloadAllFieldsFromModel(true);
      } else {
         RiptideChatField contentField = this.textFields.get("payload_content");
         if (contentField != null) {
            contentField.setText(RiptidePayloadTemplate.serializeFields(safeFields));
         }
      }
   }

   private void removePayloadRowFields() {
      this.textFields.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_value_"));
      this.enumIndices.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_type_"));
      this.enumDropdownCache.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_type_"));
      this.payloadFieldCount = 0;
   }

   private List<RiptidePayloadTemplate.Field> payloadFieldsFromEditor() {
      if (this.payloadEditorModel != null) {
         return this.payloadEditorModel.bodyFields == null ? List.of() : this.payloadEditorModel.bodyFields;
      } else {
         RiptideChatField contentField = this.textFields.get("payload_content");
         return RiptidePayloadTemplate.parseFields(contentField == null ? "" : contentField.getText());
      }
   }

   private void autoSelectPayloadControls() {
      if (!this.suppressPayloadEditorChange) {
         String channel = this.payloadEditorChannel();
         List<RiptidePayloadTemplate.Field> fields = this.payloadFieldsFromEditor();
         if (!this.payloadModeManuallyChanged) {
            RiptidePayloadTemplate.EncodingMode current = PAYLOAD_MODES.get(
               Math.max(
                  0,
                  Math.min(
                     this.enumIndices.getOrDefault("payload_mode", this.payloadModeIndex(RiptidePayloadTemplate.EncodingMode.ADVANCED_MIXED)),
                     PAYLOAD_MODES.size() - 1
                  )
               )
            );
            this.enumIndices.put("payload_mode", this.payloadModeIndex(this.suggestPayloadMode(channel, fields, current)));
         }

         if (!this.payloadAddTypeManuallyChanged) {
            RiptidePayloadTemplate.EncodingMode mode = this.payloadModeFromEditor(channel);
            this.enumIndices.put("payload_add_type", this.payloadTypeIndex(this.suggestPayloadAddType(channel, mode, fields)));
         }
      }
   }

   private String payloadEditorChannel() {
      if (this.payloadEditorModel != null && this.payloadEditorModel.channel != null && !this.payloadEditorModel.channel.isBlank()) {
         return this.payloadEditorModel.channel.strip();
      } else {
         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null && !channelField.getText().isBlank()) {
            return channelField.getText().strip();
         } else {
            return this.payloadAction != null && this.payloadAction.channel != null && !this.payloadAction.channel.isBlank()
               ? this.payloadAction.channel.strip()
               : "minecraft:brand";
         }
      }
   }

   private RiptidePayloadTemplate.EncodingMode suggestPayloadMode(
      String channel, List<RiptidePayloadTemplate.Field> fields, RiptidePayloadTemplate.EncodingMode fallback
   ) {
      List<RiptidePayloadTemplate.Field> safeFields = fields == null ? List.of() : fields;
      if (RiptidePayloadSupport.isBrandChannel(channel)) {
         return RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF;
      } else if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip())) {
         return RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT;
      } else {
         for (RiptidePayloadTemplate.Field field : safeFields) {
            if (field != null) {
               switch (field.type()) {
                  case HEX_BYTES:
                  case RAW_BYTES:
                  case BYTE_ARRAY:
                     return RiptidePayloadTemplate.EncodingMode.MANUAL_HEX;
                  case JAVA_WRITE_UTF:
                  case BYTE:
                  case UNSIGNED_BYTE:
                  case BOOLEAN:
                  case SHORT:
                  case UNSIGNED_SHORT:
                  case CHAR:
                  case INT:
                  case LONG:
                  case FLOAT:
                  case DOUBLE:
                     return RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT;
                  case VAR_INT:
                  case VAR_LONG:
                  case MINECRAFT_STRING:
                  case IDENTIFIER:
                  case UUID_FIELD:
                  case BLOCK_POS:
                  case ENUM_VAR_INT:
                  case OPTIONAL_VALUE:
                     return RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF;
                  case JSON_STRING:
                  case TEXT_COMPONENT:
                  case NBT:
                  case ITEM_STACK:
                     return RiptidePayloadTemplate.EncodingMode.JSON_TEXT;
                  case STRING_BYTES:
                  case RAW_UTF8_STRING:
                     return RiptidePayloadTemplate.EncodingMode.RAW_UTF8;
               }
            }
         }

         return fallback == null ? RiptidePayloadTemplate.EncodingMode.ADVANCED_MIXED : fallback;
      }
   }

   private RiptidePayloadTemplate.FieldType suggestPayloadAddType(
      String channel, RiptidePayloadTemplate.EncodingMode mode, List<RiptidePayloadTemplate.Field> fields
   ) {
      if (RiptidePayloadSupport.isBrandChannel(channel)) {
         return RiptidePayloadTemplate.FieldType.MINECRAFT_STRING;
      } else if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip())) {
         return RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF;
      } else if (mode == RiptidePayloadTemplate.EncodingMode.MANUAL_HEX) {
         return RiptidePayloadTemplate.FieldType.HEX_BYTES;
      } else if (mode == RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT) {
         return RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF;
      } else if (mode == RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF) {
         return RiptidePayloadTemplate.FieldType.MINECRAFT_STRING;
      } else {
         return mode == RiptidePayloadTemplate.EncodingMode.JSON_TEXT
            ? RiptidePayloadTemplate.FieldType.TEXT_COMPONENT
            : RiptidePayloadTemplate.FieldType.STRING_BYTES;
      }
   }

   private void queuePayloadEdit(ActionEditorOverlay.PayloadEditKind kind, String text) {
      if (kind != null) {
         if (this.pendingPayloadEdit != null && this.pendingPayloadEdit.kind() != kind) {
            this.flushPendingPayloadEdit(true);
         }

         this.pendingPayloadEdit = new ActionEditorOverlay.PendingPayloadEdit(kind, text == null ? "" : text);
         this.pendingPayloadEditAtNanos = System.nanoTime();
      }
   }

   private void flushPendingPayloadEdit(boolean force) {
      ActionEditorOverlay.PendingPayloadEdit edit = this.pendingPayloadEdit;
      if (edit != null) {
         if (force || System.nanoTime() - this.pendingPayloadEditAtNanos >= 125000000L) {
            this.pendingPayloadEdit = null;
            if (this.payloadEditorModel == null) {
               this.autoSelectPayloadControls();
            } else {
               switch (edit.kind()) {
                  case SCRIPT:
                     if (this.payloadEditorModel.applyPacketScript(edit.text())) {
                        this.syncPayloadQuickFieldsFromModel(false);
                        this.syncPayloadPreviewFieldsFromModel(false);
                     }

                     this.autoSelectPayloadControls();
                     break;
                  case HEX:
                     this.syncPayloadModelFromHex(edit.text());
                     break;
                  case UTF8:
                     this.syncPayloadModelFromUtf8(edit.text());
                     break;
                  case LOGICAL:
                     this.syncPayloadModelFromLogical(edit.text());
                     break;
                  case PREVIEW_ONLY:
                     this.syncPayloadPreviewFieldsFromModel(false);
               }

               this.payloadBuildCache = null;
            }
         }
      }
   }

   private void syncPayloadModelFromHex(String text) {
      if (this.payloadEditorModel != null) {
         if (this.payloadEditorModel.applyBodyHex(text)) {
            this.payloadContentEdited = true;
            this.syncPayloadAllFieldsFromModel(false);
         }
      }
   }

   private void syncPayloadModelFromUtf8(String text) {
      if (this.payloadEditorModel != null) {
         this.payloadEditorModel.applyUtf8(text);
         this.payloadContentEdited = true;
         this.syncPayloadAllFieldsFromModel(false);
      }
   }

   private void syncPayloadModelFromLogical(String text) {
      if (this.payloadEditorModel != null) {
         if (this.payloadEditorModel.applyLogicalText(text)) {
            this.payloadContentEdited = true;
            this.syncPayloadAllFieldsFromModel(false);
         }
      }
   }

   private void syncPayloadAllFieldsFromModel(boolean force) {
      this.syncPayloadQuickFieldsFromModel(force);
      this.syncPayloadPreviewFieldsFromModel(force);
   }

   private void syncPayloadQuickFieldsFromModel(boolean force) {
      if (this.payloadEditorModel != null) {
         this.suppressPayloadEditorChange = true;

         try {
            RiptideChatField channelField = this.textFields.get("payload_channel");
            if (channelField != null && (force || !channelField.isFocused())) {
               channelField.setText(this.payloadEditorModel.channel == null ? "" : this.payloadEditorModel.channel);
            }

            this.enumIndices
               .put(
                  "payload_mode",
                  this.payloadModeIndex(RiptidePayloadTemplate.EncodingMode.parse(this.payloadEditorModel.encodingMode, this.payloadEditorModel.channel))
               );
            RiptideChatField commandField = this.textFields.get("payload_command_value");
            if (commandField != null && (force || !commandField.isFocused())) {
               commandField.setText(String.valueOf(this.payloadEditorModel.commandApiValue));
            }

            this.toggleStates.put("payload_command_api", this.payloadEditorModel.commandApiOverride);
         } finally {
            this.suppressPayloadEditorChange = false;
         }
      }
   }

   private void syncPayloadPreviewFieldsFromModel(boolean force) {
      if (this.payloadEditorModel != null) {
         long revision = this.payloadEditorModel.revision();
         if (force || this.payloadPreviewSyncedRevision != revision) {
            this.suppressPayloadEditorChange = true;

            try {
               RiptideChatField contentField = this.textFields.get("payload_content");
               if (contentField != null && (force || !contentField.isFocused())) {
                  contentField.setText(this.payloadEditorModel.packetScript());
               }

               RiptideChatField hexView = this.textFields.get("payload_hex_view");
               if (hexView != null && (force || !hexView.isFocused())) {
                  hexView.setText(this.payloadEditorModel.bodyHexMultiline());
               }

               RiptideChatField utf8View = this.textFields.get("payload_utf8_view");
               if (utf8View != null && (force || !utf8View.isFocused())) {
                  utf8View.setText(this.payloadEditorModel.utf8View());
               }

               RiptideChatField logicalView = this.textFields.get("payload_logical_view");
               if (logicalView != null && (force || !logicalView.isFocused())) {
                  logicalView.setText(this.payloadEditorModel.logicalText());
               }
            } finally {
               this.suppressPayloadEditorChange = false;
            }

            this.payloadPreviewSyncedRevision = revision;
         }
      }
   }

   private String escapePayloadScriptValue(String text) {
      return text != null && !text.isEmpty() ? text.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n") : "";
   }

   private int payloadTypeIndex(RiptidePayloadTemplate.FieldType type) {
      int index = PAYLOAD_FIELD_TYPES.indexOf(type);
      return index < 0 ? PAYLOAD_FIELD_TYPES.indexOf(RiptidePayloadTemplate.FieldType.STRING_BYTES) : index;
   }

   private RiptidePayloadTemplate.FieldType payloadTypeAt(int row) {
      int index = Math.max(
         0,
         Math.min(
            this.enumIndices.getOrDefault(this.payloadTypeKey(row), this.payloadTypeIndex(RiptidePayloadTemplate.FieldType.STRING_BYTES)),
            PAYLOAD_FIELD_TYPES.size() - 1
         )
      );
      return PAYLOAD_FIELD_TYPES.get(index);
   }

   private String payloadTypeKey(int row) {
      return "payload_type_" + row;
   }

   private String payloadValueKey(int row) {
      return "payload_value_" + row;
   }

   private int payloadModeIndex(RiptidePayloadTemplate.EncodingMode mode) {
      int index = PAYLOAD_MODES.indexOf(mode);
      return index < 0 ? PAYLOAD_MODES.indexOf(RiptidePayloadTemplate.EncodingMode.ADVANCED_MIXED) : index;
   }

   private RiptidePayloadTemplate.EncodingMode payloadModeFromEditor(String channel) {
      int fallback = this.payloadModeIndex(
         RiptidePayloadTemplate.EncodingMode.parse(this.payloadAction == null ? "" : this.payloadAction.payloadEncodingMode, channel)
      );
      int index = Math.max(0, Math.min(this.enumIndices.getOrDefault("payload_mode", fallback), PAYLOAD_MODES.size() - 1));
      return PAYLOAD_MODES.get(index);
   }

   private void renderCleanPayloadPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      ActionEditorOverlay.PayloadBuildCache preview = this.payloadBuildPreview();
      RiptidePayloadTemplate.Template template = preview.template();
      RiptidePayloadTemplate.BuildResult built = preview.result();
      UiText.draw(ctx, this.textRenderer, "Custom Payload", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 14;
      cy = this.renderPayloadStatus(ctx, x, cy, w);
      cy = this.renderPayloadTabs(ctx, x, cy, w, mx, my);
      int dirLabelW = 18;
      int dirW = 38;
      int modeLabelW = 28;
      UiText.draw(ctx, this.textRenderer, "Dir", font, RiptideColors.textDim(), x, cy + 3, false);
      this.renderOverlayButton(ctx, x + dirLabelW, cy, dirW, 14, template.direction().name(), CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
         this.flushPendingPayloadEdit(true);
         if (this.payloadEditorModel != null) {
            this.payloadEditorModel.direction = RiptidePayloadTemplate.nextDirectionName(this.payloadEditorModel.direction);
            this.payloadEditorModel.touch();
            this.payloadBuildCache = null;
            this.syncPayloadAllFieldsFromModel(false);
         } else if (this.payloadAction != null) {
            this.payloadAction.payloadDirection = RiptidePayloadTemplate.nextDirectionName(this.payloadAction.payloadDirection);
         }

         this.payloadContentEdited = true;
         this.refreshInteractiveLayout();
      });
      int modeLabelX = x + dirLabelW + dirW + 5;
      UiText.draw(ctx, this.textRenderer, "Mode", font, RiptideColors.textDim(), modeLabelX, cy + 3, false);
      int modeX = modeLabelX + modeLabelW;
      this.renderPayloadModeDropdown(modeX, cy, Math.max(54, x + w - modeX), mx, my, template);
      cy += 18;
      RiptideChatField channelField = this.textFields.get("payload_channel");
      if (channelField != null) {
         int lw = this.labelWidth(w, "Channel", font, 64);
         this.drawLabel(ctx, "Channel", x, cy, lw, font);
         int ctrlX = this.controlX(x, lw);
         int ctrlW = this.controlWidth(w, lw);
         channelField.setX(ctrlX);
         channelField.setY(cy + 2);
         channelField.setWidth(ctrlW);
         channelField.render(ctx, mx, my, delta);
         cy += 17;
      }

      if (this.payloadEditorModel != null && this.payloadEditorModel.commandApiRecognized) {
         RiptideChatField commandField = this.textFields.get("payload_command_value");
         if (commandField != null) {
            int lw = this.labelWidth(w, "CommandApi", font, 64);
            this.drawLabel(ctx, "CommandApi", x, cy, lw, font);
            int ctrlX = this.controlX(x, lw);
            int ctrlW = this.controlWidth(w, lw);
            int btnW = 58;
            int fieldW = Math.max(38, ctrlW - btnW - 3);
            commandField.setX(ctrlX);
            commandField.setY(cy + 2);
            commandField.setWidth(fieldW);
            commandField.render(ctx, mx, my, delta);
            boolean override = this.toggleStates.getOrDefault("payload_command_api", this.payloadEditorModel.commandApiOverride);
            this.renderOverlayButton(
               ctx,
               ctrlX + fieldW + 3,
               cy + 2,
               btnW,
               14,
               override ? "Override" : "Exact",
               override ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY,
               true,
               mx,
               my,
               () -> {
                  if (this.payloadEditorModel != null) {
                     this.payloadEditorModel.commandApiOverride = !this.payloadEditorModel.commandApiOverride;
                     this.payloadEditorModel.touch();
                     this.payloadBuildCache = null;
                     this.toggleStates.put("payload_command_api", this.payloadEditorModel.commandApiOverride);
                     this.payloadContentEdited = true;
                  }

                  this.refreshInteractiveLayout();
               }
            );
            cy += 17;
         }
      }

      if (this.payloadTabIndex == 0) {
         UiText.draw(ctx, this.textRenderer, "Packet Script", font, RiptideColors.textSecondary(), x, cy + 2, false);
         this.renderOverlayButton(
            ctx, x + w - 42, cy, 42, 14, "Clear", CompactOverlayButton.Variant.DANGER, !this.payloadFieldsFromEditor().isEmpty(), mx, my, () -> {
               RiptideChatField contentField = this.textFields.get("payload_content");
               if (contentField != null) {
                  contentField.setText("");
               }

               this.payloadContentEdited = true;
               this.refreshInteractiveLayout();
            }
         );
         cy += 15;
         int addButtonW = 62;
         this.renderPayloadAddDropdown(x, cy, Math.max(54, w - addButtonW - 4), mx, my);
         this.renderOverlayButton(
            ctx,
            x + w - addButtonW,
            cy,
            addButtonW,
            14,
            "Add",
            CompactOverlayButton.Variant.SUCCESS,
            true,
            mx,
            my,
            () -> this.appendPayloadFieldType(
               this.enumIndices.getOrDefault("payload_add_type", this.payloadTypeIndex(RiptidePayloadTemplate.FieldType.STRING_BYTES))
            )
         );
         cy += 17;
         RiptideChatField contentField = this.textFields.get("payload_content");
         if (contentField != null) {
            contentField.setX(x);
            contentField.setY(cy);
            contentField.setWidth(w);
            contentField.setHeight(126);
            contentField.render(ctx, mx, my, delta);
         }

         cy += 130;
      } else if (this.payloadTabIndex == 1) {
         this.syncPayloadPreviewFieldsFromModel(false);
         RiptideChatField hexView = this.textFields.get("payload_hex_view");
         if (hexView != null) {
            int bytes = this.payloadEditorModel == null ? built.bytes().length : this.payloadEditorModel.bodyBytes.length;
            UiText.draw(ctx, this.textRenderer, "Body Hex - payload body only (" + bytes + " bytes)", font, RiptideColors.textSecondary(), x, cy + 2, false);
            cy += 14;
            hexView.setX(x);
            hexView.setY(cy);
            hexView.setWidth(w);
            hexView.setHeight(138);
            hexView.render(ctx, mx, my, delta);
            cy += 142;
         }
      } else if (this.payloadTabIndex == 2) {
         this.syncPayloadPreviewFieldsFromModel(false);
         RiptideChatField utf8View = this.textFields.get("payload_utf8_view");
         if (utf8View != null) {
            UiText.draw(ctx, this.textRenderer, "UTF-8 / Raw Text Body", font, RiptideColors.textSecondary(), x, cy + 2, false);
            cy += 14;
            utf8View.setX(x);
            utf8View.setY(cy);
            utf8View.setWidth(w);
            utf8View.setHeight(138);
            utf8View.render(ctx, mx, my, delta);
            cy += 142;
         }
      } else {
         this.syncPayloadPreviewFieldsFromModel(false);
         RiptideChatField logicalView = this.textFields.get("payload_logical_view");
         if (logicalView != null) {
            UiText.draw(ctx, this.textRenderer, "Logical Packet - envelope + body", font, RiptideColors.textSecondary(), x, cy + 2, false);
            cy += 14;
            logicalView.setX(x);
            logicalView.setY(cy);
            logicalView.setWidth(w);
            logicalView.setHeight(138);
            logicalView.render(ctx, mx, my, delta);
            cy += 142;
         }
      }

      int halfW = (w - 4) / 2;
      String notice = this.payloadValidationText(built);
      boolean modelBad = this.payloadEditorModel != null
         && this.payloadEditorModel.validationError != null
         && !this.payloadEditorModel.validationError.isBlank();
      int noticeColor = !modelBad && built.errors().isEmpty() ? (!built.warnings().isEmpty() ? -14249 : RiptideColors.textDim()) : RiptideColors.dangerText();
      UiText.draw(
         ctx, this.textRenderer, UiText.trimToWidth(this.textRenderer, notice, Math.max(10, w), font, noticeColor), font, noticeColor, x, cy + 2, false
      );
      cy += 14;
      this.renderOverlayButton(
         ctx,
         x,
         cy,
         halfW,
         16,
         this.configurationOnly ? "Send: In Game" : "Send",
         CompactOverlayButton.Variant.SUCCESS,
         !this.configurationOnly && built.ok() && !modelBad,
         mx,
         my,
         () -> {
            if (!this.configurationOnly) {
               try {
                  PayloadAction action = this.buildPayloadActionFromEditor();
                  action.execute(MC);
               } catch (Exception var2x) {
                  RiptideClientMessaging.sendPrefixed("§cPayload send failed: " + RiptidePayloadSupport.safeMessage(var2x));
               }
            }
         }
      );
      this.renderOverlayButton(ctx, x + halfW + 4, cy, halfW, 16, "Reset", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
         this.resetPayloadEditorFields();
         this.refreshInteractiveLayout();
      });
      cy += 20;
      if (this.isPayloadConfigPhase()) {
         cy = this.renderEditorHint(ctx, x, cy, w, "Config-phase payload — sending may disconnect.", -1657776);
      }

      this.renderEditorHint(ctx, x, cy, w, this.payloadEditorHint());
   }

   private int renderPayloadTabs(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      String[] tabs = new String[]{"Packet", "Body Hex", "UTF-8", "Logical"};
      int gap = 3;
      int tabW = Math.max(42, (w - gap * (tabs.length - 1)) / tabs.length);

      for (int i = 0; i < tabs.length; i++) {
         int tx = x + i * (tabW + gap);
         int tw = i == tabs.length - 1 ? Math.max(30, x + w - tx) : tabW;
         CompactOverlayButton.Variant variant = this.payloadTabIndex == i ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY;
         int index = i;
         this.renderOverlayButton(ctx, tx, y, tw, 14, tabs[i], variant, true, mx, my, () -> {
            this.flushPendingPayloadEdit(true);
            this.payloadTabIndex = index;
            this.clearPayloadFieldFocus();
            this.refreshInteractiveLayout();
         });
      }

      return y + 18;
   }

   private void clearPayloadFieldFocus() {
      for (String key : List.of("payload_content", "payload_hex_view", "payload_utf8_view", "payload_logical_view")) {
         RiptideChatField field = this.textFields.get(key);
         if (field != null) {
            field.setFocused(false);
         }
      }
   }

   private void renderPayloadAddDropdown(int x, int y, int w, int mx, int my) {
      int idx = Math.max(
         0,
         Math.min(
            this.enumIndices
               .getOrDefault(
                  "payload_add_type",
                  this.payloadTypeIndex(
                     this.suggestPayloadAddType(
                        this.payloadEditorChannel(), this.payloadModeFromEditor(this.payloadEditorChannel()), this.payloadFieldsFromEditor()
                     )
                  )
               ),
            PAYLOAD_FIELD_TYPE_LABELS.size() - 1
         )
      );
      CompactDropdown dd = this.enumDropdownCache
         .computeIfAbsent("payload_add_type", key -> new CompactDropdown(x, y, w, 14, PAYLOAD_FIELD_TYPE_LABELS, idx, newIdx -> {
            this.payloadAddTypeManuallyChanged = true;
            this.enumIndices.put("payload_add_type", newIdx);
         }));
      dd.setBounds(x, y, w, 14).setOptions(PAYLOAD_FIELD_TYPE_LABELS).setSelectedIndex(idx).setOnSelect(newIdx -> {
         this.payloadAddTypeManuallyChanged = true;
         this.enumIndices.put("payload_add_type", newIdx);
      }).setButtonLabelOverride("");
      this.enumDropdowns.add(dd);
   }

   private void appendPayloadFieldType(int index) {
      if (index >= 0 && index < PAYLOAD_FIELD_TYPES.size()) {
         RiptidePayloadTemplate.FieldType type = PAYLOAD_FIELD_TYPES.get(index);
         this.appendPayloadField(type.label() + " = " + type.defaultValue());
         if (!this.payloadModeManuallyChanged) {
            this.enumIndices
               .put(
                  "payload_mode",
                  this.payloadModeIndex(
                     this.suggestPayloadMode(
                        this.payloadEditorChannel(),
                        List.of(new RiptidePayloadTemplate.Field(type, type.defaultValue(), true)),
                        this.payloadModeFromEditor(this.payloadEditorChannel())
                     )
                  )
               );
         }

         this.refreshInteractiveLayout();
      }
   }

   private void updatePayloadPreviewFields(RiptidePayloadTemplate.BuildResult built) {
      RiptideChatField hexView = this.textFields.get("payload_hex_view");
      if (hexView != null && !hexView.isFocused()) {
         this.suppressPayloadEditorChange = true;

         try {
            hexView.setText(this.formatPayloadHex(built.bytes()));
         } finally {
            this.suppressPayloadEditorChange = false;
         }
      }

      RiptideChatField utf8View = this.textFields.get("payload_utf8_view");
      if (utf8View != null && !utf8View.isFocused()) {
         this.suppressPayloadEditorChange = true;

         try {
            utf8View.setText(this.formatPayloadUtf8View(built));
         } finally {
            this.suppressPayloadEditorChange = false;
         }
      }
   }

   private String formatPayloadHex(byte[] bytes) {
      if (bytes != null && bytes.length != 0) {
         StringBuilder sb = new StringBuilder(bytes.length * 3 + bytes.length / 16 * 10);

         for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
               sb.append((char)(i % 16 == 0 ? '\n' : ' '));
            }

            sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 255));
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private String formatPayloadUtf8View(RiptidePayloadTemplate.BuildResult built) {
      RiptidePayloadTemplate.Preview preview = built.preview();
      String fullUtf8 = RiptidePayloadSupport.decodeLikelyUtf8Text(built.bytes());
      if (fullUtf8 != null && !fullUtf8.isBlank()) {
         return fullUtf8;
      } else if (preview.utf8() != null && !preview.utf8().isBlank()) {
         return preview.utf8();
      } else if (preview.minecraftString() != null && !preview.minecraftString().isBlank()) {
         return preview.minecraftString();
      } else {
         return preview.javaWriteUtf() != null && !preview.javaWriteUtf().isBlank() ? preview.javaWriteUtf() : "";
      }
   }

   private String payloadValidationText(RiptidePayloadTemplate.BuildResult built) {
      if (this.payloadEditorModel != null && this.payloadEditorModel.validationError != null && !this.payloadEditorModel.validationError.isBlank()) {
         return "Fix: " + this.shortPayloadMessage(this.payloadEditorModel.validationError);
      } else {
         String scriptIssue = this.payloadScriptIssue();
         if (scriptIssue != null) {
            return "Fix: " + this.shortPayloadMessage(scriptIssue);
         } else {
            if (this.payloadTabIndex == 1) {
               RiptideChatField hexView = this.textFields.get("payload_hex_view");
               if (hexView != null && hexView.isFocused()) {
                  try {
                     this.parsePayloadHexView(hexView.getText());
                  } catch (Exception var5) {
                     return "Bad hex: " + this.shortPayloadMessage(RiptidePayloadSupport.safeMessage(var5));
                  }
               }
            }

            if (!built.errors().isEmpty()) {
               return "Fix: " + this.shortPayloadMessage(built.errors().get(0));
            } else {
               return !built.warnings().isEmpty() ? "Warn: " + this.shortPayloadMessage(built.warnings().get(0)) : "Ready: " + built.bytes().length + " bytes";
            }
         }
      }
   }

   private String shortPayloadMessage(String message) {
      String text = message == null ? "invalid payload" : message.replace('\n', ' ').strip();
      return text.length() <= 34 ? text : text.substring(0, 31) + "...";
   }

   private byte[] parsePayloadHexView(String text) {
      String value = text == null ? "" : text.strip();
      if (value.isEmpty()) {
         return new byte[0];
      } else {
         String compact = value.replaceAll("(?i)0x", "").replaceAll("\\s+", "");
         if (compact.isEmpty()) {
            return new byte[0];
         } else if ((compact.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex needs byte pairs");
         } else if (!compact.matches("(?i)[0-9a-f]+")) {
            throw new IllegalArgumentException("Only hex digits are allowed here");
         } else {
            byte[] out = new byte[compact.length() / 2];

            for (int i = 0; i < compact.length(); i += 2) {
               out[i / 2] = (byte)Integer.parseInt(compact.substring(i, i + 2), 16);
            }

            return out;
         }
      }
   }

   private String readablePayloadUtf8(byte[] bytes) {
      String text = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
      if (text != null && !text.isBlank()) {
         for (int i = 0; i < text.length(); i++) {
            char chr = text.charAt(i);
            if (chr != '\n' && chr != '\r' && chr != '\t' && (Character.isISOControl(chr) || chr == '�')) {
               return null;
            }
         }

         return text;
      } else {
         return null;
      }
   }

   private String payloadScriptIssue() {
      RiptideChatField contentField = this.textFields.get("payload_content");
      if (contentField == null) {
         return null;
      } else {
         String text = contentField.getText();
         if (text != null && !text.isBlank()) {
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

            for (int i = 0; i < lines.length; i++) {
               String raw = lines[i] == null ? "" : lines[i];
               String line = raw.strip();
               if (!line.isEmpty() && !line.startsWith("#")) {
                  int split = this.firstPayloadDelimiter(raw);
                  if (split < 0) {
                     return "Line " + (i + 1) + " needs writer = value";
                  }

                  String typeText = raw.substring(0, split).strip();
                  if (!this.isPayloadEnvelopeKey(typeText) && this.payloadFieldTypeFromToken(typeText) == null) {
                     return "Line " + (i + 1) + " unknown writer";
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }

   private long payloadEditorRevision() {
      return this.payloadEditorModel == null ? 0L : this.payloadEditorModel.revision();
   }

   private Component payloadScriptComponent(String source) {
      MutableComponent out = Component.empty();
      String safe = source == null ? "" : source;
      String[] lines = safe.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

      for (int i = 0; i < lines.length; i++) {
         if (i > 0) {
            this.appendPayloadStyled(out, "\n", 7829367);
         }

         String line = lines[i];
         if (!line.isEmpty()) {
            String stripped = line.stripLeading();
            if (stripped.startsWith("#")) {
               this.appendPayloadStyled(out, line, 7829367);
            } else {
               int split = this.firstPayloadDelimiter(line);
               if (split < 0) {
                  this.appendPayloadStyled(out, line, 16742263);
               } else {
                  String typePart = line.substring(0, split);
                  String sep = line.substring(split, split + 1);
                  String valuePart = line.substring(split + 1);
                  RiptidePayloadTemplate.FieldType type = this.payloadFieldTypeFromToken(typePart.strip());
                  this.appendPayloadStyled(out, typePart, this.isPayloadEnvelopeKey(typePart.strip()) ? 16328306 : (type == null ? 16742263 : 6740463));
                  this.appendPayloadStyled(out, sep, 11184810);
                  this.appendPayloadValueStyled(out, valuePart);
               }
            }
         }
      }

      return out;
   }

   private boolean isPayloadEnvelopeKey(String key) {
      if (key == null) {
         return false;
      } else {
         String var2 = key.strip().toLowerCase(Locale.ROOT);

         return switch (var2) {
            case "direction", "phase", "channel", "packetid", "packet_id", "packetclass", "packet_class", "sourceprotocol", "source_protocol", "provenance" -> true;
            default -> false;
         };
      }
   }

   private Component payloadHexComponent(String source) {
      MutableComponent out = Component.empty();
      String safe = source == null ? "" : source;
      int hexCount = 0;

      for (int i = 0; i < safe.length(); i++) {
         char chr = safe.charAt(i);
         if (chr == '\n' || Character.isWhitespace(chr)) {
            this.appendPayloadStyled(out, Character.toString(chr), 7829367);
         } else if (this.isPayloadHexDigit(chr)) {
            int color = hexCount / 2 % 2 == 0 ? 10936878 : 6740463;
            this.appendPayloadStyled(out, Character.toString(chr), color);
            hexCount++;
         } else {
            this.appendPayloadStyled(out, Character.toString(chr), 16742263);
         }
      }

      return out;
   }

   private Component payloadUtf8Component(String source) {
      MutableComponent out = Component.empty();
      String safe = source == null ? "" : source;
      boolean inString = false;
      boolean escaped = false;

      for (int i = 0; i < safe.length(); i++) {
         char chr = safe.charAt(i);
         int color;
         if (chr == '\n') {
            color = 7829367;
         } else if (Character.isWhitespace(chr)) {
            color = 7829367;
         } else if (escaped) {
            color = 11436543;
            escaped = false;
         } else if (chr == '\\') {
            color = 11436543;
            escaped = true;
         } else if (chr == '"' || chr == '\'') {
            color = 15129460;
            inString = !inString;
         } else if (inString) {
            color = 15129460;
         } else if ("{}[],:=".indexOf(chr) >= 0) {
            color = 6740463;
         } else if (Character.isDigit(chr) || chr == '-' || chr == '+') {
            color = 11436543;
         } else if (Character.isLetter(chr)) {
            color = 10936878;
         } else {
            color = 16316658;
         }

         this.appendPayloadStyled(out, Character.toString(chr), color);
      }

      return out;
   }

   private Component payloadLogicalComponent(String source) {
      MutableComponent out = Component.empty();
      String safe = source == null ? "" : source;
      String[] lines = safe.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

      for (int i = 0; i < lines.length; i++) {
         if (i > 0) {
            this.appendPayloadStyled(out, "\n", 7829367);
         }

         String line = lines[i];
         int split = this.firstPayloadDelimiter(line);
         if (split < 0) {
            this.appendPayloadStyled(out, line, line.strip().startsWith("#") ? 7829367 : 16316658);
         } else {
            String key = line.substring(0, split);
            String sep = line.substring(split, split + 1);
            String value = line.substring(split + 1);
            this.appendPayloadStyled(out, key, !this.isPayloadEnvelopeKey(key) && !key.toLowerCase(Locale.ROOT).contains("hex") ? 10936878 : 6740463);
            this.appendPayloadStyled(out, sep, 11184810);
            if (key.toLowerCase(Locale.ROOT).contains("hex")) {
               this.appendPayloadStyled(out, value, 15129460);
            } else {
               this.appendPayloadValueStyled(out, value);
            }
         }
      }

      return out;
   }

   private void appendPayloadValueStyled(MutableComponent out, String valuePart) {
      if (valuePart != null && !valuePart.isEmpty()) {
         int leading = 0;

         while (leading < valuePart.length() && Character.isWhitespace(valuePart.charAt(leading))) {
            leading++;
         }

         if (leading > 0) {
            this.appendPayloadStyled(out, valuePart.substring(0, leading), 11184810);
         }

         String value = valuePart.substring(leading);
         int color = this.looksNumericPayloadValue(value) ? 11436543 : (this.looksBooleanPayloadValue(value) ? 16328306 : 15129460);
         this.appendPayloadStyled(out, value, color);
      }
   }

   private void appendPayloadStyled(MutableComponent out, String text, int color) {
      if (text != null && !text.isEmpty()) {
         out.append(UiText.literal(text, this.theme.fontFor(UiTone.BODY), color));
      }
   }

   private int firstPayloadDelimiter(String line) {
      if (line == null) {
         return -1;
      } else {
         int eq = line.indexOf(61);
         int colon = line.indexOf(58);
         if (eq < 0) {
            return colon;
         } else {
            return colon < 0 ? eq : Math.min(eq, colon);
         }
      }
   }

   private RiptidePayloadTemplate.FieldType payloadFieldTypeFromToken(String token) {
      if (token != null && !token.isBlank()) {
         String normalized = token.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

         for (RiptidePayloadTemplate.FieldType type : RiptidePayloadTemplate.FieldType.values()) {
            if (type.name().equals(normalized) || type.label().equalsIgnoreCase(token.strip())) {
               return type;
            }
         }

         if ("UUID".equals(normalized)) {
            return RiptidePayloadTemplate.FieldType.UUID_FIELD;
         } else if ("UTF".equals(normalized) || "WRITEUTF".equals(normalized)) {
            return RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF;
         } else if ("MC_STRING".equals(normalized) || "MINECRAFT_STRING".equals(normalized)) {
            return RiptidePayloadTemplate.FieldType.MINECRAFT_STRING;
         } else {
            return !"COMPONENT".equals(normalized) && !"TEXT_COMPONENT".equals(normalized) ? null : RiptidePayloadTemplate.FieldType.TEXT_COMPONENT;
         }
      } else {
         return null;
      }
   }

   private boolean isPayloadHexDigit(char chr) {
      return chr >= '0' && chr <= '9' || chr >= 'a' && chr <= 'f' || chr >= 'A' && chr <= 'F';
   }

   private boolean looksNumericPayloadValue(String value) {
      String text = value == null ? "" : value.strip();
      return text.matches("-?(0x[0-9a-fA-F]+|\\d+)(\\.\\d+)?");
   }

   private boolean looksBooleanPayloadValue(String value) {
      String text = value == null ? "" : value.strip();
      return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
   }

   private void renderPayloadModeDropdown(int x, int y, int w, int mx, int my, RiptidePayloadTemplate.Template template) {
      int idx = this.payloadModeIndex(template == null ? RiptidePayloadTemplate.EncodingMode.ADVANCED_MIXED : template.mode());
      CompactDropdown dd = this.enumDropdownCache.computeIfAbsent("payload_mode", key -> new CompactDropdown(x, y, w, 14, PAYLOAD_MODE_LABELS, idx, newIdx -> {
         if (newIdx >= 0 && newIdx < PAYLOAD_MODES.size()) {
            this.flushPendingPayloadEdit(true);
            this.payloadModeManuallyChanged = true;
            this.enumIndices.put("payload_mode", newIdx);
            if (this.payloadAction != null) {
               this.payloadAction.payloadEncodingMode = PAYLOAD_MODES.get(newIdx).name();
            }

            if (this.payloadEditorModel != null) {
               this.payloadEditorModel.encodingMode = PAYLOAD_MODES.get(newIdx).name();
               this.payloadEditorModel.touch();
               this.payloadBuildCache = null;
               this.syncPayloadPreviewFieldsFromModel(false);
            }

            this.payloadContentEdited = true;
         }
      }));
      dd.setBounds(x, y, w, 14).setOptions(PAYLOAD_MODE_LABELS).setSelectedIndex(idx).setOnSelect(newIdx -> {
         if (newIdx >= 0 && newIdx < PAYLOAD_MODES.size()) {
            this.flushPendingPayloadEdit(true);
            this.payloadModeManuallyChanged = true;
            this.enumIndices.put("payload_mode", newIdx);
            if (this.payloadAction != null) {
               this.payloadAction.payloadEncodingMode = PAYLOAD_MODES.get(newIdx).name();
            }

            if (this.payloadEditorModel != null) {
               this.payloadEditorModel.encodingMode = PAYLOAD_MODES.get(newIdx).name();
               this.payloadEditorModel.touch();
               this.payloadBuildCache = null;
               this.syncPayloadPreviewFieldsFromModel(false);
            }

            this.payloadContentEdited = true;
         }
      });
      this.enumDropdowns.add(dd);
   }

   private int renderPayloadFieldRow(GuiGraphicsExtractor ctx, int x, int y, int w, int row, int mx, int my, float delta) {
      int btnH = 14;
      int typeW = Math.min(116, Math.max(88, (w - 70) / 2));
      int gap = 3;
      int actionW = 19;
      int actionsW = actionW * 3 + gap * 2;
      int valueX = x + typeW + gap;
      int valueW = Math.max(36, w - typeW - gap - actionsW - gap);
      this.renderPayloadTypeDropdown(row, x, y + 2, typeW, mx, my);
      RiptideChatField valueField = this.textFields.get(this.payloadValueKey(row));
      if (valueField != null) {
         RiptidePayloadTemplate.FieldType type = this.payloadTypeAt(row);
         valueField.setPlaceholder(Component.literal(type.defaultValue()));
         valueField.setX(valueX);
         valueField.setY(y + 3);
         valueField.setWidth(valueW);
         valueField.render(ctx, mx, my, delta);
      }

      int ax = valueX + valueW + gap;
      this.renderOverlayButton(
         ctx, ax, y + 2, actionW, btnH, "Up", CompactOverlayButton.Variant.SECONDARY, row > 0, mx, my, () -> this.movePayloadField(row, -1)
      );
      this.renderOverlayButton(
         ctx,
         ax + actionW + gap,
         y + 2,
         actionW,
         btnH,
         "Dn",
         CompactOverlayButton.Variant.SECONDARY,
         row < this.payloadFieldCount - 1,
         mx,
         my,
         () -> this.movePayloadField(row, 1)
      );
      this.renderOverlayButton(
         ctx,
         ax + (actionW + gap) * 2,
         y + 2,
         actionW,
         btnH,
         "X",
         CompactOverlayButton.Variant.DANGER,
         this.payloadFieldCount > 1,
         mx,
         my,
         () -> this.removePayloadField(row)
      );
      return y + 18;
   }

   private void renderPayloadTypeDropdown(int row, int x, int y, int w, int mx, int my) {
      String key = this.payloadTypeKey(row);
      int idx = Math.max(
         0,
         Math.min(
            this.enumIndices.getOrDefault(key, this.payloadTypeIndex(RiptidePayloadTemplate.FieldType.STRING_BYTES)), PAYLOAD_FIELD_TYPE_LABELS.size() - 1
         )
      );
      CompactDropdown dd = this.enumDropdownCache
         .computeIfAbsent(key, cacheKey -> new CompactDropdown(x, y, w, 14, PAYLOAD_FIELD_TYPE_LABELS, idx, newIdx -> this.selectPayloadType(row, newIdx)));
      dd.setBounds(x, y, w, 14).setOptions(PAYLOAD_FIELD_TYPE_LABELS).setSelectedIndex(idx).setOnSelect(newIdx -> this.selectPayloadType(row, newIdx));
      this.enumDropdowns.add(dd);
   }

   private void selectPayloadType(int row, int newIdx) {
      if (newIdx >= 0 && newIdx < PAYLOAD_FIELD_TYPES.size()) {
         String key = this.payloadTypeKey(row);
         int previous = this.enumIndices.getOrDefault(key, -1);
         this.enumIndices.put(key, newIdx);
         if (previous != newIdx) {
            RiptideChatField valueField = this.textFields.get(this.payloadValueKey(row));
            if (valueField != null && valueField.getText().isBlank()) {
               valueField.setText(PAYLOAD_FIELD_TYPES.get(newIdx).defaultValue());
            }

            this.payloadContentEdited = true;
         }
      }
   }

   private void movePayloadField(int row, int direction) {
      List<RiptidePayloadTemplate.Field> fields = new ArrayList<>(this.payloadFieldsFromEditor());
      int target = row + direction;
      if (row >= 0 && row < fields.size() && target >= 0 && target < fields.size()) {
         Collections.swap(fields, row, target);
         this.setPayloadFieldsInEditor(fields);
         this.payloadContentEdited = true;
         this.refreshInteractiveLayout();
      }
   }

   private void removePayloadField(int row) {
      List<RiptidePayloadTemplate.Field> fields = new ArrayList<>(this.payloadFieldsFromEditor());
      if (fields.size() > 1 && row >= 0 && row < fields.size()) {
         fields.remove(row);
         this.setPayloadFieldsInEditor(fields);
         this.payloadContentEdited = true;
         this.refreshInteractiveLayout();
      }
   }

   private RiptidePayloadTemplate.Template currentPayloadTemplateFromEditor() {
      if (this.payloadEditorModel != null) {
         return this.currentPayloadTemplateFromModel();
      } else {
         PayloadAction action = new PayloadAction();
         if (this.payloadAction != null) {
            action.fromTag(this.payloadAction.toTag());
         }

         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null && !channelField.getText().isBlank()) {
            action.channel = channelField.getText().strip();
         }

         action.payloadPhase = "PLAY";
         action.payloadEncodingMode = this.payloadModeFromEditor(action.channel).name();
         action.payloadFields = RiptidePayloadTemplate.serializeFields(this.payloadFieldsFromEditor());
         return RiptidePayloadTemplate.fromAction(action);
      }
   }

   private ActionEditorOverlay.PayloadBuildCache payloadBuildPreview() {
      if (this.payloadEditorModel == null) {
         RiptidePayloadTemplate.Template template = this.currentPayloadTemplateFromEditor();
         return new ActionEditorOverlay.PayloadBuildCache(Long.MIN_VALUE, template, template.build());
      } else {
         long revision = this.payloadEditorModel.revision();
         ActionEditorOverlay.PayloadBuildCache hit = this.payloadBuildCache;
         if (hit != null && hit.revision() == revision) {
            return hit;
         } else {
            RiptidePayloadTemplate.Template template = this.currentPayloadTemplateFromModel();
            hit = new ActionEditorOverlay.PayloadBuildCache(revision, template, template.build());
            this.payloadBuildCache = hit;
            return hit;
         }
      }
   }

   private RiptidePayloadTemplate.Template currentPayloadTemplateFromModel() {
      return this.payloadEditorModel == null ? RiptidePayloadTemplate.fromAction(new PayloadAction()) : this.payloadEditorModel.toTemplate();
   }

   private void appendPayloadField(String line) {
      RiptideChatField contentField = this.textFields.get("payload_content");
      if (contentField != null && line != null && !line.isBlank()) {
         String current = contentField.getText() == null ? "" : contentField.getText().stripTrailing();
         contentField.setText(current.isBlank() ? line : current + "\n" + line);
         this.payloadContentEdited = true;
      }
   }

   private void applyPayloadTemplateToEditor(RiptidePayloadTemplate.Template template) {
      if (template != null) {
         if (this.payloadAction != null) {
            this.payloadAction.channel = template.channel();
            this.payloadAction.payloadDirection = template.direction().name();
            this.payloadAction.payloadPhase = template.phase().name();
            this.payloadAction.payloadEncodingMode = template.mode().name();
            this.payloadAction.payloadFields = RiptidePayloadTemplate.serializeFields(template.fields());
         }

         this.suppressPayloadEditorChange = true;

         try {
            if (this.payloadEditorModel != null) {
               this.payloadEditorModel.channel = template.channel();
               this.payloadEditorModel.direction = template.direction().name();
               this.payloadEditorModel.phase = template.phase().name();
               this.payloadEditorModel.encodingMode = template.mode().name();
               this.payloadEditorModel.bodyFields = new ArrayList<>(template.fields());
               this.payloadEditorModel.touch();
               this.payloadEditorModel.applyPacketScript(this.payloadEditorModel.packetScript());
               this.payloadBuildCache = null;
            }

            RiptideChatField channelField = this.textFields.get("payload_channel");
            if (channelField != null) {
               channelField.setText(template.channel());
            }

            if (this.payloadEditorModel == null) {
               this.setPayloadFieldsInEditor(template.fields());
            } else {
               this.syncPayloadAllFieldsFromModel(true);
            }
         } finally {
            this.suppressPayloadEditorChange = false;
         }

         this.payloadChannelEdited = true;
         this.payloadContentEdited = true;
         this.refreshInteractiveLayout();
      }
   }

   private int renderPayloadStatus(GuiGraphicsExtractor ctx, int x, int y, int w) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      String status = this.payloadReplayStatus();
      String transport = this.payloadEditorModel == null
         ? "Encoder: vanilla"
         : this.payloadEditorModel.direction
            + " / "
            + this.payloadEditorModel.phase
            + (this.payloadEditorModel.packetId >= 0 ? " / id " + this.payloadEditorModel.packetId : " / id ?");
      UiText.draw(
         ctx,
         this.textRenderer,
         UiText.trimToWidth(this.textRenderer, status, Math.max(10, w), font, RiptideColors.textPrimary()),
         font,
         RiptideColors.textPrimary(),
         x,
         y + 2,
         false
      );
      y += 11;
      UiText.draw(
         ctx,
         this.textRenderer,
         UiText.trimToWidth(this.textRenderer, transport, Math.max(10, w), font, RiptideColors.textDim()),
         font,
         RiptideColors.textDim(),
         x,
         y + 2,
         false
      );
      return y + 13;
   }

   private String payloadReplayStatus() {
      if (this.payloadEditorModel != null) {
         String decoded = this.payloadEditorModel.decodedKind == null ? "payload" : this.payloadEditorModel.decodedKind;
         int bytes = this.payloadEditorModel.bodyBytes == null ? 0 : this.payloadEditorModel.bodyBytes.length;
         return !this.payloadContentEdited && !this.payloadChannelEdited ? "Ready: " + bytes + "B " + decoded : "Edited: " + bytes + "B " + decoded;
      } else if (this.payloadContentEdited) {
         return "Edited: script rebuilds bytes";
      } else {
         return this.payloadChannelEdited ? "Edited: channel changed" : "Payload builder ready";
      }
   }

   private String payloadContentLabel() {
      return switch (this.payloadContentMode) {
         case UTF8_TEXT -> "Payload Component";
         case BRAND_STRING -> "Brand";
         case COMMAND_INT -> "Payload Value";
         case BINARY_REPLAY -> "Payload";
      };
   }

   private String payloadEditorHint() {
      return "Rows run top to bottom.";
   }

   private boolean isPayloadConfigPhase() {
      return this.payloadEditorModel != null && this.payloadEditorModel.phase != null
         ? this.payloadEditorModel.phase.toLowerCase(Locale.ROOT).contains("config")
         : this.payloadAction != null && this.payloadAction.payloadPhase != null && this.payloadAction.payloadPhase.toLowerCase(Locale.ROOT).contains("config");
   }

   private void resetPayloadEditorFields() {
      this.pendingPayloadEdit = null;
      this.payloadEditorModel = RiptidePayloadEditorModel.fromAction(this.payloadAction);
      this.payloadPreviewSyncedRevision = Long.MIN_VALUE;
      this.payloadBuildCache = null;
      ActionEditorOverlay.PayloadEditorState state = this.resolvePayloadEditorState(this.payloadAction);
      this.payloadContentMode = state.contentMode();
      this.suppressPayloadEditorChange = true;

      try {
         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null) {
            channelField.setText(state.channel());
         }

         this.syncPayloadAllFieldsFromModel(true);
         this.payloadChannelEdited = false;
         this.payloadContentEdited = false;
         this.payloadRawEdited = false;
         this.payloadJsonEdited = false;
         this.payloadModeManuallyChanged = false;
         this.payloadAddTypeManuallyChanged = false;
      } finally {
         this.suppressPayloadEditorChange = false;
      }

      this.autoSelectPayloadControls();
   }

   private PayloadAction buildCleanPayloadActionFromEditor() {
      this.flushPendingPayloadEdit(true);
      if (this.payloadEditorModel != null) {
         if (this.payloadEditorModel.validationError != null && !this.payloadEditorModel.validationError.isBlank()) {
            throw new IllegalArgumentException(this.payloadEditorModel.validationError);
         } else {
            PayloadAction action = this.payloadEditorModel.toAction(this.payloadAction);
            if (action.channel == null || action.channel.isBlank()) {
               action.channel = "minecraft:brand";
            }

            RiptidePayloadTemplate.BuildResult built = this.payloadEditorModel.toTemplate().build();
            if (!built.ok()) {
               throw new IllegalArgumentException(String.join("; ", built.errors()));
            } else {
               action.payloadData = RiptidePayloadSupport.toHex(built.bytes());
               return action;
            }
         }
      } else {
         PayloadAction actionx = new PayloadAction();
         if (this.payloadAction != null) {
            actionx.fromTag(this.payloadAction.toTag());
         }

         actionx.payloadClassName = "";
         actionx.commandApiOverride = false;
         actionx.javaSource = "";
         actionx.payloadScriptEnabled = false;
         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null) {
            String editorChannel = channelField.getText().strip();
            if (!editorChannel.isBlank()) {
               actionx.channel = editorChannel;
            }
         }

         actionx.payloadFields = RiptidePayloadTemplate.serializeFields(this.payloadFieldsFromEditor());
         if (actionx.payloadDirection == null || actionx.payloadDirection.isBlank()) {
            actionx.payloadDirection = "C2S";
         }

         actionx.payloadPhase = "PLAY";
         actionx.payloadEncodingMode = this.payloadModeFromEditor(actionx.channel).name();
         RiptidePayloadTemplate.Template template = RiptidePayloadTemplate.fromAction(actionx);
         RiptidePayloadTemplate.BuildResult built = template.build();
         if (!built.ok()) {
            throw new IllegalArgumentException(String.join("; ", built.errors()));
         } else {
            actionx.channel = template.channel();
            actionx.payloadDirection = template.direction().name();
            actionx.payloadPhase = "PLAY";
            actionx.payloadEncodingMode = template.mode().name();
            actionx.payloadFields = RiptidePayloadTemplate.serializeFields(template.fields());
            actionx.sourceDirection = actionx.payloadDirection;
            actionx.sourceProtocol = "";
            actionx.payloadData = RiptidePayloadSupport.toHex(built.bytes());
            actionx.payloadJson = "";
            actionx.commandApiRecognized = false;
            actionx.commandApiOverride = false;
            if (actionx.channel == null || actionx.channel.isBlank()) {
               actionx.channel = this.payloadAction != null && this.payloadAction.channel != null && !this.payloadAction.channel.isBlank()
                  ? this.payloadAction.channel
                  : "minecraft:brand";
            }

            return actionx;
         }
      }
   }

   private void renderPayloadPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      if (this.payloadAction == null && this.targetAction == null) {
         Identifier font = this.theme.fontFor(UiTone.BODY);
         int cy = bodyTop + 4;
         UiText.draw(ctx, this.textRenderer, "Custom Payload Editor", font, RiptideColors.textSecondary(), x, cy + 2, false);
         cy += 13;
         UiText.draw(ctx, this.textRenderer, "Packet JSON", font, RiptideColors.textSecondary(), x, cy + 2, false);
         cy += 13;
         RiptideChatField jsonField = this.textFields.get("payload_json");
         if (jsonField != null) {
            jsonField.setX(x);
            jsonField.setY(cy);
            jsonField.setWidth(w);
            jsonField.setHeight(74);
            jsonField.render(ctx, mx, my, delta);
            cy += 78;
         }

         int halfW = (w - 4) / 2;
         this.renderActionButton(ctx, x, cy, halfW, 14, "Pretty JSON", mx, my, () -> {
            this.formatPayloadJsonField(false);
            this.refreshInteractiveLayout();
         });
         this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Apply JSON", mx, my, () -> {
            this.syncPayloadPreviewFromJson(true);
            this.refreshInteractiveLayout();
         });
         cy += 18;
         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null) {
            int lw = this.labelWidth(w, "Channel", font, 92);
            this.drawLabel(ctx, "Channel", x, cy, lw, font);
            int ctrlX = this.controlX(x, lw);
            int ctrlW = this.controlWidth(w, lw);
            channelField.setX(ctrlX);
            channelField.setY(cy + 2);
            channelField.setWidth(ctrlW);
            channelField.render(ctx, mx, my, delta);
            cy += 17;
         }

         if (this.toggleStates.getOrDefault("payload_command_api", false)) {
            RiptideChatField commandField = this.textFields.get("payload_command_value");
            if (commandField != null) {
               int lw = this.labelWidth(w, "CommandApi", font, 92);
               this.drawLabel(ctx, "CommandApi", x, cy, lw, font);
               int ctrlX = this.controlX(x, lw);
               int ctrlW = this.controlWidth(w, lw);
               int btnW = 30;
               int fieldW = Math.max(32, ctrlW - btnW - 2);
               commandField.setX(ctrlX);
               commandField.setY(cy + 2);
               commandField.setWidth(fieldW);
               commandField.render(ctx, mx, my, delta);
               this.renderOverlayButton(ctx, ctrlX + fieldW + 2, cy + 2, btnW, 14, "Sync", CompactOverlayButton.Variant.GHOST, true, mx, my, () -> {
                  this.syncPayloadRawFieldFromCommand();
                  this.refreshInteractiveLayout();
               });
               cy += 17;
            }
         }

         UiText.draw(ctx, this.textRenderer, "UTF-8 Component", font, RiptideColors.textSecondary(), x, cy + 2, false);
         cy += 13;
         RiptideChatField textField = this.textFields.get("payload_text");
         if (textField != null) {
            textField.setX(x);
            textField.setY(cy);
            textField.setWidth(w);
            textField.setHeight(30);
            textField.render(ctx, mx, my, delta);
            cy += 34;
         }

         int legacyHalfW = (w - 4) / 2;
         this.renderActionButton(ctx, x, cy, legacyHalfW, 14, "Component -> Hex", mx, my, () -> {
            this.syncPayloadRawFieldFromText();
            this.refreshInteractiveLayout();
         });
         this.renderActionButton(ctx, x + legacyHalfW + 4, cy, legacyHalfW, 14, "Hex -> Component", mx, my, () -> {
            this.syncPayloadTextFieldFromRaw();
            this.refreshInteractiveLayout();
         });
         cy += 18;
         UiText.draw(ctx, this.textRenderer, "Raw Payload", font, RiptideColors.textSecondary(), x, cy + 2, false);
         cy += 13;
         RiptideChatField rawField = this.textFields.get("payload_data");
         if (rawField != null) {
            rawField.setX(x);
            rawField.setY(cy);
            rawField.setWidth(w);
            rawField.setHeight(42);
            rawField.render(ctx, mx, my, delta);
            cy += 46;
         }

         this.renderActionButton(ctx, x, cy, halfW, 14, "Parse Int", mx, my, () -> {
            this.syncPayloadCommandFieldFromRaw();
            this.refreshInteractiveLayout();
         });
         this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Normalize", mx, my, () -> {
            RiptideChatField field = this.textFields.get("payload_data");
            if (field != null) {
               try {
                  field.setText(RiptidePayloadSupport.toHex(RiptidePayloadSupport.parsePayloadBytes(field.getText())));
               } catch (Exception var3x) {
                  RiptideClientMessaging.sendPrefixed("§cInvalid payload data: " + RiptidePayloadSupport.safeMessage(var3x));
               }
            }
         });
         cy += 18;
         this.renderActionButton(ctx, x, cy, halfW, 14, "Seed From Int", mx, my, () -> {
            this.syncPayloadRawFieldFromCommand();
            this.refreshInteractiveLayout();
         });
         this.renderOverlayButton(
            ctx,
            x + halfW + 4,
            cy,
            halfW,
            14,
            this.configurationOnly ? "Send: In Game" : "Send Now",
            CompactOverlayButton.Variant.SUCCESS,
            !this.configurationOnly,
            mx,
            my,
            () -> {
               if (!this.configurationOnly) {
                  try {
                     PayloadAction action = this.buildPayloadActionFromEditor();
                     action.execute(MC);
                  } catch (Exception var2x) {
                     RiptideClientMessaging.sendPrefixed("§cPayload send failed: " + RiptidePayloadSupport.safeMessage(var2x));
                  }
               }
            }
         );
         cy += 20;
         UiText.draw(ctx, this.textRenderer, "Runtime Java", font, RiptideColors.textSecondary(), x, cy + 2, false);
         cy += 13;
         RiptideChatField javaField = this.textFields.get("payload_java");
         if (javaField != null) {
            javaField.setX(x);
            javaField.setY(cy);
            javaField.setWidth(w);
            javaField.setHeight(48);
            javaField.render(ctx, mx, my, delta);
            cy += 52;
         }

         this.renderEditorHint(ctx, x, cy, w, "Raw replay is exact by default. Use Apply JSON only when you want the JSON view to rebuild the raw bytes.");
      } else {
         this.renderCleanPayloadPanel(ctx, x, bodyTop, w, mx, my, delta);
      }
   }

   private PayloadAction buildPayloadActionFromEditor() {
      if (this.payloadAction == null && this.targetAction == null) {
         PayloadAction action = new PayloadAction();
         if (this.payloadAction != null) {
            action.fromTag(this.payloadAction.toTag());
         }

         action.payloadClassName = "";
         RiptideChatField jsonField = this.textFields.get("payload_json");
         if (jsonField != null) {
            action.payloadJson = jsonField.getText();
         }

         boolean jsonUsable = false;
         RiptidePayloadJsonSupport.EncodedPayload jsonEncoded = null;
         if (action.payloadJson != null && !action.payloadJson.isBlank()) {
            action.payloadJson = RiptidePayloadJsonSupport.normalizeJson(action.payloadJson);

            try {
               jsonEncoded = RiptidePayloadJsonSupport.encodeAction(action);
               jsonUsable = true;
            } catch (Exception var13) {
            }
         }

         RiptideChatField channelField = this.textFields.get("payload_channel");
         if (channelField != null) {
            String editorChannel = channelField.getText().strip();
            if (!editorChannel.isBlank()) {
               action.channel = editorChannel;
            }
         }

         RiptideChatField rawField = this.textFields.get("payload_data");
         if (rawField != null) {
            action.payloadData = rawField.getText();
         }

         if (action.payloadData == null || action.payloadData.isBlank()) {
            RiptideChatField textField = this.textFields.get("payload_text");
            if (textField != null && !textField.getText().isBlank()) {
               action.payloadData = RiptidePayloadSupport.toHex(textField.getText().getBytes(StandardCharsets.UTF_8));
            }
         }

         RiptideChatField javaField = this.textFields.get("payload_java");
         if (javaField != null) {
            action.javaSource = javaField.getText();
         }

         action.commandApiRecognized = this.toggleStates.getOrDefault("payload_command_api", false);
         RiptideChatField commandField = this.textFields.get("payload_command_value");
         if (commandField != null) {
            try {
               action.commandApiValue = Integer.parseInt(commandField.getText().strip());
            } catch (NumberFormatException var12) {
            }
         }

         if (this.payloadJsonEdited && jsonUsable && jsonEncoded != null && !this.payloadRawEdited && !this.payloadChannelEdited) {
            try {
               jsonEncoded = RiptidePayloadJsonSupport.encodeAction(action);
               action.channel = jsonEncoded.channel();
               action.payloadData = RiptidePayloadSupport.toHex(jsonEncoded.bytes());
            } catch (Exception var11) {
            }
         }

         if (action.commandApiRecognized) {
            try {
               byte[] bytes = RiptidePayloadSupport.parsePayloadBytes(action.payloadData);
               action.payloadData = RiptidePayloadSupport.toHex(RiptidePayloadSupport.withCommandApiValue(bytes, action.commandApiValue));
            } catch (Exception var10) {
            }
         }

         action.payloadClassName = "";
         if (action.channel == null || action.channel.isBlank()) {
            action.channel = this.payloadAction != null && this.payloadAction.channel != null && !this.payloadAction.channel.isBlank()
               ? this.payloadAction.channel
               : "minecraft:brand";
         }

         return action;
      } else {
         return this.buildCleanPayloadActionFromEditor();
      }
   }

   private void syncPayloadCommandFieldFromRaw() {
      RiptideChatField rawField = this.textFields.get("payload_data");
      RiptideChatField commandField = this.textFields.get("payload_command_value");
      RiptideChatField channelField = this.textFields.get("payload_channel");
      if (rawField != null && commandField != null) {
         try {
            byte[] bytes = RiptidePayloadSupport.parsePayloadBytes(rawField.getText());
            Integer value = RiptidePayloadSupport.tryParseCommandApiValue(null, channelField == null ? "" : channelField.getText(), bytes);
            if (value == null) {
               RiptideClientMessaging.sendPrefixed("§cCould not parse a CommandApi integer from the current payload bytes.");
               return;
            }

            this.toggleStates.put("payload_command_api", true);
            commandField.setText(String.valueOf(value));
         } catch (Exception var6) {
            RiptideClientMessaging.sendPrefixed("§cInvalid payload data: " + RiptidePayloadSupport.safeMessage(var6));
         }
      }
   }

   private void syncPayloadRawFieldFromCommand() {
      RiptideChatField rawField = this.textFields.get("payload_data");
      RiptideChatField commandField = this.textFields.get("payload_command_value");
      if (rawField != null && commandField != null) {
         try {
            byte[] bytes = RiptidePayloadSupport.parsePayloadBytes(rawField.getText());
            int value = Integer.parseInt(commandField.getText().strip());
            this.toggleStates.put("payload_command_api", true);
            rawField.setText(RiptidePayloadSupport.toHex(RiptidePayloadSupport.withCommandApiValue(bytes, value)));
         } catch (Exception var5) {
            RiptideClientMessaging.sendPrefixed("§cFailed to sync CommandApi value: " + RiptidePayloadSupport.safeMessage(var5));
         }
      }
   }

   private void syncPayloadTextFieldFromRaw() {
      RiptideChatField rawField = this.textFields.get("payload_data");
      RiptideChatField textField = this.textFields.get("payload_text");
      if (rawField != null && textField != null) {
         try {
            byte[] bytes = RiptidePayloadSupport.parsePayloadBytes(rawField.getText());
            String text = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
            if (text.isBlank()) {
               RiptideClientMessaging.sendPrefixed("§cThe current payload bytes do not look like readable UTF-8 text.");
               return;
            }

            textField.setText(text);
         } catch (Exception var5) {
            RiptideClientMessaging.sendPrefixed("§cInvalid payload data: " + RiptidePayloadSupport.safeMessage(var5));
         }
      }
   }

   private void syncPayloadRawFieldFromText() {
      RiptideChatField rawField = this.textFields.get("payload_data");
      RiptideChatField textField = this.textFields.get("payload_text");
      if (rawField != null && textField != null) {
         rawField.setText(RiptidePayloadSupport.toHex(textField.getText().getBytes(StandardCharsets.UTF_8)));
      }
   }

   private void renderDelayPacketsPresetButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      int btnH = 14;
      int halfW = (w - 4) / 2;
      int x2 = x + halfW + 4;
      this.renderOverlayButton(ctx, x, y + 2, halfW, btnH, "Default", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
         DelayPacketsAction tmp = new DelayPacketsAction();
         tmp.applyDefaultPreset();
         this.stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
         this.stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
      });
      this.renderOverlayButton(ctx, x2, y + 2, halfW, btnH, "Module", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> {
         DelayPacketsAction tmp = new DelayPacketsAction();
         tmp.applyModulePreset();
         this.stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
         this.stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
      });
   }

   private CompactOverlayButton renderOverlayButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean active, int mx, int my, Runnable action
   ) {
      return this.renderOverlayButton(ctx, x, y, w, h, label, variant, active, mx, my, action, null, null, null);
   }

   private CompactOverlayButton renderOverlayButton(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int h,
      String label,
      CompactOverlayButton.Variant variant,
      boolean active,
      int mx,
      int my,
      Runnable action,
      Runnable secondaryAction
   ) {
      return this.renderOverlayButton(ctx, x, y, w, h, label, variant, active, mx, my, action, secondaryAction, null, null);
   }

   private CompactOverlayButton renderOverlayToggleButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean toggleState, String animationKey, int mx, int my, Runnable action
   ) {
      return this.renderOverlayButton(
         ctx,
         x,
         y,
         w,
         h,
         label,
         toggleState ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER,
         true,
         mx,
         my,
         action,
         null,
         toggleState,
         animationKey
      );
   }

   private CompactOverlayButton renderOverlayButton(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int h,
      String label,
      CompactOverlayButton.Variant variant,
      boolean active,
      int mx,
      int my,
      Runnable action,
      Runnable secondaryAction,
      Boolean toggleState,
      String animationKey
   ) {
      CompactOverlayButton button = CompactOverlayButton.create(
         x, y, w, h, Component.literal(label), ignored -> action.run(), secondaryAction == null ? null : ignored -> secondaryAction.run()
      );
      button.setVariant(variant);
      if (toggleState != null) {
         button.setToggleState(toggleState).setAnimationKey(animationKey);
      }

      button.active = active;
      CompactOverlayButton.renderStyled(ctx, this.textRenderer, button, mx, my);
      this.hitRegions.add(new ActionEditorOverlay.HitRegion(button, action));
      return button;
   }

   private static int macroEditorButtonOutline(CompactOverlayButton.Variant variant, boolean active) {
      if (!active) {
         return -2009060816;
      } else {
         return switch (variant == null ? CompactOverlayButton.Variant.SECONDARY : variant) {
            case SUCCESS -> RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
            case DANGER -> RiptideTheme.recolor(-50373, RiptideTheme.Channel.DANGER);
            case PRIMARY, FILTER_ON -> RiptideTheme.recolor(-42149, RiptideTheme.Channel.BUTTON);
            case GHOST, FILTER_OFF, SECONDARY -> RiptideTheme.recolor(-50373, RiptideTheme.Channel.OUTLINE);
         };
      }
   }

   private CompactOverlayButton renderFieldCaptureButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, CaptureMode mode, boolean capturing, boolean active, int mx, int my, Runnable action
   ) {
      return this.renderFieldCaptureButton(ctx, x, y, w, h, mode, capturing, active, mx, my, null, null, action);
   }

   private CompactOverlayButton renderFieldCaptureButton(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int h,
      CaptureMode mode,
      boolean capturing,
      boolean active,
      int mx,
      int my,
      String idleLabel,
      String capturingLabel,
      Runnable action
   ) {
      if (!this.captureControlAvailable(mode)) {
         return null;
      } else {
         CompactOverlayButton button = MacroCaptureButton.render(
            ctx, this.textRenderer, UiBounds.of(x, y, w, h), mode, capturing, active, mx, my, idleLabel, capturingLabel, action
         );
         this.hitRegions.add(new ActionEditorOverlay.HitRegion(button, action));
         return button;
      }
   }

   private void renderIconDeleteButton(GuiGraphicsExtractor ctx, int x, int y, int size, int mx, int my, Runnable action) {
      boolean hovered = mx >= x && mx < x + size && my >= y && my < y + size;
      MacroTypedListControl.renderDelete(ctx, UiBounds.of(x, y, size, size), hovered);
      this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, y, size, size, (mouseX, mouseY, mouseButton) -> {
         if (mouseButton != 0) {
            return false;
         } else {
            action.run();
            return true;
         }
      }));
   }

   private void renderActionButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int mx, int my, Runnable action) {
      this.renderOverlayButton(ctx, x, y, w, h, label, CompactOverlayButton.Variant.SECONDARY, true, mx, my, action);
   }

   private int renderEditorHint(GuiGraphicsExtractor ctx, int x, int y, int w, String text) {
      return this.renderEditorHint(ctx, x, y, w, text, RiptideColors.textDim());
   }

   private int renderEditorHint(GuiGraphicsExtractor ctx, int x, int y, int w, String text, int color) {
      String hint = this.formatEditorHint(text);
      if (hint.isEmpty()) {
         return y;
      } else {
         Identifier font = this.theme.fontFor(UiTone.BODY);
         List<String> lines = this.wrapEditorHint(hint, Math.max(10, w), font, color);
         int cy = y;

         for (String line : lines) {
            UiText.draw(ctx, this.textRenderer, line, font, color, x, cy + 2, false);
            cy += 11;
         }

         return cy;
      }
   }

   private List<String> wrapEditorHint(String hint, int maxWidth, Identifier font, int color) {
      if (hint != null && !hint.isBlank()) {
         String safe = hint.trim().replaceAll("\\s+", " ");
         if (UiText.width(this.textRenderer, safe, font, color) <= maxWidth) {
            return List.of(safe);
         } else {
            String[] words = safe.split(" ");
            StringBuilder first = new StringBuilder();
            StringBuilder second = new StringBuilder();

            for (String word : words) {
               String candidate = first.length() == 0 ? word : first + " " + word;
               if (second.length() == 0 && UiText.width(this.textRenderer, candidate, font, color) <= maxWidth) {
                  first.setLength(0);
                  first.append(candidate);
               } else {
                  if (second.length() > 0) {
                     second.append(' ');
                  }

                  second.append(word);
               }
            }

            if (second.length() != 0 && first.length() != 0) {
               String secondLine = UiText.width(this.textRenderer, second.toString(), font, color) <= maxWidth
                  ? second.toString()
                  : UiText.trimToWidth(this.textRenderer, second.toString(), maxWidth, font, color);
               return List.of(first.toString(), secondLine);
            } else {
               return List.of(UiText.trimToWidth(this.textRenderer, safe, maxWidth, font, color));
            }
         }
      } else {
         return List.of();
      }
   }

   private String formatEditorHint(String text) {
      if (text == null) {
         return "";
      } else {
         String normalized = text.trim().replaceAll("\\s+", " ");
         if (normalized.isEmpty()) {
            return "";
         } else if (normalized.startsWith("Config-phase payload")) {
            return "Config payload may disconnect.";
         } else {
            String exact = switch (normalized) {
               case "Editing this row. 0 means drop the full stack from that slot or item." -> "Editing row. 0 = full stack.";
               case "No row selected. The controls below set defaults for new rows you add." -> "No row picked. Controls set new-row defaults.";
               case "Continue when any LAN peer reaches the target step." -> "Waits for any LAN peer step.";
               case "Add peers below to narrow it down. Until then, it waits for any peer at the fallback step." -> "Add peers below or use fallback.";
               case "Each peer below must reach its step before this continues." -> "All listed peers must reach their step.";
               case "No packets selected. This step will continue on the next packet in either direction." -> "No filters: next packet continues.";
               case "This step continues as soon as any selected C2S or S2C packet arrives." -> "Waits for any chosen packet.";
               case "Capture only marks the block. It will not open it." -> "Capture only marks the block.";
               case "0,0,0 uses the current container when open, otherwise the closest nearby one." -> "0,0,0 = current or nearest container.";
               case "Pick on an item adds it by name, from any slot on the screen. Ctrl+pick or an empty slot adds that exact slot." -> "Item = name (any slot). Ctrl/empty = exact slot.";
               case "Exact slots must be chest/custom GUI slots." -> "Exact slots: GUI side only.";
               case "Exact slots must be player inventory slots." -> "Exact slots: your inventory only.";
               case "No payment targets selected yet." -> "No players selected.";
               case "Click a scanned name to toggle it, or use All to add the filtered scan results." -> "Click names to toggle, or use All.";
               case "The search box also lets you manually add a player by pressing Enter." -> "Press Enter to add a typed player.";
               case "Pick modules below, then click the mode chip on each row." -> "Pick modules, then set each mode.";
               case "Each row runs with its own Toggle / Enable / Disable setting." -> "Each row keeps its own mode.";
               case "Capture View fills in your current yaw and pitch." -> "Capture View fills yaw and pitch.";
               case "Wait for Arrival continues only after Baritone finishes." -> "Waits until Baritone arrives.";
               case "Simple disconnect. Delay only controls how long to wait before closing the connection." -> "Simple disconnect after the delay.";
               case "Sends lag packets, then the selected kick packet. Packet Count controls how hard it pushes." -> "Sends lag, then the kick packet.";
               case "Kick Dupe will run the next eligible macro actions inside the lag sandwich, then kick." -> "Runs next actions inside the dupe kick.";
               case "Kick Dupe will try to use a bundle, then kick." -> "Tries bundle dupe, then kicks.";
               case "Automatically detects teleport/world change and disconnects at the perfect timing for dupe exploits." -> "Auto-disconnects at the perfect dupe timing.";
               case "Waits until the current GUI closes." -> "Waits for the GUI to close.";
               case "Waits until a GUI opens." -> "Waits for any GUI to open.";
               case "Blank pattern waits for any incoming server or plugin message." -> "Blank pattern = any server message.";
               case "Blank pattern waits for any incoming chat line." -> "Blank pattern = any chat line.";
               case "Regex is on, so the pattern must match with Java regex rules." -> "Regex mode uses Java regex rules.";
               case "No modules found right now." -> "No modules found.";
               case "Press Scan to load the current server players." -> "Press Scan for players.";
               case "Click a row to use that message." -> "Click a row to use it.";
               case "Pick target blocks, then choose the stop rule you want." -> "Pick blocks, then choose a stop rule.";
               default -> "";
            };
            if (!exact.isEmpty()) {
               return this.fitEditorHintText(exact);
            } else {
               normalized = normalized.replace("Waits until", "Waits for")
                  .replace("Continue when", "Continues when")
                  .replace("This step continues as soon as", "Continues when")
                  .replace("the selected", "chosen")
                  .replace("the current", "this")
                  .replace("current ", "")
                  .replace("number of times", "times")
                  .replace("the chosen number of times", "times")
                  .replace("the chosen ticks", "set ticks")
                  .replace("only after", "after")
                  .replace("It will not", "Won't")
                  .replace("selected kick packet", "kick packet")
                  .replace("Packet Count controls how hard it pushes", "Packet Count controls force")
                  .replace("the open handler", "the open GUI")
                  .replace("player inventory", "inventory")
                  .replace("inventory slot", "slot")
                  .replace("the matched item into", "it into")
                  .replace("first matching visible slot", "first visible match")
                  .replace("contains an item that matches the optional name", "matches the optional item")
                  .replace("becomes empty", "turns empty")
                  .replace("reaches the target count", "hits the target count")
                  .replace("drops below the target count", "drops below the target")
                  .replace("on the first content or count change in that exact slot", "on the first exact slot change")
                  .replace("the block under your crosshair", "your target block")
                  .replace("the entity under your crosshair", "your target entity")
                  .replace("whatever is under your crosshair", "your target")
                  .replace("will attack or break", "acts on")
                  .replace("will interact with", "uses")
                  .replace("the currently selected hotbar item", "the held item");
               return this.fitEditorHintText(normalized);
            }
         }
      }
   }

   private void applyEditorPlaceholders() {
      if (this.schema != null) {
         for (FieldDef field : this.schema.fields()) {
            switch (field.type()) {
               case NUMBER:
               case DECIMAL:
               case TEXT:
               case MACRO_SELECT:
               case SLOT:
                  this.applyTextFieldPlaceholder(field.key(), this.resolveFieldPlaceholder(field));
               case ENUM:
               default:
                  break;
               case BLOCK_POS:
                  this.applyBlockPosPlaceholders(field.key());
            }
         }
      }

      this.applyTextFieldPlaceholder("item_guiName", "Any GUI");
      this.applyTextFieldPlaceholder("drop_guiName", "Any GUI");
      this.applyTextFieldPlaceholder("lan_defaultStep", "Any step");
      this.applyTextFieldPlaceholder("item_entrySlot", "Slot");
      this.applyTextFieldPlaceholder("place_itemSlot", "Slot");
      this.applyTextFieldPlaceholder("drop_entrySlot", "Slot #");
      this.applyTextFieldPlaceholder("_craft_amount", "1");
      this.applyTextFieldPlaceholder("_lan_step_add", "1");
      this.applyTextFieldPlaceholder("drop_globalCount", "1");
      this.applyTextFieldPlaceholder("amountInput", "1k");
      this.applyAddFieldPlaceholder("_item_add", "Any item");
      this.applyAddFieldPlaceholder("_drop_add", "Any item");
      this.applyAddFieldPlaceholder("_craft_search", "Find recipe");
      this.applyAddFieldPlaceholder("_lan_user_add", "Peer name");
      this.applyAddFieldPlaceholder("soundIds", "Find sound");
      this.applyAddFieldPlaceholder("entityIds", "Find entity");
      this.applyAddFieldPlaceholder("targetItems", "Item name");
      this.applyAddFieldPlaceholder("players", "Find player");
      this.applyAddFieldPlaceholder("_toggle_module_search", "Find module");
      this.applyAddFieldPlaceholder("_wait_chat_search", "Find chat");
   }

   private void applyTextFieldPlaceholder(String key, String placeholder) {
      RiptideChatField field = this.textFields.get(key);
      this.setEditorPlaceholder(field, placeholder);
   }

   private void applyAddFieldPlaceholder(String key, String placeholder) {
      RiptideChatField field = this.addFields.get(key);
      this.setEditorPlaceholder(field, placeholder);
   }

   private void applyBlockPosPlaceholders(String key) {
      this.applyTextFieldPlaceholder(key + "_0", "X");
      this.applyTextFieldPlaceholder(key + "_1", "Y");
      this.applyTextFieldPlaceholder(key + "_2", "Z");
   }

   private void setEditorPlaceholder(RiptideChatField field, String placeholder) {
      if (field != null && placeholder != null && !placeholder.isBlank()) {
         field.setPlaceholder(Component.literal(this.fitEditorGhostText(placeholder)));
      }
   }

   private String resolveFieldPlaceholder(FieldDef field) {
      if (field == null) {
         return "";
      } else {
         String var2 = field.key();

         return switch (var2) {
            case "guiName", "waitGuiName" -> "Any GUI";
            case "listFilterText" -> "text to match";
            case "listExcludeText" -> "name1, name2";
            case "pattern" -> "Any chat";
            case "message" -> "Type message";
            case "description" -> "Quick note";
            case "customText" -> "Optional text";
            case "customFilePath" -> "File in book-sources/";
            case "characters" -> "1024";
            case "title" -> this.targetAction != null && this.targetAction.getType() == MacroActionType.NBT_BOOK ? "Book title" : "Optional title";
            case "guiTitle" -> "Optional title";
            case "customName" -> "Optional name";
            case "commandTemplate" -> "/pay <p> <amt>";
            case "amountInput" -> "1k";
            case "itemName", "fromItemName", "toItemName" -> "Any item";
            case "moduleName" -> "Pick below";
            case "slot", "fromSlot", "toSlot", "slotNumber", "targetSlot" -> "Slot #";
            case "healthThreshold" -> "0-20";
            case "yaw", "pitch" -> "0";
            case "delayMs" -> "0";
            case "delayTicks" -> "0";
            case "clickCount", "useCount", "holdTicks", "packetCount", "maxDistance", "stopSlotsUsed", "slotsUsedThreshold", "minedCountTarget", "timeoutSeconds", "durationTicks", "tickOffset", "preGenCount", "revisionOffset", "maxWaitMs", "bufferMs" -> "1";
            default -> {
               switch (field.type()) {
                  case NUMBER:
                  case DECIMAL:
                     yield "0";
                     break;
                  case TEXT:
                  case MACRO_SELECT:
                     yield this.fitEditorGhostText(field.label());
                     break;
                  case ENUM:
                  default:
                     yield "";
                     break;
                  case SLOT:
                     yield "Slot #";
               }
            }
         };
      }
   }

   private String fitEditorGhostText(String text) {
      String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
      if (normalized.length() <= 16) {
         return normalized;
      } else {
         int cutoff = normalized.lastIndexOf(32, 13);
         if (cutoff < 5) {
            cutoff = 13;
         }

         return normalized.substring(0, Math.max(1, cutoff)).trim() + "...";
      }
   }

   private String fitEditorHintText(String text) {
      return text == null ? "" : text.trim().replaceAll("\\s+", " ");
   }

   private void renderRow(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      if (this.isGuiWaitBeforeKey(field.key())) {
         this.renderGuiWaitRow(ctx, this.theme.fontFor(UiTone.BODY), field.key(), "waitForGuiAfter", x, y, w, mx, my);
      } else if ("safeClickDelayTicks".equals(field.key())) {
         this.renderXCarrySafeDelaySlider(ctx, x, y, w, mx, my);
      } else {
         switch (field.type()) {
            case TOGGLE:
               this.renderToggle(ctx, field, x, y, w, mx, my);
               break;
            case NUMBER:
            case DECIMAL:
            case TEXT:
            case MACRO_SELECT:
            case SLOT:
               this.renderTextField(ctx, field, x, y, w, mx, my, delta);
               break;
            case ENUM:
               this.renderEnum(ctx, field, x, y, w, mx, my);
               break;
            case BLOCK_POS:
               this.renderBlockPos(ctx, field, x, y, w, mx, my, delta);
               break;
            case STRING_LIST:
               if (this.isRacePickerList(field)) {
                  this.renderRacePickerList(ctx, field, x, y, w, mx, my);
               } else if (this.isMultiAccountList(field)) {
                  this.renderMultiAccountList(ctx, field, x, y, w, mx, my);
               } else if (field.captureMode() == CaptureMode.BLOCK_CATALOG) {
                  this.renderStringListCatalog(ctx, field, x, y, w, mx, my, delta);
               } else {
                  this.renderStringList(ctx, field, x, y, w, mx, my, delta);
               }
               break;
            case TARGET_SUMMARY:
               this.renderTargetSummary(ctx, field, x, y, w);
               break;
            case CAPTURE_BUTTON:
               this.renderCaptureButton(ctx, field, x, y, w, mx, my);
               break;
            case CONDITION:
               this.renderConditionSummary(ctx, field, x, y, w);
         }
      }
   }

   private void renderConditionSummary(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int lw = this.labelWidth(w, field.label(), font, 56);
      this.drawLabel(ctx, field.label(), x, y, lw, font);
      String summary = this.liveConditionSummary(field.key());
      int valX = this.controlX(x, lw);
      int valW = this.controlWidth(w, lw);
      int color = RiptideColors.textPrimary();
      String trimmed = UiText.width(this.textRenderer, summary, font, color) > valW
         ? UiText.trimToWidth(this.textRenderer, summary, Math.max(8, valW - 4), font, color)
         : summary;
      UiText.draw(ctx, this.textRenderer, trimmed, font, color, valX, y + 2, false);
   }

   private String liveConditionSummary(String conditionKey) {
      CompoundTag stored = this.workingTag != null && this.workingTag.get(conditionKey) instanceof CompoundTag c ? c : null;
      boolean hasFlatEditor = this.getField("cond_kind") != null;
      boolean flatEditable = hasFlatEditor;
      if (hasFlatEditor && stored != null) {
         try {
            flatEditable = MacroCondition.fromTag(stored).simpleLeafOrNull() != null;
         } catch (Throwable var7) {
            flatEditable = true;
         }
      }

      if (flatEditable && hasFlatEditor) {
         CompoundTag flat = new CompoundTag();
         flat.putString("cond_kind", this.currentEnumValue("cond_kind"));
         flat.putBoolean("cond_negate", this.toggleStates.getOrDefault("cond_negate", false));
         flat.putString("cond_item", this.conditionFieldText("cond_item"));
         flat.putString("cond_op", this.currentEnumValue("cond_op"));
         flat.putString("cond_value", this.conditionFieldText("cond_value"));
         flat.putString("cond_cmp", this.currentEnumValue("cond_cmp"));
         flat.putInt("cond_amount", this.conditionFieldInt("cond_amount", 0));
         flat.putInt("cond_slot", this.conditionFieldInt("cond_slot", 0));

         try {
            return MacroCondition.readFlatLeaf(flat, "cond_").summary();
         } catch (Throwable var9) {
         }
      }

      if (stored != null) {
         try {
            return MacroCondition.fromTag(stored).summary();
         } catch (Throwable var8) {
         }
      }

      return "always";
   }

   private String conditionFieldText(String key) {
      RiptideChatField f = this.textFields.get(key);
      return f == null ? "" : f.getText();
   }

   private int conditionFieldInt(String key, int fallback) {
      RiptideChatField f = this.textFields.get(key);
      if (f == null) {
         return fallback;
      } else {
         try {
            return Integer.parseInt(f.getText().strip());
         } catch (NumberFormatException var5) {
            return fallback;
         }
      }
   }

   private void renderPlaceItemRow(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
      RiptideChatField nameF = this.textFields.get("itemName");
      RiptideChatField slotF = this.textFields.get("place_itemSlot");
      if (nameF != null && slotF != null) {
         int pickW = 32;
         int slotW = 26;
         int gap = 3;
         int pickX = x + w - pickW;
         int slotX = pickX - gap - slotW;
         int nameW = Math.max(1, slotX - x - 2);
         nameF.setX(x);
         nameF.setY(y + 1);
         nameF.setWidth(nameW);
         nameF.render(ctx, mx, my, delta);
         slotF.setX(slotX);
         slotF.setY(y + 1);
         slotF.setWidth(slotW);
         slotF.render(ctx, mx, my, delta);
         boolean capt = this.captureSession.isItemSlotCapture("itemName");
         this.renderFieldCaptureButton(ctx, pickX, y, pickW, 14, CaptureMode.ITEM_SLOT, capt, true, mx, my, () -> this.toggleItemSlotCapture("itemName"));
      }
   }

   private void renderTargetSummary(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int lw = this.labelWidth(w, field.label(), font, 56);
      this.drawLabel(ctx, field.label(), x, y, lw, font);
      String summary = this.resolvePacketClickTargetSummary();
      int valX = this.controlX(x, lw);
      int valW = this.controlWidth(w, lw);
      int color = summary != null && !summary.isEmpty() ? RiptideColors.textPrimary() : RiptideColors.textSecondary();
      String display = summary != null && !summary.isEmpty() ? summary : "Not captured";
      String trimmed = UiText.width(this.textRenderer, display, font, color) > valW
         ? UiText.trimToWidth(this.textRenderer, display, Math.max(8, valW - 4), font, color)
         : display;
      UiText.draw(ctx, this.textRenderer, trimmed, font, color, valX, y + 2, false);
   }

   private void renderCaptureButton(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int btnW = 60;
      int btnH = 14;
      int lw = this.labelWidth(w, field.label(), font, btnW);
      this.drawLabel(ctx, field.label(), x, y, lw, font);
      boolean capturing = field.key().equals(this.packetClickCapturePendingKey);
      int btnX = x + w - btnW;
      this.renderFieldCaptureButton(
         ctx,
         btnX,
         y + 2,
         btnW,
         btnH,
         CaptureMode.PACKET_CLICK_TARGET,
         capturing,
         true,
         mx,
         my,
         "Re-capture",
         "Click slot...",
         () -> this.beginPacketClickCapture(field.key())
      );
   }

   private String resolvePacketClickTargetSummary() {
      if (this.targetAction instanceof PacketClickAction pca) {
         return pca.target == null ? null : pca.target.withMode(this.currentPacketClickMode()).summary();
      } else {
         return null;
      }
   }

   private boolean isRacePickerList(FieldDef field) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.RACE && field != null && "raceSteps".equals(field.key());
   }

   private boolean isMultiAccountList(FieldDef field) {
      return RiptideLiteVariant.enabled()
         ? false
         : this.targetAction != null && this.targetAction.getType() == MacroActionType.MULTI && field != null && "accounts".equals(field.key());
   }

   private void renderRacePickerList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
      String key = field.key();
      List<String> values = this.stringLists.computeIfAbsent(key, ignored -> new ArrayList<>());
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, field.label() + " (" + values.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      int addY = y + 13;
      int gap = 4;
      int btnW = Math.max(60, (w - gap) / 2);
      this.renderOverlayButton(
         ctx, x, addY, btnW, 14, "+ Condition", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> this.openRaceStepSelector(values, true)
      );
      this.renderOverlayButton(
         ctx,
         x + btnW + gap,
         addY,
         Math.max(60, w - btnW - gap),
         14,
         "+ Action",
         CompactOverlayButton.Variant.SUCCESS,
         true,
         mx,
         my,
         () -> this.openRaceStepSelector(values, false)
      );
      int listY = addY + 18;
      int rowH = 18;
      int visibleRows = 4;
      DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, key + "_race", x, listY, w, visibleRows * rowH, rowH, 5);
      viewport.setContentHeight(values.size() * rowH);
      viewport.renderScrollbar(ctx, mx, my);
      if (values.isEmpty()) {
         String hint = "Use +Cond for waits or +Act for batch actions.";
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, hint, x, listY, w - 5 - 1);
      } else {
         int buttonW = 14;
         int rowGap = 2;
         int buttonArea = buttonW * 3 + rowGap * 2;
         int rowTextW = Math.max(20, w - 5 - buttonArea - 7);
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = viewport.getFirstVisibleRow();

         for (int vi = first; vi < values.size() && vi <= viewport.getLastVisibleRow(); vi++) {
            int rowY = viewport.getRowScreenY(vi);
            if (rowY != Integer.MIN_VALUE) {
               String value = values.get(vi);
               boolean hovered = mx >= x && mx < x + rowTextW && my >= rowY && my < rowY + rowH;
               this.renderRaceStepRow(ctx, x, rowY, rowTextW, rowH, vi, value, hovered);
               int bx = x + rowTextW + 3;
               int rowIndex = vi;
               this.renderRaceListSymbolButton(
                  ctx, bx, rowY + 2, buttonW, mx, my, "^", false, rowIndex > 0, () -> Collections.swap(values, rowIndex, rowIndex - 1)
               );
               bx += buttonW + rowGap;
               this.renderRaceListSymbolButton(
                  ctx, bx, rowY + 2, buttonW, mx, my, "v", false, rowIndex + 1 < values.size(), () -> Collections.swap(values, rowIndex, rowIndex + 1)
               );
               bx += buttonW + rowGap;
               boolean canDelete = this.raceActionCount(values) > 1 || !this.isRaceActionStep(value);
               this.renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "X", true, canDelete, () -> {
                  if (rowIndex >= 0 && rowIndex < values.size() && (this.raceActionCount(values) > 1 || !this.isRaceActionStep(values.get(rowIndex)))) {
                     values.remove(rowIndex);
                  }
               });
            }
         }

         viewport.endRender(ctx);
      }
   }

   private void renderRaceStepRow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int index, String step, boolean hovered) {
      String canonical = RaceAction.canonicalRaceStep(step);
      boolean condition = RaceAction.isConditionStep(canonical);
      int bgColor = hovered ? RiptideColors.rowHover() : RiptideColors.rowNormal();
      CompactSurfaces.tintedRow(ctx, x, y + 1, w, h - 2, bgColor);
      int badgeColor = condition ? -30720 : -12268476;
      CompactSurfaces.indicator(ctx, x + 1, y + 3, 3, h - 6, badgeColor);
      int numberColor = RiptideColors.textDim();
      UiText.draw(
         ctx,
         this.textRenderer,
         String.valueOf(index + 1),
         this.theme.fontFor(UiTone.BODY),
         numberColor,
         x + 8,
         UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge()),
         false
      );
      String role = condition ? "COND" : "ACT";
      int roleColor = condition ? RiptideColors.packetCyan() : RiptideColors.textSecondary();
      int roleX = x + 24;
      UiText.draw(
         ctx,
         this.textRenderer,
         role,
         this.theme.fontFor(UiTone.LABEL),
         roleColor,
         roleX,
         UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.LABEL), this.theme.bodyTextNudge()),
         false
      );
      String label = this.raceStepDisplay(step).replaceFirst("^(COND|ACT)\\s+", "");
      int labelX = roleX + 34;
      String trimmed = UiText.trimToWidth(this.textRenderer, label, Math.max(1, x + w - labelX - 4), this.theme.fontFor(UiTone.BODY), -1);
      UiText.draw(
         ctx,
         this.textRenderer,
         trimmed,
         this.theme.fontFor(UiTone.BODY),
         -1,
         labelX,
         UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge()),
         false
      );
   }

   private void renderRaceListSymbolButton(
      GuiGraphicsExtractor ctx, int x, int y, int size, int mx, int my, String symbol, boolean danger, boolean active, Runnable action
   ) {
      boolean hovered = active && mx >= x && mx < x + size && my >= y && my < y + size;
      MacroTypedListControl.renderSymbol(ctx, UiBounds.of(x, y, size, size), symbol, hovered, danger);
      this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, y, size, size, (mouseX, mouseY, mouseButton) -> {
         if (active && mouseButton == 0) {
            action.run();
            return true;
         } else {
            return false;
         }
      }));
   }

   private void openRaceStepSelector(List<String> values, boolean condition) {
      List<RaceStepSelectorOverlay.Option> options = condition
         ? this.raceSelectorOptions(RACE_CONDITION_CHOICES, "CONDITION")
         : this.raceSelectorOptions(RACE_ACTION_CHOICES, "ACTION");
      this.raceStepSelectorOverlay.open(condition ? "Condition Selector" : "Action Selector", options, option -> {
         if (option != null && option.id() != null && !option.id().isBlank()) {
            values.add((condition ? "CONDITION:" : "ACTION:") + option.id());
            this.refreshInteractiveLayout();
         }
      });
   }

   private List<RaceStepSelectorOverlay.Option> raceSelectorOptions(List<String> choices, String role) {
      ArrayList<RaceStepSelectorOverlay.Option> out = new ArrayList<>();

      for (String choice : choices) {
         String[] parts = choice == null ? new String[0] : choice.split("/", 2);
         String category = parts.length > 1 ? parts[0].trim() : role;
         String id = this.raceChoiceValue(choice);
         if (!id.isBlank()) {
            String label = id.replace('_', ' ');
            out.add(new RaceStepSelectorOverlay.Option(category, id, label, role));
         }
      }

      return out;
   }

   private String raceStepDisplay(String step) {
      String canonical = RaceAction.canonicalRaceStep(step);
      String type = RaceAction.stepTypeName(canonical);
      return (RaceAction.isConditionStep(canonical) ? "COND  " : "ACT   ") + type;
   }

   private boolean isRaceActionStep(String step) {
      return RaceAction.isActionStep(RaceAction.canonicalRaceStep(step));
   }

   private int raceActionCount(List<String> values) {
      int count = 0;
      if (values != null) {
         for (String value : values) {
            if (this.isRaceActionStep(value)) {
               count++;
            }
         }
      }

      return count;
   }

   private String raceChoiceValue(String choice) {
      if (choice == null) {
         return "";
      } else {
         int slash = choice.lastIndexOf(47);
         return (slash >= 0 ? choice.substring(slash + 1) : choice).trim();
      }
   }

   private void renderMultiAccountList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
      String key = field.key();
      List<String> values = this.stringLists.computeIfAbsent(key, ignored -> new ArrayList<>());
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, field.label() + " (" + values.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      int addY = y + 13;
      int gap = 4;
      int smallW = 34;
      this.renderOverlayButton(
         ctx,
         x,
         addY,
         Math.max(60, w - (smallW + gap) * 2),
         14,
         "+ Accounts",
         CompactOverlayButton.Variant.PRIMARY,
         true,
         mx,
         my,
         () -> this.openMultiAccountSelector(values)
      );
      this.renderOverlayButton(
         ctx, x + Math.max(60, w - (smallW + gap) * 2) + gap, addY, smallW, 14, "All", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
            List<MultiProfile.SessionSpec> specs = this.multiSessionSpecs();
            if (!specs.isEmpty()) {
               values.clear();

               for (MultiProfile.SessionSpec spec : specs) {
                  String accountId = spec.accountId();
                  if (accountId != null && !accountId.isBlank() && !values.contains(accountId)) {
                     values.add(accountId);
                  }
               }
            }
         }
      );
      this.renderOverlayButton(ctx, x + w - smallW, addY, smallW, 14, "None", CompactOverlayButton.Variant.SECONDARY, !values.isEmpty(), mx, my, values::clear);
      int listY = addY + 18;
      int rowH = 18;
      int visibleRows = 4;
      DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, key + "_multi", x, listY, w, visibleRows * rowH, rowH, 5);
      viewport.setContentHeight(values.size() * rowH);
      viewport.renderScrollbar(ctx, mx, my);
      if (values.isEmpty()) {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No accounts selected. Use + Accounts.", x, listY, w - 5 - 1);
      } else {
         int buttonW = 14;
         int rowTextW = Math.max(20, w - 5 - buttonW - 7);
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = viewport.getFirstVisibleRow();

         for (int vi = first; vi < values.size() && vi <= viewport.getLastVisibleRow(); vi++) {
            int rowY = viewport.getRowScreenY(vi);
            if (rowY != Integer.MIN_VALUE) {
               boolean hovered = mx >= x && mx < x + rowTextW && my >= rowY && my < rowY + rowH;
               this.renderMultiAccountRow(ctx, x, rowY, rowTextW, rowH, vi, values.get(vi), hovered);
               int bx = x + rowTextW + 3;
               int rowIndex = vi;
               this.renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "X", true, true, () -> {
                  if (rowIndex >= 0 && rowIndex < values.size()) {
                     values.remove(rowIndex);
                  }
               });
            }
         }

         viewport.endRender(ctx);
      }
   }

   private void renderMultiAccountRow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int index, String accountId, boolean hovered) {
      int bgColor = hovered ? RiptideColors.rowHover() : RiptideColors.rowNormal();
      CompactSurfaces.tintedRow(ctx, x, y + 1, w, h - 2, bgColor);
      CompactSurfaces.indicator(ctx, x + 1, y + 3, 3, h - 6, -12268476);
      UiText.draw(
         ctx,
         this.textRenderer,
         String.valueOf(index + 1),
         this.theme.fontFor(UiTone.BODY),
         RiptideColors.textDim(),
         x + 8,
         UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge()),
         false
      );
      String label = this.multiAccountDisplayName(accountId);
      int labelX = x + 24;
      String trimmed = UiText.trimToWidth(this.textRenderer, label, Math.max(1, x + w - labelX - 4), this.theme.fontFor(UiTone.BODY), -1);
      UiText.draw(
         ctx,
         this.textRenderer,
         trimmed,
         this.theme.fontFor(UiTone.BODY),
         -1,
         labelX,
         UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge()),
         false
      );
   }

   private List<MultiProfile.SessionSpec> multiSessionSpecs() {
      MultiManager manager = MultiManager.getIfInitialized();
      MultiProfile profile = manager != null ? manager.activeProfile() : null;
      return profile == null ? List.of() : profile.sessions;
   }

   private String multiAccountDisplayName(String accountId) {
      MultiManager manager = MultiManager.getIfInitialized();
      if (manager != null && accountId != null) {
         for (MultiSession.Snapshot snapshot : manager.snapshots()) {
            if (snapshot != null && accountId.equals(snapshot.accountId()) && snapshot.accountName() != null && !snapshot.accountName().isBlank()) {
               return snapshot.accountName();
            }
         }
      }

      return accountId == null ? "" : accountId;
   }

   private void openMultiAccountSelector(List<String> values) {
      List<RaceStepSelectorOverlay.Option> options = new ArrayList<>();

      for (MultiProfile.SessionSpec spec : this.multiSessionSpecs()) {
         String accountId = spec.accountId();
         if (accountId != null && !accountId.isBlank() && !values.contains(accountId)) {
            StringBuilder description = new StringBuilder();
            if (spec.proxyId() != null && !spec.proxyId().isBlank()) {
               description.append("proxy: ").append(spec.proxyId());
            }

            if (spec.macroName() != null && !spec.macroName().isBlank()) {
               if (description.length() > 0) {
                  description.append("  ");
               }

               description.append("macro: ").append(spec.macroName());
            }

            options.add(new RaceStepSelectorOverlay.Option("Accounts", accountId, this.multiAccountDisplayName(accountId), description.toString()));
         }
      }

      this.raceStepSelectorOverlay.open("Account Selector", options, option -> {
         if (option != null && option.id() != null && !option.id().isBlank()) {
            values.add(option.id());
            this.refreshInteractiveLayout();
         }
      });
   }

   private void beginPacketClickCapture(String fieldKey) {
      if (this.targetAction instanceof PacketClickAction) {
         this.packetClickCapturePendingKey = fieldKey;
         RiptideContainerHold.setPendingCapture(target -> this.applyPacketClickCapture(target));
         this.enterCaptureMode();
      }
   }

   private void applyPacketClickCapture(RiptidePacketClick.Target target) {
      if (target != null) {
         if (this.targetAction instanceof PacketClickAction pca) {
            RiptidePacketClick.Mode mode = this.currentPacketClickMode();
            pca.mode = mode.name();
            pca.setTarget(target.withMode(mode));
            if (this.workingTag != null) {
               this.workingTag.putString("mode", mode.name());
               this.workingTag.put("target", target.withMode(mode).toTag());
            }
         }

         this.packetClickCapturePendingKey = null;
         this.exitCaptureMode(false, false);
      }
   }

   private RiptidePacketClick.Mode currentPacketClickMode() {
      FieldDef modeField = this.fieldByKey("mode");
      if (modeField != null && modeField.type() == FieldType.ENUM && !modeField.enumOptions().isEmpty()) {
         int idx = Math.max(0, Math.min(this.enumIndices.getOrDefault("mode", 0), modeField.enumOptions().size() - 1));
         return RiptidePacketClick.Mode.fromName(modeField.enumOptions().get(idx));
      } else {
         return RiptidePacketClick.Mode.fromName(this.workingTag == null ? "" : this.workingTag.getStringOr("mode", ""));
      }
   }

   private FieldDef fieldByKey(String key) {
      if (this.schema != null && key != null) {
         for (FieldDef field : this.schema.fields()) {
            if (key.equals(field.key())) {
               return field;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public boolean wantsPacketClickCapture() {
      return this.packetClickCapturePendingKey != null && RiptideContainerHold.hasPendingCapture();
   }

   public boolean cancelPacketClickCaptureIfActive() {
      if (this.packetClickCapturePendingKey == null) {
         return false;
      } else {
         RiptideContainerHold.clearPendingCapture();
         this.packetClickCapturePendingKey = null;
         this.exitCaptureMode(false, false);
         return true;
      }
   }

   private boolean isGuiWaitBeforeKey(String key) {
      return "waitForGuiBefore".equals(key);
   }

   private boolean isGuiWaitAfterKey(String key) {
      return "waitForGuiAfter".equals(key);
   }

   private void renderToggle(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
      String key = field.key();
      boolean val = this.toggleStates.getOrDefault(key, false);
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int btnW = 34;
      int btnH = 14;
      String label = this.labelFor(field);
      int lw = this.labelWidth(w, label, font, btnW);
      this.drawLabel(ctx, label, x, y, lw, font);
      int btnX = x + w - btnW;
      this.renderOverlayToggleButton(ctx, btnX, y + 2, btnW, btnH, val ? "ON" : "OFF", val, "macro-field:" + key, mx, my, () -> {
         boolean nowOn = !this.toggleStates.getOrDefault(key, false);
         this.toggleStates.put(key, nowOn);
         if ("waitForTrigger".equals(key)) {
            this.captureWaitTouched = true;
         }

         if (this.workingTag != null) {
            this.workingTag.putBoolean(key, nowOn);
         }

         if (nowOn && field.hasMutualExclusion()) {
            for (String other : field.mutuallyExclusiveWith()) {
               this.toggleStates.put(other, false);
               if (this.workingTag != null) {
                  this.workingTag.putBoolean(other, false);
               }
            }
         }
      });
   }

   private void renderTextField(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      String key = field.key();
      RiptideChatField tf = this.textFields.get(key);
      if (tf != null) {
         Identifier font = this.theme.fontFor(UiTone.BODY);
         if (field.type() == FieldType.MACRO_SELECT) {
            this.renderMacroSelector(ctx, field, tf, x, y, w, mx, my);
         } else if (this.isWaitChatPatternField(field)) {
            UiText.draw(ctx, this.textRenderer, field.label(), font, RiptideColors.textSecondary(), x, y + 2, false);
            tf.setX(x);
            tf.setY(y + 13);
            int variableW = this.shouldShowVariableButton(field.key()) ? 24 : 0;
            tf.setWidth(Math.max(1, w - (variableW > 0 ? variableW + 2 : 0)));
            tf.setHeight(40);
            tf.render(ctx, mx, my, delta);
            if (variableW > 0) {
               this.renderVariableDropdown(field, tf, x + w - variableW, y + 13, variableW);
            }
         } else {
            boolean packetPick = field.captureMode() == CaptureMode.PACKET_NAME;
            boolean slotPick = field.type() == FieldType.SLOT && this.worldCaptureUsable();
            boolean itemSlotPick = field.captureMode() == CaptureMode.ITEM_SLOT && this.worldCaptureUsable();
            boolean menuButtonPick = field.captureMode() == CaptureMode.CUSTOM_MENU_BUTTON;
            boolean nbtBookFilePick = this.targetAction != null && this.targetAction.getType() == MacroActionType.NBT_BOOK && "customFilePath".equals(key);
            int lw = this.labelWidth(w, field.label(), font, !itemSlotPick && !packetPick && !slotPick && !menuButtonPick ? 56 : 88);
            this.drawLabel(ctx, field.label(), x, y, lw, font);
            int tfX = this.controlX(x, lw);
            int variableW = this.shouldShowVariableButton(key) ? 24 : 0;
            if (menuButtonPick) {
               int pickW = 34;
               int availableW = Math.max(1, this.controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
               int tfW = Math.max(1, availableW - pickW - 2);
               tf.setX(tfX);
               tf.setY(y + 2);
               tf.setWidth(tfW);
               tf.render(ctx, mx, my, delta);
               this.renderFieldCaptureButton(
                  ctx, tfX + tfW + 2, y + 2, pickW, 14, CaptureMode.CUSTOM_MENU_BUTTON, false, true, mx, my, () -> this.pickCustomMenuButtonInto(key)
               );
               if (variableW > 0) {
                  this.renderVariableDropdown(field, tf, tfX + tfW + pickW + 4, y + 2, variableW);
               }
            } else if (nbtBookFilePick) {
               int pickW = 30;
               int availableW = Math.max(1, this.controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
               int tfW = Math.max(1, availableW - pickW - 2);
               tf.setX(tfX);
               tf.setY(y + 2);
               tf.setWidth(tfW);
               tf.render(ctx, mx, my, delta);
               this.renderOverlayButton(
                  ctx, tfX + tfW + 2, y + 2, pickW, 14, "Pick", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> this.pickNbtBookTextFile(tf)
               );
               if (variableW > 0) {
                  this.renderVariableDropdown(field, tf, tfX + tfW + pickW + 4, y + 2, variableW);
               }
            } else if (!itemSlotPick && !packetPick && !slotPick) {
               int tfW = Math.max(1, this.controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
               tf.setX(tfX);
               tf.setY(y + 2);
               tf.setWidth(tfW);
               tf.render(ctx, mx, my, delta);
               if (variableW > 0) {
                  this.renderVariableDropdown(field, tf, tfX + tfW + 2, y + 2, variableW);
               }
            } else {
               int pickW = 30;
               int availableW = Math.max(1, this.controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
               int tfW = slotPick ? Math.max(26, availableW - pickW - 2) : Math.max(1, availableW - pickW - 2);
               tf.setX(tfX);
               tf.setY(y + 2);
               tf.setWidth(tfW);
               tf.render(ctx, mx, my, delta);
               int pkX = tfX + tfW + 2;
               boolean capt = !packetPick && this.captureSession.isItemSlotCapture(key);
               this.renderFieldCaptureButton(
                  ctx, pkX, y + 2, pickW, 14, packetPick ? CaptureMode.PACKET_NAME : CaptureMode.ITEM_SLOT, capt, true, mx, my, () -> {
                     if (packetPick) {
                        this.openPacketNameFieldSelector(key);
                     } else {
                        this.toggleItemSlotCapture(key);
                     }
                  }
               );
               if (variableW > 0) {
                  this.renderVariableDropdown(field, tf, pkX + pickW + 2, y + 2, variableW);
               }
            }
         }
      }
   }

   private void pickCustomMenuButtonInto(String key) {
      CustomMenuSnapshot menu = CustomMenuTracker.current();
      if (menu == null) {
         RiptideNotifications.warning("No custom screen is open to pick a button from.");
      } else {
         List<CustomMenuButton> buttons = new ArrayList<>();

         for (CustomMenuButton button : menu.buttons()) {
            if (button.serverRelevant() && !button.label().isBlank()) {
               buttons.add(button);
            }
         }

         if (buttons.isEmpty()) {
            RiptideNotifications.warning("This screen has no pressable buttons.");
         } else {
            RiptideChatField tf = this.textFields.get(key);
            String current = tf == null ? "" : tf.getText().trim();
            int next = 0;

            for (int i = 0; i < buttons.size(); i++) {
               if (buttons.get(i).label().equalsIgnoreCase(current)) {
                  next = (i + 1) % buttons.size();
                  break;
               }
            }

            String label = buttons.get(next).label();
            if (tf != null) {
               tf.setText(label);
            }

            RiptideNotifications.show("Button set to \"" + label + "\"" + (buttons.size() > 1 ? " (Pick again to cycle)" : ""), -11013497);
         }
      }
   }

   private void renderVariableDropdown(FieldDef field, RiptideChatField textField, int x, int y, int width) {
      if (field != null) {
         this.renderVariableDropdown(field.key(), textField, x, y, width);
      }
   }

   private void renderVariableDropdown(String key, RiptideChatField textField, int x, int y, int width) {
      if (this.shouldShowVariableButton(key) && textField != null) {
         String cacheKey = "__vars_" + key;
         CompactDropdown dropdown = this.enumDropdownCache
            .computeIfAbsent(cacheKey, ignored -> new CompactDropdown(x, y, width, 14, this.variableTokens, 0, index -> {}));
         dropdown.setBounds(x, y, width, 14).setOptions(this.variableTokens).setButtonLabelOverride("{}").setOnSelect(index -> {
            if (index >= 0 && index < this.variableTokens.size()) {
               textField.insertText(this.variableTokens.get(index));
            }

            dropdown.close();
         });
         this.enumDropdowns.add(dropdown);
      }
   }

   private boolean shouldShowVariableButton(String key) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.SEND_CHAT && "message".equals(key) && !this.variableTokens.isEmpty();
   }

   private void rebuildVariableTokens() {
      LinkedHashSet<String> tokens = new LinkedHashSet<>();
      if (this.variableMacro != null && this.variableMacro.actions != null) {
         int limit = this.variableActionIndex < 0 ? this.variableMacro.actions.size() : Math.min(this.variableActionIndex, this.variableMacro.actions.size());

         for (int i = limit - 1; i >= 0 && tokens.size() < 4; i--) {
            addCaptureValueTokens(tokens, this.variableMacro.actions.get(i), 4);
         }
      }

      tokens.addAll(DEFAULT_VARIABLE_TOKENS);
      this.variableTokens.clear();
      this.variableTokens.addAll(tokens);
   }

   private static void addCaptureValueTokens(Set<String> tokens, MacroAction action, int limit) {
      if (action instanceof CaptureValueAction capture && tokens.size() < limit) {
         for (String name : MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) {
            if (tokens.size() >= limit) {
               return;
            }

            tokens.add("{" + name + "}");
         }

         if (tokens.size() < limit) {
            String root = MacroVariableContext.cleanRootName(capture.saveAs);
            if (!root.isBlank()) {
               tokens.add("{" + root + "}");
            }
         }
      }
   }

   private static void addActionVariableTokens(Set<String> tokens, MacroAction action) {
      if (action instanceof WaitForChatAction chat) {
         for (String name : MacroCapturePattern.declaredNames(chat.effectiveMatchMode(), chat.pattern)) {
            tokens.add("{" + name + "}");
         }

         addStructuredTokens(tokens, chat.saveAs, "sender", "message", "display", "source");
      } else if (action instanceof WaitForGuiAction gui) {
         for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.guiTitle)) {
            tokens.add("{" + name + "}");
         }

         addStructuredTokens(tokens, gui.saveAs, "title", "type");
      } else if (action instanceof CaptureValueAction capture) {
         for (String name : MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) {
            tokens.add("{" + name + "}");
         }

         switch (capture.source) {
            case GUI_TITLE:
               addStructuredTokens(tokens, capture.saveAs, "title", "type");
               break;
            case RECENT_CHAT:
               addStructuredTokens(tokens, capture.saveAs, "sender", "message", "display", "source");
               break;
            case SCOREBOARD:
               addStructuredTokens(tokens, capture.saveAs, "line", "name", "score", "row", "title", "objective");
               break;
            case GUI_ITEM:
            case PLAYER_ITEM:
            case CURSOR_ITEM:
            case HELD_ITEM:
               addStructuredTokens(tokens, capture.saveAs, "name", "id", "count", "slot", "lore", "lore1");
         }
      } else if (action instanceof SelectSlotAction select && select.outputVariable != null && !select.outputVariable.isBlank()) {
         tokens.add("{" + select.outputVariable.trim() + "}");
      } else if (action instanceof MacroVariablesAction variables) {
         for (String name : variables.names) {
            if (name != null && !name.isBlank()) {
               tokens.add("{" + name.trim() + "}");
            }
         }
      } else if (action instanceof MacroCaptureOutput output) {
         String root = output.getSaveAs();
         if (action instanceof WaitForHealthAction) {
            addStructuredTokens(tokens, root, "health", "max", "absorption");
         } else if (action instanceof WaitForEntityAction) {
            addStructuredTokens(tokens, root, "name", "id", "uuid", "x", "y", "z", "distance");
         } else if (action instanceof WaitForSoundAction) {
            addStructuredTokens(tokens, root, "id", "x", "y", "z");
         } else if (action instanceof WaitForPacketAction || action instanceof WaitPacketMatchAction) {
            addStructuredTokens(tokens, root, "name", "class", "direction");
            if (action instanceof WaitPacketMatchAction packetMatch) {
               String cleanRoot = MacroVariableContext.cleanRootName(root);
               if (!cleanRoot.isBlank()) {
                  for (WaitPacketMatchAction.Rule rule : packetMatch.effectiveRules()) {
                     if (rule.fieldName != null && !rule.fieldName.isBlank()) {
                        tokens.add("{" + cleanRoot + "." + rule.fieldName.trim().toLowerCase(Locale.ROOT) + "}");
                     }
                  }
               }
            }
         } else if (action instanceof WaitGuiTypeAction gui) {
            for (String namex : MacroCapturePattern.declaredNames(gui.matchMode, gui.title)) {
               tokens.add("{" + namex + "}");
            }

            addStructuredTokens(tokens, root, "title", "type");
         } else if (action instanceof WaitPosAction || action instanceof WaitForPositionDeltaAction || action instanceof WaitForTeleportAction) {
            addStructuredTokens(tokens, root, "x", "y", "z", "yaw", "pitch");
         } else if (action instanceof WaitForWorldChangeAction) {
            addStructuredTokens(tokens, root, "id", "name");
         } else if (action instanceof WaitGamemodeChangeAction) {
            addStructuredTokens(tokens, root, "name");
         } else {
            addStructuredTokens(tokens, root, "name", "id", "count", "slot", "condition");
         }
      }
   }

   private static void addStructuredTokens(Set<String> tokens, String root, String... properties) {
      if (root != null && !root.isBlank()) {
         String clean = MacroVariableContext.cleanRootName(root);
         if (!clean.isBlank()) {
            tokens.add("{" + clean + "}");

            for (String property : properties) {
               tokens.add("{" + clean + "." + property + "}");
            }
         }
      }
   }

   private void renderMacroSelector(GuiGraphicsExtractor ctx, FieldDef field, RiptideChatField backingField, int x, int y, int w, int mx, int my) {
      List<String> macros = this.availableMacroNames(backingField.getText());
      Identifier font = this.theme.fontFor(UiTone.BODY);
      String current = backingField.getText() == null ? "" : backingField.getText();
      if (current.isBlank() && !macros.isEmpty()) {
         backingField.setText(macros.get(0));
         current = backingField.getText();
      }

      UiText.draw(ctx, this.textRenderer, field.label(), font, RiptideColors.textSecondary(), x, y + 2, false);
      int badgeW = Math.min(150, Math.max(60, w / 2));
      int badgeX = x + w - badgeW;
      CompactSurfaces.valueField(ctx, badgeX, y + 1, badgeW, 14);
      String selectedLabel = current.isBlank() ? "No macro selected" : current;
      String selectedTrimmed = UiText.trimToWidth(this.textRenderer, selectedLabel, badgeW - 6, font, -1);
      UiText.draw(
         ctx, this.textRenderer, selectedTrimmed, font, current.isBlank() ? RiptideColors.textMuted() : RiptideColors.textPrimary(), badgeX + 3, y + 3, false
      );
      int listY = y + 17;
      int listH = 75;
      int itemW = w - 5 - 1;
      String viewportKey = "macro_select_" + field.key();
      DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, viewportKey, x, listY, w, listH, 15, 5);
      viewport.setContentHeight(macros.size() * 15);
      viewport.renderScrollbar(ctx, mx, my);
      if (macros.isEmpty()) {
         CompactSurfaces.row(ctx, x, listY, itemW, 13, false, false);
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No saved macros", x, listY, itemW);
      } else {
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = viewport.getFirstVisibleRow();

         for (int i = first; i < macros.size() && i <= viewport.getLastVisibleRow(); i++) {
            String macroName = macros.get(i);
            int iy = viewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               boolean selected = macroName.equals(current);
               boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + 15;
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  Component.literal(macroName),
                  UiBounds.of(x, iy, itemW, 15),
                  hovered,
                  selected,
                  CompactListRenderer.RowTone.NORMAL,
                  false
               );
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, itemW, 15, () -> backingField.setText(macroName)));
            }
         }

         viewport.endRender(ctx);
      }
   }

   private List<String> availableMacroNames(String currentValue) {
      RiptideMacroManager manager = RiptideMacroManager.get();
      long revision = manager.getRevision();
      if (revision != this.cachedMacroNamesRevision) {
         this.cachedMacroNames.clear();

         for (RiptideMacro macro : manager.getAll()) {
            if (macro != null && macro.name != null && !macro.name.isBlank()) {
               this.cachedMacroNames.add(macro.name);
            }
         }

         this.cachedMacroNamesRevision = revision;
      }

      if (currentValue != null && !currentValue.isBlank() && !this.cachedMacroNames.contains(currentValue)) {
         this.macroNamesWithCurrent.clear();
         this.macroNamesWithCurrent.add(currentValue);
         this.macroNamesWithCurrent.addAll(this.cachedMacroNames);
         return this.macroNamesWithCurrent;
      } else {
         return this.cachedMacroNames;
      }
   }

   private boolean isWaitChatPatternField(FieldDef field) {
      return field != null && "pattern".equals(field.key()) && this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_CHAT;
   }

   private void renderEnum(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
      String key = field.key();
      List<String> opts = field.enumOptions();
      if (!opts.isEmpty()) {
         int idx = Math.min(this.enumIndices.getOrDefault(key, 0), opts.size() - 1);
         Identifier font = this.theme.fontFor(UiTone.BODY);
         int lw = this.labelWidth(w, field.label(), font, 72);
         this.drawLabel(ctx, field.label(), x, y, lw, font);
         int ctrlX = this.controlX(x, lw);
         int ctrlW = this.controlWidth(w, lw);
         int ddH = 16;
         int variableW = this.shouldShowVariableButton(key) ? 20 : 0;
         int valueW = Math.max(1, ctrlW - (variableW > 0 ? variableW + 2 : 0));
         RiptideChatField bindingField = field.dynamic() ? this.textFields.get(key) : null;
         if (bindingField != null && !bindingField.getText().isBlank()) {
            bindingField.setX(ctrlX);
            bindingField.setY(y + 2);
            bindingField.setWidth(valueW);
            bindingField.render(ctx, mx, my, 0.0F);
            if (variableW > 0) {
               this.renderDynamicEnumDropdown(field, bindingField, ctrlX + valueW + 2, y + 2, variableW);
            }
         } else {
            CompactDropdown dd = this.enumDropdownCache.computeIfAbsent(key, k -> new CompactDropdown(ctrlX, y + 1, valueW, ddH, opts, idx, newIdx -> {
               if (newIdx >= 0 && newIdx < opts.size()) {
                  this.enumIndices.put(key, newIdx);
               }
            }));
            dd.setBounds(ctrlX, y + 1, valueW, ddH).setOptions(opts).setSelectedIndex(idx).setOnSelect(newIdx -> {
               if (newIdx >= 0 && newIdx < opts.size()) {
                  this.enumIndices.put(key, newIdx);
               }
            });
            this.enumDropdowns.add(dd);
            if (variableW > 0 && bindingField != null) {
               this.renderDynamicEnumDropdown(field, bindingField, ctrlX + valueW + 2, y + 2, variableW);
            }
         }
      }
   }

   private void renderDynamicEnumDropdown(FieldDef field, RiptideChatField bindingField, int x, int y, int width) {
      List<String> options = new ArrayList<>();
      options.add("Static");
      options.addAll(this.variableTokens);
      String cacheKey = "__enum_vars_" + field.key();
      CompactDropdown dropdown = this.enumDropdownCache.computeIfAbsent(cacheKey, ignored -> new CompactDropdown(x, y, width, 14, options, 0, index -> {}));
      dropdown.setBounds(x, y, width, 14).setOptions(options).setButtonLabelOverride("{}").setOnSelect(index -> {
         if (index <= 0) {
            bindingField.setText("");
         } else if (index < options.size()) {
            bindingField.setText(options.get(index));
         }

         dropdown.close();
      });
      this.enumDropdowns.add(dropdown);
   }

   private void renderBlockPos(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      String key = field.key();
      Identifier font = this.theme.fontFor(UiTone.BODY);
      String[] axis = new String[]{"X", "Y", "Z"};
      boolean hasCapture = field.captureMode() != CaptureMode.NONE && this.captureControlAvailable(field.captureMode());
      int capBtnW = 52;
      int capBtnH = 14;
      int labelW = hasCapture ? w - capBtnW - 4 : w;
      UiText.draw(ctx, this.textRenderer, field.label(), font, RiptideColors.textSecondary(), x, y + 3, false);
      if (hasCapture) {
         int cbX = x + labelW + 4;
         this.renderFieldCaptureButton(ctx, cbX, y, capBtnW, capBtnH, field.captureMode(), false, true, mx, my, () -> this.startBlockPosCapture(field));
      }

      int fieldW = (w - 4) / 3;

      for (int i = 0; i < 3; i++) {
         RiptideChatField tf = this.textFields.get(key + "_" + i);
         if (tf != null) {
            int fx = x + i * (fieldW + 2);
            int variableW = this.shouldShowVariableButton(field.key()) ? 18 : 0;
            UiText.draw(ctx, this.textRenderer, axis[i], font, RiptideColors.textDim(), fx, y + 15 + 3, false);
            tf.setX(fx + 9);
            tf.setY(y + 15 + 1);
            tf.setWidth(Math.max(1, fieldW - 9 - (variableW > 0 ? variableW + 2 : 0)));
            tf.render(ctx, mx, my, delta);
            if (variableW > 0) {
               this.renderVariableDropdown(field.key() + "_" + axis[i].toLowerCase(Locale.ROOT), tf, fx + fieldW - variableW, y + 15 + 2, variableW);
            }
         }
      }
   }

   private void pickNbtBookTextFile(RiptideChatField field) {
      if (field != null) {
         String current = field.getText() == null ? "" : field.getText().trim();
         String selected = TinyFileDialogs.tinyfd_openFileDialog("NBT Book Text File", current.isBlank() ? null : current, null, null, false);
         if (selected != null && !selected.isBlank()) {
            field.setText(selected);
         }
      }
   }

   private void renderStringList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      String key = field.key();
      List<String> lst = this.stringLists.getOrDefault(key, Collections.emptyList());
      Identifier font = this.theme.fontFor(UiTone.BODY);
      RiptideChatField af = this.addFields.get(key);
      boolean editable = this.isEditableStringList(key);
      boolean xcarryList = this.isXCarryListKey(key);
      int editIdx = editable ? this.stringListEditIndex.getOrDefault(key, -1) : -1;
      if (editIdx >= lst.size()) {
         editIdx = -1;
         this.stringListEditIndex.put(key, -1);
      }

      String filter = !editable && af != null ? af.getText().toLowerCase() : "";
      String emptyHint = this.getStringListEmptyHint(key);
      List<Integer> filtered = MacroTypedListControl.refilter(
         lst,
         this.stringListFilteredIndices.computeIfAbsent(key, ignored -> new ArrayList<>()),
         value -> filter.isEmpty() || value.toLowerCase().contains(filter)
      );
      String sectionLabel = filter.isEmpty() ? field.label() + " (" + lst.size() + ")" : field.label() + " (" + filtered.size() + "/" + lst.size() + ")";
      UiText.draw(ctx, this.textRenderer, sectionLabel, font, RiptideColors.textSecondary(), x, y + 2, false);
      boolean hasCapture = field.captureMode() != CaptureMode.NONE && this.captureControlAvailable(field.captureMode());
      boolean hasSlotField = editable && this.usesMinecraftTextRendering(key);
      int btnH = 14;
      int addY = y + 13;
      MacroTypedListControl.ToolbarLayout toolbar = MacroTypedListControl.toolbar(UiBounds.of(x, addY, w, 14), hasSlotField, hasCapture);
      int slotW = toolbar.slot() == null ? 0 : toolbar.slot().width();
      RiptideChatField slotField = hasSlotField ? this.textFields.get(key + "_slot") : null;
      if (hasSlotField && slotField == null) {
         slotField = this.makeField(slotW);
         slotField.setNumericOnly(true);
         slotField.setPlaceholder(Component.literal("Slot#"));
         this.textFields.put(key + "_slot", slotField);
      }

      if (af != null) {
         af.setX(toolbar.text().x());
         af.setY(toolbar.text().y() + 1);
         af.setWidth(toolbar.text().width());
         af.render(ctx, mx, my, delta);
      }

      if (hasSlotField && slotField != null) {
         slotField.setX(toolbar.slot().x());
         slotField.setY(toolbar.slot().y() + 1);
         slotField.setWidth(toolbar.slot().width());
         slotField.render(ctx, mx, my, delta);
      }

      if (editable && editIdx >= 0 && editIdx < lst.size() && af != null) {
         String nameText = af.getText().strip();
         String slotText = hasSlotField && slotField != null ? slotField.getText().strip() : "";
         String entry = buildEntryFromNameAndSlot(nameText, slotText);
         if (!entry.isEmpty()) {
            String normalized = this.isXCarryListKey(key)
               ? this.normalizeXCarryEntry(entry)
               : (this.usesStoreTargetFormatting(key) ? StoreItemAction.normalizeTargetEntry(entry) : entry);
            if (normalized != null && !normalized.isBlank() && !normalized.equals(lst.get(editIdx))) {
               lst.set(editIdx, normalized);
               if (this.usesMinecraftTextRendering(key)) {
                  this.editorItemLists.put(key, this.buildStructuredListTargets(key));
               }
            }
         }
      }

      RiptideChatField fSlotField = slotField;
      if (af != null) {
         this.renderOverlayButton(
            ctx, toolbar.add().x(), toolbar.add().y(), toolbar.add().width(), btnH, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
               String nameText = af.getText().strip();
               String slotText = hasSlotField && fSlotField != null ? fSlotField.getText().strip() : "";
               String entry = buildEntryFromNameAndSlot(nameText, slotText);
               if (!entry.isEmpty() && this.addStringListEntry(field, lst, entry)) {
                  af.setText("");
                  if (fSlotField != null) {
                     fSlotField.setText("");
                  }

                  this.stringListEditIndex.put(key, -1);
               }
            }
         );
      }

      if (hasCapture) {
         boolean isItemSlot = field.captureMode() == CaptureMode.ITEM_SLOT;
         boolean capturing = isItemSlot && this.captureSession.isItemSlotCapture(key);
         this.renderFieldCaptureButton(
            ctx, toolbar.capture().x(), toolbar.capture().y(), toolbar.capture().width(), btnH, field.captureMode(), capturing, true, mx, my, () -> {
               if (isItemSlot) {
                  this.toggleItemSlotCapture(key);
               } else {
                  this.startCapture(field, lst);
               }
            }
         );
      }

      int itemsY = addY + btnH + 2;
      int selAreaH = 60;
      int delW = 13;
      int destW = xcarryList ? 62 : 0;
      int amountW = xcarryList ? 54 : 0;
      if (xcarryList) {
         this.syncXCarryDestinationCount(lst.size());
      }

      if (xcarryList) {
         this.syncXCarryAmountCount(lst.size());
         if (editIdx >= 0 && editIdx < lst.size()) {
            int selectedXCarryIndex = editIdx;
            RiptideChatField countField = this.textFields.get(key + "_xcarry_count");
            if (countField == null) {
               countField = this.makeField(38);
               countField.setNumericOnly(true);
               countField.setPlaceholder(Component.literal("Count"));
               this.textFields.put(key + "_xcarry_count", countField);
            }

            UiText.draw(ctx, this.textRenderer, "Amount", this.theme.fontFor(UiTone.BODY), RiptideColors.textSecondary(), x, itemsY + 3, false);
            int modeX = x + 52;
            boolean custom = this.getXCarryAmountMode(editIdx) == XCarryAction.AmountMode.CUSTOM;
            this.renderOverlayButton(ctx, modeX, itemsY, 58, btnH, custom ? "Count" : "FullStk", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
               boolean nowCustom = this.getXCarryAmountMode(selectedXCarryIndex) == XCarryAction.AmountMode.CUSTOM;
               this.setXCarryAmountMode(selectedXCarryIndex, nowCustom ? XCarryAction.AmountMode.FULL_STK : XCarryAction.AmountMode.CUSTOM);
               RiptideChatField cf = this.textFields.get(key + "_xcarry_count");
               if (cf != null) {
                  cf.setText(String.valueOf(this.getXCarryAmount(selectedXCarryIndex)));
               }
            });
            if (custom) {
               countField.setX(modeX + 62);
               countField.setY(itemsY + 1);
               countField.setWidth(42);
               if (countField.getText().isBlank()) {
                  countField.setText(String.valueOf(this.getXCarryAmount(editIdx)));
               }

               countField.render(ctx, mx, my, delta);

               try {
                  int parsed = Integer.parseInt(countField.getText().strip());
                  this.setXCarryAmount(selectedXCarryIndex, Math.max(1, parsed));
               } catch (NumberFormatException var55) {
               }
            }

            itemsY = itemsY + btnH + 2;
         }
      }

      DirectScrollViewport strViewport = this.getOrCreateViewport(this.selectedScrollViewports, key, x, itemsY, w, selAreaH, 15, 5);
      strViewport.setContentHeight(filtered.size() * 15);
      strViewport.renderScrollbar(ctx, mx, my);
      if (!filtered.isEmpty()) {
         int rowAreaW = Math.max(24, w - 5 - 1 - delW - 2 - (destW > 0 ? destW + 2 : 0) - (amountW > 0 ? amountW + 2 : 0));
         strViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstVi = strViewport.getFirstVisibleRow();

         for (int vi = firstVi; vi < filtered.size() && vi <= strViewport.getLastVisibleRow(); vi++) {
            int ri = filtered.get(vi);
            int iy = strViewport.getRowScreenY(vi);
            if (iy != Integer.MIN_VALUE) {
               Component displayValue = this.formatStringListEntryText(key, lst.get(ri), ri);
               boolean selected = editable && ri == editIdx;
               boolean hovered = mx >= x && mx < x + rowAreaW && my >= iy && my < iy + 15;
               if (field.captureMode() == CaptureMode.PACKET_NAME) {
                  int textColor = this.getPacketRowColor(lst.get(ri), ri, selected);
                  MacroTypedListControl.renderRowWithColor(
                     ctx, this.textRenderer, displayValue, UiBounds.of(x, iy, rowAreaW, 15), hovered, selected, textColor, this.usesMinecraftTextRendering(key)
                  );
               } else {
                  MacroTypedListControl.renderRow(
                     ctx,
                     this.textRenderer,
                     displayValue,
                     UiBounds.of(x, iy, rowAreaW, 15),
                     hovered,
                     selected,
                     CompactListRenderer.RowTone.NORMAL,
                     this.usesMinecraftTextRendering(key)
                  );
               }

               if (editable) {
                  RiptideChatField fSlotF2 = hasSlotField ? this.textFields.get(key + "_slot") : null;
                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, rowAreaW, 15, () -> {
                     int curIdx = this.stringListEditIndex.getOrDefault(key, -1);
                     if (curIdx == ri) {
                        this.stringListEditIndex.put(key, -1);
                        if (af != null) {
                           af.setText("");
                        }

                        if (fSlotF2 != null) {
                           fSlotF2.setText("");
                        }

                        RiptideChatField countFieldx = this.textFields.get(key + "_xcarry_count");
                        if (countFieldx != null) {
                           countFieldx.setText("");
                        }
                     } else {
                        this.stringListEditIndex.put(key, ri);
                        String raw = lst.get(ri);
                        if (af != null) {
                           af.setText(this.parseHandlerEntryName(raw));
                        }

                        if (fSlotF2 != null) {
                           ItemTarget parsed = ItemTarget.fromLegacyEntry(raw);
                           fSlotF2.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
                        }

                        RiptideChatField countFieldx = this.textFields.get(key + "_xcarry_count");
                        if (countFieldx != null) {
                           countFieldx.setText(String.valueOf(this.getXCarryAmount(ri)));
                        }
                     }
                  }));
               }

               int actionX = x + rowAreaW + 2;
               if (xcarryList) {
                  int dest = this.getXCarryDestination(ri);
                  int[] destValues = XCarryAction.destinationValues();
                  List<String> destOptions = XCarryAction.destinationLabels();
                  int selectedDestIndex = XCarryAction.destinationIndex(dest);
                  String ddKey = key + "_xcarry_dest_" + ri;
                  int ddX = actionX;
                  int ddY = iy + 1;
                  CompactDropdown dd = this.enumDropdownCache
                     .computeIfAbsent(ddKey, k -> new CompactDropdown(ddX, ddY, destW, 13, destOptions, selectedDestIndex, newIdx -> {
                        if (newIdx >= 0 && newIdx < destValues.length) {
                           this.setXCarryDestination(ri, destValues[newIdx]);
                        }
                     }));
                  dd.setBounds(ddX, ddY, destW, 13).setOptions(destOptions).setSelectedIndex(selectedDestIndex).setOnSelect(newIdx -> {
                     if (newIdx >= 0 && newIdx < destValues.length) {
                        this.setXCarryDestination(ri, destValues[newIdx]);
                     }
                  });
                  this.enumDropdowns.add(dd);
                  actionX += destW + 2;
                  boolean custom = this.getXCarryAmountMode(ri) == XCarryAction.AmountMode.CUSTOM;
                  String amountLabel = custom ? "x" + this.getXCarryAmount(ri) : "FullStk";
                  this.renderOverlayButton(
                     ctx,
                     actionX,
                     iy + 1,
                     amountW,
                     13,
                     amountLabel,
                     custom ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY,
                     true,
                     mx,
                     my,
                     () -> {
                        boolean nowCustom = this.getXCarryAmountMode(ri) == XCarryAction.AmountMode.CUSTOM;
                        this.setXCarryAmountMode(ri, nowCustom ? XCarryAction.AmountMode.FULL_STK : XCarryAction.AmountMode.CUSTOM);
                        this.stringListEditIndex.put(key, ri);
                        RiptideChatField cf = this.textFields.get(key + "_xcarry_count");
                        if (cf != null) {
                           cf.setText(String.valueOf(this.getXCarryAmount(ri)));
                        }
                     }
                  );
                  actionX += amountW + 2;
               }

               this.renderIconDeleteButton(ctx, actionX, iy + 1, delW, mx, my, () -> {
                  lst.remove(ri);
                  if (xcarryList) {
                     this.removeXCarryDestination(ri);
                     this.removeXCarryAmount(ri);
                  }

                  if (editable && this.stringListEditIndex.getOrDefault(key, -1) == ri) {
                     this.stringListEditIndex.put(key, -1);
                     if (af != null) {
                        af.setText("");
                     }
                  } else if (editable && this.stringListEditIndex.getOrDefault(key, -1) > ri) {
                     this.stringListEditIndex.put(key, this.stringListEditIndex.get(key) - 1);
                  }

                  DirectScrollViewport vp = this.selectedScrollViewports.get(key);
                  if (vp != null) {
                     vp.scrollBy(-1);
                  }

                  if (this.usesMinecraftTextRendering(key)) {
                     this.editorItemLists.put(key, this.buildStructuredListTargets(key));
                  }
               });
            }
         }

         strViewport.endRender(ctx);
      } else if (emptyHint != null && !emptyHint.isBlank()) {
         int itemW = w - 5 - 1;
         CompactSurfaces.row(ctx, x, itemsY, itemW, 12, false, false);
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, emptyHint, x, itemsY, itemW);
      }
   }

   private String getStringListEmptyHint(String key) {
      if (this.isInventoryAuditTargetListKey(key)) {
         List<String> selected = this.stringLists.getOrDefault(key, Collections.emptyList());
         if (selected.isEmpty()) {
            return "Add at least one target before running this audit.";
         }
      }

      return null;
   }

   private void renderStringListCatalog(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      String key = field.key();
      List<String> sel = this.stringLists.getOrDefault(key, Collections.emptyList());
      Identifier font = this.theme.fontFor(UiTone.BODY);
      RiptideChatField af = this.addFields.get(key);
      String filter = af != null ? af.getText().toLowerCase() : "";
      UiText.draw(ctx, this.textRenderer, field.label() + " (" + sel.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      int cy = y + 13;
      int delW = 13;
      int selAreaH = 60;
      DirectScrollViewport catSelViewport = this.getOrCreateViewport(this.selectedScrollViewports, key, x, cy, w, selAreaH, 15, 5);
      catSelViewport.setContentHeight(sel.size() * 15);
      catSelViewport.renderScrollbar(ctx, mx, my);
      if (!sel.isEmpty()) {
         int delX = x + w - 5 - 2 - delW;
         catSelViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstSel = catSelViewport.getFirstVisibleRow();

         for (int i = firstSel; i < sel.size() && i <= catSelViewport.getLastVisibleRow(); i++) {
            int siy = catSelViewport.getRowScreenY(i);
            if (siy != Integer.MIN_VALUE) {
               String display = RiptideRegistryLabels.block(sel.get(i));
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  Component.literal(display),
                  UiBounds.of(x, siy, delX - x - 1, 13),
                  mx >= x && mx < delX - 1 && my >= siy && my < siy + 13,
                  false,
                  CompactListRenderer.RowTone.NORMAL,
                  false
               );
               int fi = i;
               this.renderIconDeleteButton(ctx, delX, siy, delW, mx, my, () -> {
                  sel.remove(fi);
                  DirectScrollViewport vp = this.selectedScrollViewports.get(key);
                  if (vp != null) {
                     vp.scrollBy(-1);
                  }
               });
            }
         }

         catSelViewport.endRender(ctx);
      } else {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "(none selected)", x, cy, w - 5 - 1);
      }

      cy += selAreaH;
      CompactSurfaces.divider(ctx, x, cy + 1, w, RiptideColors.subPanelBorder());
      cy += 5;
      if (af != null) {
         af.setX(x);
         af.setY(cy);
         af.setWidth(w);
         af.render(ctx, mx, my, delta);
      }

      cy += 14;
      int headerH = 15;
      int capBtnH2 = 14;
      int cbX = x + w - 52;
      int headerTextW = Math.max(24, cbX - x - 4);
      String headerLabel = UiText.trimToWidth(this.textRenderer, "Available Blocks", headerTextW, font, RiptideColors.textSecondary());
      int headerTextY = UiSizing.alignTextY(cy, headerH, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
      this.renderFieldCaptureButton(ctx, cbX, cy, 52, capBtnH2, field.captureMode(), false, true, mx, my, () -> this.startCapture(field, sel));
      UiText.draw(ctx, this.textRenderer, headerLabel, font, RiptideColors.textSecondary(), x, headerTextY, false);
      cy += headerH + 2;
      List<String> filtered = MacroTypedListControl.refilterValues(
         getAllBlockIds(),
         this.catalogFilteredValues.computeIfAbsent(key + "_catalog", ignored -> new ArrayList<>()),
         idx -> matchesListFilter(filter, idx, trimMinecraftPrefix(idx), RiptideRegistryLabels.block(idx))
      );
      int catItemW = w - 5 - 1;
      DirectScrollViewport catViewport = this.getOrCreateViewport(this.catalogScrollViewports, key, x, cy, w, 60, 13, 5);
      catViewport.setContentHeight(filtered.size() * 13);
      catViewport.renderScrollbar(ctx, mx, my);
      catViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
      int firstItem = catViewport.getFirstVisibleRow();

      for (int ix = firstItem; ix < filtered.size() && ix <= catViewport.getLastVisibleRow(); ix++) {
         int iy = catViewport.getRowScreenY(ix);
         if (iy != Integer.MIN_VALUE) {
            String id = filtered.get(ix);
            String display = RiptideRegistryLabels.block(id);
            boolean already = sel.contains(id);
            boolean hov = mx >= x && mx < x + catItemW && my >= iy && my < iy + 13;
            MacroTypedListControl.renderRow(
               ctx,
               this.textRenderer,
               Component.literal(display),
               UiBounds.of(x, iy, catItemW, 13),
               hov,
               already,
               already ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
               false
            );
            if (!already) {
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, catItemW, 13, () -> {
                  if (!sel.contains(id)) {
                     sel.add(id);
                  }
               }));
            } else {
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, catItemW, 13, () -> sel.remove(id)));
            }
         }
      }

      catViewport.endRender(ctx);
   }

   private void renderFooter(GuiGraphicsExtractor ctx, int footerY, int mx, int my) {
      int btnH = 16;
      String variableWarning = this.variableAvailabilityWarning();
      int btnY = variableWarning.isBlank() ? footerY + (32 - btnH) / 2 : footerY + 32 - btnH - 2;
      if (this.standalonePayloadEditor) {
         int closeW = 72;
         int closeX = this.panelX + (this.panelW - closeW) / 2;
         CompactSurfaces.divider(ctx, this.panelX + 4, footerY, this.panelW - 8, RiptideColors.subPanelBorder());
         this.renderOverlayButton(ctx, closeX, btnY, closeW, btnH, "Close", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> this.closeEditor(false));
      } else {
         int btnW = this.payloadAction != null ? 72 : 58;
         int gap = 6;
         int total = btnW * 2 + gap;
         int sx = this.panelX + (this.panelW - total) / 2;
         CompactSurfaces.divider(ctx, this.panelX + 4, footerY, this.panelW - 8, RiptideColors.subPanelBorder());
         if (!variableWarning.isBlank()) {
            Identifier font = this.theme.fontFor(UiTone.BODY);
            String text = UiText.trimToWidth(this.textRenderer, variableWarning, this.panelW - 8, font, -1);
            UiText.draw(ctx, this.textRenderer, text, font, -38037, this.panelX + Math.max(4, (this.panelW - this.uiWidth(font, text)) / 2), footerY + 2, false);
         }

         this.renderOverlayButton(ctx, sx, btnY, btnW, btnH, "Cancel", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> this.closeEditor(false));
         int saveX = sx + btnW + gap;
         this.renderOverlayButton(
            ctx,
            saveX,
            btnY,
            btnW,
            btnH,
            this.payloadAction != null ? "Save Macro" : "Save",
            CompactOverlayButton.Variant.PRIMARY,
            true,
            mx,
            my,
            () -> this.closeEditor(true)
         );
      }
   }

   private String variableAvailabilityWarning() {
      if (this.variableMacro != null && this.variableMacro.actions != null && this.variableActionIndex >= 0) {
         Set<String> used = new LinkedHashSet<>();

         for (RiptideChatField field : this.textFields.values()) {
            collectTemplateRoots(used, field == null ? "" : field.getText());
         }

         for (List<String> values : this.stringLists.values()) {
            for (String value : values) {
               collectTemplateRoots(used, value);
            }
         }

         used.removeAll(this.currentActionCaptureRoots());
         if (used.isEmpty()) {
            return "";
         } else {
            used.removeAll(MacroTemplate.BUILT_IN_NAMES);
            if (used.isEmpty()) {
               return "";
            } else {
               Map<String, Integer> providerIndex = new LinkedHashMap<>();

               for (int i = 0; i < this.variableMacro.actions.size(); i++) {
                  LinkedHashSet<String> supplied = new LinkedHashSet<>();
                  addActionVariableTokens(supplied, this.variableMacro.actions.get(i));

                  for (String token : supplied) {
                     LinkedHashSet<String> roots = new LinkedHashSet<>();
                     collectTemplateRoots(roots, token);

                     for (String root : roots) {
                        providerIndex.putIfAbsent(root, i);
                     }
                  }
               }

               for (String root : used) {
                  Integer provider = providerIndex.get(root);
                  if (provider == null) {
                     return "Value may be missing";
                  }

                  if (provider >= this.variableActionIndex) {
                     return "Captured later";
                  }

                  if (this.isRaceBodyIndex(provider) && !this.sameRaceBody(provider, this.variableActionIndex)) {
                     return "Race-only value";
                  }
               }

               return "";
            }
         }
      } else {
         return "";
      }
   }

   private Set<String> currentActionCaptureRoots() {
      LinkedHashSet<String> outputs = new LinkedHashSet<>();
      if (this.targetAction instanceof MacroCaptureOutput captureOutput) {
         String root = MacroVariableContext.cleanRootName(captureOutput.getSaveAs());
         if (!root.isBlank()) {
            outputs.add(root);
         }
      }

      String pattern = "";
      MacroCapturePattern.Mode mode = MacroCapturePattern.Mode.MATCH;
      if (this.targetAction instanceof WaitForChatAction) {
         RiptideChatField field = this.textFields.get("pattern");
         pattern = field == null ? "" : field.getText();
         mode = this.editorCaptureMode();
      } else if (this.targetAction instanceof WaitForGuiAction) {
         RiptideChatField field = this.textFields.get("guiTitle");
         pattern = field == null ? "" : field.getText();
         mode = this.editorCaptureMode();
      } else if (this.targetAction instanceof WaitGuiTypeAction) {
         RiptideChatField field = this.textFields.get("title");
         pattern = field == null ? "" : field.getText();
         mode = this.editorCaptureMode();
      } else if (this.targetAction instanceof CaptureValueAction) {
         RiptideChatField field = this.textFields.get("pattern");
         pattern = field == null ? "" : field.getText();
         mode = this.editorCaptureMode();
      }

      if (mode != MacroCapturePattern.Mode.MATCH) {
         outputs.addAll(MacroCapturePattern.declaredNames(mode, pattern));
      }

      return outputs;
   }

   private MacroCapturePattern.Mode editorCaptureMode() {
      try {
         return MacroCapturePattern.Mode.valueOf(this.currentEnumValue("matchMode"));
      } catch (NullPointerException | IllegalArgumentException var2) {
         return MacroCapturePattern.Mode.MATCH;
      }
   }

   private static void collectTemplateRoots(Set<String> out, String text) {
      if (out != null && text != null && !text.isBlank()) {
         Matcher matcher = Pattern.compile("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_.-]{0,63})(?:\\|[^{}]*)?}(?!})").matcher(text);

         while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            int dot = name.indexOf(46);
            out.add(dot < 0 ? name : name.substring(0, dot));
         }
      }
   }

   private boolean isRaceBodyIndex(int index) {
      return this.raceHeaderFor(index) >= 0;
   }

   private boolean sameRaceBody(int left, int right) {
      int leftHeader = this.raceHeaderFor(left);
      return leftHeader >= 0 && leftHeader == this.raceHeaderFor(right);
   }

   private int raceHeaderFor(int index) {
      if (this.variableMacro != null && this.variableMacro.actions != null && index >= 0) {
         for (int i = 0; i < Math.min(index, this.variableMacro.actions.size()); i++) {
            MacroAction action = this.variableMacro.actions.get(i);
            if (action instanceof RaceAction race) {
               int end = i + Math.max(0, race.bodyCount);
               if (index > i && index <= end) {
                  return i;
               }
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private void enterCaptureMode() {
      this.clearCaptureToasts();
      this.captureHiddenOverlays = new ArrayList<>();

      for (IRiptideOverlay o : RiptideOverlayManager.get().getOverlays()) {
         if (o != this && o.isVisible()) {
            o.saveLayout();
            this.captureHiddenOverlays.add(o);
            o.setVisible(false);
         }
      }

      this.saveLayout();
      this.clearTextFieldFocus();
      this.restoreVisibleAfterCapture = this.visible;
      this.visible = false;
      RiptideSharedState.get().setCaptureMode(true);
   }

   private void toggleItemSlotCapture(String key) {
      if (this.guardWorldCaptureAction()) {
         this.captureSession.toggleItemSlotCapture(key, this::enterItemSlotCaptureMode, () -> this.exitCaptureMode(false, false));
      }
   }

   private void enterItemSlotCaptureMode() {
      Screen current = MC.gui.screen();
      boolean hasSlotScreen = current instanceof AbstractContainerScreen;
      this.autoOpenedInventoryForCapture = false;
      this.enterCaptureMode();
      if (!hasSlotScreen) {
         this.screenBeforeCapture = current;
         this.autoOpenedInventoryForCapture = true;
         MC.execute(() -> MC.gui.setScreen(new InventoryScreen(MC.player)));
      }
   }

   private void restoreCaptureHiddenOverlays() {
      if (this.captureHiddenOverlays != null) {
         RiptideOverlayManager manager = RiptideOverlayManager.get();

         for (IRiptideOverlay overlay : this.captureHiddenOverlays) {
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.saveLayout();
         }

         this.captureHiddenOverlays = null;
      }
   }

   private void clearCaptureCallbacks(boolean cancelGBreak) {
      RiptideSharedState state = RiptideSharedState.get();
      state.setCaptureCancelCallback(null);
      state.setBlockCaptureCallback(null);
      state.setDirectionalBlockCaptureCallback(null);
      state.setEntityCaptureCallback(null);
      state.setAttackCaptureCallback(null);
      state.setEntityCaptureSpecific(false);
      if (cancelGBreak && state.isGBreakCapturing()) {
         state.cancelGBreakCapture();
      }
   }

   private void exitCaptureMode(boolean reopenInventory, boolean closeCurrentScreen) {
      this.restoreCaptureHiddenOverlays();
      this.clearCaptureCallbacks(false);
      RiptideSharedState.get().setPlaceCaptureActive(false);
      RiptideSharedState.get().setCaptureMode(false);
      this.visible = this.restoreVisibleAfterCapture;
      this.restoreVisibleAfterCapture = false;
      this.hitRegions.clear();
      this.scrollDragRegions.clear();
      RiptideOverlayManager.get().bringToFront(this);
      if (this.autoOpenedInventoryForCapture) {
         Screen restore = this.screenBeforeCapture;
         MC.execute(() -> {
            if (MC.gui.screen() instanceof InventoryScreen) {
               if (restore != null) {
                  MC.gui.setScreen(restore);
               }
            } else if (restore == null && MC.gui.screen() == null && MC.player != null) {
               MC.gui.setScreen(new InventoryScreen(MC.player));
            } else if (restore != null && MC.gui.screen() == null) {
               MC.gui.setScreen(restore);
            }
         });
         this.autoOpenedInventoryForCapture = false;
      } else if (reopenInventory && this.screenBeforeCapture != null) {
         MC.execute(() -> {
            if (MC.gui.screen() == null) {
               MC.gui.setScreen(this.screenBeforeCapture);
            }
         });
      } else if (reopenInventory && MC.player != null) {
         MC.execute(() -> {
            if (MC.gui.screen() == null) {
               MC.gui.setScreen(new InventoryScreen(MC.player));
            }
         });
      }

      if (closeCurrentScreen) {
         MC.execute(() -> {
            if (MC.gui.screen() != null) {
               MC.gui.setScreen(null);
            }
         });
      }

      this.screenBeforeCapture = null;
      this.refreshInteractiveLayout();
   }

   private void closeEditor(boolean save) {
      this.restoreCaptureHiddenOverlays();
      this.restoreVisibleAfterCapture = false;
      this.clearCaptureCallbacks(true);
      RiptideSharedState.get().setCaptureMode(false);
      this.clearCaptureToasts();
      if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
         RiptideInstaBreakRenderer.clear();
      }

      if (save && this.targetAction != null) {
         if (this.onPreSave != null) {
            this.onPreSave.run();
         }

         this.flushToWorkingTag();
         this.targetAction.fromTag(this.workingTag);
         MacroDynamicBindings.load(this.targetAction, this.workingTag);
         if (this.onSaveCallback != null) {
            this.onSaveCallback.run();
         }
      }

      this.packetSelectorOverlay.close();
      this.raceStepSelectorOverlay.close();
      this.visible = false;
      this.targetAction = null;
      this.multiEditCount = 1;
      this.standalonePayloadEditor = false;
      this.payloadAction = null;
      this.pendingPayloadEdit = null;
      this.payloadBuildCache = null;
      this.workingTag = null;
      this.schema = null;
      this.itemAction = null;
      this.captureSession.clearItemSlotCapture();
      if (this.packetClickCapturePendingKey != null) {
         RiptideContainerHold.clearPendingCapture();
         this.packetClickCapturePendingKey = null;
      }

      this.enumDropdowns.clear();
      this.enumDropdownCache.clear();
      this.craftEntries = null;
      this.craftAllRecipes = null;
      this.craftFilteredRecipes = null;
      this.craftSelectedRecipe = null;
      this.craftRecipeListBounds = null;
      this.dropAction = null;
      this.editorItemFields.clear();
      this.editorItemLists.clear();
      this.lanStepEntries = null;
      this.toggleModuleEntries = null;
      this.onPreSave = null;
      this.onSaveCallback = null;
      this.screenBeforeGBreak = null;
      this.screenBeforeCapture = null;
   }

   private void clearCaptureToasts() {
      this.captureToasts.clear();
   }

   private void showCaptureToast(String message, int accentColor) {
      this.captureToasts.show(message, accentColor);
   }

   public boolean hasAbstractContainerScreenCaptureToasts() {
      return this.captureToasts.hasVisibleToasts();
   }

   public void renderAbstractContainerScreenCaptureToasts(GuiGraphicsExtractor context, int anchorX, int anchorY, int anchorWidth) {
      this.captureToasts.render(UiContexts.overlay(context, this.textRenderer, anchorX, anchorY), anchorX, anchorY, anchorWidth);
   }

   private void flushToWorkingTag() {
      if (this.workingTag != null) {
         if (this.itemAction != null) {
            if (this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()) {
               this.applyItemEntryEditor();
            }

            for (int i = 0; i < this.itemAction.itemNames.size(); i++) {
               RiptideChatField tf = this.textFields.get("item_times_" + i);
               if (tf != null) {
                  try {
                     while (this.itemAction.itemTimes.size() <= i) {
                        this.itemAction.itemTimes.add(1);
                     }

                     this.itemAction.itemTimes.set(i, Math.max(1, Integer.parseInt(tf.getText().strip())));
                  } catch (NumberFormatException var20) {
                  }
               }
            }

            this.itemAction.waitForGuiBefore = this.toggleStates.getOrDefault("item_waitForGuiBefore", false);
            this.itemAction.waitForGuiAfter = this.toggleStates.getOrDefault("item_waitForGuiAfter", false);
            this.itemAction.waitForItem = this.toggleStates.getOrDefault("item_waitForItem", false);
            this.itemAction.useCursorItemForPickupAll = this.toggleStates.getOrDefault("item_useCursorItemForPickupAll", true);
            this.itemAction.useClickDelay = this.toggleStates.getOrDefault("item_useClickDelay", false);
            RiptideChatField delayF = this.textFields.get("item_clickDelayMs");
            if (delayF != null) {
               try {
                  this.itemAction.clickDelayMs = Math.max(0, Integer.parseInt(delayF.getText().strip()));
               } catch (NumberFormatException var10) {
               }
            }

            RiptideChatField guiF = this.textFields.get("item_guiName");
            if (guiF != null) {
               this.itemAction.guiName = guiF.getText();
            }

            if (!this.itemAction.waitForGuiBefore && !this.itemAction.waitForGuiAfter) {
               this.itemAction.guiName = "";
            }

            if (!this.itemAction.itemNames.isEmpty()) {
               this.itemAction.useSlot = false;
               this.itemAction.targetSlot = -1;
               this.itemAction.actionIndex = 0;
               this.itemAction.button = 0;
               this.itemAction.times = 1;
            }

            this.workingTag = this.itemAction.toTag();
         } else if (this.craftEntries != null) {
            for (int ix = 0; ix < this.craftEntries.size(); ix++) {
               CraftAction.CraftEntry entry = this.craftEntries.get(ix);
               RiptideChatField af = this.textFields.get("craft_amount_" + ix);
               if (af != null) {
                  try {
                     entry.amount = Math.max(1, Integer.parseInt(af.getText().strip()));
                  } catch (NumberFormatException var11) {
                  }
               }

               entry.useMaxAmount = this.toggleStates.getOrDefault("craft_useMax_" + ix, false);
            }

            ListTag entryTags = new ListTag();

            for (CraftAction.CraftEntry entry : this.craftEntries) {
               if (entry.hasRecipe()) {
                  entryTags.add(entry.toTag());
               }
            }

            this.workingTag.put("entries", entryTags);
         } else if (this.dropAction != null) {
            if (this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()) {
               this.applyDropEntryEditor();
            }

            for (int ix = 0; ix < this.dropAction.itemNames.size(); ix++) {
               RiptideChatField f = this.textFields.get("drop_count_" + ix);
               if (f != null) {
                  try {
                     this.dropAction.itemCounts.set(ix, Math.max(0, Integer.parseInt(f.getText().strip())));
                  } catch (NumberFormatException var13) {
                  }
               }
            }

            while (this.dropAction.itemCounts.size() < this.dropAction.itemNames.size()) {
               this.dropAction.itemCounts.add(this.dropAction.getItemCount(this.dropAction.itemCounts.size()));
            }

            RiptideChatField cntF = this.textFields.get("drop_globalCount");
            if (cntF != null) {
               try {
                  this.dropAction.dropCount = Math.max(1, Integer.parseInt(cntF.getText().strip()));
               } catch (NumberFormatException var12) {
               }
            }

            DropAction.DropMode[] modes = DropAction.DropMode.values();
            this.dropAction.mode = modes[Math.min(this.enumIndices.getOrDefault("drop_mode", 0), modes.length - 1)];
            this.dropAction.waitForGuiBefore = this.toggleStates.getOrDefault("drop_waitForGuiBefore", false);
            this.dropAction.waitForGuiAfter = this.toggleStates.getOrDefault("drop_waitForGuiAfter", false);
            this.dropAction.useHandlerSlots = true;
            RiptideChatField guiFx = this.textFields.get("drop_guiName");
            if (guiFx != null) {
               this.dropAction.guiName = guiFx.getText();
            }

            if (!this.dropAction.waitForGuiBefore && !this.dropAction.waitForGuiAfter) {
               this.dropAction.guiName = "";
            }

            this.workingTag = this.dropAction.toTag();
            this.workingTag.putString("packetOrder", this.currentEnumValue("packetOrder"));
         } else if (this.payloadAction != null) {
            this.payloadAction = this.buildPayloadActionFromEditor();
            this.workingTag = this.payloadAction.toTag();
         } else {
            if (this.lanStepEntries != null) {
               for (int ixx = 0; ixx < this.lanStepEntries.size(); ixx++) {
                  WaitForLanStepAction.LanStepEntry e = this.lanStepEntries.get(ixx);
                  RiptideChatField uf = this.textFields.get("lan_user_" + ixx);
                  if (uf != null) {
                     e.username = uf.getText();
                  }

                  RiptideChatField sf = this.textFields.get("lan_step_" + ixx);
                  if (sf != null) {
                     try {
                        e.step = Math.max(1, Integer.parseInt(sf.getText().strip()));
                     } catch (NumberFormatException var19) {
                     }
                  }
               }

               ListTag entryList = new ListTag();

               for (WaitForLanStepAction.LanStepEntry ex : this.lanStepEntries) {
                  entryList.add(ex.toTag());
               }

               this.workingTag.put("entries", entryList);
               this.workingTag.putBoolean("filterByUser", this.toggleStates.getOrDefault("lan_filterByUser", false));
               this.workingTag.putBoolean("listenDuringPreviousAction", this.toggleStates.getOrDefault("listenDuringPreviousAction", false));
               RiptideChatField dsF = this.textFields.get("lan_defaultStep");
               if (dsF != null) {
                  try {
                     this.workingTag.putInt("defaultStep", Math.max(1, Integer.parseInt(dsF.getText().strip())));
                  } catch (NumberFormatException var18) {
                  }
               }
            }

            if (this.toggleModuleEntries != null) {
               ListTag entriesTag = new ListTag();

               for (ToggleModuleAction.ModuleEntry entryx : this.toggleModuleEntries) {
                  if (entryx != null && entryx.moduleName != null && !entryx.moduleName.isBlank()) {
                     entriesTag.add(entryx.toTag());
                  }
               }

               this.workingTag.put("entries", entriesTag);
               this.workingTag.putString("moduleName", "");
               this.workingTag.putString("toggleMode", ToggleModuleAction.ToggleMode.TOGGLE.name());
            } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET) {
               List<String> targets = this.sanitizeWaitPacketTargets(this.getOrCreateWaitPacketTargets());
               ListTag packetList = new ListTag();

               for (String target : targets) {
                  packetList.add(StringTag.valueOf(target));
               }

               this.workingTag.put("packetNames", packetList);
               this.workingTag.putString("packetName", targets.isEmpty() ? "" : targets.get(0));
               this.workingTag.putBoolean("listenDuringPreviousAction", this.toggleStates.getOrDefault("listenDuringPreviousAction", false));
               RiptideChatField saveAs = this.textFields.get("saveAs");
               if (saveAs != null) {
                  this.workingTag.putString("saveAs", saveAs.getText());
               }
            } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
               this.syncWaitPacketMatchEditedRule();
               ListTag rules = new ListTag();
               if (this.waitPacketMatchRules != null) {
                  for (WaitPacketMatchAction.Rule rule : this.waitPacketMatchRules) {
                     if (rule != null && (rule.packetName != null && !rule.packetName.isBlank() || rule.fieldName != null && !rule.fieldName.isBlank())) {
                        rules.add(rule.toTag());
                     }
                  }
               }

               this.workingTag.put("rules", rules);
               this.workingTag.putBoolean("listenDuringPreviousAction", this.toggleStates.getOrDefault("listenDuringPreviousAction", false));
               RiptideChatField saveAs = this.textFields.get("saveAs");
               if (saveAs != null) {
                  this.workingTag.putString("saveAs", saveAs.getText());
               }
            } else if (this.schema != null) {
               for (FieldDef field : this.schema.fields()) {
                  String key = field.key();
                  switch (field.type()) {
                     case TOGGLE:
                        this.workingTag.putBoolean(key, this.toggleStates.getOrDefault(key, false));
                        break;
                     case NUMBER:
                     case SLOT:
                        RiptideChatField fxx = this.textFields.get(key);
                        if (fxx == null) {
                           break;
                        }

                        String raw = fxx.getText().strip();
                        if (field.dynamic() && raw.contains("{")) {
                           this.putDynamicBinding(key, raw);
                        } else {
                           this.removeDynamicBinding(key);

                           try {
                              this.workingTag.putInt(key, Integer.parseInt(raw));
                           } catch (NumberFormatException var17) {
                           }
                        }
                        break;
                     case DECIMAL:
                        RiptideChatField fx = this.textFields.get(key);
                        if (fx == null) {
                           break;
                        }

                        String raw = fx.getText().strip();
                        if (field.dynamic() && raw.contains("{")) {
                           this.putDynamicBinding(key, raw);
                        } else {
                           this.removeDynamicBinding(key);

                           try {
                              this.workingTag.putDouble(key, Double.parseDouble(raw));
                           } catch (NumberFormatException var16) {
                           }
                        }
                        break;
                     case TEXT:
                     case MACRO_SELECT:
                        RiptideChatField f = this.textFields.get(key);
                        if (f != null) {
                           this.workingTag.putString(key, f.getText());
                        }
                        break;
                     case ENUM:
                        RiptideChatField bindingField = field.dynamic() ? this.textFields.get(key) : null;
                        String binding = bindingField == null ? "" : bindingField.getText().strip();
                        if (field.dynamic() && !binding.isBlank()) {
                           this.putDynamicBinding(key, binding);
                        } else {
                           this.removeDynamicBinding(key);
                           List<String> opts = field.enumOptions();
                           if (!opts.isEmpty()) {
                              int idx = Math.min(this.enumIndices.getOrDefault(key, 0), opts.size() - 1);
                              this.workingTag.putString(key, opts.get(idx));
                           }
                        }
                        break;
                     case BLOCK_POS:
                        String[] xyzKeys = field.xyzKeys();
                        boolean dbl = field.xyzDouble();
                        int ixx = 0;

                        for (; ixx < 3; ixx++) {
                           RiptideChatField f = this.textFields.get(key + "_" + ixx);
                           if (f != null) {
                              String t = f.getText().strip();
                              if (field.dynamic() && t.contains("{")) {
                                 this.putDynamicBinding(xyzKeys[ixx], t);
                              } else {
                                 this.removeDynamicBinding(xyzKeys[ixx]);
                                 if (dbl) {
                                    try {
                                       this.workingTag.putDouble(xyzKeys[ixx], Double.parseDouble(t));
                                    } catch (NumberFormatException var15) {
                                    }
                                 } else {
                                    try {
                                       this.workingTag.putInt(xyzKeys[ixx], Integer.parseInt(t));
                                    } catch (NumberFormatException var14) {
                                    }
                                 }
                              }
                           }
                        }
                        break;
                     case STRING_LIST:
                        List<String> items = this.stringLists.getOrDefault(key, Collections.emptyList());
                        ListTag nbt = new ListTag();

                        for (String s : items) {
                           nbt.add(StringTag.valueOf(s));
                        }

                        this.workingTag.put(key, nbt);
                  }
               }

               this.rewriteStructuredEditorTargets();
               if (this.targetAction instanceof WaitsForGui && !this.hasGuiWaitEnabled()) {
                  this.workingTag.putString("guiName", "");
                  this.workingTag.putString("waitGuiName", "");
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY) {
                  String mode = this.currentEnumValue("mode");
                  if ("CLOSE".equals(mode)) {
                     this.workingTag.putBoolean("waitForGuiBefore", false);
                     this.workingTag.putBoolean("waitForGuiAfter", false);
                  }

                  this.workingTag.putString("guiName", "");
                  this.workingTag.putBoolean("sendPacket", !this.toggleStates.getOrDefault("sendPacket", false));
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
                  ListTag nbtEntries = new ListTag();
                  if (this.wscEntries != null) {
                     for (WaitForSlotChangeAction.WaitEntry ex : this.wscEntries) {
                        CompoundTag ec = new CompoundTag();
                        ItemTarget target = ex.resolvedTarget();
                        if (target.hasSlot() || target.hasIdentity()) {
                           ec.put("target", target.toTag());
                        }

                        ec.putString("mode", ex.waitMode.name());
                        ec.putInt("count", Math.max(1, ex.targetCount));
                        nbtEntries.add(ec);
                     }
                  }

                  this.workingTag.put("entries", nbtEntries);
                  this.workingTag.putBoolean("listenDuringPreviousAction", this.toggleStates.getOrDefault("listenDuringPreviousAction", false));
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.USE_ITEM) {
                  RiptideChatField slotField = this.textFields.get("slot");
                  if (slotField == null || slotField.getText() == null || slotField.getText().strip().isEmpty()) {
                     this.workingTag.remove("slot");
                  }

                  String mode = this.workingTag.getStringOr("useMode", "AUTOMATIC");
                  if ("CUSTOM_HOLD".equals(mode)) {
                     this.workingTag.putInt("useCount", 1);
                     if (this.workingTag.getIntOr("holdTicks", 0) <= 0) {
                        this.workingTag.putInt("holdTicks", 20);
                     }
                  } else if (this.workingTag.getIntOr("useCount", 0) <= 0) {
                     this.workingTag.putInt("useCount", 1);
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.NBT_BOOK) {
                  this.normalizeNbtBookWorkingTag();
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SWAP_SLOTS) {
                  boolean fromUseItemName = this.workingTag.getBooleanOr("fromUseItemName", false);
                  boolean toUseItemName = this.workingTag.getBooleanOr("toUseItemName", false);
                  if (fromUseItemName) {
                     this.workingTag.putInt("fromSlot", -1);
                  } else {
                     this.workingTag.putString("fromItemName", "");
                  }

                  if (toUseItemName) {
                     this.workingTag.putInt("toSlot", -1);
                  } else {
                     this.workingTag.putString("toItemName", "");
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.GO_TO && !this.workingTag.getBooleanOr("waitForArrival", false)) {
                  this.workingTag.putDouble("arrivalRadius", 2.0);
                  this.workingTag.putInt("timeoutMs", 60000);
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CLICK) {
                  if (!this.hasGuiWaitEnabled()) {
                     this.workingTag.putString("guiName", "");
                  }

                  if ("MIDDLE".equals(this.workingTag.getStringOr("clickType", "RIGHT"))) {
                     this.workingTag.putString("clickType", "RIGHT");
                  }

                  if (this.workingTag.getIntOr("clickCount", 0) <= 0) {
                     this.workingTag.putInt("clickCount", 1);
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.XCARRY) {
                  this.workingTag.putInt("safeClickDelayTicks", XCarryAction.clampSafeClickDelayTicks(this.workingTag.getIntOr("safeClickDelayTicks", 1)));
                  this.workingTag
                     .putBoolean(
                        "safeClickDelayAfterPickup",
                        this.toggleStates.getOrDefault("safeClickDelayAfterPickup", this.workingTag.getBooleanOr("safeClickDelayAfterPickup", true))
                     );
                  this.workingTag
                     .putBoolean(
                        "safeClickDelayBeforeReturn",
                        this.toggleStates.getOrDefault("safeClickDelayBeforeReturn", this.workingTag.getBooleanOr("safeClickDelayBeforeReturn", true))
                     );
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DISCONNECT) {
                  String mode = this.workingTag.getStringOr("mode", "DISCONNECT");
                  if ("DISCONNECT".equals(mode)) {
                     this.workingTag.putBoolean("useNextAction", false);
                  } else if (this.workingTag.getIntOr("packetCount", 0) <= 0) {
                     this.workingTag.putInt("packetCount", 200);
                  }

                  if (!"KICK_DUPE".equals(mode)) {
                     this.workingTag.putBoolean("useNextAction", false);
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.BREAK && !this.workingTag.getBooleanOr("interact", false)) {
                  this.workingTag.putBoolean("runNextSteps", false);
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CLOSE_GUI) {
                  this.workingTag.putBoolean("sendPacket", !this.toggleStates.getOrDefault("sendPacket", false));
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SAVE_GUI) {
                  this.workingTag.putBoolean("sendPacket", !this.toggleStates.getOrDefault("sendPacket", false));
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.STORE_ITEM) {
                  this.workingTag.putBoolean("closeSendPkt", !this.toggleStates.getOrDefault("closeSendPkt", false));
               }

               if (this.targetAction != null
                  && (this.targetAction.getType() == MacroActionType.OPEN_CONTAINER || this.targetAction.getType() == MacroActionType.INTERACT_ENTITY)) {
                  List<String> selectedTargets = this.stringLists.getOrDefault("entityTargets", Collections.emptyList());
                  String entityTarget = selectedTargets.isEmpty() ? "" : selectedTargets.get(0);
                  this.workingTag.putString("entityTarget", entityTarget);
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_CHAT) {
                  this.workingTag.putInt("fuzzyPercent", WaitForChatAction.clampFuzzyPercent(this.getWaitChatFuzzyPercent()));
                  if (!this.hasGuiWaitEnabled()) {
                     this.workingTag.putString("waitGuiName", "");
                  }
               }

               if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
                  if (!this.workingTag.getBooleanOr("manualDirection", false)) {
                     this.workingTag.remove("direction");
                  }

                  this.workingTag.putBoolean("waitForItem", this.toggleStates.getOrDefault("place_waitForItem", true));
                  this.workingTag.putBoolean("silentSwitch", this.toggleStates.getOrDefault("place_silentSwitch", false));
               }
            }
         }
      }
   }

   private void putDynamicBinding(String key, String template) {
      if (this.workingTag != null && key != null && !key.isBlank()) {
         CompoundTag bindings = this.workingTag.get("_dynamicBindings") instanceof CompoundTag existing ? existing : new CompoundTag();
         bindings.putString(key, template == null ? "" : template);
         this.workingTag.put("_dynamicBindings", bindings);
      }
   }

   private void removeDynamicBinding(String key) {
      if (this.workingTag != null && key != null) {
         if (this.workingTag.get("_dynamicBindings") instanceof CompoundTag bindings) {
            bindings.remove(key);
            if (bindings.isEmpty()) {
               this.workingTag.remove("_dynamicBindings");
            }
         }
      }
   }

   private void normalizeNbtBookWorkingTag() {
      if (this.workingTag != null) {
         int pages = this.workingTag.getIntOr("pages", 100);
         if (pages <= 0) {
            this.workingTag.putInt("pages", 100);
         }

         int chars = this.workingTag.getIntOr("characters", 1024);
         if (chars <= 0) {
            this.workingTag.putInt("characters", 1024);
         }

         if (!this.workingTag.contains("sign")) {
            this.workingTag.putBoolean("sign", true);
         }

         if (!this.workingTag.contains("appendCount")) {
            this.workingTag.putBoolean("appendCount", true);
         }

         String source = this.currentEnumValue("dataSource");
         if (source == null || source.isBlank()) {
            source = "Random";
         }

         this.workingTag.putString("dataSource", source);
         if ("Random".equals(source)) {
            this.workingTag.putString("customText", "");
            this.workingTag.putString("customFilePath", "");
         } else if ("Pasted".equals(source)) {
            this.workingTag.putString("customFilePath", "");
         } else if ("File".equals(source)) {
            this.workingTag.putString("customText", "");
         }
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.visible) {
         return false;
      } else if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseClicked(mx, my, button);
      } else if (this.raceStepSelectorOverlay.isVisible()) {
         return this.raceStepSelectorOverlay.mouseClicked(mx, my, button);
      } else {
         int imx = (int)mx;
         int imy = (int)my;
         if (!this.enumDropdowns.isEmpty()) {
            if (CompactDropdown.mouseClicked(this.enumDropdowns, mx, my, button)) {
               RiptideOverlayManager.get().bringToFront(this);
               return true;
            }

            if (CompactDropdown.isMenuOpen(this.enumDropdowns)) {
               return true;
            }
         }

         if (this.isOverCloseButton(mx, my, this.getBounds())) {
            this.closeEditor(false);
            return true;
         } else if (this.isOverDragBar(mx, my)) {
            this.dragging = true;
            this.dragOffX = mx - this.panelX;
            this.dragOffY = my - this.panelY;
            RiptideOverlayManager.get().bringToFront(this);
            return true;
         } else if (!this.isMouseOver(mx, my)) {
            return false;
         } else if (this.collapsed) {
            return true;
         } else {
            RiptideOverlayManager.get().bringToFront(this);
            MouseButtonEvent click = new MouseButtonEvent(imx, imy, new MouseButtonInfo(button, 0));
            boolean bodyInputAllowed = this.bodyInputClip == null || this.bodyInputClip.contains(imx, imy);
            RiptideChatField focused = null;
            if (bodyInputAllowed) {
               for (Entry<String, RiptideChatField> entry : this.textFields.entrySet()) {
                  if (this.isPayloadTextFieldVisible(entry.getKey())) {
                     String baseKey = entry.getKey().replaceAll("_\\d+$", "");
                     FieldDef fld = this.getField(baseKey);
                     if ((fld == null || this.isFieldVisible(fld)) && entry.getValue().mouseClicked(click, false)) {
                        focused = entry.getValue();
                        break;
                     }
                  }
               }
            }

            if (bodyInputAllowed && focused == null) {
               for (Entry<String, RiptideChatField> entryx : this.addFields.entrySet()) {
                  FieldDef fld = this.getField(entryx.getKey());
                  if ((fld == null || this.isFieldVisible(fld)) && entryx.getValue().mouseClicked(click, false)) {
                     focused = entryx.getValue();
                     break;
                  }
               }
            }

            if (focused != null) {
               RiptideChatField ff = focused;
               this.textFields.values().forEach(f -> f.setFocused(f == ff));
               this.addFields.values().forEach(f -> f.setFocused(f == ff));
               return true;
            } else {
               this.clearTextFieldFocus();
               if (button == 0 && this.isOverWaitChatFuzzySlider(imx, imy)) {
                  this.waitChatFuzzySliderDragging = true;
                  this.updateWaitChatFuzzyPercentFromMouse(imx);
                  return true;
               } else if (button == 0 && this.isOverRotateSmoothnessSlider(imx, imy)) {
                  this.rotateSmoothnessSliderDragging = true;
                  this.updateRotateSmoothnessFromMouse(imx);
                  return true;
               } else if (button == 0 && this.isOverXCarrySafeDelaySlider(imx, imy)) {
                  this.xcarrySafeDelaySliderDragging = true;
                  this.updateXCarrySafeDelayFromMouse(imx);
                  return true;
               } else {
                  if (bodyInputAllowed) {
                     for (DirectScrollViewport vp : this.selectedScrollViewports.values()) {
                        if (vp.isScrollbarHovered(mx, my)) {
                           vp.mouseClicked(mx, my, button);
                           return true;
                        }
                     }

                     for (DirectScrollViewport vpx : this.catalogScrollViewports.values()) {
                        if (vpx.isScrollbarHovered(mx, my)) {
                           vpx.mouseClicked(mx, my, button);
                           return true;
                        }
                     }
                  }

                  for (ActionEditorOverlay.ScrollDragRegion r : this.scrollDragRegions) {
                     if (r.contains(imx, imy)) {
                        this.activeScrollDragHandler = r.handler();
                        this.activeScrollDragHandler.accept(imy);
                        return true;
                     }
                  }

                  for (ActionEditorOverlay.HitRegion rx : this.hitRegions) {
                     if (rx.contains(imx, imy) && rx.fire(mx, my, button)) {
                        this.refreshInteractiveLayout();
                        return true;
                     }
                  }

                  return true;
               }
            }
         }
      }
   }

   private boolean isPayloadTextFieldVisible(String key) {
      if ("payload_content".equals(key)) {
         return this.payloadTabIndex == 0;
      } else if ("payload_hex_view".equals(key)) {
         return this.payloadTabIndex == 1;
      } else if ("payload_utf8_view".equals(key)) {
         return this.payloadTabIndex == 2;
      } else {
         return "payload_logical_view".equals(key) ? this.payloadTabIndex == 3 : true;
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      if (this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.mouseReleased(mx, my, button)) {
         return true;
      } else if (this.raceStepSelectorOverlay.isVisible() && this.raceStepSelectorOverlay.mouseReleased(mx, my, button)) {
         return true;
      } else {
         boolean consumed = false;

         for (DirectScrollViewport vp : this.selectedScrollViewports.values()) {
            if (vp.isScrollbarDragging()) {
               vp.mouseReleased();
               consumed = true;
            }
         }

         for (DirectScrollViewport vpx : this.catalogScrollViewports.values()) {
            if (vpx.isScrollbarDragging()) {
               vpx.mouseReleased();
               consumed = true;
            }
         }

         if (this.activeScrollDragHandler != null) {
            this.activeScrollDragHandler = null;
            consumed = true;
         }

         if (this.waitChatFuzzySliderDragging) {
            this.waitChatFuzzySliderDragging = false;
            consumed = true;
         }

         if (this.rotateSmoothnessSliderDragging) {
            this.rotateSmoothnessSliderDragging = false;
            consumed = true;
         }

         if (this.xcarrySafeDelaySliderDragging) {
            this.xcarrySafeDelaySliderDragging = false;
            consumed = true;
         }

         if (this.dragging) {
            this.dragging = false;
            consumed = true;
         }

         if (CompactDropdown.mouseReleased(this.enumDropdowns)) {
            return true;
         } else {
            for (Entry<String, RiptideChatField> entry : this.textFields.entrySet()) {
               if (this.isPayloadTextFieldVisible(entry.getKey()) && entry.getValue().mouseReleased(mx, my, button)) {
                  consumed = true;
               }
            }

            for (RiptideChatField field : this.addFields.values()) {
               if (field.mouseReleased(mx, my, button)) {
                  consumed = true;
               }
            }

            return consumed;
         }
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.mouseDragged(mx, my, button, dx, dy)) {
         return true;
      } else if (this.raceStepSelectorOverlay.isVisible() && this.raceStepSelectorOverlay.mouseDragged(mx, my, button, dx, dy)) {
         return true;
      } else if (CompactDropdown.mouseDragged(this.enumDropdowns, mx, my, button)) {
         return true;
      } else if (this.waitChatFuzzySliderDragging) {
         this.updateWaitChatFuzzyPercentFromMouse((int)mx);
         return true;
      } else if (this.rotateSmoothnessSliderDragging) {
         this.updateRotateSmoothnessFromMouse((int)mx);
         return true;
      } else if (this.xcarrySafeDelaySliderDragging) {
         this.updateXCarrySafeDelayFromMouse((int)mx);
         return true;
      } else if (this.dragging) {
         this.panelX = (int)(mx - this.dragOffX);
         this.panelY = (int)(my - this.dragOffY);
         RiptideWindowLayout c = this.clampToScreen(this);
         this.panelX = c.x;
         this.panelY = c.y;
         return true;
      } else {
         for (Entry<String, RiptideChatField> entry : this.textFields.entrySet()) {
            if (this.isPayloadTextFieldVisible(entry.getKey()) && entry.getValue().mouseDragged(mx, my, button, dx, dy)) {
               return true;
            }
         }

         for (RiptideChatField field : this.addFields.values()) {
            if (field.mouseDragged(mx, my, button, dx, dy)) {
               return true;
            }
         }

         for (DirectScrollViewport vp : this.selectedScrollViewports.values()) {
            if (vp.isScrollbarDragging()) {
               vp.mouseDragged(mx, my);
               return true;
            }
         }

         for (DirectScrollViewport vpx : this.catalogScrollViewports.values()) {
            if (vpx.isScrollbarDragging()) {
               vpx.mouseDragged(mx, my);
               return true;
            }
         }

         if (this.activeScrollDragHandler != null) {
            this.activeScrollDragHandler.accept((int)my);
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amount) {
      if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseScrolled(mx, my, amount);
      } else if (this.raceStepSelectorOverlay.isVisible()) {
         return this.raceStepSelectorOverlay.mouseScrolled(mx, my, amount);
      } else if (this.visible && this.isMouseOver(mx, my)) {
         if (!this.enumDropdowns.isEmpty()
            && (CompactDropdown.isMenuOpen(this.enumDropdowns) || CompactDropdown.isInsideOpenMenu(this.enumDropdowns, mx, my))
            && CompactDropdown.mouseScrolled(this.enumDropdowns, mx, my, amount)) {
            return true;
         } else {
            boolean bodyScrollAllowed = this.bodyInputClip == null || this.bodyInputClip.contains((int)mx, (int)my);
            if (bodyScrollAllowed) {
               for (Entry<String, RiptideChatField> entry : this.textFields.entrySet()) {
                  if (this.isPayloadTextFieldVisible(entry.getKey()) && entry.getValue().mouseScrolled(mx, my, amount)) {
                     return true;
                  }
               }

               for (RiptideChatField field : this.addFields.values()) {
                  if (field.mouseScrolled(mx, my, amount)) {
                     return true;
                  }
               }

               for (Entry<String, DirectScrollViewport> entryx : this.selectedScrollViewports.entrySet()) {
                  DirectScrollViewport vp = entryx.getValue();
                  if (vp.contains(mx, my)) {
                     vp.mouseScrolled(mx, my, amount);
                     return true;
                  }
               }

               for (Entry<String, DirectScrollViewport> entryxx : this.catalogScrollViewports.entrySet()) {
                  DirectScrollViewport vp = entryxx.getValue();
                  if (vp.contains(mx, my)) {
                     vp.mouseScrolled(mx, my, amount);
                     return true;
                  }
               }
            }

            if (this.craftRecipeListBounds != null && my >= this.craftRecipeListBounds[0] && my < this.craftRecipeListBounds[0] + this.craftRecipeListBounds[1]
               )
             {
               this.craftRecipeScrollOffset = Math.max(0, this.craftRecipeScrollOffset - (int)(amount * 13.0));
               return true;
            } else {
               this.scrollOffset = Math.max(0, this.scrollOffset - (int)(amount * 12.0));
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.raceStepSelectorOverlay.isVisible()) {
         return this.raceStepSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
      } else if (!CompactDropdown.isMenuOpen(this.enumDropdowns)) {
         if (keyCode == 256) {
            if (this.cancelCaptureIfActive()) {
               return true;
            } else if (this.hasTextFieldFocused()) {
               this.clearTextFieldFocus();
               return true;
            } else {
               this.closeEditor(false);
               return true;
            }
         } else {
            KeyEvent ki = new KeyEvent(keyCode, scanCode, modifiers);

            for (RiptideChatField f : this.textFields.values()) {
               if (f.isFocused()) {
                  return f.keyPressed(ki);
               }
            }

            for (RiptideChatField fx : this.addFields.values()) {
               if (fx.isFocused()) {
                  return fx.keyPressed(ki);
               }
            }

            return false;
         }
      } else {
         if (keyCode == 256 || keyCode == 257) {
            CompactDropdown.closeOpenMenu(this.enumDropdowns);
         }

         return true;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.charTyped(chr, modifiers);
      } else if (this.raceStepSelectorOverlay.isVisible()) {
         return this.raceStepSelectorOverlay.charTyped(chr, modifiers);
      } else {
         CharacterEvent ci = new CharacterEvent(chr);

         for (RiptideChatField f : this.textFields.values()) {
            if (f.isFocused() && f.charTyped(ci)) {
               return true;
            }
         }

         for (RiptideChatField fx : this.addFields.values()) {
            if (fx.isFocused() && fx.charTyped(ci)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean isFieldVisible(FieldDef field) {
      if (field == null) {
         return false;
      } else if ("safeClickDelayTicks".equals(field.key())) {
         return this.isXCarrySafeClickTimingVisible();
      } else if ("safeClickDelayAfterPickup".equals(field.key()) || "safeClickDelayBeforeReturn".equals(field.key())) {
         return this.isXCarrySafeClickTimingVisible();
      } else if (this.isGuiNameField(field.key()) && this.targetAction instanceof WaitsForGui) {
         return this.hasGuiWaitEnabled();
      } else {
         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY) {
            String mode = this.currentEnumValue("mode");
            if ("waitForGuiBefore".equals(field.key()) || "waitForGuiAfter".equals(field.key())) {
               return "OPEN".equals(mode);
            }

            if ("sendPacket".equals(field.key())) {
               return "CLOSE".equals(mode);
            }
         }

         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_ENTITY) {
            String checkMode = this.currentEnumValue("checkMode");
            if ("centerOnPlayer".equals(field.key()) || "radius".equals(field.key()) || "mustBeLookingAt".equals(field.key())) {
               return "RADIUS".equals(checkMode) || "NEARBY".equals(checkMode) && !"mustBeLookingAt".equals(field.key());
            }

            if ("pos".equals(field.key())) {
               return ("RADIUS".equals(checkMode) || "NEARBY".equals(checkMode)) && !this.toggleStates.getOrDefault("centerOnPlayer", false);
            }
         }

         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
            String modex = this.currentEnumValue("mode");
            String openMode = this.currentEnumValue("openMode");
            boolean isDupeMode = "DUPE".equals(modex) || "DUPE_SPAM".equals(modex);
            boolean isDupeSpam = "DUPE_SPAM".equals(modex);
            if ("targetItems".equals(field.key())) {
               return isDupeMode;
            }

            if ("openCommand".equals(field.key())) {
               return isDupeMode && "COMMAND".equals(openMode);
            }

            if ("containerPos".equals(field.key())) {
               return isDupeMode && "CONTAINER".equals(openMode);
            }

            if ("spamCount".equals(field.key()) || "spamDelayMs".equals(field.key())) {
               return isDupeSpam;
            }

            if ("openMode".equals(field.key())
               || "dupeVector".equals(field.key())
               || "iterations".equals(field.key())
               || "maxTransferAttempts".equals(field.key())
               || "transferRetryDelayMs".equals(field.key())) {
               return isDupeMode;
            }
         }

         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PACKET_GATE && "DISABLE_GATE".equals(this.currentEnumValue("mode"))) {
            String key = field.key();
            return "mode".equals(key) || "gateId".equals(key) || "flushOnDisable".equals(key);
         } else {
            if (this.targetAction != null && (this.targetAction.getType() == MacroActionType.FPS || this.targetAction.getType() == MacroActionType.SPING)) {
               String endMode = this.currentEnumValue("endMode");
               boolean timeout = endMode.isEmpty() || "TIMEOUT".equals(endMode);
               boolean steps = "STEPS".equals(endMode);
               boolean useTicks = this.toggleStates.getOrDefault("useTicks", false);
               String key = field.key();
               if ("stepCount".equals(key)) {
                  return steps;
               }

               if ("useTicks".equals(key)) {
                  return timeout;
               }

               if ("durationMs".equals(key)) {
                  return timeout && !useTicks;
               }

               if ("durationTicks".equals(key)) {
                  return timeout && useTicks;
               }

               if ("continueNextActions".equals(key)) {
                  return timeout;
               }
            }

            if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
               String keyx = field.key();
               if ("listFilterText".equals(keyx) || "listStripPrefix".equals(keyx) || "listPickPosition".equals(keyx)) {
                  String source = this.currentEnumValue("source");
                  if (!"COMMAND_AUTOFILL".equals(source) && !"TABLIST".equals(source)) {
                     return false;
                  }

                  String filter = this.currentEnumValue("listFilter");
                  if ("listFilterText".equals(keyx)) {
                     return !"NONE".equals(filter);
                  }

                  if (!"listStripPrefix".equals(keyx)) {
                     return "POSITION".equals(this.currentEnumValue("listSelection"));
                  }

                  return "PREFIX".equals(filter) || "SUFFIX".equals(filter);
               }
            }

            if (!field.hasShowWhen()) {
               return true;
            } else {
               boolean cond;
               if (field.showWhenValue() != null && !field.showWhenValue().isEmpty()) {
                  String currentValue = this.currentEnumValue(field.showWhenKey());
                  cond = false;

                  for (String allowed : field.showWhenValue().split("\\|")) {
                     if (allowed.equals(currentValue)) {
                        cond = true;
                        break;
                     }
                  }
               } else {
                  cond = this.toggleStates.getOrDefault(field.showWhenKey(), false);
               }

               boolean visible = field.showWhenInverted() ? !cond : cond;
               if (visible && !field.showWhenInverted()) {
                  FieldDef dep = this.getField(field.showWhenKey());
                  if (dep != null && !this.isFieldVisible(dep)) {
                     visible = false;
                  }
               }

               return visible;
            }
         }
      }
   }

   private boolean isXCarrySafeClickTimingVisible() {
      return this.targetAction != null
         && this.targetAction.getType() == MacroActionType.XCARRY
         && "PUT_IN".equals(this.currentEnumValue("mode"))
         && "SAFE_CLICK".equals(this.currentEnumValue("transferMode"));
   }

   private boolean isGuiNameField(String key) {
      return "guiName".equals(key) || "waitGuiName".equals(key);
   }

   private boolean hasGuiWaitEnabled() {
      return this.toggleStates.getOrDefault("waitForGuiBefore", false)
         || this.toggleStates.getOrDefault("waitForGuiAfter", false)
         || this.toggleStates.getOrDefault("waitForGui", false);
   }

   private String currentEnumValue(String key) {
      if (this.schema == null) {
         return "";
      } else {
         for (FieldDef field : this.schema.fields()) {
            if (field.key().equals(key) && field.type() == FieldType.ENUM) {
               List<String> opts = field.enumOptions();
               if (opts.isEmpty()) {
                  return "";
               }

               int idx = Math.min(this.enumIndices.getOrDefault(key, 0), opts.size() - 1);
               return opts.get(idx);
            }
         }

         return "";
      }
   }

   private void prepareWorkingTagForEditor(MacroAction action) {
      if (action != null && this.workingTag != null) {
         if (action instanceof SelectSlotAction selectSlotAction) {
            this.primeStructuredField("itemName", selectSlotAction.itemTarget, selectSlotAction.itemName, null);
         }

         if (action instanceof UseItemAction useItemAction) {
            this.primeStructuredField("itemName", useItemAction.itemTarget, useItemAction.itemName, "slot");
            if (useItemAction.slot >= 0) {
               this.workingTag.putInt("slot", useItemAction.slot);
            }
         }

         if (action instanceof WaitForCooldownAction waitForCooldownAction) {
            this.primeStructuredField("itemName", waitForCooldownAction.itemTarget, waitForCooldownAction.itemName, null);
         }

         if (action instanceof CloseGuiAction closeGuiAction) {
            this.primeStructuredField("itemName", closeGuiAction.itemTarget, closeGuiAction.itemName, "targetSlot");
         }

         if (action instanceof SwapSlotsAction swapSlotsAction) {
            this.primeStructuredField("fromItemName", swapSlotsAction.fromItemTarget, swapSlotsAction.fromItemName, null);
            this.primeStructuredField("toItemName", swapSlotsAction.toItemTarget, swapSlotsAction.toItemName, null);
         }

         if (action instanceof StoreItemAction storeItemAction) {
            this.primeStructuredList("targetItems", storeItemAction.itemTargets, storeItemAction.targetItems);
         }

         if (action instanceof XCarryAction xCarryAction) {
            this.primeStructuredList("entries", xCarryAction.entryTargets, xCarryAction.entries);
            this.workingTag.putInt("safeClickDelayTicks", XCarryAction.clampSafeClickDelayTicks(xCarryAction.safeClickDelayTicks));
            this.workingTag.putBoolean("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
            this.workingTag.putBoolean("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
            ListTag destinations = new ListTag();
            xCarryAction.resizeDestinations(xCarryAction.entries.size());
            xCarryAction.resizeAmountSettings(xCarryAction.entries.size());
            ListTag amountModes = new ListTag();
            ListTag amounts = new ListTag();

            for (int i = 0; i < xCarryAction.entries.size(); i++) {
               destinations.add(StringTag.valueOf(String.valueOf(xCarryAction.destinationFor(i))));
               amountModes.add(StringTag.valueOf(xCarryAction.amountModeFor(i).name()));
               amounts.add(StringTag.valueOf(String.valueOf(xCarryAction.amountFor(i))));
            }

            this.workingTag.put("entryDestinations", destinations);
            this.workingTag.put("entryAmountModes", amountModes);
            this.workingTag.put("entryAmounts", amounts);
         }

         if (action instanceof InventoryAuditAction inventoryAuditAction) {
            this.primeStructuredList("targetItems", inventoryAuditAction.itemTargets, inventoryAuditAction.targetItems);
         }

         if (action instanceof PlaceAction placeAction) {
            this.primeStructuredField("itemName", placeAction.itemTarget, placeAction.itemName, null);
            this.normalizePlaceWorkingTag();
         }
      }
   }

   private void normalizePlaceWorkingTag() {
      if (this.workingTag != null) {
         this.workingTag.remove("slot");
         if (!this.workingTag.getBooleanOr("manualDirection", false)) {
            this.workingTag.remove("direction");
         }
      }
   }

   private ItemTarget buildPlaceItemTarget() {
      RiptideChatField nameF = this.textFields.get("itemName");
      RiptideChatField slotF = this.textFields.get("place_itemSlot");
      return this.buildEntryTargetFromEditor(nameF == null ? "" : nameF.getText(), slotF == null ? "" : slotF.getText(), this.editorItemFields.get("itemName"));
   }

   private void syncPlaceItemEditorFields() {
      if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
         ItemTarget target = this.buildPlaceItemTarget();
         if (!target.hasSlot() && !target.hasIdentity()) {
            this.editorItemFields.remove("itemName");
         } else {
            this.editorItemFields.put("itemName", target.copy());
         }
      }
   }

   private void primeStructuredField(String key, ItemTarget source, String legacyValue, String slotKey) {
      ItemTarget resolved = this.resolveEditorTarget(source, legacyValue);
      if (resolved.hasSlot() || resolved.hasIdentity()) {
         this.editorItemFields.put(key, resolved.copy());
         this.workingTag.putString(key, resolved.editorText());
         if (slotKey != null && resolved.hasSlot()) {
            this.workingTag.putInt(slotKey, resolved.slot);
         }
      }
   }

   private void refreshItemTextDisplayProviders() {
      this.bindStructuredItemFieldDisplay("itemName");
      this.bindStructuredItemFieldDisplay("fromItemName");
      this.bindStructuredItemFieldDisplay("toItemName");
      this.bindTransientItemEditorDisplay("_item_add");
      this.bindTransientItemEditorDisplay("_drop_add");
      this.bindTransientItemEditorDisplay("_wsc_add");
      this.bindTransientItemEditorDisplay("targetItems");
   }

   private void bindStructuredItemFieldDisplay(String key) {
      RiptideChatField field = this.textFields.get(key);
      if (field != null) {
         field.setDisplayTextProvider(value -> this.resolveStructuredItemFieldDisplay(key, value));
      }
   }

   private void bindTransientItemEditorDisplay(String key) {
      RiptideChatField field = this.addFields.get(key);
      if (field != null) {
         field.setDisplayTextProvider(value -> this.resolveTransientItemEditorDisplay(key, value));
      }
   }

   private Component resolveStructuredItemFieldDisplay(String key, String value) {
      return this.richItemFieldDisplay(this.editorItemFields.get(key), value);
   }

   private Component resolveTransientItemEditorDisplay(String key, String value) {
      return switch (key) {
         case "_item_add" -> this.richItemFieldDisplay(
            this.itemAction != null && this.itemEditIndex >= 0 && this.itemEditIndex < this.itemAction.itemNames.size()
               ? this.targetAt(this.itemAction.itemTargets, this.itemAction.itemNames, this.itemEditIndex)
               : null,
            value
         );
         case "_drop_add" -> this.richItemFieldDisplay(
            this.dropAction != null && this.dropEditIndex >= 0 && this.dropEditIndex < this.dropAction.itemNames.size()
               ? this.targetAt(this.dropAction.itemTargets, this.dropAction.itemNames, this.dropEditIndex)
               : null,
            value
         );
         case "_wsc_add" -> this.richItemFieldDisplay(
            this.wscEntries != null && this.wscEditIndex >= 0 && this.wscEditIndex < this.wscEntries.size()
               ? this.wscEntries.get(this.wscEditIndex).resolvedTarget()
               : null,
            value
         );
         case "targetItems" -> this.richItemFieldDisplay(this.resolveSelectedTargetItemsEditorTarget(), value);
         default -> null;
      };
   }

   private ItemTarget resolveSelectedTargetItemsEditorTarget() {
      int storeIndex = this.stringListEditIndex.getOrDefault("store_items_selected", -1);
      int auditIndex = this.stringListEditIndex.getOrDefault("audit_items_selected", -1);
      int index = storeIndex >= 0 ? storeIndex : auditIndex;
      if (index < 0) {
         return null;
      } else {
         List<ItemTarget> targets = this.buildStructuredListTargets("targetItems");
         if (index >= 0 && index < targets.size()) {
            ItemTarget target = targets.get(index);
            if (target != null && (target.hasSlot() || target.hasIdentity())) {
               return target;
            }
         }

         return null;
      }
   }

   private Component richItemFieldDisplay(ItemTarget target, String value) {
      if (target == null) {
         return null;
      } else {
         Component rich = target.editorComponent(value);
         if (rich == null) {
            return null;
         } else {
            String safeValue = value == null ? "" : value;
            return safeValue.equals(rich.getString()) ? rich.copy() : null;
         }
      }
   }

   private void primeStructuredList(String key, List<ItemTarget> source, List<String> legacyValues) {
      List<ItemTarget> resolved = this.copyEditorTargets(source, legacyValues);
      if (!resolved.isEmpty()) {
         this.editorItemLists.put(key, ItemTarget.copyList(resolved));
         ListTag listTag = new ListTag();

         for (ItemTarget target : resolved) {
            if (target != null) {
               String entry = target.toLegacyEntry();
               if (!entry.isBlank()) {
                  listTag.add(StringTag.valueOf(entry));
               }
            }
         }

         this.workingTag.put(key, listTag);
      }
   }

   private ItemTarget resolveEditorTarget(ItemTarget source, String legacyValue) {
      return source == null || !source.hasSlot() && !source.hasIdentity() ? ItemTarget.fromLegacyEntry(legacyValue) : source.copy();
   }

   private List<ItemTarget> copyEditorTargets(List<ItemTarget> source, List<String> legacyValues) {
      List<ItemTarget> resolved = ItemTarget.copyList(source);
      if (!resolved.isEmpty()) {
         return resolved;
      } else {
         List<ItemTarget> parsed = new ArrayList<>();
         if (legacyValues == null) {
            return parsed;
         } else {
            for (String legacyValue : legacyValues) {
               ItemTarget target = ItemTarget.fromLegacyEntry(legacyValue);
               if (target.hasSlot() || target.hasIdentity()) {
                  parsed.add(target);
               }
            }

            return parsed;
         }
      }
   }

   private ItemTarget buildStructuredFieldTarget(String key, String slotKey) {
      RiptideChatField valueField = this.textFields.get(key);
      String text = valueField == null ? "" : valueField.getText().strip();
      ItemTarget previous = this.editorItemFields.get(key);
      int slot = -1;
      boolean slotDriven = false;
      if (slotKey != null) {
         RiptideChatField slotField = this.textFields.get(slotKey);
         slot = slotField == null ? -1 : this.parseHandlerSlotField(slotField.getText(), -1);
         slotDriven = true;
      } else if (previous != null && previous.hasSlot()) {
         slot = previous.slot;
      }

      if (!text.isEmpty() || slotDriven && slot >= 0) {
         if (previous != null && text.equals(previous.editorText())) {
            ItemTarget preserved = previous.copy();
            if (slotDriven) {
               preserved.slot = slot;
            }

            return preserved;
         } else if (previous != null && previous.hasRichText() && !text.isEmpty()) {
            ItemTarget preserved = previous.withEditedRichDisplay(text);
            if (slot >= 0) {
               preserved.slot = slot;
            } else if (slotDriven) {
               preserved.slot = -1;
            }

            return preserved;
         } else {
            String raw;
            if (slot >= 0 && !text.isEmpty()) {
               raw = "#" + slot + "|" + text;
            } else if (slot >= 0) {
               raw = "#" + slot;
            } else {
               raw = text;
            }

            return ItemTarget.fromLegacyEntry(raw);
         }
      } else {
         return new ItemTarget();
      }
   }

   private List<ItemTarget> buildStructuredListTargets(String key) {
      List<String> values = this.stringLists.getOrDefault(key, Collections.emptyList());
      List<ItemTarget> previous = this.editorItemLists.getOrDefault(key, Collections.emptyList());
      boolean[] used = new boolean[previous.size()];
      List<ItemTarget> rebuilt = new ArrayList<>();

      for (String value : values) {
         String entry = value == null ? "" : value.strip();
         if (!entry.isEmpty()) {
            ItemTarget preserved = null;

            for (int i = 0; i < previous.size(); i++) {
               ItemTarget candidate = previous.get(i);
               if (!used[i] && candidate != null && entry.equals(candidate.toLegacyEntry())) {
                  preserved = candidate.copy();
                  used[i] = true;
                  break;
               }
            }

            if (preserved == null) {
               preserved = ItemTarget.fromLegacyEntry(entry);
            }

            if (this.isXCarryListKey(key) && preserved.hasIdentity()) {
               preserved.slot = -1;
            }

            if (preserved.hasSlot() || preserved.hasIdentity()) {
               rebuilt.add(preserved);
            }
         }
      }

      this.editorItemLists.put(key, ItemTarget.copyList(rebuilt));
      return rebuilt;
   }

   private void rewriteStructuredEditorTargets() {
      if (this.targetAction != null && this.workingTag != null) {
         if (this.targetAction instanceof SelectSlotAction) {
            this.writeStructuredField("itemName", this.buildStructuredFieldTarget("itemName", null));
         }

         if (this.targetAction instanceof UseItemAction) {
            this.writeStructuredField("itemName", this.buildStructuredFieldTarget("itemName", "slot"));
         }

         if (this.targetAction instanceof WaitForCooldownAction) {
            this.writeStructuredField("itemName", this.buildStructuredFieldTarget("itemName", null));
         }

         if (this.targetAction instanceof CloseGuiAction) {
            this.writeStructuredField("itemName", this.buildStructuredFieldTarget("itemName", "targetSlot"));
         }

         if (this.targetAction instanceof SwapSlotsAction) {
            this.writeStructuredField("fromItemName", this.buildStructuredFieldTarget("fromItemName", null));
            this.writeStructuredField("toItemName", this.buildStructuredFieldTarget("toItemName", null));
         }

         if (this.targetAction instanceof PlaceAction) {
            this.writeStructuredField("itemName", this.buildPlaceItemTarget());
            this.workingTag.remove("slot");
         }

         if (this.targetAction instanceof StoreItemAction) {
            this.writeStructuredList("targetItems", this.buildStructuredListTargets("targetItems"));
         }

         if (this.targetAction instanceof XCarryAction) {
            this.writeStructuredList("entries", this.buildStructuredListTargets("entries"));
            int count = this.stringLists.getOrDefault("entries", Collections.emptyList()).size();
            this.syncXCarryDestinationCount(count);
            this.syncXCarryAmountCount(count);
         }

         if (this.targetAction instanceof InventoryAuditAction) {
            this.writeStructuredList("targetItems", this.buildStructuredListTargets("targetItems"));
         }
      }
   }

   private void writeStructuredField(String key, ItemTarget target) {
      if (target != null && (target.hasSlot() || target.hasIdentity())) {
         this.workingTag.put(key, target.toTag());
         this.editorItemFields.put(key, target.copy());
      } else {
         this.workingTag.remove(key);
         this.editorItemFields.remove(key);
      }
   }

   private void writeStructuredList(String key, List<ItemTarget> targets) {
      this.workingTag.put(key, ItemTarget.toTagList(targets));
   }

   private void refreshInteractiveLayout() {
      this.hitRegions.clear();
      this.scrollDragRegions.clear();
      int neededH = 20 + this.computeContentH() + 32 + 4;
      int minH = this.currentMinPanelHeight();
      int maxH = Math.max(minH, RiptideUiScale.getVirtualScreenHeight() * 4 / 5);
      this.panelH = Math.max(minH, Math.min(maxH, neededH));
      RiptideWindowLayout clamped = this.clampToScreen(
         this, new RiptideWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed)
      );
      this.panelX = clamped.x;
      this.panelY = clamped.y;
      this.panelW = clamped.width;
      this.panelH = clamped.height;
   }

   private void toggleWscEditSelection(int index) {
      if (this.wscEditIndex == index) {
         this.wscEditIndex = -1;
         this.clearWscEditorFields();
      } else {
         this.wscEditIndex = index;
         if (this.wscEntries != null && index < this.wscEntries.size()) {
            this.syncWscEditorFromEntry(this.wscEntries.get(index));
         }
      }
   }

   private void syncWscEditorFromEntry(WaitForSlotChangeAction.WaitEntry e) {
      RiptideChatField addF = this.addFields.get("_wsc_add");
      RiptideChatField slotF = this.textFields.get("wsc_slot");
      RiptideChatField countF = this.textFields.get("wsc_count");
      if (addF != null && slotF != null) {
         this.suppressWscLiveUpdate = true;
         ItemTarget target = e.resolvedTarget();
         addF.setText(target.editorText());
         slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
         if (countF != null) {
            countF.setText(String.valueOf(Math.max(1, e.targetCount)));
         }

         this.suppressWscLiveUpdate = false;
      }
   }

   private void clearWscEditorFields() {
      RiptideChatField addF = this.addFields.get("_wsc_add");
      RiptideChatField slotF = this.textFields.get("wsc_slot");
      RiptideChatField countF = this.textFields.get("wsc_count");
      this.suppressWscLiveUpdate = true;
      if (addF != null) {
         addF.setText("");
      }

      if (slotF != null) {
         slotF.setText("");
      }

      if (countF != null) {
         countF.setText("");
      }

      this.suppressWscLiveUpdate = false;
   }

   private void handleWscEntryEditorChanged() {
      if (!this.suppressWscLiveUpdate && this.wscEntries != null) {
         if (this.wscEditIndex >= 0 && this.wscEditIndex < this.wscEntries.size()) {
            RiptideChatField addF = this.addFields.get("_wsc_add");
            RiptideChatField slotF = this.textFields.get("wsc_slot");
            if (addF != null && slotF != null) {
               String nameText = addF.getText().strip();
               String slotText = slotF.getText().strip();
               WaitForSlotChangeAction.WaitEntry entry = this.wscEntries.get(this.wscEditIndex);
               ItemTarget target = this.buildEntryTargetFromEditor(nameText, slotText, entry.resolvedTarget());
               String rawTarget = target.toLegacyEntry();
               String norm = rawTarget.isEmpty() ? "" : StoreItemAction.normalizeTargetEntry(rawTarget);
               if (norm == null) {
                  norm = "";
               }

               if (!this.wscTargetExistsOtherThan(norm, this.wscEditIndex)) {
                  entry.target = norm;
                  entry.itemTarget = target;
               }
            }
         }
      }
   }

   private void handleWscCountChanged() {
      if (!this.suppressWscLiveUpdate && this.wscEntries != null) {
         RiptideChatField countF = this.textFields.get("wsc_count");
         if (countF != null) {
            int count;
            try {
               count = Integer.parseInt(countF.getText().strip());
            } catch (NumberFormatException var4) {
               return;
            }

            count = Math.max(1, count);
            if (this.wscEditIndex >= 0 && this.wscEditIndex < this.wscEntries.size()) {
               this.wscEntries.get(this.wscEditIndex).targetCount = count;
            } else {
               this.wscAddCount = count;
            }
         }
      }
   }

   private void applyWscAddEntry(RiptideChatField addF, RiptideChatField slotF) {
      if (this.wscEntries != null) {
         String nameText = addF.getText().strip();
         String slotText = slotF.getText().strip();
         ItemTarget target = this.buildEntryTargetFromEditor(nameText, slotText, null);
         String rawTarget = target.toLegacyEntry();
         String norm = rawTarget.isEmpty() ? "" : StoreItemAction.normalizeTargetEntry(rawTarget);
         if (norm == null) {
            norm = "";
         }

         if (!this.wscTargetExists(norm)) {
            WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry(norm, this.wscAddMode, this.wscAddCount);
            entry.itemTarget = target;
            entry.target = norm;
            this.wscEntries.add(entry);
            addF.setText("");
            slotF.setText("");
         }
      }
   }

   private boolean wscTargetExists(String normTarget) {
      if (this.wscEntries == null) {
         return false;
      } else {
         for (WaitForSlotChangeAction.WaitEntry e : this.wscEntries) {
            if (e.target.equals(normTarget)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean wscTargetExistsOtherThan(String normTarget, int ignoreIdx) {
      if (this.wscEntries == null) {
         return false;
      } else {
         for (int i = 0; i < this.wscEntries.size(); i++) {
            if (i != ignoreIdx && this.wscEntries.get(i).target.equals(normTarget)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean isWaitSlotChangeEntryKey(String key) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE && "targetEntries".equals(key);
   }

   private boolean isStoreItemTargetListKey(String key) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.STORE_ITEM && "targetItems".equals(key);
   }

   private boolean isInventoryAuditTargetListKey(String key) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY_AUDIT && "targetItems".equals(key);
   }

   private boolean usesStoreTargetFormatting(String key) {
      return this.isStoreItemTargetListKey(key) || this.isInventoryAuditTargetListKey(key);
   }

   private String buildHandlerEntryFromEditor(String nameText, String slotText) {
      String trimmedName = nameText == null ? "" : nameText.trim();
      int slot = this.parseHandlerSlotField(slotText, -1);
      if (!trimmedName.isEmpty() && slot >= 0) {
         return "#" + slot + "|" + trimmedName;
      } else if (!trimmedName.isEmpty()) {
         return trimmedName;
      } else {
         return slot >= 0 ? "#" + slot : null;
      }
   }

   private ItemTarget captureItemTarget(Slot slot, String itemName, String registryId, int visibleSlot) {
      if (slot != null && slot.getItem() != null && !slot.getItem().isEmpty()) {
         return ItemTarget.capture(slot.getItem(), visibleSlot);
      } else if (visibleSlot >= 0) {
         return ItemTarget.slotOnly(visibleSlot);
      } else {
         String fallback = registryId != null && !registryId.isBlank() ? registryId.trim() : (itemName == null ? "" : itemName.trim());
         return ItemTarget.fromLegacyEntry(fallback);
      }
   }

   private static ItemTarget stripSlotFromTarget(ItemTarget target) {
      if (target == null) {
         return new ItemTarget();
      } else if (!target.hasSlot()) {
         return target;
      } else {
         ItemTarget copy = target.copy();
         copy.slot = -1;
         return copy;
      }
   }

   private static ItemTarget storeCaptureTarget(ItemTarget target) {
      if (target == null) {
         return new ItemTarget();
      } else {
         return target.hasIdentity() ? stripSlotFromTarget(target) : target.copy();
      }
   }

   private ItemTarget buildEntryTargetFromEditor(String nameText, String slotText, ItemTarget previous) {
      String trimmedName = nameText == null ? "" : nameText.strip();
      int slot = this.parseHandlerSlotField(slotText, -1);
      if (trimmedName.isEmpty() && slot < 0) {
         return new ItemTarget();
      } else if (previous != null && trimmedName.equals(previous.editorText())) {
         ItemTarget kept = previous.copy();
         kept.slot = slot;
         return kept;
      } else if (previous != null && previous.hasRichText() && !trimmedName.isEmpty()) {
         ItemTarget preserved = previous.withEditedRichDisplay(trimmedName);
         preserved.slot = slot;
         return preserved;
      } else {
         String raw;
         if (slot >= 0 && !trimmedName.isEmpty()) {
            raw = "#" + slot + "|" + trimmedName;
         } else if (slot >= 0) {
            raw = "#" + slot;
         } else {
            raw = trimmedName;
         }

         return ItemTarget.fromLegacyEntry(raw);
      }
   }

   private ItemTarget targetAt(List<ItemTarget> targets, List<String> legacyEntries, int index) {
      if (targets != null && index >= 0 && index < targets.size()) {
         ItemTarget target = targets.get(index);
         if (target != null && (target.hasSlot() || target.hasIdentity())) {
            return target;
         }
      }

      return legacyEntries != null && index >= 0 && index < legacyEntries.size() ? ItemTarget.fromLegacyEntry(legacyEntries.get(index)) : new ItemTarget();
   }

   private void preserveCapturedListTarget(String key, List<String> entries, ItemTarget capturedTarget) {
      if (key != null && entries != null && capturedTarget != null) {
         String normalized = capturedTarget.toLegacyEntry();
         if (this.usesStoreTargetFormatting(key) || this.isWaitSlotChangeEntryKey(key)) {
            normalized = StoreItemAction.normalizeTargetEntry(normalized);
         } else if (this.isXCarryListKey(key)) {
            normalized = this.normalizeXCarryEntry(normalized);
         }

         if (normalized != null && !normalized.isBlank()) {
            int index = entries.indexOf(normalized);
            if (index >= 0) {
               List<ItemTarget> targets = this.buildStructuredListTargets(key);

               while (targets.size() < entries.size()) {
                  targets.add(new ItemTarget());
               }

               ItemTarget stored = this.isXCarryListKey(key) ? stripSlotFromTarget(capturedTarget) : capturedTarget.copy();
               if (!normalized.equals(stored.toLegacyEntry())) {
                  ItemTarget normalizedTarget = ItemTarget.fromLegacyEntry(normalized);
                  stored.slot = normalizedTarget.slot;
                  if (!normalizedTarget.hasSlot() && !stored.hasIdentity()) {
                     stored = normalizedTarget;
                  }
               }

               targets.set(index, stored);
               this.editorItemLists.put(key, ItemTarget.copyList(targets));
            }
         }
      }
   }

   private void setTargetAt(List<ItemTarget> targets, List<String> legacyEntries, int index, ItemTarget target) {
      if (targets != null && legacyEntries != null && index >= 0) {
         while (targets.size() <= index) {
            targets.add(new ItemTarget());
         }

         while (legacyEntries.size() <= index) {
            legacyEntries.add("");
         }

         ItemTarget stored = target == null ? new ItemTarget() : target;
         targets.set(index, stored);
         legacyEntries.set(index, stored.toLegacyEntry());
      }
   }

   private void addTargetEntry(List<ItemTarget> targets, List<String> legacyEntries, ItemTarget target) {
      if (targets != null && legacyEntries != null && target != null) {
         targets.add(target);
         legacyEntries.add(target.toLegacyEntry());
      }
   }

   private void trimTargetEntries(List<ItemTarget> targets, int size) {
      if (targets != null) {
         while (targets.size() > size) {
            targets.remove(targets.size() - 1);
         }

         while (targets.size() < size) {
            targets.add(new ItemTarget());
         }
      }
   }

   private boolean isEditableStringList(String key) {
      return this.isXCarryListKey(key) || this.isStoreItemTargetListKey(key) || this.isInventoryAuditTargetListKey(key) || this.isWaitSlotChangeEntryKey(key);
   }

   private boolean isXCarryListKey(String key) {
      return this.targetAction != null && this.targetAction.getType() == MacroActionType.XCARRY && "entries".equals(key);
   }

   private boolean isOpenContainerEntityListKey(String key) {
      return this.targetAction != null
         && (this.targetAction.getType() == MacroActionType.OPEN_CONTAINER || this.targetAction.getType() == MacroActionType.INTERACT_ENTITY)
         && "entityTargets".equals(key);
   }

   private boolean isReplaceOnCaptureEntityKey(String key) {
      return false;
   }

   private ActionEditorOverlay.CaptureListAddResult tryAddCapturedStoreItemEntry(
      Slot slot, String itemName, String registryId, int visibleSlot, boolean slotMode, List<String> entries
   ) {
      if (slot != null && visibleSlot >= 0) {
         ItemTarget captured = slotMode
            ? ItemTarget.slotOnly(visibleSlot)
            : storeCaptureTarget(this.captureItemTarget(slot, itemName, registryId, visibleSlot));
         if (!captured.hasIdentity()) {
            String mode = this.currentEnumValue("mode");
            boolean playerInventorySlot = RiptideInventoryHelper.isInventorySlot(MC, slot);
            if ("LOOT".equals(mode) && playerInventorySlot) {
               return new ActionEditorOverlay.CaptureListAddResult(false, "Loot slot numbers must be chest/GUI slots - pick an item to add it by name", -38037);
            }

            if ("STORE".equals(mode) && !playerInventorySlot) {
               return new ActionEditorOverlay.CaptureListAddResult(false, "Store slot numbers must be inventory slots - pick an item to add it by name", -38037);
            }
         }

         return this.tryAddCapturedStringListEntry(this.findField("targetItems"), "targetItems", entries, captured.toLegacyEntry());
      } else {
         return new ActionEditorOverlay.CaptureListAddResult(false, "Could not read that slot", -38037);
      }
   }

   private String normalizeXCarryEntry(String raw) {
      if (raw == null) {
         return null;
      } else {
         String trimmed = raw.trim();
         if (trimmed.isEmpty()) {
            return null;
         } else {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(trimmed);
            if (!parsed.hasIdentity()) {
               if (!trimmed.startsWith("#") && trimmed.matches("\\d+")) {
                  trimmed = "#" + trimmed;
               }

               return XCarryAction.normalizeEntry(trimmed);
            } else {
               parsed.slot = -1;
               String entry = parsed.toLegacyEntry();
               return entry != null && !entry.isBlank() ? entry : null;
            }
         }
      }
   }

   private boolean addStringListEntry(FieldDef field, List<String> entries, String rawEntry) {
      if (entries != null && rawEntry != null) {
         String entry = rawEntry.strip();
         if (entry.isEmpty()) {
            return false;
         } else if (field != null && this.isXCarryListKey(field.key())) {
            entry = this.normalizeXCarryEntry(entry);
            if (entry == null) {
               return false;
            } else {
               int limit = this.currentXCarryEntryLimit();
               if (entries.size() >= limit) {
                  RiptideClientMessaging.sendPrefixed(this.xCarryLimitMessage(limit));
                  return false;
               } else {
                  entries.add(entry);
                  this.syncXCarryDestinationCount(entries.size());
                  this.syncXCarryAmountCount(entries.size());
                  return true;
               }
            }
         } else if (field != null && this.isOpenContainerEntityListKey(field.key())) {
            if (entry.isBlank()) {
               return false;
            } else {
               if (!entries.contains(entry)) {
                  entries.add(entry);
               }

               return true;
            }
         } else if (field == null || !this.usesStoreTargetFormatting(field.key()) && !this.isWaitSlotChangeEntryKey(field.key())) {
            if (field == null || !"players".equals(field.key())) {
               entries.add(entry);
               return true;
            } else if (this.containsIgnoreCase(entries, entry)) {
               return false;
            } else {
               entries.add(entry);
               return true;
            }
         } else {
            entry = StoreItemAction.normalizeTargetEntry(entry);
            if (entry != null && !entry.isBlank() && !entries.contains(entry)) {
               entries.add(entry);
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private ActionEditorOverlay.CaptureListAddResult tryAddCapturedStringListEntry(FieldDef field, String key, List<String> entries, String rawEntry) {
      if (entries == null) {
         return null;
      } else {
         String entry = rawEntry == null ? "" : rawEntry.strip();
         if (entry.isEmpty()) {
            return new ActionEditorOverlay.CaptureListAddResult(false, "Nothing to add from that slot", -38037);
         } else if (field != null && this.isXCarryListKey(field.key())) {
            entry = this.normalizeXCarryEntry(entry);
            if (entry != null && !entry.isBlank()) {
               String formatted = this.formatStringListEntry(key, entry);
               int limit = this.currentXCarryEntryLimit();
               if (entries.size() >= limit) {
                  return new ActionEditorOverlay.CaptureListAddResult(false, this.xCarryLimitMessage(limit), -38037);
               } else {
                  entries.add(entry);
                  this.syncXCarryDestinationCount(entries.size());
                  this.syncXCarryAmountCount(entries.size());
                  return new ActionEditorOverlay.CaptureListAddResult(true, "Added " + formatted, -10035062);
               }
            } else {
               return new ActionEditorOverlay.CaptureListAddResult(false, "Could not read that XCarry entry", -38037);
            }
         } else {
            if (field != null && (this.usesStoreTargetFormatting(field.key()) || this.isWaitSlotChangeEntryKey(field.key()))) {
               entry = StoreItemAction.normalizeTargetEntry(entry);
               if (entry == null || entry.isBlank()) {
                  return new ActionEditorOverlay.CaptureListAddResult(false, "Could not read that slot target", -38037);
               }
            }

            String formatted = this.formatStringListEntry(key, entry);
            if (entries.contains(entry)) {
               return new ActionEditorOverlay.CaptureListAddResult(false, "Already added " + formatted, -38037);
            } else {
               entries.add(entry);
               return new ActionEditorOverlay.CaptureListAddResult(true, "Added " + formatted, -10035062);
            }
         }
      }
   }

   private String formatStringListEntry(String key, String entry) {
      if (this.isXCarryListKey(key)) {
         String formatted = XCarryAction.formatEntry(entry);
         if (!formatted.isEmpty()) {
            return formatted;
         }
      }

      if (this.usesStoreTargetFormatting(key) || this.isWaitSlotChangeEntryKey(key)) {
         return StoreItemAction.formatTargetEntry(entry);
      } else if (this.isOpenContainerEntityListKey(key)) {
         return this.formatEntityEntry(entry);
      } else {
         return entry == null ? "" : entry;
      }
   }

   private int currentXCarryEntryLimit() {
      return 11;
   }

   private String xCarryLimitMessage(int limit) {
      return "XCarry limit reached: " + limit + " entries max (5 craft + 4 armor + offhand + cursor)";
   }

   private void syncXCarryDestinationCount(int count) {
      if (this.workingTag != null) {
         ListTag list = this.workingTag.getList("entryDestinations").orElse(new ListTag());

         while (list.size() < count) {
            list.add(StringTag.valueOf(String.valueOf(Integer.MIN_VALUE)));
         }

         while (list.size() > count) {
            list.remove(list.size() - 1);
         }

         this.workingTag.put("entryDestinations", list);
      }
   }

   private void syncXCarryAmountCount(int count) {
      if (this.workingTag != null) {
         ListTag modes = this.workingTag.getList("entryAmountModes").orElse(new ListTag());

         while (modes.size() < count) {
            modes.add(StringTag.valueOf(XCarryAction.AmountMode.FULL_STK.name()));
         }

         while (modes.size() > count) {
            modes.remove(modes.size() - 1);
         }

         this.workingTag.put("entryAmountModes", modes);
         ListTag amounts = this.workingTag.getList("entryAmounts").orElse(new ListTag());

         while (amounts.size() < count) {
            amounts.add(StringTag.valueOf("1"));
         }

         while (amounts.size() > count) {
            amounts.remove(amounts.size() - 1);
         }

         this.workingTag.put("entryAmounts", amounts);
      }
   }

   private int getXCarryDestination(int index) {
      if (this.workingTag != null && index >= 0) {
         ListTag list = this.workingTag.getList("entryDestinations").orElse(new ListTag());
         if (index >= list.size()) {
            return Integer.MIN_VALUE;
         } else {
            try {
               return Integer.parseInt(list.get(index).asString().orElse(String.valueOf(Integer.MIN_VALUE)));
            } catch (Exception var4) {
               return Integer.MIN_VALUE;
            }
         }
      } else {
         return Integer.MIN_VALUE;
      }
   }

   private void setXCarryDestination(int index, int destination) {
      if (this.workingTag != null && index >= 0) {
         this.syncXCarryDestinationCount(Math.max(index + 1, this.stringLists.getOrDefault("entries", Collections.emptyList()).size()));
         ListTag list = this.workingTag.getList("entryDestinations").orElse(new ListTag());
         if (index < list.size()) {
            list.set(index, StringTag.valueOf(String.valueOf(destination)));
            this.workingTag.put("entryDestinations", list);
         }
      }
   }

   private void removeXCarryDestination(int index) {
      if (this.workingTag != null && index >= 0) {
         ListTag list = this.workingTag.getList("entryDestinations").orElse(new ListTag());
         if (index >= 0 && index < list.size()) {
            list.remove(index);
         }

         this.workingTag.put("entryDestinations", list);
      }
   }

   private XCarryAction.AmountMode getXCarryAmountMode(int index) {
      if (this.workingTag != null && index >= 0) {
         ListTag list = this.workingTag.getList("entryAmountModes").orElse(new ListTag());
         if (index >= list.size()) {
            return XCarryAction.AmountMode.FULL_STK;
         } else {
            try {
               return XCarryAction.AmountMode.valueOf(list.get(index).asString().orElse(XCarryAction.AmountMode.FULL_STK.name()));
            } catch (IllegalArgumentException var4) {
               return XCarryAction.AmountMode.FULL_STK;
            }
         }
      } else {
         return XCarryAction.AmountMode.FULL_STK;
      }
   }

   private void setXCarryAmountMode(int index, XCarryAction.AmountMode mode) {
      if (this.workingTag != null && index >= 0) {
         this.syncXCarryAmountCount(Math.max(index + 1, this.stringLists.getOrDefault("entries", Collections.emptyList()).size()));
         ListTag list = this.workingTag.getList("entryAmountModes").orElse(new ListTag());
         list.set(index, StringTag.valueOf((mode == null ? XCarryAction.AmountMode.FULL_STK : mode).name()));
         this.workingTag.put("entryAmountModes", list);
      }
   }

   private int getXCarryAmount(int index) {
      if (this.workingTag != null && index >= 0) {
         ListTag list = this.workingTag.getList("entryAmounts").orElse(new ListTag());
         if (index >= list.size()) {
            return 1;
         } else {
            try {
               return Math.max(1, Integer.parseInt(list.get(index).asString().orElse("1")));
            } catch (NumberFormatException var4) {
               return 1;
            }
         }
      } else {
         return 1;
      }
   }

   private void setXCarryAmount(int index, int amount) {
      if (this.workingTag != null && index >= 0) {
         this.syncXCarryAmountCount(Math.max(index + 1, this.stringLists.getOrDefault("entries", Collections.emptyList()).size()));
         ListTag list = this.workingTag.getList("entryAmounts").orElse(new ListTag());
         list.set(index, StringTag.valueOf(String.valueOf(Math.max(1, amount))));
         this.workingTag.put("entryAmounts", list);
      }
   }

   private void removeXCarryAmount(int index) {
      if (this.workingTag != null && index >= 0) {
         ListTag modes = this.workingTag.getList("entryAmountModes").orElse(new ListTag());
         if (index >= 0 && index < modes.size()) {
            modes.remove(index);
         }

         this.workingTag.put("entryAmountModes", modes);
         ListTag amounts = this.workingTag.getList("entryAmounts").orElse(new ListTag());
         if (index >= 0 && index < amounts.size()) {
            amounts.remove(index);
         }

         this.workingTag.put("entryAmounts", amounts);
      }
   }

   private Component formatStringListEntryText(String key, String entry, int index) {
      if (this.usesMinecraftTextRendering(key)) {
         ItemTarget target = this.resolveStructuredListTarget(key, index, entry);
         return this.formatItemTargetText(target, this.formatStringListEntry(key, entry));
      } else {
         return Component.literal(this.formatStringListEntry(key, entry));
      }
   }

   private int getPacketRowColor(String entry, int index, boolean selected) {
      if (selected) {
         return RiptideColors.packetRowSelectedText();
      } else {
         Class<? extends Packet<?>> packetClass = RiptidePacketRegistry.getPacket(entry);
         if (packetClass == null) {
            return this.theme.color(UiTone.BODY);
         } else {
            boolean c2s = RiptidePacketRegistry.getC2SPackets().contains(packetClass);
            return RiptideColors.packetRowText(c2s, index);
         }
      }
   }

   private ItemTarget resolveStructuredListTarget(String key, int index, String entry) {
      List<ItemTarget> targets = this.buildStructuredListTargets(key);
      if (index >= 0 && index < targets.size()) {
         ItemTarget target = targets.get(index);
         if (target != null && (target.hasSlot() || target.hasIdentity())) {
            return target.copy();
         }
      }

      ItemTarget parsed = ItemTarget.fromLegacyEntry(entry);
      return !parsed.hasSlot() && !parsed.hasIdentity() ? null : parsed;
   }

   private boolean usesMinecraftTextRendering(String key) {
      return this.usesStoreTargetFormatting(key) || this.isXCarryListKey(key);
   }

   private Component formatItemTargetText(ItemTarget target, String fallback) {
      String safeFallback = fallback == null ? "" : fallback;
      if (target == null) {
         return Component.literal(safeFallback);
      } else {
         boolean hasSlot = target.hasSlot();
         boolean hasIdentity = target.hasIdentity();
         if (hasSlot && hasIdentity) {
            return Component.literal(target.slot + ": ").append(target.listComponent().copy());
         } else if (hasSlot) {
            return Component.literal("#" + target.slot);
         } else {
            if (hasIdentity) {
               Component display = target.listComponent();
               if (display != null && !display.getString().isBlank()) {
                  return display.copy();
               }
            }

            return Component.literal(safeFallback);
         }
      }
   }

   private static String buildEntryFromNameAndSlot(String name, String slotText) {
      String n = name == null ? "" : name.strip();
      int slot = -1;
      if (slotText != null && !slotText.isBlank()) {
         try {
            slot = Integer.parseInt(slotText.replaceAll("[^0-9]", ""));
         } catch (NumberFormatException var5) {
         }
      }

      if (slot >= 0 && !n.isEmpty()) {
         return "#" + slot + "|" + n;
      } else {
         return slot >= 0 ? "#" + slot : n;
      }
   }

   private String parseHandlerEntryName(String entry) {
      if (entry == null || entry.isBlank()) {
         return "";
      } else if (!entry.startsWith("#")) {
         return entry;
      } else {
         int separator = entry.indexOf(124);
         return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
      }
   }

   private int parseHandlerSlotField(String text, int fallback) {
      String raw = text == null ? "" : text.trim();
      if (raw.isEmpty()) {
         return fallback;
      } else {
         String cleaned = raw.replaceAll("[^0-9]", "");
         if (cleaned.isEmpty()) {
            return fallback;
         } else {
            try {
               int slot = Integer.parseInt(cleaned);
               return slot < 0 ? fallback : slot;
            } catch (NumberFormatException var6) {
               return fallback;
            }
         }
      }
   }

   private int parseSlotEntryValue(String entry) {
      if (entry != null && entry.startsWith("#")) {
         try {
            int separator = entry.indexOf(124);
            String raw = separator >= 0 ? entry.substring(1, separator) : entry.substring(1);
            return Integer.parseInt(raw);
         } catch (NumberFormatException var4) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private boolean containsEntryOtherThan(List<String> entries, String entry, int ignoreIndex) {
      for (int i = 0; i < entries.size(); i++) {
         if (i != ignoreIndex && entries.get(i).equals(entry)) {
            return true;
         }
      }

      return false;
   }

   private List<RiptideSharedState.QueuedPacket> getWorkingQueuedPackets() {
      if (this.workingTag == null) {
         return new ArrayList<>();
      } else {
         SendPacketAction action = new SendPacketAction();
         action.fromTag(this.workingTag);
         return new ArrayList<>(action.packets);
      }
   }

   private void setWorkingQueuedPackets(List<RiptideSharedState.QueuedPacket> packets) {
      if (this.workingTag != null) {
         ListTag packetList = new ListTag();
         if (packets != null) {
            for (RiptideSharedState.QueuedPacket qp : packets) {
               CompoundTag packetTag = RiptideClipboardHelper.serializeQueuedPacket(qp);
               if (packetTag != null) {
                  packetList.add(packetTag);
               }
            }
         }

         this.workingTag.put("packets", packetList);
      }
   }

   private String buildSendPacketInfo() {
      List<RiptideSharedState.QueuedPacket> packets = this.getWorkingQueuedPackets();
      if (packets.isEmpty()) {
         return "Packets: 0";
      } else {
         String autoName = "";

         for (RiptideSharedState.QueuedPacket qp : packets) {
            if (qp != null && qp.packet != null) {
               autoName = RiptidePacketNamer.getFriendlyName(qp.packet);
               break;
            }
         }

         if (autoName.isEmpty()) {
            return "Packets: " + packets.size();
         } else {
            return packets.size() == 1 ? "Packets: 1 - " + autoName : "Packets: " + packets.size() + " - " + autoName;
         }
      }
   }

   private void setRawPacketActionData(RiptideSharedState.QueuedPacket packet) {
      if (this.workingTag != null && packet != null) {
         List<RiptideSharedState.QueuedPacket> single = Collections.singletonList(packet);
         String base64 = RiptideClipboardHelper.serializeQueueToBase64(single);
         if (base64 != null && !base64.isBlank()) {
            this.workingTag.putString("packetData", base64);
            if (!this.workingTag.contains("description") || this.workingTag.getStringOr("description", "").isBlank()) {
               String name = packet.packet != null ? RiptidePacketNamer.getFriendlyName(packet.packet) : "Packet";
               this.workingTag.putString("description", name);
            }
         }
      }
   }

   private String buildPacketActionInfo() {
      if (this.workingTag == null) {
         return "Raw Packet: empty";
      } else {
         String packetData = this.workingTag.getStringOr("packetData", "");
         if (packetData != null && !packetData.isBlank()) {
            String description = this.workingTag.getStringOr("description", "");
            return description != null && !description.isBlank() ? "Raw Packet: " + description : "Raw Packet: loaded";
         } else {
            return "Raw Packet: empty";
         }
      }
   }

   private void startGBreakCaptureForEditor() {
      if (this.guardWorldCaptureAction()) {
         RiptideSharedState state = RiptideSharedState.get();
         this.screenBeforeGBreak = MC.gui.screen();
         this.enterCaptureMode();
         state.setCaptureCancelCallback(() -> {
            state.cancelGBreakCapture();
            if (this.screenBeforeGBreak != null) {
               MC.gui.setScreen(this.screenBeforeGBreak);
               this.screenBeforeGBreak = null;
            }

            this.exitCaptureMode(false, false);
         });
         MC.execute(() -> {
            if (MC.gui.screen() != null) {
               MC.gui.setScreen(null);
            }
         });
         RiptideClientMessaging.sendPrefixed("GBreak: Break a block to capture the insta-break packet");
         state.startGBreakCapture(() -> MC.execute(() -> {
            List<RiptideSharedState.QueuedPacket> captured = RiptideSharedState.get().getGBreakCapturedPackets();
            if (!captured.isEmpty()) {
               this.setWorkingQueuedPackets(Collections.singletonList(captured.get(0)));
               this.workingTag.putString("customName", "GBreak");
               RiptideChatField customNameField = this.textFields.get("customName");
               if (customNameField != null) {
                  customNameField.setText("GBreak");
               }

               RiptideClientMessaging.sendPrefixed("GBreak packet captured");
            } else {
               RiptideClientMessaging.sendPrefixed("GBreak capture finished with no packet");
            }

            if (this.screenBeforeGBreak != null) {
               MC.gui.setScreen(this.screenBeforeGBreak);
               this.screenBeforeGBreak = null;
            }

            this.exitCaptureMode(false, false);
            RiptideOverlayManager.get().bringToFront(this);
         }));
      }
   }

   private void startLookAtEntityCapture() {
      if (this.guardWorldCaptureAction()) {
         if (this.targetAction instanceof LookAtBlockAction) {
            Screen previousScreen = MC.gui.screen();
            this.enterCaptureMode();
            RiptideSharedState state = RiptideSharedState.get();
            state.setCaptureCancelCallback(() -> {
               if (previousScreen != null) {
                  MC.gui.setScreen(previousScreen);
               }

               state.setEntityCaptureCallback(null);
               this.exitCaptureMode(false, false);
            });
            MC.execute(() -> {
               if (MC.gui.screen() != null) {
                  MC.gui.setScreen(null);
               }
            });
            state.setEntityCaptureSpecific(this.entitySpecificCaptureMode);
            state.setEntityCaptureCallback(payload -> MC.execute(() -> {
               List<String> selected = this.stringLists.get("entityIds");
               if (selected != null && payload != null && !payload.isBlank()) {
                  if (this.entitySpecificCaptureMode) {
                     selected.removeIf(existing -> existing.startsWith(this.entitySpecificEntryPrefix(payload)));
                  }

                  if (!selected.contains(payload)) {
                     selected.add(payload);
                  }
               }

               if (previousScreen != null) {
                  MC.gui.setScreen(previousScreen);
               }

               this.exitCaptureMode(false, false);
               RiptideOverlayManager.get().bringToFront(this);
            }));
         }
      }
   }

   private void renderWaitSoundPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitGuiName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "checkDistance", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "maxDistance", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      List<String> selected = this.stringLists.getOrDefault("soundIds", Collections.emptyList());
      cy = this.renderSimpleSelectedList(ctx, x, cy, w, mx, my, "wait_sound_selected", "Sounds", selected, value -> {
         selected.remove(value);
         DirectScrollViewport vp = this.selectedScrollViewports.get("wait_sound_selected");
         if (vp != null) {
            vp.scrollBy(-1);
         }
      }, RiptideRegistryLabels::sound, "(any sound - add to filter)");
      RiptideChatField search = this.addFields.get("soundIds");
      if (search != null) {
         search.setX(x);
         search.setY(cy + 1);
         search.setWidth(w);
         search.render(ctx, mx, my, delta);
      }

      cy += 18;
      String filter = search != null ? search.getText().trim().toLowerCase() : "";
      List<String> filtered = this.filterRegistryValues("wait_sound_search", getAllSoundIds(), filter, RiptideRegistryLabels::sound);
      this.renderSearchRegistryList(ctx, x, cy, w, mx, my, "wait_sound_search", filtered, 6, value -> {
         if (selected.contains(value)) {
            selected.remove(value);
         } else {
            selected.add(value);
         }
      }, selected::contains, RiptideRegistryLabels::sound);
   }

   private void renderWaitEntityPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "checkMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "radius", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "centerOnPlayer", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "pos", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "mustBeLookingAt", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      List<String> selected = this.stringLists.getOrDefault("entityIds", Collections.emptyList());
      cy = this.renderSimpleSelectedList(
         ctx, x, cy, w, mx, my, "wait_entity_selected", "Entities", selected, selected::remove, this::formatEntityEntry, "(any entity - add type or specific)"
      );
      RiptideChatField search = this.addFields.get("entityIds");
      int searchW = w - 66;
      if (search != null) {
         search.setX(x);
         search.setY(cy + 1);
         search.setWidth(searchW);
         search.render(ctx, mx, my, delta);
      }

      int modeX = x + searchW + 2;
      String modeLabel = this.entitySpecificCaptureMode ? "[Spec]" : "[Type]";
      this.renderActionButton(ctx, modeX, cy, 30, 14, modeLabel, mx, my, () -> this.entitySpecificCaptureMode = !this.entitySpecificCaptureMode);
      this.renderActionButton(ctx, modeX + 34, cy, 32, 14, "CAP", mx, my, this::startWaitEntityCapture);
      cy += 18;
      String filter = search != null ? search.getText().trim().toLowerCase() : "";
      List<String> filtered = this.filterRegistryValues("wait_entity_search", getAllEntityIds(), filter, RiptideRegistryLabels::entity);
      this.renderSearchRegistryList(ctx, x, cy, w, mx, my, "wait_entity_search", filtered, 4, value -> {
         if (selected.contains(value)) {
            selected.remove(value);
         } else {
            selected.add(value);
         }
      }, selected::contains, RiptideRegistryLabels::entity);
      cy += 66;
      UiText.draw(ctx, this.textRenderer, "Nearby Entities", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 13;
      List<String> nearbyEntries = this.getNearbyEntityEntries();
      this.renderSearchRegistryList(
         ctx,
         x,
         cy,
         w,
         mx,
         my,
         "wait_entity_nearby",
         nearbyEntries,
         3,
         value -> {
            String stored = this.entitySpecificCaptureMode ? value : this.extractEntityTypeFromNearbyEntry(value);
            if (!stored.isBlank()) {
               if (this.entitySpecificCaptureMode) {
                  boolean removed = selected.removeIf(existing -> existing.startsWith(this.entitySpecificEntryPrefix(stored)));
                  if (!removed) {
                     selected.add(stored);
                  }
               } else if (selected.contains(stored)) {
                  selected.remove(stored);
               } else {
                  selected.add(stored);
               }
            }
         },
         value -> {
            String stored = this.entitySpecificCaptureMode ? value : this.extractEntityTypeFromNearbyEntry(value);
            return this.entitySpecificCaptureMode
               ? selected.stream().anyMatch(existing -> existing.startsWith(this.entitySpecificEntryPrefix(stored)))
               : selected.contains(stored);
         },
         this::formatNearbyEntityEntry
      );
   }

   private void renderOpenContainerPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "targetMode", x, cy, w, mx, my, delta);
      String targetMode = this.currentEnumValue("targetMode");
      if ("ENTITY".equals(targetMode)) {
         cy = this.renderFieldByKey(ctx, "entityTargets", x, cy, w, mx, my, delta);
      } else if ("BLOCK".equals(targetMode)) {
         cy = this.renderFieldByKey(ctx, "pos", x, cy, w, mx, my, delta);
      }

      cy = this.renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);
      if ("ENTITY".equals(targetMode)) {
         String hint = "Pick captures exact entities. Last Target reuses the most recent interaction target.";
         cy = this.renderEditorHint(ctx, x, cy, w, hint);
         List<String> selected = this.stringLists.getOrDefault("entityTargets", Collections.emptyList());
         cy += 13;
         List<String> nearbyEntries = this.getNearbyEntityEntries();
         this.renderSearchRegistryList(ctx, x, cy, w, mx, my, "open_container_entity_nearby", nearbyEntries, 3, value -> {
            if (selected.contains(value)) {
               selected.remove(value);
            } else {
               selected.add(value);
            }
         }, selected::contains, this::formatNearbyEntityEntry);
      } else if ("LAST_TARGET".equals(targetMode)) {
         this.renderEditorHint(ctx, x, cy, w, "Uses the last block or entity container you actually opened.");
      } else {
         cy = this.renderEditorHint(ctx, x, cy, w, "Capture only marks the block. It will not open it.");
         this.renderNearbyContainerList(ctx, x, cy, w, mx, my, "open_container_nearby", "pos");
      }
   }

   private void renderNearbyContainerList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, String listKey, String fieldKey) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, "Nearby Containers", font, RiptideColors.textSecondary(), x, y + 2, false);
      y += 14;
      List<BlockPos> nearbyContainers = this.getNearbyContainerPositions();
      this.renderSearchRegistryList(
         ctx,
         x,
         y,
         w,
         mx,
         my,
         listKey,
         nearbyContainers,
         2,
         pos -> this.fillBlockPosField(fieldKey, pos),
         pos -> this.isCurrentBlockPosField(fieldKey, pos),
         this::formatContainerEntry
      );
   }

   private void renderStoreItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "allItems", x, cy, w, mx, my, delta);
      List<String> selected = this.stringLists.getOrDefault("targetItems", Collections.emptyList());
      if (!this.toggleStates.getOrDefault("allItems", false)) {
         cy = this.renderSimpleSelectedList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "store_items_selected",
            "Target Items",
            selected,
            selected::remove,
            StoreItemAction::formatTargetEntry,
            value -> this.formatStringListEntryText("targetItems", value, selected.indexOf(value)),
            selected::clear,
            "(nothing selected - search, type #slot, or capture)",
            true
         );
         int storeEditIdx = this.stringListEditIndex.getOrDefault("store_items_selected", -1);
         boolean storeEditing = storeEditIdx >= 0 && storeEditIdx < selected.size();
         RiptideChatField search = this.addFields.get("targetItems");
         int pickW = 32;
         int addBtnW = 34;
         int slotW = 44;
         int capBtnX = x + w - pickW;
         int addBtnX = capBtnX - 3 - addBtnW;
         int slotX = addBtnX - 2 - slotW;
         int nameW = slotX - x - 2;
         RiptideChatField storeSlotF = this.textFields.get("store_slot");
         if (storeSlotF == null) {
            storeSlotF = this.makeField(slotW);
            storeSlotF.setNumericOnly(true);
            storeSlotF.setPlaceholder(Component.literal("Slot#"));
            this.textFields.put("store_slot", storeSlotF);
         }

         String pendingText = this.stringListEditPendingText.remove("store_items_selected");
         if (pendingText != null) {
            if (search != null) {
               search.setText(this.parseHandlerEntryName(pendingText));
            }

            ItemTarget parsed = ItemTarget.fromLegacyEntry(pendingText);
            storeSlotF.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
         }

         if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(nameW);
            search.render(ctx, mx, my, delta);
         }

         storeSlotF.setX(slotX);
         storeSlotF.setY(cy + 1);
         storeSlotF.setWidth(slotW);
         storeSlotF.render(ctx, mx, my, delta);
         if (storeEditing && search != null) {
            String nameText = search.getText().strip();
            String slotText = storeSlotF.getText().strip();
            String entry = buildEntryFromNameAndSlot(nameText, slotText);
            if (!entry.isEmpty()) {
               String normalized = StoreItemAction.normalizeTargetEntry(entry);
               if (normalized != null && !normalized.isBlank() && !normalized.equals(selected.get(storeEditIdx))) {
                  selected.set(storeEditIdx, normalized);
                  this.editorItemLists.put("targetItems", this.buildStructuredListTargets("targetItems"));
               }
            }
         }

         RiptideChatField fStoreSlotF = storeSlotF;
         this.renderOverlayButton(ctx, addBtnX, cy, addBtnW, 14, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
            if (search != null) {
               String nameText = search.getText().strip();
               String slotText = fStoreSlotF.getText().strip();
               String entry = buildEntryFromNameAndSlot(nameText, slotText);
               if (!entry.isEmpty()) {
                  String normalized = StoreItemAction.normalizeTargetEntry(entry);
                  if (normalized != null && !normalized.isBlank() && !selected.contains(normalized)) {
                     selected.add(normalized);
                     this.editorItemLists.put("targetItems", this.buildStructuredListTargets("targetItems"));
                  }

                  search.setText("");
                  fStoreSlotF.setText("");
                  this.stringListEditIndex.put("store_items_selected", -1);
               }
            }
         });
         boolean capturing = this.captureSession.isItemSlotCapture("targetItems");
         this.renderFieldCaptureButton(
            ctx, capBtnX, cy, pickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("targetItems")
         );
         cy += 18;
         String filter = search != null ? search.getText().trim().toLowerCase() : "";
         List<String> filtered = this.filterRegistryValues("store_items_search", getAllItemIds(), filter, RiptideRegistryLabels::item);
         this.renderSearchRegistryList(ctx, x, cy, w, mx, my, "store_items_search", filtered, 6, value -> {
            if (selected.contains(value)) {
               selected.remove(value);
            } else {
               selected.add(value);
            }

            this.stringListEditIndex.put("store_items_selected", -1);
            this.editorItemLists.put("targetItems", this.buildStructuredListTargets("targetItems"));
         }, selected::contains, RiptideRegistryLabels::item);
         cy += 82;
         cy = this.renderEditorHint(
            ctx, x, cy, w, "Pick on an item adds it by name, from any slot on the screen. Ctrl+pick or an empty slot adds that exact slot."
         );
         String modeHint = "LOOT".equals(this.currentEnumValue("mode"))
            ? "Exact slots must be chest/custom GUI slots."
            : "Exact slots must be player inventory slots.";
         cy = this.renderEditorHint(ctx, x, cy, w, modeHint);
      }

      cy = this.renderFieldByKey(ctx, "persistent", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "delayTicks", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "closeAfter", x, cy, w, mx, my, delta);
      this.renderFieldByKey(ctx, "closeSendPkt", x, cy, w, mx, my, delta);
   }

   private void renderInventoryAuditPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
      List<String> selected = this.stringLists.getOrDefault("targetItems", Collections.emptyList());
      FieldDef targetItemsField = this.getField("targetItems");
      if (targetItemsField != null && this.isFieldVisible(targetItemsField)) {
         String emptyLabel = this.getStringListEmptyHint("targetItems");
         if (emptyLabel == null || emptyLabel.isBlank()) {
            emptyLabel = "(nothing selected)";
         }

         boolean multipleStacks = this.toggleStates.getOrDefault("multipleStacks", false);
         int var34 = this.renderSimpleSelectedList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "audit_items_selected",
            "Targets",
            selected,
            selected::remove,
            StoreItemAction::formatTargetEntry,
            value -> this.formatStringListEntryText("targetItems", value, selected.indexOf(value)),
            selected::clear,
            emptyLabel,
            true
         );
         int multiBtnW = 96;
         int clearBtnW = 44;
         int multiBtnX = x + w - clearBtnW - 3 - multiBtnW;
         this.renderOverlayToggleButton(
            ctx,
            multiBtnX,
            cy,
            multiBtnW,
            14,
            "Multiple Stacks",
            multipleStacks,
            "macro-audit:multiple-stacks",
            mx,
            my,
            () -> this.toggleStates.put("multipleStacks", !this.toggleStates.getOrDefault("multipleStacks", false))
         );
         int auditEditIdx = this.stringListEditIndex.getOrDefault("audit_items_selected", -1);
         boolean auditEditing = auditEditIdx >= 0 && auditEditIdx < selected.size();
         RiptideChatField search = this.addFields.get("targetItems");
         int pickW = 32;
         int addBtnW = 34;
         int slotW = 26;
         int pickX = x + w - addBtnW - 3 - pickW;
         int addBtnX = x + w - addBtnW;
         int slotX = pickX - 3 - slotW;
         int nameW = slotX - x - 2;
         RiptideChatField auditSlotF = this.textFields.get("audit_slot");
         if (auditSlotF == null) {
            auditSlotF = this.makeField(slotW);
            auditSlotF.setNumericOnly(true);
            auditSlotF.setPlaceholder(Component.literal("Slot"));
            this.textFields.put("audit_slot", auditSlotF);
         }

         String pendingText = this.stringListEditPendingText.remove("audit_items_selected");
         if (pendingText != null) {
            if (search != null) {
               search.setText(this.parseHandlerEntryName(pendingText));
            }

            ItemTarget parsed = ItemTarget.fromLegacyEntry(pendingText);
            auditSlotF.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
         }

         if (search != null) {
            search.setX(x);
            search.setY(var34 + 1);
            search.setWidth(nameW);
            search.render(ctx, mx, my, delta);
         }

         auditSlotF.setX(slotX);
         auditSlotF.setY(var34 + 1);
         auditSlotF.setWidth(slotW);
         auditSlotF.render(ctx, mx, my, delta);
         if (auditEditing && search != null) {
            String nameText = search.getText().strip();
            String slotText = auditSlotF.getText().strip();
            String entry = buildEntryFromNameAndSlot(nameText, slotText);
            if (!entry.isEmpty()) {
               String normalized = StoreItemAction.normalizeTargetEntry(entry);
               if (normalized != null && !normalized.isBlank() && !normalized.equals(selected.get(auditEditIdx))) {
                  selected.set(auditEditIdx, normalized);
                  this.editorItemLists.put("targetItems", this.buildStructuredListTargets("targetItems"));
               }
            }
         }

         RiptideChatField fAuditSlotF = auditSlotF;
         boolean capturing = this.captureSession.isItemSlotCapture("targetItems");
         this.renderFieldCaptureButton(
            ctx, pickX, var34, pickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("targetItems")
         );
         String addLabel = auditEditing ? "New" : "+Add";
         this.renderOverlayButton(ctx, addBtnX, var34, addBtnW, 14, addLabel, CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
            if (search != null) {
               if (this.stringListEditIndex.getOrDefault("audit_items_selected", -1) >= 0) {
                  this.stringListEditIndex.put("audit_items_selected", -1);
                  this.stringListEditPendingText.put("audit_items_selected", "");
                  search.setText("");
                  fAuditSlotF.setText("");
               } else {
                  String nameText = search.getText().strip();
                  String slotText = fAuditSlotF.getText().strip();
                  String entry = buildEntryFromNameAndSlot(nameText, slotText);
                  if (!entry.isEmpty()) {
                     String normalizedx = StoreItemAction.normalizeTargetEntry(entry);
                     if (normalizedx != null && !normalizedx.isBlank() && !selected.contains(normalizedx)) {
                        selected.add(normalizedx);
                        this.editorItemLists.put("targetItems", this.buildStructuredListTargets("targetItems"));
                     }

                     search.setText("");
                     fAuditSlotF.setText("");
                     this.stringListEditIndex.put("audit_items_selected", -1);
                     this.stringListEditPendingText.put("audit_items_selected", "");
                  }
               }
            }
         });
         cy = var34 + 18;
      }

      cy = this.renderFieldByKey(ctx, "openMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "openCommand", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "containerPos", x, cy, w, mx, my, delta);
      if ("CONTAINER".equals(this.currentEnumValue("openMode"))) {
         cy = this.renderEditorHint(ctx, x, cy, w, "Capture stores the clicked block position. Nearby also includes likely server-handled GUI blocks.");
         this.renderNearbyContainerList(ctx, x, cy, w, mx, my, "inventory_audit_nearby", "containerPos");
         cy += 43;
      }

      cy = this.renderFieldByKey(ctx, "dupeVector", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "iterations", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "maxTransferAttempts", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "transferRetryDelayMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "spamCount", x, cy, w, mx, my, delta);
      this.renderFieldByKey(ctx, "spamDelayMs", x, cy, w, mx, my, delta);
   }

   private void renderPayPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "commandTemplate", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "amountInput", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "divideEnabled", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "probeHidden", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "delayEnabled", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "delayMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "confirmMacro", x, cy, w, mx, my, delta);
      List<String> selected = this.stringLists.getOrDefault("players", Collections.emptyList());
      cy = this.renderSimpleSelectedList(
         ctx,
         x,
         cy,
         w,
         mx,
         my,
         "pay_players_selected",
         "Players",
         selected,
         value -> this.removeStringIgnoreCase(selected, value),
         value -> value,
         ignored -> null,
         selected::clear,
         "(scan the server or add one manually)"
      );
      RiptideChatField search = this.addFields.get("players");
      int scanW = 34;
      int allW = 28;
      int searchW = Math.max(1, w - scanW - allW - 4);
      if (search != null) {
         search.setX(x);
         search.setY(cy + 1);
         search.setWidth(searchW);
         search.render(ctx, mx, my, delta);
      }

      int scanX = x + searchW + 2;
      int allX = scanX + scanW + 2;
      this.renderActionButton(ctx, scanX, cy, scanW, 14, "Scan", mx, my, this::refreshPayScannedPlayers);
      this.renderActionButton(ctx, allX, cy, allW, 14, "All", mx, my, () -> this.addFilteredPayPlayers(selected, search != null ? search.getText() : ""));
      cy += 18;
      String query = search != null ? search.getText() : "";
      if (!this.payPlayerScanPerformed) {
         this.renderRegistryPlaceholder(ctx, x, cy, w, "pay_players_scan", 1, "Press Scan to load the current server players.");
         cy += 17;
      } else {
         cy = this.renderPayGroupedList(ctx, x, cy, w, mx, my, selected, query) + 4;
      }

      RiptideChatField amountField = this.textFields.get("amountInput");
      long amount = PayAction.parseAmount(amountField != null ? amountField.getText() : "");
      boolean divide = this.toggleStates.getOrDefault("divideEnabled", false);
      int count = selected.size();
      String summary;
      if (count == 0) {
         summary = "Empty list = pays EVERYONE (tab + probed hidden).";
      } else if (divide) {
         long[] dist = PayAction.distribute(amount, count);
         String per = dist[0] == dist[count - 1]
            ? PayAction.formatAmount(dist[0])
            : PayAction.formatAmount(dist[count - 1]) + "-" + PayAction.formatAmount(dist[0]);
         summary = "Splits " + PayAction.formatAmount(amount) + " among " + count + " (" + per + " each).";
      } else {
         summary = "Pays " + count + " x " + PayAction.formatAmount(amount) + " (" + PayAction.formatAmount(amount * count) + " total).";
      }

      cy = this.renderEditorHint(ctx, x, cy, w, summary);
      String hint = this.payPlayerScanPerformed
         ? "Click a name to add it. Click a rank header to add/remove everyone in that rank; All adds all."
         : "Scan to list players by rank. Type a name then Enter to add it manually.";
      this.renderEditorHint(ctx, x, cy, w, hint);
   }

   private List<ActionEditorOverlay.PayRow> buildPayRows(String query) {
      Map<String, List<String>> byPrefix = new LinkedHashMap<>();

      for (String name : this.filterEntries(this.payScannedPlayers, query)) {
         byPrefix.computeIfAbsent(this.payPrefixFor(name), ignored -> new ArrayList<>()).add(name);
      }

      List<String> order = new ArrayList<>(byPrefix.keySet());
      order.sort((a, b) -> {
         boolean ab = a.isBlank();
         boolean bb = b.isBlank();
         if (ab != bb) {
            return ab ? 1 : -1;
         } else {
            return a.compareToIgnoreCase(b);
         }
      });
      List<ActionEditorOverlay.PayRow> rows = new ArrayList<>();

      for (String prefix : order) {
         List<String> names = byPrefix.get(prefix);
         names.sort(String.CASE_INSENSITIVE_ORDER);
         rows.add(ActionEditorOverlay.PayRow.header(prefix, names));

         for (String name : names) {
            rows.add(ActionEditorOverlay.PayRow.player(prefix, name));
         }
      }

      return rows;
   }

   private int renderPayGroupedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, List<String> selected, String query) {
      int rowH = 13;
      List<ActionEditorOverlay.PayRow> entries = this.buildPayRows(query);
      if (entries.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, w, 12, false, false);
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No players found", x, y, w);
         return y + rowH;
      } else {
         int clipTop = this.bodyInputClip != null ? this.bodyInputClip.y() : Integer.MIN_VALUE;
         int clipBottom = this.bodyInputClip != null ? this.bodyInputClip.bottom() : Integer.MAX_VALUE;
         Identifier font = this.theme.fontFor(UiTone.BODY);
         int iy = y;

         for (ActionEditorOverlay.PayRow row : entries) {
            if (iy + rowH > clipTop && iy < clipBottom) {
               if (row.header()) {
                  List<String> members = row.members();
                  int selCount = (int)members.stream().filter(m -> this.containsIgnoreCase(selected, m)).count();
                  boolean allSel = selCount == members.size() && selCount > 0;
                  UiText.fill(ctx, x, iy, x + w, iy + rowH - 1, 1140850688);
                  UiText.fill(ctx, x, iy, x + 2, iy + rowH - 1, allSel ? -11628966 : -7459271);
                  int headerColor = allSel ? -6496088 : -1593160;
                  int baseY = iy + Math.max(1, (rowH - 8) / 2);
                  String marker = allSel ? "✓ " : "+ ";
                  String counter = "  (" + selCount + "/" + members.size() + ")";
                  int markerW = this.textRenderer.width(marker);
                  int counterW = this.textRenderer.width(counter);
                  int rankMaxW = Math.max(8, w - 12 - markerW - counterW);
                  int cx = x + 6;
                  UiText.draw(ctx, this.textRenderer, marker, font, headerColor, cx, baseY, false);
                  cx += markerW;
                  Component styled = row.prefix().isBlank() ? null : this.payPrefixComponents.get(row.prefix());
                  if (styled != null && this.textRenderer.width(styled) <= rankMaxW) {
                     ctx.text(this.textRenderer, styled, cx, baseY, -1, false);
                     cx += this.textRenderer.width(styled);
                  } else {
                     String fallback = row.prefix().isBlank() ? "No rank" : row.prefix();
                     int fallbackColor = row.prefix().isBlank() ? headerColor : -1;
                     fallback = UiText.trimToWidth(this.textRenderer, fallback, rankMaxW, font, fallbackColor);
                     UiText.draw(ctx, this.textRenderer, fallback, font, fallbackColor, cx, baseY, false);
                     cx += this.textRenderer.width(fallback);
                  }

                  UiText.draw(ctx, this.textRenderer, counter, font, headerColor, cx, baseY, false);
                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, w, rowH, () -> this.togglePayRankSelection(selected, members)));
               } else {
                  boolean sel = this.containsIgnoreCase(selected, row.name());
                  boolean hovered = mx >= x && mx < x + w && my >= iy && my < iy + rowH;
                  String label = UiText.trimToWidth(this.textRenderer, "  " + row.name(), w - 6, font, RiptideColors.textSecondary());
                  MacroTypedListControl.renderRow(
                     ctx,
                     this.textRenderer,
                     Component.literal(label),
                     UiBounds.of(x, iy, w, rowH),
                     hovered,
                     sel,
                     sel ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                     false
                  );
                  String name = row.name();
                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, w, rowH, () -> this.togglePayPlayerSelection(selected, name)));
               }
            }

            iy += rowH;
         }

         return iy;
      }
   }

   private void renderToggleModulePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      List<ToggleModuleAction.ModuleEntry> selected = this.toggleModuleEntries != null ? this.toggleModuleEntries : Collections.emptyList();
      UiText.draw(ctx, this.textRenderer, "Module Actions (" + selected.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      boolean canClear = !selected.isEmpty();
      int clearX = x + w - 44;
      this.renderOverlayButton(ctx, clearX, cy, 44, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
         selected.clear();
         DirectScrollViewport vp = this.selectedScrollViewports.get("toggle_module_selected");
         if (vp != null) {
            vp.jumpTo(0);
         }
      });
      cy += 14;
      int listH = 60;
      int itemW = w - 5 - 1;
      DirectScrollViewport toggleViewport = this.getOrCreateViewport(this.selectedScrollViewports, "toggle_module_selected", x, cy, w, listH, 15, 5);
      toggleViewport.setContentHeight(selected.size() * 15);
      toggleViewport.renderScrollbar(ctx, mx, my);
      if (selected.isEmpty()) {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "Pick modules below.", x, cy, itemW);
      } else {
         toggleViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = toggleViewport.getFirstVisibleRow();

         for (int i = first; i < selected.size() && i <= toggleViewport.getLastVisibleRow(); i++) {
            int iy = toggleViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               ToggleModuleAction.ModuleEntry entry = selected.get(i);
               int removeW = 13;
               int modeW = 52;
               int rowW = itemW - removeW - modeW - 4;
               boolean hovered = mx >= x && mx < x + rowW && my >= iy + 1 && my < iy + 14;
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  Component.literal(entry.moduleName),
                  UiBounds.of(x, iy + 1, rowW, 13),
                  hovered,
                  false,
                  CompactListRenderer.RowTone.NORMAL,
                  false
               );
               int modeX = x + rowW + 2;
               int entryIndex = i;
               ToggleModuleAction.ToggleMode[] tmModes = ToggleModuleAction.ToggleMode.values();
               List<String> tmOptions = new ArrayList<>();

               for (ToggleModuleAction.ToggleMode m : tmModes) {
                  tmOptions.add(this.formatToggleModeShort(m));
               }

               this.renderInlineDropdown("toggle_mode_" + entryIndex, tmOptions, entry.toggleMode.ordinal(), modeX, iy + 1, modeW, 13, idx -> {
                  if (entryIndex >= 0 && entryIndex < selected.size()) {
                     selected.get(entryIndex).toggleMode = tmModes[Math.max(0, Math.min(idx, tmModes.length - 1))];
                  }
               });
               int removeX = modeX + modeW + 2;
               this.renderIconDeleteButton(ctx, removeX, iy + 1, removeW, mx, my, () -> {
                  if (entryIndex >= 0 && entryIndex < selected.size()) {
                     selected.remove(entryIndex);
                     DirectScrollViewport vp = this.selectedScrollViewports.get("toggle_module_selected");
                     if (vp != null) {
                        vp.scrollBy(-1);
                     }
                  }
               });
            }
         }

         toggleViewport.endRender(ctx);
      }

      cy += listH + 4;
      RiptideChatField search = this.addFields.get("_toggle_module_search");
      int refreshW = 52;
      int addW = 40;
      int searchW = Math.max(1, w - refreshW - addW - 4);
      if (search != null) {
         search.setX(x);
         search.setY(cy + 1);
         search.setWidth(searchW);
         search.render(ctx, mx, my, delta);
      }

      int refreshX = x + searchW + 2;
      int addX = refreshX + refreshW + 2;
      this.renderActionButton(ctx, refreshX, cy, refreshW, 14, "Refresh", mx, my, this::refreshMeteorModuleNames);
      this.renderActionButton(ctx, addX, cy, addW, 14, "+Add", mx, my, () -> this.addFirstFilteredModule(search != null ? search.getText() : ""));
      cy += 18;
      String query = search != null ? search.getText() : "";
      List<String> filteredRiptide = this.filterEntries("toggle_module_riptide_registry", this.riptideModuleNames, query);
      List<String> filteredMeteor = this.filterEntries("toggle_module_meteor_registry", this.meteorModuleNames, query);
      boolean hasExternalModules = !this.meteorModuleNames.isEmpty();
      if (this.riptideModuleNames.isEmpty() && this.meteorModuleNames.isEmpty()) {
         this.renderRegistryPlaceholder(ctx, x, cy, w, "toggle_module_registry_empty", 6, "No modules found right now.");
         cy += 82;
      } else {
         cy = this.renderToggleModuleCatalogSection(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "Riptide Modules",
            "toggle_module_riptide_registry",
            this.riptideModuleNames,
            filteredRiptide,
            hasExternalModules ? 3 : 6,
            selected
         );
         if (hasExternalModules) {
            cy = this.renderToggleModuleCatalogSection(
               ctx, x, cy, w, mx, my, "Meteor Modules", "toggle_module_meteor_registry", this.meteorModuleNames, filteredMeteor, 3, selected
            );
         }
      }

      String hint = selected.isEmpty()
         ? "Pick modules below, then click the mode chip on each row."
         : "Each row runs with its own Toggle / Enable / Disable setting.";
      this.renderEditorHint(ctx, x, cy, w, hint);
   }

   private int renderToggleModuleCatalogSection(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String title,
      String listKey,
      List<String> source,
      List<String> filtered,
      int visibleRows,
      List<ToggleModuleAction.ModuleEntry> selected
   ) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, title, font, RiptideColors.textSecondary(), x, y + 1, false);
      y += 12;
      if (source != null && !source.isEmpty()) {
         this.renderSearchRegistryList(
            ctx,
            x,
            y,
            w,
            mx,
            my,
            listKey,
            filtered,
            visibleRows,
            this::addToggleModuleEntry,
            value -> this.containsModuleEntry(selected, value),
            value -> value
         );
      } else {
         this.renderRegistryPlaceholder(ctx, x, y, w, listKey, visibleRows, "No modules found right now.");
      }

      return y + visibleRows * 13 + 4;
   }

   private void renderRegistryPlaceholder(GuiGraphicsExtractor ctx, int x, int y, int w, String listKey, int visibleRows, String message) {
      int listH = visibleRows * 13;
      int itemW = w - 5 - 1;
      CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
      CompactListRenderer.drawEmptyState(ctx, this.textRenderer, this.formatEditorHint(message), x, y, itemW);
   }

   private void refreshPayScannedPlayers() {
      this.payScannedPlayers.clear();
      this.payPlayerPrefixes.clear();
      this.payPrefixComponents.clear();
      this.payPlayerScanPerformed = true;
      DirectScrollViewport vp = this.catalogScrollViewports.get("pay_players_scan");
      if (vp != null) {
         vp.jumpTo(0);
      }

      for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(MC)) {
         if (player != null && player.name() != null && !player.name().isBlank() && !this.containsIgnoreCase(this.payScannedPlayers, player.name())) {
            this.payScannedPlayers.add(player.name());
            if (player.hasPrefix()) {
               this.payPlayerPrefixes.put(player.name().toLowerCase(Locale.ROOT), player.prefix());
               if (player.prefixComponent() != null) {
                  this.payPrefixComponents.putIfAbsent(player.prefix(), player.prefixComponent());
               }
            }
         }
      }

      RiptideBackgroundTasks.runTracked("pay-scan-probe", () -> {
         List<String> probed = RiptidePlayerProbe.everyone(MC, true);
         if (!probed.isEmpty()) {
            MC.execute(() -> {
               boolean changed = false;

               for (String name : probed) {
                  if (!this.containsIgnoreCase(this.payScannedPlayers, name)) {
                     this.payScannedPlayers.add(name);
                     this.payPlayerPrefixes.putIfAbsent(name.toLowerCase(Locale.ROOT), "unknown");
                     changed = true;
                  }
               }

               if (changed) {
                  this.payScannedPlayers.sort(String::compareToIgnoreCase);
               }
            });
         }
      });
      this.payScannedPlayers.sort(String::compareToIgnoreCase);
   }

   private String payPrefixFor(String name) {
      return name == null ? "" : this.payPlayerPrefixes.getOrDefault(name.toLowerCase(Locale.ROOT), "");
   }

   private void togglePayRankSelection(List<String> selected, List<String> members) {
      if (selected != null && members != null && !members.isEmpty()) {
         boolean allSelected = members.stream().allMatch(mx -> this.containsIgnoreCase(selected, mx));
         if (allSelected) {
            for (String m : members) {
               this.removeStringIgnoreCase(selected, m);
            }
         } else {
            for (String m : members) {
               if (!this.containsIgnoreCase(selected, m)) {
                  selected.add(m);
               }
            }
         }
      }
   }

   private void refreshMeteorModuleNames() {
      this.riptideModuleNames.clear();
      this.meteorModuleNames.clear();
      DirectScrollViewport riptideVp = this.catalogScrollViewports.get("toggle_module_riptide_registry");
      if (riptideVp != null) {
         riptideVp.jumpTo(0);
      }

      DirectScrollViewport meteorVp = this.catalogScrollViewports.get("toggle_module_meteor_registry");
      if (meteorVp != null) {
         meteorVp.jumpTo(0);
      }

      DirectScrollViewport emptyVp = this.catalogScrollViewports.get("toggle_module_registry_empty");
      if (emptyVp != null) {
         emptyVp.jumpTo(0);
      }

      for (String moduleName : RiptideCompatManager.getRiptideModuleNames()) {
         if (moduleName != null && !moduleName.isBlank() && !this.containsIgnoreCase(this.riptideModuleNames, moduleName)) {
            this.riptideModuleNames.add(moduleName);
         }
      }

      for (String moduleNamex : RiptideCompatManager.getMeteorOnlyModuleNames()) {
         if (moduleNamex != null && !moduleNamex.isBlank() && !this.containsIgnoreCase(this.meteorModuleNames, moduleNamex)) {
            this.meteorModuleNames.add(moduleNamex);
         }
      }

      this.riptideModuleNames.sort(String::compareToIgnoreCase);
      this.meteorModuleNames.sort(String::compareToIgnoreCase);
   }

   private List<String> filterEntries(List<String> source, String query) {
      return this.filterEntries(null, source, query);
   }

   private List<String> filterRegistryValues(String cacheKey, List<String> source, String query, Function<String, String> labeler) {
      String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      List<String> target = this.catalogFilteredValues.computeIfAbsent(cacheKey, ignored -> new ArrayList<>());
      return MacroTypedListControl.refilterValues(source, target, id -> id != null && matchesListFilter(filter, id, trimMinecraftPrefix(id), labeler.apply(id)));
   }

   private List<String> filterEntries(String cacheKey, List<String> source, String query) {
      if (source != null && !source.isEmpty()) {
         String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
         List<String> target = (List<String>)(cacheKey == null
            ? new ArrayList<>()
            : this.catalogFilteredValues.computeIfAbsent(cacheKey, ignored -> new ArrayList<>()));
         return MacroTypedListControl.refilterValues(
            source, target, value -> value != null && (filter.isEmpty() || value.toLowerCase(Locale.ROOT).contains(filter))
         );
      } else {
         return Collections.emptyList();
      }
   }

   private void addFilteredPayPlayers(List<String> selected, String query) {
      if (selected != null) {
         for (String player : this.filterEntries(this.payScannedPlayers, query)) {
            if (!this.containsIgnoreCase(selected, player)) {
               selected.add(player);
            }
         }
      }
   }

   private void togglePayPlayerSelection(List<String> selected, String player) {
      if (selected != null && player != null && !player.isBlank()) {
         if (this.containsIgnoreCase(selected, player)) {
            this.removeStringIgnoreCase(selected, player);
         } else {
            selected.add(player);
         }
      }
   }

   private boolean containsIgnoreCase(List<String> values, String target) {
      if (values != null && target != null) {
         for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void removeStringIgnoreCase(List<String> values, String target) {
      if (values != null && target != null) {
         values.removeIf(value -> value != null && value.equalsIgnoreCase(target));
      }
   }

   private void addFirstFilteredModule(String query) {
      List<String> filtered = this.filterEntries(this.riptideModuleNames, query);
      if (filtered.isEmpty()) {
         filtered = this.filterEntries(this.meteorModuleNames, query);
      }

      if (!filtered.isEmpty()) {
         this.addToggleModuleEntry(filtered.get(0));
      }
   }

   private void addToggleModuleEntry(String moduleName) {
      if (this.toggleModuleEntries != null && moduleName != null && !moduleName.isBlank() && !this.containsModuleEntry(this.toggleModuleEntries, moduleName)) {
         this.toggleModuleEntries.add(new ToggleModuleAction.ModuleEntry(moduleName, ToggleModuleAction.ToggleMode.TOGGLE));
      }
   }

   private boolean containsModuleEntry(List<ToggleModuleAction.ModuleEntry> entries, String moduleName) {
      if (entries != null && moduleName != null) {
         for (ToggleModuleAction.ModuleEntry entry : entries) {
            if (entry != null && entry.moduleName != null && entry.moduleName.equalsIgnoreCase(moduleName)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private String formatToggleModeShort(ToggleModuleAction.ToggleMode mode) {
      return switch (mode) {
         case ENABLE -> "Enable";
         case DISABLE -> "Disable";
         default -> "Toggle";
      };
   }

   private void renderWaitSlotChangePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      int headerBtnW = 44;
      this.renderInlineToggle(ctx, font, "listenDuringPreviousAction", "Listen During Previous", x, cy, w, mx, my);
      cy += 17;
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      List<WaitForSlotChangeAction.WaitEntry> entries = (List<WaitForSlotChangeAction.WaitEntry>)(this.wscEntries != null ? this.wscEntries : new ArrayList<>());
      if (this.wscEditIndex >= entries.size()) {
         this.wscEditIndex = -1;
      }

      UiText.draw(ctx, this.textRenderer, "Items / Slots (" + entries.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      boolean canClear = !entries.isEmpty();
      int clearX = x + w - headerBtnW;
      this.renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
         entries.clear();
         this.wscEditIndex = -1;
         this.clearWscEditorFields();
      });
      cy += 14;
      int delW = 13;
      int rowW = w - 5 - 1 - delW - 2;
      int selAreaH = 60;
      DirectScrollViewport wscViewport = this.getOrCreateViewport(this.selectedScrollViewports, "_wsc_entries", x, cy, w, selAreaH, 15, 5);
      wscViewport.setContentHeight(entries.size() * 15);
      wscViewport.renderScrollbar(ctx, mx, my);
      if (!entries.isEmpty()) {
         wscViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int firstVis = wscViewport.getFirstVisibleRow();

         for (int i = firstVis; i < entries.size() && i <= wscViewport.getLastVisibleRow(); i++) {
            int iy = wscViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               boolean selected = i == this.wscEditIndex;
               WaitForSlotChangeAction.WaitEntry e = entries.get(i);
               Component targetDisp = this.formatItemTargetText(e.resolvedTarget(), "(any slot)");
               String modeSummary = " • " + e.modeLabel();
               Component rowLabel = Component.empty().append(targetDisp).append(Component.literal(modeSummary).withStyle(s -> s.withColor(-7829368)));
               boolean rowHovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
               MacroTypedListControl.renderRow(
                  ctx, this.textRenderer, rowLabel, UiBounds.of(x, iy, rowW, 13), rowHovered, selected, CompactListRenderer.RowTone.NORMAL, true
               );
               int fi = i;
               if (iy + 13 > cy && iy < cy + selAreaH) {
                  this.hitRegions
                     .add(
                        new ActionEditorOverlay.HitRegion(
                           x, Math.max(cy, iy), rowW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> this.toggleWscEditSelection(fi)
                        )
                     );
               }

               int delX = x + rowW + 2;
               this.renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                  if (this.wscEditIndex == fi) {
                     this.wscEditIndex = -1;
                     this.clearWscEditorFields();
                  } else if (this.wscEditIndex > fi) {
                     this.wscEditIndex--;
                  }

                  entries.remove(fi);
                  DirectScrollViewport vp = this.selectedScrollViewports.get("_wsc_entries");
                  if (vp != null) {
                     vp.scrollBy(-1);
                  }
               });
            }
         }

         wscViewport.endRender(ctx);
      } else {
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No entries yet. Add an item or slot below.", x, cy, w - 5 - 1);
      }

      cy += selAreaH;
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 6;
      RiptideChatField wscAddF = this.addFields.get("_wsc_add");
      RiptideChatField wscSlotF = this.textFields.get("wsc_slot");
      if (wscAddF != null && wscSlotF != null) {
         int pickW = 32;
         int slotW = 44;
         int pickX = x + w - pickW;
         int slotX = pickX - 2 - slotW;
         wscAddF.setX(x);
         wscAddF.setY(cy + 1);
         wscAddF.setWidth(slotX - x - 2);
         wscSlotF.setX(slotX);
         wscSlotF.setY(cy + 1);
         wscSlotF.setWidth(slotW);
         wscAddF.render(ctx, mx, my, delta);
         wscSlotF.render(ctx, mx, my, delta);
         boolean capturing = this.captureSession.isItemSlotCapture("_wsc_entries");
         this.renderFieldCaptureButton(
            ctx, pickX, cy, pickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("_wsc_entries")
         );
      }

      cy += 16;
      boolean editing = this.wscEditIndex >= 0 && this.wscEditIndex < entries.size();
      WaitForSlotChangeAction.WaitMode curMode = editing ? entries.get(this.wscEditIndex).waitMode : this.wscAddMode;
      int curCount = editing ? entries.get(this.wscEditIndex).targetCount : this.wscAddCount;
      int addBtnW = 34;
      int cntW = 36;
      int modBtnW = w - addBtnW - 2 - cntW - 2;
      WaitForSlotChangeAction.WaitMode[] modes = WaitForSlotChangeAction.WaitMode.values();
      List<String> modeOptions = new ArrayList<>();

      for (WaitForSlotChangeAction.WaitMode m : modes) {
         modeOptions.add(m.name().replace("_", " "));
      }

      this.renderInlineDropdown("wsc_mode", modeOptions, curMode.ordinal(), x, cy, modBtnW, 14, idx -> {
         WaitForSlotChangeAction.WaitMode chosen = modes[Math.max(0, Math.min(idx, modes.length - 1))];
         if (this.wscEditIndex >= 0 && this.wscEditIndex < entries.size()) {
            entries.get(this.wscEditIndex).waitMode = chosen;
         } else {
            this.wscAddMode = chosen;
         }
      });
      int cntX = x + modBtnW + 2;
      RiptideChatField countF = this.textFields.get("wsc_count");
      boolean countRelevant = curMode == WaitForSlotChangeAction.WaitMode.COUNT_AT_LEAST || curMode == WaitForSlotChangeAction.WaitMode.COUNT_BELOW;
      if (countF != null) {
         if (countRelevant) {
            countF.setX(cntX);
            countF.setY(cy + 1);
            countF.setWidth(cntW);
            if (!countF.isFocused() && !this.suppressWscLiveUpdate) {
               this.suppressWscLiveUpdate = true;
               countF.setText(String.valueOf(curCount));
               this.suppressWscLiveUpdate = false;
            }

            countF.render(ctx, mx, my, delta);
         } else {
            CompactSurfaces.valueField(ctx, cntX, cy, cntW, 14);
            this.fillBorder(ctx, cntX, cy, cntW, 14, -14018022);
            UiText.draw(ctx, this.textRenderer, "-", font, RiptideColors.textDim(), cntX + (cntW - this.uiWidth(font, "-")) / 2, cy + 3, false);
         }
      }

      int plusX = cntX + cntW + 2;
      this.renderOverlayButton(ctx, plusX, cy, addBtnW, 14, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
         if (wscAddF != null && wscSlotF != null) {
            this.applyWscAddEntry(wscAddF, wscSlotF);
            this.wscEditIndex = -1;
            this.clearWscEditorFields();
         }
      });
      cy += 16;
      this.renderEditorHint(ctx, x, cy, w, this.wscEditIndex >= 0 ? "Editing row - all entries must match." : "Click a row to edit - all must match.");
   }

   private void renderUseItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      cy = this.renderUseItemTargetRow(ctx, x, cy, w, mx, my, delta);
      int btnW = (w - 4) / 2;
      this.renderActionButton(ctx, x, cy, btnW, 14, "Use Held", mx, my, this::fillUseItemFromHeld);
      this.renderActionButton(ctx, x + btnW + 4, cy, btnW, 14, "Use Current", mx, my, () -> {
         RiptideChatField field = this.textFields.get("itemName");
         if (field != null) {
            field.setText("");
         }

         RiptideChatField slotField = this.textFields.get("slot");
         if (slotField != null) {
            slotField.setText("");
         }

         this.editorItemFields.remove("itemName");
      });
      cy += 18;
      cy = this.renderFieldByKey(ctx, "useMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForFinish", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "sneak", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "holdTicks", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "useCount", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGui", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);
      String itemTarget = this.currentUseItemTargetLabel();
      String mode = this.currentEnumValue("useMode");
      String hint = "CUSTOM_HOLD".equals(mode) ? "Hold-uses " + itemTarget + " for the set ticks." : "Uses " + itemTarget + " for the set count.";
      this.renderEditorHint(ctx, x, cy, w, hint);
   }

   private int renderUseItemTargetRow(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
      FieldDef itemFieldDef = this.getField("itemName");
      RiptideChatField itemField = this.textFields.get("itemName");
      RiptideChatField slotField = this.textFields.get("slot");
      if (itemFieldDef != null && itemField != null && slotField != null) {
         Identifier font = this.theme.fontFor(UiTone.BODY);
         int labelW = this.labelWidth(w, itemFieldDef.label(), font, 88);
         this.drawLabel(ctx, itemFieldDef.label(), x, y, labelW, font);
         int controlX = this.controlX(x, labelW);
         int controlW = Math.max(1, this.controlWidth(w, labelW));
         int pickW = 30;
         int slotW = 34;
         int gap = 2;
         int itemW = Math.max(1, controlW - pickW - slotW - gap * 2);
         itemField.setX(controlX);
         itemField.setY(y + 2);
         itemField.setWidth(itemW);
         itemField.render(ctx, mx, my, delta);
         int slotX = controlX + itemW + gap;
         slotField.setX(slotX);
         slotField.setY(y + 2);
         slotField.setWidth(slotW);
         slotField.render(ctx, mx, my, delta);
         int pickX = slotX + slotW + gap;
         boolean capturing = this.captureSession.isItemSlotCapture("itemName");
         this.renderFieldCaptureButton(
            ctx, pickX, y + 2, pickW, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, () -> this.toggleItemSlotCapture("itemName")
         );
         return y + 15 + 2;
      } else {
         int cy = this.renderFieldByKey(ctx, "itemName", x, y, w, mx, my, delta);
         return this.renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
      }
   }

   private void renderRotatePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "yaw", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "pitch", x, cy, w, mx, my, delta);
      this.renderActionButton(ctx, x, cy, w, 14, "Capture View", mx, my, this::fillRotateFromCurrentView);
      cy += 18;
      cy = this.renderFieldByKey(ctx, "smooth", x, cy, w, mx, my, delta);
      if (this.toggleStates.getOrDefault("smooth", false)) {
         cy = this.renderRotateSmoothnessSlider(ctx, x, cy, w, mx, my);
      } else {
         this.rotateSmoothnessSliderDragging = false;
         this.clearRotateSmoothnessSliderBounds();
      }

      cy = this.renderFieldByKey(ctx, "waitForCompletion", x, cy, w, mx, my, delta);
      this.renderEditorHint(
         ctx,
         x,
         cy,
         w,
         this.toggleStates.getOrDefault("smooth", false)
            ? "Capture View fills in your current yaw and pitch. Smoothness 1 is faster, 10 is slower."
            : "Capture View fills in your current yaw and pitch."
      );
   }

   private void renderLookAtPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "targetMode", x, cy, w, mx, my, delta);
      String mode = this.currentEnumValue("targetMode");
      if ("BLOCK".equals(mode)) {
         cy = this.renderFieldByKey(ctx, "searchRadius", x, cy, w, mx, my, delta);
         cy = this.renderFieldByKey(ctx, "blockIds", x, cy, w, mx, my, delta);
      } else if ("ENTITY".equals(mode)) {
         cy = this.renderFieldByKey(ctx, "searchRadius", x, cy, w, mx, my, delta);
         cy = this.renderLookAtEntitySelector(ctx, x, cy, w, mx, my, delta);
      } else {
         cy = this.renderFieldByKey(ctx, "blockPos", x, cy, w, mx, my, delta);
      }

      cy = this.renderFieldByKey(ctx, "smooth", x, cy, w, mx, my, delta);
      if (this.toggleStates.getOrDefault("smooth", false)) {
         cy = this.renderRotateSmoothnessSlider(ctx, x, cy, w, mx, my);
      } else {
         this.rotateSmoothnessSliderDragging = false;
         this.clearRotateSmoothnessSliderBounds();
      }

      cy = this.renderFieldByKey(ctx, "waitForCompletion", x, cy, w, mx, my, delta);

      String hint = switch (mode) {
         case "BLOCK" -> "Search blocks and it faces the nearest matching block in range.";
         case "ENTITY" -> "Pick entity types or specific captures and it faces the nearest match in range.";
         default -> "Pick a specific block position to face.";
      };
      if (this.toggleStates.getOrDefault("smooth", false)) {
         hint = hint + " Smoothness 1 is faster, 10 is slower.";
      }

      this.renderEditorHint(ctx, x, cy, w, hint);
   }

   private int renderLookAtEntitySelector(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      List<String> selected = this.stringLists.getOrDefault("entityIds", Collections.emptyList());
      int cy = this.renderSimpleSelectedList(
         ctx,
         x,
         y,
         w,
         mx,
         my,
         "look_at_entity_selected",
         "Entities",
         selected,
         selected::remove,
         this::formatEntityEntry,
         "(add entity types or specific captures)"
      );
      RiptideChatField search = this.addFields.get("entityIds");
      int searchW = w - 66;
      if (search != null) {
         search.setX(x);
         search.setY(cy + 1);
         search.setWidth(searchW);
         search.render(ctx, mx, my, delta);
      }

      int modeX = x + searchW + 2;
      String modeLabel = this.entitySpecificCaptureMode ? "[Spec]" : "[Type]";
      this.renderActionButton(ctx, modeX, cy, 30, 14, modeLabel, mx, my, () -> this.entitySpecificCaptureMode = !this.entitySpecificCaptureMode);
      this.renderActionButton(ctx, modeX + 34, cy, 32, 14, "CAP", mx, my, this::startLookAtEntityCapture);
      cy += 18;
      String filter = search != null ? search.getText().trim().toLowerCase() : "";
      List<String> filtered = this.filterRegistryValues("look_at_entity_search", getAllEntityIds(), filter, RiptideRegistryLabels::entity);
      this.renderSearchRegistryList(ctx, x, cy, w, mx, my, "look_at_entity_search", filtered, 4, value -> {
         if (selected.contains(value)) {
            selected.remove(value);
         } else {
            selected.add(value);
         }
      }, selected::contains, RiptideRegistryLabels::entity);
      cy += 66;
      UiText.draw(ctx, this.textRenderer, "Nearby Entities", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 13;
      List<String> nearbyEntries = this.getNearbyEntityEntries();
      this.renderSearchRegistryList(
         ctx,
         x,
         cy,
         w,
         mx,
         my,
         "look_at_entity_nearby",
         nearbyEntries,
         3,
         value -> {
            String stored = this.entitySpecificCaptureMode ? value : this.extractEntityTypeFromNearbyEntry(value);
            if (!stored.isBlank()) {
               if (this.entitySpecificCaptureMode) {
                  boolean removed = selected.removeIf(existing -> existing.startsWith(this.entitySpecificEntryPrefix(stored)));
                  if (!removed) {
                     selected.add(stored);
                  }
               } else if (selected.contains(stored)) {
                  selected.remove(stored);
               } else {
                  selected.add(stored);
               }
            }
         },
         value -> {
            String stored = this.entitySpecificCaptureMode ? value : this.extractEntityTypeFromNearbyEntry(value);
            return this.entitySpecificCaptureMode
               ? selected.stream().anyMatch(existing -> existing.startsWith(this.entitySpecificEntryPrefix(stored)))
               : selected.contains(stored);
         },
         this::formatNearbyEntityEntry
      );
      return cy + 39 + 4;
   }

   private void renderGoToPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      FieldDef posField = this.getField("pos");
      if (posField != null) {
         cy = this.renderBlockPosWithoutCapture(ctx, posField, x, cy, w, mx, my, delta);
      }

      this.renderActionButton(ctx, x, cy, w, 14, "Capture Here", mx, my, this::fillGoToFromPlayer);
      cy += 18;
      cy = this.renderFieldByKey(ctx, "waitForArrival", x, cy, w, mx, my, delta);
      this.renderEditorHint(ctx, x, cy, w, "Wait for Arrival continues only after Baritone finishes.");
   }

   private void renderSwapSlotsPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      UiText.draw(ctx, this.textRenderer, "From", font, RiptideColors.textSecondary(), x, cy + 2, false);
      this.renderActionButton(ctx, x + w - 68, cy, 68, 14, "Flip Ends", mx, my, this::swapSwapSlotEndpoints);
      cy += 16;
      cy = this.renderFieldByKey(ctx, "fromUseItemName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "fromItemName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "fromSlot", x, cy, w, mx, my, delta);
      CompactSurfaces.divider(ctx, x, cy + 2, w, RiptideColors.subPanelBorder());
      cy += 8;
      UiText.draw(ctx, this.textRenderer, "To", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 16;
      cy = this.renderFieldByKey(ctx, "toUseItemName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "toItemName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "toSlot", x, cy, w, mx, my, delta);
      this.renderEditorHint(ctx, x, cy, w, this.buildSwapSlotsSummary());
   }

   private void renderClickPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "clickType", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "clickCount", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);
      this.renderEditorHint(ctx, x, cy, w, this.buildClickSummary());
   }

   private void renderDisconnectPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "delayMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "lagMethod", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "kickMethod", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "packetCount", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "useNextAction", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "trigger", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "tolerance", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "bufferMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "timeoutSec", x, cy, w, mx, my, delta);
      String mode = this.currentEnumValue("mode");

      String hint = switch (mode) {
         case "DISCONNECT" -> "Simple disconnect. Delay only controls how long to wait before closing the connection.";
         case "KICK" -> "Sends lag packets, then the selected kick packet. Packet Count controls how hard it pushes.";
         case "AUTO_DISCONNECT" -> "Auto-disconnects at the perfect dupe timing.";
         case "KICK_DUPE" -> this.toggleStates.getOrDefault("useNextAction", false)
            ? "Kick Dupe will run the next eligible macro actions inside the lag sandwich, then kick."
            : "Kick Dupe will try to use a bundle, then kick.";
         default -> "Unknown mode";
      };
      this.renderEditorHint(ctx, x, cy, w, hint, "DISCONNECT".equals(mode) ? RiptideColors.textDim() : -19605);
   }

   private void renderWaitGuiPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "waitMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "guiType", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "matchMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "guiTitle", x, cy, w, mx, my, delta);
      cy = this.renderCapturePatternBuilder(ctx, this.textFields.get("guiTitle"), x, cy, w, mx, my);
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);
      String mode = this.currentEnumValue("waitMode");
      String guiType = this.currentEnumValue("guiType");
      String hint = "CLOSE".equals(mode) ? "Waits until the matching " + guiType + " GUI closes." : "Waits until a matching " + guiType + " GUI opens.";
      this.renderEditorHint(ctx, x, cy, w, hint);
   }

   private void renderSelectSlotPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);
      int btnW = (w - 4) / 2;
      this.renderActionButton(ctx, x, cy, btnW, 14, "Held", mx, my, this::fillSelectSlotFromHeld);
      this.renderActionButton(ctx, x + btnW + 4, cy, btnW, 14, "Use Slot Only", mx, my, this::clearSelectSlotItemName);
      cy += 18;
      UiText.draw(ctx, this.textRenderer, "Fallback Hotbar Slot", font, RiptideColors.textSecondary(), x, cy + 2, false);
      cy += 12;
      cy = this.renderSelectSlotHotbarPicker(ctx, x, cy, w, mx, my);
      cy = this.renderFieldByKey(ctx, "strategy", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "outputVariable", x, cy, w, mx, my, delta);
      this.renderEditorHint(ctx, x, cy, w, this.buildSelectSlotSummary());
   }

   private int renderSelectSlotHotbarPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int selectedSlot = this.getSelectSlotHotbarIndex();
      int slotGap = 2;
      int cellW = Math.max(16, Math.min(24, (w - slotGap * 8) / 9));
      int totalW = cellW * 9 + slotGap * 8;
      int startX = x + Math.max(0, (w - totalW) / 2);

      for (int slot = 0; slot < 9; slot++) {
         int sx = startX + slot * (cellW + slotGap);
         boolean selected = selectedSlot == slot;
         String label = String.valueOf(slot + 1);
         int clickedSlot = slot;
         this.renderOverlayButton(
            ctx,
            sx,
            y,
            cellW,
            14,
            label,
            selected ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
            true,
            mx,
            my,
            () -> this.setSelectSlotHotbarIndex(clickedSlot)
         );
      }

      return y + 18;
   }

   private void renderWaitCooldownPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);
      int halfW = (w - 4) / 2;
      this.renderActionButton(ctx, x, cy, halfW, 14, this.currentWaitCooldownHandLabel(), mx, my, this::toggleWaitCooldownHand);
      this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Capture Held", mx, my, this::fillWaitCooldownFromHeld);
      cy += 18;
      this.renderActionButton(ctx, x, cy, Math.min(130, w), 14, "Use InteractionHand Item", mx, my, this::clearWaitCooldownItemName);
      cy += 18;
      this.renderEditorHint(ctx, x, cy, w, this.buildWaitCooldownSummary());
   }

   private void renderWaitInventoryPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "listenDuringPreviousAction", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "condition", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "count", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
      if (this.isFieldVisible(this.getField("slot"))) {
         int third = (w - 4) / 3;
         this.renderActionButton(ctx, x, cy, third, 14, "Selected", mx, my, this::fillWaitInventorySelectedSlot);
         boolean capturing = this.captureSession.isItemSlotCapture("wait_inventory_slot");
         this.renderFieldCaptureButton(
            ctx, x + third + 2, cy, third, 14, CaptureMode.ITEM_SLOT, capturing, true, mx, my, "Pick Slot", "Done", this::toggleWaitInventorySlotCapture
         );
         this.renderActionButton(ctx, x + (third + 2) * 2, cy, w - (third + 2) * 2, 14, "Slot 0", mx, my, () -> this.setWaitInventorySlot(0));
         cy += 18;
      }

      cy = this.renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
   }

   private void renderCaptureValuePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      this.applyCaptureSourceWaitDefault();
      cy = this.renderFieldByKey(ctx, "source", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "autofillCommand", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "autofillTimeoutMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "autofillCacheList", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "itemFilter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "itemText", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listSelection", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listPickPosition", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listFilter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listFilterText", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listExcludeText", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "listStripPrefix", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "excludeSelf", x, cy, w, mx, my, delta);
      if ("RECENT_CHAT".equals(this.currentEnumValue("source"))) {
         cy = this.renderCaptureRecentChatPicker(ctx, x, cy, w, mx, my);
      } else if ("SCOREBOARD".equals(this.currentEnumValue("source"))) {
         cy = this.renderCaptureScoreboardPicker(ctx, x, cy, w, mx, my);
      } else if ("TABLIST".equals(this.currentEnumValue("source"))) {
         cy = this.renderCaptureTablistMatches(ctx, x, cy, w, mx, my);
      }

      cy = this.renderFieldByKey(ctx, "exampleText", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "selectedText", x, cy, w, mx, my, delta);
      CaptureValueAction draft = this.captureValueDraft();
      String primaryName = this.captureValuePrimaryName(draft);
      String token = primaryName.isBlank() ? "" : "{" + primaryName + "}";
      boolean exampleCapture = draft.source == CaptureValueAction.Source.RECENT_CHAT
         || draft.source == CaptureValueAction.Source.GUI_TITLE
         || draft.source == CaptureValueAction.Source.SCOREBOARD;
      CaptureValueAction.Preview preview = exampleCapture && !draft.exampleText.isBlank() ? draft.previewExample() : draft.preview(MC);
      String previewText = preview.success() ? preview.value(primaryName) : preview.message();
      if (preview.success() && previewText.isBlank()) {
         previewText = preview.sourceText();
      }

      boolean derivingPattern = exampleCapture && !draft.exampleText.isBlank() && draft.matchMode != MacroCapturePattern.Mode.MATCH;
      if (derivingPattern && draft.selectedText.isBlank()) {
         previewText = "Set Capture This";
      } else if (derivingPattern && draft.pattern.isBlank()) {
         previewText = "Not in example";
      }

      cy = this.renderCaptureCopyField(ctx, "Use Later", "_capture_token", token, x, cy, w, mx, my, "Variable copied.");
      cy = this.renderCaptureCopyField(ctx, "Preview Now", "_capture_preview", previewText, x, cy, w, mx, my, "Preview copied.");
      cy = this.renderFieldByKey(ctx, "numberMode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "numberModifier", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "numberModifierAmount", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForTrigger", x, cy, w, mx, my, delta);
      String behaviorHint;
      if (draft.source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
         behaviorHint = "Asks server when run";
      } else if (draft.source == CaptureValueAction.Source.TABLIST) {
         behaviorHint = draft.waitForTrigger ? "Waits for player" : "Picks online player";
      } else {
         behaviorHint = draft.waitForTrigger
            ? (draft.source == CaptureValueAction.Source.SCOREBOARD ? "Waits for row change" : "Waits for next match")
            : (
               draft.source != CaptureValueAction.Source.RECENT_CHAT && draft.source != CaptureValueAction.Source.GUI_TITLE
                  ? "Reads value now"
                  : "Uses newest match"
            );
      }

      if (!draft.saveAs.isBlank() && MacroVariableContext.cleanRootName(draft.saveAs).isBlank()) {
         behaviorHint = "Bad variable name";
      }

      this.renderEditorHint(ctx, x, cy, w, behaviorHint);
   }

   private int renderCaptureRecentChatPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      List<MacroExecutor.RecentChatMessage> history = MacroExecutor.getRecentChatMessages().stream().limit(6L).toList();
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, "Recent Chat", font, RiptideColors.textSecondary(), x, y + 2, false);
      y += 13;
      int listH = this.captureChatHistoryListHeight(history.size());
      this.renderRecentChatHistoryList(ctx, x, y, w, listH, mx, my, history, "capture_value_recent", 3, this::applyCaptureValueChatHistoryEntry);
      return y + listH + 2;
   }

   private void applyCaptureValueChatHistoryEntry(MacroExecutor.RecentChatMessage entry) {
      if (entry != null) {
         String text = this.formatWaitChatHistoryEntry(entry);
         String suggested = CaptureValueAction.suggestDynamicPart(text);
         RiptideChatField exampleField = this.textFields.get("exampleText");
         RiptideChatField selectedField = this.textFields.get("selectedText");
         this.suppressCaptureValueAutoPattern = true;
         if (exampleField != null) {
            exampleField.setText(text);
         }

         if (selectedField != null) {
            selectedField.setText(suggested);
         }

         this.suppressCaptureValueAutoPattern = false;
         this.lastGeneratedCapturePattern = this.editorFieldText("pattern", "");
         this.refreshCaptureValueAutoPattern();
      }
   }

   private int renderCaptureScoreboardPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      List<CaptureValueAction.ScoreboardLine> lines = CaptureValueAction.scoreboardLines(MC);
      Identifier font = this.theme.fontFor(UiTone.BODY);
      String title = !lines.isEmpty() && !lines.get(0).objectiveTitle().isBlank() ? "Scoreboard - " + lines.get(0).objectiveTitle() : "Scoreboard";
      UiText.draw(ctx, this.textRenderer, UiText.trimToWidth(this.textRenderer, title, w, font, -1), font, RiptideColors.textSecondary(), x, y + 2, false);
      y += 13;
      int listH = lines.isEmpty() ? 24 : Math.min(5, lines.size()) * 22;
      int itemW = Math.max(1, w - 5 - 1);
      DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, "capture_value_scoreboard", x, y, w, listH, 22, 5);
      viewport.setContentHeight(lines.size() * 22);
      viewport.renderScrollbar(ctx, mx, my);
      if (lines.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
         UiText.draw(ctx, this.textRenderer, "No scoreboard", font, RiptideColors.textDim(), x + 3, y + 7, false);
         return y + 24 + 2;
      } else {
         String selectedKey = this.editorFieldText("scoreboardRow", "");
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = viewport.getFirstVisibleRow();

         for (int i = first; i < lines.size() && i <= viewport.getLastVisibleRow(); i++) {
            int rowY = viewport.getRowScreenY(i);
            if (rowY != Integer.MIN_VALUE) {
               CaptureValueAction.ScoreboardLine line = lines.get(i);
               boolean selected = line.key().equals(selectedKey);
               boolean hovered = mx >= x && mx < x + itemW && my >= rowY && my < rowY + 22;
               CompactSurfaces.row(ctx, x, rowY, itemW, 22, hovered, selected);
               CompactSurfaces.indicator(ctx, x, rowY + 2, 3, 18, selected ? -13248397 : -3376606);
               String rowLabel = "Row " + (line.row() + 1);
               UiText.draw(ctx, this.textRenderer, rowLabel, font, RiptideColors.textDim(), x + 6, rowY + 2, false);
               UiText.draw(
                  ctx,
                  this.textRenderer,
                  UiText.trimToWidth(this.textRenderer, line.text(), Math.max(20, itemW - 10), font, -1),
                  font,
                  RiptideColors.textPrimary(),
                  x + 6,
                  rowY + 11,
                  false
               );
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, rowY, itemW, 22, () -> this.applyCaptureValueScoreboardLine(line)));
            }
         }

         viewport.endRender(ctx);
         return y + listH + 2;
      }
   }

   private List<String> captureTablistNames() {
      long now = System.currentTimeMillis();
      if (this.captureTablistNamesCache == null || now - this.captureTablistNamesAtMs > 500L) {
         List<RiptidePlayerScanner.ScannedPlayer> players = RiptidePlayerScanner.scan(MC);
         List<String> names = new ArrayList<>(players.size());

         for (RiptidePlayerScanner.ScannedPlayer player : players) {
            names.add(player.name());
         }

         this.captureTablistNamesCache = names;
         this.captureTablistNamesAtMs = now;
      }

      return this.captureTablistNamesCache;
   }

   private List<String> captureTablistAllNames(CaptureValueAction draft) {
      List<String> names = new ArrayList<>(this.captureTablistNames());
      if (!draft.excludeSelf && MC.player != null && MC.player.getGameProfile() != null) {
         names.add(MC.player.getGameProfile().name());
         names.sort(String.CASE_INSENSITIVE_ORDER);
      }

      return names;
   }

   private int captureTablistMatchCount() {
      CaptureValueAction draft = this.captureValueDraft();
      return draft.filterCandidates(this.captureTablistAllNames(draft)).size();
   }

   private int renderCaptureTablistMatches(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      CaptureValueAction draft = this.captureValueDraft();
      List<String> names = this.captureTablistAllNames(draft);
      List<String> pool = draft.filterCandidates(names);
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, "Matches (" + pool.size() + " of " + names.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      y += 13;
      int listH = pool.isEmpty() ? 24 : Math.min(5, pool.size()) * 14;
      int itemW = Math.max(1, w - 5 - 1);
      DirectScrollViewport viewport = this.getOrCreateViewport(this.selectedScrollViewports, "capture_value_tablist", x, y, w, listH, 14, 5);
      viewport.setContentHeight(pool.size() * 14);
      viewport.renderScrollbar(ctx, mx, my);
      if (pool.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
         UiText.draw(ctx, this.textRenderer, "No matches", font, RiptideColors.textDim(), x + 3, y + 7, false);
         return y + 24 + 2;
      } else {
         viewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = viewport.getFirstVisibleRow();

         for (int i = first; i < pool.size() && i <= viewport.getLastVisibleRow(); i++) {
            int rowY = viewport.getRowScreenY(i);
            if (rowY != Integer.MIN_VALUE) {
               String name = pool.get(i);
               boolean hovered = mx >= x && mx < x + itemW && my >= rowY && my < rowY + 14;
               CompactSurfaces.row(ctx, x, rowY, itemW, 14, hovered, false);
               UiText.draw(
                  ctx,
                  this.textRenderer,
                  UiText.trimToWidth(this.textRenderer, name, Math.max(20, itemW - 10), font, -1),
                  font,
                  RiptideColors.textPrimary(),
                  x + 6,
                  rowY + 3,
                  false
               );
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, rowY, itemW, 14, () -> this.copyCaptureEditorValue(name, "Name copied.")));
            }
         }

         viewport.endRender(ctx);
         return y + listH + 2;
      }
   }

   private void applyCaptureValueScoreboardLine(CaptureValueAction.ScoreboardLine line) {
      if (line != null) {
         RiptideChatField rowField = this.textFields.get("scoreboardRow");
         RiptideChatField rowIndexField = this.textFields.get("scoreboardRowIndex");
         RiptideChatField objectiveField = this.textFields.get("scoreboardObjective");
         RiptideChatField exampleField = this.textFields.get("exampleText");
         RiptideChatField selectedField = this.textFields.get("selectedText");
         String suggested = CaptureValueAction.suggestDynamicPart(line.text());
         this.suppressCaptureValueAutoPattern = true;
         if (rowField != null) {
            rowField.setText(line.key());
         }

         if (rowIndexField != null) {
            rowIndexField.setText(Integer.toString(line.row()));
         }

         if (objectiveField != null) {
            objectiveField.setText(line.objective());
         }

         if (exampleField != null) {
            exampleField.setText(line.text());
         }

         if (selectedField != null) {
            selectedField.setText(suggested);
         }

         this.suppressCaptureValueAutoPattern = false;
         this.lastGeneratedCapturePattern = this.editorFieldText("pattern", "");
         this.refreshCaptureValueAutoPattern();
      }
   }

   private String labelFor(FieldDef field) {
      if ("waitForTrigger".equals(field.key()) && this.targetAction instanceof CaptureValueAction) {
         String var2 = this.currentEnumValue("source") == null ? "" : this.currentEnumValue("source");

         return switch (var2) {
            case "RECENT_CHAT", "GUI_TITLE" -> "Wait for New";
            case "SCOREBOARD" -> "Wait for Change";
            default -> "Wait for Match";
         };
      } else {
         return field.label();
      }
   }

   private void applyCaptureSourceWaitDefault() {
      String source = this.currentEnumValue("source");
      if (source != null && !source.equals(this.lastCaptureSource)) {
         boolean firstFrame = this.lastCaptureSource.isEmpty();
         this.lastCaptureSource = source;
         if (!firstFrame && !this.captureWaitTouched) {
            CaptureValueAction.Source parsed;
            try {
               parsed = CaptureValueAction.Source.valueOf(source);
            } catch (IllegalArgumentException var5) {
               return;
            }

            boolean waits = CaptureValueAction.defaultWaitForTrigger(parsed);
            this.toggleStates.put("waitForTrigger", waits);
            if (this.workingTag != null) {
               this.workingTag.putBoolean("waitForTrigger", waits);
            }
         }
      }
   }

   private void refreshCaptureValueAutoPattern() {
      if (!this.suppressCaptureValueAutoPattern && this.targetAction instanceof CaptureValueAction) {
         String source = this.currentEnumValue("source");
         if ("GUI_TITLE".equals(source) || "RECENT_CHAT".equals(source) || "SCOREBOARD".equals(source)) {
            String example = this.editorFieldText("exampleText", "");
            String selected = this.editorFieldText("selectedText", "");
            String variable = this.editorFieldText("saveAs", "value");
            if (this.workingTag != null) {
               this.workingTag.putString("exampleText", example);
               this.workingTag.putString("selectedText", selected);
            }

            RiptideChatField patternField = this.textFields.get("pattern");
            String current = patternField != null ? patternField.getText() : (this.workingTag == null ? "" : this.workingTag.getStringOr("pattern", ""));
            if (current.isBlank() || current.equals(this.lastGeneratedCapturePattern)) {
               if (!example.isBlank() && !selected.isBlank()) {
                  String generated = CaptureValueAction.buildCapturePattern(example, selected, variable);
                  if (!generated.isBlank()) {
                     if (patternField != null) {
                        patternField.setText(generated);
                     }

                     if (this.workingTag != null) {
                        this.workingTag.putString("pattern", generated);
                     }

                     this.lastGeneratedCapturePattern = generated;
                     if (!generated.isBlank()) {
                        this.enumIndices.put("matchMode", 1);
                        if (this.workingTag != null) {
                           this.workingTag.putString("matchMode", "CAPTURE");
                        }
                     }
                  }
               } else {
                  if (patternField != null) {
                     patternField.setText("");
                  }

                  if (this.workingTag != null) {
                     this.workingTag.putString("pattern", "");
                  }

                  this.lastGeneratedCapturePattern = "";
               }
            }
         }
      }
   }

   private int captureChatHistoryListHeight(int count) {
      return count <= 0 ? 24 : Math.min(3, Math.min(6, count)) * 34;
   }

   private void clipHitRegions(int fromIndex, UiBounds clip) {
      if (clip != null) {
         for (int i = this.hitRegions.size() - 1; i >= Math.max(0, fromIndex); i--) {
            ActionEditorOverlay.HitRegion region = this.hitRegions.get(i);
            int left = Math.max(region.x, clip.x());
            int top = Math.max(region.y, clip.y());
            int right = Math.min(region.x + region.w, clip.right());
            int bottom = Math.min(region.y + region.h, clip.bottom());
            if (right <= left || bottom <= top) {
               this.hitRegions.remove(i);
            } else if (left != region.x || top != region.y || right != region.x + region.w || bottom != region.y + region.h) {
               this.hitRegions.set(i, new ActionEditorOverlay.HitRegion(left, top, right - left, bottom - top, region.action));
            }
         }
      }
   }

   private int renderCaptureCopyField(
      GuiGraphicsExtractor ctx, String label, String fieldKey, String value, int x, int y, int w, int mx, int my, String copiedMessage
   ) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      UiText.draw(ctx, this.textRenderer, label, font, RiptideColors.textSecondary(), x, y + 2, false);
      y += 12;
      int buttonW = Math.min(52, Math.max(40, w / 4));
      RiptideChatField field = this.addFields.get(fieldKey);
      if (field != null) {
         String safe = value == null ? "" : value;
         if (!safe.equals(field.getText())) {
            field.setText(safe);
         }

         field.setX(x);
         field.setY(y);
         field.setWidth(Math.max(1, w - buttonW - 4));
         field.setHeight(14);
         field.render(ctx, mx, my, this.frameDelta);
      }

      String copied = value == null ? "" : value;
      this.renderActionButton(ctx, x + w - buttonW, y, buttonW, 14, "Copy", mx, my, () -> this.copyCaptureEditorValue(copied, copiedMessage));
      return y + 18;
   }

   private void copyCaptureEditorValue(String value, String message) {
      if (value != null && !value.isBlank()) {
         MC.keyboardHandler.setClipboard(value);
         RiptideNotifications.copied(message);
      } else {
         RiptideNotifications.warning("Nothing to copy.");
      }
   }

   private CaptureValueAction captureValueDraft() {
      CaptureValueAction draft = new CaptureValueAction();

      try {
         draft.source = CaptureValueAction.Source.valueOf(this.currentEnumValue("source"));
      } catch (NullPointerException | IllegalArgumentException var13) {
      }

      try {
         draft.matchMode = MacroCapturePattern.Mode.valueOf(this.currentEnumValue("matchMode"));
      } catch (NullPointerException | IllegalArgumentException var12) {
      }

      try {
         draft.itemText = CaptureValueAction.ItemText.valueOf(this.currentEnumValue("itemText"));
      } catch (NullPointerException | IllegalArgumentException var11) {
      }

      draft.saveAs = this.editorFieldText("saveAs", "value");
      draft.pattern = this.editorFieldText("pattern", "");
      draft.exampleText = this.editorFieldText("exampleText", "");
      draft.selectedText = this.editorFieldText("selectedText", "");
      draft.itemFilter = this.editorFieldText("itemFilter", "");
      draft.scoreboardRow = this.editorFieldText("scoreboardRow", "");
      draft.scoreboardObjective = this.editorFieldText("scoreboardObjective", "");

      try {
         draft.scoreboardRowIndex = Integer.parseInt(this.editorFieldText("scoreboardRowIndex", "-1").trim());
      } catch (NumberFormatException var10) {
         draft.scoreboardRowIndex = -1;
      }

      try {
         draft.numberMode = CaptureValueAction.NumberMode.valueOf(this.currentEnumValue("numberMode"));
      } catch (NullPointerException | IllegalArgumentException var9) {
      }

      try {
         draft.numberModifier = CaptureValueAction.NumberModifier.valueOf(this.currentEnumValue("numberModifier"));
      } catch (NullPointerException | IllegalArgumentException var8) {
      }

      try {
         draft.numberModifierAmount = Double.parseDouble(this.editorFieldText("numberModifierAmount", "1").trim());
      } catch (NumberFormatException var14) {
         draft.numberModifierAmount = this.workingTag == null ? 1.0 : this.workingTag.getDoubleOr("numberModifierAmount", 1.0);
      }

      draft.waitForTrigger = this.toggleStates.getOrDefault("waitForTrigger", this.workingTag == null || this.workingTag.getBooleanOr("waitForTrigger", true));

      try {
         draft.slot = Integer.parseInt(this.editorFieldText("slot", "-1").trim());
      } catch (NumberFormatException var7) {
         draft.slot = -1;
      }

      draft.autofillCommand = this.editorFieldText("autofillCommand", "");

      try {
         draft.autofillTimeoutMs = Integer.parseInt(this.editorFieldText("autofillTimeoutMs", "5000").trim());
      } catch (NumberFormatException var6) {
         draft.autofillTimeoutMs = 5000;
      }

      draft.autofillCacheList = this.toggleStates
         .getOrDefault("autofillCacheList", this.workingTag != null && this.workingTag.getBooleanOr("autofillCacheList", false));

      try {
         draft.listSelection = CaptureListSelector.Selection.valueOf(this.currentEnumValue("listSelection"));
      } catch (NullPointerException | IllegalArgumentException var5) {
      }

      try {
         draft.listFilter = CaptureListSelector.Filter.valueOf(this.currentEnumValue("listFilter"));
      } catch (NullPointerException | IllegalArgumentException var4) {
      }

      draft.listFilterText = this.editorFieldText("listFilterText", "");
      draft.listExcludeText = this.editorFieldText("listExcludeText", "");

      try {
         draft.listPickPosition = Integer.parseInt(this.editorFieldText("listPickPosition", "1").trim());
      } catch (NumberFormatException var3) {
         draft.listPickPosition = 1;
      }

      draft.listStripPrefix = this.toggleStates
         .getOrDefault("listStripPrefix", this.workingTag != null && this.workingTag.getBooleanOr("listStripPrefix", false));
      draft.excludeSelf = this.toggleStates.getOrDefault("excludeSelf", this.workingTag == null || this.workingTag.getBooleanOr("excludeSelf", true));
      return draft;
   }

   private String captureValuePrimaryName(CaptureValueAction draft) {
      if (draft == null) {
         return "";
      } else {
         boolean listSource = draft.source == CaptureValueAction.Source.COMMAND_AUTOFILL || draft.source == CaptureValueAction.Source.TABLIST;
         if (!listSource) {
            Set<String> captured = MacroCapturePattern.declaredNames(draft.matchMode, draft.pattern);
            if (!captured.isEmpty()) {
               return captured.iterator().next();
            }
         }

         return MacroVariableContext.cleanRootName(draft.saveAs);
      }
   }

   private String editorFieldText(String key, String fallback) {
      RiptideChatField field = this.textFields.get(key);
      return field == null ? fallback : field.getText();
   }

   private void fillWaitInventorySelectedSlot() {
      if (MC.player != null) {
         this.setWaitInventorySlot(MC.player.getInventory().getSelectedSlot());
      }
   }

   private void setWaitInventorySlot(int slot) {
      RiptideChatField field = this.textFields.get("slot");
      if (field != null) {
         field.setText(String.valueOf(Math.max(0, slot)));
      }
   }

   private void toggleWaitInventorySlotCapture() {
      this.toggleItemSlotCapture("wait_inventory_slot");
   }

   private void renderWaitChatPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int contentBottom, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "pattern", x, cy, w, mx, my, delta);
      cy = this.renderCapturePatternBuilder(ctx, this.textFields.get("pattern"), x, cy, w, mx, my);
      cy = this.renderFieldByKey(ctx, "matchMode", x, cy, w, mx, my, delta);
      String matchMode = this.currentEnumValue("matchMode");
      if ("MATCH".equals(matchMode)) {
         cy = this.renderWaitChatFuzzySlider(ctx, x, cy, w, mx, my);
      } else {
         this.waitChatFuzzySliderDragging = false;
         this.clearWaitChatFuzzySliderBounds();
      }

      cy = this.renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "waitGuiName", x, cy, w, mx, my, delta);
      String pattern = this.textFields.containsKey("pattern") ? this.textFields.get("pattern").getText().trim() : "";
      int fuzzyPercent = this.getWaitChatFuzzyPercent();
      String hint = pattern.isEmpty()
         ? "Blank pattern = any chat line."
         : (
            "REGEX".equals(matchMode)
               ? "Named groups save values."
               : ("CAPTURE".equals(matchMode) ? "Use {name} to capture." : "Fuzzy match: " + fuzzyPercent + "%.")
         );
      cy = this.renderEditorHint(ctx, x, cy, w, hint);
      RiptideChatField searchField = this.addFields.get("_wait_chat_search");
      if (searchField != null) {
         searchField.setX(x);
         searchField.setY(cy);
         searchField.setWidth(w);
         searchField.render(ctx, mx, my, delta);
         cy += 18;
      }

      List<MacroExecutor.RecentChatMessage> history = this.filterWaitChatHistory();
      UiText.draw(ctx, this.textRenderer, "Recent Messages (" + history.size() + ")", font, RiptideColors.textSecondary(), x, cy + 2, false);
      UiText.draw(
         ctx,
         this.textRenderer,
         UiText.trimToWidth(this.textRenderer, this.formatEditorHint("Click a row to use that message."), Math.max(20, w - 122), font, -1),
         font,
         RiptideColors.textDim(),
         x + 118,
         cy + 2,
         false
      );
      cy += 13;
      int availableListHeight = Math.max(24, contentBottom - cy - 4);
      this.renderWaitChatHistoryList(ctx, x, cy, w, availableListHeight, mx, my, history);
   }

   private int renderCapturePatternBuilder(GuiGraphicsExtractor ctx, RiptideChatField patternField, int x, int y, int w, int mx, int my) {
      RiptideChatField nameField = this.addFields.get("_capture_name");
      if (nameField != null && patternField != null) {
         int buttonW = 48;
         nameField.setX(x);
         nameField.setY(y + 1);
         nameField.setWidth(Math.max(1, w - buttonW - 3));
         nameField.setHeight(14);
         nameField.render(ctx, mx, my, 0.0F);
         this.renderActionButton(ctx, x + w - buttonW, y + 1, buttonW, 14, "Capture", mx, my, () -> {
            String name = MacroVariableContext.cleanRootName(nameField.getText());
            if (!name.isBlank()) {
               patternField.insertText("{" + name + "}");
               this.enumIndices.put("matchMode", 1);
               this.workingTag.putString("matchMode", "CAPTURE");
               this.workingTag.putString("patternJson", "");
            }
         });
         return y + 18;
      } else {
         return y;
      }
   }

   private int renderWaitChatFuzzySlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int percent = this.getWaitChatFuzzyPercent();
      UiText.draw(ctx, this.textRenderer, "Match Strength", font, RiptideColors.textSecondary(), x, y + 2, false);
      String label = percent + "%";
      UiText.draw(ctx, this.textRenderer, label, font, RiptideColors.textDim(), x + w - this.uiWidth(font, label), y + 2, false);
      y += 13;
      int sliderW = w;
      int sliderH = 14;
      int trackH = 4;
      int trackY = y + (sliderH - trackH) / 2;
      this.waitChatFuzzySliderX = x;
      this.waitChatFuzzySliderY = y;
      this.waitChatFuzzySliderW = w;
      this.waitChatFuzzySliderH = sliderH;
      UiRenderer.frame(ctx, UiBounds.of(x, y, w, sliderH), -15921896, RiptideColors.subPanelBorder());
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, w - 2, trackH), -15397876);
      int[] steps = new int[]{40, 50, 60, 70, 80, 90, 100};
      int knobIndex = Math.max(0, Math.min(6, (percent - 40) / 10));
      int knobCenterX = x + Math.round((w - 1) * (knobIndex / 6.0F));
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), -13345658);

      for (int i = 0; i < steps.length; i++) {
         int step = steps[i];
         int cx = x + Math.round((sliderW - 1) * (i / 6.0F));
         int tickColor = step <= percent ? -6435073 : -10862528;
         UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
      }

      int knobW = 16;
      int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
      boolean hovered = this.isOverWaitChatFuzzySlider(mx, my);
      int knobFill = this.waitChatFuzzySliderDragging ? -14202259 : (hovered ? -14007198 : -14533802);
      int knobBorder = this.waitChatFuzzySliderDragging ? -5908993 : -7816193;
      UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
      return y + sliderH + 2;
   }

   private int getWaitChatFuzzyPercent() {
      RiptideChatField field = this.textFields.get("fuzzyPercent");
      if (field == null) {
         return 100;
      } else {
         try {
            return WaitForChatAction.clampFuzzyPercent(Integer.parseInt(field.getText().trim()));
         } catch (NumberFormatException var3) {
            return 100;
         }
      }
   }

   private void setWaitChatFuzzyPercent(int percent) {
      RiptideChatField field = this.textFields.get("fuzzyPercent");
      if (field != null) {
         field.setText(String.valueOf(WaitForChatAction.clampFuzzyPercent(percent)));
      }
   }

   private void clearWaitChatFuzzySliderBounds() {
      this.waitChatFuzzySliderX = -1;
      this.waitChatFuzzySliderY = -1;
      this.waitChatFuzzySliderW = 0;
      this.waitChatFuzzySliderH = 0;
   }

   private boolean isOverWaitChatFuzzySlider(int mx, int my) {
      return this.waitChatFuzzySliderW > 0
         && this.waitChatFuzzySliderH > 0
         && mx >= this.waitChatFuzzySliderX
         && mx < this.waitChatFuzzySliderX + this.waitChatFuzzySliderW
         && my >= this.waitChatFuzzySliderY
         && my < this.waitChatFuzzySliderY + this.waitChatFuzzySliderH;
   }

   private void updateWaitChatFuzzyPercentFromMouse(int mouseX) {
      if (this.waitChatFuzzySliderW > 1) {
         float normalized = (float)(mouseX - this.waitChatFuzzySliderX) / (this.waitChatFuzzySliderW - 1);
         normalized = Math.max(0.0F, Math.min(1.0F, normalized));
         int stepIndex = Math.round(normalized * 6.0F);
         this.setWaitChatFuzzyPercent(40 + stepIndex * 10);
      }
   }

   private int renderRotateSmoothnessSlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int smoothness = this.getRotateSmoothness();
      UiText.draw(ctx, this.textRenderer, "Smoothness", font, RiptideColors.textSecondary(), x, y + 2, false);
      String label = smoothness + " / 10";
      UiText.draw(ctx, this.textRenderer, label, font, RiptideColors.textDim(), x + w - this.uiWidth(font, label), y + 2, false);
      y += 13;
      int sliderW = w;
      int sliderH = 14;
      int trackH = 4;
      int trackY = y + (sliderH - trackH) / 2;
      this.rotateSmoothnessSliderX = x;
      this.rotateSmoothnessSliderY = y;
      this.rotateSmoothnessSliderW = w;
      this.rotateSmoothnessSliderH = sliderH;
      UiRenderer.frame(ctx, UiBounds.of(x, y, w, sliderH), -15921896, RiptideColors.subPanelBorder());
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, w - 2, trackH), -15397876);
      int knobIndex = Math.max(0, Math.min(9, smoothness - 1));
      int knobCenterX = x + Math.round((w - 1) * (knobIndex / 9.0F));
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), -13345658);

      for (int i = 0; i < 10; i++) {
         int cx = x + Math.round((sliderW - 1) * (i / 9.0F));
         int tickColor = i <= knobIndex ? -6435073 : -10862528;
         UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
      }

      int knobW = 16;
      int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
      boolean hovered = this.isOverRotateSmoothnessSlider(mx, my);
      int knobFill = this.rotateSmoothnessSliderDragging ? -14202259 : (hovered ? -14007198 : -14533802);
      int knobBorder = this.rotateSmoothnessSliderDragging ? -5908993 : -7816193;
      UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
      return y + sliderH + 2;
   }

   private int getRotateSmoothness() {
      RiptideChatField field = this.textFields.get("smoothness");
      if (field == null) {
         return 9;
      } else {
         try {
            return RotateAction.clampSmoothness(Integer.parseInt(field.getText().trim()));
         } catch (NumberFormatException var3) {
            return 9;
         }
      }
   }

   private void setRotateSmoothness(int smoothness) {
      RiptideChatField field = this.textFields.get("smoothness");
      if (field != null) {
         field.setText(String.valueOf(RotateAction.clampSmoothness(smoothness)));
      }
   }

   private void clearRotateSmoothnessSliderBounds() {
      this.rotateSmoothnessSliderX = -1;
      this.rotateSmoothnessSliderY = -1;
      this.rotateSmoothnessSliderW = 0;
      this.rotateSmoothnessSliderH = 0;
   }

   private boolean isOverRotateSmoothnessSlider(int mx, int my) {
      return this.rotateSmoothnessSliderW > 0
         && this.rotateSmoothnessSliderH > 0
         && mx >= this.rotateSmoothnessSliderX
         && mx < this.rotateSmoothnessSliderX + this.rotateSmoothnessSliderW
         && my >= this.rotateSmoothnessSliderY
         && my < this.rotateSmoothnessSliderY + this.rotateSmoothnessSliderH;
   }

   private void updateRotateSmoothnessFromMouse(int mouseX) {
      if (this.rotateSmoothnessSliderW > 1) {
         float normalized = (float)(mouseX - this.rotateSmoothnessSliderX) / (this.rotateSmoothnessSliderW - 1);
         normalized = Math.max(0.0F, Math.min(1.0F, normalized));
         int stepIndex = Math.round(normalized * 9.0F);
         this.setRotateSmoothness(1 + stepIndex);
      }
   }

   private int renderXCarrySafeDelaySlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int delay = this.getXCarrySafeDelayTicks();
      UiText.draw(ctx, this.textRenderer, "Safe Delay", font, RiptideColors.textSecondary(), x, y + 2, false);
      String label = delay + "t";
      UiText.draw(ctx, this.textRenderer, label, font, RiptideColors.textDim(), x + w - this.uiWidth(font, label), y + 2, false);
      y += 13;
      int sliderW = w;
      int sliderH = 14;
      int trackH = 4;
      int trackY = y + (sliderH - trackH) / 2;
      this.xcarrySafeDelaySliderX = x;
      this.xcarrySafeDelaySliderY = y;
      this.xcarrySafeDelaySliderW = w;
      this.xcarrySafeDelaySliderH = sliderH;
      UiRenderer.frame(ctx, UiBounds.of(x, y, w, sliderH), -15921896, RiptideColors.subPanelBorder());
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, w - 2, trackH), -15397876);
      int knobIndex = Math.max(0, Math.min(10, delay));
      int knobCenterX = x + Math.round((w - 1) * (knobIndex / 10.0F));
      UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), -9818835);

      for (int i = 0; i <= 10; i++) {
         int cx = x + Math.round((sliderW - 1) * (i / 10.0F));
         int tickColor = i <= knobIndex ? -30070 : -10862528;
         UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
      }

      int knobW = 16;
      int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
      boolean hovered = this.isOverXCarrySafeDelaySlider(mx, my);
      int knobFill = this.xcarrySafeDelaySliderDragging ? -10738652 : (hovered ? -11787228 : -13296869);
      int knobBorder = this.xcarrySafeDelaySliderDragging ? -20304 : -34953;
      UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
      return y + sliderH + 2;
   }

   private int getXCarrySafeDelayTicks() {
      RiptideChatField field = this.textFields.get("safeClickDelayTicks");
      if (field == null) {
         return 1;
      } else {
         try {
            return XCarryAction.clampSafeClickDelayTicks(Integer.parseInt(field.getText().trim()));
         } catch (NumberFormatException var3) {
            return 1;
         }
      }
   }

   private void setXCarrySafeDelayTicks(int ticks) {
      RiptideChatField field = this.textFields.get("safeClickDelayTicks");
      if (field != null) {
         field.setText(String.valueOf(XCarryAction.clampSafeClickDelayTicks(ticks)));
      }
   }

   private void clearXCarrySafeDelaySliderBounds() {
      this.xcarrySafeDelaySliderX = -1;
      this.xcarrySafeDelaySliderY = -1;
      this.xcarrySafeDelaySliderW = 0;
      this.xcarrySafeDelaySliderH = 0;
   }

   private boolean isOverXCarrySafeDelaySlider(int mx, int my) {
      return this.xcarrySafeDelaySliderW > 0
         && this.xcarrySafeDelaySliderH > 0
         && mx >= this.xcarrySafeDelaySliderX
         && mx < this.xcarrySafeDelaySliderX + this.xcarrySafeDelaySliderW
         && my >= this.xcarrySafeDelaySliderY
         && my < this.xcarrySafeDelaySliderY + this.xcarrySafeDelaySliderH;
   }

   private void updateXCarrySafeDelayFromMouse(int mouseX) {
      if (this.xcarrySafeDelaySliderW > 1) {
         float normalized = (float)(mouseX - this.xcarrySafeDelaySliderX) / (this.xcarrySafeDelaySliderW - 1);
         normalized = Math.max(0.0F, Math.min(1.0F, normalized));
         int stepIndex = Math.round(normalized * 10.0F);
         this.setXCarrySafeDelayTicks(stepIndex);
      }
   }

   private void applyWaitChatHistoryEntry(MacroExecutor.RecentChatMessage entry) {
      RiptideChatField field = this.textFields.get("pattern");
      if (field != null && entry != null) {
         Component patternComponent = entry.displayComponent() != null
            ? entry.displayComponent().copy()
            : Component.literal(this.formatWaitChatHistoryEntry(entry));
         this.setWaitChatPatternField(field, patternComponent);
         this.enumIndices.put("matchMode", 0);
         this.workingTag.putString("matchMode", "MATCH");
      }
   }

   private void setWaitChatPatternField(RiptideChatField field, Component component) {
      if (field != null) {
         Component safeComponent = component != null ? component.copy() : Component.empty();
         String visibleText = safeComponent.getString();
         this.workingTag.putString("pattern", visibleText);
         this.workingTag.putString("patternJson", MacroExecutor.serializeTextComponent(safeComponent));
         this.suppressWaitChatPatternSync = true;
         field.setText(visibleText);
         this.suppressWaitChatPatternSync = false;
      }
   }

   private Component getWaitChatPatternComponent(String fallbackValue) {
      Component exact = MacroExecutor.deserializeTextComponent(this.workingTag != null ? this.workingTag.getStringOr("patternJson", "") : "");
      return exact != null ? exact.copy() : Component.literal(fallbackValue == null ? "" : fallbackValue);
   }

   private Component rebuildWaitChatPatternComponent(Component previousComponent, String editedValue) {
      String safeValue = editedValue == null ? "" : editedValue;
      if (previousComponent == null) {
         return Component.literal(safeValue);
      } else {
         String previousValue = previousComponent.getString();
         if (previousValue.equals(safeValue)) {
            return previousComponent.copy();
         } else if (safeValue.isEmpty()) {
            return Component.empty();
         } else if (previousValue.isEmpty()) {
            return Component.literal(safeValue);
         } else {
            List<Style> previousStyles = this.flattenWaitChatStyles(previousComponent, previousValue.length());
            if (previousStyles.isEmpty()) {
               return Component.literal(safeValue);
            } else {
               int prefix = this.longestCommonPrefix(previousValue, safeValue);
               int suffix = this.longestCommonSuffix(previousValue, safeValue, prefix);
               int previousLength = previousValue.length();
               int nextLength = safeValue.length();
               MutableComponent rebuilt = Component.empty();
               StringBuilder segment = new StringBuilder();
               Style segmentStyle = null;

               for (int i = 0; i < nextLength; i++) {
                  Style style = this.styleForEditedWaitChatIndex(previousStyles, previousLength, nextLength, prefix, suffix, i);
                  if (segmentStyle == null) {
                     segmentStyle = style;
                  } else if (!segmentStyle.equals(style)) {
                     rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle));
                     segment.setLength(0);
                     segmentStyle = style;
                  }

                  segment.append(safeValue.charAt(i));
               }

               if (!segment.isEmpty()) {
                  rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle == null ? Style.EMPTY : segmentStyle));
               }

               return rebuilt;
            }
         }
      }
   }

   private List<Style> flattenWaitChatStyles(Component text, int expectedLength) {
      if (text != null && expectedLength > 0) {
         List<Style> rawStyles = new ArrayList<>(expectedLength);
         text.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
               Style safeStyle = style == null ? Style.EMPTY : style;

               for (int i = 0; i < part.length(); i++) {
                  rawStyles.add(safeStyle);
               }
            }

            return Optional.empty();
         }, Style.EMPTY);
         List<Style> styles = new ArrayList<>(rawStyles);
         if (styles.isEmpty()) {
            for (int i = 0; i < expectedLength; i++) {
               styles.add(Style.EMPTY);
            }
         } else if (styles.size() < expectedLength) {
            Style fill = styles.get(styles.size() - 1);

            while (styles.size() < expectedLength) {
               styles.add(fill);
            }
         } else if (styles.size() > expectedLength) {
            styles = new ArrayList<>(styles.subList(0, expectedLength));
         }

         return styles;
      } else {
         return Collections.emptyList();
      }
   }

   private int longestCommonPrefix(String left, String right) {
      int max = Math.min(left.length(), right.length());
      int index = 0;

      while (index < max && left.charAt(index) == right.charAt(index)) {
         index++;
      }

      return index;
   }

   private int longestCommonSuffix(String previous, String next, int prefixLength) {
      int previousRemaining = previous.length() - prefixLength;
      int nextRemaining = next.length() - prefixLength;
      int max = Math.min(previousRemaining, nextRemaining);
      int suffix = 0;

      while (suffix < max && previous.charAt(previous.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
         suffix++;
      }

      return suffix;
   }

   private Style styleForEditedWaitChatIndex(List<Style> previousStyles, int previousLength, int nextLength, int prefixLength, int suffixLength, int nextIndex) {
      if (previousStyles.isEmpty()) {
         return Style.EMPTY;
      } else if (nextIndex < prefixLength) {
         return previousStyles.get(Math.min(nextIndex, previousStyles.size() - 1));
      } else {
         int nextSuffixStart = nextLength - suffixLength;
         int previousSuffixStart = previousLength - suffixLength;
         if (suffixLength > 0 && nextIndex >= nextSuffixStart) {
            int mappedIndex = previousSuffixStart + (nextIndex - nextSuffixStart);
            return previousStyles.get(Math.max(0, Math.min(mappedIndex, previousStyles.size() - 1)));
         } else {
            int anchorIndex;
            if (prefixLength > 0) {
               anchorIndex = prefixLength - 1;
            } else if (suffixLength > 0) {
               anchorIndex = previousSuffixStart;
            } else {
               anchorIndex = 0;
            }

            return previousStyles.get(Math.max(0, Math.min(anchorIndex, previousStyles.size() - 1)));
         }
      }
   }

   private List<MacroExecutor.RecentChatMessage> filterWaitChatHistory() {
      List<MacroExecutor.RecentChatMessage> history = MacroExecutor.getRecentChatMessages();
      RiptideChatField searchField = this.addFields.get("_wait_chat_search");
      String query = searchField != null ? searchField.getText().trim() : "";
      this.waitChatFilteredHistory.clear();
      String normalizedQuery = query.isEmpty() ? "" : this.normalizeWaitChatSearch(query);

      for (MacroExecutor.RecentChatMessage entry : history) {
         if (normalizedQuery.isEmpty()) {
            this.waitChatFilteredHistory.add(entry);
         } else {
            String haystack = this.normalizeWaitChatSearch(this.formatWaitChatHistoryEntry(entry));
            if (haystack.contains(normalizedQuery)) {
               this.waitChatFilteredHistory.add(entry);
            }
         }
      }

      return this.waitChatFilteredHistory;
   }

   private String normalizeWaitChatSearch(String text) {
      return MacroExecutor.normalizeChatText(text);
   }

   private String formatWaitChatHistoryEntry(MacroExecutor.RecentChatMessage entry) {
      if (entry == null) {
         return "";
      } else {
         if (entry.displayComponent() != null) {
            String rendered = entry.displayComponent().getString();
            if (rendered != null && !rendered.isBlank()) {
               return rendered;
            }
         }

         String sender = entry.sender() == null ? "" : entry.sender().trim();
         String message = entry.message() == null ? "" : entry.message().trim();
         if (!sender.isEmpty() && !message.isEmpty()) {
            return sender + ": " + message;
         } else if (!message.isEmpty()) {
            return (entry.source() == MacroExecutor.ChatSource.SERVER ? "[Server] " : "[Player] ") + message;
         } else {
            return entry.displayText() == null ? "" : entry.displayText();
         }
      }
   }

   private void renderWaitChatHistoryList(
      GuiGraphicsExtractor ctx, int x, int y, int w, int listH, int mx, int my, List<MacroExecutor.RecentChatMessage> values
   ) {
      this.renderRecentChatHistoryList(ctx, x, y, w, listH, mx, my, values, "wait_chat_recent", 4, this::applyWaitChatHistoryEntry);
   }

   private void renderRecentChatHistoryList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int listH,
      int mx,
      int my,
      List<MacroExecutor.RecentChatMessage> values,
      String viewportKey,
      int visibleRows,
      Consumer<MacroExecutor.RecentChatMessage> onSelect
   ) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int desiredListH = values.isEmpty() ? 24 : Math.min(Math.max(1, visibleRows), values.size()) * 34;
      listH = Math.max(24, Math.min(listH, desiredListH));
      int itemW = w - 5 - 1;
      DirectScrollViewport chatViewport = this.getOrCreateViewport(this.selectedScrollViewports, viewportKey, x, y, w, listH, 34, 5);
      chatViewport.setContentHeight(values.size() * 34);
      chatViewport.renderScrollbar(ctx, mx, my);
      if (values.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
         String firstLine = "capture_value_recent".equals(viewportKey) ? "No recent chat yet." : "No recent messages matched your search.";
         String secondLine = "capture_value_recent".equals(viewportKey) ? "Send or receive a message first." : "Send or receive chat first, then pick it here.";
         UiText.draw(ctx, this.textRenderer, firstLine, font, RiptideColors.textDim(), x + 3, y + 3, false);
         UiText.draw(ctx, this.textRenderer, secondLine, font, RiptideColors.textDim(), x + 3, y + 13, false);
      } else {
         chatViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = chatViewport.getFirstVisibleRow();

         for (int i = first; i < values.size() && i <= chatViewport.getLastVisibleRow(); i++) {
            int iy = chatViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               int itemH = 34;
               if (iy + itemH > y + listH) {
                  itemH = Math.max(0, y + listH - iy);
               }

               if (itemH > 0) {
                  MacroExecutor.RecentChatMessage value = values.get(i);
                  boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + itemH;
                  CompactSurfaces.row(ctx, x, iy, itemW, itemH, hovered, false);
                  int border = value.source() == MacroExecutor.ChatSource.SERVER ? (hovered ? -20134 : -3376606) : (hovered ? -8597761 : -11627064);
                  this.fillBorder(ctx, x, iy, itemW, itemH, border);
                  List<FormattedCharSequence> wrapped = this.wrapWaitChatDisplayLines(value, Math.max(20, itemW - 6), 3);
                  int textY = iy + 3;
                  if (wrapped.isEmpty()) {
                     UiText.draw(ctx, this.textRenderer, "(empty)", font, RiptideColors.textDim(), x + 3, textY, false);
                  } else {
                     for (FormattedCharSequence line : wrapped) {
                        if (textY + 8 > iy + itemH) {
                           break;
                        }

                        ctx.text(this.textRenderer, line, x + 3, textY, -1, false);
                        textY += 9;
                     }
                  }

                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, itemW, itemH, () -> {
                     if (onSelect != null) {
                        onSelect.accept(value);
                     }
                  }));
               }
            }
         }

         chatViewport.endRender(ctx);
      }
   }

   private int waitChatHistoryListHeight() {
      int count = this.filterWaitChatHistory().size();
      return count <= 0 ? 24 : Math.min(4, count) * 34;
   }

   private List<FormattedCharSequence> wrapWaitChatDisplayLines(MacroExecutor.RecentChatMessage entry, int maxWidth, int maxLines) {
      Component display = (Component)(entry != null && entry.displayComponent() != null
         ? entry.displayComponent()
         : Component.literal(this.formatWaitChatHistoryEntry(entry)));
      List<FormattedCharSequence> wrapped = this.textRenderer.split(display, Math.max(20, maxWidth));
      return (List<FormattedCharSequence>)(wrapped.size() <= maxLines ? wrapped : new ArrayList<>(wrapped.subList(0, Math.max(0, maxLines))));
   }

   private List<String> wrapWaitChatLines(String text, int maxWidth, int maxLines) {
      List<String> lines = new ArrayList<>();
      if (text != null && !text.isBlank() && maxLines > 0) {
         String[] words = text.trim().split("\\s+");
         StringBuilder current = new StringBuilder();

         for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (this.uiWidth(candidate) <= maxWidth) {
               current.setLength(0);
               current.append(candidate);
            } else if (!current.isEmpty()) {
               if (lines.size() == maxLines - 1) {
                  lines.add(this.trimWaitChatLine(current + " " + this.joinWaitChatWords(words, wordIndex), maxWidth));
                  return lines;
               }

               lines.add(current.toString());
               current.setLength(0);
               current.append(word);
            } else {
               if (lines.size() == maxLines - 1) {
                  lines.add(this.trimWaitChatLine(this.joinWaitChatWords(words, wordIndex), maxWidth));
                  return lines;
               }

               lines.add(this.trimWaitChatLine(word, maxWidth));
            }
         }

         if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
         }

         return lines;
      } else {
         return lines;
      }
   }

   private String joinWaitChatWords(String[] words, int startIndex) {
      StringBuilder out = new StringBuilder();

      for (int i = startIndex; i < words.length; i++) {
         if (out.length() > 0) {
            out.append(' ');
         }

         out.append(words[i]);
      }

      return out.toString();
   }

   private String trimWaitChatLine(String text, int maxWidth) {
      String trimmed = text == null ? "" : text;

      while (!trimmed.isEmpty() && this.uiWidth(trimmed + "...") > maxWidth) {
         trimmed = trimmed.substring(0, trimmed.length() - 1);
      }

      return trimmed.length() < (text == null ? 0 : text.length()) ? trimmed + "..." : trimmed;
   }

   private void renderDelayPacketsPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      int cy = bodyTop + 4;
      int halfW = (w - 4) / 2;
      List<String> c2sTargets = this.getDelayPacketTargets(true);
      List<String> s2cTargets = this.getDelayPacketTargets(false);
      cy = this.renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "flushOnDisable", x, cy, w, mx, my, delta);
      String mode = this.currentEnumValue("mode");
      if ("ENABLE".equals(mode)) {
         this.renderOverlayButton(
            ctx,
            x,
            cy,
            halfW,
            14,
            "Add C2S (" + c2sTargets.size() + ")",
            CompactOverlayButton.Variant.PRIMARY,
            true,
            mx,
            my,
            () -> this.openDelayPacketSelector(true)
         );
         this.renderOverlayButton(
            ctx,
            x + halfW + 4,
            cy,
            halfW,
            14,
            "Add S2C (" + s2cTargets.size() + ")",
            CompactOverlayButton.Variant.PRIMARY,
            true,
            mx,
            my,
            () -> this.openDelayPacketSelector(false)
         );
         cy += 18;
         this.renderActionButton(ctx, x, cy, halfW, 14, "Load Queue", mx, my, this::loadDelayPacketTargetsFromQueue);
         this.renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Clear All", mx, my, this::clearAllDelayPacketTargets);
         cy += 18;
         this.renderOverlayButton(
            ctx, x, cy, halfW, 14, "Default Preset", CompactOverlayButton.Variant.PRIMARY, true, mx, my, this::applyDefaultDelayPacketPreset
         );
         this.renderOverlayButton(
            ctx, x + halfW + 4, cy, halfW, 14, "Module Preset", CompactOverlayButton.Variant.PRIMARY, true, mx, my, this::applyModuleDelayPacketPreset
         );
      }
   }

   private List<String> getDelayPacketTargets(boolean c2s) {
      return this.stringLists.computeIfAbsent(c2s ? "c2sPackets" : "s2cPackets", ignored -> new ArrayList<>());
   }

   private void openDelayPacketSelector(boolean c2s) {
      if (c2s) {
         this.packetSelectorOverlay
            .openToggleC2S((packetClass, selected) -> this.setDelayPacketSelected(true, packetClass, selected), this.getSelectedDelayPacketClasses(true));
      } else {
         this.packetSelectorOverlay
            .openToggleS2C((packetClass, selected) -> this.setDelayPacketSelected(false, packetClass, selected), this.getSelectedDelayPacketClasses(false));
      }
   }

   private List<Class<? extends Packet<?>>> getSelectedDelayPacketClasses(boolean c2s) {
      List<Class<? extends Packet<?>>> selected = new ArrayList<>();

      for (String target : this.getDelayPacketTargets(c2s)) {
         Class<? extends Packet<?>> packetClass = this.resolveDelayPacketClass(target, c2s);
         if (packetClass != null && !selected.contains(packetClass)) {
            selected.add(packetClass);
         }
      }

      return selected;
   }

   private Class<? extends Packet<?>> resolveDelayPacketClass(String target, boolean c2s) {
      if (target != null && !target.isBlank()) {
         for (Class<? extends Packet<?>> candidate : c2s
            ? new ArrayList<>(RiptidePacketRegistry.getC2SPackets())
            : new ArrayList<>(RiptidePacketRegistry.getS2CPackets())) {
            String registryName = RiptidePacketRegistry.getName(candidate);
            if (this.packetNameMatches(target, registryName)) {
               return candidate;
            }

            if (this.packetNameMatches(target, RiptidePacketNamer.getFriendlyName(candidate))) {
               return candidate;
            }

            if (this.packetNameMatches(target, candidate.getSimpleName())) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void setDelayPacketSelected(boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
      if (packetClass != null) {
         List<String> targets = this.getDelayPacketTargets(c2s);
         if (selected) {
            if (!this.containsDelayPacketTarget(targets, packetClass, c2s)) {
               targets.add(RiptidePacketNamer.getFriendlyName(packetClass));
            }
         } else {
            targets.removeIf(existing -> packetClass.equals(this.resolveDelayPacketClass(existing, c2s)));
         }
      }
   }

   private boolean containsDelayPacketTarget(List<String> targets, Class<? extends Packet<?>> packetClass, boolean c2s) {
      if (targets != null && packetClass != null) {
         for (String target : targets) {
            if (packetClass.equals(this.resolveDelayPacketClass(target, c2s))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void loadDelayPacketTargetsFromQueue() {
      List<RiptideSharedState.QueuedPacket> queue = RiptideSharedState.get().getDelayedPackets();
      if (queue != null && !queue.isEmpty()) {
         int before = this.getDelayPacketTargets(true).size() + this.getDelayPacketTargets(false).size();

         for (RiptideSharedState.QueuedPacket queuedPacket : queue) {
            if (queuedPacket != null && queuedPacket.packet != null) {
               Class<? extends Packet<?>> packetClass = queuedPacket.packet.getClass();
               if (RiptidePacketRegistry.getC2SPackets().contains(packetClass)) {
                  this.setDelayPacketSelected(true, packetClass, true);
               } else if (RiptidePacketRegistry.getS2CPackets().contains(packetClass)) {
                  this.setDelayPacketSelected(false, packetClass, true);
               }
            }
         }

         int after = this.getDelayPacketTargets(true).size() + this.getDelayPacketTargets(false).size();
         int added = Math.max(0, after - before);
         RiptideClientMessaging.sendPrefixed(
            added == 0 ? "Queue did not add any new packet filters" : "Added " + added + " packet filter" + (added == 1 ? "" : "s") + " from queue"
         );
      } else {
         RiptideClientMessaging.sendPrefixed("Queue is empty");
      }
   }

   private void clearAllDelayPacketTargets() {
      this.getDelayPacketTargets(true).clear();
      this.getDelayPacketTargets(false).clear();
   }

   private void applyDefaultDelayPacketPreset() {
      DelayPacketsAction tmp = new DelayPacketsAction();
      tmp.applyDefaultPreset();
      this.stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
      this.stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
   }

   private void applyModuleDelayPacketPreset() {
      DelayPacketsAction tmp = new DelayPacketsAction();
      tmp.applyModulePreset();
      this.stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
      this.stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
   }

   private String buildDelayPacketDirectionSummary(boolean c2s) {
      String direction = c2s ? "C2S" : "S2C";
      List<String> targets = this.getDelayPacketTargets(c2s);
      if (targets.isEmpty()) {
         return direction + ": none";
      } else {
         List<String> labels = new ArrayList<>();

         for (int i = 0; i < targets.size() && i < 3; i++) {
            String target = targets.get(i);
            Class<? extends Packet<?>> packetClass = this.resolveDelayPacketClass(target, c2s);
            labels.add(packetClass != null ? RiptidePacketNamer.getFriendlyName(packetClass) : target);
         }

         String summary = direction + ": " + targets.size();
         if (!labels.isEmpty()) {
            summary = summary + " - " + String.join(", ", labels);
         }

         if (targets.size() > labels.size()) {
            summary = summary + ", +" + (targets.size() - labels.size());
         }

         return summary;
      }
   }

   private int renderDelayPacketPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta, boolean c2s) {
      String key = c2s ? "c2sPackets" : "s2cPackets";
      String searchKey = c2s ? "_delay_packets_c2s_search" : "_delay_packets_s2c_search";
      String title = c2s ? "C2S Packets" : "S2C Packets";
      List<String> selected = this.stringLists.getOrDefault(key, Collections.emptyList());
      y = this.renderSimpleSelectedList(ctx, x, y, w, mx, my, key + "_selected", title, selected, selected::remove, value -> value, "(none selected)");
      RiptideChatField search = this.addFields.get(searchKey);
      if (search != null) {
         search.setX(x);
         search.setY(y + 1);
         search.setWidth(w);
         search.render(ctx, mx, my, delta);
      }

      y += 18;
      String filter = search != null ? search.getText().trim().toLowerCase(Locale.ROOT) : "";
      List<String> options = new ArrayList<>();

      for (String packetName : this.getPacketNames(c2s)) {
         if (filter.isEmpty() || packetName.toLowerCase(Locale.ROOT).contains(filter)) {
            options.add(packetName);
         }
      }

      this.renderSearchRegistryList(ctx, x, y, w, mx, my, key + "_search", options, 5, value -> {
         if (selected.contains(value)) {
            selected.remove(value);
         } else {
            selected.add(value);
         }
      }, selected::contains, value -> value);
      return y + 65 + 4;
   }

   private void renderMinePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int cy = bodyTop + 4;
      cy = this.renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "targetBlocks", x, cy, w, mx, my, delta);
      cy += 4;
      cy = this.renderFieldByKey(ctx, "stopInventoryFull", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "stopSlotsUsed", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "slotsUsedThreshold", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "stopMinedCount", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "minedCountTarget", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "stopAfterTime", x, cy, w, mx, my, delta);
      cy = this.renderFieldByKey(ctx, "timeoutSeconds", x, cy, w, mx, my, delta);
      this.renderEditorHint(ctx, x, cy, w, "Pick target blocks, then choose the stop rule you want.");
   }

   private void fillUseItemFromHeld() {
      if (MC.player != null) {
         RiptideChatField field = this.textFields.get("itemName");
         if (field != null) {
            if (MC.player.getMainHandItem().isEmpty()) {
               field.setText("");
               this.editorItemFields.remove("itemName");
            } else {
               ItemTarget target = stripSlotFromTarget(ItemTarget.capture(MC.player.getMainHandItem(), -1));
               this.editorItemFields.put("itemName", target);
               field.setText(target.editorText());
            }
         }
      }
   }

   private void fillSelectSlotFromHeld() {
      if (MC.player != null) {
         RiptideChatField field = this.textFields.get("itemName");
         if (field != null) {
            if (MC.player.getMainHandItem().isEmpty()) {
               field.setText("");
               this.editorItemFields.remove("itemName");
            } else {
               ItemTarget target = ItemTarget.capture(MC.player.getMainHandItem(), MC.player.getInventory().getSelectedSlot());
               this.editorItemFields.put("itemName", target);
               field.setText(target.editorText());
            }
         }
      }
   }

   private void clearSelectSlotItemName() {
      RiptideChatField field = this.textFields.get("itemName");
      if (field != null) {
         field.setText("");
      }

      this.editorItemFields.remove("itemName");
   }

   private int getSelectSlotHotbarIndex() {
      RiptideChatField field = this.textFields.get("slot");
      if (field == null) {
         return 0;
      } else {
         try {
            return Math.max(0, Math.min(8, Integer.parseInt(field.getText().trim())));
         } catch (NumberFormatException var3) {
            return 0;
         }
      }
   }

   private void setSelectSlotHotbarIndex(int slot) {
      RiptideChatField field = this.textFields.get("slot");
      if (field != null) {
         field.setText(String.valueOf(Math.max(0, Math.min(8, slot))));
      }
   }

   private String buildSelectSlotSummary() {
      int fallbackSlot = this.getSelectSlotHotbarIndex() + 1;
      RiptideChatField itemField = this.textFields.get("itemName");
      String itemName = itemField != null ? itemField.getText().trim() : "";
      return itemName.isEmpty() ? "Uses hotbar slot " + fallbackSlot + "." : "Uses the named item, else slot " + fallbackSlot + ".";
   }

   private String currentUseItemTargetLabel() {
      RiptideChatField field = this.textFields.get("itemName");
      String item = field != null ? field.getText().trim() : "";
      RiptideChatField slotField = this.textFields.get("slot");
      String slot = slotField != null ? slotField.getText().trim() : "";
      if (!slot.isEmpty() && !item.isEmpty()) {
         return "slot #" + slot + " / named item";
      } else if (!slot.isEmpty()) {
         return "slot #" + slot;
      } else {
         return item.isEmpty() ? "the held item" : "the named item";
      }
   }

   private void toggleWaitCooldownHand() {
      this.toggleStates.put("checkMainHand", !this.toggleStates.getOrDefault("checkMainHand", true));
   }

   private String currentWaitCooldownHandLabel() {
      return this.toggleStates.getOrDefault("checkMainHand", true) ? "Hand: Main" : "Hand: Off";
   }

   private void fillWaitCooldownFromHeld() {
      if (MC.player != null) {
         RiptideChatField field = this.textFields.get("itemName");
         if (field != null) {
            if (MC.player.getMainHandItem().isEmpty()) {
               field.setText("");
               this.editorItemFields.remove("itemName");
            } else {
               ItemTarget target = ItemTarget.capture(MC.player.getMainHandItem(), MC.player.getInventory().getSelectedSlot());
               this.editorItemFields.put("itemName", target);
               field.setText(target.editorText());
            }
         }
      }
   }

   private void clearWaitCooldownItemName() {
      RiptideChatField field = this.textFields.get("itemName");
      if (field != null) {
         field.setText("");
      }

      this.editorItemFields.remove("itemName");
   }

   private void fillRotateFromCurrentView() {
      if (this.guardWorldCaptureAction()) {
         if (MC.player != null) {
            RiptideChatField yawField = this.textFields.get("yaw");
            RiptideChatField pitchField = this.textFields.get("pitch");
            if (yawField != null) {
               yawField.setText(fmtDouble(MC.player.getYRot()));
            }

            if (pitchField != null) {
               pitchField.setText(fmtDouble(MC.player.getXRot()));
            }
         }
      }
   }

   private void fillGoToFromPlayer() {
      if (this.guardWorldCaptureAction()) {
         if (MC.player != null) {
            RiptideChatField xField = this.textFields.get("pos_0");
            RiptideChatField yField = this.textFields.get("pos_1");
            RiptideChatField zField = this.textFields.get("pos_2");
            if (xField != null) {
               xField.setText(fmtDouble(MC.player.getX()));
            }

            if (yField != null) {
               yField.setText(fmtDouble(MC.player.getY()));
            }

            if (zField != null) {
               zField.setText(fmtDouble(MC.player.getZ()));
            }
         }
      }
   }

   private String buildWaitCooldownSummary() {
      RiptideChatField itemField = this.textFields.get("itemName");
      String itemName = itemField != null ? itemField.getText().trim() : "";
      String handLabel = this.toggleStates.getOrDefault("checkMainHand", true) ? "main hand" : "off hand";
      return itemName.isEmpty() ? "Waits for the " + handLabel + " cooldown." : "Waits for the named item cooldown.";
   }

   private void swapSwapSlotEndpoints() {
      boolean fromUseName = this.toggleStates.getOrDefault("fromUseItemName", false);
      boolean toUseName = this.toggleStates.getOrDefault("toUseItemName", false);
      String fromItem = this.textFields.containsKey("fromItemName") ? this.textFields.get("fromItemName").getText() : "";
      String toItem = this.textFields.containsKey("toItemName") ? this.textFields.get("toItemName").getText() : "";
      String fromSlot = this.textFields.containsKey("fromSlot") ? this.textFields.get("fromSlot").getText() : "";
      String toSlot = this.textFields.containsKey("toSlot") ? this.textFields.get("toSlot").getText() : "";
      this.toggleStates.put("fromUseItemName", toUseName);
      this.toggleStates.put("toUseItemName", fromUseName);
      RiptideChatField fromItemField = this.textFields.get("fromItemName");
      RiptideChatField toItemField = this.textFields.get("toItemName");
      RiptideChatField fromSlotField = this.textFields.get("fromSlot");
      RiptideChatField toSlotField = this.textFields.get("toSlot");
      if (fromItemField != null) {
         fromItemField.setText(toItem);
      }

      if (toItemField != null) {
         toItemField.setText(fromItem);
      }

      if (fromSlotField != null) {
         fromSlotField.setText(toSlot);
      }

      if (toSlotField != null) {
         toSlotField.setText(fromSlot);
      }
   }

   private String buildSwapSlotsSummary() {
      return "Swaps the two targets. Name mode uses the first visible match.";
   }

   private String buildClickSummary() {
      String clickType = this.currentEnumValue("clickType");
      byte var3 = -1;
      switch (clickType.hashCode()) {
         case 2332679:
            if (clickType.equals("LEFT")) {
               var3 = 0;
            }
         default:
            return switch (var3) {
               case 0 -> "Left click acts on your target.";
               default -> "Right click uses your target.";
            };
      }
   }

   private String buildWaitSlotSummary(String waitMode) {
      return switch (waitMode) {
         case "IS_EMPTY" -> "Waits until the slot (or any slot) is empty.";
         case "COUNT_AT_LEAST" -> "Waits until the slot/item hits the target count.";
         case "COUNT_BELOW" -> "Waits until the slot/item drops below the count.";
         case "ANY_CHANGE" -> "Waits for the first change in the specified slot.";
         default -> "Waits until the slot or item is present. -1 scans all slots.";
      };
   }

   private int renderFieldByKey(GuiGraphicsExtractor ctx, String key, int x, int y, int w, int mx, int my, float delta) {
      if (this.isGuiWaitAfterKey(key)) {
         return y;
      } else {
         FieldDef field = this.getField(key);
         if (field != null && this.isFieldVisible(field)) {
            this.renderRow(ctx, field, x, y, w, mx, my, delta);
            return y + this.rowH(field) + 2;
         } else {
            if ("safeClickDelayTicks".equals(key)) {
               this.clearXCarrySafeDelaySliderBounds();
            }

            return y;
         }
      }
   }

   private FieldDef getField(String key) {
      if (this.schema == null) {
         return null;
      } else {
         for (FieldDef field : this.schema.fields()) {
            if (field.key().equals(key)) {
               return field;
            }
         }

         return null;
      }
   }

   private <T> int renderSimpleSelectedList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      String label,
      List<T> values,
      Consumer<T> removeAction,
      Function<T, String> formatter,
      String emptyLabel
   ) {
      return this.renderSimpleSelectedList(
         ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, ignored -> null, values::clear, emptyLabel, false
      );
   }

   private <T> int renderSimpleSelectedList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      String label,
      List<T> values,
      Consumer<T> removeAction,
      Function<T, String> formatter,
      Function<T, Component> richFormatter,
      String emptyLabel
   ) {
      return this.renderSimpleSelectedList(
         ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, richFormatter, values::clear, emptyLabel, false
      );
   }

   private <T> int renderSimpleSelectedList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      String label,
      List<T> values,
      Consumer<T> removeAction,
      Function<T, String> formatter,
      Function<T, Component> richFormatter,
      Runnable clearAction,
      String emptyLabel
   ) {
      return this.renderSimpleSelectedList(ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, richFormatter, clearAction, emptyLabel, false);
   }

   private <T> int renderSimpleSelectedList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      String label,
      List<T> values,
      Consumer<T> removeAction,
      Function<T, String> formatter,
      Function<T, Component> richFormatter,
      Runnable clearAction,
      String emptyLabel,
      boolean editable
   ) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      int headerBtnW = 44;
      int editIdx = editable ? this.stringListEditIndex.getOrDefault(listKey, -1) : -1;
      if (editIdx >= values.size()) {
         editIdx = -1;
         this.stringListEditIndex.put(listKey, -1);
      }

      UiText.draw(ctx, this.textRenderer, label + " (" + values.size() + ")", font, RiptideColors.textSecondary(), x, y + 2, false);
      boolean canClear = !values.isEmpty();
      int clearX = x + w - headerBtnW;
      this.renderOverlayButton(ctx, clearX, y, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
         clearAction.run();
         this.stringListEditIndex.put(listKey, -1);
         DirectScrollViewport vp = this.selectedScrollViewports.get(listKey);
         if (vp != null) {
            vp.jumpTo(0);
         }
      });
      y += 14;
      int listH = 60;
      int itemW = w - 5 - 1;
      DirectScrollViewport simpleViewport = this.getOrCreateViewport(this.selectedScrollViewports, listKey, x, y, w, listH, 15, 5);
      simpleViewport.setContentHeight(values.size() * 15);
      simpleViewport.renderScrollbar(ctx, mx, my);
      if (values.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, emptyLabel, x, y, itemW);
         return y + listH + 4;
      } else {
         simpleViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = simpleViewport.getFirstVisibleRow();

         for (int i = first; i < values.size() && i <= simpleViewport.getLastVisibleRow(); i++) {
            int iy = simpleViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               T value = values.get(i);
               int removeW = 13;
               int rowW = itemW - removeW - 2;
               boolean selected = editable && i == editIdx;
               boolean hovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
               Component richLabel = richFormatter == null ? null : richFormatter.apply(value);
               if (richLabel != null) {
                  MacroTypedListControl.renderRow(
                     ctx, this.textRenderer, richLabel, UiBounds.of(x, iy, rowW, 13), hovered, selected, CompactListRenderer.RowTone.NORMAL, true
                  );
               } else {
                  MacroTypedListControl.renderRow(
                     ctx,
                     this.textRenderer,
                     Component.literal(formatter.apply(value)),
                     UiBounds.of(x, iy, rowW, 13),
                     hovered,
                     selected,
                     CompactListRenderer.RowTone.NORMAL,
                     false
                  );
               }

               if (editable) {
                  int fi = i;
                  String rawText = value instanceof String rawValue ? rawValue : formatter.apply(value);
                  this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, rowW, 13, () -> {
                     int curIdx = this.stringListEditIndex.getOrDefault(listKey, -1);
                     if (curIdx == fi) {
                        this.stringListEditIndex.put(listKey, -1);
                        this.stringListEditPendingText.put(listKey, "");
                     } else {
                        this.stringListEditIndex.put(listKey, fi);
                        this.stringListEditPendingText.put(listKey, rawText != null ? rawText : "");
                     }
                  }));
               }

               int removeX = x + rowW + 2;
               int fi2 = i;
               this.renderIconDeleteButton(ctx, removeX, iy, removeW, mx, my, () -> {
                  removeAction.accept(values.get(fi2));
                  if (editable) {
                     int curSel = this.stringListEditIndex.getOrDefault(listKey, -1);
                     if (curSel == fi2) {
                        this.stringListEditIndex.put(listKey, -1);
                        this.stringListEditPendingText.put(listKey, "");
                     } else if (curSel > fi2) {
                        this.stringListEditIndex.put(listKey, curSel - 1);
                     }
                  }

                  DirectScrollViewport vp = this.selectedScrollViewports.get(listKey);
                  if (vp != null) {
                     vp.scrollBy(-1);
                  }
               });
            }
         }

         simpleViewport.endRender(ctx);
         return y + listH + 4;
      }
   }

   private <T> void renderSearchRegistryList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      List<T> values,
      int visibleRows,
      Consumer<T> clickAction,
      Function<T, String> formatter
   ) {
      this.renderSearchRegistryList(ctx, x, y, w, mx, my, listKey, values, visibleRows, clickAction, ignored -> false, formatter);
   }

   private <T> void renderSearchRegistryList(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int mx,
      int my,
      String listKey,
      List<T> values,
      int visibleRows,
      Consumer<T> clickAction,
      Predicate<T> selectedPredicate,
      Function<T, String> formatter
   ) {
      int rowH = 13;
      int listH = visibleRows * rowH;
      int itemW = w - 5 - 1;
      DirectScrollViewport regViewport = this.getOrCreateViewport(this.catalogScrollViewports, listKey, x, y, w, listH, rowH, 5);
      regViewport.setContentHeight(values.size() * rowH);
      regViewport.renderScrollbar(ctx, mx, my);
      if (values.isEmpty()) {
         CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
         CompactListRenderer.drawEmptyState(ctx, this.textRenderer, "No matches", x, y, itemW);
      } else {
         regViewport.beginRender(ctx, this.theme.borderSoft(), 905969664);
         int first = regViewport.getFirstVisibleRow();

         for (int i = first; i < values.size() && i <= regViewport.getLastVisibleRow(); i++) {
            int iy = regViewport.getRowScreenY(i);
            if (iy != Integer.MIN_VALUE) {
               T value = values.get(i);
               String display = formatter.apply(value);
               boolean selected = selectedPredicate != null && selectedPredicate.test(value);
               boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + rowH;
               ItemStack icon = this.iconForRegistryListValue(listKey, value);
               MacroTypedListControl.renderRow(
                  ctx,
                  this.textRenderer,
                  Component.literal(display),
                  icon,
                  UiBounds.of(x, iy, itemW, rowH),
                  hovered,
                  selected,
                  selected ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                  false
               );
               this.hitRegions.add(new ActionEditorOverlay.HitRegion(x, iy, itemW, rowH, () -> clickAction.accept(value)));
            }
         }

         regViewport.endRender(ctx);
      }
   }

   private ItemStack iconForRegistryListValue(String listKey, Object value) {
      if (value instanceof String raw && !raw.isBlank()) {
         String id = raw;
         if (raw.startsWith("~")) {
            id = this.extractEntityTypeFromNearbyEntry(raw);
         }

         if (id != null && !id.isBlank()) {
            Identifier parsed = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (parsed == null) {
               return ItemStack.EMPTY;
            } else {
               String key = listKey == null ? "" : listKey.toLowerCase(Locale.ROOT);
               if (key.contains("item") || key.contains("store")) {
                  Item item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.AIR);
                  return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
               } else if (key.contains("block") || key.contains("container")) {
                  Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);
                  return block != Blocks.AIR && block.asItem() != Items.AIR ? block.asItem().getDefaultInstance() : ItemStack.EMPTY;
               } else if (key.contains("entity")) {
                  return this.entityTypeIcon(parsed);
               } else {
                  return key.contains("sound") ? this.soundListIcon(parsed) : ItemStack.EMPTY;
               }
            }
         } else {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   private ItemStack entityTypeIcon(Identifier entityId) {
      EntityType<?> entityType = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
      if (entityType == null) {
         return ItemStack.EMPTY;
      } else {
         for (Item item : BuiltInRegistries.ITEM) {
            TypedEntityData<EntityType<?>> data = (TypedEntityData<EntityType<?>>)item.components().get(DataComponents.ENTITY_DATA);
            if (data != null && data.type() == entityType) {
               return item.getDefaultInstance();
            }
         }

         return "player".equals(entityId.getPath()) ? Items.PLAYER_HEAD.getDefaultInstance() : ItemStack.EMPTY;
      }
   }

   private ItemStack soundListIcon(Identifier soundId) {
      String path = soundId.getPath().toLowerCase(Locale.ROOT);
      if (path.contains("fishing_bobber")) {
         return Items.FISHING_ROD.getDefaultInstance();
      } else if (path.contains("lava")) {
         return Items.LAVA_BUCKET.getDefaultInstance();
      } else if (path.contains("water") || path.contains("rain") || path.contains("bubble")) {
         return Items.WATER_BUCKET.getDefaultInstance();
      } else if (path.contains("firework")) {
         return Items.FIREWORK_ROCKET.getDefaultInstance();
      } else if (path.contains("experience_orb")) {
         return Items.EXPERIENCE_BOTTLE.getDefaultInstance();
      } else if (path.contains("lightning") || path.contains("thunder")) {
         return ((Item)Items.LIGHTNING_ROD.weathering().unaffected()).getDefaultInstance();
      } else if (path.contains("note_block")) {
         return Items.NOTE_BLOCK.getDefaultInstance();
      } else if (path.contains("ui_") || path.startsWith("ui.")) {
         return Items.COMPASS.getDefaultInstance();
      } else {
         return path.contains("ambient_cave") ? Items.TORCH.getDefaultInstance() : Items.NOTE_BLOCK.getDefaultInstance();
      }
   }

   private void startWaitEntityCapture() {
      if (this.guardWorldCaptureAction()) {
         if (this.targetAction instanceof WaitForEntityAction) {
            Screen previousScreen = MC.gui.screen();
            this.enterCaptureMode();
            RiptideSharedState state = RiptideSharedState.get();
            state.setCaptureCancelCallback(() -> {
               if (previousScreen != null) {
                  MC.gui.setScreen(previousScreen);
               }

               state.setEntityCaptureCallback(null);
               this.exitCaptureMode(false, false);
            });
            MC.execute(() -> {
               if (MC.gui.screen() != null) {
                  MC.gui.setScreen(null);
               }
            });
            state.setEntityCaptureSpecific(this.entitySpecificCaptureMode);
            state.setEntityCaptureCallback(payload -> MC.execute(() -> {
               List<String> selected = this.stringLists.get("entityIds");
               if (selected != null && payload != null && !payload.isBlank()) {
                  if (this.entitySpecificCaptureMode) {
                     selected.removeIf(existing -> existing.startsWith(this.entitySpecificEntryPrefix(payload)));
                  }

                  if (!selected.contains(payload)) {
                     selected.add(payload);
                  }
               }

               if (previousScreen != null) {
                  MC.gui.setScreen(previousScreen);
               }

               this.exitCaptureMode(false, false);
               RiptideOverlayManager.get().bringToFront(this);
            }));
         }
      }
   }

   private static String trimMinecraftPrefix(String value) {
      return RiptideRegistryLabels.stripNamespace(value);
   }

   private String formatEntityEntry(String entry) {
      if (entry == null || entry.isBlank()) {
         return "(unknown)";
      } else if (entry.startsWith("~")) {
         String[] parts = entry.split("~", 4);
         String rawName = parts.length >= 4 ? parts[3] : "";
         String type = parts.length >= 3 ? RiptideRegistryLabels.entity(parts[2]) : "?";
         String uuid = parts.length >= 2 ? parts[1] : "";
         String suffix = uuid.length() >= 4 ? uuid.substring(uuid.length() - 4) : uuid;
         String name = rawName == null ? "" : rawName.trim();
         return !name.isEmpty() && !name.equalsIgnoreCase(type) && !name.equalsIgnoreCase(trimMinecraftPrefix(type))
            ? name + " (" + type + " #" + suffix + ")"
            : type + " #" + suffix;
      } else {
         return RiptideRegistryLabels.entity(entry);
      }
   }

   private List<String> getNearbyEntityEntries() {
      boolean supportedAction = this.targetAction instanceof WaitForEntityAction
         || this.targetAction instanceof LookAtBlockAction
         || this.targetAction instanceof OpenContainerAction
         || this.targetAction instanceof InteractEntityAction;
      if (supportedAction && MC.player != null && MC.level != null) {
         List<String> result = new ArrayList<>();

         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity != MC.player && !(MC.player.distanceTo(entity) > 16.0)) {
               String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
               String uuid = entity.getStringUUID();
               String displayName = entity.getDisplayName().getString().replaceAll("§.", "").trim();
               result.add("~" + uuid + "~" + typeId + "~" + displayName);
            }
         }

         result.sort(Comparator.comparingDouble(this::distanceForNearbyEntityEntry));
         return result;
      } else {
         return Collections.emptyList();
      }
   }

   private boolean canOpenContainerEntity(Entity entity) {
      return entity == null ? false : entity instanceof AbstractMinecartContainer || entity instanceof ChestBoat || entity instanceof AbstractHorse;
   }

   private double distanceForNearbyEntityEntry(String entry) {
      if (MC.player != null && entry != null && entry.startsWith("~")) {
         String[] parts = entry.split("~", 4);
         if (parts.length >= 2 && MC.level != null) {
            try {
               UUID uuid = UUID.fromString(parts[1]);
               Entity entity = null;

               for (Entity candidate : MC.level.entitiesForRendering()) {
                  if (uuid.equals(candidate.getUUID())) {
                     entity = candidate;
                     break;
                  }
               }

               return entity != null ? MC.player.distanceTo(entity) : Double.MAX_VALUE;
            } catch (Exception var7) {
               return Double.MAX_VALUE;
            }
         } else {
            return Double.MAX_VALUE;
         }
      } else {
         return Double.MAX_VALUE;
      }
   }

   private String extractEntityTypeFromNearbyEntry(String entry) {
      if (entry != null && entry.startsWith("~")) {
         String[] parts = entry.split("~", 4);
         return parts.length >= 3 ? parts[2] : "";
      } else {
         return "";
      }
   }

   private String entitySpecificEntryPrefix(String entry) {
      if (entry != null && entry.startsWith("~")) {
         String[] parts = entry.split("~", 4);
         return parts.length >= 2 ? "~" + parts[1] + "~" : "";
      } else {
         return "";
      }
   }

   private String formatNearbyEntityEntry(String entry) {
      String label = this.formatEntityEntry(entry);
      double dist = this.distanceForNearbyEntityEntry(entry);
      return dist == Double.MAX_VALUE ? label : label + " (" + String.format(Locale.ROOT, "%.1fm", dist) + ")";
   }

   private List<BlockPos> getNearbyContainerPositions() {
      if (MC.player != null && MC.level != null) {
         BlockPos center = MC.player.blockPosition();
         List<BlockPos> found = new ArrayList<>();
         int range = 10;

         for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
               for (int dz = -range; dz <= range; dz++) {
                  BlockPos pos = center.offset(dx, dy, dz);
                  if (this.isLikelyContainerCandidate(pos)) {
                     found.add(pos);
                  }
               }
            }
         }

         found.sort(Comparator.comparingDouble(posx -> Vec3.atCenterOf(center).distanceToSqr(Vec3.atCenterOf(posx))));
         return found;
      } else {
         return Collections.emptyList();
      }
   }

   private boolean isLikelyContainerCandidate(BlockPos pos) {
      if (MC.level != null && pos != null) {
         BlockState state = MC.level.getBlockState(pos);
         if (state == null || state.isAir()) {
            return false;
         } else if (state.getMenuProvider(MC.level, pos) != null) {
            return true;
         } else {
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
            return !path.contains("chest")
                  && !path.contains("barrel")
                  && !path.contains("shulker")
                  && !path.contains("hopper")
                  && !path.contains("dispenser")
                  && !path.contains("dropper")
                  && !path.contains("furnace")
                  && !path.contains("smoker")
                  && !path.contains("blast_furnace")
                  && !path.contains("brewing")
                  && !path.contains("anvil")
                  && !path.contains("beacon")
                  && !path.contains("crafting")
                  && !path.contains("loom")
                  && !path.contains("smithing")
                  && !path.contains("stonecutter")
                  && !path.contains("cartography")
                  && !path.contains("grindstone")
                  && !path.contains("enchant")
                  && !path.contains("ender_chest")
                  && !path.contains("spawner")
               ? blockId.contains("container") || blockId.contains("gui") || blockId.contains("menu")
               : true;
         }
      } else {
         return false;
      }
   }

   private String formatContainerEntry(BlockPos pos) {
      if (pos == null) {
         return "(unknown)";
      } else {
         String blockName = MC.level != null ? MC.level.getBlockState(pos).getBlock().getName().getString() : "Container";
         return blockName + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
      }
   }

   private void fillBlockPosField(String fieldKey, BlockPos pos) {
      if (fieldKey != null && pos != null) {
         RiptideChatField fx = this.textFields.get(fieldKey + "_0");
         RiptideChatField fy = this.textFields.get(fieldKey + "_1");
         RiptideChatField fz = this.textFields.get(fieldKey + "_2");
         if (fx != null) {
            fx.setText(String.valueOf(pos.getX()));
         }

         if (fy != null) {
            fy.setText(String.valueOf(pos.getY()));
         }

         if (fz != null) {
            fz.setText(String.valueOf(pos.getZ()));
         }
      }
   }

   private boolean isCurrentBlockPosField(String fieldKey, BlockPos pos) {
      if (fieldKey != null && pos != null) {
         RiptideChatField fx = this.textFields.get(fieldKey + "_0");
         RiptideChatField fy = this.textFields.get(fieldKey + "_1");
         RiptideChatField fz = this.textFields.get(fieldKey + "_2");
         if (fx != null && fy != null && fz != null) {
            try {
               int x = (int)Math.round(Double.parseDouble(fx.getText().trim()));
               int y = (int)Math.round(Double.parseDouble(fy.getText().trim()));
               int z = (int)Math.round(Double.parseDouble(fz.getText().trim()));
               return x == pos.getX() && y == pos.getY() && z == pos.getZ();
            } catch (NumberFormatException var9) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private List<String> getOrCreateWaitPacketTargets() {
      return this.stringLists.computeIfAbsent("packetNames", ignored -> new ArrayList<>());
   }

   private void openWaitPacketMatchSelector(boolean c2s) {
      Consumer<Class<? extends Packet<?>>> add = packetClass -> {
         if (packetClass != null) {
            if (this.waitPacketMatchRules == null) {
               this.waitPacketMatchRules = new ArrayList<>();
            }

            WaitPacketMatchAction.Rule rule = new WaitPacketMatchAction.Rule();
            rule.direction = c2s ? WaitPacketMatchAction.Direction.C2S : WaitPacketMatchAction.Direction.S2C;
            String name = RiptidePacketRegistry.getName(packetClass);
            rule.packetName = name != null && !name.isBlank() ? name : RiptidePacketNamer.getFriendlyName(packetClass);
            this.waitPacketMatchRules.add(rule);
            this.waitPacketMatchEditIndex = this.waitPacketMatchRules.size() - 1;
            this.syncWaitPacketMatchValueField();
         }
      };
      if (c2s) {
         this.packetSelectorOverlay.openC2S(add);
      } else {
         this.packetSelectorOverlay.openS2C(add);
      }
   }

   private void openPacketNameFieldSelector(String fieldKey) {
      RiptideChatField field = this.textFields.get(fieldKey);
      Consumer<Class<? extends Packet<?>>> set = packetClass -> {
         if (packetClass != null && field != null) {
            String name = RiptidePacketRegistry.getName(packetClass);
            field.setText(name != null && !name.isBlank() ? name : RiptidePacketNamer.getFriendlyName(packetClass));
         }
      };
      String direction = this.currentEnumValue("direction");
      if ("S2C".equals(direction)) {
         this.packetSelectorOverlay.openS2C(set);
      } else if ("ANY".equals(direction)) {
         this.packetSelectorOverlay.open(set);
      } else {
         this.packetSelectorOverlay.openC2S(set);
      }
   }

   private void syncWaitPacketMatchEditedRule() {
      if (this.waitPacketMatchRules != null && this.waitPacketMatchEditIndex >= 0 && this.waitPacketMatchEditIndex < this.waitPacketMatchRules.size()) {
         RiptideChatField valueField = this.textFields.get("_wpm_value");
         if (valueField != null) {
            this.waitPacketMatchRules.get(this.waitPacketMatchEditIndex).value = valueField.getText();
         }
      }
   }

   private void syncWaitPacketMatchValueField() {
      RiptideChatField valueField = this.textFields.get("_wpm_value");
      if (valueField != null) {
         if (this.waitPacketMatchRules != null && this.waitPacketMatchEditIndex >= 0 && this.waitPacketMatchEditIndex < this.waitPacketMatchRules.size()) {
            valueField.setText(
               this.waitPacketMatchRules.get(this.waitPacketMatchEditIndex).value == null
                  ? ""
                  : this.waitPacketMatchRules.get(this.waitPacketMatchEditIndex).value
            );
         } else {
            valueField.setText("");
         }
      }
   }

   private String waitPacketMatchFieldLabel(WaitPacketMatchAction.Rule rule) {
      return rule != null && rule.fieldName != null && !rule.fieldName.isBlank() ? rule.fieldName : "Packet Only";
   }

   private Component formatWaitPacketMatchRule(WaitPacketMatchAction.Rule rule) {
      if (rule == null) {
         return Component.literal("(empty)");
      } else {
         String label = rule.direction
            + " "
            + (rule.packetName != null && !rule.packetName.isBlank() ? RiptidePacketNamer.getFriendlyName(rule.packetName) : "Any Packet");
         if (rule.fieldName != null && !rule.fieldName.isBlank()) {
            label = label + " / " + rule.fieldName;
         }

         return Component.literal(label);
      }
   }

   private void cycleWaitPacketMatchField(WaitPacketMatchAction.Rule rule) {
      if (rule != null) {
         Class<? extends Packet<?>> packetClass = this.resolveWaitPacketMatchClass(rule);
         List<String> fields = new ArrayList<>(WaitPacketMatchAction.packetFieldNames(packetClass));
         fields.add(0, "");
         int idx = fields.indexOf(rule.fieldName == null ? "" : rule.fieldName);
         rule.fieldName = fields.get((idx + 1 + fields.size()) % fields.size());
         rule.operator = rule.fieldName.isBlank() ? WaitPacketMatchAction.Operator.EXISTS : rule.operator;
         List<String> options = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
         if (!options.isEmpty() && (rule.value == null || rule.value.isBlank() || !options.contains(rule.value))) {
            rule.value = options.get(0);
         }

         this.syncWaitPacketMatchValueField();
      }
   }

   private Class<? extends Packet<?>> resolveWaitPacketMatchClass(WaitPacketMatchAction.Rule rule) {
      if (rule == null) {
         return null;
      } else {
         String direction = rule.direction == WaitPacketMatchAction.Direction.ANY ? "" : rule.direction.name();
         return this.resolvePacketClassForTarget(direction, rule.packetName == null ? "" : rule.packetName);
      }
   }

   private static <E extends Enum<E>> E nextEnum(E current, E[] values) {
      if (values != null && values.length != 0) {
         int idx = current == null ? -1 : current.ordinal();
         return values[(idx + 1 + values.length) % values.length];
      } else {
         return current;
      }
   }

   private List<String> sanitizeWaitPacketTargets(List<String> rawTargets) {
      List<String> sanitized = new ArrayList<>();

      for (String rawTarget : rawTargets == null ? Collections.emptyList() : rawTargets) {
         String normalized = this.normalizeWaitPacketTarget(rawTarget);
         if (!normalized.isEmpty() && !sanitized.contains(normalized)) {
            sanitized.add(normalized);
         }
      }

      return sanitized;
   }

   private String normalizeWaitPacketTarget(String target) {
      String normalized = WaitForPacketAction.normalizeTarget(target);
      if (normalized.isEmpty()) {
         return "";
      } else if (!WaitForPacketAction.getDirection(normalized).isEmpty()) {
         return normalized;
      } else {
         Class<? extends Packet<?>> packetClass = this.resolvePacketClassForTarget("", normalized);
         if (packetClass == null) {
            return normalized;
         } else if (RiptidePacketRegistry.getC2SPackets().contains(packetClass)) {
            return this.buildWaitPacketTarget("C2S", packetClass);
         } else {
            return RiptidePacketRegistry.getS2CPackets().contains(packetClass) ? this.buildWaitPacketTarget("S2C", packetClass) : normalized;
         }
      }
   }

   private List<String> getWaitPacketTargets(String direction) {
      List<String> filtered = this.catalogFilteredValues
         .computeIfAbsent("wait_packet_targets_" + direction.toUpperCase(Locale.ROOT), ignored -> new ArrayList<>());
      filtered.clear();

      for (String target : this.getOrCreateWaitPacketTargets()) {
         String normalized = this.normalizeWaitPacketTarget(target);
         if (!normalized.isEmpty() && direction.equalsIgnoreCase(WaitForPacketAction.getDirection(normalized))) {
            filtered.add(normalized);
         }
      }

      return filtered;
   }

   private String formatWaitPacketTarget(String target) {
      return WaitForPacketAction.getDisplayLabel(target);
   }

   private void clearWaitPacketTargets(String direction) {
      this.getOrCreateWaitPacketTargets()
         .removeIf(target -> direction.equalsIgnoreCase(WaitForPacketAction.getDirection(this.normalizeWaitPacketTarget(target))));
      DirectScrollViewport vpC2s = this.selectedScrollViewports.get("wait_packet_c2s");
      if (vpC2s != null) {
         vpC2s.jumpTo(0);
      }

      DirectScrollViewport vpS2c = this.selectedScrollViewports.get("wait_packet_s2c");
      if (vpS2c != null) {
         vpS2c.jumpTo(0);
      }
   }

   private void removeWaitPacketTarget(String target) {
      String normalized = this.normalizeWaitPacketTarget(target);
      if (!normalized.isEmpty()) {
         this.getOrCreateWaitPacketTargets().removeIf(existing -> normalized.equals(this.normalizeWaitPacketTarget(existing)));
      }
   }

   private void openWaitPacketSelector(boolean c2s) {
      if (c2s) {
         this.packetSelectorOverlay
            .openToggleC2S((packetClass, selected) -> this.setWaitPacketTargetSelected("C2S", packetClass, selected), this.getSelectedWaitPacketClasses("C2S"));
      } else {
         this.packetSelectorOverlay
            .openToggleS2C((packetClass, selected) -> this.setWaitPacketTargetSelected("S2C", packetClass, selected), this.getSelectedWaitPacketClasses("S2C"));
      }
   }

   private List<Class<? extends Packet<?>>> getSelectedWaitPacketClasses(String direction) {
      List<Class<? extends Packet<?>>> selected = new ArrayList<>();

      for (String target : this.getWaitPacketTargets(direction)) {
         Class<? extends Packet<?>> packetClass = this.resolvePacketClassForTarget(direction, target);
         if (packetClass != null && !selected.contains(packetClass)) {
            selected.add(packetClass);
         }
      }

      return selected;
   }

   private void setWaitPacketTargetSelected(String direction, Class<? extends Packet<?>> packetClass, boolean selected) {
      String target = this.buildWaitPacketTarget(direction, packetClass);
      if (!target.isEmpty()) {
         List<String> targets = this.getOrCreateWaitPacketTargets();
         if (selected) {
            if (!targets.stream().map(this::normalizeWaitPacketTarget).anyMatch(target::equals)) {
               targets.add(target);
            }
         } else {
            targets.removeIf(existing -> target.equals(this.normalizeWaitPacketTarget(existing)));
         }
      }
   }

   private void loadWaitPacketTargetsFromQueue() {
      List<RiptideSharedState.QueuedPacket> queue = RiptideSharedState.get().getDelayedPackets();
      if (queue != null && !queue.isEmpty()) {
         List<String> targets = this.getOrCreateWaitPacketTargets();
         List<String> merged = this.sanitizeWaitPacketTargets(targets);
         int before = merged.size();

         for (RiptideSharedState.QueuedPacket queuedPacket : queue) {
            String target = this.buildWaitPacketTarget(queuedPacket);
            if (!target.isEmpty() && !merged.contains(target)) {
               merged.add(target);
            }
         }

         targets.clear();
         targets.addAll(merged);
         int added = Math.max(0, merged.size() - before);
         RiptideClientMessaging.sendPrefixed(
            added == 0 ? "Queue did not add any new packet targets" : "Added " + added + " packet target" + (added == 1 ? "" : "s") + " from queue"
         );
      } else {
         RiptideClientMessaging.sendPrefixed("Queue is empty");
      }
   }

   private String buildWaitPacketTarget(RiptideSharedState.QueuedPacket queuedPacket) {
      if (queuedPacket != null && queuedPacket.packet != null) {
         Class<? extends Packet<?>> packetClass = queuedPacket.packet.getClass();
         if (RiptidePacketRegistry.getC2SPackets().contains(packetClass)) {
            return this.buildWaitPacketTarget("C2S", packetClass);
         } else {
            return RiptidePacketRegistry.getS2CPackets().contains(packetClass) ? this.buildWaitPacketTarget("S2C", packetClass) : "";
         }
      } else {
         return "";
      }
   }

   private String buildWaitPacketTarget(String direction, Class<? extends Packet<?>> packetClass) {
      if (packetClass == null) {
         return "";
      } else {
         String name = RiptidePacketRegistry.getName(packetClass);
         if (name == null || name.isBlank()) {
            name = RiptidePacketNamer.getFriendlyName(packetClass);
         }

         return WaitForPacketAction.withDirection(direction, name);
      }
   }

   private Class<? extends Packet<?>> resolvePacketClassForTarget(String direction, String target) {
      String packetName = WaitForPacketAction.getPacketName(target);
      if (packetName.isBlank()) {
         return null;
      } else {
         Class<? extends Packet<?>> direct = RiptidePacketRegistry.getPacket(packetName);
         if (direct != null) {
            if (direction.isBlank()) {
               return direct;
            }

            if ("C2S".equalsIgnoreCase(direction) && RiptidePacketRegistry.getC2SPackets().contains(direct)) {
               return direct;
            }

            if ("S2C".equalsIgnoreCase(direction) && RiptidePacketRegistry.getS2CPackets().contains(direct)) {
               return direct;
            }
         }

         List<Class<? extends Packet<?>>> pool = new ArrayList<>();
         if ("C2S".equalsIgnoreCase(direction)) {
            pool.addAll(RiptidePacketRegistry.getC2SPackets());
         } else if ("S2C".equalsIgnoreCase(direction)) {
            pool.addAll(RiptidePacketRegistry.getS2CPackets());
         } else {
            pool.addAll(RiptidePacketRegistry.getC2SPackets());
            pool.addAll(RiptidePacketRegistry.getS2CPackets());
         }

         for (Class<? extends Packet<?>> candidate : pool) {
            String registryName = RiptidePacketRegistry.getName(candidate);
            if (this.packetNameMatches(packetName, registryName)) {
               return candidate;
            }

            if (this.packetNameMatches(packetName, RiptidePacketNamer.getFriendlyName(candidate))) {
               return candidate;
            }

            if (this.packetNameMatches(packetName, candidate.getSimpleName())) {
               return candidate;
            }
         }

         return null;
      }
   }

   private boolean packetNameMatches(String expected, String candidate) {
      if (expected != null && !expected.isBlank() && candidate != null && !candidate.isBlank()) {
         String normalizedExpected = expected.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
         String normalizedCandidate = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
         if (normalizedExpected.isEmpty() || normalizedCandidate.isEmpty()) {
            return false;
         } else if (normalizedExpected.equals(normalizedCandidate)) {
            return true;
         } else {
            if (normalizedCandidate.endsWith("packet")) {
               String stripped = normalizedCandidate.substring(0, normalizedCandidate.length() - "packet".length());
               if (normalizedExpected.equals(stripped)) {
                  return true;
               }
            }

            return normalizedExpected.endsWith(normalizedCandidate) || normalizedCandidate.endsWith(normalizedExpected);
         }
      } else {
         return false;
      }
   }

   private List<String> getPacketNames(boolean c2s) {
      List<String> names = new ArrayList<>();

      for (Class<? extends Packet<?>> packetClass : c2s ? RiptidePacketRegistry.getC2SPackets() : RiptidePacketRegistry.getS2CPackets()) {
         String name = RiptidePacketRegistry.getName(packetClass);
         if (name != null && !name.isBlank()) {
            names.add(name);
         }
      }

      Collections.sort(names);
      return names;
   }

   private int renderBlockPosWithoutCapture(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my, float delta) {
      Identifier font = this.theme.fontFor(UiTone.BODY);
      this.drawLabel(ctx, field.label(), x, y, w, font);
      y += 13;
      int bw = (w - 4) / 3;

      for (int i = 0; i < 3; i++) {
         RiptideChatField f = this.textFields.get(field.key() + "_" + i);
         if (f != null) {
            int fx = x + i * (bw + 2);
            f.setX(fx);
            f.setY(y + 1);
            f.setWidth(bw);
            f.render(ctx, mx, my, delta);
         }
      }

      return y + 15 + 2;
   }

   private static List<String> getAllSoundIds() {
      if (ALL_SOUND_IDS == null) {
         ALL_SOUND_IDS = new ArrayList<>();

         for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent);
            if (id != null) {
               ALL_SOUND_IDS.add(id.toString());
            }
         }

         Collections.sort(ALL_SOUND_IDS);
      }

      return ALL_SOUND_IDS;
   }

   private static List<String> getAllEntityIds() {
      if (ALL_ENTITY_IDS == null) {
         ALL_ENTITY_IDS = new ArrayList<>();

         for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            ALL_ENTITY_IDS.add(id.toString());
         }

         Collections.sort(ALL_ENTITY_IDS);
      }

      return ALL_ENTITY_IDS;
   }

   private static List<String> getAllItemIds() {
      if (ALL_ITEM_IDS == null) {
         ALL_ITEM_IDS = new ArrayList<>();

         for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            ALL_ITEM_IDS.add(id.toString());
         }

         Collections.sort(ALL_ITEM_IDS);
      }

      return ALL_ITEM_IDS;
   }

   private int rowH(FieldDef field) {
      if (field != null && "safeClickDelayTicks".equals(field.key())) {
         return 29;
      } else if (this.isWaitChatPatternField(field)) {
         return 53;
      } else {
         return switch (field.type()) {
            case MACRO_SELECT -> 92;
            default -> 15;
            case BLOCK_POS -> 30;
            case STRING_LIST -> {
               if (this.isRacePickerList(field)) {
                  yield 105;
               } else if (this.isMultiAccountList(field)) {
                  yield 105;
               } else if (field.captureMode() == CaptureMode.BLOCK_CATALOG) {
                  yield 165;
               } else {
                  int extraEditorRow = this.isXCarryListKey(field.key()) && this.stringListEditIndex.getOrDefault(field.key(), -1) >= 0 ? 16 : 0;
                  yield 89 + extraEditorRow;
               }
            }
         };
      }
   }

   private int computeContentH() {
      if (this.itemAction != null) {
         int h = 104;
         h += 16;
         if (this.isEditingNameOnlyItemTargetingAction()) {
            h += 17;
         }

         h += 17;
         if (this.toggleStates.getOrDefault("item_waitForGuiBefore", false) || this.toggleStates.getOrDefault("item_waitForGuiAfter", false)) {
            h += 17;
         }

         h += 17;
         h += 17;
         if (this.toggleStates.getOrDefault("item_useClickDelay", false)) {
            h += 17;
         }

         return h;
      } else if (this.payloadAction != null) {
         int hx = 4;
         hx += 13;
         hx += 24;
         hx += 18;
         hx += 18;
         hx += 17;
         hx += 13;
         hx += (this.payloadTabIndex == 0 ? 143 : 152) + 4;
         hx += 14;
         hx += 20;
         hx += 22;
         if (this.isPayloadConfigPhase()) {
            hx += 22;
         }

         return hx;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SEND_PACKET) {
         int hx = 84;
         hx += 17;
         hx += 17;
         if (this.hasGuiWaitEnabled()) {
            hx += 17;
         }

         return hx + 120;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SOUND) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("waitForGuiBefore")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForGuiAfter")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitGuiName")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("checkDistance")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("maxDistance")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("saveAs")) ? 17 : 0;
         hx += 78;
         hx += 18;
         return hx + 78;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_ENTITY) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("checkMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("radius")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("centerOnPlayer")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("pos")) ? this.rowH(this.getField("pos")) + 2 : 0;
         hx += this.isFieldVisible(this.getField("mustBeLookingAt")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("saveAs")) ? 17 : 0;
         hx += 78;
         hx += 18;
         hx += 62;
         return hx + 52;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.LOOK_AT_BLOCK) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("targetMode")) ? 17 : 0;
         String mode = this.currentEnumValue("targetMode");
         if ("BLOCK".equals(mode)) {
            hx += this.isFieldVisible(this.getField("searchRadius")) ? 17 : 0;
            hx += this.rowH(this.getField("blockIds")) + 2;
         } else if ("ENTITY".equals(mode)) {
            hx += this.isFieldVisible(this.getField("searchRadius")) ? 17 : 0;
            hx += 78;
            hx += 18;
            hx += 66;
            hx += 56;
         } else {
            hx += this.rowH(this.getField("blockPos")) + 2;
         }

         hx += this.isFieldVisible(this.getField("smooth")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("smoothness")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForCompletion")) ? 17 : 0;
         return hx + 14;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.ROTATE) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("yaw")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("pitch")) ? 17 : 0;
         hx += 18;
         hx += this.isFieldVisible(this.getField("smooth")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("smoothness")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForCompletion")) ? 17 : 0;
         return hx + 14;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.GO_TO) {
         int hx = 4;
         hx += this.rowH(this.getField("pos")) + 2;
         hx += 18;
         hx += this.isFieldVisible(this.getField("waitForArrival")) ? 17 : 0;
         return hx + 14;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.USE_ITEM) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("itemName")) ? 17 : 0;
         hx += 18;
         hx += this.isFieldVisible(this.getField("useMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForFinish")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("sneak")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("holdTicks")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("useCount")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForGui")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("guiName")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("mode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("targetItems")) ? 96 : 0;
         hx += this.isFieldVisible(this.getField("openMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("openCommand")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("containerPos")) ? this.rowH(this.getField("containerPos")) + 2 : 0;
         if ("CONTAINER".equals(this.currentEnumValue("openMode"))) {
            hx += 56;
         }

         hx += this.isFieldVisible(this.getField("dupeVector")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("iterations")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("maxTransferAttempts")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("transferRetryDelayMs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("spamCount")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("spamDelayMs")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SWAP_SLOTS) {
         int hx = 20;
         hx += this.isFieldVisible(this.getField("fromUseItemName")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("fromItemName")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("fromSlot")) ? 17 : 0;
         hx += 24;
         hx += this.isFieldVisible(this.getField("toUseItemName")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("toItemName")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("toSlot")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CLICK) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("clickType")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("clickCount")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForGuiBefore")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("guiName")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DISCONNECT) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("mode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("delayMs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("lagMethod")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("kickMethod")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("packetCount")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("useNextAction")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("trigger")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("tolerance")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("bufferMs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("timeoutSec")) ? 17 : 0;
         return hx + 11;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_GUI) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("waitMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("guiType")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("matchMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("guiTitle")) ? 17 : 0;
         hx += 18;
         hx += this.isFieldVisible(this.getField("saveAs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("timeoutMs")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.SELECT_SLOT) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("itemName")) ? 17 : 0;
         hx += 18;
         hx += 30;
         hx += this.isFieldVisible(this.getField("strategy")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("outputVariable")) ? 17 : 0;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_COOLDOWN) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("itemName")) ? 17 : 0;
         hx += 18;
         hx += 18;
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_CHAT) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("pattern")) ? this.rowH(this.getField("pattern")) + 2 : 0;
         hx += 18;
         hx += this.isFieldVisible(this.getField("matchMode")) ? 17 : 0;
         hx += "MATCH".equals(this.currentEnumValue("matchMode")) ? 29 : 0;
         hx += this.isFieldVisible(this.getField("saveAs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("timeoutMs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForGuiBefore")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForGuiAfter")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitGuiName")) ? 17 : 0;
         hx += 18;
         hx += 18;
         hx += 13 + this.waitChatHistoryListHeight();
         return hx + 16;
      } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
         int hx = 4;
         hx += this.isFieldVisible(this.getField("source")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("autofillCommand")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("autofillTimeoutMs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("autofillCacheList")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("saveAs")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listSelection")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listPickPosition")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listFilter")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listFilterText")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listExcludeText")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("listStripPrefix")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("excludeSelf")) ? 17 : 0;
         if ("RECENT_CHAT".equals(this.currentEnumValue("source"))) {
            hx += 13 + this.captureChatHistoryListHeight(MacroExecutor.getRecentChatMessages().size()) + 2;
         } else if ("SCOREBOARD".equals(this.currentEnumValue("source"))) {
            int lineCount = CaptureValueAction.scoreboardLines(MC).size();
            hx += 13 + (lineCount <= 0 ? 24 : Math.min(5, lineCount) * 22) + 2;
         } else if ("TABLIST".equals(this.currentEnumValue("source"))) {
            int matchCount = this.captureTablistMatchCount();
            hx += 13 + (matchCount <= 0 ? 24 : Math.min(5, matchCount) * 14) + 2;
         }

         hx += this.isFieldVisible(this.getField("exampleText")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("selectedText")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("itemFilter")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("slot")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("itemText")) ? 17 : 0;
         hx += 60;
         hx += this.isFieldVisible(this.getField("numberMode")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("numberModifier")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("numberModifierAmount")) ? 17 : 0;
         hx += this.isFieldVisible(this.getField("waitForTrigger")) ? 17 : 0;
         return hx + 22;
      } else if (this.targetAction == null
         || this.targetAction.getType() != MacroActionType.OPEN_CONTAINER && this.targetAction.getType() != MacroActionType.INTERACT_ENTITY) {
         if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
            hx += 46;
            hx += this.isFieldVisible(this.getField("blockPos")) ? this.rowH(this.getField("blockPos")) + 2 : 0;
            hx += this.isFieldVisible(this.getField("manualDirection")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("direction")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("sneak")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("sneakMode")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("interact")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("interactTiming")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("interactCustomMs")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("waitForGuiBefore")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("waitForGuiAfter")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("guiName")) ? 17 : 0;
            return hx + 36;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
            int hx = 4;
            hx += 17;
            hx += 17;
            hx += 80;
            hx += 16;
            hx += 16;
            return hx + 11;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_INVENTORY_PREDICATE) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("listenDuringPreviousAction")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("condition")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("itemName")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("count")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("slot")) ? 35 : 0;
            hx += this.isFieldVisible(this.getField("timeoutMs")) ? 17 : 0;
            return hx + (this.isFieldVisible(this.getField("saveAs")) ? 17 : 0);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DELAY_PACKETS) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("mode")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("flushOnDisable")) ? 17 : 0;
            if ("ENABLE".equals(this.currentEnumValue("mode"))) {
               hx += 54;
            }

            return hx + 8;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.MINE) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
            hx += this.rowH(this.getField("targetBlocks")) + 2 + 4;
            hx += this.isFieldVisible(this.getField("stopInventoryFull")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("stopSlotsUsed")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("slotsUsedThreshold")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("stopMinedCount")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("minedCountTarget")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("stopAfterTime")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("timeoutSeconds")) ? 17 : 0;
            return hx + 14;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PAY) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("commandTemplate")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("amountInput")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("divideEnabled")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("probeHidden")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("delayEnabled")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("delayMs")) ? 17 : 0;
            hx += 78;
            hx += 18;
            RiptideChatField paySearch = this.addFields.get("players");
            int payRows = this.payPlayerScanPerformed ? Math.max(1, this.buildPayRows(paySearch != null ? paySearch.getText() : "").size()) : 1;
            hx += payRows * 13 + 4;
            return hx + 44;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.STORE_ITEM) {
            int hx = 4;
            hx += this.isFieldVisible(this.getField("mode")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("allItems")) ? 17 : 0;
            if (!this.toggleStates.getOrDefault("allItems", false)) {
               hx += 78;
               hx += 18;
               hx += 82;
               hx += 22;
            }

            hx += this.isFieldVisible(this.getField("persistent")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("delayTicks")) ? 17 : 0;
            hx += this.isFieldVisible(this.getField("closeAfter")) ? 17 : 0;
            return hx + (this.isFieldVisible(this.getField("closeSendPkt")) ? 17 : 0);
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.TOGGLE_MODULE) {
            int hx = 4;
            hx += 78;
            hx += 18;
            hx += this.meteorModuleNames.isEmpty() ? 94 : 110;
            return hx + 14;
         } else if (this.craftEntries != null) {
            return 152;
         } else if (this.dropAction != null) {
            int hx = 104;
            hx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
            hx += 22;
            hx += 17;
            hx += 17;
            hx += 17;
            if (this.toggleStates.getOrDefault("drop_waitForGuiBefore", false) || this.toggleStates.getOrDefault("drop_waitForGuiAfter", false)) {
               hx += 17;
            }

            return hx;
         } else if (this.lanStepEntries != null) {
            boolean filterByUser = this.toggleStates.getOrDefault("lan_filterByUser", false);
            int hx = 38;
            if (!filterByUser || this.lanStepEntries.isEmpty()) {
               hx += 17;
            }

            hx += 12;
            if (filterByUser) {
               if (!this.lanStepEntries.isEmpty()) {
                  int visibleRows = Math.min(3, Math.max(1, this.lanStepEntries.size()));
                  hx += 13 + visibleRows * 15;
               } else {
                  hx += 12;
               }

               hx += 22;
            }

            return hx;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET) {
            return 251;
         } else if (this.targetAction != null && this.targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
            int rows = this.waitPacketMatchRules == null ? 1 : Math.max(1, Math.min(4, Math.max(1, this.waitPacketMatchRules.size())));
            int hxx = 82 + rows * 15 + 5;
            if (this.waitPacketMatchRules != null && !this.waitPacketMatchRules.isEmpty()) {
               int idx = Math.max(0, Math.min(this.waitPacketMatchEditIndex, this.waitPacketMatchRules.size() - 1));
               WaitPacketMatchAction.Rule rule = this.waitPacketMatchRules.get(idx);
               hxx += 34;
               if (rule.fieldName != null && !rule.fieldName.isBlank()) {
                  hxx += 17;
                  if (rule.operator != WaitPacketMatchAction.Operator.EXISTS) {
                     hxx += 17;
                  }
               }
            } else {
               hxx += 11;
            }

            return hxx;
         } else if (this.schema == null) {
            return 0;
         } else {
            int hxx = 4;

            for (FieldDef field : this.schema.fields()) {
               if (field.type() != FieldType.STRING_LIST && !this.isGuiWaitAfterKey(field.key()) && this.isFieldVisible(field)) {
                  hxx += this.rowH(field) + 2;
               }
            }

            for (FieldDef fieldx : this.schema.fields()) {
               if (fieldx.type() == FieldType.STRING_LIST && !this.isGuiWaitAfterKey(fieldx.key()) && this.isFieldVisible(fieldx)) {
                  hxx += this.rowH(fieldx) + 2;
               }
            }

            if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PACKET) {
               hxx += 52;
            }

            if (this.targetAction != null && this.targetAction.getType() == MacroActionType.DELAY_PACKETS) {
               hxx += 18;
            }

            return hxx;
         }
      } else {
         int hxx = 4;
         hxx += this.isFieldVisible(this.getField("packetOrder")) ? 17 : 0;
         hxx += this.isFieldVisible(this.getField("targetMode")) ? 17 : 0;
         hxx += this.isFieldVisible(this.getField("pos")) ? this.rowH(this.getField("pos")) + 2 : 0;
         hxx += this.isFieldVisible(this.getField("entityTargets")) ? this.rowH(this.getField("entityTargets")) + 2 : 0;
         hxx += this.isFieldVisible(this.getField("waitForGuiBefore")) ? 17 : 0;
         hxx += this.isFieldVisible(this.getField("waitForGuiAfter")) ? 17 : 0;
         hxx += this.isFieldVisible(this.getField("guiName")) ? 17 : 0;
         String targetMode = this.currentEnumValue("targetMode");
         hxx += 12;
         if ("ENTITY".equals(targetMode)) {
            hxx += 52;
         } else if ("BLOCK".equals(targetMode)) {
            hxx += 39;
         }

         return hxx;
      }
   }

   private boolean isEditingPickupAllItemAction() {
      return this.itemAction != null
         && this.itemEditIndex >= 0
         && this.itemEditIndex < this.itemAction.itemNames.size()
         && this.itemAction.getItemAction(this.itemEditIndex) == RiptideDropAction.PICKUP_ALL;
   }

   private int computeDisconnectMaxH() {
      int h = 4;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      h += 17;
      return h + 11;
   }

   private void drawLabel(GuiGraphicsExtractor ctx, String text, int x, int y, int maxW, Identifier font) {
      String trimmed = UiText.trimToWidth(this.textRenderer, text, maxW - 4, font, -1);
      UiText.draw(
         ctx,
         this.textRenderer,
         trimmed,
         font,
         RiptideColors.textPrimary(),
         x,
         UiSizing.alignTextY(y, 15, this.fontHeight(font), this.theme.bodyTextNudge()),
         false
      );
   }

   private int fontHeight(Identifier font) {
      if (UiAssets.FONT_TITLE.equals(font)) {
         return this.theme.fontHeight(UiTone.TITLE);
      } else {
         return UiAssets.FONT_LABEL.equals(font) ? this.theme.fontHeight(UiTone.LABEL) : this.theme.fontHeight(UiTone.BODY);
      }
   }

   private int uiWidth(Identifier font, String text) {
      return UiText.width(this.textRenderer, text == null ? "" : text, font, RiptideColors.textPrimary());
   }

   private int uiWidth(String text) {
      return this.uiWidth(this.theme.fontFor(UiTone.BODY), text);
   }

   private int labelWidth(int totalWidth, String label, Identifier font) {
      return this.labelWidth(totalWidth, label, font, 56);
   }

   private int labelWidth(int totalWidth, String label, Identifier font, int minControlWidth) {
      int measured = this.uiWidth(font, label) + 8;
      int availableMax = Math.max(44, totalWidth - Math.max(28, minControlWidth) - 3);
      return Math.max(44, Math.min(measured, availableMax));
   }

   private int controlX(int x, int labelWidth) {
      return x + labelWidth + 3;
   }

   private int controlWidth(int totalWidth, int labelWidth) {
      return Math.max(28, totalWidth - labelWidth - 3);
   }

   private void fillBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
      UiRenderer.outline(ctx, UiBounds.of(x, y, w, h), color);
   }

   private void drawScrollbar(
      GuiGraphicsExtractor ctx, int trackX, int trackY, int trackH, int totalCount, int visibleCount, int itemH, int scrollOffset, int mouseX, int mouseY
   ) {
      if (totalCount > visibleCount) {
         int viewPixels = Math.max(1, Math.min(trackH, visibleCount * itemH));
         int contentPixels = Math.max(0, totalCount * itemH);
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, 5, trackH, scrollOffset);
         boolean sbDragging = this.activeScrollDragHandler != null;
         CompactScrollbar.draw(ctx, metrics, metrics.contains(mouseX, mouseY), sbDragging);
      }
   }

   private DirectScrollViewport getOrCreateViewport(
      Map<String, DirectScrollViewport> viewports, String key, int x, int y, int width, int height, int rowHeight, int scrollbarWidth
   ) {
      DirectScrollViewport vp = viewports.get(key);
      if (vp == null || vp.getX() != x || vp.getY() != y || vp.getWidth() != width || vp.getHeight() != height) {
         vp = new DirectScrollViewport(x, y, width, height, rowHeight, scrollbarWidth);
         viewports.put(key, vp);
      }

      return vp;
   }

   private RiptideChatField makeField(int w) {
      return new RiptideChatField(MC, this.textRenderer, 0, 0, w, 13, false);
   }

   private static String fmtDouble(double v) {
      long lv = (long)v;
      return v == lv ? String.valueOf(lv) : String.valueOf(v);
   }

   private static boolean matchesListFilter(String filter, String... candidates) {
      String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
      if (normalized.isEmpty()) {
         return true;
      } else {
         for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(normalized)) {
               return true;
            }
         }

         return false;
      }
   }

   private void startCapture(FieldDef field, List<String> lst) {
      if (field.captureMode() == CaptureMode.PACKET_NAME) {
         boolean both = this.targetAction != null && this.targetAction.getType() == MacroActionType.PACKET_GATE;
         if (!both) {
            Consumer<Class<? extends Packet<?>>> add = packetClass -> {
               if (packetClass != null && lst != null) {
                  String namex = RiptidePacketRegistry.getName(packetClass);
                  if (namex == null || namex.isBlank()) {
                     namex = RiptidePacketNamer.getFriendlyName(packetClass);
                  }

                  if (!lst.contains(namex)) {
                     lst.add(namex);
                  }
               }
            };
            String direction = this.currentEnumValue("direction");
            if ("ANY".equals(direction)) {
               this.packetSelectorOverlay.open(add);
            } else if ("S2C".equals(direction)) {
               this.packetSelectorOverlay.openS2C(add);
            } else {
               this.packetSelectorOverlay.openC2S(add);
            }
         } else {
            BiConsumer<Class<? extends Packet<?>>, Boolean> toggle = (packetClass, selected) -> {
               if (packetClass != null && lst != null) {
                  String namex = RiptidePacketRegistry.getName(packetClass);
                  if (namex == null || namex.isBlank()) {
                     namex = RiptidePacketNamer.getFriendlyName(packetClass);
                  }

                  if (selected) {
                     if (!lst.contains(namex)) {
                        lst.add(namex);
                     }
                  } else {
                     lst.remove(namex);
                  }
               }
            };
            List<Class<? extends Packet<?>>> selected = new ArrayList<>();
            if (lst != null) {
               for (String name : lst) {
                  Class<? extends Packet<?>> c = RiptidePacketRegistry.getPacket(name);
                  if (c != null && !selected.contains(c)) {
                     selected.add(c);
                  }
               }
            }

            this.packetSelectorOverlay.openToggleAny(toggle, selected);
         }
      } else if (this.guardWorldCaptureAction()) {
         this.screenBeforeCapture = MC.gui.screen();
         this.enterCaptureMode();
         MC.execute(() -> {
            if (MC.gui.screen() != null) {
               MC.gui.setScreen(null);
            }
         });
         RiptideSharedState state = RiptideSharedState.get();
         state.setCaptureCancelCallback(() -> this.exitCaptureMode(true, false));
         if (field.captureMode() == CaptureMode.BLOCK_ID || field.captureMode() == CaptureMode.BLOCK_CATALOG) {
            state.setBlockCaptureCallback(pos -> {
               if (MC.level != null) {
                  String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock()).toString();
                  if (!lst.contains(id)) {
                     lst.add(id);
                  }
               }

               this.exitCaptureMode(true, false);
            });
         } else if (field.captureMode() == CaptureMode.ENTITY_ID) {
            boolean specificCapture = this.isOpenContainerEntityListKey(field.key());
            boolean replaceOnCapture = this.isReplaceOnCaptureEntityKey(field.key());
            state.setEntityCaptureSpecific(specificCapture);
            state.setEntityCaptureCallback(payload -> {
               String value = payload == null ? "" : payload.strip();
               if (!value.isBlank()) {
                  if (replaceOnCapture) {
                     lst.clear();
                  }

                  if (!lst.contains(value)) {
                     lst.add(value);
                  }
               }

               this.exitCaptureMode(true, false);
            });
         }
      }
   }

   private void startBlockPosCapture(FieldDef field) {
      if (this.guardWorldCaptureAction()) {
         if (this.targetAction != null
            && (this.targetAction.getType() == MacroActionType.INSTA_BREAK || this.targetAction.getType() == MacroActionType.BREAK)
            && "blockPos".equals(field.key())) {
            this.startInstaBreakCapture();
         } else {
            this.screenBeforeCapture = MC.gui.screen();
            this.enterCaptureMode();
            String key = field.key();
            boolean dbl = field.xyzDouble();
            MC.execute(() -> {
               if (MC.gui.screen() != null) {
                  MC.gui.setScreen(null);
               }
            });
            RiptideSharedState state = RiptideSharedState.get();
            state.setPlaceCaptureActive(this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE);
            state.setCaptureCancelCallback(() -> {
               state.setPlaceCaptureActive(false);
               this.exitCaptureMode(true, false);
            });
            if (this.targetAction != null && this.targetAction.getType() == MacroActionType.PLACE) {
               state.setDirectionalBlockCaptureCallback((pos, face) -> {
                  Direction useFace = face == null ? Direction.UP : face;
                  BlockPos targetPos = pos.relative(useFace);
                  RiptideChatField fx = this.textFields.get(key + "_0");
                  RiptideChatField fy = this.textFields.get(key + "_1");
                  RiptideChatField fz = this.textFields.get(key + "_2");
                  if (fx != null) {
                     fx.setText(dbl ? fmtDouble(targetPos.getX()) : String.valueOf(targetPos.getX()));
                  }

                  if (fy != null) {
                     fy.setText(dbl ? fmtDouble(targetPos.getY()) : String.valueOf(targetPos.getY()));
                  }

                  if (fz != null) {
                     fz.setText(dbl ? fmtDouble(targetPos.getZ()) : String.valueOf(targetPos.getZ()));
                  }

                  state.setPlaceCaptureActive(false);
                  this.exitCaptureMode(true, false);
               });
            } else {
               state.setBlockCaptureCallback(pos -> {
                  RiptideChatField fx = this.textFields.get(key + "_0");
                  RiptideChatField fy = this.textFields.get(key + "_1");
                  RiptideChatField fz = this.textFields.get(key + "_2");
                  if (fx != null) {
                     fx.setText(dbl ? fmtDouble(pos.getX()) : String.valueOf(pos.getX()));
                  }

                  if (fy != null) {
                     fy.setText(dbl ? fmtDouble(pos.getY()) : String.valueOf(pos.getY()));
                  }

                  if (fz != null) {
                     fz.setText(dbl ? fmtDouble(pos.getZ()) : String.valueOf(pos.getZ()));
                  }

                  this.exitCaptureMode(true, false);
               });
            }
         }
      }
   }

   private void startInstaBreakCapture() {
      if (this.guardWorldCaptureAction()) {
         this.screenBeforeCapture = MC.gui.screen();
         this.enterCaptureMode();
         MC.execute(() -> {
            if (MC.gui.screen() != null) {
               MC.gui.setScreen(null);
            }
         });
         RiptideSharedState state = RiptideSharedState.get();
         state.setCaptureCancelCallback(() -> this.exitCaptureMode(true, false));
         state.setDirectionalBlockCaptureCallback((pos, direction) -> {
            this.fillBlockPosField("blockPos", pos);
            this.enumIndices.put("direction", direction == null ? 1 : direction.ordinal());
            RiptideClientMessaging.sendPrefixed("InstaBreak target captured");
            this.exitCaptureMode(true, false);
         });
         RiptideClientMessaging.sendPrefixed("InstaBreak: right-click the target block to capture it");
      }
   }

   private static List<String> getAllBlockIds() {
      if (ALL_BLOCK_IDS == null) {
         ALL_BLOCK_IDS = new ArrayList<>();

         for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            String path = id.getPath();
            if (!path.endsWith("_wall_head")
               && !path.endsWith("_wall_sign")
               && !path.endsWith("_wall_banner")
               && !path.endsWith("_wall_torch")
               && !path.endsWith("_wall_skull")) {
               ALL_BLOCK_IDS.add(id.toString());
            }
         }

         Collections.sort(ALL_BLOCK_IDS);
      }

      return ALL_BLOCK_IDS;
   }

   private static String formatTypeName(MacroActionType type) {
      if (type == null) {
         return "Action";
      } else {
         String[] parts = type.name().split("_");
         StringBuilder sb = new StringBuilder();

         for (String part : parts) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               sb.append(part.substring(1).toLowerCase());
            }
         }

         return sb.toString();
      }
   }

   private String getAbstractContainerScreenCaptureTargetLabel() {
      String activeCaptureKey = this.captureSession.itemSlotKey();
      if (!"_item_entries".equals(activeCaptureKey) && !"_drop_entries".equals(activeCaptureKey) && !"_wsc_entries".equals(activeCaptureKey)) {
         FieldDef field = this.findField(activeCaptureKey);
         return field != null ? field.label() : "Slot";
      } else {
         return "Slot + Item";
      }
   }

   private String getCaptureActionLabel() {
      return this.targetAction != null ? this.targetActionTitle() : "Action";
   }

   private String targetActionTitle() {
      if (this.targetAction == null) {
         return "Edit Action";
      } else {
         String title = this.targetAction.getType() != null ? formatTypeName(this.targetAction.getType()) : this.targetAction.getDisplayName();
         return this.multiEditCount > 1 ? title + " x" + this.multiEditCount : title;
      }
   }

   private FieldDef findField(String key) {
      if (this.schema != null && key != null) {
         for (FieldDef field : this.schema.fields()) {
            if (key.equals(field.key())) {
               return field;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private record CaptureListAddResult(boolean added, String message, int accentColor) {
   }

   private static final class HitRegion {
      private final int x;
      private final int y;
      private final int w;
      private final int h;
      private final ActionEditorOverlay.HitRegionAction action;

      private HitRegion(int x, int y, int w, int h, Runnable action) {
         this(x, y, w, h, (mx, my, mouseButton) -> {
            if (mouseButton != 0) {
               return false;
            } else {
               action.run();
               return true;
            }
         });
      }

      private HitRegion(CompactOverlayButton button, Runnable action) {
         this(
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            (mx, my, mouseButton) -> CompactOverlayButton.fireIfHit(button, mx, my, mouseButton)
         );
      }

      private HitRegion(int x, int y, int w, int h, ActionEditorOverlay.HitRegionAction action) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.action = action;
      }

      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }

      boolean fire(double mx, double my, int mouseButton) {
         return this.action != null && this.action.fire(mx, my, mouseButton);
      }
   }

   @FunctionalInterface
   private interface HitRegionAction {
      boolean fire(double var1, double var3, int var5);
   }

   private record PayRow(boolean header, String prefix, String name, List<String> members) {
      static ActionEditorOverlay.PayRow header(String prefix, List<String> members) {
         return new ActionEditorOverlay.PayRow(true, prefix, null, members);
      }

      static ActionEditorOverlay.PayRow player(String prefix, String name) {
         return new ActionEditorOverlay.PayRow(false, prefix, name, null);
      }
   }

   private record PayloadBuildCache(long revision, RiptidePayloadTemplate.Template template, RiptidePayloadTemplate.BuildResult result) {
   }

   private static enum PayloadContentMode {
      UTF8_TEXT,
      BRAND_STRING,
      COMMAND_INT,
      BINARY_REPLAY;
   }

   private static enum PayloadEditKind {
      SCRIPT,
      HEX,
      UTF8,
      LOGICAL,
      PREVIEW_ONLY;
   }

   private record PayloadEditorState(
      String channel,
      byte[] rawBytes,
      boolean commandApiRecognized,
      int commandApiValue,
      ActionEditorOverlay.PayloadContentMode contentMode,
      String contentText
   ) {
      private PayloadEditorState(
         String channel,
         byte[] rawBytes,
         boolean commandApiRecognized,
         int commandApiValue,
         ActionEditorOverlay.PayloadContentMode contentMode,
         String contentText
      ) {
         rawBytes = rawBytes == null ? new byte[0] : (byte[])rawBytes.clone();
         contentMode = contentMode == null ? ActionEditorOverlay.PayloadContentMode.BINARY_REPLAY : contentMode;
         contentText = contentText == null ? "" : contentText;
         this.channel = channel;
         this.rawBytes = rawBytes;
         this.commandApiRecognized = commandApiRecognized;
         this.commandApiValue = commandApiValue;
         this.contentMode = contentMode;
         this.contentText = contentText;
      }

      public byte[] rawBytes() {
         return (byte[])this.rawBytes.clone();
      }
   }

   private record PendingPayloadEdit(ActionEditorOverlay.PayloadEditKind kind, String text) {
   }

   private record ScrollDragRegion(int x, int y, int w, int h, IntConsumer handler) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
