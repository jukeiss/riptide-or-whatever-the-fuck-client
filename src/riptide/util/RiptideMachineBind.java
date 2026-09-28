package riptide.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class RiptideMachineBind {
   private static final byte[] PEPPER = new byte[]{-114, 20, -61, 90, 119, -16, 41, -67, 70, -95, 13, -100, -25, 50, 88, -74};
   private static final int IV_LEN = 12;
   private static final int TAG_BITS = 128;
   private static volatile byte[] cachedKey;

   private RiptideMachineBind() {
   }

   private static String fingerprint() {
      StringBuilder var0 = new StringBuilder();
      var0.append(prop("os.name"))
         .append('\u0001')
         .append(prop("os.arch"))
         .append('\u0001')
         .append(prop("user.name"))
         .append('\u0001')
         .append(prop("user.home"))
         .append('\u0001')
         .append(host());
      return var0.toString();
   }

   private static String prop(String var0) {
      try {
         String var1 = System.getProperty(var0);
         return var1 == null ? "" : var1;
      } catch (Throwable var2) {
         return "";
      }
   }

   private static String host() {
      try {
         String var0 = System.getenv("COMPUTERNAME");
         if (var0 == null || var0.isBlank()) {
            var0 = System.getenv("HOSTNAME");
         }

         return var0 == null ? "" : var0;
      } catch (Throwable var1) {
         return "";
      }
   }

   private static byte[] machineKey() throws Exception {
      byte[] var0 = cachedKey;
      if (var0 != null) {
         return var0;
      } else {
         MessageDigest var1 = MessageDigest.getInstance("SHA-256");
         var1.update(PEPPER);
         byte[] var2 = var1.digest(fingerprint().getBytes(StandardCharsets.UTF_8));
         cachedKey = var2;
         return var2;
      }
   }

   public static String seal(String var0) {
      try {
         byte[] var1 = new byte[12];
         new SecureRandom().nextBytes(var1);
         Cipher var2 = Cipher.getInstance("AES/GCM/NoPadding");
         var2.init(1, new SecretKeySpec(machineKey(), "AES"), new GCMParameterSpec(128, var1));
         byte[] var3 = var2.doFinal(var0.getBytes(StandardCharsets.UTF_8));
         byte[] var4 = new byte[var1.length + var3.length];
         System.arraycopy(var1, 0, var4, 0, var1.length);
         System.arraycopy(var3, 0, var4, var1.length, var3.length);
         return Base64.getEncoder().encodeToString(var4);
      } catch (Throwable var5) {
         return null;
      }
   }

   public static String open(String var0) {
      try {
         byte[] var1 = Base64.getDecoder().decode(var0.trim());
         if (var1.length <= 12) {
            return null;
         } else {
            byte[] var2 = new byte[12];
            System.arraycopy(var1, 0, var2, 0, 12);
            Cipher var3 = Cipher.getInstance("AES/GCM/NoPadding");
            var3.init(2, new SecretKeySpec(machineKey(), "AES"), new GCMParameterSpec(128, var2));
            byte[] var4 = var3.doFinal(var1, 12, var1.length - 12);
            return new String(var4, StandardCharsets.UTF_8);
         }
      } catch (Throwable var5) {
         return null;
      }
   }
}
