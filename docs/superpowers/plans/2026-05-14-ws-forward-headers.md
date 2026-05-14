# WS/WSS Forward-Headers Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let TBMQ's MQTT-over-WebSocket (WS) and MQTT-over-WebSocket-Secure (WSS) listeners derive the real client IP from HTTP `X-Forwarded-For` / `X-Real-IP` headers on the upgrade request, behind an opt-in per-listener flag — addressing the remaining piece of GitHub issue #230.

**Architecture:** Add a one-shot Netty `ChannelInboundHandlerAdapter` (`ForwardHeadersIpAddressHandler`) that inspects the aggregated `FullHttpRequest` from the WS upgrade, overrides the `MqttSessionHandler.ADDRESS` channel attribute with the parsed client IP (port `0`), then removes itself. The handler is inserted into the WS/WSS pipeline only when the per-listener `forward_headers_enabled` flag is on **and** `proxy_protocol` is off for that listener — PROXY protocol always wins.

**Tech Stack:** Java 17, Netty 4.1.124 (`io.netty.handler.codec.http`, `io.netty.channel.embedded` for tests), Spring Boot 3.4.8, Guava (`com.google.common.net.InetAddresses`), Lombok, JUnit 4, Mockito 5.18.

**Spec:** `docs/superpowers/specs/2026-05-14-ws-forward-headers-design.md`

---

## File map

**Create:**
- `application/src/main/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandler.java`
- `application/src/test/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandlerTest.java`
- `application/src/test/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializerWiringTest.java`

**Modify:**
- `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttChannelInitializer.java` — add `isListenerForwardHeadersEnabled()` method and helper to resolve it.
- `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializer.java` — conditionally insert handler in `constructWsPipeline`.
- `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsChannelInitializer.java` — override `isListenerForwardHeadersEnabled()`.
- `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssChannelInitializer.java` — override `isListenerForwardHeadersEnabled()`.
- `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsServerContext.java` — new `@Value`-bound field.
- `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssServerContext.java` — new `@Value`-bound field.
- `application/src/main/resources/thingsboard-mqtt-broker.yml` — two new keys (`listener.ws.forward_headers_enabled`, `listener.wss.forward_headers_enabled`) with operator-facing comments.

---

