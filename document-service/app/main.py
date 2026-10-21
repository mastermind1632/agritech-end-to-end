"""Document service: POST /extract returns the text lines of a bank statement (PDF text layer or OCR)."""
import asyncio

from fastapi import FastAPI, File, HTTPException, UploadFile

from app.extract import extract, tesseract_available

MAX_BYTES = 15 * 1024 * 1024

app = FastAPI(title="AgriTech document service", version="1.0")


@app.get("/health")
def health():
    return {"status": "ok", "ocr": tesseract_available()}


@app.post("/extract")
async def extract_endpoint(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="File too large")
    # OCR is CPU-bound; run it off the event loop. Nothing is written to disk or kept.
    result = await asyncio.to_thread(extract, data, file.filename)
    return result.as_dict()
