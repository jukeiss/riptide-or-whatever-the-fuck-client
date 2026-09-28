package riptide.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PacketRegenerator {
   private static final Map<Class<? extends Packet<?>>, PacketRegenerator.PacketHandler<?>> handlers = new HashMap<>();

   public static <T extends Packet<?>> T regenerate(T original) {
      if (original == null) {
         return null;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return original;
         } else {
            PacketRegenerator.PacketHandler<T> handler = (PacketRegenerator.PacketHandler<T>)handlers.get(original.getClass());
            if (handler != null) {
               try {
                  return handler.regenerate(original, mc);
               } catch (Exception var4) {
                  riptide.RiptideClientAddon.LOG.error("[Riptide] Failed to regenerate {}: {}", original.getClass().getSimpleName(), var4.getMessage());
                  return original;
               }
            } else {
               return original;
            }
         }
      }
   }

   private static void registerAllHandlers() {
      register(
         ServerboundPlayerActionPacket.class, (packet, mc) -> new ServerboundPlayerActionPacket(packet.getAction(), packet.getPos(), packet.getDirection(), 0)
      );
      register(
         ServerboundUseItemOnPacket.class,
         (packet, mc) -> {
            BlockPos targetBlock = packet.getHitResult().getBlockPos();
            Direction face = packet.getHitResult().getDirection();
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 hitPos = new Vec3(
               targetBlock.getX() + 0.5 + face.getStepX() * 0.5,
               targetBlock.getY() + 0.5 + face.getStepY() * 0.5,
               targetBlock.getZ() + 0.5 + face.getStepZ() * 0.5
            );
            BlockHitResult newHit = new BlockHitResult(hitPos, face, targetBlock, false);
            return new ServerboundUseItemOnPacket(packet.getHand(), newHit, 0);
         }
      );
      register(ServerboundContainerClickPacket.class, (packet, mc) -> {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler == null) {
            return packet;
         } else {
            int currentSyncId = handler.containerId;
            int currentRevision = handler.getStateId();

            try {
               int slot = getFieldInt(packet, "slot", short.class, 0);
               int button = getFieldInt(packet, "button", byte.class, 0);
               ContainerInput action = getFieldEnum(packet, ContainerInput.class);
               Int2ObjectMap<HashedStack> modifiedStacks = new Int2ObjectArrayMap();
               return new ServerboundContainerClickPacket(currentSyncId, currentRevision, (short)slot, (byte)button, action, modifiedStacks, HashedStack.EMPTY);
            } catch (Exception var9) {
               riptide.RiptideClientAddon.LOG.error("[Riptide] ClickSlot regeneration failed: {}", var9.getMessage());
               return packet;
            }
         }
      });
      register(ServerboundContainerButtonClickPacket.class, (packet, mc) -> {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler == null) {
            return packet;
         } else {
            int buttonId = getFieldInt(packet, "buttonId", int.class, 1);
            return new ServerboundContainerButtonClickPacket(handler.containerId, buttonId);
         }
      });
      register(ServerboundUseItemPacket.class, (packet, mc) -> new ServerboundUseItemPacket(packet.getHand(), 0, mc.player.getYRot(), mc.player.getXRot()));
      register(
         PosRot.class,
         (packet, mc) -> new PosRot(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision
         )
      );
      register(Pos.class, (packet, mc) -> new Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision));
      register(Rot.class, (packet, mc) -> new Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
      register(StatusOnly.class, (packet, mc) -> new StatusOnly(mc.player.onGround(), mc.player.horizontalCollision));
      register(
         ServerboundInteractPacket.class,
         (packet, mc) -> {
            int originalEntityId = packet.entityId();
            Entity originalEntity = mc.level != null ? mc.level.getEntity(originalEntityId) : null;
            if (originalEntity == null && mc.level != null) {
               Vec3 playerPos = mc.player.getEyePosition().subtract(0.0, mc.player.getEyeHeight(), 0.0);
               AABB searchAABB = new AABB(playerPos.subtract(5.0, 5.0, 5.0), playerPos.add(5.0, 5.0, 5.0));
               List<Entity> nearby = new ArrayList<>();

               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != null && entity != mc.player && searchAABB.contains(entity.position())) {
                     nearby.add(entity);
                  }
               }

               if (!nearby.isEmpty()) {
                  originalEntity = nearby.stream().min((a, b) -> Double.compare(a.distanceToSqr(playerPos), b.distanceToSqr(playerPos))).orElse(null);
               }
            }

            return originalEntity != null
               ? new ServerboundInteractPacket(originalEntity.getId(), packet.hand(), packet.location(), packet.usingSecondaryAction())
               : packet;
         }
      );
      register(ServerboundSetCarriedItemPacket.class, (packet, mc) -> packet);
      register(ServerboundContainerClosePacket.class, (packet, mc) -> {
         AbstractContainerMenu handler = mc.player.containerMenu;
         return handler == null ? packet : new ServerboundContainerClosePacket(handler.containerId);
      });
      register(ServerboundChatPacket.class, (packet, mc) -> packet);
      register(ServerboundCustomPayloadPacket.class, (packet, mc) -> {
         CustomPacketPayload payload = packet.payload();
         if (payload == null) {
            return null;
         } else {
            String channel = RiptidePayloadSupport.payloadChannel(payload);
            byte[] rawBytes = RiptidePayloadSupport.extractPayloadBytes(payload);
            if (channel != null && !channel.isBlank()) {
               return RiptidePayloadSupport.createC2SPacket(channel, rawBytes);
            } else {
               riptide.RiptideClientAddon.LOG.warn("[Riptide] Cannot regenerate ServerboundCustomPayloadPacket - channel is blank");
               return null;
            }
         }
      });
   }

   private static <T extends Packet<?>> void register(Class<T> clazz, PacketRegenerator.PacketHandler<T> handler) {
      handlers.put(clazz, handler);
   }

   private static int getFieldInt(Object obj, String hint, Class<?> type, int index) {
      try {
         Field field = findField(obj.getClass(), type, index);
         if (type == byte.class) {
            return field.getByte(obj);
         } else {
            return type == short.class ? field.getShort(obj) : field.getInt(obj);
         }
      } catch (Exception var5) {
         return 0;
      }
   }

   private static void setFieldInt(Object obj, String hint, Class<?> type, int index, int value) throws Exception {
      Field field = findField(obj.getClass(), type, index);
      field.setInt(obj, value);
   }

   private static String getFieldString(Object obj, String hint, int index) {
      try {
         Field field = findField(obj.getClass(), String.class, index);
         return (String)field.get(obj);
      } catch (Exception var4) {
         return null;
      }
   }

   private static <E extends Enum<E>> E getFieldEnum(Object obj, Class<E> enumClass) {
      try {
         Field field = findField(obj.getClass(), enumClass, 0);
         return (E)field.get(obj);
      } catch (Exception var3) {
         return null;
      }
   }

   private static Field findField(Class<?> clazz, Class<?> type, int index) throws NoSuchFieldException {
      List<Field> matches = new ArrayList<>();

      for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Field f : current.getDeclaredFields()) {
            if (f.getType().equals(type)) {
               matches.add(f);
            }
         }
      }

      if (index >= 0 && index < matches.size()) {
         Field fx = matches.get(index);
         fx.setAccessible(true);
         return fx;
      } else {
         throw new NoSuchFieldException("Field of type " + type.getName() + " at index " + index + " not found");
      }
   }

   static {
      registerAllHandlers();
   }

   @FunctionalInterface
   public interface PacketHandler<T extends Packet<?>> {
      T regenerate(T var1, Minecraft var2);
   }
}
