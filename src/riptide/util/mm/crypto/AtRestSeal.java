package riptide.util.mm.crypto;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class AtRestSeal {
   private static final byte SCHEME_PLAIN = 0;
   private static final byte SCHEME_DPAPI = 1;
   private static final byte SCHEME_AESKEY = 2;

   private AtRestSeal() {
   }

   private static File keyFile() {
      try {
         if (riptide.RiptideClientAddon.FOLDER != null) {
            return new File(riptide.RiptideClientAddon.FOLDER, "riptide-seal.key");
         }
      } catch (Throwable var1) {
      }

      File fallback = new File(System.getProperty("java.io.tmpdir", "."), "riptide-seal");
      return new File(fallback, "riptide-seal.key");
   }

   private static File keyTmp() {
      File key = keyFile();
      return new File(key.getParentFile(), key.getName() + ".tmp");
   }

   public static byte[] seal(byte[] plain) {
      if (plain == null) {
         plain = new byte[0];
      }

      byte[] dpapi = WinDpapi.protect(plain);
      if (dpapi != null) {
         return prepend((byte)1, dpapi);
      } else {
         byte[] aes = aesSeal(plain);
         return aes != null ? prepend((byte)2, aes) : prepend((byte)0, plain);
      }
   }

   public static byte[] sealStrict(byte[] plain) {
      byte[] sealed = seal(plain);
      return sealed.length > 0 && sealed[0] != 0 ? sealed : null;
   }

   public static byte[] unseal(byte[] stored) {
      if (stored != null && stored.length >= 1) {
         byte[] payload = Arrays.copyOfRange(stored, 1, stored.length);

         return switch (stored[0]) {
            case 0 -> payload;
            case 1 -> WinDpapi.unprotect(payload);
            case 2 -> aesOpen(payload);
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static byte[] unsealStrict(byte[] stored) {
      return stored != null && stored.length >= 1 && stored[0] != 0 ? unseal(stored) : null;
   }

   private static byte[] prepend(byte scheme, byte[] body) {
      byte[] out = new byte[body.length + 1];
      out[0] = scheme;
      System.arraycopy(body, 0, out, 1, body.length);
      return out;
   }

   private static byte[] aesSeal(byte[] plain) {
      try {
         byte[] key = getOrCreateAesKey();
         if (key == null) {
            return null;
         } else {
            byte[] nonce = MmCrypto.randomBytes(12);
            byte[] ct = MmCrypto.aesGcmSeal(key, nonce, plain, null);
            byte[] out = new byte[12 + ct.length];
            System.arraycopy(nonce, 0, out, 0, 12);
            System.arraycopy(ct, 0, out, 12, ct.length);
            return out;
         }
      } catch (Throwable var5) {
         return null;
      }
   }

   private static byte[] aesOpen(byte[] stored) {
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
            File keyFile = keyFile();
            File keyTmp = keyTmp();
            byte[] key = MmCrypto.randomBytes(32);
            File dir = keyFile.getParentFile();
            if (dir != null) {
               dir.mkdirs();
            }

            Files.write(keyTmp.toPath(), key);
            if (!restrictToOwner(keyTmp)) {
               Files.deleteIfExists(keyTmp.toPath());
               return null;
            } else {
               try {
                  Files.move(keyTmp.toPath(), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               } catch (AtomicMoveNotSupportedException var6) {
                  Files.move(keyTmp.toPath(), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }

               return !restrictToOwner(keyFile) ? null : key;
            }
         } catch (Throwable var7) {
            return null;
         }
      }
   }

   private static byte[] readAesKey() {
      try {
         File keyFile = keyFile();
         if (!keyFile.exists()) {
            return null;
         } else if (!restrictToOwner(keyFile)) {
            return null;
         } else {
            byte[] k = Files.readAllBytes(keyFile.toPath());
            return k != null && k.length == 32 ? k : null;
         }
      } catch (Throwable var2) {
         return null;
      }
   }

   private static boolean restrictToOwner(File f) {
      try {
         PosixFileAttributeView posix = Files.getFileAttributeView(f.toPath(), PosixFileAttributeView.class);
         if (posix != null) {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(f.toPath(), ownerOnly);
            return Files.getPosixFilePermissions(f.toPath()).equals(ownerOnly);
         } else {
            f.setReadable(false, false);
            f.setWritable(false, false);
            return f.setReadable(true, true) && f.setWritable(true, true);
         }
      } catch (Throwable var3) {
         return false;
      }
   }
}
