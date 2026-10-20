import io
import re
from datetime import datetime
from typing import List, Dict, Any, Optional

try:
    import pdfplumber
except ImportError:
    pdfplumber = None

DATE_RE = re.compile(r"\b(\d{4}[-/]\d{2}[-/]\d{2}|\d{2}[-/]\d{2}[-/]\d{4})\b")
Tymebank_DATE = re.compile(r"^\d{2} \w{3} \d{4}")
BALANCE_LINE = re.compile(r"(opening|closing|balance)\s*(?:balance)?\s*[:\-]?\s*R?\s*(-?\d[\d,\s]*(?:\.\d{1,2})?)", re.IGNORECASE)
LAST_AMOUNT_RE = re.compile(r"(-?)\s*R?\s*(\d{1,3}(?:[,\s]\d{3})*\.\d{2}|\d+\.\d{2})\s*$")

# TymeBank row parsing is done via _parse_tymebank_wrapped_row (token-based, more reliable than regex)
AMOUNT_TOKEN = re.compile(r"^-?\d[\d,]*\.\d{2}$")


def _normalize_amount(raw: str) -> float:
    if raw is None or raw.strip() in ("", "-"):
        return 0.0
    cleaned = raw.replace(",", "").replace(" ", "").strip()
    try:
        return float(cleaned)
    except ValueError:
        return 0.0


