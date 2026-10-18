#!/usr/bin/env python3
"""Read-only legacy archive gate. Inventory is not semantic approval; never moves files."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import zipfile

P = Path(__file__).resolve().parent
ROOT = P.parents[1]


def review():
    ledger = json.loads((P / 'records/legacy-archive-review.json').read_text())
    entries = ledger['entries']
    paths = [e['path'] for e in entries]
    assert len(paths) == len(set(paths))
    archive = ROOT / ledger['archiveTarget']
    assert archive.name == 'old_version.zip' and archive.parent == ROOT
    moved = [e for e in entries if e['state'] == 'archived']
    assert ledger['filesMoved'] == len(moved)
    assert ledger['archiveCreated'] == archive.exists()
    for e in entries:
        path = PurePosixPath(e['path'])
        assert not path.is_absolute() and '..' not in path.parts
        assert str(path) == e['path'] and '\\' not in e['path']
        if path.parts[:2] == ('app', 'src'):
            assert path.parts[2] in ['main', 'full', 'minimal', 'background', 'lite']
        else:
            assert e['scope'] == 'legacy-application-runtime-input-outside-app-src'
            assert e['path'] in [
                'tools/music/netease_portrait_reference.js',
                'tools/music/netease_expanded_portrait.css',
                'tools/music/netease_home_card.css',
            ]
        assert path.suffix in ['.java', '.js', '.css', '.html', '.sql']
        assert not (ROOT / path).is_symlink()
        if e['state'] == 'archived':
            assert e['archiveApproved'] and e['refactorValueExhausted']
            assert e['evidenceRefs'] and e['reason'] and e['reviewedAt']
            assert not (ROOT / path).exists()
        else:
            assert not e['archiveApproved'] and not e['refactorValueExhausted']
            data = (ROOT / path).read_bytes()
            assert len(data) == e['bytes']
            assert hashlib.sha256(data).hexdigest() == e['sha256']
    if archive.exists():
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            assert len(z.namelist()) == len(set(z.namelist()))
            assert set(z.namelist()) == {e['path'] for e in moved}
            for e in moved:
                assert hashlib.sha256(z.read(e['path'])).hexdigest() == e['sha256']
    else:
        assert not moved
    return {
        'legacyRuntimeFilesInventoried': len(entries),
        'legacyFilesSemanticallyReviewedRetain': sum(e['state'] == 'reviewed-retain-value-remains' for e in entries),
        'legacyFilesQueuedNotSemanticallyReviewed': sum(e['state'] == 'queued-not-semantically-reviewed' for e in entries),
        'generatedRuntimeArtifactsRetained': sum(e['state'] == 'generated-runtime-retain-not-archive-candidate' for e in entries),
        'legacyFilesArchived': len(moved),
        'temporaryOrAuxiliaryFilesArchived': False,
    }


if __name__ == '__main__':
    print(json.dumps(review(), indent=2))
