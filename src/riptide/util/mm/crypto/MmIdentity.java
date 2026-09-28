package riptide.util.mm.crypto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Locale;

public final class MmIdentity {
   private static final File FILE = new File(riptide.RiptideClientAddon.FOLDER, "mm_identity.dat");
   private static final File BAK = new File(riptide.RiptideClientAddon.FOLDER, "mm_identity.dat.bak");
   private static final File TMP = new File(riptide.RiptideClientAddon.FOLDER, "mm_identity.dat.tmp");
   private static final File KEY_FILE = new File(riptide.RiptideClientAddon.FOLDER, "mm_identity.key");
   private static final File KEY_TMP = new File(riptide.RiptideClientAddon.FOLDER, "mm_identity.key.tmp");
   private static final int FORMAT_VERSION = 3;
   private static final byte SCHEME_PLAIN = 0;
   private static final byte SCHEME_DPAPI = 1;
   private static final byte SCHEME_AESKEY = 2;
   private static final int MAX_FIELD_LEN = 16384;
   private static volatile MmIdentity instance;
   private final KeyPair keyPair;
   private final byte[] publicSpki;
   private final byte[] fullFingerprint;
   private final byte[] senderFp;
   private final String shortFingerprint;
   private final KeyPair x25519KeyPair;
   private final byte[] x25519Spki;

   private MmIdentity(KeyPair keyPair, KeyPair x25519KeyPair) {
      this.keyPair = keyPair;
      this.publicSpki = MmCrypto.spki(keyPair.getPublic());
      this.fullFingerprint = MmCrypto.sha256(this.publicSpki);
      this.senderFp = MmCrypto.prefix(this.fullFingerprint, 8);
      this.shortFingerprint = formatFingerprint(this.senderFp);
      this.x25519KeyPair = x25519KeyPair;
      this.x25519Spki = MmCrypto.spki(x25519KeyPair.getPublic());
   }

   public static MmIdentity get() {
      MmIdentity local = instance;
      if (local == null) {
         synchronized (MmIdentity.class) {
            if (instance == null) {
               instance = load();
            }

            local = instance;
         }
      }

      return local;
   }

   public PublicKey publicKey() {
      return this.keyPair.getPublic();
   }

   public byte[] publicKeySpki() {
      return (byte[])this.publicSpki.clone();
   }

   public byte[] fullFingerprint() {
      return (byte[])this.fullFingerprint.clone();
   }

   public byte[] senderFp() {
      return (byte[])this.senderFp.clone();
   }

   public String fingerprint() {
      return this.shortFingerprint;
   }

   public byte[] sign(byte[] message) {
      return MmCrypto.ed25519Sign(this.keyPair.getPrivate(), message);
   }

   public byte[] x25519PublicSpki() {
      return (byte[])this.x25519Spki.clone();
   }

   public byte[] agreeWith(byte[] peerX25519Spki) {
      try {
         return MmCrypto.x25519Agree(this.x25519KeyPair.getPrivate(), MmCrypto.x25519PublicFromSpki(peerX25519Spki));
      } catch (Throwable var3) {
         return null;
      }
   }

   public static synchronized void regenerate() {
      MmIdentity created = new MmIdentity(MmCrypto.generateEd25519(), MmCrypto.generateX25519());
      save(created);
      instance = created;
   }

   public static String formatFingerprint(byte[] fp8) {
      String hex = MmCrypto.hex(fp8).toUpperCase(Locale.ROOT);
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < hex.length(); i += 4) {
         if (i > 0) {
            sb.append('-');
         }

         sb.append(hex, i, Math.min(hex.length(), i + 4));
      }

