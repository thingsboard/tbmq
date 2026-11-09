# TBMQ Lite: high-level design

Date: 2026-10-14
Status: design agreed in brainstorming on 2026-10-14; reviewed and revised on 2026-11-09
Revised 2026-11-09: the edition is fixed by the image (D8, §4.2). A review round the same day
settled positioning, releases, reference hardware, capacity and the crash guarantee (D12 to D15),
moved the dao split to Phase 3, dropped the spike phase (D16), settled the DEVICE queue semantics
(D17), and fixed the points listed in §3.1.

Builds on:

- The September study, `docs/superpowers/research/2026-09-14-tbmq-lightweight-analysis.md`
  (kept outside the repository; published as the "TBMQ Lightweight Study" page).
- `~/projects/gsd/tbmq/tbmq-lightweight-brief.md` (April brief) and the May prototype in
  `~/projects/gsd/tbmq/lightweight`.

Supersedes the brief's repository strategy and Release 1 scope, and the study's sections 4
to 9 wherever they differ from this document.

## 1. Summary

TBMQ Lite is a single-node edition of TBMQ: one JVM, one data volume, no external services
(no Kafka, no Valkey/Redis, no PostgreSQL). It is built in this repository from TBMQ's own
code. The Netty layer, actor system, MQTT 3.1/3.1.1/5 handling, auth providers, REST API and
UI are reused; a standalone mode swaps every infrastructure-facing implementation for an
in-process one and keeps all state in an embedded RocksDB. Lite supports DEVICE clients only,
keeps persistent sessions with durable offline queues, ships as one multi-arch Docker image,
and is Apache 2 open source. It first ships with TBMQ 3.0; the MQTT bridge for edge deployments
follows in 3.1.

Why: no customer asked for Lite; it is a product bet. TBMQ's smallest deployment today is one
JVM plus Kafka, PostgreSQL and Valkey, which leaves the single-node slot to Mosquitto, HiveMQ CE
and NanoMQ. Lite takes that slot. It is marketed as the single-node production broker and as the
broker for edge sites, labs and laptops; clustered TBMQ is the enterprise offering. Single-node
`tbmq-node` deployments stay supported, for example for customers who need APPLICATION clients.

## 2. Decisions

| # | Decision | Choice | Decided |
|---|---|---|---|
| D1 | Product shape | One node, no external services, Apache 2, its own image in the TBMQ family | 2026-09-14 |
| D2 | Client types | DEVICE only: no APPLICATION type, no application shared subscriptions | 2026-09-14 |
| D3 | Persistence | Persistent DEVICE sessions with durable offline queues; retained messages and blocked clients survive restarts | 2026-09-14 |
| D4 | Code base | Built in this repo by reusing TBMQ's application code. The May prototype is archived; its wire tests are harvested | 2026-10-14 |
| D5 | Name and first-release scope | "TBMQ Lite". The first release (3.0, D13) ships without the bridge and integrations; a native two-way MQTT bridge follows in 3.1 | 2026-10-14 |
| D6 | Ack point | PUBACK/PUBREC after dispatch: handed to every online non-persistent subscriber and written to the WAL for every persistent subscriber and for the retained store. WAL fsync at most 1 s apart by default; per-write sync is a setting | 2026-10-14 |
| D7 | Storage | RocksDB for everything: broker state, management entities, timeseries | 2026-10-14 |
| D8 | Edition selection | Fixed by the image, never by configuration. `tbmq-node` and `tbmq` carry the Kafka, Valkey and PostgreSQL backends; `tbmq-lite` carries the embedded ones. There is no mode property. Several `tbmq-node` containers sharing Kafka form one cluster; several `tbmq-lite` containers are independent brokers | 2026-10-14, revised 2026-11-09 |
| D9 | Replacing Kafka | At the publisher/consumer-service level, not by emulating Kafka behind the queue interfaces | 2026-10-14 |
| D10 | Platforms | linux/amd64 and linux/arm64, no 32-bit ARM. Docker image only in 3.0. Devices with 2 GB of memory are the focus (D14); 1 GB is best effort. No deb or rpm. Raspberry Pi 4/5 on a 64-bit OS is a target platform, but for now arm64 is tested only on AWS Graviton: the documentation says Lite runs on arm64, including 64-bit Raspberry Pi OS, and publishes no Pi-specific numbers | 2026-10-14, revised 2026-11-09 |
| D11 | Release model | Same repo, version numbers and release train as TBMQ. Phase 1 ships in CE 2.5.0 and the dao split in a later 2.x release; each merges into PE right away. Lite code lands on `main` as it is ready and stays out of the cluster images | 2026-10-14, revised 2026-11-09 |
| D12 | Positioning | Lite is the single-node production broker and the broker for edge sites, labs and laptops. Clustered TBMQ is the enterprise offering. Single-node `tbmq-node` stays supported | 2026-11-09 |
| D13 | First Lite release | TBMQ 3.0; both editions carry the number | 2026-11-09 |
| D14 | Reference hardware and capacity | Reference: a container with 2 GB of memory, on amd64 and arm64, with an EBS gp3 volume at its baseline (3,000 IOPS, 125 MB/s). Target on it: 10,000 connections, 10,000 msg/s in, 1,000 persistent DEVICE clients (workload in §9). The CPU count, and whether the target includes TLS, are decided before the Phase 2 capacity check (§14) | 2026-11-09 |
| D15 | Crash guarantee | No acknowledged message is lost on a process crash. QoS 2 is exactly-once while the broker runs; a QoS 1 or QoS 2 message in flight when the process dies may reach persistent subscribers twice, as in TBMQ today. A power cut can lose up to the WAL sync interval | 2026-11-09 |
| D16 | No spike phase | Phase 0 is dropped; Lite gets built either way. Performance is measured on the real code: the RocksDB queue benchmark opens Phase 2, a first capacity check closes it, footprint is measured in Phase 4, and the full runs and the soak happen in Phase 5 | 2026-11-09 |
| D17 | DEVICE queues | Persistent DEVICE queues keep the cluster's semantics, including the per-client cap (default 10,000, at most 65,535 messages). A backend that must ride out longer outages at high rates uses `tbmq-node` with APPLICATION clients; the comparison page says so | 2026-11-09 |

## 3. What the code check changed

The September study was checked against `main` (d9be3f994) on 2026-10-14. Its direction
holds. These findings change the design or the effort:

