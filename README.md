# ShelfLife

**Expiration-date tracking for small grocery and convenience stores that use Square.**

Square doesn't track expiry dates, so stores lose money on products that expire on the shelf. ShelfLife connects to Square, lets staff log each batch's expiry date in seconds, warns the store before things expire, and shows how much money waste is costing.

> Status: **planning / early development.** See [docs/PLAN.md](docs/PLAN.md) for the product plan, architecture and milestones.

## Build and run

You need **Java 17** or newer and **Docker** (the tests start a throwaway PostgreSQL with Testcontainers). Maven is downloaded automatically by the Maven wrapper.

To run the API you also need a PostgreSQL database. Until docker-compose lands (M5), start one with:

```sh
docker run -d --name shelflife-db -p 5432:5432 \
  -e POSTGRES_DB=shelflife -e POSTGRES_USER=shelflife -e POSTGRES_PASSWORD=change-me postgres:16-alpine
export DATABASE_PASSWORD=change-me   # see .env.example for all settings
```

Flyway creates the tables on startup.

```sh
./mvnw verify            # build and run the tests (Windows: mvnw.cmd verify)
./mvnw spring-boot:run   # start the API on http://localhost:8080
curl localhost:8080/actuator/health   # {"status":"UP"}
```

Set `PORT` to use a different port.

### API (early)

Until app login lands (milestone M8), each request names its store with an `X-Merchant-Id` header. **Don't expose the API publicly before M8.** Errors come back as `application/problem+json`.

| Method | Path | What |
|---|---|---|
| `GET` | `/api/items` | All items by name; `?gtin=<barcode>` finds by barcode |
| `GET` | `/api/items/{id}` | One item |
| `POST` | `/api/items` | Create: `{"name": "...", "sku": "...", "gtin": "..."}` |
| `PUT` | `/api/items/{id}` | Replace name, SKU and barcode (blank clears) |

## Planned stack

Java 17 · Spring Boot 3 · PostgreSQL + Flyway · Angular · Docker · GitHub Actions · Square API

## Author

Built by [Abrham Fantaye](https://github.com/fantaye-1).
