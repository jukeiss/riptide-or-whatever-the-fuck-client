package riptide.util.mm.guardian.impl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import riptide.util.mm.crypto.MmCrypto;
import riptide.util.mm.guardian.Guardian;

public final class OfficialGuardian implements Guardian {
   @Override
   public boolean isOfficial() {
      return true;
   }

   @Override
   public String attest(String nonceB64, String version, String pubkeyB64) {
      byte[] kb = null;

      String var9;
      try {
         kb = K.k();
         if (kb == null || kb.length == 0) {
            return null;
         }

         String ev = InjectionScan.evidence().toString();
         String msg = nz(nonceB64) + "\n" + nz(version) + "\n" + nz(pubkeyB64) + "\n" + ev;
         String sig = MmCrypto.hex(MmCrypto.hmacSha256(kb, msg.getBytes(StandardCharsets.UTF_8)));
         String evB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(ev.getBytes(StandardCharsets.UTF_8));
         var9 = evB64 + "." + sig;
      } catch (Throwable var13) {
         return null;
      } finally {
         if (kb != null) {
            Arrays.fill(kb, (byte)0);
         }
      }

      return var9;
   }

   @Override
   public byte[] unwrapKey(byte[] wrapped, byte[] info) {
      byte[] kb = null;

      byte[] var7;
      try {
         if (wrapped == null || wrapped.length < 28 || info == null) {
            return null;
         }

         kb = K.k();
         if (kb == null || kb.length == 0) {
            return null;
         }

         byte[] key = MmCrypto.hkdf(MmCrypto.utf8("riptide/guardian/kd/v1"), kb, new String(info, StandardCharsets.UTF_8), 32);
         byte[] nonce = Arrays.copyOfRange(wrapped, 0, 12);
         byte[] ct = Arrays.copyOfRange(wrapped, 12, wrapped.length);
         var7 = MmCrypto.aesGcmOpen(key, nonce, ct, null);
      } catch (Throwable var11) {
         return null;
      } finally {
         if (kb != null) {
            Arrays.fill(kb, (byte)0);
         }
      }

      return var7;
   }

   private static String nz(String s) {
      return s == null ? "" : s;
   }
}
