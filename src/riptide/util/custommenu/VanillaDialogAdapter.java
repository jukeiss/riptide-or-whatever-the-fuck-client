package riptide.util.custommenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent.CopyToClipboard;
import net.minecraft.network.chat.ClickEvent.Custom;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.ClickEvent.ShowDialog;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ButtonListDialog;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.action.Action.ValueGetter;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.dialog.input.NumberRangeInput.RangeInfo;
import net.minecraft.server.dialog.input.SingleOptionInput.Entry;
import riptide.api.custommenu.CustomMenuAdapter;
import riptide.api.custommenu.CustomMenuButton;
import riptide.api.custommenu.CustomMenuEvent;
import riptide.api.custommenu.CustomMenuInput;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;
import riptide.api.custommenu.CustomMenuSubmitResult;

public final class VanillaDialogAdapter implements CustomMenuAdapter {
   public static final String ID = "minecraft:dialog";

   @Override
   public String id() {
      return "minecraft:dialog";
   }

   @Override
   public boolean acceptsInbound(Packet<?> packet) {
      return packet instanceof ClientboundShowDialogPacket || packet instanceof ClientboundClearDialogPacket;
   }

   @Override
   public CustomMenuEvent inspectInbound(Packet<?> packet, String phase) {
      if (packet instanceof ClientboundClearDialogPacket) {
         return CustomMenuEvent.CLEAR;
      } else {
         return packet instanceof ClientboundShowDialogPacket show && show.dialog() != null
            ? CustomMenuEvent.open(snapshot((Dialog)show.dialog().value(), phase))
            : CustomMenuEvent.NONE;
      }
   }

   public static CustomMenuSnapshot snapshotOf(Dialog dialog, String phase) {
      return dialog == null ? null : snapshot(dialog, phase);
   }

   private static CustomMenuSnapshot snapshot(Dialog dialog, String phase) {
      List<CustomMenuInput> inputs = new ArrayList<>();
      int inputIndex = 1;

      for (Input input : dialog.common().inputs()) {
         inputs.add(input(inputIndex++, input));
      }

      List<ActionButton> actions = actions(dialog);
      List<CustomMenuButton> buttons = new ArrayList<>();

      for (int i = 0; i < actions.size(); i++) {
         buttons.add(button(i + 1, actions.get(i)));
      }

      return new CustomMenuSnapshot(
         "minecraft:dialog", phase, 0L, dialog.common().title().getString(), inputs, buttons, new VanillaDialogAdapter.State(dialog, actions)
      );
   }

   private static CustomMenuInput input(int index, Input input) {
      String key = input.key();
      if (input.control() instanceof TextInput text) {
         return new CustomMenuInput(index, key, text.label().getString(), CustomMenuInput.Kind.TEXT, text.initial(), text.maxLength(), 0.0, 0.0, 0.0, List.of());
      } else if (input.control() instanceof BooleanInput bool) {
         return new CustomMenuInput(
            index,
            key,
            bool.label().getString(),
            CustomMenuInput.Kind.BOOLEAN,
            Boolean.toString(bool.initial()),
            0,
            0.0,
            0.0,
            0.0,
            List.of(bool.onFalse(), bool.onTrue())
         );
      } else if (input.control() instanceof NumberRangeInput number) {
         RangeInfo range = number.rangeInfo();
         float initial = range.initial().orElse((range.start() + range.end()) / 2.0F);
         return new CustomMenuInput(
            index,
            key,
            number.label().getString(),
            CustomMenuInput.Kind.NUMBER,
            numberString(initial),
            0,
            Math.min(range.start(), range.end()),
            Math.max(range.start(), range.end()),
            range.step().orElse(0.0F).floatValue(),
            List.of()
         );
      } else if (input.control() instanceof SingleOptionInput option) {
         String initial = ((Entry)option.initial().orElse((Entry)option.entries().getFirst())).id();
         return new CustomMenuInput(
            index,
            key,
            option.label().getString(),
            CustomMenuInput.Kind.OPTION,
            initial,
            0,
            0.0,
            0.0,
            0.0,
            option.entries().stream().<String>map(Entry::id).toList()
         );
      } else {
         return new CustomMenuInput(index, key, "", CustomMenuInput.Kind.TEXT, "", 0, 0.0, 0.0, 0.0, List.of());
      }
   }

