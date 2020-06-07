package com.ivanbonkin.neutrino;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.SucceededEvent;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.ip.udp.UnicastSendingMessageHandler;
import org.springframework.integration.support.MessageBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import static com.ivanbonkin.neutrino.DiodeParameters.MESSAGE_SIZE;
import static com.ivanbonkin.neutrino.DiodeParameters.SYMBOL_SIZE;
import static com.ivanbonkin.neutrino.Receiver.LOSS_RATIO;
import static com.ivanbonkin.neutrino.Receiver.SYMBOL_OVERHEAD;
import static java.util.Objects.requireNonNull;

@Slf4j
@UIScope
@SpringComponent
public class TxView extends Div {

    static final UnicastSendingMessageHandler handler = new UnicastSendingMessageHandler("localhost", 9600);

    @SuppressWarnings("UnstableApiUsage")
    static final RateLimiter rateLimiter = RateLimiter.create(20_000_000. / (1 << 16));

    public TxView(@Autowired Receiver receiver, @Autowired ProgressView progressView) {

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.addSucceededListener((SucceededEvent event) -> {

            log.info(event.getMIMEType());
            log.info(event.getFileName());
            log.info("Size: " + humanReadableByteCountBin(event.getContentLength()));


            try {
                sendHeader(new Header(event.getContentLength(), event.getFileName(), event.getMIMEType()));
                Enumeration<InputStream> enumeration = Collections.enumeration(Arrays.asList(
                        new ByteArrayInputStream((buffer.getFileData().getFileName() + "/").getBytes()),
                        buffer.getInputStream()
                ));
                InputStream is = new SequenceInputStream(enumeration);
                byte[] bytes = IOUtils.toByteArray(is);
                FECParameters parameters = DiodeParameters.getParameters(bytes.length);
                ArrayDataEncoder dataEnc = OpenRQ.newEncoder(bytes, parameters);

                MESSAGE_SIZE = bytes.length;
                receiver.reset();

                new Thread(() -> pushUdp(dataEnc, progressView)).start();
                log.debug("Thread started...");

            } catch (IOException e) {
                log.error("Send error", e);
            }
        });
        add(upload);
    }

    private void sendHeader(Header header) throws JsonProcessingException {
        int msgSize = SYMBOL_SIZE;
        byte[] headerBytes = new ObjectMapper().writeValueAsBytes(header);
        byte[] paddedBytes = new byte[msgSize];
        System.arraycopy(requireNonNull(headerBytes), 0, paddedBytes, msgSize - headerBytes.length, headerBytes.length);

        FECParameters parameters = DiodeParameters.getParameters(msgSize);
        ArrayDataEncoder dataEnc = OpenRQ.newEncoder(paddedBytes, parameters);

        final int numSourceBlocks = dataEnc.numberOfSourceBlocks();
        for (int sb = 0; sb < numSourceBlocks; sb++) {
            SourceBlockEncoder sbEnc = dataEnc.sourceBlock(sb);

            // send all source symbols
            final int numSourceSymbols = sbEnc.numberOfSourceSymbols();
            final int numRepairSymbols = OpenRQ.minRepairSymbols(numSourceSymbols, SYMBOL_OVERHEAD, LOSS_RATIO);

            for (int ss = 0; ss < numSourceSymbols; ss++) {
                EncodingPacket packet = sbEnc.sourcePacket(ss);
                handler.handleMessage(MessageBuilder.withPayload(packet.asArray()).build());
            }
            // send nr repair symbols
            for (EncodingPacket pac : sbEnc.repairPacketsIterable(numRepairSymbols)) {
                handler.handleMessage(MessageBuilder.withPayload(pac.asArray()).build());
            }
        }


    }

    void pushUdp(ArrayDataEncoder dataEnc, ProgressBar progressBar) {
        final int numSourceBlocks = dataEnc.numberOfSourceBlocks();
        for (int sb = 0; sb < numSourceBlocks; sb++) {
            encodeSourceBlock(dataEnc.sourceBlock(sb), progressBar);
        }
    }

    void encodeSourceBlock(SourceBlockEncoder sbEnc, ProgressBar progressBar) {

        // send all source symbols
        final int numSourceSymbols = sbEnc.numberOfSourceSymbols();
        final int numRepairSymbols = OpenRQ.minRepairSymbols(numSourceSymbols, SYMBOL_OVERHEAD, LOSS_RATIO);

        for (int ss = 0; ss < numSourceSymbols; ss++) {
            final double pbValue = ss;
            getUI().ifPresent(ui -> ui.access(() ->
                    progressBar.setValue(pbValue / numSourceSymbols)
            ));
            rateLimiter.acquire();
            EncodingPacket packet = sbEnc.sourcePacket(ss);
            handler.handleMessage(MessageBuilder.withPayload(packet.asArray()).build());
        }

        // send nr repair symbols
        for (EncodingPacket pac : sbEnc.repairPacketsIterable(numRepairSymbols)) {
            rateLimiter.acquire();
            handler.handleMessage(MessageBuilder.withPayload(pac.asArray()).build());
        }
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

}


