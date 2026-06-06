package com.fr3ts0n.ecu.gui.androbd;

import com.fr3ts0n.ecu.prot.obd.ElmProt;

final class ObdConnectionHealth
{
    private ObdConnectionHealth()
    {
    }

    static boolean isVehicleResponsive(CommService.STATE transportState, ElmProt.STAT protocolState)
    {
        return transportState == CommService.STATE.CONNECTED
                && protocolState == ElmProt.STAT.CONNECTED;
    }

    static boolean shouldRefreshConnection(CommService.STATE transportState, ElmProt.STAT protocolState)
    {
        if (transportState != CommService.STATE.CONNECTED)
        {
            return false;
        }

        switch (protocolState)
        {
            case NODATA:
            case DISCONNECTED:
            case BUSERROR:
            case DATAERROR:
            case RXERROR:
            case ERROR:
                return true;

            default:
                return false;
        }
    }
}
