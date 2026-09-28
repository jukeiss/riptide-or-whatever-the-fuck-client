package riptide.util;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;
import riptide.mixin.accessor.RiptideMinecraftAccessor;

public final class RiptideAccountSessionSwitcher {
   private static User originalUser;
   private static String lastError = "";

   private RiptideAccountSessionSwitcher() {
   }

   public static User getOriginalUser() {
      if (originalUser == null) {
         originalUser = Minecraft.getInstance().getUser();
      }

      return originalUser;
   }

   public static boolean setSession(User user) {
      return setSession(user, new YggdrasilAuthenticationService(RiptideAuthNetwork.directProxy()));
   }

   public static boolean setSession(User user, YggdrasilAuthenticationService authService) {
      lastError = "";

      try {
         Minecraft mc = Minecraft.getInstance();
         if (originalUser == null) {
            originalUser = mc.getUser();
         }

         RiptideMinecraftAccessor accessor = (RiptideMinecraftAccessor)mc;
         YggdrasilAuthenticationService userApiAuthService = new YggdrasilAuthenticationService(RiptideAuthNetwork.directProxy());
         Services services = Services.create(authService, mc.gameDirectory);
         UserApiService apiService = userApiAuthService.createUserApiService(user.getAccessToken());
         FriendsService friendsService = userApiAuthService.createFriendsService(user.getAccessToken());
         RemoteFriendListUpdateHandler friendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, mc);
         Path skinCachePath = mc.gameDirectory.toPath().resolve("assets").resolve("skins");
         accessor.riptide$setServices(services);
         accessor.riptide$setUser(user);
         accessor.riptide$setUserApiService(apiService);
         accessor.riptide$setRemoteFriendListUpdateHandler(friendListUpdateHandler);
         accessor.riptide$setPlayerSocialManager(new PlayerSocialManager(mc, apiService, friendsService, friendListUpdateHandler));
         accessor.riptide$setProfileKeyPairManager(ProfileKeyPairManager.create(apiService, user, mc.gameDirectory.toPath()));
         accessor.riptide$setReportingContext(ReportingContext.create(ReportEnvironment.local(), apiService));
         accessor.riptide$setProfileFuture(
            CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.nonCriticalIoPool())
         );
         accessor.riptide$setSkinManager(
            new SkinManager(skinCachePath, services, new SkinTextureDownloader(RiptideAuthNetwork.directProxy(), mc.getTextureManager(), mc), mc)
         );
         return true;
      } catch (Exception var10) {
         lastError = shortError(var10);
         riptide.RiptideClientAddon.LOG.error("Failed to switch Riptide account session", var10);
         return false;
      }
   }

   public static String lastError() {
      return lastError == null ? "" : lastError;
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
}
