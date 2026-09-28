package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideWorldGeometry;

/**
 * The box-and-counter half of ItemESP.
 *
 * Riptide used to ship two item ESPs: ItemESP (shader outline) and this one (box + label). There
 * is now one: ItemESP. This class is hidden from the menu and draws only for items ItemESP itself
 * has selected, in ItemESP's own colour, so ItemESP's item list, distances, fade and colour mode
 * control everything. What it adds on top of ItemESP's outline glow:
 *   - a translucent glow shell around each drop that breathes,
 *   - a pop-in when a drop first appears,
 *   - a floating name + count badge, with identical nearby drops folded into one total.
 *
 * (It keeps the "loot-esp" id and class name because the compiled module list registers it by
 * that name; changing either would need BuiltinModules recompiled.)
 */
public final class LootEspModule extends Module {
   private static volatile boolean hook;
   // entity id -> first time we drew it, for the pop-in; pruned when drops disappear
   private static final Map<Integer, Long> FIRST_SEEN = new HashMap<>();
   private static final long POP_MS = 260L;
   private static final double GROUP_RANGE_SQ = 3.0 * 3.0;

   public LootEspModule() {
      super("loot-esp", "Loot ESP", ModuleCategory.RENDER, "Box and count overlay for ItemESP. Configure it through ItemESP.");
      // Install at construction, not in onEnable: Riptide restores enabled state from config
      // without calling onEnable, and this overlay must follow ItemESP regardless.
      installHook();
   }

   public static void initialize() {
      installHook();
   }

