# Schema 1 store fixture

`db/` is a store of schema version 1, written on 2026-11-23 by the code at commit `e355e60cd` with a throwaway
generator kept outside this repository. The generator started the broker against an empty store directory, built the
state below through real MQTT clients and broker services, ran the session cleanup once (both sessions survived),
closed the application gracefully, compacted every column family into SST files with a raw RocksDB handle and
deleted the info logs.

## Expiry rule

The store keeps absolute expiry times, so nothing in a fixture may expire within decades. This fixture, and every
later schema fixture, is written with:

- `MQTT_PERSISTENT_SESSION_DEVICE_PERSISTED_MESSAGES_TTL=2000000000`
- `MQTT_CLIENT_SESSION_EXPIRY_TTL=2000000000`
- `MQTT_CLIENT_SESSION_EXPIRY_MAX_EXPIRY_INTERVAL=2000000000`

and every Session Expiry Interval and Message Expiry Interval set to 1576800000 s (50 years). In this fixture the
earliest DEVICE entry TTL time is 2090-04-09, the Message Expiry time of `fx/r/2` and of the `b2` entries is
2076-11-10.

The stored times the upgrade tests pin, in epoch milliseconds:

| Entry | Stored time |
|---|---|
| `fx/r/1` created time | 1795428998713 |
| `fx/r/2` created time | 1795428998716 (Message Expiry time 3372228998716) |
| `b2` created time | 1795428997477 |
| `b2` `msgExpiresAtMs`, both queues | 3372228997477 |

## meta

| Key | Value |
|---|---|
| `schema_version` | 1 (4-byte big-endian int) |
| `product` | `LITE` |
| `install_id` | `f61b7502-608d-4b6a-a5c0-3ad396336e04` |

## client_session

| Key | Content |
|---|---|
| `fx-dev-1` | DEVICE, MQTT 5, Clean Start 0, Session Expiry Interval 1576800000, disconnected, service id `tbmq-lite` |
| `fx-dev-2` | DEVICE, MQTT 3.1.1, clean session 0 (no Session Expiry Interval), disconnected, service id `tbmq-lite` |

## client_subscriptions

| Key | Subscriptions |
|---|---|
| `fx-dev-1` | `fx/a/#` QoS 1 with subscription identifier 7; `fx/b` QoS 2 |
| `fx-dev-2` | `fx/a/#` QoS 1; `fx/b` QoS 2; `fx/s` QoS 1 in shared group `fx-g` (`$share/fx-g/fx/s`) |

## retained_msg

| Key | Content |
|---|---|
| `fx/r/1` | payload `r1`, QoS 1, no Message Expiry Interval |
| `fx/r/2` | payload `r2`, QoS 1, Message Expiry Interval 1576800000 |

## blocked_client

| Key | Content |
|---|---|
| `clientId:fx-blocked` | type CLIENT_ID, value `fx-blocked`, expiration time 0 |
| `regex:fx-bad-.*:byID` | type REGEX, value `fx-bad-.*`, match target BY_CLIENT_ID, expiration time 0 |

## client_session_attr

Written by the connect path (the clients authenticated with basic credentials).

| Key | Value |
|---|---|
| `clientMqttVersion\0fx-dev-1` | `MQTT_5` |
| `clientMqttVersion\0fx-dev-2` | `MQTT_3_1_1` |
| `clientSessionCredentials\0fx-dev-1` | `fx-dev-1-credentials` |
| `clientSessionCredentials\0fx-dev-2` | `fx-dev-2-credentials` |

(`\0` is one 0x00 byte between the cache name and the client id. Each stored value starts with the one-byte value tag
`0x01` before the text shown.)

## device_msg and device_msg_meta

Published while both clients were offline, in this order: `a1`, `a2`, `a3` QoS 1 to `fx/a/1`; `b1` QoS 2 to `fx/b`;
`a4`, `a5` QoS 1 to `fx/a/1`; `b2` QoS 2 to `fx/b` with Message Expiry Interval 1576800000 and user property `k=v`.
Then `fx-dev-1` reconnected with Receive Maximum 3, received `a1`, `a2`, `a3`, acknowledged `a1`, received `b1`,
sent PUBREC, received PUBREL, withheld PUBCOMP and disconnected.

`fx-dev-1` meta: first sequence 2, last sequence 7, last packet id 7, count 6, sent up to 4.

| Seq | Packet id | Type | State | Message |
|---|---|---|---|---|
| 2 | 2 | PUBLISH | sent, unacknowledged | `a2`, `fx/a/1`, QoS 1, subscription identifier 7 |
| 3 | 3 | PUBLISH | sent, unacknowledged | `a3`, `fx/a/1`, QoS 1, subscription identifier 7 |
| 4 | 4 | PUBREL | sent (awaiting PUBCOMP) | `b1`, `fx/b`, QoS 2 |
| 5 | 5 | PUBLISH | unsent | `a4`, `fx/a/1`, QoS 1, subscription identifier 7 |
| 6 | 6 | PUBLISH | unsent | `a5`, `fx/a/1`, QoS 1, subscription identifier 7 |
| 7 | 7 | PUBLISH | unsent | `b2`, `fx/b`, QoS 2, Message Expiry Interval 1576800000, user property `k=v` |

`fx-dev-2` meta: first sequence 1, last sequence 7, last packet id 7, count 7, sent up to 0.

| Seq | Packet id | Type | State | Message |
|---|---|---|---|---|
| 1 | 1 | PUBLISH | unsent | `a1`, `fx/a/1`, QoS 1 |
| 2 | 2 | PUBLISH | unsent | `a2`, `fx/a/1`, QoS 1 |
| 3 | 3 | PUBLISH | unsent | `a3`, `fx/a/1`, QoS 1 |
| 4 | 4 | PUBLISH | unsent | `b1`, `fx/b`, QoS 2 |
| 5 | 5 | PUBLISH | unsent | `a4`, `fx/a/1`, QoS 1 |
| 6 | 6 | PUBLISH | unsent | `a5`, `fx/a/1`, QoS 1 |
| 7 | 7 | PUBLISH | unsent | `b2`, `fx/b`, QoS 2, Message Expiry Interval 1576800000, user property `k=v` |

The shared group `fx-g` has no queue: nothing was published to `fx/s`.
