package riptide.api.macro;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;

public final class SimpleCondition extends AddonContextAction {
   private final String displayName;
   private final String status;
   private final String icon;
   private final Predicate<Minecraft> predicate;

   public SimpleCondition(String typeId, String displayName, String status, String icon, Predicate<Minecraft> predicate) {
      super(typeId);
      this.displayName = displayName != null && !displayName.isBlank() ? displayName : typeId;
      this.status = status != null && !status.isBlank() ? status : this.displayName;
      this.icon = icon != null && !icon.isBlank() ? icon : "?";
      this.predicate = predicate;
   }

   @Override
   public void run(MacroExecutionContext ctx) {
      ctx.setStatus(this.status);
      ctx.awaitCondition(new AddonCondition() {
         {
            Objects.requireNonNull(SimpleCondition.this);
         }

         @Override
         public boolean check(Minecraft mc) {
            return SimpleCondition.this.predicate != null && SimpleCondition.this.predicate.test(mc);
         }
      });
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
