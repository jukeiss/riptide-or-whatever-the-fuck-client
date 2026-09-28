package riptide.util.mm.guardian.impl;

import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import riptide.util.RiptideDiscordLogin;
import riptide.util.RiptideHttp;
import riptide.util.mm.crypto.MmIdentity;
import riptide.util.mm.guardian.Guardians;

public final class LoginProof {
   private LoginProof() {
   }

   public static String build() {
      try {
         String version = RiptideDiscordLogin.modVersionString();
         JsonObject ch = RiptideHttp.postForm("                                  ", "version=" + enc(version));
         if (ch != null && ch.has("ranges")) {
            String cid = ch.has("cid") ? ch.get("cid").getAsString() : "";
            if (cid.isEmpty()) {
               return "";
            } else {
               String nonce = ch.has("nonce") ? ch.get("nonce").getAsString() : "";
               String pubkey = pubkey();
               String answer = RiptideJarAttest.answer(ch);
               String guardian = Guardians.get().attest(nonce, version, pubkey);
               if (answer != null && !answer.isEmpty() || guardian != null && !guardian.isEmpty()) {
                  StringBuilder sb = new StringBuilder("&cid=").append(enc(cid));
                  if (answer != null && !answer.isEmpty()) {
                     sb.append("&answer=").append(enc(answer));
                  }

                  if (guardian != null && !guardian.isEmpty()) {
                     sb.append("&g=").append(enc(guardian));
                  }

                  return sb.toString();
               } else {
                  return "";
               }
            }
         } else {
            return "";
         }
      } catch (Throwable var8) {
         return "";
      }
   }

   private static String pubkey() {
      try {
         return Base64.getEncoder().encodeToString(MmIdentity.get().publicKeySpki());
      } catch (Throwable var1) {
         return "";
      }
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }
}
