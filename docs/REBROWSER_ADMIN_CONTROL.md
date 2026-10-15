# ReBrowser ADB administrator control

ReBrowser is a mode inside the same `io.github.reoutlook` application, not a second APK. Its administrator bridge exists for development, recovery, and controlled automation. It does not weaken the stricter ReOutlook mail-export protocol.

## Removed host-layout zoom experiment

The rejected host-resize/View-transform experiment has been removed from current source, including its protocol operations and capability advertisement. It is not browser engine page zoom. Old requests are rejected as unsupported operations; there is no replacement zoom command. Public v1.0.2 never included it. Previously installed development previews are unchanged until an authorized upgrade; see the [removal record](REBROWSER_LAYOUT_ZOOM.md).

## Platform and authorization boundary

Both exported ReBrowser administrator components require `android.permission.DUMP`, so only ADB shell/system callers can reach them. Every command then uses a three-minute, single-use challenge bound to the exact request, installation ID, request ID, random nonce, issue time, and expiry.

Two immutable public roots are compiled into the application:

- `reoutlook-root-v1`: RSA-3072/SHA-256, fingerprint `0c045556f779ddc469cdf64c14b33f27904bf75fd48956111860a9f081be99fd`;
- `rebrowser-root-v1`: ECDSA P-256/SHA-256, fingerprint `38d674fb6eb66ed4efaf2360def9c023c81b18a594b3fcea127bc87338a0ef01`.

Both roots can independently authorize all three ReBrowser control levels and cannot be disabled by an application setting or administrator command. The ReBrowser root does **not** authorize ReOutlook mail export. ReOutlook export continues to require its RSA administrator signature and a separate foreground owner-credential confirmation.

The device owner may approve level-one and level-two ReBrowser operations using the Android system lock credential instead of a root signature. Level-three operations always require at least one administrator root signature and cannot be approved by the owner credential alone.

Other trusted keys, if introduced, must be added with explicit scopes in source code and a new APK. There is no command for enrolling a key. Non-root keys may be marked unavailable by a later build; the two development roots have no runtime revocation state.

## Authorization levels

- **Level 1:** bounded state, diagnostics, navigation, UI routing, browser settings, download policy, redacted download status, and website-permission policy/grant metadata.
- **Level 2:** lifecycle and exact-object operations such as locking, shelving, closing, or deleting a specific workspace/tab; enabling/disabling new downloads; approving, rejecting, cancelling, retrying or deleting one download; and privacy-preserving one-way disabling of a website capability. Delete commands bind an exact object ID and require `confirmDelete=true`.
- **Level 3:** invariant repair and global deletion. `REPAIR_STATE` repairs bounded workspace metadata and removes expired, malformed, future-dated, disabled, duplicate or excessive website grants; `REPAIR_DOWNLOADS` reconciles bounded task metadata; `CLEAR_DOWNLOADS` removes every recorded download and corresponding managed file; `CLEAR_SITE_PERMISSION_GRANTS` removes all grants or all grants for one named permission. Global deletion requires `confirmDelete=true`.

Administrator website-permission control is deliberately monotonic: it can query, disable, or clear, but cannot enable a capability, approve a prompt, create an authorization window, or access clipboard, location, camera, or microphone data.

The owner-confirmation screen displays the operation, level, target IDs, and a destructive-operation warning. It does not offer an owner-only approval button for level three.

## Protocol v2

Every request contains a protocol version, unique request ID, operation, optional target IDs, and bounded arguments. Results are stored independently by request ID and move through structured states:

- `challenge-created`
- `authorized`
- `queued`
- `running`
- `completed`
- `failed`
- `cancelled`

Terminal results contain structured details or a bounded error string. The CLI polls `getResult` by request ID instead of guessing from the foreground UI. Navigation commands can use `--wait`; completion is then reported only after the targeted main frame finishes, fails, or reaches the bounded timeout.

A bounded audit ring records the request ID, operation, level, authorization method, key ID, target IDs, status, and time. It does not record complete URLs or page content.

