package com.rkmsh.ltest.ltshell;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@Command(group = "Load Test")
public class LoadTestShellCommand {

    private final LoadTester loadTester;

    public LoadTestShellCommand(LoadTester loadTester) {
        this.loadTester = loadTester;
    }

    /*
    ltest => command to invoke
     */
    @Command(command = "ltest", description = "Run Load Test")
    public String run(
            @Option(longNames = "n", shortNames = 'n', required = true, description = "Number of requests") int totalRequests,
            @Option(longNames = "c", shortNames = 'c', defaultValue = "50", description = "Concurrency (workers)") int concurrency,
            @Option(longNames = "q", shortNames = 'q', defaultValue = "0", description = "QPS (0 = unlimited)") int qps,
            @Option(longNames = "m", shortNames = 'm', defaultValue = "GET", description = "HTTP method") String method,
            @Option(longNames = "d", shortNames = 'd', defaultValue = "", description = "Request body") String data,
            @Option(longNames = "H", shortNames = 'H', description = "Header(s) 'Key: Value'") List<String> headers,
            @Option(longNames = "timeout", defaultValue = "30", description = "Timeout seconds") int timeoutSeconds,
            @Option(longNames = "insecure", defaultValue = "false", description = "Skip TLS verification") boolean insecure,
            @Option(longNames = "http2", defaultValue = "true", description = "Use HTTP/2 when possible") boolean http2,
            @Option(longNames = "url", required = true, description = "Target URL") String url
    ) {
        try {
            LoadTester.Options opts = new LoadTester.Options();
            opts.totalRequests = totalRequests;
            opts.concurrency = concurrency;
            opts.qps = qps;
            opts.method = method;
            opts.body = data;
            opts.headers = headers;
            opts.timeoutSeconds = timeoutSeconds;
            opts.insecure = insecure;
            opts.http2 = http2;
            opts.uri = URI.create(url);

            LoadResult result = loadTester.run(opts);
            return LoadTestReportFormatter.format(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
