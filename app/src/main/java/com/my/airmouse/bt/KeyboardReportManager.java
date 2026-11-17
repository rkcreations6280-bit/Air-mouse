package com.my.airmouse.bt;

/** Builds and sends keyboard reports: modifiers plus up to 6 simultaneous keys. */
public final class KeyboardReportManager {

    private final ReportSender sender;
    private int modifiers;
    private final int[] keys = new int[6];
    private int count;

    public KeyboardReportManager(ReportSender sender) {
        this.sender = sender;
    }

    public void keyDown(int code) {
        for (int i = 0; i < count; i++) if (keys[i] == code) return;
        if (count < keys.length) keys[count++] = code;
        send();
    }

    public void keyUp(int code) {
        for (int i = 0; i < count; i++) {
            if (keys[i] == code) {
                for (int j = i; j < count - 1; j++) keys[j] = keys[j + 1];
                count--;
                break;
            }
        }
        send();
    }

    public void modifierDown(int mask) {
        modifiers |= mask;
        send();
    }

    public void modifierUp(int mask) {
        modifiers &= ~mask;
        send();
    }

    public int getModifiers() {
        return modifiers;
    }

    /** Press and release one key, with extra modifiers held only for this key. */
    public void tap(int extraModifiers, int code) {
        int old = modifiers;
        modifiers |= extraModifiers;
        keyDown(code);
        keyUp(code);
        modifiers = old;
        send();
    }

    /** Releases everything that is held and tells the host. */
    public void releaseAll() {
        if (modifiers != 0 || count != 0) {
            modifiers = 0;
            count = 0;
            send();
        }
    }

    /** Forgets held keys without sending (used when the connection is already gone). */
    public void resetState() {
        modifiers = 0;
        count = 0;
    }

    private void send() {
        byte[] r = new byte[8];
        r[0] = (byte) modifiers;
        for (int i = 0; i < count; i++) r[2 + i] = (byte) keys[i];
        sender.send(HidDescriptor.REPORT_KEYBOARD, r);
    }
}
