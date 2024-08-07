### Javascript
In this guide, we present an illustrative example of using a JavaScript library [MQTT.js](https://github.com/mqttjs/MQTT.js) 
to establish a connection to the TBMQ broker, subscribe to a topic and publish a message.

##### Prerequisites
To start using the MQTT.js library in your project, you first need to install it using a package manager such as **npm** or **yarn**.

If you want to install package globally, please do not forget to add _‘-g’_ flag to the installation command with npm and _‘global’_ option for yarn.

```bash
npm install mqtt{:copy-code}
```

```bash
yarn add mqtt{:copy-code}
```

If you prefer to link directly to a CDN-hosted version of MQTT.js, you can include the above script tag in your HTML file.

```bash
<script src="https://unpkg.com/mqtt/dist/mqtt.min.js"></script>{:copy-code}
```

To confirm the successful installation of the library, please use the following command::

```bash
mqtt --version{:copy-code}
```

The output should be similar to the following result:

```bash
MQTT.js version: 5.9.1
```

##### Connect MQTT.js to the TBMQ
The code snippet below provides a demonstration on how to:
1. connect to a TBMQ broker using default pre-configured client credentials _'TBMQ WebSockets MQTT Credentials'_
2. subscribe for a topic 
3. publish a message 
4. handle received messages
5. end session
6. handle basic MQTT client events

Please paste this code into a new JavaScript file in your project, e.g. _tbmq_js_example.ja_:

```bash
const mqtt = require('mqtt');

const url = 'ws://localhost:8084/mqtt'; // default TBMQ WebSocket port is 8084
const options = {
 clean: true, // clean session flag
 clientId: 'tbmq_websockets_client_id',
 username: 'tbmq_websockets_username',
 password: null
};
const client = mqtt.connect(url, options); // create a client

const topic = 'sensors/temperature';
const message = 'Hello World';
const qos = 1;

client.on('connect', function () { // connect client
 console.log('Client connected!');
 client.subscribe({[topic]: {qos}}, function (error) { // subscribe to a topic
   if (!error) {
     client.publish(topic, message, {qos}); // publish a message
   }
 });
});

client.on('message', (topic, message) => { // handle received messages
 console.log(`Received Message: ${message.toString()} \nTopic: '${topic}'`);
 client.end(); // end client session
});

client.on('disconnect', () => { console.log('Disconnecting...'); });
client.on('error', (error) => { console.log('Error: ', error?.message); }); // handle errors
client.on('packetreceive', (packet) => { console.log('Packet receive cmd: ', packet.cmd); }); // handle received packet
client.on('packetsend', (packet) => { console.log('Packet send cmd: ', packet.cmd); }); // handle sent packet

// client.publish(topic, 'Hello World',{qos: 1}); // publish a message

{:copy-code}
```

To run this JavaScript application you may use [node](https://nodejs.org/en/download/package-manager/):

```bash
node tbmq_js_example.js
{:copy-code}
```

Here is the output from executing the _tbmq_js_example_.js file:

```bash
Packet receive cmd:  connack
Client connected!
Packet send cmd:  subscribe
Packet receive cmd:  suback
Packet send cmd:  publish
Packet receive cmd:  puback
Packet receive cmd:  publish
Received Message: Hello World 
Topic: 'sensors/temperature'
Packet send cmd:  disconnect
Packet send cmd:  puback
```

#### Next steps
On the official MQTT.js [GitHub page](https://github.com/mqttjs/MQTT.js), you can find detailed information about using JavaScript library, including its extensive features and usage examples.

Additionally, our guide on [MQTT over WebSocket](https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-over-ws/), featuring an example of using the aforementioned library with the [TBMQ WebSocket Client](/ws-client), might be useful to you.
