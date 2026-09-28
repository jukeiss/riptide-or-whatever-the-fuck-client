package riptide.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import riptide.gui.RiptideThemeApplyOverlay;

public final class RiptideThemeTextures {
   private static final Minecraft MC = Minecraft.getInstance();
   public static final Identifier PANORAMA_LOCATION = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/panorama");
   private static final Identifier PANORAMA_OVERLAY_SRC = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/panorama_overlay.png");
   private static final Identifier PANORAMA_OVERLAY_DYN = Identifier.fromNamespaceAndPath("riptide", "dynamic/theme/panorama_overlay");
   private static final Map<Identifier, RiptideThemeTextures.Recolored> UI_IDS = new HashMap<>();
   private static final Map<Identifier, RiptideThemeTextures.ColorRecolored> COLOR_IDS = new HashMap<>();
   private static final Set<Identifier> COLOR_REBUILDING = new HashSet<>();
   private static final Set<Identifier> REBUILDING = new HashSet<>();
   private static final Map<Identifier, Integer> FAILED = new HashMap<>();
   private static final Map<Identifier, Identifier> WHITE_IDS = new HashMap<>();
   private static int themeGeneration;
   private static RiptideThemeTextures.RecoloredCubeMapTexture panorama;
   private static volatile boolean panoramaAvailable;
   private static AbstractTexture overlayTexture;
   private static boolean overlayBuilt;

   private RiptideThemeTextures() {
   }

   public static Identifier recolored(Identifier source, RiptideTheme.Channel ch) {
      if (source == null) {
         return null;
      } else {
         RiptideTheme.State st = RiptideTheme.active();
         if (!st.isActive(ch)) {
            return source;
         } else {
            RiptideThemeTextures.Recolored cached = UI_IDS.get(source);
            if (cached != null && cached.generation == themeGeneration) {
               return cached.id;
            } else {
               Integer failedGen = FAILED.get(source);
               if (failedGen != null && failedGen == themeGeneration) {
                  return cached != null ? cached.id : source;
               } else {
                  if (cached == null && !REBUILDING.contains(source)) {
                     Identifier built = buildRecolorNow(source, ch, st);
                     if (built != null) {
                        return built;
                     }
                  }

                  kickRecolorRebuild(source, ch, st);
                  return cached != null ? cached.id : source;
               }
            }
         }
      }
   }

   public static Identifier recoloredTo(Identifier source, int targetColor) {
      if (source == null) {
         return null;
      } else {
         RiptideThemeTextures.ColorRecolored cached = COLOR_IDS.get(source);
         if (cached != null && cached.color == targetColor) {
            return cached.id;
         } else {
            kickColorRebuild(source, targetColor);
            return cached != null ? cached.id : source;
         }
      }
   }

   private static void kickColorRebuild(Identifier source, int targetColor) {
      if (COLOR_REBUILDING.add(source)) {
         byte[] png = readResourceBytes(source);
         if (png == null) {
            COLOR_REBUILDING.remove(source);
         } else {
            float[] hsb = Color.RGBtoHSB(targetColor >> 16 & 0xFF, targetColor >> 8 & 0xFF, targetColor & 0xFF, null);
            float targetHue = hsb[0];
            float targetSat = hsb[1];
            RiptideBackgroundTasks.runTracked("skybox-recolor", () -> {
               NativeImage recolored = null;

               try {
                  NativeImage src = NativeImage.read(png);

                  try {
                     recolored = src.mappedCopy(argb -> RiptideTheme.recolorImagePixelTo(argb, targetHue, targetSat));
                  } catch (Throwable var10) {
                     if (src != null) {
                        try {
                           src.close();
                        } catch (Throwable var9) {
                           var10.addSuppressed(var9);
                        }
                     }

                     throw var10;
                  }

                  if (src != null) {
                     src.close();
                  }
               } catch (Throwable var11) {
                  riptide.RiptideClientAddon.LOG.warn("Skybox recolor failed for {}", source, var11);
               }

               NativeImage result = recolored;
               MC.execute(() -> {
                  COLOR_REBUILDING.remove(source);
                  if (result != null) {
                     Identifier id = colorDerivedId(source);
                     MC.getTextureManager().register(id, new RiptideThemeTextures.DynamicTexture(source.toString(), result, FilterMode.LINEAR));
                     COLOR_IDS.put(source, new RiptideThemeTextures.ColorRecolored(id, targetColor));
                  }
               });
            });
         }
      }
   }

