package com.my.airmouse.bt;

/** Sends one HID input report. Returns false when nothing was sent (not really connected). */
public interface ReportSender {
    boolean send(int reportId, byte[] data);
}
