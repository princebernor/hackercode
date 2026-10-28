"""
Extract the placement-training problem list from "CodeSheet File.pdf".

Usage:
    pip install pymupdf
    python scripts/extract_problems.py "path/to/CodeSheet File.pdf" src/main/resources/data/problems.json

The PDF layout is consistent:
  * 16.5pt serif lines  -> topic headings (e.g. "Array - Data structure")
  * 13.5pt serif lines  -> subsection / pattern headings (may wrap over several lines)
  * 12pt lines "N. ..." -> numbered problem entries (wrapped text continues on following 12pt lines)
  * link annotations    -> practice URLs, matched to entries by vertical position on the page
  * 6pt lines           -> running header / footer, ignored

The script fails loudly if it does not find exactly 442 sequentially numbered entries.
"""
import json
import re
import sys

import pymupdf

EXPECTED = 442
ITEM_RE = re.compile(r"^(\d+)\.\s+(.*)$")


def slugify(text):
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s[:80] or "problem"


def split_title(source_text):
    """Split 'Title(explanatory note) trailing' into (title, note).

    A parenthetical counts as a note only when it reads like prose (3+ words), so short
    qualifiers such as 'Power(x,n)' stay in the title. The verbatim text is always kept
    separately as sourceText, so this split is presentation only.
    """
    for m in re.finditer(r"\(([^()]*)\)?", source_text):
        inner = m.group(1)
        if len(inner.split()) >= 3:
            title = source_text[: m.start()].strip()
            note = source_text[m.start():].strip()
            if note.startswith("(") and note.endswith(")") and note.count("(") == 1:
                note = note[1:-1].strip()
            if title:
                return title, note
    return source_text.strip(), None


def extract(pdf_path):
    doc = pymupdf.open(pdf_path)
    entries, topics = [], []
    topic = subsection = None
    heading_buf = None  # (kind, text) for headings that wrap across lines
    last_heading_y = None
    current = None
    unmatched_links = []

    def flush_heading():
        nonlocal heading_buf, topic, subsection
        if heading_buf is None:
            return
        kind, text = heading_buf
        text = re.sub(r"\s+", " ", text).strip()
        if kind == "topic":
            topic = {"name": text, "subsections": []}
            topics.append(topic)
            subsection = None
        else:
            if topic is None:
                raise ValueError(f"Subsection before any topic: {text}")
            subsection = text
            topic["subsections"].append(text)
        heading_buf = None

    for page in doc:
        lines = []
        for block in page.get_text("dict")["blocks"]:
            for line in block.get("lines", []):
                spans = [s for s in line["spans"] if s["text"].strip()]
                if not spans:
                    continue
                text = "".join(s["text"] for s in line["spans"]).strip()
                lines.append((round(spans[0]["size"], 1), text, line["bbox"]))
        lines.sort(key=lambda l: (l[2][1], l[2][0]))

        page_items = []
        for size, text, bbox in lines:
            if size <= 7 or size >= 20:
                continue  # header/footer and document title
            if size >= 16:
                if heading_buf and heading_buf[0] == "topic" and last_heading_y is not None and bbox[1] - last_heading_y < 30:
                    heading_buf = ("topic", heading_buf[1] + " " + text)
                else:
                    flush_heading()
                    heading_buf = ("topic", text)
                last_heading_y = bbox[1]
                continue
            if size >= 13:
                if heading_buf and heading_buf[0] == "sub" and last_heading_y is not None and bbox[1] - last_heading_y < 25:
                    heading_buf = ("sub", heading_buf[1] + " " + text)
                else:
                    flush_heading()
                    heading_buf = ("sub", text)
                last_heading_y = bbox[1]
                continue
            flush_heading()
            m = ITEM_RE.match(text)
            if m:
                if topic is None:
                    continue  # introduction paragraph
                current = {
                    "number": int(m.group(1)),
                    "text": m.group(2).strip(),
                    "topic": topic["name"],
                    "subsection": subsection,
                    "rects": [(page.number, bbox)],
                    "urls": [],
                }
                entries.append(current)
                page_items.append(current)
            elif current is not None and topic is not None:
                current["text"] += " " + text.strip()
                current["rects"].append((page.number, bbox))
                if current not in page_items:
                    page_items.append(current)

        for link in page.get_links():
            uri = link.get("uri")
            if not uri:
                continue
            r = link["from"]
            cy = (r.y0 + r.y1) / 2
            for item in page_items:
                if any(pn == page.number and b[1] - 2 <= cy <= b[3] + 2 for pn, b in item["rects"]):
                    if uri not in item["urls"]:
                        item["urls"].append(uri)
                    break
            else:
                unmatched_links.append((page.number + 1, uri))
    flush_heading()
    if unmatched_links:
        raise SystemExit(f"{len(unmatched_links)} link annotations could not be matched to an entry: {unmatched_links[:5]}")
    return topics, entries


def build(pdf_path):
    topics, raw = extract(pdf_path)
    numbers = [e["number"] for e in raw]
    if numbers != list(range(1, EXPECTED + 1)):
        missing = sorted(set(range(1, EXPECTED + 1)) - set(numbers))
        raise SystemExit(f"Expected entries 1..{EXPECTED} in order; got {len(numbers)} entries, missing={missing[:20]}")

    problems, seen_slugs = [], set()
    for order, e in enumerate(raw, start=1):
        source_text = re.sub(r"\s+", " ", e["text"]).strip()
        title, note = split_title(source_text)
        slug = slugify(title)
        if slug in seen_slugs:
            slug = f"{slug}-{e['number']}"
        seen_slugs.add(slug)
        problems.append({
            "id": f"cf-{e['number']:03d}",
            "number": e["number"],
            "order": order,
            "slug": slug,
            "title": title,
            "sourceText": source_text,
            "sourceNote": note,
            "topic": e["topic"],
            "subsection": e["subsection"],
            "practiceUrls": e["urls"],
        })
    return {
        "source": "CodeSheet File.pdf (Curious Freaks Coding Sheet - Full Problem List)",
        "expectedCount": EXPECTED,
        "topics": topics,
        "problems": problems,
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    data = build(sys.argv[1])
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    no_url = sum(1 for p in data["problems"] if not p["practiceUrls"])
    print(f"Wrote {len(data['problems'])} problems in {len(data['topics'])} topics; {no_url} without a practice URL")
