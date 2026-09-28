package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.Kind;
import riptide.api.module.Setting;
import riptide.gui.screen.RiptideAdminItemOptionScreen;
import riptide.gui.screen.RiptideAdminItemStructuredScreen;
import riptide.gui.screen.RiptideForceOpPreviewScreen;
import riptide.gui.screen.RiptideItemPickerScreen;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactFieldFactory;
import riptide.gui.vanillaui.components.CompactForm;
import riptide.gui.vanillaui.components.CompactToolbar;
import riptide.gui.vanillaui.components.Dropdown;
import riptide.gui.vanillaui.components.Scrollbar;
import riptide.gui.vanillaui.components.SectionPanel;
import riptide.gui.vanillaui.components.Slider;
import riptide.gui.vanillaui.components.TabStrip;
import riptide.modules.BuiltinModules;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;

public final class RiptideAdminToolsOverlay extends RiptideOverlayBase {
   private static volatile RiptideAdminToolsOverlay sharedOverlay;
   private static final int DEFAULT_W = 374;
   private static final int DEFAULT_H = 264;
   private static final int TAB_H = 16;
   private static final int FIELD_H = 16;
   private static final int SCROLLBAR_W = 6;
   private static final int CARD_PAD_X = 10;
   private static final int CARD_PAD_BOTTOM = 10;
   private static final int FLOW_GAP = 5;
   private static final int LABEL_H = 11;
   private static final int FIELD_ROW_H = 29;
   private static final int MUTED_ROW_H = 13;
   private static final String[] TABS = new String[]{"Fireballs", "Item Editor", "ForceOP"};
   private static final String[] ITEM_TABS = new String[]{"Item", "Visual", "Stats", "Raw"};
   private static final int RAW_PREVIEW_H = 104;
   private static final String[] FIREBALL_PRESETS = new String[]{"Balanced", "Strong", "Max", "Beam"};
   private final List<RiptideAdminToolsOverlay.Hit> hits = new ArrayList<>();
   private int activeTab;
   private int itemTab;
   private int fireballPreset;
   private int scroll;
   private int contentHeight;
   private Dropdown dropdown;
   private Setting<?, ?> dropdownOption;
   private RiptideAdminToolsOverlay.Editing editing;
   private Setting<?, ?> bindingOption;
   private Setting<?, ?> sliderOption;
   private UiBounds lastBodyBounds;
   private UiBounds lastViewport;
   private UiBounds editingTextFieldBounds;
   private boolean draggingTextSelection;
   private boolean scrollbarDragging;
   private boolean headerDragging;
   private int scrollbarGrabOffset;
   private boolean itemStackCaptureActive;
   private boolean restoreVisibleAfterItemStackCapture;
   private boolean autoOpenedInventoryForItemStackCapture;
   private Screen screenBeforeItemStackCapture;

   private RiptideAdminToolsOverlay() {
      super("admin-tools-panel", 374, 264);
      this.panelX = 88;
      this.panelY = 32;
   }

   public static synchronized RiptideAdminToolsOverlay getSharedOverlay() {
      if (sharedOverlay == null) {
         sharedOverlay = new RiptideAdminToolsOverlay();
      }

      return sharedOverlay;
   }

   public static RiptideAdminToolsOverlay getSharedOverlayIfExists() {
      return sharedOverlay;
   }

   public void showItemEditor() {
      this.activeTab = 1;
      this.itemTab = 0;
      this.scroll = 0;
      this.setVisible(true);
   }

   public void showRawItemEditor() {
      this.activeTab = 1;
      this.itemTab = 3;
      this.scroll = 0;
      this.setVisible(true);
   }

   @Override
   public int getMinWidth() {
      return 336;
   }

   @Override
   public int getMinHeight() {
      return 144;
   }

   @Override
   public boolean isOverResizeHandle(double mouseX, double mouseY) {
      return this.visible && !this.collapsed
         ? mouseX >= this.panelX + this.panelWidth - 10
            && mouseX <= this.panelX + this.panelWidth
            && mouseY >= this.panelY + this.panelHeight - 10
            && mouseY <= this.panelY + this.panelHeight
         : false;
   }

