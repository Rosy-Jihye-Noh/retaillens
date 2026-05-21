"""
Step 2.2 - 영상 person tracking (YOLO + BoT-SORT)
각 사람에게 고유 ID가 부여되고 trajectory가 추출되는지 검증
"""
from ultralytics import YOLO
from collections import defaultdict

model = YOLO('yolov8n.pt')

VIDEO_PATH = 'notebooks/experiments/test_video.mp4'

# track() 메서드 — BoT-SORT 트래킹 활성화
results = model.track(
    source=VIDEO_PATH,
    classes=[0],                  # person only
    tracker='botsort.yaml',       # BoT-SORT (Ultralytics 내장)
    persist=True,
    save=True,                    # 결과 영상 저장 (runs/ 폴더)
    verbose=False,
)

# Track ID별 trajectory 누적
trajectories = defaultdict(list)
for frame_idx, result in enumerate(results):
    if result.boxes.id is None:
        continue
    for box, tid in zip(result.boxes.xywh, result.boxes.id):
        x, y, w, h = box.tolist()
        trajectories[int(tid)].append({'x': round(x), 'y': round(y), 'frame': frame_idx})

print(f"\n=== Tracking Result ===")
print(f"Total unique persons tracked: {len(trajectories)}")
for tid, points in sorted(trajectories.items()):
    print(f"  Person ID {tid}: {len(points)} frames, "
          f"start_frame={points[0]['frame']}, end_frame={points[-1]['frame']}")