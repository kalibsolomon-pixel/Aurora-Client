# C-PERF.2B — bounded re-parking rejected

Collected 2026-10-18/19, Windows, source `66db439`. Production remains original
PARK with 500 us spin allowance. The developer prototype stays default-off for
traceability; it is not a recommended optimization.

## Decision and scope

The [predeclared protocol](CPERF2B_PROTOCOL.md) rejects repeated disjoint worse
deadline P95/P99 buckets. Both prescribed pairs fail that screen. Collection
therefore ended after R1/E2/E3/R4; prepared R5/E6 remain unused and preserved.
This is an early rejection, not a completed three-pair acceptance trial. There
were no invalid runs, replacements, parameter changes or discarded captures.

| Run | Path | Callback FPS | Callback P95 / P99 ms | Mean spin wall us | Deadline P95 us | Deadline P99 us | Deadline >=100 us |
| --- | --- | ---: | ---: | ---: | --- | --- | ---: |
| 1 | Reference | 239.849 | 4.497 / 4.737 | 1252.728 | [0,25) | [300,325) | 2.209% |
| 2 | Experimental | 239.897 | 4.449 / 4.698 | 739.699 | [250,275) | [475,500) | 8.208% |
| 3 | Experimental | 239.947 | 4.440 / 4.709 | 733.918 | [200,225) | [475,500) | 7.529% |
| 4 | Reference | 239.899 | 4.540 / 4.709 | 1302.371 | [25,50) | [375,400) | 2.976% |

R1→E2 mean spin fell 40.95%; R4→E3 fell 43.65%. Callback P95/P99 did not
regress by the 0.1 ms screen, and throughput stayed within 0.05%. Nevertheless,
deadline tails repeatedly worsened. Experimental >=100 us late rates exceeded
both references by 4.55–6.00 percentage points, versus 0.77 points reference
spread. Lower wall time spent spinning does not establish lower active CPU or
better frame quality. No automatic spin tuning is justified by these results or
the earlier rejected 250 us experiment.

## Population, stability and observer checks

All four warmups exceeded 60 seconds and confirmed FRAME_HEAD_GLOBAL/240/500,
focused, segment 1, no condition/owner/focus changes or discarded invocations.
All eight CSVs completed their automatic 60-second windows with positive timing,
matching counts/footer and coherent elapsed intervals. All eight process captures
completed 121 samples using explicit process creation identities. Keyboard-only
camera checkpoints and exact normally saved position/rotation matched the source.
Every process saved normally and ended with Gradle exit 0. No renderer or Reflex
failure was observed. Pre-freeze world evolution was not identical: saved world
times were 124596, 124597, 124802 and 124465; each measurement scene was frozen.

Candidate retries were 6991/21590 (32.38%) and 9347/23468 (39.83%). Every retry
request was exactly 1 ms, no invocation exceeded two calls, eligible/taken and
0/1/2-call populations reconciled, and no interrupted retry was observed.
Retry-limit exhaustion counts were 2 and 3. This observation does not replace
the existing interruption/deadline correctness tests.

| Run | >1.25T / >2T / >50ms callbacks | Maximum >2T in one second | Late entries / reanchors per 10k | Spin >=1ms / >=2ms |
| --- | --- | ---: | ---: | ---: |
| 1 | 7 / 3 / 0 | 2 | 0.466 / 1.864 | 63.21% / 15.38% |
| 2 | 11 / 3 / 0 | 2 | 1.390 / 1.390 | 34.65% / 0% |
| 3 | 12 / 2 / 0 | 1 | 0.426 / 1.278 | 30.36% / 0.004% |
| 4 | 3 / 2 / 0 | 1 | 0 / 1.875 | 67.35% / 15.38% |

Whole-process CPU (percent of one logical core) Off→On was 68.83→57.11,
57.06→73.72, 52.84→58.18 and 60.00→59.97. This inconsistent contextual metric
does not establish CPU savings. Primary RSS ranges were 2929.5–2934.6,
2918.0–2920.9, 2934.4–2936.9 and 2920.0–2921.9 MiB. Overlay GC counter deltas
were 47/38/25/33 ms; individual-frame causality is not established. Expanded
candidate diagnostic overhead remains unresolved. Off controls are retained,
not used to subtract an invented observer correction.

## Reproduction and remaining pacer review

Exact conditions are in the protocol and ignored `run/cperf2b-benchmark/inputs.json`:
Java 21.0.7+6, fixed 2 GiB initial/max heap, Minecraft 1.21.11, Sodium 0.8.14,
2560×1440, RD4/SD12, global 240, native Unlimited, VSync off. PARK diagnostics
bracket the Overlay/process windows with recorded manual gaps; they are not
sample-identical. Internal HUD cadence is not presentation or input-to-photon
latency. Histogram percentiles are rank buckets, never exact interpolated values.

`python -B tools/performance/cperf2b_review.py 1 2 3 4` rebuilds offline reviews.
[Machine-readable results](docs/performance-initiative/cperf2b-results.json) retain
histograms, counters, resource context and raw capture hashes. Ignored run folders
retain CSVs, exports, process samples, event timestamps, camera PNGs and logs;
`completed-run-manifest.json` inventories 789 completed-run files by SHA-256.
Preparation refuses overwrite and must not be rerun over this evidence.

The existing C-PERF.3 native/HYBRID correctness fixes and focus/menu/VSync/Reflex
ownership tests remain the supported behavior. Disabled diagnostics retain the
original allocation-free dispatch. No hooks, queued-task ordering, cap ownership,
manual settings or visual behavior changed in this phase. The 546-test full build
passed at the initial checkpoint. Windows 240 FPS frozen-scene evidence does not
establish other-cap, Linux, driver-wide or latency acceptance. Further scheduler
complexity has no demonstrated benefit here; proceed to attributable Client and
Launcher overhead instead of another parameter sweep.
