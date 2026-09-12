# Shader compatibility

## Setup

- Minecraft 26.2, Iris 1.11.2, Sodium 0.9.1.
- Complementary Unbound r5.9; optionally Euphoria Patches 1.10.0 for that version.
- Shader settings: **RP Support → Integrated PBR+** (`RP_MODE=1`).
- To cast pump/valve shadows, set **Entity Shadows → Full** (`ENTITY_SHADOW=2`). Complementary's
  Off and Regular Entities settings disable block-entity shadows, including vanilla chests and machinery.
- Install only the mod plus the shader stack. No external PBR resource pack or edited shader archive is required.

Iris is optional and is never bundled. Shader integration is client-only. Dedicated servers, vanilla
clients, and shader-disabled clients do not require the optional classes. Physics and registry identities
remain unchanged; finite water is not added to vanilla's water tag.

## Materials and rendering

Finite water and finite ice inherit the active shader's corresponding vanilla material IDs. Frozen
waterlogged ice quads keep their own ice material, independently of the original host. Sodium's fluid
renderer clips finite water above ice/snow, matching the vanilla rendering hook. Camera water recognition
is restricted to rendering with the supported shader pipeline.

Terrain and item quads select materials from their existing vanilla sprites. This preserves iron/copper
sensor surfaces, the assembler's metal/crystal/concrete surfaces, and wool/honeycomb insulation materials.
Buckets inherit the shader's bucket item mapping. Explicit shader mappings for mod content take precedence.

Animated pumps and valves use atlas-backed iron textures. Repeating housing UVs are split at atlas tile
boundaries. Iris carries block-entity material identity with its deferred submissions. The mod adds an iron
dispatch branch to the shader's block-entity material handler and an ice response to its item handler.
Machinery gloss is calculated from the iron texture before applying vertex colors, so dark housings
retain the iron response alongside pale blades. Valve UVs are converted directly into the iron sprite;
fluent vertex calls must not bypass Minecraft's sprite wrapper.
Both edits happen in Iris's in-memory include graph before preprocessing; no shader files are changed.
Ordinary shadow geometry is retained; no fake light sources are added.

Compatibility uses separate source signatures for Complementary r5.9 and Euphoria 1.10.0. All source
checks and reserved dispatch-ID collision checks must succeed before either source edit is applied.
The private dispatch IDs (29990–29992) are not hardcoded vanilla shader material IDs. Actual material
IDs come from the currently loaded pack. Unsupported revisions fall back to ordinary rendering with a
diagnostic. Shader/resource reloads recreate material maps and atlas lookups; no sprite coordinates are cached.

## Automated checks

```powershell
.\gradlew.bat check build --no-daemon
# Validate an extracted, unmodified shader fixture (path must point at its shaders directory):
.\gradlew.bat shaderSelfTest '-PshaderPack=build/shader-inspect/complementary/shaders' --no-daemon
.\gradlew.bat shaderSelfTest '-PshaderPack=build/shader-inspect/euphoria/shaders' --no-daemon
```

`check` includes atlas repeat/area/winding checks and rejection of unsupported shader sources, alongside
the existing ice, pump, valve, and physics regressions. Supplied shader fixtures additionally exercise both
source edits together and rejection/recovery across changed source versions. Fixtures are not bundled.

The opt-in `-PshaderTest=<profile> runClient` probe uses `build/shader-test-client/<profile>`, with its own
mods, configuration, and disposable world. Profiles are `vanilla`, `sodium`, `iris`, `complementary`, and
`euphoria`; put the corresponding runtime jars/shader packs in that directory first. Only the last two
require an active shader pipeline. The probe checks real Mixin loading and material registry aliases,
creates a world with mod blocks and a held mixed-material item, renders it, then exits. Shader profiles
also disable/re-enable shaders, reload resources, and check active atlas materials and host/ice separation.
Nested and exceptional item emissions verify material-context restoration. Its entrypoint
is excluded from normal builds. Success markers are `SHADER_CLIENT_STARTUP_PASS`,
`SHADER_MATERIAL_REGISTRY_PASS` (Iris profiles), `SHADER_ACTIVE_SURFACES_PASS` (active shaders),
and `SHADER_CLIENT_WORLD_RENDER_PASS`.

## Recorded runtime checks — 2026-09-10

Minecraft 26.2, Fabric Loader 0.19.3, Iris 1.11.2, Sodium 0.9.1; Windows, NVIDIA RTX 5080.

