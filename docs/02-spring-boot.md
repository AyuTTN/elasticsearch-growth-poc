# Spring Boot (theory — no code on this branch yet)

Read this before you ask for the first Java files. After the skeleton is generated, we will add a “what we generated and why” section at the bottom.

## What problem Spring Boot solves

A web API in Java needs:

- An HTTP server (almost always Tomcat, sometimes Jetty or Netty)
- JSON serialization (Jackson)
- A way to map `POST /api/products` to a method
- Dependency injection so controllers do not `new` everything
- A single runnable artifact

You *can* assemble that from Spring Framework + Tomcat JARs by hand. **Spring Boot** is an opinionated packaging of that assembly:

- **Starters** — one dependency pulls a coherent set (example: `spring-boot-starter-web`)
- **Auto-configuration** — if Tomcat is on the classpath, Boot starts Tomcat; if Elasticsearch client is on the classpath, Boot tries to connect
- **Embedded server** — no separate Tomcat install; `main()` is enough
- **Production-ready extras** (optional) — Actuator health/metrics

For this POC, Boot is the **application**. Elasticsearch is a **dependency of the application**. Mixing those two sentences is how people accidentally treat ES as “Spring.”

---

## Why Spring Boot here, not something else

| Option | What it is | Why we skip it |
|--------|------------|----------------|
| **Spring Boot** | JVM HTTP + DI + starters, including Spring Data Elasticsearch | We want this |
| Spring Framework without Boot | Same libraries, more XML/Java config | Teaching auto-config is part of the ES step |
| Quarkus / Micronaut | Also JVM, faster startup | Different docs; your growth path is Spring |
| Jakarta EE / Payara | Application server model | Heavier ops for a POC |
| Node + Express + `@elastic/elasticsearch` | Perfectly valid | Wrong language for this repo |
| Python FastAPI + elasticsearch-py | Same | Wrong language |

If the goal were “fastest search prototype,” Node or Python would be fine. The goal is **JVM + Spring Data + Kibana**.

---

## The layering we will use (classic Spring)

```text
HTTP JSON
  controller/     @RestController — URLs, status codes, @Valid
  dto/            records for request/response — not the ES document
  service/        rules (stock, “not found”)
  repository/     save/find — first in-memory, later Elasticsearch
  model/          the thing we persist
```

Why DTOs instead of exposing `Product` directly?

- HTTP clients should not send `id` / `createdAt` on create if we generate them.
- Elasticsearch annotations (`@Document`, `@Field`) do not belong in the public JSON contract.
- We can change the index mapping without breaking the API on day one.

**Alternative:** expose the entity. Faster to type, worse when the index grows fields for scoring and you leak them.

---

## Starters we will add, and when

| Starter | When | What you get | Alternative |
|---------|------|--------------|-------------|
| `spring-boot-starter-web` | First Java prompt | Tomcat, Jackson, `@RestController` | `starter-webflux` (reactive). Skip unless we need streaming backpressure. |
| `spring-boot-starter-thymeleaf` | First Java prompt | Server-rendered HTML so the POC works without Postman | React/Vue SPA or API-only |
| `spring-boot-starter-validation` | With the first POST body | Jakarta Validation (`@NotBlank`, `@Positive`) | Manual if-statements |
| `spring-boot-starter-data-elasticsearch` | **After** Docker ES | Spring Data repositories + ES Java API client | RestClient calls by hand; or no Spring Data |
| `spring-boot-starter-test` | With skeleton | JUnit 5, MockMvc | Raw JUnit only |
| `spring-boot-starter-actuator` | Optional later | `/actuator/health` | A tiny `/api/health` controller (we will likely start with that so health does not require Actuator knowledge) |
| `spring-boot-starter-data-jpa` | **Not in this POC** | Hibernate + SQL | The usual “normal app” persistence |

The important teaching moment: **adding `starter-data-elasticsearch` changes Boot’s auto-config.** The app will try to reach `localhost:9200` at startup. That is why we will first run a **web-only** app, then add ES, not the reverse.

---

## Auto-configuration (this is the “magic” people hate)

`@SpringBootApplication` means:

