from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.staticfiles import StaticFiles
from moviepy import VideoFileClip
from pathlib import Path
from fastapi.middleware.cors import CORSMiddleware
import uuid
from pydantic import BaseModel
from typing import List, Optional
from google import genai
import os
import time

#TODO: remove later
metrics_json = {
    "0.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "1.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "2.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "3.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "4.0": {"pulseRate": 95, "breathingRate": 15, "engagement": 0.7},
    "5.0": {"pulseRate": 120, "breathingRate": 19, "engagement": 0.9},
    "6.0": {"pulseRate": 97, "breathingRate": 15, "engagement": 0.6},
    "7.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "8.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "9.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "10.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "11.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "12.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "13.0": {"pulseRate": 73, "breathingRate": 13, "engagement": 0.6},
    "14.0": {"pulseRate": 74, "breathingRate": 13.4, "engagement": 0.7},
    "15.0": {"pulseRate": 95, "breathingRate": 15, "engagement": 0.7},
    "16.0": {"pulseRate": 120, "breathingRate": 19, "engagement": 0.9},
    "17.0": {"pulseRate": 97, "breathingRate": 15, "engagement": 0.6}
}

api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    raise ValueError("GEMINI_API_KEY not set")
client = genai.Client(api_key=api_key)

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

UPLOAD_DIR = Path("Uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

app.mount("/Uploads", StaticFiles(directory="Uploads"), name="uploads")

sessions = {}
uploaded_ads = {}

# Pre-populate `uploaded_ads` from existing files in UPLOAD_DIR on startup
if UPLOAD_DIR.exists():
    for file in UPLOAD_DIR.glob("*.mp4"):
        # Assuming filename format is "ad_id_original_filename.mp4"
        parts = file.name.split("_", 1)
        if len(parts) == 2:
            ad_id = parts[0]
            uploaded_ads[ad_id] = {
                "original_filename": parts[1],
                "saved_filename": file.name
            }

@app.get("/")
def root():
    return {"message": "Backend is running"}

@app.get("/videos")
def get_videos():
    videos = []
    if UPLOAD_DIR.exists():
        for file in UPLOAD_DIR.glob("*.mp4"):
            videos.append({
                "id": file.name,
                "name": file.name[37:],
                "url": f"http://127.0.0.1:8000/Uploads/{file.name}#t=0.001"
            })
    return {"videos": videos}

@app.post("/upload-ad")
async def upload_ad(file: UploadFile = File(...)):
    ad_id = str(uuid.uuid4())
    saved_name = f"{ad_id}_{file.filename}"
    file_path = UPLOAD_DIR / saved_name

    contents = await file.read()
    with open(file_path, "wb") as buffer:
        buffer.write(contents)

    uploaded_ads[ad_id] = {
        "original_filename": file.filename,
        "saved_filename": saved_name
    }

    return {
        "ad_id": ad_id,
        "filename": saved_name,
    }

@app.post("/analysis/start")
async def start_analysis(payload: dict):
    ad_id = payload.get("ad_id")

    if not ad_id:
        raise HTTPException(status_code=400, detail="ad_id is required")

    if ad_id not in uploaded_ads:
        raise HTTPException(status_code=404, detail="ad not found")

    saved_filename = uploaded_ads[ad_id]["saved_filename"]
    file_path = UPLOAD_DIR / saved_filename
    try:
        uploaded_file = upload_video(file_path)
        video_text = get_video_understanding(uploaded_file)
        session_id = str(uuid.uuid4())
        dur = get_video_duration(file_path)
        sessions[session_id] = {
            "ad_id": ad_id,
            "saved_filename": saved_filename,
            "duration_seconds": dur,
            "start_buffer_ms": 1500,
            "stop_buffer_ms": 1500,
            "video_text": video_text,
            "status": "collecting",
            "final_analysis": None,
            "raw_metrics": None
        }
        return {
            "session_id": session_id,
            "ad_id": ad_id,
            "duration_seconds": dur,
            "start_buffer_ms": 1500,
            "stop_buffer_ms": 1500,
            "status": "collecting"
        }

    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"Analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/analysis/result/{session_id}")
