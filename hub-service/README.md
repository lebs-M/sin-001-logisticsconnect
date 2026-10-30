# HubServiceApp

## Overview

Serves provinces and sorting centers (place-name source of truth).

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

## Project structure

```
hub-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/HubServiceApp.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/hub-service.jar
```

Listens on port `7051`.

hub-service loads its data from ingestion-service (`INGESTION_SERVICE_URL`, default
`http://localhost:7050`) and caches it. Endpoints:

- `GET /health` — liveness check
- `GET /hubs`, `GET /hubs/{hubId}` — cached hub records (aliases resolve)
- `GET /provinces`, `GET /provinces/{province}/hubs` — place-name browsing
- `GET /sorting-centers` — distinct sorting centers
- `POST /refresh` — reload from ingestion-service

If ingestion-service is down, endpoints return `503` and a later request retries.

## Test

Run the automated tests with `mvn test`. You can also verify it's up:

```
curl http://localhost:7051/health   # -> OK
```
