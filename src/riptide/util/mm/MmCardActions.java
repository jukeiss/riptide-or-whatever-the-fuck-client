package riptide.util.mm;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptideFilterViewOverlay;
import riptide.util.RiptideGuiViewOverlay;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideModuleViewOverlay;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketInspectOverlay;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.mm.msg.MmMessages;

public final class MmCardActions {
   private static final Map<Integer, MmChatLine> CARDS = new ConcurrentHashMap<>();
   private static final AtomicInteger SEQ = new AtomicInteger();
   private static final int MAX = 256;
   static final String NAMESPACE = "riptidecard";
   private static final int OK = -13248397;
   private static final int WARN = -14249;
   private static final int ERR = -42149;

   private MmCardActions() {
   }

   public static int register(MmChatLine line) {
      int id = SEQ.incrementAndGet();
      CARDS.put(id, line);
      if (CARDS.size() > 256) {
         int threshold = id - 256;
         CARDS.keySet().removeIf(k -> k <= threshold);
      }

      return id;
   }

   public static boolean handleClickCommand(String command) {
      if (command == null) {
         return false;
      } else {
         String c = command.trim();
         if (c.startsWith("/")) {
            c = c.substring(1).trim();
         }

         if (!c.equals("riptidecard") && !c.startsWith("riptidecard ")) {
            return false;
         } else {
            String rest = c.length() > "riptidecard".length() ? c.substring("riptidecard".length()).trim() : "";
            int sp = rest.indexOf(32);
            if (sp > 0) {
               String action = rest.substring(0, sp).trim();

               try {
                  handleClick(action, Integer.parseInt(rest.substring(sp + 1).trim()));
               } catch (NumberFormatException var6) {
                  RiptideNotifications.show("This shared item is no longer available.", -14249);
               }
            }

            return true;
         }
      }
   }

