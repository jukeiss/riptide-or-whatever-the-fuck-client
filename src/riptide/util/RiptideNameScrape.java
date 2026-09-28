package riptide.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.ProgressBar;
import riptide.modules.PackHideState;

public final class RiptideNameScrape {
   private static final int MAX_NAMES = 120000;
   private static final int MAX_QUERIES = 150000;
   private static final char[] SPINNER = new char[]{'|', '/', '-', '\\'};
   private static volatile boolean active;
   private static volatile boolean paused;
   private static volatile boolean stopped;
   private static volatile int foundCount;
   private static volatile int queriesDone;
   private static volatile int limit;
   private static volatile boolean userLimited;
   private static volatile boolean deepMode;
   private static volatile int serverOnline;
   private static volatile String currentStatus = "";
   private static volatile ClientPacketListener activeConnection;
   private static final RiptideNameHarvest.Control CONTROL = new RiptideNameHarvest.Control() {
      @Override
      public boolean cancelled() {
         return RiptideNameScrape.isCancelled();
      }

      @Override
      public boolean paused() {
         return RiptideNameScrape.paused && RiptideNameScrape.active;
      }

      @Override
      public int limit() {
         return RiptideNameScrape.limit;
      }

      @Override
      public int maxQueries() {
         return 150000;
      }

      @Override
      public boolean deepSweep() {
         return RiptideNameScrape.deepMode;
      }

      @Override
      public void onProgress(int names, int queries, int maxQueries, String status) {
         RiptideNameScrape.foundCount = names;
         RiptideNameScrape.queriesDone = queries;
         RiptideNameScrape.currentStatus = status == null ? "" : status;
      }
   };

   private RiptideNameScrape() {
   }

   public static boolean isActive() {
      return active;
   }

   public static boolean isPaused() {
      return active && paused;
   }

   public static void start(int requestedLimit) {
      start(requestedLimit, false);
   }

