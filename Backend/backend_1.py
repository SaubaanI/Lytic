from fastapi import FastAPI, UploadFile, File
from pathlib import Path
from fastapi.middleware.cors import CORSMiddleware
import shutil
app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://127.0.0.1:3000"],
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