      return sb.toString();
   }

   private static MmIdentity load() {
      if (FILE.exists()) {
         MmIdentity.Loaded primary = tryRead(FILE);
         if (primary != null) {
            if (primary.upgrade) {
               save(primary.identity);
            }

            return primary.identity;
         }

         MmIdentity.Loaded backup = BAK.exists() ? tryRead(BAK) : null;
         if (backup != null) {
            riptide.RiptideClientAddon.LOG.warn("Matchmaking identity was unreadable; restored it from the backup copy");
            save(backup.identity);
            return backup.identity;
         }

         quarantineCorrupt();
         riptide.RiptideClientAddon.LOG
            .error(
               "Matchmaking identity is corrupt or could not be decrypted, and no usable backup exists. The old file was preserved as mm_identity.corrupt and a NEW identity was generated — your fingerprint has changed. (A DPAPI-encrypted identity only decrypts under the SAME Windows user account on the SAME machine; a copied/synced file cannot be used elsewhere.)"
            );
      }

      MmIdentity created = new MmIdentity(MmCrypto.generateEd25519(), MmCrypto.generateX25519());
      save(created);
      return created;
   }

   private static MmIdentity.Loaded tryRead(File f) {
      try {
         Object edSpki;
         try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            int version = in.readInt();
            if (version == 1) {
               MmIdentity id = fromKeys(readBounded(in), readBounded(in), null, null);
               return id == null ? null : new MmIdentity.Loaded(id, true);
            }

            if (version == 2) {
               byte[] spki = readBounded(in);
               int scheme = in.readUnsignedByte();
               byte[] stored = readBounded(in);
               if (spki != null && stored != null) {
                  MmIdentity id = fromKeys(spki, unseal(scheme, stored), null, null);
                  return id == null ? null : new MmIdentity.Loaded(id, true);
               }

               return null;
            }

            if (version == 3) {
               byte[] edSpkix = readBounded(in);
               int scheme = in.readUnsignedByte();
               byte[] edStored = readBounded(in);
               byte[] xSpki = readBounded(in);
               byte[] xStored = readBounded(in);
               if (edSpkix != null && edStored != null && xSpki != null && xStored != null) {
                  byte[] edPkcs8 = unseal(scheme, edStored);
                  byte[] xPkcs8 = unseal(scheme, xStored);
                  MmIdentity id = fromKeys(edSpkix, edPkcs8, xPkcs8 != null ? xSpki : null, xPkcs8);
                  if (id == null) {
                     return null;
                  }

                  boolean upgrade = scheme == 0 || xPkcs8 == null;
                  return new MmIdentity.Loaded(id, upgrade);
               }

               return null;
            }

            edSpki = null;
         }

         return (MmIdentity.Loaded)edSpki;
      } catch (Throwable var15) {
         return null;
      }
   }

   private static byte[] unseal(int scheme, byte[] stored) {
      return switch (scheme) {
         case 1 -> WinDpapi.unprotect(stored);
         case 2 -> aesKeyFileOpen(stored);
         default -> stored;
      };
   }

   private static MmIdentity fromKeys(byte[] edSpki, byte[] edPkcs8, byte[] xSpki, byte[] xPkcs8) {
      if (edSpki != null && edPkcs8 != null) {
         try {
            PublicKey edPub = MmCrypto.ed25519PublicFromSpki(edSpki);
            PrivateKey edPriv = MmCrypto.ed25519PrivateFromPkcs8(edPkcs8);
            KeyPair ed = new KeyPair(edPub, edPriv);
            KeyPair x;
            if (xSpki != null && xPkcs8 != null) {
               x = new KeyPair(MmCrypto.x25519PublicFromSpki(xSpki), MmCrypto.x25519PrivateFromPkcs8(xPkcs8));
            } else {
               x = MmCrypto.generateX25519();
            }

            return new MmIdentity(ed, x);
         } catch (Throwable var8) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static byte[] readBounded(DataInputStream in) throws IOException {
      int len = in.readInt();
      if (len > 0 && len <= 16384) {
         byte[] out = new byte[len];
         in.readFully(out);
         return out;
      } else {
         return null;
      }
   }

   private static void quarantineCorrupt() {
      try {
         File q = new File(FILE.getParentFile(), "mm_identity.corrupt");
         Files.move(FILE.toPath(), q.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Throwable var1) {
      }
   }

   private static void save(MmIdentity identity) {
      try {
         File dir = FILE.getParentFile();
         if (dir != null) {
            dir.mkdirs();
         }

         if (FILE.exists()) {
            try {
               Files.copy(FILE.toPath(), BAK.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable var12) {
            }
         }

         byte[] edSpki = identity.publicSpki;
         byte[] edPkcs8 = identity.keyPair.getPrivate().getEncoded();
         byte[] xSpki = identity.x25519Spki;
         byte[] xPkcs8 = identity.x25519KeyPair.getPrivate().getEncoded();
         MmIdentity.Sealed sealed = sealBoth(edPkcs8, xPkcs8);

         try (DataOutputStream out = new DataOutputStream(new FileOutputStream(TMP))) {
            out.writeInt(3);
            out.writeInt(edSpki.length);
            out.write(edSpki);
            out.writeByte(sealed.scheme);
            out.writeInt(sealed.edStored.length);
            out.write(sealed.edStored);
            out.writeInt(xSpki.length);
            out.write(xSpki);
            out.writeInt(sealed.xStored.length);
            out.write(sealed.xStored);
            out.flush();
         }

         Arrays.fill(edPkcs8, (byte)0);
         Arrays.fill(xPkcs8, (byte)0);
         restrictToOwner(TMP);
         atomicReplace(TMP, FILE);
         restrictToOwner(FILE);
      } catch (Throwable var13) {
         riptide.RiptideClientAddon.LOG.error("Failed to persist Matchmaking identity", var13);
      }
   }

   private static MmIdentity.Sealed sealBoth(byte[] edPkcs8, byte[] xPkcs8) {
      byte[] e = WinDpapi.protect(edPkcs8);
      byte[] x = WinDpapi.protect(xPkcs8);
      if (e != null && x != null) {
         return new MmIdentity.Sealed((byte)1, e, x);
      } else {
         e = aesKeyFileSeal(edPkcs8);
         x = aesKeyFileSeal(xPkcs8);
         if (e != null && x != null) {
            return new MmIdentity.Sealed((byte)2, e, x);
         } else {
            riptide.RiptideClientAddon.LOG
               .warn(
                  "Matchmaking identity stored WITHOUT encryption (no OS encryption and the key file could not be written); the private keys are protected only by file permissions."
               );
            return new MmIdentity.Sealed((byte)0, edPkcs8, xPkcs8);
         }
      }
   }

   private static void atomicReplace(File tmp, File target) throws IOException {
      try {
         Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException var3) {
         Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static byte[] aesKeyFileSeal(byte[] pkcs8) {
      try {
         byte[] key = getOrCreateAesKey();
         if (key == null) {
            return null;
         } else {
            byte[] nonce = MmCrypto.randomBytes(12);
            byte[] ct = MmCrypto.aesGcmSeal(key, nonce, pkcs8, null);
            byte[] out = new byte[12 + ct.length];
            System.arraycopy(nonce, 0, out, 0, 12);
            System.arraycopy(ct, 0, out, 12, ct.length);
            return out;
         }
      } catch (Throwable var5) {
         return null;
      }
   }

   private static byte[] aesKeyFileOpen(byte[] stored) {
      try {
         if (stored != null && stored.length >= 28) {
            byte[] key = readAesKey();
            if (key == null) {
               return null;
            } else {
               byte[] nonce = Arrays.copyOfRange(stored, 0, 12);
               byte[] ct = Arrays.copyOfRange(stored, 12, stored.length);
               return MmCrypto.aesGcmOpen(key, nonce, ct, null);
            }
         } else {
            return null;
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   private static byte[] getOrCreateAesKey() {
      byte[] existing = readAesKey();
      if (existing != null) {
         return existing;
      } else {
         try {
            byte[] key = MmCrypto.randomBytes(32);
            File dir = KEY_FILE.getParentFile();
            if (dir != null) {
               dir.mkdirs();
            }

            Files.write(KEY_TMP.toPath(), key);
            restrictToOwner(KEY_TMP);
            atomicReplace(KEY_TMP, KEY_FILE);
            restrictToOwner(KEY_FILE);
            return key;
         } catch (Throwable var3) {
            return null;
         }
      }
   }

   private static byte[] readAesKey() {
      try {
         if (!KEY_FILE.exists()) {
            return null;
         } else {
            byte[] k = Files.readAllBytes(KEY_FILE.toPath());
            return k != null && k.length == 32 ? k : null;
         }
      } catch (Throwable var1) {
         return null;
      }
   }

   private static void restrictToOwner(File f) {
      try {
         f.setReadable(false, false);
         f.setReadable(true, true);
         f.setWritable(false, false);
         f.setWritable(true, true);
      } catch (Throwable var2) {
      }
   }

   private record Loaded(MmIdentity identity, boolean upgrade) {
   }

   private record Sealed(byte scheme, byte[] edStored, byte[] xStored) {
   }
}
