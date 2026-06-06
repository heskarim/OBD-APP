package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoolantAlertEvaluatorTest
{
    @Test
    public void evaluatesNormalWarningAndCriticalLevels()
    {
        assertEquals(
                CoolantAlertEvaluator.Level.NORMAL,
                CoolantAlertEvaluator.evaluate(99.9, 100, 110));
        assertEquals(
                CoolantAlertEvaluator.Level.WARNING,
                CoolantAlertEvaluator.evaluate(100, 100, 110));
        assertEquals(
                CoolantAlertEvaluator.Level.CRITICAL,
                CoolantAlertEvaluator.evaluate(110, 100, 110));
    }

    @Test
    public void keepsCriticalThresholdAboveWarningWhenSettingsAreInvalid()
    {
        assertEquals(
                CoolantAlertEvaluator.Level.WARNING,
                CoolantAlertEvaluator.evaluate(100, 100, 90));
        assertEquals(
                CoolantAlertEvaluator.Level.CRITICAL,
                CoolantAlertEvaluator.evaluate(101, 100, 90));
    }
}