| Finding | Consequence for Lite |
|---|---|
| Kafka does more than three jobs. `tbmq.msg.persisted` stages DEVICE messages before Valkey; the four compacted topics are live replication between nodes; disconnect commands and CONNECT responses travel through per-node topics even when the target is the local node | More pipeline steps to replace. They do not disappear on one node by themselves |
| No queue implementation switch exists. All 14 broker Kafka factories and `TbKafkaAdmin` are unconditional `@Component`s. Kafka types sit in the core queue interfaces (`TopicPartition`, `RecordMetadata`, `ConsumerRebalanceListener`, `ListConsumerGroupOffsetsResult`). There is no in-memory queue anywhere, tests included | Phase 1 cleans the interfaces before anything else |
| MQTT 5 SCRAM is built on `kafka-clients` internals (`ScramSaslServer`) | SCRAM is reimplemented before Kafka can leave the classpath |
| Spring Boot auto-configuration is enabled only in `JpaDaoConfig`. `ApplicationStatusChecker` requires a Hikari datasource. The install step needs Redis | Plumbing fixes in Phase 1 |
| PUBACK today means "written to `tbmq.msg.all`" (`acks=1`). Persistent copies that fail later are dropped after 20 s by the `SKIP_ALL` strategy. Nothing applies back-pressure between publish and delivery: no `autoRead`, unbounded actor mailboxes. #305 is an unrelated, unmerged APPLICATION outbound rate limit | Lite defines its own ack guarantee (D6) and real back-pressure (§5.5) |
| The Valkey node registry drives internode fan-out and leader election (lowest service id; entries never expire). Two of the five "caches" (`clientSessionCredentials`, `clientMqttVersion`) are session metadata with no TTL | §7.1 |
| The service id defaults to the hostname and is stored in every session | Standalone uses a fixed service id |
| QoS 2 state lives in PostgreSQL (`generic_client_session_ctx`). Per-client stats upsert about 670 rows/s into `ts_kv_latest` at 10k active devices | Both move into the store; per-client stats are flushed far less often (§6.10) |
| APPLICATION code is injected into 13 classes outside its package. The APPLICATION type can come in through four routes: credentials form/REST, CSV bulk import, JWT provider, HTTP provider | §7.2 |
| 18 Spring Data JPA repositories, not 31, plus 9 JdbcTemplate classes. The `*Dao` interfaces (17, 15 without APPLICATION) separate persistence from the service logic | The embedded store plugs in at the `*Dao` level |
| Defaults unsafe for a LAN: actuator and Prometheus on 8083 without login, a seeded WebSocket credential with no password and full publish/subscribe rights, `sysadmin`/`sysadmin` | §7.4 |
| PE adds 8 tables: audit_log, role, oauth, domain, domain_oauth, resource, white_labeling, dropped_msg | PE Lite needs its own work; not in 3.0 |
| arm64 images already ship. A credentials CSV import exists, with no export. `/api/app/config` already carries some feature flags, but the route guard only checks login | Less packaging work, more UI work |

Separate CE issues found along the way, outside Lite's timeline:

- The Valkey DEVICE store's `UPDATE_PACKET_TYPE` script uses a plain `SET`, which drops the
  TTL, so messages in PUBREL state never expire.
- Leader election picks the lowest service id from a registry whose entries never expire, so a
  dead node that sorts first stops the cleanup jobs.
- `SECURITY_MQTT_BASIC_ENABLED` is set in the compose and k8s files but read nowhere.

### 3.1 Second code check (2026-11-09)

A review of this spec against `main` and PE `main` found these errors and gaps. Each is handled
where the last column says; file and line references are in the code-check file, §14.

| Finding | Handled in |
|---|---|
| Saving the inbound QoS 2 state at PUBREC does not give exactly-once across a crash: copies can be stored before PUBREC goes out, and the publisher then resends | D15, §5.4 |
| `/api/app/config`, `/brokers` and `/service/info` share `AppController` with `/cluster-info` and the Kafka endpoints; marking it cluster-only would break the UI's first call | §8.2, Phase 1 |
| `common/cache` and `dao` do not depend on `common/queue`, so the edition annotations cannot live there | §4.2 |
| `AbstractPubSubIntegrationTest`, the base of 48 integration test classes and the controller tests, always starts Kafka and Valkey containers and references Kafka settings classes | §9, Phase 1 |
| `ThingsboardErrorResponseHandler` imports Hibernate's `ConstraintViolationException` | §4.5 |
| The integration executor uses the Kafka types in the queue interfaces; PE's executor has 7 more integration types | §4.3, Phase 1 |
| The Valkey DEVICE store uses the publisher's MQTT 5 message expiry interval as each message's TTL, not only the 7-day default | §6.4 |
| `findPersistedMessages` returns a client's whole queue (up to 10,000 messages) as one list | §6.4 |
| `docker stop` kills after 10 s; TBMQ's shutdown waits up to 15 s for client disconnects and 1 minute per Spring lifecycle phase | §5.6 |
| Three of the four perf-test scenarios (simple, general, fan-in) use APPLICATION clients | §9 |
| PE `main` has about 27 more JPA files in `dao` than CE (73 against 46); the dao split moves them by hand | §4.3, Phase 3 |
| The home-page Kafka tables listed in the code-check file are unused components | Nothing to hide |
| Builder check: prod sizing (about 0.36 vCPU per 1,000 msg/s in+out in cluster mode, about 45 KB of heap per TLS session) puts D14 at risk on 2 CPUs and, with TLS, in 2 GB | D14, D16, §14 |
| Builder check: the cluster images install the deb that `application` builds, so `application` has to keep the cluster modules at runtime | §4.3, §4.6, §9 |
| Builder check: open PRs #370, #385 and #400 change code that Phase 1 rewrites (internode notifications; SCRAM and a new DAO; `AppController` and the node registry) | §10: they land after Phase 1 |

## 4. Architecture and code structure

### 4.1 Process

```
                    TBMQ Lite: one JVM, /data volume
┌──────────────────────────────────────────────────────────────────┐
│ Netty listeners 1883/8883/8084/8085         HTTP 8083: REST + UI │
│   (unchanged)                                (Lite pages hidden) │
│ Client actors · auth providers · ACL · tries · MQTT 5 handling   │
│   (unchanged; SCRAM rewritten)                                   │
├─────────────────────────── service interfaces ───────────────────┤
│ Standalone implementations (application, @StandaloneComponent):  │
│   dispatcher · local session events · local notifications ·      │
│   Caffeine caches · local limits · local jobs and stats          │
├──────────────────────────────────────────────────────────────────┤
│ dao services ──► *Dao interfaces                                 │
│ dao-rocksdb: entity store · broker state · DEVICE queues ·       │
│              timeseries · migrations · checkpoints               │
└──────────────────────────────────────────────────────────────────┘
```

Ports are TBMQ's: MQTT on 1883 (TCP), 8883 (TLS), 8084 (WS) and 8085 (WSS); REST, UI and
actuator on 8083.

### 4.2 Edition selection

The edition is a property of the image, not of its configuration. A user picks a product by
picking an image and cannot turn one into the other.

| Image | Edition | Several containers |
|---|---|---|
| `tbmq-node` (`msa/mqtt-broker`), `tbmq` (`msa/tbmq`) | `cluster` | Connected to the same Kafka, Valkey and PostgreSQL, they form one cluster, as today |
| `tbmq-lite` (`msa/tbmq-lite`) | `lite` | Each is a separate broker. There is no node registry and no peer traffic, each container owns its `/data`, and RocksDB's lock file stops a second process from opening the same store |

- There is no `tbmq.mode` property and no environment variable. The edition follows from the
  infrastructure modules the assembly puts on the classpath.
- Each edition has one marker module that ships a classpath resource, `META-INF/tbmq-edition`:
  `queue-kafka` marks `cluster`, `dao-rocksdb` marks `lite`.
- `TbmqEditionCondition` reads the marker through the class loader, not through the Spring
  `Environment`, so no YAML key, environment variable or `-D` flag can change it.
  `TbmqEditionCondition`, `@ClusterComponent` and `@StandaloneComponent` live in `common/util`,
  which `common/queue`, `common/cache`, `dao` and `application` all depend on (`common/cache` and
  `dao` cannot see `common/queue`). The annotations are `@Conditional(TbmqEditionCondition)`
  meta-annotations. Every bean that touches infrastructure carries one of them; all other beans
  are edition-agnostic.
- Startup fails with a clear message if the classpath has both markers or neither, and if the
  `lite` edition runs with a `service.type` other than `tbmq`.
- A `TbmqEdition` bean exposes the edition to the status checker, `/api/app/config` (§8.1) and the
  startup log.
