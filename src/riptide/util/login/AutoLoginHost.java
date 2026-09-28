package riptide.util.login;

import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;

public interface AutoLoginHost {
   String password();

   boolean spawnedInWorld();

   boolean canSendChat();

   CustomMenuSnapshot customMenu();

   boolean submitCustomMenu(CustomMenuSnapshot var1, CustomMenuSubmission var2);

   boolean sendCommandLine(String var1);

   default boolean screenOwnedElsewhere() {
      return false;
   }

   default void note(String message) {
   }

   default void needsPassword(String context) {
   }
}
