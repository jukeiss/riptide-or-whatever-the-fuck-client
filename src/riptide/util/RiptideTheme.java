package riptide.util;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import riptide.gui.vanillaui.UiContexts;

public final class RiptideTheme {
   public static final int[] DEFAULTS = new int[]{-50373, -5035221, -791321, -50373, -5093304, -13248397, -1938838, -7397596, -50373, -39836};
   private static final float NEUTRAL_THRESHOLD = 0.1F;
   private static final float RED_BAND = 0.092F;
   private static final float FH = 0.027777778F;
   private static final float PURPLE_BAND_MIN = 0.5416667F;
   private static final float PURPLE_BAND_MAX = 0.875F;
   private static volatile RiptideTheme.State active;
   private static final Map<Long, Integer> CACHE = new HashMap<>(512);
   private static volatile int generation;

   private RiptideTheme() {
   }

   private static float hueDistance(float var0, float var1) {
      float var2 = Math.abs(var0 - var1);
      return Math.min(var2, 1.0F - var2);
   }

   private static float smoothstep(float var0, float var1, float var2) {
      float var3 = clamp01((var2 - var0) / (var1 - var0));
      return var3 * var3 * (3.0F - 2.0F * var3);
   }

   private static float shortestArc(float var0, float var1) {
      return (var1 - var0 + 1.5F) % 1.0F - 0.5F;
   }

   private static float wrapHue(float var0) {
      return var0 - (float)Math.floor(var0);
   }

   public static RiptideTheme.State active() {
      RiptideTheme.State var0 = active;
      if (var0 == null) {
         var0 = new RiptideTheme.State(RiptideLiteVariant.enabled() ? new RiptideConfig.ThemeColors() : RiptideConfig.getGlobal().themeColors);
         active = var0;
      }

      return var0;
   }

   public static boolean isCustomized() {
      return active().anyActive;
   }

   public static int generation() {
      return generation;
   }

   public static void reload() {
      if (!RiptideLiteVariant.enabled()) {
         active = new RiptideTheme.State(RiptideConfig.getGlobal().themeColors);
         generation++;
         synchronized (CACHE) {
            CACHE.clear();
         }

         try {
            UiContexts.refreshTheme();
         } catch (Throwable var3) {
         }

         try {
            RiptideThemeTextures.invalidate();
         } catch (Throwable var2) {
         }
      }
   }

   public static int recolor(int var0) {
      return recolor(var0, RiptideTheme.Channel.ACCENT);
   }

   public static int recolor(int var0, RiptideTheme.Channel var1) {
      RiptideTheme.State var2 = active();
      if (var2.anyActive && var2.activeChannel[var1.ordinal()]) {
         long var3 = (long)var1.ordinal() << 56 | var0 & 4294967295L;
         synchronized (CACHE) {
            Integer var6 = CACHE.get(var3);
            if (var6 != null) {
               return var6;
            } else {
               int var7 = recolor(var0, var1, var2);
               CACHE.put(var3, var7);
               return var7;
            }
         }
      } else {
         return var0;
      }
   }

   public static int recolor(int var0, RiptideTheme.Channel var1, RiptideTheme.State var2) {
      int var3 = var1.ordinal();
      if (var2 != null && var2.activeChannel[var3]) {
         int var4 = Math.round((var0 >>> 24 & 0xFF) * var2.alpha[var3]);
         int var5 = var0 >>> 16 & 0xFF;
         int var6 = var0 >>> 8 & 0xFF;
         int var7 = var0 & 0xFF;
         float[] var8 = Color.RGBtoHSB(var5, var6, var7, null);
         float var9 = var8[1];
         float var10 = var8[2];
         float var11 = var2.hue[var3];
         float var12 = var2.sat[var3];
         float var13 = var2.val[var3];
         if (var1 == RiptideTheme.Channel.TEXT) {
            float var16 = Math.min(0.4F, var9 + var12 * 0.3F);
            return var4 << 24 | Color.HSBtoRGB(var11, var16, var10) & 16777215;
         } else if (var9 < 0.1F) {
            return var4 << 24 | var0 & 16777215;
         } else {
            float var14 = var9 * var12;
            float var15 = clamp01(var10 + (var13 - var10) * (1.0F - var12) * 0.7F);
            return var4 << 24 | Color.HSBtoRGB(var11, var14, var15) & 16777215;
         }
      } else {
         return var0;
      }
   }

