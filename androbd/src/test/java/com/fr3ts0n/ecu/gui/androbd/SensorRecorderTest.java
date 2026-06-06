package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensorRecorderTest
{
    @Test
    public void recordsSamplesAndExportsCsv()
    {
        SensorRecorder recorder = new SensorRecorder();

        recorder.start(1000L);
        recorder.recordSample(1100L, 5, "engine_coolant_temperature", "Coolant, Temp", 101.5, "C");
        recorder.recordSample(
                1200L,
                12,
                "engine_rpm",
                "Engine RPM",
                820,
                "rpm",
                "snapshot",
                true,
                5000L);
        recorder.stop(1300L);

        assertFalse(recorder.isRecording());
        assertEquals(2, recorder.getSampleCount());
        assertEquals(300L, recorder.getDurationMs(1300L));

        String csv = recorder.toCsv();
        assertTrue(csv.startsWith(
                "timestamp_ms,elapsed_ms,pid,mnemonic,description,value,units,sample_type,is_stale,age_ms,raw_response,polling_error,response_time_ms\n"));
        assertTrue(csv.contains(
                "1100,100,0x05,engine_coolant_temperature,\"Coolant, Temp\",101.5,C,update,false,0,,,-1\n"));
        assertTrue(csv.contains(
                "1200,200,0x0C,engine_rpm,Engine RPM,820,rpm,snapshot,true,5000,,,-1\n"));
    }

    @Test
    public void exportsAiFriendlyZipBundle() throws Exception
    {
        SensorRecorder recorder = new SensorRecorder();
        VehicleEventLog eventLog = new VehicleEventLog();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        recorder.start(1000L);
        recorder.recordSample(1100L, 5, "engine_coolant_temperature", "Coolant", 101.5, "C");
        recorder.recordAvailableSensor(5, "engine_coolant_temperature", "Coolant", "C");
        recorder.recordAvailableSensor(0x5A, "accelerator_pedal_position", "Accelerator Pedal", "%");
        recorder.setDtcSnapshot(java.util.Arrays.asList(
                new SensorRecorder.DtcRecord(0x03, "P0101", "Mass air flow"),
                new SensorRecorder.DtcRecord(0x07, "P0299", "Pending boost")));
        recorder.recordFreezeFrameValue(
                1500L,
                0x0C,
                "engine_rpm",
                "Engine RPM",
                1800,
                "rpm");
        eventLog.recordDtcClear(1200L, "auto_clear", "P0101");
        recorder.writeZip(out, eventLog, "AndrOBD test");

        String zipContent = readZipText(out.toByteArray());
        assertTrue(zipContent.contains("ENTRY:session.json"));
        assertTrue(zipContent.contains("\"app\":\"AndrOBD test\""));
        assertTrue(zipContent.contains("\"format\":\"androbd-ai-session-v3\""));
        assertTrue(zipContent.contains("\"sample_count\":1"));
        assertTrue(zipContent.contains("ENTRY:samples.csv"));
        assertTrue(zipContent.contains("raw_response,polling_error,response_time_ms"));
        assertTrue(zipContent.contains("engine_coolant_temperature"));
        assertTrue(zipContent.contains("ENTRY:available_sensors.csv"));
        assertTrue(zipContent.contains("accelerator_pedal_position"));
        assertTrue(zipContent.contains("ENTRY:dtcs.json"));
        assertTrue(zipContent.contains("\"stored\""));
        assertTrue(zipContent.contains("\"pending\""));
        assertTrue(zipContent.contains("P0299"));
        assertTrue(zipContent.contains("ENTRY:freeze_frame.csv"));
        assertTrue(zipContent.contains("engine_rpm"));
        assertTrue(zipContent.contains("ENTRY:events.jsonl"));
        assertTrue(zipContent.contains("\"type\":\"dtc_clear\""));
        assertTrue(zipContent.contains("ENTRY:analysis_profile.json"));
        assertTrue(zipContent.contains("\"nissan_consult_profile\""));
        assertTrue(zipContent.contains("ENTRY:standard_pid_coverage.csv"));
        assertTrue(zipContent.contains("BATTERY VOLT,0x42,ecu_voltage"));
        assertTrue(zipContent.contains("BARO SEN,0x33,barometric_pressure"));
        assertTrue(zipContent.contains("ACCEL POS SEN 1,0x49,throttle_position_d"));
        assertTrue(zipContent.contains("ACCEL SEN 2,0x4A,throttle_position_e"));
        assertTrue(zipContent.contains("ENTRY:nissan_consult_gap_report.csv"));
        assertTrue(zipContent.contains("EGR VOL CON/V,requires_nissan_consult_pid"));
        assertTrue(zipContent.contains("MAIN INJ WID,requires_nissan_consult_pid"));
        assertTrue(zipContent.contains("INT/A VOLUME,requires_nissan_consult_pid"));
        assertTrue(zipContent.contains("COOLING FAN,requires_nissan_consult_pid"));
    }

    private String readZipText(byte[] zipBytes) throws Exception
    {
        StringBuilder result = new StringBuilder();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry;
        byte[] buffer = new byte[1024];
        while ((entry = zip.getNextEntry()) != null)
        {
            result.append("ENTRY:").append(entry.getName()).append("\n");
            int count;
            while ((count = zip.read(buffer)) > 0)
            {
                result.append(new String(buffer, 0, count, "UTF-8"));
            }
            result.append("\n");
        }
        return result.toString();
    }
}
