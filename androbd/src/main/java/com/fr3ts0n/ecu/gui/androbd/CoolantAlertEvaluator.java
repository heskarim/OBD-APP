package com.fr3ts0n.ecu.gui.androbd;

final class CoolantAlertEvaluator
{
    enum Level
    {
        NORMAL,
        WARNING,
        CRITICAL
    }

    private CoolantAlertEvaluator()
    {
    }

    static Level evaluate(double temperature, int warningThreshold, int criticalThreshold)
    {
        int effectiveCriticalThreshold = Math.max(criticalThreshold, warningThreshold + 1);
        if (temperature >= effectiveCriticalThreshold)
        {
            return Level.CRITICAL;
        }
        if (temperature >= warningThreshold)
        {
            return Level.WARNING;
        }
        return Level.NORMAL;
    }
}
