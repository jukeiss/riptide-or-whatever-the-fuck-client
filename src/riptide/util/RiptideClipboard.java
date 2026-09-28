package riptide.util;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class RiptideClipboard {
   private static final long TOOL_TIMEOUT_MS = 3000L;
   private static final Map<String, Boolean> TOOL_AVAILABILITY = new ConcurrentHashMap<>();
   private static volatile boolean loggedFallback;
   private static volatile String shadow;
   private static volatile boolean awtBroken;

   private RiptideClipboard() {
   }

   public static String get() {
      for (String[] command : getCommands()) {
         if (toolAvailable(command[0])) {
            String out = runForOutput(command);
            if (out != null && !out.isEmpty()) {
               return out;
            }
         }
      }

      String awt = awtGet();
      if (awt != null && !awt.isEmpty()) {
         return awt;
      } else {
         String glfw = glfwGet();
         if (!glfw.isEmpty()) {
            return glfw;
         } else {
            String local = shadow;
            if (local != null) {
               logFallbackOnce("read");
               return local;
            } else {
               return "";
            }
         }
      }
   }

   public static void set(String text) {
      if (text == null) {
         text = "";
      }

      shadow = text;

      for (String[] command : setCommands()) {
         if (toolAvailable(command[0]) && runWithInput(command, text)) {
            return;
         }
      }

      if (!awtSet(text)) {
         logFallbackOnce("write");
         glfwSet(text);
      }
   }

   private static String[][] getCommands() {
      return isWaylandSession()
         ? new String[][]{{"wl-paste"}, {"xclip", "-selection", "clipboard", "-o"}, {"xsel", "--clipboard", "--output"}}
         : new String[][]{{"xclip", "-selection", "clipboard", "-o"}, {"xsel", "--clipboard", "--output"}, {"wl-paste"}};
   }

   private static String[][] setCommands() {
      return isWaylandSession()
         ? new String[][]{{"wl-copy"}, {"xclip", "-selection", "clipboard", "-i"}, {"xsel", "--clipboard", "--input"}}
         : new String[][]{{"xclip", "-selection", "clipboard", "-i"}, {"xsel", "--clipboard", "--input"}, {"wl-copy"}};
   }

   private static boolean isWaylandSession() {
      String sessionType = System.getenv("XDG_SESSION_TYPE");
      if (sessionType != null && sessionType.toLowerCase(Locale.ROOT).contains("wayland")) {
         return true;
      } else {
         String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
         return waylandDisplay != null && !waylandDisplay.isBlank();
      }
   }

   private static boolean toolAvailable(String tool) {
      return TOOL_AVAILABILITY.computeIfAbsent(tool, RiptideClipboard::probeTool);
   }

   private static boolean probeTool(String tool) {
      Process process = null;

      try {
         process = new ProcessBuilder("sh", "-c", "command -v " + tool).redirectOutput(Redirect.DISCARD).redirectError(Redirect.DISCARD).start();
         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return false;
         } else {
            return process.exitValue() == 0;
         }
      } catch (Exception var3) {
         if (process != null) {
            process.destroyForcibly();
         }

         return false;
      }
   }

   private static boolean runWithInput(String[] command, String text) {
      Process process = null;

      try {
         process = new ProcessBuilder(command).redirectOutput(Redirect.DISCARD).redirectError(Redirect.DISCARD).start();

         try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(text.getBytes(StandardCharsets.UTF_8));
         }

         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return false;
         } else {
            return process.exitValue() == 0;
         }
      } catch (Exception var8) {
         if (process != null) {
            process.destroyForcibly();
         }

         return false;
      }
   }

   private static String runForOutput(String[] command) {
      Process process = null;

      try {
         process = new ProcessBuilder(command).redirectError(Redirect.DISCARD).start();
         CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
            try {
               return process.getInputStream().readAllBytes();
            } catch (IOException var2) {
               return new byte[0];
            }
         });
         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return null;
         } else {
            byte[] out = reader.get(1L, TimeUnit.SECONDS);
            return process.exitValue() != 0 ? null : new String(out, StandardCharsets.UTF_8);
         }
      } catch (Exception var5) {
         if (process != null) {
            process.destroyForcibly();
         }

         return null;
      }
   }

   private static String awtGet() {
      if (awtUnavailable()) {
         return null;
      } else {
         FutureTask<String> task = new FutureTask<>(
            () -> Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor) instanceof String s ? s : null
         );
         runAwtTask(task);

         try {
            return task.get(3000L, TimeUnit.MILLISECONDS);
         } catch (TimeoutException var2) {
            awtBroken = true;
            task.cancel(true);
            return null;
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
            return null;
         } catch (Exception var4) {
            return null;
         }
      }
   }

   private static boolean awtSet(String text) {
      if (awtUnavailable()) {
         return false;
      } else {
         FutureTask<Boolean> task = new FutureTask<>(() -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
         });
         runAwtTask(task);

         try {
            return Boolean.TRUE.equals(task.get(3000L, TimeUnit.MILLISECONDS));
         } catch (TimeoutException var3) {
            awtBroken = true;
            task.cancel(true);
            return false;
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            return false;
         } catch (Exception var5) {
            return false;
         }
      }
   }

   private static boolean awtUnavailable() {
      if (awtBroken) {
         return true;
      } else {
         try {
            if (GraphicsEnvironment.isHeadless()) {
               awtBroken = true;
            }
         } catch (Throwable var1) {
            awtBroken = true;
         }

         return awtBroken;
      }
   }

   private static void runAwtTask(FutureTask<?> task) {
      Thread thread = new Thread(task, "riptide-clipboard-awt");
      thread.setDaemon(true);
      thread.start();
   }

   private static String glfwGet() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null) {
         long window = mc.getWindow().handle();
         if (window == 0L) {
            return "";
         } else if (mc.isSameThread()) {
            return glfwGetOnMain(window);
         } else {
            CompletableFuture<String> future = new CompletableFuture<>();
            mc.execute(() -> future.complete(glfwGetOnMain(window)));

            try {
               return future.get(2L, TimeUnit.SECONDS);
            } catch (Exception var5) {
               return "";
            }
         }
      } else {
         return "";
      }
   }

   private static void glfwSet(String text) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null) {
         long window = mc.getWindow().handle();
         if (window != 0L) {
            if (mc.isSameThread()) {
               glfwSetOnMain(window, text);
            } else {
               mc.execute(() -> glfwSetOnMain(window, text));
            }
         }
      }
   }

   private static String glfwGetOnMain(long window) {
      try {
         String value = GLFW.glfwGetClipboardString(window);
         return value != null ? value : "";
      } catch (Throwable var3) {
         return "";
      }
   }

   private static void glfwSetOnMain(long window, String text) {
      try {
         GLFW.glfwSetClipboardString(window, text);
      } catch (Throwable var4) {
      }
   }

   private static void logFallbackOnce(String operation) {
      if (!loggedFallback) {
         loggedFallback = true;
         riptide.RiptideClientAddon.LOG
            .info("[Riptide] Clipboard {}: no native tool (wl-copy/xclip/xsel) or AWT path worked; using GLFW/in-client fallback", operation);
      }
   }
}