   public static synchronized void start(int requestedLimit, boolean deep) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.getConnection() == null || mc.player == null) {
         RiptideClientMessaging.sendPrefixed("Namescrape: join a server first.");
      } else if (active) {
         RiptideClientMessaging.sendPrefixed("Namescrape: already running. Use stop / pause / resume.");
      } else {
         active = true;
         paused = false;
         stopped = false;
         deepMode = deep;
         foundCount = 0;
         queriesDone = 0;
         currentStatus = "Starting...";
         userLimited = requestedLimit > 0;
         limit = userLimited ? Math.min(requestedLimit, 120000) : 120000;
         serverOnline = readServerOnline(mc);
         ClientPacketListener connection = mc.getConnection();
         activeConnection = connection;
         String self = mc.player.getName().getString();
         LinkedHashMap<String, String> names = RiptideNameHarvest.instantNames(mc, self);
         foundCount = names.size();
         List<RiptideNameHarvest.Vector> vectors = RiptideNameHarvest.discoverVectors(connection);
         RiptideBackgroundTasks.runTracked("namescrape", () -> run(names, connection, self, vectors));
      }
   }

   public static void pause() {
      if (!active) {
         RiptideClientMessaging.sendPrefixed("Namescrape: nothing to pause.");
      } else {
         paused = true;
         RiptideClientMessaging.sendPrefixed("Namescrape: paused (" + foundCount + " so far). Use resume.");
      }
   }

   public static void resume() {
      if (!active) {
         RiptideClientMessaging.sendPrefixed("Namescrape: nothing to resume.");
      } else {
         paused = false;
         RiptideClientMessaging.sendPrefixed("Namescrape: resumed.");
      }
   }

   public static void stop() {
      if (!active) {
         RiptideClientMessaging.sendPrefixed("Namescrape: nothing to stop.");
      } else {
         stopped = true;
         paused = false;
         active = false;
         RiptideClientMessaging.sendPrefixed("Namescrape: stopping, copying " + foundCount + " so far...");
      }
   }

   private static void run(LinkedHashMap<String, String> names, ClientPacketListener connection, String self, List<RiptideNameHarvest.Vector> vectors) {
      try {
         RiptideNameHarvest.sweep(connection, names, self, vectors, CONTROL);
         List<String> result = new ArrayList<>(names.values());
         if (userLimited && result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
         }

         finish(result);
      } catch (Throwable var5) {
         finishMessage("Namescrape failed.", "", 0);
      }
   }

   private static boolean isCancelled() {
      if (active && !PackHideState.isHardLocked()) {
         Minecraft mc = Minecraft.getInstance();
         return mc == null || mc.getConnection() != activeConnection;
      } else {
         return true;
      }
   }

   private static void finish(List<String> result) {
      String joined = String.join("\n", result);
      int bytes = joined.getBytes(StandardCharsets.UTF_8).length;
      String kb = String.format(Locale.ROOT, "%.1f", bytes / 1024.0);
      if (result.isEmpty()) {
         finishMessage("Namescrape: no names found.", "", 0);
      } else {
         finishMessage(
            (stopped ? "Namescrape stopped: " : "Namescrape: ") + String.format(Locale.US, "%,d", result.size()) + " names (" + kb + " KB) copied.",
            joined,
            result.size()
         );
      }
   }

   private static void finishMessage(String message, String clipboard, int count) {
      Minecraft mc = Minecraft.getInstance();
      Runnable done = () -> {
         active = false;
         paused = false;
         activeConnection = null;
         if (!PackHideState.shouldSuppressClientOutput()) {
            if (count > 0 && mc != null && mc.keyboardHandler != null && !clipboard.isEmpty()) {
               mc.keyboardHandler.setClipboard(clipboard);
            }

            RiptideNotifications.success(message);
         }
      };
      if (mc != null) {
         mc.execute(done);
      } else {
         done.run();
      }
   }

   public static void render(GuiGraphicsExtractor graphics) {
      if (active) {
         Minecraft mc = Minecraft.getInstance();
         if (graphics != null && mc != null && mc.font != null) {
            UiContext ctx = UiContexts.overlay(graphics, mc.font, -1, -1);
            UiColors colors = ctx.theme().colors();
            int names = foundCount;
            char spin = SPINNER[(int)(System.currentTimeMillis() / 120L % SPINNER.length)];
            int width = 244;
            int height = 46;
            int pad = 9;
            int x = Math.max(4, (RiptideUiScale.getVirtualScreenWidth() - width) / 2);
            int y = 6;
            UiRenderer.frame(graphics, UiBounds.of(x, y, width, height), colors.windowStrong, colors.border);
            String title = (paused ? "|| " : spin + " ") + (paused ? "Namescrape paused" : "Scraping names");
            int target = targetTotal();
            String count = fmt(names) + (target > 0 ? " / " + fmt(target) : "");
            ctx.text().draw(graphics, title, x + pad, y + 6, colors.text);
            int cw = ctx.text().width(count);
            ctx.text().draw(graphics, count, x + width - pad - cw, y + 6, colors.muted);
            ProgressBar.render(ctx, UiBounds.of(x + pad, y + 20, width - pad * 2, 8), progress());
            String status = currentStatus != null && !currentStatus.isBlank() ? currentStatus : "Starting...";
            ctx.text().draw(graphics, clip(ctx, status, width - pad * 2), x + pad, y + 33, colors.muted);
         }
      }
   }

   private static int targetTotal() {
      return userLimited ? limit : serverOnline;
   }

   private static double progress() {
      int target = targetTotal();
      return target > 0 ? Math.min(1.0, (double)foundCount / target) : foundCount / (foundCount + 1500.0);
   }

   private static int readServerOnline(Minecraft mc) {
      try {
         ServerData data = mc.getCurrentServer();
         if (data != null && data.players != null) {
            return Math.max(0, data.players.online());
         }
      } catch (Throwable var2) {
      }

      return 0;
   }

   private static String fmt(int n) {
      return String.format(Locale.US, "%,d", n);
   }

   private static String clip(UiContext ctx, String text, int maxWidth) {
      if (ctx.text().width(text) <= maxWidth) {
         return text;
      } else {
         String ellipsis = "...";
         int limit = maxWidth - ctx.text().width(ellipsis);
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < text.length() && ctx.text().width(sb.toString() + text.charAt(i)) <= limit; i++) {
            sb.append(text.charAt(i));
         }

         return sb + ellipsis;
      }
   }
}
