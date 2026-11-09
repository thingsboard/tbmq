# TBMQ Lite: code check of `main`

Date: 2026-10-14, second check 2026-11-09 (§14)
Commit checked: `main` at d9be3f994 (2.4.1); the second check also read `origin/main` and PE
`origin/main` (52f62469c)

The file-level findings behind `docs/superpowers/specs/2026-10-14-tbmq-lite-design.md`, kept so
that phase plans can be written without repeating the research. Line numbers refer to d9be3f994
and will drift; class names are the stable reference.

Method: three read-only research passes on 2026-10-14 (Kafka, Valkey and cluster coupling;
persistence, UI, integrations and packaging; the May prototype), plus targeted greps.

| Prefix | Path |
|---|---|
| `A/` | `application/src/main/java/org/thingsboard/mqtt/broker/` |
| `Q/` | `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/` |
| `D/` | `dao/src/main/java/org/thingsboard/mqtt/broker/dao/` |
| `UI/` | `ui-ngx/src/app/` |

## 1. Kafka

### 1.1 Topics

Names come from `queue.kafka.<key>`, with `kafka-prefix` prepended. "Group per node" means a
fresh consumer group `serviceId-<startMillis>` on every start, so each node reads the whole
topic.

| Topic | Producer | Consumer | Role |
|---|---|---|---|
| `tbmq.msg.all` (16 partitions, key = MQTT topic) | `PublishMsgQueuePublisherImpl`, reached from `MsgDispatcherServiceImpl.persistPublishMsg`, called by `MqttPublishHandler`, `RestPublishServiceImpl`, `DefaultLastWillService`, `SparkplugCertificateRepublisherImpl` | `PublishMsgConsumerServiceImpl` (one group across the cluster) | Publish work queue; the PUBACK point |
| `tbmq.msg.persisted` (12 partitions, key = subscriber clientId) | `DeviceMsgQueuePublisherImpl`, called by `MsgPersistenceManagerImpl` | `DeviceMsgQueueConsumerImpl`, which writes to `DeviceMsgService` (Valkey) | Staging hop before the DEVICE store |
| `tbmq.msg.app.<clientId or sha256>`, `tbmq.msg.app.shared.<filter>` | `ApplicationMsgQueuePublisherImpl` (partition 0) | `ApplicationPersistenceProcessorImpl` | APPLICATION persistence |
| `tbmq.client.session`, `tbmq.client.subscriptions`, `tbmq.msg.retained`, `tbmq.client.blocked` (compacted, 1 partition) | `*PersistenceServiceImpl`, `BlockedClientProducerServiceImpl` | `ClientSessionConsumerImpl`, `ClientSubscriptionConsumerImpl`, `RetainedMsgConsumerImpl`, `BlockedClientConsumerServiceImpl` (group per node) | Compacted state and live replication between nodes |
| `tbmq.client.session.event.request` (24 partitions, key = clientId) | `DefaultClientSessionEventService` | `ClientSessionEventConsumerImpl`, then the actor, then `SessionClusterManagerImpl` | Request side of the session request/response; per-client serialization |
| `tbmq.client.session.event.response.<serviceId>` | `SessionClusterManagerImpl:411-430` | `DefaultClientSessionEventService` | Per node |
| `tbmq.client.disconnect.<serviceId>` | `DisconnectClientCommandServiceImpl` | `DisconnectClientCommandConsumerImpl` | Per node |
| `tbmq.msg.downlink.basic.<serviceId>`, `tbmq.msg.downlink.persisted.<serviceId>` | `DownLinkQueuePublisherImpl` | `BasicDownLinkConsumerImpl`, `PersistentDownLinkConsumerImpl` (group per node, offset reset `latest`) | Per node |
| `tbmq.sys.internode.notifications.<serviceId>` | `InternodeNotificationsServiceImpl` | `InternodeNotificationsConsumerImpl` | Per node: auth settings and providers, session-stats cleanup, integration lifecycle config |
| `tbmq.sys.app.removed` (1 partition) | `ApplicationRemovedEventServiceImpl` | `ApplicationRemovedEventProcessorImpl` (cron 03:00) | APPLICATION housekeeping |
| `tbmq.sys.historical.data` (1 partition) | `TbMessageStatsReportClientImpl` (every node) | `HistoricalStatsTotalConsumer` | Cross-node stats total; one group means one aggregator |
| `tbmq.ie.downlink.{http,kafka,mqtt}`, `tbmq.ie.uplink`, `tbmq.ie.uplink.notifications.<serviceId>`, `tbmq.msg.ie.<id>`, `tbmq.ie.event.<id>` | `Tbmq*` / `Executor*QueueProvider`, `IntegrationMsgQueuePublisherImpl`, `IntegrationEventMsgQueuePublisherImpl` | Executor `IntegrationMsgProcessorImpl`; broker `IntegrationUplinkConsumer` | Integration executor (§7) |

Per-node routing:

- The target serviceId comes from the replicated `ClientSessionInfo.serviceId` (downlink,
  disconnect), from a `responseTopic` header (session responses), or from the Valkey registry
  (internode notifications).
- Only downlink has a local shortcut (`DownLinkProxyImpl:41-58`). Internode notifications are
  applied locally first (`InternodeNotificationsServiceImpl:63-71`). Disconnect commands and
  CONNECT responses always travel through Kafka, even when the target is the local node.
- The prefixes `tbmq.msg.app.`, `tbmq.msg.ie.` and `tbmq.ie.event.` are hard-coded.
- Four helpers build per-node topic names from Kafka settings classes:
  - `A/service/historical/stats/HistoricalStatsTotalHelperImpl.java`
  - `A/service/mqtt/client/disconnect/DisconnectClientCommandHelperImpl.java`
  - `A/service/notification/InternodeNotificationsHelperImpl.java`
  - `A/service/processing/downlink/DownLinkPublisherHelperImpl.java`

