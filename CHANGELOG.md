# Changelog

## 1.0.0 (2026-06-10)


### Features

* [WebhookService, ProxyService] бизнес-логика и проксирование ([4e15d01](https://github.com/Zeldrizz/webhook-service/commit/4e15d0156beffb13c945138ddbd76a3bedcda073))
* add periodic log cleanup timer ([97c54f4](https://github.com/Zeldrizz/webhook-service/commit/97c54f40a21fe70d089f786d1c560e752db1a31a))
* **api:** CacheStatsHandler for /api/cache/stats and /api/cache/flush ([e807216](https://github.com/Zeldrizz/webhook-service/commit/e807216350bef0685c85c4ec61ed259d80498a55))
* **auth:** admin X-API-Key handler and Web UI login overlay ([075c3c0](https://github.com/Zeldrizz/webhook-service/commit/075c3c03ddbcc7cceaecb1fecf317d1e40833537))
* **cache:** CaffeineCache wrapper and CacheManager facade with EventBus consumer ([5390f2e](https://github.com/Zeldrizz/webhook-service/commit/5390f2e21721b9f3ab7af103c9f5702cc18d6092))
* **cache:** names, invalidation event, codec and metric DTOs ([132da4a](https://github.com/Zeldrizz/webhook-service/commit/132da4a33472e2e42284bca7edd1ef4666ead4cd))
* **cache:** WebhookCache (slug/id/negative) and StatsCache ([d48d3b1](https://github.com/Zeldrizz/webhook-service/commit/d48d3b15086c83c73e3e412c80bc7d7f83090967))
* **config:** cache, retry, batch, circuit-breaker, auth and pipelining blocks ([76a988a](https://github.com/Zeldrizz/webhook-service/commit/76a988af101f4615c8a38bebfcde86862f7d3006))
* **proxy:** circuit breaker plus exponential-backoff retry in forward() ([afe4f8c](https://github.com/Zeldrizz/webhook-service/commit/afe4f8c213a3e194d33c68482b8f9ca24fd8167e))
* **proxy:** RetryPolicy with exponential backoff and full jitter ([c0624d0](https://github.com/Zeldrizz/webhook-service/commit/c0624d087c2fd84cecebf929a7fd3d1019dc7ecc))
* **repository:** saveBatch via PgClient executeBatch ([77afb20](https://github.com/Zeldrizz/webhook-service/commit/77afb2038fd816ba77cc31c1e71094de52548991))
* REST API хэндлеры, MainVerticle, точка входа ([9b7ac71](https://github.com/Zeldrizz/webhook-service/commit/9b7ac713586e73aea04f63ea7a50f1b43aac678f))
* **server:** wire cache, auth, retry and batching in MainVerticle ([c077239](https://github.com/Zeldrizz/webhook-service/commit/c077239117eb251ab3c7ea40ac8a915c0fe77407))
* **service:** cache-aside lookup and EventBus invalidation in WebhookService ([7cece8f](https://github.com/Zeldrizz/webhook-service/commit/7cece8f67bc3580057d64c63612de00d78d66eaa))
* **template:** add compiled template previews ([0c242d4](https://github.com/Zeldrizz/webhook-service/commit/0c242d48c22c741fbc9b964b978250a601683a19))
* **ui:** improve webhook detail actions and add delete confirmation ([3efbe60](https://github.com/Zeldrizz/webhook-service/commit/3efbe606d43224170b39e921df54106b135a885c))
* WebhookRepository, JDBC-реализация с CRUD ([9234f8f](https://github.com/Zeldrizz/webhook-service/commit/9234f8fbf0ecff436dcd2b3df033eb4885a58ff7))
* миграции Flyway, таблицы webhooks и request_logs ([ac9bbcc](https://github.com/Zeldrizz/webhook-service/commit/ac9bbcc5eabe6e032ee2c6a9c46041c6e59d110b))
* модель Webhook, RequestLog, DTO, фабрика ([82b646c](https://github.com/Zeldrizz/webhook-service/commit/82b646c5ea9e3d75fda451eb2d64ff55c766fa0a))
* настроить pom.xml, Docker, logback ([36ff688](https://github.com/Zeldrizz/webhook-service/commit/36ff688949942cb90b29965591c61df50e38bb27))
* утилиты (DB, JSON, slug, id), валидация, конфиг ([e416818](https://github.com/Zeldrizz/webhook-service/commit/e416818b16ebcd5b709f0365789355e7c753371e))


### Bug Fixes

* **app-config:** increase pool-size ([1812c35](https://github.com/Zeldrizz/webhook-service/commit/1812c35a9ed8f085545aaec2bdb15b272a93e745))
* **cache:** eliminate create→EventBus race, return 409 on duplicate slug ([12bbf96](https://github.com/Zeldrizz/webhook-service/commit/12bbf96e3a910bd5f2710f730c51ea04f5dfd3b3))
* **template:** correctly handle {{else}} branch in {{#if}} blocks ([9c250bd](https://github.com/Zeldrizz/webhook-service/commit/9c250bd9bb9247fdd7cab0769c7996fa4cb37165))


### Documentation

* refresh README ([928d2fa](https://github.com/Zeldrizz/webhook-service/commit/928d2faaf135e9d6a8ca0120a9842d3160a1867f))
* update README ([f9aafce](https://github.com/Zeldrizz/webhook-service/commit/f9aafce1b0ca63acc324e2920ec3543e9413b117))
