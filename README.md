# Dynamic Webhook Service

Высокопроизводительный сервис для создания динамических HTTP-эндпоинтов (вебхуков) с возможностью приёма, инспекции, трансформации и проксирования запросов.

## Технологии

- **Java 21** (LTS) — виртуальные потоки, records, pattern matching
- **Eclipse Vert.x 4.5+** — реактивный неблокирующий фреймворк
- **PostgreSQL 16** — реляционная база данных с JSONB
- **Vert.x PgClient** — асинхронный драйвер для PostgreSQL (pipelining-limit: 256)
- **Caffeine 3.1.8** — in-process кэш (W-TinyLFU, per-verticle, zero lock contention)
- **Vert.x Circuit Breaker 4.5.1** — защита от каскадных сбоев при проксировании
- **Maven 3.9+** — сборка проекта
- **Docker & Docker Compose** — контейнеризация

## Быстрый старт

### Требования

- Java 21 или выше
- Maven 3.9+
- Docker и Docker Compose

### Запуск с Docker Compose

```bash
docker-compose up --build -d
```

Сервис будет доступен по адресу: `http://localhost:8080`
- Web UI: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger/`
- API Health: `http://localhost:8080/api/health`

Для демо-окружения admin API key по умолчанию — `password` (переопределяется через `ADMIN_API_KEY`).

### Остановка

```bash
docker-compose down
```

### Локальная разработка

1. Запустить PostgreSQL:
```bash
docker-compose up -d db
```

2. Собрать проект:
```bash
mvn clean package
```

3. Запустить приложение:
```bash
java -jar target/webhook-service.jar
```

### Конфигурация

Настройки в `application.yaml`. Все значения переопределяются переменными окружения:

```yaml
server:
  port: 8080

database:
  url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/webhooks}
  user: ${DATABASE_USER:webhook_user}
  password: ${DATABASE_PASSWORD:webhook_password}
  pool-size: 32
  pipelining-limit: 256
  connection-timeout: 30000

proxy:
  timeout-ms: 10000
  max-retries: 3
  retry:
    base-delay-ms: 100
    max-delay-ms: 5000
    multiplier: 2.0
    jitter: true
    retry-on-status-5xx: true
  circuit-breaker:
    enabled: true
    max-failures: 5
    reset-ms: 3000
    timeout-ms: 10000

auth:
  enabled: true
  admin-api-key: ${ADMIN_API_KEY:password}  # сменить перед деплоем!

cache:
  enabled: true        # false = все кэши отключены (для сравнительных замеров)
  webhook:
    max-size: 10000
    ttl-seconds: 300
    negative-ttl-seconds: 30
  stats:
    max-size: 1000
    ttl-seconds: 30
  template:
    max-size: 1000
    ttl-seconds: 1800

request-log:
  batch:
    enabled: true
    max-size: 100      # flush при накоплении 100 записей
    flush-ms: 100      # или каждые 100 мс

webhook:
  max-log-count-default: 100
  cleanup-interval-hours: 24
```

## Что умеет продукт

Webhook Service превращает входящие HTTP-события в управляемые endpoint'ы:

- **Dashboard вебхуков:** быстрый поиск, статусы active/debug, копирование endpoint URL в один клик, включение/выключение и безопасное удаление.
- **Создание и редактирование:** поля сгруппированы как `основное → проксирование → шаблоны`, есть inline-валидация, preview request/response шаблонов и подсказки по синтаксису.
- **Инспекция запросов:** история с фильтрами по HTTP-методу и proxy status, пагинацией без перезагрузки, JSON-viewer для headers/query/body/proxy response.
- **Проксирование:** optional `proxyUrl`, кастомные proxy headers, retry с exponential backoff и circuit breaker для защиты hot path.
- **Шаблонизатор:** переменные `{{body.field}}` и `${body.field}`, условия `{{#if}}`, `{{else}}`, циклы `{{#each}}`, fallback `|`/`??`, metadata `{{@index}}`, `{{@first}}`, `{{@last}}`, `{{@key}}`.
- **Cache/Auth UX:** cache hit-ratio badge с tooltip, подтверждаемый Flush, дружелюбный login overlay для `X-API-Key` и повторный запрос ключа при 401.

