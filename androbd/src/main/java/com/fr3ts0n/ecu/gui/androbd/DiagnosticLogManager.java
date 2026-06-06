package com.fr3ts0n.ecu.gui.androbd;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.fr3ts0n.ecu.prot.obd.ElmProt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

final class DiagnosticLogManager
{
    static final class Result
    {
        final String displayName;

        Result(String displayName)
        {
            this.displayName = displayName;
        }
    }

    private static final Logger log = Logger.getLogger(DiagnosticLogManager.class.getName());
    private static FileHandler diagnosticHandler;
    private static Level previousRootLevel;
    private static long sessionStartedAt;

    private final Context context;
    private final SharedPreferences preferences;

    DiagnosticLogManager(Context context, SharedPreferences preferences)
    {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
    }

    synchronized boolean isActive()
    {
        return diagnosticHandler != null;
    }

    synchronized void start() throws IOException
    {
        if (diagnosticHandler != null)
        {
            return;
        }

        File directory = getLogDirectory();
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new IOException("Unable to create diagnostic log directory");
        }

        sessionStartedAt = System.currentTimeMillis();
        File logFile = new File(directory, "diagnostic-" + timestamp(sessionStartedAt) + ".log.txt");
        diagnosticHandler = new FileHandler(logFile.getAbsolutePath(), true);
        diagnosticHandler.setLevel(Level.FINER);
        diagnosticHandler.setFormatter(new SimpleFormatter()
        {
            private static final String FORMAT = "%1$tF\t%1$tT.%1$tL\t%4$s\t%3$s\t%5$s%n";

            @Override
            public synchronized String format(LogRecord record)
            {
                return String.format(
                        Locale.US,
                        FORMAT,
                        new Date(record.getMillis()),
                        record.getSourceClassName(),
                        record.getLoggerName(),
                        record.getLevel().getName(),
                        record.getMessage());
            }
        });

        Logger root = Logger.getLogger("");
        previousRootLevel = root.getLevel();
        root.setLevel(Level.FINER);
        root.addHandler(diagnosticHandler);
        log.info("Diagnostic logging started");
    }

    synchronized void stop()
    {
        if (diagnosticHandler == null)
        {
            return;
        }

        log.info("Diagnostic logging stopped");
        Logger root = Logger.getLogger("");
        root.removeHandler(diagnosticHandler);
        diagnosticHandler.flush();
        diagnosticHandler.close();
        diagnosticHandler = null;
        if (previousRootLevel != null)
        {
            root.setLevel(previousRootLevel);
        }
    }

    synchronized Result exportToDownloads() throws IOException
    {
        flush();
        File[] logFiles = getLogDirectory().listFiles((directory, name) -> name.endsWith(".log.txt"));
        if (logFiles == null || logFiles.length == 0)
        {
            throw new IOException("No diagnostic logs recorded yet");
        }

        String displayName = "androbd-diagnostic-" + timestamp(System.currentTimeMillis()) + ".zip";
        Map<String, String> metadata = createMetadata();
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
            try (OutputStream output = context.getContentResolver().openOutputStream(uri))
            {
                if (output == null)
                {
                    throw new IOException("Unable to open diagnostic export");
                }
                DiagnosticLogArchive.writeZip(output, logFiles, metadata);
            }
        }
        else
        {
            File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null || (!directory.exists() && !directory.mkdirs()))
            {
                throw new IOException("Downloads directory is unavailable");
            }
            try (OutputStream output = new FileOutputStream(new File(directory, displayName)))
            {
                DiagnosticLogArchive.writeZip(output, logFiles, metadata);
            }
        }
        return new Result(displayName);
    }

    private synchronized void flush()
    {
        if (diagnosticHandler != null)
        {
            diagnosticHandler.flush();
        }
        for (Handler handler : Logger.getLogger("").getHandlers())
        {
            handler.flush();
        }
    }

    private Map<String, String> createMetadata()
    {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("app_version", BuildConfig.VERSION_NAME);
        metadata.put("android_version", Build.VERSION.RELEASE);
        metadata.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        metadata.put("session_started_at", sessionStartedAt == 0 ? "unknown" : timestamp(sessionStartedAt));
        metadata.put("exported_at", timestamp(System.currentTimeMillis()));
        metadata.put("comm_medium", CommService.medium.toString());
        metadata.put("saved_adapter_address",
                preferences.getString(BackgroundAutoConnectConfig.KEY_LAST_DEV_ADDRESS, "none"));
        metadata.put("secure_connection",
                String.valueOf(preferences.getBoolean(BackgroundAutoConnectConfig.KEY_BT_SECURE_CONNECTION, false)));
        metadata.put("preferred_protocol",
                preferences.getString(SettingsActivity.KEY_PROT_SELECT, "0"));
        metadata.put("elm_status", CommService.elm.getStatus().toString());
        metadata.put("obd_service", String.valueOf(CommService.elm.getService()));
        metadata.put("demo_thread_running", String.valueOf(ElmProt.runDemo));
        AutomationSettings automationSettings = new AutomationSettings(preferences);
        metadata.put("coolant_monitoring_enabled",
                String.valueOf(automationSettings.isCoolantMonitoringEnabled()));
        metadata.put("coolant_warning_threshold_c",
                String.valueOf(automationSettings.getCoolantWarningThreshold()));
        metadata.put("coolant_critical_threshold_c",
                String.valueOf(automationSettings.getCoolantCriticalThreshold()));
        return metadata;
    }

    private File getLogDirectory()
    {
        return new File(FileHelper.getPath(context), "diagnostic");
    }

    private static String timestamp(long time)
    {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(time));
    }
}
