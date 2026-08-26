# AJ Live Studio — Phase 12 (Polish + roadmap wrap-up)

Package: `com.amarjeetmaan.ajlivestudio`
Version: 0.12.0 (versionCode 12)

## What's new

- **Control bar reorganized into two clean rows** — Flip/Torch/WB/Layout
  (camera) and Mic/Overlays/Screen/Audio Mixer (production), instead of
  7 icons crammed into one row.
- **Audio Mixer bottom sheet** (new "Tune" icon) — Mic + Music sliders in
  one place, plus the input-route/Bluetooth control moved here from the
  main bar. Same UI-only caveat as before, now consolidated in one spot
  with one clear explanation instead of scattered notes.

## Two things I'm not building, and why

**"App Share"** (from the original spec — sharing just one app instead of
the whole screen): this isn't something standard Android actually exposes.
`MediaProjection` — the only public screen-capture API — captures the
whole display; there's no public API to isolate a single app's rendering
separately from that. Some OEMs have private capture APIs, but nothing
cross-device and public. Rather than build UI that implies this works, I'm
flagging it as not realistically buildable as originally described — the
practical equivalent is Screen Share (Phase 7) with the user manually
opening the app they want to show, since the whole screen gets captured
either way.

**Music mixed into the live audio**: needs the same StreamPack audio-
pipeline hook flagged since Phase 4 (real gain) and now also here (real
music mixing) — no verified, stable API found for it. The mixer UI is in
place and ready; wiring in real audio processing is a good candidate for
that on-device StreamPack verification pass I've flagged for several
phases now.

## How to get the APK

1. Push this folder to your GitHub repo (same package ID — installs as an
   update over Phase 1-11).
2. Actions tab → run "Build APK" (or push to `main`).
3. Download the `AJLiveStudio-debug-apk` artifact.
4. Install on your tablet/phone.

## Where the roadmap stands after 12 phases

**Solid, verified, no known gaps:**
- Studio setup, camera preview + controls (flip/torch/zoom/focus/exposure/WB)
- Custom RTMP + YouTube Direct API live streaming (GO LIVE actually works)
- Mic mute (real), dual-camera hardware detection (real)
- Overlay/scene/layout design tools (fully functional locally)
- Screen-share permission flow (real)

**Waiting on one on-device StreamPack build-and-test pass:**
- Baking overlays into the actual broadcast (needs `ISurfaceProcessorInternal`)
- Screen share → broadcast wiring (needs confirming the exact service base class)
- Real mic gain + music mixing (needs the audio-pipeline hook)
- Dual-camera + layout compositing into one broadcast feed (depends on the above)

That one verification pass is genuinely the highest-leverage next step —
it unblocks four separate pending pieces at once instead of each needing
its own guess-and-flag cycle. Happy to do that whenever you're ready to
run a build and share what happens.
