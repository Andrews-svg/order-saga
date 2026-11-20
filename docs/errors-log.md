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
| 17.11.2026 | Testcontainers 1.21.0 не стартует с Docker 29 (API 1.56): `ContainerFetchException` / `IllegalStateException at DockerClientProviderStrategy` | Spring Boot 3.5.0 BOM жёстко фиксирует Testcontainers 1.21.0. Переопределить: `ext["testcontainers.version"] = "1.21.4"` в build.gradle.kts (ext-свойство > BOM). Проверка фактом: `./gradlew :product-service:dependencies --configuration testRuntimeClasspath | Select-String "testcontainers:testcontainers"` — версия без `-> 1.21.0` |
| 17.11.2026 | `Get-Content` в PS 5.1 показывает mojibake (`Р–СѓСЂРЅР°Р»` вместо `Журнал`) для UTF-8 без BOM | PS 5.1 читает по умолчанию в ANSI. Читать с явным encoding: `[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)` или `Get-Content -Encoding UTF8`. Файл может быть цел — сломан только дисплей. Не перезаписывать до проверки |
| 17.11.2026 | `.Replace()` с многострочным шаблоном не нашёл код (M6, первый провал): файл CRLF (`\r\n`), в PS-строке LF (`` `n ``) | Сверять CRLF/LF до `.Replace`. Надёжнее `-replace '(?m)^(\s*)...'` — regex не зависит от `\r`. Факт: `Select-String "MUTATION"` после правки |
| 17.11.2026 | Склейка команд: `git log -1 origin/mastergit status --short` → `fatal: ambiguous argument 'origin/mastergit'` | PS 5.1 склеивает при вставке из буфера. Вставлять блоками по 1–3, проверять вывод. `fatal: ambiguous argument` с «хвостом» = склейка |
| 18.11.2026 | Ложное «мутация не легла» (M6): replace записал `/* M6a */`, grep искал `MUTATION M6` — пусто | **Grep-паттерн = строка из replace буква в букву.** Перед выводом «не легло» — сверить: что записано vs что ищем. Разведка `-split`/`IndexOf` по файлу — факт |
| 19.11.2026 | `ParserError: Непредвиденная лексема "ы"` (M10-запись): `unit'ы` внутри `'...'` — апостроф закрыл строку | В PS 5.1 апостроф внутри `'...'` ломает строку. Варианты: `unit''ы` (двойной), `"..."`, или here-string `@'...'@`. Проверять `$line.Length` перед WriteAllText |
| 20.11.2026 | `@Testcontainers` + `@Container` в abstract-классе — не singleton: два наследника → контейнер перезапускается, `@ServiceConnection` запоминает адрес первого → `Hikari total=0`, `ConnectException`, 30s таймаут × 7 тестов (4m13s) | Для shared-контейнера: убрать `@Testcontainers`/`@Container`, `static { POSTGRES.start(); }` — один контейнер на JVM. Время: 4m13s → 30s. Симптом: `Hikari total=0` + `ConnectException` — смотреть порт, не SQL |
