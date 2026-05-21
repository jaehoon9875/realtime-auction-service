package com.jaehoon.auction.controller;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.dto.UpdateAuctionStatusRequest;
import com.jaehoon.auction.service.AuctionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 경매 REST API.
 * JWT 검증은 OAuth2 Resource Server(JWKS 기반)가 처리하며, principal 은 {@link Jwt} 타입으로 주입된다.
 * 내부 시크릿 헤더 검증은 InternalRequestTokenFilter 에서 처리한다.
 */
@Tag(name = "Auction", description = "경매 생성·조회·상태 관리 API")
@RestController
@RequestMapping("/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @Operation(summary = "경매 생성", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "201", description = "경매 생성 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 시작·마감 시각 불일치")
    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateAuctionRequest request
    ) {
        UUID sellerId = UUID.fromString(jwt.getSubject());
        AuctionResponse response = auctionService.createAuction(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "경매 단건 조회")
    @ApiResponse(responseCode = "200", description = "경매 정보 반환 (currentPrice는 State Store 조회)")
    @ApiResponse(responseCode = "404", description = "경매 없음")
    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(auctionService.getAuction(id));
    }

    @Operation(summary = "경매 목록 조회")
    @ApiResponse(responseCode = "200", description = "페이징된 경매 목록 반환")
    @GetMapping
    public ResponseEntity<Page<AuctionResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auctionService.getAuctions(pageable));
    }

    @Operation(
            summary = "경매 상태 변경",
            description = "내부 API. X-Internal-Request-Token 헤더 필요.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "상태 변경 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패")
    @ApiResponse(responseCode = "403", description = "판매자 본인이 아님")
    @ApiResponse(responseCode = "404", description = "경매 없음")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AuctionResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateAuctionStatusRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 시스템 내부 호출(비인증)이면 null → AuctionService에서 권한 검증 생략
        UUID requesterId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(auctionService.updateStatus(id, request.status(), requesterId));
    }
}
