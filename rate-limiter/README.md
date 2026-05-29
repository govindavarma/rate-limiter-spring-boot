# Rate Limiter — Spring Boot Microservice

A production-style **token-bucket rate limiter** built with Java, Spring Boot, and Redis.
Each client gets a fixed number of requests per time window. When the bucket empties, further requests receive HTTP `429 Too Many Requests` with a `Retry-After` header.

---

## Architecture

```
Client Request
      │
      ▼
┌─────────────────────┐
│  RateLimiterController │  ◄── REST layer (HTTP in/out)
│  POST /api/request   │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  RateLimiterService  │  ◄── Core token-bucket logic
│  isAllowed(clientId) │
└────────┬────────────┘
         │  get/decrement/set
         ▼
┌─────────────────────┐
│       Redis          │  ◄── Token counter per client (TTL = window)
│  key: rate_limit:    │
│  value: tokenCount   │
└─────────────────────┘
```

### Token Bucket Algorithm
- Each client gets a bucket of **N tokens** (configurable).
- Every request consumes **1 token**.
- Tokens **fully refill** after the time window (Redis TTL handles this automatically).
- If tokens = 0 → **reject** with `429` + `Retry-After` header.

---

## Tech Stack

| Layer       | Technology          |
|-------------|---------------------|
| Language    | Java 17             |
| Framework   | Spring Boot 3.2     |
| Storage     | Redis               |
| Build       | Maven               |
| Testing     | JUnit 5 + Mockito   |

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Redis running on `localhost:6379`

### Install Redis (Windows)
Download from https://github.com/microsoftarchive/redis/releases  
Run `redis-server.exe`

### Install Redis (Mac)
```bash
brew install redis
brew services start redis
```

### Install Redis (Ubuntu/WSL)
```bash
sudo apt install redis-server
sudo service redis-server start
```

---

## How to Run

### Option 1 — IntelliJ IDEA
1. Open IntelliJ → **File → Open** → select the `rate-limiter` folder
2. Wait for Maven to download dependencies (bottom progress bar)
3. Open `RateLimiterApplication.java`
4. Click the **green Run ▶** button next to `main()`
5. You should see: `Started RateLimiterApplication on port 8080`

### Option 2 — Spring Tool Suite (STS)
1. Open STS → **File → Import → Maven → Existing Maven Projects**
2. Select the `rate-limiter` folder → Finish
3. Right-click project → **Run As → Spring Boot App**

### Option 3 — Terminal
```bash
cd rate-limiter
mvn spring-boot:run
```

---

## API Endpoints

### 1. Send a Rate-Limited Request
```
POST /api/request
Header: X-Client-Id: your-client-id
```

**Response — Allowed (200 OK):**
```json
{
  "allowed": true,
  "remainingTokens": 9,
  "retryAfterSeconds": 0,
  "message": "Request accepted",
  "clientId": "client_123"
}
```

**Response — Rejected (429 Too Many Requests):**
```json
{
  "allowed": false,
  "remainingTokens": 0,
  "retryAfterSeconds": 45,
  "message": "Rate limit exceeded. You have used all 10 requests. Try again in 45 seconds.",
  "clientId": "client_123"
}
```
Headers returned on 429:
```
Retry-After: 45
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Window: 60s
```

---

### 2. Check Client Token Status
```
GET /api/status/{clientId}
```
```json
{
  "clientId": "client_123",
  "remainingTokens": 7,
  "maxTokens": 10,
  "windowSeconds": 60,
  "retryAfterSeconds": 38
}
```

---

### 3. Health Check
```
GET /api/health
```
```json
{ "status": "UP", "service": "rate-limiter" }
```

---

### 4. View Config
```
GET /api/config
```
```json
{
  "maxTokensPerClient": 10,
  "windowSeconds": 60,
  "algorithm": "token-bucket",
  "storage": "Redis"
}
```

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
rate.limiter.max-tokens=10        # requests allowed per window
rate.limiter.window-seconds=60    # window duration in seconds
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## Testing with Postman

Import the collection from `postman/rate-limiter-collection.json`

**Quick test manually:**
1. Send `POST /api/request` with header `X-Client-Id: test-user` 10 times → all succeed
2. Send the 11th request → you get `429 Too Many Requests`
3. Check `GET /api/status/test-user` → `remainingTokens: 0`
4. Wait 60 seconds → bucket refills automatically

---

## Running Tests

```bash
mvn test
```

---

## Author

Govinda Varma Alluri — [LinkedIn](https://www.linkedin.com/in/govind-varma-a5a76b265) · [Portfolio](https://govindavarma.github.io/my-portfolio/)
