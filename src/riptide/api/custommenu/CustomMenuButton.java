package riptide.api.custommenu;

public record CustomMenuButton(int index, String label, String actionId, CustomMenuButton.Kind kind, String labelColor) {
   public CustomMenuButton(int index, String label, String actionId, CustomMenuButton.Kind kind) {
      this(index, label, actionId, kind, "");
   }

   public CustomMenuButton(int index, String label, String actionId, CustomMenuButton.Kind kind, String labelColor) {
      label = label == null ? "" : label;
      actionId = actionId == null ? "" : actionId;
      kind = kind == null ? CustomMenuButton.Kind.OTHER : kind;
      labelColor = labelColor == null ? "" : labelColor;
      this.index = index;
      this.label = label;
      this.actionId = actionId;
      this.kind = kind;
      this.labelColor = labelColor;
   }

   public boolean serverRelevant() {
      return this.kind == CustomMenuButton.Kind.CUSTOM || this.kind == CustomMenuButton.Kind.COMMAND || this.kind == CustomMenuButton.Kind.DIALOG;
   }

   public static enum Kind {
      CUSTOM,
      COMMAND,
      DIALOG,
      URL,
      CLIPBOARD,
      OTHER,
      EMPTY;
   }
}
