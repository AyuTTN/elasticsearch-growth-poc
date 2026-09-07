# Spring Data Elasticsearch: replacing the in-memory store

This step connects the existing Spring Boot product page to the Elasticsearch process on port **9200**. The page and controller remain the same. The persistence implementation changes.

## Before and after

Before:

```text
Browser -> ProductPageController -> ProductService -> ConcurrentHashMap
                                                    (lost on restart)
```

After:

```text
Browser -> ProductPageController -> ProductService -> ProductRepository
                                                    -> Elasticsearch :9200
                                                    -> products index
```

This is why we built the layers separately. Thymeleaf does not need to know where products are stored. The controller still calls `productService.findAll()` and `productService.create(form)`.

## Dependency added

`build.gradle.kts` now contains:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
```

This starter brings:

- Spring Data repository abstractions
- Spring Data Elasticsearch mapping annotations
- Elastic's Java API client
- Spring Boot auto-configuration for the client

We omit a version because the Spring Boot 3.5.5 dependency management selects a compatible Spring Data release.

**Alternative 1: official Java client directly.** More control over requests and responses, but CRUD requires more code.

**Alternative 2: raw HTTP calls.** Best for learning Elasticsearch's REST API, but JSON construction and error handling become our responsibility.

**Alternative 3: JPA repository.** Same repository idea, but stores relational rows in SQL. It does not provide Elasticsearch analyzers or relevance scoring.

## Connection configuration

`application.yml`:

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}
```

The expression means:

1. Use the environment variable `ELASTICSEARCH_URI` if it exists.
2. Otherwise connect to `http://localhost:9200`.

The default works because Spring runs on Windows and Docker publishes container port 9200 to host port 9200.

In production, this would normally be HTTPS plus credentials stored outside git. Our Docker Compose explicitly disables security for a laptop-only POC.

## Document mapping

`Product` is now an Elasticsearch document:

```java
@Document(indexName = "products")
public record Product(
        @Id @Field(type = FieldType.Keyword) String id,
        @Field(type = FieldType.Text, analyzer = "standard") String name,
        @Field(type = FieldType.Text, analyzer = "standard") String description,
        @Field(type = FieldType.Keyword) String category,
        @Field(type = FieldType.Double) BigDecimal price,
        @Field(type = FieldType.Integer) int stock
) {
}
```

### What each annotation means

| Annotation | Effect |
|------------|--------|
| `@Document(indexName = "products")` | Instances live in the `products` index |
| `@Id` | The Java `id` is also used as Elasticsearch metadata `_id` |
| `Keyword` | Exact value; useful for IDs, category filters, sorting, aggregations |
| `Text` | Analyzed into terms; useful for full-text matching |
| `Double` | Numeric price; supports ranges and sorting |
| `Integer` | Numeric stock count |

### Why `name` is Text but `category` is Keyword

The standard analyzer turns:

```text
Wireless Headphones
```

into searchable terms similar to:

```text
wireless
headphones
```

A future `match` query for `headphones` can find the product. Category is an exact label. We want `Electronics` as one value for filtering and aggregation, not individual analyzed words.

This step does not add the search form yet. It creates the correct mapping so the next step can demonstrate `match` versus `term`.

## Repository

```java
public interface ProductRepository
        extends ElasticsearchRepository<Product, String> {
}
```

There is no implementation class. Spring Data creates a proxy at application startup.

The generic arguments mean:

- Entity/document type: `Product`
- ID type: `String`

Inherited methods include `save`, `saveAll`, `findAll`, `findById`, `existsById`, and `deleteById`.

This convenience is useful for CRUD. More advanced relevance queries will use repository query methods or `ElasticsearchOperations` later.

## Service changes

`ProductService` now receives `ProductRepository` through its constructor:

```text
Spring creates repository proxy
  -> Spring creates ProductService(repository)
  -> Spring creates ProductPageController(service)
```

`findAll()` converts the repository's `Iterable<Product>` into a stream and sorts it for stable page output.

`create()`:

1. Trims form strings.
2. Generates a UUID in Java.
3. Constructs a `Product`.
4. Calls `productRepository.save(product)`.
5. Returns the indexed document.

We generate the ID ourselves rather than depending on undocumented ID-generation behavior. String UUIDs work well as distributed identifiers. Sequential numeric IDs would require another system to safely allocate them.

## Seed data is now durable and idempotent

`ProductDataInitializer` implements `ApplicationRunner`, so it executes after the Spring context starts.

Each seed has a stable ID such as `seed-headphones`. On every startup:

1. Check whether each seed ID exists.
2. Save only missing seeds.

This is **idempotent**: running the app repeatedly does not create duplicate seed rows.

User-created products use UUIDs and survive Spring application restarts because they are in the Docker volume.

They do **not** survive:

```powershell
docker compose down -v
```

`-v` deletes the Elasticsearch data volume.

### Production alternative

Application startup seed code is convenient for a POC. Production mappings and reference data are normally managed with versioned migrations, index templates, or deployment jobs. Startup should not silently mutate large production indexes.

## Startup order now matters

Start Elasticsearch first:

```powershell
docker compose up -d
docker compose ps
```

Wait for `healthy`, then start Spring:

```powershell
gradle bootRun
```

Open:

```text
http://localhost:8080/products
```

If Elasticsearch is down, the application may fail during repository initialization or seed loading. That is now expected because persistence is no longer in memory.

## Verify without Postman

Use the browser to add a product. Then inspect Elasticsearch with PowerShell:

```powershell
Invoke-RestMethod http://localhost:9200/products/_count
Invoke-RestMethod http://localhost:9200/products/_mapping |
    ConvertTo-Json -Depth 10
Invoke-RestMethod http://localhost:9200/products/_search |
    ConvertTo-Json -Depth 10
```

Restart only Spring:

1. Ctrl+C in the `gradle bootRun` terminal.
2. Run `gradle bootRun` again.
3. Refresh the page.

The product remains. That proves it is in Elasticsearch, not the old map.

## Verification performed during implementation

The new application was started on a random temporary port while Elasticsearch remained healthy.

Observed:

- One Elasticsearch repository discovered
- Application started successfully
- `products` index created
- `_count` returned **4**
- `name` and `description` mapped as `text`
- `category` mapped as `keyword`
- `price` mapped as `double`
- `stock` mapped as `integer`

The existing port 8080 process was not replaced automatically. Stop and restart your own `gradle bootRun` process to load this code.

## Test strategy

Normal tests should be fast and should not require Docker.

- `ProductPageControllerTest` is now a web-slice test. `ProductService` is mocked, so it tests URLs, validation, redirects, and rendered HTML.
- `ProductServiceTest` mocks `ProductRepository`. It tests sorting, trimming, generated IDs, and the call to `save`.

An actual Elasticsearch integration test belongs in a later Testcontainers step. Testcontainers will create a known clean ES container per test suite instead of depending on whatever happens to be running on port 9200.

## Next step

Add product search:

- Search text across `name` and `description`
- Optional exact `category` filter
- Keep the same browser UI
- Explain `match`, `term`, and `bool` queries
