# API 명세

## 공통

### Base URL
```
http://localhost:8080/api/v1   # 로컬
https://{domain}/api/v1        # 운영
```

### 인증
JWT Bearer Token 방식. 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더 필요.

### 공통 에러 응답
```json
{
  "code": "BID_AMOUNT_TOO_LOW",
  "message": "입찰 금액이 현재 최고가보다 낮습니다.",
  "timestamp": "2024-04-28T15:30:00Z"
}
```

| 에러 코드 | 설명 |
|-----------|------|
| AUCTION_NOT_FOUND | 경매를 찾을 수 없음 |
| AUCTION_ALREADY_CLOSED | 이미 마감된 경매 |
| BID_AMOUNT_TOO_LOW | 입찰 금액이 현재 최고가 이하 |
| BID_ON_OWN_AUCTION | 본인 경매에 입찰 시도 |
| UNAUTHORIZED | 인증 필요 |
| TOKEN_EXPIRED | 토큰 만료 |

---

## User Service

### POST /api/v1/users/register
회원가입

**Request**
```json
{
  "email": "user@example.com",
  "password": "password1234",
  "nickname": "재훈"
}
```

**Response** `201 Created`
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "nickname": "재훈",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

### POST /api/v1/users/login
로그인. Access Token + Refresh Token 발급.

**Request**
```json
{
  "email": "user@example.com",
  "password": "password1234"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

---

### POST /api/v1/users/refresh
Access Token 재발급. Refresh Token Rotation 적용 (재발급 시 Refresh Token도 교체).

**Request**
```json
{
  "refreshToken": "eyJ..."
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

---

### DELETE /api/v1/users/logout
로그아웃. Refresh Token 무효화.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `204 No Content`

---

### GET /api/v1/users/me
내 정보 조회.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `200 OK`
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "nickname": "재훈",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

## Auction Service

### POST /api/v1/auctions
경매 생성.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "title": "맥북 프로 14인치",
  "description": "2023년형, 상태 양호",
  "startPrice": 1000000,
  "endsAt": "2024-05-01T18:00:00Z"
}
```

**Response** `201 Created`
```json
{
  "auctionId": "uuid",
  "title": "맥북 프로 14인치",
  "sellerId": "uuid",
  "startPrice": 1000000,
  "currentPrice": 1000000,
  "status": "PENDING",
  "endsAt": "2024-05-01T18:00:00Z",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

### GET /api/v1/auctions
경매 목록 조회. 페이징 + 상태 필터 지원.

**Query Parameters**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| status | string | - | PENDING, ONGOING, CLOSED, CANCELLED |

**Response** `200 OK`
```json
{
  "content": [
    {
      "auctionId": "uuid",
      "title": "맥북 프로 14인치",
      "currentPrice": 1350000,
      "bidCount": 7,
      "status": "ONGOING",
      "endsAt": "2024-05-01T18:00:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "page": 0,
  "size": 20
}
```

---

### GET /api/v1/auctions/{id}
경매 상세 조회. `currentPrice`는 DB가 아닌 Kafka Streams State Store에서 조회.

**Response** `200 OK`
```json
{
  "auctionId": "uuid",
  "title": "맥북 프로 14인치",
  "description": "2023년형, 상태 양호",
  "sellerId": "uuid",
  "startPrice": 1000000,
  "currentPrice": 1350000,
  "currentWinnerId": "uuid",
  "bidCount": 7,
  "status": "ONGOING",
  "endsAt": "2024-05-01T18:00:00Z",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

### PATCH /api/v1/auctions/{id}
경매 수정. 입찰이 없을 때만 가능.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "title": "맥북 프로 14인치 (수정)",
  "description": "설명 수정",
  "endsAt": "2024-05-02T18:00:00Z"
}
```

**Response** `200 OK`

---

### DELETE /api/v1/auctions/{id}
경매 취소. 입찰이 없을 때만 가능.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `204 No Content`

---

### GET /api/v1/auctions/{id}/bids
경매 입찰 내역 조회.

**Response** `200 OK`
```json
{
  "content": [
    {
      "bidId": "uuid",
      "bidderId": "uuid",
      "amount": 1350000,
      "placedAt": "2024-04-28T15:30:00Z"
    }
  ],
  "totalElements": 7
}
```

---

## Bid Service

### POST /api/v1/bids
입찰.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "auctionId": "uuid",
  "amount": 1400000
}
```

**Response** `201 Created`
```json
{
  "bidId": "uuid",
  "auctionId": "uuid",
  "amount": 1400000,
  "status": "ACCEPTED",
  "placedAt": "2024-04-28T15:30:00Z"
}
```

---

### GET /api/v1/bids/me
내 입찰 내역 조회.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `200 OK`
```json
{
  "content": [
    {
      "bidId": "uuid",
      "auctionId": "uuid",
      "amount": 1400000,
      "status": "ACCEPTED",
      "placedAt": "2024-04-28T15:30:00Z"
    }
  ],
  "totalElements": 3
}
```

---

## WebSocket (Notification Service)

### 연결
```
WS /ws/auctions/{id}    # 경매방 실시간 구독
WS /ws/users/me         # 개인 알림 구독
```

### 서버 → 클라이언트 메시지

**BID_UPDATED** - 최고가 갱신 시
```json
{
  "type": "BID_UPDATED",
  "auctionId": "uuid",
  "currentPrice": 1400000,
  "bidCount": 8,
  "occurredAt": "2024-04-28T15:30:00Z"
}
```

**AUCTION_CLOSED** - 경매 마감 시
```json
{
  "type": "AUCTION_CLOSED",
  "auctionId": "uuid",
  "finalPrice": 1400000,
  "winnerId": "uuid",
  "occurredAt": "2024-04-28T18:00:00Z"
}
```

**OUTBID** - 내가 입찰한 경매에서 더 높은 금액 입찰 시 (개인 알림)
```json
{
  "type": "OUTBID",
  "auctionId": "uuid",
  "currentPrice": 1500000,
  "occurredAt": "2024-04-28T15:35:00Z"
}
```

**AUCTION_WON** - 낙찰 시 (개인 알림)
```json
{
  "type": "AUCTION_WON",
  "auctionId": "uuid",
  "finalPrice": 1400000,
  "occurredAt": "2024-04-28T18:00:00Z"
}
```
