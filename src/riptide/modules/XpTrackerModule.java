package riptide.modules;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.util.RiptideClientMessaging;

public final class XpTrackerModule extends Module {
   private static final long MIN_SAMPLE_MS = 10000L;
   private static final long HOUR_MS = 3600000L;
   private static volatile long gained;
   private static volatile long startedMs = System.currentTimeMillis();
   private int lastTotal = -1;

   public XpTrackerModule() {
      super("xp-tracker", "XpTracker", ModuleCategory.MISC, "Counts experience gained and works out the rate per hour.");
      this.add(
         new BoolSetting("announce-level", "Say On Level Up", false)
            .description("Print the running total each time you gain a level.")
            .group("General")
            .build()
      );
      this.add(
         new ActionSetting("report", "Rate", this::report).buttonLabel("Show").description("Print what you have gained and how fast.").group("General").build()
      );
      this.add(new ActionSetting("reset", "Reset", this::reset).buttonLabel("Reset").description("Start counting again from zero.").group("General").build());
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public void onEnable() {
      this.lastTotal = -1;
   }

   @Override
   public void onGameJoin() {
      this.lastTotal = -1;
   }

   @Override
   public void onGameLeft() {
      this.lastTotal = -1;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null) {
         int var2 = totalExperience(var1.player.experienceLevel, var1.player.experienceProgress);
         int var3 = this.lastTotal;
         this.lastTotal = var2;
         if (var3 >= 0) {
            if (var2 > var3) {
               int var4 = var2 - var3;
               gained += var4;
               if (this.isEnabled() && this.bool("announce-level")) {
                  int var5 = levelOf(var3);
                  if (levelOf(var2) > var5) {
                     this.report();
                  }
               }
            }
         }
      } else {
         this.lastTotal = -1;
      }
   }

   static int totalExperience(int var0, float var1) {
      int var2;
      if (var0 <= 16) {
         var2 = var0 * var0 + 6 * var0;
      } else if (var0 <= 31) {
         var2 = (int)(2.5 * var0 * var0 - 40.5 * var0 + 360.0);
      } else {
         var2 = (int)(4.5 * var0 * var0 - 162.5 * var0 + 2220.0);
      }

      return var2 + Math.round(var1 * levelCost(var0));
   }

   static int levelCost(int var0) {
      if (var0 <= 15) {
         return 2 * var0 + 7;
      } else {
         return var0 <= 30 ? 5 * var0 - 38 : 9 * var0 - 158;
      }
   }

   static int levelOf(int var0) {
      int var1 = 0;
      int var2 = 0;

      while (var2 + levelCost(var1) <= var0) {
         var2 += levelCost(var1);
         if (++var1 > 25000) {
            break;
         }
      }

      return var1;
   }

   static long perHour(long var0, long var2) {
      return var2 >= 10000L && var0 > 0L ? var0 * 3600000L / var2 : -1L;
   }

   private void report() {
      long var1 = System.currentTimeMillis() - startedMs;
      long var3 = perHour(gained, var1);
      String var5 = var3 < 0L ? "not enough time yet" : NetWorthModule.format(var3) + "/h";
      RiptideClientMessaging.sendPrefixed("§aXP: §f" + NetWorthModule.format(gained) + " §7gained in §f" + StatTrackerModule.duration(var1) + " §7— §f" + var5);
   }

   private void reset() {
      gained = 0L;
      startedMs = System.currentTimeMillis();
      this.lastTotal = -1;
      RiptideClientMessaging.sendPrefixed("§7Experience count reset.");
   }

   public static String rateText() {
      long var0 = perHour(gained, System.currentTimeMillis() - startedMs);
      return var0 < 0L ? null : NetWorthModule.format(var0) + "/h";
   }

   public static long gainedPoints() {
      return gained;
   }

   @Override
   public String info() {
      String var1 = rateText();
      return var1 == null ? "" : var1.toLowerCase(Locale.ROOT);
   }
}
