package riptide.util.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class MultiAutoAccept {
   public static final int MAX_RESPONDERS = 6;
   public String tpaToMeCommand = "/tpahere {bot}";
   public String tpaToBotCommand = "/tpa {bot}";
   public String tradeCommand = "/trade {bot}";
   public boolean tpaEnabled = true;
   public String tpaAcceptCommand = "/tpaccept {name}";
   public boolean tpaUseMacro = false;
   public String tpaMacroName = "";
   public int tpaArmWindowMs = 15000;
   public int tpaAcceptDelayMs = 300;
   public boolean tradeEnabled = true;
   public String tradeAcceptCommand = "/trade accept";
   public boolean tradeUseMacro = false;
   public String tradeMacroName = "";
   public int tradeArmWindowMs = 15000;
   public int tradeAcceptDelayMs = 300;
   public final List<MultiAutoAccept.Responder> responders = new ArrayList<>();

   public MultiAutoAccept() {
   }

   public MultiAutoAccept(MultiAutoAccept source) {
      if (source != null) {
         this.tpaToMeCommand = source.tpaToMeCommand;
         this.tpaToBotCommand = source.tpaToBotCommand;
         this.tradeCommand = source.tradeCommand;
         this.tpaEnabled = source.tpaEnabled;
         this.tpaAcceptCommand = source.tpaAcceptCommand;
         this.tpaUseMacro = source.tpaUseMacro;
         this.tpaMacroName = source.tpaMacroName;
         this.tpaArmWindowMs = source.tpaArmWindowMs;
         this.tpaAcceptDelayMs = source.tpaAcceptDelayMs;
         this.tradeEnabled = source.tradeEnabled;
         this.tradeAcceptCommand = source.tradeAcceptCommand;
         this.tradeUseMacro = source.tradeUseMacro;
         this.tradeMacroName = source.tradeMacroName;
         this.tradeArmWindowMs = source.tradeArmWindowMs;
         this.tradeAcceptDelayMs = source.tradeAcceptDelayMs;
         this.responders.addAll(source.responders);
      }
   }

   public void normalize() {
      this.tpaToMeCommand = clean(this.tpaToMeCommand, "/tpahere {bot}");
      this.tpaToBotCommand = clean(this.tpaToBotCommand, "/tpa {bot}");
      this.tradeCommand = clean(this.tradeCommand, "/trade {bot}");
      this.tpaAcceptCommand = clean(this.tpaAcceptCommand, "/tpaccept {name}");
      this.tradeAcceptCommand = clean(this.tradeAcceptCommand, "/trade accept");
      this.tpaMacroName = this.tpaMacroName == null ? "" : this.tpaMacroName.trim();
      this.tradeMacroName = this.tradeMacroName == null ? "" : this.tradeMacroName.trim();
      this.tpaArmWindowMs = clampArm(this.tpaArmWindowMs);
      this.tpaAcceptDelayMs = clampDelay(this.tpaAcceptDelayMs);
      this.tradeArmWindowMs = clampArm(this.tradeArmWindowMs);
      this.tradeAcceptDelayMs = clampDelay(this.tradeAcceptDelayMs);
      this.responders.removeIf(r -> r == null || !r.valid());

      while (this.responders.size() > 6) {
         this.responders.remove(this.responders.size() - 1);
      }
   }

   public static String expand(String template, String botName, String userName) {
      String out = template == null ? "" : template;
      out = replaceCi(out, "{bot}", botName == null ? "" : botName);
      out = replaceCi(out, "{name}", userName == null ? "" : userName);
      out = replaceCi(out, "{me}", userName == null ? "" : userName);
      return out.trim();
   }

   private static String replaceCi(String in, String token, String value) {
      StringBuilder sb = new StringBuilder();
      String lower = in.toLowerCase(Locale.ROOT);
      String tok = token.toLowerCase(Locale.ROOT);
      int i = 0;

      while (i < in.length()) {
         int at = lower.indexOf(tok, i);
         if (at < 0) {
            sb.append(in, i, in.length());
            break;
         }

         sb.append(in, i, at).append(value);
         i = at + tok.length();
      }

      return sb.toString();
   }

   private static String clean(String value, String fallback) {
      String v = value == null ? "" : value.trim();
      return v.isEmpty() ? fallback : v;
   }

   private static int clampArm(int value) {
      return Math.max(2000, Math.min(60000, value));
   }

   private static int clampDelay(int value) {
      return Math.max(0, Math.min(10000, value));
   }

   public CompoundTag toTag() {
      this.normalize();
      CompoundTag tag = new CompoundTag();
      tag.putString("tpaToMe", this.tpaToMeCommand);
      tag.putString("tpaToBot", this.tpaToBotCommand);
      tag.putString("tradeCmd", this.tradeCommand);
      tag.putBoolean("tpaEnabled", this.tpaEnabled);
      tag.putString("tpaAccept", this.tpaAcceptCommand);
      tag.putBoolean("tpaUseMacro", this.tpaUseMacro);
      tag.putString("tpaMacro", this.tpaMacroName);
      tag.putInt("tpaArmMs", this.tpaArmWindowMs);
      tag.putInt("tpaDelayMs", this.tpaAcceptDelayMs);
      tag.putBoolean("tradeEnabled", this.tradeEnabled);
      tag.putString("tradeAccept", this.tradeAcceptCommand);
      tag.putBoolean("tradeUseMacro", this.tradeUseMacro);
      tag.putString("tradeMacro", this.tradeMacroName);
      tag.putInt("tradeArmMs", this.tradeArmWindowMs);
      tag.putInt("tradeDelayMs", this.tradeAcceptDelayMs);
      ListTag list = new ListTag();

      for (MultiAutoAccept.Responder responder : this.responders) {
         list.add(responder.toTag());
      }

      tag.put("responders", list);
      return tag;
   }

   public static MultiAutoAccept fromTag(CompoundTag tag) {
      MultiAutoAccept auto = new MultiAutoAccept();
      auto.tpaToMeCommand = tag.getStringOr("tpaToMe", auto.tpaToMeCommand);
      auto.tpaToBotCommand = tag.getStringOr("tpaToBot", auto.tpaToBotCommand);
      auto.tradeCommand = tag.getStringOr("tradeCmd", auto.tradeCommand);
      auto.tpaEnabled = tag.getBooleanOr("tpaEnabled", true);
      auto.tpaAcceptCommand = tag.getStringOr("tpaAccept", auto.tpaAcceptCommand);
      auto.tpaUseMacro = tag.getBooleanOr("tpaUseMacro", false);
      auto.tpaMacroName = tag.getStringOr("tpaMacro", "");
      auto.tradeEnabled = tag.getBooleanOr("tradeEnabled", true);
      auto.tradeAcceptCommand = tag.getStringOr("tradeAccept", auto.tradeAcceptCommand);
      auto.tradeUseMacro = tag.getBooleanOr("tradeUseMacro", false);
      auto.tradeMacroName = tag.getStringOr("tradeMacro", "");
      int legacyArm = tag.getIntOr("armWindowMs", 15000);
      int legacyDelay = tag.getIntOr("acceptDelayMs", 300);
      auto.tpaArmWindowMs = tag.getIntOr("tpaArmMs", legacyArm);
      auto.tpaAcceptDelayMs = tag.getIntOr("tpaDelayMs", legacyDelay);
      auto.tradeArmWindowMs = tag.getIntOr("tradeArmMs", legacyArm);
      auto.tradeAcceptDelayMs = tag.getIntOr("tradeDelayMs", legacyDelay);
      auto.responders.clear();

      for (Tag value : tag.getListOrEmpty("responders")) {
         if (value instanceof CompoundTag compound) {
            auto.responders.add(MultiAutoAccept.Responder.fromTag(compound));
         }
      }

      auto.normalize();
      return auto;
   }

   public record Responder(String trigger, String response, boolean useMacro, String macroName, int delayMs) {
      public Responder(String trigger, String response, boolean useMacro, String macroName, int delayMs) {
         trigger = trigger == null ? "" : trigger.trim();
         response = response == null ? "" : response.trim();
         macroName = macroName == null ? "" : macroName.trim();
         delayMs = Math.max(0, Math.min(60000, delayMs));
         this.trigger = trigger;
         this.response = response;
         this.useMacro = useMacro;
         this.macroName = macroName;
         this.delayMs = delayMs;
      }

      public boolean valid() {
         return !this.trigger.isBlank() && (this.useMacro ? !this.macroName.isBlank() : !this.response.isBlank());
      }

      CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("trigger", this.trigger);
         tag.putString("response", this.response);
         tag.putBoolean("useMacro", this.useMacro);
         tag.putString("macroName", this.macroName);
         tag.putInt("delayMs", this.delayMs);
         return tag;
      }

      static MultiAutoAccept.Responder fromTag(CompoundTag tag) {
         return new MultiAutoAccept.Responder(
            tag.getStringOr("trigger", ""),
            tag.getStringOr("response", ""),
            tag.getBooleanOr("useMacro", false),
            tag.getStringOr("macroName", ""),
            tag.getIntOr("delayMs", 0)
         );
      }
   }
}
