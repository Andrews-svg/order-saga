# Мутационный отчёт — order-saga

**Проект:** order-saga — OMS Transactional Core
**Создан:** 17.11.2026
**Метод:** ручной мутационный тест (без PIT)
**Принцип:** тест зелёный ≠ тест ловит баг. Мутация — единственное доказательство.

## M1 — product-service

Правила: каждая мутация — цикл «сломать → прогон → RED → откат → GREEN → запись». **KILLED** — ≥1 тест упал (RED). **SURVIVED** — все тесты зелёные при сломанном коде = дыра в покрытии, разбор отдельно.

| # | Мутация | Файл:строка | Поймали тесты | Статус |
|---|---|---|---|---|
| M1 | `create`: `return toResponse(saved)` → `return null` | `ProductService.java:30` | `ProductServiceTest.create_shouldSaveAndReturnResponse` (NPE) + `ProductControllerIntegrationTest`: `createProduct_returns201WithBody`, `getById_returns200`, `getAll_returnsList`, `update_returns200WithUpdatedBody`, `delete_returns204` (5 через helper `createProduct`, assert на строке 40) | KILLED (6/17) |
| M2 | `getById` + `update`: `.orElseThrow(() -> new ProductNotFoundException(id))` → `.orElse(null)` | `ProductService.java:36,50` | `ProductServiceTest.getById_shouldThrow_whenNotFound`, `update_shouldThrow_whenNotFound` (AssertionError: NPE вместо исключения) + `ProductControllerIntegrationTest.getById_notFound_returns404`, `update_notFound_returns404` (AssertionFailedError: 500 вместо 404, NPE перехвачен `handleGeneric`) | KILLED (4/17) |
| M3 | `getAll`: `return repository.findAll().stream()` → `...stream().limit(0)` | `ProductService.java:42` | **Retest после M3a:** Unit `ProductServiceTest.getAll_shouldReturnAll` (AssertionError, строка 93) + Integration `ProductControllerIntegrationTest.getAll_returnsList` (AssertionError, строка 87). Первый прогон: unit SURVIVED — теста не было | KILLED (2/18) |
| M3a | **Находка:** unit-покрытие `getAll` отсутствовало | `ProductServiceTest.java` | Unit-теста `getAll` не было — 7 тестов покрывали `create`, `getById`×2, `update`×2, `delete`×2, но не `getAll`. **Закрыто:** добавлен `getAll_shouldReturnAll` (Mockito, `verify(repository).findAll()`), прогон 18/18 green, M3-retest = KILLED (2/18) | CLOSED |
| M4 | `update`: `product.setName(request.name())` закомментирован (точечно через `Substring`, не затронут `create`) | `ProductService.java:51` | `ProductServiceTest.update_shouldUpdateFieldsAndReturnResponse_whenFound` (AssertionFailedError, строка 113: name="Old", ждали "New") + `ProductControllerIntegrationTest.update_returns200WithUpdatedBody` (AssertionFailedError, строка 103: name="Laptop", ждали "Laptop Pro") | KILLED (2/18) |
| M5 | `delete`: `repository.deleteById(id)` закомментирован (ветка `existsById`/not-found цела) | `ProductService.java:63` | `ProductServiceTest.delete_shouldDelete_whenExists` (WantedButNotInvoked, строка 136: `verify(repository).deleteById` не сработал) + `ProductControllerIntegrationTest.delete_returns204` (AssertionError, строка 128: `findById` не пуст — запись осталась). `delete_notFound_returns404` зелёный — точечность | KILLED (2/18) |
| M6 | `toResponse`: `name` ↔ `description` перепутаны (плейсхолдер `__TMP__` после провала двойного `-replace`) | `ProductService.java:69,70` | **Retest после M6a:** Unit 4 RED (create:53, getById:70, getAll:95, update:113) + Integration 4 RED (createProduct:48, getById:65, update:105, **getAll:89**). Первый прогон: `getAll_returnsList` SURVIVED — GAP M6a | KILLED (8/18) |
| M6a | **Находка:** integration `getAll_returnsList` не проверял name/description — только size + id | `ProductControllerIntegrationTest.java:89-90` | Мутация `toResponse` перепутала поля, integration `getAll_returnsList` не упал (первый прогон). **Закрыто:** добавлены assert на `name` (строка 89) и `description` (строка 90). Retest M6: `getAll_returnsList` падает на строке 89 → KILLED (8/18) | CLOSED |
| M7 | `handleNotFound`: `ResponseEntity.status(NOT_FOUND)` → `INTERNAL_SERVER_ERROR` (тело `ErrorResponse` не тронуто) | `GlobalExceptionHandler.java:26` | **Integration 3 RED:** `getById_notFound_returns404`:74, `update_notFound_returns404`:118, `delete_notFound_returns404`:139 (статус 500 вместо 404). **Unit 3 зелёных** — проверяют `ProductNotFoundException` из сервиса, не HTTP-статус | KILLED (3/18) |
| M8 | `handleValidation`: `ResponseEntity.status(BAD_REQUEST)` → `INTERNAL_SERVER_ERROR` (тело `ErrorResponse` не тронуто) | `GlobalExceptionHandler.java:42` | **Integration 2 RED:** `create_blankName_returns400`:149, `create_nullPrice_returns400`:159 (500 вместо 400). **Unit зелёные** — `ProductServiceTest` не проходит через `@Valid`/Spring MVC | KILLED (2/18) |
| M9 | `ProductController.create`: `ResponseEntity.status(CREATED)` → `OK` (201 → 200) | `ProductController.java:34` | **Integration 5 RED, все на строке 39 (helper `createProduct`, assert CREATED):** `createProduct_returns201WithBody`, `getById_returns200`, `getAll_returnsList`, `update_returns200WithUpdatedBody`, `delete_returns204`. **Зелёные 13:** `create_blankName/NullPrice` (прямой POST), 3 not-found, 7 unit | KILLED (5/18) |
| M10 | `ProductRequest.price`: `@NotNull` закомментирован | `ProductRequest.java:18` | `ProductControllerIntegrationTest.create_nullPrice_returns400`:159 (AssertionFailedError — без `@NotNull` валидация пропустила null, статус не 400). `create_blankName_returns400` зелёный (`@NotBlank` цел), unit'ы зелёные (вне Bean Validation) | KILLED (1/18) |

