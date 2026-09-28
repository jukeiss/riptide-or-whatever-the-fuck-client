package riptide.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ArmorHudModule;
import riptide.modules.ArrayListHudModule;
import riptide.modules.CpsHudModule;
import riptide.modules.HudStack;
import riptide.modules.InfoHudModule;
import riptide.modules.KeystrokesHudModule;
import riptide.modules.PotionHudModule;
import riptide.modules.PvpCountHudModule;
import riptide.modules.RadarHudModule;
import riptide.modules.StaffListModule;
import riptide.modules.TargetHudModule;

@Mixin({Hud.class})
public abstract class RiptideHudSuiteMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$hudSuite(GuiGraphicsExtractor var1, DeltaTracker var2, CallbackInfo var3) {
      // Order matters: panels sharing a corner stack outward from the edge in this order.
      HudStack.reset();
      InfoHudModule.render(var1);
      KeystrokesHudModule.render(var1);
      CpsHudModule.render(var1);
      RadarHudModule.render(var1);
      ArrayListHudModule.render(var1);
      ArmorHudModule.render(var1);
      PotionHudModule.render(var1);
      PvpCountHudModule.render(var1);
      StaffListModule.render(var1);
      TargetHudModule.render(var1);
   }
}
