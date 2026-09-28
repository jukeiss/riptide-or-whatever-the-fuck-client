package riptide.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.HashedStack.ActualItem;
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
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class RiptidePacketContextTracker {
   private static final Minecraft MC = Minecraft.getInstance();
   public static final RiptidePacketContextTracker.Capture EMPTY_CAPTURE = new RiptidePacketContextTracker.Capture(
      RiptidePacketContextTracker.Snapshot.EMPTY, RiptidePacketContextTracker.Snapshot.EMPTY, List.of(), false
   );
   private boolean contextStarted;
   private int activeContainerId = -1;
   private int activeContainerStateId = -1;
   private String activeScreenType = "";
   private String activeScreenTitle = "";
   private int slotCount = -1;
   private int containerSlotCount = -1;
   private String cursorItem = "unknown";
   private int selectedHotbarSlot = -1;
   private RiptidePacketContextTracker.Position lastClientPosition;
   private RiptidePacketContextTracker.Position lastServerPosition;
   private int lastTeleportId = -1;
   private String dimension = "";
   private Integer chunkCenterX;
   private Integer chunkCenterZ;
   private Integer chunkRadius;
   private Integer simulationDistance;
   private String protocolState = "unknown";
   private final Map<Integer, String> containerSlots = new LinkedHashMap<>();
   private final Map<Integer, String> playerInventorySlots = new LinkedHashMap<>();

   public synchronized void reset() {
      this.contextStarted = false;
      this.activeContainerId = -1;
      this.activeContainerStateId = -1;
      this.activeScreenType = "";
      this.activeScreenTitle = "";
      this.slotCount = -1;
      this.containerSlotCount = -1;
      this.cursorItem = "unknown";
      this.selectedHotbarSlot = -1;
      this.lastClientPosition = null;
      this.lastServerPosition = null;
      this.lastTeleportId = -1;
      this.dimension = "";
      this.chunkCenterX = null;
      this.chunkCenterZ = null;
      this.chunkRadius = null;
      this.simulationDistance = null;
      this.protocolState = "unknown";
      this.containerSlots.clear();
      this.playerInventorySlots.clear();
   }

   public synchronized RiptidePacketContextTracker.Capture capture(Packet<?> packet, String direction) {
      if (!isRelevant(packet)) {
         return EMPTY_CAPTURE;
      } else {
         this.seedFromClientIfUseful();
         RiptidePacketContextTracker.Snapshot before = this.snapshot();
         List<String> changes = new ArrayList<>();
         this.apply(packet, direction == null ? "" : direction.toUpperCase(Locale.ROOT), changes);
         RiptidePacketContextTracker.Snapshot after = this.snapshot();
         this.contextStarted = true;
         return new RiptidePacketContextTracker.Capture(before, after, List.copyOf(changes), true);
      }
   }

   public static boolean isRelevant(Packet<?> packet) {
      return packet == null
         ? false
         : packet instanceof ClientboundOpenScreenPacket
            || packet instanceof ClientboundContainerClosePacket
            || packet instanceof ClientboundContainerSetContentPacket
            || packet instanceof ClientboundContainerSetSlotPacket
            || packet instanceof ClientboundContainerSetDataPacket
            || packet instanceof ClientboundSetCursorItemPacket
            || packet instanceof ClientboundSetPlayerInventoryPacket
            || packet instanceof ClientboundSetHeldSlotPacket
            || packet instanceof ServerboundSetCarriedItemPacket
            || packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerClosePacket
            || packet instanceof ServerboundMovePlayerPacket
            || packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ServerboundAcceptTeleportationPacket
            || packet instanceof ClientboundLoginPacket
            || packet instanceof ClientboundRespawnPacket
            || packet instanceof ClientboundTransferPacket
            || packet instanceof ClientboundStartConfigurationPacket
            || packet instanceof ClientboundFinishConfigurationPacket
            || packet instanceof ClientboundSetChunkCacheCenterPacket
            || packet instanceof ClientboundSetChunkCacheRadiusPacket
            || packet instanceof ClientboundSetSimulationDistancePacket
            || packet instanceof ClientboundForgetLevelChunkPacket
            || packet instanceof ClientboundLevelChunkWithLightPacket
            || packet instanceof ClientboundGameEventPacket
            || packet instanceof ClientboundResourcePackPushPacket
            || packet instanceof ClientboundResourcePackPopPacket
            || packet instanceof ClientboundDisconnectPacket;
   }

   private void apply(Packet<?> packet, String direction, List<String> changes) {
      if (packet instanceof ClientboundLoginPacket login) {
         this.clearScreenState();
         this.protocolState = "play";
         this.dimension = String.valueOf(login.commonPlayerSpawnInfo().dimension().identifier());
         this.chunkRadius = login.chunkRadius();
         this.simulationDistance = login.simulationDistance();
         changes.add("Entered world " + this.dimension + " as entity #" + login.playerId());
      } else if (packet instanceof ClientboundRespawnPacket respawn) {
         this.clearScreenState();
         this.protocolState = "play";
         this.dimension = String.valueOf(respawn.commonPlayerSpawnInfo().dimension().identifier());
         changes.add("Respawned / switched world to " + this.dimension);
      } else if (packet instanceof ClientboundTransferPacket transfer) {
         this.clearScreenState();
         this.protocolState = "transfer " + transfer.host() + ":" + transfer.port();
         changes.add("Server transfer to " + transfer.host() + ":" + transfer.port());
      } else if (packet instanceof ClientboundStartConfigurationPacket) {
         this.protocolState = "configuration";
         changes.add("Server moved connection into configuration phase");
      } else if (packet instanceof ClientboundFinishConfigurationPacket) {
         this.protocolState = "configuration finished";
         changes.add("Configuration phase finished");
      } else if (packet instanceof ClientboundDisconnectPacket) {
         changes.add("Disconnected; tracked packet context cleared");
         this.reset();
      } else if (packet instanceof ClientboundOpenScreenPacket open) {
         this.activeContainerId = open.getContainerId();
         this.activeContainerStateId = -1;
         this.activeScreenType = safeString(open.getType());
         this.activeScreenTitle = open.getTitle() == null ? "" : open.getTitle().getString();
         this.slotCount = -1;
         this.containerSlotCount = -1;
         this.containerSlots.clear();
         changes.add("Opened container #" + this.activeContainerId + " " + quote(this.activeScreenTitle));
      } else if (!(packet instanceof ClientboundContainerSetContentPacket content)) {
         if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            this.activeContainerStateId = slot.getStateId();
            String previous = this.slotItem(slot.getSlot());
            String next = summarizeItem(slot.getItem());
            if (slot.getContainerId() == -1) {
               this.cursorItem = next;
               changes.add("Cursor changed " + previous + " -> " + next);
            } else if (slot.getContainerId() == -2) {
               this.playerInventorySlots.put(slot.getSlot(), next);
               changes.add("Player inventory slot " + slot.getSlot() + " changed " + previous + " -> " + next);
            } else {
               if (this.activeContainerId < 0) {
                  this.activeContainerId = slot.getContainerId();
               }

               this.containerSlots.put(slot.getSlot(), next);
               changes.add("Slot " + slot.getSlot() + " changed " + previous + " -> " + next);
            }
         } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            String previous = this.cursorItem;
            this.cursorItem = summarizeItem(cursor.contents());
            changes.add("Cursor changed " + previous + " -> " + this.cursorItem);
         } else if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            String previous = this.playerInventorySlots.getOrDefault(inventory.slot(), "unknown");
            String next = summarizeItem(inventory.contents());
            this.playerInventorySlots.put(inventory.slot(), next);
            changes.add("Player inventory slot " + inventory.slot() + " changed " + previous + " -> " + next);
         } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
            int previous = this.selectedHotbarSlot;
            this.selectedHotbarSlot = held.slot();
            changes.add("Held hotbar slot changed " + previous + " -> " + this.selectedHotbarSlot);
         } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            int previous = this.selectedHotbarSlot;
            this.selectedHotbarSlot = carried.getSlot();
            changes.add("Client selected hotbar slot " + previous + " -> " + this.selectedHotbarSlot);
         } else if (packet instanceof ServerboundContainerClickPacket click) {
            this.activeContainerId = click.containerId();
            this.activeContainerStateId = click.stateId();
            this.applyChangedSlots(click.changedSlots());
            this.cursorItem = summarizeHashedStack(click.carriedItem());
            changes.add(
               "Client " + describeInput(click.containerInput()) + " on slot " + click.slotNum() + " changed " + click.changedSlots().size() + " slots"
            );
         } else if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
            changes.add("Closed container #" + this.activeContainerId);
            this.clearScreenState();
         } else if (packet instanceof ServerboundMovePlayerPacket move) {
            RiptidePacketContextTracker.Position before = this.lastClientPosition == null ? currentPlayerPosition() : this.lastClientPosition;
            double x = move.getX(before == null ? 0.0 : before.x());
            double y = move.getY(before == null ? 0.0 : before.y());
            double z = move.getZ(before == null ? 0.0 : before.z());
            float yaw = move.getYRot(before == null ? 0.0F : before.yaw());
            float pitch = move.getXRot(before == null ? 0.0F : before.pitch());
            this.lastClientPosition = new RiptidePacketContextTracker.Position(x, y, z, yaw, pitch, move.hasPosition(), move.hasRotation());
            if (before != null) {
               changes.add("Client move delta " + formatDistance(before.distanceTo(this.lastClientPosition)) + " blocks");
            }
         } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            RiptidePacketContextTracker.Position base = this.lastServerPosition == null ? currentPlayerPosition() : this.lastServerPosition;
            RiptidePacketContextTracker.Position next = computeServerPosition(base, position.change(), position.relatives());
            this.lastServerPosition = next;
            this.lastTeleportId = position.id();
            changes.add("Server correction/teleport #" + this.lastTeleportId + " -> " + formatPosition(next));
         } else if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
            changes.add(
               "Client acknowledged teleport #"
                  + ack.getId()
                  + (ack.getId() == this.lastTeleportId ? " (matches pending)" : " (tracked pending " + this.lastTeleportId + ")")
            );
            if (ack.getId() == this.lastTeleportId) {
               this.lastTeleportId = -1;
            }
         } else if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            this.chunkCenterX = center.getX();
            this.chunkCenterZ = center.getZ();
            changes.add("Chunk cache center -> " + this.chunkCenterX + ", " + this.chunkCenterZ);
         } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            this.chunkRadius = radius.getRadius();
            changes.add("Chunk cache radius -> " + this.chunkRadius);
         } else if (packet instanceof ClientboundSetSimulationDistancePacket sim) {
            this.simulationDistance = sim.simulationDistance();
            changes.add("Simulation distance -> " + this.simulationDistance);
         } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            changes.add("Unload chunk " + forget.pos().x() + ", " + forget.pos().z());
         } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            changes.add("Load chunk " + chunk.getX() + ", " + chunk.getZ() + " with light");
         } else if (packet instanceof ClientboundGameEventPacket gameEvent) {
            changes.add("Game event " + gameEvent.getEvent() + " value=" + gameEvent.getParam());
         } else if (packet instanceof ClientboundResourcePackPushPacket pack) {
            changes.add("Resource pack push " + pack.id() + " required=" + pack.required());
         } else {
            if (packet instanceof ClientboundResourcePackPopPacket pack) {
               changes.add("Resource pack pop " + pack.id().map(Object::toString).orElse("latest"));
            }
         }
      } else {
         this.activeContainerId = content.containerId();
         this.activeContainerStateId = content.stateId();
         this.slotCount = content.items().size();
         this.containerSlotCount = estimateContainerSlotCount(this.activeContainerId, this.slotCount);
         this.containerSlots.clear();

         for (int i = 0; i < content.items().size(); i++) {
            this.containerSlots.put(i, summarizeItem((ItemStack)content.items().get(i)));
         }

         this.cursorItem = summarizeItem(content.carriedItem());
         changes.add("Synced " + this.slotCount + " slots (" + nonEmpty(content.items()) + " non-empty)");
      }
   }

   private void seedFromClientIfUseful() {
      if (this.lastClientPosition == null) {
         this.lastClientPosition = currentPlayerPosition();
      }

      if (this.lastServerPosition == null) {
         this.lastServerPosition = currentPlayerPosition();
      }

      if (this.selectedHotbarSlot < 0 && MC != null && MC.player != null) {
         try {
            this.selectedHotbarSlot = MC.player.getInventory().getSelectedSlot();
         } catch (Throwable var2) {
         }
      }
   }

   private RiptidePacketContextTracker.Snapshot snapshot() {
      return new RiptidePacketContextTracker.Snapshot(
         this.contextStarted,
         this.activeContainerId,
         this.activeContainerStateId,
         this.activeScreenType,
         this.activeScreenTitle,
         this.slotCount,
         this.containerSlotCount,
         this.cursorItem,
         this.selectedHotbarSlot,
         this.lastClientPosition,
         this.lastServerPosition,
         this.lastTeleportId,
         this.dimension,
         this.chunkCenterX,
         this.chunkCenterZ,
         this.chunkRadius,
         this.simulationDistance,
         this.protocolState,
         Collections.unmodifiableMap(new LinkedHashMap<>(this.containerSlots)),
         Collections.unmodifiableMap(new LinkedHashMap<>(this.playerInventorySlots))
      );
   }

   private void clearScreenState() {
      this.activeContainerId = -1;
      this.activeContainerStateId = -1;
      this.activeScreenType = "";
      this.activeScreenTitle = "";
      this.slotCount = -1;
      this.containerSlotCount = -1;
      this.cursorItem = "unknown";
      this.containerSlots.clear();
   }

   private void applyChangedSlots(Int2ObjectMap<HashedStack> changedSlots) {
      if (changedSlots != null) {
         ObjectIterator var2 = changedSlots.int2ObjectEntrySet().iterator();

         while (var2.hasNext()) {
            Entry<HashedStack> entry = (Entry<HashedStack>)var2.next();
            this.containerSlots.put(entry.getIntKey(), summarizeHashedStack((HashedStack)entry.getValue()));
         }
      }
   }

   private String slotItem(int slot) {
      if (slot < 0) {
         return "outside";
      } else {
         String value = this.containerSlots.get(slot);
         return value != null ? value : this.playerInventorySlots.getOrDefault(slot, "unknown");
      }
   }

   private static int estimateContainerSlotCount(int containerId, int totalSlots) {
      if (totalSlots < 0) {
         return -1;
      } else if (containerId == 0) {
         return Math.max(0, totalSlots - 36);
      } else {
         return totalSlots >= 36 ? Math.max(0, totalSlots - 36) : -1;
      }
   }

   private static int nonEmpty(List<ItemStack> stacks) {
      int count = 0;
      if (stacks != null) {
         for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
               count++;
            }
         }
      }

      return count;
   }

   public static String summarizeItem(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
         StringBuilder out = new StringBuilder();
         out.append(stack.getCount()).append('x').append(' ').append(id);

         try {
            String display = stack.getHoverName() == null ? "" : stack.getHoverName().getString();
            String vanilla = stack.getItem().getName(stack).getString();
            if (!display.isBlank() && !Objects.equals(display, vanilla)) {
               out.append(" [").append(shorten(display, 48)).append(']');
            }
         } catch (Throwable var6) {
         }

         try {
            if (stack.isDamageableItem()) {
               int max = stack.getMaxDamage();
               int damage = stack.getDamageValue();
               out.append(" dur=").append(Math.max(0, max - damage)).append('/').append(max);
            }
         } catch (Throwable var5) {
         }

         return out.toString();
      } else {
         return "empty";
      }
   }

   public static String summarizeHashedStack(HashedStack stack) {
      if (stack == null || stack == HashedStack.EMPTY) {
         return "empty";
      } else if (stack instanceof ActualItem actual) {
         String id = actual.item().unwrapKey().map(key -> key.identifier().toString()).orElseGet(() -> safeString(actual.item()));
         return actual.count() + "x " + id + componentHint(actual.components());
      } else {
         return safeString(stack);
      }
   }

   private static String componentHint(Object components) {
      String value = safeString(components);
      return value != null && !value.isBlank() && !"HashedPatchMap[hashedPatches={}]".equals(value) ? " components" : "";
   }

   private static RiptidePacketContextTracker.Position computeServerPosition(
      RiptidePacketContextTracker.Position base, PositionMoveRotation change, Set<Relative> relatives
   ) {
      Vec3 pos = change.position();
      Vec3 delta = change.deltaMovement();
      float yaw = change.yRot();
      float pitch = change.xRot();
      if (base != null && relatives != null) {
         if (relatives.contains(Relative.X)) {
            pos = new Vec3(base.x() + pos.x, pos.y, pos.z);
         }

         if (relatives.contains(Relative.Y)) {
            pos = new Vec3(pos.x, base.y() + pos.y, pos.z);
         }

         if (relatives.contains(Relative.Z)) {
            pos = new Vec3(pos.x, pos.y, base.z() + pos.z);
         }

         if (relatives.contains(Relative.Y_ROT)) {
            yaw += base.yaw();
         }

         if (relatives.contains(Relative.X_ROT)) {
            pitch += base.pitch();
         }
      }

      return new RiptidePacketContextTracker.Position(pos.x, pos.y, pos.z, yaw, pitch, true, true);
   }

   private static RiptidePacketContextTracker.Position currentPlayerPosition() {
      if (MC != null && MC.player != null) {
         try {
            return new RiptidePacketContextTracker.Position(
               MC.player.getX(), MC.player.getY(), MC.player.getZ(), MC.player.getYRot(), MC.player.getXRot(), true, true
            );
         } catch (Throwable var1) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String describeInput(ContainerInput input) {
      if (input == null) {
         return "clicked";
      } else {
         return switch (input) {
            case PICKUP -> "picked up / placed";
            case QUICK_MOVE -> "quick-moved";
            case SWAP -> "hotbar-swapped";
            case CLONE -> "cloned";
            case THROW -> "threw";
            case QUICK_CRAFT -> "drag-crafted";
            case PICKUP_ALL -> "picked up all matching";
            default -> throw new MatchException(null, null);
         };
      }
   }

   private static String formatDistance(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }

   public static String formatPosition(RiptidePacketContextTracker.Position position) {
      return position == null
         ? "unknown"
         : String.format(
            Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f, yaw=%.2f, pitch=%.2f", position.x(), position.y(), position.z(), position.yaw(), position.pitch()
         );
   }

   public static String slotArea(RiptidePacketContextTracker.Snapshot snapshot, int slot) {
      if (slot < 0) {
         return "outside / carried item area";
      } else if (snapshot != null && snapshot.slotCount() > 0 && snapshot.containerSlotCount() >= 0) {
         int containerSlots = snapshot.containerSlotCount();
         if (slot < containerSlots) {
            return "container slot " + slot;
         } else {
            int playerRelative = slot - containerSlots;
            if (playerRelative >= 0 && playerRelative < 27) {
               return "player inventory slot " + (playerRelative + 9);
            } else {
               return playerRelative >= 27 && playerRelative < 36 ? "hotbar slot " + (playerRelative - 27) : "handler slot " + slot;
            }
         }
      } else {
         return "handler slot " + slot;
      }
   }

   private static String quote(String value) {
      return value != null && !value.isBlank() ? "\"" + shorten(value, 72) + "\"" : "\"\"";
   }

   private static String shorten(String value, int max) {
      if (value == null) {
         return "";
      } else {
         return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
      }
   }

   private static String safeString(Object value) {
      if (value == null) {
         return "";
      } else {
         try {
            return String.valueOf(value);
         } catch (Throwable var2) {
            return value.getClass().getSimpleName();
         }
      }
   }

   public record Capture(RiptidePacketContextTracker.Snapshot before, RiptidePacketContextTracker.Snapshot after, List<String> changes, boolean relevant) {
      public Capture(RiptidePacketContextTracker.Snapshot before, RiptidePacketContextTracker.Snapshot after, List<String> changes, boolean relevant) {
         changes = changes == null ? List.of() : List.copyOf(changes);
         before = before == null ? RiptidePacketContextTracker.Snapshot.EMPTY : before;
         after = after == null ? RiptidePacketContextTracker.Snapshot.EMPTY : after;
         this.before = before;
         this.after = after;
         this.changes = changes;
         this.relevant = relevant;
      }
   }

   public record Position(double x, double y, double z, float yaw, float pitch, boolean hasPosition, boolean hasRotation) {
      public double distanceTo(RiptidePacketContextTracker.Position other) {
         if (other == null) {
            return 0.0;
         } else {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
         }
      }
   }

   public record Snapshot(
      boolean known,
      int containerId,
      int containerStateId,
      String screenType,
      String screenTitle,
      int slotCount,
      int containerSlotCount,
      String cursorItem,
      int selectedHotbarSlot,
      RiptidePacketContextTracker.Position clientPosition,
      RiptidePacketContextTracker.Position serverPosition,
      int lastTeleportId,
      String dimension,
      Integer chunkCenterX,
      Integer chunkCenterZ,
      Integer chunkRadius,
      Integer simulationDistance,
      String protocolState,
      Map<Integer, String> containerSlots,
      Map<Integer, String> playerInventorySlots
   ) {
      public static final RiptidePacketContextTracker.Snapshot EMPTY = new RiptidePacketContextTracker.Snapshot(
         false, -1, -1, "", "", -1, -1, "unknown", -1, null, null, -1, "", null, null, null, null, "unknown", Map.of(), Map.of()
      );

      public Snapshot(
         boolean known,
         int containerId,
         int containerStateId,
         String screenType,
         String screenTitle,
         int slotCount,
         int containerSlotCount,
         String cursorItem,
         int selectedHotbarSlot,
         RiptidePacketContextTracker.Position clientPosition,
         RiptidePacketContextTracker.Position serverPosition,
         int lastTeleportId,
         String dimension,
         Integer chunkCenterX,
         Integer chunkCenterZ,
         Integer chunkRadius,
         Integer simulationDistance,
         String protocolState,
         Map<Integer, String> containerSlots,
         Map<Integer, String> playerInventorySlots
      ) {
         screenType = screenType == null ? "" : screenType;
         screenTitle = screenTitle == null ? "" : screenTitle;
         cursorItem = cursorItem == null ? "unknown" : cursorItem;
         dimension = dimension == null ? "" : dimension;
         protocolState = protocolState == null ? "unknown" : protocolState;
         containerSlots = containerSlots == null ? Map.of() : Map.copyOf(containerSlots);
         playerInventorySlots = playerInventorySlots == null ? Map.of() : Map.copyOf(playerInventorySlots);
         this.known = known;
         this.containerId = containerId;
         this.containerStateId = containerStateId;
         this.screenType = screenType;
         this.screenTitle = screenTitle;
         this.slotCount = slotCount;
         this.containerSlotCount = containerSlotCount;
         this.cursorItem = cursorItem;
         this.selectedHotbarSlot = selectedHotbarSlot;
         this.clientPosition = clientPosition;
         this.serverPosition = serverPosition;
         this.lastTeleportId = lastTeleportId;
         this.dimension = dimension;
         this.chunkCenterX = chunkCenterX;
         this.chunkCenterZ = chunkCenterZ;
         this.chunkRadius = chunkRadius;
         this.simulationDistance = simulationDistance;
         this.protocolState = protocolState;
         this.containerSlots = containerSlots;
         this.playerInventorySlots = playerInventorySlots;
      }

      public String slotItem(int slot) {
         if (slot < 0) {
            return "outside";
         } else {
            String value = this.containerSlots.get(slot);
            return value != null ? value : this.playerInventorySlots.getOrDefault(slot, "unknown");
         }
      }
   }
}
