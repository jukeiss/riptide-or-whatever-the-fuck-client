package riptide.modules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptidePerf;
import riptide.util.RiptideUiScale;

public final class ModuleScreenRenderer {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final Map<Integer, ModuleScreenRenderer.SmoothedScreenBox> SMOOTHED_BOXES = new HashMap<>();
   private static final ModuleScreenRenderer.ScreenBox SCRATCH_BOX = new ModuleScreenRenderer.ScreenBox();
   private static Object lastLevel;
   private static long frameIndex;

   private ModuleScreenRenderer() {
   }

   public static void render(GuiGraphicsExtractor context) {
      if (!PackHideState.isActive()) {
         if (MC != null && MC.level != null && MC.player != null && !MC.gui.hud.isHidden()) {
            if (ModuleRenderUtil.has2dEspWork()) {
               if (!ModuleRenderUtil.shouldSuppressEspForUi()) {
                  long perf = RiptidePerf.begin();

                  try {
                     renderEsp2d(context);
                  } finally {
                     RiptidePerf.end("modules.esp2d", perf);
                  }
               }
            }
         }
      }
   }

   private static void renderEsp2d(GuiGraphicsExtractor context) {
      Camera camera = MC.gameRenderer.mainCamera();
      if (camera != null && MC.getWindow() != null) {
         if (lastLevel != MC.level) {
            SMOOTHED_BOXES.clear();
            lastLevel = MC.level;
         }

         long frame = ++frameIndex;
         ModuleScreenRenderer.Projection projection = new ModuleScreenRenderer.Projection(
            camera.position(),
            camera.getViewRotationProjectionMatrix(new Matrix4f()),
            RiptideUiScale.getVirtualScreenWidth(),
            RiptideUiScale.getVirtualScreenHeight()
         );
         float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         Module espModule = ModuleRegistry.get("esp");
         boolean fill = espModule != null && espModule.bool("fill");

         for (Entity entity : MC.level.entitiesForRendering()) {
            try {
               if (ModuleRenderUtil.shouldEsp(entity)) {
                  ModuleScreenRenderer.ScreenBox box = projectBox(entity, tickDelta, projection);
                  if (box != null) {
                     box = smoothBox(entity, box, frame);
                     int color = ModuleRenderUtil.espColor(entity);
                     drawRIPTIDE2dBox(context, box, color, entity, fill);
                  }
               }
            } catch (Throwable var12) {
            }
         }

         pruneSmoothingCache(frame);
      }
   }

   private static ModuleScreenRenderer.ScreenBox projectBox(Entity entity, float tickDelta, ModuleScreenRenderer.Projection projection) {
      double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
      double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
      double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
      EntityDimensions dimensions = entity.getDimensions(entity.getPose());
      double halfWidth = dimensions.width() / 2.0;
      double minX = x - halfWidth - 0.05;
      double minY = y - 0.05;
      double minZ = z - halfWidth - 0.05;
      double maxX = x + halfWidth + 0.05;
      double maxY = y + dimensions.height() + 0.05;
      double maxZ = z + halfWidth + 0.05;
      ModuleScreenRenderer.ScreenBox out = SCRATCH_BOX;
      out.reset();
      out.include(minX, minY, minZ, projection);
      out.include(maxX, minY, minZ, projection);
      out.include(minX, minY, maxZ, projection);
      out.include(maxX, minY, maxZ, projection);
      out.include(minX, maxY, minZ, projection);
      out.include(maxX, maxY, minZ, projection);
      out.include(minX, maxY, maxZ, projection);
      out.include(maxX, maxY, maxZ, projection);
      return out.isValid() ? out : null;
   }

   private static ModuleScreenRenderer.ScreenBox smoothBox(Entity entity, ModuleScreenRenderer.ScreenBox raw, long frame) {
      int id = entity.getId();
      ModuleScreenRenderer.SmoothedScreenBox cached = SMOOTHED_BOXES.get(id);
      if (cached != null && !cached.shouldReset(raw)) {
         cached.lerpTo(raw);
      } else {
         cached = new ModuleScreenRenderer.SmoothedScreenBox(raw);
         SMOOTHED_BOXES.put(id, cached);
      }

      cached.lastFrame = frame;
      cached.applyTo(raw);
      return raw;
   }

   private static void pruneSmoothingCache(long frame) {
      if ((frame & 31L) == 0L) {
         Iterator<Entry<Integer, ModuleScreenRenderer.SmoothedScreenBox>> iterator = SMOOTHED_BOXES.entrySet().iterator();

         while (iterator.hasNext()) {
            if (frame - iterator.next().getValue().lastFrame > 10L) {
               iterator.remove();
            }
         }
      }
   }

   private static void drawRIPTIDE2dBox(GuiGraphicsExtractor context, ModuleScreenRenderer.ScreenBox box, int color, Entity entity, boolean fill) {
      int x = Mth.floor(box.minX);
      int y = Mth.floor(box.minY);
      int w = Mth.ceil(box.maxX) - x;
      int h = Mth.ceil(box.maxY) - y;
      if (w > 0 && h > 0) {
         int rgb = color & 16777215;
         int outline = 0xFF000000 | rgb;
         if (fill) {
            int fillColor = 838860800 | rgb;
            UiRenderer.rect(context, UiBounds.of(x, y, w, h), fillColor);
         }

         drawBorderedRectOutline(context, x, y, w, h, outline);
         if (entity instanceof LivingEntity living) {
            drawHealthBar(context, x, y, h, living);
         }
      }
   }

