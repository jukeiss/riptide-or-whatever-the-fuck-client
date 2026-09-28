package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideChatLine;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class ShopAlertModule extends Module {
   private static final Pattern PRICE = Pattern.compile("(?<![\\w.])(\\d[\\d,.]*)\\s*([kKmMbB])?(?![\\w])");
   private static final long ALERT_INTERVAL_MS = 3000L;
   private final RiptideSettingCache<List<ShopAlertModule.Want>> wants = new RiptideSettingCache<>(ShopAlertModule::parseWants);
   private volatile long nextAlertMs;
   private volatile String pendingMessage;

   public ShopAlertModule() {
      super("shop-alert", "ShopAlert", ModuleCategory.MISC, "Tells you when somebody is selling something on your list at your price.");
      this.add(
         new StringListSetting("wants", "Watching For", "netherite ingot=200000|elytra=350000")
            .description("One item=most you will pay per entry. The price may be written 200k.")
            .group("General")
            .build()
      );
      this.add(
         new StringListSetting("phrases", "Selling Wording", "selling|wts|for sale|s>")
            .description("Text that marks a line as an offer. Any one of these is enough.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("max-price", "Ignore Above", 0, 0, 100000000, 1000)
            .description("A price ceiling applied to everything, on top of the per-item ones. Zero means no extra ceiling.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping as well as printing the alert.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.nextAlertMs = 0L;
      this.pendingMessage = null;
   }

   @Override
   public void onGameJoin() {
      this.onEnable();
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundSystemChatPacket var2) {
         String var3;
         try {
            var3 = var2.content().getString();
         } catch (Throwable var14) {
            return false;
         }

         if (var3 == null || var3.isEmpty()) {
            return false;
         } else if (!this.looksLikeAnOffer(var3)) {
            return false;
         } else {
            List var4 = this.wants.get(this.list("wants"));
            if (var4.isEmpty()) {
               return false;
            } else {
               String var5 = var3.toLowerCase(Locale.ROOT);
               ShopAlertModule.Want var6 = null;

               for (ShopAlertModule.Want var8 : var4) {
                  if (var5.contains(var8.item())) {
                     var6 = var8;
                     break;
                  }
               }

               if (var6 == null) {
                  return false;
               } else {
                  long var15 = cheapestPrice(var3);
                  if (var15 < 0L) {
                     return false;
                  } else {
                     long var9 = this.integer("max-price");
                     if (var15 > var6.maxPrice()) {
                        return false;
                     } else if (var9 > 0L && var15 > var9) {
                        return false;
                     } else {
                        long var11 = System.currentTimeMillis();
                        if (var11 < this.nextAlertMs) {
                           return false;
                        } else {
                           this.nextAlertMs = var11 + 3000L;
                           String var13 = RiptideChatLine.sender(var3);
                           this.pendingMessage = "§a" + var6.item() + " §7for §f" + NetWorthModule.format(var15) + (var13 == null ? "" : " §7from §f" + var13);
                           return false;
                        }
                     }
                  }
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean looksLikeAnOffer(String var1) {
      for (String var3 : this.list("phrases")) {
         if (RiptideChatLine.contains(var1, var3)) {
            return true;
         }
      }

      return false;
   }

   static long cheapestPrice(String var0) {
      Matcher var1 = PRICE.matcher(var0);
      long var2 = -1L;

      while (var1.find()) {
         long var4 = parsePrice(var1.group(1), var1.group(2));
         if (var4 > 0L && (var2 < 0L || var4 < var2)) {
            var2 = var4;
         }
      }

      return var2;
   }

   static long parsePrice(String var0, String var1) {
      if (var0 != null && !var0.isEmpty()) {
         String var2 = var0.replace(",", "");

         double var3;
         try {
            var3 = Double.parseDouble(var2);
         } catch (NumberFormatException var6) {
            return -1L;
         }

         if (var1 != null && !var1.isEmpty()) {
            var3 *= switch (Character.toLowerCase(var1.charAt(0))) {
               case 'b' -> 1.0E9;
               case 'k' -> 1000.0;
               case 'm' -> 1000000.0;
               default -> 1.0;
            };
         }

         return !(var3 <= 0.0) && !(var3 > 9.223372E18F) ? Math.round(var3) : -1L;
      } else {
         return -1L;
      }
   }

   static List<ShopAlertModule.Want> parseWants(List<String> var0) {
      ArrayList var1 = new ArrayList();

      for (String var3 : var0) {
         if (var3 != null) {
            int var4 = var3.indexOf(61);
            if (var4 > 0 && var4 < var3.length() - 1) {
               String var5 = var3.substring(0, var4).trim().toLowerCase(Locale.ROOT);
               if (!var5.isEmpty()) {
                  Matcher var6 = PRICE.matcher(var3.substring(var4 + 1).trim());
                  if (var6.find()) {
                     long var7 = parsePrice(var6.group(1), var6.group(2));
                     if (var7 > 0L) {
                        var1.add(new ShopAlertModule.Want(var5, var7));
                     }
                  }
               }
            }
         }
      }

      return List.copyOf(var1);
   }

   @Override
   public void tick() {
      String var1 = this.pendingMessage;
      if (var1 != null) {
         this.pendingMessage = null;
         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null) {
            RiptideClientMessaging.sendPrefixed(var1);
            if (this.bool("sound")) {
               try {
                  var2.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.8F);
               } catch (Throwable var4) {
               }
            }
         }
      }
   }

   record Want(String item, long maxPrice) {
   }
}