## Итог M1 — mutation score

| Метрика | Значение |
|---|---|
| Мутаций проведено | 10 |
| KILLED | 10/10 (100%) |
| GAP найдено | 2 (M3a — unit `getAll`, M6a — integration `getAll` поля) |
| GAP закрыто фактом (retest) | 2/2 |
| Тестов до серии | 17 |
| Тестов после серии | 18 (+unit `getAll`) |
| Уроков | unit ≠ integration (слои), helper = общая точка, точечность через `Substring`, плейсхолдер при пересечении replace |

**Вывод:** тесты ловят баги. Не «зелёные», а «ловят» — доказано 10 мутациями и 2 retest'ами.
| M-A3.1 | `IdempotencyService.hash()`: возврат константы `"constant"` вместо SHA-256 | `IdempotencyService.java:67` | `IdempotencyIntegrationTest.reserve_sameKeyDifferentPayload_returns409`:89 (AssertionFailedError: expected 409, got 200 — hash «совпал», вернулся кэш). Зелёные 21: `sameKeySamePayload` (кэш не про hash), `withoutKey` (hash не вызывается) | KILLED (1/22) |
| M-A3.2 | `IdempotencyService.execute`: `if (existing.isPresent())` → `if (false && ...)` — кэш никогда не используется | `IdempotencyService.java:33` | **3 RED:** `reserve_sameKeySamePayload`:71 (reserved=60 вместо 30), `release_sameKeySamePayload`:122 (второй release → 409 вместо 200), `reserve_sameKeyDifferentPayload`:89 (без кэша второй action → save падает на PK → 500 вместо 409). `withoutKey` зелёный | KILLED (3/22) |
| M-A3.3 | `IdempotencyService.execute`: `@Transactional` убран | `IdempotencyService.java:28` | **22/22 green** — последовательный сценарий (action + save успешны) не требует атомарности. Spring создаёт две неявные транзакции, обе коммитятся | **SURVIVED** |
| M-A3.3a | **Находка:** тесты не проверяют атомарность (rollback при падении `save`) | `IdempotencyIntegrationTest` | Атомарность нужна только в **race** (два параллельных запроса с одним ключом). Последовательные тесты не ловят. **Backlog A3.4:** race-тест (`@Transactional(REQUIRES_NEW)` для re-read), 2 потока + `CountDownLatch` + `Awaitility` | GAP |
| M-A3.4 | `IdempotencyService.resolve`: `!equals(hash)` → `equals(hash)` — логика перевёрнута | `IdempotencyService.java:43` | **3 RED:** `reserve_sameKeySamePayload`:70 (hash совпал → 409 вместо 200), `release_sameKeySamePayload`:122 (409 вместо 200), `reserve_sameKeyDifferentPayload`:89 (hash не совпал → кэш → 200 вместо 409). `withoutKey` зелёный | KILLED (3/22) |