1. `@Configuration` — this class can define beans
2. `@EnableAutoConfiguration` — load `AutoConfiguration` classes from JARs
3. `@ComponentScan` — pick up `@RestController`, `@Service`, etc. in the same package and below

Boot looks at the **classpath**. Example:

- Jackson present → JSON converters registered
- Elasticsearch client present → `ElasticsearchClient` bean, connection to `spring.elasticsearch.uris`

There is no magic at runtime beyond “if this class exists, configure that bean.” You can always exclude auto-config, but then you own the wiring.

**Alternative:** disable auto-config and write `@Bean` methods for every client. More explicit, more code, useful when Boot’s defaults fight you (SSL, multiple clusters).

---

## Embedded Tomcat vs installing Tomcat

Boot fat JAR contains Tomcat. `bootRun` or `java -jar app.jar` listens on **8080** by default (`server.port`).

**Alternative:** WAR deployed to an external Tomcat. Common in old ops. We will not do it; Docker + fat JAR is the modern default.

---

## `application.yml` vs `application.properties`

Both work. YAML is easier for nested Elasticsearch settings:

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
server:
  port: 8080
```

**Alternative:** environment variables (`SERVER_PORT=8081`) and Docker Compose `environment:`. We will use YAML plus optional `ELASTICSEARCH_URI` when we dockerize.

---

## Package name

We will use something like `com.poc.elasticsearch` or `com.poc.growth`. The package is not “the Elasticsearch vendor.” It is our namespace. Component scan starts at the `@SpringBootApplication` class: **do not** put that class in `com.poc` and controllers in `org.example` or Boot will not see them.

---

## What “plain Spring Boot” means for the next prompt

A successful Step 2 (your second or third prompt) looks like:

- `gradle bootRun`
- No Docker
- `GET /api/health` → `{"status":"UP"}`
- Maybe in-memory `POST /api/products` + `GET /api/products/{id}`

If that requires Elasticsearch, we failed the layering.

---

## Implemented baseline (2026-09-07)

### Versions and commands

- Spring Boot: **3.5.5**
- Required global Gradle version: **9.7.1**
- Java toolchain: **21**
- Tests: `gradle test`
- Run: `gradle bootRun`
- Browser: `http://localhost:8080/products`

This project intentionally uses the globally installed `gradle` command. Any other machine or CI runner must install a compatible Gradle version and place its `bin` directory on PATH.

### Request flow

```text
GET /products
  -> ProductPageController.products()
  -> ProductService.findAll()
  -> Model attributes
  -> products.html
  -> HTML response

POST /products
  -> ProductForm field binding
  -> Jakarta Validation
  -> ProductService.create()
  -> in-memory ConcurrentHashMap
  -> redirect to GET /products
```

The redirect after a successful POST is the **Post/Redirect/Get pattern**. Refreshing the browser then repeats the GET instead of resubmitting the form.

### Why an in-memory service first

`ProductService` currently owns a `ConcurrentHashMap<Long, Product>`. This is deliberately temporary:

- It proves controllers, validation, templates, and Gradle work with no Docker.
- It creates a clean seam: later, persistence changes to Elasticsearch while the browser workflow remains familiar.
- Data disappears on restart. That is a feature at this stage, not a production design.

`ConcurrentHashMap` avoids obvious corruption if two browser requests arrive together. It does **not** provide transactions or durable storage.

### Thymeleaf boundary (intentionally not a learning topic)

Thymeleaf turns `src/main/resources/templates/products.html` plus model attributes into HTML. The controller returns the template name `products`; it does not manually concatenate HTML.

The only concepts needed to maintain this POC are:

- `th:object="${productForm}"` binds the form object.
- `th:field="*{name}"` binds one input.
- `th:each` renders every product.
- `th:errors` shows server-side validation messages.

We will not study template syntax beyond what is required to keep the page working.

### Test coverage

`ProductPageControllerTest` uses Spring's `MockMvc` to verify:

1. The page renders.
2. Invalid fields return validation errors.
3. A valid product is created, redirects, and appears in rendered HTML.

These tests exercise Spring MVC without opening a real browser or binding port 8080. They also run without Elasticsearch, proving the current layer is independent.
