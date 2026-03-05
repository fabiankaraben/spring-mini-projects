# 35 — Freemarker Templates

A Spring Boot mini-project that renders **dynamic HTML pages** using the
[Apache FreeMarker](https://freemarker.apache.org/) template engine.

FreeMarker is a classic Java server-side template language. It processes
`.ftlh` (FreeMarker Template Language for HTML) files stored in
`src/main/resources/templates/`, substituting `${variable}` interpolations
and executing `<#list>`, `<#if>`, and other **FTL directives** at request time
before sending the rendered HTML to the browser.

---

## Table of Contents

1. [What This Project Demonstrates](#what-this-project-demonstrates)
2. [Requirements](#requirements)
3. [Project Structure](#project-structure)
4. [How to Run](#how-to-run)
5. [API Endpoints & curl Examples](#api-endpoints--curl-examples)
6. [How to Run the Tests](#how-to-run-the-tests)
7. [Key FreeMarker Concepts](#key-freemarker-concepts)

---

## What This Project Demonstrates

| Concept | Where to look |
|---------|---------------|
| `@Controller` returning a **view name** | `ProductController.java` |
| Spring `Model` → FreeMarker **data-model** | `ProductController.java` |
| FreeMarker `${...}` interpolation | all `.ftlh` templates |
| FreeMarker `<#list>` loop directive | `index.ftlh`, `in-stock.ftlh` |
| FreeMarker `<#if>` / `<#else>` conditional | all `.ftlh` templates |
| FreeMarker built-ins (`?html`, `?size`, `?string`, `?has_content`, `?url`) | `index.ftlh` |
| Template file extension `.ftlh` for HTML output | `src/main/resources/templates/` |
| `@WebMvcTest` sliced integration test | `ProductControllerTest.java` |
| `@MockitoBean` (replaces deprecated `@MockBean`) | `ProductControllerTest.java` |
| Pure JUnit 5 unit test (no Spring context) | `ProductServiceUnitTest.java` |

---

## Requirements

| Tool | Minimum version |
|------|-----------------|
| Java | 21 |
| Maven | 3.9+ (or use the included Maven Wrapper) |

> **No Docker required.** This project uses an embedded Tomcat server and an
> in-memory product list — no external services are needed.

---

## Project Structure

```
35-freemarker-templates/
├── src/
│   ├── main/
│   │   ├── java/com/example/freemarker/
│   │   │   ├── FreemarkerTemplatesApplication.java   # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java            # @Controller — maps URLs to view names
│   │   │   ├── model/
│   │   │   │   └── Product.java                      # Java record used as view-model DTO
│   │   │   └── service/
│   │   │       └── ProductService.java               # In-memory product catalog
│   │   └── resources/
│   │       ├── application.yml                       # FreeMarker & server configuration
│   │       └── templates/
│   │           ├── index.ftlh                        # Product catalog page
│   │           ├── product-detail.ftlh               # Single-product detail page
│   │           ├── in-stock.ftlh                     # In-stock filter page
│   │           └── not-found.ftlh                    # 404 / not-found page
│   └── test/
│       └── java/com/example/freemarker/
│           ├── FreemarkerTemplatesApplicationTests.java   # Context smoke test
│           ├── controller/
│           │   └── ProductControllerTest.java             # @WebMvcTest sliced tests
│           └── service/
│               └── ProductServiceUnitTest.java            # JUnit 5 unit tests
├── .gitignore
├── mvnw / mvnw.cmd                                   # Maven Wrapper scripts
├── pom.xml
└── README.md
```

---

## How to Run

### Using the Maven Wrapper (recommended)

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### Using a local Maven installation

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## API Endpoints & curl Examples

All endpoints return **rendered HTML** (not JSON). Use a browser for the best
experience, or `curl` as shown below.

### `GET /` — Product Catalog (all products)

```bash
curl -s http://localhost:8080/ | grep "card-title"
```

### `GET /?category=Electronics` — Filter by category

```bash
curl -s "http://localhost:8080/?category=Electronics" | grep "card-title"
```

### `GET /?category=Books` — Filter by Books

```bash
curl -s "http://localhost:8080/?category=Books" | grep "card-title"
```

### `GET /products/{id}` — Product detail page

```bash
# Existing product (ID 1)
curl -s http://localhost:8080/products/1 | grep "<h1>"

# Non-existing product (renders not-found.ftlh)
curl -s http://localhost:8080/products/999 | grep "Not Found"
```

### `GET /products/in-stock` — In-stock products only

```bash
curl -s http://localhost:8080/products/in-stock | grep "items"
```

---

## How to Run the Tests

```bash
# Run all tests (unit + sliced integration)
./mvnw test

# Run only the unit tests
./mvnw test -Dtest=ProductServiceUnitTest

# Run only the @WebMvcTest integration tests
./mvnw test -Dtest=ProductControllerTest

# Run the context smoke test
./mvnw test -Dtest=FreemarkerTemplatesApplicationTests
```

### Test summary

| Test class | Type | What it tests |
|-----------|------|---------------|
| `FreemarkerTemplatesApplicationTests` | Spring Boot (full context) | Application context starts successfully |
| `ProductServiceUnitTest` | JUnit 5 (no Spring) | `findAll`, `findById`, `findInStock`, `findByCategory` in isolation |
| `ProductControllerTest` | `@WebMvcTest` (web layer only) | HTTP responses, view names, model attributes, rendered HTML content |

---

## Key FreeMarker Concepts

### Template file extension

FreeMarker HTML templates use the `.ftlh` extension (since FreeMarker 2.3.24).
The `H` reminds editors to apply HTML syntax highlighting alongside FTL
directives. The suffix is configured in `application.yml`:

```yaml
spring:
  freemarker:
    suffix: .ftlh
```

### Interpolation

```ftl
${product.name}         <#-- calls product.name() on the Java record -->
${product.price?string("0.00")}  <#-- formats a double with 2 decimal places -->
${someText?html}        <#-- escapes HTML special chars for XSS safety -->
```

### Directives

```ftl
<#-- conditional -->
<#if product.inStock>In Stock<#else>Out of Stock</#if>

<#-- loop -->
<#list products as product>
    <div>${product.name?html}</div>
</#list>

<#-- guard against empty/null list -->
<#if products?has_content>
    ...
</#if>
```

### Built-ins

| Built-in | Purpose |
|----------|---------|
| `?html` | Escape HTML special characters |
| `?url` | URL-encode a string for use in hrefs |
| `?size` | Number of elements in a sequence |
| `?has_content` | `false` for null, empty string, or empty list |
| `?string("0.00")` | Format a number with a Java `DecimalFormat` pattern |

### Java Records and FreeMarker — Important Gotcha

Java `record` components expose **non-JavaBean accessors** (e.g. `name()` instead
of `getName()`). FreeMarker's default object wrapper resolves `${product.name}`
to a `method+sequence` wrapper and **cannot coerce it to a string**, producing a
runtime error:

```
Expected a string but this has evaluated to a method+sequence (wrapper: f.e.b.SimpleMethodModel)
```

**Fix:** always call record accessors with **explicit parentheses** in FTL:

```ftl
${product.name()}      ✔ correct — calls the record accessor explicitly
${product.name}        ✘ wrong   — FreeMarker cannot coerce a method reference to string
```

This is distinct from regular JavaBeans, where FreeMarker resolves
`product.name` → `product.getName()` transparently.

---

### FreeMarker vs Thymeleaf

| Aspect | FreeMarker | Thymeleaf |
|--------|-----------|-----------|
| Syntax | Custom directive tags (`<#if>`, `<#list>`) | HTML attributes (`th:if`, `th:each`) |
| File is valid HTML without server? | No (directives break plain-HTML view) | Yes (HTML fallback values) |
| Expression language | FTL (`${product.name()}`) | Spring EL (`${product.name}`) |
| Default extension | `.ftlh` (HTML), `.ftl` (general) | `.html` |
| Java record support | Requires `product.name()` syntax | Works with `${product.name}` |
| Directives in HTML comments | Parsed and executed (use `<#-- -->`) | Ignored (standard `<!-- -->` is fine) |
| Directives inside attr values | Not allowed — use `<#assign>` + `${}` | Supported via `th:class` etc. |
