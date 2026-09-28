package riptide.modules;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideKeyMappingBridge;

public final class MacroRecorderModule extends Module {
   private static final int BIT_FORWARD = 1;
   private static final int BIT_BACK = 2;
   private static final int BIT_LEFT = 4;
   private static final int BIT_RIGHT = 8;
   private static final int BIT_JUMP = 16;
   private static final int BIT_SNEAK = 32;
   private static final int BIT_SPRINT = 64;
   private static final int BIT_ATTACK = 128;
   private static final int BIT_USE = 256;
   private static final int DEFAULT_RECORD_KEY = 298;
   private static final int DEFAULT_PLAY_KEY = 299;
   private final List<MacroRecorderModule.Frame> frames = new ArrayList<>();
   private MacroRecorderModule.Mode mode = MacroRecorderModule.Mode.IDLE;
   private int playIndex;
   private double playCursor;
   private boolean recordKeyWasDown;
   private boolean playKeyWasDown;
   private int loadedSlot = -1;

   public MacroRecorderModule() {
      super("macro-recorder", "Macro Recorder", ModuleCategory.MISC, "Records and replays your mouse and keys.");
      this.add(new KeybindSetting("record-key", "Record Key", 298).description("Press to start/stop recording. Default F9.").build());
      this.add(new KeybindSetting("play-key", "Play Key", 299).description("Press to start/stop playback. Default F10.").build());
      this.add(new BoolSetting("loop", "Loop", false).description("Repeat the recording until stopped.").build());
      this.add(new IntSetting("speed", "Speed %", 100, 25, 400, 25).description("Playback rate. 50% is half speed, 200% is double.").build());
      this.add(new IntSetting("slot", "Save Slot", 1, 1, 5, 1).description("Five separate recordings. Switching slots loads that one.").build());
      this.add(new ActionSetting("clear", "Clear", this::clearCurrentSlot).buttonLabel("Erase").description("Delete the recording in this slot.").build());
      this.add(new BoolSetting("look", "Look", true).description("Mouse movement (yaw and pitch).").group("Capture").build());
      this.add(new BoolSetting("movement", "Movement", true).description("WASD, jump, sneak and sprint.").group("Capture").build());
      this.add(new BoolSetting("clicks", "Clicks", true).description("Attack and use buttons.").group("Capture").build());
      this.add(new BoolSetting("hotbar", "Hotbar", true).description("Selected hotbar slot.").group("Capture").build());
   }

   @Override
   public String info() {
      return switch (this.mode) {
         case IDLE -> this.frames.isEmpty() ? "empty" : this.frames.size() + " frames";
         case RECORDING -> "REC " + this.frames.size();
         case PLAYING -> this.playIndex + "/" + this.frames.size();
      };
   }

   @Override
   public void onEnable() {
      this.ensureLoaded();
      this.mode = MacroRecorderModule.Mode.IDLE;
      this.recordKeyWasDown = false;
      this.playKeyWasDown = false;
      RiptideClientMessaging.sendPrefixed(
         "§7Slot §f"
            + this.integer("slot")
            + "§7: §f"
            + keyName(this.bindCode("record-key"))
            + "§7 records, §f"
            + keyName(this.bindCode("play-key"))
            + "§7 plays. "
            + (this.frames.isEmpty() ? "§7Slot is empty." : "§f" + this.frames.size() + "§7 frames stored.")
      );
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if ("slot".equals(var1)) {
         this.stopEverything();
         this.ensureLoaded();
         RiptideClientMessaging.sendPrefixed(
            "§7Slot §f" + this.integer("slot") + "§7: " + (this.frames.isEmpty() ? "empty." : "§f" + this.frames.size() + "§7 frames.")
         );
      }
   }

   private void ensureLoaded() {
      int var1 = this.integer("slot");
      if (this.loadedSlot != var1) {
         this.load();
         this.loadedSlot = var1;
      }
   }

   private void clearCurrentSlot() {
      this.stopEverything();
      this.frames.clear();
      this.loadedSlot = this.integer("slot");

      try {
         Files.deleteIfExists(this.file());
      } catch (IOException var2) {
      }

      RiptideClientMessaging.sendPrefixed("§aSlot §f" + this.integer("slot") + "§a cleared.");
   }

   private static String keyName(int var0) {
      return var0 == -1 ? "§cunbound" : RiptideBindUtil.getBindName(var0);
   }

