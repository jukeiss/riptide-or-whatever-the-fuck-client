package riptide.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class RiptideHoleScanner {
   private static final float BLAST_RESISTANT_AT = 600.0F;
   private static final float INDESTRUCTIBLE_AT = 3600000.0F;
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
   private static final byte UNKNOWN = 0;
   private static final byte AIR = 1;
   private static final byte BREAKABLE = 2;
   private static final byte RESISTANT = 3;
   private static final byte INDESTRUCTIBLE = 4;
   private static final int MOVE_THRESHOLD = 4;
   private static final int REVALIDATE_TICKS = 20;
   private static final int CELLS_PER_PUMP = 16384;
   private static final int MAX_HORIZONTAL = 128;
   private static final int MAX_VERTICAL = 64;
   private static final int MAX_SYNC_CELLS = 64000;
   private static final Set<RiptideHoleScanner.Subscriber> SUBSCRIBERS = new CopyOnWriteArraySet<>();
   private static volatile RiptideHoleScanner.Snapshot published = RiptideHoleScanner.Snapshot.EMPTY;
   private static volatile boolean resetRequested;
   private static RiptideHoleScanner.Scan active;
   private static BlockPos lastCentre;
   private static int lastHorizontal;
   private static int lastVertical;
   private static long lastPumpTick = Long.MIN_VALUE;
   private static long lastFinishedTick = Long.MIN_VALUE;
   private static WeakReference<Level> lastLevel;

   private RiptideHoleScanner() {
   }

   public static void subscribe(RiptideHoleScanner.Subscriber subscriber) {
      if (subscriber != null) {
         SUBSCRIBERS.add(subscriber);
      }
   }

   public static void unsubscribe(RiptideHoleScanner.Subscriber subscriber) {
      if (subscriber != null && SUBSCRIBERS.remove(subscriber) && SUBSCRIBERS.isEmpty()) {
         clear();
      }
   }

   public static boolean running() {
      return !SUBSCRIBERS.isEmpty();
   }

   public static void clear() {
      published = RiptideHoleScanner.Snapshot.EMPTY;
      resetRequested = true;
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         reset();
      } else {
         mc.execute(RiptideHoleScanner::reset);
      }
   }

   public static void tick() {
      pump();
   }

   public static List<RiptideHoleScanner.Hole> holes() {
      pump();
      return published.holes();
   }

   public static RiptideHoleScanner.Hole holeAt(BlockPos pos) {
      pump();
      return pos == null ? null : published.byCell().get(pos.asLong());
   }

   public static boolean isSafeHole(BlockPos pos) {
      return holeAt(pos) != null;
   }

   public static List<RiptideHoleScanner.Hole> scan(BlockPos centre, int horizontalRadius, int verticalRadius) {
      Level level = Minecraft.getInstance().level;
      if (level != null && centre != null) {
         RiptideHoleScanner.Scan scan = new RiptideHoleScanner.Scan(level, centre, horizontalRadius, verticalRadius, 64000L);
         scan.advance(Integer.MAX_VALUE);
         return List.copyOf(scan.found);
      } else {
         return List.of();
      }
   }

   public static RiptideHoleScanner.Hole scanAt(BlockPos pos) {
      Level level = Minecraft.getInstance().level;
      if (level != null && pos != null) {
         RiptideHoleScanner.Scan scan = new RiptideHoleScanner.Scan(level, pos, 0, 0, 64000L);
         scan.advance(Integer.MAX_VALUE);
         return scan.found.isEmpty() ? null : scan.found.get(0);
      } else {
         return null;
      }
   }

   private static void pump() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         if (resetRequested) {
            resetRequested = false;
            reset();
         }

         Level level = mc.level;
         if (level != null && mc.player != null && !SUBSCRIBERS.isEmpty()) {
            if (lastLevel() != level) {
               reset();
               rememberLevel(level);
               published = RiptideHoleScanner.Snapshot.EMPTY;
            }

            long gameTime = level.getGameTime();
            if (gameTime != lastPumpTick) {
               lastPumpTick = gameTime;
               if (active == null) {
                  int horizontal = 0;
                  int vertical = 0;

                  for (RiptideHoleScanner.Subscriber subscriber : SUBSCRIBERS) {
                     horizontal = Math.max(horizontal, subscriber.horizontalDistance());
                     vertical = Math.max(vertical, subscriber.verticalDistance());
                  }

                  BlockPos centre = mc.player.blockPosition();
                  boolean moved = lastCentre == null || centre.distManhattan(lastCentre) >= 4;
                  boolean resized = horizontal != lastHorizontal || vertical != lastVertical;
                  boolean stale = gameTime - lastFinishedTick >= 20L;
                  if (!moved && !resized && !stale) {
                     return;
                  }

                  lastHorizontal = horizontal;
                  lastVertical = vertical;
                  active = new RiptideHoleScanner.Scan(level, centre, horizontal, vertical, 2147483647L);
               }

               if (active.advance(16384)) {
                  publish(active.found);
                  lastCentre = active.centre;
                  lastFinishedTick = gameTime;
                  active = null;
               }
            }
         } else {
            if (active != null || lastCentre != null) {
               reset();
            }

            if (published != RiptideHoleScanner.Snapshot.EMPTY) {
               published = RiptideHoleScanner.Snapshot.EMPTY;
            }

            rememberLevel(level);
         }
      }
   }

   private static Level lastLevel() {
      WeakReference<Level> ref = lastLevel;
      return ref == null ? null : ref.get();
   }

   private static void rememberLevel(Level level) {
      if (lastLevel() != level) {
         lastLevel = level == null ? null : new WeakReference<>(level);
      }
   }

   private static void reset() {
      active = null;
      lastCentre = null;
      lastLevel = null;
      lastHorizontal = 0;
      lastVertical = 0;
      lastPumpTick = Long.MIN_VALUE;
      lastFinishedTick = Long.MIN_VALUE;
   }

   private static void publish(List<RiptideHoleScanner.Hole> found) {
      List<RiptideHoleScanner.Hole> frozen = List.copyOf(found);
      Map<Long, RiptideHoleScanner.Hole> byCell = new HashMap<>(Math.max(16, frozen.size() * 3));

      for (RiptideHoleScanner.Hole hole : frozen) {
         for (int dx = 0; dx < hole.sizeX; dx++) {
            for (int dz = 0; dz < hole.sizeZ; dz++) {
               byCell.putIfAbsent(BlockPos.asLong(hole.pos.getX() + dx, hole.pos.getY(), hole.pos.getZ() + dz), hole);
            }
         }
      }

      published = new RiptideHoleScanner.Snapshot(frozen, byCell);
   }

   private static long cellCount(int horizontal, int vertical) {
      long side = 2L * horizontal + 1L;
      return side * side * (2L * vertical + 1L);
   }

   public static final class Hole {
      private final RiptideHoleScanner.HoleType type;
      private final BlockPos pos;
      private final int sizeX;
      private final int sizeZ;
      private final AABB box;

      private Hole(RiptideHoleScanner.HoleType type, BlockPos pos, int sizeX, int sizeZ) {
         this.type = type;
         this.pos = pos;
         this.sizeX = sizeX;
         this.sizeZ = sizeZ;
         this.box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + sizeX, pos.getY() + 1, pos.getZ() + sizeZ);
      }

      public RiptideHoleScanner.HoleType type() {
         return this.type;
      }

      public BlockPos pos() {
         return this.pos;
      }

      public int sizeX() {
         return this.sizeX;
      }

      public int sizeZ() {
         return this.sizeZ;
      }

      public AABB box() {
         return this.box;
      }

      public boolean bedrockOnly() {
         return this.type == RiptideHoleScanner.HoleType.ONE_BY_ONE_BEDROCK;
      }

      public int cellCount() {
         return this.sizeX * this.sizeZ;
      }

      public boolean contains(int x, int y, int z) {
         return y == this.pos.getY() && x >= this.pos.getX() && x < this.pos.getX() + this.sizeX && z >= this.pos.getZ() && z < this.pos.getZ() + this.sizeZ;
      }

      public boolean contains(BlockPos other) {
         return other != null && this.contains(other.getX(), other.getY(), other.getZ());
      }

      public boolean isInvalidatedByFilling(BlockPos placed) {
         return placed == null
            ? false
            : placed.getX() >= this.pos.getX()
               && placed.getX() < this.pos.getX() + this.sizeX
               && placed.getZ() >= this.pos.getZ()
               && placed.getZ() < this.pos.getZ() + this.sizeZ
               && placed.getY() >= this.pos.getY()
               && placed.getY() <= this.pos.getY() + 2;
      }

      public List<BlockPos> cells() {
         List<BlockPos> list = new ArrayList<>(this.sizeX * this.sizeZ);

         for (int dx = 0; dx < this.sizeX; dx++) {
            for (int dz = 0; dz < this.sizeZ; dz++) {
               list.add(new BlockPos(this.pos.getX() + dx, this.pos.getY(), this.pos.getZ() + dz));
            }
         }

         return list;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof RiptideHoleScanner.Hole other)
               ? false
               : this.type == other.type && this.sizeX == other.sizeX && this.sizeZ == other.sizeZ && this.pos.equals(other.pos);
         }
      }

      @Override
      public int hashCode() {
         return this.pos.hashCode() * 31 + this.type.hashCode();
      }

      @Override
      public String toString() {
         return this.type + "@" + this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ();
      }
   }

   public static enum HoleType {
      ONE_BY_ONE_BEDROCK,
      ONE_BY_ONE,
      ONE_BY_TWO,
      TWO_BY_TWO;
   }

   private static final class Scan {
      private final WeakReference<Level> level;
      private final BlockPos centre;
      private final int minX;
      private final int minY;
      private final int minZ;
      private final int maxX;
      private final int maxY;
      private final int maxZ;
      private final int bufX;
      private final int bufZ;
      private final int spanX;
      private final int spanZ;
      private final int layerSize;
      private final byte[] states;
      private final BitSet claimed;
      private final MutableBlockPos cursor = new MutableBlockPos();
      private final List<RiptideHoleScanner.Hole> found = new ArrayList<>();
      private int x;
      private int y;
      private int z;
      private int loadedY = Integer.MIN_VALUE;
      private boolean finished;

      Scan(Level level, BlockPos centre, int horizontal, int vertical, long maxCells) {
         this.level = new WeakReference<>(level);
         this.centre = centre;
         int h = Mth.clamp(horizontal, 0, 128);
         int v = Mth.clamp(vertical, 0, 64);

         while (h > 0 && RiptideHoleScanner.cellCount(h, v) > maxCells) {
            h--;
         }

         while (v > 0 && RiptideHoleScanner.cellCount(h, v) > maxCells) {
            v--;
         }

         this.minX = centre.getX() - h;
         this.maxX = centre.getX() + h;
         this.minZ = centre.getZ() - h;
         this.maxZ = centre.getZ() + h;
         this.minY = Math.max(centre.getY() - v, level.getMinY() + 1);
         this.maxY = Math.min(centre.getY() + v, level.getMaxY() - 3);
         this.bufX = this.minX - 2;
         this.bufZ = this.minZ - 2;
         this.spanX = this.maxX - this.minX + 1 + 4;
         this.spanZ = this.maxZ - this.minZ + 1 + 4;
         this.layerSize = this.spanX * this.spanZ;
         this.states = new byte[this.layerSize * 4];
         this.claimed = new BitSet(this.layerSize);
         this.x = this.minX;
         this.y = this.minY;
         this.z = this.minZ;
      }

      boolean advance(int cellBudget) {
         if (this.finished) {
            return true;
         } else {
            int budget = Math.max(1, cellBudget);

            while (budget > 0) {
               if (this.y > this.maxY) {
                  this.finished = true;
                  return true;
               }

               if (this.loadedY != this.y) {
                  this.beginRow(this.y);
               }

               this.detect(this.x, this.y, this.z);
               budget--;
               if (++this.z > this.maxZ) {
                  this.z = this.minZ;
                  if (++this.x > this.maxX) {
                     this.x = this.minX;
                     this.y++;
                  }
               }
            }

            if (this.y > this.maxY) {
               this.finished = true;
            }

            return this.finished;
         }
      }

      private void beginRow(int row) {
         if (this.loadedY != Integer.MIN_VALUE && row - this.loadedY == 1) {
            int slot = row + 2 & 3;
            Arrays.fill(this.states, slot * this.layerSize, (slot + 1) * this.layerSize, (byte)0);
         } else {
            Arrays.fill(this.states, (byte)0);
         }

         this.claimed.clear();
         this.loadedY = row;
      }

      private void detect(int cellX, int cellY, int cellZ) {
         if (!this.isClaimed(cellX, cellZ)) {
            if (this.checkColumn(cellX, cellY, cellZ)) {
               int resistant = 0;
               int open1 = -1;
               int open2 = -1;

               for (int i = 0; i < RiptideHoleScanner.HORIZONTALS.length; i++) {
                  Direction direction = RiptideHoleScanner.HORIZONTALS[i];
                  if (isResistant(this.state(cellX + direction.getStepX(), cellY, cellZ + direction.getStepZ()))) {
                     resistant++;
                  } else if (open1 < 0) {
                     open1 = i;
                  } else if (open2 < 0) {
                     open2 = i;
                  }
               }

               switch (resistant) {
                  case 2:
                     this.addTwoByTwo(cellX, cellY, cellZ, RiptideHoleScanner.HORIZONTALS[open1], RiptideHoleScanner.HORIZONTALS[open2]);
                     break;
                  case 3:
                     this.addOneByTwo(cellX, cellY, cellZ, RiptideHoleScanner.HORIZONTALS[open1]);
                     break;
                  case 4:
                     this.addOneByOne(cellX, cellY, cellZ);
               }
            }
         }
      }

      private void addOneByOne(int cellX, int cellY, int cellZ) {
         boolean bedrock = this.state(cellX, cellY - 1, cellZ) == 4;

         for (int i = 0; bedrock && i < RiptideHoleScanner.HORIZONTALS.length; i++) {
            Direction direction = RiptideHoleScanner.HORIZONTALS[i];
            bedrock = this.state(cellX + direction.getStepX(), cellY, cellZ + direction.getStepZ()) == 4;
         }

         this.add(
            new RiptideHoleScanner.Hole(
               bedrock ? RiptideHoleScanner.HoleType.ONE_BY_ONE_BEDROCK : RiptideHoleScanner.HoleType.ONE_BY_ONE, new BlockPos(cellX, cellY, cellZ), 1, 1
            )
         );
      }

      private void addOneByTwo(int cellX, int cellY, int cellZ, Direction open) {
         int otherX = cellX + open.getStepX();
         int otherZ = cellZ + open.getStepZ();
         if (this.checkColumn(otherX, cellY, otherZ)) {
            Direction back = open.getOpposite();

            for (Direction direction : RiptideHoleScanner.HORIZONTALS) {
               if (direction != back && !isResistant(this.state(otherX + direction.getStepX(), cellY, otherZ + direction.getStepZ()))) {
                  return;
               }
            }

            boolean alongX = open.getAxis() == Axis.X;
            this.add(
               new RiptideHoleScanner.Hole(
                  RiptideHoleScanner.HoleType.ONE_BY_TWO, new BlockPos(Math.min(cellX, otherX), cellY, Math.min(cellZ, otherZ)), alongX ? 2 : 1, alongX ? 1 : 2
               )
            );
         }
      }

      private void addTwoByTwo(int cellX, int cellY, int cellZ, Direction d1, Direction d2) {
         int ax = cellX + d1.getStepX();
         int az = cellZ + d1.getStepZ();
         if (this.checkCell(ax, cellY, az, d1, d2.getOpposite())) {
            int bx = cellX + d2.getStepX();
            int bz = cellZ + d2.getStepZ();
            if (this.checkCell(bx, cellY, bz, d2, d1.getOpposite())) {
               int cx = bx + d1.getStepX();
               int cz = bz + d1.getStepZ();
               if (this.checkCell(cx, cellY, cz, d1, d2)) {
                  this.add(
                     new RiptideHoleScanner.Hole(RiptideHoleScanner.HoleType.TWO_BY_TWO, new BlockPos(Math.min(cellX, cx), cellY, Math.min(cellZ, cz)), 2, 2)
                  );
               }
            }
         }
      }

      private boolean checkCell(int cellX, int cellY, int cellZ, Direction a, Direction b) {
         return this.checkColumn(cellX, cellY, cellZ)
            && isResistant(this.state(cellX + a.getStepX(), cellY, cellZ + a.getStepZ()))
            && isResistant(this.state(cellX + b.getStepX(), cellY, cellZ + b.getStepZ()));
      }

      private boolean checkColumn(int cellX, int cellY, int cellZ) {
         return isResistant(this.state(cellX, cellY - 1, cellZ))
            && this.state(cellX, cellY, cellZ) == 1
            && this.state(cellX, cellY + 1, cellZ) == 1
            && this.state(cellX, cellY + 2, cellZ) == 1;
      }

      private void add(RiptideHoleScanner.Hole hole) {
         this.found.add(hole);

         for (int dx = 0; dx < hole.sizeX; dx++) {
            for (int dz = 0; dz < hole.sizeZ; dz++) {
               this.claim(hole.pos.getX() + dx, hole.pos.getZ() + dz);
            }
         }
      }

      private int planeIndex(int cellX, int cellZ) {
         int dx = cellX - this.bufX;
         int dz = cellZ - this.bufZ;
         return dx >= 0 && dz >= 0 && dx < this.spanX && dz < this.spanZ ? dx * this.spanZ + dz : -1;
      }

      private void claim(int cellX, int cellZ) {
         int index = this.planeIndex(cellX, cellZ);
         if (index >= 0) {
            this.claimed.set(index);
         }
      }

      private boolean isClaimed(int cellX, int cellZ) {
         int index = this.planeIndex(cellX, cellZ);
         return index >= 0 && this.claimed.get(index);
      }

      private byte state(int cellX, int cellY, int cellZ) {
         int index = this.planeIndex(cellX, cellZ);
         if (index >= 0 && cellY >= this.loadedY - 1 && cellY <= this.loadedY + 2) {
            index += (cellY & 3) * this.layerSize;
            byte cached = this.states[index];
            if (cached != 0) {
               return cached;
            } else {
               byte value = this.classify(cellX, cellY, cellZ);
               this.states[index] = value;
               return value;
            }
         } else {
            return this.classify(cellX, cellY, cellZ);
         }
      }

      private byte classify(int cellX, int cellY, int cellZ) {
         Level world = this.level.get();
         if (world == null) {
            return 2;
         } else {
            BlockState blockState = world.getBlockState(this.cursor.set(cellX, cellY, cellZ));
            if (blockState.isAir()) {
               return 1;
            } else {
               float resistance = blockState.getBlock().getExplosionResistance();
               if (resistance >= 3600000.0F) {
                  return 4;
               } else {
                  return (byte)(resistance >= 600.0F ? 3 : 2);
               }
            }
         }
      }

      private static boolean isResistant(byte value) {
         return value == 3 || value == 4;
      }
   }

   private record Snapshot(List<RiptideHoleScanner.Hole> holes, Map<Long, RiptideHoleScanner.Hole> byCell) {
      static final RiptideHoleScanner.Snapshot EMPTY = new RiptideHoleScanner.Snapshot(List.of(), Map.of());
   }

   public interface Subscriber {
      int horizontalDistance();

      int verticalDistance();
   }
}
