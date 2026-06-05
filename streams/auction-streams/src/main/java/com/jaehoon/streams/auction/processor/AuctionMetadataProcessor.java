package com.jaehoon.streams.auction.processor;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.auction.events.AuctionEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.store.AuctionBidState;
import com.jaehoon.streams.auction.store.AuctionMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AUCTION_CREATED 이벤트를 수신해 경매 메타데이터를 State Store에 저장하고,
 * 주기적으로 만료된 경매를 탐지해 AUCTION_CLOSED · AUCTION_WON 알림을 발행하는 프로세서.
 *
 * <p>Punctuator는 WALL_CLOCK_TIME 기준으로 동작한다. 입찰이 없어도 실제 시계로 마감 시각을 감지할 수 있다.
 * process()는 메타데이터 저장만 담당하며, 알림 발행은 Punctuator에서만 이루어진다.
 */
public class AuctionMetadataProcessor implements Processor<String, AuctionEvent, String, NotificationEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuctionMetadataProcessor.class);

    private final int intervalSeconds;

    private ProcessorContext<String, NotificationEvent> context;
    private KeyValueStore<String, AuctionMetadata> metadataStore;
    private KeyValueStore<String, AuctionBidState> bidStateStore;

    public AuctionMetadataProcessor(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * State Store 참조를 획득하고 경매 마감 감지용 Punctuator를 등록한다.
     *
     * @param context 다운스트림 forward 및 State Store 접근에 사용되는 프로세서 컨텍스트
     */
    @Override
    public void init(ProcessorContext<String, NotificationEvent> context) {
        this.context = context;
        this.metadataStore = context.getStateStore(STORE_AUCTION_METADATA);
        this.bidStateStore = context.getStateStore(STORE_HIGHEST_BID);
        // 실제 시계(wall clock) 기준으로 마감 경매 탐색
        context.schedule(Duration.ofSeconds(intervalSeconds), PunctuationType.WALL_CLOCK_TIME, this::checkExpiredAuctions);
    }

    /**
     * AUCTION_CREATED 이벤트에서 마감 판정에 필요한 메타데이터를 추출해 Store에 저장한다.
     * Punctuator가 endsAt 기준으로 만료 여부를 주기적으로 판정하므로, 이 시점에는 forward하지 않는다.
     *
     * @param record auctionId를 키로 갖는 AUCTION_CREATED 이벤트 레코드
     */
    @Override
    public void process(Record<String, AuctionEvent> record) {
        if (record.value() == null) return;
        AuctionEvent event = record.value();
        // AUCTION_CREATED 이벤트에서 마감 판정에 필요한 메타데이터 추출 후 저장
        metadataStore.put(event.getAuctionId(), new AuctionMetadata(
                event.getEndsAt(),
                event.getStartPrice(),
                event.getTitle()
        ));
    }

    // endsAt이 현재 시각보다 이전인 경매를 낙찰 처리하고 알림 이벤트를 발행한다.
    private void checkExpiredAuctions(long timestamp) {
        List<String> expired = new ArrayList<>();

        try (KeyValueIterator<String, AuctionMetadata> iterator = metadataStore.all()) {
            while (iterator.hasNext()) {
                KeyValue<String, AuctionMetadata> entry = iterator.next();
                if (entry.value.endsAt() < timestamp) {
                    try {
                        processExpiredAuction(entry.key, entry.value, timestamp);
                        expired.add(entry.key);
                    } catch (Exception e) {
                        // 한 경매 실패가 다른 경매 처리를 막지 않도록 예외를 삼키고 다음 주기에 재시도
                        log.error("경매 마감 처리 실패, 다음 주기에 재시도. auctionId: {}", entry.key, e);
                    }
                }
            }
        }

        // 이터레이터 닫힌 후 삭제 (반복 중 수정 방지)
        expired.forEach(metadataStore::delete);
    }

    private void processExpiredAuction(String auctionId, AuctionMetadata metadata, long timestamp) {
        AuctionBidState bidState = bidStateStore.get(auctionId);

        // 낙찰자가 있을 때만 AUCTION_WON 발행
        if (bidState != null && bidState.highestBidderId() != null) {
            context.forward(new Record<>(auctionId, buildAuctionWonEvent(auctionId, bidState, metadata, timestamp), timestamp));
        }

        context.forward(new Record<>(auctionId, buildAuctionClosedEvent(auctionId, metadata, timestamp), timestamp));

        // 마감된 경매의 최고가 상태는 더 이상 필요 없으므로 함께 제거한다.
        // metadata만 삭제하면 bidState가 영구 잔류해 RocksDB·changelog가 무한 증가한다.
        // (두 스토어는 auctionId로 co-partition 되어 이 태스크가 둘 다 소유하므로 안전)
        bidStateStore.delete(auctionId);
    }

    private NotificationEvent buildAuctionWonEvent(String auctionId, AuctionBidState bidState, AuctionMetadata metadata, long timestamp) {
        return NotificationEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setNotificationType(NOTIFICATION_AUCTION_WON)
                .setTargetUserId(bidState.highestBidderId())
                .setTargetAuctionId(null)
                .setAuctionId(auctionId)
                .setPayload(Map.of(
                        "highestBid", String.valueOf(bidState.highestBid()),
                        "title", metadata.title()
                ))
                .setOccurredAt(timestamp)
                .build();
    }

    private NotificationEvent buildAuctionClosedEvent(String auctionId, AuctionMetadata metadata, long timestamp) {
        // 경매 구독자 전체 대상 브로드캐스트 — notification-service가 targetAuctionId로 구독자 라우팅
        return NotificationEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setNotificationType(NOTIFICATION_AUCTION_CLOSED)
                .setTargetUserId(null)
                .setTargetAuctionId(auctionId)
                .setAuctionId(auctionId)
                .setPayload(Map.of("title", metadata.title()))
                .setOccurredAt(timestamp)
                .build();
    }
}
