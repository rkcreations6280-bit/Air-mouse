package com.my.airmouse.bt;

/** Builds and sends mouse reports. Keeps track of held buttons so they can always be released. */
public final class MouseReportManager {

    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int MIDDLE = 4;

    private final ReportSender sender;
    private int buttons;

    public MouseReportManager(ReportSender sender) {
        this.sender = sender;
    }

    public void move(int dx, int dy) {
        send(buttons, dx, dy, 0, 0);
    }

    /** vertical: positive = scroll up. horizontal: positive = scroll right. */
    public void scroll(int vertical, int horizontal) {
        send(buttons, 0, 0, vertical, horizontal);
    }

    public void press(int mask) {
        buttons |= mask;
        send(buttons, 0, 0, 0, 0);
    }

    public void release(int mask) {
        buttons &= ~mask;
        send(buttons, 0, 0, 0, 0);
    }

    public void click(int mask) {
        press(mask);
        release(mask);
    }

    public int getButtons() {
        return buttons;
    }

    /** Releases every held button and tells the host. */
    public void releaseAll() {
        if (buttons != 0) {
            buttons = 0;
            send(0, 0, 0, 0, 0);
        }
    }

    /** Forgets held buttons without sending (used when the connection is already gone). */
    public void resetState() {
        buttons = 0;
    }

    private void send(int b, int dx, int dy, int wheel, int pan) {
        do {
            int cx = clamp(dx), cy = clamp(dy), cw = clamp(wheel), cp = clamp(pan);
            sender.send(HidDescriptor.REPORT_MOUSE,
                    new byte[]{(byte) b, (byte) cx, (byte) cy, (byte) cw, (byte) cp});
            dx -= cx;
            dy -= cy;
            wheel -= cw;
            pan -= cp;
        } while (dx != 0 || dy != 0 || wheel != 0 || pan != 0);
    }

    private static int clamp(int v) {
        return Math.max(-127, Math.min(127, v));
    }
}
