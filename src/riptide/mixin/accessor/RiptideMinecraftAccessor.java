package riptide.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Minecraft.class})
public interface RiptideMinecraftAccessor {
   @Mutable
   @Accessor("user")
   void riptide$setUser(User var1);

   @Mutable
   @Accessor("profileKeyPairManager")
   void riptide$setProfileKeyPairManager(ProfileKeyPairManager var1);

   @Mutable
   @Accessor("userApiService")
   void riptide$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("skinManager")
   void riptide$setSkinManager(SkinManager var1);

   @Mutable
   @Accessor("playerSocialManager")
   void riptide$setPlayerSocialManager(PlayerSocialManager var1);

   @Mutable
   @Accessor("remoteFriendListUpdateHandler")
   void riptide$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler var1);

   @Mutable
   @Accessor("reportingContext")
   void riptide$setReportingContext(ReportingContext var1);

   @Mutable
   @Accessor("profileFuture")
   void riptide$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("services")
   void riptide$setServices(Services var1);

   @Accessor("rightClickDelay")
   int riptide$getRightClickDelay();

   @Accessor("rightClickDelay")
   void riptide$setRightClickDelay(int var1);

   @Accessor("missTime")
   int riptide$getMissTime();

   @Invoker("startAttack")
   boolean riptide$startAttack();
}
