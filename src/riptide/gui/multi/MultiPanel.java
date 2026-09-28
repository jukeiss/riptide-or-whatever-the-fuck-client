package riptide.gui.multi;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.ClickEvent.CopyToClipboard;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.Scrollbar;
import riptide.gui.vanillaui.components.Slider;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountSessionSwitcher;
import riptide.util.RiptideAccountType;
import riptide.util.RiptideConfig;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideTheme;
import riptide.util.multi.MultiAutoAccept;
import riptide.util.multi.MultiMacroDelay;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiPacketPolicy;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiProfileManager;
import riptide.util.multi.MultiQuickAction;
import riptide.util.multi.MultiSession;

public final class MultiPanel {
   private static final int CARD = 705827348;
   private static final int DISABLED = 403771667;
   private static final int BORDER_STOCK = -12174784;
   private static final int ACCENT_STOCK = -46518;
   private static final int SUCCESS_STOCK = -13248397;
   private static final int DANGER_STOCK = -42149;
   private static final int TEXT_STOCK = -855310;
   private static final int MUTED_STOCK = -6645094;
   private static final int WARN_STOCK = -14249;
   private static final int ROW = 20;
   private static final int DETAIL_ROW = 54;
   private static final int MACRO_SUB_H = 11;
   private static final int PROGRESS_ROW = 13;
   private static final int SELECTED_PINK = -39220;
   private static final int DISCONNECTED_RED = -42406;
   private static final int CONNECTING_YELLOW = -866997;
   private static final Identifier HEART_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
   private static final Identifier FOOD_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
   private static final int CHAT_LINE_HEIGHT = 11;
   private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
   private static final int[] PING_OPTIONS = new int[]{50, 100, 200, 300, 500, 1000};
   private static final List<String> PACING_OPTIONS = List.of("Gentle", "Balanced", "Fast", "Immediate", "Custom");
   private static final List<String> PROXY_MODE_OPTIONS = List.of("Off", "Auto", "Manual");
   private final Minecraft mc = Minecraft.getInstance();
   private final MultiPanel.Host host;
   private final Font font;
   private final CompactTheme theme = new CompactTheme();
   private final List<MultiPanel.Hotspot> hotspots = new ArrayList<>();
   private final List<CompactTextInput> activeInputs = new ArrayList<>();
   private final EnumSet<RiptideAccountType> accountFilters = EnumSet.allOf(RiptideAccountType.class);
   private final LinkedHashSet<String> selectedSessionIds = new LinkedHashSet<>();
   private final List<CompactDropdown> frameDropdowns = new ArrayList<>();
   private final CompactDropdown pacingDropdown;
   private final CompactDropdown proxyModeDropdown;
   private final CompactDropdown autoPingDropdown;
   private final CompactTextInput profileName = this.field("Profile name", 64);
   private final CompactTextInput serverAddress = this.field("server.example:25565", 160);
   private final CompactTextInput customConcurrency = this.field("Concurrency", 4);
   private final CompactTextInput customDelay = this.field("Delay ms", 5);
   private final CompactTextInput autoPing = this.field("Max ping", 4);
   private final CompactTextInput accountSearch = this.field("Search accounts", 48);
   private final CompactTextInput macroDelayField = this.field("0", 4).setFieldHeight(14).setHorizontalPadding(3).setFilter(MultiMacroDelay::typable);
   private final CompactTextInput chatInput = this.field("Chat or /command", 256);
   private final List<String> chatSuggests = new ArrayList<>();
   private final List<int[]> chatSuggestRects = new ArrayList<>();
   private int chatSuggestIndex;
   private String chatSuggestFor;
   private String chatSuggestApplied;
   private int chatSuggestStart;
   private int chatSuggestLen;
   private String chatLastReqCmd;
   private long chatLastReqAt;
   private final CompactTextInput macroSearch = this.field("Search macros", 48);
   private final CompactTextInput quickName = this.field("Action name", 32);
   private final CompactTextInput quickArgs = this.field("Packet args", 2048);
   private MultiPanel.Tab tab = MultiPanel.Tab.SETUP;
   private MultiProfile draft;
   private long lastAutoSaveCheckAt;
   private String lastCommittedState;
   private String selectedProfileId = "";
   private String selectedMacroName = "";
   private String pendingProfileDelete = "";
   private String pendingMacroDelete = "";
   private boolean assignPopupOpen;
   private final LinkedHashSet<String> assignSelected = new LinkedHashSet<>();
   private int assignScroll;
   private String status = "";
   private String macroRowTooltip;
   private int statusColor = -6645094;
   private long savedFlashAt;
   private int profileScroll;
   private int setupDetailScroll;
   private int accountScroll;
   private int sessionScroll;
   private boolean detailsOpen;
   private int chatScrollLines;
   private int macroScroll;
   private int macroProgressScroll;
   private String scrollbarDragId;
   private int scrollbarGrab;
   private int scrollbarTrackY;
   private int scrollbarTravel;
   private int scrollbarMax;
   private IntConsumer scrollbarSetter;
   private MultiPanel.SliderSetter sliderSetter;
   private Runnable sliderRelease;
   private int sliderTrackX;
   private int sliderTrackW;
   private int pressMx;
   private int quickEdit = -1;
   private int quickStep;
   private MultiQuickAction quickDraft;
   private float delta;
   private int lastMx;
   private int lastMy;
   private int bx;
   private int by;
   private int bw;
   private int bh;
   private int contentTop;
   private int contentBottom;
   private MultiPanel.Viewport profileViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport setupDetailViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport accountViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport sessionViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport chatViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport macroViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport macroProgressViewport = MultiPanel.Viewport.NONE;
   private MultiPanel.Viewport macroDelayViewport = MultiPanel.Viewport.NONE;
   private boolean macroDelayWasFocused;
   private boolean syncingMacroDelay;
   private MultiPanel.Viewport interactionClip = MultiPanel.Viewport.NONE;
   private final List<MultiPanel.ChatHitRow> chatHitRows = new ArrayList<>();
   private final List<MultiPanel.RowHit> sessionRowHits = new ArrayList<>();
   private String contextMenuId = "";
   private int contextMenuX;
   private int contextMenuY;
   private final List<int[]> contextItemRects = new ArrayList<>();
   private final MultiChatSelection chatSel = new MultiChatSelection();
   private boolean chatSelectingPress;
   private List<MultiSession.Snapshot> frameSnapshots = List.of();
   private MultiProfile frameProfile;
   private int frameReadyCount;
   private final Set<String> frameLiveIds = new HashSet<>(1000);
   private long cachedProfilesRevision = Long.MIN_VALUE;
   private List<MultiProfile> cachedProfiles = List.of();
   private long cachedActiveUiRevision = Long.MIN_VALUE;
   private long cachedActiveSessionRevision = Long.MIN_VALUE;
   private MultiProfile cachedActiveProfile;
   private long cachedChatRevision = Long.MIN_VALUE;
   private String cachedChatScope = "";
   private int cachedChatWidth = -1;
   private int cachedChatTotal = -1;
   private List<MultiChatPresentation.VisualRow> cachedChatRows = List.of();
   private int chatMessageX;
   private int chatMessageRight;
   private int chatWrapWidth;
   private long cachedMacroRevision = Long.MIN_VALUE;
   private String cachedMacroQuery = "";
   private List<RiptideMacro> cachedMacros = List.of();
   private int cachedAccountSourceSize = -1;
   private long cachedAccountSourceRevision = Long.MIN_VALUE;
   private List<RiptideAccount> cachedAccountSource = List.of();
   private String cachedAccountChoiceQuery = "";
   private int cachedAccountChoiceFilterMask = Integer.MIN_VALUE;
   private MultiProfile cachedAccountChoiceProfile;
   private int cachedAccountChoiceSessionCount = -1;
   private long cachedAccountChoiceUiRevision = Long.MIN_VALUE;
   private List<MultiPanel.AccountChoice> cachedAccountChoices = List.of();
   private long cachedCompatibilityRevision = Long.MIN_VALUE;
   private String cachedCompatibilityName = "";
   private List<String> cachedCompatibility = List.of();
   private static final int ASSIGN_ROW_H = 16;

   public MultiPanel(MultiPanel.Host host, Font font) {
      this.host = host;
      this.font = font;
      this.pacingDropdown = new CompactDropdown(0, 0, 1, 1, PACING_OPTIONS, 0, this::setPacing);
      this.proxyModeDropdown = new CompactDropdown(0, 0, 1, 1, PROXY_MODE_OPTIONS, 0, this::setProxyMode);
      this.autoPingDropdown = new CompactDropdown(0, 0, 1, 1, pingOptionLabels(), 1, this::setAutoPing);
      this.chatInput.setOnSubmit(this::sendChat);
      this.macroDelayField.setOnChange(this::typeMacroDelay);
      this.macroDelayField.setSubmitOnEnter(true);
      this.macroDelayField.setOnSubmit(text -> {
         this.typeMacroDelay(text);
         MultiMacroDelay.persist();
         this.macroDelayField.setFocused(false);
      });
      this.chatInput.setHistoryNavigationEnabled(true);
      this.chatInput.setHistoryProvider(() -> MultiManager.get().commandHistory());
      this.loadInitialDraft();
   }

   private CompactTextInput field(String placeholder, int maxLength) {
      return new CompactTextInput().setPlaceholder(placeholder).setMaxLength(maxLength).setFieldHeight(16);
   }

