package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideChatLine;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class AutoTpAcceptModule extends Module {
   private static final String LIST = "My List";
   private static final String FRIENDS = "Friends";
   private static final String BOTH = "Both";
   private final RiptideSettingCache<Set<String>> allowed = new RiptideSettingCache<>(AutoTpAcceptModule::lowerCased);
   private volatile String lastAcceptedLower = "";
   private volatile long lastAcceptedMs;
   private volatile String pendingCommand;

   public AutoTpAcceptModule() {
      super("auto-tpaccept", "AutoTpAccept", ModuleCategory.MISC, "Accepts teleport requests from people you have listed.");
      this.add(
         new ChoiceSetting("who", "Accept From", "My List", "My List", "Friends", "Both")
            .description("Whose requests to accept. There is no option for everybody.")
            .group("General")
            .build()
      );
      this.add(
         new StringListSetting("players", "My List", "")
            .description("Names to accept from.")
            .playerNameList()
            .visibleWhen(() -> !"Friends".equals(this.choice("who")))
            .group("General")
            .build()
      );
      this.add(
         new StringSetting("command", "Accept Command", "/tpaccept")
            .description("What to send. Change it if your server words it differently.")
            .group("General")
            .build()
      );
      this.add(
         new StringSetting("phrase", "Request Wording", "has requested to teleport")
            .description("Text that identifies a request line. Part of the sentence is enough.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("delay", "Delay", 700, 0, 5000, 100)
            .unit("ms")
            .description("Wait this long before accepting, so it does not answer instantly.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("chat", "Say Why", true).description("Print a line when it accepts one.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.pendingCommand = null;
      this.lastAcceptedLower = "";
   }

   @Override
   public void onGameJoin() {
      this.onEnable();
   }

   @Override
   public void onGameLeft() {
      this.onEnable();
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundSystemChatPacket var2) {
         String var3 = this.value("phrase");
         if (var3 != null && !var3.isBlank()) {
            String var4;
            try {
               var4 = var2.content().getString();
            } catch (Throwable var9) {
               return false;
            }

            if (var4 == null || var4.isEmpty()) {
               return false;
            } else if (!RiptideChatLine.contains(var4, var3)) {
               return false;
            } else {
               String var5 = RiptideChatLine.senderBefore(var4, var3);
               if (var5 != null && this.isAllowed(var5)) {
                  long var6 = System.currentTimeMillis();
                  String var8 = var5.toLowerCase(Locale.ROOT);
                  if (var8.equals(this.lastAcceptedLower) && var6 - this.lastAcceptedMs < 5000L) {
                     return false;
                  } else {
                     this.lastAcceptedLower = var8;
                     this.lastAcceptedMs = var6;
                     this.pendingCommand = var5;
                     return false;
                  }
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static String requester(String var0) {
      return RiptideChatLine.sender(var0);
   }

   private boolean isAllowed(String var1) {
      String var2 = this.choice("who");
      String var3 = var1.toLowerCase(Locale.ROOT);
      if (!"Friends".equals(var2) && this.allowed.get(this.list("players")).contains(var3)) {
         return true;
      } else if ("My List".equals(var2)) {
         return false;
      } else {
         try {
            for (String var5 : TeamsModule.storedFriendNames()) {
               if (RiptideChatLine.sameName(var5, var1)) {
                  return true;
               }
            }

            return false;
         } catch (RuntimeException var6) {
            return false;
         }
      }
   }

   @Override
   public void tick() {
      String var1 = this.pendingCommand;
      if (var1 != null) {
         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null && var2.getConnection() != null) {
            if (System.currentTimeMillis() - this.lastAcceptedMs >= Math.max(0, this.integer("delay"))) {
               this.pendingCommand = null;
               String var3 = this.value("command");
               if (var3 != null && !var3.isBlank()) {
                  this.sendCommand(var3.trim());
                  if (this.bool("chat")) {
                     RiptideClientMessaging.sendPrefixed("§aAccepted §f" + var1 + "§a.");
                  }
               }
            }
         } else {
            this.pendingCommand = null;
         }
      }
   }

   static Set<String> lowerCased(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            var1.add(var3.trim().toLowerCase(Locale.ROOT));
         }
      }

      return Set.copyOf(var1);
   }
}
