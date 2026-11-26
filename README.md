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

### Web app (early)

The Angular app lives in [`web/`](web). You need **Node.js 22**. With the API running on port 8080:

```sh
cd web
npm install
npm start        # http://localhost:4200, /api calls are proxied to localhost:8080
npm test         # unit tests in Chrome (CI runs them headless)
npm run build    # production build in web/dist/
```

Open **Settings** and paste your store's merchant id once; the browser keeps it and sends it as `X-Merchant-Id` until sign-in arrives (M8). The first screen is **Expiring soon**: tap **Update** on a batch to record units sold or thrown away (with the dollar loss), mark it down, or close it when the shelf is empty. **Log batch** records a delivery (scan or search the item, then expiry date, quantity and an optional unit cost).

### API (early)

Until app login lands (milestone M8), each request names its store with an `X-Merchant-Id` header. **Don't expose the API publicly before M8.** Errors come back as `application/problem+json`.

| Method | Path | What |
|---|---|---|
| `GET` | `/api/items` | All items by name; `?gtin=<barcode>` finds by barcode |
| `GET` | `/api/items/{id}` | One item |
| `POST` | `/api/items` | Create: `{"name": "...", "sku": "...", "gtin": "..."}` |
| `PUT` | `/api/items/{id}` | Replace name, SKU and barcode (blank clears) |
| `GET` | `/api/locations` | All store locations by name |
| `GET` | `/api/locations/{id}` | One location |
| `POST` | `/api/locations` | Create: `{"name": "...", "timezone": "America/New_York", "digestEmail": "owner@example.com"}` (`digestEmail` optional) |
| `PUT` | `/api/locations/{id}` | Replace name, time zone and digest recipient (leaving `digestEmail` out turns the digest off) |
| `GET` | `/api/batches?locationId=<id>` | Open batches at a store, soonest expiry first; repeat `&status=SOLD_OUT` etc. to filter |
| `GET` | `/api/batches/{id}` | One batch, with `daysUntilExpiry` judged by the store's own day |
| `POST` | `/api/batches` | Log: `{"itemId": "...", "locationId": "...", "expiresOn": "2026-12-01", "quantity": 12, "unitCostCents": 289}` |
| `POST` | `/api/batches/{id}/sales` | Units sold: `{"quantity": 4}`; selling the last unit closes it as sold out |
| `POST` | `/api/batches/{id}/mark-down` | Marked down for a quick sale |
| `POST` | `/api/batches/{id}/sold-out` | Shelf found empty: the rest counts as sold |
| `PUT` | `/api/batches/{id}/expires-on` | Fix a mistyped date: `{"expiresOn": "2026-12-01"}` |
| `POST` | `/api/batches/{id}/waste` | Units thrown away: `{"quantity": 2}`; records the loss in cents (null if the batch has no unit cost); wasting the last unit closes it as wasted |
| `GET` | `/api/batches/{id}/waste` | Waste records of one batch |
| `GET` | `/api/waste?from=<instant>&to=<instant>` | Waste in a time window (`to` exclusive, e.g. `2026-11-01T04:00:00Z`), with `knownLossCents` and `entriesWithUnknownLoss` |
| `GET` | `/api/expiring?locationId=<id>` | Open batches expired or expiring within `withinDays` (default 7, max 30) of the store's today, bucketed `EXPIRED`/`TODAY`/`NEXT_3_DAYS`/`NEXT_7_DAYS`/`LATER`, with counts and `knownValueAtRiskCents` |

### Daily digest

Each store with a `digestEmail` gets one plain-text email per day once its own local clock reaches `DIGEST_SEND_HOUR` (default 7 AM): expired batches still on the shelf, then today, the next 3 days and the next 7 days, with the dollar value at risk. Stores with nothing expiring get no email. The job checks a few minutes past every hour and catches up after downtime. With `MAIL_PROVIDER=log` (the default) the email is written to the log instead of sent; a real mail provider is added before launch.

## Planned stack

Java 17 · Spring Boot 3 · PostgreSQL + Flyway · Angular 19 · Docker · GitHub Actions · Square API

## Author

Built by [Abrham Fantaye](https://github.com/fantaye-1).
