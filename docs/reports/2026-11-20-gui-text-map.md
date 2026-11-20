# GUI allocations and world map I/O

The baseline is Aurora commit `dd3d3812`, which already includes the first
reference-instance performance pass. The changes affect two existing Minecraft
GUI optimizations and Aurora's asynchronous world map storage.

## World map regions

A 512 × 512 region has 1,024 capture timestamps and 262,144 pixels. The worker
now transfers them through gzip in 64 KiB blocks using big-endian buffer views.
The reader still accepts the older optional cave layer and checks for truncated
or trailing data. The writer still replaces the region atomically where the
filesystem supports it.

The benchmark loaded a real 140,162-byte compressed region, saved it to a
second path, reloaded it, and compared every timestamp and pixel. Each JVM ran
12 warm-ups and 41 measured round trips through the public asynchronous API.
Both writers produced the same compressed file as the original. Its SHA-256 was
`30c1e4b71e4b55ebb0e25a5d724319e5053ab35f0e5f4198ffbc497f9a55f12e`.
These timings cover the map worker when explored data is read or saved.
Each line ran baseline, candidate, candidate, baseline; medians of the two
runs per side, p95 in brackets:

| Line | Baseline read | New read | Baseline write | New write |
|---|---:|---:|---:|---:|
| 26.2 | 10.1–10.5 ms (13) | 6.2–6.5 ms (8) | 31.3–31.7 ms (35) | 27.5–28.2 ms (29–30) |
| 26.3 | 10.4 ms (12–13) | 6.1 ms (8–9) | 31.3–31.5 ms (32–35) | 27.2–27.4 ms (28–34) |

Reads are about 40% faster and writes about 12% faster. The 26.1.2 run was
noisy (base 11.6–17.9 ms read) but ordered the same way.

## Minecraft GUI

On the 26.2 reference stack, 132 of 238 layer intersection queries in a sampled
HUD frame examined a layer with one rectangle. The layer now answers those
queries from the union alone. It keeps the first two immutable rectangles and
allocates a packed coordinate array when a third arrives. Text ordering reuses
a literal when a left-to-right run has one fragment. It allocates a builder
when fragments must join or formatting codes need decoding. Minecraft still
handles bidirectional text and decodes formatting and malformed surrogates.

The replay captured the input bounds and intersection answers from one frame
per screen. Each implementation had to reproduce every answer. Both used the
same prebuilt elements. Allocation was measured on the executing Java thread,
with the resulting lists escaping each frame.

| Frame | Appends | Queries | Baseline allocation | New allocation |
|---|---:|---:|---:|---:|
| In-world HUD | 73 | 238 | 5,640 B | 4,128 B |
| Settings | 108 | 333 | 8,080 B | 6,400 B |
| Particles detail | 106 | 381 | 8,760 B | 5,992 B |

Replay time per frame stayed within noise (HUD 1.29–1.36 µs against
1.36 µs on 26.2 and 26.3); the gain is allocation.

Text ordering was timed on 26.2 over 10,000 calls per sample, median of 31
samples after 35 warm-ups, in two alternating pairs. Both implementations
produced the same checksum over every visited index, style and code point.

| Input | Baseline | New |
|---|---:|---:|
| Plain label | 102–142 ns, 432 B | 35 ns, 24 B |
| Two styles | 146–149 ns, 784 B | 70–87 ns, 248–280 B |
| Section codes | 208–212 ns, 984 B | 198–207 ns, 824–840 B |
| CJK and emoji | 106–108 ns, 504 B | 40–46 ns, 56 B |
| Contains Hebrew (vanilla fallback) | 56 ns, 264 B | 13–15 ns, 32 B |

The fallback case is faster because the scan stops at the first right-to-left
character without building any runs.

## Startup compatibility

The loader skips all three GUI layering mixins when Polytone or Gnetum owns
layer placement. Modern UI and Caxton own text ordering. Dynamic FPS sets the
unfocused frame limit and skips draws below its tick-loop minimum. Aurora
yields its unfocused-cap hook. Its general frame pacer reads the resulting
native limit. Skipping a mixin leaves the user's preference available for a
later launch with a different mod set.

The dev-client fixtures used Windows, an RX 9070 XT, 854 × 480, GUI scale 2
and render distance 12. Fabric API and Aurora came from Loom. The reference
stacks came from the owner's Aurora Launcher instances, with Crash Assistant,
spark and Simple Voice Chat excluded where present. The 26.2 checks included
Sodium, ImmediatelyFast, BadOptimizations, MoreCulling, Particle Core,
FerriteCore and EMF/ETF. The 26.3 checks also included C2ME, Lithium, VMP,
ModernFix, ScalableLux and Sodium Extra. The OpenGL reference used Iris 1.11.7
without a shader pack. The native Vulkan fixture omitted Iris.
