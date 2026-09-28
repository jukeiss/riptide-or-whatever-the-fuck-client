package riptide.modules;

import java.util.Locale;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.mixin.accessor.RiptideEntityAccessor;
import riptide.util.RiptideLiteVariant;

public final class PhaseModule extends Module {
   private static final int SHAPE_NONE = 0;
   private static final int SHAPE_SPIDER = 1;
   private static final int SHAPE_BLINK = 2;
   private static final PhaseModule.ClipMode CLIP = new PhaseModule.ClipMode();
   private static final PhaseModule.IntaveMode INTAVE = new PhaseModule.IntaveMode();
   private static final PhaseModule.BlinkMode BLINK = new PhaseModule.BlinkMode();
   private static final PhaseModule.SpiderMode SPIDER = new PhaseModule.SpiderMode();
   private static volatile PhaseModule instance;
   private static volatile int shapeMode;
   private volatile PhaseModule.PhaseMode active;

   public PhaseModule() {
      super("phase", "Phase", ModuleCategory.MOVEMENT, "Phase through blocks.");
      this.add(new ChoiceSetting("mode", "Mode", "Clip", "Clip", "Intave", "Blink", "Spider").description("Phase method.").build());
      this.add(
         new IntSetting("maximum", "Maximum", 120, 1, 300, 1)
            .unit("ticks")
            .visibleWhen(() -> "Blink".equals(this.choice("mode")))
            .description("Blink tick budget.")
            .build()
      );
      instance = this;
   }

   @Override
   public String info() {
      String mode = this.choice("mode");
      PhaseModule.PhaseMode current = this.active;
      if (current == null) {
         return mode;
      } else {
         String state = current.info();
         return state.isEmpty() ? mode : mode + " " + state;
      }
   }

   @Override
   public void onEnable() {
      this.enterMode(modeFor(this.choice("mode")));
   }

   @Override
   public void onDisable() {
      this.exitMode();
   }

   @Override
   public void onGameJoin() {
      if (this.isEnabled() && this.active == null) {
         this.enterMode(modeFor(this.choice("mode")));
      }
   }

   @Override
   public void onGameLeft() {
      this.exitMode();
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if ("mode".equals(settingId) && this.isEnabled()) {
         this.switchMode();
      }
   }