## Архитектура

### Компонентная диаграмма

```
┌──────────────────────────────────────────────────────────────┐
│                      HTTP Server (:8080)                     │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                      Vert.x Router                     │  │
│  │  /api/webhooks/*          -> WebhookApiHandler         │  │
│  │  /api/webhooks/:id/r/*    -> RequestLogHandler         │  │
│  │  /webhook/:slug           -> WebhookReceiverHandler    │  │
│  │  /api/templates/*         -> TemplateHandler           │  │
│  │  /api/cache/*             -> CacheStatsHandler         │  │
│  │  /api/auth/verify         -> ApiKeyAuthHandler         │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────┐  ┌─────────────────┐  ┌───────────────┐   │
│  │WebhookService│  │RequestLogService│  │ ProxyService  │   │
│  │  + cache     │  │  + batch write  │  │ + CB + retry  │   │
│  └──────┬───────┘  └────────┬────────┘  └──────┬────────┘   │
│         │                   │                  │            │
│  ┌──────▼───────────────────▼──────────────────┘            │
│  │              CacheManager (per-verticle)                 │
│  │  WebhookCache (slug/id/negative) + StatsCache            │
│  │  Vert.x EventBus → инвалидация между инстансами          │
│  └──────────────────────────┬───────────────────────────────│
│                             │ cache miss                    │
│  ┌──────────────────────────▼───────────────────────────┐   │
│  │               Repository Layer (PgPool)              │   │
│  │  PgWebhookRepository  +  PgRequestLogRepository      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────-┘
                              │
                              ▼
                     ┌──────────────┐
                     │  PostgreSQL  │
                     └──────────────┘
```

### Hot Path (запрос к вебхуку)

```
POST /webhook/:slug
        │
        ▼
ApiKeyAuthHandler (публичный маршрут — пропускает без проверки)
        │
        ▼
WebhookReceiverHandler
        │
        ├── WebhookCache.getBySlug(slug)
        │       ├── HIT  → ~50 нс, БД не трогается
        │       └── MISS → PgWebhookRepository → кэш
        │
        ├── RequestLogService.save()  →  batch queue → flush каждые 100мс/100 шт
        │
        ├── (если proxyUrl задан) ProxyService.forwardWithRetry()
        │       ├── retry: exponential backoff + full jitter (до 3 попыток)
        │       └── circuit breaker: fast-fail после 5 сбоев подряд
        │
        └── return {"status":"received","logId":"..."}
```

## Производительность и кэширование

### Многоуровневое кэширование (Caffeine)

Сервис использует three-tier кэш поверх PostgreSQL. Все кэши — per-verticle (zero lock contention на event loop), инвалидация координируется через Vert.x EventBus pub/sub.

| Кэш | Ключ | TTL | Max size | Назначение |
|-----|------|-----|----------|------------|
| `webhookBySlug` | slug (String) | 300 с | 10 000 | Основной горячий путь `/webhook/:slug` |
| `webhookById` | UUID | 300 с | 10 000 | Lookup для CRUD-операций |
| `negativeSlug` | slug (String) | 30 с | 10 000 | Защита от сканирования несуществующих slug |
| `stats` | webhookId | 30 с | 1 000 | Кэш агрегированной статистики |
| `compiledTemplate` | SHA-256 строки шаблона | 1800 с | 1 000 | AST скомпилированных шаблонов |

**Стратегия инвалидации:**

- Мутации (`create`, `update`, `delete`, `toggle`) публикуют `CacheInvalidationEvent` через EventBus.
- Каждый инстанс `MainVerticle` подписан на адрес `cache.invalidate` и сбрасывает свою локальную копию.
- Negative cache сбрасывается при `UPSERT`, но не при `DELETE` — после удаления новый запрос по slug корректно попадёт в negative cache естественным путём.

**Что это даёт на практике:**

- При cache hit `getBySlug` возвращает за ~50 нс вместо 50–200 µs (round-trip к PostgreSQL).
- На стационарном наборе из ~100 активных вебхуков hit ratio ≥ 99% → 99 из 100 запросов вообще не идут в БД.
- Negative cache при боте, сканирующем случайные slug: первый запрос на каждый slug — БД, следующие 30 секунд — кэш.

