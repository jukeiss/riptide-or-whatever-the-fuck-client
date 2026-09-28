package riptide.gui.screen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountSessionSwitcher;
import riptide.util.RiptideAccountType;
import riptide.util.RiptideChatField;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiProfileManager;

public final class RiptideMultiScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MARGIN = 14;
   private static final int LEFT_WIDTH = 176;
   private static final int GAP = 8;
   private static final int ROW_HEIGHT = 24;
   private static final int[] PING_OPTIONS = new int[]{50, 100, 200, 300, 500, 1000};
   private static final List<String> PACING_OPTIONS = List.of("Gentle", "Balanced", "Fast", "Immediate", "Custom");
   private static final List<String> PROXY_MODE_OPTIONS = List.of("Off", "Auto", "Manual");
   private final Screen parent;
   private final List<CompactDropdown> dropdowns = new ArrayList<>();
   private final String prefillServer;
   private final boolean openedFromActiveConsole;
   private final EnumSet<RiptideAccountType> typeFilters = EnumSet.allOf(RiptideAccountType.class);
   private MultiProfile draft;
   private String selectedProfileId;
   private boolean dirty;
   private long lastDirtyAt;
   private long savedFlashAt;
   private EditBox nameField;
   private EditBox serverField;
   private EditBox concurrencyField;
   private EditBox delayField;
   private RiptideChatField accountSearchField;
   private int profileScroll;
   private int accountScroll;
   private String lastActiveKey = "";
   private static final int MANAGE_BTN_W = 76;

   public RiptideMultiScreen(Screen parent, String prefillServer) {
      this(parent, prefillServer, false);
   }

   public RiptideMultiScreen(Screen parent, String prefillServer, boolean openedFromActiveConsole) {
      super(Component.literal("Multi"));
      this.parent = parent;
      this.prefillServer = prefillServer == null ? "" : prefillServer.trim();
      this.openedFromActiveConsole = openedFromActiveConsole;
      List<MultiProfile> profiles = MultiProfileManager.get().all();
      MultiProfile active = MultiManager.get().activeProfile();
      MultiProfile shared = active == null ? MultiProfileManager.get().find(MultiProfileManager.get().selectedId()) : null;
      this.draft = active != null ? active : (shared != null ? shared : (profiles.isEmpty() ? this.newProfile() : new MultiProfile(profiles.getFirst())));
      this.selectedProfileId = active == null && profiles.isEmpty() ? null : this.draft.id;
      if (this.selectedProfileId != null) {
         MultiProfileManager.get().setSelectedId(this.selectedProfileId);
      }
   }

   protected void init() {
      this.lastActiveKey = this.activeKey();
      this.rebuildControls();
   }

   public void tick() {
      super.tick();
      if (this.dirty && System.currentTimeMillis() - this.lastDirtyAt >= 500L) {
         this.commit();
      }

      String key = this.activeKey();
      if (!key.equals(this.lastActiveKey) && !(this.getFocused() instanceof EditBox)) {
         this.captureFields();
         this.lastActiveKey = key;
         this.rebuildControls();
      }
   }

   private String activeKey() {
      MultiManager manager = MultiManager.get();
      return Boolean.toString(manager.isActive());
   }

   private void rebuildControls() {
      this.clearWidgets();
      this.dropdowns.clear();
      int ix = this.rightX() + 12;
      int innerW = this.rightWidth() - 24;
      int half = (innerW - 6) / 2;
      boolean custom = this.draft.pacing == MultiProfile.Pacing.Custom;
      boolean auto = this.draft.proxyMode == MultiProfile.ProxyMode.Auto;
      boolean locked = this.isActiveProfile(this.draft.id);
      this.nameField = this.serverField = this.concurrencyField = this.delayField = null;
      if (locked) {
         this.buildActiveReadOnly(ix, innerW);
      }

      if (!locked) {
         this.nameField = this.field(ix, 40, half, "Profile name", this.draft.name, 64);
         this.serverField = this.field(ix + half + 6, 40, innerW - half - 6, "host:port", this.draft.serverAddress, 255);
         if (auto) {
            int third = (innerW - 12) / 3;
            this.addPacingDropdown(ix, 66, third);
            this.addProxyModeDropdown(ix + third + 6, 66, third);
            int pingX = ix + 2 * (third + 6);
            this.dropdowns.add(new CompactDropdown(pingX, 66, innerW - 2 * (third + 6), 18, pingOptionLabels(), pingIndex(this.draft.autoMaxPingMs), index -> {
               this.captureFields();
               if (index >= 0 && index < PING_OPTIONS.length) {
                  this.draft.autoMaxPingMs = PING_OPTIONS[index];
               }

               this.commit();
               this.rebuildControls();
            }).setButtonLabelOverride("<=" + this.draft.autoMaxPingMs + "ms"));
         } else {
            this.addPacingDropdown(ix, 66, half);
            this.addProxyModeDropdown(ix + half + 6, 66, innerW - half - 6);
         }

         if (custom) {
            this.concurrencyField = this.field(ix, 92, half, "Accounts 1-500", Integer.toString(this.draft.customConcurrency), 4);
            this.delayField = this.field(ix + half + 6, 92, innerW - half - 6, "Delay 0-5000ms", Integer.toString(this.draft.customDelayMs), 6);
         } else {
            this.concurrencyField = null;
            this.delayField = null;
         }

         int macroY = 92 + (custom ? 26 : 0);
         String allLabel = this.draft.allMacroName.isBlank() ? "Macro (all): none" : "Macro (all): " + this.draft.allMacroName;
         this.addStyled(
            ix, macroY, innerW, 18, allLabel, this.draft.allMacroName.isBlank() ? Button.Tone.NORMAL : Button.Tone.PRIMARY, b -> this.pickAllMacro()
         );
         int formY = macroY + 22;
         this.addStyled(ix, formY, innerW, 18, "Passwords (login)", Button.Tone.NORMAL, b -> this.openFormValues());
         if (!this.compactVertical()) {
            this.addAccountFilterChips(ix, this.chipsY(), innerW);
         }
      }

      int footerY = this.screenHeight() - 32;
      int profileFooterY = this.compactFooter() ? this.screenHeight() - 54 : footerY;
      this.addStyled(14, profileFooterY, 60, 18, "New", Button.Tone.PRIMARY, b -> this.createNew());
      this.addStyled(78, profileFooterY, 76, 18, "Duplicate", Button.Tone.NORMAL, b -> this.duplicateDraft());
      if (MultiManager.get().isActive()) {
         int activeW = Math.min(132, Math.max(1, this.screenWidth() - 28 - 146));
         RiptideStyledButton activeButton = new RiptideStyledButton(
            160,
            profileFooterY,
            activeW,
            18,
            Component.literal("Active Multi"),
            Button.Tone.SUCCESS,
            () -> "Active " + MultiManager.get().readyFraction(),
            b -> this.openActiveConsole()
         );
         this.addRenderableWidget(activeButton);
      }

      int footerX = this.rightX() + 12;
      int footerW = Math.max(1, this.rightWidth() - 24);
      int footerGap = footerW >= 220 ? 6 : 3;
      boolean compactActiveReturn = this.openedFromActiveConsole && MultiManager.get().isActive();
      int count = compactActiveReturn ? 2 : 3;
      int footerEach = Math.max(1, (footerW - footerGap * (count - 1)) / count);
      int fx = footerX;
      if (!compactActiveReturn) {
         this.addStyled(footerX, footerY, footerEach, 18, "Advanced", Button.Tone.NORMAL, b -> this.openAdvanced());
         fx = footerX + footerEach + footerGap;
      }

      this.addStyled(fx, footerY, footerEach, 18, "Connect", Button.Tone.SUCCESS, b -> this.connect());
      fx += footerEach + footerGap;
      this.addStyled(fx, footerY, Math.max(1, footerX + footerW - fx), 18, "Back", Button.Tone.NORMAL, b -> this.onClose());
      int mix = this.rightX() + 12;
      int minnerW = this.rightWidth() - 24;
      this.addStyled(mix + minnerW - 76, this.accountsBaseY() + 12, 76, 16, "Accounts...", Button.Tone.NORMAL, b -> this.openAccounts());
      if (this.showBulkProxy()) {
         String commonProxy = this.commonManualProxyId();
         String label = commonProxy == null ? "Set All Proxies (Mixed)..." : "Set All Proxies: " + this.proxyLabel(commonProxy);
         this.addStyled(
            mix,
            this.accountsHeaderBaseY(),
            minnerW,
            18,
            label,
            Button.Tone.PRIMARY,
            button -> this.openManualProxyPicker("Proxy for all selected accounts", commonProxy, this::setAllProxies)
         );
      }

      this.addProfileButtons();
      this.addAccountButtons();
   }

   private void openAccounts() {
      this.captureFields();
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideAccountsScreen(this));
      }
   }

   private void buildActiveReadOnly(int ix, int innerW) {
      this.disabledLabel(ix, 40, innerW, "Profile: " + this.draft.name);
      this.disabledLabel(ix, 62, innerW, "Server: " + (this.draft.serverAddress.isBlank() ? "-" : this.draft.serverAddress));
      this.disabledLabel(ix, 84, innerW, this.draft.sessions.size() + " accounts");
      RiptideStyledButton note = this.addStyled(ix, 110, innerW, 18, "Batch running - Disconnect to edit", Button.Tone.DANGER, b -> {});
      note.active = false;
   }

   private void disabledLabel(int x, int y, int w, String text) {
      RiptideStyledButton label = this.addStyled(x, y, w, 18, text, Button.Tone.NORMAL, b -> {});
      label.active = false;
   }

   private EditBox field(int x, int y, int w, String hint, String value, int max) {
      EditBox box = new EditBox(this.font, x, y, Math.max(1, w), 18, Component.literal(hint));
      box.setMaxLength(max);
      box.setValue(value);
      box.setHint(Component.literal(hint));
      box.setResponder(v -> this.markDirty());
      this.addRenderableWidget(box);
      return box;
   }

   private void addAccountFilterChips(int ix, int y, int innerW) {
      String[] labels = new String[]{"Cracked", "Session", "Microsoft", "TheAltening"};
      RiptideAccountType[] types = new RiptideAccountType[]{
         RiptideAccountType.Cracked, RiptideAccountType.Session, RiptideAccountType.Microsoft, RiptideAccountType.TheAltening
      };
      int gap = 4;
      int chipW = (innerW - gap * (labels.length - 1)) / labels.length;

      for (int i = 0; i < labels.length; i++) {
         RiptideAccountType type = types[i];
         Button.Tone tone = this.typeFilters.contains(type) ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
         RiptideStyledButton filter = new RiptideStyledButton(
            ix + i * (chipW + gap), y, Math.max(1, chipW), 14, Component.literal(this.fitLabel(labels[i], chipW - 6)), tone, b -> {
               this.captureFields();
               if (!this.typeFilters.add(type)) {
                  this.typeFilters.remove(type);
               }

               this.accountScroll = 0;
               this.rebuildControls();
            }
         );
         filter.setToggled(this.typeFilters.contains(type));
         this.addRenderableWidget(filter);
      }
   }

   private void addProfileButtons() {
      List<MultiProfile> profiles = MultiProfileManager.get().all();
      int top = 40;
      int bottom = this.profileListBottom();
      int visible = Math.max(1, (bottom - top) / 24);
      this.profileScroll = Math.max(0, Math.min(this.profileScroll, Math.max(0, profiles.size() - visible)));
      int end = Math.min(profiles.size(), this.profileScroll + visible);
      int y = top;
      int nameW = Math.max(24, this.leftWidth() - 16 - 26);

      for (int i = this.profileScroll; i < end; i++) {
         MultiProfile profile = profiles.get(i);
         boolean picked = profile.id.equals(this.selectedProfileId);
         Button.Tone tone = picked ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
         String mark = picked ? "> " : "";
         this.addStyled(22, y, nameW, 20, mark + profile.name, tone, b -> this.selectProfileRow(profile)).setToggled(picked);
         RiptideStyledButton delete = this.addStyled(22 + nameW + 4, y, 22, 20, "X", Button.Tone.DANGER, b -> this.deleteProfile(profile));
         delete.active = !this.isActiveProfile(profile.id);
         y += 24;
      }
   }

   private void addAccountButtons() {
      List<RiptideMultiScreen.AccountChoice> choices = this.accountChoices();
      boolean manual = this.draft.proxyMode == MultiProfile.ProxyMode.Manual;
      boolean locked = this.isActiveProfile(this.draft.id);
      int top = this.accountsTop();
      int bottom = this.accountListBottom();
      int visible = Math.max(1, (bottom - top) / 24);
      this.accountScroll = Math.max(0, Math.min(this.accountScroll, Math.max(0, choices.size() - visible)));
      int end = Math.min(choices.size(), this.accountScroll + visible);
      int x = this.rightX() + 12;
      int w = this.rightWidth() - 24;
      int y = top;

      for (int i = this.accountScroll; i < end; i++) {
         RiptideMultiScreen.AccountChoice choice = choices.get(i);
         boolean current = choice.current();
         MultiProfile.SessionSpec selected = current ? null : this.selectedSpec(choice.id());
         Button.Tone tone = current ? Button.Tone.DANGER : (selected == null ? Button.Tone.NORMAL : Button.Tone.SUCCESS);
         boolean canToggle = !current;
         String accountLabel = choice.label() + (current ? " (Current)" : "");
         if (manual) {
            int proxyW = Math.max(1, Math.min(128, Math.max(36, w / 3)));
            if (proxyW + 4 >= w) {
               proxyW = Math.max(1, w / 2);
            }

            int toggleW = Math.max(1, w - proxyW - 4);
            RiptideStyledButton toggle = this.addStyled(x, y, toggleW, 20, accountLabel, tone, b -> this.toggleAccount(choice.id()));
            toggle.active = canToggle && !locked;
            toggle.setToggled(selected != null || current);
            if (selected == null) {
               RiptideStyledButton proxyButton = this.addStyled(x + toggleW + 4, y, proxyW, 20, "-", Button.Tone.NORMAL, b -> {});
               proxyButton.active = false;
            } else {
               RiptideStyledButton proxyButton = this.addStyled(
                  x + toggleW + 4,
                  y,
                  proxyW,
                  20,
                  this.proxyLabel(selected.proxyId()),
                  Button.Tone.PRIMARY,
                  button -> this.openManualProxyPicker("Proxy for " + choice.label(), selected.proxyId(), proxyId -> {
                     MultiProfile.SessionSpec spec = this.selectedSpec(choice.id());
                     if (spec != null) {
                        this.setSessionProxy(spec, proxyId);
                     }
                  })
               );
               proxyButton.active = !choice.current() && !locked;
            }
         } else {
            RiptideStyledButton toggle = this.addStyled(x, y, w, 20, accountLabel, tone, b -> this.toggleAccount(choice.id()));
            toggle.active = canToggle && !locked;
            toggle.setToggled(selected != null || current);
         }

         y += 24;
      }
   }

   private void captureFields() {
      if (this.nameField != null) {
         this.draft.name = safeTrim(this.nameField.getValue());
      }

      if (this.serverField != null) {
         this.draft.serverAddress = safeTrim(this.serverField.getValue());
      }

      if (this.concurrencyField != null) {
         this.draft.customConcurrency = parseInt(this.concurrencyField.getValue(), this.draft.customConcurrency);
      }

      if (this.delayField != null) {
         this.draft.customDelayMs = parseInt(this.delayField.getValue(), this.draft.customDelayMs);
      }

      this.draft.normalize();
   }

   private void markDirty() {
      this.dirty = true;
      this.lastDirtyAt = System.currentTimeMillis();
   }

   private void commit() {
      this.captureFields();
      MultiProfileManager.get().put(this.draft);
      this.selectedProfileId = this.draft.id;
      MultiProfileManager.get().setSelectedId(this.draft.id);
      boolean wasDirty = this.dirty;
      this.dirty = false;
      if (wasDirty) {
         this.savedFlashAt = System.currentTimeMillis();
      }
   }

   private void connect() {
      this.captureFields();
      this.draft.name = MultiProfileManager.get().nextAvailableName(this.draft.name, this.draft.id);
      MultiManager.StartResult result = MultiManager.get().start(this.draft);
      if (!result.ok()) {
         this.toast(MultiManager.singleLine(result.message(), 90), -42149);
      } else {
         MultiProfileManager.get().put(this.draft);
         this.selectedProfileId = this.draft.id;
         this.dirty = false;
         this.minecraft.gui.setScreen(new RiptideMultiConsoleScreen(this.parent));
      }
   }

   private void openActiveConsole() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideMultiConsoleScreen(this.parent));
      }
   }

   private void createNew() {
      if (this.dirty) {
         this.commit();
      }

      this.draft = this.newProfile();
      MultiProfileManager.get().put(this.draft);
      this.selectedProfileId = this.draft.id;
      MultiProfileManager.get().setSelectedId(this.draft.id);
      this.dirty = false;
      this.profileScroll = 0;
      this.accountScroll = 0;
      this.toast("New profile created.", -13248397);
      this.rebuildControls();
   }

   private void duplicateDraft() {
      if (this.dirty) {
         this.commit();
      }

      MultiProfile copy = new MultiProfile(this.draft);
      copy.id = UUID.randomUUID().toString();
      copy.name = MultiProfileManager.get().nextAvailableName(copy.name, copy.id);
      this.draft = copy;
      MultiProfileManager.get().put(this.draft);
      this.selectedProfileId = this.draft.id;
      this.dirty = false;
      this.toast("Duplicated.", -13248397);
      this.rebuildControls();
   }

   private void deleteProfile(MultiProfile profile) {
      this.captureFields();
      if (profile != null && this.isActiveProfile(profile.id)) {
         this.toast("Disconnect the active profile first.", -42149);
      } else {
         MultiProfileManager.get().remove(profile.id);
         if (profile.id.equals(this.selectedProfileId) || this.draft.id.equals(profile.id)) {
            List<MultiProfile> remaining = MultiProfileManager.get().all();
            this.draft = remaining.isEmpty() ? this.newProfile() : new MultiProfile(remaining.getFirst());
            this.selectedProfileId = remaining.isEmpty() ? null : this.draft.id;
            this.dirty = false;
         }

         this.toast("Deleted " + MultiManager.singleLine(profile.name, 40) + ".", -6645094);
         this.rebuildControls();
      }
   }

   private void openAdvanced() {
      this.captureFields();
      this.minecraft.gui.setScreen(new RiptideMultiPacketPolicyScreen(this, this.draft.packetPolicy, policy -> {
         this.draft.packetPolicy = policy;
         this.commit();
      }, this.draft.autoAccept, cfg -> {
         this.draft.autoAccept = cfg;
         this.commit();
      }, false));
   }

   private void openFormValues() {
      this.captureFields();
      Set<String> accounts = new LinkedHashSet<>();

      for (MultiProfile.SessionSpec spec : this.draft.sessions) {
         accounts.add(spec.accountId());
      }

      this.minecraft.gui.setScreen(new RiptideFormValuesScreen(this, this.draft, accounts, updated -> {
         this.draft = updated;
         this.commit();
         this.rebuildControls();
      }));
   }

   private void loadProfile(MultiProfile profile) {
      if (this.dirty) {
         this.commit();
      }

      this.draft = new MultiProfile(profile);
      this.selectedProfileId = this.draft.id;
      MultiProfileManager.get().setSelectedId(this.draft.id);
      this.dirty = false;
      this.accountScroll = 0;
      this.rebuildControls();
   }

   private void selectProfileRow(MultiProfile profile) {
      if (profile != null) {
         if (!profile.id.equals(this.selectedProfileId)) {
            this.loadProfile(profile);
         }
      }
   }

   private void addPacingDropdown(int x, int y, int width) {
      this.dropdowns.add(new CompactDropdown(x, y, width, 18, PACING_OPTIONS, this.draft.pacing.ordinal(), index -> {
         this.captureFields();
         if (index >= 0 && index < MultiProfile.Pacing.values().length) {
            this.draft.pacing = MultiProfile.Pacing.values()[index];
            this.commit();
            this.rebuildControls();
         }
      }).setButtonLabelOverride("Join speed: " + this.draft.pacing.name()));
   }

   private void addProxyModeDropdown(int x, int y, int width) {
      this.dropdowns.add(new CompactDropdown(x, y, width, 18, PROXY_MODE_OPTIONS, this.draft.proxyMode.ordinal(), index -> {
         this.captureFields();
         if (index >= 0 && index < MultiProfile.ProxyMode.values().length) {
            this.draft.proxyMode = MultiProfile.ProxyMode.values()[index];
            this.commit();
            this.rebuildControls();
         }
      }).setButtonLabelOverride("Proxy: " + this.proxyModeLabel()));
   }

   private void pickAllMacro() {
      this.captureFields();
      this.minecraft.gui.setScreen(new RiptideMultiMacroPickerScreen(this, this.draft.allMacroName, name -> {
         this.draft.allMacroName = name == null ? "" : name;
         this.commit();
      }));
   }

   private void toggleAccount(String accountId) {
      if (!this.isActiveProfile(this.draft.id)) {
         if (MultiManager.isCurrentRenderedAccount(accountId)) {
            this.toast("Current account cannot join Multi.", -42149);
         } else {
            this.captureFields();
            MultiProfile.SessionSpec existing = this.selectedSpec(accountId);
            if (existing != null) {
               this.draft.sessions.remove(existing);
            } else if (this.draft.sessions.size() < 500) {
               this.draft.sessions.add(new MultiProfile.SessionSpec(accountId, ""));
            } else {
               this.toast("Maximum 500 accounts.", -42149);
            }

            this.commit();
            this.rebuildControls();
         }
      }
   }

   private void setSessionProxy(MultiProfile.SessionSpec existing, String proxyId) {
      if (!this.isActiveProfile(this.draft.id)) {
         int index = this.draft.sessions.indexOf(existing);
         if (index >= 0) {
            this.draft.sessions.set(index, new MultiProfile.SessionSpec(existing.accountId(), proxyId, existing.macroName()));
         }

         this.commit();
         this.rebuildControls();
      }
   }

   private void openManualProxyPicker(String title, String selectedProxyId, Consumer<String> onPick) {
      this.captureFields();
      if (this.minecraft != null) {
         this.minecraft
            .gui
            .setScreen(new RiptideMultiProxyPickerScreen(this, title, this.draft.serverAddress, selectedProxyId, this.manualProxyUsage(), onPick));
      }
   }

   private Map<String, Integer> manualProxyUsage() {
      Map<String, Integer> usage = new LinkedHashMap<>();

      for (MultiProfile.SessionSpec spec : this.draft.sessions) {
         if (!spec.direct() && !spec.bestProxy()) {
            usage.merge(spec.proxyId(), 1, Integer::sum);
         }
      }

      return Map.copyOf(usage);
   }

   private void setAllProxies(String proxyId) {
      if (!this.isActiveProfile(this.draft.id)) {
         if (this.draft.sessions.isEmpty()) {
            this.toast("Select some accounts first.", -42149);
         } else {
            String id = proxyId == null ? "" : proxyId;

            for (int i = 0; i < this.draft.sessions.size(); i++) {
               MultiProfile.SessionSpec spec = this.draft.sessions.get(i);
               this.draft.sessions.set(i, new MultiProfile.SessionSpec(spec.accountId(), id, spec.macroName()));
            }

            this.commit();
            this.rebuildControls();
            this.toast("Set proxy on " + this.draft.sessions.size() + " account" + (this.draft.sessions.size() == 1 ? "" : "s") + ".", -13248397);
         }
      }
   }

   private MultiProfile.SessionSpec selectedSpec(String accountId) {
      for (MultiProfile.SessionSpec spec : this.draft.sessions) {
         if (spec.accountId().equals(accountId)) {
            return spec;
         }
      }

      return null;
   }

   private String proxyLabel(String proxyId) {
      if (proxyId == null || proxyId.isBlank()) {
         return "Proxy Off";
      } else if ("best".equals(proxyId)) {
         return "Best Proxy";
      } else {
         RiptideProxy proxy = RiptideProxyManager.get().findById(proxyId);
         return proxy == null ? "Missing" : proxy.displayName();
      }
   }

   private String proxyModeLabel() {
      return switch (this.draft.proxyMode) {
         case Off -> "Off";
         case Auto -> "Auto";
         case Manual -> "Manual";
      };
   }

   private String commonManualProxyId() {
      if (this.draft.sessions.isEmpty()) {
         return "";
      } else {
         String common = this.draft.sessions.getFirst().proxyId();

         for (int i = 1; i < this.draft.sessions.size(); i++) {
            if (!Objects.equals(common, this.draft.sessions.get(i).proxyId())) {
               return null;
            }
         }

         return common == null ? "" : common;
      }
   }

   private List<RiptideMultiScreen.AccountChoice> accountChoices() {
      List<RiptideMultiScreen.AccountChoice> choices = new ArrayList<>();
      Set<String> known = new HashSet<>();
      choices.add(
         new RiptideMultiScreen.AccountChoice(
            "default", RiptideAccountSessionSwitcher.getOriginalUser().getName() + " (Default)", MultiManager.isCurrentRenderedAccount("default")
         )
      );
      known.add("default");
      String query = this.accountSearchText().toLowerCase(Locale.ROOT);

      for (RiptideAccount account : RiptideAccountManager.get().all()) {
         known.add(account.stableId());
         RiptideAccountType type = account.type == null ? RiptideAccountType.Cracked : account.type;
         if (this.typeFilters.contains(type)) {
            String label = account.displayName() + " (" + type.name() + ")";
            if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query)) {
               choices.add(new RiptideMultiScreen.AccountChoice(account.stableId(), label, MultiManager.isCurrentRenderedAccount(account.stableId())));
            }
         }
      }

      for (MultiProfile.SessionSpec spec : this.draft.sessions) {
         if (known.add(spec.accountId())) {
            choices.add(new RiptideMultiScreen.AccountChoice(spec.accountId(), "Missing account: " + spec.accountId(), false));
         }
      }

      return choices;
   }

   public boolean charTyped(CharacterEvent event) {
      return this.accountSearchField != null && this.accountSearchField.charTyped(event) ? true : super.charTyped(event);
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 256 && CompactDropdown.closeOpenMenu(this.dropdowns)) {
         return true;
      } else {
         return this.accountSearchField != null && this.accountSearchField.keyPressed(event) ? true : super.keyPressed(event);
      }
   }

   private void ensureAccountSearchField() {
      if (this.accountSearchField == null) {
         this.accountSearchField = new RiptideChatField(Minecraft.getInstance(), this.font, 0, 0, 20, 16, false);
         this.accountSearchField.setPlaceholder(Component.literal("Search accounts..."));
         this.accountSearchField.setMaxLength(48);
         this.accountSearchField.setChangedListener(value -> {
            this.accountScroll = 0;
            this.rebuildControls();
         });
      }
   }

   private String accountSearchText() {
      return this.accountSearchField == null ? "" : this.accountSearchField.getText();
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      double vx = RiptideUiScale.toVirtual(mouseX);
      double vy = RiptideUiScale.toVirtual(mouseY);
      if (CompactDropdown.mouseScrolled(this.dropdowns, vx, vy, vertical)) {
         return true;
      } else if (vx < 14 + this.leftWidth()) {
         this.captureFields();
         this.profileScroll = Math.max(0, this.profileScroll + (vertical < 0.0 ? 1 : -1));
         this.rebuildControls();
         return true;
      } else if (vx >= this.rightX() && vy >= this.accountsTop() - 4) {
         this.captureFields();
         this.accountScroll = Math.max(0, this.accountScroll + (vertical < 0.0 ? 1 : -1));
         this.rebuildControls();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (CompactDropdown.mouseClicked(this.dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
         return true;
      } else if (this.accountSearchField != null && this.accountSearchField.mouseClicked(virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
         this.captureFields();
         this.clearInputFocus();
         return true;
      } else {
         return super.mouseClicked(virtualEvent, doubled);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (CompactDropdown.mouseReleased(this.dropdowns)) {
         return true;
      } else {
         MouseButtonEvent virtualEvent = virtualEvent(event);
         if (this.accountSearchField != null) {
            this.accountSearchField.mouseReleased(virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
         }

         return super.mouseReleased(virtualEvent);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (CompactDropdown.mouseDragged(this.dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
         return true;
      } else {
         double vdx = RiptideUiScale.toVirtual(dragX);
         double vdy = RiptideUiScale.toVirtual(dragY);
         return this.accountSearchField != null && this.accountSearchField.mouseDragged(virtualEvent.x(), virtualEvent.y(), virtualEvent.button(), vdx, vdy)
            ? true
            : super.mouseDragged(virtualEvent, vdx, vdy);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), themeBg());
         int leftPanelHeight = Math.max(1, this.profileListBottom() + 6 - 14);
         int rightPanelHeight = Math.max(1, this.accountListBottom() + 6 - 14);
         UiRenderer.frame(graphics, UiBounds.of(14, 14, this.leftWidth(), leftPanelHeight), themePanelSoft(), themeBorder());
         UiRenderer.frame(graphics, UiBounds.of(this.rightX(), 14, this.rightWidth(), rightPanelHeight), themePanel(), themeBorder());
         this.drawText(graphics, "Profiles", 24, 24, themeText());
         this.drawText(graphics, this.profileStateLabel(), this.rightX() + 12, 24, themeText());
         int ix = this.rightX() + 12;
         int innerW = this.rightWidth() - 24;
         this.drawText(graphics, "Accounts", ix, this.accountsBaseY(), themeText());
         long selectedCount = this.draft.sessions.stream().filter(s -> !MultiManager.isCurrentRenderedAccount(s.accountId())).count();
         this.drawRight(graphics, selectedCount + " selected", ix + innerW, this.accountsBaseY(), themeMuted());
         this.renderSearchBox(graphics, virtualMouseX, virtualMouseY, delta);
         boolean menuOpen = CompactDropdown.isMenuOpen(this.dropdowns);
         int mx = menuOpen ? Integer.MIN_VALUE : virtualMouseX;
         int my = menuOpen ? Integer.MIN_VALUE : virtualMouseY;
         super.extractRenderState(graphics, mx, my, delta);
         CompactDropdown.renderAll(graphics, this.font, this.dropdowns, virtualMouseX, virtualMouseY);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderSearchBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      UiBounds box = this.searchBox();
      this.ensureAccountSearchField();
      this.accountSearchField.setX(box.x());
      this.accountSearchField.setY(box.y());
      this.accountSearchField.setWidth(box.width());
      this.accountSearchField.setHeight(box.height());
      this.accountSearchField.render(graphics, mouseX, mouseY, delta);
   }

   private UiBounds searchBox() {
      int ix = this.rightX() + 12;
      return UiBounds.of(ix, this.accountsBaseY() + 12, Math.max(20, this.rightWidth() - 24 - 76 - 4), 16);
   }

   private String profileStateLabel() {
      if (this.selectedProfileId == null) {
         return "New profile";
      } else {
         String name = MultiManager.singleLine(this.draft.name, 28);
         if (this.dirty) {
            return "Editing " + name + " (changed)";
         } else {
            return System.currentTimeMillis() - this.savedFlashAt < 1500L ? "Editing " + name + " (saved)" : "Editing " + name;
         }
      }
   }

   private int accountsHeaderBaseY() {
      return 92 + (this.draft.pacing == MultiProfile.Pacing.Custom ? 26 : 0) + 52;
   }

   private int accountsBaseY() {
      return this.accountsHeaderBaseY() + (this.showBulkProxy() ? 22 : 0);
   }

   private boolean showBulkProxy() {
      return this.draft.proxyMode == MultiProfile.ProxyMode.Manual && !this.isActiveProfile(this.draft.id);
   }

   private int chipsY() {
      return this.accountsBaseY() + 32;
   }

   private int accountsTop() {
      return this.accountsBaseY() + (this.compactVertical() ? 32 : 52);
   }

   public void onClose() {
      if (this.dirty) {
         this.commit();
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private RiptideStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone, OnPress press) {
      Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
      RiptideStyledButton button = new RiptideStyledButton(x, y, Math.max(1, w), h, Component.literal(this.fitLabel(text, w - 6)), interactiveTone, press);
      this.addRenderableWidget(button);
      return button;
   }

   private String fitLabel(String text, int width) {
      return UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(text, 72), Math.max(1, width), THEME.fontFor(UiTone.BODY), themeText());
   }

   private void clearInputFocus() {
      if (this.nameField != null) {
         this.nameField.setFocused(false);
      }

      if (this.serverField != null) {
         this.serverField.setFocused(false);
      }

      if (this.concurrencyField != null) {
         this.concurrencyField.setFocused(false);
      }

      if (this.delayField != null) {
         this.delayField.setFocused(false);
      }

      this.setFocused(null);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      UiText.draw(graphics, this.font, MultiManager.singleLine(text, 120), fontId, color, x, y, false);
   }

   private void drawRight(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      int w = UiText.width(this.font, text, fontId, color);
      UiText.draw(graphics, this.font, text, fontId, color, right - w, y, false);
   }

   private static int themeBg() {
      return RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP);
   }

   private static int themePanel() {
      return RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON);
   }

   private static int themePanelSoft() {
      return RiptideTheme.recolor(-1206643689, RiptideTheme.Channel.BUTTON);
   }

   private static int themeBorder() {
      return RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
   }

   private static int themeText() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private static int themeMuted() {
      return RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
   }

   private MultiProfile newProfile() {
      MultiProfile profile = new MultiProfile();
      profile.name = MultiProfileManager.get().nextAvailableName("New profile", profile.id);
      profile.serverAddress = this.prefillServer;
      if (!MultiManager.isCurrentRenderedAccount("default")) {
         profile.sessions.add(new MultiProfile.SessionSpec("default", ""));
      } else {
         for (RiptideAccount account : RiptideAccountManager.get().all()) {
            if (!MultiManager.isCurrentRenderedAccount(account.stableId())) {
               profile.sessions.add(new MultiProfile.SessionSpec(account.stableId(), ""));
               break;
            }
         }
      }

      return profile;
   }

   private boolean isActiveProfile(String profileId) {
      if (profileId != null && MultiManager.get().isActive()) {
         MultiProfile active = MultiManager.get().activeProfile();
         return active != null && profileId.equals(active.id);
      } else {
         return false;
      }
   }

   private int rightX() {
      return 14 + this.leftWidth() + 8;
   }

   private int rightWidth() {
      return Math.max(1, this.screenWidth() - this.rightX() - 14);
   }

   private int leftWidth() {
      int roomAfterMinimumEditor = this.screenWidth() - 28 - 8 - 180;
      return Math.max(90, Math.min(176, roomAfterMinimumEditor));
   }

   private boolean compactFooter() {
      return this.screenWidth() < 620;
   }

   private boolean compactVertical() {
      return this.screenHeight() < 300;
   }

   private int profileListBottom() {
      return this.screenHeight() - (this.compactFooter() ? 70 : 48);
   }

   private int accountListBottom() {
      return this.screenHeight() - 48;
   }

   private static List<String> pingOptionLabels() {
      List<String> labels = new ArrayList<>(PING_OPTIONS.length);

      for (int option : PING_OPTIONS) {
         labels.add(option + "ms");
      }

      return labels;
   }

   private static int pingIndex(int value) {
      int index = 0;
      int best = Integer.MAX_VALUE;

      for (int i = 0; i < PING_OPTIONS.length; i++) {
         int distance = Math.abs(PING_OPTIONS[i] - value);
         if (distance < best) {
            best = distance;
            index = i;
         }
      }

      return index;
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value.trim());
      } catch (RuntimeException var3) {
         return fallback;
      }
   }

   private record AccountChoice(String id, String label, boolean current) {
   }
}
