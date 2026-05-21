---
title: RetailLens AI Server
emoji: 🛒
colorFrom: blue
colorTo: green
sdk: docker
app_port: 7860
pinned: false
---

# RetailLens — AI Server (FastAPI)

무인매장 Vision Analytics의 추론 모듈. mp4 영상을 입력받아 YOLO + BoT-SORT 기반 사람 탐지·추적을 수행하고, Virtual Line Crossing·ROI Dwell·Estimated Purchase 등 비즈니스 로직을 적용한 분석 결과를 SpringBoot 백엔드에 비동기 콜백으로 push한다.

## 기술 스택

| 영역 | 도구 | 버전 |
|---|---|---|
| Language | Python | 3.11+ |
| Framework | FastAPI | 0.115+ |
| ASGI Server | Uvicorn | 0.30+ |
| 객체 탐지 | Ultralytics YOLOv8 (yolov8n.pt) | 8.3+ |
| 다중 객체 추적 | BoT-SORT (Ultralytics 내장) | - |
| 영상 처리 | OpenCV (headless) | 4.10+ |
| HTTP Client | httpx (Spring 콜백) | 0.27+ |

## 아키텍처 — 비동기 콜백 (BackgroundTasks)
[SpringBoot] ──POST /analyze──▶ [FastAPI]  (즉시 202 Accepted)
│
▼ BackgroundTasks
run_analysis(job_id, video_url)
│
├─ analyzer.analyze_video()
│   ├─ YOLOv8n person detection (conf ≥ 0.5)
│   ├─ BoT-SORT 다중객체 추적 (ReID off)
│   ├─ ROI dwell time 누적
│   ├─ Estimated purchase 룰베이스 판정
│   └─ trajectory 1초당 1점 샘플링
▼
POST {SPRING}/api/callback
(visitors 리스트 JSON)

Render/HF 무료 티어의 30~120초 HTTP 타임아웃을 회피하기 위해 동기 long-running 응답을 완전히 제거. 영상 분석은 BackgroundTasks 안에서 진행되고, 완료 시 웹훅으로 결과를 push.

## 폴더 구조
ai-server/
├─ main.py              # FastAPI 앱, 라우트, BackgroundTasks 등록
├─ analyzer.py          # YOLO + BoT-SORT + 비즈니스 로직 (핵심)
├─ requirements.txt
└─ .env.example

## 환경 설정

### `requirements.txt`
fastapi>=0.115
uvicorn[standard]>=0.30
httpx>=0.27
pydantic>=2.7
python-multipart>=0.0.9
ultralytics>=8.3.0
opencv-python-headless>=4.10

### 환경 변수 (`.env`)
SPRING_CALLBACK_URL=http://localhost:8080/api/callback
PORT=8000

## 실행

루트의 `.venv` 활성화 후:

```bash
cd ai-server
uvicorn main:app --reload --port 8000
```

`http://localhost:8000` 에서 가동. Swagger UI는 `/docs`.

## API 명세

| Method | Endpoint | 응답 | 설명 |
|---|---|---|---|
| GET  | `/health` | 200 | 헬스체크 |
| POST | `/analyze` | **202 Accepted** | 영상 분석 의뢰 (BackgroundTasks에 등록, 즉시 응답) |

### `POST /analyze` 요청 예시

```json
{
  "job_id": "5d327350-04c4-4a2b-8ab0-238ecd7195bf",
  "video_url": "C:/retaillens/notebooks/experiments/test_video.mp4"
}
```

### 콜백 (FastAPI → Spring `POST /api/callback`)

```json
{
  "job_id": "...",
  "status": "DONE",
  "visitors": [
    {
      "visitor_id": 10,
      "estimated_age_band": null,
      "estimated_gender":   null,
      "enter_at_sec": 0.0,
      "exit_at_sec":  11.4,
      "dwell_sec": 11.4,
      "visited_checkout": true,
      "checkout_dwell_sec": 4.16,
      "estimated_purchase": true,
      "trajectory": [{"x":540,"y":660,"t":0.0}, ...]
    }
  ]
}


{
  "job_id": "...",
  "status": "DONE",
  "visitors": [ ... ],
  "heatmap": {
    "grid_width": 32,
    "grid_height": 18,
    "data": [[0.01, 0.02, ...], ...]
  }
}
```

