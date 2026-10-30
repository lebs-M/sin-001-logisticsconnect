# AlertBotApp

## Overview

Posts proactive delay notifications to public transit social media pages (simulated).

Part of the [LogisticsConnect](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

Mechanism: Outbound webhook, simulated social post

MQ (stretch goal): this service subscribes to the ActiveMQ topic
`package-status-topic` — see [`../common/`](../common). Broker URL and topic name
come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside
it in this module. Use the stage in each message to decide when to raise an alert
(e.g. above a threshold you choose).

## Project structure

```
alertbot/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── AlertBotApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/alertbot.jar
```

Listens on port `7054`.

Endpoints:

- `GET /health` — liveness check
- `GET /alerts` — simulated posts raised so far
- `GET /config` — active threshold and simulated channel

It subscribes to `package-status-topic` and posts a simulated social-media alert
when a stage is at or above `ALERT_STAGE_THRESHOLD` (default `4`). Alerts are logged
with the `SIMULATED SOCIAL POST` prefix and stored in memory.

## Test

Run the automated tests with `mvn test`. You can also verify it's up:

```
curl http://localhost:7054/health   # -> OK
```
