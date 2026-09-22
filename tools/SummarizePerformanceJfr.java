import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;
import java.util.*;

/** java tools/SummarizePerformanceJfr.java path/to/world.jfr; streams events without a large JSON export. */
class SummarizePerformanceJfr {
    public static void main(String[] args) throws Exception {
        Map<String, Long> samples = new HashMap<>(), allocations = new HashMap<>();
        Map<String, Long> allocationMethods = new HashMap<>();
        long serverSamples = 0, gcCpuNanos = 0;
        long firstAfterGcHeap = -1, lastAfterGcHeap = -1, maxAfterGcHeap = -1;
        var pauses = new ArrayList<Long>();
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String type = event.getEventType().getName();
                if (type.equals("jdk.GCPhasePause")) pauses.add(event.getDuration().toNanos());
                if (type.equals("jdk.GCHeapSummary") && event.getString("when").equals("After GC")) {
                    lastAfterGcHeap = event.getLong("heapUsed");
                    if (firstAfterGcHeap < 0) firstAfterGcHeap = lastAfterGcHeap;
                    maxAfterGcHeap = Math.max(maxAfterGcHeap, lastAfterGcHeap);
                }
                if (type.equals("jdk.GCCPUTime")) {
                    gcCpuNanos += event.getDuration("userTime").toNanos() + event.getDuration("systemTime").toNanos();
                }
                if (type.equals("jdk.ExecutionSample") && event.getThread("sampledThread") != null
                        && event.getThread("sampledThread").getJavaName().equals("Server thread")) {
                    serverSamples++;
                    Set<String> names = new HashSet<>();
                    if (event.getStackTrace() != null) for (var frame : event.getStackTrace().getFrames()) {
                        var method = frame.getMethod();
                        String name = method.getType().getName();
                        if (name.startsWith("io.github.SirWashington.")) {
                            names.add(name.substring(name.lastIndexOf('.') + 1) + "." + method.getName());
                        }
                    }
                    names.forEach(name -> samples.merge(name, 1L, Long::sum));
                }
                if (type.equals("jdk.ObjectAllocationSample") && event.getStackTrace() != null) {
                    Set<String> methods = new HashSet<>();
                    for (var frame : event.getStackTrace().getFrames()) {
                        var method = frame.getMethod();
                        if (method.getType().getName().startsWith("io.github.SirWashington.")) {
                            methods.add(method.getType().getName() + "." + method.getName());
                        }
                    }
                    for (String method : methods) allocationMethods.merge(method, event.getLong("weight"), Long::sum);
                    for (var frame : event.getStackTrace().getFrames()) {
                        if (frame.getMethod().getType().getName().contains("FiniteWaterPhysics")) {
                            allocations.merge(event.getClass("objectClass").getName(), event.getLong("weight"), Long::sum);
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("server_execution_samples=" + serverSamples);
        print("inclusive_samples", samples);
        print("sampled_allocation_weight_bytes", allocations);
        print("inclusive_sampled_allocation_weight_bytes", allocationMethods);
        pauses.sort(Long::compare);
        System.out.println("gc_cpu_ms=" + gcCpuNanos / 1e6);
        System.out.println("after_gc_heap_first_bytes=" + firstAfterGcHeap);
        System.out.println("after_gc_heap_last_bytes=" + lastAfterGcHeap);
        System.out.println("after_gc_heap_max_bytes=" + maxAfterGcHeap);
        if (!pauses.isEmpty()) {
            System.out.println("gc_pause_p95_ms=" + pauses.get((int) Math.ceil(pauses.size() * .95) - 1) / 1e6);
            System.out.println("gc_pause_max_ms=" + pauses.getLast() / 1e6);
        }
    }

    private static void print(String label, Map<String, Long> values) {
        values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(25)
                .forEach(entry -> System.out.println(label + " " + entry.getKey() + "=" + entry.getValue()));
    }
}
