package riptide.util.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import riptide.api.custommenu.CustomMenuButton;
import riptide.api.custommenu.CustomMenuInput;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;

public final class CustomMenuActionSupport {
   private static final List<String> POSITIVE = List.of(
      "login", "register", "submit", "continue", "confirm", "yes", "ok", "accept", "agree", "agreed", "done", "join", "enter"
   );
   private static final List<String> NEGATIVE = List.of("cancel", "quit", "back", "deny", "decline", "no", "exit", "disconnect", "close");
   private static final List<String> ACCEPT = List.of("accept", "agree", "agreed", "consent", "understood", "acknowledge", "confirm");

   private CustomMenuActionSupport() {
   }

   public static boolean titleMatches(CustomMenuAction action, String title) {
      return true;
   }

   public static String titleMatcherError(CustomMenuAction action) {
      return "";
   }

   public static CustomMenuActionSupport.Prepared prepare(CustomMenuAction action, CustomMenuSnapshot snapshot, Function<String, String> resolver) {
      if (action != null && snapshot != null) {
         Function<String, String> safeResolver = resolver == null ? Function.identity() : resolver;
         Map<String, String> values = new LinkedHashMap<>();

         for (CustomMenuInput input : snapshot.inputs()) {
            values.put(input.key(), input.initialValue());
         }

         List<CustomMenuInput> textInputs = new ArrayList<>();

         for (CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == CustomMenuInput.Kind.TEXT) {
               textInputs.add(input);
            }
         }

         if (!textInputs.isEmpty()) {
            List<String> fields = (List<String>)(action.fieldValues.isEmpty() ? List.of("{secret.password}") : action.fieldValues);
            List<String> resolved = new ArrayList<>(fields.size());

            for (int i = 0; i < fields.size(); i++) {
               String out;
               try {
                  out = safeResolver.apply(safe(fields.get(i)));
               } catch (RuntimeException var11) {
                  return CustomMenuActionSupport.Prepared.fail("Text field value " + (i + 1) + " is unavailable");
               }

               if (out == null) {
                  return CustomMenuActionSupport.Prepared.fail("Text field value " + (i + 1) + " is unavailable");
               }

               resolved.add(out);
            }

            if (resolved.size() == 1) {
               for (CustomMenuInput inputx : textInputs) {
                  values.put(inputx.key(), resolved.get(0));
               }
            } else {
               for (int i = 0; i < textInputs.size() && i < resolved.size(); i++) {
                  values.put(textInputs.get(i).key(), resolved.get(i));
               }
            }
         }

         CustomMenuButton button = selectButton(action, snapshot.buttons());
         return button == null
            ? CustomMenuActionSupport.Prepared.fail(
               safe(action.clickButton).isBlank()
                  ? "No obvious button to press; type the button's text (e.g. Register)"
                  : "No button matched \"" + action.clickButton + "\" on this screen"
            )
            : CustomMenuActionSupport.Prepared.ok(new CustomMenuSubmission(values, button));
      } else {
         return CustomMenuActionSupport.Prepared.fail("No custom screen is open");
      }
   }

   public static CustomMenuButton selectButton(CustomMenuAction action, List<CustomMenuButton> buttons) {
      if (buttons != null && !buttons.isEmpty()) {
         List<CustomMenuButton> usable = new ArrayList<>();

         for (CustomMenuButton button : buttons) {
            if (button.serverRelevant()) {
               usable.add(button);
            }
         }

         if (usable.isEmpty()) {
            return null;
         } else {
            String want = safe(action == null ? "" : action.clickButton).trim();
            if (want.isEmpty()) {
               return autoButton(usable);
            } else {
               if (want.startsWith("#")) {
                  try {
                     int index = Integer.parseInt(want.substring(1).trim());

                     for (CustomMenuButton buttonx : usable) {
                        if (buttonx.index() == index) {
                           return buttonx;
                        }
                     }
                  } catch (NumberFormatException var7) {
                  }
               }

               String lower = want.toLowerCase(Locale.ROOT);

               for (CustomMenuButton buttonxx : usable) {
                  if (buttonxx.label().equalsIgnoreCase(want)) {
                     return buttonxx;
                  }
               }

               for (CustomMenuButton buttonxxx : usable) {
                  if (buttonxxx.label().toLowerCase(Locale.ROOT).contains(lower)) {
                     return buttonxxx;
                  }
               }

               for (CustomMenuButton buttonxxxx : usable) {
                  if (!buttonxxxx.actionId().isBlank() && buttonxxxx.actionId().toLowerCase(Locale.ROOT).contains(lower)) {
                     return buttonxxxx;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static CustomMenuButton acceptButton(List<CustomMenuButton> buttons) {
      if (buttons == null) {
         return null;
      } else {
         for (CustomMenuButton button : buttons) {
            if (button.serverRelevant() && (containsWord(button.label(), ACCEPT) || containsWord(button.actionId(), ACCEPT))) {
               return button;
            }
         }

         return null;
      }
   }

   public static CustomMenuButton autoSubmitButton(List<CustomMenuButton> buttons) {
      if (buttons == null) {
         return null;
      } else {
         List<CustomMenuButton> usable = new ArrayList<>();

         for (CustomMenuButton button : buttons) {
            if (button.serverRelevant()) {
               usable.add(button);
            }
         }

         return usable.isEmpty() ? null : autoButton(usable);
      }
   }

   private static CustomMenuButton autoButton(List<CustomMenuButton> usable) {
      List<CustomMenuButton> nonNegative = new ArrayList<>();

      for (CustomMenuButton button : usable) {
         if (!containsWord(button.label(), NEGATIVE) && !containsWord(button.actionId(), NEGATIVE)) {
            nonNegative.add(button);
         }
      }

      if (nonNegative.size() == 1) {
         return nonNegative.get(0);
      } else {
         List<CustomMenuButton> positive = new ArrayList<>();

         for (CustomMenuButton buttonx : nonNegative) {
            if (containsWord(buttonx.label(), POSITIVE) || containsWord(buttonx.actionId(), POSITIVE)) {
               positive.add(buttonx);
            }
         }

         return positive.size() == 1 ? positive.get(0) : null;
      }
   }

   public static CustomMenuButton loginButton(List<CustomMenuButton> buttons) {
      if (buttons == null) {
         return null;
      } else {
         List<CustomMenuButton> usable = new ArrayList<>();

         for (CustomMenuButton button : buttons) {
            if (button.serverRelevant()) {
               usable.add(button);
            }
         }

         if (usable.isEmpty()) {
            return null;
         } else {
            List<CustomMenuButton> nonNegative = new ArrayList<>();

            for (CustomMenuButton buttonx : usable) {
               if (!containsWord(buttonx.label(), NEGATIVE) && !containsWord(buttonx.actionId(), NEGATIVE)) {
                  nonNegative.add(buttonx);
               }
            }

            for (CustomMenuButton buttonxx : nonNegative) {
               if (isGreen(buttonxx.labelColor())) {
                  return buttonxx;
               }
            }

            for (CustomMenuButton buttonxxx : nonNegative) {
               if (containsWord(buttonxxx.label(), POSITIVE) || containsWord(buttonxxx.actionId(), POSITIVE)) {
                  return buttonxxx;
               }
            }

            return !nonNegative.isEmpty() ? nonNegative.get(0) : usable.get(0);
         }
      }
   }

   static boolean isGreen(String serializedColor) {
      String value = safe(serializedColor).trim().toLowerCase(Locale.ROOT);
      if (value.isEmpty()) {
         return false;
      } else if (!value.equals("green") && !value.equals("dark_green")) {
         if (value.length() == 7 && value.charAt(0) == '#') {
            int rgb;
            try {
               rgb = Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException var6) {
               return false;
            }

            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            return g >= 60 && g > r + 30 && g > b + 30;
         } else {
            return false;
         }
      } else {
         return true;
      }
   }

   private static boolean containsWord(String value, List<String> words) {
      String[] tokens = safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+");

      for (String token : tokens) {
         for (String word : words) {
            if (token.equals(word)) {
               return true;
            }
         }
      }

      return false;
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }

   public record Prepared(CustomMenuSubmission submission, String error) {
      public boolean success() {
         return this.submission != null && this.error.isEmpty();
      }

      static CustomMenuActionSupport.Prepared ok(CustomMenuSubmission submission) {
         return new CustomMenuActionSupport.Prepared(submission, "");
      }

      static CustomMenuActionSupport.Prepared fail(String error) {
         return new CustomMenuActionSupport.Prepared(null, error == null ? "Custom screen failed" : error);
      }
   }
}
