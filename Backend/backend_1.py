from fastapi import FastAPI, UploadFile, File
from pathlib import Path
from fastapi.middleware.cors import CORSMiddleware
import shutil
app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def root():
    return {"message": "Backend running"}
UPLOAD_DIR = Path("Uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

@app.get("/")
def root():
    return {"message": "Backend is running"}

@app.get("/videos")
def get_videos():
    # Only return .mp4 files from the Uploads directory
    videos = []
    if UPLOAD_DIR.exists():
        for file in UPLOAD_DIR.glob("*.mp4"):
            videos.append({
                "id": file.name,
                "name": file.name,
                "date": "Uploaded", 
                "duration": "N/A"
            })
    return {"videos": videos}

@app.post("/upload-ad")
async def upload_ad(file: UploadFile = File(...)):
    file_path = UPLOAD_DIR / file.filename

    contents = await file.read()
    with open(file_path, "wb") as buffer:
        buffer.write(contents)

    return {
        "status": "success",
        "filename": file.filename,
        "content_type": file.content_type,
        "saved_to": str(file_path)
    }
