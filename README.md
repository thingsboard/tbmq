![banner](https://github.com/user-attachments/assets/3584b592-33dd-4fb4-91d4-47b62b34806c)

<div align="center">

# Open-source, highly scalable, and fault-tolerant MQTT Broker.

</div>
<br>
<div align="center">
 
💡 [Get started](https://thingsboard.io/docs/mqtt-broker/getting-started/)&ensp;•&ensp;🌐 [Website](https://thingsboard.io/products/mqtt-broker/)&ensp;•&ensp;📚 [Documentation](https://thingsboard.io/docs/mqtt-broker/)&ensp;•&ensp;📄 [Architecture](https://thingsboard.io/docs/mqtt-broker/architecture/)&ensp;

</div>
TBMQ is an open-source MQTT message broker built for large-scale, production IoT deployments. A single TBMQ node can handle millions of concurrent client connections and millions of MQTT messages per second with low latency; in cluster mode, TBMQ scales horizontally to support massive IoT workloads.

## 🚀 Installation options
TBMQ offers flexible installation options tailored for both development and large-scale production environments:

* **Docker:** Install a standalone TBMQ instance quickly on Linux, Mac OS, or Windows using Docker Compose.
* **Kubernetes (K8s):** Deploy TBMQ in a highly available cluster mode using official Helm Charts for various cloud platforms (AWS EKS, Azure AKS, GCP GKE).
* **Building from sources:** Compile and run TBMQ directly from the source code.

➡️ **[View Installation Guides ➜](https://thingsboard.io/docs/mqtt-broker/install/installation-options/)**

## 💡 Getting started with TBMQ

Check out our [Getting Started guide](https://thingsboard.io/docs/mqtt-broker/getting-started/) to learn the basics of TBMQ. In minutes, you will learn to:

* **Connect** MQTT clients to TBMQ.
* **Publish** messages.
* **Subscribe** to topics.
* **Configure** authentication and authorization.
* **Monitor** sessions and subscriptions via the Web UI.

<div width="100%">
 <img width="49%" src="./img/tbmq-home.png"/>
 <img width="49%" src="./img/tbmq-sessions.png"/>
</div>

## 💊 Common IoT usage scenarios for TBMQ

At ThingsBoard, we've gained a lot of experience in building scalable IoT applications, which has helped us identify three main scenarios for MQTT-based solutions. 

### 1. Fan-in (telemetry ingestion)
Numerous devices generate a large volume of messages that are consumed by specific applications. Normally, a few applications are set up to handle these lots of incoming data. It must be ensured that they do not miss any single message.
<p align="center">
  <img src="https://github.com/user-attachments/assets/bbc92bcf-c09b-4141-b71d-d5e469ceeec2" 
       alt="Diagram showing fan-in communication pattern" 
       width="600">
</p>

### 2. Fan-out (broadcast messaging)
Numerous devices subscribing to specific updates or notifications that must be delivered. This leads to a few incoming requests that cause a high volume of outgoing data.
<p align="center">
  <img src="https://github.com/user-attachments/assets/85fe89da-ae81-4bef-8d50-c0c21e60b60f" 
       alt="Diagram showing fan-out communication pattern" 
       width="600">
</p>

### 3. Point-to-point (command & control)
Messages are routed between a single publisher and a specific subscriber through uniquely defined topics. Pattern that is primarily used for one-to-one communication. Ideal for private messaging or command-based interactions.
<p align="center">
  <img src="https://github.com/user-attachments/assets/88ce216d-891a-4232-9b4a-8590ded2cdfc" 
       alt="Diagram showing p2p communication pattern" 
       width="600"
       style="margin-left: 10px;">
</p>

Acknowledging these scenarios, we intentionally designed TBMQ to be exceptionally well-suited for all three.

## ✨ Features

<table>
  <tr>
    <td width="50%" valign="top">
      <br>
      <div align="center">
        <img src="https://placehold.co/378x200/EFEFEF/222222?text=Extreme+Scalability" alt="Extreme Scalability" width="378" />
        <h3>Extreme Scalability & <br> Performance</h3>
      </div>
      <div align="center">
        <p>Built for massive IoT loads. TBMQ can handle over **4M+ concurrent connections** on a single node and **100M+** in cluster mode, delivering a throughput of **3M+ messages/sec** with consistently low latency.</p>
      </div>
      <br>
      <div align="center">
        <a href="https://thingsboard.io/products/mqtt-broker/">Read more ➜</a>
      </div>
      <br>
    </td>
    <td width="50%" valign="top">
      <br>
      <div align="center">
        <img src="https://placehold.co/378x200/EFEFEF/222222?text=Fault+Tolerance" alt="Fault Tolerance" width="378" />
        <h3>Fault Tolerance & <br> High Availability</h3>
      </div>
      <div align="center">
        <p>Ensure zero downtime with a masterless, horizontally scalable cluster architecture. TBMQ utilizes <b>Kafka</b> for persistent storage and <b>Redis</b> for caching, ensuring high data durability and no single point of failure.</p>
      </div>
      <br>
      <div align="center">
        <a href="https://thingsboard.io/docs/mqtt-broker/architecture/">Read more ➜</a>
      </div>
      <br>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <br>
      <div align="center">
        <img src="https://placehold.co/378x200/EFEFEF/222222?text=MQTT+5.0+%26+Security" alt="MQTT 5.0 Compliance" width="378" />
        <h3>Full MQTT 5.0 <br> & Security</h3>
      </div>
      <div align="center">
        <p>Full compliance with <b>MQTT v3.1, v3.1.1, and v5.0</b> specifications. Features include QoS 0/1/2, Retained Messages, and Shared Subscriptions. Secures data via TLS/SSL and supports Basic, X.509, and OAuth2 authentication.</p>
      </div>
      <br>
      <div align="center">
        <a href="https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/">Read more ➜</a>
      </div>
      <br>
    </td>
    <td width="50%" valign="top">
      <br>
      <div align="center">
        <img src="https://placehold.co/378x200/EFEFEF/222222?text=Seamless+Integration" alt="Seamless Integration" width="378" />
        <h3>Seamless Data <br> Integration</h3>
      </div>
      <div align="center">
        <p>Don't just route data—process it. Use the dedicated <b>Integration Executor</b> to forward MQTT streams to external systems like Kafka, HTTP endpoints, or other brokers without blocking the main processing loop.</p>
      </div>
      <br>
      <div align="center">
        <a href="https://thingsboard.io/docs/mqtt-broker/integration-architecture/">Read more ➜</a>
      </div>
      <br>
    </td>
  </tr>
</table>

## Supported features:

- All MQTT v3.x features
- All MQTT v5.0 features
- Multi-node cluster support
- X.509 certificate chain authentication support
- JWT authentication
- Access control (ACL) based on client ID, username, or X.509 certificate chain
- REST query support for clients’ sessions and subscriptions
- Rate limits of message processing
- Cluster and clients' metrics monitoring
- Unauthorized clients
- MQTT WebSocket client
- Integrations with external systems (HTTP, MQTT, Kafka)
- Kafka topics and consumer groups monitoring
- Proxy protocol
- Blocked clients
- MQTT channel backpressure support

## ⚙️ Cloud-Native Architecture

TBMQ is designed as a microservices-based solution (in cluster mode) or a monolithic application (in standalone mode) to fit any infrastructure requirement.

* **Persistence:** All messages are persisted in Kafka to ensure data is never lost, even during processing spikes.
* **State Management:** Client sessions and subscriptions are managed via Redis for sub-millisecond access.
* **Scalability:** Nodes can be added or removed dynamically without service interruption.

![TBMQ Sessions](https://img.thingsboard.io/mqtt-broker/architecture/tbmq-architecture.png)

<div align="center">
<a href="https://thingsboard.io/docs/mqtt-broker/architecture/"><b>Explore the Architecture ➜</b></a>
</div>

---

## 🫶 Support
To get support, please visit our **[GitHub issues page](https://github.com/thingsboard/tbmq/issues)** and check the [TBMQ FAQ](https://thingsboard.io/docs/mqtt-broker/faq/).

---

## 📄 Licenses
This project is released under **[Apache 2.0 License](./LICENSE)**.


