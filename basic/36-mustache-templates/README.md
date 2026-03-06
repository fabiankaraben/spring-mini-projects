# 36 – Mustache Templates

A Spring Boot mini-project that renders dynamic HTML pages using the **Mustache** template engine.

---

## What this project demonstrates

| Concept | Description |
|---|---|
| `spring-boot-starter-mustache` | Auto-configures the JMustache engine and a `MustacheViewResolver` |
| `@Controller` vs `@RestController` | `@Controller` returns a view name; Mustache resolves it to a `.mustache` file |
| Mustache sections `{{#key}}...{{/key}}` | Iterates a list **or** renders a block when a boolean is `true` |
| Mustache inverted sections `{{^key}}...{{/key}}` | Renders when a value is `false` or a list is empty |
| Mustache object sections | `{{#product}}...{{/product}}` enters the scope of a single object |
| Pre-computed model attributes | Mustache has no expression language; all logic lives in Java |
| `formattedPrice()` helper | Number formatting done on the Java side, exposed as a plain `String` |
| `CategoryItem` DTO | Pre-computes the `active` boolean so the template avoids any logic |
| `@WebMvcTest` | Sliced integration test that renders real templates via `MockMvc` |
| `@MockitoBean` | Replaces the service with a Mockito mock in the sliced context |

---

## Why Mustache?

Mustache is a **logic-less** template language — it deliberately has no expression language, no method calls, and no conditionals beyond simple true/false checks. This forces a clean separation of concerns: all decisions are made in Java; the template only formats the data it receives.

Key differences from FreeMarker/Thymeleaf:

- **No `product.name()` call syntax** — Mustache resolves `{{name}}` by calling `name()` or `getName()` on the current object automatically.
- **No built-in formatters** — format numbers, dates, etc. in Java and pass the result as a `String`.
- **No conditional expressions** — use a pre-computed `boolean` model attribute and a section tag.
- **No `.size()` calls** — expose the count as a separate model attribute.

---

## Requirements

| Tool | Minimum version |
|---|---|
| Java | 21 |
| Maven | 3.9 (or use the bundled `./mvnw`) |

No Docker is required — this project uses only an in-memory product catalog.

---

## Project structure

```
36-mustache-templates/
├── src/
│   ├── main/
│   │   ├── java/com/example/mustache/
│   │   │   ├── MustacheTemplatesApplication.java   # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java          # @Controller — 3 routes
│   │   │   ├── model/
│   │   │   │   └── Product.java                    # Java record + formattedPrice()
│   │   │   └── service/
│   │   │       └── ProductService.java             # In-memory product catalog
│   │   └── resources/
│   │       ├── application.yml
│   │       └── templates/
│   │           ├── index.mustache          # Product catalog (list + filter)
│   │           ├── product-detail.mustache # Single product detail
│   │           ├── in-stock.mustache       # Only available products
│   │           └── not-found.mustache      # 404-style page
│   └── test/
│       └── java/com/example/mustache/
│           ├── MustacheTemplatesApplicationTests.java  # Context smoke test
│           ├── controller/
│           │   └── ProductControllerTest.java          # @WebMvcTest sliced tests
│           └── service/
│               └── ProductServiceUnitTest.java         # Pure unit tests
├── pom.xml
├── mvnw / mvnw.cmd
└── README.md
```

---

## Running the application

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Endpoints and curl examples

### `GET /` — Product catalog

Returns all products as an HTML page.

```bash
curl http://localhost:8080/
```

Filter by category:

```bash
curl "http://localhost:8080/?category=Electronics"
curl "http://localhost:8080/?category=Books"
curl "http://localhost:8080/?category=Office"
```

---

### `GET /products/{id}` — Product detail

Returns the detail page for a single product.

```bash
# Existing product
curl http://localhost:8080/products/1

# Products 1–8 are pre-loaded
curl http://localhost:8080/products/4

# Non-existent product — renders the not-found page
curl http://localhost:8080/products/999
```

---

### `GET /products/in-stock` — In-stock products

Returns only the products that are currently available.

```bash
curl http://localhost:8080/products/in-stock
```

---

## Running the tests

```bash
./mvnw test
```

Expected output:

```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Test breakdown

| Test class | Type | What it tests |
|---|---|---|
| `ProductServiceUnitTest` | Unit test (no Spring) | All `ProductService` methods + `Product.formattedPrice()` |
| `ProductControllerTest` | `@WebMvcTest` sliced integration | HTTP routes, model attributes, rendered HTML content |
| `MustacheTemplatesApplicationTests` | Full context smoke test | Application context starts without errors |

### Key testing patterns

**`@WebMvcTest` — sliced integration test**

Starts only the web layer (Spring MVC + Mustache `ViewResolver`). The real `ProductService` is replaced with a `@MockitoBean`.

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getIndex_noFilter_returns200AndIndexView() throws Exception {
        when(productService.findAll()).thenReturn(List.of(...));

        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(view().name("index"))
               .andExpect(content().string(containsString("Wireless Headphones")));
    }
}
```

> **Note:** `@MockitoBean` from `org.springframework.test.context.bean.override.mockito` is used instead of the deprecated `@MockBean`.

**Unit test — no Spring context**

```java
class ProductServiceUnitTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
    }

    @Test
    void findAll_returnsAllEightProducts() {
        assertThat(productService.findAll()).hasSize(8);
    }
}
```

---

## Mustache syntax reference

| Syntax | Meaning |
|---|---|
| `{{name}}` | Outputs the value of `name`, HTML-escaped |
| `{{{html}}}` | Outputs raw (unescaped) HTML |
| `{{#products}}...{{/products}}` | Iterates over `products` list **or** renders once if truthy boolean |
| `{{^products}}...{{/products}}` | Renders when `products` is empty or falsy (inverted section) |
| `{{#inStock}}...{{/inStock}}` | Renders when `inStock()` returns `true` (boolean section) |
| `{{^inStock}}...{{/inStock}}` | Renders when `inStock()` returns `false` (inverted boolean section) |
| `{{! comment }}` | Mustache comment — not included in output |
| `{{.}}` | Current item in a scalar list iteration |

### JMustache-specific notes

1. **Do not write `{{...}}` syntax inside HTML comments or other text** — JMustache scans the entire template file for `{{` delimiters and will try to evaluate any tag it encounters, even inside `<!-- HTML comments -->`.

2. **Record component resolution** — `{{name}}` resolves `product.name()` on the current context object. No explicit call syntax is needed (unlike FreeMarker which requires `${product.name()}`).

3. **Number formatting** — Use a helper method like `formattedPrice()` on the model object to pre-format numbers. Mustache has no built-in formatters.

4. **Boolean attributes** — To conditionally add a CSS class, pre-compute a boolean in Java and expose it as a model attribute. Then use `{{#flag}} class-name{{/flag}}` in the template.