### Наблюдаемость кэша

```bash
# Текущие метрики всех кэшей
curl -H "X-API-Key: password" http://localhost:8080/api/cache/stats

# Пример ответа
{
  "caches": {
    "webhookBySlug":    { "size": 42, "hitCount": 4999, "missCount": 1, "hitRatio": 0.9998, "evictionCount": 0 },
    "webhookById":      { "size": 42, "hitCount": 112,  "missCount": 42, "hitRatio": 0.727,  "evictionCount": 0 },
    "negativeSlug":     { "size": 3,  "hitCount": 97,   "missCount": 3,  "hitRatio": 0.97,   "evictionCount": 0 },
    "stats":            { "size": 5,  "hitCount": 28,   "missCount": 5,  "hitRatio": 0.848,  "evictionCount": 0 },
    "compiledTemplate": { "size": 3,  "hitCount": 95,   "missCount": 3,  "hitRatio": 0.969,  "evictionCount": 0 }
  }
}

# Сброс всех кэшей (например, после ручного изменения в БД)
curl -X POST -H "X-API-Key: password" http://localhost:8080/api/cache/flush
```

**Как запустить smoke-benchmark:**

```bash
# Создать тестовый вебхук (slug автогенерируется из name: "bench-hook" → "bench-hook")
curl -s -X POST http://localhost:8080/api/webhooks \
  -H "Content-Type: application/json" \
  -H "X-API-Key: password" \
  -d '{"name":"bench-hook","methods":"POST"}' | jq .

# Создать файл с телом запроса для ab
echo '{"test":true}' > /tmp/body.json

# Снапшот ДО
curl -s -H "X-API-Key: password" http://localhost:8080/api/cache/stats | jq '.caches.webhookBySlug'

# 5000 одинаковых запросов
ab -n 5000 -c 50 -p /tmp/body.json -T application/json http://localhost:8080/webhook/bench-hook

# Снапшот ПОСЛЕ — hitRatio должен быть ≥ 0.99
curl -s -H "X-API-Key: password" http://localhost:8080/api/cache/stats | jq '.caches.webhookBySlug'
```

Для сравнения без кэша **пересобирать не нужно** — `cache.enabled` читается из env `CACHE_ENABLED`
(`application.yaml`: `enabled: ${CACHE_ENABLED:true}`). Достаточно перезапустить контейнер:

```bash
CACHE_ENABLED=false docker compose up -d --force-recreate app
```

### Нагрузочное тестирование (k6) — Демо 6

Актуальный нагрузочный стенд лежит в [`load-tests/`](load-tests/) и отвечает на вопрос:
какой RPS сервис держит устойчиво при заданных SLO.

| Файл | Назначение |
|------|------------|
| `run-capacity.sh` | Главный запуск: поднимает стек, прогоняет матрицу cache/verticles/workloads, сохраняет результаты. |
| `capacity.js` | Staircase и soak-тест в open-model режиме (`constant-arrival-rate`). |
| `spike.js` | Резкий всплеск нагрузки выше устойчивой точки. |
| `ci-check.js` | Быстрый smoke для CI/локальной проверки. |
| `mock-target.js` | Upstream-заглушка для proxy-сценариев. |
| `gen-capacity-report.js` | Генерация итогового [`load-tests/CAPACITY.md`](load-tests/CAPACITY.md). |

```bash
cd load-tests
QUICK=1 ./run-capacity.sh   # короткая проверка
./run-capacity.sh           # полный прогон
```

Сценарии покрывают hot path `/webhook/:slug`, proxy/template path и CRUD `/api/webhooks`.
Матрица сравнивает cache `on/off` и разное число Vert.x verticles. В отчёте фиксируются
max sustainable RPS @ SLO, p50/p95/p99, error-rate, dropped iterations, soak и spike.

### Batch insert для логов

`RequestLogService` накапливает входящие логи в очередь и делает flush окном: **100 мс или 100 записей** (что наступит раньше). Вместо 1000 одиночных INSERT в секунду при 1000 RPS — 10 batch INSERT. Overhead PostgreSQL на парсинг и planning снижается в 10–20 раз.

