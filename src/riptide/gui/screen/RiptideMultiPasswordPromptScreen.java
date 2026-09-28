package riptide.gui.screen;

import java.util.Iterator;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideConfig;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;

public final class RiptideMultiPasswordPromptScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   public static final int MAX_PASSWORD_CHARS = 16;
   public static final int MIN_GENERATED_CHARS = 9;
   private final Screen parent;
   private EditBox passwordField;
   private boolean reveal = true;
   private String status = "";
   private int statusColor = -6645094;

   public RiptideMultiPasswordPromptScreen(Screen parent) {
      super(Component.literal("Multi Login Password"));
      this.parent = parent;
   }

   protected void init() {
      this.clearWidgets();
      int w = this.panelW();
      int x = (this.screenWidth() - w) / 2;
      int inner = w - 24;
      int fieldY = this.panelY() + 100;
      this.passwordField = new EditBox(this.font, x + 12, fieldY, inner - 62, 18, Component.literal("Password"));
      this.passwordField.setMaxLength(16);
      this.passwordField.setHint(Component.literal("Password for all accounts"));
      this.applyMaskFormatter();
      this.addRenderableWidget(this.passwordField);
      this.addButton(x + 12 + inner - 58, fieldY, 58, this.reveal ? "Hide" : "Reveal", Button.Tone.SECONDARY, b -> {
         this.reveal = !this.reveal;
         this.applyMaskFormatter();
      });
      int y = fieldY + 24;
      this.addButton(x + 12, y, inner, "Use This Password for All Accounts", Button.Tone.SUCCESS, b -> this.applyManual());
      y += 22;
      this.addButton(x + 12, y, inner, "Generate Random for Each Account", Button.Tone.PRIMARY, b -> this.applyGenerated());
      y += 22;
      String macroName = MultiManager.get().allMacroName();
      String macroLabel = "Login Macro: " + (macroName != null && !macroName.isBlank() ? macroName : "none");
      this.addButton(
         x + 12, y, inner, macroLabel, macroName != null && !macroName.isBlank() ? Button.Tone.PRIMARY : Button.Tone.SECONDARY, b -> this.pickLoginMacro()
      );
      y += 22;
      boolean autoCaptcha = RiptideConfig.getGlobal().multiAutoSolveCaptcha;
      this.addButton(
         x + 12, y, inner, "Auto-solve Captchas: " + (autoCaptcha ? "On" : "Off"), autoCaptcha ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> {
            RiptideConfig config = RiptideConfig.getGlobal();
            config.multiAutoSolveCaptcha = !config.multiAutoSolveCaptcha;
            config.save();
            this.init();
         }
      );
      y += 22;
      this.addButton(x + 12, y, inner, "Not Now", Button.Tone.SECONDARY, b -> this.onClose());
      this.setFocused(this.passwordField);
      this.passwordField.setFocused(true);
   }

   private void pickLoginMacro() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideMultiMacroPickerScreen(this, MultiManager.get().allMacroName(), name -> {
            MultiManager.get().assignAllMacro(name == null ? "" : name);
            this.init();
         }));
      }
   }

   private void applyMaskFormatter() {
      if (this.passwordField != null) {
         this.passwordField.addFormatter((value, offset) -> FormattedCharSequence.forward(this.reveal ? value : "*".repeat(value.length()), Style.EMPTY));
      }
   }

   private void applyManual() {
      String password = this.passwordField == null ? "" : this.passwordField.getValue();
      if (password.isBlank()) {
         this.status("Type a password first (or generate random ones).", -42149);
      } else if (password.length() > 16) {
         this.status("Password must be at most 16 characters.", -42149);
      } else {
         int updated = MultiManager.get().applyPasswordToAllAccounts(password);
         if (updated <= 0) {
            this.status("No accounts to store the password for.", -42149);
         } else {
            this.finish(
               "Password set for " + updated + " account(s).",
               password.length() < 9 ? " Note: it is shorter than 9 characters; some servers may reject it." : ""
            );
         }
      }
   }

   private void applyGenerated() {
      int updated = MultiManager.get().applyGeneratedPasswords();
      if (updated <= 0) {
         this.status("No accounts to generate passwords for.", -42149);
      } else {
         this.startLoginMacroIfAssigned();
         RiptideNotifications.show("Generated a random password for " + updated + " account(s).", RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS));
         MultiProfile snapshot = MultiManager.get().activeProfile();
         if (snapshot != null && this.minecraft != null) {
            this.minecraft
               .gui
               .setScreen(new RiptideFormValuesScreen(this.parent, snapshot, null, saved -> MultiManager.get().updateActiveFormValues(saved), true));
         } else {
            this.onClose();
         }
      }
   }

   private void finish(String message, String note) {
      this.startLoginMacroIfAssigned();
      RiptideNotifications.show(message + note, RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS));
      this.onClose();
   }

   private void startLoginMacroIfAssigned() {
      MultiManager manager = MultiManager.get();
      if (manager.isActive() && manager.hasAnyAssignedMacro()) {
         manager.runMacroOnScope(Set.of(), true);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP));
         int w = this.panelW();
         int x = (this.screenWidth() - w) / 2;
         int y = this.panelY();
         UiRenderer.frame(
            graphics,
            UiBounds.of(x, y, w, this.panelH()),
            RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON),
            RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE)
         );
         int textColor = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         int mutedColor = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         Identifier fontId = THEME.fontFor(UiTone.BODY);
         UiText.draw(graphics, this.font, "Multi: login password needed", fontId, textColor, x + 12, y + 10, false);
         String message = "A server login screen appeared but no password is stored for this profile. Set one password for every account, or generate a different random one per account (9-16 characters). Waiting login macros continue automatically.";
         int ty = y + 24;

         for (FormattedCharSequence line : this.font.split(FormattedText.of(message), w - 24)) {
            graphics.text(this.font, line, x + 12, ty, mutedColor, false);
            ty += 10;
         }

         if (!this.status.isBlank()) {
            Iterator var21 = this.font.split(FormattedText.of(this.status), w - 24).iterator();
            if (var21.hasNext()) {
               FormattedCharSequence line = (FormattedCharSequence)var21.next();
               graphics.text(this.font, line, x + 12, y + this.panelH() - 26, this.themedStatus(), false);
            }
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private int themedStatus() {
      if (this.statusColor == -42149) {
         return RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
      } else {
         return this.statusColor == -13248397
            ? RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS)
            : RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
      }
   }

   private int panelW() {
      return Math.min(320, Math.max(220, this.screenWidth() - 40));
   }

   private int panelH() {
      return 262;
   }

   private int panelY() {
      return Math.max(10, (this.screenHeight() - this.panelH()) / 2);
   }

   private void status(String text, int color) {
      this.status = text == null ? "" : text;
      this.statusColor = color;
   }

   private void addButton(int x, int y, int w, String label, Button.Tone tone, OnPress press) {
      this.addRenderableWidget(new RiptideStyledButton(x, y, Math.max(1, w), 18, Component.literal(label), tone, press));
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }
}
