# TransitServiceApp

## Overview

Calculates estimated arrival windows based on hub and delay stage.

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `package-status-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside it in this module.

## Project structure

```
transit-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── TransitServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/transit-service.jar
```

Listens on port `7053`.

Endpoints:

- `GET /health` — liveness check
- `GET /eta/{hubId}` — ETA window from hub details plus the current delay stage
- `GET /delays`, `GET /delays/{hubId}` — stage cache fed by `package-status-topic`

The stage is read from the MQ subscription, not by calling delay-stage-service.
Unknown hubs return `404`; an unavailable hub-service returns `503`.

## Test

Run the automated tests with `mvn test`. You can also verify it's up:

```
curl http://localhost:7053/health   # -> OK
```
