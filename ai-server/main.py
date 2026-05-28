import os
import asyncio
from typing import Optional, List

import httpx
from fastapi import FastAPI, BackgroundTasks, UploadFile, File, Form
import tempfile, shutil, os
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ===== Config =====
SPRING_CALLBACK_URL = os.getenv("SPRING_CALLBACK_URL", "http://localhost:8080/api/callback")
PORT = int(os.getenv("PORT", "8000"))

# ===== App =====
app = FastAPI(title="RetailLens AI Server", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # MVP — prod에서는 origin 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ===== Schemas =====
class AnalyzeRequest(BaseModel):
    job_id: str = Field(..., description="Spring이 생성한 Job UUID")
    video_url: str = Field(..., description="분석할 영상 URL 또는 로컬 경로")

class AnalyzeResponse(BaseModel):
    job_id: str
    status: str = "ACCEPTED"
    message: str = "분석을 시작했습니다. 완료되면 콜백으로 결과를 전송합니다."

class VisitorResult(BaseModel):
    visitor_id: int
    estimated_age_band: Optional[str] = None
    estimated_gender: Optional[str] = None
    enter_at_sec: float
    exit_at_sec: Optional[float] = None
    dwell_sec: Optional[float] = None
    visited_checkout: bool = False
    checkout_dwell_sec: float = 0.0
    estimated_purchase: bool = False
    trajectory: list = []

class HeatmapData(BaseModel):
    grid_width: int
    grid_height: int
    data: list

class CallbackPayload(BaseModel):
    job_id: str
    status: str  # DONE | FAILED
    visitors: List[VisitorResult] = []
    heatmap: Optional[HeatmapData] = None
    error_message: Optional[str] = None

# ===== Endpoints =====
@app.get("/health")
def health():
    return {"status": "ok", "service": "retaillens-ai-server"}

@app.post("/analyze", response_model=AnalyzeResponse, status_code=202)
async def analyze(
    background_tasks: BackgroundTasks,
    job_id: str = Form(...),
    video: UploadFile = File(...),
):
    # 업로드된 영상을 임시 파일로 저장
    suffix = os.path.splitext(video.filename or "")[1] or ".mp4"
    tmp = tempfile.NamedTemporaryFile(suffix=suffix, delete=False)
    shutil.copyfileobj(video.file, tmp)
    tmp.close()
    background_tasks.add_task(run_analysis, job_id, tmp.name)
    return AnalyzeResponse(job_id=job_id)

# ===== Background Job  =====
async def run_analysis(job_id: str, video_path: str):
    print(f"[Analysis START] job_id={job_id}")
    try:
        from analyzer import analyze_video
        import asyncio
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(None, analyze_video, video_path)
        print(f"[Analysis DONE] job_id={job_id}, visitors={len(result['visitors'])}")
        visitors = [VisitorResult(**v) for v in result['visitors']]
        heatmap  = HeatmapData(**result['heatmap'])
        payload = CallbackPayload(job_id=job_id, status="DONE",
                                  visitors=visitors, heatmap=heatmap)
        await send_callback(payload)
    except Exception as e:
        import traceback
        print(f"[Analysis ERROR] job_id={job_id}: {e}")
        traceback.print_exc()
        payload = CallbackPayload(job_id=job_id, status="FAILED", error_message=str(e))
        await send_callback(payload)
    finally:
        try: os.remove(video_path)
        except OSError: pass

async def send_callback(payload: CallbackPayload):
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            r = await client.post(SPRING_CALLBACK_URL, json=payload.model_dump())
            print(f"[Callback OK] job_id={payload.job_id} status={payload.status} -> HTTP {r.status_code}")
        except Exception as e:
            print(f"[Callback ERROR] job_id={payload.job_id} -> {e}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=PORT, reload=False)