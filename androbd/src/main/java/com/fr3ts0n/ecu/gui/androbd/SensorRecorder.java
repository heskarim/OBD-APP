package com.fr3ts0n.ecu.gui.androbd;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SensorRecorder
{
    static final class Sample
    {
        final long timestampMs;
        final long elapsedMs;
        final int pid;
        final String mnemonic;
        final String description;
        final String value;
        final String units;
        final String sampleType;
        final boolean stale;
        final long ageMs;
        final String rawResponse;
        final String pollingError;
        final long responseTimeMs;

        Sample(long timestampMs, long elapsedMs, int pid, String mnemonic, String description,
               String value, String units, String sampleType, boolean stale, long ageMs,
               String rawResponse, String pollingError, long responseTimeMs)
        {
            this.timestampMs = timestampMs;
            this.elapsedMs = elapsedMs;
            this.pid = pid;
            this.mnemonic = mnemonic;
            this.description = description;
            this.value = value;
            this.units = units;
            this.sampleType = sampleType;
            this.stale = stale;
            this.ageMs = ageMs;
            this.rawResponse = rawResponse;
            this.pollingError = pollingError;
            this.responseTimeMs = responseTimeMs;
        }
    }

    static final class SensorInfo
    {
        final int pid;
        final String mnemonic;
        final String description;
        final String units;

        SensorInfo(int pid, String mnemonic, String description, String units)
        {
            this.pid = pid;
            this.mnemonic = safe(mnemonic);
            this.description = safe(description);
            this.units = safe(units);
        }
    }

    static final class DtcRecord
    {
        final int status;
        final String code;
        final String description;

        DtcRecord(int status, String code, String description)
        {
            this.status = status;
            this.code = safe(code);
            this.description = safe(description);
        }
    }

    static final class FreezeFrameValue
    {
        final long timestampMs;
        final long elapsedMs;
        final int pid;
        final String mnemonic;
        final String description;
        final String value;
        final String units;

        FreezeFrameValue(long timestampMs, long elapsedMs, int pid, String mnemonic, String description,
                         String value, String units)
        {
            this.timestampMs = timestampMs;
            this.elapsedMs = elapsedMs;
            this.pid = pid;
            this.mnemonic = safe(mnemonic);
            this.description = safe(description);
            this.value = safe(value);
            this.units = safe(units);
        }
    }

    private final List<Sample> samples = new ArrayList<>();
    private final Map<String, SensorInfo> availableSensors = new LinkedHashMap<>();
    private final List<DtcRecord> dtcSnapshot = new ArrayList<>();
    private final Map<String, FreezeFrameValue> freezeFrameValues = new LinkedHashMap<>();
    private long startedAtMs;
    private long stoppedAtMs;
    private boolean recording;

    synchronized void start(long timestampMs)
    {
        samples.clear();
        availableSensors.clear();
        dtcSnapshot.clear();
        freezeFrameValues.clear();
        startedAtMs = timestampMs;
        stoppedAtMs = 0;
        recording = true;
    }

    synchronized void stop(long timestampMs)
    {
        stoppedAtMs = timestampMs;
        recording = false;
    }

    synchronized boolean isRecording()
    {
        return recording;
    }

    synchronized long getStartedAtMs()
    {
        return startedAtMs;
    }

    synchronized int getSampleCount()
    {
        return samples.size();
    }

    synchronized long getDurationMs(long nowMs)
    {
        if (startedAtMs == 0)
        {
            return 0;
        }
        long end = recording ? nowMs : stoppedAtMs;
        return Math.max(0, end - startedAtMs);
    }

    synchronized List<Sample> getSamples()
    {
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }

    synchronized void recordSample(long timestampMs, int pid, String mnemonic, String description,
                                   Object value, String units)
    {
        recordSample(timestampMs, pid, mnemonic, description, value, units, "update", false, 0);
    }

    synchronized void recordSample(long timestampMs, int pid, String mnemonic, String description,
                                   Object value, String units, String sampleType, boolean stale, long ageMs)
    {
        recordSample(timestampMs, pid, mnemonic, description, value, units, sampleType, stale, ageMs, "", "", -1L);
    }

    synchronized void recordSample(long timestampMs, int pid, String mnemonic, String description,
                                   Object value, String units, String sampleType, boolean stale, long ageMs,
                                   String rawResponse, String pollingError, long responseTimeMs)
    {
        if (!recording)
        {
            return;
        }
        recordAvailableSensor(pid, mnemonic, description, units);
        samples.add(new Sample(
                timestampMs,
                Math.max(0, timestampMs - startedAtMs),
                pid,
                safe(mnemonic),
                safe(description),
                safe(value),
                safe(units),
                safe(sampleType),
                stale,
                ageMs,
                safe(rawResponse),
                safe(pollingError),
                responseTimeMs));
    }

    synchronized void recordAvailableSensor(int pid, String mnemonic, String description, String units)
    {
        availableSensors.put(sensorKey(pid, mnemonic), new SensorInfo(pid, mnemonic, description, units));
    }

    synchronized void setDtcSnapshot(List<DtcRecord> records)
    {
        dtcSnapshot.clear();
        if (records != null)
        {
            dtcSnapshot.addAll(records);
        }
    }

    synchronized void recordFreezeFrameValue(long timestampMs, int pid, String mnemonic, String description,
                                             Object value, String units)
    {
        if (!recording)
        {
            return;
        }
        FreezeFrameValue freezeFrameValue = new FreezeFrameValue(
                timestampMs,
                Math.max(0, timestampMs - startedAtMs),
                pid,
                mnemonic,
                description,
                safe(value),
                units);
        freezeFrameValues.put(sensorKey(pid, mnemonic), freezeFrameValue);
    }

    synchronized String toCsv()
    {
        StringBuilder result = new StringBuilder();
        result.append("timestamp_ms,elapsed_ms,pid,mnemonic,description,value,units,sample_type,is_stale,age_ms,raw_response,polling_error,response_time_ms\n");
        for (Sample sample : samples)
        {
            result.append(sample.timestampMs).append(',')
                    .append(sample.elapsedMs).append(',')
                    .append(String.format(Locale.ROOT, "0x%02X", sample.pid)).append(',')
                    .append(csv(sample.mnemonic)).append(',')
                    .append(csv(sample.description)).append(',')
                    .append(csv(sample.value)).append(',')
                    .append(csv(sample.units)).append(',')
                    .append(csv(sample.sampleType)).append(',')
                    .append(sample.stale).append(',')
                    .append(sample.ageMs).append(',')
                    .append(csv(sample.rawResponse)).append(',')
                    .append(csv(sample.pollingError)).append(',')
                    .append(sample.responseTimeMs).append('\n');
        }
        return result.toString();
    }

    synchronized String availableSensorsCsv()
    {
        StringBuilder result = new StringBuilder();
        result.append("pid,mnemonic,description,units\n");
        for (SensorInfo sensor : availableSensors.values())
        {
            result.append(String.format(Locale.ROOT, "0x%02X", sensor.pid)).append(',')
                    .append(csv(sensor.mnemonic)).append(',')
                    .append(csv(sensor.description)).append(',')
                    .append(csv(sensor.units)).append('\n');
        }
        return result.toString();
    }

    synchronized String freezeFrameCsv()
    {
        StringBuilder result = new StringBuilder();
        result.append("timestamp_ms,elapsed_ms,pid,mnemonic,description,value,units\n");
        for (FreezeFrameValue value : freezeFrameValues.values())
        {
            result.append(value.timestampMs).append(',')
                    .append(value.elapsedMs).append(',')
                    .append(String.format(Locale.ROOT, "0x%02X", value.pid)).append(',')
                    .append(csv(value.mnemonic)).append(',')
                    .append(csv(value.description)).append(',')
                    .append(csv(value.value)).append(',')
                    .append(csv(value.units)).append('\n');
        }
        return result.toString();
    }

    synchronized String dtcJson()
    {
        List<DtcRecord> current = new ArrayList<>();
        List<DtcRecord> stored = new ArrayList<>();
        List<DtcRecord> pending = new ArrayList<>();
        List<DtcRecord> permanent = new ArrayList<>();
        List<DtcRecord> unknown = new ArrayList<>();

        for (DtcRecord record : dtcSnapshot)
        {
            if (isNoCode(record.code))
            {
                continue;
            }
            switch (record.status)
            {
                case 0x03:
                    current.add(record);
                    stored.add(record);
                    break;
                case 0x07:
                    pending.add(record);
                    break;
                case 0x0A:
                    permanent.add(record);
                    break;
                default:
                    unknown.add(record);
                    break;
            }
        }

        return "{\n"
                + "  \"current\":" + dtcArray(current) + ",\n"
                + "  \"stored\":" + dtcArray(stored) + ",\n"
                + "  \"pending\":" + dtcArray(pending) + ",\n"
                + "  \"permanent\":" + dtcArray(permanent) + ",\n"
                + "  \"unknown_status\":" + dtcArray(unknown) + "\n"
                + "}\n";
    }

    synchronized void writeZip(OutputStream outputStream, VehicleEventLog eventLog, String appName)
            throws IOException
    {
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        writeEntry(zip, "session.json", sessionJson(eventLog, appName));
        writeEntry(zip, "samples.csv", toCsv());
        writeEntry(zip, "available_sensors.csv", availableSensorsCsv());
        writeEntry(zip, "dtcs.json", dtcJson());
        writeEntry(zip, "freeze_frame.csv", freezeFrameCsv());
        writeEntry(zip, "events.jsonl", eventLog == null ? "" : eventLog.toJsonLines());
        writeEntry(zip, "analysis_profile.json", analysisProfileJson());
        writeEntry(zip, "standard_pid_coverage.csv", standardPidCoverageCsv());
        writeEntry(zip, "nissan_consult_gap_report.csv", nissanConsultGapReportCsv());
        zip.finish();
    }

    synchronized String analysisProfileJson()
    {
        return "{\n"
                + "  \"format\":\"androbd-ai-session-v3\",\n"
                + "  \"purpose\":\"AI engine diagnostic export\",\n"
                + "  \"samples\":\"Samples include stale markers and reserved raw/polling timing columns.\",\n"
                + "  \"dtc_export\":{\n"
                + "    \"status\":\"snapshot_from_loaded_obd_code_lists\",\n"
                + "    \"note\":\"Current, stored, pending, and permanent groups are exported when the app has read those OBD services before export.\"\n"
                + "  },\n"
                + "  \"freeze_frame_export\":{\n"
                + "    \"status\":\"recorded_when_freeze_frame_service_is_active\",\n"
                + "    \"note\":\"Freeze-frame values are included when the ECU/app exposes mode 02 data during the recording session.\"\n"
                + "  },\n"
                + "  \"nissan_consult_profile\":{\n"
                + "    \"status\":\"known_labels_missing_request_ids\",\n"
                + "    \"note\":\"Nissan CONSULT labels are listed in the gap report until exact request bytes and formulas are known. Standard OBD equivalents are listed separately.\"\n"
                + "  }\n"
                + "}\n";
    }

    synchronized String standardPidCoverageCsv()
    {
        StringBuilder result = new StringBuilder();
        result.append("requested_label,pid,mnemonic,coverage,notes\n");
        for (String[] row : STANDARD_PID_COVERAGE)
        {
            appendCsvRow(result, row);
        }
        return result.toString();
    }

    synchronized String nissanConsultGapReportCsv()
    {
        StringBuilder result = new StringBuilder();
        result.append("requested_label,status,standard_equivalent,notes\n");
        for (String[] row : NISSAN_CONSULT_GAPS)
        {
            appendCsvRow(result, row);
        }
        return result.toString();
    }

    private String sessionJson(VehicleEventLog eventLog, String appName)
    {
        long endedAt = stoppedAtMs == 0 ? System.currentTimeMillis() : stoppedAtMs;
        int eventCount = eventLog == null ? 0 : eventLog.getEventCount();
        int clearCount = eventLog == null ? 0 : eventLog.getClearCount();
        return "{\n"
                + "  \"app\":\"" + VehicleEventLog.escapeJson(appName) + "\",\n"
                + "  \"format\":\"androbd-ai-session-v3\",\n"
                + "  \"started_at_ms\":" + startedAtMs + ",\n"
                + "  \"ended_at_ms\":" + endedAt + ",\n"
                + "  \"duration_ms\":" + getDurationMs(endedAt) + ",\n"
                + "  \"sample_count\":" + samples.size() + ",\n"
                + "  \"available_sensor_count\":" + availableSensors.size() + ",\n"
                + "  \"freeze_frame_value_count\":" + freezeFrameValues.size() + ",\n"
                + "  \"event_count\":" + eventCount + ",\n"
                + "  \"dtc_clear_count\":" + clearCount + "\n"
                + "}\n";
    }

    private static void writeEntry(ZipOutputStream zip, String name, String text) throws IOException
    {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String csv(String value)
    {
        String safe = safe(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r"))
        {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String safe(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sensorKey(int pid, String mnemonic)
    {
        return String.format(Locale.ROOT, "%02X:%s", pid, safe(mnemonic));
    }

    private static void appendCsvRow(StringBuilder result, String[] values)
    {
        for (int index = 0; index < values.length; index++)
        {
            if (index > 0)
            {
                result.append(',');
            }
            result.append(csv(values[index]));
        }
        result.append('\n');
    }

    private static boolean isNoCode(String code)
    {
        String normalized = safe(code).trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() || "0".equals(normalized) || "P0000".equals(normalized);
    }

    private static String dtcArray(List<DtcRecord> records)
    {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < records.size(); index++)
        {
            DtcRecord record = records.get(index);
            if (index > 0)
            {
                result.append(',');
            }
            result.append("{\"status\":")
                    .append(record.status)
                    .append(",\"code\":\"")
                    .append(VehicleEventLog.escapeJson(record.code))
                    .append("\",\"description\":\"")
                    .append(VehicleEventLog.escapeJson(record.description))
                    .append("\"}");
        }
        return result.append(']').toString();
    }

    private static final String[][] STANDARD_PID_COVERAGE = {
            {"RPM", "0x0C", "engine_speed", "standard_obd", "Engine speed when ECU supports PID 0x0C"},
            {"vehicle speed", "0x0D", "vehicle_speed", "standard_obd", "Vehicle speed when ECU supports PID 0x0D"},
            {"coolant temp", "0x05", "engine_coolant_temperature", "standard_obd", "Engine coolant temperature"},
            {"MAP / boost pressure", "0x0B", "intake_manifold_pressure", "standard_obd", "Intake manifold absolute pressure"},
            {"fuel rail pressure", "0x22/0x23/0x59/0x6D", "fuel_pressure_rel/fuel_pressure_wr/fuel_pressure_abs/rail_pressure_a",
                    "standard_obd", "Available when the ECU advertises the diesel fuel-pressure PIDs"},
            {"engine load", "0x04", "engine_load_calculated", "standard_obd", "Calculated engine load"},
            {"ACCEL POS SEN 1", "0x49", "throttle_position_d", "standard_obd_equivalent", "Accelerator Pedal Position D"},
            {"ACCEL SEN 2", "0x4A", "throttle_position_e", "standard_obd_equivalent", "Accelerator Pedal Position E"},
            {"accelerator pedal position", "0x5A", "accelerator_pedal_position", "standard_obd_equivalent", "Relative accelerator pedal position"},
            {"BATTERY VOLT", "0x42", "ecu_voltage", "standard_obd_equivalent", "Control module voltage; close practical equivalent for battery voltage"},
            {"BARO SEN", "0x33", "barometric_pressure", "standard_obd_equivalent", "Barometric pressure"},
            {"EGR command", "0x2C", "egr_ratio_commanded", "standard_obd_equivalent", "Commanded EGR percentage, not Nissan CONSULT EGR voltage"}
    };

    private static final String[][] NISSAN_CONSULT_GAPS = {
            {"EGR VOL CON/V", "requires_nissan_consult_pid", "0x2C egr_ratio_commanded",
                    "Standard OBD has commanded EGR percentage; exact Nissan voltage label needs CONSULT request bytes and formula."},
            {"MAIN INJ WID", "requires_nissan_consult_pid", "",
                    "Main injection width is manufacturer-specific on this platform."},
            {"INT/A VOLUME", "requires_nissan_consult_pid", "",
                    "Nissan intake-air volume label needs CONSULT request bytes and formula."},
            {"FUEL TEMP SEN", "requires_nissan_consult_pid_or_supported_standard_pid", "0x6D rail_temp_a/rail_temp_b",
                    "Generic rail temperature may exist on some ECUs, but the exact Nissan fuel temperature label is not verified."},
            {"COOLING FAN", "requires_nissan_consult_pid", "",
                    "Fan command/status is manufacturer-specific and should not be inferred from coolant temperature."}
    };
}
