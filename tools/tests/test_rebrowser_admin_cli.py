#!/usr/bin/env python3
"""Pure request-building checks; no ADB, credentials, or device changes."""
import importlib.util
import pathlib
import types
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / "admin" / "rebrowser_admin.py"
spec = importlib.util.spec_from_file_location("rebrowser_admin", path)
admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admin)


def request(command, *arguments):
    return admin.build_request(types.SimpleNamespace(
        command=command, arguments=arguments, workspace=None, tab=None,
        wait=False, timeout=30, confirm_delete=False))


class SettingsCommands(unittest.TestCase):
    def test_legacy_settings_still_has_default_route(self):
        self.assertEqual("OPEN_SETTINGS", request("settings")["operation"])
        self.assertNotIn("section", request("settings"))

    def test_three_fixed_peer_routes(self):
        for section in ("general", "personalization", "isolation"):
            self.assertEqual(section, request("settings", section)["section"])
        for section in ("unknown", "true", "io.github.reoutlook.MainActivity"):
            with self.assertRaises(SystemExit):
                request("settings", section)

    def test_bounded_status_request(self):
        self.assertEqual("GET_SETTINGS_STATUS", request("settings-status")["operation"])
        with self.assertRaises(SystemExit):
            request("settings-status", "privatePreference")

    def test_personalization_values_have_correct_wire_types(self):
        for value in ("white", "exclusive"):
            self.assertEqual(value, request("set-pref", "newTabBackground", value)["value"])
        self.assertIs(False, request("set-pref", "newTabRecent", "false")["value"])
        self.assertIs(True, request("set-pref", "newTabRecent", "true")["value"])

    def test_arbitrary_files_components_and_privilege_switches_are_rejected(self):
        for name, value in (("newTabBackground", "file:///private/image"),
                            ("newTabBackground", "https://example.com/image"),
                            ("newTabRecent", "maybe"), ("camera", "true"),
                            ("outlookLauncherVisible", "false"), ("historyPaused", "true")):
            with self.assertRaises(SystemExit):
                request("set-pref", name, value)

    def test_existing_settings_and_site_permission_commands_remain_compatible(self):
        self.assertEqual("google", request("set-pref", "searchEngine", "google")["value"])
        self.assertIs(False, request("set-pref", "thirdPartyCookies", "false")["value"])
        self.assertEqual("DISABLE_SITE_PERMISSION", request("disable-site-permission", "camera")["operation"])
        with self.assertRaises(SystemExit):
            request("clear-site-permissions")


if __name__ == "__main__":
    unittest.main()