| Client configuration | Startup and world rendering | Toggle and resource reload |
| --- | --- | --- |
| No Iris or Sodium | Passed | Not exercised |
| Sodium 0.9.1 alone | Passed | Not exercised |
| Iris 1.11.2, shaders disabled | Passed | Not exercised |
| Complementary Unbound r5.9, Integrated PBR+ | Passed | Passed |
| Euphoria Patches 1.10.0 on r5.9, Integrated PBR+ | Passed | Passed |

Logs are generated under `build/shader-test-client/<profile>/logs/latest.log`.
The dedicated server without Iris also passed `-PiceTest runServer` with `FINITE_ICE_SERVER_TEST_PASS`.
`check build` passed, as did source-fixture checks for both supported packs. The normal release jar
was inspected: it contains neither probe/server-test classes nor bundled Iris/Sodium classes.
These are isolated development clients, not an exhaustive test of the normal instance's additional mods.
The original world-load crash (`@Shadow field blockId was not located`) is covered by loading the real
Sodium renderer and rendering a world. The hook now adjusts encoded quad data after Iris writes it,
without shadowing a field supplied by another Mixin. Item mapping changes copy Iris's immutable map
before adding entries, preventing shader fallback during pack loading.

The machinery vertex probe checks actual Iris buffer output for valid normals and iron-sprite UV bounds
at sizes 1–3 in all six directions. Shadow-enabled follow-up runs use `ENTITY_SHADOW=2` in isolated
profiles. The shadow submission regression requires this setting and checks that both controller
renderers submit geometry while Iris's shadow pass is active (`MACHINERY_SHADOW_SUBMISSION_PASS`).
The Complementary follow-up passed with 605 submissions per machine; a side-view screenshot also
showed cast shadows. The development instance's saved setting was subsequently changed from Off to
Full to address missing pump/valve shadows and light passing through the valve panels and frame.
Its previous settings are backed up under `build/shadow-debug/ComplementaryUnbound_r5.9.zip.txt.before`.

## Manual acceptance checklist

Automated startup, compilation, and scene rendering do not establish visual parity. Compare against
vanilla material blocks with the same shader settings:

1. Standalone and waterlogged finite water at levels 1–8: look from above, below, and underwater;
   verify reflections, transparency, surface motion, and the underwater transition.
2. Full/layered ice, offset frozen plants, slabs, chests, and snow with ice/water above it: check dry,
   partial, and full states, adjacent heights, internal faces, and host materials.
3. Pump/valve sizes 1–3 in all six directions: inspect moving/open/closed parts, shadows, tiling,
   and multiblock visibility. Check muted pump variants as well.
4. Assemblers (including muted), both sensor modes, buckets, ice, compressed wool variants, and
   insulator shards: inspect placement where applicable, held items in both hands, dropped items,
   frames, and inventory icons. Metal, copper, wool, and crystal must remain distinct.
5. Repeat in daylight, darkness, rain, and with neighboring vanilla blocks. Reload resources, disable
   and re-enable shaders, and switch between Complementary and Euphoria; look for material leakage,
   missing geometry, compilation errors, or stale textures.
6. Compare a machinery-heavy scene with the previous build at identical settings; record frame time
   and any visible regression. Performance and visual parity require this final in-game check.

No compatibility claim is made for untested shader packs or future Iris/Sodium/shader versions.

## Machinery mesh optimization — 2026-09-10

Pump housing normals, sprite-local UVs, and atlas clipping are prepared once for the 12 size/connection
variants. Valve parts are transformed into one mesh and one custom submission per controller.
Open/closed meshes are shared by size; each render state retains only its latest exact moving mesh.
Deferred callbacks capture the immutable mesh, sprite, and light rather than mutable animation state.
Neither detail nor shadow geometry was reduced. Atlas coordinates remain resolved at emission.

A temporary baseline comparison exercised the original and optimized custom emitters through Iris's
real BufferBuilder on the render thread. All 72 pump size/connection/direction combinations and 216
valve size/progress/direction combinations preserved vertex count, positions (within 0.000002 blocks),
UVs, colors, and packed normals (within one quantization unit). It passed before and after a resource
reload. The original renderer sources and comparison probe are retained locally under
build/render-baseline, outside release source sets.

Measured after resource reload with Euphoria 1.10.0 / Complementary r5.9, size 3, south-facing:
median of five batches of 300 calls after 150 warmup calls, comparing both versions in the same client.

| Custom rendering workload | Original CPU time | Cached CPU time | Original Java allocation | Cached Java allocation |
| --- | ---: | ---: | ---: | ---: |
| Pump housing submission/emission | 135.711 us | 43.328 us | 578,280 B | 4,832 B |
| Stationary valve submission/emission | 191.317 us | 91.504 us | 282,568 B | 1,032 B |
| Moving valve, two submissions at the same progress | 397.259 us | 241.339 us | 565,136 B | 475,556 B |

