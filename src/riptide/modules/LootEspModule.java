package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideWorldGeometry;

public final class LootEspModule extends Module {
   private static volatile boolean hook;
   private static LootEspModule cached;
   private static final String DEFAULT_PRICES = String.join(
      "|",
      "netherite_ingot=250000",
      "netherite_block=2250000",
      "ancient_debris=180000",
      "diamond=2500",
      "diamond_block=22500",
      "elytra=400000",
      "totem_of_undying=90000",
      "shulker_box=150000",
      "enchanted_golden_apple=120000",
      "golden_apple=1500",
      "ender_pearl=900",
      "experience_bottle=400",
      "emerald=600",
      "netherite_scrap=45000"
   );
   private static Map<Item, Long> priceTable;

   public LootEspModule() {
      super("loot-esp", "Loot ESP", ModuleCategory.RENDER, "Boxes dropped items with a name label and a tracer.");
      this.add(new BoolSetting("box", "Box", true).description("Draw a box around each dropped item.").build());
      this.add(new BoolSetting("tracer", "Tracers", true).description("Draw a line from you to each item.").build());
      this.add(new BoolSetting("name", "Names", true).description("Float the item name and count above each drop.").build());
      this.add(new IntSetting("range", "Range", 96, 8, 256, 4).description("How far away items still show, in blocks.").build());
      this.add(
         new BoolSetting("group", "Group Stacks", true)
            .group("Stacks")
            .description("Fold identical drops sitting near each other into one label with the combined total.")
            .build()
      );
      this.add(
         new IntSetting("group-range", "Group Range", 4, 1, 16, 1)
            .group("Stacks")
            .description("How close, in blocks, two identical drops must be to merge into one label.")
            .build()
      );
      this.add(
         new IntSetting("min-value", "Min Value", 0, 0, 5000000, 1000)
            .group("Filter")
            .description("Hide drops worth less than this (price x count). 0 shows everything. Unpriced items count as 0.")
            .build()
      );
      this.add(new ColorSetting("color", "Color", -11890433).group("Colors").description("Box and tracer color.").build());
   }

   public static void initialize() {
      installHook();
   }

   @Override
   public void onEnable() {
      installHook();
   }

   @Override
   public String info() {
      return this.bool("name") ? "names" : "esp";
   }

   private static LootEspModule instance() {
      LootEspModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("loot-esp") instanceof LootEspModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static Map<Item, Long> prices() {
      Map var0 = priceTable;
      if (var0 != null) {
         return var0;
      } else {
         HashMap var10 = new HashMap();

         for (String var4 : DEFAULT_PRICES.split("\\|")) {
            int var5 = var4.indexOf(61);
            if (var5 > 0) {
               try {
                  Item var6 = (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(var4.substring(0, var5).trim()));
                  long var7 = Long.parseLong(var4.substring(var5 + 1).trim());
                  if (var6 != null) {
                     var10.put(var6, var7);
                  }
               } catch (RuntimeException var9) {
               }
            }
         }

         priceTable = var10;
         return var10;
      }
   }

   private static long value(ItemStack var0) {
      Long var1 = prices().get(var0.getItem());
      return var1 == null ? 0L : var1 * var0.getCount();
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     LootEspModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     CameraRenderState var2 = var0.levelState().cameraRenderState;
                     Vec3 var3 = var2.pos;
                     int var4 = ModuleRenderUtil.color(var1, "color", -11890433) | 0xFF000000;
                     double var5 = var1.integer("range");
                     double var7 = var5 * var5;
                     boolean var9 = var1.bool("box");
                     boolean var10 = var1.bool("tracer");
                     boolean var11 = var1.bool("name");
                     boolean var12 = var1.bool("group");
                     long var13 = var1.integer("min-value");
                     Vec3 var15 = MC.player.getEyePosition();
                     ArrayList var16 = new ArrayList();

                     for (Entity var18 : MC.level.entitiesForRendering()) {
                        if (var18 instanceof ItemEntity var19
                           && var18.position().distanceToSqr(var3) <= var7
                           && (var13 <= 0L || value(var19.getItem()) >= var13)) {
                           var16.add(var19);
                        }
                     }

                     if (var16.isEmpty()) {
                        return;
                     }

                     if (var9 || var10) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(),
                              RiptideRenderTypes.storageEspLinesSeeThrough(),
                              (var6, var7x) -> {
                                 for (ItemEntity var9x : var16) {
                                    AABB var10x = var9x.getBoundingBox();
                                    if (var9) {
                                       drawBox(var6, var7x, var10x, var3, var4);
                                    }

                                    if (var10) {
                                       Vec3 var11x = var9x.position();
                                       RiptideWorldGeometry.line(
                                          var6,
                                          var7x,
                                          var15.x - var3.x,
                                          var15.y - var3.y,
                                          var15.z - var3.z,
                                          var11x.x - var3.x,
                                          (var10x.minY + var10x.maxY) / 2.0 - var3.y,
                                          var11x.z - var3.z,
                                          var4,
                                          1.5F
                                       );
                                    }
                                 }
                              }
                           );
                     }