### PgPool pipelining

`setPipeliningLimit(256)` позволяет одному соединению держать до 256 запросов «в полёте» одновременно (PostgreSQL ставит их в очередь execution). На pool из 32 соединений это эквивалентно пропускной способности pool из 8192 классических соединений при write-heavy сценарии.

### Circuit Breaker для ProxyService

Если внешний proxy target начинает отказывать, circuit breaker открывается после 5 последовательных сбоев и следующие 3 секунды все запросы получают `503` за миллисекунды — без ожидания 10-секундного таймаута. Это предотвращает накопление backlog на event loop.

```
Retry strategy:  exponential backoff + full jitter
  base: 100мс → 200мс → 400мс → ... max: 5000мс
  4xx: никогда не ретраить
  5xx + network error: до 3 попыток (configurable)
  Circuit breaker: снаружи retry-цикла (один retry = одно «обращение»)
```

## Безопасность

### Admin API (X-API-Key)

Все `/api/*` эндпоинты (кроме `/api/health`) защищены заголовком `X-API-Key`.  
Публичные маршруты (без аутентификации): `/webhook/:slug`, `/api/health`, `/swagger/*`, `/docs/*`, статика.

```bash
# Пример запроса с ключом
curl -H "X-API-Key: password" http://localhost:8080/api/webhooks

# Проверка что ключ валиден
curl -H "X-API-Key: password" http://localhost:8080/api/auth/verify
# 200 OK — ключ принят
# 401 Unauthorized — неверный ключ
```

**Задать свой ключ:**

```bash
# В docker-compose.yml — добавить в environment сервиса app:
ADMIN_API_KEY: my-secret-key

# Или при локальном запуске:
ADMIN_API_KEY=my-secret-key java -jar target/webhook-service.jar
```

Сравнение ключей — constant-time через SHA-256 + `MessageDigest.isEqual` (нейтрализует timing attack и length leak).

**Web UI:** при открытии браузера появляется login overlay с полем для ввода ключа. После ввода он сохраняется в `localStorage` и автоматически подставляется во все запросы к `/api/*`. При получении 401 overlay показывается снова.

## API Endpoints

### Webhooks CRUD

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| POST | `/api/webhooks` | Создать вебхук | ✅ |
| GET | `/api/webhooks` | Список (пагинация) | ✅ |
| GET | `/api/webhooks/:id` | По ID | ✅ |
| GET | `/api/webhooks/slug/:slug` | По slug | ✅ |
| PUT | `/api/webhooks/:id` | Обновить | ✅ |
| DELETE | `/api/webhooks/:id` | Удалить | ✅ |
| PATCH | `/api/webhooks/:id/toggle` | Вкл/выкл | ✅ |

### Request Logs

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| GET | `/api/webhooks/:id/requests?page=0&size=20&method=POST&status=2xx` | История с фильтрами и пагинацией | ✅ |
| GET | `/api/webhooks/:id/requests/:reqId` | Детали запроса | ✅ |
| DELETE | `/api/webhooks/:id/requests` | Очистить историю | ✅ |
| GET | `/api/webhooks/:id/stats` | Статистика | ✅ |

Фильтр `status` принимает `2xx`, `3xx`, `4xx`, `5xx`, точный код `200`..`599` или `none`
для запросов без proxy response.

### Templates

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| POST | `/api/templates/preview` | Предпросмотр шаблона | ✅ |

Preview возвращает `ok=true/result` либо `ok=false/error/line/column/snippet`, поэтому UI может
показывать ошибку шаблона рядом с редактором без сохранения вебхука.

### Cache

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| GET | `/api/cache/stats` | Метрики всех кэшей (hit ratio, evictions, size) | ✅ |
| POST | `/api/cache/flush` | Сброс всех кэшей | ✅ |

### Auth

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| GET | `/api/auth/verify` | Проверка X-API-Key (200 OK = валиден) | ✅ |
| GET | `/api/health` | Health check | — |
| GET | `/metrics` | Prometheus scrape endpoint | — |

### Dynamic Endpoints

