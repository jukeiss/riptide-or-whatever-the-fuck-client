package riptide.commands.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.macro.HClipAction;

public class HClipCommand extends Command {
   public HClipCommand() {
      super("hclip", "Lets you clip through blocks horizontally (forward) with movement packets.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "hclip <blocks>");
         RiptideClientMessaging.sendPrefixed("§7Modes: §fdefault, forward, back, padding, single, custom (paper alias still works)");
         RiptideClientMessaging.sendPrefixed("§7Examples: §f" + prefix + "hclip forward §7or §f" + prefix + "hclip custom -25 10 20 true true true");
         return 1;
      });
      root.then(blocksArgument("blocks", options -> options));
      root.then(LiteralArgumentBuilder.literal("forward").executes(ctx -> {
         HClipAction.Options options = HClipAction.Options.defaults(0.0);
         options.mode = HClipAction.Mode.FORWARD;
         run(options);
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("back").executes(ctx -> {
         HClipAction.Options options = HClipAction.Options.defaults(0.0);
         options.mode = HClipAction.Mode.BACK;
         run(options);
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("default").then(blocksArgument("blocks", options -> options)));
      root.then(LiteralArgumentBuilder.literal("paper").then(blocksArgument("blocks", options -> options)));
      root.then(LiteralArgumentBuilder.literal("padding").then(blocksArgument("blocks", options -> options)));
      root.then(LiteralArgumentBuilder.literal("single").then(blocksArgument("blocks", HClipAction.Options::singlePacket)));
      root.then(LiteralArgumentBuilder.literal("normal").then(blocksArgument("blocks", HClipAction.Options::singlePacket)));
      root.then(
         LiteralArgumentBuilder.literal("custom")
            .then(
               RequiredArgumentBuilder.argument("blocks", DoubleArgumentType.doubleArg())
                  .suggests(CommandSuggest::offsets)
                  .then(
                     ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("segment", IntegerArgumentType.integer(1, 50))
                           .suggests(CommandSuggest::vclipSegments)
                           .executes(ctx -> {
                              HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                              options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                              run(options);
                              return 1;
                           }))
                        .then(
                           ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("maxPackets", IntegerArgumentType.integer(1, 100))
                                 .suggests(CommandSuggest::vclipPacketLimits)
                                 .executes(ctx -> {
                                    HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                                    options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                                    options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
                                    run(options);
                                    return 1;
                                 }))
                              .then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("updateLocal", BoolArgumentType.bool()).executes(ctx -> {
                                 HClipAction.Options options = customOptions(ctx);
                                 options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                 run(options);
                                 return 1;
                              })).then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("vehicle", BoolArgumentType.bool()).executes(ctx -> {
                                 HClipAction.Options options = customOptions(ctx);
                                 options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                 options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                 run(options);
                                 return 1;
                              })).then(RequiredArgumentBuilder.argument("forceGround", BoolArgumentType.bool()).executes(ctx -> {
                                 HClipAction.Options options = customOptions(ctx);
                                 options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                 options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                 options.forceGrounded = BoolArgumentType.getBool(ctx, "forceGround");
                                 run(options);
                                 return 1;
                              }))))
                        )
                  )
            )
      );
   }

   private static RequiredArgumentBuilder<RiptideCommandSource, Double> blocksArgument(String name, HClipCommand.OptionsCustomizer customizer) {
      return (RequiredArgumentBuilder<RiptideCommandSource, Double>)RequiredArgumentBuilder.argument(name, DoubleArgumentType.doubleArg())
         .suggests(CommandSuggest::offsets)
         .executes(ctx -> {
            HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, name));
            run(customizer.apply(options));
            return 1;
         });
   }

   private static HClipAction.Options customOptions(CommandContext<RiptideCommandSource> ctx) {
      HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
      options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
      options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
      options.maxRoutePackets = options.maxPackets;
      return options;
   }

   public static void hclip(double blocks) {
      run(HClipAction.Options.defaults(blocks));
   }

   private static void run(HClipAction.Options options) {
      options.showMessage = true;
      HClipAction.perform(Minecraft.getInstance(), options);
   }

   private interface OptionsCustomizer {
      HClipAction.Options apply(HClipAction.Options var1);
   }
}
