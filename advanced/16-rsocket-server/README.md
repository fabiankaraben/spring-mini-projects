# RSocket Server — Spring Boot Mini-Project

A Spring Boot backend implementing **reactive, backpressure-aware communication** over **RSocket**. This project demonstrates all four RSocket interaction models using a **Stock Market Ticker** domain scenario.

---

## What is RSocket?

RSocket is a binary, multiplexed, **reactive application-level protocol** designed for reactive streams with **native backpressure support** over TCP, WebSocket, or other transport layers.

### Key advantages over REST

| Feature | REST (HTTP/1.1) | RSocket |
|---|---|---|
| Interaction models | Request-Response only | 4 models (see below) |
| Backpressure | Application-level only | **Protocol-level** (REQUEST_N) |
| Transport | Text (HTTP headers) | Binary (efficient framing) |
| Streaming | Polling / SSE (workaround) | Native Request-Stream |
| Bidirectional | WebSocket (separate) | Native Request-Channel |
| Reconnection | Not built-in | **Resume capability** |

### The four RSocket interaction models

| Model | Direction | Return type | Route in this project |
|---|---|---|---|
| **Request-Response** | 1 request → 1 response | `Mono<T>` | `stock.quote` |
| **Request-Stream** | 1 request → N responses | `Flux<T>` | `stock.stream` |
| **Fire-and-Forget** | 1 request → 0 responses | `Mono<Void>` | `stock.trade` |
| **Request-Channel** | N requests ↔ N responses | `Flux<T>` ← `Flux<R>` | `stock.alerts` |

---

## Domain Scenario: Stock Market Ticker

Five stock symbols are pre-seeded on startup:

| Symbol | Company | Price (USD) |
|---|---|---|
| AAPL | Apple Inc. | 215.50 |
| GOOG | Alphabet Inc. | 178.30 |
| MSFT | Microsoft Corporation | 420.75 |
| AMZN | Amazon.com Inc. | 198.40 |
| TSLA | Tesla Inc. | 248.90 |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.8+ (or use the included `./mvnw` wrapper) |
| Docker | 20+ (for Docker Compose deployment) |
| Docker Compose | 2.x (uses `docker compose` command) |

---

## Project Structure

```
src/main/java/com/example/rsocket/
├── RSocketServerApplication.java       # Spring Boot entry point
├── config/
│   └── DataInitializer.java            # Seeds H2 with sample stock quotes
├── controller/
│   └── StockMarketController.java      # @MessageMapping route handlers (all 4 models)
├── domain/
│   ├── StockQuote.java                 # JPA entity — stock price quote
│   ├── TradeRecord.java                # JPA entity — trade event (buy/sell)
│   └── TradeType.java                  # Enum: BUY | SELL
├── dto/
│   ├── QuoteRequest.java               # Client → server: request a quote
│   ├── QuoteResponse.java              # Server → client: quote data
│   ├── TradeRequest.java               # Client → server: submit a trade
│   ├── PriceAlertRequest.java          # Client → server: watch a symbol
│   └── PriceAlertResponse.java         # Server → client: price alert notification
├── repository/
│   ├── StockQuoteRepository.java       # Spring Data JPA for quotes
│   └── TradeRecordRepository.java      # Spring Data JPA for trades
└── service/
    └── StockMarketService.java         # Domain business logic (returns Mono/Flux)

src/test/java/com/example/rsocket/
├── integration/
│   └── StockMarketIntegrationTest.java # Full stack integration tests (Testcontainers)
└── service/
    └── StockMarketServiceTest.java     # Unit tests (JUnit 5 + Mockito + StepVerifier)
```

---

## Port Layout

| Port | Protocol | Purpose |
|---|---|---|
| `7000` | RSocket/TCP | All four interaction model routes |
| `8080` | HTTP | Spring Actuator (`/actuator/health`, `/actuator/info`) |

---

## Running with Docker Compose

The entire project is containerised and can be run via Docker Compose.

