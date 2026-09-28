package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideItemCommandSerializer;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideNotifications;
import riptide.util.multi.MultiPilot;

public class NbtCommand extends Command {
   public NbtCommand() {
      super("nbt", "Inspect or copy the held item's components (NBT).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         inspect();
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("get").executes(ctx -> {
         inspect();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("copy").executes(ctx -> {
         copy();
         return 1;
      }));
   }

   private static ItemStack held() {
      Player player = MultiPilot.commandPlayer();
      if (player == null) {
         return ItemStack.EMPTY;
      } else {
         ItemStack main = player.getMainHandItem();
         return !main.isEmpty() ? main : player.getOffhandItem();
      }
   }

   private static void inspect() {
      ItemStack stack = held();
      if (stack.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cHold an item in either hand first.");
      } else if (!RiptideItemNbtInspectOverlay.openGlobal(stack)) {
         RiptideClientMessaging.sendPrefixed("§cCould not open the NBT inspector.");
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            RiptideItemNbtInspectOverlay overlay = RiptideItemNbtInspectOverlay.getSharedOverlay(mc.font);
            mc.execute(() -> {
               if (mc.gui.screen() == null) {
                  mc.gui.setScreen(new RiptideOverlayHostScreen(overlay));
               }
            });
         }
      }
   }

   private static void copy() {
      ItemStack stack = held();
      if (stack.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cHold an item in either hand first.");
      } else {
         try {
            Minecraft.getInstance().keyboardHandler.setClipboard(RiptideItemCommandSerializer.giveCommand(stack));
            RiptideNotifications.copied("Copied /give command.");
         } catch (Throwable var2) {
            RiptideNotifications.error("Copy failed.");
         }
      }
   }
}
