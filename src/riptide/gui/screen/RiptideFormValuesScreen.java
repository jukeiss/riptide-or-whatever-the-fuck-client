package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;

public final class RiptideFormValuesScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MAX_PASSWORD_CHARS = 16;
   private static final int MIN_GENERATED_CHARS = 9;
   private static final int ROW_H = 28;
   private final Screen parent;
   private final Consumer<MultiProfile> onSave;
   private final MultiProfile draft;
   private final boolean showLoginMacro;
   private EditBox passwordField;
   private boolean hidden;
   private String selectedAccount;
   private int accountScroll;
   private final List<int[]> accountRowRects = new ArrayList<>();
   private String status = "";
   private int statusColor = -6645094;
   private int helpY = 124;

   public RiptideFormValuesScreen(Screen parent, MultiProfile profile, Set<String> initiallySelected, Consumer<MultiProfile> onSave) {
      this(parent, profile, initiallySelected, onSave, true);
   }

   public RiptideFormValuesScreen(Screen parent, MultiProfile profile, Set<String> initiallySelected, Consumer<MultiProfile> onSave, boolean showLoginMacro) {
      super(Component.literal("Login Passwords"));
      this.parent = parent;
      this.draft = new MultiProfile(profile);
      this.onSave = onSave == null ? ignored -> {} : onSave;
      this.showLoginMacro = showLoginMacro;
      List<String> ids = this.accountIds();
      if (initiallySelected != null) {
         for (String id : initiallySelected) {
            if (ids.contains(id)) {
               this.selectedAccount = id;
               break;
            }
         }
      }

      if (this.selectedAccount == null && !ids.isEmpty()) {
         this.selectedAccount = ids.get(0);
      }
   }

   protected void init() {
      this.clearWidgets();
      int left = 18;
      int leftW = Math.max(140, Math.min(240, this.screenWidth() / 3));
      int right = left + leftW + 14;
      int rightW = Math.max(150, this.screenWidth() - right - 18);
      int gap = 6;
      int fieldY = 46;
      int revealW = 54;
      this.passwordField = new EditBox(this.font, right, fieldY, rightW - revealW - gap, 20, Component.literal("Password"));
      this.passwordField.setMaxLength(16);
      this.passwordField.setHint(Component.literal("Type a password"));
      this.applyMask();
      if (this.selectedAccount != null) {
         this.passwordField.setValue(this.storedPassword(this.selectedAccount));
      }

      this.addRenderableWidget(this.passwordField);
      this.addButton(right + rightW - revealW, fieldY + 1, revealW, this.hidden ? "Show" : "Hide", Button.Tone.SECONDARY, b -> {
         this.hidden = !this.hidden;
         this.applyMask();
      });
      int half = Math.max(1, (rightW - gap) / 2);
      int y = fieldY + 26;
      this.addButton(right, y, half, "Set for Selected", Button.Tone.PRIMARY, b -> this.setForSelected());
      this.addButton(right + half + gap, y, rightW - half - gap, "Set for All", Button.Tone.SUCCESS, b -> this.setForAll());
      y += 24;
      this.addButton(right, y, rightW, "Generate Random for Each Account", Button.Tone.SECONDARY, b -> this.generateAll());
      y += 24;
      if (this.showLoginMacro) {
         boolean off = this.draft.loginMode == MultiProfile.LoginMode.Off;
         int modeH = off ? 32 : 18;
         this.addButton(
            right,
            y,
            rightW,
            modeH,
            "Login: " + loginModeLabel(this.draft.loginMode),
            off ? Button.Tone.DANGER : Button.Tone.NORMAL,
            b -> this.cycleLoginMode()
         );
         y += modeH + 6;
         if (this.draft.loginMode == MultiProfile.LoginMode.Custom) {
            String macro = this.draft.allMacroName.isBlank() ? "none" : this.draft.allMacroName;
            this.addButton(
               right,
               y,
               rightW,
               "Login Macro: " + macro,
               this.draft.allMacroName.isBlank() ? Button.Tone.SECONDARY : Button.Tone.PRIMARY,
               b -> this.pickLoginMacro()
            );
            y += 24;
         }
      }

      if (!RiptideLiteVariant.enabled()) {
         boolean autoCaptcha = RiptideConfig.getGlobal().multiAutoSolveCaptcha;
         this.addButton(
            right, y, rightW, "Auto-solve Captchas: " + (autoCaptcha ? "On" : "Off"), autoCaptcha ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> {
               RiptideConfig config = RiptideConfig.getGlobal();
               config.multiAutoSolveCaptcha = !config.multiAutoSolveCaptcha;
               config.save();
               this.init();
            }
         );
         y += 24;
      }

      this.helpY = y + 4;
      int footerW = Math.max(1, (rightW - gap) / 2);
      this.addButton(right, this.screenHeight() - 28, footerW, "Save", Button.Tone.SUCCESS, b -> this.saveAndClose());
      this.addButton(right + footerW + gap, this.screenHeight() - 28, rightW - footerW - gap, "Back", Button.Tone.SECONDARY, b -> this.onClose());
   }

   private void applyMask() {
      if (this.passwordField != null) {
         this.passwordField.addFormatter((value, offset) -> FormattedCharSequence.forward(this.hidden ? "*".repeat(value.length()) : value, Style.EMPTY));
      }
   }

   private void setForSelected() {
      if (this.selectedAccount == null) {
         this.status("Select an account on the left first.", -42149);
      } else {
         String password = this.passwordField.getValue();
         if (this.validate(password)) {
            this.draft.setFormValue(this.selectedAccount, "password", password);
            this.status("Password set for " + this.accountLabel(this.selectedAccount) + ". Press Save to keep it.", -13248397);
         }
      }
   }

   private void setForAll() {
      String password = this.passwordField.getValue();
      if (this.validate(password)) {
         List<String> ids = this.accountIds();

         for (String id : ids) {
            this.draft.setFormValue(id, "password", password);
         }

         this.status("Password set for all " + ids.size() + " account(s). Press Save to keep it.", -13248397);
      }
   }

   private boolean validate(String password) {
      if (password != null && !password.isBlank()) {
         if (password.length() > 16) {
            this.status("Password must be at most 16 characters.", -42149);
            return false;
         } else {
            return true;
         }
      } else {
         this.status("Type a password first.", -42149);
         return false;
      }
   }

   private void generateAll() {
      List<String> ids = this.accountIds();
      if (ids.isEmpty()) {
         this.status("No accounts.", -42149);
      } else {
         for (String id : ids) {
            this.draft.setFormValue(id, "password", MultiManager.generatePassword());
         }

         this.hidden = false;
         if (this.selectedAccount != null && this.passwordField != null) {
            this.passwordField.setValue(this.storedPassword(this.selectedAccount));
         }

         this.applyMask();
         this.status("Generated a random password (9-16 chars) for each account - shown in the list. Press Save to keep them.", -13248397);
      }
   }

   private void pickLoginMacro() {
      if (this.minecraft != null) {
         if (!RiptideLiteVariant.enabled()) {
            this.minecraft
               .gui
               .setScreen(
                  new RiptideMultiMacroPickerScreen(
                     this,
                     this.draft.allMacroName,
                     name -> {
                        this.draft.allMacroName = name == null ? "" : name.trim();
                        this.status(
                           this.draft.allMacroName.isBlank()
                              ? "Login macro cleared. Press Save to keep it."
                              : "Login macro set to \"" + this.draft.allMacroName + "\". Press Save to keep it.",
                           -13248397
                        );
                     }
                  )
               );
         }
      }
   }

   private void cycleLoginMode() {
      MultiProfile.LoginMode[] modes = MultiProfile.LoginMode.values();
      this.draft.loginMode = modes[(this.draft.loginMode.ordinal() + 1) % modes.length];
      this.status("Login mode: " + loginModeLabel(this.draft.loginMode) + ". Press Save to keep it.", -13248397);
      this.rebuildWidgets();
   }

   private static String loginModeLabel(MultiProfile.LoginMode mode) {
      return switch (mode) {
         case Off -> "Off";
         case Auto -> "Auto (detect)";
         case Custom -> "Custom macro";
      };
   }

   private String loginHelp() {
      if (!this.showLoginMacro) {
         return "Type a password (you can see it), then Set for Selected or Set for All - or Generate a random one per account. Used to fill custom login screens.";
      } else {
         return switch (this.draft.loginMode) {
            case Off -> "";
            case Auto -> "Auto: bots answer the usual login and register prompts on their own - AuthMe chat commands and custom login screens - using the password set above. If no login shows up within 40 seconds, it stops.";
            case Custom -> "Custom: make a macro that logs in and put {password} wherever the password goes - for example a command \"/login {password}\", or a login-screen field set to {password}. {password} is the password you set above. Pick that macro below; it runs when the bot joins and stops after 40 seconds if no login was needed.";
         };
      }
   }

   private void saveAndClose() {
      this.onSave.accept(new MultiProfile(this.draft));
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private String storedPassword(String id) {
      String stored = this.draft.openFormValues(id).getOrDefault("password", "");
      if (!stored.isBlank()) {
         return stored;
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(id);
         return account != null && account.password != null ? account.password : "";
      }
   }

   private List<String> accountIds() {
      List<String> ids = new ArrayList<>();

      for (MultiProfile.SessionSpec spec : this.draft.sessions) {
         ids.add(spec.accountId());
      }

      return ids;
   }

   private String accountLabel(String id) {
      if ("default".equals(id)) {
         return "Current account";
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(id);
         String label = account == null ? id : account.displayName();
         return MultiManager.singleLine(label != null && !label.isBlank() ? label : id, 32);
      }
   }

   private void status(String text, int color) {
      this.status = text == null ? "" : text;
      this.statusColor = color;
   }

   private void addButton(int x, int y, int w, int h, String label, Button.Tone tone, OnPress press) {
      this.addRenderableWidget(new RiptideStyledButton(x, y, Math.max(1, w), h, Component.literal(label), tone, press));
   }

   private void addButton(int x, int y, int w, String label, Button.Tone tone, OnPress press) {
      this.addRenderableWidget(new RiptideStyledButton(x, y, Math.max(1, w), 18, Component.literal(label), tone, press));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0) {
         for (int[] rect : this.accountRowRects) {
            if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2] && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]) {
               List<String> ids = this.accountIds();
               if (rect[4] >= 0 && rect[4] < ids.size()) {
                  this.selectedAccount = ids.get(rect[4]);
                  if (this.passwordField != null) {
                     this.passwordField.setValue(this.storedPassword(this.selectedAccount));
                  }
               }

               return true;
            }
         }
      }

      return super.mouseClicked(virtualEvent, doubled);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      double vx = RiptideUiScale.toVirtual(mouseX);
      double vy = RiptideUiScale.toVirtual(mouseY);
      int left = 18;
      int leftW = Math.max(140, Math.min(240, this.screenWidth() / 3));
      if (vx >= left && vx < left + leftW && vy >= 46.0 && vy < this.screenHeight() - 28) {
         this.accountScroll = Math.max(0, this.accountScroll + (vertical < 0.0 ? 1 : -1));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP));
         int left = 18;
         int leftW = Math.max(140, Math.min(240, this.screenWidth() / 3));
         int right = left + leftW + 14;
         int rightW = Math.max(150, this.screenWidth() - right - 18);
         int border = RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
         int textColor = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         int muted = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         UiRenderer.frame(
            graphics,
            UiBounds.of(left, 18, leftW, Math.max(1, this.screenHeight() - 46)),
            RiptideTheme.recolor(-1206643689, RiptideTheme.Channel.BUTTON),
            border
         );
         UiRenderer.frame(
            graphics,
            UiBounds.of(right, 18, rightW, Math.max(1, this.screenHeight() - 46)),
            RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON),
            border
         );
         this.drawText(graphics, "Accounts (click to edit)", left + 8, 30, textColor);
         this.drawText(graphics, "Login Password", right + 8, 30, textColor);
         this.renderAccountRows(graphics, left + 4, leftW - 8, textColor, muted, border);
         int hy = this.helpY;

         for (FormattedCharSequence line : this.font.split(FormattedText.of(this.loginHelp()), rightW - 8)) {
            graphics.text(this.font, line, right, hy, muted, false);
            hy += 10;
         }

         if (!this.status.isBlank()) {
            int sy = this.screenHeight() - 52;

            for (FormattedCharSequence line : this.font.split(FormattedText.of(this.status), rightW - 8)) {
               graphics.text(this.font, line, right, sy, this.statusColor, false);
               sy += 10;
            }
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderAccountRows(GuiGraphicsExtractor graphics, int x, int w, int textColor, int muted, int border) {
      this.accountRowRects.clear();
      List<String> ids = this.accountIds();
      int top = 46;
      int bottom = this.screenHeight() - 28;
      int visible = Math.max(1, (bottom - top) / 28);
      this.accountScroll = Math.max(0, Math.min(this.accountScroll, Math.max(0, ids.size() - visible)));
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      int y = top;

      for (int i = this.accountScroll; i < Math.min(ids.size(), this.accountScroll + visible); i++) {
         String id = ids.get(i);
         boolean selected = id.equals(this.selectedAccount);
         int fill = selected ? 859166835 : 402653184;
         UiRenderer.rect(graphics, UiBounds.of(x, y, w, 25), fill);
         if (selected) {
            UiRenderer.rect(graphics, UiBounds.of(x, y, 2, 25), RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS));
         }

         String name = UiText.trimToWidthEllipsis(this.font, this.accountLabel(id), w - 10, fontId, textColor);
         UiText.draw(
            graphics, this.font, name, fontId, selected ? RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS) : textColor, x + 5, y + 4, false
         );
         String pass = this.storedPassword(id);
         String shown = pass.isEmpty() ? "(no password)" : (this.hidden ? "*".repeat(pass.length()) : pass);
         String passLine = UiText.trimToWidthEllipsis(this.font, shown, w - 10, fontId, muted);
         UiText.draw(graphics, this.font, passLine, fontId, pass.isEmpty() ? muted : textColor, x + 5, y + 14, false);
         this.accountRowRects.add(new int[]{x, y, w, 25, i});
         y += 28;
      }
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      UiText.draw(graphics, this.font, MultiManager.singleLine(text, 100), fontId, color, x, y, false);
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }
}
