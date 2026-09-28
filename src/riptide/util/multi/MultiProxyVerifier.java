package riptide.util.multi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyType;

public final class MultiProxyVerifier {
   private MultiProxyVerifier() {
   }

   public static MultiProxyVerifier.Result verify(RiptideProxy proxy, String destHost, int destPort, int timeoutMs) {
      if (proxy != null && proxy.isValid()) {
         String host = destHost == null ? "" : destHost.trim();
         if (!host.isBlank() && destPort > 0 && destPort <= 65535) {
            int timeout = Math.max(1, timeoutMs);
            RiptideProxyType primary = proxy.type == RiptideProxyType.Socks4 ? RiptideProxyType.Socks4 : RiptideProxyType.Socks5;
            RiptideProxyType secondary = primary == RiptideProxyType.Socks4 ? RiptideProxyType.Socks5 : RiptideProxyType.Socks4;
            MultiProxyVerifier.Result result = attempt(proxy, primary, host, destPort, timeout);
            return result.ok() ? result : attempt(proxy, secondary, host, destPort, timeout);
         } else {
            return MultiProxyVerifier.Result.fail();
         }
      } else {
         return MultiProxyVerifier.Result.fail();
      }
   }

   private static MultiProxyVerifier.Result attempt(RiptideProxy proxy, RiptideProxyType type, String host, int port, int timeout) {
      long started = System.nanoTime();

      try {
         MultiProxyVerifier.Result var13;
         try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(proxy.address, proxy.port), timeout);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            boolean ok = type == RiptideProxyType.Socks4 ? connectSocks4(out, in, proxy, host, port) : connectSocks5(out, in, proxy, host, port);
            if (!ok) {
               return MultiProxyVerifier.Result.fail();
            }

            long latency = Math.max(0L, (System.nanoTime() - started) / 1000000L);
            var13 = new MultiProxyVerifier.Result(true, latency, type);
         }

         return var13;
      } catch (RuntimeException | IOException var16) {
         return MultiProxyVerifier.Result.fail();
      }
   }

   private static boolean connectSocks5(OutputStream out, InputStream in, RiptideProxy proxy, String host, int port) throws IOException {
      boolean hasAuth = proxy.username != null && !proxy.username.isBlank();
      if (hasAuth) {
         out.write(new byte[]{5, 2, 0, 2});
      } else {
         out.write(new byte[]{5, 1, 0});
      }

      out.flush();
      byte[] method = readN(in, 2);
      if (method.length >= 2 && method[0] == 5) {
         int chosen = method[1] & 255;
         if (chosen == 2) {
            if (!hasAuth || !authSocks5(out, in, proxy)) {
               return false;
            }
         } else if (chosen != 0) {
            return false;
         }

         byte[] domain = host.getBytes(StandardCharsets.UTF_8);
         if (domain.length > 255) {
            return false;
         } else {
            ByteArrayOutputStream request = new ByteArrayOutputStream(7 + domain.length);
            request.write(5);
            request.write(1);
            request.write(0);
            request.write(3);
            request.write(domain.length);
            request.write(domain, 0, domain.length);
            request.write(port >> 8 & 0xFF);
            request.write(port & 0xFF);
            out.write(request.toByteArray());
            out.flush();
            byte[] reply = readN(in, 2);
            return reply.length >= 2 && reply[0] == 5 && reply[1] == 0;
         }
      } else {
         return false;
      }
   }

   private static boolean authSocks5(OutputStream out, InputStream in, RiptideProxy proxy) throws IOException {
      byte[] user = (proxy.username == null ? "" : proxy.username).getBytes(StandardCharsets.UTF_8);
      byte[] pass = (proxy.password == null ? "" : proxy.password).getBytes(StandardCharsets.UTF_8);
      if (user.length <= 255 && pass.length <= 255) {
         ByteArrayOutputStream auth = new ByteArrayOutputStream(3 + user.length + pass.length);
         auth.write(1);
         auth.write(user.length);
         auth.write(user, 0, user.length);
         auth.write(pass.length);
         auth.write(pass, 0, pass.length);
         out.write(auth.toByteArray());
         out.flush();
         byte[] reply = readN(in, 2);
         return reply.length >= 2 && reply[1] == 0;
      } else {
         return false;
      }
   }

   private static boolean connectSocks4(OutputStream out, InputStream in, RiptideProxy proxy, String host, int port) throws IOException {
      byte[] user = (proxy.username == null ? "" : proxy.username).getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream request = new ByteArrayOutputStream();
      request.write(4);
      request.write(1);
      request.write(port >> 8 & 0xFF);
      request.write(port & 0xFF);
      byte[] ipv4 = ipv4Bytes(host);
      if (ipv4 != null) {
         request.write(ipv4, 0, 4);
         request.write(user, 0, user.length);
         request.write(0);
      } else {
         request.write(new byte[]{0, 0, 0, 1});
         request.write(user, 0, user.length);
         request.write(0);
         byte[] domain = host.getBytes(StandardCharsets.UTF_8);
         request.write(domain, 0, domain.length);
         request.write(0);
      }

      out.write(request.toByteArray());
      out.flush();
      byte[] reply = readN(in, 8);
      return reply.length >= 2 && reply[0] == 0 && reply[1] == 90;
   }

   private static byte[] readN(InputStream in, int count) throws IOException {
      return in.readNBytes(count);
   }

   private static byte[] ipv4Bytes(String value) {
      if (value == null) {
         return null;
      } else {
         String[] parts = value.split("\\.");
         if (parts.length != 4) {
            return null;
         } else {
            byte[] out = new byte[4];

            for (int i = 0; i < 4; i++) {
               try {
                  int octet = Integer.parseInt(parts[i]);
                  if (octet < 0 || octet > 255) {
                     return null;
                  }

                  out[i] = (byte)octet;
               } catch (NumberFormatException var5) {
                  return null;
               }
            }

            return out;
         }
      }
   }

   public record Result(boolean ok, long latencyMs, RiptideProxyType workingType) {
      public Result(boolean ok, long latencyMs) {
         this(ok, latencyMs, null);
      }

      static MultiProxyVerifier.Result fail() {
         return new MultiProxyVerifier.Result(false, 0L, null);
      }
   }
}
