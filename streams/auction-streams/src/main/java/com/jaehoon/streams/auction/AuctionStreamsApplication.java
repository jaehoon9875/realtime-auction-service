package com.jaehoon.streams.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class AuctionStreamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuctionStreamsApplication.class, args);
    }
}
