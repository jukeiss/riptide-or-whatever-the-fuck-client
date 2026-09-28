package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;
import riptide.util.multi.PacketTeleportController;

public final class TpCommand extends Command {
   public TpCommand() {
      super("tp", "Ground-first HClip teleport.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         show(PacketTeleportController.executeMain(""));
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("arguments", StringArgumentType.greedyString()).suggests((ctx, suggestions) -> {
         String remaining = suggestions.getRemaining();
         String bare = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
         String lower = bare.toLowerCase(Locale.ROOT);

         for (String value : new String[]{"~ ~ ~", "stop", "status", "config 20 500", "reset"}) {
            if (value.startsWith(remaining)) {
               suggestions.suggest(value);
            }
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            for (RiptideWaypoints.Waypoint wp : RiptideWaypoints.get().list(RiptideWaypoints.scopeKey(mc))) {
               String name = wp.name();
               if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                  suggestions.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
               }
            }
         }

         return suggestions.buildFuture();
      }).executes(ctx -> {
         show(PacketTeleportController.executeMain(resolveWaypoint(StringArgumentType.getString(ctx, "arguments"))));
         return 1;
      }));
   }

   private static String resolveWaypoint(String args) {
      String trimmed = args == null ? "" : args.trim();
      if (trimmed.isEmpty()) {
         return args;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null) {
            return args;
         } else {
            String scope = RiptideWaypoints.scopeKey(mc);
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
               return toCoords(RiptideWaypoints.get().find(scope, trimmed.substring(1, trimmed.length() - 1)), args);
            } else {
               String lower = trimmed.toLowerCase(Locale.ROOT);
               return !lower.equals("stop") && !lower.equals("status") && !lower.equals("reset") && !lower.startsWith("config") && !looksLikeCoords(trimmed)
                  ? toCoords(RiptideWaypoints.get().find(scope, trimmed), args)
                  : args;
            }
         }
      }
   }

   private static String toCoords(RiptideWaypoints.Waypoint wp, String fallback) {
      return wp == null ? fallback : wp.x() + " " + wp.y() + " " + wp.z();
   }

   private static boolean looksLikeCoords(String value) {
      String[] parts = value.split("\\s+");
      if (parts.length < 3) {
         return false;
      } else {
         for (int i = 0; i < 3; i++) {
            if (!parts[i].startsWith("~")) {
               try {
                  Double.parseDouble(parts[i]);
               } catch (NumberFormatException var4) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private static void show(String result) {
      String text = result != null && !result.isBlank() ? result : "Usage: " + RiptideCommands.effectivePrefix() + "tp <x> <y> <z> [maxPackets] [pauseMs]";
      String color = !text.startsWith("TP started") && !text.startsWith("TP defaults")
         ? (!text.startsWith("Usage") && !text.startsWith("No active") ? "§7" : "§e")
         : "§a";
      RiptideClientMessaging.sendPrefixed(color + text);
   }
}
