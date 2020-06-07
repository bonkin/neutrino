package com.ivanbonkin.neutrino;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.ivanbonkin.neutrino.DiodeParameters.MESSAGE_SIZE;
import static com.ivanbonkin.neutrino.DiodeParameters.SYMBOL_SIZE;
import static net.fec.openrq.decoder.SourceBlockState.DECODED;
import static net.fec.openrq.decoder.SourceBlockState.DECODING_FAILURE;

@Slf4j
@Service
public class Receiver {

    public static final int SYMBOL_OVERHEAD = 1;
    public static final double LOSS_RATIO = .1;

    ArrayDataDecoder decoder;
    volatile boolean abort = false, alreadySaved = false;
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

    private boolean parseHeader(Message<byte[]> message) {
        FECParameters headerFecParams = DiodeParameters.getParameters(SYMBOL_SIZE);
        ArrayDataDecoder headerDecoder = OpenRQ.newDecoder(headerFecParams, SYMBOL_OVERHEAD);
        Parsed<EncodingPacket> parsePacket = headerDecoder.parsePacket(message.getPayload(), false);
        if (parsePacket.isValid()) {
            SourceBlockDecoder blockDecoder = headerDecoder.sourceBlock(0);
            if (DECODED == blockDecoder.putEncodingPacket(parsePacket.value())) {
                byte[] dataArray = headerDecoder.dataArray();
                int padSize = 0;
                while (dataArray[padSize] == (byte)0) {
                    padSize++;
                }
                byte[] payload = new byte[dataArray.length - padSize];
                System.arraycopy(dataArray, padSize, payload, 0, payload.length);
                try {
                    Header header = new ObjectMapper().createParser(payload).readValueAs(Header.class);
                    log.info("Parsed header {}", header);
                    FECParameters bodyFecParams = DiodeParameters.getParameters(header.getBodySize());
                    decoder = OpenRQ.newDecoder(bodyFecParams, SYMBOL_OVERHEAD);
                    packNum = 0;
                    alreadySaved = false;
                    abort = false;
                    return true;
                } catch (IOException ignored) {
                }
            }
        }
        return false;
    }

    synchronized public void handleMessage(Message<byte[]> message) {

        if (parseHeader(message)) {
            return;
        }

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
                log.debug("SB #{} packet#={} type={} state={}", pkt.sourceBlockNumber(), packNum, pkt.symbolType(), decState);
                if (decState == DECODED) {
                    log.info("File decoded. Saving...");
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
                log.info("Received and saved file {}", file.getAbsolutePath());
                alreadySaved = true;

            } catch (IOException e) {
                log.error("Error saving file", e);
            }

        }


    }

}
