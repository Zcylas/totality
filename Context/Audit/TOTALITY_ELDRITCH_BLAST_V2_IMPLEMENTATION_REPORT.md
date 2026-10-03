# Totality — Eldritch Blast V2 (Visual + Audio Experiment) — Implementation Report

Date: 2026-10-03 · Base: `a77c2353` (Small Slimes V1), master 8 commits ahead of origin · **Nothing committed, pushed or tagged.**
Review bundle: `Context/Audit/Review Bundles/TOTALITY_ELDRITCH_BLAST_V2_REVIEW.zip`

---

## 1. Executive summary

Eldritch Blast now has its own presentation instead of the shared purple dust trail and Evoker sounds:

- **Cast**: a short anticipation (75 ms) where a ring of light coils in at the caster's hand with dark wisps, then a burst.
- **Release**: the beam *snaps out* from the hand to the bolt at 2.6× width and settles (BG3's "wide flash, then thin").
- **Travel**: a white core inside a deep violet halo, pulses flowing forward and two braided filaments twisting around it. The beam follows the unchanged bolt (50 blocks/s); after 0.3 s its tail follows the head, so long shots travel as a ~12-block lance, not a continuous ray (Master §26Q.17: "a discrete force attack rather than recoloured Heat Vision").
- **Impact**: white-hot flash, expanding shock ring, star rays, 18 sparks thrown off the surface; a small distance-limited camera shake.
- **Aftermath**: the beam thins (halo first) in 0.22 s and a dark, branching crack lingers along the path for ~1.2 s (BG3's scorch residue).
- **Multi-beam**: beams fire one after another, 0.2 s apart, each re-aimed at fire time, alternating hands. Preview via a dev-only override (normal play is still 1 beam; Warlock scaling is not wired).
- **Sound**: an original, fully synthesised Totality sound set (3 cast + 3 impact variants), played by the client so it is locked to the visuals; plus the extracted BG3 audio as a **private, git-ignored** comparison option. Measured in-game A/V alignment: cast crack within ~20 ms of the visual release, impact sound within ~5 ms of the impact event.
- **Gameplay unchanged**: same bolt, speed, lifetime, collision, d20 + CHA attack roll, 1d10 Force, cooldown.

Verified in a real client on **OpenGL and Vulkan** (502 frames each, 6/6 in-game checks), 2524/2524 tests (18 new). Cost: ~0.01 ms GPU for one beam, ~0.07 ms for 20 concurrent beams + 10 impacts.

Honest status: the visuals are a strong improvement and close in structure to BG3, but colour (violet vs teal) is undecided, the cast anticipation is necessarily short (no casting animation exists yet), and **I could not listen to any audio** — the sound choice must be made by ear (§8).

---

## 2. Current implementation assessment (before V2)

| Piece | Before V2 | Notes |
|---|---|---|
| Spell | `api/magic/spell/destruction/EldritchBlast` — instant cast, one `SpellBoltEntity`, `withSounds(EVOKER_CAST_SPELL, EVOKER_FANGS_ATTACK)` | `isDefault()` true (dev), cooldown 20 t, 1d10 Force, CHA |
| Projectile | Shared `SpellBoltEntity` (2.5 b/t, 60 t lifetime, ray collision, `CombatResolver.resolveSpellAttack`) | Shared by every bolt spell; Firebolt already has `VisualStyle.FIREBOLT` |
| Visuals | Client tick spawns 2 purple `DustParticleOptions` + 1 enchant particle per tick; impact = 20 dust + 15 enchant + 1 poof | V1 capture: tiny purple dots, barely readable beyond ~10 blocks |
| **V1 bug found** | The impact burst is spawned at `getX()/getY()/getZ()` — the bolt's position at the *start* of the tick — so it appears up to 2.5 blocks short of the target | Visible in `v1_vs_v2_target_close.jpg`. Fixed for Eldritch only (Firebolt already used the hit point; DEFAULT bolts left untouched) |
| Multi-beam | Documented "future", not wired | — |
| Sound | Vanilla Evoker sounds played by the server | — |

**Existing reusable infrastructure used** (implemented, from Fireball V2 / Heat Vision V2): `RibbonGeometry` (camera-facing width-profile ribbons), the Emissive Rendering Layer + `EmissiveBudget` (new group `eldritch`, limit 1.5), Shared Screen FX (shake only), the `core/vfx_fire` vertex shader, the single-draw premultiplied/reversed-Z pipeline pattern of `FireballProjectileVfx`, `ServerScheduler`, the capture harness.
**One-off Eldritch behaviour**: beam timeline, impact model, shader `core/vfx_eldritch`, sounds.
**Not implemented anywhere (still planned)**: generalized VFX API/director, casting/player animation API, area-aware glow budgets, global particle budget, multiplayer VFX sync policy. V2 does not pretend otherwise.

---

## 3. Reference video findings

Located every Eldritch Blast clip: `Context/References/Videos/eldritch_blast_tiktok.mp4` (17.1 s, 576×1024) and `eldritch_blast_youtube.mp4` (59.1 s, 720×1280). A disk-wide search found no other Eldritch/BG3 Eldritch clips (only `Context/References/Other/bg3_dice_roll_reference.mp4`, unrelated, and source snapshots in Totality-Research).

**TikTok — the clean baseline (4 casts, top-down, releases at 3.50 / 6.60 / 10.50 / 14.30 s):**

| Stage | Observation |
|---|---|
| Targeting | BG3 UI: a dotted trajectory arrow (UI, not spell VFX) |
| Charge (~0.5–0.7 s) | Dark smoky tendrils whirl around the caster → teal spiral arcs coil around the body → contract into a white glow at the hand |
| Release (~2 frames) | A very wide teal-white cone/beam with a white core, almost full-screen bloom |
| Thinning (~130 ms) | Collapses to a thin teal line with a dark edge |
| Impact | Fountain of teal/white sparks at the target (no big explosion) |
| Residue (~1–2 s) | A **dark, branching, thorny crack** along the whole beam path, lingering, then fading |
| Feel | Discrete, pulsed: one violent discharge per beam, not a sustained ray |
| Edit overlays | Zoom-punches/radial blur exactly at each release (luminance spikes at −0.57 s and −0.27 s before every release) and a music drop — **editor effects, not spell VFX** |

**YouTube — an edited build montage:** heavy radial blur, zoom cuts, "Critical Hit!" spam, Hex (pink/red) and Agonizing Blast overlays, damage-type lists, subscribe banners, character-creation screens. Useful only for: (a) multi-beam behaviour — several beams leave the same caster in quick succession (~0.2–0.4 s apart), each a separate teal streak to possibly different targets; (b) confirmation of the dark residue (visible at ~6 s). Its colours (pink beams) are build-specific (Hex), not baseline.

**What V2 took:** the stage order (coil → snap release → thin → sparks → dark crack), the pulsed discrete feel, the wide-then-thin release, the residue, and sequential multi-beam. **What V2 deliberately did not take:** the editor zooms/radial blur, full-screen bloom, the character-wide charge (needs a casting animation), BG3 assets.

---

## 4. Audio analysis findings

Extracted both soundtracks (`ffmpeg`), rendered log-frequency spectrograms, and separated stems with **Demucs (htdemucs)** in a scratch venv.

- **YouTube**: wall-to-wall voice-over narration; game audio sits ~18 dB below the voice (stems: vocals −23.8 dBFS, everything else −41.8 dBFS) with a music pad. Unusable as a sound source.
- **TikTok**: a full **music track** (beat) runs from the first release (3.4 s) to 14.6 s — the editor synced the drop to the cast. Demucs cannot cleanly separate the SFX from the music (the spell lands in the "other"/"vocals" stems together with music synths).
- **Cleanest window**: the last TikTok cast (release 14.25 s), where the music stops ~14.45 s. Structure visible there: a broadband **release burst** 1–12 kHz (≈200 ms), then ~300 ms later **descending tonal "zing" chirps** (~1.5 k→1.2 kHz and ~500→400 Hz, plus a ~3 kHz whistle) for ≈0.3 s, then a crackly tail. The charge sound is masked by music in every cast.
- **Cleaner clip**: TikTok (the only one with audible spell audio), but even its best window keeps a constant ambience/music floor (−38 dB relative to peak at the end of the extract; it never decays to silence).

---

## 5. Design decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Hybrid: beam drawn along the real travelling bolt** | Gameplay stays the unchanged projectile (no hitscan change). At 50 b/s a typical 10–15 block shot connects in 0.2–0.3 s, so it reads as a beam; long shots become a travelling lance — a discrete force attack, not Heat Vision |
| D2 | Client-side presentation keyed on `VisualStyle.ELDRITCH`; server only sends one impact event at the **exact hit point + surface normal** | Same pattern as Firebolt; fixes the V1 short-impact bug; no new packets |
| D3 | **Sounds played by the client** at first sight / at the impact event | Locks audio to the drawn release/impact (server sounds would be independent of what is drawn) |
| D4 | 75 ms visual anticipation = the cast sound's 75 ms in-drawn swell | A cantrip with a 1 s cooldown cannot get a real wind-up without a gameplay change; this keeps the crack on the release frame |
| D5 | Beam starts at an approximate **hand position** (first person: lower right of the view; others: front of the right shoulder) | First-person cast reads like BG3 (hand → crosshair). No arm pose exists yet (Player Animation API not started) |
| D6 | Multi-beam: **sequential**, 4 ticks apart, re-aimed per beam, alternating hands | Matches BG3 and D&D (each beam its own attack); readable — overlapping simultaneous beams would merge |
| D7 | Default palette **violet** (the spell icon's colours); **teal** (BG3-like) as dev option | Master §26Q.17: final colours not approved; I did not silently change the spell's identity |
| D8 | No screen flash; small shakes only | A cantrip used every second must not strobe; shake is distance-limited (4→24 blocks) and the caster kick is subject-only |
| D9 | Original synthesised sound as default; reference extract as private dev option, git-ignored | Task §5; the repo has a public GitHub remote |

---

## 6. Visual implementation details

New package `client/vfx/eldritch/`:

- **`EldritchBeamTrack`** (pure, unit-tested): timeline per beam — `RELEASE_DELAY` 75 ms, `RELEASE` 150 ms at 2.6× width, `HOLD` 0.3 s then the tail follows at 50 b/s, `COLLAPSE` 0.22 s, residue in 0.08 / hold 0.35 / out 0.75 s, `MAX_AGE` 3.6 s safety. Also the pure helpers `hand(..)`, `distanceToSegment(..)` and the shader code packing.
- **`EldritchImpact`** (pure, unit-tested): flash/ring 0.36 s; 18 seeded sparks around the normal with drag (3/s) and gravity (10 b/s²); fizzle = no burst.
- **`EldritchBlastVfx`**: tracks `SpellBoltEntity`s with the ELDRITCH style each frame; first sight → origin at the hand, cast sound, flare; release → caster kick (subject-only); disappearance or impact event → collapse + residue; impact event → exact end point, impact, sound, shake. Everything (beams, residues, heads, flares, impacts, sparks) is **one draw per frame**: `POSITION_TEX_COLOR`, premultiplied alpha, reversed-Z depth test (`GREATER_THAN_OR_EQUAL`), no depth writes, `BEFORE_TRANSLUCENT_TERRAIN` — the Fireball V2 recipe. Emissive source: beam core lines, head, flare and impact flash (group `eldritch`, limit 1.5).
- **Shader `core/vfx_eldritch.fsh`** (vertex: existing `core/vfx_fire`): six element modes × two palettes packed into the vertex colour; layered beam compositing (deep fairly opaque halo so the colour survives daylight, braided strands, white core, faint dark fringe), dark residue crack with branches and smoke (black with alpha = darkening), flare (coil → ring burst + dark wisps), impact (flash, ring, rays), sparks. Pixel-quantised across ribbons like Fireball.
- `client/particle/eldritch/EldritchImpactParticle`: a `NoRenderParticle` that only forwards the server's impact event.
- Near-camera safety: ribbon limits `(min 0.002 rad, max 0.03 rad, near fade 0.3→0.8 blocks)`, sprites ≤ 0.10 rad — a beam from your own hand never fills the screen.

Look-development: three real-client iterations (thin pastel laser → wider/stronger → layered compositing). Iteration notes and the dead ends are in the bundle (`sheets/iterations/`).

---

## 7. Audio implementation details

- `ModSounds`: `spell.eldritch_blast.cast`, `.impact` (original), `.cast_reference`, `.impact_reference` (private). `sounds.json`: 3 variants each for the original (Minecraft picks one at random), volume 0.9 / 1.0, 16-block range.
- Files: `assets/totality/sounds/spell/eldritch_blast/cast{1,2,3}.ogg`, `impact{1,2,3}.ogg` — **mono** 44.1 kHz Vorbis q6 (mono is required for positional attenuation; a test checks the header), ~115 KB total.
- Generator: `Context/Tools/eldritch-blast-v2/make_eldritch_sounds.py` (numpy only, deterministic). **Cast** (1.0 s): 75 ms filtered-noise in-drawn swell → release crack → sub thump (96→41 Hz) → mid force punch → inharmonic descending "eldritch" tone (640→330 Hz, partials ×1, ×1.414, ×2.19, vibrato, 41 Hz ring-mod) → high zing (3.3→1.45 kHz) → thinning crackle → faint formant "whisper" (the patron) → 55 Hz hum, small room reverb, soft saturation. **Impact** (0.9 s): crack + thump (82→34 Hz) + rough shatter burst + spark crackle + downward ring-modulated zing.
- First attempt was crackle-heavy (spectral centroid ~6 kHz vs the reference's ~2.5–3 kHz); the final set was darkened to 3.5–4.4 kHz. Both are in the bundle.
- Client playback: `ClientLevel.playLocalSound` at the hand (cast, pitch 0.96–1.04) and at the hit point (impact, pitch 0.95–1.05), `SoundSource.PLAYERS`.
- Variant switch: `/totalityvfx eldritch sound custom|reference` (development) or `-Dtotality.eldritch.sound=reference`.

---

## 8. Sound comparison — custom vs extracted reference

**I cannot hear audio.** Everything below is measured; the decision needs Stefan's ears. Listen to `audio/AB_listen_custom_x2_then_reference_x2_PRIVATE.wav` (two custom casts+impacts, then two reference) and `audio/ingame_recording_custom_then_reference_then_4beams.flac` (the real game, recorded from the game's own audio stream only).

| Metric | Custom (final) | Reference (extract) |
|---|---|---|
| Source | 100 % synthesised, original | BG3 gameplay via TikTok, Demucs "vocals+other" stems, high-passed |
| Cleanliness | decays to −66…−80 dB (silence) | constant floor at −37…−38 dB (ambience/music residue never stops) |
| Onset | defined: crack at 75 ms (cast), transient at 0 ms (impact) | smeared: 50 % envelope only at 210–520 ms |
| Spectral centroid | 3.5–4.4 kHz | 2.4–3.2 kHz |
| In-game A/V (median, 8 / 3 events) | cast crack +57 ms after the cast event (visual release at +75 ms); impact +3 ms | cast onset +16 ms but scattered +15…+121 ms; impact onset not detectable (continuous floor) |
| Variation | 3 variants each | 1 each |
| Licence | Totality's own | third-party, **private review only** |

**Recommendation: keep `custom` as the default.** On every objective axis it is the better *game asset* (clean start/end, defined transients synced to the visuals, mono positional, variations). The reference is not a clean BG3 sound — it is BG3 audio mixed with a TikTok edit's ambience/music, which you will likely hear as a hiss/bed under each cast. If by ear the custom set sounds synthetic or weak, the fallback is in place (`sound reference`) — but I would then rather iterate the custom design (e.g. layer a recorded whoosh you own) than ship the extract.

---

## 9. Multi-beam handling

- Server: `EldritchBlast.beamCount()` = 1 (Warlock scaling still not wired) unless `setDevBeamOverride(1..4)` (ignored outside a development environment; `/totalityvfx eldritch beams <0-4>`). Beam 1 fires immediately, beams 2..n via `ServerScheduler` every 4 ticks, each aimed at the caster's current look; stops if the caster dies/is removed.
- Client: each beam is its own bolt and track (own flare, sound, impact, residue); consecutive beams of one caster within 0.6 s alternate hands.
- Verified in game: 2/3/4 beams fire exactly 2/3/4 beams (sound counters), sheets `multi_beam.jpg`, videos `v2_multi*`. Readability: the 0.2 s spacing gives separate pulses; residues overlap into a thicker crack (reads fine).
- Not done: separate targets per beam (needs a targeting decision); when scaling is wired, `beamCount()` should take the Warlock level.

---

## 10. Files added / modified

Modified (7): `.gitignore` (+ ignore `sounds/spell/eldritch_blast/reference_private/`), `TotalityClient` (3 registrations), `EldritchBlast` (V2 style, no server sounds, multi-beam), `SpellBoltEntity` (`ELDRITCH` style, exact-point impact/expiry event, no dust trail for it), `ModParticles` (`ELDRITCH_IMPACT`), `ModSounds` (4 events), `sounds.json` (4 events).
Added: `client/vfx/eldritch/{EldritchBlastVfx, EldritchBeamTrack, EldritchImpact}.java`, `client/vfx/eldritch/dev/{EldritchBlastDev, EldritchBlastCapture}.java`, `client/particle/eldritch/EldritchImpactParticle.java`, `shaders/core/vfx_eldritch.fsh`, `particles/eldritch_impact.json`, `sounds/spell/eldritch_blast/{cast,impact}{1,2,3}.ogg`, tests `client/vfx/eldritch/{EldritchBeamTrackTest, EldritchImpactTest, EldritchBlastV2WiringTest}`, tools `Context/Tools/eldritch-blast-v2/{make_eldritch_sounds.py, run_eldritch_capture.sh, record_game_audio.sh, analyse_av_timing.py, make_review_media.py}`, this report.
Local only, **git-ignored, not in the patch**: `sounds/spell/eldritch_blast/reference_private/{cast,impact}.ogg`.
CRLF files (`EldritchBlast`, `SpellBoltEntity`, `ModSounds`) kept CRLF.

---

## 11. Build and test results

- `./gradlew compileJava` — OK.
- `./gradlew test --rerun` — **2524 tests, 0 failures, 0 errors, 2 skipped** (pre-existing skips); 18 new: `EldritchBeamTrackTest` 10, `EldritchImpactTest` 4, `EldritchBlastV2WiringTest` 4 (gameplay constants unchanged, one beam by default, override dev-only, client-presented style, no server sounds, exact-point impact event, mono Vorbis assets, reference audio git-ignored, no client references in common code).
- Not run: the dedicated-server live verification suites (`runVerificationServer`) — no server-side system they cover was changed beyond the bolt's visual event.

---

## 12. Real-client verification

All in `runClient` (Mesa 26.2.3, RADV), capture scene 70, one frame per game tick under `/tick rate 4`, real casts through `SpellRegistry.ELDRITCH_BLAST.onActivate` by a visible "Warlock" stand-in player at an armor-stand target 14 blocks away, and by the real player for first/third person.

| Run | Backend | Frames | Checks |
|---|---|---|---|
| `v1_baseline` (before any spell change) | OpenGL | 226 | — |
| `v2_final_gl` | OpenGL | 502 | 6/6 PASS |
| `v2_final_vk` | **Vulkan** | 502 | 6/6 PASS |
| `v2_audio` (real time, sound on) | OpenGL | — | game stream recorded |

Checks: one cast = exactly one cast + one impact sound; beam/impact/residue cleaned up; 2/3/4 beams fire 2/3/4 beams; cleanup after the 20-beam stress.
Views: side, behind, target close, caster close, wall hit, far (~30 blocks), first person (HUD), third person, night side/close, multi-beam (side/behind/close), teal palette, emissive off. GL vs Vulkan look identical (pixel differences come from the world varying between runs — trees, lava — not the effect).
Note: the final GL/Vulkan frame runs were made before two non-visual edits (darkened sound files; removal of an unused impact-sound branch); visuals are unaffected, tests re-run after both.

Timing (A/V): §8 table; `audio/analysis/av_timing.txt`. Recorder start offset uncertainty ≈ ±20 ms; the cast→impact interval is offset-free and matches (~0.2 s for 14 blocks both in events and audio).

---

## 13. Performance / technical notes

Measured with a `TimerQuery` around the single draw (development timing) and CPU time of building the mesh, averaged over the whole lifecycle:

| Scene | OpenGL GPU / CPU | Vulkan GPU / CPU | Quads (peak) |
|---|---|---|---|
| 1 beam | 0.009 / 0.004 ms | 0.011 / 0.004 ms | 38 |
| 20 concurrent beams + impacts | 0.066 / 0.036 ms | 0.076 / 0.042 ms | 647–683 |

- One draw call per frame regardless of beam count; ribbons are 2–32 segments by length; 18 sparks per impact are analytic (no particle engine, no particle budget pressure).
- Not included above: the Emissive Rendering Layer's own passes (measured in Exp. 1 at ~0.06–0.15 ms, shared by all effects). Eldritch glow demand peaked at 0.80 for 20 beams (group limit 1.5, global 6) — never scaled down.
- Blaze3D only (RenderPipeline/RenderPass, no raw GL) → works on Vulkan, verified.
- Network: unchanged entity sync + one particle packet per impact/expiry (as Firebolt).

---

## 14. Known limitations

1. **No casting pose**: the beam leaves the front of the right shoulder; the arm does not rise (Player Animation API not started). The BG3 full-body charge is compressed into a 75 ms hand flare.
2. **Anticipation is short by design** (no gameplay delay). A real wind-up would change gameplay and needs approval (Q4).
3. Third-person-back camera: a beam flying straight along the aim line is hidden behind the caster (same as Fireball; view geometry).
4. If the client predicts the hit a tick before the server's impact event, the collapsing beam can jump forward to the exact point for one frame (not observed: all 105 impact events in the GL/Vulkan/audio runs matched their beam).
4b. The expiry path (a bolt that hits nothing for 3 s → fainter residue, no burst) is covered by unit tests only; no capture produced an expiry (0 fizzle events).
5. Per-tick captures can show the 75 ms anticipation in at most one frame; judge it live.
6. Multi-beam preview uses one target; per-beam targeting not designed.
7. Hit and miss look identical (BG3 also shows the beam reaching the target); the attack roll outcome is only in the combat text.
8. Sound: judged only by measurement (I cannot listen). The reference option is low quality by construction.
9. Not covered: real multiplayer (other players' views), shader packs, hardware other than this Mesa/RADV machine.

---

## 15. Recommended next steps

1. Stefan listens (A/B file + in game: `/totalityvfx eldritch sound custom|reference`) and picks; I iterate the custom set if needed.
2. Decide colours (violet vs teal, or a violet-teal mix) — one shader constant set.
3. If approved: commit V2 (excluding the private reference audio), Master Reference handoff.
4. Later (separate tasks): wire Warlock-level beam scaling; Player Animation V1 hand pose; Magic Missile V2 can reuse `EldritchImpact`-style analytic sparks and the single-draw pattern.

---

## 16. Questions needing your approval

- **Q1 — Sound**: keep the original custom set as default (my recommendation), switch to the private reference, or ask for another custom iteration?
- **Q2 — Colour**: violet (icon identity, default now) or teal (BG3)? Compare `palette_violet_vs_teal.jpg` / `v2_teal_*` videos, or live with `/totalityvfx eldritch palette teal`.
- **Q3 — Private reference audio**: keep the two git-ignored files locally for comparison, or delete them after your decision? (They are excluded from the patch and can never be committed while the ignore line exists.)
- **Q4 — Wind-up**: keep the instant cast (75 ms visual anticipation only), or approve a real cast delay (gameplay change) for a fuller BG3-style charge?
- **Q5 — Multi-beam**: is sequential firing 0.2 s apart (re-aimed per beam) the behaviour you want when Warlock scaling is wired?
- **Q6 — Is Eldritch Blast V2 approved** for commit (then Magic Missile V2 next)?
