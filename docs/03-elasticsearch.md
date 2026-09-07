# Elasticsearch

The product page still runs **without** talking to Elasticsearch. This file is why ES is its own process, and how we run that process on a laptop.

## What Elasticsearch is

Elasticsearch is a **distributed search and analytics engine**. You send it **JSON documents**. It:

1. Stores them in an **index** (roughly: a collection with a name, like `products`)
2. Builds **inverted indexes** on text fields so “headphones” can find documents that contain that term
3. Scores matches (**relevance**) instead of only returning true/false like `WHERE name = 'x'`
4. Exposes HTTP on port **9200** (JSON in, JSON out)

It is excellent at: full-text search, filters + search together, aggregations (“how many orders per status”).

It is a weak fit as the **only** system of record for money, stock, and foreign keys. It is near-real-time, not a transactional SQL engine. This POC still stores products and orders **in ES only** so we can learn one system. A production shop usually keeps **Postgres (or similar) as source of truth** and **copies** searchable fields into ES.

That dual-write / event-sync design is **out of scope** until we say otherwise. Know that we are simplifying.

---

## Why not PostgreSQL (or MySQL) for this POC

| Need | PostgreSQL | Elasticsearch |
|------|------------|----------------|
| Exact lookups by id | Excellent (`PRIMARY KEY`) | Fine (`GET /index/_doc/id`) |
| Joins, foreign keys, transactions | Excellent | Painful; we denormalize |
| Full-text “noise cancelling headphones” with ranking | `tsvector` / `pg_trgm` — workable, more DIY | Core product |
| Typo tolerance, analyzers, language | Extensions | Built-in analyzers |
| Dashboards over the same data | Metabase / Grafana SQL | **Kibana** (next doc) |
| You already operate it | Very common | Extra cluster to run |

If the growth assignment were “add search to an existing Spring + Postgres app,” we would keep Postgres and add ES as a read model. The assignment here is to **learn ES and Kibana**, so ES is the store.

**Other alternatives**

| Product | When it wins |
|---------|----------------|
| **OpenSearch** | AWS-aligned fork; APIs feel familiar; Spring Data has OpenSearch support in some stacks |
| **Apache Solr** | You already run Solr |
| **Meilisearch / Typesense** | Simpler DX, smaller ops, fewer analytics features |
| **Lucene in-process** | Libraries (Hibernate Search) without a server; no Kibana, no extra process |
| **In-memory `Map`** | Unit tests and Step 2 of *this* learning path |

---

## Core vocabulary (use these words precisely)

| Term | Meaning | SQL analogy (loose) |
|------|---------|---------------------|
| **Cluster** | One or more ES nodes | Database server |
| **Node** | One ES process | One Postgres instance |
| **Index** | Named collection of documents (`products`) | Table |
| **Document** | One JSON object with an `_id` | Row |
| **Field** | A key in the JSON | Column |
| **Mapping** | Types and analyzers for fields | Schema |
| **Analyzer** | How text is split into tokens (`standard` splits on word boundaries, lowercases) | Full-text config |
| **Query** | JSON describing what to find (match, term, bool) | SQL `SELECT ... WHERE` |
| **Shard** | Slice of an index for scale | Partition |

Loose analogies leak. An index is not a table: documents in one index can have extra fields; mappings are schema-on-write for *declared* fields.

---

## Text vs Keyword (the mapping you must understand)

This is the #1 Spring Data + ES footgun.

| Mapping | Field type in ES | Query that works | Example |
|---------|------------------|------------------|---------|
| **Text** | Analyzed, tokenized | `match` (full-text) | `name`, `description` — user types “wireless headphone” |
| **Keyword** | Exact value, not analyzed | `term` / filter | `category`, `status`, `customerEmail` (often keyword or both) |

If `category` is `text`, a term query for `electronics` can miss because the analyzer may still lowercase (sometimes it works) but aggregations and exact filters are the wrong tool. If `name` is `keyword`, searching “headphone” will **not** match “Wireless Headphones” unless you also have a text sub-field.

Spring Data looks like:

```java
@Field(type = FieldType.Text, analyzer = "standard")
private String name;

@Field(type = FieldType.Keyword)
private String category;
```

The Spring Data persistence step now uses this mapping. You can also use **multi-fields** (`name` as text + `name.keyword` as keyword) for sort + search. We will mention that when sorting by name becomes a requirement.

---

## How Spring talks to ES (preview)

