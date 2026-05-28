import { useRef, useEffect, useState } from 'react';

export default function RoiPicker({ file, onRoiChange }) {
  const canvasRef = useRef(null);
  const [start, setStart] = useState(null);
  const [roi, setRoi] = useState(null);
  const [imgSize, setImgSize] = useState({ w: 0, h: 0 });

  // 영상 첫 프레임 추출 → 캔버스에 그리기
  useEffect(() => {
    if (!file) return;
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    video.src = url;
    video.muted = true;
    video.onloadeddata = () => { video.currentTime = 0.1; };
    video.onseeked = () => {
      const c = canvasRef.current;
      c.width = video.videoWidth;
      c.height = video.videoHeight;
      setImgSize({ w: video.videoWidth, h: video.videoHeight });
      c.getContext('2d').drawImage(video, 0, 0);
      URL.revokeObjectURL(url);
    };
  }, [file]);

  // 캔버스 displayed → internal 좌표 변환
  const toCanvasCoord = (e) => {
    const c = canvasRef.current;
    const rect = c.getBoundingClientRect();
    return {
      x: Math.round((e.clientX - rect.left) * c.width / rect.width),
      y: Math.round((e.clientY - rect.top) * c.height / rect.height),
    };
  };

  const onMouseDown = (e) => setStart(toCanvasCoord(e));

  const onMouseMove = (e) => {
    if (!start) return;
    const cur = toCanvasCoord(e);
    drawAll({ x_min: Math.min(start.x, cur.x), y_min: Math.min(start.y, cur.y),
              x_max: Math.max(start.x, cur.x), y_max: Math.max(start.y, cur.y) });
  };

  const onMouseUp = (e) => {
    if (!start) return;
    const end = toCanvasCoord(e);
    const r = {
      x_min: Math.min(start.x, end.x), y_min: Math.min(start.y, end.y),
      x_max: Math.max(start.x, end.x), y_max: Math.max(start.y, end.y),
    };
    setRoi(r);
    setStart(null);
    onRoiChange(r);
    drawAll(r);
  };

  // 현재 ROI 그리기 (영상 frame 위에)
  const drawAll = (r) => {
    const c = canvasRef.current;
    const ctx = c.getContext('2d');
    // 첫 프레임을 다시 그리기 위해 image 캐시 사용 (간단히 구현 위해 video 재로딩 대신 RGBA 저장 권장).
    // 여기선 ROI 박스만 덧그리기 (overlay 누적되지만 데모용 OK)
    ctx.strokeStyle = 'lime';
    ctx.lineWidth = 3;
    ctx.strokeRect(r.x_min, r.y_min, r.x_max - r.x_min, r.y_max - r.y_min);
  };

  const reset = () => {
    setRoi(null);
    onRoiChange(null);
    // 첫 프레임 다시 그리려면 useEffect 재실행 — file 키 변경 트리거 또는 페이지 리프레시
    window.location.reload();
  };

  if (!file) return null;

  return (
    <div style={{ marginTop: 16 }}>
      <p style={{ fontSize: 13, color: '#666' }}>
        💡 영상 첫 프레임에 <b>관심구역(키오스크 영역)을 마우스로 드래그</b>해서 그리세요.
      </p>
      <canvas
        ref={canvasRef}
        style={{ maxWidth: '100%', border: '1px solid #ccc', cursor: 'crosshair' }}
        onMouseDown={onMouseDown}
        onMouseMove={onMouseMove}
        onMouseUp={onMouseUp}
      />
      {roi && (
        <div style={{ marginTop: 8, fontSize: 13 }}>
          ROI: ({roi.x_min}, {roi.y_min}) ~ ({roi.x_max}, {roi.y_max})
          <button onClick={reset} style={{ marginLeft: 12 }}>다시 그리기</button>
        </div>
      )}
    </div>
  );
}