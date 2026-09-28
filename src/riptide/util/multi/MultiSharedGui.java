package riptide.util.multi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class MultiSharedGui {
   private MultiSharedGui() {
   }

   public static List<MultiSharedGui.Group> groups() {
      Map<String, List<String>> byKey = new LinkedHashMap<>();
      Map<String, String> names = new LinkedHashMap<>();

      for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
         if (snapshot.ready()) {
            String key;
            String name;
            if (snapshot.customMenuOpen()) {
               key = "custom";
               name = "CustomScreen";
            } else if (snapshot.openScreen() != null && !snapshot.openScreen().isBlank()) {
               key = "s:" + snapshot.openScreen();
               name = snapshot.openScreen();
            } else {
               key = "inv";
               name = "Inventory";
            }

            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(snapshot.accountId());
            names.putIfAbsent(key, name);
         }
      }

      List<MultiSharedGui.Group> out = new ArrayList<>(byKey.size());

      for (Entry<String, List<String>> entry : byKey.entrySet()) {
         String name = MultiManager.singleLine(names.get(entry.getKey()), 40);
         out.add(new MultiSharedGui.Group(entry.getKey(), name + " (" + entry.getValue().size() + ")", List.copyOf(entry.getValue())));
      }

      out.sort((a, b) -> Integer.compare(b.size(), a.size()));
      return out;
   }

   public static MultiSharedGui.Group pick(List<MultiSharedGui.Group> groups, String preferredKey) {
      if (groups != null && !groups.isEmpty()) {
         if (preferredKey != null && !preferredKey.isEmpty()) {
            for (MultiSharedGui.Group group : groups) {
               if (group.key().equals(preferredKey)) {
                  return group;
               }
            }
         }

         return groups.getFirst();
      } else {
         return null;
      }
   }

   public record Group(String key, String label, List<String> accountIds) {
      public String representativeId() {
         return this.accountIds.getFirst();
      }

      public int size() {
         return this.accountIds.size();
      }
   }
}
