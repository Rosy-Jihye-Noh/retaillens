"""
Step 6 — Heatmap 좌표 누적 + 시각화
PRD §6.5: 모든 trajectory의 (x,y)를 누적 → GaussianBlur → PNG + JSON
"""
import cv2
import json
import numpy as np
from ultralytics import YOLO

VIDEO_PATH = 'notebooks/experiments/test_video.mp4'

# === 영상 메타 ===
cap = cv2.VideoCapture(VIDEO_PATH)
WIDTH  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
HEIGHT = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
ret, first_frame = cap.read()
cap.release()

# === 트래킹 ===
model = YOLO('yolov8n.pt')
results = model.track(
    source=VIDEO_PATH, classes=[0], tracker='botsort.yaml',
    conf=0.5, persist=True, save=False, verbose=False, stream=True,
)

# === Heatmap grid 누적 ===
heatmap = np.zeros((HEIGHT, WIDTH), dtype=np.float32)
total_points = 0

for result in results:
    if result.boxes.id is None:
        continue
    for box in result.boxes.xywh:
        x, y, _, _ = box.tolist()
        cx, cy = int(x), int(y)
        if 0 <= cx < WIDTH and 0 <= cy < HEIGHT:
            heatmap[cy, cx] += 1
            total_points += 1

# === GaussianBlur (반경 큰 커널) → 정규화 → 컬러맵 ===
blurred = cv2.GaussianBlur(heatmap, (101, 101), 30)
norm    = cv2.normalize(blurred, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
color   = cv2.applyColorMap(norm, cv2.COLORMAP_JET)
overlay = cv2.addWeighted(first_frame, 0.5, color, 0.5, 0)

# === 시각화 PNG 저장 ===
cv2.imwrite('notebooks/experiments/heatmap.jpg',         color)
cv2.imwrite('notebooks/experiments/heatmap_overlay.jpg', overlay)

# === API용 JSON 다운샘플링 (32x18 grid) ===
GRID_W, GRID_H = 32, 18
downsampled = cv2.resize(norm.astype(np.float32) / 255.0, (GRID_W, GRID_H))
with open('notebooks/experiments/heatmap.json', 'w') as f:
    json.dump({
        'grid_width':  GRID_W,
        'grid_height': GRID_H,
        'data': [[round(v, 4) for v in row] for row in downsampled.tolist()],
    }, f)

print(f"\n=== Heatmap Generated ===")
print(f"  Total trajectory points: {total_points}")
print(f"  Max grid intensity:      {int(heatmap.max())}")
print(f"\nFiles:")
print(f"  heatmap.jpg          - 컬러맵 단독")
print(f"  heatmap_overlay.jpg  - 원본 영상 위 50:50 오버레이 (시연용)")
print(f"  heatmap.json         - {GRID_W}x{GRID_H} 다운샘플링 (API 전송용)")