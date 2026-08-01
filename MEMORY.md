# bubble-watch (Cowork Watch) — Memory

## Session Status
- **Status**: Active — Cowork co-pilot feature added 2026-07-31; builds clean, APK sideload pending.
- **Current Focus**: End-to-end test on the Pixel Watch (sideload + configure + voice loop).
- **Blockers**: none (sideload needs physical watch pairing from daptop).
- **Last Updated**: 2026-07-31

## Project Identity
A Wear OS app (`com.darney.bubblewatch`) that started as a toddler bubble-popping toy and
now doubles as **Cowork** — a wrist co-pilot for the parallel Claude Code sessions running
in the `dev` tmux group on fenton. Owner: Darren. One launcher, two modes; bubbles became
the idle/ambient screen shown between prompts.

## Architecture & Patterns
- **Backend**: `clawatch-bridge` — FastAPI on fenton (`~/clawatch-bridge`, systemd user
  service, tailnet/LAN `192.168.0.22:8793`, bearer token in `~/clawatch-bridge/clawatch.env`).
  Wraps `tmux list-panes/capture-pane/send-keys` for `dev:1.*`. Injection-safe: argv lists,
  `send-keys -l -- <text>` then a separate `Enter`, pane addressed by validated integer index.
- **App**: Compose for Wear OS. `BubbleActivity` → `CoworkApp` (`SwipeDismissableNavHost`).
  Screens: threads (crown scroll, status dots) → detail (tail + voice Reply/Add) → idle
  (bubbles) → settings. Networking via Retrofit/OkHttp with absolute @Url so the base can
  change at runtime; config in DataStore. Voice via RemoteInput (`wear-input`).
- **Build**: on fenton, JDK 17 + Android SDK at `/home/darney/Android/Sdk`,
  `./gradlew assembleDebug`. Sideload from daptop (Windows adb) — watch has no USB port.
- **Toddler-lock gotcha**: `BubbleScreen(toddlerLock=true)` = consume-all/no-exit (kid mode).
  Cowork uses `toddlerLock=false`. Do not re-add a global Back block.
