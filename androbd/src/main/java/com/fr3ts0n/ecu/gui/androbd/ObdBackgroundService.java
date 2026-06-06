/*
 * (C) Copyright 2015 by fr3ts0n <erwin.scheuch-heilig@gmx.at>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

package com.fr3ts0n.ecu.gui.androbd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationCompat;

import com.fr3ts0n.ecu.EcuDataPv;
import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.fr3ts0n.ecu.prot.obd.ObdProt;
import com.fr3ts0n.pvs.PvChangeEvent;
import com.fr3ts0n.pvs.PvChangeListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Background service for OBD communication
 * Provides continuous monitoring and data collection even when app is in background
 */
public class ObdBackgroundService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, PvChangeListener, PropertyChangeListener {
    
    private static final String TAG = "ObdBackgroundService";
    private static final Logger log = Logger.getLogger(TAG);
    
    public static final String CHANNEL_ID = "obd_service_channel";
    public static final String ACTION_START_MONITORING = "com.fr3ts0n.ecu.gui.androbd.START_MONITORING";
    private static final int NOTIFICATION_ID = 1001;
    private static final long RECONNECT_DELAY_MS = 15000;
    private static final long AUTO_CONNECT_WATCHDOG_MS = 30000;
    private static final long PROTOCOL_REFRESH_DELAY_MS = 30000;
    
    // Service states
    public enum ServiceState {
        STOPPED, STARTING, RUNNING, STOPPING
    }
    
