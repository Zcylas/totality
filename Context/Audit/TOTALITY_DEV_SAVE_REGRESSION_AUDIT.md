# Totality: dev-client slow "Saving World" regression audit

**Date:** 2026-09-24. **Type:** read-only diagnosis. **No source, Gradle, dependency, world or configuration change was made.** Nothing committed.
**Repository baseline** (verified from files): Minecraft 26.2 · Fabric Loader 0.19.5 · Fabric API 0.161.0+26.2 · Loom 1.18.2 · Gradle wrapper 9.7.1 (uncommitted; HEAD has 9.5.1) · dev JVM OpenJDK 25.0.4.1 (`/usr/lib/jvm/java-25-openjdk`) · Prism instance JVM 25.0.1 · CachyOS, `/home` = Btrfs (`compress=zstd:1,ssd,discard=async`) on NVMe.

---

## A. Executive findings

**Root cause of the slowdown: identified with high confidence at the mechanism level. One in-game A/B toggle remains to close it (§G).**

1. **The development client runs with `syncChunkWrites:true`; the Prism instance runs with `syncChunkWrites:false`.**
   - Dev: `run/options.txt` → `syncChunkWrites:true`
   - Prism: `~/.local/share/PrismLauncher/instances/26.2/minecraft/options.txt` → `syncChunkWrites:false`
   - Vanilla defaults this option to `true` only on Windows (`Options.java:1469`). On Linux, the dev value has been explicitly enabled at some point. The file is outside git (`run/` is ignored), so there is no history of when.
2. **With the option on, the integrated server opens every region file with `StandardOpenOption.DSYNC`.** The call chain in 26.2:
   - `IntegratedServer.forceSynchronousWrites()` returns `options.syncWrites` (`IntegratedServer.java:419-420`).
   - `ServerLevel` passes this to `ChunkMap`/`IOWorker`/`RegionFileStorage` (`ServerLevel.java:246`).
   - `RegionFile` opens the file with DSYNC when sync is set (`RegionFile.java:65-66`).
   - Every chunk save then does two synchronous `pwrite`s: the chunk data, then the 8 KiB header (`RegionFile.java:~304-310`, `writeHeader` at 336-338).
3. **On this Btrfs `/home`, an O_DSYNC write pair costs ~6.1 ms.** That caps chunk saving at **~160 chunks/s**.
   - A standalone reproduction of the exact `RegionFile.write` I/O pattern (`dsync_region_bench.py`) measured 162–164 chunks/s with O_DSYNC, against ~99,000 chunks/s buffered, over 3 runs on the same filesystem.
   - The dev saves show exactly this ceiling in the worlds' own region headers: every sizeable dev-client save since Sep 23 peaks at **153–162 chunks/s**.
   - Prism's saves of the same worlds, minutes apart on the same disk, peak at **1,536–2,131 chunks/s**.
4. **The slow phase is exactly where the dumps and logs point.** The server thread waits in `ChunkMap.saveAllChunks → CompletableFuture.join` while an IO worker sits in `RegionFile.write → pwrite0`. With sync on, that is the expected steady state for the *whole* flush, not only its end.
   - The delay equals roughly (dirty chunks at stop) / 160 per second. For example, ~690 chunks gave the 5 s save at 11:59:55, and ~1,640 chunks gave the 12 s save at 11:39:35.
   - This also explains the variable 2–12 s durations for the *same* world: the amount of dirty data differs per session.
5. **Not implicated by the evidence:**
   - Gradle version (slow and fast saves under both 9.5.1 and 9.7.1)
   - Loom/devlaunch instrumentation
   - Debug agents (no JDWP)
   - Totality persistence code (no chunk-level hooks; its SavedData is tiny)
   - Storage hardware (the same disk writes ~99k chunks/s buffered, and Prism is fast on the same filesystem)
6. **Remaining uncertainty:**
   - **Why the option is `true`.** The first dev-client session with the ~160/s ceiling is Sep 23 00:53; the last dev-client session clearly without it is Sep 16 01:14. The logs covering the change have rotated away.
   - **Secondary amplifier, not dev-specific.** In both environments, almost every loaded chunk is rewritten on quit even after ~2 s of play: Prism rewrote all 3,481 chunks of "Test Uno". That multiplies the DSYNC cost. Whether this is vanilla 26.2 behaviour or mod-induced was not established (§F).

---

## B. Reproduction evidence