   @Override
   public void onDisable() {
      this.stopEverything();
   }

   @Override
   public void onGameLeft() {
      this.stopEverything();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.options != null) {
         this.pollKeys();
         if (MC.gui.screen() != null) {
            if (this.mode == MacroRecorderModule.Mode.PLAYING) {
               this.releaseInput();
            }
         } else {
            switch (this.mode) {
               case IDLE:
               default:
                  break;
               case RECORDING:
                  this.captureFrame();
                  break;
               case PLAYING:
                  this.playFrame();
            }
         }
      } else {
         this.stopEverything();
      }
   }

   private void pollKeys() {
      boolean var1 = this.bindDown("record-key");
      if (var1 && !this.recordKeyWasDown) {
         this.toggleRecording();
      }

      this.recordKeyWasDown = var1;
      boolean var2 = this.bindDown("play-key");
      if (var2 && !this.playKeyWasDown) {
         this.togglePlayback();
      }

      this.playKeyWasDown = var2;
   }

   private boolean bindDown(String var1) {
      int var2 = this.bindCode(var1);
      return var2 != -1 && RiptideBindUtil.isBindPressed(MC, var2);
   }

   private void toggleRecording() {
      if (this.mode == MacroRecorderModule.Mode.RECORDING) {
         this.mode = MacroRecorderModule.Mode.IDLE;
         this.save();
         RiptideClientMessaging.sendPrefixed("§aRecorded §f" + this.frames.size() + "§a frames.");
      } else {
         this.releaseInput();
         this.frames.clear();
         this.playIndex = 0;
         this.playCursor = 0.0;
         this.mode = MacroRecorderModule.Mode.RECORDING;
         RiptideClientMessaging.sendPrefixed("§eRecording... press the key again to stop.");
      }
   }

   private void togglePlayback() {
      if (this.mode == MacroRecorderModule.Mode.PLAYING) {
         this.releaseInput();
         this.mode = MacroRecorderModule.Mode.IDLE;
         RiptideClientMessaging.sendPrefixed("§ePlayback stopped.");
      } else if (this.frames.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cNothing recorded yet.");
      } else {
         if (this.mode == MacroRecorderModule.Mode.RECORDING) {
            this.save();
         }

         this.playIndex = 0;
         this.playCursor = 0.0;
         this.mode = MacroRecorderModule.Mode.PLAYING;
         RiptideClientMessaging.sendPrefixed("§aPlaying §f" + this.frames.size() + "§a frames.");
      }
   }

   private void captureFrame() {
      short var1 = 0;
      if (this.held(MC.options.keyUp)) {
         var1 |= 1;
      }

      if (this.held(MC.options.keyDown)) {
         var1 |= 2;
      }

      if (this.held(MC.options.keyLeft)) {
         var1 |= 4;
      }

      if (this.held(MC.options.keyRight)) {
         var1 |= 8;
      }

      if (this.held(MC.options.keyJump)) {
         var1 |= 16;
      }

      if (this.held(MC.options.keyShift)) {
         var1 |= 32;
      }

      if (this.held(MC.options.keySprint)) {
         var1 |= 64;
      }

      if (this.held(MC.options.keyAttack)) {
         var1 |= 128;
      }

      if (this.held(MC.options.keyUse)) {
         var1 |= 256;
      }

      this.frames.add(new MacroRecorderModule.Frame(MC.player.getYRot(), MC.player.getXRot(), var1, MC.player.getInventory().getSelectedSlot()));
   }

   private void playFrame() {
      if (this.playIndex >= this.frames.size()) {
         if (!this.bool("loop")) {
            this.releaseInput();
            this.mode = MacroRecorderModule.Mode.IDLE;
            RiptideClientMessaging.sendPrefixed("§aPlayback finished.");
            return;
         }

         this.playIndex = 0;
         this.playCursor = 0.0;
      }

      MacroRecorderModule.Frame var1 = this.frames.get(Math.min(this.playIndex, this.frames.size() - 1));
      if (this.bool("look")) {
         MC.player.setYRot(var1.yaw());
         MC.player.setYHeadRot(var1.yaw());
         MC.player.setXRot(var1.pitch());
      }

      if (this.bool("movement")) {
         press(MC.options.keyUp, (var1.mask() & 1) != 0);
         press(MC.options.keyDown, (var1.mask() & 2) != 0);
         press(MC.options.keyLeft, (var1.mask() & 4) != 0);
         press(MC.options.keyRight, (var1.mask() & 8) != 0);
         press(MC.options.keyJump, (var1.mask() & 16) != 0);
         press(MC.options.keyShift, (var1.mask() & 32) != 0);
         press(MC.options.keySprint, (var1.mask() & 64) != 0);
      }

      if (this.bool("clicks")) {
         RiptideInputClicker.setAttackHeld((var1.mask() & 128) != 0);
         RiptideInputClicker.setUseHeld((var1.mask() & 256) != 0);
      }

      if (this.bool("hotbar") && var1.slot() != MC.player.getInventory().getSelectedSlot()) {
         RiptideInputClicker.queueHotbarSlot(var1.slot());
      }

      this.playCursor = this.playCursor + this.integer("speed") / 100.0;
      this.playIndex = (int)this.playCursor;
   }

   private void releaseInput() {
      if (MC.options != null) {
         press(MC.options.keyUp, false);
         press(MC.options.keyDown, false);
         press(MC.options.keyLeft, false);
         press(MC.options.keyRight, false);
         press(MC.options.keyJump, false);
         press(MC.options.keyShift, false);
         press(MC.options.keySprint, false);
      }

      RiptideInputClicker.setAttackHeld(false);
      RiptideInputClicker.setUseHeld(false);
   }

   private void stopEverything() {
      if (this.mode == MacroRecorderModule.Mode.PLAYING) {
         this.releaseInput();
      }

      this.mode = MacroRecorderModule.Mode.IDLE;
      this.playIndex = 0;
      this.playCursor = 0.0;
      this.recordKeyWasDown = false;
      this.playKeyWasDown = false;
   }

   private static boolean down(KeyMapping var0) {
      return var0 != null && RiptideKeyMappingBridge.of(var0).riptide$isActuallyDown();
   }

   private boolean held(KeyMapping var1) {
      return down(var1) && !this.isControlKey(var1);
   }

   private boolean isControlKey(KeyMapping var1) {
      if (var1 == null) {
         return false;
      } else {
         Key var2 = bindKey(this.bindCode("record-key"));
         if (var2 != null && var1.matches(var2)) {
            return true;
         } else {
            Key var3 = bindKey(this.bindCode("play-key"));
            return var3 != null && var1.matches(var3);
         }
      }
   }

   private int bindCode(String var1) {
      try {
         return Integer.parseInt(this.value(var1));
      } catch (NumberFormatException var3) {
         return -1;
      }
   }

   private static Key bindKey(int var0) {
      if (var0 == -1) {
         return null;
      } else {
         return RiptideBindUtil.isMouseBind(var0) ? Type.MOUSE.getOrCreate(RiptideBindUtil.decodeMouseButton(var0)) : Type.KEYSYM.getOrCreate(var0);
      }
   }

   private static void press(KeyMapping var0, boolean var1) {
      if (var0 != null) {
         RiptideKeyMappingBridge.of(var0).riptide$simulatePress(var1);
      }
   }

   private Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("riptide").resolve("recording-" + this.integer("slot") + ".txt");
   }

   private void save() {
      StringBuilder var1 = new StringBuilder();

      for (MacroRecorderModule.Frame var3 : this.frames) {
         var1.append(var3.yaw()).append(',').append(var3.pitch()).append(',').append(var3.mask()).append(',').append(var3.slot()).append('\n');
      }

      try {
         Path var5 = this.file();
         Files.createDirectories(var5.getParent());
         Files.writeString(var5, var1.toString(), StandardCharsets.UTF_8);
      } catch (IOException var4) {
      }
   }

   private void load() {
      try {
         Path var1 = this.file();
         if (!Files.isRegularFile(var1)) {
            return;
         }

         this.frames.clear();

         for (String var3 : Files.readAllLines(var1, StandardCharsets.UTF_8)) {
            String[] var4 = var3.split(",");
            if (var4.length == 4) {
               this.frames
                  .add(
                     new MacroRecorderModule.Frame(Float.parseFloat(var4[0]), Float.parseFloat(var4[1]), Integer.parseInt(var4[2]), Integer.parseInt(var4[3]))
                  );
            }
         }
      } catch (NumberFormatException | IOException var5) {
         this.frames.clear();
      }
   }

   private record Frame(float yaw, float pitch, int mask, int slot) {
   }

   private static enum Mode {
      IDLE,
      RECORDING,
      PLAYING;
   }
}
