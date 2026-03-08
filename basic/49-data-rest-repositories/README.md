# Data Rest Repositories

This mini-project demonstrates how to expose JPA repositories directly via **Spring Data REST**. 
Spring Data REST builds on top of Spring Data repositories and automatically analyzes your repository interfaces to create RESTful endpoints for your entities.

## 📝 Requirements

- **Java 21** or higher
- **Maven** (bundled wrapper included)

## 🚀 How to Run

1. **Clone the repository** (if you haven't already):
   ```bash
   git clone https://github.com/fabiankaraben/spring-mini-projects.git
   cd spring-mini-projects/basic/49-data-rest-repositories
   ```

2. **Run the application** using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

3. The application will start on `http://localhost:8080`.

## 🛠 Usage Examples

Spring Data REST automatically creates endpoints based on the repository. By default, the base path is configured to `/api` in `application.properties`.

### 1. List all products
```bash
curl -X GET http://localhost:8080/api/products
```

### 2. Create a new product
```bash
curl -X POST http://localhost:8080/api/products \
     -H "Content-Type: application/json" \
     -d '{ "name": "Gaming Mouse", "description": "High precision mouse", "price": 59.99 }'
```

### 3. Get a specific product (replace `{id}` with the actual ID, e.g., 1)
```bash
curl -X GET http://localhost:8080/api/products/1
```

### 4. Update a product (Partial Update using PATCH)
```bash
curl -X PATCH http://localhost:8080/api/products/1 \
     -H "Content-Type: application/json" \
     -d '{ "price": 49.99 }'
```

### 5. Search products by name
We have defined a custom query method `findByName` which is exposed under `/search`.
```bash
curl -X GET "http://localhost:8080/api/products/search/by-name?name=Gaming%20Mouse"
```

### 6. Delete a product
```bash
curl -X DELETE http://localhost:8080/api/products/1
```

## 🧪 Running Tests

This project includes:
- **Unit Tests** for the Entity (`ProductTest`).
- **Integration Tests** for the Repository using `@DataJpaTest` (`ProductRepositoryTest`).

To run the tests, execute:
```bash
./mvnw test
```

## 📚 Key Concepts

- **@RepositoryRestResource**: Used to customize the REST endpoint for a repository (e.g., changing the path or relation name).
- **HAL (Hypertext Application Language)**: Spring Data REST uses HAL by default, providing links (`_links`) to navigate the API.
- **Search Resources**: Custom query methods in the repository are automatically exposed as search resources.