The moving workload advances exact fractional progress each iteration and includes mesh preparation.
These are isolated renderer CPU measurements, not whole-frame FPS or GPU measurements. The proxy
executes custom submissions immediately through real Iris buffers and ignores pump model-part
submissions, whose implementation is unchanged. Per-submission buffer creation is included, so these
figures must not be read as the complete game's batching cost. Java allocation excludes native buffers.
Evidence: build/render-euphoria.log (MACHINERY_BASELINE_VERTEX_PARITY_PASS and MACHINERY_RENDER_BENCH).
A same-camera Spark comparison in the user's machinery-heavy world and manual appearance acceptance
remain necessary to measure the actual scene improvement.

The permanent machineryMeshSelfTest checks surface area/winding, UV bounds, normals, colors, all
pump cache keys, valve endpoints/animation phases/fractional progress, exact-progress invalidation,
size changes, and shared endpoints. The opt-in machinery vertex probe now checks all these valve
stages and pump connection variants in all directions, and requires exactly one valve submission.

Final optimized-client checks passed for vanilla, Sodium alone, Iris with shaders disabled,
Complementary, and Euphoria (build/render-final-<profile>.log). Both active shader profiles passed
resource/shader reloads, 955,392 inspected vertices per probe invocation, and real pump/valve shadow
submissions. These automated checks establish geometry/material compatibility, not subjective visual
acceptance or a whole-scene Spark frame-time result.

## Pump tessellation follow-up — 2026-09-11

The pump's flat exterior and outer square cap rims are merged without reducing the 64-segment circular
opening. Housing quad counts are now 340 for a standalone stage, 208 for one connected end, and 76 for
two connected ends (previously 640, 448, and 256). The ordinary cached emitter is retained.

The proposed Sodium 0.9.1 / Iris 1.11.2 bulk writer was implemented and rejected by the correctness gate.
Isolated ordinary/bulk buffers matched across 288 size/direction/animation cases, including shader
attributes. However, an ordinary → bulk → ordinary sequence in one actual Iris buffer changed the
preceding quad's mc_midTexCoord at vertex 1356 from 0.74990237 to 0.74799806. This reproduces a buffer
boundary problem that an isolated-emitter benchmark would miss. No bulk path or extra optional-mod
dependency is shipped, and no performance claim is made for that rejected path.
The failed probe and prototype sources are retained locally under build/emission-baseline;
euphoria-benchmark.log records the failure and source line.

PUMP_SURFACE_EQUIVALENCE_PASS checks both directions of sampled surface coverage against the original
tessellation, area by color, face orientation, and planar UV mapping for every size/connection state.
The geometry self-test also asserts the new quad counts. The headless geometry preview was inspected;
manual shader appearance and a same-camera Spark comparison in the user's world remain outstanding.

Geometry-only measurements, 2026-09-11: size-3 standalone housing, actual Iris buffers on the render
thread, fresh render states, 300 warmup iterations followed by five batches of 400 iterations.
The table reports the median batch time. One-pass and two-pass workloads use identical geometry,
light, pose, and buffer-allocation policy before/after; pump rotor model submissions are excluded.

| Shader | Passes | Before | Merged | CPU reduction | Java bytes before/after |
| --- | ---: | ---: | ---: | ---: | ---: |
| Complementary r5.9 | 1 | 43.890 us | 22.116 us | 49.6% | 816 / 816 |
| Complementary r5.9 | 2 | 84.902 us | 42.681 us | 49.7% | 776 / 776 |
| Euphoria 1.10.0 | 1 | 42.168 us | 24.792 us | 41.2% | 816 / 816 |
| Euphoria 1.10.0 | 2 | 89.991 us | 51.034 us | 43.3% | 776 / 776 |

Stationary/moving valves were included as controls with exact fractional progress and fresh states;
their production renderer is unchanged, and no valve improvement is claimed for this follow-up.
Java allocation measurements exclude native buffers. These are isolated emission workloads, not
whole-frame FPS or the user's Spark scene. Logs: build/emission-baseline/final-complementary.log and
final-euphoria.log. The rejected bulk path was not benchmarked after failing the mixed-buffer gate.

Final retained-change validation passed: check build, vanilla, Sodium alone, Iris with shaders disabled,
Complementary, and Euphoria. Both active shader profiles passed actual pump/valve shadow submissions,
shader toggles, and resource reloads. The release JAR was checked for excluded benchmark/probe classes,
the rejected emitter, and bundled Iris/Sodium classes; RELEASE_MACHINERY_PACKAGE_PASS.

