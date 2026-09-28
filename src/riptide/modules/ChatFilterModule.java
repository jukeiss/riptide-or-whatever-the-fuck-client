package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class ChatFilterModule extends Module {
   private static final String DEFAULT_FILTERS = String.join("|", "is selling", "buying at", "/shop", "discord.gg", "join my");
   private final RiptideSettingCache<List<String>> filters = new RiptideSettingCache<>(this::parsePhrases);
   private volatile int hidden;

   public ChatFilterModule() {
      super("chat-filter", "ChatFilter", ModuleCategory.MISC, "Hides server chat messages containing phrases you choose.");
      this.add(
         new StringListSetting("filters", "Phrases", DEFAULT_FILTERS).description("A message containing any of these is hidden.").group("General").build()
      );
      this.add(new BoolSetting("case-sensitive", "Match Case", false).description("Off means BUYING and buying both match.").group("General").build());
      this.add(new BoolSetting("count", "Count Hidden", true).description("Keep a running total of what has been hidden.").group("General").build());
      this.add(
         new ActionSetting("report", "Hidden So Far", this::reportHidden)
            .buttonLabel("Show")
            .description("Say how many messages have been hidden since the module was switched on.")
            .visibleWhen(() -> this.bool("count"))
            .group("General")
            .build()
      );
   }

   private void reportHidden() {
      int var1 = this.hiddenCount();
      if (var1 == 0) {
         RiptideClientMessaging.sendPrefixed("§7Nothing hidden yet.");
      } else {
         RiptideClientMessaging.sendPrefixed("§f" + var1 + " §7message" + (var1 == 1 ? "" : "s") + " hidden this session.");
      }
   }

   @Override
   public void onEnable() {
      this.hidden = 0;
   }

   @Override
   public boolean onPacketReceive(Packet<?> var1) {
      if (var1 instanceof ClientboundSystemChatPacket var2) {
         Component var3 = var2.content();
         if (var3 == null) {
            return false;
         } else {
            String var4 = var3.getString();
            if (var4 == null || var4.isEmpty()) {
               return false;
            } else if (!this.matches(var4)) {
               return false;
            } else {
               if (this.bool("count")) {
                  this.hidden++;
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean matches(String var1) {
      List var2 = this.filters();
      if (var2.isEmpty()) {
         return false;
      } else {
         String var3 = this.bool("case-sensitive") ? var1 : var1.toLowerCase(Locale.ROOT);

         for (int var4 = 0; var4 < var2.size(); var4++) {
            if (var3.contains((CharSequence)var2.get(var4))) {
               return true;
            }
         }

         return false;
      }
   }

   private List<String> filters() {
      return this.filters.get(this.list("filters"), this.bool("case-sensitive") ? "case" : "nocase");
   }

   private List<String> parsePhrases(List<String> var1) {
      boolean var2 = this.bool("case-sensitive");
      ArrayList var3 = new ArrayList();

      for (String var5 : var1) {
         if (var5 != null) {
            String var6 = var5.trim();
            if (!var6.isEmpty()) {
               var3.add(var2 ? var6 : var6.toLowerCase(Locale.ROOT));
            }
         }
      }

      return List.copyOf(var3);
   }

   public int hiddenCount() {
      return this.hidden;
   }
}
