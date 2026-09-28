package riptide.modules;

import net.minecraft.client.gui.screens.DeathScreen;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;

public final class AutoRespawnModule extends Module {
   private int delayTicks = -1;
   private int commandTicks = -1;

   public AutoRespawnModule() {
      super("auto-respawn", "Auto Respawn", ModuleCategory.PLAYER, "Respawns you the moment you die.");
      this.add(new IntSetting("delay", "Delay", 2, 0, 100, 1).description("Ticks to wait before respawning.").build());
      this.add(new StringSetting("command", "Command After", "").description("Optional command once you respawn, e.g. /home").build());
      this.add(
         new IntSetting("command-delay", "Command Delay", 20, 1, 200, 1)
            .description("Ticks after respawning before the command is sent.")
            .visibleWhen(() -> !this.text("command").isBlank())
            .build()
      );
      this.add(
         new BoolSetting("only-overworld", "Skip In Void", false)
            .description("Don't auto-respawn while the death screen says you fell out of the world.")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.delayTicks = -1;
      this.commandTicks = -1;
   }

   @Override
   public void onGameLeft() {
      this.onEnable();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.getConnection() != null) {
         if (this.commandTicks > 0 && --this.commandTicks == 0) {
            String var1 = this.text("command");
            if (!var1.isBlank()) {
               this.sendCommand(var1);
            }

            this.commandTicks = -1;
         }

         if (!(MC.gui.screen() instanceof DeathScreen)) {
            this.delayTicks = -1;
         } else {
            if (this.delayTicks < 0) {
               this.delayTicks = this.integer("delay");
            }

            if (this.delayTicks > 0) {
               this.delayTicks--;
            } else {
               MC.player.respawn();
               MC.gui.setScreen(null);
               this.delayTicks = -1;
               if (!this.text("command").isBlank()) {
                  this.commandTicks = this.integer("command-delay");
               }
            }
         }
      }
   }
}
