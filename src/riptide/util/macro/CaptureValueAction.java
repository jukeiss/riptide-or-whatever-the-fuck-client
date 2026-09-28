package riptide.util.macro;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptidePlayerScanner;
import riptide.util.custommenu.CustomMenuTracker;

public class CaptureValueAction implements MacroAction, MacroCaptureOutput {
   private static final Pattern OBVIOUS_DYNAMIC_VALUE = Pattern.compile(
      "(?i)(?<![a-z0-9_])[-+]?\\d[\\d,._]*(?:[.,]\\d+)?\\s*(?:k|m|b|t|q|thousand|million|billion|trillion)?(?![a-z0-9_])"
   );
   private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
   private static final Comparator<PlayerScoreEntry> SCOREBOARD_DISPLAY_ORDER = Comparator.comparing(PlayerScoreEntry::value)
      .reversed()
      .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
   public CaptureValueAction.Source source = CaptureValueAction.Source.GUI_TITLE;
   public String saveAs = "value";
   public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
   public String pattern = "";
   public String exampleText = "";
   public String selectedText = "";
   public String itemFilter = "";
   public String scoreboardRow = "";
   public int scoreboardRowIndex = -1;
   public String scoreboardObjective = "";
   public int slot = -1;
   public CaptureValueAction.ItemText itemText = CaptureValueAction.ItemText.NAME;
   public CaptureValueAction.NumberMode numberMode = CaptureValueAction.NumberMode.OFF;
   public CaptureValueAction.NumberModifier numberModifier = CaptureValueAction.NumberModifier.NONE;
   public double numberModifierAmount = 1.0;
   public boolean waitForTrigger = defaultWaitForTrigger(CaptureValueAction.Source.GUI_TITLE);
   public String autofillCommand = "";
   public int autofillTimeoutMs = 5000;
   public boolean autofillCacheList = true;
   public CaptureListSelector.Selection listSelection = CaptureListSelector.Selection.RANDOM;
   public CaptureListSelector.Filter listFilter = CaptureListSelector.Filter.NONE;
   public String listFilterText = "";
   public String listExcludeText = "";
   public int listPickPosition = 1;
   public boolean listStripPrefix;
   public boolean excludeSelf = true;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      this.run(mc);
   }

   public CaptureValueAction.Preview run(Minecraft mc) {
      if (mc == null) {
         return CaptureValueAction.Preview.unavailable("Game unavailable");
      } else {
         this.clearOutputs();
         CaptureValueAction.Preview preview = this.preview(mc);
         if (preview.success()) {
            MacroVariables.setAll(preview.values());
         }

         return preview;
      }
   }

   public CaptureValueAction.Preview preview(Minecraft mc) {
      if (mc == null) {
         return CaptureValueAction.Preview.unavailable("Game unavailable");
      } else if (this.source == CaptureValueAction.Source.RECENT_CHAT) {
         List<MacroExecutor.RecentChatMessage> messages = MacroExecutor.getRecentChatMessages();
         if (messages.isEmpty()) {
            return CaptureValueAction.Preview.unavailable("No recent chat");
         } else {
            for (MacroExecutor.RecentChatMessage chat : messages) {
               CaptureValueAction.Preview candidate = this.evaluateCaptured(this.chatValue(chat));
               if (candidate.success()) {
                  return candidate;
               }
            }

            return CaptureValueAction.Preview.unavailable("No recent message matches");
         }
      } else if (this.source == CaptureValueAction.Source.GUI_TITLE) {
         CaptureValueAction.Preview current = this.previewCurrent(mc);
         if (current.success()) {
            return current;
         } else {
            List<MacroExecutor.RecentGuiTitle> titles = MacroExecutor.getRecentGuiTitles();

            for (MacroExecutor.RecentGuiTitle title : titles) {
               CaptureValueAction.Preview candidate = this.evaluateCaptured(this.guiValue(title));
               if (candidate.success()) {
                  return candidate;
               }
            }

            return titles.isEmpty() ? current : CaptureValueAction.Preview.unavailable("No recent GUI matches");
         }
      } else {
         return this.previewCurrent(mc);
      }
   }

   public CaptureValueAction.Preview previewCurrent(Minecraft mc) {
      if (mc == null) {
         return CaptureValueAction.Preview.unavailable("Game unavailable");
      } else {
         if (this.isListSource()) {
            String filterError = CaptureListSelector.filterError(this.listFilter, this.listFilterText);
            if (!filterError.isBlank()) {
               return CaptureValueAction.Preview.unavailable(filterError);
            }

            if (this.source == CaptureValueAction.Source.COMMAND_AUTOFILL || this.source == CaptureValueAction.Source.NAMESCRAPE) {
               return CaptureValueAction.Preview.unavailable("Fetched when macro runs");
            }
         }

         CaptureValueAction.Captured captured = this.capture(mc);
         return this.evaluateCaptured(captured);
      }
   }

   public CaptureValueAction.Preview previewSuggestions(List<String> suggestions, String query) {
      CaptureValueAction.Captured captured = this.pickFromList(suggestions, Map.of());
      if (captured != null && query != null && !query.isBlank()) {
         Map<String, MacroValue> props = new LinkedHashMap<>(captured.value().properties());
         props.put("query", MacroValue.text(query));
         captured = new CaptureValueAction.Captured(captured.text(), MacroValue.structured(MacroValue.Kind.TEXT, captured.value().value(), props));
      }

      return this.evaluateCaptured(captured);
   }

   private boolean isListSource() {
      return this.source == CaptureValueAction.Source.COMMAND_AUTOFILL
         || this.source == CaptureValueAction.Source.TABLIST
         || this.source == CaptureValueAction.Source.NAMESCRAPE;
   }

   public CaptureValueAction.Preview previewChat(MacroExecutor.RecentChatMessage chat) {
      return this.evaluateCaptured(this.chatValue(chat));
   }

   public CaptureValueAction.Preview previewScoreboardLine(CaptureValueAction.ScoreboardLine line) {
      return this.evaluateCaptured(this.scoreboardValue(line));
   }

   public CaptureValueAction.ScoreboardLine selectedScoreboardLine(Minecraft mc) {
      return this.resolveScoreboardLine(mc, this.scoreboardRow, this.scoreboardRowIndex, this.scoreboardObjective);
   }

   public CaptureValueAction.ScoreboardLine resolveScoreboardLine(Minecraft mc, String preferredKey, int preferredRow, String preferredObjective) {
      List<CaptureValueAction.ScoreboardLine> lines = scoreboardLines(mc);
      if (lines.isEmpty()) {
         return null;
      } else {
         String key = preferredKey == null ? "" : preferredKey;

         for (CaptureValueAction.ScoreboardLine line : lines) {
            if (!key.isBlank() && key.equals(line.key())) {
               return line;
            }
         }

         CaptureValueAction.ScoreboardLine rowCandidate = null;

         for (CaptureValueAction.ScoreboardLine linex : lines) {
            if (sameObjective(linex, preferredObjective) && linex.row() == preferredRow) {
               rowCandidate = linex;
               break;
            }
         }

         if (rowCandidate != null && this.previewScoreboardLine(rowCandidate).success()) {
            return rowCandidate;
         } else {
            CaptureValueAction.ScoreboardLine best = this.bestMatchingScoreboardLine(lines, preferredRow, preferredObjective, true);
            return best != null ? best : this.bestMatchingScoreboardLine(lines, preferredRow, preferredObjective, false);
         }
      }
   }

   private CaptureValueAction.ScoreboardLine bestMatchingScoreboardLine(
      List<CaptureValueAction.ScoreboardLine> lines, int preferredRow, String preferredObjective, boolean requireObjective
   ) {
      CaptureValueAction.ScoreboardLine best = null;
      int bestDistance = Integer.MAX_VALUE;

      for (CaptureValueAction.ScoreboardLine line : lines) {
         if ((!requireObjective || sameObjective(line, preferredObjective)) && this.previewScoreboardLine(line).success()) {
            int distance = preferredRow < 0 ? line.row() : Math.abs(line.row() - preferredRow);
            if (best == null || distance < bestDistance) {
               best = line;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private static boolean sameObjective(CaptureValueAction.ScoreboardLine line, String objective) {
      return line != null && (objective == null || objective.isBlank() || objective.equals(line.objective()));
   }

   public static List<CaptureValueAction.ScoreboardLine> scoreboardLines(Minecraft mc) {
      if (mc != null && mc.level != null && mc.player != null) {
         Scoreboard scoreboard = mc.level.getScoreboard();
         Objective objective = sidebarObjective(scoreboard, mc);
         if (objective == null) {
            return List.of();
         } else {
            NumberFormat scoreFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
            List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective)
               .stream()
               .filter(entryx -> !entryx.isHidden())
               .sorted(SCOREBOARD_DISPLAY_ORDER)
               .limit(15L)
               .toList();
            List<CaptureValueAction.ScoreboardLine> lines = new ArrayList<>(entries.size());
            String objectiveTitle = objective.getDisplayName() == null ? "" : objective.getDisplayName().getString();

            for (int i = 0; i < entries.size(); i++) {
               PlayerScoreEntry entry = entries.get(i);
               PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
               Component nameComponent = PlayerTeam.formatNameForTeam(team, entry.ownerName());
               Component scoreComponent = entry.formatValue(scoreFormat);
               String name = nameComponent == null ? "" : nameComponent.getString();
               String score = scoreComponent == null ? "" : scoreComponent.getString();
               String text = score.isEmpty() ? name : name + ": " + score;
               String key = objective.getName() + "\u001f" + entry.owner();
               lines.add(new CaptureValueAction.ScoreboardLine(key, i, objective.getName(), objectiveTitle, entry.owner(), name, score, text));
            }

            return List.copyOf(lines);
         }
      } else {
         return List.of();
      }
   }

   private static Objective sidebarObjective(Scoreboard scoreboard, Minecraft mc) {
      if (scoreboard != null && mc != null && mc.player != null) {
         Objective teamObjective = null;
         PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
         if (team != null) {
            Optional<TeamColor> color = team.getColor();
            if (color.isPresent()) {
               teamObjective = scoreboard.getDisplayObjective(color.get().displaySlot());
            }
         }

         return teamObjective != null ? teamObjective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      } else {
         return null;
      }
   }

   public CaptureValueAction.Preview previewExample() {
      return this.exampleText != null && !this.exampleText.isBlank()
         ? this.evaluateCaptured(new CaptureValueAction.Captured(this.exampleText, MacroValue.text(this.exampleText)))
         : CaptureValueAction.Preview.unavailable("Enter an example message");
   }

   private CaptureValueAction.Preview evaluateCaptured(CaptureValueAction.Captured captured) {
      if (captured != null && captured.value != null) {
         Map<String, MacroValue> additions = new LinkedHashMap<>();
         if (this.isListSource()) {
            additions = new LinkedHashMap<>(combineCapturedOutputs(additions, this.saveAs, captured.value));
            return additions.isEmpty()
               ? CaptureValueAction.Preview.unavailable("Enter a variable name")
               : new CaptureValueAction.Preview(true, captured.text, additions, "Ready");
         } else {
            if (this.matchMode != MacroCapturePattern.Mode.MATCH && this.pattern != null && !this.pattern.isBlank()) {
               Optional<MacroCapturePattern.Result> result = MacroCapturePattern.match(this.matchMode, this.pattern, captured.text);
               if (result.isEmpty()) {
                  return CaptureValueAction.Preview.unavailable("Pattern does not match");
               }

               additions.putAll(result.get().values());
            } else if (this.pattern != null
               && !this.pattern.isBlank()
               && !captured.text.toLowerCase(Locale.ROOT).contains(this.pattern.toLowerCase(Locale.ROOT))) {
               return CaptureValueAction.Preview.unavailable("Text does not match");
            }

            additions = new LinkedHashMap<>(combineCapturedOutputs(additions, this.saveAs, captured.value));
            if (this.mode() != CaptureValueAction.NumberMode.OFF) {
               additions = new LinkedHashMap<>(normalizeCapturedOutputs(additions, this.saveAs, this.mode()));
            }

            CaptureValueAction.NumberModification modification = this.modifyCapturedOutputs(additions);
            if (!modification.valid()) {
               return CaptureValueAction.Preview.unavailable(modification.message());
            } else {
               additions = new LinkedHashMap<>(modification.values());
               return additions.isEmpty()
                  ? CaptureValueAction.Preview.unavailable("Enter a variable name")
                  : new CaptureValueAction.Preview(true, captured.text, additions, "Ready");
            }
         }
      } else {
         return CaptureValueAction.Preview.unavailable("Nothing available now");
      }
   }

   public static boolean defaultWaitForTrigger(CaptureValueAction.Source source) {
      return source == CaptureValueAction.Source.RECENT_CHAT;
   }

   public String numberModifierError() {
      if (this.isListSource()) {
         return "";
      } else {
         CaptureValueAction.NumberModifier modifier = this.numberModifier == null ? CaptureValueAction.NumberModifier.NONE : this.numberModifier;
         if (modifier == CaptureValueAction.NumberModifier.NONE) {
            return "";
         } else if (!Double.isFinite(this.numberModifierAmount)) {
            return "Enter a valid modifier";
         } else {
            return modifier == CaptureValueAction.NumberModifier.DIVIDE && this.numberModifierAmount == 0.0 ? "Cannot divide by zero" : "";
         }
      }
   }

   private CaptureValueAction.NumberMode mode() {
      return this.numberMode == null ? CaptureValueAction.NumberMode.OFF : this.numberMode;
   }

   private MacroTemplate.NumberStyle style() {
      return this.mode() == CaptureValueAction.NumberMode.DROP_CENTS ? MacroTemplate.NumberStyle.WHOLE : MacroTemplate.NumberStyle.AUTO;
   }

   private CaptureValueAction.NumberModification modifyCapturedOutputs(Map<String, MacroValue> values) {
      String error = this.numberModifierError();
      if (!error.isBlank()) {
         return CaptureValueAction.NumberModification.invalid(error);
      } else {
         CaptureValueAction.NumberModifier modifier = this.numberModifier == null ? CaptureValueAction.NumberModifier.NONE : this.numberModifier;
         if (modifier != CaptureValueAction.NumberModifier.NONE && values != null && !values.isEmpty()) {
            BigDecimal operand = BigDecimal.valueOf(this.numberModifierAmount);
            String outputName = MacroVariableContext.cleanRootName(this.saveAs);
            boolean targetTracked = !outputName.isBlank() && values.containsKey(outputName);
            Map<String, MacroValue> modified = new LinkedHashMap<>();
            boolean ok = false;

            for (Entry<String, MacroValue> entry : values.entrySet()) {
               boolean target = targetTracked && entry.getKey().equals(outputName);
               MacroValue original = entry.getValue();
               MacroValue result = modifyCapturedNumber(original, modifier, operand, target, this.style());
               modified.put(entry.getKey(), result);
               if (result != original && (target || !targetTracked)) {
                  ok = true;
               }
            }

            return ok ? CaptureValueAction.NumberModification.valid(modified) : CaptureValueAction.NumberModification.invalid("Not a number");
         } else {
            return CaptureValueAction.NumberModification.valid(values == null ? Map.of() : values);
         }
      }
   }

   private static MacroValue modifyCapturedNumber(
      MacroValue value, CaptureValueAction.NumberModifier modifier, BigDecimal operand, boolean embedded, MacroTemplate.NumberStyle style
   ) {
      BigDecimal number = capturedNumber(value, embedded, style);
      if (number == null) {
         return value;
      } else {
         try {
            BigDecimal result = switch (modifier) {
               case NONE -> number;
               case PLUS -> number.add(operand);
               case MINUS -> number.subtract(operand);
               case MULTIPLY -> number.multiply(operand);
               case DIVIDE -> divideForDisplay(number, operand);
               case PLUS_PERCENT -> percentOfForDisplay(number, BigDecimal.ONE.add(percentFactor(operand)));
               case MINUS_PERCENT -> percentOfForDisplay(number, BigDecimal.ONE.subtract(percentFactor(operand)));
            };
            return rebuildNumber(value, result);
         } catch (ArithmeticException var7) {
            return value;
         }
      }
   }

   private static BigDecimal divideForDisplay(BigDecimal number, BigDecimal operand) {
      BigDecimal exact = number.divide(operand, new MathContext(Math.max(16, number.precision() + 8), RoundingMode.HALF_UP));
      return roundForDisplay(exact, number);
   }

   public static BigDecimal percentFactor(BigDecimal percent) {
      return percent.divide(ONE_HUNDRED, new MathContext(34, RoundingMode.HALF_UP));
   }

   private static BigDecimal percentOfForDisplay(BigDecimal number, BigDecimal factor) {
      return roundForDisplay(number.multiply(factor), number);
   }

   private static BigDecimal roundForDisplay(BigDecimal result, BigDecimal source) {
      int scale = Math.max(2, source.scale() + 2);
      int adjustedExponent = result.precision() - result.scale() - 1;
      if (result.signum() != 0 && adjustedExponent < 0) {
         scale = Math.max(scale, 1 - adjustedExponent);
      }

      return result.setScale(scale, RoundingMode.HALF_UP);
   }

   private static BigDecimal capturedNumber(MacroValue value, boolean embedded, MacroTemplate.NumberStyle style) {
      if (value != null && value.kind() != MacroValue.Kind.SLOT && value.kind() != MacroValue.Kind.IDENTIFIER) {
         try {
            return new BigDecimal(
               embedded ? MacroTemplate.parseCaptureNumber(value.value(), style) : MacroTemplate.parseCaptureNumberStrict(value.value(), style)
            );
         } catch (IllegalArgumentException var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   static Map<String, MacroValue> combineCapturedOutputs(Map<String, MacroValue> patternValues, String saveAs, MacroValue sourceValue) {
      Map<String, MacroValue> outputs = new LinkedHashMap<>();
      if (patternValues != null) {
         outputs.putAll(patternValues);
      }

      String outputName = MacroVariableContext.cleanRootName(saveAs);
      if (!outputName.isBlank() && sourceValue != null) {
         outputs.putIfAbsent(outputName, sourceValue);
      }

      return outputs;
   }

   static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values) {
      return normalizeCapturedOutputs(values, "", CaptureValueAction.NumberMode.SUFFIX_KMB);
   }

   static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values, String saveAs) {
      return normalizeCapturedOutputs(values, saveAs, CaptureValueAction.NumberMode.SUFFIX_KMB);
   }

   static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values, String saveAs, CaptureValueAction.NumberMode mode) {
      if (values != null && !values.isEmpty()) {
         MacroTemplate.NumberStyle style = mode == CaptureValueAction.NumberMode.DROP_CENTS ? MacroTemplate.NumberStyle.WHOLE : MacroTemplate.NumberStyle.AUTO;
         String outputName = MacroVariableContext.cleanRootName(saveAs);
         Map<String, MacroValue> normalized = new LinkedHashMap<>();
         values.forEach((name, value) -> normalized.put(name, normalizeCapturedNumber(value, !outputName.isBlank() && outputName.equals(name), style)));
         return normalized;
      } else {
         return Map.of();
      }
   }

   private static MacroValue normalizeCapturedNumber(MacroValue value, boolean embedded, MacroTemplate.NumberStyle style) {
      BigDecimal number = capturedNumber(value, embedded, style);
      return number == null ? value : rebuildNumber(value, number);
   }

   private static MacroValue rebuildNumber(MacroValue source, BigDecimal number) {
      return source.properties().isEmpty()
         ? MacroValue.number(number)
         : MacroValue.structured(source.kind(), MacroTemplate.renderNumber(number), source.properties());
   }

   private CaptureValueAction.Captured capture(Minecraft mc) {
      return switch (this.source == null ? CaptureValueAction.Source.GUI_TITLE : this.source) {
         case GUI_TITLE -> {
            Screen screen = mc.gui.screen();
            if (screen != null && !MacroGuiMatcher.isOwnScreen(screen)) {
               String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
               yield new CaptureValueAction.Captured(
                  title,
                  MacroValue.structured(
                     MacroValue.Kind.GUI, title, Map.of("title", MacroValue.text(title), "type", MacroValue.text(MacroGuiMatcher.semanticName(screen)))
                  )
               );
            } else {
               CustomMenuSnapshot menu = CustomMenuTracker.current();
               if (menu == null) {
                  yield null;
               } else {
                  String title = menu.title();
                  yield new CaptureValueAction.Captured(
                     title, MacroValue.structured(MacroValue.Kind.GUI, title, Map.of("title", MacroValue.text(title), "type", MacroValue.text("CUSTOM_MENU")))
                  );
               }
            }
         }
         case RECENT_CHAT -> {
            List<MacroExecutor.RecentChatMessage> messages = MacroExecutor.getRecentChatMessages();
            yield messages.isEmpty() ? null : this.chatValue(messages.get(0));
         }
         case SCOREBOARD -> this.scoreboardValue(this.selectedScoreboardLine(mc));
         case GUI_ITEM -> this.captureMenuItem(mc, false);
         case PLAYER_ITEM -> this.capturePlayerItem(mc);
         case CURSOR_ITEM -> mc.player != null && mc.player.containerMenu != null ? this.itemValue(mc.player.containerMenu.getCarried(), -1) : null;
         case HELD_ITEM -> mc.player == null ? null : this.itemValue(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot());
         case COMMAND_AUTOFILL, NAMESCRAPE -> null;
         case TABLIST -> this.tablistValue(mc);
      };
   }

   private CaptureValueAction.Captured tablistValue(Minecraft mc) {
      List<RiptidePlayerScanner.ScannedPlayer> players = RiptidePlayerScanner.scan(mc);
      List<String> names = new ArrayList<>(players.size() + 1);
      Map<String, String> prefixes = new HashMap<>();

      for (RiptidePlayerScanner.ScannedPlayer player : players) {
         names.add(player.name());
         if (player.hasPrefix()) {
            prefixes.put(player.name(), player.prefix());
         }
      }

      if (!this.excludeSelf && mc.player != null && mc.player.getGameProfile() != null) {
         names.add(mc.player.getGameProfile().name());
         names.sort(String.CASE_INSENSITIVE_ORDER);
      }

      return this.pickFromList(names, prefixes);
   }

   public List<String> filterCandidates(List<String> candidates) {
      if (candidates == null) {
         return List.of();
      } else {
         List<String> pool = CaptureListSelector.filter(candidates, this.listFilter, this.listFilterText);
         return CaptureListSelector.exclude(pool, this.listExcludeText);
      }
   }

   private CaptureValueAction.Captured pickFromList(List<String> candidates, Map<String, String> prefixByValue) {
      if (candidates == null) {
         return null;
      } else {
         List<String> pool = this.filterCandidates(candidates);
         CaptureListSelector.State state = MacroExecutor.captureSelectionState(this);
         Optional<CaptureListSelector.Pick> pick = CaptureListSelector.pick(pool, this.listSelection, this.listPickPosition, state);
         if (pick.isEmpty()) {
            return null;
         } else {
            CaptureListSelector.Pick picked = pick.get();
            String value = this.listStripPrefix ? CaptureListSelector.stripMatch(picked.value(), this.listFilter, this.listFilterText) : picked.value();
            Map<String, MacroValue> props = new LinkedHashMap<>();
            props.put("index", MacroValue.number(picked.index() + 1));
            props.put("count", MacroValue.number(pool.size()));
            props.put("total", MacroValue.number(candidates.size()));
            props.put("list", MacroValue.text(String.join(", ", pool)));
            String prefix = prefixByValue == null ? null : prefixByValue.get(picked.value());
            if (prefix != null && !prefix.isBlank()) {
               props.put("prefix", MacroValue.text(prefix));
            }

            return new CaptureValueAction.Captured(value, MacroValue.structured(MacroValue.Kind.TEXT, value, props));
         }
      }
   }

   private CaptureValueAction.Captured chatValue(MacroExecutor.RecentChatMessage chat) {
      if (chat == null) {
         return null;
      } else {
         String display = chat.displayText() == null ? "" : chat.displayText();
         Map<String, MacroValue> props = new LinkedHashMap<>();
         props.put("sender", MacroValue.text(chat.sender()));
         props.put("message", MacroValue.text(chat.message()));
         props.put("display", MacroValue.text(display));
         props.put("source", MacroValue.text(chat.source() == null ? "" : chat.source().name()));
         return new CaptureValueAction.Captured(display, MacroValue.structured(MacroValue.Kind.TEXT, display, props));
      }
   }

   private CaptureValueAction.Captured guiValue(MacroExecutor.RecentGuiTitle gui) {
      if (gui == null) {
         return null;
      } else {
         String title = gui.title() == null ? "" : gui.title();
         return new CaptureValueAction.Captured(
            title,
            MacroValue.structured(
               MacroValue.Kind.GUI, title, Map.of("title", MacroValue.text(title), "type", MacroValue.text(gui.type() == null ? "" : gui.type()))
            )
         );
      }
   }

   private CaptureValueAction.Captured scoreboardValue(CaptureValueAction.ScoreboardLine line) {
      if (line == null) {
         return null;
      } else {
         Map<String, MacroValue> props = new LinkedHashMap<>();
         props.put("line", MacroValue.text(line.text()));
         props.put("name", MacroValue.text(line.name()));
         props.put("score", MacroValue.text(line.score()));
         props.put("row", MacroValue.number(BigDecimal.valueOf(line.row() + 1L)));
         props.put("title", MacroValue.text(line.objectiveTitle()));
         props.put("objective", MacroValue.text(line.objective()));
         return new CaptureValueAction.Captured(line.text(), MacroValue.structured(MacroValue.Kind.TEXT, line.text(), props));
      }
   }

   private CaptureValueAction.Captured captureMenuItem(Minecraft mc, boolean playerOnly) {
      if (mc.player != null && mc.player.containerMenu != null) {
         CaptureValueAction.FilterResolution filterResolution = this.resolveFilter(mc);
         if (!filterResolution.valid()) {
            return null;
         } else {
            ItemTarget filter = filterResolution.target();

            for (Slot candidate : mc.player.containerMenu.slots) {
               if (candidate != null && !candidate.getItem().isEmpty()) {
                  boolean playerSlot = RiptideInventoryHelper.isInventorySlot(mc, candidate);
                  if (playerOnly == playerSlot) {
                     int visible = RiptideInventoryHelper.toUserVisibleSlot(mc, candidate.index);
                     if ((this.slot < 0 || this.slot == visible || this.slot == candidate.index)
                        && (filter == null || !filter.hasIdentity() || filter.score(candidate.getItem(), visible) >= 0)) {
                        return this.itemValue(candidate.getItem(), visible);
                     }
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private CaptureValueAction.Captured capturePlayerItem(Minecraft mc) {
      CaptureValueAction.Captured menu = this.captureMenuItem(mc, true);
      if (menu == null && mc.player != null) {
         CaptureValueAction.FilterResolution filterResolution = this.resolveFilter(mc);
         if (!filterResolution.valid()) {
            return null;
         } else {
            ItemTarget filter = filterResolution.target();

            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               if (!stack.isEmpty() && (this.slot < 0 || this.slot == i) && (filter == null || !filter.hasIdentity() || filter.score(stack, i) >= 0)) {
                  return this.itemValue(stack, i);
               }
            }

            return null;
         }
      } else {
         return menu;
      }
   }

   private CaptureValueAction.FilterResolution resolveFilter(Minecraft mc) {
      if (this.itemFilter != null && !this.itemFilter.isBlank()) {
         MacroTemplate.Resolution resolution = MacroVariables.resolve(this.itemFilter, mc);
         return resolution.success()
            ? new CaptureValueAction.FilterResolution(true, ItemTarget.fromLegacyEntry(resolution.value()))
            : new CaptureValueAction.FilterResolution(false, null);
      } else {
         return new CaptureValueAction.FilterResolution(true, null);
      }
   }

   private CaptureValueAction.Captured itemValue(ItemStack stack, int visibleSlot) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         String itemId = id == null ? "" : id.toString();
         String name = stack.getHoverName().getString();
         ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
         String loreText = lore == null ? "" : lore.lines().stream().map(line -> line.getString()).reduce((a, b) -> a + "\n" + b).orElse("");
         Map<String, MacroValue> props = new LinkedHashMap<>();
         props.put("name", MacroValue.text(name));
         props.put("id", MacroValue.identifier(itemId));
         props.put("count", MacroValue.number(stack.getCount()));
         props.put("slot", MacroValue.slot(visibleSlot));
         props.put("lore", MacroValue.text(loreText));
         if (lore != null) {
            for (int i = 0; i < lore.lines().size(); i++) {
               props.put("lore" + (i + 1), MacroValue.text(((Component)lore.lines().get(i)).getString()));
            }
         }
         String text = switch (this.itemText == null ? CaptureValueAction.ItemText.NAME : this.itemText) {
            case NAME -> name;
            case ID -> itemId;
            case LORE -> loreText;
         };
         return new CaptureValueAction.Captured(text, MacroValue.structured(MacroValue.Kind.ITEM, text, props));
      } else {
         return null;
      }
   }

   private void clearOutputs() {
      if (this.saveAs != null && !this.saveAs.isBlank()) {
         MacroVariables.remove(this.saveAs);
      }

      for (String name : MacroCapturePattern.declaredNames(this.matchMode, this.pattern)) {
         MacroVariables.remove(name);
      }
   }

   public static String suggestDynamicPart(String example) {
      if (example != null && !example.isBlank()) {
         Matcher matcher = OBVIOUS_DYNAMIC_VALUE.matcher(example);
         return matcher.find() ? matcher.group().trim() : "";
      } else {
         return "";
      }
   }

   public static String buildCapturePattern(String example, String selected, String variableName) {
      if (example != null && selected != null && !selected.isBlank()) {
         String root = MacroVariableContext.cleanRootName(variableName);
         if (root.isBlank()) {
            if (variableName != null && !variableName.isBlank()) {
               return "";
            }

            root = "value";
         }

         int index = example.indexOf(selected);
         if (index < 0) {
            index = example.toLowerCase(Locale.ROOT).indexOf(selected.toLowerCase(Locale.ROOT));
         }

         if (index < 0) {
            return "";
         } else {
            String before = escapeCaptureLiteral(example.substring(0, index));
            String after = escapeCaptureLiteral(example.substring(index + selected.length()));
            return before + "{" + root + "}" + after;
         }
      } else {
         return "";
      }
   }

   private static String escapeCaptureLiteral(String value) {
      return value == null ? "" : value.replace("{", "{{").replace("}", "}}");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.CAPTURE_VALUE;
   }

   @Override
   public String getDisplayName() {
      return "Capture " + (this.saveAs != null && !this.saveAs.isBlank() ? this.saveAs : "Value");
   }

   @Override
   public String getIcon() {
      return "{}";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("source", (this.source == null ? CaptureValueAction.Source.GUI_TITLE : this.source).name());
      tag.putString("saveAs", this.saveAs == null ? "" : this.saveAs);
      tag.putString("matchMode", (this.matchMode == null ? MacroCapturePattern.Mode.MATCH : this.matchMode).name());
      tag.putString("pattern", this.pattern == null ? "" : this.pattern);
      tag.putString("exampleText", this.exampleText == null ? "" : this.exampleText);
      tag.putString("selectedText", this.selectedText == null ? "" : this.selectedText);
      tag.putString("itemFilter", this.itemFilter == null ? "" : this.itemFilter);
      tag.putString("scoreboardRow", this.scoreboardRow == null ? "" : this.scoreboardRow);
      tag.putInt("scoreboardRowIndex", this.scoreboardRowIndex);
      tag.putString("scoreboardObjective", this.scoreboardObjective == null ? "" : this.scoreboardObjective);
      tag.putInt("slot", this.slot);
      tag.putString("itemText", (this.itemText == null ? CaptureValueAction.ItemText.NAME : this.itemText).name());
      tag.putString("numberMode", this.mode().name());
      tag.putString("numberModifier", (this.numberModifier == null ? CaptureValueAction.NumberModifier.NONE : this.numberModifier).name());
      tag.putDouble("numberModifierAmount", this.numberModifierAmount);
      tag.putBoolean("waitForTrigger", this.waitForTrigger);
      tag.putString("autofillCommand", this.autofillCommand == null ? "" : this.autofillCommand);
      tag.putInt("autofillTimeoutMs", this.autofillTimeoutMs);
      tag.putBoolean("autofillCacheList", this.autofillCacheList);
      tag.putString("listSelection", (this.listSelection == null ? CaptureListSelector.Selection.RANDOM : this.listSelection).name());
      tag.putString("listFilter", (this.listFilter == null ? CaptureListSelector.Filter.NONE : this.listFilter).name());
      tag.putString("listFilterText", this.listFilterText == null ? "" : this.listFilterText);
      tag.putString("listExcludeText", this.listExcludeText == null ? "" : this.listExcludeText);
      tag.putInt("listPickPosition", this.listPickPosition);
      tag.putBoolean("listStripPrefix", this.listStripPrefix);
      tag.putBoolean("excludeSelf", this.excludeSelf);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.source = MacroStringList.enumValue(CaptureValueAction.Source.class, tag.getStringOr("source", "GUI_TITLE"), CaptureValueAction.Source.GUI_TITLE);
      this.saveAs = tag.getStringOr("saveAs", "value");
      this.matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
      this.pattern = tag.getStringOr("pattern", "");
      this.exampleText = tag.getStringOr("exampleText", "");
      this.selectedText = tag.getStringOr("selectedText", "");
      this.itemFilter = tag.getStringOr("itemFilter", "");
      this.scoreboardRow = tag.getStringOr("scoreboardRow", "");
      this.scoreboardRowIndex = tag.getIntOr("scoreboardRowIndex", -1);
      this.scoreboardObjective = tag.getStringOr("scoreboardObjective", "");
      this.slot = tag.getIntOr("slot", -1);
      this.itemText = MacroStringList.enumValue(CaptureValueAction.ItemText.class, tag.getStringOr("itemText", "NAME"), CaptureValueAction.ItemText.NAME);
      this.numberMode = tag.contains("numberMode")
         ? MacroStringList.enumValue(CaptureValueAction.NumberMode.class, tag.getStringOr("numberMode", "OFF"), CaptureValueAction.NumberMode.OFF)
         : (tag.getBooleanOr("normalizeNumbers", false) ? CaptureValueAction.NumberMode.SUFFIX_KMB : CaptureValueAction.NumberMode.OFF);
      this.numberModifier = MacroStringList.enumValue(
         CaptureValueAction.NumberModifier.class, tag.getStringOr("numberModifier", "NONE"), CaptureValueAction.NumberModifier.NONE
      );
      this.numberModifierAmount = tag.getDoubleOr("numberModifierAmount", 1.0);
      this.waitForTrigger = tag.getBooleanOr("waitForTrigger", defaultWaitForTrigger(this.source));
      this.autofillCommand = tag.getStringOr("autofillCommand", "");
      this.autofillTimeoutMs = tag.getIntOr("autofillTimeoutMs", 5000);
      this.autofillCacheList = tag.getBooleanOr("autofillCacheList", true);
      this.listSelection = MacroStringList.enumValue(
         CaptureListSelector.Selection.class, tag.getStringOr("listSelection", "RANDOM"), CaptureListSelector.Selection.RANDOM
      );
      this.listFilter = MacroStringList.enumValue(CaptureListSelector.Filter.class, tag.getStringOr("listFilter", "NONE"), CaptureListSelector.Filter.NONE);
      this.listFilterText = tag.getStringOr("listFilterText", "");
      this.listExcludeText = tag.getStringOr("listExcludeText", "");
      this.listPickPosition = tag.getIntOr("listPickPosition", 1);
      this.listStripPrefix = tag.getBooleanOr("listStripPrefix", false);
      this.excludeSelf = tag.getBooleanOr("excludeSelf", true);
      this.enabled = tag.getBooleanOr("enabled", true);
   }

   private record Captured(String text, MacroValue value) {
   }

   private record FilterResolution(boolean valid, ItemTarget target) {
   }

   public static enum ItemText {
      NAME,
      ID,
      LORE;
   }

   public static enum NumberMode {
      OFF,
      SUFFIX_KMB,
      DROP_CENTS;
   }

   private record NumberModification(boolean valid, Map<String, MacroValue> values, String message) {
      private static CaptureValueAction.NumberModification valid(Map<String, MacroValue> values) {
         return new CaptureValueAction.NumberModification(true, values == null ? Map.of() : values, "");
      }

      private static CaptureValueAction.NumberModification invalid(String message) {
         return new CaptureValueAction.NumberModification(false, Map.of(), message == null ? "Invalid number modifier" : message);
      }
   }

   public static enum NumberModifier {
      NONE,
      PLUS,
      MINUS,
      MULTIPLY,
      DIVIDE,
      PLUS_PERCENT,
      MINUS_PERCENT;
   }

   public record Preview(boolean success, String sourceText, Map<String, MacroValue> values, String message) {
      public Preview(boolean success, String sourceText, Map<String, MacroValue> values, String message) {
         sourceText = sourceText == null ? "" : sourceText;
         values = values == null ? Map.of() : Map.copyOf(values);
         message = message == null ? "" : message;
         this.success = success;
         this.sourceText = sourceText;
         this.values = values;
         this.message = message;
      }

      public static CaptureValueAction.Preview unavailable(String message) {
         return new CaptureValueAction.Preview(false, "", Map.of(), message);
      }

      public String value(String variableName) {
         String clean = MacroVariableContext.cleanRootName(variableName);
         MacroValue value = clean.isBlank() ? null : this.values.get(clean);
         return value == null ? "" : value.value();
      }
   }

   public record ScoreboardLine(String key, int row, String objective, String objectiveTitle, String owner, String name, String score, String text) {
   }

   public static enum Source {
      GUI_TITLE,
      RECENT_CHAT,
      SCOREBOARD,
      GUI_ITEM,
      PLAYER_ITEM,
      CURSOR_ITEM,
      HELD_ITEM,
      COMMAND_AUTOFILL,
      TABLIST,
      NAMESCRAPE;
   }
}
