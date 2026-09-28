package riptide.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import riptide.util.RiptideRemoteView;
import riptide.util.multi.MultiPilot;

@Mixin({ItemInHandRenderer.class})
public class RiptidePilotHandItemMixin {
   private static AbstractClientPlayer riptide$armOwner(AbstractClientPlayer fallback) {
      return RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot()) instanceof AbstractClientPlayer clientBot ? clientBot : fallback;
   }

   @ModifyVariable(
      method = {"submitArmWithItem"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0,
      require = 0
   )
   private AbstractClientPlayer riptide$botArmOwner(AbstractClientPlayer player) {
      return riptide$armOwner(player);
   }

   @Redirect(
      method = {"renderPlayerArm"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"
      ),
      require = 0
   )
   private AvatarRenderer<AbstractClientPlayer> riptide$botArmRenderer(EntityRenderDispatcher dispatcher, AbstractClientPlayer player) {
      return dispatcher.getPlayerRenderer(riptide$armOwner(player));
   }

   @Redirect(
      method = {"renderPlayerArm"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"
      ),
      require = 0
   )
   private PlayerSkin riptide$botArmSkin(AbstractClientPlayer player) {
      return riptide$armOwner(player).getSkin();
   }

   @Redirect(
      method = {"renderPlayerArm"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"
      ),
      require = 0
   )
   private boolean riptide$botArmSleeve(AbstractClientPlayer player, PlayerModelPart part) {
      return riptide$armOwner(player).isModelPartShown(part);
   }

   @Redirect(
      method = {"renderMapHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"
      ),
      require = 0
   )
   private AvatarRenderer<AbstractClientPlayer> riptide$botMapArmRenderer(EntityRenderDispatcher dispatcher, AbstractClientPlayer player) {
      return dispatcher.getPlayerRenderer(riptide$armOwner(player));
   }

   @Redirect(
      method = {"renderMapHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"
      ),
      require = 0
   )
   private PlayerSkin riptide$botMapArmSkin(LocalPlayer player) {
      return riptide$armOwner(player).getSkin();
   }

   @Redirect(
      method = {"renderMapHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"
      ),
      require = 0
   )
   private boolean riptide$botMapArmSleeve(LocalPlayer player, PlayerModelPart part) {
      return riptide$armOwner(player).isModelPartShown(part);
   }

   @Redirect(
      method = {"renderOneHandedMap", "renderTwoHandedMap"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"
      ),
      require = 0
   )
   private boolean riptide$botMapArmInvisible(LocalPlayer player) {
      return riptide$armOwner(player).isInvisible();
   }

   @Redirect(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"
      ),
      require = 0
   )
   private ItemStack riptide$tickMain(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
   }

   @Redirect(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"
      ),
      require = 0
   )
   private ItemStack riptide$tickOff(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
   }

   @Redirect(
      method = {"submitHandsWithItems"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"
      ),
      require = 0
   )
   private float riptide$botSwing(LocalPlayer player, float partialTicks) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getAttackAnim(partialTicks) : player.getAttackAnim(partialTicks);
   }

   @Redirect(
      method = {"evaluateWhichHandsToRender"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"
      ),
      require = 0
   )
   private static ItemStack riptide$evalMain(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
   }

   @Redirect(
      method = {"evaluateWhichHandsToRender"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"
      ),
      require = 0
   )
   private static ItemStack riptide$evalOff(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
   }

   @Redirect(
      method = {"evaluateWhichHandsToRender"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"
      ),
      require = 0
   )
   private static boolean riptide$evalUsing(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.isUsingItem() : player.isUsingItem();
   }

   @Redirect(
      method = {"selectionUsingItemWhileHoldingBowLike"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"
      ),
      require = 0
   )
   private static InteractionHand riptide$bowHand(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getUsedItemHand() : player.getUsedItemHand();
   }

   @Redirect(
      method = {"selectionUsingItemWhileHoldingBowLike"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"
      ),
      require = 0
   )
   private static ItemStack riptide$bowOff(LocalPlayer player) {
      Player bot = RiptideRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
      return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
   }
}
