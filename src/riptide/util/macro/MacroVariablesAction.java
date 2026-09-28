package riptide.util.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class MacroVariablesAction implements MacroAction {
   public ArrayList<String> names = new ArrayList<>();
   public ArrayList<String> values = new ArrayList<>();

   @Override
   public void execute(Minecraft mc) {
      int count = Math.min(this.names.size(), this.values.size());
      LinkedHashMap<String, MacroValue> resolvedValues = new LinkedHashMap<>();

      for (int i = 0; i < count; i++) {
         MacroTemplate.Resolution resolved = MacroVariables.resolve(this.values.get(i), mc);
         if (!resolved.success()) {
            return;
         }

         String name = this.names.get(i);
         if (!MacroVariableContext.cleanRootName(name).isEmpty()) {
            resolvedValues.put(name, MacroValue.text(resolved.value()));
         }
      }

      MacroVariables.setAll(resolvedValues);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.MACRO_VARIABLES;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "MACRO_VARIABLES");
      tag.put("names", MacroStringList.toTag(this.names));
      tag.put("values", MacroStringList.toTag(this.values));
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.names = MacroStringList.fromTag(tag.getList("names").orElse(new ListTag()));
      this.values = MacroStringList.fromTag(tag.getList("values").orElse(new ListTag()));
   }

   @Override
   public String getDisplayName() {
      return "Set variables x" + Math.max(this.names.size(), this.values.size());
   }

   @Override
   public String getIcon() {
      return "V";
   }
}
