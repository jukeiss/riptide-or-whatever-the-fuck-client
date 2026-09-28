package riptide.util.multi;

import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.world.phys.Vec3;

final class MultiPilotTruth {
   private static final int MAX_SENT = 64;
   private static final long MAX_AGE_MS = 4000L;
   private static final double ACK_DISTANCE_SQR = 0.04000000000000001;
   private static final double CHANGE_DISTANCE_SQR = 1.0E-8;
   private static final long TRANSITION_GRACE_MS = 750L;
   private final ArrayDeque<MultiPilotTruth.Sent> sent = new ArrayDeque<>();
   private Vec3 lastObserved;
   private long rebaseAfter;
   private boolean pendingUnexpected;

   synchronized void reset(Vec3 observed, long now) {
      this.sent.clear();
      this.lastObserved = observed;
      this.rebaseAfter = now + 750L;
      this.pendingUnexpected = false;
   }

   synchronized void recordSent(Vec3 position, long now) {
      if (position != null) {
         this.sent.addLast(new MultiPilotTruth.Sent(position, now));

         while (this.sent.size() > 64) {
            this.sent.removeFirst();
         }

         this.prune(now);
      }
   }

   synchronized MultiPilotTruth.Result observe(Vec3 observed, long now) {
      if (observed == null) {
         return MultiPilotTruth.Result.UNCHANGED;
      } else if (this.lastObserved != null && this.lastObserved.distanceToSqr(observed) <= 1.0E-8) {
         this.prune(now);
         if (this.pendingUnexpected && now >= this.rebaseAfter) {
            this.pendingUnexpected = false;
            this.sent.clear();
            return MultiPilotTruth.Result.REBASE;
         } else {
            return MultiPilotTruth.Result.UNCHANGED;
         }
      } else {
         this.lastObserved = observed;
         this.prune(now);
         MultiPilotTruth.Sent matched = null;
         Iterator<MultiPilotTruth.Sent> it = this.sent.descendingIterator();

         while (it.hasNext()) {
            MultiPilotTruth.Sent candidate = it.next();
            if (candidate.position().distanceToSqr(observed) <= 0.04000000000000001) {
               matched = candidate;
               break;
            }
         }

         if (matched == null) {
            if (now < this.rebaseAfter) {
               this.pendingUnexpected = true;
               return MultiPilotTruth.Result.UNCHANGED;
            } else {
               this.pendingUnexpected = false;
               this.sent.clear();
               return MultiPilotTruth.Result.REBASE;
            }
         } else {
            this.pendingUnexpected = false;

            while (!this.sent.isEmpty()) {
               MultiPilotTruth.Sent first = this.sent.removeFirst();
               if (first == matched) {
                  break;
               }
            }

            return MultiPilotTruth.Result.ACKNOWLEDGED;
         }
      }
   }

   private void prune(long now) {
      while (!this.sent.isEmpty() && now - this.sent.peekFirst().at() > 4000L) {
         this.sent.removeFirst();
      }
   }

   static enum Result {
      UNCHANGED,
      ACKNOWLEDGED,
      REBASE;
   }

   private record Sent(Vec3 position, long at) {
   }
}
