package riptide.gui.vanillaui.module;

import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import riptide.api.module.ActionSetting;
import riptide.api.module.DisplayMode;
import riptide.api.module.Kind;
import riptide.api.module.RangeSetting;
import riptide.api.module.Setting;
import riptide.api.module.ValueRange;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.gui.vanillaui.UiTheme;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.AnimatedToggleButton;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CollapsibleSection;
import riptide.gui.vanillaui.components.ColorPicker;
import riptide.gui.vanillaui.components.CompactKeybindButton;
import riptide.gui.vanillaui.components.CompactWindow;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.gui.vanillaui.components.Dropdown;
import riptide.gui.vanillaui.components.RangeSlider;
import riptide.gui.vanillaui.components.ScrollContainer;
import riptide.gui.vanillaui.components.Scrollbar;
import riptide.gui.vanillaui.components.Slider;
import riptide.gui.vanillaui.components.TextField;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.Tooltip;
import riptide.gui.vanillaui.components.TopBar;
import riptide.modules.Module;
import riptide.modules.ModuleCategory;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.util.AutoFishStopMacroFactory;
import riptide.util.PacketListCodec;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideFavorites;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptideSharedState;
import riptide.util.StringListCodec;
import riptide.util.macro.MacroConditionUtil;

public final class VanillaModuleMenuController {
   private static final int CATEGORY_W = 150;
   private static final int CATEGORY_MIN_W = 100;
   private static final int SETTINGS_W = 360;
   private static final int SLIDER_CONTROL_W = 170;
   private static final int SLIDER_VALUE_W = 44;
   private static final int SLIDER_UNIT_W = 36;
   private static final int SLIDER_TAIL_W = 87;
   private static final int SLIDER_MIN_TRACK_W = 36;
   private static final int UTILITY_W = 170;
   private static final int MIN_VIEW_H = 42;
   private static final int MAX_CATEGORY_VISIBLE_ROWS = 9;
   private static final String MAIN_MENU = "MAIN_MENU";
   private static final String LEGACY_UTILITIES = "UTILITIES";
   private static final String SEARCH_WINDOW = "SEARCH";
   private static final String MODE_SETTING_ID = "mode";
   private static final List<VanillaModuleMenuController.UtilityAction> UTILITY_ACTIONS = List.of(
      VanillaModuleMenuController.UtilityAction.button("macros", "Macros", UiAssets.ICON_MACROS, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("admin", "Admin Tools", UiAssets.ICON_FABRICATOR, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("lan", "LAN Sync", UiAssets.ICON_LANSYNC, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("queue", "Packet Q", UiAssets.ICON_PACKET_Q_EDITOR, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("logger", "Logger", UiAssets.ICON_PACKET_LOGGER, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("packets", "Packets", UiAssets.ICON_FILTER, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("server", "Server", UiAssets.ICON_SERVER_INFO, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("keys", "Settings", UiAssets.ICON_KEYBINDS, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("multi", "Multi", UiAssets.ICON_MULTI, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.category("Packet", UiAssets.ICON_PACKET_CATEGORY),
      VanillaModuleMenuController.UtilityAction.toggle("send", "Send", null, Button.Tone.SUCCESS),
      VanillaModuleMenuController.UtilityAction.toggle("delay", "Delay", null, Button.Tone.PRIMARY),
      VanillaModuleMenuController.UtilityAction.button("flush", "Flush", null, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("clear", "Clear", null, Button.Tone.NORMAL)
   );
   private static final List<VanillaModuleMenuController.UtilityAction> OFFLINE_UTILITY_ACTIONS = List.of(
      VanillaModuleMenuController.UtilityAction.button("macros", "Macros", UiAssets.ICON_MACROS, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("lan", "LAN Sync", UiAssets.ICON_LANSYNC, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("queue", "Packet Q", UiAssets.ICON_PACKET_Q_EDITOR, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("logger", "Logger", UiAssets.ICON_PACKET_LOGGER, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("packets", "Filters", UiAssets.ICON_FILTER, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("server", "Server", UiAssets.ICON_SERVER_INFO, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("keys", "Settings", UiAssets.ICON_KEYBINDS, Button.Tone.NORMAL),
      VanillaModuleMenuController.UtilityAction.button("multi", "Multi", UiAssets.ICON_MULTI, Button.Tone.NORMAL)
   );
   private final VanillaModuleMenuController.Host host;
   private final UiTheme theme = new UiTheme();
   private final UiScissorStack scissors = new UiScissorStack();
   private final List<VanillaModuleMenuController.Hit> hits = new ArrayList<>();
   private final List<VanillaModuleMenuController.Hit> recycledHits = new ArrayList<>();
   private static final Map<String, Integer> windowScroll = new HashMap<>();
   private static final Map<Module, Integer> settingsScroll = new IdentityHashMap<>();
   private static final Map<String, int[]> windowScrollMetrics = new HashMap<>();
   private static final Map<Module, int[]> settingsScrollMetrics = new IdentityHashMap<>();
   private static final Map<String, Boolean> groupCollapsed = new HashMap<>();
   private static final List<String> windowZOrder = new ArrayList<>();
   private final Map<ModuleCategory, VanillaModuleMenuController.CachedOrderedModules> orderedModulesCache = new HashMap<>();
   private final Map<String, VanillaModuleMenuController.CachedSettingRows> settingRowsCache = new HashMap<>();
   private final Map<String, VanillaModuleMenuController.AnimatedValue> enabledAnimations = new HashMap<>();
   private final Map<String, String> enabledSeparatorOwners = new HashMap<>();
   private UiTextRenderer text;
   private Module selectedModule;
   private Module bindingModule;
   private Module bindingOptionModule;
   private Setting<?, ?> bindingOption;
   private long bindConsumedAtNanos;
   private VanillaModuleMenuController.Editing editing;
   private Dropdown dropdown;
   private Module dropdownModule;
   private Setting<?, ?> dropdownOption;
   private ColorPicker colorPicker;
   private VanillaModuleMenuController.MacroPicker macroPicker;
   private String draggingWindowId;
   private String resizingCategoryId;
   private int dragOffsetX;
   private int dragOffsetY;
   private int dragStartX;
   private int dragStartY;
   private boolean draggingMoved;
   private VanillaModuleMenuController.DragScrollbar scrollbarDrag;
   private Module sliderModule;
   private Setting<?, ?> sliderOption;
   private int rangeThumb = -1;
   private int screenWidth;
   private int screenHeight;
   private String tooltip;
   private final Map<ModuleCategory, Integer> categoryWidthCache = new HashMap<>();
   private int categoryWidthStampRevision = Integer.MIN_VALUE;
   private boolean categoryWidthStampHidden;
   private int categoryWidthStampFont;
   private int categoryWidthStampScreenW;
   private int categoryWidthStampScreenH;
   private String searchQuery = "";
   private String cachedSearchQuery = "";
   private int cachedSearchModuleRevision = Integer.MIN_VALUE;
   private List<VanillaModuleMenuController.SearchMatch> cachedSearchMatches = List.of();
   private String settingsSearchQuery = "";
   private UiBounds editingFieldBounds;
   private boolean draggingTextSelection;
   private final Map<String, VanillaModuleMenuController.HoverFade> hoverFades = new HashMap<>();
   private static final Set<String> VALID_WINDOW_IDS = buildValidWindowIds();
   private final Map<String, String> summaryCache = new HashMap<>();

   public VanillaModuleMenuController(VanillaModuleMenuController.Host var1) {
      this.host = Objects.requireNonNull(var1);
   }

   public void init() {
      this.text = UiContexts.textRenderer(this.host.font());
   }

   public boolean blocksGlobalKeybinds() {
      return this.editing != null
         || this.bindingModule != null
         || this.bindingOption != null
         || this.dropdown != null
         || this.colorPicker != null
         || this.macroPicker != null;
   }

   public boolean hasTopLayer() {
      return this.dropdown != null || this.colorPicker != null || this.macroPicker != null || this.editing != null || this.selectedModule != null;
   }

   public boolean hasSelectedModule() {
      return this.selectedModule != null;
   }

   public void openSettingsByModuleId(String var1) {
      Module var2 = ModuleRegistry.get(var1);
      if (var2 != null) {
         this.openSettings(var2);
      }
   }

   public void render(GuiGraphicsExtractor var1, int var2, int var3, float var4, int var5, int var6) {
      if (this.text == null || this.text.font() != this.host.font()) {
         this.text = UiContexts.textRenderer(this.host.font());
      }

      this.screenWidth = Math.max(1, var5);
      this.screenHeight = Math.max(1, var6);
      this.tooltip = "";
      this.recycleHits();
      this.initializeWindowDefaults();
      UiRenderer.rect(var1, UiBounds.of(0, 0, this.screenWidth, this.screenHeight), this.theme.colors().screenScrim);
      boolean var7 = this.dropdown != null || this.colorPicker != null || this.macroPicker != null;
      int var8 = var7 ? -10000 : var2;
      int var9 = var7 ? -10000 : var3;
      UiContext var10 = new UiContext(var1, this.theme, this.text, this.screenWidth, this.screenHeight, var8, var9, var4);
      if (this.selectedModule == null) {
         this.renderCategoryWindows(var10);
      } else {
         if (PackHideState.isActive() && !PackHideState.isHideModule(this.selectedModule)) {
            this.selectedModule = null;
         }

         if (this.selectedModule != null) {
            this.renderSettingsWindow(var10, this.selectedModule);
         }
      }

      UiContext var11 = var7 ? new UiContext(var1, this.theme, this.text, this.screenWidth, this.screenHeight, var2, var3, var4) : var10;
      this.renderDropdown(var11, var2, var3);
      this.renderColorPicker(var11, var2, var3);
      this.renderMacroPicker(var11, var2, var3);
      if (this.tooltip != null && !this.tooltip.isBlank() && !var7) {
         Tooltip.render(var10, this.tooltip, var2, var3);
      }
   }

   private void renderCategoryWindows(UiContext var1) {
      String var2 = this.topWindowIdAt(var1.mouseX(), var1.mouseY());

      for (String var4 : this.windowRenderOrder()) {
         UiContext var5 = this.hoverContextFor(var1, var2, var4);
         if ("MAIN_MENU".equals(var4)) {
            this.renderUtilityWindow(var5);
         } else if ("SEARCH".equals(var4)) {
            this.renderSearchWindow(var5);
         } else {
            ModuleCategory var6 = this.categoryById(var4);
            if (var6 != null) {
               this.renderCategoryWindow(var5, var6);
            }
         }
      }
   }

   private UiContext hoverContextFor(UiContext var1, String var2, String var3) {
      return var2 != null && !var2.equals(var3)
         ? new UiContext(var1.graphics(), var1.theme(), var1.text(), var1.screenWidth(), var1.screenHeight(), -10000, -10000, var1.delta())
         : var1;
   }

   private String topWindowIdAt(int var1, int var2) {
      List var3 = this.windowRenderOrder();

      for (int var4 = var3.size() - 1; var4 >= 0; var4--) {
         String var5 = (String)var3.get(var4);
         UiBounds var6;
         if ("MAIN_MENU".equals(var5)) {
            int var7 = this.utilityContentHeight();
            var6 = this.utilityWindowBounds(var7);
         } else if ("SEARCH".equals(var5)) {
            var6 = this.searchWindowBounds(this.searchContentHeight());
         } else {
            ModuleCategory var9 = this.categoryById(var5);
            if (var9 == null) {
               continue;
            }

            List var8 = this.orderedModules(var9);
            if (var8.isEmpty()) {
               continue;
            }

            var6 = this.categoryWindowBounds(var9, var8.size() * 15);
         }

         if (var6.contains(var1, var2)) {
            return var5;
         }
      }

      return null;
   }

   private UiBounds categoryWindowBounds(ModuleCategory var1, int var2) {
      RiptideConfig.ModuleCategoryLayout var3 = this.layout(var1.name());
      int var4 = this.categoryWidth(var1);
      int var5 = this.maxCategoryBodyHeight(var1);
      int var6 = var3.collapsed ? 0 : Math.min(var2, var5);
      return UiBounds.of(var3.x, var3.y, var4, 15 + var6).clampInside(this.screenWidth - 4, this.screenHeight - 4);
   }

   private int maxCategoryBodyHeight(ModuleCategory var1) {
      int var2 = this.screenHeight - 8 - 15;
      int var3 = this.layout(var1.name()).visibleRows;
      int var4 = (var3 > 0 ? Math.max(1, var3) : 9) * 15;
      return Math.max(42, Math.min(var2, var4));
   }

   private int categoryWidth(ModuleCategory var1) {
      int var2 = ModuleRegistry.revision();
      boolean var3 = PackHideState.isActive();
      int var4 = this.text == null ? 0 : System.identityHashCode(this.text);
      if (var2 != this.categoryWidthStampRevision
         || var3 != this.categoryWidthStampHidden
         || var4 != this.categoryWidthStampFont
         || this.screenWidth != this.categoryWidthStampScreenW
         || this.screenHeight != this.categoryWidthStampScreenH) {
         this.categoryWidthCache.clear();
         this.categoryWidthStampRevision = var2;
         this.categoryWidthStampHidden = var3;
         this.categoryWidthStampFont = var4;
         this.categoryWidthStampScreenW = this.screenWidth;
         this.categoryWidthStampScreenH = this.screenHeight;
      }

      Integer var5 = this.categoryWidthCache.get(var1);
      if (var5 != null) {
         return var5;
      } else {
         int var6 = this.computeCategoryWidth(var1);
         this.categoryWidthCache.put(var1, var6);
         return var6;
      }
   }

   private int computeCategoryWidth(ModuleCategory var1) {
      List var2 = this.orderedModules(var1);
      int var3 = 0;

      for (Module var5 : var2) {
         var3 = Math.max(var3, this.text.width(var5.name()));
      }

      int var10 = var2.size() * 15;
      int var11 = this.maxCategoryBodyHeight(var1);
      int var6 = var10 > var11 ? 6 : 0;
      int var7 = 5 + var3 + 9 + 34 + 3 + 2 + var6;
      int var8 = this.text.width(var1.label()) + 26;
      int var9 = Math.max(72, Math.max(var7, var8));
      return Math.min(var9, Math.min(150, Math.max(1, this.screenWidth - 8)));
   }

   private int windowWidth(String var1) {
      if ("MAIN_MENU".equals(var1)) {
         return Math.max(112, Math.min(170, this.screenWidth - 8));
      } else {
         ModuleCategory var2 = this.categoryById(var1);
         return var2 != null ? this.categoryWidth(var2) : Math.max(100, Math.min(150, this.screenWidth - 8));
      }
   }

   private UiBounds utilityWindowBounds(int var1) {
      RiptideConfig.ModuleCategoryLayout var2 = this.layout("MAIN_MENU");
      int var3 = Math.max(112, Math.min(170, this.screenWidth - 8));
      int var4 = Math.max(42, this.screenHeight - 8 - 15);
      int var5 = var2.collapsed ? 0 : Math.min(var1, var4);
      return UiBounds.of(var2.x, var2.y, var3, 15 + var5 + (var2.collapsed ? 0 : 1)).clampInside(this.screenWidth - 4, this.screenHeight - 4);
   }

   private void renderCategoryWindow(UiContext var1, ModuleCategory var2) {
      List var3 = this.orderedModules(var2);
      if (!var3.isEmpty()) {
         RiptideConfig.ModuleCategoryLayout var4 = this.layout(var2.name());
         int var5 = var3.size() * 15;
         UiBounds var6 = this.categoryWindowBounds(var2, var5);
         UiBounds var7 = UiBounds.of(var6.x(), var6.y(), var6.width(), 15);
         String var8 = var2.label();
         CompactWindow.renderFrame(var1, var6, var8, var4.collapsed, false, var7.contains(var1.mouseX(), var1.mouseY()));
         this.addHit(VanillaModuleMenuController.HitType.WINDOW_HEADER, var7, null, null, var2, var2.name(), -1);
         if (!var4.collapsed) {
            int var9 = Math.max(0, var6.height() - 15);
            UiBounds var10 = UiBounds.of(var6.x() + 1, var6.y() + 15 - 1, var6.width() - 2, var9);
            int var11 = this.clampWindowScroll(var2.name(), var5, var10.height());
            if (var5 > var10.height()) {
               var10 = UiBounds.of(var10.x(), var10.y(), var10.width() - 6, var10.height());
            }

            this.addHit(VanillaModuleMenuController.HitType.SCROLL_AREA, var10, null, null, var2, var2.name(), -1);
            this.scissors.push(var1.graphics(), var10);

            try {
               int var12 = ScrollContainer.firstVisibleRow(var11, 15, var3.size());
               int var13 = ScrollContainer.lastVisibleRow(var11, var10.height(), 15, var3.size());

               for (int var14 = var12; var14 <= var13; var14++) {
                  Module var15 = (Module)var3.get(var14);
                  int var16 = var10.y() - var11 + var14 * 15;
                  this.renderModuleRow(var1, var15, UiBounds.of(var10.x(), var16, var10.width(), 15), var2, var14);
               }
            } finally {
               this.scissors.pop(var1.graphics());
            }

            if (var5 > var10.height()) {
               UiBounds var20 = UiBounds.of(var6.right() - 6 - 1, var10.y(), 6, var10.height());
               Scrollbar.Metrics var22 = Scrollbar.metrics(var20, var5, var10.height(), var11);
               Scrollbar.render(
                  var1, var22, var22.track().contains(var1.mouseX(), var1.mouseY()), this.scrollbarDrag != null && var2.name().equals(this.scrollbarDrag.id)
               );
               this.addHit(VanillaModuleMenuController.HitType.SCROLLBAR, this.scrollbarHitBounds(var20), null, null, var2, var2.name(), -1);
            }

            boolean var21 = this.resizingCategoryId != null && this.resizingCategoryId.equals(var2.name())
               || var1.mouseY() >= var6.bottom() - 6 && var1.mouseY() < var6.bottom() && var1.mouseX() >= var6.x() && var1.mouseX() < var6.right();
            if (var21) {
               UiRenderer.rect(var1.graphics(), UiBounds.of(var6.x() + 1, var6.bottom() - 2, var6.width() - 2, 2), var1.theme().colors().accent);
            }

            this.addHit(
               VanillaModuleMenuController.HitType.CATEGORY_RESIZE,
               UiBounds.of(var6.x(), var6.bottom() - 6, var6.width(), 6),
               null,
               null,
               var2,
               var2.name(),
               -1
            );
         }
      }
   }

   private boolean isSearchEditing() {
      return this.editing != null && this.editing.module == null && this.editing.option == null;
   }

   private boolean isSettingsSearchEditing() {
      return this.editing != null && this.editing.module != null && this.editing.option == null;
   }

   private String currentSearchQuery() {
      return this.isSearchEditing() ? this.editing.text : this.searchQuery;
   }

   private String currentSettingsSearch() {
      return this.isSettingsSearchEditing() ? this.editing.text : this.settingsSearchQuery;
   }

   private int cursorAtX(String var1, int var2, UiBounds var3) {
      int var4 = Math.max(0, var2 - var3.x() - 4);
      int var5 = 0;
      int var6 = var1.length();

      while (var5 < var6) {
         int var7 = var5 + (var6 - var5 >>> 1);
         if (this.text.width(var1.substring(0, var7)) < var4) {
            var5 = var7 + 1;
         } else {
            var6 = var7;
         }
      }

      int var8 = var5;
      if (var5 > 0 && var5 < var1.length() && Character.isHighSurrogate(var1.charAt(var5 - 1)) && Character.isLowSurrogate(var1.charAt(var5))) {
         var8 = var5 - 1;
      }

      return var8;
   }

   private void beginTextDrag(UiBounds var1) {
      this.editingFieldBounds = var1;
      this.draggingTextSelection = true;
   }

   private void beginSettingsSearchEditing(int var1, UiBounds var2) {
      this.editing = new VanillaModuleMenuController.Editing(
         this.selectedModule, null, this.settingsSearchQuery, this.cursorAtX(this.settingsSearchQuery, var1, var2)
      );
      this.beginTextDrag(var2);
   }

   private void beginSearchEditing(int var1, UiBounds var2) {
      this.editing = new VanillaModuleMenuController.Editing(null, null, this.searchQuery, this.cursorAtX(this.searchQuery, var1, var2));
      this.beginTextDrag(var2);
   }

   private void focusSearchWindow() {
      this.finishEditing(true);
      this.selectedModule = null;
      RiptideConfig.ModuleCategoryLayout var1 = this.layout("SEARCH");
      var1.collapsed = false;
      this.host.saveConfig();
      this.bringWindowToFront("SEARCH");
      this.editing = new VanillaModuleMenuController.Editing(null, null, this.searchQuery, this.searchQuery.length());
      if (!this.searchQuery.isEmpty()) {
         this.editing.selectAll();
      }
   }

   private List<VanillaModuleMenuController.SearchMatch> searchMatches() {
      String var1 = this.currentSearchQuery().trim().toLowerCase(Locale.ROOT);
      if (var1.isEmpty()) {
         return List.of();
      } else {
         String var2 = fuzzyNormalize(var1);
         if (var2.isEmpty()) {
            return List.of();
         } else {
            int var3 = ModuleRegistry.revision();
            if (var3 == this.cachedSearchModuleRevision && var1.equals(this.cachedSearchQuery)) {
               return this.cachedSearchMatches;
            } else {
               ArrayList var4 = new ArrayList();

               for (ModuleCategory var6 : ModuleCategory.values()) {
                  List var7 = this.orderedModules(var6);

                  for (int var8 = 0; var8 < var7.size(); var8++) {
                     Module var9 = (Module)var7.get(var8);
                     String var10 = fuzzyNormalize(var9.name());
                     if (var10.contains(var2) || fuzzyNormalize(var9.id()).contains(var2) || isSubsequence(var2, var10)) {
                        var4.add(new VanillaModuleMenuController.SearchMatch(var9, var6, var8));
                     }
                  }
               }

               this.cachedSearchQuery = var1;
               this.cachedSearchModuleRevision = var3;
               this.cachedSearchMatches = List.copyOf(var4);
               return this.cachedSearchMatches;
            }
         }
      }
   }

   private static String fuzzyNormalize(String var0) {
      StringBuilder var1 = new StringBuilder(var0.length());

      for (int var2 = 0; var2 < var0.length(); var2++) {
         char var3 = var0.charAt(var2);
         if (var3 != ' ' && var3 != '-' && var3 != '_' && var3 != '.') {
            var1.append(Character.toLowerCase(var3));
         }
      }

      return var1.toString();
   }

   private static boolean isSubsequence(String var0, String var1) {
      if (var0.isEmpty()) {
         return false;
      } else {
         int var2 = 0;

         for (int var3 = 0; var3 < var1.length() && var2 < var0.length(); var3++) {
            if (var1.charAt(var3) == var0.charAt(var2)) {
               var2++;
            }
         }

         return var2 == var0.length();
      }
   }

   private int searchFieldAreaHeight() {
      return 21;
   }

   private int searchContentHeight() {
      int var1 = this.currentSearchQuery().trim().isEmpty() ? 0 : Math.max(15, this.searchMatches().size() * 15);
      return this.searchFieldAreaHeight() + var1;
   }

   private UiBounds searchWindowBounds(int var1) {
      RiptideConfig.ModuleCategoryLayout var2 = this.layout("SEARCH");
      int var3 = this.windowWidth("SEARCH");
      int var4 = var2.collapsed ? 0 : Math.min(var1, this.maxSearchBodyHeight(var2));
      return UiBounds.of(var2.x, var2.y, var3, 15 + var4).clampInside(this.screenWidth - 4, this.screenHeight - 4);
   }

   private int maxSearchBodyHeight(RiptideConfig.ModuleCategoryLayout var1) {
      int var2 = this.screenHeight - 8 - 15;
      int var3 = this.screenHeight - 4 - Math.max(0, var1.y) - 15;
      return Math.max(42, Math.min(var2, var3));
   }

   private void renderSearchWindow(UiContext var1) {
      RiptideConfig.ModuleCategoryLayout var2 = this.layout("SEARCH");
      List var3 = this.searchMatches();
      boolean var4 = !this.currentSearchQuery().trim().isEmpty();
      UiBounds var5 = this.searchWindowBounds(this.searchContentHeight());
      UiBounds var6 = UiBounds.of(var5.x(), var5.y(), var5.width(), 15);
      CompactWindow.renderFrame(var1, var5, "Search", var2.collapsed, false, var6.contains(var1.mouseX(), var1.mouseY()));
      this.addHit(VanillaModuleMenuController.HitType.WINDOW_HEADER, var6, null, null, null, "SEARCH", -1);
      if (!var2.collapsed) {
         UiBounds var7 = UiBounds.of(var5.x() + 4, var5.y() + 15 + 3, Math.max(1, var5.width() - 8), 15);
         if (this.isSearchEditing()) {
            TextField.render(
               var1, var7, this.editing.text, "Search modules...", true, this.editing.cursor, this.editing.selectionStart(), this.editing.selectionEnd()
            );
         } else {
            TextField.render(var1, var7, this.searchQuery, "Search modules...", false, this.searchQuery.length());
         }

         this.addHit(VanillaModuleMenuController.HitType.SEARCH_FIELD, var7, null, null, null, "SEARCH", -1);
         if (var4) {
            int var8 = var5.y() + 15 + this.searchFieldAreaHeight() - 1;
            int var9 = Math.max(0, var5.bottom() - var8);
            UiBounds var10 = UiBounds.of(var5.x() + 1, var8, var5.width() - 2, var9);
            if (var3.isEmpty()) {
               var1.text().drawFitted(var1.graphics(), "No matches", var10.x() + 4, var10.y() + 3, Math.max(1, var10.width() - 8), this.theme.colors().muted);
            } else {
               int var11 = var3.size() * 15;
               int var12 = this.clampWindowScroll("SEARCH", var11, var10.height());
               if (var11 > var10.height()) {
                  var10 = UiBounds.of(var10.x(), var10.y(), var10.width() - 6, var10.height());
               }

               this.addHit(VanillaModuleMenuController.HitType.SCROLL_AREA, var10, null, null, null, "SEARCH", -1);
               this.scissors.push(var1.graphics(), var10);

               try {
                  int var13 = ScrollContainer.firstVisibleRow(var12, 15, var3.size());
                  int var14 = ScrollContainer.lastVisibleRow(var12, var10.height(), 15, var3.size());

                  for (int var15 = var13; var15 <= var14; var15++) {
                     VanillaModuleMenuController.SearchMatch var16 = (VanillaModuleMenuController.SearchMatch)var3.get(var15);
                     int var17 = var10.y() - var12 + var15 * 15;
                     this.renderSearchModuleRow(var1, var16.module(), UiBounds.of(var10.x(), var17, var10.width(), 15));
                  }
               } finally {
                  this.scissors.pop(var1.graphics());
               }

               if (var11 > var10.height()) {
                  UiBounds var21 = UiBounds.of(var5.right() - 6 - 1, var10.y(), 6, var10.height());
                  Scrollbar.Metrics var22 = Scrollbar.metrics(var21, var11, var10.height(), var12);
                  Scrollbar.render(
                     var1, var22, var22.track().contains(var1.mouseX(), var1.mouseY()), this.scrollbarDrag != null && "SEARCH".equals(this.scrollbarDrag.id)
                  );
                  this.addHit(VanillaModuleMenuController.HitType.SCROLLBAR, this.scrollbarHitBounds(var21), null, null, null, "SEARCH", -1);
               }
            }
         }
      }
   }

   private void renderSearchModuleRow(UiContext var1, Module var2, UiBounds var3) {
      boolean var4 = var3.contains(var1.mouseX(), var1.mouseY());
      float var5 = this.animatedEnabledProgress(var2);
      float var6 = this.hoverFade("mod:search:" + var2.id(), var4);
      UiRenderer.rect(var1.graphics(), var3, lerpColor(this.theme.colors().row, this.theme.colors().rowHover, var6));
      if (var5 > 0.001F) {
         int var7 = Math.max(1, Math.round(var3.width() * var5));
         UiRenderer.rect(
            var1.graphics(),
            UiBounds.of(var3.x(), var3.y(), var7, var3.height()),
            var4 ? alphaColor(this.theme.colors().accent, 0.34F) : alphaColor(this.theme.colors().accent, 0.22F)
         );
         int var8 = alphaColor(this.theme.colors().accent, var5);
         int var9 = Math.max(1, Math.round(var3.width() * var5));
         UiRenderer.horizontalEdge(var1.graphics(), var3.x(), var3.y(), var9, var8);
         UiRenderer.horizontalEdge(var1.graphics(), var3.x(), var3.bottom() - 1, var9, var8);
         UiRenderer.verticalEdge(var1.graphics(), var3.x(), var3.y(), var3.height(), var8);
         UiRenderer.verticalEdge(var1.graphics(), var3.right() - 1, var3.y(), var3.height(), var8);
         UiRenderer.rect(var1.graphics(), UiBounds.of(var3.x(), var3.y(), 2, var3.height()), var8);
      }

      String var10 = this.bindingModule == var2 ? "Press key" : var2.name();
      UiBounds var11 = CompactKeybindButton.atRowEnd(var3, 3, 2);
      int var12 = Math.max(1, var11.x() - var3.x() - 9);
      this.drawHighlighted(
         var1,
         var10,
         var3.x() + 5,
         var3.y() + 3,
         var12,
         var2.isEnabled() ? this.theme.colors().text : this.theme.colors().muted,
         this.currentSearchQuery().trim().toLowerCase(Locale.ROOT)
      );
      this.addHit(VanillaModuleMenuController.HitType.MODULE, var3, var2, null, null, "SEARCH", -1);
      CompactKeybindButton.render(var1, var11, var2.keybind(), this.bindingModule == var2, var11.contains(var1.mouseX(), var1.mouseY()));
      this.addHit(VanillaModuleMenuController.HitType.MODULE_BIND, var11, var2, null, null, "SEARCH", -1);
      if (var4 && var2.description() != null && !var2.description().isBlank()) {
         this.tooltip = var2.description();
      }
   }

   private void renderUtilityWindow(UiContext var1) {
      if (!PackHideState.isActive()) {
         RiptideConfig.ModuleCategoryLayout var2 = this.layout("MAIN_MENU");
         int var3 = this.utilityContentHeight();
         UiBounds var4 = this.utilityWindowBounds(var3);
         UiBounds var5 = UiBounds.of(var4.x(), var4.y(), var4.width(), 15);
         CompactWindow.renderFrame(var1, var4, "Main Menu", var2.collapsed, false, var5.contains(var1.mouseX(), var1.mouseY()));
         this.addHit(VanillaModuleMenuController.HitType.WINDOW_HEADER, var5, null, null, null, "MAIN_MENU", -1);
         if (!var2.collapsed) {
            int var6 = Math.max(0, var4.height() - 15 - 1);
            UiBounds var7 = UiBounds.of(var4.x() + 1, var4.y() + 15, var4.width() - 2, var6);
            int var8 = this.clampWindowScroll("MAIN_MENU", var3, var7.height());
            if (var3 > var7.height()) {
               var7 = UiBounds.of(var7.x(), var7.y(), var7.width() - 6, var7.height());
            }

            this.addHit(VanillaModuleMenuController.HitType.SCROLL_AREA, var7, null, null, null, "MAIN_MENU", -1);
            int var9 = Math.max(44, var7.width() / 2);
            int var10 = var7.y() - var8;
            int var11 = 0;
            Object var12 = null;
            Object var13 = null;
            float var14 = 0.0F;
            this.scissors.push(var1.graphics(), var7);

            try {
               List var15 = this.utilityActions();

               for (int var16 = 0; var16 < var15.size(); var16++) {
                  VanillaModuleMenuController.UtilityAction var17 = (VanillaModuleMenuController.UtilityAction)var15.get(var16);
                  if (var17.category()) {
                     if (var11 != 0) {
                        var10 += 15;
                        var11 = 0;
                        var12 = null;
                        var13 = null;
                        var14 = 0.0F;
                     }

                     UiBounds var18 = UiBounds.of(var7.x(), var10, var7.width(), 15);
                     if (var18.bottom() >= var7.y() && var18.y() <= var7.bottom()) {
                        this.renderUtilityCategory(var1, var18, var17);
                     }

                     var10 += 15;
                  } else {
                     boolean var40 = this.utilityFullWidth(var17);
                     if (var40 && var11 != 0) {
                        var10 += 15;
                        var11 = 0;
                        var12 = null;
                        var13 = null;
                        var14 = 0.0F;
                     }

                     UiBounds var19 = var40
                        ? UiBounds.of(var7.x(), var10, var7.width(), 15)
                        : UiBounds.of(var7.x() + var11 * var9, var10, var11 == 0 ? var9 : var7.width() - var9, 15);
                     boolean var20 = this.utilityActive(var17.id);
                     float var21 = var17.toggle() ? this.animatedUtilityProgress(var17.id, var20, var19.width()) : 0.0F;
                     if (var19.bottom() >= var7.y() && var19.y() <= var7.bottom()) {
                        boolean var22 = var19.contains(var1.mouseX(), var1.mouseY());
                        if (var17.toggle()) {
                           ConnectedButton.renderToggle(var1, var19, this.utilityLabel(var17, var20), var17.icon, var22, var21, ConnectedButton.FULL);
                        } else {
                           ConnectedButton.renderAction(var1, var19, var17.label, var17.icon, var17.tone, var22, ConnectedButton.FULL);
                        }

                        this.addHit(VanillaModuleMenuController.HitType.UTILITY, var19, null, null, null, var17.id, var16);
                     }

                     if (var40) {
                        var10 += 15;
                        var11 = 0;
                        var12 = null;
                        var13 = null;
                        var14 = 0.0F;
                     } else {
                        if (var11 == 0) {
                        }

                        if (++var11 >= 2) {
                           var11 = 0;
                           var10 += 15;
                           var12 = null;
                           var13 = null;
                           var14 = 0.0F;
                        }
                     }
                  }
               }
            } finally {
               this.scissors.pop(var1.graphics());
            }

            if (var3 > var7.height()) {
               UiBounds var38 = UiBounds.of(var4.right() - 6 - 1, var7.y(), 6, var7.height());
               Scrollbar.Metrics var39 = Scrollbar.metrics(var38, var3, var7.height(), var8);
               Scrollbar.render(
                  var1, var39, var39.track().contains(var1.mouseX(), var1.mouseY()), this.scrollbarDrag != null && "MAIN_MENU".equals(this.scrollbarDrag.id)
               );
               this.addHit(VanillaModuleMenuController.HitType.SCROLLBAR, this.scrollbarHitBounds(var38), null, null, null, "MAIN_MENU", -1);
            }
         }
      }
   }

   private void renderUtilityCategory(UiContext var1, UiBounds var2, VanillaModuleMenuController.UtilityAction var3) {
      ConnectedButton.renderCategory(var1, var2, var3.label, var3.icon);
   }

   private void drawUtilitySeam(
      UiContext var1, VanillaModuleMenuController.UtilityAction var2, float var3, VanillaModuleMenuController.UtilityAction var4, float var5, UiBounds var6
   ) {
      int var7 = var2.toggle() ? ConnectedButton.toggleBorderColor(var1, var3) : ConnectedButton.toneBorderColor(var1, var2.tone);
      int var8 = var4.toggle() ? ConnectedButton.toggleBorderColor(var1, var5) : ConnectedButton.toneBorderColor(var1, var4.tone);
      float var9 = ConnectedButton.seamWeight(var2.tone, var2.toggle(), var3);
      float var10 = ConnectedButton.seamWeight(var4.tone, var4.toggle(), var5);
      ConnectedButton.drawVerticalSeam(var1, var6.x(), var6.y(), var6.height(), ConnectedButton.chooseSeamColor(var7, var9, var8, var10));
   }

   private int utilityContentHeight() {
      int var1 = 0;
      int var2 = 0;

      for (VanillaModuleMenuController.UtilityAction var4 : this.utilityActions()) {
         if (var4.category()) {
            if (var2 != 0) {
               var1++;
               var2 = 0;
            }

            var1++;
         } else if (this.utilityFullWidth(var4)) {
            if (var2 != 0) {
               var1++;
               var2 = 0;
            }

            var1++;
         } else if (++var2 >= 2) {
            var1++;
            var2 = 0;
         }
      }

      if (var2 != 0) {
         var1++;
      }

      return Math.max(15, var1 * 15);
   }

   private boolean utilityFullWidth(VanillaModuleMenuController.UtilityAction var1) {
      return this.host.offlineSetup() && var1 != null && "keys".equals(var1.id());
   }

   private List<VanillaModuleMenuController.UtilityAction> utilityActions() {
      return this.host.offlineSetup() ? OFFLINE_UTILITY_ACTIONS : UTILITY_ACTIONS;
   }

   private boolean utilityActive(String var1) {
      RiptideSharedState var2 = RiptideSharedState.get();

      return switch (var1) {
         case "send" -> var2.shouldSendGuiPackets();
         case "delay" -> var2.shouldDelayGuiPackets();
         default -> false;
      };
   }

   private String utilityLabel(VanillaModuleMenuController.UtilityAction var1, boolean var2) {
      return var1.label;
   }

   private float animatedUtilityProgress(String var1, boolean var2, int var3) {
      String var4 = "utility:" + var1;
      float var5 = var2 ? 1.0F : 0.0F;
      VanillaModuleMenuController.AnimatedValue var6 = this.enabledAnimations.get(var4);
      long var7 = System.nanoTime();
      if (var6 == null) {
         var6 = new VanillaModuleMenuController.AnimatedValue(var5, var7);
         this.enabledAnimations.put(var4, var6);
         return var5;
      } else {
         float var9 = Math.max(0.0F, Math.min(0.05F, (float)(var7 - var6.lastNanos) / 1.0E9F));
         var6.lastNanos = var7;
         float var10 = var9 / ((float)AnimatedToggleButton.durationNanos(var3) / 1.0E9F);
         if (var6.value < var5) {
            var6.value = Math.min(var5, var6.value + var10);
         } else if (var6.value > var5) {
            var6.value = Math.max(var5, var6.value - var10);
         }

         return var6.value;
      }
   }

   private void renderModuleRow$riptideOrig(UiContext var1, Module var2, UiBounds var3, ModuleCategory var4, int var5) {
      boolean var6 = var3.contains(var1.mouseX(), var1.mouseY());
      float var7 = this.animatedEnabledProgress(var2);
      float var8 = this.hoverFade("mod:" + (var4 == null ? "search" : var4.name()) + ":" + var2.id(), var6);
      UiRenderer.rect(var1.graphics(), var3, lerpColor(this.theme.colors().row, this.theme.colors().rowHover, var8));
      if (var7 > 0.001F) {
         int var9 = Math.max(1, Math.round(var3.width() * var7));
         UiRenderer.rect(
            var1.graphics(),
            UiBounds.of(var3.x(), var3.y(), var9, var3.height()),
            var6 ? alphaColor(this.theme.colors().accent, 0.34F) : alphaColor(this.theme.colors().accent, 0.22F)
         );
         this.drawAnimatedEnabledOutline(var1, var3, var4, var5, var7);
      }

      String var12 = this.bindingModule == var2 ? "Press key" : var2.name();
      UiBounds var10 = CompactKeybindButton.atRowEnd(var3, 3, 2);
      int var11 = Math.max(1, var10.x() - var3.x() - 9);
      var1.text()
         .drawFitted(var1.graphics(), var12, var3.x() + 5, var3.y() + 3, var11, var2.isEnabled() ? this.theme.colors().text : this.theme.colors().muted);
      this.addHit(VanillaModuleMenuController.HitType.MODULE, var3, var2, null, var4, "", var5);
      CompactKeybindButton.render(var1, var10, var2.keybind(), this.bindingModule == var2, var10.contains(var1.mouseX(), var1.mouseY()));
      this.addHit(VanillaModuleMenuController.HitType.MODULE_BIND, var10, var2, null, var4, "", var5);
      if (var6 && var2.description() != null && !var2.description().isBlank()) {
         this.tooltip = var2.description();
      }
   }

   private void drawAnimatedEnabledOutline(UiContext var1, UiBounds var2, ModuleCategory var3, int var4, float var5) {
      int var6 = alphaColor(this.theme.colors().accent, var5);
      List var7 = this.orderedModules(var3);
      Module var8 = (Module)var7.get(var4);
      int var9 = Math.max(1, Math.round(var2.width() * var5));
      if (this.shouldDrawTopEnabledEdge(var3, var7, var8, var4, var5)) {
         UiRenderer.horizontalEdge(var1.graphics(), var2.x(), var2.y(), var9, var6);
      }

      if (this.shouldDrawBottomEnabledEdge(var3, var7, var8, var4, var5)) {
         UiRenderer.horizontalEdge(var1.graphics(), var2.x(), var2.bottom() - 1, var9, var6);
      }

      UiRenderer.verticalEdge(var1.graphics(), var2.x(), var2.y(), var2.height(), var6);
      UiRenderer.verticalEdge(var1.graphics(), var2.right() - 1, var2.y(), var2.height(), var6);
      UiRenderer.rect(var1.graphics(), UiBounds.of(var2.x(), var2.y(), 2, var2.height()), var6);
   }

   private boolean shouldDrawTopEnabledEdge(ModuleCategory var1, List<Module> var2, Module var3, int var4, float var5) {
      if (var4 == 0) {
         return false;
      } else if (var4 < 0) {
         return true;
      } else {
         Module var6 = (Module)var2.get(var4 - 1);
         float var7 = this.enabledProgressSnapshot(var6);
         if (var7 <= 0.001F) {
            return true;
         } else {
            String var8 = this.enabledSeparatorOwner(var1, var6, var3, var7, var5);
            return var3.id().equals(var8);
         }
      }
   }

   private boolean shouldDrawBottomEnabledEdge(ModuleCategory var1, List<Module> var2, Module var3, int var4, float var5) {
      if (var4 + 1 >= var2.size()) {
         return true;
      } else {
         Module var6 = (Module)var2.get(var4 + 1);
         float var7 = this.enabledProgressSnapshot(var6);
         if (var7 <= 0.001F) {
            return true;
         } else {
            String var8 = this.enabledSeparatorOwner(var1, var3, var6, var5, var7);
            return var3.id().equals(var8);
         }
      }
   }

   private String enabledSeparatorOwner(ModuleCategory var1, Module var2, Module var3, float var4, float var5) {
      String var6 = var1.name() + "|" + var2.id() + "|" + var3.id();
      String var7 = this.enabledSeparatorOwners.get(var6);
      if (var4 <= 0.001F && var5 <= 0.001F) {
         this.enabledSeparatorOwners.remove(var6);
         return "";
      } else {
         if (var7 != null) {
            if (var7.equals(var2.id()) && var4 > 0.001F) {
               return var7;
            }

            if (var7.equals(var3.id()) && var5 > 0.001F) {
               return var7;
            }
         }

         String var8 = var4 >= var5 ? var2.id() : var3.id();
         this.enabledSeparatorOwners.put(var6, var8);
         return var8;
      }
   }

   private void renderSettingsWindow(UiContext var1, Module var2) {
      int var3 = Math.max(1, this.screenWidth - 8);
      int var4 = Math.max(1, this.screenHeight - 12);
      int var5 = Math.min(360, var3);
      byte var6 = 15;
      byte var7 = 4;
      int var8 = Math.max(1, var5 - 10);
      boolean var9 = var8 < 88 + var7 + 50 + var7 + 52 + var7 + 52;
      boolean var10 = var8 < 92;
      int var11 = var10 ? 4 : (var9 ? 2 : 1);
      int var12 = 20 + var11 * var6 + (var11 - 1) * var7 + 5 + var6 + 10;
      VanillaModuleMenuController.CachedSettingRows var13 = this.cachedRows(var2, var8);
      int var14 = Math.min(var13.contentHeight, Math.max(42, var4 - var12));
      int var15 = Math.min(var4, var12 + Math.max(42, var14));
      int var16 = Math.max(4, (this.screenWidth - var5) / 2);
      int var17 = Math.max(4, (this.screenHeight - var15) / 2);
      UiBounds var18 = UiBounds.of(var16, var17, var5, var15).clampInside(this.screenWidth - 4, this.screenHeight - 4);
      UiBounds var19 = UiBounds.of(var18.x(), var18.y(), var18.width(), 15);
      CompactWindow.renderFrame(var1, var18, var2.name(), false, true, var19.contains(var1.mouseX(), var1.mouseY()));
      this.addHit(VanillaModuleMenuController.HitType.SETTINGS_HEADER, var19, var2, null, null, "", -1);
      this.addHit(VanillaModuleMenuController.HitType.BACK, TopBar.closeButton(var19), var2, null, null, "", -1);
      int var20 = var18.x() + 5;
      int var21 = var18.y() + 15 + 5;
      int var22 = var20 + var8;
      int var23 = 52 + var7 + 52;
      int var24 = var10 ? var8 : Math.min(88, Math.max(1, var9 ? (var8 - var7) / 2 : 88));
      UiBounds var25 = UiBounds.of(var20, var21, var24, var6);
      Toggle.renderLabeled(var1, var25, "Enabled", var2.isEnabled(), var25.contains(var1.mouseX(), var1.mouseY()), "module:" + var2.id());
      this.addHit(VanillaModuleMenuController.HitType.TOGGLE_MODULE, var25, var2, null, null, "", -1);
      int var26 = var10 ? var20 : var25.right() + var7;
      int var27 = var10 ? var25.bottom() + var7 : var25.y();
      int var28 = !var10 && !var9 ? var22 - var23 - var7 : var22;
      UiBounds var29 = UiBounds.of(var26, var27, Math.max(1, Math.min(88, var28 - var26)), var6);
      Button.render(
         var1,
         var29,
         this.bindingModule == var2 ? "Press key" : "Bind " + RiptideBindUtil.getBindName(var2.keybind()),
         Button.Tone.NORMAL,
         var29.contains(var1.mouseX(), var1.mouseY()),
         this.bindingModule == var2
      );
      this.addHit(VanillaModuleMenuController.HitType.BIND_MODULE, var29, var2, null, null, "", -1);
      int var30 = var10 ? var29.bottom() + var7 : (var9 ? var25.bottom() + var7 : var21);
      int var31 = var10 ? var8 : (var9 ? Math.max(1, (var8 - var7) / 2) : 52);
      UiBounds var32 = UiBounds.of(var22 - var31, var30, var31, var6);
      UiBounds var33 = var10
         ? UiBounds.of(var20, var32.bottom() + var7, var8, var6)
         : UiBounds.of(Math.max(var20, var32.x() - var7 - var31), var30, var31, var6);
      Button.render(var1, var33, "Reset", Button.Tone.DANGER, var33.contains(var1.mouseX(), var1.mouseY()), false);
      this.addHit(VanillaModuleMenuController.HitType.RESET_MODULE_SETTINGS, var33, var2, null, null, "", -1);
      Button.render(var1, var32, "Macro", Button.Tone.NORMAL, var32.contains(var1.mouseX(), var1.mouseY()), false);
      this.addHit(VanillaModuleMenuController.HitType.MACRO, var32, var2, null, null, "", -1);
      int var34 = Math.max(Math.max(var25.bottom(), var29.bottom()), Math.max(var32.bottom(), var33.bottom()));
      UiBounds var35 = UiBounds.of(var18.x() + 5, var34 + 5, Math.max(1, var18.width() - 10), var6);
      boolean var36 = this.isSettingsSearchEditing();
      String var37 = var36 ? this.editing.text : this.settingsSearchQuery;
      TextField.render(
         var1,
         var35,
         var37,
         "Search settings...",
         var36,
         var36 ? this.editing.cursor : var37.length(),
         var36 ? this.editing.selectionStart() : -1,
         var36 ? this.editing.selectionEnd() : -1
      );
      this.addHit(VanillaModuleMenuController.HitType.SETTINGS_SEARCH_FIELD, var35, var2, null, null, "", -1);
      UiBounds var38 = UiBounds.of(var18.x() + 5, var35.bottom() + 5, Math.max(1, var18.width() - 10), Math.max(1, var18.bottom() - var35.bottom() - 10));
      VanillaModuleMenuController.CachedSettingRows var39 = this.cachedRows(var2, var38.width());
      int var40 = this.clampSettingsScroll(var2, var39.contentHeight, var38.height());
      UiBounds var41 = var39.contentHeight > var38.height() ? UiBounds.of(var38.x(), var38.y(), var38.width() - 6 - 2, var38.height()) : var38;
      this.addHit(VanillaModuleMenuController.HitType.SCROLL_AREA, var38, var2, null, null, "settings", -1);
      this.scissors.push(var1.graphics(), var41);

      try {
         for (VanillaModuleMenuController.SettingRow var43 : var39.rows) {
            int var44 = var41.y() - var40 + var43.y;
            if (var44 + var43.height > var41.y() && var44 < var41.bottom()) {
               if (var43.group != null) {
                  this.renderGroupHeader(var1, var2, var43.group, UiBounds.of(var41.x(), var44, var41.width(), var43.height));
               } else {
                  this.renderSettingRow(var1, var2, var43.option, UiBounds.of(var41.x(), var44, var41.width(), var43.height));
               }
            }
         }
      } finally {
         this.scissors.pop(var1.graphics());
      }

      if (var39.contentHeight > var38.height()) {
         UiBounds var48 = UiBounds.of(var38.right() - 6, var38.y(), 6, var38.height());
         Scrollbar.Metrics var49 = Scrollbar.metrics(var48, var39.contentHeight, var38.height(), var40);
         Scrollbar.render(
            var1, var49, var49.track().contains(var1.mouseX(), var1.mouseY()), this.scrollbarDrag != null && "settings".equals(this.scrollbarDrag.id)
         );
         this.addHit(VanillaModuleMenuController.HitType.SCROLLBAR, this.scrollbarHitBounds(var48), var2, null, null, "settings", -1);
      }
   }

   private void renderGroupHeader(UiContext var1, Module var2, String var3, UiBounds var4) {
      boolean var5 = this.isGroupCollapsed(var2, var3);
      CollapsibleSection.renderHeader(var1, var4, var3, var5, var4.contains(var1.mouseX(), var1.mouseY()));
      this.addHit(VanillaModuleMenuController.HitType.GROUP, var4, var2, null, null, var3, -1);
   }

   private void renderSettingRow(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      boolean var5 = var4.contains(var1.mouseX(), var1.mouseY());
      float var6 = this.hoverFade("set:" + var2.id() + ":" + var3.id(), var5);
      UiRenderer.rect(var1.graphics(), var4, lerpColor(this.theme.colors().row, this.theme.colors().rowHover, var6));
      boolean var7 = var3.displayMode() == DisplayMode.MACRO_PICKER || var3.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER;
      int var8 = var7
         ? Math.min(190, Math.max(132, var4.width() / 2))
         : (wideControlRow(var3) ? Math.min(170, Math.max(96, var4.width() * 3 / 5)) : Math.min(132, Math.max(96, var4.width() / 3)));
      int var9 = Math.max(40, var4.width() - var8 - 8);
      int var10 = var4.y() + 4;
      String var11 = var2 == this.selectedModule ? this.currentSettingsSearch().trim().toLowerCase(Locale.ROOT) : "";
      this.drawHighlighted(var1, var3.label(), var4.x() + 5, var10, var9, this.theme.colors().text, var11);
      boolean var12 = !var3.description().isBlank() && var4.height() > 24;
      if (var12) {
         this.drawHighlighted(var1, var3.description(), var4.x() + 5, var4.y() + 15, var9, this.theme.colors().muted, var11);
      }

      if (var5 && !var12 && var3.description() != null && !var3.description().isBlank()) {
         this.tooltip = var3.description();
      }

      UiBounds var13 = UiBounds.of(var4.right() - var8 - 4, var4.y() + Math.max(3, (var4.height() - 15) / 2), var8, 15);
      if (var3.displayMode() == DisplayMode.FILE_PICKER) {
         this.renderSummaryActionRow(var1, var2, var3, var13, "Pick", Button.Tone.PRIMARY, VanillaModuleMenuController.HitType.FILE_PICKER);
      } else if (var3.displayMode() == DisplayMode.READONLY_SUMMARY) {
         this.renderSummaryOnly(var1, var2, var3, var13);
      } else if (var3.displayMode() == DisplayMode.MACRO_PICKER || var3.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER) {
         this.renderSummaryActionRow(var1, var2, var3, var13, "Select", Button.Tone.NORMAL, VanillaModuleMenuController.HitType.MACRO_PICKER);
      } else if (var3.displayMode() == DisplayMode.RANGE_SLIDER) {
         this.renderRangeOption(var1, var2, var3, var13);
      } else {
         switch (var3.kind()) {
            case BOOLEAN:
               UiBounds var14 = UiBounds.of(var13.right() - 34, var13.y(), 34, var13.height());
               Toggle.render(
                  var1, var14, Boolean.parseBoolean(var2.value(var3.id())), var5 || var14.contains(var1.mouseX(), var1.mouseY()), var2.id() + "/" + var3.id()
               );
               this.addHit(VanillaModuleMenuController.HitType.OPTION, var4, var2, var3, null, "", -1);
               break;
            case INTEGER:
            case DOUBLE:
               this.renderNumericOption(var1, var2, var3, var13);
               break;
            case ENUM:
               Dropdown.renderControl(
                  var1,
                  var13,
                  var2.displayValue(var3),
                  var13.contains(var1.mouseX(), var1.mouseY()),
                  this.dropdownModule == var2 && this.dropdownOption == var3
               );
               this.addHit(VanillaModuleMenuController.HitType.DROPDOWN, var13, var2, var3, null, "", -1);
               break;
            case STRING:
               boolean var21 = this.editing != null && this.editing.module == var2 && this.editing.option == var3;
               TextField.render(
                  var1,
                  var13,
                  var21 ? this.editing.text : var2.value(var3.id()),
                  "<empty>",
                  var21,
                  var21 ? this.editing.cursor : var2.value(var3.id()).length(),
                  var21 ? this.editing.selectionStart() : -1,
                  var21 ? this.editing.selectionEnd() : -1
               );
               this.addHit(VanillaModuleMenuController.HitType.TEXT_FIELD, var13, var2, var3, null, "", -1);
               break;
            case COLOR:
               this.renderColorOption(var1, var2, var3, var13);
               break;
            case STRING_LIST:
            case ITEM_LIST:
            case BLOCK_LIST:
            case ENTITY_TYPE_LIST:
            case SOUND_EVENT_LIST:
            case STORAGE_LIST:
               UiBounds var20 = UiBounds.of(var13.right() - 38, var13.y(), 38, var13.height());
               UiBounds var24 = UiBounds.of(var13.x(), var13.y(), Math.max(1, var13.width() - 42), var13.height());
               this.drawSummaryValue(var1, var2, var3, var24);
               Button.render(var1, var20, "Edit", Button.Tone.NORMAL, var20.contains(var1.mouseX(), var1.mouseY()), false);
               this.addHit(VanillaModuleMenuController.HitType.LIST_EDITOR, var20, var2, var3, null, "", -1);
               break;
            case PACKET_LIST:
               UiBounds var19 = UiBounds.of(var13.right() - 44, var13.y(), 44, var13.height());
               UiBounds var23 = UiBounds.of(var13.x(), var13.y(), Math.max(1, var13.width() - 48), var13.height());
               this.drawSummaryValue(var1, var2, var3, var23);
               Button.render(var1, var19, "Pick", Button.Tone.PRIMARY, var19.contains(var1.mouseX(), var1.mouseY()), false);
               this.addHit(VanillaModuleMenuController.HitType.PACKET_EDITOR, var19, var2, var3, null, "", -1);
               break;
            case KEYBIND:
               UiBounds var18 = UiBounds.of(var13.x(), var13.y(), Math.max(46, var13.width() - 42), var13.height());
               UiBounds var22 = UiBounds.of(var18.right() + 4, var13.y(), 38, var13.height());
               boolean var17 = this.bindingOptionModule == var2 && this.bindingOption == var3;
               Button.render(
                  var1,
                  var18,
                  var17 ? "Press" : RiptideBindUtil.getBindName(parseInt(var2.value(var3.id()), -1)),
                  Button.Tone.NORMAL,
                  var18.contains(var1.mouseX(), var1.mouseY()),
                  var17
               );
               Button.render(var1, var22, "Clear", Button.Tone.NORMAL, var22.contains(var1.mouseX(), var1.mouseY()), false);
               this.addHit(VanillaModuleMenuController.HitType.BIND_OPTION, var18, var2, var3, null, "", -1);
               this.addHit(VanillaModuleMenuController.HitType.RESET_OPTION_BIND, var22, var2, var3, null, "", -1);
               break;
            case ACTION:
               String var15 = var3 instanceof ActionSetting var16 ? var16.buttonLabel() : "Run";
               Button.render(var1, var13, var15, Button.Tone.PRIMARY, var13.contains(var1.mouseX(), var1.mouseY()), false);
               this.addHit(VanillaModuleMenuController.HitType.OPTION, var13, var2, var3, null, "", -1);
         }
      }
   }

   private void renderNumericOption(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      if (var3.displayMode() == DisplayMode.NUMERIC_TEXT_FIELD) {
         boolean var5 = this.editing != null && this.editing.module == var2 && this.editing.option == var3;
         TextField.render(
            var1,
            var4,
            var5 ? this.editing.text : var2.value(var3.id()),
            "",
            var5,
            var5 ? this.editing.cursor : var2.value(var3.id()).length(),
            var5 ? this.editing.selectionStart() : -1,
            var5 ? this.editing.selectionEnd() : -1
         );
         this.addHit(VanillaModuleMenuController.HitType.TEXT_FIELD, var4, var2, var3, null, "", -1);
      } else {
         String var16 = var3.unit() == null ? "" : var3.unit();
         int var6 = var16.isEmpty() ? 0 : var1.text().width(var16);
         int var7 = var16.isEmpty() ? 0 : var6 + 3;
         int var8 = 48 + var7;
         UiBounds var9 = UiBounds.of(var4.x(), var4.y(), Math.max(36, var4.width() - var8), var4.height());
         UiBounds var10 = UiBounds.of(var4.right() - var7 - 44, var4.y(), 44, var4.height());
         double var11 = var3.kind() == Kind.INTEGER
            ? parseInt(var2.value(var3.id()), parseInt(var3.defaultValue(), 0))
            : parseDouble(var2.value(var3.id()), parseDouble(var3.defaultValue(), 0.0));
         double var13 = (var11 - var3.sliderMin()) / Math.max(1.0E-4, var3.sliderMax() - var3.sliderMin());
         Slider.render(var1, var9, var13, var9.contains(var1.mouseX(), var1.mouseY()));
         boolean var15 = this.editing != null && this.editing.module == var2 && this.editing.option == var3;
         TextField.render(
            var1,
            var10,
            var15 ? this.editing.text : var2.value(var3.id()),
            "",
            var15,
            var15 ? this.editing.cursor : var2.value(var3.id()).length(),
            var15 ? this.editing.selectionStart() : -1,
            var15 ? this.editing.selectionEnd() : -1
         );
         if (!var16.isEmpty()) {
            var1.text().drawTrimmed(var1.graphics(), var16, var10.right() + 3, var1.text().centeredY(var10), var6, var1.theme().colors().text);
         }

         this.addHit(VanillaModuleMenuController.HitType.SLIDER, var9, var2, var3, null, "", -1);
         this.addHit(VanillaModuleMenuController.HitType.TEXT_FIELD, var10, var2, var3, null, "", -1);
      }
   }

   private void renderRangeOption(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      String var5 = var3.unit() == null ? "" : var3.unit();
      ValueRange var6 = this.rangeValue(var2, var3);
      String var7 = var6.format(var3.step()) + (var5.isEmpty() ? "" : " " + var5);
      UiBounds var8 = UiBounds.of(var4.x(), var4.y(), Math.max(36, var4.width() - 87), var4.height());
      UiBounds var9 = UiBounds.of(var8.right() + 4, var4.y(), 83, var4.height());
      double var10 = var3.sliderMin();
      double var12 = var3.sliderMax();
      boolean var14 = this.sliderModule == var2 && this.sliderOption == var3;
      RangeSlider.render(
         var1,
         var8,
         RangeSlider.ratio(var6.min(), var10, var12),
         RangeSlider.ratio(var6.max(), var10, var12),
         var8.contains(var1.mouseX(), var1.mouseY()),
         var14 ? this.rangeThumb : -1
      );
      var1.text().drawTrimmed(var1.graphics(), var7, var9.x(), var1.text().centeredY(var9), var9.width(), var1.theme().colors().text);
      this.addHit(VanillaModuleMenuController.HitType.RANGE_SLIDER, var8, var2, var3, null, "", -1);
   }

   private static boolean wideControlRow(Setting<?, ?> var0) {
      return switch (var0.displayMode()) {
         case RANGE_SLIDER -> true;
         case DEFAULT -> var0.kind() == Kind.INTEGER || var0.kind() == Kind.DOUBLE || var0.kind() == Kind.STRING;
         default -> false;
      };
   }

   private void renderColorOption(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      byte var5 = 38;
      UiBounds var6 = UiBounds.of(var4.right() - var5, var4.y(), var5, var4.height());
      UiBounds var7 = UiBounds.of(var4.x(), var4.y(), Math.max(1, var4.width() - var5 - 4), var4.height());
      int var8 = parseColor(var2.value(var3.id()), parseColor(var3.defaultValue(), -1));
      UiRenderer.frame(var1.graphics(), var7, var1.theme().colors().field, var1.theme().colors().borderSoft);
      UiBounds var9 = UiBounds.of(var7.x() + 3, var7.y() + 3, 14, Math.max(1, var7.height() - 6));
      UiRenderer.frame(var1.graphics(), var9, var8 | 0xFF000000, var1.theme().colors().borderSoft);
      String var10 = String.format(Locale.ROOT, "#%06X", var8 & 16777215);
      var1.text()
         .drawFitted(
            var1.graphics(), var10, var9.right() + 5, var1.text().centeredY(var7), Math.max(1, var7.right() - var9.right() - 8), var1.theme().colors().text
         );
      Button.render(var1, var6, "Pick", Button.Tone.NORMAL, var6.contains(var1.mouseX(), var1.mouseY()), this.colorPicker != null);
      this.addHit(VanillaModuleMenuController.HitType.COLOR_PICKER, var6, var2, var3, null, "", -1);
      this.addHit(VanillaModuleMenuController.HitType.COLOR_PICKER, var7, var2, var3, null, "", -1);
   }

   private void renderDropdown(UiContext var1, int var2, int var3) {
      if (this.dropdown != null) {
         var1.graphics().nextStratum();
         this.dropdown.render(var1);
      }
   }

   private void renderColorPicker(UiContext var1, int var2, int var3) {
      if (this.colorPicker != null) {
         var1.graphics().nextStratum();
         this.colorPicker.render(var1);
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }
      }
   }

   private void renderMacroPicker(UiContext var1, int var2, int var3) {
      if (this.macroPicker != null) {
         var1.graphics().nextStratum();
         this.macroPicker.render(var1, var2, var3);
      }
   }

   private void clearDropdown() {
      if (this.dropdown != null) {
         this.dropdown.close();
      }

      this.dropdown = null;
      this.dropdownModule = null;
      this.dropdownOption = null;
   }

   private void openColorPicker(Module var1, Setting<?, ?> var2, UiBounds var3) {
      if (var1 != null && var2 != null) {
         this.clearDropdown();
         this.clearMacroPicker();
         this.finishEditing(true);
         int var4 = parseColor(var2.defaultValue(), -1);
         int var5 = parseColor(var1.value(var2.id()), var4);
         this.colorPicker = new ColorPicker(var3, var5, var4, this.screenWidth, this.screenHeight, var3x -> {
            this.setOptionValue(var1, var2, String.format(Locale.ROOT, "%08X", var3x));
            this.invalidateSettings(var1);
         });
      }
   }

   private void clearColorPicker() {
      if (this.colorPicker != null) {
         this.colorPicker.closeCancel();
      }

      this.colorPicker = null;
   }

   private void openMacroPicker(Module var1, Setting<?, ?> var2, UiBounds var3) {
      if (var1 != null && var2 != null) {
         this.clearDropdown();
         this.clearColorPicker();
         this.finishEditing(true);
         this.macroPicker = new VanillaModuleMenuController.MacroPicker(var1, var2, var3);
      }
   }

   private void clearMacroPicker() {
      this.macroPicker = null;
   }

   private void handleMacroPickerKey(int var1) {
      if (this.macroPicker != null) {
         switch (var1) {
            case 256:
               this.clearMacroPicker();
               break;
            case 259:
               if (!this.macroPicker.query.isEmpty()) {
                  this.macroPicker.setQuery(this.macroPicker.query.substring(0, this.macroPicker.query.length() - 1));
               }
         }
      }
   }

   private boolean mouseClicked$riptideOrig(int var1, int var2, int var3) {
      if (this.handleBindMouse(var3)) {
         return true;
      } else if (this.dropdown != null) {
         this.dropdown.mouseClicked(var1, var2, var3);
         if (!this.dropdown.isOpen()) {
            this.clearDropdown();
         }

         return true;
      } else if (this.colorPicker != null) {
         this.colorPicker.mouseClicked(var1, var2, var3);
         if (!this.colorPicker.isOpen()) {
            this.colorPicker = null;
         }

         return true;
      } else if (this.macroPicker != null) {
         if (!this.macroPicker.mouseClicked(var1, var2, var3)) {
            this.clearMacroPicker();
         }

         return true;
      } else {
         this.finishEditing(true);

         for (int var4 = this.hits.size() - 1; var4 >= 0; var4--) {
            VanillaModuleMenuController.Hit var5 = this.hits.get(var4);
            if (var5.bounds.contains(var1, var2)) {
               switch (var5.type) {
                  case WINDOW_HEADER:
                     this.bringWindowToFront(var5.id);
                     this.draggingWindowId = var5.id;
                     this.dragOffsetX = var1 - var5.bounds.x();
                     this.dragOffsetY = var2 - var5.bounds.y();
                     this.dragStartX = var1;
                     this.dragStartY = var2;
                     this.draggingMoved = false;
                     return true;
                  case SETTINGS_HEADER:
                  case SCROLL_AREA:
                  default:
                     break;
                  case MODULE:
                     this.bringWindowToFront(var5.category != null ? var5.category.name() : "SEARCH");
                     if (var3 == 1) {
                        this.openSettings(var5.module);
                     } else if (var3 == 2) {
                        this.bindingModule = var5.module;
                     } else if (var3 == 0) {
                        if (this.hasVisibleSettings(var5.module) && (var5.module.opensSettingsOnClick() || !var5.module.hasActivationToggle())) {
                           this.openSettings(var5.module);
                        } else {
                           this.toggleModule(var5.module);
                        }
                     }

                     return true;
                  case MODULE_BIND:
                     if (var3 == 0) {
                        this.bindingModule = var5.module;
                     }

                     return true;
                  case UTILITY:
                     if (var3 == 0) {
                        this.host.runUtility(var5.id);
                     }

                     return true;
                  case BACK:
                     this.selectedModule = null;
                     return true;
                  case TOGGLE_MODULE:
                     if (var5.module.hasActivationToggle()) {
                        this.toggleModule(var5.module);
                     }

                     return true;
                  case BIND_MODULE:
                     this.bindingModule = var5.module;
                     return true;
                  case MACRO:
                     Module var9 = var5.module;
                     this.selectedModule = null;
                     this.host.addToggleMacro(var9);
                     return true;
                  case RESET_MODULE_SETTINGS:
                     if (this.host.offlineSetup()) {
                        var5.module.resetConfiguredSettings();
                     } else {
                        var5.module.resetSettings();
                     }

                     this.invalidateSettings(var5.module);
                     settingsScroll.put(var5.module, 0);
                     if (this.bindingOptionModule == var5.module) {
                        this.bindingOptionModule = null;
                        this.bindingOption = null;
                     }

                     RiptideClientMessaging.sendPrefixed(var5.module.name() + " settings reset to defaults.");
                     return true;
                  case GROUP:
                     this.setGroupCollapsed(var5.module, var5.id, !this.isGroupCollapsed(var5.module, var5.id));
                     this.invalidateSettings(var5.module);
                     return true;
                  case OPTION:
                     this.activateOption(var5.module, var5.option, var3);
                     return true;
                  case FILE_PICKER:
                     if (var5.option != null && var5.option.action() != null) {
                        var5.option.action().run();
                        this.invalidateSettings(var5.module);
                     }

                     return true;
                  case SLIDER:
                     this.sliderModule = var5.module;
                     this.sliderOption = var5.option;
                     this.rangeThumb = -1;
                     this.updateSlider(var1);
                     return true;
                  case RANGE_SLIDER:
                     this.sliderModule = var5.module;
                     this.sliderOption = var5.option;
                     ValueRange var6 = this.rangeValue(var5.module, var5.option);
                     this.rangeThumb = RangeSlider.nearestThumb(
                        var1,
                        var5.bounds,
                        RangeSlider.ratio(var6.min(), var5.option.sliderMin(), var5.option.sliderMax()),
                        RangeSlider.ratio(var6.max(), var5.option.sliderMin(), var5.option.sliderMax())
                     );
                     this.updateRangeSlider(var1);
                     return true;
                  case TEXT_FIELD:
                     this.beginEditing(var5.module, var5.option, var1, var5.bounds);
                     return true;
                  case DROPDOWN:
                     Module var10 = var5.module;
                     Setting var11 = var5.option;
                     if (var10 != null && var11 != null) {
                        this.scrollbarDrag = null;
                        this.sliderModule = null;
                        this.sliderOption = null;
                        this.rangeThumb = -1;
                        this.draggingWindowId = null;
                        this.draggingMoved = false;
                        this.clearColorPicker();
                        this.clearMacroPicker();
                        this.dropdownModule = var10;
                        this.dropdownOption = var11;
                        this.dropdown = new Dropdown(var5.bounds, var11.choices(), var10.value(var11.id()), var3x -> {
                           if (this.dropdownModule == var10 && this.dropdownOption == var11) {
                              this.setOptionValue(var10, var11, var3x);
                              this.invalidateSettings(var10);
                           }
                        });
                        this.dropdown.open();
                        return true;
                     }

                     this.clearDropdown();
                     return true;
                  case COLOR_PICKER:
                     this.openColorPicker(var5.module, var5.option, var5.bounds);
                     return true;
                  case LIST_EDITOR:
                     this.openListEditor(var5.module, var5.option);
                     return true;
                  case PACKET_EDITOR:
                     this.host.openPacketSelector(var5.module, var5.option);
                     return true;
                  case MACRO_PICKER:
                     this.openMacroPicker(var5.module, var5.option, var5.bounds);
                     return true;
                  case SEARCH_FIELD:
                     this.bringWindowToFront("SEARCH");
                     this.beginSearchEditing(var1, var5.bounds);
                     return true;
                  case SETTINGS_SEARCH_FIELD:
                     this.beginSettingsSearchEditing(var1, var5.bounds);
                     return true;
                  case BIND_OPTION:
                     this.bindingOptionModule = var5.module;
                     this.bindingOption = var5.option;
                     return true;
                  case RESET_OPTION_BIND:
                     this.setOptionValue(var5.module, var5.option, "-1");
                     this.invalidateSettings(var5.module);
                     return true;
                  case SCROLLBAR:
                     Scrollbar.Metrics var7 = this.metricsForHit(var5);
                     if (var7 != null && var7.maxScroll() > 0) {
                        int var8 = var7.overThumb(var1, var2) ? var2 - var7.thumb().y() : var7.thumb().height() / 2;
                        this.scrollbarDrag = new VanillaModuleMenuController.DragScrollbar(var5.id, var8);
                        this.setScroll(var5, Scrollbar.scrollFromMouse(var7, var2, var8));
                     }

                     return true;
                  case CATEGORY_RESIZE:
                     this.bringWindowToFront(var5.id);
                     this.resizingCategoryId = var5.id;
                     return true;
               }
            }
         }

         return true;
      }
   }

   public boolean mouseReleased(int var1, int var2, int var3) {
      Module var4 = this.sliderModule;
      this.draggingTextSelection = false;
      this.scrollbarDrag = null;
      this.sliderModule = null;
      this.sliderOption = null;
      this.rangeThumb = -1;
      if (var4 != null) {
         var4.persistConfiguredState();
      }

      if (this.colorPicker != null) {
         this.colorPicker.mouseReleased(var1, var2, var3);
         if (!this.colorPicker.isOpen()) {
            this.colorPicker = null;
         }

         return true;
      } else if (this.dropdown != null) {
         this.dropdown.mouseReleased(var1, var2, var3);
         if (!this.dropdown.isOpen()) {
            this.clearDropdown();
         }

         return true;
      } else if (this.macroPicker != null) {
         this.macroPicker.mouseReleased(var1, var2, var3);
         return true;
      } else {
         if (var3 == 0 && this.resizingCategoryId != null) {
            this.resizingCategoryId = null;
            this.host.saveConfig();
         }

         if (var3 == 0 && this.draggingWindowId != null) {
            if (!this.draggingMoved) {
               RiptideConfig.ModuleCategoryLayout var5 = this.layout(this.draggingWindowId);
               var5.collapsed = !var5.collapsed;
            }

            this.draggingWindowId = null;
            this.draggingMoved = false;
            this.host.saveConfig();
         }

         return true;
      }
   }

   public boolean mouseDragged(int var1, int var2, int var3, double var4, double var6) {
      if (this.colorPicker != null) {
         this.colorPicker.mouseDragged(var1, var2, var3, var4, var6);
         if (!this.colorPicker.isOpen()) {
            this.colorPicker = null;
         }

         return true;
      } else if (this.dropdown != null) {
         this.dropdown.mouseDragged(var1, var2, var3, var4, var6);
         return true;
      } else if (this.macroPicker != null) {
         this.macroPicker.mouseDragged(var1, var2, var3, var4, var6);
         return true;
      } else if (this.draggingTextSelection && var3 == 0 && this.editing != null && this.editingFieldBounds != null) {
         this.moveEditingCursor(this.cursorAtX(this.editing.text, var1, this.editingFieldBounds), true);
         return true;
      } else if (this.scrollbarDrag != null) {
         VanillaModuleMenuController.Hit var13 = this.findScrollbarHit(this.scrollbarDrag.id);
         if (var13 != null) {
            Scrollbar.Metrics var15 = this.metricsForHit(var13);
            if (var15 != null) {
               this.setScroll(var13, Scrollbar.scrollFromMouse(var15, var2, this.scrollbarDrag.grabOffset));
            }
         }

         return true;
      } else if (this.resizingCategoryId != null) {
         RiptideConfig.ModuleCategoryLayout var12 = this.layout(this.resizingCategoryId);
         int var14 = Math.max(1, 15);
         int var10 = var12.y + 15;
         int var11 = Math.max(1, (this.screenHeight - 8 - 15) / var14);
         var12.visibleRows = clamp((var2 - var10 + var14 / 2) / var14, 1, var11);
         return true;
      } else if (this.draggingWindowId == null) {
         if (this.sliderModule != null && this.sliderOption != null) {
            this.updateSlider(var1);
            return true;
         } else {
            return true;
         }
      } else {
         RiptideConfig.ModuleCategoryLayout var8 = this.layout(this.draggingWindowId);
         if (!this.draggingMoved && (Math.abs(var1 - this.dragStartX) >= 3 || Math.abs(var2 - this.dragStartY) >= 3)) {
            this.draggingMoved = true;
         }

         int var9 = this.windowWidth(this.draggingWindowId);
         var8.x = clamp(var1 - this.dragOffsetX, 0, Math.max(0, this.screenWidth - Math.min(var9, this.screenWidth)));
         var8.y = clamp(var2 - this.dragOffsetY, 0, Math.max(0, this.screenHeight - 15));
         return true;
      }
   }

   public boolean mouseScrolled(int var1, int var2, double var3) {
      if (this.colorPicker != null) {
         this.colorPicker.mouseScrolled(var1, var2, var3);
         return true;
      } else if (this.dropdown != null) {
         this.dropdown.mouseScrolled(var1, var2, var3);
         return true;
      } else if (this.macroPicker != null) {
         this.macroPicker.mouseScrolled(var1, var2, var3);
         return true;
      } else {
         VanillaModuleMenuController.Hit var5 = this.scrollTargetAt(var1, var2);
         if (var5 != null) {
            String var6 = this.selectedModule != null ? "settings" : (var5.category != null ? var5.category.name() : var5.id);
            if (var6 != null && !var6.isBlank()) {
               this.scrollBy(var6, this.selectedModule, var3 < 0.0 ? 30 : -30);
            }

            return true;
         } else {
            return true;
         }
      }
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (this.bindingModule != null) {
         this.bindingModule.setKeybind(CompactKeybindButton.keyOrClear(var1));
         this.bindingModule = null;
         this.bindConsumedAtNanos = System.nanoTime();
         return true;
      } else if (this.bindingOptionModule != null && this.bindingOption != null) {
         this.setOptionValue(this.bindingOptionModule, this.bindingOption, Integer.toString(CompactKeybindButton.keyOrClear(var1)));
         this.invalidateSettings(this.bindingOptionModule);
         this.bindingOptionModule = null;
         this.bindingOption = null;
         this.bindConsumedAtNanos = System.nanoTime();
         return true;
      } else if (var1 == 70 && (var3 & 10) != 0) {
         this.focusSearchWindow();
         return true;
      } else if (this.editing != null) {
         this.handleEditingKey(var1, var3);
         return true;
      } else if (this.colorPicker != null) {
         this.colorPicker.keyPressed(var1, var2, var3);
         if (!this.colorPicker.isOpen()) {
            this.colorPicker = null;
         }

         return true;
      } else if (this.macroPicker != null) {
         this.handleMacroPickerKey(var1);
         return true;
      } else if (var1 == 256) {
         if (this.dropdown != null) {
            this.clearDropdown();
         } else if (this.macroPicker != null) {
            this.clearMacroPicker();
         } else if (this.selectedModule != null) {
            this.selectedModule = null;
         } else {
            this.host.closeMenu();
         }

         return true;
      } else if (this.shouldReserveModifierForMacroEditor(var1)) {
         return true;
      } else if (var1 != RiptideConfig.getGlobal().keybindModuleMenu && var1 != 69) {
         return false;
      } else if (this.dropdown == null && var1 >= 65 && var1 <= 90) {
         return false;
      } else {
         if (this.selectedModule != null) {
            this.selectedModule = null;
         } else {
            this.host.closeMenu();
         }

         return true;
      }
   }

   private boolean shouldReserveModifierForMacroEditor(int var1) {
      int var2 = RiptideConfig.getGlobal().keybindModuleMenu;
      if (var1 != var2) {
         return false;
      } else {
         return var1 != 341 && var1 != 345 && var1 != 340 && var1 != 344 ? false : RiptideSharedState.get().isMacroEditorVisible();
      }
   }

   public boolean charTyped(char var1) {
      if (this.bindConsumedAtNanos != 0L && System.nanoTime() - this.bindConsumedAtNanos < 250000000L) {
         this.bindConsumedAtNanos = 0L;
         return true;
      } else if (this.colorPicker != null) {
         this.colorPicker.charTyped(var1);
         if (!this.colorPicker.isOpen()) {
            this.colorPicker = null;
         }

         return true;
      } else if (this.macroPicker != null) {
         if (var1 >= ' ' && var1 != 127) {
            this.macroPicker.setQuery(this.macroPicker.query + var1);
         }

         return true;
      } else if (this.editing != null) {
         if (var1 >= ' ' && var1 != 127) {
            this.replaceEditingSelection(Character.toString(var1));
         }

         return true;
      } else if (this.dropdown == null && !RiptideOverlayManager.get().hasVisibleOverlay() && (var1 >= 'a' && var1 <= 'z' || var1 >= 'A' && var1 <= 'Z')) {
         if (this.selectedModule != null) {
            this.editing = new VanillaModuleMenuController.Editing(this.selectedModule, null, this.settingsSearchQuery, this.settingsSearchQuery.length());
         } else {
            this.focusSearchWindow();
            this.editing = new VanillaModuleMenuController.Editing(null, null, this.searchQuery, this.searchQuery.length());
         }

         this.replaceEditingSelection(Character.toString(var1));
         return true;
      } else {
         return false;
      }
   }

   private boolean handleBindMouse(int var1) {
      if (!RiptideBindUtil.isAllowedMouseButton(var1)) {
         return false;
      } else if (this.bindingModule != null) {
         this.bindingModule.setKeybind(RiptideBindUtil.encodeMouseButton(var1));
         this.bindingModule = null;
         return true;
      } else if (this.bindingOptionModule != null && this.bindingOption != null) {
         this.setOptionValue(this.bindingOptionModule, this.bindingOption, Integer.toString(RiptideBindUtil.encodeMouseButton(var1)));
         this.invalidateSettings(this.bindingOptionModule);
         this.bindingOptionModule = null;
         this.bindingOption = null;
         return true;
      } else {
         return false;
      }
   }

   private void activateOption(Module var1, Setting<?, ?> var2, int var3) {
      if (var1 != null && var2 != null) {
         switch (var2.kind()) {
            case BOOLEAN:
            case ACTION:
               if (this.host.offlineSetup()) {
                  var1.adjustConfiguredOption(var2, var3 == 1 ? -1 : 1);
               } else {
                  var1.adjustOption(var2, var3 == 1 ? -1 : 1);
               }
            default:
               this.invalidateSettings(var1);
         }
      }
   }

   private void beginEditing(Module var1, Setting<?, ?> var2, int var3, UiBounds var4) {
      if (var1 != null
         && var2 != null
         && var2.kind() != Kind.KEYBIND
         && var2.kind() != Kind.ACTION
         && var2.kind() != Kind.COLOR
         && var2.displayMode() != DisplayMode.FILE_PICKER
         && var2.displayMode() != DisplayMode.READONLY_SUMMARY
         && var2.displayMode() != DisplayMode.MACRO_PICKER
         && var2.displayMode() != DisplayMode.CONDITIONAL_MACRO_PICKER) {
         String var5 = var1.value(var2.id());
         this.editing = new VanillaModuleMenuController.Editing(var1, var2, var5, this.cursorAtX(var5, var3, var4));
         this.beginTextDrag(var4);
      }
   }

   private void handleEditingKey(int var1, int var2) {
      if (this.editing != null) {
         boolean var3 = (var2 & 10) != 0;
         boolean var4 = (var2 & 1) != 0;
         switch (var1) {
            case 65:
               if (var3) {
                  this.editing.selectAll();
               }
               break;
            case 67:
               if (var3) {
                  this.copyEditingSelection();
               }
               break;
            case 86:
               if (var3) {
                  this.pasteEditingClipboard();
               }
               break;
            case 88:
               if (var3) {
                  this.copyEditingSelection();
                  this.deleteEditingSelection();
               }
               break;
            case 256:
               this.finishEditing(true);
               break;
            case 257:
            case 335:
               this.finishEditing(true);
               break;
            case 259:
               if (!this.deleteEditingSelection() && this.editing.cursor > 0) {
                  int var6 = var3 ? this.previousWordBoundary(this.editing.text, this.editing.cursor) : this.editing.cursor - 1;
                  this.replaceEditingRange(var6, this.editing.cursor, "");
               }
               break;
            case 261:
               if (!this.deleteEditingSelection() && this.editing.cursor < this.editing.text.length()) {
                  int var5 = var3 ? this.nextWordBoundary(this.editing.text, this.editing.cursor) : this.editing.cursor + 1;
                  this.replaceEditingRange(this.editing.cursor, var5, "");
               }
               break;
            case 262:
               this.moveEditingCursor(var3 ? this.nextWordBoundary(this.editing.text, this.editing.cursor) : this.editing.cursor + 1, var4);
               break;
            case 263:
               this.moveEditingCursor(var3 ? this.previousWordBoundary(this.editing.text, this.editing.cursor) : this.editing.cursor - 1, var4);
               break;
            case 268:
               this.moveEditingCursor(0, var4);
               break;
            case 269:
               this.moveEditingCursor(this.editing.text.length(), var4);
         }
      }
   }

   private void moveEditingCursor(int var1, boolean var2) {
      if (this.editing != null) {
         if (var2 && !this.editing.hasSelection()) {
            this.editing.selectionAnchor = this.editing.cursor;
         }

         this.editing.cursor = clamp(var1, 0, this.editing.text.length());
         if (!var2) {
            this.editing.clearSelection();
         }
      }
   }

   private boolean deleteEditingSelection() {
      if (this.editing != null && this.editing.hasSelection()) {
         this.replaceEditingRange(this.editing.selectionStart(), this.editing.selectionEnd(), "");
         return true;
      } else {
         return false;
      }
   }

   private void replaceEditingSelection(String var1) {
      if (this.editing != null) {
         int var2 = this.editing.hasSelection() ? this.editing.selectionStart() : this.editing.cursor;
         int var3 = this.editing.hasSelection() ? this.editing.selectionEnd() : this.editing.cursor;
         this.replaceEditingRange(var2, var3, this.sanitizeSingleLineClipboard(var1));
      }
   }

   private void replaceEditingRange(int var1, int var2, String var3) {
      if (this.editing != null) {
         int var4 = clamp(var1, 0, this.editing.text.length());
         int var5 = clamp(Math.max(var4, var2), var4, this.editing.text.length());
         String var6 = var3 == null ? "" : var3;
         this.editing.text = this.editing.text.substring(0, var4) + var6 + this.editing.text.substring(var5);
         this.editing.cursor = var4 + var6.length();
         this.editing.clearSelection();
      }
   }

   private void copyEditingSelection() {
      if (this.editing != null && this.editing.hasSelection()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1 != null && var1.keyboardHandler != null) {
            var1.keyboardHandler.setClipboard(this.editing.text.substring(this.editing.selectionStart(), this.editing.selectionEnd()));
         }
      }
   }

   private void pasteEditingClipboard() {
      if (this.editing != null) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1 != null && var1.keyboardHandler != null) {
            String var2 = var1.keyboardHandler.getClipboard();
            if (var2 != null && !var2.isEmpty()) {
               this.replaceEditingSelection(var2);
            }
         }
      }
   }

   private String sanitizeSingleLineClipboard(String var1) {
      if (var1 != null && !var1.isEmpty()) {
         StringBuilder var2 = new StringBuilder(var1.length());

         for (int var3 = 0; var3 < var1.length(); var3++) {
            char var4 = var1.charAt(var3);
            if (var4 == '\r' || var4 == '\n' || var4 == '\t') {
               var2.append(' ');
            } else if (var4 >= ' ' && var4 != 127) {
               var2.append(var4);
            }
         }

         return var2.toString();
      } else {
         return "";
      }
   }

   private int previousWordBoundary(String var1, int var2) {
      String var3 = var1 == null ? "" : var1;
      int var4 = clamp(var2, 0, var3.length());

      while (var4 > 0 && Character.isWhitespace(var3.charAt(var4 - 1))) {
         var4--;
      }

      if (var4 > 0 && this.isWordChar(var3.charAt(var4 - 1))) {
         while (var4 > 0 && this.isWordChar(var3.charAt(var4 - 1))) {
            var4--;
         }
      } else {
         while (var4 > 0 && !Character.isWhitespace(var3.charAt(var4 - 1)) && !this.isWordChar(var3.charAt(var4 - 1))) {
            var4--;
         }
      }

      return var4;
   }

   private int nextWordBoundary(String var1, int var2) {
      String var3 = var1 == null ? "" : var1;
      int var4 = clamp(var2, 0, var3.length());

      while (var4 < var3.length() && Character.isWhitespace(var3.charAt(var4))) {
         var4++;
      }

      if (var4 < var3.length() && this.isWordChar(var3.charAt(var4))) {
         while (var4 < var3.length() && this.isWordChar(var3.charAt(var4))) {
            var4++;
         }
      } else {
         while (var4 < var3.length() && !Character.isWhitespace(var3.charAt(var4)) && !this.isWordChar(var3.charAt(var4))) {
            var4++;
         }
      }

      return var4;
   }

   private boolean isWordChar(char var1) {
      return Character.isLetterOrDigit(var1) || var1 == '_' || var1 == '-' || var1 == ':' || var1 == '.';
   }

   private void finishEditing(boolean var1) {
      if (this.editing != null) {
         VanillaModuleMenuController.Editing var2 = this.editing;
         this.editing = null;
         this.editingFieldBounds = null;
         this.draggingTextSelection = false;
         if (var2.option == null) {
            if (var2.module == null) {
               this.searchQuery = var1 ? var2.text : "";
            } else {
               this.settingsSearchQuery = var1 ? var2.text : "";
            }
         } else if (var1) {
            Setting var3 = var2.option;
            String var4 = var2.text;
            if (var3.kind() == Kind.INTEGER) {
               try {
                  Integer.parseInt(var4);
               } catch (NumberFormatException var7) {
                  RiptideClientMessaging.sendPrefixed("Invalid number for " + var3.label() + ".");
                  return;
               }
            } else if (var3.kind() == Kind.DOUBLE) {
               try {
                  Double.parseDouble(var4);
               } catch (NumberFormatException var6) {
                  RiptideClientMessaging.sendPrefixed("Invalid number for " + var3.label() + ".");
                  return;
               }
            } else if (var3.kind() == Kind.COLOR && parseColor(var4, -1) == -1) {
               RiptideClientMessaging.sendPrefixed("Invalid color: use RRGGBB or AARRGGBB.");
               return;
            }

            this.setOptionValue(var2.module, var3, var4);
            this.invalidateSettings(var2.module);
         }
      }
   }

   private ValueRange rangeValue(Module var1, Setting<?, ?> var2) {
      return ValueRange.parse(var1.value(var2.id()), ValueRange.parse(var2.defaultValue(), new ValueRange(var2.min(), var2.max())));
   }

   private void updateRangeSlider(int var1) {
      if (this.sliderModule != null && this.sliderOption != null && this.rangeThumb != -1) {
         VanillaModuleMenuController.Hit var2 = this.findHit(VanillaModuleMenuController.HitType.RANGE_SLIDER, this.sliderModule, this.sliderOption);
         if (var2 != null) {
            double var3 = RangeSlider.valueFromMouse(var1, var2.bounds, this.sliderOption.sliderMin(), this.sliderOption.sliderMax(), this.sliderOption.step());
            ValueRange var5 = this.rangeValue(this.sliderModule, this.sliderOption);
            boolean var6 = this.rangeThumb == 0;
            ValueRange var7 = var6 ? new ValueRange(var3, var5.max()) : new ValueRange(var5.min(), var3);
            boolean var8 = var6 == var3 <= var7.max();
            double var9 = this.sliderOption instanceof RangeSetting var11 ? var11.minSeparation() : 0.0;
            this.setOptionValueTransient(
               this.sliderModule, this.sliderOption, var7.withMinSeparation(var9, this.sliderOption.min(), this.sliderOption.max(), var8).toString()
            );
            this.invalidateSettings(this.sliderModule);
         }
      }
   }

   private void updateSlider(int var1) {
      if (this.sliderModule != null && this.sliderOption != null) {
         if (this.rangeThumb != -1) {
            this.updateRangeSlider(var1);
         } else {
            VanillaModuleMenuController.Hit var2 = this.findHit(VanillaModuleMenuController.HitType.SLIDER, this.sliderModule, this.sliderOption);
            if (var2 != null) {
               double var3 = (double)(var1 - var2.bounds.x()) / Math.max(1, var2.bounds.width());
               var3 = Math.max(0.0, Math.min(1.0, var3));
               double var5 = this.sliderOption.sliderMin() + var3 * (this.sliderOption.sliderMax() - this.sliderOption.sliderMin());
               double var7 = Math.max(1.0E-4, this.sliderOption.step());
               var5 = this.sliderOption.sliderMin() + Math.round((var5 - this.sliderOption.sliderMin()) / var7) * var7;
               if (this.sliderOption.kind() == Kind.INTEGER) {
                  this.setOptionValueTransient(this.sliderModule, this.sliderOption, Integer.toString((int)Math.round(var5)));
               } else {
                  this.setOptionValueTransient(this.sliderModule, this.sliderOption, String.format(Locale.ROOT, "%.2f", var5));
               }

               this.invalidateSettings(this.sliderModule);
            }
         }
      }
   }

   private void openListEditor(Module var1, Setting<?, ?> var2) {
      if (var1 != null && var2 != null) {
         Minecraft var3 = Minecraft.getInstance();
         if (var3 != null) {
            switch (var2.kind()) {
               case STRING_LIST:
                  this.host.openStringListEditor(var1, var2);
                  break;
               case ITEM_LIST:
               case BLOCK_LIST:
               case ENTITY_TYPE_LIST:
               case SOUND_EVENT_LIST:
               case STORAGE_LIST:
                  this.host.openRegistryListEditor(var1, var2);
            }
         }
      }
   }

   private void openSettings(Module var1) {
      if (var1 != null) {
         if (!this.hasVisibleSettings(var1)) {
            RiptideNotifications.show(var1.name() + " has no settings", -14249);
         } else {
            this.settingsSearchQuery = "";
            if (this.isSettingsSearchEditing()) {
               this.editing = null;
               this.editingFieldBounds = null;
               this.draggingTextSelection = false;
            }

            this.selectedModule = var1;
         }
      }
   }

   private boolean hasVisibleSettings(Module var1) {
      for (Setting var3 : var1.settings()) {
         if (var3.isVisible() && this.optionAvailable(var3)) {
            return true;
         }
      }

      return false;
   }

   private float hoverFade(String var1, boolean var2) {
      long var3 = System.nanoTime();
      VanillaModuleMenuController.HoverFade var5 = this.hoverFades.get(var1);
      if (var5 == null) {
         var5 = new VanillaModuleMenuController.HoverFade();
         var5.value = var2 ? 1.0F : 0.0F;
         var5.target = var2;
         var5.since = var3;
         this.hoverFades.put(var1, var5);
         return var5.value;
      } else {
         if (var5.target != var2) {
            var5.start = var5.value(var3);
            var5.target = var2;
            var5.since = var3;
         }

         return var5.value(var3);
      }
   }

   private static int lerpColor(int var0, int var1, float var2) {
      int var3 = var0 >> 16 & 0xFF;
      int var4 = var0 >> 8 & 0xFF;
      int var5 = var0 & 0xFF;
      int var6 = var1 >> 16 & 0xFF;
      int var7 = var1 >> 8 & 0xFF;
      int var8 = var1 & 0xFF;
      return 0xFF000000
         | Math.round(var3 + (var6 - var3) * var2) << 16
         | Math.round(var4 + (var7 - var4) * var2) << 8
         | Math.round(var5 + (var8 - var5) * var2);
   }

   private void drawHighlighted(UiContext var1, String var2, int var3, int var4, int var5, int var6, String var7) {
      String var8 = var2 == null ? "" : var2;
      int var9 = var7 != null && !var7.isEmpty() ? var8.toLowerCase(Locale.ROOT).indexOf(var7) : -1;
      if (var9 >= 0 && var1.text().width(var8) <= var5) {
         String var10 = var8.substring(0, var9);
         String var11 = var8.substring(var9, var9 + var7.length());
         String var12 = var8.substring(var9 + var7.length());
         var1.text().draw(var1.graphics(), var10, var3, var4, var6);
         int var13 = var3 + var1.text().width(var10);
         var1.text().draw(var1.graphics(), var11, var13, var4, this.theme.colors().accent);
         var1.text().draw(var1.graphics(), var12, var13 + var1.text().width(var11), var4, var6);
      } else {
         var1.text().drawFitted(var1.graphics(), var8, var3, var4, var5, var6);
      }
   }

   private VanillaModuleMenuController.CachedSettingRows cachedRows(Module var1, int var2) {
      long var3 = this.settingSignature(var1, var2);
      VanillaModuleMenuController.CachedSettingRows var5 = this.settingRowsCache.get(var1.id());
      if (var5 != null && var5.signature == var3) {
         return var5;
      } else {
         ArrayList var6 = new ArrayList();
         int var7 = 0;
         boolean var8 = var1 == this.selectedModule && !this.currentSettingsSearch().trim().isEmpty();
         Map var9 = this.groupedOptions(var1);

         for (Entry var11 : var9.entrySet()) {
            var6.add(VanillaModuleMenuController.SettingRow.group((String)var11.getKey(), var7, 15));
            var7 += 17;
            if (var8 || !this.isGroupCollapsed(var1, (String)var11.getKey())) {
               for (Setting var13 : (List)var11.getValue()) {
                  int var14 = var13.description().isBlank() ? 26 : 36;
                  var6.add(VanillaModuleMenuController.SettingRow.option(var13, var7, var14));
                  var7 += var14 + 2;
               }

               var7 += 4;
            }
         }

         VanillaModuleMenuController.CachedSettingRows var15 = new VanillaModuleMenuController.CachedSettingRows(var3, List.copyOf(var6), var7);
         this.settingRowsCache.put(var1.id(), var15);
         return var15;
      }
   }

   private Map<String, List<Setting<?, ?>>> groupedOptions(Module var1) {
      String var2 = var1 == this.selectedModule ? this.currentSettingsSearch().trim().toLowerCase(Locale.ROOT) : "";
      LinkedHashMap var3 = new LinkedHashMap();

      for (Setting var5 : var1.settings()) {
         if (var5.isVisible() && this.optionAvailable(var5) && (var2.isEmpty() || matchesSettingSearch(var5, var2))) {
            var3.computeIfAbsent(var5.group(), var0 -> new ArrayList<>()).add(var5);
         }
      }

      for (List var7 : var3.values()) {
         var7.sort(Comparator.comparingInt(VanillaModuleMenuController::settingOrder));
      }

      return var3;
   }

   private static int settingOrder(Setting<?, ?> var0) {
      if ("mode".equals(var0.id())) {
         return 0;
      } else if (var0.isListModeSelector()) {
         return 1;
      } else {
         return var0.isListSelection() ? 2 : 3;
      }
   }

   private static boolean matchesSettingSearch(Setting<?, ?> var0, String var1) {
      return containsIgnoreCase(var0.label(), var1)
         || containsIgnoreCase(var0.group(), var1)
         || containsIgnoreCase(var0.description(), var1)
         || containsIgnoreCase(var0.id(), var1);
   }

   private static boolean containsIgnoreCase(String var0, String var1) {
      return var0 != null && var0.toLowerCase(Locale.ROOT).contains(var1);
   }

   private boolean optionAvailable(Setting<?, ?> var1) {
      return var1 != null && this.host.offlineSetup() ? var1.isAvailable(false, false) : var1 != null;
   }

   private void toggleModule(Module var1) {
      if (var1 != null) {
         if (this.host.offlineSetup()) {
            var1.setConfiguredEnabled(!var1.isEnabled());
         } else {
            var1.toggle();
         }
      }
   }

   private void setOptionValue(Module var1, Setting<?, ?> var2, String var3) {
      if (var1 != null && var2 != null) {
         if (this.host.offlineSetup()) {
            var1.setConfiguredValue(var2.id(), var3);
         } else {
            var1.setValue(var2.id(), var3);
         }
      }
   }

   private void setOptionValueTransient(Module var1, Setting<?, ?> var2, String var3) {
      if (var1 != null && var2 != null) {
         if (this.host.offlineSetup()) {
            var1.setConfiguredValueTransient(var2.id(), var3);
         } else {
            var1.setValueTransient(var2.id(), var3);
         }
      }
   }

   private long settingSignature(Module var1, int var2) {
      long var3 = ModuleRegistry.revision();
      var3 = var3 * 31L + var2;
      var3 = var3 * 31L + (var1.isEnabled() ? 1 : 0);
      if (var1 == this.selectedModule) {
         var3 = var3 * 31L + this.currentSettingsSearch().hashCode();
      }

      return var3;
   }

   private void invalidateSettings(Module var1) {
      if (var1 != null) {
         this.settingRowsCache.remove(var1.id());
         if (!this.summaryCache.isEmpty()) {
            this.summaryCache.clear();
         }
      }
   }

   private int clampSettingsScroll(Module var1, int var2, int var3) {
      settingsScrollMetrics.put(var1, new int[]{var2, var3});
      return ScrollContainer.clampScroll(settingsScroll.getOrDefault(var1, 0), var2, var3);
   }

   private int clampWindowScroll(String var1, int var2, int var3) {
      windowScrollMetrics.put(var1, new int[]{var2, var3});
      return ScrollContainer.clampScroll(windowScroll.getOrDefault(var1, 0), var2, var3);
   }

   private Scrollbar.Metrics metricsForHit(VanillaModuleMenuController.Hit var1) {
      if (var1 == null || var1.type != VanillaModuleMenuController.HitType.SCROLLBAR) {
         return null;
      } else if ("settings".equals(var1.id) && this.selectedModule != null) {
         int var5 = Math.min(360, Math.max(1, this.screenWidth - 8)) - 10;
         VanillaModuleMenuController.CachedSettingRows var6 = this.cachedRows(this.selectedModule, var5);
         int var7 = ScrollContainer.clampScroll(settingsScroll.getOrDefault(this.selectedModule, 0), var6.contentHeight, var1.bounds.height());
         return Scrollbar.metrics(var1.bounds, var6.contentHeight, var1.bounds.height(), var7);
      } else {
         ModuleCategory var2 = var1.category;
         int var3;
         if (var2 != null) {
            var3 = this.orderedModules(var2).size() * 15;
         } else if ("SEARCH".equals(var1.id)) {
            var3 = this.searchMatches().size() * 15;
         } else {
            var3 = this.utilityContentHeight();
         }

         int var4 = ScrollContainer.clampScroll(windowScroll.getOrDefault(var1.id, 0), var3, var1.bounds.height());
         return Scrollbar.metrics(var1.bounds, var3, var1.bounds.height(), var4);
      }
   }

   private UiBounds scrollbarHitBounds(UiBounds var1) {
      return UiBounds.of(var1.x() - 1, var1.y(), var1.width() + 2, var1.height());
   }

   private VanillaModuleMenuController.Hit findScrollbarHit(String var1) {
      for (VanillaModuleMenuController.Hit var3 : this.hits) {
         if (var3.type == VanillaModuleMenuController.HitType.SCROLLBAR && var3.id.equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   private VanillaModuleMenuController.Hit scrollTargetAt(int var1, int var2) {
      for (int var3 = this.hits.size() - 1; var3 >= 0; var3--) {
         VanillaModuleMenuController.Hit var4 = this.hits.get(var3);
         if (var4.bounds.contains(var1, var2)) {
            if (var4.type != VanillaModuleMenuController.HitType.WINDOW_HEADER
               && var4.type != VanillaModuleMenuController.HitType.SETTINGS_HEADER
               && var4.type != VanillaModuleMenuController.HitType.BACK) {
               if (this.selectedModule != null) {
                  return new VanillaModuleMenuController.Hit(
                     VanillaModuleMenuController.HitType.SCROLL_AREA, var4.bounds, this.selectedModule, null, null, "settings", -1
                  );
               }

               if ("SEARCH".equals(var4.id)) {
                  return new VanillaModuleMenuController.Hit(VanillaModuleMenuController.HitType.SCROLL_AREA, var4.bounds, null, null, null, "SEARCH", -1);
               }

               if (var4.category != null) {
                  return new VanillaModuleMenuController.Hit(
                     VanillaModuleMenuController.HitType.SCROLL_AREA, var4.bounds, null, null, var4.category, var4.category.name(), -1
                  );
               }

               if (var4.type != VanillaModuleMenuController.HitType.SCROLL_AREA && var4.type != VanillaModuleMenuController.HitType.SCROLLBAR) {
                  return null;
               }

               return var4;
            }

            return null;
         }
      }

      return null;
   }

   private void setScroll(VanillaModuleMenuController.Hit var1, int var2) {
      if ("settings".equals(var1.id) && this.selectedModule != null) {
         settingsScroll.put(this.selectedModule, var2);
      } else {
         windowScroll.put(var1.id, var2);
      }
   }

   private void scrollBy(String var1, Module var2, int var3) {
      if ("settings".equals(var1) && var2 != null) {
         settingsScroll.put(var2, nudged(settingsScroll.getOrDefault(var2, 0), settingsScrollMetrics.get(var2), var3));
      } else {
         windowScroll.put(var1, nudged(windowScroll.getOrDefault(var1, 0), windowScrollMetrics.get(var1), var3));
      }
   }

   private static int nudged(int var0, int[] var1, int var2) {
      if (var1 == null) {
         return Math.max(0, var0 + var2);
      } else {
         int var3 = ScrollContainer.clampScroll(var0, var1[0], var1[1]);
         return ScrollContainer.clampScroll(Math.max(0, var3 + var2), var1[0], var1[1]);
      }
   }

   private boolean isGroupCollapsed(Module var1, String var2) {
      return groupCollapsed.getOrDefault(var1.id() + ":" + var2, false);
   }

   private void setGroupCollapsed(Module var1, String var2, boolean var3) {
      groupCollapsed.put(var1.id() + ":" + var2, var3);
   }

   private void initializeWindowDefaults() {
      RiptideConfig.ModuleCategoryLayout var1 = this.layout("MAIN_MENU");
      if (var1.x < 0 || var1.y < 0) {
         var1.x = Math.max(4, this.screenWidth - 170 - 8);
         var1.y = 4;
      }

      boolean var2 = false;
      boolean var3 = false;

      for (ModuleCategory var5 : ModuleCategory.values()) {
         RiptideConfig.ModuleCategoryLayout var6 = this.layout(var5.name());
         if (var6.x >= 0 && var6.y >= 0) {
            var3 = true;
         } else {
            var2 = true;
         }
      }

      if (var2 && var3) {
         this.placeMissingBelowExisting();
      } else if (var2) {
         int var18 = var1.x < 0 ? Math.max(4, this.screenWidth - 170 - 8) : var1.x;
         int var20 = Math.max(112, Math.min(170, this.screenWidth - 8));
         boolean var22 = var18 >= this.screenWidth / 2;
         int var7 = var22 ? 4 : Math.min(this.screenWidth - 4, var18 + var20 + 8);
         int var8 = var22 ? Math.max(108, var18 - 8) : Math.max(var7 + 100 + 8, this.screenWidth - 4);
         int var9 = var7;
         int var10 = 4;
         int var11 = 0;

         for (ModuleCategory var13 : ModuleCategory.values()) {
            RiptideConfig.ModuleCategoryLayout var14 = this.layout(var13.name());
            int var15 = this.windowWidth(var13.name());
            if (var9 + var15 + 4 > var8 && var9 > var7) {
               var9 = 4;
               var10 += var11 + 4;
               var11 = 0;
            }

            var14.x = var9;
            var14.y = var10;
            int var16 = this.orderedModules(var13).size() * 15;
            int var17 = 15 + Math.min(var16, this.maxCategoryBodyHeight(var13));
            var11 = Math.max(var11, var17);
            var9 += var15 + 4;
         }

         RiptideConfig.ModuleCategoryLayout var28 = this.layout("SEARCH");
         int var29 = this.windowWidth("SEARCH");
         if (var9 + var29 + 4 > var8 && var9 > var7) {
            var9 = 4;
            var10 += var11 + 4;
         }

         var28.x = var9;
         var28.y = var10;
      } else {
         RiptideConfig.ModuleCategoryLayout var19 = this.layout("SEARCH");
         if (var19.x < 0 || var19.y < 0) {
            int var21 = 4;

            for (ModuleCategory var24 : ModuleCategory.values()) {
               RiptideConfig.ModuleCategoryLayout var25 = this.layout(var24.name());
               int var26 = this.orderedModules(var24).size() * 15;
               int var27 = var25.y + 15 + Math.min(var26, this.maxCategoryBodyHeight(var24));
               var21 = Math.max(var21, var27);
            }

            var19.x = 4;
            var19.y = var21 + 4;
         }
      }

      this.ensureWindowZOrder();
   }

   private void placeMissingBelowExisting() {
      int var1 = 4;

      for (ModuleCategory var3 : ModuleCategory.values()) {
         RiptideConfig.ModuleCategoryLayout var4 = this.layout(var3.name());
         if (var4.x >= 0 && var4.y >= 0) {
            var1 = Math.max(var1, var4.y + this.windowHeightFor(var3));
         }
      }

      RiptideConfig.ModuleCategoryLayout var11 = this.layout("MAIN_MENU");
      if (var11.x >= 0 && var11.y >= 0) {
         var1 = Math.max(var1, var11.y + 15);
      }

      int var12 = 4;
      int var13 = var1 + 4;
      int var5 = 0;
      int var6 = Math.max(108, this.screenWidth - 4);

      for (ModuleCategory var8 : ModuleCategory.values()) {
         RiptideConfig.ModuleCategoryLayout var9 = this.layout(var8.name());
         if (var9.x < 0 || var9.y < 0) {
            int var10 = this.windowWidth(var8.name());
            if (var12 + var10 + 4 > var6 && var12 > 4) {
               var12 = 4;
               var13 += var5 + 4;
               var5 = 0;
            }

            var9.x = var12;
            var9.y = var13;
            var5 = Math.max(var5, this.windowHeightFor(var8));
            var12 += var10 + 4;
         }
      }
   }

   private int windowHeightFor(ModuleCategory var1) {
      int var2 = this.orderedModules(var1).size() * 15;
      return 15 + Math.min(var2, this.maxCategoryBodyHeight(var1));
   }

   private RiptideConfig.ModuleCategoryLayout layout(String var1) {
      Map var2 = RiptideConfig.getGlobal().moduleCategoryLayouts;
      if ("MAIN_MENU".equals(var1) && !var2.containsKey("MAIN_MENU") && var2.containsKey("UTILITIES")) {
         RiptideConfig.ModuleCategoryLayout var3 = (RiptideConfig.ModuleCategoryLayout)var2.get("UTILITIES");
         RiptideConfig.ModuleCategoryLayout var4 = new RiptideConfig.ModuleCategoryLayout();
         var4.x = var3.x;
         var4.y = var3.y;
         var4.collapsed = var3.collapsed;
         var2.put("MAIN_MENU", var4);
      }

      return var2.computeIfAbsent(var1, var0 -> new RiptideConfig.ModuleCategoryLayout());
   }

   private List<String> windowRenderOrder() {
      this.ensureWindowZOrder();
      return List.copyOf(windowZOrder);
   }

   private static Set<String> buildValidWindowIds() {
      LinkedHashSet var0 = new LinkedHashSet();

      for (ModuleCategory var2 : ModuleCategory.values()) {
         var0.add(var2.name());
      }

      var0.add("SEARCH");
      var0.add("MAIN_MENU");
      return var0;
   }

   private void ensureWindowZOrder() {
      if (windowZOrder.size() != VALID_WINDOW_IDS.size()) {
         windowZOrder.removeIf(var0 -> !VALID_WINDOW_IDS.contains(var0));

         for (String var2 : VALID_WINDOW_IDS) {
            if (!windowZOrder.contains(var2)) {
               windowZOrder.add(var2);
            }
         }
      }
   }

   private void bringWindowToFront(String var1) {
      if (var1 != null && !var1.isBlank()) {
         this.ensureWindowZOrder();
         windowZOrder.remove(var1);
         windowZOrder.add(var1);
      }
   }

   private ModuleCategory categoryById(String var1) {
      if (var1 == null) {
         return null;
      } else {
         for (ModuleCategory var3 : ModuleCategory.values()) {
            if (var3.name().equals(var1)) {
               return var3;
            }
         }

         return null;
      }
   }

   private List orderedModules$riptideOrig(ModuleCategory var1) {
      List var2 = RiptideConfig.getGlobal().moduleCategoryOrder.computeIfAbsent(var1.name(), var0 -> new ArrayList<>());
      int var3 = ModuleRegistry.revision();
      boolean var4 = PackHideState.isActive();
      String var5 = String.join("|", var2);
      VanillaModuleMenuController.CachedOrderedModules var6 = this.orderedModulesCache.get(var1);
      if (var6 != null && var6.revision == var3 && var6.hide == var4 && var6.orderSignature.equals(var5)) {
         return var6.modules;
      } else {
         ArrayList var7 = new ArrayList<>(ModuleRegistry.byCategory(var1));
         if (var4) {
            var7.removeIf(var0 -> !PackHideState.isHideModule(var0));
         }

         if (this.host.offlineSetup()) {
            var7.removeIf(
               var1x -> !var1x.hasActivationToggle()
                  && var1x.settings().stream().noneMatch(var1xx -> var1xx.isVisible() && this.optionAvailable((Setting<?, ?>)var1xx))
            );
         }

         HashSet var8 = new HashSet(var2);
         boolean var9 = false;

         for (Module var11 : var7) {
            if (!var8.contains(var11.id())) {
               var2.add(var11.id());
               var9 = true;
            }
         }

         if (var9) {
            var5 = String.join("|", var2);
         }

         HashMap var12 = new HashMap();

         for (int var13 = 0; var13 < var2.size(); var13++) {
            var12.put((String)var2.get(var13), var13);
         }

         var7.sort(Comparator.comparingInt(var1x -> var12.getOrDefault(var1x.id(), Integer.MAX_VALUE)));
         List var14 = List.copyOf(var7);
         this.orderedModulesCache.put(var1, new VanillaModuleMenuController.CachedOrderedModules(var3, var4, var5, var14));
         return var14;
      }
   }

   private float animatedEnabledProgress(Module var1) {
      if (var1 == null) {
         return 0.0F;
      } else {
         float var2 = var1.isEnabled() ? 1.0F : 0.0F;
         VanillaModuleMenuController.AnimatedValue var3 = this.enabledAnimations.get(var1.id());
         long var4 = System.nanoTime();
         if (var3 == null) {
            var3 = new VanillaModuleMenuController.AnimatedValue(var2, var4);
            this.enabledAnimations.put(var1.id(), var3);
            return var2;
         } else {
            float var6 = Math.max(0.0F, Math.min(0.05F, (float)(var4 - var3.lastNanos) / 1.0E9F));
            var3.lastNanos = var4;
            float var7 = var6 / 0.16F;
            if (var3.value < var2) {
               var3.value = Math.min(var2, var3.value + var7);
            } else if (var3.value > var2) {
               var3.value = Math.max(var2, var3.value - var7);
            }

            return var3.value;
         }
      }
   }

   private float enabledProgressSnapshot(Module var1) {
      if (var1 == null) {
         return 0.0F;
      } else {
         VanillaModuleMenuController.AnimatedValue var2 = this.enabledAnimations.get(var1.id());
         if (var2 != null) {
            return var2.value;
         } else {
            return var1.isEnabled() ? 1.0F : 0.0F;
         }
      }
   }

   private static int alphaColor(int var0, float var1) {
      int var2 = Math.max(0, Math.min(255, Math.round((var0 >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, var1)))));
      return var2 << 24 | var0 & 16777215;
   }

   private VanillaModuleMenuController.Hit findHit(VanillaModuleMenuController.HitType var1, Module var2, Setting<?, ?> var3) {
      for (VanillaModuleMenuController.Hit var5 : this.hits) {
         if (var5.type == var1 && var5.module == var2 && var5.option == var3) {
            return var5;
         }
      }

      return null;
   }

   private void recycleHits() {
      this.recycledHits.addAll(this.hits);
      this.hits.clear();
   }

   private void addHit(VanillaModuleMenuController.HitType var1, UiBounds var2, Module var3, Setting<?, ?> var4, ModuleCategory var5, String var6, int var7) {
      VanillaModuleMenuController.Hit var8 = this.recycledHits.isEmpty()
         ? new VanillaModuleMenuController.Hit()
         : this.recycledHits.remove(this.recycledHits.size() - 1);
      var8.set(var1, var2, var3, var4, var5, var6, var7);
      this.hits.add(var8);
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (Exception var3) {
         return var1;
      }
   }

   private static double parseDouble(String var0, double var1) {
      try {
         return Double.parseDouble(var0);
      } catch (Exception var4) {
         return var1;
      }
   }

   private static int parseColor(String var0, int var1) {
      if (var0 == null) {
         return var1;
      } else {
         String var2 = var0.trim();
         if (var2.startsWith("#")) {
            var2 = var2.substring(1);
         }

         try {
            long var3 = Long.parseUnsignedLong(var2, 16);
            if (var2.length() <= 6) {
               var3 |= 4278190080L;
            }

            return (int)var3;
         } catch (Exception var5) {
            return var1;
         }
      }
   }

   private static String listSummary(String var0) {
      List var1 = StringListCodec.parse(var0);
      int var2 = var1.size();
      if (var2 <= 0) {
         return "Empty";
      } else {
         return var2 == 1 ? (String)var1.get(0) : var2 + " entries";
      }
   }

   private String optionSummary(Module var1, Setting<?, ?> var2) {
      if (var1 != null && var2 != null) {
         String var3 = var1.value(var2.id());
         if (var2.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER) {
            return conditionalMacroSummary(var3);
         } else if (var2.displayMode() == DisplayMode.MACRO_PICKER) {
            return macroSummary(var3);
         } else {
            return switch (var2.kind()) {
               case STRING_LIST, ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, STORAGE_LIST -> this.cachedSummary(var1, var2, var3, () -> listSummary(var3));
               case SOUND_EVENT_LIST -> this.cachedSummary(var1, var2, var3, () -> soundListSummary(var3));
               case PACKET_LIST -> this.cachedSummary(var1, var2, var3, () -> packetListSummary(var2, var3));
               default -> var2.format(var3);
            };
         }
      } else {
         return "";
      }
   }

   private String cachedSummary(Module var1, Setting<?, ?> var2, String var3, Supplier<String> var4) {
      if (this.summaryCache.size() > 512) {
         this.summaryCache.clear();
      }

      return this.summaryCache.computeIfAbsent(var1.id() + "\u0000" + var2.id() + "\u0000" + var3, var1x -> (String)var4.get());
   }

   private static String macroSummary(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      if (var1.isBlank()) {
         return "No macro";
      } else {
         return RiptideMacroManager.get().get(var1) == null ? "Missing: " + var1 : var1;
      }
   }

   private static String conditionalMacroSummary(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      if (var1.isBlank()) {
         return "No macro";
      } else {
         String var2 = var1.toLowerCase(Locale.ROOT);
         if (var2.equals("AutoFish - Free Slots Stop".toLowerCase(Locale.ROOT))
            || var2.startsWith("AutoFish - Free Slots Stop".toLowerCase(Locale.ROOT) + " (")) {
            return "Free Slots";
         } else if (var2.equals("AutoFish - Durability Stop".toLowerCase(Locale.ROOT))
            || var2.startsWith("AutoFish - Durability Stop".toLowerCase(Locale.ROOT) + " (")) {
            return "Durability";
         } else if (!var2.equals("AutoFish - Custom Stop".toLowerCase(Locale.ROOT))
            && !var2.startsWith("AutoFish - Custom Stop".toLowerCase(Locale.ROOT) + " (")) {
            return RiptideMacroManager.get().get(var1) == null ? "Missing: " + var1 : var1;
         } else {
            return "Custom";
         }
      }
   }

   private static String packetListSummary(Setting<?, ?> var0, String var1) {
      boolean var2 = PacketListCodec.isC2SOption(var0 == null ? "" : var0.id());
      Set var3 = PacketListCodec.resolvePackets(var1, var2);
      List var4 = PacketListCodec.invalidTokens(var1, var2);
      int var5 = var3.size() + var4.size();
      if (var5 <= 0) {
         return "Empty";
      } else if (var5 > 1) {
         return var5 + " packets";
      } else {
         return !var3.isEmpty() ? RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)var3.iterator().next()) : (String)var4.get(0);
      }
   }

   private static String soundListSummary(String var0) {
      List var1 = StringListCodec.parse(var0);
      if (var1.isEmpty()) {
         return "Empty";
      } else {
         return var1.size() > 1 ? var1.size() + " sounds" : compactSoundId((String)var1.get(0));
      }
   }

   private static String compactSoundId(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.trim();
         int var2 = var1.indexOf(58);
         String var3 = var2 >= 0 ? var1.substring(var2 + 1) : var1;
         String[] var4 = var3.split("[._/]+");
         ArrayList var5 = new ArrayList();

         for (String var9 : var4) {
            if (!var9.isBlank()) {
               var5.add(var9);
            }
         }

         if (var5.size() >= 2) {
            return (String)var5.get(var5.size() - 2) + "." + (String)var5.get(var5.size() - 1);
         } else {
            return var3.isBlank() ? var1 : var3;
         }
      } else {
         return "Empty";
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }

   private void renderSummaryActionRow(
      UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4, String var5, Button.Tone var6, VanillaModuleMenuController.HitType var7
   ) {
      UiBounds var8 = UiBounds.of(var4.right() - 40, var4.y(), 40, var4.height());
      UiBounds var9 = UiBounds.of(var4.x(), var4.y(), Math.max(1, var4.width() - 44), var4.height());
      this.drawSummaryValue(var1, var2, var3, var9, var7 == VanillaModuleMenuController.HitType.MACRO_PICKER);
      Button.render(var1, var8, var5, var6, var8.contains(var1.mouseX(), var1.mouseY()), false);
      Setting var10 = var3.linkedActionId().isBlank() ? var3 : var2.setting(var3.linkedActionId());
      this.addHit(var7, var8, var2, var10 == null ? var3 : var10, null, "", -1);
   }

   private void renderSummaryOnly(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      this.drawSummaryValue(var1, var2, var3, var4);
   }

   private void drawSummaryValue(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4) {
      this.drawSummaryValue(var1, var2, var3, var4, false);
   }

   private void drawSummaryValue(UiContext var1, Module var2, Setting<?, ?> var3, UiBounds var4, boolean var5) {
      String var6 = this.optionSummary(var2, var3);
      boolean var7 = var6 == null || var6.isBlank() || "Empty".equals(var6) || "No file selected".equals(var6);
      String var8 = var7 ? (var6 != null && !var6.isBlank() ? var6 : "Empty") : var6;
      int var9 = Math.max(1, var4.width() - 4);
      int var10 = var4.x() + 2;
      if (var5) {
         int var11 = var1.text().width(var8);
         if (var11 < var9) {
            var10 = Math.max(var4.x() + 2, var4.right() - 2 - var11);
         }
      }

      var1.text()
         .drawFitted(
            var1.graphics(),
            var8,
            var10,
            var1.text().centeredY(var4),
            Math.max(1, var4.right() - 2 - var10),
            var7 ? this.theme.colors().muted : this.theme.colors().text
         );
      if (var4.contains(var1.mouseX(), var1.mouseY()) && var6 != null && !var6.isBlank()) {
         this.tooltip = var6;
      }
   }

   private List<Module> orderedModules(ModuleCategory var1) {
      return RiptideFavorites.orderedModules(this, var1);
   }

   public boolean mouseClicked(int var1, int var2, int var3) {
      return RiptideFavorites.mouseClicked(this, var1, var2, var3);
   }

   private void renderModuleRow(UiContext var1, Module var2, UiBounds var3, ModuleCategory var4, int var5) {
      RiptideFavorites.renderModuleRow(this, var1, var2, var3, var4, var5);
   }

   private static final class AnimatedValue {
      private float value;
      private long lastNanos;

      private AnimatedValue(float var1, long var2) {
         this.value = var1;
         this.lastNanos = var2;
      }
   }

   private record CachedOrderedModules(int revision, boolean hide, String orderSignature, List<Module> modules) {
   }

   private record CachedSettingRows(long signature, List<VanillaModuleMenuController.SettingRow> rows, int contentHeight) {
   }

   private record DragScrollbar(String id, int grabOffset) {
   }

   private static final class Editing {
      private final Module module;
      private final Setting<?, ?> option;
      private String text;
      private int cursor;
      private int selectionAnchor = -1;

      private Editing(Module var1, Setting<?, ?> var2, String var3, int var4) {
         this.module = var1;
         this.option = var2;
         this.text = var3 == null ? "" : var3;
         this.cursor = VanillaModuleMenuController.clamp(var4, 0, this.text.length());
      }

      private boolean hasSelection() {
         return this.selectionAnchor >= 0 && this.selectionAnchor != this.cursor;
      }

      private int selectionStart() {
         return this.hasSelection() ? Math.min(this.selectionAnchor, this.cursor) : this.cursor;
      }

      private int selectionEnd() {
         return this.hasSelection() ? Math.max(this.selectionAnchor, this.cursor) : this.cursor;
      }

      private void clearSelection() {
         this.selectionAnchor = -1;
      }

      private void selectAll() {
         this.selectionAnchor = 0;
         this.cursor = this.text.length();
      }
   }

   private static final class Hit {
      private VanillaModuleMenuController.HitType type;
      private UiBounds bounds;
      private Module module;
      private Setting<?, ?> option;
      private ModuleCategory category;
      private String id;
      private int index;

      private Hit() {
      }

      private Hit(VanillaModuleMenuController.HitType var1, UiBounds var2, Module var3, Setting<?, ?> var4, ModuleCategory var5, String var6, int var7) {
         this.set(var1, var2, var3, var4, var5, var6, var7);
      }

      private void set(VanillaModuleMenuController.HitType var1, UiBounds var2, Module var3, Setting<?, ?> var4, ModuleCategory var5, String var6, int var7) {
         this.type = var1;
         this.bounds = var2;
         this.module = var3;
         this.option = var4;
         this.category = var5;
         this.id = var6 == null ? "" : var6;
         this.index = var7;
      }
   }

   private static enum HitType {
      WINDOW_HEADER,
      SETTINGS_HEADER,
      MODULE,
      MODULE_BIND,
      UTILITY,
      BACK,
      TOGGLE_MODULE,
      BIND_MODULE,
      MACRO,
      RESET_MODULE_SETTINGS,
      GROUP,
      OPTION,
      FILE_PICKER,
      SLIDER,
      RANGE_SLIDER,
      TEXT_FIELD,
      DROPDOWN,
      COLOR_PICKER,
      LIST_EDITOR,
      PACKET_EDITOR,
      MACRO_PICKER,
      SEARCH_FIELD,
      SETTINGS_SEARCH_FIELD,
      BIND_OPTION,
      RESET_OPTION_BIND,
      SCROLL_AREA,
      SCROLLBAR,
      CATEGORY_RESIZE;
   }

   public interface Host {
      default boolean offlineSetup() {
         return false;
      }

      Screen screen();

      Font font();

      void closeMenu();

      void saveConfig();

      void openPacketSelector(Module var1, Setting<?, ?> var2);

      void openStringListEditor(Module var1, Setting<?, ?> var2);

      void openRegistryListEditor(Module var1, Setting<?, ?> var2);

      void openMacroCreator(Module var1, Setting<?, ?> var2);

      default void openMacroCreator(Module var1, Setting<?, ?> var2, RiptideMacro var3) {
         this.openMacroCreator(var1, var2);
      }

      void runUtility(String var1);

      void addToggleMacro(Module var1);
   }

   private static final class HoverFade {
      float start;
      float value;
      boolean target;
      long since;

      float value(long var1) {
         float var3 = Math.min(1.0F, (float)(var1 - this.since) / 9.0E7F);
         float var4 = var3 * var3 * (3.0F - 2.0F * var3);
         return this.start + ((this.target ? 1.0F : 0.0F) - this.start) * var4;
      }
   }

   private final class MacroPicker {
      private static final int ROW_H = 18;
      private static final int MAX_ROWS = 8;
      private static final String PRESET_FREE_SLOTS = "Free Slots";
      private static final String PRESET_DURABILITY = "Durability";
      private final Module module;
      private final Setting<?, ?> option;
      private final UiBounds anchor;
      private String query;
      private int scroll;
      private UiBounds menuBounds;
      private UiBounds searchBounds;
      private UiBounds clearBounds;
      private UiBounds createBounds;
      private UiBounds listBounds;
      private List<String> visible;
      private String filterQuery;
      private long filterMacroRevision;

      private MacroPicker(Module nullx, Setting<?, ?> nullxx, UiBounds nullxxx) {
         Objects.requireNonNull(VanillaModuleMenuController.this);
         super();
         this.query = "";
         this.menuBounds = UiBounds.of(0, 0, 0, 0);
         this.searchBounds = UiBounds.of(0, 0, 0, 0);
         this.clearBounds = UiBounds.of(0, 0, 0, 0);
         this.createBounds = UiBounds.of(0, 0, 0, 0);
         this.listBounds = UiBounds.of(0, 0, 0, 0);
         this.visible = List.of();
         this.filterQuery = null;
         this.filterMacroRevision = Long.MIN_VALUE;
         this.module = nullx;
         this.option = nullxx;
         this.anchor = nullxxx == null ? UiBounds.of(0, 0, 120, 18) : nullxxx;
      }

      private void setQuery(String var1) {
         String var2 = var1 == null ? "" : var1;
         if (!this.query.equals(var2)) {
            this.query = var2;
            this.scroll = 0;
         }
      }

      private void render(UiContext var1, int var2, int var3) {
         long var4 = RiptideMacroManager.get().getRevision();
         if (!this.query.equals(this.filterQuery) || var4 != this.filterMacroRevision) {
            this.visible = this.filteredMacros();
            this.filterQuery = this.query;
            this.filterMacroRevision = var4;
         }

         int var6 = Math.min(Math.max(this.anchor.width(), 196), Math.max(120, var1.screenWidth() - 8));
         int var7 = Math.max(1, Math.min(8, Math.max(this.visible.size(), 1)));
         int var8 = var7 * 18;
         int var9 = 38 + var8 + 2;
         int var10 = VanillaModuleMenuController.clamp(this.anchor.x(), 4, Math.max(4, var1.screenWidth() - var6 - 4));
         int var11 = this.anchor.bottom() + 2;
         int var12 = this.anchor.y() - var9 - 2;
         int var13 = var11 + var9 > var1.screenHeight() - 4 && var12 >= 4 ? var12 : var11;
         var13 = VanillaModuleMenuController.clamp(var13, 4, Math.max(4, var1.screenHeight() - var9 - 4));
         this.menuBounds = UiBounds.of(var10, var13, var6, var9);
         this.searchBounds = UiBounds.of(var10 + 2, var13 + 2, var6 - 4, 18);
         int var14 = (var6 - 6) / 2;
         this.clearBounds = UiBounds.of(var10 + 2, this.searchBounds.bottom(), var14, 18);
         this.createBounds = UiBounds.of(this.clearBounds.right() + 2, this.searchBounds.bottom(), var6 - 4 - var14 - 2, 18);
         this.listBounds = UiBounds.of(var10 + 2, this.createBounds.bottom(), var6 - 4, var8);
         UiRenderer.frame(
            var1.graphics(),
            this.menuBounds,
            VanillaModuleMenuController.this.theme.colors().windowStrong,
            VanillaModuleMenuController.this.theme.colors().border
         );
         TextField.render(var1, this.searchBounds, this.query, "Search macros...", true, this.query.length());
         Button.render(var1, this.clearBounds, "Clear", Button.Tone.NORMAL, this.clearBounds.contains(var2, var3), false);
         Button.render(
            var1, this.createBounds, this.isConditionalPicker() ? "Custom" : "Create New", Button.Tone.SUCCESS, this.createBounds.contains(var2, var3), false
         );
         int var15 = Math.max(0, this.visible.size() - var7);
         this.scroll = VanillaModuleMenuController.clamp(this.scroll, 0, var15);
         if (this.visible.isEmpty()) {
            var1.text().drawCentered(var1.graphics(), "No saved macros", this.listBounds, VanillaModuleMenuController.this.theme.colors().muted);
         } else {
            int var16 = this.visible.size() > var7 ? this.listBounds.width() - 6 - 2 : this.listBounds.width();

            for (int var17 = 0; var17 < var7; var17++) {
               int var18 = this.scroll + var17;
               if (var18 >= this.visible.size()) {
                  break;
               }

               String var19 = this.visible.get(var18);
               UiBounds var20 = UiBounds.of(this.listBounds.x(), this.listBounds.y() + var17 * 18, var16, 18);
               boolean var21 = var20.contains(var2, var3);
               boolean var22 = var19.equalsIgnoreCase(this.module.value(this.option.id()));
               boolean var23 = this.isPresetChoice(var19);
               UiRenderer.rect(
                  var1.graphics(),
                  var20,
                  var22
                     ? VanillaModuleMenuController.this.theme.colors().accentSoft
                     : (var21 ? VanillaModuleMenuController.this.theme.colors().rowHover : VanillaModuleMenuController.this.theme.colors().row)
               );
               var1.text()
                  .drawFitted(
                     var1.graphics(),
                     var19,
                     var20.x() + 4,
                     var1.text().centeredY(var20),
                     Math.max(1, var20.width() - 8),
                     var23 ? VanillaModuleMenuController.this.theme.colors().success : VanillaModuleMenuController.this.theme.colors().text
                  );
            }

            if (this.visible.size() > var7) {
               UiBounds var25 = UiBounds.of(this.listBounds.right() - 6, this.listBounds.y(), 6, this.listBounds.height());
               Scrollbar.Metrics var26 = Scrollbar.metrics(var25, this.visible.size() * 18, var7 * 18, this.scroll * 18);
               Scrollbar.render(var1, var26, false);
            }
         }
      }

      private boolean mouseClicked(int var1, int var2, int var3) {
         if (var3 != 0) {
            return this.menuBounds.contains(var1, var2);
         } else if (!this.menuBounds.contains(var1, var2)) {
            return false;
         } else if (this.clearBounds.contains(var1, var2)) {
            VanillaModuleMenuController.this.setOptionValue(this.module, this.option, "");
            VanillaModuleMenuController.this.invalidateSettings(this.module);
            VanillaModuleMenuController.this.clearMacroPicker();
            return true;
         } else if (this.createBounds.contains(var1, var2)) {
            VanillaModuleMenuController.this.clearMacroPicker();
            VanillaModuleMenuController.this.selectedModule = null;
            if (this.isConditionalPicker()) {
               RiptideMacro var7 = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.CUSTOM);
               if (var7 != null) {
                  VanillaModuleMenuController.this.setOptionValue(this.module, this.option, var7.name);
                  VanillaModuleMenuController.this.invalidateSettings(this.module);
               }

               VanillaModuleMenuController.this.host.openMacroCreator(this.module, this.option, var7);
            } else {
               VanillaModuleMenuController.this.host.openMacroCreator(this.module, this.option);
            }

            return true;
         } else if (this.listBounds.contains(var1, var2) && !this.visible.isEmpty()) {
            int var4 = this.scroll + Math.max(0, (var2 - this.listBounds.y()) / 18);
            if (var4 >= 0 && var4 < this.visible.size()) {
               String var5 = this.visible.get(var4);
               RiptideMacro var6 = this.createPresetChoice(var5);
               if (var6 != null) {
                  var5 = var6.name;
               }

               if (this.isConditionalPicker() && var6 == null && !MacroConditionUtil.startsWithWaitCondition(RiptideMacroManager.get().get(var5))) {
                  RiptideNotifications.warning("Auto stop rule must start with a conditional.");
               } else {
                  VanillaModuleMenuController.this.setOptionValue(this.module, this.option, var5);
                  VanillaModuleMenuController.this.invalidateSettings(this.module);
               }

               VanillaModuleMenuController.this.clearMacroPicker();
            }

            return true;
         } else {
            return true;
         }
      }

      private void mouseReleased(int var1, int var2, int var3) {
      }

      private void mouseDragged(int var1, int var2, int var3, double var4, double var6) {
      }

      private void mouseScrolled(int var1, int var2, double var3) {
         if (this.menuBounds.contains(var1, var2)) {
            int var5 = Math.max(1, Math.min(8, Math.max(this.visible.size(), 1)));
            this.scroll = VanillaModuleMenuController.clamp(this.scroll + (var3 < 0.0 ? 1 : -1), 0, Math.max(0, this.visible.size() - var5));
         }
      }

      private List<String> filteredMacros() {
         ArrayList var1 = new ArrayList();
         ArrayList var2 = new ArrayList();
         String var3 = this.query.trim().toLowerCase(Locale.ROOT);
         if (this.isConditionalPicker()) {
            this.addPresetIfVisible(var1, "Free Slots", var3);
            this.addPresetIfVisible(var1, "Durability", var3);
         }

         for (RiptideMacro var5 : RiptideMacroManager.get().getAll()) {
            if (var5 != null
               && var5.name != null
               && !var5.name.isBlank()
               && (!this.isConditionalPicker() || !this.isGeneratedAutoFishPresetName(var5.name))
               && (!this.isConditionalPicker() || MacroConditionUtil.startsWithWaitCondition(var5))
               && (var3.isEmpty() || var5.name.toLowerCase(Locale.ROOT).contains(var3))) {
               var2.add(var5.name);
            }
         }

         var2.sort(String.CASE_INSENSITIVE_ORDER);
         if (!var1.isEmpty()) {
            var2.addAll(0, var1);
         }

         return var2;
      }

      private boolean isConditionalPicker() {
         return this.option != null && this.option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER;
      }

      private boolean isPresetChoice(String var1) {
         return "Free Slots".equals(var1) || "Durability".equals(var1);
      }

      private boolean isGeneratedAutoFishPresetName(String var1) {
         return AutoFishStopMacroFactory.isGeneratedStopMacroName(var1);
      }

      private void addPresetIfVisible(List<String> var1, String var2, String var3) {
         if (var3 == null || var3.isBlank() || var2.toLowerCase(Locale.ROOT).contains(var3)) {
            var1.add(var2);
         }
      }

      private RiptideMacro createPresetChoice(String var1) {
         if ("Free Slots".equals(var1)) {
            return AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.FREE_SLOTS);
         } else {
            return "Durability".equals(var1) ? AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.DURABILITY) : null;
         }
      }
   }

   private record SearchMatch(Module module, ModuleCategory category, int index) {
   }

   private record SettingRow(String group, Setting<?, ?> option, int y, int height) {
      static VanillaModuleMenuController.SettingRow group(String var0, int var1, int var2) {
         return new VanillaModuleMenuController.SettingRow(var0, null, var1, var2);
      }

      static VanillaModuleMenuController.SettingRow option(Setting<?, ?> var0, int var1, int var2) {
         return new VanillaModuleMenuController.SettingRow(null, var0, var1, var2);
      }
   }

   private record UtilityAction(String id, String label, Identifier icon, Button.Tone tone, boolean toggle, boolean category) {
      static VanillaModuleMenuController.UtilityAction button(String var0, String var1, Identifier var2, Button.Tone var3) {
         return new VanillaModuleMenuController.UtilityAction(var0, var1, var2, var3, false, false);
      }

      static VanillaModuleMenuController.UtilityAction toggle(String var0, String var1, Identifier var2, Button.Tone var3) {
         return new VanillaModuleMenuController.UtilityAction(var0, var1, var2, var3, true, false);
      }

      static VanillaModuleMenuController.UtilityAction category(String var0, Identifier var1) {
         return new VanillaModuleMenuController.UtilityAction("", var0, var1, Button.Tone.NORMAL, false, true);
      }
   }
}