async def get_result(session_id: str):

    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="session not found")

    session = sessions[session_id]

    return {
        "status": session["status"],
        "result": session.get("final_analysis")
    }

def upload_video(video_file: Path):
    uploaded = client.files.upload(file=str(video_file))
    while getattr(uploaded, "state", None) and uploaded.state.name == "PROCESSING":
        time.sleep(10)
        uploaded = client.files.get(name=uploaded.name)
    return uploaded

def get_video_understanding(uploaded_file):
    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=[
            uploaded_file,
            """
            Analyze this advertisement video.
            Return:
            1) Full transcript with timestamps
            2) Scene summary describing what visually happens
            Format:
            TRANSCRIPT:
            [0.0-2.4] text
            SCENES:
            [0.0-2.4] description
            """
        ]
    )
    return response.text

def analyze_engagement(video_text, biometrics):
    prompt_text = f"""
    You are an expert advertisement analyst. Analyze the following video content and viewer biometrics to provide a detailed engagement report.

    VIDEO ANALYSIS:
    {video_text}

    VIEWER BIOMETRICS:
    {biometrics}

    You MUST return a valid JSON object with the following structure:
    {{
      "overallScore": 87,
      "hookScore": 8,
      "clarityScore": 7,
      "pacingScore": 6,
      "ctaScore": 7,
      "strongMoments": [
        {{
          "timestamp": "0:03 - 0:05",
          "description": "Product reveal with bold text overlay",
          "reason": "Viewer engagement increased sharply during this moment"
        }}
      ],
      "weakMoments": [
        {{
          "timestamp": "0:10 - 0:13",
          "description": "Extended product demo",
          "reason": "Viewer engagement dropped and pacing slowed"
        }}
      ],
      "strongPoints": [
        "Clear product reveal",
        "Strong emotional hook in opening line"
      ],
      "lowPoints": [
        "Middle section pacing slows down",
        "Call-to-action lacks urgency"
      ],
      "suggestions": [
        "Shorten the product demo by 2–3 seconds",
        "Add a stronger CTA phrase near the end",
        "Introduce branding earlier in the ad"
      ],
      "keyInsight": "Viewer engagement peaks during the product reveal, suggesting the ad's strongest element is the visual introduction of the product."
    }}
    """

    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt_text,
        config={
            "response_mime_type": "application/json"
        }
    )
    return response.text

def get_video_duration(video_path: Path):
    clip = VideoFileClip(str(video_path))
    duration = clip.duration
    clip.close()
    return duration

class MetricPoint(BaseModel):
    timestampMs: int
    pulseRate: Optional[float] = None
    pulseConfidence: Optional[float] = None
    breathingRate: Optional[float] = None
    breathingConfidence: Optional[float] = None

class SessionExport(BaseModel):
    sessionId: str
    adId: str
    deviceTimeZone: str
    metrics: List[MetricPoint]

@app.post("/session")
async def receive_session(session: SessionExport):
    session_id = session.sessionId

    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="session not found")

    if sessions[session_id]["ad_id"] != session.adId:
        raise HTTPException(status_code=400, detail="adId does not match session")

    sessions[session_id]["status"] = "processing"

    biometrics = {
        timestamp: metric.model_dump()
        for timestamp, metric in session.metrics.items()
    }

    sessions[session_id]["raw_metrics"] = biometrics

    try:
        video_text = sessions[session_id]["video_text"]
        final_analysis = analyze_engagement(video_text, biometrics)
        sessions[session_id]["final_analysis"] = final_analysis
        sessions[session_id]["status"] = "complete"

        return {
            "ok": True,
            "message": "session received",
            "status": "complete"
        }

    except Exception as e:
        sessions[session_id]["status"] = "error"
        raise HTTPException(status_code=500, detail=str(e))
