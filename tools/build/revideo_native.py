#!/usr/bin/env python3
"""Build native ReVideo in an explicitly owned, bounded scratch directory."""
import argparse
import fcntl
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time

GIB = 1024 ** 3
REPO = Path(__file__).resolve().parents[2]
MODULE = REPO / "revideo"
MARKER = ".rebrowser-native-build-owner"


class SpaceLimit(RuntimeError):
    pass


def size_bytes(directory):
    result = subprocess.run(["du", "-sb", str(directory)], text=True, capture_output=True,
                            env={**os.environ, "LC_ALL": "C"})
    # Gradle atomically replaces transform directories while the scan is running.
    vanished = result.returncode == 1 and result.stderr and all(
        line.startswith("du: cannot access ") and line.endswith(": No such file or directory")
        for line in result.stderr.splitlines()
    )
    if not vanished:
        result.check_returncode()
    return int(result.stdout.split()[0])


def check_space(repo, work, *, initial=False, size=None):
    root_floor = 3 * GIB if initial else 2 * GIB
    work_floor = 12 * GIB if initial else 8 * GIB
    root_free = shutil.disk_usage(repo).free
    work_free = shutil.disk_usage(work).free
    if root_free < root_floor:
        raise SpaceLimit(f"System filesystem has {root_free / GIB:.2f} GiB free; need {root_floor / GIB:.0f} GiB")
    if work_free < work_floor:
        raise SpaceLimit(f"Build filesystem has {work_free / GIB:.2f} GiB free; need {work_floor / GIB:.0f} GiB")
    if size is not None and size > 16 * GIB:
        raise SpaceLimit("Owned build workspace exceeded its 16 GiB limit")


