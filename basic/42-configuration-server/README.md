# Configuration Server

This mini-project demonstrates how to set up a Spring Cloud Config Server using the **Native** profile. This means the configuration files are loaded from the local classpath (or filesystem) instead of a Git repository, which is useful for local development and testing.

## Description

The Configuration Server acts as a central place to manage external properties for applications across all environments. In this example, it serves properties for a hypothetical client application named `client-app` with different profiles (`default`, `dev`, `prod`).

## Requirements

- Java 21+
- Maven (wrapper included)

## Project Structure

- **ConfigServerApplication.java**: The main entry point, annotated with `@EnableConfigServer`.
- **application.properties**: Configures the server to run on port 8888 and use the native profile to search for config files in `classpath:/config`.
- **resources/config/**: Contains the sample configuration files:
    - `client-app.properties` (Default)
    - `client-app-dev.properties` (Development)
    - `client-app-prod.properties` (Production)

## How to Run

1.  **Build and Run the application:**

    ```bash
    ./mvnw spring-boot:run
    ```

    The server will start on port `8888`.

## How to Use

You can access the configuration properties via HTTP. The URL format is `/{application}/{profile}`.

### Examples

**1. Fetch Default Configuration:**

```bash
curl http://localhost:8888/client-app/default
```

*Expected Output (partial):*
```json
{
  "name": "client-app",
  "profiles": [
    "default"
  ],
  "propertySources": [
    {
      "name": "classpath:/config/client-app.properties",
      "source": {
        "user.role": "Developer",
        "feature.toggles.beta": "false",
        "app.welcome.message": "Welcome to the Default Environment"
      }
    }
  ]
}
```

**2. Fetch Development Configuration:**

```bash
curl http://localhost:8888/client-app/dev
```

*Expected Output (partial):*
```json
{
  "name": "client-app",
  "profiles": [
    "dev"
  ],
  "propertySources": [
    {
      "name": "classpath:/config/client-app-dev.properties",
      "source": {
        "user.role": "Dev-Admin",
        "feature.toggles.beta": "true",
        "app.welcome.message": "Welcome to the Development Environment",
        "logging.level.root": "DEBUG"
      }
    },
    {
      "name": "classpath:/config/client-app.properties",
      "source": {
        "user.role": "Developer",
        "feature.toggles.beta": "false",
        "app.welcome.message": "Welcome to the Default Environment"
      }
    }
  ]
}
```

**3. Fetch Production Configuration:**

```bash
curl http://localhost:8888/client-app/prod
```

*Expected Output (partial):*
```json
{
  "name": "client-app",
  "profiles": [
    "prod"
  ],
  "propertySources": [
    {
      "name": "classpath:/config/client-app-prod.properties",
      "source": {
        "user.role": "User",
        "feature.toggles.beta": "false",
        "app.welcome.message": "Welcome to the Production Environment",
        "logging.level.root": "WARN"
      }
    },
    {
      "name": "classpath:/config/client-app.properties",
      "source": {
        "user.role": "Developer",
        "feature.toggles.beta": "false",
        "app.welcome.message": "Welcome to the Default Environment"
      }
    }
  ]
}
```

## Running Tests

This project includes integration tests that verify the config server is correctly serving properties for different profiles.

To run the tests:

```bash
./mvnw test
```
