package riptide.util;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import java.util.function.Consumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

public final class RiptideEspMeshBuffer implements AutoCloseable {
   private static final int SCRATCH_BYTES = 1048576;
   private final String label;
   private GpuBuffer vertexBuffer;
   private PrimitiveTopology topology;
   private int indexCount;

   public RiptideEspMeshBuffer(String label) {
      this.label = label;
   }

   public boolean hasMesh() {
      return this.vertexBuffer != null && !this.vertexBuffer.isClosed() && this.indexCount > 0;
   }

   public boolean bake(RenderType type, Consumer<VertexConsumer> emit) {
      this.drop();
      ByteBufferBuilder scratch = new ByteBufferBuilder(1048576);

      boolean var14;
      label70: {
         boolean var8;
         label69: {
            try {
               BufferBuilder builder = new BufferBuilder(scratch, type.primitiveTopology(), type.format());
               emit.accept(builder);
               MeshData mesh = builder.build();
               if (mesh == null) {
                  var14 = false;
                  break label70;
               }

               MeshData var6 = mesh;

               label75: {
                  try {
                     DrawState state = mesh.drawState();
                     if (state.indexCount() > 0) {
                        this.topology = state.primitiveTopology();
                        this.indexCount = state.indexCount();
                        this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> this.label, 32, mesh.vertexBuffer());
                        break label75;
                     }

                     var8 = false;
                  } catch (Throwable var11) {
                     if (mesh != null) {
                        try {
                           var6.close();
                        } catch (Throwable var10) {
                           var11.addSuppressed(var10);
                        }
                     }

                     throw var11;
                  }

                  if (mesh != null) {
                     mesh.close();
                  }
                  break label69;
               }

               if (mesh != null) {
                  mesh.close();
               }

               var14 = this.hasMesh();
            } catch (Throwable var12) {
               try {
                  scratch.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }

               throw var12;
            }

            scratch.close();
            return var14;
         }

         scratch.close();
         return var8;
      }

      scratch.close();
      return var14;
   }

   public void draw(RenderType type, Matrix4fc framePose, double offsetX, double offsetY, double offsetZ) {
      if (this.hasMesh()) {
         AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(this.topology);
         GpuBuffer indices = sequential.getBuffer(this.indexCount);
         Matrix4fStack modelView = RenderSystem.getModelViewStack();
         modelView.pushMatrix();

         try {
            if (framePose != null) {
               modelView.mul(framePose);
            }

            modelView.translate((float)offsetX, (float)offsetY, (float)offsetZ);
            type.prepare().drawFromBuffer(this.vertexBuffer, indices, sequential.type(), 0, 0, this.indexCount);
         } finally {
            modelView.popMatrix();
         }
      }
   }

   public void drop() {
      GpuBuffer buffer = this.vertexBuffer;
      this.vertexBuffer = null;
      this.indexCount = 0;
      this.topology = null;
      if (buffer != null && !buffer.isClosed()) {
         if (RenderSystem.isOnRenderThread()) {
            buffer.close();
         } else {
            RenderSystem.queueFencedTask(buffer::close);
         }
      }
   }

   @Override
   public void close() {
      this.drop();
   }
}
