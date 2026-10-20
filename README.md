# MET Apps (Android)

The mobile companion to the MET dispatch site: sign in with the same account, check duty
status, PNC, and messages on the go, and use a floating push-to-talk button that sits on top
of Roblox so mobile players can use the dispatch radio without switching apps.

## Build the APK
**Easiest (no Android Studio):** push this folder to a GitHub repo -> Actions tab -> "Build APK" ->
download the `MetRadio-apk` artifact (app-debug.apk).

**Android Studio:** open this folder, let Gradle sync, Build -> Build APK(s).

Before building, check `BASE_URL` in `app/build.gradle.kts` matches your dispatch site.

## Server
No server_patch.py needed anymore — everything this app needs (`/api/officer/*`, radio
pairing/token, the Discord Ingress relay) already lives directly in `app.py`.

## How it fits together
- **Login** is the same Auth0 flow as the website, driven in a WebView — there's no separate
  username/password for the app. Once signed in, every screen (native or WebView) shares that
  one session.
- **Home** is a native dashboard: duty status (tap to change), and tiles for PNC, ANPR,
  Messages, Radio, Phone, and CAD/Map.
- **PNC** is a native, read-only list — full editing stays on the dispatch site, dispatcher-only.
- **Messages / CAD-Map / ANPR / Phone** currently open the matching part of the existing web
  app in an embedded WebView rather than reimplementing it natively — same features, no
  duplicate code to maintain. ANPR doesn't yet have its own tracked-vehicle/heading map layer;
  that's a separate piece of work still to do.
- **Radio** is the original floating-PTT overlay, now reached from its own tab. Tapping Start
  asks for whatever's missing — overlay permission, mic permission, pairing — one step at a
  time, rather than needing to set everything up elsewhere first. It runs as a foreground
  service, so it keeps working over Roblox even while you're using other tabs inside this app.
- **Settings** has the detailed permission/pairing status and an Unpair button, for double
  -checking or starting over.

## Install & use
1. Install the APK (allow "install unknown apps" for your browser/file manager).
2. Open MET Apps, log in, and tap the Radio tile.
3. Follow the prompts (overlay permission -> mic permission -> pairing code from the dispatch
   site's Radio panel) -> Start radio overlay.
4. Open Roblox. Hold PTT to talk; drag the ⋮ handle to move it; tap ⋮ to change channel or stop.
