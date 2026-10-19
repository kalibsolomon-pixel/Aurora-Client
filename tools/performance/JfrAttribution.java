import jdk.jfr.consumer.*;
import java.nio.file.Path;
import java.util.*;

/** Streaming Java 21 JFR attribution: bounded summaries instead of a huge stack JSON export. */
public final class JfrAttribution {
    private static final Map<String, Long> counts = new TreeMap<>(), execution = new HashMap<>(),
            allocations = new HashMap<>(), auroraExecution = new HashMap<>(), auroraAllocation = new HashMap<>(),
            auroraTypes = new HashMap<>(), threads = new HashMap<>(), collectors = new HashMap<>();
    private static void add(Map<String, Long> map, String key, long value) { map.merge(key, value, Long::sum); }
    private static List<List<Object>> top(Map<String, Long> map) {
        return map.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20).map(e -> List.<Object>of(e.getKey(), e.getValue())).toList();
    }
    private static String json(Object value) {
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var pairs = new ArrayList<String>();
            map.forEach((k,v) -> pairs.add(json(k.toString()) + ":" + json(v)));
            return "{" + String.join(",", pairs) + "}";
        }
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(JfrAttribution::json).toList()) + "]";
        throw new IllegalArgumentException("Unsupported summary value");
    }
    public static void main(String[] args) throws Exception {
        double pauseTotal = 0, pauseMax = 0, heapMin = Double.POSITIVE_INFINITY, heapMax = 0;
        int pauses = 0;
        long allocationWeight = 0, allocationEvents = 0;
        java.time.Instant firstEvent = null, lastEvent = null;
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                var timestamp = event.getStartTime();
                if (firstEvent == null || timestamp.isBefore(firstEvent)) firstEvent = timestamp;
                if (lastEvent == null || timestamp.isAfter(lastEvent)) lastEvent = timestamp;
                String name = event.getEventType().getName();
                add(counts, name, 1);
                if (name.equals("jdk.DataLoss")) throw new IllegalArgumentException("JFR reports data loss");
                if (name.equals("jdk.GarbageCollection")) add(collectors, event.getString("name"), 1);
                if (name.equals("jdk.GCPhasePause")) {
                    double ms = event.getDuration().toNanos() / 1e6;
                    pauses++; pauseTotal += ms; pauseMax = Math.max(pauseMax, ms);
                }
                if (name.equals("jdk.GCHeapSummary")) {
                    double used = event.getLong("heapUsed") / 1048576.0;
                    heapMin = Math.min(heapMin, used); heapMax = Math.max(heapMax, used);
                }
                boolean allocation = name.equals("jdk.ObjectAllocationSample");
                if (!allocation && !name.equals("jdk.ExecutionSample")) continue;
                long weight = allocation ? event.getLong("weight") : 1;
                if (allocation) { allocationWeight += weight; allocationEvents++; }
                var counter = allocation ? allocations : execution;
                var aurora = allocation ? auroraAllocation : auroraExecution;
                RecordedThread thread = event.getThread(allocation ? "eventThread" : "sampledThread");
                if (thread != null) add(threads, thread.getJavaName(), 1);
                var trace = event.getStackTrace();
                if (trace == null) continue;
                boolean first = true, found = false;
                for (var frame : trace.getFrames()) {
                    var method = frame.getMethod();
                    String full = method.getType().getName() + "." + method.getName();
                    if (first) { add(counter, full, weight); first = false; }
                    if (!found && full.startsWith("com.aurora.client.")) {
                        add(aurora, full, weight); found = true;
                        if (allocation) add(auroraTypes, full + " -> " + event.getClass("objectClass").getName(), weight);
                    }
                }
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("events", counts); result.put("gcPauseCount", pauses);
        double observedSeconds = firstEvent == null ? 0 : java.time.Duration.between(firstEvent, lastEvent).toNanos() / 1e9;
        result.put("observedEventSpanSeconds", observedSeconds);
        result.put("sampledAllocationWeightBytes", allocationWeight);
        if (allocationEvents > 0 && observedSeconds > 0)
            result.put("sampledAllocationMiBPerSecond", allocationWeight / 1048576.0 / observedSeconds);
        result.put("garbageCollectors", collectors);
        result.put("gcPauseTotalMs", pauseTotal); result.put("gcPauseMaxMs", pauseMax);
        result.put("gcHeapUsedMiBRange", List.of(Double.isFinite(heapMin) ? heapMin : 0, heapMax));
        result.put("sampledThreads", top(threads)); result.put("sampledExecutionTop", top(execution));
        result.put("allocationWeightTop", top(allocations));
        result.put("nearestAuroraExecution", top(auroraExecution));
        result.put("nearestAuroraAllocationWeights", top(auroraAllocation));
        result.put("nearestAuroraAllocationTypes", top(auroraTypes));
        result.put("sampledRegionKeyAllocationWeight", auroraTypes.getOrDefault(
                "com.aurora.client.worldmap.cache.RegionCache.peek -> java.lang.Long", 0L));
        result.put("limitations", List.of("Whole recording includes setup/startup/save", "Statistical samples and weights; not exact CPU/allocated bytes", "Nearest Aurora caller includes downstream vanilla work"));
        System.out.println(json(result));
    }
}
