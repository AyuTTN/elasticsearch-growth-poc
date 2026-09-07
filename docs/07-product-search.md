# Product search: full text plus an exact category filter

This is the first step that uses Elasticsearch for what it is designed for, rather than only as durable JSON storage.

## Browser behavior

The product page now has two optional inputs:

| Input | Example | Elasticsearch behavior |
|-------|---------|------------------------|
| Name or description (`q`) | `headphones` | Analyzed full-text `multi_match` query |
| Category | `Electronics` | Exact `term` filter on a keyword field |

Both blank: list all products.

Text only: find relevant text.

Category only: exact category filter.

Both: a product must match the text **and** the category.

Example URL:

```text
http://localhost:8080/products?q=headphones&category=Electronics
```

We use HTTP **GET** because search only reads data. The query appears in the URL, so the user can bookmark, copy, refresh, and use browser history.

## Request path

```text
Browser GET /products?q=headphones&category=Electronics
  -> ProductPageController
  -> ProductService.search("headphones", "Electronics")
  -> ElasticsearchOperations.search(...)
  -> Elasticsearch :9200 / products index
  -> SearchHit<Product>
  -> controller model
  -> products.html
```

The repository remains useful for simple CRUD. `ElasticsearchOperations` is used for search because the query has optional clauses and is easier to read as native Elasticsearch DSL.

## Query built by `ProductService`

The Java query is conceptually equivalent to:

```json
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

This JSON is called the Elasticsearch **Query DSL**.

## `bool`: combine different kinds of conditions

A bool query is not a Java boolean. It is an Elasticsearch container for clauses:

| Clause | Meaning | Affects `_score`? |
|--------|---------|-------------------|
| `must` | Document must match | Yes |
| `filter` | Document must match, but only as yes/no | No |
| `should` | Prefer matches; can behave like OR | Yes |
| `must_not` | Document must not match | No |

Text is in `must` because relevance matters. Category is in `filter` because `Electronics` either matches exactly or does not; it should not make one hit “more electronic” than another.

Filters are also easier for Elasticsearch to cache.

## `multi_match`: search multiple Text fields

```java
.query(text.trim())
.fields("name^2", "description")
```

`multi_match` applies full-text matching across multiple fields.

`name^2` gives the name field a **boost of 2**. A hit in the name counts roughly twice as strongly as the same hit in the description. That affects `_score` and ranking.

Why not one `match` query?

`match` targets one field. We want one user input to search both `name` and `description`. Two separate `match` clauses are possible; `multi_match` expresses this intent directly.

The same `standard` analyzer used while indexing is used for the search text. Case is normalized:

```text
HEADPHONES -> headphones
Wireless Headphones -> wireless, headphones
```

## `term`: exact Keyword matching

```java
.field("category")
.value(category.trim())
```

A `term` query does **not** analyze the input.

Our mapping:

```java
@Field(type = FieldType.Keyword)
String category
```

Therefore:

- `Electronics` matches `Electronics`
- `electronics` does not match `Electronics`
- `Electro` does not partially match

The page explicitly labels category as exact and case-sensitive.

### If we wanted case-insensitive categories

Options:

1. Normalize in Java (store and search lowercase).
2. Add a lowercase **normalizer** to the keyword mapping.
3. Use a second normalized sub-field.

Mappings generally cannot be changed in place for existing indexed data. A real change often means a new index and reindexing. We keep exact case for this learning step.

## Optional clauses

The service uses `StringUtils.hasText`:

- `null`, empty, or spaces → absent
- non-whitespace → present

Only present clauses are added to the bool query.

No filters at all returns `findAll()` instead of sending an unnecessary bool query.

The four resulting shapes are:

```text
q blank + category blank       -> repository.findAll()
q present + category blank     -> bool.must(multi_match)
q blank + category present     -> bool.filter(term)
q present + category present   -> both clauses
```

## `NativeQuery` and `ElasticsearchOperations`

Spring Data exposes two levels:

| API | Used for | Here |
|-----|----------|------|
| `ProductRepository` | Simple CRUD | `save`, `findAll`, seed checks |
| `ElasticsearchOperations` | Custom searches | bool + multi-match + term |

`NativeQuery` wraps the official Elasticsearch Java client's query object so Spring Data can execute it and map hits back to `Product`.

```java
elasticsearchOperations.search(query, Product.class)
```

returns `SearchHits<Product>`. Each `SearchHit` contains:

- The mapped `Product` content
- `_score`
- Index name
- Sort values and optional highlights

This page currently extracts only `SearchHit::getContent`. A later relevance lesson could display `_score`.

## Result limit

```java
.withPageable(PageRequest.of(0, 100))
```

This means first page, maximum 100 hits.

Elasticsearch defaults to 10 results if size is not specified. Loading every hit is unsafe as an index grows. A real UI should expose pagination rather than hard-code 100.

## Why not a derived repository method?

Spring Data can derive methods from names, for example:

```java
findByCategory(String category)
```

That is fine for a fixed exact query. It becomes awkward when:

- Text is optional
- Category is optional
- Text spans two fields
- Field boosting matters

Building the bool query keeps the search intent explicit.

## Verify in the browser

Restart Spring to load the new code:

```powershell
gradle bootRun
```

Try:

| Text | Category | Expected |
|------|----------|----------|
| `headphones` | blank | Wireless Headphones |
| `noise cancelling` | blank | Wireless Headphones |
| blank | `Furniture` | Standing Desk |
| `desk` | `Electronics` | 0 results |
| blank | blank | all products |

Clear resets the URL to `/products`.

## Verify the equivalent DSL directly

PowerShell can send a JSON query:

```powershell
$body = @{
  query = @{
    bool = @{
      must = @(
        @{
          multi_match = @{
            query = "headphones"
            fields = @("name^2", "description")
          }
        }
      )
      filter = @(
        @{
          term = @{
            category = "Electronics"
          }
        }
      )
    }
  }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:9200/products/_search `
  -ContentType application/json `
  -Body $body |
  ConvertTo-Json -Depth 10
```

This bypasses Spring and proves the underlying ES query.

## Tests and live verification

Automated tests verify:

- Controller forwards `q` and `category`
- Query is a bool query
- `must` contains a multi-match query
- `filter` contains a term query
- Returned `SearchHit` content becomes products

Live verification against ES 8.15.3 confirmed:

- `headphones` + `Electronics` found Wireless Headphones
- Standing Desk was excluded
- Category-only `Furniture` found Standing Desk
- `desk` + `Electronics` returned zero

## Next step

Add the orders workflow and a second index, or add Kibana now that `products` contains searchable documents. The planned sequence puts orders first, then Kibana so both indexes can be inspected together.
