package riptide.util.macro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;

public class PayAction implements MacroAction {
   public String commandTemplate = "/pay <player> <amount>";
   public String amountInput = "1";
   public int delayMs = 1000;
   public boolean delayEnabled = true;
   public boolean divideEnabled = false;
   public boolean probeHidden = true;
   public String confirmMacro = "";
   public List<String> players = new ArrayList<>();
   private boolean enabled = true;
   public static final int CONFIRM_TIMEOUT_MS = 15000;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("commandTemplate", this.commandTemplate);
      tag.putString("amountInput", this.amountInput);
      tag.putInt("delayMs", normalizeDelay(this.delayMs));
      tag.putBoolean("delayEnabled", this.delayEnabled);
      tag.putBoolean("divideEnabled", this.divideEnabled);
      tag.putBoolean("probeHidden", this.probeHidden);
      tag.putString("confirmMacro", this.confirmMacro == null ? "" : this.confirmMacro);
      ListTag list = new ListTag();

      for (String player : this.players) {
         if (player != null && !player.isBlank()) {
            list.add(StringTag.valueOf(player));
         }
      }

      tag.put("players", list);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("commandTemplate")) {
         this.commandTemplate = tag.getStringOr("commandTemplate", this.commandTemplate);
      }

      if (tag.contains("amountInput")) {
         this.amountInput = tag.getStringOr("amountInput", this.amountInput);
      }

      if (tag.contains("delayMs")) {
         this.delayMs = normalizeDelay(tag.getIntOr("delayMs", 1000));
      }

      if (tag.contains("delayEnabled")) {
         this.delayEnabled = tag.getBooleanOr("delayEnabled", true);
      }

      if (tag.contains("divideEnabled")) {
         this.divideEnabled = tag.getBooleanOr("divideEnabled", false);
      }

      if (tag.contains("probeHidden")) {
         this.probeHidden = tag.getBooleanOr("probeHidden", true);
      }

      this.confirmMacro = tag.getStringOr("confirmMacro", "");
      this.players.clear();
      if (tag.contains("players")) {
         for (Tag element : tag.getList("players").orElse(new ListTag())) {
            String value = element.asString().orElse("").trim();
            if (!value.isEmpty() && !this.players.contains(value)) {
               this.players.add(value);
            }
         }
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.PAY;
   }

   @Override
   public String getDisplayName() {
      String target = this.players.isEmpty() ? "no players" : (this.players.size() == 1 ? this.players.get(0) : this.players.size() + " players");
      String amount = this.amountInput != null && !this.amountInput.isBlank() ? this.amountInput.trim() : "?";
      return "Pay " + target + (this.divideEnabled ? " split " : " ") + amount;
   }

   @Override
   public String getIcon() {
      return "$";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public long resolvedAmount() {
      return parseAmount(this.amountInput);
   }

   public long totalAmount() {
      return this.resolvedAmount() * Math.max(0, this.players.size());
   }

   public static long[] distribute(long total, int count) {
      if (count <= 0) {
         return new long[0];
      } else {
         long safeTotal = Math.max(0L, total);
         long base = safeTotal / count;
         long remainder = safeTotal - base * count;
         long[] amounts = new long[count];

         for (int i = 0; i < count; i++) {
            amounts[i] = base + (i < remainder ? 1L : 0L);
         }

         return amounts;
      }
   }

   public static int normalizeDelay(int delayMs) {
      return Math.max(0, delayMs);
   }

   public static long parseAmount(String input) {
      if (input == null) {
         return 0L;
      } else {
         String normalized = input.trim().replace(" ", "").replace("_", "");
         if (normalized.isEmpty()) {
            return 0L;
         } else {
            char suffix = Character.toUpperCase(normalized.charAt(normalized.length() - 1));
            BigDecimal multiplier = BigDecimal.ONE;
            if (suffix == 'K' || suffix == 'M' || suffix == 'B') {
               normalized = normalized.substring(0, normalized.length() - 1);

               multiplier = switch (suffix) {
                  case 'B' -> new BigDecimal("1000000000");
                  case 'K' -> new BigDecimal("1000");
                  case 'M' -> new BigDecimal("1000000");
                  default -> BigDecimal.ONE;
               };
            }

            if (normalized.contains(",") && normalized.contains(".")) {
               normalized = normalized.replace(",", "");
            } else if (normalized.contains(",")) {
               normalized = normalized.replace(',', '.');
            }

            if (!normalized.isEmpty() && !normalized.equals(".")) {
               try {
                  BigDecimal value = new BigDecimal(normalized);
                  return value.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValue();
               } catch (NumberFormatException var5) {
                  return 0L;
               }
            } else {
               return 0L;
            }
         }
      }
   }

   public static boolean runConfirmMacro(String macroName, BooleanSupplier cancelled) {
      if (macroName == null || macroName.isBlank()) {
         return true;
      } else if (MacroExecutor.isMacroRunning(macroName)) {
         return true;
      } else {
         RiptideMacro macro = RiptideMacroManager.get().get(macroName);
         if (macro == null) {
            return true;
         } else {
            long runId = macro.executeTracked();
            if (runId < 0L) {
               return true;
            } else {
               long deadline = System.nanoTime() + 15000000000L;

               while (MacroExecutor.isRunActive(runId)) {
                  if (cancelled != null && cancelled.getAsBoolean()) {
                     return false;
                  }

                  if (System.nanoTime() >= deadline) {
                     break;
                  }

                  try {
                     Thread.sleep(20L);
                  } catch (InterruptedException var8) {
                     Thread.currentThread().interrupt();
                     return false;
                  }
               }

               return true;
            }
         }
      }
   }

   public static String formatAmount(long amount) {
      DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
      DecimalFormat format = new DecimalFormat("#,##0", symbols);
      return format.format(amount);
   }
}
