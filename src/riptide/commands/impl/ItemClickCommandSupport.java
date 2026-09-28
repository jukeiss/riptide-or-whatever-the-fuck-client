package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideInventoryClickHelper;

final class ItemClickCommandSupport {
   static final int MAX_REPEAT_COUNT = 100000;

   private ItemClickCommandSupport() {
   }

   static void attachModes(ArgumentBuilder<RiptideCommandSource, ?> target, ItemClickCommandSupport.ClickExecutor executor) {
      target.then(repeated("left", ItemClickCommandSupport.ClickSpec.click("Left", ContainerInput.PICKUP, 0), executor));
      target.then(repeated("right", ItemClickCommandSupport.ClickSpec.click("Right", ContainerInput.PICKUP, 1), executor));
      target.then(repeated("middle", ItemClickCommandSupport.ClickSpec.click("Middle", ContainerInput.PICKUP, 2), executor));
      target.then(repeated("shift-left", ItemClickCommandSupport.ClickSpec.click("Shift Left", ContainerInput.QUICK_MOVE, 0), executor));
      target.then(repeated("shift-right", ItemClickCommandSupport.ClickSpec.click("Shift Right", ContainerInput.QUICK_MOVE, 1), executor));
      target.then(repeated("clone", ItemClickCommandSupport.ClickSpec.click("Clone", ContainerInput.CLONE, 2), executor));
      target.then(repeated("pickup-all", ItemClickCommandSupport.ClickSpec.click("Pick Up All", ContainerInput.PICKUP_ALL, 0), executor));
      target.then(repeated("drop-item", ItemClickCommandSupport.ClickSpec.drop("Drop Item", ItemClickCommandSupport.DropMode.ITEM), executor));
      target.then(single("drop-stack", ItemClickCommandSupport.ClickSpec.drop("Drop Stack", ItemClickCommandSupport.DropMode.STACK), executor));
      LiteralArgumentBuilder<RiptideCommandSource> swap = LiteralArgumentBuilder.literal("swap");
      RequiredArgumentBuilder<RiptideCommandSource, Integer> hotbar = (RequiredArgumentBuilder<RiptideCommandSource, Integer>)RequiredArgumentBuilder.argument(
            "swap-hotbar", IntegerArgumentType.integer(1, 9)
         )
         .suggests((context, builder) -> CommandSuggest.literals(builder, "1", "2", "3", "4", "5", "6", "7", "8", "9"))
         .executes(
            context -> executor.execute(
               context,
               ItemClickCommandSupport.ClickSpec.click(
                  "Swap " + IntegerArgumentType.getInteger(context, "swap-hotbar"),
                  ContainerInput.SWAP,
                  IntegerArgumentType.getInteger(context, "swap-hotbar") - 1
               ),
               1
            )
         );
      hotbar.then(
         timesArgument(
            (context, times) -> executor.execute(
               context,
               ItemClickCommandSupport.ClickSpec.click(
                  "Swap " + IntegerArgumentType.getInteger(context, "swap-hotbar"),
                  ContainerInput.SWAP,
                  IntegerArgumentType.getInteger(context, "swap-hotbar") - 1
               ),
               times
            )
         )
      );
      swap.then(hotbar);
      target.then(swap);
   }

   static int clickHandlerSlot(int handlerSlot, ItemClickCommandSupport.ClickSpec spec, int times, String targetLabel) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.gameMode == null || mc.getConnection() == null || mc.player.containerMenu == null) {
         RiptideClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
         return 1;
      } else if (handlerSlot >= 0 && handlerSlot < mc.player.containerMenu.slots.size()) {
         int requested = Math.max(1, times);
         int completed = 0;
         if (spec.dropMode() == ItemClickCommandSupport.DropMode.ITEM) {
            ItemStack stack = ((Slot)mc.player.containerMenu.slots.get(handlerSlot)).getItem();
            if (!stack.isEmpty()) {
               int actual = Math.min(requested, stack.getCount());
               if (RiptideDropHelper.dropFromHandlerSlot(mc, handlerSlot, actual) > 0) {
                  completed = actual;
               }
            }
         } else if (spec.dropMode() == ItemClickCommandSupport.DropMode.STACK) {
            completed = RiptideDropHelper.dropFromHandlerSlot(mc, handlerSlot, 0) > 0 ? 1 : 0;
         } else {
            for (int i = 0; i < requested && RiptideInventoryClickHelper.click(mc, handlerSlot, spec.button(), spec.input()); i++) {
               completed++;
            }
         }

         if (completed == 0) {
            RiptideClientMessaging.sendPrefixed("§cCould not click §f" + targetLabel + "§c.");
         } else {
            String repeat = completed > 1 && spec.dropMode() != ItemClickCommandSupport.DropMode.STACK ? " x" + completed : "";
            String partial = completed < requested && spec.dropMode() != ItemClickCommandSupport.DropMode.STACK
               ? " §e(" + completed + "/" + requested + ")"
               : "";
            RiptideClientMessaging.sendPrefixed("§a" + spec.label() + " clicked §f" + targetLabel + "§a" + repeat + "." + partial);
         }

         return 1;
      } else {
         RiptideClientMessaging.sendPrefixed("§cThat slot does not exist in the current GUI.");
         return 1;
      }
   }

   private static LiteralArgumentBuilder<RiptideCommandSource> repeated(
      String literal, ItemClickCommandSupport.ClickSpec spec, ItemClickCommandSupport.ClickExecutor executor
   ) {
      LiteralArgumentBuilder<RiptideCommandSource> node = single(literal, spec, executor);
      node.then(timesArgument((context, times) -> executor.execute(context, spec, times)));
      return node;
   }

   private static LiteralArgumentBuilder<RiptideCommandSource> single(
      String literal, ItemClickCommandSupport.ClickSpec spec, ItemClickCommandSupport.ClickExecutor executor
   ) {
      return (LiteralArgumentBuilder<RiptideCommandSource>)LiteralArgumentBuilder.literal(literal).executes(context -> executor.execute(context, spec, 1));
   }

   private static RequiredArgumentBuilder<RiptideCommandSource, Integer> timesArgument(ItemClickCommandSupport.TimesExecutor executor) {
      return (RequiredArgumentBuilder<RiptideCommandSource, Integer>)RequiredArgumentBuilder.argument("times", IntegerArgumentType.integer(1, 100000))
         .suggests(CommandSuggest::counts)
         .executes(context -> executor.execute(context, IntegerArgumentType.getInteger(context, "times")));
   }

   @FunctionalInterface
   interface ClickExecutor {
      int execute(CommandContext<RiptideCommandSource> var1, ItemClickCommandSupport.ClickSpec var2, int var3);
   }

   record ClickSpec(String label, ContainerInput input, int button, ItemClickCommandSupport.DropMode dropMode) {
      static ItemClickCommandSupport.ClickSpec click(String label, ContainerInput input, int button) {
         return new ItemClickCommandSupport.ClickSpec(label, input, button, ItemClickCommandSupport.DropMode.NONE);
      }

      static ItemClickCommandSupport.ClickSpec drop(String label, ItemClickCommandSupport.DropMode dropMode) {
         return new ItemClickCommandSupport.ClickSpec(label, ContainerInput.THROW, dropMode == ItemClickCommandSupport.DropMode.STACK ? 1 : 0, dropMode);
      }
   }

   static enum DropMode {
      NONE,
      ITEM,
      STACK;
   }

   @FunctionalInterface
   private interface TimesExecutor {
      int execute(CommandContext<RiptideCommandSource> var1, int var2);
   }
}