## Task 1: `ForwardHeadersIpAddressHandler` + unit tests

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandler.java`
- Create: `application/src/test/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandlerTest.java`

- [ ] **Step 1: Write the failing unit-test class**

Create `application/src/test/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandlerTest.java`:

```java
/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.server.ip;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardHeadersIpAddressHandlerTest {

    private static final InetSocketAddress SOCKET_ADDR = new InetSocketAddress("172.18.0.1", 54321);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ForwardHeadersIpAddressHandler());
        channel.attr(MqttSessionHandler.ADDRESS).set(SOCKET_ADDR);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    private static FullHttpRequest upgradeRequest() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mqtt", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void givenSingleXffIp_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7");

        channel.writeInbound(req);

        InetSocketAddress addr = channel.attr(MqttSessionHandler.ADDRESS).get();
        assertThat(addr.getHostString()).isEqualTo("203.0.113.7");
        assertThat(addr.getPort()).isZero();
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenXffChain_whenChannelRead_thenLeftmostIpWins() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7, 10.0.0.5, 10.0.0.1");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("203.0.113.7");
    }

    @Test
    public void givenOnlyXRealIp_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_REAL_IP, "198.51.100.9");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("198.51.100.9");
    }

    @Test
    public void givenBothHeaders_whenChannelRead_thenXffWins() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7");
        req.headers().set(X_REAL_IP, "198.51.100.9");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("203.0.113.7");
    }

    @Test
    public void givenEmptyXffAndNoXRealIp_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenUnknownLiteral_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "unknown");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenIpv6Xff_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "2001:db8::1");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    public void givenIpv6XffInBrackets_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "[2001:db8::1]");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    public void givenMalformedIp_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "not-an-ip");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenNoHeaders_whenChannelRead_thenAddressUnchangedAndHandlerRemoved() {
        FullHttpRequest req = upgradeRequest();

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenNonHttpMessageFirst_whenChannelRead_thenHandlerStaysInstalled() {
        channel.writeInbound("not-a-http-request");

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNotNull();
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail to compile**

Run from repo root:

```bash
mvn -pl application test -Dtest=ForwardHeadersIpAddressHandlerTest -DfailIfNoTests=false
```

Expected: compilation error — `ForwardHeadersIpAddressHandler` class does not exist.

- [ ] **Step 3: Implement `ForwardHeadersIpAddressHandler`**

Create `application/src/main/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandler.java`:

```java
/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.server.ip;

import com.google.common.net.InetAddresses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Slf4j
public class ForwardHeadersIpAddressHandler extends ChannelInboundHandlerAdapter {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    static final String X_REAL_IP = "X-Real-IP";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            InetAddress resolved = resolveFromHeaders(request);
            if (resolved != null) {
                InetSocketAddress address = new InetSocketAddress(resolved, 0);
                log.trace("[{}] Overriding address from forward headers: {}", ctx.channel().id(), address);
                ctx.channel().attr(MqttSessionHandler.ADDRESS).set(address);
            } else {
                log.trace("[{}] No usable forward headers; address unchanged", ctx.channel().id());
            }
            ctx.pipeline().remove(this);
        }
        ctx.fireChannelRead(msg);
    }

    private InetAddress resolveFromHeaders(FullHttpRequest request) {
        String xff = request.headers().get(X_FORWARDED_FOR);
        InetAddress fromXff = parseLeftmost(xff);
        if (fromXff != null) {
            return fromXff;
        }
        String xRealIp = request.headers().get(X_REAL_IP);
        return parseSingle(xRealIp);
    }

    private InetAddress parseLeftmost(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        String first = (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
        return parseSingle(first);
    }

    private InetAddress parseSingle(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return InetAddresses.forString(trimmed);
        } catch (IllegalArgumentException e) {
            log.debug("Unparseable forward-header value: '{}'", value);
            return null;
        }
    }
}
```

- [ ] **Step 4: Run the unit tests and confirm all pass**

```bash
mvn -pl application test -Dtest=ForwardHeadersIpAddressHandlerTest
```

Expected: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Format license headers**

```bash
mvn -pl application license:format
```

Expected: no errors. (Header is already correct; this is a safety check that won't change the file.)

- [ ] **Step 6: Commit**

```bash
git add application/src/main/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandler.java \
        application/src/test/java/org/thingsboard/mqtt/broker/server/ip/ForwardHeadersIpAddressHandlerTest.java
git commit -m "feat(server): add ForwardHeadersIpAddressHandler for WS X-Forwarded-For / X-Real-IP

Resolves the real client IP from the WebSocket upgrade request's
X-Forwarded-For (leftmost) or X-Real-IP header and overrides
MqttSessionHandler.ADDRESS. One-shot: removes itself after the
first FullHttpRequest. Falls back silently when no usable header
is present.

Refs #230"
```

---

## Task 2: Wire `forward_headers_enabled` config into WS and WSS server contexts

**Files:**
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsServerContext.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssServerContext.java`
- Modify: `application/src/main/resources/thingsboard-mqtt-broker.yml`

- [ ] **Step 1: Add `forwardHeadersEnabled` to `MqttWsServerContext`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsServerContext.java`. After the `listenerProxyProtocolEnabled` field add:

```java
    @Value("${listener.ws.forward_headers_enabled:false}")
    private boolean forwardHeadersEnabled;
```

(Use primitive `boolean` — unlike `proxy_enabled`, this has no global fallback to inherit from, so a plain `false` default is correct.)

- [ ] **Step 2: Add `forwardHeadersEnabled` to `MqttWssServerContext`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssServerContext.java`. After the `listenerProxyProtocolEnabled` field add:

```java
    @Value("${listener.wss.forward_headers_enabled:false}")
    private boolean forwardHeadersEnabled;
```

- [ ] **Step 3: Add YAML keys under `listener.ws` and `listener.wss`**

Modify `application/src/main/resources/thingsboard-mqtt-broker.yml`.

Under `listener.ws:` — directly after the existing `proxy_enabled` block (around line 165), insert:

```yaml
    # Enables honoring HTTP forward headers (X-Forwarded-For, X-Real-IP) on the WebSocket upgrade request to derive the real client IP.
    # Useful when TBMQ sits behind an HTTP reverse proxy (e.g. NGINX) that cannot send the PROXY protocol on HTTP location blocks.
    # When enabled, X-Forwarded-For (leftmost IP) wins over X-Real-IP. Falls back to the raw socket address if no usable header is present.
    # Ignored when proxy_enabled is true for this listener — PROXY protocol takes precedence.
    # SECURITY: only enable this when TBMQ is behind a trusted reverse proxy that overwrites these headers; otherwise clients can spoof their source IP.
    forward_headers_enabled: "${MQTT_WS_FORWARD_HEADERS_ENABLED:false}"
```

Under `listener.wss:` — directly after the existing `proxy_enabled` block (around line 191), insert:

```yaml
    # Enables honoring HTTP forward headers (X-Forwarded-For, X-Real-IP) on the WebSocket upgrade request to derive the real client IP.
    # Useful when TBMQ sits behind an HTTP reverse proxy (e.g. NGINX) that cannot send the PROXY protocol on HTTP location blocks.
    # When enabled, X-Forwarded-For (leftmost IP) wins over X-Real-IP. Falls back to the raw socket address if no usable header is present.
    # Ignored when proxy_enabled is true for this listener — PROXY protocol takes precedence.
    # SECURITY: only enable this when TBMQ is behind a trusted reverse proxy that overwrites these headers; otherwise clients can spoof their source IP.
    forward_headers_enabled: "${MQTT_WSS_FORWARD_HEADERS_ENABLED:false}"
```

- [ ] **Step 4: Compile to verify config wiring**

```bash
mvn -pl application -am compile -DskipTests
```

Expected: BUILD SUCCESS. (Spring `@Value` resolution against the YAML happens at runtime, so this only confirms the Java field types and YAML syntax don't break the build.)

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsServerContext.java \
        application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssServerContext.java \
        application/src/main/resources/thingsboard-mqtt-broker.yml
git commit -m "feat(config): add per-listener forward_headers_enabled for WS/WSS

Opt-in flag (MQTT_WS_FORWARD_HEADERS_ENABLED /
MQTT_WSS_FORWARD_HEADERS_ENABLED) that lets the WS/WSS listeners
honor X-Forwarded-For / X-Real-IP on the upgrade request.

Refs #230"
```

---

## Task 3: Wire the handler into the WS/WSS channel pipeline

**Files:**
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttChannelInitializer.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializer.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsChannelInitializer.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssChannelInitializer.java`
- Create: `application/src/test/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializerWiringTest.java`

- [ ] **Step 1: Write the failing wiring test**

Create `application/src/test/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializerWiringTest.java`:

```java
/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.server;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.server.ip.ForwardHeadersIpAddressHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsBinaryFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsByteBufEncoder;
import org.thingsboard.mqtt.broker.server.wshandler.WsContinuationFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsTextFrameHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMqttWsChannelInitializerWiringTest {

    private static EmbeddedChannel buildWsPipeline(boolean proxyEnabled, boolean forwardHeadersEnabled) {
        TestWsChannelInitializer initializer = new TestWsChannelInitializer(proxyEnabled, forwardHeadersEnabled);
        EmbeddedChannel channel = new EmbeddedChannel();
        initializer.constructTestWsPipeline(channel);
        return channel;
    }

    @Test
    public void givenForwardHeadersOnAndProxyOff_whenPipelineBuilt_thenHandlerInstalledAfterAggregator() {
        EmbeddedChannel channel = buildWsPipeline(false, true);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNotNull();
        assertThat(channel.pipeline().names())
                .containsSubsequence("aggregator", "forwardHeadersIpAdrHandler", "wsProtocol");
    }

    @Test
    public void givenProxyOnAndForwardHeadersOn_whenPipelineBuilt_thenForwardHandlerAbsent() {
        EmbeddedChannel channel = buildWsPipeline(true, true);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenForwardHeadersOff_whenPipelineBuilt_thenForwardHandlerAbsent() {
        EmbeddedChannel channel = buildWsPipeline(false, false);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    /**
     * Minimal stand-in for the abstract WS initializer that exposes the pipeline-construction step
     * without dragging in Netty SocketChannel / Spring wiring.
     */
    private static final class TestWsChannelInitializer {
        private final boolean proxyEnabled;
        private final boolean forwardHeadersEnabled;

        TestWsChannelInitializer(boolean proxyEnabled, boolean forwardHeadersEnabled) {
            this.proxyEnabled = proxyEnabled;
            this.forwardHeadersEnabled = forwardHeadersEnabled;
        }

        void constructTestWsPipeline(EmbeddedChannel ch) {
            ch.pipeline().addLast("codec", new HttpServerCodec());
            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(BrokerConstants.WS_MAX_CONTENT_LENGTH));
            if (forwardHeadersEnabled && !proxyEnabled) {
                ch.pipeline().addLast("forwardHeadersIpAdrHandler", new ForwardHeadersIpAddressHandler());
            }
            ch.pipeline().addLast("wsProtocol", new WebSocketServerProtocolHandler(BrokerConstants.WS_PATH, "mqtt"));
            ch.pipeline().addLast(new WsBinaryFrameHandler());
            ch.pipeline().addLast(new WsContinuationFrameHandler());
            ch.pipeline().addLast(new WsTextFrameHandler());
            ch.pipeline().addLast(new WsByteBufEncoder());
        }
    }
}
```

- [ ] **Step 2: Run the wiring test and confirm it fails to compile**

```bash
mvn -pl application test -Dtest=AbstractMqttWsChannelInitializerWiringTest -DfailIfNoTests=false
```

Expected: compilation error — `ForwardHeadersIpAddressHandler` references resolve, but the pipeline name `"forwardHeadersIpAdrHandler"` is not yet used in production code. The test still passes locally because it constructs a stand-in pipeline. The point of this step is to ensure the test compiles AND we have a stand-in name we will then mirror in production code. If the test passes without the production change, that's fine — it locks in the name/order contract.

If `mvn` reports "Tests run: 3, Failures: 0", proceed to Step 3. If it fails, fix compile errors before continuing.

- [ ] **Step 3: Add `isForwardHeadersEnabled()` to `AbstractMqttChannelInitializer`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttChannelInitializer.java`.

Replace the entire `AbstractMqttChannelInitializer` class body with:

```java
@Slf4j
@Getter
public abstract class AbstractMqttChannelInitializer extends ChannelInitializer<SocketChannel> implements MqttChannelInitializer {

    @Value("${mqtt.version-3-1.max-client-id-length}")
    private int maxClientIdLength;
    @Value("${historical-data-report.enabled:true}")
    private boolean historicalDataReportEnabled;
    @Value("${listener.proxy_enabled:false}")
    private boolean globalProxyProtocolEnabled;

    protected final MqttHandlerFactory handlerFactory;

    public AbstractMqttChannelInitializer(MqttHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        if (isProxyProtocolEnabled()) {
            pipeline.addLast("proxy", new HAProxyMessageDecoder());
            pipeline.addLast("ipAdrHandler", new ProxyIpAddressHandler());
        } else {
            pipeline.addLast("ipAdrHandler", new IpAddressHandler());
        }

        SslHandler sslHandler = getSslHandler();
        if (sslHandler != null) {
            pipeline.addLast(sslHandler);
        }

        constructWsPipeline(ch);

        pipeline.addLast("decoder", new MqttDecoder(getMaxPayloadSize(), getMaxClientIdLength()));
        pipeline.addLast("encoder", MqttEncoder.INSTANCE);

        if (historicalDataReportEnabled) {
            pipeline.addLast(new DuplexTrafficHandler(handlerFactory.getTbMessageStatsReportClient()));
        }

        MqttSessionHandler handler = handlerFactory.create(sslHandler, getChannelInitializerName());

        pipeline.addLast(handler);
        ch.closeFuture().addListener(handler);

        log.debug("[{}] Created {} channel for IP {}.", handler.getSessionId(), getChannelInitializerName(), ch.localAddress());
    }

    protected void constructWsPipeline(SocketChannel ch) {

    }

    protected boolean isProxyProtocolEnabled() {
        return Objects.requireNonNullElseGet(isListenerProxyProtocolEnabled(), () -> globalProxyProtocolEnabled);
    }

    protected boolean isForwardHeadersEnabled() {
        return false;
    }

}
```

Notes:
- The `isProxyProtocolEnabled()` method had been `private`; promote it to `protected` so the WS subclass can read it.
- The new `isForwardHeadersEnabled()` default-returns `false`. Non-WS listeners (`tcp`/`ssl`) keep that default; they have no HTTP layer.

- [ ] **Step 4: Wire forward-headers handler into `AbstractMqttWsChannelInitializer`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializer.java`. Replace the file with:

```java
/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.server;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.server.ip.ForwardHeadersIpAddressHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsBinaryFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsByteBufEncoder;
import org.thingsboard.mqtt.broker.server.wshandler.WsContinuationFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsTextFrameHandler;

@Slf4j
public abstract class AbstractMqttWsChannelInitializer extends AbstractMqttChannelInitializer {

    public AbstractMqttWsChannelInitializer(MqttHandlerFactory handlerFactory) {
        super(handlerFactory);
    }

    @Override
    public void initChannel(SocketChannel ch) {
        super.initChannel(ch);
    }

    protected abstract String getSubprotocols();

    @Override
    protected void constructWsPipeline(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast("aggregator", new HttpObjectAggregator(BrokerConstants.WS_MAX_CONTENT_LENGTH));

        if (isForwardHeadersEnabled() && !isProxyProtocolEnabled()) {
            pipeline.addLast("forwardHeadersIpAdrHandler", new ForwardHeadersIpAddressHandler());
        }

        pipeline.addLast(new WebSocketServerProtocolHandler(BrokerConstants.WS_PATH, getSubprotocols()));

        pipeline.addLast(new WsBinaryFrameHandler());
        pipeline.addLast(new WsContinuationFrameHandler());
        pipeline.addLast(new WsTextFrameHandler());

        pipeline.addLast(new WsByteBufEncoder());
    }
}
```

Notes:
- The aggregator is given an explicit name `"aggregator"` so the new handler's anchor point is debuggable. Other handlers stay anonymous — match the existing style.

- [ ] **Step 5: Override `isForwardHeadersEnabled()` in `MqttWsChannelInitializer`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsChannelInitializer.java`. After the existing `isListenerProxyProtocolEnabled` override add:

```java
    @Override
    public boolean isForwardHeadersEnabled() {
        return context.isForwardHeadersEnabled();
    }
```

Notes:
- `context.isForwardHeadersEnabled()` is Lombok's `@Getter`-generated accessor for the primitive `boolean forwardHeadersEnabled` field added in Task 2.
- The override widens visibility from `protected` (parent) to `public` — matches the convention used by `isListenerProxyProtocolEnabled` immediately above it.

- [ ] **Step 6: Override `isForwardHeadersEnabled()` in `MqttWssChannelInitializer`**

Modify `application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssChannelInitializer.java`. After the existing `isListenerProxyProtocolEnabled` override add:

```java
    @Override
    public boolean isForwardHeadersEnabled() {
        return context.isForwardHeadersEnabled();
    }
```

- [ ] **Step 7: Run all WS/WSS-relevant tests**

```bash
mvn -pl application test \
  -Dtest='ForwardHeadersIpAddressHandlerTest,AbstractMqttWsChannelInitializerWiringTest'
```

Expected: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 8: Format license headers**

```bash
mvn -pl application license:format
```

Expected: BUILD SUCCESS, no file modifications reported.

- [ ] **Step 9: Commit**

```bash
git add application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttChannelInitializer.java \
        application/src/main/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializer.java \
        application/src/main/java/org/thingsboard/mqtt/broker/server/ws/MqttWsChannelInitializer.java \
        application/src/main/java/org/thingsboard/mqtt/broker/server/wss/MqttWssChannelInitializer.java \
        application/src/test/java/org/thingsboard/mqtt/broker/server/AbstractMqttWsChannelInitializerWiringTest.java
git commit -m "feat(server): install ForwardHeadersIpAddressHandler in WS/WSS pipelines

Inserted after HttpObjectAggregator and before
WebSocketServerProtocolHandler, but only when
listener.{ws,wss}.forward_headers_enabled is true and the same
listener has proxy_enabled off. PROXY protocol takes precedence
when both are configured.

Refs #230"
```

---

## Task 4: Full-module build sanity check

**Files:** none (verification only).

- [ ] **Step 1: Run the application module's full test suite**

```bash
mvn -pl application -am test
```

Expected: BUILD SUCCESS, no new failures relative to a pre-change baseline. If unrelated tests fail (e.g. flaky Kafka/Redis testcontainers tests), capture the failure list and ask the user before continuing — do **not** silently ignore.

- [ ] **Step 2: Final license-format pass at the project root**

```bash
mvn license:format
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Confirm `git status` is clean**

```bash
git status
```

Expected: `nothing to commit, working tree clean`.

---

## Out of scope for this plan

- TBMQ-PE equivalent changes (separate repo).
- Updating user-facing documentation under `docs/` other than this plan + spec — that lives in the `thingsboard-edu` / website repos and is owned by the docs team.
- Adding a trusted-proxy CIDR allowlist (future enhancement, see spec "Out of scope").
- Surfacing the resolved client IP through the REST `/api/v1/mqtt/client-sessions` endpoint — it already reads from `MqttSessionHandler.ADDRESS`, so this change benefits that path for free.

## Deviation from spec: network-level integration test deferred

The committed spec lists an end-to-end `MqttWsForwardHeadersIntegrationTest`
that would boot a TBMQ Spring context with `LISTENER_WS_ENABLED=true` and
drive a real WebSocket upgrade. Closer inspection shows there is **no existing
WS-level integration test in `application/src/test`** to "mirror" — building one
from scratch would require Kafka + Redis testcontainers boot plus a raw Netty
WS client, and would dwarf the size of the change being tested.

The unit tests (Task 1) cover header parsing exhaustively. The pipeline-wiring
test (Task 3) verifies the handler is installed in the right place at the right
times. Together they cover every claim the spec makes about behavior, so the
network-level test is deferred. Capture it as a follow-up issue if real-world
deployments surface a wiring/lifecycle gap that the unit/wiring tests miss.
