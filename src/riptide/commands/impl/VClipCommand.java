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
import riptide.util.macro.VClipAction;

public class VClipCommand extends Command {
   public VClipCommand() {
      super("vclip", "Lets you clip through blocks vertically with movement packets.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "vclip <blocks>");
         RiptideClientMessaging.sendPrefixed("§7Modes: §fdefault, top, bottom, paper, single, custom");
         RiptideClientMessaging.sendPrefixed("§7Examples: §f" + prefix + "vclip top §7or §f" + prefix + "vclip custom -25 10 20 true true");
         return 1;
      });
      root.then(blocksArgument("blocks", options -> options));
      root.then(LiteralArgumentBuilder.literal("top").executes(ctx -> {
         VClipAction.Options options = VClipAction.Options.defaults(0.0);
         options.mode = VClipAction.Mode.TOP;
         run(options);
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("bottom").executes(ctx -> {
         VClipAction.Options options = VClipAction.Options.defaults(0.0);
         options.mode = VClipAction.Mode.BOTTOM;
         run(options);
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("default").then(blocksArgument("blocks", options -> options)));
      root.then(LiteralArgumentBuilder.literal("paper").then(blocksArgument("blocks", options -> options)));
      root.then(LiteralArgumentBuilder.literal("single").then(blocksArgument("blocks", VClipAction.Options::singlePacket)));
      root.then(LiteralArgumentBuilder.literal("normal").then(blocksArgument("blocks", VClipAction.Options::singlePacket)));
      root.then(
         LiteralArgumentBuilder.literal("custom")
            .then(
               RequiredArgumentBuilder.argument("blocks", DoubleArgumentType.doubleArg())
                  .suggests(CommandSuggest::offsets)
                  .then(
                     ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("segment", IntegerArgumentType.integer(1, 50))
                           .suggests(CommandSuggest::vclipSegments)
                           .executes(ctx -> {
                              VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                              options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                              run(options);
                              return 1;
                           }))
                        .then(
                           ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("maxPackets", IntegerArgumentType.integer(1, 100))
                                 .suggests(CommandSuggest::vclipPacketLimits)
                                 .executes(ctx -> {
                                    VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                                    options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                                    options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
                                    run(options);
                                    return 1;
                                 }))
                              .then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("updateLocal", BoolArgumentType.bool()).executes(ctx -> {
                                 VClipAction.Options options = customOptions(ctx);
                                 options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                 run(options);
                                 return 1;
                              })).then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("vehicle", BoolArgumentType.bool()).executes(ctx -> {
                                 VClipAction.Options options = customOptions(ctx);
                                 options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                 options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                 run(options);
                                 return 1;
                              })).then(RequiredArgumentBuilder.argument("forceGround", BoolArgumentType.bool()).executes(ctx -> {
                                 VClipAction.Options options = customOptions(ctx);
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

   private static RequiredArgumentBuilder<RiptideCommandSource, Double> blocksArgument(String name, VClipCommand.OptionsCustomizer customizer) {
      return (RequiredArgumentBuilder<RiptideCommandSource, Double>)RequiredArgumentBuilder.argument(name, DoubleArgumentType.doubleArg())
         .suggests(CommandSuggest::offsets)
         .executes(ctx -> {
            VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, name));
            run(customizer.apply(options));
            return 1;
         });
   }

   private static VClipAction.Options customOptions(CommandContext<RiptideCommandSource> ctx) {
      VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
      options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
      options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
      return options;
   }

   public static void vclip(double blocks) {
      run(VClipAction.Options.defaults(blocks));
   }

   private static void run(VClipAction.Options options) {
      options.showMessage = true;
      VClipAction.perform(Minecraft.getInstance(), options);
   }

   private interface OptionsCustomizer {
      VClipAction.Options apply(VClipAction.Options var1);
   }
}
