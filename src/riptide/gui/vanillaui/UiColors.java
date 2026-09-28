package riptide.gui.vanillaui;

import riptide.util.RiptideTheme;

public final class UiColors {
   public int screenScrim;
   public int window;
   public int windowStrong;
   public int header;
   public int headerHover;
   public int row;
   public int rowAlt;
   public int rowHover;
   public int field;
   public int fieldFocused;
   public int border;
   public int borderSoft;
   public int buttonBorder;
   public int hairline;
   public int hoverVeil;
   public int accent;
   public int accentDark;
   public int accentSoft;
   public int success;
   public int successSoft;
   public int text;
   public int muted;
   public int disabled;
   public int bad;

   public UiColors() {
      this.recompute();
   }

   public void recompute() {
      this.screenScrim = 1711276032;
      this.window = -670364656;
      this.windowStrong = -301331955;
      this.header = -266855904;
      this.headerHover = -14407630;
      this.row = -1340992486;
      this.rowAlt = -1810490847;
      this.rowHover = -920837589;
      this.field = -653126631;
      this.fieldFocused = -400941530;
      this.border = RiptideTheme.recolor(-3258039, RiptideTheme.Channel.OUTLINE);
      this.borderSoft = RiptideTheme.recolor(-861980102, RiptideTheme.Channel.OUTLINE);
      this.buttonBorder = RiptideTheme.recolor(-525836706, RiptideTheme.Channel.OUTLINE);
      this.hairline = 738197503;
      this.hoverVeil = 285212671;
      this.accent = RiptideTheme.recolor(-50373, RiptideTheme.Channel.ACCENT);
      this.accentDark = RiptideTheme.recolor(-7397596, RiptideTheme.Channel.ACCENT);
      this.accentSoft = RiptideTheme.recolor(1157577531, RiptideTheme.Channel.ACCENT);
      this.success = RiptideTheme.recolor(-12588930, RiptideTheme.Channel.SUCCESS);
      this.successSoft = RiptideTheme.recolor(1715464318, RiptideTheme.Channel.SUCCESS);
      this.text = RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT);
      this.muted = RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
      this.disabled = RiptideTheme.recolor(-9016726, RiptideTheme.Channel.TEXT);
      this.bad = RiptideTheme.recolor(-43691, RiptideTheme.Channel.DANGER);
   }
}