   private static void drawBorderedRectOutline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
      int black = -16777216;
      UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, w + 2, 3), black);
      UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, 3, h + 2), black);
      UiRenderer.rect(context, UiBounds.of(x - 1, y + h - 2, w + 2, 3), black);
      UiRenderer.rect(context, UiBounds.of(x + w - 2, y - 1, 3, h + 2), black);
      UiRenderer.outline(context, UiBounds.of(x, y, w, h), color);
   }

   private static void drawHealthBar(GuiGraphicsExtractor context, int x, int y, int h, LivingEntity entity) {
      float maxHealth = Math.max(1.0F, entity.getMaxHealth());
      float health = Mth.clamp(entity.getHealth() / maxHealth, 0.0F, 1.0F);
      int filled = Math.max(0, Math.min(h, Math.round(h * health)));
      int barX = x - 5;
      UiRenderer.rect(context, UiBounds.of(barX - 1, y - 1, 3, h + 2), -16777216);
      if (filled > 0) {
         int red = Math.round(255.0F * (1.0F - health));
         int green = Math.round(255.0F * health);
         int healthColor = 0xFF000000 | red << 16 | green << 8;
         UiRenderer.rect(context, UiBounds.of(barX, y + h - filled, 1, filled), healthColor);
      }
   }

   private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
   }

   private static final class ScreenBox {
      private final Vector4f vector = new Vector4f();
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;

      void reset() {
         this.minX = Float.MAX_VALUE;
         this.minY = Float.MAX_VALUE;
         this.maxX = -Float.MAX_VALUE;
         this.maxY = -Float.MAX_VALUE;
      }

      boolean include(double worldX, double worldY, double worldZ, ModuleScreenRenderer.Projection projection) {
         Vec3 cam = projection.cameraPosition();
         this.vector.set((float)(worldX - cam.x), (float)(worldY - cam.y), (float)(worldZ - cam.z), 1.0F);
         projection.matrix().transform(this.vector);
         if (this.vector.w <= 0.001F) {
            return false;
         } else {
            float ndcX = this.vector.x / this.vector.w;
            float ndcY = this.vector.y / this.vector.w;
            if (!Float.isNaN(ndcX) && !Float.isNaN(ndcY) && !Float.isInfinite(ndcX) && !Float.isInfinite(ndcY)) {
               float x = (ndcX * 0.5F + 0.5F) * projection.screenWidth();
               float y = (0.5F - ndcY * 0.5F) * projection.screenHeight();
               this.minX = Math.min(this.minX, x);
               this.minY = Math.min(this.minY, y);
               this.maxX = Math.max(this.maxX, x);
               this.maxY = Math.max(this.maxY, y);
               return true;
            } else {
               return false;
            }
         }
      }

      boolean isValid() {
         return this.maxX > this.minX && this.maxY > this.minY;
      }
   }

   private static final class SmoothedScreenBox {
      private static final float SMOOTHING = 0.42F;
      private static final float RESET_DISTANCE = 42.0F;
      private static final float RESET_SIZE_DELTA = 24.0F;
      float minX;
      float minY;
      float maxX;
      float maxY;
      long lastFrame;

      SmoothedScreenBox(ModuleScreenRenderer.ScreenBox raw) {
         this.set(raw);
      }

      void set(ModuleScreenRenderer.ScreenBox raw) {
         this.minX = raw.minX;
         this.minY = raw.minY;
         this.maxX = raw.maxX;
         this.maxY = raw.maxY;
      }

      void lerpTo(ModuleScreenRenderer.ScreenBox raw) {
         this.minX = this.minX + (raw.minX - this.minX) * 0.42F;
         this.minY = this.minY + (raw.minY - this.minY) * 0.42F;
         this.maxX = this.maxX + (raw.maxX - this.maxX) * 0.42F;
         this.maxY = this.maxY + (raw.maxY - this.maxY) * 0.42F;
      }

      boolean shouldReset(ModuleScreenRenderer.ScreenBox raw) {
         float centerDx = (raw.minX + raw.maxX - (this.minX + this.maxX)) * 0.5F;
         float centerDy = (raw.minY + raw.maxY - (this.minY + this.maxY)) * 0.5F;
         float widthDelta = Math.abs(raw.maxX - raw.minX - (this.maxX - this.minX));
         float heightDelta = Math.abs(raw.maxY - raw.minY - (this.maxY - this.minY));
         return centerDx * centerDx + centerDy * centerDy > 1764.0F || widthDelta > 24.0F || heightDelta > 24.0F;
      }

      void applyTo(ModuleScreenRenderer.ScreenBox box) {
         box.minX = this.minX;
         box.minY = this.minY;
         box.maxX = this.maxX;
         box.maxY = this.maxY;
      }
   }
}
