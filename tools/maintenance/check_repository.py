#!/usr/bin/env python3
"""Read-only, offline repository hygiene checks; not a Markdown parser or security audit."""
import hashlib
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
IMMUTABLE = {
    'docs/MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md':
        '974ba8c6a8d287f41f46a32de8fdd57b1aaf409a99ddf877861d78751fbb56f4',
    'tools/music/netease_portrait_reference.js':
        '4ee8ce6f44c9fd26f009babfe996746c86c440e384890b8b808c270a9e13e0ec',
}
GENERATED = {'.gradle', '.idea', '__pycache__', 'node_modules'}
FORBIDDEN_SUFFIXES = {
    '.apk', '.aab', '.apks', '.idsig', '.jks', '.keystore', '.pem', '.db',
    '.sqlite', '.sqlite3', '.log', '.pyc', '.hprof', '.heapdump', '.jfr',
}


def markdown_lines(text):
    """Exclude fenced examples so illustrative links/headings are not treated as real."""
    fence = None
    for line in text.splitlines():
        match = re.match(r'^\s{0,3}(`{3,}|~{3,})(.*)$', line)
        if fence:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                fence = None
            continue
        if match:
            fence = match[1]
            continue
        yield line


def anchors(text):
    result, counts = set(), {}
    for line in markdown_lines(text):
        match = re.match(r'^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$', line)
        if not match:
            continue
        title = re.sub(r'\[([^]]+)\]\([^)]*\)', r'\1', match[1])
        slug = re.sub(r'[^\w\-\s]', '', title.lower()).replace(' ', '-')
        number = counts.get(slug, 0)
        counts[slug] = number + 1
        result.add(slug if number == 0 else f'{slug}-{number}')
    return result


def check_links(root, paths):
    problems = []
    for name in paths:
        if not name.endswith('.md'):
            continue
        document = root / name
        for line in markdown_lines(document.read_text(encoding='utf-8')):
            # Code spans may show illustrative commands or Markdown syntax.
            line = re.sub(r'(`+).*?\1', '', line)
            for match in re.finditer(r'!?\[[^]\n]*\]\(\s*(<[^>\n]+>|[^)\n]+)\)', line):
                value = match[1].strip()
                value = value[1:value.index('>')] if value.startswith('<') else value.split()[0]
                url = urlsplit(value)
                if url.scheme or url.netloc:
                    continue
                target = (document.parent / unquote(url.path)).resolve() if url.path else document.resolve()
                if not target.is_relative_to(root.resolve()):
                    problems.append(f'{name}: link outside repository: {value}')
                elif not target.exists():
                    problems.append(f'{name}: missing link: {value}')
                elif url.fragment and target.is_file() and target.suffix == '.md':
                    if unquote(url.fragment) not in anchors(target.read_text(encoding='utf-8')):
                        problems.append(f'{name}: missing heading: {value}')
    return problems


def check_immutable(root, expected=IMMUTABLE):
    problems = []
    for name, digest in expected.items():
        path = root / name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            problems.append(f'{name}: immutable reference missing or changed')
    return problems


def check_paths(root, paths):
    problems = []
    for name in paths:
        path = Path(name)
        if (root / path).is_symlink() or not (root / path).resolve().is_relative_to(root.resolve()):
            problems.append(f'{name}: symlink/outside source requires manual review')
        generated = any(p in GENERATED for p in path.parts) or (
            'build' in path.parts and path.parts[:2] != ('tools', 'build'))
        if generated or path.suffix.lower() in FORBIDDEN_SUFFIXES:
            problems.append(f'{name}: generated/private artifact in source inventory')
        if path.name.endswith(('.db-wal', '.db-shm', '.sqlite-wal', '.sqlite-shm')):
            problems.append(f'{name}: database sidecar in source inventory')
    return problems


def check_sources(root):
    required = [
        'app/src/main/AndroidManifest.xml', 'app/src/full/AndroidManifest.xml',
        'app/src/minimal/AndroidManifest.xml',
        'app/src/full/assets/rebrowser_netease_layout.js',
        'app/src/full/assets/rebrowser_netease_provider.js',
        'gradle/wrapper/gradle-wrapper.jar',
    ]
    forbidden = ['app/src/main/java/io/github/reoutlook/ReBrowserLayoutZoom.java']
    forbidden += ['app/src/main/assets/' + name for name in [
        'rebrowser_netease_layout.js', 'rebrowser_netease_provider.js']]
    forbidden += ['app/src/main/java/io/github/reoutlook/ReBrowserMusic' + name + '.java'
                  for name in ['Activity', 'Runtime', 'Artwork', 'Service', 'ControlReceiver']]
    return ([f'{name}: required source missing' for name in required if not (root / name).is_file()]
            + [f'{name}: obsolete/wrong-source-set implementation present' for name in forbidden
               if (root / name).exists()])


def check_release(root):
    build = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
    match = re.search(r'versionName\s*=\s*"([^"]+)"', build)
    if not match:
        return ['app/build.gradle.kts: versionName missing']
    version = match[1]
    # Development versions need not already have a published-release record.
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        return []
    problems = []
    # Owner-authored README is frozen independently of release maintenance.
    # Keep the same version/link consistency check in the maintained release index.
    index = root / 'docs/releases/README.md'
    if not index.is_file():
        problems.append('docs/releases/README.md: release index missing')
    else:
        current = next((line for line in index.read_text(encoding='utf-8').splitlines()
                        if line.startswith('**当前稳定版：')), '')
        if f'[v{version}](v{version}.md)' not in current:
            problems.append('docs/releases/README.md: current stable link differs from build version')
    record = root / f'docs/releases/v{version}.md'
    if not record.is_file():
        problems.append(f'{record.relative_to(root)}: release record missing')
    return problems


def main():
    inventory = subprocess.check_output(
        ['git', '-C', str(ROOT), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'])
    paths = sorted(set(name.decode('utf-8') for name in inventory.split(b'\0') if name))
    problems = check_paths(ROOT, paths)
    # Refuse to follow a source symlink while inspecting Markdown or policy files.
    if not any('symlink' in problem for problem in problems):
        problems += check_links(ROOT, paths) + check_immutable(ROOT) + check_sources(ROOT) + check_release(ROOT)
    if problems:
        print('\n'.join(problems), file=sys.stderr)
        return 1
    print(f'Repository checks passed: {len(paths)} source files; local Markdown links/anchors, '
          'immutable references, source-set layout and current release link. No network/device access.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
