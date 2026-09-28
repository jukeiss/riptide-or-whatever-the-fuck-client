package riptide.gui.vanillaui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.TextWrapLayout;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectUiNode;
import riptide.util.RiptideTheme;

public class CompactTextInput extends DirectUiNode implements FocusableTextInput {
   private static final int DEFAULT_MAX_LENGTH = 512;
   private static final long DOUBLE_CLICK_MS = 320L;
   private static final float DOUBLE_CLICK_MAX_DIST = 4.0F;
   private static CompactTextInput focusedInput;
   private String value = "";
   private String placeholder = "";
   private int maxLength = 512;
   private float minWidth = 56.0F;
   private float maxWidth = Float.MAX_VALUE;
   private float preferredWidth = -1.0F;
   private int horizontalPadding = 6;
   private int fixedHeight = -1;
   private int textYOffset = 1;
   private boolean editable = true;
   private boolean drawBackground = true;
   private boolean hoverEffectsEnabled = true;
   private boolean focusEffectsEnabled = true;
   private int backgroundColorOverride = 0;
   private boolean focused = false;
   private int cursor = 0;
   private int selectionAnchor = 0;
   private int viewOffset = 0;
   private int wrapViewLine = 0;
   private boolean draggingMultilineScrollbar = false;
   private int multilineScrollbarGrabOffset = 0;
   private boolean manualWrapScroll = false;
   private boolean draggingSelection = false;
   private boolean multiline = false;
   private long blinkStartMs = System.currentTimeMillis();
   private Identifier fontId = null;
   private UiTone textTone = UiTone.BODY;
   private UiTone placeholderTone = UiTone.MUTED;
   private Predicate<String> filter = next -> true;
   private Consumer<String> onChange;
   private Consumer<String> onSubmit;
   private boolean submitOnEnter;
   private Function<String, Component> displayTextProvider;
   private LongSupplier displayTextRevisionProvider;
   private boolean historyNavigationEnabled = false;
   private final List<String> historyEntries = new ArrayList<>();
   private Supplier<Collection<String>> historyProvider;
   private int historyIndex = -1;
   private String historyDraft = "";
   private boolean applyingHistoryValue = false;
   private String cachedRichValue = "";
   private Function<String, Component> cachedRichProvider;
   private CompactTextInput.RichMetrics cachedRichMetrics;
   private boolean cachedRichComputed;
   private long cachedRichProviderRevision = Long.MIN_VALUE;
   private long textRevision = 1L;
   private long styleRevision = 1L;
   private long measuredTextRevision = Long.MIN_VALUE;
   private long measuredStyleRevision = Long.MIN_VALUE;
   private Font measuredRenderer;
   private Identifier measuredFont;
   private int measuredReloadGeneration = Integer.MIN_VALUE;
   private int measuredColor;
   private CompactTextInput.RichMetrics measuredRichMetrics;
   private int[] cumulativeWidths = new int[]{0};
   private long wrappedTextRevision = Long.MIN_VALUE;
   private long wrappedStyleRevision = Long.MIN_VALUE;
   private Font wrappedRenderer;
   private Identifier wrappedFont;
   private int wrappedReloadGeneration = Integer.MIN_VALUE;
   private int wrappedWidth = Integer.MIN_VALUE;
   private boolean wrappedMultiline;
   private CompactTextInput.RichMetrics wrappedRichMetrics;
   private List<CompactTextInput.WrappedLine> cachedWrappedLines = List.of();
   private String cachedPlaceholderText = "";
   private Font cachedPlaceholderRenderer;
   private Identifier cachedPlaceholderFont;
   private int cachedPlaceholderReloadGeneration = Integer.MIN_VALUE;
   private int cachedPlaceholderWidth = Integer.MIN_VALUE;
   private List<String> cachedPlaceholderLines = List.of();
   private long lastClickMs = 0L;
   private float lastClickX = Float.NaN;
   private float lastClickY = Float.NaN;
   private int repeatedClickCount = 0;

   public CompactTextInput() {
      this.height = 16.0F;
   }

   public CompactTextInput setText(String value) {
      String sanitized = this.sanitize(value);
      if (!Objects.equals(this.value, sanitized)) {
         this.value = sanitized;
         this.invalidateTextCaches();
         this.markLayoutDirty();
         if (this.cursor > this.value.length()) {
            this.cursor = this.value.length();
         }

         if (this.selectionAnchor > this.value.length()) {
            this.selectionAnchor = this.value.length();
         }

         this.manualWrapScroll = false;
         if (this.onChange != null) {
            this.onChange.accept(this.value);
         }
      }

      this.ensureSelectionOrder();
      this.viewOffset = Math.min(this.viewOffset, this.value.length());
      if (this.historyNavigationEnabled && !this.applyingHistoryValue) {
         this.historyIndex = -1;
         this.historyDraft = this.value;
      }

      return this;
   }

   public String text() {
      return this.value;
   }

