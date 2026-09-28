package riptide.util.multi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import riptide.util.mm.crypto.AtRestSeal;

public final class MultiProfile {
   public static final int MAX_SESSIONS = 500;
   public static final String DEFAULT_ACCOUNT_ID = "default";
   public static final String BEST_PROXY_ID = "best";
   public static final int QUICK_ACTIONS = 4;
   public String id = UUID.randomUUID().toString();
   public String name = "New profile";
   public String serverAddress = "";
   public MultiProfile.Pacing pacing = MultiProfile.Pacing.Balanced;
   public MultiProfile.ProxyMode proxyMode = MultiProfile.ProxyMode.Off;
   public int customConcurrency = 4;
   public int customDelayMs = 250;
   public int autoMaxPingMs = 200;
   public String allMacroName = "";
   public boolean runMacroWhileJoining;
   public MultiProfile.LoginMode loginMode = MultiProfile.LoginMode.Auto;
   public final List<MultiProfile.SessionSpec> sessions = new ArrayList<>();
   private final Map<String, Map<String, String>> sealedFormValues = new LinkedHashMap<>();
   public MultiPacketPolicy packetPolicy = new MultiPacketPolicy();
   public MultiAutoAccept autoAccept = new MultiAutoAccept();
   public final List<MultiQuickAction> quickActions = new ArrayList<>(defaultQuickActions());

   public MultiProfile() {
   }

   public MultiProfile(MultiProfile source) {
      if (source != null) {
         this.id = source.id;
         this.name = source.name;
         this.serverAddress = source.serverAddress;
         this.pacing = source.pacing;
         this.proxyMode = source.proxyMode;
         this.customConcurrency = source.customConcurrency;
         this.customDelayMs = source.customDelayMs;
         this.autoMaxPingMs = source.autoMaxPingMs;
         this.allMacroName = source.allMacroName;
         this.runMacroWhileJoining = source.runMacroWhileJoining;
         this.loginMode = source.loginMode;
         this.sessions.addAll(source.sessions);
         source.sealedFormValues
            .forEach((account, values) -> this.sealedFormValues.put(account, new LinkedHashMap<>((Map<? extends String, ? extends String>)values)));
         this.packetPolicy = new MultiPacketPolicy(source.packetPolicy);
         this.autoAccept = new MultiAutoAccept(source.autoAccept);
         this.quickActions.clear();

         for (MultiQuickAction action : source.quickActions) {
            this.quickActions.add(new MultiQuickAction(action));
         }

         this.normalize();
      }
   }

   public int concurrency() {
      return this.pacing == MultiProfile.Pacing.Custom ? this.customConcurrency : this.pacing.concurrency();
   }

   public int delayMs() {
      return this.pacing == MultiProfile.Pacing.Custom ? this.customDelayMs : this.pacing.delayMs();
   }

   public void normalize() {
      if (this.id == null || this.id.isBlank()) {
         this.id = UUID.randomUUID().toString();
      }

      this.name = this.name != null && !this.name.isBlank() ? this.name.trim() : "New profile";
      this.serverAddress = this.serverAddress == null ? "" : this.serverAddress.trim();
      if (this.pacing == null) {
         this.pacing = MultiProfile.Pacing.Balanced;
      }

      if (this.proxyMode == null) {
         this.proxyMode = MultiProfile.ProxyMode.Off;
      }

      this.allMacroName = this.allMacroName == null ? "" : this.allMacroName.trim();
      this.customConcurrency = Math.max(1, Math.min(500, this.customConcurrency));
      this.customDelayMs = Math.max(0, Math.min(5000, this.customDelayMs));
      this.autoMaxPingMs = Math.max(50, Math.min(1000, this.autoMaxPingMs));
      if (this.packetPolicy == null) {
         this.packetPolicy = new MultiPacketPolicy();
      }

      if (this.autoAccept == null) {
         this.autoAccept = new MultiAutoAccept();
      }

      this.autoAccept.normalize();
      this.normalizeQuickActions();
      Set<String> accounts = new HashSet<>();
      this.sessions.removeIf(spec -> spec == null || spec.accountId().isBlank() || !accounts.add(spec.accountId()));

      while (this.sessions.size() > 500) {
         this.sessions.remove(this.sessions.size() - 1);
      }

      this.sealedFormValues.keySet().removeIf(account -> !accounts.contains(account));
      this.sealedFormValues
         .values()
         .forEach(
            values -> values.entrySet()
               .removeIf(entry -> normalizeSecretName(entry.getKey()).isEmpty() || entry.getValue() == null || entry.getValue().isBlank())
         );
      this.sealedFormValues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
   }

