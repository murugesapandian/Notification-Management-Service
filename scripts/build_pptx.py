#!/usr/bin/env python3
"""Generates docs/Notification-Management-Service-Overview.pptx.

Run: python3 scripts/build_pptx.py
Regenerate any time the content needs updating — this script is the source of
truth for the deck, not the .pptx file itself (which is binary and not diffable).
"""
import os
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

# ---- palette -----------------------------------------------------------
NAVY = RGBColor(0x0F, 0x1B, 0x3D)
BLUE = RGBColor(0x1E, 0x4E, 0xD8)
BLUE_DARK = RGBColor(0x16, 0x3A, 0xA8)
LIGHT_BG = RGBColor(0xF4, 0xF6, 0xF9)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
TEXT = RGBColor(0x1A, 0x22, 0x33)
MUTED = RGBColor(0x61, 0x6D, 0x82)
GREEN = RGBColor(0x02, 0x7A, 0x48)
GREEN_BG = RGBColor(0xEC, 0xFD, 0xF3)
AMBER = RGBColor(0xB5, 0x47, 0x08)
AMBER_BG = RGBColor(0xFF, 0xFA, 0xEB)
RED = RGBColor(0xD9, 0x2D, 0x20)
RED_BG = RGBColor(0xFE, 0xF3, 0xF2)
BORDER = RGBColor(0xE2, 0xE6, 0xED)

SLIDE_W = Inches(13.333)
SLIDE_H = Inches(7.5)

prs = Presentation()
prs.slide_width = SLIDE_W
prs.slide_height = SLIDE_H
BLANK = prs.slide_layouts[6]


def add_slide():
    return prs.slides.add_slide(BLANK)


def fill_bg(slide, color=WHITE):
    rect = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SLIDE_W, SLIDE_H)
    rect.fill.solid()
    rect.fill.fore_color.rgb = color
    rect.line.fill.background()
    rect.shadow.inherit = False
    # push to back
    spTree = slide.shapes._spTree
    spTree.remove(rect._element)
    spTree.insert(2, rect._element)
    return rect


def textbox(slide, l, t, w, h, text, size=18, color=TEXT, bold=False, align=PP_ALIGN.LEFT,
            font="Calibri", line_spacing=1.0, anchor=None, wrap=True):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = wrap
    if anchor:
        tf.vertical_anchor = anchor
    lines = text.split("\n")
    for i, line in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = line
        p.alignment = align
        p.line_spacing = line_spacing
        for run in p.runs:
            run.font.size = Pt(size)
            run.font.bold = bold
            run.font.color.rgb = color
            run.font.name = font
    return box


def bullets(slide, l, t, w, h, items, size=14, color=TEXT, bold_first=False, font="Calibri",
            space_after=8, bullet_color=None):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        if isinstance(item, tuple):
            text, level = item
        else:
            text, level = item, 0
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        prefix = "•  " if level == 0 else "-  "
        p.text = prefix + text
        p.level = 0
        p.space_after = Pt(space_after)
        p.line_spacing = 1.15
        for run in p.runs:
            run.font.size = Pt(size if level == 0 else size - 1)
            run.font.color.rgb = color if level == 0 else MUTED
            run.font.name = font
            run.font.bold = False
        if level > 0:
            p_el = p._pPr if p._pPr is not None else p.get_or_add_pPr()
            p_el.set('marL', str(Emu(Inches(0.35))))
            p_el.set('indent', str(-Emu(Inches(0.2))))
    return box


def box_shape(slide, l, t, w, h, fill=WHITE, line=BORDER, radius=True):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shp = slide.shapes.add_shape(shape_type, l, t, w, h)
    shp.fill.solid()
    shp.fill.fore_color.rgb = fill
    shp.line.color.rgb = line
    shp.line.width = Pt(1)
    shp.shadow.inherit = False
    if radius:
        try:
            shp.adjustments[0] = 0.06
        except Exception:
            pass
    return shp


def shape_with_text(slide, l, t, w, h, title, subtitle=None, fill=WHITE, line=BORDER,
                     title_color=TEXT, title_size=13, subtitle_color=MUTED, subtitle_size=10,
                     bold=True, align=PP_ALIGN.CENTER):
    shp = box_shape(slide, l, t, w, h, fill=fill, line=line)
    tf = shp.text_frame
    tf.word_wrap = True
    tf.margin_left = Emu(Inches(0.05))
    tf.margin_right = Emu(Inches(0.05))
    tf.margin_top = Emu(Inches(0.03))
    tf.margin_bottom = Emu(Inches(0.03))
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.text = title
    p.alignment = align
    for run in p.runs:
        run.font.size = Pt(title_size)
        run.font.bold = bold
        run.font.color.rgb = title_color
    if subtitle:
        p2 = tf.add_paragraph()
        p2.text = subtitle
        p2.alignment = align
        for run in p2.runs:
            run.font.size = Pt(subtitle_size)
            run.font.color.rgb = subtitle_color
    return shp


def arrow(slide, x1, y1, x2, y2, color=MUTED, width=1.25, dashed=False):
    conn = slide.shapes.add_connector(2, x1, y1, x2, y2)  # 2 = straight
    conn.line.color.rgb = color
    conn.line.width = Pt(width)
    line = conn.line._get_or_add_ln()
    tail = line.makeelement(qn('a:tailEnd'), {'type': 'triangle', 'w': 'med', 'len': 'med'})
    line.append(tail)
    if dashed:
        d = line.makeelement(qn('a:prstDash'), {'val': 'dash'})
        line.append(d)
    return conn


