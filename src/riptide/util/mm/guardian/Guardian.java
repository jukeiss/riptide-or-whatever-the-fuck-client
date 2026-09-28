package riptide.util.mm.guardian;

public interface Guardian {
   boolean isOfficial();

   String attest(String var1, String var2, String var3);

   byte[] unwrapKey(byte[] var1, byte[] var2);
}