   public boolean setFormValue(String accountId, String name, String value) {
      String account = accountId == null ? "" : accountId.trim();
      String key = normalizeSecretName(name);
      if (!account.isEmpty() && !key.isEmpty() && value != null) {
         byte[] sealed = AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
         this.sealedFormValues.computeIfAbsent(account, ignored -> new LinkedHashMap<>()).put(key, Base64.getEncoder().encodeToString(sealed));
         return true;
      } else {
         return false;
      }
   }

   public void removeFormValue(String accountId, String name) {
      Map<String, String> values = this.sealedFormValues.get(accountId == null ? "" : accountId.trim());
      if (values != null) {
         values.remove(normalizeSecretName(name));
         if (values.isEmpty()) {
            this.sealedFormValues.remove(accountId == null ? "" : accountId.trim());
         }
      }
   }

   public void clearFormValues(String accountId) {
      this.sealedFormValues.remove(accountId == null ? "" : accountId.trim());
   }

   public Set<String> formValueNames(String accountId) {
      Map<String, String> values = this.sealedFormValues.get(accountId == null ? "" : accountId.trim());
      return values == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
   }

   public Map<String, String> openFormValues(String accountId) {
      Map<String, String> sealed = this.sealedFormValues.get(accountId == null ? "" : accountId.trim());
      if (sealed != null && !sealed.isEmpty()) {
         Map<String, String> opened = new LinkedHashMap<>();
         sealed.forEach((name, encoded) -> {
            try {
               byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
               if (plain != null) {
                  opened.put(name, new String(plain, StandardCharsets.UTF_8));
               }
            } catch (IllegalArgumentException var4) {
            }
         });
         return Map.copyOf(opened);
      } else {
         return Map.of();
      }
   }

   public static String normalizeSecretName(String name) {
      if (name == null) {
         return "";
      } else {
         String value = name.trim().toLowerCase(Locale.ROOT);
         if (value.startsWith("secret.")) {
            value = value.substring("secret.".length());
         }

         return value.matches("[a-z0-9_.-]{1,64}") ? value : "";
      }
   }

   public MultiQuickAction quickAction(int index) {
      if (index >= 0 && index < 4) {
         this.normalizeQuickActions();
         return new MultiQuickAction(this.quickActions.get(index));
      } else {
         throw new IndexOutOfBoundsException(index);
      }
   }

   public void setQuickAction(int index, MultiQuickAction action) {
      if (index >= 0 && index < 4) {
         this.normalizeQuickActions();
         this.quickActions.set(index, action == null ? new MultiQuickAction() : new MultiQuickAction(action));
      } else {
         throw new IndexOutOfBoundsException(index);
      }
   }

   private void normalizeQuickActions() {
      while (this.quickActions.size() < 4) {
         this.quickActions.add(new MultiQuickAction());
      }

      while (this.quickActions.size() > 4) {
         this.quickActions.remove(this.quickActions.size() - 1);
      }

      for (int i = 0; i < this.quickActions.size(); i++) {
         MultiQuickAction action = this.quickActions.get(i);
         if (action == null) {
            action = new MultiQuickAction();
         }

         action.normalize();
         this.quickActions.set(i, action);
      }
   }

   public void resetQuickActions() {
      this.quickActions.clear();
      this.quickActions.addAll(defaultQuickActions());
   }

   public boolean replaceMacroReference(String oldName, String newName) {
      String before = oldName == null ? "" : oldName.trim();
      if (before.isBlank()) {
         return false;
      } else {
         String after = newName == null ? "" : newName.trim();
         boolean changed = false;
         if (before.equals(this.allMacroName)) {
            this.allMacroName = after;
            changed = true;
         }

         for (int i = 0; i < this.sessions.size(); i++) {
            MultiProfile.SessionSpec spec = this.sessions.get(i);
            if (before.equals(spec.macroName())) {
               this.sessions.set(i, spec.withMacro(after));
               changed = true;
            }
         }

         return changed;
      }
   }

