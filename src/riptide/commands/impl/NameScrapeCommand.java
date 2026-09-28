package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideNameScrape;

public final class NameScrapeCommand extends Command {
   public NameScrapeCommand() {
      super("namescrape", "Scrape player names to clipboard. [count] limits it; deep = exhaustive; pause/resume/stop.", "scrapenames");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> apply(0, false));
      root.then(
         RequiredArgumentBuilder.argument("count", IntegerArgumentType.integer(1)).executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), false))
      );
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("deep").executes(ctx -> apply(0, true)))
            .then(
               RequiredArgumentBuilder.argument("count", IntegerArgumentType.integer(1))
                  .executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), true))
            )
      );
      root.then(LiteralArgumentBuilder.literal("pause").executes(ctx -> {
         RiptideNameScrape.pause();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("resume").executes(ctx -> {
         RiptideNameScrape.resume();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("stop").executes(ctx -> {
         RiptideNameScrape.stop();
         return 1;
      }));
   }

   private static int apply(int count, boolean deep) {
      RiptideNameScrape.start(count, deep);
      return 1;
   }
}
