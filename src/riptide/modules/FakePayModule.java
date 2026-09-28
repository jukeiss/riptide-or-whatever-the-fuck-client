package riptide.modules;

import net.minecraft.client.Minecraft;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;

/**
 * Prints a made-up transaction line into your own chat, for screenshots and video.
 * It is display only: nothing is sent to the server and no other player's client is
 * touched, so this cannot move money or make anyone else see the line.
 */
public final class FakePayModule extends Module {
   private boolean keyWasDown;

   public FakePayModule() {
      super("fake-pay", "Fake Pay", ModuleCategory.MISC, "Prints a fake payment line in your own chat for thumbnails and video. Local only.");
      this.add(
         new ChoiceSetting("direction", "Direction", "Received", "Received", "Sent")
            .description("Whether the line reads as money coming in or going out.")
            .build()
      );
      this.add(new StringSetting("player", "Player", "Notch").description("The other name shown in the line.").build());
      this.add(new StringSetting("amount", "Amount", "10,000,000").description("The amount shown. Typed exactly as you write it.").build());
      this.add(
         new StringSetting("received-format", "Received Line", "&a&l(!) &fYou have received &a$<amount> &ffrom &a<player>")
            .description("Template for an incoming payment. <player> and <amount> are filled in; & is a colour code.")
            .group("Format")
            .build()
      );
      this.add(
         new StringSetting("sent-format", "Sent Line", "&c&l(!) &fYou have sent &c$<amount> &fto &c<player>")
            .description("Template for an outgoing payment. <player> and <amount> are filled in; & is a colour code.")
            .group("Format")
            .build()
      );
      this.add(new KeybindSetting("bind", "Print Key", -1).description("Press to print the line. Unbound by default.").group("Trigger").build());
      this.add(
         new BoolSetting("only-in-world", "Only In World", true)
            .description("Ignore the key while a screen or the chat box is open.")
            .group("Trigger")
            .build()
      );
      this.add(new ActionSetting("print", "Print Now", this::print).buttonLabel("Print").group("Trigger").description("Print one line right now.").build());
   }

   @Override
   public void onEnable() {
      this.keyWasDown = false;
   }

   @Override
   public String info() {
      return this.choice("direction");
   }

   @Override
   public void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null) {
         int bind = this.integer("bind");
         boolean down = bind != -1 && (!this.bool("only-in-world") || mc.gui.screen() == null) && RiptideBindUtil.isBindPressed(mc, bind);
         boolean pressed = down && !this.keyWasDown;
         this.keyWasDown = down;
         if (pressed) {
            this.print();
         }
      } else {
         this.keyWasDown = false;
      }
   }

   private void print() {
      try {
         boolean sent = "Sent".equals(this.choice("direction"));
         String template = this.text(sent ? "sent-format" : "received-format");
         if (template != null && !template.isBlank()) {
            String line = template.replace("<player>", this.text("player")).replace("<amount>", this.text("amount")).replace('&', '§');
            RiptideClientMessaging.send(line);
         }
      } catch (Throwable var4) {
      }
   }
}
