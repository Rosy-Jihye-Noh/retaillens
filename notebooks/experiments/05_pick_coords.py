"""첫 프레임을 띄워서 마우스 클릭으로 좌표 확인"""
import cv2

VIDEO_PATH = 'notebooks/experiments/test_video3.mp4'
cap = cv2.VideoCapture(VIDEO_PATH)
ret, frame = cap.read()
cap.release()

print(f"Image size: {frame.shape[1]} x {frame.shape[0]} (W x H)")
print("클릭하면 픽셀 좌표가 출력됩니다. ESC로 종료.")

def on_click(event, x, y, flags, param):
    if event == cv2.EVENT_LBUTTONDOWN:
        print(f"  ({x}, {y})")

cv2.namedWindow('pick', cv2.WINDOW_NORMAL)
cv2.setMouseCallback('pick', on_click)
cv2.imshow('pick', frame)
cv2.waitKey(0)
cv2.destroyAllWindows()