package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.XCarrySlotArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSharedState;
import riptide.util.multi.MultiPilot;

public class XCarryCommand extends Command {
   public XCarryCommand() {
      super("xcarry", "Stash the held item into an XCarry slot: craft1..craft5 / helmet / chestplate / leggings / boots / offhand / cursor.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "xcarry <slot>");
         RiptideClientMessaging.sendPrefixed("§7Slots: §fcraft1§7..§fcraft5§7, §fhelmet§7, §fchestplate§7, §fleggings§7, §fboots§7, §foffhand§7, §fcursor§7.");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("slot", XCarrySlotArgumentType.slot()).executes(ctx -> stash(XCarrySlotArgumentType.get(ctx, "slot"))));
   }

   private static int stash(int targetSlot) {
      String povResult = MultiPilot.commandStashXCarry(targetSlot);
      if (povResult != null) {
         RiptideClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c") + "POV XCarry: §f" + povResult);
         return 1;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer p = mc.player;
         if (p != null && mc.gameMode != null) {
            ItemStack heldStack = p.getMainHandItem().copy();
            if (heldStack == null || heldStack.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cHold an item first.");
               return 1;
            } else if (p.containerMenu != p.inventoryMenu) {
               RiptideClientMessaging.sendPrefixed("§cOpen your inventory first (E), then run the command.");
               return 1;
            } else {
               AbstractContainerMenu container = p.containerMenu;
               int hotbar = p.getInventory().getSelectedSlot();
               int sourceSlotId = 36 + hotbar;
               RiptideSharedState shared = RiptideSharedState.get();
               boolean prevBypass = shared.isXCarryArmorBypass();
               shared.setXCarryArmorBypass(true);

               byte var15;
               try {
                  mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
                  if (container.getCarried().isEmpty()) {
                     RiptideClientMessaging.sendPrefixed("§cCould not pick up the held item.");
                     return 1;
                  }

                  String itemName = heldStack.getHoverName().getString();
                  String slotName = XCarrySlotArgumentType.displayName(targetSlot);
                  LinkedHashSet<Integer> forcedSlots = mergedForcedSlots(shared);
                  boolean forcedCursor = shared.isXCarryForced() && shared.isXCarryForcedCarryCursor();
                  if (targetSlot == Integer.MIN_VALUE) {
                     shared.setXCarryForcedTargets(forcedSlots, true);
                     shared.setXCarryForced(true);
                     shared.setXCarryActive(true);
                     RiptideClientMessaging.sendPrefixed("§aHolding §f" + itemName + "§a on cursor (XCarry).");
                     return 1;
                  }

                  if (targetSlot < 0 || targetSlot >= container.slots.size()) {
                     RiptideClientMessaging.sendPrefixed("§cInvalid slot (" + targetSlot + ").");
                     mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
                     return 1;
                  }

                  mc.gameMode.handleContainerInput(container.containerId, targetSlot, 0, ContainerInput.PICKUP, p);
                  boolean placed = container.getCarried().isEmpty()
                     && targetSlot >= 0
                     && targetSlot < container.slots.size()
                     && !((Slot)container.slots.get(targetSlot)).getItem().isEmpty();
                  if (!placed && !container.getCarried().isEmpty()) {
                     mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
                  }

                  if (placed) {
                     forcedSlots.add(targetSlot);
                     shared.setXCarryForcedTargets(forcedSlots, forcedCursor);
                     shared.setXCarryForced(true);
                     shared.setXCarryActive(true);
                     RiptideClientMessaging.sendPrefixed("§aStashed §f" + itemName + "§a into §f" + slotName + "§a.");
                     return 1;
                  }

                  RiptideClientMessaging.sendPrefixed("§cCould not stash into §f" + slotName + "§c.");
                  var15 = 1;
               } finally {
                  shared.setXCarryArmorBypass(prevBypass);
               }

               return var15;
            }
         } else {
            RiptideClientMessaging.sendPrefixed("§cNot in a world.");
            return 1;
         }
      }
   }

   private static LinkedHashSet<Integer> mergedForcedSlots(RiptideSharedState shared) {
      LinkedHashSet<Integer> slots = new LinkedHashSet<>();
      if (shared != null && shared.isXCarryForced()) {
         Set<Integer> existing = shared.getXCarryForcedSlotMask();
         if (existing != null) {
            for (Integer slot : existing) {
               if (slot != null && slot >= 0) {
                  slots.add(slot);
               }
            }
         }
      }

      return slots;
   }
}
