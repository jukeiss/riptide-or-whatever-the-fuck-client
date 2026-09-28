package riptide.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.mixin.accessor.AbstractContainerScreenAccessor;
import riptide.util.macro.CraftAction;
import riptide.util.macro.DropAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.PacketClickAction;

public class RiptideFabricatorOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final CompactTheme THEME = new CompactTheme();
   private static RiptideFabricatorOverlay sharedOverlay;
   private int DEFAULT_PANEL_WIDTH = 236;
   private int LINE_HEIGHT = 14;
   private int CRAFT_LIST_HEIGHT = 84;
   private static final int CRAFT_LIST_ROWS = 6;
   private int CRAFT_PLAN_HEIGHT = 52;
   private static final int CRAFT_PLAN_ROWS = 4;
   private static final int SCROLLBAR_GUTTER = 8;
   private int panelX = 220;
   private int panelY = 5;
   private int PANEL_WIDTH = this.DEFAULT_PANEL_WIDTH;
   private int PANEL_HEIGHT = 392;
   private int FIELD_WIDTH = 100;
   private int BUTTON_HEIGHT = 20;
   private int LABEL_WIDTH = 64;
   private boolean isDragging = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private boolean collapsed = false;
   private boolean visible = false;
   private AbstractContainerScreen<?> parentScreen;
   private final List<RiptideChatField> textFields = new ArrayList<>();
   private RiptideChatField slotField;
   private RiptideChatField itemNameField;
   private RiptideChatField timesField;
   private RiptideChatField craftSearchField;
   private int currentActionIndex = 0;
   private final RiptideFabricatorOverlay.FabricatorAction[] actions = RiptideFabricatorOverlay.FabricatorAction.values();
   private final List<String> actionLabels = Arrays.stream(this.actions).map(action -> action.displayName).toList();
   private static final List<String> STANDARD_CLICK_LABELS = List.of("Left Click", "Right Click");
   private static final List<String> PACKET_CLICK_LABELS = List.of("Left Click", "Right Click", "Quick Move");
   private static final List<String> DROP_MODE_LABELS = List.of("Single", "Stack");
   private final List<CompactDropdown> uiDropdowns = new ArrayList<>();
   private CompactDropdown actionDropdown;
   private CompactDropdown clickModeDropdown;
   private CompactDropdown dropModeDropdown;
   private int currentButtonIndex = 0;
   private final String[] buttonTypes = new String[]{"Left Click", "Right Click", "Quick Move"};
   private boolean dropWholeStack = false;
   private RiptidePacketClick.Target packetClickTarget = null;
   private Integer selectedSlotId = null;
   private String statusMessage = "";
   private ChatFormatting statusColor = ChatFormatting.GRAY;
   private long statusExpiresAtMs = 0L;
   private final List<RiptideCraftingHelper.CraftableRecipeOption> craftableRecipes = new ArrayList<>();
   private List<RiptideCraftingHelper.CraftableRecipeOption> filteredCraftableRecipes = new ArrayList<>();
   private int readyCraftableRecipeCount = 0;
   private int craftScrollOffset = 0;
   private final ScrollState craftListScrollState = new ScrollState();
   private RiptideCraftingHelper.CraftableRecipeOption selectedCraftOption = null;
   private ItemStack selectedCraftResult = ItemStack.EMPTY;
   private final List<CraftAction.CraftEntry> plannedCraftEntries = new ArrayList<>();
   private int craftPlanSelectedIndex = -1;
   private int craftPlanScrollOffset = 0;
   private final ScrollState craftPlanScrollState = new ScrollState();
   private static final ExecutorService CRAFT_SCAN_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "riptide-craft-scan");
      t.setDaemon(true);
      return t;
   });
   private final AtomicReference<RiptideFabricatorOverlay.ScanResult> pendingScanResult = new AtomicReference<>();
   private final AtomicLong scanGeneration = new AtomicLong();
   private volatile boolean scanInFlight;
   private int lastScanFingerprint;
   private boolean hasScannedOnce;
   private volatile boolean craftExecutionInProgress = false;
   private boolean craftUseMaxAmount = false;
   private int activeScrollbarDrag = 0;
   private int scrollbarGrabOffset = 0;
   private static final int SCROLLBAR_NONE = 0;
   private static final int SCROLLBAR_CRAFT_PLAN = 1;
   private static final int SCROLLBAR_CRAFT_LIST = 2;
   private String savedSlotValue = "0";
   private String savedItemNameValue = "";
   private ItemTarget savedItemTarget = new ItemTarget();
   private String savedTimesValue = "1";
   private String savedCraftSearchValue = "";
   private int savedCraftSelectedRecipeId = -1;
   private String savedCraftSelectedRecipeKey = "";
   private RiptideFabricatorOverlay.FabricatorAction currentAction = RiptideFabricatorOverlay.FabricatorAction.CLICK;

   private int getFieldWidth() {
      return Math.max(108, this.PANEL_WIDTH - this.LABEL_WIDTH - 26);
   }

   private int getBottomButtonY() {
      return this.panelY + this.PANEL_HEIGHT - this.BUTTON_HEIGHT - 18;
   }

   private int getStatusY() {
      return Math.max(this.getInfoFloorY(), this.getActionInfoY() - 11);
   }

   private int getActionInfoY() {
      return Math.max(this.getInfoFloorY() + 11, this.getBottomButtonY() - 3 - this.getActionDescriptionLines().size() * 10);
   }

   private List<String> getActionDescriptionLines() {
      return UiContexts.textRenderer(MC.font).wrapFully(this.currentAction.getDescription(this.dropWholeStack), Math.max(1, this.PANEL_WIDTH - 20));
   }

   private int getInfoFloorY() {
      return this.isCraftMode() ? this.getCraftListY() + this.CRAFT_LIST_HEIGHT + 12 : this.getStandardTimesRowY() + 18;
   }

   private int requiredPanelHeightForInfoRows(int floorY) {
      int relativeFloor = Math.max(0, floorY - this.panelY);
      return relativeFloor + 11 + this.getActionDescriptionLines().size() * 10 + 3 + this.BUTTON_HEIGHT + 18;
   }

   private boolean isCraftMode() {
      return this.currentAction.isCraftAction();
   }

   private boolean showsTargetFields() {
      return !this.isCraftMode();
   }

   private boolean showsSlotItemRows() {
      return this.showsTargetFields() && !this.currentAction.isPacketAction();
   }

   private int getActionRowY() {
      return this.panelY + 24;
   }

   private int getSlotRowY() {
      return this.getActionRowY() + 22;
   }

   private int getItemRowY() {
      return this.getSlotRowY() + (this.showsSlotItemRows() ? 22 : 0);
   }

   private int getOptionRowY() {
      return this.getItemRowY() + (this.showsSlotItemRows() ? 22 : 0);
   }

   private int getStandardTimesRowY() {
      return !this.isClickSelectorRelevant() && !this.isDropModeRelevant() ? this.getItemRowY() + 22 : this.getOptionRowY() + 22;
   }

   private int getCraftSearchY() {
      return this.getCraftSearchLabelY() + 11;
   }

   private int getCraftAmountRowY() {
      return this.getCraftSummaryY() + 14;
   }

   private int getCraftAmountToggleWidth() {
      return 78;
   }

   private int getCraftAmountFieldX() {
      return this.panelX + this.LABEL_WIDTH + 15;
   }

   private int getCraftAmountToggleX() {
      return this.getCraftAmountFieldX() + this.getCraftAmountFieldWidth() + 4;
   }

   private int getCraftAddButtonWidth() {
      return 44;
   }

   private int getCraftAmountFieldWidth() {
      return Math.max(48, this.getFieldWidth() - this.getCraftAmountToggleWidth() - this.getCraftAddButtonWidth() - 8);
   }

   private int getCraftAddButtonX() {
      return this.getCraftAmountToggleX() + this.getCraftAmountToggleWidth() + 4;
   }

   private int getCraftPlanHeaderY() {
      return this.getSlotRowY() + 2;
   }

   private int getCraftPlanListY() {
      return this.getSlotRowY() + 13;
   }

   private int getCraftSummaryY() {
      return this.getCraftPlanListY() + this.CRAFT_PLAN_HEIGHT + 3;
   }

   private int getCraftSearchLabelY() {
      return this.getCraftAmountRowY() + 18;
   }

   private boolean isClickSelectorRelevant() {
      return this.currentAction.usesClickSelector();
   }

   private int getClickSelectorOptionCount() {
      return this.currentAction.isPacketAction() ? RiptidePacketClick.Mode.values().length : 2;
   }

   private RiptidePacketClick.Mode getCurrentPacketMode() {
      return RiptidePacketClick.Mode.byIndex(this.currentButtonIndex);
   }

   private boolean isDropModeRelevant() {
      return this.currentAction.usesDropToggle();
   }

   private int getEffectiveButton() {
      if (this.currentAction.isPacketAction()) {
         return this.getCurrentPacketMode().button;
      } else if (this.currentAction.usesDropToggle()) {
         return this.dropWholeStack ? 1 : 0;
      } else {
         RiptideDropAction packetAction = this.currentAction.toPacketAction(this.dropWholeStack);
         return packetAction.usesFixedButton() ? packetAction.getButton() : Math.min(this.currentButtonIndex, 1);
      }
   }

   private String getClickSelectorLabel() {
      if (this.currentAction.isPacketAction()) {
         return this.getCurrentPacketMode().displayName;
      } else if (this.isClickSelectorRelevant()) {
         return this.buttonTypes[Math.min(this.currentButtonIndex, 1)];
      } else if (this.isDropModeRelevant()) {
         return this.dropWholeStack ? "Whole Stack" : "Single Item";
      } else {
         return "Auto";
      }
   }

   private void drawOverlayButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, int mouseX, int mouseY
   ) {
      CompactOverlayControls.action(context, MC.font, x, y, w, h, label, variant, enabled, mouseX, mouseY);
   }

   private void drawOverlayToggleButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean enabled, String animationKey, int mouseX, int mouseY
   ) {
      CompactOverlayControls.toggle(context, MC.font, x, y, w, h, label, enabled, animationKey, mouseX, mouseY);
   }

   public RiptideFabricatorOverlay(AbstractContainerScreen<?> parentScreen) {
      this.parentScreen = parentScreen;
      this.applyPresetMetrics();
      this.PANEL_WIDTH = this.DEFAULT_PANEL_WIDTH;
      this.PANEL_HEIGHT = this.craftModePanelHeight();
   }

   public static synchronized RiptideFabricatorOverlay getSharedOverlay(AbstractContainerScreen<?> parentScreen) {
      if (sharedOverlay == null) {
         sharedOverlay = new RiptideFabricatorOverlay(parentScreen);
         sharedOverlay.restoreState();
      } else {
         sharedOverlay.parentScreen = parentScreen;
      }

      return sharedOverlay;
   }

   @Override
   public int getMinWidth() {
      return this.DEFAULT_PANEL_WIDTH;
   }

   @Override
   public int getMinHeight() {
      return this.standardPanelHeight();
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
         if (this.visible) {
            this.initWidgets();
         }
      }
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (visible) {
         this.initWidgets();
         RiptideOverlayManager.get().bringToFront(this);
      } else {
         this.clearWidgets();
      }

      this.saveState();
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
   public void setCollapsed(boolean collapsed) {
      if (this.collapsed != collapsed) {
         this.collapsed = collapsed;
         if (collapsed) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         int panelHeight = this.collapsed ? 18 : this.PANEL_HEIGHT;
         return mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + panelHeight;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         return mouseX >= this.panelX
            && mouseX <= this.panelX + this.PANEL_WIDTH
            && mouseY >= this.panelY
            && mouseY <= this.panelY + 18
            && !this.isOverWindowControl(mouseX, mouseY, bounds);
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      for (RiptideChatField field : this.textFields) {
         if (field.isFocused()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void clearTextFieldFocus() {
      for (RiptideChatField field : this.textFields) {
         field.setFocused(false);
      }
   }

   public void saveState() {
      RiptideSharedState shared = RiptideSharedState.get();
      shared.setFabricatorOverlayVisible(this.visible);
      shared.setFabricatorOverlayX(this.panelX);
      shared.setFabricatorOverlayY(this.panelY);
      shared.setFabricatorSlotValue(this.savedSlotValue);
      shared.setFabricatorItemNameValue(this.savedItemNameValue);
      shared.setFabricatorTimesValue(this.savedTimesValue);
      shared.setFabricatorActionIndex(this.currentActionIndex);
      shared.setFabricatorButtonIndex(this.currentButtonIndex);
      shared.setFabricatorDropWholeStack(this.dropWholeStack);
      shared.setFabricatorCraftUseMaxAmount(this.craftUseMaxAmount);
      shared.setFabricatorCraftSearchValue(this.savedCraftSearchValue);
      shared.setFabricatorCraftSelectedRecipeId(this.savedCraftSelectedRecipeId);
      shared.setFabricatorCraftSelectedRecipeKey(this.savedCraftSelectedRecipeKey);
      shared.setFabricatorCraftScrollOffset(this.craftScrollOffset);
      shared.setFabricatorCraftPlanEntries(this.plannedCraftEntries);
      shared.setFabricatorCraftPlanSelectedIndex(this.craftPlanSelectedIndex);
      shared.setFabricatorCraftPlanScrollOffset(this.craftPlanScrollOffset);
      this.saveLayout();
   }

   public void restoreState() {
      RiptideSharedState shared = RiptideSharedState.get();
      this.restoreLayout();
      this.visible = shared.isFabricatorOverlayVisible();
      this.panelX = shared.getFabricatorOverlayX();
      this.panelY = shared.getFabricatorOverlayY();
      this.savedSlotValue = shared.getFabricatorSlotValue();
      this.savedItemNameValue = shared.getFabricatorItemNameValue();
      this.savedTimesValue = shared.getFabricatorTimesValue();
      this.currentActionIndex = shared.getFabricatorActionIndex();
      this.currentButtonIndex = shared.getFabricatorButtonIndex();
      this.dropWholeStack = shared.isFabricatorDropWholeStack();
      this.craftUseMaxAmount = shared.isFabricatorCraftUseMaxAmount();
      this.savedCraftSearchValue = shared.getFabricatorCraftSearchValue();
      this.savedCraftSelectedRecipeId = shared.getFabricatorCraftSelectedRecipeId();
      this.savedCraftSelectedRecipeKey = shared.getFabricatorCraftSelectedRecipeKey();
      this.craftScrollOffset = shared.getFabricatorCraftScrollOffset();
      this.craftListScrollState.restore(this.craftScrollOffset * this.LINE_HEIGHT);
      this.plannedCraftEntries.clear();
      this.plannedCraftEntries.addAll(shared.getFabricatorCraftPlanEntries());
      this.craftPlanSelectedIndex = shared.getFabricatorCraftPlanSelectedIndex();
      this.craftPlanScrollOffset = shared.getFabricatorCraftPlanScrollOffset();
      this.craftPlanScrollState.restore(this.craftPlanScrollOffset * 13);
      this.selectedCraftOption = null;
      this.selectedCraftResult = ItemStack.EMPTY;
      if (this.currentActionIndex < 0 || this.currentActionIndex >= this.actions.length) {
         this.currentActionIndex = 0;
      }

      this.currentAction = this.actions[this.currentActionIndex];
      if (this.currentButtonIndex < 0 || this.currentButtonIndex >= this.buttonTypes.length) {
         this.currentButtonIndex = 0;
      }

      if (this.visible) {
         this.initWidgets();
      }
   }

   private void initWidgets() {
      this.clearWidgets();
      if (!this.isDragging) {
         this.updateListHeights();
         this.PANEL_HEIGHT = this.isCraftMode() ? this.craftModePanelHeight() : this.standardPanelHeight();
      }

      int fieldX = this.panelX + this.LABEL_WIDTH + 15;
      int fieldWidth = this.getFieldWidth();
      this.currentAction = this.actions[this.currentActionIndex];
      this.slotField = new RiptideChatField(MC, MC.font, fieldX, this.getSlotRowY(), fieldWidth, 16, false);
      this.slotField.setPlaceholder(Component.literal("Optional slot - right-click to fill"));
      this.slotField.setText(this.savedSlotValue);
      this.slotField.setMaxLength(5);
      this.slotField.setChangedListener(text -> {
         this.savedSlotValue = text;
         this.clearStatus();

         try {
            int stableSlot = Integer.parseInt(text);
            int slotId = RiptideInventoryHelper.resolveConfiguredHandlerSlot(MC, stableSlot);
            ItemTarget slotTarget = this.getItemTargetFromSlot(slotId);
            String itemName = slotTarget.editorText();
            if (!itemName.isEmpty()) {
               this.savedItemTarget = slotTarget;
               this.savedItemNameValue = itemName;
               if (this.itemNameField != null) {
                  this.itemNameField.setText(itemName);
               }
            }
         } catch (NumberFormatException var6x) {
         }
      });
      if (this.showsSlotItemRows()) {
         this.textFields.add(this.slotField);
      }

      this.itemNameField = new RiptideChatField(MC, MC.font, fieldX, this.getItemRowY(), fieldWidth, 16, false);
      this.itemNameField.setPlaceholder(Component.literal("Optional item match").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)));
      this.itemNameField.setText(this.savedItemNameValue);
      this.itemNameField.setMaxLength(50);
      this.itemNameField.setDisplayTextProvider(value -> {
         Component rich = this.savedItemTarget == null ? null : this.savedItemTarget.editorComponent(value);
         return rich != null && value != null && value.equals(rich.getString()) ? rich.copy() : null;
      });
      this.itemNameField.setChangedListener(text -> {
         this.savedItemNameValue = text;
         if (this.savedItemTarget != null && !text.equals(this.savedItemTarget.editorText())) {
            this.savedItemTarget = this.savedItemTarget.hasRichText() ? this.savedItemTarget.withEditedDisplay(text) : ItemTarget.fromLegacyEntry(text);
         }

         this.clearStatus();
      });
      if (this.showsSlotItemRows()) {
         this.textFields.add(this.itemNameField);
      }

      int timesY = this.isCraftMode() ? this.getCraftAmountRowY() : this.getStandardTimesRowY();
      int timesX = this.isCraftMode() ? this.getCraftAmountFieldX() : fieldX;
      int timesWidth = this.isCraftMode() ? this.getCraftAmountFieldWidth() : fieldWidth;
      this.timesField = new RiptideChatField(MC, MC.font, timesX, timesY, timesWidth, 16, false);
      this.timesField.setPlaceholder(Component.literal("1"));
      this.timesField.setText(this.savedTimesValue);
      this.timesField.setMaxLength(5);
      this.timesField.setEditable(!this.isCraftMode() || !this.craftUseMaxAmount);
      this.timesField.setChangedListener(text -> {
         this.savedTimesValue = text;
         if (this.isCraftMode() && this.craftPlanSelectedIndex >= 0 && this.craftPlanSelectedIndex < this.plannedCraftEntries.size()) {
            try {
               int val = Integer.parseInt(text.replaceAll("[^0-9]", ""));
               this.plannedCraftEntries.get(this.craftPlanSelectedIndex).amount = Math.max(1, val);
            } catch (Exception var3x) {
            }
         }

         this.clearStatus();
      });
      this.textFields.add(this.timesField);
      if (this.isCraftMode()) {
         int searchY = this.getCraftSearchY();
         this.craftSearchField = new RiptideChatField(MC, MC.font, this.panelX + 10, searchY, this.PANEL_WIDTH - 20, 16, false);
         this.craftSearchField.setPlaceholder(Component.literal("Search crafting recipes..."));
         this.craftSearchField.setText(this.savedCraftSearchValue);
         this.craftSearchField.setChangedListener(text -> {
            this.savedCraftSearchValue = text;
            this.clearStatus();
            this.updateCraftRecipeFilter(text, true);
            this.saveState();
         });
         this.textFields.add(this.craftSearchField);
         this.pollCraftScan();
      } else {
         this.craftSearchField = null;
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         if (!this.uiDropdowns.isEmpty()) {
            if (CompactDropdown.mouseClicked(this.uiDropdowns, mouseX, mouseY, button)) {
               return true;
            }

            if (CompactDropdown.isMenuOpen(this.uiDropdowns)) {
               return true;
            }
         }

         if (button == 0 && mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + 20) {
            RiptideWindowLayout bounds = new RiptideWindowLayout(
               this.panelX, this.panelY, this.PANEL_WIDTH, this.collapsed ? 18 : this.PANEL_HEIGHT, this.visible, this.collapsed
            );
            if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
               this.setVisible(false);
               return true;
            } else {
               this.isDragging = true;
               this.dragOffsetX = mouseX - this.panelX;
               this.dragOffsetY = mouseY - this.panelY;

               for (RiptideChatField field : this.textFields) {
                  field.setFocused(false);
               }

               return true;
            }
         } else if (this.collapsed) {
            return false;
         } else {
            MouseButtonEvent click = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
            boolean clickedField = false;

            for (RiptideChatField field : this.textFields) {
               boolean wasClicked = field.mouseClicked(click, false);
               if (wasClicked) {
                  field.setFocused(true);
                  clickedField = true;
               } else {
                  field.setFocused(false);
               }
            }

            if (clickedField) {
               return true;
            } else {
               if (this.isCraftMode()) {
                  this.pollCraftScan();
                  int listX = this.getCraftListX();
                  int listWidth = this.getCraftListWidth();
                  int planContentWidth = this.getCraftPlanContentWidth();
                  int recipeContentWidth = this.getCraftListContentWidth();
                  int planHeaderY = this.getCraftPlanHeaderY();
                  int planListY = this.getCraftPlanListY();
                  int listY = this.getCraftListY();
                  if (button == 0) {
                     CompactScrollbar.Metrics craftPlanScrollbar = this.getCraftPlanScrollbarMetrics();
                     if (craftPlanScrollbar.hasScroll() && craftPlanScrollbar.contains((int)mouseX, (int)mouseY)) {
                        this.activeScrollbarDrag = 1;
                        this.scrollbarGrabOffset = Math.max(0, (int)mouseY - craftPlanScrollbar.thumbY());
                        this.craftPlanScrollOffset = this.toRowScroll(
                           craftPlanScrollbar, (int)mouseY, this.scrollbarGrabOffset, 13, Math.max(0, this.plannedCraftEntries.size() - 4)
                        );
                        this.craftPlanScrollState
                           .jumpTo(this.craftPlanScrollOffset * 13, Math.max(0, this.plannedCraftEntries.size() * 13 - this.CRAFT_PLAN_HEIGHT));
                        this.saveState();
                        return true;
                     }

                     CompactScrollbar.Metrics craftListScrollbar = this.getCraftListScrollbarMetrics();
                     if (craftListScrollbar.hasScroll() && craftListScrollbar.contains((int)mouseX, (int)mouseY)) {
                        this.activeScrollbarDrag = 2;
                        this.scrollbarGrabOffset = Math.max(0, (int)mouseY - craftListScrollbar.thumbY());
                        this.craftScrollOffset = this.toRowScroll(
                           craftListScrollbar, (int)mouseY, this.scrollbarGrabOffset, this.LINE_HEIGHT, Math.max(0, this.filteredCraftableRecipes.size() - 6)
                        );
                        this.craftListScrollState
                           .jumpTo(
                              this.craftScrollOffset * this.LINE_HEIGHT,
                              Math.max(0, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT - this.CRAFT_LIST_HEIGHT)
                           );
                        this.saveState();
                        return true;
                     }
                  }

                  if (!this.plannedCraftEntries.isEmpty()
                     && mouseX >= this.panelX + this.PANEL_WIDTH - 74
                     && mouseX < this.panelX + this.PANEL_WIDTH - 10
                     && mouseY >= planHeaderY - 2
                     && mouseY < planHeaderY + 10
                     && button == 0) {
                     this.plannedCraftEntries.clear();
                     this.craftPlanSelectedIndex = -1;
                     this.craftPlanScrollOffset = 0;
                     this.craftPlanScrollState.jumpTo(0, 0);
                     this.clearStatus();
                     this.saveState();
                     return true;
                  }

                  if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= planListY && mouseY <= planListY + this.CRAFT_PLAN_HEIGHT && button == 0) {
                     int maxPlanScrollPx = Math.max(0, this.plannedCraftEntries.size() * 13 - this.CRAFT_PLAN_HEIGHT);
                     int drawPlanScroll = this.craftPlanScrollState.tick(0.0F, maxPlanScrollPx);
                     int firstIndex = drawPlanScroll / 13;
                     int rowY = planListY - drawPlanScroll % 13;

                     for (int index = firstIndex; index < this.plannedCraftEntries.size() && rowY < planListY + this.CRAFT_PLAN_HEIGHT; rowY += 13) {
                        if (!(mouseY < rowY) && !(mouseY >= rowY + 12)) {
                           if (mouseX >= listX + planContentWidth - 16 && mouseX < listX + planContentWidth - 2) {
                              this.removePlannedCraftEntry(index);
                              return true;
                           }

                           if (mouseX >= listX && mouseX < listX + planContentWidth) {
                              if (this.craftPlanSelectedIndex == index) {
                                 this.selectCraftPlanEntry(-1);
                              } else {
                                 this.selectCraftPlanEntry(index);
                              }

                              for (RiptideChatField fieldx : this.textFields) {
                                 fieldx.setFocused(false);
                              }

                              return true;
                           }
                        }

                        index++;
                     }

                     if (mouseX >= listX && mouseX < listX + planContentWidth) {
                        this.selectCraftPlanEntry(-1);

                        for (RiptideChatField fieldx : this.textFields) {
                           fieldx.setFocused(false);
                        }

                        return true;
                     }
                  }

                  if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + this.CRAFT_LIST_HEIGHT && button == 0) {
                     int maxRecipeScrollPx = Math.max(0, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT - this.CRAFT_LIST_HEIGHT);
                     int drawRecipeScroll = this.craftListScrollState.tick(0.0F, maxRecipeScrollPx);
                     int firstIndex = drawRecipeScroll / this.LINE_HEIGHT;
                     int rowY = listY - drawRecipeScroll % this.LINE_HEIGHT;

                     for (int index = firstIndex;
                        index < this.filteredCraftableRecipes.size() && rowY < listY + this.CRAFT_LIST_HEIGHT;
                        rowY += this.LINE_HEIGHT
                     ) {
                        if (mouseX >= listX && mouseX < listX + recipeContentWidth && mouseY >= rowY && mouseY < rowY + this.LINE_HEIGHT) {
                           this.selectCraftRecipe(this.filteredCraftableRecipes.get(index));
                           this.addOrUpdatePlannedCraftEntry();

                           for (RiptideChatField fieldx : this.textFields) {
                              fieldx.setFocused(false);
                           }

                           return true;
                        }

                        index++;
                     }

                     if (mouseX >= listX && mouseX < listX + recipeContentWidth) {
                        this.clearCraftRecipeSelection();

                        for (RiptideChatField fieldx : this.textFields) {
                           fieldx.setFocused(false);
                        }

                        this.saveState();
                        return true;
                     }
                  }
               }

               if (this.isCraftMode()) {
                  int toggleX = this.getCraftAmountToggleX();
                  int toggleY = this.getCraftAmountRowY();
                  int toggleW = this.getCraftAmountToggleWidth();
                  if (mouseY >= toggleY && mouseY < toggleY + 16 && mouseX >= toggleX && mouseX < toggleX + toggleW && (button == 0 || button == 1)) {
                     this.craftUseMaxAmount = !this.craftUseMaxAmount;
                     if (this.timesField != null) {
                        this.timesField.setEditable(!this.craftUseMaxAmount);
                     }

                     if (this.craftPlanSelectedIndex >= 0 && this.craftPlanSelectedIndex < this.plannedCraftEntries.size()) {
                        this.plannedCraftEntries.get(this.craftPlanSelectedIndex).useMaxAmount = this.craftUseMaxAmount;
                     }

                     this.clearStatus();
                     this.saveState();

                     for (RiptideChatField fieldx : this.textFields) {
                        fieldx.setFocused(false);
                     }

                     return true;
                  }
               }

               int btnY = this.getBottomButtonY();
               int btnArea = this.PANEL_WIDTH - 20;
               int gap = 2;
               int bw = (btnArea - gap * 2) / 3;
               int bx = this.panelX + 10;
               if (mouseY >= btnY && mouseY < btnY + this.BUTTON_HEIGHT && button == 0) {
                  if (mouseX >= bx && mouseX < bx + bw) {
                     this.send(false);

                     for (RiptideChatField fieldx : this.textFields) {
                        fieldx.setFocused(false);
                     }

                     return true;
                  }

                  bx += bw + gap;
                  if (mouseX >= bx && mouseX < bx + bw) {
                     this.send(true);

                     for (RiptideChatField fieldx : this.textFields) {
                        fieldx.setFocused(false);
                     }

                     return true;
                  }

                  bx += bw + gap;
                  if (mouseX >= bx && mouseX < bx + bw) {
                     RiptideMacroEditorOverlay macroEditor = null;

                     for (IRiptideOverlay ov : RiptideOverlayManager.get().getOverlays()) {
                        if (ov instanceof RiptideMacroEditorOverlay) {
                           macroEditor = (RiptideMacroEditorOverlay)ov;
                           break;
                        }
                     }

                     if (macroEditor != null && macroEditor.isVisible()) {
                        this.sendToMacro(macroEditor);
                     } else {
                        this.setStatus("Open a macro editor first!", ChatFormatting.RED);
                     }

                     for (RiptideChatField fieldx : this.textFields) {
                        fieldx.setFocused(false);
                     }

                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      CompactDropdown.mouseReleased(this.uiDropdowns);
      if (button == 0 && this.activeScrollbarDrag != 0) {
         this.activeScrollbarDrag = 0;
         this.saveState();
         return true;
      } else {
         if (button == 0) {
            if (this.isDragging) {
               this.saveState();
            }

            this.isDragging = false;
         }

         for (RiptideChatField field : this.textFields) {
            if (field.mouseReleased(mouseX, mouseY, button)) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (CompactDropdown.mouseDragged(this.uiDropdowns, mouseX, mouseY, button)) {
         return true;
      } else if (this.activeScrollbarDrag == 1) {
         this.craftPlanScrollOffset = this.toRowScroll(
            this.getCraftPlanScrollbarMetrics(), (int)mouseY, this.scrollbarGrabOffset, 13, Math.max(0, this.plannedCraftEntries.size() - 4)
         );
         this.craftPlanScrollState.jumpTo(this.craftPlanScrollOffset * 13, Math.max(0, this.plannedCraftEntries.size() * 13 - this.CRAFT_PLAN_HEIGHT));
         return true;
      } else if (this.activeScrollbarDrag == 2) {
         this.craftScrollOffset = this.toRowScroll(
            this.getCraftListScrollbarMetrics(), (int)mouseY, this.scrollbarGrabOffset, this.LINE_HEIGHT, Math.max(0, this.filteredCraftableRecipes.size() - 6)
         );
         this.craftListScrollState
            .jumpTo(this.craftScrollOffset * this.LINE_HEIGHT, Math.max(0, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT - this.CRAFT_LIST_HEIGHT));
         return true;
      } else if (this.isDragging) {
         RiptideWindowLayout nextBounds = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)(mouseX - this.dragOffsetX), (int)(mouseY - this.dragOffsetY), this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed
            )
         );
         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         this.initWidgets();
         return true;
      } else {
         for (RiptideChatField field : this.textFields) {
            if (field.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed) {
         if (!this.uiDropdowns.isEmpty()
            && (CompactDropdown.isMenuOpen(this.uiDropdowns) || CompactDropdown.isInsideOpenMenu(this.uiDropdowns, mouseX, mouseY))
            && CompactDropdown.mouseScrolled(this.uiDropdowns, mouseX, mouseY, amount)) {
            return true;
         } else {
            if (this.isCraftMode()) {
               int planX = this.getCraftListX();
               int planY = this.getCraftPlanListY();
               int listWidth = this.getCraftListWidth();
               if (mouseX >= planX && mouseX <= planX + listWidth && mouseY >= planY && mouseY <= planY + this.CRAFT_PLAN_HEIGHT) {
                  int maxPlanScroll = Math.max(0, this.plannedCraftEntries.size() - 4);
                  this.craftPlanScrollOffset = Math.max(0, Math.min(maxPlanScroll, this.craftPlanScrollOffset - (int)Math.signum(amount)));
                  this.craftPlanScrollState
                     .setTarget(this.craftPlanScrollOffset * 13, Math.max(0, this.plannedCraftEntries.size() * 13 - this.CRAFT_PLAN_HEIGHT));
                  this.saveState();
                  return true;
               }

               int listX = this.getCraftListX();
               int listY = this.getCraftListY();
               if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + this.CRAFT_LIST_HEIGHT) {
                  int maxScroll = Math.max(0, this.filteredCraftableRecipes.size() - 6);
                  this.craftScrollOffset = Math.max(0, Math.min(maxScroll, this.craftScrollOffset - (int)Math.signum(amount)));
                  this.craftListScrollState
                     .setTarget(
                        this.craftScrollOffset * this.LINE_HEIGHT,
                        Math.max(0, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT - this.CRAFT_LIST_HEIGHT)
                     );
                  this.saveState();
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private String getItemNameFromSlot(int slotId) {
      ItemTarget target = this.getItemTargetFromSlot(slotId);
      return target.editorText();
   }

   private ItemTarget getItemTargetFromSlot(int slotId) {
      AbstractContainerMenu handler = this.getActiveHandler();
      if (handler != null && slotId >= 0 && slotId < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(slotId);
         if (slot != null && !slot.getItem().isEmpty()) {
            int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(MC, slot.index);
            return ItemTarget.capture(slot.getItem(), visibleSlot);
         } else {
            return new ItemTarget();
         }
      } else {
         return new ItemTarget();
      }
   }

   private void pollCraftScan() {
      if (this.isCraftMode() && MC.player != null && MC.level != null) {
         RiptideFabricatorOverlay.ScanResult result = this.pendingScanResult.getAndSet(null);
         if (result != null && result.generation == this.scanGeneration.get() && result.connection == MC.getConnection()) {
            this.applyScanResult(result.options);
         }

         if (!this.scanInFlight) {
            int fingerprint = this.computeScanFingerprint();
            if (!this.hasScannedOnce || fingerprint != this.lastScanFingerprint) {
               this.hasScannedOnce = true;
               this.lastScanFingerprint = fingerprint;
               this.requestCraftScan();
            }
         }
      }
   }

   private void requestCraftScan() {
      long perfStart = RiptidePerf.begin();
      RiptideCraftingHelper.CraftScanSnapshot snapshot = RiptideCraftingHelper.createScanSnapshot(MC);
      RiptidePerf.endSpike("craft.snapshot", perfStart, 5000000L);
      if (snapshot != null) {
         long generation = this.scanGeneration.incrementAndGet();
         this.scanInFlight = true;
         CRAFT_SCAN_EXECUTOR.execute(() -> {
            try {
               List<RiptideCraftingHelper.CraftableRecipeOption> options = RiptideCraftingHelper.computeCraftableRecipes(snapshot);
               this.pendingScanResult.set(new RiptideFabricatorOverlay.ScanResult(generation, snapshot.connection, options));
            } catch (Throwable var8) {
               riptide.RiptideClientAddon.LOG.warn("[Riptide] craft recipe scan failed", var8);
            } finally {
               this.scanInFlight = false;
            }
         });
      }
   }

   private void applyScanResult(List<RiptideCraftingHelper.CraftableRecipeOption> options) {
      this.craftableRecipes.clear();
      this.craftableRecipes.addAll(options);
      this.readyCraftableRecipeCount = 0;

      for (RiptideCraftingHelper.CraftableRecipeOption option : this.craftableRecipes) {
         if (option.craftableNow) {
            this.readyCraftableRecipeCount++;
         }
      }

      this.updateCraftRecipeFilter(this.craftSearchField != null ? this.craftSearchField.getText() : this.savedCraftSearchValue, false);
      this.clearSelectedCraftIfInvalid();
      this.restoreSelectedCraftIfNeeded();
   }

   private int computeScanFingerprint() {
      int hash = System.identityHashCode(MC.getConnection());
      if (MC.player == null) {
         return hash;
      } else {
         AbstractContainerMenu menu = MC.player.containerMenu;
         if (menu != null) {
            hash = hash * 31 + menu.containerId;
            hash = hash * 31 + menu.getStateId();
         }

         Inventory inventory = MC.player.getInventory();

         for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            hash = hash * 31 + (stack != null && !stack.isEmpty() ? System.identityHashCode(stack.getItem()) + stack.getCount() * 31 : 0);
         }

         if (menu instanceof AbstractCraftingMenu crafting) {
            hash = hash * 31 + crafting.getGridWidth();
            hash = hash * 31 + crafting.getGridHeight();

            for (Slot slot : crafting.getInputGridSlots()) {
               ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
               hash = hash * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) + stack.getCount() * 31);
            }
         }

         return hash;
      }
   }

   private void updateCraftRecipeFilter(String query, boolean resetScroll) {
      this.savedCraftSearchValue = query == null ? "" : query;
      this.filteredCraftableRecipes = RiptideCraftingHelper.filterRecipes(this.craftableRecipes, query);
      int maxScroll = Math.max(0, this.filteredCraftableRecipes.size() - 6);
      this.craftScrollOffset = resetScroll ? 0 : Math.max(0, Math.min(maxScroll, this.craftScrollOffset));
   }

   private void clearSelectedCraftIfInvalid() {
      if (this.selectedCraftOption != null) {
         for (RiptideCraftingHelper.CraftableRecipeOption option : this.craftableRecipes) {
            if (!option.recipeKey.isBlank() && option.recipeKey.equalsIgnoreCase(this.selectedCraftOption.recipeKey)
               || option.recipeId >= 0 && option.recipeId == this.selectedCraftOption.recipeId
               || option.syncedRecipeId >= 0 && option.syncedRecipeId == this.selectedCraftOption.syncedRecipeId) {
               this.selectedCraftOption = option;
               this.selectedCraftResult = option.result.copy();
               this.savedCraftSelectedRecipeId = option.recipeId;
               this.savedCraftSelectedRecipeKey = option.recipeKey;
               return;
            }
         }

         this.selectedCraftOption = null;
         this.selectedCraftResult = ItemStack.EMPTY;
         this.savedCraftSelectedRecipeId = -1;
         this.savedCraftSelectedRecipeKey = "";
      }
   }

   private void restoreSelectedCraftIfNeeded() {
      if (this.savedCraftSelectedRecipeKey != null && !this.savedCraftSelectedRecipeKey.isBlank() || this.savedCraftSelectedRecipeId >= 0) {
         if (this.selectedCraftOption != null) {
            if (!this.savedCraftSelectedRecipeKey.isBlank() && this.savedCraftSelectedRecipeKey.equalsIgnoreCase(this.selectedCraftOption.recipeKey)) {
               return;
            }

            if (this.savedCraftSelectedRecipeId >= 0
               && (
                  this.selectedCraftOption.recipeId == this.savedCraftSelectedRecipeId
                     || this.selectedCraftOption.syncedRecipeId == this.savedCraftSelectedRecipeId
               )) {
               return;
            }
         }

         for (RiptideCraftingHelper.CraftableRecipeOption option : this.craftableRecipes) {
            if (!this.savedCraftSelectedRecipeKey.isBlank() && this.savedCraftSelectedRecipeKey.equalsIgnoreCase(option.recipeKey)
               || this.savedCraftSelectedRecipeId >= 0 && option.recipeId == this.savedCraftSelectedRecipeId
               || this.savedCraftSelectedRecipeId >= 0 && option.syncedRecipeId == this.savedCraftSelectedRecipeId) {
               this.selectedCraftOption = option;
               this.selectedCraftResult = option.result.copy();
               this.savedItemTarget = ItemTarget.capture(option.result, -1);
               this.savedItemNameValue = option.label;
               this.savedCraftSelectedRecipeKey = option.recipeKey;
               this.savedCraftSelectedRecipeId = option.recipeId;
               if (this.itemNameField != null) {
                  this.itemNameField.setText(this.savedItemNameValue);
               }

               return;
            }
         }
      }
   }

   private int getCraftListY() {
      return this.getCraftSearchY() + 20;
   }

   private int getCraftListX() {
      return this.panelX + 10;
   }

   private int getCraftListWidth() {
      return this.PANEL_WIDTH - 20;
   }

   private int getCraftPlanContentWidth() {
      return Math.max(40, this.getCraftListWidth() - (this.getCraftPlanScrollbarMetrics().hasScroll() ? 8 : 0));
   }

   private int getCraftListContentWidth() {
      return Math.max(40, this.getCraftListWidth() - (this.getCraftListScrollbarMetrics().hasScroll() ? 8 : 0));
   }

   private CompactScrollbar.Metrics getCraftPlanScrollbarMetrics() {
      int listX = this.getCraftListX();
      int listY = this.getCraftPlanListY();
      int listWidth = this.getCraftListWidth();
      int contentHeight = this.plannedCraftEntries.size() * 13;
      int maxScroll = Math.max(0, contentHeight - this.CRAFT_PLAN_HEIGHT);
      return CompactScrollbar.compute(
         contentHeight, this.CRAFT_PLAN_HEIGHT, listX + listWidth - 5, listY, 3, this.CRAFT_PLAN_HEIGHT, this.craftPlanScrollState.tick(0.0F, maxScroll)
      );
   }

   private CompactScrollbar.Metrics getCraftListScrollbarMetrics() {
      int listX = this.getCraftListX();
      int listY = this.getCraftListY();
      int listWidth = this.getCraftListWidth();
      int contentHeight = this.filteredCraftableRecipes.size() * this.LINE_HEIGHT;
      int maxScroll = Math.max(0, contentHeight - this.CRAFT_LIST_HEIGHT);
      return CompactScrollbar.compute(
         contentHeight, this.CRAFT_LIST_HEIGHT, listX + listWidth - 5, listY, 3, this.CRAFT_LIST_HEIGHT, this.craftListScrollState.tick(0.0F, maxScroll)
      );
   }

   private int toRowScroll(CompactScrollbar.Metrics metrics, int mouseY, int grabOffset, int rowHeight, int maxScrollRows) {
      int pixelScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset);
      return Math.max(0, Math.min(maxScrollRows, Math.round((float)pixelScroll / rowHeight)));
   }

   private void selectCraftRecipe(RiptideCraftingHelper.CraftableRecipeOption option) {
      if (option != null) {
         this.selectedCraftOption = option;
         this.selectedCraftResult = option.result.copy();
         this.savedItemTarget = ItemTarget.capture(option.result, -1);
         this.savedItemNameValue = option.label;
         this.savedCraftSelectedRecipeId = option.recipeId;
         this.savedCraftSelectedRecipeKey = option.recipeKey;
         if (this.itemNameField != null) {
            this.itemNameField.setText(this.savedItemNameValue);
         }

         this.setStatus(
            "Selected craft: "
               + option.label
               + " | "
               + (option.craftSource == RiptideCraftingHelper.CraftSource.TABLE_3X3 ? "Table" : "2x2")
               + " | "
               + RiptideCraftingHelper.getAvailabilityLabel(option),
            option.craftableNow ? ChatFormatting.GREEN : ChatFormatting.RED
         );
         this.saveState();
      }
   }

   private void clearCraftRecipeSelection() {
      this.craftPlanSelectedIndex = -1;
      this.selectedCraftOption = null;
      this.selectedCraftResult = ItemStack.EMPTY;
      this.savedItemTarget = new ItemTarget();
      this.savedCraftSelectedRecipeId = -1;
      this.savedCraftSelectedRecipeKey = "";
   }

   private RiptideCraftingHelper.CraftableRecipeOption resolveCraftOption(CraftAction.CraftEntry entry) {
      return entry == null ? null : RiptideCraftingHelper.findInList(this.craftableRecipes, entry.recipeKey, entry.recipeId);
   }

   private CraftAction.CraftEntry buildConfiguredCraftEntry() {
      if (this.selectedCraftOption != null && !this.selectedCraftResult.isEmpty()) {
         int configuredAmount = this.parseConfiguredCraftAmount();
         return configuredAmount <= 0 ? null : CraftAction.CraftEntry.fromOption(this.selectedCraftOption, configuredAmount, this.craftUseMaxAmount);
      } else {
         return null;
      }
   }

   private CraftAction.CraftEntry getSelectedPlannedEntry() {
      return this.craftPlanSelectedIndex >= 0 && this.craftPlanSelectedIndex < this.plannedCraftEntries.size()
         ? this.plannedCraftEntries.get(this.craftPlanSelectedIndex)
         : null;
   }

   private void selectCraftPlanEntry(int index) {
      if (index >= 0 && index < this.plannedCraftEntries.size()) {
         this.craftPlanSelectedIndex = index;
         CraftAction.CraftEntry entry = this.plannedCraftEntries.get(index);
         if (entry != null) {
            this.craftUseMaxAmount = entry.useMaxAmount;
            this.savedTimesValue = String.valueOf(Math.max(1, entry.amount));
            if (this.timesField != null) {
               this.timesField.setText(this.savedTimesValue);
               this.timesField.setEditable(!this.craftUseMaxAmount);
            }

            RiptideCraftingHelper.CraftableRecipeOption option = this.resolveCraftOption(entry);
            if (option != null) {
               this.selectCraftRecipe(option);
            } else {
               this.savedCraftSelectedRecipeId = entry.recipeId;
               this.savedCraftSelectedRecipeKey = entry.recipeKey;
               this.selectedCraftOption = null;
               this.selectedCraftResult = ItemStack.EMPTY;
            }
         }

         this.saveState();
      } else {
         this.craftPlanSelectedIndex = -1;
         this.selectedCraftOption = null;
         this.selectedCraftResult = ItemStack.EMPTY;
         this.savedCraftSelectedRecipeId = -1;
         this.savedCraftSelectedRecipeKey = "";
         this.saveState();
      }
   }

   private void addOrUpdatePlannedCraftEntry() {
      CraftAction.CraftEntry draft = this.buildConfiguredCraftEntry();
      if (draft == null) {
         this.setStatus("Select a recipe and amount first", ChatFormatting.RED);
      } else {
         int existingIndex = -1;

         for (int i = 0; i < this.plannedCraftEntries.size(); i++) {
            CraftAction.CraftEntry existing = this.plannedCraftEntries.get(i);
            if (existing != null
               && (
                  !draft.recipeKey.isBlank() && draft.recipeKey.equalsIgnoreCase(existing.recipeKey)
                     || draft.recipeId >= 0 && draft.recipeId == existing.recipeId
               )) {
               existingIndex = i;
               break;
            }
         }

         if (this.craftPlanSelectedIndex >= 0 && this.craftPlanSelectedIndex < this.plannedCraftEntries.size()) {
            this.plannedCraftEntries.set(this.craftPlanSelectedIndex, draft);
         } else if (existingIndex >= 0) {
            this.plannedCraftEntries.set(existingIndex, draft);
            this.craftPlanSelectedIndex = existingIndex;
         } else {
            this.plannedCraftEntries.add(draft);
            this.craftPlanSelectedIndex = this.plannedCraftEntries.size() - 1;
         }

         int maxPlanScroll = Math.max(0, this.plannedCraftEntries.size() - 4);
         this.craftPlanScrollOffset = Math.max(0, Math.min(maxPlanScroll, this.craftPlanSelectedIndex));
         this.saveState();
      }
   }

   private void removePlannedCraftEntry(int index) {
      if (index >= 0 && index < this.plannedCraftEntries.size()) {
         this.plannedCraftEntries.remove(index);
         if (this.craftPlanSelectedIndex == index) {
            this.craftPlanSelectedIndex = -1;
         } else if (this.craftPlanSelectedIndex > index) {
            this.craftPlanSelectedIndex--;
         }

         this.craftPlanScrollOffset = Math.max(0, Math.min(Math.max(0, this.plannedCraftEntries.size() - 4), this.craftPlanScrollOffset));
         this.saveState();
      }
   }

   private List<CraftAction.CraftEntry> getActiveCraftEntries() {
      if (!this.plannedCraftEntries.isEmpty()) {
         List<CraftAction.CraftEntry> copies = new ArrayList<>(this.plannedCraftEntries.size());

         for (CraftAction.CraftEntry entry : this.plannedCraftEntries) {
            if (entry != null && entry.hasRecipe()) {
               copies.add(entry.copy());
            }
         }

         return copies;
      } else {
         CraftAction.CraftEntry draft = this.buildConfiguredCraftEntry();
         return draft == null ? List.of() : List.of(draft);
      }
   }

   private CraftAction buildCraftPlanAction() {
      List<CraftAction.CraftEntry> activeEntries = this.getActiveCraftEntries();
      if (activeEntries.isEmpty()) {
         return null;
      } else {
         CraftAction action = new CraftAction();
         action.setEntries(activeEntries);
         return action;
      }
   }

   private Component getCraftPlanRowLabel(CraftAction.CraftEntry entry) {
      if (entry != null && entry.hasRecipe()) {
         RiptideCraftingHelper.CraftableRecipeOption option = this.resolveCraftOption(entry);
         String amount = entry.useMaxAmount ? "Max" : "x" + Math.max(1, entry.amount);
         Component resultName = option != null ? option.result.getHoverName().copy() : Component.literal(entry.resultName);
         return Component.empty()
            .append(resultName)
            .append(Component.literal(" | " + amount + (option != null && option.craftableNow ? " | x" + option.maxCraftsNow : "")));
      } else {
         return Component.literal("(empty)");
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.visible && !this.collapsed) {
         boolean anyFocused = false;

         for (RiptideChatField field : this.textFields) {
            if (field.isFocused()) {
               anyFocused = true;
               break;
            }
         }

         if (anyFocused) {
            if (keyCode == 256) {
               for (RiptideChatField fieldx : this.textFields) {
                  fieldx.setFocused(false);
               }

               return true;
            }

            KeyEvent input = new KeyEvent(keyCode, scanCode, modifiers);

            for (RiptideChatField fieldx : this.textFields) {
               if (fieldx.isFocused()) {
                  fieldx.keyPressed(input);
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.visible && !this.collapsed) {
         CharacterEvent input = new CharacterEvent(chr);

         for (RiptideChatField field : this.textFields) {
            if (field.isFocused() && field.charTyped(input)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.expireTransientStatus();
         int rawMouseX = mouseX;
         int rawMouseY = mouseY;
         if (CompactDropdown.isMenuOpen(this.uiDropdowns)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
         }

         this.uiDropdowns.clear();
         this.updateListHeights();
         if (!this.isDragging) {
            this.PANEL_HEIGHT = this.isCraftMode() ? this.craftModePanelHeight() : this.standardPanelHeight();
         }

         int panelHeight = this.PANEL_HEIGHT;
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, panelHeight, this.visible, this.collapsed);
         this.renderWindowFrame(context, mouseX, mouseY, bounds, "Fabricator", this.collapsed, this.isDragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.isDragging);
         } else {
            try {
               int labelX = this.panelX + 10;
               int fieldX = this.panelX + this.LABEL_WIDTH + 15;
               int fieldWidth = this.getFieldWidth();
               RiptideText.draw(context, MC.font, "Action", RiptideText.Tone.MUTED, labelX, this.getActionRowY() + 3, false);
               if (this.actionDropdown == null) {
                  this.actionDropdown = new CompactDropdown(fieldX, this.getActionRowY(), fieldWidth, 16, this.actionLabels, this.currentActionIndex, idx -> {
                     if (idx >= 0 && idx < this.actions.length && idx != this.currentActionIndex) {
                        this.currentActionIndex = idx;
                        this.currentAction = this.actions[idx];
                        this.clearStatus();
                        this.initWidgets();
                        this.saveState();
                     }
                  });
               }

               this.actionDropdown
                  .setBounds(fieldX, this.getActionRowY(), fieldWidth, 16)
                  .setOptions(this.actionLabels)
                  .setSelectedIndex(this.currentActionIndex);
               this.uiDropdowns.add(this.actionDropdown);

               for (RiptideChatField field : this.textFields) {
                  field.render(context, mouseX, mouseY, delta);
               }

               if (this.showsSlotItemRows()) {
                  RiptideText.draw(context, MC.font, "Slot", RiptideText.Tone.MUTED, labelX, this.getSlotRowY() + 3, false);
                  RiptideText.draw(context, MC.font, "Item", RiptideText.Tone.MUTED, labelX, this.getItemRowY() + 3, false);
               }

               if (this.showsTargetFields()) {
                  if (this.isClickSelectorRelevant() || this.isDropModeRelevant()) {
                     RiptideText.draw(
                        context, MC.font, this.isDropModeRelevant() ? "Drop Mode" : "Click", RiptideText.Tone.MUTED, labelX, this.getOptionRowY() + 3, false
                     );
                     if (this.isClickSelectorRelevant()) {
                        boolean packetMode = this.currentAction.isPacketAction();
                        List<String> options = packetMode ? PACKET_CLICK_LABELS : STANDARD_CLICK_LABELS;
                        int clamped = Math.min(this.currentButtonIndex, options.size() - 1);
                        if (this.clickModeDropdown == null) {
                           this.clickModeDropdown = new CompactDropdown(fieldX, this.getOptionRowY(), fieldWidth, 16, options, clamped, idx -> {
                              if (idx >= 0 && idx < 3) {
                                 this.currentButtonIndex = idx;
                                 this.clearStatus();
                                 this.saveState();
                              }
                           });
                        }

                        this.clickModeDropdown.setBounds(fieldX, this.getOptionRowY(), fieldWidth, 16).setOptions(options).setSelectedIndex(clamped);
                        this.uiDropdowns.add(this.clickModeDropdown);
                     } else {
                        List<String> options = DROP_MODE_LABELS;
                        int sel = this.dropWholeStack ? 1 : 0;
                        if (this.dropModeDropdown == null) {
                           this.dropModeDropdown = new CompactDropdown(fieldX, this.getOptionRowY(), fieldWidth, 16, options, sel, idx -> {
                              this.dropWholeStack = idx == 1;
                              this.clearStatus();
                              this.saveState();
                           });
                        }

                        this.dropModeDropdown.setBounds(fieldX, this.getOptionRowY(), fieldWidth, 16).setOptions(options).setSelectedIndex(sel);
                        this.uiDropdowns.add(this.dropModeDropdown);
                     }
                  }

                  RiptideText.draw(context, MC.font, this.getTimesLabel(), RiptideText.Tone.MUTED, labelX, this.getStandardTimesRowY() + 3, false);
               } else {
                  this.pollCraftScan();
                  int listX = this.getCraftListX();
                  int listWidth = this.getCraftListWidth();
                  CompactListRenderer.drawHeader(context, MC.font, "Craft Plan", labelX, this.getCraftPlanHeaderY());
                  if (!this.plannedCraftEntries.isEmpty()) {
                     CompactListRenderer.drawHeaderAction(context, MC.font, "Clear All", true, listX, listWidth, this.getCraftPlanHeaderY(), mouseX, mouseY);
                  }

                  int planListY = this.getCraftPlanListY();
                  int planContentWidth = this.getCraftPlanContentWidth();
                  CompactListRenderer.drawFrame(context, listX, planListY, listWidth, this.CRAFT_PLAN_HEIGHT, this.craftPlanSelectedIndex >= 0);
                  if (this.plannedCraftEntries.isEmpty()) {
                     CompactListRenderer.drawEmptyState(context, MC.font, "(no plan - pick recipe and Add)", listX, planListY + 2, planContentWidth);
                  } else {
                     int maxPlanScrollPx = Math.max(0, this.plannedCraftEntries.size() * 13 - this.CRAFT_PLAN_HEIGHT);
                     this.craftPlanScrollOffset = Math.max(0, Math.min(this.craftPlanScrollOffset, Math.max(0, this.plannedCraftEntries.size() - 4)));
                     this.craftPlanScrollState.setTarget(this.craftPlanScrollOffset * 13, maxPlanScrollPx);
                     int drawPlanScroll = this.craftPlanScrollState.tick(delta, maxPlanScrollPx);
                     UiScissorStack.global()
                        .push(context, UiBounds.of(listX + 1, planListY + 1, Math.max(0, listWidth - 2), Math.max(0, this.CRAFT_PLAN_HEIGHT - 2)));
                     int startIndex = drawPlanScroll / 13;
                     int rowY = planListY - drawPlanScroll % 13;

                     for (int index = startIndex; index < this.plannedCraftEntries.size() && rowY < planListY + this.CRAFT_PLAN_HEIGHT; rowY += 13) {
                        boolean hovered = mouseX >= listX && mouseX < listX + planContentWidth && mouseY >= rowY && mouseY < rowY + 12;
                        boolean selected = index == this.craftPlanSelectedIndex;
                        CompactListRenderer.drawRow(
                           context,
                           MC.font,
                           this.getCraftPlanRowLabel(this.plannedCraftEntries.get(index)),
                           listX,
                           rowY,
                           planContentWidth,
                           12,
                           hovered,
                           selected,
                           CompactListRenderer.RowTone.NORMAL,
                           true
                        );
                        boolean removeHovered = mouseX >= listX + planContentWidth - 16
                           && mouseX < listX + planContentWidth - 2
                           && mouseY >= rowY
                           && mouseY < rowY + 12;
                        CompactListRenderer.drawDeleteButton(context, listX + planContentWidth - 14, rowY, 12, removeHovered);
                        index++;
                     }

                     UiScissorStack.global().pop(context);
                  }

                  CompactScrollbar.Metrics craftPlanScrollbar = this.getCraftPlanScrollbarMetrics();
                  CompactScrollbar.draw(context, craftPlanScrollbar, craftPlanScrollbar.contains(mouseX, mouseY), this.activeScrollbarDrag == 1);
                  if (this.selectedCraftResult.isEmpty()) {
                     RiptideText.draw(context, MC.font, "Recipe: none selected", RiptideText.Tone.MUTED, listX, this.getCraftSummaryY(), false);
                  } else {
                     Component summaryText = Component.empty()
                        .append(this.selectedCraftResult.getHoverName().copy())
                        .append(
                           Component.literal(
                              " x"
                                 + this.selectedCraftResult.getCount()
                                 + (
                                    this.selectedCraftOption != null && this.selectedCraftOption.craftableNow
                                       ? " | x" + this.selectedCraftOption.maxCraftsNow
                                       : ""
                                 )
                           )
                        );
                     RiptideText.draw(context, MC.font, summaryText.getString(), RiptideColors.textMuted(), listX, this.getCraftSummaryY(), false);
                  }

                  RiptideText.draw(context, MC.font, "Amount", RiptideText.Tone.MUTED, labelX, this.getCraftAmountRowY() + 3, false);
                  this.drawOverlayToggleButton(
                     context,
                     this.getCraftAmountToggleX(),
                     this.getCraftAmountRowY(),
                     this.getCraftAmountToggleWidth(),
                     16,
                     "Use Max",
                     this.craftUseMaxAmount,
                     "fabricator:use-max",
                     mouseX,
                     mouseY
                  );
                  CompactListRenderer.drawHeader(context, MC.font, "Recipes", listX, this.getCraftSearchLabelY());
                  int listY = this.getCraftListY();
                  int recipeContentWidth = this.getCraftListContentWidth();
                  CompactListRenderer.drawFrame(context, listX, listY, listWidth, this.CRAFT_LIST_HEIGHT, this.selectedCraftOption != null);
                  UiScissorStack.global().push(context, UiBounds.of(listX + 1, listY + 1, Math.max(0, listWidth - 2), Math.max(0, this.CRAFT_LIST_HEIGHT - 2)));
                  if (this.filteredCraftableRecipes.isEmpty()) {
                     String emptyText;
                     if (MC.player == null || MC.level == null) {
                        emptyText = "Join a world to load crafting recipes.";
                     } else if (this.craftableRecipes.isEmpty() && this.scanInFlight) {
                        emptyText = "Scanning crafting recipes...";
                     } else {
                        emptyText = "No crafting recipes match this search.";
                     }

                     CompactListRenderer.drawEmptyState(context, MC.font, emptyText, listX, listY + 4, recipeContentWidth);
                  } else {
                     int maxRecipeScrollPx = Math.max(0, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT - this.CRAFT_LIST_HEIGHT);
                     this.craftScrollOffset = Math.max(0, Math.min(this.craftScrollOffset, Math.max(0, this.filteredCraftableRecipes.size() - 6)));
                     this.craftListScrollState.setTarget(this.craftScrollOffset * this.LINE_HEIGHT, maxRecipeScrollPx);
                     int drawRecipeScroll = this.craftListScrollState.tick(delta, maxRecipeScrollPx);
                     int startIndex = drawRecipeScroll / this.LINE_HEIGHT;
                     int rowY = listY - drawRecipeScroll % this.LINE_HEIGHT;

                     for (int index = startIndex;
                        index < this.filteredCraftableRecipes.size() && rowY < listY + this.CRAFT_LIST_HEIGHT;
                        rowY += this.LINE_HEIGHT
                     ) {
                        RiptideCraftingHelper.CraftableRecipeOption option = this.filteredCraftableRecipes.get(index);
                        boolean hovered = mouseX >= listX && mouseX <= listX + recipeContentWidth && mouseY >= rowY && mouseY < rowY + this.LINE_HEIGHT;
                        boolean selected = this.selectedCraftOption != null && option.recipeKey.equalsIgnoreCase(this.selectedCraftOption.recipeKey);
                        Component richLabel = Component.empty()
                           .append(option.result.getHoverName().copy())
                           .append(Component.literal(" x" + option.result.getCount() + " | " + RiptideCraftingHelper.getAvailabilityLabel(option)));
                        CompactListRenderer.drawRow(
                           context,
                           MC.font,
                           richLabel,
                           listX + 2,
                           rowY,
                           recipeContentWidth - 2,
                           this.LINE_HEIGHT,
                           hovered,
                           selected,
                           option.craftableNow ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.MISSING,
                           true
                        );
                        CompactListRenderer.drawDivider(context, listX + 2, rowY + this.LINE_HEIGHT - 1, recipeContentWidth - 2);
                        index++;
                     }
                  }

                  UiScissorStack.global().pop(context);
                  CompactScrollbar.Metrics craftListScrollbar = this.getCraftListScrollbarMetrics();
                  CompactScrollbar.draw(context, craftListScrollbar, craftListScrollbar.contains(mouseX, mouseY), this.activeScrollbarDrag == 2);
                  String recipeCountLine = RiptideText.trimToWidth(
                     MC.font, this.readyCraftableRecipeCount + " ready / " + this.craftableRecipes.size() + " total", listWidth, RiptideText.Tone.MUTED
                  );
                  RiptideText.draw(context, MC.font, recipeCountLine, RiptideText.Tone.MUTED, listX, listY + this.CRAFT_LIST_HEIGHT + 2, false);
               }

               int btnY = this.getBottomButtonY();
               int btnArea = this.PANEL_WIDTH - 20;
               int gap = 2;
               int bw = (btnArea - gap * 2) / 3;
               int bx = this.panelX + 10;
               String sendLabel = this.isCraftMode() ? (this.craftExecutionInProgress ? "Crafting..." : "Craft") : "Send";
               this.drawOverlayButton(context, bx, btnY, bw, this.BUTTON_HEIGHT, sendLabel, CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);
               bx += bw + gap;
               this.drawOverlayButton(context, bx, btnY, bw, this.BUTTON_HEIGHT, "Queue", CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);
               bx += bw + gap;
               this.drawOverlayButton(context, bx, btnY, bw, this.BUTTON_HEIGHT, "Macro", CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);
               int infoWidth = this.PANEL_WIDTH - 20;
               int statusY = this.getStatusY();
               if (statusY + 9 < btnY) {
                  if (!this.statusMessage.isEmpty()) {
                     int color = chatFormattingColor(this.statusColor);
                     UiText.drawFitted(context, MC.font, this.statusMessage, THEME.fontFor(UiTone.BODY), color, this.panelX + 10, statusY, infoWidth, true);
                  } else {
                     UiText.drawFitted(
                        context,
                        MC.font,
                        this.getTargetSummary(),
                        THEME.fontFor(UiTone.MUTED),
                        THEME.color(UiTone.MUTED),
                        this.panelX + 10,
                        statusY,
                        infoWidth,
                        false
                     );
                  }
               }

               List<String> actionLines = this.getActionDescriptionLines();

               for (int i = 0; i < actionLines.size(); i++) {
                  int lineY = this.getActionInfoY() + i * 10;
                  if (lineY + 9 >= btnY) {
                     break;
                  }

                  RiptideText.draw(context, MC.font, actionLines.get(i), RiptideText.Tone.MUTED, this.panelX + 10, lineY, false);
               }

               if (!this.uiDropdowns.isEmpty()) {
                  CompactDropdown.renderButtons(context, MC.font, this.uiDropdowns, rawMouseX, rawMouseY);
               }
            } finally {
               this.endWindowBodyClip(context, clipBody);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.isDragging);
            }

            if (!this.uiDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.uiDropdowns)) {
               context.nextStratum();
               CompactDropdown.renderOpenMenu(context, MC.font, this.uiDropdowns, rawMouseX, rawMouseY);
            }
         }
      }
   }

   private static int chatFormattingColor(ChatFormatting formatting) {
      if (formatting == null) {
         return -1;
      } else {
         return switch (formatting) {
            case BLACK -> -16777216;
            case DARK_BLUE -> -16777046;
            case DARK_GREEN -> -16733696;
            case DARK_AQUA -> -16733526;
            case DARK_RED -> -5636096;
            case DARK_PURPLE -> -5635926;
            case GOLD -> -22016;
            case GRAY -> -5592406;
            case DARK_GRAY -> -11184811;
            case BLUE -> -11184641;
            case GREEN -> -11141291;
            case AQUA -> -11141121;
            case RED -> -43691;
            case LIGHT_PURPLE -> -43521;
            case YELLOW -> -171;
            case WHITE -> -1;
            default -> -1;
         };
      }
   }

   private void cycleAction() {
      this.currentActionIndex = (this.currentActionIndex + 1) % this.actions.length;
      this.currentAction = this.actions[this.currentActionIndex];
      this.clearStatus();
      this.initWidgets();
      this.saveState();
   }

   private void cycleActionBackwards() {
      this.currentActionIndex = (this.currentActionIndex - 1 + this.actions.length) % this.actions.length;
      this.currentAction = this.actions[this.currentActionIndex];
      this.clearStatus();
      this.initWidgets();
      this.saveState();
   }

   private void clearWidgets() {
      this.textFields.clear();
      this.craftSearchField = null;
   }

   public void onSlotClick(Slot slot, int button) {
      if (slot != null) {
         if (!this.isCraftMode()) {
            int stableSlotId = RiptideInventoryHelper.toUserVisibleSlot(MC, slot.index);
            ItemTarget clickedTarget = this.getItemTargetFromSlot(slot.index);
            String itemName = clickedTarget.editorText();
            if (this.currentAction.isPacketAction()) {
               AbstractContainerMenu handler = this.getActiveHandler();
               if (handler == null) {
                  this.setStatus("No container is open to capture", ChatFormatting.RED);
               } else {
                  String screenTitle = this.parentScreen != null ? this.parentScreen.getTitle().getString() : "";
                  String menuClass = handler.getClass().getName();
                  this.packetClickTarget = new RiptidePacketClick.Target(
                     handler.containerId,
                     handler.getStateId(),
                     slot.index,
                     stableSlotId,
                     screenTitle,
                     menuClass,
                     itemName == null ? "" : itemName,
                     this.getCurrentPacketMode(),
                     System.currentTimeMillis()
                  );
                  this.setStatus("Captured Packet " + this.packetClickTarget.summary(), ChatFormatting.GREEN);
                  this.saveState();
               }
            } else {
               this.selectedSlotId = stableSlotId;
               this.savedSlotValue = String.valueOf(stableSlotId);
               if (this.slotField != null) {
                  this.slotField.setText(this.savedSlotValue);
               }

               if (!itemName.isEmpty()) {
                  this.savedItemTarget = clickedTarget;
                  this.savedItemNameValue = itemName;
                  if (this.itemNameField != null) {
                     this.itemNameField.setText(itemName);
                  }

                  this.setStatus("Selected slot " + stableSlotId + ": " + itemName, ChatFormatting.GREEN);
               } else {
                  this.savedItemTarget = new ItemTarget();
                  this.savedItemNameValue = "";
                  if (this.itemNameField != null) {
                     this.itemNameField.setText("");
                  }

                  this.setStatus("Selected slot " + stableSlotId + " (Empty)", ChatFormatting.YELLOW);
               }

               this.saveState();
            }
         }
      }
   }

   private void sendToMacro(RiptideMacroEditorOverlay macroEditor) {
      try {
         if (this.isCraftMode()) {
            CraftAction craftAction = this.buildCraftPlanAction();
            if (craftAction != null && craftAction.hasEntries()) {
               macroEditor.addAction(craftAction);
               return;
            }

            this.setStatus("Select a recipe or add a craft entry first!", ChatFormatting.RED);
            return;
         }

         Integer enteredSlot = this.parseEnteredSlot();
         String itemName = this.getEnteredItemName();
         int repeats = this.resolveRepeatCount(-1);
         if (repeats <= 0) {
            return;
         }

         if (enteredSlot == null && itemName.isEmpty() && !this.currentAction.isPacketAction()) {
            this.setStatus("Pick a slot or enter an item name first!", ChatFormatting.RED);
            return;
         }

         if (this.currentAction.isPacketAction()) {
            RiptidePacketClick.Target target = this.getEffectivePacketClickTarget();
            if (target == null) {
               this.setStatus("Right-click a GUI slot to capture a Packet target first!", ChatFormatting.RED);
               return;
            }

            PacketClickAction action = new PacketClickAction(target, repeats, false);
            macroEditor.addAction(action);
            this.setStatus("Added Packet Click action to macro!", ChatFormatting.GREEN);
            return;
         }

         AbstractContainerMenu handler = this.getActiveHandler();
         int resolvedSlot = enteredSlot == null ? -1 : RiptideInventoryHelper.resolveConfiguredHandlerSlot(MC, enteredSlot);
         if (enteredSlot != null && !this.isValidHandlerSlot(handler, resolvedSlot)) {
            this.setStatus("Slot " + enteredSlot + " is not available in this screen!", ChatFormatting.RED);
            return;
         }

         if (this.currentAction == RiptideFabricatorOverlay.FabricatorAction.DROP) {
            DropAction dropAction = new DropAction();
            dropAction.useHandlerSlots = true;
            dropAction.mode = this.dropWholeStack ? DropAction.DropMode.ALL : DropAction.DropMode.TIMES;
            dropAction.dropCount = this.dropWholeStack ? 0 : repeats;
            String entry = enteredSlot != null ? "#" + enteredSlot + (!itemName.isEmpty() ? "|" + itemName : "") : itemName;
            if (!entry.isBlank()) {
               ItemTarget target = this.buildEnteredItemTarget(enteredSlot, itemName);
               entry = target.toLegacyEntry();
               dropAction.itemTargets.add(target);
               dropAction.itemNames.add(entry);
               dropAction.itemCounts.add(this.dropWholeStack ? 0 : repeats);
            }

            macroEditor.addAction(dropAction);
            this.setStatus("Added Drop action to macro!", ChatFormatting.GREEN);
            return;
         }

         ItemAction itemAction = new ItemAction();
         itemAction.actionIndex = this.currentAction.toPacketAction(this.dropWholeStack).ordinal();
         itemAction.button = this.getEffectiveButton();
         itemAction.times = repeats;
         if (enteredSlot != null) {
            itemAction.useSlot = true;
            itemAction.targetSlot = enteredSlot;
         }

         if (!itemName.isEmpty()) {
            ItemTarget target = this.buildEnteredItemTarget(enteredSlot, itemName);
            itemAction.itemTargets.add(target);
            itemAction.itemNames.add(target.toLegacyEntry());
            itemAction.itemTimes.add(repeats);
            itemAction.itemActionIdx.add(itemAction.actionIndex);
            itemAction.itemButtons.add(itemAction.button);
         }

         macroEditor.addAction(itemAction);
         this.setStatus("Added " + this.currentAction.displayName + " action to macro!", ChatFormatting.GREEN);
      } catch (NumberFormatException var10) {
         this.setStatus("Invalid value", ChatFormatting.RED);
      }
   }

   private ItemTarget buildEnteredItemTarget(Integer enteredSlot, String itemName) {
      String safeName = itemName == null ? "" : itemName.trim();
      ItemTarget target = this.savedItemTarget != null && safeName.equals(this.savedItemTarget.editorText())
         ? this.savedItemTarget.copy()
         : ItemTarget.fromLegacyEntry(safeName);
      if (enteredSlot != null) {
         target.slot = enteredSlot;
      }

      return target;
   }

   private RiptidePacketClick.Target getEffectivePacketClickTarget() {
      return this.packetClickTarget == null ? null : this.packetClickTarget.withMode(this.getCurrentPacketMode());
   }

   private Packet<?> buildPacketClickPacket() {
      RiptidePacketClick.Target target = this.getEffectivePacketClickTarget();
      return target == null ? null : target.buildPacket();
   }

   private Packet<?> buildFabricatedPacket(int slot) {
      if (this.currentAction.isPacketAction()) {
         return this.buildPacketClickPacket();
      } else if (MC.player == null) {
         return null;
      } else {
         AbstractContainerMenu handler = MC.player.containerMenu;
         if (handler == null) {
            return null;
         } else {
            ContainerInput actionType = this.currentAction.toPacketAction(this.dropWholeStack).toContainerInput();
            return new ServerboundContainerClickPacket(
               handler.containerId, handler.getStateId(), (short)slot, (byte)this.getEffectiveButton(), actionType, new Int2ObjectArrayMap(), HashedStack.EMPTY
            );
         }
      }
   }

   private void send(boolean queue) {
      if (this.isCraftMode()) {
         this.sendCraft(queue);
      } else if (this.currentAction.isPacketAction()) {
         this.sendPacketClick(queue);
      } else {
         try {
            int slot = this.resolveTargetSlotForBuild();
            if (slot < 0) {
               return;
            }

            int repeats = this.resolveRepeatCount(slot);
            if (repeats <= 0) {
               return;
            }

            ClientPacketListener handler = MC.getConnection();
            if (handler == null) {
               return;
            }

            if (this.currentAction == RiptideFabricatorOverlay.FabricatorAction.DROP && !queue) {
               int queuedCount = RiptideSharedState.get().flushDelayedPackets(handler);
               int sent = RiptideDropHelper.dropFromHandlerSlot(MC, slot, this.dropWholeStack ? 0 : repeats);
               if (sent <= 0) {
                  this.setStatus("Nothing to drop", ChatFormatting.YELLOW);
               } else if (queuedCount > 0) {
                  this.setStatus("Sent " + queuedCount + " queued + drop action", ChatFormatting.GREEN);
               } else {
                  this.setStatus(this.dropWholeStack ? "Dropped stack" : "Dropped " + repeats + " item(s)", ChatFormatting.GREEN);
               }

               return;
            }

            if (!queue) {
               RiptideSharedState.get().flushDelayedPackets(handler);
               ContainerInput actionType = this.currentAction.toPacketAction(this.dropWholeStack).toContainerInput();
               int effectiveButton = this.getEffectiveButton();

               for (int i = 0; i < repeats; i++) {
                  RiptideInventoryClickHelper.click(MC, slot, effectiveButton, actionType);
               }

               return;
            }

            this.sendFabricatedPackets(slot, repeats, queue, handler);
         } catch (NumberFormatException var8) {
            this.setStatus("Invalid number format", ChatFormatting.RED);
         }
      }
   }

   private void sendPacketClick(boolean queue) {
      try {
         RiptidePacketClick.Target target = this.getEffectivePacketClickTarget();
         if (target == null) {
            this.setStatus("Right-click a GUI slot to capture a Packet target first", ChatFormatting.RED);
            return;
         }

         int repeats = this.resolveRepeatCount(-1);
         if (repeats <= 0) {
            return;
         }

         ClientPacketListener handler = MC.getConnection();
         if (handler == null) {
            this.setStatus("No network connection", ChatFormatting.RED);
            return;
         }

         List<Packet<?>> packets = new ArrayList<>(repeats);

         for (int i = 0; i < repeats; i++) {
            packets.add(target.buildPacket());
         }

         if (queue) {
            for (Packet<?> packet : packets) {
               RiptideSharedState.get().enqueueExactPacket(packet);
            }

            this.setStatus("Queued " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
            return;
         }

         int queuedCount = RiptideSharedState.get().flushDelayedPackets(handler);

         for (Packet<?> packet : packets) {
            RiptideSharedState.get().sendPacketBypassDelay(handler, packet);
         }

         if (queuedCount > 0) {
            this.setStatus("Sent " + queuedCount + " queued + " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
         } else {
            this.setStatus("Sent " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
         }
      } catch (NumberFormatException var9) {
         this.setStatus("Invalid number format", ChatFormatting.RED);
      }
   }

   private void sendCraft(boolean queue) {
      List<CraftAction.CraftEntry> entries = this.getActiveCraftEntries();
      if (entries.isEmpty()) {
         this.setStatus("Select a recipe or add a craft entry first", ChatFormatting.RED);
      } else if (!queue) {
         if (this.craftExecutionInProgress) {
            this.setStatus("A craft is already running", ChatFormatting.YELLOW);
         } else {
            this.craftExecutionInProgress = true;
            Thread craftThread = new Thread(() -> {
               RiptideCraftingHelper.CraftExecutionResult result = null;

               for (CraftAction.CraftEntry entryx : entries) {
                  RiptideCraftingHelper.CraftableRecipeOption optionx = RiptideCraftingHelper.findCraftableRecipe(MC, entryx.recipeKey);
                  if (optionx == null) {
                     optionx = RiptideCraftingHelper.findCraftableRecipe(MC, entryx.recipeId);
                  }

                  if (optionx == null) {
                     result = RiptideCraftingHelper.CraftExecutionResult.failure("Recipe not found: " + entryx.resultName);
                     break;
                  }

                  int desiredAmountx = RiptideCraftingHelper.getEffectiveRequestedOutput(optionx, entryx.amount, entryx.useMaxAmount);
                  if (desiredAmountx <= 0) {
                     result = RiptideCraftingHelper.CraftExecutionResult.failure("No space or materials for " + entryx.resultName + ".");
                     break;
                  }

                  result = RiptideCraftingHelper.executeCraftImmediately(MC, entryx.recipeKey, entryx.recipeId, desiredAmountx);
                  if (!result.success) {
                     break;
                  }
               }

               this.craftExecutionInProgress = false;
               RiptideCraftingHelper.CraftExecutionResult finalResult = result;
               if (finalResult != null && !finalResult.success) {
                  MC.execute(() -> {
                     this.setStatus(finalResult.message, ChatFormatting.RED);
                     RiptideClientMessaging.sendPrefixed("§c" + finalResult.message);
                  });
               }
            }, "Riptide-Fabricator-Craft");
            craftThread.setDaemon(true);
            craftThread.start();
         }
      } else {
         for (CraftAction.CraftEntry entry : entries) {
            RiptideCraftingHelper.CraftableRecipeOption option = RiptideCraftingHelper.findCraftableRecipe(MC, entry.recipeKey);
            if (option == null) {
               option = RiptideCraftingHelper.findCraftableRecipe(MC, entry.recipeId);
            }

            if (option == null) {
               String msg = "Recipe not found: " + entry.resultName;
               this.setStatus(msg, ChatFormatting.RED);
               RiptideClientMessaging.sendPrefixed("§c" + msg);
               return;
            }

            int desiredAmount = RiptideCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
            if (desiredAmount <= 0) {
               String msg = "No space or materials for " + entry.resultName;
               this.setStatus(msg, ChatFormatting.RED);
               RiptideClientMessaging.sendPrefixed("§c" + msg);
               return;
            }

            List<Packet<?>> packets = RiptideCraftingHelper.buildCraftSequence(MC, entry.recipeKey, entry.recipeId, desiredAmount);
            if (packets.isEmpty()) {
               String msg = option.hasSyncedRecipe()
                  ? "Failed to build craft queue for " + entry.resultName
                  : "Manual queue cannot be built safely for " + entry.resultName + "; use Craft instead.";
               this.setStatus(msg, ChatFormatting.RED);
               RiptideClientMessaging.sendPrefixed("§c" + msg);
               return;
            }

            for (Packet<?> packet : packets) {
               RiptideSharedState.get().enqueuePacket(packet);
            }
         }
      }
   }

   private void sendFabricatedPackets(int slot, int repeats, boolean queue, ClientPacketListener handler) {
      List<Packet<?>> packets = this.buildFabricatedSequence(slot, repeats);
      if (packets.isEmpty()) {
         this.setStatus("Failed to build packet", ChatFormatting.RED);
      } else if (queue) {
         for (Packet<?> packet : packets) {
            RiptideSharedState.get().enqueuePacket(packet);
         }

         this.setStatus("Queued " + packets.size() + " packet(s)", ChatFormatting.GREEN);
      } else {
         int queuedCount = RiptideSharedState.get().flushDelayedPackets(handler);

         for (Packet<?> packet : packets) {
            RiptidePacketSender.send(packet);
         }

         if (queuedCount > 0) {
            this.setStatus("Sent " + queuedCount + " queued + " + packets.size() + " new packet(s)", ChatFormatting.GREEN);
         } else {
            this.setStatus("Sent " + packets.size() + " packet(s)", ChatFormatting.GREEN);
         }
      }
   }

   private void setStatus(String message, ChatFormatting color) {
      this.statusMessage = message;
      this.statusColor = color;
      this.statusExpiresAtMs = 0L;
   }

   private void clearStatus() {
      this.statusMessage = "";
      this.statusColor = ChatFormatting.GRAY;
      this.statusExpiresAtMs = 0L;
   }

   private void expireTransientStatus() {
      if (this.statusExpiresAtMs > 0L) {
         if (System.currentTimeMillis() >= this.statusExpiresAtMs) {
            this.clearStatus();
         }
      }
   }

   private String getTimesLabel() {
      if (this.isCraftMode()) {
         return "Amount";
      } else {
         return this.isDropModeRelevant() && !this.dropWholeStack ? "Items" : "Times";
      }
   }

   private String getTargetSummary() {
      if (this.isCraftMode()) {
         String planInfo = this.plannedCraftEntries.isEmpty()
            ? "Plan: none"
            : "Plan: " + this.plannedCraftEntries.size() + " entr" + (this.plannedCraftEntries.size() == 1 ? "y" : "ies");
         if (this.selectedCraftResult.isEmpty()) {
            return planInfo + " | Recipe: none selected";
         } else {
            String source = this.selectedCraftOption != null && this.selectedCraftOption.craftSource == RiptideCraftingHelper.CraftSource.TABLE_3X3
               ? "Table"
               : "2x2";
            String availability = this.selectedCraftOption != null ? RiptideCraftingHelper.getAvailabilityLabel(this.selectedCraftOption) : "Unavailable";
            String amountMode = this.craftUseMaxAmount ? "Max" : "x" + Math.max(1, this.parseConfiguredCraftAmount());
            return planInfo + " | Recipe: " + this.selectedCraftResult.getHoverName().getString() + " | " + source + " | " + availability + " | " + amountMode;
         }
      } else if (this.currentAction.isPacketAction()) {
         RiptidePacketClick.Target target = this.getEffectivePacketClickTarget();
         return target == null ? "Packet: right-click a GUI slot to capture" : "Packet: " + target.summary();
      } else {
         Integer slot = this.parseEnteredSlot();
         String itemName = this.getEnteredItemName();
         if (slot != null && !itemName.isEmpty()) {
            return "Target: '" + itemName + "' in exact slot " + slot;
         } else if (slot != null) {
            return "Target: exact slot " + slot;
         } else {
            return !itemName.isEmpty() ? "Target: best matching '" + itemName + "'" : "Target: choose a slot or enter an item name";
         }
      }
   }

   private AbstractContainerMenu getActiveHandler() {
      if (MC.player != null && MC.player.containerMenu != null) {
         return MC.player.containerMenu;
      } else {
         return this.parentScreen != null ? ((AbstractContainerScreenAccessor)this.parentScreen).riptide$getMenu() : null;
      }
   }

   private Integer parseEnteredSlot() {
      if (this.slotField == null) {
         return null;
      } else {
         String text = this.slotField.getText().trim();
         if (text.isEmpty()) {
            return null;
         } else {
            try {
               return Integer.parseInt(text);
            } catch (NumberFormatException var3) {
               return null;
            }
         }
      }
   }

   private String getEnteredItemName() {
      return this.itemNameField == null ? "" : this.itemNameField.getText().trim();
   }

   private boolean isValidHandlerSlot(AbstractContainerMenu handler, int slotId) {
      return handler != null && slotId >= 0 && slotId < handler.slots.size();
   }

   private boolean stackMatchesQuery(ItemStack stack, String query) {
      return stack != null && !stack.isEmpty() && query != null && !query.isBlank() ? ItemTarget.fromLegacyEntry(query).score(stack, -1) >= 0 : false;
   }

   private boolean slotMatchesItemQuery(AbstractContainerMenu handler, int slotId, String itemQuery) {
      if (!this.isValidHandlerSlot(handler, slotId)) {
         return false;
      } else {
         Slot slot = (Slot)handler.slots.get(slotId);
         return slot != null && this.stackMatchesQuery(slot.getItem(), itemQuery);
      }
   }

   private int resolveTargetSlotForBuild() {
      AbstractContainerMenu handler = this.getActiveHandler();
      if (handler == null) {
         this.setStatus("No screen is open right now", ChatFormatting.RED);
         return -1;
      } else {
         Integer enteredSlot = this.parseEnteredSlot();
         String itemName = this.getEnteredItemName();
         int resolvedSlot = enteredSlot == null ? -1 : RiptideInventoryHelper.resolveConfiguredHandlerSlot(MC, enteredSlot);
         if (enteredSlot != null && !this.isValidHandlerSlot(handler, resolvedSlot)) {
            this.setStatus("Slot " + enteredSlot + " is not available in this screen", ChatFormatting.RED);
            return -1;
         } else if (enteredSlot != null && !itemName.isEmpty()) {
            if (this.slotMatchesItemQuery(handler, resolvedSlot, itemName)) {
               return resolvedSlot;
            } else {
               this.setStatus("Slot " + enteredSlot + " does not contain '" + itemName + "'", ChatFormatting.RED);
               return -1;
            }
         } else if (enteredSlot != null) {
            return resolvedSlot;
         } else if (!itemName.isEmpty()) {
            Integer foundSlot = this.findSlotByItemName(itemName);
            if (foundSlot == null) {
               this.setStatus("No item matching '" + itemName + "' found", ChatFormatting.RED);
               return -1;
            } else {
               return foundSlot;
            }
         } else {
            this.setStatus("Pick a slot or enter an item name first", ChatFormatting.RED);
            return -1;
         }
      }
   }

   private int resolveRepeatCount(int slot) {
      int value;
      try {
         value = Integer.parseInt(this.timesField.getText().trim());
      } catch (NumberFormatException var6) {
         this.setStatus(this.currentAction.isCraftAction() ? "Invalid amount" : "Invalid repeat count", ChatFormatting.RED);
         return -1;
      }

      if (value <= 0) {
         this.setStatus(this.currentAction.isCraftAction() ? "Amount must be at least 1" : "Repeat count must be at least 1", ChatFormatting.RED);
         return -1;
      } else if (!this.currentAction.isCraftAction()) {
         return value;
      } else if (this.selectedCraftOption != null && !this.selectedCraftResult.isEmpty()) {
         if (MC.player != null && MC.player.containerMenu instanceof CraftingMenu craftingHandler) {
            int var7 = Math.max(1, this.selectedCraftResult.getCount());
            return Math.max(1, (int)Math.ceil((double)value / var7));
         } else {
            this.setStatus("Open a crafting screen first", ChatFormatting.RED);
            return -1;
         }
      } else {
         this.setStatus("Select a recipe first", ChatFormatting.RED);
         return -1;
      }
   }

   private int resolveCraftAmount() {
      if (this.selectedCraftOption == null || this.selectedCraftResult.isEmpty()) {
         this.setStatus("Select a recipe first", ChatFormatting.RED);
         return -1;
      } else if (this.craftUseMaxAmount) {
         int maxAmount = this.selectedCraftOption.maxOutputNow;
         if (maxAmount <= 0) {
            this.setStatus(this.selectedCraftOption.hasMaterialsNow ? "No room to store crafted items" : "Missing materials", ChatFormatting.RED);
            return -1;
         } else {
            return maxAmount;
         }
      } else {
         int value = this.parseConfiguredCraftAmount();
         if (value <= 0) {
            this.setStatus("Invalid craft amount", ChatFormatting.RED);
            return -1;
         } else {
            return value;
         }
      }
   }

   private int parseConfiguredCraftAmount() {
      if (this.timesField == null) {
         return 1;
      } else {
         try {
            int value = Integer.parseInt(this.timesField.getText().trim());
            return Math.max(1, value);
         } catch (NumberFormatException var2) {
            return -1;
         }
      }
   }

   private List<Packet<?>> buildFabricatedSequence(int slot, int repeats) {
      List<Packet<?>> packets = new ArrayList<>();

      for (int i = 0; i < repeats; i++) {
         Packet<?> built = this.buildFabricatedPacket(slot);
         if (built == null) {
            return List.of();
         }

         packets.add(this.normalizeFabricatorPacket(built));
      }

      return packets;
   }

   private Packet<?> normalizeFabricatorPacket(Packet<?> packet) {
      if (this.currentAction.isPacketAction()) {
         return packet;
      } else {
         return packet instanceof ServerboundContainerClickPacket clickSlotPacket ? PacketRegenerator.regenerate(clickSlotPacket) : packet;
      }
   }

   private Integer findSlotByItemName(String searchName) {
      if (searchName != null && !searchName.trim().isEmpty()) {
         AbstractContainerMenu handler = this.getActiveHandler();
         if (handler == null) {
            return null;
         } else {
            ItemTarget target = ItemTarget.fromLegacyEntry(searchName);
            Integer bestMatch = null;
            int bestScore = -1;

            for (Slot slot : handler.slots) {
               if (slot != null && !slot.getItem().isEmpty()) {
                  int score = target.score(slot.getItem(), -1);
                  if (score > bestScore) {
                     bestScore = score;
                     bestMatch = slot.index;
                  }
               }
            }

            return bestScore >= 0 ? bestMatch : null;
         }
      } else {
         return null;
      }
   }

   private void applyPresetMetrics() {
      this.DEFAULT_PANEL_WIDTH = 236;
      this.LINE_HEIGHT = 14;
      this.CRAFT_LIST_HEIGHT = 84;
      this.CRAFT_PLAN_HEIGHT = 52;
      this.FIELD_WIDTH = 100;
      this.BUTTON_HEIGHT = 20;
      this.LABEL_WIDTH = 64;
   }

   private int standardPanelHeight() {
      return Math.max(140, this.requiredPanelHeightForInfoRows(this.getStandardTimesRowY() + 22));
   }

   private int craftModePanelHeight() {
      return Math.max(200, this.requiredPanelHeightForInfoRows(this.getCraftListY() + this.CRAFT_LIST_HEIGHT + 14));
   }

   private void updateListHeights() {
      int listHeight = Math.max(28, Math.min(84, this.filteredCraftableRecipes.size() * this.LINE_HEIGHT));
      int planHeight = Math.max(26, Math.min(52, this.plannedCraftEntries.size() * 13));
      if (listHeight != this.CRAFT_LIST_HEIGHT || planHeight != this.CRAFT_PLAN_HEIGHT) {
         this.CRAFT_LIST_HEIGHT = listHeight;
         this.CRAFT_PLAN_HEIGHT = planHeight;
         if (this.isCraftMode()) {
            if (this.timesField != null) {
               this.timesField.setY(this.getCraftAmountRowY());
            }

            if (this.craftSearchField != null) {
               this.craftSearchField.setY(this.getCraftSearchY());
            }
         }
      }
   }

   private static enum FabricatorAction {
      CLICK("Click", "Use Left or Right on the resolved slot.") {
         @Override
         boolean usesClickSelector() {
            return true;
         }

         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return RiptideDropAction.PICKUP;
         }
      },
      QUICK_MOVE("Quick Move", "Shift-click the resolved slot.") {
         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return RiptideDropAction.QUICK_MOVE;
         }
      },
      PICKUP_ALL("Pick Up All", "Double-click the resolved slot. Usually needs you to hold a matching stack first.") {
         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return RiptideDropAction.PICKUP_ALL;
         }
      },
      CRAFT_RESULT("Craft", "Choose a craftable item, set the total amount, then send, queue, or add it to a macro.") {
         @Override
         boolean isCraftAction() {
            return true;
         }

         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return RiptideDropAction.QUICK_MOVE;
         }
      },
      DROP("Drop", "Drop from the resolved slot. If slot and item are both set, both must match.") {
         @Override
         boolean usesDropToggle() {
            return true;
         }

         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return dropWholeStack ? RiptideDropAction.DROP_STACK : RiptideDropAction.DROP_ITEM;
         }

         @Override
         String getDescription(boolean dropWholeStack) {
            return dropWholeStack ? "Drops the whole stack from the selected slot." : "Drops one item at a time from the selected slot.";
         }
      },
      PACKET("Packet", "Replay a captured slot click with the captured container id and state.") {
         @Override
         boolean usesClickSelector() {
            return true;
         }

         @Override
         boolean isPacketAction() {
            return true;
         }

         @Override
         RiptideDropAction toPacketAction(boolean dropWholeStack) {
            return RiptideDropAction.PICKUP;
         }
      };

      final String displayName;
      final String description;

      private FabricatorAction(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      boolean usesClickSelector() {
         return false;
      }

      boolean usesDropToggle() {
         return false;
      }

      boolean isCraftAction() {
         return false;
      }

      boolean isPacketAction() {
         return false;
      }

      String getDescription(boolean dropWholeStack) {
         return this.description;
      }

      abstract RiptideDropAction toPacketAction(boolean var1);
   }

   private static final class ScanResult {
      final long generation;
      final Object connection;
      final List<RiptideCraftingHelper.CraftableRecipeOption> options;

      ScanResult(long generation, Object connection, List<RiptideCraftingHelper.CraftableRecipeOption> options) {
         this.generation = generation;
         this.connection = connection;
         this.options = options;
      }
   }
}