def header(slide, kicker, title, dark=False):
    fill_bg(slide, NAVY if dark else WHITE)
    kicker_color = RGBColor(0x9D, 0xB2, 0xF2) if dark else BLUE
    title_color = WHITE if dark else NAVY
    textbox(slide, Inches(0.6), Inches(0.35), Inches(8), Inches(0.35), kicker.upper(),
            size=12, color=kicker_color, bold=True)
    textbox(slide, Inches(0.6), Inches(0.62), Inches(11.5), Inches(0.7), title,
            size=28, color=title_color, bold=True)
    line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.6), Inches(1.32), Inches(1.1), Pt(3))
    line.fill.solid()
    line.fill.fore_color.rgb = BLUE
    line.line.fill.background()
    line.shadow.inherit = False
    return


def footer(slide, text, dark=False):
    color = RGBColor(0x6C, 0x7A, 0xA8) if dark else MUTED
    textbox(slide, Inches(0.6), Inches(7.08), Inches(9), Inches(0.3), text, size=9.5, color=color)
    textbox(slide, Inches(11.6), Inches(7.08), Inches(1.2), Inches(0.3), "Schwab Internal",
            size=9.5, color=color, align=PP_ALIGN.RIGHT)


def badge(slide, l, t, w, h, text, fill, color, size=10):
    shp = box_shape(slide, l, t, w, h, fill=fill, line=fill)
    shp.adjustments[0] = 0.5
    tf = shp.text_frame
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf.margin_left = Emu(Inches(0.02))
    tf.margin_right = Emu(Inches(0.02))
    tf.margin_top = 0
    tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.text = text
    p.alignment = PP_ALIGN.CENTER
    for run in p.runs:
        run.font.size = Pt(size)
        run.font.bold = True
        run.font.color.rgb = color


# =========================================================================
# Slide 1 — Title
# =========================================================================
s = add_slide()
fill_bg(s, NAVY)
accent = slide_accent = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, Inches(6.55), SLIDE_W, Inches(0.06))
accent.fill.solid(); accent.fill.fore_color.rgb = BLUE; accent.line.fill.background(); accent.shadow.inherit = False

textbox(s, Inches(0.9), Inches(2.35), Inches(11), Inches(0.4), "AI-ASSISTED SOFTWARE ENGINEERING ASSIGNMENT",
        size=14, color=RGBColor(0x9D, 0xB2, 0xF2), bold=True)
textbox(s, Inches(0.9), Inches(2.75), Inches(11.5), Inches(1.4), "Notification Management Service",
        size=42, color=WHITE, bold=True)
textbox(s, Inches(0.9), Inches(3.75), Inches(10.5), Inches(0.9),
        "A production-oriented prototype demonstrating requirement understanding, task\n"
        "decomposition, and validation rigor across greenfield, brownfield, and ambiguous\n"
        "requirement engineering scenarios.",
        size=15, color=RGBColor(0xC9, 0xD4, 0xEE), line_spacing=1.3)

for i, (label, val) in enumerate([
    ("Backend", "Spring Boot 3 / Java 17"),
    ("Frontend", "React 19 / TypeScript"),
    ("Tests", "76 passing"),
    ("Coverage", "96.4% overall"),
]):
    x = Inches(0.9 + i * 2.9)
    badge_box = box_shape(s, x, Inches(5.15), Inches(2.6), Inches(0.95), fill=RGBColor(0x18, 0x28, 0x54), line=RGBColor(0x2B, 0x3D, 0x73))
    tf = badge_box.text_frame
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf.margin_left = Inches(0.15)
    p = tf.paragraphs[0]
    p.text = val
    for r in p.runs:
        r.font.size = Pt(15); r.font.bold = True; r.font.color.rgb = WHITE
    p2 = tf.add_paragraph()
    p2.text = label.upper()
    for r in p2.runs:
        r.font.size = Pt(9.5); r.font.color.rgb = RGBColor(0x9D, 0xB2, 0xF2)

textbox(s, Inches(0.9), Inches(6.7), Inches(8), Inches(0.4),
        "Notification Management Service — Architecture & Delivery Overview", size=10.5,
        color=RGBColor(0x6C, 0x7A, 0xA8))

# =========================================================================
# Slide 2 — Objective & Scope
# =========================================================================
s = add_slide()
header(s, "Assignment", "Objective & Scope")
bullets(s, Inches(0.6), Inches(1.65), Inches(6.0), Inches(4.8), [
    "Build a working prototype: alert requests in, notifications delivered through",
    ("configurable channels — engineer-led execution, AI as accelerator", 1),
    "Demonstrate requirement understanding, decomposition, multi-step execution,",
    ("and output validation — not autonomous orchestration", 1),
    "Three required engineering scenarios:",
    ("Greenfield — initial notification-management capability", 1),
    ("Brownfield — multi-layer enhancement to the shipped system", 1),
    ("Ambiguous requirement — resolve real open questions, then build", 1),
    "Deliverables: working prototype, architecture overview, setup instructions,",
    ("testing approach/limitations/trade-offs, and this deck", 1),
], size=15)

