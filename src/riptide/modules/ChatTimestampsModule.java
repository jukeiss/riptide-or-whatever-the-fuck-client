package riptide.modules;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;

public final class ChatTimestampsModule extends Module {
   private static final String SHORT = "13:45";
   private static final String LONG = "13:45:02";
   private static final String TWELVE = "1:45 PM";
   private static volatile boolean active;
   private static volatile DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
   private static volatile int colour = -7697782;

   public ChatTimestampsModule() {
      super("chat-timestamps", "ChatTimestamps", ModuleCategory.MISC, "Shows the time next to each chat message, on your screen only.");
      this.add(new ChoiceSetting("format", "Format", "13:45", "13:45", "13:45:02", "1:45 PM").description("How to write the time.").group("General").build());
      this.add(new ColorSetting("color", "Colour", -7697782).description("Colour of the timestamp. Chat itself is left alone.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      active = false;
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      this.refresh();
   }

   @Override
   protected void onSettingsReset() {
      this.refresh();
   }

   private void refresh() {
      active = this.isEnabled();
      colour = ModuleRenderUtil.color(this, "color", -7697782);
      formatter = formatterFor(this.choice("format"));
   }

   static DateTimeFormatter formatterFor(String var0) {
      return switch (var0) {
         case "13:45:02" -> DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
         case "1:45 PM" -> DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
         default -> DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
      };
   }

   public static Component decorate(Component var0) {
      if (active && var0 != null) {
         try {
            MutableComponent var1 = Component.literal(LocalTime.now().format(formatter) + " ")
               .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(colour & 16777215)));
            return var1.append(var0);
         } catch (Throwable var2) {
            return var0;
         }
      } else {
         return var0;
      }
   }
}
