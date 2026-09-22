# Finite-water performance verification

Status: implementation benchmarked; remaining limits listed below, 2026-09-22. No commit, push,
deployment, or original-save replacement is part of this work.

## Changes

- Puddle BFS uses reusable primitive position/depth/direction arrays and a primitive
  visited set, with explicit reentrancy ownership. Traversal and mutation order remain unchanged.
- A bounded, identity-keyed shape-pair/direction cache reuses pure barrier calculations.
  World-dependent collision retrieval and gameplay rules remain live.
- Extended-path configuration iteration avoids stream allocation. Parsed tag selectors
  use a bounded cache; tag membership is still read on every call, including after reload.
- Pump structure and series discovery is shared within one server tick. Pump block,
  block-entity, and chunk lifecycle changes invalidate it. Live power and water are not cached.
- Isolated pump assemblies receive deterministic phases. Nearby assemblies conservatively
  retain the original cadence. The separated-assembly fixture met the peak-time target;
  the dense fixture did not meet the same threshold.
- Rain remains immediate. No deferred deltas, search-result cache, sleeping puddles,
  dropped work, or asynchronous simulation was introduced.

## Verified so far

The current `check build` passed. Its checks include 6,936 pure barrier comparisons
and parsed-selector reload/collision checks. Dedicated fluid optimization passed all
96 differential BFS cases, contact, puddle, transfer, and pump optimization checks.
The full pump, rainfall, valve, fire, ice, and sensor server profiles also passed their
named completion markers. The added pump interval-transition check also passed,
including duplicate-call rejection and cadence recovery after assembly changes.

Latest dedicated fixture measurements (`build/performance/phase-abcd/fluid.log`):

| Fixture | Median ns per fixture tick | Median allocated bytes per fixture tick |
|---|---:|---:|
| Puddle | 19,288 | 6,632 |
| Drainage | 8,702 | 4,324 |
| Contact | 2,795 | 1,833 |
| Waterlogged | 38,612 | 10,585 |

These are focused harness measurements, not full server tick times. Earlier fixture
measurements used different warm-up histories; small differences are not conclusive.

## Copied-world measurements

The frozen source world is `build/performance/world-snapshot`; each run receives a
fresh copy. Baseline production sources are archived from commit `806129f` in
`build/performance/baseline-src`. Configuration, mods, options, shader packs, and resource
packs are copied from the benchmark baseline directory. The original save is untouched.

Required workload: seed -2541210193554019765, player 205/121/53, rain,
randomTickSpeed 100, target 20 TPS, render distance 17, simulation distance 10,
60 seconds warm-up and 180 seconds measurement. Allocation tracing runs separately.

Runs `baseline-a` and `candidate-a` are exploratory: the previous harness teleported
only once and did not verify camera stability. They must not establish acceptance.
The corrected harness fixes position and orientation and records camera/player state.
Run `baseline-1` is invalid because an earlier test resource filter corrupted textures.

Corrected rainfall measurements completed so far:

| Pair | Baseline TPS | Candidate TPS | Baseline p95 ms | Candidate p95 ms | Baseline MB/tick | Candidate MB/tick |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 17.84 | 20.00 | 72.83 | 55.36 | 52.37 | 23.06 |
| 2 | 17.34 | 20.00 | 78.21 | 56.67 | 52.20 | 17.28 |
| 3 | 18.46 | 20.00 | 71.30 | 51.92 | 44.75 | 23.39 |

MB here means 1,000,000 bytes. The first candidate screenshot was visually inspected.
All three primary pairs completed. Median p95 changed from 72.83 to 55.36 ms (24.0%
lower), while individual paired reductions ranged from 24.0% to 27.5%. Median allocation
changed from 52.20 to 23.06 MB/tick (55.8% lower). The 30% p95 target and p95 below 50 ms
are not met. No simulation or graphical settings were reduced.

One clear-weather pair also completed: both held 20 TPS with no ticks above 50 ms.
Baseline/candidate p95 was 28.68/17.90 ms and allocation was 16.28/7.86 MB per tick.
This supports improvement in clear weather, but is only one pair.

