package riptide.util.multi.captcha;

import java.awt.Font;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class CaptchaMapOcr {
   private static final String SONAR_ALPHABET = "abcdefhjkmnoprstuxyz";
   private static final String DIGITS = "0123456789";
   private static final int PER_CELL_TOPK = 3;
   private static volatile CaptchaGlyphMatcher digitMatcher;
   private static volatile CaptchaGlyphMatcher sonarMatcher;
   private static final Object INIT_LOCK = new Object();
   private static final double CUT_PENALTY_WEIGHT = 0.15;
   static final double WIN_FRAC = 0.82;
   static final double STEP_FRAC = 0.26;
   private static final int CTC_BLANK = "abcdefhjkmnoprstuxyz".length();
   private static final int CTC_BEAM = 24;
   private static final double CTC_EMIT = 0.55;
   private static final double MIN_CELL_WEIGHT = 0.4;

   private CaptchaMapOcr() {
   }

   public static CaptchaMapOcr.OcrResult detectAndSolve(CaptchaMapImage img) {
      List<CaptchaMapOcr.OcrResult> ranked = detectAndSolveRanked(img, 1);
      return ranked.isEmpty() ? new CaptchaMapOcr.OcrResult("", 0.0) : ranked.get(0);
   }

   private static CaptchaMapOcr.Prepared prepare(CaptchaMapImage img) {
      if (looksLikeDarkDigitCaptcha(img)) {
         boolean[] fg = new boolean[16384];

         for (int i = 0; i < fg.length; i++) {
            fg[i] = CaptchaMapImage.brightness(img.rgb[i]) > 170;
         }

         fg = CaptchaMapImage.medianDenoise(fg, 128, 128);
         return new CaptchaMapOcr.Prepared(fg, ensureDigitMatcher(), 3, 6);
      } else {
         boolean[] fg = extractVividForeground(img);
         fg = CaptchaMapImage.medianDenoise(fg, 128, 128);
         fg = removeThinStrokes(fg, 128, 128, 2, 14);
         fg = removeThinComponents(fg);
         fg = cropToTextBand(fg);
         return new CaptchaMapOcr.Prepared(fg, ensureSonarMatcher(), 3, 4);
      }
   }

   public static List<CaptchaMapOcr.OcrResult> detectAndSolveRanked(CaptchaMapImage img, int maxCandidates) {
      if (img == null) {
         return List.of();
      } else {
         CaptchaMapOcr.Prepared prepared = prepare(img);
         boolean[] fg = prepared.fg();
         CaptchaGlyphMatcher matcher = prepared.matcher();
         int minLen = prepared.minLen();
         int maxLen = prepared.maxLen();
         int[] box = inkBounds(fg);
         if (box == null) {
            return List.of();
         } else if (!looksLikeCaptcha(fg, box)) {
            return List.of();
         } else {
            if (matcher.hasNet()) {
               List<CaptchaMapOcr.OcrResult> ctc = ctcRead(fg, box, matcher, maxCandidates);
               if (!ctc.isEmpty()) {
                  return ctc;
               }
            }

            Map<String, Double> merged = new LinkedHashMap<>();

            for (int len = minLen; len <= maxLen; len++) {
               CaptchaMapOcr.Split s = splitByValleys(fg, box, len);
               if (s != null) {
                  addRanked(s.cells(), matcher, merged, -0.15 * s.cutPenalty());
               }
            }

            List<CaptchaMapOcr.OcrResult> out = new ArrayList<>();

            for (Entry<String, Double> e : merged.entrySet()) {
               out.add(new CaptchaMapOcr.OcrResult(e.getKey(), e.getValue()));
            }

            out.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
            return out.size() > maxCandidates ? out.subList(0, maxCandidates) : out;
         }
      }
   }

   private static boolean looksLikeCaptcha(boolean[] fg, int[] box) {
      int size = 128;
      int ink = 0;

      for (boolean b : fg) {
         if (b) {
            ink++;
         }
      }

      double frac = (double)ink / (size * size);
      int bw = box[2] - box[0] + 1;
      int bh = box[3] - box[1] + 1;
      if (!(frac < 0.008) && !(frac > 0.42)) {
         if (bh > size * 0.75) {
            return false;
         } else if (bw < size * 0.1) {
            return false;
         } else {
            int comps = CaptchaMapImage.connectedComponents(fg, size, size).size();
            return comps >= 1 && comps <= 60;
         }
      } else {
         return false;
      }
   }

   private static List<CaptchaMapOcr.OcrResult> ctcRead(boolean[] fg, int[] box, CaptchaGlyphMatcher matcher, int maxCandidates) {
      int h = box[3] - box[1] + 1;
      if (h < 8) {
         return List.of();
      } else {
         int winW = Math.max(6, (int)Math.round(0.82 * h));
         int step = Math.max(2, (int)Math.round(0.26 * h));
         List<float[]> post = new ArrayList<>();

         for (int wc = box[0]; wc <= box[2]; wc += step) {
            float[] cov = windowCoverage(fg, wc, winW, box);
            if (cov != null) {
               float[] p = matcher.classify(cov);
               if (p == null || p.length != CTC_BLANK + 1) {
                  return List.of();
               }

               post.add(p);
            }
         }

         return post.isEmpty() ? List.of() : ctcBeamSearch(post, maxCandidates);
      }
   }

   static float[] windowCoverage(boolean[] fg, int wc, int winW, int[] box) {
      int x0 = Math.max(box[0], wc - winW / 2);
      int x1 = Math.min(box[2], wc + winW / 2);
      if (x1 < x0) {
         return null;
      } else {
         int y0 = box[1];
         int y1 = box[3];
         int w = x1 - x0 + 1;
         int hh = y1 - y0 + 1;
         boolean[] cell = new boolean[w * hh];

         for (int y = 0; y < hh; y++) {
            for (int x = 0; x < w; x++) {
               cell[y * w + x] = fg[(y0 + y) * 128 + x0 + x];
            }
         }

         return CaptchaGlyphMatcher.coverageFromBinary(cell, w, hh);
      }
   }

   private static List<CaptchaMapOcr.OcrResult> ctcBeamSearch(List<float[]> post, int maxCandidates) {
      Map<String, double[]> beams = new HashMap<>();
      beams.put("", new double[]{1.0, 0.0});

      for (float[] p : post) {
         Map<String, double[]> next = new HashMap<>();

         for (Entry<String, double[]> e : beams.entrySet()) {
            String prefix = e.getKey();
            double pb = e.getValue()[0];
            double pnb = e.getValue()[1];
            double ptot = pb + pnb;
            if (!(ptot <= 1.0E-9)) {
               add(next, prefix, ptot * p[CTC_BLANK], 0.0);
               char last = prefix.isEmpty() ? 0 : prefix.charAt(prefix.length() - 1);

               for (int c = 0; c < CTC_BLANK; c++) {
                  double pc = p[c];
                  if (!(pc < 1.0E-4)) {
                     char ch = "abcdefhjkmnoprstuxyz".charAt(c);
                     if (ch == last) {
                        add(next, prefix, 0.0, pnb * pc);
                        add(next, prefix + ch, 0.0, pb * pc * 0.55);
                     } else {
                        add(next, prefix + ch, 0.0, ptot * pc * 0.55);
                     }
                  }
               }
            }
         }

         beams = prune(next, 24);
      }

      List<CaptchaMapOcr.OcrResult> ranked = new ArrayList<>();

      for (Entry<String, double[]> ex : beams.entrySet()) {
         String s = ex.getKey();
         if (!s.isEmpty()) {
            ranked.add(new CaptchaMapOcr.OcrResult(s, ex.getValue()[0] + ex.getValue()[1]));
         }
      }

      ranked.sort((a, b) -> {
         boolean la = a.text().length() >= 3 && a.text().length() <= 4;
         boolean lb = b.text().length() >= 3 && b.text().length() <= 4;
         if (la != lb) {
            return la ? -1 : 1;
         } else {
            return Double.compare(b.confidence(), a.confidence());
         }
      });
      return ranked.size() > maxCandidates ? ranked.subList(0, maxCandidates) : ranked;
   }

   private static void add(Map<String, double[]> m, String key, double addBlank, double addNonBlank) {
      double[] v = m.get(key);
      if (v == null) {
         v = new double[2];
         m.put(key, v);
      }

      v[0] += addBlank;
      v[1] += addNonBlank;
   }

   private static Map<String, double[]> prune(Map<String, double[]> beams, int width) {
      if (beams.size() <= width) {
         return beams;
      } else {
         List<Entry<String, double[]>> es = new ArrayList<>(beams.entrySet());
         es.sort((a, b) -> Double.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
         Map<String, double[]> out = new HashMap<>();

         for (int i = 0; i < width; i++) {
            out.put(es.get(i).getKey(), es.get(i).getValue());
         }

         return out;
      }
   }

   private static void addRanked(List<CaptchaMapOcr.Cell> cells, CaptchaGlyphMatcher matcher, Map<String, Double> into, double bonus) {
      if (cells != null && !cells.isEmpty()) {
         record Partial(String text, double sum, double min) {
         }

         List<Partial> beam = new ArrayList<>();
         beam.add(new Partial("", 0.0, Double.POSITIVE_INFINITY));

         for (CaptchaMapOcr.Cell cell : cells) {
            List<CaptchaGlyphMatcher.Result> topk = matcher.matchTopK(cell.mask, cell.w, cell.h, 3);
            if (topk.isEmpty()) {
               return;
            }

            List<Partial> next = new ArrayList<>();

            for (Partial p : beam) {
               for (CaptchaGlyphMatcher.Result r : topk) {
                  next.add(new Partial(p.text() + r.ch(), p.sum() + r.score(), Math.min(p.min(), r.score())));
               }
            }

            next.sort((a, b) -> Double.compare(b.sum(), a.sum()));
            beam = next.size() > 8 ? next.subList(0, 8) : next;
         }

         int n = cells.size();

         for (Partial p : beam) {
            double avg = p.sum() / n;
            double conf = Math.min(1.0, 0.6 * avg + 0.4 * p.min() + bonus);
            into.merge(p.text(), conf, Math::max);
         }
      }
   }

   private static CaptchaMapOcr.Split splitByValleys(boolean[] fg, int[] box, int len) {
      int x0 = box[0];
      int y0 = box[1];
      int x1 = box[2];
      int y1 = box[3];
      int totalW = x1 - x0 + 1;
      int h = y1 - y0 + 1;
      if (totalW >= len && h >= 6) {
         int[] colInk = new int[totalW];
         int maxCol = 1;

         for (int x = 0; x < totalW; x++) {
            int cnt = 0;

            for (int y = y0; y <= y1; y++) {
               if (fg[y * 128 + x0 + x]) {
                  cnt++;
               }
            }

            colInk[x] = cnt;
            if (cnt > maxCol) {
               maxCol = cnt;
            }
         }

         int[] cut = new int[len + 1];
         cut[0] = 0;
         cut[len] = totalW;
         int win = Math.max(2, totalW / (len * 3));
         double cutInk = 0.0;

         for (int i = 1; i < len; i++) {
            int ideal = (int)((long)i * totalW / len);
            int bestX = ideal;
            int bestInk = Integer.MAX_VALUE;

            for (int dx = -win; dx <= win; dx++) {
               int x = ideal + dx;
               if (x > cut[i - 1] + 1 && x < totalW - 1 && colInk[x] < bestInk) {
                  bestInk = colInk[x];
                  bestX = x;
               }
            }

            cut[i] = bestX;
            cutInk += (double)colInk[bestX] / maxCol;
         }

         double penalty = len > 1 ? cutInk / (len - 1) : 0.0;
         List<CaptchaMapOcr.Cell> cells = new ArrayList<>(len);

         for (int c = 0; c < len; c++) {
            int cx0 = x0 + cut[c];
            int cx1 = x0 + cut[c + 1] - 1;
            if (cx1 < cx0) {
               cx1 = cx0;
            }

            int[] cellBox = columnInkBounds(fg, cx0, cx1, y0, y1);
            if (cellBox == null) {
               return null;
            }

            int cw = cellBox[2] - cellBox[0] + 1;
            int ch = cellBox[3] - cellBox[1] + 1;
            boolean[] cell = new boolean[cw * ch];

            for (int yx = 0; yx < ch; yx++) {
               for (int x = 0; x < cw; x++) {
                  cell[yx * cw + x] = fg[(cellBox[1] + yx) * 128 + cellBox[0] + x];
               }
            }

            cells.add(new CaptchaMapOcr.Cell(cell, cw, ch));
         }

         return new CaptchaMapOcr.Split(cells, penalty);
      } else {
         return null;
      }
   }

   private static boolean looksLikeDarkDigitCaptcha(CaptchaMapImage img) {
      int dark = 0;
      int brightWhite = 0;
      int brightColor = 0;

      for (int c : img.rgb) {
         int b = CaptchaMapImage.brightness(c);
         if (b < 40) {
            dark++;
         } else if (b > 120) {
            if (CaptchaMapImage.saturation(c) < 45) {
               brightWhite++;
            } else {
               brightColor++;
            }
         }
      }

      return dark > img.rgb.length * 55 / 100 && brightWhite >= brightColor;
   }

   private static boolean[] extractVividForeground(CaptchaMapImage img) {
      int[] value = new int[img.rgb.length];
      int[] hist = new int[256];

      for (int i = 0; i < img.rgb.length; i++) {
         int c = img.rgb[i];
         int v = Math.max(c >> 16 & 0xFF, Math.max(c >> 8 & 0xFF, c & 0xFF));
         value[i] = v;
         hist[v]++;
      }

      int threshold = Math.max(otsuThreshold(hist, img.rgb.length) + 8, 110);
      boolean[] fg = new boolean[img.rgb.length];

      for (int i = 0; i < fg.length; i++) {
         fg[i] = value[i] > threshold;
      }

      return fg;
   }

   private static boolean[] cropToTextBand(boolean[] fg) {
      List<CaptchaMapImage.Blob> blobs = CaptchaMapImage.connectedComponents(fg, 128, 128);
      if (blobs.isEmpty()) {
         return fg;
      } else {
         int maxArea = 0;

         for (CaptchaMapImage.Blob bl : blobs) {
            maxArea = Math.max(maxArea, bl.area);
         }

         int glyphArea = (int)(maxArea * 0.3);
         int bandTop = Integer.MAX_VALUE;
         int bandBottom = -1;

         for (CaptchaMapImage.Blob bl : blobs) {
            if (bl.area >= glyphArea) {
               bandTop = Math.min(bandTop, bl.minY);
               bandBottom = Math.max(bandBottom, bl.maxY);
            }
         }

         if (bandBottom < 0) {
            return fg;
         } else {
            boolean[] out = new boolean[fg.length];

            for (CaptchaMapImage.Blob blx : blobs) {
               boolean glyph = blx.area >= glyphArea;
               int overlap = Math.min(blx.maxY, bandBottom) - Math.max(blx.minY, bandTop) + 1;
               double frac = overlap <= 0 ? 0.0 : (double)overlap / blx.height();
               if (glyph || !(frac < 0.5)) {
                  int bw = blx.width();

                  for (int y = 0; y < blx.height(); y++) {
                     for (int x = 0; x < bw; x++) {
                        if (blx.localMask[y * bw + x]) {
                           out[(blx.minY + y) * 128 + blx.minX + x] = true;
                        }
                     }
                  }
               }
            }

            return out;
         }
      }
   }

   private static boolean[] removeThinStrokes(boolean[] fg, int w, int h, int maxThin, int minLong) {
      boolean[] out = new boolean[fg.length];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            if (fg[y * w + x]) {
               int hr = run(fg, w, h, x, y, 1, 0) + run(fg, w, h, x, y, -1, 0) - 1;
               int vr = run(fg, w, h, x, y, 0, 1) + run(fg, w, h, x, y, 0, -1) - 1;
               int d1 = run(fg, w, h, x, y, 1, 1) + run(fg, w, h, x, y, -1, -1) - 1;
               int d2 = run(fg, w, h, x, y, 1, -1) + run(fg, w, h, x, y, -1, 1) - 1;
               int min = Math.min(Math.min(hr, vr), Math.min(d1, d2));
               out[y * w + x] = min > maxThin;
            }
         }
      }

      return out;
   }

   private static int run(boolean[] fg, int w, int h, int x, int y, int dx, int dy) {
      int n = 0;

      while (x >= 0 && y >= 0 && x < w && y < h && fg[y * w + x]) {
         n++;
         x += dx;
         y += dy;
      }

      return n;
   }

   private static int otsuThreshold(int[] hist, int total) {
      double sum = 0.0;

      for (int t = 0; t < 256; t++) {
         sum += (double)t * hist[t];
      }

      double sumB = 0.0;
      long wB = 0L;
      double maxVar = -1.0;
      int best = 127;

      for (int t = 0; t < 256; t++) {
         wB += hist[t];
         if (wB != 0L) {
            long wF = total - wB;
            if (wF == 0L) {
               break;
            }

            sumB += (double)t * hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double)wB * wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
               maxVar = between;
               best = t;
            }
         }
      }

      return best;
   }

   private static boolean[] removeThinComponents(boolean[] fg) {
      List<CaptchaMapImage.Blob> blobs = CaptchaMapImage.connectedComponents(fg, 128, 128);
      boolean[] out = new boolean[fg.length];

      for (CaptchaMapImage.Blob bl : blobs) {
         int bw = bl.width();
         int bh = bl.height();
         double fill = (double)bl.area / (bw * bh);
         boolean junk = bl.area < 40 || bh < 12 || fill < 0.18 && Math.max(bw, bh) > 24;
         if (!junk) {
            for (int y = 0; y < bh; y++) {
               for (int x = 0; x < bw; x++) {
                  if (bl.localMask[y * bw + x]) {
                     out[(bl.minY + y) * 128 + bl.minX + x] = true;
                  }
               }
            }
         }
      }

      return out;
   }

   private static int[] inkBounds(boolean[] fg) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = -1;
      int maxY = -1;

      for (int y = 0; y < 128; y++) {
         for (int x = 0; x < 128; x++) {
            if (fg[y * 128 + x]) {
               if (x < minX) {
                  minX = x;
               }

               if (x > maxX) {
                  maxX = x;
               }

               if (y < minY) {
                  minY = y;
               }

               if (y > maxY) {
                  maxY = y;
               }
            }
         }
      }

      return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
   }

   private static int[] columnInkBounds(boolean[] fg, int x0, int x1, int y0, int y1) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = -1;
      int maxY = -1;

      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            if (fg[y * 128 + x]) {
               if (x < minX) {
                  minX = x;
               }

               if (x > maxX) {
                  maxX = x;
               }

               if (y < minY) {
                  minY = y;
               }

               if (y > maxY) {
                  maxY = y;
               }
            }
         }
      }

      return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
   }

   private static CaptchaGlyphMatcher ensureDigitMatcher() {
      CaptchaGlyphMatcher m = digitMatcher;
      if (m != null) {
         return m;
      } else {
         synchronized (INIT_LOCK) {
            if (digitMatcher == null) {
               digitMatcher = new CaptchaGlyphMatcher(new Font("Monospaced", 1, 48), "0123456789", new double[]{0.0}, false);
            }

            return digitMatcher;
         }
      }
   }

   private static CaptchaGlyphMatcher ensureSonarMatcher() {
      CaptchaGlyphMatcher m = sonarMatcher;
      if (m != null) {
         return m;
      } else {
         synchronized (INIT_LOCK) {
            if (sonarMatcher == null) {
               Font font = loadFont("Kingthings_Trypewriter_2.ttf", 48.0F);
               if (font == null) {
                  font = new Font("Serif", 0, 48);
               }

               CaptchaGlyphMatcher built = new CaptchaGlyphMatcher(font, "abcdefhjkmnoprstuxyz", new double[]{-0.17, -0.08, 0.0, 0.08, 0.17}, true);
               built.setNet(CaptchaNet.loadBundled());
               sonarMatcher = built;
            }

            return sonarMatcher;
         }
      }
   }

   private static Font loadFont(String name, float size) {
      try {
         Font var3;
         try (InputStream is = CaptchaMapOcr.class.getResourceAsStream("/assets/riptide/captcha/fonts/" + name)) {
            if (is == null) {
               return null;
            }

            var3 = Font.createFont(0, is).deriveFont(0, size);
         }

         return var3;
      } catch (Throwable var7) {
         return null;
      }
   }

   private record Cell(boolean[] mask, int w, int h) {
   }

   public record OcrResult(String text, double confidence) {
      public boolean ok() {
         return this.text != null && !this.text.isEmpty();
      }
   }

   private record Prepared(boolean[] fg, CaptchaGlyphMatcher matcher, int minLen, int maxLen) {
   }

   private record Split(List<CaptchaMapOcr.Cell> cells, double cutPenalty) {
   }
}
