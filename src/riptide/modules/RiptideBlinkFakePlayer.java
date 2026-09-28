package riptide.modules;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

public final class RiptideBlinkFakePlayer extends RemotePlayer {
   private static final int CLONE_ENTITY_ID = -4344907;
   private final PlayerInfo info;

   public RiptideBlinkFakePlayer(ClientLevel level, LocalPlayer source) {
      super(level, source.getGameProfile());
      this.setId(-4344907);
      this.setUUID(UUID.randomUUID());
      this.getInventory().replaceWith(source.getInventory());

      try {
         this.getAttributes().assignAllValues(source.getAttributes());
      } catch (Throwable var6) {
      }

      this.setPose(source.getPose());
      PlayerInfo resolved = null;

      try {
         if (Minecraft.getInstance().getConnection() != null) {
            resolved = Minecraft.getInstance().getConnection().getPlayerInfo(source.getGameProfile().id());
         }
      } catch (Throwable var5) {
      }

      this.info = resolved;
   }

   public void freezeHeadRotation(float headYaw, float bodyYaw) {
      this.yHeadRot = headYaw;
      this.yHeadRotO = headYaw;
      this.yBodyRot = bodyYaw;
      this.yBodyRotO = bodyYaw;
   }

   protected PlayerInfo getPlayerInfo() {
      return this.info != null ? this.info : super.getPlayerInfo();
   }

   protected void doPush(Entity entity) {
   }

   public void tick() {
   }

   public boolean isPickable() {
      return false;
   }
}
