package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;

public class DiagnosticLogArchiveTest
{
    @Test
    public void exportsLogsAndMetadata() throws Exception
    {
        File directory = Files.createTempDirectory("androbd-diagnostic").toFile();
        File logFile = new File(directory, "diagnostic.log.txt");
        try (FileOutputStream output = new FileOutputStream(logFile))
        {
            output.write("TX:0100\nRX:4100".getBytes(StandardCharsets.UTF_8));
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("medium", "BLUETOOTH");
        metadata.put("protocol", "Automatic");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DiagnosticLogArchive.writeZip(output, new File[]{logFile}, metadata);

        Map<String, String> entries = readEntries(output.toByteArray());
        assertEquals("TX:0100\nRX:4100", entries.get("logs/diagnostic.log.txt"));
        assertEquals("medium=BLUETOOTH\nprotocol=Automatic\n", entries.get("metadata.txt"));
    }

    private Map<String, String> readEntries(byte[] bytes) throws Exception
    {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes)))
        {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null)
            {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    content.write(buffer, 0, read);
                }
                result.put(entry.getName(), content.toString(StandardCharsets.UTF_8.name()));
            }
        }
        return result;
    }
}
