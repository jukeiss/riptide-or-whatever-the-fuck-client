package riptide.api.module;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public abstract class Setting<T, S extends Setting<T, S>> {
   private final Kind kind;
   private final String name;
   private final T def;
   private String title;
   private String description = "";
   private String group = "General";
   private String unit = "";
   private DisplayMode displayMode = DisplayMode.DEFAULT;
   private Availability availability;
   private BooleanSupplier visible;
   private Function<String, String> formatter;
   private Runnable action;
   private String linkedActionId = "";
   private boolean keepOnReset;
   private double min;
   private double max;
   private double sliderMin;
   private double sliderMax;
   private double step = 1.0;
   private List<String> choices = List.of();
   private SettingOwner owner;
   private transient boolean cached;
   private transient String cachedRaw;
   private transient T cachedValue;

   protected Setting(Kind kind, String name, String title, T def) {
      this.kind = kind == null ? Kind.STRING : kind;
      this.name = name == null ? "" : name;
      this.title = title != null && !title.isBlank() ? title : this.name;
      this.def = def;
   }

   protected final S self() {
      return (S)this;
   }

   protected abstract T decode(String var1);

   protected abstract String encode(T var1);

   protected T sanitizeTyped(T value) {
      return value == null ? this.def : value;
   }

   public final void attach(SettingOwner owner) {
      this.owner = owner;
      this.cached = false;
   }

   private String stored() {
      if (this.owner == null) {
         return this.defaultValue();
      } else {
         String raw = this.owner.settingValue(this.name);
         return raw == null ? this.defaultValue() : raw;
      }
   }

   public final T get() {
      String raw = this.stored();
      if (this.cached && Objects.equals(raw, this.cachedRaw)) {
         return this.cachedValue;
      } else {
         T value = this.decode(raw);
         this.cachedRaw = raw;
         this.cachedValue = value;
         this.cached = true;
         return value;
      }
   }

   public final void set(T value) {
      if (this.owner != null) {
         this.owner.putSettingValue(this.name, this.encode(this.sanitizeTyped(value)));
      }
   }

   public final T defaultValueTyped() {
      return this.def;
   }

   public final S description(String description) {
      this.description = description == null ? "" : description;
      return this.self();
   }

   public final S group(String group) {
      this.group = group != null && !group.isBlank() ? group : "General";
      return this.self();
   }

   public final S unit(String unit) {
      this.unit = unit == null ? "" : unit;
      return this.self();
   }

   public final S visibleWhen(BooleanSupplier visible) {
      this.visible = visible;
      return this.self();
   }

   public final S formatter(Function<String, String> formatter) {
      this.formatter = formatter;
      return this.self();
   }

   public final S displayMode(DisplayMode displayMode) {
      this.displayMode = displayMode == null ? DisplayMode.DEFAULT : displayMode;
      return this.self();
   }

   public final S readonlySummary() {
      return this.displayMode(DisplayMode.READONLY_SUMMARY);
   }

   public final S numericTextField() {
      return this.displayMode(DisplayMode.NUMERIC_TEXT_FIELD);
   }

   public final S playerNameList() {
      return this.displayMode(DisplayMode.PLAYER_NAME_LIST);
   }

   public final S rankTagList() {
      return this.displayMode(DisplayMode.RANK_TAG_LIST);
   }

   public final S playerRankPicker() {
      return this.displayMode(DisplayMode.PLAYER_RANK_PICKER);
   }

   public final S macroPicker() {
      return this.displayMode(DisplayMode.MACRO_PICKER);
   }

   public final S conditionalMacroPicker() {
      return this.displayMode(DisplayMode.CONDITIONAL_MACRO_PICKER);
   }

   public final S filePicker(String linkedActionId) {
      this.linkedActionId = linkedActionId == null ? "" : linkedActionId;
      return this.displayMode(DisplayMode.FILE_PICKER);
   }

   public final S linkedActionId(String linkedActionId) {
      this.linkedActionId = linkedActionId == null ? "" : linkedActionId;
      return this.self();
   }

   public final S availableOffline() {
      this.availability = Availability.ALWAYS;
      return this.self();
   }

   public final S requiresWorld() {
      this.availability = Availability.IN_WORLD;
      return this.self();
   }

   public final S requiresContainer() {
      this.availability = Availability.IN_CONTAINER;
      return this.self();
   }

   public final S keepOnReset() {
      this.keepOnReset = true;
      return this.self();
   }

   public final boolean isKeptOnReset() {
      return this.keepOnReset;
   }

   public final S build() {
      return this.self();
   }

   protected final void setRange(double min, double max) {
      this.min = min;
      this.max = Math.max(min, max);
   }

   protected final void setSliderRange(double min, double max) {
      this.sliderMin = min;
      this.sliderMax = Math.max(min, max);
   }

   protected final void setStep(double step) {
      this.step = step <= 0.0 ? 1.0 : step;
   }

   protected final void setChoices(List<String> choices) {
      this.choices = choices == null ? List.of() : List.copyOf(choices);
   }

   protected final void setAction(Runnable action) {
      this.action = action;
   }

   public final String id() {
      return this.name;
   }

   public final String label() {
      return this.title;
   }

   public final String description() {
      return this.description;
   }

   public final String group() {
      return this.group;
   }

   public boolean isListSelection() {
      return false;
   }

   public final boolean isListModeSelector() {
      List<String> options = this.choices();
      if (options.size() < 2) {
         return false;
      } else {
         boolean whitelist = false;
         boolean blacklist = false;

         for (String option : options) {
            if ("whitelist".equalsIgnoreCase(option)) {
               whitelist = true;
            } else if ("blacklist".equalsIgnoreCase(option)) {
               blacklist = true;
            }
         }

         return whitelist && blacklist;
      }
   }

   public final Kind kind() {
      return this.kind;
   }

   public final DisplayMode displayMode() {
      return this.displayMode;
   }

   public final Availability availability() {
      return this.availability == null ? (this.kind == Kind.ACTION ? Availability.IN_WORLD : Availability.ALWAYS) : this.availability;
   }

   public final String unit() {
      return this.unit;
   }

   public final List<String> choices() {
      return this.choices;
   }

   public final double min() {
      return this.min;
   }

   public final double max() {
      return this.max;
   }

   public final double sliderMin() {
      return this.sliderMin;
   }

   public final double sliderMax() {
      return this.sliderMax;
   }

   public final double step() {
      return this.step;
   }

   public final Runnable action() {
      return this.action;
   }

   public final String linkedActionId() {
      return this.linkedActionId;
   }

   public final String defaultValue() {
      return this.encode(this.def);
   }

   public final String serialize() {
      return this.stored();
   }

   public final String displayString() {
      return this.format(this.serialize());
   }

   public final String format(String value) {
      if (this.formatter == null) {
         return value == null ? "" : value;
      } else {
         try {
            return this.formatter.apply(value);
         } catch (Throwable var3) {
            return value == null ? "" : value;
         }
      }
   }

   public final String sanitizeUiString(String raw) {
      return this.encode(this.decode(raw));
   }

   public final String deserialize(String raw) {
      return this.sanitizeUiString(raw);
   }

   public final boolean isVisible() {
      if (this.visible == null) {
         return true;
      } else {
         try {
            return this.visible.getAsBoolean();
         } catch (Throwable var2) {
            return true;
         }
      }
   }

   public final boolean isAvailable(boolean inWorld, boolean inContainer) {
      return switch (this.availability()) {
         case ALWAYS -> true;
         case IN_WORLD -> inWorld;
         case IN_CONTAINER -> inContainer;
      };
   }

   public final boolean isModified() {
      return !Objects.equals(this.serialize(), this.defaultValue());
   }
}
