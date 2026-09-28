package riptide.util.mm.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class MmCrypto {
   public static final SecureRandom RNG = new SecureRandom();

   private MmCrypto() {
   }

   public static byte[] randomBytes(int n) {
      byte[] out = new byte[n];
      RNG.nextBytes(out);
      return out;
   }

   public static boolean constantTimeEquals(byte[] a, byte[] b) {
      return MessageDigest.isEqual(a, b);
   }

   public static byte[] sha256(byte[]... parts) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");

         for (byte[] p : parts) {
            md.update(p);
         }

         return md.digest();
      } catch (Exception var6) {
         throw new MmCrypto.MmCryptoException("sha256", var6);
      }
   }

   public static byte[] utf8(String s) {
      return s.getBytes(StandardCharsets.UTF_8);
   }

   public static byte[] hmacSha256(byte[] key, byte[] data) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, "HmacSHA256"));
         return mac.doFinal(data);
      } catch (Exception var3) {
         throw new MmCrypto.MmCryptoException("hmac", var3);
      }
   }

   public static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
      return hmacSha256(salt, ikm);
   }

   public static byte[] hkdfExpand(byte[] prk, String info, int length) {
      byte[] infoBytes = utf8(info);
      byte[] out = new byte[length];
      byte[] t = new byte[0];
      int pos = 0;

      for (byte counter = 1; pos < length; counter++) {
         byte[] input = new byte[t.length + infoBytes.length + 1];
         System.arraycopy(t, 0, input, 0, t.length);
         System.arraycopy(infoBytes, 0, input, t.length, infoBytes.length);
         input[input.length - 1] = counter;
         t = hmacSha256(prk, input);
         int n = Math.min(t.length, length - pos);
         System.arraycopy(t, 0, out, pos, n);
         pos += n;
      }

      return out;
   }

   public static byte[] hkdf(byte[] salt, byte[] ikm, String info, int length) {
      return hkdfExpand(hkdfExtract(salt, ikm), info, length);
   }

   public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int dkLenBytes) {
      try {
         SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
         PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, dkLenBytes * 8);
         return f.generateSecret(spec).getEncoded();
      } catch (Exception var6) {
         throw new MmCrypto.MmCryptoException("pbkdf2", var6);
      }
   }

   public static byte[] aesGcmSeal(byte[] key32, byte[] nonce12, byte[] plaintext, byte[] aad) {
      try {
         Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
         c.init(1, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, nonce12));
         if (aad != null) {
            c.updateAAD(aad);
         }

         return c.doFinal(plaintext);
      } catch (Exception var5) {
         throw new MmCrypto.MmCryptoException("aesGcmSeal", var5);
      }
   }

   public static byte[] aesGcmOpen(byte[] key32, byte[] nonce12, byte[] ciphertext, byte[] aad) {
      try {
         Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
         c.init(2, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, nonce12));
         if (aad != null) {
            c.updateAAD(aad);
         }

         return c.doFinal(ciphertext);
      } catch (AEADBadTagException var5) {
         return null;
      } catch (Exception var6) {
         return null;
      }
   }

   public static KeyPair generateEd25519() {
      try {
         return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      } catch (Exception var1) {
         throw new MmCrypto.MmCryptoException("genEd25519", var1);
      }
   }

   public static byte[] ed25519Sign(PrivateKey priv, byte[] message) {
      try {
         Signature s = Signature.getInstance("Ed25519");
         s.initSign(priv);
         s.update(message);
         return s.sign();
      } catch (Exception var3) {
         throw new MmCrypto.MmCryptoException("ed25519Sign", var3);
      }
   }

   public static boolean ed25519Verify(PublicKey pub, byte[] message, byte[] signature) {
      try {
         Signature s = Signature.getInstance("Ed25519");
         s.initVerify(pub);
         s.update(message);
         return s.verify(signature);
      } catch (Exception var4) {
         return false;
      }
   }

   public static PublicKey ed25519PublicFromSpki(byte[] spki) {
      try {
         return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
      } catch (Exception var2) {
         throw new MmCrypto.MmCryptoException("ed25519Pub", var2);
      }
   }

   public static PrivateKey ed25519PrivateFromPkcs8(byte[] pkcs8) {
      try {
         return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      } catch (Exception var2) {
         throw new MmCrypto.MmCryptoException("ed25519Priv", var2);
      }
   }

   public static KeyPair generateX25519() {
      try {
         return KeyPairGenerator.getInstance("X25519").generateKeyPair();
      } catch (Exception var1) {
         throw new MmCrypto.MmCryptoException("genX25519", var1);
      }
   }

   public static byte[] x25519Agree(PrivateKey priv, PublicKey peerPub) {
      try {
         KeyAgreement ka = KeyAgreement.getInstance("X25519");
         ka.init(priv);
         ka.doPhase(peerPub, true);
         return ka.generateSecret();
      } catch (Exception var3) {
         throw new MmCrypto.MmCryptoException("x25519Agree", var3);
      }
   }

   public static PublicKey x25519PublicFromSpki(byte[] spki) {
      try {
         return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(spki));
      } catch (Exception var2) {
         throw new MmCrypto.MmCryptoException("x25519Pub", var2);
      }
   }

   public static PrivateKey x25519PrivateFromPkcs8(byte[] pkcs8) {
      try {
         return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      } catch (Exception var2) {
         throw new MmCrypto.MmCryptoException("x25519Priv", var2);
      }
   }

   public static byte[] spki(PublicKey key) {
      return key.getEncoded();
   }

   public static byte[] prefix(byte[] data, int n) {
      return Arrays.copyOf(data, n);
   }

   public static String hex(byte[] data) {
      StringBuilder sb = new StringBuilder(data.length * 2);

      for (byte b : data) {
         sb.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
      }

      return sb.toString();
   }

   public static final class MmCryptoException extends RuntimeException {
      public MmCryptoException(String message, Throwable cause) {
         super(message, cause);
      }
   }
}
