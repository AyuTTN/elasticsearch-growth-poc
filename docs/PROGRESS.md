# Progress tracker

Update this file whenever a step is finished. Status values: `not started` | `in progress` | `done`.

| Step | Topic | Code on this branch? | Status | Notes |
|------|--------|----------------------|--------|-------|
| 0 | Toolchain (Git, JDK 21, Gradle 9 PATH) | No | done | Gradle `bin` had to be added to User PATH. `GRADLE_HOME` alone is not enough. |
| 1 | Gradle project skeleton (`settings.gradle.kts`, `build.gradle.kts`) | Yes | done | Global Gradle 9.7.1, Java 21, Spring Boot 3.5.5 |
| 2 | Plain Spring Boot web app (no Elasticsearch) | Yes | done | Browser product form/list, validation, in-memory storage, tests |
| 3 | Docker Compose: Elasticsearch | Yes | done | ES 8.15.3; Kibana added in step 7 |
| 4 | Spring Data Elasticsearch persistence | Yes | done | `products` index, repository, durable seed and user products; glossary in `docs/06-elasticsearch-terms-in-this-code.md` |
| 5 | Elasticsearch full-text product search | Yes | done | `multi_match` on name/description + exact category `term` filter inside `bool` |
| 6 | Orders workflow on Elasticsearch | Yes | done | `orders` index, nested line items, Java stock decrement, email/status `term` filters |
| 7 | Docker Compose: Kibana + products walkthrough | Yes | done | Kibana 8.15.3 on :5601; data view + Dev Tools in `docs/04-kibana.md` |
| 8 | 100,000-product performance benchmark | Yes | done | 7,071.87 docs/s; ES search `took` ~1–3 ms; client p95 ~49–52 ms |

## Decision log

| Date | Decision | Why | Alternative we did not take |
|------|----------|-----|-----------------------------|
| 2026-09-07 | New branch `cursor/learn-spring-es-gradle` from `main` | Rebuild from zero; the other branch already has a finished Maven app | Continuing on `cursor/spring-boot-es-kibana-poc-d372` |
| 2026-09-07 | Gradle, not Maven | You already run Gradle 9.7.1; we want one build tool you will keep using | Maven Wrapper (what the old branch used) |
| 2026-09-07 | Kotlin DSL (`*.gradle.kts`) | Type-safe, current Spring Initializr default | Groovy `build.gradle` |
| 2026-09-07 | Docs first, then prompt-by-prompt code | Each layer gets a “why + alternative” before it lands in the repo | Copying the finished POC |
| 2026-09-07 | Add a server-rendered Thymeleaf UI | Use the POC entirely in a browser instead of Postman | SPA frontend or API-only workflow |
| 2026-09-07 | Do not make Thymeleaf a learning topic | The learning goal remains Spring Boot, Elasticsearch, and Kibana | Detailed template-engine lessons |
| 2026-09-07 | Keep products in memory before Elasticsearch | Proves Spring MVC works without Docker and makes the persistence replacement visible | Adding Spring Data Elasticsearch immediately |
| 2026-09-07 | Use the globally installed Gradle command | You prefer a simpler local setup and already have Gradle 9.7.1 configured | Committing the Gradle Wrapper for reproducible builds |
| 2026-09-07 | `org.gradle.console=plain` | Cursor terminal should stream Spring logs like IntelliJ | Gradle's default rich progress bar |
| 2026-09-07 | Seed four products in `ProductService` | Catalog is usable immediately after `bootRun` | Empty map until the user submits the form |
| 2026-09-07 | Elasticsearch in Docker Compose, not in Spring yet | Prove the cluster is a separate process on `:9200` | Adding `starter-data-elasticsearch` at the same time |
| 2026-09-07 | ES 8.15.3, single-node, security off | Laptop POC; skip TLS/passwords until we need them | Elastic Cloud, Windows installer, OpenSearch |
| 2026-09-07 | Kibana not in this Compose file | Empty Discover is confusing; add Kibana after documents exist | Bundling Kibana on day one |
| 2026-09-07 | `ProductRepository` replaces `ConcurrentHashMap` | Products now persist in the `products` index and survive Spring restarts | Keeping the temporary in-memory store |
| 2026-09-07 | Stable IDs for seeds, UUIDs for user products | Startup is idempotent and does not need a numeric sequence | Re-inserting seeds or depending on generated ES IDs |
| 2026-09-07 | Unit/MVC tests mock the repository boundary | `gradle test` remains fast and does not depend on Docker | Requiring local Elasticsearch for every test |
| 2026-09-07 | `ElasticsearchOperations` for optional search clauses | Explicit bool DSL handles text, category, or both cleanly | Long derived repository method names |
| 2026-09-07 | `multi_match` in `must`, category `term` in `filter` | Text contributes relevance; exact category should not change score | Treating category as analyzed text |
| 2026-09-07 | Limit search to 100 results | Avoid Elasticsearch's default 10 while keeping a hard safety limit for this POC | Unbounded result loading |
| 2026-09-07 | Add Kibana after `products` has documents | Discover is useful immediately; same 8.15.3 tag as ES | Shipping Kibana on day one with an empty cluster |
| 2026-09-07 | Data view without a time field | `Product` has no `@timestamp` / Date mapping | Forcing a time filter and wondering why Discover is empty |
| 2026-09-07 | Orders denormalize product name/price onto the order | ES has no joins; history must not depend on later catalog edits | SQL foreign key to `products` |
| 2026-09-07 | Stock check in Java, two ES writes | Business rule is not a search query; ES is not the transaction manager | Relying on Elasticsearch to decrement stock atomically |
| 2026-09-07 | Performance benchmark instead of Testcontainers | Current POC goal is indexing/search latency over 100,000 products | Automated real-ES correctness integration test |
| 2026-09-07 | Separate `products-perf-*` index | Benchmark data must not pollute the UI's `products` index | Loading synthetic products through the application |
| 2026-09-07 | Sequential warm benchmark with percentiles | Simple, dependency-free PowerShell baseline on this laptop | Claiming production concurrency capacity |

## What is intentionally missing

There is intentionally **no Testcontainers integration test**. Products, orders, search, Elasticsearch, Kibana, and a local performance harness are in place.
