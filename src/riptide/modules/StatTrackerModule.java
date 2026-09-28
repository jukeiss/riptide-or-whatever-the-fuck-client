package riptide.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.util.RiptideClientMessaging;

public final class StatTrackerModule extends Module {
   private static final AtomicInteger KILLS = new AtomicInteger();
   private static final AtomicInteger DEATHS = new AtomicInteger();
   private static final AtomicInteger PEARLS = new AtomicInteger();
   private static final AtomicInteger TOTEMS = new AtomicInteger();
   private static volatile long startedMs = System.currentTimeMillis();
   private static volatile StatTrackerModule.Kill lastKill;
   private static volatile StatTrackerModule.Struck lastStruck;
   private boolean wasAlive = true;
   private int lastHealthSeen = -1;
   private static final long KILL_CREDIT_MS = 10000L;
   private final Map<Integer, Long> struck = new HashMap<>();
   private final Set<Integer> counted = new HashSet<>();

   public static int lastStruckId(long var0) {
      StatTrackerModule.Struck var2 = lastStruck;
      if (var2 == null) {
         return -1;
      } else {
         return System.currentTimeMillis() - var2.atMs() <= var0 ? var2.entityId() : -1;
      }
   }

   public static StatTrackerModule.Kill lastKill() {
      return lastKill;
   }

   public StatTrackerModule() {
      super("stat-tracker", "StatTracker", ModuleCategory.MISC, "Counts kills, deaths and what you got through this session.");
      this.add(new BoolSetting("announce-death", "Say On Death", true).description("Print the running total each time you die.").group("General").build());
      this.add(
         new ActionSetting("report", "Session", this::report).buttonLabel("Show").description("Print everything counted so far.").group("General").build()
      );
      this.add(new ActionSetting("reset", "Reset", this::reset).buttonLabel("Reset").description("Start the counts again from zero.").group("General").build());
   }

   public static void notePearl() {
      PEARLS.incrementAndGet();
   }

   public static void noteTotem() {
      TOTEMS.incrementAndGet();
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public void onEnable() {
      this.wasAlive = true;
      this.struck.clear();
      this.counted.clear();
   }

   @Override
   public void onGameJoin() {
      this.struck.clear();
      this.counted.clear();
   }

   @Override
   public void onGameLeft() {
      this.struck.clear();
      this.counted.clear();
   }

   @Override
   public boolean shouldCancelAttack(HitResult var1) {
      if (var1 instanceof EntityHitResult var2 && var2.getEntity() instanceof Player var3 && var3.getHealth() > 0.0F) {
         long var6 = System.currentTimeMillis();
         this.struck.put(var3.getId(), var6);
         lastStruck = new StatTrackerModule.Struck(var3.getId(), var6);
      }

      return false;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null) {
         this.wasAlive = true;
      } else if (this.isEnabled()) {
         boolean var2 = var1.player.getHealth() > 0.0F;
         if (this.wasAlive && !var2) {
            DEATHS.incrementAndGet();
            if (this.bool("announce-death")) {
               this.report();
            }
         }

         this.wasAlive = var2;
         this.lastHealthSeen = Math.round(var1.player.getHealth());
         if (var1.level != null) {
            this.countKills(var1);
         }
      }
   }

   private void countKills(Minecraft var1) {
      if (!this.struck.isEmpty()) {
         long var2 = System.currentTimeMillis();
         this.struck.entrySet().removeIf(var2x -> var2 - var2x.getValue() > 10000L);

         for (Entry var5 : this.struck.entrySet()) {
            int var6 = (Integer)var5.getKey();
            if (!this.counted.contains(var6) && var1.level.getEntity(var6) instanceof Player var8 && !(var8.getHealth() > 0.0F)) {
               this.counted.add(var6);
               KILLS.incrementAndGet();
               String var9 = var8.getGameProfile().name();
               if (var9 != null && !var9.isEmpty()) {
                  lastKill = new StatTrackerModule.Kill(var9, System.currentTimeMillis());
               }
            }
         }

         this.counted.retainAll(this.struck.keySet());
      }
   }

   private void report() {
      int var1 = KILLS.get();
      int var2 = DEATHS.get();
      RiptideClientMessaging.sendPrefixed(
         "§7Session: §f"
            + var1
            + " §7kills, §f"
            + var2
            + " §7deaths, ratio §f"
            + ratio(var1, var2)
            + " §8("
            + PEARLS.get()
            + " pearls, "
            + TOTEMS.get()
            + " totems, "
            + duration(System.currentTimeMillis() - startedMs)
            + ")"
      );
   }

   private void reset() {
      KILLS.set(0);
      DEATHS.set(0);
      PEARLS.set(0);
      TOTEMS.set(0);
      startedMs = System.currentTimeMillis();
      lastKill = null;
      lastStruck = null;
      RiptideClientMessaging.sendPrefixed("§7Session counts reset.");
   }

   public static String ratio(int var0, int var1) {
      if (var1 <= 0) {
         return Integer.toString(Math.max(0, var0));
      } else {
         double var2 = (double)var0 / var1;
         String var4 = String.format(Locale.ROOT, "%.2f", var2);
         return var4.endsWith("0") ? var4.substring(0, var4.length() - 1) : var4;
      }
   }

   static String duration(long var0) {
      long var2 = Math.max(0L, var0) / 1000L;
      if (var2 < 60L) {
         return var2 + "s";
      } else {
         long var4 = var2 / 60L;
         return var4 < 60L ? var4 + "m" : var4 / 60L + "h " + var4 % 60L + "m";
      }
   }

   @Override
   public String info() {
      return KILLS.get() + "/" + DEATHS.get();
   }

   public static int kills() {
      return KILLS.get();
   }

   public static int deaths() {
      return DEATHS.get();
   }

   int lastHealth() {
      return this.lastHealthSeen;
   }

   public record Kill(String name, long atMs) {
   }

   public record Struck(int entityId, long atMs) {
   }
}