## Supported command groups

`tools/admin/rebrowser_admin.py` currently supports:

- capability and state: `capabilities`, `state`, `diagnostics`, `launcher-status`, `history-status`, `settings-status`, `audit`, `validate`;
- navigation: `open`, `reload`, `stop`, `back`, `forward`, `home`, and non-disclosing `assert-location`;
- object selection: `activate-workspace`, `activate-tab`;
- creation and UI: `new-workspace`, `new-tab`, `workspaces`, `tabs`, `settings`, `history`, `outlook`;
- lifecycle: `lock`, `unlock`, `promote`, `demote`, `shelve`, `restore`;
- destructive operations: `close-workspace`, `close-tab`, `delete-shelved` with `--confirm-delete`;
- settings: `set-home` and whitelisted `set-pref` values;
- downloads: `show-downloads`, `download-policy`, `set-downloads-enabled`, `downloads`, `approve-download`, `reject-download`, `cancel-download`, `retry-download`, and exact-object `delete-download`;
- website permissions: `site-permissions`, one-way `disable-site-permission PERMISSION`, and root-only `clear-site-permissions [PERMISSION] --confirm-delete`;
- level-three bounded repair/global cleanup: `repair`, `repair-downloads`, `clear-downloads --confirm-delete`, and `clear-site-permissions --confirm-delete`.

Examples:

```bash
export REBROWSER_ROOT_PRIVATE_KEY=/secure/path/rebrowser-root-v1-private.pem

python3 tools/admin/rebrowser_admin.py capabilities --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py state --key-id rebrowser-root-v1 --compact
python3 tools/admin/rebrowser_admin.py new-workspace --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py open https://www.baidu.com/ \
  --workspace WORKSPACE_ID --tab TAB_ID --wait --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py lock WORKSPACE_ID --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py close-workspace WORKSPACE_ID \
  --confirm-delete --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py download-policy --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py set-downloads-enabled false --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py set-downloads-enabled true --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py downloads --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py approve-download DOWNLOAD_ID --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py delete-download DOWNLOAD_ID \
  --confirm-delete --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py repair-downloads --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py site-permissions --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py disable-site-permission camera \
  --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py clear-site-permissions clipboard \
  --confirm-delete --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py repair --key-id rebrowser-root-v1
```

The existing ReOutlook RSA root remains accepted:

```bash
export REOUTLOOK_MAINTENANCE_PRIVATE_KEY=/secure/path/reoutlook-maintenance-rsa-private.pem
python3 tools/admin/rebrowser_admin.py diagnostics --key-id reoutlook-root-v1
```

Use `--auth device` for an owner-approved level-one or level-two command. Run `--help` for target and wait options.

## Device and orientation diagnostics (v1.0.1-beta)

Level-one `SET_PREFERENCE deviceType` (`set-pref deviceType auto|phone|tablet`) is a fixed enum and executes natively without creating a browser. `GET_SETTINGS_STATUS.general.device` reports the persisted choice, automatic/effective type, source, diagonal, threshold, full-display pixels and physical DPI. Missing dimensions/DPI default to phone; manual tablet still overrides that fallback.

Browser diagnostics add `device`, `window` (scope `browser-activity-window`) and `systemRotation` (scope `system-settings-read-only`). Window fields are actual Activity multi-window/PiP/full-window, requested/configuration orientation, fullscreen video and whether tablet four-direction policy applies. Rotation fields only read system auto-rotate and user-rotation settings, with null for unavailable values. Native capability/fallback snapshots explicitly set `browserWindowStatusAvailable=false`; they do not present the administrator Activity as the browser window. No system-setting writes, sensor reads or arbitrary keys are introduced. See [device policy](REBROWSER_DEVICE_MODE.md) for limitations.

## ReOutlook desktop-entry diagnostics

Protocol v2 adds the level-one, read-only `GET_LAUNCHER_STATUS` operation (`launcher-status` in the CLI). It uses the existing challenge/signature or owner-credential authorization and audit flow. The query runs directly in the protected administrator Activity, without opening a ReBrowser workspace or loading a website.

