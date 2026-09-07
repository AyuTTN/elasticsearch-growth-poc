# Orders on Elasticsearch

This step adds a second index, **`orders`**, and a business rule that Elasticsearch cannot own: **stock**.

## What you can do in the browser

http://localhost:8080/orders

- Place an order: email + product + quantity
- Stock on the product document goes down if the order succeeds
- Search by exact email and/or exact status (`PENDING`)
- Line items are stored **on the order document** (name, qty, price at order time)

Products page is unchanged except for a nav link.

## Why a second index

| Index | Role |
|-------|------|
| `products` | Catalog + current stock + search |
| `orders` | Purchase snapshot + filter by customer/status |

They are **not joined**. Elasticsearch has no foreign keys. The order copies `productName` and `unitPrice` so later catalog edits do not rewrite history.

That copy is **denormalization**. SQL would keep `order_line.product_id` and join to `product.name`. Here a GET of one order already has the names.

**Nested** (`@Field(type = FieldType.Nested)` on `items`): each line is its own inner document. That matters when you later query “orders that bought Headphones **and** qty ≥ 2 on that same line.” `Object` (flattened) can mix fields across lines. We use Nested even for one line so the mapping is honest.

## Stock lives in Java

```text
find product by id
  if missing → error
  if stock < qty → error (no writes)
  save product with stock - qty
  save order document
```

Elasticsearch **does not** run this as one ACID transaction. If the process dies between the two saves, you can get reduced stock without an order (or the reverse if you swapped the order). Production systems use a SQL/source-of-truth store, or ES `if_seq_no` optimistic concurrency, or a queue. This POC does the two writes in one JVM method so the rule is visible.

`filter` / BM25 are irrelevant to stock. Stock is an integer on `products`.

## Mapping (orders)

| Field | Type | Why |
|-------|------|-----|
| `id` | Keyword | `_id` |
| `customerEmail` | Keyword | exact `term` search |
| `status` | Keyword | `PENDING` / `CONFIRMED` / `CANCELLED` |
| `items` | Nested | line snapshots |
| `items.productName` | Text | optional later search by product name on the order |
| `items.productId` | Keyword | which catalog id |
| `total` | Double | qty × unit price at order time |
| `createdAt` | Date | Kibana **can** use this as a time field |

New orders start as `PENDING`. We do not build a status machine in this step.

Order search uses **only `filter` + `term`**. Email is not analyzed. `buyer@example.com` must match exactly (trim only).

## Kibana

After you place an order:

1. Dev Tools: `GET orders/_search`
2. Data view `orders` — here you **may** pick `createdAt` as the time field
3. Discover should show the order JSON next to `products`

## Files

| File | Role |
|------|------|
| `Order.java` | `@Document(indexName = "orders")` |
| `OrderItem.java` | Nested line |
| `OrderRepository.java` | CRUD |
| `OrderService.java` | stock + save + search |
| `OrderPageController.java` | `/orders` |
| `templates/orders.html` | UI |

## Verify

```powershell
gradle bootRun
```

Open `/orders`, order 1× Wireless Headphones. Catalog stock should drop. Then:

```powershell
Invoke-RestMethod http://localhost:9200/orders/_count
Invoke-RestMethod http://localhost:9200/products/_doc/seed-headphones | ConvertTo-Json -Depth 5
```
