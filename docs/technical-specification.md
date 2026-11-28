# order-saga — OMS Transactional Core
## Техническое задание

**Дата создания:** 11.11.2026
**Дата фиксации:** 12.11.2026, 16:30
**Статус:** согласовано (разделы 1–11)
**Проект:** order-saga — OMS Transactional Core

---

## Раздел 1. Введение

### 1.1. Цель проекта
Создать backend-ядро обработки заказов интернет-магазина на паттерне Saga с компенсациями. 
Не «магазин» (нет фронта, оплаты, кабинета), а движок, который принимает заказ, 
резервирует остатки, создаёт заказ, уведомляет — и остаётся согласованным, когда части падают.

### 1.2. Что демонстрирует проект
- Паттерн Saga с компенсациями (оркестрация + хореография).
- Микросервисная архитектура: 4 сервиса, database-per-service.
- Kafka как брокер событий (KRaft, без ZooKeeper).
- Идемпотентность, outbox, Resilience4j.
- Наблюдаемость: Micrometer + Prometheus + Grafana, correlation ID.
- Тесты: JUnit 5 + AssertJ + Testcontainers, мутация как доказательство.
- CI/CD: GitHub Actions, Docker Compose.

### 1.3. Целевая аудитория
Технические интервьюеры, ревьюеры, инженеры. README и ТЗ — сигнал навыка до первого запуска кода.

---

## Раздел 2. Название и суть

### 2.1. Название
`order-saga` = order (домен: заказы, OMS) + saga (паттерн распределённых транзакций с компенсацией).

### 2.2. Первая строка README (план)
> order-saga — OMS Transactional Core. A distributed order processing system built on the Saga pattern with compensations, Kafka, and observability.

### 2.3. Суть
Backend-ядро обработки заказов интернет-магазина. Принимает заказ, резервирует остатки, создаёт заказ, уведомляет — и остаётся согласованным, когда части падают. Сага с компенсацией — центральный паттерн.

---

## Раздел 3. Границы

### 3.1. Входит в MVP
- Каталог (CRUD), заказы, резервирование, отмена, уведомления.
- 4 сервиса: `product-service`, `order-service` (оркестратор), `inventory-service`, `notification-service`.
- Database-per-service (3 PostgreSQL), Kafka, сага с компенсацией, идемпотентность, Resilience4j.
- Micrometer + Prometheus + Grafana, correlation ID.
- Testcontainers, Docker Compose, CI/CD, README.

### 3.2. НЕ входит (осознанно)
- Оплата, фронтенд, аутентификация/JWT, личный кабинет, корзина.
- Маркетплейс (продавцы, комиссии, выплаты).
- Kubernetes (post-MVP), возвраты и споры, полноценная безопасность (HTTPS, CSRF).

### 3.3. Post-MVP (если время)
- `springdoc-openapi`.
- Аутентификация (JWT, роли).
- Kubernetes (kind).
- История заказов пользователя.
- Метрики бизнес-уровня.
- Distributed tracing (OpenTelemetry + Jaeger).
- `Idempotency-Key` для `POST /orders`.
- HA БД (репликация).

**Правило:** ни один пункт post-MVP не начинается, пока MVP не закрыт (end-to-end, тесты, CI 4/4, README).

---

## Раздел 4. Архитектура

**Дата:** 12.11.2026, 16:30
**Статус:** согласован

### 4.1. Координация саги — гибридная
- **Оркестрация (default):** `order-service` — оркестратор. Явная логика: проверка каталога → резерв остатков → создание заказа.
- **Хореография:** компенсации и уведомления — через события Kafka, без центрального координатора.
- **Точка баланса** — документируемое решение (см. 4.4).

### 4.2. Способ связи — гибридный
| Взаимодействие | Способ | Обоснование |
|---|---|---|
| `order → product` (проверка каталога) | Синхронно, REST + Resilience4j | Нужен немедленный ответ |
| `order → inventory` (резерв) | Синхронно, REST + Resilience4j | Решение: зарезервировано или нет |
| `order → notification` (уведомление) | Асинхронно, Kafka | Не блокирует создание заказа |
| `order → inventory` (компенсация) | Асинхронно, Kafka | Не зависит от доступности в момент сбоя |
| `order → order` (отмена заказа) | Внутренняя смена состояния + Kafka `order.cancelled` | Локальная транзакция + событие для аудита |

**Правило:** синхронный вызов — только для запросных решений. Асинхронное событие — для побочных эффектов.

