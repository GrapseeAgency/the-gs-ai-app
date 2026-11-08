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
import re
import sys
from pathlib import Path
from PIL import Image

SECTIONS = ['01-dashboard', '02-structured-launch', '03-react-code', '04-citations', '05-data-grid', '06-interactive-charts']


def publish(directory: Path, platform: str, section: str | None = None) -> None:
    if platform not in ('ios', 'android'):
        raise ValueError('Expected actual native platform ios or android')
    if section is not None and section not in SECTIONS:
        raise ValueError('Unknown supplied response section')
    for section in ([section] if section is not None else SECTIONS):
        name = f'genui-reference-{platform}-{section}-393x852.png'
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
            if len(data) <= 22000:
                break
        if len(data) > 22000:
            raise ValueError(f'Preview exceeds the annotation bound: {name}')
        digest = hashlib.sha256(data).hexdigest()
        encoded = base64.b64encode(data).decode('ascii')
        assert re.fullmatch(r'[A-Za-z0-9+/=]+', encoded)
        # Workflow commands truncate messages to 4096 characters and retain
        # at most ten notices per step. One section gets its own step and at
        # most ten bounded chunks, whose whole-image digest detects loss.
        chunks = [encoded[i:i + 3750] for i in range(0, len(encoded), 3750)]
        if len(chunks) > 10:
            raise ValueError('Too many annotation chunks')
        for index, chunk in enumerate(chunks, 1):
            print(f'::notice title=Native section {platform} {section} {index}/{len(chunks)}::GS_RENDER_V1 {name} {digest} {index}/{len(chunks)} {original_size} {chunk}', flush=True)


if __name__ == '__main__':
    if len(sys.argv) not in (3, 4):
        raise SystemExit('Usage: publish_response_section_frames.py DIRECTORY ios|android [SECTION]')
    try:
        publish(Path(sys.argv[1]), sys.argv[2], sys.argv[3] if len(sys.argv) == 4 else None)
    except Exception as error:
        message = str(error).replace('%', '%25').replace('\n', '%0A').replace('\r', '%0D')
        print(f'::error::Actual native preview failed: {message}', flush=True)
        raise
