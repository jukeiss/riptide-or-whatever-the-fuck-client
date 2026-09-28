package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideFakeGamemode;
import riptide.util.RiptideGamemode;

public class FakeGamemodeAction implements MacroAction {
   public FakeGamemodeAction.Mode mode = FakeGamemodeAction.Mode.RESET;
   public FakeGamemodeAction.Method method = FakeGamemodeAction.Method.FAKE;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      RiptideFakeGamemode.Result result;
      if (this.mode == FakeGamemodeAction.Mode.RESET) {
         result = RiptideFakeGamemode.reset();
      } else if (this.method == FakeGamemodeAction.Method.REAL) {
         result = RiptideGamemode.real(toGameType(this.mode));
      } else {
         result = RiptideFakeGamemode.apply(toGameType(this.mode));
      }

      RiptideClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("mode", this.mode.name());
      tag.putString("method", this.method.name());
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("mode")) {
         try {
            this.mode = FakeGamemodeAction.Mode.valueOf(tag.getStringOr("mode", "RESET"));
         } catch (IllegalArgumentException var4) {
            this.mode = FakeGamemodeAction.Mode.RESET;
         }
      }

      try {
         this.method = FakeGamemodeAction.Method.valueOf(tag.getStringOr("method", "FAKE"));
      } catch (IllegalArgumentException var3) {
         this.method = FakeGamemodeAction.Method.FAKE;
      }

      this.enabled = tag.getBooleanOr("enabled", true);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.FAKE_GAMEMODE;
   }

   @Override
   public String getDisplayName() {
      return this.mode == FakeGamemodeAction.Mode.RESET
         ? "GM: Reset"
         : "GM: " + displayMode(this.mode) + (this.method == FakeGamemodeAction.Method.REAL ? " (Real)" : " (Fake)");
   }

   @Override
   public String getIcon() {
      return "GM";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   private static GameType toGameType(FakeGamemodeAction.Mode mode) {
      return switch (mode) {
         case SURVIVAL, RESET -> GameType.SURVIVAL;
         case CREATIVE -> GameType.CREATIVE;
         case ADVENTURE -> GameType.ADVENTURE;
         case SPECTATOR -> GameType.SPECTATOR;
      };
   }

   private static String displayMode(FakeGamemodeAction.Mode mode) {
      if (mode == null) {
         return "Reset";
      } else {
         String lower = mode.name().toLowerCase(Locale.ROOT);
         return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
      }
   }

   public static enum Method {
      REAL,
      FAKE;
   }

   public static enum Mode {
      SURVIVAL,
      CREATIVE,
      ADVENTURE,
      SPECTATOR,
      RESET;
   }
}
