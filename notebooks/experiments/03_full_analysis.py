"""
Step 3 — Tracking + Virtual Line + ROI Dwell + Estimated Purchase
PRD §6.5 핵심 로직 통합 검증
"""
import cv2
from ultralytics import YOLO
from collections import defaultdict

VIDEO_PATH = 'notebooks/experiments/test_video3.mp4'

# === ROI/Line 시각화 (첫 프레임에 그려서 저장) ===
def preview_roi(video_path, line_y, roi, out='notebooks/experiments/roi_preview.jpg'):
    import cv2
    cap = cv2.VideoCapture(video_path); ret, frame = cap.read(); cap.release()
    h, w = frame.shape[:2]
    cv2.line(frame, (0, line_y), (w, line_y), (0, 0, 255), 3)       # 빨간 라인
    cv2.rectangle(frame, (roi['x_min'], roi['y_min']),
                  (roi['x_max'], roi['y_max']), (0, 255, 0), 3)      # 초록 ROI
    cv2.imwrite(out, frame)
    print(f"Preview saved: {out}")

# === 영상 메타 정보 ===
cap = cv2.VideoCapture(VIDEO_PATH)
WIDTH  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
HEIGHT = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
FPS    = cap.get(cv2.CAP_PROP_FPS)
TOTAL_FRAMES = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
DURATION = TOTAL_FRAMES / FPS
cap.release()
print(f"Video: {WIDTH}x{HEIGHT}, {FPS:.1f} FPS, {DURATION:.1f}s, {TOTAL_FRAMES} frames")

# === 설정 (영상에 맞게 조정) ===
CONF_THRESHOLD    = 0.5                  # confidence ≥ 0.5만 — 마네킹 false positive 제거
MIN_TRAJECTORY    = 10                   # 10 프레임 미만은 노이즈로 간주

ENTRY_LINE_Y      = 180                  # 입구 중앙 y좌표 # outside(위) → inside(아래) = enter

CHECKOUT_ROI = {             # 키오스크 영역 (사다리꼴 → 직사각형 근사)
    'x_min': 888,  'y_min': 82,
    'x_max': 1141, 'y_max': 471,
}
CHECKOUT_MIN_DWELL_SEC = 3         # 10초 영상이라 짧게

preview_roi(VIDEO_PATH, ENTRY_LINE_Y, CHECKOUT_ROI)

# === YOLO + BoT-SORT 트래킹 (confidence 필터 포함) ===
model = YOLO('yolov8n.pt')
results = model.track(
    source=VIDEO_PATH,
    classes=[0],
    tracker='botsort.yaml',
    conf=CONF_THRESHOLD,
    persist=True,
    save=False,
    verbose=False,
)

# === 데이터 누적 ===
trajectories      = defaultdict(list)
checkout_frames   = defaultdict(int)

for frame_idx, result in enumerate(results):
    if result.boxes.id is None:
        continue
    for box, tid in zip(result.boxes.xywh, result.boxes.id):
        x, y, _, _ = box.tolist()
        tid = int(tid)
        t = round(frame_idx / FPS, 2)
        trajectories[tid].append({'x': round(x), 'y': round(y), 't': t})
        if CHECKOUT_ROI['x_min'] <= x <= CHECKOUT_ROI['x_max'] \
           and CHECKOUT_ROI['y_min'] <= y <= CHECKOUT_ROI['y_max']:
            checkout_frames[tid] += 1

# === 비즈니스 로직 적용 ===
visitors = []
for tid, pts in trajectories.items():
    if len(pts) < MIN_TRAJECTORY:
        continue   # 노이즈 제거

    first_y, last_y = pts[0]['y'], pts[-1]['y']
    crossed_enter = first_y < ENTRY_LINE_Y and last_y >= ENTRY_LINE_Y
    # 영상 시작 0.5초 이내 등장한 ID는 "이미 매장 안에 있던 사람"으로 간주
    if pts[0]['t'] < 0.5:
        crossed_enter = False
    crossed_exit  = first_y >= ENTRY_LINE_Y and last_y < ENTRY_LINE_Y

    dwell = pts[-1]['t'] - pts[0]['t']
    checkout_dwell = checkout_frames[tid] / FPS
    visited_checkout = checkout_dwell > 0
    estimated_purchase = visited_checkout and checkout_dwell >= CHECKOUT_MIN_DWELL_SEC

    visitors.append({
        'visitor_id': tid,
        'frames': len(pts),
        'enter_at_sec': pts[0]['t'],
        'exit_at_sec':  pts[-1]['t'],
        'dwell_sec':    round(dwell, 2),
        'visited_checkout':   visited_checkout,
        'checkout_dwell_sec': round(checkout_dwell, 2),
        'estimated_purchase': estimated_purchase,
        'crossed_enter_line': crossed_enter,
        'crossed_exit_line':  crossed_exit,
    })

# === 요약 출력 ===
entered     = sum(1 for v in visitors if v['crossed_enter_line'])
checked_out = sum(1 for v in visitors if v['visited_checkout'])
purchased   = sum(1 for v in visitors if v['estimated_purchase'])

print(f"\n=== Analysis Result ===")
print(f"Raw track IDs:                       {len(trajectories)}")
print(f"Valid visitors (≥{MIN_TRAJECTORY} frames):           {len(visitors)}")
print(f"  Crossed entry line (top → down):   {entered}")
print(f"  Visited checkout ROI:              {checked_out}")
print(f"  Estimated purchase:                {purchased}")
print(f"  Estimated no-purchase:             {len(visitors) - purchased}")

print(f"\nTop 5 by dwell time:")
for v in sorted(visitors, key=lambda x: x['dwell_sec'], reverse=True)[:5]:
    print(f"  ID {v['visitor_id']:>3}: dwell={v['dwell_sec']:.1f}s, "
          f"checkout={v['checkout_dwell_sec']}s, "
          f"purchase={'Y' if v['estimated_purchase'] else 'N'}")