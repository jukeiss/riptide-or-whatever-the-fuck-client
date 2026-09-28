package riptide.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class RiptideModMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return RiptideModMenuConfigScreen::new;
   }
}
