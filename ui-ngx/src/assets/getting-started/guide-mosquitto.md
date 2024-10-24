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

In this guide we will use the default TBMQ credentials `TBMQ WebSockets MQTT Credentials` to subscribe to the MQTT topic `tbmq/demo/+`.

In case you have changed the `TBMQ WebSockets MQTT Credentials`, don't forget to update the client ID (`-i`), username (`-u`), and password (`-P`) in the commands below.

Please copy and paste the following code into a terminal tab:

```bash
mosquitto_sub -d -q 1 -h {:mqttHost} -p {:mqttPort} -t tbmq/demo/+ -i tbmq -u tbmq_websockets_username -c -v{:copy-code}
```

##### Publish

In order to publish a message on topic `tbmq/demo/topic`, open a new terminal tab and paste the following command:

```bash
mosquitto_pub -d -q 1 -h {:mqttHost} -p {:mqttPort} -t tbmq/demo/topic -u tbmq_websockets_username -m 'Hello, TBMQ!'{:copy-code}
```

<br>

Once you run this command, you should see the published message in the terminal tab of the subscribed client:

```bash
Client tbmq sending CONNECT
Client tbmq received CONNACK (0)
Client tbmq sending SUBSCRIBE (Mid: 1, Topic: tbmq/demo/+, QoS: 1, Options: 0x00)
Client tbmq received SUBACK
Subscribed (mid: 1): 1
Client tbmq received PUBLISH (d0, q1, r0, m3, 'tbmq/demo/topic', ... (11 bytes))
Client tbmq sending PUBACK (m3, rc0)
tbmq/demo/topic Hello, TBMQ!
```
