#!/usr/bin/env python3
"""Publish bounded, reviewable native-frame previews through check annotations.

Original, lossless PNGs remain the artifacts of record. These JPEG previews
make the actual native composition inspectable when artifact blob hosts cannot
be reached. Neither thumbnails nor successful capture establish visual parity.
Only the six non-user-data response-section fixtures are eligible.
"""
import base64
import hashlib
import io
import json
import math
import re
import sys
from pathlib import Path
from PIL import Image

SECTIONS = ['01-dashboard', '02-structured-launch', '03-react-code', '04-citations', '05-data-grid', '06-interactive-charts']


def scroll_manifest(directory: Path, platform: str, section: str) -> dict:
    if platform not in ('ios', 'android') or section not in SECTIONS:
        raise ValueError('Unknown actual native response fixture')
    stem = f'genui-reference-{platform}-{section}-393x852'
    manifest = json.loads((directory / (stem + '-scroll.json')).read_text())
    frames = manifest['frames']
    viewport = float(manifest['viewportHeight'])
    maximum = float(manifest['maximumOffsetY'])
    if not math.isfinite(viewport) or viewport <= 0 or not math.isfinite(maximum) or maximum < 0:
        raise ValueError('Invalid actual scroll geometry')
    if not 1 <= len(frames) <= 20:
        raise ValueError('Actual scroll evidence exceeds the renderer capture cap')
    offsets = [float(frame['offsetY']) for frame in frames]
    if any(not math.isfinite(value) for value in offsets) or abs(offsets[0]) > 1 or abs(offsets[-1] - maximum) > 1:
        raise ValueError('Full-scroll evidence must cover both the actual top and bottom')
    if any(not 0 < end - start <= viewport * 0.76 + 1 for start, end in zip(offsets, offsets[1:])):
        raise ValueError('Actual scroll viewports must advance with overlap, never omit content')
    for index, frame in enumerate(frames):
        if frame['file'] != f'{stem}-scroll-{index}.png' or not (directory / frame['file']).is_file():
            raise ValueError('Missing or misidentified actual scroll viewport')
    return manifest


def publish(directory: Path, platform: str, section: str | None = None, scroll_index: int | None = None) -> None:
    if platform not in ('ios', 'android'):
        raise ValueError('Expected actual native platform ios or android')
    if section is not None and section not in SECTIONS:
        raise ValueError('Unknown supplied response section')
    for section in ([section] if section is not None else SECTIONS):
        stem = f'genui-reference-{platform}-{section}-393x852'
        name = stem + '.png'
        if scroll_index is not None:
            if scroll_index < 0 or scroll_index > 19:
                raise ValueError('Scroll review index must be 0 through 19')
            manifest = scroll_manifest(directory, platform, section)
            frames = manifest['frames']
            maximum = float(manifest['maximumOffsetY'])
            if scroll_index >= len(frames):
                print(f'Full-scroll coverage: {platform} {section} has {len(frames)} genuine viewports through offset {maximum}; no further viewport exists')
                continue
            name = frames[scroll_index]['file']
        image_path = directory / name
        with Image.open(image_path) as original:
            if original.width < 100 or original.height <= original.width:
                raise ValueError(f'Unexpected actual phone frame dimensions for {name}: {original.size}')
            original_size = f'{original.width}x{original.height}'
            # Robolectric's captured app root can exclude system-bar insets.
            # Preserve its actual aspect: never stretch a captured viewport to
            # pretend it was the qualifier's complete physical display.
            height = round(original.height * 393 / original.width)
            image = original.convert('RGB').resize((393, height), Image.Resampling.LANCZOS)
        data = b''
        for quality in (80, 65, 50, 35, 20):
            output = io.BytesIO()
            image.save(output, 'JPEG', quality=quality, optimize=True)
            data = output.getvalue()
            if len(data) <= 23500:
                break
        if len(data) > 23500:
            raise ValueError(f'Preview exceeds the annotation bound: {name}')
        digest = hashlib.sha256(data).hexdigest()
        encoded = base64.b64encode(data).decode('ascii')
        assert re.fullmatch(r'[A-Za-z0-9+/=]+', encoded)
        # Workflow commands truncate messages to 4096 characters and retain
        # at most ten notices per step. One section gets its own step and at
        # most ten bounded chunks, whose whole-image digest detects loss.
        chunks = [encoded[i:i + 3920] for i in range(0, len(encoded), 3920)]
        if len(chunks) > 10:
            raise ValueError('Too many annotation chunks')
        for index, chunk in enumerate(chunks, 1):
            print(f'::notice title=Native section {platform} {section} {index}/{len(chunks)}::GS_RENDER_V1 {name} {digest} {index}/{len(chunks)} {original_size} {chunk}', flush=True)


if __name__ == '__main__':
    if len(sys.argv) not in (3, 4, 5):
        raise SystemExit('Usage: publish_response_section_frames.py DIRECTORY ios|android [SECTION [SCROLL_INDEX]]')
    try:
        publish(Path(sys.argv[1]), sys.argv[2], sys.argv[3] if len(sys.argv) >= 4 else None, int(sys.argv[4]) if len(sys.argv) == 5 else None)
    except Exception as error:
        message = str(error).replace('%', '%25').replace('\n', '%0A').replace('\r', '%0D')
        print(f'::error::Actual native preview failed: {message}', flush=True)
        raise
