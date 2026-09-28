package riptide.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import riptide.modules.RiptideModule;

public final class RiptidePayloadChannelRegistrations {
   public static final String SOURCE_CUSTOM = "custom";
   public static final String SOURCE_PRESET = "preset";
   public static final String SOURCE_LEARNED = "learned";
   private final List<RiptideConfig.PayloadChannelRegistrationRule> rules = new ArrayList<>();

   public RiptidePayloadChannelRegistrations() {
      this.load();
   }

   public void load() {
      this.rules.clear();
      RiptideConfig config = RiptideConfig.getGlobal();
      if (config.packetLoggerPayloadRegistrations == null) {
         config.packetLoggerPayloadRegistrations = new ArrayList<>();
      }

      Map<String, RiptideConfig.PayloadChannelRegistrationRule> merged = new LinkedHashMap<>();

      for (RiptideConfig.PayloadChannelRegistrationRule rule : config.packetLoggerPayloadRegistrations) {
         RiptideConfig.PayloadChannelRegistrationRule clean = clean(rule);
         if (clean != null) {
            mergeInto(merged, clean);
         }
      }

      if (merged.isEmpty() && config.packetLoggerPayloadFilters != null) {
         this.migrateExactFilters(merged, config.packetLoggerPayloadFilters);
      }

      this.rules.addAll(merged.values());
   }

   public List<RiptideConfig.PayloadChannelRegistrationRule> rules() {
      return Collections.unmodifiableList(this.rules);
   }

   List<RiptideConfig.PayloadChannelRegistrationRule> mutableRules() {
      return this.rules;
   }

