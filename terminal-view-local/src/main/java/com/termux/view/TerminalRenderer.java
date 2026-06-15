package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        int visualRowsDrawn = 0;
        int row = topRow;
        while (visualRowsDrawn < mEmulator.mRows) {
            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();
            final int displayColumns = Math.max(1, measureVisibleColumns(line, charsUsedInLine));
            final int visualSegmentCount = Math.max(1, (displayColumns + columns - 1) / columns);

            for (int segment = 0; segment < visualSegmentCount && visualRowsDrawn < mEmulator.mRows; segment++) {
                heightOffset += mFontLineSpacing;
                drawRowSegment(mEmulator, canvas, palette, lineObject, line, charsUsedInLine, row,
                    segment * columns, (segment + 1) * columns, heightOffset, cursorCol, cursorRow,
                    cursorVisible, cursorShape, reverseVideo, selectionY1, selectionY2, selectionX1, selectionX2);
                visualRowsDrawn++;
            }
            row++;
        }
    }

    private void drawRowSegment(TerminalEmulator mEmulator, Canvas canvas, int[] palette, TerminalRow lineObject,
                                char[] line, int charsUsedInLine, int row, int segmentStartColumn,
                                int segmentEndColumn, float heightOffset, int cursorCol, int cursorRow,
                                boolean cursorVisible, int cursorShape, boolean reverseVideo,
                                int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final int columns = mEmulator.mColumns;
        final int cursorX = (row == cursorRow && cursorVisible && cursorCol >= segmentStartColumn && cursorCol < segmentEndColumn)
            ? cursorCol - segmentStartColumn
            : -1;
        int selx1 = -1, selx2 = -1;
        if (row >= selectionY1 && row <= selectionY2) {
            selx1 = (row == selectionY1) ? selectionX1 : 0;
            selx2 = (row == selectionY2) ? selectionX2 : Math.max(columns, segmentEndColumn);
        }

        long lastRunStyle = 0;
        boolean lastRunInsideCursor = false;
        boolean lastRunInsideSelection = false;
        int lastRunStartColumn = -1;
        int lastRunStartIndex = 0;
        int lastRunEndColumn = -1;
        int lastRunEndIndex = 0;
        boolean lastRunFontWidthMismatch = false;
        int currentCharIndex = 0;
        int sourceColumn = 0;
        float measuredWidthForRun = 0.f;

        while (currentCharIndex < charsUsedInLine && sourceColumn < segmentEndColumn) {
            final int codePointStartIndex = currentCharIndex;
            final char charAtIndex = line[currentCharIndex];
            final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex) && currentCharIndex + 1 < charsUsedInLine;
            final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
            final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
            final int codePointWcWidth = WcWidth.width(codePoint);
            int nextCharIndex = currentCharIndex + charsForCodePoint;
            while (nextCharIndex < charsUsedInLine && WcWidth.width(line, nextCharIndex) <= 0) {
                nextCharIndex += Character.isHighSurrogate(line[nextCharIndex]) && nextCharIndex + 1 < charsUsedInLine ? 2 : 1;
            }

            if (codePointWcWidth <= 0) {
                currentCharIndex = nextCharIndex;
                continue;
            }

            final int nextSourceColumn = sourceColumn + codePointWcWidth;
            if (nextSourceColumn <= segmentStartColumn) {
                sourceColumn = nextSourceColumn;
                currentCharIndex = nextCharIndex;
                continue;
            }
            if (sourceColumn < segmentStartColumn) {
                sourceColumn = nextSourceColumn;
                currentCharIndex = nextCharIndex;
                continue;
            }

            final int visualColumn = sourceColumn - segmentStartColumn;
            final boolean insideCursor = (cursorX == visualColumn || (codePointWcWidth == 2 && cursorX == visualColumn + 1));
            final boolean insideSelection = sourceColumn >= selx1 && sourceColumn <= selx2;
            final long style = getStyleSafely(lineObject, sourceColumn, columns);

            // Check if the measured text width for this code point is not the same as that expected by wcwidth().
            // This can happen with non-monospace fallback glyphs; scale the run to terminal cell width.
            final int charsForCluster = nextCharIndex - codePointStartIndex;
            final float measuredCodePointWidth = (codePoint < asciiMeasures.length && charsForCluster == 1)
                ? asciiMeasures[codePoint]
                : mTextPaint.measureText(line, codePointStartIndex, charsForCluster);
            final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

            if (lastRunStartColumn == -1) {
                lastRunStyle = style;
                lastRunInsideCursor = insideCursor;
                lastRunInsideSelection = insideSelection;
                lastRunStartColumn = visualColumn;
                lastRunStartIndex = codePointStartIndex;
                lastRunFontWidthMismatch = fontWidthMismatch;
            } else if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                drawRunIfNeeded(mEmulator, canvas, line, palette, heightOffset, lastRunStartColumn,
                    lastRunEndColumn - lastRunStartColumn, lastRunStartIndex, lastRunEndIndex - lastRunStartIndex,
                    measuredWidthForRun, lastRunInsideCursor, cursorShape, lastRunStyle,
                    reverseVideo || lastRunInsideSelection);
                measuredWidthForRun = 0.f;
                lastRunStyle = style;
                lastRunInsideCursor = insideCursor;
                lastRunInsideSelection = insideSelection;
                lastRunStartColumn = visualColumn;
                lastRunStartIndex = codePointStartIndex;
                lastRunFontWidthMismatch = fontWidthMismatch;
            }

            measuredWidthForRun += measuredCodePointWidth;
            lastRunEndColumn = visualColumn + codePointWcWidth;
            lastRunEndIndex = nextCharIndex;
            sourceColumn = nextSourceColumn;
            currentCharIndex = nextCharIndex;
        }

        if (lastRunStartColumn != -1) {
            drawRunIfNeeded(mEmulator, canvas, line, palette, heightOffset, lastRunStartColumn,
                lastRunEndColumn - lastRunStartColumn, lastRunStartIndex, lastRunEndIndex - lastRunStartIndex,
                measuredWidthForRun, lastRunInsideCursor, cursorShape, lastRunStyle,
                reverseVideo || lastRunInsideSelection);
        }
    }

    private void drawRunIfNeeded(TerminalEmulator mEmulator, Canvas canvas, char[] line, int[] palette,
                                 float heightOffset, int startColumn, int runWidthColumns,
                                 int startCharIndex, int runWidthChars, float measuredWidthForRun,
                                 boolean insideCursor, int cursorShape, long style, boolean reverseVideo) {
        if (runWidthColumns <= 0 || runWidthChars <= 0) return;
        int cursorColor = insideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
        boolean invertCursorTextColor = false;
        if (insideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
            invertCursorTextColor = true;
        }
        drawTextRun(canvas, line, palette, heightOffset, startColumn, runWidthColumns, startCharIndex, runWidthChars,
            measuredWidthForRun, cursorColor, cursorShape, style, reverseVideo || invertCursorTextColor);
    }

    private static long getStyleSafely(TerminalRow lineObject, int sourceColumn, int columns) {
        return lineObject.getStyle(Math.max(0, Math.min(sourceColumn, columns - 1)));
    }

    private static int measureVisibleColumns(char[] line, int charsUsedInLine) {
        int lastContentIndex = charsUsedInLine;
        while (lastContentIndex > 0 && line[lastContentIndex - 1] == ' ') {
            lastContentIndex--;
        }
        int columns = 0;
        int currentCharIndex = 0;
        while (currentCharIndex < lastContentIndex) {
            final char charAtIndex = line[currentCharIndex];
            final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex) && currentCharIndex + 1 < lastContentIndex;
            final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
            final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
            final int codePointWcWidth = WcWidth.width(codePoint);
            if (codePointWcWidth > 0) columns += codePointWcWidth;
            currentCharIndex += charsForCodePoint;
            while (currentCharIndex < lastContentIndex && WcWidth.width(line, currentCharIndex) <= 0) {
                currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) && currentCharIndex + 1 < lastContentIndex ? 2 : 1;
            }
        }
        return columns;
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }
            foreColor = ensureReadableForeground(foreColor, backColor);

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }

    private static int ensureReadableForeground(int foreground, int background) {
        final double minContrast = 2.8;
        if (contrastRatio(foreground, background) >= minContrast) return foreground;

        int adjusted = foreground;
        final boolean lightBackground = relativeLuminance(background) >= 0.5;
        for (int step = 1; step <= 8 && contrastRatio(adjusted, background) < minContrast; step++) {
            float amount = step / 20f;
            adjusted = blend(foreground, lightBackground ? 0xff000000 : 0xffffffff, amount);
        }
        return adjusted;
    }

    private static int blend(int color, int target, float amount) {
        int red = Math.round(channel(color, 16) + (channel(target, 16) - channel(color, 16)) * amount);
        int green = Math.round(channel(color, 8) + (channel(target, 8) - channel(color, 8)) * amount);
        int blue = Math.round(channel(color, 0) + (channel(target, 0) - channel(color, 0)) * amount);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first) + 0.05;
        double secondLuminance = relativeLuminance(second) + 0.05;
        return Math.max(firstLuminance, secondLuminance) / Math.min(firstLuminance, secondLuminance);
    }

    private static double relativeLuminance(int color) {
        double red = linearizedChannel(channel(color, 16));
        double green = linearizedChannel(channel(color, 8));
        double blue = linearizedChannel(channel(color, 0));
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearizedChannel(int value) {
        double normalized = value / 255.0;
        if (normalized <= 0.03928) return normalized / 12.92;
        return Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static int channel(int color, int shift) {
        return (color >> shift) & 0xff;
    }
}
