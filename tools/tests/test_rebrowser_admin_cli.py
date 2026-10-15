#!/usr/bin/env python3
"""Pure request-building checks; no ADB, credentials, or device changes."""
import importlib.util
import pathlib
import types
import unittest
from unittest import mock

path = pathlib.Path(__file__).resolve().parents[1] / "admin" / "rebrowser_admin.py"
spec = importlib.util.spec_from_file_location("rebrowser_admin", path)
admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admin)


def request(command, *arguments):
    return admin.build_request(types.SimpleNamespace(
        command=command, arguments=arguments, workspace=None, tab=None,
        wait=False, timeout=30, confirm_delete=False))


class SettingsCommands(unittest.TestCase):
    def test_removed_host_zoom_commands_are_absent(self):
        for command in ("page-zoom", "page-zoom-status"):
            self.assertNotIn(command, admin.OPERATIONS)
            with self.assertRaises(KeyError):
                request(command)

    def test_legacy_settings_still_has_default_route(self):
        self.assertEqual("OPEN_SETTINGS", request("settings")["operation"])
        self.assertNotIn("section", request("settings"))

    def test_three_fixed_peer_routes(self):
        for section in ("general", "isolation", "applications"):
            self.assertEqual(section, request("settings", section)["section"])
        for section in ("unknown", "personalization", "true", "io.github.reoutlook.MainActivity"):
            with self.assertRaises(SystemExit):
                request("settings", section)

    def test_native_application_commands_and_fixed_modes(self):
        for command, operation in (("applications", "GET_APPLICATION_STATUS"), ("runtime", "GET_RUNTIME_STATUS"),
                                   ("application-management", "OPEN_APPLICATION_MANAGEMENT")):
            self.assertEqual(operation, request(command)["operation"])
            with self.assertRaises(SystemExit): request(command, "arbitraryKey")
        for mode in ("browser", "outlook"):
            self.assertEqual(mode, request("mode", mode)["mode"])
        for mode in ("com.android.settings", "file:///private/file", "true"):
            with self.assertRaises(SystemExit): request("mode", mode)
        with self.assertRaises(SystemExit): request("mode")

    def test_bounded_status_request(self):
        self.assertEqual("GET_SETTINGS_STATUS", request("settings-status")["operation"])
        with self.assertRaises(SystemExit):
            request("settings-status", "privatePreference")

    def test_personalization_values_have_correct_wire_types(self):
        for value in ("white", "exclusive"):
            self.assertEqual(value, request("set-pref", "newTabBackground", value)["value"])
        self.assertIs(False, request("set-pref", "newTabRecent", "false")["value"])
        self.assertIs(True, request("set-pref", "newTabRecent", "true")["value"])

    def test_device_choice_is_a_fixed_enum_not_an_orientation_or_sensor_switch(self):
        for value in ("auto", "phone", "tablet"):
            self.assertEqual(value, request("set-pref", "deviceType", value)["value"])
        for value in ("sensor", "full-sensor", "true", "file:///device"):
            with self.assertRaises(SystemExit):
                request("set-pref", "deviceType", value)

    def test_arbitrary_files_components_and_privilege_switches_are_rejected(self):
        for name, value in (("newTabBackground", "file:///private/image"),
                            ("newTabBackground", "https://example.com/image"),
                            ("newTabRecent", "maybe"), ("camera", "true"),
                            ("outlookLauncherVisible", "false"), ("historyPaused", "true")):
            with self.assertRaises(SystemExit):
                request("set-pref", name, value)

    def test_launch_always_creates_a_fresh_transient_authorization_task(self):
        args = types.SimpleNamespace(command="runtime", arguments=[], workspace=None, tab=None,
            wait=False, timeout=30, confirm_delete=False, auth="key", key_id=None, private_key=None)
        with mock.patch.object(admin, "provider_call", return_value="Bundle[{challenge=one-use, requestId=request}]") as provider, \
             mock.patch.object(admin, "selected_key", return_value=("reoutlook-root-v1", None)), \
             mock.patch.object(admin, "sign_challenge", return_value=b"signature"), \
             mock.patch.object(admin, "adb") as adb, \
             mock.patch.object(admin, "wait_for_result", return_value={"status": "completed"}):
            self.assertEqual("completed", admin.run_request(args)["status"])
            launch = adb.call_args.args
            self.assertEqual(("shell", "am", "start", "-W", "-f", "0x18000000"), launch[:6])
            self.assertEqual(admin.ACTIVITY, launch[7])
            self.assertIn("--es", launch)
            self.assertEqual("createChallenge", provider.call_args.args[0])

    def test_adb_commands_have_a_transport_deadline(self):
        with mock.patch.object(admin.subprocess, "run", return_value=types.SimpleNamespace(stdout="done")) as run:
            self.assertEqual("done",admin.adb("shell", "echo", "test"))
            self.assertEqual(30,run.call_args.kwargs["timeout"])

    def test_existing_settings_and_site_permission_commands_remain_compatible(self):
        self.assertEqual("google", request("set-pref", "searchEngine", "google")["value"])
        self.assertIs(False, request("set-pref", "thirdPartyCookies", "false")["value"])
        self.assertEqual("DISABLE_SITE_PERMISSION", request("disable-site-permission", "camera")["operation"])
        with self.assertRaises(SystemExit):
            request("clear-site-permissions")


if __name__ == "__main__":
    unittest.main()
