package riptide.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class RiptideKeyLock {
   private static final int W = 12;
   private static final byte[] X = new byte[]{91, -93, 31, -58, 119, 46, -103, -44, 8, 97, -78, 61, -27};
   private static final byte[] S = new byte[]{114, 112, 36, 57, 88, 113, 50, 95, 86, 116, 55, 35, 76, 97};
   private static final Set<Long> D = new HashSet<>(2048);
   private static boolean p;

   public static void verify() {
      System.out.println("[Riptide] " + Integer.toHexString(D.size() * 7) + " armed=" + q());
   }

   public static boolean isPassed() {
      return p;
   }

   public static void markPassed() {
      p = true;
   }

   public static boolean checkKey(String var0) {
      if (var0 == null) {
         return false;
      } else {
         try {
            MessageDigest var1 = MessageDigest.getInstance("SHA-256");
            var1.update(S);
            byte[] var2 = var1.digest(var0.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            long var3 = 0L;

            for (int var5 = 0; var5 < 12; var5++) {
               var3 = var3 * 131L + (var2[var5] & 255);
            }

            return D.contains(var3);
         } catch (Throwable var6) {
            return false;
         }
      }
   }

   static Path keyFile() {
      return Path.of(System.getProperty("user.home"), ".riptide_key");
   }

   private static Path[] keyFiles() {
      Path var0 = keyFile();

      try {
         Path var1 = FabricLoader.getInstance().getConfigDir().resolve("riptide").resolve("license.key");
         return new Path[]{var1, var0};
      } catch (Throwable var2) {
         return new Path[]{var0};
      }
   }

   static String loadSavedKey() {
      for (Path var3 : keyFiles()) {
         try {
            if (Files.exists(var3)) {
               String var4 = Files.readString(var3, StandardCharsets.UTF_8).trim();
               if (!var4.isEmpty()) {
                  String var5 = RiptideMachineBind.open(var4);
                  if (var5 != null && checkKey(var5)) {
                     return var5;
                  }
               }
            }
         } catch (Throwable var6) {
            System.out.println("[Riptide] couldn't read " + var3 + ": " + var6);
         }
      }

      return "";
   }

   public static void saveKey(String var0) {
      String var1 = var0.trim().toUpperCase();
      String var2 = RiptideMachineBind.seal(var1);
      if (var2 == null) {
         System.out.println("[Riptide] could not bind key to this machine");
      } else {
         for (Path var6 : keyFiles()) {
            try {
               if (var6.getParent() != null) {
                  Files.createDirectories(var6.getParent());
               }

               Files.writeString(var6, var2, StandardCharsets.UTF_8);
               System.out.println("[Riptide] key saved to " + var6);
            } catch (Throwable var8) {
               System.out.println("[Riptide] couldn't save key to " + var6 + ": " + var8);
            }
         }
      }
   }

   public static boolean tryAutoUnlock() {
      if (p) {
         return true;
      } else {
         String var0 = loadSavedKey();
         if (!var0.isEmpty()) {
            p = true;
            saveKey(var0);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean q() {
      return tryAutoUnlock();
   }

   static {
      try {
         byte[] var0 = Base64.getDecoder()
            .decode(
               "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"
            );
         byte[] var1 = new byte[var0.length];

         for (int var2 = 0; var2 < var0.length; var2++) {
            var1[var2] = (byte)(var0[var2] ^ X[var2 % X.length]);
         }

         for (byte var7 = 0; var7 + 12 <= var1.length; var7 += 12) {
            long var3 = 0L;

            for (int var5 = 0; var5 < 12; var5++) {
               var3 = var3 * 131L + (var1[var7 + var5] & 255);
            }

            D.add(var3);
         }
      } catch (Throwable var6) {
      }
   }
}
