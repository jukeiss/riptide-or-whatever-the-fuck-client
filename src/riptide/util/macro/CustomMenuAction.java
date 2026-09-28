package riptide.util.macro;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideNotifications;
import riptide.util.custommenu.CustomMenuScreens;
import riptide.util.custommenu.CustomMenuTracker;

public final class CustomMenuAction implements MacroAction {
   private static final long FAILURE_REPEAT_MS = 15000L;
   private static String lastFailure = "";
   private static long lastFailureAtMs;
   public final ArrayList<String> fieldValues = new ArrayList<>();
   public String clickButton = "";
   public int timeoutMs = 30000;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      long deadline = System.currentTimeMillis() + this.boundedTimeout();
      boolean gateOnRun = MacroExecutor.isCurrentActionRunActive();
      String lastError = "";

      while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
         if (gateOnRun && !MacroExecutor.isCurrentActionRunActive()) {
            return;
         }

         long seenGeneration = CustomMenuTracker.generation();
         CustomMenuSnapshot snapshot = CustomMenuTracker.current();
         if (snapshot != null) {
            seenGeneration = snapshot.generation();
         } else {
            snapshot = CustomMenuScreens.openScreenSnapshot(mc);
         }

         if (snapshot == null) {
            LockSupport.parkNanos(20000000L);
         } else {
            CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(this, snapshot, value -> {
               String withSecrets = RiptideJoinMacroController.resolveStoredFormTemplate(value);
               if (withSecrets == null) {
                  throw new IllegalStateException("Missing form value");
               } else {
                  MacroTemplate.Resolution resolved = MacroVariables.resolve(withSecrets, mc);
                  if (!resolved.success()) {
                     throw new IllegalStateException("Missing macro value");
                  } else {
                     return resolved.value();
                  }
               }
            });
            if (!prepared.success()) {
               lastError = prepared.error() == null ? "" : prepared.error();
               if (prepared.error() != null && prepared.error().contains("unavailable")) {
                  LockSupport.parkNanos(20000000L);
                  continue;
               }

               this.fail(prepared.error());
               return;
            }

            Screen answered = CustomMenuScreens.openScreen(mc);
            CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, prepared.submission());
            if (!result.success()) {
               this.fail(result.error());
               return;
            }

            for (Packet<?> packet : result.packets()) {
               if (!RiptideJoinMacroController.sendCommonPacket(packet)) {
                  this.fail("Connection closed before custom-menu submission");
                  return;
               }
            }

            CustomMenuTracker.consumeAt(seenGeneration, result.replacement(), snapshot.phase());
            CustomMenuScreens.advanceAfterSubmit(mc, result.clientAction(), answered);
            return;
         }
      }

      this.fail(lastError.isBlank() ? "Custom screen never appeared (timed out)" : lastError + " (timed out)");
   }

   private void fail(String reason) {
      warnOnce(reason != null && !reason.isBlank() ? reason : "Custom screen action failed");
   }

   private static void warnOnce(String message) {
      long now = System.currentTimeMillis();
      synchronized (CustomMenuAction.class) {
         if (message.equals(lastFailure) && now - lastFailureAtMs < 15000L) {
            return;
         }

         lastFailure = message;
         lastFailureAtMs = now;
      }

      RiptideNotifications.warning(message);
   }

   public int boundedTimeout() {
      return Math.max(100, Math.min(120000, this.timeoutMs));
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.CUSTOM_MENU;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", MacroActionType.CUSTOM_MENU.name());
      tag.put("fieldValues", MacroStringList.toTag(this.fieldValues));
      tag.putString("clickButton", this.clickButton);
      tag.putInt("timeoutMs", this.boundedTimeout());
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.fieldValues.clear();
      this.clickButton = tag.getStringOr("clickButton", "");
      if (tag.getList("fieldValues").isPresent()) {
         this.fieldValues.addAll(MacroStringList.fromTag(tag.getList("fieldValues").orElse(new ListTag())));
      } else {
         String primary = tag.getStringOr("primaryValue", "");
         if (!primary.isBlank()) {
            this.fieldValues.add(primary);
         }

         this.fieldValues.addAll(MacroStringList.fromTag(tag.getList("inputValues").orElse(new ListTag())));
         if (this.clickButton.isBlank()) {
            String buttonMode = tag.getStringOr("buttonMode", "SAFE_AUTO");
            String selector = tag.getStringOr("buttonSelector", "");
            int index = Math.max(1, tag.getIntOr("buttonIndex", 1));
            String var6 = buttonMode.toUpperCase(Locale.ROOT);

            this.clickButton = switch (var6) {
               case "ACTION_ID", "LABEL" -> selector;
               case "INDEX" -> "#" + index;
               default -> "";
            };
         }
      }

      this.timeoutMs = Math.max(100, Math.min(120000, tag.getIntOr("timeoutMs", 30000)));
      this.enabled = tag.getBooleanOr("enabled", true);
   }

   @Override
   public String getDisplayName() {
      String button = this.clickButton.isBlank() ? "auto" : this.clickButton;
      return "Custom Screen (press " + button + ")";
   }

   @Override
   public String getIcon() {
      return "CS";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