- In this document "standalone mode" means the runtime of the `lite` edition, and "cluster mode"
  that of the `cluster` edition.
- Mixed classpaths, such as local queues with PostgreSQL for management data (the Phase 2
  milestone), exist only in test modules. No image is built from one.

### 4.3 Modules

| Today | After |
|---|---|
| `common/queue` | `common/queue`: interfaces with no Kafka types, protobuf, the component annotations · **`common/queue-kafka`**: Kafka factories, admin, settings |
| `common/cache` | `common/cache`: cache names, cache operations, Caffeine · **`common/cache-redis`**: Jedis, Lettuce, bucket4j-redis, the Valkey DEVICE store and its Lua scripts |
| `dao` | `dao`: services, `*Dao` interfaces, domain logic, no JPA · **`dao-sql`**: entities, repositories, JdbcTemplate DAOs, SQL schema and upgrade scripts, Hikari, `JpaDaoConfig` (split in Phase 3) · **`dao-rocksdb`**: new in Phase 2 |
| `application` | Stays one module and gains the standalone implementations. It keeps building the cluster edition's boot jar, deb, rpm and Windows packages, so it takes `queue-kafka` and `cache-redis` (and `dao-sql` from Phase 3) as runtime-scope dependencies: its main code cannot compile against them, and the cluster artifacts and their locations stay as they are |
| `msa/tbmq`, `msa/mqtt-broker` | Unchanged: they install the deb that `application` builds |
| `integration/executor`, `msa/integration/executor` | Adapted to the Kafka-free queue interfaces in Phase 1; adds `queue-kafka` explicitly |
| — | **`msa/tbmq-lite`**: depends on `application` with the cluster modules excluded, adds `dao-rocksdb`, and builds its own boot jar and the Lite image |

Cluster-mode services such as the Kafka-backed publishers stay in `application`. After Phase 1
they reference only the queue interfaces, so they compile without Kafka and stay inactive in
the `lite` edition. `application` does not depend on any infrastructure module at compile time;
the cluster ones are runtime-scope dependencies that package the cluster edition, and the Lite
assembly and the lite test module exclude them. Not splitting `application` avoids the largest possible PE merge conflict.