# right column: functional requirements checklist card
card = box_shape(s, Inches(7.0), Inches(1.65), Inches(5.7), Inches(4.9), fill=LIGHT_BG)
textbox(s, Inches(7.3), Inches(1.85), Inches(5), Inches(0.35), "Functional coverage (section 4)", size=13, bold=True, color=NAVY)
reqs = [
    "4.1  Notification submission API",
    "4.2  Status retrieval (overall + per recipient/channel)",
    "4.3  Channel routing (requested / severity / preference)",
    "4.4  Deduplication & idempotency (2 boundaries, enforced)",
    "4.5  Retry & failure classification (6 categories)",
    "4.9  Audit history (no sensitive payloads)",
]
y = 2.3
for r in reqs:
    check = slide_check = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(7.3), Inches(y), Inches(0.22), Inches(0.22))
    check.fill.solid(); check.fill.fore_color.rgb = GREEN; check.line.fill.background(); check.shadow.inherit = False
    ctf = check.text_frame; ctf.margin_left=0; ctf.margin_right=0; ctf.margin_top=0; ctf.margin_bottom=0
    ctf.vertical_anchor = MSO_ANCHOR.MIDDLE
    cp = ctf.paragraphs[0]; cp.text = "✓"; cp.alignment = PP_ALIGN.CENTER
    for rr in cp.runs: rr.font.size = Pt(11); rr.font.bold=True; rr.font.color.rgb = WHITE
    textbox(s, Inches(7.65), Inches(y-0.05), Inches(4.9), Inches(0.35), r, size=12.5, color=TEXT)
    y += 0.55
footer(s, "docs/architecture-overview.md")

# =========================================================================
# Slide 3 — High-level architecture
# =========================================================================
s = add_slide()
header(s, "Architecture", "System Components & Control Flow")

def layer_box(x, y, w, h, title, items, fill=WHITE, title_color=NAVY):
    box_shape(s, x, y, w, h, fill=fill)
    textbox(s, x + Inches(0.12), y + Inches(0.06), w - Inches(0.24), Inches(0.3), title, size=11.5, bold=True, color=title_color)
    b = s.shapes.add_textbox(x + Inches(0.12), y + Inches(0.4), w - Inches(0.24), h - Inches(0.45))
    tf = b.text_frame; tf.word_wrap = True
    for i, it in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = "• " + it
        p.space_after = Pt(2)
        for r in p.runs:
            r.font.size = Pt(10); r.font.color.rgb = TEXT

top = Inches(1.55)
layer_box(Inches(0.5), top, Inches(2.6), Inches(1.0), "CLIENTS", ["Source systems", "React UI (separate repo)"], fill=RGBColor(0xEE, 0xF1, 0xF6))
layer_box(Inches(3.35), top, Inches(2.6), Inches(1.0), "API LAYER", ["NotificationController", "GlobalExceptionHandler"], fill=GREEN_BG)
layer_box(Inches(6.2), top, Inches(3.1), Inches(1.0), "APPLICATION (use cases)", ["Submission, Routing, Retry,", "Orchestration, Aggregation, Ack"], fill=AMBER_BG)
layer_box(Inches(9.55), top, Inches(3.25), Inches(1.0), "INFRASTRUCTURE", ["Workers, ChannelProviderRegistry,", "Repositories"], fill=RED_BG)

arrow(s, Inches(3.1), top + Inches(0.5), Inches(3.35), top + Inches(0.5))
arrow(s, Inches(5.95), top + Inches(0.5), Inches(6.2), top + Inches(0.5))
arrow(s, Inches(9.3), top + Inches(0.5), Inches(9.55), top + Inches(0.5))

mid = Inches(2.85)
box_shape(s, Inches(0.5), mid, Inches(12.3), Inches(2.05), fill=LIGHT_BG)
textbox(s, Inches(0.7), mid + Inches(0.08), Inches(6), Inches(0.3), "Async delivery pipeline", size=12, bold=True, color=NAVY)

nodes = [
    ("Submit\n(sync)", 0.8), ("Event\nlistener", 2.55), ("Orchestrate\n(routing)", 4.3),
    ("DeliveryWorker\n(poll)", 6.15), ("DeliveryDispatcher\n(claim+dispatch)", 8.15),
    ("ChannelProvider\n(Email/SMS/Push/Slack)", 10.5),
]
ny = mid + Inches(0.55)
prev_right = None
for label, x in nodes:
    w = Inches(1.6) if "Provider" not in label and "Dispatcher" not in label else Inches(1.9)
    shp = shape_with_text(s, Inches(x), ny, w, Inches(0.85), label, fill=WHITE, title_size=10.5)
    if prev_right is not None:
        arrow(s, prev_right, ny + Inches(0.42), Inches(x), ny + Inches(0.42))
    prev_right = Inches(x) + w

textbox(s, Inches(0.7), mid + Inches(1.5), Inches(11.8), Inches(0.45),
        "DB-backed poll queue (not a broker) — transactional, testable, no extra infra for the prototype; see trade-off on architecture doc §6.",
        size=10.5, color=MUTED)

