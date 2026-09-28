package riptide.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RiptideFarmReplantMemory<T> {
   private final int capacity;
   private final Map<Long, T> cells = new LinkedHashMap<>();

   public RiptideFarmReplantMemory(int capacity) {
      if (capacity <= 0) {
         throw new IllegalArgumentException("capacity must be positive");
      } else {
         this.capacity = capacity;
      }
   }

   public boolean hasRoomFor(long position) {
      return this.cells.containsKey(position) || this.cells.size() < this.capacity;
   }

   public boolean remember(long position, T value) {
      if (value == null) {
         throw new NullPointerException("value");
      } else if (!this.hasRoomFor(position)) {
         return false;
      } else {
         this.cells.put(position, value);
         return true;
      }
   }

   public T get(long position) {
      return this.cells.get(position);
   }

   public void remove(long position) {
      this.cells.remove(position);
   }

   public Collection<T> values() {
      return this.cells.values();
   }

   public int size() {
      return this.cells.size();
   }

   public boolean isEmpty() {
      return this.cells.isEmpty();
   }

   public void clear() {
      this.cells.clear();
   }
}
