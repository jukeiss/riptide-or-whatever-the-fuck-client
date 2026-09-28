package riptide.util.macro;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import riptide.modules.PackHideState;
import riptide.util.RiptideBookFileReader;
import riptide.util.RiptideBookPayloadBuilder;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideInventoryHelper;

public class NbtBookAction implements MacroAction {
   public static final String SOURCE_RANDOM = "Random";
   public static final String SOURCE_PASTED = "Pasted";
   public static final String SOURCE_FILE = "File";
   public int pages = 100;
   public int characters = 1024;
   public String title = "EldenRingLore";
   public boolean sign = true;
   public boolean appendCount = true;
   public boolean onlyAscii = false;
   public String randomType = "Utf8";
   public String dataSource = "Random";
   public String customComponent = "";
   public String customFilePath = "";
   public boolean wordWrap = true;
   public int delayTicks = 20;
   public int bookCount = 1;
   public boolean requireHeldWritableBook = false;
   public boolean dropInventoryBefore = false;
   public boolean disconnectAfter = false;
   private boolean enabled = true;
   public transient String pasteInfo = "";
   private static final int MAX_PAGES = 100;
   private static final int LINES_PER_PAGE = 14;
   private static final float LINE_WIDTH_PX = 114.0F;

   public boolean executeSingleBook(Minecraft mc, int bookIndex, int totalBooks) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc.player == null || mc.getConnection() == null) {
         RiptideClientMessaging.sendPrefixed("§cNo player or network connection!");
         return false;
      } else if (bookIndex == 0 && !this.prepareBeforeSigning(mc)) {
         return false;
      } else {
         int bookSlot = this.findWritableBook(mc);
         if (bookSlot < 0) {
            if (bookIndex == 0) {
               RiptideClientMessaging.sendPrefixed("§cNo Book & Quill found in inventory!");
            } else {
               RiptideClientMessaging.sendPrefixed("§eRan out of books after " + bookIndex + " signed.");
            }

            return false;
         } else {
            int hotbarSlot = bookSlot;
            if (bookSlot > 8) {
               int targetHotbar = 0;

               for (int i = 0; i <= 8; i++) {
                  if (mc.player.getInventory().getItem(i).isEmpty()) {
                     targetHotbar = i;
                     break;
                  }
               }

               RiptideInventoryHelper.swapInventorySlots(mc, bookSlot, targetHotbar);
               hotbarSlot = targetHotbar;
            }

            RiptideInventoryHelper.selectHotbarSlot(mc, hotbarSlot);
            int numPages = Math.min(100, Math.max(1, this.pages));
            List<String> pageList = this.buildPageList(mc, numPages);
            if (pageList.isEmpty()) {
               return false;
            } else {
               String bookTitle = this.title;
               if (this.appendCount && totalBooks > 1 && bookIndex > 0) {
                  bookTitle = this.title + " #" + (bookIndex + 1);
               }

               mc.getConnection().send(new ServerboundEditBookPacket(hotbarSlot, pageList, this.sign ? Optional.of(bookTitle) : Optional.empty()));
               int estimatedBytes = RiptideBookPayloadBuilder.estimateUtf8Bytes(pageList);
               RiptideClientMessaging.sendPrefixed(
                  "§aWrote book " + (bookIndex + 1) + "/" + totalBooks + " (~" + estimatedBytes / 1024 + "KB, " + pageList.size() + " pages)"
               );
               return true;
            }
         }
      }
   }

   private List<String> buildPageList(Minecraft mc, int numPages) {
      String source = this.normalizedSource();
      if ("Pasted".equals(source)) {
         return this.customComponent != null && !this.customComponent.isBlank()
            ? this.textPages(mc.font, this.customComponent, numPages)
            : this.generatedPages(numPages);
      } else if ("File".equals(source)) {
         String text = this.readCustomFile();
         if (text != null && !text.isBlank()) {
            return this.textPages(mc.font, text, numPages);
         } else {
            RiptideClientMessaging.sendPrefixed("§cNBT Book file is missing or empty.");
            return List.of();
         }
      } else {
         return this.generatedPages(numPages);
      }
   }

   private List<String> textPages(Font font, String text, int numPages) {
      return this.wordWrap ? splitTextIntoPages(font, text, numPages) : chunkTextIntoPages(text, numPages, this.characters);
   }

   private List<String> generatedPages(int numPages) {
      return RiptideBookPayloadBuilder.randomPages(numPages, this.characters, this.effectiveRandomType(), new Random());
   }

   public static File bookSourcesDir() {
      File dir = new File(riptide.RiptideClientAddon.FOLDER, "book-sources");
      dir.mkdirs();
      return dir;
   }

   private String readCustomFile() {
      String path = this.customFilePath == null ? "" : this.customFilePath.trim();
      if (path.isBlank()) {
         return "";
      } else {
         try {
            Path base = bookSourcesDir().getCanonicalFile().toPath();
            Path resolved = base.resolve(path).toFile().getCanonicalFile().toPath();
            if (!resolved.startsWith(base)) {
               RiptideClientMessaging.sendPrefixed("§cBook file must be inside book-sources/.");
               return "";
            } else {
               return RiptideBookFileReader.read(resolved);
            }
         } catch (Exception var4) {
            return "";
         }
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (this.executeSingleBook(mc, 0, 1)) {
            this.afterSigning(mc);
         }
      }
   }

   private boolean prepareBeforeSigning(Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else {
         if (this.requireHeldWritableBook) {
            ItemStack held = mc.player.getMainHandItem();
            Identifier writableBookId = Identifier.fromNamespaceAndPath("minecraft", "writable_book");
            if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).equals(writableBookId)) {
               RiptideClientMessaging.sendPrefixed("§cMust hold a Book & Quill!");
               return false;
            }
         }

         if (this.dropInventoryBefore) {
            int keepSlot = mc.player.getInventory().getSelectedSlot();

            for (int i = 0; i < 36; i++) {
               if (i != keepSlot && !mc.player.getInventory().getItem(i).isEmpty()) {
                  RiptideDropHelper.dropFromInventorySlot(mc, i, 0);
               }
            }
         }

         return true;
      }
   }

   public void afterSigning(Minecraft mc) {
      if (this.disconnectAfter) {
         if (mc.level != null && mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("Disconnected by Macro"));
         }
      }
   }

   private int findWritableBook(Minecraft mc) {
      if (mc.player == null) {
         return -1;
      } else {
         Identifier writableBookId = Identifier.fromNamespaceAndPath("minecraft", "writable_book");

         for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(writableBookId)) {
               return i;
            }
         }

         return -1;
      }
   }

   public static List<String> splitTextIntoPages(Font tr, String text, int maxPages) {
      if (text != null && !text.isEmpty()) {
         String normalized = RiptideBookFileReader.normalize(text);
         String[] paragraphs = normalized.split("\n", -1);
         List<String> visualLines = new ArrayList<>();

         for (String para : paragraphs) {
            if (para.isEmpty()) {
               visualLines.add("");
            } else {
               wrapParagraph(tr, para, visualLines);
            }
         }

         List<String> pages = new ArrayList<>();
         StringBuilder page = new StringBuilder();
         int linesOnPage = 0;
         List<String> boundedLines = new ArrayList<>(visualLines.size());

         for (String line : visualLines) {
            if (line.isEmpty()) {
               boundedLines.add("");
            } else {
               int offset = 0;

               while (offset < line.length()) {
                  int end = safePageEnd(line, offset, 1024);
                  boundedLines.add(line.substring(offset, end));
                  offset = end;
               }
            }
         }

         for (String linex : boundedLines) {
            int addedLength = linex.length() + (linesOnPage > 0 ? 1 : 0);
            if (linesOnPage >= 14 || page.length() + addedLength > 1024) {
               pages.add(page.toString());
               if (pages.size() >= maxPages) {
                  return pages;
               }

               page = new StringBuilder();
               linesOnPage = 0;
            }

            if (linesOnPage > 0) {
               page.append('\n');
            }

            page.append(linex);
            linesOnPage++;
         }

         if (page.length() > 0) {
            pages.add(page.toString());
         }

         if (pages.isEmpty()) {
            pages.add("");
         }

         return pages;
      } else {
         return new ArrayList<>(List.of(""));
      }
   }

   private static void wrapParagraph(Font tr, String para, List<String> out) {
      int idx = 0;

      while (idx < para.length()) {
         float width = 0.0F;
         int lineEnd = idx;
         int lastSpace = -1;

         while (lineEnd < para.length()) {
            int codePoint = para.codePointAt(lineEnd);
            int next = lineEnd + Character.charCount(codePoint);
            float cw = tr.width(para.substring(lineEnd, next));
            if (width + cw > 114.0F && lineEnd > idx) {
               break;
            }

            if (Character.isWhitespace(codePoint)) {
               lastSpace = lineEnd;
            }

            width += cw;
            lineEnd = next;
         }

         if (lineEnd >= para.length()) {
            out.add(para.substring(idx));
            return;
         }

         if (lastSpace > idx) {
            out.add(para.substring(idx, lastSpace));
            idx = lastSpace + 1;
         } else {
            out.add(para.substring(idx, lineEnd));
            idx = lineEnd;
         }
      }
   }

   public static int calculatePagesNeeded(Font tr, String text) {
      return text != null && !text.isEmpty() ? splitTextIntoPages(tr, text, 100).size() : 0;
   }

   public static List<String> chunkTextIntoPages(String text, int maxPages, int charsPerPage) {
      if (text != null && !text.isEmpty()) {
         int max = Math.max(1, Math.min(1024, charsPerPage));
         List<String> pages = new ArrayList<>();
         int index = 0;

         while (index < text.length() && pages.size() < maxPages) {
            int end = safePageEnd(text, index, max);
            pages.add(text.substring(index, end));
            index = end;
         }

         if (pages.isEmpty()) {
            pages.add("");
         }

         return pages;
      } else {
         return new ArrayList<>(List.of(""));
      }
   }

   private static int safePageEnd(String text, int start, int maxChars) {
      int end = Math.min(text.length(), start + Math.max(1, maxChars));
      if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) {
         end--;
      }

      if (end == start && start < text.length()) {
         end = start + Character.charCount(text.codePointAt(start));
      }

      return end;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.NBT_BOOK;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("pages", this.pages);
      tag.putInt("characters", this.characters);
      tag.putString("title", this.title);
      tag.putBoolean("sign", this.sign);
      tag.putBoolean("appendCount", this.appendCount);
      tag.putBoolean("onlyAscii", this.onlyAscii);
      tag.putString("randomType", this.effectiveRandomType());
      tag.putString("dataSource", this.normalizedSource());
      tag.putString("customText", this.customComponent);
      tag.putString("customFilePath", this.customFilePath);
      tag.putBoolean("wordWrap", this.wordWrap);
      tag.putInt("delayTicks", this.delayTicks);
      tag.putInt("bookCount", this.bookCount);
      tag.putBoolean("requireHeldWritableBook", this.requireHeldWritableBook);
      tag.putBoolean("dropInventoryBefore", this.dropInventoryBefore);
      tag.putBoolean("disconnectAfter", this.disconnectAfter);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("pages")) {
         this.pages = tag.getIntOr("pages", 100);
      } else if (tag.contains("targetSizeKB")) {
         this.pages = Math.min(100, tag.getIntOr("targetSizeKB", 100));
      }

      this.characters = tag.getIntOr("characters", 1024);
      if (tag.contains("title")) {
         this.title = tag.getStringOr("title", "EldenRingLore");
      }

      this.sign = tag.getBooleanOr("sign", true);
      this.appendCount = tag.getBooleanOr("appendCount", true);
      if (tag.contains("onlyAscii")) {
         this.onlyAscii = tag.getBooleanOr("onlyAscii", false);
      }

      if (tag.contains("randomType")) {
         this.randomType = RiptideBookPayloadBuilder.normalizeRandomType(tag.getStringOr("randomType", "Utf8"));
      } else if (this.onlyAscii) {
         this.randomType = "Ascii";
      }

      if (tag.contains("customText")) {
         this.customComponent = tag.getStringOr("customText", "");
      }

      if (tag.contains("customFilePath")) {
         this.customFilePath = tag.getStringOr("customFilePath", "");
      }

      this.wordWrap = tag.getBooleanOr("wordWrap", true);
      if (tag.contains("dataSource")) {
         this.dataSource = normalizeSource(tag.getStringOr("dataSource", "Random"));
      } else {
         this.dataSource = legacySourceForCustomText(this.customComponent);
      }

      if (tag.contains("delayTicks")) {
         this.delayTicks = tag.getIntOr("delayTicks", 20);
      }

      if (tag.contains("bookCount")) {
         this.bookCount = tag.getIntOr("bookCount", 1);
      }

      this.requireHeldWritableBook = tag.getBooleanOr("requireHeldWritableBook", false);
      this.dropInventoryBefore = tag.getBooleanOr("dropInventoryBefore", false);
      this.disconnectAfter = tag.getBooleanOr("disconnectAfter", false);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String mode = "Random".equals(this.normalizedSource()) ? this.effectiveRandomType() : this.normalizedSource();
      String count = this.bookCount > 1 ? ", x" + this.bookCount : "";
      String extras = "";
      if (this.dropInventoryBefore) {
         extras = extras + ", drop inv";
      }

      if (this.disconnectAfter) {
         extras = extras + ", disconnect";
      }

      return "NBT Book (" + this.pages + "pg, " + mode + count + extras + ")";
   }

   private String normalizedSource() {
      this.dataSource = normalizeSource(this.dataSource);
      return this.dataSource;
   }

   private String effectiveRandomType() {
      this.randomType = RiptideBookPayloadBuilder.normalizeRandomType(this.randomType);
      this.onlyAscii = "Ascii".equals(this.randomType);
      return this.randomType;
   }

   private static String normalizeSource(String source) {
      if (source == null) {
         return "Random";
      } else {
         String var1 = source.trim().toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "generated", "random" -> "Random";
            case "pasted", "paste", "custom" -> "Pasted";
            case "file", "path" -> "File";
            default -> "Random";
         };
      }
   }

   private static String legacySourceForCustomText(String text) {
      return text != null && !text.isBlank() && !"Riptide - EldenRingLore".equals(text) ? "Pasted" : "Random";
   }

   @Override
   public void sanitizeForSharing() {
      this.customFilePath = "";
      if ("File".equals(normalizeSource(this.dataSource))) {
         this.dataSource = "Random";
      }
   }

   @Override
   public String getIcon() {
      return "B";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }
}
