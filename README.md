# MET Radio Overlay (Android)

Floating push-to-talk button that sits on top of Roblox, so mobile players can use the dispatch radio.

## Build the APK
**Easiest (no Android Studio):** push this folder to a GitHub repo -> Actions tab -> "Build APK" ->
download the `MetRadio-apk` artifact (app-debug.apk).

**Android Studio:** open this folder, let Gradle sync, Build -> Build APK(s).

Before building, check `BASE_URL` in `app/build.gradle.kts` matches your dispatch site.

## Server
Paste `server_patch.py` into app.py (after the `/api/radio/token` route) and restart.

## Install & use
1. Install the APK (allow "install unknown apps" for your browser/file manager).
2. Open MET Radio -> do steps 1-3 -> Start radio overlay.
3. Open Roblox. Hold PTT to talk; drag the ⋮ handle to move it; tap ⋮ to change channel or stop.
