# How we learn on this branch

## Goal

Build a small **product catalog + orders** HTTP API in Spring Boot, store and search documents in **Elasticsearch**, and inspect them in **Kibana**. The point is not to finish quickly. The point is to know **why each piece exists** and **what we would use instead**.

## Why a new empty branch

The branch `cursor/spring-boot-es-kibana-poc-d372` already contains a working Maven app: controllers, Elasticsearch repositories, Docker Compose, tests. If we only read that code, it is easy to treat Elasticsearch as “the database Spring picked” and Kibana as “the UI that came with Docker.” Those are the wrong stories.

On `cursor/learn-spring-es-gradle` we start with **zero application code**. You send one prompt per layer. After each layer we:

1. Add only the files that layer needs.
2. Extend these docs with what we learned (failures, versions, commands that actually worked on Windows).
3. Update [PROGRESS.md](PROGRESS.md).

## Prompt-by-prompt contract

You drive. Suggested sequence (you can change it):

1. **“Create the Gradle Spring Boot skeleton”** — wrapper, plugins, Java 21, empty `@SpringBootApplication`, `bootRun`.
2. **“Add a normal web app with no Elasticsearch”** — in-memory products, validation, and a browser UI. App must start with JDK only. **Done.**
3. **“Add Docker Compose for Elasticsearch only”** — compose file, why 8.x, security off for local only. **Done.**
4. **“Replace in-memory store with Spring Data Elasticsearch”** — `@Document`, `Text` vs `Keyword`, repository, durable seeds. **Done.**
5. **“Add product search”** — full-text `multi_match`, exact `term`, combined `bool` query. **Done.**
6. **“Add orders”** — stock check, second index. **Done.**
7. **“Add Kibana and a walkthrough”** — data views, Discover, Dev Tools. **Done.**
8. **“Add performance testing”** — 100,000 synthetic products, bulk indexing, warm search latency percentiles. **Done.** Testcontainers explicitly skipped.

Do not ask for two layers in one prompt if you want the docs to stay aligned with the code.

## What “done” means for a layer

A layer is done when:

- You can run a command and see a result (compile, HTTP response, ES cluster health, Kibana page).
- The docs say **why**, **how**, and **what else we could have used**.
- Failures we hit (PATH, first Gradle download, Docker not running) are written down so we do not rediscover them.

## Mental model (keep this)

```text
You / curl / Postman
        |
        v
Spring Boot (our code: HTTP + business rules)
        |
        v
Elasticsearch (search engine + JSON documents)
        ^
        |
Kibana (technical UI over the same cluster; not used by shop customers)
```

Three processes, three jobs. Spring Boot does not visualize data. Kibana does not accept `POST /api/products`. Elasticsearch does not know your stock business rule unless we encode it in Java.

The product page is a fourth piece, but it runs **inside Spring Boot**:

```text
Browser <--> Thymeleaf HTML rendered by Spring Boot <--> ProductService
```

We use that page so normal POC actions do not require Postman. Thymeleaf is intentionally treated as implementation plumbing rather than a learning module. When Elasticsearch is added, the page stays mostly unchanged; the service's storage implementation changes.

## Reading order for the theory docs

1. [01-toolchain-and-gradle.md](01-toolchain-and-gradle.md) — before any Java
2. [02-spring-boot.md](02-spring-boot.md) — before Elasticsearch
3. [03-elasticsearch.md](03-elasticsearch.md) — before Docker Compose
4. [04-kibana.md](04-kibana.md) — after data exists in an index
