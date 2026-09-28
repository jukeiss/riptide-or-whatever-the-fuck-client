package riptide.util.multi;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.modules.PackFreecamState;
import riptide.util.RiptideInventoryMoveHelper;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;

public final class MultiTakeoverState {
   private static volatile MultiTakeoverState.Phase phase = MultiTakeoverState.Phase.IDLE;
   private static volatile String targetId;
   private static final ConcurrentHashMap<String, long[]> availableCache = new ConcurrentHashMap<>();
   private static final long AVAILABLE_TTL_MS = 400L;

   private MultiTakeoverState() {
   }

   public static boolean isActive() {
      return phase != MultiTakeoverState.Phase.IDLE;
   }

   public static boolean isActive(String accountId) {
      return accountId != null && accountId.equals(targetId);
   }

   public static String activeAccountId() {
      return targetId;
   }

   public static boolean available(String accountId) {
      if (accountId == null) {
         return false;
      } else if (accountId.equals(targetId)) {
         return true;
      } else if (phase != MultiTakeoverState.Phase.IDLE) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.getConnection() != null) {
            MultiManager mgr = MultiManager.getIfInitialized();
            if (mgr != null && mgr.isActive()) {
               MultiSession session = mgr.session(accountId);
               if (session != null && session.ready()) {
                  long now = System.currentTimeMillis();
                  long[] cached = availableCache.get(accountId);
                  if (cached != null && now < cached[0]) {
                     return cached[1] != 0L;
                  } else {
                     boolean ok = findBotEntity(mc, session) != null;
                     availableCache.put(accountId, new long[]{now + 400L, ok ? 1L : 0L});
                     return ok;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static void toggle(String accountId, Screen screen) {
      if (accountId != null) {
         if (accountId.equals(targetId)) {
            exit();
         } else if (phase != MultiTakeoverState.Phase.IDLE) {
            note("Leave the current bot first.");
         } else {
            enter(accountId);
         }
      }
   }

   private static void enter(String accountId) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.getConnection() == null) {
         note("Join a world near the bot first.");
      } else if (PackFreecamState.isActive()) {
         note("Disable freecam first.");
      } else {
         MultiManager mgr = MultiManager.getIfInitialized();
         if (mgr != null && mgr.isActive()) {
            MultiSession session = mgr.session(accountId);
            if (session != null && session.ready()) {
               if (session.takeoverHealth() <= 0.0F) {
                  note("That bot is dead - wait for its respawn.");
               } else {
                  RemotePlayer bot = findBotEntity(mc, session);
                  if (bot == null) {
                     note("That bot isn't near you (must be visible in your world).");
                  } else {
                     try {
                        session.setPiloted(true);
                        MultiPilot.begin(session, bot);
                        MultiPilot.neutralizeMainPlayer(mc);
                        mc.setCameraEntity(bot);
                        mc.gui.setScreen((Screen)null);
                        RiptideOverlayManager.get().clearTextFieldFocus();
                        targetId = accountId;
                        phase = MultiTakeoverState.Phase.ACTIVE;
                        note("You are now " + label(accountId) + ".");
                     } catch (Throwable var8) {
                        riptide.RiptideClientAddon.LOG.error("POV pilot enter failed", var8);
                        note("Could not become that bot: " + var8.getClass().getSimpleName());

                        try {
                           MultiPilot.end(bot);
                        } catch (Throwable var7) {
                        }

                        session.setPiloted(false);
                        if (mc.player != null) {
                           mc.setCameraEntity(mc.player);
                        }

                        clearState();
                     }
                  }
               }
            } else {
               note("That bot isn't ready.");
            }
         } else {
            note("No bot batch is running.");
         }
      }
   }

   public static void exit() {
      if (phase != MultiTakeoverState.Phase.IDLE) {
         Minecraft mc = Minecraft.getInstance();

         try {
            Screen current = mc.gui.screen();
            if (current instanceof AbstractSignEditScreen || current instanceof BookEditScreen || current instanceof BookViewScreen) {
               mc.gui.setScreen((Screen)null);
            }
         } catch (Throwable var8) {
         }

         MultiManager mgr = MultiManager.getIfInitialized();
         MultiSession session = mgr == null ? null : mgr.session(targetId);
         RemotePlayer bot = session == null ? null : findBotEntity(mc, session);
         boolean macroAuthority = session != null && session.macroOwnsPilot();

         try {
            MultiPilot.end(bot);
         } catch (Throwable var7) {
            riptide.RiptideClientAddon.LOG.error("POV pilot end failed", var7);
         }

         if (mc.player != null) {
            mc.setCameraEntity(mc.player);
         }

         if (session != null) {
            try {
               if (!macroAuthority && session.ready()) {
                  session.resumeAfterPilot();
               }
            } catch (Throwable var6) {
               riptide.RiptideClientAddon.LOG.warn("POV exit position sync failed", var6);
            }

            session.setPiloted(false);
         }

         restoreMultiUi(mc);
         clearState();
         RiptideInventoryMoveHelper.resyncMovementKeysAfterPov();
      }
   }

   public static void tick() {
      if (phase != MultiTakeoverState.Phase.IDLE) {
         Minecraft mc = Minecraft.getInstance();
         if (!PackFreecamState.isActive() && (mc.gui.screen() instanceof InventoryScreen || mc.gui.screen() instanceof CreativeModeInventoryScreen)) {
            mc.gui.setScreen((Screen)null);
         }

         MultiManager mgr = MultiManager.getIfInitialized();
         MultiSession session = mgr == null ? null : mgr.session(targetId);
         if (session == null || mc.level == null || mc.player == null) {
            exit();
         } else if (!session.ready()) {
            MultiSession.Status status = session.statusValue();
            boolean switching = status == MultiSession.Status.CONFIGURING
               || status == MultiSession.Status.CONNECTING
               || status == MultiSession.Status.LOGIN
               || status == MultiSession.Status.JOINED;
            exit();
            if (switching) {
               note("Bot switched servers - session continues headless.");
            }
         } else if (session.takeoverHealth() <= 0.0F) {
            exit();
            note("Bot died - POV ended.");
         } else {
            RemotePlayer bot = findBotEntity(mc, session);
            if (bot != null && !bot.isRemoved() && !bot.isPassenger()) {
               if (mc.getCameraEntity() != bot) {
                  exit();
               }
            } else {
               exit();
               if (bot == null || bot.isRemoved()) {
                  note("Bot changed world - session stays connected.");
               }
            }
         }
      }
   }

   private static RemotePlayer findBotEntity(Minecraft mc, MultiSession session) {
      if (mc.level == null) {
         return null;
      } else {
         try {
            UUID uuid = session.serverUuid();
            if (uuid != null && mc.level.getPlayerByUUID(uuid) instanceof RemotePlayer byUuid) {
               return byUuid;
            }

            String name = session.takeoverProfile() == null ? null : session.takeoverProfile().name();
            if (name != null && !name.isBlank()) {
               for (Player player : mc.level.players()) {
                  if (player != mc.player && player instanceof RemotePlayer remote && player.getName().getString().equalsIgnoreCase(name)) {
                     return remote;
                  }
               }
            }
         } catch (Throwable var7) {
         }

         return null;
      }
   }

   private static void restoreMultiUi(Minecraft mc) {
      try {
         if (mc.gui.screen() instanceof RiptideOverlayHostScreen) {
            mc.gui.setScreen((Screen)null);
         }
      } catch (Throwable var2) {
      }
   }

   private static void clearState() {
      phase = MultiTakeoverState.Phase.IDLE;
      targetId = null;
      availableCache.clear();
   }

   private static String label(String accountId) {
      MultiManager mgr = MultiManager.getIfInitialized();
      if (mgr != null) {
         for (MultiSession.Snapshot s : mgr.snapshots()) {
            if (s.accountId().equals(accountId)) {
               return s.accountName();
            }
         }
      }

      return accountId == null ? "bot" : accountId;
   }

   private static void note(String message) {
      try {
         RiptideNotifications.show(message, -14249);
      } catch (Throwable var2) {
      }
   }

   private static enum Phase {
      IDLE,
      ACTIVE;
   }
}
