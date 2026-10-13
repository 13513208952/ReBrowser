# ReOutlook and ReBrowser administrator tools

These tools control two modes of the same `io.github.reoutlook` application; ReBrowser is not a
separate installed app. Neither administrator surface has a normal in-app entry point. The
maintenance components remain registered but are callable only by Android shell permission. The
ReOutlook export tool submits an offline RSA-3072/SHA-256 administrator signature and then requires
the device owner to approve the exact export through Android's system lock credential UI.

Private keys are deliberately not stored in this repository or APK.

ReOutlook mail export keeps the stricter rule: administrator signature **and** device-owner lock
confirmation are both mandatory. ReBrowser protocol v2 uses three authorization levels: either
immutable administrator root may independently authorize every level; the device owner may approve
only level one or two. See [the ReBrowser control protocol](../../docs/REBROWSER_ADMIN_CONTROL.md).

## Required private keys

Set the paths explicitly:

```bash
export REOUTLOOK_MAINTENANCE_PRIVATE_KEY=/secure/path/reoutlook-maintenance-rsa-private.pem
export REOUTLOOK_EXPORT_PRIVATE_KEY=/secure/path/reoutlook-export-rsa-private.pem
```

## Request an owner-approved export

```bash
python3 tools/admin/reoutlook_admin.py export
```

The owner must review the foreground warning, enter the system lock credential, and choose a file.
The resulting `.reoutlook-admin` file is encrypted for the offline administrator export key.

If a run is interrupted, invalidate the pending one-time challenge:

```bash
python3 tools/admin/reoutlook_admin.py clear
```

A normal app launch also clears stale maintenance challenges.

## Control ReBrowser

The existing ReOutlook RSA root and the dedicated ReBrowser ECDSA root are both accepted. Select
the matching key ID and private-key path explicitly:

```bash
export REBROWSER_ROOT_PRIVATE_KEY=/secure/path/rebrowser-root-v1-private.pem
python3 tools/admin/rebrowser_admin.py capabilities --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py state --key-id rebrowser-root-v1 --compact
python3 tools/admin/rebrowser_admin.py open https://www.baidu.com/ \
  --workspace WORKSPACE_ID --tab TAB_ID --wait --key-id rebrowser-root-v1
```

Protocol v2 provides per-request structured results, object-ID targeting, bounded load waits,
lifecycle control, diagnostics, audit metadata, settings, website-permission control, validation,
download-task control, and root-only repair. Commands that close or delete an object—or globally
clear downloads or website grants—require `--confirm-delete`.

Three peer settings pages (v1.0.0): General, Isolation/Permissions, Application Management. Personalization UI is removed; bounded protocol-v2 preference controls remain compatible.

```bash
python3 tools/admin/rebrowser_admin.py settings                 # General; legacy-compatible
python3 tools/admin/rebrowser_admin.py settings applications
python3 tools/admin/rebrowser_admin.py applications
python3 tools/admin/rebrowser_admin.py runtime
python3 tools/admin/rebrowser_admin.py application-management
python3 tools/admin/rebrowser_admin.py mode outlook
python3 tools/admin/rebrowser_admin.py settings isolation
python3 tools/admin/rebrowser_admin.py settings-status
python3 tools/admin/rebrowser_admin.py set-pref newTabBackground exclusive
python3 tools/admin/rebrowser_admin.py set-pref newTabRecent true
python3 tools/admin/rebrowser_admin.py set-pref deviceType tablet  # v1.0.1-beta: auto | phone | tablet
```

The CLI starts each authorization in a fresh transient task and bounds individual ADB calls to 30 seconds. Native settings are kept outside that task and reordered to the foreground without finishing browser activities or losing drafts.

These are level-one operations. The fixed status snapshot omits the homepage URL, site-grant identities and website data; the read-only query does not load a website. Lite retains an exclusive-background preference without displaying the absent image. Settings arguments and values are whitelisted; this does not add permission enabling, arbitrary preference/file access, launcher or history setters, or a claimed full incognito mode. See [the settings scope](../../docs/REBROWSER_SETTINGS.md).