   private static float clamp01(float var0) {
      return Math.max(0.0F, Math.min(1.0F, var0));
   }

   public static int recolorImagePixel(int var0, RiptideTheme.Channel var1, RiptideTheme.State var2) {
      int var3 = var1.ordinal();
      if (var2 != null && var2.activeChannel[var3]) {
         int var4 = var0 >>> 24 & 0xFF;
         if (var4 == 0) {
            return var0;
         } else {
            int var5 = var0 >>> 16 & 0xFF;
            int var6 = var0 >>> 8 & 0xFF;
            int var7 = var0 & 0xFF;
            float[] var8 = Color.RGBtoHSB(var5, var6, var7, null);
            float var9 = var8[0];
            float var10 = var8[1];
            float var11 = var8[2];
            float var12 = satWeight(var10) * bandWeight(var9, var1);
            if (var12 <= 0.0F) {
               return var0;
            } else {
               float var13 = var10 * var2.sat[var3];
               float var14 = wrapHue(var9 + shortestArc(var9, var2.hue[var3]) * var12);
               float var15 = var10 + (var13 - var10) * var12;
               return var4 << 24 | Color.HSBtoRGB(var14, var15, var11) & 16777215;
            }
         }
      } else {
         return var0;
      }
   }

   private static float satWeight(float var0) {
      return smoothstep(0.12F, 0.24F, var0);
   }

   private static float bandWeight(float var0, RiptideTheme.Channel var1) {
      return var1 == RiptideTheme.Channel.BACKDROP ? purpleBandWeight(var0) : 1.0F - smoothstep(0.092F, 0.11977778F, hueDistance(var0, 0.0F));
   }

   private static float purpleBandWeight(float var0) {
      return smoothstep(0.5138889F, 0.5416667F, var0) * (1.0F - smoothstep(0.875F, 0.9027778F, var0));
   }

   public static int recolorImagePixelTo(int var0, float var1, float var2) {
      int var3 = var0 >>> 24 & 0xFF;
      if (var3 == 0) {
         return var0;
      } else {
         int var4 = var0 >>> 16 & 0xFF;
         int var5 = var0 >>> 8 & 0xFF;
         int var6 = var0 & 0xFF;
         float[] var7 = Color.RGBtoHSB(var4, var5, var6, null);
         float var8 = var7[0];
         float var9 = var7[1];
         float var10 = var7[2];
         float var11 = satWeight(var9) * purpleBandWeight(var8);
         if (var11 <= 0.0F) {
            return var0;
         } else {
            float var12 = clamp01(var2 * (0.65F + 0.35F * var9));
            float var13 = wrapHue(var8 + shortestArc(var8, var1) * var11);
            float var14 = var9 + (var12 - var9) * var11;
            return var3 << 24 | Color.HSBtoRGB(var13, var14, var10) & 16777215;
         }
      }
   }

   public static enum Channel {
      ACCENT,
      OUTLINE,
      TEXT,
      TOGGLE,
      BACKDROP,
      SUCCESS,
      DANGER,
      BUTTON,
      HEADER,
      HOVER;
   }

   public static final class State {
      public final boolean advanced;
      public final boolean anyActive;
      final boolean[] activeChannel = new boolean[RiptideTheme.Channel.values().length];
      final float[] hue = new float[RiptideTheme.Channel.values().length];
      final float[] sat = new float[RiptideTheme.Channel.values().length];
      final float[] val = new float[RiptideTheme.Channel.values().length];
      final float[] alpha = new float[RiptideTheme.Channel.values().length];

