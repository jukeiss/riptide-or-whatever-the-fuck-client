package riptide.modules;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class SoundAlertModule extends Module {
   private static final long ALERT_INTERVAL_MS = 2000L;
   private final AtomicLong nextContainerMs = new AtomicLong();
   private final AtomicLong nextExplosiveMs = new AtomicLong();
   private final AtomicLong nextCombatMs = new AtomicLong();

   public SoundAlertModule() {
      super("sound-alert", "SoundAlert", ModuleCategory.MISC, "Warns you about sounds that mean somebody is nearby.");
      this.add(new IntSetting("range", "Range", 32, 4, 96, 4).unit("blocks").description("Ignore sounds further away than this.").group("General").build());
      this.add(
         new BoolSetting("containers", "Chests And Doors", true).description("Chests, shulkers, barrels, trapdoors and doors opening.").group("Sounds").build()
      );
      this.add(
         new BoolSetting("explosives", "Anchors And TNT", true)
            .description("Respawn anchors charging, TNT priming, end crystals placed.")
            .group("Sounds")
            .build()
      );
      this.add(new BoolSetting("combat", "Combat", true).description("Totems firing, pearls thrown, elytra fireworks.").group("Sounds").build());
      this.add(
         new BoolSetting("sound", "Play Sound", false)
            .description("Ping as well as printing the warning. Off by default, since the thing that triggered this was already a sound.")
            .group("Alerts")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.nextContainerMs.set(0L);
      this.nextExplosiveMs.set(0L);
      this.nextCombatMs.set(0L);
   }

   @Override
   public void onGameJoin() {
      this.onEnable();
   }

   @Override
   public void onSoundPacket(ClientboundSoundPacket var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2 != null && var2.player != null && var1 != null) {
         String var3 = soundName(var1);
         if (var3 != null) {
            SoundAlertModule.Category var4 = this.classify(var3);
            if (var4 != null) {
               int var5 = this.integer("range");
               double var6 = var1.getX() - var2.player.getX();
               double var8 = var1.getY() - var2.player.getY();
               double var10 = var1.getZ() - var2.player.getZ();
               double var12 = var6 * var6 + var8 * var8 + var10 * var10;
               if (!(var12 > (double)var5 * var5)) {
                  AtomicLong var14 = switch (var4) {
                     case CONTAINER -> this.nextContainerMs;
                     case EXPLOSIVE -> this.nextExplosiveMs;
                     case COMBAT -> this.nextCombatMs;
                  };
                  long var15 = System.currentTimeMillis();
                  long var17 = var14.get();
                  if (var15 >= var17 && var14.compareAndSet(var17, var15 + 2000L)) {
                     this.alert(var2, var4, var3, (int)Math.round(Math.sqrt(var12)));
                  }
               }
            }
         }
      }
   }

   private void alert(Minecraft var1, SoundAlertModule.Category var2, String var3, int var4) {
      String var5 = switch (var2) {
         case CONTAINER -> "§e";
         case EXPLOSIVE -> "§c";
         case COMBAT -> "§b";
      };
      RiptideClientMessaging.sendPrefixed(var5 + prettyName(var3) + " §7about §f" + var4 + "m §7away");
      if (this.bool("sound")) {
         try {
            var1.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.2F);
         } catch (Throwable var7) {
         }
      }
   }

   private SoundAlertModule.Category classify(String var1) {
      if (this.bool("containers") && isContainer(var1)) {
         return SoundAlertModule.Category.CONTAINER;
      } else if (this.bool("explosives") && isExplosive(var1)) {
         return SoundAlertModule.Category.EXPLOSIVE;
      } else {
         return this.bool("combat") && isCombat(var1) ? SoundAlertModule.Category.COMBAT : null;
      }
   }

   static boolean isContainer(String var0) {
      return var0.contains("chest.open")
         || var0.contains("shulker_box.open")
         || var0.contains("barrel.open")
         || var0.contains("ender_chest.open")
         || var0.contains("wooden_door.open")
         || var0.contains("wooden_trapdoor.open")
         || var0.contains("iron_door.open");
   }

   static boolean isExplosive(String var0) {
      return var0.contains("respawn_anchor.charge")
         || var0.contains("respawn_anchor.set_spawn")
         || var0.contains("tnt.primed")
         || var0.contains("end_crystal")
         || var0.contains("generic.explode");
   }

   static boolean isCombat(String var0) {
      return var0.contains("totem.use") || var0.contains("ender_pearl.throw") || var0.contains("firework_rocket.launch") || var0.contains("elytra.loop");
   }

   private static String soundName(ClientboundSoundPacket var0) {
      try {
         SoundEvent var1 = (SoundEvent)var0.getSound().value();
         return var1 == null ? null : var1.location().getPath();
      } catch (Throwable var2) {
         return null;
      }
   }

   static String prettyName(String var0) {
      String var1 = var0.replace('.', ' ').replace('_', ' ').trim();
      return var1.isEmpty() ? "Sound" : Character.toUpperCase(var1.charAt(0)) + var1.substring(1);
   }

   private static enum Category {
      CONTAINER,
      EXPLOSIVE,
      COMBAT;
   }
}