| Метод | Endpoint | Описание | Auth |
|-------|----------|----------|------|
| ANY | `/webhook/:slug` | Приём запросов на вебхук | — |

## Демо-сценарий

1. Открыть `http://localhost:8080/`, ввести admin API key (`password` в demo-compose).
2. На пустом dashboard нажать **Создать вебхук**.
3. Заполнить название `Demo GitHub Events`, методы `POST,GET`, включить debug.
4. В блоке шаблонов вставить request template и нажать **Preview**:

```json
{
  "event": "{{body.event | \"unknown\"}}",
  "repo": "{{body.repository.name | \"demo-repository\"}}",
  "items": "{{#each body.items}}{{@index}}:{{name}};{{/each}}"
}
```

5. Сохранить, скопировать endpoint URL кнопкой copy.
6. Отправить тест из UI или curl:

```bash
curl -X POST http://localhost:8080/webhook/<slug> \
  -H "Content-Type: application/json" \
  -d '{"event":"push","repository":{"name":"demo"},"items":[{"name":"build"},{"name":"deploy"}]}'
```

7. В деталях вебхука показать статистику, фильтры истории по `POST`/`2xx`, JSON-viewer body/headers/proxy response.
8. Нажать cache badge tooltip, затем **Flush** с подтверждением. Для Grafana/метрик запустить `bash demo/load.sh`.

## Docker

```bash
docker compose up --build -d
```

- **Multi-stage сборка** (`Dockerfile`): Maven-build → JRE-runtime, на выходе fat JAR.
- **Container-aware JVM** (Демо 6): `JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"` —
  heap масштабируется от cgroup-лимита контейнера, G1GC держит низкий p99 под нагрузкой,
  OOM приводит к рестарту инстанса вместо «зомби».
- **Healthcheck** на обоих сервисах: `db` через `pg_isready`, `app` через `curl /api/health`.
  `app` стартует только после `db: service_healthy`.
- Переключатели через env: `CACHE_ENABLED` (toggle кэша), `VERTICLE_INSTANCES` (число event loop),
  `ADMIN_API_KEY` (admin-ключ).

## Тестирование

```bash
mvn test                 # Все тесты + JaCoCo-отчёт (target/site/jacoco/)
mvn verify               # Полный цикл: тесты + fat JAR
mvn compile              # Только компиляция
mvn package -DskipTests  # Сборка JAR без тестов
```

**Покрытие:** `mvn test` автоматически запускает JaCoCo (`prepare-agent` + `report`).
HTML-отчёт — `target/site/jacoco/index.html`, CSV — `target/site/jacoco/jacoco.csv`.
Ориентир Демо 6: **30–40% line coverage** по backend / **65–75%** по критичным flow
(фактически на момент защиты — ~57% line, ~49% branch при 161 зелёном тесте).

## CI/CD (GitHub Actions)

`.github/workflows/ci.yml` — на каждый push/PR в `main`/`develop`:

1. **build-and-test** — `mvn -B clean verify` на JDK 21 (Temurin), с Maven-кэшем.
   Прогоняет unit- и Testcontainers-интеграционные тесты (Docker на `ubuntu-latest` доступен),
   собирает fat JAR и JaCoCo-отчёт. Артефакты — coverage-отчёт и surefire-reports — загружаются
   через `actions/upload-artifact`.
2. **docker** — после успешных тестов собирает Docker-образ (`docker build`), проверяя что
   multi-stage Dockerfile валиден.

> Прежний `ci.yml` вызывал `mvn jacoco:report` и `mvn checkstyle:check` при не подключённых
> плагинах — отчёт получался пустым, а `checkstyle:check` падал без конфигурации. К Демо 6
> добавлен `jacoco-maven-plugin` (через `@{argLine}` в surefire), а CI приведён к рабочему виду.

### Релизы и защита main

