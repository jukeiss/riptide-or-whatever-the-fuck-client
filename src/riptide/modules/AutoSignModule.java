package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.StringSetting;

public final class AutoSignModule extends Module {
   AutoSignModule() {
      super("auto-sign", "AutoSign", ModuleCategory.PLAYER, "Fills signs with configured text.");
      this.add(new StringSetting("line-1", "Line 1", "").description("Text for sign line one.").build());
      this.add(new StringSetting("line-2", "Line 2", "").description("Text for sign line two.").build());
      this.add(new StringSetting("line-3", "Line 3", "").description("Text for sign line three.").build());
      this.add(new StringSetting("line-4", "Line 4", "").description("Text for sign line four.").build());
      this.add(new BoolSetting("edit-existing", "Also When Editing", false).description("Replace text when editing signs.").build());
      this.add(new BoolSetting("auto-done", "Auto Done", true).description("Close editor after filling text.").build());
   }

   public String[] signLines() {
      return new String[]{this.text("line-1"), this.text("line-2"), this.text("line-3"), this.text("line-4")};
   }

   public boolean editExisting() {
      return this.bool("edit-existing");
   }

   public boolean autoDone() {
      return this.bool("auto-done");
   }
}
