# Backlog — order-saga

**Проект:** order-saga — OMS Transactional Core
**Создан:** 27.11.2026
**Статус:** живой документ

## Правила

- Каждая запись — конкретная, проверяемая задача с фактом закрытия.
- Приоритет: `P0` (до M3) / `P1` (до M7) / `P2` (post-MVP).
- При закрытии — запись в decisions-log/mutation-report, задача переезжает в «Закрыто».

## P0 — до M3 (архитектурные GAP)

| # | GAP | Источник | Факт закрытия |
|---|---|---|---|
| A1-refactor | Привести `inventory-service` к ТЗ 5.4: `inventory`/`available_qty`/`reserved_qty` вместо `stock`/`total_quantity`/`reserved_quantity`. Flyway V1, entity, repository, service, 34 теста, README | decisions-log 27.11 | код + тесты GREEN + CI |
| A3.4 | Race idempotency: два параллельных запроса с одним `Idempotency-Key`. `@Transactional(REQUIRES_NEW)` re-read + `CountDownLatch` + `Awaitility` | mutation-report M-A3.3 SURVIVED | тест RED до фикса, GREEN после |
| A4.4 | Атомарность relay: два PENDING, второе падает на `save` → первое тоже откатывается (PENDING) | mutation-report A4-M3 SURVIVED | тест RED до фикса, GREEN после |


## P1 — до M7

| # | GAP | Источник |
|---|---|---|
| M-A5.3 | Мутация: убрать FAILED-ветку в `publishOne` → RED | ПАМЯТКА v6 |
| @TestPropertySource | 3 Spring context (HikariPool-1/2/3, факт 26.11). Убрать `@TestPropertySource` в `OutboxRelayKafkaIntegrationTest` | errors-log 22.11 |
| Outbox PENDING assert | `reserve_createsPendingOutboxEvent` ассертит `PENDING` — race-уязвим. → «PENDING или SENT» | mutation-report A5 |
| README fence | Незакрытый fence в разделе Architecture | README |
| 400 malformed JSON | `handleGeneric` → 500. Должно быть 400 (`HttpMessageNotReadableException`) | ТЗ 7.4 |
| 404 ErrorResponse body | `getById_notFound_returns404` проверяет статус, не тело | mutation-report M1 |
| @ConditionalOnProperty (B) | Заменить test-profile на `@ConditionalOnProperty(name="outbox.relay.enabled")` | decisions-log 25.11 |

## P2 — post-MVP

| # | GAP | Источник |
|---|---|---|
| inventory 18.11 | Падение `inventory-service` 18.11 — причина не установлена | ПАМЯТКА v6 |
| Kafka dual listener | `localhost:9092` + `kafka:9092` для Docker-хоста | ПАМЯТКА v6 |

## Закрыто

| # | GAP | Дата | Факт |
|---|---|---|---|
| errors-log 25.11 | 4 урока 25.11 + мета-урок ПАМЯТКА vs репо | 26.11.2026 | коммит `3415b25` |
| M-A5.1 | Мутация `send` → 3 RED → откат → GREEN | 26.11.2026 | коммит `3415b25` |
| CI #48 | Kafka Testcontainers `withImagePullPolicy(false)` в CI → Docker 404 | 27.11.2026 | коммит `9a318f2`, CI #52 зелёный |
| M-A5.2a | Тест `marksFailed_whenSendFutureFails` (async failedFuture) — ловит отсутствие `.get()` | 28.11.2026 | RED подтверждён, GREEN после отката |
| M-A5.2 | Мутация `.get()` → KILLED (retest) | 28.11.2026 | mutation-report 28.11 |

