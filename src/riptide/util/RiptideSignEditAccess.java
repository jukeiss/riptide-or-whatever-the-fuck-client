package riptide.util;

import net.minecraft.core.BlockPos;

public interface RiptideSignEditAccess {
   BlockPos riptide$getSignPos();

   boolean riptide$isFrontText();

   String[] riptide$getSignLines();
}
