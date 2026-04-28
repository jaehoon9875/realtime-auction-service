# AI 워크플로우

이 프로젝트에서 활용할 AI 자동화 워크플로우입니다.
M7 단계에서 도입 예정이며, 구현 전 참고 문서로 관리합니다.

---

## 워크플로우 목록

| 워크플로우 | 트리거 | 설명 |
|---|---|---|
| CodeRabbit (GitHub App) | PR 생성/업데이트 | 코드 리뷰 자동화 (이미 적용 중) |
| `ai-doc-update.yml` | main 브랜치 머지 | 문서 자동 업데이트 PR 생성 |
| `ai-issue-analysis.yml` | 이슈에 `analyze` 라벨 추가 | 이슈 원인 분석 및 해결 방향 코멘트 |

---

## 1. 코드 리뷰 자동화 (CodeRabbit)

[CodeRabbit](https://coderabbit.ai) GitHub App으로 동작. 별도 워크플로우 파일 불필요.
설정 파일: `.coderabbit.yml`

PR 생성/업데이트 시 자동 트리거. 검토 항목:

- 계층 의존 방향 준수 (Controller → Service → Repository → Entity)
- Outbox Pattern 준수 여부 (Kafka 직접 발행 금지)
- 서비스 간 DB 직접 참조 금지
- Resilience4j Circuit Breaker 적용 여부
- 시크릿 하드코딩 여부
- Testcontainers 기반 통합 테스트 작성 여부

---

## 2. 문서 자동 업데이트 (`ai-doc-update.yml`)

main 브랜치 머지 시 트리거. API 또는 Kafka 스키마 변경을 감지하여 `docs/` 업데이트 PR을 자동 생성.

**감지 대상:**
- `services/**/controller/**/*.java` 변경 → `docs/api.md` 업데이트
- `*.avsc` 또는 Avro 스키마 관련 변경 → `docs/kafka.md` 업데이트
- `**/schema.sql` 또는 마이그레이션 파일 변경 → `docs/schema.md` 업데이트

**구현 방식 (M7 시점에 결정):**
- Claude API 또는 OpenAI API 호출
- `ANTHROPIC_API_KEY` 또는 `OPENAI_API_KEY`를 GitHub Secret으로 등록

---

## 3. 이슈 분석 및 개선 제안 (`ai-issue-analysis.yml`)

이슈에 `analyze` 라벨 추가 시 트리거. 이슈 내용과 관련 코드를 분석하여 원인 및 해결 방향을 이슈 코멘트로 작성.

**동작 흐름:**
1. 이슈 본문에서 키워드 추출 (서비스명, 파일명, 에러 메시지 등)
2. 관련 코드 파일 탐색
3. 원인 분석 + 해결 방향 + 참고 코드 위치를 이슈 코멘트로 작성

**구현 방식 (M7 시점에 결정):**
- Claude API 또는 OpenAI API 호출
- `ANTHROPIC_API_KEY` 또는 `OPENAI_API_KEY`를 GitHub Secret으로 등록
