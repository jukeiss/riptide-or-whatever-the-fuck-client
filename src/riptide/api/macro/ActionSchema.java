package riptide.api.macro;

import riptide.gui.macro.editor.ActionFieldSchema;

public final class ActionSchema {
   private final ActionFieldSchema internal;

   private ActionSchema(ActionFieldSchema internal) {
      this.internal = internal;
   }

   public ActionFieldSchema internal() {
      return this.internal;
   }

   public static ActionSchema.Builder builder() {
      return new ActionSchema.Builder();
   }

   public static final class Builder {
      private final ActionFieldSchema.Builder delegate = ActionFieldSchema.builder();

      private Builder() {
      }

      public ActionSchema.Builder toggle(String key, String label) {
         this.delegate.toggle(key, label);
         return this;
      }

      public ActionSchema.Builder number(String key, String label) {
         this.delegate.number(key, label);
         return this;
      }

      public ActionSchema.Builder decimal(String key, String label) {
         this.delegate.decimal(key, label);
         return this;
      }

      public ActionSchema.Builder text(String key, String label) {
         this.delegate.text(key, label);
         return this;
      }

      public ActionSchema.Builder macroSelect(String key, String label) {
         this.delegate.macroSelect(key, label);
         return this;
      }

      public ActionSchema.Builder enumField(String key, String label, String... options) {
         this.delegate.enumField(key, label, options);
         return this;
      }

      public ActionSchema.Builder stringList(String key, String label) {
         this.delegate.stringList(key, label);
         return this;
      }

      public ActionSchema.Builder slot(String key, String label) {
         this.delegate.slot(key, label);
         return this;
      }

      public ActionSchema.Builder targetSummary(String key, String label) {
         this.delegate.targetSummary(key, label);
         return this;
      }

      public ActionSchema.Builder capturePacketClick(String key, String label) {
         this.delegate.capturePacketClick(key, label);
         return this;
      }

      public ActionSchema.Builder blockPos(String key, String label) {
         this.delegate.blockPos(key, label);
         return this;
      }

      public ActionSchema.Builder range(int min, int max) {
         this.delegate.range(min, max);
         return this;
      }

      public ActionSchema.Builder decRange(double min, double max) {
         this.delegate.decRange(min, max);
         return this;
      }

      public ActionSchema.Builder showWhen(String key) {
         this.delegate.showWhen(key);
         return this;
      }

      public ActionSchema.Builder hideWhen(String key) {
         this.delegate.hideWhen(key);
         return this;
      }

      public ActionSchema.Builder showWhenEnum(String key, String value) {
         this.delegate.showWhenEnum(key, value);
         return this;
      }

      public ActionSchema.Builder hideWhenEnum(String key, String value) {
         this.delegate.hideWhenEnum(key, value);
         return this;
      }

      public ActionSchema.Builder addLabel(String label) {
         this.delegate.addLabel(label);
         return this;
      }

      public ActionSchema.Builder xyzKeys(String... keys) {
         this.delegate.xyzKeys(keys);
         return this;
      }

      public ActionSchema.Builder xyzDouble(boolean value) {
         this.delegate.xyzDouble(value);
         return this;
      }

      public ActionSchema.Builder captureBlock() {
         this.delegate.captureBlock();
         return this;
      }

      public ActionSchema.Builder captureEntity() {
         this.delegate.captureEntity();
         return this;
      }

      public ActionSchema.Builder captureCatalog() {
         this.delegate.captureCatalog();
         return this;
      }

      public ActionSchema.Builder captureItemSlot() {
         this.delegate.captureItemSlot();
         return this;
      }

      public ActionSchema.Builder capturePacketName() {
         this.delegate.capturePacketName();
         return this;
      }

      public ActionSchema.Builder exclusiveWith(String... keys) {
         this.delegate.exclusiveWith(keys);
         return this;
      }

      public ActionSchema.Builder dynamic() {
         this.delegate.dynamic();
         return this;
      }

      public ActionSchema build() {
         return new ActionSchema(this.delegate.build());
      }
   }
}
