package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.Registry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentInitializers.PendingComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryDataLoader.NetworkedRegistryData;
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.security.RiptideRegistryComponentCompat;

@Mixin({RegistryDataCollector.class})
public abstract class RiptideRegistryDataCollectorMixin {
   private static final Set<String> RIPTIDE$REPORTED_WORLD_CLOCK_FIXES = ConcurrentHashMap.newKeySet();
   private static final Identifier RIPTIDE$OVERWORLD_CLOCK = Identifier.withDefaultNamespace("overworld");
   private static final Identifier RIPTIDE$THE_END_CLOCK = Identifier.withDefaultNamespace("the_end");

   @ModifyArg(
      method = {"loadNewElementsAndTags"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/resources/RegistryDataLoader;load(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
      ),
      index = 0,
      require = 0
   )
   private Map<ResourceKey<? extends Registry<?>>, NetworkedRegistryData> riptide$ensureVanillaWorldClocks(
      Map<ResourceKey<? extends Registry<?>>, NetworkedRegistryData> entries
   ) {
      if (entries == null) {
         return null;
      } else {
         NetworkedRegistryData previous = entries.get(Registries.WORLD_CLOCK);
         List<PackedRegistryEntry> elements = new ArrayList<>(previous == null ? List.of() : previous.elements());
         boolean changed = riptide$addWorldClockIfMissing(elements, RIPTIDE$OVERWORLD_CLOCK);
         changed |= riptide$addWorldClockIfMissing(elements, RIPTIDE$THE_END_CLOCK);
         if (!changed) {
            return entries;
         } else {
            entries.put(Registries.WORLD_CLOCK, new NetworkedRegistryData(List.copyOf(elements), previous == null ? NetworkPayload.EMPTY : previous.tags()));
            if (RIPTIDE$REPORTED_WORLD_CLOCK_FIXES.add("vanilla-world-clocks")) {
               riptide.RiptideClientAddon.LOG
                  .warn(
                     "[Riptide] Server registry payload was missing vanilla world clocks; added minecraft:overworld/minecraft:the_end so configuration can continue."
                  );
            }

            return entries;
         }
      }
   }

   private static boolean riptide$addWorldClockIfMissing(List<PackedRegistryEntry> elements, Identifier id) {
      for (PackedRegistryEntry element : elements) {
         if (id.equals(element.id())) {
            return false;
         }
      }

      elements.add(new PackedRegistryEntry(id, Optional.of(new CompoundTag())));
      return true;
   }

   @WrapOperation(
      method = {"updateComponents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/component/DataComponentInitializers;build(Lnet/minecraft/core/HolderLookup$Provider;)Ljava/util/List;"
      )},
      require = 0
   )
   private static List<PendingComponents<?>> riptide$buildRemoteComponentsCompat(
      DataComponentInitializers initializers, Provider context, Operation<List<PendingComponents<?>>> original
   ) {
      RiptideRegistryComponentCompat.beginRemoteComponentBake();

      List var3;
      try {
         var3 = (List)original.call(new Object[]{initializers, context});
      } finally {
         RiptideRegistryComponentCompat.endRemoteComponentBake();
      }

      return var3;
   }
}
