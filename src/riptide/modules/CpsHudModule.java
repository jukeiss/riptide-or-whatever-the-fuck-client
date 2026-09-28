package riptide.modules;

import java.util.ArrayDeque;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class CpsHudModule extends Module {
   private static CpsHudModule cached;
   private final ArrayDeque<Long> left = new ArrayDeque<>();
   private final ArrayDeque<Long> right = new ArrayDeque<>();
   private boolean leftWas;
   private boolean rightWas;

   public CpsHudModule() {
      super("cps-hud", "CPS", ModuleCategory.RENDER, "Shows your left/right clicks per second.");
      this.add(
         new ChoiceSetting("corner", "Corner", "Bottom Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the counter sits in.")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 6, 0, 300, 1).description("Gap from the screen edge, in pixels.").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-text", "Text", -1).group("Colors").build());
   }

   private static CpsHudModule instance() {
      CpsHudModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("cps-hud") instanceof CpsHudModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   public static void render(GuiGraphicsExtractor var0) {
      CpsHudModule var1 = instance();
      if (var1 != null && var1.isEnabled() && !PackHideState.isActive()) {
         if (MC != null && MC.getWindow() != null && !MC.gui.hud.isHidden()) {
            try {
               var1.sample();
               var1.draw(var0);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void sample() {
      long var1 = MC.getWindow().handle();
      long var3 = System.currentTimeMillis();
      boolean var5 = GLFW.glfwGetMouseButton(var1, 0) == 1;
      boolean var6 = GLFW.glfwGetMouseButton(var1, 1) == 1;
      if (var5 && !this.leftWas) {
         this.left.addLast(var3);
      }

      if (var6 && !this.rightWas) {
         this.right.addLast(var3);
      }

      this.leftWas = var5;
      this.rightWas = var6;
      prune(this.left, var3);
      prune(this.right, var3);
   }

   private static void prune(ArrayDeque<Long> var0, long var1) {
      while (!var0.isEmpty() && var1 - var0.peekFirst() > 1000L) {
         var0.pollFirst();
      }
   }

   private void draw(GuiGraphicsExtractor var1) {
      Font var2 = MC.font;
      String var3 = "CPS " + this.left.size() + " | " + this.right.size();
      byte var4 = 3;
      int var5 = var2.width(var3) + var4 * 2;
      int var6 = 9 + var4 * 2;
      boolean var7 = this.choice("corner").contains("Right");
      boolean var8 = this.choice("corner").contains("Bottom");
      int var9 = this.integer("margin");
      int var10 = var7 ? var1.guiWidth() - var9 - var5 : var9;
      int var11 = var8 ? var1.guiHeight() - var9 - var6 : var9;
      int var12 = ModuleRenderUtil.color(this, "c-bg", -1879048192);
      int var13 = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
      if (var12 >>> 24 != 0) {
         var1.fill(var10, var11, var10 + var5, var11 + var6, var12);
      }

      var1.text(var2, Component.literal(var3), var10 + var4, var11 + var4, var13);
   }
}
