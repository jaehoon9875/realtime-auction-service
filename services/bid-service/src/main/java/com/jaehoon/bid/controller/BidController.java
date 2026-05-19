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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PlaceBidRequest request) {
        UUID bidderId = UUID.fromString(jwt.getSubject());
        BidResponse response = bidService.placeBid(bidderId, request.auctionId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<BidResponse>> getMyBids(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "placedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID bidderId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(bidService.getMyBids(bidderId, pageable));
    }
}
