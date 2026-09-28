package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class ChunkReloaderModule extends Module {
   private int ticks;
   private int stage;
   private int savedDistance = -1;

   public ChunkReloaderModule() {
      super("chunk-reloader", "Chunk Reloader", ModuleCategory.MISC, "Reloads the chunks around you by cycling your render distance.");
      this.add(new IntSetting("low", "Low Distance", 2, 2, 16, 1).description("Render distance to drop to before restoring. Lower reloads more.").build());
      this.add(new IntSetting("delay", "Hold Ticks", 20, 1, 200, 1).description("How long to hold the low render distance (20 ticks = 1 second).").build());
      this.add(new BoolSetting("notify", "Notify", true).description("Print reload progress in chat.").build());
   }

   @Override
   public String info() {
      return this.savedDistance >= 0 ? "reloading" : "";
   }

   @Override
   public void onEnable() {
      if (MC.level != null && MC.player != null) {
         this.ticks = 0;
         this.stage = 0;
         this.savedDistance = (Integer)MC.options.renderDistance().get();
         MC.options.renderDistance().set(Math.min(this.integer("low"), this.savedDistance));
         this.progress(0);
      } else {
         this.finish("§cJoin a world first.");
      }
   }

   @Override
   public void onDisable() {
      this.restore();
      this.ticks = 0;
      this.stage = 0;
   }

   @Override
   public void onGameLeft() {
      this.savedDistance = -1;
      this.ticks = 0;
      this.stage = 0;
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         int var1 = this.integer("delay");
         this.ticks++;
         if (this.stage == 0 && this.ticks >= Math.max(1, var1 / 2)) {
            this.stage = 1;
            this.progress(50);
         }

         if (this.stage == 1 && this.ticks >= var1) {
            this.restore();
            this.stage = 2;
            this.progress(100);
         }

         if (this.stage == 2 && this.ticks >= var1 + 2) {
            this.finish(null);
         }
      } else {
         this.finish(null);
      }
   }

   private void restore() {
      if (this.savedDistance >= 0) {
         MC.options.renderDistance().set(this.savedDistance);
         this.savedDistance = -1;
      }
   }

   private void finish(String var1) {
      this.restore();
      if (var1 != null) {
         RiptideClientMessaging.sendPrefixed(var1);
      }

      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private void progress(int var1) {
      if (this.bool("notify")) {
         RiptideClientMessaging.sendPrefixed("§7Reloading chunks §b" + var1 + "%");
      }
   }
}