   @Override
   protected void onSettingsReset() {
      if (this.isEnabled()) {
         this.switchMode();
      }
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         PhaseModule.PhaseMode mode = this.active;
         if (mode != null) {
            mode.preMovementTick();
         }
      }
   }

   @Override
   public void onNetworkMovementTickPre() {
      PhaseModule.PhaseMode mode = this.active;
      if (mode != null) {
         mode.onNetworkMovementTickPre();
      }
   }

   @Override
   public void onPacketProcessFrame() {
      PhaseModule.PhaseMode mode = this.active;
      if (mode != null) {
         mode.onPacketProcessFrame();
      }
   }

   @Override
   public boolean shouldCancelPlayerTick() {
      PhaseModule.PhaseMode mode = this.active;
      return mode != null && mode.shouldCancelPlayerTick();
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      PhaseModule.PhaseMode mode = this.active;
      if (mode != null && packet instanceof ClientboundPlayerPositionPacket) {
         mode.onTeleportPacket();
      }

      return false;
   }

   public static VoxelShape blockShape(VoxelShape original, BlockState state, BlockPos pos) {
      int mode = shapeMode;
      if (mode == 0) {
         return original;
      } else {
         LocalPlayer player = MC.player;
         if (player == null) {
            return original;
         } else {
            return mode == 2 ? BLINK.shape(original, player, pos) : SPIDER.shape(original, state, player, pos);
         }
      }
   }

   private void switchMode() {
      PhaseModule.PhaseMode next = modeFor(this.choice("mode"));
      if (next != this.active) {
         this.exitMode();
         this.enterMode(next);
      }
   }

   private void enterMode(PhaseModule.PhaseMode mode) {
      this.active = mode;
      this.publishShapeMode();
      mode.enable();
   }

   private void exitMode() {
      PhaseModule.PhaseMode mode = this.active;
      this.active = null;
      this.publishShapeMode();
      if (mode != null) {
         mode.disable();
      }
   }

   private void publishShapeMode() {
      PhaseModule.PhaseMode mode = this.active;
      if (mode == SPIDER) {
         shapeMode = 1;
      } else if (mode == BLINK) {
         shapeMode = 2;
      } else {
         shapeMode = 0;
      }
   }

   private static PhaseModule.PhaseMode modeFor(String name) {
      return (PhaseModule.PhaseMode)(switch (name) {
         case "Intave" -> INTAVE;
         case "Blink" -> BLINK;
         case "Spider" -> SPIDER;
         default -> CLIP;
      });
   }

   private static boolean doesCollideAt(LocalPlayer player, Vec3 pos) {
      AABB box = player.getBoundingBox().move(pos.subtract(player.position()));

      for (VoxelShape shape : player.level().getBlockCollisions(player, box)) {
         if (!shape.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static void setDeltaY(LocalPlayer player, double y) {
      Vec3 velocity = player.getDeltaMovement();
      player.setDeltaMovement(velocity.x, y, velocity.z);
   }

   private static final class BlinkMode implements PhaseModule.PhaseMode {
      private static final int MAX_FRUITLESS_CYCLES = 3;
      private final RiptideBlinkManager.HoldPolicy policy = this::classify;
      private volatile PhaseModule.BlinkMode.State state = PhaseModule.BlinkMode.State.WAITING;
      private volatile boolean inCollisionCheck;
      private volatile String standDown;
      private volatile boolean recycleRequested;
      private int currentTicks;
      private boolean reachedWalking;
      private int fruitlessCycles;

      @Override
      public void enable() {
         this.state = PhaseModule.BlinkMode.State.WAITING;
         this.currentTicks = 0;
         this.standDown = null;
         this.recycleRequested = false;
         this.reachedWalking = false;
         this.fruitlessCycles = 0;
         RiptideBlinkManager.addPolicy(this.policy);
      }

      @Override
      public void disable() {
         this.state = PhaseModule.BlinkMode.State.FLUSHING;
         RiptideBlinkManager.flushIncoming();
         this.state = PhaseModule.BlinkMode.State.WAITING;
         RiptideBlinkManager.removePolicy(this.policy);
         RiptideBlinkManager.requestFlush(true, true);
         this.currentTicks = 0;
         this.standDown = null;
         this.recycleRequested = false;
         this.reachedWalking = false;
         this.fruitlessCycles = 0;
      }

      @Override
      public void onNetworkMovementTickPre() {
         LocalPlayer player = Module.MC.player;
         if (player != null) {
            this.inCollisionCheck = true;

            try {
               if (this.state == PhaseModule.BlinkMode.State.WAITING) {
                  if (PhaseModule.doesCollideAt(player, player.position())) {
                     this.state = PhaseModule.BlinkMode.State.PHASING;
                  }
               } else if (this.state == PhaseModule.BlinkMode.State.PHASING && !PhaseModule.doesCollideAt(player, player.position())) {
                  this.state = PhaseModule.BlinkMode.State.WALKING;
                  this.reachedWalking = true;
               }
            } finally {
               this.inCollisionCheck = false;
            }
         }
      }

      @Override
      public void onPacketProcessFrame() {
         String reason = this.standDown;
         if (reason != null) {
            this.standDown = null;
            PhaseModule.instance.disableWithToggleMessage(reason);
         } else if (this.recycleRequested) {
            this.recycleRequested = false;
            this.recycle();
         } else if (this.state == PhaseModule.BlinkMode.State.WALKING) {
            LocalPlayer player = Module.MC.player;
            if (player != null) {
               boolean collides = false;
               this.inCollisionCheck = true;

               try {
                  for (Vec3 pos : RiptideBlinkManager.heldOutgoingPositions()) {
                     if (PhaseModule.doesCollideAt(player, pos)) {
                        collides = true;
                        break;
                     }
                  }
               } finally {
                  this.inCollisionCheck = false;
               }

               if (!collides) {
                  PhaseModule.instance.setEnabled(false);
               }
            }
         }
      }

      @Override
      public boolean shouldCancelPlayerTick() {
         if (this.currentTicks > PhaseModule.instance.integer("maximum")) {
            this.recycleRequested = true;
            return true;
         } else {
            if (this.state == PhaseModule.BlinkMode.State.PHASING || this.state == PhaseModule.BlinkMode.State.WALKING) {
               this.currentTicks++;
            }

            return false;
         }
      }

      private void recycle() {
         this.state = PhaseModule.BlinkMode.State.FLUSHING;
         RiptideBlinkManager.flushIncoming();
         this.state = PhaseModule.BlinkMode.State.WAITING;
         RiptideBlinkManager.requestFlush(false, true);
         this.currentTicks = 0;
         if (this.reachedWalking) {
            this.fruitlessCycles = 0;
         } else if (++this.fruitlessCycles >= 3) {
            this.standDown = "Phase disabled: block did not clear.";
         }

         this.reachedWalking = false;
      }

      @Override
      public void onTeleportPacket() {
         this.standDown = "Phase disabled: server set you back.";
      }

      private RiptideBlinkManager.Hold classify(Packet<?> packet, boolean incoming) {
         if (PackHideState.isHardLocked()) {
            return RiptideBlinkManager.Hold.FLUSH;
         } else {
            PhaseModule.BlinkMode.State current = this.state;
            if (current == PhaseModule.BlinkMode.State.WAITING) {
               return RiptideBlinkManager.Hold.FLUSH;
            } else if (!(packet instanceof ClientboundBlockUpdatePacket)
               && !(packet instanceof ClientboundBlockEventPacket)
               && !(packet instanceof ClientboundSectionBlocksUpdatePacket)
               && !(packet instanceof ClientboundLevelChunkWithLightPacket)
               && !(packet instanceof ClientboundChunkBatchFinishedPacket)
               && !(packet instanceof ClientboundSetTitleTextPacket)) {
               return current != PhaseModule.BlinkMode.State.PHASING && current != PhaseModule.BlinkMode.State.WALKING
                  ? RiptideBlinkManager.Hold.PASS
                  : RiptideBlinkManager.Hold.QUEUE;
            } else {
               return RiptideBlinkManager.Hold.PASS;
            }
         }
      }

      VoxelShape shape(VoxelShape original, LocalPlayer player, BlockPos pos) {
         if (!this.inCollisionCheck && !this.state.boxCollisions) {
            return !(pos.getY() >= player.position().y) && (!player.isShiftKeyDown() || !player.onGround()) ? original : Shapes.empty();
         } else {
            return original;
         }
      }

      @Override
      public String info() {
         PhaseModule.BlinkMode.State current = this.state;
         return current == PhaseModule.BlinkMode.State.WAITING ? "" : current.name().toLowerCase(Locale.ROOT);
      }

      private static enum State {
         WAITING(false),
         PHASING(false),
         WALKING(true),
         FLUSHING(true);

         final boolean boxCollisions;

         private State(boolean boxCollisions) {
            this.boxCollisions = boxCollisions;
         }
      }
   }

   private static final class ClipMode implements PhaseModule.PhaseMode {
      private static final double GRAVITY = 0.07840000152;

      @Override
      public void preMovementTick() {
         if (!RiptideLiteVariant.enabled()) {
            LocalPlayer player = Module.MC.player;
            ClientPacketListener connection = Module.MC.getConnection();
            if (player != null && connection != null) {
               Vec3 center = Vec3.atCenterOf(player.blockPosition());
               connection.send(new PosRot(center.x, player.getY() - 0.07840000152, center.z, player.getYRot(), player.getXRot(), false, false));
               PhaseModule.instance.disableWithToggleMessage("Phase: clip packet sent.");
            }
         }
      }
   }

   private static final class IntaveMode implements PhaseModule.PhaseMode {
      private boolean mining;

      @Override
      public void preMovementTick() {
         if (!RiptideLiteVariant.enabled()) {
            LocalPlayer player = Module.MC.player;
            ClientPacketListener connection = Module.MC.getConnection();
            if (player != null && connection != null) {
               boolean check = Module.MC.options.keyAttack.isDown() && player.getXRot() > 80.0F;
               BlockPos below = player.blockPosition().offset(0, -1, 0);
               if (check) {
                  connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, below, Direction.UP));
                  this.mining = true;
               } else if (this.mining) {
                  this.mining = false;
               }

               if (this.mining) {
                  ((RiptideEntityAccessor)player).riptide$setPosition(new Vec3(player.getX(), player.getY() - 0.0052, player.getZ()));
               }

               if (player.isShiftKeyDown()) {
                  float distance = 0.005F;
                  double rotation = Math.toRadians(player.getYRot());
                  if (Module.MC.options.keyUp.isDown()) {
                     move(player, rotation, distance, 1, 1);
                  } else if (Module.MC.options.keyDown.isDown()) {
                     move(player, rotation, -distance, 1, -1);
                  } else if (Module.MC.options.keyLeft.isDown()) {
                     move(player, rotation, distance, -1, 1);
                  } else if (Module.MC.options.keyRight.isDown()) {
                     move(player, rotation, -distance, -1, -1);
                  }
               }
            }
         }
      }

      @Override
      public String info() {
         if (Module.MC.player == null) {
            return "";
         } else if (this.mining) {
            return "sinking";
         } else {
            return Module.MC.options.keyAttack.isDown() ? "look down" : "hold attack";
         }
      }

      private static void move(LocalPlayer player, double rotation, float distance, int xMultiplier, int zMultiplier) {
         double xx = Math.cos(rotation) * distance * xMultiplier;
         double zz = Math.sin(rotation) * distance * zMultiplier;
         player.setPos(player.getX() + xx, player.getY(), player.getZ() + zz);
      }
   }

   private interface PhaseMode {
      default void enable() {
      }

      default void disable() {
      }

      default void preMovementTick() {
      }

      default void onNetworkMovementTickPre() {
      }

      default void onPacketProcessFrame() {
      }

      default boolean shouldCancelPlayerTick() {
         return false;
      }

      default void onTeleportPacket() {
      }

      default String info() {
         return "";
      }
   }

   private static final class SpiderMode implements PhaseModule.PhaseMode {
      private int spiderTicks = 1;

      @Override
      public void enable() {
         this.spiderTicks = 1;
         LocalPlayer player = Module.MC.player;
         if (player != null) {
            PhaseModule.setDeltaY(player, 0.0);
         }
      }

      @Override
      public void disable() {
         if (Module.MC.player != null) {
            Module.MC.player.noPhysics = false;
         }
      }

      @Override
      public void preMovementTick() {
         if (!RiptideLiteVariant.enabled()) {
            LocalPlayer player = Module.MC.player;
            if (player != null && Module.MC.level != null) {
               switch (this.spiderTicks) {
                  case 1:
                     if (Module.MC.options.keyJump.isDown() && !Module.MC.level.getBlockState(player.blockPosition()).isAir()) {
                        PhaseModule.setDeltaY(player, 0.42);
                        this.spiderTicks++;
                     }

                     player.setOnGround(true);
                     break;
                  case 2:
                     PhaseModule.setDeltaY(player, 0.33);
                     this.spiderTicks++;
                     break;
                  case 3:
                     PhaseModule.setDeltaY(player, 0.25);
                     this.spiderTicks++;
               }

               if (this.spiderTicks > 3) {
                  this.spiderTicks = 1;
               }

               player.noPhysics = true;
               if (player.isShiftKeyDown()) {
                  player.setDeltaMovement(ModuleMovementUtil.withStrafe(player, player.getDeltaMovement(), 0.179));
               }
            }
         }
      }

      VoxelShape shape(VoxelShape original, BlockState state, LocalPlayer player, BlockPos pos) {
         if (state.getBlock() instanceof LiquidBlock) {
            return original;
         } else {
            return pos.getY() >= player.getY() ? Shapes.empty() : original;
         }
      }
   }
}
