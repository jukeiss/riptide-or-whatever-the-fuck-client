package riptide.util.oresim;

final class NearestPositionSelector {
   private final double[] distances;
   private final long[] positions;
   private final int[] states;
   private int size;
   private boolean truncated;

   NearestPositionSelector(int capacity) {
      if (capacity <= 0) {
         throw new IllegalArgumentException("capacity must be positive");
      } else {
         this.distances = new double[capacity];
         this.positions = new long[capacity];
         this.states = new int[capacity];
      }
   }

   boolean isFull() {
      return this.size == this.distances.length;
   }

   double farthestDistanceSquared() {
      if (this.size == 0) {
         throw new IllegalStateException("selector is empty");
      } else {
         return this.distances[0];
      }
   }

   boolean truncated() {
      return this.truncated;
   }

   void offer(double distanceSquared, long packedPosition, int stateId) {
      if (this.size < this.distances.length) {
         this.distances[this.size] = distanceSquared;
         this.positions[this.size] = packedPosition;
         this.states[this.size] = stateId;
         this.siftUp(this.size++);
      } else {
         this.truncated = true;
         if (compare(distanceSquared, packedPosition, stateId, this.distances[0], this.positions[0], this.states[0]) < 0) {
            this.distances[0] = distanceSquared;
            this.positions[0] = packedPosition;
            this.states[0] = stateId;
            this.siftDown(0, this.size);
         }
      }
   }

   int writeNearestFirst(long[] outPositions, int[] outStates) {
      if (outPositions.length >= this.size && outStates.length >= this.size) {
         int remaining = this.size;

         while (remaining > 1) {
            this.swap(0, remaining - 1);
            this.siftDown(0, --remaining);
         }

         System.arraycopy(this.positions, 0, outPositions, 0, this.size);
         System.arraycopy(this.states, 0, outStates, 0, this.size);
         return this.size;
      } else {
         throw new IllegalArgumentException("output arrays are smaller than the selection");
      }
   }

   private void siftUp(int index) {
      while (index > 0) {
         int parent = index - 1 >>> 1;
         if (this.compare(parent, index) >= 0) {
            return;
         }

         this.swap(parent, index);
         index = parent;
      }
   }

   private void siftDown(int index, int heapSize) {
      while (true) {
         int left = index * 2 + 1;
         if (left >= heapSize) {
            return;
         }

         int largest = left;
         int right = left + 1;
         if (right < heapSize && this.compare(right, left) > 0) {
            largest = right;
         }

         if (this.compare(index, largest) >= 0) {
            return;
         }

         this.swap(index, largest);
         index = largest;
      }
   }

   private int compare(int a, int b) {
      return compare(this.distances[a], this.positions[a], this.states[a], this.distances[b], this.positions[b], this.states[b]);
   }

   private static int compare(double distanceA, long positionA, int stateA, double distanceB, long positionB, int stateB) {
      int distanceOrder = Double.compare(distanceA, distanceB);
      if (distanceOrder != 0) {
         return distanceOrder;
      } else {
         int positionOrder = Long.compare(positionA, positionB);
         return positionOrder != 0 ? positionOrder : Integer.compare(stateA, stateB);
      }
   }

   private void swap(int a, int b) {
      double distance = this.distances[a];
      this.distances[a] = this.distances[b];
      this.distances[b] = distance;
      long position = this.positions[a];
      this.positions[a] = this.positions[b];
      this.positions[b] = position;
      int state = this.states[a];
      this.states[a] = this.states[b];
      this.states[b] = state;
   }
}
