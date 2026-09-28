package riptide.util.mm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import riptide.util.RiptideClientMessaging;
import riptide.util.mm.msg.MmMessages;

public final class MmChatComponents {
   private static final int TAG = 8161736;
   private static final int MUTED = 10132122;
   private static final int BODY = 15132390;
   private static final int HILITE = 16762967;
   private static final int BTN_DO = 7332768;
   private static final int BTN_VIEW = 9347327;
   private static final int BTN_COPY = 11579568;
   private static final int BTN_JOIN = 16762967;

   private MmChatComponents() {
   }

   public static void emit(MmChatLine line) {
      if (line != null) {
         try {
            RiptideClientMessaging.send(build(line));
         } catch (Throwable var2) {
         }
      }
   }

   public static Component build(MmChatLine line) {
      MutableComponent root = Component.empty().append(tag());
      if (line.system) {
         return root.append(seg(trunc(line.text, 96), 10132122, true));
      } else if (!line.isCard()) {
         return root.append(name(line)).append(seg(": ", 10132122, false)).append(seg(trunc(line.text, 220), 15132390, false));
      } else {
         List<MmChatComponents.Btn> btns = new ArrayList<>();
         appendSentence(root, line, btns);
         if (!btns.isEmpty()) {
            int token = MmCardActions.register(line);

            for (MmChatComponents.Btn b : btns) {
               root.append(seg("  ", 10132122, false)).append(btn(b, token));
            }
         }

         return root;
      }
   }

   private static MutableComponent btn(MmChatComponents.Btn b, int token) {
      Style style = Style.EMPTY
         .withColor(b.color())
         .withClickEvent(new RunCommand("/riptidecard " + b.action() + " " + token))
         .withHoverEvent(new ShowText(Component.literal(b.hover())));
      return Component.literal("[" + b.label() + "]").setStyle(style);
   }

   private static void appendSentence(MutableComponent root, MmChatLine line, List<MmChatComponents.Btn> btns) {
      boolean self = line.self;
      switch (line.kind) {
         case MACRO_CARD:
            int steps = line.macro.actionCount;
            root.append(name(line)).append(seg(" shared a macro", 10132122, false));
            if (line.macro.macroName != null && !line.macro.macroName.isBlank()) {
               root.append(seg(" \"" + trunc(line.macro.macroName, 26) + "\"", 16762967, false));
            }

            root.append(seg(" (" + steps + (steps == 1 ? " step)" : " steps)"), 10132122, false));
            if (!self) {
               btns.add(new MmChatComponents.Btn("Inspect", "inspect", "Review and save this macro to your library", 9347327));
            }

            btns.add(copy("Copy the macro"));
            break;
         case COMMAND_CARD:
            boolean tpa = MmCardActions.isTpa(line);
            if (tpa) {
               if (self) {
                  root.append(name(line)).append(seg(" sent a TPA invite", 10132122, false));
               } else if (MatchmakingManager.sameServerAs(MatchmakingManager.get().peer(line.senderFpHex))) {
                  root.append(name(line)).append(seg(" invited you to TPA", 15132390, false));
                  btns.add(new MmChatComponents.Btn("Accept", "run", "Send /tpa to accept", 7332768));
               } else {
                  root.append(name(line)).append(seg(" invited you to TPA ", 10132122, false)).append(seg("(not on their server)", 10132122, true));
               }
            } else {
               root.append(name(line)).append(seg(" shared a command ", 10132122, false)).append(seg(trunc(line.command.body, 44), 16762967, false));
               if (!self) {
                  btns.add(new MmChatComponents.Btn("Execute", "run", "Run this command", 7332768));
               }
            }

            btns.add(copy("Copy the command"));
            break;
         case PACKET_CARD:
            root.append(name(line))
               .append(seg(" shared a packet queue ", 10132122, false))
               .append(seg("(" + trunc(line.cardHeadline(), 26) + ")", 16762967, false));
            if (!self) {
               btns.add(new MmChatComponents.Btn("Add to queue", "addqueue", "Add these packets to your queue", 7332768));
            }

            btns.add(new MmChatComponents.Btn("Inspect", "inspect", "Inspect the packet", 9347327));
            btns.add(copy("Copy the packet data"));
            break;
         case BLOB_CARD:
            appendBlob(root, line, btns, self);
            break;
         default:
            root.append(name(line)).append(seg(": ", 10132122, false)).append(seg(trunc(line.text, 200), 15132390, false));
      }
   }

