package riptide.util.multi.captcha;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class CaptchaNet {
   private static final int MAGIC_MLP = 1128746545;
   private static final int MAGIC_CNN = 1128746546;
   static final int GRID_W = 32;
   static final int GRID_H = 48;
   private final boolean cnn;
   private final int outputs;
   private int dim;
   private int hid;
   private float[] w1;
   private float[] b1;
   private float[] w2;
   private float[] b2;
   private static final int C1 = 24;
   private static final int C2 = 48;
   private static final int K = 3;
   private static final int FC1 = 96;
   private static final int PH = 12;
   private static final int PW = 8;
   private float[] c1w;
   private float[] c1b;
   private float[] c2w;
   private float[] c2b;
   private float[] f1w;
   private float[] f1b;
   private float[] f2w;
   private float[] f2b;

   private CaptchaNet(boolean cnn, int outputs) {
      this.cnn = cnn;
      this.outputs = outputs;
   }

   public int outputs() {
      return this.outputs;
   }

   public int inputDim() {
      return 1536;
   }

   public static CaptchaNet loadBundled() {
      try {
         Object var3;
         try (InputStream is = CaptchaNet.class.getResourceAsStream("/assets/riptide/captcha/glyphnet.bin")) {
            if (is == null) {
               return null;
            }

            DataInputStream in = new DataInputStream(is);
            int magic = in.readInt();
            if (magic == 1128746546) {
               return loadCnn(in);
            }

            if (magic == 1128746545) {
               return loadMlp(in);
            }

            var3 = null;
         }

         return (CaptchaNet)var3;
      } catch (Throwable var6) {
         return null;
      }
   }

   private static CaptchaNet loadCnn(DataInputStream in) throws IOException {
      int classes = in.readInt();
      if (classes > 0 && classes <= 256) {
         CaptchaNet n = new CaptchaNet(true, classes);
         n.c1w = read(in, 216);
         n.c1b = read(in, 24);
         n.c2w = read(in, 10368);
         n.c2b = read(in, 48);
         n.f1w = read(in, 442368);
         n.f1b = read(in, 96);
         n.f2w = read(in, classes * 96);
         n.f2b = read(in, classes);
         return n;
      } else {
         return null;
      }
   }

   private static CaptchaNet loadMlp(DataInputStream in) throws IOException {
      int dim = in.readInt();
      int hid = in.readInt();
      int out = in.readInt();
      if (dim > 0 && hid > 0 && out > 0 && dim <= 1048576 && hid <= 65536 && out <= 256) {
         CaptchaNet n = new CaptchaNet(false, out);
         n.dim = dim;
         n.hid = hid;
         n.w1 = read(in, dim * hid);
         n.b1 = read(in, hid);
         n.w2 = read(in, hid * out);
         n.b2 = read(in, out);
         return n;
      } else {
         return null;
      }
   }

   private static float[] read(DataInputStream in, int n) throws IOException {
      float[] a = new float[n];

      for (int i = 0; i < n; i++) {
         a[i] = in.readFloat();
      }

      return a;
   }

   public float[] probs(float[] x) {
      if (x != null && x.length == 1536) {
         return this.cnn ? this.forwardCnn(x) : this.forwardMlp(x);
      } else {
         return null;
      }
   }

   private float[] forwardCnn(float[] x) {
      float[] a1 = new float[36864];
      conv(x, 1, 48, 32, this.c1w, this.c1b, 24, a1);
      relu(a1);
      int h1 = 24;
      int w1c = 16;
      float[] p1 = new float[24 * h1 * w1c];
      pool(a1, 24, 48, 32, p1);
      float[] a2 = new float[48 * h1 * w1c];
      conv(p1, 24, h1, w1c, this.c2w, this.c2b, 48, a2);
      relu(a2);
      float[] p2 = new float[4608];
      pool(a2, 48, h1, w1c, p2);
      float[] h = new float[96];

      for (int o = 0; o < 96; o++) {
         float s = this.f1b[o];
         int base = o * p2.length;

         for (int i = 0; i < p2.length; i++) {
            s += this.f1w[base + i] * p2[i];
         }

         h[o] = s > 0.0F ? s : 0.0F;
      }

      float[] logit = new float[this.outputs];

      for (int o = 0; o < this.outputs; o++) {
         float s = this.f2b[o];
         int base = o * 96;

         for (int j = 0; j < 96; j++) {
            s += this.f2w[base + j] * h[j];
         }

         logit[o] = s;
      }

      return softmax(logit);
   }

   private static void conv(float[] in, int inC, int H, int W, float[] w, float[] b, int outC, float[] out) {
      for (int oc = 0; oc < outC; oc++) {
         int obase = oc * H * W;
         float bias = b[oc];

         for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
               float s = bias;

               for (int ic = 0; ic < inC; ic++) {
                  int ibase = ic * H * W;
                  int wbase = (oc * inC + ic) * 3 * 3;

                  for (int ky = 0; ky < 3; ky++) {
                     int iy = y + ky - 1;
                     if (iy >= 0 && iy < H) {
                        for (int kx = 0; kx < 3; kx++) {
                           int ix = x + kx - 1;
                           if (ix >= 0 && ix < W) {
                              s += in[ibase + iy * W + ix] * w[wbase + ky * 3 + kx];
                           }
                        }
                     }
                  }
               }

               out[obase + y * W + x] = s;
            }
         }
      }
   }

   private static void pool(float[] in, int C, int H, int W, float[] out) {
      int oh = H / 2;
      int ow = W / 2;

      for (int c = 0; c < C; c++) {
         int ibase = c * H * W;
         int obase = c * oh * ow;

         for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
               int iy = y * 2;
               int ix = x * 2;
               float m = in[ibase + iy * W + ix];
               m = Math.max(m, in[ibase + iy * W + ix + 1]);
               m = Math.max(m, in[ibase + (iy + 1) * W + ix]);
               m = Math.max(m, in[ibase + (iy + 1) * W + ix + 1]);
               out[obase + y * ow + x] = m;
            }
         }
      }
   }

   private static void relu(float[] a) {
      for (int i = 0; i < a.length; i++) {
         if (a[i] < 0.0F) {
            a[i] = 0.0F;
         }
      }
   }

   private float[] forwardMlp(float[] x) {
      float[] h = new float[this.hid];

      for (int j = 0; j < this.hid; j++) {
         h[j] = this.b1[j];
      }

      for (int i = 0; i < this.dim; i++) {
         float xi = x[i];
         if (xi != 0.0F) {
            int base = i * this.hid;

            for (int j = 0; j < this.hid; j++) {
               h[j] += xi * this.w1[base + j];
            }
         }
      }

      for (int j = 0; j < this.hid; j++) {
         if (h[j] < 0.0F) {
            h[j] = 0.0F;
         }
      }

      float[] logit = new float[this.outputs];

      for (int o = 0; o < this.outputs; o++) {
         logit[o] = this.b2[o];
      }

      for (int jx = 0; jx < this.hid; jx++) {
         float hj = h[jx];
         if (hj != 0.0F) {
            int base = jx * this.outputs;

            for (int o = 0; o < this.outputs; o++) {
               logit[o] += hj * this.w2[base + o];
            }
         }
      }

      return softmax(logit);
   }

   private static float[] softmax(float[] logit) {
      float max = Float.NEGATIVE_INFINITY;

      for (float l : logit) {
         if (l > max) {
            max = l;
         }
      }

      float sum = 0.0F;

      for (int o = 0; o < logit.length; o++) {
         logit[o] = (float)Math.exp(logit[o] - max);
         sum += logit[o];
      }

      if (sum <= 0.0F) {
         return null;
      } else {
         for (int o = 0; o < logit.length; o++) {
            logit[o] /= sum;
         }

         return logit;
      }
   }
}