   private static Identifier colorDerivedId(Identifier source) {
      return Identifier.fromNamespaceAndPath(
         "riptide", "dynamic/skybox/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", "")
      );
   }

   private static Identifier buildRecolorNow(Identifier source, RiptideTheme.Channel ch, RiptideTheme.State st) {
      byte[] png = readResourceBytes(source);
      if (png == null) {
         return null;
      } else {
         int generation = themeGeneration;

         try {
            NativeImage src = NativeImage.read(png);

            Identifier var8;
            try {
               NativeImage recolored = src.mappedCopy(argb -> RiptideTheme.recolorImagePixel(argb, ch, st));
               Identifier id = derivedId(source);
               MC.getTextureManager().register(id, new RiptideThemeTextures.DynamicTexture(source.toString(), recolored, FilterMode.LINEAR));
               UI_IDS.put(source, new RiptideThemeTextures.Recolored(id, generation));
               var8 = id;
            } catch (Throwable var10) {
               if (src != null) {
                  try {
                     src.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (src != null) {
               src.close();
            }

            return var8;
         } catch (Throwable var11) {
            FAILED.put(source, generation);
            riptide.RiptideClientAddon.LOG.warn("Theme recolor (sync) failed for {} (backing off this theme generation)", source, var11);
            return null;
         }
      }
   }

   private static void kickRecolorRebuild(Identifier source, RiptideTheme.Channel ch, RiptideTheme.State st) {
      if (REBUILDING.add(source)) {
         int generation = themeGeneration;
         byte[] png = readResourceBytes(source);
         if (png == null) {
            REBUILDING.remove(source);
         } else {
            RiptideBackgroundTasks.runTracked("theme-recolor", () -> {
               NativeImage recolored = null;

               try {
                  NativeImage src = NativeImage.read(png);

                  try {
                     recolored = src.mappedCopy(argb -> RiptideTheme.recolorImagePixel(argb, ch, st));
                  } catch (Throwable var10) {
                     if (src != null) {
                        try {
                           src.close();
                        } catch (Throwable var9) {
                           var10.addSuppressed(var9);
                        }
                     }

                     throw var10;
                  }

                  if (src != null) {
                     src.close();
                  }
               } catch (Throwable var11) {
                  riptide.RiptideClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", source, var11);
               }

               NativeImage result = recolored;
               MC.execute(() -> {
                  REBUILDING.remove(source);
                  if (generation != themeGeneration) {
                     if (result != null) {
                        result.close();
                     }
                  } else if (result == null) {
                     FAILED.put(source, generation);
                  } else {
                     Identifier id = derivedId(source);
                     MC.getTextureManager().register(id, new RiptideThemeTextures.DynamicTexture(source.toString(), result, FilterMode.LINEAR));
                     UI_IDS.put(source, new RiptideThemeTextures.Recolored(id, generation));
                  }
               });
            });
         }
      }
   }

   private static byte[] readResourceBytes(Identifier source) {
      try {
         Optional<Resource> res = MC.getResourceManager().getResource(source);
         if (res.isEmpty()) {
            return null;
         } else {
            byte[] var3;
            try (InputStream in = res.get().open()) {
               var3 = in.readAllBytes();
            }

            return var3;
         }
      } catch (Throwable var7) {
         FAILED.put(source, themeGeneration);
         riptide.RiptideClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", source, var7);
         return null;
      }
   }

   public static Identifier whitened(Identifier source) {
      if (source == null) {
         return null;
      } else {
         Identifier cached = WHITE_IDS.get(source);
         if (cached != null) {
            return cached;
         } else {
            try {
               TextureContents contents = TextureContents.load(MC.getResourceManager(), source);
               NativeImage src = contents.image();
               NativeImage white = src.mappedCopy(argb -> argb & 0xFF000000 | 16777215);
               src.close();
               Identifier id = Identifier.fromNamespaceAndPath(
                  "riptide", "dynamic/white/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", "")
               );
               MC.getTextureManager().register(id, new RiptideThemeTextures.DynamicTexture(source.toString(), white, FilterMode.LINEAR));
               WHITE_IDS.put(source, id);
               return id;
            } catch (Throwable var6) {
               riptide.RiptideClientAddon.LOG.warn("Theme whiten failed for {}", source, var6);
               return source;
            }
         }
      }
   }

   private static Identifier derivedId(Identifier source) {
      return Identifier.fromNamespaceAndPath("riptide", "dynamic/theme/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", ""));
   }

   public static void registerPanorama(TextureManager textureManager) {
      try {
         panorama = new RiptideThemeTextures.RecoloredCubeMapTexture(PANORAMA_LOCATION);
         textureManager.register(PANORAMA_LOCATION, panorama);
      } catch (Throwable var2) {
         panoramaAvailable = false;
         riptide.RiptideClientAddon.LOG.warn("Failed to register themed panorama", var2);
      }
   }

   public static boolean isPanoramaAvailable() {
      return panoramaAvailable;
   }

   public static void reloadPanorama() {
      if (panorama != null && panoramaAvailable) {
         int generation = themeGeneration;
         Runnable jobDone = RiptideThemeApplyOverlay.beginJob("Recoloring panorama");
         MC.execute(() -> {
            try {
               if (generation == themeGeneration) {
                  TextureContents contents = panorama.loadContents(MC.getResourceManager());

                  try {
                     panorama.apply(contents);
                  } catch (Throwable var11) {
                     riptide.RiptideClientAddon.LOG.warn("Failed to upload the recolored panorama (keeping the current one)", var11);

                     try {
                        contents.image().close();
                     } catch (Throwable var10) {
                     }

                     return;
                  }

                  return;
               }
            } catch (Throwable var12) {
               riptide.RiptideClientAddon.LOG.warn("Failed to recolor themed panorama (keeping the current one)", var12);
               return;
            } finally {
               jobDone.run();
            }
         });
      }
   }

   public static Identifier panoramaOverlay(Identifier original) {
      RiptideTheme.State st = RiptideTheme.active();
      if (!st.isActive(RiptideTheme.Channel.BACKDROP)) {
         return original;
      } else if (overlayTexture != null) {
         return PANORAMA_OVERLAY_DYN;
      } else {
         if (!overlayBuilt) {
            overlayBuilt = true;
            kickOverlayBuild(st, () -> {});
         }

         return original;
      }
   }

   private static void kickOverlayBuild(RiptideTheme.State st, Runnable jobDone) {
      int generation = themeGeneration;

      byte[] png;
      try {
         Optional<Resource> res = MC.getResourceManager().getResource(PANORAMA_OVERLAY_SRC);
         if (res.isEmpty()) {
            overlayBuilt = false;
            jobDone.run();
            return;
         }

         try (InputStream in = res.get().open()) {
            png = in.readAllBytes();
         }
      } catch (Throwable var10) {
         riptide.RiptideClientAddon.LOG.warn("Failed to build themed panorama overlay", var10);
         jobDone.run();
         return;
      }

      RiptideBackgroundTasks.runTracked("theme-overlay", () -> {
         NativeImage recolored = null;

         try {
            NativeImage src = NativeImage.read(png);

            try {
               recolored = src.mappedCopy(argb -> RiptideTheme.recolorImagePixel(argb, RiptideTheme.Channel.BACKDROP, st));
            } catch (Throwable var9x) {
               if (src != null) {
                  try {
                     src.close();
                  } catch (Throwable var8) {
                     var9x.addSuppressed(var8);
                  }
               }

               throw var9x;
            }

            if (src != null) {
               src.close();
            }
         } catch (Throwable var10x) {
            riptide.RiptideClientAddon.LOG.warn("Failed to build themed panorama overlay", var10x);
         }

         NativeImage result = recolored;
         MC.execute(() -> {
            try {
               if (generation == themeGeneration) {
                  if (result == null) {
                     return;
                  }

                  overlayTexture = new RiptideThemeTextures.DynamicTexture("panorama_overlay", result, FilterMode.LINEAR);
                  MC.getTextureManager().register(PANORAMA_OVERLAY_DYN, overlayTexture);
                  return;
               }

               if (result != null) {
                  result.close();
               }
            } finally {
               jobDone.run();
            }
         });
      });
   }

   public static void invalidate() {
      themeGeneration++;
      FAILED.clear();
      overlayBuilt = false;
      overlayTexture = null;
      RiptideSvgHudLogo.clear();
      RiptideTheme.State st = RiptideTheme.active();
      if (st.isActive(RiptideTheme.Channel.BACKDROP)) {
         overlayBuilt = true;
         kickOverlayBuild(st, RiptideThemeApplyOverlay.beginJob("Recoloring backdrop"));
      }

      reloadPanorama();
   }

   public static RiptideThemeTextures.Preview preview(Identifier source, RiptideTheme.Channel channel, int maxDim) {
      try {
         TextureContents contents = TextureContents.load(MC.getResourceManager(), source);
         NativeImage full = contents.image();
         NativeImage scaled = downscale(full, maxDim);
         if (scaled != full) {
            full.close();
         }

         NativeImage work = new NativeImage(scaled.getWidth(), scaled.getHeight(), false);

         for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
               work.setPixel(x, y, scaled.getPixel(x, y));
            }
         }

         Identifier id = Identifier.fromNamespaceAndPath("riptide", "dynamic/theme/preview/" + source.getPath().replace('/', '_').replace(".png", ""));
         RiptideThemeTextures.DynamicTexture tex = new RiptideThemeTextures.DynamicTexture("preview " + source, work, FilterMode.LINEAR);
         MC.getTextureManager().register(id, tex);
         return new RiptideThemeTextures.Preview(channel, scaled, tex, id);
      } catch (Throwable var9) {
         riptide.RiptideClientAddon.LOG.warn("Failed to build theme preview for {}", source, var9);
         return null;
      }
   }

   private static NativeImage downscale(NativeImage src, int maxDim) {
      int w = src.getWidth();
      int h = src.getHeight();
      if (w <= maxDim && h <= maxDim) {
         return src;
      } else {
         float scale = Math.min((float)maxDim / w, (float)maxDim / h);
         int nw = Math.max(1, Math.round(w * scale));
         int nh = Math.max(1, Math.round(h * scale));
         NativeImage out = new NativeImage(nw, nh, false);

         for (int y = 0; y < nh; y++) {
            int sy0 = (int)Math.floor(y * ((double)h / nh));
            int sy1 = Math.min(h, Math.max(sy0 + 1, (int)Math.ceil((y + 1) * ((double)h / nh))));

            for (int x = 0; x < nw; x++) {
               int sx0 = (int)Math.floor(x * ((double)w / nw));
               int sx1 = Math.min(w, Math.max(sx0 + 1, (int)Math.ceil((x + 1) * ((double)w / nw))));
               long aSum = 0L;
               long rSum = 0L;
               long gSum = 0L;
               long bSum = 0L;
               int n = 0;

               for (int yy = sy0; yy < sy1; yy++) {
                  for (int xx = sx0; xx < sx1; xx++) {
                     int px = src.getPixel(xx, yy);
                     int pa = px >>> 24 & 0xFF;
                     aSum += pa;
                     rSum += (long)(px >>> 16 & 0xFF) * pa;
                     gSum += (long)(px >>> 8 & 0xFF) * pa;
                     bSum += (long)(px & 0xFF) * pa;
                     n++;
                  }
               }

               if (n == 0) {
                  out.setPixel(x, y, 0);
               } else {
                  int outA = (int)(aSum / n);
                  int outR = aSum == 0L ? 0 : (int)(rSum / aSum);
                  int outG = aSum == 0L ? 0 : (int)(gSum / aSum);
                  int outB = aSum == 0L ? 0 : (int)(bSum / aSum);
                  out.setPixel(x, y, outA << 24 | outR << 16 | outG << 8 | outB);
               }
            }
         }

         return out;
      }
   }

   private record ColorRecolored(Identifier id, int color) {
   }

   private static final class DynamicTexture extends AbstractTexture {
      private final NativeImage pixels;

      private DynamicTexture(String label, NativeImage pixels, FilterMode filter) {
         this.pixels = pixels;
         this.texture = RenderSystem.getDevice().createTexture(label, 5, GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
         this.sampler = RenderSystem.getSamplerCache().getRepeat(filter);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
         this.upload();
      }

      private void upload() {
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
      }

      public void close() {
         try {
            this.pixels.close();
         } catch (Throwable var2) {
         }

         super.close();
      }
   }

   public static final class Preview implements AutoCloseable {
      private final RiptideTheme.Channel channel;
      private final NativeImage source;
      private final RiptideThemeTextures.DynamicTexture texture;
      private final Identifier id;
      private int signature = Integer.MIN_VALUE;
      private final Object lock = new Object();
      private boolean closed;
      private boolean jobRunning;
      private boolean resourcesFreed;
      private RiptideTheme.State pendingState;

      private Preview(RiptideTheme.Channel channel, NativeImage source, RiptideThemeTextures.DynamicTexture texture, Identifier id) {
         this.channel = channel;
         this.source = source;
         this.texture = texture;
         this.id = id;
      }

      public Identifier id() {
         return this.id;
      }

      public int width() {
         return this.source.getWidth();
      }

      public int height() {
         return this.source.getHeight();
      }

      private static int backdropSignature(RiptideTheme.State st) {
         int sig = Float.floatToIntBits(st.hueOf(RiptideTheme.Channel.BACKDROP)) * 31 + Float.floatToIntBits(st.satOf(RiptideTheme.Channel.BACKDROP));
         return sig == 0 ? 1 : sig;
      }

      public void update(RiptideTheme.State st) {
         int sig = this.channel == RiptideTheme.Channel.BACKDROP ? backdropSignature(st) : st.previewSignature(this.channel);
         if (sig != this.signature) {
            this.signature = sig;
            synchronized (this.lock) {
               if (this.closed) {
                  return;
               }

               this.pendingState = st;
               if (this.jobRunning) {
                  return;
               }

               this.jobRunning = true;
            }

            RiptideBackgroundTasks.runTracked("theme-preview", this::recolorPending);
         }
      }

      private void recolorPending() {
         try {
            while (true) {
               RiptideTheme.State st;
               synchronized (this.lock) {
                  if (this.closed || this.pendingState == null) {
                     this.jobRunning = false;
                     break;
                  }

                  st = this.pendingState;
                  this.pendingState = null;
               }

               int w = this.source.getWidth();
               int h = this.source.getHeight();
               int[] out = new int[w * h];

               for (int y = 0; y < h; y++) {
                  for (int x = 0; x < w; x++) {
                     out[y * w + x] = this.channel == RiptideTheme.Channel.BACKDROP
                        ? RiptideTheme.recolorImagePixelTo(
                           this.source.getPixel(x, y), st.hueOf(RiptideTheme.Channel.BACKDROP), st.satOf(RiptideTheme.Channel.BACKDROP)
                        )
                        : RiptideTheme.recolorImagePixel(this.source.getPixel(x, y), this.channel, st);
                  }
               }

               RiptideThemeTextures.MC.execute(() -> {
                  synchronized (this.lock) {
                     if (!this.closed) {
                        for (int yx = 0; yx < h; yx++) {
                           for (int xx = 0; xx < w; xx++) {
                              this.texture.pixels.setPixel(xx, yx, out[yx * w + xx]);
                           }
                        }

                        this.texture.upload();
                     }
                  }
               });
            }
         } catch (Throwable var12) {
            synchronized (this.lock) {
               this.jobRunning = false;
            }

            riptide.RiptideClientAddon.LOG.error("Theme preview recolor failed", var12);
         }

         boolean freeNow;
         synchronized (this.lock) {
            freeNow = this.closed;
         }

         if (freeNow) {
            this.closeResources();
         }
      }

      @Override
      public void close() {
         synchronized (this.lock) {
            this.closed = true;
            this.pendingState = null;
            if (this.jobRunning) {
               return;
            }
         }

         this.closeResources();
      }

      private void closeResources() {
         synchronized (this.lock) {
            if (this.resourcesFreed) {
               return;
            }

            this.resourcesFreed = true;
         }

         try {
            this.source.close();
         } catch (Throwable var4) {
         }

         try {
            this.texture.close();
         } catch (Throwable var3) {
         }
      }
   }

   private record Recolored(Identifier id, int generation) {
   }

   private static final class RecoloredCubeMapTexture extends CubeMapTexture {
      private RecoloredCubeMapTexture(Identifier id) {
         super(id);
      }

      public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
         TextureContents contents = super.loadContents(resourceManager);
         RiptideThemeTextures.panoramaAvailable = true;
         RiptideTheme.State st = RiptideTheme.active();
         float hue = st.hueOf(RiptideTheme.Channel.BACKDROP);
         float sat = st.satOf(RiptideTheme.Channel.BACKDROP);
         NativeImage img = contents.image();
         int changed = 0;

         for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
               int before = img.getPixel(x, y);
               int after = RiptideTheme.recolorImagePixelTo(before, hue, sat);
               if (before != after) {
                  img.setPixel(x, y, after);
                  changed++;
               }
            }
         }

         riptide.RiptideClientAddon.LOG
            .info(
               "Panorama recolored to hue {} sat {}: {} of {} pixels changed",
               new Object[]{
                  String.format(Locale.ROOT, "%.0f", hue * 360.0F), String.format(Locale.ROOT, "%.2f", sat), changed, img.getWidth() * img.getHeight()
               }
            );
         return contents;
      }
   }
}