PE: its `application` has no extra Kafka, Redis or JPA imports, so the Phase 1 rules cost it
nothing there. It drops `msa/tbmq-lite` when it merges Phase 1 (PE Lite is later work) and adapts
its executor's 7 extra integration types. When the dao split arrives in Phase 3, PE moves its own
JPA files (about 27 beyond CE's) to its `dao-sql`.

### 4.4 Reused, replaced, left out

Reused as-is: Netty handlers and the actor system, auth providers and ACL, the subscription and
retained-message tries, DEVICE shared subscriptions, all MQTT 5 features, REST controllers, the
UI, the stats services.

Replaced in standalone mode: the message pipeline (§5); session-event request/response,
disconnect commands and internode notifications (§7.1); the four compacted-topic stores and the
DEVICE message store (§6); caches; rate-limit and quota storage; the node registry and leader
election; the cross-node stats total; DAO persistence; install and upgrade.

Left out of Lite: the APPLICATION client type and its persistence, application shared
subscriptions, Kafka monitoring and cluster info, node drain, the integration executor and
integrations (after 3.0).

### 4.5 Plumbing fixes

- `@EnableAutoConfiguration` moves from `JpaDaoConfig` to `ThingsboardMqttBrokerApplication`,
  with the DataSource, JPA, Redis and Kafka auto-configurations active only when their modules
  are present.
- `ApplicationStatusChecker` becomes edition-aware; in the `lite` edition it checks the store.
- Classes in `application` that import Kafka or Redis types move to the infrastructure modules:
  the per-node topic-name helpers, the registry code in `TbmqSystemInfoService`,
  `RateLimitRedisCacheServiceImpl`, and the Kafka exception handling in
  `IntegrationCleanupServiceImpl`. SCRAM is reimplemented (§7.3).
- `application` stops importing dao internals (`ProtocolUtil`, `UserServiceImpl`,
  `SqlQueueStatsManager`) before the dao split.
- `ThingsboardErrorResponseHandler` maps Hibernate's `ConstraintViolationException`; it gets a
  persistence-neutral exception from `dao` instead.
- `AppController` is split (§8.2).

### 4.6 Enforcement

- ArchUnit rules: `application` and `dao` import none of `org.apache.kafka`, `redis.clients`,
  `io.lettuce`, `jakarta.persistence`, `org.hibernate`, `org.rocksdb`; the infrastructure
  modules do not import each other. The rules for `application` start in Phase 1; the JPA part
  for `dao` starts in Phase 3 with the dao split.
- A Maven Enforcer rule fails the `msa/tbmq-lite` build if `kafka-clients`, Jedis, Lettuce,
  Hibernate or the PostgreSQL driver appear in its dependency tree.
- A second Enforcer rule fails the `application` build, which packages the cluster edition, if
  `dao-rocksdb` or `rocksdbjni` appear in its runtime classpath.
- An image-level test per image: `tbmq-lite` started with Kafka, Redis and datasource variables
  set still runs as `lite` and ignores them; `tbmq-node` started without Kafka fails instead of
  falling back to anything local.

## 5. Message pipeline

The two Kafka hops on the publish path, `tbmq.msg.all` (keyed by topic) and
`tbmq.msg.persisted` (keyed by subscriber client id), become two in-process stages with the same
keys, so ordering stays as it is in TBMQ today.

### 5.1 Flow

```
PUBLISH ─► Netty handler ─► ClientActor
             │                (topic alias, validation, Receive Maximum,
             │                 retained message written to the store)
             │  reads paused when the pipeline is over its high-water mark
             ▼
  Stage 1 · dispatch: N threads, partitioned by topic, bounded
    subscription trie match, shared-subscription member pick
    ├─► online non-persistent sessions: written to Netty
    │     (dropped and counted if the channel isn't writable, as today)
    └─► Stage 2 · persist: M threads, partitioned by subscriber client id, bounded
          appends batched into one RocksDB WriteBatch (WAL)
          → notify the subscriber's PersistedDeviceActor if it is online

  every copy handed off or written ─► callback ─► publisher's actor
  sends PUBACK/PUBREC in packet order (existing ordered-ack logic)

  WAL fsync: background thread every 1 s by default, or on every write ("always")
```

### 5.2 Components

- `@StandaloneComponent` implementations replace `PublishMsgQueuePublisher` and
  `PublishMsgConsumerService` (stage 1), and `DeviceMsgQueuePublisher` and
  `DeviceMsgQueueConsumer` (stage 2).
- Reused: `processPublishMsg`, shared subscriptions (including the `ss_<share>_<filter>`
  pseudo-clients for offline DEVICE groups), `MsgPersistenceManager`, the delivery services and
  the `TbQueueCallback` that triggers PUBACK.
- REST publish (`RestPublishServiceImpl`), Last Will (`DefaultLastWillService`) and the Sparkplug
  republisher feed the same dispatcher.
- The per-node downlink topics disappear; `DownLinkProxy` already has a local path.

### 5.3 Why two stages

Packet ids for a persistent subscriber must be assigned in sequence. If two topic partitions
appended to the same subscriber's queue concurrently, a reader could move past a lower sequence
number that has not been written yet, and that message would never be delivered. Stage 2
serializes writes per subscriber without locks, and folds many appends into one WAL write.

### 5.4 Ack semantics

- PUBACK/PUBREC is sent once the message is handed to every online non-persistent subscriber and
  written to the WAL for every persistent subscriber and for the retained-message store.
- A process crash loses no acknowledged message for persistent subscribers. A power cut can lose
  up to the WAL sync interval (1 s by default).
- PUBCOMP is still sent immediately on PUBREL.
- QoS 2 is exactly-once while the broker runs (D15). A QoS 1 or 2 message in flight when the
  process dies may reach persistent subscribers twice: its copies can already be stored while the
  publisher, which never got PUBACK or PUBREC, resends it. TBMQ has the same window today.
  Closing it would need an inbound log as the commit point, one more write per message; that is
  not planned.
- The inbound QoS 2 packet-id state is saved as TBMQ saves it, at disconnect and at shutdown,
  into `session_ctx`.
- If a persistent copy or a retained update cannot be written (disk below the watermark, store
  error), the publish fails the way a Kafka failure does today: an error PUBACK on MQTT 5 (0x97
  when the cause is the disk watermark), a disconnect on MQTT 3.1.1. Failures are counted.

### 5.5 Back-pressure

- The pipeline (both stages together) is bounded by queued payload bytes and by message count.
- At 80% of either limit, a connection that sends a PUBLISH has its reads paused
  (`autoRead=false`). At 50%, all paused connections resume.
- Messages already read when a pause takes effect are still accepted. At 100%, QoS 0 messages are
  dropped and counted; QoS 1/2 fail as in §5.4. A message is never acknowledged and then
  dropped.
- Keep-alive checks treat paused connections as alive.
- Unchanged: Receive Maximum, MQTT 5 flow control, outbound limits, and the existing slow
  subscriber handling (non-persistent copies dropped when the channel is not writable,
  persistent delivery paused on writability).

### 5.6 Lifecycle

- Startup: the store opens first; sessions, subscriptions and retained messages are loaded;
  only then do the listeners bind.
- Shutdown: stop reading, flush the buffered per-client stats, drain both stages within the drain
  timeout, run a final synced WAL flush, close the store. Anything not dispatched by then was
  never acknowledged, so QoS 1/2 publishers resend it after reconnecting. QoS 0 losses are counted.
- The whole shutdown fits in 8 s by default, because `docker stop` kills the process after 10 s.
  Lite lowers the waits it inherits (up to 15 s for client disconnects, 1 minute per Spring
  lifecycle phase, 5 s per Netty listener) to fit. Being killed is safe for acknowledged messages,
  since it equals a crash, but skips the stats flush and loses QoS 0 messages in flight. The
  documentation shows `--stop-timeout` and `stop_grace_period` for longer drains.

### 5.7 Failure behaviour

| Event | Non-persistent subscriber | Persistent DEVICE subscriber | Publisher |
|---|---|---|---|
| Process crash after PUBACK | May miss the message (its session is gone anyway) | Message is in the store, delivered after restart | Nothing to resend |
| Process crash before PUBACK | Not delivered | Possibly already stored; a resend can duplicate it (allowed for QoS 1) | QoS 1/2 resend after reconnecting |
| Power cut | n/a | Messages from the last sync interval can be lost | Same as a process crash |

### 5.8 Settings (standalone only)

| Property | Default |
|---|---|
| `standalone.dispatch.partitions` | available processors, minimum 2 |
| `standalone.persist.partitions` | available processors, minimum 2 |
| `standalone.pipeline.max-bytes` | 10% of the maximum heap |
| `standalone.pipeline.max-messages` | 100000 |
| `standalone.pipeline.pause-percent` / `resume-percent` | 80 / 50 |
| `standalone.shutdown.drain-timeout-ms` | 4000 |
| `store.wal-sync` | `periodic` (alternative: `always`) |
| `store.wal-sync-interval-ms` | 1000 |

## 6. Storage

### 6.1 Engine and formats

One RocksDB instance under `/data/db`, opened before anything else starts. Broker state uses the
protobuf formats the compacted topics already use (`ClientSessionInfoProto`,
`ClientSubscriptionsProto`, `RetainedMsgProto`, `BlockedClientProto`, `DevicePublishMsgProto`), so
the existing converters carry over. Management entities are stored as JSON of the domain models,
written by a dedicated storage `ObjectMapper` rather than the REST one, so an API-only annotation
such as `@JsonIgnore` can never drop a stored field; added fields need no migration.

### 6.2 Column families

| Column family | Key | Value | Notes |
|---|---|---|---|
| `meta` | name | bytes | Schema version, product `LITE`, install id, per-install JWT signing key, last wall-clock time |
| `client_session` | clientId | `ClientSessionInfoProto` | Persistent sessions only; non-persistent sessions live in memory |
| `client_subscriptions` | clientId | `ClientSubscriptionsProto` | Persistent sessions only |
| `retained_msg` | topic | `RetainedMsgProto` | |
| `blocked_client` | type + value | `BlockedClientProto` | |
| `session_ctx` | clientId | inbound QoS 2 packet ids | Saved at disconnect and shutdown, as today (§5.4); replaces `generic_client_session_ctx` |
| `device_msg` | 8-byte hash of clientId + length-prefixed clientId + sequence number (big-endian) | `DevicePublishMsgProto` (packet id, type and expiry time inside) | Appended by stage 2, deleted on acknowledgment. The hash is the fixed-length prefix for Bloom filters; the full clientId keeps scans exact, so `ab` never matches `abc` |
| `device_msg_meta` | clientId | first live sequence, last sequence, last packet id, count | Written in the same batch as every append or delete. Constant-time limit checks; reads start at the first live entry so deleted entries do not slow them |
| `entity` | type + id | JSON | Users, user credentials, admin settings, auth providers, MQTT client credentials, unauthorized clients, WebSocket connections and subscriptions |
| `entity_index` | index name + value | id | Written in the same batch as the entity. Unique lookups: email, credentialsId, settings key, provider type |
| `ts` | entity + key id + timestamp (big-endian) | value | Range scans; aggregation buckets computed in Java |
| `ts_latest` | entity + key id | value + timestamp | |

### 6.3 DAO mapping

- One generic `RocksEntityDao<T>` provides CRUD, unique indexes and paging. Each `*Dao`
  interface Lite needs is a thin class on top of it, or on top of the timeseries store: 13 of 17.
  The two APPLICATION DAOs are dropped, and `IntegrationDao` and `EventDao` get no implementation
  because integrations are not in the first release.
- Each listable entity type keeps an in-memory projection (id, name, type, searchable fields,
  created time) that is updated on every write. Filtering, sorting and text search run on the
  projection; only the documents on the requested page are loaded. At 100k credentials the
  projection is about 20 MB.
- The timeseries DAOs (`TimeseriesDao`, `TimeseriesLatestDao`, `AggregationTimeseriesDao`,
  `KeyDictionaryDao`) use `ts` and `ts_latest`. Seven days of stats on one node is about 130k
  rows.
- `tb_schema_settings` becomes `meta`.

### 6.4 DEVICE message store

`DeviceMsgService` gets a RocksDB implementation with the Valkey one's semantics:
`saveAndReturnPreviousPacketId`, reads in order, removal on acknowledgment, the PUBREL packet-type
update, removal of a whole client queue, and the per-client limit
(`mqtt.persistent-session.device.persisted-messages.limit`, default 10,000, maximum 65,535; the
oldest message is dropped when the limit is exceeded). Each message expires after the
publisher's MQTT 5 message expiry interval when one is set, otherwise after the configured TTL
(default 7 days); messages waiting in PUBREL state expire too.

Reads are paged. A reconnecting client's queue is read `store.device-msg.read-page-size` messages
at a time as its in-flight window frees up, never as one list (today's `findPersistedMessages`
returns the whole queue). After a restart, at most `standalone.persist.max-concurrent-replays`
queues replay at once, so a reconnect storm cannot pull every queue onto the heap.

### 6.5 Expiry and cleanup

- Message expiry is checked when a message is read, plus a slow background sweep.
- The existing session-expiry cleanup removes a session, its subscriptions, its `session_ctx`
  entry and its queue; the queue goes in one range delete.
- The existing retained-message expiry, blocked-client cleanup and unauthorized-client TTL jobs
  delete from the store. Timeseries keep their 7-day TTL through range deletes.

### 6.6 First start and migrations

- An empty store gets its column families and its `meta` record, including a generated JWT
  signing key. It then gets the defaults TBMQ's install step seeds today (sysadmin user, general,
  mail, connectivity and WebSocket settings, the MQTT auth provider order, the five auth providers
  with Basic enabled, the WebSocket-client credential and its default connection), created
  through the services. There is no SQL, no install step and no `.firstlaunch` marker file.
