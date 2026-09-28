package riptide.util.mm;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.Setting;
import riptide.mixin.accessor.AbstractContainerScreenAccessor;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideSharedState;
import riptide.util.mm.msg.MmMessages;

public final class MmBlobs {
   private static volatile Map<String, Class<? extends Packet<?>>> knownPacketsByName;
   private static final String CLIPBOARD_TYPE = "riptide_mm_blob";

   private MmBlobs() {
   }

   private static Provider reg() {
      return RiptideClipboardHelper.registries();
   }

   public static MmMessages.BlobOffer captureGui() {
      Screen screen = Minecraft.getInstance().gui.screen();
      if (!(screen instanceof AbstractContainerScreen)) {
         return null;
      } else {
         AbstractContainerMenu menu = ((AbstractContainerScreenAccessor)screen).riptide$getMenu();
         if (menu == null) {
            return null;
         } else {
            try {
               Provider provider = reg();
               CompoundTag root = new CompoundTag();
               JsonElement titleJson = (JsonElement)ComponentSerialization.CODEC
                  .encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), screen.getTitle())
                  .result()
                  .orElse(null);
               root.putString("title", titleJson != null ? titleJson.toString() : "");
               ListTag slots = new ListTag();

               for (Slot slot : menu.slots) {
                  if (slot.x >= 0 && slot.y >= 0) {
                     CompoundTag s = new CompoundTag();
                     s.putInt("x", slot.x);
                     s.putInt("y", slot.y);
                     ItemStack st = slot.getItem();
                     if (st != null && !st.isEmpty()) {
                        Tag enc = (Tag)ItemStack.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), st).result().orElse(null);
                        if (enc instanceof CompoundTag ct) {
                           s.put("item", ct);
                        }
                     }

                     slots.add(s);
                  }
               }

