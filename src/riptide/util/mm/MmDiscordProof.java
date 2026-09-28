package riptide.util.mm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import riptide.util.RiptideDiscordLogin;
import riptide.util.RiptideHttp;
import riptide.util.mm.crypto.MmCrypto;

public final class MmDiscordProof {
   private static volatile PublicKey serverKey;
   private static volatile long lastFetchMs;
   private static final long REFETCH_MS = 60000L;

   private MmDiscordProof() {
   }

   public static boolean serverKeyReady() {
      return serverKey() != null;
   }

   public static PublicKey serverPublicKey() {
      return serverKey();
   }

   private static PublicKey serverKey() {
      PublicKey k = serverKey;
      if (k != null) {
         return k;
      } else {
         long now = System.currentTimeMillis();
         if (now - lastFetchMs < 60000L) {
            return null;
         } else {
            lastFetchMs = now;

            try {
               JsonObject r = RiptideHttp.getJson("                                 ", null);
               if (r != null && r.has("pub")) {
                  byte[] spki = Base64.getDecoder().decode(r.get("pub").getAsString());
                  serverKey = MmCrypto.ed25519PublicFromSpki(spki);
               }
            } catch (Throwable var5) {
            }

            return serverKey;
         }
      }
   }

   public static MmDiscordProof.Identity verify(String idtoken, byte[] senderSpki) {
      if (idtoken != null && !idtoken.isEmpty() && senderSpki != null) {
         PublicKey key = serverKey();
         if (key == null) {
            return null;
         } else {
            try {
               int dot1 = idtoken.indexOf(46);
               int dot2 = idtoken.indexOf(46, dot1 + 1);
               if (dot1 > 0 && dot2 > dot1) {
                  String signingInput = idtoken.substring(0, dot2);
                  byte[] sig = Base64.getUrlDecoder().decode(idtoken.substring(dot2 + 1));
                  if (!MmCrypto.ed25519Verify(key, signingInput.getBytes(StandardCharsets.US_ASCII), sig)) {
                     return null;
                  } else {
                     JsonObject claims = claims(idtoken, dot1, dot2);
                     long exp = claims.has("exp") ? claims.get("exp").getAsLong() : 0L;
                     if (exp * 1000L <= ServerClock.nowMs()) {
                        return null;
                     } else {
                        String fp = claims.has("fp") ? claims.get("fp").getAsString() : "";
                        if (!fp.equalsIgnoreCase(MmCrypto.hex(MmCrypto.sha256(senderSpki)))) {
                           return null;
                        } else {
                           String did = claims.has("did") ? claims.get("did").getAsString() : "";
                           if (did.isEmpty()) {
                              return null;
                           } else {
                              String uname = claims.has("uname") ? claims.get("uname").getAsString() : "";
                              return new MmDiscordProof.Identity(did, uname);
                           }
                        }
                     }
                  }
               } else {
                  return null;
               }
            } catch (Throwable var13) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static String ownName() {
      String token = RiptideDiscordLogin.currentIdToken();
      if (token != null && !token.isEmpty()) {
         try {
            int dot1 = token.indexOf(46);
            int dot2 = token.indexOf(46, dot1 + 1);
            if (dot1 > 0 && dot2 > dot1) {
               JsonObject claims = claims(token, dot1, dot2);
               return claims.has("uname") ? claims.get("uname").getAsString() : "";
            } else {
               return "";
            }
         } catch (Throwable var4) {
            return "";
         }
      } else {
         return "";
      }
   }

   private static JsonObject claims(String jwt, int dot1, int dot2) {
      byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(dot1 + 1, dot2));
      return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   public record Identity(String id, String username) {
   }
}