- On startup, if `meta.schema_version` is older than the code: take a checkpoint named
  `pre-upgrade-<from>-<to>`, run the ordered Java migration steps, bump the version. Jumps across
  several versions run their steps in sequence.
- A store newer than the binary refuses to start.

### 6.7 Backup and restore

- `GET /api/system/backup` (sysadmin) and a "Download backup" button in the UI stream a fresh
  RocksDB checkpoint as a `.tar.gz`, so the backup leaves the device. The checkpoint is taken in a
  temporary directory on the volume (hard links: consistent and nearly instant) and deleted after
  streaming. A command-line entry point writes the same archive for scripts.
- Pre-upgrade checkpoints stay on the volume under `/data/backups/`; the last `store.backups.keep`
  are kept. They share files with the live store at first and grow as compaction rewrites it; the
  disk watermark counts them.
- Restore is documented, not automated: stop the broker, unpack the archive in place of
  `/data/db`, start.
- A store that is corrupted at startup makes the broker refuse to start and name the latest
  on-volume checkpoint. With `store.corruption.auto-restore=true` (env `STORE_AUTO_RESTORE`) it
  restores that checkpoint instead and logs it loudly, for unattended devices. Off by default,
  because a silent rollback loses data nobody notices.

### 6.8 Limits and failure handling

- Per-client queue limit and TTL (§6.4); unauthorized clients keep their 3-day TTL and also get a
  count limit, oldest removed first, so an attack cannot fill the disk.
- Free space on `/data` is checked periodically. Below the watermark, or on any store write
  error: persistent appends and retained writes fail (§5.4), management writes return errors,
  non-persistent traffic keeps flowing, health reports degraded, and the process does not
  crash. Writes resume when space recovers.

### 6.9 Memory budget

Defaults are computed from the container memory limit at startup. These are starting points,
tuned in Phases 4 and 5:

- Java heap: `-XX:MaxRAMPercentage=45`.
- RocksDB block cache: 10% of container memory, minimum 16 MiB, maximum 512 MiB. Memtables are
  capped by a WriteBufferManager at 25% of the block cache and charged to it.
- RocksDB `max_open_files`: 256. Bloom filters on the `device_msg` key prefix.
- Netty direct memory: 10% of container memory, minimum 64 MiB.
- Serial GC when the container has less than 2 GiB or fewer than 2 CPUs; G1 otherwise.
- jemalloc is preloaded in the image (`MALLOC_ARENA_MAX=2` where it is not available): glibc's
  default arenas fragment under RocksDB and inflate the process size.

The defaults are tuned for the 2 GB reference (D14). The documentation lists the resulting values
for 1, 2, 4 and 8 GB devices.

### 6.10 Flash wear

- The WAL is synced at most 1 s apart, not on every write.
- Stage 2 batches its writes; whole queues are removed with range deletes.
- Per-client latest stats are kept in memory and written every 10 minutes and at shutdown,
  instead of every minute. The UI and REST read them from memory, so they stay current.
- The Docker image logs to stdout only.
- At startup Lite logs a warning when `/data` is on an SD card (an `mmcblk` device) and
  recommends an SSD or USB disk.

### 6.11 Settings

| Property | Default |
|---|---|
| `store.path` | `${DATA_FOLDER:/data}/db` |
| `store.backups.path` | `${DATA_FOLDER:/data}/backups` |
| `store.backups.keep` | 3 |
| `store.disk.check-interval-ms` | 10000 |
| `store.disk.min-free-percent` / `store.disk.min-free-bytes` | 5 / 268435456; the watermark is the larger of the two |
| `store.memory.block-cache-bytes`, `store.memory.write-buffer-bytes` | derived from container memory when unset (§6.9) |
| `standalone.unauthorized-clients.max` | 10000 |
| `standalone.stats.client-latest-flush-interval-ms` | 600000 |
| `store.device-msg.read-page-size` | 256 |
| `standalone.persist.max-concurrent-replays` | 16 |
| `store.corruption.auto-restore` | false |

## 7. Sessions, cluster mechanisms, authentication, security

### 7.1 Cluster mechanisms in standalone mode

| Today | Standalone |
|---|---|
| Session events: a request topic keyed by clientId hands the event to an actor, which calls `SessionClusterManagerImpl`; the reply returns on the node's response topic | The request goes straight to the same actor. `SessionClusterManagerImpl` keeps all its logic: the same-session guard, two-phase CONNACK, takeover, clean start, expiry checks on connect. Its reply path becomes an interface with a Kafka and a local implementation. CONNACK waits for the `client_session` write |
| Disconnect commands on `tbmq.client.disconnect.<serviceId>` | Direct call |
| Internode notifications (auth provider and settings changes, session-stats cleanup) | Applied locally only |
| Valkey node registry and leader election | One static entry; this node always leads; every cleanup job runs locally |
| Service id = hostname | `standalone.service-id`, default `tbmq-lite`; a recreated container keeps its sessions and stats series |
| Cross-node stats total via `tbmq.sys.historical.data` | Node stats are written as the total; the charts already fall back to the total for a single node |
| Valkey caches: credentials (including not-found results), Basic passwords, X.509 patterns | Caffeine with the same TTLs and a size limit |
| `clientSessionCredentials`, `clientMqttVersion` | Fields of the in-memory session, saved with `client_session` for persistent sessions |
| Total throughput quota, persisted-message rate limit, session and app-client counters (bucket4j on Valkey) | Local bucket4j buckets and counters behind the same interfaces |

### 7.2 Keeping APPLICATION out

- Admin-time inputs reject APPLICATION with a validation error: the credentials form and REST
  API, the CSV bulk import (an error per row), and the default client type of the JWT and HTTP
  providers.
- Runtime inputs map it to DEVICE: a JWT claim or an HTTP provider response that says
  APPLICATION connects the client as DEVICE, with a warning log and a counter.
- The 13 classes that inject APPLICATION services get standalone no-op implementations; the
  APPLICATION persistence package is never active.

### 7.3 Authentication

- All five MQTT auth providers stay: Basic, X.509, JWT, SCRAM, HTTP. Provider changes apply
  locally.
- SCRAM is reimplemented server-side (RFC 5802 and RFC 7677; SHA-256 and SHA-512) without
  `kafka-clients`, reading the same stored credential format so existing SCRAM credentials keep
  working. It lands in CE in Phase 1; both editions run it.
