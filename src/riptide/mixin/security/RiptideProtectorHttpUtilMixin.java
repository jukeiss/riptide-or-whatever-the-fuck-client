package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.Proxy.Type;
import java.util.Map;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorLocalAddressUtil;

@Mixin({HttpUtil.class})
public class RiptideProtectorHttpUtilMixin {
   @WrapOperation(
      method = {"downloadFile"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;"
      )},
      require = 1
   )
   private static InputStream riptide$blockLocal(
      HttpURLConnection instance,
      Operation<InputStream> original,
      @Local(argsOnly = true) Proxy proxy,
      @Local(argsOnly = true) Map<String, String> requestProperties,
      @Local LocalRef<HttpURLConnection> connectionRef
   ) throws IOException {
      if (!RiptideProtector.shouldBlockLocalUrls()) {
         return (InputStream)original.call(new Object[]{instance});
      } else {
         rejectLocal(instance.getURL());
         instance.setInstanceFollowRedirects(false);
         int redirects = 0;
         int maxRedirects = maxRedirects();

         for (int status = instance.getResponseCode(); instance.getHeaderField("Location") != null && isRedirect(status); redirects++) {
            if (redirects >= maxRedirects - 1) {
               leakVanillaCapSocket(instance);
               throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");
            }

            if (status == 305) {
               URL proxyUrl = resolveRedirect(instance);
               if (proxyUrl == null) {
                  break;
               }

               rejectLocal(proxyUrl);
               int proxyPort = proxyUrl.getPort() == -1 ? proxyUrl.getDefaultPort() : proxyUrl.getPort();
               Proxy hopProxy = new Proxy(Type.HTTP, InetSocketAddress.createUnresolved(proxyUrl.getHost(), proxyPort));
               URL originalUrl = instance.getURL();
               instance = (HttpURLConnection)originalUrl.openConnection(hopProxy);
            } else {
               URL next = resolveRedirect(instance);
               if (next == null || !sameProtocol(instance.getURL(), next)) {
                  break;
               }

               rejectLocal(next);
               instance = (HttpURLConnection)next.openConnection(proxy);
            }

            instance.setAuthenticator(new Authenticator() {});
            instance.setInstanceFollowRedirects(false);
            requestProperties.forEach(instance::setRequestProperty);
            status = instance.getResponseCode();
         }

         if (connectionRef != null) {
            connectionRef.set(instance);
         }

         return (InputStream)original.call(new Object[]{instance});
      }
   }

   private static void rejectLocal(URL url) {
      String host = url == null ? null : url.getHost();
      if (RiptideProtectorLocalAddressUtil.shouldBlock(host)) {
         throw new IllegalStateException("[RiptideProtector] Refused resource pack download from local address: " + url);
      }
   }

   private static boolean isRedirect(int status) {
      return status == 300 || status == 301 || status == 302 || status == 303 || status == 305 || status == 307;
   }

   private static URL resolveRedirect(HttpURLConnection connection) throws IOException {
      String location = connection.getHeaderField("Location");
      if (location != null && !location.isBlank()) {
         try {
            URI uri = URI.create(location);
            if (uri.isAbsolute()) {
               return uri.toURL();
            }
         } catch (IllegalArgumentException var5) {
         }

         try {
            return connection.getURL().toURI().resolve(location).toURL();
         } catch (IllegalArgumentException | URISyntaxException var4) {
            MalformedURLException malformed = new MalformedURLException(location);
            malformed.initCause(var4);
            throw malformed;
         }
      } else {
         return null;
      }
   }

   private static boolean sameProtocol(URL a, URL b) {
      return a != null && b != null && a.getProtocol().equalsIgnoreCase(b.getProtocol());
   }

   private static int maxRedirects() {
      String value = System.getProperty("http.maxRedirects");
      if (value == null) {
         return 20;
      } else {
         try {
            return Math.max(1, Integer.parseInt(value));
         } catch (NumberFormatException var2) {
            return 20;
         }
      }
   }

   private static void leakVanillaCapSocket(HttpURLConnection connection) {
      try {
         URL url = resolveRedirect(connection);
         if (url == null) {
            return;
         }

         int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
         new Socket(url.getHost(), port);
      } catch (Exception var3) {
      }
   }
}
