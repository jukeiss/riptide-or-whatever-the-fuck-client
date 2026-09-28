package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideCraftingHelper;

public class CraftAction implements MacroAction {
   public final List<CraftAction.CraftEntry> entries = new ArrayList<>();

   public void clearEntries() {
      this.entries.clear();
   }

   public boolean hasEntries() {
      return this.entries.stream().anyMatch(CraftAction.CraftEntry::hasRecipe);
   }

   public List<CraftAction.CraftEntry> copyEntries() {
      List<CraftAction.CraftEntry> copies = new ArrayList<>(this.entries.size());

      for (CraftAction.CraftEntry entry : this.entries) {
         if (entry != null) {
            copies.add(entry.copy());
         }
      }

      return copies;
   }

   public void setEntries(List<CraftAction.CraftEntry> values) {
      this.entries.clear();
      if (values != null) {
         for (CraftAction.CraftEntry entry : values) {
            if (entry != null) {
               this.entries.add(entry.copy());
            }
         }
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.getConnection() != null) {
         if (!this.hasEntries()) {
            RiptideClientMessaging.sendPrefixed("No craft recipe selected!");
         } else {
            for (CraftAction.CraftEntry entry : this.entries) {
               if (entry != null && entry.hasRecipe()) {
                  RiptideCraftingHelper.CraftableRecipeOption option = RiptideCraftingHelper.findCraftableRecipe(mc, entry.recipeKey);
                  if (option == null) {
                     option = RiptideCraftingHelper.findCraftableRecipe(mc, entry.recipeId);
                  }

                  if (option == null) {
                     RiptideClientMessaging.sendPrefixed("§cRecipe not found: " + entry.resultName);
                     return;
                  }

                  int desiredAmount = RiptideCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
                  if (desiredAmount <= 0) {
                     RiptideClientMessaging.sendPrefixed("§cNo space or materials for " + entry.resultName + ".");
                     return;
                  }

                  RiptideCraftingHelper.CraftExecutionResult result = RiptideCraftingHelper.executeCraftImmediately(
                     mc, entry.recipeKey, entry.recipeId, desiredAmount
                  );
                  if (!result.success) {
                     RiptideClientMessaging.sendPrefixed("§c" + result.message);
                     return;
                  }
               }
            }
         }
      } else {
         RiptideClientMessaging.sendPrefixed("No network connection!");
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "CRAFT");
      ListTag entryTags = new ListTag();

      for (CraftAction.CraftEntry entry : this.entries) {
         if (entry != null && entry.hasRecipe()) {
            entryTags.add(entry.toTag());
         }
      }

      tag.put("entries", entryTags);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.entries.clear();
      if (tag != null) {
         if (tag.contains("entries")) {
            for (Tag element : tag.getList("entries").orElse(new ListTag())) {
               if (element instanceof CompoundTag entryTag) {
                  CraftAction.CraftEntry entry = CraftAction.CraftEntry.fromTag(entryTag);
                  if (entry.hasRecipe()) {
                     this.entries.add(entry);
                  }
               }
            }
         }

         if (this.entries.isEmpty()) {
            CraftAction.CraftEntry legacyEntry = CraftAction.CraftEntry.fromTag(tag);
            if (legacyEntry.hasRecipe()) {
               this.entries.add(legacyEntry);
            }
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.CRAFT;
   }

   @Override
   public String getDisplayName() {
      if (!this.hasEntries()) {
         return "Craft (empty)";
      } else {
         CraftAction.CraftEntry first = this.entries.get(0);
         if (this.entries.size() == 1) {
            return "Craft " + first.getDisplayName();
         } else {
            String firstName = first.resultName != null && !first.resultName.isBlank() ? first.resultName : "item";
            return "Craft " + firstName + " (+" + (this.entries.size() - 1) + ")";
         }
      }
   }

   @Override
   public String getIcon() {
      return "C";
   }

   public static final class CraftEntry {
      public int recipeId = -1;
      public String recipeKey = "";
      public String resultName = "";
      public String resultNameJson = "";
      public int resultCount = 1;
      public int amount = 1;
      public boolean useMaxAmount = false;

      public CraftAction.CraftEntry copy() {
         CraftAction.CraftEntry copy = new CraftAction.CraftEntry();
         copy.recipeId = this.recipeId;
         copy.recipeKey = this.recipeKey;
         copy.resultName = this.resultName;
         copy.resultNameJson = this.resultNameJson;
         copy.resultCount = this.resultCount;
         copy.amount = this.amount;
         copy.useMaxAmount = this.useMaxAmount;
         return copy;
      }

      public boolean hasRecipe() {
         return (this.recipeId >= 0 || !this.recipeKey.isBlank()) && !this.resultName.isBlank();
      }

      public String getDisplayName() {
         if (!this.hasRecipe()) {
            return "unset";
         } else {
            return this.useMaxAmount ? this.resultName + " [Max]" : this.resultName + " x" + Math.max(1, this.amount);
         }
      }

      public Component resultNameComponent() {
         Component rich = MacroExecutor.deserializeTextComponent(this.resultNameJson);
         return rich != null && !rich.getString().isBlank() ? rich.copy() : Component.literal(this.resultName == null ? "" : this.resultName);
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putInt("recipeId", this.recipeId);
         tag.putString("recipeKey", this.recipeKey == null ? "" : this.recipeKey);
         tag.putString("resultName", this.resultName == null ? "" : this.resultName);
         if (this.resultNameJson != null && !this.resultNameJson.isBlank()) {
            tag.putString("resultNameJson", this.resultNameJson);
         }

         tag.putInt("resultCount", Math.max(1, this.resultCount));
         tag.putInt("amount", Math.max(1, this.amount));
         tag.putBoolean("useMaxAmount", this.useMaxAmount);
         return tag;
      }

      public static CraftAction.CraftEntry fromTag(CompoundTag tag) {
         CraftAction.CraftEntry entry = new CraftAction.CraftEntry();
         if (tag == null) {
            return entry;
         } else {
            entry.recipeId = tag.getIntOr("recipeId", -1);
            entry.recipeKey = tag.getStringOr("recipeKey", "");
            entry.resultName = tag.getStringOr("resultName", "");
            entry.resultNameJson = tag.getStringOr("resultNameJson", "");
            entry.resultCount = Math.max(1, tag.getIntOr("resultCount", 1));
            entry.amount = Math.max(1, tag.getIntOr("amount", 1));
            entry.useMaxAmount = tag.getBooleanOr("useMaxAmount", false);
            return entry;
         }
      }

      public static CraftAction.CraftEntry fromOption(RiptideCraftingHelper.CraftableRecipeOption option, int amount, boolean useMaxAmount) {
         CraftAction.CraftEntry entry = new CraftAction.CraftEntry();
         if (option == null) {
            return entry;
         } else {
            entry.recipeId = option.recipeId;
            entry.recipeKey = option.recipeKey;
            entry.resultName = option.label;
            entry.resultNameJson = MacroExecutor.serializeTextComponent(option.labelComponent);
            entry.resultCount = Math.max(1, option.result.getCount());
            entry.amount = Math.max(1, amount);
            entry.useMaxAmount = useMaxAmount;
            return entry;
         }
      }
   }
}
