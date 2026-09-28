package riptide.util.custommenu;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.mixin.accessor.RiptideDialogScreenAccessor;

public final class CustomMenuScreens {
   private static final long ADVANCE_TIMEOUT_MS = 500L;

   private CustomMenuScreens() {
   }

   public static CustomMenuSnapshot openScreenSnapshot(Minecraft mc) {
      Dialog dialog = openDialog(mc);
      return dialog == null ? null : VanillaDialogAdapter.snapshotOf(dialog, mc.getConnection() != null ? "PLAY" : "CONFIGURATION");
   }

   public static Screen openScreen(Minecraft mc) {
      return mc != null && mc.gui != null ? mc.gui.screen() : null;
   }

   public static void advanceAfterSubmit(Minecraft mc, ClickEvent clientAction, Screen answered) {
      if (mc != null && answered instanceof DialogScreen) {
         CompletableFuture<Void> applied;
         try {
            applied = mc.submit(() -> {
               if (mc.gui.screen() == answered && mc.gui.screen() instanceof DialogScreen<?> screen) {
                  Dialog dialog = ((RiptideDialogScreenAccessor)screen).riptide$dialog();
                  DialogAction after = dialog == null ? DialogAction.CLOSE : dialog.common().afterAction();
                  if (clientAction != null || after != DialogAction.NONE) {
                     screen.runAction(Optional.ofNullable(clientAction));
                  }
               }
            });
         } catch (RuntimeException var7) {
            return;
         }

         try {
            applied.get(500L, TimeUnit.MILLISECONDS);
         } catch (InterruptedException var5) {
            Thread.currentThread().interrupt();
         } catch (Exception var6) {
         }
      }
   }

   private static Dialog openDialog(Minecraft mc) {
      if (mc != null && mc.gui != null) {
         return mc.gui.screen() instanceof DialogScreen<?> screen ? ((RiptideDialogScreenAccessor)screen).riptide$dialog() : null;
      } else {
         return null;
      }
   }
}
