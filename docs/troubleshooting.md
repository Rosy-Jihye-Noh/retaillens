# 트러블슈팅 — 2026-05-22 (클라우드 E2E 안정화)

P2 완성 직후 클라우드에서 분석이 진행되지 않거나 매우 느린 문제를 단계별로 해결.

## 1. BoT-SORT 의존성(lap) 런타임 자동설치로 분석 중단

**증상**: HF Spaces에서 `POST /analyze` 후 분석이 멈춤. 콜백 미수신.
**원인**: BoT-SORT가 요구하는 `lap` 패키지가 ai-server 컨테이너에 없어, ultralytics가 런타임에 자동 설치를 시도. "Restart runtime or rerun command for updates to take effect" 경고와 함께 현재 프로세스에서 lap을 사용하지 못해 추적이 진행 안 됨.
**해결**: `ai-server/requirements.txt`에 `lap>=0.5.12` 추가 → 빌드 타임에 Docker 이미지에 포함.

## 2. ai-server 콜백이 localhost로 향함 (status가 영원히 RUNNING)

**증상**: 분석은 일부 진행되는데, backend가 콜백을 받지 못해 status가 RUNNING에서 안 바뀜.
**원인**: HF Spaces 환경변수 `SPRING_CALLBACK_URL` 미설정 → 코드 기본값 `http://localhost:8080/api/callback` 사용 → HF 컨테이너에서 localhost는 자기 자신을 가리킴.
**해결**: HF Space → Settings → Variables and secrets에 `SPRING_CALLBACK_URL = https://retaillens-backend.onrender.com/api/callback` 추가.

## 3. Vercel에서 "Failed to fetch" — Render URL 미반영

**증상**: 배포된 frontend에서 업로드 시 "업로드 실패: Failed to fetch".
**원인**: Vercel에 `VITE_API_URL` 환경변수를 추가했으나 **재배포(Redeploy)를 안 함**. Vite는 빌드 타임에 env를 주입하므로 기존 빌드엔 적용 안 됨.
**해결**: Vercel → Deployments → 최신 배포 → Redeploy. 이후 fetch가 Render URL로 향함.

## 4. Docker stdout 버퍼링으로 로그 미출력

**증상**: `[Analysis START]` 등 print 로그가 HF Logs에 안 보임. 어디서 멈추는지 진단 불가.
**원인**: 파이썬 print가 Docker 컨테이너에서 stdout 버퍼링됨.
**해결**: `Dockerfile`에 `ENV PYTHONUNBUFFERED=1` 추가 + 진단용 print에 `flush=True` 명시.

## 5. analyze_video 내부 진단 불가

**증상**: `[Analysis START]`만 뜨고 그 다음이 깜깜이.
**해결**: `analyzer.analyze_video()`에 단계별 print(영상 열기 / 메타 / 모델 로딩 / 프레임 진행 / 완료) 추가 → HF Logs에서 어느 단계에서 멈추는지 가시화.

## 6. HF CPU 추론 속도 매우 느림 (10분+ 소요)

**증상**: 1080p 영상 분석에 10분 이상 걸림.
**원인**: HF Spaces 무료 CPU basic의 한계 + 고해상도 영상(1920×1080) 처리 부담.
**해결**: `analyzer.py`의 `model.track()` 호출에 두 가지 최적화 적용:

- `imgsz=480` — 추론 해상도를 480으로 다운스케일 (약 4배 빠름)
- `vid_stride = max(1, round(fps / 5))` — fps 기반 동적 계산으로 초당 5프레임만 처리 (약 6배 빠름)
  **결과**: 10분 → 약 4분.

## 7. vid_stride 도입으로 시각(t) 계산 오류

**증상**: vid_stride를 적용하면 dwell_sec, enter_at_sec 등 시간 관련 값이 실제의 1/stride로 작아짐.
**원인**: `t = frame_idx / fps`에서 `frame_idx`는 추출된 프레임 인덱스라, 실제 시각은 stride배 곱해야 함.
**해결**: `t = round(frame_idx * vid_stride / fps, 2)`로 보정.

## 향후 개선 후보

- 모델 ONNX 변환으로 CPU 추론 추가 가속화
- HF Persistent Storage 사용 시 yolov8n.pt 재다운로드 방지
- 영상 입력을 720p 이하로 가이드 (업로드 가드레일)
- CORS origin을 Vercel 도메인으로 제한 (보안)
