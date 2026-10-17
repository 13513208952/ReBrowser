#!/usr/bin/env python3
"""Read-only R8 usage triage; optimizer removal is NOT proof of dead source code."""
import argparse
import hashlib
import json
import pathlib

VARIANTS = ("fullBackgroundRelease", "minimalLiteRelease", "minimalBackgroundRelease")
PREFIX = "io.github.reoutlook."


def parse_usage(text):
    entries = {}
    current = None
    for line in text.splitlines():
        if line and not line[0].isspace():
            current = line.rstrip(":") if line.startswith(PREFIX) else None
            if current:
                entry = entries.setdefault(current, {"classRemovedOrMerged": False, "fields": [], "methods": [], "constructors": []})
                entry["classRemovedOrMerged"] |= not line.endswith(":")
        elif current and line.strip():
            member = line.strip()
            kind = "constructors" if "<init>(" in member else "methods" if "(" in member else "fields"
            if member not in entries[current][kind]:
                entries[current][kind].append(member)
    return entries


def analyze(reports):
    variants = {}
    removed_sets = []
    for variant, text in reports.items():
        entries = parse_usage(text)
        removed = {name for name, info in entries.items() if info["classRemovedOrMerged"]}
        removed_sets.append(removed)
        variants[variant] = {
            "ownEntries": len(entries), "classesRemovedOrMerged": len(removed),
            "fieldEntries": sum(len(e["fields"]) for e in entries.values()),
            "methodEntries": sum(len(e["methods"]) for e in entries.values()),
            "constructorEntries": sum(len(e["constructors"]) for e in entries.values()),
            "entries": entries,
        }
    return {
        "mode": "triage-only", "automaticDeletion": False, "sourceCorrelationVerified": False,
        "warning": "R8 can inline constants/methods, merge classes, remove private constructors and specialize suites. Cross-report removal is still not proof that source can be deleted. Verify source/test/manifest/generated references and regression coverage; reports may be stale.",
        "variants": variants,
        "classesRemovedOrMergedInEveryProvidedVariant": sorted(set.intersection(*removed_sets)) if removed_sets else [],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping-root", type=pathlib.Path, default=pathlib.Path("app/build/outputs/mapping"))
    args = parser.parse_args()
    reports = {}
    provenance = {}
    for variant in VARIANTS:
        path = args.mapping_root / variant / "usage.txt"
        raw = path.read_bytes()
        reports[variant] = raw.decode("utf-8")
        provenance[variant] = {"path": str(path), "sha256": hashlib.sha256(raw).hexdigest(), "mtimeNs": path.stat().st_mtime_ns}
    result = analyze(reports)
    result["reportProvenance"] = provenance
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