   @Override
   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.recordMouse(mouseX, mouseY);
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            BuiltinModules.AdminToolsModule module = this.adminTools();
            if (module != null) {
               this.applyAutoHeight();
               RiptideWindowLayout bounds = this.clampToScreen(this);
               this.panelX = bounds.x;
               this.panelY = bounds.y;
               this.panelWidth = bounds.width;
               this.panelHeight = bounds.height;
               boolean topPopup = this.dropdown != null;
               int baseMouseX = topPopup ? -10000 : mouseX;
               int baseMouseY = topPopup ? -10000 : mouseY;
               UiContext context = UiContexts.overlay(graphics, mc.font, baseMouseX, baseMouseY);
               this.renderWindowFrame(graphics, baseMouseX, baseMouseY, bounds, "Admin Tools", this.collapsed, false);
               this.hits.clear();
               if (!this.collapsed) {
                  boolean clipped = this.beginWindowBodyClip(graphics, bounds, this.collapsed);

                  try {
                     this.renderBody(context, module, bounds, baseMouseX, baseMouseY);
                  } finally {
                     this.endWindowBodyClip(graphics, clipped);
                     this.renderWindowInactiveOverlay(graphics, bounds, this.collapsed, false);
                  }

                  if (this.dropdown != null) {
                     graphics.nextStratum();
                     this.dropdown.render(UiContexts.overlay(graphics, mc.font, mouseX, mouseY));
                  }
               }
            }
         }
      }
   }

   private void renderBody(UiContext context, BuiltinModules.AdminToolsModule module, RiptideWindowLayout bounds, int mouseX, int mouseY) {
      int x = bounds.x + 5;
      int y = bounds.y + 16 + 5;
      int w = Math.max(1, bounds.width - 10);
      UiBounds strip = UiBounds.of(x, y, w, 16);

      for (int i = 0; i < TABS.length; i++) {
         UiBounds tab = TabStrip.tabBounds(strip, TABS.length, i, 3);
         boolean active = i == this.activeTab;
         TabStrip.renderTab(context, tab, TABS[i], active);
         this.hits.add(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.TAB, tab, null, Integer.toString(i)));
      }

      UiBounds body = UiBounds.of(x, y + 16 + 5, w, Math.max(1, bounds.y + bounds.height - y - 16 - 10));
      this.lastBodyBounds = body;
      this.contentHeight = this.tabContentHeight(body.width());
      this.scroll = clamp(this.scroll, 0, this.maxScroll(body.height()));
      UiBounds viewport = this.contentHeight > body.height() ? UiBounds.of(body.x(), body.y(), body.width() - 6 - 3, body.height()) : body;
      this.lastViewport = viewport;
      UiScissorStack.global().push(context.graphics(), viewport);

      try {
         int contentY = viewport.y() - this.scroll;
         switch (this.activeTab) {
            case 1:
               this.renderItemEditor(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
               break;
            case 2:
               this.renderForceOp(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
               break;
            default:
               this.renderFireballs(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
         }
      } finally {
         UiScissorStack.global().pop(context.graphics());
      }

      if (this.contentHeight > body.height()) {
         UiBounds track = UiBounds.of(body.right() - 6, body.y(), 6, body.height());
         Scrollbar.Metrics metrics = Scrollbar.metrics(track, this.contentHeight, body.height(), this.scroll);
         Scrollbar.render(context, metrics, this.scrollbarDragging);
         this.hits.add(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.SCROLLBAR, track, null, ""));
      }
   }

   private int tabContentHeight(int availableWidth) {
      return switch (this.activeTab) {
         case 1 -> this.itemEditorHeight(Math.max(1, availableWidth - 6 - 3));
         case 2 -> this.forceOpCardHeight(availableWidth);
         default -> this.fireballsCardHeight();
      };
   }

   private void applyAutoHeight() {
      if (!this.collapsed) {
         int bodyWidth = Math.max(1, this.panelWidth - 10);
         int desired = 42 + this.tabContentHeight(bodyWidth) + 10;
         int screenHeight = RiptideUiScale.getVirtualScreenHeight();
         int maxHeight = screenHeight > 0 ? Math.max(this.getMinHeight(), screenHeight - 8) : Math.max(264, desired);
         this.panelHeight = clamp(desired, this.getMinHeight(), maxHeight);
      }
   }

   private int fireballsCardHeight() {
      return 99;
   }

   private int forceOpCardHeight(int width) {
      int innerW = Math.max(1, width - 20);
      int cursor = 20;
      cursor += 66;
      cursor += 26;
      cursor += this.actionRowHeight(innerW, 3) + 6;
      cursor += this.actionRowHeight(innerW, 1) + 5;
      cursor += 13;
      return cursor + 7;
   }

   private int itemEditorHeight(int width) {
      int innerW = Math.max(1, width - 20);
      int tabHeight = 21;

      int content = switch (this.itemTab) {
         case 1 -> this.itemVisualHeight(innerW);
         case 2 -> this.itemStatsHeight(innerW);
         case 3 -> this.itemRawHeight(innerW);
         default -> this.itemBasicHeight(innerW);
      };
      return 18 + tabHeight + content + 10;
   }

   private int itemBasicHeight(int width) {
      RiptideAdminToolsOverlay.FlowSizer sizer = new RiptideAdminToolsOverlay.FlowSizer();
      sizer.add(13);
      if (width >= 270) {
         sizer.add(29);
      } else {
         sizer.add(29);
         sizer.add(29);
      }

      sizer.add(29);
      sizer.add(29);
      sizer.add(29);
      sizer.add(this.actionRowHeight(width, 3));
      sizer.add(16);
      return sizer.height();
   }

   private int itemVisualHeight(int width) {
      RiptideAdminToolsOverlay.FlowSizer sizer = new RiptideAdminToolsOverlay.FlowSizer();
      sizer.add(29);
      sizer.add(29);
      sizer.add(width >= 280 ? 29 : 63);
      sizer.add(this.actionRowHeight(width, 2));
      sizer.add(16);
      return sizer.height();
   }

   private int itemStatsHeight(int width) {
      RiptideAdminToolsOverlay.FlowSizer sizer = new RiptideAdminToolsOverlay.FlowSizer();
      sizer.add(29);
      sizer.add(this.actionRowHeight(width, 2));
      sizer.add(29);
      sizer.add(29);
      sizer.add(13);
      sizer.add(16);
      return sizer.height();
   }

   private int itemRawHeight(int width) {
      RiptideAdminToolsOverlay.FlowSizer sizer = new RiptideAdminToolsOverlay.FlowSizer();
      sizer.add(13);
      sizer.add(104);
      sizer.add(this.actionRowHeight(width, 3));
      sizer.add(16);
      return sizer.height();
   }

   private void renderFireballs(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
      this.renderCard(context, "Fireballs", UiBounds.of(x, y, w, this.fireballsCardHeight()));
      int innerX = x + 8;
      int innerY = y + 18;
      int innerW = Math.max(1, w - 16);
      int presetW = Math.min(118, Math.max(84, innerW / 3));
      this.drawLabel(context, "Strength", innerX, innerY, presetW);
      this.renderDropdown(
         context, null, UiBounds.of(innerX, innerY + 11, presetW, 16), FIREBALL_PRESETS[this.fireballPreset], List.of(FIREBALL_PRESETS), mouseX, mouseY
      );
      this.renderSliderRow(context, module, "fireball-delay", innerX + presetW + 8, innerY + 7, Math.max(90, innerW - presetW - 8), mouseX, mouseY);
      this.renderBindRow(context, module, innerX, y + 54, innerW, "Stream Bind", "fireball-stream-bind", mouseX, mouseY);
      this.renderBindRow(context, module, innerX, y + 75, innerW, "Firestorm Bind", "firestorm-bind", mouseX, mouseY);
   }

   private void renderItemEditor(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
      int cardH = this.itemEditorHeight(w);
      this.renderCard(context, "Held Item Editor", UiBounds.of(x, y, w, cardH));
      int tx = x + 10;
      int ty = y + 18;
      int tw = Math.max(1, w - 20);
      RiptideAdminToolsOverlay.Flow flow = new RiptideAdminToolsOverlay.Flow(tx, ty, tw);
      UiBounds strip = flow.take(16);

      for (int i = 0; i < ITEM_TABS.length; i++) {
         UiBounds tab = TabStrip.tabBounds(strip, ITEM_TABS.length, i, 3);
         boolean active = i == this.itemTab;
         TabStrip.renderTab(context, tab, ITEM_TABS[i], active);
         this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.ITEM_TAB, tab, null, Integer.toString(i)));
      }

      flow.gap(5);
      switch (this.itemTab) {
         case 1:
            this.renderItemVisual(context, module, flow, mouseX, mouseY);
            break;
         case 2:
            this.renderItemStats(context, module, flow, mouseX, mouseY);
            break;
         case 3:
            this.renderItemRaw(context, module, flow, mouseX, mouseY);
            break;
         default:
            this.renderItemBasic(context, module, flow, mouseX, mouseY);
      }
   }

   private void renderItemBasic(UiContext context, BuiltinModules.AdminToolsModule module, RiptideAdminToolsOverlay.Flow flow, int mouseX, int mouseY) {
      UiBounds info = flow.take(13);
      this.drawMuted(context, "Fill held, copy a targeted held item, or type an item id.", info.x(), info.y(), info.width());
      int gap = 5;
      if (flow.w >= 270) {
         UiBounds row = flow.take(29);
         int pickW = 52;
         int numericW = 54;
         int idW = Math.max(80, row.width() - pickW - gap - (numericW + gap) * 2);
         this.renderTextField(context, module, this.option(module, "nbt-item-id"), row.x(), row.y(), idW, "Item ID", mouseX, mouseY);
         this.renderActionButton(
            context, module, UiBounds.of(row.x() + idW + gap, row.y() + 11 + 2, pickW, 16), this.custom("List", "pick-item"), mouseX, mouseY
         );
         this.renderTextField(
            context, module, this.option(module, "nbt-item-count"), row.x() + idW + gap + pickW + gap, row.y(), numericW, "Count", mouseX, mouseY
         );
         this.renderTextField(
            context, module, this.option(module, "nbt-max-stack"), row.x() + idW + gap + pickW + gap + numericW + gap, row.y(), numericW, "Max", mouseX, mouseY
         );
      } else {
         UiBounds itemRow = flow.take(29);
         int pickW = 52;
         int idW = Math.max(60, itemRow.width() - pickW - gap);
         this.renderTextField(context, module, this.option(module, "nbt-item-id"), itemRow.x(), itemRow.y(), idW, "Item ID", mouseX, mouseY);
         this.renderActionButton(
            context, module, UiBounds.of(itemRow.x() + idW + gap, itemRow.y() + 11 + 2, pickW, 16), this.custom("List", "pick-item"), mouseX, mouseY
         );
         this.renderInlineFields(context, module, flow.take(29), mouseX, mouseY, "nbt-item-count", "nbt-max-stack");
      }

      this.renderTextField(context, module, this.option(module, "nbt-custom-data"), flow.take(29), "Custom data SNBT", mouseX, mouseY);
      this.renderGeneratedField(context, module, this.option(module, "nbt-item-components"), flow.take(29), "Generated component patch");
      this.renderTextField(context, module, this.option(module, "nbt-command"), flow.take(29), "Optional embedded command", mouseX, mouseY);
      this.renderActionRow(
         context,
         module,
         flow.take(this.actionRowHeight(flow.w, 3)),
         mouseX,
         mouseY,
         this.custom("Pick Item", "pick-inventory-item"),
         this.action("Copy Target", "copy-target-item"),
         this.action("Apply to Held", "apply-held-item")
      );
      this.renderRawEditorActionRow(context, module, flow.take(16), mouseX, mouseY);
   }

   private void renderItemVisual(UiContext context, BuiltinModules.AdminToolsModule module, RiptideAdminToolsOverlay.Flow flow, int mouseX, int mouseY) {
      this.renderTextField(context, module, this.option(module, "nbt-item-name"), flow.take(29), "Display name", mouseX, mouseY);
      this.renderTextField(context, module, this.option(module, "nbt-item-lore"), flow.take(29), "Lore lines separated with |", mouseX, mouseY);
      Setting<?, ?> glint = this.option(module, "nbt-glint");
      Setting<?, ?> rarity = this.option(module, "nbt-rarity");
      Setting<?, ?> unbreakable = this.option(module, "nbt-unbreakable");
      if (flow.w >= 280) {
         UiBounds row = flow.take(29);
         int glintW = Math.max(76, (row.width() - 8) / 3);
         int rarityW = Math.max(86, (row.width() - 8) / 3);
         this.drawLabel(context, "Glint", row.x() + 2, row.y(), glintW - 4);
         this.renderDropdown(context, glint, UiBounds.of(row.x(), row.y() + 11 + 2, glintW, 16), module.value(glint.id()), glint.choices(), mouseX, mouseY);
         this.drawLabel(context, "Rarity", row.x() + glintW + 4, row.y(), rarityW - 4);
         this.renderDropdown(
            context, rarity, UiBounds.of(row.x() + glintW + 4, row.y() + 11 + 2, rarityW, 16), module.value(rarity.id()), rarity.choices(), mouseX, mouseY
         );
         this.renderBooleanInline(
            context,
            module,
            unbreakable,
            UiBounds.of(row.x() + glintW + rarityW + 8, row.y() + 11 + 2, Math.max(40, row.width() - glintW - rarityW - 8), 16),
            "Unbreakable",
            mouseX,
            mouseY
         );
      } else {
         UiBounds row = flow.take(29);
         this.renderDropdownField(context, module, glint, UiBounds.of(row.x(), row.y(), Math.max(1, (row.width() - 4) / 2), 29), "Glint", mouseX, mouseY);
         this.renderDropdownField(
            context, module, rarity, UiBounds.of(row.x() + (row.width() + 4) / 2, row.y(), Math.max(1, (row.width() - 4) / 2), 29), "Rarity", mouseX, mouseY
         );
         this.renderBooleanInline(context, module, unbreakable, flow.take(29), "Unbreakable", mouseX, mouseY);
      }

      this.renderActionRow(
         context,
         module,
         flow.take(this.actionRowHeight(flow.w, 2)),
         mouseX,
         mouseY,
         this.custom("Edit Lore", "edit-lore"),
         this.custom("Edit Full NBT", "edit-item-stack")
      );
      this.renderFittedActionRow(
         context,
         module,
         flow.take(16),
         mouseX,
         mouseY,
         this.custom("Clear", "item-clear"),
         this.custom("Pick Item", "pick-inventory-item"),
         this.action("Fill Held", "fill-held-item"),
         this.action("Copy Target", "copy-target-item"),
         this.action("Apply to Held", "apply-held-item"),
         this.custom("Give", "give-item")
      );
   }

   private void renderItemStats(UiContext context, BuiltinModules.AdminToolsModule module, RiptideAdminToolsOverlay.Flow flow, int mouseX, int mouseY) {
      this.renderInlineFields(context, module, flow.take(29), mouseX, mouseY, "nbt-max-damage", "nbt-damage");
      this.renderActionRow(
         context,
         module,
         flow.take(this.actionRowHeight(flow.w, 2)),
         mouseX,
         mouseY,
         this.custom("Edit Enchants", "edit-enchants"),
         this.custom("Edit Attributes", "edit-attributes")
      );
      this.renderTextField(context, module, this.option(module, "nbt-enchants"), flow.take(29), "Enchantments raw list", mouseX, mouseY);
      this.renderTextField(context, module, this.option(module, "nbt-attributes"), flow.take(29), "Attributes raw list", mouseX, mouseY);
      UiBounds info = flow.take(13);
      this.drawMuted(context, "Raw lists stay editable for exact component control.", info.x(), info.y(), info.width());
      this.renderFittedActionRow(
         context,
         module,
         flow.take(16),
         mouseX,
         mouseY,
         this.custom("Clear", "item-clear"),
         this.custom("Pick Item", "pick-inventory-item"),
         this.action("Fill Held", "fill-held-item"),
         this.action("Copy Target", "copy-target-item"),
         this.action("Apply to Held", "apply-held-item"),
         this.custom("Give", "give-item")
      );
   }

   private void renderItemRaw(UiContext context, BuiltinModules.AdminToolsModule module, RiptideAdminToolsOverlay.Flow flow, int mouseX, int mouseY) {
      UiBounds info = flow.take(13);
      this.drawMuted(context, "Complete ItemStack SNBT. Click the preview to edit; raw edits are authoritative.", info.x(), info.y(), info.width());
      UiBounds preview = flow.take(104);
      this.renderCard(context, "Full ItemStack SNBT", preview);
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.ACTION, preview, null, "edit-item-stack"));
      List<String> lines = RiptideItemNbtInspector.prettySnbtLines(module.value("nbt-item-stack"));
      int x = preview.x() + 7;
      int y = preview.y() + 18;
      int width = Math.max(1, preview.width() - 14);
      int maxLines = Math.max(1, (preview.height() - 23) / 10);
      int shown = Math.min(maxLines, lines.size());

      for (int i = 0; i < shown; i++) {
         String line = i == shown - 1 && lines.size() > shown ? lines.get(i) + "  ... (" + (lines.size() - shown) + " more lines)" : lines.get(i);
         this.drawRawPreviewLine(context, line, x, y + i * 10, width);
      }

      this.renderActionRow(
         context,
         module,
         flow.take(this.actionRowHeight(flow.w, 3)),
         mouseX,
         mouseY,
         this.custom("Edit Raw NBT", "edit-item-stack"),
         this.action("Fill Held", "fill-held-item"),
         this.action("Apply to Held", "apply-held-item")
      );
      this.renderFittedActionRow(
         context,
         module,
         flow.take(16),
         mouseX,
         mouseY,
         this.custom("Copy Raw", "copy-item-stack-snbt"),
         this.custom("Clear", "item-clear"),
         this.custom("Give", "give-item")
      );
   }

   private void drawRawPreviewLine(UiContext context, String line, int x, int y, int maxWidth) {
      int cursor = x;
      int remaining = maxWidth;

      for (RiptideItemNbtInspector.TextToken token : RiptideItemNbtInspector.tokenizeStructuredText(line, context.theme().colors().text)) {
         if (token != null && token.text() != null && !token.text().isEmpty() && remaining > 0) {
            String text = context.text().trimEllipsis(token.text(), remaining);
            context.text().draw(context.graphics(), text, cursor, y, token.color());
            int width = context.text().width(text);
            cursor += width;
            remaining -= width;
            if (!text.equals(token.text())) {
               break;
            }
         }
      }
   }

   private void renderForceOp(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
      int innerX = x + 10;
      int innerW = Math.max(1, w - 20);
      int cursor = y + 20;
      this.renderCard(context, "AuthMe ForceOP", UiBounds.of(x, y, w, this.forceOpCardHeight(w)));
      this.renderSliderRow(context, module, "forceop-delay", innerX, cursor, innerW, mouseX, mouseY);
      cursor += 22;
      this.renderSliderRow(context, module, "forceop-min-length", innerX, cursor, innerW, mouseX, mouseY);
      cursor += 22;
      this.renderSliderRow(context, module, "forceop-max-length", innerX, cursor, innerW, mouseX, mouseY);
      cursor += 22;
      Setting<?, ?> wait = this.option(module, "forceop-wait");
      UiBounds toggle = UiBounds.of(innerX, cursor + 2, 34, 16);
      CompactFieldFactory.toggle(
         context, toggle, Boolean.parseBoolean(module.value(wait.id())), toggle.contains(mouseX, mouseY), "admintools:" + module.id() + "/" + wait.id()
      );
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.BOOLEAN, UiBounds.of(innerX, cursor, innerW, 20), wait, ""));
      this.drawLabel(context, wait.label(), innerX + 40, cursor + 6, Math.max(1, innerW - 46));
      cursor += 26;
      int actionH = this.actionRowHeight(innerW, 3);
      this.renderActionRow(
         context,
         module,
         UiBounds.of(innerX, cursor, innerW, actionH),
         mouseX,
         mouseY,
         this.custom("Load TXT", "forceop-load"),
         this.custom("Unload", "forceop-unload"),
         this.custom("Preview", "forceop-preview")
      );
      cursor += actionH + 6;
      int runH = this.actionRowHeight(innerW, 1);
      this.renderActionRow(
         context,
         module,
         UiBounds.of(innerX, cursor, innerW, runH),
         mouseX,
         mouseY,
         this.custom(module.isForceOpRunning() ? "Stop" : "Start", module.isForceOpRunning() ? "forceop-stop" : "forceop-start")
      );
      cursor += runH + 5;
      this.drawText(
         context,
         "Passwords: " + module.getForceOpTotal() + "   Attempt: " + module.getForceOpIndex() + " / " + module.getForceOpTotal(),
         innerX,
         cursor,
         innerW,
         module.isForceOpRunning() ? context.theme().colors().accent : context.theme().colors().muted
      );
   }

   private void renderCard(UiContext context, String title, UiBounds bounds) {
      SectionPanel.render(context, bounds, title);
   }

   private void renderActionRow(UiContext context, Module module, int x, int y, int w, int mouseX, int mouseY, RiptideAdminToolsOverlay.AdminAction... actions) {
      this.renderActionRow(context, module, UiBounds.of(x, y, w, this.actionRowHeight(w, actions == null ? 0 : actions.length)), mouseX, mouseY, actions);
   }

   private void renderActionRow(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, RiptideAdminToolsOverlay.AdminAction... actions) {
      if (actions != null && actions.length != 0) {
         int gap = 3;
         int minCellW = 54;
         int x = bounds.x();
         int y = bounds.y();
         int w = bounds.width();
         int columns = Math.max(1, Math.min(actions.length, (w + gap) / Math.max(1, minCellW + gap)));

         for (int index = 0; index < actions.length; index++) {
            RiptideAdminToolsOverlay.AdminAction action = actions[index];
            int rowIndex = index / columns;
            int colIndex = index % columns;
            int yOffset = rowIndex * 19;
            UiBounds row = UiBounds.of(x, y + yOffset, w, 16);
            int countInRow = Math.min(columns, actions.length - rowIndex * columns);
            UiBounds cell = CompactToolbar.cell(row, countInRow, colIndex, gap);
            Button.Tone tone = !action.label.toLowerCase(Locale.ROOT).contains("run") && !action.label.equals("Start")
               ? (!action.label.equals("Stop") && !action.label.equals("Clear") ? Button.Tone.NORMAL : Button.Tone.DANGER)
               : Button.Tone.PRIMARY;
            Button.render(context, cell, action.label, tone, cell.contains(mouseX, mouseY), false);
            this.addViewportHit(
               new RiptideAdminToolsOverlay.Hit(
                  RiptideAdminToolsOverlay.HitType.ACTION, cell, action.optionId == null ? null : this.option(module, action.optionId), action.command
               )
            );
         }
      }
   }

   private void renderFittedActionRow(
      UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, RiptideAdminToolsOverlay.AdminAction... actions
   ) {
      if (actions != null && actions.length != 0) {
         int gap = 3;
         int pad = 6;
         int n = actions.length;
         int[] want = new int[n];
         long total = (long)gap * (n - 1);
         long labelTotal = 0L;

         for (int i = 0; i < n; i++) {
            int label = context.text().width(actions[i].label);
            labelTotal += label;
            want[i] = Math.max(1, label + pad);
            total += want[i];
         }

         int x = bounds.x();
         int usable = Math.max(1, bounds.width() - gap * (n - 1));

         for (int i = 0; i < n; i++) {
            int w;
            if (i == n - 1) {
               w = Math.max(1, bounds.right() - x);
            } else if (total <= bounds.width()) {
               int leftover = (int)(bounds.width() - total);
               w = want[i] + leftover / n;
            } else {
               w = (int)Math.max(1L, (long)usable * context.text().width(actions[i].label) / Math.max(1L, labelTotal));
            }

            this.renderActionButton(context, module, UiBounds.of(x, bounds.y(), w, 16), actions[i], mouseX, mouseY);
            x += w + gap;
         }
      }
   }

   private void renderRawEditorActionRow(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY) {
      this.renderFittedActionRow(
         context,
         module,
         bounds,
         mouseX,
         mouseY,
         this.custom("Clear", "item-clear"),
         this.action("Fill", "fill-held-item"),
         this.custom("Edit Custom Data", "edit-custom-data"),
         this.custom("Edit Full NBT", "edit-item-stack"),
         this.custom("Give", "give-item")
      );
   }

   private int actionRowHeight(int width, int actionCount) {
      if (actionCount <= 0) {
         return 0;
      } else {
         int gap = 3;
         int minCellW = 54;
         int columns = Math.max(1, Math.min(actionCount, (Math.max(1, width) + gap) / Math.max(1, minCellW + gap)));
         int rows = (int)Math.ceil((double)actionCount / columns);
         return rows * 16 + Math.max(0, rows - 1) * gap;
      }
   }

   private void renderActionButton(UiContext context, Module module, UiBounds bounds, RiptideAdminToolsOverlay.AdminAction action, int mouseX, int mouseY) {
      if (action != null) {
         Button.render(context, bounds, action.label, Button.Tone.NORMAL, bounds.contains(mouseX, mouseY), false);
         this.addViewportHit(
            new RiptideAdminToolsOverlay.Hit(
               RiptideAdminToolsOverlay.HitType.ACTION, bounds, action.optionId == null ? null : this.option(module, action.optionId), action.command
            )
         );
      }
   }

   private void renderBindRow(UiContext context, Module module, int x, int y, int w, String label, String optionId, int mouseX, int mouseY) {
      Setting<?, ?> option = this.option(module, optionId);
      this.drawLabel(context, label, x + 2, y + 5, w - 160);
      UiBounds bind = UiBounds.of(x + w - 126, y, 78, 16);
      UiBounds clear = UiBounds.of(x + w - 44, y, 44, 16);
      boolean focused = this.bindingOption == option;
      Button.render(
         context,
         bind,
         focused ? "Press key" : RiptideBindUtil.getBindName(parseInt(module.value(option.id()), -1)),
         Button.Tone.NORMAL,
         bind.contains(mouseX, mouseY),
         focused
      );
      Button.render(context, clear, "Clear", Button.Tone.NORMAL, clear.contains(mouseX, mouseY), false);
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.KEYBIND, bind, option, ""));
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.CLEAR_KEYBIND, clear, option, ""));
   }

   private void renderInlineFields(UiContext context, Module module, int x, int y, int w, int mouseX, int mouseY, String... ids) {
      this.renderInlineFields(context, module, UiBounds.of(x, y, w, 29), mouseX, mouseY, ids);
   }

   private void renderInlineFields(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, String... ids) {
      int gap = 4;

      for (int index = 0; index < ids.length; index++) {
         String id = ids[index];
         UiBounds field = CompactForm.cell(bounds, ids.length, index, gap);
         this.renderTextField(context, module, this.option(module, id), field.x(), field.y(), field.width(), this.option(module, id).label(), mouseX, mouseY);
      }
   }

   private void renderTextField(UiContext context, Module module, Setting<?, ?> option, int x, int y, int w, String label, int mouseX, int mouseY) {
      this.renderTextField(context, module, option, UiBounds.of(x, y, w, 29), label, mouseX, mouseY);
   }

   private void renderTextField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label, int mouseX, int mouseY) {
      if (option != null) {
         this.drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
         UiBounds bounds = UiBounds.of(row.x(), row.y() + 11 + 2, row.width(), 16);
         boolean focused = this.editing != null && this.editing.option == option;
         CompactFieldFactory.text(
            context,
            bounds,
            focused ? this.editing.text : module.value(option.id()),
            "",
            focused,
            focused ? this.editing.cursor : module.value(option.id()).length(),
            focused ? this.editing.selectionStart() : -1,
            focused ? this.editing.selectionEnd() : -1
         );
         this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.TEXT, bounds, option, ""));
      }
   }

   private void renderGeneratedField(UiContext context, Module module, Setting<?, ?> option, int x, int y, int w, String label) {
      this.renderGeneratedField(context, module, option, UiBounds.of(x, y, w, 29), label);
   }

   private void renderGeneratedField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label) {
      this.drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
      UiBounds bounds = UiBounds.of(row.x(), row.y() + 11 + 2, row.width(), 16);
      CompactFieldFactory.text(context, bounds, module.displayValue(option), "", false, 0);
   }

   private void renderDropdownField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label, int mouseX, int mouseY) {
      if (option != null) {
         this.drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
         this.renderDropdown(
            context, option, UiBounds.of(row.x(), row.y() + 11 + 2, row.width(), 16), module.value(option.id()), option.choices(), mouseX, mouseY
         );
      }
   }

   private void renderBooleanInline(UiContext context, Module module, Setting<?, ?> option, UiBounds bounds, String label, int mouseX, int mouseY) {
      if (option != null) {
         UiBounds toggle = UiBounds.of(bounds.x(), bounds.y(), 34, 16);
         CompactFieldFactory.toggle(
            context, toggle, Boolean.parseBoolean(module.value(option.id())), bounds.contains(mouseX, mouseY), "admintools:" + module.id() + "/" + option.id()
         );
         this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.BOOLEAN, bounds, option, ""));
         this.drawLabel(context, label, toggle.right() + 6, bounds.y() + 4, Math.max(1, bounds.right() - toggle.right() - 8));
      }
   }

   private void renderSliderRow(UiContext context, Module module, String optionId, int x, int y, int w, int mouseX, int mouseY) {
      Setting<?, ?> option = this.option(module, optionId);
      this.drawLabel(context, option.label(), x, y + 5, 82);
      UiBounds control = UiBounds.of(x + 84, y + 2, Math.max(60, w - 84), 16);
      this.renderNumber(context, module, option, control, mouseX, mouseY);
   }

   private void renderNumber(UiContext context, Module module, Setting<?, ?> option, UiBounds control, int mouseX, int mouseY) {
      int valueW = Math.min(54, Math.max(42, control.width() / 4));
      UiBounds slider = UiBounds.of(control.x(), control.y(), Math.max(40, control.width() - valueW - 4), control.height());
      UiBounds value = UiBounds.of(slider.right() + 4, control.y(), valueW, control.height());
      double current = option.kind() == Kind.INTEGER
         ? parseInt(module.value(option.id()), parseInt(option.defaultValue(), 0))
         : parseDouble(module.value(option.id()), parseDouble(option.defaultValue(), 0.0));
      Slider.render(context, slider, Slider.ratio(current, option.sliderMin(), option.sliderMax()), slider.contains(mouseX, mouseY));
      boolean focused = this.editing != null && this.editing.option == option;
      CompactFieldFactory.text(
         context,
         value,
         focused ? this.editing.text : module.value(option.id()),
         "",
         focused,
         focused ? this.editing.cursor : module.value(option.id()).length(),
         focused ? this.editing.selectionStart() : -1,
         focused ? this.editing.selectionEnd() : -1
      );
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.SLIDER, slider, option, ""));
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.TEXT, value, option, ""));
   }

   private void renderDropdown(UiContext context, Setting<?, ?> option, UiBounds bounds, String label, List<String> choices, int mouseX, int mouseY) {
      boolean open = this.dropdown != null && this.dropdownOption == option && this.dropdown.bounds().equals(bounds);
      CompactFieldFactory.dropdown(context, bounds, label, bounds.contains(mouseX, mouseY), open);
      this.addViewportHit(new RiptideAdminToolsOverlay.Hit(RiptideAdminToolsOverlay.HitType.DROPDOWN, bounds, option, String.join("\u0000", choices)));
      if (!open) {
         if (this.dropdown != null && this.dropdown.bounds().equals(bounds)) {
            this.dropdownOption = option;
         }
      }
   }

   private void drawLabel(UiContext context, String text, int x, int y, int width) {
      this.drawText(context, text, x, y, width, context.theme().colors().muted);
   }

   private void drawMuted(UiContext context, String text, int x, int y, int width) {
      this.drawText(context, text, x, y, width, context.theme().colors().muted);
   }

   private void drawText(UiContext context, String text, int x, int y, int width, int color) {
      context.text().drawFitted(context.graphics(), text == null ? "" : text, x, y, Math.max(1, width), color);
   }

   private void addViewportHit(RiptideAdminToolsOverlay.Hit hit) {
      if (this.lastViewport == null || this.intersects(this.lastViewport, hit.bounds)) {
         this.hits.add(hit);
      }
   }

   private boolean intersects(UiBounds a, UiBounds b) {
      return b.right() > a.x() && b.x() < a.right() && b.bottom() > a.y() && b.y() < a.bottom();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         if (button == 0) {
            RiptideWindowLayout bounds = this.getBounds();
            if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
               this.setVisible(false);
               return true;
            }

            if (this.isOverDragBar(mouseX, mouseY)) {
               this.headerDragging = true;
               return true;
            }
         }

         if (this.collapsed) {
            return true;
         } else {
            BuiltinModules.AdminToolsModule module = this.adminTools();
            if (module == null) {
               return true;
            } else if (this.dropdown != null) {
               this.dropdown.mouseClicked((int)mouseX, (int)mouseY, button);
               if (!this.dropdown.isOpen()) {
                  this.clearDropdown();
               }

               return true;
            } else {
               this.finishEditing(true);

               for (int i = this.hits.size() - 1; i >= 0; i--) {
                  RiptideAdminToolsOverlay.Hit hit = this.hits.get(i);
                  if (hit.bounds.contains((int)mouseX, (int)mouseY)) {
                     switch (hit.type) {
                        case TAB:
                           this.activeTab = clamp(parseInt(hit.value, 0), 0, TABS.length - 1);
                           this.scroll = 0;
                           return true;
                        case ITEM_TAB:
                           this.itemTab = clamp(parseInt(hit.value, 0), 0, ITEM_TABS.length - 1);
                           if (this.itemTab == 3) {
                              module.prepareRawItemStackEditor();
                           }

                           this.scroll = 0;
                           return true;
                        case BOOLEAN:
                           module.setValue(hit.option.id(), Boolean.toString(!Boolean.parseBoolean(module.value(hit.option.id()))));
                           if (hit.option.id().startsWith("nbt-")) {
                              module.prepareRawItemStackEditor();
                           }

                           return true;
                        case TEXT:
                           this.beginEditing(module, hit.option, (int)mouseX, hit.bounds);
                           return true;
                        case SLIDER:
                           this.sliderOption = hit.option;
                           this.updateSlider(module, (int)mouseX);
                           return true;
                        case DROPDOWN:
                           List<String> values = hit.value.isEmpty() ? List.of() : List.of(hit.value.split("\u0000", -1));
                           String selected = hit.option == null ? FIREBALL_PRESETS[this.fireballPreset] : module.value(hit.option.id());
                           this.dropdownOption = hit.option;
                           this.dropdown = new Dropdown(hit.bounds, values, selected, value -> {
                              if (hit.option == null) {
                                 this.fireballPreset = Math.max(0, values.indexOf(value));
                                 this.applyFireballPreset(module, this.fireballPreset);
                              } else {
                                 module.setValue(hit.option.id(), value);
                                 if (hit.option.id().startsWith("nbt-")) {
                                    module.prepareRawItemStackEditor();
                                 }
                              }
                           });
                           this.dropdown.open();
                           return true;
                        case KEYBIND:
                           this.bindingOption = hit.option;
                           return true;
                        case CLEAR_KEYBIND:
                           module.setValue(hit.option.id(), "-1");
                           return true;
                        case ACTION:
                           this.handleAction(module, hit);
                           return true;
                        case SCROLLBAR:
                           Scrollbar.Metrics metrics = this.scrollbarMetrics();
                           if (metrics != null && metrics.maxScroll() > 0) {
                              this.scrollbarGrabOffset = metrics.thumb().contains((int)mouseX, (int)mouseY)
                                 ? (int)mouseY - metrics.thumb().y()
                                 : metrics.thumb().height() / 2;
                              this.scrollbarDragging = true;
                              this.scroll = Scrollbar.scrollFromMouse(metrics, (int)mouseY, this.scrollbarGrabOffset);
                           }

                           return true;
                     }
                  }
               }

               return this.isMouseOver(mouseX, mouseY);
            }
         }
      }
   }

   private void handleAction(BuiltinModules.AdminToolsModule module, RiptideAdminToolsOverlay.Hit hit) {
      if (hit.option != null && hit.option.action() != null) {
         hit.option.action().run();
      } else {
         String var3 = hit.value;
         switch (var3) {
            case "give-item":
               module.giveEditorItem();
               break;
            case "item-clear":
               this.clearItemEditor(module);
               break;
            case "pick-item":
               this.openItemPicker(module, hit.bounds);
               break;
            case "pick-inventory-item":
               this.startInventoryItemPicker(module);
               break;
            case "edit-lore":
               this.openItemOptionEditor(module, "nbt-item-lore", RiptideAdminItemOptionScreen.Mode.LORE);
               break;
            case "edit-enchants":
               this.openItemStructuredEditor(module, "nbt-enchants", RiptideAdminItemStructuredScreen.Mode.ENCHANTMENTS);
               break;
            case "edit-attributes":
               this.openItemStructuredEditor(module, "nbt-attributes", RiptideAdminItemStructuredScreen.Mode.ATTRIBUTES);
               break;
            case "edit-custom-data":
               this.openItemOptionEditor(module, "nbt-custom-data", RiptideAdminItemOptionScreen.Mode.RAW);
               break;
            case "edit-item-stack":
               module.prepareRawItemStackEditor();
               this.openItemOptionEditor(module, "nbt-item-stack", RiptideAdminItemOptionScreen.Mode.FULL_NBT);
               break;
            case "copy-item-stack-snbt":
               Minecraft mcx = Minecraft.getInstance();
               module.prepareRawItemStackEditor();
               String raw = module.value("nbt-item-stack");
               if (mcx != null && mcx.keyboardHandler != null && raw != null && !raw.isBlank()) {
                  mcx.keyboardHandler.setClipboard(raw);
                  RiptideNotifications.copied("Copied full ItemStack NBT.");
               } else {
                  RiptideNotifications.error("No full ItemStack NBT to copy.");
               }
               break;
            case "forceop-load":
               module.loadForceOpPasswords();
               break;
            case "forceop-unload":
               module.unloadForceOpPasswords();
               break;
            case "forceop-start":
               module.startForceOp();
               break;
            case "forceop-stop":
               module.stopForceOp();
               break;
            case "forceop-preview":
               Minecraft mc = Minecraft.getInstance();
               if (mc != null) {
                  mc.gui.setScreen(new RiptideForceOpPreviewScreen(mc.gui.screen(), module.getForceOpPasswords()));
               }
         }
      }
   }

   private void openItemOptionEditor(Module module, String optionId, RiptideAdminItemOptionScreen.Mode mode) {
      Minecraft mc = Minecraft.getInstance();
      Setting<?, ?> option = this.option(module, optionId);
      if (mc != null && option != null) {
         mc.gui.setScreen(new RiptideAdminItemOptionScreen(mc.gui.screen(), module, option, mode));
      }
   }

   private void openItemStructuredEditor(Module module, String optionId, RiptideAdminItemStructuredScreen.Mode mode) {
      Minecraft mc = Minecraft.getInstance();
      Setting<?, ?> option = this.option(module, optionId);
      if (mc != null && option != null) {
         mc.gui.setScreen(new RiptideAdminItemStructuredScreen(mc.gui.screen(), module, option, mode));
      }
   }

   private void openItemPicker(Module module, UiBounds anchor) {
      this.clearDropdown();
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.gui.setScreen(new RiptideItemPickerScreen(mc.gui.screen(), module.value("nbt-item-id"), value -> {
            if (module instanceof BuiltinModules.AdminToolsModule adminTools) {
               adminTools.selectEditorItemId(value);
               adminTools.prepareRawItemStackEditor();
            } else {
               module.setValue("nbt-item-id", value);
            }
         }));
      }
   }

   private void startInventoryItemPicker(BuiltinModules.AdminToolsModule module) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null) {
         this.finishEditing(true);
         this.clearDropdown();
         this.itemStackCaptureActive = true;
         this.restoreVisibleAfterItemStackCapture = this.visible;
         this.visible = false;
         this.autoOpenedInventoryForItemStackCapture = !(mc.gui.screen() instanceof AbstractContainerScreen);
         this.screenBeforeItemStackCapture = this.autoOpenedInventoryForItemStackCapture ? mc.gui.screen() : null;
         RiptideSharedState.get().setCaptureMode(true);
         RiptideSharedState.get().setCaptureCancelCallback(this::cancelInventoryItemPicker);
         RiptideNotifications.show("Right-click an inventory item to pick it.", -13248397);
         if (this.autoOpenedInventoryForItemStackCapture) {
            mc.execute(() -> {
               if (mc.player != null) {
                  mc.gui.setScreen(new InventoryScreen(mc.player));
               }
            });
         }
      } else {
         RiptideNotifications.warning("Join a world first.");
      }
   }

   public boolean wantsItemStackCapture() {
      return this.itemStackCaptureActive;
   }

   public boolean shouldRenderAbstractContainerScreenCaptureBanner() {
      return this.itemStackCaptureActive;
   }

   public String getAbstractContainerScreenCaptureTitle() {
      return "Capturing NBT item";
   }

   public String getAbstractContainerScreenCaptureInstruction() {
      return "Right-click a slot to fill the NBT item editor. Esc = cancel";
   }

   public String getAbstractContainerScreenCaptureHoverText(Slot slot, String itemName, String registryId) {
      if (slot == null) {
         return "";
      } else {
         String itemText = registryId != null && !registryId.isBlank() ? registryId : itemName;
         if (itemText == null || itemText.isBlank()) {
            itemText = "Empty slot";
         }

         int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(Minecraft.getInstance(), slot);
         return "Hover: " + visibleSlot + " | " + itemText;
      }
   }

   public boolean onInventoryItemStackCapture(Slot slot) {
      if (!this.itemStackCaptureActive) {
         return false;
      } else {
         ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
         BuiltinModules.AdminToolsModule module = this.adminTools();
         if (module != null && !stack.isEmpty()) {
            module.fillItemEditorFromStack(stack, false);
            RiptideNotifications.show("NBT editor filled from " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ".", -13248397);
            this.finishInventoryItemPicker();
            return true;
         } else {
            RiptideNotifications.warning("Pick a non-empty item slot.");
            return true;
         }
      }
   }

   public void cancelInventoryItemPicker() {
      if (this.itemStackCaptureActive) {
         RiptideNotifications.warning("Item pick cancelled.");
         this.finishInventoryItemPicker();
      }
   }

   private void finishInventoryItemPicker() {
      Minecraft mc = Minecraft.getInstance();
      this.itemStackCaptureActive = false;
      this.visible = this.restoreVisibleAfterItemStackCapture;
      this.restoreVisibleAfterItemStackCapture = false;
      RiptideSharedState.get().setCaptureMode(false);
      RiptideSharedState.get().setCaptureCancelCallback(null);
      RiptideOverlayManager.get().bringToFront(this);
      if (this.autoOpenedInventoryForItemStackCapture) {
         Screen restore = this.screenBeforeItemStackCapture;
         mc.execute(() -> {
            if (mc.gui.screen() instanceof InventoryScreen) {
               if (restore != null) {
                  mc.gui.setScreen(restore);
               }
            } else if (restore != null && mc.gui.screen() == null) {
               mc.gui.setScreen(restore);
            }
         });
      }

      this.autoOpenedInventoryForItemStackCapture = false;
      this.screenBeforeItemStackCapture = null;
   }

   private void clearItemEditor(Module module) {
      module.setValue("nbt-item-id", "");
      module.setValue("nbt-item-count", "1");
      module.setValue("nbt-max-stack", "64");
      module.setValue("nbt-custom-data", "");
      module.setValue("nbt-item-name", "");
      module.setValue("nbt-item-lore", "");
      module.setValue("nbt-glint", "Default");
      module.setValue("nbt-rarity", "Default");
      module.setValue("nbt-unbreakable", "false");
      module.setValue("nbt-max-damage", "0");
      module.setValue("nbt-damage", "0");
      module.setValue("nbt-enchants", "");
      module.setValue("nbt-attributes", "");
      module.setValue("nbt-command", "");
      module.setValue("nbt-item-components", "");
      module.setValue("nbt-imported-components", "");
      module.setValue("nbt-imported-signature", "");
      module.setValue("nbt-item-stack", "");
      module.setValue("nbt-item-stack-signature", "");
   }

   private void applyFireballPreset(Module module, int preset) {
      String delay = module.value("fireball-delay");
      if (delay == null || delay.isBlank()) {
         delay = "1";
      }

      switch (preset) {
         case 1:
            this.setFireball(module, "48", "10.0", "2.4", "5", delay, "2.5", "Cone", "true");
            break;
         case 2:
            this.setFireball(module, "128", "16.0", "3.2", "8", delay, "3.0", "Cone", "true");
            break;
         case 3:
            this.setFireball(module, "16", "0.0", "5.0", "6", delay, "2.5", "Look", "false");
            break;
         default:
            this.setFireball(module, "12", "8.0", "1.8", "3", delay, "2.0", "Cone", "true");
      }
   }

   private void setFireball(Module module, String count, String spread, String speed, String power, String delay, String distance, String aim, String randomize) {
      module.setValue("fireball-count", count);
      module.setValue("fireball-spread", spread);
      module.setValue("fireball-speed", speed);
      module.setValue("fireball-power", power);
      module.setValue("fireball-delay", delay);
      module.setValue("fireball-distance", distance);
      module.setValue("fireball-aim", aim);
      module.setValue("fireball-randomize", randomize);
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dropdown != null) {
         this.dropdown.mouseReleased((int)mouseX, (int)mouseY, button);
      }

      this.headerDragging = false;
      this.scrollbarDragging = false;
      this.draggingTextSelection = false;
      this.sliderOption = null;
      return this.visible && this.isMouseOver(mouseX, mouseY);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (button == 0 && this.headerDragging) {
         RiptideWindowLayout current = this.getBounds();
         this.setBounds(
            new RiptideWindowLayout(
               current.x + (int)Math.round(deltaX), current.y + (int)Math.round(deltaY), current.width, current.height, current.visible, current.collapsed
            )
         );
         return true;
      } else {
         BuiltinModules.AdminToolsModule module = this.adminTools();
         if (module == null) {
            return false;
         } else if (this.dropdown != null) {
            this.dropdown.mouseDragged((int)mouseX, (int)mouseY, button, deltaX, deltaY);
            return true;
         } else if (this.scrollbarDragging) {
            Scrollbar.Metrics metrics = this.scrollbarMetrics();
            if (metrics != null) {
               this.scroll = Scrollbar.scrollFromMouse(metrics, (int)mouseY, this.scrollbarGrabOffset);
            }

            return true;
         } else if (this.sliderOption != null) {
            this.updateSlider(module, (int)mouseX);
            return true;
         } else if (this.draggingTextSelection && button == 0 && this.editing != null && this.editingTextFieldBounds != null) {
            this.moveEditingCursor(this.cursorAtX(this.editing.text, (int)mouseX, this.editingTextFieldBounds), true);
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.visible || this.collapsed) {
         return false;
      } else if (this.dropdown != null) {
         this.dropdown.mouseScrolled((int)mouseX, (int)mouseY, amount);
         return true;
      } else if (!this.isMouseOver(mouseX, mouseY)) {
         return false;
      } else {
         this.scroll = clamp(this.scroll + (amount < 0.0 ? 36 : -36), 0, this.maxScroll(this.bodyBounds().height()));
         return true;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      BuiltinModules.AdminToolsModule module = this.adminTools();
      if (module == null) {
         return false;
      } else if (this.bindingOption == null) {
         if (this.editing != null) {
            boolean ctrl = (modifiers & 10) != 0;
            boolean shift = (modifiers & 1) != 0;
            if (keyCode == 256) {
               this.finishEditing(true);
               return true;
            } else if (ctrl && keyCode == 65) {
               this.editing.selectAll();
               return true;
            } else if (ctrl && keyCode == 67) {
               this.copyEditingSelection();
               return true;
            } else if (ctrl && keyCode == 88) {
               this.copyEditingSelection();
               this.deleteEditingSelection();
               return true;
            } else if (ctrl && keyCode == 86) {
               this.pasteEditingClipboard();
               return true;
            } else if (keyCode == 257 || keyCode == 335) {
               this.finishEditing(true);
               return true;
            } else if (keyCode == 259 && this.deleteEditingSelection()) {
               return true;
            } else if (keyCode == 259 && this.editing.cursor > 0) {
               this.editing.text = this.editing.text.substring(0, this.editing.cursor - 1) + this.editing.text.substring(this.editing.cursor);
               this.editing.cursor--;
               this.editing.clearSelection();
               return true;
            } else if (keyCode == 261 && this.deleteEditingSelection()) {
               return true;
            } else if (keyCode == 261 && this.editing.cursor < this.editing.text.length()) {
               this.editing.text = this.editing.text.substring(0, this.editing.cursor) + this.editing.text.substring(this.editing.cursor + 1);
               this.editing.clearSelection();
               return true;
            } else if (keyCode == 263) {
               this.moveEditingCursor(Math.max(0, this.editing.cursor - 1), shift);
               return true;
            } else if (keyCode == 262) {
               this.moveEditingCursor(Math.min(this.editing.text.length(), this.editing.cursor + 1), shift);
               return true;
            } else if (keyCode == 268) {
               this.moveEditingCursor(0, shift);
               return true;
            } else if (keyCode == 269) {
               this.moveEditingCursor(this.editing.text.length(), shift);
               return true;
            } else {
               return true;
            }
         } else if (keyCode == 256 && this.dropdown != null) {
            this.clearDropdown();
            return true;
         } else {
            return false;
         }
      } else {
         module.setValue(this.bindingOption.id(), Integer.toString(keyCode != 256 && keyCode != 259 && keyCode != 261 ? keyCode : -1));
         this.bindingOption = null;
         return true;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.editing == null) {
         return false;
      } else {
         if (chr >= ' ' && chr != 127) {
            this.replaceEditingSelection(Character.toString(chr));
         }

         return true;
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.editing != null || this.bindingOption != null;
   }

   @Override
   public void clearTextFieldFocus() {
      this.finishEditing(true);
      this.bindingOption = null;
      this.clearDropdown();
   }

   private void beginEditing(Module module, Setting<?, ?> option, int mouseX, UiBounds bounds) {
      String value = module.value(option.id());
      this.editing = new RiptideAdminToolsOverlay.Editing(option, value, this.cursorAtX(value, mouseX, bounds));
      this.editingTextFieldBounds = bounds;
      this.draggingTextSelection = true;
   }

   private int cursorAtX(String value, int mouseX, UiBounds bounds) {
      int local = mouseX - bounds.x() - 4;
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.font != null) {
         for (int i = 0; i <= value.length(); i++) {
            if (mc.font.width(value.substring(0, i)) >= local) {
               return i;
            }
         }

         return value.length();
      } else {
         return value.length();
      }
   }

   private void beginEditing(Module module, Setting<?, ?> option) {
      if (module != null && option != null) {
         String value = module.value(option.id());
         this.editing = new RiptideAdminToolsOverlay.Editing(option, value, value.length());
      }
   }

   private void moveEditingCursor(int cursor, boolean extendSelection) {
      if (this.editing != null) {
         if (extendSelection && !this.editing.hasSelection()) {
            this.editing.selectionAnchor = this.editing.cursor;
         }

         this.editing.cursor = clamp(cursor, 0, this.editing.text.length());
         if (!extendSelection) {
            this.editing.clearSelection();
         }
      }
   }

   private boolean deleteEditingSelection() {
      if (this.editing != null && this.editing.hasSelection()) {
         int start = this.editing.selectionStart();
         int end = this.editing.selectionEnd();
         this.editing.text = this.editing.text.substring(0, start) + this.editing.text.substring(end);
         this.editing.cursor = start;
         this.editing.clearSelection();
         return true;
      } else {
         return false;
      }
   }

   private void replaceEditingSelection(String replacement) {
      if (this.editing != null) {
         String safe = replacement == null ? "" : replacement;
         int start = this.editing.hasSelection() ? this.editing.selectionStart() : this.editing.cursor;
         int end = this.editing.hasSelection() ? this.editing.selectionEnd() : this.editing.cursor;
         this.editing.text = this.editing.text.substring(0, start) + safe + this.editing.text.substring(end);
         this.editing.cursor = start + safe.length();
         this.editing.clearSelection();
      }
   }

   private void copyEditingSelection() {
      if (this.editing != null && this.editing.hasSelection()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(this.editing.text.substring(this.editing.selectionStart(), this.editing.selectionEnd()));
         }
      }
   }

   private void pasteEditingClipboard() {
      if (this.editing != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.keyboardHandler != null) {
            String clipboard = mc.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
               this.replaceEditingSelection(this.sanitizeSingleLineClipboard(clipboard));
            }
         }
      }
   }

   private String sanitizeSingleLineClipboard(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
               out.append(' ');
            } else if (c >= ' ' && c != 127) {
               out.append(c);
            }
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private void finishEditing(boolean save) {
      if (this.editing != null) {
         BuiltinModules.AdminToolsModule module = this.adminTools();
         String optionId = this.editing.option.id();
         if (save && module != null) {
            module.setValue(optionId, this.editing.text);
            if ("nbt-item-id".equals(optionId)) {
               module.syncEditorMaxStackForItemId();
            }

            if (optionId.startsWith("nbt-")) {
               module.prepareRawItemStackEditor();
            }
         }

         this.editing = null;
         this.editingTextFieldBounds = null;
         this.draggingTextSelection = false;
      }
   }

   private void updateSlider(Module module, int mouseX) {
      if (module != null && this.sliderOption != null) {
         for (RiptideAdminToolsOverlay.Hit hit : this.hits) {
            if (hit.type == RiptideAdminToolsOverlay.HitType.SLIDER && hit.option == this.sliderOption) {
               double value = Slider.valueFromMouse(
                  mouseX, hit.bounds.x(), hit.bounds.width(), this.sliderOption.sliderMin(), this.sliderOption.sliderMax(), this.sliderOption.step()
               );
               if (this.sliderOption.kind() == Kind.INTEGER) {
                  module.setValue(this.sliderOption.id(), Integer.toString((int)Math.round(value)));
               } else {
                  module.setValue(this.sliderOption.id(), String.format(Locale.ROOT, "%.2f", value));
               }

               return;
            }
         }
      }
   }

   private Scrollbar.Metrics scrollbarMetrics() {
      UiBounds body = this.bodyBounds();
      if (this.contentHeight <= body.height()) {
         return null;
      } else {
         UiBounds track = UiBounds.of(body.right() - 6, body.y(), 6, body.height());
         return Scrollbar.metrics(track, this.contentHeight, body.height(), this.scroll);
      }
   }

   private UiBounds bodyBounds() {
      if (this.lastBodyBounds != null) {
         return this.lastBodyBounds;
      } else {
         int y = this.panelY + 16 + 5 + 16 + 5;
         return UiBounds.of(this.panelX + 5, y, this.panelWidth - 10, Math.max(1, this.panelY + this.panelHeight - y - 10));
      }
   }

   private int maxScroll(int viewHeight) {
      return Math.max(0, this.contentHeight - Math.max(1, viewHeight));
   }

   private void clearDropdown() {
      if (this.dropdown != null) {
         this.dropdown.close();
      }

      this.dropdown = null;
      this.dropdownOption = null;
   }

   private BuiltinModules.AdminToolsModule adminTools() {
      return ModuleRegistry.get("admin-tools") instanceof BuiltinModules.AdminToolsModule adminTools ? adminTools : null;
   }

   private Setting<?, ?> option(Module module, String id) {
      if (module != null && id != null) {
         for (Setting<?, ?> option : module.settings()) {
            if (id.equals(option.id())) {
               return option;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private RiptideAdminToolsOverlay.AdminAction action(String label, String optionId) {
      return new RiptideAdminToolsOverlay.AdminAction(label, optionId, "");
   }

   private RiptideAdminToolsOverlay.AdminAction custom(String label, String command) {
      return new RiptideAdminToolsOverlay.AdminAction(label, null, command);
   }

   private String activeText(boolean active) {
      return active ? "Active" : "Idle";
   }

   private static int parseInt(String text, int fallback) {
      try {
         return Integer.parseInt(text);
      } catch (Exception var3) {
         return fallback;
      }
   }

   private static double parseDouble(String text, double fallback) {
      try {
         return Double.parseDouble(text);
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private record AdminAction(String label, String optionId, String command) {
   }

   private static final class Editing {
      private final Setting<?, ?> option;
      private String text;
      private int cursor;
      private int selectionAnchor = -1;

      private Editing(Setting<?, ?> option, String text, int cursor) {
         this.option = option;
         this.text = text == null ? "" : text;
         this.cursor = RiptideAdminToolsOverlay.clamp(cursor, 0, this.text.length());
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

   private static final class Flow {
      private final int x;
      private final int w;
      private int cursor;

      private Flow(int x, int y, int w) {
         this.x = x;
         this.cursor = y;
         this.w = Math.max(1, w);
      }

      private UiBounds take(int height) {
         int h = Math.max(0, height);
         UiBounds bounds = UiBounds.of(this.x, this.cursor, this.w, h);
         this.cursor += h + 5;
         return bounds;
      }

      private void gap(int gap) {
         this.cursor = this.cursor + Math.max(0, gap);
      }
   }

   private static final class FlowSizer {
      private int height;
      private boolean empty = true;

      private void add(int rowHeight) {
         if (rowHeight > 0) {
            if (!this.empty) {
               this.height += 5;
            }

            this.height += rowHeight;
            this.empty = false;
         }
      }

      private int height() {
         return this.height;
      }
   }

   private record Hit(RiptideAdminToolsOverlay.HitType type, UiBounds bounds, Setting<?, ?> option, String value) {
   }

   private static enum HitType {
      TAB,
      ITEM_TAB,
      BOOLEAN,
      TEXT,
      SLIDER,
      DROPDOWN,
      KEYBIND,
      CLEAR_KEYBIND,
      ACTION,
      SCROLLBAR;
   }
}