   private static List<ActionButton> actions(Dialog dialog) {
      List<ActionButton> result = new ArrayList<>();
      if (dialog instanceof SimpleDialog simple) {
         result.addAll(simple.mainActions());
      } else if (dialog instanceof MultiActionDialog multi) {
         result.addAll(multi.actions());
         multi.exitAction().ifPresent(result::add);
      } else if (dialog instanceof DialogListDialog list) {
         list.dialogs()
            .stream()
            .forEach(
               holder -> result.add(
                  new ActionButton(
                     new CommonButtonData(((Dialog)holder.value()).common().computeExternalTitle(), list.buttonWidth()),
                     Optional.of(new StaticAction(new ShowDialog(holder)))
                  )
               )
            );
         list.exitAction().ifPresent(result::add);
      } else if (dialog instanceof ButtonListDialog list) {
         list.exitAction().ifPresent(result::add);
      }

      return List.copyOf(result);
   }

   private static CustomMenuButton button(int index, ActionButton button) {
      String label = button.button().label().getString();
      String color = labelColor(button.button().label());
      if (button.action().isEmpty()) {
         return new CustomMenuButton(index, label, "", CustomMenuButton.Kind.EMPTY, color);
      } else {
         Action action = (Action)button.action().get();
         String actionId = action instanceof CustomAll custom ? custom.id().toString() : "";
         CustomMenuButton.Kind kind = CustomMenuButton.Kind.OTHER;
         if (action instanceof CustomAll) {
            kind = CustomMenuButton.Kind.CUSTOM;
         } else if (action instanceof CommandTemplate) {
            kind = CustomMenuButton.Kind.COMMAND;
         } else if (action instanceof StaticAction stat) {
            ClickEvent click = stat.value();
            if (click instanceof Custom customx) {
               kind = CustomMenuButton.Kind.CUSTOM;
               actionId = customx.id().toString();
            } else if (click instanceof RunCommand) {
               kind = CustomMenuButton.Kind.COMMAND;
            } else if (click instanceof ShowDialog) {
               kind = CustomMenuButton.Kind.DIALOG;
            } else if (click instanceof OpenUrl) {
               kind = CustomMenuButton.Kind.URL;
            } else if (click instanceof CopyToClipboard) {
               kind = CustomMenuButton.Kind.CLIPBOARD;
            }
         }

         return new CustomMenuButton(index, label, actionId, kind, color);
      }
   }

   private static String labelColor(Component label) {
      if (label == null) {
         return "";
      } else {
         Style style = label.getStyle();
         TextColor color = style == null ? null : style.getColor();
         if (color != null) {
            return color.serialize().toLowerCase(Locale.ROOT);
         } else {
            for (Component sibling : label.getSiblings()) {
               String nested = labelColor(sibling);
               if (!nested.isEmpty()) {
                  return nested;
               }
            }

            return "";
         }
      }
   }

