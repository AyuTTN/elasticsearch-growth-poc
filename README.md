# Elasticsearch Growth POC

A from-scratch learning project: **Spring Boot 3.5 + Gradle 9 + Elasticsearch 8.15 + Kibana**.

Built layer by layer on branch `cursor/learn-spring-es-gradle` so each tool has a reason. The older Maven branch (`cursor/spring-boot-es-kibana-poc-d372`) is a finished copy, not this learning path.

```text
Browser  :8080  →  Spring Boot (catalog, search, orders, stock rules)
                      ↓
                 Elasticsearch :9200   (indexes: products, orders)
                      ↑
You      :5601  →  Kibana (Discover, Dev Tools — not a shop UI)
```

---

## What we built

| Layer | What exists |
|-------|-------------|
| Spring Boot + Gradle | Browser UI (Thymeleaf). No Postman required. |
| Products | Create, list, full-text search + exact category filter. Stored in index `products`. |
| Orders | Place order, list/filter by email and status. Stored in index `orders`. Stock reduced in Java. |
| Docker Compose | Elasticsearch + Kibana, versions pinned to **8.15.3**, security off (laptop only). |
| Performance harness | PowerShell bulk-loads **100,000** synthetic products into a **separate** `products-perf-*` index and measures search latency. |

The catalog UI never uses the 100k benchmark index. Seeds and real orders stay in `products` / `orders`.

---

## What we learned

**Spring vs Elasticsearch vs Kibana**  
Three processes, three jobs. Boot is HTTP and business rules. ES stores JSON and runs search. Kibana is a human UI over the **same** cluster. Kibana does not “discover Spring entities”; Elasticsearch already has the index. Discover only shows `products` after you create a **data view**. Index Management lists whatever ES has.

**Why Elasticsearch (not Postgres CriteriaQuery)**  
Optional filters exist in SQL too. ES is for **analyzers** (split/lowercase text), **BM25** ranking (`_score`), and **boosts** (`name^2`). Specs answer “which rows?” Search answers “which docs, in what order?”

**Mapping**  
`Text` = analyzed, use `match` / `multi_match`. `Keyword` = exact, use `term`. Category and email are Keyword. Mixing them is the main footgun.

**Bool query**  
`must` + `multi_match` = scored text. `filter` + `term` = yes/no, no score, cache-friendly. `_score` is relevance for this query, not a percent and not stock/price.

**How ES stores data**  
Not Redis, not SQL heaps. Lucene **inverted index** on disk (Docker volume `es-data`). Speed comes from term lookup, segments, and OS **page cache** — not “everything only in RAM.”

**Orders**  
No joins. Line items are **copied** onto the order (denormalize). Stock is **not** a search query; two ES writes are **not** one ACID transaction.

**Gradle**  
This repo uses global `gradle` (9.7.1), not the Wrapper. `org.gradle.console=plain` so Cursor prints Spring logs instead of a stuck 80% bar.

**Kibana networking**  
Spring uses `localhost:9200`. Kibana in Docker uses `http://elasticsearch:9200`. `localhost` inside the Kibana container is Kibana itself.

Longer notes: [docs/](docs/) (`00`–`09`). Checklist: [docs/PROGRESS.md](docs/PROGRESS.md).

---

## Run the app

Needs: JDK 21, Gradle 9.7.1 on PATH, Docker Desktop.

```powershell
cd D:\Code\JVM\elasticsearch-growth-poc
docker compose up -d
docker compose ps
```

Wait until **elasticsearch** and **kibana** are `healthy` (Kibana is slower; `:9200` too early fails the same way).

```powershell
gradle bootRun
```

| URL | Use |
|-----|-----|
| http://localhost:8080/products | Catalog + search (`headphones`, category `Furniture`) |
| http://localhost:8080/orders | Place order; stock drops |
| http://localhost:9200 | Cluster JSON |
| http://localhost:5601 | Kibana — data view `products` **without** a time field; `orders` may use `createdAt` |

```powershell
docker compose down      # keep data
docker compose down -v   # wipe volumes
gradle test              # mocks; no Docker
```

If Cursor’s terminal has no `gradle`: `$env:Path = "C:\gradle\gradle-9.7.1\bin;$env:Path"` or restart Cursor after PATH changes.

---

## Performance result (2026-09-07)

Laptop: Windows 11, Docker Desktop, ES **8.15.3**, **512 MB** heap, **one node**. Script: `scripts/benchmark-products.ps1`.

| What | Result |
|------|--------|
| Documents | **100,000** synthetic products (`products-perf-20260907-225910`) |
| Bulk index | **14.14 s** → **~7,072 docs/s** |
| Method | 20 warm-ups + **200 sequential** requests per scenario (not 100k concurrent users) |

Search (warm). **Client** = PowerShell `Invoke-RestMethod`. **ES `took`** = time inside Elasticsearch.

| Scenario | Client avg | p95 | p99 | ES `took` avg |
|----------|------------|-----|-----|----------------|
| Get by `_id` | 2.06 ms | 3.75 ms | 4.55 ms | (no `took` on GET `_doc`) |
| Full-text `headphones` | 48.17 ms | 51.7 ms | 54.5 ms | **1.32 ms** |
| Text + category | 48.60 ms | 52.4 ms | 53.94 ms | **2.76 ms** |
| Category `term` only | 46.22 ms | 48.69 ms | 49.32 ms | **1.12 ms** |
| Full-text miss | 45.43 ms | 48.54 ms | 51.61 ms | **1.04 ms** |

**How to read it:** ES executed `_search` in about **1–3 ms**. ~45–50 ms on the client is HTTP + Docker + PowerShell JSON, not “BM25 is slow.” p95 close to p50 means a **stable** run. This is **not** production QPS.

Re-run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\benchmark-products.ps1
```

JSON: `build/performance/` (gitignored). Detail: [docs/09-performance-testing.md](docs/09-performance-testing.md).

Delete only the printed perf index, never `products` / `orders`.

---

## This machine

| Tool | Version |
|------|---------|
| JDK | Amazon Corretto 21 |
| Gradle | 9.7.1 |
| Git | 2.55 |
| ES / Kibana | 8.15.3 (Compose) |
