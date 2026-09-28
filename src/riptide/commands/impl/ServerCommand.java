package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.macro.ServerTickTracker;

public class ServerCommand extends Command {
   public ServerCommand() {
      super("server", "Open the server info panel, or the plugin scanner.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         openInfo();
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("info").executes(ctx -> {
         openInfo();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("plugins").executes(ctx -> {
         openPlugins();
         return 1;
      }));
      root.then(LiteralArgumentBuilder.literal("tps").executes(ctx -> {
         tps();
         return 1;
      }));
   }

   static void openInfo() {
      openOverlay(false);
   }

   static void openPlugins() {
      openOverlay(true);
   }

   private static void openOverlay(boolean pluginsTab) {
      RiptideServerInfoOverlay overlay = RiptideModule.get().getServerDataOverlay();
      if (overlay == null) {
         RiptideClientMessaging.sendPrefixed("§cServer overlay unavailable.");
      } else {
         RiptideOverlayManager.get().register(overlay);
         if (pluginsTab) {
            overlay.openPluginsTab();
         } else {
            overlay.openInfoTab();
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            mc.execute(() -> {
               if (mc.gui.screen() == null) {
                  mc.gui.setScreen(new RiptideOverlayHostScreen(overlay));
               }
            });
         }
      }
   }

   private static void tps() {
      double tps = ServerTickTracker.getEstimatedTps();
      RiptideClientMessaging.sendPrefixed(String.format("§eTPS: §f%.2f §7(estimated)", tps));
   }
}
