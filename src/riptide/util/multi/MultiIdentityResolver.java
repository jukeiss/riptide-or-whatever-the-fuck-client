package riptide.util.multi;

import com.mojang.authlib.Environment;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountSessionSwitcher;
import riptide.util.RiptideAccountType;
import riptide.util.RiptideAuthNetwork;

final class MultiIdentityResolver {
   private static final Environment ALTENING_ENVIRONMENT = new Environment(
      "http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening"
   );

   private MultiIdentityResolver() {
   }

   static MultiIdentityResolver.Identity resolve(String accountId) {
      Minecraft minecraft = Minecraft.getInstance();
      if ("default".equals(accountId)) {
         User user = RiptideAccountSessionSwitcher.getOriginalUser();
         YggdrasilAuthenticationService authentication = new YggdrasilAuthenticationService(RiptideAuthNetwork.directProxy());
         Services services = Services.create(authentication, minecraft.gameDirectory);
         return new MultiIdentityResolver.Identity(
            accountId,
            user.getAccessToken().isBlank() ? RiptideAccountType.Cracked : RiptideAccountType.Session,
            user,
            services.sessionService(),
            keyManager(minecraft, authentication, user),
            profileKeyValidator(services)
         );
      } else {
         RiptideAccount stored = RiptideAccountManager.get().findById(accountId);
         if (stored == null) {
            throw new IllegalStateException("Account no longer exists");
         } else {
            RiptideAccount account = copy(stored);
            if (!account.fetchInfoSilently()) {
               throw new IllegalStateException(account.lastError().isBlank() ? "Account authentication failed" : account.lastError());
            } else {
               RiptideAccountManager.get().applyResolvedCredentials(account);
               User user = new User(account.username, parseProfileId(account), accessToken(account), Optional.empty(), Optional.empty());
               YggdrasilAuthenticationService authentication = account.type == RiptideAccountType.TheAltening
                  ? new YggdrasilAuthenticationService(minecraft.getProxy(), ALTENING_ENVIRONMENT)
                  : new YggdrasilAuthenticationService(RiptideAuthNetwork.directProxy());
               YggdrasilAuthenticationService mojangAuth = new YggdrasilAuthenticationService(RiptideAuthNetwork.directProxy());
               Services services = Services.create(authentication, minecraft.gameDirectory);
               return new MultiIdentityResolver.Identity(
                  account.id, account.type, user, services.sessionService(), keyManager(minecraft, mojangAuth, user), profileKeyValidator(services)
               );
            }
         }
      }
   }

   private static ProfileKeyPairManager keyManager(Minecraft minecraft, YggdrasilAuthenticationService auth, User user) {
      if (user.getAccessToken() != null && !user.getAccessToken().isBlank()) {
         try {
            UserApiService userApi = auth.createUserApiService(user.getAccessToken());
            return ProfileKeyPairManager.create(userApi, user, minecraft.gameDirectory.toPath());
         } catch (RuntimeException var4) {
            return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
         }
      } else {
         return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
      }
   }

   private static SignatureValidator profileKeyValidator(Services services) {
      try {
         return services.canValidateProfileKeys() ? services.profileKeySignatureValidator() : null;
      } catch (RuntimeException var2) {
         return null;
      }
   }

   private static UUID parseProfileId(RiptideAccount account) {
      if (account.uuid != null && !account.uuid.isBlank()) {
         try {
            return UndashedUuid.fromStringLenient(account.uuid);
         } catch (RuntimeException var4) {
            try {
               return UUID.fromString(account.uuid);
            } catch (RuntimeException var3) {
            }
         }
      }

      return UUIDUtil.createOfflinePlayerUUID(account.username);
   }

   private static String accessToken(RiptideAccount account) {
      if (account.type == RiptideAccountType.TheAltening) {
         return safe(account.sessionToken);
      } else {
         return account.type != RiptideAccountType.Cracked && account.type != RiptideAccountType.Generated ? safe(account.token) : "";
      }
   }

   private static RiptideAccount copy(RiptideAccount source) {
      RiptideAccount copy = new RiptideAccount();
      copy.id = source.stableId();
      copy.type = source.type;
      copy.label = safe(source.label);
      copy.token = safe(source.token);
      copy.sessionToken = safe(source.sessionToken);
      copy.username = safe(source.username);
      copy.uuid = safe(source.uuid);
      copy.sessionTokenExpiresAt = source.sessionTokenExpiresAt;
      return copy;
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }

   record Identity(
      String accountId,
      RiptideAccountType type,
      User user,
      MinecraftSessionService sessionService,
      ProfileKeyPairManager keyPairManager,
      SignatureValidator profileKeyValidator
   ) {
   }
}
