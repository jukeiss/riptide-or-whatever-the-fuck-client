package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideFromPacketAccess;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorTracker;

@Mixin({TranslatableContents.class})
public abstract class RiptideProtectorTranslatableContentsMixin implements RiptideFromPacketAccess {
   @Unique
   private boolean riptide$fromPacket;
   @Unique
   private boolean riptide$silent;
   @Unique
   private static final String RIPTIDE_ALLOW = "\u0000__riptide_allow__";

   @Override
   public void riptide$setFromPacket() {
      this.riptide$fromPacket = true;
   }

   @Override
   public void riptide$setSilent() {
      this.riptide$silent = true;
   }

   @WrapOperation(
      method = {"decompose"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"
      )}
   )
   private String riptide$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
      String result = this.riptide$handle(id, id);
      return result == "\u0000__riptide_allow__" ? (String)original.call(new Object[]{instance, id}) : result;
   }

   @WrapOperation(
      method = {"decompose"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
      )}
   )
   private String riptide$wrapGetOrDefaultFallback(Language instance, String idArg, String defaultValue, Operation<String> original) {
      String result = this.riptide$handle(idArg, defaultValue);
      return result == "\u0000__riptide_allow__" ? (String)original.call(new Object[]{instance, idArg, defaultValue}) : result;
   }

   @Unique
   private String riptide$handle(String translationKey, String defaultValue) {
      if (this.riptide$silent) {
         return "\u0000__riptide_allow__";
      } else if (!this.riptide$fromPacket) {
         return "\u0000__riptide_allow__";
      } else if (!RiptideProtector.shouldProtectTranslationKeys()) {
         return "\u0000__riptide_allow__";
      } else {
         Minecraft mc;
         try {
            mc = Minecraft.getInstance();
         } catch (Throwable var5) {
            return "\u0000__riptide_allow__";
         }

         if (mc != null && !mc.hasSingleplayerServer()) {
            String replacement = RiptideProtectorTracker.translationReplacement(translationKey, defaultValue);
            return replacement == null ? "\u0000__riptide_allow__" : replacement;
         } else {
            return "\u0000__riptide_allow__";
         }
      }
   }
}
