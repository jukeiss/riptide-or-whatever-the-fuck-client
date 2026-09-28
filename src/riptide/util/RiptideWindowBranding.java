package riptide.util;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import riptide.modules.PackHideState;

public final class RiptideWindowBranding {
   public static final String WINDOW_TITLE = "Riptide Client";
   private static final String ICON_ROOT = "assets/riptide/icons/window/";
   private static final int[] ICON_SIZES = new int[]{16, 32, 48, 128, 256};
   private static final int MAX_RETRY_TICKS = 60;
   private static boolean applied = false;
   private static int pendingTicks;

   private RiptideWindowBranding() {
   }

   public static void apply(Minecraft client) {
      if (!applied && client != null && client.getWindow() != null) {
         applied = true;
         refresh(client);
      }
   }

   public static void refresh(Minecraft client) {
      pendingTicks = 60;
      applyPending(client);
   }

   public static void tick(Minecraft client) {
      if (pendingTicks > 0) {
         applyPending(client);
      }
   }

   private static void applyPending(Minecraft client) {
      if (pendingTicks > 0) {
         pendingTicks--;
         if (client != null && client.getWindow() != null) {
            boolean hidden = PackHideState.isActive() || RiptideLiteVariant.enabled();
            boolean ok;
            if (hidden) {
               client.updateTitle();
               ok = applyVanillaIcon(client);
            } else {
               client.getWindow().setTitle("Riptide Client");
               ok = applyIcon(client.getWindow().handle());
            }

            if (ok) {
               pendingTicks = 0;
            }
         }
      }
   }

   private static boolean applyVanillaIcon(Minecraft client) {
      try {
         IconSet iconSet = SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT;
         client.getWindow().setIcon(client.getVanillaPackResources(), iconSet);
         return true;
      } catch (Throwable var2) {
         return false;
      }
   }

   private static boolean applyIcon(long windowHandle) {
      if (windowHandle == 0L) {
         return false;
      } else if (MacosUtil.IS_MACOS) {
         try {
            MacosUtil.loadIcon(() -> openIconStream("mac_icon.png"));
            return true;
         } catch (Throwable var24) {
            return false;
         }
      } else {
         List<ByteBuffer> allocatedBuffers = new ArrayList<>(ICON_SIZES.length);

         boolean icons;
         try {
            MemoryStack stack = MemoryStack.stackPush();

            boolean var31;
            try {
               Buffer iconsx = GLFWImage.malloc(ICON_SIZES.length, stack);

               for (int i = 0; i < ICON_SIZES.length; i++) {
                  int size = ICON_SIZES[i];

                  try (InputStream stream = openIconStream("icon_" + size + "x" + size + ".png")) {
                     NativeImage image = NativeImage.read(stream);

                     try {
                        ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                        allocatedBuffers.add(pixels);
                        pixels.asIntBuffer().put(image.getPixelsABGR());
                        iconsx.position(i);
                        iconsx.width(image.getWidth());
                        iconsx.height(image.getHeight());
                        iconsx.pixels(pixels);
                     } catch (Throwable var25) {
                        if (image != null) {
                           try {
                              image.close();
                           } catch (Throwable var23) {
                              var25.addSuppressed(var23);
                           }
                        }

                        throw var25;
                     }

                     if (image != null) {
                        image.close();
                     }
                  }
               }

               GLFW.glfwSetWindowIcon(windowHandle, (Buffer)iconsx.position(0));
               var31 = true;
            } catch (Throwable var27) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var21) {
                     var27.addSuppressed(var21);
                  }
               }

               throw var27;
            }

            if (stack != null) {
               stack.close();
            }

            return var31;
         } catch (Throwable var28) {
            icons = false;
         } finally {
            allocatedBuffers.forEach(MemoryUtil::memFree);
         }

         return icons;
      }
   }

   private static InputStream openIconStream(String fileName) throws IOException {
      InputStream stream = RiptideWindowBranding.class.getClassLoader().getResourceAsStream("assets/riptide/icons/window/" + fileName);
      if (stream == null) {
         throw new IOException("Missing Riptide window icon resource: " + fileName);
      } else {
         return stream;
      }
   }
}
