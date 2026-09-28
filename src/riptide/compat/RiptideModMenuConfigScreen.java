package riptide.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.screen.RiptideAddonsScreen;
import riptide.gui.screen.RiptideThemeColorScreen;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.Dropdown;
import riptide.gui.vanillaui.components.Scrollbar;
import riptide.gui.vanillaui.components.UiText;
import riptide.modules.RiptideModule;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLinks;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public class RiptideModMenuConfigScreen extends Screen {
   private static final int TEXT_COLOR = -791321;
   private static final int MUTED_COLOR = -4743522;
   private static final int ROW_H = 18;
   private static final int ROW_GAP = 3;
   private static final int ROW_STEP = 21;
   private static final int HEADER_H = 22;
   private static final int FOOTER_H = 30;
   private final Screen parent;
   private final List<RiptideModMenuConfigScreen.Keybind> keybinds = new ArrayList<>();
   private int capturing = -1;
   private int scroll;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private final List<RiptideModMenuConfigScreen.Hit> hits = new ArrayList<>();
   private UiBounds contentViewport = UiBounds.of(0, 0, 0, 0);
   private int contentHeight;
   private UiContext ctx;
   private Dropdown prefixDropdown;

   public RiptideModMenuConfigScreen(Screen var1) {
      super(Component.literal("Riptide Client Settings"));
      this.parent = var1;
      RiptideConfig var2 = RiptideConfig.getGlobal();
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Module Menu", () -> var2.keybindModuleMenu, var1x -> {
         var2.keybindModuleMenu = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Load GUI", () -> var2.keybindLoadGui, var1x -> {
         var2.keybindLoadGui = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Flush Queue", () -> var2.keybindFlushQueue, var1x -> {
         var2.keybindFlushQueue = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Clear Queue", () -> var2.keybindClearQueue, var1x -> {
         var2.keybindClearQueue = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Toggle Logger", () -> var2.keybindToggleLogger, var1x -> {
         var2.keybindToggleLogger = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Toggle Send", () -> var2.keybindToggleSend, var1x -> {
         var2.keybindToggleSend = var1x;
         var2.save();
      }));
      this.keybinds.add(new RiptideModMenuConfigScreen.Keybind("Toggle Delay", () -> var2.keybindToggleDelay, var1x -> {
         var2.keybindToggleDelay = var1x;
         var2.save();
      }));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      int var5 = RiptideUiScale.toVirtualInt(var2);
      int var6 = RiptideUiScale.toVirtualInt(var3);
      RiptideUiScale.pushOverlayScale(var1);

      try {
         this.hits.clear();
         this.ctx = UiContexts.overlay(var1, this.font, var5, var6);
         int var7 = RiptideUiScale.getVirtualScreenWidth();
         int var8 = RiptideUiScale.getVirtualScreenHeight();
         UiRenderer.rect(var1, UiBounds.of(0, 0, var7, var8), -267843831);
         int var9 = Math.min(320, var7 - 20);
         int var10 = Math.min(var8 - 20, 52 + this.rows().size() * 21 + 8);
         var10 = Math.max(var10, 73);
         int var11 = (var7 - var9) / 2;
         int var12 = (var8 - var10) / 2;
         UiBounds var13 = UiBounds.of(var11, var12, var9, var10);
         UiRenderer.frame(var1, var13, -435549684, this.border());
         UiBounds var14 = UiBounds.of(var11, var12, var9, 22);
         UiRenderer.rect(var1, UiBounds.of(var11 + 1, var12 + 1, var9 - 2, 21), this.p(858003997, RiptideTheme.Channel.HEADER));
         this.drawCentered(var1, "Riptide Client Settings", UiAssets.FONT_TITLE, this.p(-791321, RiptideTheme.Channel.TEXT), var14);
         UiBounds var15 = UiBounds.of(var11, var13.bottom() - 30, var9, 30);
         UiBounds var16 = UiBounds.of(var11 + 8, var15.y() + 6, var9 - 16, 18);
         this.button(var1, var16, "Done", var16.contains(var5, var6), true);
         this.addHit(var16, this::onClose, null);
         int var17 = var12 + 22 + 4;
         int var18 = var15.y() - 4;
         this.contentViewport = UiBounds.of(var11 + 4, var17, var9 - 8, Math.max(0, var18 - var17));
         List var19 = this.rows();
         this.contentHeight = var19.size() * 21;
         this.scroll = this.clampScroll(this.scroll);
         boolean var20 = this.contentHeight > this.contentViewport.height();
         int var21 = this.contentViewport.width() - (var20 ? 6 : 0);
         UiScissorStack.global().push(var1, this.contentViewport);

         try {
            for (int var22 = 0; var22 < var19.size(); var22++) {
               int var23 = this.contentViewport.y() - this.scroll + var22 * 21;
               if (var23 + 18 >= this.contentViewport.y() && var23 <= this.contentViewport.bottom()) {
                  UiBounds var24 = UiBounds.of(this.contentViewport.x(), var23, var21, 18);
                  boolean var25 = var23 >= this.contentViewport.y() - 1 && var23 + 18 <= this.contentViewport.bottom() + 1;
                  ((RiptideModMenuConfigScreen.Row)var19.get(var22)).render(var1, var24, var5, var6, var25);
               }
            }
         } finally {
            UiScissorStack.global().pop(var1);
         }

         if (var20) {
            Scrollbar.Metrics var35 = this.scrollbarMetrics();
            Scrollbar.render(this.ctx, var35, var35.overTrack(var5, var6), this.scrollbarDragging);
            this.addHit(var35.track(), () -> this.startScrollbarDrag(var35, var6), null);
         }

         if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
            this.prefixDropdown.render(this.ctx);
         }
      } finally {
         RiptideUiScale.popOverlayScale(var1);
      }
   }

   private List<RiptideModMenuConfigScreen.Row> rows() {
      RiptideConfig var1 = RiptideConfig.getGlobal();
      RiptideModule var2 = RiptideModule.get();
      ArrayList var3 = new ArrayList();
      var3.add(this.toggleRow("Open Inside GUI", () -> var1.keybindInsideGui, var1x -> {
         var1.keybindInsideGui = var1x;
         var1.save();
      }));
      var3.add(this.toggleRow("Custom Main Menu", () -> var1.customMainMenu, var1x -> {
         var1.customMainMenu = var1x;
         var1.save();
      }));
      var3.add(this.toggleRow("Auto Probe Plugins", () -> var1.autoProbePlugins, var1x -> {
         var1.autoProbePlugins = var1x;
         var1.save();
      }));
      var3.add(this.toggleRow("InfiniChat", () -> var1.infiniChat, var1x -> {
         var1.infiniChat = var1x;
         var1.save();
      }));
      var3.add(this.toggleRow("Stop On Leave", () -> var1.stopMacroOnLeave, var1x -> {
         var1.stopMacroOnLeave = var1x;
         var1.save();
      }));
      var3.add(
         this.cycleRow(
            "Overlay Scale",
            RiptideUiScale.getOverlayScaleLabel(),
            () -> RiptideUiScale.setOverlayScaleMultiplier(RiptideUiScale.nextOverlayScaleMultiplier()),
            () -> RiptideUiScale.setOverlayScaleMultiplier(RiptideUiScale.previousOverlayScaleMultiplier())
         )
      );
      ArrayList var4 = new ArrayList<>(RiptideCompatManager.COMMAND_PREFIX_CHOICES);
      if (RiptideCompatManager.isMeteorAvailable()) {
         var4.remove(".");
      }

      String var5 = RiptideCompatManager.effectiveCommandPrefix();
      if (!var4.contains(var5)) {
         var5 = var4.isEmpty() ? "%" : (String)var4.get(0);
      }

      var3.add(this.prefixRow(var2, var4, var5));

      for (int var6 = 0; var6 < this.keybinds.size(); var6++) {
         var3.add(this.keybindRow(var6));
      }

      var3.add(this.actionRow("Theme Color", () -> this.minecraft.gui.setScreen(new RiptideThemeColorScreen(this))));
      var3.add(this.actionRow("Addons", () -> this.minecraft.gui.setScreen(new RiptideAddonsScreen(this))));
      var3.add(this.actionRow("Discord", () -> RiptideLinks.open("")));
      return var3;
   }

   private RiptideModMenuConfigScreen.Row toggleRow(String var1, RiptideModMenuConfigScreen.IntSupplierBool var2, Consumer<Boolean> var3) {
      return (var4, var5, var6, var7, var8) -> {
         boolean var9 = var2.get();
         boolean var10 = var8 && var5.contains(var6, var7);
         UiRenderer.frame(var4, var5, var10 ? this.p(1075845661, RiptideTheme.Channel.BUTTON) : 639113758, this.p(1717972012, RiptideTheme.Channel.OUTLINE));
         this.draw(var4, var1, UiAssets.FONT_LABEL, this.p(-791321, RiptideTheme.Channel.TEXT), var5.x() + 8, var5.y() + 5);
         UiBounds var11 = UiBounds.of(var5.right() - 34, var5.y() + 3, 30, var5.height() - 6);
         UiRenderer.frame(
            var4, var11, var9 ? this.p(-1474094563, RiptideTheme.Channel.TOGGLE) : 1074926358, var9 ? this.p(-39836, RiptideTheme.Channel.TOGGLE) : 1716936038
         );
         int var12 = var9 ? var11.right() - 12 : var11.x() + 2;
         UiRenderer.rect(var4, UiBounds.of(var12, var11.y() + 2, 10, var11.height() - 4), var9 ? this.p(-50373, RiptideTheme.Channel.TOGGLE) : -7697782);
         if (var8) {
            this.addHit(var5, () -> var3.accept(!var2.get()), null);
         }
      };
   }

   private RiptideModMenuConfigScreen.Row cycleRow(String var1, String var2, Runnable var3, Runnable var4) {
      return (var5, var6, var7, var8, var9) -> {
         boolean var10 = var9 && var6.contains(var7, var8);
         UiRenderer.frame(var5, var6, var10 ? this.p(1075845661, RiptideTheme.Channel.BUTTON) : 639113758, this.p(1717972012, RiptideTheme.Channel.OUTLINE));
         this.draw(var5, var1, UiAssets.FONT_LABEL, this.p(-791321, RiptideTheme.Channel.TEXT), var6.x() + 8, var6.y() + 5);
         int var11 = UiText.width(this.font, var2, UiAssets.FONT_BODY, -4743522);
         this.draw(var5, var2, UiAssets.FONT_BODY, this.p(-39836, RiptideTheme.Channel.ACCENT), var6.right() - var11 - 8, var6.y() + 5);
         if (var9) {
            this.addHit(var6, var3, var4);
         }
      };
   }

   private RiptideModMenuConfigScreen.Row prefixRow(RiptideModule var1, List<String> var2, String var3) {
      if (this.prefixDropdown == null) {
         this.prefixDropdown = new Dropdown(UiBounds.of(0, 0, 0, 0), var2, var3, var1::setCommandPrefix);
      } else {
         this.prefixDropdown.setOptions(var2);
         this.prefixDropdown.setSelected(var3);
      }

      return (var2x, var3x, var4, var5, var6) -> {
         UiRenderer.frame(var2x, var3x, 639113758, this.p(1717972012, RiptideTheme.Channel.OUTLINE));
         this.draw(var2x, "Command Prefix", UiAssets.FONT_LABEL, this.p(-791321, RiptideTheme.Channel.TEXT), var3x.x() + 8, var3x.y() + 5);
         int var7 = Math.min(110, var3x.width() / 2);
         UiBounds var8 = UiBounds.of(var3x.right() - var7 - 2, var3x.y() + 1, var7, var3x.height() - 2);
         this.prefixDropdown.setBounds(var8);
         boolean var9 = var6 && var8.contains(var4, var5);
         Dropdown.renderControl(this.ctx, var8, var3, var9, this.prefixDropdown.isOpen());
         if (var6) {
            this.addHit(var8, () -> {
               if (!this.prefixDropdown.isOpen()) {
                  this.prefixDropdown.open();
               }
            }, null);
         } else if (this.prefixDropdown.isOpen()) {
            this.prefixDropdown.close();
         }
      };
   }

   private RiptideModMenuConfigScreen.Row keybindRow(int var1) {
      return (var2, var3, var4, var5, var6) -> {
         RiptideModMenuConfigScreen.Keybind var7 = this.keybinds.get(var1);
         boolean var8 = var6 && var3.contains(var4, var5);
         boolean var9 = this.capturing == var1;
         UiRenderer.frame(
            var2,
            var3,
            var9 ? this.p(1428429341, RiptideTheme.Channel.ACCENT) : (var8 ? this.p(1075845661, RiptideTheme.Channel.BUTTON) : 639113758),
            var9 ? this.p(-39836, RiptideTheme.Channel.ACCENT) : this.p(1717972012, RiptideTheme.Channel.OUTLINE)
         );
         this.draw(var2, var7.label(), UiAssets.FONT_LABEL, this.p(-791321, RiptideTheme.Channel.TEXT), var3.x() + 8, var3.y() + 5);
         String var10 = var9 ? "press a key..." : RiptideBindUtil.getBindName(var7.getter().getAsInt());
         int var11 = UiText.width(this.font, var10, UiAssets.FONT_BODY, -4743522);
         this.draw(var2, var10, UiAssets.FONT_BODY, this.p(-4743522, RiptideTheme.Channel.TEXT), var3.right() - var11 - 8, var3.y() + 5);
         if (var6) {
            this.addHit(var3, () -> this.startCapture(var1), null);
         }
      };
   }

   private RiptideModMenuConfigScreen.Row actionRow(String var1, Runnable var2) {
      return (var3, var4, var5, var6, var7) -> {
         this.button(var3, var4, var1, var7 && var4.contains(var5, var6), false);
         if (var7) {
            this.addHit(var4, var2, null);
         }
      };
   }

   private RiptideModMenuConfigScreen.Row splitRow(String var1, Runnable var2, String var3, Runnable var4) {
      return (var5, var6, var7, var8, var9) -> {
         int var10 = (var6.width() - 3) / 2;
         UiBounds var11 = UiBounds.of(var6.x(), var6.y(), var10, var6.height());
         UiBounds var12 = UiBounds.of(var6.x() + var10 + 3, var6.y(), var6.width() - var10 - 3, var6.height());
         this.button(var5, var11, var1, var9 && var11.contains(var7, var8), false);
         this.button(var5, var12, var3, var9 && var12.contains(var7, var8), false);
         if (var9) {
            this.addHit(var11, var2, null);
            this.addHit(var12, var4, null);
         }
      };
   }

   private void addHit(UiBounds var1, Runnable var2, Runnable var3) {
      this.hits.add(new RiptideModMenuConfigScreen.Hit(var1, var2, var3));
   }

   private int clampScroll(int var1) {
      int var2 = Math.max(0, this.contentHeight - this.contentViewport.height());
      return Math.max(0, Math.min(var1, var2));
   }

   private Scrollbar.Metrics scrollbarMetrics() {
      UiBounds var1 = UiBounds.of(this.contentViewport.right() - 6, this.contentViewport.y(), 6, this.contentViewport.height());
      return Scrollbar.metrics(var1, this.contentHeight, this.contentViewport.height(), this.scroll);
   }

   private void startScrollbarDrag(Scrollbar.Metrics var1, int var2) {
      this.scrollbarDragging = true;
      this.scrollbarGrabOffset = var1.overThumb(var1.track().x() + 1, var2) ? var2 - var1.thumb().y() : var1.thumb().height() / 2;
      this.scroll = this.clampScroll(Scrollbar.scrollFromMouse(var1, var2, this.scrollbarGrabOffset));
   }

   private int p(int var1, RiptideTheme.Channel var2) {
      return RiptideTheme.recolor(var1, var2);
   }

   private int border() {
      return this.p(-5035221, RiptideTheme.Channel.OUTLINE);
   }

   private void button(GuiGraphicsExtractor var1, UiBounds var2, String var3, boolean var4, boolean var5) {
      Button.render(this.ctx, var2, var3, var5 ? Button.Tone.PRIMARY : Button.Tone.NORMAL, var4, false);
   }

   private void drawCentered(GuiGraphicsExtractor var1, String var2, Identifier var3, int var4, UiBounds var5) {
      int var6 = UiText.width(this.font, var2, var3, var4);
      this.draw(var1, var2, var3, var4, var5.x() + (var5.width() - var6) / 2, var5.y() + (var5.height() - 8) / 2);
   }

   private void draw(GuiGraphicsExtractor var1, String var2, Identifier var3, int var4, int var5, int var6) {
      UiText.draw(var1, this.font, var2, var3, var4, var5, var6, false);
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      int var3 = RiptideUiScale.toVirtualInt(var1.x());
      int var4 = RiptideUiScale.toVirtualInt(var1.y());
      boolean var5 = var1.button() == 1;
      if (!var5 && var1.button() != 0) {
         return true;
      } else if (this.capturing >= 0) {
         this.applyCapture(RiptideBindUtil.encodeMouseButton(var1.button()));
         return true;
      } else if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         this.prefixDropdown.mouseClicked(var3, var4, var1.button());
         return true;
      } else {
         for (int var6 = this.hits.size() - 1; var6 >= 0; var6--) {
            RiptideModMenuConfigScreen.Hit var7 = this.hits.get(var6);
            if (var7.bounds().contains(var3, var4)) {
               Runnable var8 = var5 ? var7.onRight() : var7.onLeft();
               if (var8 != null) {
                  var8.run();
               }

               return true;
            }
         }

         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent var1) {
      this.scrollbarDragging = false;
      if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         this.prefixDropdown.mouseReleased(RiptideUiScale.toVirtualInt(var1.x()), RiptideUiScale.toVirtualInt(var1.y()), var1.button());
         return true;
      } else {
         return super.mouseReleased(var1);
      }
   }

   public boolean mouseDragged(MouseButtonEvent var1, double var2, double var4) {
      if (this.scrollbarDragging) {
         this.scroll = this.clampScroll(Scrollbar.scrollFromMouse(this.scrollbarMetrics(), RiptideUiScale.toVirtualInt(var1.y()), this.scrollbarGrabOffset));
         return true;
      } else if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         this.prefixDropdown.mouseDragged(RiptideUiScale.toVirtualInt(var1.x()), RiptideUiScale.toVirtualInt(var1.y()), var1.button(), var2, var4);
         return true;
      } else {
         return super.mouseDragged(var1, var2, var4);
      }
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         this.prefixDropdown.mouseScrolled(RiptideUiScale.toVirtualInt(var1), RiptideUiScale.toVirtualInt(var3), var7);
         return true;
      } else {
         if (var7 != 0.0) {
            this.scroll = this.clampScroll(this.scroll - (int)Math.signum(var7) * 21 * 2);
         }

         return true;
      }
   }

   public boolean keyPressed(KeyEvent var1) {
      if (this.capturing >= 0) {
         this.applyCapture(var1.key() == 256 ? -1 : var1.key());
         return true;
      } else if (var1.key() == 256) {
         if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
            this.prefixDropdown.close();
            return true;
         } else {
            this.onClose();
            return true;
         }
      } else {
         return super.keyPressed(var1);
      }
   }

   private void startCapture(int var1) {
      this.capturing = var1;
   }

   private void applyCapture(int var1) {
      if (this.capturing >= 0 && this.capturing < this.keybinds.size()) {
         this.keybinds.get(this.capturing).setter().accept(var1);
         this.capturing = -1;
      }
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private record Hit(UiBounds bounds, Runnable onLeft, Runnable onRight) {
   }

   private interface IntSupplierBool {
      boolean get();
   }

   private record Keybind(String label, IntSupplier getter, IntConsumer setter) {
   }

   private interface Row {
      void render(GuiGraphicsExtractor var1, UiBounds var2, int var3, int var4, boolean var5);
   }
}
