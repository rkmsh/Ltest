package com.rkmsh.ltest.ltshell;

import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Component
class LoadTester {

    static class Options {
        int totalRequests;
        int concurrency = 50;
        int qps = 0; // 0 = unlimited
        String method = "GET";
        String body = "";
        List<String> headers = List.of();
        int timeoutSeconds = 30;
        boolean insecure = false;
        boolean http2 = true;
        URI uri;
    }

    private static HttpClient buildClient(Options o) throws NoSuchAlgorithmException, KeyManagementException {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(o.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(o.http2 ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1);

        if (o.insecure) {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            b.sslContext(sc);
            b.sslParameters(new SSLParameters(){{
                setEndpointIdentificationAlgorithm(null); // disable hostname verification
            }});
        }

        return b.build();
    }

    LoadResult run(Options o) throws Exception {
        Objects.requireNonNull(o.uri, "url required");
        if (o.totalRequests <= 0) throw new IllegalArgumentException("n must be > 0");
        if (o.concurrency <= 0) throw new IllegalArgumentException("c must be > 0");
        if (o.qps < 0) throw new IllegalArgumentException("q must be >= 0");

        HttpClient client = buildClient(o);

        HttpRequest.Builder proto = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(o.timeoutSeconds))
                .uri(o.uri);

        // Method + body
        String m = o.method.toUpperCase(Locale.ROOT);
        if (List.of("POST","PUT","PATCH","DELETE").contains(m)) {
            proto.method(m, HttpRequest.BodyPublishers.ofString(o.body == null ? "" : o.body));
        } else {
            proto.method("GET", HttpRequest.BodyPublishers.noBody());
        }

        // Headers
        if (o.headers != null) {
            for (String h : o.headers) {
                int idx = h.indexOf(':');
                if (idx > 0) {
                    String k = h.substring(0, idx).trim();
                    String v = h.substring(idx + 1).trim();
                    proto.header(k, v);
                }
            }
        }

        byte[] bodyBytes = o.body == null ? new byte[0] : o.body.getBytes();
        long startWall = System.nanoTime();

        ExecutorService pool = Executors.newFixedThreadPool(o.concurrency);
        CountDownLatch latch = new CountDownLatch(o.totalRequests);
        List<Sample> samples = Collections.synchronizedList(new ArrayList<>(o.totalRequests));
        ConcurrentMap<Integer, AtomicInteger> statusDist = new ConcurrentHashMap<>();
        ConcurrentMap<String, AtomicInteger> errorDist = new ConcurrentHashMap<>();
        AtomicLong bytesRead = new AtomicLong(0);
        AtomicLong bytesWritten = new AtomicLong(bodyBytes.length * (long)o.totalRequests);

        // Simple global QPS pacing (token-less interval scheduler)
        final long intervalNanos = o.qps > 0 ? (long) (1_000_000_000.0 / o.qps) : 0L;
        final AtomicLong nextSlot = new AtomicLong(System.nanoTime());

        for (int i = 0; i < o.totalRequests; i++) {
            // rate limit
            if (intervalNanos > 0) {
                long slot = nextSlot.getAndAdd(intervalNanos);
                long wait = slot - System.nanoTime();
                if (wait > 0) LockSupport.parkNanos(wait);
            }

            pool.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    HttpRequest req = proto.build();
                    HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    long t1 = System.nanoTime();
                    samples.add(new Sample((t1 - t0), false));
                    bytesRead.addAndGet(resp.body() == null ? 0 : resp.body().length);
                    statusDist.computeIfAbsent(resp.statusCode(), k -> new AtomicInteger()).incrementAndGet();
                } catch (Exception ex) {
                    long t1 = System.nanoTime();
                    samples.add(new Sample((t1 - t0), true));
                    errorDist.computeIfAbsent(slim(ex), k -> new AtomicInteger()).incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdownNow();
        long endWall = System.nanoTime();

        return computeResult(o, samples, statusDist, errorDist, bytesRead.get(), bytesWritten.get(), startWall, endWall);
    }

    private static String slim(Exception ex) {
        String s = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            // keep first clause for grouping
            String msg = ex.getMessage().split("\\R", 2)[0];
            if (msg.length() > 100) msg = msg.substring(0, 100) + "...";
            return s + ": " + msg;
        }
        return s;
    }

