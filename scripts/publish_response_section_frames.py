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


def publish(directory: Path, platform: str) -> None:
    if platform not in ('ios', 'android'):
        raise ValueError('Expected actual native platform ios or android')
    for section in SECTIONS:
        name = f'genui-reference-{platform}-{section}-393x852.png'
        image_path = directory / name
        with Image.open(image_path) as original:
            if original.width * 852 != original.height * 393:
                raise ValueError(f'Unexpected actual frame dimensions for {name}: {original.size}')
            image = original.convert('RGB').resize((393, 852), Image.Resampling.LANCZOS)
        data = b''
        for quality in (80, 65, 50, 35, 20):
            output = io.BytesIO()
            image.save(output, 'JPEG', quality=quality, optimize=True)
            data = output.getvalue()
            if len(data) <= 28000:
                break
        if len(data) > 28000:
            raise ValueError(f'Preview exceeds the annotation bound: {name}')
        digest = hashlib.sha256(data).hexdigest()
        encoded = base64.b64encode(data).decode('ascii')
        assert re.fullmatch(r'[A-Za-z0-9+/=]+', encoded)
        print(f'::notice title=Native section {platform} {section}::GS_RENDER_V1 {name} {digest} {encoded}', flush=True)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: publish_response_section_frames.py DIRECTORY ios|android')
    publish(Path(sys.argv[1]), sys.argv[2])
