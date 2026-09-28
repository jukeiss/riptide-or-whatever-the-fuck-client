package riptide.util.mm.crypto;

public final class RoomKey {
   private static final String DOMAIN = "riptide/mm/v1";
   private static final byte[] HKDF_SALT = MmCrypto.utf8("riptide/mm/v1/hkdf");
   private final String lobbyId;
   private final boolean isPublic;
   private final String contentTopic;
   private final String controlTopic;
   private final String sysTopic;
   private final byte[] lobbyTag;

   private RoomKey(String lobbyId, boolean isPublic, String contentTopic, String controlTopic, String sysTopic, byte[] lobbyTag) {
      this.lobbyId = lobbyId;
      this.isPublic = isPublic;
      this.contentTopic = contentTopic;
      this.controlTopic = controlTopic;
      this.sysTopic = sysTopic;
      this.lobbyTag = lobbyTag;
   }

   public String lobbyId() {
      return this.lobbyId;
   }

   public boolean isPublic() {
      return this.isPublic;
   }

   public String topic() {
      return this.contentTopic;
   }

   public String contentTopic() {
      return this.contentTopic;
   }

   public String controlTopic() {
      return this.controlTopic;
   }

   public String sysTopic() {
      return this.sysTopic;
   }

   public byte[] lobbyTag() {
      return (byte[])this.lobbyTag.clone();
   }

   public static RoomKey fromServer(String lobbyId, String contentTopic, String controlTopic, String sysTopic, boolean isPublic) {
      if (validTopic(contentTopic) && validTopic(controlTopic) && validTopic(sysTopic)) {
         byte[] tag = MmCrypto.hkdf(HKDF_SALT, MmCrypto.utf8(sysTopic), "mm-lobby-tag", 8);
         return new RoomKey(lobbyId, isPublic, contentTopic, controlTopic, sysTopic, tag);
      } else {
         throw new IllegalArgumentException("invalid server topic");
      }
   }

   private static boolean validTopic(String topic) {
      return topic != null && topic.matches("mm/[0-9a-f]+(?:/sys)?");
   }
}
