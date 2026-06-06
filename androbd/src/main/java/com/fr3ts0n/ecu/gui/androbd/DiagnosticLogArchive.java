package com.fr3ts0n.ecu.gui.androbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticLogArchive
{
    private DiagnosticLogArchive()
    {
    }

    static void writeZip(OutputStream output, File[] logFiles, Map<String, String> metadata)
            throws IOException
    {
        try (ZipOutputStream zip = new ZipOutputStream(output))
        {
            writeMetadata(zip, metadata);
            File[] sortedFiles = logFiles.clone();
            Arrays.sort(sortedFiles, (left, right) -> left.getName().compareTo(right.getName()));
            for (File logFile : sortedFiles)
            {
                if (logFile.isFile())
                {
                    writeFile(zip, logFile);
                }
            }
        }
    }

    private static void writeMetadata(ZipOutputStream zip, Map<String, String> metadata)
            throws IOException
    {
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(metadata).entrySet())
        {
            content.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        zip.putNextEntry(new ZipEntry("metadata.txt"));
        zip.write(content.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeFile(ZipOutputStream zip, File file) throws IOException
    {
        zip.putNextEntry(new ZipEntry("logs/" + file.getName()));
        try (FileInputStream input = new FileInputStream(file))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }
}
