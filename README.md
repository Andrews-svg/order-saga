# order-saga — OMS Transactional Core

A distributed order processing system built on the Saga pattern with compensations, Kafka, and observability.

## What it is

Backend-ядро обработки заказов интернет-магазина. Принимает заказ, резервирует остатки, создаёт заказ, уведомляет — и остаётся согласованным, когда части падают. Сага с компенсацией — центральный паттерн.

Не «магазин» (нет фронта, оплаты, кабинета), а движок, который демонстрирует:
- Паттерн Saga с компенсациями (оркестрация + хореография).
- Микросервисную архитектуру: 4 сервиса, database-per-service.
- Kafka как брокер событий (KRaft, без ZooKeeper).
- Идемпотентность, outbox, Resilience4j.
- Наблюдаемость: Micrometer + Prometheus + Grafana, correlation ID.
- Тесты: JUnit 5 + AssertJ + Testcontainers, мутация как доказательство.

## Stack

| Компонент | Решение |
|---|---|
| Язык | Java 21 (LTS) |
| Фреймворк | Spring Boot 3.5.x |
| Сборка | Gradle (Kotlin DSL) |
| БД | PostgreSQL 16 x 3 (database-per-service) |
| Брокер | Kafka 3.8 (KRaft) |
| Контейнеры | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Тесты | JUnit 5 + AssertJ + Testcontainers |

## Architecture

    client
      | POST /orders (sync REST)
      v
    order-service (orchestrator, state -> PostgreSQL)
      |
      |-- sync REST -----> product-service
      |-- sync REST -----> inventory-service
      |
      +-- async Kafka ---> notification-service (order.created, order.cancelled)
                       +-> inventory-service  (inventory.release.command)

Полная архитектура, сценарии, риски — в [docs/technical-specification.md](docs/technical-specification.md).

## How to run

### Prerequisites
- Java 21
- Docker Desktop (Docker Compose v2)

### Infrastructure (PostgreSQL x 3 + Kafka)

    cp .env.example .env
    docker compose up -d
    docker compose ps

Ждём все 8 контейнеров в статусе Up (healthy).

### Build

    ./gradlew build

## Project status

**M0 — Foundation: closed (14.11.2026).**
- Gradle multi-module skeleton (4 модуля)
- docker-compose: 3 PostgreSQL + Kafka + 4 сервиса, все healthy
- CI: GitHub Actions, build зелёный
- README, decisions-log, errors-log

**M1 — product-service: closed (19.11.2026).**
- CRUD REST API: POST/GET/GET all/PUT/DELETE + 404 + 400 (/products, порт 8081)
- 18 тестов: 8 unit (Mockito) + 10 integration (Testcontainers + real PostgreSQL 16)
- Мутация: 10/10 KILLED (mutation score 100%), 2 gap найдено и закрыто retest'ом — [docs/mutation-report.md](docs/mutation-report.md)
- CI: GitHub Actions, build + test зелёный

**M2 — inventory-service: in progress (A1–A5.4 done).**
- Flyway V1–V3: stock, idempotency_keys, outbox
- REST: /inventory/stock (set/get), /inventory/reserves (reserve/release) + idempotency (SHA-256 payload hash)
- Outbox + relay -> Kafka (at-least-once)
- 34 теста, 10 suites: unit + Testcontainers PostgreSQL + Testcontainers Kafka
- Мутации A3/A4/A5.1: 6 KILLED, 2 SURVIVED -> GAP A3.4 / A4.4 ([backlog P0](docs/backlog.md))
- CI: GitHub Actions, build + test зелёный (после фикса #48 — Kafka pull policy conditional on CI)
- Открыто: M-A5.2, M-A5.3 (мутации) -> потом M2 closed

## Documentation

- [Technical Specification](docs/technical-specification.md) — архитектура, данные, сценарии, риски, критерии готовности.
- [Decisions Log](docs/decisions-log.md) — архитектурные решения M0–M2.
- [Errors Log](docs/errors-log.md) — уроки M0–M2, мета-уроки процесса.
- [Mutation Report](docs/mutation-report.md) — доказательства мутаций.
- [Backlog](docs/backlog.md) — GAP P0/P1/P2, что осталось до M3/M7.

## License

Не указана. Код защищён авторским правом. Для использования — запрос к автору.