- JWKS (network access needed, keys cached for 24 h) and the HTTP provider (one blocking call per
  CONNECT, no cache) are unchanged.

### 7.4 Secure defaults

| Area | Lite default | Override |
|---|---|---|
| JWT signing key | Generated per install on first start, stored in `meta` | `JWT_TOKEN_SIGNING_KEY` |
| Admin account | `sysadmin@thingsboard.org` / `sysadmin`, flagged so the first login goes through the existing expired-password flow (`UserPasswordExpiredException` → `login/resetExpiredPassword`) | `TBMQ_ADMIN_PASSWORD` presets the password and clears the flag |
| WebSocket-client credential | Seeded with a generated password, stored in its default connection too; keeps full publish/subscribe rights | — |
| Actuator | `/actuator/health` open; `info` and `prometheus` disabled | `TBMQ_METRICS_TOKEN` enables them behind a bearer token |
| MQTT clients on first start | As in TBMQ: Basic is the only provider enabled and no client credentials exist, so nothing connects until the admin creates some; the getting-started page walks through it | — |
| UI and REST transport | HTTP on 8083. HTTPS through the existing `SSL_ENABLED` server settings or a reverse proxy, which the production guide recommends | `SSL_ENABLED` |

The admin-password and WebSocket-credential defaults are worth carrying back to CE. Login
lockout is out of scope; Lite inherits it when CE adds it (GH #363 follow-up).

## 8. UI, REST API, packaging, operations

### 8.1 UI

One codebase and one build; Lite only switches features off.

- `/api/app/config`, which loads before the menu, gains `edition` (`cluster` or `lite`) and
  `capabilities`: `applicationClients`, `kafka`, `cluster`, `integrations`, `nodeDrain`, `bridge`.
  The backend takes `edition` from the `TbmqEdition` bean (§4.2) and derives the capabilities from it. In Lite 3.0 all six are `false`;
  in the `cluster` edition all are `true` except `bridge`.
- The menu is filtered by capabilities as well as by role. A capability guard protects the
  routes of hidden features, so they are not reachable by URL either.
- "TBMQ Lite" is shown in the header and on the system info page.
- A "Download backup" button for the sysadmin (§6.7).
- The full list of pages and elements to hide or adapt is in Appendix B.

### 8.2 REST API

- Controllers and endpoints for features Lite lacks are `@ClusterComponent` and therefore absent
  (404): cluster info and the Kafka endpoints, application shared subscriptions, integrations,
  node drain.
- `AppController` is split first. `/api/app/config`, `/brokers` and `/service/info`, which the UI
  needs in both editions, stay in an edition-neutral controller; `/cluster-info` and the Kafka
  topic and consumer-group endpoints move to a cluster-only one.
- Endpoints that accept a client type validate DEVICE only.
- New endpoints: `GET /api/system/backup` (sysadmin, streams a `.tar.gz`, §6.7) and
  `GET /api/mqtt/client/credentials/export`, a CSV with the columns of the existing
  `POST /api/mqtt/client/credentials/bulk_import`.
- The build checks two specs for drift: `api/openapi.json` and `api/openapi-lite.json`.

### 8.3 Packaging

- `msa/tbmq-lite` builds `thingsboard/tbmq-lite:<version>` for amd64 and arm64 with the existing
  buildx profile.
- Runtime: a jlink-built Java runtime (modules from jdeps plus `jdk.crypto.ec`, `jdk.unsupported`,
  `jdk.management`) on a slim glibc base (Debian slim or distroless, chosen in Phase 4). RocksDB
  and Netty's native libraries need glibc.
- Each per-architecture image uses RocksDB's per-architecture jar (the `linux64` or
  `linux-aarch64` classifier, about 14 MB) instead of the 68 MB all-platform jar.
- jemalloc is installed and preloaded (§6.9).
- A JDK 25 AOT cache is built per architecture during the image build, if it shows a gain in Phase 4.
- JVM defaults as in §6.9.
- Install: `docker run -p 1883:1883 -p 8083:8083 -v tbmq-lite:/data thingsboard/tbmq-lite`. No
  install or upgrade scripts. Upgrade: pull the new tag and recreate the container; migration and
  its pre-upgrade checkpoint run automatically.
- The public beta is published as `thingsboard/tbmq-lite:3.0.0-beta`; `latest` starts at 3.0.0.

### 8.4 Configuration

- The same YAML file and environment variable names as TBMQ. There is no mode setting: the
  image's edition cannot be changed by configuration (§4.2).
- Cluster-only keys (`queue.kafka.*`, `redis.*`, the datasource) are ignored, because no bean
  reads them in standalone mode.
- New keys live under `standalone.*` and `store.*` (§5.8, §6.11).
- A "Lite configuration" documentation page lists only the keys that matter.

### 8.5 Offline operation and the clock

- The GitHub release check (`TBMQReleaseService`, every 3 h) logs failures quietly with backoff;
  `TBMQ_VERSION_CHECK_ENABLED=false` disables it.
- JWKS and the HTTP auth provider need network access; the documentation says so.
- A Raspberry Pi has no hardware clock and may boot with a stale time. Lite records the wall-clock
  time in `meta` every minute. If it starts with a clock earlier than the recorded value, it logs
  a warning and postpones time-based cleanup (session expiry, message-expiry sweeps, TTL jobs)
  until the clock passes that value. The documentation states that NTP is required.

### 8.6 Logging

Standard output only in the image, at INFO, with noisy loggers turned down. File logging is
opt-in with small rotation limits.

### 8.7 Documentation and website

- Positioning (D12): Lite is presented as the single-node production broker and the broker for
  edge sites, labs and laptops; clustered TBMQ as the enterprise offering. A comparison page says
  when to pick Lite, a single `tbmq-node` (APPLICATION clients, integrations, backends that must
  buffer beyond the DEVICE queue cap of D17) or a cluster.
- Documentation: install, configuration, guarantees (ack point, sync window, crash behaviour),
  limits per device class, backup and restore, upgrades, Lite compared with TBMQ, and moving to
  TBMQ (export credentials to CSV, import them into TBMQ, re-point clients, let persistent queues
  drain first). A production guide covers off-device backups, HTTPS, metrics behind the token,
  disk choice and the shutdown timeout.
- Website: rewrite the Mosquitto-alternative pages, which currently say TBMQ cannot run on a
  Raspberry Pi, and list Lite under installation options and pricing (free, Apache 2).

## 9. Testing

1. Unit tests for every new component: dispatcher, persist stage, back-pressure, WAL syncer,
   entity store, migrations, checkpoints, local quotas. SCRAM is tested against the RFC test
   vectors and against credentials created by today's Kafka-based implementation.
2. The existing integration suite runs twice, and the classpath picks the edition exactly as in
   the images; there is no property to flip. `application`'s own tests get the cluster modules
   through its runtime-scope dependencies and run the `cluster` edition. A separate test module
   depends on `application` and its test jar with the cluster modules excluded, plus
   `dao-rocksdb`, and runs the same tests in-process with RocksDB in a temporary directory; until Phase 3 it still uses
   PostgreSQL for management data. CI runs both; cluster- and APPLICATION-only tests are tagged
   and skipped in the `lite` run. This needs a Phase 1 change first:
   `AbstractPubSubIntegrationTest`, which 48 integration test classes and the controller tests
   extend, always starts Kafka and Valkey containers and references Kafka settings classes. It
   becomes an edition-neutral base plus one setup class per edition.
