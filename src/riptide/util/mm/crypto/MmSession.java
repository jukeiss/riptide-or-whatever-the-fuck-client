package riptide.util.mm.crypto;

import java.util.concurrent.atomic.AtomicLong;

public final class MmSession {
   private final AtomicLong seq = new AtomicLong(0L);

   public long nextSeq() {
      return this.seq.incrementAndGet();
   }

   public byte[] nextNonce() {
      return MmCrypto.randomBytes(12);
   }
}
