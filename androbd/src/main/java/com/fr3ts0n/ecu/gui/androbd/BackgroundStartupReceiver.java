package com.fr3ts0n.ecu.gui.androbd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BackgroundStartupReceiver extends BroadcastReceiver
{
    private static final String TAG = "BackgroundStartupReceiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null)
        {
            return;
        }

        String action = intent.getAction();
        boolean shouldStart = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
        {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            shouldStart = state == BluetoothAdapter.STATE_ON;
        }

        if (shouldStart)
        {
            Log.i(TAG, "Starting OBD monitoring service from broadcast: " + action);
            startMonitoringService(context);
        }
    }

    static void startMonitoringService(Context context)
    {
        Intent serviceIntent = new Intent(context, ObdBackgroundService.class)
                .setAction(ObdBackgroundService.ACTION_START_MONITORING);
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                context.startForegroundService(serviceIntent);
            }
            else
            {
                context.startService(serviceIntent);
            }
        }
        catch (RuntimeException e)
        {
            Log.w(TAG, "Unable to start background OBD service", e);
        }
    }
}
