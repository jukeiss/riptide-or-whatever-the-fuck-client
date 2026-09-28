package riptide.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptidePlayerScanner;

public final class StaffListModule extends Module {
   private static final String DEFAULT_RANKS = "owner, co-owner, admin, sr-admin, manager, developer, dev, sr-mod, mod, moderator, jr-mod, helper, trainee, staff, support, builder";
   // Small-caps and other "fancy" letters servers use for rank tags, mapped back to ASCII.
   private static final String FANCY = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ";
   private static final String PLAIN = "abcdefghijklmnopqrstuvwyz";
   private static StaffListModule cached;
   private final Map<String, String> online = new LinkedHashMap<>();
   private int scanCooldown;
   private boolean primed;

   public StaffListModule() {
      super("staff-list", "Staff List", ModuleCategory.MISC, "Lists online staff from the tab list and tells you when they join or leave.");
      this.add(
         new StringListSetting("ranks", "Staff Ranks", DEFAULT_RANKS)
            .description("Rank words that count as staff. Matched as whole words against the tab-list/team prefix.")
            .group("Detection")
            .build()
      );
      this.add(
         new StringListSetting("names", "Always Staff", "")
            .description("Players to treat as staff no matter their prefix (for servers that use icon ranks).")
            .playerNameList()
            .group("Detection")
            .build()
      );
      this.add(new BoolSetting("hud", "Show Panel", true).description("Draw the list on screen.").group("Display").build());
      this.add(new BoolSetting("show-rank", "Show Rank", true).description("Show each staff member's rank next to their name.").group("Display").build());
      this.add(new BoolSetting("hide-empty", "Hide When None", false).description("Hide the panel when no staff are online.").group("Display").build());
      this.add(
         new ChoiceSetting("corner", "Corner", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right")
            .description("Which corner the panel sits in.")
            .group("Display")
            .build()
      );
      this.add(new IntSetting("margin", "Margin", 4, 0, 300, 1).description("Gap from the screen edge, in pixels.").group("Display").build());
      this.add(new BoolSetting("on-join", "Alert On Join", true).description("Say something when staff come online.").group("Alerts").build());
      this.add(new BoolSetting("on-leave", "Alert On Leave", true).description("Say something when staff go offline.").group("Alerts").build());
      this.add(new BoolSetting("sound", "Play Sound", true).description("Ping when staff come online.").group("Alerts").build());
      this.add(new ColorSetting("c-bg", "Background", -1879048192).group("Colors").description("Panel backing. 0 alpha removes it.").build());
      this.add(new ColorSetting("c-title", "Title", -43691).group("Colors").build());
      this.add(new ColorSetting("c-text", "Name", -1).group("Colors").build());
      this.add(new ColorSetting("c-rank", "Rank", -4602154).group("Colors").build());
   }

   private static StaffListModule instance() {
      StaffListModule module = cached;
      if (module == null && ModuleRegistry.get("staff-list") instanceof StaffListModule found) {
         module = found;
         cached = found;
      }

      return module;
   }

   @Override
   public void onEnable() {
      this.forget();
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   @Override
   public void onGameJoin() {
      this.forget();
   }

   @Override
   public void onGameLeft() {
      this.forget();
   }

   private void forget() {
      this.online.clear();
      this.scanCooldown = 0;
      this.primed = false;
   }

   @Override
   public String info() {
      return this.online.isEmpty() ? "" : String.valueOf(this.online.size());
   }

   @Override
   public void tick() {
      if (MC.player == null || MC.getConnection() == null) {
         this.forget();
      } else if (--this.scanCooldown <= 0) {
         // The scan walks the whole tab list; once a second is plenty.
         this.scanCooldown = 20;
         Map<String, String> now = this.scan();
         if (this.primed) {
            if (this.bool("on-join")) {
               for (Map.Entry<String, String> entry : now.entrySet()) {
                  if (!this.online.containsKey(entry.getKey())) {
                     String rank = entry.getValue().isEmpty() ? "" : " §7(" + entry.getValue() + "§7)";
                     RiptideClientMessaging.sendPrefixed("§cStaff online: §f" + entry.getKey() + rank);
                     if (this.bool("sound")) {
                        ping();
                     }
                  }
               }
            }

            if (this.bool("on-leave")) {
               for (String name : this.online.keySet()) {
                  if (!now.containsKey(name)) {
                     RiptideClientMessaging.sendPrefixed("§7Staff offline: " + name);
                  }
               }
            }
         }

         // First scan after joining just records who is already on, so you aren't spammed on login.
         this.primed = true;
         this.online.clear();
         this.online.putAll(now);
      }
   }

   private Map<String, String> scan() {
      Set<String> ranks = new HashSet<>();

      for (String rank : this.list("ranks")) {
         String normalized = normalize(rank).trim();
         if (!normalized.isEmpty()) {
            ranks.add(normalized);
         }
      }

      Set<String> forced = new HashSet<>();

      for (String name : this.list("names")) {
         if (name != null && !name.isBlank()) {
            forced.add(name.trim().toLowerCase(Locale.ROOT));
         }
      }

      Map<String, String> found = new LinkedHashMap<>();

      for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(MC)) {
         String prefix = player.prefix() == null ? "" : player.prefix().trim();
         if (forced.contains(player.name().toLowerCase(Locale.ROOT)) || isStaffRank(prefix, ranks)) {
            found.put(player.name(), prefix);
         }
      }

      return found;
   }

   private static boolean isStaffRank(String prefix, Set<String> ranks) {
      if (prefix.isEmpty() || ranks.isEmpty()) {
         return false;
      } else {
         String text = normalize(prefix);
         if (ranks.contains(text.trim())) {
            return true;
         } else {
            // Whole words only, so "mod" doesn't match "modern" or a clan tag that happens to contain it.
            for (String word : text.split("[^a-z0-9-]+")) {
               if (!word.isEmpty() && ranks.contains(word)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private static String normalize(String text) {
      StringBuilder out = new StringBuilder(text.length());
      String lower = RiptidePlayerScanner.stripFormatting(text).toLowerCase(Locale.ROOT);

      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         int fancy = FANCY.indexOf(c);
         char mapped = fancy >= 0 ? PLAIN.charAt(fancy) : c;
         out.append(mapped == '.' || mapped == '_' ? '-' : mapped);
      }

      return out.toString();
   }

   private static void ping() {
      try {
         MC.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.8F);
      } catch (Throwable var1) {
      }
   }

   public static void render(GuiGraphicsExtractor graphics) {
      StaffListModule module = instance();
      if (module != null && module.isEnabled() && !PackHideState.isActive() && module.bool("hud")) {
         if (MC != null && MC.player != null && !MC.gui.hud.isHidden()) {
            try {
               module.draw(graphics);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private void draw(GuiGraphicsExtractor graphics) {
      if (!this.online.isEmpty() || !this.bool("hide-empty")) {
         Font font = MC.font;
         boolean showRank = this.bool("show-rank");
         List<String> names = new ArrayList<>(this.online.keySet());
         String title = "Staff (" + names.size() + ")";
         int pad = 3;
         int lineHeight = 10;
         int width = font.width(title);

         for (String name : names) {
            String rank = this.online.get(name);
            width = Math.max(width, font.width(name) + (showRank && !rank.isEmpty() ? font.width(" " + rank) : 0));
         }

         int panelW = width + pad * 2;
         int panelH = (names.size() + 1) * lineHeight + pad * 2 - (lineHeight - 9);
         String corner = this.choice("corner");
         int margin = this.integer("margin");
         int x = corner.contains("Right") ? graphics.guiWidth() - margin - panelW : margin;
         int y = HudStack.y(corner, margin, panelH, graphics.guiHeight());
         int bg = ModuleRenderUtil.color(this, "c-bg", -1879048192);
         int titleColor = ModuleRenderUtil.color(this, "c-title", -43691) | 0xFF000000;
         int nameColor = ModuleRenderUtil.color(this, "c-text", -1) | 0xFF000000;
         int rankColor = ModuleRenderUtil.color(this, "c-rank", -4602154) | 0xFF000000;
         if (bg >>> 24 != 0) {
            graphics.fill(x, y, x + panelW, y + panelH, bg);
         }

         int lineY = y + pad;
         graphics.text(font, Component.literal(title), x + pad, lineY, titleColor);

         for (String name : names) {
            lineY += lineHeight;
            graphics.text(font, Component.literal(name), x + pad, lineY, nameColor);
            String rank = this.online.get(name);
            if (showRank && !rank.isEmpty()) {
               graphics.text(font, Component.literal(" " + rank), x + pad + font.width(name), lineY, rankColor);
            }
         }
      }
   }
}
