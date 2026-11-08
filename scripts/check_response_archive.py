#!/usr/bin/env python3
"""Read an actual exported XCTest ZIP with Python's independent ZIP implementation.

This proves the generated archive's bytes, not that a user saved it through Files.
"""
import io
import json
import sys
import zipfile
import zlib
from pathlib import Path

SOURCE = 'বাংলা 日本語\r\n😀\x00'.encode('utf-8')


def verify(payload):
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        assert sorted(archive.namelist()) == ['README.txt', 'snippet-1.py', 'snippet-2.txt'], 'Wrong or extra archive entries'
        assert archive.testzip() is None, 'ZIP CRC validation failed'
        assert archive.read('snippet-1.py') == SOURCE, 'Unicode/CRLF/NUL source bytes changed'
        assert archive.read('snippet-2.txt') == b'123456789', 'Known CRC test vector changed'
        assert archive.getinfo('snippet-2.txt').CRC == 0xcbf43926, 'Known CRC-32 disagrees'
        assert b'not a complete runnable project' in archive.read('README.txt'), 'Missing honest project disclosure'
        for info in archive.infolist():
            assert info.compress_type == zipfile.ZIP_STORED, 'Unexpected compression format'
            assert info.CRC == zlib.crc32(archive.read(info)), 'Central-directory CRC disagrees'
            print(f'PASS ZIP entry {info.filename}: {info.file_size} bytes, CRC32={info.CRC:08x}')


def check(root):
    root = Path(root).resolve()
    found = []
    for manifest in root.rglob('manifest.json'):
        data = json.loads(manifest.read_text())
        for group in data if isinstance(data, list) else [data]:
            for attachment in group.get('attachments', []):
                if not attachment.get('suggestedHumanReadableName', '').startswith('response-source-archive'):
                    continue
                path = (manifest.parent / attachment['exportedFileName']).resolve()
                assert path.is_relative_to(root), 'Attachment escapes export directory'
                found.append(path)
    assert found, 'Native response-source-archive.zip attachment missing; byte proof did not run'
    for path in found:
        verify(path.read_bytes())
    print(f'PASS {len(found)} actual XCTest source archive(s) independently opened and CRC-verified')


def self_test():
    def fixture(source=SOURCE, extra=False):
        target = io.BytesIO()
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('README.txt', 'not a complete runnable project')
            archive.writestr('snippet-1.py', source)
            archive.writestr('snippet-2.txt', b'123456789')
            if extra:
                archive.writestr('invented.py', 'fake project')
        return target.getvalue()
    verify(fixture())
    for payload in (fixture(b'changed'), fixture(extra=True), b'not a ZIP'):
        try:
            verify(payload)
        except (AssertionError, zipfile.BadZipFile):
            pass
        else:
            raise AssertionError('Invalid archive unexpectedly accepted')
    import tempfile
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        try:
            check(root)
        except AssertionError:
            pass
        else:
            raise AssertionError('Missing native attachment unexpectedly accepted')
        (root / 'native.zip').write_bytes(fixture())
        (root / 'manifest.json').write_text(json.dumps([{'attachments': [{
            'suggestedHumanReadableName': 'response-source-archive.zip_0_test.zip',
            'exportedFileName': 'native.zip'}]}]))
        check(root)
        (root / 'manifest.json').write_text(json.dumps([{'attachments': [{
            'suggestedHumanReadableName': 'response-source-archive_0_12345678-1234-1234-1234-123456789abc.zip',
            'exportedFileName': 'native.zip'}]}]))
        check(root)
    print('PASS archive gate self-tests: valid, altered bytes, extra files, invalid ZIP, missing attachment, native manifest')


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == '--self-test':
        self_test()
    else:
        check(sys.argv[1])
