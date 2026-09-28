package riptide.util.oresim;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class RiptideOreSimWorker {
   private final ThreadPoolExecutor executor;
   private RiptideOreSimWorker.Work active;

   RiptideOreSimWorker(String threadName) {
      this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
         Thread thread = new Thread(task, threadName);
         thread.setDaemon(true);
         thread.setPriority(1);
         return thread;
      });
   }

   synchronized void submit(Runnable task) {
      if (task == null) {
         throw new IllegalArgumentException("OreSim work must not be null");
      } else {
         this.cancelAllLocked();
         RiptideOreSimWorker.Work work = new RiptideOreSimWorker.Work();
         this.active = work;
         Future<?> future = this.executor.submit(() -> {
            try {
               task.run();
            } finally {
               this.completed(work);
            }
         });
         work.future = future;
         if (this.active != work) {
            future.cancel(true);
         }
      }
   }

   synchronized void cancelAll() {
      this.cancelAllLocked();
   }

   synchronized boolean isIdle() {
      return this.active == null && this.executor.getQueue().isEmpty();
   }

   private void cancelAllLocked() {
      RiptideOreSimWorker.Work work = this.active;
      this.active = null;
      this.executor.getQueue().clear();
      if (work != null && work.future != null) {
         work.future.cancel(true);
      }
   }

   private synchronized void completed(RiptideOreSimWorker.Work work) {
      if (this.active == work) {
         this.active = null;
      }
   }

   void shutdownNowForTest() {
      this.cancelAll();
      this.executor.shutdownNow();
   }

   private static final class Work {
      volatile Future<?> future;
   }
}