### 1.2 The QoS 1 publish path

1. `MqttSessionHandler.processPublish:233-257` checks the per-client incoming limit (local
   bucket4j) and the cluster-wide total quota (a Valkey lease); both are off by default. On
   rejection, MQTT 5 gets PUBACK 0x97 and MQTT 3 is disconnected. Otherwise the message goes to
   the `ClientActor`.
2. `MqttPublishHandler:89-154` resolves the topic alias and validates (MQTT 5 errors are acked
   immediately). It adds the packet to an `OrderedProcessingQueue` capped by
   `max-in-flight-msgs` (1000, advertised as Receive Maximum); overflow disconnects the client.
   A retained message updates the in-memory trie; its Kafka write is async and not awaited
   (`RetainedMsgListenerServiceImpl:82-93`).
3. `persistPubMsg:233-258` calls `MsgDispatcherServiceImpl:97-104`, which writes to
   `tbmq.msg.all`, keyed by topic name.
4. The Kafka callback runs on the `publish-callback-processor` pool (2 threads) and sends
   `PubAckResponseMsg` to the actor. `processPubAckResponse:260-270` writes the PUBACKs in packet
   order and flushes. PUBREC works the same way (`:272-286`); PUBCOMP is sent immediately on
   PUBREL. A Kafka failure gives an MQTT 5 error PUBACK or an MQTT 3 disconnect. Defaults:
   `acks=1`, `replication.factor=1`.
5. `PublishMsgConsumerServiceImpl:83-129` calls `processPublishMsg:107-131`, then
   `PersistentMsgSubscriptions:101-158`. A subscriber counts as persistent only if the publish QoS
   is above 0, the session is persistent, and the subscription QoS is above 0.
   - Non-persistent: `BasicDownLinkProcessorImpl` applies the outgoing limit and quota, then
     `DefaultMqttMsgDeliveryService:89-121` writes straight to Netty from the consumer thread and
     drops the message if the channel is not writable.
   - DEVICE: `MsgPersistenceManagerImpl:76-110` (device limit, quota) sends to
     `tbmq.msg.persisted`. `DeviceMsgQueueConsumerImpl:128-164` calls
     `DeviceMsgService.saveAndReturnPreviousPacketId`, where a Lua script assigns packet ids.
     After the commit, `deliverClientDeviceMessages` hands the message to `DownLinkProxy`, then the
     `PersistedDeviceActor`. On reconnect the actor reads Valkey with blocking `.get()` calls
     (`PersistedDeviceActorMessageProcessor:126-150`). Offline DEVICE shared-subscription
     messages are stored under the pseudo-client id `ss_<share>_<filter>`.
   - APPLICATION: the per-client topic is read by a dedicated consumer thread.
   - Commit: the `tbmq.msg.all` offset waits up to 20 s for the persistent copies; the default
     `SKIP_ALL` strategy then commits anyway and counts them as dropped.

### 1.3 Back-pressure today

- There is no `autoRead` anywhere in the broker (only in the netty-mqtt client), and actor
  mailboxes are unbounded.
- Inbound: the limits above, Receive Maximum, the pre-connect queue (1000), the session-event
  pending map (10,000), and implicitly Kafka `buffer.memory` / `max.block.ms` blocking `send` on
  the actor thread.
- Between publish and delivery: nothing. Lag on `tbmq.msg.all` accumulates.
- Outbound: writability changes (`MqttSessionHandler:320-328`) pause APPLICATION consumers
  (`ApplicationPersistenceProcessorImpl:356-371`) and the DEVICE actor; MQTT 5 flow control
  (delayed queue of 1000, TTL 600 s); a QoS 0 outgoing limit.
- #305 is the APPLICATION outbound rate limit. It exists only on the unmerged branch
  `origin/feature/305-app-outbound-rate-limit` and is unrelated to `tbmq.msg.all` lag.

### 1.4 Kafka types in the queue interfaces

Files outside `Q/kafka/` that import `org.apache.kafka`:

| File | Kafka type |
|---|---|
| `Q/TbQueueConsumer.java` | `subscribe(ConsumerRebalanceListener)` (:28) |
| `Q/TbQueueControlledOffsetConsumer.java` | `TopicPartition` (:36-38); also `commit(partition, offset)` and `getCommittedOffset` |
| `Q/TbQueueMsgMetadata.java` | returns `RecordMetadata` (:22) |
| `Q/TbQueueAdmin.java` | returns `Node` and `ListConsumerGroupOffsetsResult` (:47, :66) |
| `Q/common/AbstractTbQueueConsumerTemplate.java` | consumer template |

Also: `TbQueueMsg` carries partition and offset; `TbQueueProducer.send` takes a partition; topic
configuration is passed as Kafka property strings; `AbstractQueueFactory` forces `partitions=1`
and `cleanup.policy=compact`.

### 1.5 Implementation selection

- There is no switch. All Kafka factories and `TbKafkaAdmin` are unconditional components under
  a whole-package `@ComponentScan`.
- Role annotations live in `Q/`: `TbmqComponent` (`@ConditionalOnExpression("'${service.type:null}'=='tbmq'")`),
  `TbmqIntegrationExecutorComponent`, `TbmqOrIntegrationExecutorComponent`. `service.type`
  selects broker or executor; the two are mutually exclusive.
- No in-memory queue implementation exists anywhere, tests included (only Mockito mocks and one
  capturing producer). Integration tests use Testcontainers for Kafka, Valkey and PostgreSQL.

