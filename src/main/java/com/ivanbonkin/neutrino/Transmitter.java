package com.ivanbonkin.neutrino;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class Transmitter {

    public static InetAddress[] getBroadcastAddresses() throws SocketException, UnknownHostException {
        List<InetAddress> broadcastList = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces
                = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            networkInterface.getInterfaceAddresses().stream()
                    .map(InterfaceAddress::getBroadcast)
                    .filter(Objects::nonNull)
                    .forEach(broadcastList::add);
        }
        broadcastList.add(InetAddress.getByName("127.0.0.255"));
        return broadcastList.toArray(new InetAddress[0]);
    }

    public void broadcast(byte[] broadcastMessage, InetAddress address) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        DatagramPacket packet = new DatagramPacket(broadcastMessage, broadcastMessage.length, address, 9600);
        socket.send(packet);
        socket.close();
    }
}
