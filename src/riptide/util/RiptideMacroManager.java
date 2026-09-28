package riptide.util;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfileManager;
import riptide.util.multi.MultiTakeoverState;

public class RiptideMacroManager {
   private static RiptideMacroManager INSTANCE;
   private List<RiptideMacro> macros = new ArrayList<>();
   private volatile File saveFile;
   private volatile long revision;
   private volatile boolean suppressLanBroadcast = false;

   private RiptideMacroManager() {
      this.saveFile = sharedLibraryFile();
      this.load();
   }

   public static File sharedLibraryFile() {
      return new File(riptide.RiptideClientAddon.FOLDER, "riptide_macros.nbt");
   }

   public static void writeEmptyLibrary(File file) {
      if (file != null) {
         try {
            CompoundTag tag = new CompoundTag();
            tag.put("macros", new ListTag());
            Files.createDirectories(file.toPath().getParent());
            NbtIo.write(tag, file.toPath());
         } catch (Exception var2) {
            riptide.RiptideClientAddon.LOG.warn("Could not write empty macro library {}", file, var2);
         }
      }
   }

   public static synchronized RiptideMacroManager get() {
      if (INSTANCE == null) {
         INSTANCE = new RiptideMacroManager();
      }

      return INSTANCE;
   }

   public synchronized String createUniqueName(String preferredName) {
      String baseName = preferredName != null && !preferredName.isBlank() ? preferredName.trim() : "New Macro";
      String candidate = baseName;
      int suffix = 1;

      while (this.get(candidate) != null) {
         candidate = baseName + " (" + suffix++ + ")";
      }

      return candidate;
   }

   public synchronized RiptideMacro addImportedCopy(RiptideMacro source, String preferredName) {
      if (source == null) {
         return null;
      } else {
         RiptideMacro copy = source.deepCopy();
         copy.sanitizeForSharing();
         copy.name = this.createUniqueName(preferredName != null && !preferredName.isBlank() ? preferredName : source.name);
         this.add(copy);
         return copy;
      }
   }

   public synchronized void add(RiptideMacro macro) {
      if (macro != null) {
         RiptideMacro existing = this.get(macro.name);
         if (existing != null && existing != macro) {
            this.removeByIdentity(existing);
         }

         this.macros.add(macro);
         this.save();
      }
   }

   private boolean removeByIdentity(RiptideMacro macro) {
      Iterator<RiptideMacro> it = this.macros.iterator();

      while (it.hasNext()) {
         if (it.next() == macro) {
            it.remove();
            return true;
         }
      }

      return false;
   }

   public synchronized RiptideMacro get(String name) {
      if (name == null) {
         return null;
      } else {
         for (RiptideMacro macro : this.macros) {
            if (macro != null && MacroNames.equal(macro.name, name)) {
               return macro;
            }
         }

         return null;
      }
   }

   public synchronized List<RiptideMacro> getAll() {
      return new ArrayList<>(this.macros);
   }

   public long getRevision() {
      return this.revision;
   }

   public synchronized void remove(RiptideMacro macro) {
      if (macro != null) {
         if (MacroExecutor.isMacroRunning(macro.name)) {
            MacroExecutor.stopMacro(macro.name);
            RiptideClientMessaging.sendPrefixed("§eStopped running macro before deletion: " + macro.name);
         }

         if (this.removeByIdentity(macro)) {
            String deletedName = macro.name == null ? "" : macro.name;
            this.save();
            RiptideClientMessaging.sendPrefixed("§aDeleted macro: " + macro.name);
            if (!deletedName.isBlank() && !RiptideLiteVariant.enabled()) {
               MultiProfileManager.get().replaceMacroReferences(deletedName, "");
               MultiManager liveMulti = MultiManager.getIfInitialized();
               if (liveMulti != null) {
                  liveMulti.replaceMacroReference(deletedName, "");
               }
            }

            RiptideMacroEditorOverlay editor = RiptideMacroEditorOverlay.getSharedOverlay();
            if (editor != null && editor.isEditingMacro(macro)) {
               editor.close();
            }

            if (!this.suppressLanBroadcast && RiptideLANSync.getInstance().isInSession()) {
               RiptideLANSync.getInstance().broadcastMacroDeletion(macro.name);
            }
         }
      }
   }

   public void delete(RiptideMacro macro) {
      this.remove(macro);
   }

   public void executeMacro(String name) {
      RiptideMacro macro = this.get(name);
      if (macro != null) {
         macro.execute();
         RiptideClientMessaging.sendPrefixed("§aExecuting macro: " + macro.name);
      } else {
         RiptideClientMessaging.sendPrefixed("§cMacro not found: " + name);
      }
   }

   public void stopMacro() {
      if (MultiTakeoverState.isActive()) {
         MultiManager multi = MultiManager.getIfInitialized();
         if (multi != null) {
            MultiManager.BroadcastResult result = multi.stopMacroOnInteractiveScope(Set.of());
            RiptideClientMessaging.sendPrefixed("§eStop POV macro: " + result.summary());
         }
      } else if (MacroExecutor.isVisibleRunning()) {
         MacroExecutor.stop();
      } else {
         RiptideClientMessaging.sendPrefixed("§eNo macro is currently running.");
      }
   }

