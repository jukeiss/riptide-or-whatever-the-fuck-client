package riptide.api.module;

public final class ActionSetting extends Setting<Void, ActionSetting> {
   private String buttonLabel = "Run";

   public ActionSetting(String name, String title, Runnable action) {
      super(Kind.ACTION, name, title, null);
      this.setAction(action);
   }

   public ActionSetting buttonLabel(String buttonLabel) {
      this.buttonLabel = buttonLabel != null && !buttonLabel.isBlank() ? buttonLabel : "Run";
      return this;
   }

   public String buttonLabel() {
      return this.buttonLabel;
   }

   protected Void decode(String raw) {
      return null;
   }

   protected String encode(Void value) {
      return "";
   }
}