### Start the application

```bash
# Build the Docker image and start the container
docker compose up --build

# Or start in detached (background) mode
docker compose up --build -d
```

### Stop the application

```bash
docker compose down
```

### View logs

```bash
docker compose logs -f
```

### Health check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

## Running Locally (without Docker)

```bash
./mvnw spring-boot:run
```

The application starts on:
- RSocket TCP: `localhost:7000`
- HTTP Actuator: `localhost:8080`

---

## Interacting with the RSocket Server

RSocket uses a binary protocol — you cannot use `curl` directly. Instead, use **`rsc`** (RSocket CLI), which is the standard tool for interacting with RSocket servers from the command line.

### Install rsc (RSocket CLI)

```bash
# macOS via Homebrew
brew install rsc

# Or download the JAR from GitHub releases:
# https://github.com/making/rsc/releases
# Then run with: java -jar rsc.jar <args>
```

---

### 1. Request-Response — `stock.quote`

Fetch the latest price for a stock symbol.

```bash
rsc --request \
    --route stock.quote \
    --data '{"symbol":"AAPL"}' \
    --mime-type application/json \
    tcp://localhost:7000
```

Expected response:
```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "price": 215.50,
  "change": 3.25,
  "changePercent": 1.53,
  "volume": 72345100,
  "timestamp": "2024-03-25T10:00:00Z"
}
```

Fetch quotes for other symbols:
```bash
rsc --request --route stock.quote --data '{"symbol":"GOOG"}' --mime-type application/json tcp://localhost:7000
rsc --request --route stock.quote --data '{"symbol":"TSLA"}' --mime-type application/json tcp://localhost:7000
```

Unknown symbol (returns empty response):
```bash
rsc --request --route stock.quote --data '{"symbol":"UNKN"}' --mime-type application/json tcp://localhost:7000
```

---

### 2. Request-Stream — `stock.stream`

Subscribe to a live price stream (one quote per second, simulated random walk).

```bash
# Stream 5 quotes for MSFT, then stop
rsc --stream \
    --route stock.stream \
    --data '{"symbol":"MSFT"}' \
    --mime-type application/json \
    --take 5 \
    tcp://localhost:7000
```

Stream indefinitely (press Ctrl+C to stop):
```bash
rsc --stream \
    --route stock.stream \
    --data '{"symbol":"AAPL"}' \
    --mime-type application/json \
    tcp://localhost:7000
```

---

### 3. Fire-and-Forget — `stock.trade`

Submit a trade event. The server logs it and sends **no response**.

```bash
# Submit a BUY order
rsc --fnf \
    --route stock.trade \
    --data '{"symbol":"AAPL","tradeType":"BUY","quantity":100,"executionPrice":215.50,"traderId":"trader-001"}' \
    --mime-type application/json \
    tcp://localhost:7000

# Submit a SELL order
rsc --fnf \
    --route stock.trade \
    --data '{"symbol":"TSLA","tradeType":"SELL","quantity":50,"executionPrice":248.90,"traderId":"trader-001"}' \
    --mime-type application/json \
    tcp://localhost:7000
```

Verify trades were recorded (via H2 console at `http://localhost:8080/h2-console`):
- JDBC URL: `jdbc:h2:mem:stockmarket`
- Username: `sa` / Password: *(empty)*
- Query: `SELECT * FROM TRADE_RECORDS`

---

### 4. Request-Channel — `stock.alerts`

Submit a stream of watchlist subscriptions, receive price alert notifications when thresholds are crossed.

```bash
# Watch AAPL (threshold $200) and GOOG (threshold $200)
# AAPL at $215.50 will trigger; GOOG at $178.30 will NOT
rsc --channel \
    --route stock.alerts \
    --data '{"symbol":"AAPL","thresholdPrice":200.00}
{"symbol":"GOOG","thresholdPrice":200.00}
{"symbol":"MSFT","thresholdPrice":400.00}' \
    --mime-type application/json \
    tcp://localhost:7000
```

