package riptide.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.mixin.accessor.RiptideCommandSuggestionsListAccessor;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideMacroManager;

@Mixin({CommandSuggestions.class})
public abstract class RiptideChatSuggestMixin {
   @Shadow
   @Final
   private EditBox input;
   @Shadow
   @Nullable
   private ParseResults<ClientSuggestionProvider> currentParse;
   @Shadow
   @Nullable
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   @Nullable
   private SuggestionsList suggestions;
   @Shadow
   @Final
   private List<FormattedCharSequence> commandUsage;
   @Shadow
   private boolean currentParseIsCommand;
   @Shadow
   private boolean currentParseIsMessage;
   @Shadow
   private boolean keepSuggestions;
   @Unique
   private boolean riptide$owningSuggestions;
   @Unique
   private String riptide$cachedInput;
   @Unique
   private int riptide$cachedCursor = -1;
   @Unique
   private int riptide$cachedCommandRevision = Integer.MIN_VALUE;
   @Unique
   private int riptide$cachedModuleRevision = Integer.MIN_VALUE;
   @Unique
   private long riptide$cachedMacroRevision = Long.MIN_VALUE;
   @Unique
   private AbstractContainerMenu riptide$cachedMenu;
   @Unique
   private int riptide$cachedMenuRevision = Integer.MIN_VALUE;
   @Unique
   private static final int RIPTIDE_MAX_SUGGESTION_INPUT = 4096;

   @Shadow
   public abstract void showSuggestions(boolean var1);

