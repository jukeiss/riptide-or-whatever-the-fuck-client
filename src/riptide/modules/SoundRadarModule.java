package riptide.modules;

import java.util.ArrayDeque;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

/**
 * A radar of where recent sounds came from. SoundAlert says something happened;
 * this shows you which way it was, which is what you need when the wall is in the way.
 * Blips fade out over the hold time and the newest one can name the sound.
 */
public final class SoundRadarModule extends Module {
   private static SoundRadarModule cached;
   private final ArrayDeque<SoundRadarModule.Blip> blips = new ArrayDeque<>();

   public SoundRadarModule() {
      super("sound-radar", "Sound Radar", ModuleCategory.RENDER, "Plots where recent sounds came from, relative to the way you are facing.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Bottom Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the radar sits in.")
            .group("Display")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 6, 0, 300, 1).description("Gap from the screen edge, in pixels.").group("Display").build());
      this.add(new IntSetting("size", "Size", 80, 48, 200, 4).description("Radar width/height, in pixels.").group("Display").build());
      this.add(new IntSetting("range", "Range", 48, 8, 128, 8).unit("blocks").description("How far the edge of the radar reaches.").group("Display").build());
      this.add(new IntSetting("hold", "Fade Time", 3000, 250, 15000, 250).unit("ms").description("How long a blip takes to fade out.").group("Display").build());
      this.add(
         new BoolSetting("rotate", "Face Forward", true)
            .description("On, the radar turns with you so up is straight ahead. Off, up is north.")
            .group("Display")
            .build()
      );
      this.add(new BoolSetting("label", "Name Newest", true).description("Print the newest sound's name under the radar.").group("Display").build());
      this.add(new BoolSetting("containers", "Chests And Doors", true).description("Containers, doors and trapdoors opening.").group("Sounds").build());
      this.add(new BoolSetting("combat", "Combat", true).description("Hits, totems, pearls, crystals and anchors.").group("Sounds").build());
      this.add(new BoolSetting("mining", "Mining", true).description("Blocks being broken or placed.").group("Sounds").build());
      this.add(new BoolSetting("steps", "Footsteps", false).description("Footsteps. Noisy, so off by default.").group("Sounds").build());
      this.add(new BoolSetting("other", "Everything Else", false).description("Every other sound the server sends.").group("Sounds").build());
      this.add(new ColorSetting("c-bg", "Background", -1609559016).group("Colors").build());
      this.add(new ColorSetting("c-grid", "Grid", 1090519039).group("Colors").build());
      this.add(new ColorSetting("c-you", "You", -1).group("Colors").build());
      this.add(new ColorSetting("c-container", "Chests And Doors", -678365).group("Colors").build());
      this.add(new ColorSetting("c-combat", "Combat", -2080722).group("Colors").build());
      this.add(new ColorSetting("c-mining", "Mining", -11890433).group("Colors").build());
      this.add(new ColorSetting("c-other", "Other", -4602154).group("Colors").build());
   }

   private static SoundRadarModule instance() {
      SoundRadarModule module = cached;
      if (module == null && ModuleRegistry.get("sound-radar") instanceof SoundRadarModule found) {
         module = found;
         cached = found;
      }

      return module;
   }

   @Override
   public void onEnable() {
      this.clear();
   }

   @Override
   public void onDisable() {
      this.clear();
   }

   @Override
   public void onGameJoin() {
      this.clear();
   }

   @Override
   public void onGameLeft() {
      this.clear();
   }

   private void clear() {
      synchronized (this.blips) {
         this.blips.clear();
      }
   }

   @Override
   public void onSoundPacket(ClientboundSoundPacket packet) {
      try {
         if (MC.player != null && !PackHideState.isActive()) {
            String name = soundName(packet);
            SoundRadarModule.Kind kind = classify(name);
            if (kind != null && this.enabled(kind)) {
               double dx = packet.getX() - MC.player.getX();
               double dz = packet.getZ() - MC.player.getZ();
               double range = this.integer("range");
               if (!(dx * dx + dz * dz > range * range)) {
                  synchronized (this.blips) {
                     this.blips.addLast(new SoundRadarModule.Blip(dx, dz, System.currentTimeMillis(), kind, shortName(name)));

                     while (this.blips.size() > 64) {
                        this.blips.pollFirst();
                     }
                  }
               }
            }
         }
      } catch (Throwable var11) {
      }
   }

   private boolean enabled(SoundRadarModule.Kind kind) {
      return switch (kind) {
         case CONTAINER -> this.bool("containers");
         case COMBAT -> this.bool("combat");
         case MINING -> this.bool("mining");
         case STEP -> this.bool("steps");
         default -> this.bool("other");
      };
   }

   private static String soundName(ClientboundSoundPacket packet) {
      try {
         SoundEvent event = (SoundEvent)packet.getSound().value();
         return event == null ? "" : event.location().getPath().toLowerCase(Locale.ROOT);
      } catch (Throwable var2) {
         return "";
      }
   }

   private static String shortName(String path) {
      String pretty = path.replace('.', ' ').replace('_', ' ').trim();
      return pretty.isEmpty() ? "Sound" : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
   }

   private static SoundRadarModule.Kind classify(String name) {
      if (name.isEmpty()) {
         return null;
      } else if (name.contains("chest") || name.contains("shulker_box") || name.contains("barrel") || name.contains("door") || name.contains("ender_chest")) {
         return SoundRadarModule.Kind.CONTAINER;
      } else if (name.contains("totem")
         || name.contains("ender_pearl")
         || name.contains("explode")
         || name.contains("anchor")
         || name.contains("crystal")
         || name.contains("hurt")
         || name.contains("attack")
         || name.contains("death")) {
         return SoundRadarModule.Kind.COMBAT;
      } else if (name.contains("step")) {
         return SoundRadarModule.Kind.STEP;
      } else {
         return name.contains("break") || name.contains("place") || name.contains("dig") ? SoundRadarModule.Kind.MINING : SoundRadarModule.Kind.OTHER;
      }
   }

   public static void render(GuiGraphicsExtractor graphics) {
      SoundRadarModule module = instance();
      if (module != null && module.isEnabled() && !PackHideState.isActive()) {
         if (MC != null && MC.player != null && MC.level != null && !MC.gui.hud.isHidden()) {
            try {
               module.draw(graphics);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor graphics) {
      int size = this.integer("size");
      long hold = this.integer("hold");
      long now = System.currentTimeMillis();
      boolean label = this.bool("label");
      int panelH = size + (label ? 10 : 0);
      String corner = this.choice("corner");
      int margin = this.integer("margin");
      int x = corner.contains("Right") ? graphics.guiWidth() - margin - size : margin;
      int y = HudStack.y(corner, margin, panelH, graphics.guiHeight());
      int cx = x + size / 2;
      int cy = y + size / 2;
      graphics.fill(x, y, x + size, y + size, ModuleRenderUtil.color(this, "c-bg", -1609559016));
      int grid = ModuleRenderUtil.color(this, "c-grid", 1090519039);
      graphics.fill(cx, y, cx + 1, y + size, grid);
      graphics.fill(x, cy, x + size, cy + 1, grid);
      double range = this.integer("range");
      double scale = size / 2.0 / range;
      // Screen y grows downwards, so a blip north of you has to move up the panel.
      boolean rotate = this.bool("rotate");
      double yaw = Math.toRadians(MC.player.getYRot());
      double sin = Math.sin(yaw);
      double cos = Math.cos(yaw);
      String newest = null;
      long newestAt = 0L;
      SoundRadarModule.Blip[] snapshot;
      synchronized (this.blips) {
         this.blips.removeIf(blip -> now - blip.at > hold);
         snapshot = this.blips.toArray(new SoundRadarModule.Blip[0]);
      }

      for (SoundRadarModule.Blip blip : snapshot) {
         long age = now - blip.at;
         if (age <= hold) {
            float life = 1.0F - (float)age / hold;
            int alpha = Math.max(16, Math.round(255.0F * life));
            int color = ModuleRenderUtil.color(this, blip.kind.colorId, -4602154) & 16777215 | alpha << 24;
            // North-up leaves world offsets alone; face-forward projects onto the
            // player's right/look axes so straight ahead is the top of the panel.
            double rx = rotate ? -(blip.dx * cos + blip.dz * sin) : blip.dx;
            double rz = rotate ? blip.dx * sin - blip.dz * cos : blip.dz;
            int px = cx + (int)Math.round(rx * scale);
            int py = cy + (int)Math.round(rz * scale);
            int half = life > 0.6F ? 2 : 1;
            px = Math.max(x + half, Math.min(x + size - half - 1, px));
            py = Math.max(y + half, Math.min(y + size - half - 1, py));
            graphics.fill(px - half, py - half, px + half + 1, py + half + 1, color);
            if (blip.at > newestAt) {
               newestAt = blip.at;
               newest = blip.name;
            }
         }
      }

      graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, ModuleRenderUtil.color(this, "c-you", -1) | 0xFF000000);
      if (label && newest != null) {
         Font font = MC.font;
         graphics.text(font, Component.literal(newest), x, y + size + 1, ModuleRenderUtil.color(this, "c-other", -4602154) | 0xFF000000);
      }
   }

   private static enum Kind {
      CONTAINER("c-container"),
      COMBAT("c-combat"),
      MINING("c-mining"),
      STEP("c-other"),
      OTHER("c-other");

      final String colorId;

      private Kind(String colorId) {
         this.colorId = colorId;
      }
   }

   private static final class Blip {
      final double dx;
      final double dz;
      final long at;
      final SoundRadarModule.Kind kind;
      final String name;

      Blip(double dx, double dz, long at, SoundRadarModule.Kind kind, String name) {
         this.dx = dx;
         this.dz = dz;
         this.at = at;
         this.kind = kind;
         this.name = name;
      }
   }
}