bottom = Inches(5.15)
box_shape(s, Inches(0.5), bottom, Inches(12.3), Inches(1.55), fill=WHITE)
textbox(s, Inches(0.7), bottom + Inches(0.08), Inches(4), Inches(0.3), "Cross-cutting", size=12, bold=True, color=NAVY)
bullets(s, Inches(0.7), bottom + Inches(0.4), Inches(12), Inches(1.1), [
    "AuditService — every accept/route/queue/attempt/succeed/fail/retry event, structured metadata only, never message bodies or credentials",
    "IdempotencyCleanupJob & EscalationJob — scheduled, actively enforce documented policies rather than leaving them aspirational",
], size=11)
footer(s, "Package layout mirrors this diagram: api / application / domain / infrastructure — see docs/architecture-overview.md")

# =========================================================================
# Slide 4 — Tech stack & design principles
# =========================================================================
s = add_slide()
header(s, "Engineering foundations", "Tech Stack & Design Principles")

box_shape(s, Inches(0.6), Inches(1.6), Inches(6.0), Inches(4.95), fill=LIGHT_BG)
textbox(s, Inches(0.85), Inches(1.8), Inches(5.5), Inches(0.3), "Tech stack", size=13, bold=True, color=NAVY)
stack_rows = [
    ("Backend", "Java 17, Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator)"),
    ("Database", "H2 (dev/test, Postgres-compatible mode) / PostgreSQL (prod profile)"),
    ("Migrations", "Flyway, versioned (V1 greenfield, V2 brownfield, V3 ambiguous)"),
    ("Patterns", "Hexagonal layering, Strategy (ChannelProvider), Registry"),
    ("API docs", "springdoc-openapi / Swagger UI"),
    ("Backend tests", "JUnit 5, Mockito, AssertJ, Awaitility, JaCoCo, Failsafe"),
    ("Frontend", "React 19, TypeScript, Vite 8, React Router 7"),
    ("Build", "Maven (backend), npm (frontend) — separate git repos"),
]
y = 2.25
for label, val in stack_rows:
    textbox(s, Inches(0.85), Inches(y), Inches(1.5), Inches(0.4), label, size=11.5, bold=True, color=BLUE)
    textbox(s, Inches(2.3), Inches(y), Inches(4.15), Inches(0.5), val, size=11, color=TEXT)
    y += 0.53

box_shape(s, Inches(6.9), Inches(1.6), Inches(5.85), Inches(4.95), fill=WHITE)
textbox(s, Inches(7.15), Inches(1.8), Inches(5), Inches(0.3), "Design principles applied", size=13, bold=True, color=NAVY)
bullets(s, Inches(7.15), Inches(2.2), Inches(5.4), Inches(4.2), [
    "Modular — hexagonal layering (api / application / domain / infrastructure);",
    ("business logic has no JPA/HTTP types leaked into it", 1),
    "Testable — Clock injected everywhere time matters; retry/escalation timing",
    ("externalized as config, not hardcoded, so tests run in seconds not minutes", 1),
    "Reliable — DB-level unique constraints back every dedup boundary, not just",
    ("application logic; optimistic-lock conflicts detected and retried, not ignored", 1),
    "Secure — audit trail never stores message bodies or credentials; input",
    ("validated at the API boundary; no secrets hardcoded", 1),
    "Scalable — pessimistic-locked claim step makes horizontal worker scale-out safe;",
    ("swap-to-broker path documented and requires no business-logic changes", 1),
    "Safe change management — every scenario landed with full regression passing,",
    ("real bugs found by tests fixed and documented, not shipped silently", 1),
], size=12.5)
footer(s, "docs/architecture-overview.md, docs/testing-strategy.md")

# =========================================================================
# Slide 5 — Data model
# =========================================================================
s = add_slide()
header(s, "Architecture", "Data Model")
tables = [
    ("notifications", ["id (PK)", "source_system", "severity / priority", "overall_status", "idempotency_key",
                        "acknowledged_at/by", "escalated_at", "version (optimistic lock)"], BLUE),
    ("notification_recipients", ["id (PK)", "notification_id", "recipient_id", "recipient_type"], MUTED),
    ("requested_channels", ["id (PK)", "notification_id", "channel"], MUTED),
    ("delivery_attempts", ["id (PK)", "notification_id, recipient_id, channel (UNIQUE)", "status / attempt_count",
                             "last_failure_category", "next_retry_at", "version"], RED),
    ("routing_decisions", ["id (PK)", "notification_id", "requested/resolved channel", "reason"], MUTED),
    ("audit_events", ["id (PK)", "notification_id", "action", "detail (structured only)"], MUTED),
    ("idempotency_records", ["source_system, idempotency_key (UNIQUE)", "notification_id", "expires_at (7d)"], AMBER),
    ("recipient_preferences", ["recipient_id, channel (UNIQUE)", "enabled", "rank"], MUTED),
]
cols = 4
cw, ch = Inches(2.85), Inches(1.5)
gx, gy = Inches(0.3), Inches(0.25)
start_x, start_y = Inches(0.55), Inches(1.65)
for i, (name, fields, accent_color) in enumerate(tables):
    col = i % cols
    row = i // cols
    x = start_x + col * (cw + gx)
    y = start_y + row * (ch + gy)
    box_shape(s, x, y, cw, ch, fill=WHITE)
    hdr = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, y, cw, Inches(0.32))
    hdr.fill.solid(); hdr.fill.fore_color.rgb = accent_color; hdr.line.fill.background(); hdr.shadow.inherit = False
    htf = hdr.text_frame; htf.vertical_anchor = MSO_ANCHOR.MIDDLE
    hp = htf.paragraphs[0]; hp.text = name; hp.alignment = PP_ALIGN.CENTER
    for r in hp.runs: r.font.size = Pt(11); r.font.bold = True; r.font.color.rgb = WHITE
    b = s.shapes.add_textbox(x + Inches(0.1), y + Inches(0.38), cw - Inches(0.2), ch - Inches(0.45))
    tf = b.text_frame; tf.word_wrap = True
    for j, f in enumerate(fields):
        p = tf.paragraphs[0] if j == 0 else tf.add_paragraph()
        p.text = "• " + f
        p.space_after = Pt(1)
        for r in p.runs:
            r.font.size = Pt(9); r.font.color.rgb = TEXT
