package riptide.gui.screen;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.ClickEvent.CopyToClipboard;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import riptide.commands.RiptideCommands;
import riptide.gui.multi.MultiChatCompletion;
import riptide.gui.multi.MultiChatPresentation;
import riptide.gui.multi.MultiChatSelection;
import riptide.gui.multi.MultiMacroPresentation;
import riptide.gui.multi.MultiMenuInput;
import riptide.gui.multi.MultiMenuRenderer;
import riptide.gui.multi.MultiTooltip;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.Slider;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideChatField;
import riptide.util.RiptideConfig;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiClientCommands;
import riptide.util.multi.MultiMacroDelay;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiQuickAction;
import riptide.util.multi.MultiSession;
import riptide.util.multi.MultiSharedGui;
import riptide.util.multi.MultiTakeoverState;

public final class RiptideMultiConsoleScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MARGIN = 12;
   private static final int MAX_SESSION_WIDTH = 300;
   private static final int ROW_HEIGHT = 25;
   private static final int DETAIL_ROW_H = 72;
   private static final int GUI_SUB_H = 11;
   private static final int MACRO_SUB_H = 11;
   private static final int MACRO_DELAY_H = 14;
   private static final String MACRO_DELAY_LABEL = "Delay between bots";
   private static final int GUI_W = 40;
   private static final int POV_W = 40;
   private static final int CHAT_MIN_H = 66;
   private static final Identifier HEART_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
   private static final Identifier FOOD_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
   private static final int STATUS_GREEN = -11013497;
   private static final int STATUS_YELLOW = -866997;
   private static final int STATUS_RED = -42406;
   private static final int CHAT_LINE_HEIGHT = 11;
   private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
   private final Screen parent;
   private final List<RiptideMultiConsoleScreen.SessionRow> sessionRows = new ArrayList<>();
   private final List<int[]> presetRects = new ArrayList<>();
   private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
   private String anchorId;
   private final List<RiptideMultiConsoleScreen.ChatRow> chatRows = new ArrayList<>();
   private List<MultiChatPresentation.VisualRow> cachedVisualRows = List.of();
   private long cachedChatRevision = Long.MIN_VALUE;
   private String cachedChatScope = null;
   private int cachedChatWidth = -1;
   private int cachedChatTotal = -1;
   private int chatScroll;
   private int chatLeft;
   private int chatRight;
   private int chatTop;
   private int chatBottom;
   private int chatAvail;
   private final MultiChatSelection chatSel = new MultiChatSelection();
   private boolean chatSelectingPress;
   private CompactScrollbar.Metrics chatScrollbar;
   private boolean chatScrollbarDragging;
   private int chatScrollbarGrab;
   private int chatMaxScrollRows;
   private boolean detailsOpen;
   private String viewingId;
   private boolean sharedView;
   private String sharedKey = "";
   private List<MultiSharedGui.Group> sharedGroups = List.of();
   private final List<int[]> sharedTabRects = new ArrayList<>();
   private int hoveredViewHotbar = -1;
   private int[] closeSilentRect;
   private int[] detailsRect;
   private int[] sharedGuiRect;
   private String macroRowTooltip;
   private int[] macroDelayTrack;
   private int[] macroDelayRow;
   private int[] macroDelayBox;
   private boolean macroDelayDragging;
   private int viewScroll;
   private MultiSession.MenuView cachedView;
   private String cachedViewId;
   private long cachedViewRev = Long.MIN_VALUE;
   private final List<int[]> viewSlotRects = new ArrayList<>();
   private final List<MultiMenuRenderer.MenuHit> viewWidgetHits = new ArrayList<>();
   private final MultiMenuInput menuInput = new MultiMenuInput();
   private final Map<String, MultiSession.Snapshot> frameSnapshots = new HashMap<>();
   private ItemStack hoveredViewStack = ItemStack.EMPTY;
   private int hoveredViewHandler = -1;
   private int hoveredViewX;
   private int hoveredViewY;
   private int viewGridX;
   private int viewGridY;
   private int viewGridW;
   private int viewGridH;
   private int infoColumnTop;
   private EditBox chatInput;
   private RiptideChatField delayField;
   private boolean delayFieldWasFocused;
   private boolean syncingDelayField;
   private int sessionScroll;
   private long sessionLayoutSignature = Long.MIN_VALUE;
   private int historyIndex = -1;
   private String historyDraft = "";
   private final List<String> suggestions = new ArrayList<>();
   private int suggestionIndex;
   private int suggestStart;
   private int suggestLength;
   private String suggestForText;
   private String suggestOriginal;
   private boolean appliedOnce;
   private boolean frozen;
   private String expectedValue;
   private String lastRequestedCmd;
   private long lastRequestAt;
   private final List<int[]> suggestRects = new ArrayList<>();
   private long lastUiRevision = Long.MIN_VALUE;
   private long lastSessionRevision = Long.MIN_VALUE;
   private String resultText = "";
   private int resultColor = -6645094;
   private String consoleMacro = "";
   private static final int ACTION_H = 16;

   public RiptideMultiConsoleScreen(Screen parent) {
      super(Component.literal("Multi Console"));
      this.parent = parent;
      this.consoleMacro = MultiManager.get().allMacroName();
   }

   protected void init() {
      this.rebuildControls();
   }

   private void rebuildControls() {
      String chatValue = this.chatInput == null ? "" : this.chatInput.getValue();
      boolean restoreChatFocus = this.chatInput != null && this.chatInput.isFocused();
      int chatCursor = this.chatInput == null ? chatValue.length() : this.chatInput.getCursorPosition();
      this.clearWidgets();
      int sessionW = this.sessionWidth();
      int consoleX = this.consoleX();
      int consoleW = this.consoleWidth();
      int sendW = Math.min(68, Math.max(36, consoleW / 3));
      this.chatInput = new EditBox(
         this.font, consoleX + 10, this.screenHeight() - 36, Math.max(10, consoleW - sendW - 24), 18, Component.literal("Chat or command")
      );
      this.chatInput.setMaxLength(256);
      this.chatInput.setHint(Component.literal("Chat or /command"));
      this.chatInput.setResponder(value -> this.updateGhostSuggestion());
      this.chatInput.setValue(chatValue);
      this.chatInput.setCursorPosition(Math.max(0, Math.min(chatCursor, chatValue.length())));
      this.addRenderableWidget(this.chatInput);
      if (restoreChatFocus) {
         this.chatInput.setFocused(true);
         this.setFocused(this.chatInput);
      }

      this.addStyled(consoleX + consoleW - sendW - 10, this.screenHeight() - 36, sendW, 18, "Send", Button.Tone.SUCCESS, button -> this.sendChat());
      this.addQuickActionButtons(consoleX + 10, 46, consoleW - 20);
      this.addQuickManagementButtons(consoleX + 10, 72, consoleW - 20);
      this.addActionButtons(consoleX + 10, 94, consoleW - 20);
      if (!this.isViewing()) {
         this.addMacroButtons(consoleX + 10, 116, consoleW - 20);
      }

      if (this.isViewing()) {
         this.addStyled(consoleX + consoleW - 68, 22, 60, 14, "Close GUI", Button.Tone.NORMAL, button -> this.exitView());
      }

      int detailsW = Math.min(62, Math.max(34, sessionW - 8));
      this.detailsRect = new int[]{12 + sessionW - detailsW - 4, 22, detailsW, 14};
      this.addStyled(
         12 + sessionW - detailsW - 4,
         22,
         detailsW,
         14,
         this.detailsOpen ? "Details On" : "Details",
         this.detailsOpen ? Button.Tone.SUCCESS : Button.Tone.NORMAL,
         button -> this.toggleDetails()
      );
      this.sharedGuiRect = new int[]{16, 44, sessionW - 8, 16};
      this.addStyled(
         16,
         44,
         sessionW - 8,
         16,
         this.sharedView ? "Shared GUI: On" : "Shared GUI",
         this.sharedView ? Button.Tone.SUCCESS : Button.Tone.PRIMARY,
         button -> this.toggleSharedView()
      );
      int footerGap = 3;
      int footerEach = Math.max(1, (sessionW - footerGap * 2) / 3);
      this.addStyled(12, this.screenHeight() - 34, footerEach, 18, "Disconnect", Button.Tone.DANGER, button -> {
         MultiManager.get().disconnectAll("Disconnected by user");
         this.openProfiles();
      });
      this.addStyled(12 + footerEach + footerGap, this.screenHeight() - 34, footerEach, 18, "Retry All", Button.Tone.PRIMARY, button -> this.retryAll());
      this.addStyled(
         12 + (footerEach + footerGap) * 2,
         this.screenHeight() - 34,
         sessionW - (footerEach + footerGap) * 2,
         18,
         "Profiles",
         Button.Tone.NORMAL,
         button -> this.openProfiles()
      );
      this.addSessionButtons();
      this.lastUiRevision = MultiManager.get().uiRevision();
      this.lastSessionRevision = MultiManager.get().sessionRevision();
   }

   private void addQuickActionButtons(int x, int y, int width) {
      MultiProfile profile = MultiManager.get().activeProfile();
      if (profile != null) {
         this.presetRects.clear();
         int gap = 6;
         int count = 5;
         String[] labels = new String[count];
         labels[0] = "Move";

         for (int i = 0; i < 4; i++) {
            MultiQuickAction action = profile.quickAction(i);
            labels[i + 1] = action.empty() ? "Empty" : action.label(i);
         }

         int avail = width - gap * (count - 1);
         int each = Math.max(1, avail / count);
         this.addStyled(x, y, each, 18, "Move", Button.Tone.SUCCESS, button -> this.sendMovement());
         int cx = x + each + gap;

         for (int i = 0; i < 4; i++) {
            int index = i;
            MultiQuickAction action = profile.quickAction(i);
            Button.Tone tone = action.empty() ? Button.Tone.NORMAL : Button.Tone.PRIMARY;
            int cw = i == 3 ? x + width - cx : each;
            this.addStyled(cx, y, cw, 18, labels[i + 1], tone, button -> this.sendQuickAction(index));
            this.presetRects.add(new int[]{cx, y, cw, 18, index});
            cx += cw + gap;
         }
      }
   }

   private void addQuickManagementButtons(int x, int y, int width) {
      int gap = 6;
      int half = Math.max(1, (width - gap) / 2);
      this.addStyled(x, y, half, 16, "Reset presets", Button.Tone.NORMAL, button -> this.resetQuickActions());
      this.addStyled(x + half + gap, y, width - half - gap, 16, "Advanced", Button.Tone.NORMAL, button -> this.openPolicy());
   }

   private void addActionButtons(int x, int y, int width) {
      String[] labels = new String[]{"Use", "GUI", "Close", "Close W/O Pkt"};
      int gap = 6;
      int count = labels.length;
      int avail = width - gap * (count - 1);
      int each = Math.max(1, avail / count);
      int cx = x;
      Runnable[] actions = new Runnable[]{this::doUse, this::doOpenInventory, this::doClose, this::doCloseSilent};

      for (int i = 0; i < count; i++) {
         Runnable action = actions[i];
         int cw = i == count - 1 ? x + width - cx : each;
         this.addStyled(cx, y, cw, 16, labels[i], Button.Tone.NORMAL, button -> action.run());
         if (i == count - 1) {
            this.closeSilentRect = new int[]{cx, y, cw, 16};
         }

         cx += cw + gap;
      }
   }

   private void addMacroButtons(int x, int y, int width) {
      int gap = 6;
      int sideW = Math.max(24, Math.min(60, Math.max(1, (width - 3 * gap) / 5)));
      int macroX = x + 3 * (sideW + gap);
      int macroW = Math.max(1, x + width - macroX);
      this.addStyled(x, y, sideW, 16, "Run", Button.Tone.SUCCESS, button -> this.runMacroScope());
      this.addStyled(x + sideW + gap, y, sideW, 16, "Stop", Button.Tone.DANGER, button -> this.stopMacroScope());
      this.addStyled(x + 2 * (sideW + gap), y, sideW, 16, "Assign", Button.Tone.PRIMARY, button -> this.openAssign());
      RiptideStyledButton picker = new RiptideStyledButton(
         macroX,
         y,
         macroW,
         16,
         Component.literal("Macro (" + this.currentMacroLabel() + ")"),
         Button.Tone.NORMAL,
         () -> this.fitLabel("Macro (" + this.currentMacroLabel() + ")", macroW - 8),
         button -> this.chooseMacro()
      );
      this.addRenderableWidget(picker);
   }

   private String currentMacroLabel() {
      return orNone(this.consoleMacro);
   }

   private static String orNone(String name) {
      return name != null && !name.isBlank() ? name : "none";
   }

   private void chooseMacro() {
      this.minecraft.gui.setScreen(new RiptideMultiMacroPickerScreen(this, this.consoleMacro, name -> {
         this.consoleMacro = name == null ? "" : name;
         this.resultText = this.consoleMacro.isBlank() ? "No macro chosen" : "Chose \"" + this.consoleMacro + "\"";
         this.resultColor = -13248397;
      }));
   }

   private void openAssign() {
      if (this.consoleMacro.isBlank()) {
         this.resultText = "Choose a macro first (Macro button)";
         this.resultColor = -6645094;
      } else {
         this.minecraft.gui.setScreen(new RiptideMultiAssignScreen(this, this.consoleMacro, this.selectedIds));
      }
   }

   private void runMacroScope() {
      MultiManager m = MultiManager.get();
      if (m.hasAssignedMacroOnInteractiveScope(this.actionScope())) {
         this.applyResult(m.runMacroOnInteractiveScope(this.actionScope()));
      } else if (!this.consoleMacro.isBlank()) {
         RiptideMacro macro = RiptideMacroManager.get().get(this.consoleMacro);
         if (macro != null) {
            this.applyResult(m.runMacroDirectInteractive(macro, this.actionScope()));
         } else {
            this.resultText = "Macro not found";
            this.resultColor = -42149;
         }
      } else {
         this.resultText = "Choose or assign a macro first";
         this.resultColor = -6645094;
      }
   }

   private void stopMacroScope() {
      this.applyResult(MultiManager.get().stopMacroOnInteractiveScope(this.actionScope()));
   }

   private void doUse() {
      this.applyResult(MultiManager.get().useOnScope(this.actionScope()));
   }

   private void doClose() {
      this.applyResult(MultiManager.get().closeOnScope(this.actionScope()));
   }

   private void doCloseSilent() {
      this.applyResult(MultiManager.get().closeSilentOnScope(this.actionScope()));
   }

   private void doOpenInventory() {
      if (this.selectedIds.size() != 1) {
         this.resultText = "Select one bot";
         this.resultColor = -6645094;
      } else {
         this.enterView(this.selectedIds.iterator().next());
      }
   }

   private void applyResult(MultiManager.BroadcastResult result) {
      this.resultText = shortResult(result);
      this.resultColor = result.failed() > 0 ? -42149 : (result.skipped() > 0 ? -6645094 : -13248397);
   }

   private void enterView(String accountId) {
      this.viewingId = accountId;
      this.sharedView = false;
      this.viewScroll = 0;
      this.chatScroll = 0;
      this.cachedViewId = null;
      this.rebuildControls();
   }

   private void exitView() {
      this.viewingId = null;
      this.sharedView = false;
      this.chatScroll = 0;
      this.cachedView = null;
      this.cachedViewId = null;
      this.rebuildControls();
   }

   private void toggleSharedView() {
      this.sharedView = !this.sharedView;
      this.viewingId = null;
      this.viewScroll = 0;
      this.chatScroll = 0;
      this.cachedView = null;
      this.cachedViewId = null;
      this.rebuildControls();
   }

   private boolean isViewing() {
      return this.viewingId != null || this.sharedView;
   }

   private String viewTargetId() {
      if (!this.sharedView) {
         return this.viewingId;
      } else {
         MultiSharedGui.Group group = MultiSharedGui.pick(this.sharedGroups, this.sharedKey);
         return group == null ? null : group.representativeId();
      }
   }

   private List<String> sharedIds() {
      if (!this.sharedView) {
         return List.of();
      } else {
         MultiSharedGui.Group group = MultiSharedGui.pick(this.sharedGroups, this.sharedKey);
         return group == null ? List.of() : group.accountIds();
      }
   }

   private void applyFanout(MultiManager.BroadcastResult result) {
      if (result != null) {
         int missed = result.failed() + result.skipped();
         if (result.sent() <= 0 || missed != 0) {
            this.resultText = result.sent() == 0 ? "No bot took the click" : "Sent " + result.sent() + ", missed " + missed;
            this.resultColor = result.sent() == 0 ? -42149 : -6645094;
         }
      }
   }

   private void toggleDetails() {
      this.detailsOpen = !this.detailsOpen;
      this.sessionScroll = 0;
      this.rebuildControls();
   }

   private void addSessionButtons() {
      this.sessionRows.clear();
      List<MultiSession.Snapshot> sessions = MultiManager.get().snapshots();
      this.sessionLayoutSignature = sessionLayoutSignature(sessions);
      Set<String> present = new HashSet<>();

      for (MultiSession.Snapshot s : sessions) {
         present.add(s.accountId());
      }

      this.selectedIds.retainAll(present);
      if (this.anchorId != null && !present.contains(this.anchorId)) {
         this.anchorId = null;
      }

      int top = 64;
      int bottom = this.screenHeight() - 46;
      int rowPitch = (this.detailsOpen ? 72 : 25) + 11;
      int viewH = Math.max(1, bottom - top);
      int maxScroll = 0;
      int tail = 0;

      for (int i = sessions.size() - 1; i >= 0; i--) {
         tail += rowPitch + guiExtra(sessions.get(i));
         if (tail >= viewH) {
            maxScroll = i;
            break;
         }
      }

      this.sessionScroll = Math.max(0, Math.min(this.sessionScroll, maxScroll));
      int y = top;

      for (int ix = this.sessionScroll; ix < sessions.size(); ix++) {
         int extra = guiExtra(sessions.get(ix));
         if (!this.sessionRows.isEmpty() && y + rowPitch + extra > bottom) {
            break;
         }

         this.sessionRows.add(new RiptideMultiConsoleScreen.SessionRow(sessions.get(ix).accountId(), y, extra));
         y += rowPitch + extra;
      }
   }

   private static long sessionLayoutSignature(List<MultiSession.Snapshot> sessions) {
      long hash = sessions.size();

      for (MultiSession.Snapshot snapshot : sessions) {
         hash = 31L * hash + snapshot.accountId().hashCode();
         hash = 31L * hash + (guiExtra(snapshot) > 0 ? 1 : 0);
      }

      return hash;
   }

   private void refreshSessionLayoutIfNeeded() {
      List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();
      if (sessionLayoutSignature(snapshots) != this.sessionLayoutSignature) {
         this.addSessionButtons();
      }
   }

   private static int guiExtra(MultiSession.Snapshot snapshot) {
      String gui = snapshot.openScreen();
      return !snapshot.customMenuOpen() && (gui == null || gui.isBlank()) ? 0 : 11;
   }

   private int rowFrameHeight() {
      return this.detailsOpen ? 67 : 20;
   }

   private void triggerSessionAction(String id) {
      MultiSession.Snapshot current = this.findSnapshot(id);
      if (current != null && !MultiManager.isRetryable(current.status())) {
         MultiManager.get().disconnectSession(id);
         this.resultText = "Stopped";
         this.resultColor = -42149;
      } else {
         MultiManager.RetryResult result = MultiManager.get().retry(id);
         this.resultText = MultiManager.singleLine(result.message(), 40);
         this.resultColor = result.ok() ? -13248397 : -42149;
      }
   }

   private void retryAll() {
      MultiManager.RetryResult result = MultiManager.get().retryAllDisconnected();
      this.resultText = MultiManager.singleLine(result.message(), 40);
      this.resultColor = result.ok() ? -13248397 : -6645094;
   }

   private void openProfiles() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideMultiScreen(this.parent, "", true));
      }
   }

   private int actionX() {
      return 12 + this.sessionWidth() - this.actionWidth() - 4;
   }

   private int actionWidth() {
      return this.sessionWidth() < 180 ? 44 : 60;
   }

   private void renderSessionRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      this.macroRowTooltip = null;
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      int sessionW = this.sessionWidth();
      int actionW = this.actionWidth();

      for (RiptideMultiConsoleScreen.SessionRow row : this.sessionRows) {
         MultiSession.Snapshot snapshot = this.findSnapshot(row.id());
         if (snapshot != null) {
            int color = statusColor(snapshot);
            boolean selected = this.selectedIds.contains(row.id());
            int selectionColor = -39220;
            int frameH = this.rowFrameHeight() + 11 + row.guiExtra();
            int frameColor = selected ? selectionColor : color;
            int fill = frameColor & 16777215 | (selected ? 1140850688 : 520093696);
            UiRenderer.rect(graphics, UiBounds.of(16, row.y(), sessionW - 8, frameH), fill);
            UiRenderer.rect(graphics, UiBounds.of(16, row.y(), 3, frameH), frameColor);
            int actionX = this.actionX();
            boolean showView = sessionW >= 220;
            int viewX = showView ? actionX - 6 - 40 : actionX;
            boolean viewingThis = row.id().equals(this.viewingId);
            int viewBorder = RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE);
            int viewFill = viewingThis ? 1432015210 : 855638016;
            if (showView) {
               UiRenderer.frame(graphics, UiBounds.of(viewX, row.y() + 2, 40, 16), viewFill, viewBorder);
               if (viewingThis) {
                  UiRenderer.rect(graphics, UiBounds.of(viewX + 1, row.y() + 2 + 16 - 2, 38, 1), -10825366);
               }

               int labelColor = viewingThis ? -10825366 : -6641998;
               String viewLabelFit = UiText.trimToWidthEllipsis(this.font, "GUI", 36, fontId, labelColor);
               int viewLabelW = UiText.width(this.font, viewLabelFit, fontId, labelColor);
               UiText.draw(graphics, this.font, viewLabelFit, fontId, labelColor, viewX + Math.max(2, (40 - viewLabelW) / 2), row.y() + 6, false);
            }

            boolean povThis = MultiTakeoverState.isActive(row.id());
            boolean povOk = povThis || MultiTakeoverState.available(row.id());
            boolean showPov = showView && povOk;
            int povX = showPov ? viewX - 6 - 40 : viewX;
            if (showPov) {
               int povFill = povThis ? 1432015210 : 855638016;
               UiRenderer.frame(graphics, UiBounds.of(povX, row.y() + 2, 40, 16), povFill, viewBorder);
               if (povThis) {
                  UiRenderer.rect(graphics, UiBounds.of(povX + 1, row.y() + 2 + 16 - 2, 38, 1), -10825366);
               }

               int labelColor = povThis ? -10825366 : -6641998;
               String povFit = UiText.trimToWidthEllipsis(this.font, "POV", 36, fontId, labelColor);
               int povLabelW = UiText.width(this.font, povFit, fontId, labelColor);
               UiText.draw(graphics, this.font, povFit, fontId, labelColor, povX + Math.max(2, (40 - povLabelW) / 2), row.y() + 6, false);
            }

            String ping = snapshot.ping() >= 0 ? snapshot.ping() + "ms" : "--";
            int pingWidth = UiText.width(this.font, ping, fontId, color);
            int pingX = (showView ? povX : actionX) - 6 - pingWidth;
            UiText.draw(graphics, this.font, ping, fontId, color, pingX, row.y() + 6, false);
            int macroY = row.y() + 17;
            this.drawMacroRow(graphics, fontId, snapshot, 24, macroY, sessionW - 24);
            if (mouseX >= 16 && mouseX < 12 + sessionW - 4 && mouseY >= macroY - 1 && mouseY < macroY + 11) {
               this.macroRowTooltip = MultiMacroPresentation.tooltip(MultiManager.get(), snapshot);
            }

            if (row.guiExtra() > 0) {
               String gui = snapshot.openScreen();
               String guiLabel = snapshot.customMenuOpen() ? "GUI: CustomScreen" : "GUI: " + MultiManager.singleLine(gui == null ? "" : gui, 48);
               UiText.draw(
                  graphics,
                  this.font,
                  UiText.trimToWidthEllipsis(this.font, guiLabel, sessionW - 24, fontId, themeMuted()),
                  fontId,
                  themeMuted(),
                  24,
                  row.y() + 17 + 11,
                  false
               );
            }

            int nameX = 24;
            int nameMax = Math.max(1, pingX - nameX - 6);
            int nameColor = color == -42406 ? -42406 : themeText();
            String name = UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(snapshot.accountName(), 48), nameMax, fontId, nameColor);
            UiText.draw(graphics, this.font, name, fontId, nameColor, nameX, row.y() + 6, false);
            String actionLabel = MultiManager.isRetryable(snapshot.status()) ? "Retry" : "Stop";
            int actionFill = color & 16777215 | 1493172224;
            UiRenderer.frame(graphics, UiBounds.of(actionX, row.y() + 2, actionW, 16), actionFill, viewBorder);
            UiRenderer.rect(graphics, UiBounds.of(actionX + 1, row.y() + 2 + 16 - 2, actionW - 2, 1), color);
            int labelWidth = UiText.width(this.font, actionLabel, fontId, color);
            UiText.draw(graphics, this.font, actionLabel, fontId, color, actionX + Math.max(2, (actionW - labelWidth) / 2), row.y() + 6, false);
            if (this.detailsOpen) {
               int dx = 24;
               int dw = sessionW - 24;
               int dy = row.y() + 22 + 11 + row.guiExtra();
               this.drawStatsLine(graphics, fontId, snapshot, dx, dy);
               String held = snapshot.heldItem() != null && !snapshot.heldItem().isBlank() ? snapshot.heldItem() : "empty";
               this.drawDetailLine(graphics, fontId, "Held: " + held + "   Slot " + snapshot.hotbarSlot(), dx, dy + 12, dw);
               String dim = snapshot.dimension() != null && !snapshot.dimension().isBlank() ? snapshot.dimension() : "?";
               this.drawDetailLine(graphics, fontId, "World: " + dim, dx, dy + 23, dw);
               String pos = snapshot.hasPosition() ? String.format(Locale.ROOT, "Pos: %.1f  %.1f  %.1f", snapshot.x(), snapshot.y(), snapshot.z()) : "Pos: --";
               this.drawDetailLine(graphics, fontId, pos, dx, dy + 34, dw);
            }
         }
      }
   }

   private void drawMacroRow(GuiGraphicsExtractor graphics, Identifier fontId, MultiSession.Snapshot snapshot, int x, int y, int width) {
      MultiManager manager = MultiManager.get();
      String assignedName = MultiMacroPresentation.assignedName(manager, snapshot);
      String assigned = MultiMacroPresentation.assignedLabel(assignedName);
      long now = System.currentTimeMillis();
      String trailing = MultiMacroPresentation.queuedLabel(snapshot, now);
      int trailingColor = -1526710;
      if (trailing.isBlank()) {
         trailing = MultiMacroPresentation.playingLabel(MultiMacroPresentation.playingName(snapshot));
         trailingColor = -11013497;
      }

      if (trailing.isBlank()) {
         UiText.draw(graphics, this.font, UiText.trimToWidthEllipsis(this.font, assigned, width, fontId, -6641998), fontId, -6641998, x, y, false);
      } else {
         int trailingW = Math.min(width, UiText.width(this.font, trailing, fontId, trailingColor));
         int gap = 7;
         int assignedW = Math.max(0, width - trailingW - gap);
         int cx = x;
         if (assignedW > 12) {
            String fit = UiText.trimToWidthEllipsis(this.font, assigned, assignedW, fontId, -6641998);
            UiText.draw(graphics, this.font, fit, fontId, -6641998, x, y, false);
            cx = x + assignedW + gap;
         }

         String fitTrailing = UiText.trimToWidthEllipsis(this.font, trailing, Math.max(1, x + width - cx), fontId, trailingColor);
         UiText.draw(graphics, this.font, fitTrailing, fontId, trailingColor, cx, y, false);
      }
   }

   private static boolean rectHover(int[] rect, int mouseX, int mouseY) {
      return rect != null && MultiTooltip.hovered(rect[0], rect[1], rect[2], rect[3], mouseX, mouseY);
   }

   private int[] macroDelayLayout(int x, int width) {
      int gap = 6;
      int unitW = this.font.width("s") + 2;
      int labelW = Math.min(width / 2, UiText.width(this.font, "Delay between bots", THEME.fontFor(UiTone.BODY), themeMuted()) + 2);
      int boxW = Math.min(34, width - labelW - gap * 2 - unitW - 24);
      int trackX = x + labelW + gap;
      int trackW = (boxW >= 18 ? x + width - unitW - boxW - gap : x + width) - trackX;
      if (trackW < 12) {
         return new int[]{x, Math.max(1, width), 0, 0};
      } else {
         return boxW >= 18 ? new int[]{trackX, trackW, x + width - unitW - boxW, boxW} : new int[]{trackX, trackW, 0, 0};
      }
   }

   private RiptideChatField delayField() {
      if (this.delayField == null) {
         this.delayField = new RiptideChatField(this.minecraft, this.font, 0, 0, 10, 14, false);
         this.delayField.setMaxLength(4);
         this.delayField.setFilter(MultiMacroDelay::typable);
         this.delayField.setPlaceholder(Component.literal("0"));
         this.delayField.setChangedListener(this::typeMacroDelay);
      }

      return this.delayField;
   }

   private void typeMacroDelay(String text) {
      if (!this.syncingDelayField) {
         MultiMacroDelay.setMs(MultiMacroDelay.fromTyped(text, MultiMacroDelay.currentMs()));
      }
   }

   private void nudgeMacroDelay(int direction) {
      MultiMacroDelay.nudgeAndPersist(direction);
      this.setMacroDelayText(MultiMacroDelay.editText(MultiMacroDelay.currentMs()));
   }

   private void setMacroDelayText(String want) {
      if (this.delayField != null && !this.delayField.getText().equals(want)) {
         this.syncingDelayField = true;

         try {
            this.delayField.setText(want);
         } finally {
            this.syncingDelayField = false;
         }
      }
   }

   private void renderMacroDelay(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float delta) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      int delayMs = MultiMacroDelay.currentMs();
      int[] layout = this.macroDelayLayout(x, width);
      int trackX = layout[0];
      int trackW = layout[1];
      int boxX = layout[2];
      int boxW = layout[3];
      if (trackX > x) {
         UiText.draw(graphics, this.font, "Delay between bots", fontId, themeMuted(), x, y + 3, false);
      }

      boolean hovered = this.macroDelayDragging || mouseX >= trackX && mouseX < trackX + trackW && mouseY >= y && mouseY < y + 14;
      Slider.render(UiContexts.overlay(graphics, this.font, mouseX, mouseY), UiBounds.of(trackX, y, trackW, 14), MultiMacroDelay.ratio(delayMs), hovered);
      this.macroDelayTrack = new int[]{trackX, y, trackW, 14};
      this.macroDelayRow = new int[]{x, y, width, 14};
      if (boxW <= 0) {
         this.macroDelayBox = null;
         this.releaseMacroDelayBox();
      } else {
         RiptideChatField box = this.delayField();
         box.setX(boxX);
         box.setY(y);
         box.setWidth(boxW);
         box.setHeight(14);
         this.macroDelayBox = new int[]{boxX, y, boxW, 14};
         if (!box.isFocused()) {
            this.setMacroDelayText(MultiMacroDelay.editText(delayMs));
         }

         box.render(graphics, mouseX, mouseY, delta);
         UiText.draw(graphics, this.font, "s", fontId, themeMuted(), boxX + boxW + 2, y + 3, false);
         boolean focused = box.isFocused();
         if (this.delayFieldWasFocused && !focused) {
            MultiMacroDelay.persist();
         }

         this.delayFieldWasFocused = focused;
      }
   }

   private void releaseMacroDelayBox() {
      if (this.delayField != null && this.delayField.isFocused()) {
         this.delayField.setFocused(false);
      }

      if (this.delayFieldWasFocused) {
         MultiMacroDelay.persist();
      }

      this.delayFieldWasFocused = false;
   }

   private boolean macroDelayAt(double mouseX, double mouseY, boolean pressing) {
      int[] track = this.macroDelayTrack;
      if (track == null) {
         return false;
      } else if (!pressing || !(mouseX < track[0]) && !(mouseX >= track[0] + track[2]) && !(mouseY < track[1]) && !(mouseY >= track[1] + track[3])) {
         MultiMacroDelay.setMs(MultiMacroDelay.fromMouse(mouseX, track[0], track[2]));
         return true;
      } else {
         return false;
      }
   }

   private void drawDetailLine(GuiGraphicsExtractor graphics, Identifier fontId, String text, int x, int y, int width) {
      UiText.draw(graphics, this.font, UiText.trimToWidthEllipsis(this.font, text, width, fontId, themeMuted()), fontId, themeMuted(), x, y, false);
   }

   private void drawStatsLine(GuiGraphicsExtractor graphics, Identifier fontId, MultiSession.Snapshot snapshot, int x, int y) {
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_SPRITE, x, y - 1, 9, 9);
      int cx = x + 11;
      String hp = trimNumber(snapshot.health()) + "/" + trimNumber(snapshot.maxHealth());
      UiText.draw(graphics, this.font, hp, fontId, themeText(), cx, y, false);
      cx += UiText.width(this.font, hp, fontId, themeText()) + 10;
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_SPRITE, cx, y - 1, 9, 9);
      cx += 11;
      UiText.draw(graphics, this.font, snapshot.food() + "/20", fontId, themeText(), cx, y, false);
   }

   private static String trimNumber(float value) {
      return value == Math.rint(value) ? Integer.toString((int)value) : String.format(Locale.ROOT, "%.1f", value);
   }

   private static int statusColor(MultiSession.Snapshot snapshot) {
      return switch (snapshot.displayState(System.currentTimeMillis())) {
         case GREEN -> -11013497;
         case RED -> -42406;
         case YELLOW -> -866997;
      };
   }

   private void sendChat() {
      String value = this.chatInput.getValue();
      if (value == null || value.isBlank()) {
         String last = MultiManager.get().lastHistoryEntry();
         if (last == null || last.isBlank()) {
            this.resultText = "Empty input";
            this.resultColor = -6645094;
            return;
         }

         value = last;
      }

      Set<String> targets = (Set<String>)(!this.selectedIds.isEmpty() ? this.selectedIds : (this.viewingId != null ? Set.of(this.viewingId) : this.selectedIds));
      MultiManager.BroadcastResult result = MultiManager.get().broadcastConsole(value, targets);
      this.resultText = shortResult(result);
      this.resultColor = result.failed() > 0 ? -42149 : (result.skipped() > 0 ? -6645094 : -13248397);
      MultiManager.get().pushHistory(value);
      if (result.sent() > 0) {
         this.chatInput.setValue("");
      }

      this.historyIndex = -1;
      this.updateGhostSuggestion();
      this.clearSuggestions();
   }

   private void updateGhostSuggestion() {
      if (this.chatInput != null) {
         String last = this.chatInput.getValue().isEmpty() ? MultiManager.get().lastHistoryEntry() : null;
         this.chatInput.setSuggestion(last);
      }
   }

   private void sendMovement() {
      MultiManager.BroadcastResult result = MultiManager.get().broadcastMovementNow(this.actionScope());
      this.resultText = shortResult(result);
      this.resultColor = result.failed() > 0 ? -42149 : (result.skipped() > 0 ? -6645094 : -13248397);
   }

   private void sendQuickAction(int index) {
      MultiProfile profile = MultiManager.get().activeProfile();
      if (profile != null) {
         MultiQuickAction action = profile.quickAction(index);
         if (action.empty()) {
            this.openQuickEditor(index);
         } else {
            MultiManager.BroadcastResult result = MultiManager.get().broadcastQuickAction(action, this.actionScope());
            this.resultText = shortResult(result);
            this.resultColor = result.failed() > 0 ? -42149 : (result.skipped() > 0 ? -6645094 : -13248397);
         }
      }
   }

   private void openQuickEditor(int index) {
      MultiProfile profile = MultiManager.get().activeProfile();
      if (profile != null && this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideMultiQuickActionScreen(this, index, profile.quickAction(index), action -> {
            MultiManager.get().updateQuickAction(index, action);
            this.resultText = "Saved";
            this.resultColor = -13248397;
         }, action -> {
            MultiManager.BroadcastResult result = MultiManager.get().broadcastQuickAction(action, this.actionScope());
            this.resultText = shortResult(result);
            this.resultColor = result.failed() > 0 ? -42149 : (result.skipped() > 0 ? -6645094 : -13248397);
            return result;
         }));
      }
   }

   private void resetQuickActions() {
      MultiManager.get().resetQuickActions();
      this.resultText = "Reset";
      this.resultColor = -13248397;
      this.rebuildControls();
   }

   private void openPolicy() {
      MultiProfile profile = MultiManager.get().activeProfile();
      if (profile != null) {
         this.minecraft
            .gui
            .setScreen(
               new RiptideMultiPacketPolicyScreen(
                  this, profile.packetPolicy, MultiManager.get()::updatePolicy, profile.autoAccept, MultiManager.get()::updateAutoAccept, true
               )
            );
      }
   }

   public void tick() {
      super.tick();
      if (!MultiManager.get().isActive()) {
         if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
         }
      } else {
         if (this.viewingId != null && this.findSnapshot(this.viewingId) == null) {
            this.exitView();
         }

         long uiRevision = MultiManager.get().uiRevision();
         if (uiRevision != this.lastUiRevision) {
            this.rebuildControls();
         } else {
            long sessionRevision = MultiManager.get().sessionRevision();
            if (sessionRevision != this.lastSessionRevision) {
               this.lastSessionRevision = sessionRevision;
               this.addSessionButtons();
            }
         }

         this.refreshSessionLayoutIfNeeded();
         this.refreshSuggestions();
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.menuInput.rename.focused()) {
         if (this.menuInput.rename.keyPressed(event.key())) {
            this.sendRename();
            return true;
         } else {
            return true;
         }
      } else if (event.key() != 67
         || !event.hasControlDown()
         || !this.chatSel.hasSelection()
         || this.chatInput != null && this.chatInput.isFocused()
         || this.delayField != null && this.delayField.isFocused()) {
         if (event.key() == 256 && this.chatSel.hasSelection()) {
            this.chatSel.clear();
            return true;
         } else if (this.isViewing() && event.key() == 256) {
            this.exitView();
            return true;
         } else {
            boolean chatFocused = this.chatInput != null && this.chatInput.isFocused();
            if (this.isViewing() && !chatFocused && this.hoveredViewHandler >= 0 && this.minecraft != null && this.minecraft.options.keyDrop.matches(event)) {
               if (this.cachedView != null && this.cachedView.interactive()) {
                  MultiClientCommands.ClickSpec spec = MultiClientCommands.dropSpec(event.hasControlDown());
                  if (this.sharedView) {
                     this.applyFanout(MultiManager.get().clickBotSlots(this.sharedIds(), this.hoveredViewHandler, spec));
                  } else {
                     MultiManager.get().clickBotSlot(this.viewingId, this.hoveredViewHandler, spec);
                  }

                  return true;
               } else {
                  this.resultText = this.cachedView != null && this.cachedView.synchronizationBlocked()
                     ? "Inventory is waiting for a server update"
                     : "Inventory is synchronizing";
                  this.resultColor = -6645094;
                  return true;
               }
            } else if (this.isViewing() && !chatFocused && this.hoveredViewHotbar >= 0 && (event.key() == 85 || event.key() == 73)) {
               if (this.cachedView != null && this.cachedView.interactive()) {
                  boolean use = event.key() == 73;
                  if (this.sharedView) {
                     this.applyFanout(
                        use
                           ? MultiManager.get().useBotHotbars(this.sharedIds(), this.hoveredViewHotbar)
                           : MultiManager.get().selectBotHotbars(this.sharedIds(), this.hoveredViewHotbar)
                     );
                  } else {
                     String result = use
                        ? MultiManager.get().useBotHotbar(this.viewingId, this.hoveredViewHotbar)
                        : MultiManager.get().selectBotHotbar(this.viewingId, this.hoveredViewHotbar);
                     if (!"Sent".equals(result)) {
                        this.resultText = result;
                        this.resultColor = -6645094;
                     }
                  }

                  return true;
               } else {
                  this.resultText = this.cachedView != null && this.cachedView.synchronizationBlocked()
                     ? "Inventory is waiting for a server update"
                     : "Inventory is synchronizing";
                  this.resultColor = -6645094;
                  return true;
               }
            } else if (this.delayField != null && this.delayField.keyPressed(event)) {
               return true;
            } else {
               if (this.chatInput != null && this.chatInput.isFocused()) {
                  switch (event.key()) {
                     case 256:
                        if (!this.suggestions.isEmpty()) {
                           this.clearSuggestions();
                           return true;
                        }
                        break;
                     case 257:
                     case 335:
                        this.sendChat();
                        return true;
                     case 258:
                        this.cycleSuggestion((event.modifiers() & 1) != 0);
                        return true;
                     case 264:
                        this.recallHistory(1);
                        return true;
                     case 265:
                        this.recallHistory(-1);
                        return true;
                  }
               }

               return super.keyPressed(event);
            }
         }
      } else {
         this.copyChatSelection();
         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.menuInput.rename.focused()) {
         if (this.menuInput.rename.charTyped((char)input.codepoint())) {
            this.sendRename();
         }

         return true;
      } else {
         return this.delayField != null && this.delayField.charTyped(input) ? true : super.charTyped(input);
      }
   }

   private void recallHistory(int direction) {
      List<String> history = MultiManager.get().commandHistory();
      if (!history.isEmpty() && this.chatInput != null) {
         if (direction < 0) {
            if (this.historyIndex == -1) {
               this.historyDraft = this.chatInput.getValue();
               this.historyIndex = history.size() - 1;
            } else if (this.historyIndex > 0) {
               this.historyIndex--;
            }
         } else {
            if (this.historyIndex == -1) {
               return;
            }

            this.historyIndex++;
            if (this.historyIndex >= history.size()) {
               this.historyIndex = -1;
               this.setInput(this.historyDraft);
               return;
            }
         }

         this.setInput(history.get(this.historyIndex));
      }
   }

   private void setInput(String value) {
      this.chatInput.setValue(value);
      this.chatInput.moveCursorToEnd(false);
      this.clearSuggestions();
   }

   private boolean cycleSuggestion(boolean backwards) {
      if (!this.suggestionsFresh()) {
         return false;
      } else {
         if (this.appliedOnce) {
            this.suggestionIndex = Math.floorMod(this.suggestionIndex + (backwards ? -1 : 1), this.suggestions.size());
         }

         this.appliedOnce = true;
         this.frozen = true;
         this.applySuggestion(this.suggestionIndex);
         return true;
      }
   }

   private boolean suggestionsFresh() {
      if (!this.suggestions.isEmpty() && this.chatInput != null) {
         String value = this.chatInput.getValue();
         return value.equals(this.suggestOriginal) || value.equals(this.expectedValue);
      } else {
         return false;
      }
   }

   private void applySuggestion(int index) {
      if (this.chatInput != null && this.suggestOriginal != null && index >= 0 && index < this.suggestions.size()) {
         int start = Math.max(0, Math.min(this.suggestStart, this.suggestOriginal.length()));
         int end = Math.max(start, Math.min(this.suggestStart + this.suggestLength, this.suggestOriginal.length()));
         String entry = this.suggestions.get(index);
         String full = this.suggestOriginal.substring(0, start) + entry + this.suggestOriginal.substring(end);
         this.chatInput.setValue(full);
         int caret = Math.min(start + entry.length(), full.length());
         this.chatInput.setCursorPosition(caret);
         this.chatInput.setHighlightPos(caret);
         this.expectedValue = full;
      }
   }

   private void clearSuggestions() {
      this.suggestions.clear();
      this.suggestRects.clear();
      this.suggestionIndex = 0;
      this.suggestForText = null;
      this.suggestOriginal = null;
      this.expectedValue = null;
      this.appliedOnce = false;
      this.frozen = false;
   }

   private void refreshSuggestions() {
      if (this.chatInput != null) {
         String value = this.chatInput.getValue();
         if (!this.chatInput.isFocused()) {
            if (!this.suggestions.isEmpty() || this.suggestForText != null) {
               this.clearSuggestions();
            }

            this.lastRequestedCmd = null;
         } else {
            if (this.frozen) {
               if (value.equals(this.expectedValue)) {
                  return;
               }

               this.frozen = false;
            }

            if (!value.equals(this.suggestForText)) {
               if (value.startsWith("/")) {
                  this.refreshServerSuggestions(value);
               } else if (RiptideCommands.isRiptideCommandMessage(value)) {
                  this.refreshClientSuggestions(value);
               } else {
                  if (!this.suggestions.isEmpty() || this.suggestForText != null) {
                     this.clearSuggestions();
                  }

                  this.lastRequestedCmd = null;
               }
            }
         }
      }
   }

   private void refreshServerSuggestions(String value) {
      String stripped = value.substring(1);
      long now = System.currentTimeMillis();
      if (!value.equals(this.lastRequestedCmd) && now - this.lastRequestAt >= 60L) {
         MultiManager.get().requestSuggestions(stripped, this.actionScope());
         this.lastRequestedCmd = value;
         this.lastRequestAt = now;
      }

      MultiManager.SuggestionResult result = MultiManager.get().suggestions(stripped);
      if (result != null) {
         this.applyResult(value, 1 + result.start(), result.length(), result.entries());
      }
   }

   private void refreshClientSuggestions(String value) {
      this.lastRequestedCmd = null;
      MultiChatCompletion.Result r = MultiChatCompletion.clientSuggestions(value);
      if (r != null) {
         this.applyResult(value, r.start(), r.length(), r.entries());
      }
   }

   private void applyResult(String value, int absStart, int length, List<String> entries) {
      if (!value.equals(this.suggestForText)) {
         this.suggestionIndex = 0;
         this.appliedOnce = false;
      }

      this.suggestForText = value;
      this.suggestOriginal = value;
      this.suggestStart = absStart;
      this.suggestLength = length;
      this.suggestions.clear();
      this.suggestions.addAll(entries);
      if (this.suggestionIndex >= this.suggestions.size()) {
         this.suggestionIndex = Math.max(0, this.suggestions.size() - 1);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0 && this.suggestionsFresh()) {
         for (int[] rect : this.suggestRects) {
            if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2] && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]) {
               this.suggestionIndex = rect[4];
               this.appliedOnce = true;
               this.frozen = true;
               this.applySuggestion(this.suggestionIndex);
               if (this.chatInput != null) {
                  this.chatInput.setFocused(true);
               }

               return true;
            }
         }
      }

      if (this.sharedView && virtualEvent.button() == 0) {
         for (int[] rectx : this.sharedTabRects) {
            if (virtualEvent.x() >= rectx[0]
               && virtualEvent.x() < rectx[0] + rectx[2]
               && virtualEvent.y() >= rectx[1]
               && virtualEvent.y() < rectx[1] + rectx[3]
               && rectx[4] < this.sharedGroups.size()) {
               this.sharedKey = this.sharedGroups.get(rectx[4]).key();
               this.viewScroll = 0;
               this.cachedViewId = null;
               return true;
            }
         }
      }

      if (this.isViewing()) {
         if (virtualEvent.button() == 0) {
            for (MultiMenuRenderer.MenuHit hit : this.viewWidgetHits) {
               if (!(virtualEvent.x() < hit.x())
                  && !(virtualEvent.x() >= hit.x() + hit.w())
                  && !(virtualEvent.y() < hit.y())
                  && !(virtualEvent.y() >= hit.y() + hit.h())) {
                  this.clearInputFocus();
                  this.dispatchViewWidget(hit.action());
                  return true;
               }
            }
         }

         for (int[] rectxx : this.viewSlotRects) {
            if (virtualEvent.x() >= rectxx[0] && virtualEvent.x() < rectxx[0] + 18 && virtualEvent.y() >= rectxx[1] && virtualEvent.y() < rectxx[1] + 18) {
               this.clearInputFocus();
               this.handleViewClick(rectxx[2], virtualEvent.button(), virtualEvent.hasShiftDown(), virtualEvent.hasControlDown());
               return true;
            }
         }

         if (virtualEvent.x() >= this.viewGridX
            && virtualEvent.x() < this.viewGridX + this.viewGridW
            && virtualEvent.y() >= this.viewGridY
            && virtualEvent.y() < this.viewGridY + this.viewGridH) {
            this.clearInputFocus();
            return true;
         }
      }

      if (virtualEvent.button() == 1) {
         for (int[] rectxxx : this.presetRects) {
            if (virtualEvent.x() >= rectxxx[0]
               && virtualEvent.x() < rectxxx[0] + rectxxx[2]
               && virtualEvent.y() >= rectxxx[1]
               && virtualEvent.y() < rectxxx[1] + rectxxx[3]) {
               this.openQuickEditor(rectxxx[4]);
               return true;
            }
         }
      }

      if (virtualEvent.button() == 0
         && this.macroDelayBox != null
         && this.delayField != null
         && this.delayField.mouseClicked(virtualEvent.x(), virtualEvent.y(), 0)) {
         if (this.chatInput != null) {
            this.chatInput.setFocused(false);
         }

         this.setFocused(null);
         return true;
      } else if (virtualEvent.button() == 0 && this.macroDelayAt(virtualEvent.x(), virtualEvent.y(), true)) {
         this.macroDelayDragging = true;
         return true;
      } else {
         if (virtualEvent.button() == 0) {
            for (RiptideMultiConsoleScreen.SessionRow row : this.sessionRows) {
               int ax = this.actionX();
               int ay = row.y() + 2;
               if (virtualEvent.x() >= ax && virtualEvent.x() < ax + this.actionWidth() && virtualEvent.y() >= ay && virtualEvent.y() < ay + 16) {
                  this.triggerSessionAction(row.id());
                  return true;
               }

               int viewX = ax - 6 - 40;
               if (this.sessionWidth() >= 220
                  && virtualEvent.x() >= viewX
                  && virtualEvent.x() < viewX + 40
                  && virtualEvent.y() >= ay
                  && virtualEvent.y() < ay + 16) {
                  if (row.id().equals(this.viewingId)) {
                     this.exitView();
                  } else {
                     this.enterView(row.id());
                  }

                  return true;
               }

               boolean povShown = this.sessionWidth() >= 220 && (MultiTakeoverState.isActive(row.id()) || MultiTakeoverState.available(row.id()));
               int povX = viewX - 6 - 40;
               if (povShown && virtualEvent.x() >= povX && virtualEvent.x() < povX + 40 && virtualEvent.y() >= ay && virtualEvent.y() < ay + 16) {
                  MultiTakeoverState.toggle(row.id(), this);
                  return true;
               }

               if (virtualEvent.x() >= 16.0
                  && virtualEvent.x() < 12 + this.sessionWidth() - 4
                  && virtualEvent.y() >= row.y()
                  && virtualEvent.y() < row.y() + this.rowFrameHeight() + 11 + row.guiExtra()) {
                  this.selectRow(row.id(), virtualEvent.hasControlDown(), virtualEvent.hasShiftDown());
                  return true;
               }
            }

            if (this.chatScrollbar != null && this.chatScrollbar.hasScroll() && this.chatScrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
               this.chatScrollbarDragging = true;
               this.chatScrollbarGrab = this.chatScrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
                  ? (int)Math.round(virtualEvent.y()) - this.chatScrollbar.thumbY()
                  : this.chatScrollbar.thumbHeight() / 2;
               this.chatScrollbarDrag(virtualEvent.y());
               return true;
            }

            if (this.handleChatClick(virtualEvent.x(), virtualEvent.y())) {
               return true;
            }

            if (this.beginChatSelection(virtualEvent.x(), virtualEvent.y())) {
               return true;
            }
         }

         boolean handled = super.mouseClicked(virtualEvent, doubled);
         if (!handled && virtualEvent.button() == 0) {
            this.clearInputFocus();
            if (!this.selectedIds.isEmpty()) {
               this.chatScroll = 0;
            }

            this.selectedIds.clear();
            this.anchorId = null;
            return true;
         } else {
            return handled;
         }
      }
   }

   private void selectRow(String id, boolean ctrl, boolean shift) {
      LinkedHashSet<String> before = new LinkedHashSet<>(this.selectedIds);
      List<String> ordered = this.orderedIds();
      if (shift && this.anchorId != null && ordered.contains(this.anchorId) && ordered.contains(id)) {
         int a = ordered.indexOf(this.anchorId);
         int b = ordered.indexOf(id);
         this.selectedIds.clear();

         for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            this.selectedIds.add(ordered.get(i));
         }
      } else if (ctrl) {
         if (!this.selectedIds.remove(id)) {
            this.selectedIds.add(id);
         }

         this.anchorId = id;
      } else {
         boolean only = this.selectedIds.size() == 1 && this.selectedIds.contains(id);
         this.selectedIds.clear();
         if (!only) {
            this.selectedIds.add(id);
            this.anchorId = id;
         } else {
            this.anchorId = null;
         }
      }

      if (!before.equals(this.selectedIds)) {
         this.chatScroll = 0;
      }
   }

   private List<String> orderedIds() {
      List<String> ids = new ArrayList<>();

      for (MultiSession.Snapshot s : MultiManager.get().snapshots()) {
         ids.add(s.accountId());
      }

      return ids;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.delayField != null && this.delayField.isFocused() && this.delayField.mouseReleased(virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
         return true;
      } else if (this.macroDelayDragging) {
         this.macroDelayDragging = false;
         MultiMacroDelay.persist();
         return true;
      } else if (this.chatScrollbarDragging) {
         this.chatScrollbarDragging = false;
         return true;
      } else if (this.chatSelectingPress) {
         this.chatSelectingPress = false;
         this.chatSel.finishDrag();
         return true;
      } else {
         return super.mouseReleased(virtualEvent);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.macroDelayDragging) {
         this.macroDelayAt(virtualEvent.x(), virtualEvent.y(), false);
         return true;
      } else if (this.delayField != null
         && this.delayField.isFocused()
         && this.delayField.mouseDragged(virtualEvent.x(), virtualEvent.y(), virtualEvent.button(), dragX, dragY)) {
         return true;
      } else if (this.chatScrollbarDragging) {
         this.chatScrollbarDrag(virtualEvent.y());
         return true;
      } else if (this.chatSelectingPress) {
         this.chatSelectAt(virtualEvent.x(), virtualEvent.y(), false);
         return true;
      } else {
         return super.mouseDragged(virtualEvent, RiptideUiScale.toVirtual(dragX), RiptideUiScale.toVirtual(dragY));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      double vx = RiptideUiScale.toVirtual(mouseX);
      double vy = RiptideUiScale.toVirtual(mouseY);
      if (vx < 12 + this.sessionWidth()) {
         this.sessionScroll = Math.max(0, this.sessionScroll + (vertical < 0.0 ? 1 : -1));
         this.addSessionButtons();
         return true;
      } else {
         int[] delayRow = this.macroDelayRow;
         if (delayRow != null && vx >= delayRow[0] && vx < delayRow[0] + delayRow[2] && vy >= delayRow[1] && vy < delayRow[1] + delayRow[3]) {
            this.nudgeMacroDelay(vertical > 0.0 ? 1 : -1);
            return true;
         } else if (this.isViewing() && vy < this.chatTop) {
            this.viewScroll = Math.max(0, this.viewScroll + (vertical < 0.0 ? 18 : -18));
            return true;
         } else {
            this.chatScroll = Math.max(0, this.chatScroll + (vertical > 0.0 ? 1 : -1));
            return true;
         }
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), themeBg());
         int sessionW = this.sessionWidth();
         UiRenderer.frame(graphics, UiBounds.of(12, 14, sessionW, this.screenHeight() - 58), themePanelSoft(), themeBorder());
         int consoleX = this.consoleX();
         int consoleW = this.consoleWidth();
         UiRenderer.frame(graphics, UiBounds.of(consoleX, 14, consoleW, this.screenHeight() - 58), themePanel(), themeBorder());
         List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();
         this.frameSnapshots.clear();
         int ready = 0;

         for (MultiSession.Snapshot s : snapshots) {
            this.frameSnapshots.put(s.accountId(), s);
            if (s.ready()) {
               ready++;
            }
         }

         this.drawFitted(graphics, "Sessions  " + ready + "/" + snapshots.size() + " ready", 20, 24, Math.max(1, sessionW - 84), themeText());
         MultiSession.Snapshot viewed = this.selectedIds.size() == 1 ? this.findSnapshot(this.selectedIds.iterator().next()) : null;
         if (viewed != null) {
            String detail = UiText.trimToWidthEllipsis(this.font, detailLabel(viewed), Math.max(1, sessionW - 16), THEME.fontFor(UiTone.BODY), themeMuted());
            boolean up = viewed.displayState(System.currentTimeMillis()) == MultiSession.DisplayState.GREEN;
            this.drawText(graphics, detail, 20, 35, up ? themeMuted() : statusColor(viewed));
         } else if (!this.selectedIds.isEmpty()) {
            this.drawText(graphics, this.selectedIds.size() + " selected", 20, 35, themeMuted());
         } else if (snapshots.isEmpty()) {
            this.drawText(graphics, "Starting sessions...", 20, 66, themeMuted());
         }

         if (this.sharedView) {
            this.sharedGroups = MultiSharedGui.groups();
         }

         String viewTarget = this.viewTargetId();
         boolean viewing = viewTarget != null && this.findSnapshot(viewTarget) != null;
         if (viewing) {
            this.refreshView(viewTarget);
         }

         if (viewing && this.cachedView != null) {
            RiptideUiScale.enableOverlayScissor(graphics, consoleX + 10, 22, consoleX + consoleW - 72, 36);
            Component viewTitle = this.cachedView.title();
            graphics.text(this.font, viewTitle.getVisualOrderText(), consoleX + 10, 24, themeText(), false);
            graphics.disableScissor();
         } else {
            this.drawFitted(graphics, "Console", consoleX + 10, 24, consoleW - 20, themeText());
         }

         if (viewing) {
            this.macroDelayTrack = this.macroDelayRow = this.macroDelayBox = null;
            this.releaseMacroDelayBox();
            int gridTop = 118;
            if (this.sharedView) {
               this.renderSharedTabs(graphics, consoleX + 10, gridTop, consoleW - 20, virtualMouseX, virtualMouseY);
               gridTop += 20;
            }

            int bottomLimit = this.screenHeight() - 48;
            int available = bottomLimit - gridTop;
            int gridMaxH = Math.max(36, available - 66 - 12);
            this.renderGuiView(graphics, consoleX + 10, gridTop, consoleW - 20, gridMaxH, virtualMouseX, virtualMouseY);
            this.infoColumnTop = gridTop + 2;
            int chatTop = gridTop + Math.max(12, this.viewGridH) + 12;
            this.renderChat(graphics, consoleX + 10, chatTop, consoleW - 20, Math.max(0, bottomLimit - chatTop));
         } else {
            this.sharedTabRects.clear();
            if (this.sharedView) {
               this.drawFitted(graphics, "No bot has a GUI open yet.", consoleX + 10, 120, consoleW - 20, themeMuted());
            }

            this.renderMacroDelay(graphics, consoleX + 10, 136, consoleW - 20, virtualMouseX, virtualMouseY, delta);
            int chatTop = 156;
            this.renderChat(graphics, consoleX + 10, chatTop, consoleW - 20, Math.max(0, this.screenHeight() - 44 - chatTop));
         }

         if (!this.resultText.isBlank()) {
            this.drawFitted(graphics, this.resultText, consoleX + 10, this.screenHeight() - 50, consoleW - 20, themeStatusColor(this.resultColor));
         }

         this.renderSessionRows(graphics, virtualMouseX, virtualMouseY);
         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
         this.renderSuggestions(graphics, virtualMouseX, virtualMouseY);
         if (viewing) {
            ItemStack carried = this.cachedView != null ? this.cachedView.carried() : ItemStack.EMPTY;
            if (carried != null && !carried.isEmpty()) {
               graphics.nextStratum();
               graphics.item(carried, virtualMouseX - 8, virtualMouseY - 8);
               graphics.itemDecorations(this.font, carried, virtualMouseX - 8, virtualMouseY - 8);
            } else if (!this.hoveredViewStack.isEmpty()) {
               this.drawItemTooltip(graphics, this.hoveredViewStack, this.hoveredViewX, this.hoveredViewY);
            }

            if (this.cachedView != null && this.viewGridW > 0) {
               graphics.nextStratum();
               int colW = this.font.width("SyncID: 000000") + 6;
               int infoX = consoleX + consoleW - 10 - colW;
               if (infoX >= consoleX + 10 + this.viewGridW + 8) {
                  this.drawMetric(
                     graphics,
                     infoX,
                     this.infoColumnTop,
                     colW,
                     "Rev: ",
                     Integer.toString(this.cachedView.stateId()),
                     RiptideTheme.recolor(-46518, RiptideTheme.Channel.ACCENT)
                  );
                  this.drawMetric(
                     graphics,
                     infoX,
                     this.infoColumnTop + 11,
                     colW,
                     "SyncID: ",
                     Integer.toString(this.cachedView.syncId()),
                     RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT)
                  );
                  int visibleSlot = this.hoveredViewHandler >= 0 ? MultiManager.get().visibleSlotForHandler(this.viewTargetId(), this.hoveredViewHandler) : -1;
                  this.drawMetric(
                     graphics,
                     infoX,
                     this.infoColumnTop + 22,
                     colW,
                     "Slot: ",
                     visibleSlot >= 0 ? Integer.toString(visibleSlot) : "--",
                     RiptideTheme.recolor(-7350273, RiptideTheme.Channel.ACCENT)
                  );
               }
            }
         }

         if (RiptideConfig.getGlobal().multiShowTooltips) {
            String tip = null;
            if (rectHover(this.closeSilentRect, virtualMouseX, virtualMouseY)) {
               tip = "Hide GUI locally, keep it open";
            } else if (rectHover(this.sharedGuiRect, virtualMouseX, virtualMouseY)) {
               tip = "Control every bot's GUI at once";
            } else if (rectHover(this.detailsRect, virtualMouseX, virtualMouseY)) {
               tip = "Show HP, food, and position";
            } else if (this.macroRowTooltip != null) {
               tip = this.macroRowTooltip;
            }

            if (tip != null) {
               MultiTooltip.render(graphics, this.font, tip, virtualMouseX, virtualMouseY);
            }
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      this.suggestRects.clear();
      if (!this.suggestions.isEmpty() && this.chatInput != null && this.chatInput.isFocused()) {
         Identifier fontId = THEME.fontFor(UiTone.BODY);
         int rowH = 12;
         int visible = Math.min(8, this.suggestions.size());
         int start = Math.max(0, Math.min(this.suggestionIndex - visible + 1, this.suggestions.size() - visible));
         int textW = 40;

         for (int i = 0; i < visible; i++) {
            textW = Math.max(textW, this.font.width(this.suggestions.get(start + i)));
         }

         int popupW = Math.min(240, textW + 10);
         int popupH = visible * rowH + 2;
         int caretIndex = Math.min(this.suggestStart, this.chatInput.getValue().length());
         int anchorX = this.chatInput.getScreenX(caretIndex) - 3;
         int minX = this.consoleX() + 10;
         int maxX = Math.max(minX, this.screenWidth() - 12 - popupW);
         int popupX = Math.max(minX, Math.min(anchorX, maxX));
         int popupY = Math.max(20, this.chatInput.getY() - popupH - 2);
         UiRenderer.frame(graphics, UiBounds.of(popupX, popupY, popupW, popupH), -301266161, themeBorder());

         for (int i = 0; i < visible; i++) {
            int idx = start + i;
            int ry = popupY + 1 + i * rowH;
            boolean hover = mouseX >= popupX && mouseX < popupX + popupW && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = idx == this.suggestionIndex;
            if (sel || hover) {
               UiRenderer.rect(graphics, UiBounds.of(popupX + 1, ry, popupW - 2, rowH), sel ? 1714322943 : 872415231);
            }

            int color = sel ? -1 : themeMuted();
            String label = UiText.trimToWidthEllipsis(this.font, this.suggestions.get(idx), popupW - 8, fontId, color);
            UiText.draw(graphics, this.font, label, fontId, color, popupX + 4, ry + 2, false);
            this.suggestRects.add(new int[]{popupX + 1, ry, popupW - 2, rowH, idx});
         }
      }
   }

   private void refreshView(String target) {
      MultiSession.Snapshot snap = this.frameSnapshots.get(target);
      long rev = snap != null ? snap.menuRevision() : -1L;
      if (!target.equals(this.cachedViewId) || rev != this.cachedViewRev) {
         this.cachedView = MultiManager.get().menuView(target);
         this.cachedViewId = target;
         this.cachedViewRev = rev;
      }
   }

   private void renderSharedTabs(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
      this.sharedTabRects.clear();
      MultiSharedGui.Group current = MultiSharedGui.pick(this.sharedGroups, this.sharedKey);
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      int cx = x;

      for (int i = 0; i < this.sharedGroups.size(); i++) {
         MultiSharedGui.Group group = this.sharedGroups.get(i);
         int room = x + w - cx;
         if (room < 40) {
            UiText.draw(graphics, this.font, "+" + (this.sharedGroups.size() - i), fontId, themeMuted(), cx, y + 4, false);
            break;
         }

         boolean selected = current != null && group.key().equals(current.key());
         String label = UiText.trimToWidthEllipsis(this.font, group.label(), Math.min(150, room - 10), fontId, themeText());
         int bw = UiText.width(this.font, label, fontId, themeText()) + 10;
         boolean hover = mouseX >= cx && mouseX < cx + bw && mouseY >= y && mouseY < y + 16;
         UiRenderer.frame(
            graphics, UiBounds.of(cx, y, bw, 16), selected ? 859166835 : (hover ? 872415231 : 705827348), selected ? themeSuccess() : themeBorder()
         );
         UiText.draw(graphics, this.font, label, fontId, selected ? themeText() : themeMuted(), cx + 5, y + 4, false);
         this.sharedTabRects.add(new int[]{cx, y, bw, 16, i});
         cx += bw + 4;
      }
   }

   private void renderGuiView(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
      this.viewSlotRects.clear();
      MultiSession.MenuView view = this.cachedView;
      if (view == null) {
         this.viewGridX = x;
         this.viewGridY = y;
         this.viewGridW = 0;
         this.viewGridH = 0;
         this.hoveredViewStack = ItemStack.EMPTY;
         this.hoveredViewHandler = -1;
         this.hoveredViewHotbar = -1;
         this.drawFitted(graphics, "Loading...", x, y, w, themeMuted());
      } else {
         this.viewWidgetHits.clear();
         this.menuInput.sync(view.extras() == null ? "" : view.extras().typeId());
         int[] cs = MultiMenuRenderer.contentSize(view);
         int contentW = Math.max(18, cs[0]);
         int contentH = Math.max(18, cs[1]);
         int panelW = Math.min(w, contentW);
         int panelH = Math.min(h, contentH);
         this.viewScroll = Math.max(0, Math.min(this.viewScroll, Math.max(0, contentH - panelH)));
         this.viewGridX = x;
         this.viewGridY = y;
         this.viewGridW = panelW;
         this.viewGridH = panelH;
         RiptideUiScale.enableOverlayScissor(graphics, x - 3, y - 3, x + panelW + 3, y + panelH + 3);
         UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, panelW + 6, panelH + 6), -3750202);
         int selectedHandler = MultiManager.get().selectedHotbarHandler(this.viewTargetId());
         MultiSession.ViewSlot hovered = null;

         for (MultiSession.ViewSlot slot : view.slots()) {
            int sx = x + slot.x();
            int sy = y + slot.y() - this.viewScroll;
            if (sy + 18 >= y && sy <= y + panelH) {
               this.drawSlotCell(graphics, sx, sy);
               if (selectedHandler >= 0 && slot.handler() == selectedHandler) {
                  UiRenderer.frame(graphics, UiBounds.of(sx, sy, 18, 18), 452935744, -1325449152);
               }

               ItemStack stack = slot.item();
               if (stack != null && !stack.isEmpty()) {
                  try {
                     graphics.item(stack, sx + 1, sy + 1);
                     graphics.itemDecorations(this.font, stack, sx + 1, sy + 1);
                  } catch (Throwable var22) {
                  }
               }

               this.viewSlotRects.add(new int[]{sx, sy, slot.handler()});
               if (mouseX >= sx
                  && mouseX < sx + 18
                  && mouseY >= sy
                  && mouseY < sy + 18
                  && mouseX >= x
                  && mouseX < x + panelW
                  && mouseY >= y
                  && mouseY < y + panelH) {
                  hovered = slot;
               }
            }
         }

         MultiMenuRenderer.render(graphics, this.font, view, x, y, this.viewScroll, mouseX, mouseY, this.viewWidgetHits, this.menuInput);
         graphics.disableScissor();
         this.hoveredViewStack = hovered != null && hovered.item() != null ? hovered.item() : ItemStack.EMPTY;
         this.hoveredViewHandler = hovered != null ? hovered.handler() : -1;
         this.hoveredViewHotbar = hovered != null ? MultiManager.get().hotbarIndexForHandler(this.viewTargetId(), hovered.handler()) : -1;
         this.hoveredViewX = mouseX;
         this.hoveredViewY = mouseY;
      }
   }

   private void drawMetric(GuiGraphicsExtractor graphics, int x, int y, int w, String key, String value, int valueColor) {
      int keyColor = RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
      graphics.text(this.font, Component.literal(key).getVisualOrderText(), x, y, keyColor, false);
      int vx = x + this.font.width(key);
      String fit = UiText.trimToWidthEllipsis(this.font, value, Math.max(1, x + w - vx), THEME.fontFor(UiTone.BODY), valueColor);
      graphics.text(this.font, Component.literal(fit).getVisualOrderText(), vx, y, valueColor, false);
   }

   private void drawSlotCell(GuiGraphicsExtractor graphics, int cx, int cy) {
      UiRenderer.rect(graphics, UiBounds.of(cx, cy, 18, 18), -7631989);
      UiRenderer.rect(graphics, UiBounds.of(cx, cy, 18, 1), -13158601);
      UiRenderer.rect(graphics, UiBounds.of(cx, cy, 1, 18), -13158601);
      UiRenderer.rect(graphics, UiBounds.of(cx, cy + 17, 18, 1), -1);
      UiRenderer.rect(graphics, UiBounds.of(cx + 17, cy, 1, 18), -1);
   }

   private void drawItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mx, int my) {
      try {
         List<Component> base = Screen.getTooltipFromItem(this.minecraft, stack);
         if (base == null || base.isEmpty()) {
            return;
         }

         List<Component> lines = base;
         if (this.hoveredViewHotbar >= 0) {
            lines = new ArrayList<>(base);
            lines.add(Component.literal("[U] switch to slot"));
            lines.add(Component.literal("[I] switch + use item"));
         }

         int tw = 0;

         for (Component c : lines) {
            tw = Math.max(tw, this.font.width(c));
         }

         int th = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
         int tx = mx + 12;
         int ty = my - 12;
         if (tx + tw + 4 > this.screenWidth()) {
            tx = Math.max(4, mx - tw - 16);
         }

         if (ty + th + 4 > this.screenHeight()) {
            ty = this.screenHeight() - th - 4;
         }

         if (ty < 4) {
            ty = 4;
         }

         graphics.nextStratum();
         UiRenderer.rect(graphics, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), -267386864);
         UiRenderer.frame(graphics, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0, 1347420320);
         int yy = ty;

         for (Component c : lines) {
            graphics.text(this.font, c.getVisualOrderText(), tx, yy, -1, true);
            yy += 10;
         }
      } catch (Throwable var14) {
      }
   }

   private ItemStack viewStackAt(int handler) {
      if (this.cachedView == null) {
         return ItemStack.EMPTY;
      } else {
         for (MultiSession.ViewSlot slot : this.cachedView.slots()) {
            if (slot.handler() == handler) {
               return slot.item();
            }
         }

         return ItemStack.EMPTY;
      }
   }

   private void handleViewClick(int handler, int button, boolean shift, boolean ctrl) {
      if (handler >= 0) {
         ItemStack stack = this.viewStackAt(handler);
         if (button == 1 && ctrl && shift) {
            if (stack != null && !stack.isEmpty()) {
               this.openNbt(stack);
            }
         } else if (this.cachedView != null && this.cachedView.interactive()) {
            MultiClientCommands.ClickSpec spec = MultiClientCommands.fromMouse(button, shift, ctrl);
            if (this.sharedView) {
               this.applyFanout(MultiManager.get().clickBotSlots(this.sharedIds(), handler, spec));
            } else {
               String result = MultiManager.get().clickBotSlot(this.viewingId, handler, spec);
               if (!"Sent".equals(result)) {
                  this.resultText = result;
                  this.resultColor = -6645094;
               }
            }
         } else {
            this.resultText = this.cachedView != null && this.cachedView.synchronizationBlocked()
               ? "Inventory is waiting for a server update"
               : "Inventory is synchronizing";
            this.resultColor = -6645094;
         }
      }
   }

   private void dispatchViewWidget(MultiMenuRenderer.MenuAction action) {
      if (action instanceof MultiMenuRenderer.RenameFocusAct) {
         this.menuInput.rename.focus();
         this.menuInput.rename.set("");
      } else if (action instanceof MultiMenuRenderer.BeaconPick p) {
         if (p.secondary()) {
            this.menuInput.beaconSecondary = p.effectId();
         } else {
            this.menuInput.beaconPrimary = p.effectId();
         }
      } else if (this.cachedView != null && this.cachedView.interactive()) {
         MultiManager mgr = MultiManager.get();
         String type = this.viewTypeId();
         if (action instanceof MultiMenuRenderer.ButtonAct b) {
            if (this.sharedView) {
               this.applyFanout(mgr.buttonClickBots(this.sharedIds(), b.id(), type));
            } else {
               this.viewActionResult(mgr.buttonClickBot(this.viewingId, b.id()));
            }
         } else if (action instanceof MultiMenuRenderer.TradeAct t) {
            if (this.sharedView) {
               this.applyFanout(mgr.selectTradeBots(this.sharedIds(), t.index(), type));
            } else {
               this.viewActionResult(mgr.selectTradeBot(this.viewingId, t.index()));
            }
         } else if (action instanceof MultiMenuRenderer.BeaconAct be) {
            if (this.sharedView) {
               this.applyFanout(mgr.setBeaconBots(this.sharedIds(), be.primary(), be.secondary(), type));
            } else {
               this.viewActionResult(mgr.setBeaconBot(this.viewingId, be.primary(), be.secondary()));
            }
         } else if (action instanceof MultiMenuRenderer.RecipeStep rs) {
            this.menuInput.recipeIndex = Math.max(0, this.menuInput.recipeIndex + rs.delta());
            if (this.sharedView) {
               this.applyFanout(mgr.buttonClickBots(this.sharedIds(), this.menuInput.recipeIndex, type));
            } else {
               this.viewActionResult(mgr.buttonClickBot(this.viewingId, this.menuInput.recipeIndex));
            }
         }
      } else {
         this.resultText = "Inventory is synchronizing";
         this.resultColor = -6645094;
      }
   }

   private void viewActionResult(String result) {
      if (!"Sent".equals(result)) {
         this.resultText = result;
         this.resultColor = -6645094;
      }
   }

   private String viewTypeId() {
      return this.cachedView != null && this.cachedView.extras() != null ? this.cachedView.extras().typeId() : "";
   }

   private void sendRename() {
      if (this.sharedView) {
         this.applyFanout(MultiManager.get().renameBotItems(this.sharedIds(), this.menuInput.rename.text(), this.viewTypeId()));
      } else {
         MultiManager.get().renameBotItem(this.viewingId, this.menuInput.rename.text());
      }
   }

   private void openNbt(ItemStack stack) {
      if (this.minecraft != null && stack != null && !stack.isEmpty()) {
         if (RiptideItemNbtInspectOverlay.openGlobal(stack)) {
            RiptideItemNbtInspectOverlay overlay = RiptideItemNbtInspectOverlay.getSharedOverlay(this.font);
            if (overlay != null) {
               this.minecraft.gui.setScreen(new RiptideOverlayHostScreen(overlay, this, false, true));
            }
         }
      }
   }

   private void renderChat(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
      this.chatRows.clear();
      this.chatLeft = x;
      this.chatRight = x + w;
      this.chatTop = y;
      this.chatBottom = y + h;
      int gutter = 6;
      int tsWidth = this.font.width("[00:00:00] ");
      int x0 = x + tsWidth;
      int avail = Math.max(1, x + w - gutter - x0);
      this.chatAvail = avail;
      this.refreshChatCache(avail);
      List<MultiChatPresentation.VisualRow> rows = this.cachedVisualRows;
      List<MultiChatSelection.Row> selRows = new ArrayList<>(rows.size());

      for (MultiChatPresentation.VisualRow row : rows) {
         selRows.add(new MultiChatSelection.Row(row.line().seq(), row.lineIndex(), MultiChatSelection.plain(row.hit())));
      }

      this.chatSel.setRows(selRows);
      int visible = Math.max(1, h / 11);
      int maxScroll = Math.max(0, rows.size() - visible);
      this.chatScroll = Math.max(0, Math.min(this.chatScroll, maxScroll));
      int end = rows.size() - this.chatScroll;
      int start = Math.max(0, end - visible);
      int yy = y;

      for (int i = start; i < end; i++) {
         MultiChatPresentation.VisualRow row = rows.get(i);
         String rowText = MultiChatSelection.plain(row.hit());
         int[] range = this.chatSel.rangeFor(row.line().seq(), row.lineIndex(), rowText.length());
         if (range != null && range[1] > range[0]) {
            int sx = x0 + MultiChatSelection.widthOfStyled(this.font, row.hit(), range[0]);
            int ex = x0 + MultiChatSelection.widthOfStyled(this.font, row.hit(), range[1]);
            UiRenderer.rect(graphics, UiBounds.of(sx, yy - 1, Math.max(1, ex - sx), 11), 1430023925);
         }

         if (row.lineIndex() == 0) {
            graphics.text(this.font, Component.literal(this.timestamp(row.line().time())), x, yy, themeMuted(), false);
         }

         graphics.text(this.font, row.render(), x0, yy, themeText(), false);
         this.underlineClickableLine(graphics, row.hit(), x0, yy);
         this.chatRows.add(new RiptideMultiConsoleScreen.ChatRow(row.line(), row.lineIndex(), yy, x0, row.hit(), rowText));
         yy += 11;
      }

      this.chatMaxScrollRows = maxScroll;
      int lineH = 11;
      int offsetFromTop = Math.max(0, maxScroll - this.chatScroll) * lineH;
      this.chatScrollbar = CompactScrollbar.compute(rows.size() * lineH, visible * lineH, x + w - 4, y, 3, Math.max(1, h), offsetFromTop);
      CompactScrollbar.draw(graphics, this.chatScrollbar, false, this.chatScrollbarDragging);
   }

   private void chatScrollbarDrag(double vy) {
      if (this.chatScrollbar != null) {
         int px = CompactScrollbar.scrollFromThumb(this.chatScrollbar, vy, this.chatScrollbarGrab);
         int rowsFromTop = Math.round(px / 11.0F);
         this.chatScroll = Math.max(0, Math.min(this.chatMaxScrollRows, this.chatMaxScrollRows - rowsFromTop));
      }
   }

   private void chatSelectAt(double mx, double my, boolean begin) {
      if (!this.chatRows.isEmpty()) {
         RiptideMultiConsoleScreen.ChatRow target = this.chatRows.getFirst();

         for (RiptideMultiConsoleScreen.ChatRow row : this.chatRows) {
            if (my >= row.y()) {
               target = row;
            }
         }

         int ch = MultiChatSelection.charIndexAtStyled(this.font, target.hit(), (int)Math.round(mx - target.x0()));
         if (begin) {
            this.chatSel.begin(target.line().seq(), target.lineIndex(), ch);
         } else {
            this.chatSel.extend(target.line().seq(), target.lineIndex(), ch);
         }
      }
   }

   private boolean beginChatSelection(double mx, double my) {
      if (!(mx < this.chatLeft) && !(mx > this.chatRight) && !(my < this.chatTop) && !(my > this.chatBottom) && !this.chatRows.isEmpty()) {
         this.chatSelectingPress = true;
         this.chatSelectAt(mx, my, true);
         return true;
      } else {
         this.chatSel.clear();
         return false;
      }
   }

   private void copyChatSelection() {
      String text = this.chatSel.selectedText();
      if (!text.isBlank() && this.minecraft != null && this.minecraft.keyboardHandler != null) {
         this.minecraft.keyboardHandler.setClipboard(text);
         this.resultText = "Copied " + text.length() + " chars";
         this.resultColor = -13248397;
      }
   }

   private Set<String> chatScope() {
      return this.scopeForView();
   }

   private Set<String> actionScope() {
      return this.scopeForView();
   }

   private Set<String> scopeForView() {
      if (!this.selectedIds.isEmpty()) {
         return this.selectedIds;
      } else if (this.viewingId != null) {
         return Set.of(this.viewingId);
      } else {
         if (this.sharedView) {
            List<String> ids = this.sharedIds();
            if (!ids.isEmpty()) {
               return new LinkedHashSet<>(ids);
            }
         }

         return this.selectedIds;
      }
   }

   private void refreshChatCache(int avail) {
      Set<String> scope = this.chatScope();
      String scopeKey = String.join(",", scope);
      long rev = MultiManager.get().chatRevision();
      int total = this.currentChatTotal();
      if (rev != this.cachedChatRevision || !scopeKey.equals(this.cachedChatScope) || avail != this.cachedChatWidth || total != this.cachedChatTotal) {
         int previousRows = this.cachedVisualRows.size();
         boolean preserveScrolledPosition = this.chatScroll > 0
            && scopeKey.equals(this.cachedChatScope)
            && avail == this.cachedChatWidth
            && total == this.cachedChatTotal;
         this.cachedChatRevision = rev;
         this.cachedChatScope = scopeKey;
         this.cachedChatWidth = avail;
         this.cachedChatTotal = total;
         List<MultiChatPresentation.VisualRow> out = MultiChatPresentation.wrap(
            this.font, MultiManager.get().chatView(scope), avail, total, this::chatAccountLabel, themeMuted()
         );
         this.cachedVisualRows = out;
         if (preserveScrolledPosition && out.size() > previousRows) {
            this.chatScroll = this.chatScroll + (out.size() - previousRows);
         }
      }
   }

   private int currentChatTotal() {
      Set<String> scope = this.chatScope();
      return scope.isEmpty() ? Math.max(1, this.frameSnapshots.size()) : scope.size();
   }

   private String chatAccountLabel(String id) {
      MultiSession.Snapshot snapshot = this.findSnapshot(id);
      return snapshot == null ? id : snapshot.accountName();
   }

   private void underlineClickableLine(GuiGraphicsExtractor graphics, FormattedText line, int x0, int y) {
      MultiChatPresentation.underlineClickableLine(graphics, this.font, line, x0, y);
   }

   private boolean handleChatClick(double mx, double my) {
      if (!(mx < this.chatLeft) && !(mx > this.chatRight)) {
         for (RiptideMultiConsoleScreen.ChatRow row : this.chatRows) {
            if (my >= row.y() && my < row.y() + 11) {
               int relativeX = (int)(mx - row.x0());
               ClickEvent kind = relativeX >= 0 ? this.resolveClickInLine(row.hit(), relativeX) : null;
               if (kind == null) {
                  return false;
               }

               this.executeChatClick(row.line(), row.lineIndex(), relativeX, kind);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private ClickEvent resolveClickInLine(FormattedText line, int relativeX) {
      return MultiChatPresentation.resolveClick(this.font, line, relativeX);
   }

   private void executeChatClick(MultiManager.ChatLine line, int lineIndex, int relativeX, ClickEvent kind) {
      switch (kind) {
         case RunCommand command:
            int ran = MultiChatPresentation.runCommands(
               this.font, line, lineIndex, relativeX, this.chatAvail, this.currentChatTotal(), this::chatAccountLabel, themeMuted(), command
            );
            this.resultText = ran > 0 ? "Clicked " + ran : "Clicked";
            this.resultColor = -13248397;
            break;
         case SuggestCommand suggest:
            if (this.chatInput != null) {
               this.chatInput.setValue(suggest.command());
            }
            break;
         case CopyToClipboard copy:
            if (this.minecraft != null) {
               this.minecraft.keyboardHandler.setClipboard(copy.value());
            }

            this.resultText = "Copied";
            this.resultColor = -6645094;
            break;
         case OpenUrl url:
            MultiChatPresentation.openLinkSafely(url.uri());
            this.resultText = "Opening link";
            this.resultColor = -13248397;
            break;
         default:
      }
   }

   private String timestamp(long time) {
      return "[" + LocalTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).format(CHAT_TIME) + "] ";
   }

   public void onClose() {
      if (this.delayField != null && this.delayField.isFocused()) {
         MultiMacroDelay.persist();
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   private RiptideStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone, OnPress press) {
      String label = this.fitLabel(text, w - 8);
      Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
      RiptideStyledButton button = new RiptideStyledButton(x, y, Math.max(1, w), h, Component.literal(label), interactiveTone, press);
      this.addRenderableWidget(button);
      return button;
   }

   private String fitLabel(String text, int width) {
      return UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(text, 64), Math.max(1, width), THEME.fontFor(UiTone.BODY), themeText());
   }

   private int sessionWidth() {
      int room = this.screenWidth() - 24 - 8 - 160;
      return Math.max(120, Math.min(300, room));
   }

   private int consoleX() {
      return 12 + this.sessionWidth() + 8;
   }

   private int consoleWidth() {
      return Math.max(1, this.screenWidth() - this.consoleX() - 12);
   }

   private void clearInputFocus() {
      if (this.chatInput != null) {
         this.chatInput.setFocused(false);
      }

      if (this.delayField != null) {
         this.delayField.setFocused(false);
      }

      this.setFocused(null);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      UiText.draw(graphics, this.font, MultiManager.singleLine(text, 180), fontId, color, x, y, false);
   }

   private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      String line = UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(text, 160), Math.max(1, width), fontId, color);
      UiText.draw(graphics, this.font, line, fontId, color, x, y, false);
   }

   private static String detailLabel(MultiSession.Snapshot snapshot) {
      if (snapshot == null) {
         return "";
      } else {
         String proxy = MultiManager.singleLine(snapshot.proxyName(), 24);
         String status = statusWord(snapshot);
         return proxy.isBlank() ? status : proxy + " - " + status;
      }
   }

   private static String statusWord(MultiSession.Snapshot snapshot) {
      return switch (snapshot.status()) {
         case QUEUED -> "Queued";
         case AUTHENTICATING -> "Auth";
         case CONNECTING -> "Connecting";
         case LOGIN -> "Login";
         case CONFIGURING -> "Configuring";
         case JOINED -> "Joining";
         case READY -> "Ready";
         case DISCONNECTED -> "Disconnected";
         case FAILED -> "Failed";
      };
   }

   private static String shortResult(MultiManager.BroadcastResult result) {
      if (result == null) {
         return "Failed";
      } else if (result.failed() > 0) {
         return "Failed";
      } else if (result.sent() > 0 && result.skipped() == 0) {
         return "Sent";
      } else if (result.sent() > 0) {
         return "Sent " + result.sent();
      } else {
         return result.skipped() > 0 ? "Skipped" : "Failed";
      }
   }

   private static int themeBg() {
      return RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP);
   }

   private static int themePanel() {
      return RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON);
   }

   private static int themePanelSoft() {
      return RiptideTheme.recolor(-1206643689, RiptideTheme.Channel.BUTTON);
   }

   private static int themeBorder() {
      return RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
   }

   private static int themeText() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private static int themeMuted() {
      return RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
   }

   private static int themeSuccess() {
      return RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
   }

   private static int themeError() {
      return RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
   }

   private static int themeStatusColor(int color) {
      if (color == -13248397) {
         return themeSuccess();
      } else if (color == -42149) {
         return themeError();
      } else {
         return color == -855310 ? themeText() : themeMuted();
      }
   }

   private MultiSession.Snapshot findSnapshot(String accountId) {
      MultiSession.Snapshot cached = this.frameSnapshots.get(accountId);
      if (cached != null) {
         return cached;
      } else {
         for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (snapshot.accountId().equals(accountId)) {
               return snapshot;
            }
         }

         return null;
      }
   }

   private record ChatRow(MultiManager.ChatLine line, int lineIndex, int y, int x0, FormattedText hit, String text) {
   }

   private record SessionRow(String id, int y, int guiExtra) {
   }
}
