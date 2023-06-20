# TBMQ

TBMQ represents an open-source MQTT message broker renowned for its remarkable capacity to handle a staggering number of connected MQTT clients,
reaching up to **4M** clients, while proficiently processing a minimum of **200K messages per second** per node.
In the cluster mode, its capabilities are further enhanced.

TBMQ support MQTT v3.x and v5.0.

## Documentation

TBMQ documentation is hosted on [thingsboard.io](https://thingsboard.io/docs/mqtt-broker/).

## MQTT 5 supported features:

- User Properties
- Reason Codes for MQTT packets
- Session Clean Start
- New Data Type: UTF-8 String pairs
- Bi-directional DISCONNECT packets
- Using passwords without usernames
- Session expiry feature
- Shared subscriptions
- Subscription options
- Maximum Packet Size
- Will delay
- Server Keep-Alive
- Assigned ClientID
- Server reference

## MQTT 5 features in active development:

- AUTH packet & Enhanced Authentication
- Message expiry feature
- Topic aliases
- The payload format and content types
- Request / Response & Correlation Data
- Flow Control
- Subscription Identifiers

## Licenses

This project is released under [Apache 2.0 License](./LICENSE).
