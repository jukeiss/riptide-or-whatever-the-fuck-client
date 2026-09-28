package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideFromPacketAccess;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorTracker;

@Mixin({KeybindContents.class})
public abstract class RiptideProtectorKeybindContentsMixin implements RiptideFromPacketAccess {
   @Shadow
   @Final
   private String name;
   @Unique
   private boolean riptide$fromPacket;
   @Unique
   private Object riptide$cachedBlocked;

   @Override
   public void riptide$setFromPacket() {
      this.riptide$fromPacket = true;
   }

   @WrapOperation(
      method = {"getNestedComponent"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"
      )}
   )
   private Object riptide$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
      if (!this.riptide$fromPacket) {
         return original.call(new Object[]{supplier});
      } else if (!RiptideProtector.shouldProtectTranslationKeys()) {
         return original.call(new Object[]{supplier});
      } else {
         Minecraft mc;
         try {
            mc = Minecraft.getInstance();
         } catch (Throwable var5) {
            return original.call(new Object[]{supplier});
         }

         if (mc == null || mc.hasSingleplayerServer()) {
            return original.call(new Object[]{supplier});
         } else if (!RiptideProtectorTracker.shouldBlockKeybind(this.name)) {
            return original.call(new Object[]{supplier});
         } else if (this.riptide$cachedBlocked != null) {
            return this.riptide$cachedBlocked;
         } else {
            Component replacement = Component.literal(this.name);
            this.riptide$cachedBlocked = replacement;
            return replacement;
         }
      }
   }
}
