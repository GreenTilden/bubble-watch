# Cowork Watch (was Bubble Watch)

Wear OS app, one launcher (`com.darney.bubblewatch`, label "Cowork"), two modes:

1. **Cowork** (primary) — a wrist co-pilot for the parallel Claude Code sessions in the
   `dev` tmux group on **fenton**. List threads with status, pick with the rotating crown,
   read the tail, voice-dictate a reply (RemoteInput), and append supplemental text.
2. **Bubbles / idle** — the original tap-to-pop bubble animation, now the idle/ambient
   screen shown while threads are working (a toddler can also just stay on it).

Talks to **clawatch-bridge** (FastAPI on fenton, `~/clawatch-bridge`) at
`http://192.168.0.22:8793` with a bearer token, entered on the app's Settings screen.

## Stack
- Kotlin + Jetpack Compose for Wear OS; Wear Compose 1.3.0; compose-navigation;
  Retrofit/OkHttp; DataStore; `androidx.wear:wear-input` (RemoteInput voice).
- Gradle 8.5, Kotlin 1.9.22, compileSdk 34, minSdk 30, JDK 17.
- Android SDK at `/home/darney/Android/Sdk` (installed on fenton 2026-07-31).

## Build (on fenton)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd ~/projects/bubble-watch && ./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Sideload **directly from fenton** over wireless debugging (watch paired to fenton's adb
keys 2026-07-31; no daptop needed). The port changes per session — discover it:
```bash
A=~/Android/Sdk/platform-tools/adb
$A mdns services            # Pixel Watch 3 (wrist) = product:luna @ 192.168.0.18:PORT
                            # Pixel Watch 2 (pendant) @ 192.168.0.145:PORT — update BOTH
$A connect 192.168.0.18:PORT
$A -s 192.168.0.18:PORT install -r app/build/outputs/apk/debug/app-debug.apk
```
If connect is refused, wireless debugging is off on the watch — Settings → Developer
options → Wireless debugging (pairing already done; just toggle it on).

## Key Files
- `BubbleActivity.kt` — single activity; ambient + keep-screen-on; hosts `CoworkApp`.
  (The old global back-block was REMOVED — it was toddler-only and trapped the co-pilot.)
- `ui/CoworkApp.kt` — `SwipeDismissableNavHost`: threads / detail / idle / settings.
- `cowork/threads/*` — thread list (crown scroll, status dots).
- `cowork/detail/*` — tail view + Reply/Add voice flow.
- `cowork/input/VoiceReply.kt` — RemoteInput voice dictation.
- `cowork/idle/IdleScreen.kt` — bubbles as idle; polls for NEEDS_INPUT → haptic + return.
- `BubbleScreen.kt` — bubble animation; `toddlerLock` flag scopes the pointer-consume /
  no-exit behavior to bubble mode only.
- `data/*` — BridgeApi, BridgeRepository, AuthInterceptor, Settings (DataStore), Models.

## Toddler-lock gotcha
`BubbleScreen(toddlerLock=true)` consumes ALL pointer events (and the activity used to
block Back) — that is ONLY for the standalone bubble/kid use. Cowork mode uses
`toddlerLock=false` + swipe-dismiss nav. Never re-apply the global back-block.