Three common styles:

| Style | What you write | Pros | Cons |
|-------|----------------|------|------|
| **Spring Data repository** | `ProductRepository extends ElasticsearchRepository<Product, String>` | `save`, `findById`, derived queries | Complex relevance queries get awkward |
| **`ElasticsearchOperations` + NativeQuery** | Bool query, `multiMatch` | Real search | More ES knowledge |
| **Java API Client** (`ElasticsearchClient`) | Official ES client | Closest to REST API | Least “Spring-y” |

This project now uses repository methods for CRUD. Complex full-text search is the next layer and may use `ElasticsearchOperations` with native queries.

**Alternative:** call HTTP with `WebClient` to `:9200`. You learn the REST API; you maintain JSON by hand. Fine for a spike, noisy for a service.

---

## Local cluster: Docker, not a Windows installer

We run the **official Elasticsearch image** with Docker Compose. Reasons:

- Matches Linux production more than an MSI installer
- Version pinned in `docker-compose.yml` (this repo: **8.15.3**)
- Kibana can join the same compose file **later** with a matching image tag
- Data lives in a named volume `es-data`; deleting the volume wipes POC data

**Single-node** (`discovery.type=single-node`) is for a laptop. Production is multiple nodes.

**Security off** (`xpack.security.enabled=false`) is for local learning so we do not fight TLS and passwords on day one. **Never** copy that to a shared or production cluster.

**Alternative:** Elastic Cloud trial. No Docker. Needs network and an API key (do not commit keys).

**Alternative:** Elasticsearch as a Windows service. Painful upgrades; skip.

---

## Implemented: Compose file (2026-09-07)

File: `docker-compose.yml` at the repo root. **One service.** No Kibana. No change to `build.gradle.kts` or `ProductService`.

### Why a separate process at all

Spring Boot is an HTTP app. Elasticsearch is a **search server**. They do not share a JVM.

| Process | Port | Job right now |
|---------|------|----------------|
| `gradle bootRun` | 8080 | Product page; reads/writes through `ProductRepository` |
| `docker compose` Elasticsearch | 9200 | Stores the `products` index |

If we skipped Docker and added the Spring Data starter first, Boot would fail at startup with connection errors. You would debug Spring and Docker at the same time. This layer exists so `:9200` can be proven **before** Java depends on it.

### Why Docker Compose, not `docker run`

Compose pins image, ports, env, volume, and healthcheck in git. `docker run -p 9200:9200 ...` works once and is easy to mistype next week. Compose is the repeatable laptop recipe.

**Alternative:** Kubernetes. Absurd for one node on a laptop.

### Why this image and version

`docker.elastic.co/elasticsearch/elasticsearch:8.15.3`

- Official image, not a random Hub clone
- **8.x** matches what Spring Boot 3.5’s Elasticsearch client expects (the Java API client is ES 8)
- Pin a **patch** (`8.15.3`), not `8.15` or `latest`, so a rebuild next month does not surprise you
- Kibana, when we add it, must use the **same** `8.15.3`

**Alternative:** OpenSearch `2.x`. Similar HTTP ideas, different product and Spring starter. Out of scope.

### Line-by-line: environment and ports

| Setting | Why |
|---------|-----|
| `discovery.type=single-node` | One container must not wait for other nodes to form a cluster |
| `xpack.security.enabled=false` | No user/password. `curl http://localhost:9200` works. Laptop only |
| `xpack.security.http.ssl.enabled=false` | HTTP, not HTTPS. Spring will use `http://localhost:9200` later |
| `ES_JAVA_OPTS=-Xms512m -Xmx512m` | Cap heap so Docker Desktop on Windows does not eat the machine |
| `9200:9200` | Host browser/PowerShell/Spring → container HTTP API |
| Volume `es-data` | Indexes survive `docker compose down`. `down -v` wipes them |
| Healthcheck | `green` or `yellow` on `_cluster/health`. Yellow is normal on one node (replicas cannot be placed) |

Port **9300** (transport) is not published. We only talk HTTP.

### Why Kibana is not here yet

Kibana is a UI over documents. This cluster has **no indexes and no documents** until Spring writes them. Adding `:5601` now would be an empty shell and mix two failures (Compose vs “why is Discover empty?”).

### Commands (Windows PowerShell)

Docker Desktop must be running.