   @Shadow
   private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parseResults, Suggestions suggestions) {
   }

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$updateRiptideCommandInfo(CallbackInfo ci) {
      try {
         this.riptide$updateRiptideCommandInfoSafe(ci);
      } catch (Throwable var5) {
         riptide.RiptideClientAddon.LOG.warn("[Commands] suggestion update failed", var5);

         try {
            this.riptide$clearActiveSuggestions();
         } catch (Throwable var4) {
         }

         ci.cancel();
      }
   }

   @Unique
   private void riptide$updateRiptideCommandInfoSafe(CallbackInfo ci) {
      String value = this.input.getValue();
      if (riptide$shouldSuppressMeteorSuggestions(value)) {
         this.riptide$clearActiveSuggestions();
         ci.cancel();
      } else if (!riptide$isRiptideCommandInput(value)) {
         if (this.riptide$owningSuggestions) {
            this.riptide$clearActiveSuggestions();
         }
      } else if (value.length() > 4096) {
         if (this.riptide$owningSuggestions) {
            this.riptide$clearActiveSuggestions();
         }

         ci.cancel();
      } else {
         int prefixLen = RiptideCommands.effectivePrefix().length();
         StringReader reader = new StringReader(value);
         reader.setCursor(prefixLen);
         int cursor = Math.max(prefixLen, Math.min(this.input.getCursorPosition(), value.length()));
         int commandRevision = RiptideCommands.revision();
         int moduleRevision = ModuleRegistry.revision();
         long macroRevision = RiptideMacroManager.get().getRevision();
         Minecraft minecraft = Minecraft.getInstance();
         AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
         int menuRevision = menu == null ? -1 : menu.getStateId();
         if (!value.equals(this.riptide$cachedInput)
            || cursor != this.riptide$cachedCursor
            || commandRevision != this.riptide$cachedCommandRevision
            || moduleRevision != this.riptide$cachedModuleRevision
            || macroRevision != this.riptide$cachedMacroRevision
            || menu != this.riptide$cachedMenu
            || menuRevision != this.riptide$cachedMenuRevision
            || !this.riptide$owningSuggestions && this.pendingSuggestions == null) {
            ParseResults<RiptideCommandSource> parse = RiptideCommands.dispatcher().parse(reader, RiptideCommandSource.INSTANCE);
            ParseResults<ClientSuggestionProvider> widgetParse = parse;
            this.currentParse = widgetParse;
            this.currentParseIsCommand = true;
            this.currentParseIsMessage = false;
            this.commandUsage.clear();
            if (this.suggestions != null && this.keepSuggestions && this.riptide$canKeepSuggestionList(value)) {
               this.riptide$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);
               ci.cancel();
            } else {
               this.riptide$clearActiveSuggestions();
               this.riptide$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);
               CompletableFuture<Suggestions> future = RiptideCommands.dispatcher().getCompletionSuggestions(parse, cursor);
               this.pendingSuggestions = future;
               future.thenAccept(result -> {
                  try {
                     if (this.pendingSuggestions != future) {
                        return;
                     }

                     if (!value.equals(this.input.getValue())) {
                        return;
                     }

                     if (!riptide$isRiptideCommandInput(value)) {
                        return;
                     }

                     this.riptide$owningSuggestions = true;
                     this.updateUsageInfo(widgetParse, result);
                     if (this.pendingSuggestions == future && value.equals(this.input.getValue())) {
                        this.showSuggestions(false);
                     }
                  } catch (Throwable var8x) {
                     riptide.RiptideClientAddon.LOG.warn("[Commands] suggestion apply failed", var8x);

                     try {
                        this.riptide$clearActiveSuggestions();
                     } catch (Throwable var7x) {
                     }
                  }
               });
               ci.cancel();
            }
         } else {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At("TAIL")}
   )
   private void riptide$hideLateMeteorSuggestionsInPanic(CallbackInfo ci) {
      if (riptide$shouldSuppressMeteorSuggestions(this.input.getValue())) {
         this.riptide$clearActiveSuggestions();
      }
   }

   @Inject(
      method = {"showSuggestions"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$hideShownMeteorSuggestionsInPanic(boolean narrateFirstSuggestion, CallbackInfo ci) {
      if (riptide$shouldSuppressMeteorSuggestions(this.input.getValue())) {
         this.riptide$clearActiveSuggestions();
         ci.cancel();
      }
   }

   @Unique
   private static boolean riptide$isRiptideCommandInput(String value) {
      return !RiptideCommands.commandsBlockedByPanic() && RiptideCommands.isRiptideCommandMessage(value);
   }

   @Unique
   private static boolean riptide$shouldSuppressMeteorSuggestions(String value) {
      if (!PackHideState.isActive()) {
         return false;
      } else if (!RiptideCompatManager.isMeteorAvailable()) {
         return false;
      } else if (value == null) {
         return false;
      } else {
         String trimmed = value.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("/")) {
            String prefix = RiptideCompatManager.meteorCommandPrefix();
            return !prefix.isEmpty() && trimmed.startsWith(prefix);
         } else {
            return false;
         }
      }
   }

   @Unique
   private boolean riptide$canKeepSuggestionList(String currentInput) {
      if (this.suggestions != null && currentInput != null) {
         try {
            RiptideCommandSuggestionsListAccessor accessor = (RiptideCommandSuggestionsListAccessor)this.suggestions;
            String original = accessor.riptide$getOriginalContents();
            List<Suggestion> list = accessor.riptide$getSuggestionList();
            if (original != null && list != null && !list.isEmpty()) {
               for (Suggestion suggestion : list) {
                  if (suggestion != null) {
                     try {
                        if (currentInput.equals(suggestion.apply(original))) {
                           return true;
                        }
                     } catch (Throwable var8) {
                        return false;
                     }
                  }
               }

               return false;
            } else {
               return false;
            }
         } catch (Throwable var9) {
            return false;
         }
      } else {
         return false;
      }
   }

   @Unique
   private void riptide$rememberSuggestionKey(
      String value, int cursor, int commandRevision, int moduleRevision, long macroRevision, AbstractContainerMenu menu, int menuRevision
   ) {
      this.riptide$cachedInput = value;
      this.riptide$cachedCursor = cursor;
      this.riptide$cachedCommandRevision = commandRevision;
      this.riptide$cachedModuleRevision = moduleRevision;
      this.riptide$cachedMacroRevision = macroRevision;
      this.riptide$cachedMenu = menu;
      this.riptide$cachedMenuRevision = menuRevision;
   }

   @Unique
   private void riptide$clearActiveSuggestions() {
      this.input.setSuggestion(null);
      this.suggestions = null;
      this.pendingSuggestions = null;
      this.keepSuggestions = false;
      this.riptide$owningSuggestions = false;
   }
}
