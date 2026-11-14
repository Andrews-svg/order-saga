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
| БД | PostgreSQL 16 × 3 (database-per-service) |
| Брокер | Kafka 3.8 (KRaft) |
| Контейнеры | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Тесты | JUnit 5 + AssertJ + Testcontainers |

## Architecture
┌──────────────────────────┐
│ client │
└────────────┬─────────────┘
│ POST /orders (sync REST)
▼
┌──────────────────────────┐
│ order-service │
│ (оркестратор) │
│ state → PostgreSQL │
└──┬────────┬──────────┬───┘
sync REST │ │ │ async Kafka
GET /products│ │POST │ order.created
▼ ▼ │ inventory.release.command
┌──────────┐ ┌──────────┐ │ order.cancelled
│ product │ │inventory │ │
│ service │ │ service │ │
└──────────┘ └────┬─────┘ │
│ │
▼ │
┌──────────────────┐
│ notification │
│ service │
└──────────────────┘

```text

Полная архитектура, сценарии, риски — в [docs/technical-specification.md](docs/technical-specification.md).

## How to run

### Prerequisites
- Java 21
- Docker Desktop (Docker Compose v2)

### Infrastructure (PostgreSQL × 3 + Kafka)

```bash
cp .env.example .env
docker compose up -d
docker compose ps
Ждём все 4 контейнера в статусе Up (healthy).

Build
```bash
./gradlew build
Project status
**M0 — Foundation: closed (14.11.2026).**
- Gradle multi-module skeleton (4 модуля)
- docker-compose: 3 PostgreSQL + Kafka + 4 сервиса, все healthy
- CI: GitHub Actions, build зелёный
- README, decisions-log, errors-log

**M1 — product-service (in progress):** CRUD, тесты, мутация.

Подробный roadmap — в разделе 8 ТЗ.

Documentation
Technical Specification — архитектура, данные, сценарии, риски, критерии готовности.

License
Не указана. Код защищён авторским правом. Для использования — запрос к автору.