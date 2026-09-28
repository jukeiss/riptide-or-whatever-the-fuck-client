package riptide.modules;

import net.minecraft.client.Minecraft;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;

public final class ZoomModule extends Module {
   private static volatile boolean zooming;
   private static volatile double factor = 4.0;
   private double savedSensitivity = -1.0;
   private boolean keyWasDown;

   public ZoomModule() {
      super("zoom", "Zoom", ModuleCategory.RENDER, "Narrows your field of view while a key is held.");
      this.add(new KeybindSetting("bind", "Zoom Key", 67).description("Hold to zoom.").group("General").build());
      this.add(new IntSetting("level", "Amount", 4, 2, 15, 1).unit("x").description("How far in to zoom.").group("General").build());
      this.add(
         new BoolSetting("slow-mouse", "Slow The Mouse", true)
            .description("Scale mouse sensitivity down to match, so aiming stays controllable.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.keyWasDown = false;
   }

   @Override
   public void onDisable() {
      this.stop();
   }

   @Override
   public void onGameLeft() {
      this.stop();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.options != null) {
         int var2 = this.integer("bind");
         boolean var3 = var2 != -1 && var1.gui.screen() == null && RiptideBindUtil.isBindPressed(var1, var2);
         if (var3 != this.keyWasDown) {
            this.keyWasDown = var3;
            if (var3) {
               this.start(var1);
            } else {
               this.stop();
            }
         }
      } else {
         this.stop();
      }
   }

   private void start(Minecraft var1) {
      factor = Math.max(1.0, (double)this.integer("level"));
      zooming = true;
      if (this.bool("slow-mouse") && !(this.savedSensitivity >= 0.0)) {
         try {
            this.savedSensitivity = (Double)var1.options.sensitivity().get();
            var1.options.sensitivity().set(this.savedSensitivity / factor);
         } catch (Throwable var3) {
            this.savedSensitivity = -1.0;
         }
      }
   }

   private void stop() {
      zooming = false;
      this.keyWasDown = false;
      double var1 = this.savedSensitivity;
      this.savedSensitivity = -1.0;
      if (!(var1 < 0.0)) {
         Minecraft var3 = Minecraft.getInstance();
         if (var3 != null && var3.options != null) {
            try {
               var3.options.sensitivity().set(var1);
            } catch (Throwable var5) {
            }
         }
      }
   }

   public static float apply(float var0) {
      if (!zooming) {
         return var0;
      } else {
         double var1 = factor;
         return var1 <= 1.0 ? var0 : (float)(var0 / var1);
      }
   }

   public static boolean zooming() {
      return zooming;
   }

   @Override
   public String info() {
      return zooming ? this.integer("level") + "x" : "";
   }
}
