package riptide.modules;

import net.minecraft.core.BlockPos;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;

public final class CoordSnapperModule extends Module {
   private boolean wasDown;

   public CoordSnapperModule() {
      super("coord-snapper", "Coord Snapper", ModuleCategory.MISC, "Copies your current coordinates to the clipboard on a key.");
      this.add(new KeybindSetting("key", "Copy Key", -1).description("Press to copy your coordinates.").build());
      this.add(
         new ChoiceSetting("format", "Format", "Plain", "Plain", "Labelled", "Command")
            .description("Plain: 123 64 -512. Labelled: X: 123, Y: 64, Z: -512. Command: /tp 123 64 -512.")
            .build()
      );
      this.add(new BoolSetting("chat", "Confirm In Chat", true).description("Print a confirmation line when you copy.").build());
   }

   @Override
   public void tick() {
      if (MC.player == null) {
         this.wasDown = false;
      } else {
         int var1 = this.integer("key");
         boolean var2 = var1 != -1 && RiptideBindUtil.isBindPressed(MC, var1);
         if (var2 && !this.wasDown) {
            this.copy();
         }

         this.wasDown = var2;
      }
   }

   private void copy() {
      BlockPos var1 = MC.player.blockPosition();
      String var3 = this.choice("format");

      String var2 = switch (var3) {
         case "Labelled" -> "X: " + var1.getX() + ", Y: " + var1.getY() + ", Z: " + var1.getZ();
         case "Command" -> "/tp " + var1.getX() + " " + var1.getY() + " " + var1.getZ();
         default -> var1.getX() + " " + var1.getY() + " " + var1.getZ();
      };
      if (MC.keyboardHandler != null) {
         MC.keyboardHandler.setClipboard(var2);
      }

      if (this.bool("chat")) {
         RiptideClientMessaging.sendPrefixed("Copied: " + var2);
      }
   }
}
