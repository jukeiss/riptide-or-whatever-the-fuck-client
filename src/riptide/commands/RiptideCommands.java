package riptide.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import riptide.addons.AddonManager;
import riptide.api.AddonRegistrationResult;
import riptide.commands.impl.BindCommand;
import riptide.commands.impl.BindsCommand;
import riptide.commands.impl.ChangeSlotCommand;
import riptide.commands.impl.ClearCommand;
import riptide.commands.impl.ClickItemCommand;
import riptide.commands.impl.ClickSlotCommand;
import riptide.commands.impl.CommandsCommand;
import riptide.commands.impl.CopyPosCommand;
import riptide.commands.impl.DamageCommand;
import riptide.commands.impl.DelayCommand;
import riptide.commands.impl.DisconnectCommand;
import riptide.commands.impl.DismountCommand;
import riptide.commands.impl.DropCommand;
import riptide.commands.impl.FriendCommand;
import riptide.commands.impl.GiveCommand;
import riptide.commands.impl.HClipCommand;
import riptide.commands.impl.HelpCommand;
import riptide.commands.impl.MacroCommand;
import riptide.commands.impl.ModulesCommand;
import riptide.commands.impl.MultiCommand;
import riptide.commands.impl.NameScrapeCommand;
import riptide.commands.impl.NbtCommand;
import riptide.commands.impl.PluginsCommand;
import riptide.commands.impl.PrefixCommand;
import riptide.commands.impl.RemoteViewCommand;
import riptide.commands.impl.SayCommand;
import riptide.commands.impl.SendCommand;
import riptide.commands.impl.ServerCommand;
import riptide.commands.impl.SyncCommand;
import riptide.commands.impl.ToggleCommand;
import riptide.commands.impl.TpCommand;
import riptide.commands.impl.VClipCommand;
import riptide.commands.impl.WaypointsCommand;
import riptide.commands.impl.XCarryCommand;
import riptide.modules.PackHideState;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideLiteVariant;
import riptide.util.multi.MultiPovCommandRouter;

public final class RiptideCommands {
   private static final CommandDispatcher<RiptideCommandSource> DISPATCHER = new CommandDispatcher();
   private static final List<Command> ALL = new ArrayList<>();
   private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
   private static final Map<String, String> ADDON_COMMAND_OWNERS = new LinkedHashMap<>();
   private static final Map<Command, String> ADDON_COMMAND_OBJECT_OWNERS = new IdentityHashMap<>();
   private static final Set<String> DISABLED_ADDON_COMMAND_NAMES = new HashSet<>();
   private static final List<String> PANIC_PREFIX_FALLBACKS = List.of(
      ".", ",", ";", ":", "'", "\"", "\\", "|", "-", "_", "+", "=", "*", "#", "@", "!", "$", "%", "&", "~"
   );
   private static boolean initialized = false;
   private static int revision;
   private static final ThreadLocal<Boolean> PLAIN_CHAT_BYPASS = ThreadLocal.withInitial(() -> false);

   private RiptideCommands() {
   }

   public static synchronized void init() {
      if (!initialized) {
         initialized = true;
         register(new MacroCommand());
         register(new SyncCommand());
         register(new DelayCommand());
         register(new SendCommand());
         register(new VClipCommand());
         register(new HClipCommand());
         register(new TpCommand());
         register(new CopyPosCommand());
         register(new NbtCommand());
         register(new ServerCommand());
         register(new PluginsCommand());
         register(new PrefixCommand());
         register(new CommandsCommand());
         register(new HelpCommand());
         register(new BindsCommand());
         register(new BindCommand());
         register(new ClearCommand());
         register(new DismountCommand());
         register(new DisconnectCommand());
         register(new SayCommand());
         register(new DropCommand());
         register(new ChangeSlotCommand());
         register(new ClickSlotCommand());
         register(new ClickItemCommand());
         register(new GiveCommand());
         register(new DamageCommand());
         if (!RiptideLiteVariant.enabled()) {
            register(new MultiCommand());
            register(new ToggleCommand());
            register(new ModulesCommand());
            register(new FriendCommand());
            register(new WaypointsCommand());
            register(new XCarryCommand());
            register(new RemoteViewCommand());
            register(new NameScrapeCommand());
         }
      }
   }

   private static void register(Command var0) {
      registerWithName(var0, var0.name(), true);
   }