Hardware: AMD Ryzen 7 9800X3D, NVIDIA GeForce RTX 5080 (driver 616.64), Java 25.0.1,
maximum JVM heap 16,525,557,760 bytes. The copied Iris settings have `enableShaders=false`;
shaders were not disabled for the optimization. The player remained in the save's
original spectator mode. Each run records its JVM arguments, mods, and player/camera state.

## Targets and remaining limits

| Target | Observed result | Gate |
|---|---|---|
| 50% lower finite-water CPU per tick | Estimated 31.56 → 19.33 ms/tick, 38.8% lower | Not met |
| 75% lower search allocation | Sampled inclusive search weight 37.04 → 4.06 MB/tick, 89.0% lower | Met in the allocation pair |
| 30% lower tick p95, below 50 ms | Median 72.83 → 55.36 ms, 24.0% lower | Not met |
| 50% lower pump peak, equal throughput | Separated assemblies: 13.62 → 1.72 ms, 87.4% lower | Met in this fixture |
| No material clear-weather regression | Both 20 TPS; p95 28.68 → 17.90 ms | No regression observed in one pair |

The fluid CPU estimate scales the server thread's measured CPU time by the share of
its JFR execution samples containing `FiniteWaterPhysics.tick`, then takes the median
across the three runs. This is a sampling estimate, not an exact method timer. Current
processing outside that method is not included. Search allocation is inclusive of work
called by the search, and comes from a separate traced pair: 121.22 GB/3,273 baseline
ticks versus 14.62 GB/3,601 candidate ticks. It is a statistical allocation weight.

Total allocation fell 55.8% per tick and 49.0% per second (median 905.00 → 461.21 MB/s).
The smaller per-second improvement reflects the restored TPS. Median p99 fell
88.95 → 61.90 ms, 30.4% lower.

Across primary runs, the median GC pause p95 was 17.10 → 10.38 ms. Median recorded GC
CPU was 14.91 → 14.63 seconds per measurement window. Individual pauses and allocation
totals varied; do not treat a single run's maximum as a stable speedup.
In the allocation pair, after-GC heap changed 2.45 → 2.58 GB over the baseline window,
and 2.42 → 2.41 GB over the candidate window. Candidate maximum after-GC heap reached
3.75 GB. These are whole-process heap observations, not a retained-object ownership
analysis. Runtime caches have fixed bounds; this is not a long-duration leak test.

### Pump fixtures

Each fixture contains 64 three-by-three assemblies (576 block entities). After 600
warm-up simulation ticks, it measures three batches of 600 ticks. Source and rotor
water is replenished and outlets cleared outside timed callbacks once per 20-tick
cycle. No automatic fluid ticking runs inside this synchronous fixture. It measures
pump callback cost and steady transfer volume, not a complete world simulation.

Every baseline and candidate batch transferred exactly 138,240 units. The separated
fixture asserts this expected volume. Assemblies 160 blocks apart permit phase
staggering; nearby assemblies use the conservative original-cadence fallback.

| Fixture | Median baseline batch peak | Median candidate batch peak | Reduction |
|---|---:|---:|---:|
| Dense, five-block spacing | 13.10 ms | 8.42 ms | 35.7% |
| Separated, 160-block spacing | 13.62 ms | 1.72 ms | 87.4% |

Dense candidate batch peaks were 8.42, 33.40, and 6.64 ms. Separated baseline peaks
were 13.45, 37.25, and 13.62 ms; candidate peaks were 1.72, 2.24, and 1.43 ms. The
outliers remain in the raw results. Dense fixtures used normal generation; separated
fixtures used fresh flat worlds. Compare each fixture only with its matching baseline.

### Remaining work and proposed follow-up

- Exact real-world search counts, visited-cell counts, work-limit exits, and rain/scheduler
  request counts were not instrumented. The differential fixtures cover search limits,
  but JFR cannot supply those exact counts or exact allocated bytes per individual search.
- No long-duration retained-object analysis was performed. Cache bounds and short-run
  after-GC heap observations provide narrower evidence.
