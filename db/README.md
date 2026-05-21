# RetailLens — Database Schema (v1.0)

## Database
- **DBMS**: PostgreSQL 16+
- **DB**: `retaillens` / **User**: `retaillens` / **Password**: `retaillens_dev` (로컬 개발용)
- **Extension**: `uuid-ossp`

---

## 테이블 1: `jobs` — 영상 분석 작업 단위

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | UUID PK | 자동 생성 (`uuid_generate_v4()`). React polling 식별자 |
| `status` | VARCHAR(20) | `QUEUED` → `RUNNING` → `DONE` / `FAILED` |
| `progress` | SMALLINT | 진행률 0~100 (CHECK 제약) |
| `video_filename` | VARCHAR(255) | 업로드된 원본 파일명 |
| `video_size_byte` | BIGINT | 가드레일 검증(30MB) |
| `video_duration_sec` | NUMERIC(8,2) | 가드레일 검증(60초) |
| `recorded_at` | TIMESTAMPTZ | **CCTV 실제 촬영 시작 시각** — 시간대별 KPI 집계용 |
| `error_message` | TEXT | `FAILED` 시 원인 |
| `created_at` | TIMESTAMPTZ | Job 등록 시각 (사용자 업로드 시점) |
| `started_at` | TIMESTAMPTZ | FastAPI 분석 시작 시각 |
| `finished_at` | TIMESTAMPTZ | 분석 완료 시각 |

**Constraints**: `chk_status`(4개 값), `chk_progress`(0~100)
**Indexes**: `idx_jobs_status`, `idx_jobs_created_at`

---

## 테이블 2: `visitors` — 추적된 익명 방문자

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | DB 행 번호 |
| `job_id` | UUID FK | jobs(id) 참조, `ON DELETE CASCADE` |
| `visitor_id` | INT | 영상 내부 익명 추적 ID (BoT-SORT) — cross-video 매칭 금지 |
| `estimated_age_band` | VARCHAR(20) | `child / teen / young_adult / middle / senior / unknown` |
| `estimated_gender` | VARCHAR(10) | `male / female / unknown` |
| `enter_at_sec` | NUMERIC(8,2) | 영상 시작점 기준 입장 시각(초) |
| `exit_at_sec` | NUMERIC(8,2) | 영상 시작점 기준 퇴장 시각(초). NULL 가능 |
| `dwell_sec` | NUMERIC(8,2) | 매장 전체 체류 시간(초) |
| `visited_checkout` | BOOLEAN | 계산대 ROI 진입 여부 |
| `checkout_dwell_sec` | NUMERIC(8,2) | 계산대 ROI 누적 체류 시간 |
| `estimated_purchase` | BOOLEAN | 추정 구매 여부 (rule-based) |
| `trajectory` | JSONB | `[{x,y,t}, ...]` — **1초당 1점**으로 샘플링하여 저장 |
| `created_at` | TIMESTAMPTZ | DB 레코드 생성 시각 |

**Constraints**: `UNIQUE(job_id, visitor_id)`, `chk_gender`, `chk_age`
**Indexes**: `idx_visitors_job`, `idx_visitors_demo`, `idx_visitors_purchase`

---

## 운영 정책

- **익명성**: 얼굴 원본·개인정보 미저장. `visitor_id`는 영상 내부에서만 유효
- **추정 표기**: 성별·연령·구매전환은 모두 `estimated_` 접두로 명시 (사실 아닌 추론)
- **`estimated_purchase` 룰**: `visited_checkout=TRUE AND checkout_dwell_sec ≥ 10 AND 이후 exit direction → TRUE`
- **`trajectory` 샘플링**: FastAPI에서 1초당 1점만 저장 (60초 영상 × 5명 ≈ 12KB)
- **`estimated_emotion`**: Phase 3 experimental 옵션 — 필요 시 `ALTER TABLE visitors ADD COLUMN estimated_emotion VARCHAR(20);`

---

## 변경 이력

- **v1.0 (2026-05-20)**: 초기 스키마. GEMINI·GPT 피드백 반영
  - `recorded_at` 추가 (시간대 분석 필수)
  - `progress` / `gender` / `age_band` CHECK 제약 추가
  - GIN 인덱스 제거 → `estimated_purchase` 인덱스로 교체
  - `estimated_emotion` 제외 (P3 옵션화)
  - jobs 테이블에 `heatmap JSONB` 컬럼 추가 (Job 단위 heatmap 저장)