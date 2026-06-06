package com.fr3ts0n.ecu.gui.androbd;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RecordingExporter
{
    static final class Result
    {
        final String displayName;
        final Uri uri;
        final File file;

        Result(String displayName, Uri uri, File file)
        {
            this.displayName = displayName;
            this.uri = uri;
            this.file = file;
        }
    }

    private RecordingExporter()
    {
    }

    static Result exportToDownloads(
            Context context,
            SensorRecorder recorder,
            VehicleEventLog eventLog,
            String appName) throws IOException
    {
        return exportToDownloads(context, recorder, eventLog, appName, createExportName());
    }

    static Result exportToDownloads(
            Context context,
            SensorRecorder recorder,
            VehicleEventLog eventLog,
            String appName,
            String displayName) throws IOException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null)
            {
                throw new IOException("Unable to create Downloads entry");
            }
            writeToUri(context, recorder, eventLog, appName, uri);
            return new Result(displayName, uri, null);
        }

        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null)
        {
            throw new IOException("Downloads directory is unavailable");
        }
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new IOException("Unable to create Downloads directory");
        }
        File file = new File(directory, displayName);
        try (OutputStream outputStream = new FileOutputStream(file))
        {
            recorder.writeZip(outputStream, eventLog, appName);
        }
        return new Result(displayName, null, file);
    }

    private static void writeToUri(
            Context context,
            SensorRecorder recorder,
            VehicleEventLog eventLog,
            String appName,
            Uri uri) throws IOException
    {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri))
        {
            if (outputStream == null)
            {
                throw new IOException("Unable to open export file");
            }
            recorder.writeZip(outputStream, eventLog, appName);
        }
    }

    private static String createExportName()
    {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "androbd-session-" + timestamp + ".zip";
    }
}
