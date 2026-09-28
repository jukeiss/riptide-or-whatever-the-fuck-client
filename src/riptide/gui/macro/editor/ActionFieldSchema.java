package riptide.gui.macro.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ActionFieldSchema {
   private final List<FieldDef> fields;

   private ActionFieldSchema(List<FieldDef> fields) {
      this.fields = Collections.unmodifiableList(fields);
   }

   public List<FieldDef> fields() {
      return this.fields;
   }

   public static ActionFieldSchema.Builder builder() {
      return new ActionFieldSchema.Builder();
   }

   public static final class Builder {
      private final List<FieldDef> fields = new ArrayList<>();
      private String pendingKey;
      private String pendingLabel;
      private FieldType pendingType;
      private int min = Integer.MIN_VALUE;
      private int max = Integer.MAX_VALUE;
      private double decMin = -Double.MAX_VALUE;
      private double decMax = Double.MAX_VALUE;
      private List<String> enumOpts;
      private String showWhenKey;
      private boolean showWhenInv;
      private String showWhenValue;
      private String addLabel;
      private String[] xyzKeys;
      private boolean xyzDouble;
      private CaptureMode captureMode = CaptureMode.NONE;
      private String[] exclusiveWith;
      private boolean dynamic;

      private void commit() {
         if (this.pendingKey != null) {
            this.fields
               .add(
                  new FieldDef(
                     this.pendingKey,
                     this.pendingLabel,
                     this.pendingType,
                     this.min,
                     this.max,
                     this.decMin,
                     this.decMax,
                     this.enumOpts,
                     this.showWhenKey,
                     this.showWhenInv,
                     this.showWhenValue,
                     this.addLabel,
                     this.xyzKeys,
                     this.xyzDouble,
                     this.captureMode,
                     this.exclusiveWith,
                     this.dynamic
                  )
               );
            this.pendingKey = null;
            this.pendingLabel = null;
            this.pendingType = null;
            this.min = Integer.MIN_VALUE;
            this.max = Integer.MAX_VALUE;
            this.decMin = -Double.MAX_VALUE;
            this.decMax = Double.MAX_VALUE;
            this.enumOpts = null;
            this.showWhenKey = null;
            this.showWhenInv = false;
            this.showWhenValue = null;
            this.addLabel = null;
            this.xyzKeys = null;
            this.xyzDouble = false;
            this.captureMode = CaptureMode.NONE;
            this.exclusiveWith = null;
            this.dynamic = false;
         }
      }

      private ActionFieldSchema.Builder start(String key, String label, FieldType type) {
         this.commit();
         this.pendingKey = key;
         this.pendingLabel = label;
         this.pendingType = type;
         return this;
      }

      public ActionFieldSchema.Builder toggle(String key, String label) {
         return this.start(key, label, FieldType.TOGGLE);
      }

      public ActionFieldSchema.Builder number(String key, String label) {
         return this.start(key, label, FieldType.NUMBER);
      }

      public ActionFieldSchema.Builder decimal(String key, String label) {
         return this.start(key, label, FieldType.DECIMAL);
      }

      public ActionFieldSchema.Builder text(String key, String label) {
         return this.start(key, label, FieldType.TEXT);
      }

      public ActionFieldSchema.Builder macroSelect(String key, String label) {
         return this.start(key, label, FieldType.MACRO_SELECT);
      }

      public ActionFieldSchema.Builder stringList(String key, String label) {
         return this.start(key, label, FieldType.STRING_LIST);
      }

      public ActionFieldSchema.Builder blockPos(String key, String label) {
         return this.start(key, label, FieldType.BLOCK_POS);
      }

      public ActionFieldSchema.Builder slot(String key, String label) {
         return this.start(key, label, FieldType.SLOT);
      }

      public ActionFieldSchema.Builder targetSummary(String key, String label) {
         return this.start(key, label, FieldType.TARGET_SUMMARY);
      }

      public ActionFieldSchema.Builder condition(String key, String label) {
         return this.start(key, label, FieldType.CONDITION);
      }

      public ActionFieldSchema.Builder capturePacketClick(String key, String label) {
         this.start(key, label, FieldType.CAPTURE_BUTTON);
         this.captureMode = CaptureMode.PACKET_CLICK_TARGET;
         return this;
      }

      public ActionFieldSchema.Builder enumField(String key, String label, String... options) {
         this.start(key, label, FieldType.ENUM);
         this.enumOpts = new ArrayList<>(Arrays.asList(options));
         return this;
      }

      public ActionFieldSchema.Builder range(int min, int max) {
         this.min = min;
         this.max = max;
         return this;
      }

      public ActionFieldSchema.Builder decRange(double min, double max) {
         this.decMin = min;
         this.decMax = max;
         return this;
      }

      public ActionFieldSchema.Builder showWhen(String key) {
         this.showWhenKey = key;
         this.showWhenInv = false;
         return this;
      }

      public ActionFieldSchema.Builder hideWhen(String key) {
         this.showWhenKey = key;
         this.showWhenInv = true;
         return this;
      }

      public ActionFieldSchema.Builder showWhenEnum(String key, String value) {
         if (key.equals(this.showWhenKey) && !this.showWhenInv && this.showWhenValue != null && !this.showWhenValue.isEmpty()) {
            this.showWhenValue = this.showWhenValue + "|" + value;
         } else {
            this.showWhenKey = key;
            this.showWhenInv = false;
            this.showWhenValue = value;
         }

         return this;
      }

      public ActionFieldSchema.Builder hideWhenEnum(String key, String value) {
         if (key.equals(this.showWhenKey) && this.showWhenInv && this.showWhenValue != null && !this.showWhenValue.isEmpty()) {
            this.showWhenValue = this.showWhenValue + "|" + value;
         } else {
            this.showWhenKey = key;
            this.showWhenInv = true;
            this.showWhenValue = value;
         }

         return this;
      }

      public ActionFieldSchema.Builder addLabel(String label) {
         this.addLabel = label;
         return this;
      }

      public ActionFieldSchema.Builder xyzKeys(String... keys) {
         this.xyzKeys = keys;
         return this;
      }

      public ActionFieldSchema.Builder xyzDouble(boolean v) {
         this.xyzDouble = v;
         return this;
      }

      public ActionFieldSchema.Builder captureBlock() {
         this.captureMode = CaptureMode.BLOCK_ID;
         return this;
      }

      public ActionFieldSchema.Builder captureEntity() {
         this.captureMode = CaptureMode.ENTITY_ID;
         return this;
      }

      public ActionFieldSchema.Builder captureCatalog() {
         this.captureMode = CaptureMode.BLOCK_CATALOG;
         return this;
      }

      public ActionFieldSchema.Builder captureItemSlot() {
         this.captureMode = CaptureMode.ITEM_SLOT;
         return this;
      }

      public ActionFieldSchema.Builder capturePacketName() {
         this.captureMode = CaptureMode.PACKET_NAME;
         return this;
      }

      public ActionFieldSchema.Builder captureMenuInput() {
         this.captureMode = CaptureMode.CUSTOM_MENU_INPUT;
         return this;
      }

      public ActionFieldSchema.Builder captureMenuButton() {
         this.captureMode = CaptureMode.CUSTOM_MENU_BUTTON;
         return this;
      }

      public ActionFieldSchema.Builder captureMenuTitle() {
         this.captureMode = CaptureMode.CUSTOM_MENU_TITLE;
         return this;
      }

      public ActionFieldSchema.Builder exclusiveWith(String... keys) {
         this.exclusiveWith = keys;
         return this;
      }

      public ActionFieldSchema.Builder dynamic() {
         this.dynamic = true;
         return this;
      }

      public ActionFieldSchema build() {
         this.commit();
         return new ActionFieldSchema(new ArrayList<>(this.fields));
      }
   }
}
