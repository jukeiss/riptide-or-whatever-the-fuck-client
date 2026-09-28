package riptide.modules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.EntityHitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.MultiManager;

public final class TeamsModule extends Module {
   public static final int DEFAULT_FRIENDS_COLOR = -866779137;
   private static final long BOT_NAME_CACHE_MS = 500L;
   private static TeamsModule instance;
   private String cachedFriendsSource;
   private Set<String> cachedFriendNames = Set.of();
   private Set<String> cachedBotNames = Set.of();
   private long cachedBotNamesAt;
   private boolean pickWasDown;

   public TeamsModule() {
      super("teams", "Teams", ModuleCategory.MISC, "Friends and team detection.");
      instance = this;
      this.add(new StringListSetting("friends", "Friends", "").playerNameList().build());
      this.add(new BoolSetting("auto-teams", "Auto Teams", false).description("Detect teammates automatically.").build());
      this.add(new BoolSetting("multi-bots", "Multi Bots", true).description("Count my bots as teammates").build());
      this.add(new BoolSetting("quick-add", "Quick Add", true).description("Add/remove with key.").build());
      this.add(new KeybindSetting("quick-add-bind", "Quick Add Key", RiptideBindUtil.encodeMouseButton(2)).visibleWhen(() -> this.bool("quick-add")).build());
      this.add(new BoolSetting("esp", "ESP", true).build());
      this.add(new BoolSetting("tracers", "Tracers", true).build());
      this.add(new BoolSetting("nametags", "Nametags", true).build());
      this.add(
         new ColorSetting("friends-color", "Friends Color", -866779137)
            .visibleWhen(() -> this.bool("esp") || this.bool("tracers") || this.bool("nametags"))
            .build()
      );
      this.add(new BoolSetting("aimassist", "AimAssist", false).build());
      this.add(new BoolSetting("killaura", "KillAura", false).build());
      this.add(new BoolSetting("triggerbot", "TriggerBot", false).build());
   }

   @Override
   public void onOptionValueChanged(String settingId) {
      if ("friends".equals(settingId)) {
         this.cachedFriendsSource = null;
      }
   }

   @Override
   public void tick() {
      if (MC != null && MC.player != null && MC.level != null) {
         int bind = this.integer("quick-add-bind");
         boolean down = this.bool("quick-add") && bind != -1 && MC.gui.screen() == null && RiptideBindUtil.isBindPressed(MC, bind);
         if (down && !this.pickWasDown && MC.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player && player != MC.player) {
            this.toggleFriend(player.getScoreboardName());
         }

         this.pickWasDown = down;
      }
   }

   private void toggleFriend(String name) {
      if (name != null && !name.isBlank()) {
         List<String> friends = new ArrayList<>(this.list("friends"));
         String existing = null;

         for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
               existing = friend;
               break;
            }
         }

         if (existing != null) {
            friends.remove(existing);
            RiptideClientMessaging.sendPrefixed("Removed friend " + existing + ".");
         } else {
            friends.add(name);
            RiptideClientMessaging.sendPrefixed("Added friend " + name + ".");
         }

