package riptide.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class NetWorthModule extends Module {
   private static final String DEFAULT_PRICES = String.join(
      "|",
      "netherite_ingot=250000",
      "netherite_block=2250000",
      "ancient_debris=180000",
      "diamond=2500",
      "diamond_block=22500",
      "elytra=400000",
      "totem_of_undying=90000",
      "shulker_box=150000",
      "enchanted_golden_apple=120000",
      "golden_apple=1500",
      "ender_pearl=900",
      "experience_bottle=400",
      "emerald=600"
   );
   private final RiptideSettingCache<Map<Item, Long>> prices = new RiptideSettingCache<>(NetWorthModule::parsePrices);

   public NetWorthModule() {
      super("net-worth", "NetWorth", ModuleCategory.MISC, "Adds up the value of what you are carrying, using prices you set.");
      this.add(
         new StringListSetting("prices", "Prices", DEFAULT_PRICES)
            .description("One item=price per entry, such as diamond=2500. Anything not listed counts as worthless.")
            .group("Prices")
            .build()
      );
      this.add(new BoolSetting("include-hotbar", "Count Hotbar", true).description("Include the nine slots you can hold.").group("What Counts").build());
      this.add(
         new BoolSetting("include-armor", "Count Worn Armour", true)
            .description("Include what you are wearing and holding in your off hand.")
            .group("What Counts")
            .build()
      );
      this.add(
         new BoolSetting("include-shulkers", "Look Inside Shulkers", true)
            .description("Add up what is inside shulker boxes you are carrying, as well as the box.")
            .group("What Counts")
            .build()
      );
      this.add(
         new ActionSetting("report", "Total", this::report)
            .buttonLabel("Count")
            .description("Print what you are carrying and what it comes to.")
            .group("Prices")
            .build()
      );
   }

   static Map<Item, Long> parsePrices(List<String> var0) {
      HashMap var1 = new HashMap();

      for (String var3 : var0) {
         if (var3 != null) {
            int var4 = var3.indexOf(61);
            if (var4 > 0 && var4 < var3.length() - 1) {
               String var5 = var3.substring(0, var4).trim().toLowerCase(Locale.ROOT);
               if (!var5.isEmpty()) {
                  if (var5.indexOf(58) < 0) {
                     var5 = "minecraft:" + var5;
                  }

                  long var6;
                  try {
                     var6 = Long.parseLong(var3.substring(var4 + 1).trim().replace(",", ""));
                  } catch (NumberFormatException var9) {
                     continue;
                  }

                  if (var6 > 0L) {
                     Identifier var8 = Identifier.tryParse(var5);
                     if (var8 != null) {
                        BuiltInRegistries.ITEM.getOptional(var8).ifPresent(var3x -> var1.put(var3x, var6));
                     }
                  }
               }
            }
         }
      }

      return Map.copyOf(var1);
   }

   private long valueOf(ItemStack var1, Map<Item, Long> var2, boolean var3, int var4) {
      if (var1 != null && !var1.isEmpty()) {
         long var5 = var2.getOrDefault(var1.getItem(), 0L) * var1.getCount();
         if (var3 && var4 <= 1) {
            long var7 = 0L;
            ItemContainerContents var9 = (ItemContainerContents)var1.get(DataComponents.CONTAINER);
            if (var9 != null) {
               for (ItemStackTemplate var11 : var9.nonEmptyItems()) {
                  var7 += this.valueOf(var11, var2, var4 + 1);
               }
            }

            return var5 + var7;
         } else {
            return var5;
         }
      } else {
         return 0L;
      }
   }

   private long valueOf(ItemStackTemplate var1, Map<Item, Long> var2, int var3) {
      if (var1 != null && var1.count() > 0) {
         long var4 = var2.getOrDefault(var1.item().value(), 0L) * var1.count();
         if (var3 > 1) {
            return var4;
         } else {
            long var6 = 0L;
            ItemContainerContents var8 = (ItemContainerContents)var1.get(DataComponents.CONTAINER);
            if (var8 != null) {
               for (ItemStackTemplate var10 : var8.nonEmptyItems()) {
                  var6 += this.valueOf(var10, var2, var3 + 1);
               }
            }

            return var4 + var6;
         }
      } else {
         return 0L;
      }
   }

   private NetWorthModule.Carried carried(Minecraft var1, Map<Item, Long> var2) {
      boolean var3 = this.bool("include-shulkers");
      long var4 = 0L;
      int var6 = 0;
      int var7 = this.bool("include-hotbar") ? 0 : 9;

      for (int var8 = var7; var8 < 36; var8++) {
         long var9 = this.valueOf(var1.player.getInventory().getItem(var8), var2, var3, 0);
         if (var9 > 0L) {
            var4 += var9;
            var6++;
         }
      }

      if (this.bool("include-armor")) {
         for (EquipmentSlot var11 : EquipmentSlot.values()) {
            long var12 = this.valueOf(var1.player.getItemBySlot(var11), var2, var3, 0);
            if (var12 > 0L) {
               var4 += var12;
               var6++;
            }
         }
      }

      return new NetWorthModule.Carried(var4, var6);
   }

   public static String carriedTotalText() {
      if (ModuleRegistry.get("net-worth") instanceof NetWorthModule var1 && var1.isEnabled()) {
         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null) {
            Map var3 = var1.prices.get(var1.list("prices"));
            return var3.isEmpty() ? null : format(var1.carried(var2, var3).total());
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String stackValueText(ItemStack var0) {
      if (!(ModuleRegistry.get("net-worth") instanceof NetWorthModule var2 && var2.isEnabled())) {
         return null;
      } else if (var0 != null && !var0.isEmpty()) {
         Map var3 = var2.prices.get(var2.list("prices"));
         if (var3.isEmpty()) {
            return null;
         } else {
            long var4 = var2.valueOf(var0, var3, var2.bool("include-shulkers"), 0);
            return var4 <= 0L ? null : format(var4);
         }
      } else {
         return null;
      }
   }

   private void report() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null) {
         Map var2 = this.prices.get(this.list("prices"));
         if (var2.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§cNo prices set, so everything counts as worthless.");
         } else {
            NetWorthModule.Carried var3 = this.carried(var1, var2);
            long var4 = var3.total();
            int var6 = var3.stacks();
            if (var4 <= 0L) {
               RiptideClientMessaging.sendPrefixed("§7Nothing you are carrying has a price set.");
            } else {
               RiptideClientMessaging.sendPrefixed("§aWorth §f" + format(var4) + " §7across §f" + var6 + " §7stack" + (var6 == 1 ? "" : "s") + ".");
            }
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cNot in a world.");
      }
   }

   static String format(long var0) {
      if (var0 >= 1000000000L) {
         return trim(var0 / 1.0E9) + "B";
      } else if (var0 >= 1000000L) {
         return trim(var0 / 1000000.0) + "M";
      } else {
         return var0 >= 1000L ? trim(var0 / 1000.0) + "K" : Long.toString(var0);
      }
   }

   private static String trim(double var0) {
      String var2 = String.format(Locale.ROOT, "%.1f", var0);
      return var2.endsWith(".0") ? var2.substring(0, var2.length() - 2) : var2;
   }

   record Carried(long total, int stacks) {
   }
}
