package riptide.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.Setting;
import riptide.api.module.SettingOwner;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLock;

public abstract class Module implements SettingOwner {
   protected static final Minecraft MC = Minecraft.getInstance();
   private final String id;
   private final String name;
   private ModuleCategory category;
   private final String description;
   private final List<Setting<?, ?>> settings = new ArrayList<>();
   private String replacementToggleMessage;
   private boolean addon;

   protected Module(String id, String name, ModuleCategory category, String description) {
      this.id = id;
      this.name = name;
      this.category = category;
      this.description = description;
   }

   protected Module(String id, String name, String description) {
      this(id, name, null, description);
   }

   final void assignCategory(ModuleCategory category) {
      if (this.category == null) {
         this.category = category;
      }
   }

   public final String id() {
      return this.id;
   }

   public final boolean isAddon() {
      return this.addon;
   }

   final void markAddon() {
      this.addon = true;
   }

   public final String name() {
      return this.name;
   }

   public final ModuleCategory category() {
      return this.category;
   }

   public final String description() {
      return this.description;
   }

   public final boolean isEnabled() {
      return this.state().enabled & RiptideLock.allowed();
   }

   public final void setEnabled(boolean enabled) {
      if (!enabled || !PackHideState.blocksEnable(this)) {
         boolean wasEnabled = this.isEnabled();
         if (wasEnabled == enabled) {
            this.replacementToggleMessage = null;
         } else {
            this.state().enabled = enabled;
            if (enabled) {
               this.onEnable();
            } else {
               this.onDisable();
            }

            ModuleRegistry.markModuleEnabledChanged();
            this.save();
            String message = this.replacementToggleMessage;
            this.replacementToggleMessage = null;
            if (this.emitsToggleMessage() && !PackHideState.isSilenced()) {
               RiptideClientMessaging.sendPrefixed(
                  message != null && !message.isBlank() ? message : this.name + ": " + (this.isEnabled() ? "enabled" : "disabled")
               );
            }
         }
      }
   }

   public final void setConfiguredEnabled(boolean enabled) {
      if (!enabled || !PackHideState.blocksEnable(this)) {
         boolean wasEnabled = this.isEnabled();
         if (wasEnabled != enabled) {
            this.state().enabled = enabled;
            ModuleRegistry.recordOfflineConfiguredState(this, wasEnabled, enabled);
            ModuleRegistry.markModuleEnabledChanged();
            this.save();
         }
      }
   }

   protected final void setEnabledSilently(boolean enabled) {
      if (!enabled || !PackHideState.blocksEnable(this)) {
         boolean wasEnabled = this.isEnabled();
         if (wasEnabled != enabled) {
            this.state().enabled = enabled;
            if (enabled) {
               this.onEnable();
            } else {
               this.onDisable();
            }

            ModuleRegistry.markModuleEnabledChanged();
            this.save();
         }
      }
   }

   protected final void replaceNextToggleMessage(String message) {
      this.replacementToggleMessage = message;
   }

   protected final void disableSilentlyWithToggleMessage(String message) {
      this.replaceNextToggleMessage(message);
      this.setEnabledSilently(false);
   }

   protected final void disableWithToggleMessage(String message) {
      this.replaceNextToggleMessage(message);
      this.setEnabled(false);
   }

   public final void toggle() {
      this.setEnabled(!this.isEnabled());
   }

   protected int defaultKeybind() {
      return -1;
   }

   public final int keybind() {
      return this.state().keybind;
   }