## Validated bulk-buffer boundary fix — 2026-09-11

This follow-up revisits the rejected bulk prototype above. Before each of our bulk batches, an
Iris-version-gated BufferBuilder invoker calls endLastVertex. This completes the preceding ordinary
quad's extended data, or consumes Iris's skip-finalization flag after a preceding bulk batch. The
hook is called only by our machinery emitter; it does not alter other mods' push methods.

Eligibility is restricted to exact BufferBuilder targets, complete quad boundaries, supported
intrinsics, Sodium 0.9.1, and Iris 1.11.2. Other configurations and wrapped consumers use the ordinary
emitter. Batches remain limited to 256 vertices with scoped temporary memory. Iris's own serializer
still computes extended shader attributes and material IDs. Geometry, textures, and shadows are
unchanged by this follow-up.

The permanent opt-in MACHINERY_BULK_PARITY_PASS probe compares all vertex attributes for 288 machine
variants, ordinary/bulk/ordinary transitions, consecutive bulk emissions, bulk/ordinary/bulk
transitions, changing material IDs, non-uniform transforms, wrapped consumers, empty input,
incomplete input, partial destination quads, and non-quad destinations. It passed before and after
resource reload with Euphoria; the original mixed-buffer midpoint-UV failure is covered explicitly.

Both Complementary r5.9 and Euphoria 1.10.0 passed the expanded parity probe before and after reload,
including actual shadow submissions. The bulk path is retained after the following warmed-up
measurements (median of five batches of 400 after 300 warmup iterations). Each operation uses fresh
render states; moving valves sample identical fractional progress sequences. Times include mesh
preparation and buffer allocation but exclude pump rotor model submissions.

| Shader | Machinery | Passes | Ordinary us | Bulk us | Java bytes ordinary / bulk |
| --- | --- | ---: | ---: | ---: | ---: |
| Complementary | Pump | 1 | 23.317 | 20.146 | 784 / 784 |
| Complementary | Pump | 2 | 46.395 | 41.676 | 836 / 776 |
| Complementary | Stationary valve | 1 | 93.717 | 78.204 | 910 / 864 |
| Complementary | Stationary valve | 2 | 176.334 | 144.212 | 1240 / 1240 |
| Complementary | Moving valve | 1 | 149.055 | 129.980 | 472804 / 472780 |
| Complementary | Moving valve | 2 | 256.004 | 242.768 | 473060 / 473060 |
| Euphoria | Pump | 1 | 24.146 | 21.860 | 784 / 784 |
| Euphoria | Pump | 2 | 48.590 | 41.536 | 837 / 776 |
| Euphoria | Stationary valve | 1 | 116.863 | 98.836 | 894 / 864 |
| Euphoria | Stationary valve | 2 | 194.230 | 174.112 | 1240 / 1240 |
| Euphoria | Moving valve | 1 | 172.452 | 152.718 | 472804 / 472780 |
| Euphoria | Moving valve | 2 | 242.570 | 236.476 | 473060 / 473060 |

This is an additional 9.5-14.5% pump and 2.5-18.2% valve CPU reduction in these isolated workloads,
not whole-frame gains. Java allocations are essentially unchanged and exclude native memory.
Logs and the temporary benchmark source are retained locally under build/bulk-boundary. The benchmark
is excluded from source sets after measurement; the focused parity regression remains opt-in.
All five client profiles passed: vanilla, Sodium alone, Iris with shaders disabled, Complementary,
and Euphoria. Active shader profiles exercised resource reloads, shader toggles, and shadow passes.
Subjective shader appearance and same-camera Spark comparison in the user's original scene still
require in-game acceptance.

Final check build passed after benchmark removal. Release JAR inspection confirmed the intended emitter
and boundary invoker, with no probe, benchmark, self-test, or bundled Sodium/Iris classes:
RELEASE_MACHINERY_PACKAGE_PASS.

## Valve internal-face removal — 2026-09-12

Valve mesh preparation now omits a complete box face only when a single other box with the same yaw
covers its full extent and extends beyond its outward side. Partial overlaps, differently rotated
parts, and exposed coplanar faces remain. Fully enclosed parts skip vertex preparation entirely.
The check uses the current interpolated geometry; endpoint sharing and the one-mesh animation cache
are unchanged. Collision geometry is untouched. All retained positions, UVs, normals, colors, winding,
texture density, materials, and per-quad shader data are unchanged; shadow submissions remain enabled.

