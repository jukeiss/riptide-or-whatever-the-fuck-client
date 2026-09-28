package riptide.api.module;

import java.util.Locale;
import java.util.function.Consumer;
import riptide.api.RiptideAddons;
import riptide.modules.Module;

public class SimpleModule extends Module {
   private Consumer<SimpleModule> enableHandler;
   private Consumer<SimpleModule> disableHandler;
   private Consumer<SimpleModule> tickHandler;

   public SimpleModule(String localId, String name, String description) {
      super(RiptideAddons.id(localId), name, description);
   }

   public final SimpleModule onEnabled(Consumer<SimpleModule> handler) {
      this.enableHandler = handler;
      return this;
   }

   public final SimpleModule onDisabled(Consumer<SimpleModule> handler) {
      this.disableHandler = handler;
      return this;
   }

   public final SimpleModule onTick(Consumer<SimpleModule> handler) {
      this.tickHandler = handler;
      return this;
   }

   public final SimpleModule addBool(String id, String label, boolean defaultValue) {
      this.add(new BoolSetting(id, label, defaultValue));
      return this;
   }

   public final SimpleModule addInt(String id, String label, int defaultValue, int min, int max, int step) {
      this.add(new IntSetting(id, label, defaultValue, min, max, step));
      return this;
   }

   public final SimpleModule addDouble(String id, String label, double defaultValue, double min, double max, double step) {
      this.add(new DoubleSetting(id, label, defaultValue, min, max, step));
      return this;
   }

   public final SimpleModule addChoice(String id, String label, String defaultValue, String... choices) {
      this.add(new ChoiceSetting(id, label, defaultValue, choices == null ? new String[0] : choices));
      return this;
   }

   public final SimpleModule addText(String id, String label, String defaultValue) {
      this.add(new StringSetting(id, label, defaultValue));
      return this;
   }

   public final SimpleModule addColor(String id, String label, int defaultArgb) {
      this.add(new ColorSetting(id, label, defaultArgb));
      return this;
   }

   public final boolean getBool(String id) {
      return this.bool(id);
   }

   public final int getInt(String id) {
      return this.integer(id);
   }

   public final double getDouble(String id) {
      return this.decimal(id);
   }

   public final String getText(String id) {
      return this.text(id);
   }

   public final String getChoice(String id) {
      return this.choice(id);
   }

   @Override
   public void onEnable() {
      if (this.enableHandler != null) {
         this.enableHandler.accept(this);
      }
   }

   @Override
   public void onDisable() {
      if (this.disableHandler != null) {
         this.disableHandler.accept(this);
      }
   }

   @Override
   public void tick() {
      if (this.tickHandler != null) {
         this.tickHandler.accept(this);
      }
   }

   protected final String upper(String value) {
      return value == null ? "" : value.toUpperCase(Locale.ROOT);
   }
}
