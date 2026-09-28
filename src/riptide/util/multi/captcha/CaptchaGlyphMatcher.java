package riptide.util.multi.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class CaptchaGlyphMatcher {
   static final int GRID_W = 32;
   static final int GRID_H = 48;
   private static final double HB_HOLE = 0.12;
   private static final int MIN_HOLE_AREA = 22;
   private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
   private final List<CaptchaGlyphMatcher.Template> templates = new ArrayList<>();
   private final boolean heightAware;
   private final String charset;
   private CaptchaNet net;

   public CaptchaGlyphMatcher(Font font, String charset, double[] shears, boolean heightAware) {
      this.heightAware = heightAware;
      this.charset = charset;

      for (int i = 0; i < charset.length(); i++) {
         char ch = charset.charAt(i);

         for (double shear : shears) {
            CaptchaGlyphMatcher.Rendered r = this.renderNormalized(font, ch, shear);
            if (r != null && r.cov() != null) {
               float[] cc = center(r.cov());
               double norm = l2(cc);
               if (norm > 1.0E-6) {
                  this.templates.add(new CaptchaGlyphMatcher.Template(ch, cc, norm, r.aspect(), hasEnclosedHole(r.gridMask())));
               }
            }
         }
      }
   }

   public boolean heightAware() {
      return this.heightAware;
   }

   public boolean hasNet() {
      return this.net != null;
   }

   public float[] classify(float[] cov) {
      return this.net == null ? null : this.net.probs(cov);
   }

   public void setNet(CaptchaNet net) {
      if (net != null && (net.outputs() == this.charset.length() || net.outputs() == this.charset.length() + 1)) {
         this.net = net;
      }
   }

   private CaptchaGlyphMatcher.Rendered renderNormalized(Font font, char ch, double shear) {
      GlyphVector gv = font.createGlyphVector(FRC, String.valueOf(ch));
      Shape outline = gv.getOutline();
      if (Math.abs(shear) > 1.0E-6) {
         AffineTransform sh = AffineTransform.getShearInstance(shear, shear);
         outline = sh.createTransformedShape(outline);
      }

      Rectangle2D b = outline.getBounds2D();
      if (!(b.getWidth() < 1.0) && !(b.getHeight() < 1.0)) {
         double aspect = b.getWidth() / b.getHeight();
         int rw = (int)Math.ceil(b.getWidth()) + 2;
         int rh = (int)Math.ceil(b.getHeight()) + 2;
         BufferedImage img = new BufferedImage(rw, rh, 10);
         Graphics2D g = img.createGraphics();
         g.setColor(Color.BLACK);
         g.fillRect(0, 0, rw, rh);
         g.translate(1.0 - b.getX(), 1.0 - b.getY());
         g.setColor(Color.WHITE);
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.fill(outline);
         g.dispose();
         int[] gray = new int[rw * rh];
         boolean[] src = new boolean[rw * rh];

         for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
               int v = img.getRGB(x, y) & 0xFF;
               gray[y * rw + x] = v;
               src[y * rw + x] = v > 96;
            }
         }

         return new CaptchaGlyphMatcher.Rendered(coverageFromGray(gray, rw, rh), maskToGrid(src, rw, rh), aspect);
      } else {
         return null;
      }
   }

   static float[] coverageFromGray(int[] gray, int w, int h) {
      float[] out = new float[1536];
      if (w > 0 && h > 0) {
         for (int gy = 0; gy < 48; gy++) {
            int ya = gy * h / 48;
            int yb = Math.max(ya + 1, (gy + 1) * h / 48);

            for (int gx = 0; gx < 32; gx++) {
               int xa = gx * w / 32;
               int xb = Math.max(xa + 1, (gx + 1) * w / 32);
               double sum = 0.0;
               int tot = 0;

               for (int y = ya; y < yb && y < h; y++) {
                  for (int x = xa; x < xb && x < w; x++) {
                     sum += gray[y * w + x];
                     tot++;
                  }
               }

               out[gy * 32 + gx] = tot == 0 ? 0.0F : (float)(sum / (tot * 255.0));
            }
         }

         return out;
      } else {
         return out;
      }
   }

   static float[] coverageFromBinary(boolean[] src, int w, int h) {
      float[] out = new float[1536];
      if (w > 0 && h > 0) {
         for (int gy = 0; gy < 48; gy++) {
            int ya = gy * h / 48;
            int yb = Math.max(ya + 1, (gy + 1) * h / 48);

            for (int gx = 0; gx < 32; gx++) {
               int xa = gx * w / 32;
               int xb = Math.max(xa + 1, (gx + 1) * w / 32);
               int cnt = 0;
               int tot = 0;

               for (int y = ya; y < yb && y < h; y++) {
                  for (int x = xa; x < xb && x < w; x++) {
                     tot++;
                     if (src[y * w + x]) {
                        cnt++;
                     }
                  }
               }

               out[gy * 32 + gx] = tot == 0 ? 0.0F : (float)cnt / tot;
            }
         }

         return out;
      } else {
         return out;
      }
   }

   static boolean[] maskToGrid(boolean[] src, int w, int h) {
      boolean[] out = new boolean[1536];
      if (w > 0 && h > 0) {
         for (int gy = 0; gy < 48; gy++) {
            int sy = (int)((gy + 0.5) * h / 48.0);
            if (sy >= h) {
               sy = h - 1;
            }

            for (int gx = 0; gx < 32; gx++) {
               int sx = (int)((gx + 0.5) * w / 32.0);
               if (sx >= w) {
                  sx = w - 1;
               }

               out[gy * 32 + gx] = src[sy * w + sx];
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private static float[] center(float[] v) {
      double mean = 0.0;

      for (float f : v) {
         mean += f;
      }

      mean /= v.length;
      float[] out = new float[v.length];

      for (int i = 0; i < v.length; i++) {
         out[i] = (float)(v[i] - mean);
      }

      return out;
   }

   private static double l2(float[] v) {
      double s = 0.0;

      for (float f : v) {
         s += (double)f * f;
      }

      return Math.sqrt(s);
   }

   public CaptchaGlyphMatcher.Result match(boolean[] cellMask, int w, int h) {
      List<CaptchaGlyphMatcher.Result> ranked = this.matchTopK(cellMask, w, h, 1);
      return ranked.isEmpty() ? new CaptchaGlyphMatcher.Result('\u0000', 0.0) : ranked.get(0);
   }

   public List<CaptchaGlyphMatcher.Result> matchTopK(boolean[] cellMask, int w, int h, int k) {
      double cellAspect = h > 0 ? (double)w / h : 1.0;
      float[] cov = coverageFromBinary(cellMask, w, h);
      if (this.net != null) {
         float[] probs = this.net.probs(cov);
         if (probs != null) {
            List<CaptchaGlyphMatcher.Result> out = new ArrayList<>(this.charset.length());

            for (int i = 0; i < this.charset.length(); i++) {
               out.add(new CaptchaGlyphMatcher.Result(this.charset.charAt(i), probs[i]));
            }

            out.sort((a, b) -> Double.compare(b.score(), a.score()));
            return out.size() > k ? out.subList(0, k) : out;
         }
      }

      float[] cc = center(cov);
      double cellNorm = l2(cc);
      if (cellNorm < 1.0E-6) {
         return List.of();
      } else {
         boolean cellHole = this.heightAware && hasEnclosedHoleSealed(cellMask, w, h, Math.max(14, (int)(0.03 * w * h)));
         Map<Character, Double> bestPerChar = new HashMap<>();

         for (CaptchaGlyphMatcher.Template t : this.templates) {
            double dot = 0.0;

            for (int i = 0; i < cc.length; i++) {
               dot += (double)cc[i] * t.cc[i];
            }

            double corr = dot / (cellNorm * t.norm);
            double aspectSim = aspectSimilarity(cellAspect, t.aspect);
            double score = Math.max(0.0, corr) * (0.65 + 0.35 * aspectSim);
            if (this.heightAware) {
               score += t.hole == cellHole ? 0.12 : -0.12;
            }

            Double prev = bestPerChar.get(t.ch);
            if (prev == null || score > prev) {
               bestPerChar.put(t.ch, score);
            }
         }

         List<CaptchaGlyphMatcher.Result> out = new ArrayList<>();

         for (Entry<Character, Double> e : bestPerChar.entrySet()) {
            out.add(new CaptchaGlyphMatcher.Result(e.getKey(), e.getValue()));
         }

         out.sort((a, b) -> Double.compare(b.score(), a.score()));
         return out.size() > k ? out.subList(0, k) : out;
      }
   }

   static boolean hasEnclosedHole(boolean[] mask) {
      return hasEnclosedHole(mask, 32, 48, 22);
   }

   static boolean hasEnclosedHoleSealed(boolean[] mask, int w, int h, int minArea) {
      boolean[] sealed = CaptchaMapImage.dilate(mask, w, h);
      return hasEnclosedHole(sealed, w, h, minArea);
   }

   static boolean hasEnclosedHole(boolean[] mask, int w, int h, int minArea) {
      int n = w * h;
      boolean[] reached = new boolean[n];
      int[] stack = new int[n];
      int sp = 0;

      for (int x = 0; x < w; x++) {
         int bot = (h - 1) * w + x;
         if (!mask[x] && !reached[x]) {
            reached[x] = true;
            stack[sp++] = x;
         }

         if (!mask[bot] && !reached[bot]) {
            reached[bot] = true;
            stack[sp++] = bot;
         }
      }

      for (int y = 0; y < h; y++) {
         int left = y * w;
         int right = y * w + w - 1;
         if (!mask[left] && !reached[left]) {
            reached[left] = true;
            stack[sp++] = left;
         }

         if (!mask[right] && !reached[right]) {
            reached[right] = true;
            stack[sp++] = right;
         }
      }

      while (sp > 0) {
         int p = stack[--sp];
         int px = p % w;
         int py = p / w;
         if (px > 0) {
            sp = pushBg(mask, reached, stack, sp, p - 1);
         }

         if (px < w - 1) {
            sp = pushBg(mask, reached, stack, sp, p + 1);
         }

         if (py > 0) {
            sp = pushBg(mask, reached, stack, sp, p - w);
         }

         if (py < h - 1) {
            sp = pushBg(mask, reached, stack, sp, p + w);
         }
      }

      int enclosed = 0;

      for (int i = 0; i < n; i++) {
         if (!mask[i] && !reached[i]) {
            enclosed++;
         }
      }

      return enclosed >= minArea;
   }

   private static int pushBg(boolean[] mask, boolean[] reached, int[] stack, int sp, int idx) {
      if (!mask[idx] && !reached[idx]) {
         reached[idx] = true;
         stack[sp++] = idx;
      }

      return sp;
   }

   private static double aspectSimilarity(double a, double b) {
      return !(a <= 0.0) && !(b <= 0.0) ? Math.min(a, b) / Math.max(a, b) : 1.0;
   }

   private record Rendered(float[] cov, boolean[] gridMask, double aspect) {
   }

   public record Result(char ch, double score) {
   }

   private record Template(char ch, float[] cc, double norm, double aspect, boolean hole) {
   }
}