   @Override
   public CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
      if (!(snapshot.adapterState() instanceof VanillaDialogAdapter.State state)) {
         return CustomMenuSubmitResult.failure("Invalid dialog state");
      } else if (submission != null && submission.button() != null) {
         int index = submission.button().index() - 1;
         if (index >= 0 && index < state.actions().size()) {
            ActionButton selected = state.actions().get(index);
            if (selected.action().isEmpty()) {
               return CustomMenuSubmitResult.failure("Dialog button has no action");
            } else {
               Map<String, ValueGetter> getters = new LinkedHashMap<>();

               for (Input input : state.dialog().common().inputs()) {
                  String raw = submission.values().get(input.key());
                  getters.put(input.key(), valueGetter(input, raw));
               }

               Optional<ClickEvent> click = ((Action)selected.action().get()).createAction(getters);
               if (click.isEmpty()) {
                  return CustomMenuSubmitResult.failure("Dialog action produced no response");
               } else if (click.get() instanceof Custom custom) {
                  return CustomMenuSubmitResult.packets(List.of(new ServerboundCustomClickActionPacket(custom.id(), custom.payload())));
               } else if (click.get() instanceof RunCommand command) {
                  if (!"PLAY".equalsIgnoreCase(snapshot.phase())) {
                     return CustomMenuSubmitResult.failure("Commands cannot be sent during configuration");
                  } else {
                     String value = command.command();
                     if (value.startsWith("/")) {
                        value = value.substring(1);
                     }

                     return CustomMenuSubmitResult.packets(List.of(new ServerboundChatCommandPacket(value)));
                  }
               } else {
                  return click.get() instanceof ShowDialog nested
                     ? CustomMenuSubmitResult.replacement(snapshot((Dialog)nested.dialog().value(), snapshot.phase()), click.get())
                     : CustomMenuSubmitResult.failure("Dialog action is not safe for automation");
               }
            }
         } else {
            return CustomMenuSubmitResult.failure("Dialog button is unavailable");
         }
      } else {
         return CustomMenuSubmitResult.failure("No dialog button selected");
      }
   }

   private static ValueGetter valueGetter(Input input, String supplied) {
      if (input.control() instanceof TextInput text) {
         String value = supplied == null ? text.initial() : supplied;
         if (value.length() > text.maxLength()) {
            throw new IllegalArgumentException("Input '" + input.key() + "' exceeds max length");
         } else {
            return getter(StringTag.escapeWithoutQuotes(value), StringTag.valueOf(value));
         }
      } else if (input.control() instanceof BooleanInput bool) {
         boolean value = supplied == null ? bool.initial() : parseBoolean(input.key(), supplied);
         return getter(value ? bool.onTrue() : bool.onFalse(), ByteTag.valueOf(value));
      } else if (input.control() instanceof NumberRangeInput number) {
         RangeInfo range = number.rangeInfo();
         float value = supplied == null ? range.initial().orElse((range.start() + range.end()) / 2.0F) : parseFloat(input.key(), supplied);
         float min = Math.min(range.start(), range.end());
         float max = Math.max(range.start(), range.end());
         if (!(value < min) && !(value > max)) {
            if (range.step().isPresent()) {
               float origin = range.initial().orElse(range.start());
               float quotient = (value - origin) / (Float)range.step().get();
               if (Math.abs(quotient - Math.round(quotient)) > 1.0E-4F) {
                  throw new IllegalArgumentException("Input '" + input.key() + "' does not match its step");
               }
            }

            return getter(numberString(value), FloatTag.valueOf(value));
         } else {
            throw new IllegalArgumentException("Input '" + input.key() + "' is outside its range");
         }
      } else if (input.control() instanceof SingleOptionInput option) {
         String value = supplied == null ? ((Entry)option.initial().orElse((Entry)option.entries().getFirst())).id() : supplied;
         if (option.entries().stream().noneMatch(entry -> entry.id().equals(value))) {
            throw new IllegalArgumentException("Input '" + input.key() + "' is not a valid option");
         } else {
            return getter(value, StringTag.valueOf(value));
         }
      } else {
         throw new IllegalArgumentException("Unsupported input '" + input.key() + "'");
      }
   }

   private static ValueGetter getter(final String text, final Tag tag) {
      return new ValueGetter() {
         public String asTemplateSubstitution() {
            return text;
         }

         public Tag asTag() {
            return tag;
         }
      };
   }

   private static boolean parseBoolean(String key, String raw) {
      String var2 = raw.trim().toLowerCase(Locale.ROOT);

      return switch (var2) {
         case "true", "1", "yes", "on" -> true;
         case "false", "0", "no", "off" -> false;
         default -> throw new IllegalArgumentException("Input '" + key + "' is not a boolean");
      };
   }

   private static float parseFloat(String key, String raw) {
      try {
         return Float.parseFloat(raw.trim());
      } catch (NumberFormatException var3) {
         throw new IllegalArgumentException("Input '" + key + "' is not a number");
      }
   }

   private static String numberString(float value) {
      int integer = (int)value;
      return integer == value ? Integer.toString(integer) : Float.toString(value);
   }

   private record State(Dialog dialog, List<ActionButton> actions) {
   }
}
