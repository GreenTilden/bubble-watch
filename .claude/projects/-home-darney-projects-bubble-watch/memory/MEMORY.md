# Bubble Watch — Memory

## Session Status
- **Status**: Active
- **Date**: 2026-03-10
- **Current Focus**: Assess on-device issues, fix deprecation warnings, get app reliably working
- **Blockers**: Need to confirm watch/emulator connectivity for testing
- **Next Steps**: Fix deprecated flags, test on device, address any swipe-dismiss or screen issues

## Project Identity
- **Purpose**: Toddler distraction app for Oliver's Wear OS watch — tap to pop bubbles
- **Tier**: 2 (active development)
- **Repo**: `/home/darney/projects/bubble-watch` (git initialized 2026-03-10)

## Architecture & Patterns
- Single Activity (`BubbleActivity`) + single Composable (`BubbleScreen`)
- Canvas-based rendering with `rememberInfiniteTransition` for animation frames
- Toddler lock: back button blocked, swipe consumed via `pointerInput`, ambient mode handled
- Haptic feedback on tap (30ms, amplitude 60)
- Soft muted color palette, max 6 bubbles, 2.5s lifetime

## Known Issues
- `FLAG_TURN_SCREEN_ON` and `FLAG_SHOW_WHEN_LOCKED` deprecated — should use Activity methods instead
- Wear OS swipe-to-dismiss may still get through depending on OS version
- "Kind of working but not really" — need to identify specific on-device issues
