package riptide.util.mm.guardian.impl;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import riptide.util.mm.guardian.Guardians;

public final class LobbyUnwrap {
   private LobbyUnwrap() {
   }

   public static byte[] apply(byte[] original, JsonObject b) {
      try {
         String wrapped = b.has("keyWrapped") && !b.get("keyWrapped").isJsonNull() ? b.get("keyWrapped").getAsString() : "";
         String sess = b.has("sessNonce") && !b.get("sessNonce").isJsonNull() ? b.get("sessNonce").getAsString() : "";
         if (!wrapped.isEmpty() && !sess.isEmpty()) {
            byte[] k = Guardians.get().unwrapKey(Base64.getDecoder().decode(wrapped), sess.getBytes(StandardCharsets.UTF_8));
            if (k != null && k.length == 32) {
               return k;
            }
         }
      } catch (Throwable var5) {
      }

      return original;
   }
}
