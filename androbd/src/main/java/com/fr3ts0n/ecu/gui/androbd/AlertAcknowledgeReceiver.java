package com.fr3ts0n.ecu.gui.androbd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlertAcknowledgeReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        new VehicleAlertNotifier(context).acknowledgeCriticalAlert();
    }
}
