package com.jaehoon.streams.auction.service;

import static com.jaehoon.streams.auction.constants.StreamsConstants.STORE_AUCTION_METADATA;
import static com.jaehoon.streams.auction.constants.StreamsConstants.STORE_HIGHEST_BID;

import com.jaehoon.streams.auction.config.LocalInteractiveQueryHost;
import com.jaehoon.streams.auction.store.AuctionBidState;
import com.jaehoon.streams.auction.store.AuctionMetadata;
import com.jaehoon.streams.auction.web.dto.AuctionStatusResponse;
import com.jaehoon.streams.auction.web.dto.HighestBidResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Interactive Query: Kafka Streams State Store를 HTTP로 조회한다.
 * <p>
 * 스케일 아웃(동일 application-id 다수 프로세스)을 전제로 하며, 항상 아래 한 경로만 탄다.
 * {@code queryMetadataForKey}로 해당 키의 active 담당 호스트({@code application.server})를 구한 뒤,
 * 이 JVM과 같으면 로컬 {@code ReadOnlyKeyValueStore}를 읽고, 다르면 담당 peer에 동일 REST 경로로 위임한다.
 * 인스턴스가 1대뿐이면 담당 호스트는 항상 자기 자신이므로 위임 분기는 실행되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StateQueryService {

    private static final int STORE_RETRY = 3;
    private static final long STORE_RETRY_MS = 50L;

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;
    private final LocalInteractiveQueryHost localInteractiveQueryHost;
    private final RestClient restClient;

    /**
     * {@code auction-highest-bid} 스토어에서 경매별 최고 입찰 상태를 조회한다.
     * 담당 인스턴스가 원격이면 그쪽으로 HTTP 위임한다.
     */
    public HighestBidResponse getHighestBid(String auctionId) {
        String id = requireAuctionId(auctionId);
        KafkaStreams streams = requireRunningStreams();
        HostInfo owner = requireStoreOwner(streams, STORE_HIGHEST_BID, id);
        if (!localInteractiveQueryHost.isLocal(owner)) {
            return forwardHighestBid(owner, id);
        }
        AuctionBidState state = readWithRetry(streams, STORE_HIGHEST_BID, id);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 경매의 입찰 상태가 없습니다.");
        }
        return new HighestBidResponse(id, state.highestBid(), state.highestBidderId(), state.bidCount());
    }

    /**
     * {@code auction-metadata} 스토어에서 마감 시각 등을 읽어 경매 활성 여부를 반환한다.
     * 담당 인스턴스가 원격이면 그쪽으로 HTTP 위임한다. (최고가 스토어와 소스 토폴로지가 달라 담당 호스트가 다를 수 있다.)
     */
    public AuctionStatusResponse getStatus(String auctionId) {
        String id = requireAuctionId(auctionId);
        KafkaStreams streams = requireRunningStreams();
        HostInfo owner = requireStoreOwner(streams, STORE_AUCTION_METADATA, id);
        if (!localInteractiveQueryHost.isLocal(owner)) {
            return forwardStatus(owner, id);
        }
        AuctionMetadata metadata = readMetadataWithRetry(streams, id);
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 경매 메타데이터가 없습니다.");
        }
        boolean active = metadata.endsAt() > System.currentTimeMillis();
        return new AuctionStatusResponse(id, active, metadata.endsAt());
    }

    /** 경로 변수 {@code auctionId}가 비어 있지 않은지 검사한다. */
    private static String requireAuctionId(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "auctionId 가 필요합니다.");
        }
        return auctionId.trim();
    }

    /** IQ는 RUNNING 상태에서만 안전하므로, 그렇지 않으면 503으로 거절한다. */
    private KafkaStreams requireRunningStreams() {
        KafkaStreams streams = streamsBuilderFactoryBean.getKafkaStreams();
        if (streams == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka Streams 가 아직 초기화되지 않았습니다.");
        }
        if (streams.state() != KafkaStreams.State.RUNNING) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka Streams 상태가 RUNNING 이 아닙니다: " + streams.state());
        }
        return streams;
    }

    /**
     * 주어진 스토어·키에 대해 Kafka Streams가 배정한 active 담당 호스트({@link HostInfo})를 반환한다.
     * Kafka 4.x에서는 {@link KafkaStreams#queryMetadataForKey}와 {@link KeyQueryMetadata#activeHost()}를 사용한다.
     */
    private HostInfo requireStoreOwner(KafkaStreams streams, String storeName, String key) {
        KeyQueryMetadata meta = streams.queryMetadataForKey(storeName, key, Serdes.String().serializer());
        if (meta == null || meta == KeyQueryMetadata.NOT_AVAILABLE) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "스토어 키 라우팅 메타데이터를 조회할 수 없습니다: " + storeName);
        }
        HostInfo active = meta.activeHost();
        if (active == null || active.equals(HostInfo.unavailable())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "스토어 active 호스트가 아직 할당되지 않았습니다: " + storeName);
        }
        return active;
    }

    /**
     * 로컬 JVM이 담당하는 스토어 파티션에서 최고 입찰 상태를 읽는다. ({@code storeName}은 보통 {@code auction-highest-bid})
     * 리밸런스 직후 등 {@link InvalidStateStoreException}이 나면 짧게 재시도한다.
     */
    private AuctionBidState readWithRetry(KafkaStreams streams, String storeName, String key) {
        InvalidStateStoreException last = null;
        for (int i = 0; i < STORE_RETRY; i++) {
            try {
                ReadOnlyKeyValueStore<String, AuctionBidState> store = streams.store(
                        StoreQueryParameters.fromNameAndType(storeName, QueryableStoreTypes.keyValueStore())
                );
                return store.get(key);
            } catch (InvalidStateStoreException e) {
                last = e;
                sleepQuietly();
            }
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "State Store 일시적으로 조회할 수 없습니다. 잠시 후 다시 시도하세요.",
                Objects.requireNonNull(last));
    }

    /**
     * 로컬 JVM이 담당하는 {@code auction-metadata} 스토어에서 키를 읽는다.
     * 리밸런스 등으로 스토어가 잠시 조회 불가할 때 재시도한다.
     */
    private AuctionMetadata readMetadataWithRetry(KafkaStreams streams, String key) {
        InvalidStateStoreException last = null;
        for (int i = 0; i < STORE_RETRY; i++) {
            try {
                ReadOnlyKeyValueStore<String, AuctionMetadata> store = streams.store(
                        StoreQueryParameters.fromNameAndType(STORE_AUCTION_METADATA, QueryableStoreTypes.keyValueStore())
                );
                return store.get(key);
            } catch (InvalidStateStoreException e) {
                last = e;
                sleepQuietly();
            }
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "State Store 일시적으로 조회할 수 없습니다. 잠시 후 다시 시도하세요.",
                Objects.requireNonNull(last));
    }

    /** State Store 재시도 간격만큼 대기한다. 인터럽트 시 503을 던진다. */
    private void sleepQuietly() {
        try {
            Thread.sleep(STORE_RETRY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "조회가 중단되었습니다.");
        }
    }

    /**
     * 최고가 IQ를 담당 peer({@code owner}의 HTTP)에 그대로 위임한다. 멀티 인스턴스에서만 사용된다.
     */
    private HighestBidResponse forwardHighestBid(HostInfo owner, String auctionId) {
        String url = peerBaseUrl(owner) + "/state/auctions/{auctionId}/highest-bid";
        try {
            HighestBidResponse body = restClient.get()
                    .uri(url, auctionId)
                    .retrieve()
                    .body(HighestBidResponse.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "peer 응답 본문이 비어 있습니다.");
            }
            return body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 경매의 입찰 상태가 없습니다.", e);
            }
            throw new ResponseStatusException(e.getStatusCode(), "다른 인스턴스 IQ 조회 실패", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "다른 인스턴스 IQ 조회 실패", e);
        }
    }

    /**
     * 상태 IQ를 담당 peer에 그대로 위임한다. 멀티 인스턴스에서만 사용된다.
     */
    private AuctionStatusResponse forwardStatus(HostInfo owner, String auctionId) {
        String url = peerBaseUrl(owner) + "/state/auctions/{auctionId}/status";
        try {
            AuctionStatusResponse body = restClient.get()
                    .uri(url, auctionId)
                    .retrieve()
                    .body(AuctionStatusResponse.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "peer 응답 본문이 비어 있습니다.");
            }
            return body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 경매 메타데이터가 없습니다.", e);
            }
            throw new ResponseStatusException(e.getStatusCode(), "다른 인스턴스 IQ 조회 실패", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "다른 인스턴스 IQ 조회 실패", e);
        }
    }

    /** IQ peer 호출용 기본 URL({@code http://host:port})을 만든다. */
    private static String peerBaseUrl(HostInfo owner) {
        return "http://" + owner.host() + ":" + owner.port();
    }
}
