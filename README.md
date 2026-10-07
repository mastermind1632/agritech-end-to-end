# AgriTech Command Center

A portfolio-grade full-stack agricultural finance and group-buying platform built around **Spring Boot 4.1.1, Java 21, PostgreSQL, JWT authentication, React/Vite, Docker and GitHub Actions**.

## What this project demonstrates

- Secure farmer registration/login with BCrypt password hashing and stateless JWT authentication.
- Farmer finance ledger: income, expenses, totals and net position.
- Supplier marketplace with product catalogue and local supplier information.
- Group purchasing: create a bulk order, join it, track progress and trigger supplier notifications.
- Decision intelligence: rule-based recommendations derived from farmer activity and open group orders.
- Supplier workspace for catalogue visibility and group-order notifications.
- Responsive command-center UI suitable for a portfolio demonstration.
- PostgreSQL persistence with Docker Compose.
- Spring Boot Actuator health/metrics endpoints.
- Environment-based secrets/configuration.
- GitHub Actions CI/CD and container image support.

## Architecture

```text
React + Vite frontend
        |
        | JSON / REST + Bearer JWT
        v
Spring Boot REST API
  |     |       |       |
  |     |       |       +-- Decision Intelligence
  |     |       +---------- Group Orders / Notifications
  |     +------------------ Finance
  +------------------------ Farmers / Suppliers
        |
        v
    PostgreSQL
```

## Run with Docker

1. Copy `.env.example` to `.env`.
2. Replace the database password and JWT secret (the defaults are for local use only).
3. From the project root:

```bash
docker compose up --build
```

Open:

- Web app: `http://localhost:3000`
- API: `http://localhost:8080`
- API health: `http://localhost:8080/actuator/health`

The frontend container reverse-proxies `/api` to the Spring Boot service, so the browser does not need to know the internal Docker hostname.

## Run locally without Docker (IDE / terminal)

Requirements: **Java 21**, **Node 18+**, and a running **PostgreSQL**.

### 1. Start PostgreSQL

Easiest option - only start the database container:

```bash
docker compose up -d postgres
```

Or use your own PostgreSQL and create the database once:

```sql
CREATE DATABASE agritech;
```

The tables are created automatically on first start (`ddl-auto=update`). `database_schema.sql` is only a reference copy.

### 2. Backend

All settings have local defaults (`postgres` / `postgres` on `localhost:5432/agritech`, and a development-only JWT secret), so **no environment variables are required**. Just run `AgriFinanceApIsApplication` from your IDE, or:

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

(On Windows use `mvnw.cmd`.) To override anything, set these environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agritech
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-password
APP_JWT_SECRET=your-long-random-secret-at-least-32-characters
```

Check it is up: `http://localhost:8080/actuator/health` should return `{"status":"UP"}`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to `http://localhost:8080`, so there are no CORS problems. If your backend runs elsewhere: `VITE_PROXY_TARGET=http://host:port npm run dev`.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Could not resolve placeholder 'app.security.jwt-secret'` | A property points at an env variable with no default. `application.properties` now has defaults for everything; make sure you are running the fixed file from `src/main/resources` and do a clean rebuild. |
| `JWT secret must contain at least 32 characters` | `APP_JWT_SECRET` is set but too short. |
| `Connection to localhost:5432 refused` | PostgreSQL is not running. `docker compose up -d postgres`. |
| `password authentication failed for user "postgres"` | The DB password differs from `SPRING_DATASOURCE_PASSWORD` (default `postgres`). If the volume was created with another password run `docker compose down -v` once to reset it (this deletes local data). |
| `database "agritech" does not exist` | Create it (`CREATE DATABASE agritech;`). |
| `Port 8080 was already in use` | Stop the other process or set `SERVER_PORT=8081` (and `VITE_PROXY_TARGET=http://localhost:8081`). |
| Browser shows "Cannot reach the server" | Backend is not running on 8080. |
| Signed out unexpectedly | Tokens last 24 h (`APP_JWT_EXPIRATION_SECONDS`); the app returns you to sign-in when one expires. Changing `APP_JWT_SECRET` also invalidates old tokens. |

## Main API surface

### Authentication

`POST /api/farmers/register`

```json
{
  "name": "John Farmer",
  "location": "Polokwane",
  "contact": "0712345689",
  "password": "Password123"
}
```

`POST /api/farmers/login`

```json
{
  "contact": "0712345689",
  "password": "Password123"
}
```

The response contains a JWT and farmer profile. The frontend stores the token locally and sends it as a Bearer token for protected requests.

### Finance

- `POST /api/finance/expenses`
- `GET /api/finance/expenses/{farmerId}`
- `POST /api/finance/income`
- `GET /api/finance/income/{farmerId}`
- `GET /api/finance/{farmerId}/summary`

### Suppliers / marketplace

- `GET /api/suppliers`
- `GET /api/suppliers/{supplierId}/products`
- `POST /api/suppliers`
- `POST /api/suppliers/{supplierId}/products`

### Group purchasing

- `POST /api/group-orders`
- `GET /api/group-orders?status=open`
- `GET /api/group-orders/{id}`
- `GET /api/group-orders/{id}/items`
- `POST /api/group-orders/{id}/join`
- `PUT /api/group-orders/{id}/close`

### Decision intelligence

- `POST /api/recommendations/generate`
- `GET /api/recommendations/farmer/{farmerId}`

The current recommendation engine is intentionally described as **rule-based decision intelligence**, not as a trained machine-learning model. This is more defensible in a technical interview because the implementation matches the claim.

### Monitoring

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`

## Production hardening notes

This repository is substantially more production-oriented than the original demo, but a real commercial deployment should still add:

1. Supplier identity/authentication and role-based access control.
2. Ownership checks across every farmer-owned finance/recommendation endpoint, preferably deriving farmer identity exclusively from the JWT rather than accepting arbitrary farmer IDs from request bodies.
3. Database migrations with Flyway or Liquibase instead of relying on `ddl-auto=update`.
4. Secret management through a cloud secret manager rather than `.env` files on a server.
5. HTTPS/TLS, rate limiting, security headers and a production CORS allow-list.
6. Automated integration tests using Testcontainers/PostgreSQL.
7. Audit logging and structured observability.
8. Refresh-token/session revocation strategy if long-lived sessions are required.
9. A real ML service/model if the recommendation feature is marketed as machine learning rather than rule-based intelligence.
10. Payment/delivery integration only when those business processes are actually implemented and secured.

These items are deliberately documented instead of hidden: they give you credible talking points when presenting the project to an employer.

## Suggested portfolio demo

1. Register a farmer.
2. Open the dashboard and explain the financial KPI cards.
3. Add an expense and income record.
4. Open Marketplace and show supplier/product data.
5. Create a group order and explain the progress/discount logic.
6. Join the order as another farmer account and show the target being updated.
7. Generate AI Insights and explain exactly what the current recommendation engine analyses.
8. Switch to the Supplier Portal and show catalogue/notification activity.
9. Open `/actuator/health` to demonstrate operational readiness.
10. Explain the Docker + PostgreSQL + CI/CD architecture.
