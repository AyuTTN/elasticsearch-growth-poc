# Elasticsearch terms mapped to this codebase

Use this as a dictionary. Each term is Elasticsearch language first, then the Java/Spring piece that implements it here.

## The three processes (do not mix these words)

| Term | What it is | In this POC |
|------|------------|-------------|
| **Cluster** | One or more Elasticsearch nodes that share data | The Docker container `es-growth-poc-elasticsearch`. Compose sets `discovery.type=single-node`, so the cluster has **one node**. |
| **Node** | One Elasticsearch process | That same container. `GET http://localhost:9200` returns `number_of_nodes: 1`. |
| **Spring Boot app** | Our JVM HTTP server | `gradle bootRun` on port **8080**. Not a node. Not an index. |

Port **9200** is Elasticsearch HTTP. Port **8080** is Spring. Kibana (later) is a third process.

---

## Storage words (SQL analogies are loose)

| ES term | Meaning | Closest SQL idea | Code here |
|---------|---------|------------------|-----------|
| **Index** | Named collection of JSON documents | Table | `@Document(indexName = "products")` on `Product`. HTTP: `/products`. |
| **Document** | One JSON object stored in an index | Row | One `Product` instance after `save`. |
| **Field** | One key on that JSON | Column | `name`, `description`, `category`, `price`, `stock`, `id`. |
| **Mapping** | Schema: type and analyzer per field | CREATE TABLE types | `@Field(type = FieldType....)` plus what ES stores under `_mapping`. |
| **`_id`** | Document identity inside the index | Primary key | Java field annotated `@Id`. Seed example: `seed-headphones`. |
| **Shard** | Slice of an index for scale | Partition | We do not configure this. ES 8 defaults (often 1 primary + replicas). Yellow health on one node often means replicas cannot be assigned. |

An index is **not** exactly a table: extra JSON fields can appear; mappings are declared for the fields we annotate.

---

## Annotations on `Product`

File: `src/main/java/com/poc/elasticsearch/model/Product.java`

```java
@Document(indexName = "products")
public record Product(
        @Id @Field(type = FieldType.Keyword) String id,
        @Field(type = FieldType.Text, analyzer = "standard") String name,
        @Field(type = FieldType.Text, analyzer = "standard") String description,
        @Field(type = FieldType.Keyword) String category,
        @Field(type = FieldType.Double) BigDecimal price,
        @Field(type = FieldType.Integer) int stock
) {}
```

### `@Document`

Spring Data: this class is an Elasticsearch **document type**.

`indexName = "products"` is the **index name**. First `save` (or seed) creates that index if it does not exist, using these field mappings.

**Not the same as** `@Entity` + `@Table` (JPA). No joins, no foreign keys.

### `@Id`

The value is both:

1. Java `product.id()`
2. Elasticsearch metadata `_id` (URL like `/products/_doc/seed-headphones`)

Seeds use stable strings (`seed-headphones`) so restart does not duplicate. User creates use `UUID.randomUUID()` in `ProductService`.

### `@Field` and `FieldType`

`@Field` tells Elasticsearch **how to index** that Java property. `FieldType` is the mapping type.

| `FieldType` | ES mapping | What ES does | Used on | Why |
|-------------|------------|--------------|---------|-----|
| **`Text`** | `"type": "text"` | **Analyzes** the string into tokens (words). Full-text `match` queries. | `name`, `description` | User will search “headphones”, not the exact title. |
| **`Keyword`** | `"type": "keyword"` | Stores the **whole string** as one value. `term` filters, aggregations, exact ID. | `id`, `category` | Category is a label (`Electronics`), not a sentence. |
| **`Double`** | `"type": "double"` | Number. Range queries, sort. | `price` | `BigDecimal` in Java; ES stores a floating numeric type. |
| **`Integer`** | `"type": "integer"` | Whole number. | `stock` | Count of items. |

Other `FieldType` values exist (`Date`, `Boolean`, `Long`, `Nested`, …). We do not use them yet.

### `analyzer = "standard"`

Only meaningful on **Text** fields.

An **analyzer** is a pipeline: split text, lowercase, drop punctuation.

`standard` turns `Wireless Headphones` into tokens similar to `wireless` and `headphones`.

**Keyword fields are not analyzed.** `Electronics` stays `Electronics` (exact). If category were `Text`, a `term` filter for `Electronics` would often miss because the value was tokenized.

This is the main footgun: **Text vs Keyword**.

---

## Spring Data vs Elasticsearch HTTP

| Spring / Java | What Elasticsearch actually does |
|---------------|----------------------------------|
| `spring.elasticsearch.uris` | Client base URL, default `http://localhost:9200` |
| `ElasticsearchRepository<Product, String>` | Spring generates a proxy that sends HTTP JSON to ES |
| `save(product)` | Index or update a document (`PUT /products/_doc/{id}`) |
| `saveAll(...)` | Bulk index |
| `findAll()` | Search/scan documents in `products` |
| `existsById(id)` | Check document `_id` |
| `starter-data-elasticsearch` | Auto-configures the Java client from YAML |

There is **no** `ProductRepository` implementation class. Spring creates it at startup (log: “Found 1 Elasticsearch repository interface”).

---

## Query words (used later; know them now)

| Term | Meaning | Typical field type |
|------|---------|--------------------|
| **`match`** | Full-text; uses analyzer on the query too | `Text` |
| **`term`** | Exact value; no analysis | `Keyword`, numbers |
| **`bool`** | Combine clauses (`must`, `filter`, `should`) | Mix of both |
| **Relevance / `_score`** | How well a `match` hit ranks | Text search |
| **Filter** | Yes/no, no score (e.g. category) | Keyword |

CRUD today uses `save` / `findAll`, not `match`. Mapping is already `Text`/`Keyword` so search can be added without remapping (remapping often needs a new index).

---

## Where each piece lives

```text
docker-compose.yml          cluster / node / volume es-data
application.yml             uris → client
Product.java                index + mapping + FieldType
ProductRepository.java      Spring Data CRUD to that index
ProductService.java         UUID + save / findAll
ProductDataInitializer.java seed documents with stable _id
ProductPageController       HTTP UI only — not an ES term
```

Inspect after `bootRun`:

```powershell
Invoke-RestMethod http://localhost:9200/products/_mapping | ConvertTo-Json -Depth 10
Invoke-RestMethod http://localhost:9200/products/_search | ConvertTo-Json -Depth 10
```

`_mapping` is the schema. `_search` hits are **documents**.
