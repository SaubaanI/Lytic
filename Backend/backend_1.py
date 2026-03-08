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
from pathlib import Path
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

        final_analysis = analyze_engagement(video_text, metrics_json)

        return final_analysis

    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"Analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

def upload_video(video_file: Path):
    uploaded = client.files.upload(file=str(video_file))

    while getattr(uploaded, "state", None) and uploaded.state.name == "PROCESSING":
        # Increased to 10 seconds to avoid hitting the 15 RPM free tier quota during video processing
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

    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=f"""
        You are analyzing advertisement engagement.

        Video Analysis:
        {video_text}

        Viewer biometrics:
        {biometrics}

        Tasks:
        1. Identify engaging moments
        2. Correlate biometrics with video events
        3. Rate hook, clarity, pacing, CTA
        4. Suggest improvements

        Return VALID JSON.
        """,
        config={
        "response_mime_type": "application/json"
        }
    )

    return response.text

class SessionMetric(BaseModel):
    timestampMs: int
    pulseRate: Optional[float] = None
    pulseConfidence: Optional[float] = None
    breathingRate: Optional[float] = None
    breathingConfidence: Optional[float] = None

class SessionExport(BaseModel):
    adId: str
    deviceTimeZone: str
    metrics: List[SessionMetric]

@app.post("/session")
async def receive_session(session: SessionExport):
    print(session.model_dump_json(indent = 2))
    return {"ok": True, "message": "session received"}
