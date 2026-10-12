# Security policy

ReBrowser includes the ReOutlook mail mode in the same APK and has not received an independent security audit.
A first non-alpha release number does not imply a hardened enterprise browser or mail product.

## Security boundaries

- The mail database remains in Android credential-protected app-private storage.
- The current database relies on Android file-based encryption and is not independently encrypted with SQLCipher.
- Android automatic backup is disabled; exports use an explicit, authenticated flow.
- The native bridge accepts structured mail data only from configured Outlook mail origins.
- Passwords, cookies, access tokens, refresh tokens, and Web Storage are excluded from native persistence and exports.
- Offline HTML is rendered with JavaScript, network loading, local file access, and navigation disabled.
- ReOutlook has no download path. ReBrowser downloads bind Cookie lookup to the exact named Profile and use Cookie only for the concrete HTTP(S) request.
- Downloaded files are never automatically opened, installed, previewed, unpacked, or executed. The separate, user-initiated local `.1` APK handoff uses `REQUEST_INSTALL_PACKAGES` and a bounded unchanged copy with a private read-only content URI; only an allowlisted system installer is invoked. Android unknown-source authorization, signature verification and final user confirmation remain mandatory. The administrator protocol has no install or arbitrary-file command.
- Blob/Data extraction uses fixed application code, bounded size, one active Blob transfer, one-time object names, and chunked output; it is not an administrator or website-controlled JavaScript API.
- Web-page external intents require a foreground main-frame user gesture and an allowlisted scheme. `file:`, `content:`, `javascript:`, and `intent:` navigation is rejected.
- ReBrowser website grants are bounded to a named Profile, HTTPS top-level origin, permission type, and expiry. Global enablement never grants a site automatically; disabled capabilities reject without prompting and delete existing grants.
- Camera, microphone, and precise location use one-request or three-hour authorization. Approximate location and text clipboard access use five-minute or fifteen-day authorization. No clipboard value, coordinate, or media data enters permission persistence, administrator results, logs, or audit.
- Native WebView geolocation is disabled. The fixed semantic location bridge coarsens approximate results before disclosure. Website notifications, push, and motion sensors are permanently disabled. ReOutlook explicitly denies WebView media and geolocation requests even though Android runtime permissions belong to the shared APK.

- Browser capability checks precede workspace initialization. Missing isolation or permission features open native Application Management, never Outlook's Default Profile. Mail can use an available provider without Multi-Profile; provider availability is not an Outlook-compatibility guarantee.
- Subsoftware desktop visibility is owner-controlled, defaults hidden for fresh installations and preserves old upgrade state. Administrator operations accept only fixed mode IDs, not arbitrary components, Intents or visibility setters.
- Public APKs retain the historical Android Debug certificate for upgrade continuity but are non-debuggable Release builds. This is not a new production-signing identity; private signing material remains outside the repository.

## Administrator maintenance interfaces

Administrator components have no launcher or normal in-app entry point and require the platform `android.permission.DUMP` permission used by Android shell. A ReOutlook data export additionally requires a short-lived, single-use administrator signature bound to the installation, active account, operation, nonce, and expiry. The device owner must then approve the export through Android's system lock credential UI.

The bounded ReBrowser protocol-v2 bridge uses a separate one-use challenge and three authorization levels. Either immutable administrator root may independently authorize every ReBrowser level; foreground device credential alone is limited to levels one and two. Requests and results carry unique IDs, destructive commands bind exact object IDs, and level-three repair requires a root signature. The bridge can inspect and manage bounded download tasks but cannot open, install, or execute them. Download results omit raw Cookie and reduce request URLs to origins so paths, queries, fragments, and embedded URL tokens are not disclosed. It does not expose arbitrary JavaScript execution, Cookie, tokens, Web Storage, or ReOutlook mail data. Details are in [docs/REBROWSER_ADMIN_CONTROL.md](docs/REBROWSER_ADMIN_CONTROL.md).

The APK contains only administrator public keys. Private keys and private derivation/recovery material must never be committed to the public repository or packaged in an APK. The development roots have no runtime revocation switch; replacing one requires a code and APK update.

## Reporting a vulnerability

Open a minimal public issue without including real mail, credentials, database files, logs, or administrator keys. For sensitive reports, contact the repository owner through a private GitHub channel before sharing diagnostic material.