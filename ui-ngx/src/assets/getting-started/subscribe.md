Let's create a subscription with an open source message broker <a href='https://mosquitto.org/download' target="_blank">**Eclipse Mosquitto**</a>.

Paste this code in a new terminal tab:

```bash
mosquitto_sub -h {:hostname} -p {:port} -d --qos 1 -i tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -k 60 -x 120 -V mqttv5
{:copy-code}
```

This will create a subscription for the MQTT **topic** <i>demo/topic</i> for clients with **client-id and password** <i>tb_mqtt_demo</i>.
