"""Step 20 - InsightFace로 얼굴에서 연령/성별 추정 검증"""
import cv2
from insightface.app import FaceAnalysis

# 모델 로딩 (첫 실행 시 자동 다운로드)
app = FaceAnalysis(name='buffalo_l', providers=['CPUExecutionProvider'])
app.prepare(ctx_id=-1, det_size=(640, 640))  # CPU

# 영상 첫 프레임에서 얼굴 탐지 + 인구통계
VIDEO_PATH = 'notebooks/experiments/test_video2.mp4'
cap = cv2.VideoCapture(VIDEO_PATH)
ret, frame = cap.read()
cap.release()

faces = app.get(frame)
print(f"\n=== InsightFace 결과 ===")
print(f"Detected faces: {len(faces)}")
for i, f in enumerate(faces):
    gender = 'male' if f.sex == 'M' else 'female'
    print(f"  Face {i+1}: age={f.age}, gender={gender}, "
          f"bbox=[{int(f.bbox[0])},{int(f.bbox[1])}-{int(f.bbox[2])},{int(f.bbox[3])}]")

# 시각화 저장
for f in faces:
    x1, y1, x2, y2 = [int(v) for v in f.bbox]
    cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
    cv2.putText(frame, f"{f.age}/{f.sex}", (x1, y1-10),
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
cv2.imwrite('notebooks/experiments/demographics_test.jpg', frame)
print("\nVisualization: notebooks/experiments/demographics_test.jpg")