It returns only four fixed booleans: `outlookLauncherVisible`, `outlookActivityEnabled`, `rebrowserLauncherEnabled`, and `administratorCanSet` (always false). Capabilities and diagnostics include the same snapshot under `launcherVisibility`. Visibility reports the configured launcher component state; the desktop launcher may take time to refresh its icon cache.

The normal display/hide control exists only in ReBrowser settings. It changes the `ReOutlookLauncher` activity alias using PackageManager with `DONT_KILL_APP`; MainActivity, ReBrowser's launcher, mail state, and named Profiles remain untouched. In v1.0.0 the control lives in Application Management, not General or the browser menu. Fresh installations hide the subsoftware launcher; upgrades preserve explicit owner choices and the old implicit-visible default using a one-time migration. Application Management and the existing `outlook` administrator operation route directly to MainActivity, not through the toggleable alias.

There is no administrator setter for this choice, no arbitrary component-name input, and no component-enable/disable command. `set-pref outlookLauncherVisible ...` is not whitelisted. Hiding the alias is not disabling the mail feature, uninstalling the app, or preventing explicit navigation to ReOutlook.

```bash
python3 tools/admin/rebrowser_admin.py launcher-status --key-id rebrowser-root-v1
```

## History diagnostics and UI

Protocol v2 adds level-one `GET_HISTORY_STATUS` (`history-status`) and `SHOW_HISTORY` (`history`). Both retain the existing DUMP permission, one-use challenge, authorization, structured result, and audit boundaries. The status query runs directly in the protected administrator Activity without loading a browser page; the UI route opens the owner's history list, not a history-data export.

The status snapshot contains only `maintaining`, `paused`, `recordCount`, `timedRecordCount`, `longLivedRecordCount`, `retentionSeconds`, `nextExpiryAt`, `lastStorageError` (exception type only), and `administratorCanSet=false`. Capabilities and diagnostics include it under `history`. Timed counts classify normally limited sources even when maintaining suspends expiry; zero next-expiry means no currently effective automatic expiry. Reading may perform ordinary timestamp expiry cleanup but does not change switches.

No operation returns history URLs, titles, entries or page contents, changes its switches, deletes or exports arbitrary history, or accesses SQLite directly. The ordinary history interface may display complete URLs to the owner; this protocol boundary is not a promise to conceal visible UI from an authorized device owner or shell.

```bash
python3 tools/admin/rebrowser_admin.py history-status --key-id rebrowser-root-v1
python3 tools/admin/rebrowser_admin.py history --key-id rebrowser-root-v1
```

## Three peer settings pages (v1.0.0)

The native settings UI has General, Isolation/Permissions, and Application Management peer pages. Personalization UI is removed. `OPEN_SETTINGS` accepts an optional fixed `section` (`general`, `isolation`, or `applications`); old requests with no section default to General. Unknown sections and non-string values are rejected. The CLI uses `settings [SECTION]`.

Level-one `GET_SETTINGS_STATUS` (`settings-status`) runs directly in the protected administrator Activity without loading a website. Capabilities and diagnostics include the same `settings` snapshot: fixed browser preferences, photo availability, stored/effective background, and global website policy. It omits the homepage URL entirely (only `homeCustomized` is returned), all grant identities, history, and website data.

The level-one `SET_PREFERENCE` whitelist adds `newTabBackground` (`white`/`exclusive`) and `newTabRecent` (Boolean). Lite can remember `exclusive` but still displays white; this difference is explicit in the snapshot. No arbitrary image URL/file, settings-key reader, isolation-mode setter, capability-enabling command, component setter, or history setter is added. Existing one-way permission operations and authorization levels are unchanged. See [settings scope](REBROWSER_SETTINGS.md).

