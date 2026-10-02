# convert_pdf.py
# Usage: python convert_pdf.py <input.pdf> <output.docx>

import sys
import fitz  # pymupdf
import ocrmypdf
from pdf2docx import Converter


def has_real_text(pdf_path: str, min_chars: int = 20) -> bool:
    """Quick check: does this PDF already have a usable text layer?"""
    doc = fitz.open(pdf_path)
    total_chars = 0
    for page in doc:
        total_chars += len(page.get_text().strip())
        if total_chars >= min_chars:
            doc.close()
            return True
    doc.close()
    return False


def main():
    if len(sys.argv) != 3:
        print("Usage: python convert_pdf.py <input.pdf> <output.docx>")
        sys.exit(1)

    input_pdf = sys.argv[1]
    output_docx = sys.argv[2]
    working_pdf = input_pdf

    if not has_real_text(input_pdf):
        print("No usable text layer detected — running OCR...")
        ocr_output = input_pdf.replace(".pdf", "_ocr.pdf")
        ocrmypdf.ocr(
            input_pdf,
            ocr_output,
            force_ocr=True,
            deskew=True,
            optimize=1,
        )
        working_pdf = ocr_output
    else:
        print("Text layer found — skipping OCR.")

    print("Converting to DOCX...")
    cv = Converter(working_pdf)
    cv.convert(output_docx)
    cv.close()
    print("Done:", output_docx)


if __name__ == "__main__":
    main()