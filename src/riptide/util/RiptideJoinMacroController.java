package riptide.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.util.custommenu.CustomMenuScreens;
import riptide.util.custommenu.CustomMenuTracker;
import riptide.util.macro.CustomMenuAction;
import riptide.util.macro.CustomMenuActionSupport;
import riptide.util.macro.DelayAction;
import riptide.util.macro.MacroAction;
import riptide.util.macro.WaitForGuiAction;
import riptide.util.macro.WaitGuiTypeAction;
import riptide.util.mm.crypto.AtRestSeal;
import riptide.util.multi.MultiProfile;

public final class RiptideJoinMacroController {
   private static boolean executedThisConnection;
   private static boolean playJoinSeen;
   private static boolean worldReadySeen;
   private static int worldTicksSeen;
   private static int playableTicksSeen;
   private static int joinOrdinal;
   private static volatile RiptideMacro armedRemainder;
   private static volatile ClientConfigurationPacketListenerImpl configurationListener;
   private static volatile Thread preJoinThread;

   private RiptideJoinMacroController() {
   }

   public static RiptideJoinMacroController.Timing timing() {
      return RiptideJoinMacroController.Timing.fromConfig(RiptideConfig.getGlobal().joinMacroTiming);
   }

   public static void setTiming(RiptideJoinMacroController.Timing timing) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroTiming = (timing == null ? RiptideJoinMacroController.Timing.WORLD : timing).id();
      resetSequence();
      config.save();
   }

   public static void setSelectedMacro(String macroName) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroName = macroName == null ? "" : macroName.trim();
      config.joinMacroEnabled = !config.joinMacroName.isBlank();
      resetSequence();
      config.save();
   }

   public static void setEnabled(boolean enabled) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroEnabled = enabled && config.joinMacroName != null && !config.joinMacroName.isBlank();
      resetSequence();
      config.save();
   }

   public static RiptideJoinMacroController.TriggerJoin triggerJoin() {
      return normalizeTriggerJoin(RiptideJoinMacroController.TriggerJoin.fromConfig(RiptideConfig.getGlobal().joinMacroTriggerJoin), keepEnabled());
   }

   public static void setTriggerJoin(RiptideJoinMacroController.TriggerJoin triggerJoin) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroTriggerJoin = normalizeTriggerJoin(
            triggerJoin == null ? RiptideJoinMacroController.TriggerJoin.FIRST : triggerJoin, config.joinMacroKeepEnabled
         )
         .id();
      resetSequence();
      config.save();
   }

   public static boolean keepEnabled() {
      return RiptideConfig.getGlobal().joinMacroKeepEnabled;
   }

   public static void setKeepEnabled(boolean keepEnabled) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroKeepEnabled = keepEnabled;
      config.joinMacroTriggerJoin = normalizeTriggerJoin(RiptideJoinMacroController.TriggerJoin.fromConfig(config.joinMacroTriggerJoin), keepEnabled).id();
      resetSequence();
      config.save();
   }

   public static String selectedMacroName() {
      String name = RiptideConfig.getGlobal().joinMacroName;
      return name == null ? "" : name.trim();
   }

   public static boolean enabled() {
      return selectedMacroName().length() > 0;
   }

   public static void resetForJoin() {
      playJoinSeen = false;
      worldReadySeen = false;
      worldTicksSeen = 0;
      playableTicksSeen = 0;
   }

   public static void onPlayJoin() {
      resetForJoin();
      executedThisConnection = false;
      playJoinSeen = true;
      if (timing() == RiptideJoinMacroController.Timing.JOINING) {
         executeOnce(RiptideJoinMacroController.Timing.JOINING);
      }
   }

   public static void onWorldReady() {
      worldReadySeen = true;
      if (timing() == RiptideJoinMacroController.Timing.WORLD) {
         executeOnce(RiptideJoinMacroController.Timing.WORLD);
      }
   }

   public static void onClientTick(Minecraft mc) {
      if (enabled() && !executedThisConnection) {
         if (mc != null) {
            boolean worldReady = isWorldReady(mc);
            if (!worldReady) {
               worldReadySeen = false;
               worldTicksSeen = 0;
               playableTicksSeen = 0;
            } else {
               if (!worldReadySeen) {
                  worldReadySeen = true;
                  worldTicksSeen = 0;
               }

               worldTicksSeen++;
               RiptideJoinMacroController.Timing timing = timing();
               switch (timing) {
                  case JOINING:
                     if (playJoinSeen) {
                        executeOnce(RiptideJoinMacroController.Timing.JOINING);
                     }
                     break;
                  case WORLD:
                     executeOnce(RiptideJoinMacroController.Timing.WORLD);
                     break;
                  case FIRST_TICK:
                     if (worldTicksSeen >= 1) {
                        executeOnce(RiptideJoinMacroController.Timing.FIRST_TICK);
                     }
                     break;
                  case INVENTORY_READY:
                     if (mc.player != null && mc.player.inventoryMenu != null && mc.player.getInventory() != null) {
                        executeOnce(RiptideJoinMacroController.Timing.INVENTORY_READY);
                     }
                     break;
                  case PLAYABLE:
                     if (isPlayable(mc)) {
                        executeOnce(RiptideJoinMacroController.Timing.PLAYABLE);
                     }
                     break;
                  case FULLY_READY:
                     if (isPlayable(mc)) {
                        playableTicksSeen++;
                        if (playableTicksSeen >= 2) {
                           executeOnce(RiptideJoinMacroController.Timing.FULLY_READY);
                        }
                     } else {
                        playableTicksSeen = 0;
                     }
               }
            }
         }
      }
   }

   public static void onGameLeft() {
      resetForJoin();
      executedThisConnection = false;
      armedRemainder = null;
      CustomMenuTracker.clear();
      configurationListener = null;
      Thread worker = preJoinThread;
      preJoinThread = null;
      if (worker != null) {
         worker.interrupt();
      }
   }

   public static void onConfigurationInit(ClientConfigurationPacketListenerImpl listener) {
      resetForJoin();
      executedThisConnection = false;
      armedRemainder = null;
      CustomMenuTracker.clear();
      configurationListener = listener;
      joinOrdinal++;
      if (enabled() && triggerJoin().matches(joinOrdinal)) {
         RiptideMacro selected = RiptideMacroManager.get().get(selectedMacroName());
         if (selected != null) {
            RiptideMacro copy = selected.deepCopy();
            int prefix = 0;

            boolean sawCustomMenu;
            for (sawCustomMenu = false; prefix < copy.actions.size(); prefix++) {
               MacroAction action = copy.actions.get(prefix);
               if (action instanceof CustomMenuAction) {
                  sawCustomMenu = true;
               } else if (!isPreJoinWait(action)) {
                  break;
               }
            }

            armedRemainder = copy.deepCopy();
            if (prefix > 0) {
               armedRemainder.actions.subList(0, prefix).clear();
            }

            if (prefix != 0 && sawCustomMenu) {
               List<MacroAction> leading = List.copyOf(copy.actions.subList(0, prefix));
               Thread worker = new Thread(() -> runPreJoinActions(leading), "RIPTIDE-Join-CustomMenu");
               worker.setDaemon(true);
               preJoinThread = worker;
               worker.start();
            }
         }
      }
   }

   private static boolean isPreJoinWait(MacroAction action) {
      return action instanceof WaitForGuiAction || action instanceof WaitGuiTypeAction || action instanceof DelayAction;
   }

   public static boolean isDrivingCustomMenu() {
      Thread worker = preJoinThread;
      return worker != null && worker.isAlive();
   }

   public static boolean sendCommonPacket(Packet<?> packet) {
      if (packet == null) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getConnection() != null) {
            mc.getConnection().send(packet);
            return true;
         } else {
            ClientConfigurationPacketListenerImpl listener = configurationListener;
            if (listener != null) {
               listener.send(packet);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   public static void onConfigurationDisconnect() {
      onGameLeft();
   }

   private static void runPreJoinActions(List<MacroAction> actions) {
      for (MacroAction macroAction : actions) {
         if (macroAction != null && macroAction.isEnabled()) {
            if (isPreJoinWait(macroAction)) {
               waitPreJoin(macroAction);
               if (Thread.currentThread().isInterrupted()) {
                  return;
               }
            } else if (macroAction instanceof CustomMenuAction action) {
               long deadline = System.currentTimeMillis() + action.boundedTimeout();
               Minecraft mc = Minecraft.getInstance();
               CustomMenuSnapshot snapshot = null;
               long seenGeneration = 0L;
               CustomMenuActionSupport.Prepared prepared = null;
               String lastError = "";

               while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
                  seenGeneration = CustomMenuTracker.generation();
                  snapshot = CustomMenuTracker.current();
                  if (snapshot != null) {
                     seenGeneration = snapshot.generation();
                  } else {
                     snapshot = CustomMenuScreens.openScreenSnapshot(mc);
                  }

                  if (snapshot == null) {
                     LockSupport.parkNanos(20000000L);
                  } else {
                     prepared = CustomMenuActionSupport.prepare(action, snapshot, RiptideJoinMacroController::resolveFormTemplate);
                     if (prepared.success() || prepared.error() == null || !prepared.error().contains("unavailable")) {
                        break;
                     }

                     lastError = prepared.error();
                     prepared = null;
                     LockSupport.parkNanos(20000000L);
                  }
               }

               if (snapshot != null && prepared != null) {
                  if (!prepared.success()) {
                     skipPreJoin(prepared.error());
                  } else {
                     Screen answered = CustomMenuScreens.openScreen(mc);
                     CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, prepared.submission());
                     if (!result.success()) {
                        skipPreJoin(result.error());
                     } else {
                        for (Packet<?> packet : result.packets()) {
                           if (!sendCommonPacket(packet)) {
                              skipPreJoin("Connection closed");
                              return;
                           }
                        }

                        CustomMenuTracker.consumeAt(seenGeneration, result.replacement(), snapshot.phase());
                        CustomMenuScreens.advanceAfterSubmit(mc, result.clientAction(), answered);
                     }
                  }
               } else {
                  skipPreJoin(lastError.isBlank() ? "Custom screen never appeared (timed out)" : lastError + " (timed out)");
               }
            }
         }
      }
   }

   private static void waitPreJoin(MacroAction action) {
      long timeoutMs;
      if (action instanceof WaitForGuiAction w) {
         timeoutMs = w.timeoutMs > 0 ? w.timeoutMs : 30000L;
      } else {
         if (!(action instanceof WaitGuiTypeAction w)) {
            if (action instanceof DelayAction d) {
               long ms = d.useTicks ? d.delayTicks * 50L : d.delayMs;
               sleepBounded(Math.max(0L, Math.min(120000L, ms)));
               return;
            }

            return;
         }

         timeoutMs = w.timeoutMs > 0 ? w.timeoutMs : 30000L;
      }

      long deadline = System.currentTimeMillis() + Math.max(100L, Math.min(120000L, timeoutMs));

      while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
         if (CustomMenuTracker.current() != null) {
            return;
         }

         LockSupport.parkNanos(20000000L);
      }
   }

   private static void sleepBounded(long ms) {
      long deadline = System.currentTimeMillis() + ms;

      while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
         LockSupport.parkNanos(20000000L);
      }
   }

   private static void skipPreJoin(String reason) {
      RiptideNotifications.warning(reason != null && !reason.isBlank() ? reason : "Join custom menu skipped");
   }

   private static String resolveFormTemplate(String template) {
      return resolveStoredFormTemplate(template);
   }

   public static String resolveStoredFormTemplate(String template) {
      String out = template == null ? "" : template;

      for (Entry<String, String> entry : openFormValues().entrySet()) {
         out = out.replace("{secret." + entry.getKey() + "}", entry.getValue());
      }

      if (out.matches("(?s).*\\{secret\\.[^}]+}.*")) {
         return null;
      } else {
         Minecraft mc = Minecraft.getInstance();
         String username = mc != null && mc.getUser() != null ? mc.getUser().getName() : "";
         String accountId = mc != null && mc.getUser() != null ? mc.getUser().getProfileId().toString() : "";
         return out.replace("{username}", username).replace("{account_id}", accountId).replace("{profile_name}", "Rendered client");
      }
   }

   public static Map<String, String> openFormValues() {
      Map<String, String> result = new LinkedHashMap<>();
      Map<String, String> stored = RiptideConfig.getGlobal().joinMacroFormValues;
      if (stored == null) {
         return result;
      } else {
         stored.forEach((name, encoded) -> {
            try {
               byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
               if (plain != null) {
                  result.put(name, new String(plain, StandardCharsets.UTF_8));
               }
            } catch (IllegalArgumentException var4) {
            }
         });
         return result;
      }
   }

   public static boolean setFormValues(Map<String, String> values) {
      Map<String, String> sealed = new LinkedHashMap<>();
      if (values != null) {
         for (Entry<String, String> entry : values.entrySet()) {
            String name = MultiProfile.normalizeSecretName(entry.getKey());
            if (!name.isEmpty()) {
               byte[] blob = AtRestSeal.seal((entry.getValue() == null ? "" : entry.getValue()).getBytes(StandardCharsets.UTF_8));
               sealed.put(name, Base64.getEncoder().encodeToString(blob));
            }
         }
      }

      RiptideConfig config = RiptideConfig.getGlobal();
      config.joinMacroFormValues = sealed;
      config.save();
      return true;
   }

   private static void executeOnce(RiptideJoinMacroController.Timing timing) {
      if (enabled() && !executedThisConnection && triggerJoin().matches(joinOrdinal)) {
         executedThisConnection = true;
         String macroName = selectedMacroName();
         RiptideMacro macro = armedRemainder != null ? armedRemainder : RiptideMacroManager.get().get(macroName);
         if (macro == null) {
            RiptideNotifications.warning("Join macro missing: " + macroName);
            setSelectedMacro("");
         } else {
            boolean keepEnabled = keepEnabled();
            if (!keepEnabled) {
               RiptideConfig config = RiptideConfig.getGlobal();
               config.joinMacroName = "";
               config.joinMacroEnabled = false;
               config.save();
            } else if (triggerJoin() != RiptideJoinMacroController.TriggerJoin.ANY) {
               joinOrdinal = 0;
            }

            Minecraft mc = Minecraft.getInstance();
            Runnable run = () -> {
               try {
                  if (!macro.actions.isEmpty()) {
                     macro.execute();
                  }

                  RiptideNotifications.show("Join macro: " + macro.name, -13248397);
               } catch (Throwable var2x) {
                  RiptideNotifications.error("Join macro failed.");
               }
            };
            if (mc != null) {
               mc.execute(run);
            } else {
               run.run();
            }
         }
      }
   }

   private static boolean isWorldReady(Minecraft mc) {
      return mc.getConnection() != null && mc.player != null && mc.level != null;
   }

   private static boolean isPlayable(Minecraft mc) {
      return isWorldReady(mc) && mc.gui.screen() == null;
   }

   public static String modeSummary() {
      boolean keepEnabled = keepEnabled();
      return timing().label() + " / " + triggerJoin().displayLabel(keepEnabled) + " / " + (keepEnabled ? "Stays Enabled" : "Clears After Run");
   }

   private static void resetSequence() {
      joinOrdinal = 0;
      executedThisConnection = false;
      resetForJoin();
   }

   private static RiptideJoinMacroController.TriggerJoin normalizeTriggerJoin(RiptideJoinMacroController.TriggerJoin triggerJoin, boolean keepEnabled) {
      RiptideJoinMacroController.TriggerJoin value = triggerJoin == null ? RiptideJoinMacroController.TriggerJoin.FIRST : triggerJoin;
      if (keepEnabled && value == RiptideJoinMacroController.TriggerJoin.FIRST) {
         return RiptideJoinMacroController.TriggerJoin.ANY;
      } else {
         return !keepEnabled && value == RiptideJoinMacroController.TriggerJoin.ANY ? RiptideJoinMacroController.TriggerJoin.FIRST : value;
      }
   }

   public static enum Timing {
      JOINING("JOINING", "Joining"),
      WORLD("WORLD", "In World"),
      FIRST_TICK("FIRST_TICK", "First Tick"),
      INVENTORY_READY("INVENTORY_READY", "Inventory Ready"),
      PLAYABLE("PLAYABLE", "Playable"),
      FULLY_READY("FULLY_READY", "Fully Ready");

      private final String id;
      private final String label;

      private Timing(String id, String label) {
         this.id = id;
         this.label = label;
      }

      public String id() {
         return this.id;
      }

      public String label() {
         return this.label;
      }

      public RiptideJoinMacroController.Timing other() {
         RiptideJoinMacroController.Timing[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }

      public static RiptideJoinMacroController.Timing fromConfig(String value) {
         if ("JOINING".equalsIgnoreCase(value)) {
            return JOINING;
         } else if ("FIRST_TICK".equalsIgnoreCase(value) || "FIRSTTICK".equalsIgnoreCase(value)) {
            return FIRST_TICK;
         } else if ("INVENTORY_READY".equalsIgnoreCase(value) || "INVENTORY".equalsIgnoreCase(value)) {
            return INVENTORY_READY;
         } else if ("PLAYABLE".equalsIgnoreCase(value)) {
            return PLAYABLE;
         } else {
            return !"FULLY_READY".equalsIgnoreCase(value) && !"FULLYREADY".equalsIgnoreCase(value) ? WORLD : FULLY_READY;
         }
      }
   }

   public static enum TriggerJoin {
      ANY("ANY", "Every Join", 0),
      FIRST("FIRST", "1st Join", 1),
      SECOND("SECOND", "2nd Join", 2),
      THIRD("THIRD", "3rd Join", 3),
      FOURTH("FOURTH", "4th Join", 4),
      FIFTH("FIFTH", "5th Join", 5),
      SIXTH_PLUS("SIXTH_PLUS", "6th+ Join", 6);

      private final String id;
      private final String label;
      private final int number;

      private TriggerJoin(String id, String label, int number) {
         this.id = id;
         this.label = label;
         this.number = number;
      }

      public String id() {
         return this.id;
      }

      public String label() {
         return this.label;
      }

      public String displayLabel(boolean keepEnabled) {
         if (keepEnabled) {
            return switch (this) {
               case ANY, FIRST -> "Every Join";
               case SECOND -> "Every 2nd Join";
               case THIRD -> "Every 3rd Join";
               case FOURTH -> "Every 4th Join";
               case FIFTH -> "Every 5th Join";
               case SIXTH_PLUS -> "Every 6th Join";
            };
         } else {
            return switch (this) {
               case ANY, FIRST -> "Next Join";
               case SECOND -> "After 1 Transfer";
               case THIRD -> "After 2 Transfers";
               case FOURTH -> "After 3 Transfers";
               case FIFTH -> "After 4 Transfers";
               case SIXTH_PLUS -> "After 5+ Transfers";
            };
         }
      }

      public boolean matches(int joinOrdinal) {
         if (this == ANY) {
            return true;
         } else {
            return this == SIXTH_PLUS ? joinOrdinal >= this.number : joinOrdinal == this.number;
         }
      }

      public static RiptideJoinMacroController.TriggerJoin fromConfig(String value) {
         if (value != null) {
            for (RiptideJoinMacroController.TriggerJoin target : values()) {
               if (target.id.equalsIgnoreCase(value)) {
                  return target;
               }
            }

            try {
               int ordinal = Integer.parseInt(value.trim());

               return switch (ordinal) {
                  case 0 -> ANY;
                  default -> ordinal >= 6 ? SIXTH_PLUS : FIRST;
                  case 2 -> SECOND;
                  case 3 -> THIRD;
                  case 4 -> FOURTH;
                  case 5 -> FIFTH;
               };
            } catch (NumberFormatException var5) {
            }
         }

         return FIRST;
      }
   }
}