   public CompoundTag toTag() {
      this.normalize();
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.id);
      tag.putString("name", this.name);
      tag.putString("server", this.serverAddress);
      tag.putString("pacing", this.pacing.name());
      tag.putString("proxyMode", this.proxyMode.name());
      tag.putInt("customConcurrency", this.customConcurrency);
      tag.putInt("customDelayMs", this.customDelayMs);
      tag.putInt("autoMaxPingMs", this.autoMaxPingMs);
      tag.putString("allMacroName", this.allMacroName);
      tag.putBoolean("runMacroWhileJoining", this.runMacroWhileJoining);
      tag.putString("loginMode", this.loginMode.name());
      ListTag sessionTags = new ListTag();

      for (MultiProfile.SessionSpec session : this.sessions) {
         sessionTags.add(session.toTag());
      }

      tag.put("sessions", sessionTags);
      ListTag formAccounts = new ListTag();
      this.sealedFormValues.forEach((accountId, values) -> {
         CompoundTag accountTag = new CompoundTag();
         accountTag.putString("accountId", accountId);
         ListTag valueTags = new ListTag();
         values.forEach((key, sealed) -> {
            CompoundTag valueTag = new CompoundTag();
            valueTag.putString("name", key);
            valueTag.putString("sealed", sealed);
            valueTags.add(valueTag);
         });
         accountTag.put("values", valueTags);
         formAccounts.add(accountTag);
      });
      tag.put("formValues", formAccounts);
      tag.put("packetPolicy", this.packetPolicy.toTag());
      tag.put("autoAccept", this.autoAccept.toTag());
      ListTag actionTags = new ListTag();

      for (MultiQuickAction action : this.quickActions) {
         actionTags.add(action.toTag());
      }