- The copied-world screenshot and camera records were checked. Pump sound-state tests
  passed, but listening to sounds and manually checking currents/interactions remain open.
- Equalization scratch-array reuse was not implemented. Rain eligibility caching and
  scheduling changes were omitted; Minecraft already deduplicates scheduled ticks.
- The remaining prominent sampled paths are world water mutations and puddle traversal.
  The 50% fluid-CPU and 30% p95 goals remain unmet. No search-result cache, sleeping puddle,
  work budget, rain batching, or asynchronous planning was added to close the gap.

A narrow next design would retain the already-read block state alongside each queued
puddle position for the lifetime of that single synchronous search, discarding it before
any world mutation. This trades another bounded array of references for fewer world reads.
It requires per-step differential validation and new candidate benchmarks. Broader reuse
across searches or ticks would require explicit dependency/invalidation tracking and
approval; it is not part of this implementation.

The deferred architectural option is a bounded cache of failed searches, with explicit
dependencies on every queried cell (including rejected candidates and potential drop
cells), collision geometry, loaded-chunk state, direction rotation, and config/tag
generations. Any relevant change must invalidate entries before callbacks can reuse
them. First measure failed-search repetition; abandon this option if invalidation cost
or hit rate makes it unhelpful. It must never postpone work or reuse an entry whose
dependencies cannot be validated. This option is proposed for approval, not implemented.

## Evidence

- `build/performance/phase-abcd/`: full build, all six dedicated profiles, fluid regression,
  and pump interval-transition logs.
- `build/performance/runs/{baseline,candidate}-fixed-{1,2,3}/`: individual metrics,
  environment, start/end player state, camera record, screenshot, JFR, and summary.
- `build/performance/runs/{baseline,candidate}-{clear,allocation}-1/`: diagnostic pairs.
- `build/performance/pumps/{baseline,candidate,baseline-separated,candidate-separated}/`:
  pump fixture logs, properties, and disposable worlds.
- `build/performance/paired-summary.json`: computed primary medians and reductions.
- `build/performance/{baseline,candidate}-source-sha256.json`: production source/resource
  file hashes. Baseline sources are the archived `806129f` tree; candidate changes are uncommitted.
- `build/performance/final-build.log`: final normal `check build` passed. The release
  JAR was inspected: only normal main/client entrypoints are present and no benchmark,
  server-test, or reference-search classes are packaged.

Release artifact: `build/libs/Immersive-Fluids-0.1.2.5-26.2.jar`.
SHA-256: `82d11e4bf728a8d07babebededaa2a23946ec954e40a30c1a28d7e79baff3c1c`.

## Reproduction

```powershell
rtk proxy .\gradlew.bat check build --no-daemon
rtk proxy .\gradlew.bat -PfluidOptimizationTest runServer --no-daemon
rtk proxy python tools/prepare-performance-run.py candidate-fixed-1
rtk proxy .\gradlew.bat -PfluidPerformanceTest=candidate-fixed-1 runClient --no-daemon
```

Run names must be fresh. Add `-PperformanceBaseline` for archived baseline production
sources, `-PperformanceClear` for clear weather, or `-PperformanceAllocations` for the
separate allocation diagnostic. Never compile another profile while a benchmark client
is running, because Gradle profiles share compiled output directories.

`rtk proxy pwsh -NoProfile -File tools/run-performance-pairs.ps1` runs three fresh timing
pairs in alternating order. Use `-Mode clear` or `-Mode allocation`, with
`-FirstPair 1 -LastPair 1`, for the diagnostic pairs. Existing names are refused.

Pump profiles use `-PpumpPerformanceTest=<name> runServer`; add `-PperformanceBaseline`
for the original source and `-PpumpSpacing=160` for separated assemblies. Prepare the
named directory under `build/performance/pumps` with matching server properties and
the existing accepted test-server EULA before launching.

JFR execution samples report inclusive stacks; parent and child percentages must not
be summed. Allocation samples are statistical weights, not exact object sizes. Thread
allocation counters provide per-tick and per-second totals. Reported tick durations span
Fabric server tick callbacks, not every part of the enclosing Minecraft tick loop.
