# Kibana: inspect the `products` index

Kibana is a **web UI for Elasticsearch** on port **5601**. Shop customers use the Spring page on **8080**. You use Kibana to see the same cluster Spring writes to.

This file is the laptop walkthrough for **`products`**. Security is off (local POC only).

---

## Why Kibana exists in this POC

| Job | Kibana | Alternative |
|-----|--------|-------------|
| Browse documents | Discover | `Invoke-RestMethod .../_search` |
| Run Query DSL | Dev Tools | curl / PowerShell JSON |
| See mapping (`text` vs `keyword`) | Index Management + `_mapping` | Spring `@Field` only |
| Charts | Lens — optional | Skip for this step |

**Why not Grafana?** Extra datasource; weaker document browser.  
**Why version 8.15.3?** Same tag as Elasticsearch. Mismatched Kibana/ES majors often fail to start.

Kibana does **not** replace Spring: no product form, no validation, no `name^2` in Java. It speaks HTTP to ES like our client does.

---

## Compose service (what we added)

`docker-compose.yml` now has a second service:

| Setting | Why |
|---------|-----|
| Image `kibana:8.15.3` | Match ES 8.15.3 |
| `depends_on` + `service_healthy` | Do not start Kibana until ES answers `_cluster/health` |
| `ELASTICSEARCH_HOSTS=http://elasticsearch:9200` | **Docker DNS name** of the ES service, not `localhost` |
| `5601:5601` | Your browser → Kibana |

**Hostname split (same cluster, two URLs):**

```text
You (browser)     -->  localhost:5601     -->  Kibana container
Spring (Windows)  -->  localhost:9200     -->  ES container
Kibana container  -->  elasticsearch:9200 -->  same ES container
```

Inside the Kibana container, `localhost:9200` is **Kibana itself**, not Elasticsearch. That is why the env var uses the Compose **service name** `elasticsearch`.

No extra volume for Kibana. Saved objects (data views) live in Elasticsearch hidden indices (`.kibana*`). `docker compose down -v` wipes those too.

---

## Start and wait

Docker Desktop must be running. First Kibana pull takes several minutes.

```powershell
cd D:\Code\JVM\elasticsearch-growth-poc
docker compose up -d
docker compose ps
```

Wait until **both** show `healthy`:

- `es-growth-poc-elasticsearch`
- `es-growth-poc-kibana`

Kibana often stays `health: starting` longer than ES (1–3 minutes). Same lesson as ES: do not assume the UI is ready the second `up -d` returns.

```powershell
docker compose logs -f kibana
```

Ctrl+C stops following, not the container. Look for Kibana being available / status available.

Open:

**http://localhost:5601**

Skip extra Elastic Cloud / sample-data tours if offered. You already have `products` from Spring.

If Discover is empty: Spring must have run at least once against this volume so the index exists.

```powershell
Invoke-RestMethod http://localhost:9200/products/_count
```

`count` should be ≥ 4 after seed.

---

## Walkthrough: `products` index

Kibana 8 labels this a **Data view** (older docs say **index pattern**). Same idea: “which index names may I query?”

After you place orders, create a second data view named `orders` on index `orders`. That index **has** `createdAt`, so you may select it as the time field.

### 1. Create a data view

1. Menu (☰) → **Management** → **Stack Management**
2. **Kibana** → **Data Views**
3. **Create data view**
4. **Name:** `products` (label in Kibana only)
5. **Index pattern:** `products` (must match the ES index name from `@Document(indexName = "products")`)
6. **Timestamp field:** **Do not use a time field** / skip time filter  

   Our `Product` has **no** `Date` field. If Kibana requires `@timestamp`, Discover will look empty or error because documents have no time.

7. Save

`products*` as a pattern also works; we use exact `products` to stay obvious.

### 2. Discover (the documents)

1. Menu → **Analytics** → **Discover**
2. Top left: select data view **products**
3. You should see seed rows: Wireless Headphones, Standing Desk, Espresso Beans, Running Shoes, plus any products you added in Spring

Click a row: `_id` (`seed-headphones` vs a UUID), `_source` (name, category, price, stock).

This is the same JSON Spring indexed. If Spring’s table shows a product and Discover does not, you are on a different cluster/volume or the data view name is wrong.

### 3. Index Management (the mapping)

1. **Stack Management** → **Index Management**
2. Open **products**
3. **Mappings** (or equivalent)

Confirm:

| Field | Type you should see |
|-------|---------------------|
| `name`, `description` | `text` (+ analyzer `standard`) |
| `category` | `keyword` |
| `price` | `double` |
| `stock` | `integer` |

That is `@Field(type = FieldType....)` made visible without Java.

### 4. Dev Tools (the Query DSL)

1. Menu → **Management** → **Dev Tools** (or search “Console”)

Paste and click the play icon:

**Mapping**

```http
GET products/_mapping
```

**All products (size 10 by default)**

```http
GET products/_search
```

**Same idea as the catalog search** (`headphones` + category `Electronics`):

```http
POST products/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "headphones",
            "fields": ["name^2", "description"]
          }
        }
      ],
      "filter": [
        {
          "term": {
            "category": "Electronics"
          }
        }
      ]
    }
  }
}
```

Hits include **`_score`**. Spring’s table hides it; here you see ranking.

**Exact category only** (no BM25):

```http
GET products/_search
{
  "query": {
    "term": {
      "category": "Furniture"
    }
  }
}
```

`electronics` (lowercase) should **not** match `Electronics` — Keyword `term` is exact.

---

## How this relates to Spring

```text
Thymeleaf form  -->  ProductService.search  -->  ElasticsearchOperations
                                              -->  POST /products/_search
                                              -->  same DSL you run in Dev Tools
```

If Dev Tools returns hits and the page does not, debug Spring (app not restarted, wrong port). If both are empty, debug ES (wrong index, empty volume).

---

## Stop / wipe

```powershell
docker compose down          # keep es-data (products + .kibana*)
docker compose down -v       # wipe indexes AND Kibana saved objects
```

---

## Common failures

| Symptom | Cause | Fix |
|---------|--------|-----|
| `5601` connection refused | Kibana still starting | `docker compose ps` until `healthy` |
| Kibana “unable to connect to Elasticsearch” | `ELASTICSEARCH_HOSTS` used `localhost` | Must be `http://elasticsearch:9200` |
| Discover empty, `_count` is 4 | Time field selected, docs have no date | Recreate data view **without** timestamp |
| Discover empty, `_count` is 0 | Spring never indexed this volume | `gradle bootRun` with ES healthy |
| Version mismatch errors | Different ES vs Kibana tags | Both `8.15.3` |

---

## After this step (filled in)

- Image tag: `docker.elastic.co/kibana/kibana:8.15.3`
- URL: http://localhost:5601
- Data view: `products` (index `products`, no time field)
- Dev Tools: `POST products/_search` bool `multi_match` + `term` on `category`
