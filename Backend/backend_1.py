from fastapi import FastAPI, UploadFile, File
from pathlib import Path
import shutil
app = FastAPI()
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

    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    return {
        "status": "success",
        "filename": file.filename,
        "content_type": file.content_type,
        "saved_to": str(file_path)
    }
