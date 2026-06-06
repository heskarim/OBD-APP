package com.fr3ts0n.ecu.gui.androbd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VehicleEventLog
{
    private static final VehicleEventLog SHARED = new VehicleEventLog();

    static final class Event
    {
        final long timestampMs;
        final String type;
        final String message;
        final int clearCount;

        Event(long timestampMs, String type, String message, int clearCount)
        {
            this.timestampMs = timestampMs;
            this.type = type;
            this.message = message;
            this.clearCount = clearCount;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private int clearCount;

    static VehicleEventLog getShared()
    {
        return SHARED;
    }

    synchronized int recordDtcClear(long timestampMs, String mode, String codes)
    {
        clearCount++;
        recordEvent(timestampMs, "dtc_clear", mode + ": " + codes, clearCount);
        return clearCount;
    }

    synchronized void recordUnknownDtcsBlocked(long timestampMs, String codes)
    {
        recordEvent(timestampMs, "dtc_auto_clear_blocked", codes);
    }

    synchronized void recordEvent(long timestampMs, String type, String message)
    {
        recordEvent(timestampMs, type, message, clearCount);
    }

    private void recordEvent(long timestampMs, String type, String message, int eventClearCount)
    {
        events.add(new Event(timestampMs, safe(type), safe(message), eventClearCount));
    }

    synchronized int getClearCount()
    {
        return clearCount;
    }

    synchronized int getEventCount()
    {
        return events.size();
    }

    synchronized List<Event> getEvents()
    {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    synchronized String toJsonLines()
    {
        StringBuilder result = new StringBuilder();
        for (Event event : events)
        {
            result.append("{")
                    .append("\"timestamp_ms\":").append(event.timestampMs).append(",")
                    .append("\"type\":\"").append(escapeJson(event.type)).append("\",")
                    .append("\"message\":\"").append(escapeJson(event.message)).append("\",")
                    .append("\"clear_count\":").append(event.clearCount)
                    .append("}\n");
        }
        return result.toString();
    }

    static String escapeJson(String value)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < safe(value).length(); i++)
        {
            char c = safe(value).charAt(i);
            switch (c)
            {
                case '\\':
                    result.append("\\\\");
                    break;
                case '"':
                    result.append("\\\"");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        return result.toString();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