## 핵심 분석 로직 (`analyzer.py`)

PRD §6.5에서 정의한 5종 로직을 구현:

| 로직 | 구현 |
|---|---|
| **Confidence 필터** | YOLO 추론 시 `conf=0.5` 적용 — 마네킹 등 false positive 제거 |
| **트래킹 ID 노이즈 제거** | 10 프레임 미만 trajectory는 visitor에서 제외 |
| **Virtual Line Crossing** | 화면 세로 중앙선(`entry_line_ratio=0.5`)을 위→아래로 통과한 ID만 입장으로 카운트 |
| **ROI Dwell** | 사용자 정의 직사각형(`roi_ratio`) 안에서 머문 누적 프레임 → 초로 환산 |
| **Estimated Purchase** | `visited_checkout AND checkout_dwell ≥ checkout_min_dwell_sec` (default 3.0s) |
| **Trajectory 샘플링** | 모든 프레임이 아닌 **1초당 1점**만 저장 → DB JSONB 용량 절감 |
| **Heatmap 생성** | 모든 trajectory의 (x,y) 누적 → GaussianBlur → 32×18 그리드로 다운샘플링한 JSON 반환 |

### 주요 파라미터 (조정 가능)

```python
analyze_video(
    video_path,
    conf_threshold=0.5,           # YOLO confidence
    min_trajectory=10,            # 노이즈 필터
    entry_line_ratio=0.5,         # 가상 라인 위치 (0~1)
    roi_ratio={                   # ROI 직사각형 (0~1)
        'x_min': 0.25, 'y_min': 0.30,
        'x_max': 0.75, 'y_max': 0.85,
    },
    checkout_min_dwell_sec=3.0,   # 구매 추정 임계값
)
```

> 백화점/편의점/카페 등 영상 유형에 따라 ROI는 "관심 구역(Interest Zone)"으로 일반화 가능. 계산대일 수도, 신상품 매대일 수도, 핫존일 수도 있음.

## 주요 설계 결정

- **모델 lazy load + 단일 인스턴스**: `_get_model()`로 첫 요청 시 1회만 로딩, 이후 재사용 → 메모리·시간 절감
- **`stream=True` 사용**: Ultralytics 결과를 generator로 받아 메모리 OOM 방지 (긴 영상 대비)
- **CPU bound → 별도 thread**: `loop.run_in_executor(None, ...)`로 BackgroundTasks 안에서도 이벤트 루프 점유 방지
- **Trajectory 1초 샘플링**: 5 FPS × 60초 × 5명 = 1,500점이 아니라 60점 정도로 압축 → DB·네트워크 부담 최소
- **CCTV 하이앵글 가정**: 얼굴 모델(MiVOLO/DeepFace)은 P3 옵션으로 분리. 본 모듈은 trajectory 분석에 집중


## 배포 (HuggingFace Spaces)

Docker SDK 기반으로 배포. `Dockerfile` + README frontmatter(`sdk: docker`, `app_port: 7860`)로 자동 빌드.

- 배포 URL: https://rosyhey-retaillens-ai-server.hf.space
- 헬스체크: `/health`, API 문서: `/docs`
- 하드웨어: CPU basic (무료), 영구 스토리지 없음 (모델은 시작 시 자동 다운로드)
- 모델 yolov8n.pt는 repo에 포함하지 않고 런타임 자동 다운로드

## 진행 현황 및 로드맵

### 완료 (P1)
- [x] YOLOv8 + BoT-SORT 추적 파이프라인
- [x] Virtual Line Crossing / ROI Dwell / Estimated Purchase 로직
- [x] Heatmap 좌표 누적 → Spring 저장 및 조회 API 노출
- [x] HuggingFace Spaces 배포

### 향후 과제 (P3 / 최적화)
- [ ] 인구통계 ID별 캐싱 (P3 — MiVOLO 연령·성별, DeepFace 감정)
- [ ] 모델 ONNX 변환 (CPU 추론 속도 최적화)
- [ ] 영상 업로드(multipart) 또는 URL 다운로드 방식 통합