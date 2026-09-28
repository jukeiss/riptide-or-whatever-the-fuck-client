package riptide;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import riptide.util.RiptideKeyLock;

public final class RiptidePreLaunch implements PreLaunchEntrypoint {
   public void onPreLaunch() {
      RiptideKeyLock.verify();
   }
}
