package riptide.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class RiptideBindUtil {
   private static final int MOUSE_BIND_BASE = -1000;

   private RiptideBindUtil() {
   }

   public static boolean isMouseBind(int bindCode) {
      return bindCode <= -1000;
   }

   public static int encodeMouseButton(int button) {
      return -1000 - button;
   }

   public static int decodeMouseButton(int bindCode) {
      return -1000 - bindCode;
   }

   public static boolean isAllowedMouseButton(int button) {
      return button > 0 && button <= 7;
   }

   public static boolean isBindPressed(Minecraft client, int bindCode) {
      if (bindCode != -1 && client != null && client.getWindow() != null) {
         long handle = client.getWindow().handle();
         if (!isMouseBind(bindCode)) {
            return GLFW.glfwGetKey(handle, bindCode) == 1;
         } else {
            int button = decodeMouseButton(bindCode);
            return isAllowedMouseButton(button) && GLFW.glfwGetMouseButton(handle, button) == 1;
         }
      } else {
         return false;
      }
   }

   public static String getBindName(int bindCode) {
      if (bindCode == -1) {
         return "None";
      } else if (isMouseBind(bindCode)) {
         return getMouseButtonName(decodeMouseButton(bindCode));
      } else {
         String name = GLFW.glfwGetKeyName(bindCode, 0);
         if (name != null) {
            return name.toUpperCase();
         } else {
            return switch (bindCode) {
               case 32 -> "Space";
               case 257 -> "Enter";
               case 258 -> "Tab";
               case 259 -> "Backsp";
               case 260 -> "Insert";
               case 261 -> "Delete";
               case 262 -> "Right";
               case 263 -> "Left";
               case 264 -> "Down";
               case 265 -> "Up";
               case 266 -> "PgUp";
               case 267 -> "PgDn";
               case 268 -> "Home";
               case 269 -> "End";
               case 280 -> "CapsLk";
               case 281 -> "ScrLk";
               case 282 -> "NumLk";
               case 283 -> "PrtSc";
               case 284 -> "Pause";
               case 290 -> "F1";
               case 291 -> "F2";
               case 292 -> "F3";
               case 293 -> "F4";
               case 294 -> "F5";
               case 295 -> "F6";
               case 296 -> "F7";
               case 297 -> "F8";
               case 298 -> "F9";
               case 299 -> "F10";
               case 300 -> "F11";
               case 301 -> "F12";
               case 335 -> "Num Enter";
               case 340 -> "L.Shift";
               case 341 -> "L.Ctrl";
               case 342 -> "L.Alt";
               case 344 -> "R.Shift";
               case 345 -> "R.Ctrl";
               case 346 -> "R.Alt";
               default -> "Key " + bindCode;
            };
         }
      }
   }

   public static String getMouseButtonName(int button) {
      return switch (button) {
         case 0 -> "LMB";
         case 1 -> "RMB";
         case 2 -> "MMB";
         default -> "M" + (button + 1);
      };
   }
}