### B.1 Every dev Save & Quit since April (from `run/logs/*.log.gz` + `latest.log`, 1 s resolution)
- 513 integrated-server shutdowns were parsed; 13 took ≥ 3 s. **In every slow case the time is entirely in the Overworld phase**, and the Nether/End phases take 0 s.
- Full table: `evidence/shutdown_phases_run_logs.txt`. Script: `scripts/shutdown_phases.py`.

| Log | World | "Stopping server" | Overworld | Other dims | Total |
|---|---|---|---|---|---|
| 2026-09-19-3 | New World | 23:14:36 | 3 s | 0 | 3 s |
| 2026-09-23-4 | Test Uno | 19:52:42 | 5 s | 0 | 5 s |
| 2026-09-23-5 | Test Uno | 20:25:53 | 8 s | 0 | 8 s |
| 2026-09-23-6 | Test Uno | 20:54:44 | 5 s | 0 | 5 s |
| 2026-09-24-4 | Test | 11:38:42 | **10 s** | 0 | 10 s |
| 2026-09-24-4 | Test | 11:39:02 | 5 s | 0 | 5 s |
| 2026-09-24-4 | Test Uno | 11:39:15 | 3 s | 0 | 3 s |
| 2026-09-24-4 | Test S | 11:39:35 | **12 s** | 0 | 12 s |
| 2026-09-24-5 | Test S / Test S / Test Uno | 11:44:42 / 11:45:01 / 11:45:09 | 1 / 1 / 2 s | 0 | 1 / 1 / 2 s |
| latest.log | Test Uno | 11:59:55 | **5 s** | 0 | 5 s |

Four slow saves also occurred in July (2026-07-12, 3–4 s, "New World", loader 0.19.2). That was an older setup, and its sync state is unknown.

**Phases of the most recent session** (`latest.log`, the dev client on "Test Uno"):
- 11:59:53: joined.
- 11:59:54: "Saving and pausing game" (pause menu opened).
- **Click → shutdown begins: ≤ 1 s.** 11:59:55 is "Stopping server".
- **Overworld: 5 s.** 11:59:55 → 12:00:00.
- **Other dimensions: 0 s.**
- **All dimensions saved:** 12:00:00.
- **Return to menu:** not logged. Here "Stopping!" at 12:00:01 is the game exiting. In the dumped 11:45 session, the client was back in its main loop ≤ 1 s after the save (dump 2).

The player was in the world for about 2 s, yet the final save took 5 s.

### B.2 Prism, same worlds, same disk, minutes earlier
Source: Prism `logs/latest.log`, with the same script (`evidence/shutdown_phases_prism.txt`).

| World | "Stopping server" | Overworld | Total |
|---|---|---|---|
| Test Uno | 11:58:13 | 1 s | 1 s |
| Test S | 11:58:19 | 0 s | 1 s |
| Test | 11:58:26 | 0 s | 0 s |

### B.3 How many chunks each save wrote, and how fast
Every Anvil region header stores each chunk's last-write time. `scripts/region_timestamps.py` and `scripts/write_rate_history.py` read those headers **read-only**. Evidence: `evidence/region_timestamps.txt`, `evidence/write_rate_history.txt`.

| Save | Chunks carrying that save's timestamps | Peak chunks/second |
|---|---|---|
| Dev, Test Uno, 11:59:53–12:00:00 | 845 (≈690 after "Stopping server") | **159** (steady 159/s for 4 s) |
| Dev, Test S, 11:39:35 (12 s save) | 1,640 | **161** |
| Dev, Test (1), 11:38:42 (10 s save) | 1,290 | **162** |
| Dev, Test Uno, 2026-09-23 20:54:44 | 707 | **162** |
| Dev, New World, 2026-09-23 00:53 / 00:58 | 1,320 / 529 | **158 / 162** |
| **Prism**, Test Uno, 11:58:10 | **3,481 (every chunk)** | **2,131** |
| **Prism**, Test S, 11:58:17 | **3,481** | **2,112** |
| Dev client, Test, 2026-09-15 13:49 / 2026-09-16 01:14 | 783 / 1,315 | **783 / 400** (no cap then) |
| Scratch world on tmpfs, sync on (2026-09-24 11:24:43) | 1,811 | 1,811 in < 1 s |

