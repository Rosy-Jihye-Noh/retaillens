# RetailLens — Backend (Spring Boot)

무인매장 Vision Analytics 플랫폼의 백엔드 모듈. 영상 분석 Job을 관리하고, AI 서버(FastAPI)와 비동기 콜백으로 통신하며, PostgreSQL에 결과를 저장한다.

## 기술 스택

| 영역 | 도구 | 버전 |
|---|---|---|
| Language | Java | 21 (Temurin) |
| Framework | Spring Boot | 3.5.14 |
| Build | Gradle | 8.x (wrapper) |
| ORM | Spring Data JPA + Hibernate | 6.6.x |
| DB | PostgreSQL | 17 |
| HTTP Client | Spring RestClient | - |
| Util | Lombok, Validation | - |

## 아키텍처 — 비동기 콜백 패턴
[Client] ──POST /jobs──▶ [Spring] ──POST /analyze──▶ [FastAPI]
│                          │
▼                          │ (BackgroundTasks)
jobs INSERT                     │
▲                          ▼
└──POST /api/callback──── 분석 완료
│
▼
jobs UPDATE + visitors INSERT

Render Free Tier의 504 타임아웃을 방지하기 위해 **동기 long-running 요청을 완전히 제거**. FastAPI는 202 Accepted를 즉시 반환하고 백그라운드로 처리한 뒤, Spring의 웹훅(`/api/callback`)으로 결과를 push한다.

## 폴더 구조
backend/
└─ src/main/java/com/retaillens/backend/
├─ BackendApplication.java
├─ config/RestClientConfig.java   # HTTP/1.1 강제 (uvicorn 호환)
├─ controller/
│   ├─ JobController.java         # POST /jobs, GET /jobs/{id}
│   └─ CallbackController.java    # POST /api/callback (FastAPI 웹훅 수신)
├─ service/JobService.java        # 핵심 비즈 로직
├─ entity/
│   ├─ Job.java                   # jobs 테이블 매핑
│   └─ Visitor.java               # visitors 테이블 (trajectory JSONB 포함)
├─ repository/
│   ├─ JobRepository.java
│   └─ VisitorRepository.java
└─ dto/
├─ JobCreateRequest.java
├─ JobResponse.java
├─ AnalyzeRequest.java        # FastAPI에 전송
├─ CallbackPayload.java       # FastAPI에서 수신
└─ VisitorResult.java

## 환경 설정

### 사전 요구사항

- JDK 21 (Temurin)
- PostgreSQL 17 (로컬, `retaillens` DB, `retaillens` user)
- DB 스키마 적용 완료 (`db/schema.sql` 참조)

### `application.yml`

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/retaillens
    username: retaillens
    password: retaillens_dev
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
  jackson:
    property-naming-strategy: SNAKE_CASE
ai-server:
  url: http://localhost:8000
```

## 실행

```bash
cd backend
./gradlew bootRun       # 첫 실행은 의존성 다운로드로 1~3분 소요
```

`http://localhost:8080` 에서 가동. 종료는 `Ctrl+C`.

## API 명세

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/jobs` | 영상 분석 Job 생성 + FastAPI에 분석 의뢰 (202 Accepted) |
| GET | `/jobs/{id}` | Job 진행 상태·결과 조회 (React polling용) |
| POST | `/api/callback` | FastAPI에서 분석 완료 시 호출하는 웹훅 |
| GET | `/jobs/{id}/heatmap` | 해당 Job의 heatmap JSON (32×18 그리드) 조회 |
| GET | `/stats` | 전체 visitors 집계 KPI (8종) |
| GET | `/stats/{jobId}` | 특정 Job의 집계 KPI |


## 집계 KPI (GET /stats)

trajectory 분석 결과를 비즈니스 인사이트로 집계:

| KPI | 설명 |
|---|---|
| visitor_count | 총 방문자 수 |
| avg_dwell_sec | 평균 체류 시간 |
| estimated_conversion_rate | 추정 구매 전환율 |
| no_purchase_count | 미구매 추정 방문자 수 |
| checkout_visit_count | ROI(관심구역) 방문자 수 |
| avg_checkout_dwell_sec | 평균 ROI 체류 시간 |
| age_distribution | 추정 연령대 분포 (P3에서 채워짐) |
| gender_distribution | 추정 성별 분포 (P3에서 채워짐) |

> 인구통계 2종은 현재 unknown. Phase 3에서 MiVOLO 통합 시 실제 값으로 채워짐.

### 호출 예시 (Walking Skeleton)

```bash
# Job 생성
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"video_filename":"test.mp4","video_url":"http://example.com/test.mp4"}'
# → 202 Accepted + JSON (id, status=RUNNING)

# 약 3초 후 상태 조회
curl http://localhost:8080/jobs/<id>
# → status=DONE, progress=100
```

## End-to-End 테스트 절차

1. PostgreSQL 실행 확인
2. ai-server 기동 (`cd ../ai-server && uvicorn main:app --reload --port 8000`)
3. backend 기동 (`./gradlew bootRun`)
4. 위 curl 호출 → DBeaver에서 `SELECT * FROM visitors WHERE job_id = '<id>'` 로 2행 확인

## 주요 설계 결정

- **`RestClient`에 `SimpleClientHttpRequestFactory` 사용**: JDK 21 기본 HttpClient의 자동 HTTP/2 upgrade가 uvicorn(HTTP/1.1 only)과 충돌해 body가 누락되는 문제 회피
- **DTO 기반 직렬화**: `Map<String, String>` 대신 명시적 DTO를 사용해 Spring Jackson의 SNAKE_CASE 자동 매핑을 활용
- **`trajectory`는 JSONB로 매핑**: Hibernate 6.x의 `@JdbcTypeCode(SqlTypes.JSON)` 사용해 List<Map>을 PostgreSQL JSONB와 직접 매핑