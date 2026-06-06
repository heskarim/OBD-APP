package com.fr3ts0n.ecu.gui.androbd;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Set;
import java.util.logging.Logger;

final class VehicleAlertNotifier
{
    private static final Logger log = Logger.getLogger(VehicleAlertNotifier.class.getSimpleName());
    private static final String WARNING_CHANNEL_ID = "vehicle_warning_channel";
    private static final String CRITICAL_CHANNEL_ID = "vehicle_critical_alarm_channel_v2";
    private static final int UNKNOWN_DTC_NOTIFICATION_ID = 2101;
    private static final int COOLANT_WARNING_NOTIFICATION_ID = 2102;
    static final int COOLANT_CRITICAL_NOTIFICATION_ID = 2103;
    private static final int DTC_CLEAR_NOTIFICATION_ID = 2104;

    private final Context context;
    private final NotificationManager manager;

    VehicleAlertNotifier(Context context)
    {
        this.context = context.getApplicationContext();
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannels();
    }

    void showUnknownDtcs(Set<String> codes)
    {
        if (manager == null)
        {
            log.warning("Unknown DTC notification skipped: notification manager unavailable");
            return;
        }
        if (codes == null || codes.isEmpty())
        {
            manager.cancel(UNKNOWN_DTC_NOTIFICATION_ID);
            return;
        }

        manager.notify(
                UNKNOWN_DTC_NOTIFICATION_ID,
                baseBuilder(WARNING_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.unknown_dtc_title))
                        .setContentText(context.getString(
                                R.string.unknown_dtc_message,
                                DtcAutoClearPolicy.formatCodes(codes)))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(
                                R.string.unknown_dtc_message,
                                DtcAutoClearPolicy.formatCodes(codes))))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }

    void showCoolantWarning(double temperature)
    {
        if (manager == null)
        {
            log.warning("Coolant warning notification skipped: notification manager unavailable");
            return;
        }
        log.info("Showing coolant warning notification temp=" + temperature);
        manager.cancel(COOLANT_CRITICAL_NOTIFICATION_ID);
        manager.notify(
                COOLANT_WARNING_NOTIFICATION_ID,
                baseBuilder(WARNING_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.coolant_warning_title))
                        .setContentText(context.getString(R.string.coolant_warning_message, temperature))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }

    void showCoolantCritical(double temperature)
    {
        if (manager == null)
        {
            log.warning("Coolant critical notification skipped: notification manager unavailable");
            return;
        }
        log.info("Showing coolant critical notification temp=" + temperature);
        ensureChannels();
        Intent fullScreenIntent = new Intent(context, CriticalAlertActivity.class)
                .putExtra(CriticalAlertActivity.EXTRA_TEMPERATURE, temperature)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                1,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent acknowledgeIntent = new Intent(context, AlertAcknowledgeReceiver.class);
        PendingIntent acknowledgePendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                acknowledgeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        manager.cancel(COOLANT_WARNING_NOTIFICATION_ID);
        manager.notify(
                COOLANT_CRITICAL_NOTIFICATION_ID,
                baseBuilder(CRITICAL_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.coolant_critical_title))
                        .setContentText(context.getString(R.string.coolant_critical_message, temperature))
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .setVibrate(new long[]{0, 500, 250, 500, 250, 1000})
                        .addAction(
                                android.R.drawable.ic_menu_close_clear_cancel,
                                context.getString(R.string.acknowledge),
                                acknowledgePendingIntent)
                        .build());
    }

    void showDtcClear(String codes, int clearCount)
    {
        if (manager == null)
        {
            log.warning("DTC clear notification skipped: notification manager unavailable");
            return;
        }
        manager.notify(
                DTC_CLEAR_NOTIFICATION_ID,
                baseBuilder(WARNING_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.dtc_clear_notification_title))
                        .setContentText(context.getString(
                                R.string.dtc_clear_notification_message,
                                codes,
                                clearCount))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(
                                R.string.dtc_clear_notification_message,
                                codes,
                                clearCount)))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }

    void clearCoolantAlerts()
    {
        if (manager == null)
        {
            log.warning("Coolant alert clear skipped: notification manager unavailable");
            return;
        }
        log.info("Clearing coolant alert notifications");
        manager.cancel(COOLANT_WARNING_NOTIFICATION_ID);
        manager.cancel(COOLANT_CRITICAL_NOTIFICATION_ID);
    }

    void acknowledgeCriticalAlert()
    {
        if (manager == null)
        {
            log.warning("Critical alert acknowledge skipped: notification manager unavailable");
            return;
        }
        manager.cancel(COOLANT_CRITICAL_NOTIFICATION_ID);
    }

    private NotificationCompat.Builder baseBuilder(String channelId)
    {
        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true);
    }

    private void ensureChannels()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null)
        {
            return;
        }

        NotificationChannel warningChannel = new NotificationChannel(
                WARNING_CHANNEL_ID,
                context.getString(R.string.vehicle_warning_channel),
                NotificationManager.IMPORTANCE_HIGH);
        warningChannel.setDescription(context.getString(R.string.vehicle_warning_channel_description));

        NotificationChannel criticalChannel = new NotificationChannel(
                CRITICAL_CHANNEL_ID,
                context.getString(R.string.vehicle_critical_channel),
                NotificationManager.IMPORTANCE_HIGH);
        criticalChannel.setDescription(context.getString(R.string.vehicle_critical_channel_description));
        criticalChannel.enableVibration(true);
        criticalChannel.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 1000});
        criticalChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && manager.isNotificationPolicyAccessGranted())
        {
            criticalChannel.setBypassDnd(true);
        }

        manager.createNotificationChannel(warningChannel);
        manager.createNotificationChannel(criticalChannel);
    }
}
