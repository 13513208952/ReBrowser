#!/usr/bin/env python3
"""Dry-run by default. Only remove fixed, project-local, untracked Gradle outputs."""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def validate_targets(root, targets, tracked):
    root = root.resolve()
    allowed = {'app/build', 'build', '.gradle'}
    for name in targets:
        if name not in allowed:
            raise ValueError(f'Not an allowed generated directory: {name}')
        target = root / name
        if target.is_symlink() or not target.resolve().is_relative_to(root):
            raise ValueError(f'Symlink or outside-repository target: {name}')
        for parent in target.parents:
            if parent == root:
                break
            if parent.is_symlink():
                raise ValueError(f'Symlink parent: {name}')
        if any(path == name or path.startswith(name + '/') for path in tracked):
            raise ValueError(f'Target contains tracked source: {name}')
        if target.exists() and not target.is_dir():
            raise ValueError(f'Expected generated directory, got file: {name}')


def gradle_processes():
    """Conservative Linux preflight, not a substitute for stopping IDE build jobs."""
    found = []
    proc = Path('/proc')
    if not proc.is_dir():
        raise ValueError('Cannot inspect /proc; manually verify idle builds before cleaning elsewhere')
    for item in proc.iterdir():
        if not item.name.isdigit():
            continue
        try:
            args = (item / 'cmdline').read_bytes().split(b'\0')
        except (OSError, PermissionError):
            continue
        executable = Path(args[0].decode(errors='replace')).name if args[0] else ''
        java_gradle = executable == 'java' and any(
            b'org.gradle.' in arg or b'GradleWrapperMain' in arg for arg in args[1:])
        wrapper = any(Path(arg.decode(errors='replace')).name == 'gradlew' for arg in args[:2] if arg)
        if java_gradle or wrapper:
            found.append(item.name)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Actually remove validated directories')
    parser.add_argument('--include-local-gradle-cache', action='store_true', help='Also remove project .gradle; never user-wide caches')
    args = parser.parse_args()
    targets = ['app/build', 'build'] + (['.gradle'] if args.include_local_gradle_cache else [])
    try:
        top = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', '--show-toplevel'], text=True).strip()
        if Path(top).resolve() != ROOT.resolve():
            raise ValueError('Tool directory is not the Git worktree root')
        tracked = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
        validate_targets(ROOT, targets, tracked)
        if args.apply:
            busy = gradle_processes()
            if busy:
                raise ValueError('Gradle processes present; stop/finish builds first. PIDs: ' + ', '.join(busy))
        for name in targets:
            target = ROOT / name
            if target.exists():
                if args.apply:
                    shutil.rmtree(target)
                    print('Removed:', name)
                else:
                    print('Would remove:', name)
            else:
                print('Already absent:', name)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'Refusing cleanup: {error}', file=sys.stderr)
        return 1
    if not args.apply:
        print('Dry run only. Archive necessary release/R8/test evidence before using --apply.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
