package com.fr3ts0n.ecu.gui.androbd;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class DtcAutoClearPolicy
{
    enum Decision
    {
        NO_CODES,
        CLEAR_ALLOWED,
        UNKNOWN_CODES
    }

    private DtcAutoClearPolicy()
    {
    }

    static Set<String> parseCodes(String value)
    {
        TreeSet<String> result = new TreeSet<>();
        if (value == null)
        {
            return result;
        }

        for (String code : value.split("[,\\s]+"))
        {
            String normalized = normalizeCode(code);
            if (!normalized.isEmpty())
            {
                result.add(normalized);
            }
        }
        return result;
    }

    static String formatCodes(Collection<String> codes)
    {
        TreeSet<String> normalized = new TreeSet<>();
        if (codes != null)
        {
            for (String code : codes)
            {
                String value = normalizeCode(code);
                if (!value.isEmpty())
                {
                    normalized.add(value);
                }
            }
        }
        return String.join(", ", normalized);
    }

    static Decision evaluate(Collection<String> detectedCodes, Set<String> approvedCodes)
    {
        Set<String> detected = realCodes(detectedCodes);

        if (detected.isEmpty())
        {
            return Decision.NO_CODES;
        }

        return approvedCodes != null && approvedCodes.containsAll(detected)
                ? Decision.CLEAR_ALLOWED
                : Decision.UNKNOWN_CODES;
    }

    static Set<String> unknownCodes(Collection<String> detectedCodes, Set<String> approvedCodes)
    {
        TreeSet<String> result = new TreeSet<>(realCodes(detectedCodes));
        if (approvedCodes != null)
        {
            result.removeAll(approvedCodes);
        }
        return result;
    }

    private static Set<String> realCodes(Collection<String> detectedCodes)
    {
        TreeSet<String> result = new TreeSet<>();
        if (detectedCodes != null)
        {
            for (String code : detectedCodes)
            {
                String normalized = normalizeCode(code);
                if (!normalized.isEmpty() && !"P0000".equals(normalized))
                {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private static String normalizeCode(String code)
    {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
