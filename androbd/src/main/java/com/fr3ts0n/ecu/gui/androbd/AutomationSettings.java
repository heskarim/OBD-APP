package com.fr3ts0n.ecu.gui.androbd;

import android.content.SharedPreferences;

import java.util.Set;

final class AutomationSettings
{
    static final String KEY_AUTO_CLEAR_ENABLED = "auto_clear_known_dtcs";
    static final String KEY_APPROVED_DTCS = "approved_auto_clear_dtcs";
    static final String KEY_COOLANT_MONITORING_ENABLED = "coolant_monitoring_enabled";
    static final String KEY_COOLANT_WARNING_THRESHOLD = "coolant_warning_threshold";
    static final String KEY_COOLANT_CRITICAL_THRESHOLD = "coolant_critical_threshold";

    static final int DEFAULT_WARNING_THRESHOLD = 100;
    static final int DEFAULT_CRITICAL_THRESHOLD = 110;

    private final SharedPreferences preferences;

    AutomationSettings(SharedPreferences preferences)
    {
        this.preferences = preferences;
    }

    boolean isAutoClearEnabled()
    {
        return preferences.getBoolean(KEY_AUTO_CLEAR_ENABLED, false);
    }

    Set<String> getApprovedDtcs()
    {
        return DtcAutoClearPolicy.parseCodes(preferences.getString(KEY_APPROVED_DTCS, ""));
    }

    boolean isDtcApproved(String code)
    {
        return getApprovedDtcs().containsAll(DtcAutoClearPolicy.parseCodes(code));
    }

    void setDtcApproved(String code, boolean approved)
    {
        Set<String> codes = getApprovedDtcs();
        Set<String> selected = DtcAutoClearPolicy.parseCodes(code);
        if (approved)
        {
            codes.addAll(selected);
        }
        else
        {
            codes.removeAll(selected);
        }
        preferences.edit()
                .putString(KEY_APPROVED_DTCS, DtcAutoClearPolicy.formatCodes(codes))
                .apply();
    }

    boolean isCoolantMonitoringEnabled()
    {
        return preferences.getBoolean(KEY_COOLANT_MONITORING_ENABLED, false);
    }

    int getCoolantWarningThreshold()
    {
        return getInt(KEY_COOLANT_WARNING_THRESHOLD, DEFAULT_WARNING_THRESHOLD);
    }

    int getCoolantCriticalThreshold()
    {
        return getInt(KEY_COOLANT_CRITICAL_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD);
    }

    private int getInt(String key, int defaultValue)
    {
        try
        {
            return Integer.parseInt(preferences.getString(key, String.valueOf(defaultValue)));
        }
        catch (Exception ignored)
        {
            return defaultValue;
        }
    }
}