```powershell
cd D:\Code\JVM\elasticsearch-growth-poc
docker compose up -d
docker compose ps
Invoke-RestMethod http://localhost:9200
Invoke-RestMethod http://localhost:9200/_cluster/health
docker compose logs -f elasticsearch
```

First `up` downloads ~1GB. Later starts are faster. `ps` should show `healthy` after the healthcheck retries (up to ~2 minutes).

Logs: look for `started` / cluster UUID. Ctrl+C only detaches from `logs -f`; the container keeps running.

### What each Compose command does

Compose always reads `docker-compose.yml` in the **current folder**. Run these from `D:\Code\JVM\elasticsearch-growth-poc`.

`docker` is the Docker CLI. `compose` is the subcommand that understands a **project** (this folder + the YAML file): several containers, networks, and volumes as one unit.

| Command | What it does |
|---------|----------------|
| `docker compose up` | Create the network and volume if needed, **pull** the image if missing, **create** the container, **start** Elasticsearch. Without `-d`, this terminal **stays attached** and prints ES logs. Ctrl+C **stops** the container. |
| `docker compose up -d` | Same start, but **detached**. `-d` means “run in the background and give the prompt back.” The cluster keeps running after you close the terminal. This is what you want day to day. |
| `docker compose ps` | **Process status** for this Compose project only. Name, image, ports (`0.0.0.0:9200->9200`), and state (`running`, `healthy`, `starting`). Not the same as Windows Task Manager; it lists **containers**. |
| `docker compose logs elasticsearch` | Print logs already produced by the `elasticsearch` service. |
| `docker compose logs -f elasticsearch` | Same, then **follow** (`-f`) like `tail -f`. Ctrl+C stops following; the container **keeps running** (because you started with `-d`). |
| `docker compose down` | Stop and **remove** the container and the project network. The named volume `es-data` **stays**, so indexes survive the next `up`. |
| `docker compose down -v` | Same as `down`, plus **delete volumes**. Cluster data is gone. Use when you want a clean ES. |
| `docker compose stop` | Stop the container but **leave** it created. `up -d` later is a quick restart. Less common than `down` for this POC. |
| `docker compose pull` | Download the image without starting. Optional if `up` already pulls. |

**`up` vs `ps`:** `up -d` **changes** the world (start/create). `ps` only **reads** status.

**`up` vs `down`:** `up` is start; `down` is tear down. They are not start/stop of the Spring app. `gradle bootRun` is a different process on port 8080.

**`docker ps` vs `docker compose ps`:** `docker ps` lists **every** running container on the machine. `docker compose ps` lists only services defined in **this** `docker-compose.yml`.

Stop:

```powershell
docker compose down
```

### What you should see from `:9200`

`GET http://localhost:9200` JSON includes `name`, `cluster_name`, `version.number` (`8.15.3`).

`GET /_cluster/health`: `status` is `green` or `yellow`, `number_of_nodes` is `1`.

`GET /_cat/indices?v` should be **empty** (or only system indices). Product rows in the browser are **not** in Elasticsearch yet.

### Common failures

| Symptom | Cause | Fix |
|---------|--------|-----|
| `docker` not recognized | Docker Desktop not installed or not on PATH | Install/start Desktop; new terminal |
| `error during connect` | Desktop not running | Start Docker Desktop; wait until it is idle |
| Port 9200 in use | Another ES or a leftover container | `docker compose ps`; `netstat`; stop the other process |
| Container restarts / `max virtual memory` | Linux VM limits (rare on Docker Desktop) | Docker docs for `vm.max_map_count` |
| Health stays unhealthy | Still booting | `docker compose logs elasticsearch`; wait |
| `connection was closed unexpectedly` right after `up -d` | You called `:9200` while status was `health: starting` | Wait until `docker compose ps` shows `healthy`, then retry `Invoke-RestMethod` |

---

## Why the Spring app must not depend on ES in this step

If `starter-data-elasticsearch` is on the classpath, Boot **creates a client at startup**. No server → application context fails → you never get to debug HTTP.

Sequence:

1. Web starter only → `bootRun` works — **done**
2. Compose up ES → `Invoke-RestMethod :9200` works — **this step**
3. Add Spring Data dependency + mapping → `bootRun` talks to ES — **done**

---

## After this step (filled in)

- Image tag: `docker.elastic.co/elasticsearch/elasticsearch:8.15.3`
- Security: disabled for local HTTP
- Index names: `products`
- `curl` health output: record yours after `docker compose up -d`
- First mapping mistake and fix: not applicable yet
