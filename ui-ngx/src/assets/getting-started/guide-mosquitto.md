### Mosquitto

This guide contains example of using an open-source command-line MQTT client library [Eclipse Mosquitto](https://mosquitto.org/).
The **mosquitto_pub** tool is used to publish messages to an MQTT topic, while the **mosquitto_sub** command is used to subscribe to 
MQTT topics and receive messages.

##### Prerequisites

The Mosquitto client library can be installed using the following command:

```bash
sudo apt-get install mosquitto-clients{:copy-code}
```

##### Subscribe
To subscribe client to the MQTT topic `tbmq/demo/+` we will use the <a href='https://mosquitto.org/man/mosquitto_sub-1.html' target="_blank">mosquitto_sub</a> MQTT client.
Please copy and paste the following code into a terminal tab:

<br>

```bash
mosquitto_sub -h {:hostname} -p {:port} -d -u tbmq_websockets_username -t tbmq/demo/+ -q 1 -c -i tbmq -v -V mqttv5{:copy-code}
```

##### Publish

In order to publish a message on topic `tbmq/demo/topic`, open a new terminal tab and paste the following command:

<br>

```bash
mosquitto_pub -h {:hostname} -p {:port} -d -u tbmq_websockets_username -t tbmq/demo/topic -m 'Hello World' -q 1 -V mqttv5{:copy-code}
```

Once you run this command, you should see the published message in the terminal tab of the subscribed client:

```bash
Client tbmq sending CONNECT
Client tbmq received CONNACK (0)
Client tbmq sending SUBSCRIBE (Mid: 1, Topic: tbmq/demo/+, QoS: 1, Options: 0x00)
Client tbmq received SUBACK
Subscribed (mid: 1): 1
Client tbmq received PUBLISH (d0, q1, r0, m3, 'tbmq/demo/topic', ... (11 bytes))
Client tbmq sending PUBACK (m3, rc0)
tbmq/demo/topic Hello World
```

#### See also

