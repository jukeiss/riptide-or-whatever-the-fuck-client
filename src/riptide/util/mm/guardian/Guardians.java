package riptide.util.mm.guardian;

import java.util.ServiceLoader;

public final class Guardians {
   private static final Guardian INSTANCE = resolve();

   private Guardians() {
   }

   public static Guardian get() {
      return INSTANCE;
   }

   public static boolean official() {
      return INSTANCE.isOfficial();
   }

   private static Guardian resolve() {
      try {
         for (Guardian g : ServiceLoader.load(Guardian.class, Guardian.class.getClassLoader())) {
            if (g != null && g.isOfficial()) {
               return g;
            }
         }
      } catch (Throwable var2) {
      }

      return new Guardians.NoopGuardian();
   }

   private static final class NoopGuardian implements Guardian {
      @Override
      public boolean isOfficial() {
         return false;
      }

      @Override
      public String attest(String nonceB64, String version, String pubkeyB64) {
         return null;
      }

      @Override
      public byte[] unwrapKey(byte[] wrapped, byte[] info) {
         return null;
      }
   }
}
