package riptide.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideColors;
import riptide.util.RiptideUiScale;

public final class ModuleNameTagRenderer {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int LINE_H = 10;
   private static final int BG_COLOR = -1879048192;
   private static int tagSnapshotRevision = -1;
   private static boolean tagEnabled;
   private static boolean tagPlayers;
   private static boolean tagMobs;
   private static boolean tagItems;
   private static double tagMaxDistSq;

   private ModuleNameTagRenderer() {
   }

   public static void render(GuiGraphicsExtractor context) {
      if (!PackHideState.isActive()) {
         if (MC != null && MC.level != null && MC.player != null && !MC.gui.hud.isHidden()) {
            Module module = ModuleRegistry.get("nametags");
            if (module != null && module.isEnabled()) {
               Camera camera = MC.gameRenderer.mainCamera();
               if (camera != null) {
                  boolean players = module.bool("players");
                  boolean mobs = module.bool("mobs");
                  boolean items = module.bool("items");
                  if (players || mobs || items) {
                     boolean showHealth = module.bool("show-health");
                     boolean showDistance = module.bool("show-distance");
                     boolean distanceScale = module.bool("distance-scale");
                     boolean groupItems = module.bool("group-items");
                     double baseScale = module.decimal("scale");
                     double maxDist = module.decimal("max-distance");
                     double groupRadius = module.decimal("group-radius");
                     ModuleNameTagRenderer.Projection projection = new ModuleNameTagRenderer.Projection(
                        camera.position(),
                        camera.getViewRotationProjectionMatrix(new Matrix4f()),
                        RiptideUiScale.getVirtualScreenWidth(),
                        RiptideUiScale.getVirtualScreenHeight()
                     );
                     float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                     Vec3 camPos = camera.position();
                     Entity self = MC.player;
                     List<ItemEntity> itemEntities = items ? new ArrayList<>() : null;

                     for (Entity entity : MC.level.entitiesForRendering()) {
                        try {
                           if (entity != self && !RiptideAntiBot.suppress(entity)) {
                              boolean friend = TeamsModule.isFriendOrTeam(entity);
                              if (!friend || TeamsModule.visualTargetsFriends("nametags")) {
                                 double dist = Math.sqrt(entity.distanceToSqr(camPos));
                                 if (!(maxDist > 0.0) || !(dist > maxDist)) {
                                    if (entity instanceof ItemEntity item) {
                                       if (items) {
                                          itemEntities.add(item);
                                       }
                                    } else {
                                       boolean isPlayer = entity instanceof Player;
                                       if (isPlayer ? players : mobs && entity instanceof LivingEntity) {
                                          float[] screen = projectHead(entity, tickDelta, projection);
                                          if (screen != null) {
                                             Component label = buildEntityLabel(entity, showHealth, showDistance, dist);
                                             if (friend) {
                                                label = label.copy()
                                                   .withStyle(style -> style.withColor(TextColor.fromRgb(TeamsModule.friendsColor() & 16777215)));
                                             }

                                             drawLabel(
                                                context,
                                                screen[0],
                                                screen[1],
                                                List.of(label.getVisualOrderText()),
                                                -1,
                                                scaleFor(baseScale, distanceScale, dist)
                                             );
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        } catch (Throwable var29) {
                        }
                     }

                     if (items && !itemEntities.isEmpty()) {
                        renderItems(context, itemEntities, groupItems, groupRadius, baseScale, distanceScale, camPos, tickDelta, projection);
                     }
                  }
               }
            }
         }
      }
   }

   private static void refreshTagSnapshot() {
      int revision = ModuleRegistry.revision();
      if (revision != tagSnapshotRevision) {
         tagSnapshotRevision = revision;
         Module module = ModuleRegistry.get("nametags");
         tagEnabled = module != null && module.isEnabled();
         tagPlayers = tagEnabled && module.bool("players");
         tagMobs = tagEnabled && module.bool("mobs");
         tagItems = tagEnabled && module.bool("items");
         double maxDist = tagEnabled ? module.decimal("max-distance") : 0.0;
         tagMaxDistSq = maxDist > 0.0 ? maxDist * maxDist : 0.0;
      }
   }

   public static boolean tags(Entity entity) {
      refreshTagSnapshot();
      if (!tagEnabled) {
         return false;
      } else if (PackHideState.isActive()) {
         return false;
      } else if (MC == null || MC.player == null || entity == null || entity == MC.player) {
         return false;
      } else if (RiptideAntiBot.isBot(entity)) {
         return false;
      } else if (TeamsModule.isFriendOrTeam(entity) && !TeamsModule.visualTargetsFriends("nametags")) {
         return false;
      } else {
         if (entity instanceof ItemEntity) {
            if (!tagItems) {
               return false;
            }
         } else if (entity instanceof Player) {
            if (!tagPlayers) {
               return false;
            }
         } else if (!(entity instanceof LivingEntity) || !tagMobs) {
            return false;
         }

         if (tagMaxDistSq > 0.0) {
            Camera cam = MC.gameRenderer.mainCamera();
            if (cam != null && entity.distanceToSqr(cam.position()) > tagMaxDistSq) {
               return false;
            }
         }

         return true;
      }
   }

   private static Component buildEntityLabel(Entity entity, boolean showHealth, boolean showDistance, double dist) {
      MutableComponent label = entity.getDisplayName().copy();
      if (showHealth && entity instanceof LivingEntity living) {
         label.append(Component.literal("  " + (int)Math.ceil(living.getHealth()) + "HP"));
      }

      if (showDistance) {
         label.append(Component.literal("  " + (int)dist + "m"));
      }

      return label;
   }

   private static void renderItems(
      GuiGraphicsExtractor context,
      List<ItemEntity> itemEntities,
      boolean group,
      double groupRadius,
      double baseScale,
      boolean distanceScale,
      Vec3 camPos,
      float tickDelta,
      ModuleNameTagRenderer.Projection projection
   ) {
      boolean[] visited = new boolean[itemEntities.size()];
      double r2 = groupRadius * groupRadius;
      Style countStyle = Style.EMPTY.withColor(TextColor.fromRgb(RiptideColors.accent() & 16777215));

      for (int i = 0; i < itemEntities.size(); i++) {
         try {
            if (!visited[i]) {
               ItemEntity base = itemEntities.get(i);
               visited[i] = true;
               List<ItemEntity> cluster = new ArrayList<>();
               cluster.add(base);
               if (group) {
                  for (int j = i + 1; j < itemEntities.size(); j++) {
                     if (!visited[j] && base.distanceToSqr((Entity)itemEntities.get(j)) <= r2) {
                        visited[j] = true;
                        cluster.add(itemEntities.get(j));
                     }
                  }
               }

               Map<String, ModuleNameTagRenderer.Agg> agg = new LinkedHashMap<>();
               double cx = 0.0;
               double cy = 0.0;
               double cz = 0.0;

               for (ItemEntity ie : cluster) {
                  ItemStack st = ie.getItem();
                  Component name = st.getHoverName();
                  ModuleNameTagRenderer.Agg var10000 = agg.computeIfAbsent(name.getString(), k -> new ModuleNameTagRenderer.Agg(name));
                  var10000.count = var10000.count + st.getCount();
                  cx += Mth.lerp(tickDelta, ie.xOld, ie.getX());
                  cy += Mth.lerp(tickDelta, ie.yOld, ie.getY());
                  cz += Mth.lerp(tickDelta, ie.zOld, ie.getZ());
               }

               int n = cluster.size();
               cx /= n;
               cy = cy / n + 0.5;
               cz /= n;
               float[] screen = project(cx, cy, cz, projection);
               if (screen != null) {
                  List<ModuleNameTagRenderer.Agg> aggs = new ArrayList<>(agg.values());
                  aggs.sort((ax, b) -> Integer.compare(b.count, ax.count));
                  List<FormattedCharSequence> lines = new ArrayList<>();
                  int max = Math.min(aggs.size(), 6);

                  for (int k = 0; k < max; k++) {
                     ModuleNameTagRenderer.Agg a = aggs.get(k);
                     lines.add(a.name.copy().append(Component.literal(" ×" + a.count).withStyle(countStyle)).getVisualOrderText());
                  }

                  if (aggs.size() > max) {
                     lines.add(Component.literal("+" + (aggs.size() - max) + " more").getVisualOrderText());
                  }

                  double dist = Math.sqrt(distSq(camPos, cx, cy, cz));
                  drawLabel(context, screen[0], screen[1], lines, -1, scaleFor(baseScale, distanceScale, dist));
               }
            }
         } catch (Throwable var32) {
         }
      }
   }

   private static float scaleFor(double baseScale, boolean distanceScale, double dist) {
      float s = (float)baseScale;
      if (distanceScale) {
         s *= (float)Mth.clamp(12.0 / Math.max(1.0, dist), 0.4, 1.2);
      }

      return Math.max(0.05F, s);
   }

   private static void drawLabel(GuiGraphicsExtractor context, float screenX, float screenY, List<FormattedCharSequence> lines, int color, float scale) {
      if (!lines.isEmpty()) {
         context.pose().pushMatrix();
         context.pose().scale(scale, scale);
         int ox = Math.round(screenX / scale);
         int oy = Math.round(screenY / scale);
         int w = 0;

         for (FormattedCharSequence line : lines) {
            w = Math.max(w, MC.font.width(line));
         }

         int totalH = lines.size() * 10;
         int top = oy - totalH - 2;
         UiRenderer.rect(context, UiBounds.of(ox - w / 2 - 2, top, w + 4, totalH + 2), -1879048192);
         int ty = top + 2;

         for (FormattedCharSequence line : lines) {
            context.text(MC.font, line, ox - MC.font.width(line) / 2, ty, color, true);
            ty += 10;
         }

         context.pose().popMatrix();
      }
   }

   private static float[] projectHead(Entity entity, float tickDelta, ModuleNameTagRenderer.Projection p) {
      double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
      double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) + entity.getDimensions(entity.getPose()).height() + 0.45;
      double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
      return project(x, y, z, p);
   }

   private static float[] project(double worldX, double worldY, double worldZ, ModuleNameTagRenderer.Projection p) {
      Vec3 cam = p.cameraPosition();
      Vector4f v = new Vector4f((float)(worldX - cam.x), (float)(worldY - cam.y), (float)(worldZ - cam.z), 1.0F);
      p.matrix().transform(v);
      if (v.w <= 0.001F) {
         return null;
      } else {
         float ndcX = v.x / v.w;
         float ndcY = v.y / v.w;
         if (!Float.isNaN(ndcX) && !Float.isNaN(ndcY) && !Float.isInfinite(ndcX) && !Float.isInfinite(ndcY)) {
            float sx = (ndcX * 0.5F + 0.5F) * p.screenWidth();
            float sy = (0.5F - ndcY * 0.5F) * p.screenHeight();
            return new float[]{sx, sy};
         } else {
            return null;
         }
      }
   }

   private static double distSq(Vec3 cam, double x, double y, double z) {
      double dx = x - cam.x;
      double dy = y - cam.y;
      double dz = z - cam.z;
      return dx * dx + dy * dy + dz * dz;
   }

   private static final class Agg {
      final Component name;
      int count;

      Agg(Component name) {
         this.name = name;
      }
   }

   private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
   }
}
