package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class AutoLeaveModule extends Module {
   private static final long JOIN_GRACE_MS = 5000L;
   private long joinedMs;
   private final RiptideSettingCache<Set<String>> watched = new RiptideSettingCache<>(AutoLeaveModule::lowerCased);

   private static Set<String> lowerCased(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            var1.add(var3.trim().toLowerCase(Locale.ROOT));
         }
      }

      return Set.copyOf(var1);
   }

   public AutoLeaveModule() {
      super("auto-leave", "AutoLeave", ModuleCategory.COMBAT, "Disconnects when your health drops or a watched player appears.");
      this.add(new BoolSetting("on-health", "On Low Health", true).description("Leave when your health drops to the level below.").group("Health").build());
      this.add(
         new IntSetting("health", "Health", 6, 1, 19, 1)
            .unit("hp")
            .description("Half a heart is 1. Leaves at or below this.")
            .visibleWhen(() -> this.bool("on-health"))
            .group("Health")
            .build()
      );
      this.add(
         new BoolSetting("ignore-absorption", "Ignore Absorption", false)
            .description("Judge on health alone, not the golden hearts on top.")
            .visibleWhen(() -> this.bool("on-health"))
            .group("Health")
            .build()
      );
      this.add(new BoolSetting("on-player", "On Player Near", false).description("Leave when one of the players below comes close.").group("Players").build());
      this.add(
         new StringListSetting("players", "Players", "")
            .description("Who to run from. Leave empty to mean any player.")
            .playerNameList()
            .visibleWhen(() -> this.bool("on-player"))
            .group("Players")
            .build()
      );
      this.add(
         new IntSetting("range", "Range", 48, 8, 256, 8)
            .unit("blocks")
            .description("How close they have to get.")
            .visibleWhen(() -> this.bool("on-player"))
            .group("Players")
            .build()
      );
   }

   @Override
   public void onGameJoin() {
      this.joinedMs = System.currentTimeMillis();
   }

   @Override
   public void onEnable() {
      this.joinedMs = System.currentTimeMillis();
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.level != null) {
         if (System.currentTimeMillis() - this.joinedMs >= 5000L) {
            if (this.bool("on-health")) {
               float var2 = var1.player.getHealth();
               if (!this.bool("ignore-absorption")) {
                  var2 += var1.player.getAbsorptionAmount();
               }

               if (var2 > 0.0F && var2 <= this.integer("health")) {
                  this.leave("health at " + Math.round(var2));
                  return;
               }
            }

            if (this.bool("on-player")) {
               Set var8 = this.watched.get(this.list("players"));
               int var3 = this.integer("range");

               for (Player var5 : var1.level.players()) {
                  if (var5 != var1.player) {
                     float var6 = var5.distanceTo(var1.player);
                     if (!(var6 > var3)) {
                        String var7 = var5.getGameProfile().name();
                        if (var7 != null && !var7.isEmpty() && (var8.isEmpty() || var8.contains(var7.toLowerCase(Locale.ROOT)))) {
                           this.leave(var7 + " came within " + Math.round(var6) + " blocks");
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void leave(String var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2 != null && var2.getConnection() != null) {
         RiptideClientMessaging.sendPrefixed("§cAutoLeave: §f" + var1);
         this.setEnabled(false);

         try {
            var2.getConnection().getConnection().disconnect(Component.literal("AutoLeave: " + var1));
         } catch (Throwable var4) {
         }
      }
   }
}