   public void save() {
      CompoundTag tag;
      File target;
      boolean broadcast;
      synchronized (this) {
         this.revision++;
         target = this.saveFile;
         broadcast = !this.suppressLanBroadcast;
         tag = new CompoundTag();
         ListTag list = new ListTag();

         for (RiptideMacro macro : this.macros) {
            if (macro != null) {
               list.add(macro.toTag());
            }
         }

         tag.put("macros", list);
      }

      SaveCoordinator.enqueueLatest("macro-library:" + target.getAbsolutePath(), () -> writeTagAtomically(target, tag));
      if (broadcast && RiptideLANSync.getInstance().isInSession()) {
         RiptideLANSync.getInstance().broadcastMacroList();
      }
   }

   private static void writeTagAtomically(File targetFile, CompoundTag tag) {
      Path target = targetFile.toPath();
      Path temp = target.resolveSibling(targetFile.getName() + ".tmp");
      Path backup = target.resolveSibling(targetFile.getName() + ".bak");

      try {
         Files.createDirectories(target.getParent());
         NbtIo.write(tag, temp);
         if (Files.exists(target)) {
            try {
               Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception var17) {
               riptide.RiptideClientAddon.LOG.warn("Could not update macro backup {}; continuing with atomic save", backup, var17);
            }
         }

         try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException var16) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var18) {
         riptide.RiptideClientAddon.LOG.error("Failed to save Riptide macros", var18);
      } finally {
         try {
            Files.deleteIfExists(temp);
         } catch (Exception var15) {
         }
      }
   }

   public synchronized void switchBackingFile(File newFile, boolean seedIfMissing, boolean announce) {
      if (newFile != null) {
         File old = this.saveFile;
         if (old == null || !old.equals(newFile)) {
            boolean prevSuppress = this.suppressLanBroadcast;
            this.suppressLanBroadcast = true;

            try {
               this.save();
               this.saveFile = newFile;
               File backup = new File(newFile.getParentFile(), newFile.getName() + ".bak");
               if (newFile.exists() || backup.exists()) {
                  this.load();
               } else if (seedIfMissing) {
                  this.save();
               } else {
                  this.macros = new ArrayList<>();
                  this.revision++;
               }
            } finally {
               this.suppressLanBroadcast = prevSuppress;
            }

            if (announce && !this.suppressLanBroadcast && RiptideLANSync.getInstance().isInSession()) {
               RiptideLANSync.getInstance().broadcastMacroList();
            }
         }
      }
   }

   public synchronized void resetToSharedLibrary() {
      this.switchBackingFile(sharedLibraryFile(), false, true);
   }

   public synchronized File backingFile() {
      return this.saveFile;
   }

   public synchronized void load() {
      Path target = this.saveFile.toPath();
      Path backup = target.resolveSibling(this.saveFile.getName() + ".bak");
      if (Files.exists(target) || Files.exists(backup)) {
         try {
            if (!Files.exists(target)) {
               throw new IllegalStateException("Main macro file is missing");
            }

            this.macros = this.loadFile(target);
            this.revision++;
         } catch (Exception var6) {
            riptide.RiptideClientAddon.LOG.error("Failed to load Riptide macros; trying backup", var6);
            if (!Files.exists(backup)) {
               return;
            }

            try {
               this.macros = this.loadFile(backup);
               this.revision++;
               riptide.RiptideClientAddon.LOG.warn("Recovered Riptide macros from {}", backup);
            } catch (Exception var5) {
               riptide.RiptideClientAddon.LOG.error("Failed to load Riptide macro backup", var5);
            }
         }
      }
   }

   private List<RiptideMacro> loadFile(Path path) throws Exception {
      CompoundTag tag = NbtIo.read(path);
      if (tag == null) {
         throw new IllegalStateException("Macro file was empty");
      } else if (tag.get("macros") instanceof ListTag list) {
         ArrayList var12 = new ArrayList();
         HashMap seen = new HashMap();

         for (Tag element : list) {
            if (element instanceof CompoundTag macroTag) {
               try {
                  RiptideMacro macro = new RiptideMacro().fromTag(macroTag);
                  Integer previous = (Integer)seen.get(MacroNames.key(macro.name));
                  if (previous != null) {
                     riptide.RiptideClientAddon.LOG
                        .warn("Dropping macro '{}' shadowed by a name that differs only in case", ((RiptideMacro)var12.get(previous)).name);
                     var12.set(previous, macro);
                  } else {
                     seen.put(MacroNames.key(macro.name), var12.size());
                     var12.add(macro);
                  }
               } catch (Throwable var11) {
                  riptide.RiptideClientAddon.LOG.warn("Skipping one damaged macro entry from {}", path, var11);
               }
            }
         }

         return var12;
      } else {
         throw new IllegalStateException("Macro file has no macro list");
      }
   }
}
