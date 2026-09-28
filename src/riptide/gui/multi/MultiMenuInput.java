package riptide.gui.multi;

public final class MultiMenuInput {
   public final MultiRenameField rename = new MultiRenameField();
   public int beaconPrimary = -1;
   public int beaconSecondary = -1;
   public int recipeIndex = -1;
   private String type = "";

   public void sync(String typeId) {
      String t = typeId == null ? "" : typeId;
      if (!t.equals(this.type)) {
         this.type = t;
         if (!t.endsWith("anvil")) {
            this.rename.blur();
         }

         this.beaconPrimary = -1;
         this.beaconSecondary = -1;
         this.recipeIndex = -1;
      }
   }
}
