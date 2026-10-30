# LogisticsConnect

## Overview

Supply chain parcel delivery hub and transit delay tracking.

Domain entities: hubs, sorting centers, regional districts.

Every class in this repo lives in a single flat package, `co.wethinkcode.logisticsconnect`. LogisticsConnect is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`hubs-global.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`package-status-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **AlertBotApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7050 | Parses and cleans `hubs-global.csv` |
| HubServiceApp | [`hub-service/`](hub-service) | 7051 | Serves provinces and sorting centers (place-name source of truth). |
| DelayStageServiceApp | [`delay-stage-service/`](delay-stage-service) | 7052 | Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns). |
| TransitServiceApp | [`transit-service/`](transit-service) | 7053 | Calculates estimated arrival windows based on hub and delay stage. |
| AlertBotApp | [`alertbot/`](alertbot) | 7054 | posts proactive delay notifications to public transit social media pages (simulated). |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `package-status-topic`: Package status updates move from latency-driven RPC to bandwidth-driven messaging.

**Status:** implemented — all four stages are complete. The cleaned CSV is served by
ingestion-service, the REST services are wired together, stage changes flow through
ActiveMQ, and AlertBot posts simulated alerts. Unit tests cover the CSV cleaning,
ETA calculation, stage storage, and alert threshold logic.

## What was implemented

| Stage | Result |
|---|---|
| 1. Clean `hubs-global.csv` | 18 raw rows → 10 deduplicated hubs; casing, padding, boolean flags, province spelling variants, and placeholders are normalized; missing provinces are inferred from matching sorting centers. |
| 2. Wire up the REST services | hub-service loads cleaned hubs from ingestion-service and serves provinces/sorting centers; delay-stage-service stores stages 0-8; transit-service returns an ETA window for any hub. |
| 3. Decouple with the MQ topic | delay-stage-service publishes `{hubId, stage, timestamp}` to `package-status-topic`; transit-service keeps an in-memory stage cache fed by the topic instead of calling delay-stage-service. |
| 4. AlertBot | alertbot subscribes to the same topic and simulates a social post when a stage is at or above the threshold (default `4`). |

