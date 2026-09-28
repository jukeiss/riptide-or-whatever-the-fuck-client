package riptide.modules;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class AntiAfkModule extends Module {
   private static final double MOVE_EPSILON = 0.01;
   private long nextActionMs;
   private double lastX;
   private double lastZ;
   private float lastYaw;
   private long idleSinceMs;
   private boolean actedRecently;

   public AntiAfkModule() {
      super("anti-afk", "AntiAFK", ModuleCategory.MISC, "Makes small movements while you are idle so the server does not mark you away.");
      this.add(
         new IntSetting("idle", "Idle Before", 30, 5, 600, 5).unit("s").description("How long you must be still before it starts.").group("General").build()
      );
      this.add(new IntSetting("interval", "Interval", 20, 3, 300, 1).unit("s").description("Roughly how often to act once idle.").group("General").build());
      this.add(
         new IntSetting("jitter", "Jitter", 40, 0, 100, 5).unit("%").description("How much the gap varies, so it is not a metronome.").group("General").build()
      );
      this.add(new BoolSetting("rotate", "Look Around", true).description("Turn a few degrees.").group("Actions").build());
      this.add(new BoolSetting("swing", "Swing Arm", true).description("Swing your hand.").group("Actions").build());
      this.add(
         new BoolSetting("sprint", "Tap Sprint", false)
            .description("Flick sprint on and off. Some checks only watch for state changes.")
            .group("Actions")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.idleSinceMs = System.currentTimeMillis();
      this.scheduleNext();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.getConnection() != null) {
         long var2 = System.currentTimeMillis();
         if (this.movedSinceLastTick(var1)) {
            this.idleSinceMs = var2;
            this.scheduleNext();
         } else if (var2 - this.idleSinceMs >= this.integer("idle") * 1000L) {
            if (var2 >= this.nextActionMs) {
               this.scheduleNext();
               this.act(var1);
            }
         }
      }
   }

   private boolean movedSinceLastTick(Minecraft var1) {
      double var2 = var1.player.getX();
      double var4 = var1.player.getZ();
      float var6 = var1.player.getYRot();
      boolean var7 = this.justActed();
      boolean var8 = Math.abs(var2 - this.lastX) > 0.01 || Math.abs(var4 - this.lastZ) > 0.01 || Math.abs(var6 - this.lastYaw) > 0.5F && !var7;
      this.lastX = var2;
      this.lastZ = var4;
      this.lastYaw = var6;
      return var8;
   }

   private boolean justActed() {
      boolean var1 = this.actedRecently;
      this.actedRecently = false;
      return var1;
   }

   private void act(Minecraft var1) {
      ThreadLocalRandom var2 = ThreadLocalRandom.current();
      if (this.bool("rotate")) {
         float var3 = var1.player.getYRot() + var2.nextFloat(-12.0F, 12.0F);
         float var4 = Math.max(-80.0F, Math.min(80.0F, var1.player.getXRot() + var2.nextFloat(-6.0F, 6.0F)));
         var1.player.setYRot(var3);
         var1.player.setXRot(var4);
         this.actedRecently = true;
         this.lastYaw = var3;
         var1.getConnection().send(new Rot(var3, var4, var1.player.onGround(), false));
      }

      if (this.bool("swing")) {
         var1.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
      }

      if (this.bool("sprint")) {
         var1.getConnection().send(new ServerboundPlayerCommandPacket(var1.player, Action.START_SPRINTING));
         var1.getConnection().send(new ServerboundPlayerCommandPacket(var1.player, Action.STOP_SPRINTING));
      }
   }

   private void scheduleNext() {
      this.nextActionMs = System.currentTimeMillis()
         + nextDelay(this.integer("interval") * 1000L, this.integer("jitter"), ThreadLocalRandom.current().nextDouble());
   }

   static long nextDelay(long var0, int var2, double var3) {
      long var5 = Math.max(1000L, var0);
      double var7 = Math.max(0, Math.min(100, var2)) / 100.0;
      long var9 = (long)(var5 * var7);
      if (var9 <= 0L) {
         return var5;
      } else {
         long var11 = Math.round((var3 * 2.0 - 1.0) * var9);
         return Math.max(1000L, var5 + var11);
      }
   }
}
