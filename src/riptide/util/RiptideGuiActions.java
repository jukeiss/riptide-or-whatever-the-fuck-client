package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import riptide.modules.PackHideState;

public final class RiptideGuiActions {
   private RiptideGuiActions() {
   }

   public static boolean saveCurrentGui(Minecraft mc) {
      return saveCurrentGui(mc, true);
   }

   public static boolean saveCurrentGui(Minecraft mc, boolean notify) {
      if (mc != null && mc.gui.screen() != null && mc.player != null) {
         RiptideSharedState.get().storeScreen(mc.gui.screen(), mc.player.containerMenu);
         if (notify) {
            RiptideNotifications.show(savedGuiMessage(), -13248397);
         }

         return true;
      } else {
         if (notify) {
            RiptideNotifications.error("Failed to store GUI.");
         }

         return false;
      }
   }

   private static String savedGuiMessage() {
      int keyCode = RiptideConfig.getGlobal().keybindLoadGui;
      return keyCode == -1 ? "GUI stored." : "GUI stored. Press " + RiptideKeybindOverlay.getKeyName(keyCode) + " to restore.";
   }

   public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket) {
      return closeCurrentScreen(mc, sendPacket, true);
   }

   public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket, boolean notify) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc != null && mc.gui.screen() != null) {
         if (mc.gui.screen() instanceof RiptideSpecialGuiActions special) {
            if (sendPacket) {
               special.riptide$closeWithPacket(notify);
            } else {
               special.riptide$closeWithoutPacket(notify);
            }

            return true;
         } else {
            if (sendPacket) {
               if (mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                  mc.player.closeContainer();
               } else {
                  mc.gui.setScreen(null);
               }
            } else if (mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
               RiptideSharedState.get().setSuppressNextContainerClosePacket(true);
               mc.player.closeContainer();
               if (notify) {
                  RiptideClientMessaging.sendPrefixed("GUI closed without packet.");
               }
            } else {
               mc.gui.setScreen(null);
               if (notify) {
                  RiptideClientMessaging.sendPrefixed("Screen closed locally.");
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean desyncCurrentScreen(Minecraft mc) {
      return desyncCurrentScreen(mc, true);
   }

   public static boolean desyncCurrentScreen(Minecraft mc, boolean notify) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc != null && mc.gui.screen() != null) {
         if (mc.gui.screen() instanceof RiptideSpecialGuiActions special) {
            special.riptide$desync(notify);
            return true;
         } else if (mc.getConnection() != null && mc.player != null && mc.player.containerMenu != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            if (notify) {
               RiptideClientMessaging.sendPrefixed("GUI desynced: close packet sent while client screen stays open.");
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
