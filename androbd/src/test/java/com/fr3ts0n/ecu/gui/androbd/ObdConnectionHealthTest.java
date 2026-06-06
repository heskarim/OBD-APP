package com.fr3ts0n.ecu.gui.androbd;

import com.fr3ts0n.ecu.prot.obd.ElmProt;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObdConnectionHealthTest
{
    @Test
    public void socketConnectedIsUnhealthyWhenProtocolIsDisconnected()
    {
        assertFalse(ObdConnectionHealth.isVehicleResponsive(
                CommService.STATE.CONNECTED,
                ElmProt.STAT.DISCONNECTED));
    }

    @Test
    public void socketConnectedIsUnhealthyWhenProtocolHasNoData()
    {
        assertFalse(ObdConnectionHealth.isVehicleResponsive(
                CommService.STATE.CONNECTED,
                ElmProt.STAT.NODATA));
    }

    @Test
    public void socketConnectedIsHealthyWhenProtocolIsConnected()
    {
        assertTrue(ObdConnectionHealth.isVehicleResponsive(
                CommService.STATE.CONNECTED,
                ElmProt.STAT.CONNECTED));
    }

    @Test
    public void connectedTransportWithBadProtocolNeedsReconnect()
    {
        assertTrue(ObdConnectionHealth.shouldRefreshConnection(
                CommService.STATE.CONNECTED,
                ElmProt.STAT.NODATA));
    }
}
