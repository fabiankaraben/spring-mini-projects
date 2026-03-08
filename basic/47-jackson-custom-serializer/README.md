# Jackson Custom Serializer

## 🔹 Project Description
This is a backend in Spring Boot creating custom Jackson serializers/deserializers. It demonstrates how to customize the JSON representation of Java objects, specifically handling currency formatting where an Integer (cents) is serialized to a String (e.g., "$10.00") and vice versa.

## 📦 Dependency Manager
- **Maven**

## 📋 Requirements
- **Java 21**
- **Spring Boot 3.4.1**

## 🚀 How to Use

### 1. Build and Run the Application
```bash
./mvnw spring-boot:run
```

### 2. Test with Curl

#### Get Example Product (Serialization)
Retrieve a product to see how the integer price is serialized into a formatted currency string.
```bash
curl -X GET http://localhost:8080/products/example
```
**Expected Response:**
```json
{
  "name": "Premium Headphones",
  "priceInCents": "$299.99",
  "category": "Electronics"
}
```

#### Create Product (Deserialization)
Send a product with a formatted price string to see how it is deserialized into an integer.
```bash
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Gaming Mouse",
    "priceInCents": "$49.50",
    "category": "Electronics"
  }'
```
**Expected Response:**
```json
{
  "name": "Gaming Mouse",
  "priceInCents": "$49.50",
  "category": "Electronics"
}
```

## 🧪 Running Tests
This project includes Unit tests using JUnit 5 and Mockito, and Sliced Integration Testing using `@WebMvcTest`.

To run the tests:
```bash
./mvnw test
```
