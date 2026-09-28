package riptide.api.custommenu;

import java.util.List;

public record CustomMenuSnapshot(
   String adapterId, String phase, long generation, String title, List<CustomMenuInput> inputs, List<CustomMenuButton> buttons, Object adapterState
) {
   public CustomMenuSnapshot(
      String adapterId, String phase, long generation, String title, List<CustomMenuInput> inputs, List<CustomMenuButton> buttons, Object adapterState
   ) {
      adapterId = adapterId == null ? "" : adapterId;
      phase = phase == null ? "" : phase;
      title = title == null ? "" : title;
      inputs = inputs == null ? List.of() : List.copyOf(inputs);
      buttons = buttons == null ? List.of() : List.copyOf(buttons);
      this.adapterId = adapterId;
      this.phase = phase;
      this.generation = generation;
      this.title = title;
      this.inputs = inputs;
      this.buttons = buttons;
      this.adapterState = adapterState;
   }

   public CustomMenuSnapshot withConnectionState(String newPhase, long newGeneration) {
      return new CustomMenuSnapshot(this.adapterId, newPhase, newGeneration, this.title, this.inputs, this.buttons, this.adapterState);
   }
}
