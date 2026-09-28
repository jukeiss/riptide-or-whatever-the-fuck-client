package riptide.util.mm.relay;

import java.util.function.Consumer;

public interface Relay {
   String name();

   RelayStatus status();

   void publish(String var1, byte[] var2);

   default void publish(String topic, byte[] frame, boolean durable) {
      this.publish(topic, frame);
   }

   void subscribe(String var1, Consumer<byte[]> var2);

   void unsubscribe(String var1);

   default void reconnect() {
   }

   void close();
}
