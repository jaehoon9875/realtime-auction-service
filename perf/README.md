# perf — 성능 테스트 실행 자산

GKE 클러스터 내부에서 k6 부하 테스트를 실행하는 스크립트 모음.
**측정 방법론·결과 리포트는 [`docs/performance/`](../docs/performance/README.md)** 에 있다.

## 구성

```text
perf/
├── config/baseline.env   # 부하 고정값 (before/after 공용 — 수정 금지)
├── k6/
│   ├── bid-latency.js     # P3: POST /api/bids 레이턴시 측정
│   └── auth.js            # signup/login/경매생성/State Store 대기 헬퍼
├── results/<git-sha>/     # 실행 결과(로그 + summary JSON) — 자동 생성
└── run-perf.sh            # 실행 러너 (ConfigMap+Job 생성 → 로그 수거)
```

## 빠른 실행

```bash
kubectl config current-context   # 올바른 클러스터인지 먼저 확인
./perf/run-perf.sh               # baseline.env 기본값으로 측정
./perf/run-perf.sh stop          # Job/ConfigMap 정리
```

상세 절차·비용 주의사항은 [docs/performance/README.md](../docs/performance/README.md) 참고.
