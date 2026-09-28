package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.util.RiptideWorldGeometry;

public final class BlockEspStylesModule extends Module {
   private static volatile boolean renderHookInstalled;
   private static BlockEspStylesModule cachedInstance;

   public BlockEspStylesModule() {
      super("block-esp-styles", "Block ESP+", ModuleCategory.RENDER, "Block ESP with selectable shapes.");
      this.add(RegistryListSetting.blocks("blocks", "Blocks", "minecraft:ancient_debris").description("Blocks to highlight.").build());
      this.add(new ChoiceSetting("style", "Style", "Box", "Box", "Outline", "Fill", "Corners", "Top Face").description("How each block is drawn.").build());
      this.add(
         new IntSetting("corner-size", "Corner Size", 30, 5, 50, 5)
            .description("Corner length, as a percent of the block.")
            .visibleWhen(() -> "Corners".equals(this.choice("style")))
            .build()
      );
      this.add(
         new IntSetting("fill-alpha", "Fill Opacity", 25, 1, 100, 5)
            .description("How solid the filled faces are.")
            .visibleWhen(() -> !"Outline".equals(this.choice("style")) && !"Corners".equals(this.choice("style")))
            .build()
      );
      this.add(
         new IntSetting("line-width", "Line Width", 15, 5, 50, 5)
            .description("Outline thickness, tenths of a pixel.")
            .visibleWhen(() -> !"Fill".equals(this.choice("style")))
            .build()
      );
      this.add(new IntSetting("max-targets", "Max Blocks", 1024, 64, 8192, 64).description("Upper limit on blocks drawn at once.").build());
      this.add(new ColorSetting("color", "Color", -855688389).description("Highlight color.").build());
   }

   public static void initialize() {
      installRenderHook();
   }

   @Override
   public String info() {
      return this.choice("style");
   }

   @Override
   public void onEnable() {
      installRenderHook();
   }

   private static BlockEspStylesModule instance() {
      BlockEspStylesModule var0 = cachedInstance;
      if (var0 == null && ModuleRegistry.get("block-esp-styles") instanceof BlockEspStylesModule var1) {
         var0 = var1;
         cachedInstance = var1;
      }

      return var0;
   }

   private static synchronized void installRenderHook() {
      if (!renderHookInstalled) {
         renderHookInstalled = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     BlockEspStylesModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) {
                        return;
                     }

                     if (ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     ArrayList var2 = new ArrayList();
                     ModuleBlockEsp.collectBoth(var1, MC.level, MC.player, (var1x, var2x) -> var2.add(new BlockEspStylesModule.Hit(var1x, var2x)), null);
                     if (var2.isEmpty()) {
                        return;
                     }

                     String var3 = var1.choice("style");
                     Vec3 var4 = var0.levelState().cameraRenderState.pos;
                     boolean var5 = "Fill".equals(var3) || "Box".equals(var3) || "Top Face".equals(var3);
                     boolean var6 = !"Fill".equals(var3);
                     if (var5) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (var4x, var5x) -> var1.emitFill(var4x, var5x, var4, var2, var3)
                           );
                     }

