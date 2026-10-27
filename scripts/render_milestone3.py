#!/usr/bin/env python3
"""Optional report/diagram exporter. Requires reportlab==5.1.0 (includes Pillow)."""
from pathlib import Path
from html import escape
import re
import textwrap
from PIL import Image, ImageDraw, ImageFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import letter, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Preformatted, Image as PDFImage, KeepTogether
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'


def font(size, bold=False):
    candidates = [
        '/System/Library/Fonts/Supplemental/Arial Bold.ttf' if bold else '/System/Library/Fonts/Supplemental/Arial.ttf',
        '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
    ]
    for name in candidates:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)


def diagram():
    im = Image.new('RGB', (2400, 1500), '#f7f9fc')
    d = ImageDraw.Draw(im)
    ink = '#172a46'
    d.text((75, 50), 'Nail Salon Appointment Scheduler', fill=ink, font=font(60, True))
    d.text((78, 130), 'CMPE 172 / Milestone 3 / HTTP distribution and commit-then-notify', fill='#52617a', font=font(31))

    def box(rect, title, lines, fill='#ffffff', border='#ced8e5'):
        x1, y1, x2, y2 = rect
        d.rounded_rectangle(rect, radius=24, fill=fill, outline=border, width=3)
        y = y1 + 24
        for i, line in enumerate([title] + lines):
            d.text((x1 + 25, y), line, fill=ink, font=font(30 if i == 0 else 25, i == 0))
            y += 44 if i == 0 else 36

    def arrow(points, label=None, at=None, color='#47688f'):
        d.line(points, fill=color, width=5, joint='curve')
        x, y = points[-1]
        px, py = points[-2]
        if abs(x - px) >= abs(y - py):
            s = 1 if x > px else -1
            tri = [(x, y), (x - 16 * s, y - 10), (x - 16 * s, y + 10)]
        else:
            s = 1 if y > py else -1
            tri = [(x, y), (x - 10, y - 16 * s), (x + 10, y - 16 * s)]
        d.polygon(tri, fill=color)
        if label:
            d.text(at, label, fill=color, font=font(23))

    d.rounded_rectangle((430, 255, 1410, 1045), radius=30, outline='#8aaed1', width=4, fill='#edf4fb')
    d.text((460, 278), 'APPOINTMENT SCHEDULER', fill='#315b83', font=font(26, True))
    box((65, 345, 355, 560), 'Client', ['REST API', 'Thymeleaf website'])
    box((490, 345, 1330, 465), 'Shared AppointmentService', ['Both controllers call book / cancel'])
    box((490, 520, 1330, 695), 'Database transaction', ['Lock + validate + insert / update', 'Publish immutable event snapshot'])
    box((490, 770, 1330, 1000), 'After-commit listener + Gateway', ['AFTER_COMMIT: skip rollback events', 'NOT_SUPPORTED: suspend DB resources', 'Send a complete NotificationRequest'])
    box((1670, 345, 2335, 585), 'Mock Notification Service', ['POST /notifications + DTO validation', '200 RECEIVED or DUPLICATE', '409 if ID reused with changed payload'])
    box((1670, 720, 2335, 930), 'Durable receiver inbox', ['PostgreSQL unique notification ID', 'Payload hash only; no recipient text', 'Concurrent retries deduplicated'])
    box((75, 770, 355, 1000), 'PostgreSQL', ['Appointments', 'Availability slots', 'Row locks +', 'unique index'])
    box((490, 1150, 1330, 1380), 'Timeout / retry / fallback', ['Connect: 500 ms / response: 1000 ms', '3 attempts / 100 ms fixed retry delay', 'WARN fallback: appointment stays committed'])
    box((1670, 1100, 2335, 1390), 'Delivery scope', ['Default: mock in same app process', 'Configurable URL for remote instance', 'No real email or SMS', 'No outbox: crashes can lose delivery'])
    arrow([(355, 410), (490, 410)])
    arrow([(900, 465), (900, 520)])
    arrow([(490, 615), (385, 615), (385, 860), (355, 860)])
    arrow([(900, 695), (900, 770)], 'Commit first', (940, 715))
    arrow([(1330, 865), (1515, 865), (1515, 455), (1670, 455)], 'HTTP POST', (1450, 400))
    d.text((1445, 905), 'Stable ID +', fill='#47688f', font=font(23))
    d.text((1445, 940), 'same payload', fill='#47688f', font=font(23))
    arrow([(2000, 585), (2000, 720)])
    arrow([(900, 1000), (900, 1150)])
    d.text((80, 1440), 'Best effort sender; durable receiver deduplication. Database history remains authoritative.', fill='#52617a', font=font(25))
    output = DOCS / 'Milestone3_Distribution.png'
    im.save(output)
    pdf = canvas.Canvas(str(DOCS / 'Milestone3_Distribution.pdf'), pagesize=landscape(letter))
    pdf.setTitle('Milestone 3 Distribution Diagram')
    pdf.drawImage(ImageReader(im), 0, 0, width=792, height=495)
    pdf.showPage()
    pdf.save()


