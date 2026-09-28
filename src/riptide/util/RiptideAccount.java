package riptide.util;

import com.google.gson.JsonObject;
import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import de.florianreuth.waybackauthlib.InvalidCredentialsException;
import de.florianreuth.waybackauthlib.WaybackAuthLib;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import riptide.util.mm.crypto.AtRestSeal;

public class RiptideAccount {
   private static final Environment ALTENING_ENVIRONMENT = new Environment(
      "http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening"
   );
   private static final String ALTENING_PASSWORD = "Riptide Client";
   public String id = UUID.randomUUID().toString();
   public RiptideAccountType type = RiptideAccountType.Cracked;
   public String label = "";
   public String token = "";
   public String sessionToken = "";
   public String username = "";
   public String uuid = "";
   public String password = "";
   public long sessionTokenExpiresAt;
   private static final long SESSION_REFRESH_MARGIN_MS = 300000L;
   public transient volatile RiptideAccount.CheckStatus checkStatus = RiptideAccount.CheckStatus.UNKNOWN;
   private transient String lastError = "";
   private transient WaybackAuthLib alteningAuth;
   private transient String alteningAuthToken = "";
   private transient boolean suppressMessages;
   transient boolean generatedStableId;

   public RiptideAccount() {
   }

   public RiptideAccount(Tag tag) {
      if (tag instanceof CompoundTag compoundTag) {
         this.fromTag(compoundTag);
      }
   }

   public String displayName() {
      if (this.username != null && !this.username.isBlank()) {
         return this.username;
      } else {
         return this.label == null ? "" : this.label;
      }
   }

   public boolean fetchInfo() {
      this.lastError = "";

      return switch (this.type) {
         case Cracked, Generated -> this.fetchCracked();
         case Session -> this.fetchSession();
         case Microsoft -> this.fetchMicrosoft();
         case TheAltening -> this.fetchTheAltening();
      };
   }

   public boolean fetchInfoSilently() {
      this.suppressMessages = true;

      boolean var1;
      try {
         var1 = this.fetchInfo();
      } finally {
         this.suppressMessages = false;
      }

      return var1;
   }

   public boolean login() {
      this.lastError = "";
      boolean needFetch = this.username == null || this.username.isBlank() || this.type == RiptideAccountType.Microsoft && !this.hasFreshSessionToken();
      if (needFetch && !this.fetchInfo()) {
         return false;
      } else {
         return switch (this.type) {
            case Cracked, Generated -> {
               boolean ok = RiptideAccountSessionSwitcher.setSession(
                  new User(this.username, UUIDUtil.createOfflinePlayerUUID(this.username), "", Optional.empty(), Optional.empty())
               );
               if (!ok) {
                  this.lastError = RiptideAccountSessionSwitcher.lastError();
               }

               yield ok;
            }
            case Session, Microsoft -> {
               if (this.token != null && !this.token.isBlank() && this.uuid != null && !this.uuid.isBlank()) {
                  boolean ok = RiptideAccountSessionSwitcher.setSession(
                     new User(this.username, UndashedUuid.fromStringLenient(this.uuid), this.token, Optional.empty(), Optional.empty())
                  );
                  if (!ok) {
                     this.lastError = RiptideAccountSessionSwitcher.lastError();
                  }

                  yield ok;
               } else {
                  this.lastError = "missing token or uuid";
                  yield false;
               }
            }
            case TheAltening -> this.loginTheAltening();
         };
      }
   }

   private boolean fetchCracked() {
      if (this.label != null && !this.label.isBlank()) {
         this.username = this.label.trim();
         this.uuid = UUIDUtil.createOfflinePlayerUUID(this.username).toString();
         return true;
      } else {
         this.lastError = "missing username";
         return false;
      }
   }

