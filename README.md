# ShelfLife

**Expiration-date tracking for small grocery and convenience stores that use Square.**

Square doesn't track expiry dates, so stores lose money on products that expire on the shelf. ShelfLife connects to Square, lets staff log each batch's expiry date in seconds, warns the store before things expire, and shows how much money waste is costing.

> Status: **planning / early development.** See [docs/PLAN.md](docs/PLAN.md) for the product plan, architecture and milestones.

## Build and run

You need **Java 17** or newer. Maven is downloaded automatically by the Maven wrapper.

```sh
./mvnw verify            # build and run the tests (Windows: mvnw.cmd verify)
./mvnw spring-boot:run   # start the API on http://localhost:8080
curl localhost:8080/actuator/health   # {"status":"UP"}
```

Set `PORT` to use a different port.

## Planned stack

Java 17 · Spring Boot 3 · PostgreSQL + Flyway · Angular · Docker · GitHub Actions · Square API

## Author

Built by [Abrham Fantaye](https://github.com/fantaye-1).
