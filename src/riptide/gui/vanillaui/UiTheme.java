package riptide.gui.vanillaui;

public final class UiTheme {
   private final UiColors colors = new UiColors();
   private final UiSpacing spacing = new UiSpacing();
   private final UiTypography typography = new UiTypography();

   public UiColors colors() {
      return this.colors;
   }

   public void refresh() {
      this.colors.recompute();
   }

   public UiSpacing spacing() {
      return this.spacing;
   }

   public UiTypography typography() {
      return this.typography;
   }
}
