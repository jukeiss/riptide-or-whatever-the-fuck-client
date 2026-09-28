package riptide.util;

import java.util.List;

public class MeteorMacroAdapter {
   public static List<RiptideMacro> getMeteorMacros() {
      return RiptideCompatManager.getMeteorMacros();
   }

   public static void importToMeteor(RiptideMacro packUtilMacro) {
      if (RiptideCompatManager.importToMeteor(packUtilMacro)) {
         RiptideClientMessaging.sendPrefixed("§aImported to Meteor: " + packUtilMacro.name);
      } else {
         RiptideClientMessaging.sendPrefixed("§cFailed to import to Meteor");
      }
   }

   public static boolean importToRiptide(String macroName) {
      RiptideMacro packUtilMacro = RiptideCompatManager.getMeteorMacro(macroName);
      if (packUtilMacro == null) {
         RiptideClientMessaging.sendPrefixed("§cMeteor macro not found: " + macroName);
         return false;
      } else {
         RiptideMacro imported = RiptideMacroManager.get().addImportedCopy(packUtilMacro, packUtilMacro.name);
         if (imported == null) {
            return false;
         } else {
            if (!imported.name.equals(packUtilMacro.name)) {
               RiptideClientMessaging.sendPrefixed("§eImported Meteor macro as: " + imported.name);
            }

            return true;
         }
      }
   }
}
