# TBMQ Java SDK

Unified Java client for all TBMQ REST APIs. It supports Java 8 or newer and only depends on Jackson.
It provides a generic client for the complete REST surface and a strongly typed facade for MQTT publishing.

```xml
<dependency>
  <groupId>org.thingsboard.mqtt-broker</groupId>
  <artifactId>tbmq-java-sdk</artifactId>
  <version>2.4.1-SNAPSHOT</version>
</dependency>
```

Create one shared client with an access token or administrator credentials:

```java
TbmqClient client = TbmqClient.builder("https://tbmq.example.com")
        .accessToken(System.getenv("TBMQ_TOKEN"))
        .build();

JsonNode info = client.get("/api/system/info", JsonNode.class);
```

Every endpoint is available through `TbmqApiRequest`, including query parameters, headers,
arbitrary request DTOs, generic response types and asynchronous execution:

```java
TbmqApiRequest request = TbmqApiRequest.builder(TbmqHttpMethod.GET, "/api/client-session")
        .query("pageSize", 20)
        .query("page", 0)
        .build();

TbmqApiResponse<JsonNode> response = client.execute(request, JsonNode.class);
```

For parameterized responses, use Jackson's `TypeReference` overload:

```java
TbmqApiResponse<List<MyDto>> response = client.execute(request,
        new TypeReference<List<MyDto>>() {});
```

Common management domains also have discoverable, strongly typed clients:

```java
TbmqPage<MqttClientCredentials> credentials =
        client.credentials().list(20, 0, "gateway");

ClientSession session = client.sessions().get("device-a");
client.sessions().disconnect(session.getClientId(), session.getSessionId());

TbmqPage<Integration> integrations = client.integrations().list(20, 0, null);
```

The typed clients currently cover client credentials, MQTT authentication providers, client sessions,
subscriptions, retained messages, integrations and REST MQTT publishing. Extensible
configuration fields remain `JsonNode` so a newer broker can add fields without breaking an older SDK.

Asynchronous methods require an application-owned executor, preventing blocking HTTP calls from occupying
the JVM common pool:

```java
ExecutorService sdkExecutor = Executors.newFixedThreadPool(4);
TbmqClient client = TbmqClient.builder("https://tbmq.example.com")
        .accessToken(System.getenv("TBMQ_TOKEN"))
        .executor(sdkExecutor)
        .build();
```

Use the typed MQTT publish facade from the same client:

```java
TbmqRestPublishClient publisher = client.mqttPublish();
RestPublishResult result = publisher.publish(RestPublishRequest.text("devices/a/commands", "reboot")
        .qos(1)
        .build());
```

Or let the SDK log in and repeat the request once when an access token expires:

```java
TbmqClient client = TbmqClient.builder("https://tbmq.example.com")
        .credentials(System.getenv("TBMQ_USERNAME"), System.getenv("TBMQ_PASSWORD"))
        .build();
```

Binary and JSON MQTT payloads are explicit:

```java
TbmqRestPublishClient publisher = client.mqttPublish();
publisher.publish(RestPublishRequest.bytes("firmware/chunk", bytes).qos(1).build());

RestPublishProperties properties = new RestPublishProperties()
        .contentType("application/json")
        .messageExpiryInterval(60)
        .userProperty("source", "backend");
publisher.publish(RestPublishRequest.json("devices/a/config", jsonNode)
        .retain(true).properties(properties).build());
```

All 2xx responses are successful. Other responses throw `TbmqApiException`, which exposes the HTTP status
and raw response body. The publish facade converts it to `TbmqRestPublishException` for backward compatibility.
Only a 401 caused by an expired credential-based session is repeated once; other failures are never retried automatically.
