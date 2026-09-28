package riptide.api.macro;

import net.minecraft.client.Minecraft;

public abstract class AddonCondition {
   public abstract boolean check(Minecraft var1);

   public void onComplete() {
   }
}
