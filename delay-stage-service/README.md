# DelayStageServiceApp

## Overview

Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `package-status-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside it in this module.

## Project structure

```
delay-stage-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── DelayStageServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/delay-stage-service.jar
```

Listens on port `7052`.

Endpoints:

- `GET /health` — liveness check
- `GET /delay-stage/{hubId}` — current stage (`0` by default)
- `POST` / `PUT /delay-stage/{hubId}` — set a stage (`0`-`8`), e.g.
  `{"stage": 5}`; publishes an event to `package-status-topic`
- `GET /delay-stages` — all explicitly set stages

Publishing failures are logged and do not fail the HTTP request; the next stage
change reconnects to the broker.

## Test

Run the automated tests with `mvn test`. You can also verify it's up:

```
curl http://localhost:7052/health   # -> OK
```
