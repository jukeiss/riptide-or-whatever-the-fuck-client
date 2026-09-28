package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.MacroArgumentType;
import riptide.commands.args.MultiBotArgumentType;
import riptide.commands.args.MultiProfileArgumentType;
import riptide.gui.screen.RiptideMultiConsoleScreen;
import riptide.gui.screen.RiptideMultiDisclaimerScreen;
import riptide.gui.screen.RiptideMultiScreen;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiProfileManager;

public class MultiCommand extends Command {
   public MultiCommand() {
      super("multi", "Open the Multi menu, or launch/stop a profile.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         openMenu();
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("open").executes(ctx -> {
         openMenu();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("stop").executes(ctx -> {
         stopBatch();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("status").executes(ctx -> {
         status();
         return 1;
      }));
      root.then(((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("launch").executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "multi launch <profile>");
         return 1;
      })).then(RequiredArgumentBuilder.argument("profile", MultiProfileArgumentType.profileName()).executes(ctx -> {
         launch(MultiProfileArgumentType.get(ctx, "profile"));
         return 1;
      })));
      root.then(((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("macro").executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "multi macro <macro> [bot] §7- no bot = all bots");
         return 1;
      })).then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("macro", MacroArgumentType.macroName()).executes(ctx -> {
         runMacro(MacroArgumentType.get(ctx, "macro"), "");
         return 1;
      })).then(RequiredArgumentBuilder.argument("bot", MultiBotArgumentType.botName()).executes(ctx -> {
         runMacro(MacroArgumentType.get(ctx, "macro"), MultiBotArgumentType.get(ctx, "bot"));
         return 1;
      }))));
   }

   private static void runMacro(String macroName, String botName) {
      MultiManager manager = MultiManager.get();
      if (!manager.isActive()) {
         RiptideClientMessaging.sendPrefixed("§7No Multi batch is active.");
      } else {
         RiptideMacro macro = RiptideMacroManager.get().get(macroName);
         if (macro == null) {
            RiptideClientMessaging.sendPrefixed("§cNo macro named: §f" + macroName);
         } else {
            Set<String> scope = manager.scopeForBot(botName);
            if (scope == null) {
               RiptideClientMessaging.sendPrefixed("§cNo bot named: §f" + botName);
               String live = liveBotNames(manager);
               if (!live.isEmpty()) {
                  RiptideClientMessaging.sendPrefixed("§7Bots: §f" + live);
               }
            } else {
               MultiManager.BroadcastResult result = manager.runMacroDirect(macro, scope);
               String target = botName != null && !botName.isBlank() ? botName : "all bots";
               RiptideClientMessaging.sendPrefixed("§aRunning §f" + macro.name + " §aon §f" + target + " §7- " + result.summary());
            }
         }
      }
   }

   private static String liveBotNames(MultiManager manager) {
      StringBuilder sb = new StringBuilder();

      for (MultiManager.BotHandle bot : manager.liveBots()) {
         if (sb.length() > 0) {
            sb.append("§7, §f");
         }

         sb.append(bot.username());
      }

      return sb.toString();
   }

   private static void openMenu() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.execute(() -> {
            if (mc.gui.screen() == null) {
               Runnable proceed = () -> {
                  if (mc.level != null) {
                     RiptideModule module = RiptideModule.get();
                     if (module != null) {
                        module.openMultiUiInGame();
                     }
                  } else if (MultiManager.get().isActive()) {
                     mc.gui.setScreen(new RiptideMultiConsoleScreen(null));
                  } else {
                     mc.gui.setScreen(new RiptideMultiScreen(null, currentServerAddress(mc)));
                  }
               };
               RiptideMultiDisclaimerScreen.open(mc, null, proceed);
            }
         });
      }
   }

   private static void launch(String name) {
      MultiProfile profile = resolveByName(MultiProfileManager.get().all(), name);
      if (profile == null) {
         RiptideClientMessaging.sendPrefixed("§cNo profile named: §f" + name);
         String available = availableNames();
         if (!available.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§7Profiles: §f" + available);
         }
      } else {
         MultiManager.StartResult result = MultiManager.get().start(profile);
         if (result.ok()) {
            RiptideClientMessaging.sendPrefixed(
               "§aLaunching Multi profile: §f"
                  + profile.name
                  + " §7("
                  + profile.sessions.size()
                  + " account(s)) - run "
                  + RiptideCommands.effectivePrefix()
                  + "multi to open the console."
            );
         } else {
            RiptideClientMessaging.sendPrefixed("§cCould not launch: §f" + result.message());
         }
      }
   }

   private static void stopBatch() {
      if (!MultiManager.get().isActive()) {
         RiptideClientMessaging.sendPrefixed("§7No Multi batch is active.");
      } else {
         MultiManager.get().disconnectAll("Stopped via command");
         RiptideClientMessaging.sendPrefixed("§eStopped the Multi batch.");
      }
   }

   private static void status() {
      MultiManager mm = MultiManager.get();
      if (!mm.isActive()) {
         RiptideClientMessaging.sendPrefixed("§7Multi: no batch active.");
      } else {
         MultiProfile active = mm.activeProfile();
         String name = active != null && active.name != null ? active.name : "(unknown)";
         RiptideClientMessaging.sendPrefixed("§aMulti: §f" + name + " §7- §f" + mm.readyCount() + "§7 ready, §f" + mm.connectedCount() + "§7 connected.");
      }
   }

   private static String currentServerAddress(Minecraft mc) {
      ServerData sd = mc.getCurrentServer();
      return sd != null && sd.ip != null ? sd.ip.trim() : "";
   }

   private static String availableNames() {
      StringBuilder sb = new StringBuilder();

      for (MultiProfile p : MultiProfileManager.get().all()) {
         if (p != null && p.name != null) {
            if (sb.length() > 0) {
               sb.append("§7, §f");
            }

            sb.append(p.name);
         }
      }

      return sb.toString();
   }

   public static MultiProfile resolveByName(Collection<MultiProfile> profiles, String name) {
      if (profiles != null && name != null) {
         String want = name.trim().toLowerCase(Locale.ROOT);
         if (want.isEmpty()) {
            return null;
         } else {
            for (MultiProfile p : profiles) {
               if (p != null && p.name != null && p.name.trim().toLowerCase(Locale.ROOT).equals(want)) {
                  return p;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }
}
