package com.my.airmouse.bt;

/** Media keys (consumer control usages). */
public final class ConsumerReportManager {

    public static final int PLAY_PAUSE = 0xCD;
    public static final int NEXT = 0xB5;
    public static final int PREVIOUS = 0xB6;
    public static final int VOLUME_UP = 0xE9;
    public static final int VOLUME_DOWN = 0xEA;
    public static final int MUTE = 0xE2;

    private final ReportSender sender;

    public ConsumerReportManager(ReportSender sender) {
        this.sender = sender;
    }

    /** Press and release one media key. */
    public void tap(int usage) {
        send(usage);
        send(0);
    }

    private void send(int usage) {
        sender.send(HidDescriptor.REPORT_CONSUMER, new byte[]{(byte) (usage & 0xFF), (byte) ((usage >> 8) & 0xFF)});
    }
}