      tag.put("quickActions", actionTags);
      return tag;
   }

   public static MultiProfile fromTag(CompoundTag tag) {
      MultiProfile profile = new MultiProfile();
      profile.id = tag.getStringOr("id", profile.id);
      profile.name = tag.getStringOr("name", profile.name);
      profile.serverAddress = tag.getStringOr("server", "");
      String pacingName = tag.getStringOr("pacing", MultiProfile.Pacing.Balanced.name());
      profile.pacing = MultiProfile.Pacing.Balanced;

      for (MultiProfile.Pacing value : MultiProfile.Pacing.values()) {
         if (value.name().equalsIgnoreCase(pacingName)) {
            profile.pacing = value;
            break;
         }
      }

      profile.customConcurrency = tag.getIntOr("customConcurrency", 4);
      profile.customDelayMs = tag.getIntOr("customDelayMs", 250);
      profile.autoMaxPingMs = tag.getIntOr("autoMaxPingMs", 200);
      profile.allMacroName = tag.getStringOr("allMacroName", "");
      profile.runMacroWhileJoining = tag.getBooleanOr("runMacroWhileJoining", false);
      profile.loginMode = MultiProfile.LoginMode.Auto;
      String loginModeName = tag.getStringOr("loginMode", "");

      for (MultiProfile.LoginMode valuex : MultiProfile.LoginMode.values()) {
         if (valuex.name().equalsIgnoreCase(loginModeName)) {
            profile.loginMode = valuex;
            break;
         }
      }

      profile.sessions.clear();

      for (Tag valuexx : tag.getListOrEmpty("sessions")) {
         if (valuexx instanceof CompoundTag compound) {
            profile.sessions.add(MultiProfile.SessionSpec.fromTag(compound));
         }
      }

      profile.sealedFormValues.clear();

      for (Tag accountValue : tag.getListOrEmpty("formValues")) {
         if (accountValue instanceof CompoundTag accountTag) {
            String accountId = accountTag.getStringOr("accountId", "").trim();
            if (!accountId.isEmpty()) {
               Map<String, String> values = new LinkedHashMap<>();

               for (Tag formValue : accountTag.getListOrEmpty("values")) {
                  if (formValue instanceof CompoundTag valueTag) {
                     String key = normalizeSecretName(valueTag.getStringOr("name", ""));
                     String sealed = valueTag.getStringOr("sealed", "");
                     if (!key.isEmpty() && !sealed.isBlank()) {
                        values.put(key, sealed);
                     }
                  }
               }

               if (!values.isEmpty()) {
                  profile.sealedFormValues.put(accountId, values);
               }
            }
         }
      }

      String proxyModeName = tag.getStringOr("proxyMode", "");
      if (proxyModeName.isBlank()) {
         boolean anyProxy = profile.sessions.stream().anyMatch(spec -> !spec.proxyId().isBlank());
         profile.proxyMode = anyProxy ? MultiProfile.ProxyMode.Manual : MultiProfile.ProxyMode.Off;
      } else {
         profile.proxyMode = MultiProfile.ProxyMode.Off;

         for (MultiProfile.ProxyMode valuexxx : MultiProfile.ProxyMode.values()) {
            if (valuexxx.name().equalsIgnoreCase(proxyModeName)) {
               profile.proxyMode = valuexxx;
               break;
            }
         }
      }

      profile.packetPolicy = tag.getCompound("packetPolicy").map(MultiPacketPolicy::fromTag).orElseGet(MultiPacketPolicy::new);
      profile.autoAccept = tag.getCompound("autoAccept").map(MultiAutoAccept::fromTag).orElseGet(MultiAutoAccept::new);
      profile.quickActions.clear();
      ListTag actionTags = tag.getListOrEmpty("quickActions");
      if (actionTags.isEmpty()) {
         profile.quickActions.addAll(migrateQuickActions(profile.packetPolicy));
      } else {
         for (Tag valuexxxx : actionTags) {
            if (valuexxxx instanceof CompoundTag compound) {
               profile.quickActions.add(MultiQuickAction.fromTag(compound));
            }
         }
      }

      profile.normalize();
      return profile;
   }

   public static List<MultiQuickAction> defaultQuickActions() {
      List<MultiQuickAction> actions = new ArrayList<>(4);

      for (int i = 0; i < 4; i++) {
         actions.add(new MultiQuickAction());
      }

      return actions;
   }

   private static List<MultiQuickAction> migrateQuickActions(MultiPacketPolicy policy) {
      List<MultiQuickAction> actions = defaultQuickActions();
      if (policy == null) {
         return actions;
      } else {
         List<MultiPacketPolicy.Slot> slots = policy.slots();

         for (int i = 0; i < Math.min(4, slots.size()); i++) {
            MultiPacketPolicy.Slot slot = slots.get(i);
            if (slot != null && slot.enabled() && !slot.packetClass().isBlank()) {
               actions.set(i, new MultiQuickAction("Slot " + (i + 1), slot.packetClass(), ""));
            }
         }

         return actions;
      }
   }

   public static enum LoginMode {
      Off,
      Auto,
      Custom;
   }

   public static enum Pacing {
      Gentle(1, 1000),
      Balanced(4, 250),
      Fast(8, 100),
      Immediate(500, 0),
      Custom(4, 250);

      private final int concurrency;
      private final int delayMs;

      private Pacing(int concurrency, int delayMs) {
         this.concurrency = concurrency;
         this.delayMs = delayMs;
      }

      public int concurrency() {
         return this.concurrency;
      }

      public int delayMs() {
         return this.delayMs;
      }
   }

   public static enum ProxyMode {
      Off,
      Auto,
      Manual;
   }

   public record SessionSpec(String accountId, String proxyId, String macroName) {
      public SessionSpec(String accountId, String proxyId, String macroName) {
         accountId = accountId == null ? "" : accountId.trim();
         proxyId = proxyId == null ? "" : proxyId.trim();
         macroName = macroName == null ? "" : macroName.trim();
         this.accountId = accountId;
         this.proxyId = proxyId;
         this.macroName = macroName;
      }

      public SessionSpec(String accountId, String proxyId) {
         this(accountId, proxyId, "");
      }

      public boolean direct() {
         return this.proxyId.isBlank();
      }

      public boolean bestProxy() {
         return "best".equals(this.proxyId);
      }

      public MultiProfile.SessionSpec withMacro(String macro) {
         return new MultiProfile.SessionSpec(this.accountId, this.proxyId, macro);
      }

      CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("accountId", this.accountId);
         tag.putString("proxyId", this.proxyId);
         if (!this.macroName.isBlank()) {
            tag.putString("macroName", this.macroName);
         }

         return tag;
      }

      static MultiProfile.SessionSpec fromTag(CompoundTag tag) {
         return new MultiProfile.SessionSpec(tag.getStringOr("accountId", ""), tag.getStringOr("proxyId", ""), tag.getStringOr("macroName", ""));
      }
   }
}
