🚀 Trading Simulator API
A backend trading simulator built with Spring Boot, enabling users to:

Register and log in securely with JWT authentication.

Place buy and sell orders for stocks.

Track their portfolio holdings and balances.

Fetch real-time stock prices via Alpha Vantage API.

🛠 Tech Stack
Spring Boot (REST API)

Spring Security with JWT

PostgreSQL

JPA/Hibernate

Mockito & JUnit 5 (testing)

Swagger / OpenAPI (API docs)

📦 Features
✅ User Authentication

Register and log in

Secure endpoints via JWT

Role-based authorization

✅ Trading Operations

Place buy/sell orders

Check real-time prices

Enforce balance checks

Validate sufficient holdings for sells

✅ Portfolio Management

View current holdings

Track order history

✅ Documentation

Interactive Swagger UI

Clear API contracts

✅ Testing

Unit tests (Mockito)

Integration tests (Spring Test)

H2 in-memory DB for tests

📈 API Endpoints
User
bash
Copy
Edit
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/users/{id}/balance
Prices
bash
Copy
Edit
GET /api/v1/prices/{ticker}
Response:

json
Copy
Edit
{
  "ticker": "AAPL",
  "price": 185.23,
  "timestamp": "2025-07-06T15:30:45Z"
}
Orders
bash
Copy
Edit
POST /api/v1/orders
Sample request body:

json
Copy
Edit
{
  "userId": "2",
  "ticker": "AAPL",
  "orderType": "BUY",
  "quantity": 5,
  "budget": 1000
}
✅ Automatically:

Checks available balance

Validates holdings

Updates balance and holdings

Stores the order in DB

Holdings
bash
Copy
Edit
GET /api/v1/holdings/{userId}
Response:

json
Copy
Edit
[
  {
    "ticker": "AAPL",
    "quantity": 12
  },
  {
    "ticker": "TSLA",
    "quantity": 7
  }
]
✅ Architectural Overview
scss
Copy
Edit
Frontend (e.g. Postman, Swagger UI, React)
      │
      ▼
Spring Boot REST API
      │
 ┌───────────────┬───────────────┐
 │               │               │
UserController   OrderController PriceController
 │               │               │
 ▼               ▼               ▼
Services      Services       Price Client
 │               │               │
 ▼               ▼               ▼
Repositories   Repositories   External APIs
 │               │
PostgreSQL     PostgreSQL
⚙️ How It Works
Buy Order

Fetch latest price for ticker

Calculate total cost

Check user’s balance

Deduct funds

Update holdings

Sell Order

Verify user owns enough shares

Calculate total sale proceeds

Update balance

Decrease holdings

✅ Possible Improvements
Here’s how you could scale this for production:

✅ Async Processing

Use message queues for order placement

Handle heavy loads smoothly

✅ Caching

Cache price data to reduce API calls

✅ Rate Limiting

Protect external APIs from overuse

✅ Historical Data

Store historical prices for analytics

✅ Monitoring

Add Prometheus, Grafana, etc.

✅ How To Run
✅ Local PostgreSQL setup (example):

bash
Copy
Edit
docker run --name postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=trading \
  -p 5432:5432 \
  -d postgres
✅ Run Spring Boot app:

bash
Copy
Edit
./mvnw spring-boot:run
✅ Access Swagger UI:

bash
Copy
Edit
http://localhost:8080/swagger-ui/index.html
✅ Tests
Run all tests:

bash
Copy
Edit
./mvnw test
✅ Security
Endpoints are protected by JWT tokens

Use Swagger’s Authorize button to test authenticated endpoints

CSRF disabled for simplicity (adjust for production)