footer(s, "delivery_attempts unique(notification_id, recipient_id, channel) = delivery-level dedup boundary (section 4.4)")

# =========================================================================
# Slide 6 — Notification status state machine
# =========================================================================
s = add_slide()
header(s, "Architecture", "Notification Status Lifecycle")
states_row1 = ["RECEIVED", "VALIDATED", "ROUTED", "QUEUED", "IN_PROGRESS"]
states_row2 = [("DELIVERED", GREEN_BG, GREEN), ("PARTIALLY_DELIVERED", AMBER_BG, AMBER), ("FAILED", RED_BG, RED), ("ESCALATED", RED_BG, RED)]
side_states = [("REJECTED", RED_BG, RED), ("DUPLICATE_SUPPRESSED", AMBER_BG, AMBER), ("EXPIRED", AMBER_BG, AMBER)]

x = Inches(0.6)
y1 = Inches(1.9)
prev_x = None
for st in states_row1:
    w = Inches(2.1)
    shape_with_text(s, x, y1, w, Inches(0.7), st, fill=WHITE, title_size=12)
    if prev_x is not None:
        arrow(s, prev_x, y1 + Inches(0.35), x, y1 + Inches(0.35))
    prev_x = x + w
    x += w + Inches(0.15)

y2 = Inches(3.3)
x = Inches(1.9)
for st, fill, color in states_row2:
    w = Inches(2.4)
    shape_with_text(s, x, y2, w, Inches(0.7), st, fill=fill, title_color=color, title_size=12)
    x += w + Inches(0.3)

# Single arrow from IN_PROGRESS (last box of row 1) down to the outcomes row.
in_progress_center_x = Inches(0.6) + 4 * (2.1 + 0.15) * 914400 + Inches(1.05)
arrow(s, in_progress_center_x, y1 + Inches(0.7), in_progress_center_x, y2, color=MUTED)

textbox(s, Inches(0.6), Inches(4.35), Inches(6), Inches(0.3), "Terminal side-states (from RECEIVED/VALIDATED):", size=11.5, bold=True, color=NAVY)
x = Inches(0.6)
for st, fill, color in side_states:
    w = Inches(2.6)
    shape_with_text(s, x, Inches(4.7), w, Inches(0.6), st, fill=fill, title_color=color, title_size=11)
    x += w + Inches(0.2)

box_shape(s, Inches(0.6), Inches(5.6), Inches(12.1), Inches(1.05), fill=LIGHT_BG)
textbox(s, Inches(0.85), Inches(5.75), Inches(11.6), Inches(0.75),
        "NotificationStatusAggregator recomputes DELIVERED / PARTIALLY_DELIVERED / FAILED / IN_PROGRESS live from\n"
        "delivery_attempts after every outcome — and explicitly protects EXPIRED, REJECTED, and ESCALATED from being overwritten.",
        size=12, color=TEXT, line_spacing=1.3)
footer(s, "docs/architecture-overview.md §4")

# =========================================================================
# Slide 7 — Scenario 1: Greenfield
# =========================================================================
s = add_slide()
header(s, "Scenario 1 of 3", "Greenfield — Initial Capability")
box_shape(s, Inches(0.6), Inches(1.6), Inches(1.5), Inches(0.45), fill=BLUE)
badge_tf = s.shapes[-1].text_frame; badge_tf.vertical_anchor = MSO_ANCHOR.MIDDLE
bp = badge_tf.paragraphs[0]; bp.text = "BUILD"; bp.alignment = PP_ALIGN.CENTER
for r in bp.runs: r.font.size = Pt(11); r.font.bold = True; r.font.color.rgb = WHITE

bullets(s, Inches(0.6), Inches(2.25), Inches(6.1), Inches(4.3), [
    "Decomposed into 6 units, built bottom-up:",
    ("Domain model -> Submission+idempotency -> Routing -> Async queueing", 1),
    ("-> Delivery+retry -> Status+audit", 1),
    "Key decisions (documented, not assumed):",
    ("Routing: opt-out model; CRITICAL overrides preference & fans out", 1),
    ("Retry: 6 failure categories, only 3 retryable, exponential backoff", 1),
    ("Dedup: DB unique constraint backs the boundary, not app logic alone", 1),
    ("DB-polling worker over a broker — explicit trade-off for prototype scope", 1),
], size=13.5)

