package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.modules.GhostBlockModule;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideClientMessaging;

public class GhostBlockCommand extends Command {
   public GhostBlockCommand() {
      super("ghostblock", "Client-side ghost blocks: place, remove and manage blocks only you can see.", "gb");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: " + prefix + "ghostblock <toggle|reset|creative|block>");
         RiptideClientMessaging.sendPrefixed("§7" + prefix + "ghostblock toggle §8- §7toggle the module");
         RiptideClientMessaging.sendPrefixed("§7" + prefix + "ghostblock reset §8- §7remove all ghost blocks");
         RiptideClientMessaging.sendPrefixed("§7" + prefix + "ghostblock creative [on|off] §8- §7fake creative gamemode");
         RiptideClientMessaging.sendPrefixed("§7" + prefix + "ghostblock block [id|off] §8- §7arm a block for placement");
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("toggle").executes(ctx -> {
         GhostBlockModule module = module();
         if (module == null) {
            return 1;
         } else {
            module.toggle();
            return 1;
         }
      }));
      root.then(
         LiteralArgumentBuilder.literal("reset")
            .executes(
               ctx -> {
                  GhostBlockModule module = module();
                  if (module == null) {
                     return 1;
                  } else {
                     int removed = module.restoreAll();
                     RiptideClientMessaging.sendPrefixed(
                        removed == 0
                           ? "§7No ghost blocks to remove."
                           : "§aRemoved §f" + removed + "§a ghost block" + (removed == 1 ? "" : "s") + " and restored the original states."
                     );
                     return 1;
                  }
               }
            )
      );
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("creative").executes(ctx -> setCreative(null)))
            .then(
               RequiredArgumentBuilder.argument("state", StringArgumentType.word())
                  .suggests(CommandSuggest::state)
                  .executes(ctx -> setCreative(StringArgumentType.getString(ctx, "state")))
            )
      );
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("block")
               .executes(
                  ctx -> {
                     GhostBlockModule module = module();
                     if (module == null) {
                        return 1;
                     } else {
                        Block armed = module.armedBlock();
                        RiptideClientMessaging.sendPrefixed(
                           armed == null ? "§7No ghost block armed." : "§7Armed ghost block: §f" + BuiltInRegistries.BLOCK.getKey(armed)
                        );
                        return 1;
                     }
                  }
               ))
            .then(
               RequiredArgumentBuilder.argument("block", StringArgumentType.word())
                  .suggests(CommandSuggest::blockIds)
                  .executes(ctx -> arm(StringArgumentType.getString(ctx, "block")))
            )
      );
   }

   private static int setCreative(String state) {
      GhostBlockModule module = module();
      if (module == null) {
         return 1;
      } else {
         boolean target;
         if (state == null || "toggle".equalsIgnoreCase(state)) {
            target = !module.isCreative();
         } else if (!"on".equalsIgnoreCase(state) && !"enable".equalsIgnoreCase(state)) {
            if (!"off".equalsIgnoreCase(state) && !"disable".equalsIgnoreCase(state)) {
               RiptideClientMessaging.sendPrefixed("§cUnknown state: §f" + state);
               return 1;
            }

            target = false;
         } else {
            target = true;
         }

         module.setCreative(target);
         if (target && !module.isEnabled()) {
            module.setEnabled(true);
         }

         RiptideClientMessaging.sendPrefixed(
            target ? "§aFake creative on. §7Whatever you place becomes a ghost block." : "§7Fake creative off. §7Back to your real gamemode."
         );
         return 1;
      }
   }

   private static int arm(String input) {
      GhostBlockModule module = module();
      if (module == null) {
         return 1;
      } else {
         String value = input.toLowerCase(Locale.ROOT);
         if (!"off".equals(value) && !"none".equals(value) && !"reset".equals(value)) {
            Identifier id = Identifier.tryParse(value.contains(":") ? value : "minecraft:" + value);
            Block block = id == null ? null : (Block)BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
               RiptideClientMessaging.sendPrefixed("§cUnknown block: §f" + input);
               return 1;
            } else {
               module.setArmedBlock(block, false);
               if (!module.isEnabled()) {
                  module.setEnabled(true);
               }

               RiptideClientMessaging.sendPrefixed(
                  "§aGhost block: §f" + BuiltInRegistries.BLOCK.getKey(block) + (module.isCreative() ? "" : " §7(switch Mode to Creative to place it)")
               );
               return 1;
            }
         } else {
            module.setArmedBlock(null, false);
            RiptideClientMessaging.sendPrefixed("§7Ghost block disarmed; held blocks place ghosts again.");
            return 1;
         }
      }
   }

   private static GhostBlockModule module() {
      if (ModuleRegistry.get("ghostblock") instanceof GhostBlockModule ghostBlock) {
         return ghostBlock;
      } else {
         RiptideClientMessaging.sendPrefixed("§cGhostBlock module is not available.");
         return null;
      }
   }
}
