package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiProxyVerifier;

public final class RiptideMultiProxyPickerScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MARGIN = 14;
   private static final int LIST_TOP = 72;
   private static final int LIST_HEADER_HEIGHT = 24;
   private static final int ROW_HEIGHT = 34;
   private static final int ROW_FRAME_HEIGHT = 30;
   private static final int CHECK_WIDTH = 52;
   private static final int USED_YELLOW = -14249;
   private final Screen parent;
   private final String contextTitle;
   private final String serverHost;
   private final int serverPort;
   private final String selectedProxyId;
   private final Map<String, Integer> profileUsage;
   private final Consumer<String> onPick;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<RiptideMultiProxyPickerScreen.Row> visibleRows = new ArrayList<>();
   private EditBox searchField;
   private String search = "";
   private int scrollOffset;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private long cachedListRevision = Long.MIN_VALUE;
   private long cachedStatusRevision = Long.MIN_VALUE;
   private String cachedSearch = null;
   private List<RiptideMultiProxyPickerScreen.Choice> cachedChoices = List.of();
   private boolean refreshWasRunning;
   private long watchedRefreshGeneration = Long.MIN_VALUE;

   public RiptideMultiProxyPickerScreen(
      Screen parent, String contextTitle, String serverAddress, String selectedProxyId, Map<String, Integer> profileUsage, Consumer<String> onPick
   ) {
      super(Component.literal("Choose Proxy"));
      this.parent = parent;
      this.contextTitle = safeTrim(contextTitle).isBlank() ? "Choose a manual proxy" : safeTrim(contextTitle);
      this.selectedProxyId = selectedProxyId;
      this.profileUsage = profileUsage == null ? Map.of() : Map.copyOf(profileUsage);
      this.onPick = onPick;
      ServerAddress parsed = null;

      try {
         if (serverAddress != null && !serverAddress.isBlank()) {
            parsed = ServerAddress.parseString(serverAddress.trim());
         }
      } catch (RuntimeException var9) {
      }

      this.serverHost = parsed == null ? "" : parsed.getHost();
      this.serverPort = parsed == null ? 0 : parsed.getPort();
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      int searchX = this.rowX();
      this.searchField = new EditBox(this.font, searchX, 46, Math.max(40, this.rowRight() - searchX), 18, Component.literal("Search proxies"));
      this.searchField.setHint(Component.literal("Search name, address, type, or region..."));
      this.searchField.setMaxLength(160);
      this.searchField.setValue(this.search);
      this.searchField.setResponder(value -> {
         this.search = safeTrim(value);
         this.scrollOffset = 0;
         this.rebuild();
      });
      this.addRenderableWidget(this.searchField);
      RiptideProxyManager manager = RiptideProxyManager.get();
      manager.requestGeoLookup(false);
      RiptideProxyManager.RefreshStatus refresh = manager.refreshStatus();
      this.refreshWasRunning = refresh.running();
      this.watchedRefreshGeneration = refresh.generation();
      this.rebuild();
   }

   public void tick() {
      super.tick();
      RiptideProxyManager manager = RiptideProxyManager.get();
      long listRevision = manager.listRevision();
      RiptideProxyManager.RefreshStatus refresh = manager.refreshStatus();
      long statusRevision = refresh.revision();
      if (this.refreshWasRunning && !refresh.running() && refresh.generation() == this.watchedRefreshGeneration) {
         if (refresh.checked() >= refresh.total()) {
            manager.sortByLatencyNow();
            this.toast("Proxy refresh finished and sorted best to worst.", -13248397);
         }

         this.scrollOffset = 0;
         this.invalidateChoices();
      }

      this.refreshWasRunning = refresh.running();
      this.watchedRefreshGeneration = refresh.generation();
      if (listRevision != this.cachedListRevision || statusRevision != this.cachedStatusRevision) {
         this.rebuild();
      }
   }

   private void rebuild() {
      this.buttons.clear();
      this.visibleRows.clear();
      int right = this.screenWidth() - 14 - 10;
      CompactOverlayButton back = CompactOverlayButton.create(right - 60, 22, 60, 18, Component.literal("Back"), button -> this.onClose())
         .setVariant(CompactOverlayButton.Variant.SECONDARY);
      CompactOverlayButton manage = CompactOverlayButton.create(
            right - 60 - 6 - 104, 22, 104, 18, Component.literal("Manage Proxies"), button -> this.openProxyManager()
         )
         .setVariant(CompactOverlayButton.Variant.PRIMARY);
      RiptideProxyManager.RefreshStatus refreshStatus = RiptideProxyManager.get().refreshStatus();
      CompactOverlayButton refresh = CompactOverlayButton.create(
            right - 60 - 6 - 104 - 6 - 78, 22, 78, 18, Component.literal(refreshStatus.running() ? "Cancel" : "Refresh"), button -> this.refreshAll()
         )
         .setVariant(refreshStatus.running() ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS);
      this.buttons.add(back);
      this.buttons.add(manage);
      this.buttons.add(refresh);
      List<RiptideMultiProxyPickerScreen.Choice> choices = this.choices();
      int visibleCount = this.visibleRowCount();
      this.scrollOffset = clampScroll(this.scrollOffset, choices.size(), visibleCount);
      int y = this.rowsTop();
      int end = Math.min(choices.size(), this.scrollOffset + visibleCount);

      for (int i = this.scrollOffset; i < end; i++) {
         RiptideMultiProxyPickerScreen.Choice choice = choices.get(i);
         CompactOverlayButton check = null;
         if (choice.proxy() != null) {
            RiptideProxy proxy = choice.proxy();
            check = CompactOverlayButton.create(
                  this.rowRight() - 52 - 6,
                  y + 6,
                  52,
                  18,
                  Component.literal(proxy.status == RiptideProxy.Status.CHECKING ? "..." : "Check"),
                  button -> this.checkOne(proxy)
               )
               .setVariant(CompactOverlayButton.Variant.PRIMARY);
            check.active = !refreshStatus.running() && proxy.status != RiptideProxy.Status.CHECKING;
         }

         this.visibleRows.add(new RiptideMultiProxyPickerScreen.Row(choice, y, check));
         y += 34;
      }
   }

   private void refreshAll() {
      RiptideProxyManager manager = RiptideProxyManager.get();
      RiptideProxyManager.RefreshStatus current = manager.refreshStatus();
      if (current.running()) {
         if (manager.cancelRefresh()) {
            this.toast("Proxy refresh canceled.", -14249);
         }

         this.invalidateChoices();
         this.rebuild();
      } else if (this.serverHost.isBlank() || this.serverPort <= 0) {
         this.toast("Set a valid profile server before checking proxies.", -14249);
      } else if (!manager.startRefreshToServer(true, this.serverHost, this.serverPort)) {
         this.toast(manager.size() == 0 ? "No proxies to refresh." : "Proxy refresh could not start.", -14249);
      } else {
         RiptideProxyManager.RefreshStatus started = manager.refreshStatus();
         this.refreshWasRunning = true;
         this.watchedRefreshGeneration = started.generation();
         this.invalidateChoices();
         this.rebuild();
         this.toast("Checking proxies against " + this.serverHost + ":" + this.serverPort + "...", -13248397);
      }
   }

   private void checkOne(RiptideProxy proxy) {
      if (proxy != null && !RiptideProxyManager.get().refreshStatus().running()) {
         if (!this.serverHost.isBlank() && this.serverPort > 0) {
            proxy.status = RiptideProxy.Status.CHECKING;
            proxy.latency = 0L;
            this.invalidateChoices();
            this.rebuild();
            RiptideBackgroundTasks.runTracked(
               "Riptide-Multi-Proxy-Check",
               () -> {
                  int attempts = Math.max(1, RiptideProxyManager.get().getRetries() + 1);
                  MultiProxyVerifier.Result verified = new MultiProxyVerifier.Result(false, 0L);

                  for (int attempt = 0; attempt < attempts && !verified.ok(); attempt++) {
                     verified = MultiProxyVerifier.verify(proxy, this.serverHost, this.serverPort, RiptideProxyManager.get().getTimeoutMs());
                  }

                  RiptideProxy.CheckResult result = verified.ok()
                     ? new RiptideProxy.CheckResult(RiptideProxy.Status.ALIVE, verified.latencyMs(), 1)
                     : new RiptideProxy.CheckResult(RiptideProxy.Status.DEAD, 0L, 2);
                  MultiProxyVerifier.Result completed = verified;
                  if (this.minecraft != null) {
                     this.minecraft.execute(() -> {
                        if (RiptideProxyManager.get().applySingleCheck(proxy, result, completed.workingType())) {
                           this.toast(proxy.displayName() + " is " + statusText(proxy) + ".", statusColor(proxy));
                        }

                        this.invalidateChoices();
                        this.rebuild();
                     });
                  }
               }
            );
         } else {
            this.toast("Set a valid profile server before checking this proxy.", -14249);
         }
      }
   }

   private void invalidateChoices() {
      this.cachedListRevision = Long.MIN_VALUE;
      this.cachedStatusRevision = Long.MIN_VALUE;
      this.cachedSearch = null;
   }

   private void openProxyManager() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideProxiesScreen(this));
      }
   }

   private void pick(String proxyId) {
      if (this.onPick != null) {
         this.onPick.accept(proxyId == null ? "" : proxyId);
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private List<RiptideMultiProxyPickerScreen.Choice> choices() {
      RiptideProxyManager manager = RiptideProxyManager.get();
      long listRevision = manager.listRevision();
      long statusRevision = manager.refreshStatus().revision();
      String query = this.search.toLowerCase(Locale.ROOT);
      if (listRevision == this.cachedListRevision && statusRevision == this.cachedStatusRevision && query.equals(this.cachedSearch)) {
         return this.cachedChoices;
      } else {
         List<RiptideProxy> proxies = manager.all()
            .stream()
            .filter(proxyx -> proxyx != null && proxyx.isValid())
            .sorted(
               Comparator.comparingInt(RiptideMultiProxyPickerScreen::proxyRank)
                  .thenComparingLong(proxyx -> proxyx.status == RiptideProxy.Status.ALIVE && proxyx.latency > 0L ? proxyx.latency : Long.MAX_VALUE)
                  .thenComparing(RiptideProxy::displayName, String.CASE_INSENSITIVE_ORDER)
                  .thenComparing(proxyx -> safeTrim(proxyx.address), String.CASE_INSENSITIVE_ORDER)
                  .thenComparingInt(proxyx -> proxyx.port)
            )
            .toList();
         List<RiptideMultiProxyPickerScreen.Choice> choices = new ArrayList<>(proxies.size() + 2);
         addIfMatches(choices, new RiptideMultiProxyPickerScreen.Choice("", "Proxy Off", "Connect directly without a proxy", null), query);
         if (proxies.stream().anyMatch(proxyx -> proxyx.status != RiptideProxy.Status.DEAD)) {
            addIfMatches(
               choices, new RiptideMultiProxyPickerScreen.Choice("best", "Best Proxy", "Balance usable proxies across Best Proxy accounts", null), query
            );
         }

         for (RiptideProxy proxy : proxies) {
            String auth = proxy.username != null && !proxy.username.isBlank() ? "  Auth" : "";
            RiptideMultiProxyPickerScreen.Choice choice = new RiptideMultiProxyPickerScreen.Choice(
               proxy.stableId(), proxy.displayName(), proxy.type + "  " + proxy.address + ":" + proxy.port + auth, proxy
            );
            addIfMatches(choices, choice, query);
         }

         this.cachedListRevision = listRevision;
         this.cachedStatusRevision = statusRevision;
         this.cachedSearch = query;
         this.cachedChoices = List.copyOf(choices);
         return this.cachedChoices;
      }
   }

   private static void addIfMatches(List<RiptideMultiProxyPickerScreen.Choice> choices, RiptideMultiProxyPickerScreen.Choice choice, String query) {
      if (query.isBlank() || choice.searchText().contains(query)) {
         choices.add(choice);
      }
   }

   private static int proxyRank(RiptideProxy proxy) {
      if (proxy != null && proxy.status != null) {
         return switch (proxy.status) {
            case ALIVE -> 0;
            case UNCHECKED -> 1;
            case CHECKING -> 2;
            case DEAD -> 3;
         };
      } else {
         return 2;
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0) {
         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics();
         if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
               ? Math.max(0, (int)Math.round(virtualEvent.y()) - scrollbar.thumbY())
               : scrollbar.thumbHeight() / 2;
            this.setScrollFromPixels(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.scrollbarGrabOffset));
            return true;
         }

         for (CompactOverlayButton button : this.buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
               return true;
            }
         }

         for (RiptideMultiProxyPickerScreen.Row row : this.visibleRows) {
            CompactOverlayButton check = row.check();
            if (check != null
               && virtualEvent.x() >= check.getX()
               && virtualEvent.x() < check.getX() + check.getWidth()
               && virtualEvent.y() >= check.getY()
               && virtualEvent.y() < check.getY() + check.getHeight()) {
               CompactOverlayButton.fireIfHit(check, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
               return true;
            }
         }

         for (RiptideMultiProxyPickerScreen.Row rowx : this.visibleRows) {
            if (virtualEvent.x() >= this.rowX() && virtualEvent.x() < this.rowRight() && virtualEvent.y() >= rowx.y() && virtualEvent.y() < rowx.y() + 30) {
               this.pick(rowx.choice().id());
               return true;
            }
         }
      }

      return super.mouseClicked(virtualEvent, doubleClick);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.scrollbarDragging) {
         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics();
         this.setScrollFromPixels(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.scrollbarGrabOffset));
         return true;
      } else {
         return super.mouseDragged(virtualEvent, RiptideUiScale.toVirtual(dragX), RiptideUiScale.toVirtual(dragY));
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      int x = RiptideUiScale.toVirtualInt(mouseX);
      int y = RiptideUiScale.toVirtualInt(mouseY);
      if (x < this.rowX() || x >= this.rowRight() + 8 || y < this.rowsTop() || y >= this.rowsBottom()) {
         return super.mouseScrolled(x, y, horizontal, vertical);
      } else if (vertical == 0.0) {
         return true;
      } else {
         this.scrollOffset = clampScroll(this.scrollOffset + (vertical < 0.0 ? 2 : -2), this.choices().size(), this.visibleRowCount());
         this.rebuild();
         return true;
      }
   }

   private void setScrollFromPixels(int pixels) {
      this.scrollOffset = clampScroll(Math.round(pixels / 34.0F), this.choices().size(), this.visibleRowCount());
      this.rebuild();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), themeBg());
         UiRenderer.frame(graphics, UiBounds.of(14, 14, this.panelWidth(), this.screenHeight() - 28), themePanel(), themeBorder());
         UiRenderer.frame(
            graphics,
            UiBounds.of(this.rowX() - 4, 72, this.rowRight() - this.rowX() + 12, Math.max(24, this.screenHeight() - 72 - 20)),
            RiptideTheme.recolor(-1206643689, RiptideTheme.Channel.BUTTON),
            themeBorder()
         );
         int firstButtonX = this.buttons.stream().mapToInt(CompactOverlayButton::getX).min().orElse(this.screenWidth() - 14 - 10);
         int titleMaxWidth = Math.max(1, firstButtonX - 24 - 6);
         this.drawFitted(graphics, this.contextTitle, 24, 25, titleMaxWidth, themeText());
         this.renderListHeader(graphics);

         for (RiptideMultiProxyPickerScreen.Row row : this.visibleRows) {
            this.renderRow(graphics, row, virtualMouseX, virtualMouseY);
         }

         for (CompactOverlayButton button : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
         }

         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics();
         if (scrollbar.hasScroll()) {
            CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), this.scrollbarDragging);
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderListHeader(GuiGraphicsExtractor graphics) {
      List<RiptideMultiProxyPickerScreen.Choice> choices = this.choices();
      RiptideProxyManager.RefreshStatus refresh = RiptideProxyManager.get().refreshStatus();
      int totalUsable = (int)RiptideProxyManager.get()
         .all()
         .stream()
         .filter(proxy -> proxy != null && proxy.isValid() && proxy.status != RiptideProxy.Status.DEAD)
         .count();
      RiptideProxy selectedProxy = this.selectedProxyId != null && !this.selectedProxyId.isBlank() && !"best".equals(this.selectedProxyId)
         ? RiptideProxyManager.get().findById(this.selectedProxyId)
         : null;
      boolean selectedVisible = this.selectedProxyId == null
         || this.selectedProxyId.isBlank()
         || "best".equals(this.selectedProxyId) && totalUsable > 0
         || selectedProxy != null && selectedProxy.isValid() && selectedProxy.status != RiptideProxy.Status.DEAD;
      if (refresh.running()) {
         String progress = "Refreshing " + refresh.checked() + "/" + refresh.total() + " against " + this.serverHost + ":" + this.serverPort;
         this.drawFitted(graphics, progress, this.rowX(), 79, Math.max(20, this.rowRight() - this.rowX()), themeText());
      } else if (!selectedVisible) {
         this.drawFitted(graphics, "Current proxy is missing; choose a replacement", this.rowX(), 80, Math.max(20, this.rowRight() - this.rowX()), themeWarn());
      } else if (selectedProxy != null && selectedProxy.status == RiptideProxy.Status.DEAD) {
         this.drawFitted(
            graphics,
            "Selected proxy failed its last check; manual selection is still preserved",
            this.rowX(),
            80,
            Math.max(20, this.rowRight() - this.rowX()),
            themeWarn()
         );
      } else {
         String summary = choices.size() + " choices  " + totalUsable + " usable proxies";
         this.drawFitted(graphics, summary, this.rowX(), 80, Math.max(20, this.rowRight() - this.rowX()), themeText());
      }

      if (choices.isEmpty()) {
         this.drawText(graphics, "No proxy choices match this search.", this.rowX() + 4, this.rowsTop() + 8, themeMuted());
      }

      if (refresh.running()) {
         int trackX = this.rowX();
         int trackY = 92;
         int trackW = Math.max(1, this.rowRight() - this.rowX());
         UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, trackW, 2), themeBorder());
         int total = Math.max(1, refresh.total());
         int fillW = refresh.checked() <= 0 ? 0 : Math.max(1, Math.min(trackW, Math.round((float)(trackW * refresh.checked()) / total)));
         if (fillW > 0) {
            UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, fillW, 2), themeSuccess());
         }
      }
   }

   private void renderRow(GuiGraphicsExtractor graphics, RiptideMultiProxyPickerScreen.Row row, int mouseX, int mouseY) {
      RiptideMultiProxyPickerScreen.Choice choice = row.choice();
      boolean selected = Objects.equals(this.selectedProxyId, choice.id());
      int usedCount = this.profileUsage.getOrDefault(choice.id(), 0);
      boolean used = usedCount > 0;
      boolean hovered = mouseX >= this.rowX() && mouseX < this.rowRight() && mouseY >= row.y() && mouseY < row.y() + 30;
      int fill = used
         ? 858989064
         : (
            selected
               ? RiptideTheme.recolor(858052714, RiptideTheme.Channel.SUCCESS)
               : (hovered ? RiptideTheme.recolor(707467805, RiptideTheme.Channel.ACCENT) : RiptideTheme.recolor(403771667, RiptideTheme.Channel.BUTTON))
         );
      UiRenderer.rect(graphics, UiBounds.of(this.rowX(), row.y(), this.rowRight() - this.rowX(), 30), fill);
      if (selected) {
         UiRenderer.rect(graphics, UiBounds.of(this.rowX(), row.y(), 2, 30), themeSuccess());
      }

      if (used) {
         UiRenderer.rect(graphics, UiBounds.of(this.rowX(), row.y(), 2, 30), -14249);
      }

      RiptideProxy proxy = choice.proxy();
      int checkX = row.check() == null ? this.rowRight() - 6 : row.check().getX();
      int metaWidth = proxy == null ? 84 : Math.min(86, Math.max(48, (this.rowRight() - this.rowX()) / 4));
      int metaX = checkX - metaWidth - 7;
      int textWidth = Math.max(24, metaX - this.rowX() - 18);
      this.drawFitted(graphics, choice.label(), this.rowX() + 9, row.y() + 6, textWidth, used ? -14249 : (selected ? themeSuccess() : themeText()));
      this.drawFitted(graphics, choice.description(), this.rowX() + 9, row.y() + 18, textWidth, themeMuted());
      if (proxy == null) {
         String status = choice.id().isBlank() ? "Direct" : this.usableProxyCount() + " available";
         this.drawRightFitted(graphics, status, metaX, row.y() + 12, metaWidth, choice.id().isBlank() ? themeMuted() : themeSuccess());
      } else {
         this.drawRightFitted(
            graphics,
            used ? "Used" + (usedCount > 1 ? " x" + usedCount : "") : proxy.geoLabel(),
            metaX,
            row.y() + 6,
            metaWidth,
            used ? -14249 : proxy.geoColor()
         );
         this.drawRightFitted(graphics, statusText(proxy), metaX, row.y() + 18, metaWidth, statusColor(proxy));
         if (row.check() != null) {
            CompactOverlayButton.renderStyled(graphics, this.font, row.check(), mouseX, mouseY);
         }
      }
   }

   private int usableProxyCount() {
      int count = 0;

      for (RiptideProxy proxy : RiptideProxyManager.get().all()) {
         if (proxy != null && proxy.isValid() && proxy.status != RiptideProxy.Status.DEAD) {
            count++;
         }
      }

      return count;
   }

   private static String statusText(RiptideProxy proxy) {
      if (proxy != null && proxy.status != null) {
         return switch (proxy.status) {
            case ALIVE -> proxy.latency > 0L ? proxy.latency + "ms" : "Alive";
            case UNCHECKED -> "Unchecked";
            case CHECKING -> "Checking";
            case DEAD -> "Dead";
         };
      } else {
         return "Unchecked";
      }
   }

   private static int statusColor(RiptideProxy proxy) {
      if (proxy != null && proxy.status != null) {
         return switch (proxy.status) {
            case ALIVE -> -13248397;
            case UNCHECKED, CHECKING -> -6645094;
            case DEAD -> -42149;
         };
      } else {
         return -6645094;
      }
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      int viewport = Math.max(1, this.rowsBottom() - this.rowsTop());
      return CompactScrollbar.compute(this.choices().size() * 34, viewport, this.rowRight() + 5, this.rowsTop(), 4, viewport, this.scrollOffset * 34);
   }

   private int visibleRowCount() {
      return Math.max(1, Math.max(1, this.rowsBottom() - this.rowsTop()) / 34);
   }

   private static int clampScroll(int offset, int total, int visible) {
      return Math.max(0, Math.min(offset, Math.max(0, total - visible)));
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private int panelWidth() {
      return Math.max(1, this.screenWidth() - 28);
   }

   private int rowX() {
      return 24;
   }

   private int rowRight() {
      return Math.max(this.rowX() + 40, this.screenWidth() - 14 - 14);
   }

   private int rowsTop() {
      return 96;
   }

   private int rowsBottom() {
      return Math.max(this.rowsTop() + 34, this.screenHeight() - 22);
   }

   private void drawText(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
      UiText.draw(graphics, this.font, value == null ? "" : value, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private void drawFitted(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      String fitted = UiText.trimToWidthEllipsis(this.font, value == null ? "" : value, Math.max(1, maxWidth), fontId, color);
      UiText.draw(graphics, this.font, fitted, fontId, color, x, y, false);
   }

   private void drawRightFitted(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      String fitted = UiText.trimToWidthEllipsis(this.font, value == null ? "" : value, Math.max(1, maxWidth), fontId, color);
      UiText.draw(graphics, this.font, fitted, fontId, color, x + maxWidth - this.font.width(fitted), y, false);
   }

   private static int themeBg() {
      return RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP);
   }

   private static int themePanel() {
      return RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON);
   }

   private static int themeBorder() {
      return RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
   }

   private static int themeText() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private static int themeMuted() {
      return -6645094;
   }

   private static int themeSuccess() {
      return RiptideTheme.recolor(-12588930, RiptideTheme.Channel.SUCCESS);
   }

   private static int themeWarn() {
      return -14249;
   }

   private record Choice(String id, String label, String description, RiptideProxy proxy) {
      private String searchText() {
         String proxyText = this.proxy == null ? "" : this.proxy.geoSearchText() + " " + this.proxy.status;
         return (RiptideScreen.safeTrim(this.label) + " " + RiptideScreen.safeTrim(this.description) + " " + proxyText).toLowerCase(Locale.ROOT);
      }
   }

   private record Row(RiptideMultiProxyPickerScreen.Choice choice, int y, CompactOverlayButton check) {
   }
}
