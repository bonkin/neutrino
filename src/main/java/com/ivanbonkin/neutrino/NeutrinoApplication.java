package com.ivanbonkin.neutrino;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.ip.udp.UnicastReceivingChannelAdapter;

@SpringBootApplication
public class NeutrinoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeutrinoApplication.class, args);
    }

    @Bean
    public IntegrationFlow processUniCastUdpMessage() {
        return IntegrationFlows
                .from(new UnicastReceivingChannelAdapter(9600))
                .handle("receiver", "handleMessage")
                .get();
    }

}