3. The prototype's Paho v3/v5 wire tests (88) become a suite that runs against a live broker in
   either mode.
4. DAO contract tests: one abstract suite runs against both `dao-sql` and `dao-rocksdb`.
5. Durability: kill -9 under load loses no acknowledged message for persistent subscribers;
   messages in flight at the kill may arrive twice, QoS 2 included (D15). A LazyFS run in CI,
   which simulates losing unsynced data the way a power cut does, checks that losses stay within
   the sync interval.
6. Overload: no QoS 1/2 message is acknowledged and then dropped, memory stays bounded, paused
   connections resume, keep-alive does not fire on them.
7. Upgrades: stores saved from each release are migrated forward in CI.
8. Performance runs on the reference (D14): a container limited to 2 GB and to the reference CPU
   count (`--memory=2g --cpus=<n>`) on non-burstable AWS instances (c7g for arm64, c7i for
   amd64, sized to the CPU count) with EBS gp3 volumes, and on the developer PC for quick checks. Three of the four existing perf-test
   scenarios use APPLICATION clients, so the perf-test repo gets DEVICE-only variants of simple,
   general and fan-in, plus a capacity scenario for D14: 9,000 devices publishing about
   1.1 msg/s each (10,000 msg/s in, QoS 1, 256-byte payloads) and 1,000 persistent DEVICE
   subscribers that each receive one slice of that traffic, so every message passes through the
   persistent store. It first runs at the end of Phase 2. A 72-hour soak at that load watches
   memory, store size and disk writes. Phase 5 also times 10,000 Basic-auth clients reconnecting
   after a restart with an empty cache (BCrypt cost 10); that is measured and documented, not a gate. SD
   cards are approximated with Docker's write IOPS and bandwidth limits. CI checks image size,
   startup time and idle memory against their budgets.
9. The ArchUnit and Enforcer rules and the image-level edition tests of §4.6. Unit tests for the UI capability guard plus a manual
   checklist of hidden pages.

## 10. Phases and exit criteria

| Phase | Work | Exit criteria | Size |
|---|---|---|---|
| 1 · CE refactoring, no behaviour change | Edition markers, `TbmqEditionCondition`, `TbmqEdition` and the component annotations in `common/util`. Queue interfaces without Kafka types, the integration executor included. `queue-kafka` and `cache-redis` splits. Auto-configuration and status checker. SCRAM rewrite. Interfaces for the session reply path, disconnects, notifications, registry and quotas. APPLICATION stand-ins. `AppController` split. Capabilities in `/api/app/config` and the UI guard. Edition-neutral integration-test base. ArchUnit rules for `application` and the Enforcer rules for both builds | The full CE suite passes. The perf scenarios show no regression in cluster mode. Shipped in CE 2.5.0. Merged into PE (which drops `msa/tbmq-lite` and adapts its executor) and the demo with no open conflicts | 4 weeks |
| 2 · Standalone runtime | First, the RocksDB DEVICE-queue benchmark on gp3, NVMe and a throttled SD-card stand-in (periodic vs per-batch sync, tombstones, memory settings). Two-stage pipeline, back-pressure, ack point, WAL syncer. Local session events, disconnects, notifications, registry and jobs; fixed service id. Caffeine, local quotas. `dao-rocksdb` for broker state and DEVICE messages, with paged reads and capped replays. Shutdown budget. The `lite` test module, with management data still on PostgreSQL. The D14 capacity scenario in the perf-test repo | The suite passes in standalone mode with PostgreSQL as the only external service. The kill -9 and overload tests pass. First capacity check: the D14 scenario end to end in the reference container, reviewed with the author (go, adjust the targets, or stop) | 5 weeks |
| 3 · Embedded management store | The `dao` / `dao-sql` split, shipped in a 2.x release and merged into PE (which moves its own JPA files). Entity store, the 13 `*Dao` implementations, paging projections, timeseries. First start, `meta`, per-install key. Migrations, checkpoints, downloadable backup, the auto-restore option. Disk watermark, clock guard | No external services. The full standalone suite and the DAO contract tests pass on both backends. A saved store from an earlier version upgrades cleanly | 5 weeks |
| 4 · Lite build, public beta | `msa/tbmq-lite`, jlink runtime, slim base, per-architecture `rocksdbjni`, jemalloc, JVM defaults, multi-arch push. Footprint measured; base image and AOT cache decided. UI gating, branding, `openapi-lite`, credentials CSV export. Secure defaults. Draft documentation | Image within its size budget. `3.0.0-beta` published. One-command install verified in the arm64 and amd64 reference containers | 3 weeks |
| 5 · Hardening, 3.0 | Perf runs, the reconnect test and the soak, LazyFS tests, memory defaults per device class, tuning. Final documentation and website. Beta feedback | The 3.0 definition of done (§11) | 3 weeks |

Total: about 20 weeks of effort to 3.0, 17 to the public beta. One engineer builds it with
Claude Code, in PR-sized tasks; the sizes are effort estimates, not dates, and there is no fixed
date. Phase 1 PRs target `main`, like the other open PRs. The open PRs that touch code Phase 1
rewrites (#370, #385, #400, #401, #404) land after Phase 1 and rebase onto it; whatever has
merged by Phase 3 joins the embedded store's scope (#385, for example, adds an
authorization-policy DAO). Lite code lands on `main` as it is ready: it ships inside 2.x releases but stays out of the
cluster images, and the Lite image is first published with the beta. Phases 2 to 5 are planned in
detail when they start, because they depend on the earlier phases' outcomes.

## 11. 3.0 definition of done

- Functional: the standalone integration suite and the Paho suite pass. Persistent sessions,
  retained messages and blocked clients survive restarts. All five auth providers work. Every
  visible UI page works with no dead links. A downloaded backup restores cleanly, and an upgrade
  from the previous version is verified. A credentials CSV exported from Lite imports cleanly into
  TBMQ.
- Editions: two `tbmq-lite` containers on one host run as independent brokers; neither image can
  be started as the other edition (§4.6).
- Durability: no acknowledged message is lost for persistent subscribers across a kill -9, and
  duplicates stay within D15; power-cut losses stay within the sync interval.
- Footprint: image at most 300 MB uncompressed, idle memory (process RSS) at most 256 MB,
  startup at most 20 s in the arm64 reference container, unless the Phase 2 check sets different targets.
- Capacity and stability: the D14 target sustained in the 2 GB reference container for a 72-hour
  soak with no memory growth; limits for other sizes published.
- Security: no shared default secrets, a forced admin password change, metrics only behind a
  token.
- Documentation and website as in §8.7.

## 12. After 3.0

- 3.1: native two-way MQTT bridge with a store-and-forward buffer in RocksDB, loop prevention,
  topic remapping and a per-bridge status view, reusing the in-tree `netty-mqtt` client. It gets
  its own spec.
- Later: integrations inside the broker through the existing
  `@TbmqOrIntegrationExecutorComponent` hook, PE Lite (8 more tables), deb/rpm packages, a
  no-disk mode, fleet management of many Lite brokers, a GraalVM native-image experiment.

## 13. Risks