         this.setValue("friends", String.join("|", friends));
      }
   }

   public static boolean addFriend(String name) {
      TeamsModule module = instance;
      if (module != null && name != null && !name.isBlank()) {
         List<String> friends = new ArrayList<>(module.list("friends"));

         for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
               RiptideClientMessaging.sendPrefixed("§e" + friend + " §7is already a friend.");
               return false;
            }
         }

         friends.add(name);
         module.setValue("friends", String.join("|", friends));
         RiptideClientMessaging.sendPrefixed("§aAdded friend §f" + name + "§a.");
         return true;
      } else {
         RiptideClientMessaging.sendPrefixed("§cTeams module unavailable.");
         return false;
      }
   }

   public static boolean removeFriend(String name) {
      TeamsModule module = instance;
      if (module != null && name != null && !name.isBlank()) {
         List<String> friends = new ArrayList<>(module.list("friends"));

         for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
               friends.remove(friend);
               module.setValue("friends", String.join("|", friends));
               RiptideClientMessaging.sendPrefixed("§aRemoved friend §f" + friend + "§a.");
               return true;
            }
         }

         RiptideClientMessaging.sendPrefixed("§cNo friend named §f" + name + "§c.");
         return false;
      } else {
         RiptideClientMessaging.sendPrefixed("§cTeams module unavailable.");
         return false;
      }
   }

   public static boolean clearFriends() {
      TeamsModule module = instance;
      if (module == null) {
         RiptideClientMessaging.sendPrefixed("§cTeams module unavailable.");
         return false;
      } else {
         int count = module.list("friends").size();
         if (count == 0) {
            RiptideClientMessaging.sendPrefixed("§7Friend list is already empty.");
            return false;
         } else {
            module.setValue("friends", "");
            RiptideClientMessaging.sendPrefixed("§aCleared §f" + count + " §afriend" + (count == 1 ? "" : "s") + ".");
            return true;
         }
      }
   }

   public static List<String> storedFriendNames() {
      TeamsModule module = instance;
      return module == null ? List.of() : module.list("friends");
   }

   private static TeamsModule active() {
      TeamsModule module = instance;
      return module != null && module.isEnabled() ? module : null;
   }

   public static boolean isFriendOrTeam(Entity entity) {
      TeamsModule module = active();
      return module != null && entity instanceof Player player && entity != MC.player
         ? module.isListedFriend(player)
            || module.bool("multi-bots") && module.isMultiBot(player)
            || module.bool("auto-teams") && module.isAutoTeammate(player)
         : false;
   }

   public static boolean combatExcluded(Entity entity, String option) {
      TeamsModule module = active();
      if (module == null) {
         return false;
      } else {
         return module.bool(option) ? false : isFriendOrTeam(entity);
      }
   }

   public static boolean visualTargetsFriends(String option) {
      TeamsModule module = active();
      return module != null && module.bool(option);
   }

   public static int friendsColor() {
      TeamsModule module = active();
      return module == null ? -866779137 : ModuleRenderUtil.color(module, "friends-color", -866779137);
   }

   private boolean isListedFriend(Player player) {
      String name = player.getScoreboardName();
      return name != null && !name.isBlank() ? this.friendNames().contains(name.toLowerCase(Locale.ROOT)) : false;
   }

   private Set<String> friendNames() {
      List<String> entries = this.list("friends");
      String source = String.join("|", entries);
      if (source.equals(this.cachedFriendsSource)) {
         return this.cachedFriendNames;
      } else {
         Set<String> names = new LinkedHashSet<>();

         for (String entry : entries) {
            if (entry != null) {
               String name = entry.trim().toLowerCase(Locale.ROOT);
               if (!name.isEmpty()) {
                  names.add(name);
               }
            }
         }

         this.cachedFriendsSource = source;
         this.cachedFriendNames = Set.copyOf(names);
         return this.cachedFriendNames;
      }
   }

   private boolean isMultiBot(Player player) {
      String name = player.getScoreboardName();
      return name != null && !name.isBlank() ? this.botNames().contains(name.toLowerCase(Locale.ROOT)) : false;
   }

   private Set<String> botNames() {
      long now = System.currentTimeMillis();
      if (this.cachedBotNamesAt != 0L && now - this.cachedBotNamesAt < 500L) {
         return this.cachedBotNames;
      } else {
         this.cachedBotNamesAt = now;
         this.cachedBotNames = MultiManager.get().botUsernamesLower();
         return this.cachedBotNames;
      }
   }

   private boolean isAutoTeammate(Player suspected) {
      LocalPlayer self = MC.player;
      if (self == null) {
         return false;
      } else if (self.isAlliedTo(suspected)) {
         return true;
      } else {
         Component ownName = self.getDisplayName();
         Component theirName = suspected.getDisplayName();
         TextColor ownColor = ownName == null ? null : ownName.getStyle().getColor();
         TextColor theirColor = theirName == null ? null : theirName.getStyle().getColor();
         if (ownColor != null && ownColor.equals(theirColor)) {
            return true;
         } else {
            Integer ownHelmet = dyedColor(self);
            Integer theirHelmet = dyedColor(suspected);
            return ownHelmet != null && ownHelmet.equals(theirHelmet);
         }
      }
   }

   private static Integer dyedColor(Player player) {
      ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
      if (stack != null && !stack.isEmpty()) {
         DyedItemColor dyed = (DyedItemColor)stack.get(DataComponents.DYED_COLOR);
         return dyed == null ? null : dyed.rgb() & 16777215;
      } else {
         return null;
      }
   }
}
