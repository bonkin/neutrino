package com.ivanbonkin.demo;


import com.google.common.util.concurrent.RateLimiter;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import static com.ivanbonkin.demo.DiodeParameters.MESSAGE_SIZE;
import static com.ivanbonkin.demo.Receiver.LOSS_RATIO;
import static com.ivanbonkin.demo.Receiver.SYMBOL_OVERHEAD;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Push
@Route
public class MainView extends VerticalLayout {

    Receiver receiver;

    final UnicastSendingMessageHandler handler = new UnicastSendingMessageHandler("localhost", 9600);

    final RateLimiter rateLimiter = RateLimiter.create(100_000_000. / (1 << 16));

    public MainView(@Autowired Receiver receiver) {
        this.receiver = receiver;

        add(new Button("Send Spring", (ComponentEventListener<ClickEvent<Button>>) event -> {
            String payload = "Hello world";
            handler.handleMessage(MessageBuilder.withPayload(payload).build());
        }));


        add(new Button("Send JDK", (ComponentEventListener<ClickEvent<Button>>) event -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);

                byte[] bytes = "Hello There".getBytes(UTF_8);

                DatagramPacket packet = new DatagramPacket(
                        bytes,
                        bytes.length,
                        InetAddress.getByName("127.0.0.1"),
                        9600);

                socket.send(packet);
                socket.close();
            } catch (IOException e) {
                log.error("Send error", e);
            }
        }));

        ProgressBar progressBar = new ProgressBar();
        add(progressBar);

        add(new Text("Welcome to MainView."));

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.addSucceededListener(event -> {

            log.info(event.getMIMEType());
            log.info(event.getFileName());
            log.info("Size: " + humanReadableByteCountBin(event.getContentLength()));

            try {
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

                new Thread(() -> pushUdp(dataEnc, progressBar)).start();
                log.debug("Thread started...");

            } catch (IOException e) {
                log.error("Send error", e);
            }
        });
        add(upload);

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


