package com.jaehoon.bid.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.dto.PlaceBidRequest;
import com.jaehoon.bid.service.BidService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Bid", description = "입찰 요청 및 조회 API")
@RestController
@RequestMapping("/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @Operation(summary = "입찰 요청", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "201", description = "입찰 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 입찰 (경매 상태, 마감, 입찰가)")
    @ApiResponse(responseCode = "404", description = "경매를 찾을 수 없음")
    @ApiResponse(responseCode = "503", description = "auction-service 또는 auction-streams 장애")
    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PlaceBidRequest request) {
        UUID bidderId = UUID.fromString(jwt.getSubject());
        BidResponse response = bidService.placeBid(bidderId, request.auctionId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "내 입찰 내역 조회", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "입찰 내역 반환")
    @GetMapping("/me")
    public ResponseEntity<Page<BidResponse>> getMyBids(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "placedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID bidderId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(bidService.getMyBids(bidderId, pageable));
    }
}