`.github/workflows/release.yml` — на push в `main` [release-please](https://github.com/googleapis/release-please)
по Conventional Commits ведёт «Release PR» и считает следующую версию (`fix:`→patch, `feat:`→minor,
`feat!:`/`BREAKING CHANGE`→major). При мёрже Release PR создаётся тег `vX.Y.Z`, GitHub Release,
CHANGELOG; fat JAR прикладывается к релизу, а Docker-образ пушится в **GHCR** (`:X.Y.Z` и `:latest`).

Запрет прямого пуша в `main` и обязательные проверки (`build-and-test`, `docker`) перед мёржем —
это настройка ruleset репозитория на GitHub (не YAML). Команда `gh`, готовый ruleset и детали
версионирования — в [`.github/RELEASE_AND_PROTECTION.md`](.github/RELEASE_AND_PROTECTION.md).

## Структура проекта

```
webhook-service/
├── src/main/java/com/webhookservice/
│   ├── App.java                        # Точка входа
│   ├── MainVerticle.java               # HTTP сервер, роутинг, wiring
│   ├── auth/
│   │   └── ApiKeyAuthHandler.java      # X-API-Key аутентификация
│   ├── cache/                          # Кэш-слой (Caffeine + EventBus)
│   │   ├── CacheManager.java           # Facade, владеет всеми кэшами
│   │   ├── WebhookCache.java           # slug/id/negative кэши
│   │   ├── StatsCache.java             # кэш статистики
│   │   ├── CaffeineCache.java          # Generic wrapper с enabled-toggle
│   │   ├── CacheInvalidationEvent.java # Событие инвалидации (UPSERT/DELETE/FLUSH_ALL)
│   │   ├── CacheInvalidationCodec.java # MessageCodec для EventBus
│   │   ├── CacheMetricsSnapshot.java   # DTO агрегата для /api/cache/stats
│   │   ├── CachePerCacheMetrics.java   # DTO метрик одного кэша
│   │   └── CacheNames.java             # Константы имён кэшей и EventBus-адресов
│   ├── config/AppConfig.java           # Конфигурация (все поля)
│   ├── handler/                        # HTTP обработчики
│   │   ├── WebhookApiHandler.java
│   │   ├── RequestLogHandler.java
│   │   ├── WebhookReceiverHandler.java
│   │   ├── TemplateHandler.java
│   │   └── CacheStatsHandler.java      # /api/cache/stats + /api/cache/flush
│   ├── service/
│   │   ├── WebhookService.java         # CRUD + cache-aside + EventBus publish
│   │   ├── RequestLogService.java      # Batch write + StatsCache
│   │   ├── ProxyService.java           # Circuit breaker + retry
│   │   ├── RetryPolicy.java            # Exponential backoff + full jitter
│   │   └── TemplateService.java
│   ├── repository/impl/               # Vert.x PgClient репозитории
│   ├── template/                      # Шаблонизатор
│   ├── model/                         # Доменные модели, DTO
│   └── util/                          # Утилиты
├── src/main/resources/
│   ├── application.yaml               # Конфигурация
│   ├── db/migration/                  # Flyway миграции
│   └── webroot/                       # Frontend статика
│       └── js/
│           └── auth.js                # Login overlay + X-API-Key monkey-patch
├── src/test/java/com/webhookservice/
│   ├── cache/                         # Тесты кэш-слоя (39 тестов)
│   ├── service/                       # Тесты сервисов
│   ├── handler/                       # Тесты обработчиков
│   └── auth/                          # Тесты ApiKeyAuthHandler
├── load-tests/                       # k6 нагрузочные тесты (Демо 6)
│   ├── lib/common.js                 # Общий конфиг + handleSummary
│   ├── capacity.js                   # Staircase/soak open-model сценарий
│   ├── spike.js                      # Spike-тест
│   ├── ci-check.js                   # Быстрый smoke для CI
│   ├── mock-target.js                # Node upstream для proxy-сценария
│   ├── run-capacity.sh               # Прогон матрицы cache/verticles/workloads
│   ├── compute-sustainable.js        # Расчёт max sustainable RPS @ SLO
│   ├── gen-capacity-report.js        # Агрегация results/*.json в markdown
│   └── CAPACITY.md                   # Результаты + SLI/SLO/SLA
├── .github/workflows/ci.yml          # CI/CD pipeline
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

## Команда

- **Марат** — Backend (CRUD, Proxy, Infrastructure, Performance)
- **Ксения** — Backend (Receiver, Logging, Template), Frontend, Swagger