def _normalize_date(raw: str) -> Optional[str]:
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%d-%m-%Y", "%d/%m/%Y", "%d %b %Y", "%d %B %Y"):
        try:
            return datetime.strptime(raw, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def _extract_text_from_pdf(file_bytes: bytes, password: Optional[str] = None) -> str:
    if pdfplumber is None:
        raise RuntimeError("pdfplumber is not installed on the OCR service")
    text_parts: List[str] = []
    with pdfplumber.open(io.BytesIO(file_bytes), password=password or "") as pdf:
        for page in pdf.pages:
            txt = page.extract_text() or ""
            text_parts.append(txt)
            for table in page.extract_tables() or []:
                for row in table:
                    if row:
                        text_parts.append(" | ".join(cell or "" for cell in row))
    return "\n".join(text_parts)


def _extract_all_tables(file_bytes: bytes, password: Optional[str] = None) -> List[List[List[str]]]:
    """Return all tables across all pages, flattened."""
    if pdfplumber is None:
        raise RuntimeError("pdfplumber is not installed on the OCR service")
    all_tables: List[List[List[str]]] = []
    with pdfplumber.open(io.BytesIO(file_bytes), password=password or "") as pdf:
        for page in pdf.pages:
            for table in page.extract_tables() or []:
                if table:
                    all_tables.append(table)
    return all_tables


def detect_bank(text: str) -> str:
    t = (text or "").lower()
    if "tymebank" in t or "tyme bank" in t or "everyday account" in t:
        return "tymebank"
    if "first national bank" in t or "fnb" in t:
        return "fnb"
    if "amalgamated banks" in t or "absa" in t:
        return "absa"
    if "standard bank" in t:
        return "standardbank"
    if "nedbank" in t:
        return "nedbank"
    if "capitec" in t:
        return "capitec"
    return "generic"


def _merge_tymebank_rows(tables: List[List[List[str]]]) -> List[List[str]]:
    """Flatten all table rows from all tables into a single list of row-arrays.
    Keeps single-cell wrapped rows for later merging."""
    rows: List[List[str]] = []
    for table in tables:
        for row in table:
            if row:
                rows.append([cell or "" for cell in row])
    return rows


def _parse_tymebank_wrapped_row(text: str):
    """Parse a single-cell TymeBank row like:
       '01 Apr 2025 Description - 41.00 - 143.74\n509113513031'
       Returns {date, description, amount} dict or None."""
    first_line = text.split("\n")[0].strip()
    tokens = first_line.split()
    if len(tokens) < 6:
        return None
    if not (tokens[0].isdigit() and tokens[2].isdigit()):
        return None
    date_str = f"{tokens[0]} {tokens[1]} {tokens[2]}"
    iso = _normalize_date(date_str)
    if not iso:
        return None
    # Walk from the end: collect amount tokens (or "-") until non-amount
    tail = []
    rest = tokens[3:]
    for token in reversed(rest):
        if token == "-" or AMOUNT_TOKEN.match(token.replace(",", "")):
            tail.insert(0, token)
        else:
            break
    if len(tail) < 2:
        return None
    desc_end = len(tokens) - len(tail)
    description = " ".join(tokens[3:desc_end]).strip()
    amounts = [_normalize_amount(t) for t in tail]
    amount = 0.0
    if len(tail) == 2:
        amount = -abs(amounts[0])
    elif len(tail) == 3:
        fees, out, _balance = amounts
        amount = -((abs(out) if out else 0) + fees)
    elif len(tail) >= 4:
        fees, out, incoming, _balance = amounts[0], amounts[1], amounts[2], amounts[3]
        amount = (incoming or 0) - (abs(out) if out else 0) - fees
    if not description:
        description = "Transaction"
    return {
        "date": iso,
        "description": description[:180],
        "amount": round(amount, 2),
        "cropTag": None,
    }

def _parse_tymebank(tables: List[List[List[str]]]) -> Dict[str, Any]:
    rows = _merge_tymebank_rows(tables)
    opening: Optional[float] = None
    closing: Optional[float] = None
    transactions: List[Dict[str, Any]] = []
    last_entry: Optional[Dict[str, Any]] = None

    def push(entry: Dict[str, Any]):
        nonlocal last_entry
        if last_entry is not None:
            transactions.append(last_entry)
        last_entry = entry

    for row in rows:
        # Skip fully empty rows
        if not any(row):
            continue
        joined = " ".join(row).strip()
        if not joined:
            continue

        # Header / section rows
        low = joined.lower()
        if "opening balance" in low:
            nums = re.findall(r"\d[\d,]*\.\d{2}", joined)
            if nums:
                opening = _normalize_amount(nums[-1])
            continue
        if "closing balance" in low:
            nums = re.findall(r"\d[\d,]*\.\d{2}", joined)
            if nums:
                closing = _normalize_amount(nums[-1])
            continue
        if low.startswith("date description"):
            continue
        if low.startswith("everyday account") or low.startswith("portfolio summary"):
            continue
        if "all fees are inclusive" in low or "please inform us" in low:
            continue

        # Clean 6-column row: [date, description, fees, money_out, money_in, balance]
        if len(row) >= 6 and row[5]:
            date_str = row[0].strip()
            if Tymebank_DATE.match(date_str):
                iso = _normalize_date(date_str)
                if not iso:
                    continue
                description = row[1].strip().replace("\n", " ")[:180]
                fees = _normalize_amount(row[2])
                money_out = _normalize_amount(row[3])
                money_in = _normalize_amount(row[4])
                balance = _normalize_amount(row[5])

                # Debit transactions (money out) are negative from the account's perspective
                amount = (money_in if money_in > 0 else 0) - (money_out if money_out > 0 else 0) - fees
                push({
                    "date": iso,
                    "description": description or "Transaction",
                    "amount": round(amount, 2),
                    "cropTag": None,
                })
                continue

        # Wrapped single-cell row (all other cells are empty)
        populated = [c for c in row if c.strip()]
        if len(populated) == 1:
            parsed = _parse_tymebank_wrapped_row(populated[0])
            if parsed:
                push(parsed)
                continue

    if last_entry is not None:
        transactions.append(last_entry)

    if opening is None and transactions:
        opening = 0.0
    if closing is None and opening is not None:
        closing = round(opening + sum(t["amount"] for t in transactions), 2)

    return {
        "openingBalance": round(opening or 0.0, 2),
        "closingBalance": round(closing or 0.0, 2),
        "transactions": transactions,
    }


def _parse_generic(text: str) -> Dict[str, Any]:
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


def extract_statement(file_bytes: bytes, filename: str = "", password: Optional[str] = None) -> Dict[str, Any]:
    if not file_bytes:
        raise ValueError("Empty file")

    # Try password first
    try:
        text = _extract_text_from_pdf(file_bytes, password=password)
    except Exception as e:
        err = str(e).lower()
        if "password" in err or "encrypted" in err:
            if not password:
                raise ValueError("PDF is password-protected. Please provide the statement password (often your ID number or account number).")
            raise ValueError("Incorrect PDF password. Please try again.")
        raise

    if not text.strip():
        raise ValueError("No text could be extracted. This PDF may be a scanned image; OCR fallback is not yet enabled.")

    bank = detect_bank(text)
    if bank == "tymebank":
        try:
            tables = _extract_all_tables(file_bytes, password=password)
            result = _parse_tymebank(tables)
            if result["transactions"]:
                return result
        except Exception:
            pass  # fall through to generic

    result = _parse_generic(text)
    if not result["transactions"]:
        raise ValueError(f"No transaction lines were found ({bank} layout detected but not parsed). Please check the PDF layout.")
    return result




