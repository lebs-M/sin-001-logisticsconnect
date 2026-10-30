# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `package-status-topic`

Package status updates move from latency-driven RPC to bandwidth-driven messaging.

Part of the [LogisticsConnect](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- Producer: `delay-stage-service` (`../delay-stage-service`)
- Consumer(s): `transit-service` (required, stage 3); `alertbot` (stretch, stage 4 —
  reacts to stage changes to decide when to raise an alert)

Broker URL and topic name are shared via a common `co.wethinkcode.logisticsconnect.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Verify end-to-end by publishing a stage change and checking the consumers:

```
curl -X POST http://localhost:7052/delay-stage/H-501 \
  -H 'Content-Type: application/json' -d '{"stage": 5}'

# transit-service should now report stage 5 from its MQ-fed cache
curl http://localhost:7053/delays/H-501

# alertbot should have raised a simulated alert (threshold default 4)
curl http://localhost:7054/alerts
```

Producer/consumer logs and the ActiveMQ web console
(http://localhost:8161, admin/admin) also show messages on the topic.

## Implementation

- `delay-stage-service` publishes `{hubId, stage, timestamp}` on every accepted
  stage change.
- `transit-service` subscribes and keeps an in-memory stage cache; it no longer
  calls delay-stage-service synchronously.
- `alertbot` subscribes and raises a simulated post at or above its threshold.
- Both consumers run a daemon reconnect loop, so starting them before the broker is
  up is safe.
