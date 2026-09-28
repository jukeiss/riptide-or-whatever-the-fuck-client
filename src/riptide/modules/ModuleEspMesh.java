package riptide.modules;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

final class ModuleEspMesh {
   private static final double EPSILON = 1.0E-7;

   private ModuleEspMesh() {
   }

   static ModuleEspMesh.Geometry build(List<ModuleEspMesh.Box> input) {
      if (input != null && !input.isEmpty()) {
         Map<Integer, LongOpenHashSet> voxelsByColor = new HashMap<>();
         List<ModuleEspMesh.Box> partial = new ArrayList<>();

         for (ModuleEspMesh.Box target : input) {
            if (target != null && target.box() != null) {
               int[] voxel = fullVoxel(target.box());
               if (voxel == null) {
                  partial.add(target);
               } else {
                  voxelsByColor.computeIfAbsent(target.color(), ignored -> new LongOpenHashSet()).add(BlockPos.asLong(voxel[0], voxel[1], voxel[2]));
               }
            }
         }

         List<ModuleEspMesh.Quad> quads = new ArrayList<>();
         List<ModuleEspMesh.Edge> edges = new ArrayList<>();

         for (Entry<Integer, LongOpenHashSet> group : voxelsByColor.entrySet()) {
            buildVoxelGroup(group.getValue(), group.getKey(), quads, edges);
         }

         for (ModuleEspMesh.Box box : mergePartialBoxes(partial)) {
            addBox(box.box(), box.color(), quads, edges);
         }

         return new ModuleEspMesh.Geometry(List.copyOf(quads), List.copyOf(edges));
      } else {
         return ModuleEspMesh.Geometry.EMPTY;
      }
   }

   static boolean isFullVoxel(AABB box) {
      return fullVoxel(box) != null;
   }

   private static int[] fullVoxel(AABB box) {
      if (box == null) {
         return null;
      } else {
         int x = (int)Math.rint(box.minX);
         int y = (int)Math.rint(box.minY);
         int z = (int)Math.rint(box.minZ);
         return close(box.minX, x)
               && close(box.minY, y)
               && close(box.minZ, z)
               && close(box.maxX, x + 1.0)
               && close(box.maxY, y + 1.0)
               && close(box.maxZ, z + 1.0)
            ? new int[]{x, y, z}
            : null;
      }
   }