```bash
python3 tools/admin/rebrowser_admin.py settings applications
python3 tools/admin/rebrowser_admin.py settings isolation
python3 tools/admin/rebrowser_admin.py settings-status
python3 tools/admin/rebrowser_admin.py set-pref newTabBackground white
python3 tools/admin/rebrowser_admin.py set-pref newTabRecent true
```

## Native mode management and runtime diagnostics (v1.0.0)

New level-one operations retain DUMP, exact signed one-use challenge, both immutable roots, owner-credential limits, and audit records:

- `GET_APPLICATION_STATUS` / `applications`: fixed same-package modes, launcher visibility and provider availability; no component setter.
- `GET_RUNTIME_STATUS` / `runtime`: installed WebView identity/version and missing browser features, without creating a workspace or WebView.
- `OPEN_APPLICATION_MANAGEMENT` / `application-management`: opens the native settings peer page.
- `OPEN_MODE` / `mode browser|outlook`: only these fixed values. Unsupported browser requests open native management and report `view=application-management`, `modeAvailable=false`; absent provider rejects Outlook with `webview-unavailable`.

Each CLI authorization starts a fresh transient administrator task, with a 30-second deadline per ADB call. Native settings are launched outside that task and reordered to the foreground; repeated requests honor the selected section without finishing the browser or discarding drafts.

Capabilities execute natively. When the browser is unavailable, diagnostics use a `scope=native-environment` snapshot with `workspaceRuntimeLoaded=false`, runtime/application/settings/history/launcher status and fixed download/security policy. It is not a live workspace diagnostic. Browser-dependent commands fail with `browser-runtime-unavailable` rather than silently using Outlook's Default Profile or remaining queued. Existing native history/settings/launcher/audit queries remain usable. Provider availability is not a guarantee that Outlook login works on every vendor.

The old `settings.personalization` diagnostic key and bounded background/recent setters remain protocol-v2 compatibility fields, not a hidden UI page. Defaults are white in lite, photo in background and recent visits enabled; explicit old choices survive upgrades and flavor switches.

No default-browser setter, arbitrary Intent/component, launcher setter, install command, website-capability enablement/grant or private-file export is added. New commands do not authorize ReOutlook mail export. Release builds remain non-debuggable; no public feature override is introduced for tests.

## Android handoff additions (v0.4.5-alpha)

`settings.general.defaultBrowser` adds only `registered`, `roleAvailable`, `roleHeld`, and `administratorCanSet=false`. No other browser identity, file location, handoff URI, default-role setter, arbitrary Intent, or installer operation is exposed. The ordinary owner's settings button opens the system default-app UI only on click, including when the app already holds the browser role. The exported `.1` file entry is a separate ordinary Android surface, not an administrator operation; final installation remains a system/user decision. See [Android handoff](REBROWSER_ANDROID_HANDOFF.md).

## Data boundary

State and diagnostics expose bounded workspace/tab/download IDs, controlled Profile names, titles, page origins, load progress, main-frame errors, lifecycle levels, feature support, non-secret settings, download progress, risk reasons and SHA-256. Page URL paths, queries, and fragments are stripped. Download request locations expose only the HTTP(S) origin; paths, query parameters and fragments are stripped, and Blob/Data payloads are never returned.

Website-permission results expose only global booleans plus Profile name, HTTPS origin, permission type, grant and expiry times. They never expose clipboard values, coordinates, media frames, audio, Android permission payloads, or arbitrary page data. There is intentionally no administrator command to enable a website capability or create a site grant.

The bridge never reads or returns passwords, raw Cookie, Cookie digests, URL tokens, Web Storage, IndexedDB, Service Worker data, or ReOutlook mail. Disabling the download policy rejects pending and new requests while allowing already-running transfers to reach a stable terminal state. Download commands do not include an open, install, preview, unpack, execute, arbitrary destination, or arbitrary header operation. The protocol does not provide arbitrary JavaScript execution, arbitrary database access, arbitrary private-file access, or arbitrary Intent execution.

Ordinary third-party applications cannot call this ADB-only bridge. A future plugin API must use a separate signature permission and explicit capability grants rather than exposing this administrator surface.
