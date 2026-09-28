package riptide.util;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.LastSeenMessages.Update;
import net.minecraft.network.chat.RemoteChatSession.Data;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetGameRulePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundSetGameRulePacket.Entry;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.CommandBlockEntity.Mode;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class RiptidePacketArgumentBuilder {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final List<String> RAW_BODY_ARG_NAMES = List.of("raw", "rawHex", "body", "bodyHex", "hex", "bytes");
   private static final List<String> RAW_PACKET_ARG_NAMES = List.of("packet", "packetHex", "full", "fullHex", "plaintext", "packetBytes");
   private static final List<String> RAW_BASE64_ARG_NAMES = List.of("base64", "b64", "bodyBase64");
   private static final List<String> RAW_ARG_NAMES;

   private RiptidePacketArgumentBuilder() {
   }

   public static RiptidePacketArgumentBuilder.PreparedArgs prepare(String rawArgs) {
      List<String> tokens = tokenize(rawArgs == null ? "" : rawArgs);
      boolean dryRun = false;
      List<String> kept = new ArrayList<>();

      for (String token : tokens) {
         String normalized = normalizeName(token);
         int eq = token.indexOf(61);
         String key = eq >= 0 ? normalizeName(token.substring(0, eq)) : normalized;
         if (!key.equals("dry") && !key.equals("dryrun") && !key.equals("preview") && !key.equals("nosend")) {
            kept.add(token);
         } else {
            dryRun = eq < 0 || parseBoolean(key, token.substring(eq + 1));
         }
      }

      return new RiptidePacketArgumentBuilder.PreparedArgs(joinTokens(kept), dryRun);
   }

   public static RiptidePacketArgumentBuilder.Result build(Class<? extends Packet<?>> packetClass, String rawArgs) {
      if (packetClass == null) {
         return RiptidePacketArgumentBuilder.Result.error("Packet class is missing.");
      } else {
         Map<String, String> args;
         try {
            args = parseArgs(rawArgs);
         } catch (IllegalArgumentException var6) {
            return RiptidePacketArgumentBuilder.Result.error(var6.getMessage());
         }

         if (!args.containsKey("help") && !args.containsKey("?")) {
            RiptidePacketArgumentBuilder.Result rawCodec = tryRawCodec(packetClass, args);
            if (rawCodec != null) {
               return rawCodec;
            } else {
               RiptidePacketArgumentBuilder.Result special = trySpecial(packetClass, args);
               if (special != null) {
                  return special;
               } else if (args.isEmpty()) {
                  Packet<?> noArg = tryNoArg(packetClass);
                  return noArg != null
                     ? RiptidePacketArgumentBuilder.Result.ok(noArg, "no-arg")
                     : RiptidePacketArgumentBuilder.Result.error("Packet needs arguments. " + help(packetClass));
               } else {
                  if (packetClass.isRecord()) {
                     RiptidePacketArgumentBuilder.Result record = tryRecord(packetClass, args);
                     if (record != null) {
                        return record;
                     }
                  }

                  RiptidePacketArgumentBuilder.Result schema = trySchemaConstructor(packetClass, args);
                  return schema != null
                     ? schema
                     : RiptidePacketArgumentBuilder.Result.error("Could not build " + displayName(packetClass) + ". " + help(packetClass));
               }
            }
         } else {
            return RiptidePacketArgumentBuilder.Result.help(help(packetClass));
         }
      }
   }

   public static String help(Class<? extends Packet<?>> packetClass) {
      if (packetClass == null) {
         return "";
      } else {
         RiptidePacketSchemaRegistry.PacketSchema schema = RiptidePacketSchemaRegistry.find(packetClass);
         List<String> fields = new ArrayList<>();
         if (schema != null) {
            for (RiptidePacketSchemaRegistry.FieldSchema field : schema.fields()) {
               fields.add(field.name() + "=" + exampleFor(field.javaType(), field.valueKind()) + " (" + shortType(field.javaType(), field.valueKind()) + ")");
            }
         }

         if (fields.isEmpty()) {
            for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
               if (constructor.getParameterCount() != 0) {
                  fields.add("ctor(" + constructor.getParameterCount() + " args)");
               }
            }
         }

         String usage = fields.isEmpty()
            ? ".send " + displayName(packetClass)
            : ".send " + displayName(packetClass) + " " + String.join(" ", compactUsageFields(fields));
         StringBuilder out = new StringBuilder("Usage: ").append(usage);
         if (!fields.isEmpty()) {
            out.append("\nFields: ");
            out.append(String.join(", ", fields));
         }

         List<String> examples = examplesFor(packetClass);
         if (!examples.isEmpty()) {
            out.append("\nExamples: ");
            out.append(String.join(" | ", examples));
         }

         out.append("\nRaw: rawHex=<packet body>, base64=<packet body>, or packetHex=<packet id + body> decodes through Minecraft's own STREAM_CODEC.");
         out.append("\nTip: Tab completes fields and enum values. Use current for live container/player defaults where offered.");
         return out.toString();
      }
   }

   public static CompletableFuture<Suggestions> suggest(Class<? extends Packet<?>> packetClass, SuggestionsBuilder builder) {
      if (packetClass == null) {
         return builder.buildFuture();
      } else {
         String remaining = builder.getRemaining();
         int tokenStart = lastTokenStart(remaining);
         String current = remaining.substring(tokenStart);
         SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenStart);
         int eq = current.indexOf(61);
         if (eq >= 0) {
            String field = current.substring(0, eq);
            String valuePrefix = current.substring(eq + 1);

            for (String value : valueSuggestions(packetClass, field)) {
               if (value.toLowerCase(Locale.ROOT).startsWith(valuePrefix.toLowerCase(Locale.ROOT))) {
                  tokenBuilder.suggest(field + "=" + quoteSuggestionValue(value));
               }
            }

            return tokenBuilder.buildFuture();
         } else {
            Set<String> used = parsedKeysBeforeCurrent(remaining, tokenStart);
            String currentLower = current.toLowerCase(Locale.ROOT);

            for (String field : suggestionFields(packetClass)) {
               if (!used.contains(normalizeName(field))) {
                  String suggestion = field + "=" + suggestionExample(packetClass, field);
                  if (suggestion.toLowerCase(Locale.ROOT).startsWith(currentLower) || field.toLowerCase(Locale.ROOT).startsWith(currentLower)) {
                     tokenBuilder.suggest(suggestion);
                  }
               }
            }

            for (String control : List.of("dry", "preview", "noSend")) {
               if (control.toLowerCase(Locale.ROOT).startsWith(currentLower)) {
                  tokenBuilder.suggest(control);
               }
            }

            if ("help".startsWith(currentLower)) {
               tokenBuilder.suggest("help");
            }

            return tokenBuilder.buildFuture();
         }
      }
   }

   private static Packet<?> tryNoArg(Class<? extends Packet<?>> packetClass) {
      for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
         if (constructor.getParameterCount() == 0) {
            try {
               constructor.setAccessible(true);
               return (Packet<?>)constructor.newInstance();
            } catch (Throwable var6) {
            }
         }
      }

      return null;
   }

   private static RiptidePacketArgumentBuilder.Result tryRawCodec(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
      String bodyHex = firstArg(args, RAW_BODY_ARG_NAMES.toArray(new String[0]));
      String fullPacketHex = firstArg(args, RAW_PACKET_ARG_NAMES.toArray(new String[0]));
      String bodyBase64 = firstArg(args, RAW_BASE64_ARG_NAMES.toArray(new String[0]));
      int provided = (bodyHex == null ? 0 : 1) + (fullPacketHex == null ? 0 : 1) + (bodyBase64 == null ? 0 : 1);
      if (provided == 0) {
         return null;
      } else if (provided > 1) {
         return RiptidePacketArgumentBuilder.Result.error("Use only one raw packet source: rawHex, packetHex, or base64.");
      } else {
         try {
            requireOnly(args, RAW_ARG_NAMES.toArray(new String[0]));
            boolean fullPacket = fullPacketHex != null;
            byte[] sourceBytes;
            if (bodyBase64 != null) {
               sourceBytes = Base64.getDecoder().decode(bodyBase64.trim());
            } else {
               sourceBytes = parseBytes(fullPacket ? fullPacketHex : bodyHex);
            }

            Packet<?> packet = fullPacket
               ? RiptidePacketCodecIo.decodeFullPacket(packetClass, sourceBytes, null).packet()
               : RiptidePacketCodecIo.decodeBody(packetClass, sourceBytes, null);
            return RiptidePacketArgumentBuilder.Result.ok(packet, fullPacket ? "minecraft packet STREAM_CODEC" : "minecraft body STREAM_CODEC");
         } catch (IllegalArgumentException var9) {
            return RiptidePacketArgumentBuilder.Result.error(var9.getMessage());
         } catch (Throwable var10) {
            return RiptidePacketArgumentBuilder.Result.error("Minecraft codec decode failed: " + safeMessage(var10));
         }
      }
   }

   private static RiptidePacketArgumentBuilder.Result trySpecial(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
      String name = packetClass.getName();

      try {
         if (packetClass == ServerboundContainerClosePacket.class) {
            requireOnly(args, "containerId", "id");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundContainerClosePacket(intArg(args, RiptidePacketArgumentBuilder::currentContainerId, "containerId", "id")), "typed constructor"
            );
         } else if (packetClass == ServerboundSetCarriedItemPacket.class) {
            requireOnly(args, "slot");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundSetCarriedItemPacket(intArg(args, RiptidePacketArgumentBuilder::currentSelectedSlot, "slot")), "typed constructor"
            );
         } else if (packetClass == ServerboundSelectBundleItemPacket.class) {
            requireOnly(args, "slot", "selectedItemIndex", "index");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundSelectBundleItemPacket(intArg(args, "slot"), intArg(args, "selectedItemIndex", "index")), "typed constructor"
            );
         } else if (packetClass == ServerboundSwingPacket.class) {
            requireOnly(args, "hand");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundSwingPacket(enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand")), "typed constructor"
            );
         } else if (packetClass == ServerboundUseItemPacket.class) {
            requireOnly(args, "hand", "sequence", "yRot", "xRot");
            InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
            int sequence = intArg(args, 0, "sequence");
            float yRot = floatArg(args, currentYRot(), "yRot", "yaw");
            float xRot = floatArg(args, currentXRot(), "xRot", "pitch");
            return RiptidePacketArgumentBuilder.Result.ok(new ServerboundUseItemPacket(hand, sequence, yRot, xRot), "typed constructor");
         } else if (packetClass == ServerboundUseItemOnPacket.class) {
            requireOnly(args, "hand", "sequence", "pos", "blockPos", "x", "y", "z", "direction", "dir", "face", "hit", "hitX", "hitY", "hitZ", "inside");
            InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
            BlockHitResult hitResult = blockHitArg(args);
            int sequence = intArg(args, 0, "sequence");
            return RiptidePacketArgumentBuilder.Result.ok(new ServerboundUseItemOnPacket(hand, hitResult, sequence), "typed constructor");
         } else if (packetClass == ServerboundClientCommandPacket.class) {
            requireOnly(args, "action");
            return RiptidePacketArgumentBuilder.Result.ok(new ServerboundClientCommandPacket(enumArg(Action.class, args, "action")), "typed constructor");
         } else if (packetClass == ServerboundChatCommandPacket.class) {
            requireOnly(args, "command", "cmd");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundChatCommandPacket(normalizeChatCommand(requireString(args, firstArg(args, "command") != null ? "command" : "cmd"))),
               "typed constructor"
            );
         } else if (packetClass == ServerboundChatPacket.class) {
            requireOnly(args, "message", "timeStamp", "timestamp", "time", "salt", "signature", "lastSeenMessages", "lastSeen");
            String message = requireString(args, "message");
            Instant time = instantArg(args, Instant.now(), "timeStamp", "timestamp", "time");
            long salt = longArg(args, 0L, "salt");
            MessageSignature signature = parseMessageSignature(firstArg(args, "signature"));
            Update lastSeen = parseLastSeenUpdate(firstArg(args, "lastSeenMessages", "lastSeen"));
            return RiptidePacketArgumentBuilder.Result.ok(new ServerboundChatPacket(message, time, salt, signature, lastSeen), "typed constructor");
         } else if (packetClass == ServerboundChatCommandSignedPacket.class) {
            requireOnly(args, "command", "timeStamp", "timestamp", "time", "salt", "argumentSignatures", "signatures", "lastSeenMessages", "lastSeen");
            String command = requireString(args, "command");
            Instant time = instantArg(args, Instant.now(), "timeStamp", "timestamp", "time");
            long salt = longArg(args, 0L, "salt");
            ArgumentSignatures signatures = parseArgumentSignatures(firstArg(args, "argumentSignatures", "signatures"));
            Update lastSeen = parseLastSeenUpdate(firstArg(args, "lastSeenMessages", "lastSeen"));
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundChatCommandSignedPacket(command, time, salt, signatures, lastSeen), "typed constructor"
            );
         } else if (packetClass == ServerboundChatSessionUpdatePacket.class) {
            requireOnly(args, "current", "default");
            Data data = currentChatSessionData();
            if (data == null) {
               throw new IllegalArgumentException("No current local chat session is available.");
            } else {
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundChatSessionUpdatePacket(data), "current chat session");
            }
         } else if (packetClass == ServerboundClientInformationPacket.class) {
            requireOnly(args, "default", "information");
            return RiptidePacketArgumentBuilder.Result.ok(
               new ServerboundClientInformationPacket(ClientInformation.createDefault()), "current client information"
            );
         } else if (packetClass != ServerboundCustomPayloadPacket.class) {
            if (packetClass == ServerboundCustomQueryAnswerPacket.class) {
               requireOnly(args, "transactionId", "id", "payload");
               int id = intArg(args, "transactionId", "id");
               String payload = firstArg(args, "payload");
               if (payload != null && !isNone(payload)) {
                  return normalizeName(payload).equals("discarded")
                     ? RiptidePacketArgumentBuilder.Result.ok(
                        new ServerboundCustomQueryAnswerPacket(id, DiscardedQueryAnswerPayload.INSTANCE), "typed constructor"
                     )
                     : RiptidePacketArgumentBuilder.Result.error("Login custom query answers support payload=empty/null/discarded only from .send.");
               } else {
                  return RiptidePacketArgumentBuilder.Result.ok(new ServerboundCustomQueryAnswerPacket(id, null), "typed constructor");
               }
            } else if (packetClass == ServerboundEditBookPacket.class) {
               requireOnly(args, "slot", "pages", "page", "title");
               int slot = intArg(args, "slot");
               String pagesValue = firstArg(args, "pages", "page");
               if (pagesValue == null) {
                  throw new IllegalArgumentException("Missing required argument: pages");
               } else {
                  Optional<String> title = optionalString(firstArg(args, "title"));
                  return RiptidePacketArgumentBuilder.Result.ok(
                     new ServerboundEditBookPacket(slot, splitList(pagesValue, "\\|", 100), title), "typed constructor"
                  );
               }
            } else if (packetClass == ServerboundSignUpdatePacket.class) {
               requireOnly(args, "pos", "blockPos", "x", "y", "z", "front", "isFrontText", "line0", "line1", "line2", "line3", "l0", "l1", "l2", "l3", "lines");
               BlockPos pos = blockPosArg(args);
               boolean front = boolArg(args, true, "front", "isFrontText");
               String[] lines = lines4(args);
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundSignUpdatePacket(pos, front, lines[0], lines[1], lines[2], lines[3]), "typed constructor"
               );
            } else if (packetClass == ServerboundInteractPacket.class) {
               requireOnly(args, "entityId", "id", "entity", "hand", "location", "pos", "x", "y", "z", "secondary", "usingSecondaryAction");
               int entityId = intArg(args, RiptidePacketArgumentBuilder::currentEntityId, "entityId", "id", "entity");
               InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
               Vec3 location = firstArg(args, "location", "pos") != null
                  ? parseVec3(firstArg(args, "location", "pos"))
                  : currentEntityHitLocation(new Vec3(doubleArg(args, 0.0, "x"), doubleArg(args, 0.0, "y"), doubleArg(args, 0.0, "z")));
               boolean secondary = boolArg(args, false, "secondary", "usingSecondaryAction");
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundInteractPacket(entityId, hand, location, secondary), "typed constructor");
            } else if (packetClass == ServerboundContainerClickPacket.class) {
               requireOnly(
                  args,
                  "containerId",
                  "id",
                  "stateId",
                  "slotNum",
                  "slot",
                  "buttonNum",
                  "button",
                  "containerInput",
                  "input",
                  "click",
                  "changedSlots",
                  "changed",
                  "carriedItem",
                  "carried"
               );
               int containerId = intArg(args, RiptidePacketArgumentBuilder::currentContainerId, "containerId", "id");
               int stateId = intArg(args, RiptidePacketArgumentBuilder::currentContainerStateId, "stateId");
               short slot = (short)intArg(args, "slotNum", "slot");
               byte button = (byte)intArg(args, 0, "buttonNum", "button");
               ContainerInput input = enumArg(ContainerInput.class, args, "containerInput", "input", "click");
               Int2ObjectMap<HashedStack> changed = parseHashedSlotMap(firstArg(args, "changedSlots", "changed"));
               HashedStack carried = parseHashedStack(firstArg(args, "carriedItem", "carried"));
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundContainerClickPacket(containerId, stateId, slot, button, input, changed, carried), "typed constructor"
               );
            } else if (packetClass == ServerboundContainerButtonClickPacket.class) {
               requireOnly(args, "containerId", "id", "buttonId", "button");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundContainerButtonClickPacket(
                     intArg(args, RiptidePacketArgumentBuilder::currentContainerId, "containerId", "id"), intArg(args, "buttonId", "button")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == ServerboundContainerSlotStateChangedPacket.class) {
               requireOnly(args, "slotId", "slot", "containerId", "id", "newState", "state", "enabled");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundContainerSlotStateChangedPacket(
                     intArg(args, "slotId", "slot"),
                     intArg(args, RiptidePacketArgumentBuilder::currentContainerId, "containerId", "id"),
                     boolArg(args, false, "newState", "state", "enabled")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == ServerboundSetCreativeModeSlotPacket.class) {
               requireOnly(args, "slotNum", "slot", "itemStack", "item");
               int slot = intArg(args, "slotNum", "slot");
               ItemStack stack = parseItemStack(firstArg(args, "itemStack", "item"));
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundSetCreativeModeSlotPacket(slot, stack), "typed constructor");
            } else if (packetClass == ServerboundPlayerCommandPacket.class) {
               requireOnly(args, "action", "data");
               if (MC.player == null) {
                  throw new IllegalArgumentException("Player is required for this packet.");
               } else {
                  net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action = enumArg(
                     net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.class, args, "action"
                  );
                  int data = intArg(args, 0, "data");
                  return RiptidePacketArgumentBuilder.Result.ok(new ServerboundPlayerCommandPacket(MC.player, action, data), "current player constructor");
               }
            } else if (packetClass == ServerboundPlayerInputPacket.class) {
               requireOnly(args, "forward", "backward", "left", "right", "jump", "shift", "sneak", "sprint", "empty");
               return boolArg(args, false, "empty")
                  ? RiptidePacketArgumentBuilder.Result.ok(new ServerboundPlayerInputPacket(Input.EMPTY), "typed constructor")
                  : RiptidePacketArgumentBuilder.Result.ok(
                     new ServerboundPlayerInputPacket(
                        new Input(
                           boolArg(args, false, "forward"),
                           boolArg(args, false, "backward"),
                           boolArg(args, false, "left"),
                           boolArg(args, false, "right"),
                           boolArg(args, false, "jump"),
                           boolArg(args, false, "shift", "sneak"),
                           boolArg(args, false, "sprint")
                        )
                     ),
                     "typed constructor"
                  );
            } else if (packetClass == ServerboundPaddleBoatPacket.class) {
               requireOnly(args, "left", "right");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundPaddleBoatPacket(boolArg(args, false, "left"), boolArg(args, false, "right")), "typed constructor"
               );
            } else if (packetClass == ServerboundSetCommandBlockPacket.class) {
               requireOnly(args, "pos", "blockPos", "x", "y", "z", "command", "mode", "trackOutput", "conditional", "automatic");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundSetCommandBlockPacket(
                     blockPosArg(args),
                     requireString(args, "command"),
                     enumArg(Mode.class, args, "mode"),
                     boolArg(args, false, "trackOutput"),
                     boolArg(args, false, "conditional"),
                     boolArg(args, false, "automatic")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == ServerboundSetGameRulePacket.class) {
               requireOnly(args, "entries", "rules", "rule", "value");
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundSetGameRulePacket(gameRuleEntries(args)), "typed constructor");
            } else if (packetClass == ServerboundSeenAdvancementsPacket.class) {
               requireOnly(args, "action", "tab");
               net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action action = enumArg(
                  net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action.class, args, "action"
               );
               Identifier tab = action == net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action.OPENED_TAB
                  ? Identifier.parse(requireString(args, "tab"))
                  : null;
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundSeenAdvancementsPacket(action, tab), "typed constructor");
            } else if (packetClass == ServerboundTestInstanceBlockActionPacket.class) {
               requireOnly(args, "pos", "blockPos", "x", "y", "z", "action", "size", "rotation", "ignoreEntities", "ignore");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new ServerboundTestInstanceBlockActionPacket(
                     blockPosArg(args),
                     enumArg(net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket.Action.class, args, "action"),
                     Optional.empty(),
                     vec3iArg(args, Vec3i.ZERO, "size"),
                     enumArgOrDefault(Rotation.class, args, Rotation.NONE, "rotation"),
                     boolArg(args, false, "ignoreEntities", "ignore")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == ServerboundPlayerActionPacket.class) {
               requireOnly(args, "action", "pos", "x", "y", "z", "direction", "sequence");
               net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action = enumArg(
                  net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.class, args, "action"
               );
               BlockPos pos = blockPosArg(args);
               Direction direction = enumArg(Direction.class, args, "direction", "dir", "face");
               int sequence = intArg(args, 0, "sequence");
               return RiptidePacketArgumentBuilder.Result.ok(new ServerboundPlayerActionPacket(action, pos, direction, sequence), "typed constructor");
            } else if (packetClass == Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) {
               requireOnly(args, "x", "y", "z", "onGround", "ground", "horizontalCollision", "collision");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new Pos(
                     doubleArg(args, currentX(), "x"),
                     doubleArg(args, currentY(), "y"),
                     doubleArg(args, currentZ(), "z"),
                     boolArg(args, currentOnGround(), "onGround", "ground"),
                     boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) {
               requireOnly(args, "x", "y", "z", "yRot", "yaw", "xRot", "pitch", "onGround", "ground", "horizontalCollision", "collision");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new PosRot(
                     doubleArg(args, currentX(), "x"),
                     doubleArg(args, currentY(), "y"),
                     doubleArg(args, currentZ(), "z"),
                     floatArg(args, currentYRot(), "yRot", "yaw"),
                     floatArg(args, currentXRot(), "xRot", "pitch"),
                     boolArg(args, currentOnGround(), "onGround", "ground"),
                     boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) {
               requireOnly(args, "yRot", "yaw", "xRot", "pitch", "onGround", "ground", "horizontalCollision", "collision");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new Rot(
                     floatArg(args, currentYRot(), "yRot", "yaw"),
                     floatArg(args, currentXRot(), "xRot", "pitch"),
                     boolArg(args, currentOnGround(), "onGround", "ground"),
                     boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == StatusOnly.class || name.endsWith("ServerboundMovePlayerPacket$StatusOnly")) {
               requireOnly(args, "onGround", "ground", "horizontalCollision", "collision");
               return RiptidePacketArgumentBuilder.Result.ok(
                  new StatusOnly(
                     boolArg(args, currentOnGround(), "onGround", "ground"), boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                  ),
                  "typed constructor"
               );
            } else if (packetClass == ServerboundMoveVehiclePacket.class) {
               requireOnly(args, "position", "pos", "x", "y", "z", "yRot", "yaw", "xRot", "pitch", "onGround", "ground");
               if (args.isEmpty() && MC.player != null && MC.player.getVehicle() != null) {
                  return RiptidePacketArgumentBuilder.Result.ok(ServerboundMoveVehiclePacket.fromEntity(MC.player.getVehicle()), "current vehicle");
               } else {
                  Vec3 position = firstArg(args, "position", "pos") != null
                     ? parseVec3(firstArg(args, "position", "pos"))
                     : new Vec3(doubleArg(args, currentX(), "x"), doubleArg(args, currentY(), "y"), doubleArg(args, currentZ(), "z"));
                  return RiptidePacketArgumentBuilder.Result.ok(
                     new ServerboundMoveVehiclePacket(
                        position,
                        floatArg(args, currentYRot(), "yRot", "yaw"),
                        floatArg(args, currentXRot(), "xRot", "pitch"),
                        boolArg(args, currentOnGround(), "onGround", "ground")
                     ),
                     "typed constructor"
                  );
               }
            } else {
               return null;
            }
         } else {
            requireOnly(args, "brand", "payload", "channel");
            String channel = firstArg(args, "channel");
            String brand = firstArg(args, "brand", "payload");
            return brand == null || channel != null && !normalizeIdentifier(channel).equals("minecraft:brand") && !normalizeIdentifier(channel).equals("brand")
               ? RiptidePacketArgumentBuilder.Result.error(
                  "Use Packet Logger > Payload Sender for arbitrary plugin payload bytes. .send can build minecraft:brand only."
               )
               : RiptidePacketArgumentBuilder.Result.ok(new ServerboundCustomPayloadPacket(new BrandPayload(brand)), "brand payload");
         }
      } catch (IllegalArgumentException var10) {
         return RiptidePacketArgumentBuilder.Result.error(var10.getMessage());
      } catch (Throwable var11) {
         return RiptidePacketArgumentBuilder.Result.error("Special builder failed: " + safeMessage(var11));
      }
   }

   private static RiptidePacketArgumentBuilder.Result tryRecord(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
      RecordComponent[] components = packetClass.getRecordComponents();
      if (components != null && components.length != 0) {
         Constructor<?> constructor;
         try {
            Class<?>[] types = new Class[components.length];

            for (int i = 0; i < components.length; i++) {
               types[i] = components[i].getType();
            }

            constructor = packetClass.getDeclaredConstructor(types);
         } catch (Throwable var8) {
            return null;
         }

         try {
            Object[] values = new Object[components.length];

            for (int i = 0; i < components.length; i++) {
               values[i] = parseNamedValue(args, components[i].getName(), components[i].getType(), components[i].getGenericType());
            }

            ensureNoUnknown(args, expandableAllowedNames(componentNames(components)));
            constructor.setAccessible(true);
            return RiptidePacketArgumentBuilder.Result.ok((Packet<?>)constructor.newInstance(values), "record constructor");
         } catch (IllegalArgumentException var6) {
            return RiptidePacketArgumentBuilder.Result.error(var6.getMessage());
         } catch (Throwable var7) {
            return RiptidePacketArgumentBuilder.Result.error("Record builder failed: " + safeMessage(var7));
         }
      } else {
         return null;
      }
   }

   private static RiptidePacketArgumentBuilder.Result trySchemaConstructor(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
      RiptidePacketSchemaRegistry.PacketSchema schema = RiptidePacketSchemaRegistry.find(packetClass);
      if (schema != null && !schema.fields().isEmpty()) {
         String firstError = null;

         for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == schema.fields().size() && !isCodecConstructor(constructor)) {
               try {
                  Object[] values = new Object[schema.fields().size()];
                  Class<?>[] parameterTypes = constructor.getParameterTypes();
                  List<String> names = new ArrayList<>();

                  for (int i = 0; i < schema.fields().size(); i++) {
                     String fieldName = schema.fields().get(i).name();
                     names.add(fieldName);
                     values[i] = parseNamedValue(args, fieldName, parameterTypes[i], parameterTypes[i]);
                  }

                  ensureNoUnknown(args, expandableAllowedNames(names));
                  constructor.setAccessible(true);
                  return RiptidePacketArgumentBuilder.Result.ok((Packet<?>)constructor.newInstance(values), "schema constructor");
               } catch (IllegalArgumentException var13) {
                  if (firstError == null) {
                     firstError = var13.getMessage();
                  }
               } catch (Throwable var14) {
               }
            }
         }

         return firstError == null ? null : RiptidePacketArgumentBuilder.Result.error(firstError);
      } else {
         return null;
      }
   }

   private static boolean isCodecConstructor(Constructor<?> constructor) {
      for (Class<?> type : constructor.getParameterTypes()) {
         String name = type.getName();
         if (name.contains("ByteBuf")) {
            return true;
         }
      }

      return false;
   }

   private static Map<String, String> parseArgs(String rawArgs) {
      Map<String, String> out = new LinkedHashMap<>();
      if (rawArgs != null && !rawArgs.isBlank()) {
         for (String token : tokenize(rawArgs)) {
            if (!token.isBlank()) {
               int eq = token.indexOf(61);
               if (eq < 0) {
                  String key = normalizeName(token);
                  out.put(key, "true");
               } else {
                  String key = normalizeName(token.substring(0, eq));
                  String value = token.substring(eq + 1);
                  if (key.isBlank()) {
                     throw new IllegalArgumentException("Blank argument name in: " + token);
                  }

                  out.put(key, value);
               }
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private static List<String> tokenize(String text) {
      List<String> out = new ArrayList<>();
      StringBuilder token = new StringBuilder();
      boolean quoted = false;
      char quote = 0;
      boolean escaped = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (escaped) {
            token.append(c);
            escaped = false;
         } else if (c == '\\') {
            escaped = true;
         } else if (quoted) {
            if (c == quote) {
               quoted = false;
            } else {
               token.append(c);
            }
         } else if (c == '"' || c == '\'') {
            quoted = true;
            quote = c;
         } else if (Character.isWhitespace(c)) {
            if (!token.isEmpty()) {
               out.add(token.toString());
               token.setLength(0);
            }
         } else {
            token.append(c);
         }
      }

      if (quoted) {
         throw new IllegalArgumentException("Unclosed quote in arguments.");
      } else {
         if (!token.isEmpty()) {
            out.add(token.toString());
         }

         return out;
      }
   }

   private static String joinTokens(List<String> tokens) {
      List<String> out = new ArrayList<>();

      for (String token : tokens) {
         out.add(quoteToken(token));
      }

      return String.join(" ", out);
   }

   private static String quoteToken(String token) {
      if (token == null) {
         return "";
      } else {
         boolean needsQuote = token.isBlank();

         for (int i = 0; i < token.length() && !needsQuote; i++) {
            char c = token.charAt(i);
            needsQuote = Character.isWhitespace(c) || c == '"' || c == '\'';
         }

         return !needsQuote ? token : "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
      }
   }

   private static int lastTokenStart(String text) {
      boolean quoted = false;
      char quote = 0;
      boolean escaped = false;
      int start = 0;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (escaped) {
            escaped = false;
         } else if (c == '\\') {
            escaped = true;
         } else if (quoted) {
            if (c == quote) {
               quoted = false;
            }
         } else if (c == '"' || c == '\'') {
            quoted = true;
            quote = c;
         } else if (Character.isWhitespace(c)) {
            start = i + 1;
         }
      }

      return start;
   }

   private static Set<String> parsedKeysBeforeCurrent(String text, int tokenStart) {
      Set<String> out = new HashSet<>();
      String prefix = tokenStart <= 0 ? "" : text.substring(0, tokenStart);

      try {
         out.addAll(parseArgs(prefix).keySet());
      } catch (IllegalArgumentException var5) {
      }

      return out;
   }

   private static List<String> suggestionFields(Class<? extends Packet<?>> packetClass) {
      List<String> fields = specialSuggestionFields(packetClass);
      if (!fields.isEmpty()) {
         return withRawSuggestions(fields);
      } else {
         RiptidePacketSchemaRegistry.PacketSchema schema = RiptidePacketSchemaRegistry.find(packetClass);
         if (schema != null && !schema.fields().isEmpty()) {
            List<String> out = new ArrayList<>();

            for (RiptidePacketSchemaRegistry.FieldSchema field : schema.fields()) {
               out.add(field.name());
            }

            return withRawSuggestions(out);
         } else {
            return withRawSuggestions(List.of("help"));
         }
      }
   }

   private static List<String> withRawSuggestions(List<String> fields) {
      List<String> out = new ArrayList<>(fields);
      out.add("rawHex");
      out.add("packetHex");
      out.add("base64");
      return out;
   }

   private static List<String> specialSuggestionFields(Class<? extends Packet<?>> packetClass) {
      String name = packetClass.getName();
      if (packetClass == ServerboundUseItemOnPacket.class) {
         return List.of("hand", "pos", "direction", "sequence", "hit", "inside");
      } else if (packetClass == ServerboundContainerClickPacket.class) {
         return List.of("slot", "input", "button", "containerId", "stateId", "changed", "carried");
      } else if (packetClass == ServerboundSignUpdatePacket.class) {
         return List.of("pos", "front", "lines", "line0", "line1", "line2", "line3");
      } else if (packetClass == ServerboundEditBookPacket.class) {
         return List.of("slot", "pages", "title");
      } else if (packetClass == ServerboundSetGameRulePacket.class) {
         return List.of("rule", "value", "entries");
      } else if (packetClass == ServerboundSetCreativeModeSlotPacket.class) {
         return List.of("slot", "item");
      } else if (packetClass == ServerboundPlayerInputPacket.class) {
         return List.of("forward", "backward", "left", "right", "jump", "shift", "sprint");
      } else if (packetClass == ServerboundInteractPacket.class) {
         return List.of("entityId", "hand", "location", "secondary");
      } else if (packetClass == ServerboundTestInstanceBlockActionPacket.class) {
         return List.of("pos", "action", "size", "rotation", "ignore");
      } else if (packetClass == ServerboundChatCommandPacket.class) {
         return List.of("command");
      } else if (packetClass == ServerboundChatPacket.class) {
         return List.of("message", "time", "salt", "signature", "lastSeen");
      } else if (packetClass == ServerboundChatCommandSignedPacket.class) {
         return List.of("command", "time", "salt", "signatures", "lastSeen");
      } else if (packetClass == ServerboundCustomPayloadPacket.class) {
         return List.of("brand", "channel");
      } else if (packetClass == ServerboundCustomQueryAnswerPacket.class) {
         return List.of("transactionId", "payload");
      } else if (packetClass == Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) {
         return List.of("x", "y", "z", "onGround", "collision");
      } else if (packetClass == PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) {
         return List.of("x", "y", "z", "yaw", "pitch", "onGround", "collision");
      } else if (packetClass == Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) {
         return List.of("yaw", "pitch", "onGround", "collision");
      } else {
         return packetClass != StatusOnly.class && !name.endsWith("ServerboundMovePlayerPacket$StatusOnly") ? List.of() : List.of("onGround", "collision");
      }
   }

   private static List<String> valueSuggestions(Class<? extends Packet<?>> packetClass, String rawField) {
      String field = normalizeName(rawField);
      if (field.equals("rawhex") || field.equals("raw") || field.equals("body") || field.equals("bodyhex") || field.equals("hex") || field.equals("bytes")) {
         return List.of("hex:010203", "utf8:text");
      } else if (field.equals("packethex")
         || field.equals("packet")
         || field.equals("full")
         || field.equals("fullhex")
         || field.equals("plaintext")
         || field.equals("packetbytes")) {
         return List.of("hex:010203");
      } else if (!field.equals("base64") && !field.equals("b64") && !field.equals("bodybase64")) {
         Class<? extends Enum<?>> enumClass = enumSuggestionType(packetClass, field);
         if (enumClass != null) {
            List<String> values = new ArrayList<>();

            for (Enum<?> constant : enumClass.getEnumConstants()) {
               values.add(constant.name());
            }

            return values;
         } else if (field.equals("hand")) {
            return List.of("MAIN_HAND", "OFF_HAND");
         } else if (field.equals("direction") || field.equals("dir") || field.equals("face")) {
            return List.of("UP", "DOWN", "NORTH", "SOUTH", "WEST", "EAST");
         } else if (field.equals("input") || field.equals("click") || field.equals("containerinput")) {
            return List.of("PICKUP", "QUICK_MOVE", "SWAP", "THROW", "QUICK_CRAFT", "PICKUP_ALL");
         } else if (field.equals("containerid") || field.equals("id") || field.equals("stateid")) {
            return List.of("current", "0");
         } else if (field.equals("slot") || field.equals("slotnum") || field.equals("slotid")) {
            return List.of("current", "0");
         } else if (field.equals("x")) {
            return List.of(formatDouble(currentX()));
         } else if (field.equals("y")) {
            return List.of(formatDouble(currentY()));
         } else if (field.equals("z")) {
            return List.of(formatDouble(currentZ()));
         } else if (field.equals("yaw") || field.equals("yrot")) {
            return List.of(formatFloat(currentYRot()));
         } else if (field.equals("pitch") || field.equals("xrot")) {
            return List.of(formatFloat(currentXRot()));
         } else if (field.equals("pos") || field.equals("blockpos") || field.equals("hit") || field.equals("size")) {
            return List.of("0,64,0");
         } else if (field.equals("item") || field.equals("itemstack") || field.equals("carried")) {
            return List.of("empty", "held", "offhand", "cursor");
         } else if (field.equals("payload")) {
            return List.of("empty", "discarded");
         } else if (field.equals("time") || field.equals("timestamp") || field.equals("timestamp")) {
            return List.of("now");
         } else if (field.equals("signature") || field.equals("signatures") || field.equals("lastseen")) {
            return List.of("empty");
         } else if (field.equals("brand")) {
            return List.of("RIPTIDE");
         } else if (field.equals("channel")) {
            return List.of("minecraft:brand");
         } else {
            return !field.equals("command") && !field.equals("cmd") ? List.of("true", "false", "0", "empty") : List.of("help", "shop");
         }
      } else {
         return List.of("AAECAw==");
      }
   }

   private static Class<? extends Enum<?>> enumSuggestionType(Class<? extends Packet<?>> packetClass, String field) {
      if (field.equals("action")) {
         if (packetClass == ServerboundResourcePackPacket.class) {
            return net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.class;
         }

         if (packetClass == ServerboundClientCommandPacket.class) {
            return Action.class;
         }

         if (packetClass == ServerboundPlayerActionPacket.class) {
            return net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.class;
         }

         if (packetClass == ServerboundPlayerCommandPacket.class) {
            return net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.class;
         }

         if (packetClass == ServerboundSeenAdvancementsPacket.class) {
            return net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action.class;
         }

         if (packetClass == ServerboundTestInstanceBlockActionPacket.class) {
            return net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket.Action.class;
         }
      }

      if (field.equals("mode") && packetClass == ServerboundSetCommandBlockPacket.class) {
         return Mode.class;
      } else if (field.equals("rotation")) {
         return Rotation.class;
      } else if (field.equals("hand")) {
         return InteractionHand.class;
      } else if (!field.equals("direction") && !field.equals("dir") && !field.equals("face")) {
         if (!field.equals("input") && !field.equals("click") && !field.equals("containerinput")) {
            RiptidePacketSchemaRegistry.PacketSchema schema = RiptidePacketSchemaRegistry.find(packetClass);
            if (schema == null) {
               return null;
            } else {
               for (RiptidePacketSchemaRegistry.FieldSchema schemaField : schema.fields()) {
                  if (normalizeName(schemaField.name()).equals(field)) {
                     try {
                        Class<?> cls = Class.forName(enumClassName(schemaField.javaType()));
                        return (Class<? extends Enum<?>>)(cls.isEnum() ? cls : null);
                     } catch (Throwable var6) {
                        return null;
                     }
                  }
               }

               return null;
            }
         } else {
            return ContainerInput.class;
         }
      } else {
         return Direction.class;
      }
   }

   private static String enumClassName(String javaType) {
      if (javaType == null) {
         return "";
      } else if (javaType.indexOf(46) >= 0) {
         return javaType;
      } else {
         return switch (javaType) {
            case "Difficulty" -> "net.minecraft.world.Difficulty";
            case "GameType" -> "net.minecraft.world.level.GameType";
            case "RecipeBookType" -> "net.minecraft.world.inventory.RecipeBookType";
            case "ContainerInput" -> "net.minecraft.world.inventory.ContainerInput";
            case "InteractionHand" -> "net.minecraft.world.InteractionHand";
            case "Direction" -> "net.minecraft.core.Direction";
            case "Mirror" -> "net.minecraft.world.level.block.Mirror";
            case "Rotation" -> "net.minecraft.world.level.block.Rotation";
            case "StructureMode" -> "net.minecraft.world.level.block.state.properties.StructureMode";
            case "StructureBlockEntity.UpdateType" -> "net.minecraft.world.level.block.entity.StructureBlockEntity$UpdateType";
            case "CommandBlockEntity.Mode" -> "net.minecraft.world.level.block.entity.CommandBlockEntity$Mode";
            case "JigsawBlockEntity.JointType" -> "net.minecraft.world.level.block.entity.JigsawBlockEntity$JointType";
            case "TestBlockMode" -> "net.minecraft.world.level.block.state.properties.TestBlockMode";
            case "ServerboundResourcePackPacket.Action" -> "net.minecraft.network.protocol.common.ServerboundResourcePackPacket$Action";
            case "ServerboundClientCommandPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundClientCommandPacket$Action";
            case "ServerboundPlayerActionPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action";
            case "ServerboundPlayerCommandPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket$Action";
            case "ServerboundSeenAdvancementsPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket$Action";
            case "ServerboundTestInstanceBlockActionPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket$Action";
            default -> javaType.replace('.', '$');
         };
      }
   }

   private static String suggestionExample(Class<? extends Packet<?>> packetClass, String field) {
      List<String> values = valueSuggestions(packetClass, field);
      return !values.isEmpty() ? quoteSuggestionValue(values.get(0)) : "0";
   }

   private static String quoteSuggestionValue(String value) {
      if (value == null) {
         return "";
      } else {
         return value.indexOf(32) >= 0 ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
      }
   }

   private static List<String> examplesFor(Class<? extends Packet<?>> packetClass) {
      String prefix = ".send " + displayName(packetClass) + " ";
      String name = packetClass.getName();
      if (packetClass == ServerboundUseItemPacket.class) {
         return List.of(prefix + "hand=MAIN_HAND");
      } else if (packetClass == ServerboundUseItemOnPacket.class) {
         return List.of(prefix + "hand=MAIN_HAND", prefix + "pos=0,64,0 direction=UP");
      } else if (packetClass == ServerboundSwingPacket.class) {
         return List.of(prefix + "hand=MAIN_HAND");
      } else if (packetClass == ServerboundContainerClickPacket.class) {
         return List.of(prefix + "slot=0 input=PICKUP", prefix + "slot=0 input=QUICK_MOVE");
      } else if (packetClass == ServerboundContainerClosePacket.class) {
         return List.of(prefix + "containerId=current");
      } else if (packetClass == ServerboundContainerButtonClickPacket.class) {
         return List.of(prefix + "button=0 containerId=current");
      } else if (packetClass == ServerboundSetCarriedItemPacket.class) {
         return List.of(prefix + "slot=current", prefix + "slot=0");
      } else if (packetClass == Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) {
         return List.of(prefix + "x=current y=current z=current");
      } else if (packetClass == PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) {
         return List.of(prefix + "x=current y=current z=current yaw=current pitch=current");
      } else if (packetClass == Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) {
         return List.of(prefix + "yaw=current pitch=current");
      } else if (packetClass == ServerboundChatCommandPacket.class) {
         return List.of(prefix + "command=shop", prefix + "command=\"/warp spawn\"");
      } else if (packetClass == ServerboundChatPacket.class) {
         return List.of(prefix + "message=\"hello\" dry");
      } else if (packetClass == ServerboundEditBookPacket.class) {
         return List.of(prefix + "slot=0 pages=\"page one|page two\" title=\"title\"");
      } else if (packetClass == ServerboundSignUpdatePacket.class) {
         return List.of(prefix + "pos=0,64,0 lines=\"one|two|three|four\"");
      } else if (packetClass == ServerboundSetCreativeModeSlotPacket.class) {
         return List.of(prefix + "slot=0 item=held", prefix + "slot=0 item=empty");
      } else if (packetClass == ServerboundSetGameRulePacket.class) {
         return List.of(prefix + "rule=doDaylightCycle value=false");
      } else if (packetClass == ServerboundCustomPayloadPacket.class) {
         return List.of(prefix + "brand=vanilla");
      } else {
         return packetClass == ServerboundMoveVehiclePacket.class
            ? List.of(prefix + "dry", prefix + "pos=current yaw=current pitch=current")
            : List.of(prefix + "dry", prefix + "rawHex=hex:00 dry", prefix + "packetHex=hex:0000 dry");
      }
   }

   private static Object parseNamedValue(Map<String, String> args, String fieldName, Class<?> targetType, Type genericType) {
      String value = firstArg(args, aliasNames(fieldName));
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + fieldName);
      } else {
         return parseValue(fieldName, value, targetType, genericType);
      }
   }

   private static Object parseValue(String fieldName, String value, Class<?> targetType, Type genericType) {
      if (isCurrent(value)) {
         return currentValueFor(fieldName, targetType);
      } else if (targetType == String.class) {
         return value;
      } else if (targetType == int.class || targetType == Integer.class) {
         return parseInt(fieldName, value);
      } else if (targetType == short.class || targetType == Short.class) {
         return (short)parseInt(fieldName, value);
      } else if (targetType == byte.class || targetType == Byte.class) {
         return (byte)parseInt(fieldName, value);
      } else if (targetType == long.class || targetType == Long.class) {
         return parseLong(fieldName, value);
      } else if (targetType == float.class || targetType == Float.class) {
         return Float.parseFloat(value);
      } else if (targetType == double.class || targetType == Double.class) {
         return Double.parseDouble(value);
      } else if (targetType == boolean.class || targetType == Boolean.class) {
         return parseBoolean(fieldName, value);
      } else if (targetType.isEnum()) {
         return enumValue(targetType, value);
      } else if (targetType == Identifier.class) {
         return Identifier.parse(value);
      } else if (targetType == BlockPos.class) {
         return parseBlockPos(value);
      } else if (targetType == Vec3i.class) {
         return parseVec3i(value);
      } else if (targetType == Vec3.class) {
         return parseVec3(value);
      } else if (targetType == UUID.class) {
         return UUID.fromString(value);
      } else if (targetType == Instant.class) {
         return parseInstant(value);
      } else if (targetType == byte[].class) {
         return parseBytes(value);
      } else if (targetType == String[].class) {
         return splitList(value, "\\|", 64).toArray(new String[0]);
      } else if (targetType == Optional.class) {
         return parseOptional(fieldName, value, genericType);
      } else if (targetType == List.class) {
         return parseList(fieldName, value, genericType);
      } else if (targetType == Set.class) {
         return parseEmptySet(fieldName, value);
      } else if (targetType == Map.class) {
         return parseEmptyMap(fieldName, value);
      } else if (targetType == Int2ObjectMap.class) {
         return parseHashedSlotMap(value);
      } else if (targetType == HashedStack.class) {
         return parseHashedStack(value);
      } else if (targetType == ItemStack.class) {
         return parseItemStack(value);
      } else if (targetType == Input.class) {
         return parseInput(value);
      } else if (targetType == ClientInformation.class) {
         return ClientInformation.createDefault();
      } else if (targetType == RecipeDisplayId.class) {
         return new RecipeDisplayId(parseInt(fieldName, value));
      } else if (targetType == ArgumentSignatures.class) {
         return parseArgumentSignatures(value);
      } else if (targetType == Update.class) {
         return parseLastSeenUpdate(value);
      } else if (targetType == MessageSignature.class) {
         return parseMessageSignature(value);
      } else {
         throw new IllegalArgumentException(
            "Argument '"
               + fieldName
               + "' uses unsupported type "
               + targetType.getSimpleName()
               + ". Capture/resend or packet macros are still needed for complex packet objects."
         );
      }
   }

   private static Object currentValueFor(String fieldName, Class<?> targetType) {
      String normalized = normalizeName(fieldName);
      if (targetType == int.class || targetType == Integer.class) {
         if (normalized.equals("containerid")) {
            return currentContainerId();
         }

         if (normalized.equals("stateid")) {
            return currentContainerStateId();
         }

         if (normalized.equals("slot") || normalized.equals("slotid") || normalized.equals("slotnum")) {
            return currentSelectedSlot();
         }

         if ((normalized.equals("id") || normalized.equals("entityid")) && MC != null && MC.player != null) {
            return MC.player.getId();
         }
      }

      if ((targetType == short.class || targetType == Short.class)
         && (normalized.equals("slot") || normalized.equals("slotid") || normalized.equals("slotnum"))) {
         return (short)currentSelectedSlot();
      } else if (targetType != byte.class && targetType != Byte.class || !normalized.equals("buttonnum") && !normalized.equals("button")) {
         if (targetType == double.class || targetType == Double.class) {
            if (normalized.equals("x")) {
               return currentX();
            }

            if (normalized.equals("y")) {
               return currentY();
            }

            if (normalized.equals("z")) {
               return currentZ();
            }
         }

         if (targetType == float.class || targetType == Float.class) {
            if (normalized.equals("yrot") || normalized.equals("yaw")) {
               return currentYRot();
            }

            if (normalized.equals("xrot") || normalized.equals("pitch")) {
               return currentXRot();
            }
         }

         if (targetType == boolean.class || targetType == Boolean.class) {
            if (normalized.equals("onground") || normalized.equals("ground")) {
               return currentOnGround();
            }

            if (normalized.equals("horizontalcollision") || normalized.equals("collision")) {
               return currentHorizontalCollision();
            }
         }

         if (targetType == BlockPos.class) {
            BlockHitResult hit = currentBlockHit();
            if (hit != null) {
               return hit.getBlockPos();
            }
         }

         if (targetType == Vec3.class) {
            if (normalized.equals("position") || normalized.equals("pos")) {
               return new Vec3(currentX(), currentY(), currentZ());
            }

            BlockHitResult hit = currentBlockHit();
            if (hit != null) {
               return hit.getLocation();
            }
         }

         if (targetType == ItemStack.class) {
            return parseItemStack("held");
         } else if (targetType == ClientInformation.class) {
            return ClientInformation.createDefault();
         } else if (targetType == Instant.class) {
            return Instant.now();
         } else {
            throw new IllegalArgumentException("current is not available for " + fieldName + " (" + targetType.getSimpleName() + ").");
         }
      } else {
         return (byte)0;
      }
   }

   private static int intArg(Map<String, String> args, String... names) {
      String value = firstArg(args, names);
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + names[0]);
      } else {
         return parseInt(names[0], value);
      }
   }

   private static int intArg(Map<String, String> args, int fallback, String... names) {
      String value = firstArg(args, names);
      return value != null && !isCurrent(value) ? parseInt(names[0], value) : fallback;
   }

   private static int intArg(Map<String, String> args, IntSupplier fallback, String... names) {
      String value = firstArg(args, names);
      return value != null && !isCurrent(value) ? parseInt(names[0], value) : fallback.getAsInt();
   }

   private static long longArg(Map<String, String> args, long fallback, String... names) {
      String value = firstArg(args, names);
      return value == null ? fallback : parseLong(names[0], value);
   }

   private static double doubleArg(Map<String, String> args, String name) {
      String value = firstArg(args, name);
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + name);
      } else {
         return Double.parseDouble(value);
      }
   }

   private static double doubleArg(Map<String, String> args, double fallback, String name) {
      String value = firstArg(args, name);
      return value != null && !isCurrent(value) ? Double.parseDouble(value) : fallback;
   }

   private static float floatArg(Map<String, String> args, String... names) {
      String value = firstArg(args, names);
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + names[0]);
      } else {
         return Float.parseFloat(value);
      }
   }

   private static float floatArg(Map<String, String> args, float fallback, String... names) {
      String value = firstArg(args, names);
      return value != null && !isCurrent(value) ? Float.parseFloat(value) : fallback;
   }

   private static boolean boolArg(Map<String, String> args, boolean fallback, String... names) {
      String value = firstArg(args, names);
      return value != null && !isCurrent(value) ? parseBoolean(names[0], value) : fallback;
   }

   private static <E extends Enum<E>> E enumArg(Class<E> enumClass, Map<String, String> args, String... names) {
      String value = firstArg(args, names);
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + names[0]);
      } else {
         return enumValue(enumClass, value);
      }
   }

   private static <E extends Enum<E>> E enumArgOrDefault(Class<E> enumClass, Map<String, String> args, E fallback, String... names) {
      String value = firstArg(args, names);
      return value == null ? fallback : enumValue(enumClass, value);
   }

   private static BlockPos blockPosArg(Map<String, String> args) {
      String packed = firstArg(args, "pos", "blockPos");
      return packed != null ? parseBlockPos(packed) : new BlockPos(intArg(args, "x"), intArg(args, "y"), intArg(args, "z"));
   }

   private static BlockHitResult blockHitArg(Map<String, String> args) {
      BlockHitResult current = currentBlockHit();
      boolean hasExplicitPos = firstArg(args, "pos", "blockPos") != null
         || firstArg(args, "x") != null
         || firstArg(args, "y") != null
         || firstArg(args, "z") != null;
      boolean hasExplicitDirection = firstArg(args, "direction", "dir", "face") != null;
      boolean hasExplicitHit = firstArg(args, "hit") != null
         || firstArg(args, "hitX") != null
         || firstArg(args, "hitY") != null
         || firstArg(args, "hitZ") != null;
      if (!hasExplicitPos && !hasExplicitDirection && !hasExplicitHit && current != null) {
         return current;
      } else {
         BlockPos pos = hasExplicitPos ? blockPosArg(args) : (current != null ? current.getBlockPos() : null);
         if (pos == null) {
            throw new IllegalArgumentException("Missing required block position. Use pos=x,y,z or look at a block.");
         } else {
            Direction direction = hasExplicitDirection
               ? enumArg(Direction.class, args, "direction", "dir", "face")
               : (current != null ? current.getDirection() : Direction.UP);
            Vec3 hit = vec3Arg(args, current != null ? current.getLocation() : blockCenter(pos), "hit");
            boolean inside = boolArg(args, current != null && current.isInside(), "inside");
            return new BlockHitResult(hit, direction, pos, inside);
         }
      }
   }

   private static Vec3i vec3iArg(Map<String, String> args, Vec3i fallback, String packedName) {
      String packed = firstArg(args, packedName);
      return packed == null ? fallback : parseVec3i(packed);
   }

   private static BlockPos parseBlockPos(String value) {
      if (isCurrent(value)) {
         BlockHitResult hit = currentBlockHit();
         if (hit != null) {
            return hit.getBlockPos();
         } else if (MC != null && MC.player != null) {
            return MC.player.blockPosition();
         } else {
            throw new IllegalArgumentException("current BlockPos needs a player or block hit.");
         }
      } else {
         String[] parts = value.trim().split("[,;]");
         if (parts.length != 3) {
            throw new IllegalArgumentException("BlockPos must be x,y,z.");
         } else {
            return new BlockPos(parseInt("x", parts[0].trim()), parseInt("y", parts[1].trim()), parseInt("z", parts[2].trim()));
         }
      }
   }

   private static Vec3 vec3Arg(Map<String, String> args, Vec3 fallback, String packedName) {
      String packed = firstArg(args, packedName);
      if (packed != null) {
         return parseVec3(packed);
      } else {
         String hx = firstArg(args, packedName + "X");
         String hy = firstArg(args, packedName + "Y");
         String hz = firstArg(args, packedName + "Z");
         if (hx == null && hy == null && hz == null) {
            return fallback;
         } else if (hx != null && hy != null && hz != null) {
            return new Vec3(Double.parseDouble(hx), Double.parseDouble(hy), Double.parseDouble(hz));
         } else {
            throw new IllegalArgumentException(packedName + "X/Y/Z must all be provided.");
         }
      }
   }

   private static Vec3 blockCenter(BlockPos pos) {
      return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
   }

   private static Vec3 parseVec3(String value) {
      if (isCurrent(value)) {
         return new Vec3(currentX(), currentY(), currentZ());
      } else {
         String[] parts = value.trim().split("[,;]");
         if (parts.length != 3) {
            throw new IllegalArgumentException("Vec3 must be x,y,z.");
         } else {
            return new Vec3(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
         }
      }
   }

   private static Vec3i parseVec3i(String value) {
      if (isCurrent(value)) {
         return (Vec3i)(MC != null && MC.player != null ? MC.player.blockPosition() : Vec3i.ZERO);
      } else {
         String[] parts = value.trim().split("[,;]");
         if (parts.length != 3) {
            throw new IllegalArgumentException("Vec3i must be x,y,z.");
         } else {
            return new Vec3i(parseInt("x", parts[0].trim()), parseInt("y", parts[1].trim()), parseInt("z", parts[2].trim()));
         }
      }
   }

   private static byte[] parseBytes(String value) {
      String trimmed = value == null ? "" : value.trim();
      if (trimmed.isEmpty() || isNone(trimmed)) {
         return new byte[0];
      } else if (trimmed.regionMatches(true, 0, "base64:", 0, 7)) {
         return Base64.getDecoder().decode(trimmed.substring(7).trim());
      } else if (trimmed.regionMatches(true, 0, "hex:", 0, 4)) {
         return parseHex(trimmed.substring(4));
      } else {
         String compactHex = trimmed.replace(" ", "").replace("_", "");
         if (compactHex.length() % 2 == 0 && compactHex.matches("(?i)[0-9a-f]+")) {
            return parseHex(compactHex);
         } else {
            return trimmed.regionMatches(true, 0, "utf8:", 0, 5)
               ? trimmed.substring(5).getBytes(StandardCharsets.UTF_8)
               : trimmed.getBytes(StandardCharsets.UTF_8);
         }
      }
   }

   private static byte[] parseHex(String value) {
      String compact = value.replaceAll("[^0-9A-Fa-f]", "");
      if (compact.length() % 2 != 0) {
         throw new IllegalArgumentException("Hex byte string must have an even length.");
      } else {
         byte[] out = new byte[compact.length() / 2];

         for (int i = 0; i < compact.length(); i += 2) {
            out[i / 2] = (byte)Integer.parseInt(compact.substring(i, i + 2), 16);
         }

         return out;
      }
   }

   private static Object parseOptional(String fieldName, String value, Type genericType) {
      if (value != null && !isNone(value)) {
         if (genericArgument(genericType) instanceof Class<?> innerClass) {
            return Optional.of(parseValue(fieldName, value, innerClass, innerClass));
         } else {
            throw new IllegalArgumentException("Optional argument '" + fieldName + "' only supports empty/null here.");
         }
      } else {
         return Optional.empty();
      }
   }

   private static Object parseList(String fieldName, String value, Type genericType) {
      if (!(genericArgument(genericType) instanceof Class<?> innerClass)) {
         if (isNone(value)) {
            return List.of();
         } else {
            throw new IllegalArgumentException("List argument '" + fieldName + "' needs a supported element type.");
         }
      } else {
         List parts = splitList(value, "\\|", 256);
         ArrayList values = new ArrayList(parts.size());

         for (String part : parts) {
            values.add(parseValue(fieldName, part, innerClass, innerClass));
         }

         return List.copyOf(values);
      }
   }

   private static Set<?> parseEmptySet(String fieldName, String value) {
      if (isNone(value)) {
         return Set.of();
      } else {
         throw new IllegalArgumentException("Set argument '" + fieldName + "' only supports empty/null from .send.");
      }
   }

   private static Map<?, ?> parseEmptyMap(String fieldName, String value) {
      if (isNone(value)) {
         return Map.of();
      } else {
         throw new IllegalArgumentException("Map argument '" + fieldName + "' only supports empty/null from .send.");
      }
   }

   private static Type genericArgument(Type genericType) {
      return (Type)(genericType instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > 0
         ? parameterized.getActualTypeArguments()[0]
         : Object.class);
   }

   private static Int2ObjectMap<HashedStack> parseHashedSlotMap(String value) {
      Int2ObjectOpenHashMap<HashedStack> out = new Int2ObjectOpenHashMap();
      if (value != null && !isNone(value)) {
         for (String entry : splitList(value, "\\|", 128)) {
            if (!entry.isBlank()) {
               int eq = entry.indexOf(61);
               if (eq < 0) {
                  throw new IllegalArgumentException("changedSlots entries must be slot=empty.");
               }

               int slot = parseInt("changedSlots", entry.substring(0, eq).trim());
               HashedStack stack = parseHashedStack(entry.substring(eq + 1).trim());
               out.put(slot, stack);
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private static HashedStack parseHashedStack(String value) {
      if (value != null && !isNone(value)) {
         throw new IllegalArgumentException("HashedStack from .send currently supports only empty/null. Capture/resend for real hashed item data.");
      } else {
         return HashedStack.EMPTY;
      }
   }

   private static ItemStack parseItemStack(String value) {
      if (value != null && !isNone(value)) {
         String normalized = normalizeName(value);
         if (MC.player != null) {
            if ("held".equals(normalized) || "mainhand".equals(normalized)) {
               return MC.player.getMainHandItem().copy();
            }

            if ("offhand".equals(normalized)) {
               return MC.player.getOffhandItem().copy();
            }

            if ("cursor".equals(normalized) && MC.player.containerMenu != null) {
               return MC.player.containerMenu.getCarried().copy();
            }
         }

         throw new IllegalArgumentException("ItemStack supports empty, held/mainhand, offhand, or cursor.");
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static Input parseInput(String value) {
      if (value != null && !isNone(value)) {
         boolean forward = false;
         boolean backward = false;
         boolean left = false;
         boolean right = false;
         boolean jump = false;
         boolean shift = false;
         boolean sprint = false;

         for (String part : value.split("[,;|+]")) {
            String var12 = normalizeName(part);
            switch (var12) {
               case "":
               case "empty":
                  break;
               case "forward":
               case "w":
                  forward = true;
                  break;
               case "backward":
               case "back":
               case "s":
                  backward = true;
                  break;
               case "left":
               case "a":
                  left = true;
                  break;
               case "right":
               case "d":
                  right = true;
                  break;
               case "jump":
               case "space":
                  jump = true;
                  break;
               case "shift":
               case "sneak":
                  shift = true;
                  break;
               case "sprint":
                  sprint = true;
                  break;
               default:
                  throw new IllegalArgumentException("Unknown input flag: " + part);
            }
         }

         return new Input(forward, backward, left, right, jump, shift, sprint);
      } else {
         return Input.EMPTY;
      }
   }

   private static String[] lines4(Map<String, String> args) {
      String packed = firstArg(args, "lines");
      String[] lines = new String[]{"", "", "", ""};
      if (packed != null) {
         List<String> parts = splitList(packed, "\\|", 4);

         for (int i = 0; i < Math.min(4, parts.size()); i++) {
            lines[i] = parts.get(i);
         }
      }

      for (int i = 0; i < 4; i++) {
         String value = firstArg(args, "line" + i, "l" + i);
         if (value != null) {
            lines[i] = value;
         }
      }

      return lines;
   }

   private static Optional<String> optionalString(String value) {
      return value != null && !isNone(value) ? Optional.of(value) : Optional.empty();
   }

   private static List<Entry> gameRuleEntries(Map<String, String> args) {
      String packed = firstArg(args, "entries", "rules");
      if (packed == null) {
         String rule = firstArg(args, "rule");
         String value = firstArg(args, "value");
         if (rule == null || value == null) {
            throw new IllegalArgumentException("Provide entries=rule=value|rule2=value2 or rule=... value=...");
         }

         packed = rule + "=" + value;
      }

      List<Entry> entries = new ArrayList<>();

      for (String entry : splitList(packed, "\\|", 256)) {
         int eq = entry.indexOf(61);
         if (eq < 0) {
            throw new IllegalArgumentException("Game rule entries must be rule=value.");
         }

         ResourceKey<GameRule<?>> key = ResourceKey.create(Registries.GAME_RULE, parseIdentifierDefault(entry.substring(0, eq).trim()));
         entries.add(new Entry(key, entry.substring(eq + 1)));
      }

      return List.copyOf(entries);
   }

   private static Identifier parseIdentifierDefault(String value) {
      return value.contains(":") ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
   }

   private static String normalizeIdentifier(String value) {
      return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
   }

   private static String requireString(Map<String, String> args, String name) {
      String value = firstArg(args, name);
      if (value == null) {
         throw new IllegalArgumentException("Missing required argument: " + name);
      } else {
         return value;
      }
   }

   private static String normalizeChatCommand(String command) {
      String trimmed = command == null ? "" : command.trim();

      while (trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }

      if (trimmed.isBlank()) {
         throw new IllegalArgumentException("Command cannot be blank.");
      } else {
         return trimmed;
      }
   }

   private static List<String> splitList(String value, String delimiterRegex, int max) {
      if (value != null && !isNone(value)) {
         String[] parts = value.split(delimiterRegex, -1);
         if (parts.length > max) {
            throw new IllegalArgumentException("Too many list values; max " + max + ".");
         } else {
            List<String> out = new ArrayList<>(parts.length);

            for (String part : parts) {
               out.add(part);
            }

            return out;
         }
      } else {
         return List.of();
      }
   }

   private static boolean isNone(String value) {
      if (value == null) {
         return true;
      } else {
         String normalized = value.trim().toLowerCase(Locale.ROOT);
         return normalized.isEmpty()
            || normalized.equals("empty")
            || normalized.equals("null")
            || normalized.equals("none")
            || normalized.equals("{}")
            || normalized.equals("[]");
      }
   }

   private static boolean isCurrent(String value) {
      return value != null && value.trim().equalsIgnoreCase("current");
   }

   private static int parseInt(String name, String value) {
      try {
         return Integer.decode(value.trim());
      } catch (NumberFormatException var3) {
         throw new IllegalArgumentException("Invalid integer for " + name + ": " + value);
      }
   }

   private static long parseLong(String name, String value) {
      try {
         return Long.decode(value.trim());
      } catch (NumberFormatException var3) {
         throw new IllegalArgumentException("Invalid long for " + name + ": " + value);
      }
   }

   private static Instant instantArg(Map<String, String> args, Instant fallback, String... names) {
      String value = firstArg(args, names);
      return value == null ? fallback : parseInstant(value);
   }

   private static Instant parseInstant(String value) {
      if (value != null && !value.isBlank() && !"now".equalsIgnoreCase(value.trim())) {
         String trimmed = value.trim();
         return trimmed.matches("-?\\d+") ? Instant.ofEpochMilli(Long.parseLong(trimmed)) : Instant.parse(trimmed);
      } else {
         return Instant.now();
      }
   }

   private static ArgumentSignatures parseArgumentSignatures(String value) {
      if (value != null && !isNone(value)) {
         throw new IllegalArgumentException("ArgumentSignatures supports only empty/null from .send.");
      } else {
         return ArgumentSignatures.EMPTY;
      }
   }

   private static Update parseLastSeenUpdate(String value) {
      if (value != null && !isNone(value)) {
         String[] parts = value.split("[,;]");
         if (parts.length == 1) {
            return new Update(parseInt("lastSeenOffset", parts[0].trim()), new BitSet(20), (byte)0);
         } else {
            throw new IllegalArgumentException("lastSeen supports empty/null or an offset integer.");
         }
      } else {
         return new Update(0, new BitSet(20), (byte)0);
      }
   }

   private static MessageSignature parseMessageSignature(String value) {
      if (value != null && !isNone(value)) {
         byte[] bytes = parseBytes(value);
         if (bytes.length != 256) {
            throw new IllegalArgumentException("MessageSignature must be exactly 256 bytes.");
         } else {
            return new MessageSignature(bytes);
         }
      } else {
         return null;
      }
   }

   private static Data currentChatSessionData() {
      if (MC != null && MC.getConnection() != null) {
         try {
            Field field = MC.getConnection().getClass().getDeclaredField("chatSession");
            field.setAccessible(true);
            if (field.get(MC.getConnection()) instanceof LocalChatSession localChatSession) {
               return localChatSession.asRemote().asData();
            }
         } catch (Throwable var7) {
         }

         for (Field field : MC.getConnection().getClass().getDeclaredFields()) {
            try {
               if (field.getType() == LocalChatSession.class) {
                  field.setAccessible(true);
                  if (field.get(MC.getConnection()) instanceof LocalChatSession localChatSession) {
                     return localChatSession.asRemote().asData();
                  }
               }
            } catch (Throwable var6) {
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static boolean parseBoolean(String name, String value) {
      String normalized = value.trim().toLowerCase(Locale.ROOT);

      return switch (normalized) {
         case "true", "1", "yes", "y", "on" -> true;
         case "false", "0", "no", "n", "off" -> false;
         default -> throw new IllegalArgumentException("Invalid boolean for " + name + ": " + value);
      };
   }

   private static <E extends Enum<E>> E enumValue(Class<E> enumClass, String value) {
      String wanted = normalizeName(value);

      for (E constant : (Enum[])enumClass.getEnumConstants()) {
         if (normalizeName(constant.name()).equals(wanted)) {
            return constant;
         }
      }

      throw new IllegalArgumentException("Invalid " + enumClass.getSimpleName() + ": " + value + ". Options: " + enumOptions(enumClass));
   }

   private static String enumOptions(Class<? extends Enum<?>> enumClass) {
      List<String> names = new ArrayList<>();

      for (Enum<?> constant : enumClass.getEnumConstants()) {
         names.add(constant.name());
      }

      return String.join(", ", names);
   }

   private static void requireOnly(Map<String, String> args, String... allowed) {
      Set<String> normalized = new HashSet<>();

      for (String name : allowed) {
         normalized.add(normalizeName(name));
      }

      for (String key : args.keySet()) {
         if (!normalized.contains(key)) {
            throw new IllegalArgumentException("Unknown argument: " + key);
         }
      }
   }

   private static void ensureNoUnknown(Map<String, String> args, List<String> allowed) {
      Set<String> normalized = new HashSet<>();

      for (String name : allowed) {
         normalized.add(normalizeName(name));
      }

      for (String key : args.keySet()) {
         if (!normalized.contains(key)) {
            throw new IllegalArgumentException("Unknown argument: " + key);
         }
      }
   }

   private static List<String> componentNames(RecordComponent[] components) {
      List<String> names = new ArrayList<>();

      for (RecordComponent component : components) {
         names.add(component.getName());
      }

      return names;
   }

   private static List<String> expandableAllowedNames(List<String> names) {
      List<String> out = new ArrayList<>();

      for (String name : names) {
         for (String alias : aliasNames(name)) {
            out.add(alias);
         }
      }

      return out;
   }

   private static String[] aliasNames(String name) {
      String normalized = normalizeName(name);

      return switch (normalized) {
         case "slotnum", "slotid" -> new String[]{name, "slot"};
         case "buttonnum", "buttonid" -> new String[]{name, "button"};
         case "selecteditemindex" -> new String[]{name, "index"};
         case "containerid" -> new String[]{name, "id"};
         case "containerinput" -> new String[]{name, "input", "click"};
         case "transactionid" -> new String[]{name, "id", "tx"};
         case "entityid" -> new String[]{name, "id", "entity"};
         case "yrot" -> new String[]{name, "yaw"};
         case "xrot" -> new String[]{name, "pitch"};
         case "position", "blockpos" -> new String[]{name, "pos"};
         case "uuid" -> new String[]{name, "id"};
         case "profileid" -> new String[]{name, "uuid", "id"};
         case "isopen" -> new String[]{name, "open"};
         case "isfiltering" -> new String[]{name, "filtering"};
         case "isflying" -> new String[]{name, "flying"};
         case "includedata" -> new String[]{name, "data"};
         case "usemaxitems" -> new String[]{name, "max"};
         case "isfronttext" -> new String[]{name, "front"};
         case "itemstack" -> new String[]{name, "item"};
         case "carrieditem" -> new String[]{name, "carried"};
         case "changedslots" -> new String[]{name, "changed"};
         case "keybytes" -> new String[]{name, "key"};
         case "encryptedchallenge" -> new String[]{name, "challenge"};
         default -> new String[]{name};
      };
   }

   private static String getArg(Map<String, String> args, String name) {
      return args.get(normalizeName(name));
   }

   private static String firstArg(Map<String, String> args, String... names) {
      for (String name : names) {
         String value = getArg(args, name);
         if (value != null) {
            return value;
         }
      }

      return null;
   }

   private static String normalizeName(String value) {
      return value == null ? "" : value.trim().replace("-", "").replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
   }

   private static String exampleFor(String javaType, String kind) {
      String lower = (javaType == null ? "" : javaType).toLowerCase(Locale.ROOT);
      if ("boolean".equals(kind)) {
         return "true";
      } else if ("enum".equals(kind)) {
         return "VALUE";
      } else if ("identifier".equals(kind)) {
         return "minecraft:stone";
      } else if ("string".equals(kind)) {
         return "\"text\"";
      } else if (lower.contains("blockpos")) {
         return "0,64,0";
      } else if (lower.contains("float") || lower.contains("double")) {
         return "0.0";
      } else {
         return "number".equals(kind) ? "0" : "<" + (javaType != null && !javaType.isBlank() ? javaType : "value") + ">";
      }
   }

   private static List<String> compactUsageFields(List<String> fields) {
      List<String> out = new ArrayList<>();

      for (String field : fields) {
         int typeStart = field.indexOf(" (");
         out.add(typeStart >= 0 ? field.substring(0, typeStart) : field);
      }

      return out;
   }

   private static String shortType(String javaType, String kind) {
      if (kind != null && !kind.isBlank() && !"object".equals(kind)) {
         return kind;
      } else if (javaType != null && !javaType.isBlank()) {
         int generic = javaType.indexOf(60);
         String base = generic >= 0 ? javaType.substring(0, generic) : javaType;
         int dot = Math.max(base.lastIndexOf(46), base.lastIndexOf(36));
         return dot >= 0 ? base.substring(dot + 1) : base;
      } else {
         return "value";
      }
   }

   private static String displayName(Class<?> packetClass) {
      String known = RiptidePacketRegistry.getName((Class<? extends Packet<?>>)packetClass);
      return known != null ? known : packetClass.getSimpleName().replace('$', '.');
   }

   private static float currentYRot() {
      return MC != null && MC.player != null ? MC.player.getYRot() : 0.0F;
   }

   private static float currentXRot() {
      return MC != null && MC.player != null ? MC.player.getXRot() : 0.0F;
   }

   private static double currentX() {
      return MC != null && MC.player != null ? MC.player.getX() : 0.0;
   }

   private static double currentY() {
      return MC != null && MC.player != null ? MC.player.getY() : 0.0;
   }

   private static double currentZ() {
      return MC != null && MC.player != null ? MC.player.getZ() : 0.0;
   }

   private static boolean currentOnGround() {
      return MC != null && MC.player != null && MC.player.onGround();
   }

   private static boolean currentHorizontalCollision() {
      return MC != null && MC.player != null && MC.player.horizontalCollision;
   }

   private static int currentSelectedSlot() {
      if (MC != null && MC.player != null) {
         return MC.player.getInventory().getSelectedSlot();
      } else {
         throw new IllegalArgumentException("Player is required for current selected slot.");
      }
   }

   private static int currentContainerId() {
      if (MC != null && MC.player != null && MC.player.containerMenu != null) {
         return MC.player.containerMenu.containerId;
      } else {
         throw new IllegalArgumentException("Open/current container is required.");
      }
   }

   private static int currentContainerStateId() {
      return MC != null && MC.player != null && MC.player.containerMenu != null ? MC.player.containerMenu.getStateId() : 0;
   }

   private static int currentEntityId() {
      if (MC != null && MC.hitResult instanceof EntityHitResult entityHit && entityHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
         return entityHit.getEntity().getId();
      } else {
         throw new IllegalArgumentException("Look at an entity or provide entityId=...");
      }
   }

   private static Vec3 currentEntityHitLocation(Vec3 fallback) {
      return MC != null && MC.hitResult instanceof EntityHitResult entityHit && entityHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
         ? entityHit.getLocation()
         : fallback;
   }

   private static BlockHitResult currentBlockHit() {
      return MC != null && MC.hitResult instanceof BlockHitResult blockHit && blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
         ? blockHit
         : null;
   }

   private static String formatDouble(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }

   private static String formatFloat(float value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static String safeMessage(Throwable t) {
      if (t == null) {
         return "unknown";
      } else {
         String message = t.getMessage();
         return message != null && !message.isBlank() ? message : t.getClass().getSimpleName();
      }
   }

   static {
      List<String> names = new ArrayList<>();
      names.addAll(RAW_BODY_ARG_NAMES);
      names.addAll(RAW_PACKET_ARG_NAMES);
      names.addAll(RAW_BASE64_ARG_NAMES);
      RAW_ARG_NAMES = List.copyOf(names);
   }

   public record PreparedArgs(String args, boolean dryRun) {
   }

   public record Result(Packet<?> packet, boolean ok, boolean help, String message, String source) {
      static RiptidePacketArgumentBuilder.Result ok(Packet<?> packet, String source) {
         return new RiptidePacketArgumentBuilder.Result(packet, true, false, "", source == null ? "builder" : source);
      }

      static RiptidePacketArgumentBuilder.Result error(String message) {
         return new RiptidePacketArgumentBuilder.Result(null, false, false, message == null ? "Packet build failed." : message, "");
      }

      static RiptidePacketArgumentBuilder.Result help(String message) {
         return new RiptidePacketArgumentBuilder.Result(null, false, true, message == null ? "" : message, "");
      }
   }
}
