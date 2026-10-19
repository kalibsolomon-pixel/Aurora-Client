# Client runtime overhead — scoped allocation improvement

2026-10-19, performance initiative Phase C. The Frame Pacer remains original
PARK/500 following [C-PERF.2B rejection](CPERF2B_RESULTS.md).

Two retained P3-B.1a JFR exports were reviewed inside their existing 30-second
process-capture windows, not across startup. There were 4,272/4,099 allocation
samples with weighted totals 5.219/4.980 GB. Nearest Aurora callers included font
visitor paths (111.4/113.3 MB), Info HUD draw submission (44.8/44.8 MB), rounded
rectangles (39.5/34.0 MB), Reflex scheduling (17.8/37.8 MB), and
`WorldScope.sanitize` (14.6/18.4 MB). These are statistical weighted samples, not
an exact allocation ledger. Five-frame stacks often end inside vanilla drawing;
an Aurora caller does not make all downstream allocation avoidable. Camera
continuity runs remain ineligible for heap/performance comparisons.

The attribution review is preserved privately at
`run/performance-initiative/retained-allocation-review.json`, with window times,
source hashes and representative stacks. Source export SHA-256:
`5274b36bf4e59d960958845ac685f35877ae200811beab32f9c75d190d546ebd` and
`cc1c9afae8162ddcfc1900659993d8f4b050add1e7471a91705ee2f8b6b580c6`.
No new profiling observer was introduced into
C-PERF.2B windows.

## Change and validation

`WorldScope.current()` is used by playtime, throttling, waypoints, world map and
the Info HUD. It previously lowercased, compiled a regex, replaced characters,
trimmed and concatenated the same identity on every query. `WorldScopeKey` now
keeps one immutable entry, keyed by namespace, source value and default locale.
The regex is compiled once. Renaming, changing server, namespace or locale
recomputes immediately; a menu still returns `none`. It retains only strings and
locale, never a client/world/server object. Concurrent readers cannot observe a
partially updated entry. No persistence format, existing key, refresh cadence,
world scoping or rendering quality changes.

Focused tests compare original output across namespaces, punctuation, whitespace,
Unicode and Turkish casing, plus cache reuse and transition invalidation. Full
`test build --offline` passed **548 tests, zero failures/errors/skips**; test logs
remain isolated from original repository/game logs.

The explicit standalone diagnostic compiles the actual helper with managed
Java 21.0.7+6, warms 100,000 calls and measures 500,000 calls per fresh process.
Order legacy/cached/cached/legacy/legacy/cached, 512 MiB initial/max heap, same
stable source string and volatile output consumption. ThreadMXBean counts the
calling thread's allocated bytes. It is a hot-path diagnostic, not Minecraft.

| Path | ns/call, three process results | Allocated bytes/call |
| --- | --- | --- |
| Original expression | 245.095 / 244.934 / 241.618 | 1376.000 / 1375.437 / 1375.367 |
| Stable cached key | 5.765 / 5.856 / 5.810 | 0 / 0 / 0 |

[Machine-readable measurements](docs/performance-initiative/world-scope-results.json)
include source hashes. Compile `tools/performance/WorldScopeBenchmark.java` together
with `WorldScopeKey.java` using `javac --release 21 -d <fresh-output>`, then invoke
`com.aurora.client.util.WorldScopeBenchmark legacy` or `cached` with that classpath.
Raw fork output stays under `run/performance-initiative/world-scope-benchmark/`.

The confirmed improvement is removal of redundant allocation on stable identity
queries. The microdiagnostic cannot predict end-to-end CPU, GC or FPS gains;
recording weights cannot be subtracted from another process as an exact saving.
Live combined validation remains part of Phase I. Other leads do not justify a
renderer/font/Reflex rewrite: HUD strings already refresh at 10 Hz, font visitors
already cache segment styles, and downstream vanilla glyph work dominates some
stacks. Sodium's chunk work and Better Hitreg timing are unchanged. Vulkan and
Linux performance are untested by this Windows investigation.
