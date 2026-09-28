package riptide.modules;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideChatLine;
import riptide.util.RiptideClientMessaging;

public final class AutoResponderModule extends Module {
   private static final int MAX_REMEMBERED = 128;
   private final Map<String, Long> answered = new LinkedHashMap<>();
   private volatile String pendingTarget;
   private volatile long pendingAtMs;

   public AutoResponderModule() {
      super("auto-responder", "AutoResponder", ModuleCategory.MISC, "Replies once to anyone who messages you directly.");
      this.add(new StringSetting("reply", "Reply", "AFK right now, I will get back to you.").description("What to say back.").group("General").build());
      this.add(
         new StringSetting("command", "Reply Command", "/msg")
            .description("How your server sends a direct message. The name is added after it.")
            .group("General")
            .build()
      );
      this.add(
         new StringSetting("phrase", "Message Wording", "whispers to you")
            .description("Text that identifies an incoming direct message. Part of it is enough.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("cooldown", "Cooldown", 120, 5, 3600, 5)
            .unit("s")
            .description("Do not answer the same person again within this.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("delay", "Delay", 1200, 200, 8000, 100)
            .unit("ms")
            .description("Wait before replying, so it does not answer faster than a person could.")
            .group("General")
            .build()
      );
      this.add(new StringListSetting("ignore", "Never Reply To", "").description("Names to say nothing to.").playerNameList().group("General").build());
      this.add(new BoolSetting("chat", "Show Locally", true).description("Print a line here when it replies.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.clear();
   }

   @Override
   public void onDisable() {
      this.clear();
   }

   @Override
   public void onGameJoin() {
      this.clear();
   }

   @Override
   public void onGameLeft() {
      this.clear();
   }

   private void clear() {
      synchronized (this.answered) {
         this.answered.clear();
      }

      this.pendingTarget = null;
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
               if (var5 == null) {
                  return false;
               } else {
                  Minecraft var6 = Minecraft.getInstance();
                  if (var6 != null && var6.getUser() != null && var5.equalsIgnoreCase(var6.getUser().getName())) {
                     return false;
                  } else if (this.isIgnored(var5)) {
                     return false;
                  } else {
                     long var7 = System.currentTimeMillis();
                     if (!this.claim(var5, var7)) {
                        return false;
                     } else {
                        this.pendingTarget = var5;
                        this.pendingAtMs = var7;
                        return false;
                     }
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

   static String sender(String var0) {
      return RiptideChatLine.sender(var0);
   }

   private boolean claim(String var1, long var2) {
      String var4 = var1.toLowerCase(Locale.ROOT);
      long var5 = Math.max(1, this.integer("cooldown")) * 1000L;
      synchronized (this.answered) {
         Long var8 = this.answered.get(var4);
         if (var8 != null && var2 - var8 < var5) {
            return false;
         } else {
            this.answered.put(var4, var2);

            while (this.answered.size() > 128) {
               this.answered.remove(this.answered.keySet().iterator().next());
            }

            return true;
         }
      }
   }

   private boolean isIgnored(String var1) {
      for (String var3 : this.list("ignore")) {
         if (RiptideChatLine.sameName(var3, var1)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void tick() {
      String var1 = this.pendingTarget;
      if (var1 != null) {
         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null && var2.getConnection() != null) {
            if (System.currentTimeMillis() - this.pendingAtMs >= Math.max(0, this.integer("delay"))) {
               this.pendingTarget = null;
               String var3 = this.value("command");
               String var4 = this.value("reply");
               if (var3 != null && !var3.isBlank() && var4 != null && !var4.isBlank()) {
                  this.sendCommand(var3.trim() + " " + var1 + " " + var4.trim());
                  if (this.bool("chat")) {
                     RiptideClientMessaging.sendPrefixed("§7Replied to §f" + var1 + "§7.");
                  }
               }
            }
         } else {
            this.pendingTarget = null;
         }
      }
   }
}
