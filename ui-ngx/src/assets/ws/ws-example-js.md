### Installation

#### Using NPM or Yarn
To install MQTT.js in your project using npm or yarn, open the terminal and navigate to your project root.
Then, run the above command. This command will install the MQTT.js package and also add it to your package.json file as a dependency.

```bash
npm install mqtt --save
{:copy-code}
```

```bash
yarn add mqtt
{:copy-code}
```

#### Using Content Delivery Network
If you prefer to link directly to a CDN-hosted version of MQTT.js, you can include the above script tag in your HTML file.
When the browser loads your web application, it will download and execute the MQTT.js library from the URL provided in the script tag.

Add this to your HTML:

```bash
<script src="https://unpkg.com/mqtt/dist/mqtt.min.js"></script>
{:copy-code}
```

The bundled package of MQTT.js can be used directrly by loading by link https://unpkg.com/mqtt/dist/mqtt.min.js.

#### Installing globally

Sometimes, particularly for command line tools or development server usage, you might want to install packages globally.
This can be done by adding '-g' flag to the install command with npm.
This installs the package globally on your machine, making it accessible anywhere on your system.
Note that global installations should be used judiciously as they can lead to version conflicts.

For global installation use **-g** flag:

```bash
npm install -g mqtt
{:copy-code}
```

Global installation allows to use a commands that facilitate interaction with a broker.

For example, you can subscribe to a topic using one terminal window. The following command subscribes to a topic named 'hello' on a specified broker:
```bash
mqtt sub -t 'hello' -h 'test.mosquitto.org' -v
{:copy-code}
```

In another terminal window, you can publish a message to the same topic on the same broker:
```bash
mqtt pub -t 'hello' -h 'test.mosquitto.org' -m 'from MQTT.js'
{:copy-code}
```

The -v flag displays verbose outputs, the -t flag specifies the topic, -h is used to set the hostname of the broker, and -m is for the message to publish.

### A Basic MQTT.js demonstration
This simple scenario illustrates utilizing MQTT.js for interfacing with TBMQ using Javascript.
We'll delve into connecting to the service, subscribing to topics, and exchanging messages, all through the MQTT.js library.

#### Connect client

In MQTT.js library the **mqtt.connect(url, options)** function is used to establish a connection to an MQTT broker:
* **url** parameter specifies the URL of the MQTT broker you want to connect to. This could be in various forms including 'mqtt://localhost', 'mqtts://localhost' (MQTT over SSL), 'tcp://localhost:123', 'tls://localhost:123' (MQTT over SSL), 'wx://localhost:123' (MQTT over Websockets), or 'wxs://localhost:123' (MQTT over Websockets over SSL).
* **options** parameter is an optional object that can contain various properties to customize the connection. The properties can include clientId, username, password, keepalive, clean, reconnectPeriod, connectTimeout etc.

Read more https://github.com/mqttjs/MQTT.js, examples https://github.com/mqttjs/mqtt-packet#connect


```bash
// import mqtt from "mqtt"; // ES6 Modules import

const mqtt = require('mqtt'); // CommonJS (Require)
const url = 'ws://localhost:8084/mqtt'; // default TBMQ ws port 8084

const options = {
  "clientId": "tbmq_websockets_client_id", // Client ID
  "username": "tbmq_websockets_username", // Client username
  "password": null, // Client authentication password
  "clean": true, // If true, the broker should clean all client sessions whenever the client disconnects
  "keepalive": 60, // The keepalive period in seconds between the client and broker
  "connectTimeout": 30000, // The duration in milliseconds before deciding that the broker is unresponsive
  "reconnectPeriod": 1000, // Reconnection interval duration in milliseconds
  "protocolVersion": 5, // MQTT protocol version, or 4 (3.1.1), 3(3.1)
  "protocolId": "MQTT", // Or 'MQIsdp' in MQTT 3.1
  "properties": {
      "sessionExpiryInterval": 0, // Session expiry interval in seconds
      "receiveMaximum": 65535, // Maximum packets that can be “in-flight”. MQTT.js will throttle the messages sent to the broker accordingly
      "maximumPacketSize": 65535, // Maximum MQTT packet size that client should accept
      "topicAliasMaximum": 10, // Maximum Identifier for Topic Alias to be used
      "requestResponseInformation": true // Enables response information from the broker
  },
  "will": {
      "topic": "sensors/lastwill_topic", // Topic on which 'will' will be delivered
      "qos": 1, // QoS or quality of service for the 'will' message
      "retain": true, // If true, the broker should hold the message and send it out to any subscribing clients
      "payload": "Will message", // Payload of the 'will' message
      "properties": {
          "contentType": "content_type", // Content type of 'will' message
          "responseTopic": "sensors/response_topic", // The topic that the response should be published on
          "willDelayInterval": 0, / The delay in seconds before the server should publish the 'will' message after a disconnection
          "messageExpiryInterval": 0, // Interval in seconds after which the 'will' message should be deleted; 0 means no expiry
          "payloadFormatIndicator": false, // If payload is UTF-8 Encoded Character Data or not
          "userProperties": { // An object pair of user properties for the 'will' message
              "keyA": "Value A",
              "keyB": "Value B"
          }
      }
  }
};

const client = mqtt.connect(url, options); // create a client

{:copy-code}
```

