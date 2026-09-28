package riptide.util.custommenu;

import net.minecraft.network.protocol.Packet;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.custommenu.CustomMenuEvent;
import riptide.api.custommenu.CustomMenuSnapshot;

public final class CustomMenuSession {
   private volatile long generation;
   private volatile CustomMenuSnapshot current;

   public boolean accept(Packet<?> packet, String phase) {
      return !CustomMenuAdapterRegistry.acceptsInbound(packet) ? false : this.acceptInterested(packet, phase);
   }

   public boolean acceptInterested(Packet<?> packet, String phase) {
      CustomMenuEvent event = CustomMenuAdapterRegistry.inspect(packet, phase);
      if (event.type() == CustomMenuEvent.Type.NONE) {
         return false;
      } else {
         synchronized (this) {
            if (event.type() == CustomMenuEvent.Type.CLEAR) {
               this.current = null;
               this.generation++;
            } else if (event.snapshot() != null) {
               this.current = event.snapshot().withConnectionState(phase, ++this.generation);
            }

            return true;
         }
      }
   }

   public CustomMenuSnapshot current() {
      return this.current;
   }

   public long generation() {
      return this.generation;
   }

   public synchronized void consume(CustomMenuSnapshot expected, CustomMenuSnapshot replacement) {
      if (expected == null || this.current == null || this.current.generation() == expected.generation()) {
         this.current = replacement == null ? null : replacement.withConnectionState(expected == null ? "" : expected.phase(), ++this.generation);
      }
   }

   public synchronized void consumeAt(long seenGeneration, CustomMenuSnapshot replacement, String phase) {
      if (this.generation == seenGeneration) {
         this.generation++;
         this.current = replacement == null ? null : replacement.withConnectionState(phase == null ? "" : phase, this.generation);
      }
   }

   public void clear() {
      if (this.current != null) {
         synchronized (this) {
            if (this.current != null) {
               this.current = null;
               this.generation++;
            }
         }
      }
   }
}
