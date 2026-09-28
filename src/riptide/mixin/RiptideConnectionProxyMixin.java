package riptide.mixin;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalChannel;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideProxyType;
import riptide.util.multi.MultiConnectionContext;

@Mixin(
   value = {Connection.class},
   priority = 2000
)
public abstract class RiptideConnectionProxyMixin {
   @Inject(
      method = {"configureSerialization"},
      at = {@At("HEAD")}
   )
   private static void riptide$disableMeteorProxyBeforeHandlers(
      ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci
   ) {
      if (!local && inboundDirection == PacketFlow.CLIENTBOUND) {
         riptide$disableMeteorProxy();
      }
   }

   @Inject(
      method = {"configurePacketHandler"},
      at = {@At("HEAD")}
   )
   private void riptide$applyProxy(ChannelPipeline pipeline, CallbackInfo ci) {
      Connection connection = (Connection)this;
      if (connection.getReceiving() == PacketFlow.CLIENTBOUND) {
         if (!(pipeline.channel() instanceof LocalChannel)) {
            if (MultiConnectionContext.isMulti(connection)) {
               riptide$removeAllProxyHandlers(pipeline);
               MultiConnectionContext.ProxySpec proxy = MultiConnectionContext.proxy(connection);
               if (proxy != null && !proxy.address().isBlank() && proxy.port() > 0) {
                  riptide$installProxy(pipeline, proxy.type(), proxy.address(), proxy.port(), proxy.username(), proxy.password());
               }
            } else {
               RiptideProxy main = RiptideProxyManager.get().getEnabled();
               if (main != null) {
                  riptide$removeAllProxyHandlers(pipeline);
                  riptide$installProxy(pipeline, main.type, main.address, main.port, main.username, main.password);
               }
            }
         }
      }
   }

   @Unique
   private static void riptide$installProxy(ChannelPipeline pipeline, RiptideProxyType type, String address, int port, String username, String password) {
      if (address != null && !address.isBlank() && port > 0) {
         InetSocketAddress target = new InetSocketAddress(address, port);
         String user = username == null ? "" : username;
         String pass = password == null ? "" : password;
         switch (type) {
            case Socks4:
               pipeline.addFirst("riptide_socks4_proxy", new Socks4ProxyHandler(target, user));
               break;
            case Socks5:
               pipeline.addFirst("riptide_socks5_proxy", new Socks5ProxyHandler(target, user, pass));
         }
      }
   }

   @Unique
   private static void riptide$removeAllProxyHandlers(ChannelPipeline pipeline) {
      for (String name : new ArrayList(pipeline.names())) {
         if (pipeline.get(name) instanceof ProxyHandler) {
            pipeline.remove(name);
         }
      }
   }

   private static void riptide$disableMeteorProxy() {
      if (FabricLoader.getInstance().isModLoaded("meteor-client")) {
         try {
            Class<?> proxiesClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxies");
            Class<?> proxyClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxy");
            Object proxies = proxiesClass.getMethod("get").invoke(null);
            Object enabled = proxiesClass.getMethod("getEnabled").invoke(proxies);
            if (enabled != null) {
               proxiesClass.getMethod("setEnabled", proxyClass, boolean.class).invoke(proxies, enabled, false);
            }
         } catch (ReflectiveOperationException var4) {
         }
      }
   }
}
