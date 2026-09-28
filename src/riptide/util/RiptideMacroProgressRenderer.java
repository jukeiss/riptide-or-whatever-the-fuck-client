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
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.MacroStepPalette;

public final class RiptideMacroProgressRenderer {
   private static final CompactTheme THEME = new CompactTheme();
   private static final long CACHE_REFRESH_NANOS = 50000000L;
   private static final int STEP_NOW_COLOR = -9448565;
   private static final Identifier MUTED_FONT = THEME.fontFor(UiTone.MUTED);
   private static final Identifier BODY_FONT = THEME.fontFor(UiTone.BODY);
   private static final Identifier LABEL_FONT = THEME.fontFor(UiTone.LABEL);
   private static final int MUTED_COLOR = THEME.color(UiTone.MUTED);
   private static final int HEADER_COLOR = THEME.color(UiTone.BODY);
   private static String[] cachedRunNames = new String[0];
   private static int[] cachedRunSteps = new int[0];
   private static int[] cachedRunTotals = new int[0];
   private static int[] cachedRunCompleted = new int[0];
   private static int cachedRowsPerRun = Integer.MIN_VALUE;
   private static int cachedCurrent = Integer.MIN_VALUE;
   private static int cachedTotal = Integer.MIN_VALUE;
   private static int cachedScroll = Integer.MIN_VALUE;
   private static int cachedVisibleRows = Integer.MIN_VALUE;
   private static int cachedPanelWidth = Integer.MIN_VALUE;
   private static int cachedThemeGeneration = Integer.MIN_VALUE;
   private static String cachedMacroName = null;
   private static String cachedTitle = "";
   private static List<DirectHudPanelRenderer.Row> cachedRows = List.of();
   private static long lastCacheBuildNanos;
   private static Font lastEnsureFont;
   private static int lastEnsurePanelWidth = Integer.MIN_VALUE;
   private static int lastEnsureMaxLines = Integer.MIN_VALUE;
   private static long lastEnsureNanos;
   private static boolean lastEnsureResult;
   private static boolean ensureMemoArmed;

   private RiptideMacroProgressRenderer() {
   }

   public static void render(GuiGraphicsExtractor context, Font textRenderer, int x, int y, int width, int maxLines) {
      renderStacked(context, textRenderer, x - 6, y - 8, width + 12, maxLines, true, true, true);
   }

   public static int measureStacked(Font textRenderer, int panelWidth, int maxLines) {
      if (textRenderer == null) {
         return 0;
      } else {
         return !ensureCache(textRenderer, panelWidth, maxLines) ? 0 : DirectHudPanelRenderer.panelHeight(cachedRows.size());
      }
   }

   public static int renderStacked(
      GuiGraphicsExtractor context, Font textRenderer, int x, int y, int panelWidth, int maxLines, boolean topBorder, boolean bottomBorder, boolean rightBorder
   ) {
      return textRenderer != null && ensureCache(textRenderer, panelWidth, maxLines)
         ? DirectHudPanelRenderer.renderPreTrimmed(
            context, textRenderer, x, y, panelWidth, cachedTitle, cachedRows, THEME.headerAccent(), 0, true, rightBorder, topBorder, bottomBorder
         )
         : 0;
   }

   private static boolean ensureCache(Font textRenderer, int panelWidth, int maxLines) {
      if (ensureMemoArmed
         && textRenderer == lastEnsureFont
         && panelWidth == lastEnsurePanelWidth
         && maxLines == lastEnsureMaxLines
         && System.nanoTime() - lastEnsureNanos < 8000000L) {
         ensureMemoArmed = false;
         return lastEnsureResult;
      } else {
         boolean result = ensureCacheUncached(textRenderer, panelWidth, maxLines);
         lastEnsureFont = textRenderer;
         lastEnsurePanelWidth = panelWidth;
         lastEnsureMaxLines = maxLines;
         lastEnsureNanos = System.nanoTime();
         lastEnsureResult = result;
         ensureMemoArmed = true;
         return result;
      }
   }

   private static boolean ensureCacheUncached(Font textRenderer, int panelWidth, int maxLines) {
      List<MacroExecutor.MacroRunSnapshot> runs = MacroExecutor.getActiveRunSnapshots();
      if (runs.isEmpty()) {
         clearCache();
         return false;
      } else {
         MacroExecutor.MacroRunSnapshot primary = runs.get(0);
         RiptideMacro macro = primary.macro();
         if (macro != null && macro.actions != null && !macro.actions.isEmpty()) {
            int total = totalFor(primary, macro);
            int current = Math.max(0, primary.currentStepIndex());
            int rowsPerRun = Math.max(2, maxLines / Math.max(1, runs.size()));
            int visibleRows = visibleRowsFor(total, rowsPerRun);
            int scroll = scrollFor(current, total, rowsPerRun);
            long now = System.nanoTime();
            boolean runsMatch = runsMatchCache(runs, rowsPerRun);
            if (shouldRebuildCache(runsMatch, current, total, scroll, visibleRows, panelWidth, now)) {
               rebuildCache(textRenderer, runs, rowsPerRun, panelWidth, now);
            }

            return true;
         } else {
            clearCache();
            return false;
         }
      }
   }

   private static int totalFor(MacroExecutor.MacroRunSnapshot run, RiptideMacro macro) {
      int actionCount = macro != null && macro.actions != null ? macro.actions.size() : 0;
      return Math.max(run.totalSteps(), actionCount);
   }

   private static int visibleRowsFor(int total, int rowsPerRun) {
      return Math.min(rowsPerRun, total);
   }

   private static int scrollFor(int current, int total, int rowsPerRun) {
      return total <= rowsPerRun ? 0 : Math.max(0, Math.min(current - rowsPerRun / 2, total - rowsPerRun));
   }

