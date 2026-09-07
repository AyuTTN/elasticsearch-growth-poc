# Performance testing Elasticsearch with 100,000 products

This milestone intentionally does **not** use Testcontainers. It benchmarks the running local Elasticsearch 8.15.3 container with synthetic product data.

Script:

```text
scripts/benchmark-products.ps1
```

## Be precise: 100,000 products vs 100,000 searches

The default run means:

- **100,000 indexed product documents**
- 20 warm-up requests per scenario
- 200 measured requests per scenario
- 5 scenarios
- 1,000 measured requests total
- Requests are sequential (one client), not concurrent

This answers: “How quickly doessearch an index containing 100,000 this laptop  products?”

It does **not** answer: “Can the cluster survive 100,000 simultaneous users?” That requires a load generator with concurrency (k6, Gatling, JMeter, Rally) and a production-sized cluster.

You can set `-Iterations 100000`, but that still sends requests sequentially and is not equivalent to 100,000 concurrent requests.

## Why a separate index

The script creates:

```text
products-perf-<timestamp>
```

It does not touch the application's `products` index. Benchmark documents do not appear in the Thymeleaf catalog.

Every run gets a new index, making runs independent. The script never automatically deletes data. It prints the explicit cleanup command when finished.

## Run

Elasticsearch must be healthy:

```powershell
docker compose ps
```

Then:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\benchmark-products.ps1
```

Custom example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\benchmark-products.ps1 `
  -DocumentCount 100000 `
  -BatchSize 1000 `
  -WarmupIterations 20 `
  -Iterations 200
```

JSON reports go under:

```text
build/performance/
```

`build/` is gitignored because results depend on the machine and current load.

## Indexing methodology

### Explicit mapping

The benchmark index uses the same mapping shape as the app:

- `id`: keyword
- `name`, `description`: text with standard analyzer
- `category`: keyword
- `price`: double
- `stock`: integer

### Bulk API

Documents are sent as NDJSON batches to:

```http
POST /_bulk
```

Default batch size: 1,000.

One HTTP request per document would mostly measure network overhead. Bulk indexing is Elasticsearch's intended ingestion path.

### Refresh disabled during load

The index starts with:

```json
{ "refresh_interval": "-1" }
```

Elasticsearch therefore does not create searchable segments every second while 100,000 documents are arriving. After indexing:

1. Restore `refresh_interval` to `1s`
2. Call `POST /<index>/_refresh`
3. Verify `_count`

This improves load speed, but documents are not searchable during loading. Production bulk imports often do the same.

### One shard, zero replicas

The benchmark index uses:

```json
{
  "number_of_shards": 1,
  "number_of_replicas": 0
}
```

This matches a single-node laptop. Multiple shards on one node can add overhead without adding hardware parallelism. Zero replicas gives green health on one node.

Do not copy these settings blindly to production. Replicas provide availability and read capacity.

## Search scenarios

| Scenario | What it measures |
|----------|------------------|
| Exact document by `_id` | Direct known-ID retrieval; no BM25 |
| Full-text `headphones` | `multi_match` on `name^2` + description |
| Text + category | BM25 text in `must`, exact category in `filter` |
| Exact category | Keyword `term` filter matching many documents |
| Full-text miss | Analyzer/query path with zero hits |

Each search requests up to 20 hit IDs/scores and enables exact `track_total_hits`.

`track_total_hits = true` makes Elasticsearch count all matching documents. This costs more than accepting an approximate/lower-bound count. It represents a UI that needs an exact total.

## Warm-up

Before measuring each scenario, the script sends 20 warm-ups.

Why:

- Elasticsearch parses and optimizes the first query
- Lucene segment pages become hot in the OS page cache
- Docker networking and PowerShell/JIT paths warm up

The reported measurements are therefore **warm steady-state latency**, not first-request/cold-cache latency.

A cold-cache benchmark is harder: restarting a container does not guarantee the host OS page cache is empty. Never claim “cold disk” unless you actually control the OS cache.

## Metrics

| Metric | Meaning |
|--------|---------|
| Indexing docs/s | Documents divided by bulk-load wall-clock seconds |
| Client average | Mean wall-clock time seen by PowerShell |
| p50 | Median; half the requests were at or below this |
| p95 | 95% were at or below this; useful tail latency |
| p99 | 99% were at or below this; rare slow requests |
| Server average (`took`) | Milliseconds Elasticsearch reports inside search execution |
| Sequential requests/s | `1000 / client average`; one request at a time |

