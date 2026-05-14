# WS/WSS forward-header support for real client IP

**Status:** Design approved — implementation pending
**Author:** dlandiak
**Date:** 2026-05-14
**Related:** [GitHub issue #230](https://github.com/thingsboard/tbmq/issues/230)

## Context

TBMQ's WS (8084) and WSS (8085) MQTT listeners currently only derive the client IP from
either the raw TCP socket address or, when enabled, the HAProxy PROXY protocol frame.
HTTP forward headers (`X-Forwarded-For`, `X-Real-IP`) sent on the WebSocket upgrade
request are not inspected.

This is a problem for users who put TBMQ behind NGINX: NGINX does not support the
`proxy_protocol on;` directive on `http`/`location` blocks (only on `stream` blocks).
So a typical NGINX-fronted deployment can deliver the PROXY frame for plain MQTT/MQTTS
but cannot do so for WS/WSS — and TBMQ ends up logging the proxy's container/bridge
address (e.g. `172.18.0.1`) for every WebSocket session.

The goal of this change is to let operators opt in to honoring standard forward
headers on the WS/WSS listeners, so the real client IP shows up in TBMQ session
details when running behind a trusted reverse proxy.

## Scope

In:
- WS listener (port 8084 default)
- WSS listener (port 8085 default)
- `X-Forwarded-For` (leftmost token) and `X-Real-IP`

Out:
- RFC 7239 `Forwarded` header parsing
- Trusted-proxy CIDR allowlist
- Any change to TCP/SSL listener behavior (they have no HTTP layer)
- Any change to Spring's REST/HTTP `forward_headers_strategy`

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Opt-in via a simple per-listener boolean | Mirrors the existing `proxy_enabled` per-listener pattern. No CIDR allowlist — keep config small; document the trust requirement. |
| 2 | Honor `X-Forwarded-For` and `X-Real-IP` only | Covers NGINX, HAProxy, AWS ALB, GCP LB, Cloudflare. RFC 7239 `Forwarded` is rarely used in practice and adds parsing complexity. |
| 3 | proxy_protocol wins if both enabled | PROXY frame is a lower-layer, more authoritative signal. Decided at pipeline construction time — the forward-headers handler is simply not installed when proxy_protocol is on. |
| 4 | Per-listener only; no global flag | A global flag would only ever affect WS/WSS, which is misleading. Two explicit env vars: `MQTT_WS_FORWARD_HEADERS_ENABLED`, `MQTT_WSS_FORWARD_HEADERS_ENABLED`. |
| 5 | Missing/invalid headers fall back to socket address | Most permissive — a misconfigured proxy doesn't break connectivity, it just shows the proxy's IP. |

## Architecture

### New code

`org.thingsboard.mqtt.broker.server.ip.ForwardHeadersIpAddressHandler` —
a `ChannelInboundHandlerAdapter` that:

- On `FullHttpRequest`, reads `X-Forwarded-For` (leftmost trimmed token), falling back
  to `X-Real-IP`.
- If a valid IP parses (via `com.google.common.net.InetAddresses.forString`),
  overrides `MqttSessionHandler.ADDRESS` with `new InetSocketAddress(parsedIp, 0)`.
  Port is set to `0` — the real client port is not carried in either header, and
  this matches what most reverse proxies expose.
- Always calls `ctx.fireChannelRead(msg)`.
- Removes itself from the pipeline **only after processing a `FullHttpRequest`**
  (regardless of whether a usable header was found). One-shot per upgrade,
  matching the spirit of `ProxyIpAddressHandler`, which removes itself only
  after consuming an `HAProxyMessage`.
- For any non-`FullHttpRequest` message (shouldn't occur in normal flow after
  `HttpObjectAggregator`, but defensive), just forwards and stays in place.

### Changes to existing code

- `AbstractMqttChannelInitializer` — add a new method
  `protected Boolean isListenerForwardHeadersEnabled()` defaulting to `false`.
- `AbstractMqttWsChannelInitializer.constructWsPipeline` — after
  `HttpObjectAggregator`, conditionally insert `ForwardHeadersIpAddressHandler`
  when `isListenerForwardHeadersEnabled()` is true **and**
  `isProxyProtocolEnabled()` is false. Order matters — the handler must see
  the aggregated `FullHttpRequest`, not raw `HttpRequest`/`HttpContent` parts.
- `MqttWsChannelInitializer` / `MqttWssChannelInitializer` — override
  `isListenerForwardHeadersEnabled()` to return value from their respective
  context.
- `MqttWsServerContext` / `MqttWssServerContext` — new field bound via
  `@Value("${listener.ws.forward_headers_enabled:false}")` /
  `@Value("${listener.wss.forward_headers_enabled:false}")` with env overrides
  `MQTT_WS_FORWARD_HEADERS_ENABLED` / `MQTT_WSS_FORWARD_HEADERS_ENABLED`.
- `thingsboard-mqtt-broker.yml` — two new keys under `listener.ws` and
  `listener.wss`, documented in the existing comment style. Each comment notes:
  (a) what it does, (b) that it only takes effect when proxy_protocol is off,
  (c) the security caveat about trusted proxies.

### Data flow

```
TCP accept
  └─ IpAddressHandler.accept()                ADDRESS = proxy socket addr
  └─ HttpServerCodec
  └─ HttpObjectAggregator                     emits FullHttpRequest
  └─ ForwardHeadersIpAddressHandler           [only when forward_headers=true AND proxy=false]
       ├─ XFF leftmost → InetAddresses.forString() → set ADDRESS = real client IP
       ├─ else X-Real-IP → set ADDRESS
       ├─ else: leave ADDRESS unchanged (still socket addr)
       ├─ fireChannelRead(request)
       └─ pipeline.remove(this)
  └─ WebSocketServerProtocolHandler           upgrade
  └─ Ws*FrameHandler / Encoder / MqttDecoder / MqttEncoder
  └─ MqttSessionHandler                       reads ADDRESS
```

## Error handling

- `X-Forwarded-For: ""` after trim → fall through to X-Real-IP.
- Garbage values (`unknown`, malformed IPs, IPv6 with zone id like `fe80::1%eth0`)
  → `InetAddresses.forString` throws → fall through / fall back.
- IPv6 in brackets `[2001:db8::1]` → strip a single leading `[` and trailing `]`
  before parsing.
- We never close the channel from this handler. Worst case: wrong address logged.
- Logging:
  - `TRACE` on every parse attempt (matches `IpAddressHandler` / `ProxyIpAddressHandler`).
  - `DEBUG` when a header was present but unparseable (operator troubleshooting).
  - No `WARN` — a misconfigured proxy must not flood logs.

## Security caveat (documented, not enforced)

Enabling `forward_headers_enabled` while TBMQ is reachable directly by clients
(no trusted proxy in front) allows any client to spoof its source IP via the
upgrade headers. The yaml comments and any documentation MUST state explicitly:

> Only enable when TBMQ is behind a trusted reverse proxy that overwrites these
> headers before forwarding the request.

## Testing

### Unit tests — `ForwardHeadersIpAddressHandlerTest` (Netty `EmbeddedChannel`)

| # | Scenario | Expected |
|---|---|---|
| 1 | `X-Forwarded-For: 203.0.113.7` | ADDRESS = `203.0.113.7:0`, handler removed |
| 2 | `X-Forwarded-For: 203.0.113.7, 10.0.0.5, 10.0.0.1` | ADDRESS = `203.0.113.7:0` (leftmost) |
| 3 | Only `X-Real-IP: 198.51.100.9` | ADDRESS = `198.51.100.9:0` |
| 4 | Both XFF and X-Real-IP | XFF wins |
| 5 | `X-Forwarded-For: ` (empty) and no X-Real-IP | ADDRESS unchanged |
| 6 | `X-Forwarded-For: unknown` | unchanged |
| 7 | `X-Forwarded-For: 2001:db8::1` | ADDRESS = `[2001:db8::1]:0` |
| 8 | Malformed IP `not-an-ip` | unchanged |
| 9 | No headers at all | unchanged, handler removed |
| 10 | Non-`FullHttpRequest` first message (defensive) | forwarded, handler retained |

Each case pre-sets `ADDRESS` to a sentinel and asserts override vs. unchanged.

### Integration tests — `MqttWsForwardHeadersIntegrationTest`

Mirrors the existing WS integration tests:
- TBMQ context booted with `LISTENER_WS_ENABLED=true`,
  `MQTT_WS_FORWARD_HEADERS_ENABLED=true`.
- A raw Netty client performs the WS upgrade with `X-Forwarded-For: 203.0.113.7`,
  then sends an MQTT CONNECT.
- Assert the session's reported client address is `203.0.113.7` (via session info
  API or by capturing the address attribute on `MqttSessionHandler`).
- Regression guard: same setup with the flag off → session shows loopback.

### Pipeline-wiring tests

- Handler is **present** when `forward_headers=true` AND `proxy=false`.
- Handler is **absent** when `proxy=true` (proxy_protocol takes precedence).
- Handler is **absent** when `forward_headers=false`.

No changes needed to `MqttSessionHandler` tests — its contract with `ADDRESS`
is unchanged.

## Out of scope / future work

- Trusted-proxy CIDR allowlist — would be a natural follow-up if real-world
  deployments need mixed direct/proxied access on the same listener.
- RFC 7239 `Forwarded` header support.
- Honoring forward headers on the REST API (`forward_headers_strategy` already
  covers that path).