#### Subscribe to a topic

```bash
client.on('connect', function () {
  console.log('Client connected!');
  const topicObject = {
      "sensors/#": {
          "qos": 1,
          "nl": true,
          "rap": true,
          "rh": 0
      }
  }
  client.subscribe(topicObject);
});
{:copy-code}
```

#### Publish a message

```bash
const options = {
  "qos": 1,
  "retain": false,
  "properties": {
  "messageExpiryInterval": 60,
  "userProperties": {
  "propA": "a",
  "propB": "b"
  },
  "contentType": "publish_msg_content_type",
  "responseTopic": ""
}
client.publish('sensors/temperature', 'Hello World', options);
{:copy-code}
```

#### Handle received messages

```bash
client.on('message', function (topic, message) {
  console.log(`Received Message: ${message.toString()} On topic: ${topic}`); // console topic and message
  client.end(); // end client session
});
{:copy-code}
```

#### Handle client events

```bash
client.on('error', error => {
  console.log('Connection error', error);
});
client.on('reconnect', () => {
  console.log('Reconnecting...');
});
client.on('close', () => {
  console.log('Closing...');
});
client.on('disconnect', () => {
  console.log('Disconnecting...');
});
client.on('offline', () => {
  console.log('Offline...');
});
client.on('end', () => {
  console.log('End...');
});
client.on('packetsend', (packet) => {
  console.log('Packet Send...', packet);
});
client.on('packetreceive', (packet) => {
  console.log('Packet Receive...', packet);
});

{:copy-code}
```

In MQTT.js, client events are significant happenings or actions that occur during the lifecycle of the MQTT client-broker communication. They provide valuable insight into the state and behavior of the MQTT client, helping to manage the connection, troubleshoot issues, and implement logic based on the state of the client.
These events can range from successful client-broker connection (connect), initiation of the reconnection process (reconnect), to the receipt of messages from the broker (message). Subscribing to these events can be done using client.on(eventName, callback), where eventName is a string with the name of the event and callback is a function that will be triggered when the event occurs.
With these events, developers can build robust applications aware of the connection and communication status, handle errors and disconnections more gracefully and provide a more stable and reliable service.

* **connect**: This event is emitted when the MQTT client successfully connects to the broker. It receives a CONNACK packet as an argument. 
* **reconnect**: If the connection is closed and the options.reconnectPeriod option is greater than zero, this event will be emitted when starting the reconnect process. 
* **close**: This event is emitted when the MQTT connection is closed. This can be a result of calling end() or because the connection is dropped. 
* **offline**: This event is emitted when the client goes offline, i.e. it cannot connect to the broker. 
* **error**: If there is an error at the MQTT protocol level, or any network-level errors occur, this event is emitted. 
* **end**: This event is emitted when mqtt.Client#end() is called and the resulting FIN packet has been received.
* **message**: This event is emitted when a message is received, with parameters topic and message. message is a string containing the payload, and topic is a string with the topic name. 
* **packetsend**: This event is emitted just after a packet is written out to the connected socket. 
* **packetreceive**: This event is emitted just after a packet is read from the connected socket.
