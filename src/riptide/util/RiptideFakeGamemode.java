package riptide.util;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

public final class RiptideFakeGamemode {
   private static boolean fakeActive;
   private static boolean applyingFake;
   private static GameType serverKnownMode;
   private static GameType fakeMode;
   private static long realRevision;
   private static long fakeRevision;

   private RiptideFakeGamemode() {
   }

   public static synchronized RiptideFakeGamemode.Result apply(GameType mode) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null || mc.gameMode == null) {
         return RiptideFakeGamemode.Result.fail("Not connected.");
      } else if (mode == null) {
         return RiptideFakeGamemode.Result.fail("Unknown game mode.");
      } else {
         if (!fakeActive) {
            serverKnownMode = mc.gameMode.getPlayerMode();
         }

         GameType previousDisplayed = displayedMode();
         fakeActive = true;
         fakeMode = mode;
         if (previousDisplayed != mode) {
            fakeRevision++;
         }

         applyLocal(mc, mode);
         return RiptideFakeGamemode.Result.ok("Fake gamemode: " + mode.getName());
      }
   }

   public static synchronized RiptideFakeGamemode.Result reset() {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null || mc.gameMode == null) {
         clear();
         return RiptideFakeGamemode.Result.fail("Not connected.");
      } else if (!fakeActive) {
         serverKnownMode = mc.gameMode.getPlayerMode();
         return RiptideFakeGamemode.Result.ok("Fake gamemode already reset.");
      } else {
         GameType restore = serverKnownMode != null ? serverKnownMode : GameType.DEFAULT_MODE;
         GameType previousDisplayed = displayedMode();
         fakeActive = false;
         fakeMode = null;
         if (previousDisplayed != restore) {
            fakeRevision++;
         }

         applyLocal(mc, restore);
         return RiptideFakeGamemode.Result.ok("Fake gamemode reset: " + restore.getName());
      }
   }

   public static synchronized void onVanillaLocalMode(GameType mode) {
      if (!applyingFake && mode != null) {
         if (serverKnownMode != mode) {
            serverKnownMode = mode;
            realRevision++;
         }

         if (fakeActive && fakeMode != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode() != fakeMode) {
               applyLocal(mc, fakeMode);
            }
         }
      }
   }

   public static synchronized void clear() {
      fakeActive = false;
      applyingFake = false;
      serverKnownMode = null;
      fakeMode = null;
      realRevision++;
      fakeRevision++;
   }

   public static synchronized RiptideFakeGamemode.Snapshot snapshot() {
      Minecraft mc = Minecraft.getInstance();
      GameType current = mc != null && mc.gameMode != null ? mc.gameMode.getPlayerMode() : null;
      GameType real = serverKnownMode != null ? serverKnownMode : current;
      GameType displayed = fakeActive && fakeMode != null ? fakeMode : real;
      return new RiptideFakeGamemode.Snapshot(real, displayed, fakeActive, realRevision, fakeRevision);
   }

   public static GameType parseMode(String input) {
      if (input == null) {
         return null;
      } else {
         String var1 = input.trim().toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "s", "0", "survival" -> GameType.SURVIVAL;
            case "c", "1", "creative" -> GameType.CREATIVE;
            case "a", "2", "adventure" -> GameType.ADVENTURE;
            case "sp", "spec", "3", "spectator" -> GameType.SPECTATOR;
            default -> null;
         };
      }
   }

   public static String displayName(GameType mode) {
      if (mode == null) {
         return "Reset";
      } else {
         String name = mode.getName();
         return name.isEmpty() ? mode.name() : Character.toUpperCase(name.charAt(0)) + name.substring(1);
      }
   }

   private static void applyLocal(Minecraft mc, GameType mode) {
      applyingFake = true;

      try {
         mc.gameMode.setLocalMode(mode);
      } finally {
         applyingFake = false;
      }
   }

   private static GameType displayedMode() {
      if (fakeActive && fakeMode != null) {
         return fakeMode;
      } else if (serverKnownMode != null) {
         return serverKnownMode;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc != null && mc.gameMode != null ? mc.gameMode.getPlayerMode() : null;
      }
   }

   public record Result(boolean success, String message) {
      public static RiptideFakeGamemode.Result ok(String message) {
         return new RiptideFakeGamemode.Result(true, message == null ? "" : message);
      }

      public static RiptideFakeGamemode.Result fail(String message) {
         return new RiptideFakeGamemode.Result(false, message == null ? "" : message);
      }
   }

   public record Snapshot(GameType realMode, GameType displayedMode, boolean fakeActive, long realRevision, long fakeRevision) {
   }
}
