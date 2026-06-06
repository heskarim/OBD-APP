package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DtcAutoClearPolicyTest
{
    @Test
    public void parsesAndNormalizesApprovedCodes()
    {
        Set<String> codes = DtcAutoClearPolicy.parseCodes(" p0420, P0441\nu0100 ");

        assertEquals(DtcAutoClearPolicy.parseCodes("P0420,P0441,U0100"), codes);
    }

    @Test
    public void allowsClearOnlyWhenEveryRealCodeIsApproved()
    {
        Set<String> approved = DtcAutoClearPolicy.parseCodes("P0420,P0441");

        assertEquals(
                DtcAutoClearPolicy.Decision.CLEAR_ALLOWED,
                DtcAutoClearPolicy.evaluate(Arrays.asList("P0420", "p0441"), approved));
        assertEquals(
                DtcAutoClearPolicy.Decision.UNKNOWN_CODES,
                DtcAutoClearPolicy.evaluate(Arrays.asList("P0420", "P0300"), approved));
    }

    @Test
    public void treatsSyntheticP0000AsNoCodes()
    {
        assertEquals(
                DtcAutoClearPolicy.Decision.NO_CODES,
                DtcAutoClearPolicy.evaluate(
                        Collections.singletonList("P0000"),
                        DtcAutoClearPolicy.parseCodes("P0000")));
    }

    @Test
    public void formatsCodesForPreferenceStorage()
    {
        assertEquals(
                "P0420, P0441",
                DtcAutoClearPolicy.formatCodes(Arrays.asList("p0441", "P0420", "P0420")));
    }

    @Test
    public void returnsOnlyUnknownRealCodes()
    {
        assertEquals(
                DtcAutoClearPolicy.parseCodes("P0300,U0100"),
                DtcAutoClearPolicy.unknownCodes(
                        Arrays.asList("P0000", "P0420", "P0300", "U0100"),
                        DtcAutoClearPolicy.parseCodes("P0420")));
    }
}