- **Prism wrote 4–5× more chunks than dev and still finished in 1 s.** The dev client is not doing more work; it is capped at ~160 writes/s.
- The dev cap was **absent on Sep 15–16** (400–783/s) and **present from Sep 23 00:53 on**.
- The dedicated-server `run/world` also shows ~153/s on 2026-09-22 20:56. That is expected: the dedicated server's `sync-chunk-writes` defaults to `true` (`DedicatedServerProperties.java:111`).

### B.4 Direct reproduction of the I/O cost (no Minecraft involved)
`scripts/dsync_region_bench.py` replays `RegionFile.write`'s pattern for 800 chunks: 4,400-byte data `pwrite` + 8 KiB header `pwrite` at offset 0. It writes a throwaway file in `~/.cache/totality-dsync-bench/`, which was deleted afterwards. Evidence: `evidence/dsync_bench.txt`.

| Filesystem | Buffered (sync=false) | O_DSYNC (sync=true) |
|---|---|---|
| Btrfs `/home` (run 1/2/3) | 99,035 / 99,321 / 99,013 chunks/s | **164 / 163 / 162 chunks/s (6.1–6.2 ms each)** |
| tmpfs `/tmp` (control) | 300,213 chunks/s | 339,613 chunks/s (DSYNC is free) |

The measured O_DSYNC rate matches the dev ceiling (153–162/s) to within a few percent.

### B.5 Thread dumps (`totality-save-dumps.zip`)
The archive was found in the desktop Trash; a copy was read, and the Trash was not touched. Per-dump summary: `evidence/thread_dump_summary.txt`. Script: `scripts/analyze_dumps.py`.
- There are 15 `jstack` dumps of PID 8481 (JDK 25.0.4.1), taken 11:45:11 → 11:45:26 at ~1 s intervals.
- **Only dump 1 (11:45:11) is during a save.** Its Server thread is only 6.43 s old, so the world was loaded at ~11:45:04.
  - **Server thread:** `CompletableFuture.join ← ChunkMap.saveAllChunks:453 ← ServerChunkCache.save ← ServerLevel.save ← MinecraftServer.saveAllChunks ← stopServer ← IntegratedServer.stopServer`
  - **IO-Worker-8:** `UnixFileDispatcherImpl.pwrite0 ← FileChannelImpl.write ← RegionFile.write:420 ← RegionFile$ChunkBuffer.close ← RegionFileStorage.write:82 ← IOWorker.runStore:280`. This is the DSYNC `pwrite`, the only I/O on the path.
  - **Render thread:** `Minecraft.disconnect → renderFrame → FramerateLimiter` (the "Saving world" screen).
