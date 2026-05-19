# services/user-service/CLAUDE.md

회원가입/로그인, JWT 발급, Refresh Token Rotation을 담당하는 서비스.

---

## 도메인 책임

- 회원가입 (이메일/비밀번호, 중복 검증)
- 로그인 → Access Token + Refresh Token 발급
- Refresh Token Rotation (토큰 재발급 시 기존 Refresh Token 무효화)
- 토큰 검증 (API Gateway 인증 필터와 연동)

---

## 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/users/signup` | 회원가입 |
| POST | `/users/login` | 로그인 → 토큰 발급 |
| POST | `/users/refresh` | Access Token 재발급 |
| POST | `/users/logout` | Refresh Token 무효화 |
| GET | `/users/me` | 내 정보 조회 (인증 필요) |

---

## JWT 설계

- **Access Token**: 만료 15분, stateless. API Gateway에서 공개키로 직접 검증.
- **Refresh Token**: 만료 7일, Redis에 저장.
  - 키: `refresh_token:{userId}`, 값: token 문자열, TTL: 7일
  - Rotation: 재발급 시 기존 키 덮어쓰기 → 이전 토큰 자동 무효화
  - 탈취 감지: Redis에 존재하지 않는 토큰으로 재발급 요청 시 해당 userId 키 삭제 (전체 세션 무효화)

### Refresh Token 전송 방식: Bearer 헤더 채택

현재는 `Authorization: Bearer {refreshToken}` 헤더로 전송한다.
모바일 앱·외부 API 클라이언트 지원을 고려한 선택이며, 실무에서도 흔히 쓰이는 방식이다.

웹 전용 서비스로 전환할 경우 **HttpOnly 쿠키** 방식이 XSS 공격에 더 안전하다.
- Bearer 헤더: XSS에 취약(JS로 토큰 탈취 가능), CSRF 안전, 모바일 친화적
- HttpOnly 쿠키: XSS 안전(JS 접근 불가), CSRF 방어 추가 필요, 웹 친화적

---

## Spring Security 설정

- `/users/signup`, `/users/login`, `/users/refresh`는 인증 없이 접근 가능.
- 나머지는 `Authorization: Bearer {accessToken}` 헤더 필요.
- 비밀번호는 BCrypt로 해싱한다. 평문 저장 금지.

---

## API Gateway 연동

- Gateway의 인증 필터는 `user-service`에 토큰 검증을 위임하거나, 공개키로 직접 검증한다.
- 검증 성공 시 Gateway가 `X-User-Id`, `X-User-Email` 헤더를 하위 서비스에 전달한다.
- 하위 서비스는 이 헤더로 현재 사용자를 식별한다 (직접 토큰 파싱 불필요).

---

## 의존 서비스

| 서비스 | 방식 | 용도 |
|--------|------|------|
| Redis | Spring Data Redis | Refresh Token 저장/무효화 |

---

## DB 스키마 참고

docs/schema.md의 user-service 섹션 참고.