   private static void buildVoxelGroup(LongOpenHashSet occupied, int color, List<ModuleEspMesh.Quad> quads, List<ModuleEspMesh.Edge> outputEdges) {
      if (occupied != null && !occupied.isEmpty()) {
         LongOpenHashSet[] candidates = new LongOpenHashSet[]{
            new LongOpenHashSet(occupied.size() * 4), new LongOpenHashSet(occupied.size() * 4), new LongOpenHashSet(occupied.size() * 4)
         };
         LongIterator iterator = occupied.iterator();

         while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            if (!contains(occupied, x, y - 1, z)) {
               addFace(quads, 0, x, y, z, x + 1, y + 1, z + 1, color);
            }

            if (!contains(occupied, x, y + 1, z)) {
               addFace(quads, 1, x, y, z, x + 1, y + 1, z + 1, color);
            }

            if (!contains(occupied, x, y, z + 1)) {
               addFace(quads, 2, x, y, z, x + 1, y + 1, z + 1, color);
            }

            if (!contains(occupied, x, y, z - 1)) {
               addFace(quads, 3, x, y, z, x + 1, y + 1, z + 1, color);
            }

            if (!contains(occupied, x - 1, y, z)) {
               addFace(quads, 4, x, y, z, x + 1, y + 1, z + 1, color);
            }

            if (!contains(occupied, x + 1, y, z)) {
               addFace(quads, 5, x, y, z, x + 1, y + 1, z + 1, color);
            }

            addVoxelEdgeCandidates(candidates, x, y, z);
         }

         List<ModuleEspMesh.GridEdge> visible = new ArrayList<>();

         for (int axis = 0; axis < candidates.length; axis++) {
            LongIterator iteratorx = candidates[axis].iterator();

            while (iteratorx.hasNext()) {
               long packedx = iteratorx.nextLong();
               int xx = BlockPos.getX(packedx);
               int yx = BlockPos.getY(packedx);
               int zx = BlockPos.getZ(packedx);
               if (isBoundaryCrease(occupied, axis, xx, yx, zx)) {
                  visible.add(new ModuleEspMesh.GridEdge(axis, xx, yx, zx, color));
               }
            }
         }

         mergeGridEdges(visible, outputEdges);
      }
   }

   private static void addVoxelEdgeCandidates(LongOpenHashSet[] candidates, int x, int y, int z) {
      candidates[0].add(BlockPos.asLong(x, y, z));
      candidates[0].add(BlockPos.asLong(x, y + 1, z));
      candidates[0].add(BlockPos.asLong(x, y, z + 1));
      candidates[0].add(BlockPos.asLong(x, y + 1, z + 1));
      candidates[1].add(BlockPos.asLong(x, y, z));
      candidates[1].add(BlockPos.asLong(x + 1, y, z));
      candidates[1].add(BlockPos.asLong(x, y, z + 1));
      candidates[1].add(BlockPos.asLong(x + 1, y, z + 1));
      candidates[2].add(BlockPos.asLong(x, y, z));
      candidates[2].add(BlockPos.asLong(x + 1, y, z));
      candidates[2].add(BlockPos.asLong(x, y + 1, z));
      candidates[2].add(BlockPos.asLong(x + 1, y + 1, z));
   }

   private static boolean isBoundaryCrease(LongOpenHashSet occupied, int axis, int x, int y, int z) {
      boolean a;
      boolean b;
      boolean c;
      boolean d;
      if (axis == 0) {
         a = contains(occupied, x, y - 1, z - 1);
         b = contains(occupied, x, y, z - 1);
         c = contains(occupied, x, y - 1, z);
         d = contains(occupied, x, y, z);
      } else if (axis == 1) {
         a = contains(occupied, x - 1, y, z - 1);
         b = contains(occupied, x, y, z - 1);
         c = contains(occupied, x - 1, y, z);
         d = contains(occupied, x, y, z);
      } else {
         a = contains(occupied, x - 1, y - 1, z);
         b = contains(occupied, x, y - 1, z);
         c = contains(occupied, x - 1, y, z);
         d = contains(occupied, x, y, z);
      }

      int count = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);
      return count == 1 || count == 3 || count == 2 && (a && d || b && c);
   }

   private static void mergeGridEdges(List<ModuleEspMesh.GridEdge> input, List<ModuleEspMesh.Edge> output) {
      input.sort((left, right) -> {
         int compared = Integer.compare(left.color(), right.color());
         if (compared != 0) {
            return compared;
         } else {
            compared = Integer.compare(left.axis(), right.axis());
            if (compared != 0) {
               return compared;
            } else {
               compared = Integer.compare(fixedA(left), fixedA(right));
               if (compared != 0) {
                  return compared;
               } else {
                  compared = Integer.compare(fixedB(left), fixedB(right));
                  return compared != 0 ? compared : Integer.compare(axisStart(left), axisStart(right));
               }
            }
         }
      });
      int index = 0;

      while (index < input.size()) {
         ModuleEspMesh.GridEdge first = input.get(index++);

         int end;
         for (end = axisStart(first) + 1; index < input.size(); index++) {
            ModuleEspMesh.GridEdge next = input.get(index);
            if (next.color() != first.color()
               || next.axis() != first.axis()
               || fixedA(next) != fixedA(first)
               || fixedB(next) != fixedB(first)
               || axisStart(next) != end) {
               break;
            }

            end++;
         }

         addGridEdge(output, first, end);
      }
   }

   private static int axisStart(ModuleEspMesh.GridEdge edge) {
      return edge.axis() == 0 ? edge.x() : (edge.axis() == 1 ? edge.y() : edge.z());
   }

   private static int fixedA(ModuleEspMesh.GridEdge edge) {
      return edge.axis() == 0 ? edge.y() : edge.x();
   }

   private static int fixedB(ModuleEspMesh.GridEdge edge) {
      return edge.axis() == 2 ? edge.y() : edge.z();
   }

   private static void addGridEdge(List<ModuleEspMesh.Edge> output, ModuleEspMesh.GridEdge edge, int end) {
      if (edge.axis() == 0) {
         output.add(new ModuleEspMesh.Edge(edge.x(), edge.y(), edge.z(), end, edge.y(), edge.z(), edge.color()));
      } else if (edge.axis() == 1) {
         output.add(new ModuleEspMesh.Edge(edge.x(), edge.y(), edge.z(), edge.x(), end, edge.z(), edge.color()));
      } else {
         output.add(new ModuleEspMesh.Edge(edge.x(), edge.y(), edge.z(), edge.x(), edge.y(), end, edge.color()));
      }
   }

   private static boolean contains(LongOpenHashSet occupied, int x, int y, int z) {
      return occupied.contains(BlockPos.asLong(x, y, z));
   }

   private static List<ModuleEspMesh.Box> mergePartialBoxes(List<ModuleEspMesh.Box> boxes) {
      if (boxes.size() < 2) {
         return boxes;
      } else {
         List<ModuleEspMesh.Box> merged = new ArrayList<>(boxes);
         int passes = 0;

         boolean changed;
         do {
            int before = merged.size();
            List<ModuleEspMesh.Box> var5 = mergeAxis(merged, 0);
            List<ModuleEspMesh.Box> var6 = mergeAxis(var5, 1);
            merged = mergeAxis(var6, 2);
            changed = merged.size() < before;
         } while (changed && ++passes < 12);

         return merged;
      }
   }

   private static List<ModuleEspMesh.Box> mergeAxis(List<ModuleEspMesh.Box> boxes, int axis) {
      if (boxes.size() < 2) {
         return boxes;
      } else {
         List<ModuleEspMesh.Box> sorted = new ArrayList<>(boxes);
         sorted.sort(partialComparator(axis));
         List<ModuleEspMesh.Box> output = new ArrayList<>(sorted.size());
         ModuleEspMesh.Box current = sorted.getFirst();

         for (int i = 1; i < sorted.size(); i++) {
            ModuleEspMesh.Box next = sorted.get(i);
            if (sameStrip(current, next, axis) && axisMin(next.box(), axis) <= axisMax(current.box(), axis) + 1.0E-7) {
               current = new ModuleEspMesh.Box(unionAlong(current.box(), next.box(), axis), current.color());
            } else {
               output.add(current);
               current = next;
            }
         }

         output.add(current);
         return output;
      }
   }

   private static Comparator<ModuleEspMesh.Box> partialComparator(int axis) {
      return (left, right) -> {
         int compared = Integer.compare(left.color(), right.color());
         if (compared != 0) {
            return compared;
         } else {
            AABB a = left.box();
            AABB b = right.box();

            for (int candidate = 0; candidate < 3; candidate++) {
               if (candidate != axis) {
                  compared = Double.compare(axisMin(a, candidate), axisMin(b, candidate));
                  if (compared != 0) {
                     return compared;
                  }

                  compared = Double.compare(axisMax(a, candidate), axisMax(b, candidate));
                  if (compared != 0) {
                     return compared;
                  }
               }
            }

            compared = Double.compare(axisMin(a, axis), axisMin(b, axis));
            return compared != 0 ? compared : Double.compare(axisMax(a, axis), axisMax(b, axis));
         }
      };
   }

   private static boolean sameStrip(ModuleEspMesh.Box left, ModuleEspMesh.Box right, int axis) {
      if (left.color() != right.color()) {
         return false;
      } else {
         for (int candidate = 0; candidate < 3; candidate++) {
            if (candidate != axis
               && (
                  !close(axisMin(left.box(), candidate), axisMin(right.box(), candidate))
                     || !close(axisMax(left.box(), candidate), axisMax(right.box(), candidate))
               )) {
               return false;
            }
         }

         return true;
      }
   }

   private static AABB unionAlong(AABB left, AABB right, int axis) {
      return switch (axis) {
         case 0 -> new AABB(Math.min(left.minX, right.minX), left.minY, left.minZ, Math.max(left.maxX, right.maxX), left.maxY, left.maxZ);
         case 1 -> new AABB(left.minX, Math.min(left.minY, right.minY), left.minZ, left.maxX, Math.max(left.maxY, right.maxY), left.maxZ);
         default -> new AABB(left.minX, left.minY, Math.min(left.minZ, right.minZ), left.maxX, left.maxY, Math.max(left.maxZ, right.maxZ));
      };
   }

   private static double axisMin(AABB box, int axis) {
      return axis == 0 ? box.minX : (axis == 1 ? box.minY : box.minZ);
   }

   private static double axisMax(AABB box, int axis) {
      return axis == 0 ? box.maxX : (axis == 1 ? box.maxY : box.maxZ);
   }

   private static boolean close(double left, double right) {
      return Math.abs(left - right) <= 1.0E-7;
   }

   private static void addBox(AABB box, int color, List<ModuleEspMesh.Quad> quads, List<ModuleEspMesh.Edge> edges) {
      for (int face = 0; face < 6; face++) {
         addFace(quads, face, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
      }

      double x1 = box.minX;
      double y1 = box.minY;
      double z1 = box.minZ;
      double x2 = box.maxX;
      double y2 = box.maxY;
      double z2 = box.maxZ;
      edges.add(new ModuleEspMesh.Edge(x1, y1, z1, x2, y1, z1, color));
      edges.add(new ModuleEspMesh.Edge(x2, y1, z1, x2, y1, z2, color));
      edges.add(new ModuleEspMesh.Edge(x2, y1, z2, x1, y1, z2, color));
      edges.add(new ModuleEspMesh.Edge(x1, y1, z2, x1, y1, z1, color));
      edges.add(new ModuleEspMesh.Edge(x1, y2, z1, x2, y2, z1, color));
      edges.add(new ModuleEspMesh.Edge(x2, y2, z1, x2, y2, z2, color));
      edges.add(new ModuleEspMesh.Edge(x2, y2, z2, x1, y2, z2, color));
      edges.add(new ModuleEspMesh.Edge(x1, y2, z2, x1, y2, z1, color));
      edges.add(new ModuleEspMesh.Edge(x1, y1, z1, x1, y2, z1, color));
      edges.add(new ModuleEspMesh.Edge(x2, y1, z1, x2, y2, z1, color));
      edges.add(new ModuleEspMesh.Edge(x2, y1, z2, x2, y2, z2, color));
      edges.add(new ModuleEspMesh.Edge(x1, y1, z2, x1, y2, z2, color));
   }

   private static void addFace(List<ModuleEspMesh.Quad> quads, int face, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
      switch (face) {
         case 0:
            quads.add(new ModuleEspMesh.Quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color));
            break;
         case 1:
            quads.add(new ModuleEspMesh.Quad(x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, color));
            break;
         case 2:
            quads.add(new ModuleEspMesh.Quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, color));
            break;
         case 3:
            quads.add(new ModuleEspMesh.Quad(x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, color));
            break;
         case 4:
            quads.add(new ModuleEspMesh.Quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, color));
            break;
         default:
            quads.add(new ModuleEspMesh.Quad(x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, color));
      }
   }

   record Box(AABB box, int color) {
   }

   record Edge(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
   }

   record Geometry(List<ModuleEspMesh.Quad> quads, List<ModuleEspMesh.Edge> edges) {
      private static final ModuleEspMesh.Geometry EMPTY = new ModuleEspMesh.Geometry(List.of(), List.of());
   }

   private record GridEdge(int axis, int x, int y, int z, int color) {
   }

   record Quad(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int color) {
   }
}
