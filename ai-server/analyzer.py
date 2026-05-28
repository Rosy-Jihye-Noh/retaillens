"""
RetailLens AI Analyzer
영상 파일 경로를 받아 visitor 분석 결과 리스트를 반환
"""
import cv2
from ultralytics import YOLO
from collections import defaultdict
from typing import List, Dict, Optional
import numpy as np

_model: Optional[YOLO] = None

def _get_model() -> YOLO:
    global _model
    if _model is None:
        _model = YOLO('yolov8n.pt')
    return _model

def analyze_video(
    video_path: str,
    conf_threshold: float = 0.5,
    min_trajectory: int = 5,
    entry_line_ratio: float = 0.5,
    roi_ratio: Optional[Dict[str, float]] = None,
    roi_abs: Optional[Dict[str, int]] = None, 
    checkout_min_dwell_sec: float = 3.0,
    heatmap_grid: tuple = (32, 18),
) -> Dict:
    if roi_ratio is None:
        roi_ratio = {'x_min': 0.25, 'y_min': 0.30, 'x_max': 0.75, 'y_max': 0.85}

    print(f"[analyze] open video: {video_path}", flush=True)
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise FileNotFoundError(f"Cannot open video: {video_path}")
    width  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps    = cap.get(cv2.CAP_PROP_FPS) or 25.0
    cap.release()

    # ROI 결정: 절대값(roi_abs) 우선, 없으면 비율(roi_ratio)
    if roi_abs is not None:
        roi = roi_abs
    else:
        roi = {
            'x_min': int(width  * roi_ratio['x_min']),
            'y_min': int(height * roi_ratio['y_min']),
            'x_max': int(width  * roi_ratio['x_max']),
            'y_max': int(height * roi_ratio['y_max']),
        }
    print(f"[analyze] ROI: {roi}", flush=True)

    print(f"[analyze] meta: {width}x{height}, {fps}fps", flush=True)

    model = _get_model()
    print(f"[analyze] model loaded, tracking start", flush=True)
    TARGET_FPS = 5                                  # 초당 5프레임 처리 (트래킹 안정성 균형점)
    vid_stride = max(1, round(fps / TARGET_FPS))    # 30fps → stride 6
    effective_fps = fps / vid_stride
    results = model.track(
        source=video_path, classes=[0], tracker='botsort.yaml',
        conf=conf_threshold, persist=True, save=False, verbose=False, stream=True, vid_stride=vid_stride, imgsz=480,
    )

    trajectories: dict = defaultdict(list)
    roi_frames: dict   = defaultdict(int)
    heatmap_acc = np.zeros((height, width), dtype=np.float32)

    for frame_idx, result in enumerate(results):
        if frame_idx % 20 == 0:
            print(f"[analyze] frame {frame_idx}", flush=True)
        if result.boxes.id is None:
            continue
        for box, tid in zip(result.boxes.xywh, result.boxes.id):
            x, y, _, _ = box.tolist()
            tid = int(tid)
            t = round(frame_idx * vid_stride / fps, 2)
            trajectories[tid].append({'x': round(x), 'y': round(y), 't': t})
            cx, cy = int(x), int(y)
            if 0 <= cx < width and 0 <= cy < height:
                heatmap_acc[cy, cx] += 1
            if roi['x_min'] <= x <= roi['x_max'] and roi['y_min'] <= y <= roi['y_max']:
                roi_frames[tid] += 1

    print(f"[analyze] tracking done, raw_ids={len(trajectories)}", flush=True)

    # === visitors 분석 ===
    visitors = []
    for tid, pts in trajectories.items():
        if len(pts) < min_trajectory:
            continue
        dwell = pts[-1]['t'] - pts[0]['t']
        ckdwell = roi_frames[tid] / effective_fps 
        visited_checkout = ckdwell > 0
        estimated_purchase = visited_checkout and ckdwell >= checkout_min_dwell_sec

        sampled, last_t = [], -1.0
        for pt in pts:
            if pt['t'] - last_t >= 1.0:
                sampled.append(pt); last_t = pt['t']

        visitors.append({
            'visitor_id': tid,
            'estimated_age_band': None,
            'estimated_gender':   None,
            'enter_at_sec': pts[0]['t'],
            'exit_at_sec':  pts[-1]['t'],
            'dwell_sec':    round(dwell, 2),
            'visited_checkout':   visited_checkout,
            'checkout_dwell_sec': round(ckdwell, 2),
            'estimated_purchase': estimated_purchase,
            'trajectory': sampled,
        })

    # === heatmap 후처리 (Gaussian blur → 정규화 → 다운샘플링) ===
    blurred = cv2.GaussianBlur(heatmap_acc, (101, 101), 30)
    norm = cv2.normalize(blurred, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
    gw, gh = heatmap_grid
    downsampled = cv2.resize(norm.astype(np.float32) / 255.0, (gw, gh))
    heatmap_json = {
        'grid_width':  gw,
        'grid_height': gh,
        'data': [[round(v, 4) for v in row] for row in downsampled.tolist()],
    }

    return {'visitors': visitors, 'heatmap': heatmap_json}