def stop_group(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            break
        process.poll()
        time.sleep(0.05)
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def run_guarded(command, *, cwd, env, work):
    check_space(REPO, work, initial=True, size=size_bytes(work))
    print("Running:", " ".join(map(str, command)), flush=True)
    process = subprocess.Popen(command, cwd=cwd, env=env, start_new_session=True)
    next_size_check = time.monotonic()
    try:
        while True:
            try:
                code = process.wait(timeout=1)
                if code:
                    raise subprocess.CalledProcessError(code, command)
                check_space(REPO, work, size=size_bytes(work))
                return
            except subprocess.TimeoutExpired:
                now = time.monotonic()
                used = size_bytes(work) if now >= next_size_check else None
                if used is not None:
                    next_size_check = now + 10
                check_space(REPO, work, size=used)
    except BaseException:
        stop_group(process)
        raise


def ensure_owned(work):
    if work.is_symlink():
        raise ValueError("Scratch root must not be a symlink")
    work.mkdir(parents=True, exist_ok=True)
    marker = work / MARKER
    identity = str(REPO) + "\n"
    if marker.exists():
        if marker.read_text() != identity:
            raise ValueError("Scratch directory belongs to another checkout")
    elif any(work.iterdir()):
        raise ValueError("Refusing to adopt a non-empty, unmarked directory")
    else:
        marker.write_text(identity)
    for directory, names, files in os.walk(work, followlinks=False):
        for name in names + files:
            child = Path(directory) / name
            if child.is_symlink() and not child.resolve().is_relative_to(work.resolve()):
                raise ValueError(f"Scratch link escapes owned directory: {child}")


def remove_owned(work, relative):
    if (work / MARKER).read_text() != str(REPO) + "\n":
        raise ValueError("Missing workspace ownership marker")
    target = work / relative
    if target.is_symlink() or not target.resolve().is_relative_to(work.resolve()) or target.resolve() == work.resolve():
        raise ValueError(f"Refusing to clean outside an owned child directory: {target}")
    if target.exists():
        shutil.rmtree(target)


def cleanup(work):
    for name in ("builds", "project-cache", "flutter-assembly", "tmp"):
        remove_owned(work, name)
    caches = work / "gradle-caches"
    if caches.exists():
        for version in caches.iterdir():
            if re.fullmatch(r"\d+\.\d+(?:\.\d+)?", version.name):
                for transient in ("transforms", "kotlin-dsl", "groovy-dsl"):
                    remove_owned(work, str((version / transient).relative_to(work)))
    # Artifacts and pinned native libraries are retained; cached dependencies are
    # expendable when they would otherwise accumulate beyond the retention cap.
    if size_bytes(work) > 8 * GIB:
        remove_owned(work, "gradle-caches/modules-2")


def link_directory(link, target):
    target.mkdir(parents=True, exist_ok=True)
    if link.is_symlink():
        if link.resolve() != target.resolve():
            raise ValueError(f"Existing generated link has another target: {link}")
    elif link.exists():
        if any(link.iterdir()):
            raise ValueError(f"Refusing to replace non-empty generated directory: {link}")
        link.rmdir()
        link.symlink_to(target, target_is_directory=True)
    else:
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(target, target_is_directory=True)


def prepare(work):
    home = MODULE / ".dart_tool" / "native-gradle-home"
    home.mkdir(parents=True, exist_ok=True)
    for name in ("caches", "daemon", "native", "notifications", "jdks", ".tmp"):
        link_directory(home / name, work / ("gradle-" + name.lstrip(".")))
    distributions = home / "wrapper" / "dists"
    distributions.mkdir(parents=True, exist_ok=True)
    sources = [Path.home() / ".gradle/wrapper/dists"]
    if old_home := os.environ.get("GRADLE_USER_HOME"):
        sources.insert(0, Path(old_home) / "wrapper/dists")
    for wrapper in (REPO / "gradle/wrapper/gradle-wrapper.properties",
                    MODULE / ".android/gradle/wrapper/gradle-wrapper.properties"):
        match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-(all|bin)\.zip", wrapper.read_text())
        if match is None:
            raise ValueError(f"Unsupported wrapper declaration: {wrapper}")
        name = match.group(0).removesuffix(".zip")
        candidates = [root / name for root in sources] + [work / "gradle-wrapper" / name]
        link = distributions / name
        established = link.resolve() if link.is_symlink() else None
        if established is not None and established.name != name:
            raise ValueError(f"Invalid established wrapper cache: {link}")
        target = established or next((path for path in candidates if path.is_dir()), candidates[-1])
        link_directory(link, target)
    link_directory(MODULE / ".dart_tool/flutter_build", work / "flutter-assembly")
    link_directory(MODULE / "build", work / "builds/flutter-default")
    init = home / "init.d" / "revideo-storage.gradle"
    init.parent.mkdir(parents=True, exist_ok=True)
    init.write_text('''def base = new File(System.getenv("REBROWSER_NATIVE_WORK_ROOT"))
def stage = System.getenv("REBROWSER_BUILD_STAGE")
gradle.beforeProject { p ->
    def name = p.path == ":" ? "root" : p.path.substring(1).replace(":", "/")
    p.layout.buildDirectory.set(new File(base, "builds/${stage}/${name}"))
    if (stage == "flutter") {
        p.pluginManager.withPlugin("com.android.library") {
            p.extensions.getByName("androidComponents").finalizeDsl { android ->
                if (android.compileSdk != null && android.compileSdk < 36) {
                    android.compileSdk = 36
                }
            }
        }
    }
}
gradle.projectsEvaluated {
    gradle.rootProject.allprojects.each { p ->
        if (!p.layout.buildDirectory.get().asFile.canonicalFile.toPath().startsWith(base.canonicalFile.toPath())) {
            throw new GradleException("Unmanaged build output: ${p.path}: ${p.layout.buildDirectory.get().asFile}")
        }
    }
}
''')
    return home


def stop_gradle(wrapper, env):
    try:
        subprocess.run([str(wrapper), "--stop"], cwd=wrapper.parent, env=env,
                       timeout=20, check=True)
    except (OSError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
        raise RuntimeError("Could not confirm owned Gradle shutdown; refusing cache cleanup") from error


def main():
    def interrupted(signum, frame):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-root", type=Path, required=True)
    parser.add_argument("--flutter-sdk", type=Path, required=True)
    parser.add_argument("--stage", choices=("module", "hosts", "all"), default="all")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    work = args.work_root.absolute()
    ensure_owned(work)
    lock_path = MODULE / ".dart_tool/native-build.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        check_space(REPO, work, initial=True, size=size_bytes(work))
        home = prepare(work)
        if args.prepare_only:
            print(f"Space guard and generated output routing ready: {work}")
            return
        env = os.environ.copy()
        env.update({
            "GRADLE_USER_HOME": str(home),
            "GRADLE_OPTS": "-Dorg.gradle.jvmargs=-Xmx2048m -Dorg.gradle.workers.max=1 -Dorg.gradle.daemon=false",
            "REBROWSER_NATIVE_WORK_ROOT": str(work),
            "REBROWSER_NATIVE_LIB_CACHE": str(work / "native-libs"),
            "REBROWSER_BUILD_ROOT": str(work / "builds/hosts"),
            "REBROWSER_REVIDEO_MAVEN_DIR": str(work / "artifacts/module/host/outputs/repo"),
            "TMPDIR": str(work / "tmp"),
            "TMP": str(work / "tmp"),
            "TEMP": str(work / "tmp"),
            "JAVA_TOOL_OPTIONS": (
                env.get("JAVA_TOOL_OPTIONS", "")
                + f' -Djava.io.tmpdir="{work / "tmp"}" -XX:+PerfDisableSharedMem'
            ).strip(),
        })
        stages = []
        if args.stage in ("module", "all"):
            stages.append(("flutter", MODULE / ".android/gradlew", MODULE,
                [str(args.flutter_sdk / "bin/flutter"), "--suppress-analytics", "build", "aar",
                 "--no-pub", "--no-debug", "--no-profile", "--release", "--no-tree-shake-icons",
                 "--target-platform", "android-arm,android-arm64,android-x64", "--output", str(work / "artifacts/module"),
                 "--android-project-cache-dir", str(work / "project-cache/flutter"),
                 "--android-project-arg", "kotlin.compiler.execution.strategy=in-process",
                 "--android-project-arg", "org.gradle.workers.max=1"]))
        if args.stage in ("hosts", "all"):
            stages.append(("hosts", REPO / "gradlew", REPO,
                [str(REPO / "gradlew"), "--no-daemon", "--max-workers=1", "--project-cache-dir",
                 str(work / "project-cache/hosts"), ":compat:assembleArm64Debug",
                 ":compat:assembleX86_64Debug", ":app:assembleFullBackgroundDebug"]))
        for stage, wrapper, cwd, command in stages:
            (work / "tmp").mkdir(exist_ok=True)
            env["REBROWSER_BUILD_STAGE"] = stage
            try:
                run_guarded(command, cwd=cwd, env=env, work=work)
                if stage == "hosts":
                    source = work / "builds/hosts"
                    for module in ("app", "compat"):
                        output = source / module / "outputs/apk"
                        if output.exists():
                            for apk in output.rglob("*.apk"):
                                destination = work / "artifacts/apk" / module / apk.relative_to(output)
                                destination.parent.mkdir(parents=True, exist_ok=True)
                                os.replace(apk, destination)
            finally:
                stop_gradle(wrapper, env)
                cleanup(work)
        print(f"Retained artifacts: {work / 'artifacts'}")


if __name__ == "__main__":
    try:
        main()
    except (SpaceLimit, OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Native build stopped: {error}", file=sys.stderr)
        sys.exit(1)
