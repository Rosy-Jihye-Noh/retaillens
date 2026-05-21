"""
Step 2.1 - YOLO Person Detection 단일 이미지 테스트
사람 클래스(class 0)만 필터링해서 bbox와 confidence 출력
"""
from ultralytics import YOLO

model = YOLO('yolov8n.pt')

# Ultralytics 공식 샘플 (버스 앞에 사람 4명)
results = model('https://ultralytics.com/images/bus.jpg', classes=[0], verbose=False)

boxes = results[0].boxes
print(f"\n=== YOLO Person Detection ===")
print(f"Detected {len(boxes)} persons")
for i, box in enumerate(boxes):
    conf = box.conf.item()
    xyxy = box.xyxy[0].tolist()
    print(f"  Person {i+1}: conf={conf:.2f}, "
          f"bbox=({int(xyxy[0])},{int(xyxy[1])})-({int(xyxy[2])},{int(xyxy[3])})")

# bbox 그려진 결과 이미지 저장
results[0].save(filename='notebooks/experiments/yolo_test_result.jpg')
print("\nVisualization saved: notebooks/experiments/yolo_test_result.jpg")