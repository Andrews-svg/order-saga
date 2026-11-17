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