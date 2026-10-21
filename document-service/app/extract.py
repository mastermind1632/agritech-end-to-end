"""Text extraction for bank statements.

Digital PDFs are read from their text layer with pdfplumber (fast and exact). Scanned PDFs and photos have no
text layer, so each page is rendered and read with Tesseract OCR. The service only returns text lines; parsing,
balance validation and categorisation happen in the Java API so there is one source of truth.

Privacy (POPIA): files are processed in memory and never written to disk or kept.
"""
from __future__ import annotations

import io
import shutil
import subprocess
import tempfile
from dataclasses import dataclass, field

MAX_PAGES = 40
MIN_TEXT_CHARS = 40  # below this, a PDF page is treated as a scan


@dataclass
class Extraction:
    method: str
    pages: int
    lines: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {"method": self.method, "pages": self.pages, "lines": self.lines, "warnings": self.warnings}


def detect(data: bytes, filename: str | None = None) -> str:
    if data[:4] == b"%PDF":
        return "pdf"
    if data[:3] == b"\xff\xd8\xff" or data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image"
    if data[:4] in (b"II*\x00", b"MM\x00*"):
        return "image"
    return "unknown"


def tesseract_available() -> bool:
    return shutil.which("tesseract") is not None


def ocr_image(image, lang: str = "eng") -> str:
    """Runs Tesseract on a PIL image. Uses pytesseract when installed, otherwise the CLI."""
    from PIL import ImageOps

    prepared = ImageOps.autocontrast(ImageOps.grayscale(image))
    # Small photos read badly; upscale so text is roughly 30px high.
    if prepared.width < 1600:
        factor = 1600 / max(prepared.width, 1)
        prepared = prepared.resize((int(prepared.width * factor), int(prepared.height * factor)))
    try:
        import pytesseract

        return pytesseract.image_to_string(prepared, lang=lang, config="--psm 6")
    except ImportError:
        with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
            prepared.save(tmp.name)
            out = subprocess.run(["tesseract", tmp.name, "stdout", "-l", lang, "--psm", "6"],
                                 capture_output=True, text=True, timeout=120, check=False)
            return out.stdout


def _clean_lines(text: str) -> list[str]:
    return [" ".join(line.split()) for line in (text or "").splitlines() if line.strip()]


def extract_pdf(data: bytes) -> Extraction:
    import pdfplumber

    lines: list[str] = []
    warnings: list[str] = []
    ocr_pages = 0
    with pdfplumber.open(io.BytesIO(data)) as pdf:
        pages = len(pdf.pages)
        if pages > MAX_PAGES:
            warnings.append(f"Only the first {MAX_PAGES} of {pages} pages were read.")
        for page in pdf.pages[:MAX_PAGES]:
            text = page.extract_text(x_tolerance=1.5, y_tolerance=3) or ""
            if len(text.strip()) >= MIN_TEXT_CHARS:
                lines.extend(_clean_lines(text))
                continue
            if not tesseract_available():
                warnings.append("This PDF looks scanned, but OCR (Tesseract) is not installed on the server.")
                break
            image = page.to_image(resolution=300).original
            lines.extend(_clean_lines(ocr_image(image)))
            ocr_pages += 1
    if ocr_pages:
        warnings.append(f"{ocr_pages} page(s) were read with OCR. Check amounts carefully; the bank's CSV export is more accurate.")
    method = "pdf-text" if ocr_pages == 0 else ("ocr" if ocr_pages == min(pages, MAX_PAGES) else "pdf-mixed")
    return Extraction(method=method, pages=pages, lines=lines, warnings=warnings)


def extract_image(data: bytes) -> Extraction:
    from PIL import Image, ImageOps

    if not tesseract_available():
        return Extraction(method="ocr", pages=1, warnings=["OCR (Tesseract) is not installed on the server."])
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(data)))
    lines = _clean_lines(ocr_image(image))
    return Extraction(method="ocr", pages=1, lines=lines,
                      warnings=["Read from a photo with OCR. Check every amount; a CSV export or PDF from the bank is more accurate."])


def extract(data: bytes, filename: str | None = None) -> Extraction:
    kind = detect(data, filename)
    if kind == "pdf":
        return extract_pdf(data)
    if kind == "image":
        return extract_image(data)
    return Extraction(method="unsupported", pages=0, warnings=["Unsupported file type. Upload a PDF, JPEG or PNG."])