   private static List<String> registerWithName(Command var0, String var1, boolean var2) {
      ArrayList var3 = new ArrayList();
      revision++;
      ALL.add(var0);
      LiteralArgumentBuilder var4 = LiteralArgumentBuilder.literal(var1);
      var0.build(var4);
      DISPATCHER.register(var4);
      BY_NAME.put(var1.toLowerCase(Locale.ROOT), var0);
      DISABLED_ADDON_COMMAND_NAMES.remove(var1.toLowerCase(Locale.ROOT));
      var3.add(var1.toLowerCase(Locale.ROOT));
      if (!var2) {
         return var3;
      } else {
         for (String var8 : var0.aliases()) {
            if (var8 != null && !var8.isBlank() && !BY_NAME.containsKey(var8.toLowerCase(Locale.ROOT))) {
               LiteralArgumentBuilder var9 = LiteralArgumentBuilder.literal(var8);
               var0.build(var9);
               DISPATCHER.register(var9);
               BY_NAME.put(var8.toLowerCase(Locale.ROOT), var0);
               DISABLED_ADDON_COMMAND_NAMES.remove(var8.toLowerCase(Locale.ROOT));
               var3.add(var8.toLowerCase(Locale.ROOT));
            }
         }

         return var3;
      }
   }

   public static synchronized void registerAddonCommand(Command var0, String var1) {
      registerAddonCommandDetailed(var0, var1);
   }

   public static synchronized AddonRegistrationResult registerAddonCommandDetailed(Command var0, String var1) {
      if (var0 == null) {
         return AddonRegistrationResult.rejected("command", "", "command was null");
      } else if (var1 != null && !var1.isBlank()) {
         String var2 = var0.name();
         if (var2 != null && !var2.isBlank()) {
            boolean var3 = BY_NAME.containsKey(var2.toLowerCase(Locale.ROOT));
            List var4;
            if (var3) {
               String var5 = (var1 != null && !var1.isBlank() ? var1 : "addon") + ":" + var2;
               riptide.RiptideClientAddon.LOG
                  .warn(
                     "[Commands] Command name '{}' from addon '{}' collides with an existing command; registering it as '{}' instead",
                     new Object[]{var2, var1, var5}
                  );
               var4 = registerWithName(var0, var5, false);
            } else {
               var4 = registerWithName(var0, var2, true);
            }

            ADDON_COMMAND_OBJECT_OWNERS.put(var0, var1);

            for (String var6 : var4) {
               ADDON_COMMAND_OWNERS.put(var6, var1);
            }

            String var8 = var4.isEmpty() ? var2 : (String)var4.get(0);
            AddonManager.recordAcceptedRegistration("command", var8);
            return AddonRegistrationResult.accepted("command", var8);
         } else {
            return rejectAddonCommand(var1, "", "blank command name");
         }
      } else {
         return rejectAddonCommand(var1, "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
      }
   }

   private static AddonRegistrationResult rejectAddonCommand(String var0, String var1, String var2) {
      riptide.RiptideClientAddon.LOG.warn("[Commands] Rejecting addon command '{}': {}", var1, var2);
      AddonManager.recordRejectedRegistration(var0, "command", var1, var2);
      return AddonRegistrationResult.rejected("command", var1, var2);
   }

   public static synchronized void unregisterAddonCommands(String var0) {
      if (var0 != null && !var0.isBlank()) {
         ArrayList var1 = new ArrayList();

         for (Entry var3 : ADDON_COMMAND_OWNERS.entrySet()) {
            if (var0.equals(var3.getValue())) {
               var1.add((String)var3.getKey());
            }
         }

         for (String var5 : var1) {
            BY_NAME.remove(var5);
            ADDON_COMMAND_OWNERS.remove(var5);
            DISABLED_ADDON_COMMAND_NAMES.add(var5);
         }

         ALL.removeIf(var1x -> var0.equals(ADDON_COMMAND_OBJECT_OWNERS.get(var1x)));
         ADDON_COMMAND_OBJECT_OWNERS.entrySet().removeIf(var1x -> var0.equals(var1x.getValue()));
         if (!var1.isEmpty()) {
            revision++;
         }
      }
   }

   public static CommandDispatcher<RiptideCommandSource> dispatcher() {
      return DISPATCHER;
   }

   public static int revision() {
      return revision;
   }

   public static List<Command> all() {
      return Collections.unmodifiableList(ALL);
   }

   public static Command find(String var0) {
      return var0 == null ? null : BY_NAME.get(var0.trim().toLowerCase(Locale.ROOT));
   }

   public static String effectivePrefix() {
      return RiptideCompatManager.effectiveCommandPrefix();
   }

   public static boolean isRiptideCommandMessage(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.trim();
         String var2 = effectivePrefix();
         return !var2.isEmpty() && var1.startsWith(var2);
      } else {
         return false;
      }
   }