## Итог A3 — идемпотентность

| Метрика | Значение |
|---|---|
| Мутаций | 4 |
| KILLED | 3 (M-A3.1 hash, M-A3.2 кэш, M-A3.4 сравнение) |
| SURVIVED | 1 (M-A3.3 @Transactional) |
| GAP | 1 (M-A3.3a: атомарность/race → backlog A3.4) |
| Тестов A3 | 6 новых (2 repository + 4 integration idempotency) |
| Всего тестов | 22 |

**Итог:** последовательная идемпотентность покрыта фактом. Атомарность (race) — GAP, записан честно.
| A4-M1 | `StockService.reserve`: `outbox.record(EVENT_RESERVED, ...)` закомментирован | `StockService.java:55` | `StockServiceTest.reserve_shouldIncreaseReserved_whenAvailableEnough`:55 (WantedButNotInvoked — verify(outbox).record не сработал) + `OutboxIntegrationTest.reserve_createsPendingOutboxEvent`:63 (AssertionError: hasSize(1) → 0). `release_createsPendingOutboxEvent` зелёный — точечность | KILLED (2/30) |
| A4-M2 | `OutboxRelay.publishPending`: `setStatus(SENT)` и `setSentAt(now)` закомментированы (log и save остались) | `OutboxRelay.java:34,35` | `OutboxRelayIntegrationTest.publishPending_marksPendingAsSent`:46 (AssertionError: allMatch SENT → PENDING) + `publishPending_skipsAlreadySent`:61 (AssertionError: findByStatus PENDING не пуст). `publishPending_noop_whenNoPending` зелёный | KILLED (2/30) |
| A4-M3 | `OutboxRelay.publishPending`: `@Transactional` убран | `OutboxRelay.java:28` | **SURVIVED (0 RED)** — 30/30 зелёные. Тесты проверяют успех (PENDING → SENT), не откат при падении события. Без теста «одно событие падает → все откатились» отсутствие `@Transactional` невидимо | SURVIVED → GAP A4.4 |
| A4.4 | **GAP (backlog):** атомарность relay не покрыта | `OutboxRelayIntegrationTest.java` | Нужен тест: два PENDING, второе падает при `save` → первое тоже откатилось (PENDING). Сейчас проверяется только успех. Связано с A3.3 (race) — паттерн «happy-path без rollback» | GAP |

## A5 — outbox relay → Kafka

| # | Мутация | Файл:строка | Поймали тесты | Статус |
|---|---|---|---|---|
| M-A5.1 | `OutboxRelay.publishOne`: `kafkaTemplate.send(...).get(...)` убран (заменён комментарием `// M-A5.1: send disabled`) | `OutboxRelay.java:42` | **Unit 2 RED:** `publishPending_sendsToKafkaAndMarksSent`:56 (WantedButNotInvoked — send не вызван), `publishPending_marksFailedAndContinues_whenSendThrows`:75 (AssertionFailedError — failing стал SENT, не FAILED). **Integration 1 RED:** `OutboxRelayKafkaIntegrationTest.publishPending_sendsRealMessageToKafka_andMarksSent`:69 (IllegalStateException — сообщение не ушло). **Зелёные:** `noop_whenNoPending` (send и так не ждали) | KILLED (3/3) |

**Итог A5 (на 26.11):** 1 мутация — KILLED. M-A5.2/M-A5.3 — ⬜.

| M-A5.2 | `OutboxRelay.publishOne`: `.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)` убран | `OutboxRelay.java:43` | **0 RED — SURVIVED.** `publishPending_sendsToKafkaAndMarksSent` — mock возвращает completedFuture(null), статус SENT и без `.get()`. `publishPending_marksFailedAndContinues_whenSendThrows` — синхронный `thenThrow`, ловится `catch(Exception)` без `.get()`. **GAP:** нет теста на async-failedFuture (Kafka падает внутри CompletableFuture → без `.get()` исключение не вылетает → событие останется SENT вместо FAILED) | SURVIVED → GAP M-A5.2a |

**Retest M-A5.2 (после M-A5.2a):** тест `publishPending_marksFailed_whenSendFutureFails` добавлен (async failedFuture → событие должно быть FAILED). Мутация `.get(...)` убрана → **RED** на строке 90 (AssertionFailedError: SENT вместо FAILED), 3 остальных GREEN → точечность. Откат → GREEN 4/4. **M-A5.2 = KILLED (retest).**