| Stage | Required? | What "done" looks like | Rough effort |
|---|---|---|---|
| 1. Clean `hubs-global.csv` | Required | IngestionServiceApp exposes the cleaned records via REST (see [Integration contracts](#integration-contracts)); every issue category in [ingestion-service/README.md](ingestion-service/README.md#known-data-issues) is handled | ~1-1.5h |
| 2. Wire up the REST services | Required | hub-service, delay-stage-service, and transit-service each expose real domain endpoints (not just `/health`) and call each other synchronously per the contracts below; `transit-service` can return an ETA for a hub | ~1.5-2h |
| 3. Decouple with the MQ topic | Required | `delay-stage-service` publishes to `package-status-topic` on stage change; `transit-service` subscribes instead of calling `delay-stage-service` directly; broker runs via `common/docker-compose.yml` | ~1h |
| 4. AlertBot | Stretch | `alertbot` subscribes to `package-status-topic` and simulates posting an alert when a hub's delay stage crosses a threshold you choose | ~30-45m |

You don't need to match any exact field names, endpoint paths, or message shapes —
the ones below are illustrative. Favor a working, readable implementation over a
gold-plated one; partial completion of stage 3 or 4 is fine if 1-2 are solid.

## Integration contracts

Two kinds of integration point exist in this repo: synchronous REST calls (stage 2)
and the asynchronous MQ topic (stage 3). Field/endpoint names below are illustrative
— reasonable variations are fine as long as the shape (who calls whom, with what
kind of payload) is preserved.

### REST (stage 2)

| Caller | Callee | Example | Purpose |
|---|---|---|---|
| hub-service | ingestion-service | `GET :7050/hubs` → JSON array of cleaned hub records | hub-service loads its place-name data from the cleaned CSV output instead of re-parsing it itself |
| transit-service | hub-service | `GET :7051/hubs/{hubId}` → hub/sorting-center details | transit-service needs hub location data to calculate an ETA |
| transit-service | delay-stage-service | `GET :7052/delay-stage/{hubId}` → `{ "hubId": "H-501", "stage": 3 }` | transit-service needs the current delay stage to calculate an ETA — **this call is replaced by the MQ subscription in stage 3** |
| (client) | delay-stage-service | `POST :7052/delay-stage/{hubId}` with a body like `{ "stage": 3 }` | the stage/state-change endpoint referenced in [common/README.md](common/README.md) — this is also where the stage-3 MQ publish happens |

### MQ (stage 3) — topic `package-status-topic`

Already documented in detail in [common/README.md](common/README.md): broker URL and
topic name come from the shared `co.wethinkcode.logisticsconnect.mq.MqConfig` class,
duplicated into each participating service.

- **Producer:** `delay-stage-service`, on its stage/state-change endpoint above.
- **Consumers:** `transit-service` (replacing its direct REST call to
  delay-stage-service) and, for the stretch goal, `alertbot`.
- **Example message shape:** `{ "hubId": "H-501", "stage": 5, "timestamp": "2026-07-18T10:15:00Z" }`

## Implemented endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| ingestion-service | `GET /hubs` | Cleaned, deduplicated hub records |
| ingestion-service | `GET /hubs/{hubId}` | Record by canonical ID or any alias |
| ingestion-service | `GET /stats` | Raw row, cleaned row, duplicate, and skipped counts |
| hub-service | `GET /hubs`, `GET /hubs/{hubId}` | Cached copy of ingestion-service data |
| hub-service | `GET /provinces`, `GET /provinces/{province}/hubs` | Place-name browsing |
| hub-service | `GET /sorting-centers`, `POST /refresh` | Sorting centers and cache refresh |
| delay-stage-service | `GET /delay-stage/{hubId}` | Current stage (defaults to `0`) |
| delay-stage-service | `POST`/`PUT /delay-stage/{hubId}` | Set stage (`0`-`8`) and publish to MQ |
| delay-stage-service | `GET /delay-stages` | All explicitly set stages |
| transit-service | `GET /eta/{hubId}` | ETA window using hub data and the MQ-fed stage cache |
| transit-service | `GET /delays`, `GET /delays/{hubId}` | Current stage cache |
| alertbot | `GET /alerts` | Simulated posts raised so far |
| alertbot | `GET /config` | Active threshold and simulated channel |

Example ETA:

```bash
curl http://localhost:7053/eta/H-501
```

```json
{
  "hubId": "H-501",
  "sortingCenter": "Cape Town Port",
  "province": "Western Cape",
  "stage": 5,
  "stageDescription": "Extended hub shutdown",
  "delayMinutes": 480,
  "etaStart": "2026-07-18T19:00:00Z",
  "etaEnd": "2026-07-18T19:30:00Z",
  "status": "SEVERELY_DELAYED",
  "calculatedAt": "2026-07-18T10:00:00Z"
}
```

## Project structure

```
logisticsconnect/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7050)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/logisticsconnect/IngestionServiceApp.java
│       └── resources/hubs-global.csv
├── hub-service/          (port 7051)
├── delay-stage-service/          (port 7052)
├── transit-service/          (port 7053)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── alertbot/          (port 7054)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `alertbot/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd hub-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn -q package \;
```

## Run

```
# ingestion
cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

# domain services, each in its own terminal
# terminal 1
cd hub-service && mvn package && java -jar target/hub-service.jar
# terminal 2
cd delay-stage-service && mvn package && java -jar target/delay-stage-service.jar
# terminal 3
cd transit-service && mvn package && java -jar target/transit-service.jar

# MQ broker (needed once the MQ-aware services above are wired up)
cd common && docker compose up -d

# alerting
cd alertbot && mvn package && java -jar target/alertbot.jar
```

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7050 |
| HubServiceApp (`hub-service`) | 7051 |
| DelayStageServiceApp (`delay-stage-service`) | 7052 |
| TransitServiceApp (`transit-service`) | 7053 |
| AlertBotApp (`alertbot`) | 7054 |

## Implementation notes

### Deduplication strategy

Rows are grouped by normalized `sortingCenter` plus `province` (after inferring a
missing province from other rows with the same sorting center). Each group becomes
one hub:

- the canonical ID is the lowest numeric ID in the group (`H-500` wins over `H-515`);
- every original ID is retained in `aliases`, so lookups by any legacy ID still work;
- `active` uses a majority vote across non-null values; ties fall back to the
  canonical row's value, which keeps the result deterministic.

This keeps the historical IDs usable while giving every downstream service one
authoritative record per physical hub.

### REST vs. MQ

Live, request/response lookups (hub details, ETA calculation) stay synchronous REST
because the caller needs an answer immediately. Stage changes are events, not
queries: delay-stage-service publishes once and an arbitrary number of consumers can
react without the producer knowing about them. transit-service replaces its direct
delay-stage call with a topic subscription, and alertbot can be added without
touching the producer.

### With more time

- Persist stages and alerts instead of keeping them in memory.
- Add retry/backoff metrics and a dead-letter queue for poison messages.
- Make the broker URL and service URLs fully configurable through environment
  variables (service URLs already are; broker URL lives in `MqConfig`).
- Add integration tests that start the services and broker together.

## Test

Each module has JUnit 5 tests:

```
mvn test -f ingestion-service/pom.xml
mvn test -f delay-stage-service/pom.xml
mvn test -f transit-service/pom.xml
mvn test -f alertbot/pom.xml
```

Or from the project root:

```
find . -name pom.xml -execdir mvn -q test \;
```

Every running service also exposes `/health`:

```
curl http://localhost:7050/health   # -> OK
```

End-to-end smoke check (broker running, all services up):

```
curl -X POST http://localhost:7052/delay-stage/H-501 \
  -H 'Content-Type: application/json' -d '{"stage": 5}'

curl http://localhost:7053/eta/H-501
curl http://localhost:7054/alerts
```
