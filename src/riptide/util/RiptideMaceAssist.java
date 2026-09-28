package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

public final class RiptideMaceAssist {
   public static volatile boolean keepMace = true;

   private RiptideMaceAssist() {
   }

   public static boolean holdingMace() {
      Minecraft var0 = Minecraft.getInstance();
      return var0.player != null && var0.player.getMainHandItem().is(Items.MACE);
   }

   public static boolean suppressWeaponSwitch() {
      return keepMace && holdingMace();
   }
}
