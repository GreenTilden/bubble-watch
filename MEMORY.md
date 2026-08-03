# bubble-watch (Cowork Watch) — Memory

## Session Status
- **Status**: Active — 1833e08 build SIDELOADED 2026-08-03 (v2.1, direct from fenton over
  wireless adb — see CLAUDE.md Build section; daptop no longer in the loop) and launched.
- **Milestone**: **E2E voice loop VERIFIED 2026-08-03** — watch dictation → bridge
  `POST /threads/1/send` → tmux pane → Claude session replied, observed live from inside
  the target session itself. `/suggest` + `/summary` also confirmed working (200s, from
  the Watch 2 pendant). Both watches on v2.1.
- **Current Focus**: real-world daily use; next dev work is whatever friction that surfaces.
- **Blockers**: none.
- **Last Updated**: 2026-08-03

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
  `./gradlew assembleDebug`. Sideload from fenton over wireless adb (paired 2026-07-31;
  `adb mdns services` → connect → install; watch has no USB port). Flow in CLAUDE.md.
- **Toddler-lock gotcha**: `BubbleScreen(toddlerLock=true)` = consume-all/no-exit (kid mode).
  Cowork uses `toddlerLock=false`. Do not re-add a global Back block.
