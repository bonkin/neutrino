package com.ivanbonkin.demo;

import lombok.extern.slf4j.Slf4j;
import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.decoder.SourceBlockState;
import net.fec.openrq.parameters.FECParameters;
import org.apache.commons.io.FileUtils;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

import static com.ivanbonkin.demo.DiodeParameters.MESSAGE_SIZE;
import static net.fec.openrq.decoder.SourceBlockState.DECODED;
import static net.fec.openrq.decoder.SourceBlockState.DECODING_FAILURE;

@Slf4j
@Service
public class Receiver {

    public static final int SYMBOL_OVERHEAD = 1;
    public static final double LOSS_RATIO = .1;

    ArrayDataDecoder decoder;
    boolean abort = false, alreadySaved = false;
    Parsed<EncodingPacket> latestParse;
    SourceBlockDecoder latestBlockDecoder;
    EncodingPacket pkt;
    SourceBlockState decState;
    int packNum;

    public void reset() {
        FECParameters fecParams = DiodeParameters.getParameters(MESSAGE_SIZE);
        decoder = OpenRQ.newDecoder(fecParams, SYMBOL_OVERHEAD);
        packNum = 0;
    }

    synchronized public void handleMessage(Message<byte[]> message) {

        byte[] data = message.getPayload();

        if (!decoder.isDataDecoded() && !abort) {
            latestParse = decoder.parsePacket(data, false);
            if (!latestParse.isValid()) {
                abort = true;

            } else {
                pkt = latestParse.value();
                latestBlockDecoder = decoder.sourceBlock(pkt.sourceBlockNumber());
                decState = latestBlockDecoder.putEncodingPacket(pkt);
                if (decState == DECODING_FAILURE) {
                    log.warn("Decoding failure occurred");
                    abort = true;
                }
                log.info("SB #{} packet#={} type={} state={}", pkt.sourceBlockNumber(), packNum, pkt.symbolType(), decState);
                if (decState == DECODED) {
                    System.out.println("File decoded. Saving...");
                }
            }

            packNum++;
        }
        if (decState == DECODED && !alreadySaved) {

            byte[] dataArray = decoder.dataArray();

            int fileNameLength = 0;
            for (int i = 0; i < dataArray.length; i++) {
                if (dataArray[i] == '/') {
                    fileNameLength = i;
                    break;
                }
            }
            if (fileNameLength == 0) {
                throw new IllegalStateException("File name was not recognized");
            }

            File file = new File("out", new String(dataArray, 0, fileNameLength++));
            try {
                if (!file.createNewFile()) {
                    if (!file.delete()) {
                        log.warn("Can not delete file");
                    }
                }
                FileUtils.writeByteArrayToFile(file, dataArray, fileNameLength, dataArray.length - fileNameLength);
                System.out.println("Written file " + file.getAbsolutePath());
                alreadySaved = true;

            } catch (IOException e) {
                log.error("Error saving file", e);
            }

        }


    }

}
