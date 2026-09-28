package riptide.modules;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.api.module.DoubleSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideJoinMacroController;
import riptide.util.custommenu.CustomMenuTracker;
import riptide.util.login.AutoLoginConfig;
import riptide.util.login.AutoLoginEngine;
import riptide.util.login.AutoLoginHost;

public final class AutoLoginModule extends Module {
   private static volatile AutoLoginModule instance;
   private final AutoLoginEngine engine = new AutoLoginEngine(new AutoLoginModule.ClientHost());

   AutoLoginModule() {
      super("auto-login", "AutoLogin", ModuleCategory.PLAYER, "Answers server login and register gates.");
      instance = this;
      this.add(new StringSetting("password", "Password", "").description("Sent to login and register gates.").build());
      this.add(new DoubleSetting("chat-delay", "Chat Delay", 2.0, 0.0, 10.0, 0.1).description("Wait after spawning before sending.").unit("s").build());
      this.add(new DoubleSetting("window", "Window", 10.0, 3.0, 60.0, 0.5).description("Stop watching this long after joining.").unit("s").build());
   }

   @Override
   public boolean ticksWhenDisabled() {
      return false;
   }

   @Override
   public boolean settingsShareable() {
      return false;
   }

   @Override
   public void onGameJoin() {
      this.engine.reset(System.currentTimeMillis());
   }

   @Override
   public void onGameLeft() {
      this.engine.reset(System.currentTimeMillis());
   }

   @Override
   public void tick() {
      this.engine.configure(new AutoLoginConfig((long)(this.decimal("window") * 1000.0), (long)(this.decimal("chat-delay") * 1000.0), 2500L, 4, 1000L, 3000L));
      this.engine.tick(System.currentTimeMillis());
   }

   public static void observeIncomingChat(Packet<?> packet) {
      AutoLoginModule module = instance;
      if (module != null && module.isEnabled()) {
         try {
            if (packet instanceof ClientboundSystemChatPacket system) {
               module.engine.onChatLine(system.content().getString());
            } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
               module.engine.onChatLine(disguised.message().getString());
            }
         } catch (Throwable var4) {
         }
      }
   }

   private final class ClientHost implements AutoLoginHost {
      private ClientHost() {
         Objects.requireNonNull(AutoLoginModule.this);
         super();
      }

      @Override
      public String password() {
         String configured = AutoLoginModule.this.text("password");
         if (configured != null && !configured.isBlank()) {
            return configured;
         } else {
            try {
               String shared = RiptideJoinMacroController.openFormValues().get("password");
               if (shared != null && !shared.isBlank()) {
                  return shared;
               }
            } catch (RuntimeException var3) {
            }

            return "";
         }
      }

      @Override
      public boolean spawnedInWorld() {
         return Module.MC != null && Module.MC.getConnection() != null && Module.MC.player != null && Module.MC.level != null;
      }

      @Override
      public boolean canSendChat() {
         return Module.MC != null && Module.MC.getConnection() != null;
      }

      @Override
      public CustomMenuSnapshot customMenu() {
         return CustomMenuTracker.current();
      }

      @Override
      public boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
         CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, submission);
         if (result != null && result.success()) {
            for (Packet<?> packet : result.packets()) {
               if (!RiptideJoinMacroController.sendCommonPacket(packet)) {
                  return false;
               }
            }

            CustomMenuTracker.consume(snapshot, result.replacement());
            if (result.clientAction() != null && Module.MC != null) {
               Module.MC.execute(() -> {
                  if (Module.MC.gui.screen() instanceof DialogScreen<?> dialog) {
                     dialog.runAction(Optional.of(result.clientAction()));
                  }
               });
            }

            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean sendCommandLine(String line) {
         if (!this.canSendChat()) {
            return false;
         } else {
            AutoLoginModule.this.sendCommand(line);
            return true;
         }
      }

      @Override
      public boolean screenOwnedElsewhere() {
         return RiptideJoinMacroController.isDrivingCustomMenu();
      }

      @Override
      public void note(String message) {
         RiptideClientMessaging.sendPrefixed("§c" + message);
      }

      @Override
      public void needsPassword(String context) {
         RiptideClientMessaging.sendPrefixed("§eAutoLogin: set a password in the module settings.");
      }
   }
}
