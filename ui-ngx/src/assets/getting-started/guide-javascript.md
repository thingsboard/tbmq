### Javascript
In this guide, we present an illustrative example of how by using a popular javascript library to establish a connection to the TBMQ broker, subscribe to a topic and publish a message.

##### Prerequisites
In order to start using MQTT.js library in your project you need to install it first. 
You can use one of the package managers as npm or yarn (for global installation add **-g** flag):

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

##### Connect MQTT.js to the TBMQ
The code snippet below provides a demonstration on how to:
* Connect to a TBMQ broker using pre-configured client credentials "TBMQ WebSockets MQTT Credentials"
* Subscribe for a topic
* Publish a message
* Handle received messages
* Terminate session
* Handle MQTT client events

You can paste this code into a new javascript file in your project, e.g. 'tbmq-javascript-demo.js':

```bash
const mqtt = require('mqtt');

const url = 'ws://localhost:8084/mqtt'; // default TBMQ ws port 8084
const options = {
 clean: true, // Clean session
 clientId: 'tbmq_websockets_client_id',
 username: 'tbmq_websockets_username',
 password: null
};
const client = mqtt.connect(url, options); // create a client

const topic = 'tbmq/demo';
const message = 'Hello World';

client.on('connect', function () { // connect client
 console.log('Client connected!');
 client.subscribe(topic, function (error) { // subscribe to a topic
   if (!error) {
     client.publish(topic, message); // publish a message
   }
 });
});

client.on('message', (topic, message) => { // handle received messages
 console.log(`Received Message: ${message.toString()} \nTopic: '${topic}'`);
 client.end(); // end client session
});

client.on('disconnect', () => { console.log('Disconnecting...'); });
client.on('error', (error) => { console.log('Error: ', error?.message); }); // handle errors
client.on('packetreceive', (packet) => { console.log('Packet receive...', packet); }); // handle received packet
client.on('packetsend', (packet) => { console.log('Packet send...', packet); }); // handle sent packet

client.publish('tbmq/demo', 'Hello World',{qos: 1}); // publish a message
{:copy-code}
```

To run this javascript application you may use node:

```bash
node tbmq-javascript-demo.js
{:copy-code}
```

#### Next steps
Additional in-depth information about MQTT.js, including its extensive features and usage examples,
can be found on its official [GitHub page](https://github.com/mqttjs/MQTT.js).
