package com.rkmsh.ltest.ltshell;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;


class LoadResult {
    LoadTester.Options options;
    int totalRequests;
    int totalErrors;
    double durationSeconds;
    double rps;
    long bytesRead;
    long bytesWritten;
    Stats latencyStats;
    Map<Integer, Integer> statusDist;
    Map<String, Integer> errorDist;
    List<HistogramBin> histogram;
}

class Stats {
    double fastest, average, slowest, stdev;
    double p50, p90, p95, p99;
}

class HistogramBin {
    final double fromMs;
    final double toMs;
    final int count;
    HistogramBin(double fromMs, double toMs, int count) {
        this.fromMs = fromMs; this.toMs = toMs; this.count = count;
    }
}