Broker Kafka factories, `Q/provider/` (14):
`KafkaApplicationPersistenceMsgQueueFactory`, `KafkaApplicationRemovedEventQueueFactory`,
`KafkaBlockedClientQueueFactory`, `KafkaClientSessionEventQueueFactory`,
`KafkaClientSessionQueueFactory`, `KafkaClientSubscriptionsQueueFactory`,
`KafkaDevicePersistenceMsgQueueFactory`, `KafkaDisconnectClientCommandQueueFactory`,
`KafkaDownLinkBasicPublishMsgQueueFactory`, `KafkaDownLinkPersistentPublishMsgQueueFactory`,
`KafkaHistoricalDataQueueFactory`, `KafkaInternodeNotificationsQueueFactory`,
`KafkaPublishMsgQueueFactory`, `KafkaRetainedMsgQueueFactory`.

Integration Kafka factories, `Q/provider/integration/` (6):
`KafkaHttpIntegrationDownlinkQueueFactory`, `KafkaIntegrationDownlinkQueueFactoryImpl`,
`KafkaIntegrationMsgQueueFactory`, `KafkaIntegrationUplinkNotificationsQueueFactory`,
`KafkaIntegrationUplinkQueueFactory`, `KafkaMqttIntegrationDownlinkQueueFactory`.

### 1.6 Queue-layer usage in `application`

84 of 743 files import `queue.*`.

`TbQueueAdmin` users (10); the six marked * call `deleteOldConsumerGroups` at startup:

- `A/controller/BaseController.java`
- `A/service/entity/app/DefaultTbAppService.java`
- `A/service/mqtt/client/blocked/consumer/BlockedClientConsumerServiceImpl.java` *
- `A/service/mqtt/client/session/ClientSessionConsumerImpl.java` *
- `A/service/mqtt/persistence/application/ApplicationPersistenceProcessorImpl.java`
- `A/service/mqtt/persistence/application/topic/ApplicationTopicServiceImpl.java`
- `A/service/mqtt/retain/RetainedMsgConsumerImpl.java` *
- `A/service/processing/downlink/basic/BasicDownLinkConsumerImpl.java` *
- `A/service/processing/downlink/persistent/PersistentDownLinkConsumerImpl.java` *
- `A/service/subscription/ClientSubscriptionConsumerImpl.java` *

`TbQueueControlledOffsetConsumer` users (9):

- `A/service/mqtt/client/blocked/consumer/BlockedClientConsumerServiceImpl.java`
- `A/service/mqtt/client/event/ClientSessionEventConsumerImpl.java`
- `A/service/mqtt/client/event/DefaultClientSessionEventService.java`
- `A/service/mqtt/client/session/ClientSessionConsumerImpl.java`
- `A/service/mqtt/persistence/application/ApplicationPersistenceProcessorImpl.java`
- `A/service/mqtt/persistence/application/data/ApplicationMainProcessingState.java`
- `A/service/mqtt/persistence/application/topic/ApplicationRemovedEventProcessorImpl.java`
- `A/service/mqtt/retain/RetainedMsgConsumerImpl.java`
- `A/service/subscription/ClientSubscriptionConsumerImpl.java`

Worst offenders: `ApplicationPersistenceProcessorImpl` (manual partitions and offsets),
`ApplicationTopicServiceImpl`, the four compacted-topic consumers, `SessionClusterManagerImpl`,
`AppController`.

What makes an in-process queue implementation awkward: ordering depends on keyed partitions
(topic name, subscriber clientId, session-event clientId); startup replay uses a marker record
plus tombstones; topics are created at runtime and `send` itself calls `createTopicIfNotExists`;
request/response routing travels in headers. This is why the design replaces Kafka at the service
level (spec D9).

### 1.7 Kafka in the REST API and UI

`/api/app/cluster-info`, `/kafka-topics`, `/consumer-groups`, `DELETE /consumer-group`,
`/remove-topics`, the shared-subscription `createTopics` / `deleteTopics`, and a
`KafkaHealthIndicator` that is always on. (The home-page Kafka table components in
`UI/modules/home/components/kafka-tables/` are referenced nowhere; corrected 2026-11-09.)

## 2. Valkey/Redis

Three clients, all configured in `common/cache` `TBRedisCacheConfiguration`: Jedis
(CacheManager, RedisTemplate), Lettuce (the DEVICE store), UnifiedJedis (bucket4j).

| Usage | Structure | Classification |
|---|---|---|
| Caches `mqttClientCredentials` (1 day, not-found results cached too), `basicCredentialsPassword` (1 min), `sslRegexBasedCredentials` (1 day), via `TbCacheOps` | RedisCache, JSON | Cache. The shared cache is also the only cross-node invalidation mechanism |
| `clientSessionCredentials`, `clientMqttVersion` (TTL 0), written on CONNECT (`SessionClusterManagerImpl:499-514`), read by `ClientSessionController:146-156` | RedisCache | Shared session metadata, not a cache |
| `D/messages/DeviceMsgServiceImpl.java` | ZSET `{cid}_messages`, a STRING per message with `SET EX`, `{cid}_last_packet_id`; 5 inline Lua scripts | Durable state (§2.1) |
| `A/service/limits/RateLimitRedisCacheServiceImpl.java` | bucket4j CAS buckets (device-persisted messages, total); INCR/DECR session and APP-client counters | Rate limits and counters, all off by default |
| `ThroughputQuotaServiceImpl` | block draws from the total bucket | Rate limit |
| `A/service/system/TbmqSystemInfoService.java` | HASH `tbmq:service:registry` (serviceId → type), HSET every 5 min, no TTL | Cluster coordination (§3) |

- Not found: locks, connection tracking, expiry bookkeeping, drain state.
- Per-client publish limits are already local.
- There is no cache-type switch, only `redis.connection.type`.
- The shipped Valkey cluster compose runs `--appendonly no`.

### 2.1 The DEVICE message store

`D/messages/DeviceMsgServiceImpl.java`:

- The Lua scripts are loaded synchronously at startup; the service throws if Valkey is
  unreachable, which is why install needs Redis.
- Limit: `mqtt.persistent-session.device.persisted-messages.limit`, default 10000, validated to be
  at most 65535 (:190), matching the packet-id wrap. When it is exceeded, the oldest messages are
  removed (`ZRANGE 0..n-1`, then `DEL` and `ZREM`).