   public final void setKeybind(int keybind) {
      this.state().keybind = keybind;
      ModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   public final List<Setting<?, ?>> settings() {
      return Collections.unmodifiableList(this.settings);
   }

   public final List<Setting<?, ?>> visibleSettings() {
      List<Setting<?, ?>> visible = new ArrayList<>();

      for (Setting<?, ?> setting : this.settings) {
         if (setting.isVisible()) {
            visible.add(setting);
         }
      }

      return visible;
   }

   protected final <T, S extends Setting<T, S>> S add(S setting) {
      if (setting == null) {
         throw new IllegalArgumentException("Module setting cannot be null");
      } else if (setting.id() == null || setting.id().isBlank()) {
         throw new IllegalArgumentException("Module setting id cannot be blank for " + this.id);
      } else if (this.setting(setting.id()) != null) {
         throw new IllegalStateException("Duplicate setting '" + setting.id() + "' in module '" + this.id + "'");
      } else {
         setting.attach(this);
         this.settings.add(setting);
         this.state().settings.putIfAbsent(setting.id(), setting.defaultValue());
         ModuleRegistry.markModuleSettingsChanged();
         return setting;
      }
   }

   public String info() {
      return "";
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void tick() {
   }

   public boolean ticksWhenDisabled() {
      return false;
   }

   public boolean hasDisabledTickWork() {
      return this.ticksWhenDisabled();
   }

   public boolean opensSettingsOnClick() {
      return false;
   }

   public boolean hasActivationToggle() {
      return true;
   }

   public boolean holdToActivate() {
      return false;
   }

   public boolean showInModuleMenu() {
      return true;
   }

   public boolean settingsShareable() {
      return true;
   }

   public boolean showInArrayList() {
      return true;
   }

   public boolean emitsToggleMessage() {
      return true;
   }

   public void preMovementTick() {
   }

   public void onNetworkMovementTickPre() {
   }

   public void onPacketProcessFrame() {
   }

   public boolean shouldCancelPlayerTick() {
      return false;
   }

   public void onRenderLevel(float partialTick) {
   }

   public void onMouseRotation(double deltaYaw, double deltaPitch) {
   }

   public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
      return movement;
   }

   public boolean shouldApplySpeedTimer() {
      return false;
   }

   public float speedTimerMultiplier() {
      return 1.0F;
   }

   public void onGameJoin() {
   }

   public void onGameLeft() {
   }

   public boolean onPacketSend(Packet<?> packet) {
      return false;
   }

   public boolean onPacketReceive(Packet<?> packet) {
      return false;
   }

   public void onSoundPacket(ClientboundSoundPacket packet) {
   }

   public void appendTooltip(ItemStack stack, List<?> lines) {
   }

   public boolean shouldCancelAttack(HitResult hitResult) {
      return false;
   }

   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return false;
   }

   public void onStartBreakingBlock(BlockPos pos, Direction direction) {
   }

