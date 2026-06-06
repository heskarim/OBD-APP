package com.fr3ts0n.ecu.gui.androbd;

import android.content.SharedPreferences;

final class BackgroundAutoConnectConfig
{
    static final String KEY_COMM_MEDIUM = "comm_medium";
    static final String KEY_LAST_DEV_ADDRESS = "LAST_DEV_ADDRESS";
    static final String KEY_BT_SECURE_CONNECTION = "bt_secure_connection";

    interface Reader
    {
        String getString(String key, String defaultValue);

        boolean getBoolean(String key, boolean defaultValue);
    }

    final CommService.MEDIUM medium;
    final String address;
    final boolean secure;

    private BackgroundAutoConnectConfig(CommService.MEDIUM medium, String address, boolean secure)
    {
        this.medium = medium;
        this.address = address;
        this.secure = secure;
    }

    static BackgroundAutoConnectConfig from(Reader reader)
    {
        CommService.MEDIUM medium = parseMedium(reader.getString(
                KEY_COMM_MEDIUM,
                String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal())));
        String address = reader.getString(KEY_LAST_DEV_ADDRESS, null);
        boolean secure = reader.getBoolean(KEY_BT_SECURE_CONNECTION, false);
        return new BackgroundAutoConnectConfig(medium, trimToNull(address), secure);
    }

    static Reader sharedPreferencesReader(SharedPreferences preferences)
    {
        return new Reader()
        {
            @Override
            public String getString(String key, String defaultValue)
            {
                return preferences.getString(key, defaultValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue)
            {
                return preferences.getBoolean(key, defaultValue);
            }
        };
    }

    boolean canAutoConnect()
    {
        return address != null
                && (medium == CommService.MEDIUM.BLUETOOTH || medium == CommService.MEDIUM.BLE);
    }

    private static CommService.MEDIUM parseMedium(String value)
    {
        try
        {
            int ordinal = Integer.parseInt(value);
            CommService.MEDIUM[] values = CommService.MEDIUM.values();
            if (ordinal >= 0 && ordinal < values.length)
            {
                return values[ordinal];
            }
        }
        catch (Exception ignored)
        {
            // Use the default below.
        }
        return CommService.MEDIUM.BLUETOOTH;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
