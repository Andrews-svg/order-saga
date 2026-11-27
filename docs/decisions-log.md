# Журнал решений — order-saga

**Проект:** order-saga — OMS Transactional Core
**Создан:** 14.11.2026
**Статус:** живой документ

## Правила ведения

- Каждое архитектурное решение — запись с датой, альтернативами и обоснованием.
- Изменение ранее принятого решения — **новая** запись, не правка старой (история важна).
- Решение без записи — не решение.

## M0 — Foundation

| Дата | Решение | Альтернативы | Почему |
|---|---|---|---|
| 14.11.2026 | Gradle Wrapper 8.14 | Глобальный Gradle | Фиксация версии для всех участников; `gradlew` скачивает нужную версию |
| 14.11.2026 | JDK 21 через `JAVA_HOME`, без `org.gradle.java.home` в `gradle.properties` | Путь в properties | Локальный Windows-путь ломает Linux CI |
| 14.11.2026 | Multi-stage Dockerfile для каждого сервиса | Один общий Dockerfile | Каждый сервис — свой образ; кэш слоёв переиспользуется |
| 14.11.2026 | Kafka `apache/kafka:3.8.1` (KRaft) | Confluent, Zookeeper | Без Zookeeper; official образ; KRaft production-ready |
| 14.11.2026 | Kafka healthcheck: `/opt/kafka/bin/kafka-topics.sh` + `start_period: 60s` | Голый `kafka-topics.sh` | Путь не в PATH контейнера; KRaft требует времени на выборы лидера |
| 14.11.2026 | Порты сервисов: 8081 / 8082 / 8083 / 8084 | Один порт + маршрутизация | Каждый сервис — отдельный процесс, отдельный порт |
| 14.11.2026 | `notification-service` — без БД, без `depends_on` к PostgreSQL | С БД | ТЗ 5.5: уведомления — побочный эффект, дубли допустимы |
| 14.11.2026 | CI: один шаг `./gradlew build` | Сразу все тесты, smoke, публикация | «CI по этапам»: шаг добавляется, когда может быть зелёным по факту |
| 14.11.2026 | `gradlew` с битом `100755` в git | Только `gradlew.bat` для Windows | Linux CI: `./gradlew: Permission denied` без бита |

## M1 — product-service

| Дата | Решение | Альтернативы | Почему |
|---|---|---|---|
| 15.11.2026 | `Product` PK — UUID, генерируется приложением (`UUID.randomUUID()`) | `BIGSERIAL`, `@GeneratedValue(AUTO)` | Распределённая система: id известен до commit, нет коллизий между сервисами, id = бизнес-ключ продукта |
| 15.11.2026 | DTO (record) на границе REST, entity — внутри | Entity в контроллере | Слой: контракт API не привязан к JPA, нет lazy-прокси в JSON, безопаснее (нет утечки полей) |
| 15.11.2026 | `@RestControllerAdvice` + `ErrorResponse` для 404/400 | try/catch в контроллере | Единая точка обработки ошибок, единый контракт ответа, не дублируется в каждом методе |
| 17.11.2026 | `@ServiceConnection` для Testcontainers | `@DynamicPropertySource` вручную | Boot 3.1+ сам связывает контейнер с properties, меньше кода, нет дублей bootstrap-адресов |
| 17.11.2026 | Unit (Mockito) + integration (Testcontainers) — разные слои | Только integration | Unit быстрые (0.35s), ловят логику; integration ловят JPA/Flyway/HTTP. Слои не смешиваются |

## M2 — inventory-service

| Дата | Решение | Альтернативы | Почему |
|---|---|---|---|
| 20.11.2026 | Singleton Testcontainers: `static { POSTGRES.start(); }` в abstract, без `@Testcontainers`/`@Container` | `@Testcontainers` на abstract | `@Container` в abstract не singleton: 2 наследника → перезапуск контейнера → `Hikari total=0`, `ConnectException`, 4m13s → 30s |
| 20.11.2026 | Идемпотентность: SHA-256 payload hash + cached response + 409 при mismatch | Дедуп по ключу, без хэша | Повтор с тем же телом — кэш; с другим телом — 409, не «тихо перезаписали». Контракт явный |
| 22.11.2026 | Outbox пишется в **одной транзакции** с бизнес-изменением (`OutboxService.record`) | Асинхронная запись, Kafka Transactions | Гарантия at-least-once: БД и намерение отправить атомарны. Kafka Transactions не решают окно с БД |
| 22.11.2026 | Relay — `@Scheduled` + `findByStatusOrderByCreatedAtAsc` | CDC, Debezium, Kafka Transactions | Проще, нет внешней инфраструктуры, достаточно для MVP. SKIP LOCKED — следующий шаг при нагрузке |
| 23.11.2026 | `spring-kafka` + `@ServiceConnection` для Kafka Testcontainers | Ручной `KafkaTemplate` конфиг | Boot-стиль, единообразие с Postgres |
| 25.11.2026 | A5.4: отключить `@Scheduled` в тестах через `application-test.properties` (`initial-delay=3600000`) | `@ConditionalOnProperty` на relay | Test-properties — точечно, не трогает прод-код. GAP NPE/race закрыт. `@ConditionalOnProperty` — backlog (B) |
| 27.11.2026 | `withImagePullPolicy(image -> System.getenv("CI") != null)` + убрать глобальный `testcontainers.image.pull=never` | Безусловный `never` (как было) | CI: pull (кэша нет → Docker 404 без pull). Локально: never (обход resolve-таймаута Docker 29, 9 мин → 44s). Глобальный `never` — ложный рычаг, не работал |

## Противоречие ТЗ 5.4 ↔ код (закрыто 27.11.2026)

| Дата | Решение | Альтернативы | Почему |
|---|---|---|---|
| 27.11.2026 | Привести код `inventory-service` к ТЗ 5.4: таблица `inventory`, поля `available_qty` + `reserved_qty` (вместо текущего `stock` с `total_quantity` + `reserved_quantity`). Действие — в backlog `A1-refactor`, до M3 | Оставить код (`stock`/`total_quantity`), обновить ТЗ | Модель `available_qty` прямее для OMS: резерв = `UPDATE ... WHERE available_qty >= :qty` — одна колонка в условии. Инвариант в БД проще: `CHECK (available_qty >= 0)`. ТЗ — авторское решение, код отклонился без записи — это GAP |
