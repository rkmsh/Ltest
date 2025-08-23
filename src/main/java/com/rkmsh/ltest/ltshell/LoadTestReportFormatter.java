package com.rkmsh.ltest.ltshell;

import org.springframework.stereotype.Component;

import java.util.Map;

class LoadTestReportFormatter {

    static String format(LoadResult r) {
        StringBuilder sb = new StringBuilder();

        sb.append("\nSummary:\n");
        sb.append(String.format("  Total:        %.2fs\n", r.durationSeconds));
        sb.append(String.format("  Slowest:      %.3f ms\n", r.latencyStats.slowest));
        sb.append(String.format("  Fastest:      %.3f ms\n", r.latencyStats.fastest));
        sb.append(String.format("  Average:      %.3f ms\n", r.latencyStats.average));
        sb.append(String.format("  Requests/sec: %.2f\n", r.rps));
        sb.append(String.format("  Total data:   %,d bytes (read), %,d bytes (written)\n",
                r.bytesRead, r.bytesWritten));

        sb.append("\nLatency distribution:\n");
        sb.append(String.format("  50%% in %.3f ms\n", r.latencyStats.p50));
        sb.append(String.format("  90%% in %.3f ms\n", r.latencyStats.p90));
        sb.append(String.format("  95%% in %.3f ms\n", r.latencyStats.p95));
        sb.append(String.format("  99%% in %.3f ms\n", r.latencyStats.p99));

        if (!r.histogram.isEmpty()) {
            sb.append("\nLatency histogram (ms):\n");
            int maxCount = r.histogram.stream().mapToInt(h -> h.count).max().orElse(1);
            for (HistogramBin bin : r.histogram) {
                int bar = (int) Math.round((bin.count * 40.0) / maxCount);
                sb.append(String.format("  %7.3f - %7.3f  [%5d] %s\n",
                        bin.fromMs, bin.toMs, bin.count, "#".repeat(Math.max(0, bar))));
            }
        }

        sb.append("\nStatus code distribution:\n");
        if (r.statusDist.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<Integer,Integer> e : r.statusDist.entrySet()) {
                sb.append(String.format("  [%d] %d responses\n", e.getKey(), e.getValue()));
            }
        }

        sb.append("\nError distribution:\n");
        if (r.errorDist.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String,Integer> e : r.errorDist.entrySet()) {
                sb.append(String.format("  %s -> %d\n", e.getKey(), e.getValue()));
            }
        }

        sb.append('\n');
        return sb.toString();
    }
}