card = box_shape(s, Inches(7.0), Inches(2.25), Inches(5.7), Inches(4.3), fill=LIGHT_BG)
textbox(s, Inches(7.25), Inches(2.45), Inches(5.2), Inches(0.3), "Validation", size=13, bold=True, color=NAVY)
bullets(s, Inches(7.25), Inches(2.85), Inches(5.2), Inches(2.4), [
    "49 unit tests — routing, retry, idempotency, aggregation,",
    ("dispatcher, all provider simulation rules", 1),
    "7 end-to-end integration tests over real HTTP + H2 + the",
    ("actual scheduled worker (Awaitility-driven)", 1),
    "mvn verify: JaCoCo gate, 90% min on application/domain",
], size=12.5)
box_shape(s, Inches(7.25), Inches(5.4), Inches(5.2), Inches(1.05), fill=RED_BG)
textbox(s, Inches(7.45), Inches(5.5), Inches(4.8), Inches(0.85),
        "Real bug found & fixed: @Transactional silently no-ops on self-\n"
        "invocation (this.method()) — caught by the integration test, fixed\n"
        "by splitting trigger from transactional collaborator.",
        size=11, color=RED, line_spacing=1.2)
footer(s, "docs/scenarios/01-greenfield.md")

# =========================================================================
# Slide 8 — Scenario 2: Brownfield
# =========================================================================
s = add_slide()
header(s, "Scenario 2 of 3", "Brownfield — Multi-Layer Enhancement")
box_shape(s, Inches(0.6), Inches(1.6), Inches(1.9), Inches(0.45), fill=AMBER)
btf = s.shapes[-1].text_frame; btf.vertical_anchor = MSO_ANCHOR.MIDDLE
bp = btf.paragraphs[0]; bp.text = "ENHANCE"; bp.alignment = PP_ALIGN.CENTER
for r in bp.runs: r.font.size = Pt(11); r.font.bold = True; r.font.color.rgb = WHITE

textbox(s, Inches(0.6), Inches(2.2), Inches(12), Inches(0.5),
        "Three changes landed together because they're genuinely coupled in this codebase:", size=13, color=MUTED)

cols3 = [
    ("New channel", "Slack", ["Channel.SLACK + SlackChannelProvider", "V2 migration: seed data only —", "channel is VARCHAR everywhere,", "no schema change needed"]),
    ("Refactor", "Provider dispatch", ["if/else chain -> ChannelProviderRegistry", "(Strategy/Registry pattern)", "Adding Slack touched zero lines", "of the dispatch method itself"]),
    ("Harden dedup", "Retention enforcement", ["IdempotencyCleanupJob actually", "purges expired records now —", "policy was documented but never", "enforced in greenfield"]),
]
x = Inches(0.6)
for title, sub, items in cols3:
    w = Inches(3.95)
    box_shape(s, x, Inches(2.8), w, Inches(3.5), fill=WHITE)
    hdr = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, Inches(2.8), w, Inches(0.75))
    hdr.fill.solid(); hdr.fill.fore_color.rgb = NAVY; hdr.line.fill.background(); hdr.shadow.inherit = False
    htf = hdr.text_frame; htf.vertical_anchor = MSO_ANCHOR.MIDDLE
    hp = htf.paragraphs[0]; hp.text = title; hp.alignment = PP_ALIGN.CENTER
    for r in hp.runs: r.font.size=Pt(12); r.font.bold=True; r.font.color.rgb=WHITE
    hp2 = htf.add_paragraph(); hp2.text = sub; hp2.alignment = PP_ALIGN.CENTER
    for r in hp2.runs: r.font.size=Pt(10); r.font.color.rgb=RGBColor(0x9D,0xB2,0xF2)
    bullets(s, x + Inches(0.2), Inches(3.7), w - Inches(0.4), Inches(2.5), items, size=11.5)
    x += w + Inches(0.15)

box_shape(s, Inches(0.6), Inches(6.4), Inches(12.1), Inches(0.7), fill=GREEN_BG)
textbox(s, Inches(0.85), Inches(6.55), Inches(11.6), Inches(0.45),
        "Full regression: all greenfield tests pass unchanged — the refactor never touched RoutingService or NotificationSubmissionService.",
        size=11.5, color=GREEN)
footer(s, "docs/scenarios/02-brownfield.md")

# =========================================================================
# Slide 9 — Scenario 3: Ambiguous requirement
# =========================================================================
s = add_slide()
header(s, "Scenario 3 of 3", "Ambiguous Requirement — Escalation")
box_shape(s, Inches(0.6), Inches(1.55), Inches(2.1), Inches(0.45), fill=RED)
btf = s.shapes[-1].text_frame; btf.vertical_anchor = MSO_ANCHOR.MIDDLE
bp = btf.paragraphs[0]; bp.text = "DISAMBIGUATE"; bp.alignment = PP_ALIGN.CENTER
for r in bp.runs: r.font.size = Pt(10.5); r.font.bold = True; r.font.color.rgb = WHITE

box_shape(s, Inches(0.6), Inches(2.15), Inches(12.1), Inches(0.75), fill=LIGHT_BG)
textbox(s, Inches(0.85), Inches(2.3), Inches(11.6), Inches(0.5),
        "The ask, as given: “For critical alerts, if nobody acknowledges it in time, make sure it gets escalated so it doesn't get missed.”",
        size=13, color=NAVY, bold=True)

