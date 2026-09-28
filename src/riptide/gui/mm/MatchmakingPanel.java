package riptide.gui.mm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import riptide.commands.RiptideCommands;
import riptide.gui.vanillaui.TextWrapLayout;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.Card;
import riptide.gui.vanillaui.components.Chip;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.modules.Module;
import riptide.modules.ModuleCategory;
import riptide.modules.ModuleRegistry;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptideDiscordLogin;
import riptide.util.RiptideFilterViewOverlay;
import riptide.util.RiptideGuiViewOverlay;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideLinks;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideModuleViewOverlay;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketInspectOverlay;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptideSharePickerOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.mm.Lobby;
import riptide.util.mm.LobbyListing;
import riptide.util.mm.LobbySettings;
import riptide.util.mm.MatchmakingManager;
import riptide.util.mm.MmBlobs;
import riptide.util.mm.MmCardActions;
import riptide.util.mm.MmChatLine;
import riptide.util.mm.MmPeer;
import riptide.util.mm.MmPrefs;
import riptide.util.mm.MmShare;
import riptide.util.mm.msg.MmMessages;

public final class MatchmakingPanel {
   private static final int BG = -200405488;
   private static final int PANEL_SOFT = -938208233;
   private static final int CARD_BG = 705827348;
   private static final int DISABLED_FILL = 403771667;
   private static final int DISABLED_BORDER = -14013906;
   private static final int WARN = -14249;
   private static final int INFO = -7429889;
   private static final int BORDER_STOCK = -13425624;
   private static final int BORDER_BRIGHT_STOCK = -8759968;
   private static final int ACCENT_STOCK = -13248397;
   private static final int TEXT_STOCK = -855310;
   private static final int MUTED_STOCK = -6645094;
   private static final int ERROR_STOCK = -42149;
   private int BORDER = -13425624;
   private int BORDER_BRIGHT = -8759968;
   private int ACCENT = -13248397;
   private int TEXT = -855310;
   private int MUTED = -6645094;
   private int ERROR = -42149;
   private final MatchmakingPanel.Host host;
   private final Font font;
   private final CompactTheme theme = new CompactTheme();
   private final MatchmakingManager mm = MatchmakingManager.get();
   private final MmPrefs prefs = MmPrefs.get();
   private final List<MatchmakingPanel.Hotspot> hotspots = new ArrayList<>();
   private MatchmakingPanel.Tab activeTab = MatchmakingPanel.Tab.LOBBIES;
   private MatchmakingPanel.LobbyPane lobbyPane = MatchmakingPanel.LobbyPane.BROWSE;
   private boolean joinPublicMode = true;
   private boolean createPublic = true;
   private boolean createAnnouncement;
   private final int[] scroll = new int[MatchmakingPanel.Tab.values().length];
   private final int[] contentHeight = new int[MatchmakingPanel.Tab.values().length];
   private int chromeHeight;
   private int lastContentTop;
   private int availableContentH;
   private static final int CHAT_CHROME = 60;
   private static final int MIN_CHAT_LOG = 14;
   private int chatViewPx;
   private int chatMaxScrollPx;
   private CompactScrollbar.Metrics chatScrollbar;
   private boolean chatScrollbarDragging;
   private int chatScrollGrab;
   private ItemStack chatTooltipItem = ItemStack.EMPTY;
   private int chatTooltipMx;
   private int chatTooltipMy;
   private int chatLogTop;
   private int chatLogBottom;
   private int hitGateTop = Integer.MIN_VALUE;
   private int hitGateBottom = Integer.MAX_VALUE;
   private final CompactTextInput chatInput = this.field("Message, /command, or paste a shared hash…", 100000, false);
   private final CompactTextInput lobbyName = this.field("Lobby name", 32, false);
   private final CompactTextInput server = this.field("Server (optional)", 48, false);
   private final CompactTextInput maxPlayers = this.field("40", 4, true);
   private final CompactTextInput passphrase = this.field("Paste the Room Key", 64, false);
   private final CompactTextInput joinCode = this.field("Paste the Room Code", 24, false);
   private final List<CompactTextInput> activeInputs = new ArrayList<>();
   private RiptidePacketInspectOverlay inspectOverlay;
   private RiptideGuiViewOverlay guiViewOverlay;
   private RiptideFilterViewOverlay filterViewOverlay;
   private RiptideModuleViewOverlay moduleViewOverlay;
   private RiptideSharePickerOverlay sharePicker;
   private static final int ICON_CACHE_MAX = 64;
   private final Map<String, ItemStack> itemIconCache = new LinkedHashMap<>();
   private RiptideTheme.State cachedThemeState;
   private int cachedChatPx;
   private int cachedChatPxVersion = -1;
   private int cachedChatPxWidth = -1;
   private long cachedNamesRosterVersion = Long.MIN_VALUE;
   private int cachedNamesMemberCount = -1;
   private String cachedNamesSelf = "";
   private Map<String, String> cachedNames = Map.of();
   private static final int CHAT_ROW_STEP = 10;
   private static final int CHAT_MAX_ROWS = 3;
   private int lastChatLogFullW;
   private float delta;
   private int lastMx;
   private int lastMy;
   private int bx;
   private int by;
   private int bw;
   private int bh;
   private final List<MatchmakingPanel.MemberRow> memberRows = new ArrayList<>();
   private final List<MatchmakingPanel.CtxItem> ctxItems = new ArrayList<>();
   private boolean ctxOpen;
   private int ctxX;
   private int ctxY;
   private String ctxFp = "";
   private boolean ctxBanConfirm;

   public MatchmakingPanel(MatchmakingPanel.Host host, Font font) {
      this.host = host;
      this.font = font;
      this.createPublic = this.prefs.defaultPublic();
      this.lobbyName.setText(this.prefs.defaultLobbyName());
      this.server.setText(this.prefs.defaultServer());
      this.maxPlayers.setText(this.prefs.defaultMaxPlayers() <= 0 ? "" : Integer.toString(this.prefs.defaultMaxPlayers()));
      this.lobbyName.setOnChange(this.prefs::setDefaultLobbyName);
      this.server.setOnChange(this.prefs::setDefaultServer);
      this.maxPlayers.setOnChange(v -> this.prefs.setDefaultMaxPlayers(parseMax(v)));
      this.chatInput.setMultiline(true).setSubmitOnEnter(true);
      this.chatInput.setOnSubmit(v -> this.doSend());
      this.joinCode.setOnSubmit(v -> this.doJoinPublic());
   }

   private CompactTextInput field(String placeholder, int maxLen, boolean numeric) {
      CompactTextInput f = new CompactTextInput();
      f.setPlaceholder(placeholder).setMaxLength(maxLen).setFieldHeight(16);
      if (numeric) {
         f.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
      }

      return f;
   }

   public void openDirectory() {
      this.mm.openDirectory();
   }