- TTL: `mqtt.persistent-session.device.persisted-messages.ttl`, default 604800 s.
- Bug: the `UPDATE_PACKET_TYPE` script uses a plain `SET`, which drops the TTL, so messages in
  PUBREL state never expire.

## 3. Cluster-only mechanisms

- Discovery: the Valkey hash above; executors register through `tbmq.ie.uplink`; liveness comes
  from the CPU and memory timestamps in `ts_kv_latest`. Registry entries never expire.
- Leader election: the lowest sorted serviceId (`A/service/provider/AbstractServiceProvider.java:45-52`;
  runs the job if the registry is empty). It covers `TimeseriesCleanUpService`,
  `EventsCleanUpService` and `TBMQReleaseService`. A dead node that sorts first means no live node
  runs those jobs.
- Other once-per-cluster jobs: single-partition consumer groups (historical totals, the
  app-removed cron); ownership by serviceId (session-expiry cleanup handles only its own sessions,
  `ClientSessionCleanUpServiceImpl:99-129`).
- Jobs that run on every node (idempotent): retained-message expiry, blocked-client cleanup, trie
  cleanup, unauthorized-client TTL.
- `SessionClusterManagerImpl` runs in the actor on the node that owns the clientId's event
  partition. Responsibilities:
  - request timeout (:119) and the same-session guard;
  - session and APP quotas (:133-145, Valkey);
  - conflict disconnect over Kafka (:477-485);
  - the session metadata cache writes (:499-514);
  - saving the session to Kafka, which gates CONNACK;
  - PostgreSQL `removeAllLatestForClient` (:386);
  - expiry checks on connect;
  - the APPLICATION-to-DEVICE switch;
  - clean start: clearing subscriptions and persisted messages;
  - two-phase CONNACK and the Kafka reply (:411-430);
  - disconnect and clear-session finalization;
  - APPLICATION topic deletion (:336-351).

  Its only direct infrastructure coupling is the reply producer and its factory; everything else
  goes through interfaces.
- Session request/response: `DefaultClientSessionEventService:141-167`, with requestId,
  requestTime and responseTopic headers and a 100 s timeout.
