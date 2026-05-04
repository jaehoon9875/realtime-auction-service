package com.jaehoon.auction.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.dto.UpdateAuctionStatusRequest;
import com.jaehoon.auction.service.AuctionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 경매 REST API.
 * JWT 검증은 API Gateway 에서 수행하며, auction-service 는 X-User-Id 헤더로 현재 사용자를 식별한다.
 * 내부 시크릿 헤더 검증은 SecurityConfig / InternalRequestTokenFilter 에서 처리한다.
 */
@RestController
@RequestMapping("/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    /** POST /auctions — 경매 생성 (X-User-Id 필수) */
    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @RequestHeader("X-User-Id") UUID sellerId,
            @RequestBody @Valid CreateAuctionRequest request
    ) {
        AuctionResponse response = auctionService.createAuction(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /auctions/{id} — 단건 조회 (인증 불필요, currentPrice 는 State Store 에서 조회) */
    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(auctionService.getAuction(id));
    }

    /** GET /auctions — 목록 조회 (인증 불필요, 페이징) */
    @GetMapping
    public ResponseEntity<Page<AuctionResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auctionService.getAuctions(pageable));
    }

    /** PATCH /auctions/{id}/status — 상태 변경 (내부 API, X-User-Id 선택) */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AuctionResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateAuctionStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID requesterId
    ) {
        return ResponseEntity.ok(auctionService.updateStatus(id, request.status(), requesterId));
    }
}
