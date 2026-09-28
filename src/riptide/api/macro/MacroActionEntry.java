package riptide.api.macro;

import java.util.function.Supplier;
import riptide.addons.AddonManager;
import riptide.gui.macro.editor.ActionFieldSchema;
import riptide.util.macro.MacroAction;

public final class MacroActionEntry {
   private final String typeId;
   private final Supplier<MacroAction> factory;
   private final ActionFieldSchema schema;
   private String pickerCategory;
   private final String pickerLabel;
   private final String pickerTip;
   private final MacroActionEntry.Kind kind;

   private MacroActionEntry(MacroActionEntry.Builder b) {
      this.typeId = b.typeId;
      this.factory = b.factory;
      this.schema = b.schema;
      this.pickerCategory = b.pickerCategory;
      this.pickerLabel = b.pickerLabel;
      this.pickerTip = b.pickerTip;
      this.kind = b.kind;
   }

   public String typeId() {
      return this.typeId;
   }

   public Supplier<MacroAction> factory() {
      return this.factory;
   }

   public ActionFieldSchema schema() {
      return this.schema;
   }

   public boolean hasPicker() {
      return this.pickerCategory != null && this.pickerLabel != null;
   }

   boolean wantsPicker() {
      return this.pickerLabel != null;
   }

   public String pickerCategory() {
      return this.pickerCategory;
   }

   public String pickerLabel() {
      return this.pickerLabel;
   }

   public String pickerTip() {
      return this.pickerTip == null ? "" : this.pickerTip;
   }

   public MacroActionEntry.Kind kind() {
      return this.kind;
   }

   public boolean isCondition() {
      return this.kind == MacroActionEntry.Kind.CONDITION;
   }

   void setPickerCategory(String categoryId) {
      this.pickerCategory = categoryId;
   }

   public static MacroActionEntry.Builder builder(String typeId, Supplier<MacroAction> factory) {
      return new MacroActionEntry.Builder(typeId, factory);
   }

   public static MacroActionEntry.Builder local(String localId, Supplier<MacroAction> factory) {
      return new MacroActionEntry.Builder(AddonManager.scopedId(localId), factory);
   }

   public static final class Builder {
      private final String typeId;
      private final Supplier<MacroAction> factory;
      private ActionFieldSchema schema;
      private String pickerCategory;
      private String pickerLabel;
      private String pickerTip;
      private MacroActionEntry.Kind kind = MacroActionEntry.Kind.ACTION;

      private Builder(String typeId, Supplier<MacroAction> factory) {
         this.typeId = typeId;
         this.factory = factory;
      }

      public MacroActionEntry.Builder schema(ActionSchema schema) {
         this.schema = schema == null ? null : schema.internal();
         return this;
      }

      public MacroActionEntry.Builder picker(String label, String tip) {
         return this.picker(null, label, tip);
      }

      public MacroActionEntry.Builder picker(String categoryId, String label, String tip) {
         this.pickerCategory = categoryId;
         this.pickerLabel = label;
         this.pickerTip = tip;
         this.kind = MacroActionEntry.Kind.ACTION;
         return this;
      }

      public MacroActionEntry.Builder condition(String label, String tip) {
         return this.condition(null, label, tip);
      }

      public MacroActionEntry.Builder condition(String categoryId, String label, String tip) {
         this.pickerCategory = categoryId;
         this.pickerLabel = label;
         this.pickerTip = tip;
         this.kind = MacroActionEntry.Kind.CONDITION;
         return this;
      }

      public MacroActionEntry build() {
         return new MacroActionEntry(this);
      }
   }

   public static enum Kind {
      ACTION,
      CONDITION;
   }
}