    private ServiceState currentState = ServiceState.STOPPED;
    private CommService commService;
    private CommService.MEDIUM activeMedium;
    private String currentAddress;
    private boolean autoReconnectEnabled = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ServiceStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private boolean foregroundStarted;
    private final Runnable reconnectRunnable = this::connectToSavedDevice;
    private final Runnable protocolRefreshRunnable = () -> refreshSavedConnection("protocol-health");
    private final Runnable autoConnectWatchdogRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (!autoReconnectEnabled)
            {
                return;
            }
            connectToSavedDeviceIfIdle("watchdog");
            mainHandler.postDelayed(this, AUTO_CONNECT_WATCHDOG_MS);
        }
    };
    private SharedPreferences preferences;
    private AutomationSettings automationSettings;
    private VehicleAlertNotifier vehicleAlertNotifier;
    private VehicleEventLog vehicleEventLog;
    private EcuDataPv coolantDataPv;
    private final PvChangeListener coolantChangeListener = event -> {
        if (EcuDataPv.FIELDS[EcuDataPv.FID_VALUE].equals(event.getKey())
                && event.getValue() instanceof Number) {
            double temperature = ((Number) event.getValue()).doubleValue();
            mainHandler.post(() -> evaluateCoolantTemperature(temperature));
        }
    };
    
    // Binder for local service binding
    public class LocalBinder extends Binder {
        public ObdBackgroundService getService() {
            return ObdBackgroundService.this;
        }
    }
    
    private final IBinder binder = new LocalBinder();
    
    // Interface for service state callbacks
    public interface ServiceStateListener {
        void onServiceStateChanged(ServiceState newState);
        void onDataReceived(String data);
        void onConnectionStateChanged(CommService.STATE connectionState);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        automationSettings = new AutomationSettings(preferences);
        vehicleAlertNotifier = new VehicleAlertNotifier(this);
        vehicleEventLog = VehicleEventLog.getShared();
        preferences.registerOnSharedPreferenceChangeListener(this);
        ObdProt.PidPvs.addPvChangeListener(this, PvChangeEvent.PV_ADDED | PvChangeEvent.PV_CLEARED);
        CommService.elm.addPropertyChangeListener(this);
        attachExistingCoolantPid();
        createNotificationChannel();
        log.info("ObdBackgroundService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        autoReconnectEnabled = true;
        if (!foregroundStarted) {
            startForegroundService();
        }
        if (commService == null) {
            initializeCommService();
        }
        connectToSavedDevice();
        startAutoConnectWatchdog();
        // Return sticky to restart service if killed by system
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.removeCallbacks(protocolRefreshRunnable);
        mainHandler.removeCallbacks(autoConnectWatchdogRunnable);
        detachCoolantListener();
        ObdProt.PidPvs.removePvChangeListener(this);
        CommService.elm.removePropertyChangeListener(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        stopCommService();
        currentState = ServiceState.STOPPED;
        notifyStateListeners();
        log.info("ObdBackgroundService destroyed");
        super.onDestroy();
    }
    
    private void startForegroundService() {
        Notification notification = createNotification("OBD Service Running", "Monitoring vehicle data...");
        startForeground(NOTIFICATION_ID, notification);
        foregroundStarted = true;
        currentState = ServiceState.RUNNING;
        notifyStateListeners();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "OBD Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background OBD data monitoring");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String title, String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void initializeCommService() {
        BackgroundAutoConnectConfig config = readAutoConnectConfig();
        CommService.medium = config.medium;
        if (requiresBluetoothPermission(config.medium) && !hasBluetoothConnectPermission()) {
            updateNotification("OBD Service", "Waiting for Bluetooth permission");
            return;
        }

        // Initialize communication service based on selected medium
        Handler serviceHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                handleCommServiceMessage(msg);
            }
        };
        
        switch (CommService.medium) {
            case BLUETOOTH:
                commService = new BtCommService(this, serviceHandler);
                break;
            case BLE:
                commService = new BleCommService(this, serviceHandler);
                break;
            case USB:
                commService = new UsbCommService(this, serviceHandler);
                break;
            case NETWORK:
                commService = new NetworkCommService(this, serviceHandler);
                break;
        }
        
        if (commService != null) {
            activeMedium = CommService.medium;
            commService.start();
        }
    }
    
    private void stopCommService() {
        if (commService != null) {
            commService.stop();
            commService = null;
        }
        activeMedium = null;
        currentAddress = null;
    }
    
    private void handleCommServiceMessage(android.os.Message msg) {
        // Handle messages from communication service
        // This would process data and notify listeners
        switch (msg.what) {
            case MainActivity.MESSAGE_STATE_CHANGE:
                CommService.STATE state = (CommService.STATE) msg.obj;
                notifyConnectionStateChanged(state);
                
                // Update notification based on connection state
                String notificationText = getNotificationTextForState(state);
                updateNotification("OBD Service", notificationText);
                if (state == CommService.STATE.CONNECTED) {
                    ElmProt.runDemo = false;
                    mainHandler.removeCallbacks(protocolRefreshRunnable);
                    CommService.elm.reset();
                    attachExistingCoolantPid();
                } else if (state == CommService.STATE.OFFLINE || state == CommService.STATE.NONE) {
                    mainHandler.removeCallbacks(protocolRefreshRunnable);
                    CoolantAlertDispatcher.reset(vehicleAlertNotifier, "transport-offline");
                    scheduleReconnect();
                }
                break;
                
            case MainActivity.MESSAGE_DATA_ITEMS_CHANGED:
                // Process received data
                String data = msg.obj != null ? msg.obj.toString() : "";
                notifyDataReceived(data);
                break;
        }
    }
    
    private String getNotificationTextForState(CommService.STATE state) {
        switch (state) {
            case CONNECTING:
                return "Connecting to OBD device...";
            case CONNECTED:
                return getProtocolNotificationText(CommService.elm.getStatus());
            case LISTEN:
                return "Waiting for connection...";
            default:
                return "OBD service running";
        }
    }
    
    private void updateNotification(String title, String message) {
        Notification notification = createNotification(title, message);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    // Public methods for service control
    public void connectToBluetoothDevice(String address, CommService.MEDIUM medium, boolean secure) {
        if (medium != CommService.MEDIUM.BLUETOOTH && medium != CommService.MEDIUM.BLE) {
            return;
        }
        preferences.edit()
                .putString(BackgroundAutoConnectConfig.KEY_LAST_DEV_ADDRESS, address)
                .putBoolean(BackgroundAutoConnectConfig.KEY_BT_SECURE_CONNECTION, secure)
                .putString(BackgroundAutoConnectConfig.KEY_COMM_MEDIUM, String.valueOf(medium.ordinal()))
                .apply();
        connectToBluetoothDevice(address, medium, secure, false);
    }

    public void connectToSavedDevice() {
        BackgroundAutoConnectConfig config = readAutoConnectConfig();
        if (!config.canAutoConnect()) {
            log.info("Auto-connect skipped: no saved Bluetooth OBD adapter");
            updateNotification("OBD Service", "Waiting for saved Bluetooth OBD adapter");
            return;
        }
        log.info("Auto-connect attempting saved OBD adapter medium=" + config.medium + " address=" + config.address);
        connectToBluetoothDevice(config.address, config.medium, config.secure, true);
    }

    private void connectToSavedDeviceIfIdle(String reason) {
        BackgroundAutoConnectConfig config = readAutoConnectConfig();
        if (!config.canAutoConnect()) {
            return;
        }
        CommService.STATE state = commService != null ? commService.getState() : CommService.STATE.NONE;
        if (state == CommService.STATE.CONNECTED) {
            if (ObdConnectionHealth.shouldRefreshConnection(state, CommService.elm.getStatus())) {
                refreshSavedConnection(reason);
            }
            return;
        }
        if (state == CommService.STATE.CONNECTING) {
            return;
        }
        log.info("Auto-connect retry source=" + reason + " state=" + state);
        connectToBluetoothDevice(config.address, config.medium, config.secure, true);
    }

    private void connectToBluetoothDevice(
            String address,
            CommService.MEDIUM medium,
            boolean secure,
            boolean savedConnection) {
        autoReconnectEnabled = true;
        mainHandler.removeCallbacks(reconnectRunnable);
        CommService.medium = medium;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log.info("Auto-connect waiting: Bluetooth adapter is off or unavailable");
            updateNotification("OBD Service", "Waiting for Bluetooth to turn on");
            scheduleReconnect();
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            log.info("Auto-connect waiting: missing Bluetooth connect permission");
            updateNotification("OBD Service", "Waiting for Bluetooth permission");
            return;
        }

        if (commService != null && activeMedium != medium) {
            stopCommService();
        }
        if (commService == null) {
            initializeCommService();
        }
        if (commService == null) {
            return;
        }

        CommService.STATE state = commService.getState();
        if ((state == CommService.STATE.CONNECTED || state == CommService.STATE.CONNECTING)
                && address.equals(currentAddress)) {
            if (state == CommService.STATE.CONNECTING
                    || ObdConnectionHealth.isVehicleResponsive(state, CommService.elm.getStatus())) {
                return;
            }
            log.info("Refreshing stale OBD connection for saved adapter address=" + address
                    + " protocol=" + CommService.elm.getStatus());
            stopCommService();
            initializeCommService();
            if (commService == null) {
                return;
            }
            state = commService.getState();
        }
        if (state == CommService.STATE.CONNECTED || state == CommService.STATE.CONNECTING) {
            stopCommService();
            initializeCommService();
        }

        try {
            BluetoothDevice device = medium == CommService.MEDIUM.BLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                    ? adapter.getRemoteLeDevice(address, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
                    : adapter.getRemoteDevice(address);
            currentAddress = address;
            updateNotification(
                    "OBD Service",
                    savedConnection ? "Connecting to saved OBD adapter..." : "Connecting to OBD adapter...");
            log.info("Connecting to OBD adapter medium=" + medium + " address=" + address + " saved=" + savedConnection);
            commService.connect(device, secure);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid saved Bluetooth address: " + address);
            updateNotification("OBD Service", "Saved OBD adapter address is invalid");
        }
    }
    
    public void disconnect() {
        autoReconnectEnabled = false;
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.removeCallbacks(protocolRefreshRunnable);
        mainHandler.removeCallbacks(autoConnectWatchdogRunnable);
        if (commService != null) {
            commService.stop();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (foregroundStarted) {
            updateNotification("OBD Service", "Monitoring continues in background");
        }
        super.onTaskRemoved(rootIntent);
    }
    
    public ServiceState getCurrentState() {
        return currentState;
    }
    
    public CommService.STATE getConnectionState() {
        return commService != null ? commService.getState() : CommService.STATE.NONE;
    }
    
    // Listener management
    public void addStateListener(ServiceStateListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }
    
    public void removeStateListener(ServiceStateListener listener) {
        stateListeners.remove(listener);
    }
    
    private void notifyStateListeners() {
        mainHandler.post(() -> {
            for (ServiceStateListener listener : stateListeners) {
                listener.onServiceStateChanged(currentState);
            }
        });
    }
    
    private void notifyDataReceived(String data) {
        mainHandler.post(() -> {
            for (ServiceStateListener listener : stateListeners) {
                listener.onDataReceived(data);
            }
        });
    }
    
    private void notifyConnectionStateChanged(CommService.STATE state) {
        mainHandler.post(() -> {
            for (ServiceStateListener listener : stateListeners) {
                listener.onConnectionStateChanged(state);
            }
        });
    }

    private BackgroundAutoConnectConfig readAutoConnectConfig() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return BackgroundAutoConnectConfig.from(
                BackgroundAutoConnectConfig.sharedPreferencesReader(preferences));
    }

    private void scheduleReconnect() {
        if (!autoReconnectEnabled) {
            return;
        }
        mainHandler.removeCallbacks(reconnectRunnable);
        log.info("Auto-connect retry scheduled in " + RECONNECT_DELAY_MS + "ms");
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }

    private void scheduleProtocolRefresh(String reason) {
        if (!autoReconnectEnabled) {
            return;
        }
        mainHandler.removeCallbacks(protocolRefreshRunnable);
        log.info("Protocol refresh scheduled in " + PROTOCOL_REFRESH_DELAY_MS
                + "ms reason=" + reason + " status=" + CommService.elm.getStatus());
        mainHandler.postDelayed(protocolRefreshRunnable, PROTOCOL_REFRESH_DELAY_MS);
    }

    private void refreshSavedConnection(String reason) {
        if (!autoReconnectEnabled) {
            return;
        }
        BackgroundAutoConnectConfig config = readAutoConnectConfig();
        if (!config.canAutoConnect()) {
            return;
        }
        CommService.STATE state = commService != null ? commService.getState() : CommService.STATE.NONE;
        if (state == CommService.STATE.CONNECTING) {
            return;
        }
        if (state == CommService.STATE.CONNECTED
                && !ObdConnectionHealth.shouldRefreshConnection(state, CommService.elm.getStatus())) {
            return;
        }

        log.info("Refreshing OBD connection reason=" + reason
                + " state=" + state
                + " protocol=" + CommService.elm.getStatus());
        updateNotification("OBD Service", "Refreshing OBD connection...");
        CoolantAlertDispatcher.reset(vehicleAlertNotifier, "protocol-refresh");
        stopCommService();
        initializeCommService();
        connectToBluetoothDevice(config.address, config.medium, config.secure, true);
    }

    private void startAutoConnectWatchdog() {
        mainHandler.removeCallbacks(autoConnectWatchdogRunnable);
        mainHandler.postDelayed(autoConnectWatchdogRunnable, AUTO_CONNECT_WATCHDOG_MS);
    }

    private boolean requiresBluetoothPermission(CommService.MEDIUM medium) {
        return medium == CommService.MEDIUM.BLUETOOTH || medium == CommService.MEDIUM.BLE;
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null
                || AutomationSettings.KEY_COOLANT_MONITORING_ENABLED.equals(key)
                || AutomationSettings.KEY_COOLANT_WARNING_THRESHOLD.equals(key)
                || AutomationSettings.KEY_COOLANT_CRITICAL_THRESHOLD.equals(key)) {
            if (!automationSettings.isCoolantMonitoringEnabled()) {
                CoolantAlertDispatcher.reset(vehicleAlertNotifier, "service-preferences");
            } else {
                attachExistingCoolantPid();
                evaluateCurrentCoolantTemperature();
            }
        }
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        if (event.getSource() != ObdProt.PidPvs) {
            return;
        }
        if (event.getType() == PvChangeEvent.PV_ADDED && event.getValue() instanceof EcuDataPv) {
            attachCoolantListener((EcuDataPv) event.getValue());
            attachExistingCoolantPid();
        } else if (event.getType() == PvChangeEvent.PV_CLEARED) {
            detachCoolantListener();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (ElmProt.PROP_STATUS.equals(event.getPropertyName())) {
            handleProtocolStatus((ElmProt.STAT) event.getNewValue());
        }

        if (ElmProt.PROP_ECU_ADDRESS.equals(event.getPropertyName()) && stateListeners.isEmpty()) {
            restorePreferredEcu(event.getNewValue());
        } else if (ElmProt.PROP_STATUS.equals(event.getPropertyName())
                && event.getNewValue() == ElmProt.STAT.ECU_DETECTED
                && stateListeners.isEmpty()
                && CommService.elm.getService() == ObdProt.OBD_SVC_NONE) {
            CommService.elm.setService(ObdProt.OBD_SVC_DATA);
            attachExistingCoolantPid();
        } else if (ElmProt.PROP_STATUS.equals(event.getPropertyName())
                && event.getNewValue() == ElmProt.STAT.ECU_DETECTED) {
            attachExistingCoolantPid();
        }
    }

    private void handleProtocolStatus(ElmProt.STAT protocolState) {
        CommService.STATE transportState = commService != null
                ? commService.getState()
                : CommService.STATE.NONE;
        if (transportState != CommService.STATE.CONNECTED) {
            return;
        }

        updateNotification("OBD Service", getProtocolNotificationText(protocolState));
        if (ObdConnectionHealth.isVehicleResponsive(transportState, protocolState)) {
            mainHandler.removeCallbacks(protocolRefreshRunnable);
            return;
        }

        if (ObdConnectionHealth.shouldRefreshConnection(transportState, protocolState)) {
            CoolantAlertDispatcher.reset(vehicleAlertNotifier, "protocol-unavailable");
            scheduleProtocolRefresh("protocol-" + protocolState);
        }
    }

    private String getProtocolNotificationText(ElmProt.STAT protocolState) {
        if (ObdConnectionHealth.isVehicleResponsive(CommService.STATE.CONNECTED, protocolState)) {
            return "ECU connected - Monitoring vehicle data";
        }
        switch (protocolState) {
            case INITIALIZING:
            case INITIALIZED:
            case ECU_DETECT:
            case ECU_DETECTED:
            case CONNECTING:
                return "OBD adapter connected - detecting ECU...";

            case NODATA:
            case DISCONNECTED:
                return "OBD adapter connected - waiting for ignition/ECU";

            default:
                return "OBD adapter connected - ECU unavailable";
        }
    }

    private void restorePreferredEcu(Object value) {
        if (!(value instanceof Set)) {
            return;
        }
        int address = preferences.getInt(MainActivity.PRESELECT.LAST_ECU_ADDRESS.toString(), 0);
        if (((Set<?>) value).contains(address)) {
            CommService.elm.setEcuAddress(address);
        }
    }

    private void attachExistingCoolantPid() {
        for (Object value : new ArrayList<>(ObdProt.PidPvs.values())) {
            if (value instanceof EcuDataPv) {
                if (attachCoolantListener((EcuDataPv) value)) {
                    return;
                }
            }
        }
    }

    private boolean attachCoolantListener(EcuDataPv dataPv) {
        if (dataPv.getAsInt(EcuDataPv.FID_PID) != CoolantAlertDispatcher.COOLANT_PID) {
            return false;
        }
        if (coolantDataPv == dataPv) {
            evaluateCurrentCoolantTemperature();
            return true;
        }
        detachCoolantListener();
        coolantDataPv = dataPv;
        coolantDataPv.addPvChangeListener(coolantChangeListener, PvChangeEvent.PV_MODIFIED);
        log.info("Coolant monitor attached source=service pid=0x05");
        evaluateCurrentCoolantTemperature();
        return true;
    }

    private void detachCoolantListener() {
        if (coolantDataPv != null) {
            coolantDataPv.removePvChangeListener(coolantChangeListener);
            coolantDataPv = null;
            log.info("Coolant monitor detached source=service");
        }
    }

    private void evaluateCurrentCoolantTemperature() {
        if (coolantDataPv == null) {
            return;
        }
        Object value = coolantDataPv.get(EcuDataPv.FID_VALUE);
        if (value instanceof Number) {
            evaluateCoolantTemperature(((Number) value).doubleValue());
        }
    }

    private void evaluateCoolantTemperature(double temperature) {
        CoolantAlertDispatcher.evaluate(
                temperature,
                automationSettings,
                CommService.elm.getService(),
                vehicleEventLog,
                vehicleAlertNotifier,
                "service");
    }
}