def inline(value):
    value = escape(value.strip())
    value = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', value)
    value = re.sub(r'`(.*?)`', r'<font name="Courier" size="8.5">\1</font>', value)
    return value


def report():
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name='ReportBody', fontName='Helvetica', fontSize=10, leading=14.5, spaceAfter=8))
    styles.add(ParagraphStyle(name='ReportCode', fontName='Courier', fontSize=7.3, leading=10, leftIndent=10,
                              rightIndent=10, backColor=colors.HexColor('#f0f4f8'), spaceBefore=6, spaceAfter=12))
    styles['Title'].textColor = colors.HexColor('#17345b')
    styles['Heading2'].textColor = colors.HexColor('#234f7e')
    styles['Heading2'].spaceBefore = 14
    styles['Heading2'].keepWithNext = True
    body = []
    buffer = []
    code = []
    in_code = False

    def flush():
        if buffer:
            body.append(Paragraph(inline(' '.join(buffer)), styles['ReportBody']))
            buffer.clear()

    for line in (DOCS / 'CMPE172_Milestone3_Report.md').read_text().splitlines():
        if line.startswith('```'):
            if in_code:
                wrapped = []
                for item in code:
                    wrapped.extend(textwrap.wrap(item, width=100, replace_whitespace=False, drop_whitespace=False) or [''])
                body.append(Preformatted('\n'.join(escape(item) for item in wrapped), styles['ReportCode']))
                code.clear()
            else:
                flush()
            in_code = not in_code
        elif in_code:
            code.append(line)
        elif line.startswith('# '):
            flush()
            body.append(Paragraph(inline(line[2:]), styles['Title']))
        elif line.startswith('## '):
            flush()
            body.append(Paragraph(inline(line[3:]), styles['Heading2']))
        elif not line.strip():
            flush()
        elif line.startswith('- '):
            flush()
            body.append(Paragraph('• ' + inline(line[2:]), styles['ReportBody']))
        else:
            buffer.append(line)
            if line.endswith('  '):
                flush()
    flush()
    body.append(Paragraph('Distribution diagram', styles['Heading2']))
    body.append(PDFImage(str(DOCS / 'Milestone3_Distribution.png'), width=480, height=300))

    def footer(c, doc):
        c.saveState()
        c.setStrokeColor(colors.HexColor('#ced8e5'))
        c.line(54, 44, 558, 44)
        c.setFont('Helvetica', 8)
        c.setFillColor(colors.HexColor('#52617a'))
        c.drawString(54, 30, 'CMPE 172 / Milestone 3 / FirstName LastName')
        c.drawRightString(558, 30, str(doc.page))
        c.restoreState()
    doc = SimpleDocTemplate(str(DOCS / 'CMPE172_Milestone3_Report.pdf'), pagesize=letter,
                            rightMargin=54, leftMargin=54, topMargin=48, bottomMargin=58,
                            title='CMPE 172 Milestone 3: Integration, Distribution & Deployment',
                            author='FirstName LastName')
    doc.build(body, onFirstPage=footer, onLaterPages=footer)


if __name__ == '__main__':
    diagram()
    report()
    print('Rendered report PDF and distribution PNG/PDF in docs/.')
