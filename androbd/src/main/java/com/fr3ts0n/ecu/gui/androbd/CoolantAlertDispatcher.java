package com.fr3ts0n.ecu.gui.androbd;

import com.fr3ts0n.ecu.prot.obd.ObdProt;

import java.util.Locale;
import java.util.logging.Logger;

final class CoolantAlertDispatcher
{
    static final int COOLANT_PID = 0x05;

    private static final Logger log = Logger.getLogger(CoolantAlertDispatcher.class.getSimpleName());
    private static CoolantAlertEvaluator.Level currentLevel = CoolantAlertEvaluator.Level.NORMAL;

    private CoolantAlertDispatcher()
    {
    }

    static synchronized void reset(VehicleAlertNotifier notifier, String source)
    {
        currentLevel = CoolantAlertEvaluator.Level.NORMAL;
        if (notifier != null)
        {
            notifier.clearCoolantAlerts();
        }
        log.info("Coolant monitor reset source=" + source);
    }

    static synchronized void evaluate(
            double temperature,
            AutomationSettings settings,
            int obdService,
            VehicleEventLog eventLog,
            VehicleAlertNotifier notifier,
            String source)
    {
        if (settings == null || !settings.isCoolantMonitoringEnabled())
        {
            log.fine("Coolant monitor ignored source=" + source + " reason=disabled");
            return;
        }
        if (obdService != ObdProt.OBD_SVC_DATA)
        {
            log.fine("Coolant monitor ignored source=" + source + " reason=service service=" + obdService);
            return;
        }

        CoolantAlertEvaluator.Level newLevel = CoolantAlertEvaluator.evaluate(
                temperature,
                settings.getCoolantWarningThreshold(),
                settings.getCoolantCriticalThreshold());
        log.info(String.format(
                Locale.US,
                "Coolant monitor evaluated source=%s temp=%.1f warning=%d critical=%d level=%s previous=%s",
                source,
                temperature,
                settings.getCoolantWarningThreshold(),
                settings.getCoolantCriticalThreshold(),
                newLevel,
                currentLevel));

        if (newLevel == currentLevel)
        {
            return;
        }

        currentLevel = newLevel;
        String value = String.valueOf(temperature);
        switch (newLevel)
        {
            case WARNING:
                if (eventLog != null)
                {
                    eventLog.recordEvent(System.currentTimeMillis(), "coolant_warning", value);
                }
                if (notifier != null)
                {
                    log.info("Coolant warning notification requested source=" + source + " temp=" + value);
                    notifier.showCoolantWarning(temperature);
                }
                break;

            case CRITICAL:
                if (eventLog != null)
                {
                    eventLog.recordEvent(System.currentTimeMillis(), "coolant_critical", value);
                }
                if (notifier != null)
                {
                    log.info("Coolant critical notification requested source=" + source + " temp=" + value);
                    notifier.showCoolantCritical(temperature);
                }
                break;

            default:
                if (eventLog != null)
                {
                    eventLog.recordEvent(System.currentTimeMillis(), "coolant_normal", value);
                }
                if (notifier != null)
                {
                    notifier.clearCoolantAlerts();
                }
                break;
        }
    }
}
