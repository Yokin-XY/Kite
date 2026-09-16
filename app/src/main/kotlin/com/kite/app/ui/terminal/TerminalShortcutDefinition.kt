package com.kite.app.ui.terminal

/**
 * 终端快捷面板的自定义组合键合同。
 *
 * 该模型只描述“用户选择的一组修饰键 + 一个主键”，不关心它在哪个页面渲染，
 * 也不把 Ctrl+R 等具体键位写死在 UI 或终端会话层。
 */
enum class TerminalShortcutModifier(val label: String) {
    CTRL("Ctrl"),
    ALT("Alt"),
    SHIFT("Shift"),
}

enum class TerminalShortcutKeyCategory {
    LETTER,
    DIGIT,
    SYMBOL,
    FUNCTION,
}

private val LETTER = TerminalShortcutKeyCategory.LETTER
private val DIGIT = TerminalShortcutKeyCategory.DIGIT
private val SYMBOL = TerminalShortcutKeyCategory.SYMBOL
private val FUNCTION = TerminalShortcutKeyCategory.FUNCTION
enum class TerminalShortcutKey(
    val category: TerminalShortcutKeyCategory,
    val label: String,
    val plainInput: String,
    val ctrlInput: String? = null,
    val shiftInput: String? = null,
) {
    A(LETTER, "A", "a", "\u0001", "A"),
    B(LETTER, "B", "b", "\u0002", "B"),
    C(LETTER, "C", "c", "\u0003", "C"),
    D(LETTER, "D", "d", "\u0004", "D"),
    E(LETTER, "E", "e", "\u0005", "E"),
    F(LETTER, "F", "f", "\u0006", "F"),
    G(LETTER, "G", "g", "\u0007", "G"),
    H(LETTER, "H", "h", "\u0008", "H"),
    I(LETTER, "I", "i", "\u0009", "I"),
    J(LETTER, "J", "j", "\u000a", "J"),
    K(LETTER, "K", "k", "\u000b", "K"),
    L(LETTER, "L", "l", "\u000c", "L"),
    M(LETTER, "M", "m", "\u000d", "M"),
    N(LETTER, "N", "n", "\u000e", "N"),
    O(LETTER, "O", "o", "\u000f", "O"),
    P(LETTER, "P", "p", "\u0010", "P"),
    Q(LETTER, "Q", "q", "\u0011", "Q"),
    R(LETTER, "R", "r", "\u0012", "R"),
    S(LETTER, "S", "s", "\u0013", "S"),
    T(LETTER, "T", "t", "\u0014", "T"),
    U(LETTER, "U", "u", "\u0015", "U"),
    V(LETTER, "V", "v", "\u0016", "V"),
    W(LETTER, "W", "w", "\u0017", "W"),
    X(LETTER, "X", "x", "\u0018", "X"),
    Y(LETTER, "Y", "y", "\u0019", "Y"),
    Z(LETTER, "Z", "z", "\u001a", "Z"),
    ZERO(DIGIT, "0", "0"),
    ONE(DIGIT, "1", "1"),
    TWO(DIGIT, "2", "2", "\u0000"),
    THREE(DIGIT, "3", "3", "\u001b"),
    FOUR(DIGIT, "4", "4", "\u001c"),
    FIVE(DIGIT, "5", "5", "\u001d"),
    SIX(DIGIT, "6", "6", "\u001e"),
    SEVEN(DIGIT, "7", "7", "\u001f"),
    EIGHT(DIGIT, "8", "8", "\u007f"),
    NINE(DIGIT, "9", "9"),
    SPACE(SYMBOL, "Space", " ", "\u0000"),
    MINUS(SYMBOL, "-", "-", null, "_"),
    EQUAL(SYMBOL, "=", "=", null, "+"),
    LEFT_BRACKET(SYMBOL, "[", "[", "\u001b", "{"),
    RIGHT_BRACKET(SYMBOL, "]", "]", "\u001d", "}"),
    BACKSLASH(SYMBOL, "\\", "\\", "\u001c", "|"),
    SEMICOLON(SYMBOL, ";", ";", null, ":"),
    APOSTROPHE(SYMBOL, "'", "'", null, "\""),
    COMMA(SYMBOL, ",", ",", null, "<"),
    PERIOD(SYMBOL, ".", ".", null, ">"),
    SLASH(SYMBOL, "/", "/", "\u001f", "?"),
    GRAVE(SYMBOL, "`", "`", null, "~"),
    QUESTION(SYMBOL, "?", "?", "\u007f"),
    ENTER(FUNCTION, "Enter", "\r"),
    TAB(FUNCTION, "Tab", "\t", null, "\u001b[Z"),
    ESCAPE(FUNCTION, "Esc", "\u001b"),
    BACKSPACE(FUNCTION, "Backspace", "\u007f"),
    UP(FUNCTION, "Up", "\u001b[A"),
    DOWN(FUNCTION, "Down", "\u001b[B"),
    RIGHT(FUNCTION, "Right", "\u001b[C"),
    LEFT(FUNCTION, "Left", "\u001b[D"),
    HOME(FUNCTION, "Home", "\u001b[H"),
    END(FUNCTION, "End", "\u001b[F"),
    PAGE_UP(FUNCTION, "Page Up", "\u001b[5~"),
    PAGE_DOWN(FUNCTION, "Page Down", "\u001b[6~"),
    INSERT(FUNCTION, "Insert", "\u001b[2~"),
    DELETE(FUNCTION, "Delete", "\u001b[3~"),
    F1(FUNCTION, "F1", "\u001bOP"),
    F2(FUNCTION, "F2", "\u001bOQ"),
    F3(FUNCTION, "F3", "\u001bOR"),
    F4(FUNCTION, "F4", "\u001bOS"),
    F5(FUNCTION, "F5", "\u001b[15~"),
    F6(FUNCTION, "F6", "\u001b[17~"),
    F7(FUNCTION, "F7", "\u001b[18~"),
    F8(FUNCTION, "F8", "\u001b[19~"),
    F9(FUNCTION, "F9", "\u001b[20~"),
    F10(FUNCTION, "F10", "\u001b[21~"),
    F11(FUNCTION, "F11", "\u001b[23~"),
    F12(FUNCTION, "F12", "\u001b[24~");
}

data class TerminalShortcutDefinition(
    val modifiers: Set<TerminalShortcutModifier> = emptySet(),
    val key: TerminalShortcutKey,
) {
    val label: String
        get() = if (modifiers.isEmpty()) {
            key.label
        } else {
            modifiers.sortedBy { it.ordinal }.joinToString("+") { it.label } + "+" + key.label
        }

    val id: String get() = label
}

object TerminalShortcutCodec {
    fun isSupported(definition: TerminalShortcutDefinition): Boolean =
        encode(definition) != null

    fun encode(definition: TerminalShortcutDefinition): String? {
        val modifiers = definition.modifiers
        val key = definition.key
        val ctrl = TerminalShortcutModifier.CTRL in modifiers
        val alt = TerminalShortcutModifier.ALT in modifiers
        val shift = TerminalShortcutModifier.SHIFT in modifiers
        if (modifiers.isEmpty()) return key.plainInput
        if (key.category == FUNCTION && modifiers != setOf(TerminalShortcutModifier.SHIFT)) return null

        val base = when {
            ctrl -> key.ctrlInput ?: return null
            shift -> key.shiftInput ?: key.plainInput
            else -> key.plainInput
        }
        return if (alt) ESC + base else base
    }

    private const val ESC = "\u001b"
}
