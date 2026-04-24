# Dynamic Webhook Service

Высокопроизводительный сервис для создания динамических HTTP-эндпоинтов (вебхуков) с возможностью приёма, инспекции, трансформации и проксирования запросов.

## Технологии

- **Java 21** (LTS) — виртуальные потоки, records, pattern matching
- **Eclipse Vert.x 4.5+** — реактивный неблокирующий фреймворк
- **PostgreSQL 16** — реляционная база данных с JSONB
- **Vert.x PgClient** — асинхронный драйвер для PostgreSQL
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

Настройки в `application.yaml`:

```yaml
server:
  port: 8080

database:
  url: jdbc:postgresql://localhost:5432/webhooks
  user: webhook_user
  password: webhook_pass
  pool-size: 20
  connection-timeout: 30000

proxy:
  timeout-ms: 10000
  max-retries: 0

webhook:
  max-log-count-default: 100
  cleanup-interval-hours: 24
```

## Архитектура

### Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────┐
│                   HTTP Server (:8080)                   │
│  ┌───────────────────────────────────────────────────┐  │
│  │                   Vert.x Router                   │  │
│  │  /api/webhooks/*         -> WebhookApiHandler     │  │
│  │  /api/webhooks/:id/r/*   -> RequestLogHandler     │  │
│  │  /webhook/:slug          -> WebhookReceiverHandler│  │
│  │  /api/templates/*        -> TemplateHandler       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────┐  ┌─────────────────┐  ┌────────────┐  │
│  │WebhookService│  │RequestLogService│  │ProxyService│  │
│  └──────┬───────┘  └────────┬────────┘  └─────┬──────┘  │
│         │                   │                 │         │
│         └───────────────────┴─────────────────┘         │
│                             ▼                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │             Repository Layer (Pool)               │  │
│  │  PgWebhookRepository  +  PgRequestLogRepository   │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
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
WebhookReceiverHandler
        │
        ├── findBySlug (cache/N instance) ─-> DB
        │
        ├── saveRequestLog ────────────────-> DB
        │
        ├── (optional) forward ────────────-> External URL
        │
        └── return response
```

### Производительность

- **N instances** Vert.x verticle = N event loops (по числу CPU cores)
- **pool-size: 20** соединений на инстанс
- **gzip compression** для HTTP ответов
- **tcpNoDelay + reusePort** для снижения latency
- **Async DB** — неблокирующие операции

## API Endpoints

### Webhooks CRUD

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/api/webhooks` | Создать вебхук |
| GET | `/api/webhooks` | Список (пагинация) |
| GET | `/api/webhooks/:id` | По ID |
| GET | `/api/webhooks/slug/:slug` | По slug |
| PUT | `/api/webhooks/:id` | Обновить |
| DELETE | `/api/webhooks/:id` | Удалить |
| PATCH | `/api/webhooks/:id/toggle` | Вкл/выкл |

### Request Logs

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/api/webhooks/:id/requests` | История (пагинация) |
| GET | `/api/webhooks/:id/requests/:reqId` | Детали запроса |
| DELETE | `/api/webhooks/:id/requests` | Очистить историю |
| GET | `/api/webhooks/:id/stats` | Статистика |

### Templates

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/api/templates/preview` | Предпросмотр шаблона |

### Dynamic Endpoints

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET/POST | `/webhook/:slug` | Приём запросов |

## Docker

```bash
docker-compose up --build -d
```

## Тестирование

```bash
mvn test              # Все тесты
mvn compile           # Только компиляция
mvn package -DskipTests  # Сборка JAR
```

## Структура проекта

```
webhook-service/
├── src/main/java/com/webhookservice/
│   ├── App.java                    # Точка входа
│   ├── MainVerticle.java           # HTTP сервер, роутинг
│   ├── config/AppConfig.java       # Конфигурация
│   ├── handler/                   # HTTP обработчики
│   ├── service/                   # Бизнес-логика
│   ├── repository/impl/           # Vert.x PgClient repos
│   ├── model/                    # Доменные модели
│   └── util/                     # Утилиты
├── src/main/resources/
│   ├── application.yaml           # Конфигурация
│   ├── db/migration/             # Flyway миграции
│   └── webroot/                 # Frontend статика
├── src/test/                     # Тесты
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

## Команда

- **Марат** — Backend (CRUD, Proxy, Infrastructure, Performance)
- **Ксения** — Backend (Receiver, Logging, Template), Frontend, Swagger