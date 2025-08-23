# LTest – HTTP Load Tester

A lightweight **HTTP load testing tool** built with **Spring Boot 3.4 CLI commands**.  
Run HTTP requests directly from the command line with configurable concurrency, request count, and URL.  

This version runs **without interactive shell**, and logs and Spring Boot banner are disabled for a clean output.

---

## Features

- Run HTTP load tests via CLI: `java -jar target/ltest-0.0.1-SNAPSHOT.jar ltest --n 50 --c 5 --url https://httpbin.org/get`  
- Configurable: total requests, concurrency, URL, HTTP method, headers, and more  
- Silent execution: no Spring Boot banner or logs  
- Fast and lightweight, suitable for automation and CI/CD pipelines  

---

```bash
java -jar target/ltest-0.0.1-SNAPSHOT.jar ltest --help
NAME
       ltest - Run Load Test

SYNOPSIS
       ltest [--n int] --c int --q int --m String --d String --H List --timeout int --insecure boolean --http2 boolean [--url String] --help

OPTIONS
       --n or -n int
       Number of requests
       [Mandatory]

       --c or -c int
       Concurrency (workers)
       [Optional, default = 50]

       --q or -q int
       QPS (0 = unlimited)
       [Optional, default = 0]

       --m or -m String
       HTTP method
       [Optional, default = GET]

       --d or -d String
       Request body
       [Optional]

       --H or -H List
       Header(s) 'Key: Value'
       [Optional]

       --timeout int
       Timeout seconds
       [Optional, default = 30]

       --insecure boolean
       Skip TLS verification
       [Optional, default = false]

       --http2 boolean
       Use HTTP/2 when possible
       [Optional, default = true]

       --url String
       Target URL
       [Mandatory]

       --help or -h
       help for ltest
       [Optional]
```