               root.put("slots", slots);
               String data = compress(root);
               if (data == null) {
                  return null;
               } else {
                  String name = screen.getTitle().getString();
                  if (name == null || name.isBlank()) {
                     name = "GUI";
                  }

                  return new MmMessages.BlobOffer("gui", name, slots.size(), data);
               }
            } catch (Throwable var12) {
               return null;
            }
         }
      }
   }

   public static MmBlobs.GuiSnapshot decodeGui(MmMessages.BlobOffer b) {
      if (b != null && "gui".equals(b.kind)) {
         try {
            CompoundTag root = decompress(b.data);
            if (root == null) {
               return null;
            } else {
               Provider provider = reg();

               Component title;
               try {
                  String titleJson = root.getStringOr("title", "");
                  JsonElement el = JsonParser.parseString(titleJson.isBlank() ? "\"\"" : titleJson);
                  title = (Component)ComponentSerialization.CODEC
                     .parse(provider.createSerializationContext(JsonOps.INSTANCE), el)
                     .result()
                     .orElse(Component.literal(b.friendlyName));
               } catch (Throwable var18) {
                  title = Component.literal(b.friendlyName);
               }

               ListTag slots = root.getList("slots").orElse(new ListTag());
               List<int[]> coords = new ArrayList<>();
               List<ItemStack> stacks = new ArrayList<>();
               int minX = Integer.MAX_VALUE;
               int minY = Integer.MAX_VALUE;
               int maxX = Integer.MIN_VALUE;
               int maxY = Integer.MIN_VALUE;

               for (int i = 0; i < slots.size(); i++) {
                  if (slots.get(i) instanceof CompoundTag ct) {
                     int sx = ct.getIntOr("x", i % 9 * 18);
                     int sy = ct.getIntOr("y", i / 9 * 18);
                     if (sx >= 0 && sy >= 0) {
                        Tag item = ct.get("item");
                        if (item == null && (ct.contains("id") || ct.contains("count"))) {
                           item = ct;
                        }

                        ItemStack st = ItemStack.EMPTY;
                        if (item instanceof CompoundTag ic && !ic.isEmpty()) {
                           st = ItemStack.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), ic).result().orElse(ItemStack.EMPTY);
                        }

                        coords.add(new int[]{sx, sy});
                        stacks.add(st);
                        minX = Math.min(minX, sx);
                        minY = Math.min(minY, sy);
                        maxX = Math.max(maxX, sx);
                        maxY = Math.max(maxY, sy);
                     }
                  }
               }

               if (coords.isEmpty()) {
                  minX = 0;
                  minY = 0;
                  maxX = 0;
                  maxY = 0;
               }

               List<MmBlobs.SlotView> views = new ArrayList<>(coords.size());

               for (int ix = 0; ix < coords.size(); ix++) {
                  views.add(new MmBlobs.SlotView(coords.get(ix)[0] - minX, coords.get(ix)[1] - minY, stacks.get(ix)));
               }

               return new MmBlobs.GuiSnapshot(title, views, maxX - minX + 16, maxY - minY + 16);
            }
         } catch (Throwable var19) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static MmMessages.BlobOffer captureHeldItem() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return null;
      } else {
         ItemStack st = player.getMainHandItem();
         if (st != null && !st.isEmpty()) {
            try {
               Tag enc = (Tag)ItemStack.CODEC.encodeStart(reg().createSerializationContext(NbtOps.INSTANCE), st).result().orElse(null);
               if (enc instanceof CompoundTag ct) {
                  CompoundTag root = new CompoundTag();
                  root.put("item", ct);
                  String data = compress(root);
                  return data == null ? null : new MmMessages.BlobOffer("item", st.getHoverName().getString(), st.getCount(), data);
               } else {
                  return null;
               }
            } catch (Throwable var6) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   public static ItemStack decodeItem(MmMessages.BlobOffer b) {
      if (b != null && "item".equals(b.kind)) {
         try {
            CompoundTag root = decompress(b.data);
            if (root == null) {
               return ItemStack.EMPTY;
            } else {
               return root.get("item") instanceof CompoundTag ct
                  ? ItemStack.CODEC.parse(reg().createSerializationContext(NbtOps.INSTANCE), ct).result().orElse(ItemStack.EMPTY)
                  : ItemStack.EMPTY;
            }
         } catch (Throwable var4) {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static MmMessages.BlobOffer captureFilter() {
      RiptideSharedState s = RiptideSharedState.get();
      Set<Class<? extends Packet<?>>> c2s = s.getC2SPackets();
      Set<Class<? extends Packet<?>>> s2c = s.getS2CPackets();
      CompoundTag root = new CompoundTag();
      root.putString("c2s", classNames(c2s));
      root.putString("s2c", classNames(s2c));
      String data = compress(root);
      return data == null ? null : new MmMessages.BlobOffer("filter", "Packet Filter", c2s.size() + s2c.size(), data);
   }

   public static int importFilter(MmMessages.BlobOffer b) {
      if (b != null && "filter".equals(b.kind)) {
         CompoundTag root = decompress(b.data);
         if (root == null) {
            return 0;
         } else {
            RiptideSharedState s = RiptideSharedState.get();
            Set<Class<? extends Packet<?>>> c2s = new HashSet<>(s.getC2SPackets());
            Set<Class<? extends Packet<?>>> s2c = new HashSet<>(s.getS2CPackets());
            int added = resolve(root.getStringOr("c2s", ""), c2s) + resolve(root.getStringOr("s2c", ""), s2c);
            s.setC2SPackets(c2s);
            s.setS2CPackets(s2c);
            return added;
         }
      } else {
         return 0;
      }
   }

   public static MmBlobs.FilterView decodeFilter(MmMessages.BlobOffer b) {
      if (b != null && "filter".equals(b.kind)) {
         CompoundTag root = decompress(b.data);
         return root == null
            ? new MmBlobs.FilterView(List.of(), List.of())
            : new MmBlobs.FilterView(simpleNames(root.getStringOr("c2s", "")), simpleNames(root.getStringOr("s2c", "")));
      } else {
         return new MmBlobs.FilterView(List.of(), List.of());
      }
   }

   private static List<String> simpleNames(String joined) {
      List<String> out = new ArrayList<>();
      if (joined != null && !joined.isBlank()) {
         for (String raw : joined.split("\n")) {
            String n = raw.trim();
            if (!n.isEmpty()) {
               int dot = n.lastIndexOf(46);
               out.add(dot >= 0 ? n.substring(dot + 1) : n);
            }
         }

         out.sort(String.CASE_INSENSITIVE_ORDER);
         return out;
      } else {
         return out;
      }
   }

   private static String classNames(Set<Class<? extends Packet<?>>> set) {
      StringBuilder sb = new StringBuilder();

      for (Class<?> c : set) {
         if (sb.length() > 0) {
            sb.append('\n');
         }

         sb.append(c.getName());
      }

      return sb.toString();
   }

   private static Map<String, Class<? extends Packet<?>>> knownPacketsByName() {
      Map<String, Class<? extends Packet<?>>> map = knownPacketsByName;
      if (map == null) {
         Map<String, Class<? extends Packet<?>>> built = new HashMap<>();

         try {
            for (Class<? extends Packet<?>> c : RiptidePacketRegistry.getC2SPackets()) {
               built.put(c.getName(), c);
            }

            for (Class<? extends Packet<?>> c : RiptidePacketRegistry.getS2CPackets()) {
               built.put(c.getName(), c);
            }
         } catch (Throwable var4) {
         }

         if (!built.isEmpty()) {
            knownPacketsByName = built;
         }

         map = built;
      }

      return map;
   }

   private static int resolve(String joined, Set<Class<? extends Packet<?>>> into) {
      if (joined != null && !joined.isBlank()) {
         Map<String, Class<? extends Packet<?>>> known = knownPacketsByName();
         int added = 0;

         for (String raw : joined.split("\n")) {
            String name = raw.trim();
            if (!name.isEmpty()) {
               Class<? extends Packet<?>> c = known.get(name);
               if (c != null && into.add(c)) {
                  added++;
               }
            }
         }

         return added;
      } else {
         return 0;
      }
   }

   public static MmMessages.BlobOffer captureModule(Module m) {
      if (m == null) {
         return null;
      } else if (!m.settingsShareable()) {
         return null;
      } else {
         try {
            CompoundTag root = new CompoundTag();
            root.putString("id", m.id());
            root.putString("name", m.name());
            CompoundTag s = new CompoundTag();

            for (Setting<?, ?> opt : m.settings()) {
               s.putString(opt.id(), m.value(opt.id()));
            }

            root.put("settings", s);
            String data = compress(root);
            return data == null ? null : new MmMessages.BlobOffer("module", m.name(), m.settings().size(), data);
         } catch (Throwable var5) {
            return null;
         }
      }
   }

   public static MmBlobs.ModuleView decodeModule(MmMessages.BlobOffer b) {
      if (b != null && "module".equals(b.kind)) {
         CompoundTag root = decompress(b.data);
         if (root == null) {
            return null;
         } else {
            String id = MmText.clean(root.getStringOr("id", ""), 64);
            if (isProtectedModuleId(id)) {
               return null;
            } else {
               String name = MmText.clean(root.getStringOr("name", b.friendlyName), 48);
               CompoundTag s = root.getCompound("settings").orElse(new CompoundTag());
               List<String[]> settings = new ArrayList<>();

               for (String key : s.keySet()) {
                  settings.add(new String[]{MmText.clean(key, 48), MmText.clean(s.getStringOr(key, ""), 96)});
               }

               settings.sort((a, c) -> a[0].compareToIgnoreCase(c[0]));
               return new MmBlobs.ModuleView(id, name, settings);
            }
         }
      } else {
         return null;
      }
   }

   public static int applyModule(MmMessages.BlobOffer b) {
      if (b != null && "module".equals(b.kind)) {
         CompoundTag root = decompress(b.data);
         if (root == null) {
            return -1;
         } else {
            Module m = ModuleRegistry.get(root.getStringOr("id", ""));
            if (m == null) {
               return -1;
            } else if (!m.settingsShareable()) {
               return -1;
            } else {
               CompoundTag s = root.getCompound("settings").orElse(new CompoundTag());
               int applied = 0;

               for (String key : s.keySet()) {
                  if (m.setting(key) != null) {
                     m.setValue(key, s.getStringOr(key, ""));
                     applied++;
                  }
               }

               return applied;
            }
         }
      } else {
         return -1;
      }
   }

   private static boolean isProtectedModuleId(String id) {
      if (id != null && !id.isBlank()) {
         Module m = ModuleRegistry.get(id);
         return m != null && !m.settingsShareable();
      } else {
         return false;
      }
   }

   public static String moduleName(MmMessages.BlobOffer b) {
      MmBlobs.ModuleView mv = decodeModule(b);
      return mv == null ? (b == null ? "" : b.friendlyName) : mv.name();
   }

   public static MmMessages.BlobOffer capturePosition() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.level() != null) {
         String dim = player.level().dimension().identifier().toString();
         int x = (int)Math.floor(player.getX());
         int y = (int)Math.floor(player.getY());
         int z = (int)Math.floor(player.getZ());
         return new MmMessages.BlobOffer("position", shortDim(dim) + "  " + x + " " + y + " " + z, 0, x + " " + y + " " + z);
      } else {
         return null;
      }
   }

   public static MmMessages.BlobOffer captureServer() {
      Minecraft mc = Minecraft.getInstance();
      ServerData sd = mc.getCurrentServer();
      if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
         String name = sd.name != null && !sd.name.isBlank() ? sd.name : sd.ip;
         int players = 0;
         int ping = -1;

         try {
            ClientPacketListener conn = mc.getConnection();
            if (conn != null && conn.getOnlinePlayers() != null) {
               players = conn.getOnlinePlayers().size();
            }

            if (conn != null && mc.player != null) {
               PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
               if (info != null) {
                  ping = Math.max(0, Math.min(99999, info.getLatency()));
               }
            }
         } catch (Throwable var10) {
         }

         if (ping < 0) {
            try {
               ping = (int)Math.max(0L, Math.min(99999L, sd.ping));
            } catch (Throwable var9) {
            }
         }

         int maxPlayers = 0;

         try {
            if (sd.players != null) {
               maxPlayers = Math.max(0, sd.players.max());
            }
         } catch (Throwable var8) {
         }

         String motd = serverMotdText(sd);
         String data = field(name) + "|" + field(sd.ip) + "|" + players + "|" + maxPlayers + "|" + ping + "|" + MmText.clean(motd, 120);
         return new MmMessages.BlobOffer("server", name, players, data);
      } else {
         return null;
      }
   }

   private static String serverMotdText(ServerData sd) {
      try {
         Field f;
         try {
            f = ServerData.class.getField("motd");
         } catch (NoSuchFieldException var5) {
            f = ServerData.class.getDeclaredField("motd");
         }

         f.setAccessible(true);
         if (f.get(sd) instanceof Component c) {
            String s = c.getString();
            return s == null ? "" : s.trim();
         }
      } catch (Throwable var6) {
      }

      return "";
   }

   private static String field(String s) {
      return MmText.clean(s, 80).replace('|', ' ');
   }

   private static String[] serverParts(MmMessages.BlobOffer b) {
      return b != null && b.data != null ? b.data.split("\\|", 6) : new String[0];
   }

   public static String serverName(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      return p.length >= 1 ? p[0] : "";
   }

   public static String serverIp(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      return p.length >= 2 ? p[1] : "";
   }

   public static int serverPlayers(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      if (p.length >= 3) {
         try {
            return Integer.parseInt(p[2].trim());
         } catch (NumberFormatException var3) {
         }
      }

      return b == null ? 0 : b.count;
   }

   public static int serverPlayersMax(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      if (p.length >= 6) {
         try {
            return Math.max(0, Integer.parseInt(p[3].trim()));
         } catch (NumberFormatException var3) {
         }
      }

      return 0;
   }

   public static int serverPing(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      if (p.length >= 6) {
         try {
            return Integer.parseInt(p[4].trim());
         } catch (NumberFormatException var3) {
         }
      }

      return -1;
   }

   public static String serverMotd(MmMessages.BlobOffer b) {
      String[] p = serverParts(b);
      if (p.length >= 6) {
         return p[5];
      } else {
         return p.length == 4 ? p[3] : "";
      }
   }

   public static String displayAddr(String ip) {
      if (ip == null) {
         return "";
      } else {
         String s = ip.strip();
         return s.endsWith(":25565") ? s.substring(0, s.length() - ":25565".length()) : s;
      }
   }

   public static String shortDim(String dim) {
      if (dim == null) {
         return "?";
      } else {
         int i = dim.indexOf(58);
         return i >= 0 ? dim.substring(i + 1) : dim;
      }
   }

   public static String encodeOffer(MmMessages.BlobOffer b) {
      if (b == null) {
         return "";
      } else {
         CompoundTag root = new CompoundTag();
         root.putString("type", "riptide_mm_blob");
         root.putString("kind", b.kind == null ? "" : b.kind);
         root.putString("name", b.friendlyName == null ? "" : b.friendlyName);
         root.putInt("count", b.count);
         root.putString("data", b.data == null ? "" : b.data);
         String s = compress(root);
         return s == null ? "" : s;
      }
   }

   public static MmMessages.BlobOffer decodeOffer(String base64) {
      try {
         CompoundTag root = decompress(base64);
         if (root != null && "riptide_mm_blob".equals(root.getStringOr("type", ""))) {
            String kind = root.getStringOr("kind", "");
            return kind.isBlank()
               ? null
               : new MmMessages.BlobOffer(kind, root.getStringOr("name", ""), root.getIntOr("count", 0), root.getStringOr("data", ""));
         } else {
            return null;
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private static String compress(CompoundTag root) {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         NbtIo.writeCompressed(root, out);
         return Base64.getEncoder().encodeToString(out.toByteArray());
      } catch (Throwable var2) {
         return null;
      }
   }

   private static CompoundTag decompress(String base64) {
      try {
         byte[] bytes = Base64.getDecoder().decode(base64);
         return NbtIo.readCompressed(new ByteArrayInputStream(bytes), RiptideClipboardHelper.safeNbtAccounter());
      } catch (Throwable var2) {
         return null;
      }
   }

   public record FilterView(List<String> c2s, List<String> s2c) {
   }

   public record GuiSnapshot(Component title, List<MmBlobs.SlotView> slots, int width, int height) {
   }

   public record ModuleView(String moduleId, String name, List<String[]> settings) {
   }

   public record SlotView(int x, int y, ItemStack item) {
   }
}