qa = [
    ("What does “acknowledged” mean?", "Explicit POST /acknowledge action — never inferred from delivery success"),
    ("How long is “in time”?", "Configurable threshold (15 min default), CRITICAL-only this iteration"),
    ("Escalate to whom / how?", "Reuses the existing delivery pipeline — same retry/audit guarantees"),
    ("Does it repeat?", "No — fires once, by design, to avoid alert fatigue (documented scope cut)"),
]
y = Inches(3.15)
for q, a in qa:
    box_shape(s, Inches(0.6), y, Inches(12.1), Inches(0.72), fill=WHITE)
    textbox(s, Inches(0.8), y + Inches(0.06), Inches(4.6), Inches(0.6), q, size=12, bold=True, color=BLUE)
    textbox(s, Inches(5.5), y + Inches(0.06), Inches(7.0), Inches(0.6), a, size=11.5, color=TEXT)
    y += Inches(0.82)

box_shape(s, Inches(0.6), Inches(6.45), Inches(12.1), Inches(0.75), fill=RED_BG)
textbox(s, Inches(0.85), Inches(6.55), Inches(11.6), Inches(0.6),
        "Real concurrency bug surfaced: optimistic-lock races on the Notification row (routing + escalation writing concurrently).\n"
        "Fixed with bounded retry + per-notification transaction isolation — documented as a finding, not hidden.",
        size=10.5, color=RED, line_spacing=1.15)
footer(s, "docs/scenarios/03-ambiguous-requirements.md")

# =========================================================================
# Slide 10 — Testing & Validation
# =========================================================================
s = add_slide()
header(s, "Quality", "Testing, Coverage & Validation")

stats = [("76", "Tests passing", GREEN), ("96.4%", "Overall line coverage", BLUE),
         ("90%", "JaCoCo gate (app/domain)", AMBER), ("2", "Real bugs found & fixed", RED)]
x = Inches(0.6)
for val, label, color in stats:
    w = Inches(2.95)
    box_shape(s, x, Inches(1.65), w, Inches(1.1), fill=WHITE)
    textbox(s, x, Inches(1.72), w, Inches(0.55), val, size=28, bold=True, color=color, align=PP_ALIGN.CENTER)
    textbox(s, x, Inches(2.25), w, Inches(0.4), label, size=10.5, color=MUTED, align=PP_ALIGN.CENTER)
    x += w + Inches(0.15)

box_shape(s, Inches(0.6), Inches(2.95), Inches(5.9), Inches(3.7), fill=LIGHT_BG)
textbox(s, Inches(0.85), Inches(3.1), Inches(5.4), Inches(0.3), "Approach", size=13, bold=True, color=NAVY)
bullets(s, Inches(0.85), Inches(3.5), Inches(5.4), Inches(3.0), [
    "Unit tests: no Spring context, Clock.fixed() for deterministic",
    ("timing — 65 tests", 1),
    "Integration tests: real HTTP + H2 + Flyway + the actual scheduled",
    ("workers, Awaitility-polled — 11 tests", 1),
    "Deterministic simulated providers (recipientId prefixes: flaky-,",
    ("timeout-, invalid-, ratelimit-, authfail-, reject-)", 1),
    "Combined coverage measured across both JVMs (Surefire + Failsafe",
    ("both feed the same jacoco.exec)", 1),
], size=12)

box_shape(s, Inches(6.65), Inches(2.95), Inches(6.05), Inches(3.7), fill=WHITE)
textbox(s, Inches(6.9), Inches(3.1), Inches(5.5), Inches(0.3), "Honest limitations (not hidden)", size=13, bold=True, color=NAVY)
bullets(s, Inches(6.9), Inches(3.5), Inches(5.5), Inches(3.0), [
    "Simulated providers, not real SES/Twilio/FCM/Slack integrations",
    "No load/performance testing of the DB-polling worker's ceiling",
    "No AuthN/AuthZ — explicitly out of scope for this prototype",
    "Concurrency fixes validated against the specific races tests",
    ("produced, not an exhaustive chaos/fuzz pass", 1),
    "api.exception package below 90% — one dead-code defensive",
    ("handler, explained rather than gamed for the metric", 1),
], size=12)
footer(s, "docs/testing-strategy.md")

# =========================================================================
# Slide 11 — UI
# =========================================================================
s = add_slide()
header(s, "Deliverable", "UI/UX — Separate Project")
textbox(s, Inches(0.6), Inches(1.6), Inches(11.5), Inches(0.5),
        "notification-management-ui — its own git repository, React 19 + TypeScript + Vite, talks to the backend over HTTP/CORS only.",
        size=13, color=MUTED)

pages = [
    ("Dashboard", "/", ["Recent notifications, newest first", "Auto-refresh every 4s", "Status + severity badges", "Stat tiles (delivered / in-progress / failed)"]),
    ("Submit", "/submit", ["Full request shape: recipients,", "channels, idempotency key", "Demo recipient-id prefix cheat-sheet", "for exercising retry/failure paths"]),
    ("Detail", "/notifications/:id", ["Live-polls every 2s until settled", "Per-recipient/per-channel delivery table", "Acknowledge action (escalation scenario)"]),
]
x = Inches(0.6)
for title, route, items in pages:
    w = Inches(4.0)
    box_shape(s, x, Inches(2.3), w, Inches(3.8), fill=WHITE)
    hdr = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, Inches(2.3), w, Inches(0.8))
    hdr.fill.solid(); hdr.fill.fore_color.rgb = BLUE; hdr.line.fill.background(); hdr.shadow.inherit = False
    htf = hdr.text_frame; htf.vertical_anchor = MSO_ANCHOR.MIDDLE
    hp = htf.paragraphs[0]; hp.text = title; hp.alignment = PP_ALIGN.CENTER
    for r in hp.runs: r.font.size=Pt(14); r.font.bold=True; r.font.color.rgb=WHITE
    hp2 = htf.add_paragraph(); hp2.text = route; hp2.alignment = PP_ALIGN.CENTER
    for r in hp2.runs: r.font.size=Pt(10); r.font.color.rgb=RGBColor(0xCD,0xD6,0xEE)
    bullets(s, x + Inches(0.2), Inches(3.25), w - Inches(0.4), Inches(2.7), items, size=11.5)
    x += w + Inches(0.15)