   public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
      return false;
   }

   public void onBlockBreakingProgress(BlockPos pos, Direction direction) {
   }

   public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
      return false;
   }

   public boolean shouldTraceEntity(Entity entity) {
      return false;
   }

   public int traceColor(Entity entity) {
      return -2130706433;
   }

   protected final boolean bool(String settingId) {
      return Boolean.parseBoolean(this.value(settingId));
   }

   protected final int integer(String settingId) {
      try {
         return Integer.parseInt(this.value(settingId));
      } catch (NumberFormatException var6) {
         Setting<?, ?> setting = this.setting(settingId);

         try {
            return setting == null ? 0 : Integer.parseInt(setting.defaultValue());
         } catch (NumberFormatException var5) {
            return 0;
         }
      }
   }

   protected final double decimal(String settingId) {
      try {
         return Double.parseDouble(this.value(settingId));
      } catch (NumberFormatException var6) {
         Setting<?, ?> setting = this.setting(settingId);

         try {
            return setting == null ? 0.0 : Double.parseDouble(setting.defaultValue());
         } catch (NumberFormatException var5) {
            return 0.0;
         }
      }
   }

   protected final String text(String settingId) {
      return this.value(settingId);
   }

   protected final String choice(String settingId) {
      return this.value(settingId);
   }

   protected final List<String> list(String settingId) {
      String value = this.value(settingId);
      if (value != null && !value.isBlank()) {
         List<String> out = new ArrayList<>();

         for (String raw : value.split("\\|")) {
            String item = raw.trim();
            if (!item.isEmpty()) {
               out.add(item);
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   public final String value(String settingId) {
      String external = this.externalSettingValue(settingId);
      if (external != null) {
         return external;
      } else {
         Setting<?, ?> setting = this.setting(settingId);
         String fallback = setting == null ? "" : setting.defaultValue();
         return this.state().settings.getOrDefault(settingId, fallback);
      }
   }

   @Override
   public final String settingValue(String name) {
      String external = this.externalSettingValue(name);
      return external != null ? external : this.state().settings.get(name);
   }

   @Override
   public final void putSettingValue(String name, String value) {
      this.setValue(name, value);
   }

   public final void setValue(String settingId, String value) {
      this.setValueTransient(settingId, value);
      this.save();
   }

   public final void setValueTransient(String settingId, String value) {
      String sanitized = sanitize(this.setting(settingId), value);
      if (!this.setExternalSettingValue(settingId, sanitized)) {
         this.state().settings.put(settingId, sanitized);
      }

      this.onOptionValueChanged(settingId);
      ModuleRegistry.markModuleSettingsChanged();
   }

   public final void setConfiguredValue(String settingId, String value) {
      this.setConfiguredValueTransient(settingId, value);
      this.save();
   }

   public final void setConfiguredValueTransient(String settingId, String value) {
      String sanitized = sanitize(this.setting(settingId), value);
      if (!this.setExternalSettingValue(settingId, sanitized)) {
         this.state().settings.put(settingId, sanitized);
      }

      ModuleRegistry.recordOfflineConfiguredOption(this, settingId, false);
      ModuleRegistry.markModuleSettingsChanged();
   }

   public final void persistConfiguredState() {
      this.save();
   }

   public final void resetValue(String settingId) {
      Setting<?, ?> setting = this.setting(settingId);
      if (setting != null) {
         if (!this.setExternalSettingValue(settingId, setting.defaultValue())) {
            this.state().settings.put(settingId, setting.defaultValue());
         }

         this.onOptionValueChanged(settingId);
         ModuleRegistry.markModuleSettingsChanged();
         this.save();
      }
   }

   public final void resetConfiguredValue(String settingId) {
      Setting<?, ?> setting = this.setting(settingId);
      if (setting != null) {
         if (!this.setExternalSettingValue(settingId, setting.defaultValue())) {
            this.state().settings.put(settingId, setting.defaultValue());
         }

         ModuleRegistry.recordOfflineConfiguredOption(this, settingId, false);
         ModuleRegistry.markModuleSettingsChanged();
         this.save();
      }
   }

   public final void resetSettings() {
      for (Setting<?, ?> setting : this.settings) {
         if (!setting.isKeptOnReset() && !this.setExternalSettingValue(setting.id(), setting.defaultValue())) {
            this.state().settings.put(setting.id(), setting.defaultValue());
         }
      }

      this.onSettingsReset();
      ModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   public final void resetConfiguredSettings() {
      for (Setting<?, ?> setting : this.settings) {
         if (!setting.isKeptOnReset() && !this.setExternalSettingValue(setting.id(), setting.defaultValue())) {
            this.state().settings.put(setting.id(), setting.defaultValue());
         }
      }

      ModuleRegistry.recordOfflineConfiguredOption(this, "", true);
      ModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   final void applyConfiguredSettings(Set<String> settingIds, boolean reset) {
      if (reset) {
         this.onSettingsReset();
      }

      if (settingIds != null) {
         for (String settingId : settingIds) {
            if (settingId != null && !settingId.isBlank()) {
               this.onOptionValueChanged(settingId);
            }
         }
      }
   }

   protected void onSettingsReset() {
   }

   protected void onOptionValueChanged(String settingId) {
   }

   protected String externalSettingValue(String settingId) {
      return null;
   }

   protected boolean setExternalSettingValue(String settingId, String value) {
      return false;
   }

   public final void adjustOption(Setting<?, ?> setting, int direction) {
      if (setting != null) {
         switch (setting.kind()) {
            case BOOLEAN:
               this.setValue(setting.id(), Boolean.toString(!this.bool(setting.id())));
               break;
            case INTEGER: {
               int value = this.integer(setting.id());
               int adjusted = (int)clamp(value + (int)setting.step() * direction, setting.min(), setting.max());
               this.setValue(setting.id(), Integer.toString(adjusted));
               break;
            }
            case DOUBLE: {
               double value = this.decimal(setting.id());
               double adjusted = clamp(value + setting.step() * direction, setting.min(), setting.max());
               this.setValue(setting.id(), String.format(Locale.ROOT, "%.2f", adjusted));
               break;
            }
            case ENUM:
               List<String> choices = setting.choices();
               if (!choices.isEmpty()) {
                  int index = choices.indexOf(this.value(setting.id()));
                  if (index < 0) {
                     index = 0;
                  }

                  int next = Math.floorMod(index + direction, choices.size());
                  this.setValue(setting.id(), choices.get(next));
               }
               break;
            case ACTION:
               if (setting.action() != null) {
                  setting.action().run();
               }
         }
      }
   }

   public final void adjustConfiguredOption(Setting<?, ?> setting, int direction) {
      if (setting != null) {
         switch (setting.kind()) {
            case BOOLEAN:
               this.setConfiguredValue(setting.id(), Boolean.toString(!this.bool(setting.id())));
               break;
            case INTEGER: {
               int adjusted = (int)clamp(this.integer(setting.id()) + (int)setting.step() * direction, setting.min(), setting.max());
               this.setConfiguredValue(setting.id(), Integer.toString(adjusted));
               break;
            }
            case DOUBLE: {
               double adjusted = clamp(this.decimal(setting.id()) + setting.step() * direction, setting.min(), setting.max());
               this.setConfiguredValue(setting.id(), String.format(Locale.ROOT, "%.2f", adjusted));
               break;
            }
            case ENUM:
               List<String> choices = setting.choices();
               if (!choices.isEmpty()) {
                  int index = Math.max(0, choices.indexOf(this.value(setting.id())));
                  this.setConfiguredValue(setting.id(), choices.get(Math.floorMod(index + direction, choices.size())));
               }
               break;
            case ACTION:
               if (setting.isAvailable(false, false) && setting.action() != null) {
                  setting.action().run();
               }
         }
      }
   }

   public final String displayValue(Setting<?, ?> setting) {
      if (setting == null) {
         return "";
      } else {
         String override = this.displayValueOverride(setting);
         if (override != null) {
            return override;
         } else {
            return switch (setting.kind()) {
               case BOOLEAN -> this.bool(setting.id()) ? "ON" : "OFF";
               case ACTION -> "RUN";
               default -> setting.format(this.value(setting.id()));
            };
         }
      }
   }

   protected String displayValueOverride(Setting<?, ?> setting) {
      return null;
   }

   private static String sanitize(Setting<?, ?> setting, String value) {
      if (setting == null) {
         return value == null ? "" : value;
      } else {
         return setting.sanitizeUiString(value);
      }
   }

   public final Setting<?, ?> setting(String settingId) {
      for (Setting<?, ?> setting : this.settings) {
         if (setting.id().equals(settingId)) {
            return setting;
         }
      }

      return null;
   }

   protected final boolean isAdminContext() {
      return MC != null && MC.player != null && MC.player.canUseGameMasterBlocks();
   }

   protected final boolean isCreativeContext() {
      return MC != null && MC.player != null && MC.player.hasInfiniteMaterials();
   }

   protected final void sendCommand(String command) {
      if (!PackHideState.isHardLocked()) {
         if (MC != null && MC.getConnection() != null && command != null && !command.isBlank()) {
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            MC.getConnection().sendCommand(normalized);
         }
      }
   }

   private RiptideConfig.ModuleState state() {
      RiptideConfig config = RiptideConfig.getGlobal();
      return config.modules.computeIfAbsent(this.id, ignored -> {
         RiptideConfig.ModuleState created = new RiptideConfig.ModuleState();
         created.keybind = this.defaultKeybind();
         return created;
      });
   }

   private void save() {
      RiptideConfig.getGlobal().save();
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
