package riptide.util.mm;

public final class LobbySettings {
   public static final int USER_MAX_PLAYERS = 4;
   public static final int BLUE_MAX_PLAYERS = 4;
   public static final int NOTBROKE_MAX_PLAYERS = 8;
   public static final int GOLD_MAX_PLAYERS = 50;
   public static final int AQUA_MAX_PLAYERS = 50;
   public static final int ADMIN_MAX_PLAYERS = 500;
   public static final int MAX_PLAYERS = 500;
   public final String name;
   public final boolean isPublic;
   public final int maxPlayers;
   public final char[] passphrase;
   public final String server;
   public final String plugins;
   public final boolean announcement;

   public LobbySettings(String name, boolean isPublic, int maxPlayers, char[] passphrase) {
      this(name, isPublic, maxPlayers, passphrase, "", "", false);
   }

   public LobbySettings(String name, boolean isPublic, int maxPlayers, char[] passphrase, String server, String plugins) {
      this(name, isPublic, maxPlayers, passphrase, server, plugins, false);
   }

   public LobbySettings(String name, boolean isPublic, int maxPlayers, char[] passphrase, String server, String plugins, boolean announcement) {
      this.name = name != null && !name.isBlank() ? name.trim() : "Lobby";
      this.isPublic = isPublic;
      this.maxPlayers = maxPlayers <= 0 ? 500 : Math.max(2, Math.min(500, maxPlayers));
      this.passphrase = passphrase;
      this.server = server == null ? "" : server.trim();
      this.plugins = plugins == null ? "" : plugins.trim();
      this.announcement = announcement;
   }
}
