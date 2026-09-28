package riptide.util;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.world.inventory.Slot;
import riptide.commands.RiptideCommands;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectAccordionSection;
import riptide.gui.vanillaui.direct.DirectCompactRow;
import riptide.gui.vanillaui.direct.DirectIconLabel;
import riptide.gui.vanillaui.direct.DirectMetricLabel;
import riptide.gui.vanillaui.direct.DirectPanel;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectSpacer;
import riptide.gui.vanillaui.direct.DirectSurface;
import riptide.gui.vanillaui.direct.DirectUiButton;
import riptide.gui.vanillaui.direct.DirectUiColumn;
import riptide.gui.vanillaui.direct.DirectUiInsets;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.mixin.accessor.RiptideHandledScreenAccessor;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.multi.MultiManager;

public class RiptideLauncherOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int DEFAULT_X = 0;
   private static final int DEFAULT_Y = 0;
   private static final String SHARED_MAIN_MENU_LAYOUT = "MAIN_MENU";
   private static final String LEGACY_MODULE_UTILITIES_LAYOUT = "UTILITIES";
   private static final int PANEL_PAD = 0;
   private static final int SECTION_PAD = 4;
   private static final int SECTION_GAP = 3;
   private static final int ROW_GAP = 2;
   private static final int BUTTON_HEIGHT = 20;
   private static final int CHAT_HEIGHT = 20;
   private static final int CHAT_MIN_WIDTH = 136;
   private static final int TEXT_PAD = 8;
   private static final int ICON_SIZE = 14;
   private static final int ICON_GAP = 4;
   private static final int LABEL_ICON_SIZE = 13;
   private static final int MIN_PAIR_WIDTH = 58;
   private static final int MIN_PANEL_WIDTH = 164;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private final CompactTheme theme = new CompactTheme();
   private final DirectPanel panelNode = new DirectPanel();
   private final DirectSurface surface = new DirectSurface(this.theme, this.panelNode);
   private final RiptideMacroListOverlay macroListOverlay;
   private final RiptideFabricatorOverlay fabricatorOverlay;
   private final RiptideLANSyncOverlay lanSyncOverlay;
   private final RiptideQueueEditorOverlay queueEditorOverlay;
   private final RiptidePacketLoggerOverlay packetLoggerOverlay;
   private final RiptideCustomFilterOverlay customFilterOverlay;
   private Supplier<RiptidePacketLoggerOverlay> packetLoggerOverlaySupplier;
   private Supplier<RiptideServerInfoOverlay> serverInfoOverlaySupplier;
   private final String overlayId;
   private final boolean includeFabricatorButton;
   private final boolean includeScreenSection;
   private final boolean includeChatSection;
   private Runnable closeWithoutPacketAction;
   private Runnable desyncAction;
   private RiptideKeybindOverlay keybindOverlay;
   private RiptideServerInfoOverlay serverInfoOverlay;
   private DirectUiButton sendButton;
   private DirectUiButton delayButton;
   private DirectUiButton multiButton;
   private CompactTextInput chatField;
   private static final String CHAT_PLACEHOLDER = "Type message or /command...";
   private DirectMetricLabel revMetric;
   private DirectMetricLabel syncMetric;
   private DirectMetricLabel slotMetric;
   private DirectAccordionSection mainMenuSection;
   private int panelX = 0;
   private int panelY = 0;
   private int panelWidth = 164;
   private int panelHeight = 220;
   private boolean visible = true;
   private boolean dragging = false;
   private float dragOffsetX;
   private float dragOffsetY;
   private float pressStartUiX;
   private float pressStartUiY;
   private int pressStartPanelX;
   private int pressStartPanelY;
   private boolean dragMoved = false;
   private Font cachedWidthFont;
   private int cachedWidthReloadGen = Integer.MIN_VALUE;
   private boolean cachedWidthFabricator;
   private boolean cachedWidthScreen;
   private boolean cachedWidthChat;
   private int cachedPanelWidthValue = Integer.MIN_VALUE;

   public RiptideLauncherOverlay(
      RiptideMacroListOverlay var1,
      RiptideFabricatorOverlay var2,
      RiptideLANSyncOverlay var3,
      RiptideQueueEditorOverlay var4,
      RiptidePacketLoggerOverlay var5,
      RiptideCustomFilterOverlay var6
   ) {
      this(var1, var2, var3, var4, var5, var6, "riptide-launcher", true, true);
   }

   public RiptideLauncherOverlay(
      RiptideMacroListOverlay var1,
      RiptideFabricatorOverlay var2,
      RiptideLANSyncOverlay var3,
      RiptideQueueEditorOverlay var4,
      RiptidePacketLoggerOverlay var5,
      RiptideCustomFilterOverlay var6,
      String var7,
      boolean var8,
      boolean var9
   ) {
      this(var1, var2, var3, var4, var5, var6, var7, true, var8, var9);
   }

   public RiptideLauncherOverlay(
      RiptideMacroListOverlay var1,
      RiptideFabricatorOverlay var2,
      RiptideLANSyncOverlay var3,
      RiptideQueueEditorOverlay var4,
      RiptidePacketLoggerOverlay var5,
      RiptideCustomFilterOverlay var6,
      String var7,
      boolean var8,
      boolean var9,
      boolean var10
   ) {
      this.macroListOverlay = var1;
      this.fabricatorOverlay = var2;
      this.lanSyncOverlay = var3;
      this.queueEditorOverlay = var4;
      this.packetLoggerOverlay = var5;
      this.customFilterOverlay = var6;
      this.packetLoggerOverlaySupplier = () -> this.packetLoggerOverlay;
      this.overlayId = var7 != null && !var7.isBlank() ? var7 : "riptide-launcher";
      this.includeFabricatorButton = var8;
      this.includeScreenSection = var9;
      this.includeChatSection = var10;
      this.buildUi();
      this.restoreLayout();
      if (this.mainMenuSection != null) {
         this.mainMenuSection.syncExpanded(this.mainMenuSection.isExpanded());
      }
   }

   public void setCloseWithoutPacketAction(Runnable var1) {
      this.closeWithoutPacketAction = var1;
      this.buildUi();
   }

   public void setDesyncAction(Runnable var1) {
      this.desyncAction = var1;
      this.buildUi();
   }

   public void setKeybindOverlay(RiptideKeybindOverlay var1) {
      this.keybindOverlay = var1;
   }

   public void setServerDataOverlay(RiptideServerInfoOverlay var1) {
      this.serverInfoOverlay = var1;
      this.serverInfoOverlaySupplier = () -> this.serverInfoOverlay;
   }

   public void setPacketLoggerOverlaySupplier(Supplier<RiptidePacketLoggerOverlay> var1) {
      this.packetLoggerOverlaySupplier = var1 == null ? () -> this.packetLoggerOverlay : var1;
   }

   public void setServerDataOverlaySupplier(Supplier<RiptideServerInfoOverlay> var1) {
      this.serverInfoOverlaySupplier = var1 == null ? () -> this.serverInfoOverlay : var1;
   }

   private RiptidePacketLoggerOverlay packetLoggerOverlay() {
      RiptidePacketLoggerOverlay var1 = this.packetLoggerOverlaySupplier == null ? this.packetLoggerOverlay : this.packetLoggerOverlaySupplier.get();
      if (var1 != null) {
         RiptideOverlayManager.get().register(var1);
      }

      return var1;
   }

   private RiptideServerInfoOverlay serverInfoOverlay() {
      RiptideServerInfoOverlay var1 = this.serverInfoOverlaySupplier == null ? this.serverInfoOverlay : this.serverInfoOverlaySupplier.get();
      if (var1 != null) {
         RiptideOverlayManager.get().register(var1);
      }

      return var1;
   }

   private void buildUi() {
      this.panelNode.setPadding(DirectUiInsets.all(0)).setDrawBorder(false).setDrawFill(false);
      this.panelNode.content().clearChildren();
      this.panelNode.content().setPadding(DirectUiInsets.NONE).setGap(0);
      this.mainMenuSection = new DirectAccordionSection("Main Menu")
         .setHeaderHeight(this.sectionHeaderHeight())
         .setContentTopGap(this.sectionContentTopGap())
         .setExpanded(true);
      this.mainMenuSection.content().setPadding(new DirectUiInsets(1, 0, 1, 1)).setGap(this.rowGap());
      this.panelNode.content().add(this.mainMenuSection);
      DirectUiButton var1 = this.actionButton("Macros", UiAssets.ICON_MACROS, DirectUiButton.Variant.SECONDARY, () -> {
         RiptideMacroEditorOverlay var1x = RiptideMacroEditorOverlay.getSharedOverlay();
         if (var1x != null && var1x.isVisible()) {
            if (this.macroListOverlay != null) {
               this.macroListOverlay.setVisible(false);
            }

            RiptideOverlayManager.get().bringToFront(var1x);
         } else if (this.macroListOverlay != null) {
            this.toggleRegisteredUtilityOverlay(this.macroListOverlay);
         }
      }).setGrowX(true);
      if (this.includeFabricatorButton) {
         this.addPairRow(
            this.mainMenuSection.content(), var1, this.actionButton("Fabricator", UiAssets.ICON_FABRICATOR, DirectUiButton.Variant.SECONDARY, () -> {
               if (this.fabricatorOverlay != null) {
                  this.toggleRegisteredUtilityOverlay(this.fabricatorOverlay);
               }
            }).setGrowX(true)
         );
      } else {
         this.mainMenuSection.content().add(var1);
      }

      this.addPairRow(this.mainMenuSection.content(), this.actionButton("LAN Sync", UiAssets.ICON_LANSYNC, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.lanSyncOverlay != null) {
            this.toggleRegisteredUtilityOverlay(this.lanSyncOverlay);
         }
      }).setGrowX(true), this.actionButton("Queue", UiAssets.ICON_PACKET_Q_EDITOR, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.queueEditorOverlay != null) {
            this.toggleRegisteredUtilityOverlay(this.queueEditorOverlay);
         }
      }).setGrowX(true));
      this.addPairRow(this.mainMenuSection.content(), this.actionButton("Logger", UiAssets.ICON_PACKET_LOGGER, DirectUiButton.Variant.SECONDARY, () -> {
         RiptidePacketLoggerOverlay var1x = this.packetLoggerOverlay();
         this.toggleRegisteredUtilityOverlay(var1x);
      }).setGrowX(true), this.actionButton("Packets", UiAssets.ICON_FILTER, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.customFilterOverlay != null) {
            this.toggleRegisteredUtilityOverlay(this.customFilterOverlay);
         }
      }).setGrowX(true));
      this.addPairRow(this.mainMenuSection.content(), this.actionButton("Server Info", UiAssets.ICON_SERVER_INFO, DirectUiButton.Variant.SECONDARY, () -> {
         RiptideServerInfoOverlay var1x = this.serverInfoOverlay();
         this.toggleRegisteredUtilityOverlay(var1x);
      }).setGrowX(true), this.actionButton("Settings", UiAssets.ICON_KEYBINDS, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.keybindOverlay != null) {
            this.toggleRegisteredUtilityOverlay(this.keybindOverlay);
         }
      }).setGrowX(true));
      if (!RiptideLiteVariant.enabled()) {
         this.multiButton = this.actionButton("Multi", UiAssets.ICON_MULTI, DirectUiButton.Variant.SECONDARY, () -> {
            RiptideModule var0 = RiptideModule.get();
            if (var0 != null) {
               var0.toggleMultiUiBehavior();
            }
         }).setGrowX(true);
         this.mainMenuSection.content().add(this.multiButton);
      }

      this.addSubCategory("PACKET", UiAssets.ICON_PACKET_CATEGORY);
      this.sendButton = this.actionButton("Send", null, DirectUiButton.Variant.DANGER, this::toggleSendPackets).setConnectedToggle(true).setGrowX(true);
      this.delayButton = this.actionButton("Delay", null, DirectUiButton.Variant.DANGER, this::toggleDelayPackets).setConnectedToggle(true).setGrowX(true);
      this.addPairRow(this.mainMenuSection.content(), this.sendButton, this.delayButton);
      this.addPairRow(
         this.mainMenuSection.content(),
         this.actionButton("Flush", null, DirectUiButton.Variant.SECONDARY, this::flushQueue).setGrowX(true),
         this.actionButton("Clear", null, DirectUiButton.Variant.SECONDARY, this::clearQueue).setGrowX(true)
      );
      if (this.includeScreenSection) {
         this.addSubCategory("SCREEN", UiAssets.ICON_SCREEN_CATEGORY);
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton(
                  "Close",
                  null,
                  DirectUiButton.Variant.SECONDARY,
                  this.closeWithoutPacketAction != null ? this.closeWithoutPacketAction : () -> RiptideGuiActions.closeCurrentScreen(MC, false)
               )
               .setGrowX(true),
            this.actionButton("De-sync", null, DirectUiButton.Variant.DANGER, this.desyncAction != null ? this.desyncAction : this::sendDesync).setGrowX(true)
         );
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton("Save", null, DirectUiButton.Variant.SECONDARY, this::saveGui).setGrowX(true),
            this.actionButton("Load", null, DirectUiButton.Variant.SECONDARY, this::loadGui).setGrowX(true)
         );
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton("Title", null, DirectUiButton.Variant.SECONDARY, RiptideGuiClipboardUtil::copyGuiTitleJson).setGrowX(true),
            this.actionButton("Disc+Send", null, DirectUiButton.Variant.DANGER, this::disconnectAndSend).setGrowX(true)
         );
      }

      if (this.includeChatSection) {
         this.chatField = new CompactTextInput()
            .setGrowX(true)
            .setFieldHeight(this.chatHeight())
            .setPreferredWidth(this.chatMinWidth())
            .setMinWidth(this.chatMinWidth())
            .setHorizontalPadding(6)
            .setPlaceholder("Type message or /command...")
            .setHistoryNavigationEnabled(true)
            .setHistoryProvider(() -> (Collection<String>)(MC.gui != null ? MC.gui.hud.getChat().getRecentChat() : List.of()))
            .setOnSubmit(this::submitChat);
         this.mainMenuSection.content().add(this.chatField);
         this.mainMenuSection.content().add(new DirectSpacer(0.0F, 2.0F));
         DirectCompactRow var2 = new DirectCompactRow().setGap(this.syncRowGap()).setPadding(new DirectUiInsets(4, 0, 2, 0)).setUnderlineOnHover(false);
         var2.setGrowX(true);
         this.revMetric = var2.add(
            new DirectMetricLabel("Rev: ", "--")
               .setKeyColor(RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT))
               .setValueColor(RiptideTheme.recolor(-46518, RiptideTheme.Channel.ACCENT))
               .setGrowX(true)
         );
         this.syncMetric = var2.add(
            new DirectMetricLabel("SyncID: ", "--")
               .setKeyColor(RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT))
               .setValueColor(RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT))
               .setGrowX(true)
         );
         this.slotMetric = var2.add(
            new DirectMetricLabel("Slot: ", "--")
               .setKeyColor(RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT))
               .setValueColor(RiptideTheme.recolor(-7350273, RiptideTheme.Channel.ACCENT))
               .setGrowX(true)
         );
         this.mainMenuSection.content().add(var2);
      } else {
         this.chatField = null;
         this.revMetric = null;
         this.syncMetric = null;
         this.slotMetric = null;
      }
   }

   private void toggleRegisteredUtilityOverlay(IRiptideOverlay var1) {
      if (var1 != null) {
         RiptideOverlayManager var2 = RiptideOverlayManager.get();
         boolean var3 = var2.getOverlays().contains(var1);
         var2.register(var1);
         var2.setTemporarilyHidden(var1, false);
         if (!var3 && var1.isVisible()) {
            var2.bringToFront(var1);
         } else {
            var1.setVisible(!var1.isVisible());
            if (var1.isVisible()) {
               var2.bringToFront(var1);
            }
         }
      }
   }

   private void addSubCategory(String var1, Identifier var2) {
      if (!this.mainMenuSection.content().children().isEmpty()) {
         this.mainMenuSection.content().add(new DirectSpacer(0.0F, this.sectionGap()));
      }

      this.mainMenuSection.content().add(new DirectIconLabel(var1, var2).setIconSize(this.labelIconSize()).setConnectedStyle(true));
   }

   private DirectUiButton addFullButton(DirectUiColumn var1, String var2, Identifier var3, Runnable var4) {
      DirectUiButton var5 = this.actionButton(var2, var3, DirectUiButton.Variant.SECONDARY, var4).setGrowX(true);
      var1.add(var5);
      return var5;
   }

   private void addPairRow(DirectUiColumn var1, DirectUiButton var2, DirectUiButton var3) {
      DirectCompactRow var4 = new DirectCompactRow().setGap(this.rowGap()).setPadding(DirectUiInsets.NONE).setUnderlineOnHover(false);
      var4.add(var2.setConnectedEdges(ConnectedButton.LEFT_CELL));
      var4.add(var3.setConnectedEdges(ConnectedButton.RIGHT_CELL));
      var4.setGrowX(true);
      var1.add(var4);
   }

   private DirectUiButton actionButton(String var1, Identifier var2, DirectUiButton.Variant var3, Runnable var4) {
      DirectUiButton var5 = new DirectUiButton(var1, var3, var4)
         .setGrowX(false)
         .setConnectedStyle(true)
         .setButtonHeight(this.buttonHeight())
         .setHorizontalPadding(this.buttonPadding())
         .setMinWidth(this.requiredButtonWidth(var1, var2))
         .setTextYOffset(0);
      if (var2 != null) {
         var5.setLeadingIcon(var2)
            .setContentAlignment(DirectUiButton.ContentAlignment.START)
            .setIconSize(this.buttonIconSize())
            .setIconGap(this.buttonIconGap());
      }

      return var5;
   }

   @Override
   public String getOverlayId() {
      return this.overlayId;
   }

   @Override
   public int getMinWidth() {
      return this.computePanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.minimumPanelHeight();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, !this.mainMenuSection.isExpanded());
   }

   @Override
   public void setBounds(RiptideWindowLayout var1) {
      if (var1 != null) {
         RiptideWindowLayout var2 = this.clampToViewport(var1);
         this.panelX = var2.x;
         this.panelY = var2.y;
         this.panelWidth = var2.width;
         this.panelHeight = var2.height;
         this.visible = var2.visible;
         if (this.mainMenuSection != null) {
            this.mainMenuSection.syncExpanded(!var2.collapsed);
         }
      }
   }

   @Override
   public void saveLayout() {
      RiptideSharedState.get().setWindowLayout(this.getOverlayId(), this.getBounds());
      RiptideConfig var1 = RiptideConfig.getGlobal();
      RiptideConfig.ModuleCategoryLayout var2 = var1.moduleCategoryLayouts.computeIfAbsent("MAIN_MENU", var0 -> new RiptideConfig.ModuleCategoryLayout());
      RiptideWindowLayout var3 = RiptideOverlayManager.get().clampedAwayTrueGeometry(this.getOverlayId());
      int var4 = var3 != null ? var3.x : this.panelX;
      int var5 = var3 != null ? var3.y : this.panelY;
      if (var2.x != var4 || var2.y != var5 || var2.collapsed != this.isCollapsed()) {
         var2.x = var4;
         var2.y = var5;
         var2.collapsed = this.isCollapsed();
         var1.save();
      }
   }

   @Override
   public void restoreLayout() {
      RiptideWindowLayout var1 = RiptideSharedState.get().getWindowLayout(this.getOverlayId());
      boolean var2 = var1 == null || var1.visible;
      boolean var3 = var1 != null && var1.collapsed;
      RiptideConfig var4 = RiptideConfig.getGlobal();
      RiptideConfig.ModuleCategoryLayout var5 = var4.moduleCategoryLayouts.get("MAIN_MENU");
      if (var5 == null) {
         RiptideConfig.ModuleCategoryLayout var6 = var4.moduleCategoryLayouts.get("UTILITIES");
         if (var6 != null) {
            var5 = new RiptideConfig.ModuleCategoryLayout();
            var5.x = var6.x;
            var5.y = var6.y;
            var5.collapsed = var6.collapsed;
            var4.moduleCategoryLayouts.put("MAIN_MENU", var5);
         }
      }

      if (var5 != null && var5.x >= 0 && var5.y >= 0) {
         this.setBounds(new RiptideWindowLayout(var5.x, var5.y, this.panelWidth, this.panelHeight, var2, var5.collapsed || var3));
      } else if (var1 != null) {
         this.setBounds(var1);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      if (this.visible) {
         RiptideSharedState var5 = RiptideSharedState.get();
         if (this.sendButton != null) {
            boolean var6 = var5.shouldSendGuiPackets();
            this.sendButton.setText("Send");
            this.sendButton.setVariant(var6 ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
         }

         if (this.delayButton != null) {
            boolean var11 = var5.shouldDelayGuiPackets();
            this.delayButton.setText("Delay");
            this.delayButton.setVariant(var11 ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
         }

         if (this.multiButton != null) {
            MultiManager var12 = MultiManager.get();
            this.multiButton.setText(var12.isActive() ? "Multi " + var12.readyFraction() : "Multi");
            this.multiButton.setVariant(var12.isActive() ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.SECONDARY);
         }

         this.updateSyncMetrics();
         this.updateChatGhost();
         this.panelWidth = this.computePanelWidth();
         DirectViewport var13 = this.surface.viewport();
         float var7 = var13.toUiX(var2);
         float var8 = var13.toUiY(var3);
         DirectRenderContext var9 = new DirectRenderContext(var1, MC.font, var13, this.theme, var7, var8, var4);
         this.panelHeight = Math.max(this.getMinHeight(), Math.round(this.panelNode.preferredHeight(var9, this.panelWidth)));
         RiptideWindowLayout var10 = this.clampToViewport(
            new RiptideWindowLayout(
               this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.mainMenuSection != null && !this.mainMenuSection.isExpanded()
            )
         );
         this.panelX = var10.x;
         this.panelY = var10.y;
         this.panelWidth = var10.width;
         this.panelHeight = var10.height;
         this.panelNode.setActive(true);
         this.panelNode.setBounds(this.panelX, this.panelY, this.panelWidth, this.panelHeight);
         this.surface.render(var1, var2, var3, var4);
      }
   }

   @Override
   public boolean mouseClicked(double var1, double var3, int var5) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport var6 = this.surface.viewport();
         float var7 = var6.toUiX(var1);
         float var8 = var6.toUiY(var3);
         if (var5 == 0 && this.isOverMainMenuHeader(var7, var8) && this.mainMenuSection != null) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragOffsetX = var7 - this.panelX;
            this.dragOffsetY = var8 - this.panelY;
            this.pressStartUiX = var7;
            this.pressStartUiY = var8;
            this.pressStartPanelX = this.panelX;
            this.pressStartPanelY = this.panelY;
            return true;
         } else {
            return this.surface.mouseClicked(var1, var3, var5) ? true : this.isMouseOver(var1, var3);
         }
      }
   }

   @Override
   public boolean mouseReleased(double var1, double var3, int var5) {
      if (this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else {
         return this.surface.mouseReleased(var1, var3, var5);
      }
   }

   @Override
   public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
      if (!this.dragging) {
         return this.surface.mouseDragged(var1, var3, var5, var6, var8);
      } else {
         DirectViewport var10 = this.surface.viewport();
         float var11 = var10.toUiX(var1);
         float var12 = var10.toUiY(var3);
         RiptideWindowLayout var13 = this.clampToViewport(
            new RiptideWindowLayout(
               Math.round(var11 - this.dragOffsetX),
               Math.round(var12 - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.mainMenuSection != null && !this.mainMenuSection.isExpanded()
            )
         );
         this.panelX = var13.x;
         this.panelY = var13.y;
         this.dragMoved = this.dragMoved
            || Math.abs(var11 - this.pressStartUiX) >= 3.0F
            || Math.abs(var12 - this.pressStartUiY) >= 3.0F
            || this.panelX != this.pressStartPanelX
            || this.panelY != this.pressStartPanelY;
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double var1, double var3, double var5) {
      return this.surface.mouseScrolled(var1, var3, var5);
   }

   @Override
   public boolean keyPressed(int var1, int var2, int var3) {
      if (!this.visible || this.isCollapsed()) {
         return false;
      } else if (var1 == 258 && this.chatField != null && this.chatField.isFocused() && this.chatField.text().isEmpty()) {
         String var4 = this.lastSentChatLine();
         if (var4 != null && !var4.isBlank()) {
            this.chatField.setText(var4);
            this.chatField.moveCursorToEnd();
         }

         return true;
      } else {
         return this.surface.keyPressed(var1, var2, var3);
      }
   }

   @Override
   public boolean charTyped(char var1, int var2) {
      return this.visible && !this.isCollapsed() ? this.surface.charTyped(var1, var2) : false;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean var1) {
      this.visible = var1;
      if (!var1) {
         this.surface.clearFocusedTextInputs();
         this.dragging = false;
         this.dragMoved = false;
      } else if (this.mainMenuSection != null) {
         this.mainMenuSection.syncExpanded(this.mainMenuSection.isExpanded());
      }
   }

   @Override
   public boolean isMouseOver(double var1, double var3) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport var5 = this.surface.viewport();
         float var6 = var5.toUiX(var1);
         float var7 = var5.toUiY(var3);
         return var6 >= this.panelX && var6 < this.panelX + this.panelWidth && var7 >= this.panelY && var7 < this.panelY + this.panelHeight;
      }
   }

   @Override
   public boolean isOverDragBar(double var1, double var3) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport var5 = this.surface.viewport();
         return this.isOverMainMenuHeader(var5.toUiX(var1), var5.toUiY(var3));
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean isCollapsed() {
      return this.mainMenuSection != null && !this.mainMenuSection.isExpanded();
   }

   @Override
   public void setCollapsed(boolean var1) {
      if (this.isCollapsed() != var1) {
         if (this.mainMenuSection != null) {
            this.mainMenuSection.syncExpanded(!var1);
         }

         if (var1) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.surface.hasFocusedTextInput();
   }

   @Override
   public void clearTextFieldFocus() {
      this.surface.clearFocusedTextInputs();
   }

   private boolean isOverMainMenuHeader(float var1, float var2) {
      return this.mainMenuSection != null
         && var1 >= this.panelX
         && var1 < this.panelX + this.panelWidth
         && var2 >= this.panelY
         && var2 < this.panelY + this.sectionHeaderHeight();
   }

   private RiptideWindowLayout clampToViewport(RiptideWindowLayout var1) {
      DirectViewport var2 = this.surface.viewport();
      byte var3 = 4;
      int var4 = Math.round(var2.uiWidth());
      int var5 = Math.round(var2.uiHeight());
      int var6 = Math.max(1, var4 - var3 * 2);
      int var7 = Math.max(this.sectionHeaderHeight(), var5 - var3 * 2);
      int var8 = Math.max(Math.min(this.getMinWidth(), var6), Math.min(var1.width, var6));
      int var9 = var1.collapsed ? this.sectionHeaderHeight() : this.getMinHeight();
      int var10 = Math.max(Math.min(var9, var7), Math.min(var1.height, var7));
      int var11 = var1.collapsed ? this.sectionHeaderHeight() : var10;
      int var12 = Math.max(var3, Math.min(var1.x, Math.max(var3, var4 - var3 - var8)));
      int var13 = Math.max(var3, Math.min(var1.y, Math.max(var3, var5 - var3 - var11)));
      return new RiptideWindowLayout(var12, var13, var8, var10, var1.visible, var1.collapsed);
   }

   private int computePanelWidth() {
      if (MC.font == null) {
         return this.minPanelWidth();
      } else {
         int var1 = UiText.reloadGeneration();
         if (this.cachedPanelWidthValue != Integer.MIN_VALUE
            && this.cachedWidthFont == MC.font
            && this.cachedWidthReloadGen == var1
            && this.cachedWidthFabricator == this.includeFabricatorButton
            && this.cachedWidthScreen == this.includeScreenSection
            && this.cachedWidthChat == this.includeChatSection) {
            return this.cachedPanelWidthValue;
         } else {
            int var2 = this.computePanelWidthUncached();
            this.cachedWidthFont = MC.font;
            this.cachedWidthReloadGen = var1;
            this.cachedWidthFabricator = this.includeFabricatorButton;
            this.cachedWidthScreen = this.includeScreenSection;
            this.cachedWidthChat = this.includeChatSection;
            this.cachedPanelWidthValue = var2;
            return var2;
         }
      }
   }

   private int computePanelWidthUncached() {
      int var1 = this.minPairWidth();
      var1 = Math.max(var1, this.requiredButtonWidth("Macros", UiAssets.ICON_MACROS));
      if (this.includeFabricatorButton) {
         var1 = Math.max(var1, this.requiredButtonWidth("Fabricator", UiAssets.ICON_FABRICATOR));
      }

      var1 = Math.max(var1, this.requiredButtonWidth("LAN Sync", UiAssets.ICON_LANSYNC));
      var1 = Math.max(var1, this.requiredButtonWidth("Queue", UiAssets.ICON_PACKET_Q_EDITOR));
      var1 = Math.max(var1, this.requiredButtonWidth("Logger", UiAssets.ICON_PACKET_LOGGER));
      var1 = Math.max(var1, this.requiredButtonWidth("Packets", UiAssets.ICON_FILTER));
      var1 = Math.max(var1, this.requiredButtonWidth("Server Info", UiAssets.ICON_SERVER_INFO));
      var1 = Math.max(var1, this.requiredButtonWidth("Settings", UiAssets.ICON_KEYBINDS));
      if (!RiptideLiteVariant.enabled()) {
         var1 = Math.max(var1, this.requiredButtonWidth("Multi 500", UiAssets.ICON_MULTI));
      }

      var1 = Math.max(var1, this.requiredButtonWidth("Send", null));
      var1 = Math.max(var1, this.requiredButtonWidth("Delay", null));
      var1 = Math.max(var1, this.requiredButtonWidth("Flush", null));
      var1 = Math.max(var1, this.requiredButtonWidth("Clear", null));
      if (this.includeScreenSection) {
         var1 = Math.max(var1, this.requiredButtonWidth("Close", null));
         var1 = Math.max(var1, this.requiredButtonWidth("De-sync", null));
         var1 = Math.max(var1, this.requiredButtonWidth("Save", null));
         var1 = Math.max(var1, this.requiredButtonWidth("Load", null));
         var1 = Math.max(var1, this.requiredButtonWidth("Title", null));
         var1 = Math.max(var1, this.requiredButtonWidth("Disc+Send", null));
      }

      int var2 = this.includeChatSection ? Math.max(this.chatMinWidth(), this.requiredTextFieldWidth("Type message or /command...")) : 0;
      int var3 = UiText.width(MC.font, "MAIN MENU", this.theme.fontFor(UiTone.LABEL), this.theme.color(UiTone.LABEL))
         + this.labelIconSize()
         + this.buttonIconGap()
         + this.headerReserveWidth();
      int var4 = this.requiredIconLabelWidth("PACKET");
      if (this.includeScreenSection) {
         var4 = Math.max(var4, this.requiredIconLabelWidth("SCREEN"));
      }

      int var5 = Math.max(var3, Math.max(var4, Math.max(var1 * 2 + this.rowGap(), var2)));
      return Math.max(this.minPanelWidth(), var5 + this.sectionPadding() * 2 + this.panelWidthReserve());
   }

   private int requiredButtonWidth(String var1, Identifier var2) {
      if (MC.font == null) {
         return this.minPairWidth();
      } else {
         int var3 = UiText.width(MC.font, var1, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY))
            + this.buttonPadding() * 2
            + this.buttonOutlineReserve();
         if (var2 != null) {
            var3 += this.buttonIconSize() + this.buttonIconGap();
         }

         return var3;
      }
   }

   private int requiredIconLabelWidth(String var1) {
      return UiText.width(MC.font, var1, this.theme.fontFor(UiTone.LABEL), this.theme.color(UiTone.LABEL))
         + this.labelIconSize()
         + this.buttonIconGap()
         + this.buttonOutlineReserve();
   }

   private int requiredTextFieldWidth(String var1) {
      return UiText.width(MC.font, var1, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.MUTED))
         + this.textFieldExtraWidth()
         + this.buttonOutlineReserve();
   }

   private int sectionPadding() {
      return 0;
   }

   private int sectionGap() {
      return 0;
   }

   private int rowGap() {
      return 0;
   }

   private int buttonHeight() {
      return 15;
   }

   private int buttonPadding() {
      return 5;
   }

   private int buttonIconSize() {
      return 12;
   }

   private int buttonIconGap() {
      return 3;
   }

   private int labelIconSize() {
      return 11;
   }

   private int minPairWidth() {
      return 54;
   }

   private int minPanelWidth() {
      return 152;
   }

   private int minimumPanelHeight() {
      return 56;
   }

   private int sectionHeaderHeight() {
      return 15;
   }

   private int sectionContentTopGap() {
      return 0;
   }

   private int chatHeight() {
      return 16;
   }

   private int chatMinWidth() {
      return 124;
   }

   private int syncRowGap() {
      return 4;
   }

   private int headerReserveWidth() {
      return 16;
   }

   private int textFieldExtraWidth() {
      return 10;
   }

   private int buttonOutlineReserve() {
      return 2;
   }

   private int panelWidthReserve() {
      return 2;
   }

   private void toggleSendPackets() {
      RiptideSharedState var1 = RiptideSharedState.get();
      boolean var2 = !var1.shouldSendGuiPackets();
      RiptideModule.get().applySendGuiPacketsUiBehavior(var2);
      RiptideNotifications.show("Send Packets " + (var2 ? "on" : "off"), var2 ? -13248397 : -50373);
   }

   private void toggleDelayPackets() {
      RiptideSharedState var1 = RiptideSharedState.get();
      boolean var2 = !var1.shouldDelayGuiPackets();
      RiptideModule var3 = RiptideModule.get();
      int var4 = var3.applyDelayGuiPacketsUiBehavior(var2);
      var3.notifyDelayPacketsUiResult(var2, var4);
   }

   private void flushQueue() {
      RiptideModule var1 = RiptideModule.get();
      int var2 = var1.flushQueuedPacketsUiBehavior();
      var1.notifyFlushQueuedPacketsUiResult(var2);
   }

   private void clearQueue() {
      RiptideModule var1 = RiptideModule.get();
      int var2 = var1.clearQueuedPacketsUiBehavior();
      var1.notifyClearQueuedPacketsUiResult(var2);
   }

   private void sendDesync() {
      if (!RiptideGuiActions.desyncCurrentScreen(MC)) {
         RiptideClientMessaging.sendPrefixed("Failed to desync: no open networked GUI.");
      }
   }

   private void saveGui() {
      RiptideGuiActions.saveCurrentGui(MC);
   }

   private void loadGui() {
      if (RiptideModule.get().restoreSavedScreenUiBehavior()) {
         RiptideNotifications.show("GUI restored.", -13248397);
      } else {
         RiptideNotifications.error("No stored GUI.");
      }
   }

   private void disconnectAndSend() {
      if (!PackHideState.isHardLocked()) {
         RiptideSharedState var1 = RiptideSharedState.get();
         RiptideModule.get().setDelayGuiPackets(false);
         if (MC.getConnection() != null) {
            var1.flushDelayedPackets(MC.getConnection());
            MC.getConnection().getConnection().disconnect(Component.literal("Disconnecting (Riptide)"));
         }
      }
   }

   private void submitChat(String var1) {
      if (!PackHideState.isHardLocked() && var1 != null) {
         String var2 = var1.trim();
         if (var2.isEmpty()) {
            String var3 = this.lastSentChatLine();
            if (var3 == null || var3.isBlank()) {
               return;
            }

            var2 = var3.trim();
         }

         if (RiptideCommands.isRiptideCommandMessage(var2)) {
            if (RiptideCommands.isBlockedPanicCommandMessage(var2)) {
               if (this.chatField != null) {
                  this.chatField.setText("");
                  this.chatField.setFocused(false);
               }

               return;
            }

            String var4 = RiptideCommands.commandBody(var2);
            if (!var4.isBlank()) {
               RiptideCommands.dispatch(var4);
            }
         } else {
            if (MC.getConnection() == null) {
               return;
            }

            if (var2.startsWith("/") && var2.length() > 1) {
               MC.getConnection().sendCommand(var2.substring(1));
            } else {
               MC.getConnection().sendChat(var2);
            }
         }

         RiptideClientMessaging.rememberRecentChat(var2);
         if (this.chatField != null) {
            this.chatField.addHistoryEntry(var2);
         }

         if (this.chatField != null) {
            this.chatField.setText("");
            this.chatField.setFocused(false);
         }
      }
   }

   private void updateChatGhost() {
      if (this.chatField != null) {
         String var1 = this.lastSentChatLine();
         this.chatField.setPlaceholder(var1 != null && !var1.isBlank() ? var1 : "Type message or /command...");
      }
   }

   private String lastSentChatLine() {
      try {
         if (MC.gui != null && MC.gui.hud.getChat() != null) {
            ArrayListDeque var1 = MC.gui.hud.getChat().getRecentChat();
            return var1 == null ? null : (String)var1.peekLast();
         } else {
            return null;
         }
      } catch (Throwable var2) {
         return null;
      }
   }

   private void updateSyncMetrics() {
      String var1 = "--";
      String var2 = "--";
      String var3 = "--";
      if (MC.player != null && MC.player.containerMenu != null) {
         var1 = Integer.toString(MC.player.containerMenu.getStateId());
         var2 = Integer.toString(MC.player.containerMenu.containerId);
         if (MC.gui.screen() instanceof AbstractContainerScreen var4) {
            Slot var6 = ((RiptideHandledScreenAccessor)var4).getFocusedSlot();
            if (var6 != null) {
               var3 = Integer.toString(RiptideInventoryHelper.toUserVisibleSlot(MC, var6.index));
            }
         }
      }

      if (this.revMetric != null) {
         this.revMetric.setValue(var1);
      }

      if (this.syncMetric != null) {
         this.syncMetric.setValue(var2);
      }

      if (this.slotMetric != null) {
         this.slotMetric.setValue(var3);
      }
   }
}