v1.0.1-beta device/orientation support adds a fixed `deviceType` enum and read-only native screen/DPI/classification fields to settings status. Browser diagnostics report the actual browser Activity's multi-window/PiP/full-window state and requested/configuration orientation, plus the system's auto-rotate/user-rotation settings read-only. Native fallback diagnostics explicitly report that browser-window status is unavailable; the transient authorization window is not presented as the browser window. No system-setting writes, sensor access or arbitrary device/preference keys are added.

Desktop-entry diagnostics:

```bash
python3 tools/admin/rebrowser_admin.py launcher-status --key-id rebrowser-root-v1
```

This level-one read-only query reports the fixed Outlook launcher/mail Activity/ReBrowser launcher states. It does not open a website or change the entry. Capabilities and diagnostics also expose the snapshot. Visibility can only be changed in ReBrowser settings; there is no administrator component setter or whitelisted visibility preference. The existing `outlook` command still works when the desktop alias is hidden.

History diagnostics and UI:

```bash
python3 tools/admin/rebrowser_admin.py history-status --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py history --key-id rebrowser-root-v1
```

These level-one operations query aggregate retention/recording state or open the owner's history UI. They do not return history URLs/titles or provide a switch, delete, export, or arbitrary database command. Status reads apply normal timestamp expiry cleanup. The same snapshot is available in capabilities and diagnostics.

When ADB lists multiple connections, select the phone using `ANDROID_SERIAL` before running a command.

Download examples:

```bash
python3 tools/admin/rebrowser_admin.py download-policy --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py downloads --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py set-downloads-enabled false --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py set-downloads-enabled true --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py approve-download DOWNLOAD_ID --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py cancel-download DOWNLOAD_ID --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py delete-download DOWNLOAD_ID \
  --confirm-delete --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py repair-downloads --key-id rebrowser-root-v1
```

The administrator can disable new downloads (which rejects pending requests but does not corrupt already-running transfers), approve or manage files, but cannot command ReBrowser to open, install, preview, unpack, or execute one. Results reduce request URLs to origins and never contain raw Profile Cookie.

Website-permission commands are one-way and privacy preserving:

```bash
python3 tools/admin/rebrowser_admin.py site-permissions --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py disable-site-permission camera \
  --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py clear-site-permissions clipboard \
  --confirm-delete --key-id rebrowser-root-v1
```

They can query, disable, or clear grants, but cannot enable a capability, approve a website request,
or read clipboard, location, camera, or microphone data.

The owner-authorized alternative does not require a private key, but the owner must confirm the
exact level-one or level-two command on the phone. Level three rejects owner-only authorization:

```bash
python3 tools/admin/rebrowser_admin.py new-tab --auth device
```

Run `python3 tools/admin/rebrowser_admin.py --help` for the complete bounded command set. It does not
expose Cookie, tokens, Web Storage, arbitrary JavaScript execution, or ReOutlook mail data.

## Decrypt an approved export

The decryptor requires Python's `cryptography` package:

```bash
python3 tools/admin/decrypt_reoutlook_export.py \
  exported.reoutlook-admin -o recovered.zip
unzip -t recovered.zip
```

The ZIP contains a versioned manifest plus account-scoped JSON Lines files. It never includes
Outlook cookies, passwords, access tokens, refresh tokens, or Web Storage.

## Protocol boundary

- Entry components remain registered but require `android.permission.DUMP` (ADB shell).
- Challenges expire after three minutes and are single-use.
- Signatures cover the installation ID, exact operation and arguments, nonce, expiry, and—where relevant—the target account.
- The administrator signing key authorizes an operation; a separate RSA key encrypts exports.
- ReOutlook export authorization alone cannot bypass the foreground system lock credential prompt.
- Either immutable root can authorize every ReBrowser level without an additional owner prompt.
- Foreground device credential alone is limited to ReBrowser authorization levels one and two.
- Other trusted keys can only be added with explicit scopes in source code and a new APK.
