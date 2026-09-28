package riptide.modules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class TotemPopsModule extends Module {
   private static final int TOTEM_USED = 35;
   private final Map<String, Integer> pops = new ConcurrentHashMap<>();

   public TotemPopsModule() {
      super("totem-pops", "TotemPops", ModuleCategory.COMBAT, "Counts the totems other players burn through in a fight.");
      this.add(new BoolSetting("chat", "Announce", true).description("Print each pop to chat as it happens.").group("Alerts").build());
      this.add(new BoolSetting("self", "Count Me", false).description("Also count your own totems.").group("Alerts").build());
      this.add(new IntSetting("range", "Range", 64, 8, 256, 8).unit("blocks").description("Ignore pops further away than this.").group("Alerts").build());
      this.add(new ActionSetting("reset", "Reset Counts", this::reset).buttonLabel("Reset").description("Clear every tally.").group("Alerts").build());
   }

   @Override
   public void onGameJoin() {
      this.pops.clear();
   }

   @Override
   public void onEnable() {
      this.pops.clear();
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundEntityEventPacket var2) {
         if (var2.getEventId() != 35) {
            return false;
         } else {
            Minecraft var3 = Minecraft.getInstance();
            if (var3 != null && var3.level != null && var3.player != null) {
               Entity var4;
               try {
                  var4 = var2.getEntity(var3.level);
               } catch (Throwable var9) {
                  return false;
               }

               if (var4 instanceof Player var5) {
                  if (var5 == var3.player) {
                     StatTrackerModule.noteTotem();
                  }

                  if (var5 == var3.player && !this.bool("self")) {
                     return false;
                  } else {
                     int var6 = this.integer("range");
                     if (var5.distanceTo(var3.player) > var6) {
                        return false;
                     } else {
                        String var7 = var5.getGameProfile().name();
                        if (var7 != null && !var7.isEmpty()) {
                           int var8 = this.pops.merge(var7, 1, Integer::sum);
                           if (this.bool("chat")) {
                              RiptideClientMessaging.sendPrefixed("§e" + var7 + " §7popped a totem §f(" + var8 + " this session§f)");
                           }

                           return false;
                        } else {
                           return false;
                        }
                     }
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void reset() {
      int var1 = this.pops.size();
      this.pops.clear();
      RiptideClientMessaging.sendPrefixed("§7Cleared totem counts for §f" + var1 + " §7players.");
   }
}
