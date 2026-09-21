# TBMQ REST Publish SDK

Standalone Java client for `POST /api/mqtt/publish`. It supports Java 8 or newer and only depends on Jackson.
The API currently requires a TBMQ system-administrator JWT. Prefer a dedicated administrator account when using credential-based login.

```xml
<dependency>
  <groupId>org.thingsboard.mqtt-broker</groupId>
  <artifactId>rest-publish-sdk</artifactId>
  <version>2.4.1-SNAPSHOT</version>
</dependency>
```

Use a system administrator JWT when the application manages tokens itself:

```java
TbmqRestPublishClient client = TbmqRestPublishClient.builder("https://tbmq.example.com")
        .accessToken(System.getenv("TBMQ_TOKEN"))
        .build();

RestPublishResult result = client.publish(RestPublishRequest.text("devices/a/commands", "reboot")
        .qos(1)
        .build());
```

Or let the SDK log in and repeat the login once when an access token expires:

```java
TbmqRestPublishClient client = TbmqRestPublishClient.builder("https://tbmq.example.com")
        .credentials(System.getenv("TBMQ_USERNAME"), System.getenv("TBMQ_PASSWORD"))
        .build();
```

Binary and JSON payloads are explicit:

```java
client.publish(RestPublishRequest.bytes("firmware/chunk", bytes).qos(1).build());

RestPublishProperties properties = new RestPublishProperties()
        .contentType("application/json")
        .messageExpiryInterval(60)
        .userProperty("source", "backend");
client.publish(RestPublishRequest.json("devices/a/config", jsonNode)
        .retain(true).properties(properties).build());
```

HTTP 200 and 202 return `RestPublishResult`. Other responses throw `TbmqRestPublishException`, which exposes the HTTP status and raw response body. Do not automatically retry HTTP 503: the broker may have accepted the message before the acknowledgement timed out, so retrying can publish a duplicate.
