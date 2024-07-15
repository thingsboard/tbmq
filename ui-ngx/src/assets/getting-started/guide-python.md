### Javascript
This guide contains ready-to-use example how to use the **Paho MQTT Python Client** library with TBMQ. You will be able to connect MQTT client, subscribe to a topic, publish a message.

##### Prerequisites
In order to use this project make sure you have installed:
* [python](https://github.com/eclipse/paho.mqtt.python) 
* [paho-mqtt](https://github.com/eclipse/paho.mqtt.python).

This guide was developed by using Python v3.10.12.

You can check your python with this command:
```bash
python3 --version{:copy-code}
```

The paho-mqtt latest stable version can be installed with the following command:
```bash
pip install paho-mqtt{:copy-code}
```

##### Connect paho-mqtt to the TBMQ
The code snippet below provides a demonstration on how to:
* Connect to a TBMQ broker using pre-configured client credentials "TBMQ WebSockets MQTT Credentials"
* Subscribe for a topic
* Publish a message
* Handle received messages
* Terminate session
* Handle MQTT client events

You can paste this code into a new python file in your project, e.g. 'tbmq-python-demo.py':

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
