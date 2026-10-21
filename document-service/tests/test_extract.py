import io
import unittest

from app.extract import detect, extract, tesseract_available

STATEMENT_LINES = [
    "FIRST NATIONAL BANK",
    "Opening Balance 12 500.00 Cr",
    "02 Mar POS Purchase Engen Tzaneen 1 250.00 11 250.00 Cr",
    "05 Mar Payment From Freshmark 8 400.00 19 650.00 Cr",
    "Closing Balance 19 650.00 Cr",
]


def make_pdf(lines):
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    y = 800
    for line in lines:
        c.drawString(50, y, line)
        y -= 20
    c.save()
    return buf.getvalue()


def make_image(lines):
    from PIL import Image, ImageDraw, ImageFont

    img = Image.new("RGB", (1800, 60 * len(lines) + 80), "white")
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 36)
    except OSError:
        font = ImageFont.load_default()
    for i, line in enumerate(lines):
        draw.text((40, 40 + i * 60), line, fill="black", font=font)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


class ExtractTest(unittest.TestCase):
    def test_detects_file_types(self):
        self.assertEqual(detect(b"%PDF-1.7 ..."), "pdf")
        self.assertEqual(detect(b"\x89PNG\r\n\x1a\n...."), "image")
        self.assertEqual(detect(b"\xff\xd8\xff\xe0...."), "image")
        self.assertEqual(detect(b"Date,Amount\n"), "unknown")

    def test_reads_text_layer_of_digital_pdf(self):
        try:
            data = make_pdf(STATEMENT_LINES)
        except ImportError:
            self.skipTest("reportlab not installed")
        result = extract(data, "statement.pdf")
        self.assertEqual(result.method, "pdf-text")
        self.assertEqual(result.pages, 1)
        self.assertIn("02 Mar POS Purchase Engen Tzaneen 1 250.00 11 250.00 Cr", result.lines)
        self.assertEqual(len(result.lines), len(STATEMENT_LINES))

    @unittest.skipUnless(tesseract_available(), "tesseract not installed")
    def test_reads_photo_with_ocr(self):
        result = extract(make_image(STATEMENT_LINES), "photo.png")
        self.assertEqual(result.method, "ocr")
        text = " ".join(result.lines)
        self.assertIn("Engen", text)
        self.assertIn("8 400.00", text)
        self.assertTrue(result.warnings)

    def test_rejects_unknown_files(self):
        result = extract(b"hello", "notes.txt")
        self.assertEqual(result.method, "unsupported")
        self.assertTrue(result.warnings)


if __name__ == "__main__":
    unittest.main()
