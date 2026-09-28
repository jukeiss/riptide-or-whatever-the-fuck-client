package riptide.util.macro;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class MultiAction implements MacroAction {
   public static final String WAIT_UNTIL_READY = "Until ready";
   public static final String WAIT_FIXED_DELAY = "Fixed delay";
   public static final String WAIT_NO_WAIT = "No wait";
   public LinkedHashSet<String> accountIds = new LinkedHashSet<>();
   public ArrayList<String> accounts = new ArrayList<>();
   public int stepCount = 1;
   public boolean connectIfDown = true;
   public boolean disconnectAfter = false;
   public String waitMode = "Until ready";
   public int waitMs = 30000;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.put("accounts", MacroStringList.toTag(this.effectiveAccounts()));
      tag.putInt("stepCount", Math.max(0, this.stepCount));
      tag.putBoolean("connectIfDown", this.connectIfDown);
      tag.putBoolean("disconnectAfter", this.disconnectAfter);
      tag.putString("waitMode", canonicalWaitMode(this.waitMode));
      tag.putInt("waitMs", Math.max(0, this.waitMs));
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.accounts = MacroStringList.fromTag(tag.getList("accounts").orElse(new ListTag()));
      this.accounts.removeIf(s -> s == null || s.isBlank());
      this.accountIds = new LinkedHashSet<>();

      for (String id : this.accounts) {
         this.accountIds.add(id.trim());
      }

      this.stepCount = Math.max(0, tag.getIntOr("stepCount", 1));
      this.connectIfDown = tag.getBooleanOr("connectIfDown", true);
      this.disconnectAfter = tag.getBooleanOr("disconnectAfter", false);
      this.waitMode = canonicalWaitMode(tag.getStringOr("waitMode", "Until ready"));
      this.waitMs = Math.max(0, tag.getIntOr("waitMs", 30000));
   }

   public List<String> effectiveAccounts() {
      ArrayList<String> out = new ArrayList<>();
      if (this.accounts != null) {
         for (String id : this.accounts) {
            if (id != null && !id.isBlank()) {
               String trimmed = id.trim();
               if (!out.contains(trimmed)) {
                  out.add(trimmed);
               }
            }
         }
      }

      if (out.isEmpty() && this.accountIds != null) {
         for (String idx : this.accountIds) {
            if (idx != null && !idx.isBlank()) {
               out.add(idx.trim());
            }
         }
      }

      return out;
   }

   public LinkedHashSet<String> effectiveAccountIds() {
      return new LinkedHashSet<>(this.effectiveAccounts());
   }

   public static String canonicalWaitMode(String raw) {
      return !"Fixed delay".equals(raw) && !"No wait".equals(raw) ? "Until ready" : raw;
   }

   public static String anonymizedAccountLabel(int index) {
      return "empty " + (index + 1);
   }

   public static String placeholderAccountId(int index) {
      return "empty" + (index + 1);
   }

   @Override
   public void sanitizeForSharing() {
      ArrayList<String> sanitized = new ArrayList<>();

      for (int i = 0; i < this.effectiveAccounts().size(); i++) {
         sanitized.add(placeholderAccountId(i));
      }

      this.accounts = sanitized;
      this.accountIds = new LinkedHashSet<>(sanitized);
   }

   public int normalizedStepCount(List<MacroAction> actions, int headerIndex) {
      if (actions != null && headerIndex >= 0 && headerIndex < actions.size()) {
         int max = Math.max(0, Math.min(this.stepCount, actions.size() - headerIndex - 1));
         int count = 0;

         for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction action = actions.get(i);
            if (action instanceof RaceAction
               || action instanceof ReportAction
               || action instanceof MultiAction
               || action instanceof PacketGateAction
               || action instanceof EndPacketGateAction) {
               break;
            }

            count++;
         }

         return count;
      } else {
         return 0;
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.MULTI;
   }

   @Override
   public String getDisplayName() {
      int steps = Math.max(0, this.stepCount);
      int selected = this.effectiveAccounts().size();
      return "Multi [" + steps + (steps == 1 ? " step" : " steps") + " on " + selected + (selected == 1 ? " account" : " accounts") + "]";
   }

   @Override
   public String getIcon() {
      return "Mlt";
   }
}
