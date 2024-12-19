# TBMQ

[![Join the chat at https://gitter.im/tbmq/chat](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/tbmq/chat?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![TBMQ Builds Server Status](https://img.shields.io/teamcity/build/e/TBMQ_Build?label=TB%20builds%20server&server=https%3A%2F%2Fbuilds.thingsboard.io&logo=data:image/png;base64,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&labelColor=305680)](https://builds.thingsboard.io/viewType.html?buildTypeId=TBMQ_Build&guest=1)

TBMQ represents an open-source MQTT message broker with the capacity to handle **4M+** concurrent client
connections, supporting a minimum
of [3M messages per second throughput](https://thingsboard.io/docs/mqtt-broker/reference/3m-throughput-single-node-performance-test/)
per single cluster node with low latency delivery.
In the cluster mode, its capabilities are further enhanced, enabling it to support more than [100M
concurrently connected clients](https://thingsboard.io/docs/mqtt-broker/reference/100m-connections-performance-test/).

At ThingsBoard, we've gained a lot of experience in building scalable IoT applications, which has helped us identify
three main scenarios for MQTT-based solutions.

* In the first scenario, numerous devices generate a large volume of messages that are consumed by specific
  applications, resulting in a **fan-in** pattern.
  Normally, a few applications are set up to handle these lots of incoming data. It must be ensured that they do not
  miss any single message.

<img src="https://github.com/user-attachments/assets/c28d60ae-e31c-43d4-97cc-7ffb9b5cc2b3" alt="Diagram showing fan-in communication pattern" width="500">

* The second scenario involves numerous devices subscribing to specific updates or notifications that must be delivered.
  This leads to a few incoming requests that cause a high volume of outgoing data.
  This case is known as a **fan-out (broadcast)** pattern.

<img src="https://github.com/user-attachments/assets/e8656f1d-13a3-41dc-84d5-6028f5009c49" alt="Diagram showing fan-out communication pattern" width="500">

* The third scenario, **point-to-point (P2P)** communication, is a targeted messaging pattern, primarily used for
  one-to-one communication.
  Ideal for use cases such as private messaging or command-based interactions where messages are routed between a single
  publisher and a specific subscriber through uniquely defined topics.

<img src="https://github.com/user-attachments/assets/4b0f9ba2-7387-4e05-bb46-fe4ca1e191fd" alt="Diagram showing p2p communication pattern" width="500">

> In all scenarios, persistent clients with a Quality of Service (QoS) level set to 1 or 2 are often utilized to ensure
> reliable message delivery, even when they're temporarily offline due to restarts or upgrades.

Acknowledging these scenarios, we intentionally designed TBMQ to be exceptionally well-suited for all three.

Our design principles focused on ensuring the broker’s fault tolerance and high availability. Thus, we deliberately
avoided reliance on master or coordinated processes. We ensured the same functionality across all nodes within the
cluster.

We prioritized supporting distributed processing, allowing for effortless horizontal scalability as our operations grow.
We wanted our broker to support high-throughput and guarantee low-latency delivery of messages to clients. Ensuring data
durability and replication was crucial in our design. We aimed for a system where once the broker acknowledges receiving
a message, it remains safe and won’t be lost.

TBMQ provides full compatibility with both MQTT v3.x and v5.0 protocols.
Last but not least, it had been running in production for more than a year before being open-sourced.

[![TBMQ Home](./img/tbmq-home.png "TBMQ Home")](https://thingsboard.io/docs/mqtt-broker/user-guide/ui/monitoring/)

[![TBMQ Sessions](./img/tbmq-sessions.png "TBMQ Sessions")](https://thingsboard.io/docs/mqtt-broker/user-guide/ui/sessions/)

## Supported features:

- All MQTT v3.x features
- All MQTT v5.0 features
- Multi-node cluster support
- X.509 certificate chain authentication support
- Access control (ACL) based on client ID, username, or X.509 certificate chain
- REST query support for clients’ sessions and subscriptions
- Rate limits of message processing
- Cluster and clients' metrics monitoring
- Unauthorized clients
- MQTT WebSocket client
- Kafka topics and consumer groups monitoring

## Documentation

TBMQ documentation is hosted on [thingsboard.io](https://thingsboard.io/docs/mqtt-broker/).

## Getting Started

Connect clients and Publish your IoT data in minutes by following
this [guide](https://thingsboard.io/docs/mqtt-broker/getting-started/).

## Installation

Review different TBMQ installation options by
following [this link](https://thingsboard.io/docs/mqtt-broker/install/installation-options/).

## Licenses

This project is released under [Apache 2.0 License](./LICENSE).
