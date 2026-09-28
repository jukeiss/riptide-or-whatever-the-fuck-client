package riptide.gui.multi;

import riptide.util.multi.MultiMacroDelay;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiSession;

public final class MultiMacroPresentation {
   public static final int ASSIGNED_GRAY = -6641998;
   public static final int PLAYING_GREEN = -11013497;
   public static final int QUEUED_AMBER = -1526710;

   private MultiMacroPresentation() {
   }

   public static String assignedName(MultiManager manager, MultiSession.Snapshot snapshot) {
      if (manager != null && snapshot != null) {
         String value = manager.assignedMacroName(snapshot.accountId());
         return value == null ? "" : value;
      } else {
         return "";
      }
   }

   public static boolean playing(MultiSession.Snapshot snapshot) {
      if (snapshot != null && snapshot.connected()) {
         MultiSession.MacroProgress progress = snapshot.macroProgress();
         return progress != null && progress.running() && progress.macroName() != null && !progress.macroName().isBlank();
      } else {
         return false;
      }
   }

   public static String playingName(MultiSession.Snapshot snapshot) {
      return !playing(snapshot) ? "" : snapshot.macroProgress().macroName();
   }

   public static String assignedLabel(String assignedName) {
      return "Assigned: " + (assignedName != null && !assignedName.isBlank() ? assignedName : "none");
   }

   public static String playingLabel(String playingName) {
      return playingName != null && !playingName.isBlank() ? "Playing: " + playingName : "";
   }

   public static boolean queued(MultiSession.Snapshot snapshot, long now) {
      return snapshot != null && snapshot.macroQueue() != null && snapshot.macroQueue().pending(now);
   }

   public static String queuedLabel(MultiSession.Snapshot snapshot, long now) {
      return !queued(snapshot, now) ? "" : "Starts in " + MultiMacroDelay.countdownText(snapshot.macroQueue().remainingMs(now));
   }

   public static String tooltip(MultiManager manager, MultiSession.Snapshot snapshot) {
      String assigned = assignedName(manager, snapshot);
      StringBuilder text = new StringBuilder("Assigned: ").append(assigned.isBlank() ? "none" : assigned);
      String playing = playingName(snapshot);
      if (!playing.isBlank()) {
         text.append(" | Playing: ").append(playing);
      }

      String queued = queuedLabel(snapshot, System.currentTimeMillis());
      if (!queued.isBlank()) {
         text.append(" | ").append(queued);
      }

      return text.toString();
   }
}
