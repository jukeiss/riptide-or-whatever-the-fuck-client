package riptide.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class RiptideTraceLog {
   private static final int QUEUE_CAPACITY = 4096;
   private static final BlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(4096);
   private static volatile boolean started;
   private static volatile long dropped;

   private RiptideTraceLog() {
   }

   public static void println(String line) {
      if (line != null) {
         start();
         if (!QUEUE.offer(line)) {
            dropped++;
         }
      }
   }

   private static void start() {
      if (!started) {
         synchronized (RiptideTraceLog.class) {
            if (!started) {
               started = true;
               Thread writer = new Thread(RiptideTraceLog::drain, "riptide-trace-log");
               writer.setDaemon(true);
               writer.setPriority(1);
               writer.start();
            }
         }
      }
   }

   private static void drain() {
      while (true) {
         try {
            System.out.println(QUEUE.take());
         } catch (InterruptedException var1) {
            Thread.currentThread().interrupt();
            return;
         } catch (RuntimeException var2) {
         }
      }
   }
}