   public void closeDirectory() {
      this.mm.closeDirectory();
   }

   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float partial) {
      this.refreshTheme();
      this.delta = partial;
      this.lastMx = mouseX;
      this.lastMy = mouseY;
      this.bx = x;
      this.by = y;
      this.bw = w;
      this.bh = h;
      this.hotspots.clear();
      this.activeInputs.clear();
      this.memberRows.clear();
      if (!this.mm.inLobby() && (this.activeTab == MatchmakingPanel.Tab.CHAT || this.activeTab == MatchmakingPanel.Tab.MEMBERS)) {
         this.activeTab = MatchmakingPanel.Tab.LOBBIES;
      }

      if (this.mm.inLobby() && this.activeTab == MatchmakingPanel.Tab.LOBBIES) {
         this.activeTab = MatchmakingPanel.Tab.CHAT;
      }

      if (this.activeTab != MatchmakingPanel.Tab.MEMBERS && this.ctxOpen) {
         this.closeContextMenu();
      }

      UiRenderer.rect(g, UiBounds.of(x, y, w, h), -200405488);
      if (!RiptideDiscordLogin.hasSession()) {
         this.renderSignInGate(g, x, y, w, h, mouseX, mouseY);
      } else {
         MatchmakingManager.JoinPhase jp = this.mm.joinPhase();
         if (jp != MatchmakingManager.JoinPhase.NONE) {
            this.renderJoiningGate(g, x, y, w, h, mouseX, mouseY, jp);
         } else if (!this.mm.matchmakingReady()) {
            this.renderConnectingGate(g, x, y, w, h, mouseX, mouseY);
         } else {
            Lobby lb = this.mm.currentLobby();
            String status = this.mm.inLobby()
               ? "In lobby: "
                  + lb.name
                  + (lb.announcement ? "  [Announcement]" : "")
                  + "   ("
                  + (this.mm.currentLobbyOfficial() ? this.mm.memberCount() + " users" : this.mm.memberCount() + "/" + maxLabel(lb.maxPlayers))
                  + ")"
               : "Not in a lobby";
            this.drawText(g, status, x + 8, y + 6, this.mm.inLobby() ? this.ACCENT : this.MUTED, false, w - 16);
            String subline = !this.mm.inLobby()
               ? "Browse public lobbies or create your own below."
               : (
                  lb.announcement && !this.mm.canSend()
                     ? "Announcement lobby  •  Read-only"
                     : (
                        lb.announcement
                           ? "Announcement lobby  •  You can send"
                           : (lb.isPublic ? "Invite with the Room Code in the Members tab." : "Invite with the Room Key in the Members tab.")
                     )
               );
            this.drawText(g, subline, x + 8, y + 17, this.MUTED, false, w - 16);
            int tabY = y + 30;
            int tabH = 18;
            int n = MatchmakingPanel.Tab.values().length;
            int tabW = (w - 16 - (n - 1) * 4) / n;
            int tx = x + 8;

            for (MatchmakingPanel.Tab tab : MatchmakingPanel.Tab.values()) {
               if (tab == MatchmakingPanel.Tab.LOBBIES && this.mm.inLobby()) {
                  boolean hover = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + tabH;
                  UiRenderer.frame(g, UiBounds.of(tx, tabY, tabW, tabH), tint(this.ERROR, hover ? 238 : 204), this.ERROR);
                  this.drawText(g, "Leave", tx + tabW / 2, tabY + (tabH - 8) / 2, -1, true, tabW - 4);
                  this.addHotspot(tx, tabY, tabW, tabH, this::doLeave);
               } else {
                  boolean active = tab == this.activeTab;
                  boolean usable = this.tabUsable(tab);
                  this.chipColored(g, tx, tabY, tabW, tabH, tab.label, this.ACCENT, active, usable, mouseX, mouseY, usable ? () -> {
                     this.activeTab = tab;
                     this.clearFocus();
                  } : null);
               }

               tx += tabW + 4;
            }

            int contentTop = this.renderLobbyServerBar(g, x, tabY + tabH + 6, w, mouseX, mouseY);
            int contentBottom = y + h - 6;
            int cx = x + 8;
            int cw = w - 16;
            this.chromeHeight = contentTop - y;
            this.lastContentTop = contentTop;
            this.availableContentH = Math.max(0, contentBottom - contentTop);
            if (this.activeTab != MatchmakingPanel.Tab.CHAT) {
               this.scroll[this.activeTab.ordinal()] = Math.max(0, Math.min(this.scroll[this.activeTab.ordinal()], this.maxScrollFor(this.activeTab)));
            }

            DirectRenderContext ctx = this.renderCtx(g, mouseX, mouseY);
            switch (this.activeTab) {
               case LOBBIES:
                  this.renderLobbies(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
                  break;
               case CHAT:
                  this.renderChat(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
                  break;
               case MEMBERS:
                  this.renderMembers(g, cx, contentTop, cw, contentBottom, mouseX, mouseY);
                  break;
               case SETTINGS:
                  this.renderSettings(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
            }

            this.renderContextMenu(g, mouseX, mouseY);
         }
      }
   }

   private void renderSignInGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
      int cx = x + w / 2;
      int ty = y + Math.max(14, h / 2 - 64);
      this.drawText(g, "Matchmaking", cx, ty, this.ACCENT, true, w - 16);
      ty += 18;
      String notice = RiptideDiscordLogin.authGateNotice();
      boolean gated = RiptideDiscordLogin.isGateCode(notice);
      String[] lines;
      if (gated) {
         lines = new String[]{"Your shit's out of date.", "", "Grab the latest from riptide.com,", "then restart Minecraft and sign back in."};
      } else {
         lines = new String[]{
            "Find players, share macros, and TPA across",
            "servers, all end to end encrypted.",
            "",
            "You must be a member of our Discord to use it.",
            "Sign in with Discord to continue."
         };
      }

      for (int i = 0; i < lines.length; i++) {
         int col = lines[i].isEmpty() ? this.MUTED : (gated && i == 0 ? this.ERROR : this.TEXT);
         this.drawText(g, lines[i], cx, ty, col, true, w - 24);
         ty += 11;
      }

      ty += 10;
      int bw2 = Math.min(190, w - 32);
      int bxc = cx - bw2 / 2;
      if (gated) {
         this.button(g, bxc, ty, bw2, 18, "Get the latest version", this.ACCENT, this.TEXT, mx, my, () -> RiptideLinks.open("                   "));
         ty += 24;
      }

      this.button(g, bxc, ty, bw2, 18, "Sign in with Discord", gated ? this.BORDER : this.ACCENT, this.TEXT, mx, my, this::signInDiscord);
      ty += 24;
      this.button(g, bxc, ty, bw2, 16, "Join our Discord", this.BORDER, this.TEXT, mx, my, () -> RiptideLinks.open(""));
   }

   private void renderConnectingGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
      int cx = x + w / 2;
      int ty = y + Math.max(14, h / 2 - 56);
      this.drawText(g, "Matchmaking", cx, ty, this.ACCENT, true, w - 16);
      ty += 20;
      if (this.mm.connState() == MatchmakingManager.ConnState.OFFLINE) {
         if (RiptideDiscordLogin.isGateCode(RiptideDiscordLogin.authGateNotice())) {
            this.drawText(g, RiptideDiscordLogin.errorMessage(RiptideDiscordLogin.authGateNotice()), cx, ty, this.ERROR, true, w - 24);
            ty += 13;
            String min = RiptideDiscordLogin.requiredMinVersion();
            if (!min.isEmpty()) {
               this.drawText(g, "Minimum version: " + min, cx, ty, this.MUTED, true, w - 24);
               ty += 11;
            }

            this.drawText(g, "Get the latest download at riptide.com", cx, ty, this.MUTED, true, w - 24);
            ty += 11;
         } else {
            this.drawText(g, "Can't reach the matchmaking server.", cx, ty, this.ERROR, true, w - 24);
            ty += 13;
            this.drawText(g, "It may be down or restarting.", cx, ty, this.MUTED, true, w - 24);
            ty += 11;
            this.drawText(g, "Check your connection, then try again.", cx, ty, this.MUTED, true, w - 24);
            ty += 11;
            String err = this.mm.connError();
            if (!err.isEmpty()) {
               this.drawText(g, err, cx, ty, tint(this.MUTED, 170), true, w - 24);
               ty += 12;
            }
         }

         ty += 10;
         int bw2 = Math.min(190, w - 32);
         int bxc = cx - bw2 / 2;
         this.button(g, bxc, ty, bw2, 18, "Try again", this.ACCENT, this.TEXT, mx, my, this.mm::reconnectNow);
         ty += 24;
         this.button(g, bxc, ty, bw2, 16, "Sign out", this.BORDER, this.TEXT, mx, my, this::signOutDiscord);
      } else {
         long t = System.currentTimeMillis();
         String dots = ".".repeat((int)(t / 400L % 4L));
         this.drawText(g, "Connecting to matchmaking server" + dots, cx, ty, this.TEXT, true, w - 24);
         ty += 16;
         char spin = "|/-\\".charAt((int)(t / 120L % 4L));
         this.drawText(g, String.valueOf(spin), cx, ty, this.ACCENT, true, w - 24);
         ty += 16;
         this.drawText(g, "This only takes a moment.", cx, ty, this.MUTED, true, w - 24);
      }
   }

   private void renderJoiningGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, MatchmakingManager.JoinPhase jp) {
      int cx = x + w / 2;
      int ty = y + Math.max(14, h / 2 - 70);
      this.drawText(g, "Joining lobby", cx, ty, this.ACCENT, true, w - 16);
      ty += 20;
      if (jp == MatchmakingManager.JoinPhase.FAILED) {
         this.drawText(g, this.joinFailMessage(this.mm.joinError()), cx, ty, this.ERROR, true, w - 24);
         ty += 13;
         this.drawText(g, "Nothing was joined.", cx, ty, this.MUTED, true, w - 24);
         ty += 18;
         int bw2 = Math.min(190, w - 32);
         this.button(g, cx - bw2 / 2, ty, bw2, 18, "Back", this.ACCENT, this.TEXT, mx, my, this.mm::cancelJoin);
      } else {
         boolean reserving = jp == MatchmakingManager.JoinPhase.RESERVING;
         char spin = "|/-\\".charAt((int)(System.currentTimeMillis() / 120L % 4L));
         String[] steps = new String[]{"Checking membership", "Checking lobby ban", "Reserving a slot", "Connecting"};

         for (int i = 0; i < steps.length; i++) {
            boolean done = !reserving && i < 3;
            boolean active = reserving ? i < 3 : i == 3;
            String mark = done ? "[x] " : (active ? "[" + spin + "] " : "[ ] ");
            this.drawText(g, mark + steps[i], cx, ty, done ? this.ACCENT : (active ? this.TEXT : this.MUTED), true, w - 24);
            ty += 12;
         }

         ty += 8;
         int bw2 = Math.min(160, w - 32);
         this.button(g, cx - bw2 / 2, ty, bw2, 16, "Cancel", this.BORDER, this.TEXT, mx, my, this.mm::cancelJoin);
      }
   }

   private String joinFailMessage(String err) {
      String var2 = err == null ? "" : err;

      return switch (var2) {
         case "no_such_lobby" -> "No lobby found for that code.";
         case "full" -> "That lobby is full.";
         case "lobby_banned" -> "You're banned from that lobby.";
         case "too_many" -> "You're in too many lobbies already.";
         case "busy" -> "Server busy. Try again.";
         case "unauthorized" -> "Sign in again.";
         case "old_version" -> "Update the mod.";
         case "network" -> "Can't reach matchmaking. Try again.";
         default -> "Couldn't join. Try again.";
      };
   }

   private void refreshTheme() {
      RiptideTheme.State st = RiptideTheme.active();
      if (st != this.cachedThemeState) {
         this.cachedThemeState = st;
         this.BORDER = RiptideTheme.recolor(-13425624, RiptideTheme.Channel.OUTLINE);
         this.BORDER_BRIGHT = RiptideTheme.recolor(-8759968, RiptideTheme.Channel.OUTLINE);
         this.ACCENT = RiptideTheme.recolor(-13248397, RiptideTheme.Channel.ACCENT);
         this.TEXT = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         this.MUTED = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         this.ERROR = RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
      }
   }

   private boolean tabUsable(MatchmakingPanel.Tab tab) {
      return switch (tab) {
         case CHAT, MEMBERS -> this.mm.inLobby();
         default -> true;
      };
   }

   private int renderLobbyServerBar(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
      Lobby lb = this.mm.currentLobby();
      if (lb == null) {
         return y;
      } else {
         String ip = lb.server == null ? "" : lb.server.trim();
         if (!ip.isEmpty() && !MatchmakingManager.alreadyOn(ip)) {
            int rowH = 16;
            int joinW = 44;
            Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x + 8, y, w - 16, rowH));
            this.drawText(g, "Server: " + MmBlobs.displayAddr(ip), x + 14, y + 4, -7429889, false, w - 16 - joinW - 18);
            String label = lb.name;
            this.button(
               g,
               x + w - 8 - joinW,
               y + 1,
               joinW,
               rowH - 2,
               "Join",
               -7429889,
               this.TEXT,
               mx,
               my,
               () -> MmCardActions.confirmJoinServer(ip, label, this.host.returnScreen())
            );
            return y + rowH + 4;
         } else {
            return y;
         }
      }
   }

   private void renderLobbies(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {
      UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
      this.hitGateTop = top;
      this.hitGateBottom = bottom;

      try {
         int top0 = top - this.scrollOf(MatchmakingPanel.Tab.LOBBIES);
         int segW = (w - 8) / 3;
         this.chipColored(
            g,
            x,
            top0,
            segW,
            18,
            "Browse",
            this.ACCENT,
            this.lobbyPane == MatchmakingPanel.LobbyPane.BROWSE,
            true,
            mx,
            my,
            () -> this.setPane(MatchmakingPanel.LobbyPane.BROWSE)
         );
         this.chipColored(
            g,
            x + segW + 4,
            top0,
            segW,
            18,
            "Create",
            this.ACCENT,
            this.lobbyPane == MatchmakingPanel.LobbyPane.CREATE,
            true,
            mx,
            my,
            () -> this.setPane(MatchmakingPanel.LobbyPane.CREATE)
         );
         this.chipColored(
            g,
            x + (segW + 4) * 2,
            top0,
            w - (segW + 4) * 2,
            18,
            "Join",
            this.ACCENT,
            this.lobbyPane == MatchmakingPanel.LobbyPane.JOIN,
            true,
            mx,
            my,
            () -> this.setPane(MatchmakingPanel.LobbyPane.JOIN)
         );
         int paneTop = top0 + 26;

         int paneBottom = switch (this.lobbyPane) {
            case BROWSE -> this.renderBrowse(g, x, paneTop, w, top, bottom, mx, my);
            case CREATE -> this.renderCreate(g, ctx, x, paneTop, w, bottom, mx, my);
            case JOIN -> this.renderJoin(g, ctx, x, paneTop, w, bottom, mx, my);
         };
         this.setContentBottom(MatchmakingPanel.Tab.LOBBIES, paneBottom);
      } finally {
         this.hitGateTop = Integer.MIN_VALUE;
         this.hitGateBottom = Integer.MAX_VALUE;
         UiScissorStack.global().pop(g);
      }
   }

   private void setPane(MatchmakingPanel.LobbyPane pane) {
      this.lobbyPane = pane;
      this.clearFocus();
      this.scroll[MatchmakingPanel.Tab.LOBBIES.ordinal()] = 0;
   }

   private int renderBrowse(GuiGraphicsExtractor g, int x, int y, int w, int top, int bottom, int mx, int my) {
      this.drawText(g, "Public lobbies", x, y, this.TEXT, false, w - 60);
      this.button(g, x + w - 56, y - 2, 56, 13, "Refresh", this.BORDER, this.TEXT, mx, my, this::doRefresh);
      boolean refreshing = this.mm.directoryRefreshing();
      boolean hasRefreshFeedback = refreshing || this.mm.lastDirectoryPullMs() > 0L;
      if (refreshing) {
         int barY = y + 14;
         UiRenderer.rect(g, UiBounds.of(x, barY, w, 2), tint(this.MUTED, 64));
         long t = System.currentTimeMillis();
         int segW = Math.max(24, w / 4);
         int off = (int)(t / 5L % (w + segW)) - segW;
         int sx = Math.max(x, x + off);
         int ex = Math.min(x + w, x + off + segW);
         if (ex > sx) {
            UiRenderer.rect(g, UiBounds.of(sx, barY, ex - sx, 2), this.ACCENT);
         }
      } else if (hasRefreshFeedback) {
         long ago = Math.max(0L, (System.currentTimeMillis() - this.mm.lastDirectoryPullMs()) / 1000L);
         this.drawText(g, "Refreshed " + ago + "s ago", x, y + 13, this.MUTED, false, w - 64);
      }

      y += hasRefreshFeedback ? 27 : 16;
      List<LobbyListing> listings = this.mm.directoryListings();
      if (listings.isEmpty()) {
         this.drawText(g, "No public lobbies announced yet.", x + 2, y + 2, this.MUTED, false, w - 4);
         y += 14;
         this.drawText(g, "Use Create to host one, or Join to enter a code/passphrase.", x + 2, y + 2, this.MUTED, false, w - 4);
         return y + 12;
      } else {
         for (LobbyListing l : listings) {
            boolean visible = y + 32 > top && y < bottom;
            if (visible) {
               Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, 30));
               this.drawText(g, l.name, x + 8, y + 4, l.official ? this.ERROR : this.TEXT, false, w - 96);
               String tags = l.official ? l.members + " users" : l.members + "/" + maxLabel(l.maxMembers);
               if (l.announcement) {
                  tags = tags + "  •  Announcement";
               }

               if (l.hostDupe == 0) {
                  tags = tags + "  •  dupe hunting";
               } else if (l.hostDupe == 1) {
                  tags = tags + "  •  has dupe";
               }

               if (!l.server.isBlank()) {
                  tags = tags + "  •  " + l.server;
               }

               this.drawText(g, tags, x + 8, y + 16, this.MUTED, false, w - 96);
               boolean full = l.isFull();
               this.button(
                  g,
                  x + w - 80,
                  y + 6,
                  72,
                  18,
                  full ? "Full" : "Join",
                  full ? this.BORDER : this.ACCENT,
                  full ? this.MUTED : this.TEXT,
                  mx,
                  my,
                  full ? null : () -> {
                     this.mm.joinPublic(l.lobbyId);
                     this.afterJoin();
                  }
               );
            }

            y += 34;
         }

         return y;
      }
   }

   private int renderCreate(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int y, int w, int bottom, int mx, int my) {
      boolean pub = this.createPublic;
      this.drawText(g, "Visibility", x, y + 4, this.MUTED, false, 70);
      this.chipColored(g, x + 72, y, 70, 16, "Public", this.ACCENT, pub, true, mx, my, () -> this.createPublic = true);
      this.chipColored(g, x + 146, y, 70, 16, "Private", -14249, !pub, true, mx, my, () -> this.createPublic = false);
      y += 22;
      this.drawText(g, "Name *", x, y - 1, this.MUTED, false, 56);
      this.placeField(g, ctx, this.lobbyName, x + 72, y - 3, w - 72);
      y += 20;
      if (pub) {
         this.drawText(g, "Server", x, y - 1, this.MUTED, false, 56);
         this.placeField(g, ctx, this.server, x + 72, y - 3, w - 72);
         y += 20;
      }

      if (RiptideDiscordLogin.isAdmin()) {
         this.drawText(g, "Messaging", x, y + 4, this.MUTED, false, 70);
         int mw = (w - 72 - 4) / 2;
         this.chipColored(g, x + 72, y, mw, 16, "Everyone", this.ACCENT, !this.createAnnouncement, true, mx, my, () -> this.createAnnouncement = false);
         this.chipColored(g, x + 72 + mw + 4, y, mw, 16, "Announcement", -14249, this.createAnnouncement, true, mx, my, () -> this.createAnnouncement = true);
         y += 22;
      } else {
         this.createAnnouncement = false;
      }

      this.drawText(g, "Max", x, y - 1, this.MUTED, false, 56);
      this.placeField(g, ctx, this.maxPlayers, x + 72, y - 3, 44);
      int capLimit = capLimit();
      this.drawText(g, "(blank = " + capLimit + ", max " + capLimit + ")", x + 122, y, this.MUTED, false, w - 130);
      y += 22;
      if (pub) {
         this.drawText(g, "Dupe *", x, y + 3, this.MUTED, false, 56);
         int dw = (w - 72 - 4) / 2;
         this.chipColored(g, x + 72, y, dw, 16, "Dupe Hunting", -14249, this.prefs.myDupeStatus() == 0, true, mx, my, () -> this.prefs.setMyDupeStatus(0));
         this.chipColored(
            g, x + 72 + dw + 4, y, dw, 16, "Has dupe", this.ACCENT, this.prefs.myDupeStatus() == 1, true, mx, my, () -> this.prefs.setMyDupeStatus(1)
         );
         y += 22;
      }

      this.button(g, x, y, w, 20, pub ? "Create public lobby" : "Create private lobby", this.ACCENT, this.TEXT, mx, my, this::doCreate);
      this.drawText(g, pub ? "Name and dupe status are required." : "A secure Room Key is generated for you to share.", x, y + 24, this.MUTED, false, w);
      return y + 36;
   }

   private int renderJoin(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int y, int w, int bottom, int mx, int my) {
      this.drawText(g, "Join by", x, y + 4, this.MUTED, false, 56);
      this.chipColored(g, x + 60, y, 104, 16, "Public", this.ACCENT, this.joinPublicMode, true, mx, my, () -> {
         this.joinPublicMode = true;
         this.clearFocus();
      });
      this.chipColored(g, x + 168, y, 130, 16, "Private", -14249, !this.joinPublicMode, true, mx, my, () -> {
         this.joinPublicMode = false;
         this.clearFocus();
      });
      y += 24;
      if (this.joinPublicMode) {
         this.drawText(g, "Room Code", x, y - 1, this.MUTED, false, 70);
         this.placeField(g, ctx, this.joinCode, x + 72, y - 3, w - 72);
         y += 22;
         this.button(g, x, y, w, 20, "Join", this.ACCENT, this.TEXT, mx, my, this::doJoinPublic);
         this.drawText(g, "Paste the public Room Code someone shared with you.", x, y + 24, this.MUTED, false, w);
      } else {
         this.drawText(g, "Room Key", x, y - 1, this.MUTED, false, 70);
         this.placeField(g, ctx, this.passphrase, x + 72, y - 3, w - 72);
         y += 22;
         this.button(g, x, y, w, 20, "Join", this.ACCENT, this.TEXT, mx, my, this::doJoinPrivate);
         this.drawText(g, "Paste the private Room Key someone shared with you.", x, y + 24, this.MUTED, false, w);
      }

      return y + 36;
   }

   private void afterJoin() {
      this.activeTab = MatchmakingPanel.Tab.CHAT;
      this.clearFocus();
   }

   private void doLeave() {
      this.mm.leave();
      this.activeTab = MatchmakingPanel.Tab.LOBBIES;
      this.lobbyPane = MatchmakingPanel.LobbyPane.BROWSE;
      this.scroll[MatchmakingPanel.Tab.LOBBIES.ordinal()] = 0;
      this.mm.refreshDirectory();
      this.clearFocus();
   }

   private void doRefresh() {
      this.mm.refreshDirectory();
   }

   private void doCreate() {
      String name = this.lobbyName.text().trim();
      if (name.isEmpty()) {
         RiptideNotifications.show("Enter a lobby name.", -14249);
      } else {
         int max = parseMax(this.maxPlayers.text());
         int capLimit = capLimit();
         if (max <= 0) {
            max = capLimit;
         }

         if (max > capLimit) {
            max = capLimit;
            RiptideNotifications.show("Max players is capped at " + capLimit + ".", -14249);
         }

         if (this.createPublic) {
            if (this.prefs.myDupeStatus() < 0) {
               RiptideNotifications.show("Pick a dupe status (needs or has).", -14249);
               return;
            }

            this.mm.createLobby(new LobbySettings(name, true, max, null, this.server.text().trim(), "", this.createAnnouncement));
         } else {
            this.mm.createLobby(new LobbySettings(name, false, max, null, "", "", this.createAnnouncement));
         }

         this.prefs.setDefaultPublic(this.createPublic);
         this.afterJoin();
      }
   }

   private void doJoinPublic() {
      String code = this.joinCode.text().trim();
      if (code.isEmpty()) {
         RiptideNotifications.show("Enter a lobby code.", -14249);
      } else {
         this.mm.joinPublic(code);
         this.afterJoin();
      }
   }

   private void doJoinPrivate() {
      String key = this.passphrase.text().trim();
      if (key.isEmpty()) {
         RiptideNotifications.show("Paste the room key.", -14249);
      } else {
         this.mm.joinPrivate(key.toCharArray());
         this.afterJoin();
      }
   }

   private static int parseMax(String s) {
      if (s != null && !s.isBlank()) {
         try {
            return Integer.parseInt(s.trim());
         } catch (NumberFormatException var2) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static int capLimit() {
      String var0 = RiptideDiscordLogin.role();

      return switch (var0) {
         case "god", "admin" -> 500;
         case "gold" -> 50;
         case "aqua" -> 50;
         case "notbroke" -> 8;
         case "blue" -> 4;
         default -> 4;
      };
   }

   private void renderChat(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {
      boolean canWrite = this.mm.canSend();
      this.chatInput.setEditable(canWrite).setPlaceholder(canWrite ? "Message, /command, or paste a shared hash…" : "Announcement is read-only");
      int composerFieldW = w - 56;
      int composerRows = Math.max(1, Math.min(3, this.chatInput.wrappedRowCount(ctx, composerFieldW)));
      int composerH = Math.max(16, this.chatInput.rowsToHeight(ctx, composerRows));
      int composerY = bottom - 3 - composerH;
      int toolR1 = composerY - 38;
      int chatBottom = toolR1 - 4;
      Map<String, String> names = this.disambiguate(this.mm.members());
      List<MmChatLine> lines = this.mm.chatSnapshot();
      this.lastChatLogFullW = w;
      if (lines.isEmpty()) {
         this.drawText(g, "No messages yet. Say hi, type a /command, or use the Share buttons below.", x, top + 2, this.MUTED, false, w);
      }

      int viewPx = Math.max(0, chatBottom - top);
      int[] heights = this.chatLineHeights(lines, w, names);
      boolean reserveBar = sum(heights) > viewPx;
      int logW = reserveBar ? w - 5 : w;
      if (reserveBar) {
         heights = this.chatLineHeights(lines, logW, names);
      }

      int logPx = sum(heights);
      this.chatViewPx = viewPx;
      this.chatMaxScrollPx = Math.max(0, logPx - this.chatViewPx);
      int sc = Math.max(0, Math.min(this.chatMaxScrollPx, this.scroll[MatchmakingPanel.Tab.CHAT.ordinal()]));
      this.scroll[MatchmakingPanel.Tab.CHAT.ordinal()] = sc;
      this.chatTooltipItem = ItemStack.EMPTY;
      this.chatLogTop = top;
      this.chatLogBottom = chatBottom;
      UiScissorStack.global().push(g, UiBounds.of(x, top, w, this.chatViewPx));

      try {
         int yPos = chatBottom + sc;

         for (int i = lines.size() - 1; i >= 0; i--) {
            MmChatLine line = lines.get(i);
            int lh = heights[i];
            yPos -= lh;
            if (yPos < chatBottom) {
               if (yPos + lh <= top) {
                  break;
               }

               if (line.isCard()) {
                  this.renderCard(g, line, names, x, yPos, logW, mx, my);
               } else {
                  this.renderTextLine(g, line, names, x, yPos, logW, mx, my);
               }
            }
         }
      } finally {
         UiScissorStack.global().pop(g);
      }

      if (reserveBar) {
         int trackX = x + w - 3;
         this.chatScrollbar = CompactScrollbar.compute(logPx, this.chatViewPx, trackX, top, 3, this.chatViewPx, this.chatMaxScrollPx - sc);
         CompactScrollbar.draw(g, this.chatScrollbar, this.chatScrollbar.contains(mx, my), this.chatScrollbarDragging);
      } else {
         this.chatScrollbar = null;
      }

      this.renderShareToolbar(g, x, toolR1, w, mx, my);
      this.placeField(g, ctx, this.chatInput, x, composerY, composerFieldW, composerH);
      this.button(g, x + w - 50, bottom - 3 - 16, 50, 16, "Send", this.ACCENT, this.TEXT, mx, my, this.mm.inLobby() && canWrite ? this::doSend : null);
      if (this.chatTooltipItem != null && !this.chatTooltipItem.isEmpty()) {
         this.drawItemTooltip(g, this.chatTooltipItem, this.chatTooltipMx, this.chatTooltipMy);
      }
   }

   private void drawItemTooltip(GuiGraphicsExtractor g, ItemStack stack, int mx, int my) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null) {
            return;
         }

         List<Component> lines = Screen.getTooltipFromItem(mc, stack);
         if (lines == null || lines.isEmpty()) {
            return;
         }

         int tw = 0;

         for (Component c : lines) {
            tw = Math.max(tw, this.font.width(c));
         }

         int th = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         int tx = mx + 12;
         int ty = my - 12;
         if (tx + tw + 4 > sw) {
            tx = Math.max(4, mx - tw - 16);
         }

         if (ty + th + 4 > sh) {
            ty = sh - th - 4;
         }

         if (ty < 4) {
            ty = 4;
         }

         g.nextStratum();
         UiRenderer.rect(g, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), -267386864);
         UiRenderer.frame(g, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0, 1347420320);
         int ly = ty;

         for (Component c : lines) {
            g.text(this.font, c.getVisualOrderText(), tx, ly, -1, true);
            ly += 10;
         }
      } catch (Throwable var16) {
      }
   }

   private void renderShareToolbar(GuiGraphicsExtractor g, int x, int r1, int w, int mx, int my) {
      boolean in = this.mm.inLobby();
      boolean canShare = in && this.mm.canSend();
      Minecraft mc = Minecraft.getInstance();
      int gap = 4;
      int colW = (w - gap * 4) / 5;
      int r2 = r1 + 18;
      int qn = RiptideSharedState.get().getDelayedPackets().size();
      boolean hasMacros = !RiptideMacroManager.get().getAll().isEmpty();
      boolean onServer = MatchmakingManager.currentServerIp() != null;
      this.shareBtn(g, x, r1, colW, qn > 0 ? "Queue " + qn : "Queue", canShare && qn > 0, this::shareQueue, mx, my);
      this.shareBtn(g, x + colW + gap, r1, colW, "Macro", canShare && hasMacros, this::toggleMacroPicker, mx, my);
      this.shareBtn(g, x + 2 * (colW + gap), r1, colW, "GUI", canShare && this.guiOpen(), this::shareGui, mx, my);
      this.shareBtn(g, x + 3 * (colW + gap), r1, colW, "Item", canShare && this.holdingItem(), this::shareItem, mx, my);
      this.shareBtn(g, x + 4 * (colW + gap), r1, colW, "Server", canShare && onServer, this::shareServer, mx, my);
      this.shareBtn(g, x, r2, colW, "Pos", canShare && mc.player != null, this::sharePosition, mx, my);
      this.shareBtn(g, x + colW + gap, r2, colW, "TPA inv", canShare && onServer, this::shareTpaInvite, mx, my);
      this.shareBtn(g, x + 2 * (colW + gap), r2, colW, "Filter", canShare, this::shareFilter, mx, my);
      this.shareBtn(g, x + 3 * (colW + gap), r2, colW, "Module", canShare, this::toggleModulePicker, mx, my);
      this.shareBtn(g, x + 4 * (colW + gap), r2, colW, "Clear", this.mm.hasChat(), this::clearChat, mx, my);
   }

   private void toggleMacroPicker() {
      if (this.sharePicker == null) {
         this.sharePicker = new RiptideSharePickerOverlay(this.font);
      }

      this.sharePicker.open("Share Macro", "Search macros...", filter -> {
         List<RiptideSharePickerOverlay.Row> out = new ArrayList<>();
         String f = filter.toLowerCase(Locale.ROOT);

         for (RiptideMacro m : RiptideMacroManager.get().getAll()) {
            if (f.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(f)) {
               out.add(RiptideSharePickerOverlay.Row.item(m.name, m.actions.size() + " steps", () -> this.shareMacro(m)));
            }
         }

         return out;
      });
   }

   private void toggleModulePicker() {
      if (this.sharePicker == null) {
         this.sharePicker = new RiptideSharePickerOverlay(this.font);
      }

      this.sharePicker.open("Share Module", "Search modules...", filter -> {
         List<RiptideSharePickerOverlay.Row> out = new ArrayList<>();
         String f = filter.toLowerCase(Locale.ROOT);
         List<Module> mods = new ArrayList<>();

         for (Module m : ModuleRegistry.all()) {
            if (m.showInModuleMenu() && m.settingsShareable()) {
               mods.add(m);
            }
         }

         mods.sort(Comparator.comparing(mx -> categoryLabel(mx) + " " + mx.name(), String.CASE_INSENSITIVE_ORDER));
         String lastCat = null;

         for (Module mx : mods) {
            if (f.isEmpty() || mx.name().toLowerCase(Locale.ROOT).contains(f)) {
               String cat = categoryLabel(mx);
               if (!cat.equals(lastCat)) {
                  out.add(RiptideSharePickerOverlay.Row.header(cat));
                  lastCat = cat;
               }

               out.add(RiptideSharePickerOverlay.Row.item(mx.name(), null, () -> this.shareModule(m)));
            }
         }

         return out;
      });
   }

   private static String categoryLabel(Module m) {
      ModuleCategory c = m.category();
      return c == null ? "Other" : c.label();
   }

   private void shareBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean enabled, Runnable action, int mx, int my) {
      this.button(g, x, y, w, 16, label, enabled ? -7429889 : this.BORDER, this.TEXT, mx, my, enabled ? action : null);
   }

   private boolean guiOpen() {
      return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen;
   }

   private boolean holdingItem() {
      LocalPlayer p = Minecraft.getInstance().player;
      return p != null && !p.getMainHandItem().isEmpty();
   }

   private void shareGui() {
      MmMessages.BlobOffer b = MmBlobs.captureGui();
      if (b == null) {
         RiptideNotifications.show("Open a container to share its GUI.", -14249);
      } else {
         this.mm.offerBlob(b);
      }
   }

   private void shareItem() {
      MmMessages.BlobOffer b = MmBlobs.captureHeldItem();
      if (b == null) {
         RiptideNotifications.show("Hold an item to share it.", -14249);
      } else {
         this.mm.offerBlob(b);
      }
   }

   private void sharePosition() {
      MmMessages.BlobOffer b = MmBlobs.capturePosition();
      if (b != null) {
         this.mm.offerBlob(b);
      }
   }

   private void shareFilter() {
      MmMessages.BlobOffer b = MmBlobs.captureFilter();
      if (b != null) {
         this.mm.offerBlob(b);
      }
   }

   private void shareServer() {
      MmMessages.BlobOffer b = MmBlobs.captureServer();
      if (b == null) {
         RiptideNotifications.show("Join a server first.", -14249);
      } else {
         this.mm.offerBlob(b);
      }
   }

   private void clearChat() {
      this.mm.clearChat();
   }

   private void signInDiscord() {
      RiptideNotifications.show("Opening Discord sign-in in your browser…", -13248397);
      RiptideDiscordLogin.signIn(err -> {
         if (err.isEmpty()) {
            this.mm.openDirectory();
            RiptideNotifications.show("Signed in to matchmaking.", -13248397);
         } else if (!RiptideDiscordLogin.isGateCode(err)) {
            RiptideNotifications.show(RiptideDiscordLogin.errorMessage(err), -42149);
         }
      });
   }

   private void toggleKillSwitch() {
      boolean on = !this.prefs.killSwitch();
      this.prefs.setKillSwitch(on);
      RiptideNotifications.show(on ? "Kill switch on. Sharing off." : "Kill switch off.", on ? -14249 : -13248397);
   }

   private void signOutDiscord() {
      this.mm.leave();
      RiptideDiscordLogin.signOut();
      RiptideNotifications.show("Signed out of matchmaking.", -14249);
   }

   private void shareTpaInvite() {
      String name = this.mm.selfTpaName();
      if (name != null && !name.isBlank()) {
         this.mm.offerCommand("/tpa " + name);
      } else {
         RiptideNotifications.show("Can't read your username.", -14249);
      }
   }

   private void renderTextLine(GuiGraphicsExtractor g, MmChatLine line, Map<String, String> names, int x, int y, int w, int mx, int my) {
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      if (line.system) {
         this.drawWrapped(g, "• " + line.text, x, y, w, w, this.MUTED, fontId);
      } else {
         String who = this.chatName(line, names);
         int nameColor = MatchmakingManager.nameColor(line.senderFpHex, line.self);
         int endX = this.drawRoleName(g, who, x, y, line.senderFpHex, line.self);
         UiText.draw(g, this.font, ": ", fontId, nameColor, endX, y, false);
         int nameW = endX - x + UiText.width(this.font, ": ", fontId, nameColor);
         int rows = this.drawWrapped(g, line.text, x, y, Math.max(1, w - nameW), w, this.TEXT, fontId, x + nameW);
         int blockH = rows * 10 + 1;
         if (!line.text.isBlank() && my >= this.chatLogTop && my < this.chatLogBottom && mx >= x && mx < x + w && my >= y && my < y + blockH) {
            int bw = 34;
            this.button(g, x + w - bw, y, bw, 11, "Copy", this.BORDER, this.TEXT, mx, my, () -> this.copyText(line.text));
         }
      }
   }

   private int drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int firstW, int restW, int color, Identifier fontId) {
      return this.drawWrapped(g, text, x, y, firstW, restW, color, fontId, x);
   }

   private int drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int firstW, int restW, int color, Identifier fontId, int firstX) {
      List<int[]> segs = this.wrapChatRows(text, firstW, restW);

      for (int r = 0; r < segs.size(); r++) {
         int[] seg = segs.get(r);
         boolean first = r == 0;
         boolean overflow = seg[2] == 1;
         int rx = first ? firstX : x;
         int rw = first ? firstW : restW;
         int ry = y + r * 10;
         if (overflow) {
            this.drawText(g, text.substring(seg[0]), rx, ry, color, false, rw);
         } else {
            UiText.draw(g, this.font, text.substring(seg[0], seg[1]), fontId, color, rx, ry, false);
         }
      }

      return segs.size();
   }

   private List<int[]> wrapChatRows(String text, int firstW, int restW) {
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      String src = text == null ? "" : text;
      TextWrapLayout.RangeWidth rw = (s, e) -> UiText.width(this.font, src.substring(s, e), fontId, this.TEXT);
      List<int[]> rows = new ArrayList<>();
      int cursor = 0;
      int len = src.length();

      while (cursor < len && rows.size() < 3) {
         int width = Math.max(1, rows.isEmpty() ? firstW : restW);
         int nl = src.indexOf(10, cursor);
         int limit = nl >= 0 ? nl : len;
         boolean hard = false;
         int end;
         if (cursor >= limit) {
            end = cursor;
            hard = true;
         } else {
            end = TextWrapLayout.nextLineEnd(src, cursor, limit, width, rw);
            if (end <= cursor) {
               end = Math.min(limit, cursor + 1);
            }

            if (end >= limit && nl >= 0) {
               hard = true;
            }
         }

         int renderEnd = end;

         while (renderEnd > cursor && Character.isWhitespace(src.charAt(renderEnd - 1))) {
            renderEnd--;
         }

         rows.add(new int[]{cursor, renderEnd, 0});
         if (hard) {
            cursor = nl + 1;
         } else {
            cursor = end;

            while (cursor < len && src.charAt(cursor) == ' ') {
               cursor++;
            }
         }
      }

      if (rows.isEmpty()) {
         rows.add(new int[]{0, 0, 0});
      }

      if (cursor < len) {
         rows.get(rows.size() - 1)[2] = 1;
      }

      return rows;
   }

   private int[] chatLineHeights(List<MmChatLine> lines, int logW, Map<String, String> names) {
      int[] h = new int[lines.size()];

      for (int i = 0; i < lines.size(); i++) {
         h[i] = this.chatLineHeight(lines.get(i), logW, names);
      }

      return h;
   }

   private int chatLineHeight(MmChatLine line, int logW, Map<String, String> names) {
      return line.isCard() ? 30 : this.chatTextRows(line, logW, names) * 10 + 1;
   }

   private int chatTextRows(MmChatLine line, int logW, Map<String, String> names) {
      if (logW <= 0) {
         return 1;
      } else if (line.system) {
         String t = "• " + line.text;
         return t.isEmpty() ? 1 : this.wrapChatRows(t, logW, logW).size();
      } else if (line.text.isEmpty()) {
         return 1;
      } else {
         String who = this.chatName(line, names);
         int nameW = UiText.width(this.font, who + ": ", this.theme.fontFor(UiTone.BODY), MatchmakingManager.nameColor(line.senderFpHex, line.self));
         return this.wrapChatRows(line.text, Math.max(1, logW - nameW), logW).size();
      }
   }

   private static int sum(int[] a) {
      int s = 0;

      for (int v : a) {
         s += v;
      }

      return s;
   }

   private void copyText(String text) {
      this.copyToClipboard(text);
      RiptideNotifications.copied("Copied to clipboard.");
   }

   private String chatName(MmChatLine line, Map<String, String> names) {
      return line.self
         ? names.getOrDefault(this.mm.selfFpHex(), this.mm.selfDisplayName()) + " (you)"
         : names.getOrDefault(line.senderFpHex, this.mm.displayNameFor(line.senderFpHex));
   }

   private void renderCard(GuiGraphicsExtractor g, MmChatLine line, Map<String, String> names, int x, int y, int w, int mx, int my) {
      boolean tpa = this.isTpaInvite(line);
      int accent = this.cardAccent(line);
      Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, 28));
      ItemStack icon = line.kind == MmChatLine.Kind.BLOB_CARD && "item".equals(line.blob.kind) ? this.itemIcon(line.blob) : ItemStack.EMPTY;
      if (icon != null && !icon.isEmpty()) {
         g.item(icon, x + 5, y + 6);
         if (mx >= x + 5 && mx < x + 21 && my >= y + 6 && my < y + 22 && my >= this.chatLogTop && my < this.chatLogBottom) {
            this.chatTooltipItem = icon;
            this.chatTooltipMx = mx;
            this.chatTooltipMy = my;
         }
      } else {
         this.drawText(g, this.cardTag(line, tpa), x + 6, y + 4, accent, false, 52);
      }

      if (line.kind == MmChatLine.Kind.BLOB_CARD && "server".equals(line.blob.kind)) {
         this.drawText(g, MmBlobs.displayAddr(MmBlobs.serverIp(line.blob)), x + 54, y + 4, this.TEXT, false, w - 150);
         this.drawText(g, this.serverCardOnline(line.blob), x + 54, y + 15, this.MUTED, false, w - 150);
      } else {
         this.drawText(g, this.cardHead(line, tpa), x + 54, y + 4, this.TEXT, false, w - 64);
         this.drawText(
            g,
            line.self ? "you" : "from " + names.getOrDefault(line.senderFpHex, this.mm.displayNameFor(line.senderFpHex)),
            x + 54,
            y + 15,
            this.MUTED,
            false,
            w - 160
         );
      }

      int rightX = x + w - 6;
      rightX = this.cardBtn(g, rightX, y, "Copy", this.BORDER, mx, my, () -> this.copyCard(line));
      switch (line.kind) {
         case MACRO_CARD:
            this.cardBtn(
               g, rightX, y, "Inspect", -7429889, mx, my, () -> this.openMacroEditor(RiptideClipboardHelper.deserializeMacroFromBase64(line.macro.hash))
            );
            break;
         case COMMAND_CARD:
            if (tpa) {
               this.cardBtn(g, rightX, y, "Accept", this.ACCENT, mx, my, () -> this.mm.runCommandOffer(line.command));
            } else {
               rightX = this.cardBtn(g, rightX, y, "Fill", this.BORDER, mx, my, () -> this.fillCommand(line.command));
               this.cardBtn(g, rightX, y, "Execute", this.ACCENT, mx, my, () -> this.mm.runCommandOffer(line.command));
            }
            break;
         case PACKET_CARD:
            rightX = this.cardBtn(g, rightX, y, "Inspect", -7429889, mx, my, () -> this.openInspector(MmShare.inspectableEntry(line.packet)));
            this.cardBtn(g, rightX, y, "Add to queue", this.ACCENT, mx, my, () -> this.addToQueue(line.packet));
            break;
         case BLOB_CARD:
            this.renderBlobButtons(g, line.blob, rightX, y, mx, my);
      }
   }

   private boolean isTpaInvite(MmChatLine line) {
      return line.kind == MmChatLine.Kind.COMMAND_CARD
         && line.command != null
         && line.command.kind == 0
         && line.command.body != null
         && line.command.body.toLowerCase(Locale.ROOT).startsWith("tpa ");
   }

   private int cardAccent(MmChatLine line) {
      if (line.kind == MmChatLine.Kind.BLOB_CARD) {
         String var2 = line.blob.kind;

         return switch (var2) {
            case "item", "gui", "steps" -> this.ACCENT;
            case "filter" -> -14249;
            default -> -7429889;
         };
      } else {
         return switch (line.kind) {
            case MACRO_CARD -> this.ACCENT;
            case COMMAND_CARD -> -14249;
            default -> -7429889;
         };
      }
   }

   private String cardTag(MmChatLine line, boolean tpa) {
      if (line.kind == MmChatLine.Kind.BLOB_CARD) {
         String var3 = line.blob.kind;

         return switch (var3) {
            case "gui" -> "GUI";
            case "item" -> "ITEM";
            case "filter" -> "FILTER";
            case "position" -> "POS";
            case "server" -> "SERVER";
            case "steps" -> "STEPS";
            case "module" -> "MODULE";
            default -> "SHARE";
         };
      } else {
         return switch (line.kind) {
            case MACRO_CARD -> "MACRO";
            case COMMAND_CARD -> tpa ? "TPA" : "CMD";
            case PACKET_CARD -> "QUEUE";
            default -> "SHARE";
         };
      }
   }

   private String cardHead(MmChatLine line, boolean tpa) {
      if (line.kind == MmChatLine.Kind.COMMAND_CARD) {
         String rendered = MatchmakingManager.renderCommandOffer(line.command);
         return tpa ? "TPA invite → " + rendered : rendered;
      } else if (line.kind == MmChatLine.Kind.BLOB_CARD) {
         String rendered = line.blob.kind;

         return switch (rendered) {
            case "gui" -> line.blob.friendlyName + "  (" + line.blob.count + " slots)";
            case "filter" -> "Packet Filter  (" + line.blob.count + " types)";
            case "module" -> line.blob.friendlyName + "  (" + line.blob.count + " settings)";
            default -> line.blob.friendlyName;
         };
      } else {
         return line.cardHeadline();
      }
   }

   private String serverCardOnline(MmMessages.BlobOffer b) {
      int players = MmBlobs.serverPlayers(b);
      int max = MmBlobs.serverPlayersMax(b);
      int ping = MmBlobs.serverPing(b);
      StringBuilder sb = new StringBuilder();
      sb.append(players);
      if (max > 0) {
         sb.append("/").append(max);
      }

      sb.append(" online");
      if (ping >= 0) {
         sb.append("  ·  ").append(ping).append("ms");
      }

      return sb.toString();
   }

   private void renderBlobButtons(GuiGraphicsExtractor g, MmMessages.BlobOffer b, int rightX, int y, int mx, int my) {
      String var7 = b.kind;
      switch (var7) {
         case "gui":
            this.cardBtn(g, rightX, y, "View", this.ACCENT, mx, my, () -> this.openGuiView(b));
            break;
         case "item":
            this.cardBtn(g, rightX, y, "Inspect", this.ACCENT, mx, my, () -> this.openItemInspect(MmBlobs.decodeItem(b)));
            break;
         case "filter":
            rightX = this.cardBtn(g, rightX, y, "Inspect", -7429889, mx, my, () -> this.openFilterView(b));
            this.cardBtn(g, rightX, y, "Import", this.ACCENT, mx, my, () -> {
               int n = MmBlobs.importFilter(b);
               RiptideNotifications.show("Imported " + n + " filtered packet(s).", this.ACCENT);
            });
            break;
         case "server":
            this.cardBtn(g, rightX, y, "Join", this.ACCENT, mx, my, () -> this.joinSharedServer(b));
            break;
         case "steps":
            rightX = this.cardBtn(
               g, rightX, y, "To editor", -7429889, mx, my, () -> this.openMacroEditor(RiptideClipboardHelper.deserializeMacroFromBase64(b.data))
            );
            this.cardBtn(g, rightX, y, "Import", this.ACCENT, mx, my, () -> this.importSteps(b));
            break;
         case "module":
            rightX = this.cardBtn(g, rightX, y, "Preview", -7429889, mx, my, () -> this.openModuleView(b));
            this.cardBtn(g, rightX, y, "Apply", this.ACCENT, mx, my, () -> this.applyModuleBlob(b));
      }
   }

   private int cardBtn(GuiGraphicsExtractor g, int rightX, int rowY, String label, int border, int mx, int my, Runnable action) {
      int bw = Math.max(34, UiText.width(this.font, label, this.theme.fontFor(UiTone.BODY), this.TEXT) + 10);
      int bx = rightX - bw;
      this.button(g, bx, rowY + 11, bw, 14, label, border, this.TEXT, mx, my, action);
      return bx - 4;
   }

   private void doSend() {
      if (!this.mm.inLobby()) {
         RiptideNotifications.show("Join a lobby first.", -14249);
      } else {
         String text = this.chatInput.text().trim();
         if (!text.isEmpty()) {
            String type = text.length() >= 24 ? RiptideClipboardHelper.detectShareType(text) : null;
            if ("riptide_macro_steps".equals(type)) {
               MmMessages.BlobOffer steps = this.buildStepsOffer(text);
               if (steps != null) {
                  this.mm.offerBlob(steps);
                  this.chatInput.setText("");
                  return;
               }
            } else if (type != null && type.startsWith("riptide_macro")) {
               RiptideMacro macro = RiptideClipboardHelper.deserializeMacroFromBase64(text);
               MmMessages.MacroOffer offer = MmShare.buildMacroOffer(macro);
               if (offer != null) {
                  this.mm.offerMacro(offer);
                  this.chatInput.setText("");
                  return;
               }
            } else if ("packets".equals(type)) {
               List<RiptideSharedState.QueuedPacket> q = RiptideClipboardHelper.deserializeQueueFromBase64(text);
               int count = q == null ? 0 : q.size();
               MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
               offer.friendlyName = count <= 0 ? "shared packets" : count + (count == 1 ? " packet" : " packets");
               offer.data = text;
               this.mm.offerPacket(offer);
               this.chatInput.setText("");
               return;
            }

            MmMessages.BlobOffer blob = text.length() >= 24 ? MmBlobs.decodeOffer(text) : null;
            if (blob != null) {
               this.mm.offerBlob(blob);
               this.chatInput.setText("");
            } else if (!text.startsWith("/") && !RiptideCommands.isRiptideCommandMessage(text)) {
               this.mm.sendChat(text);
               this.chatInput.setText("");
            } else {
               this.mm.offerCommand(text);
               this.chatInput.setText("");
            }
         }
      }
   }

   private void shareQueue() {
      List<RiptideSharedState.QueuedPacket> q = RiptideSharedState.get().getDelayedPackets();
      MmMessages.PacketOffer offer = MmShare.buildPacketOffer(q, q.size() + (q.size() == 1 ? " packet" : " packets"));
      if (offer == null) {
         RiptideNotifications.show("Your packet queue is empty.", -14249);
      } else {
         this.mm.offerPacket(offer);
      }
   }

   private void shareMacro(RiptideMacro macro) {
      MmMessages.MacroOffer offer = MmShare.buildMacroOffer(macro);
      if (offer != null) {
         this.mm.offerMacro(offer);
      }
   }

   private MmMessages.BlobOffer buildStepsOffer(String stepsHash) {
      RiptideMacro macro = RiptideClipboardHelper.deserializeMacroFromBase64(stepsHash);
      if (macro != null && macro.actions != null && !macro.actions.isEmpty()) {
         int count = macro.actions.size();
         String label = count == 1 ? (macro.actions.get(0) == null ? "1 step" : macro.actions.get(0).getDisplayName()) : count + " macro steps";
         return new MmMessages.BlobOffer("steps", label, count, stepsHash);
      } else {
         return null;
      }
   }

   private void shareModule(Module m) {
      MmMessages.BlobOffer b = MmBlobs.captureModule(m);
      if (b == null) {
         RiptideNotifications.show("Could not capture module settings.", this.ERROR);
      } else {
         this.mm.offerBlob(b);
      }
   }

   private void importSteps(MmMessages.BlobOffer b) {
      MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
      offer.hash = b.data;
      offer.macroName = b.friendlyName;
      MmShare.importMacro(offer);
   }

   private void openModuleView(MmMessages.BlobOffer b) {
      if (this.moduleViewOverlay == null) {
         this.moduleViewOverlay = new RiptideModuleViewOverlay(this.font);
      }

      if (this.moduleViewOverlay.open(b)) {
         this.presentOverlay(this.moduleViewOverlay, true);
      }
   }

   private void applyModuleBlob(MmMessages.BlobOffer b) {
      int n = MmBlobs.applyModule(b);
      if (n < 0) {
         RiptideNotifications.show("Can't apply. Bad data.", this.ERROR);
      } else {
         RiptideNotifications.show("Applied " + n + " setting(s) to " + MmBlobs.moduleName(b) + ".", this.ACCENT);
      }
   }

   private void addToQueue(MmMessages.PacketOffer offer) {
      int n = MmShare.addToQueue(offer);
      if (n < 0) {
         RiptideNotifications.show("Could not read shared queue.", this.ERROR);
      } else {
         RiptideNotifications.show("Added " + n + " packet(s) to your queue.", this.ACCENT);
      }
   }

   private void copyCard(MmChatLine line) {
      this.copyToClipboard(MmCardActions.clipboardTextFor(line));
      RiptideNotifications.copied("Copied to clipboard.");
   }

   private void fillCommand(MmMessages.CommandOffer c) {
      String text = MatchmakingManager.renderCommandOffer(c);
      Minecraft.getInstance().gui.setScreen(new ChatScreen(text, false));
   }

   private void joinSharedServer(MmMessages.BlobOffer b) {
      MmCardActions.confirmJoinServer(MmBlobs.serverIp(b), MmBlobs.serverName(b), this.host.returnScreen());
   }

   private void renderMembers(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {
      UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
      this.hitGateTop = top;
      this.hitGateBottom = bottom;

      int y;
      try {
         y = this.renderMembersList(g, x, top, w, bottom, mx, my);
      } finally {
         this.hitGateTop = Integer.MIN_VALUE;
         this.hitGateBottom = Integer.MAX_VALUE;
         UiScissorStack.global().pop(g);
      }

      this.setContentBottom(MatchmakingPanel.Tab.MEMBERS, y);
   }

   private int renderMembersList(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {
      int y = top - this.scrollOf(MatchmakingPanel.Tab.MEMBERS);
      String code = this.mm.currentShareCode();
      if (!code.isBlank()) {
         Lobby clb = this.mm.currentLobby();
         boolean pub = clb != null && clb.isPublic;
         Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, 20));
         this.drawText(g, pub ? "Room Code:" : "Room Key:", x + 8, y + 6, this.MUTED, false, 70);
         this.drawText(g, code, x + 78, y + 6, this.ACCENT, false, w - 78 - 60);
         this.button(g, x + w - 54, y + 2, 48, 16, "Copy", this.ACCENT, this.TEXT, mx, my, () -> {
            this.copyToClipboard(code);
            RiptideNotifications.copied(pub ? "Room code copied." : "Room key copied.");
         });
         y += 24;
      }

      this.drawText(g, "Members", x, y, this.TEXT, false, w);
      y += 16;
      List<MmPeer> peers = this.mm.members();
      Map<String, String> names = this.disambiguate(peers);
      Lobby lb = this.mm.currentLobby();
      long now = System.currentTimeMillis();
      String selfLabel = names.getOrDefault(this.mm.selfFpHex(), this.mm.selfDisplayName());
      boolean selfHost = this.mm.isHost();
      String selfServerLine = this.selfServerLine();
      String selfWorldLine = this.selfWorldLine();
      String selfPosLine = this.selfPosLine();
      int selfExtra = (selfServerLine.isEmpty() ? 0 : 1) + (selfWorldLine.isEmpty() ? 0 : 1) + (selfPosLine.isEmpty() ? 0 : 1);
      int selfH = 22 + selfExtra * 10;
      Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, selfH));
      String selfRole = lb != null && lb.announcement ? (RiptideDiscordLogin.isAdmin() ? "  [Admin]" : (this.mm.canSend() ? "  [Speaker]" : "")) : "";
      int selfEndX = this.drawRoleName(g, selfLabel, x + 8, y + 3, this.mm.selfFpHex(), true);
      this.drawText(g, "  (you)" + (selfHost ? "  (host)" : "") + selfRole, selfEndX, y + 3, this.MUTED, false, Math.max(1, x + w - 8 - selfEndX));
      int sly = y + 13;
      if (!selfServerLine.isEmpty()) {
         this.drawText(g, selfServerLine, x + 8, sly, -7429889, false, w - 16);
         sly += 10;
      }

      if (!selfWorldLine.isEmpty()) {
         this.drawText(g, selfWorldLine, x + 8, sly, -7429889, false, w - 16);
         sly += 10;
      }

      if (!selfPosLine.isEmpty()) {
         this.drawText(g, selfPosLine, x + 8, sly, -7429889, false, w - 16);
      }

      y += selfH + 3;
      if (peers.isEmpty()) {
         this.drawText(g, "No other members yet.", x + 4, y + 2, this.MUTED, false, w);
         y += 14;
      }

      for (MmPeer p : peers) {
         String serverLine = p.serverShared && !p.serverIp.isBlank() ? "Server: " + MmBlobs.displayAddr(p.serverIp) : "";
         String worldLine = p.hasLocation ? "world: " + shortDim(p.dimension) : "";
         String posLine = p.hasLocation ? "position: " + (int)p.x + " " + (int)p.y + " " + (int)p.z : "";
         int extraLines = (serverLine.isEmpty() ? 0 : 1) + (worldLine.isEmpty() ? 0 : 1) + (posLine.isEmpty() ? 0 : 1);
         int rowH = 22 + extraLines * 10;
         boolean visible = y + rowH > top && y < bottom;
         if (visible) {
            boolean isHost = p.fpHex.equals(this.mm.actingHostFp());
            Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, rowH));
            int dot = p.isOnline(now) ? this.ACCENT : this.MUTED;
            UiRenderer.rect(g, UiBounds.of(x + 6, y + 5, 6, 6), dot);
            String label = names.getOrDefault(p.fpHex, p.displayName());
            String suffix = (isHost ? "  (host)" : "")
               + (lb != null && lb.announcement ? (p.admin ? "  [Admin]" : (p.speaker ? "  [Speaker]" : "")) : "")
               + (p.muted ? "  (muted)" : "");
            int endX = this.drawRoleName(g, label, x + 18, y + 3, p.fpHex, false);
            if (!suffix.isEmpty()) {
               this.drawText(g, suffix, endX, y + 3, this.MUTED, false, Math.max(1, x + w - 6 - endX));
            }

            int ly = y + 13;
            if (!serverLine.isEmpty()) {
               this.drawText(g, serverLine, x + 18, ly, -7429889, false, w - 16);
               ly += 10;
            }

            if (!worldLine.isEmpty()) {
               this.drawText(g, worldLine, x + 18, ly, -7429889, false, w - 16);
               ly += 10;
            }

            if (!posLine.isEmpty()) {
               this.drawText(g, posLine, x + 18, ly, -7429889, false, w - 16);
            }

            String fpHex = p.fpHex;
            this.memberRows.add(new MatchmakingPanel.MemberRow(x, y, w, rowH, fpHex, this.hitGateTop, this.hitGateBottom));
            int kx = x + w - 16;
            int menuY = y + 17;
            boolean kHover = mx >= kx - 2 && mx < x + w && my >= y && my < y + 18;
            this.drawText(g, "⋮", kx + 3, y + 3, kHover ? this.TEXT : this.MUTED, false);
            this.addHotspot(kx - 2, y, 18, 18, () -> this.openContextMenu(fpHex, kx - 2, menuY));
         }

         y += rowH + 3;
      }

      return y;
   }

   private String selfServerLine() {
      if (!this.mm.effectiveShareServer()) {
         return "";
      } else {
         ServerData sd = Minecraft.getInstance().getCurrentServer();
         return sd != null && sd.ip != null && !sd.ip.isBlank() ? "Server: " + MmBlobs.displayAddr(sd.ip) : "";
      }
   }

   private String selfWorldLine() {
      if (!this.mm.effectiveShareLocation()) {
         return "";
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.player != null && mc.player.level() != null ? "world: " + shortDim(mc.player.level().dimension().identifier().toString()) : "";
      }
   }

   private String selfPosLine() {
      if (!this.mm.effectiveShareLocation()) {
         return "";
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.player == null ? "" : "position: " + (int)mc.player.getX() + " " + (int)mc.player.getY() + " " + (int)mc.player.getZ();
      }
   }

   private Map<String, String> disambiguate(List<MmPeer> peers) {
      String self = this.mm.selfDisplayName();
      long rosterVersion = this.mm.rosterUiVersion();
      if (rosterVersion == this.cachedNamesRosterVersion && peers.size() == this.cachedNamesMemberCount && self.equals(this.cachedNamesSelf)) {
         return this.cachedNames;
      } else {
         List<String[]> order = new ArrayList<>();
         order.add(new String[]{this.mm.selfFpHex(), self});

         for (MmPeer p : peers) {
            order.add(new String[]{p.fpHex, p.displayName()});
         }

         Map<String, Integer> seen = new HashMap<>();
         Map<String, String> out = new HashMap<>();

         for (String[] e : order) {
            String key = e[1].toLowerCase(Locale.ROOT);
            int count = seen.merge(key, 1, Integer::sum);
            out.put(e[0], count == 1 ? e[1] : e[1] + " (" + (count - 1) + ")");
         }

         this.cachedNamesRosterVersion = rosterVersion;
         this.cachedNamesMemberCount = peers.size();
         this.cachedNamesSelf = self;
         this.cachedNames = Map.copyOf(out);
         return this.cachedNames;
      }
   }

   private void confirmJoinServer(MmPeer peer) {
      String name = peer.serverName != null && !peer.serverName.isBlank() ? peer.serverName : peer.displayName();
      MmCardActions.confirmJoinServer(peer.serverIp, name, this.host.returnScreen());
   }

   private void payPeer(String name) {
      Minecraft.getInstance().gui.setScreen(new ChatScreen("/pay " + name + " ", false));
   }

   private static String shortDim(String dim) {
      if (dim == null) {
         return "?";
      } else {
         int i = dim.indexOf(58);
         return i >= 0 ? dim.substring(i + 1) : dim;
      }
   }

   private void renderSettings(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {
      UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
      this.hitGateTop = top;
      this.hitGateBottom = bottom;

      try {
         int y = top - this.scrollOf(MatchmakingPanel.Tab.SETTINGS);
         y = this.toggle(g, x, y, w, "Share my server", this.prefs.shareServer(), mx, my, () -> this.prefs.setShareServer(!this.prefs.shareServer()));
         y = this.toggle(g, x, y, w, "Share my position", this.prefs.shareLocation(), mx, my, () -> this.prefs.setShareLocation(!this.prefs.shareLocation()));
         y = this.toggle(g, x, y, w, "Kill switch (block sharing)", this.prefs.killSwitch(), mx, my, this::toggleKillSwitch);
         y = this.toggle(g, x, y, w, "Debug logging (console)", this.prefs.debugLog(), mx, my, () -> this.prefs.setDebugLog(!this.prefs.debugLog()));
         y += 4;
         this.drawText(g, "Public Settings", x, y, this.TEXT, false, w);
         y += 14;
         y = this.toggle(
            g, x, y, w, "Auto-join public lobby", this.prefs.autoJoinPublic(), mx, my, () -> this.prefs.setAutoJoinPublic(!this.prefs.autoJoinPublic())
         );
         Lobby olb = this.mm.currentLobbyOfficial() ? this.mm.currentLobby() : null;
         if (olb != null) {
            String olId = olb.lobbyId;
            y = this.toggle(
               g,
               x,
               y,
               w,
               "Share my server here",
               this.prefs.lobbyShareServer(olId),
               mx,
               my,
               () -> this.prefs.setLobbyShareServer(olId, !this.prefs.lobbyShareServer(olId))
            );
            y = this.toggle(
               g,
               x,
               y,
               w,
               "Share my position here",
               this.prefs.lobbyShareLocation(olId),
               mx,
               my,
               () -> this.prefs.setLobbyShareLocation(olId, !this.prefs.lobbyShareLocation(olId))
            );
         }

         Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, 18));
         this.drawText(g, "Signed in: " + this.mm.selfDisplayName(), x + 8, y + 5, this.MUTED, false, w - 72);
         this.button(g, x + w - 62, y + 1, 56, 16, "Sign out", this.ERROR, this.TEXT, mx, my, this::signOutDiscord);
         y += 22;
         y = this.renderModerationList(g, x, y, w, mx, my, "Blocked", this.prefs.blockedDiscordSnapshot(), true);
         y = this.renderModerationList(g, x, y, w, mx, my, "Banned (your lobbies)", this.prefs.bannedDiscordSnapshot(), false);
         this.setContentBottom(MatchmakingPanel.Tab.SETTINGS, y);
      } finally {
         this.hitGateTop = Integer.MIN_VALUE;
         this.hitGateBottom = Integer.MAX_VALUE;
         UiScissorStack.global().pop(g);
      }
   }

   private int renderModerationList(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, String title, Map<String, String> entries, boolean block) {
      y += 4;
      this.drawText(g, title, x, y, this.TEXT, false, w);
      y += 14;
      if (entries.isEmpty()) {
         this.drawText(g, "None.", x + 6, y + 2, this.MUTED, false, w);
         return y + 14;
      } else {
         for (Entry<String, String> e : entries.entrySet()) {
            String id = e.getKey();
            String label = e.getValue() != null && !e.getValue().isBlank() ? e.getValue() : "Discord member";
            Card.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, 18));
            this.drawText(g, label, x + 8, y + 5, this.TEXT, false, w - 76);
            this.button(g, x + w - 70, y + 1, 64, 16, block ? "Unblock" : "Unban", this.ACCENT, this.TEXT, mx, my, () -> {
               if (block) {
                  this.mm.unblockDiscord(id);
               } else {
                  this.mm.unbanDiscord(id);
               }
            });
            y += 21;
         }

         return y;
      }
   }

   private void openInspector(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry == null) {
         RiptideNotifications.show("Could not rebuild packet to inspect.", this.ERROR);
      } else {
         if (this.inspectOverlay == null) {
            this.inspectOverlay = new RiptidePacketInspectOverlay(this.font);
         }

         this.inspectOverlay.open(entry, 60, 60);
         this.presentOverlay(this.inspectOverlay, true);
      }
   }

   private void openItemInspect(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         RiptideItemNbtInspectOverlay ov = RiptideItemNbtInspectOverlay.getSharedOverlay(this.font);
         if (ov != null) {
            ov.open(stack, 60, 60);
            this.presentOverlay(ov, true);
         }
      } else {
         RiptideNotifications.show("Could not read shared item.", this.ERROR);
      }
   }

   private void openGuiView(MmMessages.BlobOffer blob) {
      if (this.guiViewOverlay == null) {
         this.guiViewOverlay = new RiptideGuiViewOverlay(this.font);
      }

      if (this.guiViewOverlay.open(blob)) {
         this.presentOverlay(this.guiViewOverlay, true);
      }
   }

   private void openFilterView(MmMessages.BlobOffer blob) {
      if (this.filterViewOverlay == null) {
         this.filterViewOverlay = new RiptideFilterViewOverlay(this.font);
      }

      if (this.filterViewOverlay.open(blob)) {
         this.presentOverlay(this.filterViewOverlay, true);
      }
   }

   private void openMacroEditor(RiptideMacro macro) {
      if (macro == null) {
         RiptideNotifications.show("Could not read shared macro.", this.ERROR);
      } else {
         RiptideMacroEditorOverlay ed = RiptideMacroEditorOverlay.getSharedOverlay();
         if (ed != null) {
            ed.openForImport(macro);
            this.presentOverlay(ed, false);
         }
      }
   }

   private void presentOverlay(IRiptideOverlay ov, boolean background) {
      if (ov != null) {
         if (background) {
            RiptideOverlayManager.get().register(ov, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
         } else {
            RiptideOverlayManager.get().register(ov);
         }

         RiptideOverlayManager.get().bringToFront(ov);
      }
   }

   private void copyToClipboard(String s) {
      Minecraft.getInstance().keyboardHandler.setClipboard(s == null ? "" : s);
   }

   public boolean mouseClicked(int mx, int my, int button) {
      this.lastMx = mx;
      this.lastMy = my;
      if (this.ctxOpen && button == 0) {
         for (MatchmakingPanel.CtxItem it : this.ctxItems) {
            if (it.hit(mx, my)) {
               try {
                  it.action.run();
               } catch (Throwable var9) {
                  RiptideNotifications.show("Action failed: " + var9.getClass().getSimpleName(), this.ERROR);
               }

               if (it.closes) {
                  this.closeContextMenu();
               }

               return true;
            }
         }

         this.closeContextMenu();
         return true;
      } else if (button == 1) {
         for (MatchmakingPanel.MemberRow r : this.memberRows) {
            if (r.hit(mx, my)) {
               this.openContextMenu(r.fp(), mx, my);
               return true;
            }
         }

         if (this.ctxOpen) {
            this.closeContextMenu();
            return true;
         } else {
            return mx >= this.bx && mx < this.bx + this.bw && my >= this.by && my < this.by + this.bh;
         }
      } else if (button == 0 && this.activeTab == MatchmakingPanel.Tab.CHAT && this.chatScrollbar != null && this.chatScrollbar.contains(mx, my)) {
         this.chatScrollbarDragging = true;
         this.chatScrollGrab = my - this.chatScrollbar.thumbY();
         this.clearFocus();
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);

         for (CompactTextInput in : this.activeInputs) {
            if (in.mouseClicked(ctx, mx, my, button)) {
               for (CompactTextInput other : this.activeInputs) {
                  if (other != in) {
                     other.setFocused(false);
                  }
               }

               return true;
            }
         }

         this.clearFocus();
         if (button == 0) {
            for (MatchmakingPanel.Hotspot h : this.hotspots) {
               if (h.hit(mx, my)) {
                  try {
                     h.action.run();
                  } catch (Throwable var10) {
                     RiptideNotifications.show("Action failed: " + var10.getClass().getSimpleName(), this.ERROR);
                  }

                  return true;
               }
            }
         }

         return mx >= this.bx && mx < this.bx + this.bw && my >= this.by && my < this.by + this.bh;
      }
   }

   public boolean mouseReleased(int mx, int my, int button) {
      if (this.chatScrollbarDragging) {
         this.chatScrollbarDragging = false;
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);
         boolean any = false;

         for (CompactTextInput in : this.activeInputs) {
            any |= in.mouseReleased(ctx, mx, my, button);
         }

         return any;
      }
   }

   public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
      if (this.chatScrollbarDragging && this.chatScrollbar != null) {
         int topVal = CompactScrollbar.scrollFromThumb(this.chatScrollbar, my, this.chatScrollGrab);
         this.scroll[MatchmakingPanel.Tab.CHAT.ordinal()] = Math.max(0, Math.min(this.chatMaxScrollPx, this.chatMaxScrollPx - topVal));
         return true;
      } else {
         DirectRenderContext ctx = this.renderCtx(null, mx, my);
         boolean any = false;

         for (CompactTextInput in : this.activeInputs) {
            any |= in.mouseDragged(ctx, mx, my, button, (float)dx, (float)dy);
         }

         return any;
      }
   }

   public boolean mouseScrolled(int mx, int my, double amount) {
      if (this.ctxOpen) {
         this.closeContextMenu();
      }

      if (this.activeTab == MatchmakingPanel.Tab.CHAT) {
         int max = this.chatMaxScrollPx;
         this.scroll[MatchmakingPanel.Tab.CHAT.ordinal()] = Math.max(
            0, Math.min(max, this.scroll[MatchmakingPanel.Tab.CHAT.ordinal()] + (int)Math.signum(amount) * 18)
         );
         return true;
      } else {
         int max = this.maxScrollFor(this.activeTab);
         if (max <= 0) {
            return false;
         } else {
            this.scroll[this.activeTab.ordinal()] = Math.max(0, Math.min(max, this.scroll[this.activeTab.ordinal()] - (int)Math.signum(amount) * 16));
            return true;
         }
      }
   }

   private int scrollOf(MatchmakingPanel.Tab tab) {
      return tab == MatchmakingPanel.Tab.CHAT ? 0 : this.scroll[tab.ordinal()];
   }

   private void setContentBottom(MatchmakingPanel.Tab tab, int renderedBottomY) {
      if (tab == this.activeTab) {
         this.contentHeight[tab.ordinal()] = Math.max(0, renderedBottomY + this.scrollOf(tab) - this.lastContentTop);
      }
   }

   private int maxScrollFor(MatchmakingPanel.Tab tab) {
      return tab == MatchmakingPanel.Tab.CHAT ? 0 : Math.max(0, this.contentHeight[tab.ordinal()] - this.availableContentH);
   }

   public int desiredHeight() {
      if (!RiptideDiscordLogin.hasSession()) {
         return 175;
      } else {
         return this.activeTab == MatchmakingPanel.Tab.CHAT
            ? this.chromeHeight + 60 + Math.max(14, this.chatContentPx(this.lastChatLogFullW, this.disambiguate(this.mm.members()))) + 8
            : this.chromeHeight + Math.max(24, this.contentHeight[this.activeTab.ordinal()]) + 8;
      }
   }

   private int chatContentPx(int logW, Map<String, String> names) {
      int v = this.mm.chatVersion();
      if (v != this.cachedChatPxVersion || logW != this.cachedChatPxWidth) {
         int px = 0;

         for (MmChatLine line : this.mm.chatSnapshot()) {
            px += this.chatLineHeight(line, logW, names);
         }

         this.cachedChatPx = px;
         this.cachedChatPxVersion = v;
         this.cachedChatPxWidth = logW;
      }

      return this.cachedChatPx;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      DirectRenderContext ctx = this.renderCtx(null, this.lastMx, this.lastMy);

      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused() && in.keyPressed(ctx, keyCode, scanCode, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(char chr, int modifiers) {
      DirectRenderContext ctx = this.renderCtx(null, this.lastMx, this.lastMy);

      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused() && in.charTyped(ctx, chr, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasFocusedTextInput() {
      for (CompactTextInput in : this.activeInputs) {
         if (in.isFocused()) {
            return true;
         }
      }

      return false;
   }

   public void clearFocus() {
      this.chatInput.setFocused(false);
      this.lobbyName.setFocused(false);
      this.server.setFocused(false);
      this.maxPlayers.setFocused(false);
      this.passphrase.setFocused(false);
      this.joinCode.setFocused(false);
      this.closeContextMenu();
   }

   private DirectRenderContext renderCtx(GuiGraphicsExtractor g, int mx, int my) {
      return new DirectRenderContext(g, this.font, DirectViewport.current(1.0F), this.theme, mx, my, this.delta);
   }

   private void placeField(GuiGraphicsExtractor g, DirectRenderContext ctx, CompactTextInput f, int x, int y, int w) {
      this.placeField(g, ctx, f, x, y, w, 16);
   }

   private void placeField(GuiGraphicsExtractor g, DirectRenderContext ctx, CompactTextInput f, int x, int y, int w, int h) {
      f.setBounds(x, y, w, h);
      f.render(ctx);
      if (y + h > this.hitGateTop && y < this.hitGateBottom) {
         this.activeInputs.add(f);
      }
   }

   private void openContextMenu(String fp, int atX, int atY) {
      this.ctxOpen = true;
      this.ctxFp = fp;
      this.ctxX = atX;
      this.ctxY = atY;
      this.ctxBanConfirm = false;
   }

   private void closeContextMenu() {
      this.ctxOpen = false;
      this.ctxBanConfirm = false;
      this.ctxItems.clear();
   }

   private void renderContextMenu(GuiGraphicsExtractor g, int mx, int my) {
      this.ctxItems.clear();
      if (this.ctxOpen && this.activeTab == MatchmakingPanel.Tab.MEMBERS) {
         MmPeer p = this.mm.peer(this.ctxFp);
         if (p == null) {
            this.ctxOpen = false;
         } else {
            record Act(String label, int color, Runnable run, boolean closes) {
            }

            List<Act> acts = new ArrayList<>();
            String tpaName = p.commandName();
            boolean coLocated = tpaName.matches("[A-Za-z0-9_]{1,16}") && MatchmakingManager.sameServerAs(p);
            if (coLocated) {
               acts.add(new Act("TPA", -7429889, () -> this.mm.tpaPeer(tpaName), true));
               acts.add(new Act("Trade", -7429889, () -> this.mm.tradePeer(tpaName), true));
               acts.add(new Act("Pay", -7429889, () -> this.payPeer(tpaName), true));
            }

            if (p.serverShared && !p.serverIp.isBlank() && !MatchmakingManager.alreadyOn(p.serverIp)) {
               acts.add(new Act("Join server", -7429889, () -> this.confirmJoinServer(p), true));
            }

            if (p.hasLocation) {
               acts.add(new Act("Copy coords", this.TEXT, () -> {
                  this.copyToClipboard((int)p.x + " " + (int)p.y + " " + (int)p.z);
                  RiptideNotifications.copied("Coords copied.");
               }, true));
            }

            if (this.mm.canManageSpeakers() && !p.admin) {
               if (p.speaker) {
                  acts.add(new Act("Revoke sending", -14249, () -> this.mm.setSpeaker(p.fpHex, false), true));
               } else {
                  acts.add(new Act("Allow sending", this.ACCENT, () -> this.mm.setSpeaker(p.fpHex, true), true));
               }
            }

            if (this.mm.isHost() && !p.fpHex.equals(this.mm.selfFpHex()) && !p.fpHex.equals(this.mm.actingHostFp())) {
               acts.add(new Act("Make host", this.ACCENT, () -> this.mm.makeHost(p.fpHex), true));
            }

            if (p.muted) {
               acts.add(new Act("Unblock", this.ACCENT, () -> this.mm.unblockPeer(p.fpHex), true));
            } else {
               acts.add(new Act("Block", this.ERROR, () -> this.mm.blockPeer(p.fpHex), true));
            }

            if (this.mm.isHost() && !p.fpHex.equals(this.mm.selfFpHex())) {
               if (this.ctxBanConfirm) {
                  acts.add(new Act("Confirm ban", -14249, () -> this.mm.banPeer(p.fpHex), true));
               } else {
                  acts.add(new Act("Ban", this.ERROR, () -> this.ctxBanConfirm = true, false));
               }
            }

            int itemH = 15;
            int pad = 8;
            int wMenu = 96;

            for (Act a : acts) {
               wMenu = Math.max(wMenu, this.font.width(a.label()) + pad * 2);
            }

            int hMenu = acts.size() * itemH + 4;
            int mxv = Math.max(this.bx + 2, Math.min(this.ctxX, this.bx + this.bw - wMenu - 2));
            int myv = Math.max(this.by + 2, Math.min(this.ctxY, this.by + this.bh - hMenu - 2));
            UiRenderer.frame(g, UiBounds.of(mxv, myv, wMenu, hMenu), -233170402, this.BORDER_BRIGHT);
            int iy = myv + 2;

            for (Act a : acts) {
               boolean hover = mx >= mxv && mx < mxv + wMenu && my >= iy && my < iy + itemH;
               if (hover) {
                  UiRenderer.rect(g, UiBounds.of(mxv + 1, iy, wMenu - 2, itemH), tint(a.color(), 51));
               }

               this.drawText(g, a.label(), mxv + pad, iy + (itemH - 8) / 2, a.color(), false, wMenu - pad);
               this.ctxItems.add(new MatchmakingPanel.CtxItem(mxv, iy, wMenu, itemH, a.label(), a.color(), a.run(), a.closes()));
               iy += itemH;
            }
         }
      }
   }

   private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int border, int textColor, int mx, int my, Runnable action) {
      boolean enabled = action != null;
      boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
      Button.Tone tone = border != this.ACCENT && textColor != this.ACCENT ? Button.Tone.NORMAL : Button.Tone.PRIMARY;
      Button.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, tone, hover, false);
      if (!enabled) {
         UiRenderer.rect(g, UiBounds.of(x, y, w, h), 1711276032);
      }

      if (enabled) {
         this.addHotspot(x, y, w, h, action);
      }
   }

   private void chipColored(
      GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int accentColor, boolean selected, boolean enabled, int mx, int my, Runnable action
   ) {
      boolean clickable = enabled && action != null;
      boolean hover = clickable && !selected && mx >= x && mx < x + w && my >= y && my < y + h;
      if (!clickable && !selected) {
         Chip.renderDisabled(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label);
      } else {
         Chip.render(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, accentColor, selected, hover);
      }

      if (clickable) {
         this.addHotspot(x, y, w, h, action);
      }
   }

   private int toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean value, int mx, int my, Runnable onToggle) {
      int h = 18;
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
      UiRenderer.frame(g, UiBounds.of(x, y, w, h), hover ? tint(this.BORDER, 51) : 705827348, this.BORDER);
      Toggle.renderLabeled(UiContexts.overlay(g, this.font, mx, my), UiBounds.of(x, y, w, h), label, value, hover, "mm:" + label);
      this.addHotspot(x, y, w, h, onToggle);
      return y + h + 3;
   }

   private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center) {
      this.drawText(g, text, x, y, color, center, Integer.MAX_VALUE);
   }

   private int drawRoleName(GuiGraphicsExtractor g, String name, int x, int y, String fpHex, boolean self) {
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      String value = name == null ? "" : name;
      if (!MatchmakingManager.isGradientName(fpHex, self)) {
         int color = MatchmakingManager.nameColor(fpHex, self);
         UiText.draw(g, this.font, value, fontId, color, x, y, false);
         return x + UiText.width(this.font, value, fontId, color);
      } else {
         int cx = x;
         int len = value.length();

         for (int i = 0; i < len; i++) {
            String ch = String.valueOf(value.charAt(i));
            int color = MatchmakingManager.gradientNameColor(fpHex, self, i, len);
            UiText.draw(g, this.font, ch, fontId, color, cx, y, false);
            cx += UiText.width(this.font, ch, fontId, color);
         }

         return cx;
      }
   }

   private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center, int maxWidth) {
      Identifier fontId = this.theme.fontFor(UiTone.BODY);
      String value = text == null ? "" : text;
      if (maxWidth != Integer.MAX_VALUE && !center) {
         UiText.drawEllipsized(g, this.font, value, fontId, color, x, y, Math.max(1, maxWidth), false);
      } else {
         if (maxWidth != Integer.MAX_VALUE) {
            value = UiText.trimToWidthEllipsis(this.font, value, maxWidth, fontId, color);
         }

         int wpx = UiText.width(this.font, value, fontId, color);
         UiText.draw(g, this.font, value, fontId, color, center ? x - wpx / 2 : x, y, false);
      }
   }

   private ItemStack itemIcon(MmMessages.BlobOffer b) {
      ItemStack cached = this.itemIconCache.get(b.data);
      if (cached != null) {
         return cached;
      } else {
         ItemStack st = MmBlobs.decodeItem(b);
         if (st == null) {
            st = ItemStack.EMPTY;
         }

         while (this.itemIconCache.size() >= 64) {
            Iterator<String> it = this.itemIconCache.keySet().iterator();
            if (!it.hasNext()) {
               break;
            }

            it.next();
            it.remove();
         }

         this.itemIconCache.put(b.data, st);
         return st;
      }
   }

   private static int tint(int color, int alpha) {
      return alpha << 24 | color & 16777215;
   }

   private static String maxLabel(int max) {
      return max <= 0 ? "∞" : Integer.toString(max);
   }

   private void addHotspot(int x, int y, int w, int h, Runnable action) {
      this.hotspots.add(new MatchmakingPanel.Hotspot(x, y, w, h, this.hitGateTop, this.hitGateBottom, action));
   }

   private static final class CtxItem {
      final int x;
      final int y;
      final int w;
      final int h;
      final String label;
      final int color;
      final Runnable action;
      final boolean closes;

      CtxItem(int x, int y, int w, int h, String label, int color, Runnable action, boolean closes) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.label = label;
         this.color = color;
         this.action = action;
         this.closes = closes;
      }

      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   public interface Host {
      Screen returnScreen();
   }

   private static final class Hotspot {
      final int x;
      final int y;
      final int w;
      final int h;
      final int gateTop;
      final int gateBottom;
      final Runnable action;

      Hotspot(int x, int y, int w, int h, int gateTop, int gateBottom, Runnable action) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.gateTop = gateTop;
         this.gateBottom = gateBottom;
         this.action = action;
      }

      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h && my >= this.gateTop && my < this.gateBottom;
      }
   }

   private static enum LobbyPane {
      BROWSE,
      CREATE,
      JOIN;
   }

   private record MemberRow(int x, int y, int w, int h, String fp, int gateTop, int gateBottom) {
      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h && my >= this.gateTop && my < this.gateBottom;
      }
   }

   private static enum Tab {
      LOBBIES("Lobbies"),
      CHAT("Chat"),
      MEMBERS("Members"),
      SETTINGS("Settings");

      final String label;

      private Tab(String l) {
         this.label = l;
      }
   }
}
