package riptide.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class RiptideNormalPacketAnalyzer {
   private static final int MAX_CHANGED_SLOTS = 16;
   private static final int MAX_CONTENT_ITEMS = 12;

   private RiptideNormalPacketAnalyzer() {
   }

   public static RiptideNormalPacketAnalyzer.Analysis analyze(RiptidePacketLoggerOverlay.LogEntry entry) {
      RiptideNormalPacketAnalyzer.Builder out = new RiptideNormalPacketAnalyzer.Builder();
      if (entry == null) {
         return out.build(false, "fallback");
      } else {
         appendWire(entry, out);
         RiptideNormalPacketAnalyzer.WireDecode wireDecode = decodeFromCapturedWire(entry);
         Packet<?> packet = wireDecode.packet != null ? wireDecode.packet : entry.packetRef;
         if (packet == null) {
            if (wireDecode.message != null && !wireDecode.message.isBlank()) {
               out.decoded("Wire Decode: " + wireDecode.message, wireDecode.success ? RiptideColors.packetGreen() : RiptideColors.packetYellow());
            }

            return out.build(false, "fallback");
         } else {
            boolean complete = decodeRealityAndMeaning(packet, out, wireDecode);
            return out.build(complete, complete ? "complete" : (out.hasDecoded() ? wireDecode.status() : "fallback"));
         }
      }
   }

   public static String exportDecodedText(RiptidePacketLoggerOverlay.LogEntry entry) {
      RiptideNormalPacketAnalyzer.Analysis analysis = analyze(entry);
      if (!analysis.hasDecoded() && !analysis.hasContext()) {
         return "";
      } else {
         StringBuilder out = new StringBuilder();
         if (analysis.hasDecoded()) {
            out.append("[Reality]\n");
            appendPlain(out, analysis.decodedLines());
         }

         if (analysis.hasContext()) {
            out.append("\n[Meaning]\n");
            appendPlain(out, analysis.contextLines());
         }

         return out.toString().stripTrailing();
      }
   }

   private static void appendPlain(StringBuilder out, List<RiptidePacketInspector.InspectionLine> lines) {
      for (RiptidePacketInspector.InspectionLine line : lines) {
         if (line != null) {
            out.append(line.getText()).append('\n');
         }
      }
   }

   private static RiptideNormalPacketAnalyzer.WireDecode decodeFromCapturedWire(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry == null || entry.packetRef == null || entry.packetClass == null) {
         return RiptideNormalPacketAnalyzer.WireDecode.none();
      } else if (!Packet.class.isAssignableFrom(entry.packetClass)) {
         return RiptideNormalPacketAnalyzer.WireDecode.none();
      } else {
         RiptidePacketCapture.PacketSnapshot snapshot = RiptidePacketCapture.snapshot(entry.packetRef);
         if (snapshot != null && snapshot.plaintextBytes().length != 0) {
            try {
               Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>)entry.packetClass;
               RiptidePacketCodecIo.DecodedPacket decoded = RiptidePacketCodecIo.decodeFullPacket(
                  packetClass, snapshot.plaintextBytes(), snapshot.protocolPhase()
               );
               return RiptideNormalPacketAnalyzer.WireDecode.success(decoded.packet(), "decoded from captured plaintext with Minecraft STREAM_CODEC");
            } catch (Throwable var4) {
               return RiptideNormalPacketAnalyzer.WireDecode.failure("captured plaintext was kept, but STREAM_CODEC decode failed: " + safe(var4));
            }
         } else {
            return RiptideNormalPacketAnalyzer.WireDecode.none();
         }
      }
   }

   private static boolean decodeRealityAndMeaning(Packet<?> packet, RiptideNormalPacketAnalyzer.Builder out, RiptideNormalPacketAnalyzer.WireDecode wireDecode) {
      if (wireDecode != null && wireDecode.message != null && !wireDecode.message.isBlank()) {
         out.decoded("Wire Decode: " + wireDecode.message, wireDecode.success ? RiptideColors.packetGreen() : RiptideColors.packetYellow());
      }

      RiptidePacketDecodedView view = RiptidePacketDecodedView.decode(packet);
      appendReality(view, out);
      appendMeaning(packet, view, out);
      return view.complete();
   }

   private static void appendReality(RiptidePacketDecodedView view, RiptideNormalPacketAnalyzer.Builder out) {
      if (view == null) {
         out.decoded("Status: fallback", RiptideColors.packetYellow());
         out.decoded("No packet object was available.", RiptideColors.dangerText());
      } else {
         RiptidePacketSchemaRegistry.PacketSchema schema = view.schema();
         out.decoded("Status: " + view.status(), view.complete() ? RiptideColors.packetGreen() : RiptideColors.packetYellow());
         if (schema != null) {
            out.decoded("Schema: " + (view.sourceBacked() ? "Minecraft source-backed schema" : "reflection fallback"), RiptideColors.textPrimary());
            out.decoded("Protocol: " + schema.protocol() + " | Direction: " + schema.direction(), RiptideColors.textSecondary());
            out.decoded("Codec: " + schema.codecStyle() + " | Source: " + schema.source(), RiptideColors.textSecondary());
            if (!schema.packetType().isBlank()) {
               out.decoded("Packet Type: " + schema.packetType(), RiptideColors.textSecondary());
            }

            if (schema.inheritedFallback()) {
               out.decoded("Field Note: using parent packet schema for nested packet variant", RiptideColors.packetYellow());
            }
         } else {
            out.decoded("Schema: reflection fallback", RiptideColors.packetYellow());
         }

         if (!view.fallbackReason().isBlank()) {
            out.decoded("Fallback Note: " + view.fallbackReason(), RiptideColors.textMuted());
         }

         if (view.fields().isEmpty()) {
            out.decoded("Fields: none", RiptideColors.textMuted());
         } else {
            out.decoded("Fields: " + view.fields().size(), RiptideColors.textPrimary());

            for (RiptidePacketFieldValue field : view.fields()) {
               int color = field.readable() ? RiptideColors.packetWhite() : RiptideColors.dangerText();
               out.decoded(field.name() + " (" + field.javaType() + "): " + field.summary(), color);

               for (String detail : field.details()) {
                  out.decoded("  " + detail, RiptideColors.textSecondary());
               }
            }
         }
      }
   }

   private static void appendMeaning(Packet<?> packet, RiptidePacketDecodedView view, RiptideNormalPacketAnalyzer.Builder out) {
      if (packet instanceof ClientboundOpenScreenPacket open) {
         out.context("Server opens a handled screen.", RiptideColors.textPrimary());
         out.context("Container: #" + open.getContainerId() + " type=" + safe(open.getType()), RiptideColors.packetBlue());
         out.context("Title: " + quote(open.getTitle() == null ? "" : open.getTitle().getString()), RiptideColors.successText());
      } else if (packet instanceof ClientboundContainerSetContentPacket content) {
         out.context("Server replaces the full contents of a container.", RiptideColors.textPrimary());
         out.context("Container: #" + content.containerId() + " state=" + content.stateId(), RiptideColors.packetBlue());
         out.context("Slots: " + content.items().size() + " total, " + countNonEmpty(content.items()) + " non-empty", RiptideColors.textPrimary());
         out.context("Carried Item: " + RiptidePacketContextTracker.summarizeItem(content.carriedItem()), RiptideColors.successText());
         appendMeaningContentPreview(content.items(), out);
      } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
         out.context("Server updates one container slot.", RiptideColors.textPrimary());
         out.context("Container: #" + slot.getContainerId() + " state=" + slot.getStateId(), RiptideColors.packetBlue());
         out.context("Slot: " + slot.getSlot(), RiptideColors.textPrimary());
         out.context("Item: " + RiptidePacketContextTracker.summarizeItem(slot.getItem()), RiptideColors.successText());
      } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
         out.context("Server sets the carried cursor item.", RiptideColors.textPrimary());
         out.context("Cursor: " + RiptidePacketContextTracker.summarizeItem(cursor.contents()), RiptideColors.successText());
      } else if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
         out.context("Server updates a player inventory slot.", RiptideColors.textPrimary());
         out.context("Inventory Slot: " + inventory.slot(), RiptideColors.packetBlue());
         out.context("Item: " + RiptidePacketContextTracker.summarizeItem(inventory.contents()), RiptideColors.successText());
      } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
         out.context("Server changes the selected hotbar slot.", RiptideColors.textPrimary());
         out.context("Selected Slot: " + held.slot(), RiptideColors.successText());
      } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
         out.context("Client changes the selected hotbar slot.", RiptideColors.textPrimary());
         out.context("Selected Slot: " + carried.getSlot(), RiptideColors.successText());
      } else if (!(packet instanceof ServerboundContainerClickPacket click)) {
         if (packet instanceof ServerboundContainerButtonClickPacket button) {
            out.context("Client presses a handled-screen button.", RiptideColors.textPrimary());
            out.context("Container: #" + safe(call(button, "containerId", "getContainerId", "getSyncId")), RiptideColors.packetBlue());
            out.context("Button Id: " + safe(call(button, "buttonId", "getButtonId")), RiptideColors.textPrimary());
         } else if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
            out.context(
               packet instanceof ServerboundContainerClosePacket ? "Client closes a handled screen." : "Server closes a handled screen.",
               RiptideColors.textPrimary()
            );
            Object id = packet instanceof ClientboundContainerClosePacket close ? close.getContainerId() : call(packet, "containerId", "getContainerId");
            out.context("Container: #" + safe(id), RiptideColors.packetBlue());
         } else if (packet instanceof ClientboundContainerSetDataPacket data) {
            out.context("Server updates a container property/data slot.", RiptideColors.textPrimary());
            out.context("Container: #" + safe(call(data, "containerId", "getContainerId", "getSyncId")), RiptideColors.packetBlue());
            out.context(
               "Property: " + safe(call(data, "id", "getId", "propertyId", "getPropertyId")) + " = " + safe(call(data, "value", "getValue")),
               RiptideColors.successText()
            );
         } else if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
            out.context("Client sets a creative inventory slot.", RiptideColors.textPrimary());
            out.context("Slot: " + safe(call(creative, "slot", "getSlot")), RiptideColors.packetBlue());
            out.context("Stack: " + summarizeMaybeItem(call(creative, "itemStack", "stack", "getItem", "getStack")), RiptideColors.successText());
         } else if (packet instanceof ServerboundSelectBundleItemPacket bundle) {
            out.context("Client selects an item inside a bundle.", RiptideColors.textPrimary());
            out.context(
               "Slot: " + safe(call(bundle, "slot", "getSlot")) + " | Index: " + safe(call(bundle, "selectedItemIndex", "index", "getIndex")),
               RiptideColors.packetBlue()
            );
         } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            PositionMoveRotation change = position.change();
            out.context("Server sends an authoritative player position/rotation correction.", RiptideColors.textPrimary());
            out.context("Teleport Id: " + position.id(), RiptideColors.packetBlue());
            out.context("Relative Flags: " + formatRelatives(position.relatives()), RiptideColors.textPrimary());
            out.context("Encoded Position: " + formatVec3(change.position()), RiptideColors.textPrimary());
            out.context("Encoded Velocity Delta: " + formatVec3(change.deltaMovement()), RiptideColors.textPrimary());
            out.context("Encoded Rotation: yaw=" + formatFloat(change.yRot()) + ", pitch=" + formatFloat(change.xRot()), RiptideColors.textPrimary());
            out.context("Expected Reply: ServerboundAcceptTeleportation #" + position.id(), RiptideColors.packetOrange());
         } else if (packet instanceof ServerboundMovePlayerPacket move) {
            out.context("Client sends player movement state.", RiptideColors.textPrimary());
            out.context("Variant: " + move.getClass().getSimpleName().replace("ServerboundMovePlayerPacket$", ""), RiptideColors.textPrimary());
            out.context("Has Position: " + move.hasPosition() + " | Has Rotation: " + move.hasRotation(), RiptideColors.textPrimary());
            out.context("On Ground: " + move.isOnGround() + " | Horizontal Collision: " + move.horizontalCollision(), RiptideColors.textPrimary());
            out.context("Encoded Position: x=" + move.getX(0.0) + ", y=" + move.getY(0.0) + ", z=" + move.getZ(0.0), RiptideColors.successText());
            out.context("Encoded Rotation: yaw=" + formatFloat(move.getYRot(0.0F)) + ", pitch=" + formatFloat(move.getXRot(0.0F)), RiptideColors.successText());
         } else if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
            out.context("Client acknowledges a server teleport/correction.", RiptideColors.textPrimary());
            out.context("Teleport Id: " + ack.getId(), RiptideColors.packetBlue());
         } else if (packet instanceof ClientboundLoginPacket login) {
            out.context("Server enters the play world.", RiptideColors.textPrimary());
            out.context("Player Entity Id: " + login.playerId(), RiptideColors.packetBlue());
            out.context("Dimension: " + login.commonPlayerSpawnInfo().dimension().identifier(), RiptideColors.successText());
            out.context("Game Mode: " + login.commonPlayerSpawnInfo().gameType(), RiptideColors.textPrimary());
            out.context("Chunk Radius: " + login.chunkRadius() + " | Simulation Distance: " + login.simulationDistance(), RiptideColors.textPrimary());
         } else if (packet instanceof ClientboundRespawnPacket respawn) {
            out.context("Server respawns player or changes dimension/world.", RiptideColors.textPrimary());
            out.context("Dimension: " + respawn.commonPlayerSpawnInfo().dimension().identifier(), RiptideColors.successText());
            out.context("Game Mode: " + respawn.commonPlayerSpawnInfo().gameType(), RiptideColors.textPrimary());
            out.context("Keep Data Mask: " + respawn.dataToKeep(), RiptideColors.textPrimary());
         } else if (packet instanceof ClientboundTransferPacket transfer) {
            out.context("Server requests client transfer.", RiptideColors.textPrimary());
            out.context("Target: " + transfer.host() + ":" + transfer.port(), RiptideColors.successText());
         } else if (packet instanceof ClientboundStartConfigurationPacket) {
            out.context("Server starts configuration phase.", RiptideColors.textPrimary());
         } else if (packet instanceof ClientboundFinishConfigurationPacket) {
            out.context("Server finishes configuration phase.", RiptideColors.textPrimary());
         } else if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            out.context("Server changes chunk cache center.", RiptideColors.textPrimary());
            out.context("Chunk Center: " + center.getX() + ", " + center.getZ(), RiptideColors.successText());
         } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            out.context("Server changes chunk render/cache radius.", RiptideColors.textPrimary());
            out.context("Radius: " + radius.getRadius(), RiptideColors.successText());
         } else if (packet instanceof ClientboundSetSimulationDistancePacket simulation) {
            out.context("Server changes simulation distance.", RiptideColors.textPrimary());
            out.context("Simulation Distance: " + simulation.simulationDistance(), RiptideColors.successText());
         } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            out.context("Client should unload a chunk.", RiptideColors.textPrimary());
            out.context("Chunk: " + forget.pos().x() + ", " + forget.pos().z(), RiptideColors.successText());
         } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            out.context("Server sends chunk data with light data.", RiptideColors.textPrimary());
            out.context("Chunk: " + chunk.getX() + ", " + chunk.getZ(), RiptideColors.successText());
         } else if (packet instanceof ClientboundGameEventPacket gameEvent) {
            out.context("Server sends a game/world event.", RiptideColors.textPrimary());
            out.context("Event: " + safe(gameEvent.getEvent()) + " | Value: " + gameEvent.getParam(), RiptideColors.successText());
         } else if (packet instanceof ClientboundResourcePackPushPacket pack) {
            out.context("Server requests a resource pack.", RiptideColors.textPrimary());
            out.context("Id: " + pack.id(), RiptideColors.packetBlue());
            out.context("Url: " + pack.url(), RiptideColors.successText());
            out.context("Required: " + pack.required() + " | Hash: " + pack.hash(), RiptideColors.textPrimary());
         } else if (packet instanceof ClientboundResourcePackPopPacket pack) {
            out.context("Server removes a resource pack.", RiptideColors.textPrimary());
            out.context("Id: " + pack.id().map(Object::toString).orElse("latest / top"), RiptideColors.packetBlue());
         } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
            out.context("Server disconnects the client.", RiptideColors.textPrimary());
            out.context("Reason: " + safe(disconnect.reason()), RiptideColors.dangerText());
         } else {
            if (view != null && view.complete()) {
               out.context("No specialist meaning layer yet; Reality shows the exact source-backed packet fields.", RiptideColors.textMuted());
            }
         }
      } else {
         out.context("Client clicks inside a handled screen.", RiptideColors.textPrimary());
         out.context("Container: #" + click.containerId() + " state=" + click.stateId(), RiptideColors.packetBlue());
         out.context("Input: " + describeInput(click.containerInput()) + " (" + click.containerInput() + ")", RiptideColors.successText());
         out.context("Slot: " + click.slotNum() + " | Button: " + describeButton(click.containerInput(), click.buttonNum()), RiptideColors.textPrimary());
         out.context("Changed Slots: " + click.changedSlots().size(), RiptideColors.textPrimary());
         int shown = 0;

         for (ObjectIterator var5 = click.changedSlots().int2ObjectEntrySet().iterator(); var5.hasNext(); shown++) {
            Entry<HashedStack> slot = (Entry<HashedStack>)var5.next();
            if (shown >= 16) {
               out.context("  ... +" + (click.changedSlots().size() - shown) + " more", RiptideColors.textMuted());
               break;
            }

            out.context(
               "  #" + slot.getIntKey() + " -> " + RiptidePacketContextTracker.summarizeHashedStack((HashedStack)slot.getValue()),
               RiptideColors.textSecondary()
            );
         }

         out.context("Carried Item: " + RiptidePacketContextTracker.summarizeHashedStack(click.carriedItem()), RiptideColors.successText());
      }
   }

   private static void appendMeaningContentPreview(List<ItemStack> items, RiptideNormalPacketAnalyzer.Builder out) {
      int shown = 0;

      for (int i = 0; i < items.size(); i++) {
         ItemStack stack = items.get(i);
         if (stack != null && !stack.isEmpty()) {
            if (shown == 0) {
               out.context("Non-empty Preview:", RiptideColors.textPrimary());
            }

            if (shown >= 12) {
               out.context("  ... more non-empty slots hidden", RiptideColors.textMuted());
               return;
            }

            out.context("  #" + i + " " + RiptidePacketContextTracker.summarizeItem(stack), RiptideColors.textSecondary());
            shown++;
         }
      }
   }

   private static boolean decodePacket(RiptidePacketLoggerOverlay.LogEntry entry, Packet<?> packet, RiptideNormalPacketAnalyzer.Builder out) {
      RiptidePacketContextTracker.Capture capture = entry.packetContext;
      RiptidePacketContextTracker.Snapshot before = capture == null ? RiptidePacketContextTracker.Snapshot.EMPTY : capture.before();
      RiptidePacketContextTracker.Snapshot after = capture == null ? RiptidePacketContextTracker.Snapshot.EMPTY : capture.after();
      if (packet instanceof ClientboundOpenScreenPacket open) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server opened a handled screen", RiptideColors.textPrimary());
         out.decoded("Container Id: " + open.getContainerId(), RiptideColors.packetBlue());
         out.decoded("Screen Type: " + safe(open.getType()), RiptideColors.textPrimary());
         out.decoded("Title: " + quote(open.getTitle() == null ? "" : open.getTitle().getString()), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundContainerSetContentPacket content) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: server replaced full container contents", RiptideColors.textPrimary());
         out.decoded("Container Id: " + content.containerId(), RiptideColors.packetBlue());
         out.decoded("State Id: " + content.stateId(), RiptideColors.textSecondary());
         out.decoded("Slots: " + content.items().size() + " total, " + countNonEmpty(content.items()) + " non-empty", RiptideColors.textPrimary());
         out.decoded("Container Area: " + describeContainerArea(after), RiptideColors.textPrimary());
         out.decoded("Cursor: " + RiptidePacketContextTracker.summarizeItem(content.carriedItem()), RiptideColors.successText());
         appendContentPreview(content.items(), out);
         return true;
      } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: server updated one container slot", RiptideColors.textPrimary());
         out.decoded("Container Id: " + slot.getContainerId(), RiptideColors.packetBlue());
         out.decoded("State Id: " + slot.getStateId(), RiptideColors.textSecondary());
         out.decoded("Slot: " + slot.getSlot() + " (" + RiptidePacketContextTracker.slotArea(after, slot.getSlot()) + ")", RiptideColors.textPrimary());
         out.decoded("Before: " + before.slotItem(slot.getSlot()), RiptideColors.textMuted());
         out.decoded("After: " + RiptidePacketContextTracker.summarizeItem(slot.getItem()), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: server updated carried cursor item", RiptideColors.textPrimary());
         out.decoded("Before: " + before.cursorItem(), RiptideColors.textMuted());
         out.decoded("After: " + RiptidePacketContextTracker.summarizeItem(cursor.contents()), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: server updated a player inventory slot", RiptideColors.textPrimary());
         out.decoded("Inventory Slot: " + inventory.slot(), RiptideColors.packetBlue());
         out.decoded("Before: " + before.playerInventorySlots().getOrDefault(inventory.slot(), "unknown"), RiptideColors.textMuted());
         out.decoded("After: " + RiptidePacketContextTracker.summarizeItem(inventory.contents()), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: server selected held hotbar slot", RiptideColors.textPrimary());
         out.decoded("Before: " + before.selectedHotbarSlot(), RiptideColors.textMuted());
         out.decoded("After: " + held.slot(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: client selected hotbar slot", RiptideColors.textPrimary());
         out.decoded("Before: " + before.selectedHotbarSlot(), RiptideColors.textMuted());
         out.decoded("After: " + carried.getSlot(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ServerboundContainerClickPacket click) {
         decodeContainerClick(click, before, after, out);
         return false;
      } else if (packet instanceof ServerboundContainerButtonClickPacket button) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: client clicked a handled-screen button", RiptideColors.textPrimary());
         out.decoded("Container Id: " + call(button, "containerId", "getContainerId", "getSyncId"), RiptideColors.packetBlue());
         out.decoded("Button Id: " + call(button, "buttonId", "getButtonId"), RiptideColors.textPrimary());
         return true;
      } else if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded(
            "Meaning: " + (packet instanceof ServerboundContainerClosePacket ? "client closed a handled screen" : "server closed a handled screen"),
            RiptideColors.textPrimary()
         );
         Object id = packet instanceof ClientboundContainerClosePacket close ? close.getContainerId() : call(packet, "containerId", "getContainerId");
         out.decoded("Container Id: " + safe(id), RiptideColors.packetBlue());
         if (!before.screenTitle().isBlank()) {
            out.decoded("Closed Screen: " + quote(before.screenTitle()), RiptideColors.successText());
         }

         return true;
      } else if (packet instanceof ClientboundContainerSetDataPacket data) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server updated container property/data", RiptideColors.textPrimary());
         out.decoded("Container Id: " + call(data, "containerId", "getContainerId", "getSyncId"), RiptideColors.packetBlue());
         out.decoded("Property: " + call(data, "id", "getId", "propertyId", "getPropertyId"), RiptideColors.textPrimary());
         out.decoded("Value: " + call(data, "value", "getValue"), RiptideColors.successText());
         return true;
      } else if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: client set a creative inventory slot", RiptideColors.textPrimary());
         out.decoded("Slot: " + call(creative, "slot", "getSlot"), RiptideColors.packetBlue());
         out.decoded("Stack: " + summarizeMaybeItem(call(creative, "itemStack", "stack", "getItem", "getStack")), RiptideColors.successText());
         return true;
      } else if (packet instanceof ServerboundSelectBundleItemPacket bundle) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: client selected an item inside a bundle", RiptideColors.textPrimary());
         out.decoded("Slot: " + call(bundle, "slot", "getSlot"), RiptideColors.packetBlue());
         out.decoded("Selected Item Index: " + call(bundle, "selectedItemIndex", "index", "getIndex"), RiptideColors.textPrimary());
         return true;
      } else if (packet instanceof ClientboundPlayerPositionPacket position) {
         decodeServerPosition(position, before, after, out);
         return true;
      } else if (packet instanceof ServerboundMovePlayerPacket move) {
         decodeClientMove(move, before, after, out);
         return true;
      } else if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
         out.decoded("Status: context-aware", RiptideColors.packetGreen());
         out.decoded("Meaning: client acknowledged server teleport/correction", RiptideColors.textPrimary());
         out.decoded("Teleport Id: " + ack.getId(), RiptideColors.packetBlue());
         if (before.lastTeleportId() >= 0) {
            out.decoded(
               "Pending Before: " + before.lastTeleportId() + (before.lastTeleportId() == ack.getId() ? " (matched)" : " (different)"),
               RiptideColors.textPrimary()
            );
         }

         return true;
      } else if (packet instanceof ClientboundLoginPacket login) {
         decodeLogin(login, out);
         return true;
      } else if (packet instanceof ClientboundRespawnPacket respawn) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: respawn or dimension/world change", RiptideColors.textPrimary());
         out.decoded("Dimension: " + respawn.commonPlayerSpawnInfo().dimension().identifier(), RiptideColors.successText());
         out.decoded("Game Mode: " + respawn.commonPlayerSpawnInfo().gameType(), RiptideColors.textPrimary());
         out.decoded("Previous Game Mode: " + respawn.commonPlayerSpawnInfo().previousGameType(), RiptideColors.textSecondary());
         out.decoded("Keep Data Mask: " + respawn.dataToKeep(), RiptideColors.textPrimary());
         return true;
      } else if (packet instanceof ClientboundTransferPacket transfer) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server requested client transfer", RiptideColors.textPrimary());
         out.decoded("Target: " + transfer.host() + ":" + transfer.port(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundStartConfigurationPacket) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: play connection is switching into configuration phase", RiptideColors.textPrimary());
         return true;
      } else if (packet instanceof ClientboundFinishConfigurationPacket) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: configuration phase finished", RiptideColors.textPrimary());
         return true;
      } else if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server changed chunk cache center", RiptideColors.textPrimary());
         out.decoded("Chunk Center: " + center.getX() + ", " + center.getZ(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server changed chunk render/cache radius", RiptideColors.textPrimary());
         out.decoded("Radius: " + radius.getRadius(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundSetSimulationDistancePacket simulation) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server changed simulation distance", RiptideColors.textPrimary());
         out.decoded("Simulation Distance: " + simulation.simulationDistance(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: client should unload a chunk", RiptideColors.textPrimary());
         out.decoded("Chunk: " + forget.pos().x() + ", " + forget.pos().z(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         out.decoded("Status: fallback", RiptideColors.packetYellow());
         out.decoded("Meaning: server sent chunk data with light data", RiptideColors.textPrimary());
         out.decoded("Chunk: " + chunk.getX() + ", " + chunk.getZ(), RiptideColors.successText());
         out.decoded("Chunk Data: " + safe(chunk.getChunkData()), RiptideColors.textSecondary());
         out.decoded("Light Data: " + safe(chunk.getLightData()), RiptideColors.textSecondary());
         return false;
      } else if (packet instanceof ClientboundGameEventPacket gameEvent) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: game/world event", RiptideColors.textPrimary());
         out.decoded("Event: " + safe(gameEvent.getEvent()), RiptideColors.textPrimary());
         out.decoded("Value: " + gameEvent.getParam(), RiptideColors.successText());
         return true;
      } else if (packet instanceof ClientboundResourcePackPushPacket pack) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server requested resource pack", RiptideColors.textPrimary());
         out.decoded("Id: " + pack.id(), RiptideColors.packetBlue());
         out.decoded("Url: " + pack.url(), RiptideColors.successText());
         out.decoded("Hash: " + pack.hash(), RiptideColors.textSecondary());
         out.decoded("Required: " + pack.required(), RiptideColors.textPrimary());
         pack.prompt().ifPresent(component -> out.decoded("Prompt: " + quote(component.getString()), RiptideColors.textPrimary()));
         return true;
      } else if (packet instanceof ClientboundResourcePackPopPacket pack) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server removed resource pack", RiptideColors.textPrimary());
         out.decoded("Id: " + pack.id().map(Object::toString).orElse("latest / top"), RiptideColors.packetBlue());
         return true;
      } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
         out.decoded("Status: complete", RiptideColors.packetGreen());
         out.decoded("Meaning: server disconnected the client", RiptideColors.textPrimary());
         out.decoded("Reason: " + safe(disconnect.reason()), RiptideColors.dangerText());
         return true;
      } else {
         return false;
      }
   }

   private static void decodeContainerClick(
      ServerboundContainerClickPacket click,
      RiptidePacketContextTracker.Snapshot before,
      RiptidePacketContextTracker.Snapshot after,
      RiptideNormalPacketAnalyzer.Builder out
   ) {
      out.decoded("Status: context-aware", RiptideColors.packetGreen());
      out.decoded("Meaning: client clicked inside a handled screen", RiptideColors.textPrimary());
      out.decoded("Container Id: " + click.containerId(), RiptideColors.packetBlue());
      out.decoded("State Id: " + click.stateId(), RiptideColors.textSecondary());
      out.decoded("Input: " + describeInput(click.containerInput()) + " (" + click.containerInput() + ")", RiptideColors.successText());
      out.decoded("Slot: " + click.slotNum() + " (" + RiptidePacketContextTracker.slotArea(before, click.slotNum()) + ")", RiptideColors.textPrimary());
      out.decoded("Button: " + describeButton(click.containerInput(), click.buttonNum()), RiptideColors.textPrimary());
      out.decoded("Slot Before: " + before.slotItem(click.slotNum()), RiptideColors.textMuted());
      out.decoded("Slot After: " + after.slotItem(click.slotNum()), RiptideColors.successText());
      out.decoded("Cursor Before: " + before.cursorItem(), RiptideColors.textMuted());
      out.decoded("Cursor After: " + RiptidePacketContextTracker.summarizeHashedStack(click.carriedItem()), RiptideColors.successText());
      out.decoded("Changed Slots: " + click.changedSlots().size(), RiptideColors.textPrimary());
      int shown = 0;

      for (ObjectIterator var5 = click.changedSlots().int2ObjectEntrySet().iterator(); var5.hasNext(); shown++) {
         Entry<HashedStack> slot = (Entry<HashedStack>)var5.next();
         if (shown >= 16) {
            out.decoded("  ... +" + (click.changedSlots().size() - shown) + " more", RiptideColors.textMuted());
            break;
         }

         out.decoded(
            "  #"
               + slot.getIntKey()
               + " "
               + RiptidePacketContextTracker.slotArea(before, slot.getIntKey())
               + " -> "
               + RiptidePacketContextTracker.summarizeHashedStack((HashedStack)slot.getValue()),
            RiptideColors.textSecondary()
         );
      }
   }

   private static void decodeServerPosition(
      ClientboundPlayerPositionPacket position,
      RiptidePacketContextTracker.Snapshot before,
      RiptidePacketContextTracker.Snapshot after,
      RiptideNormalPacketAnalyzer.Builder out
   ) {
      PositionMoveRotation change = position.change();
      out.decoded("Status: context-aware", RiptideColors.packetGreen());
      out.decoded("Meaning: server corrected/teleported local player", RiptideColors.textPrimary());
      out.decoded("Teleport Id: " + position.id(), RiptideColors.packetBlue());
      out.decoded("Relative Flags: " + formatRelatives(position.relatives()), RiptideColors.textPrimary());
      out.decoded("Raw Position Change: " + formatVec3(change.position()), RiptideColors.textPrimary());
      out.decoded("Raw Delta Movement: " + formatVec3(change.deltaMovement()), RiptideColors.textPrimary());
      out.decoded("Raw Rotation: yaw=" + formatFloat(change.yRot()) + ", pitch=" + formatFloat(change.xRot()), RiptideColors.textPrimary());
      if (before.serverPosition() != null) {
         out.decoded("Before Server Pos: " + RiptidePacketContextTracker.formatPosition(before.serverPosition()), RiptideColors.textMuted());
      }

      if (after.serverPosition() != null) {
         out.decoded("Computed After: " + RiptidePacketContextTracker.formatPosition(after.serverPosition()), RiptideColors.successText());
      }

      out.decoded("Ack Expected: ServerboundAcceptTeleportation #" + position.id(), RiptideColors.packetOrange());
   }

   private static void decodeClientMove(
      ServerboundMovePlayerPacket move,
      RiptidePacketContextTracker.Snapshot before,
      RiptidePacketContextTracker.Snapshot after,
      RiptideNormalPacketAnalyzer.Builder out
   ) {
      out.decoded("Status: context-aware", RiptideColors.packetGreen());
      out.decoded("Meaning: client player movement update", RiptideColors.textPrimary());
      out.decoded("Variant: " + move.getClass().getSimpleName().replace("ServerboundMovePlayerPacket$", ""), RiptideColors.textPrimary());
      out.decoded("Has Position: " + move.hasPosition(), RiptideColors.textPrimary());
      out.decoded("Has Rotation: " + move.hasRotation(), RiptideColors.textPrimary());
      out.decoded("On Ground: " + move.isOnGround(), RiptideColors.textPrimary());
      out.decoded("Horizontal Collision: " + move.horizontalCollision(), RiptideColors.textPrimary());
      if (before.clientPosition() != null) {
         out.decoded("Before: " + RiptidePacketContextTracker.formatPosition(before.clientPosition()), RiptideColors.textMuted());
      }

      if (after.clientPosition() != null) {
         out.decoded("After: " + RiptidePacketContextTracker.formatPosition(after.clientPosition()), RiptideColors.successText());
         if (before.clientPosition() != null) {
            out.decoded(
               "Distance Delta: " + String.format(Locale.ROOT, "%.4f blocks", before.clientPosition().distanceTo(after.clientPosition())),
               RiptideColors.packetYellow()
            );
         }
      }
   }

   private static void decodeLogin(ClientboundLoginPacket login, RiptideNormalPacketAnalyzer.Builder out) {
      out.decoded("Status: complete", RiptideColors.packetGreen());
      out.decoded("Meaning: entered play world", RiptideColors.textPrimary());
      out.decoded("Player Entity Id: " + login.playerId(), RiptideColors.packetBlue());
      out.decoded("Dimension: " + login.commonPlayerSpawnInfo().dimension().identifier(), RiptideColors.successText());
      out.decoded("Game Mode: " + login.commonPlayerSpawnInfo().gameType(), RiptideColors.textPrimary());
      out.decoded("Hardcore: " + login.hardcore(), RiptideColors.textPrimary());
      out.decoded("Known Levels: " + login.levels().size(), RiptideColors.textPrimary());
      out.decoded("Chunk Radius: " + login.chunkRadius(), RiptideColors.textPrimary());
      out.decoded("Simulation Distance: " + login.simulationDistance(), RiptideColors.textPrimary());
      out.decoded("Reduced Debug: " + login.reducedDebugInfo(), RiptideColors.textPrimary());
      out.decoded("Secure Chat Enforced: " + login.enforcesSecureChat(), RiptideColors.textPrimary());
   }

   private static void appendContext(RiptidePacketContextTracker.Capture capture, RiptideNormalPacketAnalyzer.Builder out) {
      if (capture != null && capture.relevant()) {
         RiptidePacketContextTracker.Snapshot before = capture.before();
         RiptidePacketContextTracker.Snapshot after = capture.after();
         out.context(
            "Context Status: " + (before.known() ? "tracked" : "started mid-stream / partial"),
            before.known() ? RiptideColors.packetGreen() : RiptideColors.packetYellow()
         );
         if (!after.protocolState().isBlank()) {
            out.context("Protocol State: " + after.protocolState(), RiptideColors.textPrimary());
         }

         if (after.containerId() >= 0 || !after.screenTitle().isBlank()) {
            out.context("Screen: #" + after.containerId() + " " + quote(after.screenTitle()) + " " + after.screenType(), RiptideColors.successText());
         }

         if (!after.dimension().isBlank()) {
            out.context("Dimension: " + after.dimension(), RiptideColors.successText());
         }

         if (after.chunkCenterX() != null && after.chunkCenterZ() != null) {
            out.context("Chunk Center: " + after.chunkCenterX() + ", " + after.chunkCenterZ(), RiptideColors.textPrimary());
         }

         if (after.chunkRadius() != null) {
            out.context("Chunk Radius: " + after.chunkRadius(), RiptideColors.textPrimary());
         }

         if (after.simulationDistance() != null) {
            out.context("Simulation Distance: " + after.simulationDistance(), RiptideColors.textPrimary());
         }

         if (after.lastTeleportId() >= 0) {
            out.context("Pending Teleport Ack: " + after.lastTeleportId(), RiptideColors.packetOrange());
         }

         if (!capture.changes().isEmpty()) {
            out.context("Changes:", RiptideColors.textPrimary());

            for (String change : capture.changes()) {
               out.context("  - " + change, RiptideColors.textSecondary());
            }
         }
      }
   }

   private static void appendWire(RiptidePacketLoggerOverlay.LogEntry entry, RiptideNormalPacketAnalyzer.Builder out) {
      if (entry != null && entry.packetRef != null) {
         RiptidePacketCapture.PacketSnapshot snapshot = RiptidePacketCapture.snapshot(entry.packetRef);
         if (snapshot != null) {
            out.wire("Protocol: " + snapshot.protocolPhase(), RiptideColors.textSecondary());
            out.wire(
               "Direction: " + snapshot.direction(), "C2S".equalsIgnoreCase(snapshot.direction()) ? RiptideColors.packetOrange() : RiptideColors.packetCyan()
            );
            if (snapshot.numericPacketId() >= 0) {
               out.wire("Packet ID: " + snapshot.numericPacketId(), RiptideColors.textSecondary());
            }

            if (snapshot.packetType() != null && !snapshot.packetType().isBlank()) {
               out.wire("Packet Type: " + snapshot.packetType(), RiptideColors.textSecondary());
            }

            byte[] bytes = snapshot.plaintextBytes();
            out.wire("Plaintext: " + bytes.length + " bytes " + RiptidePacketCapture.compactHex(bytes, 48), RiptideColors.textMuted());
         }
      }
   }

   private static void appendContentPreview(List<ItemStack> items, RiptideNormalPacketAnalyzer.Builder out) {
      int shown = 0;

      for (int i = 0; i < items.size(); i++) {
         ItemStack stack = items.get(i);
         if (stack != null && !stack.isEmpty()) {
            if (shown == 0) {
               out.decoded("Non-empty Preview:", RiptideColors.textPrimary());
            }

            if (shown >= 12) {
               out.decoded("  ... more non-empty slots hidden", RiptideColors.textMuted());
               return;
            }

            out.decoded("  #" + i + " " + RiptidePacketContextTracker.summarizeItem(stack), RiptideColors.textSecondary());
            shown++;
         }
      }
   }

   private static String describeContainerArea(RiptidePacketContextTracker.Snapshot snapshot) {
      if (snapshot != null && snapshot.slotCount() >= 0) {
         int containerSlots = Math.max(0, snapshot.containerSlotCount());
         int playerSlots = Math.max(0, snapshot.slotCount() - containerSlots);
         return containerSlots + " container slots + " + playerSlots + " player slots";
      } else {
         return "unknown";
      }
   }

   private static int countNonEmpty(Collection<ItemStack> items) {
      int count = 0;
      if (items != null) {
         for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
               count++;
            }
         }
      }

      return count;
   }

   private static String describeInput(ContainerInput input) {
      if (input == null) {
         return "unknown";
      } else {
         return switch (input) {
            case PICKUP -> "PICKUP left/right click pickup/place";
            case QUICK_MOVE -> "QUICK_MOVE shift-click transfer";
            case SWAP -> "SWAP hotbar/offhand swap";
            case CLONE -> "CLONE creative middle-click";
            case THROW -> "THROW drop from slot";
            case QUICK_CRAFT -> "QUICK_CRAFT drag distribute";
            case PICKUP_ALL -> "PICKUP_ALL gather matching carried stack";
            default -> throw new MatchException(null, null);
         };
      }
   }

   private static String describeButton(ContainerInput input, int button) {
      if (input == ContainerInput.PICKUP) {
         return button == 0 ? "0 left click" : (button == 1 ? "1 right click" : String.valueOf(button));
      } else if (input == ContainerInput.QUICK_MOVE) {
         return button == 0 ? "0 shift-left" : (button == 1 ? "1 shift-right" : String.valueOf(button));
      } else if (input == ContainerInput.SWAP) {
         return button == 40 ? "40 offhand" : button + " hotbar slot";
      } else if (input == ContainerInput.THROW) {
         return button == 0 ? "0 drop one" : (button == 1 ? "1 drop stack" : String.valueOf(button));
      } else {
         return String.valueOf(button);
      }
   }

   private static String summarizeMaybeItem(Object value) {
      return value instanceof ItemStack stack ? RiptidePacketContextTracker.summarizeItem(stack) : safe(value);
   }

   private static String formatRelatives(Set<Relative> relatives) {
      return relatives != null && !relatives.isEmpty() ? relatives.toString() : "none / absolute";
   }

   private static String formatVec3(Vec3 vec) {
      return vec == null ? "unknown" : String.format(Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f", vec.x, vec.y, vec.z);
   }

   private static String formatFloat(float value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static Object call(Object target, String... methodNames) {
      if (target != null && methodNames != null) {
         for (String methodName : methodNames) {
            if (methodName != null && !methodName.isBlank()) {
               try {
                  Method method = target.getClass().getMethod(methodName);
                  if (method.getParameterCount() == 0) {
                     return method.invoke(target);
                  }
               } catch (Throwable var7) {
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String safe(Object value) {
      if (value == null) {
         return "unknown";
      } else if (value instanceof ItemStack stack) {
         return RiptidePacketContextTracker.summarizeItem(stack);
      } else {
         try {
            if (value instanceof Optional<?> optional) {
               return optional.map(RiptideNormalPacketAnalyzer::safe).orElse("empty");
            } else if (value instanceof Holder<?> holder) {
               return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(String.valueOf(holder.value()));
            } else {
               return value instanceof Item item ? BuiltInRegistries.ITEM.getKey(item).toString() : String.valueOf(value);
            }
         } catch (Throwable var2) {
            return value.getClass().getSimpleName();
         }
      }
   }

   private static String quote(String value) {
      if (value == null) {
         return "\"\"";
      } else {
         String trimmed = value.length() > 96 ? value.substring(0, 93) + "..." : value;
         return "\"" + trimmed + "\"";
      }
   }

   public record Analysis(
      List<RiptidePacketInspector.InspectionLine> decodedLines,
      List<RiptidePacketInspector.InspectionLine> contextLines,
      List<RiptidePacketInspector.InspectionLine> wireLines,
      boolean complete,
      String status
   ) {
      public Analysis(
         List<RiptidePacketInspector.InspectionLine> decodedLines,
         List<RiptidePacketInspector.InspectionLine> contextLines,
         List<RiptidePacketInspector.InspectionLine> wireLines,
         boolean complete,
         String status
      ) {
         decodedLines = decodedLines == null ? List.of() : List.copyOf(decodedLines);
         contextLines = contextLines == null ? List.of() : List.copyOf(contextLines);
         wireLines = wireLines == null ? List.of() : List.copyOf(wireLines);
         status = status == null ? "fallback" : status;
         this.decodedLines = decodedLines;
         this.contextLines = contextLines;
         this.wireLines = wireLines;
         this.complete = complete;
         this.status = status;
      }

      public boolean hasDecoded() {
         return !this.decodedLines.isEmpty();
      }

      public boolean hasContext() {
         return !this.contextLines.isEmpty();
      }

      public boolean hasWire() {
         return !this.wireLines.isEmpty();
      }
   }

   private static final class Builder {
      private final List<RiptidePacketInspector.InspectionLine> decoded = new ArrayList<>();
      private final List<RiptidePacketInspector.InspectionLine> context = new ArrayList<>();
      private final List<RiptidePacketInspector.InspectionLine> wire = new ArrayList<>();

      void decoded(String text, int color) {
         this.decoded.add(new RiptidePacketInspector.InspectionLine(text, color));
      }

      void context(String text, int color) {
         this.context.add(new RiptidePacketInspector.InspectionLine(text, color));
      }

      void wire(String text, int color) {
         this.wire.add(new RiptidePacketInspector.InspectionLine(text, color));
      }

      boolean hasDecoded() {
         return !this.decoded.isEmpty();
      }

      RiptideNormalPacketAnalyzer.Analysis build(boolean complete, String status) {
         return new RiptideNormalPacketAnalyzer.Analysis(this.decoded, this.context, this.wire, complete, status);
      }
   }

   private record WireDecode(Packet<?> packet, boolean success, String message) {
      static RiptideNormalPacketAnalyzer.WireDecode none() {
         return new RiptideNormalPacketAnalyzer.WireDecode(null, false, "");
      }

      static RiptideNormalPacketAnalyzer.WireDecode success(Packet<?> packet, String message) {
         return new RiptideNormalPacketAnalyzer.WireDecode(packet, true, message);
      }

      static RiptideNormalPacketAnalyzer.WireDecode failure(String message) {
         return new RiptideNormalPacketAnalyzer.WireDecode(null, false, message);
      }

      String status() {
         return this.success ? "wire-codec" : "fallback";
      }
   }
}
