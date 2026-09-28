package riptide.util;

import java.util.List;

public record RiptidePacketFieldValue(
   RiptidePacketSchemaRegistry.FieldSchema schema, Object rawValue, List<String> formattedLines, boolean readable, boolean editableCandidate
) {
   public RiptidePacketFieldValue(
      RiptidePacketSchemaRegistry.FieldSchema schema, Object rawValue, List<String> formattedLines, boolean readable, boolean editableCandidate
   ) {
      formattedLines = formattedLines == null ? List.of() : List.copyOf(formattedLines);
      this.schema = schema;
      this.rawValue = rawValue;
      this.formattedLines = formattedLines;
      this.readable = readable;
      this.editableCandidate = editableCandidate;
   }

   public String name() {
      return this.schema == null ? "field" : this.schema.name();
   }

   public String javaType() {
      return this.schema == null ? "Object" : this.schema.javaType();
   }

   public String valueKind() {
      return this.schema == null ? "object" : this.schema.valueKind();
   }

   public String summary() {
      return this.formattedLines.isEmpty() ? "unavailable" : this.formattedLines.getFirst();
   }

   public List<String> details() {
      return this.formattedLines.size() <= 1 ? List.of() : this.formattedLines.subList(1, this.formattedLines.size());
   }
}
