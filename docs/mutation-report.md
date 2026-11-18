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