- **Dumps 2–15:** there is no Server thread, and the Render thread is back in `Minecraft.runTick`, so the client had returned to the menu by 11:45:12.
- **These dumps belong to the 11:45:09 shutdown, which took only ~2 s** (`2026-09-24-5.log.gz`: "Stopping server" 11:45:09 → "All dimensions are saved" 11:45:11). They captured the tail of a *short* save, not one of the 5–12 s saves. That is consistent with the brief's caution.
- The stack shape (server blocked on the IO worker's `pwrite`) is exactly what a DSYNC-bound save looks like at every instant, not only at the end.
- No deadlock was present: IO-Worker-8 was RUNNABLE in a kernel write. No Totality frame appears on any saving thread.

---

## C. Development vs. production comparison

| Aspect | Dev (IntelliJ "Minecraft Client" / `./gradlew runClient`) | Prism + packaged jar | Verified? | Relevant to save time? |
|---|---|---|---|---|
| **`syncChunkWrites` client option** | **`true`** (`run/options.txt`) | **`false`** | ✅ read both files | **Yes: selects O_DSYNC region I/O (§A.2)** |
| Game dir / saves location | `run/` → `run/saves/*` on Btrfs `/home` | `~/.local/share/PrismLauncher/instances/26.2/minecraft` on the same Btrfs `/home` subvolume | ✅ `findmnt`: identical mount and options; `lsattr`: no flags on either region dir | No (same FS) |
| Render / simulation distance | 12 / 8 | 16 / 12 | ✅ | Prism loads *more* chunks, yet is faster, so this does not explain dev being slow |
| Launcher | IntelliJ Gradle run config runs the Gradle task `:runClient` (`.idea/runConfigurations/Minecraft_Client.xml`, no VM options, debug off), so IntelliJ ≡ terminal | Prism | ✅ | No |
| Main class / launch shim | `net.fabricmc.devlaunchinjector.Main` → Knot (seen in the dumps) | Knot (production) | ✅ (dev); Prism by design | No: not on the save path |
| Dev properties (`.gradle/loom-cache/launch.cfg`) | `fabric.development=true`, `fabric.defaultMixinRemapType=static`, `fabric.defaultModDistributionNamespace=official`, log4j = Loom config + `log4j-dev.xml` | none | ✅ | No evidence: none affects chunk I/O |
| Logging | `log4j-dev.xml`: logger `totality` at DEBUG → console only | vanilla | ✅ | No: no Totality frames or log output on the save path |
| Java agents / debugger | None seen: no JDWP thread in the dumps (`Attach Listener` is jstack's own attach) | `OverrideJavaArgs=false`, `JvmArgs=` empty | ✅ | No |
| JVM | OpenJDK 25.0.4.1 (system), default heap (~7.8 GiB of 31.2 GiB RAM) | Prism runtime 25.0.1, `MaxMemAlloc=4096` | ✅ | Unlikely: the save is I/O-bound in a kernel `pwrite`, not in GC or CPU |
| Classpath | `build/classes/java/main` + `build/resources/main` directories (the debug log shows Knot adding them) | built `totality-1.2.0.jar` + Fabric API jar | ✅ | No: class loading is not on the save path |
| Mixins | same `totality.mixins.json` (no chunk, IO or storage mixins) | same | ✅ | No |
| Chunk I/O threading | vanilla `IOWorker` / `IO-Worker-N` pool (8 threads seen), one consecutive executor per storage | same vanilla code | ✅ code | Same code; only the `sync` flag differs |
| Other options differences | vsync, maxFps, gamma, guiScale, graphics preset, backend, tooltips | | ✅ (`evidence/options_diff.txt`) | No: not on the server save path |

---

## D. Previous "Gradle 9.7 fixed it" regression

- **No earlier investigation is recorded.** No audit document, commit or note in the repository describes the earlier slowdown or a measurement of the fix. The only related file, `Context/Audit/TOTALITY_26.2_POSTFIX_MANUAL_TEST_CLIENT_LOG.log`, shows normal saves.
- **The Gradle 9.7.1 upgrade is uncommitted.** `gradle-wrapper.properties` goes from `9.5.1` to `9.7.1`, and also gains `retries`/`retryBackOffMs`.
  - The 9.7.1 distribution was downloaded on **2026-09-23 01:28** (`~/.gradle/wrapper/dists/gradle-9.7.1-bin`); the last 9.5.1 daemon activity is 01:26.
  - Loom also moved from `1.17-SNAPSHOT` to `1.18.2` (uncommitted); its date is not determinable.
- **The Gradle version does not separate slow from fast:**
  - Gradle 9.5.1 had a slow 3 s save (Sep 19 23:14) and fast saves (Sep 21, all ≤ 1 s).
  - Gradle 9.7.1 has the slow saves on Sep 23 19:52 onward and Sep 24.
  - The only fast dev-log saves between the 9.7.1 install and Sep 23 19:52 are four sessions on Sep 23 13:58–14:05. They match the timestamps of the previous task's tooltip verification-harness launches, which used scratch `--gameDir` copies (most likely on tmpfs `/tmp`, which makes DSYNC free). They are not evidence of a Gradle effect.
- **Sync state is the better discriminator.** Region-header write rates show the ~160/s DSYNC ceiling on dev-client saves from **Sep 23 00:53**, *before* the 9.7.1 download at 01:28. On Sep 15–16 there was no ceiling (400–783/s).
- **Conclusion:** the earlier "fix" is most plausibly a coincidence of low-dirty-chunk sessions, or a period with sync off. It is not a Gradle effect. Whether the July 2026 slow saves (3–4 s, old loader 0.19.2) had the same cause cannot be determined: that `options.txt` state is not recorded. The symptom is identical (all delay in the Overworld phase).

---

## E. Relevant source and configuration findings

**Configuration (root-cause path):**
- `run/options.txt`: `syncChunkWrites:true`. Not tracked by git. It is rewritten by the game on every launch, so its mtime carries no history.
- Vanilla 26.2 (Loom sources `minecraft-merged-043a8b3edf-26.2-sources.jar`):
  - `net/minecraft/client/Options.java:1469`: default `syncWrites = OS == WINDOWS`. Line 1586 is the `syncChunkWrites` option key.
  - `net/minecraft/client/server/IntegratedServer.java:419-420`: `forceSynchronousWrites()` returns `options.syncWrites`.
  - `net/minecraft/server/level/ServerLevel.java:246-269`: passes `syncWrites` to chunk, entity and POI storage.
  - `net/minecraft/world/level/chunk/storage/RegionFile.java:65-66`: `FileChannel.open(..., DSYNC)` when sync.
  - `RegionFile.java:~292-314` (`write`): data `pwrite` + `writeHeader()` (336-338), a second `pwrite` of the 8 KiB header.
  - `RegionFile.java:353-361` (`close`): `padToFullSector` + `force(true)`. This is why region-file mtimes change on every close even when no chunk was written; per-chunk header timestamps were used as evidence instead.
  - `net/minecraft/server/dedicated/DedicatedServerProperties.java:111`: `sync-chunk-writes` defaults to `true` for dedicated/`runServer` runs.

**Totality source audit: nothing on the chunk-save path.**
- There is no `ServerChunkEvents`, chunk data attachment (`AttachmentRegistry`/`setAttached`), `setUnsaved`, or storage/IO mixin anywhere in `src/main/java`. The mixin list (`src/main/resources/totality.mixins.json`) contains no chunk, storage or save mixins.
- Totality's only world SavedData is `BlockDamageStorage` (`api/mining/BlockDamageStorage.java:88-107`, via `util/data/CodecSavedData.java`). It is a small per-dimension map written through vanilla `SavedDataStorage`, not region files.
- Shutdown and logout callbacks only clear in-memory state:
  - `MerchantRuntimeRegistry.java:39` and `TradeSessionManager.java:72` (`SERVER_STOPPED`)
  - `DISCONNECT` handlers in `CrownOfStarsSpell.java:137`, `BlessSpell.java:181`, `StatsServerEvents.java:45`, `PlayerMiningManager.java:69`, `PlayerConnectionEvents.java:239`, `BlockKeyHandler.java:33`, `ResourceSyncLifecycleEvents.java:28`
- **Verification gating: confirmed for every live-world suite.** Each registers only when `VerificationReporter.liveWorldVerificationEnabled()` holds, which requires dev **and** `-Dtotality.liveWorldVerification=true` (`api/core/util/VerificationReporter.java:52-62`). Suites checked: Mining, DurabilityRegression, PowerAttack, MerchantSell, Provisioner, TradingScreen, OffhandAttack and all `rpg/resources/verification/*`. Only `runVerificationServer` (`build.gradle`, disposable `build/verification-run`) sets that property; the `client` run does not.
- **Two dev-only suites run on every dev world start without the live-world opt-in** (observation; neither touches chunks):
  - `api/soulgem/verification/SoulGemSystemVerification.java:49-51`: gated on `isDevEnvironment()` only. It tests ItemStacks and components in memory.
  - `api/economy/value/ItemValueVerification.java:44-47`, called from `ItemValueRegistry.java:70-73` on `SERVER_STARTED`: gated on dev only. It constructs a `TotalityFakePlayer` but never adds it to the level (no `placeNewPlayer`/`addNewPlayer`).
  - Neither can dirty chunks or affect region writes. Both may deserve the explicit opt-in for consistency with the stated design, as a separate decision.

---

## F. Hypotheses

**Confirmed findings**
1. The dev client has `syncChunkWrites:true` and Prism has `false`.
2. With sync on, vanilla opens region files with O_DSYNC (source), and each chunk save is two synchronous `pwrite`s.
3. On this Btrfs `/home`, that I/O pattern runs at ~162 chunks/s, against ~99k/s buffered (benchmark).
4. Dev-client saves since Sep 23 are capped at 153–162 chunks/s; Prism saves of the same worlds on the same FS reach 1.5–2.1k/s (region headers).
5. All of the delay is in the Overworld chunk flush (logs); the saving threads are the server waiting on an IO worker in `pwrite` (dump 1).
6. The provided dumps cover the tail of a 2 s save (11:45:09–11:45:11), not a 5–12 s one.

**Supported hypotheses**
- **H1 (root cause): `syncChunkWrites:true` in `run/options.txt` makes every dev integrated-server chunk write O_DSYNC.** On Btrfs that caps saving at ~160 chunks/s, so a quit with N dirty Overworld chunks takes ≈ N/160 s. It fits every observation, including why Prism (sync off) and scratch runs on tmpfs are fast. The last step, toggling the option in game, is §G.
- **H2 (amplifier, both environments):** almost every loaded chunk is dirty at quit even after seconds of play. Prism rewrote all 3,481 chunks and the tmpfs scratch run 1,811. With buffered I/O this is invisible (< 1 s); with DSYNC it becomes seconds. The cause is unknown: vanilla 26.2 behaviour on load, or mod-induced.

**Unverified possibilities**
- Btrfs specifics (zstd compression, `discard=async`, log-tree commits) make each O_DSYNC write cost ~6 ms. They set *how* expensive DSYNC is, but are not a dev/prod difference.
- The July 2026 slow saves had the same cause (the options state then is unknown).
- A different setting (render distance) raises dirty-chunk counts per session. It does not explain the dev/Prism gap, since Prism uses the larger distance.

**Ruled out (for this regression)**
- **Gradle version:** slow and fast under both 9.5.1 and 9.7.1; the sync ceiling predates the 9.7.1 install.
- **Loom/devlaunchinjector/Knot dev launch, `fabric.development`, dev log4j config, remapping:** none is on the chunk-save path, and dump 1 shows no such frames.
- **Debugger or Java agent:** no JDWP; IntelliJ runs the Gradle task with debug off.
- **Deadlock:** the IO worker was RUNNABLE in `pwrite`, the save completed, and the client returned.
- **Defective storage or filesystem:** the same FS does ~99k buffered writes/s, and Prism saves quickly on it.
- **Totality persistence and lifecycle code, live-world verification leaking into normal play:** no chunk hooks; gating confirmed (§E).
- **Mojang auth/Realms 401 messages:** unrelated; they occur at startup.

---

## G. Recommended next step (not performed)

The root-cause mechanism is established. One experiment is left to close the causal link in game. It changes configuration, so it is described here for approval, not executed:

1. **Discriminating A/B.** Copy one affected world ("Test Uno") as a disposable copy.
   - In the dev client, open Options → Video Settings and set **Sync Chunk Writes: OFF**. Equivalently, set `syncChunkWrites:false` in `run/options.txt` while the game is closed; this is the only change.
   - Join the copy, stand still ~3 s, then Save & Quit.
   - Repeat with the setting ON.
   - **Prediction:** OFF gives ≤ 1 s "Stopping server" → "All dimensions are saved"; ON gives several seconds with a ~160 chunks/s header-timestamp ceiling (check with `scripts/write_rate_history.py`).
   - The mirror test in Prism (turn it ON there) should make Prism slow in the same way.
2. **If confirmed, the smallest corrective action** is to keep `syncChunkWrites:false` for the dev client, the vanilla Linux default. No code change is needed.
   - Optionally note it in the dev setup docs.
   - Keep in mind it trades a small durability guarantee on crash or power loss for speed, exactly as vanilla does on Linux.
   - Dedicated `runServer` / `runVerificationServer` runs use `sync-chunk-writes=true` by default and will show the same per-chunk cost on large saves.
3. **Optional follow-up for H2 (separate task):** check whether chunks are dirtied on load.
   - Run a disposable world through a vanilla-only (no Totality) 26.2 client and compare the per-save chunk counts with `region_timestamps.py`.
   - If only the Totality run rewrites every chunk, audit mod code for that separately.

## Side effects of earlier work, disclosed

- The previous task's verification runs (2026-09-24 11:17, 11:19, 11:23) used scratch `--gameDir`/`--universe` copies. Log4j still writes to the working directory, so they added `run/logs/2026-09-24-1..3.log.gz`.
- `debug.log` keeps only 5 rotations (`.gradle/loom-cache/log4j.xml`, `DefaultRolloverStrategy max="5"`), so **those three launches rotated out three older `debug-*.log.gz` files from earlier dev sessions.** Those files cannot be recovered. No daily `latest` logs were deleted: no explicit max, and log4j's default is 7 per date, which was not exceeded.
- The `2026-09-24-1..3` and `2026-09-23-1..3` sessions (world name `world`, all ≤ 1 s) are verification runs and are excluded from the conclusions above.
- This audit performed no Minecraft launches. It modified no files except creating this report (and the review bundle). Its one disk write outside the scratchpad was the benchmark's throwaway file in `~/.cache/totality-dsync-bench/`, which was deleted.