### 4.3. Требования к оркестратору (снятие SPOF)
**SPOF (Single Point of Failure)** — единственная точка отказа: компонент, отказ которого останавливает критическую часть системы, потому что нет дубля.

1. **Состояние саги персистентно.** Таблица `saga_state(saga_id, status, current_step, payload, updated_at)` в PostgreSQL `order-service`.
2. **Экземпляры `order-service` stateless.** Не хранят состояние в памяти. Несколько экземпляров за балансировщиком, взаимозаменяемы.
3. **Компенсации идемпотентны.** Каждая команда несёт `correlationId` + `messageId`. Таблица `processed_messages(message_id, processed_at)` в каждой БД сервиса.
4. **HA БД — post-MVP.** Для MVP — одна реплика PostgreSQL в Docker Compose.

**Гарантия доставки Kafka — at-least-once.** Идемпотентность компенсаций — обязательное требование.

### 4.4. Механизм удержания баланса
- Каждое изменение соотношения «оркестрация / хореография» — запись в журнале решений с обоснованием.
- **Триггеры пересмотра:** (а) оркестратор стал узким горлышком по метрикам; (б) сценарий перестал быть обозримым; (в) добавление нового участника требует правки оркестратора чаще, чем раз в спринт.

### 4.5. Таблица взаимодействий
| # | Откуда | Куда | Тип | Контракт / топик |
|---|---|---|---|---|
| 1 | client | `order-service` | sync REST | `POST /orders` |
| 2 | `order-service` | `product-service` | sync REST | `GET /products/{id}` |
| 3 | `order-service` | `inventory-service` | sync REST | `POST /inventory/reserves` |
| 4 | `order-service` | `notification-service` | async Kafka | `order.created` |
| 5 | `order-service` | `inventory-service` | async Kafka | `inventory.release.command` |
| 6 | `order-service` | `notification-service` | async Kafka | `order.cancelled` |
| 7 | `inventory-service` | `order-service` | async Kafka | `inventory.released` |

### 4.6. Диаграмма (ASCII)

```text
┌──────────────────────────┐
│ client │
└────────────┬─────────────┘
│ POST /orders (sync REST)
▼
┌──────────────────────────┐
│ order-service │
│ (оркестратор) │
│ state → PostgreSQL │
│ stateless × N │
└──┬────────┬──────────┬───┘
sync REST │ │ │ async Kafka
GET /products│ │POST │ order.created
▼ ▼ │ inventory.release.command
┌──────────┐ ┌──────────┐ │ order.cancelled
│ product │ │inventory │ │
│ service │ │ service │ │
└──────────┘ └────┬─────┘ │
│ │
│ async Kafka: inventory.release.command
│◄───────┘
│
▼
┌──────────────────┐
│ notification │
│ service │
└──────────────────┘
```

### 4.7. Глоссарий раздела
- **SPOF** — Single Point of Failure.
- **Идемпотентность** — повторное выполнение даёт тот же результат.
- **Stateless** — не хранит состояние в памяти.
- **Оркестрация** — центральный компонент управляет шагами.
- **Хореография** — нет центрального компонента.

### 4.8. Принцип 4×4
Четыре сервиса — **четыре зелёных проверки** на каждом шаге:
1. Тесты зелёные.
2. Мутация (сломал → красный → вернул → зелёный).
3. CI зелёный.
4. Факт (`git log`, `git show --stat`, `git status`).

**Шаг не закрыт, пока все четыре не зелёные.**

---

## Раздел 5. Данные

**Дата:** 12.11.2026, 16:30
**Статус:** согласован

### 5.1. Принципы данных
- **Database-per-service.** Три PostgreSQL: `product-service`, `order-service`, `inventory-service`. Никаких общих таблиц.
- **`notification-service` — без БД.** Уведомление — побочный эффект; дубли допустимы.
- **Миграции — Flyway.**
- **JSONB для payload** в `saga_state` и `outbox`.
- **Транзакционный outbox** — для гарантированной публикации событий.

### 5.2. Схема БД `product-service`
```sql
CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_name ON products (name);
```

