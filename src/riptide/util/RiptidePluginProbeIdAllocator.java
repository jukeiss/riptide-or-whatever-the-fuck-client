package riptide.util;

final class RiptidePluginProbeIdAllocator {
   static final int BLOCK_SIZE = 10000;
   private static final int FIRST_ID = 1000000;
   private int nextId = 1000000;

   int allocateBlock() {
      if (this.nextId > 1999980000) {
         this.nextId = 1000000;
      }

      int base = this.nextId;
      this.nextId += 10000;
      return base;
   }
}
