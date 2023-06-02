Let's create a subscription using a simple MQTT client <a href='https://mosquitto.org/man/mosquitto_sub-1.html' target="_blank">**mosquitto_sub**</a>.

To do this, paste the following code into a new terminal tab:

```bash
mosquitto_sub -h {:hostname} -p {:port} -d -u tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -v -V mqttv5 -x 300
{:copy-code}
```

This command will create a subscription for the MQTT **topic** `demo/topic` and for clients with the **username and password** `tb_mqtt_demo`.
