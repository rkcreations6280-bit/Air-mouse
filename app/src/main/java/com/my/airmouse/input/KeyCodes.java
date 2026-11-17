package com.my.airmouse.input;

import java.util.ArrayList;
import java.util.List;

/** USB HID keyboard usage codes and modifier bits. */
public final class KeyCodes {

    // modifier bits (left-hand side)
    public static final int MOD_CTRL = 0x01;
    public static final int MOD_SHIFT = 0x02;
    public static final int MOD_ALT = 0x04;
    public static final int MOD_GUI = 0x08;

    public static final int ENTER = 0x28, ESC = 0x29, BACKSPACE = 0x2A, TAB = 0x2B, SPACE = 0x2C;
    public static final int CAPS_LOCK = 0x39;
    public static final int F1 = 0x3A;
    public static final int PRINT_SCREEN = 0x46;
    public static final int INSERT = 0x49, HOME = 0x4A, PAGE_UP = 0x4B, DELETE = 0x4C, END = 0x4D, PAGE_DOWN = 0x4E;
    public static final int RIGHT = 0x4F, LEFT = 0x50, DOWN = 0x51, UP = 0x52;

    /** Keys offered in the shortcut editor. NAMES[i] belongs to CODES[i]. */
    public static final String[] NAMES;
    public static final int[] CODES;

    static {
        List<String> n = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            n.add(String.valueOf(ch));
            c.add(0x04 + (ch - 'A'));
        }
        for (char ch = '1'; ch <= '9'; ch++) {
            n.add(String.valueOf(ch));
            c.add(0x1E + (ch - '1'));
        }
        n.add("0");
        c.add(0x27);
        for (int i = 1; i <= 12; i++) {
            n.add("F" + i);
            c.add(F1 + i - 1);
        }
        add(n, c, "Enter", ENTER);
        add(n, c, "Esc", ESC);
        add(n, c, "Backspace", BACKSPACE);
        add(n, c, "Tab", TAB);
        add(n, c, "Space", SPACE);
        add(n, c, "Insert", INSERT);
        add(n, c, "Delete", DELETE);
        add(n, c, "Home", HOME);
        add(n, c, "End", END);
        add(n, c, "Page Up", PAGE_UP);
        add(n, c, "Page Down", PAGE_DOWN);
        add(n, c, "Up", UP);
        add(n, c, "Down", DOWN);
        add(n, c, "Left", LEFT);
        add(n, c, "Right", RIGHT);
        add(n, c, "Print Screen", PRINT_SCREEN);
        add(n, c, "-", 0x2D);
        add(n, c, "=", 0x2E);
        add(n, c, ",", 0x36);
        add(n, c, ".", 0x37);
        add(n, c, "/", 0x38);
        NAMES = n.toArray(new String[0]);
        CODES = new int[c.size()];
        for (int i = 0; i < CODES.length; i++) CODES[i] = c.get(i);
    }

    private static void add(List<String> n, List<Integer> c, String name, int code) {
        n.add(name);
        c.add(code);
    }

    private KeyCodes() {}

    public static String nameOf(int code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return NAMES[i];
        return "0x" + Integer.toHexString(code);
    }

    public static int indexOf(int code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return i;
        return 0;
    }
}
