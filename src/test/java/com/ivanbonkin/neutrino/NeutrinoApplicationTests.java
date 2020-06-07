package com.ivanbonkin.neutrino;

import lombok.extern.slf4j.Slf4j;
import net.fec.openrq.*;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.decoder.SourceBlockState;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Slf4j
@SpringBootTest
class NeutrinoApplicationTests {

    public static final int R = 1000;

    @Test
    public void corruptByte() {
        int numSrcBlks = 2;
        int dataLen = 16 * R;
        int symbSize = 4;
        int symbolOverhead = 1;
        double loss = .6;
        FECParameters fecParams = FECParameters.newParameters(dataLen, symbSize, numSrcBlks);

        final byte[] data = new byte[fecParams.dataLengthAsInt()];
        Arrays.fill(data, (byte) 77);

        final ArrayDataEncoder enc = OpenRQ.newEncoder(data, fecParams);

        ByteBuffer bb = ByteBuffer.allocate(5000 * R); // something plenty big

        for (SourceBlockEncoder sbEnc : enc.sourceBlockIterable()) {
            for (EncodingPacket encodingPacketSource : sbEnc.sourcePacketsIterable()) {
                encodingPacketSource.writeTo(bb);
            }

            int numRepairSymbols = OpenRQ.minRepairSymbols(sbEnc.numberOfSourceSymbols(), symbolOverhead, loss);
            if (numRepairSymbols > 0) {
                for (EncodingPacket encodingPacketRepair : sbEnc.repairPacketsIterable(numRepairSymbols)) {
                    encodingPacketRepair.writeTo(bb);
                }
            }
        }

        bb.flip();

        ArrayDataDecoder decoder = OpenRQ.newDecoder(fecParams, symbolOverhead);

        SourceBlockDecoder latestBlockDecoder;
        Parsed<EncodingPacket> latestParse;
        EncodingPacket pkt;
        SourceBlockState decState;
        int packNum = 0;
        boolean abort = false;
        while (bb.hasRemaining() && !decoder.isDataDecoded() && !abort) {
            latestParse = decoder.parsePacket(bb, false);
            if (!latestParse.isValid()) {
                abort = true;

            } else {
                pkt = latestParse.value();
                latestBlockDecoder = decoder.sourceBlock(pkt.sourceBlockNumber());
                decState = latestBlockDecoder.putEncodingPacket(pkt);
                if (decState.equals(SourceBlockState.DECODING_FAILURE))
                    abort = true;
                log.info("SB # " + pkt.sourceBlockNumber() + " packet#=" + packNum
                        + " type=" + pkt.symbolType() + " state = " + decState);
            }

            packNum++;
        }

        byte[] dataArray = decoder.dataArray();

        Assertions.assertThat(dataArray).isEqualTo(data);
    }

}
