### Paho MQTT Python Client
This guide contains ready-to-use example how to use the **Paho MQTT Python Client** library with TBMQ to connect MQTT client, subscribe to a topic, publish a message.

##### Prerequisites
In order to use this project make sure you have installed:
* [python3](https://www.python.org/downloads)
* [paho-mqtt](https://github.com/eclipse/paho.mqtt.python)

This guide was developed by using Python v3.10.12. You can check your python with this command:
```bash
python3 --version{:copy-code}
```

The Paho Python library is developed by the Eclipse Paho project and can be used in Python applications to implement MQTT clients.
The Paho MQTT Python client provides functions to connect to an MQTT broker, publish messages, subscribe to topics, and receive messages. It offers a fully asynchronous mode of operation and supports SSL/TLS for secure communication.

The **paho-mqtt** 1.X stable version can be installed with the following command:
```bash
pip3 install "paho-mqtt<2.0.0"{:copy-code}
```

##### Connect paho-mqtt to the TBMQ
The script below sets up a Paho MQTT client to connect to the TBMQ broker, handles MQTT operations such as connecting, publishing, subscribing, and disconnecting.

The script defines several callback functions to be executed on different MQTT events such as on_connect, on_publish, on_subscribe, on_message, on_error, on_disconnect.


You can paste this code into a new python file in your project, e.g. 'tbmq-python.py':

```bash
import paho.mqtt.client as pahoMqtt
from paho import mqtt

host = "localhost"
port = 11883

topic = "tbmq/demo"
qos = 1
retain = True

username = "tbmq_websockets_username1"
clientId = "tbmq_websockets_client_id"

payload = "Hello, world!"
clean_session = True
password = None
userdata = {"data": 123}
protocol = pahoMqtt.MQTTv311 # or MQTTv5, or MQTTv31

# This is the callback function that is called when the client is connected with the MQTT server.
def on_connect(client, userdata, flags, rc, properties=None):
    if (rc == 0):
        print("Client connected!")
        # Once the client is connected, it subscribes to a topic and sends a publish message on that topic.
        client.subscribe(topic, qos=qos)
        client.publish(topic, payload=payload, qos=qos, retain=retain)
    else:
        print("Client not connected, reason code:", rc)

# This is the callback function that is called when the publish request has been processed by the MQTT server.
def on_publish(client, userdata, mid, properties=None):
    print("Data published with message ID " + str(mid))

# This is the callback function that is called when the subscribe request has been processed by the MQTT server.
def on_subscribe(client, userdata, mid, granted_qos, properties=None):
    print("Subscribed with message ID " + str(mid) + " granted QoS " + str(granted_qos))

# This is the callback function that is called when a message is received after subscribing to a topic.
def on_message(client, userdata, msg):
    print('Received Message: ' + str(msg.payload) + ' on topic ' + msg.topic)

# This is the callback function that is called when there is any error during MQTT operations.
def on_error(client, userdata, err):
    print("Error occurred " + str(err))

# This is the callback function that is called when the client is disconnected from the MQTT server.
def on_disconnect(client, userdata, rc):
    print("Disconnected with return code " + str(rc))

# Instantiate a new MQTT client, set the username and password.
client = pahoMqtt.Client(clientId, clean_session, userdata, protocol)
client.username_pw_set(username, password)

# Set the callback functions for various MQTT events.
client.on_connect = on_connect
client.on_subscribe = on_subscribe
client.on_message = on_message
client.on_publish = on_publish
client.on_error = on_error
client.on_disconnect = on_disconnect

# Establish a connection to the MQTT server.
client.connect(host, port)

# Start a forever loop to process the MQTT events.
client.loop_forever()

{:copy-code}
```

To run this python application you may use python3:

```bash
python3 tbmq-python.py
{:copy-code}
```

#### Next steps