   private boolean fetchSession() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            JsonObject profile = RiptideHttp.getJsonDirect("https://api.minecraftservices.com/minecraft/profile", this.token);
            if (profile != null && profile.has("id") && profile.has("name")) {
               this.uuid = profile.get("id").getAsString();
               this.username = profile.get("name").getAsString();
               return true;
            } else {
               this.lastError = "profile response missing id/name";
               return false;
            }
         } catch (Exception var2) {
            this.lastError = shortError(var2);
            return false;
         }
      } else {
         this.lastError = "missing access token";
         return false;
      }
   }

   public boolean hasFreshSessionToken() {
      return this.token != null
         && !this.token.isBlank()
         && this.username != null
         && !this.username.isBlank()
         && this.uuid != null
         && !this.uuid.isBlank()
         && this.sessionTokenExpiresAt > System.currentTimeMillis() + 300000L;
   }

   private boolean fetchMicrosoft() {
      if (this.label == null || this.label.isBlank()) {
         this.lastError = "missing refresh token";
         return false;
      } else if (this.hasFreshSessionToken()) {
         return true;
      } else {
         RiptideMicrosoftLogin.LoginData data = RiptideMicrosoftLogin.login(this.label);
         if (data.isGood()) {
            this.token = data.mcToken;
            this.uuid = data.uuid;
            this.username = data.username;
            this.label = data.newRefreshToken;
            this.sessionTokenExpiresAt = data.expiresAtEpochMs();
            return true;
         } else {
            this.lastError = data.error != null && !data.error.isBlank() ? data.error : "Microsoft login failed";
            return false;
         }
      }
   }

   private boolean fetchTheAltening() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            WaybackAuthLib auth = this.createTheAlteningAuth();
            auth.logIn();
            this.username = auth.getCurrentProfile().name();
            this.uuid = auth.getCurrentProfile().id().toString();
            this.sessionToken = auth.getAccessToken() == null ? "" : auth.getAccessToken();
            this.alteningAuth = auth;
            this.alteningAuthToken = this.token;
            return true;
         } catch (InvalidCredentialsException var2) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var2);
            if (!this.suppressMessages) {
               RiptideClientMessaging.sendPrefixed("Invalid TheAltening credentials.");
            }

            return false;
         } catch (Exception var3) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var3);
            if (!this.suppressMessages) {
               RiptideClientMessaging.sendPrefixed("Failed to fetch TheAltening account info.");
            }

            return false;
         }
      } else {
         this.lastError = "missing TheAltening token";
         return false;
      }
   }

   private boolean loginTheAltening() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            boolean cached = this.validCachedTheAlteningAuth();
            WaybackAuthLib auth = cached ? this.alteningAuth : this.createTheAlteningAuth();
            if (!cached) {
               auth.logIn();
               this.alteningAuth = auth;
               this.alteningAuthToken = this.token;
            }

            this.username = auth.getCurrentProfile().name();
            this.uuid = auth.getCurrentProfile().id().toString();
            this.sessionToken = auth.getAccessToken() == null ? "" : auth.getAccessToken();
            boolean ok = RiptideAccountSessionSwitcher.setSession(
               new User(auth.getCurrentProfile().name(), auth.getCurrentProfile().id(), this.sessionToken, Optional.empty(), Optional.empty()),
               new YggdrasilAuthenticationService(Minecraft.getInstance().getProxy(), ALTENING_ENVIRONMENT)
            );
            if (!ok) {
               this.lastError = RiptideAccountSessionSwitcher.lastError();
            }

            return ok;
         } catch (Exception var4) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var4);
            RiptideClientMessaging.sendPrefixed("Failed to login with TheAltening.");
            return false;
         }
      } else {
         this.lastError = "missing TheAltening token";
         return false;
      }
   }

   private WaybackAuthLib createTheAlteningAuth() {
      WaybackAuthLib auth = new WaybackAuthLib(ALTENING_ENVIRONMENT.servicesHost());
      auth.setUsername(this.token);
      auth.setPassword("Riptide Client");
      return auth;
   }

   private boolean validCachedTheAlteningAuth() {
      return this.alteningAuth != null
         && this.token != null
         && !this.token.isBlank()
         && this.token.equals(this.alteningAuthToken)
         && this.alteningAuth.getCurrentProfile() != null
         && this.alteningAuth.getAccessToken() != null
         && !this.alteningAuth.getAccessToken().isBlank();
   }

   private void clearTheAlteningAuth() {
      this.alteningAuth = null;
      this.alteningAuthToken = "";
   }

   public String lastError() {
      return this.lastError == null ? "" : this.lastError;
   }

   public String failureSuffix() {
      String error = this.lastError();
      return error.isBlank() ? "" : " (" + error + ")";
   }

   private static String shortError(Throwable error) {
      if (error == null) {
         return "unknown error";
      } else {
         String name = error.getClass().getSimpleName();
         String message = error.getMessage();
         if (message != null && !message.isBlank()) {
            message = message.replace('\n', ' ').replace('\r', ' ').trim();
            if (message.length() > 120) {
               message = message.substring(0, 117) + "...";
            }

            return name + ": " + message;
         } else {
            return name;
         }
      }
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.stableId());
      tag.putString("type", this.type == null ? RiptideAccountType.Cracked.name() : this.type.name());
      tag.putString("username", this.username == null ? "" : this.username);
      tag.putString("uuid", this.uuid == null ? "" : this.uuid);
      tag.putString("encToken", sealString(this.token));
      tag.putString("encSessionToken", sealString(this.sessionToken));
      tag.putString("encPassword", sealString(this.password));
      if (this.type == RiptideAccountType.Microsoft) {
         tag.putString("encLabel", sealString(this.label));
      } else {
         tag.putString("label", this.label == null ? "" : this.label);
      }

      tag.putLong("sessionExpiresAt", this.sessionTokenExpiresAt);
      return tag;
   }

   public RiptideAccount fromTag(CompoundTag tag) {
      String storedId = tag.getStringOr("id", "");
      if (storedId.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      } else {
         this.id = storedId;
      }

      String typeName = tag.getStringOr("type", RiptideAccountType.Cracked.name());

      try {
         this.type = RiptideAccountType.valueOf(typeName);
      } catch (IllegalArgumentException var6) {
         this.type = RiptideAccountType.Cracked;
      }

      this.username = tag.getStringOr("username", "");
      this.uuid = tag.getStringOr("uuid", "");
      this.token = unsealString(tag.getStringOr("encToken", ""), tag.getStringOr("token", ""));
      this.sessionToken = unsealString(tag.getStringOr("encSessionToken", ""), tag.getStringOr("sessionToken", ""));
      this.password = unsealString(tag.getStringOr("encPassword", ""), tag.getStringOr("password", ""));
      String plainLabel = tag.getStringOr("label", tag.getStringOr("name", ""));
      String encLabel = tag.getStringOr("encLabel", "");
      this.label = encLabel.isEmpty() ? plainLabel : unsealString(encLabel, plainLabel);
      this.sessionTokenExpiresAt = tag.getLongOr("sessionExpiresAt", 0L);
      return this;
   }

   public String stableId() {
      if (this.id == null || this.id.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      }

      return this.id;
   }

   private static String sealString(String value) {
      if (value != null && !value.isEmpty()) {
         byte[] sealed = AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
         return sealed == null ? "" : Base64.getEncoder().encodeToString(sealed);
      } else {
         return "";
      }
   }

   private static String unsealString(String encoded, String plainFallback) {
      String fallback = plainFallback == null ? "" : plainFallback;
      if (encoded != null && !encoded.isEmpty()) {
         try {
            byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
            return plain == null ? fallback : new String(plain, StandardCharsets.UTF_8);
         } catch (Throwable var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj instanceof RiptideAccount account) {
         return this.type != account.type ? false : Objects.equals(this.identityKey(), account.identityKey());
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.type, this.identityKey());
   }

   private String identityKey() {
      return switch (this.type) {
         case Cracked, Generated -> this.username != null && !this.username.isBlank() ? this.username : this.label;
         case Session, TheAltening -> this.token != null && !this.token.isBlank() ? this.token : this.label;
         case Microsoft -> this.label != null && !this.label.isBlank() ? this.label : this.username;
      };
   }

   public static enum CheckStatus {
      UNKNOWN,
      CHECKING,
      VALID,
      EXPIRED;
   }
}