   public List<String> enabledChannels() {
      List<String> channels = new ArrayList<>();

      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null && rule.enabled && isRegisterableChannel(rule.channel)) {
            channels.add(normalizeChannel(rule.channel));
         }
      }

      return channels;
   }

   public boolean hasEnabled(String channel) {
      String normalized = normalizeChannel(channel);
      if (normalized.isBlank()) {
         return false;
      } else {
         for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
            if (rule != null && rule.enabled && normalized.equals(normalizeChannel(rule.channel))) {
               return true;
            }
         }

         return false;
      }
   }

   public int indexOf(String channel) {
      String normalized = normalizeChannel(channel);

      for (int i = 0; i < this.rules.size(); i++) {
         RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(i);
         if (rule != null && normalized.equals(normalizeChannel(rule.channel))) {
            return i;
         }
      }

      return -1;
   }

   public boolean addOrEnable(String label, String channel, String source) {
      boolean changed = this.addOrEnableInMemory(label, channel, source);
      if (changed) {
         this.save();
      }

      return changed;
   }

   public boolean addOrEnableInMemory(String label, String channel, String source) {
      String normalized = normalizeChannel(channel);
      if (!isRegisterableChannel(normalized)) {
         return false;
      } else {
         int index = this.indexOf(normalized);
         if (index >= 0) {
            RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
               rule.enabled = true;
               changed = true;
            }

            String cleanLabel = cleanLabel(label, normalized);
            if ((rule.label == null || rule.label.isBlank() || rule.label.equals(rule.channel)) && !cleanLabel.equals(normalized)) {
               rule.label = cleanLabel;
               changed = true;
            }

            if (rule.source == null || rule.source.isBlank() || "learned".equals(rule.source)) {
               rule.source = cleanSource(source);
               changed = true;
            }

            return changed;
         } else {
            RiptideConfig.PayloadChannelRegistrationRule rulex = new RiptideConfig.PayloadChannelRegistrationRule();
            rulex.channel = normalized;
            rulex.label = cleanLabel(label, normalized);
            rulex.enabled = true;
            rulex.source = cleanSource(source);
            this.rules.add(rulex);
            return true;
         }
      }
   }

   public boolean addLearnedSuggestion(String label, String channel) {
      String normalized = normalizeChannel(channel);
      if (!isRegisterableChannel(normalized)) {
         return false;
      } else if (this.indexOf(normalized) >= 0) {
         return false;
      } else {
         RiptideConfig.PayloadChannelRegistrationRule rule = new RiptideConfig.PayloadChannelRegistrationRule();
         rule.channel = normalized;
         rule.label = cleanLabel(label, normalized);
         rule.enabled = false;
         rule.source = "learned";
         this.rules.add(rule);
         this.save();
         return true;
      }
   }

   public void toggle(int index) {
      if (index >= 0 && index < this.rules.size()) {
         RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(index);
         if (rule != null) {
            rule.enabled = !rule.enabled;
            this.save();
         }
      }
   }

   public void toggleInMemory(int index) {
      if (index >= 0 && index < this.rules.size()) {
         RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(index);
         if (rule != null) {
            rule.enabled = !rule.enabled;
         }
      }
   }

   public void remove(int index) {
      if (index >= 0 && index < this.rules.size()) {
         this.rules.remove(index);
         this.save();
      }
   }

   public void removeInMemory(int index) {
      if (index >= 0 && index < this.rules.size()) {
         this.rules.remove(index);
      }
   }

   public void disableAll() {
      boolean changed = false;

      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null && rule.enabled) {
            rule.enabled = false;
            changed = true;
         }
      }

      if (changed) {
         this.save();
      }
   }

   public void disableAllInMemory() {
      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null) {
            rule.enabled = false;
         }
      }
   }

   public void replaceWithApplied(Collection<String> channels) {
      Set<String> applied = new LinkedHashSet<>();
      if (channels != null) {
         for (String channel : channels) {
            String normalized = normalizeChannel(channel);
            if (isRegisterableChannel(normalized)) {
               applied.add(normalized);
            }
         }
      }

      boolean changed = false;

      for (String channelx : applied) {
         int index = this.indexOf(channelx);
         if (index >= 0) {
            RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(index);
            if (!rule.enabled) {
               rule.enabled = true;
               changed = true;
            }
         } else {
            RiptideConfig.PayloadChannelRegistrationRule rule = new RiptideConfig.PayloadChannelRegistrationRule();
            rule.channel = channelx;
            rule.label = channelx;
            rule.enabled = true;
            rule.source = "custom";
            this.rules.add(rule);
            changed = true;
         }
      }

      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null) {
            boolean shouldEnable = applied.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
               rule.enabled = shouldEnable;
               changed = true;
            }
         }
      }

      if (changed) {
         this.save();
      }
   }

   public void applyRecommendedOnly() {
      boolean changed = false;
      Set<String> defaults = new LinkedHashSet<>();

      for (RiptidePayloadChannelListeners.Preset preset : RiptidePayloadChannelListeners.presetCatalog()) {
         if (RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
               defaults.add(pattern);
            }
         }
      }

      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null) {
            boolean shouldEnable = defaults.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
               rule.enabled = shouldEnable;
               changed = true;
            }
         }
      }

      for (RiptidePayloadChannelListeners.Preset presetx : RiptidePayloadChannelListeners.presetCatalog()) {
         if (RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(presetx)) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(presetx.pattern());
            if (RiptidePayloadChannelListeners.isRegisterablePreset(presetx) && this.addOrEnableNoSave(presetx.label(), pattern, "preset")) {
               changed = true;
            }
         }
      }

      if (changed) {
         this.save();
      }
   }

   public void applyRecommendedOnlyInMemory() {
      Set<String> defaults = new LinkedHashSet<>();

      for (RiptidePayloadChannelListeners.Preset preset : RiptidePayloadChannelListeners.presetCatalog()) {
         if (RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
            if (RiptidePayloadChannelListeners.isRegisterablePreset(preset)) {
               defaults.add(pattern);
            }
         }
      }

      for (RiptideConfig.PayloadChannelRegistrationRule rule : this.rules) {
         if (rule != null) {
            rule.enabled = defaults.contains(normalizeChannel(rule.channel));
         }
      }

      for (RiptidePayloadChannelListeners.Preset presetx : RiptidePayloadChannelListeners.presetCatalog()) {
         if (RiptidePayloadChannelListeners.isDefaultRecommendedPresetPublic(presetx)) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(presetx.pattern());
            if (RiptidePayloadChannelListeners.isRegisterablePreset(presetx)) {
               this.addOrEnableNoSave(presetx.label(), pattern, "preset");
            }
         }
      }
   }

   public static boolean isRegisterableChannel(String channel) {
      String normalized = normalizeChannel(channel);
      if (normalized.isBlank() || normalized.indexOf(42) >= 0 || normalized.indexOf(58) <= 0) {
         return false;
      } else {
         return !"minecraft:register".equals(normalized) && !"minecraft:unregister".equals(normalized) && !"minecraft:brand".equals(normalized)
            ? Identifier.tryParse(normalized) != null
            : false;
      }
   }

   public static String normalizeChannel(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   public static String sourceLabel(String source) {
      String var1 = cleanSource(source);

      return switch (var1) {
         case "preset" -> "Preset";
         case "learned" -> "Learned";
         default -> "Custom";
      };
   }

   private void migrateExactFilters(Map<String, RiptideConfig.PayloadChannelRegistrationRule> merged, List<RiptideConfig.PayloadChannelFilterRule> filterRules) {
      for (RiptideConfig.PayloadChannelFilterRule filter : filterRules) {
         if (filter != null && filter.enabled) {
            String pattern = RiptidePayloadChannelListeners.normalizePattern(filter.pattern);
            if (isRegisterableChannel(pattern)) {
               RiptideConfig.PayloadChannelRegistrationRule rule = new RiptideConfig.PayloadChannelRegistrationRule();
               rule.channel = pattern;
               rule.label = cleanLabel(filter.label, pattern);
               rule.enabled = true;
               rule.source = filter.preset ? "preset" : "custom";
               mergeInto(merged, rule);
            }
         }
      }
   }

   private boolean addOrEnableNoSave(String label, String channel, String source) {
      String normalized = normalizeChannel(channel);
      if (!isRegisterableChannel(normalized)) {
         return false;
      } else {
         int index = this.indexOf(normalized);
         if (index >= 0) {
            RiptideConfig.PayloadChannelRegistrationRule rule = this.rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
               rule.enabled = true;
               changed = true;
            }

            if ("learned".equals(rule.source)) {
               rule.source = cleanSource(source);
               changed = true;
            }

            return changed;
         } else {
            RiptideConfig.PayloadChannelRegistrationRule rulex = new RiptideConfig.PayloadChannelRegistrationRule();
            rulex.channel = normalized;
            rulex.label = cleanLabel(label, normalized);
            rulex.enabled = true;
            rulex.source = cleanSource(source);
            this.rules.add(rulex);
            return true;
         }
      }
   }

   private static void mergeInto(Map<String, RiptideConfig.PayloadChannelRegistrationRule> merged, RiptideConfig.PayloadChannelRegistrationRule clean) {
      String key = normalizeChannel(clean.channel);
      RiptideConfig.PayloadChannelRegistrationRule existing = merged.get(key);
      if (existing == null) {
         merged.put(key, clean);
      } else {
         existing.enabled = existing.enabled | clean.enabled;
         if ("learned".equals(existing.source) && !"learned".equals(clean.source)) {
            existing.source = clean.source;
         }

         if ((existing.label == null || existing.label.isBlank() || existing.label.equals(existing.channel)) && clean.label != null && !clean.label.isBlank()) {
            existing.label = clean.label;
         }
      }
   }

   private static RiptideConfig.PayloadChannelRegistrationRule clean(RiptideConfig.PayloadChannelRegistrationRule rule) {
      if (rule == null) {
         return null;
      } else {
         String channel = normalizeChannel(rule.channel);
         if (!isRegisterableChannel(channel)) {
            return null;
         } else {
            String label = rule.label == null ? "" : rule.label.trim();
            if (!isPublicProbeChannel(channel) && !isPublicProbeChannel(label)) {
               RiptideConfig.PayloadChannelRegistrationRule clean = new RiptideConfig.PayloadChannelRegistrationRule();
               clean.channel = channel;
               clean.label = cleanLabel(label, channel);
               clean.enabled = rule.enabled;
               clean.source = cleanSource(rule.source);
               return clean;
            } else {
               return null;
            }
         }
      }
   }

   private static String cleanLabel(String label, String fallback) {
      String value = label == null ? "" : label.trim();
      return value.isBlank() ? fallback : value;
   }

   private static String cleanSource(String source) {
      String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);

      return switch (value) {
         case "preset", "learned" -> value;
         default -> "custom";
      };
   }

   private static boolean isPublicProbeChannel(String value) {
      String normalized = normalizeChannel(value);
      return normalized.isBlank()
         ? false
         : normalized.contains("riptidetest")
            || normalized.contains("payloadprobe")
            || normalized.contains("payload_probe")
            || normalized.contains("brandlike")
            || normalized.contains("mc_string")
            || normalized.contains("java_utf");
   }

   private void save() {
      this.commit(true);
   }

   public void commit(boolean writeFile) {
      RiptideConfig config = RiptideConfig.getGlobal();
      config.packetLoggerPayloadRegistrations = new ArrayList<>(this.rules);
      if (writeFile) {
         config.save();
      }

      RiptideModule.get().invalidatePayloadListenerCache(false);
   }
}
