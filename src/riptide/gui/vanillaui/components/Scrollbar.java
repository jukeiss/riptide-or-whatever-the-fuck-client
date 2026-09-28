package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class Scrollbar {
   private static final int THUMB_GRAY = -6578008;
   private static final int THUMB_GRAY_ACTIVE = -3551788;

   private Scrollbar() {
   }

   public static Scrollbar.Metrics metrics(UiBounds track, int contentHeight, int viewHeight, int scroll) {
      int max = Math.max(0, contentHeight - Math.max(0, viewHeight));
      if (max > 0 && track.height() > 0) {
         int thumbH = Math.max(12, (int)Math.round(track.height() * ((double)viewHeight / Math.max(viewHeight, contentHeight))));
         thumbH = Math.min(track.height(), thumbH);
         int thumbY = track.y() + (int)Math.round((track.height() - thumbH) * ((double)scroll / max));
         return new Scrollbar.Metrics(track, UiBounds.of(track.x(), thumbY, track.width(), thumbH), max);
      } else {
         return new Scrollbar.Metrics(track, UiBounds.of(track.x(), track.y(), track.width(), track.height()), 0);
      }
   }

   public static void render(UiContext context, Scrollbar.Metrics metrics, boolean dragging) {
      render(context, metrics, false, dragging);
   }

   public static void render(UiContext context, Scrollbar.Metrics metrics, boolean hovered, boolean dragging) {
      if (metrics != null && metrics.maxScroll() > 0) {
         UiRenderer.rect(context.graphics(), metrics.track(), 788529151);
         UiBounds thumb = metrics.thumb();
         boolean active = hovered || dragging;
         int width = active ? thumb.width() : Math.max(2, thumb.width() - 2);
         int x = thumb.x() + (thumb.width() - width) / 2;
         UiRenderer.rect(context.graphics(), UiBounds.of(x, thumb.y(), width, thumb.height()), active ? -3551788 : -6578008);
      }
   }

   public static int scrollFromMouse(Scrollbar.Metrics metrics, int mouseY, int grabOffset) {
      if (metrics != null && metrics.maxScroll() > 0) {
         int available = Math.max(1, metrics.track().height() - metrics.thumb().height());
         int y = Math.max(0, Math.min(available, mouseY - grabOffset - metrics.track().y()));
         return (int)Math.round(metrics.maxScroll() * ((double)y / available));
      } else {
         return 0;
      }
   }

   public record Metrics(UiBounds track, UiBounds thumb, int maxScroll) {
      public boolean overTrack(int x, int y) {
         return this.track.contains(x, y);
      }

      public boolean overThumb(int x, int y) {
         return this.thumb.contains(x, y);
      }
   }
}
