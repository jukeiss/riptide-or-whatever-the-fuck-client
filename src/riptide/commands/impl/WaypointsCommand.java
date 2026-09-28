package riptide.commands.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.gui.screen.RiptideWaypointsScreen;
import riptide.modules.ModuleRegistry;
import riptide.modules.WaypointsModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;

public final class WaypointsCommand extends Command {
   private static final int FALLBACK_DEFAULT_COLOR = -12855297;

   public WaypointsCommand() {
      super("waypoints", "Save and manage waypoints.", "wp");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> this.addCurrentPosition());
      root.then(LiteralArgumentBuilder.literal("gui").executes(ctx -> this.openGui()));
      root.then(LiteralArgumentBuilder.literal("list").executes(ctx -> this.openGui()));
      root.then(LiteralArgumentBuilder.literal("clear").executes(ctx -> this.clearScope()));
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("del").executes(ctx -> this.usage()))
            .then(
               RequiredArgumentBuilder.argument("name", StringArgumentType.greedyString())
                  .executes(ctx -> this.delete(StringArgumentType.getString(ctx, "name")))
            )
      );
      root.then(
         RequiredArgumentBuilder.argument("x", DoubleArgumentType.doubleArg())
            .then(
               RequiredArgumentBuilder.argument("y", DoubleArgumentType.doubleArg())
                  .then(
                     RequiredArgumentBuilder.argument("z", DoubleArgumentType.doubleArg())
                        .executes(
                           ctx -> this.addAt(
                              DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z")
                           )
                        )
                  )
            )
      );
      root.then(
         RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString()).executes(ctx -> this.fallback(StringArgumentType.getString(ctx, "args")))
      );
   }

   private int fallback(String args) {
      String trimmed = args == null ? "" : args.trim();
      if (trimmed.equalsIgnoreCase("gui") || trimmed.equalsIgnoreCase("list")) {
         return this.openGui();
      } else if (trimmed.equalsIgnoreCase("clear")) {
         return this.clearScope();
      } else if (trimmed.regionMatches(true, 0, "del ", 0, 4)) {
         return this.delete(trimmed.substring(4));
      } else {
         double[] coords = parseCoords(trimmed);
         return coords != null ? this.addAt(coords[0], coords[1], coords[2]) : this.usage();
      }
   }

   private int usage() {
      RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "wp [x y z] | gui | del <name> | clear");
      return 1;
   }

   private int addCurrentPosition() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null) {
         RiptideWaypoints store = RiptideWaypoints.get();
         String scope = RiptideWaypoints.scopeKey(mc);
         int x = (int)Math.floor(mc.player.getX());
         int y = (int)Math.floor(mc.player.getY());
         int z = (int)Math.floor(mc.player.getZ());
         String name = store.nextName(scope, "Waypoint");
         store.add(scope, new RiptideWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
         RiptideClientMessaging.sendPrefixed("§aWaypoint saved: §f" + name + " §7(" + x + " " + y + " " + z + ")");
         return 1;
      } else {
         RiptideClientMessaging.sendPrefixed("§cNot in a world.");
         return 1;
      }
   }

   private int addAt(double dx, double dy, double dz) {
      Minecraft mc = Minecraft.getInstance();
      RiptideWaypoints store = RiptideWaypoints.get();
      String scope = RiptideWaypoints.scopeKey(mc);
      int x = floorCoordinate(dx);
      int y = floorCoordinate(dy);
      int z = floorCoordinate(dz);
      String name = store.nextName(scope, "Waypoint");
      store.add(scope, new RiptideWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
      RiptideClientMessaging.sendPrefixed("§aWaypoint saved: §f" + name + " §7(" + x + " " + y + " " + z + ")");
      return 1;
   }

   private int delete(String name) {
      String trimmed = name == null ? "" : name.trim();
      if (trimmed.isEmpty()) {
         return this.usage();
      } else {
         String scope = RiptideWaypoints.scopeKey(Minecraft.getInstance());
         if (RiptideWaypoints.get().remove(scope, trimmed)) {
            RiptideClientMessaging.sendPrefixed("§aWaypoint deleted: §f" + trimmed);
         } else {
            RiptideClientMessaging.sendPrefixed("§cNo waypoint named '" + trimmed + "'.");
         }

         return 1;
      }
   }

   private int clearScope() {
      String scope = RiptideWaypoints.scopeKey(Minecraft.getInstance());
      RiptideWaypoints store = RiptideWaypoints.get();
      int count = store.list(scope).size();
      store.clear(scope);
      RiptideClientMessaging.sendPrefixed(count == 0 ? "§7No waypoints to clear." : "§aCleared " + count + " waypoint" + (count == 1 ? "" : "s") + ".");
      return 1;
   }

   private int openGui() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.gui != null) {
         mc.execute(() -> mc.gui.setScreen(new RiptideWaypointsScreen(mc.gui.screen())));
         return 1;
      } else {
         return 1;
      }
   }

   private static int defaultColor() {
      return ModuleRegistry.get("waypoints") instanceof WaypointsModule waypoints ? waypoints.defaultColor() : -12855297;
   }

   private static double[] parseCoords(String args) {
      String[] parts = args.split("\\s+");
      if (parts.length != 3) {
         return null;
      } else {
         double[] out = new double[3];

         try {
            for (int i = 0; i < 3; i++) {
               out[i] = Double.parseDouble(parts[i]);
               if (!Double.isFinite(out[i])) {
                  return null;
               }
            }

            return out;
         } catch (NumberFormatException var4) {
            return null;
         }
      }
   }

   private static int floorCoordinate(double value) {
      return Double.isFinite(value) ? (int)Math.floor(value) : 0;
   }
}
