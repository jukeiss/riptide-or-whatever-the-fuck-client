package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.modules.RiptideModule;

public class MatchmakingCommand extends Command {
   public MatchmakingCommand() {
      super("matchmaking", "Toggle the Matchmaking overlay.", "mm");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Minecraft mc = Minecraft.getInstance();
         mc.execute(() -> {
            RiptideModule mod = RiptideModule.get();
            if (mod != null) {
               mod.toggleMatchmakingUiBehavior();
            }
         });
         return 1;
      });
   }
}
