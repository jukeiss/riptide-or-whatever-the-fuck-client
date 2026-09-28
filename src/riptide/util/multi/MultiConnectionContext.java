package riptide.util.multi;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.Connection;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyType;

public final class MultiConnectionContext {
   private static final ThreadLocal<Boolean> CONNECTING_MULTI = new ThreadLocal<>();
   private static final ThreadLocal<MultiConnectionContext.ProxySpec> CONNECTING_PROXY = new ThreadLocal<>();
   private static final AttributeKey<Boolean> MULTI_CHANNEL = AttributeKey.valueOf("riptide:multi_connection");
   private static final Map<Connection, MultiConnectionContext.ProxySpecHolder> UNMIXED_CONTEXTS = new ConcurrentHashMap<>();

   public static MultiConnectionContext.ProxySpec beginConnect(MultiConnectionContext.ProxySpec proxy) {
      CONNECTING_MULTI.set(Boolean.TRUE);
      MultiConnectionContext.ProxySpec previous = CONNECTING_PROXY.get();
      if (proxy == null) {
         CONNECTING_PROXY.remove();
      } else {
         CONNECTING_PROXY.set(proxy);
      }

      return previous;
   }

   public static void endConnect(MultiConnectionContext.ProxySpec previous) {
      CONNECTING_MULTI.remove();
      if (previous == null) {
         CONNECTING_PROXY.remove();
      } else {
         CONNECTING_PROXY.set(previous);
      }
   }

   public static boolean isConnecting() {
      return Boolean.TRUE.equals(CONNECTING_MULTI.get());
   }

   public static MultiConnectionContext.ProxySpec pendingProxy() {
      return CONNECTING_PROXY.get();
   }

   private MultiConnectionContext() {
   }

   public static void register(Connection connection, RiptideProxy proxy) {
      if (connection != null) {
         MultiConnectionContext.ProxySpec spec = MultiConnectionContext.ProxySpec.copyOf(proxy);
         if (connection instanceof MultiConnectionMarker marker) {
            marker.riptide$setMultiManaged(spec);
         } else {
            UNMIXED_CONTEXTS.put(connection, new MultiConnectionContext.ProxySpecHolder(spec));
         }
      }
   }

   public static boolean isMulti(Connection connection) {
      return connection instanceof MultiConnectionMarker marker
         ? marker.riptide$isMultiManaged()
         : connection != null && UNMIXED_CONTEXTS.containsKey(connection);
   }

   public static void bindChannel(Connection connection, Channel channel) {
      if (isMulti(connection) && channel != null) {
         channel.attr(MULTI_CHANNEL).set(Boolean.TRUE);
      }
   }

   public static boolean isMulti(Channel channel) {
      return channel != null && Boolean.TRUE.equals(channel.attr(MULTI_CHANNEL).get());
   }

   public static void unbindChannel(Channel channel) {
      if (channel != null) {
         channel.attr(MULTI_CHANNEL).set(null);
      }
   }

   public static MultiConnectionContext.ProxySpec proxy(Connection connection) {
      if (connection instanceof MultiConnectionMarker marker) {
         return marker.riptide$multiProxy();
      } else {
         MultiConnectionContext.ProxySpecHolder holder = connection == null ? null : UNMIXED_CONTEXTS.get(connection);
         return holder == null ? null : holder.proxy;
      }
   }

   public static void remove(Connection connection) {
      if (connection instanceof MultiConnectionMarker marker) {
         marker.riptide$clearMultiManaged();
      } else if (connection != null) {
         UNMIXED_CONTEXTS.remove(connection);
      }
   }

   public record ProxySpec(RiptideProxyType type, String address, int port, String username, String password) {
      public static MultiConnectionContext.ProxySpec copyOf(RiptideProxy proxy) {
         return proxy == null
            ? null
            : new MultiConnectionContext.ProxySpec(
               proxy.type,
               proxy.address == null ? "" : proxy.address,
               proxy.port,
               proxy.username == null ? "" : proxy.username,
               proxy.password == null ? "" : proxy.password
            );
      }
   }

   private record ProxySpecHolder(MultiConnectionContext.ProxySpec proxy) {
   }
}
