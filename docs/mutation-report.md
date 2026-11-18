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
