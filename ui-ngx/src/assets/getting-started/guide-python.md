### Paho MQTT Python Client

This guide provides a ready-to-use example of how to use the **Paho MQTT Python Client** library with TBMQ. 
Using it, you will learn how to connect an MQTT client, subscribe to a topic, publish a message, and handle MQTT events.

##### Prerequisites
In order to run the code  Python code please make sure you have installed:
* [python3](https://www.python.org/downloads)
* [paho-mqtt](https://github.com/eclipse/paho.mqtt.python)

This guide was developed by using Python v3.10.12 and the new Paho-MQTT 2.0 version (which contains some breaking [changes]((https://github.com/eclipse/paho.mqtt.python/blob/master/docs/migrations.rst)) comparing to older 1.X version). 

Use the next commands to check your Python and Paho versions:

```bash
python3 --version{:copy-code}
```

```bash
pip show paho-mqtt{:copy-code}
```

##### Connect paho-mqtt to the TBMQ
The script below sets up a Paho MQTT client to connect to the TBMQ broker, handles basic MQTT operations such as publishing a message and subscribing to a topic.

You can paste this code into a new python file in your project, e.g. 'tbmq-python.py':

```bash
import paho.mqtt.client as pahoMqtt
from paho import mqtt

host = "localhost"
port = 1883
topic = "sensors/temperature"
payload = "Hello world"
qos = 1
retain = False
clean_session = True
userdata = None
protocol = pahoMqtt.MQTTv311 # or MQTTv5, or MQTTv31

# client credentials
username = "tbmq_websockets_username"
clientId = "tbmq_websockets_client_id"
password = None

# This function initializes and returns an MQTT client.
def init_mqtt_client() -> pahoMqtt:
    # Instantiate an MQTT client
    client = pahoMqtt.Client(pahoMqtt.CallbackAPIVersion.VERSION2, clientId, clean_session, userdata, protocol)
    # Set the client's username and password.
    client.username_pw_set(username, password)
    # Connect the client to the TBMQ broker
    client.connect(host, port)
    return client

# This is the callback function that is called when the client is connected with the MQTT server.
def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print("Client connected!")
    else:
        print("Client not connected, reason code: ", rc)

# This is the callback function that is called when a message is received after subscribing to a topic.
def on_message(client, userdata, msg):
    print('Received message ' + str(msg.payload) + ' on topic ' + msg.topic)

# This is the callback function that is called when there is any error during MQTT operations.
def on_error(client, userdata, err):
    print("Error: " + str(err))

# This is the callback function that is called when the client is disconnected from the MQTT server.
def on_disconnect(client, userdata, rc):
    print("Disconnecting with reason code: " + str(rc))

client = init_mqtt_client()
client.subscribe(topic, qos)

# Assign the event handler functions to the client instance.
client.on_connect = on_connect
client.on_message = on_message
client.on_error = on_error
client.on_disconnect = on_disconnect

# Start a forever loop to process the MQTT events.
client.loop_forever()

{:copy-code}
```

To run this Python application you may use next command:

```bash
python3 tbmq-python.py
{:copy-code}
```

The output from executing the _tbmq-python.py_ file:
```bash
Client connected!
Received message 'Hello world' on topic sensors/temperature
```

#### Next steps
The full documenation on Paho MQTT Python Client [API](https://eclipse.dev/paho/files/paho.mqtt.python/html/client.html).
