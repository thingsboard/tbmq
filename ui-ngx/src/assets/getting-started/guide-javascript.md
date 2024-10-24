### Javascript
In this guide, we present an illustrative example of using a JavaScript library **MQTT.js** 
to establish a connection to the TBMQ broker, subscribe to a topic and publish a message.

##### Prerequisites
To start using the MQTT.js library in your project, you first need to install it using a package manager such as `npm` or `yarn`.

If you want to install package globally, please do not forget to add `-g` flag to the installation command with `npm` and `global` option for `yarn`.

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

To confirm the successful installation of the library, please use the following command:

```bash
mqtt --version{:copy-code}
```

The output should be similar to the following result:

```bash
MQTT.js version: 5.9.1
```

##### Connect to the TBMQ
The code snippet below provides a demonstration on how to connect to a TBMQ broker using default credentials `TBMQ WebSockets MQTT Credentials`, subscribe to a topic, publish a message, handle received messages and some MQTT client events.

In case you have changed the `TBMQ WebSockets MQTT Credentials`, don't forget to update the client ID, username, and password in the guide.

You may paste this code into a new JavaScript file in your project, e.g. `tbmq_js_example.js`. 

```bash
const mqtt = require('mqtt');

const url = 'ws://{:wsHost}:{:wsPort}/mqtt';
const options = {
 clean: true, // clean session flag
 clientId: 'tbmq_test_client',
 username: 'tbmq_websockets_username',
 password: null
};
const client = mqtt.connect(url, options); // create a client

const topic = 'sensors/temperature';
const message = 'Hello, TBMQ!';
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
 //client.end(); // end client session
});

client.on('disconnect', () => { console.log('Disconnecting...'); });
client.on('error', (error) => { console.log('Error: ', error?.message); }); // handle errors
client.on('packetreceive', (packet) => { console.log('Packet receive cmd: ', packet.cmd); }); // handle received packet
client.on('packetsend', (packet) => { console.log('Packet send cmd: ', packet.cmd); }); // handle sent packet

{:copy-code}
```

To run this JavaScript application you may use [Node.js](https://nodejs.org/en/download/package-manager/):

```bash
node tbmq_js_example.js
{:copy-code}
```

Here is the output from executing the `tbmq_js_example.js` file:

```bash
Packet receive cmd:  connack
Client connected!
Packet send cmd:  subscribe
Packet receive cmd:  suback
Packet send cmd:  publish
Packet receive cmd:  puback
Packet receive cmd:  publish
Received Message: Hello, TBMQ! 
Topic: 'sensors/temperature'
Packet send cmd:  puback
```

#### See also
On the official MQTT.js [GitHub page](https://github.com/mqttjs/MQTT.js) you can find detailed information about using JavaScript library, including its extensive features and usage examples.

Additionally, might be useful our guide on [MQTT over WebSocket](https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-over-ws/), featuring an example of using the aforementioned library with the [TBMQ WebSocket Client](/ws-client).