   @Override
   public boolean showInModuleMenu() {
      return false;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)context -> {
            try {
               render(context);
            } catch (Throwable var2) {
            }
         });
      }
   }

   private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
      if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || PackHideState.isActive()) {
         return;
      }

      long now = System.currentTimeMillis();
      List<ItemEntity> items = new ArrayList<>();

      for (Entity entity : MC.level.entitiesForRendering()) {
         // shouldItemEsp applies ItemESP's on/off, item list, max distance, fade and UI suppression.
         if (entity instanceof ItemEntity item && ModuleRenderUtil.shouldItemEsp(item)) {
            items.add(item);
         }
      }

      prune(items);
      if (items.isEmpty()) {
         return;
      }

      CameraRenderState camera = context.levelState().cameraRenderState;
      Vec3 cam = camera.pos;
      float partial = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      // one breath every ~1.6 s, shared so every drop pulses together
      double breath = 0.5 + 0.5 * Math.sin(now / 1600.0 * Math.PI * 2.0);
      int n = items.size();
      AABB[] boxes = new AABB[n];
      int[] colors = new int[n];
      float[] pops = new float[n];

      for (int i = 0; i < n; i++) {
         ItemEntity item = items.get(i);
         Long first = FIRST_SEEN.get(item.getId());
         if (first == null) {
            first = now;
            FIRST_SEEN.put(item.getId(), first);
         }

         float t = Math.min(1.0F, (float)(now - first) / POP_MS);
         // ease-out back: overshoots a touch, then settles at 1
         float pop = 1.0F + 2.70158F * (float)Math.pow(t - 1.0F, 3.0) + 1.70158F * (float)Math.pow(t - 1.0F, 2.0);
         pops[i] = Math.max(0.0F, Math.min(1.12F, pop));
         colors[i] = ModuleRenderUtil.itemEspColor(item);
         boxes[i] = animatedBox(item, partial, pops[i]);
      }

      // glow: two translucent shells whose strength breathes
      context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> {
         for (int i = 0; i < n; i++) {
            float fade = Math.min(1.0F, pops[i]);
            int base = colors[i];
            fillBox(pose, buffer, boxes[i].inflate(0.03).move(-cam.x, -cam.y, -cam.z), withAlpha(base, (float)((0.10 + 0.10 * breath) * fade)));
            fillBox(pose, buffer, boxes[i].inflate(0.09 + 0.03 * breath).move(-cam.x, -cam.y, -cam.z), withAlpha(base, (float)((0.04 + 0.05 * breath) * fade)));
         }
      });
      // crisp edges on the core box
      context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> {
         for (int i = 0; i < n; i++) {
            drawEdges(pose, buffer, boxes[i], cam, withAlpha(colors[i], Math.min(1.0F, pops[i]) * (float)(0.75 + 0.25 * breath)));
         }
      });
      drawCounters(context, camera, items, colors, partial);
   }

   /** The item's box, centred on its interpolated position and scaled by the pop-in. */
   private static AABB animatedBox(ItemEntity item, float partial, float pop) {
      double x = item.xOld + (item.getX() - item.xOld) * partial;
      double y = item.yOld + (item.getY() - item.yOld) * partial;
      double z = item.zOld + (item.getZ() - item.zOld) * partial;
      AABB bb = item.getBoundingBox();
      double hw = bb.getXsize() / 2.0 * pop;
      double h = bb.getYsize() * pop;
      double hd = bb.getZsize() / 2.0 * pop;
      return new AABB(x - hw, y, z - hd, x + hw, y + h, z + hd);
   }

   private static void drawCounters(
      net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
      CameraRenderState camera,
      List<ItemEntity> items,
      int[] colors,
      float partial
   ) {
      Vec3 cam = camera.pos;
      PoseStack poses = context.poseStack();
      boolean[] used = new boolean[items.size()];

      for (int i = 0; i < items.size(); i++) {
         if (!used[i]) {
            ItemEntity first = items.get(i);
            ItemStack stack = first.getItem();
            long total = stack.getCount();
            int drops = 1;
            double sx = lerp(partial, first.xOld, first.getX());
            double sz = lerp(partial, first.zOld, first.getZ());
            double top = lerp(partial, first.yOld, first.getY()) + first.getBoundingBox().getYsize();

            // fold identical drops lying close together into one badge with the combined total
            for (int j = i + 1; j < items.size(); j++) {
               if (!used[j]) {
                  ItemEntity other = items.get(j);
                  if (ItemStack.isSameItemSameComponents(stack, other.getItem()) && first.position().distanceToSqr(other.position()) <= GROUP_RANGE_SQ) {
                     used[j] = true;
                     total += other.getItem().getCount();
                     drops++;
                     sx += lerp(partial, other.xOld, other.getX());
                     sz += lerp(partial, other.zOld, other.getZ());
                     top = Math.max(top, lerp(partial, other.yOld, other.getY()) + other.getBoundingBox().getYsize());
                  }
               }
            }

            int accent = colors[i] & 0xFFFFFF;
            MutableComponent label = Component.literal(stack.getHoverName().getString()).withColor(0xFFFFFF);
            if (total > 1L) {
               label.append(Component.literal("  ×" + total).withColor(accent));
            }

            if (drops > 1) {
               label.append(Component.literal("  (" + drops + ")").withColor(0xAAAAAA));
            }

            poses.pushPose();
            poses.translate(sx / drops - cam.x, top + 0.45 - cam.y, sz / drops - cam.z);
            context.submitNodeCollector().submitNameTag(poses, Vec3.ZERO, 0x60000000, label, false, 15728880, camera);
            poses.popPose();
         }
      }
   }

   private static double lerp(float t, double from, double to) {
      return from + (to - from) * t;
   }

   /** Forget drops that are gone so a re-drop pops in again and the map can't grow forever. */
   private static void prune(List<ItemEntity> visible) {
      if (FIRST_SEEN.isEmpty()) {
         return;
      }

      java.util.Set<Integer> alive = new java.util.HashSet<>();
      for (ItemEntity item : visible) {
         alive.add(item.getId());
      }

      Iterator<Integer> it = FIRST_SEEN.keySet().iterator();
      while (it.hasNext()) {
         if (!alive.contains(it.next())) {
            it.remove();
         }
      }
   }

   private static int withAlpha(int color, float alpha) {
      int a = Math.max(0, Math.min(255, (int)((color >>> 24 & 0xFF) * alpha)));
      return a << 24 | color & 0xFFFFFF;
   }

   private static void fillBox(Pose pose, VertexConsumer buffer, AABB b, int color) {
      if ((color >>> 24 & 0xFF) > 0) {
         quad(pose, buffer, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, color);
         quad(pose, buffer, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ, color);
         quad(pose, buffer, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
         quad(pose, buffer, b.maxX, b.minY, b.minZ, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, color);
         quad(pose, buffer, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, color);
         quad(pose, buffer, b.maxX, b.minY, b.maxZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, color);
      }
   }

   private static void quad(
      Pose pose, VertexConsumer buffer,
      double x1, double y1, double z1, double x2, double y2, double z2,
      double x3, double y3, double z3, double x4, double y4, double z4, int color
   ) {
      buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color);
      buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color);
      buffer.addVertex(pose, (float)x3, (float)y3, (float)z3).setColor(color);
      buffer.addVertex(pose, (float)x4, (float)y4, (float)z4).setColor(color);
   }

   private static void drawEdges(Pose pose, VertexConsumer buffer, AABB box, Vec3 cam, int color) {
      double x1 = box.minX - cam.x;
      double y1 = box.minY - cam.y;
      double z1 = box.minZ - cam.z;
      double x2 = box.maxX - cam.x;
      double y2 = box.maxY - cam.y;
      double z2 = box.maxZ - cam.z;
      edge(pose, buffer, x1, y1, z1, x2, y1, z1, color);
      edge(pose, buffer, x2, y1, z1, x2, y1, z2, color);
      edge(pose, buffer, x2, y1, z2, x1, y1, z2, color);
      edge(pose, buffer, x1, y1, z2, x1, y1, z1, color);
      edge(pose, buffer, x1, y2, z1, x2, y2, z1, color);
      edge(pose, buffer, x2, y2, z1, x2, y2, z2, color);
      edge(pose, buffer, x2, y2, z2, x1, y2, z2, color);
      edge(pose, buffer, x1, y2, z2, x1, y2, z1, color);
      edge(pose, buffer, x1, y1, z1, x1, y2, z1, color);
      edge(pose, buffer, x2, y1, z1, x2, y2, z1, color);
      edge(pose, buffer, x2, y1, z2, x2, y2, z2, color);
      edge(pose, buffer, x1, y1, z2, x1, y2, z2, color);
   }

   private static void edge(Pose pose, VertexConsumer buffer, double ax, double ay, double az, double bx, double by, double bz, int color) {
      RiptideWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, color, 2.0F);
   }
}
