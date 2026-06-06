package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VehicleEventLogTest
{
    @Test
    public void tracksClearCountAndExportsJsonLines()
    {
        VehicleEventLog log = new VehicleEventLog();

        int firstCount = log.recordDtcClear(1000L, "auto_clear", "P0101, P0420");
        int secondCount = log.recordDtcClear(2000L, "manual_clear", "P0300");
        log.recordUnknownDtcsBlocked(3000L, "P0441");

        assertEquals(1, firstCount);
        assertEquals(2, secondCount);
        assertEquals(2, log.getClearCount());
        assertEquals(3, log.getEventCount());

        String jsonl = log.toJsonLines();
        assertTrue(jsonl.contains("\"type\":\"dtc_clear\""));
        assertTrue(jsonl.contains("\"clear_count\":2"));
        assertTrue(jsonl.contains("\"type\":\"dtc_auto_clear_blocked\""));
        assertTrue(jsonl.endsWith("\n"));
    }

    @Test
    public void escapesJsonText()
    {
        VehicleEventLog log = new VehicleEventLog();

        log.recordEvent(1000L, "note", "code \"quoted\" \\ slash");

        assertTrue(log.toJsonLines().contains("code \\\"quoted\\\" \\\\ slash"));
    }
}