   public CompactTextInput setPlaceholder(String placeholder) {
      String next = placeholder == null ? "" : placeholder;
      if (!this.placeholder.equals(next)) {
         this.placeholder = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setMaxLength(int maxLength) {
      this.maxLength = Math.max(1, maxLength);
      this.setText(this.value);
      return this;
   }

   public CompactTextInput setMinWidth(float minWidth) {
      float next = Math.max(0.0F, minWidth);
      if (Float.compare(this.minWidth, next) != 0) {
         this.minWidth = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setMaxWidth(float maxWidth) {
      float next = Math.max(this.minWidth, maxWidth);
      if (Float.compare(this.maxWidth, next) != 0) {
         this.maxWidth = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setPreferredWidth(float preferredWidth) {
      if (Float.compare(this.preferredWidth, preferredWidth) != 0) {
         this.preferredWidth = preferredWidth;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setHorizontalPadding(int horizontalPadding) {
      int next = Math.max(0, horizontalPadding);
      if (this.horizontalPadding != next) {
         this.horizontalPadding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setFieldHeight(int fixedHeight) {
      int next = Math.max(1, fixedHeight);
      if (this.fixedHeight != next) {
         this.fixedHeight = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setTextYOffset(int textYOffset) {
      this.textYOffset = textYOffset;
      return this;
   }

   public CompactTextInput setEditable(boolean editable) {
      this.editable = editable;
      if (!editable) {
         this.setFocused(false);
      }

      return this;
   }

   public boolean isEditable() {
      return this.editable;
   }

   public CompactTextInput setDrawBackground(boolean drawBackground) {
      this.drawBackground = drawBackground;
      return this;
   }

   public CompactTextInput setHoverEffectsEnabled(boolean hoverEffectsEnabled) {
      this.hoverEffectsEnabled = hoverEffectsEnabled;
      return this;
   }

   public CompactTextInput setFocusEffectsEnabled(boolean focusEffectsEnabled) {
      this.focusEffectsEnabled = focusEffectsEnabled;
      return this;
   }

   public CompactTextInput setBackgroundColorOverride(int backgroundColorOverride) {
      this.backgroundColorOverride = backgroundColorOverride;
      return this;
   }

   public CompactTextInput setFontId(Identifier fontId) {
      if (!Objects.equals(this.fontId, fontId)) {
         this.fontId = fontId;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setTextTone(UiTone textTone) {
      UiTone next = textTone == null ? UiTone.BODY : textTone;
      if (this.textTone != next) {
         this.textTone = next;
         this.styleRevision++;
         this.invalidateWrappedLines();
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setPlaceholderTone(UiTone placeholderTone) {
      UiTone next = placeholderTone == null ? UiTone.MUTED : placeholderTone;
      if (this.placeholderTone != next) {
         this.placeholderTone = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setFilter(Predicate<String> filter) {
      this.filter = filter == null ? next -> true : filter;
      return this;
   }

   public CompactTextInput setOnChange(Consumer<String> onChange) {
      this.onChange = onChange;
      return this;
   }

   public CompactTextInput setOnSubmit(Consumer<String> onSubmit) {
      this.onSubmit = onSubmit;
      return this;
   }

   public CompactTextInput setDisplayTextProvider(Function<String, Component> displayTextProvider) {
      if (this.displayTextProvider != displayTextProvider) {
         this.displayTextProvider = displayTextProvider;
         this.styleRevision++;
         this.invalidateRichMetrics();
         this.invalidateWrappedLines();
      }

      return this;
   }

   public CompactTextInput setDisplayTextRevisionProvider(LongSupplier revisionProvider) {
      if (this.displayTextRevisionProvider != revisionProvider) {
         this.displayTextRevisionProvider = revisionProvider;
         this.styleRevision++;
         this.invalidateRichMetrics();
         this.invalidateWrappedLines();
      }

      return this;
   }

   public CompactTextInput setMultiline(boolean multiline) {
      if (this.multiline != multiline) {
         this.multiline = multiline;
         if (multiline) {
            this.viewOffset = 0;
         } else {
            this.wrapViewLine = 0;
         }

         this.markLayoutDirty();
      }

      return this;
   }

   public CompactTextInput setSubmitOnEnter(boolean submitOnEnter) {
      this.submitOnEnter = submitOnEnter;
      return this;
   }

   public int wrappedRowCount(DirectRenderContext context, int fieldWidth) {
      if (this.value.isEmpty()) {
         return 1;
      } else {
         Identifier font = this.resolvedFont(context);
         int scaledHorizontalPadding = context.theme().scale(this.horizontalPadding);
         int frameInset = context.theme().scale(2);
         int innerW = this.contentWidth(fieldWidth, scaledHorizontalPadding, frameInset);
         int bodyColor = context.theme().color(this.textTone);
         return Math.max(1, this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, this.buildRichMetrics(this.value)).size());
      }
   }

   public int rowsToHeight(DirectRenderContext context, int rows) {
      int lineHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(1);
      return Math.max(1, rows) * lineHeight + context.theme().scale(6);
   }

   public CompactTextInput setHistoryNavigationEnabled(boolean historyNavigationEnabled) {
      this.historyNavigationEnabled = historyNavigationEnabled;
      if (!historyNavigationEnabled) {
         this.historyIndex = -1;
         this.historyDraft = "";
      }

      return this;
   }

   public CompactTextInput setHistoryProvider(Supplier<Collection<String>> historyProvider) {
      this.historyProvider = historyProvider;
      this.historyIndex = -1;
      this.historyDraft = "";
      return this;
   }

   public CompactTextInput addHistoryEntry(String entry) {
      String sanitized = this.sanitize(entry);
      if (sanitized.isEmpty()) {
         return this;
      } else {
         if (!sanitized.equals(this.historyEntries.isEmpty() ? null : this.historyEntries.get(this.historyEntries.size() - 1))) {
            this.historyEntries.add(sanitized);

            while (this.historyEntries.size() > 100) {
               this.historyEntries.remove(0);
            }
         }

         this.historyIndex = -1;
         this.historyDraft = "";
         return this;
      }
   }

   public CompactTextInput setGrowX(boolean growX) {
      super.setGrowX(growX);
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      if (this.preferredWidth > 0.0F) {
         return UiSizing.clamp(this.preferredWidth, this.minWidth, this.maxWidth);
      } else {
         String sample = this.value.isEmpty() ? this.placeholder : this.value;
         if (sample.isEmpty()) {
            sample = "Component";
         }

         Identifier font = this.resolvedFont(context);
         int color = context.theme().color(this.textTone);
         return UiSizing.fitTextWidth(
            context.textRenderer(), sample, font, color, context.theme().scale(this.horizontalPadding + 2), this.minWidth, this.maxWidth
         );
      }
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      int resolvedFixedHeight = this.fixedHeight > 0 ? this.fixedHeight : Math.max(context.theme().buttonHeight(), context.theme().scale(16));
      int contentHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(5);
      return Math.max(12, Math.max(resolvedFixedHeight, contentHeight));
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawH = Math.round(this.height);
         boolean hovered = this.contains(context.mouseX(), context.mouseY());
         float hover = this.updateHover(hovered, context.delta());
         Identifier font = this.resolvedFont(context);
         int fontHeight = context.theme().fontHeight(this.textTone);
         int scaledHorizontalPadding = context.theme().scale(this.horizontalPadding);
         int textYOffsetPx = context.theme().fieldTextNudge() + this.textYOffset - 1;
         int contentInset = context.theme().scale(3);
         int frameInset = context.theme().scale(2);
         int lineSpacing = context.theme().scale(1);
         int bodyColor = context.theme().color(this.textTone);
         int placeholderColor = context.theme().color(this.placeholderTone);
         int bg = this.backgroundColorOverride != 0 ? this.backgroundColorOverride : (this.focused && this.focusEffectsEnabled ? -1559491569 : 2081032460);
         int border = !this.editable
            ? RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE)
            : (
               this.focused && this.focusEffectsEnabled
                  ? context.theme().headerAccent()
                  : (this.hoverEffectsEnabled && hover > 0.0F ? RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE) : context.theme().borderColor())
            );
         int innerW = this.contentWidth(drawW, scaledHorizontalPadding, frameInset);
         CompactTextInput.RichMetrics richMetrics = this.value.isEmpty() ? null : this.buildRichMetrics(this.value);
         if (!this.multiline) {
            this.ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);
         }

         UiBounds fieldBounds = UiBounds.of(drawX, drawY, drawW, drawH);
         if (this.drawBackground) {
            UiRenderer.rect(context.drawContext(), fieldBounds, context.applyAlpha(bg));
         }

         if (this.editable && this.hoverEffectsEnabled && hover > 0.0F && !this.focused) {
            int hoverTint = RiptideTheme.recolor((int)(hover * 14.0F) << 24 | 16723759, RiptideTheme.Channel.ACCENT);
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(hoverTint));
         }

         UiRenderer.outline(context.drawContext(), fieldBounds, context.applyAlpha(border));
         if (!this.editable) {
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(1712592152));
         } else if (this.focused && this.focusEffectsEnabled) {
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(RiptideTheme.recolor(318716731, RiptideTheme.Channel.ACCENT)));
         }

         int textX = drawX + scaledHorizontalPadding;
         if (this.value.isEmpty()) {
            if (this.multiline) {
               int textY = drawY + contentInset + textYOffsetPx;
               context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);

               try {
                  for (String line : this.wrapPlainText(context.textRenderer(), font, placeholderColor, this.placeholder, innerW)) {
                     if (textY + fontHeight > drawY + drawH - frameInset) {
                        break;
                     }

                     UiText.draw(context.drawContext(), context.textRenderer(), line, font, context.applyAlpha(placeholderColor), textX, textY, false);
                     textY += fontHeight + lineSpacing;
                  }
               } finally {
                  context.viewport().disableScissor(context.drawContext());
               }

               if (this.focused && this.blinkVisible()) {
                  context.drawContext()
                     .fill(
                        textX,
                        drawY + contentInset,
                        textX + 1,
                        drawY + contentInset + fontHeight,
                        context.applyAlpha(RiptideTheme.recolor(-4627, RiptideTheme.Channel.TEXT))
                     );
               }
            } else {
               int textY = UiSizing.alignTextY(drawY, drawH, fontHeight, textYOffsetPx);
               String displayPlaceholder = UiText.trimToWidth(context.textRenderer(), this.placeholder, innerW, font, placeholderColor);
               UiText.draw(context.drawContext(), context.textRenderer(), displayPlaceholder, font, context.applyAlpha(placeholderColor), textX, textY, false);
               if (this.focused && this.blinkVisible()) {
                  context.drawContext()
                     .fill(
                        textX,
                        drawY + contentInset,
                        textX + 1,
                        drawY + drawH - contentInset,
                        context.applyAlpha(RiptideTheme.recolor(-4627, RiptideTheme.Channel.TEXT))
                     );
               }
            }
         } else if (this.multiline) {
            List<CompactTextInput.WrappedLine> wrappedLines = this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int lineHeight = fontHeight + lineSpacing;
            int visibleLineCount = Math.max(1, Math.max(1, drawH - context.theme().scale(6)) / lineHeight);
            this.ensureWrapViewLine(visibleLineCount, wrappedLines);
            int selectionStart = Math.min(this.cursor, this.selectionAnchor);
            int selectionEnd = Math.max(this.cursor, this.selectionAnchor);
            int startLine = Math.min(this.wrapViewLine, Math.max(0, wrappedLines.size() - 1));
            int endLine = Math.min(wrappedLines.size(), startLine + visibleLineCount);
            context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);

            try {
               int lineY = drawY + contentInset + textYOffsetPx;

               for (int lineIndex = startLine; lineIndex < endLine; lineIndex++) {
                  CompactTextInput.WrappedLine line = wrappedLines.get(lineIndex);
                  if (selectionStart != selectionEnd) {
                     int visibleSelectionStart = Math.max(selectionStart, line.start());
                     int visibleSelectionEnd = Math.min(selectionEnd, line.renderEnd());
                     if (visibleSelectionStart < visibleSelectionEnd) {
                        int selectionX = textX
                           + this.renderSubstringWidth(context.textRenderer(), font, bodyColor, this.value, richMetrics, line.start(), visibleSelectionStart);
                        int selectionW = this.renderSubstringWidth(
                           context.textRenderer(), font, bodyColor, this.value, richMetrics, visibleSelectionStart, visibleSelectionEnd
                        );
                        context.drawContext()
                           .fill(
                              selectionX,
                              lineY - 1,
                              selectionX + selectionW,
                              lineY + fontHeight + lineSpacing,
                              context.applyAlpha(RiptideTheme.recolor(1728006730, RiptideTheme.Channel.ACCENT))
                           );
                     }
                  }

                  if (richMetrics != null) {
                     this.renderRichSubstring(
                        context.drawContext(), context.textRenderer(), font, bodyColor, this.value, richMetrics, line.start(), line.renderEnd(), textX, lineY
                     );
                  } else {
                     String lineComponent = this.value.substring(line.start(), line.renderEnd());
                     UiText.draw(context.drawContext(), context.textRenderer(), lineComponent, font, context.applyAlpha(bodyColor), textX, lineY, false);
                  }

                  lineY += lineHeight;
               }
            } finally {
               context.viewport().disableScissor(context.drawContext());
            }

            if (this.focused && this.blinkVisible()) {
               int cursorLine = this.lineIndexForCursor(wrappedLines, this.cursor);
               if (cursorLine >= startLine && cursorLine < endLine) {
                  CompactTextInput.WrappedLine linex = wrappedLines.get(cursorLine);
                  int visibleCursor = Math.min(this.cursor, this.lineContentEnd(linex));
                  int caretX = textX
                     + this.renderSubstringWidth(context.textRenderer(), font, bodyColor, this.value, richMetrics, linex.start(), visibleCursor);
                  int caretY = drawY + contentInset + textYOffsetPx + (cursorLine - startLine) * lineHeight;
                  context.drawContext()
                     .fill(
                        caretX,
                        caretY - 1,
                        caretX + 1,
                        caretY + fontHeight + lineSpacing,
                        context.applyAlpha(RiptideTheme.recolor(-4627, RiptideTheme.Channel.TEXT))
                     );
               }
            }

            this.drawMultilineScrollbar(context, drawX, drawY, drawW, drawH, lineHeight, visibleLineCount, wrappedLines.size());
         } else {
            int textY = UiSizing.alignTextY(drawY, drawH, fontHeight, textYOffsetPx);
            int visibleEnd = this.visibleEndIndex(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int selectionStart = Math.min(this.cursor, this.selectionAnchor);
            int selectionEnd = Math.max(this.cursor, this.selectionAnchor);
            if (selectionStart != selectionEnd) {
               int visibleSelectionStart = Math.max(selectionStart, this.viewOffset);
               int visibleSelectionEnd = Math.min(selectionEnd, visibleEnd);
               if (visibleSelectionStart < visibleSelectionEnd) {
                  int selectionX = textX
                     + this.renderSubstringWidth(context.textRenderer(), font, bodyColor, this.value, richMetrics, this.viewOffset, visibleSelectionStart);
                  int selectionW = this.renderSubstringWidth(
                     context.textRenderer(), font, bodyColor, this.value, richMetrics, visibleSelectionStart, visibleSelectionEnd
                  );
                  context.drawContext()
                     .fill(
                        selectionX,
                        drawY + 2,
                        selectionX + selectionW,
                        drawY + drawH - 2,
                        context.applyAlpha(RiptideTheme.recolor(1728006730, RiptideTheme.Channel.ACCENT))
                     );
               }
            }

            if (richMetrics != null) {
               context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);

               try {
                  this.renderRichSubstring(
                     context.drawContext(), context.textRenderer(), font, bodyColor, this.value, richMetrics, this.viewOffset, visibleEnd, textX, textY
                  );
               } finally {
                  context.viewport().disableScissor(context.drawContext());
               }
            } else {
               context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);

               try {
                  UiText.draw(
                     context.drawContext(),
                     context.textRenderer(),
                     this.value.substring(this.viewOffset, visibleEnd),
                     font,
                     context.applyAlpha(bodyColor),
                     textX,
                     textY,
                     false
                  );
               } finally {
                  context.viewport().disableScissor(context.drawContext());
               }
            }

            if (this.focused && this.blinkVisible()) {
               int caretX = textX + this.renderSubstringWidth(context.textRenderer(), font, bodyColor, this.value, richMetrics, this.viewOffset, this.cursor);
               context.drawContext()
                  .fill(
                     caretX,
                     drawY + contentInset,
                     caretX + 1,
                     drawY + drawH - contentInset,
                     context.applyAlpha(RiptideTheme.recolor(-4627, RiptideTheme.Channel.TEXT))
                  );
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
      if (button != 0 || !this.contains(mouseX, mouseY)) {
         return false;
      } else if (this.multiline && this.tryStartMultilineScrollbarDrag(context, mouseX, mouseY)) {
         this.setFocused(true);
         return true;
      } else if (!this.editable) {
         return false;
      } else {
         this.setFocused(true);
         this.draggingSelection = true;
         int innerW = this.contentWidth(Math.round(this.width), context.theme().scale(this.horizontalPadding), context.theme().scale(2));
         Identifier font = this.resolvedFont(context);
         int bodyColor = context.theme().color(this.textTone);
         CompactTextInput.RichMetrics richMetrics = this.buildRichMetrics(this.value);
         int clickCursor;
         if (this.multiline) {
            List<CompactTextInput.WrappedLine> wrappedLines = this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int lineHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(1);
            int visibleLineCount = Math.max(1, Math.max(1, Math.round(this.height) - context.theme().scale(6)) / lineHeight);
            this.ensureWrapViewLine(visibleLineCount, wrappedLines);
            clickCursor = this.cursorFromMousePosition(
               context.textRenderer(),
               font,
               bodyColor,
               mouseX - (this.x + context.theme().scale(this.horizontalPadding)),
               mouseY - this.multilineTextTopOffset(context),
               lineHeight,
               wrappedLines,
               richMetrics
            );
         } else {
            this.ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            clickCursor = this.cursorFromMouseX(
               context.textRenderer(), font, bodyColor, mouseX - (this.x + context.theme().scale(this.horizontalPadding)), richMetrics
            );
         }

         int clickCount = this.updateClickCount(mouseX, mouseY);
         if (clickCount >= 3) {
            this.selectLineAt(clickCursor);
         } else if (clickCount == 2) {
            this.selectWordAt(clickCursor);
         } else {
            this.cursor = clickCursor;
            this.selectionAnchor = clickCursor;
         }

         this.manualWrapScroll = false;
         this.restartBlink();
         return true;
      }
   }

   @Override
   public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
      if (button == 0 && this.draggingMultilineScrollbar) {
         this.draggingMultilineScrollbar = false;
         this.multilineScrollbarGrabOffset = 0;
         return true;
      } else if (button == 0 && this.draggingSelection) {
         this.draggingSelection = false;
         return this.focused;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
      if (button == 0 && this.draggingMultilineScrollbar) {
         this.dragMultilineScrollbar(context, mouseY);
         return true;
      } else if (this.editable && this.focused && this.draggingSelection && button == 0) {
         Identifier font = this.resolvedFont(context);
         int bodyColor = context.theme().color(this.textTone);
         int innerW = this.contentWidth(Math.round(this.width), context.theme().scale(this.horizontalPadding), context.theme().scale(2));
         CompactTextInput.RichMetrics richMetrics = this.buildRichMetrics(this.value);
         if (this.multiline) {
            List<CompactTextInput.WrappedLine> wrappedLines = this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int lineHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(1);
            int visibleLineCount = Math.max(1, Math.max(1, Math.round(this.height) - context.theme().scale(6)) / lineHeight);
            this.ensureWrapViewLine(visibleLineCount, wrappedLines);
            this.cursor = this.cursorFromMousePosition(
               context.textRenderer(),
               font,
               bodyColor,
               mouseX - (this.x + context.theme().scale(this.horizontalPadding)),
               mouseY - this.multilineTextTopOffset(context),
               lineHeight,
               wrappedLines,
               richMetrics
            );
         } else {
            this.ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            this.cursor = this.cursorFromMouseX(
               context.textRenderer(), font, bodyColor, mouseX - (this.x + context.theme().scale(this.horizontalPadding)), richMetrics
            );
         }

         this.manualWrapScroll = false;
         this.restartBlink();
         return true;
      } else {
         return false;
      }
   }

   private void drawMultilineScrollbar(
      DirectRenderContext context, int drawX, int drawY, int drawW, int drawH, int lineHeight, int visibleLineCount, int wrappedLineCount
   ) {
      int maxStart = Math.max(0, wrappedLineCount - visibleLineCount);
      if (maxStart > 0) {
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            Math.max(lineHeight, wrappedLineCount * lineHeight),
            Math.max(lineHeight, visibleLineCount * lineHeight),
            drawX + drawW - 5,
            drawY + 2,
            3,
            Math.max(1, drawH - 4),
            this.wrapViewLine * lineHeight
         );
         CompactScrollbar.draw(context.drawContext(), metrics, metrics.contains(context.mouseX(), context.mouseY()), this.draggingMultilineScrollbar);
      }
   }

   private boolean tryStartMultilineScrollbarDrag(DirectRenderContext context, float mouseX, float mouseY) {
      CompactTextInput.MultilineScrollInfo info = this.multilineScrollInfo(context);
      if (info != null && info.metrics().hasScroll() && info.metrics().contains(mouseX, mouseY)) {
         this.draggingMultilineScrollbar = true;
         this.multilineScrollbarGrabOffset = info.metrics().overThumb(mouseX, mouseY)
            ? Math.max(0, Math.round(mouseY) - info.metrics().thumbY())
            : info.metrics().thumbHeight() / 2;
         this.dragMultilineScrollbar(context, mouseY);
         return true;
      } else {
         return false;
      }
   }

   private void dragMultilineScrollbar(DirectRenderContext context, float mouseY) {
      CompactTextInput.MultilineScrollInfo info = this.multilineScrollInfo(context);
      if (info != null && info.metrics().hasScroll()) {
         int pixels = CompactScrollbar.scrollFromThumb(info.metrics(), mouseY, this.multilineScrollbarGrabOffset);
         this.wrapViewLine = Math.max(0, Math.min(info.maxStart(), Math.round((float)pixels / Math.max(1, info.lineHeight()))));
         this.manualWrapScroll = true;
      }
   }

   private CompactTextInput.MultilineScrollInfo multilineScrollInfo(DirectRenderContext context) {
      if (!this.multiline) {
         return null;
      } else {
         Identifier font = this.resolvedFont(context);
         int bodyColor = context.theme().color(this.textTone);
         int innerW = this.contentWidth(Math.round(this.width), context.theme().scale(this.horizontalPadding), context.theme().scale(2));
         CompactTextInput.RichMetrics richMetrics = this.buildRichMetrics(this.value);
         List<CompactTextInput.WrappedLine> wrappedLines = this.value.isEmpty()
            ? this.wrapPlainAsLines(context.textRenderer(), font, bodyColor, this.placeholder, innerW)
            : this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
         int lineHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(1);
         int visibleLineCount = Math.max(1, Math.max(1, Math.round(this.height) - context.theme().scale(6)) / lineHeight);
         int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            Math.max(lineHeight, wrappedLines.size() * lineHeight),
            Math.max(lineHeight, visibleLineCount * lineHeight),
            Math.round(this.x + this.width) - 5,
            Math.round(this.y) + 2,
            3,
            Math.max(1, Math.round(this.height) - 4),
            this.wrapViewLine * lineHeight
         );
         return new CompactTextInput.MultilineScrollInfo(metrics, lineHeight, maxStart);
      }
   }

   private List<CompactTextInput.WrappedLine> wrapPlainAsLines(Font renderer, Identifier font, int color, String text, int maxWidth) {
      String source = text != null && !text.isEmpty() ? text : " ";
      List<CompactTextInput.WrappedLine> lines = new ArrayList<>();
      int offset = 0;

      for (String line : this.wrapPlainText(renderer, font, color, source, maxWidth)) {
         int end = Math.min(source.length(), offset + line.length());
         lines.add(new CompactTextInput.WrappedLine(offset, end, end));
         offset = Math.min(source.length(), end + 1);
      }

      if (lines.isEmpty()) {
         lines.add(new CompactTextInput.WrappedLine(0, 0, 0));
      }

      return lines;
   }

   @Override
   public boolean mouseScrolled(DirectRenderContext context, float mouseX, float mouseY, float amount) {
      if (this.multiline && this.contains(mouseX, mouseY)) {
         Identifier font = this.resolvedFont(context);
         int bodyColor = context.theme().color(this.textTone);
         int innerW = this.contentWidth(Math.round(this.width), context.theme().scale(this.horizontalPadding), context.theme().scale(2));
         CompactTextInput.RichMetrics richMetrics = this.buildRichMetrics(this.value);
         List<CompactTextInput.WrappedLine> wrappedLines = this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
         int lineHeight = context.theme().fontHeight(this.textTone) + context.theme().scale(1);
         int visibleLineCount = Math.max(1, Math.max(1, Math.round(this.height) - context.theme().scale(6)) / lineHeight);
         int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
         if (maxStart <= 0) {
            return false;
         } else {
            int step = Math.max(1, Math.round(Math.abs(amount) * 3.0F));
            int next = this.wrapViewLine + (amount < 0.0F ? step : -step);
            next = Math.max(0, Math.min(maxStart, next));
            if (next == this.wrapViewLine) {
               return false;
            } else {
               this.wrapViewLine = next;
               this.manualWrapScroll = true;
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(DirectRenderContext context, int keyCode, int scanCode, int modifiers) {
      if (this.focused && this.editable) {
         boolean ctrl = (modifiers & 10) != 0;
         boolean shift = (modifiers & 1) != 0;
         switch (keyCode) {
            case 65:
               if (ctrl) {
                  this.selectionAnchor = 0;
                  this.cursor = this.value.length();
                  this.manualWrapScroll = false;
                  this.restartBlink();
                  return true;
               }
               break;
            case 67:
               if (ctrl) {
                  this.copySelection();
                  return true;
               }
               break;
            case 86:
               if (ctrl) {
                  this.pasteClipboard();
                  return true;
               }
               break;
            case 88:
               if (ctrl) {
                  this.copySelection();
                  this.deleteSelection();
                  return true;
               }
               break;
            case 256:
               this.setFocused(false);
               return true;
            case 257:
            case 335:
               if (!this.multiline || this.submitOnEnter && !shift) {
                  if (this.onSubmit != null) {
                     this.onSubmit.accept(this.value);
                  }

                  return true;
               }

               this.replaceSelection("\n");
               return true;
            case 259:
               if (this.deleteSelection()) {
                  return true;
               }

               if (this.cursor > 0) {
                  int from = ctrl ? this.previousWordBoundary(this.cursor) : Utf16TextMetrics.previousBoundary(this.value, this.cursor);
                  this.replaceRange(from, this.cursor, "");
               }

               return true;
            case 261:
               if (this.deleteSelection()) {
                  return true;
               }

               if (this.cursor < this.value.length()) {
                  int to = ctrl ? this.nextWordBoundary(this.cursor) : Utf16TextMetrics.nextBoundary(this.value, this.cursor);
                  this.replaceRange(this.cursor, to, "");
               }

               return true;
            case 262:
               if (ctrl) {
                  this.setCursor(this.nextWordBoundary(this.cursor), shift);
               } else {
                  this.moveCursor(1, shift);
               }

               return true;
            case 263:
               if (ctrl) {
                  this.setCursor(this.previousWordBoundary(this.cursor), shift);
               } else {
                  this.moveCursor(-1, shift);
               }

               return true;
            case 264:
               if (!this.multiline && this.historyNavigationEnabled) {
                  this.navigateHistory(1);
                  return true;
               }

               if (this.multiline) {
                  this.moveCursorVertically(context, 1, shift);
                  return true;
               }
               break;
            case 265:
               if (!this.multiline && this.historyNavigationEnabled) {
                  this.navigateHistory(-1);
                  return true;
               }

               if (this.multiline) {
                  this.moveCursorVertically(context, -1, shift);
                  return true;
               }
               break;
            case 268:
               if (!ctrl && this.multiline) {
                  this.setCursor(this.lineStart(this.cursor), shift);
               } else {
                  this.setCursor(0, shift);
               }

               return true;
            case 269:
               if (!ctrl && this.multiline) {
                  this.setCursor(this.lineEnd(this.cursor), shift);
               } else {
                  this.setCursor(this.value.length(), shift);
               }

               return true;
         }

         return false;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(DirectRenderContext context, char chr, int modifiers) {
      if (this.focused && this.editable && this.isAllowedCharacter(chr)) {
         this.replaceSelection(Character.toString(chr));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean isFocused() {
      return this.focused;
   }

   @Override
   public void setFocused(boolean focused) {
      boolean nextFocused = focused && this.editable;
      if (!nextFocused || this.focused != nextFocused) {
         this.draggingSelection = false;
      }

      this.focused = nextFocused;
      if (nextFocused) {
         CompactTextInput previous = focusedInput;
         if (previous != null && previous != this) {
            previous.setFocused(false);
         }

         focusedInput = this;
      } else if (focusedInput == this) {
         focusedInput = null;
      }

      this.restartBlink();
   }

   public static boolean anyFocused() {
      CompactTextInput input = focusedInput;
      return input != null && input.isFocused();
   }

   public static void clearFocusedInput() {
      CompactTextInput input = focusedInput;
      if (input != null) {
         input.setFocused(false);
      }

      focusedInput = null;
   }

   public CompactTextInput setSelectionEnd(int selectionEnd) {
      this.cursor = Math.max(0, Math.min(this.value.length(), selectionEnd));
      this.manualWrapScroll = false;
      this.restartBlink();
      return this;
   }

   public CompactTextInput moveCursorToEnd() {
      this.setCursor(this.value.length(), false);
      return this;
   }

   public int selectionStart() {
      return Math.min(this.cursor, this.selectionAnchor);
   }

   public int selectionEnd() {
      return Math.max(this.cursor, this.selectionAnchor);
   }

   public String selectedText() {
      return this.value.substring(this.selectionStart(), this.selectionEnd());
   }

   public CompactTextInput insertText(String text) {
      this.replaceSelection(text == null ? "" : text);
      return this;
   }

   private Identifier resolvedFont(DirectRenderContext context) {
      return this.fontId != null ? this.fontId : context.theme().fontFor(UiTone.BODY);
   }

   private boolean blinkVisible() {
      return (System.currentTimeMillis() - this.blinkStartMs) / 530L % 2L == 0L;
   }

   private void restartBlink() {
      this.blinkStartMs = System.currentTimeMillis();
   }

   private void ensureSelectionOrder() {
      this.cursor = Utf16TextMetrics.floorBoundary(this.value, this.cursor);
      this.selectionAnchor = Utf16TextMetrics.floorBoundary(this.value, this.selectionAnchor);
   }

   private int visibleEndIndex(Font renderer, Identifier font, int color, int availableWidth, CompactTextInput.RichMetrics richMetrics) {
      this.ensureValueMeasurements(renderer, font, color, richMetrics);
      int start = Utf16TextMetrics.floorBoundary(this.value, this.viewOffset);
      int target = this.cumulativeWidths[start] + Math.max(0, availableWidth);
      return Utf16TextMetrics.floorBoundary(this.value, upperBound(this.cumulativeWidths, start, this.value.length(), target));
   }

   private void ensureViewOffset(Font renderer, Identifier font, int color, int availableWidth, CompactTextInput.RichMetrics richMetrics) {
      this.ensureSelectionOrder();
      this.ensureValueMeasurements(renderer, font, color, richMetrics);
      int safeCursor = Utf16TextMetrics.floorBoundary(this.value, this.cursor);
      int target = this.cumulativeWidths[safeCursor] - Math.max(0, availableWidth);
      this.viewOffset = Utf16TextMetrics.floorBoundary(this.value, lowerBound(this.cumulativeWidths, 0, safeCursor, target));
      if (this.viewOffset == safeCursor && safeCursor > 0) {
         this.viewOffset = Utf16TextMetrics.previousBoundary(this.value, safeCursor);
      }
   }

   private int cursorFromMouseX(Font renderer, Identifier font, int color, float mouseInnerX, CompactTextInput.RichMetrics richMetrics) {
      this.ensureValueMeasurements(renderer, font, color, richMetrics);
      int start = Utf16TextMetrics.floorBoundary(this.value, this.viewOffset);
      int target = this.cumulativeWidths[start] + Math.max(0, Math.round(mouseInnerX));
      int candidate = nearestIndex(this.cumulativeWidths, start, this.value.length(), target);
      return Utf16TextMetrics.nearestBoundary(this.value, this.cumulativeWidths, candidate, target);
   }

   private List<String> wrapPlainText(Font renderer, Identifier font, int color, String text, int availableWidth) {
      if (text != null && !text.isEmpty()) {
         int safeWidth = Math.max(1, availableWidth);
         int reloadGeneration = UiText.reloadGeneration();
         if (Objects.equals(text, this.placeholder)
            && Objects.equals(this.cachedPlaceholderText, text)
            && this.cachedPlaceholderRenderer == renderer
            && Objects.equals(this.cachedPlaceholderFont, font)
            && this.cachedPlaceholderReloadGeneration == reloadGeneration
            && this.cachedPlaceholderWidth == safeWidth) {
            return this.cachedPlaceholderLines;
         } else {
            List<String> wrapped = new ArrayList<>();

            for (CompactTextInput.WrappedLine line : this.wrapLinesForText(renderer, font, color, text, safeWidth, null)) {
               wrapped.add(text.substring(line.start(), line.renderEnd()));
            }

            List<String> result = wrapped.isEmpty() ? List.of("") : List.copyOf(wrapped);
            if (Objects.equals(text, this.placeholder)) {
               this.cachedPlaceholderText = text;
               this.cachedPlaceholderRenderer = renderer;
               this.cachedPlaceholderFont = font;
               this.cachedPlaceholderReloadGeneration = reloadGeneration;
               this.cachedPlaceholderWidth = safeWidth;
               this.cachedPlaceholderLines = result;
            }

            return result;
         }
      } else {
         return List.of("");
      }
   }

   private CompactTextInput.RichMetrics buildRichMetrics(String sourceValue) {
      if (this.displayTextProvider != null && sourceValue != null && !sourceValue.isEmpty()) {
         long providerRevision = this.displayTextProviderRevision();
         if (this.cachedRichComputed
            && this.cachedRichProvider == this.displayTextProvider
            && this.cachedRichProviderRevision == providerRevision
            && Objects.equals(this.cachedRichValue, sourceValue)) {
            return this.cachedRichMetrics;
         } else {
            Component richDisplay = this.displayTextProvider.apply(sourceValue);
            if (richDisplay == null) {
               this.cacheRichMetrics(sourceValue, providerRevision, null);
               return null;
            } else {
               String richComponent = richDisplay.getString();
               if (!Objects.equals(richComponent, sourceValue)) {
                  this.cacheRichMetrics(sourceValue, providerRevision, null);
                  return null;
               } else {
                  List<Style> rawStyles = new ArrayList<>(richComponent.length());
                  richDisplay.visit((style, part) -> {
                     if (part != null && !part.isEmpty()) {
                        Style safeStyle = style == null ? Style.EMPTY : style;

                        for (int i = 0; i < part.length(); i++) {
                           rawStyles.add(safeStyle);
                        }
                     }

                     return Optional.empty();
                  }, Style.EMPTY);
                  List<Style> charStyles = new ArrayList<>(rawStyles);
                  if (charStyles.isEmpty()) {
                     this.cacheRichMetrics(sourceValue, providerRevision, null);
                     return null;
                  } else {
                     while (charStyles.size() < richComponent.length()) {
                        charStyles.add(Style.EMPTY);
                     }

                     if (charStyles.size() > richComponent.length()) {
                        charStyles = new ArrayList<>(charStyles.subList(0, richComponent.length()));
                     }

                     CompactTextInput.RichMetrics metrics = new CompactTextInput.RichMetrics(richComponent, charStyles);
                     this.cacheRichMetrics(sourceValue, providerRevision, metrics);
                     return metrics;
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   private long displayTextProviderRevision() {
      if (this.displayTextRevisionProvider == null) {
         return 0L;
      } else {
         try {
            return this.displayTextRevisionProvider.getAsLong();
         } catch (RuntimeException var2) {
            return 0L;
         }
      }
   }

   private void cacheRichMetrics(String sourceValue, long providerRevision, CompactTextInput.RichMetrics metrics) {
      this.cachedRichValue = sourceValue == null ? "" : sourceValue;
      this.cachedRichProvider = this.displayTextProvider;
      this.cachedRichProviderRevision = providerRevision;
      this.cachedRichMetrics = metrics;
      this.cachedRichComputed = true;
   }

   private void invalidateRichMetrics() {
      this.cachedRichValue = "";
      this.cachedRichProvider = null;
      this.cachedRichProviderRevision = Long.MIN_VALUE;
      this.cachedRichMetrics = null;
      this.cachedRichComputed = false;
   }

   private void invalidateTextCaches() {
      this.textRevision++;
      this.invalidateRichMetrics();
      this.measuredTextRevision = Long.MIN_VALUE;
      this.invalidateWrappedLines();
   }

   private void invalidateWrappedLines() {
      this.wrappedTextRevision = Long.MIN_VALUE;
      this.wrappedRichMetrics = null;
      this.cachedWrappedLines = List.of();
   }

   private void ensureValueMeasurements(Font renderer, Identifier font, int color, CompactTextInput.RichMetrics richMetrics) {
      int reloadGeneration = UiText.reloadGeneration();
      if (this.measuredTextRevision != this.textRevision
         || this.measuredStyleRevision != this.styleRevision
         || this.measuredRenderer != renderer
         || !Objects.equals(this.measuredFont, font)
         || this.measuredReloadGeneration != reloadGeneration
         || this.measuredColor != color
         || this.measuredRichMetrics != richMetrics
         || this.cumulativeWidths.length != this.value.length() + 1) {
         this.cumulativeWidths = Utf16TextMetrics.cumulativeWidths(this.value, (text, start, end) -> {
            String glyph = text.substring(start, end);
            if (richMetrics == null) {
               return renderer.width(glyph);
            } else {
               Style style = richMetrics.styles().get(Math.min(start, richMetrics.styles().size() - 1));
               return renderer.width(Component.literal(glyph).setStyle(style == null ? Style.EMPTY : style));
            }
         });
         this.measuredTextRevision = this.textRevision;
         this.measuredStyleRevision = this.styleRevision;
         this.measuredRenderer = renderer;
         this.measuredFont = font;
         this.measuredReloadGeneration = reloadGeneration;
         this.measuredColor = color;
         this.measuredRichMetrics = richMetrics;
      }
   }

   private Component buildRichSubstringText(String sourceText, CompactTextInput.RichMetrics richMetrics, int start, int end) {
      int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
      int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
      if (richMetrics != null && safeStart < safeEnd) {
         MutableComponent out = Component.empty();
         StringBuilder segment = new StringBuilder();
         Style currentStyle = null;

         for (int i = safeStart; i < safeEnd; i++) {
            Style style = richMetrics.styles().get(Math.min(i, richMetrics.styles().size() - 1));
            if (currentStyle == null) {
               currentStyle = style;
            } else if (!currentStyle.equals(style)) {
               out.append(Component.literal(segment.toString()).setStyle(currentStyle));
               segment.setLength(0);
               currentStyle = style;
            }

            segment.append(sourceText.charAt(i));
         }

         if (!segment.isEmpty()) {
            out.append(Component.literal(segment.toString()).setStyle(currentStyle == null ? Style.EMPTY : currentStyle));
         }

         return out;
      } else {
         return Component.literal(sourceText.substring(safeStart, safeEnd));
      }
   }

   private int renderSubstringWidth(Font renderer, Identifier font, int color, String sourceText, CompactTextInput.RichMetrics richMetrics, int start, int end) {
      int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
      int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
      if (safeStart >= safeEnd) {
         return 0;
      } else if (Objects.equals(sourceText, this.value)) {
         this.ensureValueMeasurements(renderer, font, color, richMetrics);
         return this.cumulativeWidths[safeEnd] - this.cumulativeWidths[safeStart];
      } else {
         return richMetrics == null
            ? this.substringWidth(renderer, font, color, sourceText.substring(safeStart, safeEnd))
            : this.richSubstringWidth(renderer, font, color, sourceText, richMetrics, safeStart, safeEnd);
      }
   }

   private int richSubstringWidth(
      Font renderer, Identifier font, int fallbackColor, String sourceText, CompactTextInput.RichMetrics richMetrics, int start, int end
   ) {
      int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
      int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
      return renderer.width(this.buildRichSubstringText(sourceText, richMetrics, safeStart, safeEnd));
   }

   private void renderRichSubstring(
      GuiGraphicsExtractor context,
      Font renderer,
      Identifier font,
      int fallbackColor,
      String sourceText,
      CompactTextInput.RichMetrics richMetrics,
      int start,
      int end,
      int x,
      int y
   ) {
      int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
      int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
      if (safeStart < safeEnd) {
         context.text(renderer, this.buildRichSubstringText(sourceText, richMetrics, safeStart, safeEnd), x, y, fallbackColor, false);
      }
   }

   private List<CompactTextInput.WrappedLine> wrapValueLines(
      Font renderer, Identifier font, int color, int availableWidth, CompactTextInput.RichMetrics richMetrics
   ) {
      int safeWidth = Math.max(1, availableWidth);
      int reloadGeneration = UiText.reloadGeneration();
      if (this.wrappedTextRevision == this.textRevision
         && this.wrappedStyleRevision == this.styleRevision
         && this.wrappedRenderer == renderer
         && Objects.equals(this.wrappedFont, font)
         && this.wrappedReloadGeneration == reloadGeneration
         && this.wrappedWidth == safeWidth
         && this.wrappedMultiline == this.multiline
         && this.wrappedRichMetrics == richMetrics) {
         return this.cachedWrappedLines;
      } else {
         this.ensureValueMeasurements(renderer, font, color, richMetrics);
         List<CompactTextInput.WrappedLine> lines = this.wrapLinesForText(renderer, font, color, this.value, safeWidth, richMetrics);
         this.cachedWrappedLines = List.copyOf(lines);
         this.wrappedTextRevision = this.textRevision;
         this.wrappedStyleRevision = this.styleRevision;
         this.wrappedRenderer = renderer;
         this.wrappedFont = font;
         this.wrappedReloadGeneration = reloadGeneration;
         this.wrappedWidth = safeWidth;
         this.wrappedMultiline = this.multiline;
         this.wrappedRichMetrics = richMetrics;
         return this.cachedWrappedLines;
      }
   }

   private List<CompactTextInput.WrappedLine> wrapLinesForText(
      Font renderer, Identifier font, int color, String text, int availableWidth, CompactTextInput.RichMetrics richMetrics
   ) {
      String safeComponent = text == null ? "" : text;
      List<CompactTextInput.WrappedLine> lines = new ArrayList<>();

      for (TextWrapLayout.Line line : TextWrapLayout.layout(
         safeComponent, availableWidth, (start, end) -> this.renderSubstringWidth(renderer, font, color, safeComponent, richMetrics, start, end)
      )) {
         lines.add(new CompactTextInput.WrappedLine(line.start(), line.end(), line.renderEnd()));
      }

      return lines;
   }

   private void ensureWrapViewLine(int visibleLineCount, List<CompactTextInput.WrappedLine> wrappedLines) {
      if (wrappedLines.isEmpty()) {
         this.wrapViewLine = 0;
      } else {
         int cursorLine = this.lineIndexForCursor(wrappedLines, this.cursor);
         int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
         if (this.manualWrapScroll) {
            this.wrapViewLine = Math.max(0, Math.min(this.wrapViewLine, maxStart));
         } else {
            if (cursorLine < this.wrapViewLine) {
               this.wrapViewLine = cursorLine;
            }

            if (cursorLine >= this.wrapViewLine + visibleLineCount) {
               this.wrapViewLine = cursorLine - visibleLineCount + 1;
            }

            this.wrapViewLine = Math.max(0, Math.min(this.wrapViewLine, maxStart));
         }
      }
   }

   private int lineIndexForCursor(List<CompactTextInput.WrappedLine> wrappedLines, int index) {
      if (wrappedLines.isEmpty()) {
         return 0;
      } else {
         int clamped = Math.max(0, Math.min(index, this.value.length()));

         for (int i = 0; i < wrappedLines.size(); i++) {
            CompactTextInput.WrappedLine line = wrappedLines.get(i);
            if (clamped < line.end()) {
               return i;
            }

            if (clamped == line.end()) {
               if (i == wrappedLines.size() - 1) {
                  return i;
               }

               if (clamped == line.start()) {
                  return i;
               }
            }
         }

         return wrappedLines.size() - 1;
      }
   }

   private float multilineTextTopOffset(DirectRenderContext context) {
      return this.y + context.theme().scale(3) + context.theme().fieldTextNudge() + this.textYOffset - 1.0F;
   }

   private int cursorFromMousePosition(
      Font renderer,
      Identifier font,
      int color,
      float mouseInnerX,
      float mouseInnerY,
      int lineHeight,
      List<CompactTextInput.WrappedLine> wrappedLines,
      CompactTextInput.RichMetrics richMetrics
   ) {
      if (wrappedLines.isEmpty()) {
         return 0;
      } else {
         int lineIndex = this.wrapViewLine + Math.max(0, (int)Math.floor((double)mouseInnerY / Math.max(1, lineHeight)));
         lineIndex = Math.max(0, Math.min(lineIndex, wrappedLines.size() - 1));
         CompactTextInput.WrappedLine line = wrappedLines.get(lineIndex);
         return this.cursorFromMouseXForLine(renderer, font, color, mouseInnerX, line, richMetrics);
      }
   }

   private int cursorFromMouseXForLine(
      Font renderer, Identifier font, int color, float mouseInnerX, CompactTextInput.WrappedLine line, CompactTextInput.RichMetrics richMetrics
   ) {
      this.ensureValueMeasurements(renderer, font, color, richMetrics);
      int contentEnd = this.lineContentEnd(line);
      int start = Math.max(0, Math.min(line.start(), this.value.length()));
      int visibleEnd = Math.max(start, Math.min(contentEnd, this.value.length()));
      int target = this.cumulativeWidths[start] + Math.max(0, Math.round(mouseInnerX));
      int candidate = nearestIndex(this.cumulativeWidths, start, visibleEnd, target);
      int best = Utf16TextMetrics.nearestBoundary(this.value, this.cumulativeWidths, candidate, target);
      return Math.max(line.start(), Math.min(best, contentEnd));
   }

   private int lineContentEnd(CompactTextInput.WrappedLine line) {
      int end = line.end();
      if (end > line.start() && end <= this.value.length() && this.value.charAt(end - 1) == '\n') {
         end--;
      }

      return end;
   }

   private void moveCursorVertically(DirectRenderContext context, int direction, boolean keepSelection) {
      Identifier font = this.resolvedFont(context);
      int bodyColor = context.theme().color(this.textTone);
      int innerW = this.contentWidth(Math.round(this.width), context.theme().scale(this.horizontalPadding), context.theme().scale(2));
      CompactTextInput.RichMetrics richMetrics = this.buildRichMetrics(this.value);
      List<CompactTextInput.WrappedLine> wrappedLines = this.wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
      if (!wrappedLines.isEmpty()) {
         int currentLineIndex = this.lineIndexForCursor(wrappedLines, this.cursor);
         int targetLineIndex = Math.max(0, Math.min(wrappedLines.size() - 1, currentLineIndex + direction));
         if (targetLineIndex != currentLineIndex) {
            CompactTextInput.WrappedLine currentLine = wrappedLines.get(currentLineIndex);
            int visibleCursor = Math.min(this.cursor, this.lineContentEnd(currentLine));
            int cursorX = this.renderSubstringWidth(context.textRenderer(), font, bodyColor, this.value, richMetrics, currentLine.start(), visibleCursor);
            CompactTextInput.WrappedLine targetLine = wrappedLines.get(targetLineIndex);
            int targetCursor = this.cursorFromMouseXForLine(context.textRenderer(), font, bodyColor, cursorX, targetLine, richMetrics);
            this.setCursor(targetCursor, keepSelection);
         }
      }
   }

   private void moveCursor(int delta, boolean keepSelection) {
      int next = this.cursor;
      if (delta < 0) {
         for (int i = 0; i < -delta; i++) {
            next = Utf16TextMetrics.previousBoundary(this.value, next);
         }
      } else {
         for (int i = 0; i < delta; i++) {
            next = Utf16TextMetrics.nextBoundary(this.value, next);
         }
      }

      this.setCursor(next, keepSelection);
   }

   private int contentWidth(int totalWidth, int padding, int frameInset) {
      return Math.max(1, totalWidth - padding * 2 - frameInset - (this.multiline ? 5 : 0));
   }

   private int updateClickCount(float mouseX, float mouseY) {
      long now = System.currentTimeMillis();
      boolean closeEnough = !Float.isNaN(this.lastClickX) && Math.abs(mouseX - this.lastClickX) <= 4.0F && Math.abs(mouseY - this.lastClickY) <= 4.0F;
      if (now - this.lastClickMs <= 320L && closeEnough) {
         this.repeatedClickCount++;
      } else {
         this.repeatedClickCount = 1;
      }

      this.lastClickMs = now;
      this.lastClickX = mouseX;
      this.lastClickY = mouseY;
      return this.repeatedClickCount;
   }

   private void selectWordAt(int index) {
      int clamped = Math.max(0, Math.min(index, this.value.length()));
      if (this.value.isEmpty()) {
         this.cursor = this.selectionAnchor = 0;
      } else {
         int probe = clamped;
         if (clamped == this.value.length() && clamped > 0) {
            probe = clamped - 1;
         }

         if (probe < this.value.length() && !this.isWordChar(this.value.charAt(probe)) && probe > 0 && this.isWordChar(this.value.charAt(probe - 1))) {
            probe--;
         }

         int start = probe;
         int end;
         if (probe < this.value.length() && this.isWordChar(this.value.charAt(probe))) {
            while (start > 0 && this.isWordChar(this.value.charAt(start - 1))) {
               start--;
            }

            end = probe + 1;

            while (end < this.value.length() && this.isWordChar(this.value.charAt(end))) {
               end++;
            }
         } else {
            while (start > 0 && !this.isWordChar(this.value.charAt(start - 1)) && this.value.charAt(start - 1) != '\n') {
               start--;
            }

            end = Math.min(this.value.length(), probe + 1);

            while (end < this.value.length() && !this.isWordChar(this.value.charAt(end)) && this.value.charAt(end) != '\n') {
               end++;
            }
         }

         this.selectionAnchor = Utf16TextMetrics.floorBoundary(this.value, start);
         this.cursor = Utf16TextMetrics.ceilBoundary(this.value, Math.max(start, end));
      }
   }

   private void selectLineAt(int index) {
      int clamped = Math.max(0, Math.min(index, this.value.length()));
      this.selectionAnchor = Utf16TextMetrics.floorBoundary(this.value, this.lineStart(clamped));
      this.cursor = Utf16TextMetrics.floorBoundary(this.value, this.lineEnd(clamped));
      if (this.cursor < this.value.length() && this.value.charAt(this.cursor) == '\n') {
         this.cursor++;
      }
   }

   private int previousWordBoundary(int index) {
      int i = Math.max(0, Math.min(index, this.value.length()));

      while (i > 0 && Character.isWhitespace(this.value.charAt(i - 1))) {
         i--;
      }

      if (i > 0 && this.isWordChar(this.value.charAt(i - 1))) {
         while (i > 0 && this.isWordChar(this.value.charAt(i - 1))) {
            i--;
         }
      } else {
         while (i > 0 && !Character.isWhitespace(this.value.charAt(i - 1)) && !this.isWordChar(this.value.charAt(i - 1))) {
            i--;
         }
      }

      return i;
   }

   private int nextWordBoundary(int index) {
      int i = Math.max(0, Math.min(index, this.value.length()));

      while (i < this.value.length() && Character.isWhitespace(this.value.charAt(i))) {
         i++;
      }

      if (i < this.value.length() && this.isWordChar(this.value.charAt(i))) {
         while (i < this.value.length() && this.isWordChar(this.value.charAt(i))) {
            i++;
         }
      } else {
         while (i < this.value.length() && !Character.isWhitespace(this.value.charAt(i)) && !this.isWordChar(this.value.charAt(i))) {
            i++;
         }
      }

      return i;
   }

   private int lineStart(int index) {
      int i = Math.max(0, Math.min(index, this.value.length()));

      while (i > 0 && this.value.charAt(i - 1) != '\n') {
         i--;
      }

      return i;
   }

   private int lineEnd(int index) {
      int i = Math.max(0, Math.min(index, this.value.length()));

      while (i < this.value.length() && this.value.charAt(i) != '\n') {
         i++;
      }

      return i;
   }

   private boolean isWordChar(char chr) {
      return Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ':' || chr == '.';
   }

   private void navigateHistory(int direction) {
      List<String> history = this.historySnapshot();
      if (!history.isEmpty()) {
         if (this.historyIndex >= history.size()) {
            this.historyIndex = history.size() - 1;
         }

         if (direction < 0) {
            if (this.historyIndex == -1) {
               this.historyDraft = this.value;
               this.historyIndex = history.size() - 1;
            } else if (this.historyIndex > 0) {
               this.historyIndex--;
            }

            this.applyHistoryValue(history.get(this.historyIndex));
         } else {
            if (this.historyIndex == -1) {
               return;
            }

            if (this.historyIndex < history.size() - 1) {
               this.historyIndex++;
               this.applyHistoryValue(history.get(this.historyIndex));
            } else {
               this.historyIndex = -1;
               this.applyHistoryValue(this.historyDraft);
            }
         }
      }
   }

   private List<String> historySnapshot() {
      List<String> out = new ArrayList<>();
      if (this.historyProvider != null) {
         Collection<String> provided = this.historyProvider.get();
         if (provided != null) {
            for (String entry : provided) {
               this.appendHistorySnapshotEntry(out, entry);
            }
         }
      }

      for (String entry : this.historyEntries) {
         this.appendHistorySnapshotEntry(out, entry);
      }

      return out;
   }

   private void appendHistorySnapshotEntry(List<String> out, String entry) {
      String sanitized = this.sanitize(entry);
      if (!sanitized.isEmpty()) {
         if (out.isEmpty() || !sanitized.equals(out.get(out.size() - 1))) {
            out.add(sanitized);

            while (out.size() > 100) {
               out.remove(0);
            }
         }
      }
   }

   private void applyHistoryValue(String nextValue) {
      this.applyingHistoryValue = true;

      try {
         this.setText(nextValue);
      } finally {
         this.applyingHistoryValue = false;
      }

      this.setCursor(this.value.length(), false);
   }

   private void setCursor(int nextCursor, boolean keepSelection) {
      this.cursor = Utf16TextMetrics.floorBoundary(this.value, nextCursor);
      if (!keepSelection) {
         this.selectionAnchor = this.cursor;
      }

      this.manualWrapScroll = false;
      this.restartBlink();
   }

   private boolean hasSelection() {
      return this.cursor != this.selectionAnchor;
   }

   private void copySelection() {
      if (this.hasSelection()) {
         int start = Math.min(this.cursor, this.selectionAnchor);
         int end = Math.max(this.cursor, this.selectionAnchor);
         Minecraft.getInstance().keyboardHandler.setClipboard(this.value.substring(start, end));
      }
   }

   private void pasteClipboard() {
      String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
      if (clipboard != null && !clipboard.isEmpty()) {
         this.replaceSelection(this.sanitize(clipboard));
      }
   }

   private boolean deleteSelection() {
      if (!this.hasSelection()) {
         return false;
      } else {
         int start = Math.min(this.cursor, this.selectionAnchor);
         int end = Math.max(this.cursor, this.selectionAnchor);
         this.replaceRange(start, end, "");
         return true;
      }
   }

   private void replaceSelection(String replacement) {
      int start = Math.min(this.cursor, this.selectionAnchor);
      int end = Math.max(this.cursor, this.selectionAnchor);
      this.replaceRange(start, end, replacement);
   }

   private void replaceRange(int start, int end, String replacement) {
      int safeStart = Utf16TextMetrics.floorBoundary(this.value, start);
      int safeEnd = Math.max(safeStart, Utf16TextMetrics.ceilBoundary(this.value, end));
      String sanitizedReplacement = this.sanitize(replacement);
      String next = this.value.substring(0, safeStart) + sanitizedReplacement + this.value.substring(safeEnd);
      if (next.length() > this.maxLength) {
         next = Utf16TextMetrics.truncateAtBoundary(next, this.maxLength);
      }

      if (this.filter.test(next)) {
         this.value = next;
         this.invalidateTextCaches();
         this.cursor = Utf16TextMetrics.floorBoundary(next, Math.min(next.length(), safeStart + sanitizedReplacement.length()));
         this.selectionAnchor = this.cursor;
         this.manualWrapScroll = false;
         this.restartBlink();
         if (this.historyNavigationEnabled && !this.applyingHistoryValue) {
            this.historyIndex = -1;
            this.historyDraft = this.value;
         }

         if (this.onChange != null) {
            this.onChange.accept(this.value);
         }
      }
   }

   private String sanitize(String input) {
      if (input != null && !input.isEmpty()) {
         StringBuilder sb = new StringBuilder(input.length());
         int i = 0;

         while (i < input.length() && sb.length() < this.maxLength) {
            int codePoint = input.codePointAt(i);
            int units = Character.charCount(codePoint);
            i += units;
            if (this.isAllowedCharacter(codePoint) && sb.length() + units <= this.maxLength) {
               sb.appendCodePoint(codePoint);
            }
         }

         String next = sb.toString();
         return this.filter.test(next) ? next : this.value;
      } else {
         return "";
      }
   }

   private boolean isAllowedCharacter(int codePoint) {
      if (codePoint == 10) {
         return this.multiline;
      } else {
         return codePoint == 13 ? false : codePoint >= 32 && codePoint != 127;
      }
   }

   private int substringWidth(Font renderer, Identifier font, int color, String text) {
      return UiText.width(renderer, text, font, color);
   }

   private static int lowerBound(int[] widths, int from, int to, int target) {
      int low = Math.max(0, from);
      int high = Math.max(low, Math.min(to, widths.length - 1));

      while (low < high) {
         int middle = low + (high - low >>> 1);
         if (widths[middle] < target) {
            low = middle + 1;
         } else {
            high = middle;
         }
      }

      return low;
   }

   private static int upperBound(int[] widths, int from, int to, int target) {
      int low = Math.max(0, from);
      int high = Math.max(low, Math.min(to, widths.length - 1));
      int answer = low;

      while (low <= high) {
         int middle = low + (high - low >>> 1);
         if (widths[middle] <= target) {
            answer = middle;
            low = middle + 1;
         } else {
            high = middle - 1;
         }
      }

      return answer;
   }

   private static int nearestIndex(int[] widths, int from, int to, int target) {
      if (target <= widths[from]) {
         return from;
      } else if (target >= widths[to]) {
         return to;
      } else {
         int right = lowerBound(widths, from, to, target);
         if (right <= from) {
            return from;
         } else {
            int left = right - 1;
            return target - widths[left] < widths[right] - target ? left : right;
         }
      }
   }

   private record MultilineScrollInfo(CompactScrollbar.Metrics metrics, int lineHeight, int maxStart) {
   }

   private record RichMetrics(String text, List<Style> styles) {
   }

   private record WrappedLine(int start, int end, int renderEnd) {
   }
}
