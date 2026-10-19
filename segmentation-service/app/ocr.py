import io
import re
from datetime import datetime
from typing import List, Dict, Any, Optional

try:
    import pdfplumber
except ImportError:
    pdfplumber = None

DATE_RE = re.compile(r"\b(\d{4}[-/]\d{2}[-/]\d{2}|\d{2}[-/]\d{2}[-/]\d{4})\b")
BALANCE_LINE = re.compile(r"(opening|closing|balance)\s*(?:balance)?\s*[:\-]?\s*R?\s*(-?\d[\d,\s]*(?:\.\d{1,2})?)", re.IGNORECASE)
# Amount: allow optional leading minus, then digits/commas, then optional .xx. Anchored to end of string to grab the LAST amount in a line.
LAST_AMOUNT_RE = re.compile(r"(-?)\s*R?\s*(\d{1,3}(?:[,\s]\d{3})*\.\d{2}|\d+\.\d{2})\s*$")


def _normalize_amount(raw: str) -> float:
    cleaned = raw.replace(",", "").replace(" ", "").strip()
    try:
        return float(cleaned)
    except ValueError:
        return 0.0


def _normalize_date(raw: str) -> Optional[str]:
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%d-%m-%Y", "%d/%m/%Y"):
        try:
            return datetime.strptime(raw, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def _extract_text_from_pdf(file_bytes: bytes) -> str:
    if pdfplumber is None:
        raise RuntimeError("pdfplumber is not installed on the OCR service")
    text_parts: List[str] = []
    with pdfplumber.open(io.BytesIO(file_bytes)) as pdf:
        for page in pdf.pages:
            txt = page.extract_text() or ""
            text_parts.append(txt)
            for table in page.extract_tables() or []:
                for row in table:
                    if row:
                        text_parts.append(" | ".join(cell or "" for cell in row))
    return "\n".join(text_parts)


def _parse_statement(text: str) -> Dict[str, Any]:
    opening: Optional[float] = None
    closing: Optional[float] = None
    transactions: List[Dict[str, Any]] = []

    for line in text.splitlines():
        line_stripped = line.strip()
        if not line_stripped:
            continue

        bal = BALANCE_LINE.search(line_stripped)
        if bal:
            label = bal.group(1).lower()
            value = _normalize_amount(bal.group(2))
            if "opening" in label:
                opening = value
            elif "closing" in label:
                closing = value

        date_match = DATE_RE.search(line_stripped)
        if not date_match:
            continue
        iso_date = _normalize_date(date_match.group(1))
        if not iso_date:
            continue

        after_date = line_stripped[date_match.end():].strip()
        last = LAST_AMOUNT_RE.search(after_date)
        if not last:
            continue
        sign_str = last.group(1)
        amount = _normalize_amount(last.group(2))
        if sign_str == "-" or "debit" in line_stripped.lower() or "payment" in line_stripped.lower():
            amount = -abs(amount)
        # Description is everything before the amount match
        description = after_date[: last.start()].strip(" |,")
        if not description:
            description = "Transaction"
        transactions.append({
            "date": iso_date,
            "description": description[:120],
            "amount": round(amount, 2),
            "cropTag": None,
        })

    if opening is None and transactions:
        opening = 0.0
    if closing is None and opening is not None and transactions:
        closing = round(opening + sum(t["amount"] for t in transactions), 2)

    return {
        "openingBalance": round(opening or 0.0, 2),
        "closingBalance": round(closing or 0.0, 2),
        "transactions": transactions,
    }


def extract_statement(file_bytes: bytes, filename: str = "") -> Dict[str, Any]:
    if not file_bytes:
        raise ValueError("Empty file")
    text = _extract_text_from_pdf(file_bytes)
    if not text.strip():
        raise ValueError("No text could be extracted. This PDF may be a scanned image; OCR fallback is not yet enabled.")
    parsed = _parse_statement(text)
    if not parsed["transactions"]:
        raise ValueError("No transaction lines were found. Please check the PDF layout.")
    return parsed
