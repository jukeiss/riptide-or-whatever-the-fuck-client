package riptide.util.multi.captcha;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.material.MapColor;

public final class CaptchaMapImage {
   public static final int SIZE = 128;
   public final int[] rgb = new int[16384];

   public static CaptchaMapImage fromMapColors(byte[] colors) {
      if (colors != null && colors.length == 16384) {
         CaptchaMapImage img = new CaptchaMapImage();

         for (int i = 0; i < colors.length; i++) {
            img.rgb[i] = MapColor.getColorFromPackedId(colors[i] & 255) & 16777215;
         }

         return img;
      } else {
         return null;
      }
   }

   static int r(int c) {
      return c >> 16 & 0xFF;
   }

   static int g(int c) {
      return c >> 8 & 0xFF;
   }

   static int b(int c) {
      return c & 0xFF;
   }

   static int brightness(int c) {
      return (r(c) + g(c) + b(c)) / 3;
   }

   static int saturation(int c) {
      int mx = Math.max(r(c), Math.max(g(c), b(c)));
      int mn = Math.min(r(c), Math.min(g(c), b(c)));
      return mx - mn;
   }

   static boolean[] medianDenoise(boolean[] mask, int w, int h) {
      boolean[] out = new boolean[mask.length];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int on = 0;
            int total = 0;

            for (int dy = -1; dy <= 1; dy++) {
               int ny = y + dy;
               if (ny >= 0 && ny < h) {
                  for (int dx = -1; dx <= 1; dx++) {
                     int nx = x + dx;
                     if (nx >= 0 && nx < w) {
                        total++;
                        if (mask[ny * w + nx]) {
                           on++;
                        }
                     }
                  }
               }
            }

            out[y * w + x] = on * 2 > total;
         }
      }

      return out;
   }

   static boolean[] erode(boolean[] mask, int w, int h) {
      boolean[] out = new boolean[mask.length];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            if (mask[y * w + x]
               && x != 0
               && y != 0
               && x != w - 1
               && y != h - 1
               && mask[y * w + x - 1]
               && mask[y * w + x + 1]
               && mask[(y - 1) * w + x]
               && mask[(y + 1) * w + x]) {
               out[y * w + x] = true;
            }
         }
      }

      return out;
   }

   static boolean[] dilate(boolean[] mask, int w, int h) {
      boolean[] out = new boolean[mask.length];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            if (mask[y * w + x]
               || x > 0 && mask[y * w + x - 1]
               || x < w - 1 && mask[y * w + x + 1]
               || y > 0 && mask[(y - 1) * w + x]
               || y < h - 1 && mask[(y + 1) * w + x]) {
               out[y * w + x] = true;
            }
         }
      }

      return out;
   }

   static boolean[] open(boolean[] mask, int w, int h) {
      return dilate(erode(mask, w, h), w, h);
   }

   static List<CaptchaMapImage.Blob> connectedComponents(boolean[] mask, int w, int h) {
      List<CaptchaMapImage.Blob> blobs = new ArrayList<>();
      int[] label = new int[mask.length];
      int[] stack = new int[mask.length];

      for (int start = 0; start < mask.length; start++) {
         if (mask[start] && label[start] == 0) {
            int sp = 0;
            stack[sp++] = start;
            label[start] = 1;
            CaptchaMapImage.Blob blob = new CaptchaMapImage.Blob();
            blob.minX = Integer.MAX_VALUE;
            blob.minY = Integer.MAX_VALUE;
            blob.maxX = Integer.MIN_VALUE;
            blob.maxY = Integer.MIN_VALUE;
            List<Integer> pixels = new ArrayList<>();

            while (sp > 0) {
               int p = stack[--sp];
               int px = p % w;
               int py = p / w;
               pixels.add(p);
               blob.area++;
               if (px < blob.minX) {
                  blob.minX = px;
               }

               if (px > blob.maxX) {
                  blob.maxX = px;
               }

               if (py < blob.minY) {
                  blob.minY = py;
               }

               if (py > blob.maxY) {
                  blob.maxY = py;
               }

               sp = tryPush(mask, label, stack, sp, w, h, px - 1, py);
               sp = tryPush(mask, label, stack, sp, w, h, px + 1, py);
               sp = tryPush(mask, label, stack, sp, w, h, px, py - 1);
               sp = tryPush(mask, label, stack, sp, w, h, px, py + 1);
            }

            int bw = blob.width();
            int bh = blob.height();
            blob.localMask = new boolean[bw * bh];

            for (int pxx : pixels) {
               int pxxx = pxx % w - blob.minX;
               int pyx = pxx / w - blob.minY;
               blob.localMask[pyx * bw + pxxx] = true;
            }

            blobs.add(blob);
         }
      }

      return blobs;
   }

   private static int tryPush(boolean[] mask, int[] label, int[] stack, int sp, int w, int h, int x, int y) {
      if (x >= 0 && y >= 0 && x < w && y < h) {
         int idx = y * w + x;
         if (mask[idx] && label[idx] == 0) {
            label[idx] = 1;
            stack[sp++] = idx;
            return sp;
         } else {
            return sp;
         }
      } else {
         return sp;
      }
   }

   public static final class Blob {
      public int minX;
      public int minY;
      public int maxX;
      public int maxY;
      public int area;
      public boolean[] localMask;

      public int width() {
         return this.maxX - this.minX + 1;
      }

      public int height() {
         return this.maxY - this.minY + 1;
      }
   }
}
