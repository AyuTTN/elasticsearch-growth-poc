# Elasticsearch Growth POC

From-scratch learning project: **Spring Boot + Gradle + Elasticsearch + Kibana**.

Application code is added one prompt at a time. The product catalog persists and searches in Elasticsearch. **Kibana** is in Docker Compose on port 5601.

## Branch

`cursor/learn-spring-es-gradle`

Do not use `cursor/spring-boot-es-kibana-poc-d372` for this learning path. That branch already contains a finished Maven app. We rebuild here with Gradle so each layer is deliberate.

## Run the current milestone

```powershell
gradle bootRun
```

Start Elasticsearch first and wait for `healthy`, then run Spring:

```powershell
docker compose up -d
docker compose ps
gradle bootRun
```

Open `http://localhost:8080/products`. Add and list products in the browser—Postman is not required. Products survive Spring restarts because they are stored in Elasticsearch.

Inspect the same index in Kibana: `http://localhost:5601` — walkthrough in [docs/04-kibana.md](docs/04-kibana.md). Wait until `docker compose ps` shows Kibana **healthy** (often slower than Elasticsearch).

Orders: `http://localhost:8080/orders`. Stock is reduced on the product document when an order is placed. Details: [docs/08-orders.md](docs/08-orders.md).

Performance benchmark (100,000 synthetic products in a separate index):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\benchmark-products.ps1
```

Methodology and result interpretation: [docs/09-performance-testing.md](docs/09-performance-testing.md).

Search examples:

- Text `headphones`: analyzed search over product name and description
- Category `Furniture`: exact keyword filter
- Text `desk` + category `Electronics`: both must match, so zero results

Run tests without Docker dependencies:

```powershell
gradle test
```

## Elasticsearch

Docker Desktop must be running. First start can take several minutes while the image downloads.

```powershell
docker compose up -d
docker compose ps
Invoke-RestMethod http://localhost:9200
Invoke-RestMethod http://localhost:9200/_cluster/health
```

Stop (data in the `es-data` volume is kept):

```powershell
docker compose down
```

Wipe stored indexes too:

```powershell
docker compose down -v
```

Details: [docs/03-elasticsearch.md](docs/03-elasticsearch.md)

Spring Data wiring and mapping: [docs/05-spring-data-elasticsearch.md](docs/05-spring-data-elasticsearch.md)

Index, document, mapping, `FieldType`: [docs/06-elasticsearch-terms-in-this-code.md](docs/06-elasticsearch-terms-in-this-code.md)

Full-text and category search: [docs/07-product-search.md](docs/07-product-search.md)

## Start here

1. [docs/PROGRESS.md](docs/PROGRESS.md) — checklist of what exists vs what is next
2. [docs/00-learning-method.md](docs/00-learning-method.md) — how we work prompt-by-prompt
3. Then read in order:
   - [docs/01-toolchain-and-gradle.md](docs/01-toolchain-and-gradle.md)
   - [docs/02-spring-boot.md](docs/02-spring-boot.md)
   - [docs/03-elasticsearch.md](docs/03-elasticsearch.md)
   - [docs/04-kibana.md](docs/04-kibana.md)
   - [docs/05-spring-data-elasticsearch.md](docs/05-spring-data-elasticsearch.md)
   - [docs/06-elasticsearch-terms-in-this-code.md](docs/06-elasticsearch-terms-in-this-code.md)
   - [docs/07-product-search.md](docs/07-product-search.md)
   - [docs/08-orders.md](docs/08-orders.md)
   - [docs/09-performance-testing.md](docs/09-performance-testing.md)

## Current machine (2026-09-07)

| Tool | Version |
|------|---------|
| JDK | Amazon Corretto 21 |
| Gradle | 9.7.1 (`C:\gradle\gradle-9.7.1`) |
| Git | 2.55.0 |
| Docker | installed (used from Step 3 onward) |
