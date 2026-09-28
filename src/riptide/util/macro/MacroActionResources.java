package riptide.util.macro;

import java.util.EnumSet;
import java.util.List;

public final class MacroActionResources {
   private MacroActionResources() {
   }

   public static EnumSet<MacroActionResources.SharedClientResource> resourcesForAction(MacroAction action) {
      EnumSet<MacroActionResources.SharedClientResource> resources = EnumSet.noneOf(MacroActionResources.SharedClientResource.class);
      if (action instanceof ClickAction
         || action instanceof InventoryAction
         || action instanceof ItemAction
         || action instanceof DropAction
         || action instanceof CraftAction
         || action instanceof StoreItemAction
         || action instanceof InventoryAuditAction
         || action instanceof XCarryAction
         || action instanceof SwapSlotsAction
         || action instanceof SelectSlotAction) {
         resources.add(MacroActionResources.SharedClientResource.INVENTORY);
         resources.add(MacroActionResources.SharedClientResource.GUI);
      }

      if (action instanceof WaitDurabilityAction durability && durability.useNext) {
         resources.add(MacroActionResources.SharedClientResource.INVENTORY);
         resources.add(MacroActionResources.SharedClientResource.GUI);
      }

      if (action instanceof OpenContainerAction
         || action instanceof InteractEntityAction
         || action instanceof CloseGuiAction
         || action instanceof SaveGuiAction
         || action instanceof RestoreGuiAction
         || action instanceof DesyncAction
         || action instanceof NbtBookAction
         || action instanceof CustomMenuAction) {
         resources.add(MacroActionResources.SharedClientResource.GUI);
         resources.add(MacroActionResources.SharedClientResource.NETWORK);
      }

      if (action instanceof SendPacketAction
         || action instanceof PacketAction
         || action instanceof PacketClickAction
         || action instanceof PayloadAction
         || action instanceof PayAction
         || action instanceof SendChatAction) {
         resources.add(MacroActionResources.SharedClientResource.NETWORK);
      }

      if (action instanceof DelayPacketsAction) {
         resources.add(MacroActionResources.SharedClientResource.NETWORK);
         resources.add(MacroActionResources.SharedClientResource.PACKET_DELAY);
      }

      if (action instanceof UseItemAction) {
         resources.add(MacroActionResources.SharedClientResource.INPUT);
         resources.add(MacroActionResources.SharedClientResource.NETWORK);
      }

      if (action instanceof MoveAction || action instanceof SneakAction || action instanceof SprintAction || action instanceof JumpAction) {
         resources.add(MacroActionResources.SharedClientResource.INPUT);
      }

      if (action instanceof RotateAction || action instanceof LookAtBlockAction) {
         resources.add(MacroActionResources.SharedClientResource.ROTATION);
      }

      if (action instanceof GoToAction || action instanceof MineAction || action instanceof InstaBreakAction || action instanceof BreakAction) {
         resources.add(MacroActionResources.SharedClientResource.BARITONE);
         resources.add(MacroActionResources.SharedClientResource.INPUT);
         resources.add(MacroActionResources.SharedClientResource.ROTATION);
         resources.add(MacroActionResources.SharedClientResource.NETWORK);
      }

      if (action instanceof ToggleModuleAction) {
         resources.add(MacroActionResources.SharedClientResource.WORLD_ACTION);
      }

      return resources;
   }

   public static EnumSet<MacroActionResources.SharedClientResource> resourcesForActions(List<MacroAction> actions) {
      EnumSet<MacroActionResources.SharedClientResource> resources = EnumSet.noneOf(MacroActionResources.SharedClientResource.class);
      if (actions == null) {
         return resources;
      } else {
         for (MacroAction action : actions) {
            if (action != null && action.isEnabled() && RaceAction.isBodyAction(action)) {
               resources.addAll(resourcesForAction(action));
            }
         }

         return resources;
      }
   }

   public static enum SharedClientResource {
      INVENTORY,
      GUI,
      INPUT,
      ROTATION,
      BARITONE,
      NETWORK,
      PACKET_DELAY,
      WORLD_ACTION;
   }
}
