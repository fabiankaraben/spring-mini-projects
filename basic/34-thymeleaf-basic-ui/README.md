# 34 – Thymeleaf Basic UI

A Spring Boot backend that renders **dynamic HTML pages** using the [Thymeleaf](https://www.thymeleaf.org/) template engine.  
No REST/JSON is involved — the server returns fully-rendered HTML to the browser.

---

## What is this?

Thymeleaf is a server-side Java template engine that processes HTML files and replaces special `th:*` attributes with runtime values before sending the page to the browser.  
This project demonstrates the core Thymeleaf concepts:

| Concept | Example |
|---|---|
| `th:text` | Replace element text with a model value |
| `th:each` | Iterate over a list |
| `th:if` / `th:unless` | Conditional rendering |
| `th:href` with `@{...}` | Context-relative URL building |
| `th:classappend` | Conditionally append a CSS class |
| `#lists`, `#numbers` utility objects | Format values in expressions |
| Iteration status variable | Access index, count, even/odd in loops |

The product data is held in a **hardcoded in-memory list** (no database), keeping the focus entirely on Thymeleaf.

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/thymeleafbasicui/
│   │   ├── ThymeleafBasicUiApplication.java  # Entry point
│   │   ├── controller/
│   │   │   └── ProductController.java        # @Controller — returns view names
│   │   ├── model/
│   │   │   └── Product.java                  # Immutable record (view model)
│   │   └── service/
│   │       └── ProductService.java           # In-memory product data
│   └── resources/
│       ├── application.yml                   # Server + Thymeleaf config
│       └── templates/                        # Thymeleaf HTML templates
│           ├── index.html                    # Product catalog with filter
│           ├── product-detail.html           # Single product detail
│           ├── in-stock.html                 # In-stock product table
│           └── not-found.html               # Not-found page
└── test/
    └── java/com/example/thymeleafbasicui/
        ├── ThymeleafBasicUiApplicationTests.java          # Context smoke test
        ├── controller/
        │   └── ProductControllerTest.java                 # @WebMvcTest sliced test
        └── service/
            └── ProductServiceUnitTest.java                # Pure unit tests
```

---

## Requirements

| Tool | Version |
|---|---|
| Java | 21 or higher |
| Maven | 3.9+ (or use the included Maven Wrapper) |

> **Docker is not required.** Thymeleaf is a pure Java library — there are no external services needed to run this project.

---

## How to run

### Using the Maven Wrapper (recommended)

```bash
./mvnw spring-boot:run
```

### Using system Maven

```bash
mvn spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## Pages & endpoints

| URL | Description |
|---|---|
| `GET /` | Product catalog (all products) |
| `GET /?category=Electronics` | Catalog filtered by category |
| `GET /?category=Books` | Catalog filtered by Books category |
| `GET /?category=Office` | Catalog filtered by Office category |
| `GET /products/{id}` | Detail page for a specific product |
| `GET /products/in-stock` | Table of in-stock products only |

> These are **HTML pages**, not JSON endpoints. Open them in a browser for the full experience, or use `curl` to see the rendered HTML source.

---

## curl examples

### Home page — all products

```bash
curl http://localhost:8080/
```

### Catalog filtered by category

```bash
curl "http://localhost:8080/?category=Electronics"
```

```bash
curl "http://localhost:8080/?category=Books"
```

### Product detail page

```bash
curl http://localhost:8080/products/1
```

```bash
curl http://localhost:8080/products/4
```

### Not-found page (ID does not exist)

```bash
curl http://localhost:8080/products/999
```

### In-stock products table

```bash
curl http://localhost:8080/products/in-stock
```

---

## Running the tests

### Run all tests

```bash
./mvnw test
```

### Run only unit tests (no Spring context)

```bash
./mvnw test -Dtest=ProductServiceUnitTest
```

### Run only the sliced web-layer integration tests

```bash
./mvnw test -Dtest=ProductControllerTest
```

### Run only the smoke test

```bash
./mvnw test -Dtest=ThymeleafBasicUiApplicationTests
```

---

## Test strategy

| Test class | Type | Description |
|---|---|---|
| `ThymeleafBasicUiApplicationTests` | Integration (full context) | Verifies the Spring application context starts cleanly |
| `ProductControllerTest` | Sliced integration (`@WebMvcTest`) | Tests the web layer (routing, model, rendered HTML) with the service mocked via `@MockitoBean` |
| `ProductServiceUnitTest` | Unit | Tests all service methods in pure Java — no Spring context, no mocks needed |

### Test counts

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

- **1** context smoke test
- **6** controller sliced integration tests  
- **9** service unit tests

---

## Key Spring / Thymeleaf concepts

### `@Controller` vs `@RestController`

```java
// @RestController — writes return value directly to the HTTP response body (JSON)
@RestController
public class ApiController {
    @GetMapping("/api/products")
    public List<Product> list() { return service.findAll(); } // → JSON array
}

// @Controller — treats the return value as a VIEW NAME resolved by Thymeleaf
@Controller
public class ProductController {
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("products", service.findAll());
        return "index"; // → resolves to templates/index.html
    }
}
```

### Model attributes in templates

```java
// Controller
model.addAttribute("products", service.findAll());
```

```html
<!-- Template -->
<div th:each="product : ${products}">
    <span th:text="${product.name}">Placeholder</span>
</div>
```

### Thymeleaf URL expressions

```html
<!-- Context-relative link (safer than hard-coding "/") -->
<a th:href="@{/}">Home</a>

<!-- Path variable -->
<a th:href="@{/products/{id}(id=${product.id})}">Detail</a>

<!-- Query parameter -->
<a th:href="@{/(category=${cat})}">Filter</a>
```
