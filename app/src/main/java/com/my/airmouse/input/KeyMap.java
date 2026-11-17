package com.my.airmouse.input;

/** Maps typed characters to {modifier, keyCode}. */
public final class KeyMap {

    private static final String SHIFTED = "!@#$%^&*()";
    private static final String PLAIN_SYMBOLS = "-=[]\\;'`,./";
    private static final int[] PLAIN_CODES = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
    private static final String SHIFT_SYMBOLS = "_+{}|:\"~<>?";

    private KeyMap() {}

    /** Returns {modifier, keyCode}, or null if the character has no mapping. */
    public static int[] lookup(char c) {
        if (c >= 'a' && c <= 'z') return new int[]{0, 0x04 + (c - 'a')};
        if (c >= 'A' && c <= 'Z') return new int[]{KeyCodes.MOD_SHIFT, 0x04 + (c - 'A')};
        if (c >= '1' && c <= '9') return new int[]{0, 0x1E + (c - '1')};
        if (c == '0') return new int[]{0, 0x27};
        if (c == ' ') return new int[]{0, KeyCodes.SPACE};
        if (c == '\n') return new int[]{0, KeyCodes.ENTER};
        if (c == '\t') return new int[]{0, KeyCodes.TAB};
        int i = SHIFTED.indexOf(c);
        if (i >= 0) return new int[]{KeyCodes.MOD_SHIFT, 0x1E + i};
        i = PLAIN_SYMBOLS.indexOf(c);
        if (i >= 0) return new int[]{0, PLAIN_CODES[i]};
        i = SHIFT_SYMBOLS.indexOf(c);
        if (i >= 0) return new int[]{KeyCodes.MOD_SHIFT, PLAIN_CODES[i]};
        return null;
    }
}
