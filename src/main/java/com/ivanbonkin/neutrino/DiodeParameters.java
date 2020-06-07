package com.ivanbonkin.neutrino;

import net.fec.openrq.parameters.FECParameters;

import static net.fec.openrq.parameters.ParameterChecker.*;

public class DiodeParameters {

    // Fixed value for the symbol size
    public static final int SYMBOL_SIZE = 1500 - 20 - 8; // UDP-Ipv4 payload length

    // The maximum allowed data length, given the parameter above
    public static final long MAX_DATA_LEN = maxAllowedDataLength(SYMBOL_SIZE);

    public static FECParameters getParameters(long dataLen) {

        if (dataLen < minDataLength())
            throw new IllegalArgumentException("data length is too small");
        if (dataLen > MAX_DATA_LEN)
            throw new IllegalArgumentException("data length is too large");

        int numSBs = minAllowedNumSourceBlocks(dataLen, SYMBOL_SIZE);
        return FECParameters.newParameters(dataLen, SYMBOL_SIZE, numSBs);
    }

    public static volatile int MESSAGE_SIZE;

}