Average alone can hide spikes. p95 and p99 show the slow tail users notice.

## Why client time is much higher than Elasticsearch `took`

Elasticsearch's `took` measures work **inside the cluster**. It excludes:

- PowerShell preparing JSON
- Opening/reusing HTTP connections
- Docker port forwarding
- Response transfer
- JSON parsing in PowerShell

The script's client latency includes all of those. On the smoke run, ES often reported 1–3 ms while `Invoke-RestMethod` took roughly 47–50 ms. That does not mean ES lied; the timers cover different boundaries.

The Spring Java client uses a connection pool and is usually a better representation of application latency than repeatedly calling `Invoke-RestMethod`. This script is still useful for reproducible end-to-end laptop comparisons.

## Cache effects

Elasticsearch is disk-persistent, but warm Lucene files are served from the OS page cache. Filter bitsets and some request results can also be cached.

Consequences:

- Second run can be faster than first
- Repeated identical filters may benefit from cache
- Other applications can evict pages and cause noise
- The dataset (100k small docs) may fit almost entirely in RAM

A benchmark result without machine, heap, document count, query, and warm-up method is not meaningful.

## Laptop configuration to record

Current POC:

- Elasticsearch 8.15.3
- Docker Desktop / Windows 11
- ES heap: 512 MB (`-Xms512m -Xmx512m`)
- One ES node
- One primary shard, zero replicas (perf index)
- Security disabled
- Sequential PowerShell client

Record CPU model, physical RAM, and Docker resource limits if comparing machines.

## Smoke verification

A 1,000-document run completed successfully before the full benchmark:

- 1,000 docs in 0.28 seconds (~3,515 docs/s)
- ES `took` around 1–3 ms for searches
- End-to-end PowerShell latency around 47–51 ms for search scenarios

Five measured requests are too few for meaningful p95/p99. The smoke run only proves the workflow.

## Reading results correctly

Do not conclude “production supports X requests/s” from this script:

- One laptop, one node
- Tiny documents
- Warm cache
- No concurrent writers
- No TLS/auth
- Sequential requests
- Docker Desktop virtualization

Use it to:

- Compare query designs on the same machine
- Compare 10k vs 100k vs 1m documents
- See the cost of `track_total_hits`
- Compare `must` text vs keyword filter
- Catch a query becoming much slower

For production capacity testing, use Elastic Rally or Gatling/k6 with fixed concurrency and a representative data/query distribution.

## Cleanup

List performance indexes:

```powershell
Invoke-RestMethod "http://localhost:9200/_cat/indices/products-perf-*?v"
```

The benchmark prints a delete command for its exact index. Deletion is irreversible for that synthetic index:

```powershell
Invoke-RestMethod -Method Delete http://localhost:9200/<printed-index-name>
```

Do not delete the application's `products` or `orders` index.

## Results

Full run on 2026-09-07:

- Index: `products-perf-20260907-225910`
- Documents: **100,000**
- Bulk indexing: **14.14 seconds**
- Indexing throughput: **7,071.87 documents/second**
- Warmups: 20 per scenario
- Measurements: 200 sequential requests per scenario

Latency:

- Exact `_id`: average **2.06 ms**, p50 **1.88**, p95 **3.75**, p99 **4.55**, ~486 sequential req/s
- Full-text `headphones`: average **48.17 ms**, p50 **47.88**, p95 **51.70**, p99 **54.50**; ES `took` average **1.32 ms**
- Text + category: average **48.60 ms**, p50 **48.11**, p95 **52.40**, p99 **53.94**; ES `took` average **2.76 ms**
- Exact category: average **46.22 ms**, p50 **46.39**, p95 **48.69**, p99 **49.32**; ES `took` average **1.12 ms**
- Full-text miss: average **45.43 ms**, p50 **44.62**, p95 **48.54**, p99 **51.61**; ES `took` average **1.04 ms**

Raw JSON:

```text
build/performance/products-perf-20260907-225910.json
```

Interpretation: ES itself executed these searches in roughly **1–3 ms**. The much larger PowerShell client time is mostly outside ES. The only safe conclusion is this machine's warm sequential baseline under the configuration recorded above.
