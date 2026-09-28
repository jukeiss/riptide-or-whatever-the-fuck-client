package riptide.modules;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class VisualRangeModule extends Module {
   private final Set<String> seen = new HashSet<>();
   private final Set<String> seenFriends = new HashSet<>();
   private final RiptideSettingCache<Set<String>> ignore = new RiptideSettingCache<>(VisualRangeModule::lowerCased);

   public VisualRangeModule() {
      super("visual-range", "VisualRange", ModuleCategory.MISC, "Warns you when a player comes into render distance, and when they leave.");
      this.add(new BoolSetting("on-enter", "On Enter", true).description("Say something when a player appears.").group("Alerts").build());
      this.add(new BoolSetting("on-leave", "On Leave", true).description("Say something when a player disappears.").group("Alerts").build());
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping when a player appears.").group("Alerts").build());
      this.add(new IntSetting("range", "Range", 128, 16, 512, 16).unit("blocks").description("Only count players closer than this.").group("Alerts").build());
      this.add(new StringListSetting("ignore", "Ignore", "").description("Players to say nothing about.").playerNameList().group("Alerts").build());
      this.add(
         new BoolSetting("friends", "Alert For Friends", false)
            .description("Say something when a friend comes into range too. Off by default: the point of the alert is someone you did not expect.")
            .group("Alerts")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.forget();
   }

   @Override
   public void onGameJoin() {
      this.forget();
   }

   private void forget() {
      this.seen.clear();
      this.seenFriends.clear();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.level != null && var1.player != null) {
         int var2 = this.integer("range");
         boolean var3 = this.bool("friends");
         Set var4 = this.ignore.get(this.list("ignore"));
         LinkedHashSet var5 = new LinkedHashSet();
         HashSet var6 = new HashSet();

         for (Player var8 : var1.level.players()) {
            if (var8 != var1.player && !(var8.distanceTo(var1.player) > var2)) {
               String var9 = var8.getGameProfile().name();
               if (var9 != null && !var9.isEmpty() && !var4.contains(var9.toLowerCase(Locale.ROOT))) {
                  boolean var10 = isFriend(var8);
                  if (!var10 || var3) {
                     var5.add(var9);
                     if (var10) {
                        var6.add(var9);
                     }
                  }
               }
            }
         }

         if (this.bool("on-enter")) {
            for (String var13 : var5) {
               if (!this.seen.contains(var13)) {
                  boolean var15 = var6.contains(var13);
                  RiptideClientMessaging.sendPrefixed((var15 ? "§b" : "§c") + var13 + " §7came into range");
                  if (this.bool("sound") && !var15) {
                     ping(var1);
                  }
               }
            }
         }

         if (this.bool("on-leave")) {
            for (String var14 : this.seen) {
               if (!var5.contains(var14)) {
                  RiptideClientMessaging.sendPrefixed((this.seenFriends.contains(var14) ? "§8" : "§7") + var14 + " left range");
               }
            }
         }

         this.seen.clear();
         this.seen.addAll(var5);
         this.seenFriends.clear();
         this.seenFriends.addAll(var6);
      } else {
         this.forget();
      }
   }

   private static Set<String> lowerCased(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            var1.add(var3.trim().toLowerCase(Locale.ROOT));
         }
      }

      return Set.copyOf(var1);
   }

   private static boolean isFriend(Player var0) {
      try {
         return TeamsModule.isFriendOrTeam(var0);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   private static void ping(Minecraft var0) {
      try {
         var0.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.6F);
      } catch (Throwable var2) {
      }
   }
}