| Size | Open quads, before → after | Closed quads, before → after |
| --- | ---: | ---: |
| 1 | 680 → 432 | 680 → 524 |
| 2 | 940 → 516 | 972 → 792 |
| 3 | 1192 → 680 | 1288 → 1056 |

`ValveSurfaceSelfTest` checks 861 states across sizes 1–3, half-tick animation samples, and fractional
samples around stage boundaries. Retained vertices match the original tessellation exactly. An
independent transformed-box oracle checks that every omitted quad lies inside one covering box on
its outward side. Additional cases cover self-occlusion, enclosing decorative parts, touching boxes,
partial overlaps, and duplicate exterior faces. Existing mesh checks still cover UV bounds, winding,
normals, cache reuse and invalidation; the real Iris probe covers all six renderer orientations.

Before/after benchmarks use the original implementation archived under build/valve-face-reduction,
the same size-3 geometry, fresh caches for every operation, production emission through actual
client buffers, and one/two passes. Moving progress samples the entire animation. Each result is
the median of five 2000-operation batches after 10000 warmup operations, alternating implementation
order between batches. Times include preparation and buffer allocation; they are not whole-frame
measurements. Cheap dimension and overlap checks reject irrelevant boxes before full coverage checks.

| Profile | State | Passes | Before us | After us | CPU reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| vanilla | open | 1 | 43.595 | 25.107 | 42.4% |
| vanilla | closed | 1 | 48.563 | 40.629 | 16.3% |
| vanilla | moving | 1 | 110.339 | 97.308 | 11.8% |
| vanilla | open | 2 | 90.891 | 52.754 | 42.0% |
| vanilla | closed | 2 | 99.957 | 82.207 | 17.8% |
| vanilla | moving | 2 | 163.077 | 131.805 | 19.2% |
| sodium | open | 1 | 39.299 | 23.486 | 40.2% |
| sodium | closed | 1 | 44.535 | 35.636 | 20.0% |
| sodium | moving | 1 | 104.433 | 96.589 | 7.5% |
| sodium | open | 2 | 75.997 | 45.524 | 40.1% |
| sodium | closed | 2 | 83.377 | 69.317 | 16.9% |
| sodium | moving | 2 | 146.576 | 131.436 | 10.3% |
| iris | open | 1 | 21.082 | 12.544 | 40.5% |
| iris | closed | 1 | 21.956 | 19.848 | 9.6% |
| iris | moving | 1 | 89.583 | 85.857 | 4.2% |
| iris | open | 2 | 37.542 | 20.992 | 44.1% |
| iris | closed | 2 | 38.671 | 31.981 | 17.3% |
| iris | moving | 2 | 111.507 | 96.315 | 13.6% |
| complementary | open | 1 | 78.649 | 40.501 | 48.5% |
| complementary | closed | 1 | 94.219 | 64.994 | 31.0% |
| complementary | moving | 1 | 150.687 | 114.789 | 23.8% |
| complementary | open | 2 | 154.395 | 81.896 | 47.0% |
| complementary | closed | 2 | 169.595 | 131.961 | 22.2% |
| complementary | moving | 2 | 217.326 | 168.042 | 22.7% |
| euphoria | open | 1 | 80.718 | 40.312 | 50.1% |
| euphoria | closed | 1 | 86.269 | 64.207 | 25.6% |
| euphoria | moving | 1 | 140.021 | 122.155 | 12.8% |
| euphoria | open | 2 | 151.410 | 78.948 | 47.9% |
| euphoria | closed | 2 | 156.288 | 122.983 | 21.3% |
| euphoria | moving | 2 | 224.858 | 170.660 | 24.1% |

Moving Java allocations fell by 29-42% across these profiles. Shader-enabled moving workloads fell
from about 474 KB/operation to 335 KB; Sodium and shader-disabled Iris fell from about 576 KB to
335 KB. The vanilla path also allocates during ordinary vertex emission and fell from 713/830 KB
(one/two passes) to 416/498 KB. Native allocations are excluded. Full timing and allocation records
are in build/valve-face-reduction/final-benchmarks.csv alongside the raw logs and benchmark source.
The temporary benchmark classes and calls were removed after measurement; the surface regression remains.

All five final client profiles passed. Both active shader profiles passed resource reloads, toggles,
bulk parity across all six orientations, and actual shadow submissions. The fixed-camera
Complementary before/after screenshots show no obvious exterior regression; acceptance and a
same-camera Spark comparison in the user's machinery-heavy scene remain manual checks.

Final check build passed after removing benchmark instrumentation. Release JAR inspection passed:
VALVE_RELEASE_JAR_PASS (no test entrypoints/classes or bundled optional Sodium/Iris dependencies).
