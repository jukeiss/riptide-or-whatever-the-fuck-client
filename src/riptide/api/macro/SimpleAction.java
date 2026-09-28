package riptide.api.macro;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class SimpleAction extends AddonAction {
   private final String displayName;
   private final String icon;
   private final Consumer<Minecraft> runner;

   public SimpleAction(String typeId, String displayName, String icon, Consumer<Minecraft> runner) {
      super(typeId);
      this.displayName = displayName != null && !displayName.isBlank() ? displayName : typeId;
      this.icon = icon != null && !icon.isBlank() ? icon : "*";
      this.runner = runner;
   }

   @Override
   protected void run(Minecraft mc) {
      if (this.runner != null) {
         this.runner.accept(mc);
      }
   }

   @Override
   public String getDisplayName() {
      return this.displayName;
   }

   @Override
   public String getIcon() {
      return this.icon;
   }
}
