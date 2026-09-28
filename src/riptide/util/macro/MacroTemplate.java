package riptide.util.macro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import riptide.util.RiptideSharedState;

public final class MacroTemplate {
   private static final Pattern EXPRESSION = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,63}(?:\\|[^{}|]+)*");
   private static final Pattern RANDOM_RANGE = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)\\s*-\\s*([-+]?\\d+(?:\\.\\d+)?)");
   private static final Pattern PICK_WEIGHT = Pattern.compile("(.*?)\\s*\\*\\s*(\\d{1,3})\\s*$");
   private static final Pattern QUANTIFIER_SHAPE = Pattern.compile("\\d+,\\d+");
   private static final String PICK_FORBIDDEN = "\"':={}\\";
   private static final Pattern EMBEDDED_NUMBER = Pattern.compile("(?i)(?:[-+]\\p{Sc}?)?(?:\\d(?:[.,_]?\\d)*|[.,]\\d+)(?:[kmbt](?![\\p{L}\\d]))?");
   public static final Set<String> BUILT_IN_NAMES = Set.of(
      "timestamp",
      "player",
      "user",
      "username",
      "uuid",
      "x",
      "y",
      "z",
      "bx",
      "by",
      "bz",
      "pos",
      "yaw",
      "pitch",
      "rot",
      "facing",
      "dimension",
      "dim",
      "selected_slot",
      "target_slot",
      "server",
      "last_sent",
      "random"
   );

   private MacroTemplate() {
   }

   public static MacroTemplate.Resolution resolve(String template, MacroVariableContext context, Minecraft mc) {
      if (template != null && !template.isEmpty()) {
         StringBuilder out = new StringBuilder(template.length());
         List<String> missing = new ArrayList<>();
         int i = 0;

         while (i < template.length()) {
            char ch = template.charAt(i);
            if (ch == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
               out.append('{');
               i += 2;
            } else if (ch == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
               out.append('}');
               i += 2;
            } else if (ch != '{') {
               out.append(ch);
               i++;
            } else {
               int end = template.indexOf(125, i + 1);
               if (end < 0) {
                  out.append(ch);
                  i++;
               } else {
                  String expression = template.substring(i + 1, end).trim();
                  MacroTemplate.ValueResolution value;
                  if (EXPRESSION.matcher(expression).matches()) {
                     value = resolveExpression(expression, context, mc);
                  } else {
                     if (!isBareRandomSpec(expression)) {
                        out.append(ch);
                        i++;
                        continue;
                     }

                     value = resolveBareRandomSpec(expression);
                  }

                  if (!value.success) {
                     missing.add(value.missingName.isEmpty() ? expression : value.missingName);
                  } else {
                     out.append(positionalCommandValue(expression, value.value, out.length() == 0));
                  }

                  i = end + 1;
               }
            }
         }

         return missing.isEmpty() ? MacroTemplate.Resolution.ok(out.toString()) : MacroTemplate.Resolution.failed(missing, "Missing macro value");
      } else {
         return MacroTemplate.Resolution.ok(template == null ? "" : template);
      }
   }

   private static String positionalCommandValue(String expression, String rendered, boolean atStart) {
      if (!atStart && rendered.length() >= 2 && rendered.charAt(0) == '/') {
         String[] parts = expression.split("\\|", -1);
         if (parts.length != 0 && "last_sent".equalsIgnoreCase(parts[0].trim())) {
            for (int i = 1; i < parts.length; i++) {
               if ("raw".equalsIgnoreCase(parts[i].trim())) {
                  return rendered;
               }
            }

            return rendered.substring(1);
         } else {
            return rendered;
         }
      } else {
         return rendered;
      }
   }

   public static boolean hasVariables(String text) {
      if (text != null && !text.isBlank()) {
         for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '{' && (i + 1 >= text.length() || text.charAt(i + 1) != '{')) {
               int end = text.indexOf(125, i + 1);
               if (end > i) {
                  String expression = text.substring(i + 1, end).trim();
                  if (EXPRESSION.matcher(expression).matches() || isBareRandomSpec(expression)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static MacroTemplate.ValueResolution resolveExpression(String expression, MacroVariableContext context, Minecraft mc) {
      String[] parts = expression.split("\\|", -1);
      String name = parts.length == 0 ? "" : parts[0].trim();
      Optional<MacroValue> found = context == null ? Optional.empty() : context.get(name);
      if (found.isEmpty() && "random".equalsIgnoreCase(name)) {
         return resolveRandom(parts);
      } else {
         if (found.isEmpty()) {
            found = builtIn(name, mc);
         }

         String fallback = null;

         for (int i = 1; i < parts.length; i++) {
            String formatter = parts[i].trim();
            if (formatter.regionMatches(true, 0, "default:", 0, 8)) {
               fallback = formatter.substring(8);
            }
         }

         if (found.isEmpty()) {
            return fallback == null ? MacroTemplate.ValueResolution.missing(name) : MacroTemplate.ValueResolution.ok(fallback);
         } else {
            MacroValue value = found.get();
            String rendered = value.value();

            try {
               for (int ix = 1; ix < parts.length; ix++) {
                  String formatter = parts[ix].trim();
                  if (!formatter.isEmpty() && !formatter.regionMatches(true, 0, "default:", 0, 8)) {
                     rendered = applyFormatter(rendered, formatter, value);
                  }
               }

               return MacroTemplate.ValueResolution.ok(rendered);
            } catch (IllegalArgumentException var11) {
               return MacroTemplate.ValueResolution.missing(name);
            }
         }
      }
   }

   private static String applyFormatter(String text, String formatter, MacroValue value) {
      String key = formatter.toLowerCase(Locale.ROOT);

      return switch (key) {
         case "trim" -> text.trim();
         case "lower" -> text.toLowerCase(Locale.ROOT);
         case "upper" -> text.toUpperCase(Locale.ROOT);
         case "number" -> parseCompactNumber(text);
         case "name" -> (String)value.property("name").map(MacroValue::value).orElse(text);
         case "id" -> (String)value.property("id").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No id"));
         case "count" -> (String)value.property("count").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No count"));
         case "slot" -> (String)value.property("slot").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No slot"));
         default -> (String)value.property(key).map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("Unknown formatter: " + formatter));
      };
   }

   private static MacroTemplate.ValueResolution resolveRandom(String[] parts) {
      int specIndex = -1;
      String rendered = null;

      for (int i = 1; i < parts.length && specIndex < 0; i++) {
         String token = parts[i].trim();
         if (!token.isEmpty() && !token.regionMatches(true, 0, "default:", 0, 8)) {
            if (token.regionMatches(true, 0, "pick:", 0, 5)) {
               List<String> options = new ArrayList<>();

               for (String option : token.substring(5).split(",")) {
                  String trimmed = option.trim();
                  if (!trimmed.isEmpty()) {
                     options.add(trimmed);
                  }
               }

               if (options.isEmpty()) {
                  return MacroTemplate.ValueResolution.missing("random");
               }

               rendered = options.get(ThreadLocalRandom.current().nextInt(options.size()));
               specIndex = i;
            } else {
               Matcher range = RANDOM_RANGE.matcher(token);
               if (range.matches()) {
                  rendered = randomInRange(range.group(1), range.group(2));
                  specIndex = i;
               }
            }
         }
      }

      if (rendered == null) {
         rendered = Integer.toString(ThreadLocalRandom.current().nextInt(100));
      }

      return applyTrailingFormatters(rendered, parts, specIndex, "random");
   }

   private static MacroTemplate.ValueResolution applyTrailingFormatters(String rendered, String[] parts, int specIndex, String missingName) {
      MacroValue value = MacroValue.text(rendered);

      try {
         for (int i = 1; i < parts.length; i++) {
            if (i != specIndex) {
               String formatter = parts[i].trim();
               if (!formatter.isEmpty() && !formatter.regionMatches(true, 0, "default:", 0, 8)) {
                  rendered = applyFormatter(rendered, formatter, value);
               }
            }
         }

         return MacroTemplate.ValueResolution.ok(rendered);
      } catch (IllegalArgumentException var7) {
         return MacroTemplate.ValueResolution.missing(missingName);
      }
   }

   private static boolean isBareRandomSpec(String expression) {
      String spec = specOf(expression);
      if (spec.isEmpty()) {
         return false;
      } else {
         return RANDOM_RANGE.matcher(spec).matches() ? true : parseBarePick(spec) != null;
      }
   }

   private static MacroTemplate.ValueResolution resolveBareRandomSpec(String expression) {
      String[] parts = expression.split("\\|", -1);
      String spec = parts[0].trim();
      Matcher range = RANDOM_RANGE.matcher(spec);
      String rendered = range.matches() ? randomInRange(range.group(1), range.group(2)) : rollPick(parseBarePick(spec));
      return applyTrailingFormatters(rendered, parts, 0, spec);
   }

   private static String specOf(String expression) {
      int pipe = expression.indexOf(124);
      return (pipe < 0 ? expression : expression.substring(0, pipe)).trim();
   }

   private static List<MacroTemplate.PickOption> parseBarePick(String spec) {
      if (spec.indexOf(44) < 0) {
         return null;
      } else {
         for (int i = 0; i < spec.length(); i++) {
            if ("\"':={}\\".indexOf(spec.charAt(i)) >= 0) {
               return null;
            }
         }

         if (QUANTIFIER_SHAPE.matcher(spec).matches()) {
            return null;
         } else {
            List<MacroTemplate.PickOption> options = new ArrayList<>();

            for (String entry : spec.split(",", -1)) {
               String text = entry.trim();
               if (!text.isEmpty()) {
                  int weight = 1;
                  Matcher weighted = PICK_WEIGHT.matcher(text);
                  if (weighted.matches()) {
                     String base = weighted.group(1).trim();
                     int parsed = Integer.parseInt(weighted.group(2));
                     if (!base.isEmpty() && parsed >= 1 && parsed <= 100) {
                        text = base;
                        weight = parsed;
                     }
                  }

                  Matcher range = RANDOM_RANGE.matcher(text);
                  options.add(
                     range.matches()
                        ? new MacroTemplate.PickOption(text, weight, range.group(1), range.group(2))
                        : new MacroTemplate.PickOption(text, weight, null, null)
                  );
               }
            }

            return options.size() >= 2 ? options : null;
         }
      }
   }

   private static String rollPick(List<MacroTemplate.PickOption> options) {
      int total = 0;

      for (MacroTemplate.PickOption option : options) {
         total += option.weight();
      }

      int roll = ThreadLocalRandom.current().nextInt(total);

      for (MacroTemplate.PickOption option : options) {
         roll -= option.weight();
         if (roll < 0) {
            return option.lo() == null ? option.text() : randomInRange(option.lo(), option.hi());
         }
      }

      return options.get(options.size() - 1).text();
   }

   private static String randomInRange(String loText, String hiText) {
      BigDecimal lo = new BigDecimal(loText);
      BigDecimal hi = new BigDecimal(hiText);
      if (lo.compareTo(hi) > 0) {
         BigDecimal swap = lo;
         lo = hi;
         hi = swap;
      }

      if (loText.indexOf(46) < 0 && hiText.indexOf(46) < 0) {
         try {
            long min = lo.longValueExact();
            long max = hi.longValueExact();
            long picked;
            if (min == max) {
               picked = min;
            } else if (max != Long.MAX_VALUE) {
               picked = ThreadLocalRandom.current().nextLong(min, max + 1L);
            } else if (min != Long.MIN_VALUE) {
               picked = ThreadLocalRandom.current().nextLong(min - 1L, max) + 1L;
            } else {
               picked = ThreadLocalRandom.current().nextLong();
            }

            int padWidth = zeroPadWidth(loText, hiText);
            if (padWidth > 0 && picked != Long.MIN_VALUE) {
               String digits = Long.toString(Math.abs(picked));
               StringBuilder padded = new StringBuilder(padWidth + 1);
               if (picked < 0L) {
                  padded.append('-');
               }

               for (int i = digits.length(); i < padWidth; i++) {
                  padded.append('0');
               }

               return padded.append(digits).toString();
            }

            return Long.toString(picked);
         } catch (ArithmeticException var14) {
         }
      }

      int scale = Math.max(lo.scale(), hi.scale());
      BigDecimal pickedx = lo.add(hi.subtract(lo).multiply(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())));
      return pickedx.setScale(scale, RoundingMode.HALF_UP).toPlainString();
   }

   private static int zeroPadWidth(String loText, String hiText) {
      String loDigits = stripSign(loText);
      String hiDigits = stripSign(hiText);
      boolean padded = loDigits.length() >= 2 && loDigits.charAt(0) == '0' || hiDigits.length() >= 2 && hiDigits.charAt(0) == '0';
      return padded ? Math.max(loDigits.length(), hiDigits.length()) : 0;
   }

   private static String stripSign(String text) {
      return !text.startsWith("-") && !text.startsWith("+") ? text : text.substring(1);
   }

   public static String parseCaptureNumber(String raw) {
      return parseCaptureNumber(raw, MacroTemplate.NumberStyle.AUTO);
   }

   public static String parseCaptureNumber(String raw, MacroTemplate.NumberStyle style) {
      if (raw == null) {
         throw new IllegalArgumentException("Empty number");
      } else {
         Matcher found = EMBEDDED_NUMBER.matcher(raw);
         if (!found.find()) {
            throw new IllegalArgumentException("Not a number: " + raw);
         } else {
            return parseMatchedNumber(found.group(), raw, style);
         }
      }
   }

   public static String parseCaptureNumberStrict(String raw) {
      return parseCaptureNumberStrict(raw, MacroTemplate.NumberStyle.AUTO);
   }

   public static String parseCaptureNumberStrict(String raw, MacroTemplate.NumberStyle style) {
      if (raw == null) {
         throw new IllegalArgumentException("Empty number");
      } else {
         Matcher found = EMBEDDED_NUMBER.matcher(raw.trim());
         if (!found.matches()) {
            throw new IllegalArgumentException("Not a number: " + raw);
         } else {
            return parseMatchedNumber(found.group(), raw, style);
         }
      }
   }

   private static String parseMatchedNumber(String match, String raw, MacroTemplate.NumberStyle style) {
      String token = match.trim().replace("_", "").replaceAll("\\p{Sc}", "").toUpperCase(Locale.ROOT);
      if (token.isEmpty()) {
         throw new IllegalArgumentException("Not a number: " + raw);
      } else {
         BigDecimal multiplier = switch (token.charAt(token.length() - 1)) {
            case 'B' -> new BigDecimal("1000000000");
            case 'K' -> new BigDecimal("1000");
            case 'M' -> new BigDecimal("1000000");
            case 'T' -> new BigDecimal("1000000000000");
            default -> BigDecimal.ONE;
         };
         boolean scaled = multiplier.compareTo(BigDecimal.ONE) != 0;
         if (scaled) {
            token = token.substring(0, token.length() - 1);
         }

         String cleaned = style == MacroTemplate.NumberStyle.WHOLE && !scaled ? dropFractionAndSeparators(token) : dropGroupingSeparators(token);
         if (!cleaned.isEmpty() && !"-".equals(cleaned) && !"+".equals(cleaned)) {
            return renderNumber(new BigDecimal(cleaned).multiply(multiplier));
         } else {
            throw new IllegalArgumentException("Not a number: " + raw);
         }
      }
   }

   private static String dropFractionAndSeparators(String token) {
      int last = Math.max(token.lastIndexOf(46), token.lastIndexOf(44));
      String body = last >= 0 && token.length() - last - 1 != 3 ? token.substring(0, last) : token;
      StringBuilder out = new StringBuilder(body.length());

      for (int i = 0; i < body.length(); i++) {
         char ch = body.charAt(i);
         if (ch != '.' && ch != ',') {
            out.append(ch);
         }
      }

      String digits = out.toString();
      return !digits.isEmpty() && !"-".equals(digits) && !"+".equals(digits) ? digits : digits + "0";
   }

   private static String dropGroupingSeparators(String token) {
      int lastDot = token.lastIndexOf(46);
      int lastComma = token.lastIndexOf(44);
      char decimal = 0;
      if (lastDot >= 0 && lastComma >= 0) {
         decimal = (char)(lastDot > lastComma ? 46 : 44);
      } else if (lastDot >= 0 || lastComma >= 0) {
         char only = (char)(lastDot >= 0 ? 46 : 44);
         int index = Math.max(lastDot, lastComma);
         boolean groups = count(token, only) > 1 || only == ',' && token.length() - index - 1 == 3;
         if (!groups) {
            decimal = only;
         }
      }

      StringBuilder out = new StringBuilder(token.length());

      for (int i = 0; i < token.length(); i++) {
         char ch = token.charAt(i);
         if (ch != '.' && ch != ',') {
            out.append(ch);
         } else if (ch == decimal) {
            out.append('.');
         }
      }

      return out.toString();
   }

   private static int count(String text, char ch) {
      int total = 0;

      for (int i = 0; i < text.length(); i++) {
         if (text.charAt(i) == ch) {
            total++;
         }
      }

      return total;
   }

   public static String renderNumber(BigDecimal value) {
      BigDecimal stripped = value.stripTrailingZeros();
      return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
   }

   public static String parseCompactNumber(String raw) {
      if (raw == null) {
         throw new IllegalArgumentException("Empty number");
      } else {
         String token = raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
         if (token.isEmpty()) {
            throw new IllegalArgumentException("Empty number");
         } else {
            BigDecimal multiplier = BigDecimal.ONE;
            char suffix = token.charAt(token.length() - 1);

            multiplier = switch (suffix) {
               case 'B' -> new BigDecimal("1000000000");
               case 'K' -> new BigDecimal("1000");
               case 'M' -> new BigDecimal("1000000");
               case 'T' -> new BigDecimal("1000000000000");
               default -> BigDecimal.ONE;
            };
            if (multiplier.compareTo(BigDecimal.ONE) != 0) {
               token = token.substring(0, token.length() - 1);
            }

            BigDecimal number = new BigDecimal(dropGroupingSeparators(token)).multiply(multiplier).setScale(0, RoundingMode.DOWN);
            return number.toPlainString();
         }
      }
   }

   private static Optional<MacroValue> builtIn(String name, Minecraft mc) {
      if (name == null) {
         return Optional.empty();
      } else {
         String key = name.trim().toLowerCase(Locale.ROOT);
         if ("timestamp".equals(key)) {
            return Optional.of(MacroValue.text(Instant.now().toString()));
         } else if ("last_sent".equals(key)) {
            String sent = RiptideSharedState.get().getLastSentMessage();
            return Optional.of(MacroValue.structured(MacroValue.Kind.TEXT, sent, Map.of("raw", MacroValue.text(sent))));
         } else if (mc != null && mc.player != null) {
            return switch (key) {
               case "player", "user", "username" -> Optional.of(MacroValue.text(mc.player.getName().getString()));
               case "uuid" -> Optional.of(MacroValue.text(mc.player.getUUID().toString()));
               case "x" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getX())));
               case "y" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getY())));
               case "z" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getZ())));
               case "bx" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getX())));
               case "by" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getY())));
               case "bz" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getZ())));
               case "pos" -> {
                  BlockPos pos = mc.player.blockPosition();
                  yield Optional.of(MacroValue.text(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
               }
               case "yaw" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f", mc.player.getYRot())));
               case "pitch" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f", mc.player.getXRot())));
               case "rot" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f %.2f", mc.player.getYRot(), mc.player.getXRot())));
               case "facing" -> Optional.of(MacroValue.text(Direction.fromYRot(mc.player.getYRot()).getName()));
               case "dimension", "dim" -> mc.level == null ? Optional.empty() : Optional.of(MacroValue.text(mc.level.dimension().identifier().toString()));
               case "selected_slot", "target_slot" -> Optional.of(MacroValue.slot(mc.player.getInventory().getSelectedSlot()));
               case "server" -> {
                  ServerData server = mc.getCurrentServer();
                  yield server != null && server.ip != null ? Optional.of(MacroValue.text(server.ip)) : Optional.empty();
               }
               default -> Optional.empty();
            };
         } else {
            return Optional.empty();
         }
      }
   }

   public static enum NumberStyle {
      AUTO,
      WHOLE;
   }

   private record PickOption(String text, int weight, String lo, String hi) {
   }

   public record Resolution(boolean success, String value, List<String> missing, String error) {
      public static MacroTemplate.Resolution ok(String value) {
         return new MacroTemplate.Resolution(true, value, List.of(), "");
      }

      public static MacroTemplate.Resolution failed(List<String> missing, String error) {
         return new MacroTemplate.Resolution(false, "", List.copyOf(missing), error == null ? "" : error);
      }
   }

   private record ValueResolution(boolean success, String value, String missingName) {
      static MacroTemplate.ValueResolution ok(String value) {
         return new MacroTemplate.ValueResolution(true, value, "");
      }

      static MacroTemplate.ValueResolution missing(String name) {
         return new MacroTemplate.ValueResolution(false, "", name == null ? "" : name);
      }
   }
}
