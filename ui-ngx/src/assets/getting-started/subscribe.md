Let's create a subscription using a simple MQTT client <a href='https://mosquitto.org/man/mosquitto_sub-1.html' target="_blank">**mosquitto_sub**</a>.

To do this, paste the following code into a new terminal tab:

```bash
mosquitto_sub -h {:hostname} -p {:port} -d -u tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -v -V mqttv5 -x 300
{:copy-code}
```

This code will create a subscription for the MQTT **topic** <i>demo/topic</i> and for clients with the **client ID and password** <i>tb_mqtt_demo</i>.
