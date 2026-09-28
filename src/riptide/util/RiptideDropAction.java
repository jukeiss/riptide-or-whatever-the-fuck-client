package riptide.util;

import net.minecraft.world.inventory.ContainerInput;

public enum RiptideDropAction {
   PICKUP("Pickup", "Pick", false),
   QUICK_MOVE("Quick Move", "QMove", false),
   SWAP("Swap", "Swap", false),
   CLONE("Clone", "Clone", false),
   THROW("Throw (2x)", "Throw", false),
   QUICK_CRAFT("Quick Craft", "QCraft", false),
   PICKUP_ALL("Pick Up All", "All", false),
   DROP_ITEM("Drop Item (1x)", "Drop1", false),
   DROP_STACK("Drop Stack", "DropStk", false);

   public final String displayName;
   public final String shortName;
   public final boolean isPlayerAction;

   private RiptideDropAction(String displayName, String shortName, boolean isPlayerAction) {
      this.displayName = displayName;
      this.shortName = shortName;
      this.isPlayerAction = isPlayerAction;
   }

   public boolean isSlotAction() {
      return !this.isPlayerAction;
   }

   public boolean usesFixedButton() {
      return this.getButton() >= 0;
   }

   public int getButton() {
      return switch (this) {
         case QUICK_MOVE -> 0;
         default -> -1;
         case CLONE -> 2;
         case PICKUP_ALL -> 0;
         case DROP_ITEM -> 0;
         case DROP_STACK -> 1;
      };
   }

   public ContainerInput toContainerInput() {
      if (this.isPlayerAction) {
         throw new IllegalStateException("Cannot convert PlayerAction to ContainerInput: " + this);
      } else {
         return switch (this) {
            case PICKUP -> ContainerInput.PICKUP;
            case QUICK_MOVE -> ContainerInput.QUICK_MOVE;
            case SWAP -> ContainerInput.SWAP;
            case CLONE -> ContainerInput.CLONE;
            case THROW, DROP_ITEM, DROP_STACK -> ContainerInput.THROW;
            case QUICK_CRAFT -> ContainerInput.QUICK_CRAFT;
            case PICKUP_ALL -> ContainerInput.PICKUP_ALL;
            default -> throw new IllegalStateException("Unknown action: " + this);
         };
      }
   }
}
