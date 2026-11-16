# Журнал ошибок — order-saga

**Проект:** order-saga — OMS Transactional Core
**Создан:** 14.11.2026
**Статус:** живой документ

## Правила ведения

- Каждый случай — запись: ситуация / урок.
- Урок конкретный, проверяемый. Не «быть внимательнее», а «открыть файл до действия».
- Пополняется в момент, когда случай произошёл.

## M0 — Foundation

| Дата | Ситуация | Урок |
|---|---|---|
| 14.11.2026 | `kafka-topics.sh: not found` в healthcheck | В официальном образе скрипт в `/opt/kafka/bin/`, а не в `PATH`. Проверять через `docker exec ... ls /path` |
| 14.11.2026 | `--force-recreate` не применил новый healthcheck | `docker compose rm -sf <service>; docker compose up -d <service>` — надёжнее |
| 14.11.2026 | YAML: `volumes:` съехал внутрь `order-service` | `volumes:` — корневой ключ (0 пробелов), сосед `services:`. VS Code автоотступает; проверять `Home` в строке |
| 14.11.2026 | Двойной `test:` в одном `healthcheck` | YAML берёт последнее значение. `grep -n "kafka-topics"` после правки — факт |
| 14.11.2026 | Правка визуально правильная, `grep` показывает старое | Save (`Ctrl+S`) ≠ визуальная правка в буфере. Проверять с диска (`grep`, `Get-Content`) |
| 14.11.2026 | Локальный Windows-путь в `gradle.properties` | Ломает Linux CI (`Java home supplied is invalid`). Только `JAVA_HOME` |
| 14.11.2026 | `./gradlew: Permission denied` в CI (exit 126) | `git update-index --chmod=+x gradlew` — бит в git, не только на диске |
| 14.11.2026 | `JAVA_HOME` = `...\bin\java.exe` | `JAVA_HOME` — корень JDK (`...\jdk-21.0.11`), не бинарник |
| 14.11.2026 | `Ctrl+S` перепутан с `git commit` | Commit — снимок в истории; Docker и bash читают диск. Save первичен |
| 14.11.2026 | PowerShell: `&&` — ошибка `Лексема "&&" не является...` | PS 5.1 не поддерживает `&&`. Использовать `;` или `if ($?) { }` |
| 14.11.2026 | `wc`, `head`, `tail` в PowerShell — `не распознано` | Это bash-команды. PS-эквиваленты: `(Get-Content X).Count`, `Get-Content X -TotalCount N`, `-Tail N` |

## M1 — product-service

| Дата | Ситуация | Урок |
|---|---|---|
| 16.11.2026 | Word → markdown: потеряны 8 fence-открытий, 9 закрытий, 49 заголовков (`##`/`###`), табы в таблицах остались | После вставки из Word проверить `Select-String '```'` (число fence) и `Select-String "`t"` (число табов). Восстановление: fence-открытие — regex `^(text\|sql\|json)$`; заголовки — `^(Раздел \d+\.)` → `## $1`; подразделы — `^(\d+\.\d+\. )` → `### $1`; таблицы с табами — обернуть в ` ```text `-блоки (≥2 строк с табом подряд) |
| 16.11.2026 | `Set-Content -Encoding UTF8` в PowerShell 5.1 добавил BOM (`п»ї# ...` в первой строке) — `git diff` с шумом | Для чистого UTF-8: `[System.IO.File]::WriteAllText($path, $c, (New-Object System.Text.UTF8Encoding($false)))`. BOM виден в `git diff` первой строкой — проверять после каждого PS-скрипта, пишущего в файл |
| 16.11.2026 | Термины backtick / fence / pipe / "наклонная одинарная" — каждый раз уточняли, что нажимать | В командах указывать клавишу (Ё, слева от 1) и визуальный образ. "Наклонная одинарная" (Ё) = backtick. Не путать с "одинарной кавычкой" (Э) — это разные символы, только backtick экранирует HTML и открывает код |
