package riptide.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class RiptideAttest {
   private static volatile Path jarPath;

   private RiptideAttest() {
   }

   public static String formSuffix(String authBase, String version) {
      try {
         JsonObject challenge = RiptideHttp.postForm(authBase + "/challenge", "version=" + enc(version));
         if (challenge == null) {
            riptide.RiptideClientAddon.LOG.debug("[attest] /challenge unreachable for version {}", version);
            return "";
         } else if (challenge.has("cid") && challenge.has("nonce")) {
            String cid = challenge.get("cid").getAsString();
            byte[] nonce = Base64.getDecoder().decode(challenge.get("nonce").getAsString());
            byte[] jar = ownJarBytes();
            if (jar == null) {
               riptide.RiptideClientAddon.LOG.debug("[attest] cannot read own jar for version {}", version);
               return "";
            } else {
               MessageDigest digest = MessageDigest.getInstance("SHA-256");
               digest.update(nonce);
               JsonArray ranges = challenge.has("ranges") ? challenge.getAsJsonArray("ranges") : null;
               if (ranges != null && ranges.size() != 0) {
                  for (int i = 0; i < ranges.size(); i++) {
                     JsonArray range = ranges.get(i).getAsJsonArray();
                     int start = (int)Math.max(0L, Math.min((long)jar.length, range.get(0).getAsLong()));
                     int end = (int)Math.max((long)start, Math.min((long)jar.length, range.get(1).getAsLong()));
                     digest.update(jar, start, end - start);
                  }
               } else {
                  digest.update(jar);
               }

               return "&cid=" + enc(cid) + "&answer=" + hex(digest.digest());
            }
         } else {
            return "";
         }
      } catch (Throwable var12) {
         riptide.RiptideClientAddon.LOG.debug("[attest] answer failed for version {}: {}", version, var12.toString());
         return "";
      }
   }

   private static byte[] ownJarBytes() {
      try {
         Path jar = jarPath;
         if (jar == null) {
            jar = FabricLoader.getInstance()
               .getModContainer("riptide")
               .flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
               .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
               .filter(x$0 -> Files.isRegularFile(x$0))
               .orElse(null);
            jarPath = jar;
         }

         return jar == null ? null : Files.readAllBytes(jar);
      } catch (Throwable var1) {
         return null;
      }
   }

   private static String hex(byte[] bytes) {
      StringBuilder out = new StringBuilder(bytes.length * 2);

      for (byte b : bytes) {
         out.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
      }

      return out.toString();
   }

   private static String enc(String value) {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
   }
}