                     if (var11) {
                        PoseStack var40 = var0.poseStack();
                        double var41 = var12 ? Math.pow(var1.integer("group-range"), 2.0) : 0.0;
                        boolean[] var20 = new boolean[var16.size()];

                        for (int var21 = 0; var21 < var16.size(); var21++) {
                           if (!var20[var21]) {
                              ItemEntity var22 = (ItemEntity)var16.get(var21);
                              ItemStack var23 = var22.getItem();
                              long var24 = var23.getCount();
                              int var26 = 1;
                              double var27 = var22.getX();
                              double var29 = var22.getZ();
                              double var31 = var22.getBoundingBox().maxY;
                              if (var12) {
                                 for (int var33 = var21 + 1; var33 < var16.size(); var33++) {
                                    if (!var20[var33]) {
                                       ItemEntity var34 = (ItemEntity)var16.get(var33);
                                       if (ItemStack.isSameItemSameComponents(var23, var34.getItem())
                                          && var22.position().distanceToSqr(var34.position()) <= var41) {
                                          var20[var33] = true;
                                          var24 += var34.getItem().getCount();
                                          var26++;
                                          var27 += var34.getX();
                                          var29 += var34.getZ();
                                          var31 = Math.max(var31, var34.getBoundingBox().maxY);
                                       }
                                    }
                                 }
                              }

                              String var42 = var23.getHoverName().getString();
                              StringBuilder var43 = new StringBuilder(var42);
                              if (var24 > 1L) {
                                 var43.append(" x").append(var24);
                              }

                              if (var26 > 1) {
                                 var43.append(" (").append(var26).append(')');
                              }

                              double var35 = var27 / var26;
                              double var37 = var29 / var26;
                              var40.pushPose();
                              var40.translate(var35 - var3.x, var31 + 0.4 - var3.y, var37 - var3.z);
                              var0.submitNodeCollector()
                                 .submitNameTag(var40, Vec3.ZERO, 1073741824, Component.literal(var43.toString()), false, 15728880, var2);
                              var40.popPose();
                           }
                        }
                     }
                  } catch (Throwable var39) {
                  }
               }
            );
      }
   }

   private static void drawBox(Pose var0, VertexConsumer var1, AABB var2, Vec3 var3, int var4) {
      double var5 = var2.minX - var3.x;
      double var7 = var2.minY - var3.y;
      double var9 = var2.minZ - var3.z;
      double var11 = var2.maxX - var3.x;
      double var13 = var2.maxY - var3.y;
      double var15 = var2.maxZ - var3.z;
      edge(var0, var1, var5, var7, var9, var11, var7, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var7, var15, var4);
      edge(var0, var1, var11, var7, var15, var5, var7, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var7, var9, var4);
      edge(var0, var1, var5, var13, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var13, var9, var11, var13, var15, var4);
      edge(var0, var1, var11, var13, var15, var5, var13, var15, var4);
      edge(var0, var1, var5, var13, var15, var5, var13, var9, var4);
      edge(var0, var1, var5, var7, var9, var5, var13, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var7, var15, var11, var13, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var13, var15, var4);
   }

   private static void edge(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      RiptideWorldGeometry.line(var0, var1, var2, var4, var6, var8, var10, var12, var14, 1.5F);
   }
}
