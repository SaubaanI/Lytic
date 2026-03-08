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

client = genai.Client(api_key=os.environ["GEMINI_API_KEY"])

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
        clip = VideoFileClip(str(file_path))
        duration_seconds = clip.duration
        clip.close()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"failed to read video: {str(e)}")

    return {
        "ad_id": ad_id,
        "duration_seconds": duration_seconds
    }

def transcription(video_file: Path):
   transcript_response = client.models.generate_content(
       model="gemini-2.0-flash",
       contents=[
           video_file,
           """
           Transcribe all spoken words in this ad.
           Include timestamps.
           Return plain text in this format:


           [0.0-2.4] text here
           [2.4-5.1] text here
           """
       ]
   )
   transcript_text = transcript_response.text
   return transcript_text


def summary(video_file: Path):
   summary_response = client.models.generate_content(
       model="gemini-2.0-flash",
       contents=[
           video_file,
           """
           Describe this advertisement scene by scene.
           Include timestamps and key moments such as:
           - hook
           - product reveal
           - call to action
           Return concise bullet points.
           """
       ]
   )
   scene_summary = summary_response.text
   return scene_summary

class SessionMetric(BaseModel):
    timestampMs: int
    pulseRate: Optional[float] = None
    pulseConfidence: Optional[float] = None
    breathingRate: Optional[float] = None
    breathingConfidence: Optional[float] = None

class SessionExport(BaseModel):
    adId: str
    sessionStartedAtMs: int
    sampleCount: int
    deviceTimeZone: str
    metrics: List[SessionMetric]

@app.post("/session")
async def receive_session(session: SessionExport):
    print(session.model_dump_json(indent = 2))
    return {"ok": True, "message": "session received"}