- The serviceId defaults to the hostname and is stored in every session. If a container is
  recreated with a new hostname, its sessions are orphaned (never expiry-cleaned, routed to a dead
  node's topic) and per-node stats split.
- Hosting the integration executor in the broker process needs `@TbmqComponent` reworked, because
  both roles define beans for the same interfaces.

## 4. Coupling summary

Files with direct imports, main sources only:

| Module | Kafka | Redis | bucket4j-redis |
|---|---|---|---|
| application | 3 | 2 | 1 |
| common/queue | 14 | 0 | 0 |
| common/cache | 0 | 8 | 1 |
| dao | 0 | 1 | 0 |
| integration/executor | 4 | 0 | 0 |
| cluster-integration-api | 1 | 0 | 0 |

`application` files importing `org.apache.kafka`:

- `A/service/auth/DefaultEnhancedAuthenticationService.java` (SCRAM, `ScramSaslServer`, :21-22)
- `A/service/auth/enhanced/ScramAuthCallbackHandler.java` (SCRAM)
- `A/service/integration/IntegrationCleanupServiceImpl.java` (catches a Kafka exception)

`application` files importing Redis types: `A/service/limits/RateLimitRedisCacheServiceImpl.java`,
`A/service/system/TbmqSystemInfoService.java`.

Layer checks:

- `A/server/**` has no Kafka, Redis or queue imports. It does use the Valkey-backed limit
  interfaces.
- `A/actors/**` has no direct Kafka or Redis imports, but 7 files use queue or cache types:
  `ServiceInfoProvider`, `TbQueueCallback` (PUBACK is triggered from one), and a producer in
  `SessionClusterManagerImpl`.

APPLICATION code used outside `A/service/mqtt/persistence/application/` (13 classes, all need a
standalone stand-in or an interface):

- `A/actors/client/service/channel/ChannelBackpressureManagerImpl.java`
- `A/actors/client/service/handlers/MqttSubscribeHandler.java`
- `A/actors/client/service/handlers/MqttUnsubscribeHandler.java`
- `A/actors/client/service/session/SessionClusterManagerImpl.java`
- `A/controller/AppController.java`
- `A/service/entity/appsharedsub/DefaultTbAppSharedSubscriptionService.java`
- `A/service/mqtt/persistence/MsgPersistenceManagerImpl.java`
- `A/service/stats/ApplicationProcessorStats.java`
- `A/service/stats/DefaultApplicationProcessorStats.java`
- `A/service/stats/StatsManager.java`
- `A/service/stats/StatsManagerImpl.java`
- `A/service/stats/StatsManagerStub.java`
- `A/service/stats/StubApplicationProcessorStats.java`

dao internals imported by `application` (must move before the dao split): `ProtocolUtil`
(`dao/util/protocol`, 3 files), `UserServiceImpl` (`dao/user`, 2 files), `SqlQueueStatsManager`
(`dao/sql`, 1 file).

## 5. Persistence (PostgreSQL)

### 5.1 Counts

- 19 tables in `dao/src/main/resources/sql/schema-entities.sql`. Four are partitioned by time:
  `ts_kv` by day; `stats_event`, `lc_event` and `error_event` by week. Partitions are created on
  write and dropped when they expire.
- 19 `@Entity` classes: 18 in use, plus a dead `DeviceSessionCtxEntity` mapped to a table that
  does not exist.
- 33 repository-named files: 18 Spring Data JPA interfaces, 9 hand-written JdbcTemplate classes, 5
  interfaces for those, and 1 generic `EventRepository`.
- 15 dao-api interfaces: 13 SQL-backed; `DeviceMsgService` runs on Valkey; `TopicValidationService`
  stores nothing.
- There is no dropped-messages table in CE. `droppedMsgs` is a stats key in `ts_kv` plus a
  Micrometer counter. (PE has a `dropped_msg` table.)

### 5.2 Contracts

| Contract | Table(s) | Data and volume | Query needs |
|---|---|---|---|
| User | broker_user, user_credentials | a few admins | lookup by email or token; paged LIKE search with sorting |
| AdminSettings | admin_settings | 6 keys | get and put by key |
| MqttAuthProvider | mqtt_auth_provider (jsonb) | exactly 5 rows | by type; paged list; update of the enabled flag |
| MqttClientCredentials | mqtt_client_credentials | tens to about 100k rows; read on every CONNECT (cached) | `credentials_id IN (…)`; by name or type; paged search; the v2 filter does LIKE inside the stored JSON text; full scan to count by type |
| UnauthorizedClient | unauthorized_client | one row per failing clientId, TTL 3 days, unbounded under attack | batched upserts and deletes; multi-field LIKE plus time range; TRUNCATE |
| GenericClientSessionCtx | generic_client_session_ctx | pending QoS 2 packet ids per persistent client, DEVICE included | by clientId; bulk upsert, saved only on disconnect or `@PreDestroy` |
| ApplicationSessionCtx, ApplicationSharedSubscription | application_* | APPLICATION only | not needed in Lite |
| Integration | integration | a few | by id, all, count, paged |
| Event | the 3 event tables | integration events only, TTL 14 days | batch insert; time-range paging; ILIKE filters; latest N; partition drop |
| Timeseries | ts_kv, ts_kv_latest, ts_kv_dictionary | `ts_kv` about 13 rows/min on one node (about 130k rows at the 7-day TTL). `ts_kv_latest`: 6 keys per service plus up to 8 per active client, upserted every minute (about 670 writes/s at 10k active devices) | range queries with MIN/MAX/AVG/SUM/COUNT buckets (up to 700), limit and order; batched upserts; TTL |
| WebSocketConnection, WebSocketSubscription | websocket_* (jsonb, cascading deletes) | a few per user | paged by owner; by name |

### 5.3 `*Dao` interfaces (17)

Implemented over JPA or JdbcTemplate (`D/AbstractDao`, `Jpa*Dao`, `SqlTimeseriesLatestDao`,
`JpaSqlTimeseriesDao`, `AbstractChunkedAggregationTimeseriesDao`), used by the service classes.
Lite needs all but the first two:

- `D/client/application/ApplicationSessionCtxDao.java` (APPLICATION)
- `D/client/application/ApplicationSharedSubscriptionDao.java` (APPLICATION)
- `D/client/generic/GenericClientSessionCtxDao.java`
- `D/client/MqttClientCredentialsDao.java`
- `D/client/provider/MqttAuthProviderDao.java`
- `D/client/unauthorized/UnauthorizedClientDao.java`
- `D/dictionary/KeyDictionaryDao.java`
- `D/event/EventDao.java`
- `D/integration/IntegrationDao.java`
- `D/settings/AdminSettingsDao.java`
- `D/sqlts/AggregationTimeseriesDao.java`
- `D/timeseries/TimeseriesDao.java`
- `D/timeseries/TimeseriesLatestDao.java`
- `D/user/UserCredentialsDao.java`
- `D/user/UserDao.java`
- `D/ws/WebSocketConnectionDao.java`
- `D/ws/WebSocketSubscriptionDao.java`

Services that use them: `ApplicationSessionCtxServiceImpl`, `ApplicationSharedSubscriptionServiceImpl`,
`GenericClientSessionCtxServiceImpl`, `MqttClientCredentialsServiceImpl`,
`MqttAuthProviderServiceImpl`, `UnauthorizedClientServiceImpl`, `BaseEventService`,
`IntegrationServiceImpl`, `AdminSettingsServiceImpl`, `BaseTimeseriesService`, `UserServiceImpl`,
`WebSocketConnectionServiceImpl`, `WebSocketSubscriptionServiceImpl`.

### 5.4 Outside dao-api

- `tb_schema_settings`, accessed with JdbcTemplate in
  `A/service/install/DefaultDatabaseSchemaSettingsService.java`.
- `ApplicationStatusChecker` refuses to start without a Hikari datasource.
- The Valkey state of §2: the registry, counters, rate-limit buckets and the two session metadata
  caches.

### 5.5 Postgres-specific SQL

`ON CONFLICT` upserts, `ts_kv` partition maintenance, `ILIKE`, `DELETE … RETURNING`, `TRUNCATE`,
`pg_tables` scans, `DETACH PARTITION`, jsonb columns, serial keys.

### 5.6 Spring bootstrap

- `A/ThingsboardMqttBrokerApplication.java`: `@SpringBootConfiguration`, `@EnableAsync`,
  `@EnableScheduling`, `@ComponentScan("org.thingsboard.mqtt.broker")`. No
  `@EnableAutoConfiguration`.
- `JpaDaoConfig` (dao): `@EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)`,
  `@ComponentScan("org.thingsboard.mqtt.broker.dao")`, `@EnableJpaRepositories`, `@EntityScan`,
  `@EnableTransactionManagement`. It is the only place auto-configuration is enabled, so dropping
  `dao` drops auto-configuration for the whole application.

### 5.7 PE-only tables

`tbmq-pe` adds 8 tables: audit_log, domain, domain_oauth, dropped_msg, oauth, resource, role,
white_labeling. Its dao-api adds `AuditLogService`, `DomainService`, `EntityDaoService`,
`OAuth2ClientService`, `OAuth2ConfigTemplateService`, `ImageService`, `ResourceService`,
`RoleService`. A PE Lite needs embedded-store implementations for all of them.

## 6. Install and upgrade

- Entry point: `ThingsboardMqttBrokerInstallApplication` (profile `install`), which runs
  `A/install/ThingsboardMqttBrokerInstallService.java`.
- Fresh install: runs the two schema SQL files over plain JDBC, records the schema version and
  product `CE` in `tb_schema_settings`, then seeds:
  - user sysadmin@thingsboard.org with password `sysadmin`;
  - settings: general (baseUrl http://localhost:8083), mail (localhost:25), connectivity
    (1883/8883/8084/8085), websocket, and the MQTT auth provider order;
  - the 5 auth providers, with only Basic enabled;
  - the MQTT credential "TBMQ WebSockets MQTT Credentials": DEVICE type, username
    `tbmq_websockets_username`, no password, allowed to publish and subscribe to everything;
  - a default WebSocket-client connection.
  - Security settings are not seeded; the code falls back to a password length of 6 to 72.
- Upgrade: checks product and version (`SUPPORTED_VERSIONS_FOR_UPGRADE=["2.4.0"]`, overridable
  with `SKIP_SCHEMA_VERSION_CHECK`), runs `application/src/main/data/upgrade/basic/schema_update.sql`
  (empty for 2.4.1), then re-runs the schema files. Only a one-release step is supported.
- A normal start does not check the schema version. Auth providers fail at startup if their rows
  are missing (for example `HttpMqttClientAuthProvider`).
- The install step needs Redis (§2.1). It does not need Kafka.
- Docker: `thingsboard/tbmq` installs on first start when `/data/.firstlaunch` is missing (the
  marker is on the volume, not in the database). `tbmq-node` installs or upgrades through one-off
  containers started with `INSTALL_TB` or `UPGRADE_TB` (`docker/scripts`, k8s
  `database-setup.yml`). Compose upgrades go through `msa/tbmq/configs/tbmq-upgrade.sh`.

## 7. Integrations

- CE has three types, HTTP, MQTT and KAFKA, all outbound. Their code exists only in
  `integration/executor`. The broker carries the shared integration API and no-op stubs
  (`A/service/integration/NoOp*`).
- A combined mode has a starting point: beans marked `@TbmqOrIntegrationExecutorComponent` (for
  example `IntegrationManagerServiceImpl`) already load in both service types.
- Broker and executor talk over Kafka (message types in `common/queue/src/main/proto/integration.proto`):
  - `tbmq.msg.ie.<id>`: published MQTT messages;
  - `tbmq.ie.event.<id>`: opt-in MQTT client lifecycle events (connected, disconnected,
    subscribed, unsubscribed, authentication, authorization and connection failures), a second
    outbound data stream;
  - `tbmq.ie.downlink.{http,kafka,mqtt}`: create, update, delete and "check connection" requests;
  - `tbmq.ie.uplink`: integration stats, lifecycle and error events (written to the event tables),
    plus executor CPU and memory info;
  - `tbmq.ie.uplink.notifications.<serviceId>`: replies to "check connection".
- The executor needs only Kafka. Its default ack strategy is `SKIP_ALL`, so Kafka holds a backlog
  only while an integration is stopped or failed; batches that fail while it runs are skipped
  unless `RETRY_ALL` is configured.
- The MQTT integration uses the in-tree `netty-mqtt` client (`org.thingsboard.mqtt.MqttClient`):
  MQTT 3.1 to 5, TLS, publish only.
- CE has no inbound direction. PE `main` has `KAFKA_SOURCE` and `MQTT_SOURCE` (through
  `tbmq.ie.source` to `IntegrationSourceConsumer`) and five more outbound types: REDIS, RABBITMQ,
  POSTGRESQL, TIMESCALEDB, MONGODB.

## 8. Authentication

- MQTT providers: Basic, X.509, JWT, SCRAM, HTTP. Each has one jsonb row; the evaluation order is
  an admin setting. Credentials for Basic, X.509 and SCRAM live in `mqtt_client_credentials`.
- Provider changes are applied locally, then sent to the other nodes over Kafka using node ids
  from the Valkey registry (`A/service/notification/InternodeNotificationsServiceImpl.java`).
- JWT with JWKS: keys cached by Nimbus with refresh ahead of expiry and a 24-hour outage tolerance
  (`A/service/auth/providers/jwt/JwksVerificationStrategy.java`).
- HTTP provider: one HTTP call per CONNECT, no result cache, blocks the connect thread.
- SCRAM uses `kafka-clients` classes (`ScramSaslServer` in
  `A/service/auth/DefaultEnhancedAuthenticationService.java`, plus
  `A/service/auth/enhanced/ScramAuthCallbackHandler.java`).
- The APPLICATION client type can enter through four routes: the credentials form and REST API,
  the CSV bulk import (`POST /api/mqtt/client/credentials/bulk_import`, `CLIENT_TYPE` column), the
  JWT provider (default type and claim mapping), and the HTTP provider (default type and its
  response).
- UI and REST login: `/api/auth/login` and `/api/auth/token` issue stateless HS512 tokens (access
  9000 s, refresh 604800 s), signed with `security.jwt.tokenSigningKey` from the YAML file
  (`A/config/JwtSettings.java`, `A/service/security/model/token/JwtTokenFactory.java`).
- One role, SYS_ADMIN. The password policy (length, character classes, expiry, reuse) is an admin
  setting. No login lockout. Password reset goes over SMTP.
- An expired-password flow exists end to end: `DefaultSystemSecurityService:119` throws
  `UserPasswordExpiredException` with a reset token, `ThingsboardErrorResponseHandler:241` maps
  it, and `UI/modules/login/pages/login/login.component.ts:70` navigates to
  `login/resetExpiredPassword`. Lite reuses it to force a password change on first login.

## 9. Stats and monitoring

- `TbMessageStatsReportClientImpl` runs every minute on each node. It writes incoming, outgoing and
  dropped message counts and traffic to `ts_kv` under the node's id, and sends them to
  `tbmq.sys.historical.data`. It also writes 8 per-client counters to `ts_kv_latest`.
- `HistoricalStatsTotalConsumer` (one shared group) sums all nodes into the `total` series. Every
  node also writes session, subscription and retained-message counts to `total`.
- `TbmqSystemInfoService` saves CPU, memory and disk to `ts_kv_latest` every 60 s.
- Cleanup: timeseries daily (7-day TTL) and events hourly (14-day TTL), run only by the leader
  (§3). Unauthorized-client cleanup runs on every node.
- The UI reads `/api/timeseries/total/values`, `/api/timeseries/latest`, `/api/app/brokers` and
  `/api/app/service/info`. The monitoring charts fall back to `total` when there is at most one
  broker.
- Actuator health, info and Prometheus are exposed on port 8083 without authentication. Health
  combines the MQTT listeners, Kafka, node drain, and Spring's automatic database and Redis
  checks.

## 10. UI

- Startup: `UI/core/http/auth.service.ts:274-319` loads `/api/app/config`, then connectivity
  settings, then the user. `/api/app/config` returns ports, listener and auth flags,
  `allowKafkaTopicDeletion` and `statsCollectionInterval`.
- No edition, capability or feature-flag mechanism exists. `environment.ts` holds only title,
  version and languages. `/api/edges/enabled` and `/api/uiSettings/helpBaseUrl` are ThingsBoard
  leftovers with no backend.
- `UI/core/services/menu.service.ts` builds the menu from the user's role only. The route guard
  checks only authentication, so a hidden menu entry still leaves the page reachable by URL.
- To hide in Lite:
  - `/kafka/topics`, `/kafka/consumer-groups`, `/kafka/brokers` (the brokers menu entry is already
    commented out, but its route is live);
  - `/shared-subscriptions/applications` (partitions column, "Sync/Delete Kafka topics" actions);
  - the getting-started step "Add Application credentials", which creates `tbmq_app`/`tbmq_app`;
  - the "Kafka-based … massive scale" welcome text (`ui-ngx/src/assets/locale/locale.constant-en_US.json:1209`);
  - the integration pages.
- To adapt in Lite:
  - credentials (`UI/modules/home/pages/client-credentials`): client-type selector, filter,
    column, creation wizard, connectivity check;
  - the client-type blocks in the JWT and HTTP provider forms;
  - sessions: client-type and Node ID columns and filters (Node ID values come from
    `/api/app/brokers`), and the "Application client should be persistent" warning;
  - the home-page "Applications" count;
  - resource usage, which lists integration-executor rows with a delete action;
  - integrations: "check connection" (goes through the executor), the events "Server" column,
    executor-node hints.
- `ClientType.APPLICATION` appears 17 times in 11 files, plus 5 places that iterate over all
  client types.

## 11. Packaging and runtime

- Images: `msa/tbmq` builds `thingsboard/tbmq` (single node, installs itself), `msa/mqtt-broker`
  builds `tbmq-node`, `msa/integration/executor` builds `tbmq-integration-executor`. All three
  install the `.deb` from `packaging/java/build.gradle` (which also builds an rpm; Windows
  packaging is a separate assembly).
- Base image: `thingsboard/openjdk25:trixie-slim`, a full JDK 25.0.4.1, 436 MB.
- Sizes (amd64): `tbmq` 778 MB (2.4.0) / 691 MB (2.3.0); `tbmq-node` 934 / 843 MB;
  `tbmq-integration-executor` 664 / 580 MB. The fat jar is about 149 MB.
- The two-stage Dockerfile spec on `origin/chore/docker-image-size-optimization`
  (`docs/superpowers/specs/2026-09-08-docker-image-size-optimization-design.md`) aims at about
  597 MB and leaves the 444 MB Debian + JDK base untouched on purpose.
- arm64 already ships: the buildx profile `push-docker-amd-arm-images` builds amd64 and arm64.
- User-facing install: `msa/tbmq/configs` has a docker-compose file with postgres:17, kafka 4.0.0,
  valkey 8.0 and the executor, plus install and upgrade scripts for shell and PowerShell.
  `docker/` (HAProxy cluster) and `k8s/` cover clusters. The tbmq.io docs cover Docker (2 GB RAM
  minimum), clusters and building from source; there is no deb/rpm guide.
- `api/openapi.json` is checked for drift at build time.
- `TBMQReleaseService` polls api.github.com every 3 hours and stores the answer as a retained
  message on `$SYS/tbmq/latest/version/available`, which `/api/system/info` reads. An offline
  device logs errors here.
- Node drain: `/api/node/drain`, no UI, meant for load balancers.

## 12. Separate CE issues

Found along the way; outside Lite's timeline.

1. The Valkey DEVICE store's `UPDATE_PACKET_TYPE` script drops the message TTL, so messages in
   PUBREL state never expire (§2.1).
2. Leader election reads a registry whose entries never expire; a dead node that sorts first stops
   the cleanup jobs (§3).
3. `SECURITY_MQTT_BASIC_ENABLED` is set in the compose and k8s files but read nowhere.
4. `DeviceSessionCtxEntity` is a dead entity mapped to a table that does not exist.
5. The shipped Valkey cluster compose runs without AOF (`--appendonly no`), so persisted DEVICE
   messages do not survive a Valkey restart.

## 13. The May prototype

`~/projects/gsd/tbmq/lightweight` (branch `codebase-map`, HEAD dc36ff8ef), built 2026-04-03 to
2026-05-04 with the GSD workflow; planning in `~/projects/gsd/tbmq/.planning/`, review in
`lightweight/REVIEW_REPORT.md`.

- Standalone Maven module with no dependency on any TBMQ module: Spring Boot 3.5.3, Java 17, Netty
  4.1.122, rocksdbjni 9.7.4, Caffeine, BouncyCastle. 117 main files (10,012 lines), 39 test files
  (5,731 lines).
- Copied near-verbatim from TBMQ: the actor framework, `ConcurrentMapSubscriptionTrie`,
  `ConcurrentMapRetainMsgTrie`, PEM SSL credentials, the WebSocket frame handlers. Rewritten under
  TBMQ names with less coverage: `TopicAliasCtx`, `MqttReasonCodeResolver` (11 of 23 methods),
  `MqttPropertiesUtil`, `MqttSessionHandler`, `ClientSessionCtx`, and a 700-line `ClientActor`.
- Dispatch: one shared `LinkedBlockingQueue` (100k) with two consumer threads, which can reorder
  a publisher's messages; the publisher is acked before dispatch, and a full queue drops the
  message. No `autoRead` or writability back-pressure.
- Sessions: clean sessions only (`cleanSession=0` treated as 1, Session Expiry forced to 0). No
  persistent sessions or offline queues. RocksDB stores only credentials (bcrypt hash and regex
  ACL rules as JSON) and a schema version.
- No REST API or UI. Credentials can only be created in code (a seeded `tbmq`/`tbmq`). Metrics on
  an unauthenticated actuator.
- MQTT 5 gaps: No Local, Retain As Published, Retain Handling, AUTH, Maximum Packet Size, Will
  Delay, topic-alias substitution on outbound messages, flow-control buffering (Receive Maximum is
  enforced by dropping), MQTT 5 reason codes on PUBREL/PUBCOMP.
- Tests: 172 (0 failures, 3 skipped), 88 of them Paho v3/v5 clients against a live broker;
  `BrokerMetricsIT` and the soak test are not part of the default run.
- Image: 386 MB on `eclipse-temurin:17-jre-jammy`. Fat jar 116 MB, of which rocksdbjni is 67.7 MB
  (14 native libraries, including musl builds). Startup 5.2 s in Docker; heap 72 to 86 MB under a
  500-client soak. No RSS, arm64 or throughput measurements.
- Its pitfalls are summarized in Appendix C of the spec.

## 14. Second check (2026-11-09)

A review of the spec against the code. Summary and spec consequences are in the spec's §3.1.

QoS 2 inbound state:

- `MqttPublishHandler:120-127` puts the packet id into `AwaitingPubRelPacketsCtx` on arrival, in
  memory. `processPubRecResponse:272-300` marks it persisted once the copies are written.
  `GenericClientSessionCtxManagerImpl` saves the state only at disconnect and `@PreDestroy`. A crash
  between "copies stored" and "PUBREC sent" therefore lets a resent PUBLISH be stored again; the
  same holds for Lite, whatever it saves at PUBREC time.

Edition plumbing:

- `A/controller/AppController.java` serves `/cluster-info` (:65), `/kafka-topics` (:84, :95),
  `/config` (:150), `/brokers` (:164) and `/service/info` (:170, :182).
- Module dependencies (`pom.xml`): `common/cache` -> util, data; `dao` -> data, dao-api, stats,
  util, cache; `common/queue` -> data, util, stats. Neither `common/cache` nor `dao` sees
  `common/queue`.
- `A/exception/ThingsboardErrorResponseHandler.java:25` imports
  `org.hibernate.exception.ConstraintViolationException` (CE and PE).
- PE `origin/main` `application`: the same Kafka (3 files), Redis (2) and JPA (1) imports as CE,
  nothing PE-only. PE `dao`: 73 files with JPA imports against CE's 46.
- `integration/executor/.../DefaultClusterIntegrationService.java:57-58, 78, 121-219` uses
  `TopicPartition` and a rebalance listener from the queue interfaces. The executor depends on
  `queue`, `cluster-integration-api` and `netty-mqtt`.

Tests:

- `application/src/test/java/org/thingsboard/mqtt/broker/AbstractPubSubIntegrationTest.java`:
  `@ClassRule` `KafkaContainer` (:128) and Valkey `GenericContainer` (:131), an `ExternalResource`
  that sets Redis system properties (:137-154), and a `BeanPostProcessor` typed on
  `TbKafkaConsumerSettings`, `TbKafkaProducerSettings` and `TbKafkaAdminSettings` (:156-171).
  48 test classes extend it directly; `AbstractWebTest` (controller tests) extends it too.
  `IntegrationTestSuite` is a JUnit 4 `ClasspathSuite` over `*TestCase` classes.
- Perf-test scenarios (`tb-mqtt-perf-tests/src/main/conf/`): `simple`, `general` and `fan_in` use
  APPLICATION clients; only `fan_out` does not.

DEVICE store and delivery:

- `D/messages/DeviceMsgServiceImpl.java:83-85` (Lua): each message is stored with
  `SET ... EX msgExpiryInterval or defaultTtl`.
- `findPersistedMessages(clientId)` returns the whole queue as one list; called from
  `PersistedDeviceActorMessageProcessor:140` (shared-subscription queues) and `:326` (reconnect).

Shutdown:

- `thingsboard-mqtt-broker.yml`: `server.shutdown: graceful` (:20), Netty listener
  `shutdown_timeout` 5 s each (:106, :160, :192, :254), `actors.system.disconnect-wait-timeout-ms`
  15000 (:695), `spring.lifecycle.timeout-per-shutdown-phase` 1m (:863). `docker stop` sends
  SIGKILL after 10 s by default.

Sessions and auth:

- `A/actors/client/service/BrokerInitializer.java:154-178`: at startup, clean sessions stored for
  this node are cleaned up. Lite keeps clean sessions in memory only, so there is nothing to clean.
- `ClientSessionCleanUpServiceImpl.cleanUp`: fixes "ghost" sessions (connected in the store, no
  channel) only for the node's own service id, which Lite's fixed id keeps working.
- `A/service/auth/DefaultAuthorizationRoutingService.java:74-104`: if Basic, X.509, JWT and HTTP
  are all disabled, every client is let in (`AuthResponse.defaultAuthResponse()`).
- UI HTTPS exists: `server.ssl.enabled` (`SSL_ENABLED`, yml :40-42).
