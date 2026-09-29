package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideWorldGeometry;

public final class JumpCirclesModule extends Module {
   private static volatile boolean hook;
   private static JumpCirclesModule cached;
   private final List<JumpCirclesModule.Ring> rings = new ArrayList<>();
   private boolean wasOnGround = true;
   private int airTicks;

   public JumpCirclesModule() {
      super("jump-circles", "Jump Circles", ModuleCategory.RENDER, "Leaves a shockwave ring on the ground every time you land.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("radius", "Radius", 170, 25, 500, 5).description("How wide the ring grows, in hundredths of a block (170 = 1.7 blocks).").build());
      this.add(new IntSetting("duration", "Duration", 900, 100, 3000, 50).description("How long a ring lasts, in milliseconds.").build());
      this.add(new IntSetting("air-ticks", "Min Air Ticks", 3, 1, 20, 1).description("How long you must be airborne before a landing counts.").build());
      this.add(new IntSetting("max", "Max Rings", 8, 1, 24, 1).description("How many rings can be on screen at once.").build());
      this.add(new IntSetting("segments", "Smoothness", 72, 16, 160, 8).description("How many line segments make up each ring.").build());
      this.add(new BoolSetting("grow", "Grow Outward", true).description("On: the ring expands as it fades. Off: it shrinks inward.").build());
      this.add(new BoolSetting("glow", "Glow", true).description("Draw faint extra rings for a glow.").build());
      this.add(new ColorSetting("color", "Color", -4960001).group("Render").description("Ring color.").build());
   }

   @Override
   public String info() {
      return Integer.toString(this.rings.size());
   }

   @Override
   public void onEnable() {
      installHook();
      this.rings.clear();
      this.airTicks = 0;
      this.wasOnGround = MC.player != null && MC.player.onGround();
   }

   @Override
   public void onDisable() {
      this.rings.clear();
   }

   @Override
   public void onGameLeft() {
      this.rings.clear();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.level != null) {
         boolean var1 = MC.player.onGround();
         if (!var1) {
            this.airTicks++;
         } else {
            if (!this.wasOnGround && this.airTicks >= this.integer("air-ticks")) {
               this.spawn();
            }

            this.airTicks = 0;
         }

         this.wasOnGround = var1;
         long var2 = System.currentTimeMillis();
         long var4 = this.integer("duration");
         this.rings.removeIf(var4x -> var2 - var4x.born() >= var4);
      } else {
         this.rings.clear();
      }
   }

   private void spawn() {
      this.rings.add(new JumpCirclesModule.Ring(MC.player.getX(), MC.player.getY(), MC.player.getZ(), System.currentTimeMillis()));

      while (this.rings.size() > this.integer("max")) {
         this.rings.remove(0);
      }
   }

   private static JumpCirclesModule instance() {
      JumpCirclesModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("jump-circles") instanceof JumpCirclesModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)var0 -> {
            try {
               JumpCirclesModule var1 = instance();
               if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.rings.isEmpty()) {
                  return;
               }

               if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) {
                  return;
               }

               Vec3 var2 = var0.levelState().cameraRenderState.pos;
               int var3 = ModuleRenderUtil.color(var1, "color", -4960001);
               int var4 = var3 >>> 24 == 0 ? 255 : var3 >>> 24;
               double var5 = var1.integer("radius") / 100.0;
               long var7 = var1.integer("duration");
               int var9 = var1.integer("segments");
               boolean var10 = var1.bool("grow");
               boolean var11 = var1.bool("glow");
               long var12 = System.currentTimeMillis();
               ArrayList<JumpCirclesModule.Ring> var14 = new ArrayList<>(var1.rings);
               var0.submitNodeCollector().submitCustomGeometry(var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var13, var14x) -> {
                  for (JumpCirclesModule.Ring var16 : var14) {
                     double var17 = Math.min(1.0, Math.max(0.0, (double)(var12 - var16.born()) / var7));
                     double var19 = var17 * var17 * (3.0 - 2.0 * var17);
                     double var21 = var10 ? var5 * var19 : var5 * (1.0 - var19);
                     int var23 = (int)Math.round(var4 * Math.pow(1.0 - var17, 1.15));
                     if (!(var21 <= 0.02) && var23 > 2) {
                        int var24 = var23 << 24 | var3 & 16777215;
                        ring(var13, var14x, var16, var21, var9, var2, var24);
                        if (var11) {
                           int var25 = Math.max(1, var23 / 3) << 24 | var3 & 16777215;
                           ring(var13, var14x, var16, var21 * 0.88, var9, var2, var25);
                           ring(var13, var14x, var16, var21 * 1.12, var9, var2, var25);
                        }
                     }
                  }
               });
            } catch (Throwable var15) {
            }
         });
      }
   }

   private static void ring(Pose var0, VertexConsumer var1, JumpCirclesModule.Ring var2, double var3, int var5, Vec3 var6, int var7) {
      double var8 = var2.y() + 0.03 - var6.y;
      double var10 = var2.x() - var6.x;
      double var12 = var2.z() - var6.z;
      double var14 = var10 + var3;
      double var16 = var12;

      for (int var18 = 1; var18 <= var5; var18++) {
         double var19 = (Math.PI * 2) * var18 / var5;
         double var21 = var10 + Math.cos(var19) * var3;
         double var23 = var12 + Math.sin(var19) * var3;
         RiptideWorldGeometry.line(var0, var1, var14, var8, var16, var21, var8, var23, var7, 1.8F);
         var14 = var21;
         var16 = var23;
      }
   }

   private record Ring(double x, double y, double z, long born) {
   }
}
