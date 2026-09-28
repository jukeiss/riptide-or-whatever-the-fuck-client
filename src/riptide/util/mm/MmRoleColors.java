package riptide.util.mm;

public final class MmRoleColors {
   public static final int USER_COLOR = -13330213;
   public static final int BLUE_COLOR = -13330213;
   private static final int[] GOD_GRADIENT = new int[]{-41892, -65536};
   private static final int[] ADMIN_GRADIENT = new int[]{-11751984, -5047809};
   private static final int[] GOLD_GRADIENT = new int[]{-2849731, -8560};
   private static final int[] AQUA_GRADIENT = new int[]{-16718337, -12910848};
   private static final int[] NOTBROKE_GRADIENT = new int[]{-3597588, -15384386};

   private MmRoleColors() {
   }

   private static int[] gradient(String role) {
      String var1 = role == null ? "" : role;

      return switch (var1) {
         case "god" -> GOD_GRADIENT;
         case "admin" -> ADMIN_GRADIENT;
         case "gold" -> GOLD_GRADIENT;
         case "aqua" -> AQUA_GRADIENT;
         case "notbroke" -> NOTBROKE_GRADIENT;
         default -> null;
      };
   }

   public static boolean isGradient(String role) {
      return gradient(role) != null;
   }

   public static int roleColor(String role) {
      int[] g = gradient(role);
      if (g != null) {
         return g[0];
      } else {
         return "blue".equals(role) ? -13330213 : -13330213;
      }
   }

   public static int gradientNameColor(String role, int index, int length) {
      int[] g = gradient(role);
      if (g == null) {
         return roleColor(role);
      } else if (length <= 1) {
         return g[0];
      } else {
         float t = Math.max(0.0F, Math.min(1.0F, (float)index / (length - 1)));
         return lerpRgb(g[0], g[1], t);
      }
   }

   static int lerpRgb(int a, int b, float t) {
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      int r = Math.round(ar + (br - ar) * t);
      int g = Math.round(ag + (bg - ag) * t);
      int bl = Math.round(ab + (bb - ab) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }
}
