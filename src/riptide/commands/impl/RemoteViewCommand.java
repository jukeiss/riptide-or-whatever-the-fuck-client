package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptidePlayerScanner;
import riptide.util.RiptideRemoteView;

public final class RemoteViewCommand extends Command {
   public RemoteViewCommand() {
      super("rv", "Watch a loaded player's POV.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> openLookedAt());
      root.then(LiteralArgumentBuilder.literal("stop").executes(ctx -> {
         if (!RiptideRemoteView.isActive()) {
            RiptideClientMessaging.sendPrefixed("§7Remote view is not active.");
         } else {
            RiptideRemoteView.stop(false);
            RiptideClientMessaging.sendPrefixed("§aRemote view stopped.");
         }

         return 1;
      }));
      root.then(
         RequiredArgumentBuilder.argument("player", StringArgumentType.greedyString())
            .suggests(RemoteViewCommand::suggestPlayers)
            .executes(ctx -> openNamed(StringArgumentType.getString(ctx, "player")))
      );
   }

   private static int openLookedAt() {
      Minecraft mc = Minecraft.getInstance();
      Entity entity = mc == null ? null : mc.crosshairPickEntity;
      if (!(entity instanceof Player) && mc != null && mc.hitResult instanceof EntityHitResult hit) {
         entity = hit.getEntity();
      }

      if (entity instanceof Player player) {
         report(RiptideRemoteView.start(player));
         return 1;
      } else {
         RiptideClientMessaging.sendPrefixed("§cLook at a loaded player first.");
         return 1;
      }
   }

   private static int openNamed(String requested) {
      Player player = findLoadedPlayer(Minecraft.getInstance(), requested);
      if (player == null) {
         RiptideClientMessaging.sendPrefixed("§cPlayer is not loaded: §f" + requested.trim());
         return 1;
      } else {
         report(RiptideRemoteView.start(player));
         return 1;
      }
   }

   static Player findLoadedPlayer(Minecraft mc, String requested) {
      if (mc != null && mc.level != null && requested != null) {
         String wanted = requested.trim();
         if (wanted.isEmpty()) {
            return null;
         } else {
            for (Player player : mc.level.players()) {
               if (player != null) {
                  String profile = player.getGameProfile() == null ? "" : player.getGameProfile().name();
                  String shown = player.getName().getString();
                  if (wanted.equalsIgnoreCase(profile) || wanted.equalsIgnoreCase(shown)) {
                     return player;
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static void report(RiptideRemoteView.Result result) {
      RiptideClientMessaging.sendPrefixed((result.ok() ? "§a" : "§c") + result.message());
   }

   private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<RiptideCommandSource> ignored, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(Minecraft.getInstance())) {
         if (player.name().toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(player.name());
         }
      }

      return builder.buildFuture();
   }
}
