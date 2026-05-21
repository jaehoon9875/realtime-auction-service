package com.jaehoon.streams.auction.config;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.streams.auction.store.AuctionBidState;
import com.jaehoon.streams.auction.store.AuctionMetadata;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Kafka Streams State Store를 토폴로지에 등록하는 설정 클래스.
 * 
 */
@Configuration
@RequiredArgsConstructor
public class StateStoreConfig {

    private final StreamsBuilder builder;
    private final Serde<AuctionMetadata> auctionMetadataSerde;
    private final Serde<AuctionBidState> auctionBidStateSerde;

    /**
     * Kafka Streams State Store를 토폴로지에 등록하는 메서드.
     */
    @PostConstruct
    public void registerStateStores() {
        // auction-metadata store: AuctionMetadataProcessor가 쓰기·읽기 담당
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(STORE_AUCTION_METADATA),
                Serdes.String(),
                auctionMetadataSerde));

        // auction-highest-bid store: BidStateProcessor가 쓰기, AuctionMetadataProcessor가
        // 읽기 전용으로 참조
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(STORE_HIGHEST_BID),
                Serdes.String(),
                auctionBidStateSerde));
    }
}
