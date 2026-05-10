package com.jaehoon.streams.auction.processor;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.store.AuctionBidState;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Map;
import java.util.UUID;

/**
 * BID_PLACED 이벤트를 받아 auction-highest-bid State Store를 갱신하고,
 * 이전 최고 입찰자가 있으면 OUTBID 알림을 발행한다.
 */
public class BidStateProcessor implements Processor<String, BidEvent, String, NotificationEvent> {

    private ProcessorContext<String, NotificationEvent> context;
    private KeyValueStore<String, AuctionBidState> bidStateStore;

    @Override
    public void init(ProcessorContext<String, NotificationEvent> context) {
        this.context = context;
        this.bidStateStore = context.getStateStore(STORE_HIGHEST_BID);
    }

    @Override
    public void process(Record<String, BidEvent> record) {
        if (record.value() == null) return;

        BidEvent event = record.value();
        String auctionId = event.getAuctionId();
        AuctionBidState current = bidStateStore.get(auctionId);

        long currentHighest = current != null ? current.highestBid() : 0L;
        // 현재 최고가 이하 입찰은 무시 (Bid Service에서 이미 검증하지만 방어적 처리)
        if (event.getAmount() <= currentHighest) return;

        // 이전 최고 입찰자가 있으면 OUTBID 알림 발행
        if (current != null && current.highestBidderId() != null) {
            context.forward(new Record<>(
                    auctionId,
                    buildOutbidEvent(auctionId, event, current, record.timestamp()),
                    record.timestamp()
            ));
        }

        // 새 최고 입찰 상태 갱신
        int newBidCount = current != null ? current.bidCount() + 1 : 1;
        bidStateStore.put(auctionId, new AuctionBidState(event.getAmount(), event.getBidderId(), newBidCount));
    }

    private NotificationEvent buildOutbidEvent(String auctionId, BidEvent newBid, AuctionBidState prev, long timestamp) {
        return NotificationEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setNotificationType(NOTIFICATION_OUTBID)
                .setTargetUserId(prev.highestBidderId())
                .setTargetAuctionId(null)
                .setAuctionId(auctionId)
                .setPayload(Map.of(
                        "newHighestBid", String.valueOf(newBid.getAmount()),
                        "newBidderId", newBid.getBidderId()
                ))
                .setOccurredAt(timestamp)
                .build();
    }
}
