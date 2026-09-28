package riptide.util.mm;

import java.util.Comparator;
import riptide.util.mm.crypto.MmIdentity;

public final class LobbyListing {
   public static final Comparator<LobbyListing> DIRECTORY_ORDER = Comparator.<LobbyListing, Boolean>comparing(l -> !l.official)
      .thenComparing(l -> l.name, String.CASE_INSENSITIVE_ORDER);
   public final String lobbyId;
   public volatile String name;
   public volatile int members;
   public volatile int maxMembers;
   public volatile boolean serverShared;
   public volatile boolean official;
   public volatile String server = "";
   public volatile String plugins = "";
   public volatile int hostDupe = -1;
   public volatile boolean announcement;
   public final String hostFpHex;
   public final String hostFingerprint;
   public volatile String hostDid = "";
   public volatile long lastSeenMs;

   public LobbyListing(String lobbyId, byte[] hostFp) {
      this.lobbyId = lobbyId;
      this.hostFpHex = hex(hostFp);
      this.hostFingerprint = MmIdentity.formatFingerprint(hostFp);
   }

   public boolean isFull() {
      return this.maxMembers > 0 && this.members >= this.maxMembers;
   }

   public boolean isFresh(long now) {
      return now - this.lastSeenMs < 45000L;
   }

   private static String hex(byte[] b) {
      StringBuilder sb = new StringBuilder(b.length * 2);

      for (byte x : b) {
         sb.append(Character.forDigit(x >> 4 & 15, 16)).append(Character.forDigit(x & 15, 16));
      }

      return sb.toString();
   }
}
