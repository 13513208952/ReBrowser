#!/usr/bin/env python3
"""Synthetic optimizer-report checks. No source deletion or build/device interaction."""
import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / "maintenance/analyze_r8_usage.py"
spec = importlib.util.spec_from_file_location("r8_usage_analysis", path)
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)


class UsageAnalysisTests(unittest.TestCase):
    def test_third_party_members_do_not_attach_to_own_class(self):
        data = analysis.parse_usage("io.github.reoutlook.A:\n    int x\nandroidx.B:\n    void thirdParty()\n")
        self.assertEqual(["int x"], data["io.github.reoutlook.A"]["fields"])
        self.assertEqual([], data["io.github.reoutlook.A"]["methods"])

    def test_fields_methods_and_constructors_are_separate(self):
        data = analysis.parse_usage("io.github.reoutlook.A:\n    static final int VALUE\n    private void <init>()\n    boolean status()\n")
        entry = data["io.github.reoutlook.A"]
        self.assertFalse(entry["classRemovedOrMerged"])
        self.assertEqual(1, len(entry["fields"]))
        self.assertEqual(1, len(entry["methods"]))
        self.assertEqual(1, len(entry["constructors"]))

    def test_suite_intersection_is_not_a_deletion_list(self):
        result = analysis.analyze({"full": "io.github.reoutlook.Constants\nio.github.reoutlook.Merged\n",
                                   "minimal": "io.github.reoutlook.Constants\n"})
        self.assertEqual(["io.github.reoutlook.Constants"], result["classesRemovedOrMergedInEveryProvidedVariant"])
        self.assertFalse(result["automaticDeletion"])
        self.assertFalse(result["sourceCorrelationVerified"])
        self.assertIn("stale", result["warning"])

    def test_repeated_class_sections_merge_without_losing_or_double_counting_members(self):
        data = analysis.parse_usage("io.github.reoutlook.A:\n    int x\nio.github.reoutlook.A:\n    int x\n    void y()\n")
        self.assertEqual(["int x"], data["io.github.reoutlook.A"]["fields"])
        self.assertEqual(["void y()"], data["io.github.reoutlook.A"]["methods"])

    def test_empty_input_does_not_invent_candidates(self):
        result = analysis.analyze({})
        self.assertEqual([], result["classesRemovedOrMergedInEveryProvidedVariant"])


if __name__ == "__main__":
    unittest.main()
