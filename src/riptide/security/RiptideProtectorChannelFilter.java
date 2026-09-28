package riptide.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class RiptideProtectorChannelFilter {
   private static final RiptideProtectorChannelFilter.Verdict PASS = new RiptideProtectorChannelFilter.Verdict(
      RiptideProtectorChannelFilter.Verdict.Kind.PASS, null
   );
   private static final RiptideProtectorChannelFilter.Verdict DROP = new RiptideProtectorChannelFilter.Verdict(
      RiptideProtectorChannelFilter.Verdict.Kind.DROP, null
   );
   private static final String MINECRAFT = "minecraft";
   private static final String REGISTER = "register";
   private static final String UNREGISTER = "unregister";
   private static final String MCO = "mco";

   private RiptideProtectorChannelFilter() {
   }

   public static RiptideProtectorChannelFilter.Verdict pass() {
      return PASS;
   }

   public static RiptideProtectorChannelFilter.Verdict drop() {
      return DROP;
   }

   public static RiptideProtectorChannelFilter.Verdict filter(Packet<?> packet) {
      if (packet == null) {
         return PASS;
      } else if (!(packet instanceof ServerboundCustomPayloadPacket customPayload)) {
         return PASS;
      } else if (!RiptideProtector.shouldFilterChannels()) {
         noteIfRegister(customPayload);
         return PASS;
      } else if (RiptideProtector.isUserBypass(packet)) {
         noteIfRegister(customPayload);
         return PASS;
      } else {
         CustomPacketPayload payload = customPayload.payload();
         if (payload == null) {
            return PASS;
         } else if (payload instanceof BrandPayload) {
            return PASS;
         } else {
            Identifier id = payloadId(payload);
            if (id == null) {
               return PASS;
            } else if (RiptideProtector.isVanillaMode()) {
               return DROP;
            } else {
               String namespace = id.getNamespace();
               String path = id.getPath();
               if (!"minecraft".equals(namespace) || !"register".equals(path) && !"unregister".equals(path)) {
                  if ("minecraft".equals(namespace) && "mco".equals(path)) {
                     return DROP;
                  } else {
                     return RiptideProtectorTracker.isWhitelistedChannel(id) ? PASS : DROP;
                  }
               } else {
                  RiptideProtectorChannelFilter.Verdict verdict = filterRegister(payload);
                  if ("register".equals(path) && verdict.kind != RiptideProtectorChannelFilter.Verdict.Kind.DROP) {
                     RiptideFabricRegisterMimicry.noteOutboundRegister();
                  }

                  return verdict;
               }
            }
         }
      }
   }

   private static void noteIfRegister(ServerboundCustomPayloadPacket packet) {
      CustomPacketPayload payload;
      try {
         payload = packet.payload();
      } catch (Throwable var3) {
         return;
      }

      if (payload != null) {
         Identifier id = payloadId(payload);
         if (id != null && "minecraft".equals(id.getNamespace()) && "register".equals(id.getPath())) {
            RiptideFabricRegisterMimicry.noteOutboundRegister();
         }
      }
   }

   private static Identifier payloadId(CustomPacketPayload payload) {
      try {
         Type<?> type = payload.type();
         if (type != null) {
            return type.id();
         }
      } catch (Throwable var2) {
      }

      return null;
   }

   private static RiptideProtectorChannelFilter.Verdict filterRegister(CustomPacketPayload payload) {
      if (payload instanceof RegistrationPayload registrationPayload) {
         List<Identifier> kept = keepWhitelisted(registrationPayload.channels(), RiptideProtectorTracker::isWhitelistedChannel);
         if (kept.size() == registrationPayload.channels().size()) {
            return PASS;
         } else {
            return kept.isEmpty()
               ? DROP
               : new RiptideProtectorChannelFilter.Verdict(
                  RiptideProtectorChannelFilter.Verdict.Kind.REPLACE,
                  new ServerboundCustomPayloadPacket(new RegistrationPayload(registrationPayload.type(), kept))
               );
         }
      } else {
         List<Identifier> channels = extractChannels(payload);
         if (channels == null) {
            logFallbackOnce("uninspectable " + payload.getClass().getName() + " on " + payloadId(payload));
            return PASS;
         } else {
            for (Identifier channel : channels) {
               if (!RiptideProtectorTracker.isWhitelistedChannel(channel)) {
                  logFallbackOnce("dropping non-whitelisted register channel " + channel);
                  return DROP;
               }
            }

            return PASS;
         }
      }
   }

   private static List<Identifier> extractChannels(CustomPacketPayload payload) {
      try {
         Method method = payload.getClass().getMethod("channels");
         if (method.invoke(payload) instanceof List<?> list) {
            List<Identifier> channels = new ArrayList<>(list.size());

            for (Object entry : list) {
               if (!(entry instanceof Identifier identifier)) {
                  return null;
               }

               channels.add(identifier);
            }

            return channels;
         } else {
            return null;
         }
      } catch (Throwable var8) {
         return null;
      }
   }

   private static void logFallbackOnce(String detail) {
      if (RiptideFabricRegisterMimicry.claimFallbackLog()) {
         riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Register fallback engaged ({}); passing by whitelist verdict only.", detail);
      }
   }

   static List<Identifier> keepWhitelisted(Collection<Identifier> channels, Predicate<Identifier> whitelist) {
      List<Identifier> kept = new ArrayList<>(channels.size());

      for (Identifier channel : channels) {
         if (whitelist.test(channel)) {
            kept.add(channel);
         }
      }

      return kept;
   }

   public static final class Verdict {
      public final RiptideProtectorChannelFilter.Verdict.Kind kind;
      public final Packet<?> replacement;

      private Verdict(RiptideProtectorChannelFilter.Verdict.Kind kind, Packet<?> replacement) {
         this.kind = kind;
         this.replacement = replacement;
      }

      public static enum Kind {
         PASS,
         DROP,
         REPLACE;
      }
   }
}