    private LoadResult computeResult(Options o,
                                     List<Sample> samples,
                                     Map<Integer, AtomicInteger> statusDist,
                                     Map<String, AtomicInteger> errorDist,
                                     long bytesRead,
                                     long bytesWritten,
                                     long startWall,
                                     long endWall) {
        List<Long> lat = new ArrayList<>(samples.size());
        long errCount = 0;
        for (Sample s : samples) {
            lat.add(s.latencyNanos);
            if (s.error) errCount++;
        }
        Collections.sort(lat);
        double totalSec = (endWall - startWall) / 1_000_000_000.0;
        double rps = samples.size() / totalSec;

        Stats stats = new Stats();
        stats.fastest = nanosToMillis(lat.isEmpty() ? 0 : lat.get(0));
        stats.slowest = nanosToMillis(lat.isEmpty() ? 0 : lat.get(lat.size()-1));
        stats.average = nanosToMillis(avg(lat));
        stats.stdev = nanosToMillis(stddev(lat));
        stats.p50 = nanosToMillis(percentile(lat, 50));
        stats.p90 = nanosToMillis(percentile(lat, 90));
        stats.p95 = nanosToMillis(percentile(lat, 95));
        stats.p99 = nanosToMillis(percentile(lat, 99));

        LoadResult r = new LoadResult();
        r.options = o;
        r.totalRequests = samples.size();
        r.totalErrors = (int) errCount;
        r.durationSeconds = totalSec;
        r.rps = rps;
        r.bytesRead = bytesRead;
        r.bytesWritten = bytesWritten;
        r.latencyStats = stats;
        r.statusDist = new TreeMap<>();
        statusDist.forEach((k,v)-> r.statusDist.put(k, v.get()));
        r.errorDist = new TreeMap<>();
        errorDist.forEach((k,v)-> r.errorDist.put(k, v.get()));
        r.histogram = makeHistogram(lat);
        return r;
    }

    private static List<HistogramBin> makeHistogram(List<Long> sortedNanos) {
        if (sortedNanos.isEmpty()) return List.of();
        int bins = 10;
        long min = sortedNanos.get(0);
        long max = sortedNanos.get(sortedNanos.size()-1);
        if (max == min) {
            return List.of(new HistogramBin(nanosToMillis(min), nanosToMillis(max), sortedNanos.size()));
        }
        double step = (max - min) / (double) bins;
        int[] counts = new int[bins];
        for (long v : sortedNanos) {
            int idx = (int) Math.min(bins - 1, Math.floor((v - min) / step));
            counts[idx]++;
        }
        List<HistogramBin> out = new ArrayList<>();
        for (int i=0;i<bins;i++) {
            double a = nanosToMillis((long)(min + i*step));
            double b = nanosToMillis((long)(min + (i+1)*step));
            out.add(new HistogramBin(a, b, counts[i]));
        }
        return out;
    }

    private static double nanosToMillis(long n) { return n / 1_000_000.0; }
    private static long percentile(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) return 0L;
        double idx = (p/100.0) * (sortedNanos.size()-1);
        int lo = (int)Math.floor(idx);
        int hi = (int)Math.ceil(idx);
        if (lo == hi) return sortedNanos.get(lo);
        double frac = idx - lo;
        return (long)(sortedNanos.get(lo)*(1-frac) + sortedNanos.get(hi)*frac);
    }
    private static long avg(List<Long> sortedNanos) {
        if (sortedNanos.isEmpty()) return 0L;
        long s=0; for (long v: sortedNanos) s+=v; return s / sortedNanos.size();
    }
    private static long stddev(List<Long> sortedNanos) {
        if (sortedNanos.size() < 2) return 0L;
        double mean = avg(sortedNanos);
        double sum=0;
        for (long v: sortedNanos) {
            double d = v - mean;
            sum += d*d;
        }
        return (long)Math.sqrt(sum / (sortedNanos.size() - 1));
    }

    private static class Sample {
        final long latencyNanos;
        final boolean error;
        Sample(long latencyNanos, boolean error) { this.latencyNanos = latencyNanos; this.error = error; }
    }
}
