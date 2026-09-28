package riptide.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public abstract class PersistentNbtManager<T> {
   protected final List<T> items = new ArrayList<>();
   private boolean loaded;
   private long changeRevision;

   public final synchronized long changeRevision() {
      return this.changeRevision;
   }

   protected abstract File saveFile();

   protected abstract String listKey();

   protected abstract T fromTag(CompoundTag var1);

   protected abstract CompoundTag toTag(T var1);

   protected void readExtra(CompoundTag root) {
   }

   protected void writeExtra(CompoundTag root) {
   }

   protected abstract String describe();

   protected final synchronized void ensureLoaded() {
      if (!this.loaded) {
         this.loaded = true;
         this.changeRevision++;
         File file = this.saveFile();
         if (file.exists()) {
            try {
               CompoundTag tag = NbtIo.read(file.toPath());
               if (tag == null) {
                  return;
               }

               this.readExtra(tag);
               this.items.clear();

               for (Tag element : tag.getListOrEmpty(this.listKey())) {
                  if (element instanceof CompoundTag compoundTag) {
                     this.items.add(this.fromTag(compoundTag));
                  }
               }
            } catch (Exception var7) {
               riptide.RiptideClientAddon.LOG.error("Failed to load " + this.describe(), var7);
            }
         }
      }
   }

   public void save() {
      CompoundTag tag = new CompoundTag();
      List<T> snapshot;
      File target;
      synchronized (this) {
         this.changeRevision++;
         this.writeExtra(tag);
         snapshot = new ArrayList<>(this.items);
         target = this.saveFile();
      }

      ListTag list = new ListTag();

      for (T item : snapshot) {
         list.add(this.toTag(item));
      }

      tag.put(this.listKey(), list);
      String key = "nbt:" + target.getAbsolutePath();
      SaveCoordinator.enqueueLatest(key, () -> this.writeSnapshot(tag, target));
   }

   private void writeSnapshot(CompoundTag tag, File target) {
      File parent = target.getParentFile();
      if (parent == null) {
         parent = new File(".");
      }

      if (parent != null) {
         parent.mkdirs();
      }

      File tmp = new File(parent, target.getName() + ".tmp");
      File backup = new File(parent, target.getName() + ".bak");

      try {
         NbtIo.write(tag, tmp.toPath());
      } catch (Exception var11) {
         riptide.RiptideClientAddon.LOG.error("Failed to save " + this.describe(), var11);
         tmp.delete();
         return;
      }

      try {
         if (target.exists()) {
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var10) {
         riptide.RiptideClientAddon.LOG.warn("Failed to back up " + this.describe(), var10);
      }

      try {
         Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception var9) {
         try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception var8) {
            riptide.RiptideClientAddon.LOG.error("Failed to swap in " + this.describe(), var8);
         }
      }
   }

   public synchronized List<T> all() {
      return new ArrayList<>(this.items);
   }

   public synchronized int size() {
      return this.items.size();
   }

   public synchronized boolean contains(T item) {
      return this.items.contains(item);
   }
}
