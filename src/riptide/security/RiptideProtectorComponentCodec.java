package riptide.security;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public record RiptideProtectorComponentCodec(Codec<Component> wrapped) implements Codec<Component> {
   public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
      DataResult<Pair<Component, T>> result = this.wrapped.decode(ops, input);
      if (!RiptideProtector.shouldTagPacketComponents()) {
         return result;
      } else if (!RiptideProtectorPacketContext.isProcessingPacket()) {
         return result;
      } else {
         Minecraft mc;
         try {
            mc = Minecraft.getInstance();
         } catch (Throwable var6) {
            return result;
         }

         return mc != null && mc.hasSingleplayerServer() ? result : result.map(pair -> pair.mapFirst(c -> {
            markTree(c);
            return c;
         }));
      }
   }

   public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
      return this.wrapped.encode(input, ops, prefix);
   }

   private static void markTree(Component component) {
      if (component != null) {
         ComponentContents contents = component.getContents();
         if (contents instanceof RiptideFromPacketAccess access) {
            access.riptide$setFromPacket();
         }

         if (contents instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
               if (arg instanceof Component argComp) {
                  markTree(argComp);
               }
            }
         }

         for (Component sibling : component.getSiblings()) {
            markTree(sibling);
         }
      }
   }
}
