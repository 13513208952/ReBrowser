#!/usr/bin/env python3
"""Measure actual signed APK ZIP occupancy; recognize R8-renamed images by bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def measure(apk):
    apk = Path(apk)
    brands = {hashlib.sha256(p.read_bytes()).hexdigest()
              for p in (ROOT / "app/src").glob("*/res/drawable-nodpi/rb_brand_*.webp")}
    photo = hashlib.sha256((ROOT / "app/src/background/res/raw/rb_new_tab_background.webp").read_bytes()).hexdigest()
    groups = {}
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            digest = hashlib.sha256(archive.read(entry)).hexdigest()
            if entry.filename.endswith(".dex"):
                group = "code"
            elif entry.filename == "resources.arsc":
                group = "resource_index"
            elif digest in brands:
                group = "brands"
            elif digest == photo:
                group = "photo"
            elif entry.filename == "AndroidManifest.xml" or entry.filename.startswith("res/"):
                group = "ui_manifest"
            else:
                group = "metadata"
            item = groups.setdefault(group, {"bytes": 0, "raw_bytes": 0, "files": 0})
            item["bytes"] += entry.compress_size
            item["raw_bytes"] += entry.file_size
            item["files"] += 1
    total = apk.stat().st_size
    groups["packaging_signature"] = {"bytes": total - sum(g["bytes"] for g in groups.values())}
    for group in groups.values():
        group["percent"] = round(100 * group["bytes"] / total, 2)
    return {"bytes": total, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "groups": groups}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", nargs="+", help="LABEL=APK_PATH")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = {}
    for value in args.apks:
        label, path = value.split("=", 1)
        result[label] = measure(path)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
