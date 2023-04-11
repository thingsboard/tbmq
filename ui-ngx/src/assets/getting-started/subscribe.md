Let's create a subscription using the open-source message broker, <a href='https://mosquitto.org/download' target="_blank">**Eclipse Mosquitto**</a>.

To do this, paste the following code into a new terminal tab:

```bash
mosquitto_sub -h {:hostname} -p {:port} -d --qos 1 -i tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -k 60 -x 120 -V mqttv5
{:copy-code}
```

This code will create a subscription for the MQTT **topic** <i>demo/topic</i> and for clients with the **client ID and password** <i>tb_mqtt_demo</i>.