      private State(RiptideConfig.ThemeColors var1) {
         this.advanced = var1.advanced;
         int[] var2 = new int[RiptideTheme.Channel.values().length];
         if (var1.advanced) {
            var2[RiptideTheme.Channel.ACCENT.ordinal()] = var1.accent;
            var2[RiptideTheme.Channel.OUTLINE.ordinal()] = var1.outline;
            var2[RiptideTheme.Channel.TEXT.ordinal()] = var1.text;
            var2[RiptideTheme.Channel.TOGGLE.ordinal()] = var1.toggle;
            var2[RiptideTheme.Channel.BACKDROP.ordinal()] = var1.backdrop;
            var2[RiptideTheme.Channel.SUCCESS.ordinal()] = var1.success;
            var2[RiptideTheme.Channel.DANGER.ordinal()] = var1.danger;
            var2[RiptideTheme.Channel.BUTTON.ordinal()] = var1.button;
            var2[RiptideTheme.Channel.HEADER.ordinal()] = var1.header;
            var2[RiptideTheme.Channel.HOVER.ordinal()] = var1.hover;
         } else {
            var2[RiptideTheme.Channel.ACCENT.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.OUTLINE.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.TOGGLE.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.BACKDROP.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.BUTTON.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.DANGER.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.HEADER.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.HOVER.ordinal()] = var1.master;
            var2[RiptideTheme.Channel.TEXT.ordinal()] = RiptideTheme.DEFAULTS[RiptideTheme.Channel.TEXT.ordinal()];
            var2[RiptideTheme.Channel.SUCCESS.ordinal()] = RiptideTheme.DEFAULTS[RiptideTheme.Channel.SUCCESS.ordinal()];
         }

         int var3 = RiptideTheme.DEFAULTS[RiptideTheme.Channel.ACCENT.ordinal()];
         boolean var4 = false;

         for (int var5 = 0; var5 < var2.length; var5++) {
            float[] var6 = Color.RGBtoHSB(var2[var5] >> 16 & 0xFF, var2[var5] >> 8 & 0xFF, var2[var5] & 0xFF, null);
            this.hue[var5] = var6[0];
            this.sat[var5] = var6[1];
            this.val[var5] = var6[2];
            this.alpha[var5] = (var2[var5] >>> 24 & 0xFF) / 255.0F;
            int var7 = !var1.advanced && var5 != RiptideTheme.Channel.TEXT.ordinal() && var5 != RiptideTheme.Channel.SUCCESS.ordinal()
               ? var3
               : RiptideTheme.DEFAULTS[var5];
            boolean var8 = (var2[var5] & 16777215) != (var7 & 16777215);
            boolean var9 = (var2[var5] >>> 24 & 0xFF) != (var7 >>> 24 & 0xFF);
            this.activeChannel[var5] = var8 || var9;
            var4 |= this.activeChannel[var5];
         }

         this.anyActive = var4;
      }

      public static RiptideTheme.State from(RiptideConfig.ThemeColors var0) {
         return new RiptideTheme.State(var0);
      }

      public boolean isActive(RiptideTheme.Channel var1) {
         return this.activeChannel[var1.ordinal()];
      }

      public float hueOf(RiptideTheme.Channel var1) {
         return this.hue[var1.ordinal()];
      }

      public float satOf(RiptideTheme.Channel var1) {
         return this.sat[var1.ordinal()];
      }

      public int colorOf(RiptideTheme.Channel var1) {
         int var2 = var1.ordinal();
         int var3 = Color.HSBtoRGB(this.hue[var2], this.sat[var2], this.val[var2]) & 16777215;
         return Math.round(this.alpha[var2] * 255.0F) << 24 | var3;
      }

      public int previewSignature(RiptideTheme.Channel var1) {
         int var2 = var1.ordinal();
         if (!this.activeChannel[var2]) {
            return 0;
         } else {
            int var3 = Float.floatToIntBits(this.hue[var2]);
            var3 = var3 * 31 + Float.floatToIntBits(this.sat[var2]);
            var3 = var3 * 31 + Float.floatToIntBits(this.val[var2]);
            var3 = var3 * 31 + Float.floatToIntBits(this.alpha[var2]);
            return var3 == 0 ? 1 : var3;
         }
      }
   }
}