Expected output (two alerts — for AAPL and MSFT):
```json
{"symbol":"AAPL","thresholdPrice":200.0,"currentPrice":215.5,"message":"AAPL crossed $200.00 threshold at $215.50","alertTime":"..."}
{"symbol":"MSFT","thresholdPrice":400.0,"currentPrice":420.75,"message":"MSFT crossed $400.00 threshold at $420.75","alertTime":"..."}
```

---

## Running the Tests

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest=StockMarketServiceTest
```

### Run only integration tests

```bash
./mvnw test -Dtest=StockMarketIntegrationTest
```

---

## Test Architecture

### Unit Tests — `StockMarketServiceTest`

Tests the service layer **in isolation** — no Spring context, no database, no RSocket server.

| Test class | Description |
|---|---|
| `GetLatestQuote` | Request-Response logic: found, not found, symbol normalization |
| `StreamQuotes` | Request-Stream logic: symbol, positive prices, default seed |
| `LogTrade` | Fire-and-Forget logic: BUY, SELL, invalid type, uppercase normalization |
| `EvaluatePriceAlerts` | Request-Channel logic: triggered/not triggered, equality, multiple symbols |
| `ToQuoteResponse` | Entity → DTO mapping |

**Tools used:**
- **JUnit 5** — test framework (`@Nested`, `@DisplayName`, `@Test`)
- **Mockito** — mock `StockQuoteRepository` and `TradeRecordRepository`
- **StepVerifier** (reactor-test) — subscribe to `Mono`/`Flux` and assert emissions

### Integration Tests — `StockMarketIntegrationTest`

Tests the **full stack** end-to-end — full Spring Boot context + RSocket server + H2 database.

| Test | RSocket model | Route |
|---|---|---|
| `requestResponse_getQuote_returnsLatestPrice` | Request-Response | `stock.quote` |
| `requestResponse_getQuote_returnsEmptyForUnknownSymbol` | Request-Response | `stock.quote` |
| `requestResponse_getQuote_returnsDataForAllSeededSymbols` | Request-Response | `stock.quote` |
| `requestStream_streamQuotes_emitsMultipleItems` | Request-Stream | `stock.stream` |
| `requestStream_streamQuotes_itemsHaveTimestamps` | Request-Stream | `stock.stream` |
| `fireAndForget_logTrade_persistsBuyTrade` | Fire-and-Forget | `stock.trade` |
| `fireAndForget_logTrade_persistsSellTrade` | Fire-and-Forget | `stock.trade` |
| `requestChannel_priceAlerts_emitsAlertWhenPriceMeetsThreshold` | Request-Channel | `stock.alerts` |
| `requestChannel_priceAlerts_noAlertWhenPriceBelowThreshold` | Request-Channel | `stock.alerts` |
| `requestChannel_priceAlerts_processesMultipleSymbols` | Request-Channel | `stock.alerts` |
| `requestChannel_priceAlerts_emptyWatchlistCompletesNormally` | Request-Channel | `stock.alerts` |

**Tools used:**
- **@SpringBootTest** — starts the full application on a random HTTP port
- **RSocketRequester** — Spring's high-level RSocket client used by integration tests
- **@Testcontainers** — validates Docker daemon availability and manages lifecycle hooks
- **StepVerifier** — asserts reactive stream emissions end-to-end

---

## Technology Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.4.x |
| RSocket | `spring-boot-starter-rsocket` (Netty transport) |
| Reactive streams | Project Reactor (`Mono`, `Flux`) |
| Persistence | Spring Data JPA + H2 (in-memory) |
| Web / Health | Spring Boot Actuator + Spring MVC |
| Build | Maven (with Maven Wrapper) |
| Unit testing | JUnit 5 + Mockito + StepVerifier |
| Integration testing | Testcontainers + RSocketRequester + StepVerifier |
| Container | Docker + Docker Compose |
| Java version | 21+ |
