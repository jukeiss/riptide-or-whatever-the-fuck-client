package riptide.util.mm.guardian.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class RiptideJarAttest {
   private RiptideJarAttest() {
   }

   private static Path selfJar() {
      try {
         Optional<ModContainer> opt = FabricLoader.getInstance().getModContainer("riptide");
         if (opt.isEmpty()) {
            return null;
         } else {
            Path only = null;

            for (Path p : opt.get().getOrigin().getPaths()) {
               if (p != null && Files.isRegularFile(p)) {
                  if (only != null) {
                     return null;
                  }

                  only = p;
               }
            }

            return only;
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   public static String answer(JsonObject challenge) {
      if (challenge != null && challenge.has("nonce") && challenge.has("ranges")) {
         Path jar = selfJar();
         if (jar == null) {
            return null;
         } else {
            try {
               String var23;
               try (FileChannel ch = FileChannel.open(jar, StandardOpenOption.READ)) {
                  long size = ch.size();
                  MessageDigest md = MessageDigest.getInstance("SHA-256");
                  md.update(Base64.getDecoder().decode(challenge.get("nonce").getAsString()));

                  for (JsonElement el : challenge.getAsJsonArray("ranges")) {
                     JsonArray r = el.getAsJsonArray();
                     long off = r.get(0).getAsLong();
                     int len = r.get(1).getAsInt();
                     if (off < 0L || len < 0 || off + len > size) {
                        return null;
                     }

                     ByteBuffer buf = ByteBuffer.allocate(Math.min(len, 65536));
                     long remaining = len;
                     long pos = off;

                     while (remaining > 0L) {
                        buf.clear();
                        if (buf.capacity() > remaining) {
                           buf.limit((int)remaining);
                        }

                        int n = ch.read(buf, pos);
                        if (n <= 0) {
                           return null;
                        }

                        buf.flip();
                        md.update(buf);
                        pos += n;
                        remaining -= n;
                     }
                  }

                  var23 = hex(md.digest());
               }

               return var23;
            } catch (Throwable var22) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String hex(byte[] b) {
      StringBuilder sb = new StringBuilder(b.length * 2);

      for (byte x : b) {
         sb.append(Character.forDigit(x >> 4 & 15, 16)).append(Character.forDigit(x & 15, 16));
      }

      return sb.toString();
   }
}
