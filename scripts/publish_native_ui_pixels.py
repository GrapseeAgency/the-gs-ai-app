#!/usr/bin/env python3
"""Review actual fixture-only XCTest screenshots when blob downloads are blocked."""
import base64
import hashlib
import io
import re
import sys
from pathlib import Path
from PIL import Image

log, label = sys.argv[1:]
if label not in ('FILES', 'EXPORT', 'REACT'):
    raise SystemExit('Only non-user-data native acceptance fixture screenshots are eligible')
chunks = {}
count = 0
for line in Path(log).read_text(errors='replace').splitlines():
    match = re.search(r'GS_UI_PNG_V1 ' + label + r' (\d+)/(\d+) ([A-Za-z0-9+/=]+)', line)
    if match:
        index, total, chunk = match.groups()
        count = int(total)
        chunks[int(index)] = chunk
if not chunks:
    print('No actual ' + label + ' screenshot was emitted; no substitute image is created')
    raise SystemExit(0)
if not 1 <= count <= 2000 or sorted(chunks) != list(range(1, count + 1)):
    raise SystemExit('Incomplete actual native screenshot chunks')
original = base64.b64decode(''.join(chunks[i] for i in range(1, count + 1)), validate=True)
with Image.open(io.BytesIO(original)) as source:
    original_size = f'{source.width}x{source.height}'
    image = source.convert('RGB').resize((393, round(source.height * 393 / source.width)), Image.Resampling.LANCZOS)
for quality in (80, 65, 50, 35, 20):
    output = io.BytesIO()
    image.save(output, 'JPEG', quality=quality, optimize=True)
    data = output.getvalue()
    if len(data) <= 23500:
        break
if len(data) > 23500:
    raise SystemExit('Actual native UI preview exceeds the annotation bound')
digest = hashlib.sha256(data).hexdigest()
encoded = base64.b64encode(data).decode('ascii')
parts = [encoded[i:i+3920] for i in range(0, len(encoded), 3920)]
for index, part in enumerate(parts, 1):
    print(f'::notice::GS_UI_RENDER_V1 {label} {digest} {index}/{len(parts)} {original_size} {part}')
