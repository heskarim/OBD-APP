package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundAutoConnectConfigTest
{
    @Test
    public void connectsToLastClassicBluetoothDevice()
    {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("comm_medium", String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal()));
        prefs.put("LAST_DEV_ADDRESS", "00:11:22:33:44:55");
        prefs.put("bt_secure_connection", true);

        BackgroundAutoConnectConfig config = BackgroundAutoConnectConfig.from(new MapReader(prefs));

        assertTrue(config.canAutoConnect());
        assertEquals(CommService.MEDIUM.BLUETOOTH, config.medium);
        assertEquals("00:11:22:33:44:55", config.address);
        assertTrue(config.secure);
    }

    @Test
    public void skipsAutoConnectWithoutSavedAddress()
    {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("comm_medium", String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal()));

        BackgroundAutoConnectConfig config = BackgroundAutoConnectConfig.from(new MapReader(prefs));

        assertFalse(config.canAutoConnect());
    }

    @Test
    public void skipsAutoConnectForDemoMedium()
    {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("comm_medium", String.valueOf(CommService.MEDIUM.DEMO.ordinal()));
        prefs.put("LAST_DEV_ADDRESS", "00:11:22:33:44:55");

        BackgroundAutoConnectConfig config = BackgroundAutoConnectConfig.from(new MapReader(prefs));

        assertFalse(config.canAutoConnect());
    }

    private static final class MapReader implements BackgroundAutoConnectConfig.Reader
    {
        private final Map<String, Object> values;

        MapReader(Map<String, Object> values)
        {
            this.values = values;
        }

        @Override
        public String getString(String key, String defaultValue)
        {
            Object value = values.get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue)
        {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
    }
}