   public void opened() {
      this.cachedAccountSourceSize = -1;
      this.cachedAccountChoiceProfile = null;
      if (!MultiManager.get().isActive()) {
         MultiProfile shared = MultiProfileManager.get().find(MultiProfileManager.get().selectedId());
         if (shared != null && !shared.toTag().toString().equals(this.draft.toTag().toString())) {
            this.loadProfile(shared);
         }
      }

      if (!MultiManager.get().isActive() && this.draft.serverAddress.isBlank()) {
         this.useCurrentServer();
      }

      if (!MultiManager.get().isActive() && this.draft.sessions.isEmpty()) {
         this.selectFirstEligibleAccount();
      }
   }

   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float partial) {
      this.refreshTheme();
      this.autoSaveTick();
      this.hotspots.clear();
      this.activeInputs.clear();
      this.macroRowTooltip = null;
      boolean dropdownOpen = CompactDropdown.isMenuOpen(this.frameDropdowns);
      this.frameDropdowns.clear();
      this.delta = partial;
      int contentMx = dropdownOpen ? Integer.MIN_VALUE : mx;
      int contentMy = dropdownOpen ? Integer.MIN_VALUE : my;
      this.lastMx = contentMx;
      this.lastMy = contentMy;
      this.bx = x;
      this.by = y;
      this.bw = w;
      this.bh = h;
      this.contentTop = y + 25;
      this.contentBottom = y + h - 5;
      this.profileViewport = this.setupDetailViewport = this.accountViewport = this.sessionViewport = this.chatViewport = MultiPanel.Viewport.NONE;
      this.macroViewport = this.macroProgressViewport = this.macroDelayViewport = this.interactionClip = MultiPanel.Viewport.NONE;
      MultiManager manager = MultiManager.get();
      if (manager.isActive()) {
         this.frameSnapshots = manager.snapshots();
         this.frameProfile = this.tab == MultiPanel.Tab.CONSOLE ? null : this.activeProfileSnapshot(manager);
         this.frameLiveIds.clear();
         this.frameReadyCount = 0;

         for (MultiSession.Snapshot snapshot : this.frameSnapshots) {
            this.frameLiveIds.add(snapshot.accountId());
            if (snapshot.ready()) {
               this.frameReadyCount++;
            }
         }

         this.selectedSessionIds.retainAll(this.frameLiveIds);
      } else {
         this.frameSnapshots = List.of();
         this.frameProfile = null;
         this.frameReadyCount = 0;
         this.selectedSessionIds.clear();
         this.chatScrollLines = 0;
      }

      this.renderTabs(g, x + 5, y + 4, w - 10, contentMx, contentMy);
      switch (this.tab) {
         case SETUP:
            this.renderSetup(g, contentMx, contentMy);
            break;
         case ACCOUNTS:
            this.renderAccounts(g, contentMx, contentMy);
            break;
         case CONSOLE:
            this.renderConsole(g, contentMx, contentMy);
            break;
         case ACTIONS:
            this.renderActions(g, contentMx, contentMy);
            break;
         case MACROS:
            this.renderMacros(g, contentMx, contentMy);
      }

      if (this.quickEdit >= 0) {
         this.renderQuickEditor(g, contentMx, contentMy);
      } else if (!this.pendingMacroDelete.isBlank()) {
         this.renderMacroDelete(g, contentMx, contentMy);
      } else if (this.assignPopupOpen) {
         this.renderAssignPopup(g, contentMx, contentMy);
      }

      CompactDropdown.renderOpenMenu(g, this.font, this.frameDropdowns, mx, my);
      if (this.macroRowTooltip != null && RiptideConfig.getGlobal().multiShowTooltips) {
         MultiTooltip.render(g, this.font, this.macroRowTooltip, mx, my);
      }
   }

   private void renderTabs(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
      MultiPanel.Tab[] tabs = MultiPanel.Tab.values();
      String[] labels = new String[]{"Setup", "Accounts", "Console", "Actions", "Macros"};
      int gap = 3;
      int each = (w - gap * (tabs.length - 1)) / tabs.length;
      int cx = x;

      for (int i = 0; i < tabs.length; i++) {
         int cw = i == tabs.length - 1 ? x + w - cx : each;
         MultiPanel.Tab target = tabs[i];
         this.button(g, cx, y, cw, 17, labels[i], this.tab == target ? this.success() : this.border(), this.text(), mx, my, () -> {
            this.tab = target;
            this.clearFocus();
         });
         cx += cw + gap;
      }
   }

   private void renderSetup(GuiGraphicsExtractor g, int mx, int my) {
      boolean active = MultiManager.get().isActive();
      int pad = 6;
      int leftW = Math.max(105, Math.min(142, this.bw / 3));
      int lx = this.bx + pad;
      int rx = lx + leftW + 7;
      int rw = this.bx + this.bw - pad - rx;
      int top = this.contentTop;
      this.draw(g, "Saved Profiles", lx, top, this.text(), false, leftW);
      int listTop = top + 12;
      int listBottom = this.contentBottom - 42;
      this.profileViewport = new MultiPanel.Viewport(lx, listTop, leftW, Math.max(1, listBottom - listTop));
      List<MultiProfile> profiles = this.savedProfiles();
      int maxScroll = Math.max(0, profiles.size() * 20 - this.profileViewport.h());
      this.profileScroll = clamp(this.profileScroll, 0, maxScroll);
      UiScissorStack.global().push(g, this.profileViewport.bounds());
      this.interactionClip = this.profileViewport;
      int py = listTop - this.profileScroll;

      for (MultiProfile profile : profiles) {
         if (py + 20 > listTop && py < listBottom) {
            boolean selected = profile.id.equals(this.selectedProfileId);
            this.row(
               g, lx, py, leftW, 18, profile.name, selected ? this.success() : this.border(), mx, my, active ? null : () -> this.selectProfileRow(profile)
            );
         }

         py += 20;
      }

      this.interactionClip = MultiPanel.Viewport.NONE;
      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.profileViewport, this.profileScroll, maxScroll, mx, my, "profile", v -> this.profileScroll = v);
      this.button(g, lx, this.contentBottom - 36, leftW, 16, "New", this.border(), this.text(), mx, my, active ? null : this::newProfile);
      this.button(g, lx, this.contentBottom - 17, leftW, 16, "Duplicate", this.border(), this.text(), mx, my, active ? null : this::duplicateProfile);
      this.draw(g, active ? "Active Batch" : "Profile Setup", rx, top, active ? this.success() : this.text(), false, rw);
      if (!active && System.currentTimeMillis() - this.savedFlashAt < 1500L) {
         this.draw(g, "Saved", rx + rw - 34, top, this.success(), false, 34);
      }

      int detailTop = top + 13;
      this.setupDetailViewport = new MultiPanel.Viewport(rx, detailTop, rw, Math.max(1, this.contentBottom - detailTop));
      int detailHeight = this.setupDetailContentHeight(active);
      this.setupDetailScroll = clamp(this.setupDetailScroll, 0, Math.max(0, detailHeight - this.setupDetailViewport.h()));
      UiScissorStack.global().push(g, this.setupDetailViewport.bounds());
      this.interactionClip = this.setupDetailViewport;
      int cy = detailTop - this.setupDetailScroll;
      if (active) {
         MultiProfile current = this.frameProfile;
         this.draw(g, current == null ? "Multi" : current.name, rx, cy, this.text(), false, rw);
         cy += 13;
         this.draw(g, current == null ? "" : current.serverAddress, rx, cy, this.muted(), false, rw);
         cy += 18;
         this.draw(g, this.frameReadyCount + "/" + this.frameSnapshots.size() + " ready", rx, cy, this.success(), false, rw);
         cy += 22;
         this.button(g, rx, cy, rw, 18, "Disconnect All", this.danger(), this.text(), mx, my, () -> {
            MultiManager.get().disconnectAll("Disconnected by user");
            this.status("Disconnected", -6645094);
         });
         cy += 24;
         this.draw(g, "Disconnect to edit config.", rx, cy, this.muted(), false, rw);
         cy += 12;
      } else {
         this.place(g, this.profileName, rx, cy, rw);
         cy += 20;
         this.place(g, this.serverAddress, rx, cy, Math.max(10, rw - 82));
         this.button(g, rx + rw - 78, cy, 78, 16, "Use Current", this.border(), this.text(), mx, my, this::useCurrentServer);
         cy += 21;
         int half = (rw - 4) / 2;
         if (this.draft.proxyMode == MultiProfile.ProxyMode.Auto) {
            int third = Math.max(1, (rw - 8) / 3);
            this.configureSetupDropdown(this.pacingDropdown, rx, cy, third, this.draft.pacing.ordinal(), "Join speed: " + this.draft.pacing.name());
            this.configureSetupDropdown(this.proxyModeDropdown, rx + third + 4, cy, third, this.draft.proxyMode.ordinal(), "Proxy: " + this.proxyModeLabel());
            this.configureSetupDropdown(
               this.autoPingDropdown,
               rx + (third + 4) * 2,
               cy,
               rw - (third + 4) * 2,
               pingIndex(this.draft.autoMaxPingMs),
               "<=" + this.draft.autoMaxPingMs + "ms"
            );
         } else {
            this.configureSetupDropdown(this.pacingDropdown, rx, cy, half, this.draft.pacing.ordinal(), "Join speed: " + this.draft.pacing.name());
            this.configureSetupDropdown(
               this.proxyModeDropdown, rx + half + 4, cy, rw - half - 4, this.draft.proxyMode.ordinal(), "Proxy: " + this.proxyModeLabel()
            );
         }

         cy += 21;
         CompactDropdown.renderButtons(g, this.font, this.frameDropdowns, mx, my);
         this.button(g, rx, cy, rw, 17, "Passwords (login)", this.border(), this.text(), mx, my, this::editFormValues);
         cy += 21;
         if (this.draft.pacing == MultiProfile.Pacing.Custom) {
            this.place(g, this.customConcurrency, rx, cy, half);
            this.place(g, this.customDelay, rx + half + 4, cy, rw - half - 4);
            cy += 21;
         }

         this.draw(g, this.draft.sessions.size() + " accounts selected", rx, cy + 3, this.draft.sessions.isEmpty() ? this.danger() : this.muted(), false, rw);
         cy += 20;
         int halfBtn = (rw - 4) / 2;
         this.button(g, rx, cy, halfBtn, 17, "Reset", this.border(), this.text(), mx, my, this::resetDraft);
         this.button(
            g,
            rx + halfBtn + 4,
            cy,
            rw - halfBtn - 4,
            17,
            this.pendingProfileDelete.equals(this.selectedProfileId) ? "Confirm" : "Delete",
            this.danger(),
            this.text(),
            mx,
            my,
            this::deleteProfile
         );
         cy += 22;
         this.button(g, rx, cy, rw, 19, "Launch Multi", this.success(), this.text(), mx, my, this::launch);
         cy += 22;
      }

      if (!this.status.isBlank()) {
         this.draw(g, this.status, rx, cy, this.themedStatus(), false, rw);
      }

      this.interactionClip = MultiPanel.Viewport.NONE;
      UiScissorStack.global().pop(g);
      this.scrollbar(
         g,
         this.setupDetailViewport,
         this.setupDetailScroll,
         Math.max(0, detailHeight - this.setupDetailViewport.h()),
         mx,
         my,
         "setupDetail",
         v -> this.setupDetailScroll = v
      );
   }

   private int setupDetailContentHeight(boolean active) {
      if (active) {
         return 89 + (this.status.isBlank() ? 0 : 12);
      } else {
         int height = 168;
         if (this.draft.pacing == MultiProfile.Pacing.Custom) {
            height += 21;
         }

         if (!this.status.isBlank()) {
            height += 12;
         }

         return height;
      }
   }

   private void renderAccounts(GuiGraphicsExtractor g, int mx, int my) {
      boolean active = MultiManager.get().isActive();
      MultiProfile shownProfile = active && this.frameProfile != null ? this.frameProfile : this.draft;
      int x = this.bx + 6;
      int w = this.bw - 12;
      int y = this.contentTop;
      this.place(g, this.accountSearch, x, y, Math.max(10, w - 102));
      this.button(g, x + w - 98, y, 98, 16, "Manage Accounts", this.border(), this.text(), mx, my, this.host::manageAccounts);
      y += 20;
      RiptideAccountType[] types = new RiptideAccountType[]{
         RiptideAccountType.Cracked, RiptideAccountType.Session, RiptideAccountType.Microsoft, RiptideAccountType.TheAltening, RiptideAccountType.Generated
      };
      String[] labels = new String[]{"Cracked", "Session", "Microsoft", "Altening", "Generated"};
      int gap = 3;
      int each = (w - gap * 4) / 5;
      int cx = x;

      for (int i = 0; i < types.length; i++) {
         RiptideAccountType type = types[i];
         this.toggleButton(g, cx, y, i == types.length - 1 ? x + w - cx : each, 16, labels[i], this.accountFilters.contains(type), mx, my, () -> {
            if (!this.accountFilters.remove(type)) {
               this.accountFilters.add(type);
            }

            this.accountScroll = 0;
         });
         cx += each + gap;
      }

      y += 20;
      if (!active && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual) {
         String commonProxy = commonManualProxyId(shownProfile);
         Map<String, Integer> profileUsage = manualProxyUsage(shownProfile);
         String bulkLabel = commonProxy == null ? "Set All Proxies (Mixed)..." : "Set All Proxies: " + this.proxyLabel(shownProfile, commonProxy);
         this.button(
            g,
            x,
            y,
            w,
            16,
            bulkLabel,
            this.border(),
            this.text(),
            mx,
            my,
            () -> this.host.pickManualProxy("Proxy for all selected accounts", shownProfile.serverAddress, commonProxy, profileUsage, this::setAllProxies)
         );
         y += 20;
      }

      int listH = Math.max(20, this.contentBottom - y);
      this.accountViewport = new MultiPanel.Viewport(x, y, w, listH);
      List<MultiPanel.AccountChoice> choices = this.filteredAccounts(shownProfile);
      int maxScroll = Math.max(0, choices.size() * 20 - listH);
      this.accountScroll = clamp(this.accountScroll, 0, maxScroll);
      UiScissorStack.global().push(g, this.accountViewport.bounds());
      this.interactionClip = this.accountViewport;
      int ry = y - this.accountScroll;
      Map<String, Integer> profileUsage = !active && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual ? manualProxyUsage(shownProfile) : Map.of();

      for (MultiPanel.AccountChoice choice : choices) {
         if (ry + 20 > y && ry < y + listH) {
            MultiProfile.SessionSpec spec = selectedSpec(shownProfile, choice.id());
            boolean blocked = choice.current();
            boolean selected = spec != null;
            int proxyW = Math.min(116, Math.max(76, w / 3));
            int accountW = w - proxyW - 4;
            String label = choice.label() + (blocked ? " (Current)" : "");
            this.row(
               g,
               x,
               ry,
               accountW,
               18,
               label,
               blocked ? this.danger() : (selected ? this.success() : this.border()),
               mx,
               my,
               !active && !blocked ? () -> this.toggleAccount(choice.id()) : null
            );
            String proxy = spec == null ? "-" : this.proxyLabel(shownProfile, spec.proxyId());
            boolean pickable = !active && spec != null && !choice.current() && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual;
            if (pickable) {
               this.button(
                  g,
                  x + accountW + 4,
                  ry,
                  proxyW,
                  18,
                  proxy,
                  this.border(),
                  this.text(),
                  mx,
                  my,
                  () -> this.host
                     .pickManualProxy(
                        "Proxy for " + choice.label(),
                        shownProfile.serverAddress,
                        spec.proxyId(),
                        profileUsage,
                        proxyId -> this.setAccountProxy(choice.id(), proxyId)
                     )
               );
            } else {
               this.row(g, x + accountW + 4, ry, proxyW, 18, proxy, this.border(), mx, my, null);
            }
         }

         ry += 20;
      }

      this.interactionClip = MultiPanel.Viewport.NONE;
      CompactDropdown.renderButtons(g, this.font, this.frameDropdowns, mx, my);
      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.accountViewport, this.accountScroll, maxScroll, mx, my, "account", v -> this.accountScroll = v);
   }

   private void renderConsole(GuiGraphicsExtractor g, int mx, int my) {
      int x = this.bx + 6;
      int y = this.contentTop;
      int w = this.bw - 12;
      int h = this.contentBottom - y;
      if (!MultiManager.get().isActive()) {
         this.emptyState(g, "No active batch", "Launch a profile from Setup.", x, y, w, h, mx, my, () -> this.tab = MultiPanel.Tab.SETUP);
      } else {
         List<MultiSession.Snapshot> snapshots = this.frameSnapshots;
         boolean twoPane = w >= 410;
         int listW = twoPane ? Math.max(128, Math.min(158, w / 3)) : w;
         int listH = twoPane ? h : Math.min(76, Math.max(40, h / 3));
         this.renderSessionList(g, snapshots, x, y, listW, listH, mx, my);
         int chatX = twoPane ? x + listW + 6 : x;
         int chatY = twoPane ? y : y + listH + 6;
         int chatW = twoPane ? w - listW - 6 : w;
         int chatH = twoPane ? h : h - listH - 6;
         this.renderChat(g, chatX, chatY, chatW, chatH, mx, my);
         this.renderContextMenu(g, mx, my);
      }
   }

   private void renderContextMenu(GuiGraphicsExtractor g, int mx, int my) {
      this.contextItemRects.clear();
      if (!this.contextMenuId.isBlank()) {
         String[] items = new String[]{"TPA to Me", "TPA to Bot", "Trade"};
         int itemH = 14;
         int menuW = 84;
         int menuH = items.length * itemH + 2;
         int menuX = Math.min(this.contextMenuX, this.bx + this.bw - menuW - 2);
         int menuY = Math.min(this.contextMenuY, this.by + this.bh - menuH - 2);
         UiRenderer.frame(g, UiBounds.of(menuX, menuY, menuW, menuH), -267251178, this.accent());

         for (int i = 0; i < items.length; i++) {
            int ry = menuY + 1 + i * itemH;
            boolean hover = mx >= menuX && mx < menuX + menuW && my >= ry && my < ry + itemH;
            if (hover) {
               UiRenderer.rect(g, UiBounds.of(menuX + 1, ry, menuW - 2, itemH), 872415231);
            }

            this.draw(g, items[i], menuX + 5, ry + 3, hover ? this.text() : this.muted(), false, menuW - 8);
            this.contextItemRects.add(new int[]{menuX, ry, menuW, itemH, i});
         }
      }
   }

   private void runContextAction(int index) {
      String id = this.contextMenuId;
      this.contextMenuId = "";
      if (!id.isBlank()) {
         MultiManager mm = MultiManager.get();

         String result = switch (index) {
            case 0 -> mm.tpaToMe(id);
            case 1 -> mm.tpaToBot(id);
            case 2 -> mm.tradeWith(id);
            default -> "";
         };
         if (!result.isBlank()) {
            this.status(result, "Sent".equals(result) ? -13248397 : -6645094);
         }
      }
   }

   private void renderSessionList(GuiGraphicsExtractor g, List<MultiSession.Snapshot> snapshots, int x, int y, int w, int h, int mx, int my) {
      this.draw(g, "Sessions " + this.frameReadyCount + "/" + snapshots.size(), x, y, this.text(), false, w - 60);
      this.toggleButton(g, x + w - 58, y - 1, 58, 11, "Details", this.detailsOpen, mx, my, () -> {
         this.detailsOpen = !this.detailsOpen;
         this.sessionScroll = 0;
      });
      this.toggleButton(g, x, y + 12, w, 16, "Shared GUI", this.host.isSharedGuiOpen(), mx, my, this.host::openSharedGui);
      int pitch = this.detailsOpen ? 54 : 20;
      int top = y + 31;
      int footer = y + h - 19;
      int listH = Math.max(16, footer - top);
      this.sessionViewport = new MultiPanel.Viewport(x, top, w, listH);
      int totalH = 0;

      for (MultiSession.Snapshot snapshot : snapshots) {
         totalH += pitch + 11 + guiExtra(snapshot);
      }

      int maxScroll = Math.max(0, totalH - listH);
      this.sessionScroll = clamp(this.sessionScroll, 0, maxScroll);
      UiScissorStack.global().push(g, this.sessionViewport.bounds());
      this.interactionClip = this.sessionViewport;
      this.sessionRowHits.clear();
      int ry = top - this.sessionScroll;

      for (MultiSession.Snapshot snapshot : snapshots) {
         int guiExtra = guiExtra(snapshot);
         int frameH = pitch + 11 + guiExtra - 2;
         if (ry + frameH > top && ry < top + listH) {
            int state = this.sessionColor(snapshot);
            boolean selected = this.selectedSessionIds.contains(snapshot.accountId());
            UiRenderer.frame(g, UiBounds.of(x, ry, w, frameH), selected ? tint(-39220, 53) : 705827348, selected ? -39220 : state);
            this.hotspots.add(new MultiPanel.Hotspot(x, ry, w, frameH, () -> this.selectSession(snapshot.accountId())));
            if (this.interactionAllowed(x, ry, w, frameH)) {
               this.sessionRowHits.add(new MultiPanel.RowHit(x, ry, w, frameH, snapshot.accountId()));
            }

            int guiW = 29;
            int guiX = x + w - guiW - 3;
            int povW = 29;
            boolean povActive = this.host.isTakeoverActive(snapshot.accountId());
            boolean povOk = povActive || this.host.canTakeover(snapshot.accountId());
            int povX = povOk ? guiX - povW - 3 : guiX;
            String ping = snapshot.ping() >= 0 ? snapshot.ping() + "ms" : "--ms";
            int pingW = this.font.width(ping) + 5;
            int pingX = (povOk ? povX : guiX) - pingW - 2;
            int nameColor = state == -42406 ? -42406 : this.text();
            this.draw(g, snapshot.accountName(), x + 5, ry + 5, nameColor, false, Math.max(20, pingX - x - 7));
            this.draw(g, ping, pingX, ry + 5, snapshot.ping() >= 0 ? this.muted() : this.border(), false, pingW);
            if (povOk) {
               this.toggleButton(g, povX, ry + 2, povW, 14, "POV", povActive, mx, my, () -> this.host.toggleTakeover(snapshot.accountId()));
            }

            this.toggleButton(
               g, guiX, ry + 2, guiW, 14, "GUI", this.host.isGuiOpen(snapshot.accountId()), mx, my, () -> this.host.openGui(snapshot.accountId())
            );
            int macroY = ry + 17;
            this.drawMacroRow(g, snapshot, x + 5, macroY, w - 10);
            if (mx >= x && mx < x + w && my >= macroY - 1 && my < macroY + 11) {
               this.macroRowTooltip = MultiMacroPresentation.tooltip(MultiManager.get(), snapshot);
            }

            if (guiExtra > 0) {
               String gui = snapshot.openScreen();
               String guiLabel = snapshot.customMenuOpen() ? "GUI: CustomScreen" : "GUI: " + MultiManager.singleLine(gui == null ? "" : gui, 40);
               this.draw(g, guiLabel, x + 5, ry + 17 + 11, this.muted(), false, w - 10);
            }

            if (this.detailsOpen) {
               this.drawSessionDetails(g, snapshot, x + 6, ry + 17 + 11 + guiExtra, w - 12);
            }
         }

         ry += pitch + 11 + guiExtra;
      }

      this.interactionClip = MultiPanel.Viewport.NONE;
      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.sessionViewport, this.sessionScroll, maxScroll, mx, my, "session", v -> this.sessionScroll = v);
      int half = (w - 3) / 2;
      this.button(g, x, footer, half, 17, "Retry", this.border(), this.text(), mx, my, this::retrySelected);
      this.button(g, x + half + 3, footer, w - half - 3, 17, "Stop", this.danger(), this.text(), mx, my, this::stopSelectedSessions);
   }

   private static int guiExtra(MultiSession.Snapshot snapshot) {
      String gui = snapshot.openScreen();
      return !snapshot.customMenuOpen() && (gui == null || gui.isBlank()) ? 0 : 11;
   }

   private void drawMacroRow(GuiGraphicsExtractor g, MultiSession.Snapshot snapshot, int x, int y, int width) {
      String assignedName = MultiMacroPresentation.assignedName(MultiManager.get(), snapshot);
      String assigned = MultiMacroPresentation.assignedLabel(assignedName);
      long now = System.currentTimeMillis();
      String trailing = MultiMacroPresentation.queuedLabel(snapshot, now);
      int trailingColor = -1526710;
      if (trailing.isBlank()) {
         trailing = MultiMacroPresentation.playingLabel(MultiMacroPresentation.playingName(snapshot));
         trailingColor = -11013497;
      }

      if (trailing.isBlank()) {
         this.draw(g, assigned, x, y, -6641998, false, width);
      } else {
         int gap = 7;
         int trailingW = Math.min(Math.max(42, width * 3 / 5), Math.max(1, this.font.width(trailing)));
         int assignedW = Math.max(0, width - trailingW - gap);
         int cx = x;
         if (assignedW > 12) {
            this.draw(g, assigned, x, y, -6641998, false, assignedW);
            cx = x + assignedW + gap;
         }

         this.draw(g, trailing, cx, y, trailingColor, false, Math.max(1, x + width - cx));
      }
   }

   private void drawSessionDetails(GuiGraphicsExtractor g, MultiSession.Snapshot snapshot, int x, int y, int w) {
      g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_SPRITE, x, y - 1, 9, 9);
      int cx = x + 11;
      String hp = trimStat(snapshot.health()) + "/" + trimStat(snapshot.maxHealth());
      this.draw(g, hp, cx, y, this.muted(), false, w);
      cx += this.font.width(hp) + 10;
      g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_SPRITE, cx, y - 1, 9, 9);
      cx += 11;
      this.draw(g, snapshot.food() + "/20", cx, y, this.muted(), false, Math.max(1, x + w - cx));
      String held = snapshot.heldItem() != null && !snapshot.heldItem().isBlank() ? snapshot.heldItem() : "empty";
      this.draw(g, "Held " + held + "  (slot " + snapshot.hotbarSlot() + ")", x, y + 11, this.muted(), false, w);
      String dim = snapshot.dimension() != null && !snapshot.dimension().isBlank() ? snapshot.dimension() : "?";
      this.draw(g, "World: " + dim, x, y + 22, this.muted(), false, w);
   }

   private static String trimStat(float value) {
      return value == Math.rint(value) ? Integer.toString((int)value) : String.format(Locale.ROOT, "%.1f", value);
   }

   private void renderChat(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
      String scope = this.selectedSessionIds.isEmpty() ? "All" : this.selectedSessionIds.size() + " selected";
      this.draw(g, "Console - " + scope, x, y, this.text(), false, w);
      int inputY = y + h - 17;
      int sendW = 44;
      this.place(g, this.chatInput, x, inputY, Math.max(50, w - sendW - 4));
      this.button(g, x + w - sendW, inputY, sendW, 16, "Send", this.success(), this.text(), mx, my, () -> this.sendChat(this.chatInput.text()));
      int top = y + 13;
      int chatH = Math.max(10, inputY - top - 3);
      this.chatViewport = new MultiPanel.Viewport(x, top, w, chatH);
      UiRenderer.frame(g, UiBounds.of(x, top, w, chatH), 806359058, this.border());
      this.chatHitRows.clear();
      int textX = x + 4;
      int timestampWidth = this.font.width("[00:00:00] ");
      this.chatMessageX = textX + timestampWidth;
      this.chatMessageRight = x + w - 4;
      this.chatWrapWidth = Math.max(1, this.chatMessageRight - this.chatMessageX);
      long chatRevision = MultiManager.get().chatRevision();
      String requestedScope = this.selectedSessionIds.isEmpty() ? "*" : String.join("|", this.selectedSessionIds);
      int total = this.currentChatTotal();
      if (chatRevision != this.cachedChatRevision
         || !requestedScope.equals(this.cachedChatScope)
         || this.chatWrapWidth != this.cachedChatWidth
         || total != this.cachedChatTotal) {
         boolean scopeChanged = !requestedScope.equals(this.cachedChatScope);
         int previousRows = this.cachedChatRows.size();
         boolean preserveScroll = this.chatScrollLines > 0
            && requestedScope.equals(this.cachedChatScope)
            && this.chatWrapWidth == this.cachedChatWidth
            && total == this.cachedChatTotal;
         if (scopeChanged) {
            this.chatScrollLines = 0;
         }

         this.cachedChatRevision = chatRevision;
         this.cachedChatScope = requestedScope;
         this.cachedChatWidth = this.chatWrapWidth;
         this.cachedChatTotal = total;
         this.cachedChatRows = MultiChatPresentation.wrap(
            this.font, MultiManager.get().chatView(this.selectedSessionIds), this.chatWrapWidth, total, this::chatAccountLabel, this.muted()
         );
         if (preserveScroll && this.cachedChatRows.size() > previousRows) {
            this.chatScrollLines = this.chatScrollLines + (this.cachedChatRows.size() - previousRows);
         }
      }

      List<MultiChatPresentation.VisualRow> rows = this.cachedChatRows;
      List<MultiChatSelection.Row> selRows = new ArrayList<>(rows.size());

      for (MultiChatPresentation.VisualRow row : rows) {
         selRows.add(new MultiChatSelection.Row(row.line().seq(), row.lineIndex(), MultiChatSelection.plain(row.hit())));
      }

      this.chatSel.setRows(selRows);
      int visible = Math.max(1, (chatH - 4) / 11);
      int chatMaxScroll = Math.max(0, rows.size() - visible);
      this.chatScrollLines = clamp(this.chatScrollLines, 0, chatMaxScroll);
      int end = rows.size() - this.chatScrollLines;
      int start = Math.max(0, end - visible);
      UiScissorStack.global().push(g, UiBounds.of(x + 2, top + 2, Math.max(1, w - 4), Math.max(1, chatH - 4)));
      int cy = top + 3;

      for (int i = start; i < end; i++) {
         MultiChatPresentation.VisualRow row = rows.get(i);
         String rowText = MultiChatSelection.plain(row.hit());
         int[] range = this.chatSel.rangeFor(row.line().seq(), row.lineIndex(), rowText.length());
         if (range != null && range[1] > range[0]) {
            int sx = this.chatMessageX + MultiChatSelection.widthOfStyled(this.font, row.hit(), range[0]);
            int ex = this.chatMessageX + MultiChatSelection.widthOfStyled(this.font, row.hit(), range[1]);
            UiRenderer.rect(g, UiBounds.of(sx, cy - 1, Math.max(1, ex - sx), 11), 1430023925);
         }

         if (row.lineIndex() == 0) {
            g.text(this.font, Component.literal(timestamp(row.line().time())), textX, cy, this.muted(), false);
         }

         g.text(this.font, row.render(), this.chatMessageX, cy, this.text(), false);
         MultiChatPresentation.underlineClickableLine(g, this.font, row.hit(), this.chatMessageX, cy);
         this.chatHitRows.add(new MultiPanel.ChatHitRow(row.line(), row.lineIndex(), cy, row.hit(), rowText));
         cy += 11;
      }

      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.chatViewport, this.chatScrollLines, chatMaxScroll, mx, my, "chat", v -> this.chatScrollLines = v);
      this.refreshChatSuggestions();
      this.chatSuggestRects.clear();
      boolean freshSuggests = !this.chatSuggests.isEmpty()
         && this.chatInput.isFocused()
         && (this.chatInput.text().equals(this.chatSuggestFor) || this.chatInput.text().equals(this.chatSuggestApplied));
      if (freshSuggests) {
         int rowH = 11;
         int shown = Math.min(6, this.chatSuggests.size());
         int popH = shown * rowH + 2;
         int popY = inputY - popH - 1;
         UiRenderer.frame(g, UiBounds.of(x, popY, w, popH), -267251178, this.border());
         int first = clamp(this.chatSuggestIndex - shown + 1, 0, Math.max(0, this.chatSuggests.size() - shown));

         for (int i = 0; i < shown; i++) {
            int idx = first + i;
            int ry = popY + 1 + i * rowH;
            boolean sel = idx == this.chatSuggestIndex;
            if (sel) {
               UiRenderer.frame(g, UiBounds.of(x + 1, ry, w - 2, rowH), 1090519039, 0);
            }

            this.draw(g, this.chatSuggests.get(idx), x + 3, ry + 1, sel ? this.text() : this.muted(), false, w - 6);
            this.chatSuggestRects.add(new int[]{x, ry, w, rowH, idx});
         }
      }
   }

   private boolean handleChatClick(int mouseX, int mouseY) {
      if (this.chatViewport.hit(mouseX, mouseY) && mouseX >= this.chatMessageX && mouseX <= this.chatMessageRight) {
         for (MultiPanel.ChatHitRow row : this.chatHitRows) {
            if (mouseY >= row.y() && mouseY < row.y() + 11) {
               int relativeX = mouseX - this.chatMessageX;
               ClickEvent click = MultiChatPresentation.resolveClick(this.font, row.hit(), relativeX);
               if (click == null) {
                  return false;
               }

               this.executeChatClick(row.line(), row.lineIndex(), relativeX, click);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean beginChatSelection(int mouseX, int mouseY) {
      if (this.chatViewport.hit(mouseX, mouseY) && !this.chatHitRows.isEmpty()) {
         this.chatSelectingPress = true;
         this.chatSelectAt(mouseX, mouseY, true);
         return true;
      } else {
         this.chatSel.clear();
         return false;
      }
   }

   private void chatSelectAt(int mouseX, int mouseY, boolean begin) {
      if (!this.chatHitRows.isEmpty()) {
         MultiPanel.ChatHitRow target = this.chatHitRows.getFirst();

         for (MultiPanel.ChatHitRow row : this.chatHitRows) {
            if (mouseY >= row.y()) {
               target = row;
            }
         }

         int ch = MultiChatSelection.charIndexAtStyled(this.font, target.hit(), mouseX - this.chatMessageX);
         if (begin) {
            this.chatSel.begin(target.line().seq(), target.lineIndex(), ch);
         } else {
            this.chatSel.extend(target.line().seq(), target.lineIndex(), ch);
         }
      }
   }

   private void copyChatSelection() {
      String selected = this.chatSel.selectedText();
      if (!selected.isBlank() && this.mc != null && this.mc.keyboardHandler != null) {
         this.mc.keyboardHandler.setClipboard(selected);
         this.status("Copied " + selected.length() + " chars", -13248397);
      }
   }

   private void executeChatClick(MultiManager.ChatLine line, int lineIndex, int relativeX, ClickEvent click) {
      switch (click) {
         case RunCommand command:
            int ran = MultiChatPresentation.runCommands(
               this.font, line, lineIndex, relativeX, this.chatWrapWidth, this.currentChatTotal(), this::chatAccountLabel, this.muted(), command
            );
            this.status(ran > 0 ? "Clicked " + ran : "Clicked", -13248397);
            break;
         case SuggestCommand suggest:
            this.chatInput.setText(suggest.command());
            this.chatInput.setFocused(true);
            break;
         case CopyToClipboard copy:
            this.mc.keyboardHandler.setClipboard(copy.value());
            this.status("Copied", -6645094);
            break;
         case OpenUrl url:
            MultiChatPresentation.openLinkSafely(url.uri());
            this.status("Opening link", -13248397);
            break;
         default:
      }
   }

   private int currentChatTotal() {
      return this.selectedSessionIds.isEmpty() ? Math.max(1, this.frameSnapshots.size()) : this.selectedSessionIds.size();
   }

   private String chatAccountLabel(String accountId) {
      for (MultiSession.Snapshot snapshot : this.frameSnapshots) {
         if (snapshot.accountId().equals(accountId)) {
            return snapshot.accountName();
         }
      }

      return accountId;
   }

   private static String timestamp(long time) {
      return "[" + LocalTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).format(CHAT_TIME) + "] ";
   }

   private void renderActions(GuiGraphicsExtractor g, int mx, int my) {
      int x = this.bx + 6;
      int y = this.contentTop;
      int w = this.bw - 12;
      boolean active = MultiManager.get().isActive();
      MultiProfile profile = active ? this.frameProfile : this.draft;
      if (profile != null) {
         String scope = this.selectedSessionIds.isEmpty() ? "All sessions" : this.selectedSessionIds.size() + " selected";
         this.draw(g, "Quick Actions - " + scope, x, y, this.text(), false, w);
         y += 13;
         int count = 5;
         int gap = 3;
         int each = (w - gap * (count - 1)) / count;
         this.button(
            g,
            x,
            y,
            each,
            18,
            "Move",
            this.success(),
            this.text(),
            mx,
            my,
            MultiManager.get().isActive() ? () -> this.result(MultiManager.get().broadcastMovementNow(this.selectedSessionIds)) : null
         );
         int cx = x + each + gap;

         for (int i = 0; i < 4; i++) {
            MultiQuickAction action = profile.quickAction(i);
            int cw = i == 3 ? x + w - cx : each;
            this.button(g, cx, y, cw, 18, action.label(i), action.empty() ? this.border() : this.accent(), this.text(), mx, my, () -> {
               if (active && !action.empty()) {
                  this.result(MultiManager.get().broadcastQuickAction(action, this.selectedSessionIds));
               } else {
                  this.openQuickEditor(i);
               }
            });
            cx += cw + gap;
         }

         y += 22;
         int slotW = (w - gap * 3) / 4;
         cx = x;

         for (int i = 0; i < 4; i++) {
            int index = i;
            int cw = i == 3 ? x + w - cx : slotW;
            this.button(g, cx, y, cw, 16, "Edit " + (i + 1), this.border(), this.text(), mx, my, () -> this.openQuickEditor(index));
            cx += cw + gap;
         }

         y += 21;
         int third = (w - 6) / 3;
         this.button(
            g,
            x,
            y,
            third,
            17,
            "Use",
            this.border(),
            this.text(),
            mx,
            my,
            MultiManager.get().isActive() ? () -> this.result(MultiManager.get().useOnScope(this.selectedSessionIds)) : null
         );
         this.button(
            g,
            x + third + 3,
            y,
            third,
            17,
            "Close GUI",
            this.border(),
            this.text(),
            mx,
            my,
            MultiManager.get().isActive() ? () -> this.result(MultiManager.get().closeOnScope(this.selectedSessionIds)) : null
         );
         String guiAccount = this.selectedSessionIds.size() == 1 ? this.selectedSessionIds.iterator().next() : "";
         this.toggleButton(
            g,
            x + (third + 3) * 2,
            y,
            w - (third + 3) * 2,
            17,
            "GUI",
            this.host.isGuiOpen(guiAccount),
            mx,
            my,
            this.selectedSessionIds.size() == 1 ? this::openSelectedGui : null
         );
         y += 22;
         int half = (w - 4) / 2;
         MultiPacketPolicy policy = profile.packetPolicy;
         this.button(
            g,
            x,
            y,
            half,
            17,
            "Gravity: " + (policy.gravity() ? "On" : "Off"),
            policy.gravity() ? this.success() : this.border(),
            this.text(),
            mx,
            my,
            () -> this.updatePolicy(p -> p.setGravity(!p.gravity()))
         );
         this.button(
            g,
            x + half + 4,
            y,
            w - half - 4,
            17,
            "Look: " + (policy.autoLook() ? "On" : "Off"),
            policy.autoLook() ? this.success() : this.border(),
            this.text(),
            mx,
            my,
            () -> this.updatePolicy(p -> p.setAutoLook(!p.autoLook()))
         );
         y += 21;
         this.button(
            g,
            x,
            y,
            half,
            17,
            "Swing: " + (policy.autoSwing() ? "On" : "Off"),
            policy.autoSwing() ? this.success() : this.border(),
            this.text(),
            mx,
            my,
            () -> this.updatePolicy(p -> p.setAutoSwing(!p.autoSwing()))
         );
         this.button(
            g,
            x + half + 4,
            y,
            (w - half - 8) / 2,
            17,
            "Block C2S",
            this.danger(),
            this.text(),
            mx,
            my,
            () -> this.openBlocklist(MultiPacketPolicy.Direction.C2S)
         );
         this.button(
            g,
            x + half + 4 + (w - half - 8) / 2 + 4,
            y,
            w - (half + 4 + (w - half - 8) / 2 + 4),
            17,
            "Block S2C",
            this.danger(),
            this.text(),
            mx,
            my,
            () -> this.openBlocklist(MultiPacketPolicy.Direction.S2C)
         );
         y += 22;
         this.button(g, x, y, half, 17, "Reset Actions", this.border(), this.text(), mx, my, this::resetQuickActions);
         this.button(
            g,
            x + half + 4,
            y,
            w - half - 4,
            17,
            "Clear Blocklist",
            this.border(),
            this.text(),
            mx,
            my,
            () -> this.updatePolicy(p -> p.setBlocklist(List.of()))
         );
         y += 22;
         this.button(g, x, y, w, 17, "Auto-Accept: TPA & Trade", this.accent(), this.text(), mx, my, this::openAutoAccept);
         y += 22;
         if (!this.status.isBlank()) {
            this.draw(g, this.status, x, y, this.themedStatus(), false, w);
         }
      }
   }

   private void renderMacros(GuiGraphicsExtractor g, int mx, int my) {
      int x = this.bx + 6;
      int y = this.contentTop;
      int w = this.bw - 12;
      int h = this.contentBottom - y;
      int listW = Math.max(130, Math.min(180, w / 3));
      this.place(g, this.macroSearch, x, y, listW);
      y += 20;
      List<RiptideMacro> macros = this.filteredMacros();
      int listH = Math.max(30, h - 20);
      this.macroViewport = new MultiPanel.Viewport(x, y, listW, listH);
      int maxScroll = Math.max(0, macros.size() * 20 - listH);
      this.macroScroll = clamp(this.macroScroll, 0, maxScroll);
      UiScissorStack.global().push(g, this.macroViewport.bounds());
      this.interactionClip = this.macroViewport;
      int ry = y - this.macroScroll;

      for (RiptideMacro macro : macros) {
         if (ry + 20 > y && ry < y + listH) {
            boolean selected = macro.name.equals(this.selectedMacroName);
            this.row(g, x, ry, listW, 18, macro.name, selected ? this.success() : this.border(), mx, my, () -> {
               this.selectedMacroName = macro.name;
               this.pendingMacroDelete = "";
            });
         }

         ry += 20;
      }

      this.interactionClip = MultiPanel.Viewport.NONE;
      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.macroViewport, this.macroScroll, maxScroll, mx, my, "macro", v -> this.macroScroll = v);
      int dx = x + listW + 7;
      int dw = w - listW - 7;
      int cy = this.contentTop;
      String scope = this.selectedSessionIds.isEmpty() ? "All sessions" : this.selectedSessionIds.size() + " selected";
      this.draw(g, scope, dx, cy, this.muted(), false, dw);
      cy += 13;
      this.draw(g, this.selectedMacroName.isBlank() ? "No macro selected" : this.selectedMacroName, dx, cy, this.text(), false, dw);
      cy += 16;
      int third = (dw - 6) / 3;
      this.button(g, dx, cy, third, 17, "Assign", this.success(), this.text(), mx, my, this.selectedMacroName.isBlank() ? null : this::assignMacro);
      this.button(g, dx + third + 3, cy, third, 17, "Run", this.success(), this.text(), mx, my, MultiManager.get().isActive() ? this::runMacros : null, true);
      this.button(
         g,
         dx + (third + 3) * 2,
         cy,
         dw - (third + 3) * 2,
         17,
         "Stop",
         this.danger(),
         this.text(),
         mx,
         my,
         MultiManager.get().isActive() ? () -> this.result(MultiManager.get().stopMacroOnInteractiveScope(this.selectedSessionIds)) : null
      );
      cy += 21;
      this.button(g, dx, cy, third, 17, "New", this.border(), this.text(), mx, my, () -> this.editMacro(null));
      this.button(
         g,
         dx + third + 3,
         cy,
         third,
         17,
         "Edit",
         this.border(),
         this.text(),
         mx,
         my,
         this.selectedMacroName.isBlank() ? null : () -> this.editMacro(RiptideMacroManager.get().get(this.selectedMacroName))
      );
      this.button(
         g,
         dx + (third + 3) * 2,
         cy,
         dw - (third + 3) * 2,
         17,
         "Delete",
         this.danger(),
         this.text(),
         mx,
         my,
         this.selectedMacroName.isBlank() ? null : () -> this.pendingMacroDelete = this.selectedMacroName
      );
      cy += 21;
      this.button(g, dx, cy, dw, 17, "Clear Assignment", this.border(), this.text(), mx, my, this::clearMacroAssignment);
      cy += 21;
      int delayMs = MultiMacroDelay.currentMs();
      this.macroDelayViewport = new MultiPanel.Viewport(dx, cy, dw, 14);
      int unitW = this.font.width("s") + 2;
      int fieldW = Math.min(34, dw - unitW - 46);
      boolean typedBox = fieldW >= 18;
      int sliderW = typedBox ? dw - unitW - fieldW - 4 : dw;
      this.slider(
         g,
         dx,
         cy,
         sliderW,
         14,
         "Delay between bots",
         MultiMacroDelay.ratio(delayMs),
         mx,
         my,
         (mouseX, trackX, trackW) -> MultiMacroDelay.setMs(MultiMacroDelay.fromMouse(mouseX, trackX, trackW)),
         MultiMacroDelay::persist
      );
      if (typedBox) {
         this.syncMacroDelayField(delayMs);
         this.place(g, this.macroDelayField, dx + sliderW + 4, cy, fieldW, 14);
         this.draw(g, "s", dx + sliderW + 4 + fieldW + 2, cy + 3, this.muted(), false, unitW);
      } else {
         this.macroDelayField.setFocused(false);
      }

      boolean delayFocused = this.macroDelayField.isFocused();
      if (this.macroDelayWasFocused && !delayFocused) {
         MultiMacroDelay.persist();
      }

      this.macroDelayWasFocused = delayFocused;
      cy += 19;
      List<String> compatibility = this.macroCompatibility(this.selectedMacroName);
      if (compatibility.isEmpty()) {
         this.draw(g, this.selectedMacroName.isBlank() ? "Select a macro." : "Fully compatible.", dx, cy, this.muted(), false, dw);
         cy += 15;
      } else {
         int warnBottom = cy + Math.min(72, Math.max(20, (this.contentBottom - 24 - cy) / 2));
         int shown = 0;
         boolean truncated = false;

         for (String warning : compatibility) {
            List<FormattedCharSequence> lines = this.font.split(FormattedText.of(warning), Math.max(40, dw - 4));
            if (cy + lines.size() * 10 > warnBottom) {
               truncated = true;
               break;
            }

            for (FormattedCharSequence line : lines) {
               g.text(this.font, line, dx, cy, this.warn(), false);
               cy += 10;
            }

            shown++;
         }

         if (truncated) {
            this.draw(g, "+" + (compatibility.size() - shown) + " more warning(s) - shown in console chat on Run", dx, cy, this.muted(), false, dw);
            cy += 10;
         }

         cy += 5;
      }

      this.draw(g, "Progress", dx, cy, this.text(), false, dw);
      cy += 12;
      int progressBottom = this.contentBottom - 12;
      this.macroProgressViewport = new MultiPanel.Viewport(dx, cy, dw, Math.max(1, progressBottom - cy));
      int progressCount = 0;

      for (MultiSession.Snapshot snapshot : this.frameSnapshots) {
         if (this.selectedSessionIds.isEmpty() || this.selectedSessionIds.contains(snapshot.accountId())) {
            progressCount++;
         }
      }

      int progressMaxScroll = Math.max(0, progressCount * 13 - this.macroProgressViewport.h());
      this.macroProgressScroll = clamp(this.macroProgressScroll, 0, progressMaxScroll);
      UiScissorStack.global().push(g, this.macroProgressViewport.bounds());
      cy -= this.macroProgressScroll;
      long now = System.currentTimeMillis();

      for (MultiSession.Snapshot snapshotx : this.frameSnapshots) {
         if (this.selectedSessionIds.isEmpty() || this.selectedSessionIds.contains(snapshotx.accountId())) {
            this.drawMacroProgressRow(g, snapshotx, dx, cy, dw, now);
            cy += 13;
         }
      }

      UiScissorStack.global().pop(g);
      this.scrollbar(g, this.macroProgressViewport, this.macroProgressScroll, progressMaxScroll, mx, my, "macroProgress", v -> this.macroProgressScroll = v);
      if (!this.status.isBlank()) {
         this.draw(g, this.status, dx, this.contentBottom - 10, this.themedStatus(), false, dw);
      }
   }

   private void renderQuickEditor(GuiGraphicsExtractor g, int mx, int my) {
      this.hotspots.clear();
      this.activeInputs.clear();
      UiRenderer.rect(g, UiBounds.of(this.bx, this.by, this.bw, this.bh), -1342177280);
      int mw = Math.min(330, this.bw - 24);
      int mh = Math.min(184, this.bh - 24);
      int x = this.bx + (this.bw - mw) / 2;
      int y = this.by + (this.bh - mh) / 2;
      UiRenderer.frame(g, UiBounds.of(x, y, mw, mh), -267251180, this.accent());
      this.draw(g, "Edit Action " + (this.quickEdit + 1), x + 8, y + 7, this.text(), false, mw - 16);
      this.place(g, this.quickName, x + 8, y + 22, mw - 16);
      this.ensureQuickStep();
      String packet = this.quickDraft.steps.get(this.quickStep).packetClass();
      this.draw(g, "Packet " + (this.quickStep + 1) + "/" + this.quickDraft.steps.size(), x + 8, y + 44, this.muted(), false, mw - 90);
      this.button(g, x + mw - 76, y + 42, 68, 17, "Choose", this.border(), this.text(), mx, my, this::pickQuickPacket);
      this.draw(g, packet.isBlank() ? "No packet" : MultiQuickAction.shortLabel(packet), x + 8, y + 57, this.text(), false, mw - 16);
      this.place(g, this.quickArgs, x + 8, y + 70, mw - 16);
      int q = (mw - 22) / 4;
      int by1 = y + 91;
      this.button(g, x + 8, by1, q, 16, "Prev", this.border(), this.text(), mx, my, this.quickStep > 0 ? () -> this.changeQuickStep(-1) : null);
      this.button(
         g,
         x + 10 + q,
         by1,
         q,
         16,
         "Next",
         this.border(),
         this.text(),
         mx,
         my,
         this.quickStep + 1 < this.quickDraft.steps.size() ? () -> this.changeQuickStep(1) : null
      );
      this.button(g, x + 12 + q * 2, by1, q, 16, "Add", this.border(), this.text(), mx, my, this.quickDraft.steps.size() < 6 ? this::addQuickStep : null);
      this.button(g, x + 14 + q * 3, by1, mw - 22 - q * 3, 16, "Remove", this.danger(), this.text(), mx, my, this::removeQuickStep);
      int half = (mw - 20) / 2;
      int by2 = y + 113;
      this.button(g, x + 8, by2, half, 17, "Test Send", this.accent(), this.text(), mx, my, this::testQuickDraft);
      this.button(g, x + 12 + half, by2, mw - 20 - half, 17, "Clear", this.danger(), this.text(), mx, my, this::clearQuickEditor);
      int third = (mw - 22) / 3;
      int by3 = y + mh - 24;
      this.button(g, x + 8, by3, third, 17, "Save", this.success(), this.text(), mx, my, this::saveQuickEditor);
      this.button(g, x + 11 + third, by3, third, 17, "Reset All", this.border(), this.text(), mx, my, this::resetQuickActions);
      this.button(g, x + 14 + third * 2, by3, mw - 22 - third * 2, 17, "Cancel", this.border(), this.text(), mx, my, this::closeQuickEditor);
   }

   private void renderMacroDelete(GuiGraphicsExtractor g, int mx, int my) {
      this.hotspots.clear();
      this.activeInputs.clear();
      UiRenderer.rect(g, UiBounds.of(this.bx, this.by, this.bw, this.bh), -1342177280);
      int w = Math.min(270, this.bw - 30);
      int h = 76;
      int x = this.bx + (this.bw - w) / 2;
      int y = this.by + (this.bh - h) / 2;
      UiRenderer.frame(g, UiBounds.of(x, y, w, h), -267251180, this.danger());
      this.draw(g, "Delete " + this.pendingMacroDelete + "?", x + 8, y + 10, this.text(), false, w - 16);
      int half = (w - 20) / 2;
      this.button(g, x + 8, y + 46, half, 18, "Delete", this.danger(), this.text(), mx, my, this::confirmMacroDelete);
      this.button(g, x + 12 + half, y + 46, w - 20 - half, 18, "Cancel", this.border(), this.text(), mx, my, () -> this.pendingMacroDelete = "");
   }

   private void loadInitialDraft() {
      List<MultiProfile> profiles = MultiProfileManager.get().all();
      if (profiles.isEmpty()) {
         this.newProfile();
      } else {
         MultiProfile shared = MultiProfileManager.get().find(MultiProfileManager.get().selectedId());
         this.loadProfile(shared != null ? shared : profiles.getFirst());
      }
   }

   private void newProfile() {
      this.draft = new MultiProfile();
      this.draft.name = MultiProfileManager.get().nextAvailableName("New profile", this.draft.id);
      this.draft.serverAddress = this.currentServer();
      this.selectFirstEligibleAccount();
      MultiProfileManager.get().put(this.draft);
      this.selectedProfileId = this.draft.id;
      MultiProfileManager.get().setSelectedId(this.draft.id);
      this.syncFieldsFromDraft();
      this.pendingProfileDelete = "";
      this.lastCommittedState = this.draft.toTag().toString();
   }

   private void resetDraft() {
      String server = this.currentServer();
      this.newProfile();
      this.draft.serverAddress = server;
      this.syncFieldsFromDraft();
      this.status("Defaults restored", -6645094);
   }

   private void loadProfile(MultiProfile profile) {
      if (profile != null) {
         this.draft = new MultiProfile(profile);
         this.selectedProfileId = this.draft.id;
         MultiProfileManager.get().setSelectedId(this.draft.id);
         this.setupDetailScroll = 0;
         this.syncFieldsFromDraft();
         this.pendingProfileDelete = "";
         this.lastCommittedState = this.draft.toTag().toString();
         this.status("Loaded", -6645094);
      }
   }

   private void selectProfileRow(MultiProfile profile) {
      if (profile != null) {
         if (!profile.id.equals(this.selectedProfileId)) {
            this.loadProfile(profile);
         }
      }
   }

   private void commit() {
      if (this.draft != null) {
         this.captureDraftFields();
         this.draft.normalize();
         MultiProfileManager.get().put(this.draft);
         this.selectedProfileId = this.draft.id;
         MultiProfileManager.get().setSelectedId(this.draft.id);
         String serialized = this.draft.toTag().toString();
         if (!serialized.equals(this.lastCommittedState)) {
            this.savedFlashAt = System.currentTimeMillis();
         }

         this.lastCommittedState = serialized;
      }
   }

   private boolean editingProfileText() {
      return this.profileName.isFocused()
         || this.serverAddress.isFocused()
         || this.customConcurrency.isFocused()
         || this.customDelay.isFocused()
         || this.autoPing.isFocused();
   }

   private void autoSaveTick() {
      long now = System.currentTimeMillis();
      if (now - this.lastAutoSaveCheckAt >= 400L) {
         this.lastAutoSaveCheckAt = now;
         if (this.draft != null && !this.editingProfileText()) {
            this.captureDraftFields();
            String state = this.draft.toTag().toString();
            if (this.lastCommittedState == null) {
               this.lastCommittedState = state;
            } else if (!state.equals(this.lastCommittedState)) {
               this.commit();
            }
         }
      }
   }

   public void flushPendingEdit() {
      if (this.draft != null) {
         this.captureDraftFields();
         String state = this.draft.toTag().toString();
         if (this.lastCommittedState == null) {
            this.lastCommittedState = state;
         } else if (!state.equals(this.lastCommittedState)) {
            this.commit();
         }
      }
   }

   private void duplicateProfile() {
      this.captureDraftFields();
      MultiProfile copy = new MultiProfile(this.draft);
      copy.id = UUID.randomUUID().toString();
      copy.name = MultiProfileManager.get().nextAvailableName(copy.name, copy.id);
      this.draft = copy;
      this.syncFieldsFromDraft();
      this.commit();
   }

   private void deleteProfile() {
      if (this.selectedProfileId.isBlank()) {
         this.status("Unsaved profile", -6645094);
      } else if (!this.pendingProfileDelete.equals(this.selectedProfileId)) {
         this.pendingProfileDelete = this.selectedProfileId;
         this.status("Press Confirm", -14249);
      } else {
         MultiProfileManager.get().remove(this.selectedProfileId);
         this.pendingProfileDelete = "";
         List<MultiProfile> left = MultiProfileManager.get().all();
         if (left.isEmpty()) {
            this.newProfile();
         } else {
            this.loadProfile(left.getFirst());
         }

         this.status("Deleted", -6645094);
      }
   }

   private void launch() {
      this.captureDraftFields();
      this.draft.normalize();
      this.draft.name = MultiProfileManager.get().nextAvailableName(this.draft.name, this.draft.id);
      this.profileName.setText(this.draft.name);
      MultiManager.StartResult result = MultiManager.get().start(this.draft);
      if (!result.ok()) {
         this.status(result.message(), -42149);
      } else {
         MultiProfileManager.get().put(this.draft);
         this.selectedProfileId = this.draft.id;
         this.tab = MultiPanel.Tab.CONSOLE;
         this.status("Launching", -13248397);
      }
   }

   private void setPacing(int index) {
      if (index >= 0 && index < MultiProfile.Pacing.values().length) {
         this.draft.pacing = MultiProfile.Pacing.values()[index];
      }
   }

   private void setProxyMode(int index) {
      if (index >= 0 && index < MultiProfile.ProxyMode.values().length) {
         this.draft.proxyMode = MultiProfile.ProxyMode.values()[index];
      }
   }

   private void openAutoAccept() {
      boolean active = MultiManager.get().isActive();
      MultiProfile profile = active ? MultiManager.get().activeProfile() : this.draft;
      if (profile != null) {
         this.host.editAutoAccept(profile.autoAccept, cfg -> {
            if (active) {
               MultiManager.get().updateAutoAccept(cfg);
            } else {
               this.draft.autoAccept = cfg;
               this.commit();
            }
         }, active);
      }
   }

   private void editFormValues() {
      Set<String> selected = (Set<String>)(this.selectedSessionIds.isEmpty()
         ? this.draft.sessions.stream().map(MultiProfile.SessionSpec::accountId).collect(Collectors.toCollection(LinkedHashSet::new))
         : new LinkedHashSet<>(this.selectedSessionIds));
      this.host.editFormValues(new MultiProfile(this.draft), selected, updated -> {
         this.draft = updated;
         this.syncFieldsFromDraft();
         this.commit();
         this.status("Passwords saved", -13248397);
      });
   }

   private void setAutoPing(int index) {
      if (index >= 0 && index < PING_OPTIONS.length) {
         this.draft.autoMaxPingMs = PING_OPTIONS[index];
         this.autoPing.setText(Integer.toString(this.draft.autoMaxPingMs));
      }
   }

   private void useCurrentServer() {
      this.draft.serverAddress = this.currentServer();
      this.serverAddress.setText(this.draft.serverAddress);
   }

   private void captureDraftFields() {
      this.draft.name = clean(this.profileName.text());
      this.draft.serverAddress = clean(this.serverAddress.text());
      this.draft.customConcurrency = parseInt(this.customConcurrency.text(), this.draft.customConcurrency);
      this.draft.customDelayMs = parseInt(this.customDelay.text(), this.draft.customDelayMs);
      this.draft.autoMaxPingMs = parseInt(this.autoPing.text(), this.draft.autoMaxPingMs);
   }

   private void syncFieldsFromDraft() {
      this.profileName.setText(this.draft.name);
      this.serverAddress.setText(this.draft.serverAddress);
      this.customConcurrency.setText(Integer.toString(this.draft.customConcurrency));
      this.customDelay.setText(Integer.toString(this.draft.customDelayMs));
      this.autoPing.setText(Integer.toString(this.draft.autoMaxPingMs));
   }

   private void selectFirstEligibleAccount() {
      if (this.draft != null && this.draft.sessions.isEmpty()) {
         if (!MultiManager.isCurrentRenderedAccount("default")) {
            this.draft.sessions.add(new MultiProfile.SessionSpec("default", ""));
         } else {
            for (RiptideAccount account : RiptideAccountManager.get().all()) {
               if (!MultiManager.isCurrentRenderedAccount(account.stableId())) {
                  this.draft.sessions.add(new MultiProfile.SessionSpec(account.stableId(), ""));
                  return;
               }
            }
         }
      }
   }

   private void toggleAccount(String id) {
      if (MultiManager.isCurrentRenderedAccount(id)) {
         this.status("Current account cannot join Multi", -42149);
      } else {
         MultiProfile.SessionSpec spec = this.selectedSpec(id);
         if (spec != null) {
            this.draft.sessions.remove(spec);
         } else if (this.draft.sessions.size() < 500) {
            this.draft.sessions.add(new MultiProfile.SessionSpec(id, ""));
         }
      }
   }

   private void setAccountProxy(String accountId, String proxyId) {
      MultiProfile.SessionSpec spec = this.selectedSpec(accountId);
      if (spec != null) {
         int index = this.draft.sessions.indexOf(spec);
         this.draft.sessions.set(index, new MultiProfile.SessionSpec(spec.accountId(), proxyId, spec.macroName()));
      }
   }

   private void setAllProxies(String proxyId) {
      String id = proxyId == null ? "" : proxyId;

      for (int i = 0; i < this.draft.sessions.size(); i++) {
         MultiProfile.SessionSpec spec = this.draft.sessions.get(i);
         this.draft.sessions.set(i, new MultiProfile.SessionSpec(spec.accountId(), id, spec.macroName()));
      }
   }

   private void selectSession(String id) {
      if (this.ctrlDown()) {
         if (!this.selectedSessionIds.remove(id)) {
            this.selectedSessionIds.add(id);
         }
      } else {
         if (this.shiftDown() && !this.selectedSessionIds.isEmpty()) {
            List<String> order = MultiManager.get().snapshots().stream().map(MultiSession.Snapshot::accountId).toList();
            int a = order.indexOf(this.selectedSessionIds.iterator().next());
            int b = order.indexOf(id);
            if (a >= 0 && b >= 0) {
               this.selectedSessionIds.clear();

               for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                  this.selectedSessionIds.add(order.get(i));
               }

               return;
            }
         }

         boolean only = this.selectedSessionIds.size() == 1 && this.selectedSessionIds.contains(id);
         this.selectedSessionIds.clear();
         if (!only) {
            this.selectedSessionIds.add(id);
         }
      }
   }

   private void openSelectedGui() {
      if (this.selectedSessionIds.size() != 1) {
         this.status("Select one session", -6645094);
      } else {
         this.host.openGui(this.selectedSessionIds.iterator().next());
      }
   }

   private void retrySelected() {
      List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();
      int retried = 0;

      for (MultiSession.Snapshot snapshot : snapshots) {
         if ((this.selectedSessionIds.isEmpty() || this.selectedSessionIds.contains(snapshot.accountId()))
            && MultiManager.isRetryable(snapshot.status())
            && MultiManager.get().retry(snapshot.accountId()).ok()) {
            retried++;
         }
      }

      this.status(retried > 0 ? "Retrying " + retried : "Nothing to retry", retried > 0 ? -13248397 : -6645094);
   }

   private void stopSelectedSessions() {
      for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
         if (this.selectedSessionIds.isEmpty() || this.selectedSessionIds.contains(snapshot.accountId())) {
            MultiManager.get().disconnectSession(snapshot.accountId());
         }
      }

      this.status("Stopped", -42149);
   }

   private void sendChat(String text) {
      String line = clean(text);
      if (!line.isBlank() && MultiManager.get().isActive()) {
         this.result(MultiManager.get().broadcastConsole(line, this.selectedSessionIds));
         MultiManager.get().pushHistory(line);
         this.chatInput.setText("");
         this.clearChatSuggests();
      }
   }

   private void openQuickEditor(int index) {
      MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : this.draft;
      if (profile != null) {
         this.quickEdit = index;
         this.quickStep = 0;
         this.quickDraft = profile.quickAction(index);
         if (this.quickDraft.steps.isEmpty()) {
            this.quickDraft.steps.add(new MultiQuickAction.Step("", ""));
         }

         this.quickName.setText(this.quickDraft.name);
         this.quickArgs.setText(this.quickDraft.steps.getFirst().arguments());
      }
   }

   private void ensureQuickStep() {
      if (this.quickDraft == null) {
         this.quickDraft = new MultiQuickAction();
      }

      if (this.quickDraft.steps.isEmpty()) {
         this.quickDraft.steps.add(new MultiQuickAction.Step("", ""));
      }

      this.quickStep = clamp(this.quickStep, 0, this.quickDraft.steps.size() - 1);
   }

   private void captureQuickFields() {
      this.ensureQuickStep();
      this.quickDraft.name = clean(this.quickName.text());
      MultiQuickAction.Step current = this.quickDraft.steps.get(this.quickStep);
      this.quickDraft.steps.set(this.quickStep, new MultiQuickAction.Step(current.packetClass(), this.quickArgs.text()));
   }

   private void changeQuickStep(int delta) {
      this.captureQuickFields();
      this.quickStep = clamp(this.quickStep + delta, 0, this.quickDraft.steps.size() - 1);
      this.quickArgs.setText(this.quickDraft.steps.get(this.quickStep).arguments());
   }

   private void addQuickStep() {
      this.captureQuickFields();
      if (this.quickDraft.steps.size() < 6) {
         this.quickDraft.steps.add(new MultiQuickAction.Step("", ""));
         this.quickStep = this.quickDraft.steps.size() - 1;
         this.quickArgs.setText("");
      }
   }

   private void removeQuickStep() {
      this.ensureQuickStep();
      this.quickDraft.steps.remove(this.quickStep);
      if (this.quickDraft.steps.isEmpty()) {
         this.quickDraft.steps.add(new MultiQuickAction.Step("", ""));
      }

      this.quickStep = clamp(this.quickStep, 0, this.quickDraft.steps.size() - 1);
      this.quickArgs.setText(this.quickDraft.steps.get(this.quickStep).arguments());
   }

   private void pickQuickPacket() {
      this.captureQuickFields();
      this.host.pickQuickPacket(packet -> {
         this.ensureQuickStep();
         MultiQuickAction.Step old = this.quickDraft.steps.get(this.quickStep);
         this.quickDraft.steps.set(this.quickStep, new MultiQuickAction.Step(packet.getName(), old.arguments()));
         if (this.quickDraft.name.isBlank()) {
            this.quickDraft.name = MultiQuickAction.shortLabel(packet.getName());
            this.quickName.setText(this.quickDraft.name);
         }
      });
   }

   private MultiQuickAction builtQuickDraft() {
      this.captureQuickFields();
      MultiQuickAction result = new MultiQuickAction(this.quickDraft);
      result.normalize();
      return result;
   }

   private void saveQuickEditor() {
      MultiQuickAction action = this.builtQuickDraft();
      if (MultiManager.get().isActive()) {
         MultiManager.get().updateQuickAction(this.quickEdit, action);
      } else {
         this.draft.setQuickAction(this.quickEdit, action);
      }

      this.status("Saved", -13248397);
      this.closeQuickEditor();
   }

   private void testQuickDraft() {
      if (!MultiManager.get().isActive()) {
         this.status("Launch first", -6645094);
      } else {
         this.result(MultiManager.get().broadcastQuickAction(this.builtQuickDraft(), this.selectedSessionIds));
      }
   }

   private void clearQuickEditor() {
      if (MultiManager.get().isActive()) {
         MultiManager.get().clearQuickAction(this.quickEdit);
      } else {
         this.draft.setQuickAction(this.quickEdit, new MultiQuickAction());
      }

      this.status("Cleared", -6645094);
      this.closeQuickEditor();
   }

   private void closeQuickEditor() {
      this.quickEdit = -1;
      this.quickDraft = null;
      this.quickStep = 0;
      this.quickName.setFocused(false);
      this.quickArgs.setFocused(false);
   }

   private void resetQuickActions() {
      if (MultiManager.get().isActive()) {
         MultiManager.get().resetQuickActions();
      } else {
         this.draft.resetQuickActions();
      }

      this.status("Actions reset", -6645094);
      this.closeQuickEditor();
   }

   private void updatePolicy(Consumer<MultiPacketPolicy> change) {
      MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : this.draft;
      if (profile != null) {
         MultiPacketPolicy policy = new MultiPacketPolicy(profile.packetPolicy);
         change.accept(policy);
         if (MultiManager.get().isActive()) {
            MultiManager.get().updatePolicy(policy);
         } else {
            this.draft.packetPolicy = policy;
         }
      }
   }

   private void openBlocklist(MultiPacketPolicy.Direction direction) {
      MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : this.draft;
      if (profile != null) {
         Set<Class<? extends Packet<?>>> selected = new LinkedHashSet<>();

         for (MultiPacketPolicy.Rule rule : profile.packetPolicy.blocklist()) {
            if (rule.direction() == direction) {
               Class<? extends Packet<?>> packet = RiptidePacketRegistry.getPacket(rule.packetClass());
               if (packet != null) {
                  selected.add(packet);
               }
            }
         }

         this.host.editBlocklist(direction, selected, (packetx, enabled) -> this.updatePolicy(policy -> {
            List<MultiPacketPolicy.Rule> rules = new ArrayList<>(policy.blocklist());
            rules.removeIf(rulex -> rulex.direction() == direction && rulex.packetClass().equals(packetx.getName()));
            if (enabled) {
               rules.add(new MultiPacketPolicy.Rule(direction, packetx.getName()));
            }

            policy.setBlocklist(rules);
         }));
      }
   }

   private void runMacros() {
      MultiManager m = MultiManager.get();
      if (!m.isActive()) {
         this.status("Connect first", -6645094);
      } else {
         if (m.hasAssignedMacroOnInteractiveScope(this.selectedSessionIds)) {
            this.result(m.runMacroOnInteractiveScope(this.selectedSessionIds));
         } else if (!this.selectedMacroName.isBlank()) {
            RiptideMacro macro = RiptideMacroManager.get().get(this.selectedMacroName);
            if (macro != null) {
               this.result(m.runMacroDirectInteractive(macro, this.selectedSessionIds));
            } else {
               this.status("Macro not found", -42149);
            }
         } else {
            this.status("Select or assign a macro first", -6645094);
         }
      }
   }

   private void assignMacro() {
      if (this.selectedMacroName.isBlank()) {
         this.status("Select a macro first", -6645094);
      } else {
         List<String[]> accounts = this.assignAccounts();
         if (accounts.isEmpty()) {
            this.status("No accounts", -6645094);
         } else if (accounts.size() == 1) {
            this.applyAssign(List.of(accounts.get(0)[0]));
            this.status("Assigned to " + accounts.get(0)[1], -13248397);
         } else {
            this.assignSelected.clear();
            this.assignSelected.addAll(this.selectedSessionIds);
            this.assignScroll = 0;
            this.assignPopupOpen = true;
         }
      }
   }

   private List<String[]> assignAccounts() {
      List<String[]> out = new ArrayList<>();
      if (MultiManager.get().isActive()) {
         for (MultiSession.Snapshot s : this.frameSnapshots) {
            out.add(new String[]{s.accountId(), s.accountName(), orEmpty(MultiManager.get().effectiveMacroName(s.accountId()))});
         }
      } else if (this.draft != null) {
         for (MultiProfile.SessionSpec spec : this.draft.sessions) {
            String m = spec.macroName().isBlank() ? this.draft.allMacroName : spec.macroName();
            out.add(new String[]{spec.accountId(), this.assignAccountName(spec.accountId()), orEmpty(m)});
         }
      }

      return out;
   }

   private static String orEmpty(String s) {
      return s == null ? "" : s;
   }

   private String assignAccountName(String id) {
      if ("default".equals(id)) {
         return "Current account";
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(id);
         return account == null ? id : clean(account.displayName());
      }
   }

   private void applyAssign(Collection<String> ids) {
      if (MultiManager.get().isActive()) {
         MultiManager.get().assignMacroOnScope(new LinkedHashSet<>(ids), this.selectedMacroName);
      } else if (this.draft != null) {
         for (int i = 0; i < this.draft.sessions.size(); i++) {
            MultiProfile.SessionSpec spec = this.draft.sessions.get(i);
            if (ids.contains(spec.accountId())) {
               this.draft.sessions.set(i, spec.withMacro(this.selectedMacroName));
            }
         }

         this.commit();
      }
   }

   private void renderAssignPopup(GuiGraphicsExtractor g, int mx, int my) {
      this.hotspots.clear();
      this.activeInputs.clear();
      UiRenderer.rect(g, UiBounds.of(this.bx, this.by, this.bw, this.bh), -1342177280);
      int w = Math.min(300, this.bw - 24);
      int h = Math.min(220, this.bh - 24);
      int x = this.bx + (this.bw - w) / 2;
      int y = this.by + (this.bh - h) / 2;
      UiRenderer.frame(g, UiBounds.of(x, y, w, h), -267251180, this.accent());
      this.draw(g, "Assign \"" + this.selectedMacroName + "\" to:", x + 8, y + 7, this.text(), false, w - 16);
      List<String[]> accounts = this.assignAccounts();
      int listTop = y + 22;
      int listBottom = y + h - 44;
      int listH = Math.max(16, listBottom - listTop);
      int maxScroll = Math.max(0, accounts.size() * 16 - listH);
      this.assignScroll = clamp(this.assignScroll, 0, maxScroll);
      UiScissorStack.global().push(g, UiBounds.of(x + 8, listTop, w - 16, listH));
      int ry = listTop - this.assignScroll;

      for (String[] acc : accounts) {
         if (ry + 16 > listTop && ry < listBottom) {
            boolean sel = this.assignSelected.contains(acc[0]);
            int outline = sel ? this.success() : this.border();
            UiRenderer.frame(g, UiBounds.of(x + 8, ry, w - 16, 14), sel ? tint(this.success(), 48) : 705827348, outline);
            String box = sel ? "[x] " : "[ ] ";
            String cur = acc[2].isBlank() ? "" : "  -> " + acc[2];
            this.draw(g, box + acc[1] + cur, x + 12, ry + 4, sel ? this.success() : this.text(), false, w - 24);
            String id = acc[0];
            this.hotspots.add(new MultiPanel.Hotspot(x + 8, ry, w - 16, 14, () -> {
               if (!this.assignSelected.remove(id)) {
                  this.assignSelected.add(id);
               }
            }));
         }

         ry += 16;
      }

      UiScissorStack.global().pop(g);
      int by1 = y + h - 40;
      this.button(g, x + 8, by1, w - 16, 15, "Select all without a macro", this.border(), this.text(), mx, my, () -> {
         this.assignSelected.clear();

         for (String[] acc : this.assignAccounts()) {
            if (acc[2].isBlank()) {
               this.assignSelected.add(acc[0]);
            }
         }
      });
      int by2 = y + h - 22;
      int half = (w - 20) / 2;
      this.button(g, x + 8, by2, half, 16, "Save (" + this.assignSelected.size() + ")", this.success(), this.text(), mx, my, () -> {
         this.applyAssign(new LinkedHashSet<>(this.assignSelected));
         this.assignPopupOpen = false;
         this.status("Assigned to " + this.assignSelected.size() + " account(s)", -13248397);
      });
      this.button(g, x + 12 + half, by2, w - 20 - half, 16, "Cancel", this.border(), this.text(), mx, my, () -> this.assignPopupOpen = false);
   }

   private void clearMacroAssignment() {
      if (this.selectedSessionIds.isEmpty()) {
         MultiManager.get().assignAllMacro("");
      } else {
         MultiManager.get().assignMacroOnScope(this.selectedSessionIds, "");
      }

      if (!MultiManager.get().isActive()) {
         this.draft.allMacroName = "";
      }

      this.status("Assignment cleared", -6645094);
   }

   private void editMacro(RiptideMacro macro) {
      String oldName = macro != null && macro.name != null ? macro.name : "";
      this.host.editMacro(macro, saved -> {
         if (saved != null && saved.name != null) {
            if (!oldName.isBlank() && !oldName.equals(saved.name)) {
               MultiProfileManager.get().replaceMacroReferences(oldName, saved.name);
               MultiManager.get().replaceMacroReference(oldName, saved.name);
            }

            this.selectedMacroName = saved.name;
            this.status("Macro saved", -13248397);
         }
      });
   }

   private void confirmMacroDelete() {
      RiptideMacro macro = RiptideMacroManager.get().get(this.pendingMacroDelete);
      String name = this.pendingMacroDelete;
      this.pendingMacroDelete = "";
      if (macro != null) {
         RiptideMacroManager.get().delete(macro);
         MultiProfileManager.get().replaceMacroReferences(name, "");
         MultiManager.get().replaceMacroReference(name, "");
         if (name.equals(this.selectedMacroName)) {
            this.selectedMacroName = "";
         }

         this.status("Macro deleted", -6645094);
      }
   }

   public boolean mouseClicked(int mx, int my, int button) {
      this.pressMx = mx;
      if (CompactDropdown.mouseClicked(this.frameDropdowns, mx, my, button)) {
         this.clearFocus();
         return true;
      } else if (!this.contextMenuId.isBlank()) {
         if (button == 0) {
            for (int[] rect : this.contextItemRects) {
               if (mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3]) {
                  this.runContextAction(rect[4]);
                  return true;
               }
            }
         }

         this.contextMenuId = "";
         return true;
      } else {
         if (button == 1 && this.tab == MultiPanel.Tab.CONSOLE) {
            for (MultiPanel.RowHit row : this.sessionRowHits) {
               if (row.hit(mx, my)) {
                  this.contextMenuId = row.id();
                  this.contextMenuX = mx;
                  this.contextMenuY = my;
                  return true;
               }
            }
         }

         if (button == 0 && this.tab == MultiPanel.Tab.CONSOLE) {
            for (int[] rectx : this.chatSuggestRects) {
               if (mx >= rectx[0] && mx < rectx[0] + rectx[2] && my >= rectx[1] && my < rectx[1] + rectx[3]) {
                  this.applyChatSuggestionAt(rectx[4]);
                  this.chatInput.setFocused(true);
                  return true;
               }
            }
         }

         DirectRenderContext ctx = this.ctx(null, mx, my);

         for (CompactTextInput input : this.activeInputs) {
            if (input.mouseClicked(ctx, mx, my, button)) {
               for (CompactTextInput other : this.activeInputs) {
                  if (other != input) {
                     other.setFocused(false);
                  }
               }

               return true;
            }
         }

         if (button == 0 && this.tab == MultiPanel.Tab.CONSOLE) {
            if (this.handleChatClick(mx, my)) {
               return true;
            }

            if (this.beginChatSelection(mx, my)) {
               return true;
            }
         }

         this.clearFocus();
         if (button == 0) {
            for (int i = this.hotspots.size() - 1; i >= 0; i--) {
               MultiPanel.Hotspot hit = this.hotspots.get(i);
               if (hit.hit(mx, my)) {
                  try {
                     hit.action().run();
                  } catch (Throwable var9) {
                     RiptideNotifications.show("Multi action failed", this.danger());
                  }

                  return true;
               }
            }

            if (this.assignPopupOpen || this.quickEdit >= 0 || !this.pendingMacroDelete.isBlank()) {
               return true;
            }

            if (this.tab == MultiPanel.Tab.CONSOLE) {
               this.selectedSessionIds.clear();
            } else if (this.tab == MultiPanel.Tab.MACROS && this.macroViewport.hit(mx, my)) {
               this.selectedMacroName = "";
               this.pendingMacroDelete = "";
            }
         }

         return mx >= this.bx && mx < this.bx + this.bw && my >= this.by && my < this.by + this.bh;
      }
   }

   public boolean mouseReleased(int mx, int my, int button) {
      this.scrollbarDragId = null;
      this.scrollbarSetter = null;
      if (this.sliderSetter != null) {
         Runnable release = this.sliderRelease;
         this.sliderSetter = null;
         this.sliderRelease = null;
         if (release != null) {
            release.run();
         }

         return true;
      } else if (CompactDropdown.mouseReleased(this.frameDropdowns)) {
         return true;
      } else if (this.chatSelectingPress) {
         this.chatSelectingPress = false;
         this.chatSel.finishDrag();
         return true;
      } else {
         DirectRenderContext ctx = this.ctx(null, mx, my);
         boolean result = false;

         for (CompactTextInput input : this.activeInputs) {
            result |= input.mouseReleased(ctx, mx, my, button);
         }

         return result;
      }
   }

   public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
      if (CompactDropdown.mouseDragged(this.frameDropdowns, mx, my, button)) {
         return true;
      } else if (this.sliderSetter != null) {
         this.sliderSetter.apply(mx, this.sliderTrackX, this.sliderTrackW);
         return true;
      } else if (this.scrollbarDragId != null) {
         this.applyScrollbarDrag(my);
         return true;
      } else if (this.chatSelectingPress) {
         this.chatSelectAt(mx, my, false);
         return true;
      } else {
         DirectRenderContext ctx = this.ctx(null, mx, my);
         boolean result = false;

         for (CompactTextInput input : this.activeInputs) {
            result |= input.mouseDragged(ctx, mx, my, button, (float)dx, (float)dy);
         }

         return result;
      }
   }

   public boolean mouseScrolled(int mx, int my, double amount) {
      if (CompactDropdown.mouseScrolled(this.frameDropdowns, mx, my, amount)) {
         return true;
      } else if (mx < this.bx || mx >= this.bx + this.bw || my < this.by || my >= this.by + this.bh) {
         return false;
      } else if (this.assignPopupOpen) {
         this.assignScroll = Math.max(0, this.assignScroll - (int)Math.signum(amount) * 16 * 2);
         return true;
      } else if (this.quickEdit < 0 && this.pendingMacroDelete.isBlank()) {
         int direction = (int)Math.signum(amount);
         if (direction == 0) {
            return true;
         } else {
            int step = (int)Math.signum(amount) * 20 * 2;
            switch (this.tab) {
               case SETUP:
                  if (this.profileViewport.hit(mx, my)) {
                     this.profileScroll = Math.max(0, this.profileScroll - step);
                  } else if (this.setupDetailViewport.hit(mx, my)) {
                     this.setupDetailScroll = Math.max(0, this.setupDetailScroll - step);
                  }
                  break;
               case ACCOUNTS:
                  if (this.accountViewport.hit(mx, my)) {
                     this.accountScroll = Math.max(0, this.accountScroll - step);
                  }
                  break;
               case CONSOLE:
                  if (this.chatViewport.hit(mx, my)) {
                     this.chatScrollLines = Math.max(0, this.chatScrollLines + direction * 3);
                  } else if (this.sessionViewport.hit(mx, my)) {
                     this.sessionScroll = Math.max(0, this.sessionScroll - step);
                  }
               case ACTIONS:
               default:
                  break;
               case MACROS:
                  if (this.macroDelayViewport.hit(mx, my)) {
                     this.nudgeMacroDelay(direction);
                  } else if (this.macroProgressViewport.hit(mx, my)) {
                     this.macroProgressScroll = Math.max(0, this.macroProgressScroll - step);
                  } else if (this.macroViewport.hit(mx, my)) {
                     this.macroScroll = Math.max(0, this.macroScroll - step);
                  }
            }

            return true;
         }
      } else {
         return true;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 67 && (modifiers & 2) != 0 && this.chatSel.hasSelection() && !this.chatInput.isFocused()) {
         this.copyChatSelection();
         return true;
      } else {
         if (keyCode == 256) {
            if (this.chatSel.hasSelection()) {
               this.chatSel.clear();
               return true;
            }

            if (CompactDropdown.closeOpenMenu(this.frameDropdowns)) {
               return true;
            }

            if (this.quickEdit >= 0) {
               this.closeQuickEditor();
               return true;
            }

            if (!this.pendingMacroDelete.isBlank()) {
               this.pendingMacroDelete = "";
               return true;
            }

            if (this.assignPopupOpen) {
               this.assignPopupOpen = false;
               return true;
            }

            if (this.chatInput.isFocused() && !this.chatSuggests.isEmpty()) {
               this.clearChatSuggests();
               return true;
            }
         }

         if (this.chatInput.isFocused() && keyCode == 258) {
            this.applyChatTab((modifiers & 1) != 0);
            return true;
         } else {
            DirectRenderContext ctx = this.ctx(null, this.lastMx, this.lastMy);

            for (CompactTextInput input : this.activeInputs) {
               if (input.isFocused() && input.keyPressed(ctx, keyCode, scanCode, modifiers)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private void refreshChatSuggestions() {
      if (this.chatInput.isFocused()) {
         String value = this.chatInput.text();
         if (!value.equals(this.chatSuggestApplied)) {
            if (!value.equals(this.chatSuggestFor)) {
               if (value.startsWith("/")) {
                  this.refreshServerSuggestions(value);
               } else if (MultiChatCompletion.isClientCommand(value)) {
                  this.refreshClientSuggestions(value);
               } else {
                  this.clearChatSuggests();
               }
            }
         }
      } else {
         if (!this.chatSuggests.isEmpty() || this.chatSuggestFor != null || this.chatSuggestApplied != null) {
            this.clearChatSuggests();
         }
      }
   }

   private void refreshServerSuggestions(String value) {
      String stripped = value.substring(1);
      long now = System.currentTimeMillis();
      if (!value.equals(this.chatLastReqCmd) && now - this.chatLastReqAt >= 60L) {
         MultiManager.get().requestSuggestions(stripped, this.selectedSessionIds);
         this.chatLastReqCmd = value;
         this.chatLastReqAt = now;
      }

      MultiManager.SuggestionResult r = MultiManager.get().suggestions(stripped);
      if (r != null) {
         this.applyChatSuggestions(value, 1 + r.start(), r.length(), r.entries());
      }
   }

   private void refreshClientSuggestions(String value) {
      this.chatLastReqCmd = null;
      MultiChatCompletion.Result r = MultiChatCompletion.clientSuggestions(value);
      if (r != null) {
         this.applyChatSuggestions(value, r.start(), r.length(), r.entries());
      }
   }

   private void applyChatSuggestions(String value, int absStart, int length, List<String> entries) {
      if (!value.equals(this.chatSuggestFor)) {
         this.chatSuggestIndex = 0;
      }

      this.chatSuggestFor = value;
      this.chatSuggestStart = absStart;
      this.chatSuggestLen = length;
      this.chatSuggests.clear();
      this.chatSuggests.addAll(entries);
      if (this.chatSuggestIndex >= this.chatSuggests.size()) {
         this.chatSuggestIndex = Math.max(0, this.chatSuggests.size() - 1);
      }
   }

   private void applyChatSuggestionAt(int index) {
      if (index >= 0 && index < this.chatSuggests.size()) {
         this.chatSuggestIndex = index;
         String base = this.chatSuggestFor == null ? this.chatInput.text() : this.chatSuggestFor;
         int start = Math.max(0, Math.min(this.chatSuggestStart, base.length()));
         int end = Math.max(start, Math.min(this.chatSuggestStart + this.chatSuggestLen, base.length()));
         String full = base.substring(0, start) + this.chatSuggests.get(index) + base.substring(end);
         this.chatInput.setText(full);
         this.chatInput.moveCursorToEnd();
         this.chatSuggestApplied = full;
      }
   }

   private boolean applyChatTab(boolean backwards) {
      String value = this.chatInput.text();
      boolean cycling = value.equals(this.chatSuggestApplied);
      if (!this.chatSuggests.isEmpty() && (value.equals(this.chatSuggestFor) || cycling)) {
         if (cycling) {
            this.chatSuggestIndex = Math.floorMod(this.chatSuggestIndex + (backwards ? -1 : 1), this.chatSuggests.size());
         }

         String base = this.chatSuggestFor == null ? value : this.chatSuggestFor;
         int start = Math.max(0, Math.min(this.chatSuggestStart, base.length()));
         int end = Math.max(start, Math.min(this.chatSuggestStart + this.chatSuggestLen, base.length()));
         String full = base.substring(0, start) + this.chatSuggests.get(this.chatSuggestIndex) + base.substring(end);
         this.chatInput.setText(full);
         this.chatInput.moveCursorToEnd();
         this.chatInput.setFocused(true);
         this.chatSuggestApplied = full;
         return true;
      } else {
         return false;
      }
   }

   private void clearChatSuggests() {
      this.chatSuggests.clear();
      this.chatSuggestRects.clear();
      this.chatSuggestFor = null;
      this.chatSuggestApplied = null;
      this.chatLastReqCmd = null;
      this.chatSuggestIndex = 0;
   }

   public boolean charTyped(char chr, int modifiers) {
      DirectRenderContext ctx = this.ctx(null, this.lastMx, this.lastMy);

      for (CompactTextInput input : this.activeInputs) {
         if (input.isFocused() && input.charTyped(ctx, chr, modifiers)) {
            return true;
         }
      }

      return false;
   }

   private void typeMacroDelay(String text) {
      if (!this.syncingMacroDelay) {
         MultiMacroDelay.setMs(MultiMacroDelay.fromTyped(text, MultiMacroDelay.currentMs()));
      }
   }

   private void syncMacroDelayField(int delayMs) {
      if (!this.macroDelayField.isFocused()) {
         this.setMacroDelayText(MultiMacroDelay.editText(delayMs));
      }
   }

   private void setMacroDelayText(String want) {
      if (!this.macroDelayField.text().equals(want)) {
         this.syncingMacroDelay = true;

         try {
            this.macroDelayField.setText(want);
         } finally {
            this.syncingMacroDelay = false;
         }
      }
   }

   private void nudgeMacroDelay(int direction) {
      MultiMacroDelay.nudgeAndPersist(direction);
      this.setMacroDelayText(MultiMacroDelay.editText(MultiMacroDelay.currentMs()));
   }

   public boolean hasFocusedTextInput() {
      for (CompactTextInput input : this.activeInputs) {
         if (input.isFocused()) {
            return true;
         }
      }

      return false;
   }

   public void clearFocus() {
      if (this.macroDelayField.isFocused()) {
         MultiMacroDelay.persist();
      }

      this.profileName.setFocused(false);
      this.serverAddress.setFocused(false);
      this.customConcurrency.setFocused(false);
      this.customDelay.setFocused(false);
      this.autoPing.setFocused(false);
      this.accountSearch.setFocused(false);
      this.chatInput.setFocused(false);
      this.macroSearch.setFocused(false);
      this.quickName.setFocused(false);
      this.quickArgs.setFocused(false);
      this.macroDelayField.setFocused(false);
   }

   private DirectRenderContext ctx(GuiGraphicsExtractor g, int mx, int my) {
      return new DirectRenderContext(g, this.font, DirectViewport.current(1.0F), this.theme, mx, my, this.delta);
   }

   private void place(GuiGraphicsExtractor g, CompactTextInput input, int x, int y, int w) {
      this.place(g, input, x, y, w, 16);
   }

   private void place(GuiGraphicsExtractor g, CompactTextInput input, int x, int y, int w, int h) {
      input.setBounds(x, y, Math.max(10, w), h);
      input.render(this.ctx(g, this.lastMx, this.lastMy));
      if (this.interactionAllowed(x, y, Math.max(10, w), h)) {
         this.activeInputs.add(input);
      } else {
         input.setFocused(false);
      }
   }

   private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline, int color, int mx, int my, Runnable action) {
      this.button(g, x, y, w, h, label, outline, color, mx, my, action, false);
   }

   private void button(
      GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline, int color, int mx, int my, Runnable action, boolean playIcon
   ) {
      if (w > 0 && h > 0) {
         boolean enabled = action != null;
         boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
         Button.Tone tone = outline == this.success() ? Button.Tone.SUCCESS : (outline == this.danger() ? Button.Tone.DANGER : Button.Tone.NORMAL);
         Button.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, tone, hover, false);
         if (!enabled) {
            UiRenderer.rect(g, UiBounds.of(x, y, w, h), 1711276032);
         }

         if (playIcon) {
            UiRenderer.play(g, x + 5, y + Math.max(1, (h - 8) / 2), 8, enabled ? color : this.muted());
         }

         if (enabled && this.interactionAllowed(x, y, w, h)) {
            this.hotspots.add(new MultiPanel.Hotspot(x, y, w, h, action));
         }
      }
   }

   private void toggleButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, int mx, int my, Runnable action) {
      if (w > 0 && h > 0) {
         boolean enabled = action != null;
         boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
         int fill = active ? tint(this.success(), hover ? 110 : 82) : (hover ? tint(this.accent(), 52) : 705827348);
         UiRenderer.frame(g, UiBounds.of(x, y, w, h), enabled ? fill : 403771667, active ? this.success() : this.border());
         this.draw(g, label, x + w / 2, y + Math.max(2, (h - 8) / 2), enabled ? this.text() : this.muted(), true, w - 8);
         if (enabled && this.interactionAllowed(x, y, w, h)) {
            this.hotspots.add(new MultiPanel.Hotspot(x, y, w, h, action));
         }
      }
   }

   private void row(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline, int mx, int my, Runnable action) {
      if (h > 0 && w > 0) {
         boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;
         boolean emphasized = outline != this.border();
         int fill = emphasized ? tint(outline, hover ? 110 : 82) : (hover ? tint(outline, 48) : 705827348);
         if (emphasized) {
            UiRenderer.frame(g, UiBounds.of(x, y, w, h), fill, outline);
            UiRenderer.rect(g, UiBounds.of(x, y, 2, h), outline);
         } else {
            UiRenderer.rect(g, UiBounds.of(x, y, w, h), fill);
         }

         this.draw(g, label, x + 7, y + Math.max(2, (h - 8) / 2), action == null ? this.muted() : this.text(), false, w - 12);
         if (action != null && this.interactionAllowed(x, y, w, h)) {
            this.hotspots.add(new MultiPanel.Hotspot(x, y, w, h, action));
         }
      }
   }

   private void emptyState(GuiGraphicsExtractor g, String title, String sub, int x, int y, int w, int h, int mx, int my, Runnable action) {
      this.draw(g, title, x + w / 2, y + h / 2 - 16, this.text(), true, w - 12);
      this.draw(g, sub, x + w / 2, y + h / 2 - 3, this.muted(), true, w - 12);
      this.button(g, x + w / 2 - 50, y + h / 2 + 14, 100, 18, "Open Setup", this.success(), this.text(), mx, my, action);
   }

   private void draw(GuiGraphicsExtractor g, String value, int x, int y, int color, boolean centered, int maxWidth) {
      String safe = clean(value);
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      safe = UiText.trimToWidthEllipsis(this.font, safe, Math.max(1, maxWidth), fontId, color);
      int dx = centered ? x - this.font.width(safe) / 2 : x;
      UiText.draw(g, this.font, safe, fontId, color, dx, y, false);
   }

   private void scrollbar(GuiGraphicsExtractor g, MultiPanel.Viewport viewport, int offset, int maxOffset, int mx, int my, String id, IntConsumer setOffset) {
      if (viewport != MultiPanel.Viewport.NONE && viewport.h() >= 12 && maxOffset > 0) {
         int trackH = viewport.h() - 4;
         int thumbH = Math.min(trackH, Math.max(8, trackH * trackH / Math.max(trackH + maxOffset, 1)));
         int travel = Math.max(0, trackH - thumbH);
         int trackX = viewport.x() + viewport.w() - 6;
         int trackY = viewport.y() + 2;
         int top = trackY + (int)((long)travel * clamp(offset, 0, maxOffset) / maxOffset);
         boolean hovered = mx >= trackX && mx < trackX + 6 && my >= trackY && my < trackY + trackH;
         boolean dragging = id.equals(this.scrollbarDragId);
         Scrollbar.render(
            UiContexts.overlay(g, this.font, mx, my),
            new Scrollbar.Metrics(UiBounds.of(trackX, trackY, 6, trackH), UiBounds.of(trackX, top, 6, thumbH), maxOffset),
            hovered,
            dragging
         );
         this.hotspots
            .add(new MultiPanel.Hotspot(trackX, trackY, 6, trackH, () -> this.startScrollbarDrag(id, my, top, thumbH, trackY, travel, maxOffset, setOffset)));
      }
   }

   private void slider(
      GuiGraphicsExtractor g, int x, int y, int w, int h, String label, double ratio, int mx, int my, MultiPanel.SliderSetter setter, Runnable onRelease
   ) {
      if (w > 0 && h > 0) {
         int gap = 6;
         int room = Math.min(w / 2, this.font.width(label) + 2);
         boolean labelled = w - room - gap >= 12;
         int labelW = labelled ? room : 0;
         int trackX = labelled ? x + labelW + gap : x;
         int trackW = labelled ? w - labelW - gap : w;
         if (labelled) {
            this.draw(g, label, x, y + Math.max(0, (h - 8) / 2), this.muted(), false, labelW);
         }

         boolean dragging = this.sliderSetter != null && this.sliderTrackX == trackX && this.sliderTrackW == trackW;
         boolean hovered = mx >= trackX && mx < trackX + trackW && my >= y && my < y + h;
         Slider.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(trackX, y, trackW, h), ratio, hovered || dragging);
         if (this.interactionAllowed(trackX, y, trackW, h)) {
            this.hotspots.add(new MultiPanel.Hotspot(trackX, y, trackW, h, () -> {
               this.sliderSetter = setter;
               this.sliderRelease = onRelease;
               this.sliderTrackX = trackX;
               this.sliderTrackW = trackW;
               setter.apply(this.pressMx, trackX, trackW);
            }));
         }
      }
   }

   private void drawMacroProgressRow(GuiGraphicsExtractor g, MultiSession.Snapshot snapshot, int x, int y, int w, long now) {
      MultiSession.MacroProgress progress = snapshot.macroProgress();
      MultiSession.MacroQueue queue = snapshot.macroQueue();
      double fill = -1.0;
      String line;
      int color;
      if (progress != null && progress.running()) {
         line = snapshot.accountName() + "  " + progress.step() + "/" + progress.totalSteps() + "  L" + progress.loop() + "  " + progress.detail();
         color = this.success();
         fill = progress.totalSteps() <= 0 ? 0.0 : (double)progress.step() / progress.totalSteps();
      } else if (queue != null && queue.pending(now)) {
         line = snapshot.accountName() + "  waiting - starts in " + MultiMacroDelay.countdownText(queue.remainingMs(now));
         color = -1526710;
         fill = queue.elapsedRatio(now);
      } else if (progress != null && !progress.macroName().isBlank() && !progress.detail().isBlank()) {
         line = snapshot.accountName() + "  " + progress.detail();
         color = progress.detail().toLowerCase(Locale.ROOT).contains("error") ? this.danger() : this.muted();
      } else {
         line = snapshot.accountName() + "  idle";
         color = this.muted();
      }

      this.draw(g, line, x, y, color, false, w);
      if (!(fill < 0.0)) {
         int filled = (int)Math.round(w * Math.max(0.0, Math.min(1.0, fill)));
         UiRenderer.rect(g, UiBounds.of(x, y + 9, w, 2), -869256388);
         if (filled > 0) {
            UiRenderer.rect(g, UiBounds.of(x, y + 9, filled, 2), color);
         }
      }
   }

   private void startScrollbarDrag(String id, int my, int thumbTop, int thumbH, int trackY, int travel, int maxOffset, IntConsumer setOffset) {
      this.scrollbarGrab = my >= thumbTop && my < thumbTop + thumbH ? my - thumbTop : thumbH / 2;
      this.scrollbarDragId = id;
      this.scrollbarTrackY = trackY;
      this.scrollbarTravel = travel;
      this.scrollbarMax = maxOffset;
      this.scrollbarSetter = setOffset;
   }

   private void applyScrollbarDrag(int my) {
      if (this.scrollbarDragId != null && this.scrollbarSetter != null) {
         int thumbTop = Math.max(this.scrollbarTrackY, Math.min(this.scrollbarTrackY + this.scrollbarTravel, my - this.scrollbarGrab));
         int value = this.scrollbarTravel <= 0 ? 0 : (thumbTop - this.scrollbarTrackY) * this.scrollbarMax / this.scrollbarTravel;
         this.scrollbarSetter.accept(Math.max(0, Math.min(this.scrollbarMax, value)));
      }
   }

   private List<MultiPanel.AccountChoice> filteredAccounts(MultiProfile profile) {
      String query = clean(this.accountSearch.text()).toLowerCase(Locale.ROOT);
      int filterMask = 0;

      for (RiptideAccountType filter : this.accountFilters) {
         filterMask |= 1 << filter.ordinal();
      }

      long accountRevision = RiptideAccountManager.get().changeRevision();
      if (this.cachedAccountSourceSize < 0 || accountRevision != this.cachedAccountSourceRevision) {
         RiptideAccountManager accountManager = RiptideAccountManager.get();
         this.cachedAccountSource = List.copyOf(accountManager.all());
         this.cachedAccountSourceSize = this.cachedAccountSource.size();
         this.cachedAccountSourceRevision = accountRevision;
         this.cachedAccountChoiceProfile = null;
      }

      int sessionCount = profile == null ? 0 : profile.sessions.size();
      long uiRevision = MultiManager.get().uiRevision();
      if (query.equals(this.cachedAccountChoiceQuery)
         && filterMask == this.cachedAccountChoiceFilterMask
         && profile == this.cachedAccountChoiceProfile
         && sessionCount == this.cachedAccountChoiceSessionCount
         && uiRevision == this.cachedAccountChoiceUiRevision) {
         return this.cachedAccountChoices;
      } else {
         List<MultiPanel.AccountChoice> out = new ArrayList<>();
         String defaultName = RiptideAccountSessionSwitcher.getOriginalUser().getName();
         MultiPanel.AccountChoice current = new MultiPanel.AccountChoice(
            "default",
            defaultName + " (Default)",
            RiptideAccountSessionSwitcher.getOriginalUser().getAccessToken().isBlank() ? RiptideAccountType.Cracked : RiptideAccountType.Session,
            MultiManager.isCurrentRenderedAccount("default")
         );
         if (this.accountFilters.contains(current.type()) && current.label().toLowerCase(Locale.ROOT).contains(query)) {
            out.add(current);
         }

         Set<String> knownAccountIds = new HashSet<>(this.cachedAccountSource.size() * 2 + 1);

         for (RiptideAccount account : this.cachedAccountSource) {
            RiptideAccountType type = account.type == null ? RiptideAccountType.Cracked : account.type;
            String label = clean(account.displayName());
            knownAccountIds.add(account.stableId());
            if (this.accountFilters.contains(type) && label.toLowerCase(Locale.ROOT).contains(query)) {
               out.add(new MultiPanel.AccountChoice(account.stableId(), label, type, MultiManager.isCurrentRenderedAccount(account.stableId())));
            }
         }

         for (MultiProfile.SessionSpec spec : profile == null ? List.of() : profile.sessions) {
            boolean known = spec.accountId().equals("default") || knownAccountIds.contains(spec.accountId());
            if (!known && (query.isBlank() || spec.accountId().toLowerCase(Locale.ROOT).contains(query))) {
               out.add(new MultiPanel.AccountChoice(spec.accountId(), "Missing: " + spec.accountId(), RiptideAccountType.Cracked, false));
            }
         }

         this.cachedAccountChoiceQuery = query;
         this.cachedAccountChoiceFilterMask = filterMask;
         this.cachedAccountChoiceProfile = profile;
         this.cachedAccountChoiceSessionCount = sessionCount;
         this.cachedAccountChoiceUiRevision = uiRevision;
         this.cachedAccountChoices = List.copyOf(out);
         return this.cachedAccountChoices;
      }
   }

   private List<RiptideMacro> filteredMacros() {
      String query = clean(this.macroSearch.text()).toLowerCase(Locale.ROOT);
      long revision = RiptideMacroManager.get().getRevision();
      if (revision == this.cachedMacroRevision && query.equals(this.cachedMacroQuery)) {
         return this.cachedMacros;
      } else {
         this.cachedMacroRevision = revision;
         this.cachedMacroQuery = query;
         this.cachedMacros = RiptideMacroManager.get()
            .getAll()
            .stream()
            .filter(m -> m != null && m.name != null && m.name.toLowerCase(Locale.ROOT).contains(query))
            .toList();
         return this.cachedMacros;
      }
   }

   private List<MultiProfile> savedProfiles() {
      MultiProfileManager manager = MultiProfileManager.get();
      long revision = manager.revision();
      if (revision != this.cachedProfilesRevision) {
         this.cachedProfilesRevision = revision;
         this.cachedProfiles = manager.all();
      }

      return this.cachedProfiles;
   }

   private MultiProfile activeProfileSnapshot(MultiManager manager) {
      long uiRevision = manager.uiRevision();
      long sessionRevision = manager.sessionRevision();
      if (uiRevision != this.cachedActiveUiRevision || sessionRevision != this.cachedActiveSessionRevision) {
         this.cachedActiveUiRevision = uiRevision;
         this.cachedActiveSessionRevision = sessionRevision;
         this.cachedActiveProfile = manager.activeProfile();
      }

      return this.cachedActiveProfile;
   }

   private List<String> macroCompatibility(String name) {
      long revision = RiptideMacroManager.get().getRevision();
      String safeName = name == null ? "" : name;
      if (revision == this.cachedCompatibilityRevision && safeName.equals(this.cachedCompatibilityName)) {
         return this.cachedCompatibility;
      } else {
         this.cachedCompatibilityRevision = revision;
         this.cachedCompatibilityName = safeName;
         this.cachedCompatibility = MultiManager.get().macroCompatibility(safeName);
         return this.cachedCompatibility;
      }
   }

   private MultiProfile.SessionSpec selectedSpec(String id) {
      return selectedSpec(this.draft, id);
   }

   private static MultiProfile.SessionSpec selectedSpec(MultiProfile profile, String id) {
      if (profile == null) {
         return null;
      } else {
         for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (spec.accountId().equals(id)) {
               return spec;
            }
         }

         return null;
      }
   }

   private String proxyLabel(String id) {
      return this.proxyLabel(this.draft, id);
   }

   private String proxyLabel(MultiProfile profile, String id) {
      MultiProfile.ProxyMode mode = profile == null ? MultiProfile.ProxyMode.Off : profile.proxyMode;
      if (mode == MultiProfile.ProxyMode.Off) {
         return "Proxy Off";
      } else if (mode == MultiProfile.ProxyMode.Auto) {
         return "Best Proxy";
      } else if (id == null || id.isBlank()) {
         return "Proxy Off";
      } else if ("best".equals(id)) {
         return "Best Proxy";
      } else {
         RiptideProxy proxy = RiptideProxyManager.get().findById(id);
         return proxy == null ? "Missing" : proxy.displayName();
      }
   }

   private String proxyModeLabel() {
      return switch (this.draft.proxyMode) {
         case Off -> "Off";
         case Auto -> "Auto";
         case Manual -> "Manual";
      };
   }

   private void configureSetupDropdown(CompactDropdown dropdown, int x, int y, int width, int selected, String label) {
      dropdown.setBounds(x, y, Math.max(1, width), 17).setSelectedIndex(selected).setButtonLabelOverride(label);
      dropdown.active = true;
      this.frameDropdowns.add(dropdown);
   }

   private static String commonManualProxyId(MultiProfile profile) {
      if (profile != null && !profile.sessions.isEmpty()) {
         String common = profile.sessions.getFirst().proxyId();

         for (int i = 1; i < profile.sessions.size(); i++) {
            if (!Objects.equals(common, profile.sessions.get(i).proxyId())) {
               return null;
            }
         }

         return common == null ? "" : common;
      } else {
         return "";
      }
   }

   private static Map<String, Integer> manualProxyUsage(MultiProfile profile) {
      if (profile != null && !profile.sessions.isEmpty()) {
         Map<String, Integer> usage = new LinkedHashMap<>();

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!spec.direct() && !spec.bestProxy()) {
               usage.merge(spec.proxyId(), 1, Integer::sum);
            }
         }

         return Map.copyOf(usage);
      } else {
         return Map.of();
      }
   }

   private static List<String> pingOptionLabels() {
      List<String> labels = new ArrayList<>(PING_OPTIONS.length);

      for (int option : PING_OPTIONS) {
         labels.add(option + "ms");
      }

      return labels;
   }

   private static int pingIndex(int value) {
      int index = 0;
      int best = Integer.MAX_VALUE;

      for (int i = 0; i < PING_OPTIONS.length; i++) {
         int distance = Math.abs(PING_OPTIONS[i] - value);
         if (distance < best) {
            best = distance;
            index = i;
         }
      }

      return index;
   }

   private String currentServer() {
      return this.mc != null && this.mc.getCurrentServer() != null && this.mc.getCurrentServer().ip != null ? clean(this.mc.getCurrentServer().ip) : "";
   }

   private int sessionColor(MultiSession.Snapshot snapshot) {
      return switch (snapshot.displayState(System.currentTimeMillis())) {
         case GREEN -> this.success();
         case RED -> -42406;
         case YELLOW -> -866997;
      };
   }

   private void result(MultiManager.BroadcastResult result) {
      if (result == null) {
         this.status("Failed", -42149);
      } else {
         if (result.failed() > 0) {
            this.status("Failed " + result.failed(), -42149);
         } else if (result.sent() > 0) {
            this.status("Sent " + result.sent(), -13248397);
         } else if (result.skipped() > 0) {
            this.status("Skipped", -6645094);
         } else {
            this.status("Nothing sent", -6645094);
         }
      }
   }

   private void status(String text, int color) {
      this.status = MultiManager.singleLine(text, 80);
      this.statusColor = color;
   }

   private int themedStatus() {
      if (this.statusColor == -13248397) {
         return this.success();
      } else if (this.statusColor == -42149) {
         return this.danger();
      } else {
         return this.statusColor == -14249 ? this.warn() : this.muted();
      }
   }

   private void refreshTheme() {
   }

   private int border() {
      return RiptideTheme.recolor(-12174784, RiptideTheme.Channel.OUTLINE);
   }

   private int accent() {
      return RiptideTheme.recolor(-46518, RiptideTheme.Channel.ACCENT);
   }

   private int success() {
      return RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
   }

   private int danger() {
      return RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
   }

   private int text() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private int muted() {
      return RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
   }

   private int warn() {
      return RiptideTheme.recolor(-14249, RiptideTheme.Channel.ACCENT);
   }

   private static int tint(int color, int alpha) {
      return alpha << 24 | color & 16777215;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private boolean interactionAllowed(int x, int y, int w, int h) {
      return this.interactionClip == MultiPanel.Viewport.NONE || this.interactionClip.contains(x, y, w, h);
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value.trim());
      } catch (Exception var3) {
         return fallback;
      }
   }

   private static String clean(String value) {
      return MultiManager.singleLine(value == null ? "" : value.replace(' ', ' '), 512);
   }

   private boolean ctrlDown() {
      if (this.mc != null && this.mc.getWindow() != null) {
         long window = this.mc.getWindow().handle();
         return GLFW.glfwGetKey(window, 341) == 1 || GLFW.glfwGetKey(window, 345) == 1;
      } else {
         return false;
      }
   }

   private boolean shiftDown() {
      if (this.mc != null && this.mc.getWindow() != null) {
         long window = this.mc.getWindow().handle();
         return GLFW.glfwGetKey(window, 340) == 1 || GLFW.glfwGetKey(window, 344) == 1;
      } else {
         return false;
      }
   }

   private record AccountChoice(String id, String label, RiptideAccountType type, boolean current) {
   }

   private record ChatHitRow(MultiManager.ChatLine line, int lineIndex, int y, FormattedText hit, String text) {
   }

   public interface Host {
      void manageAccounts();

      void pickManualProxy(String var1, String var2, String var3, Map<String, Integer> var4, Consumer<String> var5);

      void openGui(String var1);

      boolean isGuiOpen(String var1);

      void toggleTakeover(String var1);

      boolean isTakeoverActive(String var1);

      boolean canTakeover(String var1);

      void openSharedGui();

      boolean isSharedGuiOpen();

      void pickQuickPacket(Consumer<Class<? extends Packet<?>>> var1);

      void editBlocklist(MultiPacketPolicy.Direction var1, Collection<Class<? extends Packet<?>>> var2, BiConsumer<Class<? extends Packet<?>>, Boolean> var3);

      void editMacro(RiptideMacro var1, Consumer<RiptideMacro> var2);

      void editFormValues(MultiProfile var1, Set<String> var2, Consumer<MultiProfile> var3);

      void editAutoAccept(MultiAutoAccept var1, Consumer<MultiAutoAccept> var2, boolean var3);
   }

   private record Hotspot(int x, int y, int w, int h, Runnable action) {
      boolean hit(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private record RowHit(int x, int y, int w, int h, String id) {
      boolean hit(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private interface SliderSetter {
      void apply(double var1, int var3, int var4);
   }

   private static enum Tab {
      SETUP,
      ACCOUNTS,
      CONSOLE,
      ACTIONS,
      MACROS;
   }

   private record Viewport(int x, int y, int w, int h) {
      private static final MultiPanel.Viewport NONE = new MultiPanel.Viewport(0, 0, 0, 0);

      boolean hit(int mx, int my) {
         return this.w > 0 && this.h > 0 && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }

      boolean contains(int rx, int ry, int rw, int rh) {
         return rw > 0 && rh > 0 && rx >= this.x && ry >= this.y && rx + rw <= this.x + this.w && ry + rh <= this.y + this.h;
      }

      UiBounds bounds() {
         return UiBounds.of(this.x, this.y, Math.max(1, this.w), Math.max(1, this.h));
      }
   }
}
