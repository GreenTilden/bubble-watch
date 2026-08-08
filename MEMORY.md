# bubble-watch (Cowork Watch) — Memory

## Session Status
- **Status**: Active — a27e166 build (verified 🛁 CLEAR + 4 stage vibrates + Σ spend meter
  7c0a6eb) SIDELOADED to Watch 3 2026-08-03 evening and **VERIFIED working on-wrist**.
  **Pendant still on 0d06d6b** — push a27e166 next time it's charged/awake (fenton
  wireless adb; watch may Wi-Fi-doze — have Darren open the Wireless-debugging screen).
- **Bath hardening (a27e166)**: /clear was fire-and-forget — mid-response it QUEUED as a
  chat message and the tail re-seeded into uncleared context (reproduced in scratch tmux).
  Now: Escape + C-u first, sentinel-verify old tail gone before paste, one retry, else
  "⚠ not cleared" abort. Per-stage 45ms pulses + ✓/⚠ double-buzz (`Haptics.pulse()`).
- **Milestone**: **Multi-select menus VERIFIED from the wrist 2026-08-03** — ☐/☑ toggle
  chips + "✔ Submit these" → bridge `/submit-menu` (Tab → review tab → "Submit answers"),
  driven live from the Watch 3 against a real AskUserQuestion dialog. Root cause of
  "no tap-to-answer on fresh sessions" was trailing blank pane rows swallowing the
  25-line tail window — fixed bridge-side in `_do_capture`. E2E voice loop was verified
  earlier the same day; both watches current.
- **Current Focus**: real-world daily use; next dev work is whatever friction that surfaces.
- **Blockers**: none. (The `daliquot_1_solo` ledger mislabels — hook session-context
  leak on 3 rows — were corrected in EllaBot via PUT on 2026-08-03; bubbles commits
  post with no sprint_code until it has its own contract.)
- **Last Updated**: 2026-08-03

## Project Identity
A Wear OS app (`com.darney.bubblewatch`) that started as a toddler bubble-popping toy and
now doubles as **Cowork** — a wrist co-pilot for the parallel Claude Code sessions running
in the `dev` tmux group on fenton. Owner: Darren. One launcher, two modes; bubbles became
the idle/ambient screen shown between prompts.

## Architecture & Patterns
- **Backend**: `clawatch-bridge` — FastAPI on fenton (`~/clawatch-bridge`, systemd user
  service, tailnet/LAN `<bridge-host>:<bridge-port>`, bearer token in `~/clawatch-bridge/clawatch.env`).
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