   public static void handleClick(String action, int token) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.execute(() -> perform(action, token));
      }
   }

   private static void perform(String action, int token) {
      MmChatLine line = CARDS.get(token);
      if (line == null) {
         RiptideNotifications.show("This shared item is no longer available.", -14249);
      } else {
         try {
            switch (action) {
               case "import":
                  if (line.macro != null) {
                     MmShare.importMacro(line.macro);
                  }
                  break;
               case "run":
                  if (line.command != null) {
                     MatchmakingManager.get().runCommandOffer(line.command);
                  }
                  break;
               case "addqueue":
                  if (line.packet != null) {
                     addToQueue(line.packet);
                  }
                  break;
               case "importfilter":
                  if (line.blob != null) {
                     int n = MmBlobs.importFilter(line.blob);
                     RiptideNotifications.show("Imported " + n + " filtered packet(s).", -13248397);
                  }
                  break;
               case "join":
                  if (line.blob != null) {
                     joinServer(line.blob);
                  }
                  break;
               case "importsteps":
                  if (line.blob != null) {
                     importSteps(line.blob);
                  }
                  break;
               case "toeditor":
                  if (line.blob != null) {
                     stepsToEditor(line.blob);
                  }
                  break;
               case "previewmodule":
                  if (line.blob != null) {
                     previewModule(line.blob);
                  }
                  break;
               case "applymodule":
                  if (line.blob != null) {
                     applyModule(line.blob);
                  }
                  break;
               case "copy":
                  copy(line);
                  break;
               case "inspect":
                  inspect(line);
                  break;
               case "view":
                  view(line);
            }
         } catch (Throwable var6) {
            RiptideNotifications.show("Action failed.", -42149);
         }
      }
   }

   private static void addToQueue(MmMessages.PacketOffer offer) {
      int n = MmShare.addToQueue(offer);
      if (n < 0) {
         RiptideNotifications.show("Could not read shared queue.", -42149);
      } else {
         RiptideNotifications.show("Added " + n + " packet(s) to your queue.", -13248397);
      }
   }

   private static void importSteps(MmMessages.BlobOffer b) {
      MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
      offer.hash = b.data;
      offer.macroName = b.friendlyName;
      MmShare.importMacro(offer);
   }

   private static void stepsToEditor(MmMessages.BlobOffer b) {
      RiptideMacro macro = RiptideClipboardHelper.deserializeMacroFromBase64(b.data);
      if (macro == null) {
         RiptideNotifications.show("Could not read the shared steps.", -42149);
      } else {
         RiptideMacroEditorOverlay ed = RiptideMacroEditorOverlay.getSharedOverlay();
         if (ed != null) {
            ed.openForImport(macro);
            openInHost(ed, false);
         }
      }
   }

   private static void previewModule(MmMessages.BlobOffer b) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         RiptideModuleViewOverlay ov = new RiptideModuleViewOverlay(mc.font);
         if (ov.open(b)) {
            openInHost(ov, true);
         }
      }
   }

   private static void applyModule(MmMessages.BlobOffer b) {
      int n = MmBlobs.applyModule(b);
      if (n < 0) {
         RiptideNotifications.show("Could not apply module settings.", -42149);
      } else {
         RiptideNotifications.show("Applied " + n + " setting(s) to " + MmBlobs.moduleName(b) + ".", -13248397);
      }
   }

   public static String clipboardTextFor(MmChatLine line) {
      return switch (line.kind) {
         case MACRO_CARD -> line.macro.hash;
         case PACKET_CARD -> line.packet.data;
         case COMMAND_CARD -> MatchmakingManager.renderCommandOffer(line.command);
         case BLOB_CARD -> {
            String var1 = line.blob.kind;
            switch (var1) {
               case "server":
                  yield MmBlobs.serverIp(line.blob);
               case "position":
                  yield line.blob.data;
               case "item":
               case "filter":
               case "module":
                  yield MmBlobs.encodeOffer(line.blob);
               default:
                  yield line.blob.data;
            }
         }
         default -> line.text;
      };
   }

   private static void copy(MmChatLine line) {
      String data = clipboardTextFor(line);
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.keyboardHandler.setClipboard(data == null ? "" : data);
      }

      RiptideNotifications.copied("Copied to clipboard.");
   }

   private static void inspect(MmChatLine line) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         switch (line.kind) {
            case MACRO_CARD:
               RiptideMacro macro = RiptideClipboardHelper.deserializeMacroFromBase64(line.macro.hash);
               if (macro == null) {
                  RiptideNotifications.show("Could not read macro.", -42149);
                  return;
               }

               RiptideMacroEditorOverlay ed = RiptideMacroEditorOverlay.getSharedOverlay();
               if (ed == null) {
                  return;
               }

               ed.openForImport(macro);
               openInHost(ed, false);
               break;
            case PACKET_CARD:
               RiptidePacketLoggerOverlay.LogEntry entry = MmShare.inspectableEntry(line.packet);
               if (entry == null) {
                  RiptideNotifications.show("Could not rebuild packet.", -42149);
                  return;
               }

               RiptidePacketInspectOverlay ov = new RiptidePacketInspectOverlay(mc.font);
               ov.open(entry, 60, 60);
               openInHost(ov, true);
            case COMMAND_CARD:
            default:
               break;
            case BLOB_CARD:
               if ("item".equals(line.blob.kind)) {
                  ItemStack st = MmBlobs.decodeItem(line.blob);
                  if (st == null || st.isEmpty()) {
                     RiptideNotifications.show("Could not read item.", -42149);
                     return;
                  }

                  RiptideItemNbtInspectOverlay ov = RiptideItemNbtInspectOverlay.getSharedOverlay(mc.font);
                  if (ov == null) {
                     return;
                  }

                  ov.open(st, 60, 60);
                  openInHost(ov, true);
               } else if ("filter".equals(line.blob.kind)) {
                  RiptideFilterViewOverlay ov = new RiptideFilterViewOverlay(mc.font);
                  if (ov.open(line.blob)) {
                     openInHost(ov, true);
                  }
               }
         }
      }
   }

   private static void view(MmChatLine line) {
      if (line.blob != null && "gui".equals(line.blob.kind)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            RiptideGuiViewOverlay ov = new RiptideGuiViewOverlay(mc.font);
            if (ov.open(line.blob)) {
               openInHost(ov, true);
            }
         }
      }
   }

   private static void joinServer(MmMessages.BlobOffer b) {
      if ("server".equals(b.kind)) {
         Minecraft mc = Minecraft.getInstance();
         Screen ret = mc == null ? null : mc.gui.screen();
         confirmJoinServer(MmBlobs.serverIp(b), MmBlobs.serverName(b), ret);
      }
   }

   public static void confirmJoinServer(String rawIp, String displayName, Screen returnScreen) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         String ip = MmText.clean(rawIp, 64);
         if (ip.isBlank()) {
            RiptideNotifications.show("No server address.", -14249);
         } else if (MatchmakingManager.alreadyOn(ip)) {
            RiptideNotifications.show("You're already on that server.", -14249);
         } else {
            String shown = displayName != null && !displayName.isBlank() ? MmText.clean(displayName, 64) : ip;
            mc.gui.setScreen(new ConfirmScreen(ok -> {
               if (ok) {
                  ServerData data = new ServerData(shown, ip, Type.OTHER);
                  ConnectScreen.startConnecting(returnScreen, mc, ServerAddress.parseString(ip), data, false, null);
               } else {
                  mc.gui.setScreen(returnScreen);
               }
            }, Component.literal("Join " + shown + "?"), Component.literal(ip + "\nOnly connect to servers you trust.")));
         }
      }
   }

   private static void openInHost(IRiptideOverlay ov, boolean background) {
      if (background) {
         RiptideOverlayManager.get().register(ov, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
      } else {
         RiptideOverlayManager.get().register(ov);
      }

      RiptideOverlayManager.get().bringToFront(ov);
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.gui.setScreen(new RiptideOverlayHostScreen(ov, null, false, true));
      }
   }

   static boolean isTpa(MmChatLine line) {
      return line.command != null && line.command.body != null && line.command.body.trim().toLowerCase(Locale.ROOT).startsWith("tpa");
   }
}