   private static void appendBlob(MutableComponent root, MmChatLine line, List<MmChatComponents.Btn> btns, boolean self) {
      MmMessages.BlobOffer b = line.blob;
      String var5 = b.kind;
      switch (var5) {
         case "gui":
            root.append(name(line)).append(seg(" shared a GUI ", 10132122, false)).append(seg("\"" + trunc(b.friendlyName, 26) + "\"", 16762967, false));
            btns.add(new MmChatComponents.Btn("View", "view", "View the shared GUI", 9347327));
            break;
         case "item":
            root.append(name(line)).append(seg(" shared an item ", 10132122, false)).append(seg(trunc(b.friendlyName, 28), 16762967, false));
            btns.add(new MmChatComponents.Btn("Inspect", "inspect", "Inspect the shared item", 9347327));
            btns.add(copy("Copy"));
            break;
         case "filter":
            root.append(name(line)).append(seg(" shared a packet filter ", 10132122, false)).append(seg("(" + b.count + ")", 16762967, false));
            if (!self) {
               btns.add(new MmChatComponents.Btn("Import", "importfilter", "Merge this packet filter", 7332768));
            }

            btns.add(new MmChatComponents.Btn("Inspect", "inspect", "See the packets in this filter", 9347327));
            btns.add(copy("Copy"));
            break;
         case "position":
            root.append(name(line)).append(seg(" shared a position ", 10132122, false)).append(seg(trunc(b.friendlyName, 36), 16762967, false));
            btns.add(copy("Copy the position"));
            break;
         case "server":
            String ip = MmBlobs.serverIp(b);
            int players = MmBlobs.serverPlayers(b);
            int max = MmBlobs.serverPlayersMax(b);
            int ping = MmBlobs.serverPing(b);
            String stats = " · " + players + (max > 0 ? "/" + max : "") + " online" + (ping >= 0 ? " · " + ping + "ms" : "");
            root.append(name(line))
               .append(seg(" shared a server ", 10132122, false))
               .append(seg(trunc(displayAddr(ip), 30), 16762967, false))
               .append(seg(stats, 10132122, false));
            if (!self && !MatchmakingManager.alreadyOn(ip)) {
               btns.add(new MmChatComponents.Btn("Join", "join", "Join this server", 16762967));
            }

            btns.add(copy("Copy the address"));
            break;
         case "steps":
            root.append(name(line))
               .append(seg(" shared macro steps ", 10132122, false))
               .append(seg("(" + b.count + (b.count == 1 ? " step)" : " steps)"), 16762967, false));
            if (!self) {
               btns.add(new MmChatComponents.Btn("Import", "importsteps", "Import as a new macro", 7332768));
               btns.add(new MmChatComponents.Btn("To editor", "toeditor", "Open these steps in the macro editor", 9347327));
            }

            btns.add(copy("Copy the steps"));
            break;
         case "module":
            root.append(name(line))
               .append(seg(" shared module settings ", 10132122, false))
               .append(seg("\"" + trunc(b.friendlyName, 24) + "\"", 16762967, false));
            btns.add(new MmChatComponents.Btn("Preview", "previewmodule", "Preview the module settings", 9347327));
            if (!self) {
               btns.add(new MmChatComponents.Btn("Apply", "applymodule", "Apply these settings to your module", 7332768));
            }

            btns.add(copy("Copy"));
            break;
         default:
            root.append(name(line)).append(seg(" shared something", 10132122, false));
            btns.add(copy("Copy"));
      }
   }

   private static Component tag() {
      return Component.literal("Lobby ").setStyle(Style.EMPTY.withColor(8161736)).append(Component.literal("· ").setStyle(Style.EMPTY.withColor(10132122)));
   }

   private static MutableComponent name(MmChatLine line) {
      String n = trunc(MatchmakingManager.get().displayNameFor(line.senderFpHex), 24);
      MutableComponent c;
      if (MatchmakingManager.isGradientName(line.senderFpHex, line.self)) {
         c = Component.empty();
         int len = n.length();

         for (int i = 0; i < len; i++) {
            c.append(
               Component.literal(String.valueOf(n.charAt(i)))
                  .setStyle(Style.EMPTY.withColor(MatchmakingManager.gradientNameColor(line.senderFpHex, line.self, i, len)))
            );
         }
      } else {
         c = seg(n, MatchmakingManager.nameColor(line.senderFpHex, line.self), false);
      }

      if (line.self) {
         c.append(seg(" (you)", 10132122, false));
      }

      return c;
   }

   private static MutableComponent seg(String s, int color, boolean italic) {
      return Component.literal(s == null ? "" : s).setStyle(Style.EMPTY.withColor(color).withItalic(italic));
   }

   private static MmChatComponents.Btn copy(String hover) {
      return new MmChatComponents.Btn("Copy", "copy", hover, 11579568);
   }

   private static String trunc(String s, int max) {
      if (s == null) {
         return "";
      } else {
         s = s.strip();
         return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)).strip() + "…";
      }
   }

   private static String displayAddr(String ip) {
      return MmBlobs.displayAddr(ip);
   }

   private record Btn(String label, String action, String hover, int color) {
   }
}
