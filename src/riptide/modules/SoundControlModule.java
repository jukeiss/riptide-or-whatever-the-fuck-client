package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import riptide.api.module.BoolSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideSettingCache;

public final class SoundControlModule extends Module {
   private final RiptideSettingCache<Set<String>> extra = new RiptideSettingCache<>(SoundControlModule::parseIds);
   private volatile int silenced;

   public SoundControlModule() {
      super("sound-control", "SoundControl", ModuleCategory.MISC, "Silences chosen sounds so the ones that matter can be heard.");
      this.add(
         new BoolSetting("explosions", "Explosions", false)
            .description("Crystals, TNT and anchors. The loudest thing in any fight, and the one that hides footsteps.")
            .group("What To Silence")
            .build()
      );
      this.add(
         new BoolSetting("fireworks", "Fireworks", false)
            .description("Elytra rockets, which are constant while anyone is flying.")
            .group("What To Silence")
            .build()
      );
      this.add(
         new BoolSetting("ambient", "Cave Ambience", false)
            .description("The underground noises that sound like somebody is there.")
            .group("What To Silence")
            .build()
      );
      this.add(new BoolSetting("mobs", "Mobs", false).description("Mob idle and hurt noises.").group("What To Silence").build());
      this.add(
         new StringListSetting("extra", "Also Silence", "")
            .description("Sound ids to silence as well, such as entity.ghast.scream.")
            .group("What To Silence")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.silenced = 0;
   }

   @Override
   public void onGameJoin() {
      this.silenced = 0;
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundSoundPacket var2) {
         String var3 = soundName(var2);
         if (var3 != null && this.shouldSilence(var3)) {
            this.silenced++;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   boolean shouldSilence(String var1) {
      if (this.bool("explosions") && isExplosion(var1)) {
         return true;
      } else if (this.bool("fireworks") && isFirework(var1)) {
         return true;
      } else if (this.bool("ambient") && isAmbient(var1)) {
         return true;
      } else {
         return this.bool("mobs") && isMob(var1) ? true : this.extra.get(this.list("extra")).contains(var1);
      }
   }

   static boolean isExplosion(String var0) {
      return var0.contains("generic.explode") || var0.contains("tnt.primed") || var0.contains("respawn_anchor.deplete") || var0.contains("end_crystal");
   }

   static boolean isFirework(String var0) {
      return var0.contains("firework_rocket");
   }

   static boolean isAmbient(String var0) {
      return var0.startsWith("ambient.") || var0.contains("cave");
   }

   static boolean isMob(String var0) {
      return var0.startsWith("entity.") && (var0.contains(".ambient") || var0.contains(".hurt") || var0.contains(".idle"));
   }

   private static String soundName(ClientboundSoundPacket var0) {
      try {
         SoundEvent var1 = (SoundEvent)var0.getSound().value();
         return var1 == null ? null : var1.location().getPath();
      } catch (Throwable var2) {
         return null;
      }
   }

   static Set<String> parseIds(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            String var4 = var3.trim().toLowerCase(Locale.ROOT);
            int var5 = var4.indexOf(58);
            if (var5 >= 0) {
               var4 = var4.substring(var5 + 1);
            }

            if (!var4.isEmpty()) {
               var1.add(var4);
            }
         }
      }

      return Set.copyOf(var1);
   }

   @Override
   public String info() {
      return this.silenced > 0 ? this.silenced + " muted" : "";
   }
}
