package riptide.gui;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.gui.vanillaui.components.ProgressBar;
import riptide.modules.PackHideState;
import riptide.util.RiptideTheme;

public final class RiptideThemeApplyOverlay extends Overlay {
   private static final Object LOCK = new Object();
   private static final long MIN_VISIBLE_MS = 300L;
   private static final long MAX_VISIBLE_MS = 10000L;
   private static int totalJobs;
   private static int doneJobs;
   private static String currentTitle = "Applying Theme";
   private static String currentLabel = "";
   private static boolean shown;
   private static Runnable deferredAction;
   private final UiTextRenderer text = new UiTextRenderer(Minecraft.getInstance().font);
   private long shownAtMs = -1L;
   private float easedProgress;
   private int framesRendered;

   private RiptideThemeApplyOverlay() {
   }

   public static Runnable beginJob(String label) {
      return beginJob(null, label);
   }

   public static Runnable beginJob(String title, String label) {
      synchronized (LOCK) {
         if (doneJobs >= totalJobs) {
            totalJobs = 0;
            doneJobs = 0;
            currentTitle = title != null && !title.isBlank() ? title : "Applying Theme";
         }

         totalJobs++;
         currentLabel = label == null ? "" : label;
      }

      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         if (!shown && mc.gui.overlay() == null) {
            if (!PackHideState.isActive()) {
               shown = true;
               mc.gui.setOverlay(new RiptideThemeApplyOverlay());
            }
         }
      });
      AtomicBoolean completed = new AtomicBoolean();
      return () -> {
         if (completed.compareAndSet(false, true)) {
            synchronized (LOCK) {
               doneJobs = Math.min(totalJobs, doneJobs + 1);
            }
         }
      };
   }

   public static void runAfterShown(Runnable action) {
      if (PackHideState.isActive()) {
         Minecraft.getInstance().execute(action);
      } else {
         synchronized (LOCK) {
            deferredAction = action;
         }
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      Minecraft mc = Minecraft.getInstance();
      if (PackHideState.isActive()) {
         shown = false;
         if (mc.gui.overlay() == this) {
            mc.gui.setOverlay(null);
         }

         runDeferredNow();
      } else {
         long now = Util.getMillis();
         if (this.shownAtMs < 0L) {
            this.shownAtMs = now;
         }

         Screen screen = mc.gui.screen();
         if (screen != null) {
            try {
               screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, delta);
            } catch (Exception var28) {
            }
         }

         int width = graphics.guiWidth();
         int height = graphics.guiHeight();
         graphics.nextStratum();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), -1476395008);
         float target;
         String title;
         String label;
         boolean finished;
         synchronized (LOCK) {
            target = totalJobs == 0 ? 1.0F : (float)doneJobs / totalJobs;
            title = currentTitle;
            label = currentLabel;
            finished = doneJobs >= totalJobs;
         }

         this.easedProgress = Mth.clamp(this.easedProgress + (target - this.easedProgress) * (finished ? 0.35F : 0.15F), 0.0F, 1.0F);
         if (finished && this.easedProgress > 0.98F) {
            this.easedProgress = 1.0F;
         }

         int panelW = Math.min(260, width - 40);
         int panelH = 62;
         int px = (width - panelW) / 2;
         int py = (height - panelH) / 2;
         int border = RiptideTheme.recolor(-5035221, RiptideTheme.Channel.OUTLINE);
         int textColor = RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT);
         UiRenderer.frame(graphics, UiBounds.of(px, py, panelW, panelH), -183366899, border);
         this.text.drawCentered(graphics, title, UiBounds.of(px, py + 10, panelW, 9), textColor);
         if (!label.isBlank()) {
            this.text.drawCentered(graphics, label, UiBounds.of(px, py + 24, panelW, 9), -6647926);
         }

         int barX = px + 16;
         int barW = panelW - 32;
         int barY = py + panelH - 20;
         int barH = 8;
         ProgressBar.render(UiContexts.overlay(graphics, mc.font, mouseX, mouseY), UiBounds.of(barX, barY, barW, barH), this.easedProgress);
         boolean minElapsed = now - this.shownAtMs >= 300L;
         boolean timedOut = now - this.shownAtMs >= 10000L;
         if (finished && minElapsed && this.easedProgress >= 1.0F || timedOut) {
            shown = false;
            if (mc.gui.overlay() == this) {
               mc.gui.setOverlay(null);
            }
         }

         this.framesRendered++;
         if (this.framesRendered >= 2) {
            runDeferredNow();
         }
      }
   }

   private static void runDeferredNow() {
      Runnable action;
      synchronized (LOCK) {
         action = deferredAction;
         deferredAction = null;
      }

      if (action != null) {
         try {
            action.run();
         } catch (Throwable var3) {
         }
      }
   }

   public boolean isPausing() {
      return false;
   }
}