box_shape(s, Inches(0.6), Inches(6.3), Inches(12.1), Inches(0.85), fill=GREEN_BG)
textbox(s, Inches(0.85), Inches(6.45), Inches(11.6), Inches(0.55),
        "Verified end-to-end against a running backend: submit -> async retry -> DELIVERED, CRITICAL fan-out to all 4 channels,\n"
        "acknowledge, and the dashboard list — via the exact HTTP requests the UI issues, including a live CORS preflight check.",
        size=11, color=GREEN, line_spacing=1.2)
footer(s, "notification-management-ui/README.md")

# =========================================================================
# Slide 12 — Trade-offs & production path
# =========================================================================
s = add_slide()
header(s, "Engineering judgment", "Trade-offs & Production Path")
rows = [
    ("DB-polling worker", "No broker infra to run/explain; fully testable with H2", "Swap to Kafka/SQS consumer — dispatch logic already isolated, zero business-logic change"),
    ("Simulated channel providers", "CI-safe, deterministic, no external dependency", "Implement ChannelProvider for real SES/Twilio/FCM/Slack — one class each"),
    ("Single mutable Notification row", "Simple aggregate, one place to look up status", "Split volatile per-worker state into separate rows if contention grows at scale"),
    ("No AuthN/AuthZ in the prototype", "Keeps it standalone-runnable for review", "Front with the org's gateway / OAuth2 resource server"),
    ("Escalation fires once, no re-page", "Avoids alert fatigue by default", "On-call rotation / repeated paging integration is a scoped, flagged extension"),
]
y = Inches(1.7)
headers = ["Decision", "Why (prototype)", "Production path"]
xs = [Inches(0.6), Inches(3.7), Inches(8.2)]
ws = [Inches(3.0), Inches(4.4), Inches(4.5)]
for hx, hw, htext in zip(xs, ws, headers):
    textbox(s, hx, y, hw, Inches(0.3), htext.upper(), size=10.5, bold=True, color=BLUE)
y += Inches(0.4)
for decision, why, path in rows:
    rh = Inches(0.92)
    box_shape(s, Inches(0.6), y, Inches(12.1), rh, fill=LIGHT_BG if rows.index((decision,why,path)) % 2 == 0 else WHITE, line=BORDER)
    textbox(s, Inches(0.75), y + Inches(0.08), Inches(2.85), rh - Inches(0.1), decision, size=11, bold=True, color=NAVY)
    textbox(s, Inches(3.75), y + Inches(0.08), Inches(4.3), rh - Inches(0.1), why, size=10.5, color=TEXT, line_spacing=1.1)
    textbox(s, Inches(8.25), y + Inches(0.08), Inches(4.35), rh - Inches(0.1), path, size=10.5, color=MUTED, line_spacing=1.1)
    y += rh + Inches(0.06)
footer(s, "docs/architecture-overview.md §6, §11")

# =========================================================================
# Slide 13 — Close
# =========================================================================
s = add_slide()
fill_bg(s, NAVY)
textbox(s, Inches(0.9), Inches(2.2), Inches(11), Inches(0.4), "SUMMARY", size=13, bold=True, color=RGBColor(0x9D,0xB2,0xF2))
textbox(s, Inches(0.9), Inches(2.6), Inches(11.5), Inches(1.0), "Three scenarios. One coherent, tested system.", size=32, bold=True, color=WHITE)
bullets(s, Inches(0.9), Inches(3.8), Inches(10.5), Inches(2.4), [
    "Every functional requirement in section 4 implemented and tested, not just described",
    "Each design decision documented with its rationale — reviewable, not just asserted",
    "Two real bugs found by validation and fixed, with the finding kept in the docs",
    "Runnable end-to-end: backend, separate UI, full test suite, all in this repo pair",
], size=15, color=RGBColor(0xE4, 0xE9, 0xF7))

footer_line = box_shape(s, Inches(0.9), Inches(6.5), Inches(11.5), Pt(1.5), fill=RGBColor(0x2B,0x3D,0x73), line=RGBColor(0x2B,0x3D,0x73))
textbox(s, Inches(0.9), Inches(6.65), Inches(8), Inches(0.4),
        "EXECUTION_GUIDE.md  ·  docs/architecture-overview.md  ·  docs/scenarios/  ·  docs/testing-strategy.md",
        size=11, color=RGBColor(0x9D,0xB2,0xF2))

# ---- save ----------------------------------------------------------------
out_dir = os.path.join(os.path.dirname(__file__), "..", "docs")
out_path = os.path.join(out_dir, "Notification-Management-Service-Overview.pptx")
prs.save(out_path)
print("Saved:", os.path.abspath(out_path))