   public static boolean commandsBlockedByPanic() {
      return PackHideState.isHardLocked();
   }

   public static boolean isBlockedPanicCommandMessage(String var0) {
      return commandsBlockedByPanic() && isPanicBlockedCommandMessage(var0);
   }

   private static boolean isPanicBlockedCommandMessage(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.trim();
         if ("^toggleriptide".equalsIgnoreCase(var1)) {
            return true;
         } else {
            String var2 = effectivePrefix();
            if (!var2.isEmpty() && var1.startsWith(var2)) {
               return true;
            } else {
               for (String var4 : PANIC_PREFIX_FALLBACKS) {
                  if (var4 != null && !var4.isEmpty() && !var4.equals(var2) && var1.startsWith(var4)) {
                     String var5 = var1.substring(var4.length()).trim();
                     if (!var5.isEmpty()) {
                        String var6 = firstToken(var5).toLowerCase(Locale.ROOT);
                        if (BY_NAME.containsKey(var6) || DISABLED_ADDON_COMMAND_NAMES.contains(var6)) {
                           return true;
                        }
                     }
                  }
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static String commandBody(String var0) {
      if (!isRiptideCommandMessage(var0)) {
         return "";
      } else {
         String var1 = var0.trim();
         int var2 = effectivePrefix().length();
         return var1.length() <= var2 ? "" : var1.substring(var2).trim();
      }
   }

   public static boolean plainChatBypass() {
      return PLAIN_CHAT_BYPASS.get();
   }

   public static void sendPlainChat(ClientPacketListener var0, String var1) {
      PLAIN_CHAT_BYPASS.set(true);

      try {
         var0.sendChat(var1);
      } finally {
         PLAIN_CHAT_BYPASS.remove();
      }
   }

   public static boolean dispatch(String var0) {
      if (commandsBlockedByPanic()) {
         return true;
      } else if (var0 == null) {
         return false;
      } else {
         String var1 = var0.trim();
         if (var1.isEmpty()) {
            return false;
         } else if (DISABLED_ADDON_COMMAND_NAMES.contains(firstToken(var1).toLowerCase(Locale.ROOT))) {
            sendSyntaxError(var1, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create());
            return true;
         } else {
            try {
               if (!RiptideLiteVariant.enabled() && MultiPovCommandRouter.route(var1)) {
                  return true;
               } else {
                  DISPATCHER.execute(var1, RiptideCommandSource.INSTANCE);
                  return true;
               }
            } catch (CommandSyntaxException var3) {
               sendSyntaxError(var1, var3);
               return true;
            } catch (Throwable var4) {
               riptide.RiptideClientAddon.LOG.warn("[Commands] dispatch failed for '{}'", var1, var4);
               RiptideClientMessaging.sendPrefixed("§cCommand error: " + (var4.getMessage() == null ? var4.getClass().getSimpleName() : var4.getMessage()));
               return true;
            }
         }
      }
   }

   private static void sendSyntaxError(String var0, CommandSyntaxException var1) {
      String var2 = firstToken(var0);
      Command var3 = find(var2);
      String var4 = effectivePrefix();
      if (var3 == null) {
         RiptideClientMessaging.sendPrefixed("§cUnknown RIPTIDE command: §f" + var2);
         RiptideClientMessaging.sendPrefixed("§7Use §f" + var4 + "commands §7or §f" + var4 + "help§7.");
      } else {
         String var5 = var1.getMessage();
         if (var5 == null || var5.isBlank()) {
            var5 = "Incomplete or invalid command.";
         }

         RiptideClientMessaging.sendPrefixed("§c" + var5);
         RiptideClientMessaging.sendPrefixed("§7Use §f" + var4 + "help " + var3.name() + "§7.");
      }
   }

   private static String firstToken(String var0) {
      if (var0 == null) {
         return "";
      } else {
         String var1 = var0.trim();
         if (var1.isEmpty()) {
            return "";
         } else {
            int var2 = var1.indexOf(32);
            return var2 < 0 ? var1 : var1.substring(0, var2);
         }
      }
   }
}
