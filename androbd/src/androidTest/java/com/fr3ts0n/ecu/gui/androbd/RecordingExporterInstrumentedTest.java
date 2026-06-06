package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RecordingExporterInstrumentedTest
{
    @Test
    public void exportToDownloads_writesAiFriendlyZip() throws Exception
    {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SensorRecorder recorder = new SensorRecorder();
        VehicleEventLog eventLog = new VehicleEventLog();

        recorder.start(1000L);
        recorder.recordSample(1100L, 5, "engine_coolant_temperature", "Coolant", 101.5, "C");
        recorder.recordSample(1200L, 12, "engine_rpm", "Engine RPM", 820, "rpm");
        eventLog.recordDtcClear(1300L, "manual_clear", "P0101");
        recorder.stop(1400L);

        RecordingExporter.Result result = RecordingExporter.exportToDownloads(
                context,
                recorder,
                eventLog,
                "AndrOBD test",
                "androbd-instrumented-export.zip");

        try
        {
            String exported = readZipText(context, result);
            assertTrue(exported.contains("ENTRY:session.json"));
            assertTrue(exported.contains("\"format\":\"androbd-ai-session-v1\""));
            assertTrue(exported.contains("\"sample_count\":2"));
            assertTrue(exported.contains("ENTRY:samples.csv"));
            assertTrue(exported.contains("engine_coolant_temperature"));
            assertTrue(exported.contains("engine_rpm"));
            assertTrue(exported.contains("ENTRY:events.jsonl"));
            assertTrue(exported.contains("\"type\":\"dtc_clear\""));
        }
        finally
        {
            if (result.uri != null)
            {
                context.getContentResolver().delete(result.uri, null, null);
            }
            if (result.file != null)
            {
                result.file.delete();
            }
        }
    }

    private String readZipText(Context context, RecordingExporter.Result result) throws Exception
    {
        InputStream inputStream = result.uri == null
                ? new FileInputStream(result.file)
                : context.getContentResolver().openInputStream(result.uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream zip = new ZipInputStream(inputStream))
        {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zip.getNextEntry()) != null)
            {
                out.write(("ENTRY:" + entry.getName() + "\n").getBytes("UTF-8"));
                int count;
                while ((count = zip.read(buffer)) > 0)
                {
                    out.write(buffer, 0, count);
                }
                out.write('\n');
            }
        }
        return out.toString("UTF-8");
    }
}