   private static boolean runsMatchCache(List<MacroExecutor.MacroRunSnapshot> runs, int rowsPerRun) {
      if (rowsPerRun == cachedRowsPerRun && runs.size() == cachedRunNames.length) {
         for (int i = 0; i < runs.size(); i++) {
            MacroExecutor.MacroRunSnapshot run = runs.get(i);
            if (run.currentStepIndex() != cachedRunSteps[i]
               || run.totalSteps() != cachedRunTotals[i]
               || run.lastCompletedStep() != cachedRunCompleted[i]
               || !Objects.equals(run.name(), cachedRunNames[i])) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean shouldRebuildCache(boolean runsMatch, int current, int total, int scroll, int visibleRows, int panelWidth, long now) {
      boolean layoutChanged = !runsMatch
         || cachedTotal != total
         || cachedVisibleRows != visibleRows
         || cachedPanelWidth != panelWidth
         || cachedThemeGeneration != RiptideTheme.generation();
      if (layoutChanged) {
         return true;
      } else if (cachedRows.isEmpty()) {
         return true;
      } else {
         return cachedCurrent == current && cachedScroll == scroll ? false : now - lastCacheBuildNanos >= 50000000L;
      }
   }

   private static int stepNowColor() {
      return RiptideTheme.recolor(-9448565, RiptideTheme.Channel.ACCENT);
   }

   private static void rebuildCache(Font textRenderer, List<MacroExecutor.MacroRunSnapshot> runs, int rowsPerRun, int panelWidth, long now) {
      int contentWidth = panelWidth - 12;
      int nowColor = stepNowColor();
      ArrayList<DirectHudPanelRenderer.Row> rows = new ArrayList<>();

      for (MacroExecutor.MacroRunSnapshot run : runs) {
         RiptideMacro macro = run.macro();
         if (macro != null && macro.actions != null && !macro.actions.isEmpty()) {
            List<MacroAction> actions = macro.actions;
            int total = totalFor(run, macro);
            int current = Math.max(0, run.currentStepIndex());
            int visibleRows = visibleRowsFor(total, rowsPerRun);
            int scroll = scrollFor(current, total, rowsPerRun);
            rows.add(
               new DirectHudPanelRenderer.Row(
                  UiText.trimToWidth(textRenderer, run.name() + "  " + Math.min(current + 1, total) + "/" + total, contentWidth, LABEL_FONT, HEADER_COLOR),
                  LABEL_FONT,
                  HEADER_COLOR
               )
            );

            for (int i = scroll; i < scroll + visibleRows && i < total && i < actions.size(); i++) {
               MacroAction action = actions.get(i);
               int color = MacroStepPalette.colorFor(i, current, run.lastCompletedStep(), nowColor);
               rows.add(
                  new DirectHudPanelRenderer.Row(
                     UiText.trimToWidth(textRenderer, i + 1 + ". " + action.getDisplayName(), contentWidth, BODY_FONT, color), BODY_FONT, color
                  )
               );
            }

            if (run.status() != null && !run.status().isBlank()) {
               rows.add(
                  new DirectHudPanelRenderer.Row(UiText.trimToWidth(textRenderer, run.status(), contentWidth, MUTED_FONT, MUTED_COLOR), MUTED_FONT, MUTED_COLOR)
               );
            }
         }
      }

      MacroExecutor.MacroRunSnapshot primary = runs.get(0);
      int runCount = runs.size();
      cachedRunNames = new String[runCount];
      cachedRunSteps = new int[runCount];
      cachedRunTotals = new int[runCount];
      cachedRunCompleted = new int[runCount];

      for (int i = 0; i < runCount; i++) {
         MacroExecutor.MacroRunSnapshot runx = runs.get(i);
         cachedRunNames[i] = runx.name();
         cachedRunSteps[i] = runx.currentStepIndex();
         cachedRunTotals[i] = runx.totalSteps();
         cachedRunCompleted[i] = runx.lastCompletedStep();
      }

      int primaryTotal = totalFor(primary, primary.macro());
      int primaryCurrent = Math.max(0, primary.currentStepIndex());
      cachedRowsPerRun = rowsPerRun;
      cachedCurrent = primaryCurrent;
      cachedTotal = primaryTotal;
      cachedScroll = scrollFor(primaryCurrent, primaryTotal, rowsPerRun);
      cachedVisibleRows = visibleRowsFor(primaryTotal, rowsPerRun);
      cachedPanelWidth = panelWidth;
      cachedThemeGeneration = RiptideTheme.generation();
      cachedMacroName = runs.size() == 1 ? primary.name() : runs.size() + " macros";
      cachedTitle = UiText.trimToWidth(textRenderer, runs.size() == 1 ? "MACRO" : "MACROS", contentWidth, LABEL_FONT, HEADER_COLOR);
      cachedRows = rows;
      lastCacheBuildNanos = now;
   }

   private static void clearCache() {
      cachedRunNames = new String[0];
      cachedRunSteps = new int[0];
      cachedRunTotals = new int[0];
      cachedRunCompleted = new int[0];
      cachedRowsPerRun = Integer.MIN_VALUE;
      cachedCurrent = Integer.MIN_VALUE;
      cachedTotal = Integer.MIN_VALUE;
      cachedScroll = Integer.MIN_VALUE;
      cachedVisibleRows = Integer.MIN_VALUE;
      cachedPanelWidth = Integer.MIN_VALUE;
      cachedThemeGeneration = Integer.MIN_VALUE;
      cachedMacroName = null;
      cachedTitle = "";
      cachedRows = List.of();
      lastCacheBuildNanos = 0L;
   }
}
