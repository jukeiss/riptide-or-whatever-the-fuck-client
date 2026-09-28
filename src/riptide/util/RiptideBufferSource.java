package riptide.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class RiptideBufferSource implements AutoCloseable {
   private final StagedVertexBuffer stagedBuffer;
   private final List<Draw> draws = new ArrayList<>();
   private final List<RenderType> drawTypes = new ArrayList<>();

   public RiptideBufferSource() {
      this(4194304);
   }

   public RiptideBufferSource(int bufferSize) {
      this.stagedBuffer = new StagedVertexBuffer(() -> "RiptideBufferSource", bufferSize);
   }

   public VertexConsumer getBuffer(RenderType renderType) {
      if (!this.drawTypes.isEmpty() && this.drawTypes.getLast() == renderType && renderType.canConsolidateConsecutiveGeometry()) {
         return this.stagedBuffer.getVertexBuilder(this.draws.getLast());
      } else {
         Draw draw = this.stagedBuffer
            .appendDraw(
               renderType.format(), renderType.primitiveTopology(), renderType.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null
            );
         this.draws.add(draw);
         this.drawTypes.add(renderType);
         return this.stagedBuffer.getVertexBuilder(draw);
      }
   }

   public void uploadAndDraw() {
      try {
         if (this.draws.isEmpty()) {
            return;
         }

         this.stagedBuffer.upload();

         for (int i = 0; i < this.draws.size(); i++) {
            this.draw(this.drawTypes.get(i), this.draws.get(i));
         }
      } finally {
         this.draws.clear();
         this.drawTypes.clear();
         this.stagedBuffer.endFrame();
      }
   }

   @Override
   public void close() {
      this.stagedBuffer.close();
   }

   private void draw(RenderType type, Draw draw) {
      ExecuteInfo info = this.stagedBuffer.getExecuteInfo(draw);
      if (info != null) {
         type.prepare().drawFromBuffer(info);
      }
   }

   public static final class Holder {
      private final int bufferSize;
      private RiptideBufferSource source;

      public Holder(int bufferSize) {
         this.bufferSize = bufferSize;
      }

      public RiptideBufferSource get() {
         RiptideBufferSource current = this.source;
         if (current == null) {
            current = new RiptideBufferSource(this.bufferSize);
            this.source = current;
         }

         return current;
      }
   }
}
