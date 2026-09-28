package riptide.gui.vanillaui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class SearchableSelector<T> {
   private final Function<T, String> searchText;
   private List<T> source = List.of();
   private List<T> filtered = List.of();
   private String query = "";
   private boolean dirty = true;

   public SearchableSelector(Function<T, String> searchText) {
      this.searchText = Objects.requireNonNull(searchText);
   }

   public void setItems(List<T> items) {
      List<T> next = items != null && !items.isEmpty() ? List.copyOf(items) : List.of();
      if (!this.source.equals(next)) {
         this.source = next;
         this.dirty = true;
      }
   }

   public void setQuery(String query) {
      String next = normalize(query);
      if (!this.query.equals(next)) {
         this.query = next;
         this.dirty = true;
      }
   }

   public List<T> items() {
      this.rebuildIfDirty();
      return this.filtered;
   }

   public int size() {
      return this.items().size();
   }

   private void rebuildIfDirty() {
      if (this.dirty) {
         if (this.query.isEmpty()) {
            this.filtered = this.source;
         } else {
            ArrayList<T> next = new ArrayList<>();

            for (T item : this.source) {
               String haystack = normalize(this.searchText.apply(item));
               if (haystack.contains(this.query)) {
                  next.add(item);
               }
            }

            this.filtered = List.copyOf(next);
         }

         this.dirty = false;
      }
   }

   private static String normalize(String value) {
      return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
   }
}
