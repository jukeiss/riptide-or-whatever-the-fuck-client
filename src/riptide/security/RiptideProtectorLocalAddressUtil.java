package riptide.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class RiptideProtectorLocalAddressUtil {
   public static volatile String serverAddress;

   private RiptideProtectorLocalAddressUtil() {
   }

   public static boolean isLocalAddress(String host) throws UnknownHostException {
      if (host == null) {
         return false;
      } else {
         for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivateOrLocal(address)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isPrivateOrLocal(InetAddress address) {
      if (!address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isSiteLocalAddress() && !address.isLinkLocalAddress()) {
         byte[] bytes = address.getAddress();
         if (bytes.length == 4) {
            int b0 = bytes[0] & 255;
            int b1 = bytes[1] & 255;
            if (b0 == 0) {
               return true;
            }

            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
               return true;
            }
         } else if (bytes.length == 16) {
            int b0x = bytes[0] & 255;
            if ((b0x & 254) == 252) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public static boolean shouldBlock(String host) {
      try {
         if (!isLocalAddress(host)) {
            return false;
         } else {
            return isAlwaysBlocked(host) ? true : !isLocalAddress(serverAddress);
         }
      } catch (UnknownHostException var2) {
         return false;
      }
   }

   private static boolean isAlwaysBlocked(String host) throws UnknownHostException {
      if (host == null) {
         return false;
      } else {
         for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
               return true;
            }
         }

         return false;
      }
   }
}