| Risk | Mitigation |
|---|---|
| The Phase 1 refactor destabilises CE or causes PE merge pain | No behaviour change, one CE release, immediate PE merge, full suites plus a perf check |
| Conditional beans spread through the code | Two editions only, fixed by the classpath, infrastructure code in separate jars, ArchUnit |
| Memory, startup or capacity in 2 GB misses the targets, and without a spike phase we learn it late | The first capacity check closes Phase 2, on the real pipeline; footprint is measured in Phase 4. Targets are adjusted with the author then. Publish honest numbers and compare with HiveMQ CE, not Mosquitto |
| SD-card wear and latency | 1 s sync, batched writes, stats written every 10 minutes. The documentation recommends an SSD or USB disk for heavy persistence |
| RocksDB native memory gets the container killed | Shared block cache with memtable caps, heap at 45%, memory watched during the soak |
| Users expect APPLICATION or cluster features | A clear comparison table, APPLICATION mapped to DEVICE at runtime, a documented migration path |
| One engineer with Claude Code building two editions | Same repo and release train; small PRs behind CI gates; the standalone suite runs in CI from Phase 2; each phase is planned just before it starts |
| No Raspberry Pi in the test loop | arm64 is tested on AWS Graviton and SD cards are approximated by throttled volumes; the documentation claims arm64 support, including 64-bit Raspberry Pi OS, but no Pi-specific numbers (D10) |
| Production positioning without APPLICATION clients | Backend consumers run as persistent DEVICE clients with the cluster's queue cap (D17): at 10,000 msg/s a backend that takes all traffic can be offline for under 7 seconds before the oldest messages drop. The comparison page sends such backends to `tbmq-node` |
| Five open PRs rebase onto Phase 1 | Phase 1 lands first; its PR descriptions list moved classes and new interfaces so the rebases stay straightforward |

## 14. Open items

Settled in each phase's own spec; none blocks this design:

- Base image and the AOT cache decision (Phase 4).
- The WAL sync interval (Phase 2's benchmark; the default stays periodic, D6), and the published limits for
  sizes other than the reference (Phase 5).
- Final capability names and endpoint paths, if Phase 1 or 4 finds a reason to change the ones
  given here.
- The bridge design (3.1).
- D14's conditions, decided before the Phase 2 capacity check: the reference CPU count, and
  whether the target includes TLS. Prod numbers (about 0.36 vCPU per 1,000 msg/s in+out in
  cluster mode) suggest 10,000 msg/s in plus 10,000 out will not fit 2 CPUs, and 10,000 TLS
  sessions (about 45 KB of heap each) will not fit the 2 GB memory split.

## 15. Sources

- September study: `docs/superpowers/research/2026-09-14-tbmq-lightweight-analysis.md` (kept outside
  the repository).
- April brief and May prototype: `~/projects/gsd/tbmq/tbmq-lightweight-brief.md`,
  `~/projects/gsd/tbmq/.planning/`, `~/projects/gsd/tbmq/lightweight/REVIEW_REPORT.md`.
- Docker image size spec: `docs/superpowers/specs/2026-09-08-docker-image-size-optimization-design.md`
  on `origin/chore/docker-image-size-optimization`.
- Code check of `main` (d9be3f994) and of `tbmq-pe` for the PE-only tables, 2026-10-14. The
  file-level findings, with class names and line references for planning, are in
  `docs/superpowers/research/2026-10-14-tbmq-lite-code-check.md`.

## Appendix A. Kafka topics and their standalone replacement

| Topic | Role today | Standalone |
|---|---|---|
| `tbmq.msg.all` (key: MQTT topic) | Publish work queue; the PUBACK point | Stage 1 dispatch partitions (key: topic) |
| `tbmq.msg.persisted` (key: subscriber clientId) | Staging before the Valkey DEVICE store | Stage 2 persist partitions (key: subscriber clientId) |
| `tbmq.msg.downlink.basic.<serviceId>`, `tbmq.msg.downlink.persisted.<serviceId>` | Per-node delivery | Local delivery through `DownLinkProxy` |
| `tbmq.msg.app.<clientId>`, `tbmq.msg.app.shared.<filter>` | APPLICATION persistence | Not present |
| `tbmq.client.session`, `tbmq.client.subscriptions`, `tbmq.msg.retained`, `tbmq.client.blocked` (compacted) | State and live replication | RocksDB column families, written through, loaded at startup |
| `tbmq.client.session.event.request`, `tbmq.client.session.event.response.<serviceId>` | Session request/response | Direct call into the same actor; local reply |
| `tbmq.client.disconnect.<serviceId>` | Disconnect commands | Direct call |
| `tbmq.sys.internode.notifications.<serviceId>` | Configuration change broadcast | Local apply only |
| `tbmq.sys.app.removed` | APPLICATION housekeeping | Not present |
| `tbmq.sys.historical.data` | Cross-node stats total | Node stats written as the total |
| `tbmq.ie.downlink.*`, `tbmq.ie.uplink`, `tbmq.ie.uplink.notifications.<serviceId>`, `tbmq.msg.ie.<id>`, `tbmq.ie.event.<id>` | Integration executor | Not present in 3.0 |

## Appendix B. UI changes

Paths are relative to `ui-ngx/src/app` unless stated.

- Capabilities: `/api/app/config` is loaded in `core/http/auth.service.ts` before the menu;
  `core/services/menu.service.ts` filters by role only today.
- Route guard: today it checks authentication only; add the capability check.
- Hide:
  - `/kafka/topics`, `/kafka/consumer-groups`, `/kafka/brokers` (the brokers menu entry is already
    commented out, but its route is live);
  - `/shared-subscriptions/applications` (partitions column, "Sync/Delete Kafka topics"
    actions);
  - the getting-started step "Add Application credentials" and its `tbmq_app`/`tbmq_app`
    credential;
  - the "Kafka-based … massive scale" welcome text
    (`ui-ngx/src/assets/locale/locale.constant-en_US.json:1209`);
  - the integration pages.
- Adapt:
  - credentials (`modules/home/pages/client-credentials`): client-type selector, filter, column,
    creation wizard, connectivity check;
  - the client-type blocks of the JWT and HTTP provider forms;
  - sessions: client-type and Node ID columns and filters, the "Application client should be
    persistent" warning;
  - the home page "Applications" count;
  - resource usage: integration-executor rows.
- Add: a "Download backup" button for the sysadmin (§6.7).
- `ClientType.APPLICATION` appears 17 times in 11 files, plus 5 places that iterate over all
  client types.

## Appendix C. Lessons from the May prototype

The prototype (`~/projects/gsd/tbmq/lightweight`) was a standalone copy of parts of TBMQ: about
10k lines, 172 passing tests, clean sessions only, no REST API or UI, credentials creatable only in
code. Within a month its review consisted mostly of fixes back-ported from TBMQ.

Pitfalls this design accounts for:

1. Ordering: two consumers on one shared queue reorder a publisher's messages. Keyed partitions
   are kept (§5).
2. Ack then drop: acknowledging before dispatch and dropping on a full queue breaks QoS 1/2.
   §5.4 and §5.5 forbid it.
3. Takeover races in a reused per-client actor: a late close event from an old connection tore
   down the new session. TBMQ's session-id-tagged handling and same-session guard are kept
   (§7.1).
4. Queue lifecycle took four separate fixes. Drain and counted drops are defined up front
   (§5.6).
5. Startup order: the store must open before anything replays state (§5.6).
6. Native memory: a 75% heap leaves no room for RocksDB. §6.9 caps both.
7. Slow consumers: writability handling, in-flight limits for MQTT 3.1.1 and a sane payload
   default come from TBMQ's existing code.
8. Process: a CI gate from day one, no silently skipped `*IT` tests, verification on real
   hardware.

Worth reusing: the RocksDB lifecycle code as a starting point for `dao-rocksdb`, the
`/proc/mounts` check that warns when `/data` is not a mounted volume, and the Paho v3/v5 wire
tests.
