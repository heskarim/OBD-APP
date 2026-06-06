package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CriticalAlertActivityInstrumentedTest
{
    @Test
    public void criticalAlertActivityStartsInsideAppPackage()
    {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(
                instrumentation.getTargetContext(),
                CriticalAlertActivity.class)
                .putExtra(CriticalAlertActivity.EXTRA_TEMPERATURE, 112.5)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Activity activity = instrumentation.startActivitySync(intent);
        try
        {
            assertTrue(activity instanceof CriticalAlertActivity);
            assertTrue(hasText(activity.getWindow().getDecorView(), "ENGINE OVERHEATING"));
            assertTrue(hasText(activity.getWindow().getDecorView(), "112.5 C"));
        }
        finally
        {
            activity.finish();
        }
    }

    private boolean hasText(View view, String expected)
    {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText()))
        {
            return true;
        }
        if (!(view instanceof ViewGroup))
        {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
        {
            if (hasText(group.getChildAt(i), expected))
            {
                return true;
            }
        }
        return false;
    }
}
