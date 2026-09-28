package riptide.util.multi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.level.ChunkPos;

final class MultiWorldCapture {
   private static final int CHUNK_CAP = 2048;
   private static final int ENTITY_CAP = 4096;
   private static final int PLAYER_CAP = 4096;
   private ClientboundLoginPacket login;
   private ClientboundRespawnPacket respawn;
   private ClientboundSetChunkCacheCenterPacket cacheCenter;
   private ClientboundSetChunkCacheRadiusPacket cacheRadius;
   private ClientboundOpenScreenPacket openScreen;
   private ClientboundContainerSetContentPacket containerContent;
   private final Map<Long, ClientboundLevelChunkWithLightPacket> chunks = new LinkedHashMap<>();
   private final Map<Integer, ClientboundAddEntityPacket> entitySpawns = new LinkedHashMap<>();
   private final Map<Integer, ClientboundSetEntityDataPacket> entityData = new LinkedHashMap<>();
   private final Map<UUID, ClientboundPlayerInfoUpdatePacket> playerInfoByUuid = new LinkedHashMap<>();

   synchronized void capture(Packet<?> packet) {
      if (packet instanceof ClientboundLoginPacket loginPacket) {
         this.login = loginPacket;
         this.respawn = null;
         this.resetWorld();
      } else if (packet instanceof ClientboundRespawnPacket respawnPacket) {
         this.respawn = respawnPacket;
         this.resetWorld();
      } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         long key = ChunkPos.pack(chunk.getX(), chunk.getZ());
         this.chunks.remove(key);
         this.chunks.put(key, chunk);
         if (this.chunks.size() > 2048) {
            evictOldest(this.chunks);
         }
      } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
         this.chunks.remove(forget.pos().pack());
      } else if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
         this.cacheCenter = center;
      } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
         this.cacheRadius = radius;
      } else if (packet instanceof ClientboundAddEntityPacket add) {
         this.entitySpawns.remove(add.getId());
         this.entitySpawns.put(add.getId(), add);
         if (this.entitySpawns.size() > 4096) {
            evictOldest(this.entitySpawns);
         }
      } else if (packet instanceof ClientboundSetEntityDataPacket data) {
         if (this.entitySpawns.containsKey(data.id())) {
            this.entityData.put(data.id(), data);
         }
      } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
         for (int i = 0; i < remove.getEntityIds().size(); i++) {
            int id = remove.getEntityIds().getInt(i);
            this.entitySpawns.remove(id);
            this.entityData.remove(id);
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
         for (Entry entry : info.entries()) {
            this.playerInfoByUuid.put(entry.profileId(), info);
         }

         while (this.playerInfoByUuid.size() > 4096) {
            evictOldest(this.playerInfoByUuid);
         }
      } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
         for (UUID id : remove.profileIds()) {
            this.playerInfoByUuid.remove(id);
         }
      } else if (packet instanceof ClientboundOpenScreenPacket open) {
         this.openScreen = open;
         this.containerContent = null;
      } else if (packet instanceof ClientboundContainerSetContentPacket content) {
         this.containerContent = content;
      } else if (packet instanceof ClientboundContainerClosePacket) {
         this.openScreen = null;
         this.containerContent = null;
      }
   }

   private void resetWorld() {
      this.chunks.clear();
      this.entitySpawns.clear();
      this.entityData.clear();
      this.playerInfoByUuid.clear();
      this.cacheCenter = null;
      this.cacheRadius = null;
      this.openScreen = null;
      this.containerContent = null;
   }

   private static void evictOldest(Map<?, ?> map) {
      Iterator<?> it = map.keySet().iterator();
      if (it.hasNext()) {
         it.next();
         it.remove();
      }
   }

   synchronized boolean hasWorld() {
      return this.login != null;
   }

   synchronized MultiWorldCapture.Snapshot snapshot() {
      List<ClientboundPlayerInfoUpdatePacket> distinctInfo = new ArrayList<>(new LinkedHashSet<>(this.playerInfoByUuid.values()));
      return new MultiWorldCapture.Snapshot(
         this.login,
         this.respawn,
         this.cacheCenter,
         this.cacheRadius,
         new ArrayList<>(this.chunks.values()),
         distinctInfo,
         new ArrayList<>(this.entitySpawns.values()),
         new ArrayList<>(this.entityData.values()),
         this.openScreen,
         this.containerContent
      );
   }

   synchronized void clear() {
      this.login = null;
      this.respawn = null;
      this.resetWorld();
   }

   record Snapshot(
      ClientboundLoginPacket login,
      ClientboundRespawnPacket respawn,
      ClientboundSetChunkCacheCenterPacket cacheCenter,
      ClientboundSetChunkCacheRadiusPacket cacheRadius,
      List<ClientboundLevelChunkWithLightPacket> chunks,
      List<ClientboundPlayerInfoUpdatePacket> playerInfo,
      List<ClientboundAddEntityPacket> entities,
      List<ClientboundSetEntityDataPacket> entityData,
      ClientboundOpenScreenPacket openScreen,
      ClientboundContainerSetContentPacket containerContent
   ) {
   }
}