                     if (var6) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(),
                              RiptideRenderTypes.storageEspLinesSeeThrough(),
                              (var4x, var5x) -> var1.emitLines(var4x, var5x, var4, var2, var3)
                           );
                     }
                  } catch (Throwable var7) {
                  }
               }
            );
      }
   }

   private void emitFill(Pose var1, VertexConsumer var2, Vec3 var3, List<BlockEspStylesModule.Hit> var4, String var5) {
      double var6 = this.integer("fill-alpha") / 100.0;

      for (BlockEspStylesModule.Hit var9 : var4) {
         int var10 = withAlpha(var9.color(), var6);
         if (var10 != 0) {
            double var11 = var9.box().minX - var3.x;
            double var13 = var9.box().minY - var3.y;
            double var15 = var9.box().minZ - var3.z;
            double var17 = var9.box().maxX - var3.x;
            double var19 = var9.box().maxY - var3.y;
            double var21 = var9.box().maxZ - var3.z;
            if ("Top Face".equals(var5)) {
               quad(var1, var2, var11, var19, var15, var11, var19, var21, var17, var19, var21, var17, var19, var15, var10);
            } else {
               walls(var1, var2, var11, var17, var13, var19, var15, var21, var10);
            }
         }
      }
   }

   private void emitLines(Pose var1, VertexConsumer var2, Vec3 var3, List<BlockEspStylesModule.Hit> var4, String var5) {
      float var6 = this.integer("line-width") / 10.0F;
      double var7 = this.integer("corner-size") / 100.0;

      for (BlockEspStylesModule.Hit var10 : var4) {
         int var11 = withAlpha(var10.color(), 1.0);
         if (var11 != 0) {
            double var12 = var10.box().minX - var3.x;
            double var14 = var10.box().minY - var3.y;
            double var16 = var10.box().minZ - var3.z;
            double var18 = var10.box().maxX - var3.x;
            double var20 = var10.box().maxY - var3.y;
            double var22 = var10.box().maxZ - var3.z;
            switch (var5) {
               case "Corners":
                  corners(var1, var2, var12, var18, var14, var20, var16, var22, var11, var6, var7);
                  break;
               case "Top Face":
                  RiptideWorldGeometry.line(var1, var2, var12, var20, var16, var18, var20, var16, var11, var6);
                  RiptideWorldGeometry.line(var1, var2, var18, var20, var16, var18, var20, var22, var11, var6);
                  RiptideWorldGeometry.line(var1, var2, var18, var20, var22, var12, var20, var22, var11, var6);
                  RiptideWorldGeometry.line(var1, var2, var12, var20, var22, var12, var20, var16, var11, var6);
                  break;
               default:
                  edges(var1, var2, var12, var18, var14, var20, var16, var22, var11, var6);
            }
         }
      }
   }

   private static void edges(
      Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14, float var15
   ) {
      for (double var19 : new double[]{var6, var8}) {
         RiptideWorldGeometry.line(var0, var1, var2, var19, var10, var4, var19, var10, var14, var15);
         RiptideWorldGeometry.line(var0, var1, var4, var19, var10, var4, var19, var12, var14, var15);
         RiptideWorldGeometry.line(var0, var1, var4, var19, var12, var2, var19, var12, var14, var15);
         RiptideWorldGeometry.line(var0, var1, var2, var19, var12, var2, var19, var10, var14, var15);
      }

      RiptideWorldGeometry.line(var0, var1, var2, var6, var10, var2, var8, var10, var14, var15);
      RiptideWorldGeometry.line(var0, var1, var4, var6, var10, var4, var8, var10, var14, var15);
      RiptideWorldGeometry.line(var0, var1, var4, var6, var12, var4, var8, var12, var14, var15);
      RiptideWorldGeometry.line(var0, var1, var2, var6, var12, var2, var8, var12, var14, var15);
   }

   private static void corners(
      Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14, float var15, double var16
   ) {
      double var18 = (var4 - var2) * var16;
      double var20 = (var8 - var6) * var16;
      double var22 = (var12 - var10) * var16;

      for (int var24 = 0; var24 < 8; var24++) {
         double var25 = (var24 & 1) == 0 ? var2 : var4;
         double var27 = (var24 & 2) == 0 ? var6 : var8;
         double var29 = (var24 & 4) == 0 ? var10 : var12;
         double var31 = (var24 & 1) == 0 ? var18 : -var18;
         double var33 = (var24 & 2) == 0 ? var20 : -var20;
         double var35 = (var24 & 4) == 0 ? var22 : -var22;
         RiptideWorldGeometry.line(var0, var1, var25, var27, var29, var25 + var31, var27, var29, var14, var15);
         RiptideWorldGeometry.line(var0, var1, var25, var27, var29, var25, var27 + var33, var29, var14, var15);
         RiptideWorldGeometry.line(var0, var1, var25, var27, var29, var25, var27, var29 + var35, var14, var15);
      }
   }

   private static void walls(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      quad(var0, var1, var2, var6, var10, var2, var8, var10, var4, var8, var10, var4, var6, var10, var14);
      quad(var0, var1, var4, var6, var12, var4, var8, var12, var2, var8, var12, var2, var6, var12, var14);
      quad(var0, var1, var2, var6, var12, var2, var8, var12, var2, var8, var10, var2, var6, var10, var14);
      quad(var0, var1, var4, var6, var10, var4, var8, var10, var4, var8, var12, var4, var6, var12, var14);
      quad(var0, var1, var2, var6, var10, var4, var6, var10, var4, var6, var12, var2, var6, var12, var14);
      quad(var0, var1, var2, var8, var10, var2, var8, var12, var4, var8, var12, var4, var8, var10, var14);
   }

   private static void quad(
      Pose var0,
      VertexConsumer var1,
      double var2,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      double var20,
      double var22,
      double var24,
      int var26
   ) {
      var1.addVertex(var0, (float)var2, (float)var4, (float)var6).setColor(var26);
      var1.addVertex(var0, (float)var8, (float)var10, (float)var12).setColor(var26);
      var1.addVertex(var0, (float)var14, (float)var16, (float)var18).setColor(var26);
      var1.addVertex(var0, (float)var20, (float)var22, (float)var24).setColor(var26);
   }

   private static int withAlpha(int var0, double var1) {
      int var3 = (int)Math.round((var0 >>> 24 & 0xFF) * var1);
      return var3 <= 0 ? 0 : Math.min(255, var3) << 24 | var0 & 16777215;
   }

   private record Hit(AABB box, int color) {
   }
}
