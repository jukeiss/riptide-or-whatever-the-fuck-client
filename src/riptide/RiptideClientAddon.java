package riptide;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class RiptideClientAddon {
   public static final Logger LOG = LogUtils.getLogger();
   public static final String MOD_ID = "riptide";
   public static final boolean DEBUG = false;
   public static final File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("riptide").toFile();

   private RiptideClientAddon() {
   }

   static {
      FOLDER.mkdirs();
   }
}
