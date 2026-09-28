package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectHudPanelRenderer;

public final class RiptideQueueRenderer {
   private static final CompactTheme THEME = new CompactTheme();
   private static final long CACHE_REFRESH_NANOS = 50000000L;
   private static final int ENTRY_COLOR = -1581859;
   private static final int ENTRY_SENDING_COLOR = -601459;
   private static final int STATUS_COLOR = -6366544;
   private static final int ACTIVE_ACCENT_COLOR = -9184882;
   private static final Identifier MUTED_FONT = THEME.fontFor(UiTone.MUTED);
   private static final Identifier BODY_FONT = THEME.fontFor(UiTone.BODY);
   private static final Identifier LABEL_FONT = THEME.fontFor(UiTone.LABEL);
   private static final int MUTED_COLOR = THEME.color(UiTone.MUTED);
   private static final int HEADER_COLOR = THEME.color(UiTone.BODY);
   private static boolean cachedSending;
   private static String cachedModeStr = null;
   private static int cachedMaxLines = Integer.MIN_VALUE;
   private static int cachedPanelWidth = Integer.MIN_VALUE;
   private static int cachedPacketCount = Integer.MIN_VALUE;
   private static int cachedQueueRevision = Integer.MIN_VALUE;
   private static String cachedTitle = "";
   private static int cachedAccentColor;
   private static List<DirectHudPanelRenderer.Row> cachedRows = List.of();
   private static long lastCacheBuildNanos;

   private RiptideQueueRenderer() {
   }

   public static void render(GuiGraphicsExtractor context, Font textRenderer, int x, int y, int width, int maxLines) {
      renderStacked(context, textRenderer, x - 6, y - 8, width + 12, maxLines, true, true, true);
   }

   public static int measureStacked(Font textRenderer, int panelWidth, int maxLines) {
      if (textRenderer == null) {
         return 0;
      } else {
         ensureCache(textRenderer, panelWidth, maxLines);
         return DirectHudPanelRenderer.panelHeight(cachedRows.size());
      }
   }

   public static int renderStacked(
      GuiGraphicsExtractor context, Font textRenderer, int x, int y, int panelWidth, int maxLines, boolean topBorder, boolean bottomBorder, boolean rightBorder
   ) {
      if (textRenderer == null) {
         return 0;
      } else {
         ensureCache(textRenderer, panelWidth, maxLines);
         return DirectHudPanelRenderer.renderPreTrimmed(
            context, textRenderer, x, y, panelWidth, cachedTitle, cachedRows, cachedAccentColor, 0, true, rightBorder, topBorder, bottomBorder
         );
      }
   }

   private static void ensureCache(Font textRenderer, int panelWidth, int maxLines) {
      RiptideSharedState shared = RiptideSharedState.get();
      boolean isSending = shared.hasStaggeredPackets();
      String modeStr = shared.getQueueDisplayDelayMode() == RiptideSharedState.DelayMode.MS ? "ms" : "t";
      int queueRevision = shared.getQueueRenderRevision();
      long now = System.nanoTime();
      if (shouldRebuildCache(isSending, modeStr, maxLines, panelWidth, queueRevision, now)) {
         rebuildCache(textRenderer, shared.getQueueRenderSnapshot(isSending, maxLines), isSending, modeStr, maxLines, panelWidth, queueRevision, now);
      }
   }

   private static boolean shouldRebuildCache(boolean isSending, String modeStr, int maxLines, int panelWidth, int queueRevision, long now) {
      boolean layoutChanged = cachedSending != isSending
         || !Objects.equals(cachedModeStr, modeStr)
         || cachedMaxLines != maxLines
         || cachedPanelWidth != panelWidth;
      if (layoutChanged) {
         return true;
      } else if (cachedRows.isEmpty()) {
         return true;
      } else {
         return cachedQueueRevision == queueRevision ? false : now - lastCacheBuildNanos >= 50000000L;
      }
   }

   private static void rebuildCache(
      Font textRenderer,
      RiptideSharedState.QueueRenderSnapshot snapshot,
      boolean isSending,
      String modeStr,
      int maxLines,
      int panelWidth,
      int queueRevision,
      long now
   ) {
      List<RiptideSharedState.QueuedPacket> packets = snapshot.packets();
      int totalCount = snapshot.totalCount();
      int visibleCount = Math.min(packets.size(), maxLines);
      int contentWidth = panelWidth - 12;
      ArrayList<DirectHudPanelRenderer.Row> rows = new ArrayList<>(visibleCount + 2);
      rows.add(
         new DirectHudPanelRenderer.Row(
            UiText.trimToWidth(
               textRenderer,
               isSending ? "Sending " + totalCount + " left [" + modeStr + "]" : totalCount + " queued [" + modeStr + "]",
               contentWidth,
               BODY_FONT,
               -6366544
            ),
            BODY_FONT,
            -6366544
         )
      );
      if (packets.isEmpty()) {
         rows.add(
            new DirectHudPanelRenderer.Row(UiText.trimToWidth(textRenderer, "Queue empty", contentWidth, MUTED_FONT, MUTED_COLOR), MUTED_FONT, MUTED_COLOR)
         );
      } else {
         for (int i = 0; i < visibleCount; i++) {
            RiptideSharedState.QueuedPacket qp = packets.get(i);
            String simpleName = RiptidePacketNamer.getFriendlyName(qp.packet);
            String delayStr = qp.getDelay() > 0 ? " +" + qp.getDelay() + modeStr : "";
            String label = "#" + qp.getId() + " " + (qp.isExactReplay() ? "[Exact] " : "") + simpleName + delayStr;
            int color = isSending && i == 0 ? -601459 : -1581859;
            rows.add(new DirectHudPanelRenderer.Row(UiText.trimToWidth(textRenderer, label, contentWidth, BODY_FONT, color), BODY_FONT, color));
         }
      }

      cachedSending = isSending;
      cachedModeStr = modeStr;
      cachedMaxLines = maxLines;
      cachedPanelWidth = panelWidth;
      cachedPacketCount = totalCount;
      cachedQueueRevision = queueRevision;
      cachedTitle = UiText.trimToWidth(textRenderer, "PACKET QUEUE", contentWidth, LABEL_FONT, HEADER_COLOR);
      cachedAccentColor = isSending ? -9184882 : THEME.headerAccent();
      cachedRows = rows;
      lastCacheBuildNanos = now;
   }
}