### 5.3. Схема БД order-service
```sql
CREATE TABLE orders (
    id           UUID PRIMARY KEY,
    customer_id  UUID NOT NULL,
    status       VARCHAR(32) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE order_items (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    qty        INT NOT NULL CHECK (qty > 0),
    price      NUMERIC(12,2) NOT NULL CHECK (price >= 0)
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE TABLE saga_state (
    saga_id      UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id),
    status       VARCHAR(32) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    payload      JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_saga_state_order_id ON saga_state (order_id);
CREATE INDEX idx_saga_state_status ON saga_state (status);
CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

### 5.4. Схема БД inventory-service
```sql
CREATE TABLE inventory (
    product_id    UUID PRIMARY KEY,
    available_qty INT NOT NULL CHECK (available_qty >= 0),
    reserved_qty  INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE reserves (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL,
    product_id UUID NOT NULL,
    qty        INT NOT NULL CHECK (qty > 0),
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reserves_order_id ON reserves (order_id);
CREATE INDEX idx_reserves_status ON reserves (status);
CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
Атомарность резерва: POST /inventory/reserves в одной транзакции:

UPDATE inventory SET available_qty = available_qty - :qty, reserved_qty = reserved_qty + :qty WHERE product_id = :pid AND available_qty >= :qty — если 0 строк, отказ.

INSERT INTO reserves (...) ON CONFLICT (id) DO NOTHING.
```

Вернуть результат.

### 5.5. Схема notification-service
БД нет. Потребляет order.created и order.cancelled, логирует/отправляет. Дубли допустимы.

### 5.6. Транзакционный outbox
Проблема. Между коммитом БД и отправкой в Kafka есть окно: БД закоммитили, приложение упало до отправки — событие потеряно.

Решение. В одной транзакции с бизнес-изменением пишем событие в outbox. Поллер читает published_at IS NULL, отправляет в Kafka, помечает published_at = now(). Гарантия: at-least-once.

Стоимость: +1 таблица, +1 поллер, +тесты.

Альтернатива (отвергнута): Kafka Transactions — не решают окно с БД.

### 5.7. Контракты Kafka-топиков
Имена: order-saga.`<event>`.

Ключ: orderId.

Партиции: 3.

Формат: JSON, UTF-8.

Версия: поле version: 1.

Consumer groups: notification-service, inventory-service, order-service.

| Топик |   Producer    |   Consumer    |   Назначение  |
|---|---|---|---|
| `order-saga.order.created` | `order-service` | `notification-service` | `Заказ создан` |
| `order-saga.order.cancelled` | `order-service` | `notification-service` | `Заказ отменён` |
| `order-saga.inventory.release.command` | `order-service` | `inventory-service` | `Команда: отменить резерв` |
| `order-saga.inventory.released` | `inventory-service`| `order-service`| Факт: резерв отменён |

### 5.8. Формат сообщений
Конверт:

```json
{
  "messageId": "uuid",
  "correlationId": "uuid",
  "sagaId": "uuid",
  "orderId": "uuid",
  "eventType": "order.created",
  "version": 1,
  "timestamp": "2026-11-12T16:30:00Z",
  "payload": {}
}
```
Payload:

order.created: { "customerId": "uuid", "totalAmount": 123.45, "items": [...] }

order.cancelled: { "reason": "inventory_unavailable" }

inventory.release.command: { "reserveIds": ["uuid"], "reason": "saga_compensation" }

inventory.released: { "reserveIds": ["uuid"], "releasedAt": "..." }


### 5.9. Глоссарий раздела
Outbox — таблица для атомарной записи события вместе с бизнес-изменением.

Consumer group — группа потребителей Kafka.

Ключ сообщения — определяет партицию.

## Раздел 6. Сценарии
Дата: 12.11.2026, 16:30
Статус: согласован

### 6.1. Конечный автомат заказа
Статусы заказа:

```text
CREATED ──► CONFIRMED ──► (конец, успех)
   │
   └──────► CANCELLED ──► (конец, отмена)
```

Состояния саги:

```text
STARTED ──► PRODUCT_CHECKED ──► RESERVED ──► COMPLETED
   │              │                 │
   │              │                 └──► COMPENSATING ──► COMPENSATED
   │              └──► COMPENSATING
   └──► FAILED
```

### 6.2. Happy path
```text
#	Шаг	Актор	Действие	Состояние
1	Клиент создаёт заказ	client → order-service	POST /orders	orders: CREATED, saga: STARTED
2	Проверка каталога	order-service → product-service	GET /products/{id}	saga: PRODUCT_CHECKED
3	Резерв	order-service → inventory-service	POST /inventory/reserves	saga: RESERVED
4	Подтверждение	order-service	orders.status = CONFIRMED, outbox order.created	orders: CONFIRMED, saga: COMPLETED
5	Уведомление	Kafka → notification-service	Логирует/отправляет	—
```
Ответ клиенту: 201 Created с orderId и status: CONFIRMED.

### 6.3. Компенсация A — нет товара
```text
#	Шаг	Действие	Состояние
1	Клиент создаёт заказ	POST /orders	orders: CREATED, saga: STARTED
2	Проверка каталога	404 / timeout	saga: FAILED
3	Отмена заказа	orders.status = CANCELLED, outbox order.cancelled	orders: CANCELLED
4	Уведомление	Kafka order.cancelled	—
```
Компенсаций не требуется. Ответ: 422 или 404.

### 6.4. Компенсация B — нет остатков
```text
#	Шаг	Действие	Состояние
1	Клиент создаёт заказ	POST /orders	orders: CREATED, saga: STARTED
2	Проверка каталога	OK	saga: PRODUCT_CHECKED
3	Резерв	409 Conflict	saga: COMPENSATING
4	Компенсация	outbox inventory.release.command	—
5	inventory потребляет	Отменяет резервы, публикует inventory.released	reserves: RELEASED
6	order потребляет	saga: COMPENSATED, orders: CANCELLED, outbox order.cancelled	orders: CANCELLED
7	Уведомление	Kafka order.cancelled	—
```
Частичный резерв: saga_state.payload хранит reserveIds[] → компенсация отменяет все.

Ответ: 409 Conflict.

### 6.5. Компенсация C — inventory timeout
```text
#	Шаг	Действие	Состояние
1	Резерв	timeout (2s)	—
2	Retry	3× с backoff	—
3	Circuit breaker open	Быстрый отказ	saga: COMPENSATING
4	Компенсация	outbox inventory.release.command	—
5	Kafka ждёт	inventory вернётся → отменит	reserves: RELEASED
6	inventory.released	Завершение	orders: CANCELLED, saga: COMPENSATED
```
Ответ: 503 или 409.

### 6.6. Компенсация D — падение order-service в RESERVED
```text
#	Шаг	Действие	Состояние
1	Резерв выполнен	reserves: ACTIVE	saga: RESERVED, orders: CREATED
2	order-service упал	—	orders: CREATED, saga: RESERVED (в БД)
3	Новый экземпляр	Читает saga_state	—
4	Recovery-задача	Саги в RESERVED старше 5 мин → завершает	orders: CONFIRMED, saga: COMPLETED
5	Уведомление	Kafka order.created	—
```
Требование к recovery: идемпотентность.

### 6.7. Таблица «что если»
```text
Ситуация	Что происходит	Гарантия
Повторная доставка inventory.release.command	Проверка processed_messages → пропуск	Идемпотентность
Повторная доставка order.created	notification без БД → дубль	Осознанно
Повторная доставка inventory.released	Проверка processed_messages → пропуск	Идемпотентность
Kafka недоступна при записи outbox	Транзакция коммитится, поллер отправит позже	Outbox
Поллер упал после отправки	Повторная отправка → потребители идемпотентны	At-least-once
product-service недоступен	timeout → saga: FAILED, CANCELLED	Resilience4j
inventory-service недоступен	timeout + retry → circuit open → COMPENSATING	Resilience4j + Kafka
Частичный резерв	payload.reserveIds[] → компенсация всех	Список в payload
order-service упал в RESERVED	Recovery-задача	Персистентность + recovery
Дублирование POST /orders	Idempotency-Key — post-MVP	Открытый вопрос
```

### 6.8. Таймауты и retry (Resilience4j)
```text
Вызов	Timeout	Retry	Circuit breaker
order → product (GET)	1s	2×, backoff 100ms	50% / 10 вызовов → open 10s
order → inventory (POST reserve)	2s	2×, backoff 200ms	50% / 10 → open 10s
```
Правило: retry только на идемпотентные операции.

### 6.9. Открытые вопросы
Idempotency-Key для POST /orders — post-MVP.

Таймаут «зависшей» саги в RESERVED — 5 минут (конфигурируемо).

## Раздел 7. Нефункциональные требования
Дата: 12.11.2026, 16:30
Статус: согласован
Статус чисел: гипотеза, проверяемая практикой. Изменение — только через журнал решений с обоснованием.

### 7.1. Принципы
Измеримость. Каждое требование — число или проверяемое свойство.

MVP-реализм. НФТ уровня прода (99.99%, мульти-ДЦ) не ставим.

Проверка фактом. Каждое НФТ имеет способ проверки.

### 7.2. Производительность
```text
Параметр	Значение
RPS на POST /orders	50
Latency sync-пути	p50 < 150 мс, p95 < 500 мс, p99 < 1 с
Latency async-звена	p95 < 2 с
Пропускная способность саги	≥ 50 саг/с
```
Способ проверки: нагрузочный тест (Gatling или k6) в M6.

### 7.3. Доступность и восстановление
```text
Параметр	MVP
SLA	99.0%
RTO	< 5 мин
RPO	0 для событий, 0 для orders в пределах одной БД
HA БД	Нет (post-MVP)
```
Честно: MVP переживает падение сервиса, но не потерю диска БД.

### 7.4. Безопасность
В MVP:

Bean Validation на всех эндпоинтах.

Ограничение размера payload (1 MB).

JPA/Spring Data (параметризованные запросы).

Correlation ID.

Секреты в .env + Docker Compose secrets.

НЕ в MVP (post-MVP):

Аутентификация/JWT, HTTPS/TLS, CSRF.

mTLS между сервисами. В MVP сервисы доверяют друг другу в Docker-сети.

Rate limiting, WAF.

### 7.5. Наблюдаемость
Метрики:

```text
Метрика	Тип	Зачем
http_server_requests_seconds	histogram	Latency
saga_state_count{status}	gauge	Зависшие саги
outbox_unpublished_count	gauge	Застрявший поллер
kafka_consumer_lag	gauge	Отставание потребителей
resilience4j_circuitbreaker_state	gauge	Деградация
saga_compensations_total{reason}	counter	Бизнес-метрика
```
Correlation ID: X-Correlation-Id → MDC → логи → Kafka → HTTP.

Логи: JSON в stdout → Loki → Grafana. Поля: timestamp, level, service, correlationId, message. 
Единый поиск по всем сервисам по correlationId, service, level. 
В MVP-конфигурации — docker compose logs для локальной отладки; Loki — для сквозного поиска по саге.

Grafana: один дашборд.

Distributed tracing — post-MVP.

### 7.6. Масштабируемость
```text
Аспект	MVP
Горизонтальное масштабирование	Архитектурно готово (stateless)
Партиции Kafka	3
Масштабирование БД	Нет
Outbox-поллер	SELECT ... FOR UPDATE SKIP LOCKED
```
### 7.7. Совместимость и окружение
```text
Параметр	Значение
Java	21 (LTS)
Spring Boot	3.5.x
Gradle	Kotlin DSL, wrapper
PostgreSQL	16
Kafka	KRaft, apache/kafka:3.8.x
Docker Compose	один docker-compose.yml
CI	GitHub Actions
```
Версии — в gradle/libs.versions.toml.

### 7.8. Ограничения MVP
Нет HA БД.

Нет аутентификации.

Нет HTTPS/TLS.

Нет distributed tracing.

Нагрузочные цифры — для одной машины.

POST /orders не идемпотентен на уровне клиента.

Правило: каждое ограничение — в разделе 9 или 8.4.

## Раздел 8. Roadmap
Дата: 12.11.2026, 16:30
Статус: согласован

### 8.1. Принципы
Этап закрыт — только по факту (4×4).

Последовательность жёсткая.

MVP — M0–M7. После M7 — стоп.

Шаг = одно действие = один коммит.

### 8.2. Обзор этапов
```text
Этап	Название	Результат	Зависит от
M0	Foundation	Репозиторий, скелет, Docker Compose, CI	—
M1	product-service	CRUD, тесты, мутация	M0
M2	inventory-service	Резервы, идемпотентность, outbox	M0
M3	order-service	Оркестратор саги, компенсации, recovery	M1, M2
M4	notification-service	Kafka-консьюмер	M3
M5  Observability   Micrometer, Prometheus, Grafana, Loki M1–M4
M6	E2E + нагрузка	Полный сценарий, нагрузочный тест, README M5
M7	MVP closed	CI 4/4, docker compose up, README, критерии	M6
```
### 8.3. Детализация
M0 — Foundation: репозиторий, Gradle multi-module, модули-заглушки, docker-compose.yml, CI-скелет, README-скелет, .gitignore, .env.example.
Критерий: ./gradlew build зелёный, docker compose up → 4 сервиса на /actuator/health.

M1 — product-service: Flyway, entity, repository, service, REST CRUD, validation, тесты (unit + Testcontainers), мутация, CI.
Критерий: 4×4.

M2 — inventory-service: Flyway, POST /inventory/reserves, идемпотентность, Kafka-консьюмер inventory.release.command, outbox-поллер (SKIP LOCKED), тесты, мутация, CI.
Критерий: 4×4. Повторная доставка не ломает состояние.

M3 — order-service: Flyway, POST /orders, sync-вызовы с Resilience4j, машина состояний, outbox, Kafka-консьюмер inventory.released, recovery-задача, тесты (все компенсации), мутация, CI.
Критерий: 4×4. Все 4 сценария компенсации покрыты.
Риск: самый рискованный этап. Право на дробление M3a / M3b.

M4 — notification-service: Kafka-консьюмер, логирование, метрика, тесты, мутация, CI.
Критерий: 4×4.

M5 — Observability: Micrometer, Prometheus, Grafana, Loki (сбор логов), correlation ID, JSON-логи, тесты.
Критерий: дашборд метрик работает, поиск по correlationId в Loki возвращает все логи саги end-to-end, correlation ID сквозной.

M6 — E2E + нагрузка: E2E-тесты (happy + компенсации + recovery), нагрузочный тест, отчёт, README финальный.
Критерий: E2E зелёные, нагрузочный отчёт, README полный.

M7 — MVP closed: чек-лист раздела 10, тег v0.1.0-mvp.
Критерий: все пункты — факт.

### 8.4. Post-MVP
```text
#	Что	Оценка
1	Idempotency-Key для POST /orders	S
2	springdoc-openapi	S
3	Аутентификация (JWT)	M
4	OpenTelemetry + Jaeger	M
5	HA БД	M
6	Kubernetes (kind)	L
7	История заказов	M
8	Метрики бизнес-уровня	S
```
Правило: ни один пункт не начинается, пока M7 не закрыт.

### 8.5. Риски roadmap
M3 — самый рискованный. При затягивании — сигнал замедления, дробление.

M5 может съесть время. Минимальный набор метрик — обязателен.

Нагрузочный тест может показать, что цифры нереалистичны. Это факт, не провал. Журнал решений.

## Раздел 9. Риски
Дата: 12.11.2026, 16:30
Статус: согласован
Статус рисков: гипотезы, проверяемые практикой. Вероятность и влияние уточняются после каждого этапа.

### 9.1. Принципы
Риск без митигации — не риск, а паника.

Честность важнее комфорта.

Владелец — конкретная роль (архитектор или разработчик), не «команда».

Риск пересматривается после каждого этапа.

### 9.2. Технические риски
```text
#	Риск	Вероятность	Влияние	Митигация	Владелец
T1	M3 затягивается	Высокая	Высокое	Дробление M3a / M3b	Архитектор
T2	Нагрузочные цифры не подтверждаются	Средняя	Среднее	Нагрузочный тест — факт. Журнал решений	Архитектор + Разработчик
T3	Потеря данных при отказе диска БД	Низкая	Высокое	Осознанное ограничение. Post-MVP — репликация	Архитектор
T4	Outbox-поллер дублирует отправку	Средняя	Низкое	SKIP LOCKED, тест параллельного поллера	Разработчик
T5	Kafka KRaft нестабильна в Docker Compose	Средняя	Среднее	Проверенный образ, named volume, healthcheck	Разработчик
T6	Recovery-задача не идемпотентна	Средняя	Высокое	Проверка outbox.aggregate_id + event_type, тест	Архитектор
T7	Testcontainers медленные	Средняя	Низкое	Reuse, параллельные тесты, кэш Gradle	Разработчик
```
### 9.3. Организационные риски
```text
#	Риск	Вероятность	Влияние	Митигация	Владелец
O1	Scope creep	Высокая	Высокое	Post-MVP не начинается до M7	Архитектор
O2	Потеря фокуса на M3	Средняя	Высокое	Критерий закрытия M3 — 4×4	Архитектор + Разработчик
O3	Документация устарела	Средняя	Среднее	Журнал решений, живой документ	Архитектор
O4	Bus factor = 1	Средняя	Высокое	Документация в репозитории	Архитектор
```
### 9.4. Продуктовые риски
```text
#	Риск	Вероятность	Влияние	Митигация	Владелец
P1	Демо не впечатляет	Средняя	Среднее	README + диаграмма + нагрузочный отчёт + Grafana. Демо компенсации B	Архитектор
P2	Стек тяжёлый для презентации	Низкая	Низкое	docker compose up одной командой. README объясняет	Разработчик
P3	Нет аутентификации	Средняя	Низкое	README: auth — post-MVP, осознанно	Архитектор
```

### 9.5. Матрица «вероятность × влияние»
```text
Влияние
  Высокое │  T3        T1, O1
          │  O4
  Среднее │  T5, T7    T2, T6, O2, O3, P1, P3
          │  P2
  Низкое  │
          └──────────────────────────────
            Низкая    Средняя    Высокая   Вероятность
Красная зона (фокус внимания): T1, O1.
```

### 9.6. Зоны ответственности
Архитектор:

Контроль декомпозиции M3 (дробление M3a / M3b при затягивании).

Требование написать тест на идемпотентность recovery до кода.

Контроль границы MVP: post-MVP не начинается до M7.

Ведение журнала решений при каждом изменении архитектуры.

Разработчик:

Нагрузочные замеры только инструментом, с отчётом.

Проверка фактом до закрытия шага.

M4 не начинается, пока M3 не закрыт по 4×4.

### 9.7. Механизм пересмотра
После каждого этапа — сверка: какие риски сбылись, какие закрылись, какие новые.

Запись в журнал решений.

Новый риск — сразу в таблицу.

## Раздел 10. Критерии готовности (MVP Closed)
Дата: 12.11.2026, 16:30
Статус: согласован

### 10.1. Принцип
MVP закрыт — только если каждый пункт — ФАКТ. Один невыполненный пункт = MVP не закрыт.

### 10.2. Функциональные критерии
```text
#	Критерий	Проверка
F1	POST /orders — happy path	E2E-тест
F2	Компенсация A: товар не найден	E2E-тест
F3	Компенсация B: нет остатков	E2E-тест
F4	Компенсация C: inventory timeout	E2E-тест
F5	Компенсация D: рестарт в RESERVED	E2E-тест
F6	Повторная доставка inventory.release.command	Интеграционный тест
F7	Частичный резерв	Интеграционный тест
F8	product-service CRUD	Интеграционный тест
F9	notification-service потребляет	Интеграционный тест
```
### 10.3. Технические критерии
```text
#	Критерий	Проверка
T1	4×4 зелёные	Отчёты
T2	Мутация всех тестов	docs/mutation-report.md
T3	Outbox работает	Интеграционный тест
T4	Идемпотентность компенсаций	Интеграционный тест
T5	Stateless order-service	E2E (два контейнера)
T6	Recovery идемпотентна	Интеграционный тест
T7	SKIP LOCKED в поллере	Код + тест
T8	Resilience4j настроен	Конфиг + тест
```
### 10.4. Нефункциональные критерии
```text
#	Критерий	Проверка
N1	50 RPS	Нагрузочный отчёт
N2	p95 < 500 мс	Нагрузочный отчёт
N3	p95 < 2 с async	Нагрузочный отчёт
N4	SLA 99.0% (или явно: не проверялось)	README
N5	RTO < 5 мин	E2E-тест (F5)
N6	Ограничения MVP в README	Проверка README
```
### 10.5. Наблюдаемость
```text
#	Критерий	Проверка
O1	Grafana-дашборд	Демо
O2	Correlation ID end-to-end	Демо
O3	saga_compensations_total	Демо
O4	outbox_unpublished_count	Демо
O5	kafka_consumer_lag	Демо
```
### 10.6. Инфраструктура
```text
#	Критерий	Проверка
I1	docker compose up одной командой	Чистая машина
I2	./gradlew build зелёный	CI + локально
I3	CI 4/4	GitHub Actions
I4	/actuator/health всех сервисов	curl
I5	Тег v0.1.0-mvp	git tag
```
### 10.7. Документация
```text
#	Критерий	Проверка
D1	README полный	Проверка README
D2	ТЗ в репозитории	docs/technical-specification.md
D3	Журнал решений	Проверка
D4	Журнал ошибок	Проверка
D5	Нагрузочный отчёт	docs/load-test-report.md
D6	Отчёт по мутации	docs/mutation-report.md
```
### 10.8. Процедура закрытия
Разработчик проходит чек-лист.

Архитектор проверяет факты независимо (не «на слово»).

Любой невыполненный пункт → MVP не закрыт.

Все выполнены → тег v0.1.0-mvp, запись в журнал.

После закрытия — стоп.

### 10.9. Что НЕ является критерием
«Код красивый» — не критерий.

«Работает на моей машине» — не критерий.

«Почти всё готово» — не критерий.

«Потом допишем README» — не критерий.

«Нагрузка потом» — не критерий.

## Раздел 11. Приложения
Дата: 12.11.2026, 16:30
Статус: согласован

### 11.1. Сводный глоссарий
```text
Термин	Расшифровка	Раздел
At-least-once	Гарантия доставки: минимум один раз, возможны дубли	5.6
Bus factor	Сколько человек может выпасть	9.3
Consumer group	Группа потребителей Kafka	5.7
Correlation ID	Идентификатор для связи логов и событий	7.5
Database-per-service	Каждый сервис — своя БД	5.1
E2E-тест	Тест через все сервисы	8.6
Idempotency-Key	Заголовок для распознавания повтора	6.9
Идемпотентность	Повтор даёт тот же результат	4.3
Митигация	Мера снижения риска	9.1
MDC	Контекст логов	7.5
Оркестрация	Центральный компонент управляет шагами	4.1
Outbox	Таблица для атомарной записи события	5.6
RPO	Допустимая потеря данных	7.3
RTO	Время восстановления	7.3
Saga	Паттерн распределённых транзакций	2
SLA	Соглашение об уровне обслуживания	7.3
SKIP LOCKED	Конструкция PostgreSQL	7.6
SPOF	Single Point of Failure	4.3
Stateless	Не хранит состояние в памяти	4.3
Scope creep	Неконтролируемое расширение	9.3
Хореография	Нет центрального координатора	4.1
4×4	Четыре сервиса — четыре зелёных проверки	4.8
```
### 11.2. Ссылки
```text
Тема	Источник
Saga pattern	microservices.io/patterns/data/saga.html
Kafka KRaft	kafka.apache.org/documentation
Spring Boot 3.5	docs.spring.io/spring-boot
Resilience4j	resilience4j.readme.io
Testcontainers	testcontainers.com
Micrometer	micrometer.io
Gradle Kotlin DSL	docs.gradle.org
PostgreSQL 16	postgresql.org/docs/16
Docker Compose	docs.docker.com/compose
GitHub Actions	docs.github.com/actions
```
Правило: ссылка — для углубления. Источник истины — код, терминал, git.

### 11.3. Сводный журнал решений
```text
Дата	Решение	Альтернативы	Почему
11.11.2026	Имя order-saga	order-flow, saga-flow	Сигнал навыка + домен
11.11.2026	Термин «OMS Transactional Core»	«OMS»	Точный, скромный
11.11.2026	Домен: интернет-магазин	Маркетплейс	Scope управляемый
11.11.2026	Spring Boot 3.5	3.3, 3.4	3.3 устарел
11.11.2026	Gradle (Kotlin DSL)	Maven	Современный стандарт
11.11.2026	PostgreSQL × 3	MySQL, одна БД	Database-per-service
11.11.2026	Kafka KRaft	RabbitMQ	Durability, replay
11.11.2026	4 сервиса	3, 5, 13	Минимум для саги
12.11.2026	Координация: гибридная	Чистая оркестрация/хореография	Явный контроль + устойчивость
12.11.2026	Связь: гибридная	Всё sync, всё async	Немедленный ответ + устойчивость
12.11.2026	Требования к оркестратору	Наивная оркестрация	Снятие SPOF
12.11.2026	Outbox	Kafka Transactions	Гарантия at-least-once
12.11.2026	inventory.released	Без обратного события	Завершение компенсации
12.11.2026	notification без БД	С БД	Дубли допустимы
12.11.2026	Таймаут саги 5 мин	1/30 мин	Компромисс
12.11.2026	Idempotency-Key post-MVP	В MVP	Риск зафиксирован
12.11.2026	Нагрузка 50 RPS, p95 < 500 мс	Больше/меньше	Презентационная версия
12.11.2026	SLA 99.0%, RPO 0	99.9%, HA	Честная планка
12.11.2026	Разделение зон ответственности архитектор/разработчик	«Команда виновата»	Карта ответственности
12.11.2026	Принцип 4×4	«Тесты зелёные — готово»	Мутация — доказательство
12.11.2026	Независимая проверка архитектором	Разработчик проверяет сам	Защита от самообмана
12.11.2026	Числа ТЗ — гипотезы	«Навсегда»	Проверка практикой
```
### 11.4. Структура репозитория
```text
order-saga/
├── README.md
├── docs/
│   ├── technical-specification.md
│   ├── decisions-log.md
│   ├── errors-log.md
│   ├── load-test-report.md
│   └── mutation-report.md
├── gradle/libs.versions.toml
├── product-service/
├── order-service/
├── inventory-service/
├── notification-service/
├── docker-compose.yml
├── .github/workflows/ci.yml
├── .env.example
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew, gradlew.bat, gradle/wrapper/
```

### 11.5. Что дальше
Создать репозиторий order-saga — выполнено (12.11.2026).

Первый коммит: docs: add technical specification — выполнено (12.11.2026, fcb924e).

Второй коммит: fix: remove internal roles from technical specification — текущий шаг.

M0: скелет Gradle, Docker Compose, CI.

ТЗ — живой документ. Изменения — через журнал решений.

Конец ТЗ.
Дата фиксации: 12.11.2026, 16:30
Статус: согласовано (разделы 1–11)
