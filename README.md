# Dynamic Webhook Service

Высокопроизводительный сервис для создания динамических HTTP-эндпоинтов (вебхуков) с возможностью приёма, инспекции, трансформации и проксирования запросов.

## Технологии

- **Java 21** (LTS)
- **Eclipse Vert.x 4.5+**
- **PostgreSQL 16**
- **Maven 3.9+**
- **Docker & Docker Compose**

## Быстрый старт

### Требования

- Java 21 или выше
- Maven 3.9+
- Docker и Docker Compose

### Запуск с Docker Compose

```bash
docker-compose up --build
```

Сервис будет доступен по адресу: `http://localhost:8080`

### Локальная разработка

1. Запустить PostgreSQL:
```bash
docker-compose up db
```

2. Собрать проект:
```bash
mvn clean package
```

3. Запустить приложение:
```bash
java -jar target/webhook-service.jar
```

### Тестирование

Запуск всех тестов:
```bash
mvn test
```

## Структура проекта

```
webhook-service/
├── src/
│   ├── main/
│   │   ├── java/com/webhookservice/   # Исходный код
│   │   └── resources/                 # Конфигурация и ресурсы
│   └── test/                          # Тесты
├── docs/                              # Документация
├── pom.xml                            # Maven конфигурация
├── Dockerfile                         # Docker образ
└── docker-compose.yml                 # Docker Compose конфигурация
```

## API Endpoints

- `GET /api/health` - Health check
- `POST /api/webhooks` - Создать вебхук
- `GET /api/webhooks` - Список всех вебхуков
- `GET /api/webhooks/:id` - Получить вебхук по ID
- `PUT /api/webhooks/:id` - Обновить вебхук
- `DELETE /api/webhooks/:id` - Удалить вебхук
- `GET/POST /webhook/:slug` - Принимать запросы на динамический endpoint

## Команда

- Марат - Backend (CRUD, Proxy, Infrastructure)
- Ксения - Backend (Receiver, Logging, Template), Frontend, Swagger
