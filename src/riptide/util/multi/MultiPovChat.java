package riptide.util.multi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.State;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;

public final class MultiPovChat {
   private static final int VANILLA_HISTORY_LIMIT = 100;
   private static volatile String activeAccountId;
   private static volatile long viewEpoch;
   private static ChatComponent activeChat;
   private static State renderedClientState;
   private static final ThreadLocal<Boolean> ADDING_BOT_MESSAGE = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Boolean> ROUTING_RENDERED_CLIENT = ThreadLocal.withInitial(() -> false);

   private MultiPovChat() {
   }

   static void enter(String accountId) {
      Minecraft mc = Minecraft.getInstance();
      if (accountId != null && mc != null && mc.gui != null && mc.gui.hud != null) {
         ChatComponent chat = mc.gui.hud.getChat();
         if (chat != null) {
            exit();
            activeChat = chat;
            renderedClientState = chat.storeState();
            activeAccountId = accountId;
            viewEpoch++;
            MultiManager manager = MultiManager.getIfInitialized();
            List<MultiPovChat.HistoryLine> history = manager == null ? List.of() : manager.povChatHistory(accountId);
            chat.restoreState(historyState(mc, history));
            chat.resetChatScroll();
         }
      }
   }

   static void exit() {
      ChatComponent chat = activeChat;
      State restore = renderedClientState;
      activeAccountId = null;
      viewEpoch++;
      activeChat = null;
      renderedClientState = null;
      if (chat != null && restore != null) {
         try {
            chat.restoreState(restore);
            chat.resetChatScroll();
         } catch (Error | RuntimeException var3) {
            riptide.RiptideClientAddon.LOG.warn("Could not restore rendered-client chat after POV", var3);
         }
      }
   }

   static void onBotChat(MultiSession session, Component message) {
      String accountId = activeAccountId;
      if (session != null && message != null && accountId != null && accountId.equals(session.accountId())) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            long expectedEpoch = viewEpoch;
            mc.execute(() -> {
               if (expectedEpoch == viewEpoch && session.accountId().equals(activeAccountId)) {
                  ChatComponent chat = activeChat;
                  if (chat != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() == chat) {
                     ADDING_BOT_MESSAGE.set(true);

                     try {
                        chat.addServerSystemMessage(message);
                     } finally {
                        ADDING_BOT_MESSAGE.remove();
                     }
                  }
               }
            });
         }
      }
   }

   public static State beginRenderedClientMutation(ChatComponent chat) {
      if (chat != null && chat == activeChat && activeAccountId != null && !ADDING_BOT_MESSAGE.get() && !ROUTING_RENDERED_CLIENT.get()) {
         State botState = chat.storeState();
         State mainState = renderedClientState;
         if (mainState == null) {
            return null;
         } else {
            ROUTING_RENDERED_CLIENT.set(true);

            try {
               chat.restoreState(mainState);
               return botState;
            } catch (Throwable var4) {
               ROUTING_RENDERED_CLIENT.remove();
               throw var4;
            }
         }
      } else {
         return null;
      }
   }

   public static void endRenderedClientMutation(ChatComponent chat, State botState) {
      if (chat != null && botState != null) {
         try {
            renderedClientState = chat.storeState();
            chat.restoreState(botState);
         } finally {
            ROUTING_RENDERED_CLIENT.remove();
         }
      }
   }

   private static State historyState(Minecraft mc, List<MultiPovChat.HistoryLine> history) {
      int size = Math.min(100, history == null ? 0 : history.size());
      List<GuiMessage> messages = new ArrayList<>(size);
      int currentTick = mc.gui.hud.getGuiTicks();
      long now = System.currentTimeMillis();
      int first = history == null ? 0 : history.size() - size;

      for (int i = first; history != null && i < history.size(); i++) {
         MultiPovChat.HistoryLine line = history.get(i);
         long ageTicks = Math.max(0L, (now - line.receivedAt()) / 50L);
         GuiMessage message = new GuiMessage(
            (int)Math.max(0L, currentTick - Math.min(2147483647L, ageTicks)),
            line.component(),
            null,
            GuiMessageSource.SYSTEM_SERVER,
            GuiMessageTag.systemSinglePlayer()
         );
         messages.add(0, message);
      }

      return new State(messages, List.of(), List.of());
   }

   record HistoryLine(long receivedAt, Component component) {
   }
}
