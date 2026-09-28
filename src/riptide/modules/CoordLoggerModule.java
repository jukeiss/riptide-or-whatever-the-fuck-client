package riptide.modules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class CoordLoggerModule extends Module {
   private static final Pattern COORDS = Pattern.compile("(?<![\\d.,:])(-?\\d{1,8})\\s*[, ]\\s*(-?\\d{1,3})\\s*[, ]\\s*(-?\\d{1,8})(?![\\d.,:])");
   private static final int WORLD_LIMIT = 30000000;
   private static final int MIN_HEIGHT = -128;
   private static final int MAX_HEIGHT = 400;
   private volatile String lastSaved = "";

   @Override
   public void onEnable() {
      this.lastSaved = "";
   }

   @Override
   public void onGameJoin() {
      this.lastSaved = "";
   }

   static int[] parse(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         Matcher var1 = COORDS.matcher(var0);

         while (var1.find()) {
            if (!isGroupedNumber(var1)) {
               int var2;
               int var3;
               int var4;
               try {
                  var2 = Integer.parseInt(var1.group(1));
                  var3 = Integer.parseInt(var1.group(2));
                  var4 = Integer.parseInt(var1.group(3));
               } catch (NumberFormatException var6) {
                  continue;
               }

               if (Math.abs(var2) <= 30000000 && Math.abs(var4) <= 30000000 && var3 >= -128 && var3 <= 400) {
                  return new int[]{var2, var3, var4};
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static boolean isGroupedNumber(Matcher var0) {
      String var1 = var0.group();
      if (var1.indexOf(44) < 0) {
         return false;
      } else {
         for (int var2 = 0; var2 < var1.length(); var2++) {
            if (Character.isWhitespace(var1.charAt(var2))) {
               return false;
            }
         }

         return isThousandsGroup(var0.group(2)) && isThousandsGroup(var0.group(3));
      }
   }

   private static boolean isThousandsGroup(String var0) {
      if (var0.length() != 3) {
         return false;
      } else {
         for (int var1 = 0; var1 < 3; var1++) {
            if (!Character.isDigit(var0.charAt(var1))) {
               return false;
            }
         }

         return true;
      }
   }

   public CoordLoggerModule() {
      super("coord-logger", "CoordLogger", ModuleCategory.MISC, "Saves coordinates posted in chat as waypoints.");
      this.add(new BoolSetting("waypoint", "Save Waypoint", true).description("Drop a waypoint for every set of coordinates found.").group("General").build());
      this.add(new BoolSetting("chat", "Confirm In Chat", true).description("Say so when one is saved.").group("General").build());
      this.add(new ColorSetting("color", "Waypoint Color", -11870592).description("Colour for the waypoints it creates.").group("General").build());
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundSystemChatPacket var2) {
         Component var3 = var2.content();
         if (var3 == null) {
            return false;
         } else {
            String var4 = var3.getString();
            if (var4 != null && !var4.isEmpty()) {
               int[] var5 = parse(var4);
               if (var5 == null) {
                  return false;
               } else {
                  int var6 = var5[0];
                  int var7 = var5[1];
                  int var8 = var5[2];
                  String var9 = var6 + ":" + var7 + ":" + var8;
                  if (var9.equals(this.lastSaved)) {
                     return false;
                  } else {
                     this.lastSaved = var9;
                     this.save(var6, var7, var8);
                     return false;
                  }
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void save(int var1, int var2, int var3) {
      Minecraft var4 = Minecraft.getInstance();
      if (var4 != null) {
         if (this.bool("waypoint")) {
            RiptideWaypoints.get()
               .add(
                  RiptideWaypoints.scopeKey(var4),
                  new RiptideWaypoints.Waypoint(
                     "Chat " + var1 + " " + var3, var1, var2, var3, ModuleRenderUtil.color(this, "color", -11870592), System.currentTimeMillis(), false
                  )
               );
         }

         if (this.bool("chat")) {
            RiptideClientMessaging.sendPrefixed("§aSaved coords from chat: §f" + var1 + " " + var2 + " " + var3);
         }
      }
   }
}
