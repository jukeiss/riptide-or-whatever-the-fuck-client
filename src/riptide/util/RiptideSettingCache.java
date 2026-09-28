package riptide.util;

import java.util.List;
import java.util.function.Function;

public final class RiptideSettingCache<T> {
   private final Function<List<String>, T> builder;
   private volatile RiptideSettingCache.Snapshot<T> snapshot;

   public RiptideSettingCache(Function<List<String>, T> var1) {
      if (var1 == null) {
         throw new IllegalArgumentException("builder cannot be null");
      } else {
         this.builder = var1;
      }
   }

   public T get(List<String> var1) {
      return this.get(var1, "");
   }

   public T get(List<String> var1, String var2) {
      List var3 = var1 == null ? List.of() : var1;
      String var4 = keyOf(var3, var2);
      RiptideSettingCache.Snapshot var5 = this.snapshot;
      if (var5 != null && var5.key().equals(var4)) {
         return (T)var5.value();
      } else {
         Object var6 = this.builder.apply(var3);
         this.snapshot = new RiptideSettingCache.Snapshot<>(var4, (T)var6);
         return (T)var6;
      }
   }

   public void invalidate() {
      this.snapshot = null;
   }

   private static String keyOf(List<String> var0, String var1) {
      StringBuilder var2 = new StringBuilder();
      var2.append(var1 == null ? "" : var1).append('\u0000');

      for (String var4 : var0) {
         String var5 = var4 == null ? "" : var4;
         var2.append(var5.length()).append(':').append(var5).append('\u0000');
      }

      return var2.toString();
   }

   private record Snapshot<V>(String key, V value) {
   }
}
